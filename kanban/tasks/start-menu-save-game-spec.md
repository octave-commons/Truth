---
uuid: "start-menu-save-game-spec"
title: "Start Menu and Save-Game System"
status: "draft"
priority: "P1"
labels: ["specs", "phase0", "player", "ui", "save-system"]
created_at: "2026-07-09T21:30:00.000000000Z"
source: "kanban/tasks/start-menu-save-game-spec.md"
category: "specs"
---

# Start Menu and Save-Game System Spec

**Status:** draft  
**Goal:** Give Gates of Truth a proper start menu that distinguishes the running background simulation from the game session, introduces save games, auto-saves, and world-line versioning, and can detect when a save is too old or incompatible to load.  
**Canonical backing:** `kanban/tasks/phase-0-player-focus-dual-representation-spec.md`, `kanban/tasks/exposed-tunables-and-settings-menu-spec.md`.

***

## 1. Problem

Right now the dev service boots directly into a fresh simulation. There is no notion of a "game session" separate from the simulator. The user cannot:

- Start a new world with a chosen seed or difficulty.
- Load an earlier world-line.
- Have auto-saves protect progress.
- Know whether a saved world can be loaded after the code changes.

This spec introduces the shell layer that wraps the simulator. It is intentionally a design document — no implementation work yet.

## 2. Invariants

1. **The simulator stays the same.** `domain.genesis.bootstrap/create-world` and the tick loop are still the authoritative simulation; the shell only decides which world to run and when to serialize it.
2. **Saves are deterministic replays, not just snapshots.** A save file must contain enough information that the same world can be reproduced from the same seed (if no divergence has happened). Snapshot saves are also supported for mid-session resume.
3. **Version is explicit.** Every save file carries a `save-version` and a `compatibility-version`. The game must be able to load a save only when its `compatibility-version` matches the running binary's supported range.
4. **Backward compatibility is a decision, not a default.** When a breaking change is made, the compatibility version is bumped. Old saves can be migrated if a migration is written; otherwise they are marked incompatible and the player is told why.
5. **No silent data loss.** Auto-save keeps a bounded number of recent slots. The player can name manual saves. Deleting a save requires confirmation.
6. **The dev service can still run headless.** The start menu is a shell layer; `clj -M:dev` without the UI can still boot directly into a default world for development.

## 3. Concepts

### 3.1 The Shell

The **Shell** is the application layer outside the simulation. It owns:

- The start menu.
- Save / load / auto-save.
- Settings (see `exposed-tunables-and-settings-menu-spec`).
- Player profile (key bindings, unlocked resonances, global preferences).
- Renderer window creation.

The **Simulator** owns:

- The ECS world.
- The tick loop.
- Rendering (as a consumer of the world).

### 3.2 World-Line

A **World-Line** is a single playthrough — a sequence of saves that share a starting seed and a divergence history. It is analogous to a "save game" in other games, but the name emphasizes that small differences (player interventions, random events) branch the timeline.

A world-line is identified by:

- `world-line-id` (UUID)
- `name` (player-editable)
- `created-at` (timestamp)
- `seed` (deterministic seed used to generate the initial nebula)
- `divergences` (list of player interventions that cannot be deterministically replayed)
- `compatibility-version`

### 3.3 Save Types

| Type | Contains | Use case |
|---|---|---|
| **Snapshot** | Full ECS world at a tick | Resume where you left off. Largest file. |
| **Checkpoint** | Seed + world-line state + key divergence points | Smaller; can replay from a known point. |
| **Auto-save** | Snapshot, rotated | Crash recovery; bounded retention. |

A save file is conceptually:

```clojure
{:save-version "0.2.0"           ; format version of this save file
 :compatibility-version 2         ; bump when save semantics break
 :world-line-id #uuid "..."
 :name "My First Star"
 :created-at "2026-07-09T..."
 :saved-at "2026-07-09T..."
 :tick 12345
 :sim-time 4.5e12
 :type :snapshot
 :seed 42
 :divergences [...]              ; optional replay markers
 :world {...}                    ; serialized ECS world (snapshot only)
 :settings-overrides {...}       ; tunables that differ from default
 :player-profile {...}}          ; agency, resonance, unlocked things
```

### 3.4 Compatibility version

A **compatibility version** is an integer that increments only when a change makes old saves impossible to load correctly. Examples of breaking changes:

- New required components with no sensible default.
- Renaming or removing a component used by the world state.
- Changing the meaning of a numeric field (e.g., changing `:sim/dt` from seconds to years).
- Changing the initial conditions in a way that replaying from a seed no longer produces the same world.

Non-breaking changes (do not bump compatibility version):

- Adding new optional components.
- Adding new tunables with defaults.
- Changing the renderer while the world state remains the same.
- Pure UI changes.

### 3.5 Save directory layout

