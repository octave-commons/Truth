# Phase 0 — Sink-Particle Star Formation (Epic)

**Status:** design spec for an in-progress epic. Stage 1 landed; Stages 2–4 to do.
**Date:** 2026-06-27
**Mandate:** documentary-grade authenticity. We are rendering the birth of a solar
system as if for a film that demands the physics be *real* — a supported,
rotating cloud that collapses over many free-fall times, fragments into a disk,
and feeds a small number of growing cores into one star and a few planets. No
swarm of identical balls; no instantaneous wholesale "resolve."

Companion docs: `docs/notes/2026.06.26-authentic-phase0-formation-physics.md`
(the formation beats and the classifier), `docs/designs/phase0-volumetric-renderer.md`
(the fog), and the double-buffer single-writer spec.

---

## 0. Why this epic exists — the root cause

Running the formation live surfaced four symptoms, all traced to **one design
choice plus one missing force**:

1. **Jarring fog→swarm flip.** The whole cloud condenses within ~2 ticks.
2. **~800 identical "balls."** You cannot tell a big piece from a small one until
   it lights up; everything is the same size.
3. **Low FPS after resolving.** Every system (and the renderer) runs over ~800
   bodies. Per-tick timing: the parallel physics fan-out is 60–245 ms, collision
   16–38 ms — *all of it scaling with the body count*, not collision per se.
4. **Inauthentic accretion.** Bodies "merge" when their 600× **feeding zones**
   overlap — across huge gaps, not on contact. A toy hack from the §7c go-live.

**Root cause A — `1 gas parcel → 1 equal-mass body`.** Condensation promotes each
SPH parcel into its own resolved body, so the cloud of ~1000 parcels becomes
~1000 bodies. This *single* mapping causes symptoms 1–3 and forced the hack in 4.

**Root cause B — no support.** Measured virial ratio `2·KE/|PE| ≈ 0.02` — fifty
times below equilibrium. The cloud was a cold, pressureless free-fall, so it
converted to bodies near-instantly and never showed rotation or a disk.

**The target model is how real star-formation simulations work:** the gas is a
*continuous fluid* (it stays gas, provides the fog, collapses slowly under a real
force balance into a rotating disk); a **small number of sink particles** form
only where the gas is genuinely dense; sinks **accrete the surrounding gas**
(removing it) and grow into a star + a few planets. Gas count becomes *fluid
resolution* (more parcels = smoother fog, better collapse) decoupled from *body
count* (a handful of sinks). That fixes all four symptoms at once and is
documentary-accurate.

---

## 1. Diagnostics (the instruments to keep using)

These scratch probes drove Stage 1 and should drive 2–4 (kept in the session
scratchpad; promote to `dev/` if useful):

- **`dynamics.clj`** — per-tick virial ratio `2·KE/|PE|`, rotational KE fraction,
  RMS cloud radius. The truth-meter for force balance and "is it a disk."
- **`starcheck.clj`** — condensation timeline (nebula→debris/proto→star counts)
  and when/whether a star ignites. The "does it still form a star" gate.
- **`collcost.clj`** — per-system wall-clock timing at the resolved phase. The
  FPS truth-meter.
- **Offscreen render** — `render-to-file` with `{:volumetric? true :tick-fn
  identity :camera-mode :track-largest-cluster}` writes a PNG to inspect the look.
  (Use `:track-largest-cluster`, NOT the default `:fit-all`, which zooms out when
  turbulence ejects a stray particle.)

**Rule for this epic:** every stage is validated against (a) the look (render),
(b) "still forms one star" (starcheck), and (c) FPS (collcost). A change that
breaks any of the three is not done.

---

## 2. Stage 1 — Force balance (✅ DONE)

**Goal:** the cloud resists gravity and collapses slowly, with visible rotation.

**What changed (`domain.phase0/seed-nebula`, `gas-particle-spec`):** rotation and
turbulence are now set as fractions of the **circular speed** `v_circ = √(G·M/R)`
— the velocity scale that balances self-gravity — instead of an arbitrarily
down-scaled "virial" speed. Knobs: `spin` (solid-body rotation as a fraction of
`v_circ` at the edge) and `turb` (isotropic turbulent speed as a fraction of
`v_circ`). Defaults: `spin 0.6`, `turb 0.15`.

**Result (production config):**

