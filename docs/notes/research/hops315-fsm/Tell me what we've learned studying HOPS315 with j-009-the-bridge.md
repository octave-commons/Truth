---

# continue

Perfect. Now we can stop hand-waving and align the FSM architecture directly to the **actual Phase‑0 contract**.

The handoff spec is already very concrete: it names the exact `:planet-candidate` fields, and it phases implementation as material class, thermal band, orbit stability, atmosphere retention, then handoff emission.  That means the next step is not inventing more ontology; it is **mapping the ontology into those phases**.[^20_1]

## The bridge

The clean design is:

- **FSMs** give you rich internal truth.
- **Phase components** cache the coarse public summaries.
- **Handoff system** packages those summaries plus raw observables into `:planet-candidate`.[^20_1]

So the repo can keep two layers at once:

### Rich internal state

- `c/matter-state`
- `c/role-state`
- `c/environment-state`
- `c/atmosphere-state`
- `c/magnetosphere-state`
- `c/biosphere-state`
- `c/state-modifiers`


### Coarse public summaries

- `c/material-class`
- `c/thermal-band`
- `c/orbit-stable?`
- `c/atmosphere-class`
- `c/retained-species`
- `c/core-dynamo?`
- `c/magnetic-field`

That is the right compromise between realism and tractability.

## Aligning with the handoff phases

The handoff spec’s phased plan is already almost an implementation roadmap for the FSM architecture.[^20_1]

### Phase 1 — Planet classification

The spec says to add pure `material-class` and `thermal-band` functions and write those as components.[^20_1]

That means:

- `material-class` should be a **projection** from:
    - `matter-state`
    - bulk composition
    - volatile / H-He fraction
- `thermal-band` should be a **projection** from:
    - star luminosity
    - semi-major axis
    - albedo assumption or first-pass constant

These are not full state machines on their own; they are the first public summaries derived from Matter + orbital observables.

### Phase 2 — Orbit stability

The spec says to add an analytic proxy now, and only later upgrade to full integration if needed.[^20_1]

That means the **Role FSM** should start extremely simple:

- if bound and not plunging and not too close to a sibling planet → stable candidate role
- if unstable / scattering / inside star → not candidate

So Role FSM can initially be coarse, even if the full long-term vision includes resonances, satellites, belts, co-orbitals, and scatterers.

### Phase 3 — Atmosphere retention

The spec wants a pure `atmosphere-class` function and `retained-species` component.[^20_1]

That means your **Atmosphere/EM architecture** can also start with a public compression layer first:

- `:none`
- `:thin`
- `:substantial`
- `:thick`

underneath which later you can add:

- `actively-stripped`
- `collapsing`
- `xuv-energy-limited`
- `xuv-recombination-limited`
- `xuv-photon-limited`
- `frozen`

The handoff stays simple while the internal machine grows richer later.

### Phase 4 — Handoff event

The spec explicitly calls for `domain.genesis/handoff-system` after classification, plus appending a `:phase0-handoff` ledger event.[^20_1]

That is the exact place where the rich state gets turned into the contract record.

## Recommended component layout

If I were standardizing this now, I’d split components into three classes.

### A. Deep regime components

These are the real FSM states.

- `c/matter-state`
- `c/role-state`
- `c/environment-state`
- `c/atmosphere-state`
- `c/magnetosphere-state`
- `c/biosphere-state`


### B. Derived summary components

These are cheap, stable, and handoff-friendly.

- `c/material-class`
- `c/thermal-band`
- `c/orbit-stable?`
- `c/atmosphere-class`
- `c/retained-species`
- `c/core-dynamo?`
- `c/magnetic-field`


### C. Provenance components

These explain why the world is the way it is.

- `c/state-modifiers`
- `c/formation-events`
- maybe `c/candidate-kind` later (`:planet`, `:moon`, `:dwarf-planet`)

This keeps the public contract from depending on the full internal complexity.

## A cleaner ownership rule

You want to preserve the single-writer discipline that has been good for Truth.

So I’d use this ownership rule:

- **Deep state machines** each have one owner.
- **Derived summaries** each have one projector owner.
- **Handoff record** has one emitter owner.

Concretely:

- `domain.stellar` owns gas/stellar `matter-state`.
- `domain.planet-formation` owns solid-body `matter-state`.
- `domain.orbital` owns `role-state` and `orbit-stable?`.
- `domain.environment` owns `environment-state`.
- `domain.atmosphere` owns `atmosphere-state`, `atmosphere-class`, `retained-species`.
- `domain.em` or `domain.interior` owns `magnetosphere-state`, `core-dynamo?`, `magnetic-field`.
- `domain.genesis/handoff-system` owns `:planet-candidate` emission.[^20_1]

That gives each layer a clear answer to “who writes this?”

## The projection model

The important conceptual move is this:

**Do not compute handoff fields directly from raw observables everywhere. Compute them from the FSM + observables through one projection step.**

For example:

### `material-class`

Projection of:

- `matter-state`
- composition
- envelope fraction


### `thermal-band`

Projection of:

- stellar luminosity
- orbital distance
- maybe atmosphere-independent equilibrium estimate


### `atmosphere-class`

Projection of:

- `atmosphere-state`
- retained column mass estimate
- maybe environment context


### `core-dynamo?`

Projection of:

- `magnetosphere-state`
- rotation
- interior heat proxy


### `formation-events`

Projection / ledger accumulation of:

- major transitions
- major modifiers encountered
- impact / collapse / atmosphere-loss milestones

That way, when you later deepen the atmosphere or role models, the handoff record does not need a schema rewrite.

## Recommended first pseudocode shape

If I were writing the next spec block, I’d describe `handoff-system` in this pattern:

1. Find stable star(s).
2. Find candidate bodies with eligible `matter-state`.
3. Require `orbit-stable?`.
4. Read summary components:
    - `material-class`
    - `thermal-band`
    - `atmosphere-class`
    - `retained-species`
    - `core-dynamo?`
    - `magnetic-field`
5. Package raw observables:
    - mass-derived gravity
    - orbit elements
    - angular momentum
    - rotation axis
    - bulk composition
6. Append `:phase0-handoff` event.[^20_1]

That matches the spec almost one-for-one.

## Where moons fit

This is a design decision worth making early.

The handoff contract says `:planet-candidate`, but your broader architecture clearly wants potentially habitable moons to matter. The easiest future-proof way is:

- keep the record name for now,
- add a future optional field like `:candidate-kind :planet | :moon | :dwarf-planet`,
- let `role-state` distinguish the orbital context.

That avoids locking the whole architecture into “habitability only matters on primaries.”

## The honest MVP

If I were trying to ship the smallest honest version of this, I would do **less** than the full ontology first:

### Implement now

- `material-class`
- `thermal-band`
- `orbit-stable?`
- `atmosphere-class`
- `retained-species`
- `core-dynamo?`
- `magnetic-field`
- `handoff-system`


### Stub but define now

- `role-state`
- `environment-state`
- `magnetosphere-state`


### Leave for later

- full Biosphere FSM
- rich modifier system
- detailed XUV regime transitions
- advanced ring / moon / belt semantics

This is still faithful to the larger architecture, because the public contract already expects the coarse outputs first.[^20_1]

## The key payoff

What this buys you is a project structure where:

- the continuous simulation can stay messy and artistic,
- the classifiers stay explicit and testable,
- and the handoff to later phases becomes **the real deliverable** of Phase 0.

That is exactly what the handoff spec is trying to force: a concrete deliverable rather than “some planets probably happened.”[^20_2][^20_1]

## What I’d write next

The next logical artifact is an actual **Truth-style spec section with pseudocode** for:

- `material-class`
- `thermal-band`
- `atmosphere-class`
- `retained-species`
- `core-dynamo?`
- `handoff-system`

In other words: the smallest executable slice of this entire FSM architecture.

<div align="center">⁂</div>

[^20_1]: https://pubs.acs.org/doi/pdf/10.1021/acscentsci.4c00820

[^20_2]: http://arxiv.org/pdf/1510.06205.pdf


