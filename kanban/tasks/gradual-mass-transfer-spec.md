---
uuid: "gradual-mass-transfer-spec"
title: "Gradual Mass Transfer Spec"
status: "done"
priority: "P1"
labels: ["specs", "phase0", "physics"]
created_at: "2026-07-06T17:00:00.000000000Z"
source: "kanban/tasks/gradual-mass-transfer-spec.md"
category: "specs"
---

# Gradual Mass Transfer Realspec

**Status:** specification  
**Scope:** replace all-or-nothing mass movement (whole-parcel condensation, whole-body merging, whole-parcel swallowing) with rate-limited, partial debit/credit so stars feed from disks and planets grow from feeding zones.  
**Depends on:** `docs/research/physics/rate-limited-accretion-mass-transfer.md`  
**Related:** `docs/designs/phase0-sink-particle-formation.md`, `kanban/tasks/law-planet-formation-namespace.md`, `kanban/tasks/phase-0-stellar-winds-mass-transfer-remnants-technical-spec.md`

***

## 1. The gap this closes

Today mass moves in whole units:
- A `:nebula` parcel condenses into one `:debris`/`:protostar` body in a single tick.
- `sink-formation-system` and collision/merge swallow whole parcels whole.
- A `:planet` seed gets its entire mass from a disk-fraction heuristic, not from accumulated accretion.

This produces 1000-Earth-mass “terrestrials,” disappearing clouds, and no visible feeding. Real star-formation codes use **sink particles** that drain gas gradually and **binary mass-transfer** prescriptions that move matter through Roche-lobe overflow. This spec brings those rate laws into the ECS world.

***

## 2. Invariants

1. **Mass is never created or destroyed.** Every debit from a donor has a matching credit to a sink, plus a possible angular-momentum or orbital-energy ledger entry.
2. **Donors stay live while being drained.** A parcel or body loses mass incrementally; it is removed only below a floor mass or when fully depleted.
3. **No parcel resurrection.** Once a donor entity is deleted, its mass cannot reappear from the same entity.
4. **Per-tick transfer is capped.** No single tick may move more than `accretion-fraction-cap` (default 0.25) of a donor’s mass.
5. **Only bound, infalling material accretes.** A passing unbound parcel is not stripped.
6. **Bodies that have collapsed to `:star`, `:brown-dwarf`, or `:planet` never return to `:nebula`.** Mass loss demotes down the existing ladder; only shed material becomes gas.

***

## 3. Two transfer channels

| Channel | Geometry | Driver | Primary rate law | Use case |
|---|---|---|---|---|
| **BHL sink accretion** | Point-mass sink in gas | Relative motion + gravity | $\dot M_{\rm BHL}=4\pi G^2M^2\rho_\infty/(c_s^2+v_\infty^2)^{3/2}$ | Star/proto-star/planet feeding from nebula or disk |
| **Roche-lobe overflow** | Two extended bodies in orbit | Donor radius exceeds $R_L$ | $\dot M=-A\,(M_d/P)\,\delta^3$ plus Ritter/Kolb branches | Close binary mass transfer, tidal stripping |

***

## 4. Components

### 4.1 On every accreting body (`:star`, `:protostar`, `:planet`, `:debris` above a mass floor)

- `c/accretion-radius` — current capture radius $R_{\rm acc}$ (m).
- `c/accretion-rate` — $\dot M$ (kg/s), regime tag (`:subsonic`, `:transonic`, `:supersonic`), and this-tick debit.
- `c/sink-identity` — `softening-length`, `created-at-tick`, optional `spin`.

### 4.2 On every donor parcel/body

- `c/mass`, `c/position`, `c/velocity`, `c/composition` (already exist).
- `c/gas-state` or `c/density` and `c/smoothing-length` so ambient properties can be averaged.
- `c/donor-floor-mass` — minimum mass before deletion (default one parcel mass or zero).

### 4.3 Binary pair relation entity

- `c/binary-pair` — donor-eid, accretor-eid, semi-major-axis, eccentricity.
- `c/roche-lobe` — $R_L$, overfilling $\delta$, overflow flag.
- `c/mass-transfer-rate` — signed rate and accreted fraction $\beta$.

