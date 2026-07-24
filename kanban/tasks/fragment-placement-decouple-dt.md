---
uuid: "fragment-placement-decouple-dt"
title: "Place disk fragments at physical radius, decoupled from the bulk dt"
status: "review"
priority: "P1"
labels: ["domain", "physics", "genesis", "multi-timescale", "blocker"]
created_at: "2026-07-23T00:00:00Z"
source: "kanban/tasks/fragment-placement-decouple-dt.md"
category: "specs"
estimate: 5
---

# Place disk fragments at physical radius, not dt-dictated radius

> multi-timescale epic, card 2. Design: `docs/designs/multi-timescale-integration.md` §3.5.
> The second arm of the blocker: planets are shoved out to ~162 AU purely because
> the spawn-radius floor is computed against the huge bulk `dt`. Even circularized,
> bodies beyond the 100-AU apoapsis gate can't become candidates. Once compact
> bodies sub-step (card 1), placement no longer needs the coarse global `dt`.

## Grounded integration (from investigation, cite file:line)
- `domain.stellar.disc/resolvable-orbit-radius` (`disc.clj:156-167`) forces every
  fragment to `r ≥ ∛(G·M·(min-periods·dt/2π)²)` with `min-fragment-orbit-periods`
  = 50 (`disc.clj:148-155`), evaluated at the global `dt` (~80 yr live) → ~162 AU
  floor. Used at spawn in `domain.stellar.disc-evolution` (binary branch
  `disc_evolution.clj:174-175`, GI branch `disc_evolution.clj:217-218`).
- The orbit-stability gate caps apoapsis at `max-apoapsis-au` = 100 AU
  (`orbital/stability.clj:25-33`), so a fragment born at 162 AU fails even if
  perfectly circular.
- Fix: once card 1 sub-steps compact bodies, place fragments at their PHYSICAL
  disk radius (the disk's actual annulus, ~1–30 AU), decoupled from the bulk `dt`
  — sub-stepping now provides the resolution the 50-step floor used to buy. Note
  the K-ceiling coupling (design §3.3): at 1 AU around 1 M☉, dt=80 yr,
  f_orb=1/20 demands K=1600, so placement below ~0.3 AU would exceed the 4096
  clamp — keep the placement floor at or above ~0.3 AU, or revisit the clamp.
  Keep velocity assignment exactly as-is (already correct:
  `softened-circular-speed`, tangential, star frame — `disc_evolution.clj:180-230`,
  `planet_formation/orbit.clj:48-94`).
- Reconcile `min-fragment-orbit-periods` / `resolvable-orbit-radius`: either retire
  the dt-based floor for compact bodies (they're sub-stepped now) or re-base it on
  the effective sub-step resolution, not the global `dt`.

## Done when (player-visible via live pm2 window)
- Newly formed planets appear at physically plausible radii (≈1–30 AU), inside the
  100-AU apoapsis gate, not clustered at 160+ AU.
- Combined with card 1, worlds routinely become `c/planet-candidate`s under normal
  simulation.
- `clojure -M:test` + architecture-test green.

## Risks
Placement radius interacts with disk mass/temperature/snowline classification —
verify material-class/thermal-band still resolve sensibly at the new radii. Don't
regress the (correct) spawn velocity. Existing far-flung bodies won't retroactively
move; this fixes new formation.

## Dependencies
Card 1 (sub-stepping provides the resolution that lets placement ignore the global
dt). Together they unblock candidate emergence.

## Work notes (2026-07-23, branch spark-gravity-bound-body)
- Decision: **retired** the dt-based floor at spawn (not re-based). Placement is
  now the physical disk radius (0.5× disk for binary companions, 0.3× for GI
  fragments) floored at the new dt-independent
  `disc-evolution/fragment-placement-floor-m` = 0.3 AU (K-clamp coupling, design
  §3.3). `disc/resolvable-orbit-radius` + `min-fragment-orbit-periods` kept as
  documented diagnostics (still re-exported by `domain.stellar.structure`); their
  docstrings record the retired role.
- Velocity assignment untouched (`softened-circular-speed`, tangential, star
  frame). Existing far-flung bodies not migrated.
- Representative numbers (1 M☉): old floor = 631 AU at the test dt (1e10 s),
  252 AU at live dt ≈ 80 yr; new placement = ~3 AU for a 10-AU disk, floor
  0.3 AU.
- Classification risk checked: spawn radius feeds no classifier directly.
  `classifier/thermal-band` derives from live separation `a` at classify time
  (0.3–30 AU around 1 L☉ → ~200–500 K → :cold/:temperate/:warm — all valid
  buckets); `material-class` uses composition+mass+temperature only (GI fragment
  at 300 K, solar composition, ≥1e25 kg → :gaseous, unchanged). The core-
  accretion seeder already placed at 0.1–30 AU annuli independently of
  `resolvable-orbit-radius` — untouched.
- Tests: +4 deftests in `test/domain/disk_evolution_test.clj` (physical radius,
  dt-independence, 0.3-AU floor clamp, binary physical radius).
  `bin/test domain`: 646 tests / 6625 assertions, 0 failures.

## Work notes (2026-07-23, velocity pairing rule)

Live verification caught a second seam: placement radius was fixed but spawn
VELOCITY still paired with the softened law. At live ε=3342 AU,
`softened-circular-speed` at 2.2 AU = 0.17 m/s; the sub-stepper's exact
Newtonian drift read that as a radial plunge → e≈1 within ticks (all 23 live
planets). Fix: sub-stepped spawns (GI `:gas-giant` branch here, and
`planet_formation.orbit/build-planet-spec` for `:planet` seeds) now use
`law/newtonian-circular-speed`; the `:protostar` binary branch stays on
`softened-circular-speed` (Euler path, self-consistent in the harmonic core).
Pinned by `spawn-velocity-pairs-with-substepper` in the card-3 suite.
