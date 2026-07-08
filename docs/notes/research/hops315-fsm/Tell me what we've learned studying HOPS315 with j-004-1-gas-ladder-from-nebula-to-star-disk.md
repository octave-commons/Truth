---

# I meant like, what are we missing? stepping away from what is actually implemented right now. I think this state transition diagram is going to be key to designing this thing from start to finish. Our system is model of models, determined by the state the entity in question currently exists in.

Given what you can discover about truth from our design documents, I want a detailed state machine that gets us from nebula, to life. I think a more complex state machine, helps us keep the physics from getting too insanely complicated, so long as each transition has very clear conditions, non overlapping conditions with other states.

You’re right that the state machine is the backbone: if we get the ladder and the conditions clean, the physics modules become manageable “models of models,” each tied to a well‑defined state rather than trying to simulate everything everywhere.

Below is a conceptual finite state machine from **nebula → life**, grounded in your design docs and current astrophysics, but stepping away from implementation details. Each state has:

- A clear physical meaning.
- Non‑overlapping entry conditions.
- A set of allowed transitions.

Think of this as multiple coupled ladders:

- A **gas ladder** (nebula → cores → star + disk).
- A **disk/solids ladder** (dust → planetesimals → planets).
- A **planet environment ladder** (molten → stable crust/ocean → habitable).
- A **biosphere ladder** (prebiotic → microbial → complex life → civilization).

***

## 1. Gas ladder: from nebula to star + disk

### States

1. **`NebularGas`**
Diffuse molecular cloud / SPH parcels, with density below the core condensation threshold and Jeans‑stable (self‑gravity < support).
2. **`CollapsingGas`**
Parcels that are **Jeans‑unstable** (self‑gravity beats pressure/turbulence) and moving toward core densities but have not yet formed a bound core.
3. **`CondensedCore`**
A bound, non‑diffuse object formed by gas collapse: a “first core” with material density instead of SPH density. Mass sets which branch it belongs to:
    - Sub‑opacity limit ⇒ proto‑planetesimal mass tier.
    - Opacity–deuterium ⇒ gas‑giant embryo tier.
    - Deuterium–hydrogen ⇒ brown‑dwarf tier.
    - ≥ hydrogen‑burning mass ⇒ protostellar core.[^10_1]
4. **`Protostar`**
Collapsed core above the hydrogen‑burning mass, accreting gas, not yet stably burning hydrogen.
5. **`StarWithEnvelopeAndDisk`**
A main‑sequence star plus a rotationally‑supported disk and any remaining envelope: H‑burning is active, and disk mass is non‑zero.[^10_2][^10_1]
6. **`BrownDwarfCore` / `GiantPlanetCore`**
Collapsed gas cores below hydrogen‑burning mass: either brown‑dwarfs (deuterium‑burning band) or giant planets (opacity‑limited band).[^10_1]

### Transitions \& conditions

- **NebularGas → CollapsingGas**
    - Condition: local parcel region satisfies Jeans instability: $M/R$ above a threshold or Toomre‑like criteria in the cloud.
    - No core yet; still SPH representation, but tagged as collapsing.
- **CollapsingGas → CondensedCore**
    - Condition: density ≥ core condensation density **or** parcel has accreted more than one gas particle mass.
    - Isolation criterion: parcel not inside an existing sink’s accretion radius (so you don’t condense everywhere once the first core appears).
- **CondensedCore → Protostar / BrownDwarfCore / GiantPlanetCore / PlanetesimalMassTier**
    - Condition: set purely by mass tier:
        - ≥ H‑burning limit ⇒ `Protostar`.
        - Between deuterium and H‑burning ⇒ `BrownDwarfCore`.
        - Between opacity limit and deuterium ⇒ `GiantPlanetCore`.
        - Below opacity limit ⇒ assigned to solids ladder as planetesimal mass tier.[^10_1]
- **Protostar → StarWithEnvelopeAndDisk**
    - Condition: core mass ≥ H‑burning limit **and** fusion criteria met (temperature \& structure above ignition threshold; hydrogen fraction sufficient).
    - Once a star, fusion‑sustaining hysteresis prevents flickering back to protostar on small mass dips.
- **Protostar / BrownDwarfCore / GiantPlanetCore ↔ up/down ladder by accretion and mass loss**
    - Gains mass via capture within accretion radius (Bondi + latched feeding zone).
    - Loses mass via stellar winds, stripping, or feedback.[^10_1]

