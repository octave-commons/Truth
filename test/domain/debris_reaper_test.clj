(ns domain.debris-reaper-test
  "Fix 6 (docs/specs/perf-60fps-parallel-tick.md): the debris sink reaps ONLY
   escapers — unbound debris beyond the escape distance — never bound bodies,
   however far out they orbit."
  (:require
   [clojure.test           :refer [deftest testing is]]
   [domain.debris          :as debris]
   [domain.genesis         :as genesis]
   [domain.ecs.core        :as ecs]
   [domain.ecs.components  :as c]))

(def G 6.674e-11)
(def msun 1.989e30)

(defn- add-body [w {:keys [state mass pos vel]}]
  (let [[w e] (ecs/spawn w)]
    [(ecs/put-components w e {c/matter-state state
                              c/mass mass
                              c/position pos
                              c/velocity (or vel [0.0 0.0 0.0])
                              c/radius 1.0e9
                              c/density 1.0e3
                              c/temperature 100.0})
     e]))

(defn- star-world
  "A star at the origin plus whatever `bodies` describe."
  [bodies]
  (reduce (fn [[w es] b]
            (let [[w' e] (add-body w b)]
              [w' (conj es e)]))
          (let [[w s] (add-body (assoc (ecs/empty-world) :sim/G G)
                                {:state :star :mass msun :pos [0.0 0.0 0.0]})]
            [w [s]])
          bodies))

(deftest bound-debris-survives-unbound-escaper-reaped
  (testing "circular-orbit debris far out is NOT reaped; a hyperbolic escaper is"
    (let [r-far  2.0e13                       ;; ~130 AU: >10× the RMS radius of a star-dominated system
          v-circ (Math/sqrt (/ (* G msun) r-far))
          v-esc  (Math/sqrt (/ (* 2.0 G msun) r-far))
          [w [_ bound esc]] (star-world
                             [{:state :debris :mass 1.0e22
                               :pos [r-far 0.0 0.0] :vel [0.0 v-circ 0.0]}
                              {:state :debris :mass 1.0e22
                               :pos [0.0 r-far 0.0] :vel [0.0 (* 1.5 v-esc) 0.0]}])
          ws ((:run (debris/debris-reaper-system)) w)
          marked (set (keys (get ws c/consumed-escape {})))]
      (is (not (contains? marked bound)) "bound debris at large radius survives")
      (is (contains? marked esc) "unbound debris past the edge is marked"))))

(deftest fast-but-not-receding-debris-survives
  (testing "the receding gate protects violent-but-tangential encounters: a fast
            body whose radial velocity is not outward may still interact"
    (let [r-near 1.0e10
          [w [_ near]] (star-world
                        [{:state :debris :mass 1.0e22
                          :pos [r-near 0.0 0.0] :vel [0.0 1.0e6 0.0]}])
          ws ((:run (debris/debris-reaper-system)) w)]
      (is (not (contains? (set (keys (get ws c/consumed-escape {}))) near))))))

(deftest non-debris-never-reaped
  (testing "only :debris is eligible — a runaway planet or star is not reaped"
    (let [r-far 2.0e13
          v-esc (Math/sqrt (/ (* 2.0 G msun) r-far))
          [w [_ planet]] (star-world
                          [{:state :planet :mass 6.0e24
                            :pos [r-far 0.0 0.0] :vel [0.0 (* 2.0 v-esc) 0.0]}])
          ws ((:run (debris/debris-reaper-system)) w)]
      (is (empty? (get ws c/consumed-escape {}))))))

(deftest materialize-reaps-marked-escapers-and-logs-the-event
  (testing "world-construction despawns marked escapers and records :event/body-escape"
    (let [r-far 2.0e13
          v-esc (Math/sqrt (/ (* 2.0 G msun) r-far))
          [w [_ esc]] (star-world
                       [{:state :debris :mass 1.0e22
                         :pos [r-far 0.0 0.0] :vel [(* 1.5 v-esc) 0.0 0.0]}])
          ws ((:run (debris/debris-reaper-system)) w)
          w' (-> (reduce-kv (fn [w eid v] (ecs/put-component w eid c/consumed-escape v))
                            w (get ws c/consumed-escape {}))
                 (genesis/materialize-lifecycle))]
      (is (not (ecs/alive? w' esc)) "escaper despawned at world-construction")
      (is (some #(= :event/body-escape (:kind %))
                (get-in w' [:ledger :events]))
          "ledger records the escape event"))))
