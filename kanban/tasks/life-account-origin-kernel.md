---
category: "specs"
labels: "feature, ecology, accounting, represented-life"
parent: "life-to-represented-actor-spec"
type: "task"
write-id: "1791371246950-0.3v98v68lylrg53qusv"
points: "3"
title: "Implement the pure integer life-account and origin kernel"
priority: "P1"
status: "incoming"
design: "docs/designs/resolution-regimes-and-scale-coupling.md"
uuid: "life-account-origin-kernel"
created_at: "2026-10-07T10:48:23.887Z"
---

> Design: docs/designs/resolution-regimes-and-scale-coupling.md §6.1

## Context

The existing `life-to-represented-actor-spec` proposal identifies a three-point
pure account/origin boundary in its implementation table. This is that first
child, not a second specification owner. The parent remains Incoming and its
meaningful action, causal production, persistence and visible native acceptance
remain unfinished. Three points is a proposed estimate subject to planning
review; this card does not admit implementation.

The design links the [represented-life research and proposed contract](../../docs/notes/2026-10-07-first-represented-life-boundary.md):
§6 supplies the exact material conversion and explicitly tunable allocation;
§7 supplies identity, extent transfer and terminal accounting; §8.1 clarifies
revision/replay behavior. The [nested-budget design](../../docs/designs/resolution-regimes-and-scale-coupling.md#6-coupling-contract-aggregate--detail)
and [composition research](../../docs/research/physics/nebular-chemistry-metal-enrichment.md#31-composition-mass-conservation)
ground quantities and ownership. The allocation fractions are proposed model
prescriptions, not measured biology or proof of an eligible natural habitat.

## Outcome

Named laws and pure domain functions can validate supplied account/request data,
compute the proposed origin partition, and decide exact stock/revision/outcome
transitions without creating a world entity or applying a physical write.

## Scope

- New named Malli shapes/validators in `src/law/` and pure implementation in
  `src/domain/`, plus directly corresponding law/domain tests. Reuse existing
  elemental membership and finite mass-fraction semantics; do not turn the
  looser current composition predicate into proof that fractions sum to one.
- For supplied finite binary64 mass/composition, preserve input bits, convert
  to exact binary rationals, validate the proposed sum tolerance, multiply
  exactly and floor once into nonnegative integer picogram-C units. Implement
  the existing `C`, `A`, `S0`, `W0`, `o` prescription without changing constants.
- Pure proposed origin, caller-supplied `q/g/L` extent transfer, and terminal
  closure decisions; exact conserved stocks, export history and immutable
  operation outcomes. Extents are inputs, never generated from time or biomass.
- One account and supplied outcome history per call, folded in caller-supplied
  deterministic order when demonstrating competing operations. Account identity,
  cause references and cohort lineage are data; no allocator, event UUID writer,
  ledger scan, global book, or separate mutable store is introduced here.
- Apply §8.1: unopened expected revision zero, accepted origin revision one,
  exactly one increment for every newly accepted operation, no increment for
  rejection/replay/conflict, and no revision inferred from outcome count.

## Non-goals

No ECS component/system/registry changes, lifecycle integration, cohort
materialization, natural event producer, ecological or habitat admission scan,
physical mass/composition write, imported carbon transport, clock, rate law,
quantized timed producer, renderer, inspector, input, paid Grow, avatar or Gate.
No dependency on PR50 kinetics or proof of a naturally eligible planet. Actual
final-parent/cause/consumed-set admission and atomic world application remain
the later lifecycle/producer boundaries; a pure accepted result is not a native
birth. No generic transaction framework or cross-world identity/reconciliation.

## Acceptance criteria

1. Public input/output boundaries use named validators. Invalid identities,
   nonfinite physical inputs, unknown elements, out-of-range fractions,
   out-of-tolerance sums, negative/noninteger stocks or amounts, inconsistent
   totals and excessive extents reject without partial stock or revision change.
   Missing stock is unsupported, never silently zero. A new valid-key rejection
   returns its immutable outcome as a mandatory retention proposal separately
   from the unchanged account; malformed identity cannot claim persisted
   deduplication. The later caller must retain that outcome and supply the
   retained history on retry; this kernel does not perform persistence.
2. Binary64 edge examples establish exact input conversion and floor direction,
   including values whose rounded product would cross an integer boundary,
   subnormal/tiny inputs, and values above primitive integer range. Successful
   capped origin gives `U=0`, `B=10^10`, `S=10^13-10^10`, `W=10^15-10^13`
   integer units, total `10^15`, revision one. Zero origin extent rejects and
   reserves no account/parent slot; no product is first rounded through a double.
3. Extent transfer applies `(0,-q,g-L,q-g+L)` with `0<=g<=q<=S` and
   `0<=L<=B+g`, preserving exact total and nonnegative stocks. An accepted
   zero-extent action changes no stock but increments revision exactly once.
   A later caller-supplied request observes the running result; overdraw or a
   stale expected revision cannot spend a previously accepted balance twice.
4. Exact retry returns the original outcome with no new effect or increment,
   even after closure. Conflicting reuse leaves the original outcome intact.
   A valid-key rejected origin at expected revision zero stays rejected on exact
   retry; a distinct later request may succeed from zero to one. Outcome-history
   growth from a rejected request does not change account revision.
5. A newly accepted closure increments once, exports the complete active
   inventory, zeros active stocks and retains terminal inventory/reason as
   nonspendable history. `opening = active + exported` remains exact. A new
   request against a closed account rejects; retrying its accepted origin or
   closure returns history without reopening, second export or new effect.

## Verification

After planning review and lawful implementation admission, commit failing RED
tests against the public named laws and domain transitions before implementation.
Use independent exact expected values and conservation/replay/rejection sequences,
not copied implementation formulas or only missing-namespace failures. Preserve
the exact input-bit witnesses for rounding-boundary cases. Test returned data and
unchanged supplied input; unit accounts are fixtures for laws, not native evidence.

Then run the focused laws/domain tests, architectural guards, full ordinary
suite and all six strict analysis stages under the coordinated resource policy.
The kernel is not installed in an every-tick path in this slice; no performance
or gameplay claim follows from unit timing. Later lifecycle/producer cards must
establish production ownership, ordinary natural admission and visible results.

## Risks

Exact conversion and replay precedence are the substantive risks hidden by a
small arithmetic surface. If the kernel requires a generic persistence engine,
world scheduler or biological admission policy to pass its tests, return to
planning instead of expanding this child. Completion proves local accounting
laws only and does not complete the parent specification or make a Gate earned.

---

2026-10-07 planning intake: canonical Rheos resolves this manually authored first implementation child under life-to-represented-actor-spec, Incoming with proposed3 points. Root and independent board_scout reviewed exact integer conversion, origin partition, sequential extents, terminal export and immutable replay/revision proposal. Origin0->1, accepted zero operation increments, rejected/replayed/conflicting operations do not; valid-key rejected outcomes must be returned for mandatory later retention. Existing account stays current when historical outcome is replayed. Named law/domain functions and meaningful tests only; no ECS/lifecycle/producer/habitat/clock/render/input, and no PR50 dependency. Existing design and research links resolve; original note prefix and production/test sources remain unchanged. This is a reviewed local proposal, not hosted planning convergence, implementation admission or native life evidence.

---