From this ladder you get one **dominant star with an accreting disk**, plus a handful of substellar companions.

***

## 2. Disk ladder: regimes of the protoplanetary disk

Around `StarWithEnvelopeAndDisk`, the disk itself has a **regime state machine** based on Toomre Q, cooling time, and solid surface density.[^10_3][^10_4][^10_1]

### Disk regime states (per annulus)

1. **`GravitoTurbulentDisc`**
$Q \sim 1$, cooling time long ($\beta = t_{\rm cool}\Omega \gtrsim 3$): self‑gravity present but stabilized by turbulence; no fragmentation.[^10_1]
2. **`FragmentingDisc`**
$Q \le 1$ and $\beta \lesssim 3$: disk locally unstable and able to fragment into clumps (GI channel).[^10_1]
3. **`StableDisc`**
$Q > 1$, solids present but not enough to form cores; typical gas‑dominated annulus.[^10_1]
4. **`CoreAccretionZone`**
$Q > 1.5$ and solid surface density $\Sigma_{\rm solid} > 0$: conditions favor **core accretion**, either via planetesimal or pebble accretion.[^10_4][^10_3]

### Disk regime transitions

- **StableDisc ↔ GravitoTurbulentDisc**
    - Controlled by changes in $\Sigma$, $c_s$, and cooling: increasing mass or cooling moves toward gravito‑turbulent; heating or mass loss moves back to stable.
- **GravitoTurbulentDisc → FragmentingDisc**
    - When cooling time becomes short enough ($\beta \lesssim 3$), disk can fragment into GI clumps (embryos / companions).[^10_1]
- **StableDisc / GravitoTurbulentDisc → CoreAccretionZone**
    - When solids condense and pile up (e.g. beyond snow line), making $\Sigma_{\rm solid}$ significant while Q stays >1; this marks radial bands where planet cores can form efficiently.[^10_3][^10_4]

Each annulus’ regime selects **which planet‑formation channel is active**:

- GI clumps in `FragmentingDisc`.
- Core accretion in `CoreAccretionZone`.
- Mostly transport/turbulence in `StableDisc` / `GravitoTurbulentDisc`.

***

## 3. Solids ladder: from dust to planets

This ladder covers the fate of condensed solids in the disk.

### States

1. **`DustAndVapourField`**
Silicates, metals, and ices in dust and vapour form, carried as fields by gas annuli; condensation sequence depends on temperature and pressure.[^10_4][^10_3]
2. **`CondensedSolids`**
Local temperature drops below condensation thresholds (e.g. silicates near HOPS‑315, ices beyond snow line), so dust fraction increases and solids exist as grains/pebbles.[^10_5][^10_2][^10_4]
3. **`StreamingClumps`**
Conditions for streaming instability are met: midplane dust‑to‑gas ratio >1 and Stokes numbers $\tau_s \sim 0.1–1$. Dense filaments form.[^10_3][^10_4]
4. **`Planetesimals`**
Gravitational collapse of streaming clumps into 100–1000 km bodies ($\sim 10^{18}–10^{21}\,\mathrm{kg}$), plus your seeded $10^{16}\,\mathrm{kg}$ bodies.[^10_4][^10_3]
5. **`Protoplanets`**
Larger bodies (Mars to super‑Earth) formed by collisional growth of planetesimals; often tied to specific annuli in `CoreAccretionZone`.[^10_3]
6. **`GiantPlanetEmbryos`**
Solid cores that reach ~10–15 M⊕ and start runaway gas accretion from the disk (classical core accretion).[^10_3]
7. **`FinalPlanets`**
Stable planets: terrestrial, ice giants, gas giants, with orbits and compositions set by formation channel and annulus.[^10_4][^10_3]

### Transitions \& conditions

- **DustAndVapourField → CondensedSolids**
    - Condition: local temperature passes condensation thresholds for specific elements (silicates, metals, ices); dust fraction increases.[^10_2][^10_5][^10_4]
- **CondensedSolids → StreamingClumps**
    - Condition: midplane solids enriched (dust‑to‑gas >1), particle stopping times in streaming‑unstable regime; strong gas‑solid coupling.[^10_4][^10_3]
