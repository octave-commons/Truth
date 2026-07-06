# Spec: Seed-and-Grow Condensation — decouple resolved-body mass from parcel mass

**Status:** draft (decided 2026-07-06; ready for implementation)
**Owner handoff:** Claude Code → OpenCode (kimi)
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

Condensation **seeds a small physical body and grows it**, instead of promoting a
whole parcel:

1. When a gas parcel reaches condensation conditions, spawn a **small solid seed**
   entity whose mass is set by **condensation physics** (a streaming-instability
   clump, ~1e15–1e18 kg — NOT the parcel mass), and **debit that seed mass from
   the parent gas parcel** (the parcel stays `:nebula`, slightly lighter).
   Mass is conserved.
2. The seed then grows — or doesn't — via the **existing M3 gradual BHL**
   (`domain.mass-transfer`). That half is built and cheap (0.58 ms, ~2% of tick).

This decouples resolved-body mass from parcel mass, is *more* physical than
whole-parcel promotion (real planetesimals are ~100 km seeds out of a reservoir),
and makes Chicxulub / Moon / Earth-scale bodies expressible.

Precedent: the disk planet seeder (`domain.planet-formation/planet-seeds`)
already spawns sub-parcel cores (0.01–0.1 M⊕). This spec generalizes that
seed-and-grow pattern to the classifier/condensation path.

## 3. Contract / invariants

- **Conservation:** seed mass is debited from the parent parcel in the same tick
  it is credited to the seed (Jacobi-consistent). Total mass (gas + bodies)
  unchanged by seeding.
- **Seeding is gated, not universal:** one seed per genuine condensation site per
  epoch (a one-shot marker per site), so entity count (N) does not explode — the
  tick is super-linear in N (§1), so uncapped seeding would wreck framerate.
  Consolidation is by accretion + collision, not by seeding every parcel.
- **Float-precision boundary (hard):** a 4e27 kg parcel cannot register a change
  below ~1e12 kg (its double ULP). Seeds ≥ ~1e15 kg (asteroid) are safe (~12
  orders down, at the edge); anything finer (crust voxels 1e12, organisms 1e0)
  must NOT be bookkept as a parcel debit — that is the nested-regime problem
  (see `resolution-regimes-and-scale-coupling.md`). Where the ratio is extreme,
  track body mass and gas mass in *separate* accumulators, never subtract tiny
  from huge.
- **Single ECS substrate:** seed via a `spawn-request.*` lifecycle marker
  (materialized by `materialize-lifecycle`, one-tick Jacobi delay); debit via a
  single-writer influence channel (reuse `c/mass-flux-transfer`, owned by
  mass-transfer, or a dedicated `c/mass-flux-condense`). No serial post-fold pass.

## 4. Seed-mass model (options — pick in implementation)

- **(a) Streaming-instability clump (preferred):** seed mass from local solid
  surface density Σ_solid + Stokes number + metallicity gate — the sub-grid
  planetesimal-formation timescale already specced in the epic (M3 core-accretion
  realspec). Physically grounded; ties condensation to composition.
- **(b) Fixed physical seed:** a constant seed mass (e.g. ~1e16 kg) as a
  `law.planet-formation` constant. Simplest; good first cut to validate the
  mechanism before wiring (a).
- Reject: seed = fraction of parcel (re-introduces parcel-mass coupling).

## 5. Classifier / vocabulary

Keep the physical ladder thresholds (`law.stellar` opacity-limit / deuterium /
brown-dwarf-desert / hydrogen-burning) — they are real. With seed-and-grow,
`:planetesimal` bodies become *actually* small, so the label stops being a
misnomer. If seeds can be sub-planetesimal (asteroid scale), consider whether the
`:planetesimal` bucket needs a finer floor label; do NOT rename the physical
ladder.

## 6. Implementation sketch

1. `law.planet-formation` (or `law.stellar`): a `condensation-seed-mass` fn
   (model §4) + constants.
2. Classifier / condensation site detection (`domain.stellar` classify path):
   where a parcel currently promotes `:nebula → :planetesimal`, instead emit a
   `spawn-request.condense` (seed spec at seed-mass) + a parcel mass debit, guarded
   by a one-shot per-site marker.
3. `materialize-lifecycle`: materialize condensation seeds (already generic over
   spawn-request.*; add the new marker to the list).
4. Grow: no new code — the seed is a resolved sink; M3 BHL grows it.
5. Registry: single-writer for the new spawn-request + debit channels; update
   `test/architecture_test.clj` expectations.

## 7. Tests (epistemic contracts)

- `condensation-conserves-mass`: seed mass debited exactly from parent parcel.
- `seed-mass-below-parcel`: a condensed seed is « one parcel (e.g. < 1 M⊕).
- `seed-grows-by-accretion`: a seed in a gas-rich zone grows over N ticks via M3
  (mass increases; conservation holds).
- `seeding-is-bounded`: N does not blow up — seeding gated to condensation sites,
  bounded seed count over a standard collapse run.
- Formation pipeline stays green: `dominant_star`, `formation_integration`,
  biogenesis path still produce a star + Earth-scale worlds (re-tune thresholds
  if the emergent scale shifts — the *intent* is unchanged).

## 8. Open questions

- Seed-mass model (a) vs (b) for the first landing — recommend (b) to validate,
  then (a).
- Should sub-planetesimal seeds (asteroid belt) persist indefinitely, or be
  consolidated/reaped below some count? (Physically they should persist and cause
  impacts — ties to Phase-1 mass-extinction modelling.)
- Exact float-precision guard: where to draw the "separate accumulator" line vs
  parcel-debit (§3) — measure ULP behaviour in a conservation test.
