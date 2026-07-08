# Single-ECS-Substrate Architecture for Physics Integration

**Domain:** physics | **Phase:** 0 (cross-phase foundation)  
**Date:** 2026-07-07 | **Author:** truth-research-physics  
**Status:** validated  
**Primary sources:** Truth architecture invariants (`AGENTS.md`); `docs/notes/specs/2026.06.26-ecs-double-buffer-single-writer-spec.md`; `docs/notes/specs/2026.06.29-unified-physical-state-integrator-spec.md`; Monaghan (1992); Springel (2005); Price (2012); Barnes & Hut (1986); Bilas (2002); Martin (2007).

---

## 1. Research Question

How do we integrate gravity, hydrodynamics, MHD-lite, thermal evolution, and gradual mass transfer into *Gates of Truth* without forking the simulation into parallel engines, order-dependent serial pipelines, or special-case bypasses?  The answer is the **single-ECS-substrate** design: there is exactly one world, the ECS world in `domain.ecs.core`; every physical mechanism is a component and a system; every contended physical field is decomposed into an owner plus named influence components; and the architecture is enforced by tests, not by convention.

This notebook documents the reusable patterns, the influence registry, the single-writer invariant, and the forbidden patterns that follow from that invariant.  It is a research notebook *about* the simulation substrate, not about one physics module, because the substrate is the load-bearing structure that every other physics module must fit into.

---

## 2. Literature Survey

### 2.1 Entity–Component–System as a single substrate

The ECS pattern separates entities (identifiers), components (data), and systems (queries + transformations).  Bilas (2002) introduced the data-driven game-object system that became the modern ECS, and Martin (2007) argued that ECS is the natural substrate for MMOs because it makes state composition implicit and data layout explicit.  The crucial insight for *Truth* is not ECS itself but the **single-substrate** constraint: phases are content layers on top of one world, not parallel simulations with their own world types.

### 2.2 Double-buffer / Jacobi updates in physics codes

The standard N-body and SPH split in GADGET-2 (Springel 2005) and Gasoline2 (Wadsley et al. 2017) is to compute forces in parallel and then apply them in a single kick step.  That is exactly the Jacobi snapshot pattern used here: forces are read from the old state, integrated into a new state, and the swap is atomic.  The difference is that *Truth* generalizes the split from forces→velocity to all physical influences: mass flux, heat, torque, and velocity deltas all flow through the same registry.

### 2.3 SPH and N-body influence decomposition

Monaghan (1992) and Price (2012) describe SPH as a Lagrangian particle method where each force term (pressure gradient, artificial viscosity, gravity, Lorentz) is summed into the total acceleration.  Springel (2005) splits these terms into separate code modules in GADGET-2, each writing its own contribution.  The single-ECS-substrate mirrors this decomposition at the component level: every force is a distinct influence component, and the integrator owns the summation.

> **Key finding:** The literature on high-performance astrophysical codes converges on the same discipline *Truth* enforces: one authoritative state, separate additive contributions, a single integration step, and no ad-hoc serial fixes after the integration.

---

## 3. Governing Equations

### 3.1 The double-buffer world update

Let \(W^N\) be the ECS world at tick \(N\).  Each system \(s\) is a pure function from the frozen snapshot to a private write-set:

\[
s(W^N) \;\rightarrow\; \text{ws}_s \subseteq \{c \mapsto \{e \mapsto v\}\}
\]

The next world is the fold of all disjoint write-sets onto the snapshot:

\[
W^{N+1} = \mathcal{F}\left(W^N,\; \{\text{ws}_s\}_{s \in \mathcal{S}}\right)
\]

### 3.2 Single-writer invariant

For every component type \(c\) in the component set \(\mathcal{C}\):

\[
\bigl|\{s \in \mathcal{S} : c \in \text{writes}(s)\}\bigr| = 1
\]

If this holds, the write-sets are disjoint and the fold is conflict-free.  The invariant is checked statically at startup and in `test/architecture_test.clj`.

### 3.3 Influence composition

The integrator owns the contended physical fields.  For an additive field \(f\) (e.g. velocity, mass, angular momentum), the update is:

\[
f^{N+1} = f^N + \Delta t \sum_{k} I_k^N
\]

where each influence component \(I_k\) is registered in `domain.integrator/influence-registry`.  For derived fields (e.g. pressure, spin, b-field from frozen flux), the update is a pure function:

\[
f^{N+1} = \phi\left(\{g^N\}\right)
\]

### 3.4 Lifecycle as visibility, not a contended write

Spawn and despawn are not exceptions to single-writer.  A new entity requested at tick \(N\) appears in \(W^{N+1}\); because nothing at tick \(N\) could read it, no Jacobi inconsistency arises.  A despawn is expressed by a single-owner `consumed.*` marker, and the entity is omitted from \(W^{N+1}\) during world construction.

