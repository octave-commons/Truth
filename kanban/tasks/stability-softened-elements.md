---
uuid: "stability-softened-elements"
title: "Orbit-stability elements use the softened potential the integrator actually applies"
status: "todo"
priority: "P2"
labels: ["domain", "physics", "stellar", "multi-timescale"]
created_at: "2026-07-23T00:00:00Z"
source: "kanban/tasks/stability-softened-elements.md"
category: "specs"
estimate: 2
---

# Orbit-stability elements: use the softened potential

> multi-timescale epic, card 5. Design: `docs/designs/multi-timescale-integration.md` §6.
> The gate's math silently went stale: it assumes unsoftened Kepler "far beyond any
> softening length," but softening is now at its ceiling (~3342 AU) and exceeds
> planet-star separations by 10–100×, so the two-body elements it computes don't
> match the force the integrator applies.

## Grounded integration (cite file:line)
- `domain.orbital.stability/two-body-elements` (`stability.clj:35-70`) uses plain
  `μ = G·M` and documents the assumption `r ≫ softening`
  (`stability.clj:49-52`) — now false. Downstream periapsis/apoapsis tests
  (`stability.clj:99-103`) inherit the mismatch.
- Use the softened potential the integrator actually integrates
  (`Φ = -GM/√(r²+ε²)`; `law.stellar/softened-circular-speed` is the matching speed)
  so eccentricity/periapsis/apoapsis reflect real dynamics. Read the live
  `:sim/softening` for ε.
- This is also what `orbit-integration-regression-tests` (card 3) needs to measure
  eccentricity consistently with the integrator.

## Done when
- `two-body-elements` (or a softened variant) matches the integrator's force law;
  the docstring's invariant is enforced or removed, not silently stale.
- A test asserts elements agree with a directly-integrated orbit under softening.
- `clojure -M:test` green.

## Risks
Low. Keep the unsoftened path if any caller genuinely needs vacuum Kepler; prefer
one softened source of truth.

## Dependencies
None. Used by cards 3 and 4.

## Rescoped 2026-07-23 (velocity pairing rule)

The premise inverted after the sub-stepper landed. Two populations now live under
DIFFERENT effective laws:

- **Sub-stepped compact bodies** (`:planet`/`:gas-giant`/`:stellar-remnant`): the
  WH drift supplies the exact NEWTONIAN two-body term — the gate's unsoftened
  `two-body-elements` is now CORRECT for them (verified live: Newtonian-spawned
  planets hold e < 0.4 measured by exactly this function).
- **Euler-path bodies** (gas parcels, `:protostar` companions): still under the
  softened law — for them the unsoftened elements remain stale.

So the card is no longer "make the gate softened." It is: make the element
computation PATH-AWARE — unsoftened for sub-stepped states (current behavior,
keep), softened-potential for Euler-path bodies — or document the gate's scope
as compact-bodies-only and drop the stale docstring claim. The handoff gate
(`domain.genesis/handoff-system`) only ever evaluates candidate planets, i.e.
the sub-stepped population, so the honest minimal resolution may be a docstring
fix + a test pinning the pairing.
