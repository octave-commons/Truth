(ns infra.menu.widgets
  "Low-level widgets and action model for the top menu bar.

   Pure layout helpers: colour palette, NDC conversion, steppers, tab bar, and
   the action folding that maps menu clicks to config changes or world intents."
  (:require
   [domain.player :as player]
   [domain.intervention :as intervention]
   [infra.camera :as cam]))

(def ^:const bar-h
  "Height of the top menu bar in framebuffer pixels."
  26.0)

(def domains
  "Canonical top-bar domains, in order (ux-architecture.md). :locked? domains are
   dimmed but still open an evocative panel."
  [{:id :world      :label "World"}
   {:id :view       :label "View"}
   {:id :entities   :label "Entities"}
   {:id :spark      :label "Spark"}
   {:id :phase      :label "Phase"}
   {:id :journal    :label "Journal"}
   {:id :narrator   :label "Narrator"}
   {:id :multiverse :label "Multiverse" :locked? true}])

;; --- colours ---------------------------------------------------------------
(def ^:const col-bar
  "Top-bar background colour [r g b a]."
  [0.03 0.05 0.10 0.92])

(def ^:const col-panel
  "Panel background colour [r g b a]."
  [0.05 0.07 0.13 0.95])

(def ^:const col-btn
  "Button/clickable background colour [r g b a]."
  [0.13 0.20 0.34 0.98])

(def ^:const col-active
  "Active/selected text colour [r g b a]."
  [0.80 0.92 1.0 1.0])

(def ^:const col-inactive
  "Inactive tab text colour [r g b a]."
  [0.62 0.72 0.86 0.90])

(def ^:const col-locked
  "Locked/unavailable text colour [r g b a]."
  [0.42 0.47 0.58 0.60])

(def ^:const col-accent
  "Accent/underline colour [r g b a]."
  [0.45 0.85 1.0 1.0])

(def ^:const col-value
  "Value readout text colour [r g b a]."
  [0.88 0.95 1.0 0.98])

(def ^:const col-dim
  "Dim/secondary text colour [r g b a]."
  [0.60 0.70 0.84 0.85])

(defn- text-w ^double [s ^double scale]
  ;; STBEasyFont advances ~6px per glyph at the base size; good enough for layout.
  (* 6.0 scale (count (str s))))

(def ^:private tab-scale 1.6)

(defn- ndc-x ^double [^double w ^double px]
  (- (/ (* 2.0 (double px)) w) 1.0))

(defn- ndc-y ^double [^double h ^double py]
  (- 1.0 (/ (* 2.0 (double py)) h)))

(defn rect-ctx
  "Make an NDC rect from a render context and a rect spec."
  [{:keys [w h]} {:keys [x0 y0 x1 y1 color]}]
  {:x0 (ndc-x w x0) :y0 (ndc-y h y0) :x1 (ndc-x w x1) :y1 (ndc-y h y1) :color color})

(def ^:private phase-names
  {:arc/genesis-nebula-collapse "Nebula collapsing"
   :arc/genesis-protostar       "Protostar forming"
   :arc/genesis-ignition        "Ignition"
   :arc/genesis-accretion       "Accretion"
   :arc/genesis-planets-formed  "Planets formed"
   :arc/life-emergence          "Life emerges"
   :arc/genesis-dispersed       "Dispersed"})

(defn phase-label
  "Human-readable label for the current arc keyword."
  [arc]
  (or (phase-names arc) (if arc (name arc) "Initializing")))

;; ---------------------------------------------------------------------------
;; Settings model
;; ---------------------------------------------------------------------------

(def view-rows
  "Adjustable camera settings for the View panel. Each row names the config key
   it reads, a printf format, and the [-]/[+] stepper actions (factor + clamp)."
  [{:label "Look sens." :key :look-sensitivity :fmt "%.3f"
    :dec [:setting/scale :look-sensitivity 0.8  0.005 1.0]
    :inc [:setting/scale :look-sensitivity 1.25 0.005 1.0]}
   {:label "Zoom sens." :key :zoom-sensitivity :fmt "%.1f"
    :dec [:setting/scale :zoom-sensitivity 0.8  1.0 200.0]
    :inc [:setting/scale :zoom-sensitivity 1.25 1.0 200.0]}])

