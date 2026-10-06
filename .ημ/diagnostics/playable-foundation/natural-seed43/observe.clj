;; Diagnostic only: execute the production world and production tick unchanged.
;; No alternate classifier, injected world/body, parameter tuning, or hot reload.
(require '[clojure.java.io :as io]
         '[clojure.java.shell :as shell]
         '[clojure.string :as str])

(def expected-revision "81207e7c65212685456ebe0faff3cac1cec50613")
(assert (= expected-revision
           (str/trim (:out (shell/sh "git" "rev-parse" "HEAD"))))
        "Source revision changed before loading the diagnostic process")
(assert (zero? (:exit (shell/sh "git" "diff" "--quiet" "HEAD" "--" "src" "deps.edn")))
        "Source or dependencies have uncommitted changes")

(require '[domain.arc :as arc]
         '[domain.genesis :as genesis]
         '[domain.ecs.core :as ecs]
         '[domain.ecs.components :as c]
         '[domain.stellar.classifier.planet :as planet]
         '[domain.stellar.classifier.candidate :as candidate]
         '[domain.stellar.disc :as disc]
         '[law.stellar :as law]
         '[shape.spatial :as sp])

(def tick-budget 12000)
(def wall-budget-ms 10800000)
(def sample-every 100)
(def started-ms (System/currentTimeMillis))
(def planet-states #{:planet :gas-giant :planetesimal})

(defn planet-eids [world]
  (into #{} (keep (fn [[eid state]] (when (planet-states state) eid)))
        (get-in world [:components c/matter-state])))

(defn body-observation [world stars eid birth-tick]
  (let [component #(ecs/get-component world eid %)
        parent (planet/dominant-attractor world eid stars)
        position (component c/position)
        material (component c/material-class)]
    {:eid eid :first-observed-tick birth-tick
     :survived-ticks (when birth-tick (- (:tick world) birth-tick))
     :matter-state (component c/matter-state)
     :alive? (some? (component c/matter-state))
     :position position :velocity (component c/velocity)
     :mass (component c/mass) :radius (component c/radius)
     :parent parent
     :bound-parent-radius-au (when (and parent position)
                               (/ (sp/dist position (:position parent)) law/au))
     ;; Distances are raw geometry, not a substitute host/binding classifier.
     :stellar-distances-au
     (when position
       (into {} (map (fn [star-eid]
                       [star-eid (/ (sp/dist position (ecs/get-component world star-eid c/position))
                                   law/au)])) stars))
     :orbit-elements (when parent (@#'candidate/candidate-orbit-elements world parent eid))
     :eligible-candidate? (when parent (@#'candidate/eligible-candidate? world parent eid))
     :material-class material :orbit-stable (component c/orbit-stable)
     :atmosphere-class (component c/atmosphere-class)
     :equilibrium-temperature (when (and parent material)
                                (planet/classify-body-equilibrium-temp world parent eid material))
     :stored-candidate (component c/planet-candidate)
     :ecology (component c/ecology)
     :commitment-state (component c/commitment-state)}))

(defn snapshot [world births]
  (let [stars (planet/stellar-bodies world)
        disks (filter (fn [[_ mass]] (pos? (double mass)))
                      (get-in world [:components c/disk-mass]))]
    {:tick (:tick world) :wall-ms (System/currentTimeMillis)
     :elapsed-ms (- (System/currentTimeMillis) started-ms)
     :sim-time (:genesis/sim-time world) :sim-dt (:sim/dt world)
     :ignition-time (:genesis/star-ignition-time world)
     :disk-age (- (:genesis/sim-time world) (:genesis/star-ignition-time world))
     :disk-maturity (:genesis/disk-maturity world)
     :active? (:genesis/active world) :fixture? (:demo/fixture? world)
     :arc (:arc/current world) :ending (arc/genesis-ending world)
     :states (frequencies (vals (get-in world [:components c/matter-state])))
     :stars (mapv #(@#'planet/star-record world %) stars)
     :disks
     (mapv (fn [[eid mass]]
             (let [momentum (ecs/get-component world eid c/disk-angular-mom)
                   host-mass (ecs/get-component world eid c/mass)]
               {:eid eid :mass mass :host-mass host-mass :angular-momentum momentum
                :radius (when (and momentum host-mass)
                          (disc/disk-radius (/ (sp/len momentum) mass) host-mass))
                :regime (ecs/get-component world eid c/disk-regime)
                :planets-seeded (ecs/get-component world eid c/planets-seeded)})) disks)
     :planet-count (count (planet-eids world))
     :ever-observed-planet-count (count births)
     :bodies (mapv (fn [[eid birth-tick]] (body-observation world stars eid birth-tick))
                   (sort-by key births))
     :stored-candidate-count (count (get-in world [:components c/planet-candidate]))
     :current-handoff-write-set ((:run (candidate/handoff-system)) world)
     :ledger-kinds (frequencies (map :kind (get-in world [:ledger :events])))}))

(defn emit! [writer record]
  (.write writer (str (pr-str record) "\n"))
  (.flush writer))

(try
  (with-open [writer (io/writer ".ημ/diagnostics/playable-foundation/natural-seed43/observations.edn"
                               :append true)]
    (let [world (genesis/create-world {:seed 43 :gas-count 1000})]
      (emit! writer {:kind :run-start :source-revision expected-revision
                     :seed 43 :world-options {:seed 43 :gas-count 1000}
                     :tick-budget tick-budget :wall-budget-ms wall-budget-ms
                     :sample-every-ticks sample-every :started-ms started-ms
                     :java-version (System/getProperty "java.version")
                     :fixture? (:demo/fixture? world)
                     :world-parameters (select-keys world [:sim/G :sim/theta :sim/dt :sim/softening
                                                          :genesis/nebula-mass :genesis/nebula-radius
                                                          :genesis/metallicity :genesis/gas-particle-mass
                                                          :genesis/adaptive-pacing?])})
      (println "SOURCE_LOADED" expected-revision "SEED" 43 "GAS" 1000)
      (flush)
      (loop [world world births {} previous-planets #{} ledger-count 0]
        (let [tick (:tick world)
              current-planets (planet-eids world)
              new-planets (remove #(contains? births %) current-planets)
              births (reduce #(assoc %1 %2 tick) births new-planets)
              events (get-in world [:ledger :events])
              new-events (drop ledger-count events)
              vanished (remove current-planets previous-planets)
              stop (cond
                     (>= tick tick-budget) :tick-budget
                     (>= (- (System/currentTimeMillis) started-ms) wall-budget-ms) :wall-budget
                     (false? (:genesis/active world)) :simulation-inactive)]
          (when (or (zero? (mod tick sample-every)) (seq new-planets) (seq vanished) (seq new-events) stop)
            (let [observation (snapshot world births)]
              (emit! writer (assoc observation :kind (if stop :run-stop :observation)
                                   :stop-reason stop :new-planet-eids (vec (sort new-planets))
                                   :left-planet-state-eids (vec (sort vanished))
                                   :new-ledger-events (mapv #(into {} %) new-events)))
              (println (pr-str (select-keys observation [:tick :elapsed-ms :sim-time :arc :states
                                                        :planet-count :ever-observed-planet-count
                                                        :stored-candidate-count :ledger-kinds])))
              (flush)))
          (if stop
            (println "RUN_STOP" stop "TICK" tick)
            (recur (arc/tick-genesis world) births current-planets (count events)))))))
  (catch Throwable error
    (binding [*out* *err*] (println "RUN_ERROR" (.getMessage error)))
    (throw error))
  (finally (shutdown-agents)))
