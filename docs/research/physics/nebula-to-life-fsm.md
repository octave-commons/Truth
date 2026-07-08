# Nebula-to-Life Coupled FSM Architecture

**Domain:** physics | **Phase:** 0 → 1  
**Date:** 2026-07-07 | **Author:** `truth-research-physics`  
**Status:** draft  
**Primary sources:** McClure et al. (2025); IAU (2006); Stern & Levison (2002); Youdin & Goodman (2005); Johansen et al. (2007, 2014); Birnstiel et al. (2012); Chen & Kipping (2017); Seager et al. (2007); Sasselov (2008); Kopparapu et al. (2013); Owen & Wu (2017); Luger & Barnes (2015); Wordsworth (2012); Owen & Jackson (2012); Murray-Clay et al. (2009)

**Related notebooks:**
- `docs/research/cosmology/hops315-protostar-case-study.md` (empirical anchor)
- `docs/research/physics/protoplanetary-disks-planet-formation.md` (formation channels)
- `docs/research/physics/ecs-physics-substrate.md` (single-substrate constraint)
- `docs/notes/research/hops315-fsm/README.md` (source conversation summary)

---

## 1. Research Question

How do we represent the entire arc from a collapsing nebula to a potentially living world without letting the physics code collapse into a single tangled decision tree? The answer proposed here is a **stack of orthogonal finite-state machines**, each owned by exactly one domain system, with non-overlapping guards and explicit projection to the Phase 0 → Phase 1 `:planet-candidate` handoff record.

This notebook provides:

1. Five canonical state catalogs (Matter, Role, Environment, Atmosphere/EM, Biosphere).
2. Guard-precedence rules that remove ambiguity when multiple states could apply.
3. System ownership for every state component and projection function.
4. A transition-modifier catalog for contextual factors (moons, Jupiter-like scatterers, bombardment eras, etc.).
5. Clojure pseudocode mapping the FSM stack to the existing `:planet-candidate` handoff contract.

---

## 2. Empirical and Taxonomic Anchors

### 2.1 HOPS-315: the “time zero” of rocky planet formation

McClure et al. (2025) used JWST + ALMA to observe the embedded protoplanetary disk around HOPS-315, a ~0.1–0.2 Myr old protostar at ~1.4 kpc. They detected warm SiO gas at ~200 °C and crystalline forsterite condensates within ~2.2 AU, with the highest-temperature CAI-like conditions concentrated within ~1 AU[^1]. This is an empirical prototype for the first transition in our solids ladder: hot gas → refractory solids → dust/pebble fields → planetesimals. Because individual grains cannot be resolved, the simulation must treat them as a **sub-grid dust/grain-size field** carried by SPH parcels and promote only representative “super-particles” when thresholds are crossed[^2].

### 2.2 Planetary taxonomy: planethood vs. dynamical role

The IAU 2006 definition distinguishes three physical classes for solar-system bodies: (1) planets, which are round and have cleared their orbital neighborhoods; (2) dwarf planets, which are round but have not cleared; and (3) small solar-system bodies[^3]. Stern & Levison (2002) proposed a complementary, physically based “sieve” that separates planetary bodies from planetesimals by hydrostatic-equilibrium timescales, and then separates **überplanets** (orbit-clearing) from **unterplanets** (non-clearing) by dynamical dominance[^4]. The FSM stack below adopts this separation: **Matter FSM** answers “what is it physically?” and **Role FSM** answers “what is its dynamical relationship?” Pluto, Ceres, the Moon, and asteroids therefore each get a clear, non-overlapping home.

### 2.3 Exoplanet characterization: mass-radius, thermal bands, and atmospheric escape

Exoplanet demographics show that mass and radius are sufficient to separate four broad classes: Terran worlds, Neptunian worlds, Jovian worlds, and stars (Chen & Kipping 2017)[^5]. Seager et al. (2007) give the canonical mass-radius relations for solid planets made of iron, silicate, water, and carbon[^6], while Sasselov (2008) emphasizes that “super-Earths” split into rocky and ocean-planet varieties[^7]. These classes map directly to the Matter FSM’s `:matter/planet`, `:matter/gas-giant`, `:matter/ice-giant`, and `:matter/dwarf-planet` states.

Kopparapu et al. (2013) define the habitable zone by incident stellar flux rather than a fixed orbital distance, giving the thermal-band guard for `:env/temperate-habitable`[^8].

Atmospheric escape is not a single process. Murray-Clay et al. (2009) and Owen & Jackson (2012) showed that XUV-driven escape can be **energy-limited**, **recombination-limited**, or **photon-limited**, with transitions that depend on planet mass, radius, and incident flux[^9]. Owen & Wu (2017) used these regimes to explain the Kepler “evaporation valley” and the bimodal radius distribution of close-in planets[^10]. Luger & Barnes (2015) and Wordsworth (2012) further link escape, runaway greenhouse, and abiotic oxygen buildup to the long-term habitability of M-dwarf planets[^11]. These literatures justify the rich Atmosphere/EM FSM rather than a single “stripping” flag.

---

## 3. Design Principle: a Stack of Coupled FSMs

A single monolithic state machine would force Mars to be a “failed planet,” the Moon to be a “small planet,” and Pluto to be either a planet or not. Instead, the entity is described by a **bundle of orthogonal states**, one per FSM:

| FSM | Owns the question | Example states for Mars |
| --- | --- | --- |
| **Matter** | What physically is this thing? | `:matter/planet` |
| **Role** | What is its dynamical relationship? | `:role/orbit-clearer` |
| **Environment** | What regime is its surface/interior in? | `:env/arid-thin-atmosphere` |
| **Atmosphere / EM** | Can it hold and protect gas? | `:atm/collapsing`, `:mag/collapsed` |
| **Biosphere** | What level of life exists? | `:bio/none` or `:bio/extinct` |

Each FSM has:

- one active state per entity at a time;
- one owning system that writes that component;
- explicit entry/exit guards;
- a strict precedence order so that multiple true conditions do not cause oscillation;
- a projection layer that compresses the internal state into the handoff contract.

---

## 4. State Catalogs

### 4.1 Matter FSM

**Owner:** `domain.stellar` for gas/stellar-collapse states; `domain.planet-formation` for solids and giant/ice-giant states.  
**Answers:** What physically is this entity?

