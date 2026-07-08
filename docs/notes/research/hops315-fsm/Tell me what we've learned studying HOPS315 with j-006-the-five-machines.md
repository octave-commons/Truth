---

# continue

Good. Let’s keep going and make this more **Truth-native**: not just “a list of nice categories,” but a canonical catalog you can actually design systems around.

The core change I’d make is this:

**Do not model the whole universe as one finite state machine. Model it as a stack of coupled FSMs, each owning one question.**

That is the only way this stays sane.

Truth’s formation docs already separate resolved gas, resolved disk, and sub-grid planets, and they explicitly argue that mass sets fate, density sets condensation, and temperature sets ignition rather than one variable pretending to do all three jobs.  That same principle should govern the entire arc from nebula to life.

## The five machines

I would define five canonical state machines:

1. **Matter FSM** — what kind of physical body/medium is this?
2. **Role FSM** — what is its dynamical relationship in the system?
3. **Environment FSM** — what regime is its surface/interior in?
4. **Atmosphere/EM FSM** — can it hold/protect an atmosphere, and how is it losing it?
5. **Biosphere FSM** — what level of life, if any, exists?

Each machine has:

- one active state per entity,
- a strict owner system,
- explicit entry guards,
- explicit exit guards,
- allowed successors only.

That prevents the “Mars problem,” where one label has to carry composition, climate, atmosphere loss, and history all at once.

## 1. Matter FSM

This is the deepest machine. It answers: **what physically is this thing right now?**

I’d propose these canonical states:

- `:matter/nebula`
- `:matter/collapsing-gas`
- `:matter/condensed-core`
- `:matter/protostar`
- `:matter/star`
- `:matter/stellar-remnant`
- `:matter/dust-field`
- `:matter/pebble-field`
- `:matter/planetesimal`
- `:matter/asteroid`
- `:matter/comet`
- `:matter/protoplanet`
- `:matter/dwarf-planet`
- `:matter/planet`
- `:matter/moon`
- `:matter/gas-giant`
- `:matter/ice-giant`
- `:matter/brown-dwarf`
- `:matter/ring-particle`
- `:matter/debris-cloud`

Some of these are close cousins, but that is fine because the point is to be *clear*, not minimal.

### Guard philosophy

Matter transitions should be based on **hard physical criteria**:

- binding / self-gravity,
- dominant composition,
- hydrostatic roundness,
- volatile fraction,
- stellar ignition state,
- whether the thing is a population field or a resolved body.

Examples:

- `nebula → collapsing-gas` only by Jeans-like instability or equivalent collapse criterion.
- `collapsing-gas → condensed-core` only by density/core threshold.
- `condensed-core → protostar / brown-dwarf / giant-core` by mass tier.
- `dust-field → pebble-field` by coagulation/sticking regime.
- `pebble-field → planetesimal` by streaming instability or other collapse of solids, which is exactly the kind of sub-grid bridge your disk research motivates.[^14_1]
- `planetesimal → asteroid` when it is a persistent non-rounded rocky small body.
- `planetesimal → comet` when it is volatile-rich and thermally active under stellar heating.
- `protoplanet → dwarf-planet / planet` depending on roundness plus orbit-clearing role.
- `planet → debris-cloud` only through catastrophic disruption, never by “quietly un-being.”

The important thing is that **matter states are about physical identity, not orbital context**. A moon is tricky here because “moon” is really role, not matter. So if you want maximum purity, you can move `:matter/moon` out of Matter FSM entirely and let it live only in Role FSM. I think that is cleaner.

## 2. Role FSM

This answers: **what is this body’s dynamical role?**

Canonical states:

- `:role/free-body`
- `:role/disk-embedded`
- `:role/belt-member`
- `:role/scattered-body`
- `:role/orbit-clearer`
- `:role/satellite`
- `:role/co-orbital`
- `:role/ring-member`
- `:role/resonant-member`
- `:role/interstellar-escape`

Now Pluto stops being awkward. Pluto can be:

- `matter = dwarf-planet`
- `role = belt-member` or `resonant-member`
- `environment = icy-volatile-world`
- `atmosphere = frozen/collapsible`
- `biosphere = none`

Likewise Luna becomes:

- `matter = rocky-planetary-body` or `dwarf-planetary-body`
- `role = satellite`
- `environment = airless-inert`
- `atmosphere = none`
- `biosphere = none`

