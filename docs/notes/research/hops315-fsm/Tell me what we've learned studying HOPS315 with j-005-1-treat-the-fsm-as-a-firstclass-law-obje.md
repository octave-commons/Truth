---

# Yea, continue.

Let’s turn that conceptual FSM into something you can actually build around in Truth: a **data model** for states and transitions, and a way to hook it into your existing `matter-state`, disk regimes, atmosphere, EM, and biosphere systems.

I’ll keep it concrete but high‑level—this is about the architecture, not every exact keyword.

***

## 1. Treat the FSM as a first‑class “law” object

At the top level, you want a single source of truth (ha) that defines:

- **State categories** (gas, solids, environment, atmosphere/EM, biosphere).
- **States** within each category.
- **Transitions** between states, each with:
    - A `from` state.
    - A `to` state.
    - A guard function or declarative condition.

In terms of your namespace law, this lives in `law/`:

- `law.state_machine.gas`
- `law.state_machine.solids`
- `law.state_machine.environment`
- `law.state_machine.atmosphere_em`
- `law.state_machine.biosphere`

Each `domain/*` module then consults the appropriate state machine rather than hard‑coding transitions.

***

## 2. Gas ladder FSM (nebula → star + disk)

### States (simplified)

- `:gas/nebula`
- `:gas/collapsing`
- `:gas/condensed-core`
- `:gas/protostar`
- `:gas/star`
- `:gas/brown-dwarf`
- `:gas/giant-planet-core`


### Example transitions

- `:gas/nebula → :gas/collapsing`
Guard: $M/R$ above Jeans threshold; no core yet.
- `:gas/collapsing → :gas/condensed-core`
Guard: density ≥ `core-condensation-density` OR mass > `gas-particle-mass`; isolation criterion satisfied.
- `:gas/condensed-core → :gas/protostar / :gas/brown-dwarf / :gas/giant-planet-core`
Guard: mass tiers (deuterium, hydrogen thresholds).
- `:gas/protostar → :gas/star`
Guard: `fusion-possible?` and mass ≥ H‑burning limit.
- Down‑ladder transitions (mass loss, winds):
`:gas/star → :gas/stellar-remnant` (new state) when fusion no longer possible and mass below threshold.

You already have most of this logic in `classify-next-state`; the main missing piece is the explicit `:gas/stellar-remnant` terminal and the formalization of `:gas/collapsing` vs `:gas/nebula`.[^12_1]

***

## 3. Solids ladder FSM (dust → asteroids → moons → planets)

### States

We can define something like:

- `:solids/dust-pebbles`
- `:solids/planetesimal-population` (free‑flying small bodies)
- `:solids/asteroid-belt`
- `:solids/icy-small-body-belt`
- `:solids/comet-population`
- `:solids/moon`
- `:solids/dwarf-planet`
- `:solids/full-planet`
- `:solids/giant-planet` (ties back to `:gas/giant-planet-core`)


### Transitions

Examples:

- `:solids/dust-pebbles → :solids/planetesimal-population`
Guards:
    - Streaming instability conditions: local solids/gas ratio, Stokes numbers.
    - Or seed‑and‑grow condensation gating for certain regions (e.g. inner disk with HOPS‑315‑like conditions).[^12_2]
- `:solids/planetesimal-population → :solids/asteroid-belt / :solids/icy-small-body-belt`
Guard: location (inner vs outer), plus whether mass growth stalls (no bodies cross “roundness” threshold).
- `:solids/planetesimal-population → :solids/dwarf-planet / :solids/full-planet`
Guard:
    - Collisional growth and self‑gravity produce round bodies.
    - Orbit clearing (for full planets) vs shared belt (dwarf planets).
- `:solids/planetesimal-population → :solids/moon`
Guard:
    - Capture dynamics (Hill sphere, energy loss), or giant‑impact debris re‑aggregation around a planet.

