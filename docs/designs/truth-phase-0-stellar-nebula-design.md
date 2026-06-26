# Gates of Truth — Phase 0 Stellar Nebula Slice

This document commits the implementation design for the first playable slice of **Gates of Truth**: the stellar nebula phase in which the player witnesses a star system form in front of them and learns, mostly without explicit tutorialization, what kind of being they are in this universe. The current repository already defines the four-quadrant architecture (`domain/`, `infra/`, `shape/`, `law/`), the requirement that domain logic stay pure, and the expectation that new systems be specified with law, tests, and then implementation.[cite:6]

## Purpose

Phase 0 must achieve five things at once:

1. Show a universe-forming process directly and convincingly.
2. Establish the underlying physics substrate that later phases continue rather than replace.
3. Introduce the player’s mode of agency as limited, subtle, and awe-oriented.
4. Teach the control language of the game without breaking immersion.
5. Produce a star system outcome that can continue toward habitability or gracefully fail into a witnessed conclusion.

The design target is not a menu-driven setup flow. It is an observable cosmological event sequence that the player can mostly watch, gently influence, and emotionally inhabit.

## Slice Definition

### Entry condition

The slice begins with a nebular mass distribution already present in simulation space. The player appears as a coherent spark-like observer embedded in that field.

### Exit condition

The slice ends when one of the following becomes true:
- A stable star forms and the surrounding system resolves into candidate planets and non-planets under the astronomical definition of a planet.
- The nebula fails to produce a viable life-bearing trajectory and the simulation transitions into a witnessed conclusion.
- The player’s attention/coherence can no longer sustain meaningful focus on the region, and the spark releases into another nebular site.

### Planet criterion

A body counts as a planet in this phase only when it satisfies the design’s intended astronomical meaning:
- It is in hydrostatic equilibrium.
- It has cleared its orbital neighborhood.

The player does not need this definition presented as UI text. The system uses it internally to determine which bodies survive phase transition.

## Experience Goals

### 1. A universe forms visibly

The player must be able to watch matter gather, heat, compress, ignite, flatten into a disk, collide, differentiate, and clear into orbital structure. The sequence should feel like a real process, not a scripted montage.

### 2. Awe through partial power

The player can influence outcomes, but never command them outright. Attention matters, timing matters, focus matters, but physics remains sovereign.

### 3. Soft boundaries

The game should communicate, through feel rather than explicit failure text, when a created system is no longer likely to lead to life. The player senses a narrowing of future possibility before the system is formally concluded.

### 4. One control language across scales

Camera drift, focus, interaction, and release are established here in a cosmological context and later persist at planetary, ecological, civilizational, and embodied scales.

## Design Thesis

Phase 0 is not a disposable prologue. It is the first expression of the universal simulation substrate.

The same world model introduced here must remain conceptually continuous with later systems:
- Particle and voxel matter becomes geology, hydrology, and material interaction later.
- Thermal gradients become climate and habitability constraints later.
- Chemistry becomes mineralogy, atmosphere, water, and eventually metabolism later.
- The player’s focus mechanic becomes ecological attention, cultural narrowing, and finally embodied perception later.

The player should come away feeling that they did not switch games. The universe merely cooled, slowed, and became more articulate.

## Simulation Model

## Matter Representation

Phase 0 uses a dual representation:
- **Statistical field** for unfocused or distant regions.
- **Voxel-particle resolved mass** for focused regions where local physics matters.

This maintains continuity with the later simulation architecture while making large-scale cosmology tractable.

### Representation rule

Anything outside the player’s focus volume is modeled statistically. Anything inside it can be promoted into discrete resolved matter suitable for local interaction and visualization.

### Consequence

The player’s attention is not just a camera feature. It is a computational and ontological act. Focus causes matter and events to become more detailed, more expensive, and more knowable.

## Core Physical Domains for this slice

### Gravity

Gravity drives collapse, orbital capture, accretion, and clearing. It must be present from the first frame of the slice.

Implementation intent:
- Use approximate N-body / field hybrid behavior for distant masses.
- Use more local direct interactions inside focused regions.
- Keep the API aligned with later use in orbital and geological systems.

### Temperature

Temperature emerges from compression, collision, radiative input, and cooling. This is the primary bridge between collapse and ignition, and later between sunlight and habitability.

Implementation intent:
- Track temperature as a first-class scalar on resolved matter regions.
- Support heating through compression and collisions.
- Support cooling through radiation and expansion.

### Pressure

Pressure determines whether matter remains diffuse, collapses further, or reaches the thresholds needed for ignition and hydrostatic equilibrium.

Implementation intent:
- Derive pressure from density and temperature in the minimal slice.
- Use hydrostatic equilibrium tests as part of planet classification.

### Chemistry

Chemistry determines what matter can later become: rocky bodies, volatile-rich bodies, atmospheres, oceans, and prebiotic environments.

