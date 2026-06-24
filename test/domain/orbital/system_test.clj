(ns domain.orbital.system-test
  (:require
    [clojure.test :refer [deftest is testing]]
    [domain.ecs.core        :as ecs]
    [domain.ecs.components  :as c]
    [domain.orbital.integrator :as integrator]
    [domain.orbital.kepler     :as kepler]
    [domain.orbital.system     :as sys]
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
