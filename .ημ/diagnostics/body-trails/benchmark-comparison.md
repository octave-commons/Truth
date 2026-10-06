# Body trails: isolated Phase 0 benchmark comparison

Before: `e71b12f1f5fe070695fd1cd82cded2296a9f862d` in `truth-validation`.
After: `3614d34854bd0953ea12aef4aae1e3642b5b9c4b` in `truth-motion-trails`.

Both runs executed the unchanged `bin/bench :phase0` under `/usr/bin/time -v`.
Runner, dependency, benchmark-definition hashes, selected JVM environment and
CPU affinity match. Both report OpenJDK 21.0.12.1, 22 processors, and a 6 GiB
maximum heap. Each process exited 0 and was reaped before the next load began.

Root stopped other owned simulation, native, and validation loads for each
measurement. Retained native Java PID 2372912 remained suspended. Unrelated
desktop and host services were not stopped; their command-name snapshots are
preserved in the start/end metadata. This is controlled owned concurrency,
not a claim of a dedicated or otherwise idle machine.

| Case | Before mean | After mean | Approx. change | Before reported range | After reported range |
|---|---:|---:|---:|---|---|
| tick-world (100 particles) | 4.5 ms | 4.6 ms | +2.2% | 3.6 ms — 6.7 ms | 3.4 ms — 6.8 ms |
| tick-world (500 particles) | 26.3 ms | 21.2 ms | -19.4% | 18.9 ms — 39.9 ms | 18.0 ms — 24.6 ms |
| tick-world (1000 particles) | 52.2 ms | 47.8 ms | -8.4% | 36.3 ms — 77.3 ms | 36.4 ms — 67.4 ms |
| tick-world (500 particles) — critical path | 24.1 ms | 23.5 ms | -2.5% | 18.2 ms — 39.3 ms | 17.5 ms — 33.5 ms |
| step-physics parallel (on world1 with spatial tree) | 24.7 ms | 23.9 ms | -3.2% | 16.8 ms — 34.4 ms | 15.9 ms — 32.3 ms |
| 10 ticks | 218.1 ms | 299.5 ms | +37.3% | 175.9 ms — 285.3 ms | 204.1 ms — 367.5 ms |

Percentages use the benchmark's rounded printed means. Reported ranges are
Criterium's lower/upper quantile outputs, not confidence intervals. One
before/after pair does not establish a general speedup or a narrow regression
bound; full raw output and all case comparisons are retained.

The suite repeatedly ticks fresh 100/500/1000-gas genesis worlds and includes
a ten-tick sequence. It measures the newly added hot-path owner in early
nebula conditions, not mature worlds with 64 retained samples on many bodies
or GPU line rendering. Bounded-history/segment behavior has focused tests;
native visible fading, mature-scene performance, and gameplay acceptance
remain separate evidence. Initial one-sample subsystem profiles are not a
substitute for the repeated whole-tick measurements.

`before-start.json`/`after-start.json` preserve command, environment, hashes
and source state; the corresponding end files preserve elapsed time, exit
status and raw-output hash. `*-timing.txt` contains complete resource timing.
`compare-benchmarks.py` reproduces this derived report after both logs close.
