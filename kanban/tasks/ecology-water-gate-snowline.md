---
uuid: "ecology-water-gate-snowline"
title: "Ecology water/habitability gate is trivially satisfied"
status: "breakdown"
priority: "P2"
labels: ["fix", "phase0", "chemistry", "handoff", "epic"]
created_at: "2026-07-06T16:21:51.000000000Z"
source: "kanban/tasks/ecology-water-gate-snowline.md"
category: "specs"
estimate: 21
---

# Phase 0 Habitability Handoff Spec

> **Status update (2026-07-10, Claude Code — code-state review):** Frontmatter
> reads `in_progress`, but **none of the M5 structured-handoff code exists yet**
> — this card is effectively `todo`/ready, not started. Verified absent in
> `src/`: `material-class`, `thermal-band`, `atmosphere-class`, `handoff-system`,
> and any `:planet-candidate` / `:phase0-handoff` record (§3–§5). The
> **interim scalar path described in the model-update note is still the live
> code**: `domain.habitability/habitable-worlds` filters
> `(> (habitability-of %) 0.2)` (the trivially-satisfied gate this card's title
> flags), and `domain.arc` still handles the soft handoff via
> `ready-to-narrow?`. Recommend resetting status to `todo`/`ready` or beginning
> Phase 1 (material + thermal classification, §9 first deliverable).

> **Breakdown (2026-07-10, owner call → this card is now the epic):** The full
> M5 handoff below is 13+ points, so per PROCESS.md it was split into ≤5-point
> child slices. This card is retained as the **epic / canonical spec**; execute
> the children. The trivially-satisfied water gate in this card's title is
> resolved by **Phase 1** (a real material/thermal class replaces the
> `habitability-score > 0.2` scalar gate).
>
> | Child slice | Est | Covers |
> |---|---|---|
> | `ecology-m5-phase1-planet-classification.md` | 5 | §3.1–§3.2 material class + thermal band (also fixes the trivial gate) |
> | `ecology-m5-phase2-orbit-stability.md` | 3 | §3.3 analytic orbit-stability proxy |
> | `ecology-m5-phase3-atmosphere-retention.md` | 3 | §4 atmosphere class + retained species |
> | `ecology-m5-phase4-handoff-event.md` | 5 | §2, §5 `:planet-candidate` record + `:phase0-handoff` event |
>
> Child sum 16 → epic estimate 21 (next Fibonacci with coordination overhead).
> Dependency order: Phase 1 → (2, 3 parallel) → Phase 4.

**Status:** ready for implementation (epic — see Breakdown above)
**Milestone:** M5 in `kanban/tasks/roadmap-phase-0-physics-honesty-chemistry-disks-plasma-inspection.md`
**Goal:** Define when Phase 0 ends and what information a surviving planet carries into Phase 1 (planetary cooling, atmosphere formation, prebiotic chemistry).
**Principle:** Phase 0 does not generate life; it produces physically grounded planet candidates from which life can plausibly emerge. The handoff is a data contract, not a cinematic.

> **Decision (2026-07-06):** The full `:planet-candidate` record (§5) is **built now** as Phase 0's canonical *output*, even though no Phase-1 consumer exists yet. It is fed by material class (M3/M5), thermal band (M5), and the volatile budget (M4). Only the downstream **consumer** is capability-gated (**D-consumer**, triggered when Phase 1 begins). Building the contract now makes Phase 0's deliverable concrete and testable rather than a vague scalar.

> **Model update:** references to `domain.phase0` are stale — physics is `domain.genesis`, narrative is `domain.arc` (see `genesis-arc-separation.md`). Composition is the element-resolved map; "metals + silicates" below means the derived `:metal`+`:rock` categories from `domain.chemistry/bulk-categories`, not a stored `:metals` key. The interim handoff that exists today is the scalar path (`arc/ready-to-narrow?` + `habitability/habitable-worlds`); M5 replaces it with the structured record.

***

## 1. Background from notes

From `docs/designs/truth-phase-0-stellar-nebula-design.md`:

> Phase 0 must already contain the seeds of ecology without skipping prematurely to life.

