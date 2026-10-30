(ns domain.material-resolution-test
  (:require [clojure.test :refer [deftest is]]
            [domain.material.resolution :as r]
            [domain.material :as material]
            [domain.material.rotation]
            [domain.ecs.core :as ecs]
            [domain.ecs.components :as c]
            [domain.material-debris-test :as fixture]
            [shape.spatial :as sp]))

(defn quiet-world []
  (-> (fixture/cell-world)
      (ecs/put-component 0 c/velocity [0.01 0.02 0.0])
      (ecs/put-component 1 c/velocity [0.01 0.02 0.0])))

(deftest roundtrip-retains-extensive-state-and-history
  (let [w (ecs/put-component (quiet-world) 0 c/material-damage 0.73)
        packed (r/pack w [0 1])
        id (first (ecs/entities-with packed c/material-packet))
        back (r/unpack packed id)
        before (r/observed-cells w) after (r/observed-cells back)]
    (is (= 1 (count (ecs/all-entities packed))))
    (is (= 2 (count (ecs/all-entities back))))
    (is (= (mapv :source-id before) (mapv :source-id after)))
    (is (= (mapv :cell before) (mapv :cell after)))
    (is (= [0.73 0.0] (mapv :damage after)))
    (is (< (sp/len (sp/v- (:momentum (r/account w)) (:momentum (r/account packed)))) 1e-10))
    (is (< (sp/len (sp/v- (:angular-momentum (r/account w)) (:angular-momentum (r/account packed)))) 1e-10))
    (is (< (abs (- (:energy-J (r/account w)) (:energy-J (r/account packed)))) 1e-7))
    (is (< (abs (- (:energy-J (r/account w)) (:energy-J (r/account back)))) 1e-7))))

(deftest coarse-translation-preserves-local-geometry
  (let [w (quiet-world) packed (r/pack w [0 1])
        id (first (ecs/entities-with packed c/material-packet))
        translated (ecs/put-component packed id c/position [10.95 20.0 30.0])
        cells (r/observed-cells translated)]
    (is (< (sp/dist [10.0 20.0 30.0] (:p (first cells))) 1e-12))
    (is (< (abs (- 1.9 (sp/dist (:p (first cells)) (:p (second cells))))) 1e-12))))

(deftest classify-quiet-with-hysteresis-and-reactivate-before-contact
  (let [w (assoc (quiet-world) :material/coarse-enabled? true
                 :material/quiet-seconds 0.002 :material/min-coarse-cells 2)
        labels ((:run (material/aggregate-system)) w)
        w (update w :components merge labels)
        step (fn [w] (domain.ecs.tick/apply-write-set w ((:run (r/resolution-system)) w)))
        first-w (step w) quiet (step first-w)
        req (vals (get-in quiet [:components c/material-resolution-request]))
        packed (r/materialize-requests quiet)
        id (first (ecs/entities-with packed c/material-packet))
        [packed projectile] (ecs/spawn packed)
        threat (ecs/put-components packed projectile
                                  {c/position [30.0 0.0 0.0] c/velocity [-1.0 0.0 0.0]
                                   c/radius 1.0 c/mass 1.0})]
    (is (empty? (get-in first-w [:components c/material-resolution-request])))
    (is (= :pack (:op (first req))))
    (is (= :approach (:reason (r/reactivation threat id))))
    (is (empty? (ecs/entities-with (r/materialize-requests (step threat)) c/material-packet)))))

(deftest noisy-and-unbound-material-is-never-coarsened
  (let [w (assoc (fixture/cell-world) :material/coarse-enabled? true
                 :material/quiet-seconds 0.0 :material/min-coarse-cells 2)
        w (update w :components merge ((:run (material/aggregate-system)) w))
        ws ((:run (r/resolution-system)) w)]
    (is (empty? (get ws c/material-resolution-request)))))

(deftest coarse-approximation-expires
  (let [packed (r/pack (quiet-world) [0 1])
        id (first (ecs/entities-with packed c/material-packet))]
    (is (nil? (r/reactivation packed id)))
    (is (= :time-limit (:reason (r/reactivation (assoc packed :genesis/sim-time 1000.0) id))))))

(deftest intrinsic-spin-and-inventory-survive-roundtrip
  (let [w (-> (quiet-world)
              (ecs/put-component 0 c/velocity [0.01 0.04 0.0])
              (ecs/put-component 1 c/velocity [0.01 -0.02 0.0]))
        packed (r/pack w [0 1]) id (first (ecs/entities-with packed c/material-packet))
        back (r/unpack packed id)]
    (is (pos? (sp/len (:angular-momentum (r/account w)))))
    (is (< (sp/len (sp/v- (:angular-momentum (r/account w)) (:angular-momentum (r/account back)))) 1e-10))
    (is (= (:elements-kg (r/account w)) (:elements-kg (r/account back))))
    (is (thrown? clojure.lang.ExceptionInfo (r/pack w [0 0])))))

(deftest rotating-cache-and-refinement-preserve-spin
  (let [template (ecs/get-components (quiet-world) 0)
        bulk [0.1 0.2 0.3] omega [0.02 0.03 0.04]
        w (reduce (fn [w p]
                    (let [[w id] (ecs/spawn w)]
                      (ecs/put-components w id
                                          (assoc template c/position p
                                                 c/velocity (sp/v+ bulk (sp/cross omega p))))))
                  (assoc (ecs/empty-world) :material/coarse-rotation? true)
                  (for [x [-2.0 2.0] y [-2.0 2.0] z [-2.0 2.0]] [x y z]))
        packed (r/pack w (vec (ecs/all-entities w)))
        id (first (ecs/entities-with packed c/material-packet))
        rotated (nth (iterate #(domain.ecs.tick/apply-write-set %
                                  (domain.material.rotation/orientation-ws % 0.1)) packed) 100)
        back (r/unpack rotated id)]
    (is (thrown? clojure.lang.ExceptionInfo
                 (r/observed-cells (ecs/remove-component packed id c/material-orientation))))
    (is (> (sp/dist (:p (first (r/observed-cells packed))) (:p (first (r/observed-cells rotated)))) 0.1))
    (is (< (sp/len (sp/v- (:momentum (r/account w)) (:momentum (r/account rotated)))) 1e-9))
    (is (< (sp/len (sp/v- (:angular-momentum (r/account w)) (:angular-momentum (r/account rotated)))) 1e-9))
    (is (< (abs (- (:energy-J (r/account w)) (:energy-J (r/account rotated)))) 1e-5))
    (is (= (mapv :p (r/observed-cells rotated)) (mapv :p (r/observed-cells back))))))
