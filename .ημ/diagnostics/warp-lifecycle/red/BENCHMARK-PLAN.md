# Bounded before/after warp cost qualification — proposed, not executed

Root must release an exclusive benchmark interval after native observation,
pause owned native load, and bind BEFORE to the unchanged production source in
this RED checkpoint. Do not start GREEN until the necessary baseline is closed.
Use matching JVM/heap, command, fixtures, adapter version and warmup both sides.

Existing adapter coverage:
- `bin/bench :phase0` dispatches `gates-of-truth.bench.phase0/run`; its registered
  coverage includes `domain.intervention` (`bench/gates_of_truth/bench.clj`).
- `phase0/run` accepts the existing quick/full benchmark callbacks. Select only
  its unchanged `tick-world (500 particles)` closure as the broad no-well
  control; retain ordinary setup and the existing Criterium quick adapter.
  Other callback labels must be recorded as unselected, not claimed measured.
- Default phase0 worlds contain no paid wells. Its named per-system breakdown
  also has no standalone warp measurement, so it cannot qualify clearing cost.

Add validation-only finite workloads through that same existing quick adapter:
500 ordinary physical recipients, one paid well, dt=1, fresh SoA. Reuse the
RED test's real placement/emitter/fold/integrator setup; these are deterministic
benchmark fixtures, never native world injection or acceptance evidence.
Prepare each immutable snapshot outside the timed closure and fingerprint its
semantic inputs. Time actual warp emitter plus `tick/apply-write-set` only:

1. Clean no-well snapshot, no prior accel.warp entries (common disabled path).
2. Active paid well, all 500 recipients inside reach with prior contributions.
3. The same carried snapshot after real `expire-interventions` removes the well;
   500 prior contributions must clear on the next fold.
4. A still-active well with 250 recipients that have physically moved outside
   reach through the test's ordinary integration steps and 250 still inside;
   prepare at the first clearing boundary with previous contributions intact.
   Snapshot preparation uses the unchanged baseline, then freezes the exact same
   inputs for AFTER; do not let the repaired emitter regenerate an easier input.

Build carried contributions using real emission/fold, not handcrafted force
values. Retain those exact prepared inputs across revisions (omit/rebuild only
transient SoA with verified inputs), record counts/signatures before timing.
Repeated cost samples operate on the same immutable input; do not repeatedly
clear an already-cleared result. Assert intended input counts before timing and
validate result lifecycle separately outside the timing. BEFORE's clearing
failure is expected correctness evidence, not a reason to drop those workloads.
Keep all non-warp channels and physical columns untouched by the measured fold.

One matched pair suffices unless results expose unresolved regression/noise.
There are five measured cases: one registered broad control and four bounded
warp lifecycle cases. Report distributions/confidence intervals and allocation
if the existing adapter supplies it, never infer gameplay FPS or a global
16.6-ms budget closure. A clearing path necessarily does work absent from the
buggy baseline; evaluate its absolute bounded cost and scaling, not a demand for
bit-identical output or zero cost. No new benchmark registry or production API
is required. Final exact script/fixtures remain subject to root source review
before executing; this file is a plan only.
