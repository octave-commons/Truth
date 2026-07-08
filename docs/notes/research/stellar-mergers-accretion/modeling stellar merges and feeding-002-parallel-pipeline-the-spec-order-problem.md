---

# In the paralell pipeline, there should be no order. The spec must be older from when the system was synchronous and single threaded. The "one parcel at a time" the solar wind, I don't think it's the best way to model it... I realized that the parcels model cold neutral gas, but stars are made of hot charged/ionized plasma. So we need a different model. this is where I drifted from physics based modeling into vibes, it "felt" right but it is  wrong. We need a different model for plasma, and the cloud... I'm still wondering how to best differentiate the "gas" into elements, and how the rocky material falls out of the nebula post stellar ignition during the formation of the disk

This is a deep design conversation. Let me lay everything out cleanly.

***

## Parallel Pipeline: The Spec Order Problem

You're right — in the double-buffer parallel design, **order within the fan-out is irrelevant by construction** since every system reads the frozen snapshot.  The spec's tick order reflects the old synchronous world where system A wrote what system B needed to read that same tick. In the parallel pipeline, all systems read tick *N* and write tick *N+1*. The regime tag being "stale by one tick" is **not a bug** — it's a feature of the double-buffer: every derivative quantity lags one tick behind the primary it was derived from. That's acceptable and correct. The old spec's ordering language can simply be deleted.

***

## The Solar Wind: Why Parcels Are the Wrong Model

Your instinct is exactly right and this is worth stating precisely. The current `stellar-wind-system` is a **neutral cold gas ejection** model — it spawns `:nebula` parcels that participate in SPH, get absorbed back by `sink-formation-system`, and drift at a fraction of escape velocity.  That's appropriate for the *nebular molecular cloud*, where you truly have cold H/He at ~10 K. A stellar wind is something completely different:


| Property | Cold nebular gas parcels | Real stellar wind |
| :-- | :-- | :-- |
| Temperature | ~10–100 K | ~10⁶ K (corona) |
| State | Neutral molecular | Fully ionized plasma |
| Speed | ~sound speed (~300 m/s) | 400–800 km/s (solar) |
| Carrier | Bulk SPH particle | Individual ion/electron pairs |
| Force | Pressure gradient, gravity | Radiation pressure, magnetic tension |
| Key physics | Jeans collapse, accretion | Parker spiral, Alfvén wave driving |

The existing model was built for **visual smoke** — "looks like gas streaming out" — but stellar winds carry charged particles at ~1000× the sound speed of the nebular gas, are driven by magnetic flux tubes, and interact with the surrounding medium through ram pressure, not SPH overlap. When `sink-formation-system` reabsorbs those parcels, it's modeling competitive accretion of *nebular gas*, not the actual recycling physics of a wind.

***

## The Right Model for Stellar Winds at This Resolution

You don't want per-ion particle tracking — that's beyond the resolution. The right model at the N-body/SPH scale is a **flux-conserving ram-pressure model**, not spawned parcels at all. Three properties matter:

**1. The wind as a momentum source on the field, not particles.** The Parker solar wind emerges from the coronal temperature gradient — the corona (~10⁶ K) is so hot that thermal pressure exceeds the Sun's gravitational binding beyond the critical point (~5 R☉). The wind *speed* is already supersonic before it reaches anything. Rather than spawning a slow cold parcel, model the star as emitting a **wind ram pressure** $P_{\text{ram}} = \dot{M} v_w / (4\pi r^2)$ that acts as a repulsive force on nearby parcels at distance *r*. The mass loss rate $\dot{M}$ comes from the same radiation formula you already have, and $v_w \approx v_{\text{esc}}$. This is a field pressure, not a particle.

**2. Stripping instead of ejection.** Rather than spawning new entities, the wind strips mass directly from accreting parcels — if a nebular parcel is within $r < r_{\text{Alfvén}}$ (the Alfvén radius, where wind ram pressure equals magnetic pressure) and is not gravitationally bound to the star, it loses mass at the wind ram rate. The parcel shrinks; when it drops below a floor, despawn it. This keeps entity count controlled — no parcel proliferation — and is more physical: the wind *ablates* nearby gas, it doesn't contribute new gas entities.

