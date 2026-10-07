# Warp lifecycle BEFORE checkpoint

The five authorized baseline cases completed at unchanged RED commit
`e434a8384e9e87342b0a1012b6b87b33acbb3a46`, before any production repair.
Capture retry PID 110779 and benchmark PID 111177 both exited 0 and were reaped.
The benchmark ran 2026-10-07 00:39:18–00:40:18 UTC with 256 MiB initial / 2 GiB
maximum heap. Exact commands, source hashes, boot identity, output and sampled
process/load state are preserved beside this report. The source diff was empty
before each execution and all recorded source hashes matched afterward.

| Existing adapter case | Mean | Criterium mean interval |
| --- | ---: | ---: |
| Registered Phase 0 tick, 500 gas particles | 19.1907 ms | 18.2035–20.3131 ms |
| Warp emitter + fold, no well / no prior cells | 324.551 ns | 269.794–474.518 ns |
| Warp emitter + fold, 500 active recipients | 252.746 µs | 247.630–262.015 µs |
| Warp emitter + fold, expired well / 500 prior cells | 290.280 ns | 259.993–391.662 ns |
| Warp emitter + fold, 250 live / 500 prior recipients | 204.208 µs | 202.913–205.305 µs |

Each case used the unmodified repository `gates-of-truth.bench/quick-bench`
adapter with its default Criterium quick configuration (six samples). Raw
metrics preserve all retained result fields, including the known OutlierCount
record's explicit type and every field. The first case is the exact registered
Phase 0 closure: only its ordinary world factory is temporarily replaced with
that same factory's captured output. All other registered callback labels are
listed as skipped in `coverage.edn`; ordinary group setup profiles still ran.
Those setup profiles are not additional Criterium cases.

The four focused cases start with actual paid `intervention/place` and production
parallel integrator/emitter steps from the committed lifecycle test fixture.
Fresh SoA construction and fixture validation occur outside timing. Each sample
folds one real warp write-set into the same immutable snapshot, so clearing is
not silently replaced by a later already-cleared state. Validation recorded:

| Case | Prior cells | Live emission | Folded cells BEFORE |
| --- | ---: | ---: | ---: |
| Clean | 0 | 0 | 0 |
| Active | 500 | 500 | 500 |
| Expired | 500 | 0 | 500 |
| Partial departure | 500 | 250 | 500 |

Every fixture paid 15 agency (85 remains), and emitter/fold preserved position,
velocity, mass and the independent gravity column. The expired/partial timings
include the existing bug: stale entries are not removed. Their cheap baseline
is not a correctness target; a lawful repair must perform the missing work.
The separately committed RED tests exercise well and repulsor, map and SoA,
actual subsequent motion, component/archetype removal and the final legitimate
Jacobi carry. These cost fixtures do not replace those correctness tests.

## Frozen inputs and serialization boundary

- Frozen EDN input SHA256:
  `2c275009a46b8bc813bd1541e7b3bdb73d51e1e7d2112b8aa0a1962f3b6fa096`.
- Production intervention SHA256:
  `e541dd5df5ff208818d216c9083b73693216409c73599b6ab63be0f641c83dc0`.
- Final diagnostic adapter SHA256:
  `deba81148f3b94a19de736beb54dab9c36eb43a10a9c2f14c3d460e8eea40ed2`.
- Production collision-handler source SHA256:
  `05e3a4dd24cadbc49278066278772dce1b2b8a706057fbcd5637b5fe50e2c0ba`.

The first capture failed before timing because the ordinary Phase 0 world
contains one runtime callback, `:event/collision`. The original adapter
`c8858f7b…`, command/result/log/source record and full exception are retained in
`capture-attempt1/`; original top-level capture files are also unchanged. The
exact rejected 1,103,089-byte printed snapshot remains at the diagnostic root.
No partial fixture was accepted. The retry represents only the known production
`domain.stellar.merge/stellar-merge-handler`: encode verifies the exact sole key
and callable identity; decode verifies the exact representation and source hash,
then reattaches that direct callable. No reader evaluation, arbitrary object
coercion, handler dropping or alternate world factory is used. Exact EDN
round-trip validation remains mandatory for all other data. Focused independent
source review found no blocker in either the workload adapter or this repair.

## Interpretation and next comparison

The host had rebooted before this run; the previously owned native processes
were absent and the parent held new native launch until this cost window ended.
Twelve five-second process/load observations recorded 1-minute load 3.52–6.65.
This is a bounded local JVM baseline with background host activity, not a GPU,
rendered-frame, mature-world, isolated-machine or player-FPS result. No speedup,
regression verdict or fabricated latency target is claimed before AFTER.

AFTER must use these exact frozen inputs and the same five closures/configuration.
It must validate folded cell counts `[0 500 0 250]`, retain active force and other
owners, and report intervals and the cost of newly required clearing honestly.
Do not regenerate fixtures from the repaired source. Source qualification and
fresh actual lifecycle tests remain separate obligations.

All 15 committed RED hashes reverify. Touched diagnostic clj-kondo reports zero
errors/warnings. No production edits, full suite, all-six strict, native calls,
GREEN, hosted review approval or gameplay completion are claimed here. Root owns
the baseline checkpoint and subsequent GREEN authorization.