| State | Required observables | Entry guard | Exit guard | Allowed successors | Notes / examples |
| --- | --- | --- | --- | --- | --- |
| `:matter/nebula` | density, temperature, pressure support, velocity dispersion | Default diffuse-gas state | Jeans-unstable and locally collapse-eligible | `:matter/collapsing-gas` | Canonical Phase 0 start state |
| `:matter/collapsing-gas` | same as above + collapse timescale, local overdensity | Jeans-unstable and converging | Core-condensation threshold crossed or collapse disrupted | `:matter/condensed-core`, `:matter/nebula` | Separates “about to collapse” from “already a core” |
| `:matter/condensed-core` | boundness, density above core threshold, mass | Gas forms a bound core | Mass tier and thermal structure decide fate | `:matter/protostar`, `:matter/brown-dwarf`, `:matter/gas-giant-embryo`, `:matter/planetesimal-seed-source` | Branch point where mass sets fate |
| `:matter/gas-giant-embryo` | total mass, opacity-limit envelope, disk gas supply | Mass above opacity limit but below D-burning limit, with bound gas envelope | Accretes or loses mass, merges, or is stripped | `:matter/gas-giant`, `:matter/brown-dwarf`, `:matter/planet`, stripped variants | Precursor to gas giant before runaway or stripping |
| `:matter/planetesimal-seed-source` | local dust-to-gas ratio, Stokes number, density peak | Condensed core or parcel below opacity limit but with enough solids to spawn seeds | Solids convert to discrete planetesimals or are dispersed | `:matter/planetesimal`, `:matter/dust-field`, `:matter/pebble-field` | Truth seed-and-grow source state; parent parcel stays gaseous |
| `:matter/protostar` | mass, core temperature, pressure, H fraction, accretion rate | Mass above H-burning threshold but fusion not yet sustained | Fusion ignites or contraction stalls / mass stripped | `:matter/star`, `:matter/brown-dwarf`, `:matter/stellar-remnant` | Pre-main-sequence core |
| `:matter/star` | fusion state, composition, luminosity, mass loss | Fusion possible and self-sustaining | Fusion ceases (hysteresis avoids flicker) | `:matter/stellar-remnant` | Terminal up-ladder state |
| `:matter/stellar-remnant` | residual mass, cooling luminosity, compact radius | Formerly fused body no longer sustaining fusion | Cooling, mergers, or total ablation | terminal or merger products | Collapsed bodies do not return to nebula |
| `:matter/dust-field` | dust fraction, condensation chemistry, grain-size distribution | Disk solids present but sub-resolved as fine grains | Grains grow to pebbles or vaporize back to gas | `:matter/pebble-field`, chemistry back to gas | Needed before planetesimals can form |
| `:matter/pebble-field` | Stokes-number proxy, dust-to-gas ratio, midplane enhancement | Solids large enough to drift and concentrate aerodynamically | Streaming/clumping produces bound bodies or pebbles are lost/accreted | `:matter/planetesimal`, `:matter/dust-field` | Home of streaming-instability preconditions[^2][^12] |
| `:matter/planetesimal` | size, mass, composition, internal strength, collision history | Solids become bound small bodies | Classification shifts to asteroid/comet/protoplanet, or disruption | `:matter/asteroid`, `:matter/comet`, `:matter/protoplanet`, `:matter/debris-cloud` | Truth’s seed-and-grow condensation produces this tier |
| `:matter/asteroid` | rocky composition, non-rounded shape, low volatile fraction | Planetesimal remains a small rocky body without hydrostatic roundness | Merger, catastrophic breakup, or growth | `:matter/protoplanet`, `:matter/debris-cloud` | Main-belt objects |
| `:matter/comet` | volatile fraction, thermal activity, sublimation behavior | Small body is volatile-rich and outgasses under heating | Volatile exhaustion, disruption, or accretion | `:matter/asteroid`, `:matter/debris-cloud`, accreted into larger body | Jupiter-family and long-period comets |
| `:matter/protoplanet` | mass, roundness proxy, differentiation, accretion rate | Growing solid body dominates local collisions but is not yet final | Orbit clearing, satellite capture, volatile/gas accretion | `:matter/dwarf-planet`, `:matter/planet`, `:matter/gas-giant`, `:matter/ice-giant` | Moon-forming impacts, Mars-like stalled embryos |
| `:matter/dwarf-planet` | self-rounding, composition, local dynamical dominance | Round/self-gravitating but has not cleared its neighborhood | Merger, capture, rare later clearing | `:matter/planet`, `:matter/debris-cloud` | Pluto, Ceres, Eris |
| `:matter/planet` | mass, roundness, neighborhood-clearing proxy | Round and dynamically dominant in its orbital neighborhood | Catastrophic disruption, engulfment, or stellar evolution | `:matter/debris-cloud` or swallowed by star | Earth and Mars are both `:matter/planet` |
| `:matter/gas-giant` | total mass, envelope fraction, disk gas supply | Core or clump acquires a dominant H/He envelope | Severe stripping, merger, or stellar evolution | `:matter/ice-giant`, stripped variants | Core accretion and GI channels both land here |
| `:matter/ice-giant` | volatile-rich interior, small H/He envelope | Giant planet dominated by ices/volatiles | Stripping/merger only | stripped variants | Uranus/Neptune-like |
| `:matter/debris-cloud` | fragment count, unbound/bound fraction, collision energy | Disruption or intense grinding collisions | Reaccretion, ring formation, or clearing | `:matter/ring-particle`, `:matter/planetesimal`, removal | Post-impact disks, Moon formation |
| `:matter/ring-particle` | Roche-limit context, particle population, host gravity | Debris persists inside reaccretion-suppressed ring conditions | Rings spread, accrete into moons, or decay | `:matter/debris-cloud`, moon-seed populations | Saturn-like rings |

**Key physical boundaries:**

- **Opacity limit:** ~3–10 M_J is the minimum mass for direct gas fragmentation[^13].
- **Deuterium-burning limit:** ~13 M_J[^14].
- **Hydrogen-burning limit:** ~0.075–0.08 M_☉[^14].
- **Planetesimal mass:** streaming instability produces 10^18–10^21 kg bodies[^2][^12].
- **Truth seed mass:** `condensation-seed-mass-kg = 1.0e16 kg` (~100 km rocky body) is the current super-particle mass[^15].

### 4.2 Role FSM

**Owner:** `domain.orbital` (with `domain.planet-formation` during disk-era transitions).  
**Answers:** What is this entity’s dynamical relationship in the system?

