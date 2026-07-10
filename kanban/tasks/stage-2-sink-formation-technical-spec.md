---
uuid: "stage-2-sink-formation-technical-spec"
title: "Stage 2 — Sink Formation: Technical Spec"
status: "done"
priority: "P0"
labels: ["specs", "stage2", "sink"]
created_at: "2026-07-02T19:35:28.973668076Z"
source: "kanban/tasks/stage-2-sink-formation-technical-spec.md"
category: "specs"
---

# Stage 2 — Sink Formation: Technical Spec

**Status:** ready for implementation
**Date:** 2026-06-27
**Companion docs:** `phase0-sink-particle-formation.md` (the epic),
`simulation-methods-research.md` (the research),
`phase0-coupled-physics-and-regime-classifier.md` (the physics).

***

## Goal

Replace `1 parcel → 1 equal-mass body` with **a few sinks forming only where
the gas is genuinely, locally collapsing** — one central seed, then a handful in
the disk. Everything else stays gas. Body count becomes O(1–10), not O(N).

## Design choice

Per the research consensus (Federrath et al. 2010, Hubber et al. 2013):

- **Formation criteria (2b):** density-peak + Jeans mass + isolation
- **Accretion mechanism (2c):** convert-and-seed (absorb nearby parcels on formation)

We implement the **minimal** version of the Federrath criteria — enough to get
"few sinks, each distinct" without a full five-criterion joint test. We add the
full criteria only if spurious sinks appear.

***

## 1. What changes

### 1a. New formation gate in `classify-next-state`

Currently, `:nebula` condenses when:
```
Jeans-unstable AND (ρ ≥ core-condensation-density OR mass > gas-particle-mass)
```

The new gate adds an **isolation criterion** — a parcel can only condense if
it is **outside all existing sinks' accretion radii**:

```clojure
;; Current:
(and (jeans-unstable? region)
     (or (>= density core-condensation-density)
         (> mass gas-particle-mass)))

;; New:
(and (jeans-unstable? region)
     (or (>= density core-condensation-density)
         (> mass gas-particle-mass))
     (not (within-existing-sink? world eid)))  ;; ← NEW
```

`within-existing-sink?` checks whether the parcel's position is inside any
existing sink's accretion radius. This is the isolation criterion — it prevents
the cloud from condensing wholesale because every parcel in the central density
peak is within the first sink's radius after it forms.

### 1b. Convert-and-seed on formation

When a parcel first condenses (transitions `:nebula` → `:debris` or `:protostar`),
it immediately **absorbs** all `:nebula` parcels within its accretion radius:

```clojure
;; In the barrier phase (serial, after the parallel fan-out):
;; For each newly condensed body:
;;   1. Find all :nebula parcels within accretion-radius
;;   2. Sum their mass + momentum
;;   3. Add to the sink (mass-weighted centroid for position)
;;   4. Despawn the absorbed parcels
```

This is the standard sink-formation behavior from Bate et al. (1995) — when a
sink forms, it eats its neighbors. It prevents the "swarm of identical balls"
because the first sink's radius covers the central density peak, absorbing
parcels that would otherwise have condensed individually.

### 1c. Physical accretion radius

Currently, the accretion radius is set by `feeding-zone-factor` (a hack: 600×
the smoothing length). Stage 2 replaces this with a **Bondi-like** radius that
grows with mass:

```clojure
(defn bondi-radius
  "Accretion radius from Bondi-Hill: r = G·M / c_s². Grows with mass."
  [mass sound-speed]
  (/ (* law/G mass) (* sound-speed sound-speed)))
```

On formation, the sink's accretion radius is set to `max(bondi-radius, condensation-smoothing-length)`.
As the sink accretes and grows, the radius updates each tick.

### 1d. Throttled creation

To prevent rapid-fire sink creation during the collapse, we limit new sink
formation to **at most 1 per N ticks** (N = 10–20, tunable). This is the
"high formation bar" mitigation from the research — combined with the isolation
criterion, it ensures few sinks form.

***

## 2. ECS changes

### New components

None needed. We reuse existing components:
- `c/matter-state` — written by `classifier-system` (existing)
- `c/accretion-radius` — written by `accretion-zone-system` (existing)
- `c/mass`, `c/position`, `c/velocity` — updated by barrier merge (existing)

### Modified systems

| System | Change | Writer |
|---|---|---|
| `classifier-system` | Add isolation check to condensation gate | Existing owner of `c/matter-state` |
| `accretion-zone-system` | Use Bondi radius instead of `feeding-zone-factor` | Existing owner of `c/accretion-radius` |
| **New: `sink-formation-system`** | Barrier-phase: absorb nearby parcels on formation | Barrier phase (serial) |

### The sink-formation-system

This is a **serial barrier system** (runs after the parallel fan-out, like
collision/merge). It is NOT a parallel write-set system because it despawns
entities — a discrete event, not a per-tick field update.

```clojure
(defn sink-formation-system
  "Barrier system: for each body that just condensed this tick, absorb all
   :nebula parcels within its accretion radius. Mass + momentum conserved."
  [world]
  (let [new-sinks  (newly-condensed-bodies world)  ;; from classifier output
        gas-parcels (nebula-entities world)]
    (reduce (fn [w sink-eid]
              (let [sink-pos (ecs/get-component w sink-eid c/position)
                    sink-acc (ecs/get-component w sink-eid c/accretion-radius)
                    nearby   (filter #(and (= :nebula (matter-state w %))
                                           (< (dist sink-pos (pos w %)) sink-acc))
                                     gas-parcels)]
                (if (seq nearby)
                  (absorb-parcels w sink-eid nearby)  ;; merge mass + momentum
                  w)))
            world
            new-sinks)))
```

