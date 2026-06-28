# Simulation Methods Research: How Real Science Models Each Phase

**Status:** living research document. Maps published simulation methods to each
phase of Gates of Truth. Feeds technical specs for each phase.
**Date:** 2026-06-27
**Companion docs:** `gates-of-truth-world-gen-phases.md` (the arc),
`truth-phase-0-stellar-nebula-design.md` (Phase 0 experience),
`phase0-coupled-physics-and-regime-classifier.md` (Phase 0 physics),
`phase0-sink-particle-formation.md` (the current epic).

---

## Purpose

Every phase of Gates of Truth corresponds to a real branch of computational
astrophysics, geophysics, or astrobiology. This document surveys the published
methods — codes, algorithms, scale, and what they actually compute — so that
each game phase is grounded in how the science works, not in how games typically
approximate it.

The mandate: **the physics is real until the Gates open.** That means each
phase's simulation must be recognizably descended from a published method, even
when our resolution is toy-scale.

---

## Phase 0: Stellar Nebula → Solar System

### What real simulations compute

The formation of a solar system from a molecular cloud is modeled by **Smoothed
Particle Hydrodynamics (SPH)** codes with self-gravity, sink particles, and
radiative transfer. The canonical lineage:

| Paper | Code | What it showed |
|---|---|---|
| Bate, Bonnell & Price (1995) | SPH + sink particles | Invented sink particles to follow fragmentation past protostar formation |
| Bate & Burkert (1997) | SPH | Resolution requirement: Jeans mass must be resolved by ~2N_neigh particles |
| Bate (1998) | SPH | First 3D collapse to stellar densities (17 orders of magnitude in density) |
| Bate (2012) | SPH + radiation hydro | Star cluster formation; IMF emerges naturally from fragmentation + accretion |
| Federrath et al. (2010) | FLASH (AMR) + sinks | Sink formation criteria: density + bound + Jeans + converging + not-in-existing-sink |
| Hubber et al. (2013) | SPH | Improved sink algorithm: no spurious creation, regulated accretion, angular momentum transfer |

**Key finding:** The IMF (initial mass function — how many stars of what mass
form) is an **emergent** property of the fragmentation + accretion process, not a
prescribed input. This is the research justification for our "few sinks, each
distinct" design target.

### What maps to our game

| Research concept | Game implementation | Status |
|---|---|---|
| SPH gas parcels | `:nebula` entities with adaptive-h density | ✅ built |
| Sink particles | Stage 2 of the sink-particle epic | 🚧 in progress |
| Jeans criterion | `domain.stellar/jeans-unstable?` | ✅ built |
| Density-gated condensation | `stellar/core-condensation-density` | ✅ built |
| Regime classifier | `domain.regime` (β, M_A, Mach, Jeans) | ✅ built |
| MHD (magnetic fields) | `domain.em` (ideal MHD-lite) | ✅ built |
| Radiative transfer | Coarse local cooling; full RT deferred | ⚠️ partial |
| Disk formation (Toomre Q) | Toomre Q + Gammie cooling criterion | 📋 specced |
| Sink accretion (Bondi/Hill) | Stage 3 of the sink-particle epic | 📋 designed |

### The sink particle criteria (the research consensus)

From Federrath et al. (2010), a gas parcel becomes a sink iff ALL of:

1. **Density above threshold** (ρ > ρ_crit, ~10⁻¹⁰ kg/m³ for us)
2. **Gravitationally bound** (total energy < 0)
3. **Jeans unstable** (enclosed mass > Jeans mass)
4. **Potential minimum** (local gravitational minimum)
5. **Converging flow** (velocity divergence < 0)
6. **Not within an existing sink's accretion radius**

This is the authentic formation gate. A density threshold alone creates spurious
sinks in shocks (Federrath showed this explicitly). The joint criteria are what
make "few" a structural property, not a tuning knob.

### The accretion mechanism

Once a sink exists, it accretes gas within its **accretion radius**:

