(ns domain.orbital.kepler-workload
  "Fixed public-solver and real-ECS workloads for the Stumpff reuse comparison."
  (:require
   [clojure.math :as math]
   [clojure.walk :as walk]
   [domain.ecs.components :as c]
   [domain.ecs.core :as ecs]
   [domain.ecs.tick :as tick]
   [domain.integrator.base :as base]
   [domain.integrator.kinematics :as kinematics]
   [domain.orbital.kepler :as kepler]
   [domain.orbital.multi-timescale-regression-test :as regression]
   [domain.orbital.system :as orbital]
   [domain.physics.cache.soa :as soa]
   [domain.spatial.index :as spatial]
   [law.stellar :as stellar]
   [shape.spatial :as sp]))

(def cases
  "Finite matrix spanning Stumpff branches, radial motion, rotations and scales.

   Near-parabolic inputs use mu=0.5 and radius=1, so speed=1 gives exact alpha=0.
   Each case is propagated separately with both signs of dt by the oracle."
  [{:id :circular :mu 1.0 :r [1.0 0.0 0.0] :v [0.0 1.0 0.0] :dt 0.2}
   {:id :ellipse :mu 1.0 :r [1.0 0.0 0.0] :v [0.0 1.2 0.0] :dt 0.2}
   {:id :eccentric :mu 1.0 :r [1.0 0.0 0.0]
    :v [0.0 1.396424004376894 0.0] :dt 0.1}
   {:id :parabolic :mu 0.5 :r [1.0 0.0 0.0] :v [0.0 1.0 0.0] :dt 0.02}
   {:id :near-parabolic-bound :mu 0.5 :r [1.0 0.0 0.0]
    :v [0.0 0.99999999 0.0] :dt 0.02}
   {:id :near-parabolic-unbound :mu 0.5 :r [1.0 0.0 0.0]
    :v [0.0 1.00000001 0.0] :dt 0.02}
   {:id :hyperbolic :mu 1.0 :r [1.0 0.0 0.0] :v [0.0 1.8 0.0] :dt 0.2}
   {:id :hyperbolic-infall :mu 1.0 :r [1.0 0.0 0.0]
    :v [-0.25 1.8 0.0] :dt 0.1}
   {:id :rotated-ellipse :mu 1.0 :r [0.6 0.8 0.0]
    :v [-0.72 0.54 0.7937253933193772] :dt 0.1}
   {:id :radial-outward :mu 1.0 :r [1.0 0.0 0.0]
    :v [0.2 1.2 0.0] :dt 0.1}
   {:id :solar-scale :mu 1.327124e20 :r [1.495978707e11 0.0 0.0]
    :v [0.0 29784.691831696804 0.0] :dt 86400.0}
   {:id :small-scale :mu 1.0e-6 :r [0.01 0.0 0.0]
    :v [0.0 0.012 0.0] :dt 0.1}
   {:id :negative-bracket-reproduction :mu 1.0 :r [0.7 0.0 0.0]
    :v [0.0 (math/sqrt (/ 1.3 0.7)) 0.0] :dt 1.0}
   {:id :negative-apoapsis-control :mu 1.0 :r [0.7 0.0 0.0]
    :v [0.0 (math/sqrt (/ 1.3 0.7)) 0.0] :dt math/PI}])

(def trajectory-steps
  "Number of repeated public propagations captured per case and time direction."
  16)

(def compact-steps
  "Number of real gravity-plus-kinematics folds captured per compact scenario."
  12)

(defn numeric-bits
  "Replace floating values with tagged raw IEEE-754 bits; preserve other data."
  [value]
  (walk/postwalk (fn [node]
                   (if (double? node)
                     [:float64 (Double/doubleToRawLongBits node)]
                     node))
                 value))

(defn public-trajectory
  "Capture every public state or first declared solver failure without hiding it."
  [{:keys [mu r v dt]} direction]
  (loop [state {:position r :velocity v}, states [state], i 0]
    (if (= i trajectory-steps)
      {:states states}
      (let [outcome (try
                      {:state (kepler/propagate mu (:position state) (:velocity state)
                                                (* direction dt))}
                      (catch clojure.lang.ExceptionInfo error
                        {:error {:message (.getMessage error) :data (ex-data error)}}))]
        (if-let [next-state (:state outcome)]
          (recur next-state (conj states next-state) (inc i))
          {:states states :failed-step (inc i) :error (:error outcome)})))))