***

## 3. Tick order (updated)

The existing pipeline in `domain.phase0/physics-systems`:

```
1. Gravity (Barnes-Hut)
2. Regime classifier
3. EM (induction + Lorentz)
4. Hydro (velocity/density update)
5. Thermo (energy equation)
6. Fusion (ignition check)
7. Collision detection + merge
8. Classify (matter-state transitions)  ← NEW: isolation check here
9. Accretion zone (set accretion radius) ← NEW: Bondi radius here
10. Observer
```

**New barrier step** (between 9 and 10):

```
11. Sink formation (absorb nearby parcels)  ← NEW: convert-and-seed here
```

***

## 4. Tests

Following the repo discipline: schema, then failing tests, then implementation.

### Test 1: Isolation criterion
```
Given: two Jeans-unstable parcels, one inside an existing sink's radius, one outside
When: classifier runs
Then: only the outside parcel condenses
```

### Test 2: Convert-and-seed
```
Given: a newly condensed body with 5 :nebula parcels within its accretion radius
When: sink-formation-system runs
Then: the 5 parcels are despawned, their mass + momentum added to the sink
Then: total mass + momentum conserved
```

### Test 3: Body count stays low
```
Given: the production cloud (1000 parcels, spin=0.55, turb=0.08)
When: run for 200 ticks
Then: body count ≤ 20 at all times
Then: one body has mass > 0.01 M_⊙ (the central sink)
Then: fog persists (most gas still :nebula at tick 100)
```

### Test 4: Bondi radius grows with mass
```
Given: a sink with mass M and sound speed c_s
When: mass doubles
Then: accretion radius doubles
```

### Test 5: Star still ignites
```
Given: the production cloud with Stage 2 formation
When: run to ignition
Then: exactly one :star forms
Then: star mass > 0.05 M_⊙
```

***

## 5. Tuning parameters

| Parameter | Default | Source | Notes |
|---|---|---|---|
| `core-condensation-density` | 1e-10 kg/m³ | Bate (1998) | Existing — the first-core threshold |
| `sink-creation-throttle` | 15 ticks | Tunable | Max 1 new sink per N ticks |
| `bondi-radius-floor` | condensation smoothing length | Bate et al. (1995) | Min accretion radius on formation |
| `accretion-radius-growth` | Bondi (G·M/c_s²) | Bondi (1952) | Physical, grows with mass |

The `feeding-zone-factor` (600× hack) is **retained** as a fallback for the
sequential pipeline (`:phase0/parallel? false`). The parallel path uses Bondi.

***

## 6. What this does NOT change

- **Stage 1 is untouched.** Force balance, rotation, virial ratio — all preserved.
- **The fog persists.** Most gas stays `:nebula`; only parcels within a sink's
  radius are absorbed. The volumetric renderer continues to show the cloud.
- **The classifier remains the sole writer of matter-state.** The isolation check
  is a predicate *inside* `classify-next-state`, not a separate writer.
- **The ECS substrate is unchanged.** No new components, no new world types.

***

## 7. Exit criteria

| Criterion | Test |
|---|---|
| Body count O(1–10) | `test-accretion-body-count` |
| One dominant central sink | `test-central-sink-dominant` |
| Fog persists | `test-fog-persists-after-sink-formation` |
| Star still ignites | `test-star-ignition-with-sink-formation` |
| Mass + momentum conserved | `test-sink-formation-conservation` |
| All existing tests pass | `lein test` |

***

## 8. Implementation order

1. **Write the isolation predicate** — `within-existing-sink?` in `domain.stellar`
2. **Write failing tests** — Test 1, 2, 4
3. **Add isolation check to `classify-next-state`** — Test 1 passes
4. **Implement `sink-formation-system`** — Tests 2, 4 pass
5. **Add Bondi radius to `accretion-zone-system`** — Test 4 passes
6. **Wire into `phase0/physics-systems`** — Test 3, 5 pass
7. **Tune `sink-creation-throttle`** — body count stays low
8. **Run full test suite** — all 189+ tests green

***

## 9. Risks and mitigations

| Risk | Likelihood | Mitigation |
|---|---|---|
| Gas still condenses faster than sinks eat it | Medium | Raise `sink-creation-throttle`; increase Bondi floor |
| First sink's radius too small, parcels condense outside it | Low | Bondi radius grows with mass; floor at smoothing length |
| Existing tests break from isolation check | Low | Isolation check is additive — only restricts condensation |
| Body count still O(100) at production resolution | Medium | Add "gravitationally bound" criterion (Federrath criterion 2) |

***

## 10. What "done" looks like

One central sink, bright and large, feeding on the disk. A few smaller knots in
the outer disk. The fog streams inward, thinning as gas is consumed. Body count
holds at 3–8. One star ignites. The renderer shows size-by-mass — the central
sink reads large, the disk sinks read small. The collapse is slow enough to
watch. This is the character creation screen: one world, one star, a few
potential planets, emerging from physics.

---
Triage 2026-07-10 (ready→done): DONE-IN-CODE — sink-formation-system emitter (sink.clj:241, registry.clj:178); within-existing-sink? isolation gate; bondi/effective-accretion-radius; competitive accretion; tested in dominant_star + formation_integration. Evolved beyond spec.
---
