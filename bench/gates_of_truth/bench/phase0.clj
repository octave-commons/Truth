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
   [domain.regime         :as regime]
   [domain.intervention   :as intervention]
   [domain.player         :as player]
   [domain.pacing         :as pacing]
   [domain.spatial.index  :as spatial]
   [domain.chemistry      :as chemistry]
   [clojure.pprint]))

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

(defn- nanos->ms [nanos] (/ (double nanos) 1e6))

(defn profile-tick-phases-on
  "Measure each phase of tick-world on the given world. Returns a map of phase → ms."
  [w]
  (let [t0 (System/nanoTime)
        world1 (-> (ecs/advance-tick w)
                   (assoc :phase0/frame-offset (phase0/center-of-mass w))
                   (domain.spatial.index/spatial-index))
        t1 (System/nanoTime)
        world2 (phase0/step-physics world1)
        t2 (System/nanoTime)
        world3 (-> world2
                   (intervention/expire-interventions)
                   phase0/materialize-lifecycle)
        t3 (System/nanoTime)
        summ (phase0/system-summary world3)
        t4 (System/nanoTime)
        complexity (stellar/complexity-score summ)
        phase (phase0/detect-phase summ (:phase0/sim-time world3))
        t5 (System/nanoTime)
        stats (phase0/stats-of world3 summ)
        t6 (System/nanoTime)
        pacing (when-not (false? (:phase0/adaptive-pacing? world3))
                 (-> (pacing/pace world3)
                     (pacing/with-time-slip false)))
        t7 (System/nanoTime)
        prev (phase0/system-summary w)
        world4 (cond-> world3
                 (and (:star? summ) (not (:star? prev)))
                 (phase0/emit-threshold :event/stellar-ignition (first (:stars summ)))

                 (> (:planet-count summ) (:planet-count prev))
                 (phase0/emit-threshold :event/planet-formation (first (:planets summ)))

                 (not= phase (:phase0/phase w))
                 (phase0/emit-threshold :event/phase-transition {:from (:phase0/phase w) :to phase}))
        world5 (cond-> (assoc world4
                         :phase0/complexity complexity
                         :phase0/stats      stats
                         :phase0/phase      phase
                         :phase0/sim-time   (+ (:phase0/sim-time world4) (:sim/dt w)))
                 pacing (assoc :phase0/time-scale    (:rate pacing)
                               :phase0/rate-yr       (:rate-yr pacing)
                               :phase0/time-slipping? (boolean (:time-slipping? pacing))
                               :sim/dt               (:dt pacing)
                               :sim/softening        (:softening pacing)))
        t8 (System/nanoTime)
        world6 ((player/observer-system (:sim/dt w)) world5)
        t9 (System/nanoTime)]
    {:advance-com-spatial (nanos->ms (- t1 t0))
     :step-physics        (nanos->ms (- t2 t1))
     :expire+materialize  (nanos->ms (- t3 t2))
     :system-summary      (nanos->ms (- t4 t3))
     :detect+complexity   (nanos->ms (- t5 t4))
     :stats-of            (nanos->ms (- t6 t5))
     :pacing              (nanos->ms (- t7 t6))
     :events+clock        (nanos->ms (- t8 t7))
     :observer-system     (nanos->ms (- t9 t8))
     :total               (nanos->ms (- t9 t0))}))

