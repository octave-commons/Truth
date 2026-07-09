(ns infra.menu.panels
  "Panel builders for the top menu bar.

   Each function mutates the shared draw-list atoms produced by `infra.menu` to
   lay out the View, Entities, Spark, and read-only domain panels."
  (:require
   [clojure.math :as math]
   [clojure.string :as str]
   [domain.ecs.core :as ecs]
   [domain.ecs.components :as c]
   [domain.player :as player]
   [domain.naming :as naming]
   [domain.stellar.disc :as disc]
   [domain.ecology :as ecology]
   [shape.spatial :as sp]
   [infra.inspect :as inspect]
   [infra.menu.widgets :as w]))

;; ---------------------------------------------------------------------------
;; Read-only domain panels
;; ---------------------------------------------------------------------------

(defn- domain-lines
  "Content lines for a non-View domain panel: [{:text :color}]. Live world data
   where it exists, canonical evocative placeholder text otherwise."
  [id world]
  (case id
    :world
    [{:text (str "Phase 0 — " (w/phase-label (:arc/current world)))}
     {:text "Identity: procedural" :color w/col-dim}
     {:text "Parameters: tendencies only" :color w/col-dim}
     {:text "System bodies — awaiting stable orbits" :color w/col-locked}]

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
        :color w/col-dim}
       {:text "Abilities: Drift · Focus · Influence · Release" :color w/col-dim}
       {:text "Slots locked — witness fusion" :color w/col-locked}]
      [{:text "No spark present" :color w/col-dim}])

    :phase
    [{:text (str "Arc: " (w/phase-label (:arc/current world)))}
     {:text "Next: first fusion → Protostar" :color w/col-dim}
     {:text "Then: ignition → a star is born" :color w/col-dim}
     {:text "States: thriving · sterile · ungated · ghost" :color w/col-locked}]

    :journal
    [{:text "Threshold record —"}
     {:text (or (:arc/observation-note world) "no events recorded yet") :color w/col-dim}
     {:text "Astronomy record — awaiting first body" :color w/col-locked}
     {:text "Mythology — locked" :color w/col-locked}]

    :narrator
    [{:text "Presence: Ambient"}
     {:text (or (some-> (:arc/notification world) :text)
                (:arc/observation-note world)
                "the cloud is quiet")
      :color w/col-dim}
     {:text "Addressable — not yet" :color w/col-locked}]

    :multiverse
    [{:text "Your world has not yet entangled." :color w/col-dim}
     {:text "Gate distance: 0 of 6 thresholds"}
     {:text "Ghost nodes:" :color w/col-dim}
     {:text "  Yeth-Korath — gates open, nothing answers" :color w/col-locked}
     {:text "  Auren-Sel — arrived before us; warm but old" :color w/col-locked}]

    [{:text "—" :color w/col-dim}]))

;; ---------------------------------------------------------------------------
;; Entity list (Entities viewer panel)
;; ---------------------------------------------------------------------------

(defn entity-list
  "Resolved bodies in `world`, sorted by mass descending. Returns a seq of maps
   with :eid, :name, :state, :type-str, :mass-kg, :mass-str, :radius-str and,
   for stars/protostars that carry one, :disk-mass-kg and :disk-radius-m.
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
                                (ecs/get-component world eid c/planet-type))
                    disk-m    (when stellar?
                                (double (or (ecs/get-component world eid c/disk-mass) 0.0)))
                    disk-L    (when (and stellar? (pos? disk-m))
                                (ecs/get-component world eid c/disk-angular-mom))
                    disk-r    (when (and disk-m disk-L (pos? disk-m))
                                (disc/disk-radius (/ (sp/len disk-L) disk-m) mass-kg))]
                (merge
                 {:eid eid
                  :name (naming/body-name eid)
                  :state state
                  :type-str (if ptype
                              (str/replace (name ptype) "-" " ")
                              (or (inspect/state-label state) "—"))
                  :mass-kg mass-kg
                  :mass-str (inspect/fmt-mass mass-kg stellar?)
                  :radius-str (inspect/fmt-radius radius-m stellar?)}
                 (when (and disk-m (pos? disk-m))
                   {:disk-mass-kg disk-m
                    :disk-mass-str (inspect/fmt-mass disk-m false)
                    :disk-radius-m disk-r
                    :disk-radius-str (when (and disk-r (pos? disk-r))
                                       (format "%.2f AU" (/ disk-r inspect/au)))})))))
       (sort-by :mass-kg >)))

