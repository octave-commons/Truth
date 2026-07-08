---

# Prompt wizard, in depth. The agent should go to great lengths to do research and cite sources, provide visualizations, graphs, charts and ground it in [Truth](https://github.com/octave-commons/Truth)

Here is the maximally detailed **Prompt Wizard** version, engineered for an agent that will do exhaustive multi-round research, cite primary sources, generate charts and visualizations, and ground every recommendation directly in the Truth codebase:

***

```
# Deep Research Brief: Gates of Truth — Physics Simulation Architecture

## Mission

You are a senior astrophysics and game simulation architect. Your task is to
produce an exhaustive, primary-source-cited, visualization-rich research report
that will directly inform the implementation of **Gates of Truth** — a full-stack
pure Clojure planetary and stellar simulation game.

The project lives at: https://github.com/octave-commons/Truth

Before writing a single word of your report, you MUST:

1. Read the current codebase. Start by fetching the file tree at
   https://github.com/octave-commons/Truth and then read the following files
   in full:
   - src/domain/stellar.clj
   - src/domain/em.clj
   - src/domain/chemistry.clj
   - src/domain/phase0.clj
   - src/domain/regime.clj
   - src/law/stellar.clj
   - src/law/field.clj
   - src/law/ledger.clj
   - src/law/contract.clj

2. For each section of this brief, search academic and primary sources
   (arXiv, NASA ADS, MNRAS, ApJ, Icarus, JGR Planets, LPI) and cite at
   minimum 3 independent authoritative sources per major claim.

3. Produce at minimum 8 charts, diagrams, or visualizations (using Plotly
   or Mermaid) covering:
   - Stellar SED comparison across star types
   - Condensation sequence temperature vs material
   - Streaming instability parameter space (solid-to-gas ratio vs Stokes number)
   - Crater scaling law regime diagram
   - Planet formation timeline from dust to finished world
   - Tectonic activity vs planet mass and age
   - LOD architecture diagram (observer-centric fidelity layers)
   - ECS component dependency graph across simulation layers

4. For every recommendation, include a concrete mapping to the Truth codebase:
   - which existing namespace is the right home,
   - what new namespace/file should be created if none exists,
   - what Malli schema should be defined in src/law/,
   - what ECS component should be added,
   - what system function should be written,
   - what the failing test should assert BEFORE implementation.

---

## Context

Gates of Truth is a full-stack pure Clojure JVM simulation game. Its
architecture uses:

- Four namespace quadrants: domain/ (pure sim), infra/ (rendering/IO),
  shape/ (geometry), law/ (Malli schemas + validators).
- A parallel double-buffer ECS pipeline: all systems read frozen snapshot N,
  write snapshot N+1, fold at a barrier. Order within the parallel fan-out
  is irrelevant.
- An event ledger that logs astrophysical transitions.
- A phase-based simulation lifecycle: Phase 0 covers nebular collapse through
  stellar ignition.

The core design principle is:
> Model everything as physically accurately as possible IN PRINCIPLE,
> but only RESOLVE high-fidelity physics when it materially affects
> what the player observes or interacts with.
> Everything else is driven by coarse fields, scaling laws, or statistical
> event models calibrated by high-fidelity runs.

Current known shortcomings:
- Stellar wind is modeled as cold neutral gas parcels (wrong: should be hot
  ionized plasma with ram-pressure field model).
- Luminosity is a scalar brightness (wrong: should be panchromatic SED
  per star type).
- No disk microphysics between collapse and planet formation.
- No geology/tectonics/voxel layer for planetary surfaces.
- No collision physics for asteroid/planet impacts.
- No galaxy-level context for star population and event environment.

---

## Research Sections

### Section 1: Stellar Radiation and Plasma

Research and report on:

- The full electromagnetic spectrum emitted by different star types
  (O, B, A, F, G, K, M, white dwarfs, neutron stars).
- Panchromatic stellar SEDs: what determines the SED shape, and how
  to parameterize it from (T_eff, log_g, metallicity, rotation, age).
- Stellar atmosphere layering: photosphere, chromosphere, transition
  region, corona. Key state variables per layer.
- Stellar winds as ionized plasma: Parker solar wind theory, mass loss
  rates, wind velocities, ram pressure as a field, Alfvén radius.
- XUV irradiation of planets: X-ray + EUV driven atmospheric escape,
  energy-limited vs recombination-limited escape.
- Flare activity and space weather: spot cycles, flare energy distributions,
  CMEs, effects on planetary magnetospheres and atmospheres.

Deliverables:
- Chart: SED comparison (log flux vs wavelength) for O5, G2, M5 star types.
- Chart: XUV luminosity vs stellar age for solar-type stars.
- Table: per-band (gamma/X/EUV/UV/vis/NIR/MIR/FIR/radio) physical effects
  on planets.
- Concrete Truth mapping: c/atmosphere-shells, c/wind-profile, c/sed-bands,
  domain.plasma namespace proposal, replacement for spawn-clump in
  stellar-wind-system.

### Section 2: Disk Microphysics and Planet Formation Bridge

Research and report on:

- Grain growth from micron dust to pebbles: coagulation, fragmentation
  barriers, bouncing barrier.
- Radial drift and vertical settling: Stokes number, drift timescales,
  pebble delivery to inner disk.
- Condensation sequence and ice lines: which materials freeze out where,
  how that controls solid surface density jumps.
- Streaming instability: physical mechanism, onset conditions
  (solid-to-gas ratio, Stokes number, turbulence), outcome
  (planetesimal size distribution).
- Pebble accretion vs planetesimal accretion: which dominates, when,
  consequences for core composition and growth rate.
- Disk-planet interaction: Type I and Type II migration torques,
  resonance trapping, instability after disk dispersal.
- System architecture classes: compact chains, solar analogues, hot
  Jupiters, debris-disk systems.

Deliverables:
- Chart: streaming instability onset in (St, Z) parameter space, showing
  laminar vs turbulent thresholds.
- Chart: condensation temperature vs material, annotated with snow line
  positions in a solar-type disk.
- Chart: planet formation timeline (log time vs mass) from dust to
  finished planet.
- Concrete Truth mapping: c/disk-cell component, disk-evolution-system,
  planetesimal-formation-system, pebble-accretion-system, migration-system.

### Section 3: Planetary Geology and Surface Evolution

Research and report on:

- Magma ocean formation, crystallization, and early crust formation.
- Mantle convection: parameterized convection models, heat flux, style
  (stagnant lid vs mobile lid vs episodic).
- Plate tectonics onset and driving: what determines whether a planet
  develops plate tectonics (mass, composition, water content, cooling rate).
- Volcanism: hotspot chains, arc volcanism, flood basalts; relationship
  to mantle convection pattern.
- Mountain building, rifting, basin formation.
- Erosion, hydrology, sediment transport: required state variables for
  surface evolution over geological time.
- Ocean formation: water delivery, ocean basin development, salinity.
- Long-term climate regulation: silicate weathering feedback, carbon cycle.

Deliverables:
- Chart: tectonic regime vs planet mass and surface temperature
  (stagnant lid / episodic / mobile plate).
- Chart: heat flux vs time for Earth-mass, Mars-mass, Super-Earth-mass
  planets.
- Diagram: ECS component stack for planetary interior and surface
  (from core → mantle → crust → surface field → voxel band → atmosphere).
- Concrete Truth mapping: c/interior-state, c/plate-field,
  c/mantle-convection, c/surface-geology, tectonic-system,
  volcanism-system, erosion-system namespaces.

### Section 4: Asteroids, Moons, and Non-Uniform Bodies

Research and report on:

- Rubble pile structure: porosity, tensile strength, cohesion,
  observed shapes, spin limits (Jacobi ellipsoids, YORP).
- Differentiated vs undifferentiated asteroids: what determines
  which type forms.
- Regolith: formation, properties, depth, thermal behavior.
- Non-spherical gravitational potential: implications for orbits and
  tidal interaction.
- Moon formation: giant impact debris, capture, co-formation,
  tidal evolution.
- Collision remnants: how voxel subsets of original bodies become
  new aggregates.

Deliverables:
- Chart: asteroid spin rate vs diameter (spin barrier / rubble pile
  boundary).
- Diagram: voxel representation of asteroid interior (metallic core,
  silicate mantle, regolith shell, voids).
- Concrete Truth mapping: c/voxel-field, c/body-structure,
  voxel-physics-system, when to use voxels vs coarse hull.

### Section 5: Impact Physics and Collision Outcomes

Research and report on:

- Pi-group crater scaling laws: transient crater size, excavation depth,
  melt volume, ejecta velocity distribution.
- Regime classification: simple cratering, complex cratering, basin
  formation, catastrophic disruption, merging.
- Hydrocode insights: what shock physics codes (iSALE, SPH-based codes)
  tell us about melt/vapor/fragmentation.
- Giant impact events: magma ocean generation, atmosphere blow-off,
  moon-forming debris disks.
- Post-impact cooling and reaccretion: how fragments cool, compact,
  and regain solid character.
- Impact parameter effects: angle, velocity, porosity, target/impactor
  density ratio.

Deliverables:
- Chart: impact regime diagram (impactor mass vs impact velocity,
  showing cratering/disruption/merging zones).
- Chart: melt volume vs impact energy, across target types.
- Flowchart: collision-system decision tree (classify regime →
  compute scaling outcomes → apply voxel edits → spawn fragments →
  cool/reaccrete).
- Concrete Truth mapping: collision-system, fragment-spawn-system,
  melt-flow-system, reaccretion-system, shock-outcome pure functions.

### Section 6: Climate, Hydrology, and Pre-Biosphere Groundwork

Research and report on:

- Zero-dimensional and 1D energy-balance climate models: what's
  sufficient for a simulation game.
- Greenhouse forcing: key species (CO2, H2O, CH4, N2O), band models,
  simplified parameterizations.
- Cloud and haze effects: albedo modifiers, anti-greenhouse.
- Water cycle: evaporation, precipitation, runoff, ocean basin
  interaction.
- Ice-albedo feedback and snowball states.
- Runaway greenhouse threshold and moist/dry limits.
- Silicate weathering thermostat: simplified parameterization.

Deliverables:
- Chart: climate stability zones (stellar flux vs CO2 level, showing
  snowball / temperate / runaway regimes).
- Diagram: climate-geology-atmosphere coupling loop.
- Concrete Truth mapping: c/climate-state, c/ocean-state,
  climate-system, hydrology-system, coupling with
  existing domain.atmosphere.

### Section 7: Galaxy-Scale Context and Universe Modeling

Research and report on:

- IMF (initial mass function): stellar mass distribution in galaxies.
- Galaxy star formation histories: how star type and metallicity
  distributions evolve with galactic age.
- Metallicity gradients: inner vs outer galaxy, effect on planet
  formation efficiency.
- Dark matter halos: NFW and cored profiles, when they matter vs don't.
- Black holes: stellar-mass BHs, supermassive BHs, AGN activity, jets,
  feedback on star formation.
- Gamma-ray bursts: rates, beaming, atmospheric impact on planets.
- Supernovae: rate, ejecta, role in seeding ISM metallicity.

Deliverables:
- Chart: IMF (dN/dM vs stellar mass).
- Chart: GRB rate vs redshift / galactic environment.
- Diagram: galaxy LOD layers (full-detail system → regional field →
  galaxy coarse → meta-universe).
- Concrete Truth mapping: c/star-light (low-res), c/galaxy-halo,
  c/event-source, galaxy-seed-system, promotion/demotion logic
  for zooming in/out.

### Section 8: Observer-Centric LOD Architecture and Event Statistics

Research and report on:

- Existing game/simulation approaches to observer-centric LOD:
  streaming, procedural generation, level promotion/demotion.
- Statistical/event-driven simulation: hazard models, Poisson processes,
  conditional distributions, calibration from high-fidelity runs.
- Population synthesis in astrophysics: how Bern Model and similar
  frameworks derive planet populations from distributions.
- Observational pipeline: truth state → simulated instrument → player
  observation, with noise and resolution limits.
- Timescale stitching: how to transition between Gyr-scale formation,
  Myr-scale climate, kyr-scale geology, year-scale civilization.

Deliverables:
- Diagram: full observer-centric LOD architecture (from voxels to
  galaxy background).
- Diagram: event/statistics mode pipeline (high-fidelity sim →
  event log → fit distributions → sample for off-screen evolution).
- Concrete Truth mapping: event-statistics-system, lod-scheduler,
  phase-transition hooks in domain.phase0, integration with
  existing law/ledger.clj.

