---

# continue

Great — now we can move from the conceptual FSM to the **implementation-facing spec shape**.

The habitability handoff spec is already telling you the target: Phase 0 ends when a stable star exists, at least one candidate planet exists, and candidate planets carry a structured record including atmosphere class, retained species, thermal band, orbit stability, and a simple dynamo estimate.  So the FSM architecture should be designed to **feed that record directly**, instead of being an abstract taxonomy.[^18_1]

## The design principle

The right pattern for Truth is:

- **Rich internal state machines**
- → compressed into
- **simple outward handoff summaries**

That means your FSMs can be detailed and physically expressive, while the Phase‑0 contract stays compact and testable. The handoff spec already does this with coarse outward fields like `:atmosphere-class`, `:retained-species`, `:orbit-stable?`, and `:core-dynamo?`.[^18_1]

So the next real step is to define:

1. **Internal ECS state components**
2. **State ownership**
3. **Pure classifier functions**
4. **A projection layer** from rich state to handoff record

## ECS state mapping

I would not overload `c/matter-state` to carry the whole ontology. That component should stay focused on the deepest physical identity layer. Everything else should become separate components.

A good first-pass mapping would be:

- `c/matter-state`
    - `:matter/nebula`, `:matter/protostar`, `:matter/planet`, etc.
- `c/role-state`
    - `:role/orbit-clearer`, `:role/satellite`, `:role/belt-member`, etc.
- `c/environment-state`
    - `:env/temperate-habitable`, `:env/arid-thin-atmosphere`, etc.
- `c/atmosphere-state`
    - `:atm/stable-secondary`, `:atm/actively-stripped`, etc.
- `c/magnetosphere-state`
    - `:mag/stable-dynamo`, `:mag/collapsed`, etc.
- `c/biosphere-state`
    - `:bio/none`, `:bio/prebiotic`, etc.
- `c/state-modifiers`
    - set/vector of modifiers like `:mod/has-large-moon`, `:mod/heavy-bombardment-era`

Then supporting observables remain separate:

- composition map
- mass
- radius
- density
- temperature
- luminosity
- orbit parameters
- escape velocity
- volatile inventory
- retained species
- XUV/wind flux
- dynamo proxy inputs
- bombardment flux
- tidal heating proxy

That separation is crucial. State components answer **classification**, while observables answer **why**.

## State ownership

Each FSM should have exactly one writer, just like your current classifier discipline.

A clean ownership split would be:

- `domain.stellar/classifier-system`
    - owns `c/matter-state` for gas / stellar collapse branch
- `domain.planet-formation/classifier-system`
    - owns `c/matter-state` for solids branch after disk handoff
    - owns `c/role-state` during disk-era and post-disk consolidation
- `domain.environment/classifier-system`
    - owns `c/environment-state`
- `domain.atmosphere/classifier-system`
    - owns `c/atmosphere-state`
- `domain.em/classifier-system`
    - owns `c/magnetosphere-state`
- `domain.biology/classifier-system`
    - owns `c/biosphere-state`
- `domain.genesis/handoff-system`
    - reads all of the above and emits `:planet-candidate` records

That matches the handoff spec’s call for a dedicated handoff system after classification rather than smearing candidate logic through the whole sim.[^18_1]

## Pure classifier functions

Each owned state component should be computed by a pure function, same spirit as `classify-next-state`.

Examples:

- `law.matter/classify-matter-state`
- `law.role/classify-role-state`
- `law.environment/classify-environment-state`
- `law.atmosphere/classify-atmosphere-state`
- `law.em/classify-magnetosphere-state`
- `law.biology/classify-biosphere-state`

Each function takes a **region/body snapshot** plus any needed neighbor/system summaries and returns one enum.

That gives you three big advantages:

- deterministic, testable state transitions,
- explicit guard precedence,
- and the ability to unit-test worlds like Mars, Pluto, Luna, Europa, early Earth as table-driven fixtures.


## Guard ordering

For each classifier, write down precedence explicitly.

For example, `classify-environment-state` might evaluate in this order:

1. melt fraction / magma criterion
2. impact reset criterion
3. airless collapse criterion
4. volatile-ice dominance criterion
5. runaway greenhouse criterion
6. habitable/ocean stability criterion
7. post-habitable criterion
8. residual arid/crusted/tidal branches

That way a world that is both cold and heavily bombarded does not oscillate ambiguously between `snowball` and `impact-reset`; whichever is conceptually higher precedence wins.

