# Commitment & Resonance

**Path:** `docs/designs/commitment-and-resonance.md`  
**Status:** canonical  
**Scope:** Genesis arc progression, ability allocation, planetary commitment, and the LOD handoff into Phase 1.

---

## 1. The two currencies

The player carries two spendable/allocatable resources:

| Resource | Earned by | Spent on | Feel |
|---|---|---|---|
| **Agency** ("quanta") | Witnessing any threshold event, every tick | Per-action cost of the current ability | Gas pedal. Use it or lose it; it regenerates through observation. |
| **Resonance** | Crossing an arc threshold once | Unlocking and intensifying ability slots | Build currency. It is the amplitude of the world that is in phase with you. |

Agency is flow. Resonance is legacy.

---

## 2. Resonance economy

Resonance is awarded the first time a threshold is crossed in a given world. It is not farmable.

| Threshold | Event kind | Resonance | Agency (quanta) |
|---|---|---|---|
| Nebula collapse | `:event/nebula-collapse` | 1 | 3 |
| Protostar forms | `:event/protostar-formation` | 1 | 8 |
| Star ignites | `:event/stellar-ignition` | 2 | 25 |
| Planet forms | `:event/planet-formation` | 1 | 10 |
| Arc phase transition | `:event/phase-transition` | 1 | 5 |
| Life emerges | `:event/life-emergence` | 4 | 50 |
| Gate discovered | `:event/gate-discovery` | 8 | 100 |

The Resonance award is shown in the center notification alongside the Quanta award, e.g.:

> "A star ignites! +25 quanta, +2 resonance"

Resonance persists across nebula retries if the player drifts to a new formation site. It is a property of the *observer*, not the world, but it is awarded per world-line.

---

## 3. Ability slots

The hotbar has two layers.

### Innate Spark verbs (never locked, rewrite by phase)

| Key | Phase 0 | Phase 1+ | Cost |
|---|---|---|---|
| `Q` | Focus | Focus / Narrow | 0.04 coherence |
| `E` | Nudge | Nudge / Perturb | 0.06 coherence |
| `R` | Release | Release / Widen | 0 |

These are the player's continuous body. They are never removed and never cost Resonance. Their descriptions rewrite as the arc advances, per `docs/designs/ux-architecture.md`.

### Allocatable slots (unlock and intensify with Resonance)

| Key | Default ability | Unlock cost | Intensify cost | Max intensity |
|---|---|---|---|---|
| `1` | Seed | 0 (granted at first planetary surface) | 1 | 3 |
| `2` | Heat | 0 (granted at first planetary surface) | 1 | 3 |
| `3` | Cool | 0 (granted at first planetary surface) | 1 | 3 |
| `4` | Spark | 0 (granted at first planetary surface) | 1 | 3 |
| `5` | Grow | 1 | 1 | 3 |
| `6` | Evolve | 1 | 1 | 3 |

Slots 1–4 appear once the player has a surface to act on. Slots 5 and 6 are gated by ecology phase, matching `docs/designs/player-abilities-and-ecology.md`:

- **Grow** unlocks when any world's ecology reaches `:prokaryotic`.
- **Evolve** unlocks when any world's ecology reaches `:eukaryotic`.

Intensity increases the magnitude or reliability of the ability. For example:

- Seed +1: usable at 15% moisture instead of 20%.
- Seed +2: +6% biomass instead of +4%.
- Seed +3: also seeds a second nearby compatible world.

The player can reallocate Resonance at any time while in the Genesis arc, but doing so costs a small amount of coherence (0.1) and has a 10-second cooldown. This is a soft respec, not a free shuffle.

---

## 4. Commitment

### 4.1 When it becomes available

Commitment is available once `domain.arc/ready-to-narrow?` is true:

- Current arc is `:arc/genesis-planets-formed` or `:arc/life-emergence`.
- At least one habitable world exists (`domain.habitability/habitable-worlds`).

The UI does not announce this with a popup. The Phase panel quietly adds a "Commit" entry; the Journal notes that a candidate world has stabilized; the narrator may speak one ambient line.

### 4.2 The choice

The player selects one habitable world. This emits a ledger event:

```clojure
{:event/world-commitment
 {:world entity-id
  :arc (:arc/current world)
  :reason :habitable | :living | :chosen}}
```

Commitment is irreversible for this world-line.

### 4.3 What changes immediately

- All Resonance unallocates from the Genesis palette.
- A new Phase 1 palette appears in the same six slots.
- The unchosen worlds remain visible in the Entities list and Journal but are no longer interactive.
- The camera may optionally tether to the committed world; the player can release it.

### 4.4 Phase 1 ability palette

| Slot | Ability | Unlock | Effect |
|---|---|---|---|
| `1` | Atmosphere | 0 | Nudge greenhouse, cloud albedo, or retention |
| `2` | Hydrography | 0 | Nudge ocean coverage, ice caps, runoff |
| `3` | Tectonics | 1 | Bias volcanism, rifting, mountain building |
| `4` | Orbit | 1 | Nudge axial tilt, spin rate, moon resonance |
| `5` | Biosphere | 2 | Seed / Grow / Evolve on the committed world |
| `6` | Culture | 2 | Bias first settlement, migration, ritual tendency |

