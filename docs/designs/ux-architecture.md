# Truth — UX Architecture

**Path:** `docs/designs/ux-architecture.md`  
**Status:** Living document — update at each phase milestone.

---

## Governing principle

> The viewport is for awe. The shell explains the world.  
> The player should always be able to see further than they can reach.

Every UI decision must protect and widen the gap between what the player made and what
the simulation made from it. Menus exist to orient, not to interrupt. Overlays cost
something. Information is discovered, not installed.

---

## Shell layers

Five layers, each with a strict single job.

| Layer | Job | Always visible |
|---|---|---|
| **Viewport** | World, motion, threshold events, ambient narrator manifestations | Yes |
| **Top bar** | Domain navigation: World · View · Spark · Phase · Journal · Narrator · Multiverse | Yes |
| **Right drawer** | Context detail for the current domain or selected body | On demand |
| **Modal** | Setup, irreversible choices, worldgen config, chat shell expansion, save/load | On demand |
| **Status bar** | Phase · time rate · simulation mode · hint state · 1-line telemetry | Yes |

The top bar must remain structurally stable for the entire game arc. What changes is
not which menus exist, but which entries inside them are active, grayed, renamed, or
promoted as the player narrows from cosmological witness to embodied person.

---

## Top menu map

### World
Anchor for simulation identity and setup. Sections expand as the world becomes
more articulate; never restructure, only deepen.

```
World
├── New world…                      (modal)
├── Load world…                     (modal)
├── Save snapshot
├── ── separator ──
├── Lore seed corpus…               (modal — upload text, or skip for procedural)
├── World parameters >              (submenu → modal)
│   ├── Stellar
│   │   ├── Star class bias
│   │   ├── Habitable zone bias
│   │   └── Stellar color
│   └── Planetary
│       ├── Axial tilt tendency
│       ├── Moon count tendency
│       ├── Year length tendency
│       └── Day length tendency
├── System bodies >                 (HIDDEN Phase 0, dimmed late Phase 0, active Phase 1+)
│   └── [per-body entries once stable bodies exist]
├── History >                       (active Phase 3+)
├── ── separator ──
└── Release world…                  (irreversible modal, red)
```

**Rule:** World parameters modal should frame every parameter as a *tendency*, not a
guarantee. The physics is doing the work. The player is nudging probability.

---

### View
Camera, overlays, time. The most frequently used menu as the game matures.

```
View
├── Perspective >
│   ├── ● Orbital drift (3D)        always available
│   ├──   Globe survey               active Phase 2+ (first stable planet)
│   └──   Ground level               active Phase 4+ (civilization), no announcement
├── Overlays >
│   ├── Gravity field               [G]  costs coherence slightly while active
│   ├── Thermal bands               [H]  costs coherence slightly while active
│   ├── Resolution field            [V]  shows LOD sphere boundary
│   ├── Orbit guides                [O]  free
│   ├── Atmospheric pressure             active Phase 2+
│   ├── Biome bands                      active Phase 3+
│   ├── Faction map                      active Phase 4+
│   └── Lore annotations            [L]  active Phase 3+, costs coherence
├── Time >
│   ├── ● Auto time rate            tracks observable complexity — default
│   ├──   Manual rate override       slider, available Phase 0–5 only
│   └──   Pause                     [P]
└── Simulation depth >
    ├── Particle density            low / medium / high
    ├── Star field density          minimal / normal / dense
    └── LOD radius                  near / normal / far
```

**Rule:** Overlays that cost coherence should show a small indicator while active —
not a warning, just a subtle badge. The cost is real but quiet.

**Rule:** At Gate discovery (Phase 6), `Manual rate override` grays out permanently.
Status bar notes: *"time is no longer yours alone."* No other explanation.

---

### Spark → Self
Player identity screen for the full pre-Gate arc. Reads as observation of self,
not as a loadout. Renames to **Self** at Gate discovery without announcement.

