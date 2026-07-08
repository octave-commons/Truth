---
uuid: "law-planet-formation-namespace"
title: "Create law/planet_formation.clj; relocate constants out of domain"
status: "todo"
priority: "P2"
labels: ["fix", "phase0", "chemistry"]
created_at: "2026-07-06T16:21:51.000000000Z"
source: "kanban/tasks/law-planet-formation-namespace.md"
category: "fix"
---

# Core Accretion Physics Spec

**Status:** ready for implementation
**Milestone:** M3 in `kanban/tasks/roadmap-phase-0-physics-honesty-chemistry-disks-plasma-inspection.md`
**Scope:** make planet mass and composition *emerge from accretion* instead of a disk-mass fraction and a static lookup table. Add a sub-grid planetesimal-formation timescale (streaming instability as a gate, not particles), a critical-core-mass + viscous-supply-limited runaway gas accretion model, and condensation-sequence composition for seeded planets.
**Depends on:** `docs/research/physics/protoplanetary-disks-planet-formation.md`, `kanban/tasks/render-material-color-element-keys.md` (needs `c/comp-condensed`), `kanban/tasks/protoplanetary-disk-and-planet-formation-spec.md` (disk regime).

***

## 1. The gaps this closes

The disk-regime and GI-fragmentation paths are real and wired. The **planet seeder itself is a heuristic**:

- **Mass is a disk fraction.** `planet-seeds` uses `core-accretion-timescale < disk-age` as a binary gate (`planet_formation.clj:171`), then sets planet mass = `min(0.3·annulus-mass, …)` (`planet_formation.clj:177`). No critical core mass, no envelope capture, no runaway phase, no viscous supply limit.
- **Composition is a lookup table.** `planet-composition` (`planet_formation.clj:73`) returns three hard-coded element maps keyed only on `:terrestrial`/`:ice-giant`/`:gas-giant`. It ignores disk composition, snow-line position, and the condensation sequence.
- **Streaming instability is absent entirely.** No `streaming-instability` anywhere in `src/`; the `:streaming-zone` regime tag is never emitted.
- **The seeder ignores the regime's `:solid-surface-density`** and recomputes σ_solid per annulus (`planet_formation.clj:165`).
- **Gammie α is a constant** (`disk-viscous-alpha`, `stellar.clj:1419`), never derived from the cooling β that is already computed.

Everything here stays **sub-grid**: planetesimals and cores are below mass resolution, so they appear as surface densities, timescales, and the one-shot seeder — never as new ECS bodies (except the `:planet` the seeder already emits).

***

## 2. Invariants