(def spark-knobs
  "Adjustable influence knobs for the Spark panel — every magic number of the
   observer halo / warp-well model, as data. Each row names where the value
   lives (:observer key or :world key + default), a printf format, and the
   stepper (:scale multiplies by ±factor, :add steps by ±delta), clamped to
   [lo, hi]. `spark-rows` resolves live values; `world-action` applies steps."
  [{:label "Focus radius" :scope :observer :key :focus-radius :fmt "%.1e"
    :mode :scale :down 0.5 :up 2.0 :lo 1.0e14 :hi 2.0e16}
   {:label "Focus intens" :scope :observer :key :focus-intensity :fmt "%.2f"
    :mode :add :down -0.1 :up 0.1 :lo 0.1 :hi 1.0}
   {:label "Halo mass xM" :scope :world :key :genesis/observer-halo-mass-factor
    :dflt player/default-halo-mass-factor :fmt "%.2f"
    :mode :add :down -0.25 :up 0.25 :lo 0.0 :hi 8.0}
   {:label "Dv cap xVir" :scope :world :key :genesis/influence-dv-cap
    :dflt player/default-influence-dv-cap :fmt "%.2f"
    :mode :scale :down 0.5 :up 2.0 :lo 0.125 :hi 16.0}
   {:label "Well mass xM" :scope :world :key :genesis/well-mass-factor
    :dflt intervention/default-well-mass-factor :fmt "%.2f"
    :mode :add :down -0.1 :up 0.1 :lo 0.0 :hi 4.0}
   {:label "Well radius" :scope :world :key :genesis/well-radius
    :dflt intervention/default-radius :fmt "%.1e"
    :mode :scale :down 0.5 :up 2.0 :lo 5.0e14 :hi 2.0e16}
   {:label "Well ttl" :scope :world :key :genesis/well-ttl
    :dflt intervention/default-ttl :fmt "%.0f"
    :mode :scale :down 0.5 :up 2.0 :lo 60.0 :hi 6000.0}
   {:label "Heat rate" :scope :world :key :genesis/heat-approach
    :dflt intervention/default-heat-approach :fmt "%.3f"
    :mode :scale :down 0.5 :up 2.0 :lo 0.001 :hi 0.5}
   {:label "Thrust m/t" :scope :world :key :genesis/spark-flight-displacement
    :dflt player/default-displacement-per-tick :fmt "%.1e"
    :mode :scale :down 0.5 :up 2.0 :lo 1.0e12 :hi 1.0e16}
   {:label "Damp keep/t" :scope :world :key :genesis/spark-damping-retention
    :dflt player/default-damping-retention :fmt "%.3f"
    :mode :add :down -0.01 :up 0.01 :lo 0.80 :hi 0.999}])

(defn knob-action
  "The [:spark/knob ...] menu action for stepping knob `k` in `direction`
   (:down / :up)."
  [{:keys [scope dflt mode lo hi] :as k} direction]
  [:spark/knob scope (:key k) (or dflt lo) mode (get k direction) lo hi])

