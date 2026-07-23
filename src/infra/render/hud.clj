(ns infra.render.hud
  "HUD primitives, text, and overlay layout.
   OpenGL drawing for HUD rectangles and text, plus pure layout helpers for
   stats, controls, and the observer panel."
  (:require
   [clojure.math :as math] [domain.pacing :as pacing]
   [domain.player :as player]
   [domain.ecs.core :as ecs]
   [domain.ecs.components :as c]
   [domain.intervention :as intervention]
   [domain.gravity.dark-matter :as dark-matter]
   [law.stellar :as law]
   [infra.render.color :as color]
   [infra.render.input :as rinput]
   [infra.render.passes :as passes])
  (:import
   (org.lwjgl.opengl GL11 GL15 GL20 GL30)
   (org.lwjgl.stb STBEasyFont)
   (org.lwjgl BufferUtils)
   (java.nio ByteBuffer)))

(defn- hud-quad-floats [x0 y0 x1 y1]
  (float-array [x0 y0  x1 y0  x1 y1   x0 y0  x1 y1  x0 y1]))

(defn render-hud
  "Draw a list of HUD rectangles. Each rect is {:x0 :y0 :x1 :y1 :color [r g b a]}
   in NDC. No-op without a program or rects."
  [hud-program rects]
  (when (and hud-program (pos? (int hud-program)) (seq rects))
    (GL20/glUseProgram hud-program)
    (passes/set-blend! :alpha)
    (passes/set-depth-write! false)
    (let [loc (GL20/glGetUniformLocation hud-program "hudColor")]
      (doseq [{:keys [x0 y0 x1 y1 color]} rects]
        (let [[r g b a] color
              data (hud-quad-floats x0 y0 x1 y1)
              fb   (BufferUtils/createFloatBuffer (count data))
              vao  (GL30/glGenVertexArrays)
              vbo  (GL15/glGenBuffers)]
          (doseq [f data] (.put fb (float f)))
          (.flip fb)
          (GL30/glBindVertexArray vao)
          (GL15/glBindBuffer GL15/GL_ARRAY_BUFFER vbo)
          (GL15/glBufferData GL15/GL_ARRAY_BUFFER fb GL15/GL_STATIC_DRAW)
          (GL20/glVertexAttribPointer 0 2 GL11/GL_FLOAT false 0 0)
          (GL20/glEnableVertexAttribArray 0)
          (GL20/glUniform4f loc (float r) (float g) (float b) (float (or a 1.0)))
          (GL11/glDrawArrays GL11/GL_TRIANGLES 0 6)
          (GL30/glBindVertexArray 0)
          (GL15/glDeleteBuffers vbo)
          (GL30/glDeleteVertexArrays vao))))
    (passes/set-depth-write! true)
    (passes/set-blend! :none)
    (GL20/glUseProgram 0)))

(defn- text->ndc-tris
  "Triangulate one line of `text` into a float-array of NDC (x,y) pairs.
   `x`/`y` are the line's top-left pixel origin, `scale` magnifies the ~7px base
   font, `w`/`h` are the framebuffer size. Returns [float-array vertex-count]."
  [^CharSequence text x y scale w h]
  (let [buf   (BufferUtils/createByteBuffer (max 4096 (* (count text) 400)))
        ^ByteBuffer no-color nil
        quads (STBEasyFont/stb_easy_font_print
               (float 0.0) (float 0.0) text no-color buf)
        ^java.nio.FloatBuffer fb (.asFloatBuffer buf)
        out   (float-array (* quads 12))
        ndcx  (fn ^double [^double px] (- (/ (* 2.0 px) w) 1.0))
        ndcy  (fn ^double [^double py] (- 1.0 (/ (* 2.0 py) h)))]
    (dotimes [q quads]
      (let [b  (* q 16)
            px (fn ^double [i] (ndcx (+ x (* scale (.get fb (int (+ b (* i 4))))))))
            py (fn ^double [i] (ndcy (+ y (* scale (.get fb (int (+ b (* i 4) 1)))))))
            x0 (px 0) y0 (py 0) x1 (px 1) y1 (py 1)
            x2 (px 2) y2 (py 2) x3 (px 3) y3 (py 3)
            o  (* q 12)]
        (aset out o       (float x0))  (aset out (inc o) (float y0))
        (aset out (+ o 2) (float x1))  (aset out (+ o 3) (float y1))
        (aset out (+ o 4) (float x2))  (aset out (+ o 5) (float y2))
        (aset out (+ o 6) (float x0))  (aset out (+ o 7) (float y0))
        (aset out (+ o 8) (float x2))  (aset out (+ o 9) (float y2))
        (aset out (+ o 10) (float x3)) (aset out (+ o 11) (float y3))))
    [out (* quads 6)]))

