---
uuid: "mass-transfer-benchmark"
title: "Benchmark the gradual mass-transfer system (sweep sink-count)"
status: "todo"
priority: "P2"
labels: ["perf", "phase0", "bench"]
created_at: "2026-07-06T18:00:00.000000000Z"
source: "bench/gates_of_truth/bench/"
category: "perf"
---

# Mass-transfer benchmark

Turn the inline measurement into a permanent bench file
`bench/gates_of_truth/bench/mass_transfer.clj` (match the hydro/gravity pattern;
wire a `:mass-transfer` category in `bin/bench`).

Inline result (2026-07-06, 1000 bodies, 1 sink): mass-transfer run = **0.58 ms**,
full tick = 28 ms → **~2% of tick**. So M3 is NOT the source of the tick baseline
drift (17.6 ms historical → ~28–32 ms now). BUT this was 1 sink only; cost scales
with (sink-count × gas-in-zone) because each sink runs effective-accretion-radius +
a zone query + per-parcel IMF/feedback filtering. The bench must **sweep sink
count** (e.g. 1/5/20/50 resolved bodies among the gas) to capture the scaling, not
just the 1-sink floor.