```
Spark  [→ Self at Phase 6]
├── Current state                   live bars: Coherence · Agency · Resolution
├── Abilities >
│   └── [flat list — same items across all phases, descriptions rewrite at thresholds]
│       Phase 0: Drift · Focus · Influence · Release
│       Phase 1: + Resonate        (unlocked by fusion)
│       Phase 2: + Retarget        (unlocked by stable bodies)
│       Phase 2: + Seed Diff.      (unlocked by first rocky world)
│       Phase 3: + Survey          (unlocked by 2+ worlds)
│       [each old ability shows new phase description — old description never removed]
├── Decoherence risk                low / moderate / critical — brief plain text
└── Ontology                        single non-interactive paragraph, rewrites by phase

Phase 0:  "A quantum bias in the vacuum. Attention is your mass."
Phase 1:  "A stellar witness. You warmed something into existence."
Phase 2:  "A planetary gardener. The bodies persist without you now."
Phase 3:  "An ecological presence. Life is doing things you did not plan."
Phase 4:  "An observer with historical weight. You are shaping narrative, not physics."
Phase 5:  "Narrowed to a lineage. The next threshold is a person."
Phase 6:  [Spark becomes Self. Ontology becomes the avatar's name and city.]
```

**Rule:** No tech tree. No skill web. Just a flat list that quietly rewrites itself.
The player feels the change before they read it.

---

### Phase
Orientation tool, not a quest tracker. Always available, never urgent.

```
Phase
├── Current arc                     name · what physics is doing · time rate context
├── Thresholds >
│   └── [flat list — shows 2–3 phases ahead, always]
│       each entry: name · physics precondition · what it unlocks
├── What just changed >             [EMPTY until first threshold crossed]
│   └── [updates only post-crossing: old meaning → new meaning, per ability]
└── Evolution states >
    ├── Thriving world → Gate-ready civilization
    ├── Sterile world → no ecology arose
    ├── Ungated civilization → life but no Gate technology
    └── Collapsed world → Gates exist, civilization gone (ghost node)
    [all four visible from Phase 0]
```

**Rule:** Evolution states must be visible from the very beginning. The player should
know their world might become a ghost node while they are still a spark in a gas cloud.

---

### Journal
Accumulates quietly. Never forced on the player. Becomes the richest menu by Phase 4.

```
Journal
├── Narration log                   chronological ambient lines from the Narrator
├── Threshold record                when each crossing happened, what changed
├── World events >                  notable simulation events: comet pass, extinction pulse…
├── Astronomy record >              star ignition · planet dates · moon orbital periods
│                                   [raw material mythology is generated from]
├── Mythology >                     [LOCKED until Phase 3 — unlocks silently]
│   └── [procedurally generated creation myth, cosmology, calendar religion]
│       [player did not write this — simulation generated it from astronomy record]
├── Civilizations >                 [active Phase 4+]
│   ├── Cultures
│   ├── Languages (root structures)
│   └── Belief systems
└── Lineages >                      [active Phase 5+]
    └── [families, individuals, relationships — narrowing toward avatar candidates]
```

**Rule:** When Mythology first appears, it should feel like finding something, not
unlocking something. The menu item just exists one session where it didn't before.
The content inside is the only explanation.

**Rule:** The Astronomy record must be legible enough that a player can trace exactly
why the simulation produced the religion it produced. That traceability is the awe.

---

### Narrator
The most philosophically careful menu. The chat shell is discovered, not installed.

```
Narrator
├── Presence mode
│   ├── ● Ambient              narrator exists in the environment, not addressed
│   ├──   Addressable           [GRAYED, no label, no explanation — Phase 0]
│   │                           [becomes available ~Phase 1, may never be found]
│   └──   Companion             [active Phase 4+ once Addressable has been used]
├── Last line                   most recent ambient narrator line, plain text
├── Narration log               → Journal > Narration log
└── Settings >
    ├── Frequency               sparse / normal / constant
    ├── Voice character         [lore-corpus-seeded if corpus was uploaded]
    └── Ambient line display    viewport float / status bar / off
```

