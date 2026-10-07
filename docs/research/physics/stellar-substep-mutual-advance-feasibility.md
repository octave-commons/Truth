# Stellar substepping: mutual advancement before population expansion

**Domain:** physics | **Phase:** 0
**Date:** 2026-10-07 | **Author:** interactive Codex runtime scout
**Status:** draft — source observations and derived limits; no numerical run
**Owner:** [star-substep-heating](../../../kanban/tasks/star-substep-heating.md),
canonical Rheos status at the research-time read: `todo`, estimate `5`.
This pass made no status or estimate transition; later disposition belongs to
the canonical card and its event ledger.
**Source checkpoint:** `4f4eb63ec07ffd4678c3bb635f7c11e4b6c30223`.

## 1. Question and recovered contract

Can the existing single kinematic writer advance close stellar pairs mutually,
while their planets compose around those same advanced parents, without replacing
the Jacobi world or claiming a new cluster integrator from a state-set edit?
The [approved design §3.0](../../designs/multi-timescale-integration.md#30-coordinates-relative-jacobian-formulation--required-not-optional)
requires parent-relative motion and the parent's own advance. Its §9 amendment
adds stellar substepping, but does not reconcile that addition with §3.0's
assumption that every parent advances by Euler. The card also requires a real
isolated two-star-plus-planet energy regression, a historical heating comparison,
and 10,000-tick binary survival. Those remain unverified here.

**Derived conclusion:** the architecture permits a joint calculation inside its
one writer; the current per-entity algorithm and grounding do not yet specify a
correct general stellar extension. Recommend design clarification/breakdown
before implementation, not simply adding `:star` and `:protostar` to a set.
This recommendation does not change the card's status, scope or estimate.

## 2. Primary-source verification

- [Chambers (1999), §§1–3](https://academic.oup.com/mnras/article/304/4/793/1047461)
  describes a hybrid for a predominantly central-mass problem, moving strong
  encounter interactions into a conventional integration step. This does not
  establish an equal-mass cluster's independent nearest-neighbour rule.
- [Rein et al. (2019), §2 and Appendix A](https://arxiv.org/html/1903.04972v1)
  explicitly define democratic heliocentric coordinates about one distinguished
  star. Their Hamiltonian includes centre-of-mass, Kepler, interaction and jump
  terms; close encounters require more than independent two-body solves. Thus
  the card's claim that its heuristic is exactly MERCURY's method is unsupported.
- [Hernandez & Bertschinger (2015), §§2.1–2.2, equations 1–4](https://academic.oup.com/mnras/article/452/2/1934/1069988)
  provide an actual collisional pairwise construction: drift, undo each pair's
  drift, then advance both members through a two-body flow including their
  centre of mass. Ordered pair maps and reverse-order composition matter. This
  is useful grounding for mutual advancement, not authorization to transplant
  the complete method or claim independent frozen-body maps are equivalent.
- [Hernandez & Dehnen (2023), §2](https://arxiv.org/html/2301.06253v2)
  require reversible underlying maps and a selection condition checking both
  ends, with retry when needed. Merely freezing a step count at tick entry is
  not this construction. The historical notes misattribute this paper to Dehnen
  & Read; [Hernandez & Dehnen (2024), §4](https://arxiv.org/html/2401.07113v2)
  is likewise misattributed to Rein et al. and explicitly treats a dominant
  mass. Neither reference proves arbitrary nearest-star splitting in Truth.

These records and cited method sections were read on 2026-10-07. The 2015
publisher full text was accessible; the attempted arXiv HTML route failed.
Historical paragraphs are preserved, including their original attribution and
`validated` labels. This note supplies a later qualification, not a rewritten
record. Also, symplecticity does not imply time reversibility: a symplectic Euler
map is an immediate counterexample to that implication in the older research.

## 3. Actual source boundary

In [kinematics](../../../src/domain/integrator/kinematics.clj), lines 97–117
exclude stars from substep candidates; lines 130–139 already exclude a body's
own ID when selecting its nearest stellar parent. There is no new self-parent
bug to repair. Lines 273–292 always construct a compact body's parent state
using `euler-advance`, independently of how that parent might itself advance.
Both map and SoA paths use this helper (lines 419 and 536).

`dominance-gate` (lines 200–253) subtracts the pair pull from both bodies' summed
accelerations, using species softening and current drift-predicted positions.
But the summed channels are from the previous fan-out: see
[tick physics](../../../src/domain/genesis/tick.clj), lines 80–100,
[SoA prediction](../../../src/domain/physics/cache/soa.clj), lines 43–74 and
140–154, and [gravity](../../../src/domain/gravity/barnes_hut/force.clj),
lines 179–224. A newly improved trajectory need not land at that Euler
prediction. Pair subtraction therefore cannot be assumed to cancel exactly in
a real run merely because it has the right algebraic signs.

The gate-fail path also needs an explicit limit: `gravity-at` and
`embedded-substep` (lines 305–404) move one target through sources frozen at
snapshot positions. Refining that target's internal step does not refine the
other star's motion. It cannot be cited as an already solved mutual-encounter
fallback. External influence channels, velocity impulses, absorption and the
single frame shift still belong to the existing generic fold.

## 4. Derived obstruction and the isolated mutual limit

Consider two due stars, fixed masses, no impulses or absorption, and the ideal
limit of zero residual tide. Let \(r=x_1-x_2\), \(u=v_1-v_2\), and let
\((r_K,u_K)\) be their relative Kepler result with
\(\mu=G(m_1+m_2)\). Write \((x_i^E,v_i^E)\) for each ordinary Euler
prediction. If both stars independently use the current helper with reciprocal
parents, antisymmetry gives

\[
x_1'=x_2^E+r_K,\qquad x_2'=x_1^E-r_K,
\qquad r'=2r_K-r_E,\quad r_E=x_1^E-x_2^E.
\]

The velocity relation is identical: \(u'=2u_K-u_E\). The relative result
equals the desired Kepler result only when Euler already supplied it. For
unequal masses, the centre-of-mass defect is additionally

\[
R'-R_E=\frac{m_1-m_2}{m_1+m_2}(r_K-r_E).
\]

These are algebraic counterexamples to the proposed set extension, not measured
failures of an implemented stellar substepper. Current stars still use Euler.
Equal masses hide the centre-of-mass defect but not the relative-orbit defect.
A common recentering translation cancels from the relative equation.

For an isolated Newtonian pair the consistent reconstruction instead is

\[
R'=R+hV,\quad V'=V,\qquad
x_1'=R'+\frac{m_2}{M}r_K,\quad x_2'=R'-\frac{m_1}{M}r_K,
\quad M=m_1+m_2,
\]

with the same mass fractions for velocities. It advances the pair once and
conserves the isolated centre of mass. A planet then needs this actual parent
result: retaining the old parent predictor adds
\(x_p^E-x_p'\) to its intended relative position, and similarly for velocity.
The mass fractions follow from the centre-of-mass definition; they are not new
physical coefficients or an accepted production algorithm.

Finite stellar radii introduce another explicit boundary. The force channel
uses [pair-softened gravity](../../../src/law/stellar/orbital/dynamics.clj)
with \(\epsilon_{ij}=\max(R_i,R_j)\), while the analytic Kepler term is
Newtonian. Exact Newtonian two-body motion is an analytic limiting control,
not exact motion in that softened potential at arbitrary separation. Nor does
exact pair motion make a perturbed cluster exact at arbitrary global step.

## 5. Proposed reproduction, not an executed notebook

Reuse the actual spatial-index → SoA → parallel gravity/kinematics fold in
[the orbital regression suite](../../../test/domain/orbital/multi_timescale_regression_test.clj),
lines 80–145; exercise both existing kinematic adapters. Do not copy the solver
or mock the parent advance. A bounded investigation would be:

1. Start with isolated equal- and unequal-mass stellar binaries, then add a
   finite-mass planet in a well-separated hierarchical orbit. A concrete
   starting fixture can reuse the suite's solar/Jupiter constants and 5 AU
   planetary orbit, adding a second solar-mass star at 100 AU, with nested
   barycentric initial coordinates and velocities. These are declared test
   initial conditions, not measured live state or tuned game coefficients.
   Establish the physical reference's stability by convergence; separation
   alone is not a proof of long-term stability.
2. Use the suite's documented \(h=2.5\times10^9\) seconds and matched simulated
   horizons at \(h/2,h/4,\ldots\), refining until a reference is demonstrably
   resolved. Record every actual \(h\), path, gate, demanded/clamped substep
   count, pair separation and both raw/final parent states. A second larger
   historical step is a later test condition, not a fresh live observation.
3. Preserve cold-start behaviour (initially absent force channels), then report
   it separately from continued evolution with naturally carried channels.
   Record emitted versus consumed accelerations and their prediction epochs;
   do not preload a convenient force and call it the ordinary fold. Keep
   masses fixed and omit formation, accretion, intervention and collisions in
   this *isolated numerical fixture*, not in any claimed gameplay run.
4. Measure total three-body energy, momentum, angular momentum, mutual binary
   binding and planet-relative elements over common physical-time windows.
   For finite-radius controls use the matching pair potential outside the
   dead-zone, \(U_{ij}=-Gm_im_j/\sqrt{r_{ij}^2+\epsilon_{ij}^2}\);
   also report the Newtonian diagnostic separately. Stay outside cutoff, or
   declare that the simple potential expression is insufficient. Normalise
   energy error by a nonzero initial kinetic-plus-potential magnitude, not a
   nearly cancelling total energy. Recover inertial COM/angular diagnostics
   using recorded frame shifts, or explicitly use barycentric invariants.
5. A truthful RED is an observed coarse-fold loss of a reference-supported
   bound orbit or excessive invariant/trajectory error, with a tolerance
   justified by convergence and numerical/softening scale. Do not require
   every energy increment to be positive, fabricate the historical 25%, or
   assert an internal set membership as the behaviour. Retain the full
   10,000-tick acceptance as a later qualified run and measure cost separately.

No runner, test, chart or new numerical result was produced in this pass.

## 6. Historical evidence and validation limits

The [July heating report](cluster-dispersal-integration-heating.md) cites
`scratchpad/cluster_probe.clj`, `scratchpad/probe_analyze.clj`, `scratchpad/base.edn`
and `scratchpad/coarse.edn`. At the source checkpoint, `git ls-tree` finds none;
`git log --all -- <those paths>` and the two probe-name path globs return no
commits in the available local refs. The report's introduction commit is
`cbe80dd`; `git show cbe80dd:scratchpad/cluster_probe.clj` also fails. This is a
bounded recovery result, not proof that the author's untracked files never
existed. Exact old seeds, full samples and the 25%/48% calculations cannot be
reproduced from these cited artifacts here.

Stellar-subset energy in a gas-filled evolving world is not an isolated
conserved quantity: external gravitational work and changing masses can alter
it even while membership is constant. Thus the old table motivates a controlled
investigation but does not alone isolate numerical heating. Preserve it as a
historical report; new isolated evidence must carry its own source and inputs.

Verified in this pass: source composition, canonical card state, primary-source
method/author checks and the symbolic limiting equations. Not verified:
quantitative heating, 10,000-tick survival, fixed or variable-step error bounds,
runtime cost, natural planet survival or playable approach.

## 7. Promotion boundary

Internal mutual advancement is compatible with one immutable input and one
position/velocity write-set. A correct isolated pair is therefore feasible
without another ECS writer or global barrier. General promotion still needs
an explicit choice for disjoint pairs versus larger encounter groups, parent
changes, overlapping nearest-neighbour relations, LOD due sets, and finite
planet back-reaction. It must specify how all children reuse the parent's
actual output, how current versus carried pair forces are removed once, and
how the moving-source gate-fail path is handled. A naive all-star all-pairs
method also needs a bounded cost argument before reaching production.

The smallest useful next admission is the source-pinned isolated reproduction
above, followed by a reviewed design decision on these coupling boundaries.
This note selects no production solver, threshold, new clock, coefficient,
state-set extension or fallback. The existing Todo5 card remains the owner;
its current implementation recipe needs clarification before it is executable.