| State | Required observables | Entry guard | Exit guard | Allowed successors | Notes |
| --- | --- | --- | --- | --- | --- |
| `:role/free-body` | orbit elements, binding energy, nearby dominant masses | Default for non-gas resolved body | Becomes disk-embedded, belt-bound, satellite-bound, or orbit-clearing | `:role/disk-embedded`, `:role/belt-member`, `:role/satellite`, `:role/orbit-clearer`, `:role/scattered-body` | Embryos and isolated bodies |
| `:role/disk-embedded` | gas density around orbit, relative drift, disk membership | Body dynamically coupled to gas disk | Gas disperses or body decouples | `:role/free-body`, `:role/orbit-clearer`, `:role/satellite` | Matches resolved-disk / sub-grid architecture |
| `:role/belt-member` | semimajor-axis clustering, non-cleared neighborhood, many-body population | Body persists as part of a shared orbital population | Scattered, accreted, captured, or promoted to orbit-clearer | `:role/scattered-body`, `:role/satellite`, `:role/orbit-clearer`, removal | Asteroid/Kuiper-belt populations |
| `:role/resonant-member` | mean-motion resonance ratio, libration proxy | Stably trapped in resonance with a stronger perturber | Resonance breaks | `:role/belt-member`, `:role/scattered-body`, `:role/orbit-clearer` | Pluto-like outcomes |
| `:role/scattered-body` | high e/i, repeated close encounters, weak local stability | Ejected from a quiet population by giant perturbations | Recapture, ejection, or damping into a stable population | `:role/interstellar-escape`, `:role/belt-member`, `:role/satellite` | Jupiter-like architecture matters here |
| `:role/orbit-clearer` | Hill-sphere dominance, local mass ratio, long-term stability | Body dominates its orbital neighborhood | Later destabilization, engulfment, or extreme system evolution | usually terminal | Dynamical definition of a planet |
| `:role/satellite` | bound orbit around non-stellar primary, stable Hill capture zone | Dominant gravitational relationship is to a planet/dwarf planet | Escape, collision, tidal disruption, or reclassification to rings/debris | `:role/ring-member`, `:role/free-body` | Moon is a role, not a matter state |
| `:role/ring-member` | Roche-regime host relation, ring-plane membership, non-accreting orbit | Fragment population inside a reaccretion-suppressed ring zone | Spreading, reaccretion, or clearing | `:role/satellite`, `:role/debris-associated` | Saturn-like rings |
| `:role/co-orbital` | shared semimajor axis, Trojan/horseshoe stability | Persistent co-orbital configuration | Instability breaks shared-orbit behavior | `:role/free-body`, `:role/scattered-body` | Optional richer architecture |
| `:role/interstellar-escape` | total orbital energy > 0 relative to system barycenter | No longer bound to the system | Rare recapture | terminal | Makes ejection explicit |

### 4.3 Environment FSM

**Owner:** `domain.environment`.  
**Answers:** What regime is the body’s surface/interior in right now? A body can remain `:matter/planet` while moving through several environment states over time.

| State | Required observables | Entry guard | Exit guard | Allowed successors | Notes |
| --- | --- | --- | --- | --- | --- |
| `:env/magma-ocean` | melt fraction, surface T, impact energy flux, radiative cooling | Global or near-global melt dominates | Sustained crust can form between major resets | `:env/impact-reset`, `:env/crusted-volcanic` | Early rocky worlds |
| `:env/impact-reset` | bombardment energy rate, resurfacing fraction, crust persistence | Large impacts repeatedly remelt/sterilize surface | Impact cadence drops below reset threshold | `:env/crusted-volcanic`, back to `:env/magma-ocean` | “Habitable, then partially molten again” loop |
| `:env/crusted-volcanic` | crust fraction, volcanism, outgassing, internal heat flux | Stable crust exists but internal heat strongly shapes world | Climate/hydrosphere stabilizes, air lost, or tidal/impact forcing dominates | `:env/ocean-world`, `:env/arid-thin-atmosphere`, `:env/airless-inert`, `:env/tidally-heated` | Early Venus, Io, young terrestrials |
| `:env/ocean-world` | stable liquid inventory, pressure, temperature, salinity/chemistry | Surface liquid persistent on geologic timescales | Freeze-out, desiccation, runaway heating, or atmosphere collapse | `:env/temperate-habitable`, `:env/snowball`, `:env/runaway-greenhouse`, `:env/post-habitable` | Broader than “habitable” |
| `:env/temperate-habitable` | persistent liquid solvent, stable climate window, tolerable radiation, long-lived atmosphere | Climate and solvent stability remain in life-friendly band | Heat, cold, impacts, or atmosphere loss push it out | `:env/post-habitable`, `:env/snowball`, `:env/runaway-greenhouse`, `:env/impact-reset` | Habitability is a regime, not a badge |
| `:env/post-habitable` | evidence of prior habitable regime + present failure mode | Once-habitable world leaves the life-friendly window for secular reasons | Conditions recover for long enough | `:env/arid-thin-atmosphere`, `:env/snowball`, `:env/temperate-habitable` | Mars-like “likely habitable once” |
| `:env/arid-thin-atmosphere` | low surface pressure, cold/dry climate, weak volatile cycling | Rocky world retains only tenuous atmosphere and little surface liquid | Renewed atmosphere/ocean build-up or full collapse | `:env/airless-inert`, `:env/post-habitable`, `:env/temperate-habitable` | Mars today |
| `:env/airless-inert` | negligible atmosphere, low active resurfacing, exposed surface | Atmosphere effectively absent and surface evolution is slow/inertial | Major resurfacing, capture of dense atmosphere, or extreme heating | `:env/magma-ocean`, `:env/crusted-volcanic` | Modern Moon, many asteroids |
| `:env/snowball` | global ice cover, high albedo feedback, low liquid-surface fraction | Freezing feedback pushes surface liquid out globally | Sufficient warming or interior/ocean retention under ice | `:env/ocean-world`, `:env/subsurface-ocean`, `:env/post-habitable` | Distinct from icy dwarf worlds |
| `:env/runaway-greenhouse` | radiative imbalance, volatile greenhouse loading, water-loss trajectory | Greenhouse forcing drives irreversible extreme heating | Dramatic atmospheric loss or stellar evolution changes | `:env/post-habitable` | Venus-like |
| `:env/icy-volatile-world` | low temperatures, surface volatile ices, sublimation/condensation cycling | Small/distant world dominated by frozen volatile behavior | Major warming, tidal heating, or interior activation | `:env/cryovolcanic`, `:env/subsurface-ocean` | Pluto |
| `:env/subsurface-ocean` | internal heat, ice shell, liquid layer below surface | Surface stays frozen but liquid persists below | Freeze-through or surfacing through cryovolcanism/tidal disruption | `:env/cryovolcanic`, `:env/icy-volatile-world` | Europa-like |
| `:env/tidally-heated` | orbital resonance forcing, dissipation, heat flux | Tidal dissipation is the dominant environmental power source | Resonance/heating weakens | `:env/crusted-volcanic`, `:env/subsurface-ocean` | Io-like |
| `:env/cryovolcanic` | subsurface volatile reservoirs, fracture transport, episodic venting | Icy interior activity expressed at surface | Interior freezes or heating fades | `:env/icy-volatile-world`, `:env/subsurface-ocean` | Enceladus/Pluto-like |

