# Kepler reuse: baseline-only execution protocol

(己, p=1.0) Preparation is at `b402939931575e377ae4409d9cd4061dff11df06` in
`truth-kepler-cost`; solver SHA256 is
`3707256ad8695a675c29fe3953f5441598cc2f434baa30ab6e33ab48ae1effc1`.
Production source, dependencies and benchmark registry are unchanged. The
closed native profile remains in its separate worktree. Root owns process
pause/resume, commits and approval to run. **These commands have not run.**

## Finite numerical coverage

(己, p=1.0) `test/domain/orbital/kepler_workload.clj` defines 14 explicit inputs:
circular/eccentric ellipses, exactly parabolic and near-parabolic states on both
sides, hyperbolic escape and infall, nonzero radial velocity, a rotated plane,
solar and small scales, plus the separately reproduced negative-bracket case
and its immediate-apoapsis control. Each takes up to 16 public `propagate` calls
in each time direction; a declared exception stops that trajectory and is
preserved with its step, exact message and ex-data. This covers the Stumpff
series/trigonometric/hyperbolic regimes, **not precise ±1e-7 threshold boundaries
or every initial-guess branch**. The μ=1, r=[0.7,0,0], v=[0,sqrt(1.3/0.7),0],
dt=-1 outcome is captured through the actual JVM solver, without changing it.

(己, p=1.0) Four edge cases additionally pin positive/negative zero-dt branch
ordering and invalid μ/radius exception data. Doubles become tagged raw IEEE-754
bits. Independent tests check analytic circular states, energy normalized by
μ/r (well-defined near zero total energy), angular momentum and positive-time
velocity reversal. The known negative-dt defect is preserved as a baseline
outcome, not used as a false optimization RED.

(己, p=1.0) The compact oracle reuses the existing live-scale two-body fixture:
12 actual spatial-index + SoA + frozen gravity/kinematics folds, for both map and
SoA writers, stationary and translated/moving-host scenarios. All component
columns, IDs, ticks and actual frame offsets are retained after every fold.
Every prepared snapshot's public `dominance-gate` must pass, and the moving
scenario must have a nonzero COM offset computed by the real spatial index.
Transient runtime caches are outside this explicitly named component
projection; the entire world is not falsely described as EDN-serializable.

(己, p=1.0) `evidence/kepler.clj` refuses to overwrite output and refuses oracle
capture unless the solver matches the pinned SHA. It reads back the exact
printed EDN and requires equality before writing. Unsupported tagged records,
functions or divergent data cause a visible failure; nothing is silently
stripped. Capture the fixture before running its tests. Never regenerate it
from an optimized solver.

## Exact commands after resource release

Run from `/home/err/spaces/foresight/.worktrees/truth-kepler-cost`. Set only the
explicit heap shown; do not inherit a larger minimum heap. The last alias
overrides only the entry point and adds the test/evidence paths; `:bench`
supplies the already-declared Criterium 0.4.6 dependency. No dependency edit or
new benchmark framework is needed.

```bash
JAVA_OPTS='-Xms256m -Xmx2g' clojure \
  -Sdeps '{:aliases {:kepler-evidence {:extra-paths ["test" ".ημ/diagnostics/kepler-cost"] :main-opts ["-m" "evidence.kepler"]}}}' \
  -M:bench:kepler-evidence capture test/fixtures/kepler-reuse-b402939.edn

JAVA_OPTS='-Xms256m -Xmx2g' clojure -M:test \
  -n domain.orbital.kepler-reuse-test \
  -n domain.orbital.kepler-test \
  -n domain.orbital.multi-timescale-regression-test

JAVA_OPTS='-Xms256m -Xmx2g' clojure \
  -Sdeps '{:aliases {:kepler-evidence {:extra-paths ["test" ".ημ/diagnostics/kepler-cost"] :main-opts ["-m" "evidence.kepler"]}}}' \
  -M:bench:kepler-evidence propagation-cost .ημ/diagnostics/kepler-cost/before-propagation.edn

JAVA_OPTS='-Xms256m -Xmx2g' clojure \
  -Sdeps '{:aliases {:kepler-evidence {:extra-paths ["test" ".ημ/diagnostics/kepler-cost"] :main-opts ["-m" "evidence.kepler"]}}}' \
  -M:bench:kepler-evidence phase0-cost .ημ/diagnostics/kepler-cost/before-phase0.edn
```

