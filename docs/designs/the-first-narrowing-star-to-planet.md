# The First Narrowing: Star-System → Planet

**Path:** `docs/designs/the-first-narrowing-star-to-planet.md`
**Status:** proposed (2026-07-22)
**Scope:** The Phase 0 → Phase 1 transition as a *felt, gradual, physical*
narrowing of the player's scope of agency — from god-of-the-system to a presence
bound to one world. Reconciles and extends `commitment-and-resonance.md`,
`gates-of-truth-world-gen-phases.md`, `ux-architecture.md`, and
`phase-0-player-focus-dual-representation-spec.md`.

> This is the first rung of the master arc. It is a template for every later
> narrowing (planet → biosphere → species → character at the Gates). Get the
> *feel* right here and the rest of the ladder inherits it.

---

## 1. The invariant this rung must honour

The whole game is **one continuous zoom of agency**. The player's scope of
control narrows monotonically — nebula → star system → planet → biosphere →
species → one character — and each narrowing must feel like a **decision the
player leans into**, not a level-load or a new game. Same ECS world throughout;
the camera, the tick rate, and the ability set narrow *together*.

Three non-negotiables, inherited from canon:

1. **Awe, not power fantasy** (`gates-of-truth-world-gen-phases.md` §Core
   Pillar 4). Committing to a world should feel like *giving something up* to
   gain intimacy — trading reach for touch — not like unlocking a bigger gun.
2. **No ability is ever removed; the player feels the change before they read
   it** (`ux-architecture.md`). Q/E/R persist and rewrite; the six slots re-arm.
3. **Threshold events are felt, never announced** (`ux-architecture.md` hard
   rule). No "Choose your planet!" modal. The world quietly becomes the only
   thing with weight.

The gap this document fills: canon frames **Commitment** as a discrete,
irreversible menu affordance plus a tick-lock (`commitment-and-resonance.md`
§4). The design intent is that it should feel **gradual and physical** — the
player should feel themselves *falling into* a world's gravity well over many
seconds of sustained attention, and feel that leaving costs more the deeper they
go. This document makes binding a continuous quantity and makes Commitment the
horizon you cross, not a button you press.

---

## 2. Gravitational binding as felt physics

Today "focus" is a camera/attention feature. This rung makes **binding** a real,
continuous coupling between the observer and a world — the mechanical substance
of "becoming gravitationally bound to the planet."

### 2.1 The binding quantity

Add an observer↔world coupling `binding ∈ [0,1]` (a `:component/binding` on the
observer, keyed by candidate world eid). It is *not* a UI slider; it accrues and
decays from what the player does:

- **Accrues** while the observer's `attention-shell` immediate radius overlaps a
  candidate world and the player sustains Focus (`Q`) on it. Rate scales with
  the world's habitability/resolution and with Resonance already earned in this
  world-line.
- **Decays** while attention is elsewhere, but slowly — binding is *sticky*
  (legacy, like Resonance), so a glance away doesn't unbind you.

### 2.2 Binding is depth in a potential well

Bind the abstraction to the physics the sim already computes so it reads as
honest, not gamey. The world's own gravitational potential well (from its mass
and the M5 surface-gravity estimate) sets the **shape** of the binding curve:

- The deeper `binding`, the **cheaper** it is to act *on* that world (Nudge/
  Perturb cost falls) — you have leverage because you are close.
- The deeper `binding`, the **costlier** it is to *withdraw* — Release/Widen
  (`R`) stops being free and starts charging Agency, scaling like the work to
  climb out of a gravity well (`∝ escape-energy proxy`). Leaving a world you are
  90% bound to should feel like breaking orbit.

This is the whole trick: the transition is gradual because binding fills
continuously, and it feels like a *decision* because the player watches the exit
cost rise and chooses to keep falling anyway.

### 2.3 The system contracts around you

As binding deepens, the **promotion/demotion machinery** (the Player Focus epic)
does the physical work: the bound world and its moons stay `:immediate` (full
ECS, base tick); the rest of the star system **demotes** to `:regional`
statistical envelopes. The player sees the sky simplify — other planets become
probability clouds, the disk becomes a scalar budget — *because* their attention
is collapsing onto one world. Demotion is not a graphics setting; it is the
visible consequence of binding.

---

## 3. Commitment as a crossed horizon, not a pressed button

`ready-to-narrow?` (canon) gates *availability*. Binding gates the *moment*.

- When `binding` crosses a high threshold (e.g. `0.85`) **and**
  `ready-to-narrow?` is true, the world reaches **capture**: the point past
  which the exit cost exceeds any reserve the player can hold. Crossing it emits
  the canonical `:event/world-commitment` (`commitment-and-resonance.md` §4.2)
  as a **threshold event** — felt, not prompted.
- Before capture: withdrawing is always possible, just increasingly expensive.
  This is what makes it *gradual and reversible-feeling* right up until it isn't.
- At capture: the palette swaps (Genesis → planetary, §4), planetary time-lock
  engages (`commitment-and-resonance.md` §5.1), the unchosen worlds go
  non-interactive. Irreversible for this world-line.

