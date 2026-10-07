# Manual sculpt contextual boundary — observed RED

(己, p=1.0) At source `7188fbab1400589c5a3ccadb560df6b5120a5f9b`, the one
root-released focused JVM ran **56 tests / 380 assertions / 13 expected failures /
0 errors**, exited1 and was reaped in11.74s. Java21 mainPID1192905 and tools.deps
child1192912 are absent. Heap256MiB/2GiB, work deadline175s plus5s cleanup reserve;
no timeout occurred. `focused-execution.json` holds exact argv, observed process
identities, times, options, exit and output hashes. All304 source/test pins stayed
unchanged; production loop/input are untouched.

**Interpretation:** every failing assertion explicitly reports missing admitted
`:enqueue`, `:record`, and `:request` APIs. The spec bodies behind those checks
have not yet executed; there is no alternate test implementation or fallback.
These are API-existence RED failures, not thirteen observed wrong-anchor outputs.
The independent legacy-route baseline test PASSED its numerical assertions:
actual callback→IntentAtom→sim-loop after a real production integrator fold pays
at stale published attention, and that anchor differs from current Spark-relative
aim under zero and nonzero common frame translation. It also verifies physical
columns are unchanged across the action boundary. The old legacy path is an
intentional compatibility control; GREEN must repair the opted-in ordinary dev
path rather than change the plain-Atom/absent-capability contract.

New law schema tests and existing nearby controls passed. Raw stderr's `boom`
trace is produced intentionally by existing `window_test.clj:113–118` error-log
coverage; the runner reports zero errors. Keep it byte-exact.

## Scope

The new named portable law data and two new test namespaces are the entire code
delta. Tests specify nominal versus map/keyword dispatch, swap/reset arities,
invalid capability before GLFW installation, no direct fallback on submission
failure, discrete callback events, real moving/recentered anchors, payment and
denial outcomes, rollback, coincident-point convention, FIFO reset/camera/readers,
mid-drain arrivals and one config projection. They create/free callbacks without
a GLFW window; no live service, native input or rendering was used.

The following command is the **already executed** focused run, not a request for
another uncoordinated run:

```sh
env -u JAVA_TOOL_OPTIONS -u JDK_JAVA_OPTIONS -u _JAVA_OPTIONS \
  JAVA_OPTS='-Xms256m -Xmx2g' \
  clojure -M:test \
  -n law.input-intent-test \
  -n infra.dev.sculpt-intent-test \
  -n infra.dev.focus-cadence-test \
  -n infra.dev.window-test \
  -n infra.render.input-test \
  -n domain.pilot-resolve-seam-test \
  -n architecture-test
```

`static-kondo.*` and `static-reader.*` record lightweight qualification before
execution. Initial authoring found five shadowed core names, corrected before
freeze; an initial parser-only read lacked the `intent` alias, corrected without
loading project namespaces. The first pin-inventory attempt requested a missing
`resources/` directory and failed before JVM launch; its error is retained in
`pin-inventory-attempt-01.json`. The successful pin list explicitly records that
absent directory and includes src/test/dev plus deps.edn.

## Handoff

`CLOSED-FILES.txt` and `SHA256SUMS` use repository-relative paths and cover only
the authored code and this closed evidence bundle. Run checksum verification
from the repository root. Root-owned admission/card/ledger files and the earlier
proposal are supporting companions, outside this immutable inventory. The
preexisting untracked independent planning review is untouched.

Next is root source review and RED checkpoint. No production GREEN, full gate,
host-cost qualification or natural committed-world sculpt acceptance is claimed.