### 4.4 Atmosphere / EM FSM

**Owner:** `domain.em` / `domain.interior` for magnetosphere states; `domain.atmosphere` for atmosphere states.  
**Answers:** What is happening to the atmosphere, and how shielded is it?

#### Magnetosphere sub-machine

| State | Required observables | Entry guard | Exit guard | Allowed successors |
| --- | --- | --- | --- | --- |
| `:mag/no-dynamo` | rotation rate, convective core proxy, conductivity, heat flux | No sustained core dynamo | Convective + rotational conditions recover | `:mag/episodic-dynamo`, `:mag/stable-dynamo` |
| `:mag/episodic-dynamo` | same + time variability | Dynamo action intermittent or weakly sustained | Stabilization or collapse | `:mag/stable-dynamo`, `:mag/no-dynamo` |
| `:mag/stable-dynamo` | convective power above threshold, adequate rotation, dipole estimate | Robust global magnetic field maintained | Core cooling/rotation shuts it down, or external compression dominates | `:mag/compressed`, `:mag/episodic-dynamo`, `:mag/no-dynamo` |
| `:mag/compressed` | stellar-wind pressure, magnetopause stand-off estimate | Magnetosphere exists but is strongly compressed by wind/XUV | External pressure drops or field weakens to collapse | `:mag/stable-dynamo`, `:mag/collapsed` |
| `:mag/collapsed` | magnetic pressure < wind pressure at effective shielding boundary | Field no longer meaningfully shields atmosphere | Dynamo strengthens or stellar forcing weakens enough | `:mag/compressed`, `:mag/no-dynamo` |

#### Atmosphere sub-machine

| State | Required observables | Entry guard | Exit guard | Allowed successors |
| --- | --- | --- | --- | --- |
| `:atm/none` | escape velocity, temperature, volatile inventory | No atmosphere retained at useful scale | Major outgassing, volatile delivery, or cooling creates atmosphere | `:atm/transient-outgassed`, `:atm/stable-secondary` |
| `:atm/transient-outgassed` | outgassing rate, volatile release, escape rate | Atmosphere exists but replenishment is episodic and retention weak | Stabilizes or is lost | `:atm/stable-secondary`, `:atm/collapsing`, `:atm/none` |
| `:atm/stable-secondary` | retained species, v_esc/v_thermal, replenishment vs loss | Atmosphere retention plausible over long intervals | Strong stripping, freeze-out, runaway greenhouse, or collapse | `:atm/substantial`, `:atm/actively-stripped`, `:atm/frozen`, `:atm/collapsing` |
| `:atm/substantial` | surface pressure proxy, retained heavy species, volatile budget | Persistent, climatically important atmosphere | Collapse, stripping, or runaway growth | `:atm/thick`, `:atm/actively-stripped`, `:atm/frozen`, `:atm/collapsing` |
| `:atm/thick` | high column mass, strong greenhouse or dense volatile loading | Atmosphere thick enough to dominate surface conditions | Loss or thermal transition | `:atm/runaway-associated`, `:atm/actively-stripped`, `:atm/collapsing` |
| `:atm/frozen` | condensation temperatures, surface pressure, volatile phase stability | Atmospheric volatiles collapsed to surface/ice seasonally or persistently | Warming or resurfacing release | `:atm/transient-outgassed`, `:atm/stable-secondary` |
| `:atm/collapsing` | net loss > replenishment for secular timescales | Atmosphere persists but is on a downward trajectory | Recovery or full stripping | `:atm/actively-stripped`, `:atm/none`, `:atm/stable-secondary` |
| `:atm/actively-stripped` | wind flux, XUV flux, shielding state, escape rate | External forcing dominates and loss is rapid | Forcing weakens or inventory exhausted | `:atm/xuv-energy-limited`, `:atm/xuv-recombination-limited`, `:atm/xuv-photon-limited`, `:atm/none` |
| `:atm/xuv-energy-limited` | XUV flux, heating efficiency, R_XUV, gravity | Absorbed XUV power mostly drives escape work | Cooling/recombination or photon supply takes over | `:atm/xuv-recombination-limited`, `:atm/xuv-photon-limited`, `:atm/blowoff`, `:atm/collapsing` |
| `:atm/xuv-recombination-limited` | recombination vs flow timescales, electron density, XUV flux | Radiative/recombination losses dominate | Forcing weakens or transitions to another regime | `:atm/xuv-energy-limited`, `:atm/blowoff`, `:atm/collapsing` |
| `:atm/xuv-photon-limited` | ionizing photon budget, low-gravity response | Escape limited mainly by available ionizing photons | Flux/gravity conditions move it elsewhere | `:atm/xuv-energy-limited`, `:atm/collapsing`, `:atm/none` |
| `:atm/blowoff` | Roche geometry, inflated R_XUV, tidal escape enhancement | Hydrodynamic blow-off or Roche-assisted overflow | Enough atmosphere lost to leave blow-off regime | `:atm/collapsing`, `:atm/none` |

### 4.5 Biosphere FSM

**Owner:** `domain.biology` / `domain.ecology`.  
**Answers:** What level of organized life or pre-life exists on this world?  
**Runs only when** Environment and Atmosphere/EM states provide a valid substrate.

