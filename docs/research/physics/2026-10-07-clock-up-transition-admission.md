# A finite admission rule for increasing the pre-capture clock

(己, p=0.99) **Derived and proposed; not an accepted clock, physical safety certificate, or implementation authorization.** This is a new root research artifact for the existing PR13 specification. It leaves the published PR13 clock note, Truth worktrees, board, ledgers, source and active native run untouched. No project namespace, probe, test, JVM or native operation was executed. Source is the frozen `b395c4049718f7ce015ddf25fc0a192d97821373` production composition; the source pins below match the existing damped-profile preparation's `STATIC-CHECKS.json`.

(己, p=1.0) **Publication history:** the preceding execution boundary describes the original root analysis. The copy published at [revision `a1cfb18`](https://github.com/octave-commons/Truth/blob/a1cfb18fb2b128429c3c833e9164ff501293c241/docs/research/physics/2026-10-07-clock-up-transition-admission.md) has SHA-256 `00a35964a4637ebf7d36fddb7b85a2018504b740415293eb4d1473b3153c1c69` and preserves the original bytes. The current copy clarifies guarded Auto's upward-step minimum after planning review; it is no longer byte-identical to that archive. This wording correction runs no physics or native test.

## Recommendation

(己, p=0.97) Adopt **B = the player's currently selected D** as the named own-step allowance for *admitting an upward timestep request*. D is already a displacement-per-tick control in metres. Use two independent conditions on the actual pending control channel and the full ordinary Euler result. Reject a failing request with its reason; retain the previous effective step and ordinary physics rather than clearing, rescaling or replacing any influence. This resolves a finite transition decision without choosing an arbitrary timestep ratio or claiming capture safety.

(己, p=0.99) This is a player-selected eligibility rule, not a total physical risk budget. Cruise permits a very large step. Passing does not establish target-relative station keeping, gravitational accuracy, a bounded future trajectory, or 43 consecutive binding reads. Even the previous effective timestep may produce a large next step after a rejected request. Continuing at that timestep is preservation of existing simulation behavior, not a fallback certified by this rule.

## Exact input and finite decision

(己, p=0.99) For one identified next fold, let `h >= 1 s` be the proposed consumed step; `v` the Spark's input velocity in m/s; `a_c` its **already published** `accel-thrust` in m/s²; `A` the actual integrator's sum of every registered acceleration; and `J` its sum of raw velocity impulses in m/s. The proposed pure result has the conceptual signature:

`admit-up : FrozenEulerInput × ProposedSeconds × SelectedMetres -> Accept | Reject(reason, evidence)`.

(己, p=0.99) The [canonical ordinary advance](https://github.com/octave-commons/Truth/blob/b395c4049718f7ce015ddf25fc0a192d97821373/src/domain/integrator/kinematics.clj#L191-L198) computes, in its existing expression order:

```
v_plus = (v + h*A) + J
delta_x = h*v_plus
```

(己, p=0.97) Admit only when the supported-state checks below pass and both inequalities hold using finite computed results:

```
pending control kick:  h * norm(a_c) <= B/h       [m/s]
own Euler drift:       norm(delta_x) <= B         [m]
B = D > 0                                      [m]
```

(己, p=0.99) The first is algebraically `h²*norm(a_c) <= B`; an implementation must choose and test one finite evaluation order, reject nonfinite intermediate/results and avoid pretending alternate floating-point rearrangements have identical boundary behavior. The second includes inertial velocity, **all** consumed accelerations and **all** raw impulses. A small resultant drift alone must not hide an enormous pending player-control kick canceled by another channel. Conversely, these two tests do not constrain each individual non-control force, numerical integration error or future cancellation.

(己, p=0.99) `a_c` already combines the prior thrust and brake; current key-up state cannot recover or annul it. A clock change must not regenerate this channel using the new h. The [flight emitter](https://github.com/octave-commons/Truth/blob/b395c4049718f7ce015ddf25fc0a192d97821373/src/domain/player/flight.clj#L79-L107) writes `alpha*D*u/q² - alpha*v/q`, `q=max(1,h)`, for the *following* fold. For this proposed `[1,86400]` manual band, q equals h. Guarded Auto increases also require finite `h >= 1` and the same actual-channel tests; an upward request `0 < h < 1` is unsupported. A guarded Auto increase above 86400 seconds may be admitted by those tests because Auto has no manual cap. Never-manual Auto and equal/downward fractional requests retain the distinct legacy behavior specified in the [clock design](../../designs/pre-capture-clock.md#3-effective-fold-and-persistent-guarded-auto); these statements do not certify their motion.

(己, p=0.97) The first implementation should admit only an identified, live, ordinary-Euler Spark with finite x/v/D/h and finite registered channels, due on the actual consumed fold and without absorption packets targeting it. Missing channels retain the integrator's documented zero meaning; malformed or nonfinite present data is rejection. Compact-state routing, non-due LOD ambiguity, missing observer, stale world/revision/tick, absorption blending, or an unqualified physical branch produces an explicit unsupported-context reason, not a guessed prediction. This rejects the requested increase; it does not suppress the real integrator or erase the entity. A later lifecycle removal cannot turn this result into a promise that the observer survives publication.

(己, p=0.97) Bind the decision to the exact frozen world value/revision, observer identity, effective tick, h and selected D. Include pending `a_c`, summed A/J, resulting `v_plus`/`delta_x`, thresholds and reason in the decision evidence. Retention r and current input direction are useful context but must not substitute for the actual pending channel. Revalidate if a serial intent changes these inputs before the fold. Do not introduce an epsilon that silently enlarges D or silently choose a different h on rejection.

## Reuse the actual integrator, not a second predictor

(世, p=1.0) The [influence registry](https://github.com/octave-commons/Truth/blob/b395c4049718f7ce015ddf25fc0a192d97821373/src/domain/integrator/base.clj#L11-L44) includes gravity, pressure, Lorentz, observer, warp, dark matter and thrust accelerations; raw impulses are `dv-flare` and `dv-transfer`. The map path sums these through `sum-vec-influences`; the SoA path uses the same registered lists at [kinematics lines 597–603](https://github.com/octave-commons/Truth/blob/b395c4049718f7ce015ddf25fc0a192d97821373/src/domain/integrator/kinematics.clj#L597-L603). Both ordinary branches call the same existing Euler helper.

(世, p=1.0) The separate [SoA drift-prediction cache](https://github.com/octave-commons/Truth/blob/b395c4049718f7ce015ddf25fc0a192d97821373/src/domain/physics/cache/soa.clj#L12-L20) has literal source lists that omit `accel-thrust`, `accel-dark-matter` and `dv-transfer`. Its `px-pred` values are consequently **not** this rule's full consumed-state prediction. Do not use them, copy their list, or silently repair their distinct gravity-prediction semantics as part of clock admission.

(己, p=0.97) The smallest lawful implementation seam is to extract the existing ordinary advance into a portable pure helper, preserving arithmetic grouping and using it in the existing integrator and the admission law. The adapter should obtain influences from the canonical registry/reduction path, not maintain another force inventory. Expose the existing drift vector from that same computation if needed; subtracting two huge absolute positions to reconstruct a small drift loses precision. This is reuse of the single advancement law, not a new Spark writer or alternate simulation. Characterize map and SoA inputs/results before relying on their equivalence at this boundary.

(世, p=1.0) The [map cell](https://github.com/octave-commons/Truth/blob/b395c4049718f7ce015ddf25fc0a192d97821373/src/domain/integrator/kinematics.clj#L401-L431) and [SoA cell](https://github.com/octave-commons/Truth/blob/b395c4049718f7ce015ddf25fc0a192d97821373/src/domain/integrator/kinematics.clj#L515-L552) subtract the shared frame offset *after* the advance and then apply absorption blending. The all-row SoA loop retains historical non-due behavior whereas acceleration collection uses due indices; its public due-only docstring is insufficient authority. Restricting the first admission to a due, non-absorbing ordinary Spark avoids claiming those branches are covered.

(己, p=0.99) Bound the physical pre-recenter drift `delta_x`, not raw published `x_new - x_old`, which includes subtraction of the world COM. Validate finite coordinate/recenter output as well. A common frame subtraction cancels in same-world target-minus-Spark comparisons; it is not extra physical travel. Absorption is a separate position/velocity composition and cannot simply be left out of a supposedly complete prediction.

## Effective-fold and Auto policy

(世, p=1.0) [Physics fan-out](https://github.com/octave-commons/Truth/blob/b395c4049718f7ce015ddf25fc0a192d97821373/src/domain/genesis/tick.clj#L78-L100) builds systems and caches from one world and consumes the prior influence channels. [Clock publication](https://github.com/octave-commons/Truth/blob/b395c4049718f7ce015ddf25fc0a192d97821373/src/domain/genesis/tick.clj#L178-L216) accounts for the consumed input h, then publishes pacing's next h. A decision made before this fold emits the new pending channel is not automatically valid for the next fold.

(己, p=0.97) Resolve the requested clock at the existing serial pre-fold boundary, after relevant queued intents and against the actual next-fold state, **before** systems capture dt and before dt-dependent SoA prediction is built. The current COM spatial preparation does not depend on h; it may precede the decision. Use the single admitted h for all fan-out systems and `sim-time += h`. Preserve pacing-derived softening; the manual clock is not a softening control. Keep requested automatic h distinct from admitted consumed h so post-fold pacing cannot overwrite the guard. No cache built for a rejected proposed h may be used for the actual fold.

(己, p=0.97) Make mode behavior explicit rather than enabling the new test across every genesis step:

| Proposed state | Next request and result |
| --- | --- |
| Auto, never manually overridden in this world | Preserve the existing automatic pacing/slip path exactly. No new D-based eligibility is imposed on ordinary genesis. This preserves compatibility, not a claim that legacy Auto transitions are safe. |
| Manual cap active | Form the requested h from the automatic proposal and cap, as in the existing clock proposal. Any increase over the last actually consumed h requires the finite admission above. A rejection retains the previous effective h and reports the blocked request. A decrease remains a direct lower-clock request under the original proposal, without an own-motion certificate. |
| Explicit return from Manual to Auto | Remove the manual cap, enter **guarded Auto**, and submit Auto's requested h to the same upward decision. Later automatic/slip increases remain guarded; the first accepted return does not disable this protection. Rejected requests visibly leave Auto awaiting eligibility at the retained effective h. |
| Gate removes the manual control, or another clock owner takes over | Removing a widget must not erase delayed-force transition state. Proposed default is guarded Auto until an explicitly admitted successor clock takes responsibility on an identified fold. This is a new handoff proposal for review, not evidence that the current data-only `time-lock` already implements it. |

(己, p=0.99) The guard is about changes of consumed timestep, not an invariant checked on every future same-h fold. Future forces, raw impulses, input or D changes can exceed the allowance without any clock increase. A continuous motion limiter would be a different feature. No live native result currently qualifies any of these new modes.

## Why neither focus margin nor monotonic slowing completes the claim

(己, p=0.99) The one-AU binding radius is a relative attention test. An available margin `M = R - norm(x_target - focus)` from one frozen input does not bound the target's next compact trajectory, future focus offsets, disturbances or 42 intervening intervals. Replacing B with `min(D, f*M)` requires an explicit allocation f and a reference/focus displacement budget; no existing calibration chooses f. Therefore **use B=D for this first rule and make no capture guarantee**. A later margin mode would need separately accepted semantics. Do not approximate a compact target's displacement by its final velocity times h.

(己, p=0.99) A monotone-nonincreasing cap avoids multiplying a small-h pending brake by a larger h, but is not a general own-drift bound. In one dimension, take positive D/H, `v=10D/H`, `A=-10D/H²`, `J=0`: at h=H the Euler drift is zero, while at h=H/2 it is `2.5D`. This is a symbolic cancellation counterexample, not a claim that the active game occupies this state. Lowering h attenuates the old control kick; full drift need not be monotone in h.

(己, p=0.97) If guarded Auto cannot fit an admitted implementation slice, the honest smaller alternative is a manual approach mode that rejects *all* upward requests, including Auto return, with a visible reason until an explicit successor handoff. It must advertise that limitation; it cannot label itself unrestricted Auto or silently restore the legacy large h. This is a reduced feature, not the recommended final behavior and not a proof of safe downward changes.

## Proposed small implementation breakdown, not new cards

(己, p=0.97) The current Incoming 3-point owner is specification work. The following are proposed implementation boundaries to review through the existing board process, not permission inferred from that status:

1. **3 points — canonical ordinary advance plus pure upward admission.** Extract portable arithmetic without changing the production trajectory; reuse the registry and existing Euler path; define finite inputs, B=D, supported-state exclusions and reasoned result. Characterize absent channels, every registered acceleration/raw impulse, held/released pending channel, inclusive finite boundaries, overflow/nonfinite values and cancellation. Verify the real map/SoA ordinary fold agrees with the reused computation and recentering does not consume B. No UI, mode state or new physical writer.
2. **5 points — effective-fold clock state and guarded Auto.** Integrate manual cap/request/consumed h at the serial boundary, exact first changed-fold pending-channel use, shared fan-out h and clock accounting, rejection preservation, subsequent Auto/slip increases and explicit handoff behavior. Tests must show never-manual Auto remains unchanged and that a later Auto increase cannot bypass the guard. Do not bundle a committed local-neighborhood clock or target-relative controller; if its handoff cannot be expressed without those, keep guarded Auto and split that successor work.
3. **3 points — ordinary clock UI and visible decision evidence.** Expose the already proposed cap band, Auto/guarded state, effective h and rejection reason through existing intents. Preserve D/r, physical columns and prior channels. Native verification belongs to the existing measurement lane after qualification: an accepted and a rejected ordinary upward request with before/consumed/published evidence, not an invented capture success. No automatic D change or hidden channel clearing.

(己, p=0.99) These tests are proposed, not executed. This source analysis closes the choice of a finite *own-step* admission budget; it does not close residence dynamics, biological clock calibration, whole-world integration accuracy or the product decision accepting guarded Auto. The useful next decision is whether to adopt this precise proposal in the existing specification, followed by normal scoped admission.

## Source pin record

(世, p=1.0) Files were read without changing them. Production hashes also occur in the frozen damped-profile source inventory. The root envelope and PR13 envelope are separate evidence objects; the latter was published by root as PR13 revision `b2a0181` during this task and is not modified here.

| Input | SHA-256 |
| --- | --- |
| Root `2026-10-07-pre-capture-clock-envelope.md` | `01e2472e171508f16210eab12741e341394cee5d1c674b9693e4c52be1f9a4f1` |
| PR13 `docs/research/physics/2026-10-07-pre-capture-clock-envelope.md` | `0a281f43bf248a2b9732cc9becd4ad51a9c7e6e2f8ef5a89a53cb52ee14efb78` |
| `src/domain/integrator/base.clj` | `6b490386437788b5e6b7eae238900c25d93220082d7f9f11af3d7f01091691e6` |
| `src/domain/integrator/kinematics.clj` | `d2ff250f0b1c7df1b6e05730161152d1a2bf1dd970fd1a306b3d4f5662161d61` |
| `src/domain/player/flight.clj` | `005b6f5bcb33093e93a76b26e4663af71358cd7535dc7022ff5c74bbe872a5b2` |
| `src/domain/physics/cache/soa.clj` | `8961060a036b1273e8eed5cfa734a63b82b8f8b167e719d4f49e29d0c1dece94` |
| `src/domain/genesis/tick.clj` | `d2159f6416cab06048c0b3876e495578c1f61e2b4ac4a70d59eaa49113f2e4ee` |
| `src/domain/spatial/index.clj` | `280941b79defe025dbdd7c1478940b956c1e0dc9bd270fa4c66f5170f1a94701` |
| `src/domain/pacing.clj` | `bdda144e92b23d6b82c114e3dfd908b946cebfbe713a05699d839f5b7e020279` |
| `src/domain/narrowing.clj` | `19ff9b4f0b9d73b327f6602756ef6b8c10d79fa53838572ef2c964bc5c064d22` |
| `src/domain/integrator.clj` | `be47c760ea53e0fad1645ab21693be867a82430deef47981b7b71158abfbda88` |