Implementation intent:
- Start with a minimal elemental inventory sufficient for cosmological and planetary differentiation: hydrogen, helium, oxygen, carbon, silicon, iron, and a coarse “heavy elements” bucket if needed.
- Track composition statistically for distant matter and explicitly for focused matter.

### Fusion and light

Fusion is the threshold event that turns a collapsing protostar into a star and changes the meaning of every later physical process.

Implementation intent:
- Treat ignition as a consequence of temperature, pressure, and composition crossing threshold conditions.
- Once fusion begins, emit light and radiative energy as simulation-visible outputs.
- Light must not be merely decorative; it must become a real driver of later thermal differentiation.

## Minimal World State for the Slice

The slice needs a pure simulation state that can later evolve rather than be discarded. Conceptually it should include:

- Nebular regions.
- Resolved matter clusters.
- Proto-stars and stars.
- Accretion disk structures.
- Orbiting bodies.
- Player spark state.
- Focus volume.
- Coherence/attention resource state.
- Slice phase markers that are internal to the simulation, not tutorial labels shown to the player.

This should become the basis for the law schema and test fixtures, consistent with the repository’s design rule that new systems begin with schema and tests before implementation.[cite:6]

## Player Form and Resource

## Player ontology

In this slice, the player is a coherent luminous fluctuation: a spark, sprite, or quantum oscillation in the vacuum. They are not yet a person and not quite a god.

This means their influence should feel like:
- Bias rather than command.
- Attention rather than tool use.
- Presence rather than ownership.

## Resource concept

The best current resource frame is **coherence**, expressed experientially as attention sustained against vacuum noise.

Why this works:
- It fits the cosmological fiction.
- It explains why focused observation has cost.
- It supports gentle failure states through decoherence rather than hard death.
- It scales conceptually into later forms of agency.

### Coherence behaviors

- Focusing on a region consumes coherence.
- Witnessing threshold events can restore coherence.
- Remaining in low-novelty or dying systems drains coherence over time.
- If coherence is exhausted and no regeneration route exists, the player loses the ability to sustain meaningful influence and becomes primarily observer.

## Control Model

The control language introduced here must survive into later phases with evolving meaning.

| Control | Phase 0 expression | Later continuity |
|---|---|---|
| Drift | Move the spark through nebular space | Move over a planet, through a region, or as an embodied character |
| Focus | Collapse a region into detail and attention | Inspect terrain, ecology, people, or objects |
| Influence | Bias local physical conditions within narrow constraints | Bias ecology, culture, or act as a person |
| Release | Pull back from a region and let it return to statistical distance | Exit close observation, de-escalate control, or widen scope |

The player should feel that these controls are simply becoming more intimate and more local as the universe cools.

## Visibility and Focus

Focus is the heart of both presentation and computation.

### Focus volume

The player always has a current region of strongest observational presence. This focus volume:
- Promotes matter into higher-resolution simulation.
- Enables direct local influence.
- Determines where the richest visuals and audio occur.
- Shapes what the player learns implicitly.

### Design consequence

The player never sees everything equally. This preserves awe, keeps distant regions statistical, and gives attention mechanical meaning.

## Emergent Event Sequence

The slice should reliably create a readable sequence of emergent events, even if their exact timing and outcomes vary:

1. Diffuse nebular drift.
2. Local gravitational concentration.
3. Compression heating.
4. Protostellar collapse.
5. Disk formation.
6. Ignition threshold crossing.
7. Accretion and collision among orbiting bodies.
8. Differentiation into planets, dwarf-like remnants, belts, and debris.
9. Orbital clearing and stable system settlement.
10. Habitability potential assessment for onward simulation.

These are simulation realities, not chapter cards.

## Soft Transition to Ecology

Phase 0 must already contain the seeds of ecology without skipping prematurely to life.

The slice therefore needs to produce, for each surviving planet-scale body, at least a first-pass answer to the following questions:
- Is the body rocky, icy, gaseous, or mixed?
- Does it fall into a plausible thermal band for later habitability?
- Does it retain an atmosphere, and of what rough class?
- Is liquid solvent stability plausible under future cooling conditions?
- Are the bulk chemistry and orbital circumstances compatible with later prebiotic complexity?

The point is not to generate life here. The point is to hand later slices a physically grounded starting point from which life can plausibly emerge.

## Habitability and Soft Boundaries

The player must sense when a system is narrowing toward sterile beauty rather than future life.

This should be communicated through:
- The evolving color and texture of worlds.
- The kinds of threshold events that do or do not occur.
- The recovery rate of coherence.
- The narrator’s tone, if perceived.
- The degree to which the simulation keeps presenting branching potential.

Hard fail text should be avoided. Instead, possibility thins out.

### Examples of soft-boundary signals

- A protostar ignites but the habitable zone contains no durable rocky worlds.
- Accretion yields only unstable orbital dynamics and repeated sterilizing collisions.
- Chemistry favors worlds too volatile, too dry, too massive, or too cold for later complexity.
- The player’s interventions stop creating new meaningful divergences.

These do not mean the slice is worthless. They mean the player is approaching a witnessed conclusion rather than a life-bearing continuation.

