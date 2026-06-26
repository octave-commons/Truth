(ns domain.phase0
  "Phase 0: Stellar Nebula — composition layer over the ECS substrate.

   This is NOT a separate engine. It bootstraps a normal ECS world, wires the
   stellar/thermal/fusion/collision/observer systems into a tick pipeline, seeds
   a nebula of entities and the player's observer spark, and drives the world
   forward while emitting threshold events into the shared ledger.

   Everything here is pure data transformation; rendering and IO live in infra."
  (:require
   [domain.stellar          :as stellar]
   [domain.chemistry        :as chemistry]
   [domain.player           :as player]
   [law.stellar             :as law]
   [domain.ecs.core         :as ecs]
   [domain.ecs.event        :as event]
   [domain.ecs.components    :as c]
   [domain.orbital.system   :as orbital]
   [domain.physics.collision :as collision]
   [shape.spatial           :as sp]))

;; --- Nebula seeding ---------------------------------------------------------

(defn seed-nebula
  "Seed a forming system: one dense central core that can collapse to a star,
   ringed by smaller, gravitationally stable, planet-mass clumps. Deterministic
   so simulations and tests are reproducible."
  [world total-mass extent]
  (let [core-mass   (* total-mass 0.9)
        core-radius (* extent 0.005)
        n           6
        ring-mass   (/ (* total-mass 0.1) n)
        ring-radius (* extent 0.4)
        body-radius (* extent 0.01)
        [w0 _]      (stellar/spawn-clump world
                      {:position    (sp/vec3 0 0 0)
                       :mass        core-mass
                       :radius      core-radius
                       :temperature 10.0})]
    (reduce
     (fn [w i]
       (let [a (* 2 Math/PI (/ (double i) n))
             cx (* ring-radius (Math/cos a))
             cy (* ring-radius (Math/sin a))
             v  (Math/sqrt (/ (* law/G core-mass) ring-radius))]
         (first
          (stellar/spawn-clump w
            {:position    (sp/vec3 cx cy 0)
             :velocity    (sp/vec3 (* (- v) (Math/sin a)) (* v (Math/cos a)) 0)
             :mass        ring-mass
             :radius      body-radius
             :temperature 20.0
             :composition {:H 0.4 :He 0.1 :O 0.2 :Si 0.15 :Fe 0.1 :metals 0.05}}))))
     w0
     (range n))))

;; --- World construction -----------------------------------------------------

(defn create-world
  "Bootstrap a Phase 0 world ready to tick."
  ([] (create-world {}))
  ([{:keys [G theta dt nebula-mass nebula-radius collapse-fraction]
     :or   {G law/G theta 0.5 dt 0.1
            nebula-mass 2e31 nebula-radius 1e17 collapse-fraction 0.5}}]
   (let [base   (-> (ecs/empty-world)
                    (event/with-ledger)
                    (event/register-handler :event/collision
                                            stellar/stellar-merge-handler)
                    (assoc :sim/G G :sim/theta theta :sim/dt dt
                           :phase0/sim-time          0.0
                           :phase0/time-scale        (stellar/time-scale-from-complexity 0)
                           :phase0/complexity        0
                           :phase0/phase             :initializing
                           :phase0/active            true
                           :phase0/collapse-fraction collapse-fraction))
         seeded (seed-nebula base nebula-mass nebula-radius)
         [w _]  (player/spawn-observer seeded (sp/vec3 0 0 (* nebula-radius 2)))]
     w)))

;; --- Observable summary -----------------------------------------------------

