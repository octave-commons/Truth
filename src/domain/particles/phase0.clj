(ns domain.particles.phase0
  "Particle-based Phase 0: a statistical gas cloud that collapses under its own
   gravity, fragments into protostars, and flattens into a disk through
   inelastic accretion.

   This is the high-scale representation of stellar formation. The gas cloud is
   a flat primitive-array field (domain.particles.field) accelerated by a
   particle-mesh Poisson solver. Massive accreted clumps are promoted to resolved
   ECS entities so the same rendering, observer, and habitability machinery can
   attach to them.

   Units are natural: G = 1, cloud radius ~ O(10), particle mass ~ O(0.001),
   so collapse happens in hundreds of steps and is watchable."
  (:require
   [domain.particles.field  :as field]
   [domain.particles.pm     :as pm]
   [domain.ecs.core         :as ecs]
   [domain.ecs.components   :as c]
   [domain.ecs.event        :as event]
   [domain.player           :as player]
   [domain.stellar          :as stellar]
   [domain.physics.collision :as collision]
   [law.stellar             :as law]
   [shape.spatial           :as sp]))

;; --- Natural-unit defaults --------------------------------------------------

(def ^:const default-cap 2048)
(def ^:const default-grid 32)
(def ^:const default-box 40.0)
(def ^:const default-cloud-r 10.0)
(def ^:const default-particle-mass 0.001)
(def ^:const default-r0 0.3)
(def ^:const default-m0 0.001)
(def ^:const default-dt 0.3)
(def ^:const default-spin 0.04)
(def ^:const default-turb 0.05)
(def ^:const default-seeds 4)
(def ^:const default-seed-r 2.0)
(def ^:const sink-threshold 0.02)
(def ^:const debris-threshold 0.02)
(def ^:const planet-threshold 0.05)
(def ^:const star-threshold 0.12)
(def ^:const si-mass-scale 1.0e30)

;; --- World construction -----------------------------------------------------