- **StreamingClumps → Planetesimals**
    - Condition: clump densities exceed gravitational collapse criteria; collapse yields discrete planetesimals.[^10_3][^10_4]
- **Planetesimals → Protoplanets**
    - Driven by collisional growth:
        - Collision rate set by number density, velocity dispersion, and gravitational focusing.
        - Sticking vs fragmentation determined by relative velocity and material strength.[^10_6][^10_7][^10_4]
- **Protoplanets → GiantPlanetEmbryos**
    - Condition: solid core mass exceeds critical core mass (~10–15 M⊕), disk still gas‑rich; runaway gas accretion begins.[^10_3]
- **Protoplanets / GiantPlanetEmbryos → FinalPlanets**
    - Gas clears (disk dispersal), migration and dynamical sculpting end; survivors freeze into stable orbits and become `FinalPlanets`.

This ladder lets you control complexity: each state has **one dominant module** (condensation, streaming, collision, gas accretion) instead of mixing them.

***

## 4. Planet environment ladder: from melted rock to habitable surface

Once you have `FinalPlanets`, each has an **environment state machine** that determines whether life can arise.

### States

1. **`MoltenWorld`**
Newly formed planet: high internal heat, magma ocean, heavy bombardment, atmosphere dominated by outgassing.
2. **`CoolingCrustWorld`**
Solid crust forms, volcanism active, atmosphere evolving (CO₂, N₂, H₂O), potential early oceans.
3. **`StableCrustAndOceanWorld`**
Long‑lived crust, liquid water present (if temperature and pressure in the right band), stable atmospheric composition on Myr timescales.
4. **`HabitableWorld`**
Surface temperature allows long‑term liquid water; energy flux (stellar insolation + internal heat) and atmospheric pressure stable enough for chemistry and biology; often within some “habitable zone” of the star.
5. **`RunawayGreenhouseWorld` / `SnowballWorld`**
Non‑habitable extremes: runaway greenhouse (too much greenhouse gas, too much insolation) or global glaciation.

### Transitions \& conditions

- **MoltenWorld → CoolingCrustWorld**
    - Condition: interior cooling and radiative losses drop surface temperature below silicate melting; large impacts become rare enough not to reheating entire surface.
- **CoolingCrustWorld → StableCrustAndOceanWorld**
    - Condition: atmosphere and hydrosphere settle; outgassing and impact flux go down; oceans stabilize.
- **StableCrustAndOceanWorld → HabitableWorld**
    - Condition: long‑term climate stability within a window (e.g. ~273–373 K at surface), presence of liquid water, moderate UV/X‑ray flux, and chemical inventory suitable for prebiotic chemistry.
- **HabitableWorld ↔ RunawayGreenhouseWorld / SnowballWorld**
    - Controlled by changes in greenhouse gases, stellar flux, albedo, and feedbacks; extreme shifts push the planet out of the life‑friendly window.

This ladder is where your **habitability and climate modules** live; each environment state picks different physics: radiative‑convective atmosphere, interior cooling, ice/albedo feedback, etc.

***

## 5. Biosphere ladder: from chemistry to civilization

Finally, on habitable planets you attach a **biosphere state machine**.

### States

1. **`PrebioticChemistry`**
Complex organic chemistry (e.g. in oceans or atmosphere) but no self‑replicating life; driven by energy flux and environment.
2. **`MicrobialBiosphere`**
Stable microbial ecosystems; life influences atmosphere and surface chemistry (e.g. oxygenation, methane cycles).
3. **`ComplexBiosphere`**
Multicellular, macroscopic life; ecosystems with trophic structure, significant impact on albedo and atmospheric composition.
4. **`CivilizationalBiosphere`**
Technological civilization: engineered structures, high‑rate energy use, deliberate modifications of environment; interacts with culture and myth layers.
5. **`PostBiosphereStates`**
Extinction, collapse, or transformation into non‑biological agent networks.

### Transitions \& conditions

- **HabitableWorld → PrebioticChemistry**
    - Condition: presence of liquid solvent (water), energy gradients (redox, UV, geothermal), and organic building blocks; timescale ~10⁸–10⁹ yr.
- **PrebioticChemistry → MicrobialBiosphere**
    - Emergent transition once self‑replication and evolution appear; in simulation terms, triggered by reaching certain complexity metrics in chemistry module.
- **MicrobialBiosphere → ComplexBiosphere**
    - Condition: long‑term environmental stability, availability of oxygen or other energy sources, evolutionary time.