(defn render-text
  "Draw HUD text lines via the solid-colour HUD program. Each line is
   {:text :x :y :color [r g b a] :scale} with a top-left pixel origin.
   No-op without a program or lines."
  [hud-program lines width height]
  (when (and hud-program (pos? (int hud-program)) (seq lines))
    (GL20/glUseProgram hud-program)
    (passes/set-blend! :alpha)
    (passes/set-depth-write! false)
    (let [loc (GL20/glGetUniformLocation hud-program "hudColor")]
      (doseq [{:keys [text x y color scale] :or {scale 2.0 color [1.0 1.0 1.0 1.0]}} lines]
        (when (seq text)
          (let [[verts n] (text->ndc-tris text (double x) (double y) (double scale)
                                          (double width) (double height))
                fb  (BufferUtils/createFloatBuffer (alength verts))
                vao (GL30/glGenVertexArrays)
                vbo (GL15/glGenBuffers)
                [r g b a] color]
            (.put fb verts)
            (.flip fb)
            (GL30/glBindVertexArray vao)
            (GL15/glBindBuffer GL15/GL_ARRAY_BUFFER vbo)
            (GL15/glBufferData GL15/GL_ARRAY_BUFFER fb GL15/GL_STATIC_DRAW)
            (GL20/glVertexAttribPointer 0 2 GL11/GL_FLOAT false 0 0)
            (GL20/glEnableVertexAttribArray 0)
            (GL20/glUniform4f loc (float r) (float g) (float b) (float (or a 1.0)))
            (GL11/glDrawArrays GL11/GL_TRIANGLES 0 n)
            (GL30/glBindVertexArray 0)
            (GL15/glDeleteBuffers vbo)
            (GL30/glDeleteVertexArrays vao)))))
    (passes/set-depth-write! true)
    (passes/set-blend! :none)
    (GL20/glUseProgram 0)))

(defn- format-elapsed
  "Human astronomical duration from elapsed simulation seconds."
  [sim-seconds]
  (let [yr (/ (double (or sim-seconds 0.0)) pacing/seconds-per-year)]
    (cond
      (< yr 1.0e3) (format "%.0f yr" yr)
      (< yr 1.0e6) (format "%.1f kyr" (/ yr 1.0e3))
      (< yr 1.0e9) (format "%.2f Myr" (/ yr 1.0e6))
      :else        (format "%.2f Gyr" (/ yr 1.0e9)))))

(defn- format-rate
  "Human clock rate from years-of-sim advanced per real second."
  [rate-yr]
  (let [r (double (or rate-yr 0.0))]
    (cond
      (>= r 1.0e3) (format "%.0f kyr/s" (/ r 1.0e3))
      (>= r 1.0)   (format "%.0f yr/s" r)
      :else        (format "%.1f yr/s" r))))

(defn- phase-label
  "Player-facing name for the current genesis arc."
  [arc]
  (case arc
    :arc/genesis-nebula-collapse "Nebula collapsing"
    :arc/genesis-protostar       "Protostar forming"
    :arc/genesis-ignition        "Ignition"
    :arc/genesis-accretion       "Accretion"
    :arc/genesis-planets-formed  "Planets formed"
    :arc/genesis-dispersed       "Dispersed"
    (if arc (name arc) "Initializing")))

