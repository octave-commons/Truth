# Planned detached host-loop cost observation

Status at closure: script prepared and statically checked; **not executed**.
Root is the sole native nREPL/lifecycle operator. This plan and its checksums
cover only the explicit plan files, not root's separately written live records.

The accepted production source is `1ad081475364cc3b75db76142204cbf54b5bf60c`.
Published head `b402939931575e377ae4409d9cd4061dff11df06` adds evidence only.
The original native loop loaded at `400ba3a` has identical source to RED
`10f1a81767d7d704a5b36e454b6557089fa4a77e`; the repaired loop matches GREEN
`021ef9d5c0f6064747b7443df8661248df49cbf0`.

Before lifecycle stage 3, root must verify the exact file it loads:

- `src/infra/dev/window/loop.clj`: SHA256
  `f9e860f7398141d0c0d0cf4bd0bd11dba7141f7edd4b4916f8d0051efc62ea0b`.
- Existing `test/infra/dev/focus_cadence_test.clj`: SHA256
  `4d2019a18d4f5c66ea1f49082af1ce28301fbb4e38d952bd5ad90072e8176f68`.
  The diagnostic checks this harness hash before loading its definitions.

## Lifecycle contract

`host-loop-cost.clj` consumes the atom `user/truth-cadence-reload` created by
board_scout's root-reviewed staged procedure. It runs only at `:stopped-loaded`,
after both original workers are proven dead and the new loop is loaded, before
the native workers restart. Its required state keys are:

- `:old`: original complete service map, retaining world/config/camera atoms,
  world-intents object, stop flag, and original worker references.
- `:queue`: original `ConcurrentLinkedQueue`, captured before reloading the type.
- `:stopped-world`, `:stopped-camera`, `:stopped-config`, `:pending-intents`.
- `:old-sim-loop`, `:old-drain-intents`, `:new-sim-loop`, `:new-drain-intents`.

The script guards world, camera and queue identities/contents. Stage 3 intentionally
clears obsolete GPU resources after saving `:stopped-config`, so the script uses
the **current post-cleanup config** as its identity guard and separately checks
all nonresource keys against the saved config. The only excluded keys are the
eight program/mesh slots and `:ui/applied-cursor` named by the staged procedure.

Process-wide `with-redefs-fn` is permitted only while both native workers remain
dead. Each before sample uses **both** original loop and original drain callable
references; retaining only the loop would resolve the new drain and contaminate
the baseline. Every scope restores the repaired roots, including on exception.
The script never restarts the service. Root inspects the result or failure before
continuing the next lifecycle stage; an nREPL timeout must not cause blind replay.

## Measurement

The existing finite-publication harness invokes the actual host `sim-loop` with
a detached world atom, the exact immutable stopped-world value, an identity tick,
and no render intents. No physics executes and no native world is changed.
Manual mode measures the added per-iteration attention preparation; tracking mode
is the unchanged control. Focus offset and tick interval come from the preserved
current config. The runner's snapshot/watch bookkeeping is equal in both variants.

For each mode, warm both variants for 64 publications, then run three paired
128-publication samples, alternating before/after order. The total is 1,792 paced
publications, approximately 29 seconds plus namespace loading and assertions.
Root stores completion/error in diagnostic state so a client timeout is observable
without duplicating the evaluation.

CPU time and allocation counters must already be supported and enabled. The
script does not change JVM monitoring settings or substitute wall time for an
unavailable counter. Record raw current-thread CPU and allocated bytes per sample
and per publication, elapsed wall time, GC counters, source references, controls,
and the captured world's tick/time/frame data. Root can summarize paired ranges
and medians from the output after confirming all preservation guards passed.

## Scope and limits

This is a **shared-heap detached host-boundary observation**, not native FPS,
full simulation throughput, or an isolated process benchmark. Loop sleep dominates
wall duration, so CPU/allocation carry the relevant comparison. JIT and GC still
share the native JVM; retain the raw paired spread and do not infer an optimization
from a small noisy difference.

The complete native world stays in memory. Genesis stores a function at
`[:handlers :event/collision]`, while spatial/event values include records; a
plain-EDN round trip would fail. No fields are stripped and no substitute world
model or serializer is introduced. All physical components, clocks, frame data,
and the production world atom remain unchanged. Detached manual attention is the
only permitted output difference, checked against the existing pure follow law.

`bin/bench :phase0` runs domain ticks directly. Its previous results establish
only an unchanged-domain control and cannot measure the moved host preparation.
No native gameplay success or full fly-bind-commit-sculpt acceptance follows from
this cost observation.