Likewise `classify-atmosphere-state` might do:

1. no atmosphere
2. blowoff
3. XUV regime states
4. actively stripped
5. collapsing
6. frozen
7. thick/substantial/stable-secondary
8. transient-outgassed

The atmosphere research strongly supports the idea that XUV loss must be split into distinct regimes rather than one generic escape law, so that belongs directly in classifier ordering.

## Projection to handoff record

Now the key bridge: the Phase‑0 handoff should **not** export the full internal FSM. It should export a compressed, stable schema.

The handoff spec already defines that target record, including material class, atmosphere class, retained species, and core dynamo estimate.[^18_1]

So add a projection layer, something like:

- `domain.genesis/handoff-planet-candidate`

It reads the rich state bundle and emits:

- `:material-class`
- `:thermal-band`
- `:atmosphere-class`
- `:retained-species`
- `:orbit-stable?`
- `:core-dynamo?`
- plus mass/radius/orbit/composition summaries


### Example compression

Internal state:

- `matter = :matter/planet`
- `role = :role/orbit-clearer`
- `environment = :env/arid-thin-atmosphere`
- `atmosphere = :atm/collapsing`
- `magnetosphere = :mag/collapsed`

Projected handoff:

- `:material-class :rocky`
- `:thermal-band :cold-temperate` or similar
- `:atmosphere-class :thin`
- `:retained-species #{:co2 :n2 :ar}`
- `:core-dynamo? false`
- `:orbit-stable? true`

That is exactly the kind of thing the handoff spec wants: enough structure to seed Phase 1, but not every hidden internal regime.[^18_1]

## Candidate planet gating

You also want a clear eligibility predicate for `:planet-candidate`.

The handoff spec already says a candidate must be:

- gravitationally bound,
- sufficiently stable,
- within a plausible equilibrium temperature band,
- and physically recognizable as a planet candidate.[^18_1]

So define a pure predicate like:

- `habitability/planet-candidate?`

Reading from the FSM stack, it should require at minimum:

- `matter-state ∈ #{:matter/planet :matter/dwarf-planet :matter/gas-giant :matter/ice-giant}` depending on what you want to hand forward
- `role-state ∈ #{:role/orbit-clearer :role/free-body :role/satellite}` with stricter rules if you only want primaries
- `orbit-stable? = true`
- star exists and is stable
- not engulfed / escaping / debris

Then the rest of the fields become descriptive, not gating.

## Test strategy

This architecture gets much easier to test than raw simulation outcomes.

You can write table-driven tests for each classifier:

- `moon-like-loses-atmosphere`
- `mars-like-collapsing-atmosphere`
- `pluto-like-frozen-volatile-world`
- `europa-like-subsurface-ocean`
- `early-earth-impact-reset`
- `venus-like-runaway-greenhouse`

And you can write projection tests:

- `arid-thin-atmosphere-projects-to-thin`
- `stable-dynamo-projects-core-dynamo-true`
- `collapsed-magnetosphere-projects-core-dynamo-false-or-weak`
- `orbit-clearer-and-stable-emits-candidate`
- `belt-member-unstable-does-not-emit-candidate`

That is much more robust than trying to validate every nuance only through long emergent runs.

## Minimal implementation sequence

If you want to phase this sanely, I would do it in this order:

1. **Add new state components**
    - `role`, `environment`, `atmosphere`, `magnetosphere`
2. **Implement pure classifiers**
    - initially coarse, even if many states are unreachable at first
3. **Wire a handoff projection**
    - producing the `:planet-candidate` record the spec already defines[^18_1]
4. **Backfill richer observables**
    - bombardment flux, volatile inventory, dynamo proxy, XUV regime criteria
5. **Add Biosphere FSM only after handoff is solid**
    - because Phase 0 should stop before life anyway[^18_1]

That sequencing respects the handoff spec’s boundary: Phase 0 produces grounded candidates; later phases make them alive or dead.

## The most important implementation insight

The biggest architectural win here is this:

**The FSM is not the simulation. The FSM is the interpreter of simulation observables.**

That means:

- SPH, N-body, disk transport, chemistry, EM, and climate generate observables.
- The FSM turns those observables into legible state.
- Downstream systems decide what models to run next based on that state.

That is exactly your “model of models” idea, but made concrete.

## What I would draft next

The most useful next artifact would be a Truth-style spec section called something like:

- `State Components and Classifier Ownership`

with:

- component keys,
- owning system,
- pure classifier function name,
- source observables,
- projection to `:planet-candidate`.

