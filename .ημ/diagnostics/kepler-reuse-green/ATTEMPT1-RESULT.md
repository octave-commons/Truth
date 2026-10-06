# First reuse implementation: numerical pass, performance not accepted

(己, p=1.0) The first same-iteration Stumpff reuse implementation preserves the
frozen oracle and passes the focused **21 tests / 596 assertions**. Source SHA
`94ad5b0a4aa2f33a14afb15574a005af1d4ffff1da85bec5d9068a58f5255190`
is reconstructible from baseline commit `a96dfd7` plus `solver.patch`.
Independent source review found no changed equation, branch or exception
contract. The baseline bundle and fixture remain unchanged.

(己, p=1.0) Both authorized cost actions completed once, with the exact baseline
adapter, settings and workloads. Propagation ran 22:58:53–22:59:45 UTC
(52.312 seconds), and selected phase0 ran 22:59:45–23:00:44 UTC
(58.823 seconds). Both exited 0 and were reaped before resource release. The
four endpoint CPU snapshots use deterministic gzip with their original-byte
hashes in `cost-cpu-encoding.json`.

| Workload | Before mean | After mean | Mean change | Thread CPU change | Allocated bytes change |
| --- | ---: | ---: | ---: | ---: | ---: |
| Elliptic 7 × 16 batch | 116.421 µs | 123.457 µs | +6.04% | +14.57% | +22.76% |
| Near-parabolic 3 × 16 batch | 41.966 µs | 44.432 µs | +5.88% | +13.17% | +12.30% |
| Hyperbolic 2 × 16 batch | 45.088 µs | 39.297 µs | −12.84% | −1.19% | +10.61% |
| Compact SoA stationary | 435.816 µs | 390.961 µs | −10.29% | — | — |
| Compact SoA moving | 427.851 µs | 378.630 µs | −11.50% | — | — |
| Initial 500-particle tick | 26.550 ms | 22.015 ms | −17.08% | — | — |
| Initial 1000-particle tick | 53.465 ms | 47.056 ms | −11.99% | — | — |

(己, p=1.0) All before/after Criterium mean intervals overlap. Current-thread
allocated bytes per pure batch increased from 375121.84 to 460497.84 for
elliptic, 171257.12 to 192321.12 for near-parabolic, and 135761.12 to 150161.12
for hyperbolic. Result hashes after the measured loops are identical. These
allocation increases and the elliptic/near-parabolic CPU results prevent
accepting a general performance benefit from this implementation.

(己, p=0.9) A source-level hypothesis is that moving locally computed primitive
`z` into unhinted function arguments introduced boxing; the one-argument
residual now also dispatches to its four-argument arity. This is a hypothesis
for a bounded correction, not attribution proven by these timing samples.
No source correction or additional cost run is authorized by this report.

(己, p=1.0) Both paused owned native JVMs recorded zero CPU during these cost
windows. Other host load remained. Aggregate busy fraction was 15.28% during
propagation versus 30.79% in the baseline, and 41.13% during phase0 versus
48.06%. These fractions include the measurement workload and are not a direct
external-load subtraction. Persistent desktop processes used measurable CPU;
endpoint census limits remain as stated in `load-summary.json`.

(己, p=1.0) Initial gas controls improved despite not exercising mature Kepler
work, which reinforces the need to separate load/order variation from causal
benefit. The 1000-particle mean is still 2.83 times the 16.6 ms budget. Named
isolated system means at 1000 were hydro-em 8.769 ms, neighbor-cache 7.504 ms,
structure 7.168 ms, integrator 6.893 ms, and gravity 6.666 ms; these are not a
parallel critical-path sum. All 45 named systems have five observations per
size in `named-segment-summary.json` and the original reporter log.

(己, p=1.0) `comparison.json` retains the full mean intervals and per-branch
CPU/allocation deltas. Raw `after-propagation.edn` and `after-phase0.edn` remain
unchanged. No speedup, native FPS improvement, or performance acceptance is
claimed. Root owns the next bounded implementation decision and any
reverse-order repeat resource window.