**Chat shell discovery sequence:**
1. Player opens Narrator menu at some point in Phase 1+
2. Presence mode > Addressable is no longer grayed — still no explanation
3. Player switches to Addressable
4. A small drawer slides in from the bottom-right of the viewport — not a modal
5. Its entire content is the last ambient narrator line, now re-formatted  
   as if addressed directly: *"something persists here — do you feel it?"*
6. Input box below it. Cursor blinking. No tutorial. No prompt. No instructions.

**Rule:** The player did not change what the Narrator said. They changed what kind of
entity they are in the relationship.

**Phase evolution of chat shell agency:**
- **Phase 0–1:** Translator — *"I want stronger seasons"* adjusts axial tilt tendency
- **Phase 2–3:** Historian and advisor — knows what happened, suggests paths
- **Phase 4–5:** Companion — limited world-actions, cannot reshape physics
- **Phase 6:** Voice only the avatar hears — same input box, compressed agency

---

### Multiverse
Always visible. Locked until Gate discovery. The locked state is evocative, not a gray wall.

```
Multiverse
├── Network status
│   Phase 0–5: "Your world has not yet entangled."
│   Phase 6:   "Entanglement confirmed. N worlds reachable."
├── Gate distance            "0 of 6 thresholds toward Gate discovery" — always visible
├── Connected worlds         [locked] — shows "many" but no names until Phase 6
├── Resonance broadcast      [locked]
├── Co-presence              [locked]
└── Ghost nodes              [locked — but listed with names and one-line descriptions]
    e.g. "Yeth-Korath: the gates are open and nothing answers."
         "Auren-Sel: arrived before us. the signal is warm but old."
    [these are real entries in the multiverse graph, not flavor text]
    [the player cannot reach them, but they exist]
```

**Gate discovery moment — the only UI event that feels like an event:**
- No cutscene. No notification banner.
- The player opens the Multiverse menu.
- Locked items are gone. Live entries appear one at a time, as if the menu is waking up.
- The Narrator speaks one ambient line — in the world, not in the drawer.
- That is the entire ceremony.

---

## Phase-by-phase shell state

| Menu | Ph 0 | Ph 1–2 | Ph 3–4 | Ph 5 | Ph 6 |
|---|---|---|---|---|---|
| **World** | Active, minimal | Gains bodies list | Gains ecology/climate | Gains lineage system | Full governance + history |
| **View** | Orbital + basic overlays | Thermal, orbit guides | Biome, survey, cartography | Faction/ground views | Gate-space view |
| **Spark** | Resources + 4 abilities | Abilities rewrite, +Resonate | Further rewrites | Becomes **Self**, candidates | Named avatar identity |
| **Phase** | Arc + thresholds | "What changed" appears | Evolution states deepen | Narrowing clock visible | Time lock notice |
| **Journal** | Threshold + narrator log | Astronomy record active | Mythology unlocks silently | Lineage, culture, events | Full civilizational archive |
| **Narrator** | Ambient only, chat hidden | Addressable unlocks silently | Advisor mode | Companion, limited actions | Voice only the avatar hears |
| **Multiverse** | Locked, ghost nodes named | Locked + gate distance counting | Locked + resonance building | Narrowing → gate approach | Full network wakes |

---

## Hard rules

1. **No ability is ever removed.** It rewrites. The player feels continuity.
2. **No threshold is ever announced** with a banner or popup. It is felt first, then readable in Phase > What just changed.
3. **Locked menu items must be evocative**, not just gray. They suggest what is coming.
4. **Overlays cost something.** Instruments are not free.
5. **The Narrator chat shell is discovered**, not installed. It may never be found. Both playthroughs are valid.
6. **Manual time control is removed at Gate discovery.** No override. Status bar says why. No other explanation.
7. **Multiverse menu wakes up**, it does not unlock. One entry at a time. In silence.
8. **Spark becomes Self** without announcement. It is just different the next time.
9. **Journal > Mythology appears** without announcement. It is just there one session.
10. **The player should always be able to see further than they can reach.** Every menu should have at least one visible-but-unreachable entry at all times.

---

*Generated from design sessions — June 2026.*  
*Link from root README under "Design documents."*