> The slice therefore needs to produce, for each surviving planet-scale body, at least a first-pass answer to the following questions:
> - Is the body rocky, icy, gaseous, or mixed?
> - Does it fall into a plausible thermal band for later habitability?
> - Does it retain an atmosphere, and of what rough class?
> - Is liquid solvent stability plausible under future cooling conditions?
> - Are the bulk chemistry and orbital circumstances compatible with later prebiotic complexity?

Current code (`domain.phase0/detect-phase`, `domain.stellar/classify-system`) classifies bodies by mass and matter-state, and `ready-for-phase-1?` exists but is minimal.

***

## 2. Handoff criteria

Phase 0 ends when **all** of the following are true:

1. A stable star exists (`matter-state` `:star`).
2. At least one candidate planet exists.
3. No unresolved catastrophic collisions are pending (the system is dynamically settled for a 10 Myr lookahead).
4. The player has either:
   - witnessed ignition and sustained focus long enough for the system to settle, or
   - decohered and the system is drifting to a `:sterile` or `:fadeout` ending.

A candidate planet must satisfy:

| Criterion | Minimum threshold | Rationale |
|---|---|---|
| Mass | > `law.stellar/rounding-mass-threshold` (3e20 kg) | Hydrostatic equilibrium proxy |
| Orbit | Bound to the star; eccentricity < 0.4 | Stable enough for climate |
| Temperature | Effective equilibrium temperature between 150 K and 400 K | Liquid water possible with atmosphere |
| Composition | Not > 95% H/He by mass | Distinguishes rocky/icy from gas giant |
| Atmosphere-class | At least `:thin` retention possible | See Section 4 |

***

## 3. Planet classification

### 3.1 Material class

Derived from composition and mass:

| Class | Condition | Handoff tag |
|---|---|---|
| Rocky | `metals + silicates > 50%`, `H+He < 25%`, mass < 1e25 kg | `:rocky` |
| Icy | `H2O + volatiles > 50%`, mass < 5e25 kg | `:icy` |
| Gas giant | `H+He > 50%`, mass > 1e25 kg | `:gaseous` |
| Mixed | None of the above strongly | `:mixed` |

### 3.2 Thermal band

From stellar luminosity `L` and orbital semi-major axis `a`:

```
T_eff = (L (1 - A) / (16 π σ a²))^0.25
```

where `A` is a coarse Bond albedo derived from composition. Bands:

| Band | T_eff range | Tag |
|---|---|---|
| Frozen | < 150 K | `:frozen` |
| Cold | 150–250 K | `:cold` |
| Temperate | 250–350 K | `:temperate` |
| Warm | 350–450 K | `:warm` |
| Hot | > 450 K | `:hot` |

### 3.3 Orbit stability

For each candidate planet, integrate a reduced two-body orbit with the star for 10 Myr using current position/velocity. Mark `:orbit-stable?` if:
- Periapsis > star radius + 5 stellar radii.
- Apoapsis < 100 AU.
- No close approaches to other candidate planets within 10 Hill radii.

***

## 4. Atmosphere retention

Atmosphere-class is a first-pass estimate based on escape velocity, temperature, and composition:

```
v_esc = sqrt(2 G M / R)
v_thermal = sqrt(2 k_B T / μ)
```

where `μ` is mean molecular mass of the dominant atmospheric species.

| Class | v_esc / v_thermal | Tag |
|---|---|---|
| None | < 3 | `:none` |
| Thin | 3–6 | `:thin` |
| Substantial | 6–10 | `:substantial` |
| Thick | > 10 | `:thick` |

The retained species are also estimated:
- H/He retained only if v_esc/v_thermal(H) > 6.
- H₂O, CO₂, N₂ retained if v_esc/v_thermal(species) > 3.

***

## 5. Handoff data contract

For each candidate planet, Phase 0 emits a `:planet-candidate` record:

```clojure
{:planet-id             entity-id
 :star-id               entity-id
 :material-class        :rocky | :icy | :gaseous | :mixed
 :thermal-band          :frozen | :cold | :temperate | :warm | :hot
 :equilibrium-temperature K
 :semi-major-axis       m
 :eccentricity          double
 :orbit-stable?         boolean
 :atmosphere-class      :none | :thin | :substantial | :thick
 :retained-species      #{:H2 :He :H2O :CO2 :N2 ...}
 :bulk-composition      {:H double :He double :O double :C double :Si double :Fe double ...}
 :angular-momentum      [Lx Ly Lz]
 :rotation-axis         [nx ny nz]
 :oblateness            double
 :surface-gravity       m/s²
 :core-dynamo?          boolean   ; true if convective + rotating fast enough
 :magnetic-field        [Bx By Bz] ; surface dipole estimate
 :formation-events      [event-ids] ; threshold events that shaped this body
}
```