The player should be able to *feel capture coming* — the exit cost climbing, the
sky dimming, the narrator's one ambient line — and choose it. That anticipation
is the decision.

---

## 4. Control and ability continuity (no new game)

Per `ux-architecture.md` and `commitment-and-resonance.md` §3, the body stays the
same; its meaning narrows.

| Key | Phase 0 (system) | Phase 1 (world) | Continuity |
|---|---|---|---|
| `Q` | Focus | Narrow | same verb: concentrate attention |
| `E` | Nudge | Perturb | same verb: bias a process |
| `R` | Release | Widen | same verb: let go — but now it costs (§2.2) |
| `1–6` | Genesis palette (Seed/Heat/Cool/Spark/Grow/Evolve) | Planetary palette (Atmosphere/Hydrography/Tectonics/Orbit/Biosphere/Culture) | slots re-arm in place; Resonance carries over |

The four-control continuity table from the origin notes
(`2026.06.25.16.41.16-002...md`) holds: **Camera-navigate / Focus-attend /
Interact / Release-drift** keep their bindings from Phase 0 through the Gates;
only what they *reach* changes.

---

## 5. The doorway: what the planet is made of when you arrive

The M5 `:planet-candidate` record (Phase 4, just built) is the seed manifest the
committed world is reconstituted from. Phase 1 planetary abilities act on a world
whose starting state is *derived from what Phase 0 actually produced*, not a
prefab:

- material-class + bulk composition → **voxel substrate** seed (rock/ice/metal
  fractions, mantle/crust split) — see the planetary-voxel-substrate design.
- thermal-band + atmosphere-class + retained-species → starting climate/air.
- orbit + rotation axis + surface gravity → seasons, tides, and the binding
  well's shape (§2.2).

This is why the handoff is a *data contract*, not a cinematic: the planet you
sculpt is the one you made.

---

## 6. Choreography (the felt sequence)

1. A candidate world stabilizes. The Phase panel quietly gains a dimmed sense of
   "somewhere to land." No modal.
2. The player rests Focus on it. `binding` begins to fill; acting on the world
   gets cheaper; the world's neighborhood resolves.
3. The rest of the system demotes — other planets fade to probability, the disk
   to a budget. The frame tightens without a cut.
4. `R` (Release/Widen) starts to charge Agency, and the charge climbs. The
   player feels the well.
5. Binding nears capture. One ambient narrator line. The sky is now mostly this
   world.
6. Capture crosses. `:event/world-commitment` fires; the six slots re-arm to the
   planetary palette; planetary time-lock engages. The player is bound — and it
   felt like falling, then choosing to fall.

---

## 7. Implementation spine (maps to the board)

This design sits on work already scoped:

- **Substrate:** `phase-0-player-focus-promotion-demotion` epic (children A/B/C)
  — the promotion/demotion conservation machinery §2.3 requires. **Prerequisite.**
- **This rung's new pieces** (new epic, see board): `:component/binding` + the
  accrue/decay + cost-curve system; `R`-cost-scales-with-binding; capture
  threshold → `:event/world-commitment`; palette re-arm; planetary time-lock;
  camera tether/handoff.
- **Doorway:** M5 `ecology-water-gate-snowline` epic (Phase 4 emits the
  `:planet-candidate` record §5).
- **Destination:** `planetary-voxel-substrate` epic (what Phase 1 abilities edit).

---

## 8. Open design questions (for the owner)

### Resolved 2026-07-22 (Aaron)

1. **Binding curve honesty → literal shape, tuned scale.** The exit-cost curve's
   *shape* is derived from the world's real escape-energy proxy (M5 surface
   gravity / mass); only the overall magnitude is tuned for feel. Heavier, denser
   worlds are genuinely harder to leave.
2. **Pre-capture reversibility → small sunk cost / scar.** Un-binding before
   capture is allowed but leaves a lasting mark (spent Agency and/or a faint
   world-line scar), so binding reads as a real decision, not window-shopping.
3. **Multiple candidates → you can only fall one way.** Binding to one world
   actively *decays* binding to the others; attention is zero-sum and commitment
   is a genuine fork, matching the narrowing metaphor.

### Still open



1. **Binding curve honesty:** tie the exit-cost curve literally to the world's
   escape-energy proxy (from M5 surface gravity), or keep it a tuned game curve
   that merely *evokes* a gravity well? (Recommend: literal shape, tuned scale.)
2. **Reversibility before capture:** should there be *any* irreversible cost to
   unbinding pre-capture (a scar on the world-line), or is it free until the
   horizon? The user said it should "feel like a decision" — a small sunk cost
   sharpens that.
3. **Multiple candidates:** if two worlds are habitable, does binding to one
   actively decay binding to the other (you can only fall one way), or can the
   player oscillate until capture?
4. **Camera:** hard tether at capture, or always player-releasable (canon says
   optional)? A gradual auto-tether that the player *can* fight matches the
   "felt, not forced" rule.
5. **Does binding exist in Phase 0 at all,** or only appear once
   `ready-to-narrow?`? (Recommend: the *mechanic* is general — it is how every
   narrowing works — but it is only *surfaced* when there is something to bind
   to.)
