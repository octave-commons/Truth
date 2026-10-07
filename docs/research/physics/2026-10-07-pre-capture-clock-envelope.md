# Pre-capture clock envelope from closed ordinary-flight evidence

2026-10-07. Status: **draft analysis and proposed design boundary**.
Owner: `flight-local-reference-assist-spec` (Incoming, 3).
License: GPL-3.0-or-later.

(己, p=0.99) **Offline measurement analysis and a proposed review boundary, not an accepted clock or controller.** The existing owner is `flight-local-reference-assist-spec` (Incoming, 3 points), parent `flight-assist-damping-and-toggle`. This note uses closed ordinary native attempts 02/03 and production source `b395c4049718f7ce015ddf25fc0a192d97821373`. It creates no card, implementation, test, synthetic world, runtime hook, or new native result. Root authorized this refinement of the existing three-point specification; no source admission or planning convergence is inferred. Arithmetic below was evaluated with Python's standard `math`/`json`/`re` over existing text/JSON; no diagnostic module was imported.

## Concrete result

(己, p=0.99) The smallest useful next decision is an explicit **pre-capture maximum simulation step**, applied through the existing world/intent/tick path, with a separately checked delayed-force transition. A proposed capture adjustment band of **1 second through 1 day per tick** is dimensionally useful: one day would put the measured kilometre-per-second motion inside a meaningful fraction of the one-AU radius; one second is the current flight emitter's exact lower normalization boundary. Neither endpoint is a universal capture guarantee or a biological/physical calibration.

(己, p=0.99) Keep the proposal distinct from the future celestial-reference controller. It can first make the existing inertial-brake plant temporally resolvable. It does not provide velocity matching, target acquisition, automatic proximity slowdown, interception, or the post-commitment neighborhood clock. A smaller global step also slows the rest of the world; that is an explicit player-chosen time override, not an automatic global shortest-orbit rule.

## Authority and executable seams