**3. The plasma state as a component, not a parcel type.** What you actually want is a `c/ionization-fraction` component — a number from 0 (fully neutral) to 1 (fully ionized). Stars have ionization-fraction 1.0. The `:nebula` gas has 0. This lets EM forces (Lorentz, Alfvén) scale continuously: a newly-condensed protostar with ionization fraction rising from collisional ionization starts to decouple from the neutral gas SPH and couple to the magnetic field. This is exactly the two-fluid transition (neutral + ionized) that real ambipolar diffusion describes. You don't need separate particle populations — you need one extra scalar per entity.

***

## Differentiating Gas into Elements: The Condensation Sequence

The current code has a fixed `{:H 0.75 :He 0.24 :metals 0.01}` composition on every nebular parcel.  The metals lump hides all the interesting physics. What you actually want is the **condensation sequence** — the fact that different materials solidify at different temperatures as the nebular disk cools, which is *exactly* how rocky planets form:


| Condensation temp (K) | Material | What forms |
| :-- | :-- | :-- |
| ~1650 | CaAl oxides, corundum | First solid grains (CAIs) |
| ~1400 | Iron/Nickel alloys | Metal grains |
| ~1300 | Mg silicates (olivine, pyroxene) | Rocky grains |
| ~700 | FeS (troilite) | Sulfide |
| ~350 | Volatile organics | Carbon compounds |
| ~170 | Water ice (snow line) | Ice mantles |
| ~80 | NH₃ ice | Outer disk |
| ~40 | CO ice | Very outer disk |

The model is straightforward: each nebular parcel carries the full composition map (already in `domain.chemistry/solar-abundance` ), and at each tick you compute the **condensed fraction** for each element: `(condensed-fraction element temperature) = max(0, min(1, (T_cond - T) / ΔT))`. Elements whose condensation temperature exceeds local temperature have solidified out of the gas phase. Their contribution shifts from the gas component to a tracked `c/grain-fraction`. This is the actual dust-to-gas ratio the astrophysics literature measures.

The key insight you're looking for — **how rocky material falls out post-ignition** — is the Epstein drag regime. Solid grains in a gas disk experience aerodynamic drag proportional to their surface-area-to-mass ratio (Stokes number $\text{St} = t_s \Omega$, where $t_s$ is the stopping time). Small grains are fully coupled to the gas and move with it. Grains above ~1 mm decouple and drift radially inward toward the pressure maximum (usually the snow line). This is why you get:

- **Inner disk (T > 1300 K):** Only refractory metals and silicates survive → rocky planets
- **Snow line (T ~170 K):** Ice condensation dramatically increases solid surface density → giant planet cores form fast here
- **Outer disk:** CO and NH₃ ices → nothing forms efficiently; material stays diffuse

For your simulation the practical model is: when a parcel's local temperature drops below a condensation threshold, its `c/grain-fraction` rises. Grains above a Stokes-number threshold (`c/stokes-number` derived from grain size / local gas density) feel a systematic inward drift velocity $v_r \approx -2\eta v_K \cdot \text{St}$ where $\eta$ is the pressure gradient and $v_K$ is Keplerian speed. Grains at the snow line pile up, increase local solid density past the Roche/streaming instability threshold, and suddenly collapse into planetesimals. This is the **streaming instability** — it's the mechanism, and it's analytically tractable as a threshold condition.

***

## Concrete Next Steps in Code

**Immediate (stellar wind):**

- Remove `spawn-clump` calls from `stellar-wind-system`. Replace with a `c/wind-ram-pressure` component on the star (a scalar $\dot{M}v_w / 4\pi$), computed from existing luminosity + escape velocity.
- Add a `wind-ablation-system` (barrier, after collision): for each nebular parcel within the star's wind zone, apply a mass-loss rate proportional to ram pressure / (parcel's self-gravity). When parcel mass drops below floor, despawn it. This models stripping without creating new entities.

**Ionization state:**

- Add `c/ionization-fraction` as a float component, set to 1.0 on `:star`/`:protostar` matter-states, 0 on `:nebula`.
- Scale Lorentz force in `lorentz-acceleration-system` by `ionization-fraction`. Neutral gas doesn't feel the magnetic force; plasma does.