(defn knob-value
  "Live value of a spark knob: observer knobs read the observer map, world
   knobs the :genesis/* key (falling back to the domain default)."
  ^double [{:keys [scope dflt lo] :as k} world obs]
  (double (or (if (= scope :observer) (get obs (:key k)) (get world (:key k)))
              dflt lo)))

(defn world-action
  "The world→world' fn a menu :action implies, or nil when the action targets
   the shell config (`apply-action`). World actions adjust the SIMULATION —
   observer focus knobs and :genesis/* influence keys — so the window loop
   enqueues the returned fn as a sim intent and it lands between ticks, like
   every other input."
  [action]
  (when (= :spark/knob (first action))
    (let [[_ scope k dflt mode step lo hi] action
          bump (fn [v]
                 (let [v  (double (or v dflt))
                       v' (case mode :scale (* v (double step)) :add (+ v (double step)))]
                   (max (double lo) (min (double hi) v'))))]
      (if (= scope :observer)
        (fn [w] (player/update-observer w #(update % k bump)))
        (fn [w] (update w k bump))))))

(defn apply-action
  "Fold a menu :action into the config map. Pure — the window loop swaps the
   result into config-atom. Sim-side actions are not handled here: the window
   loop routes anything `world-action` recognizes to the sim intent queue."
  [cfg action]
  (case (first action)
    :ui/toggle-domain
    (let [d (second action)]
      (update cfg :ui/active-domain #(when-not (= % d) d)))

    ;; Selecting a body also tethers the camera to it (:follow-selection mode),
    ;; so the explorer is not just a list — clicking a row takes you there.
    ;; Deselecting (nil) releases the tether back to :manual.
    :ui/select-entity
    (if-let [eid (second action)]
      (assoc cfg :selection eid :mode :follow-selection :follow-eid eid)
      (-> cfg
          (dissoc :selection :follow-eid :zoom-min)
          (cond-> (= :follow-selection (:mode cfg)) (assoc :mode :manual))))

    :setting/scale
    (let [[_ k factor lo hi] action
          dflt (get (cam/default-camera-settings) k 1.0)
          cur  (double (get cfg k dflt))]
      (assoc cfg k (max (double lo) (min (double hi) (* cur (double factor))))))

    :camera/cycle-mode
    (cam/cycle-camera-mode cfg)

    cfg))

;; UNUSED-PENDING: UX/render surface with no caller yet. CLAUDE.md: `docs/designs/ux-architecture.md`
;; is canonical for all user interaction, and much current UX/render code is
;; acknowledged ad-hoc rather than design intent — these are on the wrong side of
;; that gap, not abandoned.
;; See docs/designs/ux-architecture.md
#_{:clj-kondo/ignore [:clojure-lsp/unused-public-var]}
(defn escape-action
  "The menu :action an ESC press implies for the current shell state, or nil
   when there is nothing left to escape.

   Hierarchical — each press peels one shell layer and hands attention back to
   its parent: an open panel closes first; then a live selection releases
   (untethering the camera). ESC never reaches the window itself — closing the
   window goes through the quit-confirm prompt (`confirm-close-hud`) instead."
  [cfg]
  (cond
    (:ui/active-domain cfg) [:ui/toggle-domain (:ui/active-domain cfg)]
    (:selection cfg)        [:ui/select-entity nil]
    :else                   nil))

;; UNUSED-PENDING: UX/render surface with no caller yet. CLAUDE.md: `docs/designs/ux-architecture.md`
;; is canonical for all user interaction, and much current UX/render code is
;; acknowledged ad-hoc rather than design intent — these are on the wrong side of
;; that gap, not abandoned.
;; See docs/designs/ux-architecture.md
#_{:clj-kondo/ignore [:clojure-lsp/unused-public-var]}
(defn confirm-close-hud
  "Centered quit-confirmation prompt over a `w`×`h` framebuffer, shown while
   `:ui/confirm-close?` is set (the OS close button was pressed). Returns
   {:rects :text} in the same NDC / pixel formats as `menu-hud`."
  [^double w ^double h]
  (let [ndcx  (fn [px] (- (/ (* 2.0 (double px)) w) 1.0))
        ndcy  (fn [py] (- 1.0 (/ (* 2.0 (double py)) h)))
        bw    440.0
        bh    96.0
        x0    (/ (- w bw) 2.0)
        y0    (/ (- h bh) 2.0)
        lines [["Close the dev window?" 1.8 col-active]
               ["The sim keeps running; reopen with (w/resurrect-window!)." 1.3 col-dim]
               ["Enter — close        Esc — keep watching" 1.4 col-value]]]
    {:rects [{:x0 (ndcx x0) :y0 (ndcy (+ y0 bh)) :x1 (ndcx (+ x0 bw)) :y1 (ndcy y0)
              :color col-panel}
             {:x0 (ndcx x0) :y0 (ndcy (+ y0 3.0)) :x1 (ndcx (+ x0 bw)) :y1 (ndcy y0)
              :color col-accent}]
     :text  (map-indexed
             (fn [i [s scale color]]
               {:text s :x (+ x0 16.0) :y (+ y0 14.0 (* i 26.0)) :scale scale :color color})
             lines)}))

;; ---------------------------------------------------------------------------
;; Widgets
;; ---------------------------------------------------------------------------

(defn stepper-rows-ctx
  "Emit `rows` as 'label · value · [-] [+]' lines starting at framebuffer y
   `y0`, `row-h` apart. Mutates the draw-list atoms in `ctx` and the stepper
   spec determines layout: `px0`, `px1`, `y0`, `row-h`, and `rows`."
  [{:keys [rects text hits rect-fn pad]} {:keys [px0 px1 y0 row-h rows]}]
  (let [lab-scale 1.5
        y0 (double y0)
        row-h (double row-h)]
    (doseq [[i r] (map-indexed vector rows)]
      (let [ry  (+ y0 (* (double i) row-h))
            bsz 22.0
            bx+ (- px1 pad bsz)
            bx- (- bx+ 6.0 bsz)]
        (swap! text conj {:text (:label r) :x (+ px0 pad) :y (+ ry 5.0)
                          :scale lab-scale :color col-dim})
        (swap! text conj {:text (:value r) :x (+ px0 pad 118.0) :y (+ ry 5.0)
                          :scale lab-scale :color col-value})
        (swap! rects conj (rect-fn bx- ry (+ bx- bsz) (+ ry bsz) col-btn))
        (swap! text conj {:text "-" :x (+ bx- 7.0) :y (+ ry 5.0) :scale 1.8 :color col-value})
        (swap! hits conj {:x0 bx- :y0 ry :x1 (+ bx- bsz) :y1 (+ ry bsz) :action (:dec r)})
        (swap! rects conj (rect-fn bx+ ry (+ bx+ bsz) (+ ry bsz) col-btn))
        (swap! text conj {:text "+" :x (+ bx+ 6.0) :y (+ ry 4.0) :scale 1.8 :color col-value})
        (swap! hits conj {:x0 bx+ :y0 ry :x1 (+ bx+ bsz) :y1 (+ ry bsz) :action (:inc r)})))))

(defn bar-tabs-ctx
  "Draw the top bar domain tabs into the draw-list atoms."
  [{:keys [text hits rects rect-fn]} active]
  (loop [ds domains x 12.0]
    (when (seq ds)
      (let [{:keys [id label locked?]} (first ds)
            tw  (+ (text-w label tab-scale) 18.0)
            x1  (+ x tw)
            on? (= id active)
            col (cond on? col-active locked? col-locked :else col-inactive)
            ty  (+ (/ (- bar-h (* 8.0 tab-scale)) 2.0) 1.0)]
        (swap! text conj {:text label :x (+ x 9.0) :y ty :scale tab-scale :color col})
        (when on?
          (swap! rects conj (rect-fn x (- bar-h 3.0) x1 bar-h col-accent)))
        (swap! hits conj {:x0 x :y0 0.0 :x1 x1 :y1 bar-h :action [:ui/toggle-domain id]})
        (recur (rest ds) (+ x1 4.0))))))

(defn view-row
  "Build a stepper row map for a View setting row."
  [cfg r]
  {:label (:label r)
   :value (format (:fmt r) (double (get cfg (:key r) 0.0)))
   :dec   (:dec r)
   :inc   (:inc r)})
