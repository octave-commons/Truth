# Ability Tech-Tree — filling the hotbar with choices

**Path:** `docs/designs/ability-tech-tree.md`
**Status:** Design draft — grounded in shipped code + two inert data tables. Not
yet an epic; see the card list at the end for the build order.
**Governs:** the Spark → Self "Allocated slots (1–6)" region of
`docs/designs/ux-architecture.md`, the "Control doctrine (clarified
2026-07-23)" block in the same doc, and the resource/ability sketch in
`docs/designs/player-abilities-and-ecology.md`.

---

## 0. What already exists (read this before designing anything)

This is not a green-field tech tree. Three things are already true in the repo
and the design must fit them, not replace them:

1. **A live, static hotbar.** `infra.render.input/action-palette`
   (`src/infra/render/input.clj:23-34`) is a hard-coded vector of four actions
   — `G` well, `Shift+G` repulsor, `H` heat, `J` cool — each a flat 15.0 quanta
   (`domain.intervention/action-cost`, `src/domain/intervention.clj:33-36`).
   The HUD (`infra.render.hud/controls-hud`, `src/infra/render/hud.clj:352-`)
   renders its legend from that exact same vector, so key/label/cost can never
   drift from what fires — a property the tech tree must preserve.
2. **A domain-side palette schema and a re-arm system, wired at one moment
   only.** `law.narrowing/palette-schema` (`src/law/narrowing.clj:52-58`)
   defines `c/palette` as `{:active (:genesis|:planetary) :slots {1..6 kw}}`.
   `domain.narrowing`'s commitment system is the SOLE writer of `c/palette`
   (`src/domain/narrowing.clj:321-340`) and re-arms it, in place, from
   `law/genesis-palette` to `law/planetary-palette` at planetary Commitment —
   but nothing before or after that moment reads or writes it. It is a re-arm
   mechanism with exactly one occupant.
3. **Two inert data tables and one inert verb module**, sized for exactly
   this feature:
   - `law.narrowing/phase-1-unlock-costs` (`src/law/narrowing.clj:97-99`) —
     Resonance cost per Phase-1 ability. Docstring: *"nothing consumes it
     yet."*
   - `law.narrowing/genesis-palette` / `planetary-palette`
     (`src/law/narrowing.clj:81-95`) — the two known palettes, six slots each.
   - `domain.voxel.sculpt/request-op` (`src/domain/voxel/sculpt.clj:1-60`) —
     uplift/erosion/volcanism, already gated on `c/palette` and already
     spending Resonance via `player/spend-resonance`, but **called from
     nowhere**: no keymap dispatches it (docstring, same file, "KNOWN GAP").

The tech tree's entire job, mechanically, is: **turn `c/palette` into
something read every tick by a data-driven hotbar, and give the player a menu
screen that decides what goes into it, spending the resources that already
exist (`Resonance` earned, `phase-1-unlock-costs` spent) instead of leaving
both inert.** Everything below is designed against that shape, not invented
independently of it.

---

## 1. Research grounding (external)

Four sources shaped the constraints below; none of them are followed
uncritically — Gates of Truth already has stronger constraints (the hard
rules in `ux-architecture.md`) than any of them assume, and where they
conflict the house rules win.

- **Gating reduces choice paralysis, not player agency** — early nodes should
  be cheap/obvious, later nodes should require investment, and "gating...
  serves two functions: reducing choice paralysis... and maintaining
  balance." [Fortress of Doors — Upgrades, Equipment, and Skill Trees](https://www.fortressofdoors.com/upgrades-equipment-and-skill-trees/)
