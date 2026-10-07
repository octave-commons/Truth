# Warp lifecycle matched AFTER: correctness restored, timing signal unresolved

The authorized five-case AFTER completed 2026-10-07 00:54:40 UTC, exit 0;
PID 212179 was reaped. The unchanged adapter consumed the exact frozen baseline
fixtures. It verified folded counts `[0 500 0 250]`: all 500 expired entries and
exactly 250 departed entries are explicitly removed, while the 250 still-live
partial recipients remain. Paid agency and the independent physical columns
are unchanged. No fixture regeneration or alternate force law was used.

| Case | BEFORE mean [mean interval] | AFTER mean [mean interval] |
| --- | ---: | ---: |
| Registered Phase 0, 500 gas particles | 19.1907 [18.2035–20.3131] ms | 34.8343 [28.8352–42.2549] ms |
| No well / no prior contribution | 324.551 [269.794–474.518] ns | 567.357 [448.046–712.032] ns |
| 500 active recipients | 252.746 [247.630–262.015] µs | 287.367 [262.286–303.427] µs |
| Expired well / 500 prior entries | 0.290280 [0.259993–0.391662] µs | 297.214 [248.758–377.749] µs |
| 250 live / 500 prior recipients | 204.208 [202.913–205.305] µs | 385.291 [379.402–392.669] µs |

These are the intervals returned for Criterium's mean, not the console's
lower/upper-quantile range. `comparison.json` preserves the numeric data. Both
runs use the existing quick-bench adapter, six samples per case and the same
heap options. Coverage remains exactly the original selected Phase 0 closure
plus four finite emitter/fold cases; ordinary setup profiles are not additional
benchmarked cases. No new timing framework or call-count proxy was introduced.

## Interpretation

The broad Phase 0 mean increased **81.5%**, with separated returned mean
intervals. That is an unresolved regression signal; this evidence does not
qualify the patch as performance-neutral or establish its cause. The active
case increased 13.7%, with narrowly separated mean intervals. The clean case's
mean increased by about 0.243 µs, with overlapping intervals. The expired and
partial cases now perform missing removal and archetype maintenance; their old
fast-but-incorrect path is not a correctness target. Required cleanup is finite
in these measured 500-recipient snapshots, but that fact does not resolve the
broad tick slowdown or prove a global frame budget.

Source inspection found the registered closure still receives the identical
ordinary no-well world (hash `-1395318129`). The added registry self-read is
metadata; the actual parallel fan-out does not schedule by those read sets.
The small observed clean-path delta cannot alone account for the broad
15.64 ms increase. This is a diagnostic observation, not causal attribution.
No additional reverse-order pair or profile was authorized or run here.

The parent verified and paused its exact native process PID 131581/start125503
before timing, with PM2 autorestart disabled and the frame observer restored.
Every AFTER process sample records that process in `Tsl`, and the parent resumed
the same process afterward; pause/resume records are included. The rest of the
host was not isolated: sampled 1-minute load was 17.44–21.85 AFTER versus
3.52–6.65 BEFORE. Aggregate CPU busy fraction was 36.7% versus 26.0% across the
sampled intervals on a 22-logical-CPU host, including the benchmark itself.
These load facts do not explain away the timing signal. `ps` percent CPU is a
lifetime average and must not be mistaken for activity of the stopped process.

## Source and qualification boundary

- Production intervention: `eed4697f04bf96ce8bdabc0e08b64c63bd296a0d9cc958b49574bbef643c74a8`.
- Registry: `eb5df3121fac4eb1c2e4bb1cb94b5e13984409938e3dffa3b4a6ad59a292a3dc`.
- Frozen fixture: `2c275009a46b8bc813bd1541e7b3bdb73d51e1e7d2112b8aa0a1962f3b6fa096`.
- Unchanged adapter: `deba81148f3b94a19de736beb54dab9c36eb43a10a9c2f14c3d460e8eea40ed2`.

The runner rejects changed unrelated sources. Its sole test-byte exception is
the parent-approved three-indent formatting correction, verified by rebuilding
the exact original test bytes and matching their baseline hash. All recorded
source hashes were unchanged across the run. Commands, logs, process/load
samples, inputs, metrics and exit provenance remain inspectable.

The separate GREEN report records the passing full suite on identical production
(948 tests / 16,775 assertions), later focused 36/225 and all-six strict passes,
plus both earlier qualification failures and the exact formatting correction.
No canonical committed-source transition gate, hosted approval, native gameplay,
GPU/frame measurement or performance pass is claimed here. The card remains
In Progress; root owns checkpoint, final gate and publication decisions.