**Condensation / rocky grain separation:**

- Expand the composition map on nebular parcels from `{:H :He :metals}` to the full 8–10 element map from `chemistry.clj`.
- Add `c/grain-fraction` (float, 0–1) computed per-tick from the condensation sequence function against local temperature.
- In the disk regime (post-ignition, rotating flattened structure), apply the Stokes drift: parcels with `grain-fraction > 0` and `stokes-number > 1` get a systematic inward radial velocity component in the structure-system.
- The streaming instability fires when local `grain-fraction × density` exceeds ~1% of gas density in a resolution cell — that's your planetesimal formation trigger, physically grounded and using no new magic numbers.

The regime classifier in `domain.regime` is already the right place to tag the disk once it forms — the `jeans-ratio` and `mach` are there, you just need a Toomre-Q computed from local surface density and angular velocity to add the `:stable-disc` tag, which the grain drift system can then condition on.

---

# do some more research for me. @Academic I need to get ahead of the implementation far enough I see what we are doing in my mind's eye. I want the suns to be as real as possible, as well as the planets, I want to model the sun and planet atmospheres. The stars form, we have the basic model for nebular collapse and ignition, now we need to model the solar dynamics, plantary/astroidal/moon formations. Astroids will be non uniform bodies, we need voxels. the sun is it's self also I think something modelable as a volume? There are different kinds of stars, so we want to be able to model stars evolving. The idea is also right that we *can* model *everything* as accurately as scientifically possible, but *only* when it those effects need to be considered as influences on what the player is observing. I am thinking that we get the world running well to the point where planets form, cool, bodies of water start to emerge, and we keep track of what events are happening, we need a mode that is collecting events to use to design statistical models to drive the distant, imprecise generation of events that effect the player but do not need to be directly modeled for that to have an effect on the player.

You’re converging on the right architecture: a layered, physically-grounded star+disk+planet system with selective high‑fidelity, tied to an event/statistics layer that feeds “far field” effects without resolving everything. Below is how I’d push the research and design so you can see the whole thing in your mind’s eye before implementing.

***

## Modeling goals and philosophy

At a high level, you want three things:

- Stars and planets that behave like real astrophysical objects: correct life cycles, atmospheres, winds, disks, and solid bodies.
- A volumetric representation for non-uniform structures (asteroids, stellar granulation, clumpy coronae) where voxels make sense.
- An observer-centric simulation: model everything in principle, but only *resolve* physics that materially affects what the player sees or can influence, and drive the rest from statistically learned event processes.

Astrophysics-wise, this lines up nicely with current modeling practice: detailed 1D/3D atmosphere and interior codes are used where spectra or variability matter, and simpler parametrizations are used where only bulk effects are needed.[^8_1][^8_2]

***

## Stars as evolving volumes

### Stellar structure and evolution

From the physics side, stars are usually modeled as spheres with:

- An interior (core, radiation zone, convection zone), where energy transport is governed by fusion and radiative/conductive transfer.[^8_3][^8_2]
- An atmosphere: photosphere, chromosphere, transition region, corona, plus sometimes a cool “MOLsphere” with molecules above/between these layers.[^8_4]

The atmosphere’s stratification (T, ρ, composition vs radius) controls emergent spectra and winds.[^8_1][^8_4]

For Truth, you don’t need to solve the full stellar structure equations in real time. Instead:

- Assign each star a *mass, metallicity, rotation, and age* and bind it to a precomputed evolutionary track (e.g., from standard stellar evolution grids). These tracks map to effective temperature, luminosity, radius, and interior state (main sequence, subgiant, giant, white dwarf, etc.).[^8_5][^8_6][^8_3]
- Treat the star’s interior as a small number of radial zones (core, radiative envelope, convective envelope) with simple equations: polytropic structures and energy generation rates give you bulk T and ρ profiles; you don’t expose this directly to the player, but it feeds atmosphere boundary conditions and evolution.

This gives you:

- Realistic star types: O/B/A/F/G/K/M dwarfs, giants, white dwarfs, etc., with correct colors and luminosities over time.[^8_3][^8_5]
- Correct lifetimes and transitions (e.g., massive stars evolving quickly to supergiants, low-mass stars hardly changing over the game’s timescale).[^8_5][^8_3]