### 4.4 Influences (write-conflict safe)

- `c/mass-flux` — a vector of flux-event maps attached to the emitting entity (the sink for BHL, the binary-pair relation for RLOF). Each event has `:mass-flux/kind` ∈ `#{:bhl :rlof}`, `:mass-flux/delta-m`, `:mass-flux/delta-p`, `:mass-flux/delta-l`, `:mass-flux/tick`, plus kind-specific refs (`:sink-id`/`:donor-id` or `:binary-pair-id`/`:donor-eid`/`:accretor-eid`). The integrator owns `c/mass` and applies every event to the referenced entities.

***

## 5. Systems

### 5.1 `domain.mass-transfer/accretion-radius-system`

For each sink:
- Compute $R_{\rm B}=2GM/c_s^2$ from local mean sound speed.
- Compute $R_{\rm acc}=2GM/(c_s^2+v_\infty^2)$.
- Clamp to $\max(R_{\rm acc}, \eta\,h_{\rm smooth})$.

### 5.2 `domain.mass-transfer/sink-accretion-flux-system`

For each sink:
1. Find donor parcels within $R_{\rm acc}$ using the spatial index.
2. Average $\rho_\infty$, $c_s$, $v_\infty$ over the zone.
3. Compute $\dot M_{\rm BHL}$.
4. Compute $\Delta M = \min(\dot M\Delta t,\; f_{\rm acc}M_{\rm gas},\; f_{\rm donor}M_{\rm donor})$.
5. For each donor, produce `c/mass-flux-accretion` proportional to its mass and inward radial velocity.

### 5.3 `domain.mass-transfer/roche-lobe-system`

For each binary-pair entity:
- Compute $R_L$ via Eggleton (1983).
- Set overflow flag and $\delta=(R_d-R_L)/R_L$.
- If overflowing, compute rate with the **Ritter (1988) isothermal branch** by default.
- Produce `c/mass-flux` with `:mass-flux/kind :rlof`.

The Kolb \& Ritter (1990) adiabatic branch, outer-Lagrangian ($L_2$) overflow, and spin/orbit co-evolution are deferred to `kanban/tasks/roche-lobe-envelope-physics-realspec-deferred-capability.md`.

### 5.4 `domain.integrator` applies `c/mass-flux`

The integrator's `mass-ws` reads `c/mass-flux` events and applies `delta-m` to every referenced entity (sink, donor, accretor). Donors whose new mass falls below the floor are marked with `c/consumed-accrete` (or `c/consumed-mass-transfer`) for reaping at world-construction. Momentum changes (`delta-p`) are folded into `velocity-ws`.

### 5.5 `domain.mass-transfer/conservation-ledger-system` (barrier, optional)

- Sum total mass and linear momentum before/after flux application.
- Emit `:event/mass-transfer-imbalance` if tolerance exceeded (debugging).

***

## 6. Caps and knobs

| Knob | Default | Meaning |
|---|---|---|
| `:mass-transfer/accretion-fraction-cap` | 0.25 | Max fraction of donor gas mass movable per tick |
| `:mass-transfer/donor-fraction-cap` | 0.25 | Max fraction of an individual donor parcel per tick |
| `:mass-transfer/bondi-lambda` | 1.12 | Bondi eigenvalue for isothermal gas |
| `:mass-transfer/softening-factor` | 0.5 | $r_{\rm soft}=\eta\,r_{\rm acc}$ |
| `:mass-transfer/require-bound?` | true | Ignore unbound/infalling gas |
| `:mass-transfer/rolof-A` | 10.0 | Pols scaling prefactor for overflow rate |
| `:mass-transfer/binary-substeps` | 1 | Substeps for fast binary orbits (≥1) |

***

## 7. Tests