Resonance awarded in the Genesis arc carries over and can be reallocated into this palette. Future thresholds in Phase 1+ continue to award Resonance.

---

## 5. Post-commitment LOD and tick rate

### 5.1 The hard time lock

After commitment, the simulation enters **Phase 1 planetary time**. Time is no longer compressed by complexity. The base tick becomes one simulation second per wall second unless the player manually slows it.

This is a local lock: the committed world and its immediate causal neighborhood run at real time. Distant regions may still be statistical and can be sub-cycled.

### 5.2 Three LOD zones

| Zone | Definition | Representation | Tick cadence |
|---|---|---|---|
| **Immediate** | Within `probability-collapse-radius` of the observer's focus, plus the committed world and its moons | Full ECS entities | 1 s / s, physics fully resolved |
| **Regional** | The star system outside immediate: other planets, asteroid belt, comets, gas disks, close binary companions | Statistical envelopes + event sampling | Sub-cycled: 10 s / s, 100 s / s, or longer depending on dynamical stability |
| **Global** | Interstellar neighborhood, distant Gate-capable worlds | Scalar budgets + probability clouds | Updated only when a causal front arrives |

### 5.3 The central insight: slower tick allows cheaper high fidelity

Because time is no longer compressed after commitment, distant objects can be stepped at very long intervals without the player perceiving a gap. A body in the outer system can be advanced once every 100 simulated seconds and still feel continuous because its dynamical time is long.

Therefore:

- **Immediate zone:** high tick rate, full N-body/hydro/EM.
- **Regional zone:** lower tick rate, but the same physics code runs on the sampled event. There is no second simulator.
- **Global zone:** no tick at all unless an event resolves across the causal boundary.

The LOD system is not a separate engine. It is a scheduling layer over the existing ECS tick that decides *which entities get integrated this frame* and *how large a dt they receive*.

### 5.4 Statistical stellar mechanics

Bodies in the Regional zone are represented by probability distributions over orbital elements, composition, and thermodynamic state. The total mass of each distribution is conserved and recorded in a `:component/statistical-mass` ledger.

When a sampled event would affect the Immediate zone:

1. Sample a concrete trajectory from the distribution.
2. Spawn a temporary resolved entity with that trajectory.
3. Integrate it through the Immediate zone at full fidelity.
4. After the causal interaction completes, debit its mass from the distribution and either remove it or return it to the Regional envelope.

Canonical examples:

- **Asteroid impact:** sample an impactor from the asteroid belt mass distribution; resolve its final approach; on impact, remove its mass from the belt.
- **Stellar flare:** sample a flare energy and direction from the star's activity model; if it intersects the committed world, resolve the atmospheric response; otherwise update the scalar budget.
- **Supernova / gamma-ray burst:** the event is pre-computed when the progenitor's mass threshold is crossed; the light front propagates at `c`; when it reaches the committed world, the full effect resolves.

### 5.5 Promotion and demotion

When the player's focus moves to a Regional body:

1. Sample a concrete state from the distribution.
2. Promote it to Immediate with conservation of mass, momentum, angular momentum, and magnetic flux.
3. Mark it `:field-zone :immediate`.

When focus withdraws:

1. Aggregate the resolved entity back into its Regional distribution.
2. Demote to `:field-zone :regional`.
3. Preserve any ledger events it generated.

Promotion and demotion are the same conservation problem as `docs/specs/phase0-player-focus-and-dual-representation.md`. The difference after commitment is that demotion is the default for everything except the committed world.

---

## 6. Why this resolves the current design conflict

`docs/designs/ux-architecture.md` says abilities are a flat list that rewrites by phase.  
`docs/designs/player-abilities-and-ecology.md` says there are nine keyed slots with unlock costs.

Both are true if we split the hotbar:

- **Q/E/R** are the flat, rewriting Spark verbs.
- **1–6** are the allocatable loadout that rewrites once at Commitment.

The Spark verbs never go away. The ecology/planetary verbs are the buildable kit. Commitment is the respec moment.

---

## 7. First implementation slice

1. Add `:resonance 0.0` to `domain.player/create-observer`.
2. Award Resonance in `domain.arc/advance-arc` when threshold events fire.
3. Add Resonance display to the Spark panel (`infra.menu`) and center notification (`infra.render`).
4. Gate slots 5 and 6 behind Resonance cost and ecology phase.
5. Add the `:event/world-commitment` kind and the commitment UI flow in the Phase panel.
6. Begin the LOD scheduling layer: assign `:component/field-zone` to entities and skip integration for distant Regional/Global entities on most ticks.

Do not implement the full statistical stellar mechanics layer in the first slice. Get the currency, the hotbar split, and the commitment event in place first. The LOD switch follows naturally once those hooks exist.

---

## 8. Design rule for this layer

> Game mechanics are allowed to be simple, readable, and slightly hand-wavy. Physics must be as accurate as consumer hardware allows. The feel comes from the coupling between the two, not from perfecting either one in isolation.

Nitpick the conservation laws and the LOD invariants. Do not nitpick the exact Resonance numbers, ability intensities, or key bindings until playtesting proves them wrong.
