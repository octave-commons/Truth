# Queued formation-handoff regression qualification

The only executable change is one test in `dev/demo_test.clj`, SHA256 `88acbc55e7603c2d87b43fa3d19bb030c4cffa69dc941938c3eb9c8ada289364`, on base `760ea79d017f4e012943789f896dfdfdb0a173b1`. Production `dev/demo.clj`, the intent adapter/drain and dependencies remained unchanged. All six pinned input hashes and HEAD were checked around every subprocess.

The test uses the production IntentAtom and drain with three prior configurations: present callable settings, an explicit nil, and absent settings. An interrupted wait restores the exact prior config and leaves the published world/camera unchanged until the consumer runs. The pending replacement remains queued; a later unrelated intent survives the real serial drain and explicit publication. This characterizes existing non-cancellation, rather than adding rollback or promising a timing bound.

## Executed sensitivity control

A disposable JVM loaded the current test and queue implementation, then replaced only the demo namespace with the exact `0112e228e93abf346da31f15456290c1b4c9b7ae:dev/demo.clj` blob. Only the new test ran: **1 test, 36 assertions; 30 pass, 6 expected failures, 0 errors**. All six failures are exact configuration-restoration checks at lines 92 and 109 for the three prior shapes. The subprocess exited 1 and was reaped. This is a historical file-overlay control, not qualification of a whole historical checkout and not a new RED-before-production sequence; the production fix was already committed in c7cc34a.

## Current qualification

Four sequential processes exited 0 and were reaped, finishing 2026-10-07T02:25:32.380546Z:

- `clojure -J-Xms256m -J-Xmx2g -M:demo-test`: **7 tests, 59 assertions, 0 failures/errors**, including the unchanged actual 30-second timeout.
- `bin/analyze --strict`: **all six gates pass**. Nonblocking structural warnings and 64 clones/1.52% duplicated lines remain visible in raw stdout; this is not a zero-diagnostic claim.
- `clj-kondo --lint dev/demo_test.clj`: zero errors/warnings.
- `clojure -J-Xms256m -J-Xmx2g -M:cljfmt check dev/demo_test.clj`: pass.

The ordinary 948-test/16,775-assertion suite was not rerun for this test-only change. Its earlier exact-composition qualification is separately recorded under `current-composition-8487159`; future hosted checks apply to the new commit. No native GL run, new gameplay acceptance, benchmark, live connection, restart or hot reload occurred in this qualification. The retained game ran concurrently, so elapsed times are not performance measurements.

## Review interpretation

Review 5436791462 correctly identifies the missing world-intents failure assertion. Its 16ms/100ms timing reasoning is unsupported: prepare-formation! waits for a token in the final published world, and drain-intents folds all queued functions before publication. Another replacement can therefore conceal a consumed token, and target cadence is not a latency bound. Failure restoration neither cancels the queue nor restarts the service. The unchanged select! failure-path observation is being separately triaged; this test does not claim to repair it.

The canonical native verification card remains In Progress, 3 points. This bundle closes only the test qualification; hosted review convergence, ordinary manual approach/commit/sculpt, visuals, lifecycle and earned Gate acceptance remain open.