This record is appended to the ledger as a `:phase0-handoff` event.

***

## 6. Implementation plan

### Phase 1 — Planet classification

**Tests:**
- `rocky-planet-classified-by-composition`: a high-metal, low-H/He body < 1e25 kg is `:rocky`.
- `gas-giant-classified-by-hydrogen`: a high-H/He body > 1e25 kg is `:gaseous`.
- `thermal-band-computed-from-orbit`: known L and a yield expected T_eff.

**Implementation:**
- Add `domain.stellar/material-class` and `domain.stellar/thermal-band` pure functions.
- Extend `classify-system` to write `:component/material-class` and `:component/thermal-band`.

### Phase 2 — Orbit stability

**Tests:**
- `circular-orbit-is-stable`: a planet at 1 AU around a Sun-like star is marked stable.
- `plunging-orbit-is-unstable`: a planet inside 0.1 AU is marked unstable.
- `close-planet-pair-is-unstable`: two planets within 3 Hill radii are both unstable.

**Implementation:**
- Add `domain.orbital.stability/orbit-stability` helper.
- Run it as part of `classify-system` or a new `stability-system`.

### Phase 3 — Atmosphere retention

**Tests:**
- `earth-like-retains-n2`: Earth mass/radius/temperature retains N₂.
- `moon-like-loses-atmosphere`: low-mass, warm body loses all atmospheres.
- `gas-giant-retains-h2`: Jupiter-like retains H₂ and He.

**Implementation:**
- Add `domain.stellar/atmosphere-class` pure function.
- Write `:component/atmosphere-class` and `:component/retained-species`.

### Phase 4 — Handoff event

**Tests:**
- `handoff-emits-when-star-and-planet-exist`: when conditions met, a `:phase0-handoff` event is appended.
- `handoff-record-contains-required-keys`: every `:phase0-handoff` payload has all keys in the contract.
- `sterile-ending-does-not-emit-handoff`: `:sterile` or `:dispersal` endings produce no candidate records.

**Implementation:**
- Add `domain.genesis/handoff-system` (not `domain.phase0` — renamed) that runs after `classify-system`, wired into the parallel tick as a fan-out emitter (one writer for the handoff/candidate components).
- Update `world-ending` to distinguish `:success` (handoff emitted) from `:sterile`/`:dispersal`/`:fadeout`.
- **Orbit-stability (§3.3):** start with an analytic proxy (periapsis vs. star radius, apoapsis bound, Hill-radius separation) rather than a full 10-Myr two-body integration; the integration is a refinement gated on the proxy proving too coarse. This keeps M5 affordable inside the fixed-60Hz tick.

***

## 7. Rendering / feel

- Candidate planets receive a subtle halo or marker when the system is ready for handoff.
- The HUD displays the count of candidate planets and their material/thermal summary.
- The narrator (see `phase0-narrator-presence.md`) may emit a phrase when the first candidate planet stabilizes.

***

## 8. Out of scope

- Actual atmosphere simulation (pressure, circulation, climate) — Phase 1.
- Prebiotic chemistry — Phase 1 or later.
- Interior geology beyond the simple dynamo estimate — belongs to `domain.interior` in the disk spec.

***

## 9. First deliverable

**Phase 1** (material + thermal classification) is the smallest step. It needs no orbit integration or atmosphere physics, just composition and two-body temperature. It makes the planet categories explicit and testable.

Next action: approve this spec, then write schemas, failing tests, and Phase 1 implementation.

---
2026-07-10 → accepted (epic). Split into 4 ≤5pt child slices: ecology-m5-phase1-planet-classification, -phase2-orbit-stability, -phase3-atmosphere-retention, -phase4-handoff-event. Trivial water gate resolved by Phase 1. See Breakdown table in card.

Triage 2026-07-10: 21pt epic split into four ≤5pt children (phase1-4). Moved to breakdown as umbrella; children are ready.
---
