# Gate producer: recovered contract and missing laws

(己, p=1.00) Corpus/source audit, 2026-10-06; source rechecked at
`a5453e43a33c8ad674e78790341090899a2adb47`. Supplements the
[dependency plan](truth-playable-gate-dependency-plan.md) and
[playable-route audit](2026-10-06-playable-gate-route-audit.md), owned by
`a2b46a04-d541-4714-aed9-bf07d9a9c923` (Review, 3). This is recovered evidence,
not a new feature law, implementation authorization or gameplay-completion claim.
GPL-3.0-or-later.

## Authority and smallest documented interaction

(汝, p=1.00) The archived **original user statements**, rather than the
assistant's surrounding elaboration, require worlds that independently learn
Gate technology with devices already at the other end; they call this a work
of fiction, not established quantum physics. They also say time locks when Gates
are created, avatar choice emerges from connected lives, and temporary control
of a person becomes possible once civilization arises. See the
[origin transcript](designs/2026.06.25.16.41.16-001-the-core-vision-truth-as-a-physics-first.md),
lines 92–96 and 151–168.

(世, p=0.99) **Adopted design direction:** the
[master phase design](../designs/gates-of-truth-world-gen-phases.md), §§Phase
3–6 (lines 96–165), preserves causal culture/history, a plausible avatar pool,
possible temporary embodiment, and discovery → construction → activation of
Gate-capable civilization. The [voxel design](../designs/planetary-voxel-substrate.md),
§5 (lines 275–293), keeps embodied tool actions in the same world and voxel
substrate. These are continuity requirements, not implementation-level
admission, construction or activation rules.

(己, p=0.98) The smallest documented **real Gate interaction** is therefore a
civilization-earned device activated by an in-world person to reach another
independently capable world. Its causal prerequisites cannot be replaced by
emitting a discovery event or inserting an avatar. No currently documented
Gate-producing slice is sufficiently specified to implement that interaction.

## Evidence map

(世, p=0.99) The table distinguishes executable facts from accepted direction
and sketches; cited line numbers refer to the source revision above.

| Boundary | Documented direction / tier | Executable frontier and missing contract |
| --- | --- | --- |
| Actor and civilization | Master Phases 3–4: individuals, transmitted culture, institutions and historical technology. [Research synthesis](../designs/simulation-methods-research.md), lines 203–299, surveys candidate models. | [Ecology](../../src/domain/ecology/state.clj):42 ends at scalar `:complex`. [Components](../../src/domain/ecs/components.clj):316 declares `civilization`, but source searches find no reader, writer or actor/social/history producer. Admission, identity, action, resources and loss need laws. |
| Gate capability / construction | Master Phase 6 names discovery, construction and activation. Research synthesis:303–323 names sophistication, energy generation and theoretical understanding, while explicitly marking travel as fiction. | No capability thresholds, research record, material requirement, Gate entity/schema, producer, placement or construction interaction. |
| Receiver / connection | Original user text:94 requires an independently developed receiving device. Assistant elaboration:111–147 proposes relational graph edges and reachable worlds within an energy budget. | Graph topology, receiver readiness, relationship metric and energy accounting are sketches. Research synthesis:435–438 explicitly leaves the connecting quantum prior undefined. No world graph or receiver implementation was found. |
| Embodied activation / consequence | Master Phase 5 permits temporary control; Phase 6 replaces broad guidance with personal agency. The origin sketch:145 proposes resolving a reachable destination from statistical state. | No avatar admission/control owner, activation input, energy debit, rejection outcome, traversal transaction or destination-resolution consumer. [Ability design](../designs/ability-tech-tree.md):517–519 explicitly leaves the post-Gate Self palette undesigned. |
| Time / interface | [UX architecture](../designs/ux-architecture.md):109, 261–285 removes manual time override at discovery; live Multiverse entries appear quietly with one ambient narrator line. | No Gate clock/network consumer. [Narrowing](../../src/domain/narrowing.clj):290–346 supplies only the distinct planetary time-lock record; the earlier route audit documents missing actuation. |

