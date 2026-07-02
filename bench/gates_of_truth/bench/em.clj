(ns gates-of-truth.bench.em
  "Electromagnetic field benchmarks.

   Tests the EM pipeline costs:
   1. Curl estimation (SPH-like sum over neighbors)
   2. Lorentz force computation
   3. Magnetic braking torque
   4. Dipole field superposition (for rendering/field lines)
   5. Full field-system and em-system pipeline"
  (:require
   [domain.em             :as em]
   [domain.spatial.index  :as idx]
   [domain.ecs.core       :as ecs]
   [domain.ecs.components :as c]
   [shape.spatial         :as sp]))

;; ---------------------------------------------------------------------------
;; Test data
;; ---------------------------------------------------------------------------

(defn- make-magnetized-world
  "World with N magnetized gas particles."
  [n extent]
  (let [world (ecs/empty-world)
        rng   (java.util.Random. 42)]
    (reduce (fn [w i]
              (let [[w' eid] (ecs/spawn w)
                    r  (* extent (Math/pow (.nextDouble rng) 0.33))
                    th (* 2.0 Math/PI (.nextDouble rng))
                    ph (Math/acos (- (* 2.0 (.nextDouble rng)) 1.0))]
                (ecs/put-components w' eid
                                    {c/position          (sp/vec3 (* r (Math/sin ph) (Math/cos th))
                                                                  (* r (Math/sin ph) (Math/sin th))
                                                                  (* r (Math/cos ph)))
                                     c/velocity          [0.0 0.0 0.0]
                                     c/mass              (/ 4.0e30 n)
                                     c/radius            (* extent 0.003)
                                     c/temperature       12.0
                                     c/density           1.0e-18
                                     c/pressure          1.0e-3
                                     c/matter-state      :nebula
                                     c/b-field           [0.0 0.0 (* 1.0e-9 (+ 0.5 (.nextDouble rng)))]
                                     c/angular-momentum  [0.0 0.0 (* 1.0e40 (.nextDouble rng))]
                                     c/rotation-axis     [0.0 0.0 1.0]})))
            world
            (range n))))

(defn- entity->em-data [world eid]
  {:eid      eid
   :position (ecs/get-component world eid c/position)
   :mass     (ecs/get-component world eid c/mass)
   :radius   (ecs/get-component world eid c/radius)
   :density  (ecs/get-component world eid c/density)
   :pressure (ecs/get-component world eid c/pressure)
   :b-field  (ecs/get-component world eid c/b-field)
   :angular-momentum (ecs/get-component world eid c/angular-momentum)
   :rotation-axis    (ecs/get-component world eid c/rotation-axis)
   :state    (ecs/get-component world eid c/matter-state)})

;; ---------------------------------------------------------------------------
;; Benchmarks
;; ---------------------------------------------------------------------------

(defn run [quick-bench full-bench]
  (let [w100   (make-magnetized-world 100 2.0e16)
        w500   (make-magnetized-world 500 2.0e16)
        w1000  (make-magnetized-world 1000 2.0e16)]

    ;; --- Pure field calculations ---
    (quick-bench "magnetic-pressure (1 call)"
                 (fn [] (em/magnetic-pressure [0.0 0.0 1.0e-6])))

    (quick-bench "alfven-speed (1 call)"
                 (fn [] (em/alfven-speed [0.0 0.0 1.0e-6] 1.0e-18)))

    (quick-bench "flux-freeze (1 call, 2x density increase)"
                 (fn [] (em/flux-freeze [0.0 0.0 1.0e-9] 1.0e-18 2.0e-18 0.0)))

    (quick-bench "resistive-decay (1 call)"
                 (fn [] (em/resistive-decay [0.0 0.0 1.0e-6] 6.957e8 1.0e12)))

    ;; --- Dipole field (for rendering) ---
    (quick-bench "dipole-field-at (1 call)"
                 (fn [] (em/dipole-field-at [1.0e20 0.0 0.0] [0.0 0.0 0.0] [1.0e10 0.0 0.0])))

    ;; --- Curl and Lorentz ---
    (let [data-100  (mapv #(entity->em-data w100 %) (ecs/entities-with w100 c/b-field))
          data-1000 (mapv #(entity->em-data w1000 %) (ecs/entities-with w1000 c/b-field))
          tree-100  (idx/build data-100)
          tree-1000 (idx/build data-1000)
          q         (first data-100)
          h         (* 2.0 (double (or (:radius q) 1.0)))
          nbrs      (idx/within-radius tree-100 (:position q) h)]

      (quick-bench "curl-estimate (1 particle, ~20 neighbors)"
                   (fn [] (em/curl-estimate (:b-field q) (:density q) (:position q) nbrs)))

      (quick-bench "lorentz-acceleration (1 particle)"
                   (fn []
                     (let [curl-b (em/curl-estimate (:b-field q) (:density q) (:position q) nbrs)]
                       (em/lorentz-acceleration (:b-field q) curl-b (:density q)))))

      (quick-bench "magnetic-braking-torque (1 particle)"
                   (fn [] (em/magnetic-braking-torque q 1.0e12))))

    ;; --- Full EM systems ---
    (quick-bench "field-system (100 particles)"
                 (fn [] ((em/field-system 1.0e12) w100)))

    (quick-bench "field-system (500 particles)"
                 (fn [] ((em/field-system 1.0e12) w500)))

    (quick-bench "field-system (1000 particles)"
                 (fn [] ((em/field-system 1.0e12) w1000)))

    (quick-bench "lorentz-acceleration-system (100 particles)"
                 (fn [] ((:run (em/lorentz-acceleration-system 1.0e12)) w100)))

    (quick-bench "lorentz-acceleration-system (1000 particles)"
                 (fn [] ((:run (em/lorentz-acceleration-system 1.0e12)) w1000)))

    (quick-bench "em-system (100 particles)"
                 (fn [] ((em/em-system 1.0e12) w100)))

    (quick-bench "em-system (1000 particles)"
                 (fn [] ((em/em-system 1.0e12) w1000)))

    ;; --- Scaling ---
    (println "\n  EM Scaling:")
    (println "    Curl estimation is neighbor-bound (like SPH density).")
    (println "    Lorentz is cheap per particle; cost dominated by curl.")
    (println "    Check if em-system ≈ field-system + lorentz-system.")))

(defn profile-iterations []
  (let [w (make-magnetized-world 1000 2.0e16)]
    ((em/em-system 1.0e12) w)))
