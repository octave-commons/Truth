(ns domain.integrator.universal-substep-test
  "Universal compact sub-stepping — the fling-machine regression suite.

   Card: kanban/tasks/universal-compact-substepping.md.
   Design: docs/designs/multi-timescale-integration.md §9.
   Research: docs/research/physics/cluster-dispersal-integration-heating.md §3.3
   (the fling machine) and §6 (the test contract this suite implements).

   THE BUG: a compact body (:planet/:gas-giant/:stellar-remnant) whose 100×
   dominance gate fails — ALWAYS, in the embedded formation era — fell through
   `compact-advance` to ONE raw symplectic-Euler step at the global dt. At live
   pacing (dt = 2.5e9 s ≈ 80 yr) any tidal acceleration a injects |Δv| = a·dt
   (~10⁵–10⁹ m/s) in a single tick: an instant ejection.

   THE FIX (design §9, sub-cycling with self-consistent intermediate
   positions): a gate-failing compact body takes K KDK-leapfrog steps covering
   the FULL dt, with gravity RE-EVALUATED at each intermediate position from
   the frozen world's spatial tree (kinematics/gravity-at — the rest of the
   world frozen) and every other accel channel frozen at its snapshot value.
   KDK is symplectic in the frozen-others potential: bounded energy error per
   tick, no secular drift, no phase-lag — the body covers the full dt every
   world tick, so close encounters resolve and smooth couplings integrate at
   full rate. A per-tick K-loop of the FROZEN force was verified inert before
   this landed (Σ a·h = a·dt: byte-identical to the raw fling), and a
   one-h-step-per-tick block-step was rejected on review (1/K-rate phase-lag:
   encounters never land, smooth couplings freeze).

   THE GEOMETRY (chosen by elimination, validated against a velocity-Verlet
   reference at T/2000): a gate-failing tide must exceed 1% of the host's
   pull, and at that strength
   - a CLOSE perturber (5–20 M_sun at 40–100 AU) strips a 5 AU planet in
     ~15–100 orbits under EXACT integration (resonant fixed-direction wind;
     coplanar Stark resonance) — no survival window exists;
   - a co-orbiting perturber is physically right but forces the test to model
     two more moving bodies;
   so the perturber is DISTANT and MASSIVE (12,600 M_sun pinned at 4000 AU):
   the tide is a near-uniform 2% wind (gradient 0.01%, Stark saddle at ~35 AU,
   Lidov–Kozai timescale ~4.5e5 yr ≫ the run). The giant's orbit plane is
   PERPENDICULAR to the wind, killing the fast coplanar Stark resonance —
   the reference holds r = 5.00 ± 0.10 AU and conserves E_J to 6 digits over
   the full 80 kyr the 1000-tick run now covers."
  (:require
   [clojure.test :refer [deftest is testing]]
   [domain.ecs.components :as c]
   [domain.ecs.core :as ecs]
   [domain.ecs.tick :as tick]
   [domain.integrator.kinematics :as kinematics]
   [domain.orbital.system :as orbital]
   [domain.physics.cache.soa :as pcache-soa]
   [domain.spatial.index :as spatial]
   [domain.stellar.seeder :as seeder]
   [law.stellar :as law]
   [shape.spatial :as sp]))

;; --- Live-scale pacing constants (mirror multi_timescale_regression_test) ---

(def ^:private live-dt
  "Live late-sim tick length (s): ~80 yr/tick, pacing.clj dilated regime."
  2.5e9)

(def ^:private live-softening
  "Live Plummer softening (m): ~3342 AU, the pacing-soft-max ceiling."
  5.0e14)

(def ^:private live-cutoff
  "Gravitational dead-zone radius (m), 0.1·softening per genesis/systems.clj."
  (* 0.1 live-softening))

(def ^:private live-theta 0.5)

(def ^:private orbit-radius
  "Giant's initial orbital radius (m): 5 AU — the placement arm's floor band."
  (* 5.0 law/au))

(def ^:private giant-mass
  "Test giant mass (kg): one Jupiter — a real compact body."
  law/jupiter-mass)

;; --- Fling world: host + distant massive perturber + embedded giant ---------

(def ^:private perturber-mass
  "Perturber mass (kg): 12,600 M_sun pinned at 4000 AU (see the ns docstring
   for the geometry elimination). With the host never advanced (a-parent = 0
   in the gate), the gate's a-tidal is the perturber's DIRECT pull on the
   giant, GM_p/R² = 4.7e-6 m/s² ≈ 2% of the host's μ/r² — 100× that fails
   the gate ~2× everywhere on the orbit (margins absorb the body→parent pull
   and r-jitter, so the gate never flips to a WH tick)."
  (* 12600.0 law/solar-mass))

