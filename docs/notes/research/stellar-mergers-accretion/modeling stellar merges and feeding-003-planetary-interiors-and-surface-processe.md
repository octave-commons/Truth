---

# Ok, this works well, how about the plants geology? and astroids non uniform shapes and composition? After the atmosphere is present, these would be the last things to handle for the world the player will soon inhabit to be shaped interestingly, mountains, bodies of water, volcanos, techtonic plates. When the player reaches the civilizational phase, they will be mining rocks out of the ground, shaping the ground, turning the earth into structures and tools. We have to be able to model this, voxels are needed for for the highest resolution physical interactions with massive bodies. Before civilization, or life forms, it will also be needed to model the collision of massive bodies like astroids and planets into one another.

Once atmospheres exist, the next layer is planetary geology and small‑body structure. The good news is: planetary science already gives you a clean set of processes and scales you can mirror with voxels and ECS.

***

## Planetary interiors and surface processes

Terrestrial planets and large moons share a common interior architecture: high‑density metallic core, rocky mantle, and low‑density crust, overlain by a lithosphere that can behave as rigid plates. Interior heat (accretion, differentiation, radioactive decay) drives four major surface processes:[^9_1][^9_2]

- **Impact cratering** – reshapes surfaces early on; crater properties depend on target strength, gravity, and impactor size/velocity.[^9_3][^9_1]
- **Volcanism** – eruption of molten material from interior to surface; builds plains, shield volcanoes, stratovolcanoes, and cryovolcanic features on icy worlds.[^9_4][^9_5][^9_2]
- **Tectonism** – deformation of the lithosphere by mantle convection and cooling; causes rifts, mountain belts, faults, and, on Earth, mobile plates with subduction and seafloor spreading.[^9_6][^9_2][^9_1]
- **Erosion** – reshapes surfaces via liquids, ices, and winds; creates valleys, deltas, dunes, sedimentary layers, and gradually erases craters.[^9_2][^9_1]

Your geologic model should encode:

- Interior heat budget and lithosphere thickness → whether tectonics/volcanism are ongoing or fossil.
- Planet size and rotation → strength of erosion (dynamic atmosphere vs thin or frozen).[^9_1][^9_2]

These parameters decide whether the world has active mountains, oceans, volcanoes, and plate motion, or a frozen, heavily cratered surface.

***

## How to model geology in the engine

### Macro fields: plates and convective forcing

At the “world field” level, you don’t need a full geodynamic solver. You can represent:

- A small set of **plates** for Earth‑like worlds: each plate has boundaries (divergent, convergent, transform) and relative velocities.[^9_6][^9_1]
- A mantle convective pattern (upwellings, downwellings) that seeds hotspots and rift zones.

From these, you derive:

- Elevation trends: convergent margins → orogeny (mountains); divergent margins → rifts and mid‑ocean ridges; hotspots → shield volcano chains.[^9_1][^9_6]
- Stress fields that control where fracturing and fault zones exist.

This can all be stored as coarse grids or analytic functions attached to the planet entity (e.g., `c/plate-field`, `c/mantle-convection-field`).

### Voxels for high‑resolution terrain

Mountains, oceans, volcanoes, and tectonic features are where voxels shine:

- Use voxels to represent the upper crust and shallow mantle in a band under the surface (say, a few tens of km thick), capturing rock type, porosity, and temperature at high resolution where the player will interact.
- Above and within that voxel band, bodies of water, ice sheets, lava flows, and sediment deposits can be represented as voxel materials with appropriate physics (flow, erosion).

Planetary geology sources emphasize that volcanism and tectonism often erase older features; erosion requires a dynamic atmosphere and water/ice. You can drive voxel updates from:[^9_1][^9_2]

- Volcanic events: inject hot magma that flows and solidifies; add gases to atmosphere.[^9_4][^9_5][^9_2]
- Tectonic events: uplift/downwarp voxel regions along plate boundaries; create fault scarps and basins.[^9_6][^9_1]
- Erosional events: gradually modify voxel elevations and materials based on rainfall, rivers, glaciers, and wind, using simple erosion rules.[^9_2][^9_1]

The key is: macro fields (plates, convective cells) determine where and when events occur; voxels capture the detailed shape and material response in those regions.

***

## Asteroids and small bodies: non‑uniform shapes and composition

Observations and modeling show many asteroids are **rubble piles**: gravitational aggregates of boulders and regolith, often highly porous and non‑spherical. Their interiors are heterogeneous: mixed rock, metal, voids, and sometimes ice.[^9_7][^9_8][^9_9]

