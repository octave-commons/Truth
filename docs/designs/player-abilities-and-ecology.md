# Player Abilities & Toy Ecology

*Design document — Gates of Truth*
*Status: draft · Phase coverage: 0 → early Phase 1*

---

## 1. Purpose

This document specifies the **player ability system** (hotbar, resource economy,
ability definitions) and the **toy ecology subsystem** (the five-variable
biosphere model that gives Phase 0 planets a life arc before the full biological
simulation is written). Together they define the first moment the player is a
*gardener* rather than just a witness.

The browser prototype lives at `output/truth-full.html` and is the canonical
aesthetic and mechanical reference for this design.

---

## 2. The Hotbar

Nine ability slots in two layers.

### Innate Spark verbs (Q/E/R)

These are the player's continuous body. They never lock, never cost Resonance,
and their descriptions rewrite by phase. They are the "flat list that quietly
rewrites itself" from `docs/designs/ux-architecture.md`.

| Key | Phase 0 name | Later phase names | Cost |
|---|---|---|---|
| `Q` | Focus | Focus / Narrow | 0.04 coherence |
| `E` | Nudge | Nudge / Perturb | 0.06 coherence |
| `R` | Release | Release / Widen | 0 |

### Allocatable slots (1–6)

These are the Resonance-powered loadout. Slots 1–4 appear once the player can
act on a planetary surface. Slots 5–6 cost Resonance to unlock and are gated by
ecology phase. Each slot can be intensified by spending additional Resonance.

| Key | Slot | Group | Unlock | Intensify |
|---|---|---|---|---|
| `1` | Seed    | Ecology | 0 | 1 point per level, max 3 |
| `2` | Heat    | Ecology | 0 | 1 point per level, max 3 |
| `3` | Cool    | Ecology | 0 | 1 point per level, max 3 |
| `4` | Spark   | Ecology | 0 | 1 point per level, max 3 |
| `5` | Grow    | Ecology | 1 + `:prokaryotic` | 1 point per level, max 3 |
| `6` | Evolve  | Ecology | 1 + `:eukaryotic` | 1 point per level, max 3 |

Slot selection is instant; activation fires on the selected slot. Clicking while
the cursor is captured fires the active slot. Each slot displays:

- An icon (20 px)
- A key label
- A **cooldown fill bar** (3 px, bottom of slot) that drains from full to empty
  over the cooldown duration, giving the player an instant gestalt of readiness
- A small **Resonance pip** indicator if the slot has been intensified

Selected slots highlight with a color that matches their group: teal for spark
verbs, gold for ecology.

---

## 3. The Four Resources

The player carries three continuous scalar resources in `[0, 1]` and one
progression currency:

### Coherence

The primary cost currency. Abilities debit coherence on activation. Coherence
drifts slightly upward in surface mode (the universe rewards observation) and
slightly downward in space. If coherence drops to zero, abilities with a cost
fail silently with a log message.

*Prototype drift rates: +0.00003/ms on surface, -0.000008/ms in space.*

### Agency ("quanta")

The spendable action currency. Earned by witnessing threshold events and spent
every time a paid ability fires. See `docs/designs/commitment-and-resonance.md`
for the full economy.

### Resolution

Tracks how much of the local region has been promoted to full simulation detail.
Focus increases it; Release decreases it. This directly maps to the LOD zone
system — at sufficient resolution, the region upgrades from statistical particle
to fully integrated ECS entity. After planetary Commitment, Resolution is
localized to the committed world and its immediate neighborhood.

### Resonance

The build currency. Earned once per arc threshold. Allocated into slots 1–6 to
unlock or intensify them. Resonance unallocates at planetary Commitment and a
new palette appears. See `docs/designs/commitment-and-resonance.md`.

---

## 4. Ability Definitions

All abilities are pure functions of world state; they delegate to ecology,
player, or physics mutations but do not own any simulation loop.

```
;; Sketch — real implementation lives in domain.intervention
(defprotocol Ability
  (cost    [a] "Coherence debit [0,1]")
  (cooldown-ms [a] "Minimum refire window")
  (eco-req [a] "Minimum ecology phase index, -1 = always available")
  (fire!   [a world player] "Returns updated world + player"))
```

