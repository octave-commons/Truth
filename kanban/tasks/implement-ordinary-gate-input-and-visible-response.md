---
uuid: "5e9eac68-7674-4318-bb8d-781b7736d0b6"
title: "Implement ordinary Gate input and its visible local response"
status: "incoming"
priority: "P1"
points: "5"
labels: "feature, gates, input, rendering, finite-milestone"
parent: "59da3d85-c9b6-488f-b33b-5442c3e784a4"
epic: "embodied-character-voxel-mode"
dependency: "32e1eaea-609b-4ba9-988c-3e51e85c384d"
design: "docs/designs/finite-native-gate-energization.md"
category: "tasks"
type: "task"
created_at: "2026-10-08"
---

> Design: docs/designs/finite-native-gate-energization.md

## Context

The finite milestone needs ordinary native input, a real selectable Gate and a
visible response from the same live device state. The domain operation and
supported scene are separate prerequisites. This card consumes their contracts;
it does not supply a new world, scene initializer or Gate decision owner.

The selected source base is `42cd8daafc73c2e5f3a85033a44eac4ca372d463`.
The design was admitted at that head under PR12's completed planning review,
but this new Incoming card requires its own planning admission. Source comparison
found that the existing line pass uses `glLineWidth 1.5`, while the native core
context requires the supported width `1.0`. The finite visual response can use
the existing position/RGB/size line format and body material; held motion trails,
per-vertex opacity, Kepler, focus and demo changes are not imported.

This refinement is **five points**, rather than the design's initial three-point
estimate, because it includes an actual GL error/pixel regression for the existing
pass as well as input and projection contracts. Split again if review identifies
a concrete scope beyond five points; do not add unrelated work to the batch.

## Outcome

An ordinary unmodified E press on the selected live Gate queues one bounded
request through the existing IntentAtom. Current physical positions determine
the domain result. The existing renderer shows a cold device becoming persistently
energized and the HUD says **"Gate energized · no link"** only for its accepted
live state. Range and explicit refusal feedback make the approach understandable.

## Scope

- Add E handling in the existing GLFW PRESS path. Define and test world-input
  eligibility from both desired and applied capture state: manual camera mode,
  no active UI domain, `:ui/cursor-free?` false, and existing `:ui/applied-cursor`
  equal to `GLFW_CURSOR_DISABLED`. Unknown or not-yet-applied capture refuses
  world input. Release, repeat, modified keys and captured input enqueue nothing.
  This is adapter eligibility; camera mode is not a domain range predicate.
- Retain run/history, EID and immutable device identity from the actual picking
  observation in qualified selection metadata, while preserving the existing
  local-EID selection for legacy consumers. On E, verify that qualified identity
  still matches, then observe the current local revision and tick and allocate
  one operation identity. Freeze the complete payload once for the serial domain
  adapter. A replacement device cannot be recaptured under an old selection.
  Missing/stale selection produces explicit feedback and no world command;
  identity/range changes after queueing remain domain consumption refusals.
- Project actual Gate components into a visible `:body` core carrying its real
  entity identity, plus a cold/energized aperture and ring using existing shape,
  material and line paths. A selectable invisible proxy, fabricated matter-state
  or notification-only prop is insufficient. Keep physical/render-frame conversion
  at the existing boundary, with the canonical projection/picking paths.
- Extend existing controls/inspector HUD surfaces with physical distance, inclusive
  interaction range, E affordance and last refusal. Distinguish an energized local
  device from a link or destination. Document ordinary Tab selection and the three
  C presses that restore manual movement while retaining selection, followed by
  locking the cursor for world input. Consume the scene's validated camera defaults.
- Correct only the existing line-width boundary to `1.0`, retaining the legacy
  line format. Add a real native regression that executes that pass in the actual
  forward-compatible core context, checks GL errors and observes rendered pixels.
  Give this test an explicit invocation and strict-analysis coverage using current
  dependencies; do not copy the held branch's complete alias/config/shader changes.
  The regression's line vertices retain `:size` for this base's legacy buffer.

## Non-goals

No scene construction, physics or camera-default writer, domain state/event
decision, second renderer, alternate queue, receiver, connection or traversal.
No natural Gate generator, agency award/charge, force/energy transfer, avatar or
civilization. No motion trails, per-vertex alpha, fullscreen/menu overhaul, focus
key repair, reset shortcut, automatic aim, teleport or new game mode. This card's
native GL regression is a renderer-boundary test, not the final live Gate slice.

## Acceptance criteria

1. Production callback/adapter tests prove exactly one immutable request for an
   eligible unmodified E PRESS, and none for RELEASE, REPEAT, modifier or actual
   UI-capture states, including follow mode with both UI flags false and a manual
   mode change whose cursor capture has not yet applied. Operation identity is
   allocated once per press. Missing
   selection gives explicit feedback; stale selection is never replaced by a
   nearer Gate. Queued requests retain observed identity/revision and use the
   consuming physical positions through the registered domain operation.
2. Selecting the actually drawn core with the existing picker binds the same
   device and world as the request. Three ordinary C presses restore manual
   thrust without dropping selection. Cursor/UI state guidance makes the valid
   input route reproducible; camera movement cannot satisfy physical approach.
   Replacement under the same local EID before E gives stale-selection feedback,
   while replacement after queueing gives the domain refusal. A distinct new
   press on the same qualified energized device observes revision 1, so it can
   report already energized without replaying an old revision-zero payload.
3. Pure projection tests cover cold, energized, refused, replaced and absent
   devices, correct frame/scale conversion and actual body/line formats. Cold
   and energized states have a substantial persistent visual contrast. Removal
   produces no device projection or stale success badge; retained history alone
   never supplies current operability. No renderer-local state can fake success.
4. HUD range is derived from the designated Spark and Gate's physical positions,
   not orbit zoom, camera focus or cached projection coordinates. Boundary and
   out-of-range feedback match the domain result; only accepted local state shows
   "Gate energized · no link". No connected/travel or destination-view wording.
5. The actual corrected line pass produces nonempty visible pixels with no GL
   error in the forward-compatible context. The baseline must demonstrate the
   relevant unsupported-width failure through that path before GREEN; a copied
   success, mock-only call-count check or pure projection assertion is insufficient.
   Preserve exact source, context and driver identity plus all failed attempts.
6. Full repository tests, six strict gates and existing ownership/architecture
   checks pass on the candidate. Test files and the explicit native invocation
   are actually included in analysis; an unexecuted test alias supplies no proof.

## Verification

After canonical planning admission and lawful In Progress, author and preserve
meaningful RED for the production input/selection and pure projection/HUD seams.
Before production repair, run the real line-pass RED under its own separately
reviewed finite process/display/storage lease; preserve an error/unsupported
context as a failure rather than assuming the expected defect was reproduced.
GREEN includes focused results, the full `clojure -M:test` suite and
`bin/analyze --strict` through the canonical Rheos gate, plus the separately
released real GL test. Record commands, counts, exits, captures and cleanup.

Measure any changed per-frame projection path with matched baseline/candidate
inputs, outputs and bounded workload after review. Disclose time/allocation costs
without claiming FPS or 60-Hz attainment. The final native owner must still prove
ordinary approach, outside-range refusal, one accepted E operation, persistent
visible before/after response and no second accepted event in the same live run.

## Risks

An attractive line-only prop cannot be picked. A stale cached identity or render
distance can accept the wrong device. UI cursor state can consume E or leave
movement in follow mode. A pure projection pass can hide an invalid GL command.
Keep all four boundaries explicit and test the actual owners. Broader visual
polish and unrelated native repairs belong to later work.