- **Cumulative, not obsoleting.** Unlike gear, skill-tree spends should never
  make an earlier spend feel wasted — "every point you spend... makes them
  better." This is the design argument *for* our hard rule ("no ability is
  ever removed, it rewrites") rather than a system where a later node
  replaces an earlier one outright. [Fortress of Doors, same article](https://www.fortressofdoors.com/upgrades-equipment-and-skill-trees/)
- **A good node has a verb; a great node has a unique verb.** Stat-stick
  nodes ("+10% X or +10% Y") are filler; *Control* shipped only ~17%
  meaningful nodes this way vs *AC: Origins*' ~76%. Directly informs §3 below
  — every unlockable ability in this tree must read as a verb (uplift, erode,
  ignite), never a flat percentage buff. [GDKeys — Keys to Meaningful Skill Trees](https://gdkeys.com/keys-to-meaningful-skill-trees/)
- **Respec should exist but cost, not be free or be forbidden.** "Allow full
  respec of a skill tree, at a high cost" — balances "wasted mistake" anxiety
  against total permanence. Informs §2.4 (Resonance is not literally
  unspendable once placed, but re-speccing after Commitment is expensive and
  rare, matching the game's own "irreversibility as ceremony" posture). [GDKeys, same article](https://gdkeys.com/keys-to-meaningful-skill-trees/)
- **Large trees hide nodes more than two hops from anything unlocked**, to
  avoid overwhelming new players — the opposite of our "see further than you
  can reach" rule. Noted explicitly as a *rejected* pattern: Gates of Truth's
  `ux-architecture.md` rule 10 requires at least one visible-but-unreachable
  entry at all times in every menu, so nodes stay visible-but-dim rather than
  hidden. We follow Path of Exile's convention (a fully-visible constellation
  where every keystone/notable is seeable from level 1, only reachability is
  gated) over the hide-distant-nodes pattern. [Game Design Skill Trees (Beginner's Guide)](https://gamedesigning.org/learn/skill-trees/)

---

## 2. Slots (1–6): role stability, phase palettes, and the re-arm

### 2.1 The invariant

A slot is a **role**, not an ability. Slot 3 is always "the third ecology-ish
verb of this phase" — never once a phase begins does slot 3 mean something
from a different phase. This is the ECS-level expression of the control
doctrine's "role-stable key groups" (`ux-architecture.md`, "Control doctrine"
block): keys 1–6 are permanently bound to the **Abilities** control group; the
tech tree changes their *contents*, never their *group membership* or their
*keybinding*.

Concretely: `1`…`6` always dispatch through `action-for-key` (or its
data-driven successor, §5) to whatever `c/palette`'s `:slots` map says
occupies that index this phase. The player never re-binds a key to a
different role; they unlock or intensify what a role currently means.

### 2.2 What fills the slots per phase

Two palettes exist today in `law/narrowing.clj`; a tech tree needs a third,
earlier one, because right now slots 1–6 pre-Commitment are **not modeled
domain-side at all** — the live Phase-0 hotbar is the four-entry, ungated,
uncosted `action-palette` in infra. The tech tree's first job is to give
Phase 0 its own six-slot palette so the pattern is uniform across all three
phases instead of "infra hack, then two domain tables."

| Phase | Palette (`law/narrowing.clj`) | Slots 1–6 | Currency to fill |
|---|---|---|---|
| **Genesis (pre-Commitment)** | `genesis-palette` (exists, unconsumed) | Seed · Heat · Cool · Spark · Grow · Evolve | Resonance (unlock) — see §3 |
| **Planetary (post-Commitment)** | `planetary-palette` (exists, unconsumed) | Atmosphere · Hydrography · Tectonics · Orbit · Biosphere · Culture | Resonance, costs already tabled: `phase-1-unlock-costs` |
| **Self (post-Gate, future)** | not yet designed | avatar-scale verbs | out of scope for this doc — flagged as an open question in §8 |

Today's live `G`/`Shift+G`/`H`/`J` (well/repulsor/heat/cool) map onto Genesis
slots 1–4 (`Seed`→well-as-a-verb reads oddly; see §3.1 for the exact mapping
decision) at zero unlock cost — they are the **starter verbs**, always armed,
matching the "innate, never locked" posture of `Q/E/R`. Slots 5–6 (`Grow`,
`Evolve`) are the tech tree's first real unlockable content in Phase 0.

### 2.3 The re-arm mechanism (already exists, needs a second call site and a first one)

`domain.narrowing`'s commitment system already re-arms `c/palette` in place at
Commitment (`src/domain/narrowing.clj:332-340`). The tech tree needs:

- **A first call site**, at world-creation / observer-materialization, that
  writes the INITIAL `c/palette` value (`{:active :genesis :slots
  law/genesis-palette}` merged with whatever the player has already unlocked
  from a prior session — see §5.3 persistence note). Today `c/palette` simply
  does not exist until Commitment; before that the live hotbar is infra-only.
  This is a gap, not a design choice, and closing it is card 1 below.
- **No new re-arm logic for the Commitment transition itself** — that already
  works and is tested. The tech tree only needs the unlocked-abilities
  component (§5.2) to persist across the swap: Resonance already carries over
  automatically today (it lives on the observer, no fan-out writes it), and
  the docstring at `src/domain/narrowing.clj:333-339` already promises this.
  A **second, new component** (`c/unlocked-abilities`, §5.2) must carry over
  the same way — the commitment system does not need to touch it, since the
  set of *unlocked ability keywords* is scoped per-palette-namespace (Genesis
  keywords vs Planetary keywords never collide, per `ability-schema`,
  `src/law/narrowing.clj:47-51`) and simply stops being consulted for
  Genesis-namespace keys once `:active` flips to `:planetary`.

### 2.4 Rewrites, never removals

The hard rule (`ux-architecture.md` rule 1) is enforced structurally, not by
convention: `ability-schema` (`src/law/narrowing.clj:47-51`) is a flat
`[:enum ...]` union of every ability keyword across every phase, and
`c/unlocked-abilities` (§5.2) is additive-only — nothing in this design ever
`dissoc`s an unlocked keyword. When Commitment re-arms `:slots`, the Genesis
abilities the player unlocked do not vanish from the unlocked-set; they
simply stop being the active palette's vocabulary. If a later phase design
ever wants to bring one back into a slot (e.g. "Spark" reappearing as a
Culture-phase ritual verb), the unlock is already paid for.

Respec (GDKeys' "allow full respec, at a high cost," §1): out of scope for
v1. `phase-1-unlock-costs` has no refund path today and none is proposed here
— unlocking is monotonic, matching "committing, long-lasting choices" as the
source of meaning (§1). If churn becomes a complaint, a high-Resonance-cost
respec is a self-contained follow-on card, not a v1 requirement.

---

## 3. The tech tree: nodes, currencies, costs, intensify

### 3.1 Node = ability keyword. Tree = the two (soon three) palettes plus intensify levels.

There is no separate "skill point" graph laid over the palettes — the
palettes ARE the tree. Each of the twelve `ability-schema` keywords is a node.
The "tree" shape is the fixed, designed dependency:

```
GENESIS (Resonance-gated, all 6 slots always visible)
  Seed ──> Heat ──> Cool ──> Spark ──> Grow ──> Evolve
  (0)      (0)      (0)      (0)      (1 Res  (1 Res
                                       + phase  + phase
                                       :proka-  :euka-
                                       ryotic)  ryotic)

PLANETARY (Resonance-gated, unlocks re-armed at Commitment)
  Atmosphere ─ Hydrography ─ Tectonics ─ Orbit ─ Biosphere ─ Culture
  (0)          (0)           (1)         (1)     (2)         (2)
```

Costs are `phase-1-unlock-costs` verbatim (`src/law/narrowing.clj:97-99`):
`{:atmosphere 0 :hydrography 0 :tectonics 1 :orbit 1 :biosphere 2 :culture 2}`.
The Genesis-phase costs (Grow 1 + `:prokaryotic`, Evolve 1 + `:eukaryotic`)
already exist as **ecology-phase gates** in `player-abilities-and-ecology.md`
§2 — this design promotes them to the same `phase-1-unlock-costs`-shaped table
(a new `law.narrowing/genesis-unlock-costs`, same shape, sibling constant) so
both phases share one lookup function (§5.1) instead of two different gate
mechanisms (a flat Resonance table vs an ecology-phase check embedded in
ability code). The ecology-phase precondition becomes an *additional* gate
alongside the Resonance cost, not a replacement for it — Resonance is what the
player *spends*, ecology phase is what makes the spend *legal*.

This directly matches gating research (§1, Fortress of Doors): the tree does
not branch into mutually exclusive builds (there is no "pick uplift OR
erosion, never both" fork) because the toy-ecology and voxel-sculpt domains
already established that all six Phase-1 verbs are meant to be acquired
together over the arc of one world, gated by *cost*, not by *exclusivity*.
Exclusive branching is a valid future extension (§8) but not required for v1
and not implied by any code that exists today.

### 3.2 Which resource does what (making the four-resource table concrete)

`ux-architecture.md`'s Spark menu lists four live bars — Coherence, Agency,
Resolution, Resonance — with the terse rule "Resonance is the build currency;
Agency is the action currency." This design pins down the split precisely,
because today it is under-specified enough that `domain.intervention` spends
"agency" (its field is literally named `:agency` in `domain.player.economy`,
`src/domain/player/economy.clj:14,18,20`) while the docs call the same
resource "quanta." One name, no ambiguity, going forward:

| Resource | What it pays for | Where it already lives |
|---|---|---|
| **Agency ("quanta")** | Firing an already-unlocked ability. Recurring, per-use. | `domain.player/spend-agency` → `economy/spend-agency` (`src/domain/player/economy.clj:20`); `domain.intervention/place` (`src/domain/intervention.clj:158-168`) |
| **Resonance** | Unlocking a node in the tree, once. Progression, not recurring. | `domain.player/spend-resonance` → `economy/spend-resonance` (`economy.clj:24`); already consumed by `domain.voxel.sculpt/request-op` |
| **Coherence** | The player's continuous "attention budget" — drains on Focus/sustained aim, regenerates passively. Gates *how long* you can act, not *what* you may act on. | `domain.player/apply-coherence` (`economy.clj:26`) |
| **Resolution** | Not spent by abilities at all — it is the LOD/detail-promotion meter that Focus/Release move. Orthogonal to the tree. | `player-abilities-and-ecology.md` §3 |

So: **unlocking is a Resonance spend (once, permanent); using is an Agency
spend (every activation, unaffected by the tree).** This is why
`phase-1-unlock-costs` is denominated in whole small integers (0, 1, 2) while
`action-cost` is denominated in quanta-15s — they are different currencies
paying for different verbs (build vs. act), exactly as the existing docstrings
already imply but never joined up.

### 3.3 Intensify (leveling an ability, not just unlocking it)

`player-abilities-and-ecology.md` §2 already specifies "1 point per level, max
3" per slot and a per-slot "Resonance pip indicator" on the HUD. This design
adopts that shape unmodified and generalizes it to both palettes:

- Each unlocked ability has an integer **level** in `[0, 3]` (0 = unlocked but
  base-strength; this avoids a confusing "level 1 of 1" off-by-one).
- Intensify costs **1 Resonance per level**, paid the same way as the initial
  unlock (same `can-afford-resonance?`/`spend-resonance` pair).
- Level scales the ability's **existing numeric knobs**, never adds a new
  verb — this keeps every node a "verb, not a stat-stick" at the *unlock*
  layer (§1, GDKeys) while still giving depth via *intensify*, which is
  explicitly a magnitude dial, not a new mechanic:
  - Genesis: level scales `intervention/cost-of` down slightly (an
    intensified Well costs less Agency to fire) OR scales effect radius up —
    pick one consistent axis per ability family at implementation time; do
    not scale both (keeps the cost/benefit legible per GDKeys' "clear
    consequences" note).
  - Planetary: level scales the sculpt magnitude passed into
    `domain.voxel.sculpt/request-op` (today a caller-supplied `mag`,
    `src/domain/voxel/sculpt.clj` intensify hook — level becomes a multiplier
    on `mag`, not a new mag input).

---

## 4. The UI: where it lives, how it stays "see further than you can reach"

### 4.1 Location

The tree lives inside **Spark → Self**, as a new sub-section directly below
"Allocated slots (1–6)" in `ux-architecture.md`'s existing menu map:

```
Spark  [→ Self at Phase 6]
├── Current state
├── Spark verbs (innate)
├── Allocated slots (1–6)
│   └── [as today]
├── Tech tree >                      <-- NEW
│   ├── Genesis constellation        (active Phase 0, always visible)
│   │   Seed ● Heat ● Cool ● Spark ● Grow ○ Evolve ○
│   │   (● = unlocked  ○ = visible, locked, costed, evocative)
│   └── Planetary constellation      (visible-but-dimmed Phase 0, active post-Commitment)
│       Atmosphere ○ Hydrography ○ Tectonics ○ Orbit ○ Biosphere ○ Culture ○
├── Decoherence risk
└── Ontology
```

This is a **submenu of an already-existing top-level entry**, not a new top
bar item — it does not violate the "top bar must remain structurally stable"
rule, because Spark/Self was always the identity screen and a tech tree is
identity information (what you *can become*), same register as "Ontology."

### 4.2 Selection UX

- Clicking a locked, affordable node spends Resonance immediately (no
  confirmation modal for cheap nodes; a confirmation only for the 2-Resonance
  Phase-1 tier, matching "irreversible-feeling" spends getting slightly more
  friction — a soft version of the Commitment modal's own weight).
  Unaffordable nodes are click-disabled but still fully rendered (never
  hidden) with their cost shown in dimmed text — this is the mechanical form
  of "locked items evocative, not gray walls" (`ux-architecture.md` rule 3):
  the node shows its **name and hint text always**, and its **cost** always;
  only the *unlock action* is gated.
- Clicking an unlocked node with `level < 3` and enough Resonance intensifies
  it (fills one more pip); at `level = 3` the node shows "mastered," no
  further click target.
- The **Planetary constellation is visible from Phase 0**, dimmed, with a
  one-line evocative gloss per node pulled from existing narrative material
  (e.g. Tectonics: *"a world that folds itself"*) rather than the literal
  mechanical description — matching rule 3's "suggest what is coming."  This
  is the direct implementation of rule 10 ("always see further than you can
  reach") inside this specific menu: the Planetary row is the
  visible-but-unreachable content this screen guarantees at all times.

### 4.3 Discovered, not installed

No unlock ever produces a popup, toast, or banner (rule 2). The hotbar slot
simply becomes armed the next time the player opens it, and the tree node's
dot state flips from ○ to ● the next time Spark→Self is opened. The only
feedback at the moment of spend is the existing HUD affordance already
described for the palette (`controls-hud`'s `afford-colors`,
`src/infra/render/hud.clj:344-350`) generalized to indicate "just unlocked"
with the same subdued, non-modal visual language already used for
affordability.

---

## 5. ECS data model

Single-writer discipline throughout (`CLAUDE.md` → "Double-buffer /
single-writer tick"): schemas land in `law/` first, then one write-set system
per new component, declared in `domain.ecs.registry`.

### 5.1 Schema additions (`law/narrowing.clj`, extending what's there)

```clojure
;; New sibling to phase-1-unlock-costs, same shape, Genesis phase:
(def genesis-unlock-costs
  "Resonance unlock cost per Genesis palette ability. Grow/Evolve additionally
   require an ecology-phase precondition (see `genesis-unlock-preconditions`)."
  {:seed 0 :heat 0 :cool 0 :spark 0 :grow 1 :evolve 1})

(def genesis-unlock-preconditions
  "Non-Resonance gates on top of `genesis-unlock-costs`: ecology phase index
   the target world must have reached (player-abilities-and-ecology.md §5)."
  {:grow :prokaryotic :evolve :eukaryotic})

;; One lookup, both phases:
(defn unlock-cost [ability] (get (merge genesis-unlock-costs phase-1-unlock-costs) ability 0))

(def unlocked-abilities-schema
  "The `c/unlocked-abilities` component on the observer: every ability
   keyword the player has ever unlocked (additive-only, never shrinks —
   ux-architecture.md rule 1), each mapped to its intensify level [0,3]."
  [:map-of :keyword [:int {:min 0 :max 3}]])
```

### 5.2 New component

```clojure
;; domain/ecs/components.clj, alongside c/palette:
(def unlocked-abilities :component/unlocked-abilities) ;; {ability-kw -> level [0,3]}
```

One writer: a new `:ability-unlock` fan-out system (mirrors
`domain.intervention/place`'s pre-tick, serial, called-from-infra shape — NOT
a per-tick emitter, since unlocking is a discrete player action like
`intervention/place` and `voxel.sculpt/request-op`, not a continuous field).
It reads the observer's Resonance + the target world's ecology phase (Genesis)
or nothing extra (Planetary), and either no-ops (can't afford / precondition
unmet, mirroring `intervention/place`'s "no-op, caller never pre-checks"
posture) or writes an updated `c/unlocked-abilities` map and debits Resonance.

### 5.3 Making `action-palette` data-driven

Today `infra.render.input/action-palette` is a literal vector
(`src/infra/render/input.clj:23-34`) and `action-for-key` scans it
(`input.clj:36-40`); `controls-hud` renders it directly
(`hud.clj:352-390`). The tech tree replaces the *source* of that vector
without changing either consumer's shape:

```clojure
;; infra.render.input — REPLACES the literal def, keeps the same shape/contract
(defn action-palette
  "The player's current paid actions, derived each frame from `c/palette`'s
   active slots + `c/unlocked-abilities` — same 4-key shape action-for-key and
   controls-hud already expect (label/keycap/glfw/shift?/kind/accent/hint), so
   neither consumer changes. An unarmed or un-unlocked slot renders as an
   empty/dimmed row rather than disappearing (rule 3: evocative, not gone)."
  [world]
  (let [{:keys [slots]} (or (ecs/get-component world (player/observer-eid world) c/palette)
                             law/genesis-palette)
        unlocked (or (ecs/get-component world (player/observer-eid world) c/unlocked-abilities) {})]
    (into []
          (keep (fn [[slot-n ability]]
                  (when (contains? unlocked ability)
                    (slot->action-entry slot-n ability (get unlocked ability)))))
          (sort-by key slots))))
```

This is the load-bearing change: **`action-for-key` and `controls-hud` do not
change at all.** They already treat `action-palette` as "the current legend";
making it a function of world state instead of a literal keeps the "HUD can
never drift from what fires" invariant (`input.clj:21-22`'s own docstring
promise) while making it phase- and unlock-aware. `1..6` keys map to `slots`
positionally; the four legacy keys (`G`/`Shift+G`/`H`/`J`) become the
key-bindings for whichever Genesis slots the design settles on in §3.1's
implementation pass (the mapping decision — e.g. does "Seed" bind to a new key
or does `G` well stand in for it structurally — is an implementation
question for card 2, not resolved by this document).

### 5.4 Write-conflict check

No existing system writes `c/unlocked-abilities` or reads/writes `c/palette`
except the commitment system (unchanged). The new `:ability-unlock` action is
a pre-tick serial mutation in the `intervention/place` / `voxel.sculpt/
request-op` style — not a fan-out system — so it adds no new entry to
`domain.ecs.registry`'s write-conflict table at all; it is exactly as
architecture-legal as the two functions it's modeled on.

---

## 6. Interaction with flight / focus-aim / abilities role groups

The control-doctrine block (`ux-architecture.md`, "Control doctrine") already
settles the boundary this design must respect: abilities fire **at the focus
point**, which the Focus/aim group (arrows + `,`/`.`, soon to also track the
pilot per `spark-flight-and-camera.md`) controls independently of the Pilot
group (WASD + 6DOF). The tech tree:

- **Never touches keybindings.** It changes which ability keyword is *behind*
  key `1..6`; it never moves `1..6` to a different physical key and never
  claims a Pilot- or Focus-group key. This is the literal meaning of
  "role-stable" applied to the Abilities group specifically.
- **Never changes where an ability lands.** Every ability in both palettes,
  unlocked or not, fires at the current focus point/reticle, exactly as `G`
  well and `H` heat do today (`domain.intervention/place` takes a `position`
  argument that is always the focus point, `src/infra/render/input.clj`
  caller). The tree adds new verbs to the existing "placed at focus"
  contract; it does not add a second targeting mode.
- **Composes with the 6DOF flight rebuild cleanly** because that epic's
  changes are entirely in the Pilot group (thrust/torque/camera) and this
  epic's changes are entirely in the Abilities group + the Spark→Self menu.
  Neither epic needs to block the other; both read the same focus-point
  value, neither writes it.

---

## 7. Kanban cards (ordered, dependency-explicit)

Card format matches `kanban/tasks/*.md` frontmatter/body convention (see
`kanban/tasks/narrowing-binding-mechanic.md`, `kanban/tasks/voxel-god-scale-
sculpting-ops.md` as models).

1. **`ability-tree-genesis-palette-materialization`**
   Scope: write the INITIAL `c/palette` (`{:active :genesis :slots
   law/genesis-palette}`) at observer materialization, closing the "c/palette
   does not exist until Commitment" gap (`domain.narrowing.clj:337-339`).
   Estimate: 2. Dependencies: none — this is the true root of the epic.

2. **`law-unlock-cost-and-schema-tables`**
   Scope: add `genesis-unlock-costs`, `genesis-unlock-preconditions`,
   `unlock-cost`, `unlocked-abilities-schema` to `law/narrowing.clj` (§5.1);
   no behavior yet, schema + pure data only, tests validate the schema.
   Estimate: 1. Dependencies: none (parallel to card 1).

3. **`unlocked-abilities-component-and-unlock-action`**
   Scope: `c/unlocked-abilities` component, the `:ability-unlock` pre-tick
   action (mirrors `intervention/place`/`voxel.sculpt/request-op`, §5.2),
   spends Resonance, checks ecology-phase precondition for Genesis Grow/Evolve.
   Estimate: 3. Dependencies: 1, 2.

4. **`data-driven-action-palette`**
   Scope: replace the literal `infra.render.input/action-palette` vector with
   the world-state-derived function (§5.3); `action-for-key` and
   `controls-hud` unchanged; legacy `G`/`Shift+G`/`H`/`J` remapped onto
   Genesis slots 1–4 (decide the exact keyword↔key mapping here).
   Estimate: 3. Dependencies: 3.

5. **`spark-self-tech-tree-submenu`**
   Scope: the Spark→Self submenu UI (§4): both constellations rendered, ●/○
   states, always-visible cost/hint text on locked nodes, click-to-unlock,
   click-to-intensify, Planetary constellation dimmed-but-visible pre-
   Commitment. No new top-bar entry.
   Estimate: 5. Dependencies: 3 (needs the unlock action to call into).

6. **`ability-intensify-levels`**
   Scope: the `[0,3]` level dial (§3.3) wired into `intervention/cost-of` (or
   radius) for Genesis abilities and into `voxel.sculpt/request-op`'s `mag`
   multiplier for Planetary abilities; HUD Resonance-pip indicator per slot.
   Estimate: 3. Dependencies: 3, 4.

7. **`voxel-sculpt-verb-keymap`**
   Scope: closes `domain.voxel.sculpt`'s own known gap — wire
   Tectonics/Hydrography/Orbit/Biosphere/Culture actually into
   `action-palette`'s post-Commitment output so `request-op` finally has a
   caller (the "called from nowhere" gap, `src/domain/voxel/sculpt.clj`
   KNOWN GAP paragraph). This is the payoff card: without it the Planetary
   half of the tree unlocks abilities that still do nothing.
   Estimate: 5. Dependencies: 4, and the existing voxel-focus/voxel-sculpt
   substrate (already `done`, per `kanban/tasks/voxel-god-scale-sculpting-
   ops.md`).

8. **`tech-tree-persistence`**
   Scope: `c/unlocked-abilities` (and `c/palette`) survive save/load — audit
   whatever save mechanism exists for other observer components and extend
   it; flagged explicitly because nothing in this design assumes persistence
   exists yet.
   Estimate: 2. Dependencies: 3.

**Dependency graph:** 1, 2 → 3 → {4, 5, 6, 8}; 4 → 7 (7 also needs the
existing voxel substrate, already done). 5 and 6 can proceed in parallel once
3 and 4 land. 8 can be pulled forward or deferred independently.

---

## 8. Open questions (honest, not resolved here)

- **Self-phase (post-Gate) palette.** `ux-architecture.md` names Phase 6 as
  "Spark becomes Self" but no third palette is designed. Left for a later
  card once Phase 5/6 content exists to hang verbs on.
- **Respec.** Not designed (see §2.4) — flag if churn complaints appear
  in playtesting; GDKeys' "high-cost full respec" is the fallback shape.
- **Exclusive branches.** This design is purely cumulative (§3.1) because
  nothing in the existing ecology/voxel domains implies mutually exclusive
  builds. If a future phase wants a real fork (e.g. "Biosphere OR Culture,
  not both, this world-line"), it is a new mechanic, not an extension of this
  one — flag before building it.
- **Cross-session unlock carry-over vs. per-world-line reset.** Does
  Resonance/unlocked-abilities reset when a NEW world is created (`World >
  New world…`), or persist as meta-progression across playthroughs? Not
  addressed; the safest default given "Resonance unallocates... a new palette
  appears" language already in `domain.narrowing.clj:333-339` is per-world-
  line (fresh unlocks each new world), but this should be an explicit owner
  decision before card 8 ships.

---

*Generated from a design session — 2026-07-23. Research basis: Fortress of
Doors ("Upgrades, Equipment, and Skill Trees"), GDKeys ("Keys to Meaningful
Skill Trees"), gamedesigning.org ("Game Design Skill Trees"), and Path of
Exile's fully-visible-constellation convention (cited by contrast with the
"hide distant nodes" pattern those articles otherwise describe). Grounded in
`src/domain/intervention.clj`, `src/infra/render/input.clj`,
`src/infra/render/hud.clj`, `src/law/narrowing.clj`, `src/domain/narrowing.clj`,
`src/domain/voxel/sculpt.clj`, `docs/designs/ux-architecture.md`,
`docs/designs/player-abilities-and-ecology.md`.*
