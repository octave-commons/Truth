# Multi-Timescale N-Body Integration for a Single-Barrier Jacobi ECS

**Domain:** physics | **Phase:** 0 (planet formation / orbital integration)
**Date:** 2026-07-23 | **Author:** claude session af6e538b (research subagent, promoted by opencode)
**Status:** validated
**Primary sources:** Wisdom & Holman (1991)[^9], Rein & Tamayo (2015)[^9], Springel (2005)[^5], Dehnen & Read (2023)[^7], Rein et al. (2024)[^8], Makino & Aarseth (1992)[^3]

---

## 1. Research Question

Gates of Truth runs one global `dt` — dilated to the bulk-collapse dynamical time (Myr) — through a synchronous **double-buffer Jacobi tick**: every subsystem reads one frozen world snapshot and writes a disjoint set of outputs, folded at one barrier per tick (`domain.ecs.tick/run-parallel`). Planet orbits (period ~ years) are catastrophically under-resolved by a `dt` sized for cloud collapse, so eccentricity random-walks to 1 and no planet ever clears the `e < 0.4` `planet-candidate` gate. Live measurement on the dev sim (tick 121722, 36 planets, 6 living worlds) confirms every bound body sits at `e ∈ [0.9999999, 1.0)`.

The base integrator is symplectic leapfrog / symplectic-Euler. We need to know:

1. How do production collisional N-body codes (NBODY6, Hermite, GADGET) give each body its own timestep while keeping the system coordinated?
2. Why does symplecticity break under per-body or adaptive `dt`, and what actually fixes it?
3. How should the fast (orbital) subsystem be sub-cycled against the slow (gas/collapse) subsystem while preserving the single-barrier Jacobi discipline?
4. How does this fit the ECS invariants (single writer per component, no serial sub-phases, frozen snapshot semantics)?

---

## 2. Literature Survey

### 2.1 Individual and block timesteps (Aarseth, Hermite, NBODY6)

Aarseth (1963) introduced the individual-timestep scheme: each particle keeps its own clock and stepsize, advanced with a 4th-order Adams–Moulton predictor-corrector[^1]. Makino (1991) and Makino & Aarseth (1992) replaced that with the **Hermite scheme** — using both force and its time-derivative (jerk) from a 2-body force polynomial, giving 4th-order accuracy from a single force evaluation with predictor-corrector cleanup[^3]. This is the integrator inside NBODY6 / NBODY6++GPU and PeTar[^4].

**Block timesteps** (McMillan 1986; Makino 1991) quantize every particle's individual `dt` to a power of two of a base step:

$$ \Delta t_i = \Delta t_{\max} / 2^{p_i}. $$

All particles thus fall on a shared time grid: at any tick, the *active* set is exactly those particles whose accumulated time is a multiple of their `Δt_i` ("due" this step)[^2]. This turns per-particle asynchrony into a small number of synchronized "rungs" that can be updated together — a discrete, deterministic scheduling rule rather than continuous per-particle time integration.

- **Pros:** O(N log N)-friendly (only active particles get position/velocity prediction and new tree/force builds); trivially parallelizable per rung; timesteps naturally resolve close encounters without penalizing the whole system.
- **Cons:** Rung membership is a discrete decision made from the current force/state — evaluated carelessly it becomes a *phase-space-dependent* stepping rule, the doorway to symplecticity loss (§3). GADGET-2 (Springel 2005)[^5] uses exactly this scheme for its combined gravity+SPH system: particles may drop to a smaller step at any time, but may only rise to a *larger* step when doing so re-synchronizes with the higher rung — a rule adopted specifically to bound the phase-space-dependence hazard. The GADGET-2 acceleration criterion is

$$ \Delta t_{\rm acc} = \sqrt{\frac{2\,\eta\,\epsilon}{|\mathbf a|}}\qquad(\eta \approx 0.025\text{–}0.08), $$

and for SPH particles the softening `ε` is replaced by `min(h, ε)`; the Courant condition adds `Δt_C = C·2h/v_sig`.

### 2.2 Symplecticity under variable / individual dt — the actual hazard

This is precisely the failure mode the live sim exhibits. Standard leapfrog / KDK / symplectic-Euler is symplectic **only when the timestep is a fixed function of time, not of phase space**[^6]. Two distinct ways it breaks:

- **Symplecticity breaks** if the step-size function depends on the coordinates/velocities being integrated. A leapfrog whose `dt` is recomputed from the current acceleration each step is no longer symplectic; the resulting map has no conserved shadow Hamiltonian, so energy and (for near-circular orbits) eccentricity **do not oscillate boundedly — they drift secularly**. This is the textbook explanation for exactly the observed `e → 1` symptom.
- **Time-reversibility breaks** if the step-size is computed from the *pre-step* state only (asymmetric evaluation): stepping forward then backward does not return to the start, because the step-size itself differs depending on direction of travel. Since symplectic ⟹ reversible, an integrator that isn't reversible under its own stepping rule cannot be symplectic either, regardless of the base scheme's order.

**What actually works** (in decreasing order of rigor vs. engineering cost):

1. **Poincaré-transformed / extended-phase-space adaptive symplectic integrators** — treat time as a dynamical variable with its own conjugate momentum so stepping becomes a fixed-step map in an extended Hamiltonian; genuinely symplectic with adaptive step, but a significant rewrite (Mikkola family)[^6].
2. **Time-symmetric step-size functions, evaluated symmetrically** — pick `dt` from a criterion function evaluated using information from both the current and (predicted) next state, or iterate to a fixed point, so forward/backward stepping is exactly reversible.
3. **"Redo-if-violated" reversible switching** (Dehnen & Read 2023)[^7] — define a time-symmetric acceptance condition for a step/timestep choice; if a step would violate it, discard and retake with a corrected step. ~A few percent of naive steps violate the condition; fixing this eliminates orders-of-magnitude long-term error accumulation. Cheap to bolt onto an existing integrator.
4. **Fixed dt-per-body between resync points, chosen from the frozen snapshot** — the pragmatic block-step compromise: don't let `dt` change mid-integration-interval, and compute the *next* interval's `dt` from state already "in the past" relative to the step being taken (what GADGET-2's asymmetric-refine/symmetric-coarsen rule approximates). Not perfectly symplectic, but removes the mid-step-dependence pathology; the industry-standard tradeoff.
5. **What silently breaks energy without warning:** any scheme that (a) recomputes the acceleration criterion using post-kick velocity/position from *this* step and (b) uses that new `dt` for *this same* step (rather than the next one) — a phase-space-dependent, asymmetrically-evaluated step. The most common accidental way to defeat symplecticity while still calling the integrator "leapfrog."

**Multiple-timestep reversible generalizations of Wisdom–Holman** (Rein et al. 2024)[^8] explicitly generalize block-steps to the WH/Kepler-split setting and state the key constraint: *"adapting a global timestep reversibly and discretely must be done in a block-synchronized manner"* — any coarse/fine transition has to land on a synchronization boundary shared by the coupled bodies, not an arbitrary per-body moment. This maps directly onto the single-barrier tick.

### 2.3 Sub-stepping / operator splitting for the fast subsystem

The relevant family is **mixed-variable symplectic (MVS) / Wisdom–Holman integrators** (Wisdom & Holman 1991)[^9] and their descendants:

- **Hamiltonian splitting:** `H = H_Kep + H_Int`, where `H_Kep` is each planet's unperturbed Keplerian motion about the dominant mass (solved *exactly*, analytically, via a Kepler-equation solver — no discretization error at all for that piece) and `H_Int` is the (small) planet-planet or perturber interaction, integrated numerically with a kick. The standard symmetric composition is

$$ \Phi_{\Delta t} = e^{\Delta t/2\,\mathcal L_{\rm Kep}}\; e^{\Delta t\,\mathcal L_{\rm Int}}\; e^{\Delta t/2\,\mathcal L_{\rm Kep}}, $$

2nd-order accurate and exactly symplectic *for any step size*, because each half is an exact or explicit symplectic map. **This is the single most relevant technique for the planet-orbit bug**: the "fast" Kepler part never needs the tiny substeps a generic leapfrog would, because it isn't discretized at all.

- **REBOUND's WHFast** (Rein & Tamayo 2015)[^9] is the production-grade implementation, with 1st/2nd-order symplectic correctors; **REBOUND's hybrid/TRACE integrators**[^9] switch to a high-order scheme only during close encounters, otherwise running plain WH — directly analogous to "sub-cycle only when needed."
- **Multirate/nested splitting for genuinely different fast/slow physical subsystems:** Lie-Trotter / Strang-Marchuk operator splitting with subcycling — integrate the slow field (gas/collapse) with the big step, and inside that step subcycle the fast subsystem `K` times at `Δt/K`, holding (or linearly interpolating) the slow forcing term constant across the `K` substeps. Force-gradient nested multirate methods reduce the bias introduced by "freezing" the slow force[^10]. **Variational multirate integrators**[^10] derive this from a discrete variational principle on a two-scale (macro/micro) time grid and *inherit* the symplectic/momentum-conserving properties of the underlying variational integrator — the theoretically cleanest version of "hold the slow field fixed while the fast field substeps." **FAST** (arXiv:0908.1460)[^10] addresses the directly-analogous fast-collisional / slow-fluid split for combined N-body+gas systems; full text extraction was not accessible in this session — flagged for a follow-up direct read.

**Choosing K:** derive from the same style of criterion GADGET-2 uses, per-body, evaluated once per tick from the frozen snapshot:

$$ K_i = \left\lceil \frac{\Delta t_{\rm tick}}{\Delta t_{{\rm local},i}} \right\rceil,\qquad \Delta t_{{\rm local},i} = \min\!\left(f_{\rm orb}\, T_{{\rm orb},i},\ \sqrt{\frac{2\eta\,\epsilon}{|\mathbf a_i|}}\right) $$

with `f_orb ≈ 1/20…1/50` (fraction of the local Kepler period — the standard "resolve the orbit with N steps" heuristic) and the acceleration criterion as a safety net for close encounters/high eccentricity. Clamp `K_i` to a sane range (e.g., 1–512) and compute it **once, from the pre-tick frozen state**, never re-evaluated mid-substep-loop.

**Slow → fast coupling consistency:** because the gas/bulk-collapse dynamical time is, by construction, orders of magnitude longer than a planet's orbital period, holding the gravitational field from massive/bulk bodies constant across a body's `K` Kepler-substeps this tick is a well-justified 1st-order Lie-Trotter split — the error introduced is bounded by `O(Δt_tick / τ_slow)`, small precisely because that's the regime `dt` is dilated for.

### 2.4 LOD-coupled stepping

Games and space-sims solve the identical "close = expensive/accurate, far = cheap/coarse" problem primarily through **discrete regime switching keyed on distance/activity**, not continuous adaptive `dt`:

- **Kerbal Space Program's "on-rails" system**[^11] is the cleanest real-world analogue: the active vessel (and anything within ~2.5 km) gets full physics at render tick rate; everything else is propagated analytically via patched-conics (closed-form Kepler orbit, no integration error, updated only when queried) until focus switches or another vessel gets close, at which point it transitions between regimes.
- **Distance-based update-rate scaling** is a documented general-purpose pattern (US Patents 9,687,737 / 8,527,657)[^12]: an object's update rate is scaled down with distance from the "update locus" (camera / player-controlled entity).
- **Engine-level practice** (Unity fixed-timestep, Fiedler's "Fix Your Timestep!")[^13]: physics stays on one deterministic fixed `dt`; LOD is applied as a *coarser sampling of that same clock*, never a continuously varying per-object `dt`, because continuous variation reintroduces exactly the determinism/reversibility problems from §2.2.

**Recommendation:** treat "distance/quiescence" as just another input to the rung-selection function — a body far from the camera/active-focus entity or with negligible orbital drift gets bucketed to a coarse rung (or an analytic Kepler-propagation fallback, à la KSP's on-rails mode) automatically, using the same power-of-two rung machinery, not a separate mechanism.

---

## 3. Governing Equations

### 3.1 Wisdom–Holman split for the compact-body subsystem

For each compact body `i` orbiting dominant mass `M_*` with perturbing acceleration `a_pert` from all other bodies (frozen over the tick):

$$ H = \underbrace{\frac{p^2}{2} - \frac{G M_*}{r}}_{H_{\rm Kep}\ \text{(analytic)}} + \underbrace{V_{\rm pert}(\mathbf r)}_{H_{\rm Int}\ \text{(kick)}}. $$

Per tick of size `Δt`, apply `K_i` symmetric substeps of size `h = Δt / K_i`:

$$ \Phi_h = \underbrace{e^{h/2\,\mathcal L_{\rm Kep}}}_{\text{Kepler propagate } h/2}\; \underbrace{e^{h\,\mathcal L_{\rm Int}}}_{v \mathrel{+}= h\,\mathbf a_{\rm pert}\ (\text{frozen})}\; \underbrace{e^{h/2\,\mathcal L_{\rm Kep}}}_{\text{Kepler propagate } h/2}. $$

Because `H_Kep` is advanced analytically, discretization error in the two-body piece is zero at any `h`; correctness is insensitive to how large `K` must be. The frozen-perturber Lie-Trotter split contributes `O(Δt/τ_slow)` error, small by construction.

### 3.2 Substep-count criterion (from frozen snapshot only)

$$ K_i = \operatorname{clamp}\!\left(\left\lceil \frac{\Delta t_{\rm tick}}{\min\!\left(f_{\rm orb}\, T_{{\rm orb},i},\ \sqrt{2\eta\epsilon/|\mathbf a_i|}\right)} \right\rceil,\ 1,\ 512\right), \qquad T_{{\rm orb},i} = 2\pi\sqrt{\frac{a_i^3}{G M_*}}. $$

**The one rule that matters:** freeze `K_i` at tick-entry from the snapshot; never recompute it mid-loop from state produced within that same tick's substeps. Phase-space-dependent stepping *is* the secular-drift bug being fixed.

### 3.3 Reversibility acceptance check (optional hardening)

Per Dehnen & Read[^7]: define a time-symmetric acceptance condition on the step (e.g., the criterion value computed from pre-step and post-step states must agree within tolerance). If violated, discard and retake the step with a corrected `h`. ~Few-percent retake rate; eliminates orders-of-magnitude long-term error accumulation.

---

## 4. Fit to the Single-Barrier Jacobi ECS

Two structurally different mechanisms are compatible with "one frozen snapshot in, one disjoint write-set out, no serial phases." They solve different halves of the problem.

### 4.1 Sub-stepping *within* one system's `:run` (recommended primary fix)

The orbital/kinematics system, for each due entity, does its own internal `K`-substep Kepler/WH integration entirely inside its `(fn [frozen] write-set)` closure — reading only the frozen snapshot's positions/masses for perturbing bodies, running `K` internal steps locally in pure Clojure, and emitting exactly one final position/velocity write per entity for this tick.

- **Zero changes to ECS wiring.** Invisible to the barrier/fold machinery: from the fold's point of view it's one write, same as always. No new writer, no serial tier, no registry change, parallel-safe.
- **Consistency:** the `K` substeps see a **held-fixed** external field for the whole tick — exactly the same one-tick Jacobi lag the architecture already accepts for inter-system forces, just applied `K` times inside one tick instead of once. No new lag class is introduced.
- **Symplecticity:** use the WH/Kepler split (§3.1) for the dominant-mass part, not raw leapfrog substeps — correctness becomes insensitive to `K`, and the two-body piece stays exactly reversible even under a coarse outer tick.

### 4.2 Tick-phasing (GADGET-style block rungs) — optimization layer only

Bucket entities into power-of-two rungs by `K_i` (or by distance/quiescence) and have the owning system only include a given entity in its write-set on ticks where that entity is "due" — entities not due keep their prior component value (no write = unchanged, the natural persistent-map semantics the fold already has). First-class fit for the Jacobi model since **write-sets are already partial**.

- **Tradeoff vs. 4.1:** tick-phasing only makes bodies *coarser* relative to the base tick (skip ticks) — it cannot make a body *finer* than one tick, since there's no sub-tick barrier. The actual bug (orbits needing *finer* resolution than the tick) is therefore **not solved by tick-phasing alone**. Tick-phasing is the right tool for the *inverse* problem: bodies over-resolved relative to their dynamics wasting compute.
- **Combined scheme:** use 4.1 to guarantee correctness; layer 4.2 on top purely as a performance/LOD optimization — skip re-deriving `K_i` (or fall back to cheap analytic Kepler propagation) for bodies whose rung says they're not due. Rung transitions only at points where the body's own substep loop has just completed a full cycle (block-synchronized, per §2.2[^8]), never mid-substep.

**Force-lag bound:** a fast body's `K` substeps this tick use last-tick's snapshot of the slow/bulk field; a body skipping ticks under 4.2 holds an even staler field. Bound by requiring `N_skip · Δt_tick ≪ τ_dyn,slow` — for the Myr-scale gas process this is an enormous number of 60 Hz ticks, not a binding constraint in practice.

### 4.3 Rejected alternatives

- **Global `dt` cap** sized for the fastest orbit: 5–7 orders of magnitude too small; freezes the bulk cloud. Rejected.
- **Reusing the existing `lod-tick-phase` machinery:** inert in production (`:lod/throttle-ticks?` never set true outside tests) and pointed the wrong way (throttles distant bodies *down*; the need is fast bodies *up*). Rejected.

---

## 5. Promotion Path

### 5.1 ECS components (additions to `domain.ecs.components`)

```clojure
;; Read-only derived orbital state, written by the kinematics system
;; from the frozen snapshot at tick entry. One writer: kinematics-ws.
(c/defcomponent orbital-elements
  "Osculating elements w.r.t. the dominant local mass, recomputed at tick entry."
  {:semi-major-axis :meters
   :eccentricity    :dimensionless
   :period          :seconds
   :dominant-mass   :entity-id})

(c/defcomponent substep-plan
  "Frozen at tick entry; consumed by the same system's :run. Never recomputed mid-tick."
  {:k           [:int {:min 1 :max 512}]
   :h           :seconds           ; Δt_tick / k
   :computed-at :tick-id})
```

### 5.2 System shape

```clojure
(defn kinematics-run
  "Single writer for compact-body position/velocity.
   Reads frozen snapshot; runs K-substep WH integration per body;
   emits one final {:position :velocity} write per entity."
  [frozen]
  ;; 1. For each compact body: compute orbital-elements + substep-plan
  ;;    from frozen state ONLY.
  ;; 2. K times: Kepler-propagate h/2 → kick with frozen a_pert → Kepler-propagate h/2.
  ;; 3. Emit single write per entity. No reads of this tick's outputs.
  )
```

### 5.3 Malli schema stub (`law/orbit.clj`)

```clojure
(def SubstepPlan
  [:map
   [:k [:int {:min 1 :max 512}]]
   [:h pos?]
   [:computed-at :int]])

(def OrbitalElements
  [:map
   [:semi-major-axis pos?]
   [:eccentricity [:double {:min 0.0}]]
   [:period pos?]
   [:dominant-mass :int]])
```

### 5.4 Second arm: fragment placement decoupling

Decouple disk-fragment placement radius from the bulk `dt` so planets form at physical radii (~1–30 AU), inside the 100-AU apoapsis gate, instead of being shoved to ~162 AU by the coarse clock. This is a separate card (`fragment-placement-decouple-dt`) but part of the same epic; without it, even perfectly-integrated orbits fail the gate.

---

## 6. Test Contracts

Drawn directly from the failure modes identified in §2:

1. **Bounded (e, a) drift** — known-stable two-body test case at multiple `K` and outer-tick sizes: `(e, a)` must be flat/bounded, not secularly growing, over 10⁴–10⁶ outer ticks.
2. **Energy drift** — isolated Kepler pair: bounded oscillation, not monotonic growth (canonical symplectic regression test).
3. **Rung-transition stress** — force a body to cross rung boundaries repeatedly; verify no discontinuous jump in position/velocity and no reversibility violation (integrate forward N ticks, reverse velocities, integrate back N, check return-to-start error).
4. **Stale slow field** — verify the error from holding the bulk/gas field constant across `K` substeps stays within budget as `τ_dyn,slow / Δt_tick` shrinks (confirm the Lie-Trotter error bound holds in-regime, not just asymptotically).
5. **Live-scale reproduction** — regression test written against the live-scale `dt` that produced `e → 1` in the dev sim, so the suite actually exercises the bug it guards.

---

## 7. Concrete Recommendations (priority order)

1. **Fix correctness first, independent of performance:** internal `K`-substepping in the orbital/kinematics system (§4.1), `K_i` computed once per tick from the frozen snapshot via the period+acceleration criterion (§3.2). Use WH/Kepler split, not raw leapfrog substeps.
2. **Never recompute `K_i` or the sub-step size mid-loop.** Freeze at tick-entry. The single highest-leverage rule against the eccentricity-drift failure mode.
3. **Add a cheap reversibility check** (§3.3) if any criterion is ever allowed to vary between ticks.
4. **Layer tick-phasing / rung-skipping as a pure optimization, never as the correctness mechanism**; rung transitions only at block-synchronized boundaries.
5. **Unify LOD with the rung system**, not as a separate feature: distance/quiescence feeds the same coarse/fine bucket decision, with a KSP-style analytic-Kepler fallback for fully on-rails bodies.
6. **Write the §6 regression suite first** (TDD), against live-scale `dt`.

---

## Sources

[^1]: Aarseth, S. (1963). Origin of the individual-timestep N-body scheme. See also NBODY6 manual: https://ftp.ast.cam.ac.uk/pub/sverre/nbody6/man6.pdf

[^2]: McMillan, S. (1986); Makino, J. (1991). Block-timestep (power-of-two) scheme. See also *Block Time Step Storage Scheme for Astrophysical N-Body Simulations*, ApJS 219, 31. https://iopscience.iop.org/article/10.1088/0067-0049/219/2/31

[^3]: Makino, J. & Aarseth, S. (1992). Hermite integrator formulation; Nitadori & Makino, *6th and 8th Order Hermite Integrator for N-body Simulations*. arXiv:0708.0738.

[^4]: Nitadori & Aarseth, *Accelerating NBODY6 with GPUs*. arXiv:1205.1222; Wang et al., *PeTar*. arXiv:2006.16560; *nbody6++gpu*, MNRAS 450, 4070. https://academic.oup.com/mnras/article/450/4/4070/990854

[^5]: Springel, V. (2005). *The Cosmological Simulation Code GADGET-2*. MNRAS 364, 1105. https://doi.org/10.1111/j.1365-2966.2005.09655.x ; Springel et al., *GADGET-4*. arXiv:2010.03567.

[^6]: *Symplectic integrators with adaptive time steps*. arXiv:1108.0322; Mikkola family, *A Class of Symplectic Integrators with Adaptive Time Step for Separable Hamiltonian Systems*. https://iopscience.iop.org/article/10.1086/301102 ; *Adaptive Hamiltonian Variational Integrators*. arXiv:1709.01975; *Time stepping N-body simulations*. arXiv:astro-ph/9710043.

[^7]: Dehnen, W. & Read, J. (2023). *Switching Integrators Reversibly in the Astrophysical N-Body Problem*. arXiv:2301.06253.

[^8]: Rein, H. et al. (2024). *Multiple timestep reversible N-body integrators for close encounters in planetary systems*. arXiv:2401.07113.

[^9]: Wisdom, J. & Holman, M. (1991). Mixed-variable symplectic (Kepler/interaction split) integrator; Rein, H. & Tamayo, D. (2015). *WHFast*; REBOUND integrators: https://rebound.hanno-rein.de/integrators/ ; Rein et al., *Hybrid Symplectic Integrators for Planetary Dynamics*. arXiv:1903.04972; *High order symplectic integrators in REBOUND*. arXiv:1907.11335; *Symplectic correctors in adaptive timestepping*. arXiv:1011.3830; *FROST hierarchical forward symplectic*. arXiv:2011.14984.

[^10]: *Variational multirate integrators*. arXiv:2406.12991; *Force-gradient nested multirate methods for Hamiltonian systems*. ScienceDirect S001046551400352X; *FAST: A Fully Asynchronous Split Time-Integrator for Self-Gravitating Fluid*. arXiv:0908.1460 (full text not extractable this session — follow-up read recommended).

[^11]: Kerbal Space Program on-rails / patched-conics physics. https://wiki.kerbalspaceprogram.com/wiki/1.2 ; KSP modding docs, `PatchedConics` class.

[^12]: US Patents 9,687,737 and 8,527,657, *Methods and systems for dynamically adjusting update rates in multi-player network gaming*.

[^13]: Fiedler, G. *Fix Your Timestep!* https://gafferongames.com/post/fix_your_timestep/ ; Unity Manual, *Time and Frame Rate Management*. https://docs.unity3d.com/2022.3/Documentation/Manual/TimeFrameManagement.html

---

## Cross-references

- `docs/designs/multi-timescale-integration.md` — the design doc derived from this research (Wisdom–Holman sub-stepping epic, 6 cards).
- `docs/research/physics/barnes-hut-gravity-optimization.md` — gravity force kernel whose outputs feed the perturber kick.
- `docs/research/physics/ecs-physics-substrate.md` — single-writer / Jacobi-barrier invariants this scheme is designed to fit.
- `docs/research/physics/phase0-tick-loop-optimization.md` — tick budget context.
- Kanban cards: `integrator-kepler-substep`, `fragment-placement-decouple-dt`, `orbit-integration-regression-tests`, `central-star-nearest-attractor`, `stability-softened-elements`, `lod-rung-onrails-optimization` (icebox), `planet-orbit-circularization-blocker`.