| State | Required observables | Entry guard | Exit guard | Allowed successors | Notes |
| --- | --- | --- | --- | --- | --- |
| `:bio/none` | solvent availability, energy gradients, chemistry inventory, sterilization rate | Default | Chemistry becomes sustained rather than transient | `:bio/prebiotic` | Default for most bodies |
| `:bio/prebiotic` | persistent solvent niche, organic inventory, redox/UV/geothermal energy, environmental continuity | Chemistry rich enough for self-organizing pre-life | Replication/evolution emerges, or environment collapses | `:bio/microbial`, `:bio/none` | Life-adjacent chemistry |
| `:bio/microbial` | replicator persistence, metabolic closure, nutrient cycling, habitat continuity | Self-replicating, evolving life exists but remains simple | Extinction, or ecological complexity for multicellularity | `:bio/complex`, `:bio/extinct` | Likely dominant living state |
| `:bio/complex` | oxygenation or alternative energetic surplus, ecological specialization, habitat diversity | Large-scale differentiated ecosystems | Collapse/extinction, or technological civilization | `:bio/technological`, `:bio/extinct` | Not inevitable from microbial |
| `:bio/technological` | intelligence proxy, tool-use/engineering, sustained surplus energy, societal persistence | Technological civilization develops | Collapse, extinction, or transformation beyond biology | `:bio/post-biological`, `:bio/extinct` | Where ACTORS/culture attach |
| `:bio/extinct` | evidence of prior life + current absence of active biosphere | Life once existed but no longer persists | Re-emergence from surviving chemistry or reseeding | `:bio/prebiotic`, rare `:bio/microbial` | Mars-like histories |
| `:bio/post-biological` | non-biological agent persistence, engineered substrate, decoupling from ecosystem | Intelligence persists in non-biological forms | Terminal or transformed branches | terminal / transformed branches | Optional long-term branch |

---

## 5. Guard Precedence

When several guards are simultaneously true, the FSM must resolve to a single state. Each machine uses a strict evaluation order; across machines, the hierarchy is **Matter → Role → Environment → Atmosphere/EM → Biosphere**.

### 5.1 Cross-machine precedence

1. **Matter FSM** is evaluated first. If the entity is `:matter/nebula`, no other machine applies.
2. **Role FSM** is evaluated second. A body that is `:role/interstellar-escape` is not a viable planet candidate regardless of its environment.
3. **Environment FSM** is evaluated third. It decides surface/interior regime from raw thermal and impact observables.
4. **Atmosphere/EM FSM** is evaluated fourth. It can read environment state and stellar wind/XUV flux, but it does not override it.
5. **Biosphere FSM** is evaluated last. It only runs when the upstream machines provide an eligible substrate (e.g., not `:matter/nebula`, not `:env/airless-inert`, not `:atm/none` unless subsurface refugia apply).

### 5.2 Matter precedence

Within the gas ladder, use the mass tier in this order:

1. If fusion-sustaining → `:matter/star` (with hysteresis).
2. Else if mass ≥ H-burning limit → `:matter/protostar`.
3. Else if mass ≥ D-burning limit → `:matter/brown-dwarf`.
4. Else if mass ≥ opacity-limit/giant-embryo threshold → `:matter/gas-giant-embryo` / `:matter/gas-giant`.
5. Else if bound condensed core → `:matter/planetesimal-seed-source` or `:matter/planetesimal`.
6. Else if Jeans-unstable → `:matter/collapsing-gas`.
7. Else `:matter/nebula`.

Within the solids ladder, evaluate in order of increasing physical maturity and dynamical dominance:

1. If disrupted/inside Roche limit → `:matter/debris-cloud` / `:matter/ring-particle`.
2. Else if volatile-rich and thermally active → `:matter/comet`.
3. Else if rocky and non-rounded → `:matter/asteroid`.
4. Else if round but not cleared → `:matter/dwarf-planet`.
5. Else if round and cleared → `:matter/planet`.
6. Else if captured around a non-stellar primary → role is `:role/satellite`, matter remains rocky/icy/planetary body.

### 5.3 Environment precedence

Evaluate in order of which physical process is most likely to reset the others:

1. If melt fraction above threshold → `:env/magma-ocean`.
2. Else if bombardment reset flux above threshold → `:env/impact-reset`.
3. Else if no persistent atmosphere and no volatiles → `:env/airless-inert`.
4. Else if ice globally stable and surface liquid unstable → `:env/snowball` or `:env/icy-volatile-world`.
5. Else if runaway radiative forcing exceeded → `:env/runaway-greenhouse`.
6. Else if liquid solvent stable and pressure/temperature window holds → `:env/temperate-habitable`.
7. Else if liquid solvent once existed but atmosphere collapsed → `:env/post-habitable` or `:env/arid-thin-atmosphere`.
8. Else use tectonic/cryovolcanic/tidal branches (`:env/crusted-volcanic`, `:env/tidally-heated`, `:env/cryovolcanic`).

### 5.4 Atmosphere precedence

Evaluate from most catastrophic/rapid to most stable:

1. If no atmosphere can be retained → `:atm/none`.
2. Else if Roche-assisted blow-off active → `:atm/blowoff`.
3. Else if XUV escape regime criteria met → `:atm/xuv-energy-limited` / `:atm/xuv-recombination-limited` / `:atm/xuv-photon-limited`.
4. Else if net loss dominates and rapid → `:atm/actively-stripped`.
5. Else if net loss > replenishment over long timescales → `:atm/collapsing`.
6. Else if volatiles frozen out → `:atm/frozen`.
7. Else if thick/dense atmosphere → `:atm/thick`.
8. Else if substantial retained atmosphere → `:atm/substantial`.
9. Else if stable secondary atmosphere → `:atm/stable-secondary`.
10. Else → `:atm/transient-outgassed`.

### 5.5 Magnetosphere precedence

Evaluate shielding effectiveness rather than dynamo existence alone:

1. If no convective dynamo → `:mag/no-dynamo`.
2. Else if dynamo intermittent → `:mag/episodic-dynamo`.
3. Else if magnetic pressure < wind pressure at shielding boundary → `:mag/collapsed`.
4. Else if strongly compressed but still present → `:mag/compressed`.
5. Else → `:mag/stable-dynamo`.

---

## 6. Ownership and Projection to the Handoff Record

The internal FSM stack is rich, but the Phase 0 → Phase 1 handoff must be compact. Each summary component is owned by a single projector that reads the relevant FSM state plus raw observables.

