---
category: "specs"
labels: "feature, ecology, accounting, represented-life"
parent: "life-to-represented-actor-spec"
type: "task"
write-id: "1791401683501-0.i1ouvu0n38d3abzrykh"
points: "3"
title: "Implement the pure integer life-account and origin kernel"
priority: "P1"
status: "in_progress"
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

2026-10-07 implementation admission: canonical pr-flow status on planning head7e659bb4ce799ae324a2d64f8a7904888a5ecf2c now reports PASS: seven required checks, zero unresolved findings, exact-head CodeRabbit and MiMo approvals, and one completed cohort of all currently available reviewers. Canonical pr-flow Available agents law records hosted Codex account-quota source6036640524 observed11:07:51Z with UNKNOWN reset; it supplies no approval credit. This uses the current shared policy, not a user-approved local substitution or merge authority. Full independent local review fc37d1b410fe6add70dd8869abb93ffadf5290481ba388a0b078417da5276f21 additionally passed the15-file proposal. Existing grounded three-point outcome, non-goals and five acceptance clauses are accepted unchanged. Progress this child through canonical Rheos to InProgress subject to its gates; write named laws and meaningful failing tests, commit witnessed RED, then implement the pure integer account/origin kernel. Root coordinates all JVM tests around native measurement; no concurrent native load, ECS hookup, producer, habitat, rendering, paid Grow, embodiment or Gate claim. Parent acceptance remains open. No merge or auto-merge authorized.

2026-10-07 pure-kernel qualification: root accepts this admitted three-point supplied-data scope as fulfilled subject to the canonical full test and all-six strict gate. Original RED55dce629 passed laws and failed domain acceptance144/320; added regression REDd80c837 observed2 tests/62 assertions/10 failures/0 errors against preserved, resource-pinned pre-fix law/domain. Current independently reviewed four-file candidate passes33 tests/426 assertions/0 failures/0 errors. Exact hashes: law9de4ec6827ab9453fa54008e55780002e27ecde68f26d4f66b826c7bd0301810; domain ea01b902a2c2c776c2a593ed735fa493b1be6c67ab98db7e08afb9496c64db1c; law-test8a2aaa538039663e2dbc4bda81eacc7e20449e27fab5bc76fce346e45322888a; domain-test36a3bac05c70abe42d71135a38a5f610bace0549fb74d1ce741d1a01e32d17ec. These GREEN bytes are uncommitted; HEADd80c837 is a separate diagnostic RED checkpoint, not their commit. Independent source review419d36cd remains immutable; exact retry bits and locally provable account/history contradictions are covered, without global-history completeness claims. Invoke canonical InProgress-to-Review gate once under root-released resources and bounded supervision. No ECS/book/producer/habitat/clock/render/input/biological or native milestone is implied; parent acceptance remains open. Full gate outcome is pending this invocation, not preclaimed.

2026-10-07 canonical qualification retry: attempt 01 passed the full suite (939 tests, 16165 assertions, zero failures/errors) but correctly refused Review because strict analysis found eight owned Splint style warnings and formatting differences. Its complete raw evidence and reviewed source copies remain immutable. Root authorized only those eight substitutions and formatting of the four life-account files. Limited cljfmt check/fix/check completed with final exit 0; exact reverse proof shows only the eight named idiom changes and leading indentation changed, with no new scope or numerical acceptance change. Current hashes: law 22c12fb4e6cf3f1c46dee45fd770d73bc326efba662effe86123efaa9e8c72e6; domain 49848d2c0e8d3c43231a231c758bed99315e69924d728ed1f94a3ef0aff9bb4a; law test 96de33b0d20368b13846375201dc249035b0953fe2c0bef4c8a538e36c0a6eba; domain test 1390313ee327df7fcac345546ff05600ecc9b935e3f3ee8adb63d30500374665. Run the fresh canonical move gate once under the same bounded root-owned resource window. These files remain uncommitted at HEAD d80c837; this comment does not preclaim gate success. Pure supplied-data accounting only; no parent/native/biology integration claim.