- **ComplexBiosphere → CivilizationalBiosphere**
    - Condition: emergence of intelligence, tool use, and culture; in Truth, this is where your `ACTORS` and Myth Engine attach.
- **Any biosphere state → PostBiosphereStates**
    - Major changes in climate, stellar evolution, or self‑inflicted collapse.

Each biosphere state maps to different simulation models: geochemical cycles, population dynamics, cultural dynamics, etc.

***

## 6. How this ties into “model of models”

For Truth, you can treat **each state** above as:

- A **tag** on an entity or region (`matter-state`, `disc-regime`, `planet-environment`, `biosphere-level`).
- A **selector** for which physics model is active:
    - `NebularGas` → SPH + Jeans.
    - `StarWithEnvelopeAndDisk` → stellar evolution + disk viscous evolution.
    - `CoreAccretionZone` → planet‑seeder + collisional growth.
    - `HabitableWorld` → atmosphere/ocean/climate models.
    - `CivilizationalBiosphere` → ecology + agent/culture models.

Because conditions for moving between states are **clear and non‑overlapping** (mass tiers, Q thresholds, condensation temperatures, climate windows), you can avoid tangled if‑trees inside each module:

- Modules can assert “I only run when state = X,” and state transitions are the only place where you mix criteria.
- The FSM becomes the conceptual skeleton that keeps physics complexity bounded: you never try to run planetesimal streaming and stellar fusion and biosphere ecology on the same object at the same time.

If you’d like, next we can take this conceptual FSM and write it as an explicit Clojure data structure (states, transitions, guards), so it can live alongside your `law` namespace and drive which `domain/*` systems apply where in the sim.

<div align="center">⁂</div>

[^10_1]: https://arxiv.org/pdf/1803.00575.pdf

[^10_2]: https://skyandtelescope.org/astronomy-news/webb-space-telescope-spies-baby-planetary-system/

[^10_3]: https://www.aanda.org/articles/aa/full_html/2019/09/aa35278-19/aa35278-19.html

[^10_4]: https://iopscience.iop.org/article/10.1088/0004-637X/764/2/146

[^10_5]: https://www.almaobservatory.org/en/press-releases/for-the-first-time-astronomers-witness-the-dawn-of-a-new-solar-system/

[^10_6]: https://www.aanda.org/articles/aa/full_html/2011/04/aa15378-10/aa15378-10.html

[^10_7]: https://iopscience.iop.org/article/10.3847/1538-4357/ad05b9


---

# I think we could spend more time in between, like astroids, commits, how would you classify our moon? I don't know... then there is pluto. We aren't sure if it was possible for life to survive here with out our moon, or with out jupiter. A rocky body who is getting hit with astroids all the time might cool down, become perhaps habitable for a while, then become at least partially molten again, etc tere are a lot of outcomes. Then there is mars. That doesn't fit into any category you gave us. it's not a snow ball, it's not runaway green house. It was likely habitable at one point, but... and this is why I added the EM field,  it's possible it's atmosphere was striped away by the solar wind. Which, I don't think we are modeling solar wind correctly right now, but I think we have some good research, maybe a spec, on how we want to do that

You’re pointing at exactly the right place to add richness: the **intermediate body and environment states** between “planet” and “no planet.” If we treat “planet” as a monolith, Mars, the Moon, Pluto, asteroid belts, and comet populations all end up either shoehorned or ignored. A more detailed state machine gives each of those its own, non‑overlapping bucket, and keeps the physics modules scoped to the right regime.[^11_1][^11_2]

Below I’ll sketch that extended FSM in terms of *axes* and *states*, then show how Mars, the Moon, and Pluto land in it, and highlight what’s missing today in Truth.

***

## 1. Axes we should classify on

Rather than a single ladder, think of a few orthogonal axes:

- **Mass \& binding tier**
    - Micro: dust, pebbles.
    - Small: planetesimals, asteroids, comets.
    - Medium: moons, dwarf planets.
    - Large: full planets, gas/ice giants, stars.
- **Composition \& volatility**
    - Rocky/silicate‑dominated.
    - Icy (water/volatile ices).
    - Mixed (rock + ice + gas).
- **Dynamical role**
    - Primary (star/planet).
    - Satellite (moon).
    - Belt/distinct population (asteroid/comet belts).
