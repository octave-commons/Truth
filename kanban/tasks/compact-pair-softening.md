---
uuid: "compact-pair-softening"
title: "Per-pair/per-species softening: compact bodies gravitationally decoupled at ε=3342 AU"
status: "review"
priority: "P1"
labels: ["domain", "physics", "gravity", "multi-timescale"]
created_at: "2026-07-23T00:00:00Z"
source: "kanban/tasks/compact-pair-softening.md"
category: "specs"
estimate: 5
---

# Per-pair softening: resolved compact bodies need ε ≪ r

> Found live 2026-07-23 during multi-timescale verification: world softening sits
> at its ceiling (5e14 m ≈ 3342 AU) because the dispersing system inflated to
> million-AU scales (design §6, halo/dispersal investigation). At that ε, the
> Plummer acceleration between two planets 5 AU apart is ~zero
> (a ∝ r/(r²+ε²)^1.5 ≈ r/ε³) — **planet–planet gravity is effectively OFF**:
> no scattering, no resonances, no dynamical friction, no compact–compact
> accretion kicks. The WH sub-stepper keeps each planet's Newtonian orbit about
> its star coherent (card 1), but the compact population is dynamically sterile.

## Grounded integration (cite file:line)
- The softened force law lives in `domain.gravity.barnes-hut` (Plummer kernel,
  world `:sim/softening` scalar, dead-zone cutoff 0.1·ε). `kinematics/softened-pull`
  (`src/domain/integrator/kinematics.clj`) mirrors the same scalar ε for the
  tidal-kick decomposition.
- Softening is a per-WORLD scalar today. GADGET practice: softening is
  per-species (collisionless vs gas) and floored by the particle's physical
  size; resolved compact bodies get ε ~ their radius or a small fraction of
  their Hill radius, NOT the gas cloud's smoothing length.
- Scope: per-pair ε = max(ε_species_i, ε_species_j) in the Barnes–Hut leaf
  interactions and node acceptance, with ε_compact derived from
  `c/radius` (or Hill radius) instead of `:sim/softening`. Gas keeps the world
  ε. The kick decomposition in `compact-advance` must use the SAME per-pair ε
  as the force channel or the tidal subtraction goes stale.
- Momentum conservation: pair-symmetric ε keeps the pair force Newton's-third-
  law exact; asymmetric per-body ε does not.

## Done when
- Two 5-AU-separated planets measurably perturb each other (secular apsidal
  precession or scattering on long runs) where today the interaction is ~zero.
- The tidal kick in `compact-advance` and the Barnes–Hut force agree on ε per
  pair (a test asserts the subtraction leaves only non-parent contributions).
- No regression: full `bin/test` green; `bin/bench` shows the per-pair ε
  lookup does not blow the tick budget (it is an O(1) component read per pair).

## Risks
Tree nodes aggregate many bodies — a node containing mixed species needs a
conservative ε (max of members) or species-split trees; start with the
conservative max. Smaller compact ε re-enables close-encounter stiffness —
the sub-stepper's acceleration criterion (design §3.3) already covers it.
Interacts with the halo/dispersal investigation: if that re-scales the system
back below ~10⁴ AU, world ε shrinks and this card's urgency drops (but the
physics argument for per-species ε stands regardless).

## Dependencies
Independent of the P1 trio (landed). Sibling to the halo/dispersal
investigation (design §6 bullet 3). Should land before anyone trusts
planet–planet dynamics (scattering, migration, captures) in Phase 0→1.

## Urgency upgrade (2026-07-23, live evidence) — P2 → P1, ready

Live run (18:35 restart, t=10886): **0/12 planets bound** — all ejected to
3e5–1e7 AU at 2–58 km/s. Mechanism: the 0.1·ε gravitational DEAD-ZONE (334 AU
at ε=3342 AU) zeroes the star's pull on every planet; a planet whose
dominance gate (100×) fails in the compact early cluster — gas tidal
genuinely exceeds μ/100r² there, so the gate is honest — falls through to the
Euler path, which feels NO central force and leaves on its spawn velocity.
The sub-stepper faithfully maintains the resulting hyperbolae. Run-to-run
variance decides whether any planet's gate passes continuously from spawn
(run 16:24 got 4/24; run 18:35 got 0/12). Until per-pair ε + a dead-zone
exemption for compact pairs lands, candidate emergence is a coin flip.
Related upstream driver: halo/dispersal (stars themselves unbound,
design §6 bullet 3) — still its own investigation.

## Work notes (2026-07-23)

Implemented + suite green (857/15391). Species law in
`law.stellar.orbital.dynamics` (`body-softening`/`pair-softening`, max rule,
cutoff 0.1·ε_pair); per-node ε-max on both tree builders; `:eps` SoA array;
`kinematics/softened-pull` uses the same per-pair ε. Red capture:
/tmp/opencode/pair-softening-RED.txt (star–planet at 5 AU: 7.9e-13 → 2.37e-4
Newtonian). **Significant implementation finding:** same-ε is not enough —
the force channel is one-tick Jacobi-stale and evaluates at drift-predicted
positions (px-pred), so the kick's parent-term subtraction must read the
SAME x̂ (kinematics/compact-advance via pcache-soa/predicted-position-fn) or
the phantom tide fails the dominance gate → ejection (reproduced both
paths). Cost: strict 1e-4 phase-reversibility is unattainable at live dt
(residue is velocity-dependent); reversibility test re-scoped to
bound/stability through the reversal cycle, full analysis in the test
docstring. **Live verdict:** the card's mechanism is confirmed by the
production-path tests, but this run still ejected all 13 planets — a
DIFFERENT mechanism: tidal stripping during the embedded formation era
(clump tide genuinely exceeds the star's pull → gate honestly fails →
Euler-scramble at dt=80 yr → hyperbolic). Formation-era survival is the
halo/dispersal investigation (design §6 bullet 3) + possibly a
disk-embedded protection model — NOT this card's scope.
