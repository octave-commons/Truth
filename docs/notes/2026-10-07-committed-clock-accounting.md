# Committed clock: elapsed accounting and cutover limits

(己, p=1.00) Proposed engineering contract under existing Incoming3 owner
`committed-clock-executable-policy`, based on `98b847ce75491bdccacf2ec3a7476d451270bf49`.
This resolves an accounting boundary, not the entire local-clock design. No
production clock is changed, no consumer is admitted, and no native lock,
chronological biology or human-scale flight is demonstrated. GPL-3.0-or-later.

## Grounding and source owners

The [route audit](2026-10-06-playable-gate-route-audit.md#reopen-the-data-only-time-lock-then-define-its-consumer-precisely)
identified the missing consumer. Product intent is
[Commitment & Resonance §5](../designs/commitment-and-resonance.md#5-post-commitment-lod-and-tick-rate),
with later accelerated history in
[world-generation phases](../designs/gates-of-truth-world-gen-phases.md).
The [multi-timescale design](../designs/multi-timescale-integration.md) and its
[research](../research/physics/multi-timescale-integration-jacobi-ecs.md) address
orbital integration within a shared interval; they do not establish independently
advancing interacting regions. The proposal below introduces no such solver.

| Boundary | Existing owner and fact | Proposed responsibility |
|---|---|---|
| Capture | [`time-lock-record`](../../src/domain/narrowing.clj#L290), written by `:commitment` | Preserve the capture fact. It does not write wall time or velocity. |
| Host elapsed time | [`sim-loop`](../../src/infra/dev/window/loop.clj#L120) currently samples `System/nanoTime` for sleep pacing | Supply validated elapsed intervals at the serial tick boundary; no I/O in domain policy. |
| Physics interval and accumulated time | [`advance-simulation-clock`](../../src/domain/genesis/tick.clj#L178) accounts for the **input** `:sim/dt`, then installs adaptive pacing for the next step | Keep one serial owner. Explicitly report consumed interval and retain exact local elapsed accounting. |
| Observer elapsed time | [`arc/tick-genesis`](../../src/domain/arc.clj#L282) subtracts absolute `:genesis/sim-time` values | Use the successfully consumed interval, not cancellation-prone timestamp subtraction. Inactive/no-step means zero. |
| Cadence | [`lod-scheduler`](../../src/domain/lod.clj#L47) owns LOD phases; optional [`due-entity?`](../../src/domain/integrator/base.clj#L75) skips integration | Keep throttling disabled in the first consumer. No skipped interval is silently discarded. |
| Flight | [`thrust-acceleration-system`](../../src/domain/player/flight.clj#L79) emits a delayed channel using `max(1, dt)` | Qualify sub-second input and the cutover before enabling the lock. One kinematic writer remains. |
| Other time consumers | [`condense-tick?`](../../src/domain/stellar/classifier/state.clj#L247), [`planet seed age`](../../src/domain/planet_formation/seed.clj#L46), intervention expiry, voxel queues and narrative event times | Audit elapsed, deadline and display meanings separately; a rounded legacy timestamp is not a new timing authority. |

## Reproduced arithmetic limit

(世, p=1.00) The isolated [Clojure arithmetic probe](../../.ημ/diagnostics/committed-clock-policy/arithmetic.clj)
ran under Babashka/SCI with Java numeric semantics; its
[output](../../.ημ/diagnostics/committed-clock-policy/arithmetic.stdout.edn) and
[execution record](../../.ημ/diagnostics/committed-clock-policy/execution.json)
are preserved. It loads no project namespace and is not a production RED test.
Epochs below are illustrative inputs, not fresh native measurements.

| Absolute age (s) | Double spacing (s) | Added `1/60 s`, measured by subtraction | Sixty repeated additions, measured by subtraction |
|---:|---:|---:|---:|
| `1e12` | `0.0001220703125` | `0.0167236328125` | `1.00341796875` |
| `1e14` | `0.015625` | `0.015625` | `0.9375` |
| `4e14` | `0.0625` | `0` | `0` |

Thus a correct small physics interval can appear as zero to `tick-genesis`'s
observer update. Replacing the cosmological floor with `1/60` alone is not a
clock implementation. The existing tail already uses the input interval for
its addition; this is a precision hazard, not a claim that it adds the wrong
next-step pacing value.

The same probe shows that adding one metre to a `1e17 m` double coordinate
produces zero observed displacement (spacing 16 m). This is a separate warning
for future embodied/local-coordinate work, not a demonstrated native motion
failure or permission to change coordinate ownership in this card.

## Proposed first accounting contract

(己, p=0.97) Interpret **1 s/s** as admitted elapsed active wall time, not
`60 × configured dt`. Use one causal simulation time for the first consumer:
all currently resolved bodies advance through the same consumed interval. The
immediate neighborhood is therefore covered as a subset. Regional/global
statistical scheduling is not implemented by that first consumer.

The host uses differences from the same running JVM's
[`System.nanoTime`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/System.html#nanoTime())
source. Its origin is arbitrary; it is not UTC, a persistent timestamp or a
cross-process clock. Its precision does not promise nanosecond resolution.
Persist admitted elapsed intervals for replay, not a `nanoTime` origin that a
later process could mistake for its own.

Proposed pure shapes (names are design notation, not new production keys):

```clojure
Clock = {:epoch-s finite-nonnegative-number ; pre-lock history, unchanged
         :elapsed-ns exact-nonnegative-integer
         :credit-ns exact-nonnegative-integer
         :rate {:numerator nonnegative-integer :denominator positive-integer}
         :rate-remainder nonnegative-integer} ; always < denominator
Input = {:wall-interval-ns exact-nonnegative-integer
         :paused? boolean
         :quantum-ns positive-integer
         :max-steps positive-integer}
Allowance = {:credited-clock Clock :permitted-steps nonnegative-integer}
Completion = {:committed-steps nonnegative-integer} ; j <= permitted k
Output = {:clock Clock :consumed-ns exact-nonnegative-integer}
```

The first scope permits rational rates `0 ≤ numerator/denominator ≤ 1`.
Classify each supplied raw wall interval at the host boundary. For an active
interval `w`, credit `div(w*n + remainder, d)` nanoseconds and
retain `mod(w*n + remainder, d)`; do not lose fractions repeatedly at slow rates.
Rate changes must first settle the old-rate remainder with its denominator or
retain it as an exact fraction; it cannot be reinterpreted under the new rate.
This remainder representation/change operation remains to be chosen before an
implementation story is admitted.

Given credited budget `C`, quantum `h` and step cap `M`, permit at most
`k = min(M, floor(C/h))` steps. The host returns the number `j <= k` actually
committed; debit exactly `j*h`, advance elapsed by `j*h`, and preserve `C-j*h`
as visible backlog. A success includes the existing complete tick, `on-step`
and publication boundary: a caught `on-step` error currently republishes `w0`
and cannot count as consumed physics. Unrun or failed steps consume nothing.
The allowance and completion are one serialized operation tied to the current
clock revision, so completion cannot be applied twice or to another allowance. The production
numeric backend must check exactness and bounds before mutation; no overflowing
cast, silent clamp or dropped backlog is allowed. Exact integers here are a
law, not a claim that a particular cross-host integer representation has been
selected. Physics receives the chosen step duration converted to seconds, with
conversion error bounded by its later numerical acceptance.

Keep epoch and local elapsed amount separate. A legacy approximate
`epoch + elapsed` projection may serve an explicitly approximate age display;
it must not supply small elapsed deltas, cooldowns or deadlines. Replay consumes
the recorded intervals through the same pure policy. Persistence and the
migration of existing absolute-time readers need a complete consumer inventory
before activation; this note does not claim a save/restart path exists.

## State and first-step decisions

| State at serial boundary | Proposed action |
|---|---|
| Uncommitted | Existing adaptive/off pacing and slip behavior continue. This scope changes neither. |
| Commitment first appears after step N | Account for N using its actual old interval. Capture the epoch after that accounted step; establish the host anchor at publication. Do not bill the prior cosmic step's wall duration again as locked time. |
| First eligible locked step | Consume only elapsed time admitted after that anchor. Do not execute one more cosmological interval merely because the old `:sim/dt` remains in the published map. This requires an explicit pre-step boundary owned by the existing clock path. |
| Locked, adaptive on or off | Lock policy has precedence over both choices. Adaptive softening policy must remain separately explicit; a clock override does not silently freeze or retune softening. |
| Locked with time slip request | Do not amplify the locked interval. Report slip suppression; any later history mode needs a separate accepted transition. |
| Slow rate | Credit the interval under the rate that governed that interval, carrying its exact remainder. Apply a newly drained rate change to subsequent intervals. |
| Paused or error-paused | Admit no paused-wall credit and run no owed steps. Preserve existing backlog. Reset the host sampling anchor on resumption so paused duration cannot reappear as catch-up. |
| Loaded/uneven host cadence | Preserve debt and report lag. Bounded per-loop work may fall behind; do not claim measured real time while backlog grows. |
| Missing/invalid clock input | Reject before advancing/publishing the affected step and expose the cause. No nominal-60-Hz substitution. |
| Later history or Gate synchronization | Unsupported by this first consumer. No automatic acceleration, fabricated civilization or network-clock claim follows from commitment. |

A complete adapter must account for input/pause changes at defined boundaries;
subtracting timestamps after reading the new pause state is not sufficient to
classify the preceding interval. JVM suspend, long stalls and backlog limits
need explicit host policy rather than an undocumented maximum-delta clamp.

## Worked credit trace and cadence distinction

(世, p=1.00) The probe uses an **illustrative** 10 ms quantum and maximum four
steps per host iteration. Neither value is selected for production physics.
Active intervals 20, 20 and 100 ms produce elapsed amounts 20, 40 and 80 ms,
leaving 60 ms owed after the loaded iteration. A nine-second paused interval
changes neither elapsed nor credit. Two later zero-new-credit calls consume
40 and 20 ms, reaching exactly 140 ms with zero debt. This all-success arithmetic example proves only its accounting. It does not
execute failure feedback, publication, rate changes, integration error bounds
or runtime throughput; those remain required RED cases for the later adapter.

If a regional body is eventually evaluated after three common intervals
`h1,h2,h3`, its due interval is their sum, not merely `h3`; twice consuming that
sum is equally wrong. However, accumulation alone does not make stale forces
or asynchronous neighbor interactions correct. The first consumer therefore
keeps current full updates. A later cadence implementation needs its own
numerical contract, last-evaluated-time ownership, promotion/demotion rules and
interaction error tests. No independent fast-forwarding region is introduced.

(己, p=0.96) Proposed resolution of contradictory product wording: in
Commitment & Resonance §5, regional **10 s/s / 100 s/s** must be replaced by
**longer update intervals at the same physical time** for a coupled locked
world. This note proposes that amendment; the original text remains unchanged
pending review. Later master-design accelerated history can be an explicit
whole-world mode or a separately researched causal model. Neither is the
initial local-lock implementation, and a final Gate synchronization law remains
future work.

## Consumer boundaries and remaining design work

These are candidate slices, not created/Ready cards or guaranteed estimates:

1. **Exact elapsed accounting kernel (proposed 3):** pure validated shapes,
   credit/consumption, slowdown remainders, rejected invalid/bounds cases and
   replay traces. Decide the numeric backend and rate-change representation
   first. RED: irregular intervals, repeated slowdown fractions, pause/debt,
   invalid values, failed step and very old epoch with nonzero local elapsed.
2. **Host interval adapter (proposed 5):** monotonic sampling, cutover anchor,
   boundary mode changes, pause/resume, failure commit and visible backlog.
   RED uses an injected host clock; native acceptance measures actual elapsed
   and consumed intervals in a disposable ordinary-nebula process. No nominal
   rate claim substitutes for that measurement.
3. **Single-clock production consumption (proposed 5):** retain sole ownership,
   use consumed duration in the observer path, audit absolute-time consumers,
   and preserve full-entity shared-time integration. Replay and native traces
   must cover first capture, adaptive-off and slip. Activation depends on the
   host adapter **and** the next flight contract; arithmetic success alone is
   insufficient.
4. **Sub-second flight and cutover (size not yet established):** existing
   thrust uses `D(1-r)/dt² - v(1-r)/dt` with a clamp and one-tick force lag.
   Removing only the clamp preserves an enormous *per-tick displacement*
   target, not human-speed control. Specify phase-appropriate units, delayed
   channel behavior across changed dt, retained momentum and range/bearing
   acceptance before sizing. Do not zero velocity or write a second kinematic
   path to make a demonstration look safe.

The existing Incoming3 specification remains unfinished until the unresolved
numeric, adapter, quantum/stability, reader-migration and flight decisions fit
reviewed implementation boundaries. This note supplies source facts and a
reproducible accounting proposal; it does not turn those remaining decisions
into implied permission to run a live clock.
