(ns domain.motion-trail-test
  "Body-trail temporal/frame contracts through pure state and the real fan-out."
  (:require
   [clojure.test :refer [deftest is]]
   [domain.ecs.components :as c]
   [domain.ecs.core :as ecs]
   [domain.ecs.registry :as registry]
   [domain.ecs.tick :as tick]
   [domain.genesis.systems :as systems]
   [domain.integrator :as integrator]))

(def ^:private trail-component :component/motion-trail)

(defn- api [sym]
  ;; A new feature's absence must be an assertion failure, not an unloadable
  ;; test namespace. Availability is asserted below; behavioral tests activate
  ;; as soon as the implementation exists. Other load errors still propagate.
  (try (requiring-resolve sym)
       (catch java.io.FileNotFoundException _ nil)))

(def ^:private small-options {:capacity 4 :cadence 10.0 :horizon 30.0})

(defn- observation [sample-time dt position offset]
  {:time sample-time :dt dt :position position :frame-offset offset})

(defn- observe [advance history sample-time]
  (advance history (observation sample-time 0.0 [sample-time 0.0 0.0] [0.0 0.0 0.0])
           small-options))

(defn- sample-times [history]
  (mapv :time (:samples history)))

(defn- spawn-body [world components]
  (let [[world eid] (ecs/spawn world)]
    [(reduce-kv #(ecs/put-component %1 eid %2 %3) world components) eid]))

(deftest trail-api-and-owner-exist
  (doseq [sym '[domain.trail/eligible? domain.trail/advance-history
                law.trail/valid-trail? domain.trail.system/trail-system]]
    (is (some? (api sym)) (str "The accepted trail contract requires " sym)))
  (is (= trail-component
         (some-> (ns-resolve 'domain.ecs.components 'motion-trail) deref)))
  (let [pipeline (systems/physics-systems-parallel {:sim/G 6.674e-11
                                                    :sim/theta 0.5
                                                    :sim/dt 1.0
                                                    :sim/softening 1.0})
        owners (filter #(contains? (:writes %) trail-component) pipeline)
        declared (filter #(contains? (:writes %) trail-component) registry/systems)]
    (is (= [:motion-trail] (mapv :id owners)) "one owner in the actual fan-out")
    (is (= [:motion-trail] (mapv :id declared)) "the same registry owner")
    (when-let [owner (first declared)]
      (is (= #{trail-component} (:writes owner)))
      (is (= #{c/position c/body-kind c/matter-state trail-component}
             (:reads owner))))
    (is (= {} (registry/write-conflicts registry/systems)))))

(deftest significant-bodies-exclude-collision-fragments
  (when-let [eligible? (api 'domain.trail/eligible?)]
    (let [position [1.0 2.0 3.0]]
      (doseq [body [{:body-kind :spark}
                    {:matter-state :star}
                    {:matter-state :planet}
                    {:matter-state :gas-giant}
                    {:body-kind :body/planet :matter-state :planetesimal}]]
        (is (eligible? (assoc body :position position)) (str body)))
      (doseq [body [{:matter-state :nebula}
                    {:matter-state :planetesimal}
                    {:body-kind :body/rocky :matter-state :planetesimal}
                    {:body-kind :body/planet :matter-state :nebula}
                    {:matter-state :protostar}
                    {:matter-state :brown-dwarf}
                    {:matter-state :stellar-remnant}]]
        (is (not (eligible? (assoc body :position position))) (str body)))
      (is (not (eligible? {:matter-state :planet}))))))

(deftest timestamps-follow-actual-observations-at-shared-deadlines
  (when-let [advance (api 'domain.trail/advance-history)]
    (let [fine (reduce #(observe advance %1 %2) nil [0.0 3.0 10.0 14.0 20.0])
          coarse (reduce #(observe advance %1 %2) nil [0.0 10.0 20.0])
          initial (observe advance nil 0.0)
          before (observe advance initial 9.0)]
      (is (= [0.0 10.0 20.0] (sample-times fine)))
      (is (= (:samples coarse) (:samples fine))
          "both schedules actually observe the same positions at deadlines")
      (is (= [0.0] (sample-times before)))
      (is (= 10.0 (:next-at before)))
      (is (= fine (observe advance fine 20.0))
          "rendering/observing the same simulation time cannot append samples")
      (is (zero? (:skipped-deadlines fine))))))

(deftest skipped-deadlines-never-invent-an-orbit
  (when-let [advance (api 'domain.trail/advance-history)]
    (let [initial (observe advance nil 0.0)
          jumped (observe advance initial 35.0)
          huge (advance initial
                        (observation 1.0e12 0.0 [9.0 0.0 0.0] [0.0 0.0 0.0])
                        small-options)]
      (is (= [35.0] (sample-times jumped)) "expired time zero is removed")
      (is (= [[35.0 0.0 0.0]] (mapv :position (:samples jumped))))
      (is (= 40.0 (:next-at jumped)))
      (is (= 2 (:skipped-deadlines jumped))
          "three due deadlines yield one actual observation and two skips")
      (is (= [1.0e12] (sample-times huge)))
      (is (= 99999999999 (:skipped-deadlines huge)))
      (is (> (:next-at huge) 1.0e12)))))

(deftest history-is-bounded-by-count-and-published-time
  (when-let [advance (api 'domain.trail/advance-history)]
    (let [bounded (reduce #(observe advance %1 %2) nil
                          (mapv double (range 0 101 10)))
          expired (advance bounded
                           (observation 100.0 31.0 [100.0 0.0 0.0] [0.0 0.0 0.0])
                           small-options)
          count-bound (reduce
                       #(advance %1 (observation %2 0.0 [%2 0.0 0.0] [0.0 0.0 0.0])
                                 (assoc small-options :capacity 2 :horizon 1000.0))
                       nil [0.0 10.0 20.0 30.0])]
      (is (= [70.0 80.0 90.0 100.0] (sample-times bounded)))
      (is (empty? (:samples expired))
          "a step beyond the horizon does not publish a falsely recent tail")
      (is (= [20.0 30.0] (sample-times count-bound))))))

(deftest every-fold-rebases-even-when-no-sample-is-due
  (when-let [advance (api 'domain.trail/advance-history)]
    (let [initial (advance nil
                           (observation 0.0 1.0 [100.0 0.0 0.0] [10.0 0.0 0.0])
                           small-options)
          unsampled (advance initial
                             (observation 1.0 1.0 [92.0 0.0 0.0] [20.0 0.0 0.0])
                             small-options)
          due (advance unsampled
                       (observation 10.0 1.0 [80.0 0.0 0.0] [5.0 0.0 0.0])
                       small-options)]
      (is (= [{:time 0.0 :position [90.0 0.0 0.0]}] (:samples initial)))
      (is (= [{:time 0.0 :position [70.0 0.0 0.0]}] (:samples unsampled)))
      (is (= [{:time 0.0 :position [65.0 0.0 0.0]}
              {:time 10.0 :position [75.0 0.0 0.0]}] (:samples due))))))

(deftest history-shape-rejects-nonfinite-data
  (when-let [valid? (api 'law.trail/valid-trail?)]
    (let [history {:samples [{:time 0.0 :position [1.0 2.0 3.0]}]
                   :next-at 10.0 :skipped-deadlines 0}]
      (is (valid? history))
      (is (not (valid? (assoc-in history [:samples 0 :time] Double/NaN))))
      (is (not (valid? (assoc-in history [:samples 0 :position 2]
                                 Double/POSITIVE_INFINITY))))
      (is (not (valid? (assoc history :skipped-deadlines -1)))))))

(deftest writer-filters-before-history-and-clears-only-owned-staleness
  (when-let [factory (api 'domain.trail.system/trail-system)]
    (let [[w star] (spawn-body (assoc (ecs/empty-world) :genesis/sim-time 0.0 :sim/dt 1.0)
                               {c/matter-state :star c/position [10.0 0.0 0.0]})
          [w planet] (spawn-body w {c/matter-state :planet c/position [20.0 0.0 0.0]})
          [w spark] (spawn-body w {c/body-kind :spark c/position [30.0 0.0 0.0]})
          [w fragment] (spawn-body w {c/matter-state :planetesimal
                                      c/position [40.0 0.0 0.0]
                                      trail-component {:samples [{:time 0.0 :position [40.0 0.0 0.0]}]
                                                       :next-at 1.0 :skipped-deadlines 0}})
          dense (reduce (fn [world _]
                          (first (spawn-body world {c/matter-state :nebula
                                                    c/position [1.0 0.0 0.0]})))
                        w (range 1000))
          ws ((:run (factory)) dense)
          after (tick/apply-write-set dense ws)]
      (is (= #{trail-component} (set (keys ws))))
      (is (= #{star planet spark} (set (ecs/entities-with after trail-component))))
      (is (nil? (ecs/get-component after fragment trail-component)))
      (is (= (dissoc (:components after) trail-component)
             (dissoc (:components dense) trail-component))
          "only trail ownership changes")
      (is (= (get-in dense [:components c/position])
             (get-in after [:components c/position]))))))

(deftest real-integrator-and-trail-share-the-output-frame
  (when-let [factory (api 'domain.trail.system/trail-system)]
    (let [[w eid] (spawn-body
                   (assoc (ecs/empty-world)
                          :genesis/sim-time 0.0 :sim/dt 1.0
                          :genesis/frame-offset [10.0 0.0 0.0])
                   {c/body-kind :spark c/position [100.0 0.0 0.0]
                    c/velocity [2.0 0.0 0.0] c/mass 1.0 c/radius 1.0
                    trail-component {:samples [{:time -10.0 :position [80.0 0.0 0.0]}]
                                     :next-at 0.0 :skipped-deadlines 0}})
          first-fold (tick/run-parallel w [(integrator/integrator-system 1.0) (factory)])
          next-input (assoc first-fold :genesis/sim-time 1.0
                            :genesis/frame-offset [20.0 0.0 0.0])
          second-fold (tick/run-parallel next-input [(integrator/integrator-system 1.0) (factory)])]
      (is (= [92.0 0.0 0.0] (ecs/get-component first-fold eid c/position)))
      (is (= [-10.0 0.0] (sample-times (ecs/get-component first-fold eid trail-component)))
          "input positions retain input timestamps rather than output time")
      (is (= [[70.0 0.0 0.0] [90.0 0.0 0.0]]
             (mapv :position (:samples (ecs/get-component first-fold eid trail-component)))))
      (is (= [74.0 0.0 0.0] (ecs/get-component second-fold eid c/position)))
      (is (= [-10.0 0.0] (sample-times (ecs/get-component second-fold eid trail-component)))
          "an unsampled recenter changes coordinates, never observation time")
      (is (= [[50.0 0.0 0.0] [70.0 0.0 0.0]]
             (mapv :position (:samples (ecs/get-component second-fold eid trail-component))))))))

(deftest writer-samples-the-simulation-clock-not-tick-or-published-time
  (when-let [factory (api 'domain.trail.system/trail-system)]
    (let [[world eid] (spawn-body
                       (assoc (ecs/empty-world) :genesis/sim-time 100.0 :sim/dt 7.0 :tick 999999)
                       {c/matter-state :star c/position [10.0 0.0 0.0]})
          write (:run (factory))
          first-fold (tick/apply-write-set world (write world))
          later (-> first-fold
                    (assoc :genesis/sim-time 10000000100.0 :sim/dt 3.0 :tick 2)
                    (ecs/put-component eid c/position [20.0 0.0 0.0]))
          next-history (get-in (write later) [trail-component eid])]
      (is (= [100.0] (sample-times (ecs/get-component first-fold eid trail-component))))
      (is (= [100.0 10000000100.0] (sample-times next-history)))
      (is (= [[10.0 0.0 0.0] [20.0 0.0 0.0]] (mapv :position (:samples next-history)))))))