### Q — Focus

- **Cost:** 0.04 coherence
- **Cooldown:** 1200 ms
- **Effect:** +8% resolution, +6% coherence (net positive at full charge),
  +2% surface moisture. The observation feedback loop: looking resolves,
  resolving rewards coherence, coherence enables more observation.

### E — Nudge

- **Cost:** 0.06 coherence
- **Cooldown:** 800 ms
- **Effect:** Pushes player forward along facing vector at 5 u/s. In the full
  simulation this becomes a gravity-field perturbation: a nudge toward a
  protoplanetary body alters its trajectory within the N-body integrator.
  On surface, raises local temperature +4% (kinetic energy deposit).

### R — Release

- **Cost:** 0 (free)
- **Cooldown:** 400 ms
- **Effect:** −5% resolution (widens the statistical field), +4% ecology
  stability. The counterpart to Focus: releasing attention lets the system
  self-organise and heals instability caused by over-intervention.

### 1 — Seed

- **Cost:** 0.08 coherence
- **Cooldown:** 3000 ms
- **Requires:** surface mode, moisture ≥ 20%
- **Effect:** Deposits prebiotic chemistry. Sets `seeded = true`, adds +4%
  biomass seed stock. Cannot fire in orbit — the player must land.

### 2 / 3 — Heat / Cool

- **Cost:** 0.05 coherence each
- **Cooldown:** 600 ms
- **Effect:** ±8% local temperature. The primary tool for steering the ecology
  into the habitable band (30–75%) before triggering Spark.

### 4 — Spark

- **Cost:** 0.07 coherence
- **Cooldown:** 1500 ms
- **Requires:** `seeded = true`, temperature in habitable band (30–75%)
- **Effect:** Excites chemistry; +6% biomass, +3% complexity. Fails
  gracefully if preconditions unmet — the log reports why.

### 5 — Grow

- **Cost:** 0.09 coherence
- **Cooldown:** 4000 ms
- **Unlock:** 1 Resonance + ecology phase `:prokaryotic`
- **Effect:** +12% biomass, +4% complexity. Stimulates autonomous replication.

### 6 — Evolve

- **Cost:** 0.12 coherence
- **Cooldown:** 8000 ms
- **Unlock:** 1 Resonance + ecology phase `:eukaryotic`
- **Effect:** +15% complexity, −5% stability. Selection pressure: drives
  complexity upward but destabilises intermediate forms. The player must manage
  this trade-off or face extinction.

---

## 5. Toy Ecology

Five continuous scalars in `[0, 1]`, ticked every 400 ms when the player is
on a surface body.

| Variable | Symbol | Description |
|----------|--------|-------------|
| `moisture` | M | Available liquid water fraction |
| `temp` | T | Normalised surface temperature (0 = absolute cold, 1 = runaway heat) |
| `biomass` | B | Total living mass fraction |
| `complexity` | C | Structural/metabolic complexity of life forms |
| `stability` | S | Ecosystem homeostasis |

### Passive tick rules

```
;; Moisture
M' = M + 0.005   if T < 0.6
M' = M - 0.008   if T ≥ 0.6

;; Biomass (only if life exists and conditions are habitable)
B' = B + 0.003   if phase ∉ #{abiotic prebiotic} AND hab? AND M > 0.1

;; Complexity
C' = C + 0.001   (same conditions as biomass)

;; Stability
S' = S - 0.008   if not hab?
S' = S + 0.002   if hab?

;; Temperature mean-reversion (climate regulation)
T' = T + (0.5 - T) * 0.01

;; hab? = T ∈ (0.25, 0.75)
```

### Extinction condition

If `stability < 0.10` and `biomass > 0.05`, the ecology enters collapse:
biomass decreases by 2% per tick. When biomass drops below 5% while in a
living phase, **extinction fires**: the phase resets to `abiotic`, `seeded`
resets to `false`, slots 5 and 6 re-lock, and the event log records the cause.