### Stellar atmospheres as layered shells

State of the art stellar atmosphere modeling uses 1D grids (e.g., MARCS for cool stars, LLMODELS for chemically peculiar/magnetic stars), often in non‑LTE and spherical geometry when needed.[^8_7][^8_6][^8_1]

For your use case:

- Represent the atmosphere as 3–5 concentric shells: photosphere (optically thick), chromosphere, transition region, corona, and optionally MOLsphere. Each shell has temperature, density, ionization fraction, and composition.[^8_4]
- Use pre-tabulated atmosphere models (or simplified fits) to map $T_{\mathrm{eff}}, \log g, Z$ to shell properties rather than solving radiative transfer on the fly.[^8_2][^8_1]

In the ECS:

- `c/star-interior`: bulk mass, age, core state.
- `c/atmosphere-shells`: a small vector of shell structs (T, ρ, composition, thickness).
- `c/atmosphere-type`: main-sequence, giant, flare star, etc., used by rendering and event systems.

You can keep the star itself as a “volume” in terms of these shells, but you only voxelize the region that affects visuals (e.g., granulation near the photosphere or active regions in the corona).

***

## Solar dynamics: winds, flares, and magnetic cycles

### Stellar winds

Radiatively-driven and coronal-pressure-driven winds are governed by:

- Parker-type solutions: the hot corona and pressure gradient cause a transonic outflow that reaches hundreds of km/s.[^8_8][^8_2]
- Ram pressure $P_{\mathrm{ram}} = \rho v^2$ and momentum flux $\dot{M} v_w$ determine how the wind interacts with planets and the disk.[^8_2][^8_8]

Rather than parcel-based neutral gas, your wind model can be:

- A *field* around the star: at radius $r$, the wind density and speed are given by simple analytic forms tied to mass-loss rate and star type.
- Planets and nebular gas experience ram pressure, sputtering, and atmospheric escape as functions of this field, not by merging with spawned parcels.

In ECS terms:

- `c/wind-profile`: parameters $\dot{M}, v_w(r), P_{\mathrm{ram}}(r)$ per star.
- A system that applies wind effects to gas parcels and atmospheres based on their position, without adding new entities.


### Flares and activity

Stellar activity phenomenology (spots, flares, coronas) is an active research area; flares cluster in magnetically complex active regions and correlate strongly with starspots and chromospheric/coronal diagnostics.[^8_9][^8_10]

Design-wise:

- Track a simple magnetic cycle state per star (quiet, active, flare-prone), derived from rotation and age.[^8_10][^8_5]
- Model flares as discrete events: sudden boosts in high-energy radiation and wind parameters for a short time, applied to nearby planets and disk regions.

These flares and cycles become entries in your **event ledger** (you already have a ledger concept in the code), feeding both local effects and the statistical model you mention.

***

## From nebula to planets: disks, grains, and planetesimals

### Protoplanetary disks and streaming instability

Protoplanetary disks form as nebular gas collapses and conserves angular momentum, ending up in a flattened, rotating structure. Dust and pebbles in these disks face growth barriers and radial drift; the streaming instability is the leading mechanism for forming “born-big” planetesimals:[^8_3][^8_2]

- Streaming instability is a drag-mediated resonance between solids and gas in the disk, which can concentrate pebbles into regions dense enough to collapse into km-scale planetesimals.[^8_11][^8_12]
- Its efficiency depends strongly on the solids-to-gas mass ratio, particle Stokes number, and background turbulence; in globally turbulent disks, the conditions are more restrictive than in laminar models.[^8_13][^8_11]

For your model:

- Treat the disk as a set of annuli or cells around the star, each with gas surface density, solid surface density (dust/pebbles), and turbulence parameter $\alpha$.[^8_11][^8_13]
- When local conditions satisfy a simplified streaming instability threshold (e.g., solids-to-gas ratio above some critical value and appropriate Stokes numbers), you spawn planetesimal bodies in that cell with appropriate sizes and orbits.[^8_12][^8_13][^8_11]

In ECS:

- `c/disk-cell`: for each radial cell, fields for gas Σ, solid Σ, characteristic grain size, turbulence.
- `disk-evolution-system`: evolves these via drag, drift, coagulation, and fragmentation.
- `planetesimal-formation-system`: checks SI conditions and spawns planetesimal entities.


