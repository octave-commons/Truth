# Spec: Seed-and-Grow Condensation — decouple resolved-body mass from parcel mass

**Status:** implemented (2026-07-06; kimi)
**Owner handoff:** OpenCode (kimi) → runtime / next agent
**Depends on:** M3 gradual mass transfer (`domain.mass-transfer`, committed `0d03d0f`) — the "grow" half is DONE.
**Relates to:** `docs/specs/epic-phase0-physics-honesty.md` (M3 core-accretion, sub-grid planetesimal timescale), `docs/designs/resolution-regimes-and-scale-coupling.md`.

---

## 1. Problem

The smallest resolved body the sim can form is one gas parcel:

```
parcel mass = nebula-mass / gas-count = 4e30 / 1000 = 4e27 kg
            = 669.8 Earth masses = 2.11 Jupiter masses
```

So bodies classified `:planetesimal` are physically ~2 super-Jupiters — nonsense
as a name and as physics. A life-bearing world should resemble Earth (~6e24 kg);
the smallest *necessary* astronomical body is ~the Moon (7.3e22 kg); the *ideal*
smallest is a Chicxulub-scale asteroid (~1e15 kg) — mass-extinction impactors are
evolutionarily important.

**Root cause (important):** this floor is NOT a law of physics and NOT a
necessary consequence of the Lagrangian (SPH) scheme. Lagrangian only floors the
resolution of *gas* (you cannot resolve structure inside one parcel). The
*body* floor comes from a separate, changeable rule: **"a resolved body is made
of whole parcels"** — condensation promotes a whole parcel (`:nebula → :planetesimal`),
merges fuse whole parcels. Break that rule and the body floor decouples from the
parcel grain.

**Not fixable by brute force.** Benchmark (2026-07-06, dev paused):

| gas-count | ms/tick | rate | grain |
|---|---|---|---|
| 1000 (now) | 32 | ~30/s | 669 M⊕ |
| 2000 | 64 | ~16/s | 335 M⊕ |
| 4000 | 139 | ~7/s | 167 M⊕ |
| 8000 | 332 | ~3/s | 84 M⊕ |

Super-linear (≈2.1–2.4× per doubling). Even sub-Jupiter grain (8000) is ~3/s —
unusable. Refining the global parcel set is a dead end.

## 2. Decision

Condensation **seeds a small physical body** for the lowest rung of the
substellar ladder (`:nebula → :planetesimal`) instead of promoting a whole
parcel. Larger gas-collapse outcomes (`:gas-giant`, `:brown-dwarf`,
`:protostar`) still promote the whole parcel — stars and giant embryos form by
gas collapse, not by growing asteroid seeds.

1. When a `:nebula` parcel would condense to `:planetesimal`, spawn a **small
   solid seed** entity whose mass is set by condensation physics
   (~1e15–1e18 kg — NOT the parcel mass), and **debit that seed mass from the
   parent gas parcel** via the dedicated influence `c/mass-flux-condense`. The
   parent parcel stays `:nebula`, slightly lighter. Mass is conserved.
2. The seed persists as a small resolved body. Growth is **collisional
   consolidation** and rare BHL capture, not a runaway channel — a 1e16 kg seed
   has a microscopic Bondi radius and will not accrete meaningfully from the
   gas. It populates the small-body belt (Chicxulub-scale impactors → asteroids
   → planetesimals), not the planet-builder path.

Planets continue to form via the existing disk planet-seeder
(`domain.planet-formation/planet-seeds`). Stars and brown dwarfs continue to
form via direct gas collapse of massive parcels. Seed-and-grow decouples the
*smallest* resolved body mass from the parcel grain.

## 3. Contract / invariants

- **Conservation:** seed mass is debited from the parent parcel in the same tick
  the spawn request is emitted. Because the seed materializes next tick
  (`materialize-lifecycle`), the debit folds through the integrator's `:mass`
  accumulate one tick before the seed appears — a one-tick Jacobi blip that is
  documented and tested.
- **Seeding is gated, not universal:**
  - One seed per parcel (one-shot `c/condensation-seeded` marker).
  - Local-density-maximum gate: a parcel only seeds if it is denser than its
    `:nebula` neighbours within a small radius.
  - Per-tick cap (`:genesis/max-condensation-seeds-per-tick`, default 1).
  - Only on `condense-tick?` (sim-time paced, not tick paced).
  These gates prevent an unbounded seed swarm; the tick is super-linear in N.
- **Parent/child separation:** the seed is offset from the parent parcel by
  ~parent-radius + seed-radius so the two do not immediately remerge.
