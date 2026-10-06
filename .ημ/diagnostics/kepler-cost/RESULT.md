# Kepler reuse baseline — 2026-10-06

(己, p=1.0) **Baseline captured; production unchanged; no optimization or
performance improvement claimed.** The existing `perf-tick-residual-gap-to-60fps`
card remains in progress. The focused numerical checks passed **21 tests / 596
assertions / 0 failures / 0 errors**. The selected initial-nebula benchmark at
1000 particles averaged **53.465 ms/tick**, about **3.22 times** its 16.6 ms
budget. This observed budget miss is the performance baseline; the numerical
characterization is expected to pass before a behavior-preserving optimization.

## Source and execution

(己, p=1.0) All runs used unchanged production at
`b402939931575e377ae4409d9cd4061dff11df06` (production composition `1ad0814`).
The solver SHA256 is
`3707256ad8695a675c29fe3953f5441598cc2f434baa30ab6e33ab48ae1effc1`.
The test fixture SHA256 is
`16e02fe904a97572756d6613483a707da76cdc9b297c81973fe2ef1d2dd56aa4`.
No production source, dependency, or existing benchmark registry was edited.
The pre-execution plan remains unchanged as a historical artifact; this report
records the subsequent execution.

| Command record | UTC interval | Exit | Result |
| --- | --- | --- | --- |
| `01-capture-command.json` | 22:37:35–22:37:42 | 0 | Pinned oracle written once and strict EDN readback equal |
| `02-numerical-tests-command.json` | 22:37:42–22:37:54 | 0 | New oracle/invariants plus existing Kepler and multi-timescale regressions pass |
| `03-propagation-cost-command.json` | 22:37:54–22:38:53 | 1 | Metrics serialization rejected; no metrics file available |
| `serializer-probe-command.json` | See exact record | 0 | Actual Criterium record mismatch reproduced; explicit representation round-trips |
| `03b-propagation-cost-command.json` | 22:43:05–22:43:59 | 0 | Three propagation groups and two real compact folds recorded |
| `04-phase0-cost-command.json` | 22:43:59–22:44:58 | 0 | Original 500/1000 tick closures and named-system observations recorded |

(己, p=1.0) Primary capture/test/cost command JSON files preserve exact argv, UTC
start/end, PID, elapsed time, exit, and explicit
`JAVA_OPTS=-Xms256m -Xmx2g`. The tiny serializer probe instead records
`-Xms128m -Xmx512m` and has no PID field. Runtime was OpenJDK 21.0.12.1. One measurement JVM ran at a time. All have exited and been reaped;
the owned resource window was released to root after 22:44:58 UTC. Root owns
native pause/resume records and subsequent service activity.

## Numerical baseline

(己, p=1.0) The fixture records 14 finite input cases in both time directions,
up to 16 public `propagate` steps per case. Successful positions/velocities and
declared exception data retain raw IEEE-754 bits, including signed zero. The
matrix spans circular/eccentric ellipses, exactly and nearly parabolic states,
hyperbolic escape/infall, nonzero radial velocity, a rotated plane, and solar
and small scales. It covers the Stumpff series/trigonometric/hyperbolic regimes;
it does not claim exhaustive initial-guess or exact ±1e-7 threshold coverage.

(己, p=1.0) All 14 positive-time trajectories complete. Two negative-time
trajectories complete and 12 retain a declared failure. The μ=1,
r=[0.7,0,0], v=[0,sqrt(1.3/0.7),0], dt=-1 failure is the separately identified
pre-existing bracket defect. Its exact message, data and failed step are
preserved. Neither a negative-time repair nor universal reversal success is
part of this optimization scope. Independent reversal checks negate velocity
and use positive elapsed time. Four edge outcomes additionally preserve ±0 dt
branch ordering and invalid μ/radius errors.

(己, p=1.0) Four compact scenarios cover map/SoA writers and stationary/moving
hosts for 12 real spatial-index, SoA, gravity and kinematics folds each. Every
prepared snapshot passes the public production dominance gate; fallback cannot
stand in for Kepler coverage. All component columns, alive IDs, tick and actual
frame offsets are frozen after each fold. The moving case exercises real COM
recentering. This is an explicit component projection, not serialization of
the whole ECS world or its runtime caches.

(己, p=1.0) Independent checks include analytic circular solutions, energy
error scaled by μ/r (safe near zero total energy), angular momentum, positive
time velocity reversal, and the existing long orbital stability tests. The
fixture must never be regenerated from an optimized solver merely to obtain a
pass. A separately approved correctness change would require a deliberate,
reviewed update of affected outcomes.

## Cost baseline

(己, p=1.0) Values below come from `before-propagation.edn`. Criterium 0.4.6
quick defaults were retained: five-second warm-up, six samples targeting
100 ms each, and normal fresh-JVM overhead calibration. Intervals are the
stored Criterium mean intervals. Each pure batch propagates every input for
16 steps. The compact rows measure one real SoA fold.

