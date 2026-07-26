---
category: "specs"
labels: ["specs", "phase0", "player", "ui", "settings"]
write-id: "1784745420047-0.0flndf6wvd7vknprgz7j"
source: "kanban/tasks/exposed-tunables-and-settings-menu-spec.md"
title: "Exposed Tunables and Settings Menu"
priority: "P1"
status: "breakdown"
uuid: "exposed-tunables-and-settings-menu-spec"
created_at: "2026-07-09T21:00:00.000000000Z"
---

# Exposed Tunables and Settings Menu Spec

**Status:** draft  
**Goal:** Move the simulation's magic numbers out of source code and into editable defaults that the player can tweak from a Settings menu, while keeping the current values as the shipped defaults.  
**Canonical backing:** `kanban/tasks/phase-0-player-focus-dual-representation-spec.md`, `kanban/tasks/focus-zoom-lod-ui-spec.md`.

***

## 1. Problem

The simulation is full of hard-coded constants that strongly affect how it feels:

- Coherence drain/regen rates, the time-slip threshold, and the complexity cap control how fast the player loses agency and how fast the universe fast-forwards.
- Nebula seeding (`gas-count`, `n-seeds`, `seed-r`, `spin`, `turb`, `nebula-radius`, `nebula-mass`) controls how many cores form and where.
- Disk physics thresholds (`disk-fragment-threshold`, `binary-fragment-threshold`, `disk-viscous-alpha`, `disk-radius-max`) control fragmentation and accretion.
- Volume rendering (`kappa`, `emission-scale`, `scatter-scale`, `visual-h-scale`, `visual-h-min`, `density-cutoff`) controls how bright and opaque the nebula looks.
- Renderer LOD (`sprite-lod-threshold-pixels`, `sprite-min-pixels`, `sprite-max-pixels`, near-plane factor, far-plane factor) controls when bodies switch to sprites and when clipping happens.

Right now the player can only tweak a few Spark knobs. The rest require editing source code, recompiling, and restarting the dev service. This is especially bad for constants that interact non-linearly (e.g., coherence drain + time-slip + complexity cap) because finding a comfortable feel requires iteration.

## 2. Invariants

1. **Defaults are just defaults.** Every hard-coded number becomes a default value read from an explicit tunable map. The current behaviour of the sim is preserved when the player has not changed anything.
2. **No changes to the sim unless the player changes the value.** The tunables are not re-baked into the source code on the next run unless the player explicitly saves them as a new default.
3. **Single source of truth.** A tunable must have exactly one place where its default is defined: either in a `defaults` namespace or in the world/options map at creation time. It must not be copied into multiple places.
4. **Backward-compatible save files.** If a save file is missing a tunable, the missing value falls back to the current default, not zero or nil.
5. **No changes to physics invariants.** Making a value tunable does not remove Malli validation, single-writer rules, or architecture tests. Tunables are validated at the same boundaries as before.

## 3. Tunable registry

Introduce a `domain.defaults` namespace (or extend `law.registry` with a new defaults contract) that contains a canonical map of all player-facing tunables. Each entry contains:

```clojure
{:key        :pacing/coherence-drain-rate      ; namespaced keyword
 :label      "Coherence drain rate"             ; human label
 :scope      :world-or-observer                  ; where the value lives
 :default    0.00075                              ; shipped default
 :dflt-ns    'domain.player.economy              ; source namespace that owns the default
 :type       :double                              ; scalar type
 :range      [0.0 0.01]                          ; soft clamp
 :format     "%.5f"                               ; printf format for the UI
 :step       {:mode :scale :down 0.5 :up 2.0}    ; how the UI stepper changes it
 :group      :pacing                              ; settings panel group
 :description "Per-frame coherence drain at maximum focus."}
```

The registry is a data table, not a UI layout. The UI builds panels from it.

## 4. Groups of tunables

### 4.1 Pacing and time-slip (`domain.pacing` + `domain.player.economy`)