(defn create-world
  "Bootstrap a Phase 0 world backed by a particle gas cloud.

   Mass hierarchy (natural units, scaled by `si-mass-scale` for downstream ECS):
     < sink-threshold      : diffuse gas particle (unpromoted)
     sink / debris         : unrounded debris clump
     planet-threshold      : hydrostatically rounded planet
     star-threshold        : hydrogen-burning star

   Options:
     :cap              particle capacity (default 2048)
     :grid             PM grid side, power of two (default 32)
     :box              physical box size in natural units (default 40)
     :cloud-r          initial cloud radius (default 10)
     :particle-mass    mass per gas particle (default 0.001)
     :r0               visual radius scale (default 0.3)
     :m0               visual radius mass reference (default 0.001)
     :dt               simulation step size (default 0.3)
     :spin             initial solid-body rotation ω about z (default 0.04)
     :turb             initial turbulent dispersion (default 0.05)
     :n-seeds          number of density seeds for fragmentation (default 4)
     :seed-r           Gaussian spread of each seed (default 2.0)
     :rng              java.util.Random for reproducibility"
  ([] (create-world {}))
  ([{:keys [cap grid box cloud-r particle-mass r0 m0 dt
             spin turb n-seeds seed-r rng]
      :or   {cap default-cap grid default-grid box default-box
             cloud-r default-cloud-r particle-mass default-particle-mass
             r0 default-r0 m0 default-m0 dt default-dt
             spin default-spin turb default-turb
             n-seeds default-seeds seed-r default-seed-r}}]
    (let [rng (or rng (java.util.Random. 42))
          f   (field/make-field cap r0 m0)
          _   (field/seed-cloud! f {:n cap :cloud-r cloud-r :spin spin :turb turb
                                    :particle-mass particle-mass
                                    :n-seeds n-seeds :seed-r seed-r :rng rng})
          mesh (pm/make-mesh grid box 1.0)]
      (-> (ecs/empty-world)
          (event/with-ledger)
          (assoc :phase0/mode            :particle
                 :phase0/field           f
                 :phase0/mesh            mesh
                 :phase0/dt              dt
                 :phase0/cloud-r         cloud-r
                 :phase0/sim-time        0.0
                 :phase0/complexity      0
                 :phase0/phase           :phase-0/nebula-collapse
                 :phase0/active          true
                 :phase0/sink-threshold  sink-threshold
                 :phase0/debris-threshold debris-threshold
                 :phase0/planet-threshold planet-threshold
                 :phase0/star-threshold  star-threshold)
          (#(first (player/spawn-observer % (sp/vec3 0 0 (* cloud-r 3)))))))))

;; --- Promotion of massive particles to resolved bodies ----------------------

(defn- promote-sink
  "Spawn a resolved ECS body from a particle sink. Mass regimes follow the
   stellar-formation hierarchy: debris (unrounded collections), planets
   (hydrostatically rounded), and stars (massive enough to ignite)."
  [world {:keys [mass position velocity radius]}]
  (let [state (cond
                (>= mass star-threshold) :star
                (>= mass planet-threshold) :planet
                (>= mass sink-threshold) :debris
                :else :nebula)
        temp (case state
               :star law/fusion-temp-threshold
               :planet (max 50.0 (* 200.0 mass))
               :debris (max 20.0 (* 30.0 mass))
               10.0)
        kind (case state
               :star :body/star
               :planet :body/planet
               :debris :body/debris
               :body/gas)]
    (stellar/spawn-clump world
      {:position     position
       :velocity     velocity
       :mass         (* mass si-mass-scale)
       :radius       (max radius 0.1)
       :temperature  temp
       :composition  (if (= state :star)
                       {:H 0.75 :He 0.24 :metals 0.01}
                       {:H 0.4 :He 0.1 :O 0.2 :Si 0.15 :Fe 0.1 :metals 0.05})
       :matter-state state
       :body-kind    kind})))

(defn- classify-from-mass
  "Classify a resolved body by its natural-unit mass."
  [mass]
  (cond
    (>= mass star-threshold)   :star
    (>= mass planet-threshold) :planet
    (>= mass sink-threshold)   :debris
    :else                      :nebula))

(defn- merge-components
  "Mass-weighted merge of two resolved bodies, re-classifying the result by
   total natural-unit mass. Returns the component map for the survivor."
  [world big small]
  (let [ma (double (ecs/get-component world big c/mass))
        mb (double (ecs/get-component world small c/mass))
        total (+ ma mb)
        nat-mass (/ total si-mass-scale)
        state (classify-from-mass nat-mass)
        va (ecs/get-component world big c/velocity)
        vb (ecs/get-component world small c/velocity)
        v' (let [px (+ (* (nth va 0) ma) (* (nth vb 0) mb))
                 py (+ (* (nth va 1) ma) (* (nth vb 1) mb))
                 pz (+ (* (nth va 2) ma) (* (nth vb 2) mb))]
             [(/ px total) (/ py total) (/ pz total)])
        ra (double (ecs/get-component world big c/radius))
        rb (double (ecs/get-component world small c/radius))
        r' (Math/cbrt (+ (* ra ra ra) (* rb rb rb)))
        comp-a (or (ecs/get-component world big c/composition) {})
        comp-b (or (ecs/get-component world small c/composition) {})
        comp' (into {} (for [k (into (set (keys comp-a)) (keys comp-b))]
                         [k (/ (+ (* (get comp-a k 0.0) ma)
                                  (* (get comp-b k 0.0) mb))
                               total)]))
        temp (case state
               :star law/fusion-temp-threshold
               :planet (max 50.0 (* 200.0 nat-mass))
               :debris (max 20.0 (* 30.0 nat-mass))
               10.0)
        kind (case state
               :star :body/star
               :planet :body/planet
               :debris :body/debris
               :body/gas)]
    {c/position     (let [[ax ay az] (ecs/get-component world big c/position)
                          [bx by bz] (ecs/get-component world small c/position)]
                      [(/ (+ (* ax ma) (* bx mb)) total)
                       (/ (+ (* ay ma) (* by mb)) total)
                       (/ (+ (* az ma) (* bz mb)) total)])
     c/velocity     v'
     c/mass         total
     c/radius       r'
     c/body-kind    kind
     c/temperature  temp
     c/density      (stellar/body-density total r')
     c/pressure     (stellar/ideal-gas-pressure (stellar/body-density total r') temp)
     c/composition  comp'
     c/luminosity   (if (= state :star) (stellar/luminosity-from-fusion
                                          (stellar/fusion-rate
                                           {:temperature temp
                                            :pressure (stellar/ideal-gas-pressure (stellar/body-density total r') temp)
                                            :composition comp'
                                            :density (stellar/body-density total r')})
                                          r')
                      0.0)
     c/matter-state state}))

(defn particle-merge-handler
  "Collision handler for resolved bodies promoted from the particle field.
   Merges the smaller body into the larger and re-classifies by total mass."
  [world event]
  (let [{:keys [eid-a eid-b]} (:payload event)]
    (if (and (ecs/alive? world eid-a) (ecs/alive? world eid-b))
      (let [ma (double (or (ecs/get-component world eid-a c/mass) 0.0))
            mb (double (or (ecs/get-component world eid-b c/mass) 0.0))
            [big small] (if (>= ma mb) [eid-a eid-b] [eid-b eid-a])]
        (-> world
            (ecs/put-components big (merge-components world big small))
            (ecs/despawn small)))
      world)))

(defn- merge-resolved-bodies!
  "Detect overlapping resolved bodies and merge them. Lets debris clumps grow
   into planets and planets into stars."
  [world]
  (-> world
      (event/register-handler :event/collision particle-merge-handler)
      (collision/collision-detection-system)))

(defn promote-sinks!
  "Promote any particle whose mass exceeds the sink threshold into a resolved
   ECS body, and remove the particle from the field (mass = 0). Returns world'."
  [world]
  (let [f (get world :phase0/field)
        threshold (double (get world :phase0/sink-threshold sink-threshold))
        ^doubles px (.px f) ^doubles py (.py f) ^doubles pz (.pz f)
        ^doubles vx (.vx f) ^doubles vy (.vy f) ^doubles vz (.vz f)
        ^doubles mass (.mass f) ^doubles radius (.radius f)]
    (loop [w world sinks (field/sink-particles f threshold)]
      (if-let [[[idx m] & rest] (seq sinks)]
        (let [spec {:index idx :mass m
                    :position [(aget px idx) (aget py idx) (aget pz idx)]
                    :velocity [(aget vx idx) (aget vy idx) (aget vz idx)]
                    :radius   (aget radius idx)}]
          (aset mass idx 0.0) ;; remove from field
          (recur (first (promote-sink w spec)) rest))
        w))))

;; --- Observable summary -----------------------------------------------------

(defn system-summary
  "Tally resolved bodies promoted from the particle field."
  [world]
  (let [eids    (ecs/entities-with world c/matter-state c/mass)
        regions (mapv #(stellar/entity->region world %) eids)
        stars   (filterv #(= :star (:matter-state %)) regions)
        non-stars (remove #(= :star (:matter-state %)) regions)
        planets (filterv #(= :planet (:matter-state %)) non-stars)]
    {:body-count   (count regions)
     :star?        (boolean (seq stars))
     :fusion?      (boolean (seq stars))
     :planet-count (count planets)
     :stars        stars
     :planets      planets
     :regions      regions
     :live-particles (field/live-count (:phase0/field world))}))

(defn detect-phase
  "Current phase from resolved bodies and live particles."
  [{:keys [star? planet-count body-count live-particles]}]
  (cond
    (and star? (pos? planet-count)) :phase-0/planets-formed
    (and star? (>= body-count 2))   :phase-0/accretion
    star?                           :phase-0/ignition
    (pos? body-count)               :phase-0/protostar
    (zero? live-particles)          :phase-0/dispersed
    :else                           :phase-0/nebula-collapse))

;; --- Tick driver ------------------------------------------------------------

(defn- emit-threshold
  "Emit a threshold event into the ledger at the world's current tick."
  [world kind data]
  (event/dispatch world
    (event/->event {:tick     (:tick world)
                    :kind     kind
                    :entities #{}
                    :payload  {:data data}})))

(defn tick-world
  "Advance the particle Phase 0 world by one step."
  [world]
  (if-not (:phase0/active world)
    world
    (let [dt       (:phase0/dt world)
          f        (:phase0/field world)
          mesh     (:phase0/mesh world)
          prev     (system-summary world)
          prev-phase (:phase0/phase world)
          _        (field/step! f mesh dt {:merge-cell 1.0})
          world1   (-> world
                       (promote-sinks!)
                       (merge-resolved-bodies!))
          summ     (system-summary world1)
          phase    (detect-phase summ)
          world2   (cond-> world1
                     (and (:star? summ) (not (:star? prev)))
                     (emit-threshold :event/stellar-ignition (first (:stars summ)))

                     (> (:planet-count summ) (:planet-count prev))
                     (emit-threshold :event/planet-formation (first (:planets summ)))

                     (not= phase prev-phase)
                     (emit-threshold :event/phase-transition {:from prev-phase :to phase}))
          world3   (assoc world2
                     :phase0/complexity (+ (:body-count summ) (* 0.01 (field/live-count f)))
                     :phase0/phase      phase
                     :phase0/sim-time   (+ (:phase0/sim-time world2) dt))
          world4   ((player/observer-system dt) world3)
          obs      (player/get-observer world4)]
      (assoc world4 :phase0/active
             (and (player/can-interact? obs)
                  (not= phase :phase-0/dispersed)
                  (< (:phase0/sim-time world4) 1e6))))))

;; --- Player input -----------------------------------------------------------

(defn handle-input
  "Apply a player control to the world's observer."
  [world input-type & args]
  (case input-type
    :narrow-focus (player/update-observer world #(player/narrow-focus % 2.0))
    :widen-focus  (player/update-observer world #(player/widen-focus % 2.0))
    :move-focus   (let [[pos] args]
                    (player/update-observer world
                      #(player/set-focus % pos (:focus-radius %) (:focus-intensity %))))
    world))

;; --- Endings ----------------------------------------------------------------

(defn world-ending
  "If the world has reached a terminal state, describe it; else nil."
  [world]
  (let [phase (:phase0/phase world)
        obs   (player/get-observer world)]
    (cond
      (= phase :phase-0/planets-formed)
      {:type :success
       :message "A planetary system has emerged from the cloud."}

      (and obs (not (player/can-interact? obs)))
      {:type :fadeout
       :message "You dissolve back into the quantum foam."}

      (= phase :phase-0/dispersed)
      {:type :dispersal
       :message "The nebula disperses. No stars form here."}

      :else nil)))

;; --- Render projection ------------------------------------------------------

(defn particle-bodies
  "Project live particles into the stylised render shape used by infra.render.
   Gas particles are rendered as large diffuse fog clouds — the dominant visual
   element of the nebula."
  [world]
  (let [f (:phase0/field world)
        ^doubles px (.px f) ^doubles py (.py f) ^doubles pz (.pz f)
        ^doubles mass (.mass f) ^doubles radius (.radius f)
        cap (.cap f)]
    (vec
     (for [i (range cap)
           :when (pos? (aget mass i))]
       {:entity   i
        :position [(aget px i) (aget py i) (aget pz i)]
        :radius   (max 0.1 (aget radius i))
        :size     (+ 60.0 (* 60.0 (Math/random)))
        :color    [0.75 0.55 0.95]
        :kind     :nebula}))))
