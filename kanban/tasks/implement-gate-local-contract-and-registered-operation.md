---
uuid: "63cc75c2-70d5-4e2c-829d-38208e9714c0"
title: "Implement the local Gate contract and registered operation"
status: "incoming"
priority: "P1"
points: "5"
labels: "feature, gates, finite-milestone"
parent: "59da3d85-c9b6-488f-b33b-5442c3e784a4"
epic: "embodied-character-voxel-mode"
design: "docs/designs/finite-native-gate-energization.md"
category: "tasks"
type: "task"
created_at: "2026-10-08"
---

> Design: docs/designs/finite-native-gate-energization.md

## Context

This is the first implementation slice of the finite native Gate design admitted
at immutable source base `42cd8daa`, whose original design SHA256 is
`70d4888511c751c2da46e6c535930a496d6579efad1409aa8a830fe935aecac8`.
The linked current design refinements and this new card await planning review;
that historical hash is not a claim about the current file's bytes.
Its grounding is `docs/notes/2026-10-08-finite-native-gate-scope.md` and the
design's cited endpoint proposal. The selected implementation source base is
`42cd8daafc73c2e5f3a85033a44eac4ca372d463`. Earlier source proposals compared
other revisions; their held native/input fixes are not implicitly imported.
This Incoming card is a planning artifact, not implementation admission.

The finite milestone remains one controlled player reaching a real Gate,
interacting through ordinary input and receiving a visible effect in the live
ECS. This slice supplies the actual domain operation consumed by the later
scene and input/projection slices. It does not complete the native milestone.

## Outcome

A validated local Gate operation executes through the existing registered ECS
fan-out and real event ledger. A valid request changes one existing device from
`dormant/revision 0` to `energized/revision 1` exactly once. Accepted and refused
outcomes bind the same run, actor, device and operation as the request; replay
cannot invent a new action, event, reward or receiver. Local energization never
claims a connection or traversal.

## Scope

- Define closed, named Malli contracts for authored initial-condition provenance,
  run/history and device identity, local Gate state, request and outcome. Preserve
  the distinction between authored declarations and executed simulation events.
  Keep pure shapes/decisions in `law/` and `domain/`, using `.cljc` where practical;
  allocate identities at the existing host/construction boundary, not inside the
  pure decision law.
- Implement the pure law and a serial domain request entrypoint that validates
  and records a world-level command for the existing `IntentAtom` path. This is
  a command batch, not a second host queue or direct device mutation.
- Add the Gate components, explicit registry reads/writes and actual
  `:gate-local` system assembly in the existing genesis fan-out. The owner reads
  one frozen physical snapshot and processes the batch FIFO with accumulating
  local Gate state/results. It alone writes Gate-local state and the dedicated
  live-Spark outcome component. Name world-command/ledger reads in the function
  contract because registry reads describe component types.
- Publish the owner-decided outcomes through the existing event ledger
  immediately after the fold and before `materialize-lifecycle`, then retire
  processed commands. The publisher performs no second range/admission decision.
  Resolve replay through retained outcomes and current batch results, without
  adding a generic command framework, retry service or absent-actor carrier.

The concrete integration seams are `src/domain/ecs/components.clj`,
`src/domain/ecs/registry.clj`, `src/domain/genesis/systems.clj` and
`src/domain/genesis/tick.clj`, with the existing sculpt command and commitment
publication paths as precedents. Their existence does not itself prove the new
Gate system is registered, consumed or published; that is part of this outcome.

## Non-goals

No authored scene/launcher/bootstrap implementation, camera defaults, GLFW E
callback, selection/picking, renderer/HUD, native harness or capture. No receiver,
connection, traversal, natural Gate generation, civilization, biological avatar,
discovery reward, modeled energy transfer, resource price, cooldown or save/load.
No position, velocity, mass, radius, observer, agency, resonance or clock writer;
no integrator change, second world, serial simulation barrier or physics bypass.
Do not import held PRs or enlarge the fixed milestone or its three-worktree
resource inventory. Scene, input/projection and native verification remain their
separately scoped consumers.

## Acceptance criteria

1. Named validators reject malformed identities, provenance, requests and local
   states at their boundaries without publishing a partial command or device.
   Authored technology/construction remains tagged as an initial declaration;
   this operation emits no fabricated discovery, construction or reward event.