- **Atmosphere \& EM protection**
    - No atmosphere.
    - Thin/unprotected atmosphere.
    - Thick, protected atmosphere (magnetosphere / high gravity).
- **Surface environment**
    - Molten.
    - Impact‑dominated, frequently reheated.
    - Stable crust/ocean, temperate.
    - Frozen or baked extremes.

Every **state** in the FSM is essentially a particular combination of these axes. Transitions are changes along one or more axes (mass up/down, atmosphere gained/lost, EM field strengthened/weakened).

***

## 2. Extended solids ladder: dust → asteroids → moons → dwarf planets → planets

### Key body states

1. **`DustPebbleField`**
All solids are dust or pebbles, dynamically coupled to gas. This is your disk’s solids field.[^11_2]
2. **`PlanetesimalPopulation`**
Collapsed 100–1000 km bodies (your seeded $10^{16}\,\mathrm{kg}$ plus streaming‑instability clumps). They are building blocks, not yet “planets” or “moons.”[^11_2]
3. **`AsteroidBelt` / `IcySmallBodyBelt`**
A gravitationally bound population of planetesimals that never grew into planets:
    - Rocky belt (like the main asteroid belt).
    - Icy belt (like Kuiper belt / scattered disk).
4. **`CometPopulation`**
Small icy bodies on eccentric or inclined orbits; dynamically distinct from belts.
5. **`Moon` (SatelliteRock/Ice)**
Medium‑mass rocky or icy body bound to a planet (or, rarely, binary dwarf‑planet pair).
6. **`DwarfPlanet`**
Body that:
    - Is large enough for self‑gravity to make it round.
    - Has not cleared its orbit (still shares with belt populations).
    - May or may not have a moon.
7. **`FullPlanet`**
Body that:
    - Is round.
    - Has cleared its orbital zone (dominates local mass).
    - May be rocky, icy, or gaseous.

### Transitions

- **DustPebbleField → PlanetesimalPopulation**
    - Via streaming instability and gravitational collapse, or seed‑and‑grow condensation as in HOPS‑315‑like inner disks.[^11_2]
- **PlanetesimalPopulation → AsteroidBelt / IcySmallBodyBelt / CometPopulation**
    - Depending on:
        - Location (inner vs outer disk).
        - Dynamical heating (giant‑planet perturbations).
        - Collisional history (fragmentation vs growth).
- **PlanetesimalPopulation \& belts → Moon / DwarfPlanet / FullPlanet**
    - Through hierarchical collisional growth and core accretion:
        - Some bodies grow enough to be round ⇒ `DwarfPlanet`.
        - Some are captured into orbits around larger worlds ⇒ `Moon`.
        - Some clear their neighborhoods ⇒ `FullPlanet`.
- **AsteroidBelt / CometPopulation → debris or captured moons**
    - Individual bodies can be captured (becoming moons),
disrupted (becoming debris), or accreted onto planets.

This ladder is where you explicitly place “asteroid belt”, “Kuiper belt”, “moons”, and “dwarf planets” rather than letting them fall under a generic “planet/planetesimal” label.

***

## 3. Richer planet environment ladder: Earth, Mars, Moon, Pluto, etc.

Your earlier environment ladder had `MoltenWorld`, `StableCrustAndOceanWorld`, `HabitableWorld`, `RunawayGreenhouseWorld`, `SnowballWorld`. We should expand it to cover Mars‑like and Pluto‑like cases and worlds battered by impacts.

### Environment states (for any rocky/icy body: planet, moon, dwarf planet)

1. **`MoltenSurfaceWorld`**
Global magma ocean; frequent big impacts; atmosphere dominated by vaporized rock and volatiles.
2. **`ImpactReheatedWorld`**
Mostly solid crust but repeatedly reset by heavy bombardment; cycles of partial melting and cooling, like Hadean Earth or an unlucky small planet under prolonged asteroid/comet barrage.
3. **`ThickAtmosphereWorld`**
Substantial atmosphere, either:
    - Greenhouse‑regulated (Earth, early Venus).
    - High‑pressure ice/gas envelopes (super‑Earths, early ice giants).
4. **`ThinAtmosphereWorld`**
Atmosphere present but tenuous:
    - Gravity too low, or
    - EM protection weak, so solar wind strips gas efficiently.
