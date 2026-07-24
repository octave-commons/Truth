(ns domain.orbital.multi-timescale-regression-test
  "Multi-timescale epic, card 3: orbit-integration regression suite.

   Design: docs/designs/multi-timescale-integration.md §7; research test
   contracts: docs/research/physics/multi-timescale-integration-jacobi-ecs.md §6.
   Cards: kanban/tasks/orbit-integration-regression-tests.md (this suite),
   kanban/tasks/integrator-kepler-substep.md (the fix it gates),
   kanban/tasks/planet-orbit-circularization-blocker.md (the live bug).

   THE BUG BEING REPRODUCED: at live late-sim pacing (dt ≈ 2.5e9 s ≈ 80 yr/tick,
   Plummer softening ≈ 5e14 m ≈ 3342 AU — pacing.clj ceiling), an isolated
   star+planet pair at 5 AU cannot hold its orbit under the raw one-evaluation
   symplectic-Euler integrator: the measured Newtonian eccentricity runs to 1
   (36/36 live planets, blocker card). The fix (Wisdom–Holman Kepler
   sub-stepping inside kinematics-ws) advances the parent-relative state with an
   analytic Newtonian two-body drift, so (e, a) stay bounded at any dt.

   Windowed-equivalence per .agents/skills/physics-dt-unit-mismatch/: the suite
   runs at LIVE-SCALE dt/softening — the bug does not reproduce at cold-start
   pacing.

   Initial velocity is the NEWTONIAN circular speed √(μ/r), not the softened
   one: the sub-stepper's drift is the exact Newtonian two-body term (design
   §3.0–3.1), so the orbit it sustains is the Newtonian one. (The sim's own
   softened force at 5 AU is ~zero — 5 AU sits deep inside the 0.1·softening
   gravitational dead-zone — which is precisely why the raw integrator loses
   the orbit.)

   Scale note: the card targets 10^4–10^6 ticks. CI wall-clock sanity caps the
   bounded/energy runs at 2000 ticks here (~180 orbits of sub-stepped arc);
   the failure mode pre-fix declares within a handful of ticks, and 2000 ticks
   post-fix is ~2x the 10^3 floor with K~142 sub-steps each. Reversibility
   runs 100 ticks each way."
   (:require
    [clojure.math :as math]
    [clojure.test :refer [deftest is testing]]
    [domain.ecs.components :as c]
    [domain.ecs.core :as ecs]
    [domain.ecs.tick :as tick]
    [domain.integrator.kinematics :as kinematics]
    [domain.orbital.kepler :as kep]
    [domain.orbital.stability :as stability]
    [domain.orbital.system :as orbital]
    [domain.physics.cache.soa :as pcache-soa]
    [domain.spatial.index :as spatial]
    [domain.stellar.seeder :as seeder]
    [law.stellar :as law]
    [shape.spatial :as sp]))

;; --- Live-scale pacing constants (mirror the dev world, blocker card) -------

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
  "Planet's initial orbital radius (m): 5 AU — the placement arm's floor band."
  (* 5.0 law/au))

(def ^:private planet-mass
  "Test planet mass (kg): one Jupiter — a real compact body, dynamically
   negligible against the star."
  law/jupiter-mass)

;; --- World + harness ---------------------------------------------------------