### Planet growth and atmosphere origin

Low-mass planets’ atmospheres are tightly coupled to stellar irradiation and evolution:

- Sub-Neptune atmospheres can lose large fractions of their primordial hydrogen envelopes via photoevaporation, with outcomes strongly dependent on host star mass and activity history.[^8_14]
- Super-Earth atmospheres are “hard to make”: maintaining thick envelopes requires specific combinations of mass, composition, and irradiation. Many lose their envelopes and end up rocky.[^8_15]

Your existing `domain.chemistry` already sketches atmospheric retention via escape velocity vs thermal velocity, which matches basic Jeans escape criteria. You can expand this by:[^8_2]

- Giving each forming planet an initial envelope mass fraction and composition from the disk annulus where it forms.
- Evolving that envelope via energy-limited escape tied to stellar XUV luminosity and wind parameters, using simple hydrodynamic escape prescriptions.[^8_16][^8_14][^8_15]

This connects:

- Star evolution → stellar activity and high-energy output → atmospheric mass loss.
- Disk composition and condensation sequence → initial solid and volatile inventory.

***

## Planet and moon atmospheres in detail

### Layered atmospheres

Planetary atmospheres can also be modeled as layered shells:

- Troposphere, stratosphere, thermosphere, exosphere for terrestrial planets; deep convective envelopes for gas giants.[^8_16][^8_2]
- Each layer has its own temperature profile, composition, and dominant processes (convection, radiative transfer, chemistry, escape).

You don’t need full GCMs, but you can:

- Represent atmospheres as a handful of layers per body with T–P–composition profiles, driven by equilibrium with stellar irradiance and internal heat flux.[^8_14][^8_16]
- Use simple vertical mixing and chemical network approximations to track key species (H₂O, CO₂, CH₄, N₂, O₂) that matter for habitability and visuals.[^8_17][^8_16]

In ECS:

- `c/atmosphere-profile`: per planet, a layered profile (T, P, composition vs altitude).
- `atmosphere-evolution-system`: updates profiles based on stellar forcing, interior heat, and escape.

These profiles feed:

- Visual rendering (cloud decks, haze, scattering color).
- Habitability scoring (already partially present in `habitability-score`).[^8_17][^8_16]

***

## Asteroids, moons, and voxels

You’re right that asteroids, rubble piles, and heavily cratered moons are fundamentally non‑uniform. Smooth spheres don’t cut it. Observations and 3D models of small bodies show highly irregular shapes and heterogeneous interiors (metallic cores, porous mantle, regolith).[^8_16][^8_2]

Design:

- Treat large, roughly spherical bodies (planets, big moons) as layered spheres with radial structure.
- Treat small bodies (asteroids, comets, rubble-pile moons) as voxel fields: 3D grids or sparse voxel structures storing density, composition, and temperature.

In ECS:

- `c/voxel-field`: attached to bodies below a radius threshold, holding a reference to a voxel volume.
- `voxel-physics-system`: handles collision, fracture, and thermal processing at voxel resolution, but *only* for bodies near the player or involved in events.

You can still keep a coarse “shape shell” (radius, bulk density) at the entity level, using voxels for local detail (e.g., when the player zooms in or interacts with specific regions).

***

## Observer-centric fidelity and event/statistics mode

### Event ledger and statistical modeling

You already have a ledger and threshold-event concept in `phase0` for ignition, planet formation, and phase transitions. The extension you’re describing is:

- While the high-fidelity simulation runs, log all astrophysically meaningful events: stellar flares, wind episodes, planetesimal formation, giant impacts, atmosphere loss bursts, climate transitions, etc.[^8_14][^8_16][^8_2]
- Periodically fit statistical models (hazard rates, distributions over energy, duration, affected distances) to these events, conditioned on coarse state variables (star type, planet type, disk state).

Then you can:

- **Turn off** detailed simulation in far regions or for time intervals where the player is not observing, and *sample* events from the learned distributions.
- Apply those sampled events to the game state as impulses: a flare event raising the radiation level, a distant impact adding ejecta to a disk cell, a climate transition on a remote planet.

