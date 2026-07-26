---
uuid: "planet-orbit-circularization-blocker"
title: "Planets never enter stable orbits (e≈1) — blocks all planet-candidates and the whole voxel/progression pipeline"
status: "todo"
priority: "P1"
labels: ["domain", "physics", "genesis", "blocker", "spark-flight"]
created_at: "2026-07-23T00:00:00Z"
source: "kanban/tasks/planet-orbit-circularization-blocker.md"
category: "specs"
estimate: 8
---

# Planets never enter stable orbits — the upstream progression blocker

> Discovered 2026-07-23 by driving the LIVE dev window over nREPL (see
> receipts.edn, `docs/designs/spark-flight-and-camera.md` north star). The voxel/
> narrowing/commitment pipeline is fully wired on this branch but UNREACHABLE:
> nothing ever becomes a `c/planet-candidate`, so binding → commitment → voxel band
> can never fire. This gates the entire "fly to a planet and resolve it" goal.

## Evidence (live world, tick ~133k, 13.6 Myr, arc :arc/life-emergence)
- `n-candidates 0` — no entity carries `c/planet-candidate`
  (`domain.narrowing/candidate-worlds`, `narrowing.clj:182-186`).
- The handoff gate `eligible-candidate?` (`domain.stellar.classifier:740-762`)
  requires `c/orbit-stable` true AND eccentricity < `candidate-max-eccentricity`
  (0.4, `classifier.clj:688-690`). Live: **0 of 58 classified bodies are
  orbit-stable.**
- Measured with the game's own `domain.orbital.stability/two-body-elements`
  against the nearest star: all 36 bound bodies have **eccentricity 0.9999999–
  1.0** (near-radial plunge orbits; semi-major axes 34–140 AU but periapsis ≈ 0).
  Even all 6 living worlds (`c/ecology` living) are on these orbits.
- `orbit-stability` also floors periapsis at star-radius + 5 stellar radii and
  caps apoapsis at `max-apoapsis-au` = 100 AU (`orbital/stability.clj:19-33`);
  radial plunge orbits fail the periapsis floor regardless of the eccentricity cap.

