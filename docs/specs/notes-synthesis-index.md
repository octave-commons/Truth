# Notes-to-Specs Synthesis

**Status:** inventory complete  
**Scope:** docs/notes/ split exports, archived originals, and existing designs/specs  
**Purpose:** Group the agent-conversation exports by topic, reconcile what was decided vs. what was built, and point each group to a living spec.

---

## Logical groups

### Group A — Foundation architecture (2026.06.23.20.01.16)

These are the earliest notes. They establish the four-quadrant ontology, the ECS substrate, the event ledger, and the shape/law/domain/infra split.

| Chunk | Topic | Decision | Built? | Gap / follow-up |
|---|---|---|---|---|
| 001 | Best-fit stack | Clojure + LWJGL + custom ECS + Nuklear tool UI | Partial | Stack is Clojure + LWJGL + custom ECS; no Nuklear layer exists |
| 002 | μ0 shapes/claims/contracts | Ontology of shape, claim, law, contract | Partial | `shape.core`, `law.contract` exist but ontology is not active in ECS store |
| 003 | shape.core | Component/event IDs, registry | Yes | `shape.core`, `law.registry` |
| 004 | law.ledger | Immutable event ledger + Merkle root | Yes | `law.ledger`, `domain.ecs.ledger` |
| 005–007 | μ4 spatial primitives + Barnes–Hut | 3D vectors, AABB, octree, gravity | Yes | `shape.spatial`, `domain.gravity.barnes-hut` |
| 008 | ECS contract | Single world map; components owned by entities | Yes | `domain.ecs.core` |
| 009 | Event model | Discrete meaningful events only | Yes | `domain.ecs.event` |
| 010 | Rewindable protocol | Symmetric reversible tick + snapshots | Yes | `domain.ecs.rewindable`, `domain.ecs.timeline` |
| 011 | law/ecs_dsl.clj | DSL for components/systems/events | Yes | `law.ecs_dsl`, `domain.ecs.dsl` |
| 012–013 | μ-specs first | Schema/tests before implementation | Partial | Malli validators exist; runtime validation in `put-component` is not enforced |

**Reconciled contradictions:**
- Early chunks describe per-system ring-buffer history; later chunks and code converge on a single world map.
- Early chunks model `:component-updated` as events; later chunks restrict events to discrete multi-entity interactions.

**Derived spec:** None needed — foundation is mostly built. The remaining gaps (runtime component validation, active ontology wiring) are polish, not blockers.

---

### Group B — Core vision / player ontology (2026.06.25.16.41.16)

These notes articulate the fiction: physics-first universe, quantum observer, Gates of Truth, time-as-observability, character-creation-as-world-creation.

| Topic | Decision | Built? | Gap |
|---|---|---|---|
| Physics-first universe | Simulation is the artifact | Yes | Principle adopted |
| Quantum observer | Distant regions statistical; focus collapses detail | Partial | Focus is a rendering/HUD feature; does not change simulation resolution |
| Gates of Truth | Interdimensional travel via related wave functions | No | No gate system exists |
| Time as observability | Clock rate slows with complexity | Yes | `domain.phase0/pacing-for` |
| Character = world | Player narrows from cosmic spark to embodied person | No | No embodied-person phase exists |

**Derived specs:**
- `phase0-player-focus-and-dual-representation.md` — make focus actually promote/demote simulation resolution.
- Future: gate-network spec (outside Phase 0 scope).

---

### Group C — Physics research (2026.06.25.22.11.59)

Research notes on volumetric gas rendering and real stellar mechanics.

| Topic | Decision | Built? | Gap |
|---|---|---|---|
| Volumetric gas | Ray-marched heterogeneous participating medium | Partial | Additive fog puffs approximate the look, not the model |
| MHD equations | Ideal MHD + cooling + regime classifier | Partial | EM-lite, hydro, regime exist; true induction, Toomre Q, interior, atmosphere do not |
| Protostar pipeline | Deuterium thermostat → H ignition → main sequence | Partial | Fusion ignition exists; protostar spectral classes/birthline do not |

**Derived specs:**
- `phase0-protoplanetary-disk-implementation.md` already covers most of this. Needs status update.
- `phase0-chemistry-differentiation.md` — composition-driven planet classes.

---

### Group D — Phase 0 convergence (phase-0.md + phase-0-design-exploration.md)

These are the working sessions where the parallel `Phase0World` engine was merged onto the ECS substrate.

| Topic | Decision | Built? | Gap |
|---|---|---|---|
| Single ECS substrate | Delete `Phase0World` defrecord | Yes | Enforced by `architecture_test.clj` |
| Player spark | Singleton `:component/observer` | Yes | `domain.player` |
| Renderer unification | One `infra.render` | Partial | `render-to-file` and dev window use Phase 0; standalone `run-window` still uses orbital-only path |
| Dev server | `infra.dev.server` runs Phase 0 | Yes | Wired |
| Particle-mesh detour | FFT-Poisson core attempted and abandoned | N/A | `domain.particles` forbidden by architecture test |