This matches how large-scale population studies model exoplanet atmospheres and habitability: they use stellar evolution tracks and atmosphere evolution frameworks plus statistical distributions over formation histories to predict broad outcomes without simulating each system in detail.[^8_15][^8_14][^8_16]

### Level-of-detail rules

Tie fidelity explicitly to the observer and influence radius:

- High-fidelity mode: for objects within a certain physical or narrative distance of the player, run full ECS physics (gravity, nebula, disk, atmospheres, EM, voxel collisions).
- Statistical mode: for everything else, keep coarse “macrostate” variables (star type, planet type, atmosphere mass, disk mass) and let the event sampler evolve them.

When the player moves into a region or “zooms in” on an object, you:

- Reconstruct fine-grained state consistent with the macrostate and event history: create disk cells, atmospheres, voxels, etc., from templates drawn from your logged distributions.

This is exactly the observer-centric principle you stated: *we can* model everything as accurately as scientifically possible, but we only pay that cost where it matters to what the player experiences.

***

## How this plugs into Truth’s current ECS

Given your existing architecture:

- `domain.stellar` already tracks matter-state, collapse, fusion, luminosity, and some complexity scoring. You extend it with interior and atmosphere shell components plus wind profiles.
- `domain.em` gives you magnetic fields, Lorentz forces, and flux freezing; you add ionization fraction and couple wind/escape to EM where needed.[^8_8]
- `domain.chemistry` already models composition, phase, atmosphere retention, and habitability. You enrich the condensation sequence and atmosphere layering.[^8_17][^8_16]
- `domain.phase0` has a tick driver, system summary, and event emission framework; you add the statistics mode and LO D switch there.

If you’d like, the next step can be a concrete ECS design document: component/schema lists for stars, disks, planets, atmospheres, voxels, and event/statistics systems, aligned with the above physics so you can implement in phases without losing the big picture.
<span style="display:none">[^8_18][^8_19][^8_20][^8_21][^8_22][^8_23]</span>

<div align="center">⁂</div>

[^8_1]: https://www.uu.se/en/department/physics-and-astronomy/research/astronomy-and-space-physics/stellar-physics/models-of-stellar-atmospheres

[^8_2]: https://www.britannica.com/science/star-astronomy/Stellar-structure

[^8_3]: https://en.wikipedia.org/wiki/Stellar_evolution

[^8_4]: https://en.wikipedia.org/wiki/Stellar_atmosphere

[^8_5]: https://science.nasa.gov/universe/stars/types/

[^8_6]: http://arxiv.org/pdf/2409.03329.pdf

[^8_7]: https://www.cambridge.org/core/books/abs/from-luminous-hot-stars-to-starburst-galaxies/stellar-atmospheres/D89196811DE39B42F35107D4D0977335

[^8_8]: https://www.usm.lmu.de/~puls/stellar_at/stellaratmwinds_21_part2.pdf

[^8_9]: https://iopscience.iop.org/article/10.3847/1538-4357/abadf9

[^8_10]: http://link.springer.com/10.1007/978-3-030-11452-7_2

[^8_11]: https://ntrs.nasa.gov/citations/20230002054

[^8_12]: https://en.wikipedia.org/wiki/Streaming_instability

[^8_13]: https://academic.oup.com/mnras/article/498/1/1239/5885349

[^8_14]: https://academic.oup.com/mnras/article/504/2/2034/6195519

[^8_15]: https://www.nature.com/articles/s41550-020-01209-8

[^8_16]: https://link.springer.com/10.1007/978-3-319-09749-7

[^8_17]: https://urfjournals.org/open-access/investigating-the-interplay-of-stellar-evolution-and-exoplanet-habitability-through-spectroscopy.pdf

[^8_18]: https://iopscience.iop.org/article/10.1088/1538-3873/ae3f57

[^8_19]: https://link.springer.com/10.1134/S1990341322010047

[^8_20]: https://arxiv.org/html/2410.10968v1

[^8_21]: http://arxiv.org/pdf/2111.10381.pdf

[^8_22]: http://www.arxiv.org/pdf/2502.02124.pdf

[^8_23]: https://academic.oup.com/mnras/article-pdf/477/4/5011/24853341/sty854.pdf