For these, your representation should be voxel‑first:

- Use voxels to store density, composition, and cohesion at each location inside the asteroid. This naturally supports irregular shapes, voids, and layered structures (e.g., metallic core, rocky mantle, loose regolith).
- Attach a coarse shape hull (convex or level set) used for gravity and broad collisions; fine detail comes from voxel interactions when impacts or player mining occur.

Rubble pile research emphasizes:

- Low bulk strength: bodies deform and rearrange under tides and impacts.[^9_8][^9_7]
- Size‑dependent impact response: fragmentation energy and ejecta velocity distributions scale differently for large bodies than simple cratering laws predict.[^9_10][^9_11]

That aligns well with treating small bodies as deformable voxel aggregates rather than rigid meshes.

***

## Collisions between massive bodies

Asteroid–planet collisions and big impact events are best modeled with **shock physics** codes (hydrocodes) that resolve stress waves, fracture, and ejecta. You won’t run a full hydrocode in real time, but you can:[^9_12][^9_10][^9_11]

- Use scaling laws and approximate models from hydrocode studies to compute crater size, ejecta mass, melt volume, and fragmentation regime given impact velocity, angle, and material properties.[^9_10][^9_11][^9_12]
- Apply those outcomes as operations on your voxels:
    - Excavate a crater: remove and redistribute voxels to form a bowl, central peak, and ejecta blanket.
    - Fracture and fragment: split the impacting asteroid’s voxel body into fragments and debris fields.
    - Generate secondary effects: tsunamis for ocean impacts, atmospheric dust loading for climate perturbations.[^9_3][^9_13][^9_2]

Large‑scale impact modeling work (3D hydrocodes) shows you can treat angle, target layering, and ocean vs continent targets as parameters in those scaling relations. That’s exactly the level you want: realistic outcomes, but computed via formulas that drive voxel edits and event logs.[^9_13][^9_3]

***

## Civilizational phase: mining, construction, and terrain shaping

Planetary geology texts underscore that rock composition, tectonics, volcanism, and erosion together determine where mineral resources, mountains, basins, and fertile plains end up. For the player’s civilization phase, you can leverage that:[^9_3][^9_2][^9_14]

- Let geological history pre‑populate the voxel world with rock types, ore bodies, sedimentary basins, and existing structures (mountain belts, volcanoes, river valleys).[^9_1][^9_2][^9_3]
- Attach resource fields to voxels: each voxel knows its material (basalt, granite, ore, ice, regolith) and properties (strength, value, processability).

Civilization‑time interactions then become:

- **Mining**: the player removes or alters voxels, changing the local geology and resource field; mining deeper reaches different units (e.g., ore veins, mantle xenoliths).
- **Construction**: voxels are reassembled into engineered structures—buildings, tunnels, dams, space elevators—whose stability depends on underlying voxel support and material properties.
- **Land shaping**: cut and fill operations, terraforming, damming rivers, diverting lava or water—all voxel operations driven by player tools.

Because your geology is physically seeded by earlier processes, this creates a coherent story: the civilization exploits the actual geologic record the simulation produced, not arbitrary noise.

***

## Level of detail and when to use voxels

Given everything above:

- Use **macro geology fields** everywhere: interiors, plates, mantle convection, stress, and broad elevation, plus bulk properties for asteroids. These can be updated cheaply and drive events and resource distributions.[^9_1][^9_2]
- Use **voxels** selectively:
    - Near the surface on habitable or player‑visited worlds (upper crust + shallow mantle).
    - Inside small bodies (asteroids, comets, rubble‑pile moons) where non‑uniform structure matters.
    - In local volumes where collisions, volcanism, or tectonic deformation are currently happening and visibly affect the player.

Impacts, volcanoes, tectonism, erosion, and civilization‑time mining/construction all modify voxels; the rest of the universe can be evolved using coarse geology and event statistics.

That gives you a consistent, physics‑grounded path from “planets with atmospheres” to “planets with mountains, oceans, volcanoes, plates, asteroids, and mined landscapes,” all integrated with your existing ECS and observer‑centric design.
<span style="display:none">[^9_15]</span>

<div align="center">⁂</div>

[^9_1]: http://sirius.bu.edu/withers/teaching/as101_summer1_2006/ch09a_summary.pdf

[^9_2]: https://www.nhbs.com/de/planetary-geology-book

[^9_3]: https://ares.jsc.nasa.gov/interaction/lmdp/documents/58263main_planetary_geology.pdf

