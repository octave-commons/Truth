● Both reviews are in and they converge cleanly. Here's the verdict on your
  question.

  Did you mention this before? Is it covered?

  Partly — the symptom yes, your proposed fix no.

  - The "cloud there, then gone" poof is a documented, named problem: the
  sink-particle epic (docs/designs/phase0-sink-particle-formation.md) calls out
  "Root cause A — 1 gas parcel → 1 equal-mass body" and the wind spec states the
  goal "mass moves through the medium; nothing 'poofs'." So you've flagged the
  feeling before.
  - You also worked through Roche-lobe mass transfer between stars in
  docs/notes/modeling stellar merges and feeding.md (with the Eggleton 1983
  formula) — the closest existing analog to "transfer until it stabilizes."
  - But your two specific mechanisms are undesigned: (a) splitting a body on a 
  phase transition, and (b) an iterative transfer-until-stabilization loop with 
  a below-threshold cutoff. Nothing describes them. One invariant even runs
  counter to (a): "a body that has collapsed into a resolved state never returns
  to :nebula."

  Why the 1000-Earth-mass "terrestrial" happens (root cause, confirmed in code)

  Three compounding all-or-nothing facts:
  1. Resolution. Mass only moves in whole parcels, and one default parcel =
  ~10²⁷ kg ≈ 670 Earth masses. Absorbing a single parcel onto a rocky body
  already makes it ~670 M⊕.
  2. Whole-body everything. Classification flips the whole parcel to a body in
  one tick (classify-next-state); sink capture swallows whole parcels
  (absorb-packets); collisions merge the whole smaller body
  (stellar-merge-handler). There is no donor-debit channel — a donor is either
  despawned whole or untouched; partial consumption isn't even representable.
  3. planet-type is a location tag with no mass ceiling — computed once at seed
  time, never re-derived; inside the snow line, any mass → :terrestrial.

  The one gradual channel that already exists is disk-mass → star viscous
  accretion (mdot = disk_m · dt/t_visc, capped 5%/tick). That's the template to
  imitate everywhere else.

  Do we need research?

  Mostly no — ~80% is already on hand, but there's one real gap. Present and
  cited: Bondi capture radius, Roche/Eggleton, wind Ṁ law, viscous supply, core
  accretion + critical core mass. Missing: the actual rate laws —
  Bondi–Hoyle–Lyttleton accretion rate and the sink-particle
  accretion-efficiency schemes (how Federrath/Bate/Krumholz codes drain a gas
  particle over time and only remove it below a threshold). Your "split +
  transfer-until-stable + threshold" intuition is, in fact, exactly how modern 
  sink-particle accretion works — so it's well-grounded; we just need to pin the
  rate formulas and then spec it.

  Two decisions shape a big effort, so let me confirm before diving in:

● User answered Claude's questions:
  ⎿  · The physics is ~80% on hand; the gap is the rate laws 
     (Bondi-Hoyle-Lyttleton accretion rate, sink-particle accretion-efficiency /
     particle-draining schemes). How much research do you want? → Focused 
     research note
     · Your 'split body on phase transition, then transfer until stable, 
     terminate below threshold mass' maps onto the standard sink-particle 
     accretion model. Which primitive should the spec build? → Rate-limited sink
     accretion + donor-debit

● Locked: focused research note on the rate laws, then a spec building
  rate-limited sink accretion + donor-debit. Let me record the review verdict
  and launch the focused research.

  Ran 1 shell command (ctrl+o to expand)

● Skill(deep-research)
  ⎿  Successfully loaded skill

● Workflow(dynamic workflow: deep-research)
  ⎿  /workflows to view dynamic workflow runs

● The review is complete and the focused research is now running (5 parallel
  search angles → fetch → adversarial verification → synthesis). Summary of
  where we are:

  Confirmed: all-or-nothing is the current documented model; the 1000-M⊕
  "terrestrial" is caused by whole-parcel resolution (~670 M⊕/parcel) +
  whole-body merge/capture + no donor-debit channel + a location-only
  planet-type with no mass ceiling. Your instinct maps onto the standard
  sink-particle accretion model, which is well-grounded in the literature.

  In flight: a cited research note on the rate laws (Bondi–Hoyle–Lyttleton rate,
  sink-particle accretion/draining schemes from Federrath/Bate/Krumholz,
  Roche–Ritter transfer, rate caps to prevent runaway).

  When it lands, I'll:
  1. Write it to docs/research/physics/rate-limited-accretion-mass-transfer.md
  (citations, SI equations, the sink-accretion recipe, runaway caps).
  2. Draft docs/specs/gradual-mass-transfer-realspec.md building rate-limited 
  sink accretion + donor-debit — a body accretes surrounding gas at a physical
  rate, the donor parcel shrinks each tick (new mass-flux-out channel), and is
  reaped only below a floor mass; plus a mass ceiling / re-derived planet-type.
  It'll reconcile with the "never returns to :nebula" invariant and slot into
  the roadmap with the existing disk→star viscous accretion as the proven
  template.

  I'll take it from here once the research completes — you'll get the note and
  the spec.

✻ Crunched for 2m 54s

● Dynamic workflow "Deep research harness — fan-out web searches, fetch sources,
adversarially verify claims, synthesize a cited report." completed · 3m 25s
  ⎿  You've hit your session limit · resets 4pm (America/Chicago)
     /usage-credits to request more usage from your admin.

✻ Brewed for 21m 3s