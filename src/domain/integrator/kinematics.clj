(ns domain.integrator.kinematics
  "Kinematics write-sets for the unified integrator.

   Compact bodies (planets and other resolved non-stellar bodies) NEVER take
   the raw one-evaluation symplectic-Euler step at the dilated late-sim `dt`
   (~80 yr/tick): that step decoheres every planetary orbit to e → 1
   (kanban/tasks/planet-orbit-circularization-blocker.md), and when the local
   field is not parent-dominated it injects a·dt ~ 10⁹ m/s of Δv in a single
   tick — an instant ejection
   (docs/research/physics/cluster-dispersal-integration-heating.md §3.3).
   Instead (docs/designs/multi-timescale-integration.md §3, §9): with a
   resolvable, dominant parent star they take a Wisdom–Holman K-sub-step on
   the PARENT-RELATIVE state (analytic Kepler drift plus the frozen tidal
   perturbation kick); when the dominance gate fails or no parent is
   resolvable they SUB-CYCLE — K KDK-leapfrog steps covering the FULL dt,
   with gravity re-evaluated at each intermediate position from the frozen
   world's spatial tree (`gravity-at`). All other bodies keep the
   byte-identical symplectic-Euler path."
  (:require
   [clojure.math :as math]
   [domain.ecs.core :as ecs]
   [domain.ecs.components :as c]
   [domain.ecs.parallel :as par]
   [domain.gravity.barnes-hut.force :as bh]
   [domain.physics.cache.soa :as pcache-soa]
   [domain.profile :as profile]
   [domain.integrator.base :as base]
   [domain.orbital.kepler :as kep]
   [law.stellar :as law]
   [shape.spatial :as sp]))

(defn- com-blend
  "Mass-weighted centroid of survivor `[v0 x0]` with mass `m0` and absorbed
   packets `pkts`. Returns `[v-blend x-blend total-mass]`."
  [v0 x0 m0 pkts]
  (let [v0m (sp/v* v0 m0)
        x0m (sp/v* x0 m0)
        {:keys [vn xn total-m]}
        (reduce (fn [acc p]
                  (let [m (double (:mass p 0.0))
                        v (or (:velocity p) base/zero3)
                        x (or (:position p) base/zero3)]
                    (-> acc
                        (update :vn sp/v+ (sp/v* v m))
                        (update :xn sp/v+ (sp/v* x m))
                        (update :total-m + m))))
                {:vn v0m :xn x0m :total-m m0}
                pkts)]
    (if (pos? total-m)
      (let [inv (/ 1.0 total-m)]
        [(sp/v* vn inv) (sp/v* xn inv) total-m])
      [v0 x0 m0])))

(defn- compute-forces
  "Σ acceleration influences for every entity in `eids`."
  [world eids]
  (into {} (par/par-mapv
            (fn [eid] [eid (base/sum-vec-influences world eid base/accel-sources)])
            eids)))

;; ---------------------------------------------------------------------------
;; Wisdom–Holman sub-stepping for compact bodies (design §3)
;; ---------------------------------------------------------------------------

(def ^:const substep-orbit-fraction
  "Fraction of the local orbital period one sub-step may span (design §3.3,
   f_orb ≈ 1/20–1/50). The Kepler drift is exact at any step, so this only
   paces the perturbation-kick cadence."
  0.05)

(def ^:const substep-accuracy-eta
  "Accuracy parameter η for the close-encounter acceleration criterion
   √(2ηε/|a|) (design §3.3)."
  0.03)

(def ^:const substep-max-k
  "Sub-step ceiling (design §3.3): covers the 1-AU placement floor at
   dt = 80 yr (K = 1600 there). A binding clamp is logged, never silent."
  4096)