Having these explicit states lets the sim *know* “this cluster of bodies is an asteroid belt” or “this body is a dwarf planet with moon(s)” rather than inferring it ad hoc.

***

## 4. Environment FSM (surface \& climate)

### States (per rocky/icy body)

You might formalize:

- `:env/molten-surface`
- `:env/impact-reheated`
- `:env/stable-crust`
- `:env/thick-atmosphere`
- `:env/thin-atmosphere`
- `:env/airless`
- `:env/icy-dwarf`
- `:env/habitable`
- `:env/runaway-greenhouse`
- `:env/snowball`

These are **composed**: e.g. Mars is `:solids/full-planet + :env/thin-atmosphere + :atm_em/weak-magnetosphere`.

### Example transitions

- `:env/molten-surface → :env/impact-reheated`
Guard: interior cooling below silicate melting point, but impact flux still high.
- `:env/impact-reheated → :env/stable-crust`
Guard: impact frequency drops; crust persists between events.
- `:env/stable-crust → :env/thick-atmosphere / :env/thin-atmosphere / :env/airless`
Guard: integrated outgassing vs atmospheric loss processes; gravity and EM field.
    - Thin vs thick vs airless determined by equilibrium column mass and shielding.
- `:env/thick-atmosphere → :env/habitable / :env/runaway-greenhouse / :env/snowball`
Guard: climate model output (surface temperature, ice coverage, greenhouse feedbacks).
- `:env/habitable → :env/thin-atmosphere / :env/snowball / :env/runaway-greenhouse`
Guard: long‑term changes in atmosphere, EM field, stellar flux.

This is where Mars’ story lives entirely in the FSM:

- Early: `:env/thick-atmosphere` + `:atm_em/strong-or-moderate-magnetosphere` ⇒ `:env/habitable`.
- Later: EM field weakens; solar wind stripping pushes to `:env/thin-atmosphere`.
- Climate + water loss eventually remove habitability.

***

## 5. Atmosphere + EM FSM (interaction with solar wind)

Your wind spec gives the **source** of solar wind and stellar mass loss; the missing piece is a per‑planet state machine that consumes that flux.[^12_1]

### States

- `:atm_em/no-magnetosphere`
- `:atm_em/weak-magnetosphere`
- `:atm_em/strong-magnetosphere`
- `:atm_em/atmosphere-retained`
- `:atm_em/atmosphere-stripping`
- `:atm_em/atmosphere-collapsed`


### Transitions

- `:atm_em/strong-magnetosphere → :atm_em/weak-magnetosphere → :atm_em/no-magnetosphere`
Guard: core energy, rotation, internal dynamo; time evolution.
- `:atm_em/atmosphere-retained → :atm_em/atmosphere-stripping`
Guard: solar/stellar wind flux at orbit, EM state, gravity; e.g. when loss rate exceeds replenishment.
- `:atm_em/atmosphere-stripping → :atm_em/atmosphere-collapsed`
Guard: integrated column mass falls below threshold for surface pressure.

These transitions then feed back into the environment FSM:

- `:atm_em/atmosphere-collapsed` ⇒ environment shifts to `:env/thin-atmosphere` or `:env/airless`, making habitability impossible.

***

## 6. Biosphere FSM (life \& civilization)

Finally, the biosphere ladder attaches to `:env/habitable` states.

### States

- `:bio/prebiotic`
- `:bio/microbial`
- `:bio/complex`
- `:bio/civilizational`
- `:bio/post-biosphere` (extinction, transformation)


### Transitions

- `:env/habitable → :bio/prebiotic`
Guard: solvent + energy + chemistry metrics.
- `:bio/prebiotic → :bio/microbial`
Guard: emergence of replication; could be probabilistic in sim.
- `:bio/microbial → :bio/complex`
Guard: oxygenation or alternative energetic adaptation; long timescales.
- `:bio/complex → :bio/civilizational`
Guard: emergence of intelligence and technology.
- Downward transitions (collapse/extinction) driven by environment changes or internal dynamics.