- **Bondi radius:** r_Bondi = G·M / c_s² (where c_s is the sound speed)
- **Hill radius:** r_Hill = a · (M / 3M_☉)^(1/3) (where a is orbital distance)

The sink absorbs parcels within r_acc that are gravitationally bound to it. Mass,
momentum, and angular momentum are conserved on absorption. The accretion radius
grows with mass — the central sink runs away to a star; disk sinks grow into
planets.

### The accretion-vs-collapse race

The key risk: if gas condenses (via Jeans criterion) faster than sinks accrete
it, the cloud fragments into a swarm instead of one star. Mitigations from the
literature:

- **Slow collapse** (our Stage 1: virial ~0.5, rotation visible) — gas persists
  long enough for accretion to compete
- **High formation bar** (the full Federrath criteria) — only genuine collapse
  passes
- **Throttled creation** — at most 1 new sink per N ticks (we may need this)
- **Convert-and-seed** — when a sink forms, immediately absorb nearby parcels

---

## Phase 1: Planetary Settlement (Geophysics)

### What real simulations compute

Once a planet exists, the relevant physics is **geophysical**: mantle convection,
core dynamo, plate tectonics, atmosphere structure, and thermal evolution.

| Domain | Method | Key parameter |
|---|---|---|
| Mantle convection | Finite-element / spectral methods on spherical shells | Rayleigh number Ra = ρgαΔTd³/(μκ) |
| Core dynamo | MHD in rotating spherical shells | Magnetic Reynolds number Rm = vL/η |
| Plate tectonics | Rheological models with viscoelastic lithosphere | Yield stress, viscosity contrast |
| Atmosphere | Radiative-convective equilibrium + circulation | Optical depth, greenhouse factor |
| Thermal evolution | Energy balance: radiogenic heating vs radiative cooling | U/Th/K abundance, surface area |

**Key finding:** The Rayleigh number determines whether a planet is tectonically
alive. Below Ra_crit (~10³), conduction dominates and the planet is dead (Mars).
Above it, convection drives volcanism, plate tectonics, and magnetic field
generation. This is the single number that determines whether a world has
geological drama.

### What maps to our game

| Research concept | Game implementation | Status |
|---|---|---|
| Rayleigh number | `domain.regime/rayleigh` | 📋 specced |
| Core dynamo | `domain.interior` (induction + rotation) | 📋 specced |
| Magnetic field (planetary) | Same induction equation as nebula MHD | 📋 specced |
| Atmosphere structure | `domain.atmosphere` (hydrostatic + wind) | 📋 specced |
| Thermal evolution | `domain.thermo` energy equation with radiogenic | 📋 specced |
| Plate tectonics | Regime-gated by Ra; viscoelastic rheology | 🔮 deferred |
| Volcanism | Convective vigor → eruption rate | 🔮 deferred |

### The regime classifier for planets

The same `domain.regime/classify` function that tags nebula cells now tags
planetary cells:

| Tag | Condition | Meaning |
|---|---|---|
| `:tectonically-dead` | Ra < Ra_crit | No volcanism, no plate tectonics, dead magnetic field |
| `:convective` | Ra > Ra_crit | Active mantle, volcanism possible |
| `:strong-dynamo` | Convective power high + rotation fast | Sustained dipole field (Earth-like) |
| `:weak-dynamo` | Convective power low or rotation slow | Decaying field (Mars/Venus-like) |

### What this means for play

A planet with Ra > Ra_crit and a strong dynamo is a **geologically dramatic**
world: volcanism, plate tectonics, a magnetic umbrella that shields the
atmosphere from stellar wind. A planet below Ra_crit is a dead rock — beautiful,
but sterile. The player's Phase 1 experience is about guiding which worlds stay
alive.

---

## Phase 2: Emergence of Life (Astrobiology)

### What real simulations compute

The origin of life is the least computationally modeled phase. Real research is
divided between:

| Approach | Method | What it computes |
|---|---|---|
| Prebiotic chemistry | Molecular dynamics + reaction networks | Amino acid formation, RNA world, metabolic pathways |
| Habitable zone modeling | Climate models with stellar evolution | Liquid water stability over Gyr |
| Biosignature detection | Atmospheric spectroscopy models | O₂, CH₄, O₃ as life indicators |
| Evolutionary dynamics | Population genetics + ecological models | Selection, drift, adaptation, extinction |

