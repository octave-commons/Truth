# Attempt 02 operator preparation

Preparation only. Root must independently review and release a **fresh** ordinary
run. No game, driver, JVM, display, input, board or Git action is performed by this
bundle's preparation checks. Root owns future launch, operation and cleanup.

The [setup](root-setup.py) and [read-only poller](root-formation-poller.py) are
separate copies of the already recorded root programs. They invoke the unchanged
[frozen runner](../runner.py), with its existing source/identity guards, one-action
acknowledgements and 180/960/1470/1500-second bounds. The six executable/plan/test
pins in [the runner manifest](../preparation-hashes.json) remain unchanged.
This is an operator-sequence correction, not a change to gameplay or admission.

## New paths and initial setup

Run from the worktree root. Only after release, root allocates a free private
port and starts the ordinary fresh nebula through the existing runner into
`bounded-look-200/runs/attempt-02`. Root creates the fresh
`bounded-look-200/attempt-02-clients` output directory before invoking setup.
Do not resume attempt 01, reuse its processes, or overwrite an existing result.

The setup script performs one inspected sequence: R, Spark menu open, Fine,
Cruise, **three** acknowledged thrust-down actions, menu close, Tab lock,
baseline inspection, separately acknowledged +200/-200 X and +200/-200 Y,
final inspection, then one full frame. The provisional displacement is
`3e14 / 8 = 3.75e13`; this initial value is not an approach-safety or success
claim. Setup checks nil published thrust and zero global commitment throughout,
manual locked look mode at baseline/final return, sensitivity 0.01, return within
0.02 degrees, and the final displacement. It never aims or presses W.

Future root commands, after fresh startup/readiness has succeeded:

```sh
python3 .ημ/diagnostics/bounded-look-200/attempt-02-preparation/root-setup.py
python3 .ημ/diagnostics/bounded-look-200/attempt-02-preparation/root-formation-poller.py
```

These are sequential stages, not commands authorized to run during preparation.
The scripts write `attempt-02-calibration.json` and
`attempt-02-formation-poller.json` in the parent diagnostic directory. The
poller issues inspection only, at the inherited 30-second start cadence with at
most 32 polls and the original T+960 no-admitted-target cutoff. It stops on
global commitment first, closed run second, and stored-ready candidates third;
the latter is an observation, never target selection or supervisor admission.
If a run was already closed before a poll, it stops without another inspection.
Root always reads authoritative `closed.json`/global commitment before any next
action, including after poller error. A driver terminal outcome is not ignored.

## Later menu adjustment and approach are root-only

After the poller ends and before any new input, root inspects the actual saved
world/window identity, current global commitment, run closure and target geometry.
Root alone may choose a fixed natural candidate. A stored-ready row does not
itself select or admit that target, and fresh handoff is a separate observation.

For any later downward displacement adjustment, inspect the current cursor state.
Starting from locked look mode, the sequence is **Tab unlock and acknowledgement
→ Spark open and acknowledgement → one thrust-down and acknowledgement → Spark
close and acknowledgement → Tab lock and acknowledgement → fresh geometry before
aim**. Root reviews each saved operation before issuing the next. Do not click a
menu while `free=false`; do not repeat a gesture on failed or ambiguous readback.
If already unlocked, root does not blindly toggle it back to locked before a
click. Target-relative range, motion and current D must be evaluated from fresh
observations before any separately authorized aim or W hold.

The operator scripts do not implement this adaptive later sequence or select a
target. Setup failure calls the owned stop operation once, without retrying the
failed action. The frozen supervisor remains the lifecycle/deadline authority;
the outer scripts do not provide a stronger hard preemption guarantee. Root must
preserve both supervisor closure and outer-client reaping evidence.

## Offline checks and limits

[Pure tests](test_operators.py) extract only the poller's side-effect-free stop
classifier and inspect operator ASTs. They block subprocess, exec/fork, native
library and socket entry points. They exercise terminal plus ready, closed plus
ready, ready alone, no-target continuation, fresh attempt paths, three reductions,
four separate looks, and the absence of target-selection/gameplay commands in the
poller. No operator module top level, driver, project namespace or JVM is loaded.

The [copy proof](COPY-PROOF.json) records complete diffs and the frozen predecessor
hashes. Static closure also verifies those bytes and all 186 previously recorded
production hashes. These tests prove only operator data/ordering properties;
they do not prove delivery, ±200 native calibration, movement, overlap, commitment,
performance, person embodiment or a Gate. The original attempt-01 reporting-order
bug remains in its immutable source; only this future copy changes it.