| metric | before | after |
|---|---|---|
| virial `2·KE/\|PE\|` | 0.02 | 0.2–0.5 (marginally bound) |
| rotational KE fraction | 0.05 | ~0.5 |
| condensation tick | ~28 | ~50 |
| star ignites | ~33 | ~165 |

189 tests pass. The fog render shows a coherent, slowly-collapsing cloud with
bright knots emerging — not a vanishing free-fall.

**Lessons banked:** the apparent "KE injection" during collapse is gravitational
PE→KE (physical, not a bug). Too much `turb` (≥0.3) unbinds edge particles and
disperses the cloud; rotation-dominated (`spin` > `turb`) keeps it bound *and*
flattens it. Tuning `spin`/`turb` is the dial between "supported/static" and
"free-fall."

**Still open in Stage 1 (polish, optional):** make the disk *visually* obvious
(camera framing / an edge-on option); harden the `fit-all` camera against ejected
particles (clip to a mass percentile so one escapee can't blow out the frame).

---

## 3. Stage 2 — Sink formation (TODO, the hard one)

**Goal:** replace `1 parcel → 1 body` with **a few sinks forming only where the
gas is genuinely, locally dominant** — ideally one central seed, then a handful in
the disk. Everything else stays gas.

**The trap (learned the hard way):** the cloud collapses *homogeneously* — SPH
density (adaptive-h targets constant neighbour count) and the collective Jeans
ratio `M_enc/M_J` are both spatially **uniform** (min≈median≈max). So any
*threshold on density* fires cloud-wide at once (→ swarm), and any naive
*staggering* fragments into non-merging oligarchs (tried, reverted — see the
accretion memory). There is no emergent density contrast to key on.

**Design options (pick/iterate against the diagnostics):**

- **2a. Single seed by global extremum.** Each tick, if no sink exists, convert
  the single deepest-potential / highest-true-local-density parcel (a fixed-radius
  neighbour-mass maximum, NOT adaptive-h density) into a sink. Thereafter sinks
  grow by accretion (Stage 3), and *new* sinks form only when a region is dense
  **and** far enough from every existing sink's accretion radius (so the disk can
  fragment into a few planets, not a swarm). This makes "few" a structural
  property, not a tuning knob.
- **2b. Density-peak + isolation criterion.** A parcel becomes a sink iff it is a
  local maximum of fixed-radius enclosed mass AND that mass exceeds the local
  Jeans mass AND it is outside all existing accretion radii. Standard sink-creation
  test from real SPH star-formation codes (cf. Bate/Federrath sink criteria:
  density threshold + gravitationally bound + not within an existing sink).