This is your Mars‑like category.
5. **`AirlessRockyWorld`**
Essentially no atmosphere; surface directly exposed to space (Moon, many asteroids, small moons).
6. **`IcyDwarfWorld`**
Volatile‑rich surface, thin or seasonal atmosphere; low gravity and low temperature (Pluto, Eris).
7. **`HabitableWorld`**
Subset of `ThickAtmosphereWorld` / `StableCrustAndOceanWorld` where climate stays in a liquid‑water window and energy flux supports long‑term life.
8. **`RunawayGreenhouseWorld`**
Atmosphere and insolation push planet into high‑temperature, high‑pressure regime (classical Venus).
9. **`SnowballWorld`**
Global glaciation; surface water frozen; atmosphere may be thin or thick but climate is frozen.

### Environment transitions

- **MoltenSurfaceWorld ↔ ImpactReheatedWorld**
    - Strong bombardment can push a cooling world back toward molten conditions; quiet periods let it cool further.
- **ImpactReheatedWorld → ThickAtmosphereWorld**
    - As bombardment calms, crust stabilizes; atmosphere builds from outgassing and impact delivery.
- **ThickAtmosphereWorld ↔ ThinAtmosphereWorld / AirlessRockyWorld**
    - Controlled by:
        - Gravity (escape velocity).
        - Presence/strength of magnetosphere.
        - Stellar wind and radiation flux (XUV).
        - Volcanic and outgassing replenishment.
    - Mars transitions from “thicker atmosphere, possible surface water” to `ThinAtmosphereWorld` when EM shielding weakens and solar/stellar wind strips atmosphere over time.[^11_1]
- **ThinAtmosphereWorld → AirlessRockyWorld**
    - If loss processes (thermal escape, sputtering, solar wind) outpace replenishment and the body is small.
- **ThickAtmosphereWorld → RunawayGreenhouseWorld**
    - Strong greenhouse gases + high insolation; positive feedback makes temperature run away.
- **ThickAtmosphereWorld / ThinAtmosphereWorld → SnowballWorld**
    - Cooling plus albedo feedback (ice reflects more light) pushes the planet into a frozen state; could be temporary or permanent.
- **HabitableWorld ↔ RunawayGreenhouseWorld / SnowballWorld / ThinAtmosphereWorld**
    - Climate swings or atmosphere loss can move a planet out of the habitable state (Mars, early Venus).

Now Mars and Pluto have clear homes:

- Mars: `ThinAtmosphereWorld`, rocky composition, likely `FullPlanet` but with lower mass and no magnetosphere; historically may have traversed `ThickAtmosphereWorld` → `HabitableWorld` → `ThinAtmosphereWorld`.
- Pluto: `DwarfPlanet` + `IcyDwarfWorld` + `ThinAtmosphereWorld` (seasonal tenuous atmosphere).

***

## 4. Atmosphere + EM ladder: making solar wind matter

You already added EM fields and have a **stellar wind spec** that describes how stars shed gas parcels and how those parcels are reabsorbed. What’s missing is a **planetary atmosphere/EM state machine** that decides what solar wind does to each world.[^11_1]

### Atmosphere/EM states

For each rocky/icy body:

1. **`NoMagnetosphere`**
    - Little or no global EM field.
    - Atmosphere erosion dominated by stellar wind, sputtering, and thermal escape.
2. **`WeakMagnetosphere`**
    - Partial shielding; field exists but is small.
    - Atmosphere loss still significant.
3. **`StrongMagnetosphere`**
    - Good shielding; much of the solar/stellar wind is deflected.
4. **`AtmosphereRetained`**
    - Net balance: outgassing + replenishment ≥ loss; atmosphere persists.
5. **`AtmosphereStripping`**
    - Loss processes dominate; atmosphere column steadily declines.
6. **`AtmosphereCollapsed`**
    - Atmosphere too thin for surface liquid water; may exist as surface frost or trace gas.

### Atmosphere/EM transitions

- **Any EM state + AtmosphereRetained → AtmosphereStripping**
    - Condition: increase in stellar wind (star more active), decrease in EM field strength (core cooling, dynamo failure), or loss of greenhouse gases that maintain pressure.
- **AtmosphereStripping → AtmosphereCollapsed**
    - Condition: integrated column mass falls below threshold; no longer supports surface oceans/liquid water. This is essentially Mars’ trajectory.
