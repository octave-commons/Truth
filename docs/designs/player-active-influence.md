# Player influence becomes deliberate and paid

**Status:** Proposed design refinement, 2026-10-07; no implementation admission.
**Owner:** [`remove-passive-halo-invert-influence`](../../kanban/tasks/remove-passive-halo-invert-influence.md), currently Todo / 2 points.
**Source baseline:** `b395c4049718f7ce015ddf25fc0a192d97821373`.

The owner card's **Grounded integration**, **Done when**, and **Dependencies**
sections are superseded historical instructions. Use this document for the
**proposed scope and qualification**; it does not grant implementation approval.
The card remains Todo pending normal planning, sizing and admission. Its
canonical top-of-card description carries the same notice while its historical
body is preserved.

## 1. Decision and grounding

Keep both outcomes of the July 23 owner decision: looking at matter no longer
creates the free focus-centered gravitational halo, and deliberately placed paid
wells become stronger. The Spark remains a physical body moved by gravity and
thrust. Its ordinary mass still gravitates; this proposal removes an additional
attention-controlled field, not the body's physical gravity.

The decision is preserved in [`receipts.edn`](../../receipts.edn), the
2026-07-23T13:25:37.235857409Z entry, and commit
`452503538a0558c17b6d8092176a99d50fdbe627`. That commit authored this card directly
as Todo. The historical status and estimate do not prove present Ready admission.
The existing [process](../../PROCESS.md#grounding-research--design--task) requires
the missing research → design → task link and a reviewed, sized implementation.

Grounding and its limits:

- [July 5 influence investigation](../notes/2026.07.05-dark-halo-influence.md)
  records why fixed velocity kicks were replaced by the Plummer field, the old
  defaults, and a historical gathering observation. It predates the removal
  decision and does not calibrate the proposed factor below.
- [The accompanying investigation](../notes/2026.07.05.16.41.22.md) preserves the
  earlier player report and reasoning. Its sweeping no-ejection language is not
  a guarantee for a decaying, capped, discretely integrated field.
- [Plummer law](../../src/law/stellar/orbital/dynamics.clj) and
  [law tests](../../test/law/stellar_halo_test.clj) supply the implemented force
  shape; [intervention tests](../../test/domain/intervention_test.clj) and
  [lifecycle tests](../../test/domain/warp_lifecycle_test.clj) pin payment, field,
  decay, removal, and the final carried Jacobi kick.
- [Flight design §7](spark-flight-and-camera.md#7-control-doctrine-corrected-2026-07-23)
  retains 15-quanta ability slots. [Native foundation evidence](../notes/2026-10-06-playable-foundation-verification.md#observed-live-interaction)
  proves an ordinary paid repulsor input reached the force channel; it expressly
  does not prove comparative potency or gathering.

## 2. Proposed defaults and unchanged force law

Select **1.0 instead of 0.5** for `default-well-mass-factor`. This is a tunable
gameplay-model choice: one fresh full-strength paid field represents one seeded
cloud mass for this force calculation. It is neither empirical calibration nor
an exact compensation for the removed halo, whose strength depended on
coherence, intensity, radius, and motion.

| Parameter | Proposal |
|---|---|
| Default well/repulsor mass factor | 0.5 → **1.0** |
| Agency cost per action | **15**, unchanged |
| Plummer scale radius `a` | **4e15 metres**, unchanged |
| Lifetime | **600 simulation ticks**, unchanged; not 600 seconds |
| Initial strength / decay | **1.0**, linear remaining fraction, unchanged |
| Reach | `r < 3a`, unchanged; exact centre and cutoff produce no force |
| Summed per-tick velocity-change cap | Existing **1 × cloud virial speed**, unchanged |
| Progress/resonance/coherence multipliers | None added |

For a valid placed field at tick `t`, retain the existing implementation:

```text
d(t) = max(0, 1 - (t - born_tick) / ttl)
M_effective = k × strength × d(t) × M_cloud
g(r) = G × M_effective × r / (r² + a²)^(3/2),  0 < r < 3a
```

The vector points toward a well and away from a repulsor. `k` is the current
world well-mass setting, not real ECS mass. Both kinds share it, so **both become
stronger**. Thermal actions keep their existing cost/radius/TTL and heat law.
The default change is applied through the existing fresh-world initialization;
an existing world's explicit `:genesis/well-mass-factor` remains authoritative.
There is no retroactive world migration or hot-reload acceptance claim.

The system sums active fields at production drift-predicted body positions, then
caps the vector magnitude at `dv-cap / max(1, sim-dt)`. At identical uncapped
inputs, changing only `k` from 0.5 to 1.0 doubles acceleration. At saturation it
does not; opposing fields can cancel, and later trajectories need not remain
comparable. Preserve linear decay, expiry ordering, explicit stale-contribution
removal, and the prior channel's final Jacobi kick. No new clock or force writer.

These forces create no real material mass. They are external prescribed fields,
not a reciprocal source-body interaction: this design does not assert total
momentum or energy conservation, a guaranteed bound orbit, or zero ejections.
Decay and discrete integration make an unchanged spatial centre insufficient
for an exact conservative-dynamics claim. Keep adverse observations visible.

## 3. Complete removal boundary

Remove the passive implementation, not merely set its default to zero. The old
card's claim of one consumer is stale at this baseline. The implementation must
cover these existing seams without deleting shared paid-field primitives:

| Seam | Required change |
|---|---|
| [`player/influence.clj`](../../src/domain/player/influence.clj) | Retire passive factor, halo mass, observer acceleration and emitter. Retain `influence-reference`, velocity cap and reach factor used by paid fields. |
| [`player.clj`](../../src/domain/player.clj) | Retire passive facade exports; retain shared paid helpers and all physical/focus APIs. |
| [`genesis/systems.clj`](../../src/domain/genesis/systems.clj), [`registry.clj`](../../src/domain/ecs/registry.clj) | Remove emitter registration and integrator read of its component. Keep dark matter, warp, thrust, and physical gravity. |
| [`components.clj`](../../src/domain/ecs/components.clj), [`integrator/base.clj`](../../src/domain/integrator/base.clj) | Retire passive component definition and velocity accumulator entry. |
| [`physics/cache/soa.clj`](../../src/domain/physics/cache/soa.clj), [`orbital/system.clj`](../../src/domain/orbital/system.clj) | Remove the same channel from prediction and the orbital acceleration list; no predictor/integrator policy rewrite. |
| [`genesis/bootstrap.clj`](../../src/domain/genesis/bootstrap.clj) | Stop seeding the passive knob; new worlds receive the proposed paid default through the existing constant. |
| [`menu/widgets.clj`](../../src/infra/menu/widgets.clj), [`menu/panels.clj`](../../src/infra/menu/panels.clj) | Remove passive mass stepper/readout and its misleading focus-derived force reach. Preserve attention radius/intensity and existing paid controls; label retained reach/cap by their actual paid meaning. No HUD redesign. |
| Current docs and tests | Replace assertions that require the retired field, update active descriptions, preserve shared Plummer/payment/cap contracts and immutable historical notes/receipts. |

Existing serialized or retained diagnostic maps may contain the old keyword or
knob. No active consumer may apply it, including the predictor. Do not add a save
migration, repair framework, compatibility emitter, or new per-tick cleanup scan.
Fresh native runs qualify the new defaults. Existing load-time Spark repair is
not a proven general persistence route.

The Spark spring was already deleted by `b0b3b4a`; do not remove the distinct
attention-binding or camera-tether mechanisms. The dark-matter dependency is
implemented and wired, with its own mass/scale defaults unchanged. Its Review
status and historically deferred tuning are not a completed native acceptance.
Coherence, binding, attention resolution, paid placement aim, flight assist,
Gate progression, and chemistry remain outside this change. In particular,
attention may still affect legitimate non-halo systems; this is not a promise
that every full-world outcome is independent of focus.

## 4. Verification contract before implementation

Write failing behavioral tests for the changed contract before the production
edit; retain existing tests for unchanged behavior. Qualification must cover:

1. **No free field through the real path.** A fresh production world with no
   intervention has no passive emitter/channel consumption. Change focus
   position/radius/intensity at fixed physical state and verify there is no
   focus-centered force. Also supply a historical passive component in a test
   and verify both map/SoA prediction and integration ignore it. Keep separate
   positive controls that real physical gravity, dark matter, and paid warp act.
   Do not demand equality of unrelated attention/economy/LOD state.
2. **Higher paid potency with fixed economics.** At matched positive radius,
   age, strength and reference mass, test the selected 1.0 default and 2:1
   uncapped acceleration against the old 0.5 calculation, for both signs.
   Cover centre, just-inside/exact cutoff, half-life, expiry, summed saturation
   and cancellation. Preserve affordability, exactly one debit/placement per
   accepted domain action, no-observer/no-funds no-op, and explicit knob/option
   overrides. This does not redesign native key-repeat handling.
3. **Uniform lifecycle and presentation.** Run real emitter/fold/integrator
   expiry/departure tests including the allowed final delayed kick; preserve
   independent force channels and unchanged thermal behavior. Check the actual
   menu lacks the passive control and reports paid values accurately. No write
   conflicts, architecture/full-suite/strict gates and a before/after hot-path
   cost check under the existing performance process are required.

Use controlled numerical worlds for numerical contracts, and label them as
tests. They are not evidence of player-visible natural gathering.

## 5. Ordinary native acceptance and evidence limits

Use a fresh ordinary `clojure -M:demo serve` world on a separately owned bounded
runtime. Earn agency through its existing events. With no intervention, observe
attention changes without the retired field; then use the existing **G** action
at an actual natural material region. Preserve world/process/source identity,
seed/options, ticks and simulation times, actual placement/expiry, field settings,
earned resource reads, and full native frames. No body injection, time jump,
fixture scene, scripted relocation, or synthetic agency award.

Record gather-radius occupancy/mass and identified-body distances before,
during, and after the paid well. Distances must use the actual production well
centre and each observed world's frame; bodies that merge, transfer, or disappear
must be recorded rather than silently counted as inward motion. Record cap
saturation and error/ejection observations if measurable. Agency can accrue
between reads, so a later balance alone cannot prove the debit amount.

Show visible material convergence while the paid field is active; mere nonempty
`accel.warp` is insufficient. Native trajectories alone cannot isolate potency
from natural collapse or other forces. The matched-input numerical comparison
establishes stronger specified force; the natural G run establishes the visible
gathering acceptance. Do not claim a causal collapse-rate improvement without a
separately controlled comparison. If the bounded run lacks funds/material or
fails to show convergence, preserve that result and leave acceptance open. No
claim of approach, capture, sculpt, embodiment, or Gate follows from this proof.

## 6. Size and admission

Propose **5 points for the complete implementation and qualification**, replacing
the old two-point assumption only through the normal planning process. Although
the physics change is small, complete channel retirement touches multiple
consumers, menu semantics, old expectations, and natural runtime evidence. A
single cohesive slice should retain both outcomes rather than finish after
removal alone. No child split is proposed before this bounded scope is reviewed;
if qualification exposes unrelated formation or input defects, record their
actual owners and re-estimate rather than broaden or claim completion.

This document selects the proposed default and verification boundary, not an
implementation-ready state. Root's [scope receipt](../../.ημ/diagnostics/active-influence-plan/root-scope.json)
authorizes design work only. The existing task remains Todo / 2 in the board
until lawful planning/review decisions change it. No production/test change,
new numerical result, native tuning, or approval is supplied by this document.
