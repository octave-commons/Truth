---
uuid: "phase-0-player-focus-dual-representation-spec"
title: "Phase 0 Player Focus & Dual-Representation Spec"
status: "accepted"
priority: "P1"
labels: ["specs", "phase0", "player"]
created_at: "2026-07-02T19:35:28.969550823Z"
source: "kanban/tasks/phase-0-player-focus-dual-representation-spec.md"
category: "specs"
---

# Phase 0 Player Focus & Dual-Representation Spec

**Status:** canonical  
**Goal:** Make the player's focus a real simulation primitive, not only a camera/HUD feature. Focus must promote matter from a cheap statistical field into resolved ECS entities and demote it back when attention withdraws. After planetary Commitment, the committed world stays resolved while the rest of the system demotes to statistical or scalar representation.  
**Principle:** The same physical quantity is represented at multiple fidelities; the observer's attention decides which fidelity is active locally. No quantity is invented or destroyed during promotion/demotion. The LOD layer decides *which entities are integrated this frame and at what dt*, not which physics code runs.

***

## 1. Background from notes

The core design doc (`docs/designs/truth-phase-0-stellar-nebula-design.md`) states:

> Phase 0 uses a dual representation: statistical field for unfocused or distant regions; voxel-particle resolved mass for focused regions where local physics matters.

> The player's attention is not just a camera feature. It is a computational and ontological act. Focus causes matter and events to become more detailed, more expensive, and more knowable.

Current code (`domain.player`) computes a `probability-collapse-radius` from coherence and focus radius, but this value is used only for rendering emphasis and coherence cost. It does **not** change which entities exist or how they are simulated.

***

## 2. Physical model

### 2.1 Zones

| Zone | Definition | Representation | Tick cadence |
|---|---|---|---|
| **Immediate** | Within `probability-collapse-radius` of observer focus, plus the committed world and its moons after Commitment | Fully resolved ECS entities with all field components | Base tick rate (1 s/s after Commitment) |
| **Regional** | Outside immediate but within a larger attention radius; the star system after Commitment | Statistical cells: mass, momentum, angular momentum, averaged B, temperature, composition | Sub-cycled: 10 s/s, 100 s/s, or longer depending on dynamical stability |
| **Global** | Everything else | Scalar parameters: total mass, total angular momentum, star-formation efficiency, rough chemistry | Updated only when a causal front arrives |

The key efficiency insight: once time is no longer compressed (after planetary Commitment), distant objects can be advanced at very long intervals without the player perceiving a gap. A body in the outer system can be integrated once per 100 simulated seconds and still feel continuous because its dynamical time is long. The LOD scheduler therefore trades **tick rate** for **resolution**, not physics code.

### 2.2 Post-Commitment default

After the player Commits to a planet (`:event/world-commitment`):

- The committed planet and its moons are always `:immediate`.
- The star, other planets, asteroid belts, comets, and close binary companions default to `:regional`.
- The interstellar neighborhood defaults to `:global`.
- The player's focus can still promote Regional bodies to Immediate temporarily.

This is the operational form of the handoff described in `kanban/tasks/ecology-water-gate-snowline.md`: the simulation narrows its compute to the committed world and treats everything else as a causal/statistical backdrop.

### 2.3 Invariants (law/)

- **Mass conservation.** Total mass across all three zones equals the original nebula mass.
- **Momentum conservation.** Net linear and angular momentum are preserved across promotion/demotion.
- **Flux conservation.** Magnetic flux through a surface is preserved when a region is promoted/demoted.
- **Energy budget.** Kinetic + thermal + magnetic energy is bounded by a `law.field` tolerance; the budget may be redistributed but not created.
- **Causality.** Demotion cannot erase events that have already entered the ledger.

***

## 3. Schema additions

### 3.1 New components

```clojure
;; On a resolved gas particle / clump
(def field-zone :component/field-zone)     ;; :immediate :regional :global
(def statistical-mass :component/statistical-mass) ;; kg, for bookkeeping during demotion

;; On the observer singleton
(def attention-shell :component/attention-shell) ;; {:immediate-r m :regional-r m}
```

### 3.2 law additions

- `law.field/field-zone-schema` — `:immediate`, `:regional`, or `:global`.
- `law.field/statistical-cell-schema` — mass, center-of-mass velocity, specific angular momentum, mean B, temperature, composition, density.
- `law.field/attention-shell-schema` — radii and coherence-derivation rule.
- `law.field/promotion-invariant?` — validator for mass/momentum/flux/energy conservation across a promotion/demotion event.

***

## 4. Implementation plan

### Phase 1 — Immediate-zone promotion

**Goal:** When a statistical region enters the immediate focus radius, spawn resolved ECS entities that carry the same conserved quantities.

**Schema → test → implementation:**

1. **Schema:** Add `c/field-zone` and `law.field/statistical-cell-schema`.
2. **Tests:**
   - `promotion-conserves-mass`: promote a 1e27 kg regional cell; total immediate-zone mass increases by exactly that amount.
   - `promotion-conserves-momentum`: the spawned clumps have the same net momentum as the source cell.
   - `promotion-conserves-angular-momentum`: net L of spawned clumps equals source cell L.