## Failure, Drift, and Multi-Nebula Flow

A failed nebula must still be beautiful to watch.

When no viable path remains:
1. Coherence regeneration diminishes.
2. Interaction opportunities become sparse.
3. The player spends more time watching than shaping.
4. Time subtly accelerates.
5. The system resolves into its conclusion.
6. The spark drifts or is drawn to another active nebular site.

This makes repeated universe watching a natural part of the experience rather than a retry loop framed as punishment.

## Narrator / AI Presence

The player should be able to complete the slice without ever explicitly using a chat interface.

### Discovery approach

The AI storyteller should enter the experience in layers:
- First as a pattern in the audiovisual field.
- Then as occasional meaningful phrasing embedded into events.
- Only later as something recognizable as an addressable presence.

### Design rule

The AI exists whether or not the player talks back.

This preserves the feeling that the universe is accompanied, interpreted, and gently witnessed from within, while leaving room for players who never want explicit conversational interaction.

## Rendering and Feel

The player’s stated goal for this slice is to **see a universe form in front of their eyes**. That implies presentation constraints, even for a terminal-forward project.

The slice should prioritize:
- Readable large-scale matter motion.
- Visible heating and cooling changes.
- A clear visual event when fusion begins.
- Strong sense of scale without requiring labels.
- A luminous player-spark that remains legible but never dominates the scene.

Rendering remains an `infra/` responsibility in this repository, and any visualization should consume pure world-state rather than embed simulation logic directly.[cite:6]

## Namespace Plan

The current repository guidance implies the following initial slice boundaries:[cite:6]

### `law/`
- `law/stellar.clj` for world-state schemas and invariants.
- `law/chemistry.clj` if chemistry records are distinct enough to warrant dedicated contracts.

### `domain/`
- `domain/stellar.clj` for nebula collapse, gravity approximations, ignition thresholds, accretion flow.
- `domain/chemistry.clj` for composition blending, thermal class implications, and early material categorization.
- Potential shared interfaces via `defprotocol` if tick/update behavior needs explicit simulation roles.

### `infra/`
- `infra/render/stellar.clj` or equivalent for slice visualization, player spark rendering, and event presentation.
- Input mapping for drift, focus, influence, and release.
- Narrator presentation layer and discoverable chat shell.

### `shape/`
- Spatial helpers for focus volume, camera projection, and local field sampling if needed.

## First Implementation Slice

The first vertical slice should be as small as possible while still emotionally honest.

### Must-have behaviors

- A diffuse nebula visible on screen.
- A controllable spark-like player presence.
- Focus that changes simulation detail and visual emphasis.
- Gravitational collapse in at least one region.
- Compression heating.
- A threshold event for ignition.
- A visible accretion structure around the star.
- Formation of at least a few orbiting body classes.
- Coherence as a spend/recover resource.
- A graceful drift-away path if the system is non-viable.

### May-wait behaviors

- Full chemical realism.
- Precise multi-star dynamics.
- Exhaustive orbital clearing logic.
- Rich narrator interactivity.
- Final habitability scoring sophistication.

The first goal is not astrophysical completion. The first goal is a watchable, influenceable genesis event that proves the design language.

## Test Strategy

The repository’s guidance is explicit: new systems should be introduced as schema, then failing tests, then implementation.[cite:6]

Recommended early tests:
- Nebular collapse increases local density under gravity-like attraction.
- Compression raises temperature in collapsing focused regions.
- Ignition occurs only when threshold conditions are met.
- Accretion disk bodies can be classified into candidate planet / non-planet categories.
- Coherence decreases under sustained focus and increases at threshold events.
- A non-viable system eventually transitions into drift/conclusion state rather than stalling.

These tests are not just technical. They are epistemic commitments about what must be true for the slice to feel like the intended universe.

## Open Design Questions

These questions should be answered during implementation spikes, not before starting:

1. What is the minimal chemical model that still meaningfully drives later ecology?
2. How exact does hydrostatic equilibrium need to be in the first slice versus a proxy approximation?
3. How should coherence regeneration be tuned so the player feels influence without optimization pressure?
4. What is the smallest visual vocabulary that can make ignition and accretion feel awe-inspiring in the chosen renderer?
5. At what moment does the narrator first cross from ambience into legible address?

## Implementation Readiness

This slice is ready to move into the project’s normal build discipline:

1. Write `law/` schemas for nebula, star, body, player-spark, focus volume, and slice state.
2. Write failing tests for collapse, heating, ignition, and coherence transitions.
3. Implement the smallest pure `domain/stellar.clj` and `domain/chemistry.clj` needed to satisfy those tests.
4. Build a thin `infra/` renderer that makes the sequence legible and beautiful.
5. Iterate on feel without breaking the law/domain separation.

## Final framing

Phase 0 should make the player feel this:

A universe is not being selected.
A universe is becoming.
The player is present inside that becoming.
They may guide it, but they do not own it.
And whether the first world lives or fails, they are still there to witness the light.