***

## 7. How to integrate this into Truth concretely

Given your architecture, you’d implement this FSM in three layers:

1. **Schema / data in `law`**
    - Define EDN or Clojure data structures listing states and transitions per category.
    - Example shape:

```clojure
{:category :gas
 :states   #{:gas/nebula :gas/collapsing :gas/condensed-core :gas/protostar :gas/star ...}
 :transitions
 [{:from :gas/nebula
   :to   :gas/collapsing
   :guard law.gas/jeans-collapse?}
  {:from :gas/condensed-core
   :to   :gas/protostar
   :guard law.gas/core-mass-above-h-burning?}
  ...]}
```

    - Each `:guard` is a pure function in `law.*` that inspects a region/body and decides if the transition is allowed.
2. **Mapping to ECS components**
    - You already use `c/matter-state`, `c/disc-tag`, etc. You can:
        - Add new components for environment, atmosphere/EM, biosphere state, and small‑body classification (`c/body-class`).
        - Map FSM state names directly onto those components (e.g. `c/matter-state` carries `:gas/nebula`, `:gas/star`; `c/environment-state` carries `:env/habitable`, etc.).
3. **Domain systems consult the FSM**
    - `domain.stellar/classifier-system` becomes the **gas FSM driver**:
        - It evaluates guards from `law.state_machine.gas` and emits the correct `c/matter-state` transitions.
    - `domain.planet-formation` and `domain.debris` become the **solids FSM drivers**:
        - They move entities between `:solids/planetesimal-population`, `:solids/asteroid-belt`, `:solids/moon`, etc.
    - `domain.atmosphere` and `domain.em` drive **environment + atmosphere/EM FSMs**:
        - They read solar wind flux and EM fields and update `c/environment-state` and `c/atmosphere-state`.
    - `domain.ecology` and `biology` drive the **biosphere FSM**.

Because each system only sees the **states it owns**, and transitions are declared centrally, you get:

- Clear, non‑overlapping conditions for each transition.
- A way to audit the whole lifecycle from nebula to life just by inspecting the FSM definitions.
- A guard against different systems making conflicting assumptions: if two transitions overlap, you’ll see it in the FSM, not discover it only in code.

***

If you’d like, we can next draft an initial `law.state_machine` for one category (say, gas + solids) directly in Clojure, using the actual keywords you already have (`:nebula`, `:protostar`, `:planetesimal`, `:disc-tag`), so you can drop it into `src/law/state_machine.clj` and start wiring domain systems to it incrementally.

<div align="center">⁂</div>

[^12_1]: 02ca0590-0163-420d-a549-97702c7aae03.md

[^12_2]: https://www.aanda.org/articles/aa/full_html/2019/09/aa35278-19/aa35278-19.html


---

# continue

Yes — the next move is to make the FSM **multi-layered and compositional**, not one giant flat enum. Truth’s own formation spec already points in that direction: gas-to-star is resolved, the disk is resolved, and planets are sub-grid, with mass, density, and temperature each governing different transitions instead of one variable standing in for another.

## State layers

A single body should not have just one “state.” It should have a small bundle of orthogonal states, because Mars, Pluto, and the Moon differ along more than one dimension.

I’d split the machine into at least these layers:

- **Matter state** — nebula, condensed core, planetesimal, protostar, star, remnant.
- **Dynamical role** — free body, belt member, satellite, dominant orbital clearer, disk-owned embryo.
- **Composition class** — rocky, icy, volatile-rich, gas-dominated, differentiated, metallic-core.
- **Environment state** — molten, impact-reheated, crusted, ocean world, thin-atmosphere, airless, snowball, greenhouse, subsurface-ocean.
- **Magnetosphere / atmosphere retention state** — no dynamo, weak dynamo, shielded atmosphere, stripping atmosphere, collapsed atmosphere.
- **Biosphere state** — prebiotic, microbial, complex, technological.