```
~/.gates-of-truth/
├── profile.edn                  ; global settings, key bindings, unlocked resonances
├── saves/
│   ├── <world-line-id-1>/
│   │   ├── world-line.edn       ; metadata + divergences
│   │   ├── manual/
│   │   │   ├── <timestamp>-<name>.edn.zst
│   │   │   └── ...
│   │   └── auto/
│   │       ├── auto-0001.edn.zst
│   │       ├── auto-0002.edn.zst
│   │       └── ...
│   └── <world-line-id-2>/
│       └── ...
└── cache/
    └── neighbor-cache/...       ; optional performance cache, not part of save
```

## 4. Start menu flow

When the player launches the game, they see the **Start Menu** instead of the live nebula. The live nebula is moved to a background attractor or simply paused behind the menu.

### 4.1 Main menu

```
┌────────────────────────────────────────┐
│                                        │
│         GATES OF TRUTH                 │
│                                        │
│                                        │
│    [ Continue last world-line ]        │
│    [ New World            ]          │
│    [ Load World-Line      ]          │
│    [ Settings             ]          │
│    [ Extras / Credits     ]          │
│    [ Quit                 ]          │
│                                        │
│    Last saved: 12 minutes ago          │
│    Version 0.63.2  save-compat v2      │
│                                        │
└────────────────────────────────────────┘
```

- **Continue last world-line**: loads the most recent auto-save or snapshot of the most recently played world-line.
- **New World**: opens the new-world setup panel.
- **Load World-Line**: opens a list of saved world-lines.
- **Settings**: opens the settings panel (see tunables spec).
- **Extras / Credits**: lore, credits, debug tools, dev shortcuts.
- **Quit**: exit the application.

### 4.2 New World setup

```
┌────────────────────────────────────────┐
│  New World                             │
├────────────────────────────────────────┤
│                                        │
│  Name: [__________________]            │
│                                        │
│  Random seed: [ 12345 ]  [🎲]         │
│                                        │
│  Difficulty preset: [Storyteller ▼]    │
│    • Coherence drain: slow             │
│    • Time-slip: gentle                 │
│    • Complexity cap: soft              │
│                                        │
│  Starting conditions:                  │
│    Nebula mass:   [4.0e30   ]          │
│    Nebula radius: [3.0e16   ]          │
│    Gas count:     [1000     ]          │
│    Metallicity:   [Population I ▼]     │
│    Spin:          [0.6      ]          │
│    Turbulence:    [0.15     ]          │
│                                        │
│  [ Advanced ] [ Start ] [ Back ]       │
│                                        │
└────────────────────────────────────────┘
```

- **Name**: default is a generated poetic name (e.g., "The Silent Ember") or timestamp.
- **Random seed**: controls the RNG used for nebula seeding and any other deterministic noise. A "🎲" button generates a new seed.
- **Difficulty preset**: applies a pre-defined set of tunable overrides (Storyteller, Default, Sandbox, Hardcore). The player can still tweak individual values below.
- **Starting conditions**: exposes the world-creation tunables from the tunables spec.
- **Advanced**: toggles more tunables (disk thresholds, volume rendering, etc.).
- **Start**: creates the world-line, seeds the world, and enters the sim.

### 4.3 Load World-Line

```
┌────────────────────────────────────────┐
│  Load World-Line                       │
├────────────────────────────────────────┤
│                                        │
│  ★ The Silent Ember                    │
│    Tick 12,847 · 1.8 Myr · Saved 2h ago│
│                                        │
│  ◦ A Second Branch                     │
│    Tick  3,402 · 0.4 Myr · Saved 1d ago│
│    [incompatible: save-compat v1]        │
│                                        │
│  [ Delete ] [ Rename ] [ Load ]        │
│                                        │
└────────────────────────────────────────┘
```

- Incompatible saves are grayed out with a reason.
- Selecting a world-line shows its saves and lets the player choose which snapshot to load.
- **Delete** requires confirmation.
- **Rename** edits the world-line name.
- **Load** boots the sim from the selected snapshot.

### 4.4 In-game pause menu

During play, pressing `Esc` or choosing a menu action opens the pause menu:

```
┌────────────────────────────────────────┐
│  Paused                                │
├────────────────────────────────────────┤
│                                        │
│  [ Resume ]                            │
│  [ Save World-Line ]                   │
│  [ Load World-Line ]                   │
│  [ Settings ]                          │
│  [ Return to Main Menu ]               │
│  [ Quit to Desktop ]                   │
│                                        │
└────────────────────────────────────────┘
```

- **Save World-Line**: manual snapshot, optionally named.
- **Return to Main Menu**: returns to the start menu without quitting; the current world-line remains in memory until the player starts a new one.

## 5. Auto-save

- Auto-save triggers every 5 minutes of real time or every 10,000 ticks, whichever comes first.
- It keeps the last 5 auto-saves per world-line, rotating them.
- Auto-save is a snapshot; on load it resumes at the exact tick.
- Auto-save never overwrites a manual save.
- The player can disable auto-save in Settings.

## 6. Compatibility detection and migration

### 6.1 Detection

When the game tries to load a save:

1. Read `:save-version` and `:compatibility-version`.
2. Compare `:compatibility-version` against the running binary's `current-compatibility-version`.
3. If equal, load directly.
4. If less, check for a registered migration from the save's version to the current version.
5. If no migration exists, mark the save as incompatible and show the reason.