(defn- two-body-world
  "An isolated 1 M_sun star + one Jupiter-mass planet at 5 AU on a
   Newtonian-circular orbit about their mutual barycenter — barycenter at the
   origin AND zero total momentum, so the integrator's COM frame-offset is
   identically zero and the reversibility test measures the integrator's
   phase-space symmetry exactly (the one-tick-stale COM recentering is a
   documented Galilean shift; a nonzero barycenter drift would show up as a
   shared world-frame translation under velocity reversal, not orbit error).
   Returns [world star-eid planet-eid] with live-scale :sim pacing scalars."
  []
  (let [mu (* law/G (+ law/solar-mass planet-mass))
        v-rel (math/sqrt (/ mu orbit-radius))
        m-frac (/ planet-mass (+ law/solar-mass planet-mass))
        x-star (* (- m-frac) orbit-radius)
        x-planet (* (- 1.0 m-frac) orbit-radius)
        v-star (* (- m-frac) v-rel)
        v-planet (* (- 1.0 m-frac) v-rel)
        [w star] (seeder/spawn-clump
                  (ecs/empty-world)
                  {:position [x-star 0.0 0.0] :velocity [0.0 v-star 0.0]
                   :mass law/solar-mass :radius law/solar-radius
                   :matter-state :star :temperature 5800.0})
        [w planet] (seeder/spawn-clump
                    w
                    {:position [x-planet 0.0 0.0]
                     :velocity [0.0 v-planet 0.0]
                     :mass planet-mass :radius 7.0e7
                     :matter-state :planet :temperature 300.0})]
    [(assoc w
            :sim/G law/G :sim/theta live-theta
            :sim/dt live-dt :sim/softening live-softening
            :sim/cutoff live-cutoff
            :tick 0)
     star planet]))

(defn- step-world
  "One REAL tick: rebuild the spatial index AND the physics SoA, then
   run-parallel the real Barnes–Hut gravity channel and the real kinematics-ws
   integrator — the minimal production pipeline that owns position/velocity.
   Building the SoA routes gravity through the PRODUCTION SoA kernel
   (`acceleration-for-soa`), so this suite exercises the per-pair-ε force
   channel the live sim runs (kanban/tasks/compact-pair-softening.md): the
   star–planet pair now feels ≈ Newtonian gravity in the force channel, and
   the WH tidal kick subtracts exactly that same pair-softened parent term —
   the whole suite staying green IS the kick-consistency regression."
  [w]
  (-> w
      spatial/spatial-index
      pcache-soa/build-physics-soa
      (tick/run-parallel
       [(orbital/gravity-acceleration law/G live-theta live-softening)
        {:id     :kinematics
         :writes #{c/position c/velocity}
         :run    (fn [frozen] (kinematics/kinematics-ws frozen live-dt))}])
      (update :tick (fnil inc 0))))