That gives you a model where “Mars” is not one label; it is something like:
`matter=:planet`, `role=:primary`, `composition=:rocky-differentiated`, `environment=:thin-atmosphere-cold-arid`, `magnetosphere=:collapsed-dynamo`, `biosphere=:none-or-extinct`. That is much closer to how the world actually works.

## Small-body branch

You’re also right that the missing middle is **not** just “planetesimal → planet.” There needs to be a long-lived small-body branch. The authoritative formation spec already distinguishes planets as a disk sub-grid outcome rather than a direct gas-parcel promotion, which gives you room to insert these intermediate categories cleanly.

A useful solids branch would be:

- `dust/pebbles`
- `rubble-clump`
- `planetesimal`
- `asteroid`
- `comet`
- `protoplanet`
- `dwarf-planet`
- `planet`
- `moon`
- `ring-particle / ring-system`

And the key is that these are **not only mass classes**. For example:

- **Asteroid** = rocky small body, not rounded by self-gravity, not volatile-dominated, free or belt-bound.
- **Comet** = volatile-rich small body whose thermal history allows sublimation/outgassing near periapsis.
- **Dwarf planet** = self-rounded body that has **not** cleared its neighborhood.
- **Moon** = any body whose dominant dynamical relationship is orbiting another non-stellar body inside a stable Hill regime.
- **Ring particle / ring system** = body population inside Roche conditions where reaccretion is suppressed.

That gives you places for Ceres, Pluto, Charon, Europa, Luna, and the asteroid belt without abusing the word “planet.”

## Better environment states

The earlier environment ladder was too coarse. You need states for worlds that cycle, degrade, or remain marginal. A rocky body can cool, become habitable, get hammered, partially remelt, lose atmosphere, freeze, or retain a subsurface ocean. That means environment is not terminal; it is a **reversible regime machine**.

A better environment ladder for rocky/icy worlds:

- `magma-ocean`
- `impact-reset`
- `crusted-volcanic`
- `tectonically-active`
- `ocean-bearing`
- `temperate-habitable`
- `arid-thin-atmosphere`
- `airless-inert`
- `snowball`
- `runaway-greenhouse`
- `tidally-heated-ocean-interior`
- `subsurface-ocean`
- `cryovolcanic-ice-world`

This is where Mars fits: not snowball, not runaway greenhouse, but something like `arid-thin-atmosphere`, possibly after passing through `ocean-bearing` or `temperate-habitable`. Pluto fits as `cryovolcanic-ice-world` or `subsurface-ocean` potential plus `thin/collapsible atmosphere`. The Moon fits `airless-inert`, though early on it may have passed through `magma-ocean`. These are distinct physical regimes, not just narrative labels.

## Atmosphere and EM branch

Your instinct about the EM field is exactly right. The stellar-wind spec in Truth already frames winds as real gas parcels shed by stars, with the larger invariant that collapsed bodies never return to nebula and that mass loss changes classification down a remnant ladder rather than dissolving objects back into gas.  What’s missing is the **planet-facing side**: how wind, XUV, gravity, and magnetism change atmospheric state.

That branch should look something like:

- `no-atmosphere`
- `transient-outgassed-atmosphere`
- `stable-secondary-atmosphere`
- `dense-volatile-atmosphere`
- `collapsing-atmosphere`
- `actively-stripped-atmosphere`
- `frozen-atmosphere`
- `runaway-escape`

And independently:

- `no-dynamo`
- `episodic-dynamo`
- `stable-dynamo`
- `magnetosphere-compressed`
- `magnetosphere-failed`

Then transitions are driven by explicit conditions:

- escape velocity vs thermal speed
- wind/XUV flux at orbit
- volatile inventory
- outgassing rate
- impact delivery/removal
- dynamo power from core convection / rotation
- tidal heating contribution