## Diagnosis — CONFIRMED (investigation 2026-07-23, live-measured)
Neither original hypothesis. Both spawn paths assign velocity CORRECTLY (softened
circular speed, tangential, in the star's frame): disk fragments
(`disc_evolution.clj:180-230`) and core-accretion seeds
(`planet_formation/orbit.clj:48-94`, `seed.clj:132-137`). The DM halo is
dynamically negligible at planet-star scales (halo accel ≈3.5e-15 vs star ≈5.9e-7
m/s² at 100 AU), so H2's "wrong frame" is refuted too.

**Actual mechanism — a `dt`-resolution cascade (the recurring dt-dilation bug
class; see `.agents/skills/physics-dt-unit-mismatch/`):**
- Live `:sim/dt` ≈ 2.545e9 s (~80.6 yr/tick); `:sim/softening` ≈ 5.0e14 m (~3342
  AU) — at its ceiling `pacing-soft-max` (`pacing.clj:56,139-140`).
- `disc/resolvable-orbit-radius` (`disc.clj:156-163`) forces every fragment out to
  the smallest radius whose Keplerian period spans `min-fragment-orbit-periods`
  = 50 (`disc.clj:148-155`) steps at the CURRENT `dt`. At live `dt` that floor
  ≈162 AU — matching the observed 34–310 AU placements. Planets aren't there
  because disks are that big; they're there because of the 50-step rule at a huge
  bulk-cloud `dt`.
- 50 steps/orbit avoids a one-tick fling but is far too few for symplectic Euler
  to hold eccentricity over the ~3,400 orbits the sim then runs (13.6 Myr) — the
  initially-circular orbit secularly decoheres to e→1. Live: specific angular
  momentum `h/h_circular` = 0.0001–0.07 (15×–1000× too small), recomputed with the
  CORRECT softened potential — a genuine orbit-coherence loss, not a gate-math
  artifact.
- Compounding driver: the static DM halo (escape vel ≈325 m/s at scale radius,
  `spark-redesign` card 1) can't retain stars moving 47–1180 m/s, so the 5-star
  system ballistically disperses to million-AU scales, inflating the enclosed
  radius → `:sim/softening` to its clamp → pushing fragment placement outward. One
  drifting root cause: dispersal → pacing/softening ceiling → coarse placement →
  marginal per-orbit resolution → secular eccentricity growth.

## Fix options (high-blast-radius — pick approach before coding)
1. **PRIMARY / correct (multi-timescale `dt`):** add a pacing bound in
   `pacing.clj` capping global `dt` by the shortest currently-resolved body's
   orbital period once stars/planets exist (not just bulk-cloud `t_dyn` +
   complexity). Keeps orbits coherent without pushing placement outward. Cost:
   sim-time advances slower once planets form (more ticks per sim-year).
2. **Cheaper / partial:** raise `min-fragment-orbit-periods` above 50
   (`disc.clj:148-155`). More steps/orbit, but pushes planets to LARGER radii and
   doesn't help bodies already placed — a band-aid.
3. **Architectural:** sub-step compact bodies (stars/planets) at a finer sub-`dt`
   while bulk gas keeps the coarse `dt`. Most correct for performance, biggest
   change.
4. **Secondary, independently real, low-risk (do regardless):**
   - `classifier/central-star` (`classifier.clj:411-434`) picks the world's
     most-massive star, not the nearest — wrong for a 5-star system.
   - `orbital/stability.clj:49-52,99-103` two-body-elements assumes r ≫ softening,
     now false (eps ≫ r by 10–100×); use the softened potential the integrator
     actually applies.
   - Do NOT just retune the DM-halo to hold the system together — that treats the
     dispersal symptom; the placement/resolution bug corrupts orbits even in a
     bound system.

## Test to add
Spawn a disk fragment via `disc_evolution.clj` at realistic LATE-sim `dt`/
`softening`, run it through `domain.ecs.tick/run-parallel` for several hundred–
thousand ticks, assert eccentricity (softened two-body-elements) stays < ~0.1
rather than drifting to 1 — a windowed-equivalence test per the
`physics-dt-unit-mismatch` skill.

## Done when (player-visible via live pm2 window)
- Some formed worlds reach `c/orbit-stable` true with eccentricity < 0.4 and
  become `c/planet-candidate`s under normal simulation (no hand-forcing).
- Following/flying to such a world and sustaining focus accrues binding to the
  capture threshold, commitment fires, and its voxel band renders — the loop the
  whole spark-flight epic depends on.
- A test asserts a scripted nebula→star→planets run yields ≥1 planet-candidate.
- `clojure -M:test` + architecture-test green.

## Risks
Touching formation dynamics is high-blast-radius (formation-progress, the escape/
dark-matter-halo work, `create-world` nondeterminism gotcha). Decide hypothesis 1
vs 2 with a focused investigation FIRST — a physics fix and a gate fix are very
different changes. A debug/sandbox force-commit path may be worth adding so the
voxel/sculpt UI (spark-flight Wave 0 payoff + tech tree) can be built/tested in
parallel while this is resolved.

## Fix designed → the `multi-timescale` epic
The fix is now designed: `docs/designs/multi-timescale-integration.md`
(Wisdom-Holman Kepler sub-stepping inside the integrator + physical fragment
placement). This card is the "why"; the implementation lives in the epic cards:
`integrator-kepler-substep`, `fragment-placement-decouple-dt`,
`orbit-integration-regression-tests` (the P1 trio that unblocks candidates),
`central-star-nearest-attractor`, `stability-softened-elements`,
`lod-rung-onrails-optimization`.

## Dependencies
None upstream — this IS the upstream blocker. Blocks the player-visible payoff of
`focus-follows-pilot`, `voxel-sculpt-verb-palette-wiring`, and the whole
progression the tech tree feeds.

## Live verification (2026-07-23, post-fix)

Sub-stepper + placement + spawn-pairing landed; fresh nebula observed over
~13k ticks. Verdict: **integration decoherence fixed.** Bound planets hold e
indefinitely (1024: e=0.3827→0.3812 over 4600 ticks; 1012: 0.85271 flat) —
first sub-0.4 orbit in sim history. 20/24 planets are hyperbolic and STAY
hyperbolic (the sub-stepper faithfully maintains state; they were ejected in
the formation-era cluster chaos, not by the integrator). Pre-fix all 36 were
"bound" at e≈0.9999999+ (numerically forced plunge).

Remaining gate-blockers for candidate emergence, in order:
1. **Wrong-star gate evaluation (card 4 scope):** handoff `eligible-candidate?`
   and Phase-2 `c/orbit-stable` evaluate elements against `central-star`
   (most-massive, eid 491) — but the viable planet (1024, e=0.38, a=31 AU)
   orbits star 258. `central-star 491 ≠ 258` → gate is blind to the system's
   best planet. Per-body true-parent evaluation needed in classifier.clj.
2. **Temperature band:** candidates need 150–400 K. 1024 at 31 AU ≈ 47 K
   (:frozen) — fails regardless of e. The live hope is inner planets: 1023
   (a=3.1 AU → ~158 K, in-band) has e=0.606 and falling (0.6068→0.6060 per
   4600 ticks — far too slow to matter this run).
3. **Population sterility:** planet–planet gravity is ~zero at ε=3342 AU
   (compact-pair-softening card) — no scattering to circularize eccentric
   inner planets, no re-capture of the ejected 20.

## Status update (2026-07-23 late): integration era closed, formation-survival era open

With the sub-stepper + placement + spawn-pairing + per-pair softening all
landed, the blocker has MORPHED. Integration decoherence is dead (bound
planets hold e indefinitely; the star–planet force channel is live inside
the old dead-zone). Three consecutive live runs (18:35, 20:04, 20:14
restarts) ejected 100% of planets during the EMBEDDED formation era: the
collapsing clump's tide genuinely dominates the host star's pull
(measured ~2000× at 5 AU/500 AU clump geometry), the dominance gate
honestly falls through, and the raw Euler ticks at dt≈80 yr scramble the
orbit before the envelope disperses. The 16:24 run (4/24 bound) was
survivor's luck. This is no longer an integration bug — it is the
halo/dispersal question (design §6 bullet 3: why is the cluster unbound at
47–1180 m/s vs ~325 m/s escape?) plus a missing formation-era disk
protection model (real planets form shielded inside a disk, not naked in
the collapsing clump tide).

## Formation-survival trio: VERIFIED at birth, partial at survival (2026-07-24)

Post-trio headless run (scratchpad/trio.edn, seed 42, 12k ticks): for the
first time, planets are BORN BOUND — 1024 at 32.4 AU (a=33.4), 1022 at 558 AU
(a=311.7). 1022 survives to t=11900 and CIRCULARIZES: a 311.7→109.3 AU,
e 0.137→0.053 — the first healthy stable orbit in sim history. Remaining
gaps: (1) the t≈4100 population was still born unbound at 1.9k–71k AU —
the v2 gate did not prevent these births; suspect the direct-merge packet
channel (kept raw L when sink-absorb was renormalized — only disk-route
packets were renormalized) or another spawn path; (2) 1024 was stripped
within 500 ticks of a bound 32-AU birth (secular tide from sibling star —
mechanism unautopsied). Survival rate this run: 1/25. Up from 0/infinity —
the trend is right, the work continues.
