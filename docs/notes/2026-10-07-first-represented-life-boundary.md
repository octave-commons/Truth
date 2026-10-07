# First represented life: cohort boundary and open contracts

**Status:** proposed comparison and decision boundary, not an accepted biological
law or implementation admission. Sections 6–8 select an origin/account contract
for review; rate-driven metabolism remains outside that bounded contract. **Owner:**
[`life-to-represented-actor-spec`](../../kanban/tasks/ground-the-first-causal-life-to-represented-actor-boundary-tor-spec.md),
Incoming, 3 points. Source audit: PR12 base
`98b847ce75491bdccacf2ec3a7476d451270bf49`; primary texts accessed 2026-10-07.

The proposed first representation is a persistent **microbial cohort** that
consumes a local resource and retains causal history. It represents living stock,
not one organism, a thinking character or an avatar. Its admission partitions
accounted carbon at finer resolution. The selected
origin prescription in §6 converts a bounded portion of substrate to living
stock when recorded conditions qualify; it does not explain abiogenesis or turn
a scalar phase flag into new matter. This narrows one prerequisite toward
embodiment without claiming that embodiment or a Gate follows from it.

## 1. Authority and current source

| Tier | Evidence | Consequence |
| --- | --- | --- |
| Decided architecture | [Resolution regimes §§3–6](../designs/resolution-regimes-and-scale-coupling.md#3-principle-scale-separated-regimes-nested-as-ecs-content-layers): one ECS, biome/biomass detail and conserved nested budgets | Retain a planet-local budget; do not subtract microscopic mass from the astronomical `c/mass` value. The budget and aggregate/detail coupling are not built yet. |
| Product intent | [World generation Phase 2](../designs/gates-of-truth-world-gen-phases.md#phase-2-emergence-of-life) and Phase 5 in the same document | Environmental opportunity precedes represented life; an actual historical individual remains a later prerequisite for temporary player control. |
| Unresolved earlier research | [Simulation methods: Open Research Questions](../designs/simulation-methods-research.md#open-research-questions), especially its minimum viable agent question | An agent-based bibliography does not select admission, resource or identity rules. The [biosphere FSM](../research/physics/nebula-to-life-fsm.md) is explicitly draft, with different proposed state names. |
| Executable scalar model | [Ecology state](../../src/domain/ecology/state.clj), lines 42–64; [schema](../../src/law/ecology/schema.clj), lines 57–69 | Normalized biomass/complexity and phase history are not kilograms, cell counts, genomes or a nutrient reserve. The ladder ends at `:complex`. |
| Executable owner and cadence | [Ecology system](../../src/domain/ecology/system.clj), lines 18–22 | One `c/ecology` writer advances every 24 physics ticks. Its fixed-60-Hz wall-time comment is not a clock contract. Initial temperature/volatile proxies do not provide a continuously sampled local habitat. |
| Executable creation boundary | [Bootstrap](../../src/domain/genesis/bootstrap.clj), lines 261–352; [stellar seeder](../../src/domain/stellar/seeder.clj), lines 31–68 | Every current spawn request becomes a stellar clump with physical components. `extra-components` does not make this a generic biological producer. Spawning precedes reaping. |
| Executable identity/event facilities | [ECS core](../../src/domain/ecs/core.clj), lines 24–49; [events](../../src/domain/ecs/event.clj), lines 11–35 and 75–89 | Entity allocation and causal event fields exist; lineage identity, admission deduplication and deterministic biological event IDs do not. Default random event UUIDs are not a replay policy. |
| Observed natural milestone | [Two-seed formation note](2026-10-06-two-seed-natural-formation.md) | Recorded natural runs reached scalar prebiotic→prokaryotic transitions. Those observations did not produce organisms or validate the model proposed here. |

The [proposed Grow continuation](../designs/commitment-and-resonance.md#441-committed-world-grow--bounded-proposal-2026-10-07)
changes scalar ecology only. It neither supplies this budget nor becomes an
invented prerequisite for autonomous life. `c/biome-cell` and `c/civilization`
in [components](../../src/domain/ecs/components.clj), lines 311–318, are declarations,
not producers.

## 2. Two minimal representations

| Criterion | Cohort: provisionally preferred | Individual microbe: deferred alternative |
| --- | --- | --- |
| Causal continuity | Persistent cohort identity, origin and resource history; explicitly no individual biography | Individual birth, parentage and loss can establish organism history, but not cognition or social history |
| Minimum state | Habitat reference, tracked living stock, admission/history identity and resource-use parameters | Those commitments plus individual size/division and spatial occupancy policies if claiming individual interactions |
| Determinism | Stable admission key, ordered resource allocation and persisted state can make replay testable; none exists yet | Also needs reproducible division/placement choices; more opportunities for ordering dependence |
| Cost | Target work proportional to active habitats/cohorts, with a reviewed materialization bound | Work scales with represented individuals and any neighbor/transport model; do not promise a numerical speed ratio |
| First observable behavior | One inspector/Narrator entry reports a named cohort consuming stock and changing its living budget | One visible organism grows/divides; needs a spatial representation and rendering semantics not supplied by today's scalar model |

Jayathilake et al. (2017), *A mechanistic Individual-based Model of microbial
communities*, models cell growth, division, decay, nutrient transport and
mechanical interactions. Its methods separate biomass kinetics, agent division
and nutrient balance. This is primary evidence for the additional choices in an
individual model, not a requirement to import that simulator, its thresholds or
its terrestrial calibration. [Full research article, Methods](https://journals.plos.org/plosone/article?id=10.1371/journal.pone.0181965).

The cohort preference is a project scope judgment. It supplies a persistent
ecological unit with one action; it does **not** satisfy the eventual requirement
for a historically produced controllable individual. Pure density fields remain
the aggregate representation; renaming a field “actor” would not meet this card.

## 3. Resource-use candidate and its limits

Monod (1949), *The Growth of Bacterial Cultures*, distinguishes biomass density
from cell counts (pp. 371–373), discusses nutrient-limited yield (pp. 379–381),
and presents an empirical saturating growth relation (pp. 383–384, equation 2).
The treatment does not supply a death or abiogenesis law; it does not establish a
universal extraterrestrial growth rate. Its doubling-rate convention must be
converted before use as a natural-exponential rate.
[Original text](https://garcialab.berkeley.edu/courses/papers/Monod1949.pdf).

**Proposed model choice, not a recovered acceptance:** begin with one locally
well-mixed, organic-carbon-limited compartment. Track carbon mass, rather than
total organism mass, in a closed accounting example:

| Symbol | Proposed quantity and unit | Required provenance |
| --- | --- | --- |
| `U` | Unrepresented, already living carbon, kg C | Selected origin transfer in §6; never a conversion from normalized biomass |
| `S` | Available limiting substrate carbon, kg C | Finite accessible partition selected in §6; not an infinite food source |
| `B` | Carbon in the represented cohort, kg C | An admitted partition from `U`, then accounted resource use/loss |
| `W` | Other accounted, unavailable carbon, kg C | Retains non-biomass products/lost cohort carbon; not an automatic recyclable-food pool |
| `V` | Compartment volume, m³, finite and positive | For a later concentration/rate model; not required by the selected extent-only origin transaction |

All stocks `U/S/B/W` must be finite and nonnegative. A representation birth
requires a finite partition `0 < b <= U`, debiting `U` and crediting the new
cohort by the same amount; it cannot borrow against future growth.

A candidate reference rate is `mu(s) = mu_max * s / (K_s + s)`, with
`s = S/V`, `mu_max` in s⁻¹ and `K_s` in kg C/m³. For a source parameter expressed
as doublings per unit time, natural-exponential `mu = ln(2) * R`, followed by the
time-unit conversion. No parameter value is adopted here. Applicability requires
that the chosen substrate actually limits growth and other necessary conditions
are satisfied; today's habitability proxy does not establish that.

The following **accounting constraint** is proposed independently of a numerical
integrator. An accepted action consumes `q` kg C, puts `g = Y_c*q` into living
stock, and accounts for a separately justified loss `L`:

```text
0 <= q <= S;  0 < Y_c <= 1;  0 <= L <= B + g
S' = S - q
B' = B + g - L
W' = W + (1 - Y_c)*q + L
U' = U
U' + S' + B' + W' = U + S + B + W
```

`Y_c` is a proposed carbon-retention fraction, not Monod's dry-biomass yield
copied into a different unit. These equations conserve the tracked carbon only;
they do not close oxygen, other elements or energy. The compartment must not be
presented as a calibrated biosphere. A rate-to-`q` integration rule, maintenance,
loss, transport and boundary exchanges remain unselected. A saturating rate or
nonnegative cap alone does not justify one huge cosmological timestep. No
per-24-ticks biological rate, wall-second shortcut or new clock is introduced.

## 4. Producer, identity and lifecycle requirements

The proposed producer is one pure `domain.biology` content layer, **not present
today**, with named `law/` schemas and declared registry reads/writes. Its proposed
single-writer allocation covers the local budget and cohort state together;
existing ecology keeps sole ownership of `c/ecology`. Physical boundary fluxes
must use the existing uniform influence mechanism, not direct `c/mass` writes.
No second ECS, render engine, mutable side ledger or bespoke tick loop is proposed.

The existing lifecycle mechanism would need a reviewed generalization to create
validated component bundles without passing through `spawn-clump`. The proposed
semantic contract is one accepted birth and one living-stock partition per
admission key, or neither. A fan-out request is not a completed birth. An ECS ID
may be allocated at materialization; a durable admission/lineage identity must
survive retries, loss and LOD collapse independently of that transient ID. Section 7 selects
key shape, incarnation rules and deterministic ordering for review.
Never reconstruct a new history from `:prokaryotic`, a reset scalar, or zoom alone.

Current [tick ordering](../../src/domain/genesis/tick.clj), lines 167–196, folds
physics, expires interventions, materializes/reaps, and only later emits ecology
phase events. Admission cannot consume a same-tick event that has not been emitted.
The next eligible snapshot or an explicitly reviewed ordering is required.
At materialization, `ecs/alive?` alone is insufficient: a parent carrying a reap
marker is still alive before `reap-consumed`. Reject/cancel a pending birth whose
habitat will be removed; do not leave an orphan or a stranded stock debit. The selected serial
settlement in §7 makes this an atomic lifecycle decision, not an assumption that
the event API already supplies it.

Budget origin/version, habitat identity, cause, modeled time interval, accepted
flux and rejection/loss reason must remain inspectable. Event cause links use
existing ledger semantics, with an explicit future deterministic identity policy.
The renderer/menu only projects these facts; selecting or zooming a planet cannot
fund a birth, reset history or manufacture a character. LOD collapse must retain
the aggregate **and** enough identity/history/state to resume causal continuity;
aggregate mass plus a fresh seed alone does not recover prior lived interactions.

## 5. Worked semantic traces (illustrative quantities, not calibration)

Each row is a proposed contract example, not an executed test. Values are kg C;
`Y_c = 1/2` below is arithmetic illustration only. `U+S+B+W = 10` initially.

| Trace | Before / request | Required result |
| --- | --- | --- |
| Valid representation birth | `U=2, S=8, B=0, W=0`; extant valid habitat, accounted living origin, new key `k`, partition `b=1` | `U=1, S=8, B=1, W=0`; one cohort and birth event causally linked to the origin; no matter created |
| Resource-use action | Previous result; a future reviewed integrator admits `q=2`, `L=0` | `U=1, S=6, B=2, W=1`; one accepted action record names the resource debit and living gain |
| Lack of resource | A valid state with `S=0`; request positive consumption | `q=0`, no growth from nothing; existing cohort/history retained. Starvation death is not inferred without a loss law |
| Invalid habitat/data | Missing habitat, invalid volume, nonfinite/negative stock, excessive birth partition, or a failed reviewed habitat predicate | No birth/consumption/debit; explicit reason. Invalid habitat does not itself authorize instant death |
| Repeated admission | Retry key `k` after the birth above | No second partition, entity or birth event; return/retain prior result. Loss or LOD does not erase deduplication evidence |
| Cohort loss | Action result above; a reviewed loss cause admits `L=2`, `q=0` | `U=1, S=6, B=0, W=3`; retire the cohort through its own lifecycle marker, retaining loss/cause/history; no immediate food recycling |
| Same-fold habitat removal | Pending birth for a parent already marked for reaping | Cancel/reject before irrevocable partition; no orphan, no stranded debit, one inspectable rejection outcome |

Materialization into a new entity remains invisible to other systems in the
requesting frozen snapshot. Resource competition must resolve against one budget
with deterministic allocation, not let several cohorts each spend the same `S`.
The arbitration, deduplication and parent-loss rules selected in §7 apply;
these earlier arithmetic examples remain illustrative rather than calibration.

## 6. Selected origin/account prescription — proposed v1

This section replaces the unselected engineering options from planning commit
`e2d6893df3fd1505587300cb9ea687350205c2b9`; that historical proposal and its
review receipts remain in Git. The primary-source comparison above is unchanged.
This is a concrete model for review, not a claim of biological validity or Ready
status. No further user preference is required to select these mechanisms.

The earlier [Phase-2 resolution limit](../designs/simulation-methods-research.md#the-honest-resolution-limit)
already permits a conditions-driven sub-grid prescription. We therefore do not
wait for first-principles abiogenesis. We retain [the decided nested-budget
contract](../designs/resolution-regimes-and-scale-coupling.md#6-coupling-contract-aggregate--detail):
one ECS, coarse matter constraining finer detail, no tiny debit from astronomical
floating-point mass, and retained causal state through changes of resolution.

### 6.1 Opening material and three distinct partitions

Read the **final post-physics parent** at settlement, not a stale candidate or
surface-enriched composition. Require an extant `:planet`, finite positive
`c/mass`, and finite elemental `c/composition` entries satisfying the existing
`law.composition/mass-fraction?` predicate, with keys in `element-set`. Select an
additional origin guard `abs(sum(fractions)-1) <= 10^-6`, evaluated on the exact
input rationals; this tolerance is a proposed input-quality bound, not a claim
that the current composition schema enforces the sum. Reject rather than renormalize. Let `M` be that mass and
`x_C` its bulk elemental carbon mass fraction, with `0 < x_C <= 1`.
`M*x_C` is an upper bound on carbon represented by this parent. It is not proof
of accessible food or a globally audited primordial-to-life carbon history.
[Chemistry](../../src/domain/chemistry.clj) (lines 357–389) groups `:organics` as C plus
bound O; that value and the refreshed volatile total are not a kg-C inventory.
The original [composition research](../research/physics/nebular-chemistry-metal-enrichment.md#31-composition-mass-conservation)
supplies the mass-fraction convention, not biological accessibility.

**Selected arithmetic:** stock and transfers are nonnegative arbitrary-precision
integer units, each `10^-15 kg C` (one picogram). This is an accounting resolution,
not an indivisible organism or a carbon-atom claim. Convert each finite IEEE-754
input to its exact binary rational, multiply exactly, then floor once into units;
record the input bit patterns and discarded fractional unit. Do not round a
double product, use a decimal display string as authority, or silently coerce an
invalid value. All later arithmetic is exact integer arithmetic, including batch
totals and bounds. Division floors explicitly and its residual stays in the
source compartment. An origin below one unit is rejected; never round it up.

The named model `:life-origin/v1` selects these **tunable prescription values**:

| Quantity | Selected rule in integer units | Meaning / limit |
| --- | --- | --- |
| `C` | `floor(exact(M*x_C) / 10^-15 kg)` | Parent carbon upper bound, not a spendable second copy |
| `A` | `min(floor(C/1000000), 10^15)` | One reference habitat allocation: at most one millionth of bulk C and at most 1 kg C |
| `S0` | `floor(A/100)` | 1% of that allocation is modeled as initially accessible substrate |
| `W0` | `A-S0` | Remaining allocated carbon is unavailable; no recycling is implied |
| `o` | `floor(S0/1000)` | One origin extent: 0.1% of accessible carbon becomes living stock |

These three fractions and cap are conservative *accounting bounds*, not measured
surface distribution, chemical accessibility or abiogenesis probabilities.
They keep the first compartment finite; they may be calibrated through a later
versioned model change. A version change never opens another account or refills
an existing one automatically. The reference habitat is a parent-local modeled
compartment, not a claimed resolved ocean, voxel location or physical 1 m³ volume.
A future concentration/rate model must supply that geometry rather than assuming it.

At opening, `U=0, B=0, S=S0, W=W0`. The origin transfer is `S -> U: o`;
representation then transfers `U -> B: o` into the first cohort. Both transfers,
the account, cohort and outcomes settle together, so the result is
`U=0, B=o, S=S0-o, W=W0`, with exact total `A`. A failed birth leaves neither
allocation nor debit. The rest of parent carbon stays unresolved and unallocated.
`A` is **included** in parent material, not added to gravitating `c/mass`.
For a sufficiently carbon-rich parent hitting the 1 kg cap, the chosen arithmetic
gives 0.99 kg unavailable carbon, 0.00999 kg remaining substrate and 0.00001 kg
living cohort carbon. This is an illustration of the selected prescription, not
a measured native-world result or a microbial calibration.

### 6.2 Conditions and cause; no scalar-to-mass conversion

An origin request requires a retained production `:event/life-emergence` naming
this parent, current ecology validated by the existing `ecology-contract` before
its living-phase predicate is used, ecology moisture `> 0.25`, current physical
parent temperature strictly between 225 and 375 K, and positive bulk H, O, C and
N fractions. The moisture/temperature criteria are explicit toy proxies aligned
with the existing ecology band; elemental presence does not establish liquid
water, nutrient availability, oxygen supply or resolved habitat pressure.
`c/pressure` is not substituted for a measured surface pressure.

Use the earliest retained qualifying life-emergence event, breaking equal-tick
ties by ledger position. The request records its event ID/cause, requesting tick,
parent ID, source mass/composition values, model and source-rule version. Because
phase events are emitted after lifecycle today, the earliest request can occur
from the **next frozen snapshot**, never by consuming an event before it exists.
Recheck all conditions and the exact mass/composition signature against the final
parent before accepting. Changed source, any final consumed marker, unavailable
cause, invalid data or zero `o` rejects without a partial account or entity.
No zoom, selection, commitment, paid Grow or direct debug write supplies a cause.
Native acceptance separately verifies the source event arose in ordinary genesis;
unit fixtures establish laws only.
No observed parent has yet been shown to satisfy this complete origin guard at
its final settlement state. In the retained [seed42 observation at tick 11280](https://github.com/octave-commons/Truth/blob/5740c2783a5689752c7bed0d45f923081e8c2ce3/.%CE%B7%CE%BC/diagnostics/playable-foundation/natural-seed42/observations.edn#L154)
and [seed43 observation at tick 11328](https://github.com/octave-commons/Truth/blob/5740c2783a5689752c7bed0d45f923081e8c2ce3/.%CE%B7%CE%BC/diagnostics/playable-foundation/natural-seed43/observations.edn#L152),
the highlighted scalar-life parents' stored candidate compositions contain
`H=0`. The [committed observer](https://github.com/octave-commons/Truth/blob/5740c2783a5689752c7bed0d45f923081e8c2ce3/.%CE%B7%CE%BC/diagnostics/playable-foundation/natural-seed43/observe.clj#L34)
saves current mass/ecology and historical candidate data, but not current bulk
composition or physical `c/temperature`. Those distinct snapshots cannot evaluate
the proposed final-parent guard or establish an available substrate inventory.
Keep the guard under model review and preserve an unsuccessful native observation
instead of relaxing it to force a cohort.

There is also a **source-derived applicability gap at ordinary core/envelope
planet birth**. [Planet composition](../../src/domain/planet_formation/composition.clj)
(lines 43–49) removes the [gas-former set](../../src/law/composition.clj)
(lines 21–23), including H, from every condensed core; a seed with `gas-m=0`
therefore has `H=0`. In this path, [positive envelope mass](../../src/domain/planet_formation/physics.clj)
(lines 139–154) requires a sufficiently massive core beyond the snowline.
For positive stellar luminosity, that file's 170 K, zero-albedo snowline law
and the [seed-temperature law](../../src/domain/planet_formation/orbit.clj)
(lines 12–23 and 58–63) imply
`T_seed < 170*(1-0.3)^(1/4)+35 = 190.4975 K` beyond the snowline.
Thus this birth path does not initially supply both positive H and the proposed
225–375 K guard. Later warming, transport or material exchange may change those
conditions; this is not a proof that every later natural parent is ineligible.
The [retention classifier](../../src/domain/stellar/classifier/planet.clj)
(lines 239–250) selects candidate H2O from material class and thermal band, without
an elemental inventory intersection. It cannot override the
[chemical diagnostic](../../src/domain/chemistry.clj) (lines 254–261), which yields
zero H2O when H is zero. The existing [atmosphere research limitation](../research/atmosphere/planetary-atmosphere-retention-classifier.md)
(lines 572–581) already names that missing intersection. These are material-model
applicability limits, not permission to infer accessible water or weaken the
origin guard. In addition, the exact-signature closure below can end coverage
on ordinary mass/composition evolution; neither an eligible origin nor a useful
account lifetime has yet been demonstrated in a natural run.

Normalized `:biomass` supplies **no kg conversion and no repeated credit**.
After acceptance, `U` and cohort `B` are the only authority for this compartment's
living carbon. Existing `c/ecology` remains its sole writer's coarse toy model;
its biomass/complexity may continue to evolve but are labeled separately from
accounted carbon. They do not refill, resize or reset this account. A later model
may replace that coarse proxy with an account-derived projection, but v1 does not
claim the two values are quantitatively equivalent. Continued growth needs an
explicit resource transaction; a living scalar alone cannot fund it.

## 7. Selected identity, transaction and lifecycle contract

### 7.1 Durable keys and exact transaction interface

One persistent ECS bookkeeping entity holds the biology book and survives parent
reaping. It has no physical mass, position, body-kind or renderer geometry. It is
allocated once by the lifecycle settlement when it first observes a life-emergence
event, even if admission rejects; this empty bookkeeping entity is not a partial
birth or carbon allocation. The book is ECS content, not another mutable event
store. Retain an event cursor and parent-to-first-cause index there: consume each
new ledger event once in ledger order, then evaluate indexed parents/requests.
Do not rescan the whole event history independently for every parent each tick.

Parent incarnation is the ECS allocation ID **within the current world**: `spawn`
monotonically allocates `:next-id`, and this design forbids reusing an ID. The
book retains the parent slot after removal. Account ID is
`[:life-origin/v1 parent-id cause-event-id]`, where the cause is the persisted
first life event. Keys and retries are scoped to **one persisted book/world
lineage**, not universally distinct universes. A copied or rewound world can retain
all these IDs: shared ancestry may share keys. V1 neither combines balances/outcomes
from separate branches nor claims a cross-world identity policy; such import or
reconciliation needs a later namespace/admission contract. Identity is not derived
from title, phase or seed. While retaining this same book, the parent slot
admits at most one account across ecological phase resets and model versions.
Restoring an earlier whole-world snapshot restores its earlier book/allocator too;
v1 does not promise deduplication across destructive rewind or divergent histories. Cohort lineage is `[account-id :cohort 0]`;
its ECS ID is a replaceable representation ID, never the lineage identity.

An operation is portable data:
`{:op-id [account-id producer-id sequence] :account account-id
  :expected-revision n :kind k :amounts {...} :cause event-id
  :interval nil-or-[start-seconds end-seconds] :rule-version v}`.
The origin's producer is `:origin`, sequence is the requesting tick; later
producers own monotonically persisted sequences. Origin has no elapsed-time rate
and uses a nil interval. A timed producer must supply modeled seconds from the
existing simulation clock; this account neither fabricates nor advances a clock.

The pure transaction returns either `{:accepted? true :account next :outcome ...
:entity-effects ...}` or `{:accepted? false :reason ... :outcome ...}`; no side
effect occurs until the complete accepted result passes validation. Persist the
immutable request and its outcome. Identical retry returns that prior outcome,
including after closure, without another event, entity or stock write. Conflicting
key reuse rejects without overwriting the original; a stale revision rejects.
Malformed requests lacking a valid identity produce a diagnostic rejection but
cannot claim a persisted/deduplicated operation. Exact integer validation excludes
negative/nonintegral amounts, overflow into another numeric type and partial writes.

Resource-use requests take admitted extents `q/g/L` in integer units, satisfying
`0<=g<=q<=S` and `0<=L<=B+g`; growth precedes loss. Apply §3's transfer equations
exactly. The kernel never clips an excessive request into a valid one. Zero action
records a no-change outcome. A rate law, yield, death threshold or request source
is not manufactured by this interface; the Monod candidate remains a separate
producer choice. Representation birth and an initial living amount alone do not
satisfy the later one-meaningful-resource-action acceptance.

### 7.2 One owner in the existing lifecycle, not a debit/refund protocol

Select **one serial biology settlement owner within the existing world-construction
lifecycle**. Fan-out emits only its own immutable proposal component; it writes
neither accounts, cohort stock nor an entity allocation. After all physics writes
are folded, lifecycle obtains the complete consumed set. Its uniform validated
component-bundle creation must support non-stellar entities as well as the current
stellar constructor. It must not pass a cohort through `spawn-clump` or attach
fictional stellar components. Existing stellar behavior stays on the same path.

The serial owner is the sole writer of the biology book and biological cohort
components, with a named registry/architecture ownership declaration and validated
inputs/outputs. Extending those declarations to serial construction is part of
the implementation contract; today's fan-out registry is not claimed to cover it.
It alone settles accounts, creation/removal and outcomes as one pure world result.
This is an explicit extension of lifecycle's present creation/removal-only contract
for **local bookkeeping**, not permission to write physical mass/momentum there.
Physical boundary fluxes remain owned by the existing integrator influences.

Order at this existing boundary is: determine physical removal set; retire invalid
existing accounts (§7.3); validate new requests against surviving final parents;
settle requests and validated creations; reap physical and biological removals.
Process parents by ascending ECS ID, then origins before other operations, then
producer keyword text and sequence; use the running account balance and revision
between operations. This is deterministic engineering order, not a claim of
biological fairness. No independent consumers spend the same frozen balance.
A rejected origin never reserves a parent slot; accepted origin permanently does.

Persist operation outcomes in the book and append ordinary causal events once.
Derive their UUIDv5 IDs with the standard URL namespace
`6ba7b811-9dad-11d1-80b4-00c04fd430c8` and UTF-8 name
`"truth/life-origin/v1:" + pr-str([op-id outcome-kind])`; those tuples contain no
unordered maps/sets. Reject any same-ID/different-payload collision. Replaying persisted causes/operations is
stable; existing random upstream life-event IDs mean this does not promise equal
IDs for independent fresh same-seed universes.

### 7.3 Parent changes close the bounded compartment without duplication

V1 intentionally models **no transport of biological stock across material
changes**. Before every settlement, compare actual final parent mass and the full
bulk composition map to the recorded opening signature. Any change, invalid/missing
physical data, consumed marker, non-planet state, loss of the stated habitat
conditions, or loss of living ecology closes the compartment before any new action.
A physical move or change of camera/LOD alone does not close it.

Closure is one terminal transaction: export every active `U/S/W/B_i` amount out
of this modeled compartment, set active stocks to zero, retire cohort entities,
and retain the exact final pre-export inventory, parent signature/cause, operation
outcomes, lineage and terminal reason in the surviving book. Its accounting is
`opening A + accepted boundary imports(0 in v1) = active total + exported total`.
A closed archive is **history**, not another active allocation; exports are never
credited again by biology to a survivor, food pool or gravitating body.

The export destination is explicitly `:unresolved-physical-boundary`, with the
actual consumed/material-change cause when available. It records the limit of the
local model; it is not a claim of known chemical fate, physical mass annihilation,
biological extinction or globally traced carbon. Physics already owns the changed,
merged, escaped or depleted parent. This conservative terminal policy may end a
cohort after a small accretion change; that loss of modeling coverage is intentional
and visible, not hidden under a claim of continuous biological simulation.

No reopening, refill, new epoch or refund follows from phase reset, restored
composition, a later model version or zoom. A distinct surviving parent may have
its own origin/account, funded only from its then-current bulk state; the closed
old account contributes no active carbon and no second credit. Boundary transport
and re-admission require a later reviewed model. Pending births on a removed or
changed parent are rejected before any partition. Retirement of an existing
cohort is distinct from rejecting a pending one.

## 8. Finite implementation boundaries and proof obligations

These are **proposed** future slices under this existing specification, not new
cards, current estimates accepted by review, or implementation admission:

| Boundary | Target size | Completion evidence |
| --- | --- | --- |
| Pure integer account/origin laws | 3 | Actual exact input conversion, partitions, repeated success/rejection, conflicting key, stale revision, ordered competing transfers, zero/tiny/nonfinite inputs and exact conservation |
| Uniform lifecycle settlement and retained book | 5 | Real fan-out/fold/materialization path; non-stellar bundles; atomic account/entity/outcome; unchanged stellar path; same-fold cancellation; retained terminal histories; explicit sole-writer checks |
| Natural origin producer plus boundary closure | 5 | Actual life-event cause and final-parent checks; once-only origin; changed/reaped parent closes before later action; no refill/reset/LOD minting; scaling proportional to tracked parents/requests |
| Existing inspector/Narrator projection | 3 | Read-only cohort/account/cause/terminal-state presentation, with a natural native origin observation; no new hotkey, creature mesh or avatar |

If the lifecycle ownership generalization cannot fit five points, refine that
slice before code; do not hide a new architecture inside an asserted estimate.
The account and origin require no new biological rate or clock. A subsequent
resource-use producer must select its extent/time/loss law and prove one real
resource action; these four slices do not claim the whole represented-life card's
acceptance or the eventual organism/individual milestone.

Native proof starts from ordinary genesis and a naturally produced life event,
with actual composition, account funding, birth and visible causal readback. No
forced phase, world injection, debug actor or test account substitutes for it.
Preserve negative observations: missing carbon, an unsatisfied proxy, too-small
allocation or terminal material change must be shown as rejection/closure rather
than adjusted until a cohort appears. No result in this note is an executed test,
local review is not hosted approval, and Incoming remains Incoming until the
canonical planning/admission process completes.

## 9. Proposed first cohort resource-action producer (2026-10-07)

This is a **new, unaccepted design continuation**, isolated at PR38 head
`4a4b4f63309b4b99ed211c9c186840fe0e8d3f33`, under the same Incoming 3 owner.
Sections 3 and 8 left the action producer open; this section selects one bounded
candidate without changing the origin guards, carbon allocation or parent-loss
rule. It supplies no code, biological calibration, natural admission, individual,
civilization or Gate. The parent proposal remains unaccepted. Scope/provenance:
[delegated scope](../../.ημ/diagnostics/life-action-plan/root-scope.json) and
[design audit](../../.ημ/diagnostics/life-action-plan/design-audit.json).

### 9.1 Model choice and primary evidence

Select one well-mixed, carbon-limited cohort with constant model coefficients
within a supported interval. [Monod's original paper](https://garcialab.berkeley.edu/courses/papers/Monod1949.pdf),
pp. 379–384, supports an empirical nutrient-saturation relation and distinguishes
resource yield from growth rate under specified limiting conditions; pp. 372–373
uses doublings rather than natural-exponential rate units. Neither a universal
extraterrestrial rate nor sufficient oxygen/energy follows from that relation.
[Jayathilake et al., Methods](https://journals.plos.org/plosone/article?id=10.1371/journal.pone.0181965)
separates substrate kinetics, biomass growth, decay and division; its Methods
explicitly discretizes growth with forward Euler and a biological timestep.
That is a numerical precedent, not validation of this proposal's parameters or
step envelope. This proposal
selects **no endogenous loss, maintenance, division or recycling: L=0**. It models
only the stated growth transaction, not indefinite biological survival. Existing
habitat/account closure still applies and must not be relabeled starvation.

Choose guarded forward Euler for this first numerical reference. It uses one
bounded evaluation rather than hidden biological substeps. Positivity-preserving
Patankar methods are a real alternative ([primary research abstract](https://doi.org/10.1016/S0168-9274(03)00101-6));
positivity alone would not establish fidelity over an arbitrary cosmological
interval. The guards below are a proposed engineering envelope, **not a proven
1% global error bound**. A higher-order solver or automatic interval subdivision
is outside this continuation.

### 9.2 Required inputs and modeled interval

At origin settlement, the proposed sole biology owner initializes producer
`:cohort-uptake/v1` for the one cohort with its next expected physics tick and
local elapsed time zero. Birth in the fold for tick n cannot consume that tick's
already elapsed interval; its first action is for tick n+1. Supply an immutable,
versioned profile with exact rational `mu_max > 0`, the **natural-exponential
relative growth rate** (s^-1), `K > 0` (carbon-account units) and `0 < Y <= 1`
(carbon fraction). A sourced rate R in doublings per time unit must be converted
as mu_max = ln(2)*R and converted to seconds. Because ln(2) is irrational, importing
it into this exact-rational model requires an explicit provenance-bearing rational
approximation, including source units, conversion/error policy and profile version.
Do not silently copy R as mu_max or claim that a rational approximation is the
exact sourced conversion. Worked values below are constructed rational profiles,
not imported or calibrated biological rates. `K = K_s*V / quantum` relates the mass
parameter to §3's concentration form when supported volume and concentration
parameters exist. Do not fabricate volume from planet radius. A directly supplied
K is an explicit compartment model parameter, not measured habitat geometry.
Missing profile or applicability evidence is unsupported, never a default rate.
No numerical parameter value in the worked cases is a production prescription.

Inputs are the proposed account's integer U/S/B/W, cohort/account identities,
revision, profile identity, step identity, positive elapsed h in **modeled
seconds**, and two rational numerical residues `r_q,r_g` in [0,1), initially zero.
All values must satisfy named pure validators; finite floating point is not an
exact-rational contract. Import an actual finite positive binary64 `:sim/dt` by
its exact IEEE-754 rational value, retaining its source bits. Do not round it to
an integer second or subtract large absolute `:genesis/sim-time` values.

Bound this reference arithmetic explicitly: every normalized rational numerator
and denominator, stock and elapsed cursor is at most 4096 bits. Validate operands
before each operation; pairwise integer products/sums may use at most 8193-bit
temporaries, then reduce and recheck the 4096-bit result. Exceeding this budget
is an unsupported numeric interval, never conversion to double. Rational residue
denominators can grow across actions, so continued support is not guaranteed;
include that boundary in later horizon testing. There is a fixed count of bounded
operations per proposal, not an unqualified constant-time arbitrary-precision claim.

The input h belongs to the completed physical fold: current
[advance-simulation-clock](../../src/domain/genesis/tick.clj) lines 178–207 adds
the input dt and only then installs pacing's next dt. The fan-out producer reads
that input snapshot, not the new pacing value. A future accepted exact clock can
supply the same duration contract; this proposal neither consumes wall time nor
advances physical time. It runs once per active cohort per completed physical
step, independently of the scalar ecology's 24-tick cadence. Skipped LOD updates
are not allowed to skip this bookkeeping producer.

Require the next contiguous step identity and account-local interval [t,t+h]
where t equals the retained successful cursor. A repeated accepted request uses
§7's prior outcome; an overlapping, missing or reordered interval is rejected.
A request must first identify an existing account/cohort and its authorized
producer; an unidentifiable payload cannot suspend an arbitrary account.
On malformed input for that identified producer, oversized interval or a missing physical step, retain the
rejection and mark this producer **suspended with an unmodeled interval**, keeping
stocks, residues and successful cursor unchanged. Subsequent ticks do not accrue
catch-up work or restart it. Recovery/reinitialization needs a later explicit
contract; it cannot silently resume using new coefficients. This is a numerical
coverage failure, not death or physical pause. Account closure still runs while
suspended. Normal paused simulation emits no completed steps; it earns no growth.

### 9.3 Extent, integer conversion and conservation

For S>0 and B>0, use exact rational intermediates in carbon-account units:

```text
mu = mu_max * S / (K + S)             [s^-1]
a  = mu * h                          [dimensionless]
x  = B * a / Y                       [ideal consumed carbon units]
admit only if a <= 1/100 and x <= S/100
z_q = x + r_q;  q = floor(z_q);  r_q' = z_q - q
z_g = Y*q + r_g; g = floor(z_g); r_g' = z_g - g
L = 0
(U',S',B',W') = (U, S-q, B+g, W+q-g)
```

The two 1/100 bounds limit the unquantized step's relative living-stock increase
and substrate depletion. They do not bound external habitat changes between
samples; the existing final-parent validation still decides coverage. Validate
`0<=g<=q<=S` after quantization as well. With integer S>=1, x<=S/100 and r_q<1
imply q<=S; Y<=1 and r_g<1 imply g<=q. Every accepted integer transfer therefore
conserves U+S+B+W exactly. Quantization can move one whole unit when a stock is
very small, so the relative-change bound is not a post-quantization guarantee.

Residues are numerical error accumulators, **not carbon stocks, reservations or
claims against W**. With zero initial residues and a fixed profile, Σx−Σq=r_q<1 and
Σ(Y*q)−Σg=r_g<1 account unit. Relative to the unquantized ideal growth,
Y*Σx−Σg=Y*r_q+r_g<1+Y; claiming a combined sub-unit growth error would be
incorrect. Only the q units
actually removed from S can fund g; the balance q-g goes to W immediately. Residues
persist across accepted intervals, save/reload and LOD, so tiny positive rates do
not vanish merely because of repeated floor operations. They are archived on
closure; no residue creates refundable/exportable carbon. A profile change is
unsupported, not permission to reset residues and replay an interval.

If S=0 or B=0, return q=g=L=0, unchanged residues and stocks, and an accepted
zero-action interval, after ordinary identity/profile/habitat validation. This
neither invents growth nor infers death. h=0 is a no-step diagnostic, not an
accepted clock advance; negative, nonfinite or unrepresentable inputs reject.
There is no min(S,x) clipping, hidden retry or partial interval consumption.

### 9.4 Ownership and final-state validation

The new **proposed** fan-out owner reads the existing book/cohort/profile and
emits only its own immutable uptake-proposal component. It owns neither stock,
identity allocation, cursor nor events. Its payload contains step/input-dt bits,
interval, profile, expected revision, input-stock/residue fingerprint and q/g/L
with next residues. It has one proposal per active cohort; inactive/suspended
owners emit the established clearing write-set. Registry reads/writes and named
Malli validators remain mandatory before implementation.

The **same** §7.2 serial biology settlement checks final parent survival and the
origin material/habitat signature first, then validates the proposal against the
current account/revision/profile/cursor. A same-fold removal or closure cancels
action; it cannot spend and later refund. Any earlier ordered operation changing
the account makes a frozen proposal stale. For this producer's next unaccounted
completed step, reject it and suspend the producer with that unmodeled interval;
keep its successful cursor/residues unchanged, preserving the other operation's
already accepted changes. Do not recompute the rate on a different snapshot or
let the following step silently skip this gap. An exact prior retry still returns
its stored outcome first; a conflicting replay of an already accounted interval
is rejected without suspending an otherwise current producer. Closed accounts
remain closed, not newly suspended. After pure validation, stock, residues,
successful cursor, operation outcome and causal event commit together. A repeated
outcome neither charges nor emits again. Origin initialization and later uptake
are separate occurrences; neither constitutes a second living-stock origin.

The uniform lifecycle still lacks these capabilities: current
[bootstrap/materialize-lifecycle](../../src/domain/genesis/bootstrap.clj) lines
299–352 constructs stellar clumps. No existing generic birth/settlement writer
is being claimed. A future inspector/Narrator consumer reads the settled result:
cohort identity, consumed/gained units, modeled interval and status. Its first
meaningful action requires q>0; an attempted, rejected or rounded-to-zero action
must not be displayed as consumption. No new input binding or creature mesh.

### 9.5 Exact review cases and later acceptance

These constructed arithmetic inputs use account units (one unit = 10^-15 kg C),
not observed organisms. Start independent cases with U=W=r_q=r_g=0,
S=100000, B=2000, K=100000, mu_max=1/100 s^-1, Y=1/2 unless stated.

| Case | Exact result / required outcome |
| --- | --- |
| Funded h=1 s | mu=1/200, x=20; q=20,g=10,L=0; S'=99980,B'=2010,W'=10, total=102000; residues zero. One accepted causal action. |
| Quantization h=1/100 s, repeated five contiguous intervals | While stocks stay unchanged x=1/5 each time. After four: q=g=0,r_q=4/5. Fifth: q=1,g=0,r_q=0,r_g=1/2,S'=99999,B'=2000,W'=1. Finite uptake is not mislabeled biomass gain. |
| Depleted S=0, h=1 s | Zero action and interval accepted, no stock creation/death; residues unchanged. |
| Excessive h=3 s | a=3/200 >1/100: reject the whole interval, suspend producer, no stock/residue/cursor mutation; no three hidden one-second steps. |
| Substrate-bound rejection: S=10,B=100000,K=10,h=1 s | a=1/200 passes, x=1000 exceeds S/100=1/10: reject before rounding, no clipping or borrowing. |
| Duplicate, stale revision or same-fold parent reap | Exact duplicate returns prior outcome; stale proposal rejects; parent reap closes/exports under §7.3 and cancels uptake. No second debit or event. |

For error assessment, compare h with two h/2 steps on identical admitted inputs;
require conservation in both, record different numerical results/residues, and
measure convergence as the step shrinks. This explicit Euler proposal does **not**
promise exact timestep-partition invariance. Tests must also cover invalid
profile/rational residue, changed profile, large input dt, clock gaps, ordinary
save/reload and the shared lifecycle's same-fold cancellation. These are future
RED boundaries, not executed project tests or an implementation story admitted
by this note.

The extension remains bounded to one ecological resource action. Chemistry/stock
producers, physically admissible habitat, profile/applicability selection,
accepted lifecycle integration and supported temporal resolution remain real
prerequisites. Current cosmological steps will generally violate this envelope
for ordinary microbial rates; no naturally supported interval or active cohort
is claimed. The existing clock owner must resolve that consumer dependency, not
an arbitrary slower biological clock in this producer. Root/independent review
must assess the selected envelope and failure policy before any implementation
admission. Ordinary native acceptance eventually requires natural origin, one
positive settled uptake and visible history in the same world; this section does
not complete the whole actor card, embodiment or an earned Gate.