(defn- genesis-stat-lines
  "Render-space strings for the Phase 0 genesis stats panel."
  [world]
  (let [{:keys [total-mass-msun avg-temp peak-temp
                body-count resolved-count star-count planet-count
                xuv-escape-count sed-band-count
                lod-local lod-system lod-galaxy
                imf-bins disk-count]
         :or   {total-mass-msun 0.0 avg-temp 0.0 peak-temp 0.0
                body-count 0 resolved-count 0 star-count 0 planet-count 0
                xuv-escape-count 0 sed-band-count 0
                lod-local 0 lod-system 0 lod-galaxy 0
                imf-bins [0 0 0 0 0 0 0 0] disk-count 0}}
        (:genesis/stats world)
        tick (int (or (:tick world) 0))]
    [(format "tick   %d" tick)
     (format "%s   %s"
             (format-elapsed (:genesis/sim-time world))
             (phase-label (:arc/current world)))
     (format "clock  %s" (format-rate (:genesis/rate-yr world)))
     (format "mass   %.3f Msun" (double total-mass-msun))
     (format "temp   %.0f K  (peak %.0f K)"
             (double avg-temp) (double peak-temp))
     (format "bodies %d  resolved %d  stars %d  planets %d"
             (int body-count) (int resolved-count)
             (int star-count) (int planet-count))
     (format "SED    %d bands  XUV-esc %d"
             (int sed-band-count) (int xuv-escape-count))
     (format "LOD    local %d  system %d  galaxy %d"
             (int lod-local) (int lod-system) (int lod-galaxy))
     (format "disks  %d  planets %d"
             (int disk-count) (int planet-count))
     (format "IMF    <.1:%d  .1-.5:%d  .5-1:%d  1-2:%d  2-5:%d  5-10:%d  10-50:%d  >50:%d"
             (int (nth imf-bins 0)) (int (nth imf-bins 1))
             (int (nth imf-bins 2)) (int (nth imf-bins 3))
             (int (nth imf-bins 4)) (int (nth imf-bins 5))
             (int (nth imf-bins 6)) (int (nth imf-bins 7)))
     (format "DM halo M %.2f Msun  r %.2f AU"
             (/ (dark-matter/halo-mass world) (double law/solar-mass))
             (/ (dark-matter/halo-scale-radius world) (double law/au)))])),

(defn hud-text-from-world
  "Top-left stats panel for a Phase 0 world: the adaptive clock (elapsed
   sim-time, current rate, phase) plus total mass, temperature, body counts,
   and the simulation tick counter. Reads the per-tick `:genesis/stats` cache.
   Empty for non-genesis/bare worlds."
  [world]
  (if (:genesis/rate-yr world)
    (map-indexed (fn [i s]
                   {:text s :x 16.0 :y (+ 38.0 (* i 22.0))
                    :scale 2.2 :color [0.86 0.94 1.0 0.95]})
                 (genesis-stat-lines world))
    []))

(defn- observer-base-text
  "The fixed bottom-left observer state lines: quanta, resonance, spark state, focus."
  [obs state _width height]
  (let [agency    (long (math/floor (double (or (:agency obs) 0.0))))
        resonance (long (math/floor (double (or (:resonance obs) 0.0))))
        scol      (conj (color/coherence-color state) 1.0)
        h         (double height)]
    [{:text (format "%d quanta" agency)
      :x 16.0 :y (- h 96.0) :scale 2.4 :color [0.78 0.92 1.0 0.98]}
     {:text (format "%d resonance" resonance)
      :x 16.0 :y (- h 72.0) :scale 1.5 :color [0.85 0.78 1.0 0.85]}
     {:text (format "spark: %s" (name state))
      :x 16.0 :y (- h 48.0) :scale 1.7 :color scol}
     {:text (format "focus: %.0f%%" (* 100.0 (double (or (:focus-intensity obs) 0.5))))
      :x 16.0 :y (- h 26.0) :scale 1.5 :color [0.65 0.80 0.95 0.85]}]))

(def ^:const binding-bar-length
  "Character width of the binding bar rendered in `binding-readout-entry`."
  20)