## Existing consumers are not a Gate producer

(世, p=1.00) `:event/gate-discovery` is consumed by
[player/system.clj](../../src/domain/player/system.clj):9–23,
[economy.clj](../../src/domain/player/economy.clj):8–16,
[arc.clj](../../src/domain/arc.clj):138–154,
[narrative.clj](../../src/domain/narrative.clj):45–60 and
[promotion.clj](../../src/domain/genesis/promotion.clj):54–87. They map the event
to coherence restoration, **100 Agency / 8 first-category Resonance**, text,
wonder and current-tick demotion protection. Those are rewards/effects of a
supplied event, not Gate construction or activation costs. The inspected tests
assert reward and mood lookup from supplied events, not a production discovery.
`detect-arc` currently ends its forward ladder at life emergence.

(世, p=1.00) [panels.clj](../../src/infra/menu/panels.clj):69–74 hardcodes
**“Gate distance: 0 of 6 thresholds”**, Yeth-Korath and Auren-Sel.
[widgets.clj](../../src/infra/menu/widgets.clj):15–25 permanently marks
Multiverse locked. UX architecture:273–277 says ghost names must be real graph
entries, not flavor text. The current strings establish neither six implemented
predicates nor existing destinations; no graph producer backs them.

## Decisions that must remain visible

- (己, p=0.99) **Lifecycle:** original user wording locks time at Gate
  **creation**; master/UX wording says **discovery**; visiting requires
  **activation**. Specify their order, irreversible boundary and failures before
  collapsing them into one state flag. Planetary commitment's local lock is a
  separate boundary, not proof of network synchronization.
- (己, p=0.99) **Capability and cost:** no accepted numerical trigger, build
  recipe, energy source/unit/budget, receiver criterion or activation failure
  law was recovered. The six-threshold UI string and discovery reward must not
  be promoted into these missing rules.
- (己, p=0.98) **Identity:** temporary control before discovery, Phase 5 avatar
  convergence and final selection at discovery need an ordered contract. UX
  text renames Spark at discovery but its phase table places Self in Phase 5;
  neither is an implemented embodiment rule.
- (己, p=0.99) **Research limits:** culture Python toys/plots under
  `docs/research/culture/img/` initialize synthetic populations and illustrate
  selected models. They are neither ECS actors nor a calibrated life-to-society
  transition. Any adopted scientific model still needs primary-source checking;
  the Gate connection law needs internally consistent fictional design.

## Existing ownership; no new feature card

(世, p=1.00) Canonical Rheos reads in the separate progression-planning
checkout at `96b19f086707388e43f272e5ec87a3a7378d4c4c` found:

| UUID | Status / size | Existing responsibility |
| --- | --- | --- |
| `embodied-character-voxel-mode` | Incoming / 55 | Activated horizon; still requires intervening phase specs before implementation breakdown. |
| `life-to-represented-actor-spec` | Incoming / 3 | First causal individual: admission, identity, one action, resources, loss and provenance. |
| `committed-biosphere-native-action-spec` | Incoming / 3 | One native committed-world biosphere interaction through existing owners. |
| `committed-clock-executable-policy` | Incoming / 3 | Reconcile local lock, later history and final Gate synchronization. |
| `character-scale-mining-construction` | Blocked / 5 | Existing embodied remove/place/reshape scope after its narrowing rung exists. |

(己, p=1.00) These are branch-qualified planning facts, not claims that new
cards or status changes have landed in this checkout. The existing route owner
remains Review here. No Ready/TODO Gate producer was found in canonical
inventory; the next grounded specification is already owned by
`life-to-represented-actor-spec`, alongside the independent clock/biosphere
work. Later Gate producer and interaction stories must name their real
prerequisites, failure outcomes and visible consequences before implementation.
