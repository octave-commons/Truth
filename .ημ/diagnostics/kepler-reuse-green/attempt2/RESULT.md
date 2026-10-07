# Local-term reuse, attempt 2: allocation improvement; timing unresolved

(己, p=1.0) This implementation restores the original one-argument bracket
residual and evaluates both unchanged Newton expressions over local
`z/c2/c3`. The derivative remains in the existing nonconverged branch. Solver
SHA256 is `61009ec34e7e2317059a1f5a824bd58c94db4793206a4ad096a5b3a860ea3c8d`.
No tuple, extra function dispatch, cache, equation, bracket, tolerance, error,
negative-time behavior or final f/g change is introduced. Independent source
review found no blocker. The first rejected implementation remains frozen
under the parent directory's `ATTEMPT1-SHA256SUMS`.

(己, p=1.0) The frozen numerical baseline again passed **21 tests / 596
assertions / 0 failures / 0 errors**. The strict static run then found only two
test indentation lines; these were corrected and the targeted formatter check
passed. `format-correction.json` records exact hashes and indentation-only
changes. Solver and oracle bytes did not change. All other strict stages
passed, but the full strict run retains its actual **exit 1**. A final full
strict rerun and full ordinary suite remain pending coordination.

## Exact cost actions

(己, p=1.0) The same unchanged baseline adapter and Criterium settings were
used, with `JAVA_OPTS=-Xms256m -Xmx2g`. Propagation plus compact folds ran
23:29:06.719–23:30:03.048 UTC (56.329 seconds). Selected phase0 ran
23:30:03.080–23:30:56.524 UTC (53.444 seconds). Both exited 0 and were reaped
before resource release. Source and fixture hashes were checked before and
after; test indentation differences are disclosed in `cost-source.json`.

| Workload | Baseline mean | Attempt 2 mean | Mean change | CPU/batch change | Allocated bytes/batch change |
| --- | ---: | ---: | ---: | ---: | ---: |
| Elliptic, 7 inputs × 16 steps | 116.421 µs | 98.777 µs | −15.16% | −7.62% | −10.65% |
| Near-parabolic, 3 × 16 | 41.966 µs | 37.652 µs | −10.28% | −3.65% | −12.73% |
| Hyperbolic, 2 × 16 | 45.088 µs | 35.354 µs | −21.59% | **+59.05%** | −18.34% |
| Compact SoA stationary | 435.816 µs | 545.648 µs | **+25.20%** | — | — |
| Compact SoA moving | 427.851 µs | 436.318 µs | +1.98% | — | — |
| Initial 500-particle tick | 26.550 ms | 30.442 ms | +14.66% | — | — |
| Initial 1000-particle tick | 53.465 ms | 55.165 ms | +3.18% | — | — |

(己, p=1.0) Allocated bytes per pure batch decreased from 375121.84 to
335153.84, 171257.12 to 149457.12, and 135761.12 to 110865.12 respectively.
The earlier implementation's allocation regression is absent in these
observations. Result hashes remain the same. These are current-thread
post-warm-up observations through the same Criterium result-consuming loop;
common sink/counter overhead is included. No caller-thread allocation total
is claimed for the compact folds' futures.

(己, p=1.0) All before/after Criterium mean intervals overlap. Attempt 2
intervals are 84.127–129.574 µs (elliptic), 32.522–44.806 µs
(near-parabolic), 29.417–43.967 µs (hyperbolic), 385.804–766.226 µs
(compact stationary), and 389.218–547.316 µs (compact moving).
`comparison.json` preserves both sides and all CPU/allocation fields.
The hyperbolic 100-batch CPU reading rose from 32.537 to 51.750 µs/batch
despite its lower Criterium wall mean; this anomaly is not averaged away.
The stationary compact fold also needs a paired check before accepting a
general cost benefit.

## Controls and limits

(己, p=1.0) Root's newly launched hardware native PID4070721 was verified by
starttime3869117, executable and cwd, then observed paused at both endpoints.
It and retained PID2372912 had zero CPU delta during both measurement windows.
The earlier PID3083624 was absent; this run does not imply that lost service
resumed successfully. The root separately owns the new hardware game's resume
and service audit after this resource window.

(己, p=1.0) Aggregate machine busy fraction was 25.32% during propagation and
50.42% during phase0, compared with 30.79% and 48.06% in the baseline. Those
fractions include benchmark work, and the safe endpoint census cannot count
processes entirely born and exited inside an interval. Other host load and
the original baseline-to-attempt ordering remain confounds. Raw counter data
is losslessly gzip encoded with original-byte hashes; no environment or full
command-line census was taken.

(己, p=1.0) Initial gas worlds do not exercise mature Kepler work. Their
1000-particle mean remains 3.32 times the 16.6 ms target; no umbrella budget or
native FPS completion follows. All 45 named systems have five observations at
each size. Largest isolated means at 1000 were hydro-em10.274 ms,
gravity7.520 ms, integrator7.449 ms, structure7.088 ms and
neighbor-cache6.968 ms. These are neither a critical-path sum nor a causal
attribution of the between-run differences.

## Proposed shortest resolving comparison

(己, p=0.98) Under a new root-coordinated pause window, run the exact existing
`propagation-cost` action first on attempt 2 and then on a clean pinned
baseline checkout, with identical settings. This measures all three pure
groups plus both actual compact folds in reverse order, including the same
100-batch thread CPU/allocation reading. It takes roughly two minutes plus
startup/calibration variation. Further phase0 repetition is not presently
needed to resolve the Kepler-path signal. Preserve every result, including
any repeating CPU or compact-fold regression. No repeat has been started and
no general timing improvement is accepted by this report.