After that, the next section would be:

- `Phase 0 → Phase 1 Handoff Projection`

mapping the full FSM stack into the compact handoff schema the repo already wants.[^18_1]

Would you like me to write that next as if it were going straight into `docs/specs/`?

<div align="center">⁂</div>

[^18_1]: http://arxiv.org/pdf/1510.06205.pdf


---

# continue

Yes. Now we can write the **implementation mapping** cleanly, because the handoff contract is concrete: `:planet-candidate` already wants material class, thermal band, atmosphere class, retained species, angular momentum/rotation, surface gravity, and a dynamo / magnetic-field estimate.[^19_1]

So the FSM stack should be designed to **compute those fields by projection**, not replace them.

## Component ownership

Here’s the implementation-facing mapping I would treat as canonical.


| Component | Meaning | Owner | Feeds handoff fields |
| :-- | :-- | :-- | :-- |
| `c/matter-state` | Deep physical identity of body/medium | `domain.stellar`, `domain.planet-formation` | `:material-class`, candidate eligibility |
| `c/role-state` | Dynamical relationship in system | `domain.orbital` / `domain.planet-formation` | `:orbit-stable?`, candidate eligibility, context |
| `c/environment-state` | Surface/interior regime | `domain.environment` | not exported directly, but informs atmosphere, thermal interpretation, later Phase 1 |
| `c/atmosphere-state` | Internal atmosphere retention/loss regime | `domain.atmosphere` | `:atmosphere-class`, `:retained-species` |
| `c/magnetosphere-state` | Dynamo/shielding regime | `domain.em` / `domain.interior` | `:core-dynamo?`, `:magnetic-field` |
| `c/biosphere-state` | Level of life/pre-life | `domain.biology` | not part of Phase 0 handoff; Phase 1+ only |
| `c/state-modifiers` | Transition-shaping contextual flags | multiple writers only through one modifier aggregator | `:formation-events`, candidate interpretation |

That separation keeps the handoff contract simple while letting the internal world be richer. The handoff spec explicitly wants the Phase‑0 output to be a structured record rather than a vague scalar, which fits this projection model very well.[^19_2][^19_1]

## Pure classifiers

Each of those components should come from a pure classifier, and each classifier should own **one kind of question**.

I’d name them roughly like this:

- `law.matter/classify-matter-state`
- `law.role/classify-role-state`
- `law.environment/classify-environment-state`
- `law.atmosphere/classify-atmosphere-state`
- `law.em/classify-magnetosphere-state`
- `law.biology/classify-biosphere-state`

Then a final projector:

- `law.handoff/planet-candidate-record`

The pattern is:

1. Compute observables.
2. Run pure classifiers.
3. Write state components.
4. Project state + observables into the handoff schema.

That fits your current architecture style, where classification is centralized and downstream systems read state rather than improvising it.

## Projection rules

The handoff schema is not the same thing as the internal FSM. It is a **compressed view** of it.[^19_1]

### `:material-class`

This should come mostly from `c/matter-state` plus bulk composition:

- `:rocky`
    - matter state in rocky solid-body classes,
    - high rock/metal fraction,
    - low H/He envelope.
- `:icy`
    - volatile/ice dominated solid body.
- `:gaseous`
    - gas giant / dominant H/He envelope.
- `:mixed`
    - ambiguous layered worlds, ice giants, or worlds with major mixed inventories.

This lines up with the handoff spec’s desire for a coarse, testable material class.[^19_2][^19_1]

### `:thermal-band`

This should come from orbit + stellar luminosity, not from environment state directly:

- `:frozen`
- `:cold`
- `:temperate`
- `:warm`
- `:hot`

The handoff spec already treats this as derived from equilibrium temperature.  Environment state can interpret it, but should not define it.[^19_1][^19_2]

### `:atmosphere-class`

This should be a compression of `c/atmosphere-state`:

- `:none`
    - internal states like `atm/none`
- `:thin`
    - `transient-outgassed`, `collapsing`, weak retained cases
- `:substantial`
    - `stable-secondary`, `substantial`
- `:thick`
    - `thick`, some runaway-associated dense cases

This preserves the handoff’s simple public interface while allowing detailed internal escape regimes like energy-limited or recombination-limited to exist under the hood.[^19_1]

### `:retained-species`

This should come from composition + escape filtering + condensation filtering:

- start with bulk volatile inventory,
- remove species not retained at current escape velocity / thermal band,
- remove species currently frozen out if the handoff wants “retained in active atmosphere” rather than “present anywhere.”

