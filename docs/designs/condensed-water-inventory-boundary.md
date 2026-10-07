# Condensed-water allocation and finite inventory boundary

**Status:** proposed design, 2026-10-07; not implementation admission.
**Owner:** `a4744132-2328-4cb2-a550-e1328d346673`, Incoming, 3 points.
**Scope:** [canonical design continuation](../../.ημ/diagnostics/condensed-water-research/design-scope.json).
**Grounding:** [condensed-water material-budget research](../research/physics/2026-10-07-condensed-water-material-budget.md),
especially §§2–3 and 7; published research revision
`8f9ee36b8848e046e40eb5fb36ab4cbab5ab6946`.
**Inspected production source:** `b395c4049718f7ce015ddf25fc0a192d97821373`.

## 1. Outcome and authority

Choose a pure, finite accounting boundary that can represent hydrogen bound in
water without treating free nebular hydrogen as a refractory grain. The result
is an allocation plan and a conserved extraction, not a new material producer.
No current world gains water, an atmosphere, accessible biological substrate,
or represented life because this document exists.

The research verifies why oxygen competition, common-basis quantities and phase
weights matter. Its exact toy establishes accounting identities only. Lodders'
182 K result has a specified pressure/model; Bitsch and Battistini's 150 K is a
model prescription; neither silently replaces Truth's 170 K snowline. D'Angelo's
surface adsorption model does not justify instantaneous bulk hydration. This
design chooses supplied reservation and phase inputs rather than adopting any
of those as a new production chemistry or temperature law.

