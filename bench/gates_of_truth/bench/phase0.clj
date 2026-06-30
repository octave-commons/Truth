(ns gates-of-truth.bench.phase0
  "Full Phase 0 tick benchmarks.

   This is the integration benchmark: how long does one complete world tick
   take? Tests the full pipeline:
   1. World creation (one-time setup)
   2. Single tick (the critical path)
   3. Tick components breakdown (identify the bottleneck system)
   4. Scaling with entity count
   5. Parallel vs sequential comparison"
  (:require
   [domain.phase0         :as phase0]
   [domain.ecs.core       :as ecs]
   [domain.ecs.tick       :as tick]
   [domain.ecs.components :as c]
   [domain.stellar        :as stellar]
   [domain.orbital.system :as orbital]
   [domain.hydro          :as hydro]
   [domain.em             :as em]
   [domain.physics.collision :as collision]
   [domain.regime         :as regime]))

;; ---------------------------------------------------------------------------
;; Test worlds
;; ---------------------------------------------------------------------------

(defn- make-small-world
  "Small world: 100 gas particles. Good for per-system profiling."
  []
  (phase0/create-world {:gas-count 100 :nebula-mass 4e29 :nebula-radius 1.0e16}))

(defn- make-medium-world
  "Medium world: 500 gas particles. Default game size."
  []
  (phase0/create-world {:gas-count 500 :nebula-mass 2e30 :nebula-radius 1.5e16}))

(defn- make-large-world
  "Large world: 1000 gas particles. Stress test."
  []
  (phase0/create-world {:gas-count 1000 :nebula-mass 4e30 :nebula-radius 2.0e16}))

;; ---------------------------------------------------------------------------
;; Benchmarks
;; ---------------------------------------------------------------------------

(defn run [quick-bench full-bench]
  ;; --- World creation ---
  (quick-bench "create-world (100 particles)"
    (fn [] (make-small-world)))

  (quick-bench "create-world (500 particles)"
    (fn [] (make-medium-world)))

  (quick-bench "create-world (1000 particles)"
    (fn [] (make-large-world)))

  ;; --- Single tick ---
  (let [w100  (make-small-world)
        w500  (make-medium-world)
        w1000 (make-large-world)]

    (quick-bench "tick-world (100 particles)"
      (fn [] (phase0/tick-world w100)))

    (quick-bench "tick-world (500 particles)"
      (fn [] (phase0/tick-world w500)))

    (quick-bench "tick-world (1000 particles)"
      (fn [] (phase0/tick-world w1000)))

    ;; --- Full benchmark for the critical path (use quick-bench for speed) ---
    (quick-bench "tick-world (500 particles) — critical path"
      (fn [] (phase0/tick-world w500)))

    ;; --- System-by-system breakdown ---
    (println "\n  System Breakdown (500 particles, one tick):")
    (println "  Measuring individual system costs to identify the bottleneck.")

    ;; Gravity (the usual suspect)
    (quick-bench "  gravity-acceleration system"
      (fn []
        (let [sys (orbital/gravity-acceleration 6.674e-11 0.5 1.0e14)]
          ((:run sys) w500))))

    ;; Motion integration
    (quick-bench "  motion-integration system"
      (fn []
        (let [sys (orbital/motion-integration (:sim/dt w500))]
          ((:run sys) w500))))

    ;; SPH density
    (quick-bench "  density-system"
      (fn [] ((hydro/density-system (:sim/dt w500)) w500)))

    ;; Pressure acceleration
    (quick-bench "  pressure-acceleration system"
      (fn []
        (let [sys (hydro/pressure-acceleration)]
          ((:run sys) w500))))

    ;; EM field
    (quick-bench "  field-system"
      (fn [] ((em/field-system (:sim/dt w500)) w500)))

    ;; Lorentz
    (quick-bench "  lorentz-acceleration system"
      (fn []
        (let [sys (em/lorentz-acceleration-system)]
          ((:run sys) w500))))

    ;; Collision
    (quick-bench "  collision-detection-system"
      (fn [] (collision/collision-detection-system w500)))

    ;; Stellar systems
    (quick-bench "  stellar structure-system"
      (fn [] ((:run (stellar/structure-system)) w500)))

    (quick-bench "  stellar classifier-system"
      (fn [] ((:run (stellar/classifier-system)) w500)))

    (quick-bench "  stellar temperature-system"
      (fn [] ((:run (stellar/temperature-system (:sim/dt w500))) w500)))

    ;; Regime
    (quick-bench "  regime-system"
      (fn [] (regime/regime-system w500)))

    ;; --- Parallel vs sequential ---
    (println "\n  Parallel vs Sequential (500 particles):")

    (quick-bench "  full parallel tick"
      (fn [] (phase0/step-physics w500)))

    ;; Sequential fallback
    (quick-bench "  full sequential tick (approximate)"
      (fn []
        ;; Run each system sequentially (approximates non-parallel path)
        (-> w500
            ((:run (stellar/structure-system)))
            ((:run (stellar/eos-system)))
            ((:run (stellar/classifier-system)))
            ((:run (stellar/temperature-system (:sim/dt w500))))
            ((:run (em/field-system (:sim/dt w500))))
            (collision/collision-detection-system)
            (stellar/fusion-promotion-system)
            (stellar/deuterium-depletion-system)
            (stellar/sink-formation-system)
            ((:run (stellar/disk-evolution-system)))
            ((:run (stellar/stellar-wind-system)))
            ((:run (stellar/stellar-flare-system)))
            (phase0/recenter-system))))

    ;; --- Multi-tick scaling ---
    (println "\n  Multi-tick Scaling (500 particles):")
    (quick-bench "  10 ticks"
      (fn []
        (loop [w w500 n 10]
          (if (pos? n)
            (recur (phase0/tick-world w) (dec n))
            w))))

    ;; --- Target analysis ---
    (println)
    (println "  Phase 0 Tick Analysis:")
    (println "    Target: 16.6ms per tick for 60 Hz rendering.")
    (println "    If tick > 16.6ms, identify the dominant system above.")
    (println "    Gravity is usually the bottleneck at high N.")
    (println "    SPH/EM neighbor queries scale with particle count.")
    (println "    Collision cost depends on body density (overlaps).")
    (println "    If parallel ≈ sequential: check write-set sizes.")))

(defn profile-iterations []
  (let [w (make-medium-world)]
    (phase0/tick-world w)))