That is much better than trying to force “moon” into a giant one-dimensional category tree.

### Key role transitions

- `disk-embedded → free-body` when gas disperses and orbit decouples from disk torques.
- `free-body → belt-member` when growth stalls and it remains one of many in a shared population.
- `free-body → orbit-clearer` when it dominates local mass and scattering.
- `free-body → satellite` by capture or impact-origin disk reaccretion.
- `belt-member → scattered-body` by giant-planet perturbations.
- `scattered-body → interstellar-escape` if unbound.

This is where Jupiter’s importance lives. Jupiter is not just “a gas giant”; it is also a **strong scatterer** whose presence changes the transition probabilities of other bodies between `belt-member`, `scattered-body`, `impacting-body`, and `cleared-region`.

## 3. Environment FSM

This answers: **what regime is the world’s surface/interior in right now?**

This is where you were absolutely right to push back. “Habitable / snowball / greenhouse” is way too coarse.

I’d define:

- `:env/magma-ocean`
- `:env/impact-reset`
- `:env/crusted-volcanic`
- `:env/tectonic-temperate`
- `:env/ocean-world`
- `:env/arid-thin-atmosphere`
- `:env/airless-inert`
- `:env/snowball`
- `:env/runaway-greenhouse`
- `:env/icy-volatile-world`
- `:env/subsurface-ocean`
- `:env/tidally-heated`
- `:env/cryovolcanic`
- `:env/temperate-habitable`
- `:env/post-habitable`

Now you have places for:

- **Mars**: `arid-thin-atmosphere`, possibly previously `ocean-world` or `temperate-habitable`.
- **Moon**: `airless-inert`, formerly `magma-ocean`.
- **Pluto**: `icy-volatile-world`, maybe `cryovolcanic`, maybe `subsurface-ocean`.
- **Europa**: `subsurface-ocean + tidally-heated`.
- **Io**: `tidally-heated + crusted-volcanic`.
- **Early Earth**: `impact-reset → crusted-volcanic → ocean-world → temperate-habitable`.


### Ordered guard precedence

This machine must be **priority ordered**, because multiple conditions can be true.

A good precedence order would be:

1. If melt fraction above threshold → `magma-ocean`.
2. Else if bombardment reset flux above threshold → `impact-reset`.
3. Else if no persistent atmosphere and no volatiles → `airless-inert`.
4. Else if ice globally stable and surface liquid unstable → `snowball` or `icy-volatile-world`.
5. Else if runaway radiative forcing exceeded → `runaway-greenhouse`.
6. Else if liquid solvent stable and pressure/temperature window holds → `temperate-habitable`.
7. Else if liquid solvent once existed but atmosphere collapsed → `post-habitable` or `arid-thin-atmosphere`.
8. Else use tectonic/cryovolcanic/internal-heat states.

That ordering avoids overlap.

## 4. Atmosphere / EM FSM

This answers: **what is happening to the atmosphere, and how shielded is it?**

Your atmosphere research already points toward regime transitions in atmospheric escape rather than one universal stripping law. It explicitly distinguishes energy-limited, recombination-limited, photon-limited, and blow-off regimes for XUV-driven escape.  That is exactly the kind of thing this FSM should own.

I would split this into two linked submachines.

### A. Magnetosphere FSM

- `:mag/no-dynamo`
- `:mag/episodic-dynamo`
- `:mag/stable-dynamo`
- `:mag/compressed-magnetosphere`
- `:mag/collapsed-magnetosphere`

Guards depend on:

- core heat flux,
- rotation rate,
- conductive/convective interior state,
- stellar wind pressure.


### B. Atmosphere FSM

- `:atm/none`
- `:atm/transient-outgassed`
- `:atm/stable-secondary`
- `:atm/dense-volatile`
- `:atm/collapsing`
- `:atm/actively-stripped`
- `:atm/frozen`
- `:atm/blowoff`
- `:atm/xuv-energy-limited`
- `:atm/xuv-recombination-limited`
- `:atm/xuv-photon-limited`

The last three are useful because your research specifically identifies those escape regimes and their transition criteria. The draft notes a critical XUV transition around $10^4$ erg cm$^{-2}$ s$^{-1}$ for hot-Jupiter-like conditions, with regime determined by the ratio of recombination to flow timescales rather than flux alone.

