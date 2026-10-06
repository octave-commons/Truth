# Manual focus consumer cadence — GREEN

Based on committed RED `10f1a81767d7d704a5b36e454b6557089fa4a77e`.
The exact uncommitted production/doc diff is preserved in `source.diff`; the
focused-run provenance records SHA256 for each changed source/doc and the
unchanged regression test. All hashes still match at closure.

**Focused result:** 45 tests, 282 assertions, 0 failures, 0 errors; exit 0.
The new six-test no-render regression now passes with nearby window, input,
pilot-resolve, Spark-body and architecture tests. Command, source, heap,
start/end times and exit are in `focused-start.json`/`focused-end.json`.
The printed `ExceptionInfo: boom` originates from the existing window-test
error-logger assertion; it is not a test error. The test process is reaped.

**Static result:** touched-source/test clj-kondo, cljfmt, Splint 1.24.0 (3 files,
0 style warnings), and diff whitespace all exit 0. See `static.json` and logs.
No full suite, strict gate, native input or benchmark was run by this lane.
Independent board_scout source review found no blocker.

## Change and demonstrated behavior

The existing guarded per-intent application is extracted verbatim into private
`apply-intent`, shared by queue draining and manual focus preparation. The host
captures one config snapshot, drains input, then resolves manual attention from
the current physical position plus that config's offset before the simulation
fold. The render-thread manual enqueue is removed. The pure follow law, tracking
camera path, physical writers, input cadence and simulation clock are unchanged.

Co-moving bodies now accrue `[0.02, 0.04, 0.06, 0.08]` binding over four actual
physics folds, including nonzero recentering, without any render frames. Tests
verify current mode/offset, queued narrowing/widening, missing data and held/error
iterations. An attention AssertionError is logged and dropped without losing
already drained input or stopping the loop. Physical columns are unchanged by
serial attention preparation.

## Limits retained

Published focus still represents the pre-fold position while the integrator has
advanced/recentered physical bodies. Sculpt anchors created during earlier queue
draining and action positions captured by the renderer are not recalculated.
No prediction, teleport, new ECS writer, force or camera law was introduced.
The full live manual fly-bind-commit-voxel and paid sculpt acceptance is open.
`bin/bench :phase0` does not exercise this host loop, so no cadence cost claim is
inferred from domain benchmarks. Root owns integrated gates and native follow-up.

Original RED artifacts remain unchanged: `red-checksums.log` verifies all eleven
checksums. This GREEN subdirectory is a separate closed evidence bundle.
Focused log SHA256: `1d4a2b31808a54e4914a598833e6c85a6bedc177c5d055c29379dc4a45916f1e`.
