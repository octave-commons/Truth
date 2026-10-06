# Targeted ten-tick repeat

The full benchmark's +37.3% ten-tick increase did not reproduce consistently
in three targeted repetitions on each exact original source revision. The
raw original result remains preserved; no production optimization was made.
This does not establish a general speedup or a narrow regression bound.

Before: `e71b12f1f5fe070695fd1cd82cded2296a9f862d`.
After: `3614d34854bd0953ea12aef4aae1e3642b5b9c4b`.
Both ran back-to-back in the clean `truth-validation` checkout, with the
authorized clean detached-source switch recorded. Each process exited 0
and was reaped; other owned heavy loads remained held and native PID 2372912
remained suspended. Unrelated host workloads were recorded and untouched.

`ten-tick-repeat.clj` selects the actual `  10 ticks` closure from the existing
`gates-of-truth.bench.phase0/run` callback. The original setup and constructor
are retained. It invokes the existing normal Criterium quick runner three
times; unrelated timed callbacks are skipped. These targeted timings
therefore have different surrounding warmup work than the full-suite run.

| Repetition | Revision | Mean | Reported quantile range |
|---|---|---:|---|
| 1 | Before | 250.4 ms | 170.6–324.9 ms |
| 1 | After | 222.5 ms | 178.8–280.7 ms |
| 2 | Before | 220.0 ms | 171.1–319.4 ms |
| 2 | After | 190.5 ms | 159.0–251.4 ms |
| 3 | Before | 240.3 ms | 170.5–298.5 ms |
| 3 | After | 240.6 ms | 178.9–288.8 ms |

Each quick repetition has six samples, one ten-tick execution per sample.
Full unrounded means, confidence estimates, samples, outlier data, and
warmup times are retained in the `*-measurements.edn` files. Broad scatter
is visible on both revisions. Three repetitions within one JVM are not
three independent machine environments, and the order was not randomized.

The timed closure starts each invocation from the same immutable fresh
500-gas world. Histories grow within its ten ticks, never across benchmark
invocations. An untimed trace using the same original constructor and
production tick records the following actual after population:

| Tick | Histories | Total retained samples | Samples per history → count |
|---|---:|---:|---|
| 0 | 0 | 0 | {} |
| 1 | 1 | 1 | {1 1} |
| 2 | 1 | 2 | {2 1} |
| 3 | 1 | 3 | {3 1} |
| 4 | 1 | 4 | {4 1} |
| 5 | 1 | 5 | {5 1} |
| 6 | 1 | 6 | {6 1} |
| 7 | 2 | 8 | {7 1, 1 1} |
| 8 | 2 | 10 | {8 1, 2 1} |
| 9 | 2 | 12 | {9 1, 3 1} |
| 10 | 2 | 14 | {10 1, 4 1} |

Exact equality of observed tick, simulation-time, dt, and matter-state counts between the before and after traces: **true**.
One condensed core appears at tick 2 and becomes a gas giant at tick 6.
The second history begins at tick 7, respecting the frozen-snapshot fan-out.
By tick 10 there are two histories with ten and four samples, respectively.
The trace does not approach the 64-sample capacity or global render cap.

The repeat resolves the earlier case as a non-reproduced signal, not proof
of zero overhead. Mature-history cost, native GPU rendering, visible fading,
and gameplay acceptance still require their separate planned evidence.
Source/environment/timing/provenance files and their hashes are included in
`target-CLOSED-FILES.txt` and `target-SHA256SUMS`.
