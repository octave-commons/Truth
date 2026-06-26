# Gates of Truth: World Genesis, Player Narrowing, and Evolution States

This document captures the current design direction for the opening arc of **Gates of Truth**: the phase that begins with a stellar nebula and ends when a civilization discovers the Gates of Truth. The repository already separates simulation concerns into `domain/`, `infra/`, `shape/`, and `law/`, and contains a `docs/notes` area suitable for design material.[cite:3][cite:4]

## Design Premise

The opening of the game is not a cinematic prologue layered on top of the game. It is the first playable system. The player begins at cosmological scale, with broad but indirect influence, and gradually narrows toward a person-sized perspective as observable complexity increases.

This means character creation is not a menu. It is a long-form act of guidance. The player shapes orbital conditions, biospheric tendencies, civilizational pressure, and early cultural divergence until the simulation has narrowed enough that a small set of plausible avatars exists.

The Gates of Truth mark the hard transition point. Before that moment, time is elastic and tied to observability and complexity. After that moment, time becomes synchronized and fixed because the world has become part of a larger network of reachable civilizations.

## Core Pillars

### 1. Time follows observability

Time speed is a function of what can currently be perceived and meaningfully differentiated from the player’s vantage point. When the world is mostly unresolved, statistical, or uniform, time accelerates. When life, culture, conflict, and personal decisions become dense and distinct, time slows.

This makes fast-forwarding an ontological property of the universe rather than a user convenience feature. A hot accretion disk can pass in moments. A political schism in an early city cannot.

### 2. The player narrows from force to person

The player starts with influence that feels almost divine, but not absolute. Their role is closer to guidance than command. They can bend outcomes, bias processes, and choose where to attend, but they cannot fully override the underlying physics.

As civilization becomes more complex, the player’s sphere of influence shrinks while the world’s internal agency increases. Eventually the player is no longer shaping a world in bulk; they are choosing among people who could plausibly become their in-world self.

### 3. Cosmology becomes culture

Astronomical structure is not decorative background. It is the source material from which calendars, omens, navigation, ritual, prophecy, agriculture, and cosmology emerge.

Moon phases, eclipse frequency, axial tilt, year length, visible planets, storm regimes, ocean tides, comet activity, and stellar color should all feed into myth and social structure. Different skies produce different clocks, and different clocks produce different stories.

### 4. Awe requires power with limits

The emotional target is awe, not power fantasy. The player should feel capable, but never sovereign. The universe must always remain larger than their reach.

The correct feeling is: “I can shape this world, and I am still overwhelmed by what it becomes.”

## Phase Model

## Phase 0: Nebula

**Player role:** Witnessing force, subtle shaper of initial conditions.

**Primary simulation focus:** Stellar collapse, accretion, orbital arrangement, stellar neighborhood, gas giant placement, early bombardment profile.

**Player influence:**
- Bias where matter concentrates.
- Favor stability, volatility, density, multiplicity, or asymmetry.
- Spend a limited formative resource to encourage particular large-scale outcomes.

**Player experience:**
- Very high time compression.
- Broad observational camera.
- Agent-assisted interpretation of emerging system structure.
- Early feelings of scale and irreversible consequence.

**Design goal:** Establish that the player is participating in the creation of a real physical system, not selecting from a list of prefabs.

## Phase 1: Planetary Settlement

**Player role:** World gardener at geological scale.

**Primary simulation focus:** Cooling, crust formation, atmosphere, oceans, tidal regimes, volcanism, impact aftermath, rotational and seasonal rhythms.

**Player influence:**
- Nudge atmospheric retention, hydrological balance, and climate stability.
- Favor certain continental distributions or biome precursors.
- Decide where attention is concentrated, causing more detail to collapse into view.

**Player experience:**
- Fast time, but slower than nebular scale.
- Repeated cinematic transitions: ocean birth, first stable climate bands, first enduring sky patterns.
- Growing recognition that astronomical choices have downstream ecological meaning.

**Design goal:** Move from “a solar system exists” to “this particular world has a personality.”

## Phase 2: Emergence of Life

**Player role:** Ecological encourager, not creator ex nihilo.