That means Mars-like and close-in exoplanet-like worlds can share a common atmospheric loss framework while occupying different states.

### Why this matters

Now the atmosphere story becomes explicit:

- A world can be `stable-secondary`.
- Then stellar activity increases or dynamo weakens.
- It transitions to `actively-stripped`.
- Depending on XUV and gravity it may enter `xuv-energy-limited` or `xuv-photon-limited`.
- Eventually it becomes `collapsing` or `none`.

That gives you a principled place for solar wind and EM fields without entangling them directly with “is this world habitable?”

## 5. Biosphere FSM

This should stay simple at first.

- `:bio/none`
- `:bio/prebiotic`
- `:bio/microbial`
- `:bio/complex`
- `:bio/technological`
- `:bio/extinct`
- `:bio/post-biological`

The key rule is that this FSM only runs when the environment/atmosphere machines say the world is eligible. A biosphere should not have to compute whether the world is airless; it should be given that by upstream machines.

## Transition modifiers

Now we need the piece you were intuiting with Jupiter and the Moon.

Some factors should not be states at all. They should be **modifiers** that bias transition rates or thresholds.

Examples:

- `:modifier/has-large-moon`
- `:modifier/strong-tidal-heating`
- `:modifier/giant-planet-shielding`
- `:modifier/giant-planet-scattering`
- `:modifier/heavy-bombardment-era`
- `:modifier/resonant-chaos`
- `:modifier/volatile-rich-feedstock`
- `:modifier/late-migration-history`

These affect transitions like:

- `impact-reset ↔ crusted-volcanic`
- `ocean-world → temperate-habitable`
- `stable-secondary → actively-stripped`
- `belt-member → scattered-body`
- `subsurface-ocean → cryovolcanic`

This is the elegant place to model “life might depend on Jupiter/Moon” without turning those into magical booleans.

## Ownership in Truth

To keep complexity bounded, each FSM should have a single owner:

- `domain.stellar` owns Matter FSM for gas/star states.
- `domain.planet-formation` owns Matter + Role transitions for solids and embryos.[^14_1]
- `domain.regime` or a new `domain.environment` owns Environment FSM.
- `domain.em` + `domain.atmosphere` own Magnetosphere and Atmosphere FSMs, especially XUV/wind loss.
- `domain.ecology` / `biology` own Biosphere FSM.

Then each domain is only responsible for **its transitions**, not the whole world.

## Canonical entity examples

This is where the model proves itself.

**Earth**

- matter: `planet`
- role: `orbit-clearer`
- environment: `temperate-habitable`
- magnetosphere: `stable-dynamo`
- atmosphere: `stable-secondary`
- biosphere: `technological`

**Mars**

- matter: `planet`
- role: `orbit-clearer`
- environment: `arid-thin-atmosphere` or `post-habitable`
- magnetosphere: `collapsed-magnetosphere`
- atmosphere: `collapsing` or `actively-stripped`
- biosphere: `none` or maybe ancient `microbial`

**Moon**

- matter: `rocky-body`
- role: `satellite`
- environment: `airless-inert`
- magnetosphere: `no-dynamo`
- atmosphere: `none`
- biosphere: `none`

**Pluto**

- matter: `dwarf-planet`
- role: `belt-member` / `resonant-member`
- environment: `icy-volatile-world`
- magnetosphere: `no-dynamo`
- atmosphere: `frozen` or tenuous seasonal
- biosphere: `none`

**Europa**

- matter: `moon`
- role: `satellite`
- environment: `subsurface-ocean`
- magnetosphere: `induced/weak`
- atmosphere: `none/thin`
- biosphere: maybe `prebiotic` or `microbial`

The FSM supports all of these cleanly.

## Next drafting move

The next concrete step is to write a **Truth-style canonical catalog** with rows like:

- state id
- owning system
- observables required
- entry condition
- exit condition
- allowed successors
- notes / examples

Start with exactly two tables first:

1. **Matter FSM**
2. **Environment FSM**

Those two will do most of the conceptual heavy lifting, and they already align with the repo’s canonical formation direction and your desire for model-of-models design.

Would you like me to draft those two tables in full next, as if they were going into `docs/specs/`?

<div align="center">⁂</div>

[^14_1]: https://www.aanda.org/articles/aa/full_html/2019/09/aa35278-19/aa35278-19.html


