---
uuid: "sink-absorb-angular-momentum-renormalization"
title: "Sink absorb-packet stores raw capture-radius angular momentum — clump-scale L births kAU disks"
status: "review"
priority: "P1"
labels: ["domain", "physics", "genesis", "stellar", "blocker"]
created_at: "2026-07-23T00:00:00Z"
source: "kanban/tasks/sink-absorb-angular-momentum-renormalization.md"
category: "specs"
estimate: 3
---

# Sink absorb-packet angular-momentum renormalization

> Formation-survival era, card 4 — the ROOT of the kAU-disk birth defect.
> Found by the `formation-placement-v2` audit (2026-07-23): the clump-scale disk
> angular momentum that spawns planets at 3,474–71,440 AU enters through the
> sink whole-parcel absorb channel, NOT through disc-identification (which is
> blameless — it only writes kinematic tags).

## Grounded integration (cite file:line)
- `domain.stellar.sink/absorb-packet` (`src/domain/stellar/sink.clj:136`) stores
  the absorbed parcel's RAW orbital angular momentum `m·(r_rel × v_rel)`
  evaluated at the CAPTURE radius — the Bondi/accretion radius, ~1.4×10⁴ AU for
  a ~1 M☉ protostar in cold gas. j²/(GM) at capture ≈ 2r ≈ 10³–10⁵ AU — exactly
  the probe's measured spawn-radius range.
- The gradual BHL gas channel ALREADY renormalizes:
  `domain.mass-transfer/disk-angular-momentum-from-radius`
  (`src/domain/mass_transfer.clj:170`) maps captured gas to a ~10 AU formation
  radius. The absorb channel never got the same treatment. Mirror that
  renormalization here — same formation radius (or the same derivation), one
  shared helper if they unify cleanly.
- Physical basis: angular momentum at the capture radius is mostly shed
  (shocks, gravitational torques) before material reaches the disk; storing it
  raw double-counts it in the disk budget. The mass-transfer channel's 10 AU
  value is the project's existing decision on what survives — be consistent,
  don't invent a second answer.
- Secondary audit finding (same agent): `disc-identification-system` tags
  relative to the WORLD'S MOST-MASSIVE star, not the nearest host — the
  `central-star` defect again. If in scope-cheap, switch to
  `classifier/dominant-attractor`; else card separately.

## Done when
- Disks fed by whole-parcel absorbs carry formation-scale (≲ tens of AU)
  angular momentum, not capture-scale: probe a world with sink absorbs and
  assert derived r-disk ≤ 100 AU (matching the `formation-placement-v2` gate
  band — the gate becomes a backstop, not the primary defense).
- The renormalization is CONSISTENT with the mass-transfer channel (same
  radius or same formula, documented).
- `bin/test` green; a test pins the absorb-packet L at the renormalized value.

## Risks
Changing disk L changes disk evolution/fragmentation timing globally — expect
different formation epochs in live runs (this is the point). Momentum
conservation: the shed L must go somewhere defensible — document that it
rides the absorbing sink's bulk (or the gas it came from), don't just delete
it silently.

## Dependencies
`formation-placement-v2` (landed: the 100-AU gate this card makes a
backstop). Together with `universal-compact-substepping` (landed) unblocks
candidate emergence.