(defn system-summary
  "Tally the world's resolved matter into the shape used for complexity, phase
   detection, and habitability."
  [world]
  (let [eids    (ecs/entities-with world c/matter-state c/mass)
        regions (mapv #(stellar/entity->region world %) eids)
        stars   (filterv #(= :star (:matter-state %)) regions)
        non-stars (remove #(= :star (:matter-state %)) regions)
        planets (filterv law/hydrostatic-equilibrium? non-stars)]
    {:body-count   (count regions)
     :star?        (boolean (seq stars))
     :fusion?      (boolean (seq stars))
     :planet-count (count planets)
     :stars        stars
     :planets      planets
     :regions      regions}))

(defn detect-phase
  "Detect the current phase of the forming system from its summary."
  [{:keys [star? planet-count body-count regions]} sim-time]
  (let [nebula?    (some #(= :nebula (:matter-state %)) regions)
        protostar? (some #(= :protostar (:matter-state %)) regions)]
    (cond
      (and star? (pos? planet-count)) :phase-0/planets-formed
      (and star? (>= body-count 3))   :phase-0/accretion
      star?                           :phase-0/ignition
      protostar?                      :phase-0/protostar
      (zero? body-count)              :phase-0/dispersed
      (and nebula? (< sim-time 1e18)) :phase-0/nebula-collapse
      :else                           :phase-0/dispersed)))

;; --- Tick driver ------------------------------------------------------------

(defn- emit-threshold
  "Emit a threshold event into the ledger at the world's current tick."
  [world kind data]
  (event/dispatch world
    (event/->event {:tick     (:tick world)
                    :kind     kind
                    :entities #{}
                    :payload  {:data data}})))

(defn physics-systems
  "The ordered physical systems run each tick (everything except the observer,
   which must run after complexity and events are known)."
  [{:keys [sim/G sim/theta sim/dt]}]
  [(orbital/orbital-system G theta dt)
   stellar/collapse-system
   stellar/fusion-system
   (stellar/thermal-system dt)
   stellar/classify-system
   collision/collision-detection-system])

(defn tick-world
  "Advance the world by one tick. Pure: world -> world'."
  [world]
  (if-not (:phase0/active world)
    world
    (let [dt         (:sim/dt world)
          prev       (system-summary world)
          prev-phase (:phase0/phase world)
          ;; advance logical tick first so every event this step shares its tick
          world1     (ecs/advance-tick world)
          world2     (ecs/run-systems world1 (physics-systems world1))
          summ       (system-summary world2)
          complexity (stellar/complexity-score summ)
          time-scale (stellar/time-scale-from-complexity complexity)
          phase      (detect-phase summ (:phase0/sim-time world2))
          world3     (cond-> world2
                       (and (:star? summ) (not (:star? prev)))
                       (emit-threshold :event/stellar-ignition (first (:stars summ)))

                       (> (:planet-count summ) (:planet-count prev))
                       (emit-threshold :event/planet-formation (first (:planets summ)))

                       (not= phase prev-phase)
                       (emit-threshold :event/phase-transition {:from prev-phase :to phase}))
          world4     (assoc world3
                       :phase0/complexity complexity
                       :phase0/time-scale time-scale
                       :phase0/phase      phase
                       :phase0/sim-time   (+ (:phase0/sim-time world3) (* dt time-scale)))
          world5     ((player/observer-system dt) world4)
          obs        (player/get-observer world5)]
      (assoc world5 :phase0/active
             (and (player/can-interact? obs)
                  (not= phase :phase-0/dispersed))))))

;; --- Player input -----------------------------------------------------------

(defn handle-input
  "Apply a player control to the world's observer."
  [world input-type & args]
  (case input-type
    :move-focus  (let [[pos] args]
                   (player/update-observer world
                     #(player/set-focus % pos (:focus-radius %) (:focus-intensity %))))
    :narrow-focus (player/update-observer world #(player/narrow-focus % 2.0))
    :widen-focus  (player/update-observer world #(player/widen-focus % 2.0))
    :release      (player/update-observer world
                    #(player/release-focus %
                       (fn [pos]
                         (let [dir (sp/v- (sp/vec3 0 0 0) pos)
                               l   (sp/len dir)]
                           (if (pos? l) (sp/v* dir (/ 1.0 l)) dir)))))
    world))

;; --- Habitability / handoff -------------------------------------------------

(defn habitability-of
  "Habitability score of a resolved body region for the chemistry model."
  [region]
  (chemistry/habitability-score region))

(defn habitable-worlds
  "Resolved planet regions with non-trivial habitability potential, for the
   handoff to Phase 1."
  [world]
  (->> (:planets (system-summary world))
       (filter #(> (habitability-of %) 0.2))))

(defn ready-for-phase-1?
  [world]
  (and (= (:phase0/phase world) :phase-0/planets-formed)
       (seq (habitable-worlds world))))

;; --- Endings ----------------------------------------------------------------

(defn world-ending
  "If the world has reached a terminal state, describe it; else nil."
  [world]
  (let [phase (:phase0/phase world)
        obs   (player/get-observer world)]
    (cond
      (ready-for-phase-1? world)
      {:type :success
       :worlds (habitable-worlds world)
       :time (:phase0/sim-time world)
       :message "A world capable of harboring life has formed."}

      (and obs (not (player/can-interact? obs)))
      {:type :fadeout
       :message "You dissolve back into the quantum foam."}

      (= phase :phase-0/dispersed)
      {:type :dispersal
       :message "The nebula disperses. No stars form here."}

      (and (= phase :phase-0/planets-formed) (empty? (habitable-worlds world)))
      {:type :sterile
       :message "Beautiful, but sterile. Life will not arise here."}

      :else nil)))
