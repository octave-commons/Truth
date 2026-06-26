# Phase 0 Player Focus & Dual-Representation Spec

**Status:** draft  
**Goal:** Make the player's focus a real simulation primitive, not only a camera/HUD feature. Focus must promote matter from a cheap statistical field into resolved ECS entities and demote it back when attention withdraws.  
**Principle:** The same physical quantity is represented at multiple fidelities; the observer's attention decides which fidelity is active locally. No quantity is invented or destroyed during promotion/demotion.

---

## 1. Background from notes

The core design doc (`docs/designs/truth-phase-0-stellar-nebula-design.md`) states:

> Phase 0 uses a dual representation: statistical field for unfocused or distant regions; voxel-particle resolved mass for focused regions where local physics matters.

> The player's attention is not just a camera feature. It is a computational and ontological act. Focus causes matter and events to become more detailed, more expensive, and more knowable.

Current code (`domain.player`) computes a `probability-collapse-radius` from coherence and focus radius, but this value is used only for rendering emphasis and coherence cost. It does **not** change which entities exist or how they are simulated.

---

## 2. Physical model

### 2.1 Zones

| Zone | Definition | Representation |
|---|---|---|
| Immediate | Within `probability-collapse-radius` of observer focus | Fully resolved ECS entities with all field components |
| Regional | Outside immediate but within a larger attention radius | Statistical cells: mass, momentum, angular momentum, averaged B, temperature, composition |
| Global | Everything else | Scalar parameters: total mass, total angular momentum, star-formation efficiency, rough chemistry |

### 2.2 Invariants (law/)

- **Mass conservation.** Total mass across all three zones equals the original nebula mass.
- **Momentum conservation.** Net linear and angular momentum are preserved across promotion/demotion.
- **Flux conservation.** Magnetic flux through a surface is preserved when a region is promoted/demoted.
- **Energy budget.** Kinetic + thermal + magnetic energy is bounded by a `law.field` tolerance; the budget may be redistributed but not created.
- **Causality.** Demotion cannot erase events that have already entered the ledger.

---

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

---

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

**Goal:** Simulate the broad evolution of unfocused matter cheaply enough that the player can drift to a new nebula without the old one becoming free.

**Tests:**
- `global-zone-mass-constant`: mass in global zone only changes through promotion/demotion, not through hidden loss.
- `regional-cooling-follows-scaling`: regional cells cool radiatively using the same `domain.stellar/radiative-cooling-delta` law applied to their averaged density and radius.

**Implementation:**
- `domain.phase0/regional-system` evolves regional cells on a slower sub-tick cadence (e.g., every 10 ticks).
- Use the same thermodynamic helpers as resolved matter, but on averaged quantities.
- Global zone is a single scalar budget updated only at major phase transitions.

### Phase 4 — Coherence coupling

**Goal:** Coherence cost and gain reflect the actual simulation work of maintaining focus.

**Tests:**
- `promotion-increases-coherence-cost`: immediate-zone particle count correlates with `coherence-drain-rate`.
- `demotion-reduces-coherence-cost`: widening focus lowers immediate-zone resolution and drain.

**Implementation:**
- Update `domain.player/coherence-drain-rate` to include immediate-zone entity count and regional-cell count.
- Update `coherence-gain-from-event` to also grant coherence when a regional cell produces a threshold event (the player "sensed" it without full focus).

---

## 5. Rendering and feel

- Immediate-zone particles render as individual volumetric puffs with temperature/composition color.
- Regional cells render as larger, lower-opacity blobs with averaged color.
- Global zone contributes to a faint background nebula texture and star-formation efficiency parameter.
- The focus reticle visualizes the immediate/regional boundary so the player learns the control language.

---

## 6. Out of scope

- True grid/particle duality with `shape.field` operators — that belongs to `phase0-protoplanetary-disk-implementation.md` Phase 8.
- Multi-nebula drift network topology — belongs to a post-Phase 0 gate-network spec.
- Narrator presentation of promotion/demotion — belongs to `phase0-narrator-presence.md`.

---

## 7. First deliverable

**Phase 1** (immediate-zone promotion) is the smallest shippable step. It requires no demotion system, no regional evolution, and no rendering changes beyond using the existing particle renderer for newly promoted clumps. It proves that focus can create resolved matter and that conservation laws hold.

Next action: approve this spec, then write schemas, failing tests, and Phase 1 implementation.