This gives you a physically meaningful Mars path:
`stable-secondary-atmosphere + episodic/weak dynamo → actively-stripped-atmosphere → collapsing-atmosphere → arid-thin-atmosphere`. It also lets you model worlds that periodically recover atmosphere from volcanism or impacts.

## Moons, Jupiter, and habitability modifiers

Some things should not be states of the world itself, but **contextual modifiers** on transitions. The Moon and Jupiter are like that. We are not certain Earth needed them for life, but they plausibly influence impact rates, obliquity stability, tides, and long-term climate. Those are transition modifiers, not identity classes.

I’d add contextual modifiers such as:

- `has-large-moon`
- `has-strong-tidal-driving`
- `inside-giant-shielded-system`
- `inside-giant-destabilized-system`
- `late-heavy-bombardment`
- `resonant-migration-history`

Then the world’s transition guards can reference them:

- `temperate-habitable → impact-reset` more likely under intense bombardment.
- `temperate-habitable → climate-chaotic` more likely without obliquity stabilizers.
- `subsurface-ocean` more likely with tidal heating from giant-planet resonance.
- `stable-secondary-atmosphere → actively-stripped-atmosphere` faster around active stars and weaker magnetospheres.

So Jupiter and Luna don’t need to be “special-case hacks”; they become part of the causal context around a world.

## Non-overlapping classification

The cleanest way to avoid mushy overlap is to define a few hard rules:

- **Matter state** answers: what is this thing physically made of / bound as?
- **Role state** answers: what is its orbital/social position in the system?
- **Environment state** answers: what regime is its surface/interior in right now?
- **Atmosphere/EM state** answers: can it hold and protect gas?
- **Biosphere state** answers: what level of life exists?

Each layer gets one active state at a time, and each layer has ordered guard precedence. For example, in environment:

1. If global melt fraction exceeds threshold, `magma-ocean`.
2. Else if bombardment energy exceeds reset threshold, `impact-reset`.
3. Else if surface pressure ~0, `airless-inert`.
4. Else if volatile collapse temperature exceeded globally, `snowball` or `frozen-atmosphere`.
5. Else if runaway radiative forcing threshold exceeded, `runaway-greenhouse`.
6. Else if liquid-water window + stable pressure, `temperate-habitable`.
7. Else `arid-thin-atmosphere` or `crusted-volcanic` depending on water and heat flux.

That ordering is what prevents ambiguity.

## Truth-shaped canonical arc

If we shape this specifically to Truth, the high-level arc becomes:

- **Nebula phase**: diffuse gas, collapse, cores, dominant star formation, disk tagging, Toomre/Q regimes.
- **Disk solids phase**: condensation fronts, snow line, dust enrichment, streaming instability, planetesimals, belts, embryos.
- **Planet architecture phase**: planets, dwarf planets, moons, rings, scattering, migration, bombardment history.
- **Planetary evolution phase**: differentiation, magma oceans, crust formation, atmosphere gain/loss, EM field evolution, hydrosphere states.
- **Habitability phase**: transient habitability, stable habitability, marginal habitability, post-habitable decline.
- **Life phase**: prebiotic chemistry, microbial biosphere, complex biosphere, intelligent life, post-biological or extinct branches.

That is a real “nebula to life” machine, and it has enough room for Pluto, Mars, Luna, Europa, asteroids, comets, and failed worlds.

## What to define next

The next useful artifact is not more prose; it’s a **canonical state catalog**. Truth needs one table per layer with:

- state name
- owning system
- required observables
- entry guards
- exit guards
- allowed successor states

Start with just these four layers:

- matter
- role
- environment
- atmosphere/EM

Those four will already let you place Mars, Pluto, the Moon, asteroids, comets, and habitable-but-fragile worlds without ambiguity, while staying aligned with the formation spec’s resolved gas / resolved disk / sub-grid planets architecture.

Would you like me to draft that catalog next as an actual Truth-style spec table, starting with `matter-state` and `environment-state`?