### 6.2 Migration registry

```clojure
(def migrations
  {1 {:to 2
      :description "Renamed c/lod-level to c/lod-tier and split disk-mass into disk-mass + disk-gas-mass."
      :fn migrate-v1-to-v2}
   2 {:to 3
      :description "Changed nebula seeding RNG; seeds must be re-seeded with new generator."
      :fn migrate-v2-to-v3}})
```

A migration is a pure function `save-edn -> save-edn'`. It is applied in sequence if the save is multiple versions behind. Migrations are required for any breaking change that we want to keep playable.

### 6.3 Incompatibility reasons

When a save cannot be loaded, the UI shows a specific reason:

- `save-version-too-new` — save was made by a newer version of the game.
- `compatibility-version-obsolete` — save is from before the oldest supported migration.
- `missing-required-component <component>` — a component no longer exists with no migration path.
- `tunable-out-of-range <key>` — a saved tunable value is outside the current valid range.
- `checksum-mismatch` — save file is corrupted.

### 6.4 Where to record version

The compatibility version should be recorded in:

- A constant `infra.save/current-compatibility-version`.
- The project `deps.edn` `:project/version` or a `resources/version.edn` file.
- The receipt-river and git tags so we can correlate code changes to version bumps.

## 7. Serialization

### 7.1 ECS world serialization

The ECS world is already pure data. The save system uses `nippy` or EDN to serialize it. nippy is preferred for snapshots because it is fast and compact; EDN is preferred for world-line metadata because it is human-readable.

The world must serialize:

- All components for all entities.
- The next entity id counter.
- World-level keys (`:genesis/sim-time`, `:tick`, `:genesis/complexity`, etc.).

The world must NOT serialize:

- Renderer caches (VBOs, textures, shader program ids).
- Per-frame transient state.
- nREPL/server state.

### 7.2 Deterministic replay

For checkpoint-style saves, the world-line stores:

- The initial seed.
- The deterministic tunables used at creation.
- A list of divergences: player interventions, random events, or anything that cannot be replayed from the seed alone.

Replaying a world-line from the beginning means re-running `create-world` with the stored seed and tunables, then re-applying the divergences in order. This is useful for debugging, for speed-running, and for migrating saves when the deterministic physics changes.

### 7.3 Zstandard compression

Snapshot files are compressed with zstandard (`zstd`) to keep save sizes reasonable. nippy already supports compression; if not, we wrap the bytes with zstd-jni.

## 8. Settings and profile separation

- **Profile** (`~/.gates-of-truth/profile.edn`) is global to the player. Contains: key bindings, graphics settings, unlocked resonances, audio volume, last-played world-line id, default difficulty preset.
- **Settings overrides** (`settings-overrides` in the save or world-line) are per-world-line. Contains: tunables that the player changed from the shipped defaults for this world-line.
- **World state** is in the snapshot or checkpoint.

## 9. UI/UX details

### 9.1 Background attractor

While the start menu is open, the background shows a slow, non-interactive attractor: a saved nebula visualization, a pre-rendered star field, or the most recently played world paused at a safe angle. The attractor does not tick physics.

### 9.2 Keyboard navigation

- Arrow keys / WASD move focus between buttons.
- Enter activates.
- Esc goes back one level.
- Ctrl+S quick-saves from the pause menu.

### 9.3 Controller support (future)

The menu layout should be navigable with a gamepad. Button focus is explicit and visible.

## 10. Failure modes

| Scenario | Desired behaviour |
|---|---|
| Save file corrupted | Show error, offer to load previous auto-save. |
| Disk full during auto-save | Log warning, skip auto-save, notify player once. |
| Load of incompatible save | Gray out in list, show reason, offer to start new world-line. |
| Migration fails | Show detailed error, offer to export save for debugging. |
| Player quits without saving | Prompt to save if unsaved progress exists. |
| Settings change invalidates physics | Validate tunables before applying; reject out-of-range values. |

## 11. Open questions

1. Should the background attractor be a real paused world or a pre-rendered scene? A real paused world is more impressive but requires careful state management.
2. How many manual saves should we allow per world-line? Unlimited with pagination? A fixed cap?
3. Should we support cloud saves later? If so, the save file format should be stable and self-contained.
4. Should "Resonance" unlocks be tied to the profile or to the world-line? Profile seems right for persistence across playthroughs.
5. Do we want a "World-line summary" screen showing total real-time played, number of stars formed, number of living worlds, etc.?
6. Should we expose a "Dev quick-load" mode that bypasses the start menu for the existing dev workflow?

## 12. References

1. `src/domain/genesis/bootstrap.clj` — world creation
2. `src/infra/dev/window.clj` / `src/infra/dev/window/loop.clj` — window layer
3. `src/infra/menu/widgets.clj` — menu widget model
4. `kanban/tasks/exposed-tunables-and-settings-menu-spec.md` — companion: tunables and Settings panel
5. `kanban/tasks/focus-zoom-lod-ui-spec.md` — existing UI conventions