(defn living-worlds
  "Planets whose ecology is currently living, sorted by mass descending. Returns
   the same shape as `entity-list` rows so they can share the row renderer."
  [world]
  (->> (ecs/entities-with world c/mass c/radius c/matter-state c/ecology)
       (filter #(= :planet (ecs/get-component world % c/matter-state)))
       (filter #(ecology/living? (ecs/get-component world % c/ecology)))
       (map (fn [eid]
              (let [mass-kg  (double (or (ecs/get-component world eid c/mass) 0.0))
                    radius-m (double (or (ecs/get-component world eid c/radius) 0.0))
                    ptype    (ecs/get-component world eid c/planet-type)
                    eco      (ecs/get-component world eid c/ecology)]
                {:eid eid
                 :name (naming/body-name eid)
                 :state :planet
                 :type-str (str (str/replace (name (or ptype :terrestrial)) "-" " ")
                                 "  |  " (name (:phase eco)))
                 :mass-kg mass-kg
                 :mass-str (inspect/fmt-mass mass-kg false)
                 :radius-str (inspect/fmt-radius radius-m false)})))
       (sort-by :mass-kg >)))

(defn- entity-row-text
  "Single-line label for an entity row, including disk mass/radius when present."
  [{:keys [type-str mass-str radius-str disk-mass-str disk-radius-str] :as row}]
  (str (format "%-12s %-12s %10s  %10s"
               (:name row)
               type-str
               mass-str
               radius-str)
       (when disk-mass-str
         (format "  disk %s  %s" disk-mass-str (or disk-radius-str "—")))))

;; ---------------------------------------------------------------------------
;; Shared panel primitives
;; ---------------------------------------------------------------------------

(defn panel-shell-ctx
  "Draw a panel background rect and register its mouse-capture region."
  [{:keys [rects regions rect-fn]} {:keys [px0 py0 px1 ph]}]
  (swap! rects conj (rect-fn px0 py0 px1 (+ py0 ph) w/col-panel))
  (swap! regions conj {:x0 px0 :y0 py0 :x1 px1 :y1 (+ py0 ph)}))

(defn panel-header-ctx
  "Draw a panel header label."
  [{:keys [text pad]} {:keys [px0 py0 label color]}]
  (swap! text conj {:text label :x (+ px0 pad) :y (+ py0 pad) :scale 1.6 :color color}))

;; ---------------------------------------------------------------------------
;; Layout
;; ---------------------------------------------------------------------------

(defn view-panel-ctx
  "Draw the View camera settings panel."
  [{:keys [w pad text rects hits rect-fn] :as ctx} py0 cfg]
  (let [px1 (- w 12.0)
        px0 (- px1 320.0)
        row-h 30.0 header-h 26.0
        n (inc (count w/view-rows))
        ph (+ (* 2.0 pad) header-h (* n row-h) 4.0)
        rows (mapv #(w/view-row cfg %) w/view-rows)
        ry (+ py0 pad header-h (* (count w/view-rows) row-h))
        bw 104.0 bh 22.0 bx (- px1 pad bw)]
    (panel-shell-ctx ctx {:px0 px0 :py0 py0 :px1 px1 :ph ph})
    (panel-header-ctx ctx {:px0 px0 :py0 py0 :label "VIEW · CAMERA" :color w/col-active})
    (w/stepper-rows-ctx ctx {:px0 px0 :px1 px1 :y0 (+ py0 pad header-h) :row-h row-h :rows rows})
    (swap! text conj {:text "Mode" :x (+ px0 pad) :y (+ ry 5.0) :scale 1.5 :color w/col-dim})
    (swap! rects conj (rect-fn bx ry (+ bx bw) (+ ry bh) w/col-btn))
    (swap! text conj {:text (name (:mode cfg :manual)) :x (+ bx 8.0) :y (+ ry 5.0) :scale 1.3 :color w/col-value})
    (swap! hits conj {:x0 bx :y0 ry :x1 (+ bx bw) :y1 (+ ry bh) :action [:camera/cycle-mode]})))

(defn entities-panel-ctx
  "Draw the Entities list panel, including a clickable Living Worlds section
   at the top."
  [{:keys [w pad text hits] :as ctx} py0 world]
  (let [row-h 22.0 header-h 26.0 sub-h 20.0
        pw 520.0
        px1 (- w 12.0)
        px0 (- px1 pw)
        living (take 8 (living-worlds world))
        entities (take 40 (entity-list world))
        living-rows (count living)
        body-rows (count entities)
        gap (if (seq living) 8.0 0.0)
        ph (+ (* 2.0 pad) header-h
              (* living-rows row-h) (if (seq living) sub-h 0.0)
              gap
              (* body-rows row-h))]
    (panel-shell-ctx ctx {:px0 px0 :py0 py0 :px1 px1 :ph ph})
    (panel-header-ctx ctx {:px0 px0 :py0 py0 :label "ENTITIES" :color w/col-active})
    (when (seq living)
      (swap! text conj {:text "LIVING WORLDS — click to follow"
                        :x (+ px0 pad) :y (+ py0 pad 16.0)
                        :scale 1.3 :color w/col-accent})
      (doseq [[i ent] (map-indexed vector living)]
        (let [ry (+ py0 pad sub-h header-h (* i row-h))]
          (swap! text conj {:text (str "  " (entity-row-text ent))
                            :x (+ px0 pad) :y ry
                            :scale 1.3 :color w/col-value})
          (swap! hits conj {:x0 px0 :y0 ry :x1 px1 :y1 (+ ry row-h)
                            :action [:ui/select-entity (:eid ent)]}))))
    (let [body-y0 (+ py0 pad header-h (if (seq living) (+ sub-h (* living-rows row-h) gap) 0.0))]
      (swap! text conj {:text "name         kind             mass      radius      disk mass  disk radius"
                        :x (+ px0 pad) :y (+ body-y0 4.0)
                        :scale 1.2 :color w/col-dim})
      (doseq [[i ent] (map-indexed vector entities)]
        (let [ry (+ body-y0 18.0 (* i row-h))]
          (swap! text conj {:text (entity-row-text ent)
                            :x (+ px0 pad) :y ry
                            :scale 1.3 :color w/col-value})
          (swap! hits conj {:x0 px0 :y0 ry :x1 px1 :y1 (+ ry row-h)
                            :action [:ui/select-entity (:eid ent)]}))))))

(defn- spark-info-lines
  "Readout lines for the Spark panel when an observer is present."
  [world obs]
  (let [{:keys [ref-mass dv-cap]} (player/influence-reference world)
        kf (double (or (:genesis/observer-halo-mass-factor world)
                       player/default-halo-mass-factor))
        halo (player/halo-mass obs kf ref-mass)]
    [{:text (format "Coherence %.2f    Agency %d    Resonance %d"
                    (double (or (:coherence obs) 0.0))
                    (long (math/floor (double (or (:agency obs) 0.0))))
                    (long (math/floor (double (or (:resonance obs) 0.0)))))}
     {:text (format "Halo %.2e kg" halo) :color w/col-dim}
     {:text (format "Reach %.1e m   dv cap %.0f m/s"
                    (* player/halo-reach-factor
                       (double (or (:focus-radius obs) 0.0)))
                    (double dv-cap))
      :color w/col-dim}]))

(defn- spark-knob-rows
  "Build stepper row maps for the Spark influence knobs."
  [world obs]
  (mapv (fn [k]
          {:label (:label k)
           :value (format (:fmt k) (w/knob-value k world obs))
           :dec   (w/knob-action k :down)
           :inc   (w/knob-action k :up)})
        w/spark-knobs))

(defn spark-panel-ctx
  "Draw the Spark influence panel."
  [{:keys [w pad text] :as ctx} py0 world obs]
  (let [px1 (- w 12.0)
        px0 (- px1 320.0)
        row-h 30.0 header-h 26.0 line-h 22.0
        info (spark-info-lines world obs)
        rows (spark-knob-rows world obs)
        info-h (* line-h (count info))
        ph (+ (* 2.0 pad) header-h info-h (* (count rows) row-h) 4.0)]
    (panel-shell-ctx ctx {:px0 px0 :py0 py0 :px1 px1 :ph ph})
    (panel-header-ctx ctx {:px0 px0 :py0 py0 :label "SPARK · INFLUENCE" :color w/col-active})
    (doseq [[i ln] (map-indexed vector info)]
      (swap! text conj {:text (:text ln)
                        :x (+ px0 pad) :y (+ py0 pad header-h (* i line-h))
                        :scale 1.3 :color (or (:color ln) w/col-value)}))
    (w/stepper-rows-ctx ctx {:px0 px0 :px1 px1 :y0 (+ py0 pad header-h info-h)
                             :row-h row-h :rows rows})))

(defn read-only-panel-ctx
  "Draw a generic read-only domain panel."
  [{:keys [w pad text] :as ctx} py0 active world]
  (let [px1 (- w 12.0)
        px0 (- px1 320.0)
        lines (domain-lines active world)
        dom (first (filter #(= (:id %) active) w/domains))
        ph (+ (* 2.0 pad) 26.0 (* (count lines) 22.0))]
    (panel-shell-ctx ctx {:px0 px0 :py0 py0 :px1 px1 :ph ph})
    (panel-header-ctx ctx {:px0 px0 :py0 py0 :label (str/upper-case (:label dom))
                           :color (if (:locked? dom) w/col-locked w/col-active)})
    (doseq [[i ln] (map-indexed vector lines)]
      (swap! text conj {:text (:text ln)
                        :x (+ px0 pad) :y (+ py0 pad 26.0 (* i 22.0))
                        :scale 1.3 :color (or (:color ln) w/col-value)}))))