**Key finding:** There is no published simulation that computes abiogenesis from
first principles. The state of the art is **conditions modeling** — determining
whether a world has the right temperature, chemistry, energy sources, and
stability for life to emerge — rather than computing the emergence itself.

### What maps to our game

| Research concept | Game implementation | Notes |
|---|---|---|
| Habitable zone | Stellar luminosity + orbital distance + atmosphere | Already implied by Phase 0 |
| Liquid water stability | Temperature + pressure + composition | Sub-grid on disk/planet surface |
| Prebiotic chemistry | Not computable from first principles at our scale | Must be a **prescriptive model**, not emergent |
| Evolution | Agent-based population models with selection | Phase 3 territory |

### The honest resolution limit

Life's origin cannot be simulated from physics alone — it requires chemistry at
scales 10+ orders of magnitude below our parcel resolution (same problem as
planetesimals). The authentic approach is the same as planets: a **sub-grid
prescription** on the planet's surface conditions. The game doesn't simulate
abiogenesis; it computes *whether the conditions are right* and then prescribes
the outcome.

This is not a failure — it is how real astrobiology works. The question is not
"how does life emerge?" but "given these conditions, is life plausible?" The
game's Phase 2 is a conditions model, not a chemistry simulation.

---

## Phase 3: Sentience and Proto-Culture

### What real simulations compute

The evolution of intelligence and culture is modeled by:

| Approach | Method | What it computes |
|---|---|---|
| Population genetics | Wright-Fisher, coalescent theory | Allele frequency, adaptation, speciation |
| Ecological models | Lotka-Volterra, food webs, niche construction | Predator-prey, competition, symbiosis |
| Cultural evolution | Agent-based models with transmission | Innovation diffusion, norm formation, group selection |
| Cognitive evolution | Neural network models of social cognition | Theory of mind, language evolution, cooperation |

**Key finding:** Cultural evolution is **agent-based** — it emerges from
individuals making decisions under constraints. The state of the art is
agent-based models (ABMs) where each agent has a genome, a social network, and a
decision function. Culture emerges from transmission, mutation, and selection of
ideas — the same Darwinian logic as biological evolution, applied to information.

### What maps to our game

| Research concept | Game implementation | Notes |
|---|---|---|
| Population genetics | Agent-based entities with genomes | ECS entities with genetic components |
| Ecological models | Food web / niche construction on the planet surface | Regime-gated by biome cells |
| Cultural evolution | Agent-based with idea transmission | The "proto-culture" phase |
| Cognitive evolution | Threshold-based (brain complexity → symbolic behavior) | The sentience transition |

### The narrowing begins here

Phase 3 is where the player's influence starts to narrow. In Phase 0, the player
shaped orbital mechanics. In Phase 1, geology. In Phase 2, ecology. In Phase 3,
the player starts to interact with *agents* — individual creatures making
decisions. The player can bias outcomes (migration pressure, resource scarcity,
environmental stress) but cannot command individual behavior.

This is the phase where "character creation" starts to feel personal. The player
is no longer shaping a world — they are watching *people* emerge from it.

---

## Phase 4: Civilizational Narrowing

### What real simulations compute

Civilization is modeled by:

| Approach | Method | What it computes |
|---|---|---|
| Agent-based social models | Sugarscape, Epstein & Axtell | Trade, conflict, wealth distribution, norms |
| Institutional economics | Game theory + network models | Cooperation, defection, governance |
| Historical dynamics | Cliodynamics (Turchin) | Rise and fall of empires, secular cycles |
| Technological evolution | Innovation diffusion models | Discovery rate, adoption curves, paradigm shifts |

**Key finding:** Civilization is the most computationally expensive phase to
simulate honestly because it involves **strategic agents** — beings that model
each other's intentions. The state of the art is agent-based models with
bounded rationality, social networks, and cultural transmission. Each agent is a
small AI with goals, beliefs, and decision rules.