- **2c. Convert-and-seed.** When a sink forms, immediately absorb the parcels in
  its nascent accretion radius (don't leave them to condense individually).

**Authenticity note:** real sink criteria are (i) above a density threshold, (ii)
on the local potential minimum, (iii) gravitationally bound, (iv) not overlapping
an existing sink. Implement these literally; they are the documentary-correct
rules and they *intrinsically* limit the count.

**Single-writer / ECS:** sink creation despawns/relabels gas and spawns/flags a
sink — a discrete event, so it belongs in the **barrier phase** (serial, after the
fold), alongside collision/merge — NOT in a parallel write-set system. The
classifier stops promoting nebula→resolved; sink formation owns that transition.

**Validate:** body count stays O(1–10), not O(N); one central sink dominates;
`starcheck` still ignites a star; fog persists (most gas still gas).

---

## 4. Stage 3 — Sink accretion (TODO)

**Goal:** sinks grow by **eating the surrounding gas**, so the fog feeds
gradually into a star instead of the cloud condensing wholesale. Retires the
600× feeding-zone merge hack.

**Mechanism (real sink accretion):** each sink has an **accretion radius**
`r_acc` set by physics, not a hack — a Bondi/Hill-like capture radius that grows
with mass: `r_acc ≈ G·M_sink / c_s²` (Bondi) or a fraction of the Hill radius.
Each tick (barrier phase), a sink absorbs the `:nebula` parcels within `r_acc`
that are gravitationally bound to it: add their mass + momentum (+ angular
momentum → the sink's spin and the disk), despawn the parcel. The sink's `r_acc`
and mass co-evolve → the central sink runs away to a star; disk sinks grow into
planets.

**Why this fixes the fragmentation that killed the staggering attempt:** there,
gas condensed faster than anything could eat it (gas gone by t≈50). With Stage 1's
*slow* collapse, gas now persists much longer, so accretion can outpace
condensation — the sink eats fog over many ticks. Stage 1 is the prerequisite
that makes Stage 3 viable.

**Open risk:** the accretion-vs-collapse race. If gas still condenses (Stage 2)
faster than sinks accrete (Stage 3), we fragment again. Mitigations: make Stage 2
sink-creation *rare* (high bar) so most gas is eaten, not condensed; ensure the
collapse is slow enough (Stage 1 `spin`/`turb`); possibly throttle sink creation
to ≤1 per N ticks. Measure with `starcheck` (want few bodies, one star) +
`dynamics`.

**Conservation:** mass, linear momentum, angular momentum exactly conserved on
absorption (the merge handler already shows the pattern). Place the sink at the
mass-weighted centroid to keep the COM fixed (the recenter teleport bug — see
accretion memory).

---

## 5. Stage 4 — Rendering the resolved bodies (TODO)

**Goal:** you can read the story — big vs small, gas vs solid — without waiting
for ignition.

- **Size by mass.** Resolved bodies' render radius from a log of mass (a forming
  star reads large, a planetesimal small), not the near-identical physical radius.
  Currently `phys->render-radius` makes all condensed parcels the same size.
- **Smooth gas→core transition.** A condensing/accreting sink should read as a
  dense glowing knot *within* the fog (it already lives in the same space as the
  volume), brightening as it heats — not a tan billiard ball popping in. Consider
  feeding sinks (and their accreting envelope) into the volume emission so the
  hand-off is continuous.
- **Disk visibility.** With Stage 1 rotation, the flattened disk should read;
  ensure the camera/lighting show it (an edge-on framing option; the in-scatter
  from the central protostar lighting the disk).
- **Cull / instance.** Even with few sinks there may be many small debris during
  transitions; size-by-mass + dimming makes them recede, and instanced sphere
  drawing removes the per-body draw-call cost if needed.

---

## 6. Cross-cutting concerns

- **Resolution becomes free.** Once body-count is decoupled from parcel-count,
  raising `gas-count` improves the *fluid* (smoother fog, better-resolved disk and
  collapse) at the cost of SPH O(N²) only — which is the *nebula* phase, and is
  parallelisable / can use the BH tree. "More particles" finally helps.
- **Performance.** The FPS fix is Stage 2 (few bodies). Secondary: the SPH passes
  during the nebula phase are O(N²) — if raising `gas-count`, move SPH neighbour
  finding onto the BH octree (already built for gravity) and/or cap it.
- **Retire the feeding-zone hack.** Stage 3's physical `r_acc` replaces the 600×
  `feeding-zone-factor` and the overlap-merge-of-distant-bodies. Collision then
  reverts to *true contact* only (the literal-overlap memory) — authentic again.
- **Clock/pacing.** Slower collapse spans more ticks (watchable). Revisit the
  adaptive clock (`pacing-for`) so the dilation tracks the new, slower beats.
- **Determinism.** Keep sink creation/accretion deterministic (seeded), so runs
  reproduce (note: `create-world` is already nondeterministic at bootstrap — a
  separate known issue).

---

## 7. Build order & exit criteria

1. **Stage 1 — force balance.** ✅ Done. Exit: virial ~0.5, rotation visible,
   slower collapse, star still forms, tests green.
2. **Stage 2 — sink formation.** Exit: body count O(1–10); one dominant central
   sink; fog persists; star still ignites.
3. **Stage 3 — sink accretion.** Exit: sinks grow by eating gas; gas drains
   gradually; 1 star + a few planets; feeding-zone hack removed; FPS good.
4. **Stage 4 — rendering.** Exit: bodies readable by size; smooth gas→core
   hand-off; disk visible.

Each stage is independently committable and leaves the sim in a working,
star-forming state. Stages 2 and 3 are the substance and must be co-developed
(creation rarity vs accretion rate is one tuning surface). Expect several
iterations per stage against the diagnostics.

---

## 8. What "done" looks like (the documentary shot)

A vast, softly-glowing, **rotating** cloud. It slowly draws inward over many
beats, **flattening into a disk** that visibly spins. A bright knot kindles at the
centre and **feeds on the disk**, brightening as it heats — the fog streaming into
it — while a few smaller knots gather in the disk's outer reaches. The centre
ignites: a star. The disk thins as its gas is consumed; what remains are a handful
of worlds. Few objects, each distinct, every transition forced by real physics —
and it runs smoothly because the screen holds a fluid and a dozen bodies, not a
thousand balls.
