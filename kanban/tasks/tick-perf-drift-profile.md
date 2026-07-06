---
uuid: "tick-perf-drift-profile"
title: "Profile the ~1.6x tick baseline drift (17.6ms → ~30ms @1000)"
status: "todo"
priority: "P2"
labels: ["perf", "phase0"]
created_at: "2026-07-06T18:00:00.000000000Z"
source: "docs/specs/perf-60fps-parallel-tick.md"
category: "perf"
---

# Tick perf drift profile

The tick at gas-count 1000 now measures ~28–32 ms/tick (~30/s); the on-record
figure was 17.6 ms ("post-round-5 clean", memory `tick-perf-profile`). That's a
~1.6–1.8× drift. Mass-transfer is exonerated (0.58 ms / 2%, see
`mass-transfer-benchmark`). Candidates: the physics systems added since (gravity
dead-zone, dark-halo, EM, chemistry, wind-plasma, disk/mass-transfer wiring) or
measurement methodology (old criterium bench vs a rough nanoTime loop on possibly
-contended cores).

Do a per-system profiling pass (there is `:genesis/profile-subsystems?` +
`domain.profile/timing`, but `tick/run-parallel` does NOT yet time each system —
add per-system timing to the fan-out, or profile with async-profiler via
`bin/bench :profile`). Establish an honest baseline before choosing any Phase-0
grain or optimizing.
