---
uuid: "32e1eaea-609b-4ba9-988c-3e51e85c384d"
title: "Implement supported live Gate scene and camera lifecycle"
status: "incoming"
priority: "P1"
points: "5"
labels: "feature, gates, native, finite-milestone"
parent: "59da3d85-c9b6-488f-b33b-5442c3e784a4"
epic: "embodied-character-voxel-mode"
dependency: "63cc75c2-70d5-4e2c-829d-38208e9714c0"
design: "docs/designs/finite-native-gate-energization.md"
category: "tasks"
type: "task"
created_at: "2026-10-08"
---

> Design: docs/designs/finite-native-gate-energization.md

## Outcome

One supported, explicitly authored live ECS scene starts with the existing
physical Spark outside a real dormant Gate's interaction range. Ordinary Spark
thrust advances that same world through the registered physics and integrator;
releasing input preserves the existing delayed damping behavior. Scene camera
defaults survive launch, reset, selection and deselection. The default nebula
route retains its existing behavior.

This is an Incoming implementation proposal. Review this card and satisfy the
canonical Ready/In Progress gate before RED authoring or implementation. The
dependency supplies the reviewed Gate identity, provenance and local-state
contract; this card consumes that contract rather than defining another one.
It does not deliver the E interaction, energized projection or native acceptance
owned by the other finite-milestone slices.

## Grounding and selected base

- [Finite Gate design](../../docs/designs/finite-native-gate-energization.md),
  originally admitted at immutable `42cd8daa` with SHA256
  `70d4888511c751c2da46e6c535930a496d6579efad1409aa8a830fe935aecac8`.
  The linked current refinement and this card require their own planning review;
  the historical hash does not describe the current file's bytes.
- [Scope and source evidence](../../docs/notes/2026-10-08-finite-native-gate-scope.md)
  and [planning parent](specify-finite-native-gate-energization.md).
- [Concrete source-base evidence](../../docs/notes/2026-10-08-finite-gate-implementation-base.md).
- Future implementation source base:
  `42cd8daafc73c2e5f3a85033a44eac4ca372d463`. The Foresight source-only audit
  `.ημ/diagnostics/truth-vertical-slice/implementation-base-compatibility-01/REPORT.md`,
  SHA256 `b62ca0b6ae1d52f10f5720cb660585c4e33fc1644e0166e316a4faff2a79e59b`,
  compares that base with native `5ce5deca`. No held native composition, PR36,
  clock, construction or active-influence change is presumed merged or required.

## Scope

- Consume the dependency's closed manifest/provenance and Gate component
  contracts. Allocate a fresh run/history instance and fresh Spark/device
  identities for each launch. Instantiate one real tick-zero occurrence that
  references the authored manifest; do not counterfeit earlier simulated events.
- Extract only the validated empty-ECS/ledger initialization shared with
  `domain.genesis.bootstrap`. Construct the scene's two physical entities through
  the existing ECS allocator. Do not seed and discard a nebula or pass zero gas
  through nebula-specific pacing and mass division.
- Use the reviewed design's fixed physical settings: Spark at rest at the origin;
  Gate at `1e14 m * camera-forward(-90 degrees, -20 degrees)`, mass `1e20 kg`,
  outer extent `5e12 m`, interaction radius `1.5e13 m`; `dt=1e6 s`, adaptive
  pacing false, thrust displacement `D=1e11 m/tick`, retention `.97`, ordinary
  gravitational constant, and zero observer-halo/dark-matter mass factors.
  Preserve the existing Spark mass/radius owners and the full registered systems.
- Add the supported `clojure -M:dev --scene gate-local` selection to the existing
  launcher. Its continuation uses `genesis/tick-world` and a bounded scene
  objective/observer adapter. It preserves the world and its identity through
  ordinary steps and does not adopt zero-matter arc termination or inactive-world
  replacement. Unknown scene selections fail explicitly before world publication.
- Admit validated scene camera defaults through the existing option/reset
  owners: scale `1e15 m/ru`, orbit `.01 ru`, scene minimum `.002 ru`, yaw `-90`,
  pitch `-20`, look sensitivity `.1 degrees/pixel`, zoom sensitivity `5`.
  Preserve those defaults separately from temporary selection constraints.
  Include the render loop's per-frame `:zoom-min` overwrite/removal, not only
  lifecycle options, R/reset and menu deselection. A selected body's ordinary
  geometric minimum still applies; deselection restores the scene minimum.

The existing source seams are `bootstrap.clj:195-257`, `genesis/tick.clj:209-216`,
`arc.clj:282-300`, `infra/dev/server.clj:45-58`,
`infra/dev/window/lifecycle.clj:22-41,76-93`,
`infra/render/input.clj:141-155`, `infra/menu/widgets.clj:204-209`, and
`infra/dev/window/loop.clj:385-388` at the selected base. These are edit locations,
not evidence that the new scene already works.

## Non-goals

