# Deep Research Brief: Physics Simulation Architecture

**Topic:** Full-physics research program for Gates of Truth.  
**Source:** `# Deep Research Brief_ Gates of Truth — Physics Si` (Perplexity export).  
**Status:** Research program scoped; individual sections pending formal notebooks.

## Purpose

Define the literature-backed physics models needed to move Gates of Truth from an architectural sketch to a fully grounded simulation. The brief covers eight domains and asks for governing equations, benchmark values, and explicit mappings to the four-quadrant Clojure architecture.

## Core Design Principle

Model everything as physically accurately as possible in principle, but resolve high-fidelity physics only where it materially affects what the player observes or interacts with. Everything else is driven by coarse fields, scaling laws, or statistical event models calibrated by high-fidelity runs.

## Research Sections

| Section | Domain | Key Question | Proposed `docs/research/` Target |
|---|---|---|---|
| 1. Stellar Radiation and Plasma | cosmology/physics | Panchromatic SEDs, stellar wind plasma, XUV escape, flare activity | `cosmology/stellar-radiation-plasma.md` |
| 2. Disk Microphysics and Planet Formation | physics | Dust, pebbles, streaming instability, migration, system architecture | `physics/protoplanetary-disks-extended.md` |
| 3. Planetary Geology and Surface Evolution | geology | Magma oceans, mantle convection, plate tectonics, erosion, climate regulation | `geology/planetary-geology-surface-evolution.md` |
| 4. Asteroids, Moons, and Non-Uniform Bodies | physics/geology | Rubble piles, regolith, non-spherical gravity, moon formation | `physics/asteroids-moons-nonuniform-bodies.md` |
| 5. Impact Physics and Collision Outcomes | physics/geology | Crater scaling, disruption/merging regimes, hydrocode insights | `physics/impact-physics-collision-outcomes.md` |
| 6. Climate, Hydrology, and Pre-Biosphere | atmosphere | Energy-balance models, greenhouse, water cycle, runaway/snowball thresholds | `atmosphere/pre-biosphere-climate-hydrology.md` |
| 7. Galaxy-Scale Context and Universe Modeling | cosmology | IMF, metallicity gradients, dark matter halos, supernovae, GRBs | `cosmology/galaxy-scale-context-universe-modeling.md` |
| 8. Observer-Centric LOD and Event Statistics | physics/culture | Streaming, population synthesis, timescale stitching, observational pipeline | `physics/observer-centric-lod-event-statistics.md` |

## Known Shortcomings Addressed

- Stellar wind modeled as cold neutral gas → should be hot ionized plasma with ram-pressure field.
- Luminosity as scalar brightness → should be panchromatic SED per star type.
- Missing disk microphysics between collapse and planet formation.
- Missing geology/tectonics/voxel layer.
- Missing collision physics for asteroid/planet impacts.
- Missing galaxy-level context for star population and event environment.

## Connections to Other Topics

- Sections 1 and 2 directly feed the `phase0-nebula` and `hops315-fsm` work.
- Section 3 (geology) is the bridge into Phase 2 (rocky body formation).
- Section 6 (climate) couples with the `stellar-mergers-accretion` work through stellar flux and atmospheric escape.
- Section 8 (LOD) is the formalization of the rendering concerns in `formation-rendering`.

## Open Questions

- What is the minimum SED band count needed for gameplay-visible effects?
- Which planet-formation channel (core accretion, GI, streaming instability) dominates at Truth's resolution?
- How do we represent voxel interiors without forking the ECS substrate?
- What event-statistics distributions can be pre-calibrated to keep off-screen evolution tractable?
