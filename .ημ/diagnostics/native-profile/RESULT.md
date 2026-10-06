# Observed mature native CPU profile

(世, p=1.0) The 45-second JFR observation completed once on owned native JVM
3083624, from **2026-10-06 22:10:14 to 22:10:59 UTC**. The largest measured
target-process CPU consumer was Mesa's `llvmpipe-*` worker group. Within Java
execution samples, the Kepler solver and Barnes–Hut traversal were prominent.
This establishes an optimization investigation target, not an FPS improvement
or a new isolated 500/1000-particle benchmark result.

## Source, scene and controls

(己, p=1.0) Evidence was prepared in `codex/truth-native-profile` at
`b402939931575e377ae4409d9cd4061dff11df06`; production source is the validated
`1ad081475364cc3b75db76142204cbf54b5bf60c` composition. No source was changed.
The retained JVM runs from `truth-focus-input`, with the approved cadence loop
loaded from `truth-focus-cadence`; both compact readbacks confirm the sim-loop
root is the callable saved by that approved reload. This is the retained
same-world native service on private display `:2`, nREPL port `7895`.

(世, p=1.0) Before/after readbacks show an ordinary unfrozen nebula scenario,
active in `:arc/life-emergence`: 858 living entities, comprising 812 nebula,
36 planets, 3 protostars, 3 stars, 3 brown dwarfs, and the player; 40 motion
histories. Both workers remained alive, with no service/UI error, manual mode
and nil thrust. Ticks were 28781 and 28967 at wall times 1791324612029 and
1791324670481 ms. The readbacks span 58.452 seconds around the recording and
include client startup; **186 ticks is not the tick count for the exact 45-second
profile**. `dt` remained 2.544932992796438e9 simulated seconds. Root reported
all diagnostic hooks restored and no UI input during the recording.

## Measurements

| Observation | Result | Interpretation limit |
| --- | ---: | --- |
| Target process CPU over 47.868-second surrounding counter window | 604.15 CPU seconds; 12.621 average cores | Includes simulation, JVM, renderer and native worker threads |
| 22 surviving `llvmpipe-*` threads | 490.82 CPU seconds; 81.24% of target CPU | OS thread counters; native rasterization internals are not symbolized |
| Render-thread native samples | 648, including 642 at `glfwSwapBuffers` | A blocked/native boundary sample is not CPU-time attribution |
| Java execution samples | 1858; 38 truncated | Sampled stacks, not complete timed system segments |
| Kepler namespace present | 593 samples, 31.92% of Java samples | Inclusive union once per sample; may overlap caller categories |
| Barnes–Hut force namespace present | 294 samples, 15.82% | Inclusive, not additive with other rows |
| Spatial index namespace present | 187 samples, 10.06% | Inclusive, not an isolated query benchmark |
| GC pause events | 71; total 2.882 seconds | 6.40% of the recording; not added again as independent CPU work |
| GC pause median / maximum | 29.203 / 159.115 ms | No allocation events enabled, so no allocation-site claim |

(世, p=1.0) More specific first-project-frame counts include
`traverse-soa-idx` 259, `universal-anomaly` static entry 108,
`fg-state` 105, and Stumpff static/primitive entries 89/62. These entries are
diagnostic samples, not invocation counts. `propagate.invokeStatic` appears
in 431 samples. Namespace union counts in the table avoid adding duplicate
`invoke`/`invokeStatic` frames from the same sampled stack.

(世, p=1.0) The render thread had 54 Java execution samples and 648 native
samples; 738 other native samples were a socket accept on an nREPL worker.
Therefore raw native-sample totals must not be ranked as CPU hotspots. The
software-renderer CPU conclusion comes from the separate OS thread counters,
not from assuming that swap-buffer or socket waiting consumed CPU.

## Load and coverage limits

(世, p=1.0) Mean JFR whole-machine load was 98.55%, with target JVM user/system
fractions 56.11% / 0.24% averaged over the 44 periodic events. Two additional
Java processes, PIDs 3658963 and 3662397, consumed 97.35 and 74.72 CPU seconds
in the surrounding counter window. Both had exited before the safe cwd-only
follow-up could identify them. Their ownership and work remain unknown.
Desktop/background processes also consumed CPU. Root's two GIF encoders had
finished before release; no root capture or deliberate benchmark overlapped.

(己, p=1.0) This is consequently an **active-load observation**, not an isolated
speed benchmark. Safe process counters were sampled sequentially, so the target
row inside the whole-process census differs slightly from the earlier dedicated
target snapshot (604.93 versus 604.15 CPU seconds); use the dedicated target pair
for the stated share. Threads/processes that exit between snapshots are absent
from matched deltas. JFR sampling, attach startup, GC, scheduling and the mature
scene affect the observation. No native FPS counter or framebuffer time was
collected. The profile cannot rank individual Mesa shader/pixel costs.

## Recording and export audit

(己, p=1.0) `minimal-safe.jfc` explicitly configures all 183 registered JDK
event types. The metadata-only live registry query found no custom types.
Only ExecutionSample/NativeMethodSample (20 ms), CPU/ThreadCPU load (1 second),
GarbageCollection and GCPhasePause are enabled. Environment, properties,
JVM arguments/flags, process metadata, libraries, thread dumps and every other
payload event are explicitly disabled. No default/profile recording was taken.

(世, p=1.0) Inventory before export contained only those six event types plus
JFR's `jdk.Checkpoint` and `jdk.Metadata` infrastructure. Every sensitive event
count was zero. The first checker mistakenly allowed `CheckPoint`; it failed
closed and is preserved in `recording-inventory-audit.json`. The corrected
infrastructure spelling passed in the adjacent corrected audit **before** the
selected-only export. The original 602,356-byte JFR is preserved. Its selected
JSON export is preserved losslessly as deterministic gzip; its uncompressed
SHA256 is recorded in `sample-summary.json`. No payload content was redacted or
secret-bearing recording taken first.

(己, p=0.99) The bounded follow-up proposed in
[KEPLER-CANDIDATE.md](KEPLER-CANDIDATE.md) reuses duplicate Stumpff work within
one Newton iteration without changing the physics or solver decisions.
Implementation is pending root approval. Original perf-card acceptance for
named segment milliseconds at 500/1000, a before/after delta and numerical
equivalence remains open. No source optimization, board transition, service
stop, input, commit or push is part of this profile.