**Reconciled contradictions:**
- The design doc's "statistical field → resolved body" duality is only partially realized as particle-point clumps, not a true grid/particle duality.

**Derived specs:**
- `phase0-player-focus-and-dual-representation.md` — resolve the dual-representation gap.
- Renderer parity fix is small enough to be a task inside the disk spec, not a new spec.

---

### Group E — Physics merge implementation (claude-physics-merge.md)

The detailed session that added EM, hydro, regime classifier, angular momentum, and oblate collapse.

| Topic | Decision | Built? | Gap |
|---|---|---|---|
| EM-lite | Flux-freeze, Lorentz, magnetic braking | Yes | `domain.em` |
| Hydro | SPH pressure-gradient acceleration | Yes | `domain.hydro` |
| Regime classifier | β, Mach, Alfvén Mach, Jeans | Yes | `domain.regime` |
| Angular momentum | Conserved L, oblate collapse | Yes | `domain.stellar` |
| Toomre Q / disc classifier | Disc stability criterion | No | `domain.regime` has tags but no `toomre-q` |
| Sink particles | Accretion-based star formation | No | Still merging by overlap |
| True induction | `∂B/∂t = ∇×(v×B) − ∇×(η∇×B)` | No | Flux-freeze only |
| shape.field | Polymorphic grad/div/curl/Laplacian | No | SPH approximations only |

**Reconciled contradictions:**
- Design doc tick order: gravity → regime → em → hydro → thermo → fusion → sinks → classify. Actual pipeline in `domain.phase0.clj`: hydro → orbital → collision → classify → collapse → fusion → thermal → regime → em. EM/regime run one tick late. This is acceptable as an interim SPH scheme but must be revisited when `shape.field` arrives.

**Derived specs:**
- Update `phase0-protoplanetary-disk-implementation.md` with completed phases and remaining work.

---

### Group F — Architecture exploration (architecture-exploration.md)

Gamification and engine-feel work: clock, HUD, text rendering, viewport, time-stepping.

| Topic | Decision | Built? | Gap |
|---|---|---|---|
| Adaptive clock | Display sim-time, rate, stats | Yes | HUD clock implemented |
| Text rendering | STBEasyFont for HUD | Yes | `infra.render/render-text` |
| Viewport | Use real framebuffer size | Yes | Fixed |
| Leapfrog + tiered dt | Symplectic integrator, quantized dt changes | Partial | Leapfrog exists; dt is continuous, not tiered |
| HUD layout | Clock/stats top-left, coherence bottom-left | Yes | Implemented |

**Derived specs:** None new — these are mostly done and operational.

---

### Group G — Formation / rendering investigation (formation-rendering-investigation.md)

The session that fixed the "gas looks wrong / only one sun" problems and added player-observer visuals.

| Topic | Decision | Built? | Gap |
|---|---|---|---|
| Accretion radius | Star keeps feeding zone as photosphere contracts | Yes | `c/accretion-radius` |
| Main-sequence radius floor | Collapse stops at realistic stellar radius | Yes | `law.stellar/main-sequence-radius` |
| Softening tied to dt | Cloud stays bound | Yes | Tunable in `create-world` |
| Physics-coupled sizes/colors | Radius/color from physical properties | Yes | `infra.render` |
| Player focus reticle / coherence bar | Visual observer feedback | Yes | HUD overlays |
| Keyboard control of focus | Drift/release with keys | Partial | Input exists; influence mechanic is minimal |
| Collision outcomes by temperature | Merge/bounce/fragment | No | `law.stellar/malleability` exists but handler always merges |

**Derived specs:**
- `phase0-chemistry-differentiation.md` — temperature/composition-driven collision outcomes and material classes.

---

## Summary of missing specs to write

1. **Update `phase0-protoplanetary-disk-implementation.md`** — mark Phases 1–4 done, keep 5–9 as next work, fix tick-order note.
2. **`phase0-player-focus-and-dual-representation.md`** — focus must promote/demote matter between statistical and resolved representations.
3. **`phase0-narrator-presence.md`** — optional AI storyteller layered into ambience, event phrasing, and addressable chat.
4. **`phase0-habitability-handoff.md`** — criteria for a planet to leave Phase 0 and what data is passed to Phase 1.
5. **`phase0-chemistry-differentiation.md`** — composition-driven material classes and temperature-aware collision outcomes.

---

## Notes deliberately outside Phase 0 scope

- Gate network / multiverse graph (fiction established, no code, too early).
- Full Myth Engine / Scribes / Facets (components exist, systems do not; belongs to later phases).
- Embodied-person character creation (Phase 0 ends at habitable-world candidate).
- Multi-nebula drift network (failure model exists; network topology is post-Phase 0).