---

## 4. Implementation Sketch (Clojure Pseudocode)

### 4.1 The four component shapes

Every contended value in the ECS substrate takes one of four shapes:

| Shape | Pattern | Who writes what | Example |
|---|---|---|---|
| **Accumulator** | One integrator owns the absolute value; influencers write separate contribution components. | `:integrator` writes `:position`, `:velocity`; `:gravity` writes `:accel-gravity`. | velocity ← Σ accel·dt |
| **Derived** | One owner computes a pure function of other components. | `:eos` writes `:pressure`; `:field` writes `:b-field`. | P = ρkT/mH |
| **State machine** | One owner reads signals and decides transitions. | `:classifier` writes `:matter-state`. | :nebula → :protostar |
| **Discrete event** | Event handler runs serially at the barrier, recorded in the ledger. | Collision merge handler emits `absorb-merge` + `consumed-merge`. | Star-star merger |

### 4.2 Adding a new force in one registry line

```clojure
;; 1. Define the contribution component in domain.ecs.components
(def accel-radiation :component/accel.radiation)

;; 2. Register the emitter in domain.ecs.registry
{:id     :radiation-pressure
 :ns     'domain.radiation
 :reads  #{c/position c/mass c/luminosity}
 :writes #{c/accel-radiation}}

;; 3. Add one line to domain.integrator/influence-registry
(def influence-registry
  {:velocity {:accumulate [c/accel-gravity c/accel-pressure c/accel-lorentz
                           c/accel-observer c/accel-warp
                           c/accel-radiation]   ; <- new force
              :compose :sum :scale :dt}
   ...})

;; 4. Write the emitter as a pure write-set system
(defn radiation-pressure-system
  "Returns {c/accel-radiation {eid [ax ay az]}}."
  [world]
  (into {}
        (for [eid (emissive-entities world)]
          [eid (radiation-accel world eid)]))
  {c/accel-radiation ...})
```

No edit to the integrator body is required, and no ordering with gravity or hydro needs to be reasoned about.

### 4.3 Cross-entity transfer without a special channel

Gradual mass transfer (BHL sink accretion and Roche-lobe overflow) writes the **same influence component type** on both donor and sink.  The donor gets a negative mass-flux and a recoil velocity-delta; the sink gets a positive mass-flux and the matching momentum gain.  Both are self-owned by the mass-transfer system; the integrator folds them through the generic `:mass` and `:velocity-delta` accumulators.

```clojure
(defn mass-transfer-ws
  "Donor and sink each carry their own copy of the same influence component.
   Single-writer holds because both copies are written by this system."
  [world]
  (reduce (fn [ws {:keys [donor sink dm dv]}]
            (-> ws
                (assoc-in [c/mass-flux-transfer donor] (- dm))
                (assoc-in [c/mass-flux-transfer sink]   dm)
                (assoc-in [c/dv-transfer donor] dv)
                (assoc-in [c/dv-transfer sink] (sp/v- dv))))
          {}
          (transfers world)))
```

The forbidden alternative is to emit one rich event on the donor that the integrator then unpacks and routes to the sink by id.  That would be a side-channel and a single-writer violation.

### 4.4 Lifecycle reaping with per-owner consumed markers

```clojure
;; collision-detection system emits consumed.merge on the absorbed body.
{:id     :collision-detection
 :reads  #{c/position c/radius c/velocity c/mass ...}
 :writes #{c/absorb-merge c/consumed-merge c/spawn-request-shatter}}

;; The integrator blends absorb-merge packets into the survivor's mass/velocity.
;; The reaper removes any entity carrying any consumed.* marker.
(defn reap-consumed [world]
  (let [consumed (apply set/union
                        (for [ct [c/consumed-merge c/consumed-accrete
                                  c/consumed-escape c/consumed-transfer]]
                          (set (keys (get-in world [:components ct])))))]
    (remove-entities world consumed)))
```

Every consumed marker has a single owner; the reaper does not need to know which system decided to remove the entity.

### 4.5 The double-buffer tick

```clojure
(defn tick [world systems]
  (let [wsets (mapv (fn [sys] [(:id sys) ((:run sys) world)]) systems)
        world' (ecs.tick/fold world wsets :on-conflict :throw)]
    (advance-tick world')))
```

`ecs.tick/fold` throws if two systems wrote the same component type.  Because all systems read the same immutable snapshot, the result is deterministic and order-independent.

---

## 5. Toy Model / Numerical Experiment

The architecture itself is tested by `test/architecture_test.clj`, which is the canonical validation experiment for the substrate:

