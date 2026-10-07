# Resolving reverse pair: qualify reduced allocation, not elapsed-time speed

(己, p=1.0) The single approved reverse-order pair is complete. Attempt 2
ran first at solver SHA `61009ec34e7e2317059a1f5a824bd58c94db4793206a4ad096a5b3a860ea3c8d`,
then the clean detached baseline at commit
`a96dfd7346ba9e052488da85d45df285319317a7`, solver SHA
`3707256ad8695a675c29fe3953f5441598cc2f434baa30ab6e33ab48ae1effc1`.
The adapter and fixture are identical. Workload/test differences are only
the two recorded indentation corrections. All pinned hashes were checked
before and after, and the detached baseline remains clean.

(己, p=1.0) The unchanged `propagation-cost` action measured all three pure
batches and both real compact folds with the same Criterium settings and
`JAVA_OPTS=-Xms256m -Xmx2g`. No phase0 repeat or oracle capture occurred.
Attempt 2 ran 23:35:50.310–23:36:45.396 UTC (55.086 seconds); baseline ran
23:36:45.442–23:37:40.275 UTC (54.833 seconds). Both exited 0 and were reaped;
the resource window was released immediately before offline analysis.

## Result

(己, p=1.0) **Repeated measured allocation reduction is supported. General
elapsed-time, compact-fold throughput and native FPS improvements are not
established.** The numerical characterization remains the earlier 21 tests /
596 assertions with exact frozen result/exception bits, plus independent
analytic and invariant checks. No tolerance or oracle adjustment accompanied
this comparison.

| Workload | Baseline mean µs | Attempt 2 mean µs | Mean change | CPU/batch change | Allocation change |
| --- | ---: | ---: | ---: | ---: | ---: |
| Elliptic 7 × 16 | 148.598 | 120.361 | −19.00% | −36.95% | −10.65% |
| Near-parabolic 3 × 16 | 43.211 | 56.500 | **+30.75%** | −18.82% | −10.59% |
| Hyperbolic 2 × 16 | 43.867 | 36.212 | −17.45% | −18.73% | −14.59% |
| Compact SoA stationary | 504.652 | 372.781 | −26.13% | — | — |
| Compact SoA moving | 424.475 | 502.207 | **+18.31%** | — | — |

(己, p=1.0) All Criterium mean intervals overlap. The near-parabolic wall
mean and moving compact mean worsen in this pair, while the previously
worsened hyperbolic CPU and stationary compact observations reverse. This
mixed behavior is evidence against presenting a general timing win.
`comparison.json` retains both intervals and all observed values.

| Pure batch | Original baseline bytes | First attempt 2 bytes | Reverse baseline bytes | Reverse attempt 2 bytes |
| --- | ---: | ---: | ---: | ---: |
| Elliptic | 375121.84 | 335153.84 | 375121.84 | 335153.84 |
| Near-parabolic | 171257.12 | 149457.12 | 167153.12 | 149457.12 |
| Hyperbolic | 135761.12 | 110865.12 | 129809.12 | 110865.12 |

(己, p=1.0) Attempt 2's measured allocated bytes are identical across the
two runs. Baseline near-parabolic and hyperbolic allocation totals vary, so
the claim is bounded to observed reductions: elliptic 10.65%, near-parabolic
10.59–12.73%, and hyperbolic 14.59–18.34%. The exact cause of baseline
variation is not established by these measurements. Current-thread counters
cover the fixed 100-batch result-consuming loop, including its shared overhead;
they do not cover compact-fold worker allocations. Final result hashes are
unchanged.

## Load and scope

(己, p=1.0) Both paused owned natives, PID4070721 and PID2372912, have zero
CPU delta in both intervals. Aggregate machine busy fraction was 29.97% for
attempt 2 and 30.03% for baseline, including benchmark work. Other persistent
processes still consumed CPU. Endpoint census limitations remain explicit in
`load-summary.json`; similar aggregate percentages do not establish identical
load timing, clock behavior or memory contention. All counter snapshots are
losslessly gzip encoded with their original-byte hashes in `cpu-encoding.json`.

(己, p=1.0) The source change removes duplicate same-iteration Stumpff work
without changing initial guesses, brackets, convergence/caps/errors, final
f/g, the known negative-time defect, or ECS composition. The first rejected
implementation's allocation increases remain preserved separately. The
initial 1000-particle budget and native renderer costs remain open; these
orbital measurements do not close either claim.

(己, p=0.99) The next step is qualified source validation and checkpoint:
retain the measured allocation-only claim, finish the coordinated full
ordinary suite and full strict rerun after the two test indentation fixes,
then record the exact resulting source. No additional benchmark repetition
is proposed on the current evidence.