1. **bhl-subsonic-scaling:** for $v\ll c_s$, $\dot M\propto c_s^{-3}$.
2. **bhl-supersonic-scaling:** for $v\gg c_s$, $\dot M\propto v^{-3}$.
3. **capture-radius-scaling:** $R_{\rm acc}\propto M/(c_s^2+v^2)$.
4. **accretion-cap-respected:** a sink cannot emit `c/mass-flux` events whose total debit exceeds `accretion-fraction-cap` of available gas in one tick.
5. **momentum-conservation:** total linear momentum is unchanged after the integrator applies `c/mass-flux`.
6. **no-parcel-resurrection:** a deleted donor cannot be re-debited.
7. **roche-lobe-radius:** Eggleton formula matches known tabulated values for $q=0.1,1,10$.
8. **overflow-rate-sign:** donor loses mass, accretor gains $\beta$ fraction.
9. **mass-flux-shape-unified:** both BHL and RLOF produce `c/mass-flux` events with a valid `:mass-flux/kind`.

***

## 8. Promotion path

| File | Change |
|---|---|
| `src/law/mass_transfer.clj` | New namespace: schemas, constants, BHL/RLOF helpers, cap defaults. |
| `src/domain/mass_transfer.clj` | New namespace: `accretion-radius-system`, `sink-accretion-flux-system`, `roche-lobe-system`. |
| `src/domain/ecs/components.clj` | Add `accretion-radius`, `accretion-rate`, `sink-identity`, `mass-flux`, `binary-pair`, `roche-lobe`, `mass-transfer-rate`. |
| `src/domain/ecs/registry.clj` | Register mass-transfer systems and their reads/writes. |
| `src/domain/integrator.clj` | Add `c/mass-flux` to `:mass` influence registry; apply events to sink and donor masses in `mass-ws`; apply `delta-p` in `velocity-ws`. Mark depleted donors with `c/consumed-accrete`. |
| `src/domain/stellar.clj` | Replace whole-parcel sink capture with gradual debit; remove feeding-zone-merge hack. |
| `src/domain/physics/collision.clj` | Keep literal-contact merges; large bodies grow by accretion, not by distant feeding-zone merge. |
| `src/domain/planet_formation.clj` | Use accreted mass budget instead of disk-fraction heuristic for seeded planets. |
| `test/domain/mass_transfer_test.clj` | New tests. |

***

## 9. Decisions

1. **Primary accretion law:** BHL with a 25% per-tick cap. Hubber-style exponential relaxation is a permitted alternative for SPH-like parcels but must preserve the same caps.
2. **RLOF law:** Eggleton radius + **Ritter (1988) isothermal branch** as default; Kolb & Ritter (1990) adiabatic branch, spin/orbit co-evolution, and outer-Lagrangian ($L_2$) overflow deferred to `kanban/tasks/roche-lobe-envelope-physics-realspec-deferred-capability.md`.
3. **Angular momentum:** accreted gas momentum is added to the sink; spin/disk feedback deferred.
4. **Sink creation:** unchanged from `stage2-sink-formation.md`; this spec only changes how existing sinks consume gas.
5. **Rendering:** `infra.render` will read `c/accretion-rate` to brighten/dim the circum-sink envelope and size sinks by mass.

***

## 10. Open questions

1. **Circum-sink disk/spin component:** deferred. A `c/sink-spin` component is added to the deferred spec `kanban/tasks/roche-lobe-envelope-physics-realspec-deferred-capability.md`; it is not needed for the initial rate-limited debit/credit implementation.
2. **RLOF `:mass-flux` shape:** use one shared `c/mass-flux` influence component with a `:mass-flux/kind` key of `:bhl` or `:rlof`, plus kind-specific fields. This lets the conservation-ledger and apply-fluxes systems treat all mass transfer uniformly while keeping provenance visible.
3. **Visualizing partial draining:** the volumetric renderer uses remaining parcel mass to set local emission density and smoothing radius; a draining parcel dims and shrinks rather than disappearing. No new ECS component is required beyond the existing `c/mass` and `c/smoothing-length`.
4. **25% cap:** default remains 0.25 for physical fidelity, exposed as the world-creation knob `:mass-transfer/accretion-fraction-cap`. Tune downward for watchability only after live observation.
5. **RLOF rate prescription:** use the more physical Ritter (1988) isothermal branch as the default for Phase 0 overflow; Kolb & Ritter (1990) adiabatic branch and outer-Lagrangian ($L_2$) corrections are deferred to `kanban/tasks/roche-lobe-envelope-physics-realspec-deferred-capability.md`.