- **Float-precision boundary (hard):** a 4e27 kg parcel cannot register a change
  below ~1e12 kg (its double ULP). Seeds ≥ ~1e15 kg (asteroid) are safe (~12
  orders down, at the edge); anything finer (crust voxels 1e12, organisms 1e0)
  must NOT be bookkept as a parcel debit — that is the nested-regime problem
  (see `resolution-regimes-and-scale-coupling.md`).
- **Single ECS substrate:** seed via `c/spawn-request-condense` (materialized by
  `materialize-lifecycle`); debit via the dedicated single-writer influence
  `c/mass-flux-condense`, folded by the integrator through its generic `:mass`
  accumulate. No serial post-fold pass.
- **Single-writer:** `c/spawn-request-condense`, `c/mass-flux-condense`, and
  `c/condensation-seeded` are owned exclusively by
  `domain.stellar/condensation-seeder-system`.

## 4. Seed-mass model

Implemented: **(b) Fixed physical seed.**

- `law.planet-formation/condensation-seed-mass-kg` = 1.0e16 kg (~10× Chicxulub).
- Overridable via `:genesis/condensation-seed-mass-kg` on the world.
- Radius derived from `debris-material-density` (~2e3 kg/m³).

Rejected: seed = fraction of parcel (re-introduces parcel-mass coupling).

Future: **(a) Streaming-instability clump** can replace the constant once the
sub-grid solid surface density / Stokes-number model is wired.

## 5. Classifier / vocabulary

Keep the physical ladder thresholds (`law.stellar` opacity-limit / deuterium /
brown-dwarf-desert / hydrogen-burning) — they are real. With seed-and-grow,
`:planetesimal` bodies become *actually* small, so the label stops being a
misnomer. `:gas-giant` and above still form by whole-parcel gas collapse; do
not rename the ladder.

## 6. Implementation sketch

1. `law.planet-formation`: `condensation-seed-mass-kg` constant and
   `condensation-seed-mass` accessor.
2. `domain.ecs.components`: add `c/spawn-request-condense`,
   `c/mass-flux-condense`, `c/condensation-seeded`.
3. `domain.integrator/influence-registry`: add `c/mass-flux-condense` to the
   `:mass` accumulate.
4. `domain.stellar/classifier-system`: skip `:nebula → :planetesimal`
   transitions. Continue whole-parcel promotion for `:gas-giant`,
   `:brown-dwarf`, `:protostar`.
5. `domain.stellar/condensation-seeder-system`: new fan-out emitter that finds
   `:planetesimal` condense candidates, applies the density/cap gates, and emits
   spawn requests + debit + one-shot marker.
6. `domain.genesis/materialize-lifecycle`: add `c/spawn-request-condense` to the
   spawn-request list.
7. `domain.genesis/physics-systems-parallel`: register the seeder.
8. `domain.ecs.registry`: declare the seeder's reads/writes.
9. Tests: conservation, seed-mass-below-parcel, parent-seed separation,
   one-shot, big-condense still whole-parcel, bounded seed count.

## 7. Tests (epistemic contracts)

- `condensation-conserves-mass`: seed mass debited exactly from parent parcel
  via `c/mass-flux-condense`; integrator folds the debit.
- `seed-mass-below-parcel`: a condensed seed is ≪ one parcel (e.g. < 1 M⊕).
- `seed-is-offset-from-parent`: seed position is displaced by ≥ parent radius +
  seed radius so it does not immediately remerge.
- `seeding-is-one-shot`: a parcel carrying `c/condensation-seeded` never emits a
  second spawn request.
- `seeding-skips-big-condensations`: `:nebula` parcels that classify to
  `:gas-giant`/`:brown-dwarf`/`:protostar` are not seeded.
- `classifier-still-promotes-big-condensations`: classifier-system flips massive
  gas parcels to `:protostar` and latches `c/accretion-radius`.
- `seeding-is-bounded`: N does not blow up — seeding gated by local-density
  maximum, per-tick cap, and one-shot marker; bounded seed count over a standard
  collapse run.
- Formation pipeline stays green: `dominant_star`, `formation_integration`,
  biogenesis path still produce a star + Earth-scale worlds (re-tune thresholds
  if the emergent scale shifts — the *intent* is unchanged).

## 8. Open questions

- Seed-mass model (a) vs (b): currently (b); swap to streaming-instability when
  the sub-grid solid budget is available.
- Should sub-planetesimal seeds (asteroid belt) persist indefinitely, or be
  consolidated/reaped below some count? (Physically they should persist and cause
  impacts — ties to Phase-1 mass-extinction modelling.)
- Exact float-precision guard: where to draw the "separate accumulator" line vs
  parcel-debit — measure ULP behaviour in a conservation test.
- Collisional growth: the current literal-overlap collision path does not model
  gravitational focusing. Oligarchic growth from seeds to planets is future work;
  for now planets come from the disk seeder.