1. `:planet` entities remain produced **only** by `planet-seeds` (epic invariant 2).
2. Seeded-planet **mass is accretion-derived**: it is the outcome of solid accretion + (for giants) runaway gas capture limited by disk supply — not a fixed disk fraction.
3. Seeded-planet **composition is drawn from `c/comp-condensed`** at the formation radius, so bulk composition varies with position relative to the snow line(s).
4. Disk mass, angular momentum, and element fractions are conserved through seeding (debited from the star's disk components).
5. Streaming instability appears only as a **planetesimal-formation timescale / solid-surface-density gate**; it never spawns particles (that is D9).

***

## 3. Planetesimal formation (sub-grid streaming instability)

Planetesimals form where dust concentrates enough for streaming instability to run. We gate the *availability* of a solid reservoir, not the individual clumps.

Given local solid surface density `Σ_solid`, gas surface density `Σ_gas`, and Stokes number `τ_s` (research §2.3):

```
metallicity-ratio  Z_d = Σ_solid / Σ_gas
SI active when      Z_d ≳ Z_crit(τ_s)      (Youdin & Goodman 2005; Z_crit ~ 0.01–0.02)
planetesimal-formation-timescale  t_pf ≈ (a few) × orbital periods where SI is active
```

- `Σ_solid` comes from `c/disk-regime :solid-surface-density` (the seeder must **read it**, not recompute — closes planet gap #5).
- Where `Z_d < Z_crit`, no planetesimals form → no solid feed → seeder produces no core there.
- `t_pf` sets the earliest disk-age at which the seeder may run in that annulus.

**New fn `domain.planet-formation/planetesimal-formation-active?`** → boolean gate from `(Σ_solid, Σ_gas, τ_s)`.

## 4. Core growth and critical core mass

Solid (planetesimal/core) accretion proceeds from small seeds. The seeder emits embryos as small as $\sim0.01$–$0.1\,M_\oplus$ where the local solid budget supports growth; the body then accretes solids over subsequent ticks.

A core must reach a threshold before it can hold a hydrostatic envelope and start runaway gas accretion. Scale the standard $\sim10\,M_\oplus$ solar-composition value with orbital distance to capture the weaker thermal pressure and lower opacity at larger radii (Pollack+1996; Rafikov 2006):

```
M_crit(r) ≈ 10 M⊕ · (r / 1 AU)^(3/4)
```

This is a tunable proxy; a real opacity-dependent envelope model is deferred to Phase 1.

- While core mass `M_core < M_crit(r)`: the body accretes solids only → a **terrestrial** or (beyond snow line) **ice-rich** core.
- When `M_core ≥ M_crit(r)` and gas is still available: **runaway gas accretion** begins.

## 5. Runaway gas accretion (viscous-supply-limited)

Once `M_core ≥ M_crit`, envelope growth is limited by what the disk can deliver, not by the core:

```
Ṁ_gas ≤ viscous supply rate = f · (disk viscous accretion rate through the annulus)
final gas-giant mass = M_core + ∫ Ṁ_gas dt   until disk gas in the feeding zone is exhausted
                        or the disk dissipates
```

- The viscous accretion rate uses the disk's α (see §7) and Σ_gas.
- Cap the giant at the physical envelope limit already used for GI fragments (below the deuterium-burning mass) so seeded giants and GI embryos share the same `:gas-giant`/`:brown-dwarf` classification boundary.

**Result:** a small core far from gas → terrestrial; a core that reaches `M_crit` while gas remains → gas giant, with the final mass set by supply and timing, not `0.3·annulus-mass`.

## 6. Composition from the condensation sequence

Replace the static `planet-composition` lookup with a derivation from `c/comp-condensed` at the formation radius (research §2.4, Öberg 2011):

- **Inside the H₂O snow line:** bulk = condensed rock + metal (silicates, Fe/Ni), volatiles stay gaseous → **terrestrial**.
- **Beyond the H₂O snow line:** condensed inventory gains water ice → higher solid budget (the existing 3.5× jump) and ice-rich cores → **ice giant** / icy body.
- **Gas-giant envelope:** the captured gas carries the *gas-phase* composition at the formation radius (H/He-dominated, C/O set by which ices are frozen out — the fossil record of formation location).

The single H₂O snow line is sufficient for M3. CO/CO₂ snow lines (which sharpen atmospheric C/O) are part of the molecular network, **deferred D8**.

**Changed fn:** `planet-composition` takes `(comp-condensed, formation-radius, snow-line, planet-type)` and returns an element map, not a table lookup.

## 7. Gammie steady-state viscosity α(β)

Close planet gap #7: derive the effective α from the cooling ratio β instead of the `^:const` (research §3.2):

```
α(β) = 1 / ((9/4) · γ · (γ−1) · β)     in the gravito-turbulent steady state
```

- Feed `α(β)` into `disk-viscous-timescale` (`stellar.clj:1468`) and the runaway gas supply (§5).
- Keep a floor/ceiling so a stable (large-β) disk uses a small background α and a fragmenting disk does not diverge.

This makes cooling and transport self-consistent: fast-cooling disks are more turbulent and accrete faster.

***

## 8. Tests

1. `core-below-crit-stays-terrestrial` — a core that never reaches `M_crit` yields a rocky/icy planet, no gas envelope.
2. `core-above-crit-runs-away-into-giant` — a core reaching `M_crit` with gas available becomes a `:gas-giant`; final mass tracks viscous supply, not a disk fraction.
3. `giant-mass-supply-limited` — halving Σ_gas (or shortening disk lifetime) lowers the final giant mass.
4. `planetesimals-gate-on-metallicity-ratio` — `Z_d < Z_crit` → no core forms in that annulus.
5. `planet-composition-varies-across-snow-line` — a planet seeded inside the snow line is rock/metal-dominated; beyond it, ice-rich; giants carry gas-phase C/O.
6. `seeder-reads-regime-solid-surface-density` — the seeder uses `c/disk-regime :solid-surface-density`, not a recomputation.
7. `alpha-tracks-cooling-beta` — smaller β → larger α → shorter viscous timescale.
8. `seeding-conserves-disk-mass-and-angmom` — (keep existing) mass/angmom debited from the star.

***

## 9. Promotion path

| File | Change |
|------|--------|
| `src/domain/planet_formation.clj` | `planetesimal-formation-active?`; `M_crit`; runaway gas accretion; `planet-composition` from `c/comp-condensed`; read regime `:solid-surface-density`. |
| `src/domain/stellar.clj` | `α(β)` in `disk-viscous-timescale`; expose viscous supply rate to the seeder. |
| `src/law/planet_formation.clj` | **New namespace** (task card `law-planet-formation-namespace`): `M_crit(r)` helper, `Z_crit`, snow-line/ice-enhancement constants relocated from `domain`. |
| `test/domain/planet_formation_test.clj` | Tests §8.1–8.6, 8.8. |
| `test/domain/formation_integration_test.clj` | Extend the end-to-end run to assert accretion-derived mass + composition variation. |

***

## 10. Decisions

1. **Planetesimals & cores are sub-grid** — small seeds + timescales + surface densities + the one-shot seeder; no particles. Cores continue accreting after seeding. Explicit clumping is **D9**.
2. **Pebble accretion is deferred (D6)** — gated on the radial disk profile (Stokes number needs local Σ_gas, T) and only if planetesimal-only accretion fails to grow a giant within the disk lifetime.
3. **One H₂O snow line for M3**; CO/CO₂ lines fold in with the molecular network (**D8**).
4. **GI fragment survival check** (research disk Q4 — fragments migrating into the star) folds into migration (**D2**), gated on the radial disk profile.
5. **Minimum seed mass** — seeds may be as small as ~0.01–0.1 M⊕; post-seed solid accretion and gas capture determine final type.
6. **Critical core mass** — scales with orbital radius: M_crit(r) ≈ 10 M⊕ · (r / 1 AU)^(3/4).

## 11. Open questions

(none — research disk Q2 pebble, Q3 composition, Q4 fragment survival resolved above.)