- **EM state transitions**
    - `StrongMagnetosphere → WeakMagnetosphere → NoMagnetosphere` as planetary core cools or tectonics shut down; reversed only in special cases.

Coupling this to your **stellar wind** model:

- Stellar wind parcels (gas) already exist as emitted nebula parcels near the star.[^11_1]
- You need a **linker module** that:
    - Computes wind flux at each planet’s orbit.
    - Scales atmospheric loss rate by EM state, gravity, composition.
    - Updates atmosphere state (`Retained/Stripping/Collapsed`) each tick.

This is where your EM field and solar wind research plug in; the FSM gives them clear roles and endpoints.

***

## 5. Examples: Moon, Mars, Pluto in this FSM

### The Moon

- Solids ladder: `Moon` (SatelliteRock), bound to Earth.
- Atmosphere/EM: effectively `AirlessRockyWorld` and `NoMagnetosphere`; solar wind directly hits regolith.
- Environment: `StableCrust` but **not** `HabitableWorld` at any point.

The Moon’s presence shows up in **Earth’s environment**: stabilization of obliquity, tides. That’s another axis—“has large moon”—which influences climate and habitability transitions.

### Mars

- Solids ladder: `FullPlanet` (small rocky planet).
- Environment path:
    - Early: `ThickAtmosphereWorld` → potentially `HabitableWorld` for a while.
    - Later: EM field weakens, stellar wind erodes atmosphere ⇒ `ThinAtmosphereWorld`.
- Atmosphere/EM: transitions from `AtmosphereRetained + WeakMagnetosphere` to `AtmosphereStripping` → `AtmosphereCollapsed`.

So Mars is neither pure snowball nor runaway greenhouse; it’s a **post‑habitable, thin‑atmosphere** world with weak EM protection.

### Pluto

- Solids ladder: `DwarfPlanet` in `IcySmallBodyBelt`.
- Environment: `IcyDwarfWorld` with `ThinAtmosphereWorld` (seasonal atmosphere from sublimation).
- Atmosphere/EM: low gravity, likely `NoMagnetosphere`, strong atmosphere stripping but also strong seasonal replenishment.

Pluto never reaches `HabitableWorld` but still undergoes seasonal and secular transitions in surface and atmosphere states.

***

## 6. What’s missing in Truth and how this FSM helps

From your specs and code, Truth already has:

- A **matter‑state ladder** for gas → cores → star/giants/planetesimals.[^11_1]
- Disk regimes (`:core-accretion-zone`, `:fragmenting`, `:gravito-turbulent`) and a planet seeder.[^11_2][^11_1]
- Stellar wind and EM outlines (wind parcels, magnetosphere coupling).[^11_1]

What’s missing—and what this FSM sketches—is:

1. **Explicit small‑body classes**:
    - Asteroid belts, comet populations, moons, dwarf planets as first‑class states, not informal tags.
2. **Planet environment states past “molten vs habitable”**:
    - Thin‑atmosphere worlds (Mars), airless rocky worlds (Moon), icy dwarf worlds (Pluto) as distinct categories with their own transitions.
3. **Atmosphere + EM field state machine**:
    - Right now EM and wind are modeled for stars and broad plasma, but not tightly coupled to per‑planet atmosphere loss and protection states.
4. **Non‑overlapping transition conditions**:
    - Clearly separated gates so you never have, say, a world simultaneously treated as airless and thick‑atmosphere, or a body jumping from asteroid belt to habitable planet without passing through intermediate mass/environment states.

If we encode this FSM explicitly—e.g., as Clojure data describing states, transitions, and guards—it becomes the **control layer** above your physics modules:

- `domain/stellar` knows only about the gas ladder states and transitions.
- `domain/planet-formation` knows only about solids ladder states.
- `domain/atmosphere` and `domain/em` know about environment + atmosphere/EM states.
- `domain/ecology` and `biology` know about biosphere states.

We can make that concrete next: define a `law.state-machine` EDN/namespace holding these states and transitions, and then wire each `domain/*` system to run only when its entities/regions are in the appropriate FSM state, so the complexity stays compartmentalized even as we add more rich outcomes.

<div align="center">⁂</div>

[^11_1]: 02ca0590-0163-420d-a549-97702c7aae03.md

[^11_2]: https://www.aanda.org/articles/aa/full_html/2019/09/aa35278-19/aa35278-19.html