- (世, p=1.0) [Living UX](https://github.com/octave-commons/Truth/blob/b395c4049718f7ce015ddf25fc0a192d97821373/docs/designs/ux-architecture.md#L96-L110) specifies Auto and Manual rate override during Phases 0–5, removed at Gate discovery. It supplies neither range nor precedence. The current [View panel](https://github.com/octave-commons/Truth/blob/b395c4049718f7ce015ddf25fc0a192d97821373/src/infra/menu/panels.clj#L173-L190) exposes camera settings, not that clock.
- (世, p=1.0) [Pacing](https://github.com/octave-commons/Truth/blob/b395c4049718f7ce015ddf25fc0a192d97821373/src/domain/pacing.clj#L42-L48) floors automatic `h` at `1e7` seconds (115.74 days). [Time slip](https://github.com/octave-commons/Truth/blob/b395c4049718f7ce015ddf25fc0a192d97821373/src/domain/pacing.clj#L166-L188) can enlarge it. Its comments claiming fixed actual 60 Hz are stale; the [sleeping simulation loop](https://github.com/octave-commons/Truth/blob/b395c4049718f7ce015ddf25fc0a192d97821373/src/infra/dev/window/loop.clj#L126-L152) does not guarantee that throughput.
- (世, p=1.0) [Tick fan-out](https://github.com/octave-commons/Truth/blob/b395c4049718f7ce015ddf25fc0a192d97821373/src/domain/genesis/tick.clj#L78-L100) consumes the prior force channels. `advance-simulation-clock` at lines 175–204 counts the **consumed** input `h`, then installs the next automatic `h`. A new setting needs an exact effective-fold rule; changing a UI value does not by itself change that pipeline.
- (世, p=1.0) [Flight](https://github.com/octave-commons/Truth/blob/b395c4049718f7ce015ddf25fc0a192d97821373/src/domain/player/flight.clj#L79-L107) emits `a = alpha D u/q^2 - alpha v/q`, `q=max(1,h)`, `alpha=1-r`. Position/velocity remain integrator-owned. Current range presets are Cruise `3e14` and Fine `1e7` m/tick; the current menu exposes retention `.80`–`.999`.
- (世, p=1.0) [Compact integration](https://github.com/octave-commons/Truth/blob/b395c4049718f7ce015ddf25fc0a192d97821373/src/domain/integrator/kinematics.clj#L255-L292) returns an actual composed parent-relative position, not `v_final*h`. Both compact and ordinary paths subtract the same frame offset at lines 401–427. Spark is outside compact eligibility at lines 108–117.
- (世, p=1.0) [Binding](https://github.com/octave-commons/Truth/blob/b395c4049718f7ce015ddf25fc0a192d97821373/src/domain/narrowing.clj#L188-L229) reads prepared focus and candidate positions from the frozen input; the [commitment consumer](https://github.com/octave-commons/Truth/blob/b395c4049718f7ce015ddf25fc0a192d97821373/src/domain/narrowing.clj#L323-L379) reads previous binding. [Time-lock](https://github.com/octave-commons/Truth/blob/b395c4049718f7ce015ddf25fc0a192d97821373/src/law/narrowing.clj#L74-L90) is still a data hook, not clock actuation.

(己, p=0.99) PR13's [existing feasibility note](https://github.com/octave-commons/Truth/blob/0fce01b467f100426cae6f3c3da4e8af79e74322/docs/research/physics/local-reference-flight-assist-feasibility.md) already records the relevant delayed-state/reference-displacement model and historical time-control authority. This note narrows its missing envelope; it does not replace its unaccepted status.

## Closed endpoint evidence

(己, p=0.99) Let `rho=x_target-x_spark`. Comparing `rho` cancels the common frame recentering. The following are **chords between actual published endpoints**, not complete paths or binding-consumer observations. `Δt_sim` is the saved sim-time difference, not wall time or an assumed tick rate. Published h is the next step in each saved world, not a record of every intervening consumed step; the table uses elapsed sim time for its chord-rate arithmetic. All rows concern the fixed natural target 1010; the identifiers belong to separate worlds in attempts 02 and 03.

| Closed interval | Published h, s | Ticks | Relative chord, AU | Chord / tick, AU | Chord / elapsed sim time, m/s | Endpoint relative speed, m/s | Endpoint speed × h, AU |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| 02: pre-aim 4383→4476 | 4.138029443e9 | 93 | 1245.003679 | 13.387136 | 483.971204 | 4907.637812 | 135.750259 |
| 02: released coasts 5803→5914 | 4.138029443e9 | 111 | 1440.308671 | 12.975754 | 469.098919 | 5149.190559 | 142.431854 |
| 02: released coasts 6396→6473 | 4.138029443e9 | 77 | 910.279818 | 11.821816 | 427.381801 | 4943.603276 | 136.745101 |
| 03: final aim 5056→first cadence read 5105 | 3.065696697e9 | 49 | 2452.353597 | 50.048033 | 2442.211297 | 2442.097930 | 50.045709 |

(己, p=1.0) Inputs are the attempt-02 client snapshots `019/020`, `024/025`, and `027/028`; attempt-03 `aim-02-1010/result.json`, the tick-5056 marker in `snapshot-messages.edn`, and `bounded-cadence-01/001-inspect-snapshot.txt`. Their immutable routes and hashes are recorded below. These selected snapshots are direct files in the published evidence; the lossless auxiliary maps preserve the complete runs and identify all packed sibling inputs.

(己, p=0.99) In attempt 02 the endpoint velocity proxy is roughly 10–12 times the actual multi-tick chord rate. In attempt 03 they happen to agree. Neither relationship proves a universal bound: compact orbital curvature, sampling, Spark motion and other forces remain. Do not infer a target's production displacement by multiplying a saved velocity by `h`, nor use these long-interval chord averages as a maximum speed. No row measured a near-planet one-AU residence window. Attempt 03 stopped before any W pulse; its final aim error was 0.09186°, then the later read failed the 0.5° guard at 0.60317°. That demonstrates changing geometry during the handoff, not impossible flight.

## Binding and a conditional clock ceiling

(己, p=1.0) At the source defaults, sustained intensity is at least `.5`, overlap radius `R=149597870700 m`, gain `.02`, capture threshold `.85`. With zero prior binding and uninterrupted overlap, **43 qualifying reads** produce `.86`: there are 42 intervals between those reads. The next fold's commitment consumer sees that value. Keeping the physical body inside R even at publication of commitment is the stronger condition covering **44 folds from the first zero-binding snapshot**. The current consumer does not recheck geometric overlap when it consumes the old threshold; a test must not silently strengthen or weaken that policy. Prior binding, gaps, other focused worlds and sticky decay require the actual recurrence, not the simple 43-read shortcut.

(己, p=0.99) A sufficient conditional residence screen is

`|rho_0| + B_spark + B_reference + B_focus + B_disturbance <= R`.

The budgets must cover every relevant input snapshot, not only the final endpoint. With a justified uniform relative-displacement rate bound `V_bound`, an available motion margin `M`, and N intervals, the simplified constant-step condition is `h <= M/(N V_bound)`. It is an engineering screen, not a calibrated law. For varying steps use `V_bound * Σh_n <= M`. For a curved production reference, replace the proxy with its actual composed displacement history and a bound on unsampled steps. Frame offsets, LOD due/skipped steps and outer kicks must be accounted for.

| Illustrative bound, not a measured maximum | 42 one-day intervals, AU | h ceiling for 0.1 AU / 42 intervals | 42 one-second intervals, AU |
| --- | ---: | ---: | ---: |
| 2500 m/s | .0606426 | 142474 s = 1.6490 days | 7.01882e-7 |
| 5500 m/s | .133414 | 64761 s = .74955 days | 1.54414e-6 |
| 10000 m/s | .242570 | 35619 s = .41225 days | 2.80753e-6 |
| 50000 m/s | 1.212851 | 7124 s = .08245 days | 1.40376e-5 |

(己, p=0.99) Thus **a one-day minimum is too restrictive to guarantee a 0.1-AU allowance at even the observed ~5.15 km/s endpoint scale**, but one day could fit a larger actual margin: at 5500 m/s, 44 days give `.139767 AU`. The same 44-interval 0.1-AU screen gives `h<=61817 s` (17.17 hours). A one-second option comfortably resolves these illustrative rates; it is not evidence that one second is required. All comparisons assume the stated bound persists after changing h; the closed runs did not test that.

## Inertial damping, finite flight scale and timestep transition

(己, p=0.99) At constant `h>=1`, absent other forces, `v_(n+1)=v_n-alpha*v_(n-1)+alpha*D*u_(n-1)/h`. Full held input converges to `v*h=D`. Releasing from settled input travels `rD/alpha` in the limiting model. For Fine D=1e7, that tail is `.00026738/.00216135/.06677903 AU` at r=.80/.97/.999. These are conditional settled-state results, not bounds on arbitrary inherited velocity or gravity.

(己, p=0.99) A more useful released transition includes the actual pending acceleration. Start a constant-h coast with velocity `v0` and already published thrust/brake `a_prev`; then `v1=v0+h*a_prev`. With no later input or external force, the total **net** displacement is `h*(r*v0+h*a_prev)/alpha`. For the exposed r range (real positive recurrence roots), a conservative path-length bound is `h*r*|v0|/alpha + h^2*|a_prev|/alpha`. This follows from the nonnegative impulse responses and triangle inequality; net cancellation alone is not a path bound. External acceleration/impulses require an additional justified budget. The closed snapshots omit `c/accel-thrust` and the full consumed-force sum, so that transition bound cannot be fully evaluated from them.

(己, p=0.99) Lowering h with fixed D is **not** a physical-speed-preserving operation. Fine's terminal speeds are `.0024166 m/s` at h=4.138e9, `115.74 m/s` at one day, and `1e7 m/s` at one second. At r=.97 its commanded acceleration rises from `1.752e-14` to `4.019e-5` to `300000 m/s²`. A current D=3.75e13 or Cruise D=3e14 retains its enormous per-tick displacement at every smaller h. Therefore clock-only acceptance must not imply unchanged physical acceleration, a speed limit, or fine approach with Cruise still selected. Choosing Fine does not erase existing momentum. No stored velocity, force channel or D is to be silently rescaled by a clock setting.

(己, p=0.99) The immediate **warp-down** kick is `h_new*a_prev`, so the old large-h flight command is attenuated; position still advances using the retained physical velocity. The dangerous reverse case survives release of all keys: a brake emitted at h_old is consumed at h_new with coefficient `alpha*h_new/h_old`. Returning from one day to the observed 4.138e9 s gives coefficient `1436.816`; from one second, `1.24140883e8`. An automatic return or a time-slip increase can therefore reverse/amplify motion if blindly applied. Keys-up, Fine selected, or one settled snapshot alone is not a transition proof. Other pending force channels also need honest coverage; this note derives only flight's special dt-normalized term.

## Recommended decisions to submit to the existing specification

1. (己, p=0.97) Define the manual value as a **maximum simulation seconds per tick**, not guaranteed sim-seconds per real second. For a first bounded capture band propose `[1,86400]` seconds, with Auto outside the manual selection. Preserve the exact current Auto branch when no manual setting exists. This is a concrete candidate range for review, not a chosen production default.
2. (己, p=0.97) Resolve precedence as `h_requested = min(h_auto_after_slip, h_manual_cap)` while manual override is admitted. Apply the cap after the automatic floor/slip calculations; otherwise current min1e7 silently defeats the feature. Compute softening from the original physical cloud policy, never from the user's smaller clock. A separate safe-transition result may choose an even smaller h or reject the requested transition, visibly; it may not enlarge the cap. The existing post-commitment/local-time owner remains separate, and Gate discovery disables the manual override as designed.
3. (己, p=0.97) Give every change an identified effective world/tick boundary and use that single h for all systems and elapsed-time accounting. The next-tick pacing publication must retain the manual cap. The first changed fold still consumes the old channel: retain its identity/value and test the actual kick. Do not introduce a special Spark integration path or recompute its old command after the fact.
4. (己, p=0.97) Keep the existing D/r controls explicit. Use Fine plus an actual residual-velocity/pending-force budget for the first capture claim. Do not automatically change D, suppress momentum, or equate a settings value with safe motion. An unsupported/unsafe request must return a reason rather than invent a physical clamp. A reviewed up-transition admissibility rule remains necessary before an ordinary Auto-return implementation: at minimum it must constrain the actual pending kick and subsequent displacement against named finite budgets. An arbitrary ratio such as 2 is not itself a switched-system stability proof.

(己, p=0.99) Items 1–3 are small, testable clock decisions. Item 4 is the remaining finite transition contract, now expressed in actual state variables rather than “needs more timing research.” It should be resolved in the existing PR13 specification before claiming implementation readiness. If that work and user control cannot honestly fit the current design owner, an implementation split belongs to normal reviewed breakdown, not this diagnostic note.

## Exact later acceptance matrix

(己, p=0.99) These are proposed tests to write after design admission, not tests executed here:

- Named pure validators reject nonfinite/zero/negative/fractional-below-1 h, malformed cap/mode and invalid finite budgets without changing the prior world. Explicit 1 s/1 day endpoints and absent Auto are covered.
- Pure precedence examples prove a manual cap beats auto's min1e7 and time slip; no cap reproduces the old Auto result; auto-derived softening is identical. Gate disable and post-commit policy delegation remain explicit separate cases.
- Boundary traces prove the exact consumed h, `sim-time += h`, effective tick and next-h publication; all fan-out systems share the same frozen choice. No position/velocity/mass/spin writer is added.
- Analytic fixed-h released/held laws cover r=.80/.97/.999, finite pending channels, full Fine/Cruise range and endpoint h values. Verify the response and path bound, not implementation call counts. A fixed-D test should correctly show invariant equilibrium displacement but **changed** speed/acceleration.
- Change h downward/upward while input is held, released with residual v, and released with pending brake. Preserve v/channel exactly at intent application, then assert the actual delayed kick or explicit rejected/limited transition. Auto-return, slip entry/exit and rapid toggles must use the same policy. No test may clear the prior channel to obtain a pass.
- Feed the existing binding recurrence qualifying/missed reads, nonzero initial b, other focused worlds and threshold .85. Protect 43 zero-start qualifying reads and the later commitment fold independently. Distinguish threshold acceptance from the stronger continuous-overlap-through-publication claim.
- Reference evidence uses the real prepared SoA/fold path: same entity/world identity, input and output positions, common frame shift, consumed h, pending thrust/all-force sum, due/skip and actual compact/Euler result. Protect translation/recenter invariance and never equate compact displacement with `v_final*h` by construction. An ordinary native acceptance later records each actual binding consumer input near the target; published sparse endpoints are insufficient.

(己, p=1.0) No test, world, clock setting, native keypress or implementation was executed in preparing this analysis. This refinement changes documentation and canonical planning provenance only. Existing PR13 remains unadmitted, and existing closed runs remain unchanged.

## Immutable evidence routes and promotion boundary

(世, p=1.0) Closed attempt 02 is pinned to PR56 revision
`ae5444b63e30207838f5c31f28138cb98d0027f0`; closed attempt 03 is pinned to
`93dc5b2bc0c734e5d8c311ac004035581db885b4` on the separately published
`codex/truth-damped-flight` lineage. Later preparation does not rewrite these
observations. The selected bytes were checked against these Git objects, not
against a mutable branch URL. No media or full journals are duplicated here.

- [Attempt 02: attempt-02-closure-audit.md](https://github.com/octave-commons/Truth/blob/ae5444b63e30207838f5c31f28138cb98d0027f0/.%CE%B7%CE%BC/diagnostics/bounded-look-200/attempt-02-closure-audit.md).
- [Attempt 02: attempt-02-AUXILIARY-MAP.json](https://github.com/octave-commons/Truth/blob/ae5444b63e30207838f5c31f28138cb98d0027f0/.%CE%B7%CE%BC/diagnostics/bounded-look-200/attempt-02-AUXILIARY-MAP.json).
- [Attempt 03: attempt-03-audit.md](https://github.com/octave-commons/Truth/blob/93dc5b2bc0c734e5d8c311ac004035581db885b4/.%CE%B7%CE%BC/diagnostics/bounded-flight-cadence/attempt-03-audit.md).
- [Attempt 03: attempt-03-AUXILIARY-MAP.json](https://github.com/octave-commons/Truth/blob/93dc5b2bc0c734e5d8c311ac004035581db885b4/.%CE%B7%CE%BC/diagnostics/bounded-flight-cadence/attempt-03-AUXILIARY-MAP.json).

| Selected input | Immutable file | SHA256 |
| --- | --- | --- |
| 02 / 019-coast-before-aim-snapshot.txt | [Pinned input](https://github.com/octave-commons/Truth/blob/ae5444b63e30207838f5c31f28138cb98d0027f0/.%CE%B7%CE%BC/diagnostics/bounded-look-200/attempt-02-clients/019-coast-before-aim-snapshot.txt) | `7ea1078c1c14b00ffff46ee1b4bb64dccaff67671695c6f35b5ad29cc31d601b` |
| 02 / 020-coast-before-aim-snapshot.txt | [Pinned input](https://github.com/octave-commons/Truth/blob/ae5444b63e30207838f5c31f28138cb98d0027f0/.%CE%B7%CE%BC/diagnostics/bounded-look-200/attempt-02-clients/020-coast-before-aim-snapshot.txt) | `01c4890547b4c4a7c16a7ce070a3cb01c754baaebcee8f02ff04395716bc1714` |
| 02 / 024-pulse-one-coast-a-snapshot.txt | [Pinned input](https://github.com/octave-commons/Truth/blob/ae5444b63e30207838f5c31f28138cb98d0027f0/.%CE%B7%CE%BC/diagnostics/bounded-look-200/attempt-02-clients/024-pulse-one-coast-a-snapshot.txt) | `163db2ecaa557cca03f7f6fc3e10d15ad6c570aeab91aee0e075f1e859be5fa2` |
| 02 / 025-pulse-one-coast-b-snapshot.txt | [Pinned input](https://github.com/octave-commons/Truth/blob/ae5444b63e30207838f5c31f28138cb98d0027f0/.%CE%B7%CE%BC/diagnostics/bounded-look-200/attempt-02-clients/025-pulse-one-coast-b-snapshot.txt) | `58e69602b14be4f8cebe633898d1232e91d31a88ff044c2e04b183084e2ee427` |
| 02 / 027-pulse-two-coast-a-snapshot.txt | [Pinned input](https://github.com/octave-commons/Truth/blob/ae5444b63e30207838f5c31f28138cb98d0027f0/.%CE%B7%CE%BC/diagnostics/bounded-look-200/attempt-02-clients/027-pulse-two-coast-a-snapshot.txt) | `89364a0808353081cb7ceae7af5a62cea50f1005d6a00084256f093b644a828f` |
| 02 / 028-pulse-two-coast-b-snapshot.txt | [Pinned input](https://github.com/octave-commons/Truth/blob/ae5444b63e30207838f5c31f28138cb98d0027f0/.%CE%B7%CE%BC/diagnostics/bounded-look-200/attempt-02-clients/028-pulse-two-coast-b-snapshot.txt) | `d2db47d688fbcaf1fcd23f83ba9fbb973d90b072efb6800f03cb20d015b20178` |
| 03 / result.json | [Pinned input](https://github.com/octave-commons/Truth/blob/93dc5b2bc0c734e5d8c311ac004035581db885b4/.%CE%B7%CE%BC/diagnostics/bounded-look-200/runs/attempt-03/aim-02-1010/result.json) | `db7910f851c7dc375737779190657c343280e09495cf24dc4649c3aef6e5c3f8` |
| 03 / snapshot-messages.edn | [Pinned input](https://github.com/octave-commons/Truth/blob/93dc5b2bc0c734e5d8c311ac004035581db885b4/.%CE%B7%CE%BC/diagnostics/bounded-look-200/runs/attempt-03/snapshot-messages.edn) | `4f2398efd0849cbe9ddd3fb5402fa239ac614eda8b087e2fa425f9145319d004` |
| 03 / 001-inspect-snapshot.txt | [Pinned input](https://github.com/octave-commons/Truth/blob/93dc5b2bc0c734e5d8c311ac004035581db885b4/.%CE%B7%CE%BC/diagnostics/bounded-look-200/runs/attempt-03/bounded-cadence-01/001-inspect-snapshot.txt) | `ac62e54adb84929d8d63b5723772341c91d5356a27c681ff39d460706b990cef` |

(己, p=0.99) This supports a concrete proposal for review, not card acceptance.
The finite upward-transition budget/admission law, actual production reference
increment bounds, manual/assist allocation and native residence remain unfinished.
The [existing proposal](../../notes/2026-10-06-local-reference-flight-assist.md#proposed-pre-capture-clock-envelope--2026-10-07-utc)
records the selected candidate wording and unresolved decisions. The
[prior feasibility analysis](local-reference-flight-assist-feasibility.md) retains
its primary control/integration references and all earlier findings.

## Later finite design disposition — 2026-10-07 UTC

The subsequent [finite upward-step analysis](2026-10-07-clock-up-transition-admission.md)
and [proposed clock design](../../designs/pre-capture-clock.md) select B=D,
reject-and-retain upward admission and persistent guarded Auto for planning review.
They supersede this note's earlier unresolved-rule status, not its measured
endpoint, recurrence or residence limitations. Three Incoming consumers provide
separate 3/5/3 implementation boundaries. No empirical calibration, capture
claim, accepted implementation or new native observation follows from that choice.
