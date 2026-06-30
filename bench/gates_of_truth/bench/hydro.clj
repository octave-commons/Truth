(ns gates-of-truth.bench.hydro
  "SPH hydrodynamics benchmarks.

   Tests the two dominant SPH costs:
   1. Density pass: neighbor finding + kernel evaluation per particle
   2. Pressure gradient: neighbor finding + kernel gradient per particle

   The spatial index (octree) replaces brute-force O(N²) neighbor search
   with O(N log N), but the kernel math itself is still significant."
  (:require
   [domain.hydro          :as hydro]
   [domain.spatial.index  :as idx]
   [domain.ecs.core       :as ecs]
   [domain.ecs.components :as c]
   [shape.spatial         :as sp]))

;; ---------------------------------------------------------------------------
;; Test data
;; ---------------------------------------------------------------------------

(defn- make-gas-particles
  "N gas particles in a sphere, each with SPH-relevant components."
  [n extent]
  (let [world (ecs/empty-world)
        rng   (java.util.Random. 42)]
    (reduce (fn [w i]
              (let [[w' eid] (ecs/spawn w)
                    r  (* extent (Math/pow (.nextDouble rng) 0.33))
                    th (* 2.0 Math/PI (.nextDouble rng))
                    ph (Math/acos (- (* 2.0 (.nextDouble rng)) 1.0))
                    pos (sp/vec3 (* r (Math/sin ph) (Math/cos th))
                                (* r (Math/sin ph) (Math/sin th))
                                (* r (Math/cos ph)))]
                (ecs/put-components w' eid
                  {c/position     pos
                   c/velocity     [0.0 0.0 0.0]
                   c/mass         (/ 4.0e30 n)
                   c/radius       (* extent 0.003)
                   c/temperature  12.0
                   c/density      1.0e-18
                   c/pressure     1.0e-3
                   c/matter-state :nebula})))
            world
            (range n))))

(defn- entity->hydro-data [world eid]
  {:eid         eid
   :position    (ecs/get-component world eid c/position)
   :velocity    (ecs/get-component world eid c/velocity)
   :mass        (ecs/get-component world eid c/mass)
   :radius      (ecs/get-component world eid c/radius)
   :density     (ecs/get-component world eid c/density)
   :pressure    (ecs/get-component world eid c/pressure)
   :temperature (ecs/get-component world eid c/temperature)
   :state       (ecs/get-component world eid c/matter-state)})

;; ---------------------------------------------------------------------------
;; Benchmarks
;; ---------------------------------------------------------------------------

(defn run [quick-bench full-bench]
  (let [w100   (make-gas-particles 100 2.0e16)
        w500   (make-gas-particles 500 2.0e16)
        w1000  (make-gas-particles 1000 2.0e16)]

    ;; --- Kernel math ---
    (quick-bench "cubic-spline-w (1 call)"
      (fn [] (hydro/cubic-spline-w 0.5)))

    (quick-bench "kernel (1 call, h=1e14)"
      (fn [] (hydro/kernel 5.0e13 1.0e14)))

    (quick-bench "kernel-gradient (1 call)"
      (fn [] (hydro/kernel-gradient [1.0e13 2.0e13 0.0] 1.0e14)))

    ;; --- Density computation ---
    (let [data-100  (mapv #(entity->hydro-data w100 %) (ecs/entities-with w100 c/matter-state c/position))
          data-500  (mapv #(entity->hydro-data w500 %) (ecs/entities-with w500 c/matter-state c/position))
          data-1000 (mapv #(entity->hydro-data w1000 %) (ecs/entities-with w1000 c/matter-state c/position))]

      ;; Spatial index build
      (quick-bench "spatial index build (100 particles)"
        (fn [] (idx/build data-100)))

      (quick-bench "spatial index build (500 particles)"
        (fn [] (idx/build data-500)))

      (quick-bench "spatial index build (1000 particles)"
        (fn [] (idx/build data-1000)))

      ;; Neighbor queries
      (let [tree-100  (idx/build data-100)
            tree-500  (idx/build data-500)
            tree-1000 (idx/build data-1000)
            q100      (first data-100)
            h         (* 2.0 (double (or (:radius q100) 1.0)))]

        (quick-bench "within-radius (1 query, 100 particles)"
          (fn [] (idx/within-radius tree-100 (:position q100) h)))

        (quick-bench "within-radius (1 query, 500 particles)"
          (fn [] (idx/within-radius tree-500 (:position q100) h)))

        (quick-bench "within-radius (1 query, 1000 particles)"
          (fn [] (idx/within-radius tree-1000 (:position q100) h)))

        (quick-bench "nearest-dist (1 query, 100 particles)"
          (fn [] (idx/nearest-dist tree-100 (:position q100) (:eid q100))))

        (quick-bench "nearest-dist (1 query, 1000 particles)"
          (fn [] (idx/nearest-dist tree-1000 (:position q100) (:eid q100)))))

      ;; Density for one particle
      (let [tree-100 (idx/build data-100)
            q        (first data-100)
            h        (* 2.0 (double (or (:radius q) 1.0)))
            nbrs     (idx/within-radius tree-100 (:position q) h)]

        (quick-bench "sph-density (1 particle, ~20 neighbors)"
          (fn [] (hydro/sph-density (assoc q :radius (* 0.5 h)) nbrs)))

        (quick-bench "pressure-gradient-acceleration (1 particle)"
          (fn [] (hydro/pressure-gradient-acceleration q nbrs)))))

    ;; --- Full SPH pipeline ---
    (quick-bench "density-system (100 particles)"
      (fn [] ((hydro/density-system 1.0e12) w100)))

    (quick-bench "density-system (500 particles)"
      (fn [] ((hydro/density-system 1.0e12) w500)))

    (quick-bench "density-system (1000 particles)"
      (fn [] ((hydro/density-system 1.0e12) w1000)))

    (quick-bench "pressure-acceleration system (100 particles)"
      (fn [] ((:run (hydro/pressure-acceleration)) w100)))

    (quick-bench "pressure-acceleration system (1000 particles)"
      (fn [] ((:run (hydro/pressure-acceleration)) w1000)))

    ;; --- Scaling summary ---
    (println "\n  SPH Scaling:")
    (println "    Spatial index build should be O(N log N).")
    (println "    Density pass: N × (neighbor-finding + kernel-sum).")
    (println "    Check if neighbor count grows with N (it shouldn't if h adapts).")))

(defn profile-iterations []
  (let [w (make-gas-particles 1000 2.0e16)]
    ((hydro/density-system 1.0e12) w)))