### What maps to our game

| Research concept | Game implementation | Notes |
|---|---|---|
| Agent-based social models | Individual NPCs with goals, beliefs, relationships | ECS entities with social components |
| Institutional economics | Group-level entities (tribes, cities, states) | Hierarchical ECS: groups contain agents |
| Historical dynamics | Emergent from agent interactions + environmental pressure | Not scripted — computed |
| Technological evolution | Discovery as a function of population, diversity, resources | The "tech tree" is emergent, not prescribed |

### The avatar pool forms here

Phase 4 is where the player's "character" candidates emerge from the social
graph. The player has been shaping the world for billions of years. Now the
world produces a cast of people — scholars, rulers, explorers, mystics — whose
lives are shaped by everything the player has done. The character creation screen
is the entire game up to this point.

---

## Phase 5: Pre-Gate Avatar Convergence

### What real simulations compute

This phase has no direct research analog — it is a game design construct that
bridges simulation and embodied play. The closest research is:

| Approach | Method | What it computes |
|---|---|---|
| Social network analysis | Graph models of influence, trust, information flow | Who knows whom, who believes what |
| Narrative generation | Story graphs, character arcs, dramatic structure | Emergent plot from agent interactions |
| Decision theory | Bounded rationality, prospect theory | How agents make choices under uncertainty |

### What maps to our game

The player narrows from observer to inhabitant. They select (or are selected
by) one of the avatar candidates that emerged from Phase 4. This is the moment
the "character creation screen" resolves into a single character.

---

## Phase 6: Gate Discovery

### What real simulations compute

The Gates of Truth are the one fictional element. No real physics computes
multiverse travel. But the *conditions* for Gate discovery — technological
sophistication, energy generation, theoretical understanding — are computable.

The Gates connect places where "the world took slightly different paths, coming
from otherwise similar series of events from the beginning of the universe."
This is a **quantum branching** metaphor: two worlds whose wavefunctions
collapsed from related quantum priors. The Gate exists because the two places
share a common origin — the same initial conditions, slightly different
outcomes.

### The fiction

The Gate is the game's one break from physics. Everything before it is real.
The Gate earns its fictional status by being the *only* thing that isn't
computed from the laws we've established. This is why the physics matters — it
gives the Gate something to break *from*.

---

## Cross-Cutting Research Themes

### 1. The single ECS substrate

Every phase uses the same ECS world model. The research justification: real
simulations use the same governing equations across scales — the same MHD
equations describe a molecular cloud and a planetary core, the same gravity
equations describe orbital mechanics and mantle convection. What changes is the
*regime*, not the *laws*.

Our regime classifier (`domain.regime`) is the game's version of this: one set
of equations, many regimes, selective integration.

### 2. The resolution limit

Every phase has an honest resolution limit below which physics cannot be
computed. The research approach is always the same: **sub-grid prescriptions**
for what can't be resolved, grounded in published methods.

| Phase | What's resolved | What's sub-grid |
|---|---|---|
| 0 | Gas collapse, star formation | Planetesimals, planets |
| 1 | Mantle convection, core dynamo | Plate tectonics details |
| 2 | Habitable conditions | Prebiotic chemistry |
| 3 | Population dynamics | Individual neural circuits |
| 4 | Social networks, institutions | Individual strategic reasoning |

### 3. The player's narrowing role

The research maps directly to the player's shrinking influence:

| Phase | Player scale | Research analog |
|---|---|---|
| 0 | God-like (orbital mechanics) | Initial conditions in SPH simulations |
| 1 | Gardener (geology) | Boundary conditions in geophysical models |
| 2 | Encourager (ecology) | Environmental parameters in astrobiology |
| 3 | Patron (proto-culture) | Selection pressures in evolutionary models |
| 4 | Historian (civilization) | Initial conditions in agent-based models |
| 5 | Selector (avatar) | No research analog — game design |
| 6 | Person (embodied) | No research analog — game design |

### 4. Time follows complexity

