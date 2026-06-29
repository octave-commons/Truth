(ns domain.collision-malleability-test
  "μ for malleability-driven collision response: hot/molten bodies merge on
   impact (plastic deformation), cold/brittle bodies struck hard shatter into two
   :debris fragments — mass and momentum conserved. Makes law.stellar/malleability
   load-bearing in the collision path."
  (:require
   [clojure.test :refer [deftest is testing]]
   [domain.ecs.core   :as ecs]
   [domain.ecs.components :as c]
   [domain.stellar    :as stellar]
   [law.stellar       :as law]
   [shape.spatial     :as sp]))

(defn- two-body-world
  "A pre-touching pair (collision already detected). `:ta`/`:tb` temperatures,
   `:vb` the smaller body's velocity (the larger is at rest)."
  [{:keys [ta tb vb]}]
  (let [[w a] (ecs/spawn (ecs/empty-world))
        w (ecs/put-components w a {c/position (sp/vec3 0.0 0.0 0.0)
                                   c/velocity (sp/vec3 0.0 0.0 0.0)
                                   c/mass 1.0e29 c/radius 1.0e9 c/temperature ta
                                   c/matter-state :debris
                                   c/composition {:H 0.7 :He 0.3}})
        [w b] (ecs/spawn w)
        w (ecs/put-components w b {c/position (sp/vec3 8.0e8 0.0 0.0)
                                   c/velocity vb
                                   c/mass 1.0e28 c/radius 5.0e8 c/temperature tb
                                   c/matter-state :debris
                                   c/composition {:H 0.7 :He 0.3}})]
    [w a b]))

(def ^:private event {:payload {:eid-a 0 :eid-b 1} :tick 1})

(defn- alive-count [w] (count (:alive w)))
(defn- total-mass [w]
  (reduce + 0.0 (map #(double (or (ecs/get-component w % c/mass) 0.0)) (:alive w))))
(defn- total-momentum [w]
  (reduce (fn [p eid]
            (sp/v+ p (sp/v* (ecs/get-component w eid c/velocity)
                            (double (ecs/get-component w eid c/mass)))))
          (sp/vec3 0.0 0.0 0.0) (:alive w)))

(deftest malleability-monotone
  (is (= 0.0 (law/malleability 0.0)))
  (is (= 1.0 (law/malleability law/melt-temperature)))
  (is (= 1.0 (law/malleability (* 2.0 law/melt-temperature))) "clamped at 1"))

(deftest hot-molten-bodies-merge
  (testing "T well above melt ⇒ malleable ⇒ merge even at high dv"
    (let [[w a _] (two-body-world {:ta 3000.0 :tb 3000.0 :vb (sp/vec3 1.0e4 0.0 0.0)})
          m0      (total-mass w)
          w'      (stellar/stellar-merge-handler w event)]
      (is (= 1 (alive-count w')) "one survivor")
      (is (ecs/alive? w' a))
      (is (< (/ (Math/abs (- (total-mass w') m0)) m0) 1.0e-12) "mass summed onto survivor"))))

(deftest cold-brittle-high-dv-shatters
  (testing "cold (brittle) + hard impact ⇒ smaller shatters into two debris"
    (let [[w a b] (two-body-world {:ta 100.0 :tb 100.0 :vb (sp/vec3 1.0e4 0.0 0.0)})
          m0      (total-mass w)
          p0      (total-momentum w)
          w'      (stellar/stellar-merge-handler w event)]
      (is (= 3 (alive-count w')) "larger survives + 2 fragments (smaller gone)")
      (is (ecs/alive? w' a) "the larger body survives")
      (is (not (ecs/alive? w' b)) "the smaller body is gone")
      (testing "mass conserved"
        (is (< (/ (Math/abs (- (total-mass w') m0)) m0) 1.0e-12)))
      (testing "momentum conserved"
        (is (< (sp/len (sp/v- (total-momentum w') p0)) 1.0e18))))))

(deftest cold-but-gentle-impact-merges
  (testing "cold yet low-dv (below shatter threshold) ⇒ merge, not shatter"
    (let [[w a _] (two-body-world {:ta 100.0 :tb 100.0 :vb (sp/vec3 1.0e2 0.0 0.0)})
          w'      (stellar/stellar-merge-handler w event)]
      (is (= 1 (alive-count w')) "gentle contact merges")
      (is (ecs/alive? w' a)))))

(deftest tiny-bodies-merge-not-shatter
  (testing "below shatter-min-mass the pair merges regardless of brittleness"
    (let [[w a b] (two-body-world {:ta 100.0 :tb 100.0 :vb (sp/vec3 1.0e4 0.0 0.0)})
          w (ecs/put-component w b c/mass 1.0e20) ;; below shatter-min-mass
          w' (stellar/stellar-merge-handler w event)]
      (is (= 1 (alive-count w'))))))