No new ECS, renderer, generic entity-construction framework, physics integrator,
force law, camera mode, world-replacement fallback, runtime teleport, momentum
reset, fake matter-state/planet tag, identity-ticked fixture or natural-history
claim. No discovery reward, resource-price invention, biological actor,
civilization simulation, receiver, connection, traversal, save/load shell or
full-genesis prerequisite. Do not add E dispatch, Gate outcome settlement,
energized visuals or a new native input driver in this card. The separate input/
projection or native scope owns the tiny GL line-boundary repair and actual GL
test identified by the base audit. No import of the 32 held native source paths.

## Acceptance criteria

1. The complete scene manifest and numeric configuration pass named boundary
   validators before a world is returned or published. Missing, nonfinite,
   nonpositive where prohibited, duplicate or contradictory identity/provenance
   data refuse without a partial published world. Authored declarations remain
   visibly distinct from observed simulation history.
2. Each launch has a fresh qualified run/history identity, Spark identity and
   Gate identity, distinct live EIDs, exactly one designated Spark and one Gate,
   and exactly one scene-instantiation occurrence. Reusing a template does not
   reuse the run identity or label authored declarations as newly observed
   simulated construction. Initial agency is zero; no discovery, agency or
   resonance reward is emitted.
3. The constructor uses shared empty initialization and the dependency's Gate
   schema. Both physical entities carry the columns required by the actual map
   and SoA integration paths, including body kind; neither receives fabricated
   matter-state just to satisfy rendering or stellar classification. Initial
   separation exceeds the interaction radius and body extents do not overlap.
4. Actual registered ticks preserve finite state and the scene identity with an
   empty matter population. Map and SoA route tests exercise real prediction,
   integration and frame/recenter handling. A bounded held-thrust trace enters
   the reviewed range; a release trace clears input and measures the pending
   channel/coast without teleportation, velocity cancellation or force removal.
   Assertions account for actual gravity/frame effects. The design's simplified
   900/1200-tick arithmetic is a derived reference, not an exact full-pipeline or
   wall-time oracle; freeze justified tolerances before claiming qualification.
5. Explicit scene launch reaches that constructor and real continuation path.
   Repeated steps do not stop for zero matter, switch to identity ticking,
   silently replace the world with a nebula, change its qualified identity or
   mask an invalid scene. Existing error reporting and owned window shutdown
   remain in force. Unknown launch options/selections do not start a service.
6. Launch, R/reset, scroll, body selection, ordinary C cycling and deselection
   preserve the scene defaults and applicable geometry constraints. Tests cover
   the render-loop frame after deselection and the initially unselected frame,
   so neither restores the legacy `10 ru` floor accidentally. The existing
   follow-selection to manual route is three C presses and retains selection;
   following the Gate must not be counted as physical movement.
7. Omitting the scene option preserves the existing nebula launch, defaults,
   seeding/physical initialization, continuation and camera behavior. Tests
   compare deterministic state with explicitly accounted fresh UUIDs, not a
   blanket whole-world equality assumption or normalization that hides changes.
8. This card leaves Gate energization policy/settlement and visible acceptance
   to their owners. Its headless/source evidence is never labeled a completed
   native player-to-Gate interaction.

## Verification

After review and canonical implementation admission, author meaningful RED laws
and integration tests before changing production: malformed manifest/config,
fresh versus duplicated references, current bootstrap's nebula coupling,
empty-matter continuation, actual registered map/SoA movement and release, scene
option rejection, lost camera defaults at each existing owner, and unchanged
default-route controls. Separate missing-entry-point failures from behavioral
failures and accidental fixture/compiler errors; preserve the actual output and
RED checkpoint before GREEN.

Run focused tests against the implemented paths, registry/single-writer and
architecture checks, then the canonical full `clojure -M:test` and all six
`bin/analyze --strict` gates on the final candidate. Prior sibling results do
not qualify these bytes. Root coordinates each bounded JVM/native resource slot;
card drafting grants no execution authority.

For changed tick/continuation or per-frame camera paths, prepare a bounded
before/after cost comparison using the actual changed functions, matched
reproducible inputs and recorded outputs. Use the repository benchmark path
where it measures the change, otherwise name the small required adapter before
running it. Preserve raw samples, source/fixture pins and time/resource limits;
state intended scene-output differences and unchanged default-route oracles.
Do not turn a domain-only timing into a camera-cost result, nor claim FPS from
headless fixtures. Separately admitted native verification will demonstrate the
whole milestone after the input/projection slice is ready.

## Risks and sizing

Five points assumes reuse of the reviewed Gate contract and current allocator,
registered systems, launcher and camera owners. The main integration risks are
hidden nebula-only assumptions in shared bootstrap/summary, map/SoA eligibility
differences, delayed thrust during release, and per-frame selection overwriting
scene settings. Preserve concrete failures and refine/split before expanding
scope if these require a general construction service, new physics or camera
framework. The existing native composition is evidence, not an implicit source
dependency or a review-qualified substitute. Canonical dependency/WIP checks and
implementation-card review remain prerequisites to In Progress/RED.