| Workload | Mean µs | Mean interval µs | Thread CPU µs/batch | Allocated bytes/batch |
| --- | ---: | ---: | ---: | ---: |
| Elliptic, 7 inputs × 16 steps | 116.421 | 96.625–159.825 | 87.592 | 375121.84 |
| Near-parabolic, 3 × 16 | 41.966 | 34.985–61.823 | 33.830 | 171257.12 |
| Hyperbolic, 2 × 16 | 45.088 | 35.759–62.362 | 32.537 | 135761.12 |
| Compact SoA, stationary | 435.816 | 368.377–616.846 | — | — |
| Compact SoA, moving/recentered | 427.851 | 362.425–529.088 | — | — |

(己, p=1.0) Thread CPU/allocation readings follow warm-up and use 100 batches
through Criterium's existing `execute-expr`, whose result sink consumes each
return value. The common loop/sink overhead is included. The final result is
hashed outside the measured interval. Compact folds use futures, so no
misleading caller-thread allocation total is reported for them. Negative-time
failures are preserved in correctness characterization and are not measured as
successful propagation workloads.

(己, p=1.0) `before-phase0.edn` records only the original existing benchmark
group's selected closures; other labels are explicitly listed as skipped.

| Existing closure | Mean ms | Mean interval ms | Samples × executions |
| --- | ---: | ---: | ---: |
| `tick-world (500 particles)` | 26.550 | 20.469–34.708 | 6 × 6 |
| `tick-world (1000 particles)` | 53.465 | 42.374–69.726 | 6 × 3 |

(己, p=1.0) The named reporter measured all 45 systems five times per size on
prepared snapshots, after the documented warm-up. The five largest isolated
means at 1000 particles were hydro-em **9.131 ms**, structure **7.816 ms**,
neighbor-cache **7.641 ms**, gravity **7.579 ms**, and integrator **6.833 ms**.
At 500 they were hydro-em 5.415 ms, gravity 5.206 ms, structure 4.220 ms,
integrator 4.091 ms, and neighbor-cache 3.582 ms. Exact ranges and all systems
are in `named-segment-summary.json`; source observations are in the full
`04-phase0-cost.log`. Their isolated sum is not the concurrent critical path.

(己, p=1.0) These initial gas worlds do not exercise mature compact Kepler
cost. They provide the requested current budget and domain control. The prior
native profile motivated the distinct orbital candidate. A measured orbital
reuse benefit would not establish 60 fps, GPU cost reduction, or closure of
the initial-nebula budget gap. This selected adapter run is not the full
`bin/bench :phase0` suite. The existing hot-loop spelling is
`bin/bench profile :phase0`; the documented `:profile` spelling is a separate,
unchanged CLI discrepancy.

## Failed attempt and measurement limits

(己, p=1.0) The first propagation-cost run failed after measurement because
its strict writer detected Criterium's `OutlierCount` record becoming a plain
map when printed and read as EDN. Its script, failure, stdout/stderr and process
records remain under `attempt1/` and the `03-` files. No first-attempt metric
values are available and none were reconstructed. The approved adapter repair
encodes only that known record as explicit type plus **all** map entries;
other classes still fail. Strict oracle readback and solver source guard are
unchanged. Future writer failures preserve attempted text before throwing.
`SERIALIZATION-REPAIR.md` and the actual JVM probe describe this boundary.
The original `attempt1/serialization-error.edn` contains a trailing blank line
that produces one `new blank line at EOF` whitespace diagnostic. Its captured
bytes are preserved; this is not a blanket clean-diff claim.

(己, p=1.0) Safe endpoint process counters show zero CPU delta for both paused
owned native JVMs during successful cost windows. Other desktop/service load
continued: aggregate machine busy time was 30.8% during propagation and 48.1%
during phase0, including the benchmark itself. `load-summary.json` records the
persistent processes observed at both endpoints. It cannot count processes
that start and end entirely inside a window, and the benchmark child is absent
from the endpoint census. The ten original CPU endpoint JSON snapshots are
stored as deterministic gzip (`mtime=0`) to reduce review volume.
`cpu-encoding.json` records each original path, byte count and SHA256, each
stored gzip hash, and successful decompression byte equality. Raw originals
remain locally untracked and are excluded from both closed and staging
inventories; no measurement data was removed or rewritten. This was coordinated isolation from owned heavy
jobs, not an otherwise idle machine. Intervals are wide; paired after results
and, if needed, a reverse-order repeat are required before an improvement claim.

## Closure and next admission

(己, p=1.0) Final clj-kondo checked both new test namespaces and the current
adapter/probe with **0 errors / 0 warnings**. Production/dependency/benchmark
diff check is empty. Full strict analysis, cljfmt, Splint and the full ordinary
suite have not been run for this baseline-only checkpoint; focused numerical
success is not relabeled as those gates. Root owns later coordinated checks.

(己, p=1.0) `CLOSED-FILES.txt` names exact owned checkpoint paths, and
`SHA256SUMS` uses repository-relative names (verify from the worktree root).
Root released the pause/resume records and the successful resumed-service audit
for inclusion; these are preserved byte-for-byte. The resume occurred after the
closed measurement window. Append-only receipts and board history are staged
separately.
After the root baseline checkpoint, admission is limited to reusing z/c2/c3
within the same unconverged Newton iteration while preserving expression
order, conditional derivative, guesses, brackets, convergence/caps/errors and
final f/g propagation. No production change is included here.
