(ns infra.menu
  "Top menu bar and sub-view panels — the shell's domain navigation.

   Pure layout. `menu-hud` returns {:rects :text :hits :regions}:
     :rects / :text  NDC / pixel draw lists consumed by infra.render
     :hits           framebuffer-pixel click targets {:x0 :y0 :x1 :y1 :action}
     :regions        framebuffer-pixel rects that capture the mouse (bar + open
                     panel), so the window loop can suppress world hover/pick there

   The window loop resolves a click by finding the :hit under the cursor and
   folding its :action into the config with `apply-action`. The canonical domain
   set and their Phase-0 availability follow docs/designs/ux-architecture.md — all
   seven domains are always shown; locked ones are dimmed but still open an
   (evocative) read-only panel (rule 10: always show what you cannot yet reach)."
  (:require
   [clojure.math :as math]
   [clojure.string :as str]
   [domain.ecs.core :as ecs]
   [domain.ecs.components :as c]
   [domain.intervention :as intervention]
   [domain.naming :as naming]
   [domain.player :as player]
   [infra.camera :as cam]
   [infra.inspect :as inspect]))

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
(def ^:private col-bar      [0.03 0.05 0.10 0.92])
(def ^:private col-panel    [0.05 0.07 0.13 0.95])
(def ^:private col-btn      [0.13 0.20 0.34 0.98])
(def ^:private col-active   [0.80 0.92 1.0 1.0])
(def ^:private col-inactive [0.62 0.72 0.86 0.90])
(def ^:private col-locked   [0.42 0.47 0.58 0.60])
(def ^:private col-accent   [0.45 0.85 1.0 1.0])
(def ^:private col-value    [0.88 0.95 1.0 0.98])
(def ^:private col-dim      [0.60 0.70 0.84 0.85])

(defn- text-w ^double [s ^double scale]
  ;; STBEasyFont advances ~6px per glyph at the base size; good enough for layout.
  (* 6.0 scale (count (str s))))

(def ^:private phase-names
  {:arc/genesis-nebula-collapse "Nebula collapsing"
   :arc/genesis-protostar       "Protostar forming"
   :arc/genesis-ignition        "Ignition"
   :arc/genesis-accretion       "Accretion"
   :arc/genesis-planets-formed  "Planets formed"
   :arc/life-emergence          "Life emerges"
   :arc/genesis-dispersed       "Dispersed"})

(defn- phase-label [arc]
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
    :inc [:setting/scale :zoom-sensitivity 1.25 1.0 200.0]}
   {:label "Move speed" :key :move-speed :fmt "%.1e"
    :dec [:setting/scale :move-speed 0.5 1.0e13 1.0e17]
    :inc [:setting/scale :move-speed 2.0 1.0e13 1.0e17]}])

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
    :mode :scale :down 0.5 :up 2.0 :lo 0.001 :hi 0.5}])

(defn- knob-action
  "The [:spark/knob ...] menu action for stepping knob `k` in `direction`
   (:down / :up)."
  [{:keys [scope key dflt mode lo hi] :as k} direction]
  [:spark/knob scope key (or dflt lo) mode (get k direction) lo hi])

