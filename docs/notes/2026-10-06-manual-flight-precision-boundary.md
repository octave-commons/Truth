# Manual flight precision at the planet binding boundary

## Evidence and scope

Observed on source `81207e7c65212685456ebe0faff3cac1cec50613`: the native
manual session records actual R/W/D/A/S/Space/Tab/mouse input, thrust and release,
and no UI or service error. It does **not** establish manual approach, binding,
commitment, or sculpt. See
[native session provenance](../../.ημ/diagnostics/playable-foundation/manual-flight/run.edn).
The independent natural seed runs remain untouched by the diagnostic below.

The approved [flight design](../designs/spark-flight-and-camera.md#L254) makes
manual fly → resolve → sculpt the Wave 0 acceptance. Canonical Rheos reads on
2026-10-06 show `flight-no-jump-accel` in **review**, estimate **3**, with explicit
smooth-approach acceptance, and `focus-follows-pilot` in **review**, estimate **3**.
These existing claims are the relevant review surface for the precision gap.

## Recovered design boundaries

- [Flight design §3.1](../designs/spark-flight-and-camera.md#L64) specifies
  assisted 6DOF with force-channel damping. Damping toward a commanded target
  velocity is an explicitly optional later extension at lines 67–69; the corpus
  does not establish an automatic distance-based speed law for this patch.
- [Flight design §5](../designs/spark-flight-and-camera.md#L131) calls every
  non-manual view debug/cinematic. Its fixed chase-camera distance at lines
  136–139 is camera composition, not a physical approach-speed rule.
- [Input](../../src/infra/camera/navigation/input.clj#L102) normalizes held
  directions to a unit vector. The older `flight-move` at line 77 scales camera
  translation by orbit distance, but repository call-site search finds only its
  facade and tests; the native loop uses `thrust-direction` at
  [line 325](../../src/infra/dev/window/loop.clj#L325). It is not an existing
  scale-aware physical piloting path.
- [UX architecture](../designs/ux-architecture.md#L119) narrows Spark into Self
  at Phase 6. [First narrowing](../designs/the-first-narrowing-star-to-planet.md#L19)
  keeps one world while agency, camera, time, and abilities narrow. This current
  task controls the pre-embodiment spark; it does not implement character walking
  or Gate traversal. Older Q/E/R tables do not override the later flight design's
  separated pilot, focus, and ability bindings.
- [Commitment time lock](../designs/commitment-and-resonance.md#L134) is intended
  to establish planetary real time after capture. Its current
  [schema](../../src/law/narrowing.clj#L63) explicitly identifies pacing actuation
  as a later card. It cannot be assumed to solve pre-capture control precision.
- `spark-flight-force-channels` is **todo/8**, requiring a split before work;
  `flight-assist-damping-and-toggle` and `spark-6dof-input-mapping` are **todo/5**
  with unsatisfied dependencies. Their body-frame torque and rebindable control
  work is broader than the existing Wave 0 approach acceptance. `spark-planet-binding`
  is **review/5**, but its old spring/tether solution is expressly superseded by
  the flight design at lines 6–9. Do not restore that autopilot.

## Actual delayed control response

[Flight](../../src/domain/player/flight.clj#L93) emits
`a[n+1] = α D u[n] / dt[n]^2 − α v[n] / dt[n]`, with `α = 1 − r`,
default `r = 0.97`, and default `D = 3e14 m/tick`. The
[Jacobi contract](../../src/domain/genesis/tick.clj#L81) means the integrator reads
the prior published channel. The spark takes the ordinary
[symplectic Euler path](../../src/domain/integrator/kinematics.clj#L192), so in a
constant-dt, force-free one-axis analysis, `w[n] = v[n] dt` obeys

```text
w[n+1] = w[n] − α w[n−1] + α D u[n−1]
x[n+1] = x[n] + w[n+1]
```

This is a second-order recurrence, not exact multiplication by `r` each tick.
For the default α, its homogeneous roots are approximately 0.9690416 and
0.0309584. The fixed point under held unit input is still `w = D`. Summing the
stable response from rest gives total translation **D for one accepted input
tick**. Releasing from a settled held-input state gives remaining translation
`r D / (1 − r)`, including the first tick consuming the prior zero-net channel.
These quantities exclude gravity, moving targets, other influences, and recentering.
Changing retention alone cannot reduce the settled one-input-tick distance D
under these conditions; it changes the response shape and steady-release coast.

The [production-system probe](../../.ημ/diagnostics/playable-foundation/manual-flight/response-probe.clj)
runs the real thrust and integrator systems through `tick/run-parallel`, on a
separate single-body fixture. Its [raw output](../../.ημ/diagnostics/playable-foundation/manual-flight/response-probe.edn)
completed with exit 0 and empty stderr:

| Setting | First moving step | One-tick pulse, settled distance | Coast after steady full thrust |
| --- | ---: | ---: | ---: |
| Default `D = 3e14 m/tick`, `r = .97` | 60.1613 AU | 2,005.3761 AU | 64,840.4951 AU |
| Existing menu minimum `D = 1e12 m/tick`, `r = .97` | 0.20054 AU | 6.68459 AU | 216.13498 AU |

The default case agrees at `dt = 1e7` and `4.1e9` seconds. The probe loaded from
the shared tree whose `src`/`deps.edn` diff against `81207e7` was empty; the
subsequent HEAD was `4a74241a8983b9702a8276d4f0f9a046a89d66e4`.
The initial 1 GiB heap launch failed because inherited initial heap exceeded
that maximum; the preserved startup log is a harness failure. The successful
invocation added `-J-Xms256m -J-Xmx1g` and did not change simulation parameters.

The [world overlap radius](../../src/law/narrowing.clj#L131) is 1 AU, and neutral
binding accrues 0.02 per tick toward the 0.85 capture threshold. The
[menu](../../src/infra/menu/widgets.clj#L137) cannot currently select a displacement
below `1e12`. The production response therefore demonstrates an insufficient
fine-control range, not a newly observed native formed-world approach failure.

The same formula does **not** establish invariance across changing dt. With
different consecutive steps, the prior-channel kick is
`dt[n] α (D[n−1] u[n−1] / dt[n−1]^2 − v[n−1] / dt[n−1])`.
Future tests must preserve that lag and inspect dt changes explicitly.
The loop's [16.67 ms target](../../src/infra/dev/window/loop.clj#L123) only sleeps
when work finishes early, so ticks must not be silently converted to guaranteed
60 Hz wall time. The current flight docstrings' exact-retention and fixed-pace
wording needs correction alongside any control repair.

## Smallest proposed correction and verification

Provisional implementation direction: reopen the existing **3-point**
`flight-no-jump-accel` review acceptance, retain its single acceleration channel,
and provide a discoverable player-selectable fine displacement range through the
existing intent/settings path. Do not couple physical thrust to debug camera
orbit, inject the spark near a target, enlarge the one-world binding gate, or add
an unresearched automatic capture/approach controller. The exact input affordance
and chosen range remain design decisions, not recovered facts.

As a derived sizing bound, to coast less than `ε R` after steady fine thrust at
fixed dt, choose `D <= ε R (1 − r) / r`. For `R = 1 AU`, `r = .97`, and an
illustrative 0.1-radius coast allowance, `D <= 0.003093 AU/tick` (about `4.63e8 m/tick`).
This is a testable starting constraint, not a validated feel or a guarantee of
capturing a moving planetary target. A cruise/fine distinction can preserve
long-distance travel while making local control possible without changing physics
ownership.

Existing [terminal-displacement tests](../../test/domain/spark_body_test.clj#L191)
derive a fixed point from one emitted acceleration. Existing
[pilot/resolve tests](../../test/domain/pilot_resolve_seam_test.clj#L61) directly
write the spark position. Add a meaningful regression that drives held input and
release through the actual frozen-snapshot thrust/integrator pair, verifies lag,
bounded pulse and coast relative to the binding radius, and feeds the resulting
physical positions into focus/binding. Include multiple constant dt values and
an explicitly characterized dt change. Then use actual manual controls in the
natural world to demonstrate approach, rising binding, commitment, rendered
voxels, and a paid sculpt operation. Until that succeeds, leave the native
verification card in progress.