(defn profile-tick-phases
  "Measure each phase of tick-world on a fresh 500-particle world using the
   actual intermediate states. Warms up with 5 discarded samples, then averages
   10 samples. Returns a map of phase → ms."
  []
  (let [w (make-medium-world)
        _ (dotimes [_ 5] (profile-tick-phases-on w))
        samples (doall (repeatedly 10 #(profile-tick-phases-on w)))
        ks (keys (first samples))
        avg (fn [k] (/ (reduce + (map k samples)) (count samples)))]
    (into {:samples 10} (for [k ks] [k (avg k)]))))

(defn step-physics-sequential
  "Reference sequential step-physics using write-set folding without futures."
  [world]
  (let [all-systems (phase0/physics-systems-parallel world)
        emitters    (remove #(= :integrator (:id %)) all-systems)
        integrator  (first (filter #(= :integrator (:id %)) all-systems))]
    (-> world
        (tick/run-sequential emitters)
        (tick/run-sequential [integrator]))))

(defn profile-step-physics-systems-on
  "Run each system in physics-systems-parallel on the given world and report
   wall-clock ms per system."
  [w label]
  (println (format "\n  Per-system step-physics profile (%s):" label))
  (let [systems (phase0/physics-systems-parallel w)]
    (doseq [{:keys [id run]} systems]
      (let [t0 (System/nanoTime)
            _ (run w)
            t1 (System/nanoTime)]
        (println (format "  %-40s %.3f ms" (str id) (nanos->ms (- t1 t0))))))))

(defn profile-step-physics-systems
  "Run each system in physics-systems-parallel once on a 500-particle world and
   report wall-clock ms per system. Helps find which unbenchmarked systems are
   dominating step-physics."
  []
  (profile-step-physics-systems-on (make-medium-world) "initial w500"))

(defn run [quick-bench full-bench]
  ;; --- Realistic tick phase profile ---
  (println "\n  Realistic tick-world phase profile (500 particles, 10 samples avg):")
  (clojure.pprint/pprint (profile-tick-phases))

  ;; --- Per-system step-physics profile ---
  (println "\n  Per-system step-physics profile (initial w500, one sample each):")
  (profile-step-physics-systems)
  (let [w500 (make-medium-world)
        w1 (-> w500
                 (ecs/advance-tick)
                 (assoc :phase0/frame-offset (phase0/center-of-mass w500))
                 (domain.spatial.index/spatial-index))]
    (profile-step-physics-systems-on w1 "world1 with spatial tree"))

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

    ;; --- Overhead on post-physics world (500 particles) ---
    (println "\n  Overhead functions measured on post-physics world (500 particles):")
    (let [w1 (-> (ecs/advance-tick w500)
                  (assoc :phase0/frame-offset (phase0/center-of-mass w500))
                  (domain.spatial.index/spatial-index))
          w2 (phase0/step-physics w1)
          summ (phase0/system-summary w2)]
      (quick-bench "  advance-tick + center-of-mass + spatial-index"
        (fn [] (-> (ecs/advance-tick w500)
                   (assoc :phase0/frame-offset (phase0/center-of-mass w500))
                   (domain.spatial.index/spatial-index))))
      (quick-bench "  system-summary (post-physics)"
        (fn [] (phase0/system-summary w2)))
      (quick-bench "  stats-of (post-physics)"
        (fn [] (phase0/stats-of w2 summ)))
      (quick-bench "  detect-phase + complexity-score (post-physics)"
        (fn [] (let [s (phase0/system-summary w2)]
                 (phase0/detect-phase s (:phase0/sim-time w2))
                 (stellar/complexity-score s))))
      (quick-bench "  pacing (post-physics)"
        (fn [] (when-not (false? (:phase0/adaptive-pacing? w2))
                 (-> (pacing/pace w2)
                     (pacing/with-time-slip false)))))
      (quick-bench "  observer-system (post-physics)"
        (fn [] ((player/observer-system (:sim/dt w2)) w2)))
      (quick-bench "  non-physics tick overhead"
        (fn [] (let [summ (phase0/system-summary w2)
                     complexity (stellar/complexity-score summ)
                     phase (phase0/detect-phase summ (:phase0/sim-time w2))
                     stats (phase0/stats-of w2 summ)
                     pacing (when-not (false? (:phase0/adaptive-pacing? w2))
                              (-> (pacing/pace w2)
                                  (pacing/with-time-slip false)))]
                 (cond-> (assoc w2
                              :phase0/complexity complexity
                              :phase0/stats      stats
                              :phase0/phase      phase
                              :phase0/sim-time   (+ (:phase0/sim-time w2) (:sim/dt w2))
                              :phase0/_prev-summary summ)
                   pacing (assoc :phase0/time-scale    (:rate pacing)
                                 :phase0/rate-yr       (:rate-yr pacing)
                                 :phase0/time-slipping? (boolean (:time-slipping? pacing))
                                 :sim/dt               (:dt pacing)
                                                                                                                                       :sim/softening        (:softening pacing)))))))

    ;; --- Tick overhead breakdown ---
    (println "\n  Tick overhead breakdown (500 particles):")

    (quick-bench "  advance-tick + center-of-mass + spatial-index"
      (fn [] (-> (ecs/advance-tick w500)
                 (assoc :phase0/frame-offset (phase0/center-of-mass w500))
                 (domain.spatial.index/spatial-index))))

    (quick-bench "  expire-interventions + materialize-lifecycle"
      (fn [] (-> w500
                 (intervention/expire-interventions)
                 phase0/materialize-lifecycle)))

    (quick-bench "  system-summary"
      (fn [] (phase0/system-summary w500)))

    (quick-bench "  stats-of"
      (fn [] (phase0/stats-of w500 (phase0/system-summary w500))))

    (quick-bench "  detect-phase + complexity-score"
      (fn [] (let [summ (phase0/system-summary w500)]
               (phase0/detect-phase summ (:phase0/sim-time w500))
               (stellar/complexity-score summ))))

    (quick-bench "  pacing"
      (fn [] (when-not (false? (:phase0/adaptive-pacing? w500))
               (-> (pacing/pace w500)
                   (pacing/with-time-slip false)))))

    (quick-bench "  observer-system"
      (fn [] ((player/observer-system (:sim/dt w500)) w500)))

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
        (let [sys (em/lorentz-acceleration-system (:sim/dt w500))]
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

    ;; --- Parallel vs sequential step-physics ---
    (println "\n  Step-physics parallel vs sequential (500 particles):")
    (quick-bench "  step-physics parallel (on initial w500)"
      (fn [] (phase0/step-physics w500)))
    (let [w1 (-> (ecs/advance-tick w500)
                 (assoc :phase0/frame-offset (phase0/center-of-mass w500))
                 (domain.spatial.index/spatial-index))]
      (quick-bench "  step-physics parallel (on world1 with spatial tree)"
        (fn [] (phase0/step-physics w1))))
    (quick-bench "  step-physics sequential"
      (fn [] (step-physics-sequential w500)))

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
            ((:run (stellar/deuterium-depletion-system)))
            (stellar/sink-formation-system)
            (stellar/disk-evolution-system)
            ((:run (stellar/stellar-wind-system)))
            ((:run (stellar/stellar-flare-system))))))

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