Existing [single-writer rules](../../AGENTS.md#no-special-cases--everything-rides-the-uniform-path)
and [research → design → task admission](../../PROCESS.md#grounding-research--design--task)
remain controlling. The earlier [parcel-disk rework](planetary-disk-rework.md)
is related architecture history, not an architecture change selected here.
The completed grain-filter and retention-capability contracts are not reopened.

## 2. Selected reference contract

The following are **proposed engineering choices**, selected for this bounded
design rather than left as interchangeable options. They are not accepted
production behavior or measured physical calibration.

| Boundary | Selection |
| --- | --- |
| Element vocabulary | The existing 17 keys in `law.composition/element-set`; no molecule key in elemental stock |
| Quantity | Elemental mass in kg; each stock/compartment map explicitly contains all 17 elements |
| Reference arithmetic | Exact nonnegative rationals; exact comparisons and recombination, no epsilon or normalization repair |
| Reservation chemistry | Caller-supplied elemental allocations to other solids and gases, with explicit model/version and provenance |
| Reaction extent | Supplied exact `η ∈ [0,1]`; `η=1` denotes capacity closure unless independently supported as actual reacted material |
| Water phase | Supplied exact ice fraction `f ∈ [0,1]`; the complement is allocated vapor, with explicit phase-model/version and provenance |
| Residual matter | An explicit unassigned compartment; never silently gas, solid or extra water |
| Extraction | Homogeneous proportional draw from each already allocated phase, bounded separately by that phase's finite mass |
| Mutation | None: return a complete plan or a rejection; no ECS writes, callbacks, I/O or side ledger |

Use integers or exact ratios in the reference arithmetic. For a portable data
boundary, a rational is a reduced `[numerator denominator]` pair of integers,
with positive denominator; zero is `[0 1]`. Decimal numerals below are exact
finite-decimal quantities for readability, not binary64 literals. A future
named Malli input validator must reject floating-point/NaN/infinite values,
negative stock, malformed ratios and unsupported element keys. Do not silently
rationalize current doubles or reuse their tolerance as a conservation law.

The input identifies the supplied donor and stock revision as opaque data,
the allocation model/version, and reservation/extent/phase provenance. These
identifiers are echoed unchanged; the pure kernel cannot verify that an ECS
snapshot is current or that supplied chemical history is physically valid.
No function supplied in the input is executed. Model identifiers distinguish
claims; they do not authorize arbitrary plugins or validate those claims.

A missing or explicitly unknown donor stock returns **unsupported stock**;
it never reads host-star composition, assumes solar material, or substitutes
zero. A present malformed stock is **rejected**. A present all-zero stock is
known empty and can produce a zero plan. Metadata and scalar inputs are still
validated for an empty stock. Result shapes distinguish unsupported, rejected
and planned outcomes; only a planned result carries extractable quantities.

Future validators must have named input, allocation and extraction contracts
in `law/`; pure calculations belong in `domain/`. No namespace or implementation
stub is introduced by this document. Binary64 conversion, residual disposition,
cost and integration benchmarks are separate admission prerequisites.

## 3. Allocation without double counting

Let `b_e` be the supplied elemental stock. Let `rˢ_e` and `rᵍ_e` be the supplied
allocations to **other** solids and gases. These are allocations of `b`, not
additional mass and not previously promised outgoing transfers. Require:

\[
0\le r^s_e+r^g_e\le b_e,\quad a_e=b_e-r^s_e-r^g_e.
\]

The reservation producer must account for competing compounds and their
stoichiometry. This kernel proves only the supplied elemental bounds; it does
not infer CO, silicates, methane, ammonia or hydration from scalar categories.
Previously outstanding transfer demands must already be removed from the
available stock by its future settlement owner, not counted as reservations
that remain drawable here.

Use the research's explicitly rounded model weights as exact constants:
`A_H=126/125` and `A_O=15999/1000` kg/kmol. `:D` and `:He3` remain separate
untouched elements; no isotope exchange is inferred. Then:

\[
q_{max}=\min(a_H/(2A_H),a_O/A_O),\quad q=\eta q_{max},
\quad q_i=fq,\quad q_v=(1-f)q.
\]

Water ice and vapor own elemental maps `wⁱ` and `wᵛ`, with H amounts
`2 A_H qᵢ`, `2 A_H qᵥ` and O amounts `A_O qᵢ`, `A_O qᵥ`; all other entries
are zero. Define `u_e=a_e-wⁱ_e-wᵛ_e`. Independently validate:

\[
b_e=r^s_e+r^g_e+w^i_e+w^v_e+u_e,\qquad
r^s_e,r^g_e,w^i_e,w^v_e,u_e\ge0.
\]

The plan retains all five compartment maps and their provenance. Its total
mass is `M=Σ b_e`, derived from stock rather than an independently adjustable
field. If a future caller also supplies a physical donor mass, its agreement
and interpretation must be checked at that adapter. `H2O-kg` is only the sum
of water's allocated H and O; adding it to those elements would count twice.

This allocation establishes **capacity under supplied assumptions**. Even a
nonzero plan does not establish actual water inventory. Promotion to an
inventory fact requires an admitted stock/chemistry producer and settlement.
Reapplying allocation to the original stock is not a way to refill a spent
donor; sequential extraction uses the returned remaining compartments.

## 4. Finite phase extraction

The known allocated phases are `s=rˢ+wⁱ` and `g=rᵍ+wᵛ`, with
`S=Σs_e` and `G=Σg_e`. Unassigned `u` is not drawable. A request supplies
exact masses `m_c` (solid/core draw) and `m_g` (gas/envelope draw). Before any
division require:

\[
0\le m_c\le S,\qquad 0\le m_g\le G.
\]

For a zero phase, its draw must be zero and its draw fraction is zero; no
normalized composition is produced for that phase. Otherwise `α=m_c/S` and
`β=m_g/G`. Extract `α` of **each** solid compartment and `β` of each gas
compartment, retaining the individual water/other-material identities in both
child and remainder. Thus:

\[
d_e=\alpha(r^s_e+w^i_e)+\beta(r^g_e+w^v_e),\quad
b'_e=b_e-d_e,
\]

\[
b'_e=(1-\alpha)r^s_e+(1-\beta)r^g_e+
(1-\alpha)w^i_e+(1-\beta)w^v_e+u_e.
\]

The output must independently satisfy `b=b′+d`, nonnegative compartments,
child mass `Σd=m_c+m_g`, and phase-specific remaining amounts. Normalize a
positive child's `d` only as a derived composition. A zero draw is a successful
no-op with zero child mass and no child composition; it does not request an
entity birth. No allocation of the unchanged `u` is invented during drawing.

Reject the whole request if either phase overdraws, even when every `d_e≤b_e`
would hold. No clipping, borrowing from the opposite phase or partial draw.
Validate the supplied partition before extracting, including recombination
with its stock and model identity. Each water compartment must contain only
H and O in the exact `2 A_H : A_O` mass ratio (or be all zero); total balance
alone cannot certify a tampered water label. Recheck these identities on the
child and remainder. Original allocation parameters remain provenance, not a
request to recompute reaction extent on a depleted remainder. A serial batch
applies requests in declared input order to each returned remainder; it is not
permutation-invariant.
No sorting by entity ID, hidden priority or retry deduplication is added here.

The kernel's immutability does **not** provide runtime atomicity or prevent a
caller from submitting the same stale stock twice. Future uniform settlement
must verify donor identity/revision, available stock and all competing demands,
then account for child creation/removal without stranded debit. That missing
integration cannot be replaced by a rich cross-entity routing event.

## 5. Four exact acceptance traces

These are constructed accounting cases, not planets or empirical chemistry.
Unlisted elements in this display are explicitly zero in the full input maps.
All successful results are independently recombined; rejection changes none
of the supplied stock or compartment values.

### A. Oxygen-limited capacity and a bounded draw

Stock is `H=2.016`, `O=23.9985`, `C=12.011` kg, total **38.0255 kg**.
The supplied `trace/co-reservation-v1` gas reservation is `C=12.011`,
`O=15.999` kg; it illustrates oxygen competition, not a selected nebular model.
With `η=f=1`, residual O is 7.9995 kg, so `q=1/2` kmol. Ice owns
`H=1.008`, `O=7.9995`, total **9.0075 kg**; gas reservation totals **28.01 kg**;
unassigned H remains **1.008 kg**.

Draw half of each allocated phase: `m_c=4.50375`, `m_g=14.005` kg.
Child elements are `H=0.504`, `O=11.99925`, `C=6.0055`, total **18.50875 kg**.
Donor remainder is `H=1.512`, `O=11.99925`, `C=6.0055`, total **19.51675 kg**.
The residual donor retains all 1.008 kg of unassigned H. Their sum is the
original stock element by element. No oxygen reserved to CO was reused as water.

### B. Known zero H is not unknown inventory

Set H to zero in A and keep its explicit O/C stock and gas reservation.
Total stock is **36.0095 kg**, `q=0`, allocated water/solid mass is zero,
reserved gas is 28.01 kg and unassigned O is 7.9995 kg. A zero draw returns
the same stock and no child composition. Any positive solid draw rejects.
An absent stock, in contrast, returns unsupported; neither outcome invents H.

### C. Element bounds alone cannot authorize a phase draw

Stock is one kmol of model water: `H=2.016`, `O=15.999`, total **18.015 kg**,
with no other reservations, `η=1`, `f=1/10`. Solid mass is **1.8015 kg** and
gas mass **16.2135 kg**. Both phases have identical normalized elemental ratios.
Request `m_c=3.603`, `m_g=0` kg. Its hypothetical `H=0.4032`, `O=3.1998`
fits the donor's overall elemental bounds, but needs twice the available solid.
The result is **phase-overdraw rejection**, with every input quantity unchanged.
The supplied mixed-phase fraction is an arithmetic input, not an equilibrium
or temperature claim.

### D. Sequential exhaustion does not replenish water

Use C's original stock with `f=1`. First draw **6.005 kg** of solid:
child `H=0.672`, `O=5.333`; remaining `H=1.344`, `O=10.666`, total **12.01 kg**.
Draw that remaining 12.01 kg next; the returned stock and all compartments are
exactly zero. A third request for **0.001 kg** of solid rejects. The two children
sum to 18.015 kg and exactly the original H/O. Reallocating the old input would
be stale input reuse, not an allowed step of this trace.

The planned reference-law verification also covers malformed ratios, unsupported
keys, negative stock/reservations/draws, over-reservation, out-of-range η/f,
zero phase division, untouched elements, and tampered partitions. These are
future executable law tests; the accompanying design audit records only bounded
offline rational arithmetic and source/link checks, not Clojure test execution.

## 6. Actual inflow/outflow owners and missing integration

This table inventories the current disk material paths at the inspected source
revision. “Required later” is a prerequisite, not an implemented component or a
new writer admitted by this design. Reads and writes must remain declared in
the [registry](../../src/domain/ecs/registry.clj), particularly lines 345–370.

| Path | Current source and owner | Required later for spendable elemental stock |
| --- | --- | --- |
| Initial parcel and resolved-body material | [stellar/seeder](../../src/domain/stellar/seeder.clj), lines 31–68, puts supplied/fallback composition and mass on a new clump; it initializes no disk inventory | Explicit origin and interpretation for a new disk account; existing positive disk mass without provenance remains unsupported, not star-composition bootstrap |
| Whole captured body → disk | [stellar/sink](../../src/domain/stellar/sink.clj), lines 162–196, emits mass/position/velocity/angular momentum/route without composition; [disk evolution](../../src/domain/stellar/disc_evolution.clj), lines 213–228, folds disk-routed mass and angular momentum | Donor elemental debit and matching disk supply, correctly separated from direct-to-core capture; no implicit solar packet |
| Gradual gas → disk | [mass-transfer/add-disk!](../../src/domain/mass_transfer.clj), lines 50–60, emits disk mass/angular-momentum flux; disk evolution lines 229–248 folds them | Matched elemental quantities alongside existing uniform donor/sink influences, with one declared writer per component |
| Disk → stellar core, viscous | Disk evolution lines 271–292 debits disk mass and emits `mass-flux-disk`/`torque-disk` for integrator consumption | Same finite donor accounting and a corresponding stellar composition blend; physical mass must not be added a second time |
| Disk → binary companion | Disk evolution lines 324–368 debits companion mass and copies host composition into `spawn-request-disk` | Finite elemental draw matching this child, plus settlement/lifecycle consistency |
| Disk → GI gas-giant fragment | Disk evolution lines 381–428 debits embryo mass and also copies host composition | Same accounting, independently of the later one-shot planet-seeding route |
| Disk → core/envelope planets | [planet-seeds](../../src/domain/planet_formation/seed.clj), lines 43–144, uses host-star composition and carries only disk mass/angular-momentum remainders; disk evolution lines 443–462 applies returned debits after viscous evolution | Carry remaining compartments across ordered annuli and prior same-pass consumers; bound each phase and reconcile core/envelope draws with actual child mass |
| Planet mass proposal versus debit | [planet-mass](../../src/domain/planet_formation/physics.clj), lines 139–154, returns a core/gas split and separately clamps total seed mass | Require selected draws to sum to the final child/debit mass; do not normalize away a mismatch |
| Spawn materialization and parent removal | [genesis/bootstrap](../../src/domain/genesis/bootstrap.clj), lines 299–352, materializes requests then reaps `consumed.*`; no new inventory settlement exists there | Resolve same-fold consumed donor, rejected creation and retry semantics before implementation; `alive?` alone cannot establish donor survival |
| Direct capture, collisions and host loss | [integrator composition](../../src/domain/integrator/core.clj), lines 215–253, explicitly initiates merge blending; lifecycle reaping deletes entity components | Account disposition if a disk host merges/is consumed/escapes, without inventing transport, silently deleting stock, or treating direct core mass as disk supply |
| Reported total material | [genesis/summary](../../src/domain/genesis/summary.clj), lines 21–42, reads body mass and disk mass separately | A disk elemental inventory describes its existing disk mass; it is not additional mass in summaries or gravity |

The current disk regime and grain-filter projections are not missing inflow
owners; they are derived quantities. They must not manufacture stock. The
separate [parcel-condensation path](../../src/domain/stellar/seeder.clj), lines
96–129, copies its parent composition and emits a mass debit; it does not
establish selective water extraction from the aggregate disk reservoir.

The table does not claim all existing paths already conserve every element.
It prevents a later water integration from covering only planet births while
ignoring competing disk consumers, host removal or source composition. Keeping
one ECS, the existing influence mechanism and ordered lifecycle is mandatory;
adopting the older parcel-disk architecture would require separate review.

## 7. Admission and observable limits

This Incoming 3-point continuation is complete as a **reviewable design** when
the chosen reference contract, four traces and source-owner table withstand
review. The original research and card remain unchanged; a planning document
does not move a feature to Ready or validate native material eligibility.

Later implementation work must separately admit: named law schemas and pure
kernel tests; stock origin and all elemental transfer/settlement ownership;
numeric adapters with an explicit residual policy; chemistry/phase producers
with applicable conditions and clocks; inventory-aware presentation preserving
unknown; and normal-formation donor/child evidence. Their ordering follows those
dependencies, not the presence of this document. No new cards are created here.

Retention capability remains the existing escape/material classifier. Even a
settled positive inventory would not prove atmospheric pressure, liquid water,
surface accessibility or biological use. This design supplies no hydration law,
phase jump, debug world, Gate shortcut, new renderer, or organism producer.

The pure law has a fixed small elemental vocabulary; that is not a timing
measurement. Runtime performance, full tests, strict gates and native formation
remain unexecuted for this design-only continuation.