(defn- knob-value
  "Live value of a spark knob: observer knobs read the observer map, world
   knobs the :genesis/* key (falling back to the domain default)."
  ^double [{:keys [scope key dflt lo]} world obs]
  (double (or (if (= scope :observer) (get obs key) (get world key))
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
    (let [eid (second action)]
      (if eid
        (assoc cfg :selection eid :mode :follow-selection :follow-eid eid)
        (-> cfg
            (dissoc :selection :follow-eid :zoom-min)
            (cond-> (= :follow-selection (:mode cfg)) (assoc :mode :manual)))))

    :setting/scale
    (let [[_ k factor lo hi] action
          dflt (get (cam/default-camera-settings) k 1.0)
          cur  (double (get cfg k dflt))]
      (assoc cfg k (max (double lo) (min (double hi) (* cur (double factor))))))

    :camera/cycle-mode
    (cam/cycle-camera-mode cfg)

    cfg))

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
;; Read-only domain panels
;; ---------------------------------------------------------------------------

(defn- domain-lines
  "Content lines for a non-View domain panel: [{:text :color}]. Live world data
   where it exists, canonical evocative placeholder text otherwise."
  [id world]
  (case id
    :world
    [{:text (str "Phase 0 — " (phase-label (:arc/current world)))}
     {:text "Identity: procedural" :color col-dim}
     {:text "Parameters: tendencies only" :color col-dim}
     {:text "System bodies — awaiting stable orbits" :color col-locked}]

    :spark
    (if-let [obs (player/get-observer world)]
      [{:text (format "Coherence  %.0f / %.0f"
                      (double (or (:coherence obs) 0.0))
                      (double (or (:max-coherence obs) 1.0)))}
       {:text (format "Agency  %d quanta"
                      (long (math/floor (double (or (:agency obs) 0.0)))))}
       {:text (format "Resonance  %d"
                      (long (math/floor (double (or (:resonance obs) 0.0)))))}
       {:text (format "Focus  %.0f%%" (* 100.0 (double (or (:focus-intensity obs) 0.5))))
        :color col-dim}
       {:text "Abilities: Drift · Focus · Influence · Release" :color col-dim}
       {:text "Slots locked — witness fusion" :color col-locked}]
      [{:text "No spark present" :color col-dim}])

    :phase
    [{:text (str "Arc: " (phase-label (:arc/current world)))}
     {:text "Next: first fusion → Protostar" :color col-dim}
     {:text "Then: ignition → a star is born" :color col-dim}
     {:text "States: thriving · sterile · ungated · ghost" :color col-locked}]

    :journal
    [{:text "Threshold record —"}
     {:text (or (:arc/observation-note world) "no events recorded yet") :color col-dim}
     {:text "Astronomy record — awaiting first body" :color col-locked}
     {:text "Mythology — locked" :color col-locked}]

    :narrator
    [{:text "Presence: Ambient"}
     {:text (or (some-> (:arc/notification world) :text)
                (:arc/observation-note world)
                "the cloud is quiet")
      :color col-dim}
     {:text "Addressable — not yet" :color col-locked}]

    :multiverse
    [{:text "Your world has not yet entangled." :color col-dim}
     {:text "Gate distance: 0 of 6 thresholds"}
     {:text "Ghost nodes:" :color col-dim}
     {:text "  Yeth-Korath — gates open, nothing answers" :color col-locked}
     {:text "  Auren-Sel — arrived before us; warm but old" :color col-locked}]

    [{:text "—" :color col-dim}]))

;; ---------------------------------------------------------------------------
;; Entity list (Entities viewer panel)
;; ---------------------------------------------------------------------------

(defn entity-list
  "Resolved bodies in `world`, sorted by mass descending. Returns a seq of maps
   with :eid, :name, :state, :type-str, :mass-kg, :mass-str and :radius-str.
   Planets show their planet-type; everything else shows its matter-state."
  [world]
  (->> (ecs/entities-with world c/mass c/radius c/matter-state)
       (remove #(= :nebula (ecs/get-component world % c/matter-state)))
       (map (fn [eid]
              (let [state     (ecs/get-component world eid c/matter-state)
                    stellar?  (#{:star :protostar} state)
                    mass-kg   (double (or (ecs/get-component world eid c/mass) 0.0))
                    radius-m  (double (or (ecs/get-component world eid c/radius) 0.0))
                    ptype     (when (= :planet state)
                                (ecs/get-component world eid c/planet-type))]
                {:eid eid
                 :name (naming/body-name eid)
                 :state state
                 :type-str (if ptype
                             (str/replace (name ptype) "-" " ")
                             (or (inspect/state-label state) "—"))
                 :mass-kg mass-kg
                 :mass-str (inspect/fmt-mass mass-kg stellar?)
                 :radius-str (inspect/fmt-radius radius-m stellar?)})))
       (sort-by :mass-kg >)))

(defn- entity-row-text
  "Single-line label for an entity row."
  [{:keys [name type-str mass-str radius-str]}]
  (format "%-12s %-12s %10s  %10s"
          name
          type-str
          mass-str
          radius-str))

;; ---------------------------------------------------------------------------
;; Layout
;; ---------------------------------------------------------------------------

(defn- stepper-rows!
  "Emit `rows` — {:label :value :dec :inc} — as 'label · value · [-] [+]' lines
   starting at framebuffer y `y0`, `row-h` apart. Mutates the draw-list atoms
   the panel layout accumulates into (shared by the View and Spark panels)."
  [rects text hits rect px0 px1 pad y0 row-h rows]
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
        (swap! rects conj (rect bx- ry (+ bx- bsz) (+ ry bsz) col-btn))
        (swap! text conj {:text "-" :x (+ bx- 7.0) :y (+ ry 5.0) :scale 1.8 :color col-value})
        (swap! hits conj {:x0 bx- :y0 ry :x1 (+ bx- bsz) :y1 (+ ry bsz) :action (:dec r)})
        (swap! rects conj (rect bx+ ry (+ bx+ bsz) (+ ry bsz) col-btn))
        (swap! text conj {:text "+" :x (+ bx+ 6.0) :y (+ ry 4.0) :scale 1.8 :color col-value})
        (swap! hits conj {:x0 bx+ :y0 ry :x1 (+ bx+ bsz) :y1 (+ ry bsz) :action (:inc r)})))))

(defn menu-hud
  "Lay out the top bar and the open sub-view panel for the current `cfg` and
   `world` over an `w`×`h` framebuffer. Returns {:rects :text :hits :regions}."
  [cfg world ^double w ^double h]
  (let [ndcx (fn [px] (- (/ (* 2.0 (double px)) w) 1.0))
        ndcy (fn [py] (- 1.0 (/ (* 2.0 (double py)) h)))
        rect (fn [x0 y0 x1 y1 color]
               {:x0 (ndcx x0) :y0 (ndcy y0) :x1 (ndcx x1) :y1 (ndcy y1) :color color})
        active    (:ui/active-domain cfg)
        tab-scale 1.6
        rects   (atom [(rect 0.0 0.0 w bar-h col-bar)])
        text    (atom [])
        hits    (atom [])
        regions (atom [{:x0 0.0 :y0 0.0 :x1 w :y1 bar-h}])]
    ;; --- tabs ---
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
            (swap! rects conj (rect x (- bar-h 3.0) x1 bar-h col-accent)))
          (swap! hits conj {:x0 x :y0 0.0 :x1 x1 :y1 bar-h :action [:ui/toggle-domain id]})
          (recur (rest ds) (+ x1 4.0)))))
    ;; --- open panel (anchored top-right to avoid the top-left stats text) ---
    (when active
      (let [pad 12.0 pw 320.0
            px1 (- w 12.0)
            px0 (- px1 pw)
            py0 (+ bar-h 8.0)]
        (cond
          (= active :view)
          (let [row-h 30.0 lab-scale 1.5 header-h 26.0
                n  (inc (count view-rows))           ; setting rows + mode row
                ph (+ (* 2.0 pad) header-h (* n row-h) 4.0)]
            (swap! rects conj (rect px0 py0 px1 (+ py0 ph) col-panel))
            (swap! regions conj {:x0 px0 :y0 py0 :x1 px1 :y1 (+ py0 ph)})
            (swap! text conj {:text "VIEW · CAMERA" :x (+ px0 pad) :y (+ py0 pad)
                              :scale 1.6 :color col-active})
            (stepper-rows! rects text hits rect px0 px1 pad (+ py0 pad header-h) row-h
                           (mapv (fn [r]
                                   {:label (:label r)
                                    :value (format (:fmt r) (double (get cfg (:key r) 0.0)))
                                    :dec   (:dec r)
                                    :inc   (:inc r)})
                                 view-rows))
            (let [ry (+ py0 pad header-h (* (count view-rows) row-h))
                  bw 104.0 bh 22.0 bx (- px1 pad bw)]
              (swap! text conj {:text "Mode" :x (+ px0 pad) :y (+ ry 5.0)
                                :scale lab-scale :color col-dim})
              (swap! rects conj (rect bx ry (+ bx bw) (+ ry bh) col-btn))
              (swap! text conj {:text (name (:mode cfg :manual)) :x (+ bx 8.0) :y (+ ry 5.0)
                                :scale 1.3 :color col-value})
              (swap! hits conj {:x0 bx :y0 ry :x1 (+ bx bw) :y1 (+ ry bh)
                                :action [:camera/cycle-mode]})))

          (= active :entities)
          (let [row-h 22.0 header-h 26.0
                entities (take 40 (entity-list world))
                ph (+ (* 2.0 pad) header-h (* (count entities) row-h))]
            (swap! rects conj (rect px0 py0 px1 (+ py0 ph) col-panel))
            (swap! regions conj {:x0 px0 :y0 py0 :x1 px1 :y1 (+ py0 ph)})
            (swap! text conj {:text "ENTITIES" :x (+ px0 pad) :y (+ py0 pad)
                              :scale 1.6 :color col-active})
            (swap! text conj {:text "name         kind             mass      radius"
                              :x (+ px0 pad) :y (+ py0 pad 18.0)
                              :scale 1.2 :color col-dim})
            (doseq [[i ent] (map-indexed vector entities)]
              (let [ry (+ py0 pad header-h (* i row-h))]
                (swap! text conj {:text (entity-row-text ent)
                                  :x (+ px0 pad) :y ry
                                  :scale 1.3 :color col-value})
                (swap! hits conj {:x0 px0 :y0 ry :x1 px1 :y1 (+ ry row-h)
                                  :action [:ui/select-entity (:eid ent)]}))))

          ;; Spark: live influence knobs (falls through to the read-only panel
          ;; when no observer has been spawned).
          (and (= active :spark) (some? (player/get-observer world)))
          (let [obs    (player/get-observer world)
                row-h  30.0 header-h 26.0 line-h 22.0
                {:keys [ref-mass dv-cap]} (player/influence-reference world)
                kf     (double (or (:genesis/observer-halo-mass-factor world)
                                   player/default-halo-mass-factor))
                halo   (player/halo-mass obs kf ref-mass)
                info   [{:text (format "Coherence %.2f    Agency %d    Resonance %d"
                                       (double (or (:coherence obs) 0.0))
                                       (long (math/floor (double (or (:agency obs) 0.0))))
                                       (long (math/floor (double (or (:resonance obs) 0.0)))))}
                        {:text (format "Halo %.2e kg" halo) :color col-dim}
                        {:text (format "Reach %.1e m   dv cap %.0f m/s"
                                       (* player/halo-reach-factor
                                          (double (or (:focus-radius obs) 0.0)))
                                       (double dv-cap))
                         :color col-dim}]
                rows   (mapv (fn [k]
                               {:label (:label k)
                                :value (format (:fmt k) (knob-value k world obs))
                                :dec   (knob-action k :down)
                                :inc   (knob-action k :up)})
                             spark-knobs)
                info-h (* line-h (count info))
                ph     (+ (* 2.0 pad) header-h info-h (* (count rows) row-h) 4.0)]
            (swap! rects conj (rect px0 py0 px1 (+ py0 ph) col-panel))
            (swap! regions conj {:x0 px0 :y0 py0 :x1 px1 :y1 (+ py0 ph)})
            (swap! text conj {:text "SPARK · INFLUENCE" :x (+ px0 pad) :y (+ py0 pad)
                              :scale 1.6 :color col-active})
            (doseq [[i ln] (map-indexed vector info)]
              (swap! text conj {:text (:text ln)
                                :x (+ px0 pad) :y (+ py0 pad header-h (* i line-h))
                                :scale 1.3 :color (or (:color ln) col-value)}))
            (stepper-rows! rects text hits rect px0 px1 pad
                           (+ py0 pad header-h info-h) row-h rows))

          :else
              ;; read-only domain panel
          (let [lines (domain-lines active world)
                dom   (first (filter #(= (:id %) active) domains))
                ph    (+ (* 2.0 pad) 26.0 (* (count lines) 22.0))]
            (swap! rects conj (rect px0 py0 px1 (+ py0 ph) col-panel))
            (swap! regions conj {:x0 px0 :y0 py0 :x1 px1 :y1 (+ py0 ph)})
            (swap! text conj {:text (str/upper-case (:label dom))
                              :x (+ px0 pad) :y (+ py0 pad) :scale 1.6
                              :color (if (:locked? dom) col-locked col-active)})
            (doseq [[i ln] (map-indexed vector lines)]
              (swap! text conj {:text (:text ln)
                                :x (+ px0 pad) :y (+ py0 pad 26.0 (* i 22.0))
                                :scale 1.3 :color (or (:color ln) col-value)}))))))
    {:rects @rects :text @text :hits @hits :regions @regions}))

(defn hit-at
  "The first hit region containing framebuffer point (x, y), or nil."
  [hits ^double x ^double y]
  (some (fn [{:keys [x0 y0 x1 y1] :as h}]
          (when (and (>= x (double x0)) (<= x (double x1))
                     (>= y (double y0)) (<= y (double y1)))
            h))
        hits))

(defn over-regions?
  "True when framebuffer point (x, y) falls inside any mouse-capture region."
  [regions ^double x ^double y]
  (boolean
   (some (fn [{:keys [x0 y0 x1 y1]}]
           (and (>= x (double x0)) (<= x (double x1))
                (>= y (double y0)) (<= y (double y1))))
         regions)))