| Tunable | Source var / fn | Proposed default | Reason for exposure |
|---|---|---|---|
| `:pacing/cfl-factor` | `cfl-factor` | 1.0e-4 | Fewer/more ticks per dynamical time: faster or more precise collapse. |
| `:pacing/dt-max` | `pacing-dt-max` | 5.0e10 | Ceiling on how fast the diffuse cloud fast-forwards. |
| `:pacing/dt-min` | `pacing-dt-min` | 1.0e7 | Floor on per-tick step once compact. |
| `:pacing/complexity-cap-form` | `complexity-dt-cap` | `:sqrt` | Toggle between `:linear`, `:sqrt`, `:log` or `:none`. |
| `:pacing/time-slip-max` | `time-slip-factor` | 5.0 | Max fast-forward multiplier while slipping. |
| `:pacing/time-slip-dt-max` | `pacing-dt-slip-max` | 1.0e12 | Ceiling while time is slipping. |
| `:pacing/time-slip-threshold` | implicit in `player/time-slip-threshold?` | 0.3 | Coherence below which slipping starts. |
| `:pacing/coherence-drain-rate` | `coherence-drain-from-focus` | 0.00075 | Per-frame drain at full focus. |
| `:pacing/coherence-regen-rate` | `coherence-regen-rate` | 0.00075 | Per-frame regen at zero focus. |
| `:pacing/coherence-event-bonus` | `coherence-gain-from-event` | map | Per-event coherence gains; at least expose a global multiplier. |

### 4.2 Nebula seeding (`domain.genesis.bootstrap`)

| Tunable | Source var / fn | Proposed default | Reason for exposure |
|---|---|---|---|
| `:genesis/nebula-mass` | `default-world-options` | 4e30 | Total cloud mass. |
| `:genesis/nebula-radius` | `default-world-options` | 3.0e16 | Cloud extent. |
| `:genesis/gas-count` | `default-world-options` | 1000 | Number of parcels. |
| `:genesis/n-seeds` | `default-world-options` | 1 | Overdensity centres. |
| `:genesis/seed-r` | `default-world-options` | 0.25 | Seed spread relative to cloud extent. |
| `:genesis/spin` | `default-world-options` | 0.6 | Rotation as fraction of virial speed. |
| `:genesis/turb` | `default-world-options` | 0.15 | Turbulence as fraction of virial speed. |
| `:genesis/metallicity` | `default-world-options` | `:population-i` | `:primordial` or `:population-i`. |
| `:genesis/collapse-fraction` | `default-world-options` | 0.5 | Unused? Audit. |
| `:genesis/contraction-time` | `default-world-options` | 9.5e14 | Unused? Audit. |
| `:genesis/wind-rate-scale` | `default-world-options` | 1.5 | Stellar wind rate. |

### 4.3 Disk physics (`domain.stellar.disc`, `domain.stellar.disc-evolution`)

| Tunable | Source var / fn | Proposed default | Reason for exposure |
|---|---|---|---|
| `:disk/viscous-alpha` | `disk-viscous-alpha` | 0.05 | How fast the disk drains onto the star. |
| `:disk/formation-j-threshold` | `disk-formation-threshold` | 1.0e15 | Minimum angular momentum for disk formation. |
| `:disk/fragment-threshold` | `disk-fragment-threshold` | 0.7 | Disk/star mass ratio for planet embryo fragmentation. |
| `:disk/binary-fragment-threshold` | `binary-fragment-threshold` | 1.0 | Disk/star mass ratio for binary fragmentation. |
| `:disk/max-radius` | `disk-radius-max` | 1.5e14 | Cap on disk radius for viscous physics. |
| `:disk/sound-speed` | `disk-sound-speed` | 300.0 | Characteristic disk sound speed. |
| `:disk/outer-temperature` | `disk-outer-temperature` | 100.0 | Temperature for Toomre Q and cooling. |
| `:disk/max-fragments` | `max-gi-fragments-per-disk` | 3 | Cap on direct GI fragments. |
| `:disk/fragment-mass-cap` | `gi-fragment-mass-cap` | 0.5 × deuterium limit | Largest direct fragment. |
| `:disk/viscous-drain-cap` | `dm` clamp in `disk-evolution-pass` | 0.05 | Max 5% disk mass per tick. |
| `:disk/companion-mass-frac` | `companion-m` in binary branch | 0.3 | Fraction of disk given to binary companion. |
| `:disk/embryo-mass-frac` | `embryo-m-raw` in GI branch | 0.1 | Fraction of disk given to embryo. |
| `:disk/orbit-radius-frac` | `r-orbit` multipliers | 0.5 binary, 0.3 GI | Where fragments are placed relative to disk radius. |
| `:disk/min-orbit-periods` | `min-fragment-orbit-periods` | 50.0 | Minimum orbit steps for resolved fragments. |