2. Requests bind run/history identity, Spark identity, Gate EID plus immutable
   device identity, expected local revision, observed tick, operation identity
   and verb. Equal local EIDs in different runs are not interchangeable. A future
   observed tick refuses; older requests use the current consuming snapshot.
3. Acceptance requires the designated live Spark, matching live device,
   `dormant/revision 0`, local readiness, finite physical positions and inclusive
   center distance `d <= R`, with finite positive `R` in metres. Tests cover exact
   range, just outside, missing/nonfinite geometry, changed identity and target
   loss between recording and consumption. No camera/focus value or nearest-target
   fallback can satisfy physical range. Refusals preserve Gate and unrelated
   state; a distinct later request may succeed after conditions change.
4. Only acceptance advances local revision `0 -> 1`. Refusals/replays never
   advance it. Exact operation/payload replay, including prior refusal, returns
   the original outcome before current-revision checking and emits no second
   ledger event. Conflicting reuse fails integrity without replacing history.
   A distinct stale expected revision refuses before fresh/already evaluation;
   a new request observing revision 1 reports already energized. Two requests
   in one batch cannot both accept revision 0.
5. A missing Gate can yield a refusal on the live requesting Spark; no component
   is written on a fabricated/dead Gate. A missing Spark at serial admission
   refuses without recording a command. Loss/replacement of that bound Spark
   before the frozen decision snapshot, or a missing decision for a live actor,
   fails the fold visibly without Gate/outcome writes or invented cancellation.
   The existing error guard retains drained pre-tick `w0` and its request,
   records the error and pauses ticking; no autonomous retry or retirement.
6. Successful post-fold publication precedes lifecycle reaping and records the
   operation, actor, device, consuming tick and decided result exactly once.
   Retirement follows publication. Publication failure cannot expose a
   component-only success: the existing failed-fold path retains pre-tick `w0`
   and requests. Actor/Gate reaping after a valid decision cannot retroactively
   cancel its historical result or resurrect the entity. History alone is not
   proof that a removed device remains currently operable.
7. Actual registered-system and tick tests prove the new owner is wired, all
   component reads/writes are declared, write conflicts remain empty, later empty
   ticks do not replay events, and only the owner clears its stale outcome column.
   Unrelated logical state and physics/economic components are unchanged by the
   Gate operation; normal physics remains the responsibility of existing owners.

## Verification

Before GREEN, preserve a meaningful RED checkpoint against executable contracts:
named law/validator tests and the actual command -> registered owner -> fold ->
publisher/retirement route. Cover every acceptance branch, FIFO duplicate cases,
both actor-loss moments, same-fold device removal and injected publication
failure through the existing error guard. A standalone law pass or an unconsumed
test-only system is insufficient. Preserve failed attempts and exact source pins.

After canonical admission and a separate finite runtime lease, run the focused
RED/GREEN commands, the full `clojure -M:test` suite and `bin/analyze --strict`
through the configured Rheos gate, including architecture and registry ownership
checks. Record actual counts, exits, warnings and owned-process cleanup. Review
the exact implementation head under canonical pr-flow; planning approval and a
previous head's test result do not substitute for those gates.

Because registration and replay/publication touch the tick path, measure before
and after through the repository's actual benchmark route under a separately
bounded resource release. Include the ordinary no-Gate/no-request path and the
registered request/replay path with identical inputs and truthful result checks;
expose ledger/batch scaling rather than hiding a growing scan or cache. Preserve
raw timing/allocation evidence and qualification limits; infer no native FPS,
capture success or 60-Hz attainment from it. This card runs no native acceptance.

## Risks

Ledger writes inside fan-out are discarded by component-only folding; publication
after reaping can lose the outcome carrier. Identity reuse, stale requests and
two commands observing revision 0 can otherwise create duplicate transitions.
Keep the selected pre-reap publication, failed-fold preservation and replay
precedence together as this five-point domain slice. If implementation reveals
that a generic carrier, broad lifecycle rewrite or further authority is required,
report that concrete scope gap and split before adding it. Do not silently turn
this card into scene, input, rendering or wider progression work.
