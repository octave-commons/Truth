# Grid collector baseline: observed cost before any optimization

At unchanged production `b395c4049718f7ce015ddf25fc0a192d97821373`, collector
SHA256 `280941b79defe025dbdd7c1478940b956c1e0dc9bd270fa4c66f5170f1a94701`.
This checkpoint is the observed performance RED under the existing InProgress3
card. Behavioral characterization is expected GREEN, not an invented physics
failure. No candidate implementation or improvement is present.

The new public-query tests plus existing spatial/cache tests pass **44 tests,
775 assertions, zero failures/errors**. Initial formatting failed on indentation
in two authored files; original sources and output are preserved. Official
cljfmt fixed leading whitespace only, the corrected check passed, and the
source-pinned focused JVM exited/reaped normally. This is not a full-suite or
six-stage strict qualification.

Compact fixture capture completed at 04:36:45 UTC, exit0/reaped, with source and
adapter unchanged. `fixtures.edn` is 55,179 bytes and preserves factory settings,
ordered query IDs, raw-bit fingerprints, actual fresh neighbor write-set hashes
and four-tick complete-component hashes. Before timing, independently reconstructed
inputs and observations matched exactly. Only the observer's random ID is omitted
from the comparison projection; this is not complete ledger/host-state equality.

## Six cases

Each query batch contains32 public radius calls. Values below are **milliseconds
per batch/rebuild/tick**, respectively. Intervals are Criterium's returned mean
intervals; all six raw samples, options, execution/warmup counts and quantiles
remain in `../before/*.edn`.

| Case | Mean ms | Mean interval ms | Serial bytes/batch |
|---|---:|---:|---:|
| Uniform1000,32-query batch | 1.6366 | 1.3510–2.0357 | 3,699,009.84 |
| Clustered1000,32-query batch | 2.0461 | 2.0145–2.0974 | 4,905,081.12 |
| Neighbor rebuild500 | 5.9069 | 4.5398–7.2582 | Not measured across parallel workers |
| Neighbor rebuild1000 | 12.2580 | 8.8600–17.2889 | Not measured across parallel workers |
| Registered Phase0 tick500 | 25.9058 | 21.2438–32.3720 | Not measured across parallel workers |
| Registered Phase0 tick1000 | 60.5016 | 46.1503–80.1989 | Not measured across parallel workers |

The1000-body mean is **3.64×** the16.6ms budget, with broad dispersion. The
isolated profile's86/1858 native samples through this helper justify investigating
it; neither those samples nor this baseline predict the attainable improvement.
Current-thread CPU/allocation are reported only for serial batches and include
Criterium consumption overhead. Untimed existing-group profiles in stdout are
separate from the six timed cases. Fixtures are benchmark scenes, not mature
natural formation or playable-route evidence.

## Resource and native ownership

The timing JVM1500297 ran 04:38:46.833–04:40:34.469 UTC at explicit256MiB/2GiB
heap, exited0/reaped, with HEAD, source, tests, adapter and fixture hashes stable.
Only root-owned nativePID131581 was suspended for timing. Boot ID, startticks,
cwd, PM2 identity/restart/autorestart and live world/window were verified first.
The native remained stopped with zero CPU delta at every sample; other services
were untouched and host-load samples are retained. This controls one known load,
not all host contention.

SIGCONT ran in `finally` at04:40:34.470. Guarded after-read confirmed the same
world atom919497450/window137768861339584, both workers alive, nil errors,
production tick/body functions and fixturefalse; tick advanced193041→193067.
The benchmark did not replace the world, reload code, input controls or restart
the native service. No new native FPS/visual/gameplay result is claimed.

Root must commit this observed baseline before any source optimization. A
candidate must retain these frozen observations and demonstrate benefit with the
same workloads, plus full ordinary/strict gates. Preserve noisy or negative
results; do not recapture the oracle or repeat until a preferred outcome appears.