| Component | Meaning | Owner | Feeds handoff fields |
| --- | --- | --- | --- |
| `c/matter-state` | Deep physical identity | `domain.stellar`, `domain.planet-formation` | `:material-class`, candidate eligibility |
| `c/role-state` | Dynamical relationship | `domain.orbital` | `:orbit-stable?`, candidate eligibility |
| `c/environment-state` | Surface/interior regime | `domain.environment` | Informs atmosphere/thermal interpretation (not exported directly) |
| `c/atmosphere-state` | Atmosphere retention/escape regime | `domain.atmosphere` | `:atmosphere-class`, `:retained-species` |
| `c/magnetosphere-state` | Dynamo/shielding regime | `domain.em` / `domain.interior` | `:core-dynamo?`, `:magnetic-field` |
| `c/biosphere-state` | Life level | `domain.biology` | Not part of Phase 0 handoff |
| `c/state-modifiers` | Contextual transition biases | one aggregator system (e.g., `domain.orbital` + `domain.environment`) | `:formation-events` |

Derived summary components (public contract):

| Summary component | Owner | Projection rule |
| --- | --- | --- |
| `c/material-class` | `domain.stellar` / `domain.planet-formation` | Rocky/icy/gaseous/mixed from matter-state + composition + envelope fraction |
| `c/thermal-band` | `domain.orbital` | frozen/cold/temperate/warm/hot from stellar luminosity + semimajor axis |
| `c/orbit-stable?` | `domain.orbital` | analytic proxy: bound, not plunging, no close Hill conflicts |
| `c/atmosphere-class` | `domain.atmosphere` | none/thin/substantial/thick compression of atmosphere-state |
| `c/retained-species` | `domain.atmosphere` | volatile inventory filtered by escape velocity + thermal band + freeze-out |
| `c/core-dynamo?` | `domain.em` / `domain.interior` | true when magnetosphere-state is stable/compressed and convective conditions hold |
| `c/magnetic-field` | `domain.em` | surface dipole estimate from magnetosphere-state + rotation + core radius |
| `c/formation-events` | `domain.genesis` ledger | causal history of major transitions and modifiers |

The handoff spec already defines the target `:planet-candidate` record; the FSM stack is designed to **produce** it, not replace it.

---

## 7. Transition Modifier Catalog

Modifiers are **not states**. They are contextual flags that bias transition rates or thresholds without changing what an object is.

| Modifier | Owner | Applies to | Effect on transitions | Notes |
| --- | --- | --- | --- | --- |
| `:mod/has-large-moon` | `domain.orbital` | primary rocky planet | Biases climate/obliquity stability; increases tides; may reduce chaotic seasonal forcing | Earth–Moon system |
| `:mod/giant-planet-shielding` | `domain.orbital` | inner rocky worlds | Reduces some impactor delivery pathways probabilistically | Jupiter-like shielding |
| `:mod/giant-planet-scattering` | `domain.orbital` | belts, comets, inner worlds | Increases `belt-member → scattered-body`, raises bombardment for inner planets | Jupiter also scatters |
| `:mod/heavy-bombardment-era` | `domain.planet-formation` / `domain.environment` | young planets, moons | Raises `crusted-volcanic → impact-reset`, `temperate-habitable → impact-reset`, `prebiotic → none` | Late Heavy Bombardment |
| `:mod/strong-tidal-heating` | `domain.orbital` / `domain.environment` | satellites, close resonant worlds | Biases toward `tidally-heated`, `subsurface-ocean`, `cryovolcanic` | Europa, Io, Enceladus |
| `:mod/stellar-youth-xuv` | `domain.stellar` / `domain.atmosphere` | early atmospheres | Pushes `stable-secondary → actively-stripped`, favors XUV escape regimes | Young active stars |
| `:mod/volatile-rich-feedstock` | `domain.planet-formation` | worlds beyond/near snow lines | Increases probability of `ocean-world`, `icy-volatile-world`, thicker atmospheres | Snow-line chemistry |
| `:mod/metal-rich-feedstock` | `domain.planet-formation` | solid-body growth | Biases solids toward rocky/differentiated outcomes, faster core formation | Enrichment/seeding specs |
| `:mod/migration-history` | `domain.orbital` / `domain.planet-formation` | whole system architecture | Alters bombardment, resonance trapping, volatile delivery, final role assignments | System-scale modifier |
| `:mod/obliquity-chaos` | `domain.orbital` / `domain.environment` | terrestrial climates | Increases volatility between habitable, snowball, and arid states | “No Moon” hypothesis |
| `:mod/interior-dynamo-decline` | `domain.interior` / `domain.em` | rocky planets | Biases `stable-dynamo → episodic/no-dynamo`, amplifying atmospheric loss | Mars hook |
| `:mod/late-volatile-delivery` | `domain.orbital` / `domain.environment` | dry rocky worlds | Can reopen `airless-inert`/`arid-thin-atmosphere` toward transient or ocean states | Impact-delivered oceans |
| `:mod/sterilizing-impacts` | `domain.environment` / `domain.biology` | biospheres | Pushes `prebiotic → none`, `microbial → extinct`, resets complex biospheres | Biospheres care about kill thresholds |
| `:mod/subsurface-refugia` | `domain.environment` / `domain.biology` | icy worlds, harsh worlds | Allows life persistence despite hostile surface; weakens extinction transitions | Mars/Europa possibility |

---

## 8. Canonical Examples

The FSM stack handles the Solar System’s awkward middle cases without special-casing:

| Body | Matter | Role | Environment | Atmosphere/EM | Biosphere | Modifiers |
| --- | --- | --- | --- | --- | --- | --- |
| **Earth** | `:matter/planet` | `:role/orbit-clearer` | `:env/temperate-habitable` | `:mag/stable-dynamo`, `:atm/stable-secondary` | `:bio/technological` | `:mod/has-large-moon` |
| **Mars** | `:matter/planet` | `:role/orbit-clearer` | `:env/arid-thin-atmosphere` (previously `post-habitable`/`ocean-world`) | `:mag/collapsed`, `:atm/collapsing` | `:bio/none` or `:bio/extinct` | `:mod/interior-dynamo-decline`, `:mod/late-volatile-delivery` (past) |
| **Moon** | `:matter/rocky-body` | `:role/satellite` | `:env/airless-inert` | `:mag/no-dynamo`, `:atm/none` | `:bio/none` | — |
| **Pluto** | `:matter/dwarf-planet` | `:role/resonant-member` / `:role/belt-member` | `:env/icy-volatile-world` | `:mag/no-dynamo`, `:atm/frozen` | `:bio/none` | — |
| **Europa** | `:matter/rocky-body` / `:matter/ice-body` | `:role/satellite` | `:env/subsurface-ocean` | `:mag/induced-weak`, `:atm/none` | `:bio/prebiotic` or `:bio/microbial` | `:mod/strong-tidal-heating` |
| **Venus** | `:matter/planet` | `:role/orbit-clearer` | `:env/runaway-greenhouse` | `:mag/no-dynamo`, `:atm/thick` | `:bio/none` | — |
| **Io** | `:matter/rocky-body` | `:role/satellite` | `:env/tidally-heated` / `:env/crusted-volcanic` | `:mag/no-dynamo`, `:atm/transient-outgassed` | `:bio/none` | `:mod/strong-tidal-heating` |

