# Proposed three-point follow-up: reuse Newton iteration terms

(己, p=0.99) Existing `perf-tick-residual-gap-to-60fps` authorizes profiling
followed by one bounded pure optimization. This proposal fits its ≤5-point
limit, estimated at 3 points. It is a proposal only: root must accept the exact
scope before tests/source implementation begins. Canonical search returned no
matching Kepler task; that is not proof of corpus-wide absence. The known
existing perf card remains the planning owner.

## Exact redundant work and permitted change

(世, p=1.0) In `src/domain/orbital/kepler.clj`, `universal-anomaly` defines
separate residual `F` and derivative `dF` closures. A Newton iteration first
evaluates `F(χ)` and, when not converged, evaluates `dF(χ)` at the identical χ.
Each independently computes `z = αχ²` and `[c2,c3] = stumpff(z)`. The negative-z
branch evaluates `cosh` and `sinh`; positive z evaluates `cos` and `sin`; the
near-zero branch evaluates the same power series. The live profile contains
593 Java stacks in the Kepler namespace and 89 leaf `StrictMath.cosh` samples.
Source inspection establishes the repeated work; the samples do not measure
how many duplicate evaluations occur.

The existing equations are:

```
F(χ)  = χ³c3 + (rv/√μ)χ²c2 + r0χ(1 − zc3) − √μ dt
dF(χ) = χ²c2 + (rv/√μ)χ(1 − zc3) + r0(1 − zc2)
z     = αχ²
```

(己, p=0.99) Compute the iteration's z/c2/c3 once and let both expressions
consume them. Preserve each expression's existing floating-point arithmetic
order. Keep derivative evaluation conditional on the existing nonconverged
branch. Preserve bracket expansion, initial guess, Newton/bisection decision,
sign handling, tolerance, caps, exception messages/data, returned χ and the
final `fg-state` computation. Do not change substep K, h, tidal kicks, parent
composition, softening, frame offsets, time pacing or entity admission. Do not
introduce a cross-call cache, approximation, new writer or alternative solver.
The existing pure namespace is the intended seam; this does not need a new
runtime adapter or framework.

(己, p=0.99) The accepted design in
`docs/designs/multi-timescale-integration.md` §§3.1–3.4 requires the same analytic
Kepler drift, frozen substeps and composition. Reusing an identical intermediate
within the same solve preserves that contract. This proposal does not rely on
the relaxed density-cache window tolerances; demand bitwise trajectory equality
for this solver change.

## Evidence and acceptance before implementation

1. Capture baseline public `propagate` output bits and short repeated trajectories
   from the pinned parent for a finite matrix: circular and eccentric ellipses,
   near-parabolic states on both sides of α=0, hyperbolic escape/infall, positive
   and negative dt, zero dt, differently scaled μ/r/v, and rotation away from one
   coordinate axis. Preserve input data, source SHA and oracle output bits. Do
   not hand-copy the old solver into production or silently relax failed cases.
2. Add meaningful behavioral contracts against those frozen results, plus the
   existing independent analytic ellipse, energy/angular-momentum, reversal,
   compact-body stability and real integrator/SoA composition checks. Zero-dt and
   invalid/degenerate failure behavior must remain unchanged. Whole compact ECS
   trajectories must match at every checked step, not merely at a final aggregate.
3. Establish the performance RED truthfully: the old implementation is already
   numerically valid, so numerical tests should pass baseline. Record duplicate
   expensive evaluation on representative real public calls and baseline timing
   and allocation. A supplementary work-count regression may expose the repeated
   evaluation, but it is insufficient by itself. Do not invent a wrong-physics
   RED or use a fragile wall-clock threshold as a unit test.
4. Use existing Criterium machinery for source-pinned before/after propagation
   batches covering all Stumpff branches and nontrivial Newton steps. Add only a
   small benchmark group/workload through the existing registry if the current
   groups do not exercise this seam. Warm equally; consume returned results;
   alternate paired runs; retain CPU/allocation and dispersion. A branch-dependent
   slowdown or new allocation cost is a finding, not something to average away.
5. Run the unchanged `bin/bench :phase0` as a domain control and a real compact
   integrator workload as the exercised hot-path check. An early gas-only nebula
   does not demonstrate Kepler improvement. Coordinate exclusive owned load and
   record other processes; do not claim success if the delta remains noise. Full
   tests/strict gates and independent math/source review follow the usual root
   checkpoints. No native world mutation is needed for any of these proofs.

(己, p=0.98) Expected impact is reduced repeated solver work, not removal of the
dominant software-rendering CPU cost. The existing profile cannot predict an FPS
gain or establish a 16.6 ms tick budget. If exact reuse needs a broader API or
solver rewrite, stop and rescope rather than expanding this three-point slice.