### Phase transitions

Six phases gate the unlock chain. Transitions are checked after every ability
activation and every passive tick.

| Index | Phase | Transition condition |
|-------|-------|----------------------|
| 0 | `abiotic`      | initial state |
| 1 | `prebiotic`    | `seeded = true` AND M > 0.15 |
| 2 | `prokaryotic`  | B > 0.15 AND T ∈ (0.25, 0.80) |
| 3 | `eukaryotic`   | B > 0.35 AND C > 0.20 |
| 4 | `multicellular`| C > 0.45 AND S > 0.40 |
| 5 | `complex`      | C > 0.70 AND B > 0.60 |

Reaching `prokaryotic` unlocks slot 5 (Grow) and, separately, unlocks the
**Addressable narrator mode** (the narrator can now be spoken to; it responds
in character).

Each transition is appended to the ecology record log with a timestamp, biomass,
and complexity snapshot.

---

## 6. Intended Play Sequence (Veth Example)

1. Arrive at Veth. Surface gravity snaps on as you descend within 3.5 radii.
2. Press **3 (Cool)** once or twice — temperature drops below 0.60, moisture
   begins accumulating passively.
3. Wait for moisture ≥ 20%. Press **1 (Seed)**.
4. Confirm temperature is in the habitable band (30–75%). Press **4 (Spark)**.
5. Prokaryotic life emerges. Slot **5** unlocks. Narrator becomes addressable.
6. Press **5 (Grow)** repeatedly to push biomass. Press **Q (Focus)** between
   cycles to regenerate coherence.
7. Once eukaryotic, slot **6** unlocks. Use **6 (Evolve)** to drive complexity;
   watch stability carefully.
8. Leave orbit. Return later — the ecology continues ticking in your absence.

---

## 7. Commitment

When at least one habitable planet exists, the player may **Commit** to one of
them. Commitment is the objective of the Genesis arc: create a world worth
becoming.

On Commitment:

- All Resonance unallocates from the Genesis palette (slots 1–6).
- A new Phase 1 palette appears in the same slots:
  1. Atmosphere, 2. Hydrography, 3. Tectonics, 4. Orbit, 5. Biosphere, 6. Culture.
- The unchosen worlds remain visible but are no longer interactive.
- Time compression ends for the committed world; the simulation enters one-second-per-second time.

See `docs/designs/commitment-and-resonance.md` for the full economy and LOD
implications.

---

## 8. ECS mapping

These concepts map directly to `domain/` namespaces in the real simulation:

| Prototype concept | ECS target |
|-------------------|-----------|
| `coherence`, `agency`, `resolution`, `resonance` | `domain.player` — player state map |
| Ability `fire!` | `domain.intervention/apply-intervention!` |
| Ecology variables | ECS component `:ecology` on planetary body entities |
| Passive tick rules | `domain.ecology/ecology-system` |
| Phase transitions | `domain.ecology/emit-phase-events` |
| Ecology → narrator unlock | Event emitted to `infra.myth_engine` |
| Slot lock/unlock | `domain.player` flag map, reflected in `infra.render` HUD |
| Commitment | `:event/world-commitment` ledger event + `:player/committed-world` |

The ecology component schema belongs in `law/ecology.clj` following the same
pattern as `law/composition.clj` and `law/plasma.clj`.

---

## 9. Open questions

- **Body persistence:** When the player leaves a body, does the ecology
  continue ticking on a degraded (non-surface) schedule, or does it pause?
  The prototype pauses it. The simulation should continue at a coarsened
  timestep.
- **Multi-body ecology:** Can life spread between bodies (panspermia via
  asteroid impact)? This is a Phase 1 question but the ecology schema
  should leave room for a `:origin-body` field.
- **Coherence regeneration:** The current passive drip is very forgiving.
  Phase 1 may introduce coherence scarcity as a core tension once the player
  has more abilities competing for it.
- **Addressable narrator depth:** The prototype uses canned responses. The
  full implementation routes player text through `infra.myth_engine` (LLM
  call) with the current ecology state injected as context.