---

## 9. Promotion Paths

The full nebula-to-life arc can be read as a sequence of allowed transitions across the FSM stack.

### 9.1 Gas ladder → star + disk

```
:matter/nebula
  → :matter/collapsing-gas   (Jeans instability)
  → :matter/condensed-core   (core-condensation density)
  → :matter/protostar        (mass ≥ H-burning limit)
  → :matter/star             (fusion self-sustaining)
```

Substellar branches: condensed core can also become `:matter/brown-dwarf`, `:matter/gas-giant-embryo`, or remain in the solids ladder as a planetesimal seed source.

### 9.2 Solids ladder → planets

```
:matter/dust-field
  → :matter/pebble-field          (coagulation, condensation)
  → :matter/planetesimal          (streaming instability or seed-and-grow)
  → :matter/protoplanet           (collisional growth)
  → :matter/dwarf-planet          (round but not cleared)
  → :matter/planet                (round and cleared)
```

Side branches from `:matter/planetesimal`:
- `:matter/asteroid` (rocky, non-rounded, belt-bound)
- `:matter/comet` (volatile-rich, thermally active)
- `:matter/debris-cloud` (disruption)
- `:matter/ring-particle` (inside Roche zone)

### 9.3 Planet environment evolution

```
:env/magma-ocean
  → :env/impact-reset
  → :env/crusted-volcanic
  → :env/ocean-world
  → :env/temperate-habitable
```

Common exit paths from `:env/temperate-habitable`:
- `:env/post-habitable` → `:env/arid-thin-atmosphere` (Mars-like)
- `:env/snowball`
- `:env/runaway-greenhouse` (Venus-like)
- `:env/impact-reset` (bombardment)

Icy/distant worlds:
```
:env/icy-volatile-world
  ↔ :env/cryovolcanic
  ↔ :env/subsurface-ocean
```

### 9.4 Atmosphere / EM evolution

```
:atm/stable-secondary
  → :atm/actively-stripped      (XUV increase or dynamo decline)
  → :atm/xuv-energy-limited     (deeper potential, lower flux)
  → :atm/xuv-recombination-limited (higher flux, high density)
  → :atm/collapsing
  → :atm/none
```

Magnetosphere:
```
:mag/stable-dynamo
  → :mag/episodic-dynamo
  → :mag/no-dynamo
  → :mag/collapsed              (under strong stellar wind)
```

### 9.5 Biosphere ladder

```
:bio/none
  → :bio/prebiotic              (solvent + energy + organics)
  → :bio/microbial              (self-replication)
  → :bio/complex                (multicellularity, ecosystems)
  → :bio/technological          (intelligence, tool use)
  → :bio/post-biological
```

Downward transitions (`:bio/extinct`) are allowed from any living state.

---

## 10. Implementation Sketch (Clojure Pseudocode)

The FSM is an **interpreter of simulation observables**, not the simulation itself. Pure classifiers live in `law.*` and write components; domain systems consume those components.

```clojure
(ns law.matter)

(defn classify-matter-state
  "Returns a single :matter/* keyword from a body/region snapshot."
  [{:keys [density temperature mass bound? fusion-sustaining? jeans-unstable?
           core-condensation-density opacity-limit deuterium-limit hydrogen-limit
           composition envelope-fraction]}]
  (cond
    fusion-sustaining?                    :matter/star
    (>= mass hydrogen-limit)            :matter/protostar
    (>= mass deuterium-limit)             :matter/brown-dwarf
    (>= mass opacity-limit)              :matter/gas-giant
    (and bound? (>= density core-condensation-density)) :matter/condensed-core
    jeans-unstable?                      :matter/collapsing-gas
    :else                                :matter/nebula))
```

```clojure
(ns law.environment)

(defn classify-environment-state
  "Priority-ordered guard evaluation for surface/interior regime."
  [{:keys [melt-fraction bombardment-reset-flux surface-pressure
           volatile-ice-dominated? runaway-forcing? liquid-solvent-stable?
           previously-habitable? tidally-heated?]}]
  (cond
    (> melt-fraction 0.5)                :env/magma-ocean
    (> bombardment-reset-flux 1e-3)      :env/impact-reset
    (< surface-pressure 1e-3)            :env/airless-inert
    volatile-ice-dominated?              :env/icy-volatile-world
    runaway-forcing?                     :env/runaway-greenhouse
    liquid-solvent-stable?               :env/temperate-habitable
    previously-habitable?                :env/post-habitable
    tidally-heated?                      :env/tidally-heated
    :else                                :env/crusted-volcanic))
```

```clojure
(ns law.handoff)

(defn material-class
  "Project rich matter-state + composition into public handoff class."
  [matter-state composition envelope-fraction]
  (cond
    (and (#{:matter/gas-giant :matter/ice-giant} matter-state)
         (> envelope-fraction 0.5)) :material-class/gaseous
    (> (:volatile composition) 0.5)  :material-class/icy
    (> (:rock composition) 0.5)      :material-class/rocky
    :else                            :material-class/mixed))

(defn thermal-band
  "Project equilibrium temperature into coarse band."
  [equilibrium-temperature]
  (cond
    (< equilibrium-temperature 150) :thermal-band/frozen
    (< equilibrium-temperature 220) :thermal-band/cold
    (< equilibrium-temperature 320) :thermal-band/temperate
    (< equilibrium-temperature 450) :thermal-band/warm
    :else                           :thermal-band/hot))

(defn atmosphere-class
  "Compress atmosphere-state into handoff summary."
  [atmosphere-state]
  (case atmosphere-state
    (:atm/none :atm/transient-outgassed) :atmosphere-class/none
    (:atm/collapsing :atm/actively-stripped :atm/xuv-energy-limited
     :atm/xuv-recombination-limited :atm/xuv-photon-limited :atm/frozen)
    :atmosphere-class/thin
    (:atm/stable-secondary :atm/substantial) :atmosphere-class/substantial
    (:atm/thick :atm/blowoff) :atmosphere-class/thick))

(defn core-dynamo?
  "Project magnetosphere-state into handoff boolean."
  [magnetosphere-state]
  (#{:mag/stable-dynamo :mag/compressed} magnetosphere-state))
```