| Check | Test | Result |
|---|---|---|
| No parallel world markers | `single-ecs-substrate` | PASS: no `domain.particles`, `:genesis/mode`, `:genesis/field`, or `:genesis/mesh` in `src/` |
| One renderer | `one-renderer` | PASS: `infra.render/phase0-renderer` does not exist |
| Domain never imports infra | `domain-never-imports-infra` | PASS: no `src/domain` file requires an `infra` namespace |
| Registry well-formed | `system-registry-well-formed` | PASS: every entry has a unique `:id` and component-keyword `:reads`/` `:writes` |
| Single-writer holds | `single-writer-ownership-holds` | PASS: `(reg/write-conflicts reg/systems)` is empty |
| Boot-time guard | `assert-single-writer!` | PASS: `reg/assert-single-writer!` throws on no conflicts |

Full test suite run (2026-06-25, after the ECS convergence): **88 tests, 224 assertions, 0 failures, 0 errors**.

A minimal two-body example:

```clojure
;; Tick 0: Earth and Sun have positions and masses.
;; Gravity system reads the snapshot and writes:
;;   {:component/accel.gravity {sun [0 0 0], earth [a_x a_y a_z]}}
;; Integrator folds:
;;   v' = v + a·dt
;;   x' = x + v'·dt
;; No system writes position or velocity except the integrator.
```

This is the substrate on which the Barnes–Hut gravity kernel, the SPH hydro pass, and the MHD-lite Lorentz force all operate.

---

## 6. Validation

- [x] Single-writer invariant enforced by `domain.ecs.registry` and `test/architecture_test.clj`.
- [x] No parallel world models or second renderers exist in `src/`.
- [x] `domain/` namespaces do not import `infra/` (rendering, I/O, LLM calls stay in `infra`).
- [x] Double-buffer fan-out produces the same result as sequential folding when write-sets are disjoint.
- [x] Influence registry is declarative; adding a new force requires one component + one registry line + one emitter.
- [ ] SoA acceleration cache for the hot gravity/integrator path is still being promoted.
- [ ] Hierarchical / individual timesteps are deferred until N grows above ~2000 or close binaries require them.
- [ ] Runtime write-set conflict detection is exercised in tests but not yet benchmarked for production tick rates.

---

## 7. Promotion Path to Domain Code

The substrate is **already promoted** and is the live path in `src/`.  The relevant files are:

| File | Responsibility |
|---|---|
| `src/domain/ecs/core.clj` | World storage, component lookup, archetype tracking. |
| `src/domain/ecs/components.clj` | Canonical component keywords. |
| `src/domain/ecs/registry.clj` | System registry, single-writer validation, `assert-single-writer!`. |
| `src/domain/ecs/tick.clj` | Double-buffer write-set fold and parallel fan-out. |
| `src/domain/ecs/parallel.clj` | Deterministic `par-mapv` for intra-system parallelism. |
| `src/domain/integrator.clj` | Unified physical-state integrator and influence registry. |
| `src/domain/orbital/system.clj` | Gravity system (Barnes–Hut). |
| `src/domain/hydro.clj` | SPH pressure-gradient system. |
| `src/domain/em.clj` | MHD-lite Lorentz and magnetic-braking system. |
| `src/domain/mass_transfer.clj` | Gradual accretion / RLOF system. |
| `src/domain/stellar.clj` | Structure, EOS, fusion, disk evolution, sink formation. |
| `src/domain/regime.clj` | Regime classifier. |
| `test/architecture_test.clj` | Structural guardrails that fail CI if the substrate is violated. |

### Recipe for adding a new physics system

1. **Spec first:** add the Malli schema in `src/law/` and a failing test in `test/domain/`.
2. **Component:** add a canonical keyword in `src/domain/ecs/components.clj`.
3. **Register:** add a `{:id ... :reads ... :writes ...}` entry in `src/domain/ecs/registry.clj`.
4. **Emit:** write a pure system function in `src/domain/<physics>.clj` that returns a write-set touching only the declared components.
5. **Integrate:** if the system influences a contended physical field, add the influence component to `domain.integrator/influence-registry` rather than writing the field directly.
6. **Wire:** add the system to the tick pipeline in `src/domain/phase0.clj`.
7. **Validate:** run `clj -M:test` and confirm `reg/write-conflicts` is still empty.

---

## 8. Cross-References to Other Physics Research

- **Gravity:** `barnes-hut-gravity-optimization.md` — the `:gravity` system is the Barnes–Hut force emitter; its output is the `accel-gravity` influence consumed by the integrator.
- **Hydro:** `sph-neighbor-kernel-optimization.md` — the `:hydro` system computes the SPH pressure-gradient and writes `accel-pressure`; the same spatial index and kernel gradients can be reused by the MHD-lite curl.
- **MHD-lite:** `mhd-em-lorentz-optimization.md` — the `:em-lorentz` system writes `accel-lorentz` and `torque-em`; the `:field` system owns `b-field` and `frozen-flux` as a derived flux-freezing owner.
- **Mass transfer:** `rate-limited-accretion-mass-transfer.md` — the `:mass-transfer` system writes `mass-flux-transfer` and `dv-transfer` on both donor and sink, folded by the integrator.
- **Disks / planet formation:** `protoplanetary-disks-planet-formation.md` — the `:disk-evolution` system writes `mass-flux-disk` and `torque-disk` influences; it reads `absorb-accrete` from `:sink-formation` with a one-tick Jacobi delay.
- **Tick budget:** `phase0-tick-loop-optimization.md` — the 60 Hz budget and SoA cache recommendation apply to the integrator and the gravity/hydro fan-out systems; the cache must be owned by the integrator and rebuilt from the ECS every tick.
- **Chemistry:** `nebular-chemistry-metal-enrichment.md` — composition changes flow through the integrator as `comp-burn` and `comp-depletion` influences; the integrator owns `composition` and `comp-condensed`.
- **Stellar hierarchy:** `stellar-nebula-mass-hierarchy.md` — the mass ladder informs the `:classifier` state machine that owns `matter-state` and `accretion-radius`.
- **Winds / plasma:** `stellar-wind-plasma-state.md` — wind ablation writes `wind-heating` (temperature delta + ionization rate + mass loss), which the integrator folds into `temperature`, `ionization-fraction`, and `mass`.

---

## 9. Open Questions

- **MHD-lite threshold gate.** Where does the β / Alfvén-Mach threshold live so that the decision to compute the full curl stays single-writer?  Options: a separate `:em-regime` system that writes a gating component, or the `:em-lorentz` system itself reading the `:regime` tag from the previous snapshot.
- **Regime ordering.** The coupled-physics spec originally required `:regime` to run before `:hydro`, but the parallel pipeline places it at the end, making regime tags one tick stale.  Is the stale tag acceptable (Jacobi lag) or does regime need to be reordered before the systems that consume it?
- **Toomre-Q and convective tags.** How do we add `:toomre-q`, `:cooling-beta`, and `:convective` regime tags without giving multiple systems co-ownership of `:regime`?
- **Jacobi delay in sink→disk coupling.** `:sink-formation` emits `absorb-accrete`; `:disk-evolution` reads it next tick.  Is this one-tick delay acceptable for disk growth, or does it require a disk-mass-flux influence emitted directly by sink formation?
- **SoA cache ownership.** The integrator should own the transient SoA cache for the hot path, but the gravity system also wants cache-friendly arrays.  Should the cache be built once per tick and shared read-only with gravity, or should gravity build its own arrays from the ECS?
- **Wind reservoir cleanup.** `wind-rate-scale` defaults to 1.5 (cinematic) and wind reservoirs are not cleared on star→brown-dwarf demotion.  These are content drifts, not substrate violations, but they should be fixed before the FSM handoff to Phase 1.

---

## 10. References

1. **Truth architecture invariants.** `AGENTS.md`, §"Single Simulation Substrate (ONE PATH)" and §"No Special Cases — Everything Rides the Uniform Path".
2. **ECS double-buffer spec.** `docs/notes/specs/2026.06.26-ecs-double-buffer-single-writer-spec.md`.
3. **Unified physical-state integrator spec.** `docs/notes/specs/2026.06.29-unified-physical-state-integrator-spec.md`.
4. **ECS physics substrate notes.** `docs/notes/research/ecs-physics-substrate/README.md` and the `claude-physics-merge-*.md` chunks.
5. **Bilas, S. (2002).** *A Data-Driven Game Object System.* GDC. http://gamedevs.org/uploads/data-driven-game-object-system.pdf
6. **Martin, A. (2007).** *Entity Systems are the Future of MMOG Development.* http://t-machine.org/index.php/2007/09/03/entity-systems-are-the-future-of-mmog-development-part-1/
7. **Monaghan, J. J. (1992).** Smoothed particle hydrodynamics. *ARA&A*, 30, 543–574. https://doi.org/10.1146/annurev.aa.30.090192.002551
8. **Price, D. J. (2012).** Smoothed particle hydrodynamics and magnetohydrodynamics. *J. Comput. Phys.*, 231, 759–794. https://doi.org/10.1016/j.jcp.2010.12.011
9. **Springel, V. (2005).** The cosmological simulation code GADGET-2. *MNRAS*, 364, 1105–1134. https://arxiv.org/abs/astro-ph/0505010
10. **Barnes, J., & Hut, P. (1986).** A hierarchical O(N log N) force-calculation algorithm. *Nature*, 324, 446–449. https://doi.org/10.1038/324446a0
11. **Wadsley, J. W., Keller, B. W., & Quinn, T. R. (2017).** Gasoline2: A Modern SPH Code. *MNRAS*, 471, 2357–2368. https://arxiv.org/abs/1707.03824