2026-10-07 completed pure-kernel qualification: fresh canonical Rheos gate attempt 02 ran the full suite (939 tests, 16165 assertions, zero failures/errors) followed by all six strict stages, all passing, then printed in_progress -> review. Independent canonical readback confirms Review, 3 points. Node/PGID 1288803 exited 0 and was reaped at 16:08:25.715304Z; no owned group remains. All 322 source/config pins and the four candidate hashes were unchanged. Gate evidence binds uncommitted GREEN bytes on RED HEAD d80c837, not a false committed-source claim. Final law 22c12fb4, domain 49848d2c, law-test 96de33b0, domain-test 1390313e have full hashes in canonical-gate-attempt-02/candidate-pins.json. Root independently accepted the eight exact style substitutions and indentation-only proof; the prior independent semantic review419d36cd remains intact. Original RED55dce629 and added REDd80c837, failed oracle, pre-fix pass, focused33/426 pass, first refused canonical gate and all formatting attempts are preserved. This validates supplied-data accounting only; no ECS/book/lifecycle/habitat/clock/biological producer/native game or parent-completion claim. Prepare the authorized local GREEN checkpoint with complete evidence; no push or hosted code-approval claim yet.

Review5446390998 at ecf78775fbd8069fb86386b9d00401bf47476bab, native thread PRRT_kwDOTDahac6qCHgh/comment4210309826, identifies a locally provable close-once history contradiction. Root verified accepted close outcome revision2 with closed account revision3 passes current entry-consistent? although close increments exactly once and later operations reject without changing revision. Reopen only this accepted-close equality boundary. Write meaningful law and real apply-operation regressions first, preserve partial-history older origin/extent compatibility, rejected/other-account entries and exact valid close/retry behavior, then minimal predicate correction, full test/strict gate. This is pure account boundary work; no ECS producer, biological progression or Gate claim. CodeRabbit included review remains reserved for the corrected head after fresh dedup; no paid capacity, merge or auto-merge.

Review5446390998 close-revision correction verified. RED commit5822d2a:35tests457assertions3 failures0errors, exactly impossible greater terminal revision through law/replay/new request. Minimal GREEN adds accepted-close equality only; older nonterminal partial history and rejected/other-account entries preserved. Focused35/4570F0E. First full gate941/16196pass but cljfmt refused; raw failure preserved. One-indent formatter correction followed by fresh canonical gate02:941tests16196assertions0F0E and all six strict gates PASS in96.378s, Review admitted. Root independently verified304pins and all16observed process identities absent. Closure SHA256 f8f3a2e545bb6ee1d85c18cb20376de3a807f22a75abeca8d0cccf282e00fe59. No domain arithmetic/ECS/biological/Gate progression or native service change. Publish bounded complete evidence and fresh code review next; no merge/auto-merge.

Confirmed review5446881815/comment4210730351 duplicate-origin history finding: two accepted origins for the relevant same account are contradictory even with distinct operation counters and identical material. Root read actual law/domain plus all three new tests and whole frozen supervisor; independent author agrees. Preserve partial/empty/rejected/other-account history controls. Release exactly one focused RED red-01 at supervisor efe29bbc20f2dea55bf0adbdc94fd21b0eca1970e05424e3beeb8bb1d32136fd, manifest fec84172ed67a186282be6c9d7eeb9eaa4a24cd53298dc0d908f573d2cc57e40,316pins verified. Predict38tests508assertions4failures0errors; prediction is not observation.110swork120stotal sanitized1GiB2CPU mainJVM, owned group/start identity/raw logs/reap. Prior238native+5clock/Rheos PIDs absent. No concurrent JVM/native, retry or extension; production unchanged, retained primary untouched. Return card to InProgress for this review repair.

Observed RED for review5446881815/comment4210730351, 2026-10-07: actual focused law.life-account-test + domain.life-account-test at unchanged production4256634 ran38 tests/508 assertions/4 intended failures/0 errors in4.095704s, raw exit1. Two failures show operation-context? accepting two supplied same-account accepted origins with distinct operation ids; two show actual apply-operation replaying either contradictory retained opening instead of refusing the pair. Single/empty/partial/rejected/other-account history controls and prior terminal-revision controls pass. All316 source/preparation pins remained exact; owned2443186/2443193 absent and primary reaped, root closuref4d22833. Preserve every raw artifact. Record and commit this RED before the narrowly authorized law-only same-account accepted-origin uniqueness repair; no production change, GREEN test, full gate, native claim or push yet.

---