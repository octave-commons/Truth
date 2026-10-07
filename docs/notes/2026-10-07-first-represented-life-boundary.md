# First represented life: cohort boundary and open contracts

**Status:** proposed comparison and decision boundary, not an accepted biological
law or implementation-ready specification. **Owner:**
[`life-to-represented-actor-spec`](../../kanban/tasks/ground-the-first-causal-life-to-represented-actor-boundary-tor-spec.md),
Incoming, 3 points. Source audit: PR12 base
`98b847ce75491bdccacf2ec3a7476d451270bf49`; primary texts accessed 2026-10-07.

The proposed first representation is a persistent **microbial cohort** that
consumes a local resource and retains causal history. It represents living stock,
not one organism, a thinking character or an avatar. Its admission would expose
already accounted life at finer resolution; it would not explain abiogenesis or
turn a scalar phase flag into new matter. This narrows one prerequisite toward
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
| `U` | Unrepresented, already living carbon, kg C | A reviewed aggregate living-stock producer; no conversion from normalized biomass is currently defined |
| `S` | Available limiting substrate carbon, kg C | A reviewed habitat resource inventory; not an infinite food source |
| `B` | Carbon in the represented cohort, kg C | An admitted partition from `U`, then accounted resource use/loss |
| `W` | Other accounted, unavailable carbon, kg C | Retains non-biomass products/lost cohort carbon; not an automatic recyclable-food pool |
| `V` | Compartment volume, m³, finite and positive | A defined planet-local habitat geometry, not planet volume by default |

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
today**, with named `law/` schemas and declared registry reads/writes. Its future
single-writer allocation must cover the local budget and cohort state together;
existing ecology keeps sole ownership of `c/ecology`. Physical boundary fluxes
must use the existing uniform influence mechanism, not direct `c/mass` writes.
No second ECS, render engine, mutable side ledger or bespoke tick loop is proposed.

The existing lifecycle mechanism would need a reviewed generalization to create
validated component bundles without passing through `spawn-clump`. The proposed
semantic contract is one accepted birth and one living-stock partition per
admission key, or neither. A fan-out request is not a completed birth. An ECS ID
may be allocated at materialization; a durable admission/lineage identity must
survive retries, loss and LOD collapse independently of that transient ID. Exact
key shape, incarnation rules and deterministic ordering remain review decisions.
Never reconstruct a new history from `:prokaryotic`, a reset scalar, or zoom alone.

Current [tick ordering](../../src/domain/genesis/tick.clj), lines 167–196, folds
physics, expires interventions, materializes/reaps, and only later emits ecology
phase events. Admission cannot consume a same-tick event that has not been emitted.
The next eligible snapshot or an explicitly reviewed ordering is required.
At materialization, `ecs/alive?` alone is insufficient: a parent carrying a reap
marker is still alive before `reap-consumed`. Reject/cancel a pending birth whose
habitat will be removed; do not leave an orphan or a stranded stock debit. How
the uniform mechanism achieves this without a second budget writer is an
**implementation-blocking lifecycle decision**, not solved by the event API.

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
Exact arbitration, failure-event deduplication and parent-loss settlement still
need a single-writer-compatible design; the examples do not silently select one.

## 6. Decisions still required before implementation

| Question | Existing authority / required decision |
| --- | --- |
| Where do living stock, substrate and habitat volume first come from? | The decided nested-budget design supplies the architecture, not quantities or a producer. Establish a conservative aggregate source and physical coupling; scalar biomass is insufficient. |
| Which habitat and metabolism are supported? | Review the proposed carbon-limited compartment, additional required environmental inputs and applicability bounds. Neither the current scalar habitable band nor a paper's terrestrial parameters answers this. |
| What elapsed time does the model consume? | Coordinate with [`committed-clock-executable-policy`](../../kanban/tasks/specify-the-executable-committed-world-clock-and-later-history-contract-e-policy.md). Define accumulated modeled seconds, skipped updates, variable-step integration and reproducible LOD behavior; do not require a new clock here. |
| How are birth and budget settlement atomic? | Specify uniform component-bundle materialization, owner declarations, prior-state validation and removal ordering. No double debit/refund writer or stellar body disguise. |
| When is a cohort lost, and what survives? | Select a resource/environment loss law, retained carbon compartments, lineage/admission keys and persistence rules. This note deliberately does not select death thresholds or new-epoch rebirth. |
| What does the player see? | Propose an existing inspector/Narrator projection of cohort identity, habitat, carbon balance and causal changes. No new key, embodiment, creature mesh or phase unlock is implied. |

Two **candidate boundaries for later sizing**, not admitted stories or new cards:
(1) conservative local-budget/admission plus uniform non-stellar creation;
(2) one resource-use/loss action plus a read-only existing-UI projection. Each must
be refined to at most five points after the questions above close; if the first
requires broad budget/lifecycle architecture, split its design before code rather
than assigning it five points by assertion. This three-point continuation owns
the comparison, candidate accounting and precise decision boundary only. It does
not complete the card's implementation-level specification acceptance yet.

Later RED evidence must exercise the real fan-out/fold/materialization path:
valid/retried/rejected births, competing consumers, same-fold parent loss,
conservation, nonfinite inputs, replay/LOD identity and variable elapsed time.
Unit fixtures can establish these contracts but cannot establish native progress.
Native acceptance must begin with an ordinary naturally formed living world and
its actual budget producer, then observe the cohort's birth, resource action and
history through the existing renderer/menu. No forced phase, direct world write,
debug actor or research toy substitutes for that path. An individually represented
organism, cognition, society, embodiment and earned Gate production remain later
unspecified steps.