(己, p=1.0) Preserve full stdout/stderr, exit, UTC start/end, exact argv, source
and workload hashes for each command. The existing multi-timescale suite
contains its longer 2000-tick stability and 100-tick reversal cases; it is
intentionally run once for the baseline rather than called inside every
benchmark sample. Stop and report any baseline correctness or EDN failure;
do not relax a threshold to make the performance lane green.

## Shortest useful cost protocol

(己, p=1.0) `propagation-cost` measures three positive-time batches (elliptic,
near-parabolic and hyperbolic) through the actual public function, with 16
successive steps per input and all returned final states consumed. It also
measures one actual SoA compact fold in each host-frame scenario. Criterium's
normal quick settings are unchanged. After each pure propagation warm-up, the
existing `criterium/execute-expr` consumes 100 further batches while current
thread CPU/allocation deltas are read. Its common loop/consumption overhead is
explicit. No thread-allocation claim is made for compact folds, whose futures
run elsewhere.

(己, p=1.0) `phase0-cost` calls the existing `phase0/run` and invokes Criterium
only for its exact `tick-world (500 particles)` and `tick-world (1000 particles)`
closures. Other labels are listed as skipped; this is not a pass of the whole
group. It then uses the existing named-system reporter for five prepared
snapshot samples at each size, after ten full-tick warm calls and three
system passes. Actual tick timings are authoritative; sums of isolated system
samples are not the parallel critical path. Default seed/world construction is
unchanged. These initial nebula controls do not exercise mature Kepler cost.

(己, p=1.0) The installed Criterium quick defaults are five seconds of JIT
warm-up plus six samples targeting 100 ms. Each fresh JVM also calibrates
measurement overhead (approximately ten-second warm-up plus ten 500 ms
samples). Seven selected workloads across the two processes therefore have a
rough **70-second nominal floor**, before class loading, GC, calibration
variation, setup and named-system samples. This is not a deadline. The full
`bin/bench :phase0` remains available for the repository's normal wider check;
the compact selected run is the proposed shortest before/after protocol.

(世, p=1.0) Existing CLI inspection found that `-main` recognizes the literal
argument `profile`, not `:profile`. The working hot-loop invocation is
`bin/bench profile :phase0`; it performs 1000 fresh 500-particle constructions
and one tick each, without Criterium statistics. It is neither a 1000-particle
measurement nor a compact-orbit workload. Do not use the documented but
unrecognized `bin/bench :profile` spelling or claim this hot loop proves the
Kepler optimization. This separate CLI discrepancy is not changed here.

## Comparison and admission

(己, p=0.99) After the root baseline checkpoint, the only proposed source
change is same-iteration Stumpff reuse. The frozen oracle and existing
independent tests must remain green with no fixture or tolerance edits.
Repeat these exact workloads/heap/settings on the changed source, recording
the new source SHA. Compare each branch and compact frame individually;
report variance, CPU/allocation and the 500/1000 control delta. If the apparent
benefit is comparable to variation, schedule one reverse-order paired repeat
against the pinned baseline instead of claiming success or broadening the fix.

(己, p=1.0) Root has paused the two owned native JVMs and records that separately
in `native-pause-root.json`; no agent should edit that root-owned file. Scripts
do not touch native services or other tenants. Record safe process CPU deltas
and other load around measurements; do not infer isolation solely from the
pause. No environment, full JVM arguments or secret-bearing metadata is exported.
No solver edit, live call, commit, push, review request or performance pass is
authorized by this preparation alone.