(defn- run-ticks
  "Advance `w` by `n` real ticks, calling `(sample-fn w step)` after each."
  [w n sample-fn]
  (loop [w w, i 0]
    (if (>= i n)
      w
      (let [w' (step-world w)]
        (sample-fn w' (inc i))
        (recur w' (inc i))))))

(defn- relative-elements
  "Newtonian two-body elements of the planet RELATIVE to the star (the gate's
   own math, stability/two-body-elements) — nil when unbound."
  [w star planet]
  (stability/two-body-elements
   (sp/v- (ecs/get-component w planet c/position)
          (ecs/get-component w star c/position))
   (sp/v- (ecs/get-component w planet c/velocity)
          (ecs/get-component w star c/velocity))
   (* law/G (+ law/solar-mass planet-mass))))

(defn- specific-energy
  "Specific orbital energy (J/kg) of the relative two-body state."
  [w star planet]
  (let [r (sp/dist (ecs/get-component w planet c/position)
                   (ecs/get-component w star c/position))
        u (sp/len (sp/v- (ecs/get-component w planet c/velocity)
                         (ecs/get-component w star c/velocity)))]
    (- (/ (* u u) 2.0)
       (/ (* law/G (+ law/solar-mass planet-mass)) r))))

;; --- Card 3 suite ------------------------------------------------------------

(deftest eccentricity-and-semimajor-axis-stay-bounded
  (testing "(e,a) bounded at live-scale dt/softening — the blocker reproduction.
            Pre-fix the planet's measured Newtonian e runs to 1 (unbound) within
            a few 80-yr ticks; post-fix the WH sub-step holds a < 0.4 with a
            stable. Target 10^4–10^6 ticks; CI runs 2000 (~10^3 floor, card)."
    (let [[w star planet] (two-body-world)
          samples (atom [(relative-elements w star planet)])]
      (run-ticks w 2000 (fn [w' i]
                          (when (zero? (mod i 10))
                            (swap! samples conj (relative-elements w' star planet)))))
      (let [els @samples
            bound (filter some? els)
            es (map :eccentricity bound)
            as (map :semi-major-axis bound)
            a0 (double orbit-radius)]
        (is (some? (last els))
            "planet remains on a bound relative orbit (never ejected)")
        (is (every? #(< (double %) 0.4) es)
            (str "eccentricity stays < 0.4 for 2000 ticks, never drifting to 1; "
                 "max e seen: " (when (seq es) (apply max es))))
        (when (seq as)
          (is (< (/ (abs (- (double (last as)) a0)) a0) 0.05)
              (str "|Δa/a| < 5% over the run; final a (AU): "
                   (/ (double (last as)) law/au))))))))

(deftest specific-energy-bounded-no-secular-growth
  (testing "Specific orbital energy oscillates within a bound — no monotonic
            secular growth (canonical symplectic regression). Same 2000-tick
            window as the (e,a) run."
    (let [[w star planet] (two-body-world)
          energies (atom [(specific-energy w star planet)])]
      (run-ticks w 2000 (fn [w' i]
                          (when (zero? (mod i 10))
                            (swap! energies conj (specific-energy w' star planet)))))
      (let [es @energies
            e0 (double (first es))
            bound (/ (abs e0) 100.0)
            e-min (apply min es)
            e-max (apply max es)
            e-end (double (last es))]
        (is (< (- e-max e-min) bound)
            (str "energy oscillation (max-min) stays under |E0|/100; seen: "
                 (- e-max e-min) " vs bound " bound))
        (is (< (abs (- e-end e0)) bound)
            (str "no secular drift between first and last sample; |ΔE| = "
                 (abs (- e-end e0)) " vs bound " bound))))))

(deftest reversibility-stability-under-reversal
  (testing "Integrate N ticks, negate all velocities, integrate N back.
            With the compact-pair force channel LIVE (per-pair ε), the star's
            pull on the planet flows through the production SoA kernel — and
            strict phase-retrace at 1e-4 is UNATTAINABLE at live dt, by
            substrate construction:

            * The channel is one-tick Jacobi-stale (the integrator applies
              last tick's emitted force). At dt = 80 yr the planet completes
              ~7.2 orbits per tick, so the stale parent term is rotated ~72°
              from any pull computable from the current world — uncancellable
              at snapshot geometry (the subtraction would leave ~μ/r² of
              phantom tide, failing the dominance gate → Euler path →
              ejection; verified on BOTH the map-tree and SoA paths).
            * The consistent cancellation is therefore x̂-consistent: the
              drift-predicted evaluation (px-pred) places the pair ~217 AU
              apart, laundering the uncancellable term to ~5e-4 of the parent
              pull, and kinematics/softened-pull reads the SAME x̂ via
              predicted-position-fn. The gate passes and the orbit holds.
            * That residue is velocity-dependent (x̂ = x + v·dt launches the
              phantom along the tangent), so exact time-symmetry — which the
              pre-pair-ε suite enjoyed only because the tide was IDENTICALLY
              zero inside the dead-zone — no longer holds. What the WH split
              still guarantees is shape/energy STABILITY through the reversal:
              no ejection, (e,a) bounded, no secular blowup.

            Forward 100 ticks, reverse 100 ticks: the planet remains bound,
            e stays < 0.4 for the whole cycle, and a stays within 5% of the
            initial 5 AU."
    (let [[w star planet] (two-body-world)
          n 100
          es (atom [])
          as (atom [])
          sample (fn [w' _]
                   (when-let [el (relative-elements w' star planet)]
                     (swap! es conj (double (:eccentricity el)))
                     (swap! as conj (double (:semi-major-axis el)))))
          w-fwd (run-ticks w n sample)
          w-rev (-> w-fwd
                    (ecs/put-component planet c/velocity
                                       (sp/v* (ecs/get-component w-fwd planet c/velocity) -1.0))
                    (ecs/put-component star c/velocity
                                       (sp/v* (ecs/get-component w-fwd star c/velocity) -1.0)))
          w-back (run-ticks w-rev n sample)
          el-end (relative-elements w-back star planet)
          a0 (double orbit-radius)]
      (is (some? el-end)
          "planet remains on a bound relative orbit after the full forward+
           reversed cycle (never ejected)")
      (is (every? #(< % 0.4) @es)
          (str "e stays < 0.4 through the entire forward+reversed cycle; max e "
               (when (seq @es) (apply max @es))))
      (is (< (/ (abs (- (double (:semi-major-axis el-end)) a0)) a0) 0.05)
          (str "|Δa/a| < 5% after the full cycle; final a (AU): "
               (/ (double (:semi-major-axis el-end)) law/au))))))

(deftest compact-pair-force-channel-live
  (testing "kanban/tasks/compact-pair-softening.md: the star's pull on the
            planet at 5 AU is NO LONGER ZEROED by the 334-AU dead-zone — the
            written accel-gravity (production SoA kernel, evaluated at the
            drift-predicted positions x̂ per Jacobi force alignment) equals the
            Plummer value with pair ε = star radius at the predicted
            separation, and the WH tidal kick subtracts exactly that term, so
            the bounded/energy/reversibility invariants hold with the channel
            live."
    (let [[w star planet] (two-body-world)
          w' (step-world w)
          a  (ecs/get-component w' planet c/accel-gravity)
          ;; The SoA force channel evaluates at x̂ = x + v·dt (no prior accel
          ;; at tick 0), so the expected magnitude is the Newtonian pull at
          ;; the PREDICTED separation — nonzero, where the dead-zone gave 0.
          xh-p (sp/v+ (ecs/get-component w planet c/position)
                      (sp/v* (ecs/get-component w planet c/velocity) live-dt))
          xh-s (sp/v+ (ecs/get-component w star c/position)
                      (sp/v* (ecs/get-component w star c/velocity) live-dt))
          r-hat (sp/dist xh-p xh-s)
          eps-pair (double law/solar-radius)
          d2 (+ (* r-hat r-hat) (* eps-pair eps-pair))
          a-expected (/ (* law/G law/solar-mass r-hat) (* d2 (math/sqrt d2)))]
      (is (some? a) "accel-gravity written for the planet")
      (is (pos? (sp/len a)) "the compact-pair force is no longer zero")
      (is (< (abs (- (sp/len a) a-expected)) (* 0.01 a-expected))
          (str "within 1% of the pair-softened pull at x̂; |a| = " (some-> a sp/len)
               " vs expected " a-expected " (r̂ = " (/ r-hat law/au) " AU)"))
      (is (neg? (double (nth a 1)))
          "the pull points from the drifted planet position back toward the star"))))

(deftest propagate-matches-analytic-ellipse-and-escapers
  (testing "kepler/propagate reproduces the closed-form elliptical state from
            orbital-state (same elements, one full period ⇒ return to start)
            and conserves energy/angular momentum on hyperbolic passes."
    (let [mu (* law/G law/solar-mass)
          a orbit-radius
          e 0.3
          r0 [(* a (- 1.0 e)) 0.0 0.0]
          v0 [0.0 (math/sqrt (* mu (/ (+ 1.0 e) (* a (- 1.0 e))))) 0.0]
          period (kep/kepler-period a mu)
          one-orbit (kep/propagate mu r0 v0 period)
          half (kep/propagate mu r0 v0 (/ period 2.0))]
      (is (< (/ (sp/dist (:position one-orbit) r0) a) 1.0e-9)
          "elliptical: one period returns to the start state")
      (is (< (/ (sp/len (sp/v- (:velocity one-orbit) v0)) (sp/len v0)) 1.0e-9)
          "elliptical: velocity returns too")
      (is (< (abs (- (sp/dist (:position half) [0.0 0.0 0.0]) (* a (+ 1.0 e))))
             (* 1.0e-9 a))
          "elliptical: half a period lands at apoapsis")
      (let [r-hyp [(* 0.5 a) 0.0 0.0]
            v-hyp [0.0 (* 1.5 (math/sqrt (/ mu (* 0.5 a)))) 0.0]
            t 1.0e7
            fwd (kep/propagate mu r-hyp v-hyp t)
            e0 (- (/ (sp/len2 v-hyp) 2.0) (/ mu (sp/len r-hyp)))
            e1 (- (/ (sp/len2 (:velocity fwd)) 2.0) (/ mu (sp/len (:position fwd))))
            h0 (sp/len (sp/cross r-hyp v-hyp))
            h1 (sp/len (sp/cross (:position fwd) (:velocity fwd)))]
        (is (< (abs (/ (- e1 e0) e0)) 1.0e-9) "hyperbolic: energy conserved")
        (is (< (abs (/ (- h1 h0) h0)) 1.0e-9) "hyperbolic: angular momentum conserved")))))

(deftest substep-count-resolution
  (testing "K frozen at tick entry resolves the 5 AU orbit at live dt per
            design §3.3: f_orb·T_orb ≈ 0.56 yr ⇒ K ≈ 142, below the 4096 clamp."
    (let [mu (* law/G (+ law/solar-mass planet-mass))
          k (kinematics/substep-count live-dt {:r orbit-radius :mu mu
                                               :softening live-softening :eid 0})]
      (println "Observed K at 5 AU, dt=80 yr, live softening:" k)
      (is (<= 100 k 200)
          (str "K ≈ 142 (design §3.3: 20–50 sub-steps per 11.2-yr orbit at "
               "80 yr/tick); observed " k))
      (is (< k kinematics/substep-max-k)
          "the clamp does not bind at the 5 AU placement floor"))))

(deftest spawn-velocity-pairs-with-substepper
  (testing "The spawn seam (design §3.5 pairing rule, the 2026-07-23 live
            regression): a :gas-giant spawned per the GI-branch rule —
            NEWTONIAN-circular, tangential, star frame — holds e < 0.4 through
            the real pipeline at live ε, while the OLD softened-circular-speed
            spawn plunges to e ≈ 1 within ticks (the sub-stepper's exact
            Newtonian drift reads the near-stationary softened-spawn state as
            a radial infall). The drift law and the spawn law must match."
    (let [spawn-world (fn [v-speed]
                        (let [[w star] (seeder/spawn-clump
                                        (ecs/empty-world)
                                        {:position [0.0 0.0 0.0] :velocity [0.0 0.0 0.0]
                                         :mass law/solar-mass :radius law/solar-radius
                                         :matter-state :star :temperature 5800.0})
                              [w planet] (seeder/spawn-clump
                                          w
                                          {:position [orbit-radius 0.0 0.0]
                                           :velocity [0.0 v-speed 0.0]
                                           :mass planet-mass :radius 7.0e7
                                           :matter-state :gas-giant :temperature 300.0})]
                          [(assoc w
                                  :sim/G law/G :sim/theta live-theta
                                  :sim/dt live-dt :sim/softening live-softening
                                  :sim/cutoff live-cutoff
                                  :tick 0)
                           star planet]))
          v-newtonian (law/newtonian-circular-speed law/solar-mass orbit-radius)
          v-softened (law/softened-circular-speed law/solar-mass orbit-radius
                                                  live-softening)]
      (is (< (abs (- v-newtonian (math/sqrt (/ (* law/G law/solar-mass)
                                               orbit-radius))))
             1.0e-6)
          "law/newtonian-circular-speed IS √(GM/r)")
      (is (< (/ v-softened v-newtonian) 1.0e-3)
          (str "at live ε the softened spawn speed is ~zero — the mismatch the "
               "pairing rule exists for; ratio " (/ v-softened v-newtonian)))
      (let [[w star planet] (spawn-world v-newtonian)
            samples (atom [(relative-elements w star planet)])]
        (run-ticks w 500 (fn [w' i]
                           (when (zero? (mod i 10))
                             (swap! samples conj (relative-elements w' star planet)))))
        (let [es (keep :eccentricity @samples)]
          (is (every? #(< (double %) 0.4) es)
              (str "Newtonian-spawned :gas-giant holds e < 0.4 for 500 ticks; "
                   "max e " (when (seq es) (apply max es))))))
      (let [[w star planet] (spawn-world v-softened)
            w' (run-ticks w 10 (fn [_ _]))
            el (relative-elements w' star planet)]
        (is (or (nil? el) (> (double (:eccentricity el)) 0.9))
            (str "softened-speed spawn under the sub-stepper plunges (e → 1 or "
                 "unbound) — the hazard, pinned; e after 10 ticks: "
                 (some-> el :eccentricity)))))))