(defn edge-oracle
  "Pin zero-time branch ordering and exact declared invalid-input outcomes."
  []
  (mapv (fn [[mu r v dt :as input]]
          {:input input
           :outcome (numeric-bits
                     (try {:state (kepler/propagate mu r v dt)}
                          (catch clojure.lang.ExceptionInfo error
                            {:error {:message (.getMessage error) :data (ex-data error)}})))})
        [[-1.0 [0.0 -0.0 0.0] [1.0 2.0 3.0] 0.0]
         [-1.0 [0.0 -0.0 0.0] [1.0 2.0 3.0] -0.0]
         [0.0 [1.0 0.0 0.0] [0.0 1.0 0.0] 1.0]
         [1.0 [0.0 0.0 0.0] [0.0 1.0 0.0] 1.0]]))

(defn public-oracle
  "Return input-pinned raw-bit trajectories for both signs of dt.

   Negative-time failures are retained as baseline outcomes, not relabeled as
   optimization failures or silently excluded from the matrix."
  []
  (mapv (fn [[input direction]]
          {:case (:id input) :direction direction :input input
           :outcome (numeric-bits (public-trajectory input direction))})
        (for [input cases, direction [1.0 -1.0]] [input direction])))

(defn compact-world
  "Reuse the existing live-scale star/planet fixture, with explicit host motion."
  [scenario]
  (let [[world star planet] (#'regression/two-body-world)]
    (case scenario
      :stationary world
      :moving-recentered
      (reduce (fn [w eid]
                (-> w
                    (ecs/put-component eid c/position
                                       (sp/v+ (ecs/get-component w eid c/position)
                                              [2.0e12 -3.0e12 1.0e12]))
                    (ecs/put-component eid c/velocity
                                       (sp/v+ (ecs/get-component w eid c/velocity)
                                              [1200.0 -750.0 100.0]))))
              world [star planet]))))

(defn prepared-compact-world
  "Build the production spatial/SoA snapshot, including its computed COM offset."
  [world]
  (-> world spatial/spatial-index soa/build-physics-soa))

(defn compact-path-observation
  "Read the actual dominance decision on the exact prepared snapshot."
  [world]
  (let [frozen (prepared-compact-world world)
        states (get-in frozen [:components c/matter-state])
        star (first (keep (fn [[eid state]] (when (= state :star) eid)) states))
        planet (first (keep (fn [[eid state]] (when (= state :planet) eid)) states))
        gate (kinematics/dominance-gate
              frozen planet star (base/due-entity? frozen (:tick frozen) star)
              (base/sum-vec-influences frozen planet base/accel-sources))]
    {:star star :planet planet :pass? (:pass? gate)
     :frame-offset (:genesis/frame-offset frozen)}))

(defn compact-step
  "Run the actual frozen-snapshot gravity and selected kinematics writer/fold.

   This is the existing regression's minimal production pipeline; the map and
   SoA writers share the real Kepler implementation and are compared separately."
  [path world]
  (let [dt (:sim/dt world)
        writer (case path
                 :map #(kinematics/kinematics-ws % dt)
                 :soa #(kinematics/kinematics-ws-soa % dt (:genesis/physics-soa %)))]
    (-> (prepared-compact-world world)
        (tick/run-parallel
         [(orbital/gravity-acceleration stellar/G (:sim/theta world) (:sim/softening world))
          {:id :kinematics :writes #{c/position c/velocity} :run writer}])
        (update :tick inc))))

(defn compact-states
  "Advance the real compact workload; retain each frozen/published world."
  [path scenario]
  (vec (take (inc compact-steps) (iterate (partial compact-step path)
                                         (compact-world scenario)))))

(defn compact-observation
  "Capture all component columns and IDs; transient runtime caches are not EDN."
  [world]
  (numeric-bits {:tick (:tick world) :alive (vec (sort (:alive world)))
                 :components (:components world)
                 :frame-offset (:genesis/frame-offset world)}))

(defn compact-oracle
  "Return every component snapshot from both writers and host-frame scenarios."
  []
  (mapv (fn [[path scenario]]
          (let [states (compact-states path scenario)]
            {:path path :scenario scenario
             :gates (mapv compact-path-observation (butlast states))
             :states (mapv compact-observation states)}))
        (for [path [:map :soa], scenario [:stationary :moving-recentered]] [path scenario])))

(defn energy
  "Specific two-body energy for independent numerical checks."
  [mu {:keys [position velocity]}]
  (- (* 0.5 (sp/len2 velocity)) (/ mu (sp/len position))))

(defn angular-momentum
  "Specific angular momentum vector for independent numerical checks."
  [{:keys [position velocity]}]
  (sp/cross position velocity))

(defn circular-state
  "Independent analytic circular state at angle t for mu=r=1."
  [t]
  {:position [(math/cos t) (math/sin t) 0.0]
   :velocity [(- (math/sin t)) (math/cos t) 0.0]})