**Primary simulation focus:** Abiogenesis conditions, metabolic pathways, early selection pressure, marine-to-terrestrial transitions, extinction bottlenecks, ecological branching.

**Player influence:**
- Invest in resilience, diversity, specialization, or adaptability.
- Protect lineages indirectly by shaping environmental pressure rather than issuing direct commands.
- Guide which regions become cradles of long-term complexity.

**Player experience:**
- Time remains accelerated but begins pausing around branching events.
- Evolution is presented as a sequence of consequential thresholds rather than a blur of species names.
- The agent helps translate biology into future implications.

**Design goal:** The player begins to understand that life is not guaranteed, and that complexity is a rare narrowing path rather than an inevitable ladder.

## Phase 3: Sentience and Proto-Culture

**Player role:** Distant patron of possibility.

**Primary simulation focus:** Tool use, symbolic behavior, migration, kin structure, fire, language roots, oral tradition, celestial pattern recognition.

**Player influence:**
- Nudge migration corridors and resource pressures.
- Encourage curiosity, conservatism, ritualization, maritime adaptation, nomadism, or settlement.
- Seed initial lore anchors from corpus material or allow a blank world to elaborate procedurally.

**Player experience:**
- Time slows more noticeably.
- The sky becomes culturally legible: moons, stars, eclipses, and planetary motion begin to matter.
- Worlds start feeling familiar-but-alien: recognizably inhabited, but not Earth.

**Design goal:** Make it clear that culture is not authored arbitrarily; it is sculpted from the world’s actual recurring patterns.

## Phase 4: Civilizational Narrowing

**Player role:** Historical influence among rising societies.

**Primary simulation focus:** Agriculture, urbanization, trade, mythic systems, state formation, war, diplomacy, astronomy, priesthoods, navigation, writing, inheritance, technological divergence.

**Player influence:**
- Guide attention toward specific regions or peoples.
- Invest diminishing resources into turning points: schisms, expeditions, reforms, inventions, disasters survived.
- Influence which social clusters remain central to the player’s observational cone.

**Player experience:**
- Time is now mixed: long stretches pass quickly, but crises and discoveries unfold nearly in scene-time.
- The world begins to produce a cast of recurring bloodlines, cities, schools, and ideological factions.
- The player’s possible avatar pool begins to form.

**Design goal:** Transition from planetary authorship to human consequence without breaking continuity.

## Phase 5: Pre-Gate Avatar Convergence

**Player role:** Invisible selector among connected lives.

**Primary simulation focus:** A dense social graph near Gate-discovery conditions: scholars, explorers, rulers, engineers, mystics, laborers, families, rivals, apprentices.

**Player influence:**
- Narrow observation toward a small cluster of interconnected people.
- Resolve the last significant background conditions that determine who can become the player avatar.
- Potentially take temporary control of individuals once civilization is sophisticated enough, echoing the Gates of Aker mode.

**Player experience:**
- Time now mostly behaves like lived history.
- The player recognizes that the “character creator” has secretly been the entire game so far.
- A final set of plausible avatars emerges from the social and historical conditions already shaped.

**Design goal:** Make avatar selection feel earned, specific, and inseparable from world history.

## Phase 6: Gate Discovery and Time Lock

**Player role:** Player-character within the world.

**Primary simulation focus:** Discovery, construction, activation, and first implications of Gate-capable civilization.

**Player influence:**
- Direct personal agency replaces broad cosmological guidance.
- The prehistory of nudging ends; embodied action begins.

**Player experience:**
- A world-historic event freezes the elastic phase model.
- Time becomes fixed and synchronized because the civilization now participates in the network of other Gate-capable worlds.
- The MMO layer becomes available in-universe only when this threshold is crossed.

**Design goal:** Make the beginning of the "main game" feel like a legitimate ontological shift, not just a feature unlock.

## Cinematic Constraint Windows

The game needs moments where the player has little or no meaningful power. These are not failures of interactivity. They are how the simulation breathes.

These windows serve several purposes:
- They let the world reveal consequences without being over-managed.
- They create punctuated transitions between phases.
- They allow endings, irreversible losses, and evolutionary jumps to be witnessed rather than optimized away.

