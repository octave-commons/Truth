# Host-loop cost: observed before/after result

The repaired manual loop added a **median paired 37.900 microseconds of
current-thread CPU and 8,432 allocated bytes per publication** in this bounded
observation. The three manual CPU deltas ranged from **30.807 to 43.665 µs**;
allocation increased by exactly 8,432 bytes/publication in all three pairs.
The unchanged tracking control had identical allocation and CPU differences
that varied in sign. This measures the added serial attention work with no
render frames or physical tick; it does not establish native FPS or gameplay
throughput.

## Source and preserved input

Root executed the frozen script once in the retained JVM. The successful raw
output is `host-loop-cost.log` (5,519 bytes), SHA256
`5e02c0a0e8ae3445b199b71aa525333ce2612dba5f3cab067f69945dccaf8d37`.
Script SHA256 is
`67fdf21d3eedbb44d4ac4a4102f5a05a17ccfa3d13b50b8be185fb7a1fdb1be5`.
Before source is `10f1a81767d7d704a5b36e454b6557089fa4a77e`; after source is
`1ad081475364cc3b75db76142204cbf54b5bf60c`. Original loop source loaded at
`400ba3a` is identical to the before revision. Both the original loop and its
original drain callable were restored for each before sample.

The same complete immutable natural-nebula world was retained in memory at
tick **23,086**, with **867 alive entities**, one observer, simulated time
`1.0440968298284344e14`, and `dt = 2.544932992796438e9` seconds. Its fixture flag
was false. The exact frame offset and object identities are recorded in the raw
output and derived EDN summary. No serializer, field stripping, or world reset
was used.

All script guards passed: both native workers were stopped, the production world,
config, camera and queue were preserved, user settings survived resource cleanup,
and the repaired loop/drain roots were restored after each detached run. The raw
result reports all three preservation flags true. Native restart and later live
behavior are separate lifecycle evidence, not inferred from this result.

## Samples

Each variant/mode warmed for 64 publications, followed by three paired samples of
128 publications each. Pair orders were before→after, after→before, before→after.
The measurement ran on `nREPL-worker-0` and completed in **28.935 seconds** overall.
Recorded control overrides were `{}`: the harness used its zero focus offset and
the host's default tick interval. Manual/tracking modes were selected only in
detached config copies; the retained native mode was manual.

CPU values below are microseconds per publication, including the identical
harness setup, watch and recording overhead. Allocation values are average bytes
per publication. Each min/median/max is over the three measured repetitions,
excluding warm-up.

| Mode | Variant | CPU min / median / max (µs) | Bytes min / median / max |
| --- | --- | --- | --- |
| Manual | Before | 64.194 / 75.906 / 83.449 | 385 / 385 / 385 |
| Manual | After | 107.859 / 113.806 / 114.255 | 8,817 / 8,817 / 8,817 |
| Tracking control | Before | 57.665 / 71.358 / 87.346 | 385.5625 / 385.5625 / 385.5625 |
| Tracking control | After | 52.382 / 71.640 / 73.678 | 385.5625 / 385.5625 / 385.5625 |

Paired CPU deltas are after minus before for the same numbered pair:

| Pair | Execution order | Manual ΔCPU (µs/publication) | Tracking ΔCPU (µs/publication) |
| --- | --- | --- | --- |
| 1 | Before → after | +37.900 | −13.669 |
| 2 | After → before | +30.807 | +0.282 |
| 3 | Before → after | +43.665 | −5.283 |

The **median paired** tracking delta is −5.283 µs, with range −13.669 to +0.282 µs.
This differs from subtracting the two tracking medians (+0.282 µs); the paired
statistic preserves each run's comparison order. Tracking allocation delta is
zero in every pair. Manual allocation delta is +8,432 bytes (8.234375 KiB) in
every pair.

## Interpretation and limits

All manual pairs show the cost of refreshing attention on every iteration in this
no-render case. This is a measurable additional host cost, not a zero-overhead
claim. The tracking control's CPU spread and changing sign show why this small
sample should not be promoted into an optimization claim or an exact universal
per-call cost.

Warm-up CPU averaged 84.198 µs before and 146.899 µs after in manual mode;
tracking warm-up was 81.379 and 81.507 µs. Later tracking measurements generally
declined across pairs. The results are therefore sensitive to warm-up/order in
the shared JVM; these observations do not identify a particular JIT or scheduling
cause. All three reported GC collector counters and collection times were
unchanged across the observation.

The 128-publication samples took roughly 2.06 seconds because the real loop still
paces with sleep. Wall time cannot isolate this small host-boundary change.
Current-thread CPU excludes sleeping and other threads' work, while allocation,
JIT and GC still share the native JVM. No physics tick or render frame ran during
the comparison, and the prior render-enqueued input workload was intentionally
absent. No FPS, whole-game slowdown, simulation throughput, or live approach/bind
success follows from these numbers. Existing `bin/bench :phase0` measurements
remain only an unchanged-domain control.

`host-loop-cost-summary.edn` contains the full-precision derived statistics,
paired order, warm-up values, provenance, and preservation flags. This report
was derived offline; the raw log bytes and frozen script were not changed.