### 4.4 Volume rendering (`infra.render.field`, `infra.render.shader`)

| Tunable | Source var / fn | Proposed default | Reason for exposure |
|---|---|---|---|
| `:volume/kappa` | `default-volume-config` | 0.08 | Optical density per unit visual density. |
| `:volume/emission-scale` | `default-volume-config` | 0.8 | Emission brightness. |
| `:volume/scatter-scale` | `default-volume-config` | 1.0 | Scatter brightness. |
| `:volume/jitter` | `default-volume-config` | 1.0 | Ray-march jitter strength. |
| `:volume/visual-h-scale` | `default-volume-config` | 10.0 | Splat support multiplier. |
| `:volume/visual-h-min` | `default-volume-config` | 4.0 | Minimum splat support. |
| `:volume/splat-gain` | `default-volume-config` | 1.0 | Overall density multiplier. |
| `:volume/density-cutoff` | hard-coded in shader | 0.002 | Skip voxels below this density. |
| `:volume/steps` | hard-coded in shader | 96 | Max ray-march steps. |
| `:volume/light-steps` | hard-coded in shader | 5 | Shadow-march steps. |
| `:volume/cull-outlier-multiple` | `cull-gas-outliers` | 4.0 | Outlier culling radius multiple. |

### 4.5 Renderer LOD and camera (`infra.render.scene.bodies`, `infra.render.scene.setup`, `infra.render.mesh`)

| Tunable | Source var / fn | Proposed default | Reason for exposure |
|---|---|---|---|
| `:render/sprite-lod-threshold` | `default-sprite-lod-threshold-pixels` | 4.0 | Pixels below which bodies become sprites. |
| `:render/sprite-min-pixels` | `sprite-min-pixels` | 2.5 | Minimum sprite size. |
| `:render/sprite-max-pixels` | `sprite-max-pixels` | 24.0 | Maximum sprite size. |
| `:render/near-plane-factor` | `camera-matrices` | 0.01 | Near-plane = factor × distance. |
| `:render/near-plane-floor` | `camera-matrices` | 1.0e-9 | Hard floor for near-plane. |
| `:render/far-plane-factor` | `scene-far-plane` | 1000.0 | Far-plane = factor × distance. |
| `:render/far-plane-min` | `scene-far-plane` | 100.0 | Minimum far-plane. |
| `:render/far-plane-max` | `scene-far-plane` | 10000.0 | Maximum far-plane. |
| `:render/mesh-subdivisions` | window config | 2 | Sphere mesh base subdivision. |
| `:render/halo-segments` | `infra.inspect.overlay` | 64 | Selection/hover ring segment count. |
| `:render/body-glow-star` | `phase0-bodies-from-world*` | 1.5 | Star glow multiplier. |
| `:render/body-glow-protostar` | `phase0-bodies-from-world*` | 1.2 | Protostar glow multiplier. |
| `:render/body-glow-planet` | `phase0-bodies-from-world*` | 0.15 | Planet glow multiplier. |

### 4.6 Observer / Spark (`domain.player`, `domain.intervention`)

These already have a knob panel (`infra.menu.widgets/spark-knobs`). They should be migrated onto the same registry so the Settings menu can expose them too.

- `:genesis/observer-halo-mass-factor`
- `:genesis/influence-dv-cap`
- `:genesis/well-mass-factor`
- `:genesis/well-radius`
- `:genesis/well-ttl`
- `:genesis/heat-approach`
- `:observer/focus-radius`
- `:observer/focus-intensity`

## 5. Settings menu UI

### 5.1 Where it lives

A new top-bar domain: **Settings** (`:settings`). It sits between `:narrator` and `:multiverse`, or between `:spark` and `:phase`. The Settings panel is the in-game place to edit tunables.

### 5.2 Panel structure

The Settings panel is a scrollable column of sections:

```
┌─────────────────────────────────────┐
│  Settings              [Save] [Reset] │
├─────────────────────────────────────┤
│  ▸ Pacing & Time Slip               │
│    Coherence drain   0.00075  [-][+]│
│    Coherence regen   0.00075  [-][+]│
│    Time-slip max     5.0      [-][+]│
│    Complexity cap    sqrt     [ ▼ ] │
│                                     │
│  ▸ Nebula Seeding                   │
│    Gas count         1000     [-][+]│
│    Nebula mass       4.0e30   [-][+]│
│    Nebula radius     3.0e16   [-][+]│
│    ...                              │
│                                     │
│  ▸ Disk Physics                     │
│  ▸ Volume Rendering                 │
│  ▸ Renderer LOD                     │
│  ▸ Spark (existing knobs)            │
│  ▸ Camera Defaults                   │
└─────────────────────────────────────┘
```