A cinematic constraint window should occur when one of the following happens:
- A process is too vast to be altered meaningfully in the current phase.
- A threshold has been crossed and consequences must play out.
- The player has exhausted the resource that permits intervention.
- The simulation is resolving into a new level of complexity or collapsing away from one.

During these windows, the player remains present as observer and interpreter, but not controller.

## Intervention Resource

A limited intervention resource is useful because it formalizes the player’s role as powerful but bounded.

This resource should:
- Be scarce during cosmological and geological phases.
- Be generated only by conditions that reflect genuine leverage, not arbitrary cooldowns.
- Become harder to use as more autonomous complexity appears.
- Potentially vanish entirely during terminal sequences.

The exact fiction for this resource can remain open for now. It might be attention, favor, coherence, presence, resonance, or another concept. What matters is that when it is gone and no regeneration path exists, the player does not receive a flat “failure” screen. Instead, time slips away from them.

## Evolution and End States

A world that cannot sustain life is still a valid outcome. A biosphere that collapses is still a meaningful history. A civilization that never reaches the Gates is still a world worthy of witness.

This suggests three broad categories of non-success end or evolution states:

| State | Description | Player experience |
|---|---|---|
| Sterile conclusion | The world never crosses into durable life-bearing complexity. | The player witnesses geological or astronomical conclusion at increasing temporal distance. |
| Living but ungated world | Life or even civilization arises, but never reaches Gate-discovery conditions. | The player sees a complete cultural arc, possibly ending in silence, stability, extinction, or perpetual local renewal. |
| Collapsed world | Complexity arises but destroys or forecloses its own future before synchronization. | The player witnesses tragic culmination and the closure of possibilities that once seemed near. |

These should not be framed as simple losses. They are endings, failed ascensions, or alternate cosmologies.

## The Slip of Time

When the player can no longer influence events and no route remains to recover influence, control should recede gently instead of disappearing abruptly.

Recommended structure:
1. The player notices fewer actionable affordances.
2. The agent changes tone from collaborative to interpretive.
3. Time increments stretch subtly without explicit announcement.
4. The camera or interface language becomes more observational.
5. The player witnesses the conclusion, evolution, or silence of the world.

This creates a graceful handoff from agency to witness. It preserves dignity in failure and reinforces the core emotional truth of the game: the universe continues whether or not it can still be steered.

## Design Rules for Habitable Variety

To consistently produce worlds that feel real, familiar, and alien at once, the generator should favor a constrained band of life-supporting and culture-supporting parameters while allowing rich variation within that band.

Soft constraints should bias toward:
- Stable but varied seasons.
- Skies with legible recurring patterns.
- Climatic diversity sufficient for distinct regional cultures.
- Astronomical events that can generate myth, navigation, and calendar systems.
- Long-term survivability without removing catastrophe.

The goal is not to produce Earth repeatedly. The goal is to produce worlds that feel like places where meaning could gather.

## The Agent as Continuous Interface

The chat-based agent is the continuity layer that makes the whole experience feel like one game instead of several stacked modes.

Its role changes over time:
- In early phases it translates intention into cosmological and ecological bias.
- In mid phases it behaves more like a historian, interpreter, and advisor.
- In late pre-Gate phases it helps the player understand social networks, factions, and emerging avatar candidates.
- After Gate discovery it becomes an in-world companion interface for a person-scale game.

The interface remains stable while the meaning of speaking through it changes. That continuity is essential.

## Deliverables to Derive from This Design

This phase paper suggests several concrete next documents or implementations:

1. A formal phase state machine for world genesis.
2. A schema for intervention resource generation and exhaustion.
3. A catalog of cinematic constraint window types.
4. A world-ending taxonomy with presentation rules.
5. A pre-Gate avatar convergence design note.
6. A cosmology-to-culture pipeline spec.
7. A time-rate function tied to observability and complexity.

## Immediate Framing

The opening of Gates of Truth should be understood as a **world genesis and player narrowing sequence**.

It begins with the player acting at the scale of forces.
It ends with the player becoming a person.
If the world never reaches that point, the player still deserves to witness what it did become.