3. **Implementation:**
   - Represent regional cells as a sparse spatial hash keyed by integer cell coordinates.
   - `domain.phase0/promotion-system` scans regional cells overlapping the immediate radius.
   - For each overlapping cell, sample `n` resolved particles (Poisson or deterministic) conserving total mass, COM velocity, and angular momentum.
   - Mark spawned particles `:immediate`; remove their mass from the regional ledger.

### Phase 2 — Demotion on focus withdrawal

**Goal:** When resolved matter leaves the immediate zone (and is no longer near any threshold event), collapse it back into a regional statistical cell.

**Tests:**
- `demotion-conserves-mass`: resolved particles in a cell are removed and the cell mass increases by their total.
- `demotion-preserves-ledger`: events emitted by the resolved particles remain in the ledger.
- `demotion-threshold-events-delay`: particles involved in a collision or ignition in the last tick are not demoted until the event is processed.

**Implementation:**
- `domain.phase0/demotion-system` runs after all physics and event systems.
- Particles outside the immediate zone with no recent threshold events are aggregated into their containing regional cell.
- Angular momentum and magnetic flux are written back using the cell's moment of inertia and mean field.

### Phase 3 — Regional / global bookkeeping

**Goal:** Simulate the broad evolution of unfocused matter cheaply enough that the player can drift to a new nebula without the old one becoming free. After Commitment, use sub-cycling so that the committed world runs at 1 s/s while the rest of the system runs at lower cadence.

**Tests:**
- `global-zone-mass-constant`: mass in global zone only changes through promotion/demotion, not through hidden loss.
- `regional-cooling-follows-scaling`: regional cells cool radiatively using the same `domain.stellar/radiative-cooling-delta` law applied to their averaged density and radius.
- `outer-system-subcycle-preserves-orbit`: a Regional body advanced at 100 s/s for 100 ticks matches the same body advanced at 1 s/s for 10000 ticks within the local truncation tolerance.

**Implementation:**
- `domain.lod/lod-scheduler` assigns `:component/field-zone` and a per-entity `next-tick` deadline.
- The integrator skips entities whose `next-tick` is in the future, or integrates them with a larger `dt` if their deadline has arrived.
- Regional cells evolve on a slower sub-tick cadence (e.g., every 10 ticks in Phase 0; 10–1000 s/s after Commitment depending on distance and dynamical time).
- Global zone is a single scalar budget updated only when a causal front arrives.
- After Commitment, the committed world and its moons are forced to the base tick rate; everything else is sub-cycled.

### Phase 4 — Statistical stellar mechanics

**Goal:** Model asteroid belts, comets, stellar activity, and binary mass transfer as probability distributions that only resolve into concrete bodies when they affect the Immediate zone.

**Tests:**
- `asteroid-belt-mass-conserved`: after a resolved impactor is spawned and removed, the Regional asteroid belt mass equals the pre-impact total minus the impactor mass.
- `stellar-flare-sampling-affects-world`: a flare sampled toward the committed world resolves atmospheric effects; a flare sampled away does not.
- `supernova-light-front-arrives-at-c`: a supernova progenitor crossing its threshold schedules a light-front event that reaches the committed world after `d/c` simulated seconds.

**Implementation:**
- Regional bodies are probability distributions over orbital elements, composition, and thermodynamic state.
- Each tick, sample impact/encounter probabilities on Immediate bodies.
- If an event resolves, spawn a temporary resolved entity, integrate it through Immediate, then debit its mass from the distribution and demote or remove it.
- Stellar events (flare, CME, supernova) are sampled from activity models and scheduled as causal fronts.

### Phase 5 — Coherence coupling

**Goal:** Coherence cost and gain reflect the actual simulation work of maintaining focus.

**Tests:**
- `promotion-increases-coherence-cost`: immediate-zone particle count correlates with `coherence-drain-rate`.
- `demotion-reduces-coherence-cost`: widening focus lowers immediate-zone resolution and drain.

**Implementation:**
- Update `domain.player/coherence-drain-rate` to include immediate-zone entity count and regional-cell count.
- Update `coherence-gain-from-event` to also grant coherence when a regional cell produces a threshold event (the player "sensed" it without full focus).

***

## 5. Rendering and feel

- Immediate-zone particles render as individual volumetric puffs with temperature/composition color.
- Regional cells render as larger, lower-opacity blobs with averaged color.
- Global zone contributes to a faint background nebula texture and star-formation efficiency parameter.
- The focus reticle visualizes the immediate/regional boundary so the player learns the control language.

***

## 6. Out of scope

- True grid/particle duality with `shape.field` operators — that belongs to `phase0-protoplanetary-disk-implementation.md` Phase 8.
- Multi-nebula drift network topology — belongs to a post-Phase 0 gate-network spec.
- Narrator presentation of promotion/demotion — belongs to `phase0-narrator-presence.md`.

***

## 7. First deliverable

**Phase 1** (immediate-zone promotion) is the smallest shippable step. It requires no demotion system, no regional evolution, and no rendering changes beyond using the existing particle renderer for newly promoted clumps. It proves that focus can create resolved matter and that conservation laws hold.

Next action: approve this spec, then write schemas, failing tests, and Phase 1 implementation.

---
Triage 2026-07-10 (todo→accepted): PARTIAL/ROADMAP — only tick-cadence LOD (domain.lod) realized; promotion/demotion + conservation invariants entirely open — break into per-phase cards. Needs breakdown into residual ≤5pt cards before re-entering the queue.
---