[^9_4]: https://www.lyellcollection.org/doi/10.1144/sp401.22

[^9_5]: https://fiveable.me/planetary-science/unit-5/volcanism-tectonics-solar-system/study-guide/rpKXOlfecfLv9hTo

[^9_6]: https://www.lpi.usra.edu/education/explore/shaping_the_planets/tectonism/

[^9_7]: https://arxiv.org/ftp/arxiv/papers/1810/1810.01815.pdf

[^9_8]: https://pages.astro.umd.edu/~dcr/reprints/richardson_icarus173,349.pdf

[^9_9]: https://arxiv.org/pdf/0807.1264.pdf

[^9_10]: https://ntrs.nasa.gov/citations/19940016256

[^9_11]: https://arxiv.org/abs/1502.01844

[^9_12]: https://arxiv.org/pdf/1502.01844.pdf

[^9_13]: https://gepris.dfg.de/gepris/projekt/20497678

[^9_14]: https://www.slideshare.net/slideshow/09-lecture-outline-60394345/60394345

[^9_15]: https://www.ucolick.org/~max/Astro18-2014/Lectures/Lecture11 2014 Terrestrial Planets/Lecture11.v2_sm-print.pdf


---

# How do we handle planetary collisions? The rocks probably become fluids in the field... they hit each other, we use the shock physics to determine what parts of the voxel shape are shaved off, as they cool they become astroid like bodies...

At planetary scales your intuition is right: solid rock behaves like a shocked fluid for the duration of the collision, and the outcome is “how much is excavated, melted, vaporized, and reassembled” rather than a simple rigid-body hit.[^10_1][^10_2]

Here’s a way to handle that in your engine.

***

## 1. Use shock/point‑source physics to get the bulk outcome

Impact cratering theory and hydrocodes boil a huge amount of physics down to a few key quantities:[^10_3][^10_2][^10_4]

- **Coupling parameter** – a combined measure of impactor radius, velocity, and density, typically of the form $a U^\mu \rho^\nu$, which sets how strongly energy and momentum are coupled into the target.[^10_2][^10_3]
- **Scaling laws** – power‑law relationships between that coupling parameter and:
    - Transient crater size and excavation depth.
    - Volume of shocked melt and vapor.
    - Ejecta mass and velocity spectrum.[^10_5][^10_3][^10_2]

Hydrocode work (CTH and others) has produced fits for:

- **Melt and vapor mass** as a function of projectile mass, impact velocity, impact angle, target porosity, and starting temperature.[^10_6][^10_5][^10_2]
- **Crater dimensions and excavation flow** via modified Maxwell Z‑models.[^10_7][^10_5][^10_1]

In the engine, you don’t solve full hydrodynamics; you:

1. Compute the coupling parameter from impactor size, speed, and density.
2. Use published scaling laws (or fit curves from hydrocode tables) to get:
    - Melt volume.
    - Vaporized volume.
    - Excavated volume and crater radius/depth.
    - Ejecta mass distribution and typical speeds.

That gives you *how much* of the target and impactor “turns to fluid” and how much is removed, without simulating every shock wave.

***

## 2. Map those results onto voxels

Once you know the volumes, you apply them to your voxel fields:

### a) Excavation: shaving off the shape

- Take the excavated volume and carve a transient crater in the target:
    - Remove voxels inside the excavation bowl (using the scaled crater radius/depth).
    - Redistribute those voxels as an ejecta blanket around the impact site (lower density, more fragmented material).[^10_1][^10_3]

This is your “shaved off” material: the outer layers removed by excavation.

### b) Melting: turning rock voxels into fluid

- Tag voxels in the melt region (usually a lens beneath the crater, and parts of both bodies) as **molten**:
    - Change their material state to fluid with appropriate viscosity and temperature.
    - Allow them to flow for some relaxation time (forming melt pools, ponds, seas depending on scale).[^10_5][^10_6]

For giant impacts, melt volumes can be global: you may briefly have a planet‑wide magma ocean before it cools.[^10_8][^10_6]

### c) Vaporization: atmosphere or loss

- Voxels in the vapor region are removed from the solid field and added to:
    - The atmosphere of the target (if bound).
    - A temporary debris/gas field that may escape depending on velocity vs escape speed.[^10_3][^10_5]

This is where large impacts generate steam atmospheres, rock vapor, and high‑altitude dust that feed your climate systems.

### d) Fragmentation and rubble

- Use hydrocode‑derived fragment size distributions and ejecta speed laws to decide:
    - How much of the impactor (and target) ends up as large fragments vs small debris.[^10_4][^10_9][^10_10]
