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

### 4.4.1 Committed-world Grow — bounded proposal, 2026-10-07

**Status: proposed design continuation, not implementation admission.** Existing
Incoming/3 owner:
[`committed-biosphere-native-action-spec`](../../kanban/tasks/specify-one-causal-native-biosphere-action-on-the-committed-world-ion-spec.md),
on [PR12](https://github.com/octave-commons/Truth/pull/12). This subsection
proposes one Grow request on the already committed world. It does not grant a
Biosphere unlock, add a key, or implement the other ecology verbs, an organism,
a civilization, an embodied player or a Gate.

Grounding is the [production route audit](../notes/2026-10-06-playable-gate-route-audit.md),
the [two natural formation runs](../notes/2026-10-06-two-seed-natural-formation.md),
and the source boundaries below. Those historical runs reached prokaryotic
ecology; they did not prove ordinary native commitment or a Grow interaction.
The scalar helper is a toy-ecology mechanic, not evidence that adding biomass
creates represented organisms or that its magnitude is a biological law.

#### Authority and unresolved decisions

| Item | Recovered authority or source fact | Design consequence |
|---|---|---|
| Currency | Canonical §1 and [UX's currency rule](ux-architecture.md#spark--self) assign Agency to recurring actions and Resonance to unlocks. The July 24 [tech-tree draft §3.2](ability-tech-tree.md#32-which-resource-does-what-making-the-four-resource-table-concrete) agrees; its proposed new components are not implemented authority. | A Grow activation spends Agency once, not recurring Resonance. No new economy is proposed. |
| Unlock | §3's one-Resonance Grow is a Genesis slot. §4.4's two-Resonance Biosphere is the committed planetary slot. [The current law table](../../src/law/narrowing.clj) records the latter but explicitly has no consumer. | Retain the two-Resonance Biosphere unlock policy. Palette presence alone is not proof of purchase; representation and acquisition remain prerequisites. |
| Phase/effect | [Existing `apply-grow`](../../src/domain/ecology/abilities.clj) requires the target's prokaryotic-or-later ecology and adds clamped 0.12 biomass and 0.04 complexity; [helper tests](../../test/domain/ecology_test.clj) cover the phase gate and base effect. | Reuse that helper without intensification or a new biological model. Another world's phase does not establish this target's eligibility. |
| Activation amount | The July 2 [prototype Grow entry](player-abilities-and-ecology.md#5--grow) says 0.09 Coherence; its July 3 Agency section defers to this canonical economy. No Grow-specific Agency amount or conversion is specified. | **Unresolved:** choose and review an Agency price. Neither 0.09 relabeled as Agency nor the physical-intervention default of 15 is established. |
| Cooldown | The prototype says 4000 ms. No production cooldown consumer exists. [Ecology](../../src/domain/ecology/system.clj) advances every 24 physics ticks; its fixed-60-Hz wall-time explanation is stale. | **Unresolved:** retain 4000 ms as historical draft intent, not an accepted clock law or 240-tick conversion. Define time source, pause behavior, and when cooldown starts. |
| Controls | The [July 23 control doctrine](ux-architecture.md#spark--self) supersedes the old Q/E/R recipe and keeps key roles stable. | Future key/menu choice and on-screen affordance are separate from domain eligibility. No old prototype binding is enabled by this proposal. |

The chronology is inspectable in commits `69da447` (July 2 prototype),
`ebf92f4` (July 3 canonical commitment/economy and Agency amendment), and
`cbe80dd` (July 24 draft tech tree and control clarification). A newer draft's
timestamp does not promote its entire implementation sketch to accepted law.

#### Proposed request and settlement boundary

The candidate route is **ordinary input → existing serial intent queue →
pending request → existing ecology writer → existing serial post-fold boundary
→ published effect, payment and feedback**. This is a proposal for review;
there is no such request consumer in current source.

1. On intent application, identify the committed world and retain that target
   identity in the request. Do not select the nearest living body, camera
   selection or a later replacement target. Enqueueing alone grants no unlock,
   spends nothing, starts no cooldown and changes no ecology. Missing commitment
   is rejected with a reason. Exact request identity/schema and bounded storage
   remain to be designed with named Malli validation at the boundary.
2. The existing [`ecology-system`](../../src/domain/ecology/system.clj), the
   sole `c/ecology` writer, consumes pending requests on its scheduled ecology
   update. Recheck target existence/commitment, purchased Biosphere authority,
   target ecology phase, cooldown and current Agency at consumption. Requests
   that became invalid while waiting are rejected without payment. Preserve
   request order; a batch must account for earlier accepted spending and
   cooldown decisions so two requests cannot both spend the same budget.
3. **Proposed payment timing:** the ecology owner decides the effect and a
   terminal outcome together; a serial settlement after the fold debits each
   accepted outcome exactly once and records its result before
   publication. There is no initial debit/refund route. The serial step must
   not rerun ecological eligibility, overwrite `c/ecology`, or charge an
   unconfirmed request. Rejected outcomes leave Agency unchanged. Observer
   economy ownership stays serial; the fan-out must not become a second writer
   of the observer. This uses the existing [post-fold event precedent](../../src/domain/genesis/tick.clj),
   not an additional simulation or generic transaction framework. Current
   lifecycle reaping runs after physics and before promotion/commitment events;
   that precedent alone does not establish atomic effect/payment. The exact
   settlement position must resolve the removed-target question below.
4. The published accepted result exposes the target, causal request, actual
   clamped scalar delta and Agency debit; a rejected result exposes its reason.
   The existing shell must make these outcomes readable without a success
   notification standing in for a state change. Exact result/event shape and
   UI presentation remain review decisions. Passive ecology evolution and
   its phase-event emission continue through their existing owner and path.

#### Specific questions that block a consumer story

- What Agency amount applies to base Grow, and is the inherited 4000 ms
  cooldown retained? Which clock measures it, what does pause do, and does it
  start on accepted consumption? Coordinate these decisions with existing
  [`committed-clock-executable-policy`](../../kanban/tasks/specify-the-executable-committed-world-clock-and-later-history-contract-e-policy.md).
  Independent target/ownership design can proceed; no implementation may assume
  that the current `c/time-lock` data hook enforces wall time.
- What proves that Biosphere was purchased for two Resonance? The draft
  [unlock representation](ability-tech-tree.md#52-new-component) is only a
  candidate. Current [commitment](../../src/domain/narrowing.clj) arms every
  planetary slot without an unlock debit. Do not silently treat that as free
  entitlement or add a complete tech tree to this three-point design task.
- Which precise request/outcome components and owners let consumption,
  deduplication, budget accounting and post-fold payment stay consistent? What
  happens when a target is removed in the same parallel fold? A result must not
  debit an effect that failed to survive that fold. Name all component reads
  and writes and the serial ordering before any implementation is admitted.
- Within the scheduled update, does passive ecology advance before or after
  Grow, which phase is checked, and what happens at saturated biomass and
  complexity? Preserve the helper's clamps; do not silently choose a charge
  for a zero-delta result or a new phase-transition policy.

Later RED must exercise the real queue, owner/fold and settlement boundaries:
one accepted base effect and one debit; absent/inert/replaced target; absent or
insufficient-phase ecology; missing unlock; insufficient Agency; duplicate or
repeated input; multiple requests sharing one budget; queued eligibility
changes; no second consumption on later ticks; and coexistence with passive
evolution and phase events. Native acceptance then requires a naturally formed,
normally committed eligible world, genuine input, visible causal change and
cost/rejection readback. No fixture or direct world edit supplies that proof.

This remains a three-point **specification** continuation. The exact price,
clock and representations are unresolved, so no ready implementation story or
completed card is claimed. Split the later unlock/request/consumer work if its
reviewed scope cannot fit a cohesive story of at most five points. The existing
actor and Gate prerequisites remain untouched.

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

Promotion and demotion are the same conservation problem as `kanban/tasks/phase-0-player-focus-dual-representation-spec.md`. The difference after commitment is that demotion is the default for everything except the committed world.

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