(def ^:private pert-distance
  "Perturber's pinned distance from the host (m): 4000 AU."
  (* 4000.0 law/au))

(def ^:private never-due
  "LOD tick phase that is never due within any practical tick count:
   due when tick ≡ phase (mod period), i.e. first due at tick = 10^15."
  {:period 1000000000000 :phase 999999999999999})

(defn- fling-world
  "A 1 M_sun host star pinned at the origin, a 12,600 M_sun :star perturber
   pinned at +4000 AU, and one Jupiter-mass :gas-giant at +5 AU on a
   Newtonian-circular orbit whose plane is PERPENDICULAR to the tide
   direction (see the ns docstring). Both field bodies carry `never-due` LOD
   phases (with :lod/throttle-ticks? on) so the integrator never advances
   them — a fixed tidal field. (A free host star under the perturber's pull
   would itself take raw Euler steps — stars never sub-step — and fling.)
   Returns [world star-eid perturber-eid giant-eid]."
  []
  (let [v-circ (law/newtonian-circular-speed law/solar-mass orbit-radius)
        [w star] (seeder/spawn-clump
                  (ecs/empty-world)
                  {:position [0.0 0.0 0.0] :velocity [0.0 0.0 0.0]
                   :mass law/solar-mass :radius law/solar-radius
                   :matter-state :star :temperature 5800.0})
        [w perturber] (seeder/spawn-clump
                       w
                       {:position [pert-distance 0.0 0.0] :velocity [0.0 0.0 0.0]
                        :mass perturber-mass :radius law/solar-radius
                        :matter-state :star :temperature 5800.0})
        [w giant] (seeder/spawn-clump
                   w
                   {:position [0.0 orbit-radius 0.0]
                    :velocity [0.0 0.0 v-circ]
                    :mass giant-mass :radius 7.0e7
                    :matter-state :gas-giant :temperature 300.0})]
    [(-> w
         (ecs/put-component star c/lod-tick-phase never-due)
         (ecs/put-component perturber c/lod-tick-phase never-due)
         (assoc :lod/throttle-ticks? true
                :sim/G law/G :sim/theta live-theta
                :sim/dt live-dt :sim/softening live-softening
                :sim/cutoff live-cutoff
                :tick 0))
     star perturber giant]))