The research justifies the elastic time model. In real simulations, timestep is
determined by the fastest process: during gas collapse, timesteps are fractions
of a second; during orbital evolution, years; during geological evolution,
millennia; during cultural evolution, generations. The game's time compression
is not a convenience feature — it is the simulation's natural timescale.

---

## Key Citations

### Star formation (Phase 0)
- Bate, Bonnell & Price (1995) MNRAS 277, 362 — sink particle invention
- Bate & Burkert (1997) MNRAS 288, 1060 — Jeans resolution requirement
- Bate (1998) ApJ 508, L95 — first 3D collapse to stellar densities
- Bate (2012) MNRAS 425, 345 — radiation-hydrodynamical star cluster formation
- Federrath et al. (2010) ApJ 713, 269 — sink formation criteria (density + bound + Jeans + converging + not-in-sink)
- Hubber et al. (2013) MNRAS 430, 3261 — improved sink algorithm
- Jones & Bate (2018) MNRAS 480, 2562 — sink radiative feedback

### Planet formation (Phase 0 → 1)
- Youdin & Goodman (2005) ApJ 620, 459 — streaming instability
- Johansen et al. (2007) Nature 448, 1022 — streaming instability → planetesimals
- Johansen et al. (2015) — streaming instability with self-gravity
- Lim et al. (2024) ApJ — streaming instability + turbulence thresholds
- Kenyon & Bromley (2006) AJ 131, 2737 — hybrid N-body-coagulation code
- Grimm & Stadel (2014) ApJ 796, 23 — GENGA GPU N-body code
- Lorek (2026) A&A — N-body planet formation with pebble accretion

### Geophysics (Phase 1)
- Bercovici & Ricard (2014) — mantle convection with plate tectonics
- Christensen & Aubert (2006) — parameterized core dynamo models
- Tackley (2000) — mantle convection codes (Citcom, StagYY)

### Astrobiology (Phase 2)
- Kasting et al. (1993) — habitable zone definition
- Wordsworth & Pierrehumbert (2013) — habitable zone with CO₂-N₂ atmospheres
- Schwieterman et al. (2018) — biosignature review

### Cultural evolution (Phase 3–4)
- Boyd & Richerson (1985) — culture and the evolutionary process
- Turchin (2009) — cliodynamics: mathematical modeling of historical dynamics
- Epstein & Axtell (1996) — growing artificial societies (Sugarscape)

---

## Open Research Questions

1. **Phase 0 → 1 handoff:** How do we transition from SPH gas parcels to
   resolved planetary bodies? The research uses a "snapshot and restart" approach
   — freeze the disk state, extract planet initial conditions, run a separate
   geophysical code. We need an ECS-native version of this.

2. **Phase 2 conditions model:** What is the minimal set of planetary properties
   (temperature, atmosphere composition, energy sources, stability duration)
   needed to compute "life is plausible" without simulating chemistry?

3. **Phase 3 agent model:** How complex must individual agents be to produce
   emergent culture? The research ranges from simple (Sugarscape: ~10 rules per
   agent) to complex (cognitive architectures: thousands of rules). What is the
   minimum viable agent for Gates of Truth?

4. **Phase 4 civilization model:** How do we compute institutional evolution
   (tribes → cities → states → empires) from agent interactions without
   prescribing the outcome?

5. **The Gate fiction:** What exactly is the "quantum prior" that connects two
   places in the multiverse? This needs a consistent internal logic that doesn't
   require real quantum mechanics — just enough rigor that the player accepts it
   as the one break from physics.

---

## Next Steps

1. **Phase 0 sink particle epic** (in progress) — the current implementation work
2. **Phase 0 → 1 handoff spec** — how disk state becomes planetary initial conditions
3. **Phase 1 geophysical model spec** — Rayleigh number, core dynamo, atmosphere
4. **Phase 2 conditions model spec** — habitability without chemistry simulation
5. **Phase 3 agent architecture spec** — minimum viable agent for emergent culture
6. **Phase 4 civilization model spec** — institutional evolution from agents