```clojure
(ns domain.genesis.handoff
  (:require [domain.ecs :as ecs]
            [law.handoff :as h]))

(defn eligible-candidate? [world eid]
  (let [matter  (ecs/get world eid :matter-state)
        role    (ecs/get world eid :role-state)
        stable? (ecs/get world eid :orbit-stable?)
        temp    (ecs/get world eid :equilibrium-temperature)]
    (and stable?
         (#{:matter/planet :matter/dwarf-planet
            :matter/gas-giant :matter/ice-giant} matter)
         (not= role :role/interstellar-escape)
         (<= 150.0 temp 400.0))))

(defn planet-candidate-record [world star-id eid]
  {:planet-id               eid
   :star-id                 star-id
   :material-class          (ecs/get world eid :material-class)
   :thermal-band            (ecs/get world eid :thermal-band)
   :equilibrium-temperature (ecs/get world eid :equilibrium-temperature)
   :semi-major-axis         (ecs/get world eid :semi-major-axis)
   :eccentricity            (ecs/get world eid :eccentricity)
   :orbit-stable?          (ecs/get world eid :orbit-stable?)
   :atmosphere-class        (ecs/get world eid :atmosphere-class)
   :retained-species        (ecs/get world eid :retained-species)
   :bulk-composition        (ecs/get world eid :composition)
   :angular-momentum        (ecs/get world eid :angular-momentum)
   :rotation-axis           (ecs/get world eid :rotation-axis)
   :surface-gravity         (ecs/get world eid :surface-gravity)
   :core-dynamo?            (ecs/get world eid :core-dynamo?)
   :magnetic-field          (ecs/get world eid :magnetic-field)
   :formation-events        (ecs/get world eid :formation-events [])
   :candidate-kind          (candidate-kind world eid)})
```

---

## 11. Open Questions

1. **Minimum observables for classification:** Which subset of mass, radius, composition, orbit, temperature, and dynamo proxy is sufficient to classify each FSM without over-fitting?
2. **Dust/grain-size distribution as an ECS component:** Should the Matter FSM carry a full grain-size distribution, or a coarse categorical flag (dust/pebbles/boulders)?
3. **Streaming-instability threshold:** What precise local dust-to-gas ratio and Stokes-number condition should trigger `:matter/pebble-field → :matter/planetesimal`?
4. **Habitable moons:** Should the handoff record include `:candidate-kind :moon` for large satellites, or should the first Phase 0 handoff be strictly planet-centric?
5. **FSM oscillation guards:** How do we add hysteresis to environment transitions (e.g., `:env/temperate-habitable ↔ :env/snowball`) so that small annual fluctuations do not flip the state?
6. **Modifier aggregation:** If multiple modifiers affect the same transition, do they stack additively, multiplicatively, or via a priority order?

---

## 12. References

[^1]: McClure, M. K., van’t Hoff, M., Francis, L., et al. (2025). “Refractory solid condensation detected in an embedded protoplanetary disk.” *Nature*, 643, 649–653. DOI:10.1038/s41586-025-09163-z
[^2]: Johansen, A., Oishi, J. S., Mac Low, M.-M., et al. (2007). “Rapid planetesimal formation in turbulent circumstellar discs.” *Nature*, 448, 1022–1025. DOI:10.1038/nature06086
[^3]: IAU (2006). “Resolution B5: Definition of a Planet in the Solar System.” https://iauarchive.eso.org/static/resolutions/Resolution_GA26-5-6.pdf
[^4]: Stern, S. A., & Levison, H. F. (2002). “Regarding the Criteria for Planethood and Proposed Planetary Classification Schemes.” *Highlights of Astronomy*, 12, 205–213. DOI:10.1017/S1539299600013289
[^5]: Chen, J., & Kipping, D. (2017). “Probabilistic Forecasting of the Masses and Radii of Other Worlds.” *ApJ*, 834, 17. DOI:10.3847/1538-4357/834/1/17
[^6]: Seager, S., Kuchner, M., Hier-Majumder, C. A., & Militzer, B. (2007). “Mass-Radius Relationships for Solid Exoplanets.” *ApJ*, 669, 1279. DOI:10.1086/521346
[^7]: Sasselov, D. D. (2008). “Extrasolar planets.” *Nature*, 451, 617–619. DOI:10.1038/451617a
[^8]: Kopparapu, R. K., Ramirez, R., Kasting, J. F., et al. (2013). “Habitable Zones Around Main-Sequence Stars: New Estimates.” *ApJ*, 765, 131. DOI:10.1088/0004-637X/765/2/131
[^9]: Owen, J. E., & Jackson, A. P. (2012). “UV driven evaporation of close-in planets: energy-limited, recombination-limited, and photon-limited flows.” *ApJ*, 816, 34. DOI:10.3847/0004-637X/816/1/34
[^10]: Owen, J. E., & Wu, Y. (2017). “The Evaporation Valley in the Kepler Planets.” *ApJ*, 847, 29. DOI:10.3847/1538-4357/aa890a
[^11]: Luger, R., & Barnes, R. (2015). “Extreme Water Loss and Abiotic O₂ Buildup on Planets Throughout the Habitable Zones of M Dwarfs.” *Astrobiology*, 15, 119–143. DOI:10.1089/ast.2014.1231
[^12]: Birnstiel, T., Dullemond, C. P., & Brauer, F. (2012). “A coagulation/fragmentation model of the grain size distribution in protoplanetary disks.” *ApJ*, 764, 146. DOI:10.1088/0004-637X/764/2/146
[^13]: Krumholz, M. R., Myers, A. T., Klein, R. I., & McKee, C. F. (2016). “What Physics Determines the Peak of the IMF?” *MNRAS*, 460, 3272–3283. DOI:10.1093/mnras/stw1236
[^14]: Spiegel, D. S., Burrows, A., & Milsom, J. A. (2010). “The Deuterium-Burning Mass Limit for Brown Dwarfs and Giant Planets.” *ApJ*, 727, 57. DOI:10.1088/0004-637X/727/1/57
[^15]: Truth seed-and-grow parameters are documented in `kanban/tasks/seed-and-grow-condensation.md` and `docs/research/cosmology/hops315-protostar-case-study.md`. The current seed mass of `1.0e16 kg` is chosen to be well below a gas-parcel mass (~10^27 kg) and representative of a ~100 km rocky body.
