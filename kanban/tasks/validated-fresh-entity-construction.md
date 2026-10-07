---
category: "specs"
labels: "feature, ecs, lifecycle, represented-life"
parent: "life-to-represented-actor-spec"
type: "task"
points: "3"
title: "Validate fresh lifecycle construction and initialization ownership"
priority: "P1"
status: "incoming"
design: "docs/designs/resolution-regimes-and-scale-coupling.md"
uuid: "validated-fresh-entity-construction"
created_at: "2026-10-07T16:46:47.602697Z"
---

> Design: docs/designs/resolution-regimes-and-scale-coupling.md §6.2

## Context

This is the second initial child of `life-to-represented-actor-spec`, following
the separate pure account kernel. Existing lifecycle creation always invokes
`spawn-clump`; its extra-component overlay cannot create a nonphysical book
without first attaching stellar state. Generic `ecs/spawn` already exists, but
constructor validation and initialization/consumption authority are missing.
The parent remains Incoming and its causal action/native acceptance unfinished.

Grounding: the [represented-life note §7.1–7.3 and §8.2](../../docs/notes/2026-10-07-first-represented-life-boundary.md#82-validated-fresh-construction-prerequisite--proposed),
[source/sizing assessment](../../.ημ/diagnostics/life-account-kernel-plan/next-boundary-assessment.md),
and [existing lifecycle frame design](../../docs/designs/multi-timescale-integration.md#34-composition-with-the-existing-write-channels).
The linked resolution design cites the nested-budget research. No biological
parameter, rate, origin condition or physical force is selected by this child.

## Outcome

The actual seven-channel stellar materializer prepares checked known fields and
uses one fresh-ID installation path. A separately tagged, closed empty-book
constructor can use that same path without stellar defaults or a second
allocator. Serial initialization/consumption authority is explicit and cannot
be used to overwrite another owner's existing cells.

## Scope

- Named Malli constructor-plan, prepared-result and authority boundaries, plus
  pure domain preparation/application and directly corresponding tests. Follow
  the exact per-field checks and compatibility limits in note §8.2. Prefer
  `.cljc` for practical portable shapes/functions; do not claim a second host
  is qualified merely from a file suffix.
- Two variants only: `:stellar-seed` and `:empty-life-book`. Existing seed
  defaults/arithmetic/frame resolution run unchanged, extras overlay afterward,
  then known final fields are validated before fresh allocation. Unknown seed
  keys retain their current behavior; an explicit target ID on the new creation
  plan is invalid and can never select an existing entity.
- Preserve arbitrary legacy extra keys/values as explicitly unvalidated payloads.
  Validate known-field overrides after overlay; do not recompute derived fields.
  Reserve `:component/life-book` against all stellar extra payloads. New finite/
  shape checks and this reservation are intentional rejection changes.
- Closed empty book: version `:life-origin/v1`, integer cursor `0`, empty
  `:first-causes`, `:accounts` and `:outcomes`, no other keys; exactly one book
  component and no physical columns. Reject any current book occurrence in the
  component/archetype indexes and any second book in the application. No repair,
  replacement or reuse; check only when such construction is explicitly requested.
- Reuse `ecs/spawn` and `put-components` for fresh IDs only; preserve existing
  running-world order. No accepted partial world on invalid preparation,
  duplicate book, collision or later-batch failure. Retain seven stellar source
  channels, six reap markers, escape event, exact frame/extras semantics.
- Extend existing registry declarations to distinguish fresh initialization,
  removal of processed request cells and existing reaping from ongoing writes.
  A construction-stage descriptor must stay outside both fan-out selectors and
  runtime dispatch; all-stage ordinary single-writer checks remain effective.

## Non-goals

No automatic empty-book request/activation, life-event scanner, persistent live
book or cursor updates, populated account/cohort constructor, settlement,
biological admission, final-parent guard implementation, natural origin producer,
new event bus, kinetics, clock, physical mass/momentum write, renderer, input,
paid Grow, avatar or Gate. No dependency on PR50 or a naturally eligible habitat.
No global component-schema framework, universal world validator, validation of
arbitrary legacy extra payloads, plugin system or rewrite of unrelated allocation
sites. No second scheduler or new source of IDs.

The later atomic settlement requires this construction capability **and**
`life-account-origin-kernel`; this constructor does not require the numerical
kernel, so no artificial blocking dependency is declared. That later proposed
five-point slice must own mandatory final-parent closure before actions, full
book/cohort schemas and atomic account/entity/outcome publication. It is not
split into a debit now and a refund later. No later child is created by this card.

## Acceptance criteria

1. Named constructor laws distinguish the two variants and validate the precise
   known final fields in §8.2, including finite vectors/scalars and documented
   known extras. Invalid known overrides, malformed plans and stellar book-key
   injection reject with no accepted world result. Unknown legacy extras retain
   their exact payload; a separate test proves the validator does not claim
   their semantic validity or reject them merely for being unrecognized.
2. Real stellar producer requests pass through the actual materializer with the
   same component values and ID/traversal order for valid inputs: current-parent
   position/velocity, one-time absolute frame offset, defaults, extra overrides,
   request draining, escape event and six-marker reaping remain intact. A valid
   angular-momentum override retains the previously computed spin; no hidden
   recomputation. Repeating a drained materialization creates nothing.
3. The closed empty-book variant produces exactly one nonphysical component on
   a fresh ID. A pre-existing occurrence (including malformed/orphaned data), two
   requested books or extra payload outside the closed schema reject without
   allocation. Fresh IDs alone are not accepted as singleton proof; no existing
   book is replaced, preferred, repaired or reused.
4. Multiple valid creations allocate distinct unused IDs and consistent
   archetypes through the existing core allocator. Explicit existing-ID plans,
   allocator collisions or a malformed later creation return no accepted partial
   world, advanced allocator or consumed first request. No input world is mutated.
5. The existing registry names the materializer's reads, fresh initialization,
   exact seven request consumptions and six reap families. Its known output set
   and named per-plan legacy-extra initialization rule grant fresh-ID access only.
   Ongoing `:writes` still participates in all-stage conflict detection; adding
   a contending ongoing writer or routing construction through fan-out is rejected.

## Verification

After planning convergence and canonical admission, commit meaningful failing
RED against executable constructor/materializer boundaries before GREEN. Use
real producer/frame controls from `formation_test.clj` and
`disk_evolution_test.clj`, actual ECS allocation/archetype results, complete
unchanged-input assertions on rejection and real registry guard controls.
Missing-namespace failures and a test-only biological tick consumer do not
satisfy this boundary. An explicitly supplied empty book is a constructor
fixture, never evidence that ordinary genesis produced represented life.

Run focused tests, the full ordinary suite and all six strict stages through
the configured gates. Since materialization is on the tick path, measure matched
before/after existing `bin/bench` workloads covering no requests and actual
stellar construction under a separately coordinated resource window. Report
any unavailable coverage or regression rather than infer speed from a green
suite. No JVM, benchmark or native execution is authorized by this planning card.

## Risks

Three points is proposed for two finite constructors and a narrow authority
extension. Arbitrary extras prevent a universal final-component validity claim;
this card expressly retains that compatibility limit. If registry checks require
a general schema/ownership redesign, re-estimate before RED instead of widening
this child. Empty-book uniqueness does not validate supplied world history or
activate the later settlement. Constructor qualification does not complete the
parent's persistent resource action, natural visibility or earned progression.