(def ^:const substep-dominance-factor
  "WH-split validity gate (design §3.1: the perturbation must be \"small
   relative to the parent pull\"; §4 names the close-encounter breakdown):
   a body only takes the Kepler-split path when the Newtonian parent pull
   μ/r² exceeds this factor × the frozen tidal perturbation magnitude.
   A body whose local field is NOT parent-dominated — a condensation core
   inside a collapsing gas clump, a planet mid-scattering — has no meaningful
   Kepler arc to drift along; it SUB-CYCLES (design §9: K KDK-leapfrog steps
   covering the full dt, gravity re-evaluated at each intermediate
   position), never a raw Euler step at the global dt."
  100.0)

(def ^:const stellar-parent-states
  "Matter states that can parent a sub-stepped body's Kepler drift."
  #{:star :protostar})

(def ^:const substep-matter-states
  "Matter states that take the WH sub-step path (design §3.2: \"gate on
   body-kind ∈ star/planet\"; stars themselves never sub-step, §3.0 step 2):
   FORMED planets — the blocker's population is disc-fragment :gas-giants and
   accretion-seeded :planets — plus end-state :stellar-remnants. Formation-era
   intermediates (:condensed-core, :planetesimal, collapse-born :brown-dwarf)
   are embedded in the collapsing clump/disc where no single attractor is
   guaranteed to dominate and the frozen-tidal assumption (dt ≪ τ_slow) does
   not hold; they stay on the global integrator until promotion."
  #{:planet :gas-giant :stellar-remnant})

(defn substep-candidate?
  "True when `eid` is a formed, substellar body eligible for the WH sub-step
   path (still requires a resolvable parent — see `nearest-stellar-parent` —
   and a parent-dominated local field — see `substep-dominance-factor`).
   Bodies without a `c/matter-state` (test fixtures, the spark) are never
   candidates."
  [world eid]
  (boolean
   (when-let [ms (ecs/get-component world eid c/matter-state)]
     (contains? substep-matter-states ms))))

(defn stellar-parents
  "All entities that can serve as a sub-step parent: matter-state ∈
   {:star :protostar} with position, velocity, and mass. One cheap scan per
   tick, shared by every candidate — never a per-body O(N) neighbour query
   (performance is a correctness property, AGENTS.md)."
  [world]
  (filterv #(contains? stellar-parent-states
                       (ecs/get-component world % c/matter-state))
           (ecs/entities-with world c/matter-state c/position c/velocity c/mass)))

(defn nearest-stellar-parent
  "The nearest of `stars` (from `stellar-parents`) to `eid` by snapshot
   distance, or nil when none exists. Pure; reads the frozen world. This is
   deliberately NEAREST, not most-massive — the most-massive lookup in
   `domain.stellar.classifier/central-star` is the worked counter-example
   (design §6)."
  [world eid stars]
  (when-let [x (ecs/get-component world eid c/position)]
    (when-let [cands (seq (remove #(= % eid) stars))]
      (apply min-key #(sp/dist x (ecs/get-component world % c/position)) cands))))

(defn substep-count
  "K for one compact body this tick — the number of sub-steps of the global
   dt, FROZEN at tick entry from the snapshot (design §3.3):

     K        = clamp(ceil(dt / dt_local), 1, 4096)
     dt_local = min(f_orb · T_orb(r), √(2ηε/max(a_N, |a|)))

   with `orbit` = {:r :mu :softening :eid}: T_orb the Kepler period at the
   current separation :r, |a_N| = μ/r² the Newtonian pull of the orbit's
   primary, ε the world softening. The optional :accel key carries the body's
   TOTAL acceleration magnitude (the embedded block-step path, design §9): a
   tide-dominated body's real dynamics are faster than the orbit primary's
   pull alone suggests, and the acceleration criterion must see them. The WH
   path omits :accel and behaves exactly as before. NEVER recomputed from
   within-tick state: phase-space-dependent step sizing is exactly the
   secular-drift bug being fixed (Dehnen & Read 2023). A binding clamp is
   logged (eid, demanded K, clamped K) — silent truncation reads as \"handled
   everything\" when it wasn't."
  [dt {:keys [r mu softening eid accel]}]
  (let [dt (double dt)
        r (double r)
        mu (double mu)
        t-orb (kep/kepler-period r mu)
        a-eff (max (/ mu (* r r)) (double (or accel 0.0)))
        dt-local (min (* substep-orbit-fraction t-orb)
                      (math/sqrt (/ (* 2.0 substep-accuracy-eta
                                       (double softening))
                                    a-eff)))
        demanded (long (math/ceil (/ dt dt-local)))
        k (-> demanded (max 1) (min substep-max-k))]
    (when (< substep-max-k demanded)
      (println "[kinematics-ws] K clamp binds: eid" eid
               "demanded" demanded "clamped" substep-max-k))
    k))

(defn- softened-pull
  "The Plummer-softened gravitational acceleration the sim's Barnes–Hut force
   law applies to a body, from a source of mass `m-src` at displacement
   `to-src` (= x_src − x_body), with softening `eps` and dead-zone `cutoff`
   (pairs closer than the cutoff contribute zero — mirrors
   domain.gravity.barnes-hut/force)."
  [m-src to-src eps cutoff]
  (let [r (sp/len to-src)
        eps (double eps)
        cutoff (double cutoff)]
    (if (and (pos? r) (>= r cutoff))
      (let [d2 (+ (* r r) (* eps eps))
            s (/ (* law/G (double m-src)) (math/pow d2 1.5))]
        (sp/v* to-src s))
      base/zero3)))

(defn- euler-advance
  "The ordinary symplectic-Euler advance (no frame-offset, no absorb blend):
   [v' x'] with v' = v + a·dt + Σdv, x' = x + v'·dt. Shared by the fallback
   cell and by the parent advance inside the WH composition (design §3.0
   step 2)."
  [v x a dv dt]
  (let [v1 (sp/v+ (sp/v+ v (sp/v* a dt)) dv)]
    [v1 (sp/v+ x (sp/v* v1 dt))]))

(defn dominance-gate
  "The Wisdom–Holman dominance gate for one compact body (design §3.1, §9):
   the parent-relative snapshot state and the frozen TIDAL perturbation —
   (a_body − a_soft(parent→body)) − (a_parent − a_soft(body→parent)) — with
   the parent's own (pair-softened, x̂-consistent) pull removed from both
   sides because the WH drift supplies the exact relative term. Kick
   consistency (kanban/tasks/compact-pair-softening.md): the subtraction uses
   the same per-pair ε as the Barnes–Hut force channel, evaluated at the same
   drift-predicted positions x̂ the force channel reads when the SoA carries
   them (subtracting a snapshot-position pull from an x̂-evaluated force
   leaves a phantom tide that fails the gate and ejects the body).

   Returns {:pass? :r :r0 :u0 :mu :a-tidal :a-parent :a-parent-pull :x-p :v-p
   :softening} — everything `compact-advance` needs to compose the WH path —
   or nil when the relative geometry is degenerate (coincident with the
   parent, or a massless pair: no resolvable Kepler arc). Public so tests and
   probes can assert WHICH arm of the universal sub-stepper a body takes."
  [world eid parent-eid parent-due? a-body]
  (let [x-b (ecs/get-component world eid c/position)
        v-b (ecs/get-component world eid c/velocity)
        m-b (double (or (ecs/get-component world eid c/mass) 0.0))
        x-p (ecs/get-component world parent-eid c/position)
        v-p (ecs/get-component world parent-eid c/velocity)
        m-p (double (or (ecs/get-component world parent-eid c/mass) 0.0))
        mu (* law/G (+ m-p m-b))
        r0 (sp/v- x-b x-p)
        u0 (sp/v- v-b v-p)
        r-n (sp/len r0)]
    (when (and (pos? r-n) (pos? mu))
      (let [world-soft (double (or (:sim/softening world) 1.0e14))
            eps (law/pair-softening
                 (law/body-softening (ecs/get-component world eid c/matter-state)
                                     (ecs/get-component world eid c/radius)
                                     world-soft)
                 (law/body-softening (ecs/get-component world parent-eid c/matter-state)
                                     (ecs/get-component world parent-eid c/radius)
                                     world-soft))
            cut (* law/softening-cutoff-fraction eps)
            pos-fn (pcache-soa/predicted-position-fn world)
            xh-b (pos-fn eid)
            xh-p (pos-fn parent-eid)
            a-parent (if parent-due?
                       (base/sum-vec-influences world parent-eid base/accel-sources)
                       base/zero3)
            a-tidal (-> a-body
                        (sp/v- (softened-pull m-p (sp/v- xh-p xh-b) eps cut))
                        (sp/v- a-parent)
                        (sp/v+ (softened-pull m-b (sp/v- xh-b xh-p) eps cut)))]
        {:pass? (> (/ mu (* r-n r-n))
                   (* substep-dominance-factor (sp/len a-tidal)))
         :r r-n :r0 r0 :u0 u0 :mu mu
         :a-tidal a-tidal :a-parent a-parent
         :a-parent-pull (/ mu (* r-n r-n))
         :x-p x-p :v-p v-p :softening world-soft}))))

(defn- compact-advance
  "Wisdom–Holman K-sub-step advance of one compact body, in parent-relative
   (Jacobian) coordinates (design §3.0–3.4):

     r = x_body − x_parent,  u = v_body − v_parent  (frozen snapshot)
     K times, h = dt/K:  drift_Kep(h/2) → kick_pert(h) → drift_Kep(h/2)
     x_body' = x_parent' + r',  v_body' = v_parent' + u'

   The drift is the exact Newtonian two-body term (μ = G·(M_parent + m_body),
   domain.orbital.kepler/propagate); the kick is the frozen tidal perturbation
   from `dominance-gate`. `parent-due?` mirrors the tick's LOD scheduling: a
   parent not advanced this tick contributes its unchanged state.

   Returns nil when the gate fails (no parent-dominated Kepler arc — the
   caller takes the embedded block-step, design §9), else [v-composed
   x-composed] BEFORE the dv.* outer kick, the frame-offset subtraction, and
   any absorb blend (those compose in the caller, §3.4)."
  [world dt eid parent-eid parent-due? a-body]
  (when-let [{:keys [pass? r r0 u0 mu a-tidal a-parent x-p v-p softening]}
             (dominance-gate world eid parent-eid parent-due? a-body)]
    (when pass?
      (let [[v-p' x-p'] (if parent-due?
                          (euler-advance v-p x-p a-parent
                                         (base/sum-vec-influences
                                          world parent-eid base/dv-sources)
                                         dt)
                          [v-p x-p])
            k (substep-count dt {:r r :mu mu :softening softening :eid eid})
            h (/ (double dt) k)
            hh (/ h 2.0)
            [r' u'] (loop [i 0, r r0, u u0]
                      (if (>= i k)
                        [r u]
                        (let [d1 (kep/propagate mu r u hh)
                              u1 (sp/v+ (:velocity d1) (sp/v* a-tidal h))
                              d2 (kep/propagate mu (:position d1) u1 hh)]
                          (recur (inc i) (:position d2) (:velocity d2)))))]
        [(sp/v+ v-p' u') (sp/v+ x-p' r')]))))

;; ---------------------------------------------------------------------------
;; Universal compact sub-stepping — the embedded sub-cycle (design §9)
;; ---------------------------------------------------------------------------

(def ^:private non-gravity-accel-sources
  "Acceleration influence channels OTHER than gravity. Gravity is the
   position-dependent, re-computable channel the embedded sub-cycle
   re-evaluates at each intermediate position; these stay frozen at their
   snapshot values across the K sub-steps (gravity dominates the encounter
   dynamics that flings)."
  (into [] (remove #{c/accel-gravity} base/accel-sources)))

(defn gravity-at
  "Re-evaluate Barnes–Hut gravity on one body at an arbitrary intermediate
   position `x`, from the frozen world's spatial tree — the same map-tree
   kernel and species pair-ε rule the production force channel uses
   (domain.gravity.barnes-hut.force/acceleration; the tree's nodes carry
   :max-eps for the pair rule). Sources sit at their SNAPSHOT positions: the
   rest of the world is held frozen while the sub-cycled body moves (design
   §9 — the GADGET block-step analogue in a Jacobi world: fast/short-range
   dynamics sub-cycled, slow world frozen).

   Throws ex-info when the frozen world carries no :genesis/spatial-tree —
   production builds one before the integrator every tick
   (domain.genesis.tick/tick-world), so an absent tree is a pipeline bug,
   never something to paper over with a stale force."
  [world eid x]
  (when-not (:genesis/spatial-tree world)
    (throw (ex-info "gravity-at: frozen world carries no :genesis/spatial-tree"
                    {:eid eid})))
  (let [world-soft (double (or (:sim/softening world) 1.0e14))]
    (bh/acceleration
     {:G         (double (or (:sim/G world) law/G))
      :theta     (double (or (:sim/theta world) 0.5))
      :softening world-soft
      :tree      (:genesis/spatial-tree world)
      :body      {:id       eid
                  :position x
                  :eps      (law/body-softening
                             (ecs/get-component world eid c/matter-state)
                             (ecs/get-component world eid c/radius)
                             world-soft)}})))

(defn- embedded-orbit
  "The `substep-count` orbit map for the embedded sub-cycle path (design §9).
   With a resolvable parent: the parent's relative state {:r :mu} — the
   period criterion paces h to the local orbit the body is embedded in. With
   no resolvable parent (or a degenerate one): the documented conservative
   default {:r ε, :mu |a|·ε²}, for which the period term reduces to
   0.05·2π·√(ε/|a|) ≈ 1.28× the acceleration term √(2ηε/|a|) — so the
   acceleration criterion (which needs no orbit) always drives K, exactly the
   §9 rule. `a` here is the body's RE-EVALUATED total acceleration at the
   snapshot position (gravity from `gravity-at` plus the frozen non-gravity
   channels). Per-source attribution of |a| is deliberately NOT attempted:
   finding the strongest local source would cost an O(N) scan per body per
   tick (performance is a correctness property, AGENTS.md)."
  [world eid parent-eid a]
  (let [world-soft (double (or (:sim/softening world) 1.0e14))
        a-mag (sp/len a)
        fallback {:r world-soft :mu (* a-mag world-soft world-soft)
                  :softening world-soft :eid eid :accel a-mag}]
    (if parent-eid
      (let [x-b (ecs/get-component world eid c/position)
            x-p (ecs/get-component world parent-eid c/position)
            m-b (double (or (ecs/get-component world eid c/mass) 0.0))
            m-p (double (or (ecs/get-component world parent-eid c/mass) 0.0))
            r-n (sp/dist x-b x-p)
            mu (* law/G (+ m-p m-b))]
        (if (and (pos? r-n) (pos? mu))
          {:r r-n :mu mu :softening world-soft :eid eid :accel a-mag}
          fallback))
      fallback)))

(defn- embedded-substep
  "The embedded sub-cycle (design §9): K KDK-leapfrog steps covering the FULL
   global dt, with acceleration RE-EVALUATED at each intermediate position —
   gravity fresh from the frozen world's spatial tree (`gravity-at`), every
   other accel channel frozen at its snapshot value:

     a(x)     = a_other(frozen) + a_gravity(x)
     K times, h = dt/K:  kick(a(xᵢ), h/2) → drift(h) → kick(a(xᵢ₊₁), h/2)

   K is frozen at tick entry by `substep-count` (same criteria as the WH
   path). This is the GADGET block-step analogue in a Jacobi world: the
   fast/short-range dynamics sub-cycled, the slow world frozen. KDK is
   symplectic in the frozen-others potential — the energy error per tick is
   BOUNDED (no secular drift) — and the body covers the full dt every world
   tick, so close encounters actually resolve instead of depositing a·dt as
   one impulse, and smooth large-scale couplings integrate at full rate (no
   phase-lag). For a uniform field the loop reduces to the exact
   constant-acceleration solution at t = dt; in a curved field it converges
   to the true trajectory at O(h²)."
  [world dt eid v x a-other parent-eid]
  (let [a0 (sp/v+ a-other (gravity-at world eid x))
        k (substep-count dt (embedded-orbit world eid parent-eid a0))
        h (/ (double dt) k)
        hh (* 0.5 h)
        accel (fn [xi] (sp/v+ a-other (gravity-at world eid xi)))]
    (loop [i 0, v v, x x, a-cur a0]
      (if (>= i k)
        [v x]
        (let [v-half (sp/v+ v (sp/v* a-cur hh))
              x1 (sp/v+ x (sp/v* v-half h))
              a1 (accel x1)
              v1 (sp/v+ v-half (sp/v* a1 hh))]
          (recur (inc i) v1 x1 a1))))))

(defn- kinematics-cell
  "Compute [eid velocity' position'] for one entity, blending in absorbed packets."
  [world dt foff absorbs parent-map due-set eid forces]
  (let [a    (get forces eid base/zero3)
        dv   (base/sum-vec-influences world eid base/dv-sources)
        m0   (double (or (ecs/get-component world eid c/mass) 0.0))
        v    (ecs/get-component world eid c/velocity)
        x    (ecs/get-component world eid c/position)
        [v1 x1] (if (substep-candidate? world eid)
                  ;; A compact body NEVER takes a single raw Euler step at the
                  ;; global dt (design §9): gate passes → WH-Kepler; gate fails
                  ;; or no resolvable parent → the embedded sub-cycle (K KDK
                  ;; steps covering the full dt, gravity re-evaluated at each
                  ;; intermediate position, other channels frozen). dv.* is one
                  ;; outer kick after either, foff subtracts from position, the
                  ;; absorb blend runs last — the WH composition (§3.4).
                  (let [parent-eid (get parent-map eid)
                        [vc xc] (or (when parent-eid
                                      (compact-advance world dt eid parent-eid
                                                       (contains? due-set parent-eid) a))
                                    (embedded-substep world dt eid v x
                                                      (base/sum-vec-influences
                                                       world eid non-gravity-accel-sources)
                                                      parent-eid))]
                    [(sp/v+ vc dv) (sp/v- xc foff)])
                  (let [[v1 x1-nofoff] (euler-advance v x a dv dt)]
                    [v1 (sp/v- x1-nofoff foff)]))]
    (if-let [pkts (get absorbs eid)]
      (let [[v-blend x-blend _] (com-blend v1 x1 m0 pkts)]
        [eid v-blend x-blend])
      [eid v1 x1])))

(defn- substep-parents
  "{body-eid parent-eid} for every due entity that takes the WH sub-step path
   this tick: sub-step candidates (resolved, non-stellar) with a resolvable
   nearest parent star. Candidates without a parent — and candidates whose
   dominance gate fails — take the embedded sub-cycle instead (design §9):
   no compact body takes a raw Euler step at the global dt."
  [world eids]
  (let [stars (stellar-parents world)]
    (into {}
          (keep (fn [eid]
                  (when (substep-candidate? world eid)
                    (when-let [p (nearest-stellar-parent world eid stars)]
                      [eid p]))))
          eids)))

(defn- build-kinematics-ws
  "Fold per-entity kinematics cells into the position/velocity write-set."
  [eids world dt foff absorbs forces]
  (let [parent-map (substep-parents world eids)
        due-set (set eids)]
    (reduce (fn [ws [eid v x]]
              (-> ws
                  (assoc-in [c/velocity eid] v)
                  (assoc-in [c/position eid] x)))
            {}
            (par/par-mapv #(kinematics-cell world dt foff absorbs parent-map due-set % forces)
                          eids))))

(defn kinematics-ws
  "Position + velocity. Gas, stars, and unresolved bodies: v' = v + (Σ accel.*)·dt
   + Σ dv.*; x' = x + v'·dt (symplectic Euler), then the one-tick-stale COM
   frame-offset is subtracted from position (a pure Galilean shift, §6).
   Compact bodies (:planet/:gas-giant/:stellar-remnant) NEVER take that raw
   step at the global dt (design §9 — the fling machine): with a resolvable,
   dominant parent star they take the Wisdom–Holman K-sub-step on the
   parent-relative state (analytic Kepler drift + frozen tidal kick, §3),
   composing x' = x_parent' + r' − foff; when the gate fails or no parent is
   resolvable they SUB-CYCLE — K KDK-leapfrog steps covering the full dt with
   gravity re-evaluated at each intermediate position from the frozen
   spatial tree (`gravity-at`), other accel channels frozen. Absorb-accrete/merge packets are blended
   for COM preservation — the absorbed mass's momentum shifts the survivor.
   Gradual mass-transfer recoil rides the c/dv-transfer velocity-delta
   channel, applied as one outer kick after either sub-step path (§3.4).

   When `:lod/throttle-ticks?` is true, only entities whose `c/lod-tick-phase`
   schedule are due this tick are advanced."
  [world dt]
  (let [foff (or (:genesis/frame-offset world) base/zero3)
        eids (base/due-entities
              world
              (ecs/entities-with world c/position c/velocity
                                 c/mass c/radius c/body-kind))
        absorbs (merge (get-in world [:components c/absorb-accrete] {})
                       (get-in world [:components c/absorb-merge] {}))
        profiling? (:genesis/profile-subsystems? world)
        force-fn #(compute-forces world eids)
        leapfrog-fn #(build-kinematics-ws eids world dt foff absorbs %)
        [forces dt-force] (if profiling?
                            (profile/timing force-fn)
                            [(force-fn) nil])
        [ws dt-leap] (if profiling?
                       (profile/timing #(leapfrog-fn forces))
                       [(leapfrog-fn forces) nil])]
    (if profiling?
      (assoc ws :genesis/_profile
             (merge-with + (or (:genesis/_profile ws) {})
                         {:integrator/force-accum (double dt-force)
                          :integrator/leapfrog (double dt-leap)}))
      ws)))

(defn- sum-vec-soa
  "Sum a vector influence from component cells for the SoA path."
  [cells eid]
  (reduce (fn [[ax ay az] cell]
            (if-let [v (get cell eid)]
              [(+ ax (double (nth v 0)))
               (+ ay (double (nth v 1)))
               (+ az (double (nth v 2)))]
              [ax ay az]))
          [0.0 0.0 0.0]
          cells))

(defn- soa-kinematics-cell
  "Compute [eid velocity' position'] for one SoA index, blending absorbed packets."
  [world soa dt foff absorbs parent-map due-eids idx forces dv-cells]
  (let [eid (nth (:eids soa) idx)
        a (get forces eid [0.0 0.0 0.0])
        dv (sum-vec-soa dv-cells eid)
        vx0 (aget ^doubles (:vx soa) idx)
        vy0 (aget ^doubles (:vy soa) idx)
        vz0 (aget ^doubles (:vz soa) idx)
        px0 (aget ^doubles (:px soa) idx)
        py0 (aget ^doubles (:py soa) idx)
        pz0 (aget ^doubles (:pz soa) idx)
        m0 (aget ^doubles (:mass soa) idx)
        [v1 x1] (if (substep-candidate? world eid)
                  ;; Compact body: same universal sub-stepping as the ECS path
                  ;; — the SoA cache mirrors the frozen world, so the shared
                  ;; helpers read identical state via ecs/get-component. Gate
                  ;; passes → WH-Kepler; gate fails or no parent → embedded
                  ;; sub-cycle; never a raw Euler step at the global dt (§9).
                  (let [parent-eid (get parent-map eid)
                        [vc xc] (or (when parent-eid
                                      (compact-advance world dt eid parent-eid
                                                       (contains? due-eids parent-eid) a))
                                    (embedded-substep world dt eid
                                                      [vx0 vy0 vz0] [px0 py0 pz0]
                                                      (base/sum-vec-influences
                                                       world eid non-gravity-accel-sources)
                                                      parent-eid))]
                    [(sp/v+ vc dv) (sp/v- xc foff)])
                  (let [[v1 x1-nofoff] (euler-advance [vx0 vy0 vz0] [px0 py0 pz0]
                                                      a dv dt)]
                    [v1 (sp/v- x1-nofoff foff)]))
        [vx1 vy1 vz1] v1
        [px1 py1 pz1] x1]
    (if-let [pkts (get absorbs eid)]
      (let [[v-blend x-blend _] (com-blend [vx1 vy1 vz1] [px1 py1 pz1] m0 pkts)]
        [eid v-blend x-blend])
      [eid [vx1 vy1 vz1] [px1 py1 pz1]])))

(defn- build-soa-kinematics-ws
  "Fold per-index SoA kinematics cells into the position/velocity write-set.
   Cells run over ALL SoA indices (the historical SoA behavior for non-due
   entities under LOD throttling is preserved); `due-eids` only marks which
   parents advance their own state this tick."
  [world soa dt foff absorbs due-idxs forces dv-cells]
  (let [all-eids (:eids soa)
        parent-map (substep-parents world all-eids)
        due-eids (set (map #(nth all-eids %) due-idxs))]
    (reduce (fn [ws [eid v x]]
              (-> ws
                  (assoc-in [c/velocity eid] v)
                  (assoc-in [c/position eid] x)))
            {}
            (par/par-mapv
             #(soa-kinematics-cell world soa dt foff absorbs parent-map due-eids
                                   % forces dv-cells)
             (range (:n soa))))))

(defn kinematics-ws-soa
  "SoA-aware position + velocity updater. Reads positions/velocities/masses from
   the `:genesis/physics-soa` primitive arrays, sums acceleration contributions
   directly from their component cell maps, and produces the standard write-set
   for position and velocity. Falls back to the ECS path when the cache is
   absent. Compact bodies take the same universal sub-stepping as
   `kinematics-ws` via the shared helpers — WH-Kepler when the gate passes,
   the embedded sub-cycle (gravity re-evaluated per intermediate position)
   when it fails or no parent is resolvable (design §9), never a raw Euler
   step at the global dt — the SoA cache mirrors the frozen world, so parent
   lookups read identical state.

   When `:lod/throttle-ticks?` is true, only due entities (by `c/lod-tick-phase`)
   are advanced."
  [world dt soa]
  (let [foff (or (:genesis/frame-offset world) base/zero3)
        {:keys [eids n]} soa
        tick (long (or (:tick world) 0))
        due-idxs (if (:lod/throttle-ticks? world)
                   (filterv #(base/due-entity? world tick (nth eids %)) (range n))
                   (range n))
        absorbs (merge (get-in world [:components c/absorb-accrete] {})
                       (get-in world [:components c/absorb-merge] {}))
        dt (double dt)
        accel-cells (mapv #(get-in world [:components %]) base/accel-sources)
        dv-cells (mapv #(get-in world [:components %]) base/dv-sources)
        profiling? (:genesis/profile-subsystems? world)
        force-fn #(into {} (par/par-mapv
                            (fn [idx] [(nth eids idx) (sum-vec-soa accel-cells (nth eids idx))])
                            due-idxs))
        leapfrog-fn #(build-soa-kinematics-ws world soa dt foff absorbs due-idxs % dv-cells)
        [forces dt-force] (if profiling?
                            (profile/timing force-fn)
                            [(force-fn) nil])
        [ws dt-leap] (if profiling?
                       (profile/timing #(leapfrog-fn forces))
                       [(leapfrog-fn forces) nil])]
    (if profiling?
      (assoc ws :genesis/_profile
             (merge-with + (or (:genesis/_profile ws) {})
                         {:integrator/force-accum (double dt-force)
                          :integrator/leapfrog (double dt-leap)}))
      ws)))