(defn- pin-step
  "One tick of the fixed tidal field: rebuild the spatial index, run the real
   Barnes–Hut gravity channel, THEN run the real kinematics-ws integrator
   against the post-gravity world — sequential, not barriered — undo the
   per-tick COM frame-offset on the SUBJECT, and re-seat the field bodies to
   their fixed states via `field-fns` ({eid (fn [t] [x v])}).

   Why sequential: the §6 contract's 'fixed tidal field' is F(x) — the force
   law evaluated at the subject's CURRENT position. The production barrier
   delivers forces one tick stale (documented Jacobi lag); with it, the
   giant's first-tick force corresponds to a position one full tick old —
   an artifact of the substrate's staleness (orthogonal to this card), not
   of the fixed field the contract specifies. The map-tree kernel is used
   (no SoA), so forces are evaluated at snapshot positions — exactly F(x).
   The sub-cycle under test re-evaluates gravity per intermediate position
   from the same tree, so within-tick forces are consistent."
  [w subject-eid field-fns]
  (let [w-indexed (spatial/spatial-index w)
        foff (or (:genesis/frame-offset w-indexed) [0.0 0.0 0.0])
        w-g (tick/run-parallel w-indexed
                               [(orbital/gravity-acceleration law/G live-theta live-softening)])
        w' (-> w-g
               (tick/run-parallel
                [{:id     :kinematics
                  :writes #{c/position c/velocity}
                  :run    (fn [frozen] (kinematics/kinematics-ws frozen live-dt))}])
               (update :tick (fnil inc 0)))
        t (* (double (:tick w')) live-dt)]
    (reduce (fn [acc [eid f]]
              (let [[x v] (f t)]
                (-> acc
                    (ecs/put-component eid c/position x)
                    (ecs/put-component eid c/velocity v))))
            (ecs/put-component w' subject-eid c/position
                               (sp/v+ (ecs/get-component w' subject-eid c/position)
                                      foff))
            field-fns)))

(defn- jacobi-energy
  "Specific energy (J/kg) of the giant in the STATIC combined potential of
   host + perturber (both pinned, zero velocity): E_J = u²/2 − μ_*/r − μ_p/r_p.
   Exactly conserved by the true dynamics (verified to 6 digits over 80 kyr
   by the velocity-Verlet reference); a bounded oscillation is the symplectic
   signature, secular drift is the integrator's failure."
  [w star perturber giant]
  (let [x (ecs/get-component w giant c/position)
        u (sp/len (ecs/get-component w giant c/velocity))
        r (sp/dist x (ecs/get-component w star c/position))
        rp (sp/dist x (ecs/get-component w perturber c/position))]
    (- (/ (* u u) 2.0)
       (/ (* law/G law/solar-mass) r)
       (/ (* law/G perturber-mass) rp))))

(defn- relative-energy
  "Specific two-body energy (J/kg) of the giant RELATIVE to the host star."
  [w star giant]
  (let [r (sp/dist (ecs/get-component w giant c/position)
                   (ecs/get-component w star c/position))
        u (sp/len (sp/v- (ecs/get-component w giant c/velocity)
                         (ecs/get-component w star c/velocity)))]
    (- (/ (* u u) 2.0)
       (/ (* law/G (+ law/solar-mass giant-mass)) r))))

;; --- Card suite -------------------------------------------------------------

(deftest fling-machine-regression
  (testing "notebook §6 contract, sub-cycling form: a :gas-giant embedded in a
            fixed tidal field that FAILS the dominance gate, at live dt =
            2.5e9 s. Pre-fix the fallthrough was one raw Euler step: |Δv| =
            |a|·dt ≈ 3.5e5 m/s in a single tick and the giant was ejected
            (RED captured against the pre-fix implementation: max per-tick
            |Δv| = 351,052 m/s, ejection to 5.7M AU). Post-fix the giant
            sub-cycles the full dt with gravity re-evaluated per intermediate
            position: it covers 80,000 years over 1000 ticks (full-time
            coverage — the swept angle tracks ω·t, NOT ω·t/K), the orbit
            stays coherent (true dynamics: r = 5.00 ± 0.10 AU over 80 kyr),
            and E_J oscillates inside a symplectic envelope."
    (let [[w star perturber giant] (fling-world)
          mu (* law/G (+ law/solar-mass giant-mass))
          k-exp (kinematics/substep-count
                 live-dt {:r orbit-radius :mu mu
                          :softening live-softening :eid giant})
          _ (println "[fling-test] K observed at 5 AU, dt=80 yr (parent-orbit"
                     "criterion):" k-exp)
          w1 (pin-step w giant {star (fn [_] [[0.0 0.0 0.0] [0.0 0.0 0.0]])
                                perturber (fn [_] [[pert-distance 0.0 0.0] [0.0 0.0 0.0]])})
          a1 (ecs/get-component w1 giant c/accel-gravity)
          gate (kinematics/dominance-gate w1 giant star false a1)]
      (is (some? gate) "gate computable for the giant/host pair")
      (is (not (:pass? gate))
          (str "the 100× dominance gate must FAIL in this setup (the embedded "
               "era); |a-tidal| = " (some-> (:a-tidal gate) sp/len)
               " vs μ/r² = " (:a-parent-pull gate)))
      (is (<= 100 k-exp 200)
          (str "K ≈ 142 from the parent-orbit criterion (period term binds); "
               "observed " k-exp))
      (let [vs (atom [(ecs/get-component w1 giant c/velocity)])
            rs (atom [(sp/dist (ecs/get-component w1 giant c/position)
                               (ecs/get-component w1 star c/position))])
            es (atom [(jacobi-energy w1 star perturber giant)])
            crossings (atom 0)
            prev-z (atom (nth (ecs/get-component w1 giant c/position) 2))]
        (loop [w w1, i 1]
          (when (< i 1000)
            (let [w' (pin-step w giant {star (fn [_] [[0.0 0.0 0.0] [0.0 0.0 0.0]])
                                        perturber (fn [_] [[pert-distance 0.0 0.0] [0.0 0.0 0.0]])})
                  x' (ecs/get-component w' giant c/position)
                  z' (nth x' 2)]
              (swap! vs conj (ecs/get-component w' giant c/velocity))
              (swap! rs conj (sp/dist x' (ecs/get-component w' star c/position)))
              (swap! es conj (jacobi-energy w' star perturber giant))
              (when (neg? (* z' @prev-z)) (swap! crossings inc))
              (reset! prev-z z')
              (recur w' (inc i)))))
        (let [dvs (map (fn [v0 v1] (sp/len (sp/v- v1 v0))) @vs (rest @vs))
              max-dv (apply max dvs)
              max-r (apply max @rs)
              es-d (map double @es)
              e0 (first es-d)
              e-span (- (apply max es-d) (apply min es-d))
              t-total (* 1000.0 live-dt)
              t-orb (* 2.0 Math/PI (Math/sqrt (/ (* orbit-radius orbit-radius orbit-radius) mu)))
              expected-crossings (* 2.0 (/ t-total t-orb))]
          (println "[fling-test] max per-tick |Δv| over 1000 ticks:" max-dv
                   "m/s; max separation:" (/ max-r law/au)
                   "AU; E_J span:" e-span "vs |E0| =" (abs e0)
                   "; z-crossings:" @crossings "vs full-time ≈"
                   (long expected-crossings))
          (is (< max-dv 3.0e4)
              (str "per-tick |Δv| is the REAL orbital velocity change over dt "
                   "(≤ 2·v_circ ≈ 2.7e4 m/s), never the a·dt ≈ 3.5e5 m/s fling; "
                   "observed max " max-dv))
          (is (< max-r (* 7.0 law/au))
              (str "never ejected: max separation over 80 kyr < 7 AU (the true "
                   "trajectory holds 5.00 ± 0.10 AU); observed " (/ max-r law/au)
                   " AU"))
          (is (every? neg? es-d)
              (str "the giant stays BOUND in the combined potential (negative "
                   "Jacobi energy) for the whole 10³-tick run"))
          (is (< e-span (* 0.1 (abs e0)))
              (str "Jacobi energy oscillates within 10% of |E0| — a physical "
                   "envelope, no secular drift; span " e-span
                   " vs bound " (* 0.1 (abs e0))))
          (is (> @crossings 150)
              (str "FULL-TIME COVERAGE: at dt/tick the giant sweeps ~7.15 "
                   "orbits per tick, so its z-coordinate churns sign ~290 "
                   "times over the run (the rejected 1/K-rate semantics would "
                   "manage ~100); observed " @crossings)))))))

(deftest slingshot-resolves-through-periapsis
  (testing "The encounter the rejected semantics could never land: a giant on
            an eccentric orbit (a = 12.5 AU, e = 0.6 — periapsis 5 AU,
            apoapsis 20 AU) through the same gate-failing tide, 100 ticks =
            8000 yr ≈ 181 orbits and 181 periapsis passages. The sub-cycle
            resolves each passage in the frozen-star two-body problem: relative
            energy and angular momentum stay bounded, the giant neither plunges
            nor ejects — where the pre-fix raw step injects a_peri·dt ≈ 5.9e5
            m/s per tick at periapsis."
    (let [a-orb (* 12.5 law/au)
          e-orb 0.6
          r-peri (* a-orb (- 1.0 e-orb))
          mu-orb (* law/G law/solar-mass)
          v-ap (Math/sqrt (/ (* mu-orb (- 1.0 e-orb)) (* a-orb (+ 1.0 e-orb))))
          [w star] (seeder/spawn-clump
                    (ecs/empty-world)
                    {:position [0.0 0.0 0.0] :velocity [0.0 0.0 0.0]
                     :mass law/solar-mass :radius law/solar-radius
                     :matter-state :star :temperature 5800.0})
          [w perturber] (seeder/spawn-clump
                         w
                         {:position [pert-distance 0.0 0.0] :velocity [0.0 0.0 0.0]
                          :mass perturber-mass :radius law/solar-radius
                          :matter-state :star :temperature 5800.0})
          ;; Start at apoapsis on the +y axis, velocity along -z (orbit plane
          ;; y-z, perpendicular to the wind as in the fling test).
          [w giant] (seeder/spawn-clump
                     w
                     {:position [0.0 (* a-orb (+ 1.0 e-orb)) 0.0]
                      :velocity [0.0 0.0 (- v-ap)]
                      :mass giant-mass :radius 7.0e7
                      :matter-state :gas-giant :temperature 300.0})
          w (-> w
                (ecs/put-component star c/lod-tick-phase never-due)
                (ecs/put-component perturber c/lod-tick-phase never-due)
                (assoc :lod/throttle-ticks? true
                       :sim/G law/G :sim/theta live-theta
                       :sim/dt live-dt :sim/softening live-softening
                       :sim/cutoff live-cutoff
                       :tick 0))
          w (spatial/spatial-index w)
          e0 (relative-energy w star giant)
          l0 (sp/len (sp/cross (ecs/get-component w giant c/position)
                               (sp/v- (ecs/get-component w giant c/velocity)
                                      (ecs/get-component w star c/velocity))))
          a-peri (sp/len (kinematics/gravity-at w giant [0.0 r-peri 0.0]))
          _ (println "[slingshot-test] raw-Euler one-tick kick at periapsis"
                     "(a·dt):" (* a-peri live-dt) "m/s")
          samples (atom [])]
      (loop [w w, i 0]
        (when (< i 100)
          (let [w' (pin-step w giant {star (fn [_] [[0.0 0.0 0.0] [0.0 0.0 0.0]])
                                      perturber (fn [_] [[pert-distance 0.0 0.0] [0.0 0.0 0.0]])})]
            (swap! samples conj
                   {:dv (sp/len (sp/v- (ecs/get-component w' giant c/velocity)
                                       (ecs/get-component w giant c/velocity)))
                    :r (sp/dist (ecs/get-component w' giant c/position)
                                (ecs/get-component w' star c/position))
                    :e (relative-energy w' star giant)
                    :l (sp/len (sp/cross
                                (sp/v- (ecs/get-component w' giant c/position)
                                       (ecs/get-component w' star c/position))
                                (sp/v- (ecs/get-component w' giant c/velocity)
                                       (ecs/get-component w' star c/velocity))))})
            (recur w' (inc i)))))
      (let [ss @samples
            max-dv (apply max (map :dv ss))
            rs (map :r ss)
            es (map :e ss)
            ls (map :l ss)
            min-l (apply min ls)
            max-l (apply max ls)
            min-e (apply min (map double es))
            max-e (apply max (map double es))]
        (println "[slingshot-test] max per-tick |Δv|:" max-dv
                 "; r range (AU):" [(double (/ (apply min rs) law/au))
                                    (double (/ (apply max rs) law/au))]
                 "; E range:" [min-e max-e] "vs E0 =" (double e0)
                 "; L/L0 range:" [(/ min-l l0) (/ max-l l0)])
        (is (< max-dv 5.0e4)
            (str "periapsis passages resolve: per-tick |Δv| stays the real "
                 "orbital change (≤ 2·v_peri ≈ 3.7e4), never the raw "
                 "a_peri·dt ≈ 5.9e5 kick; observed " max-dv))
        (is (every? neg? (map double es))
            "the giant stays BOUND to the star through 181 periapsis passages")
        (is (> (double (apply min rs)) (* 3.0 law/au))
            (str "periapsis passages are resolved, not plunged: min separation "
                 "> 3 AU (orbit's periapsis is 5 AU); observed "
                 (/ (double (apply min rs)) law/au)))
        (is (< (double (apply max rs)) (* 25.0 law/au))
            (str "never ejected: max separation < 25 AU (apoapsis 20 AU, Stark "
                 "saddle ~33 AU); observed " (/ (double (apply max rs)) law/au)))
        (is (< (/ (- max-e min-e) (Math/abs (double e0))) 1.0)
            (str "relative energy oscillates inside an envelope (tide does "
                 "real but bounded work): span/|E0| = "
                 (/ (- max-e min-e) (Math/abs (double e0)))))
        (is (and (> (/ min-l l0) 0.7) (< (/ max-l l0) 1.3))
            (str "angular momentum bounded within ±30% through every "
                 "periapsis; observed L/L0 ∈ [" (/ min-l l0) ", "
                 (/ max-l l0) "]"))))))

(deftest uniform-field-full-time-coverage
  (testing "The full-time-coverage pin (design §9 acceptance): a :planet in a
            UNIFORM constant acceleration field (handcrafted accel-pressure,
            no gravity sources — gravity-at reads zero) sub-cycles to the
            EXACT closed-form constant-acceleration solution over the full dt:
            after N ticks, v = v0 + a·N·dt and the summed (COM-recentred)
            displacement equals v0·N·dt + ½·a·(N·dt)². The rejected 1/K-rate
            semantics would produce exactly 1/K of this."
    (let [a0 1.0e-3
          v0 [0.0 1.0e4 0.0]
          x0 [orbit-radius 0.0 0.0]
          [w giant] (seeder/spawn-clump
                     (ecs/empty-world)
                     {:position x0 :velocity v0
                      :mass giant-mass :radius 7.0e7
                      :matter-state :planet :temperature 300.0})
          w (-> w
                (ecs/put-component giant c/accel-pressure [a0 0.0 0.0])
                (assoc :sim/G law/G :sim/theta live-theta
                       :sim/dt live-dt :sim/softening live-softening
                       :sim/cutoff live-cutoff
                       :tick 0))
          w (spatial/spatial-index w)
          a-mag (sp/len (sp/v+ [a0 0.0 0.0] (kinematics/gravity-at w giant x0)))
          k-exp (kinematics/substep-count
                 live-dt {:r live-softening
                          :mu (* a-mag live-softening live-softening)
                          :softening live-softening :eid giant
                          :accel a-mag})
          _ (println "[uniform-test] K observed (acceleration criterion,"
                     "no gravity sources):" k-exp)
          n 10
          step (fn [w] (-> w
                           spatial/spatial-index
                           (tick/run-parallel
                            [(orbital/gravity-acceleration law/G live-theta live-softening)
                             {:id     :kinematics
                              :writes #{c/position c/velocity}
                              :run    (fn [frozen] (kinematics/kinematics-ws frozen live-dt))}])
                           (update :tick (fnil inc 0))))
          w-n (nth (iterate step w) n)
          v-n (ecs/get-component w-n giant c/velocity)
          ;; One body: each tick's write-set position is that tick's
          ;; displacement in the COM-recentred frame; the sum telescopes to
          ;; the true displacement (kinematics-ws docstring, §6).
          x-sum (loop [w w, i 0, acc [0.0 0.0 0.0]]
                  (if (>= i n)
                    acc
                    (let [w' (step w)]
                      (recur w' (inc i)
                             (sp/v+ acc (ecs/get-component w' giant c/position))))))
          t-total (* n live-dt)
          v-exp (sp/v+ v0 [(* a0 t-total) 0.0 0.0])
          x-exp (sp/v+ (sp/v* v0 t-total) [(* 0.5 a0 t-total t-total) 0.0 0.0])]
      (is (> k-exp 1)
          (str "the uniform field still engages sub-cycling (K from the "
               "acceleration criterion); observed " k-exp))
      (is (< (sp/len (sp/v- v-n v-exp)) (* 1.0e-9 (sp/len v-exp)))
          (str "velocity after " n " ticks = v0 + a·N·dt EXACTLY (full dt per "
               "tick, never dt/K); got " v-n " vs expected " v-exp))
      (is (< (sp/len (sp/v- x-sum x-exp)) (* 1.0e-9 (sp/len x-exp)))
          (str "summed displacement = v0·N·dt + ½a·(N·dt)² EXACTLY (KDK is "
               "exact for constant acceleration at any K); got " x-sum
               " vs expected " x-exp)))))

(deftest no-parent-compact-body-subcycles
  (testing "A compact body with NO resolvable stellar parent also sub-cycles
            the full dt (K driven by the acceleration criterion √(2ηε/|a|) —
            it needs no orbit). Setup: a Jupiter :planet at 1 AU from a pinned
            10 M_sun :brown-dwarf (NOT in stellar-parent-states). One tick
            must reproduce the sub-cycled KDK trajectory EXACTLY (the same
            algorithm replicated through the public gravity-at), with K = 112
            observed — and the per-tick Δv is the real 253-orbit evolution,
            never the raw |a|·dt ≈ 1.5e8 m/s kick."
    (let [bd-mass (* 10.0 law/solar-mass)
          r (* 1.0 law/au)
          v-circ (law/newtonian-circular-speed bd-mass r)
          [w bd] (seeder/spawn-clump
                  (ecs/empty-world)
                  {:position [0.0 0.0 0.0] :velocity [0.0 0.0 0.0]
                   :mass bd-mass :radius 7.0e8
                   :matter-state :brown-dwarf :temperature 1000.0})
          [w planet] (seeder/spawn-clump
                      w
                      {:position [r 0.0 0.0] :velocity [0.0 v-circ 0.0]
                       :mass giant-mass :radius 7.0e7
                       :matter-state :planet :temperature 300.0})
          w (-> w
                (ecs/put-component bd c/lod-tick-phase never-due)
                (assoc :lod/throttle-ticks? true
                       :sim/G law/G :sim/theta live-theta
                       :sim/dt live-dt :sim/softening live-softening
                       :sim/cutoff live-cutoff
                       :tick 0))
          w (spatial/spatial-index w)
          a0 (kinematics/gravity-at w planet [r 0.0 0.0])
          a-mag (sp/len a0)
          k-exp (kinematics/substep-count
                 live-dt {:r live-softening
                          :mu (* a-mag live-softening live-softening)
                          :softening live-softening :eid planet
                          :accel a-mag})
          _ (println "[no-parent-test] K observed (acceleration criterion,"
                     "no orbit):" k-exp)
          ;; Replicate the exact sub-cycle through the public gravity-at: K
          ;; KDK steps of h = dt/K with gravity re-evaluated per position.
          h (/ live-dt k-exp)
          hh (* 0.5 h)
          accel (fn [xi] (kinematics/gravity-at w planet xi))
          [v-exp _] (loop [i 0
                           v [0.0 v-circ 0.0]
                           x [r 0.0 0.0]
                           a-cur a0]
                      (if (>= i k-exp)
                        [v x]
                        (let [v-half (sp/v+ v (sp/v* a-cur hh))
                              x1 (sp/v+ x (sp/v* v-half h))
                              a1 (accel x1)
                              v1 (sp/v+ v-half (sp/v* a1 hh))]
                          (recur (inc i) v1 x1 a1))))
          w' (pin-step w planet {bd (fn [_] [[0.0 0.0 0.0] [0.0 0.0 0.0]])})
          v1 (ecs/get-component w' planet c/velocity)
          dv (sp/len (sp/v- v1 [0.0 v-circ 0.0]))]
      (is (> k-exp 10)
          (str "the acceleration criterion demands real sub-cycling (K ≈ 112); "
               "observed " k-exp))
      (is (< (sp/len (sp/v- v1 v-exp)) (* 1.0e-9 (sp/len v-exp)))
          (str "the no-parent sub-cycle reproduces the replicated KDK "
               "trajectory over the FULL dt exactly (K = " k-exp
               " sub-steps, gravity re-evaluated per intermediate position)"))
      (is (< dv 1.0e6)
          (str "per-tick |Δv| stays ~200× below the raw |a|·dt = "
               (* a-mag live-dt) " m/s kick even though the acceleration "
               "criterion's K = " k-exp " under-resolves this tight orbit "
               "(h = " (format "%.2f" (/ h 3.154e7)) " yr vs T = 0.32 yr — "
               "the documented conservative no-parent default); observed " dv)))))

(deftest embedded-path-composition-order
  (testing "dv/absorb/foff compose on the embedded sub-cycle EXACTLY as on the
            WH path (design §3.4/§9): the KDK loop integrates the re-evaluated
            gravity plus frozen non-gravity channels, dv.* rides as ONE outer
            kick after it, the COM frame-offset subtracts from position, and
            the absorb blend runs last on the composed state. One direct
            kinematics-ws call on a handcrafted world pins the full order."
    (let [v0 [0.0 1.33e4 0.0]
          x0 [orbit-radius 0.0 0.0]
          a0 [-1.87e-4 0.0 0.0]
          a-parent [7.4e-5 0.0 0.0]
          dv [0.0 3.0e3 0.0]
          foff [1.0e6 0.0 0.0]
          pkt {:mass 1.0e24 :velocity [0.0 0.0 1.0e3]
               :position [orbit-radius 0.0 1.0e6]}
          [w star] (seeder/spawn-clump
                    (ecs/empty-world)
                    {:position [0.0 0.0 0.0] :velocity [0.0 0.0 0.0]
                     :mass law/solar-mass :radius law/solar-radius
                     :matter-state :star :temperature 5800.0})
          [w giant] (seeder/spawn-clump
                     w
                     {:position x0 :velocity v0
                      :mass giant-mass :radius 7.0e7
                      :matter-state :gas-giant :temperature 300.0})
          ;; Handcrafted channel forces fail the gate (verified through the
          ;; public gate below); the sub-cycle itself re-evaluates REAL
          ;; gravity from the tree (star pull) — the expected trajectory is
          ;; computed through the public gravity-at.
          w (-> w
                (ecs/put-component giant c/accel-gravity a0)
                (ecs/put-component star c/accel-gravity a-parent)
                (ecs/put-component giant c/dv-transfer dv)
                (ecs/put-component giant c/absorb-accrete [pkt])
                (assoc :sim/G law/G :sim/theta live-theta
                       :sim/dt live-dt :sim/softening live-softening
                       :sim/cutoff live-cutoff
                       :tick 0))
          w (spatial/spatial-index w)
          ;; spatial-index recomputes :genesis/frame-offset from the COM; the
          ;; composition pin overrides it AFTER indexing (the tree the
          ;; sub-cycle's gravity-at needs is already built).
          w (assoc w :genesis/frame-offset foff)
          gate (kinematics/dominance-gate w giant star true a0)
          a-g0 (kinematics/gravity-at w giant x0)
          mu (* law/G (+ law/solar-mass giant-mass))
          k (kinematics/substep-count
             live-dt {:r orbit-radius :mu mu :softening live-softening
                      :eid giant :accel (sp/len a-g0)})
          h (/ live-dt k)
          hh (* 0.5 h)
          accel (fn [xi] (kinematics/gravity-at w giant xi))
          [v-kdk x-kdk] (loop [i 0, v v0, x x0, a-cur a-g0]
                          (if (>= i k)
                            [v x]
                            (let [v-half (sp/v+ v (sp/v* a-cur hh))
                                  x1 (sp/v+ x (sp/v* v-half h))
                                  a1 (accel x1)
                                  v1 (sp/v+ v-half (sp/v* a1 hh))]
                              (recur (inc i) v1 x1 a1))))
          ;; Expected composition, in order: sub-cycle → dv kick → foff → blend.
          v1 (sp/v+ v-kdk dv)
          x1 (sp/v- x-kdk foff)
          m0 (double giant-mass)
          mp (double (:mass pkt))
          mt (+ m0 mp)
          v-exp (sp/v* (sp/v+ (sp/v* v1 m0) (sp/v* (:velocity pkt) mp)) (/ 1.0 mt))
          x-exp (sp/v* (sp/v+ (sp/v* x1 m0) (sp/v* (:position pkt) mp)) (/ 1.0 mt))
          ws (kinematics/kinematics-ws w live-dt)
          v-got (get-in ws [c/velocity giant])
          x-got (get-in ws [c/position giant])]
      (is (some? gate) "gate computable")
      (is (not (:pass? gate))
          (str "the handcrafted tide must fail the gate (embedded path); "
               "gate: " gate))
      (is (< (sp/len (sp/v- v-got v-exp)) (* 1.0e-9 (sp/len v-exp)))
          (str "velocity = KDK(re-evaluated gravity) + dv, blended with the "
               "packet — dv AFTER the loop, blend LAST; got " v-got
               " vs expected " v-exp))
      (is (< (sp/len (sp/v- x-got x-exp)) (* 1.0e-9 (sp/len x-exp)))
          (str "position = KDK(...) − foff, blended with the packet — foff "
               "before the blend; got " x-got " vs expected " x-exp)))))

(deftest soa-cell-mirrors-embedded-subcycle
  (testing "The SoA kinematics cell takes the identical embedded sub-cycle:
            kinematics-ws-soa on the handcrafted gate-fail world produces the
            SAME giant velocity/position as kinematics-ws (both re-evaluate
            gravity through the shared gravity-at)."
    (let [v0 [0.0 1.33e4 0.0]
          x0 [orbit-radius 0.0 0.0]
          a0 [-1.87e-4 0.0 0.0]
          a-parent [7.4e-5 0.0 0.0]
          [w star] (seeder/spawn-clump
                    (ecs/empty-world)
                    {:position [0.0 0.0 0.0] :velocity [0.0 0.0 0.0]
                     :mass law/solar-mass :radius law/solar-radius
                     :matter-state :star :temperature 5800.0})
          [w giant] (seeder/spawn-clump
                     w
                     {:position x0 :velocity v0
                      :mass giant-mass :radius 7.0e7
                      :matter-state :gas-giant :temperature 300.0})
          w (-> w
                (ecs/put-component giant c/accel-gravity a0)
                (ecs/put-component star c/accel-gravity a-parent)
                (assoc :sim/G law/G :sim/theta live-theta
                       :sim/dt live-dt :sim/softening live-softening
                       :sim/cutoff live-cutoff
                       :tick 0))
          w (spatial/spatial-index w)
          ws-ecs (kinematics/kinematics-ws w live-dt)
          w-soa (pcache-soa/build-physics-soa w)
          ws-soa (kinematics/kinematics-ws-soa w-soa live-dt (:genesis/physics-soa w-soa))
          v-ecs (get-in ws-ecs [c/velocity giant])
          x-ecs (get-in ws-ecs [c/position giant])
          v-soa (get-in ws-soa [c/velocity giant])
          x-soa (get-in ws-soa [c/position giant])]
      (is (some? (:genesis/physics-soa w-soa)) "physics SoA built")
      (is (< (sp/len (sp/v- v-soa v-ecs)) (* 1.0e-9 (sp/len v-ecs)))
          (str "SoA cell velocity identical to the ECS cell's; got " v-soa
               " vs " v-ecs))
      (is (< (sp/len (sp/v- x-soa x-ecs)) (* 1.0e-9 (sp/len x-ecs)))
          (str "SoA cell position identical to the ECS cell's; got " x-soa
               " vs " x-ecs)))))