(defn- binding-bar
  "A `[####----]`-style ASCII bar for `b` in [0,1], `binding-bar-length` wide."
  [b]
  (let [filled (long (math/round (* binding-bar-length (double b))))]
    (str "[" (apply str (repeat filled \#)) (apply str (repeat (- binding-bar-length filled) \-)) "]")))

(defn- committed-world-eid
  "The eid of the world whose `c/commitment-state` is `:committed`, or nil.
   Mirrors `domain.narrowing`'s private `committed-world-eid` — read-only,
   no domain change (The First Narrowing, child B)."
  [world]
  (some (fn [[eid m]] (when (= :committed (get m c/commitment-state)) eid))
        (ecs/all-of world c/commitment-state)))

(defn- binding-readout-entry
  "HUD line for the observer's gravitational binding to candidate worlds
   (`c/binding`, The First Narrowing child A/B — `domain.narrowing`).

   - When some world already carries `c/commitment-state :committed`, shows a
     distinct 'world committed' readout instead of a live percentage — the
     commitment horizon is write-once and irreversible, so this readout never
     reverts once shown.
   - Otherwise, when the observer's `c/binding` map is non-empty, shows the
     DEEPEST (max-value) candidate world's binding as an ASCII bar + percent.
   - nil with no observer, no binding, and no commitment — purely additive,
     read-only against the ECS world."
  [world height]
  (when-let [eid (player/observer-entity world)]
    (let [h (double height)
          y (- h 118.0)]
      (if (committed-world-eid world)
        {:text "world committed"
         :x 16.0 :y y :scale 1.6 :color [1.0 0.85 0.55 0.95]}
        (let [binding (or (ecs/get-component world eid c/binding) {})]
          (when (seq binding)
            (let [[_ b] (apply max-key val binding)
                  b     (double b)]
              {:text (format "binding %s %.0f%%" (binding-bar b) (* 100.0 b))
               :x 16.0 :y y :scale 1.5 :color [0.95 0.75 0.55 0.85]})))))))

(defn- notif-entry
  "A transient centered notification, fading over 200 ticks."
  [world notif width height]
  (when-let [text (:text notif)]
    (let [age   (- (long (or (:tick world) 0)) (long (:tick notif)))
          alpha (max 0.0 (- 1.0 (/ (double age) 200.0)))]
      (when (> alpha 0.05)
        {:text text
         :x (- (* (double width) 0.5) 140.0)
         :y (- (double height) 200.0)
         :scale 2.8
         :color [1.0 0.92 0.60 ^double alpha]}))))

(defn- controls-help-line
  "Bottom-right passive control legend."
  [width height]
  {:text "focus rides camera   arrows: move focus (manual)   ,/.: narrow/widen   G: warp   L: life   space: drift"
   :x (- (double width) 460.0)
   :y (- (double height) 18.0)
   :scale 1.2
   :color [0.50 0.55 0.65 0.55]})

(def ^:const ambient-line-fade-ticks
  "How long an ambient narrator line floats in the viewport before it is gone.
   Long and quiet: the line is an ambience, not a notification (the transient
   centered event notification above fades in 200 ticks and stays a separate,
   brighter channel)."
  600)

(defn- ambient-line-entry
  "The most recent AMBIENT narrator line as a small, dim viewport float,
   fading over ambient-line-fade-ticks. This is the felt surface of
   law.narrative utterances with :attribution :ambient (The First Narrowing,
   child C; ux-architecture.md 'Ambient line display: viewport float'). Never
   addressed text, never a modal, never centred like the event notification:
   it sits low and off to the side at low alpha. nil when there is no line,
   the line is not ambient, or it has fully faded."
  [world width height]
  (when-let [eid (player/observer-entity world)]
    (when-let [line (:last-line (ecs/get-component world eid c/narrative-state))]
      (when (= :ambient (:attribution line))
        (let [age   (- (long (or (:tick world) 0)) (long (:tick line)))
              alpha (max 0.0 (- 0.55 (* 0.55 (/ (double age) ambient-line-fade-ticks))))]
          (when (> alpha 0.03)
            {:text  (:text line)
             :x     (* (double width) 0.5)
             :y     (* (double height) 0.72)
             :scale 1.5
             :color [0.80 0.72 0.85 ^double alpha]}))))))

(defn observer-hud-text
  "Player HUD: quanta/state (bottom-left), observation note + quest (bottom-center),
   event notifications (center), ambient narrator line (low viewport float),
   gravitational-binding readout (bottom-left, above quanta — see
   `binding-readout-entry`), controls hint (bottom-right). `height` anchors
   everything to the framebuffer size. Empty without an observer."
  [world width height]
  (if-let [obs (player/get-observer world)]
    (let [state   (player/decoherence-state obs)
          note    (:arc/observation-note world)
          quest   (:arc/quest world)
          notif   (:arc/notification world)
          base    (observer-base-text obs state width height)
          n-line  (notif-entry world notif width height)
          a-line  (ambient-line-entry world width height)
          b-line  (binding-readout-entry world height)
          w       (double width)
          h       (double height)]
      (cond-> base
        note   (conj {:text note
                      :x (- w 220.0) :y (- h 40.0)
                      :scale 1.6 :color [0.90 0.95 1.0 0.85]})
        quest  (conj {:text quest
                      :x (- w 200.0) :y (- h 18.0)
                      :scale 1.4 :color [0.70 0.85 0.95 0.70]})
        n-line (conj n-line)
        a-line (conj a-line)
        b-line (conj b-line)
        true   (conj (controls-help-line width height))))
    []))

(defn- afford-colors
  "Text colours for an action row given the spark's quanta and the action's cost:
   bright/green when affordable, dimmed/red when not."
  [agency cost]
  (if (>= (double agency) (double cost))
    {:label [0.88 0.95 1.0 0.98] :cost [0.65 1.0 0.78 0.98]}
    {:label [0.55 0.50 0.55 0.70] :cost [1.0 0.50 0.45 0.85]}))

(defn controls-hud
  "The teaching layer. Renders the paid-action palette (bottom-right) straight
   from `action-palette` — key, label, and cost, the key tinted with the action's
   ring colour and the row lit by whether the spark can afford it — plus a
   one-line passive-controls legend. Returns {:rects :text}."
  [world width height]
  (let [agency (double (or (:agency (player/get-observer world)) 0.0))
        w (double width) h (double height)
        scale 1.9 line-h 22.0 pad 12.0
        rows (count rinput/action-palette)
        panel-w 252.0
        panel-h (+ (* 2.0 pad) (* (inc rows) line-h))
        x0 (- w panel-w 16.0)
        y0 (- h panel-h 34.0)
        ndcx (fn [px] (- (/ (* 2.0 px) w) 1.0))
        ndcy (fn [py] (- 1.0 (/ (* 2.0 py) h)))
        rect {:x0 (ndcx x0) :y0 (ndcy (+ y0 panel-h))
              :x1 (ndcx (+ x0 panel-w)) :y1 (ndcy y0)
              :color [0.04 0.06 0.12 0.82]}
        header {:text "ACTIONS  (spend quanta)" :x (+ x0 pad) :y (+ y0 pad)
                :scale 1.6 :color [0.70 0.82 1.0 0.95]}
        action-text
        (mapcat
         (fn [i {:keys [keycap label kind accent]}]
           (let [cost (intervention/cost-of kind)
                 y    (+ y0 pad (* (inc i) line-h))
                 {lc :label cc :cost} (afford-colors agency cost)]
             [{:text keycap :x (+ x0 pad)        :y y :scale scale :color (conj (vec accent) 1.0)}
              {:text label  :x (+ x0 pad 52.0)   :y y :scale scale :color lc}
              {:text (format "%.0fq" (double cost)) :x (+ x0 pad 176.0) :y y :scale scale :color cc}]))
         (range) rinput/action-palette)
        passive {:text "mouse look   scroll zoom   C camera   Tab menu   click inspect   WASD = move mote"
                 :x 352.0 :y (- h 20.0) :scale 1.4 :color [0.55 0.68 0.85 0.8]}]
    {:rects [rect]
     :text  (into [header passive] action-text)}))

(defn view-bar-hud
  "Top-left status bar separating the active view/camera from the simulation.

   Shows current camera mode, orbit distance, move speed, and whether the view
   is user-driven or tracking the world."
  [camera-settings camera width height]
  (let [w (double width) h (double height)
        mode (:mode camera-settings :manual)
        dist (:distance camera 50.0)
        speed (:move-speed camera-settings 3.0e15)
        tracking? (not= :manual mode)
        label (case mode
                :manual "3RD PERSON"
                :track-largest-cluster "TRACK"
                :fit-all "FIT ALL"
                (name mode))
        ndcx (fn [px] (- (/ (* 2.0 px) w) 1.0))
        ndcy (fn [py] (- 1.0 (/ (* 2.0 py) h)))
        x0 10.0 y0 34.0
        line-h 20.0 pad 10.0
        panel-w 230.0
        panel-h (+ (* 2.0 pad) (* 3.0 line-h))
        rect {:x0 (ndcx x0) :y0 (ndcy (+ y0 panel-h))
              :x1 (ndcx (+ x0 panel-w)) :y1 (ndcy y0)
              :color [0.04 0.06 0.12 0.82]}
        header {:text (format "VIEW: %s%s" label (if tracking? " (AUTO)" ""))
                :x (+ x0 pad) :y (+ y0 pad) :scale 1.7
                :color (if tracking? [1.0 0.78 0.55 0.95] [0.65 1.0 0.78 0.95])}
        dist-line {:text (format "dist: %.1f" dist)
                   :x (+ x0 pad) :y (+ y0 pad line-h) :scale 1.4
                   :color [0.78 0.92 1.0 0.9]}
        speed-line {:text (format "move: %.2e m/s" (double speed))
                    :x (+ x0 pad) :y (+ y0 pad (* 2.0 line-h)) :scale 1.4
                    :color [0.70 0.82 1.0 0.85]}]
    {:rects [rect]
     :text  [header dist-line speed-line]}))