The handoff spec explicitly includes a retained-species set, so this should become a first-class pure function rather than an incidental byproduct.[^19_1]

### `:orbit-stable?`

This should come from `law.orbital/orbit-stable?`, fed by `c/role-state` and orbital observables. The handoff spec currently suggests starting with an analytic proxy rather than immediately doing an expensive 10 Myr integration, which is exactly the right place for a coarse Role FSM plus stability helper.[^19_2]

### `:core-dynamo?` and `:magnetic-field`

These should come from `c/magnetosphere-state` plus simple field-estimation functions:

- `core-dynamo? = true` when internal convective + rotational conditions support a sustained dynamo
- `magnetic-field = [Bx By Bz]` from a dipole estimate, even if crude at first

The handoff spec already defines both fields, which means magnetosphere state is not optional if you want the contract to be honest.[^19_2][^19_1]

### `:formation-events`

This is where your modifier/event system connects beautifully.

Rather than storing only current state, append IDs for threshold-shaping events like:

- first condensation,
- major impact,
- atmosphere collapse,
- resonance capture,
- giant migration encounter,
- large moon formation,
- first ocean stabilization,
- dynamo collapse.

The handoff spec already includes `:formation-events`, so your modifier/event ledger can become a real causal history, not just flavor text.[^19_1]

## Candidate eligibility

The handoff system needs a clear predicate for whether a body becomes a `:planet-candidate`.

A sensible Phase‑0 predicate would require:

- a stable star exists,[^19_2]
- body matter state is planetary enough:
    - `planet`, `dwarf-planet`, `gas-giant`, `ice-giant`, maybe some large moons if you want,
- body is gravitationally bound and not escaping,[^19_2]
- orbit stability proxy passes,[^19_2]
- not debris, ring particle, or transient scattered junk,
- equilibrium temperature inside the spec’s candidate window if you want strict gating.[^19_2]

This is where you decide whether moons are Phase‑0 candidates. The current handoff spec sounds planet-centric, but your larger architecture suggests some moons may be equally important life targets. If so, I would allow `role=satellite` with a `:candidate-kind :moon` refinement later, while still emitting the same general record format.

## Minimal first implementation

If you want a realistic sequence that does not explode scope:

### Step 1

Add only these components:

- `c/role-state`
- `c/atmosphere-state`
- `c/magnetosphere-state`

You already effectively have matter-state, and environment can come slightly later if needed.

### Step 2

Implement these pure functions:

- `material-class`
- `thermal-band`
- `atmosphere-class`
- `retained-species`
- `orbit-stable?`
- `core-dynamo?`
- `surface-dipole-estimate`

These correspond almost one-to-one with the handoff contract fields.[^19_1]

### Step 3

Create `domain.genesis/handoff-system`

It should:

- run after classification,
- scan all eligible bodies,
- emit `:planet-candidate` maps,
- append a `:phase0-handoff` ledger event exactly as the spec says.[^19_1][^19_2]

That gives you a concrete deliverable fast, even before every rich state is fully exploited.

## Rich later implementation

Once the basic projection exists, you can deepen the internals without changing the outward contract:

- environment-state becomes richer,
- atmosphere-state gains XUV regime detail,
- role-state handles moons/belts/resonances better,
- modifiers accumulate more nuanced causal history,
- biosphere-state arrives in Phase 1+.

That is the right kind of extensibility: internal complexity can grow while the contract stays stable.

## Truth-style spec shape

If this were going straight into `docs/specs/`, the next section headings I’d write would be:

- `## State components`
- `## Classifier ownership`
- `## Projection to :planet-candidate`
- `## Candidate eligibility`
- `## Event provenance and formation-events`
- `## Phased implementation`

That would make this architecture actionable, not just conceptual.

## The key synthesis

So the clean final pattern is:

- **Simulation observables** are continuous and messy.
- **FSM classifiers** interpret them into discrete regimes.
- **Projection functions** compress those regimes into the simple contract fields that Phase 1 needs.[^19_1][^19_2]

That is exactly the “model of models” design you were reaching for.

The next thing to draft is the actual **spec prose and pseudocode** for `domain.genesis/handoff-system` and the projection functions, because that is where this whole architecture becomes executable.

<div align="center">⁂</div>

[^19_1]: https://arxiv.org/html/2410.00093v1

[^19_2]: http://arxiv.org/pdf/1510.06205.pdf