- Spawn new bodies from these fragments:
    - Large coherent chunks become new asteroids or moons, built from voxel subsets of the original shape.
    - Smaller fragments become rubble‑pile aggregates or are folded into existing rings/disks.

Over time, as these fragments cool and re‑accrete, they naturally become “asteroid‑like bodies”: irregular rubble piles with mixed composition and voids.

***

## 3. Regimes: cratering, disruption, and merging

Scaling work and hydrocode studies distinguish several regimes:[^10_10][^10_2][^10_4]

- **Simple cratering** – small impactor; excavation and some melt, target mostly intact.
- **Complex cratering / basin formation** – larger impact; central peaks, rings, extensive melt sheets.
- **Catastrophic disruption** – impact energy high enough that the target’s gravity cannot reassemble it; you get many fragments and no main remnant.
- **Merging / accretion** – low‑velocity, similar‑mass collisions where the bodies partially merge and re‑shape, with global melt and relaxation.

Your collision system should first classify the event into one of these regimes based on impact energy vs target binding energy and strength, then apply scaling appropriate to that regime.

Implementation sketch:

- Compute $Q = \frac{1}{2} m_{\text{imp}} U^2 / M_{\text{target}}$ vs disruption thresholds from literature.[^10_4][^10_10]
- If $Q$ is low → cratering path (local voxel edits).
- If $Q$ near catastrophic → heavy fragmentation, multiple new bodies.
- If low velocity and similar masses → global melt and relaxation into a new merged voxel shape.

***

## 4. Cooling back into solids and new shapes

Once the “fluid” phase is over, you let the system cool:

- Apply simple cooling timescales for melt bodies (conductive + radiative), converting molten voxels back into solid rock types (basaltic crust, impact melt breccias, etc.).[^10_11][^10_6][^10_5]
- Re‑run your gravity/compaction for fragments:
    - Small aggregates settle into irregular rubble piles.
    - Large merged bodies relax toward hydrostatic shapes, but can retain non‑uniform crust/mantle structures depending on where melt pooled.

The result:

- The main planet may have a new basin, global tectonic consequences, and long‑term climate effects.
- The impactor and shaved rock volumes become new asteroid‑like voxel bodies, rings, or moons, all physically consistent with the scaling laws you applied.

So yes: at collision time, rocks *effectively* become fluids in the field; you use shock/point‑source physics to determine where and how much to excavate, melt, and vaporize, and then express those outcomes as voxel carving, re‑flow, and fragment spawning. The cooling and re‑accretion stage is what turns those fluid fields back into solid asteroids and planets.
<span style="display:none">[^10_12][^10_13][^10_14]</span>

<div align="center">⁂</div>

[^10_1]: https://ntrs.nasa.gov/api/citations/19920009568/downloads/19920009568.pdf

[^10_2]: https://ntrs.nasa.gov/api/citations/19870014063/downloads/19870014063.pdf

[^10_3]: https://impactswiki.net/impact-tools-book/crater-scaling.html

[^10_4]: https://arxiv.org/abs/1502.01844

[^10_5]: https://www.academia.edu/85161023/Impacts_onto_H2O_ice_Scaling_laws_for_melting_vaporization_excavation_and_final_crater_size

[^10_6]: https://meetingorganizer.copernicus.org/EPSC-DPS2019/EPSC-DPS2019-1686-1.pdf

[^10_7]: https://www.lpi.usra.edu/lunar/tools/lunarcratercalc/theory.pdf

[^10_8]: https://www.arxiv.org/pdf/1106.6084.pdf

[^10_9]: https://ntrs.nasa.gov/citations/19940016256

[^10_10]: https://arxiv.org/pdf/1502.01844.pdf

[^10_11]: https://ares.jsc.nasa.gov/interaction/lmdp/documents/58263main_planetary_geology.pdf

[^10_12]: https://oxfordre.com/planetaryscience/display/10.1093/acrefore/9780190647926.001.0001/acrefore-9780190647926-e-7?d=%2F10.1093%2Facrefore%2F9780190647926.001.0001%2Facrefore-9780190647926-e-7\&p=emailA49dJJfBheKlw

[^10_13]: https://www.sciencedirect.com/science/article/pii/S0019103511001898

[^10_14]: https://pdssbn.astro.umd.edu/holdings/di-c-hrii_hriv_mri_its-6-doc-set-v4.0/document/publications/di/space_science_reviews/crater_modeling_richardson.pdf