Each tunable uses the existing stepper widget from `infra.menu.widgets`.

### 5.3 Presets / profiles

Add a preset selector at the top of the panel:

- **Default** — the shipped values.
- **Storyteller** — slower coherence drain, slower time-slip, faster complexity cap; good for watching.
- **Sandbox** — high coherence drain is disabled, time-slip off, all thresholds relaxed; good for experimentation.
- **Hardcore** — faster drain, more aggressive time-slip, tighter fragmentation thresholds.

Presets are EDN files under `resources/presets/` or entries in the registry. They are applied with a single click but do not overwrite the saved defaults unless the player clicks **Save as Default**.

### 5.4 Actions and persistence

- **Apply** — changes take effect immediately in the live world (where the value lives on `:world` or `:observer`).
- **Save as Default** — writes the current non-default values to `settings.edn` in the save directory (see save-system spec). The source defaults in code are unchanged.
- **Reset to Default** — discards player overrides and restores the shipped defaults from code.
- **Reset Section** — resets only the currently open section.

## 6. Refactoring plan

Phase 1: registry
- Create `domain.defaults` with the data table and helper functions `default`, `all-tunables`, `tunables-for-group`.
- Migrate `default-world-options` and the top-level constants in `domain.pacing`, `domain.player.economy`, `domain.stellar.disc`, `domain.stellar.disc-evolution`, and `infra.render.field` to pull from `domain.defaults`.
- Keep the current values as the shipped defaults.

Phase 2: live world overrides
- At `create-world`, merge the shipped defaults with any saved `settings.edn` overrides and store the result under `:world/options` (or individual `:genesis/*` keys for already-exposed values).
- Systems that read constants now read from the world/options map instead of the source var. Pure functions that don't take the world take an explicit options map.

Phase 3: UI
- Add `:settings` to `infra.menu.widgets/domains`.
- Implement `settings-rows` from the registry.
- Add `[:settings/knob ...]` action handling in `apply-action` / `world-action`.
- Add preset buttons and Reset/Save widgets.

Phase 4: shader tunables
- For volume shader constants, make them uniforms or include them as `#define` macros generated from the tunable map before compile. `infra.render.shader` can accept an optional `preprocessor-defines` map.
- The shader program is recompiled when a shader-facing tunable changes.

## 7. Open questions

1. Should tunables be world-specific (per-save) or global (per-player-profile)? Pacing/difficulty should probably be per-save, but camera sensitivity is global.
2. Should we expose the entire `coherence-gain-from-event` map or just a global multiplier?
3. How do we handle shader recompilation cost? Should we debounce volume shader changes?
4. Do we want a "random seed" input visible in the start menu, or is that part of the save-system spec?
5. Which constants are actually safe to change mid-simulation? Some (e.g., `gas-count`, `nebula-mass`) can only be set at world creation.

## 8. References

1. `src/domain/pacing.clj`
2. `src/domain/player/economy.clj`
3. `src/domain/genesis/bootstrap.clj`
4. `src/domain/stellar/disc.clj`
5. `src/domain/stellar/disc_evolution.clj`
6. `src/infra/render/field.clj`
7. `src/infra/render/shader.clj`
8. `src/infra/render/scene/bodies.clj`
9. `src/infra/render/scene/setup.clj`
10. `src/infra/menu/widgets.clj`
11. `kanban/tasks/start-menu-save-game-spec.md` (companion: persistence layer)

---
Triage 2026-07-22 (Claude, decision from Aaron): promoted from non-canonical 'draft' to breakdown. Rationale: this is largely a defaults-registry refactor (domain.defaults single-source-of-truth + world/options merge + Settings panel) with high feel-value and no new physics. Next step: break into child cards along the spec's own phasing — (1) domain.defaults registry + migrate constants; (2) live world-overrides plumbing; (3) Settings UI panel + presets; (4) shader-uniform tunables. Companion save-game spec is iceboxed until this lands (Phase 1-2 are its prerequisite).
---