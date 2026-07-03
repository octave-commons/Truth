(ns domain.orbital.system-test
  (:require
   [clojure.test :refer [deftest is testing]]
   [domain.ecs.core        :as ecs]
   [domain.ecs.components  :as c]
   [domain.gravity.barnes-hut :as bh]
   [domain.orbital.integrator :as integrator]
   [domain.orbital.kepler     :as kepler]
   [domain.orbital.system     :as sys]
   [domain.physics.cache      :as pcache]
   [shape.spatial             :as sp]))

(deftest kepler-earth-period
  (let [AU   1.495978707e11
        GM   1.327124e20
        T    (kepler/kepler-period AU GM)
        days (/ T 86400.0)]
    (is (< (Math/abs (- days 365.25)) 0.1)
        (str "Expected ~365.25 days, got " days))))

(deftest leapfrog-energy-conservation
  (let [GM   1.327124e20
        r    1.495978707e11
        v_c  (Math/sqrt (/ GM r))
        body {:id :earth :mass 5.972e24 :radius 6.371e6 :kind :planet
              :position [r 0.0 0.0] :velocity [0.0 v_c 0.0]}
        accel (fn [b]
                (let [pos (:position b)
                      r2  (sp/len2 pos)
                      r3  (* r2 (Math/sqrt r2))]
                  (sp/v* pos (/ (- GM) r3))))
        T     (* 365.25 86400.0)
        dt    (/ T 1000.0)
        steps 1000
        energy (fn [b]
                 (let [v2  (sp/len2 (:velocity b))
                       r   (sp/len (:position b))]
                   (- (* 0.5 v2) (/ GM r))))
        e0    (energy body)
        final (loop [b body n 0]
                (if (>= n steps)
                  b
                  (recur (integrator/leapfrog-step b accel dt) (inc n))))
        ef    (energy final)
        rel   (Math/abs (/ (- ef e0) (Math/abs e0)))]
    (is (< rel 1e-4)
        (str "Energy drift " rel " exceeds threshold 1e-4"))))

(deftest orbital-system-pulls-bodies
  (testing "Orbital system updates position and velocity for gravitating bodies"
    (let [world (ecs/empty-world)
          [world sun]   (ecs/spawn world)
          [world earth] (ecs/spawn world)
          world (-> world
                    (ecs/put-components sun {c/mass 1.0e6
                                             c/radius 10.0
                                             c/body-kind :body/star
                                             c/position (sp/vec3 0.0 0.0 0.0)
                                             c/velocity (sp/vec3 0.0 0.0 0.0)})
                    (ecs/put-components earth {c/mass 1.0
                                               c/radius 1.0
                                               c/body-kind :body/planet
                                               c/position (sp/vec3 100.0 0.0 0.0)
                                               c/velocity (sp/vec3 0.0 1.0 0.0)}))
          world' ((sys/orbital-system 6.674e-11 0.5 1.0) world)
          earth-pos (ecs/get-component world' earth c/position)
          earth-vel (ecs/get-component world' earth c/velocity)]
      (is (not= [100.0 0.0 0.0] earth-pos))
      (is (vector? earth-vel)))))

(deftest test-gravity-acceleration-soa-path
  (testing "gravity-acceleration with SoA cache matches the non-SoA path"
    (let [world (ecs/empty-world)
          [world sun]   (ecs/spawn world)
          [world earth] (ecs/spawn world)
          [world mars]  (ecs/spawn world)
          world (-> world
                    (ecs/put-components sun {c/mass 1.0e6
                                             c/radius 10.0
                                             c/body-kind :body/star
                                             c/position (sp/vec3 0.0 0.0 0.0)
                                             c/velocity (sp/vec3 0.0 0.0 0.0)})
                    (ecs/put-components earth {c/mass 1.0
                                               c/radius 1.0
                                               c/body-kind :body/planet
                                               c/position (sp/vec3 100.0 0.0 0.0)
                                               c/velocity (sp/vec3 0.0 1.0 0.0)})
                    (ecs/put-components mars {c/mass 0.8
                                              c/radius 0.9
                                              c/body-kind :body/planet
                                              c/position (sp/vec3 -80.0 0.0 0.0)
                                              c/velocity (sp/vec3 0.0 -1.0 0.0)}))
          tree (bh/build-tree
                (map (fn [[eid comps]]
                       {:id       eid
                        :mass     (comps c/mass)
                        :radius   (comps c/radius)
                        :kind     (comps c/body-kind)
                        :position (comps c/position)
                        :velocity (comps c/velocity)})
                     (ecs/all-of world c/position c/velocity c/mass c/radius c/body-kind)))
          soa (pcache/build-physics-soa world)
          world-with-soa (assoc world :genesis/spatial-tree tree :genesis/physics-soa (:genesis/physics-soa soa))
          world-without-soa (assoc world :genesis/spatial-tree tree)
          sys (sys/gravity-acceleration 6.674e-11 0.5 1.0e14)
          soa-result ((:run sys) world-with-soa)
          non-soa-result ((:run sys) world-without-soa)]
      (is (= (keys non-soa-result) (keys soa-result)))
      (doseq [eid [sun earth mars]
              :let [a-soa (get-in soa-result [c/accel-gravity eid])
                    a-non (get-in non-soa-result [c/accel-gravity eid])]]
        (is (< (sp/dist a-soa a-non) 1.0e-9)
            (str "eid " eid " diverges: non-soa " a-non ", soa " a-soa))))))
