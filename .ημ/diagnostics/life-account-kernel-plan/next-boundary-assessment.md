# Next boundary after the pure life-account kernel

Assessment only, 2026-10-07. No new card, estimate admission, source change,
biological parameter, native observation or implementation permission is created.
The current four-file kernel is independent of this work and remains frozen.
The two focused JVMs authorized separately by root have finished; this assessment
uses source/document reads only.

## Recommendation

(己, p=0.94) Split out a **proposed three-point validated lifecycle creation and
ownership boundary** before attempting the proposed five-point retained-book
settlement. Section 7.2 describes one coherent end-to-end invariant, but its
current five-point row combines two independently testable changes: replacing a
stellar-only constructor boundary and introducing a persistent transaction owner.
It is unsafe to split an accepted birth's debit from entity/outcome publication;
it is reasonable to qualify the general construction prerequisite separately.
This is a sizing recommendation for review, not a claim that either next slice
is Ready.

The prerequisite has value without allocating a biological account: every
existing lifecycle spawn request can continue through the same allocator after
its existing stellar preparation yields a validated component bundle. A future
non-stellar bundle can use that same boundary without pretending to be a gas
clump. No production biology producer, second scheduler, plugin registry or
renderer is needed to establish this contract.

## Actual seams and missing guarantees

| Source anchor | Observed behavior | Consequence for the proposed slice |
| --- | --- | --- |
| `src/domain/genesis/bootstrap.clj:261–272,310–317,341–352` | Six consumed-marker columns and seven spawn-request columns are enumerated; request cells are removed, all requested entities are spawned, then consumed entities are reaped. | Reuse this one boundary. A parent can still be alive but marked consumed when a request is materialized. `ecs/alive?` alone cannot admit a biological birth. |
| `src/domain/genesis/bootstrap.clj:274–308` | Every spec is frame-resolved, then sent to `seeder/spawn-clump`; `:extra-components` are applied afterward. | Extra components do not provide a non-stellar constructor: physical fields have already been installed. Preserve the existing reanchor/frame-offset and extra-component behavior for stellar requests. |
| `src/domain/stellar/seeder.clj:31–68` | `seed-clump` builds mass, position, velocity, radius, pressure, density, temperature, luminosity, field and stellar-state columns; `spawn-clump` allocates then puts that map. | This pure map constructor is reusable for stellar preparation. A book/cohort must not acquire these columns merely to reach allocation. |
| `src/domain/ecs/core.clj:24–48,52–57,167–170` | Generic allocation and component installation already exist; despawn removes all archetype columns. They do not validate a semantic component bundle. | No new ECS world or allocator is required. A named validated construction boundary and ownership rules are missing. |
| `src/domain/genesis/tick.clj:78–100,167–176,178–207` | The frozen physics fold completes and its transient caches are stripped before lifecycle. Promotion/commitment events and then ecology phase events come later. | Retained life-event discovery necessarily starts from a later snapshot. A final-parent read can see all this-fold physical writes at lifecycle; no second physical integration pass is needed. |
| `src/domain/ecs/registry.clj:534–628` | Runtime declarations describe fan-out owners; conflicts examine all declared `:writes`, even barriers. There is no lifecycle initialization/book owner declaration. | Simply adding a lifecycle writer of every created physical column would conflict with existing integrator/structure owners. Distinguish initialization on a newly allocated ID from ongoing writes, without exempting ongoing biological writes from sole ownership. The stale introductory migration comments are not evidence of an operative exemption. |
| `src/domain/genesis/systems.clj:87–135` and `src/domain/ecs/tick.clj:160–187` | The system list dispatches frozen-snapshot writers; construction is outside that list. | A serial declaration must not accidentally add the settlement to the parallel run or allow it to allocate in a worker. |
| `src/domain/ecs/event.clj:19–35,75–99` | Explicit event IDs are supported, but default IDs are random; emit/dispatch append directly and provide no collision/dedup gate. Query helpers scan events by tick/kind. | Book cursor/index, UUIDv5 outcome identity and same-ID/different-payload rejection are new obligations, not existing event guarantees. |

## Option A: keep the entire proposed five-point row

This is cohesive only if its RED includes all of: typed non-stellar construction,
stellar compatibility, serial ownership, one surviving book/cursor/parent index,
account/entity/outcome atomicity, deterministic ordering, retries/rejections,
parent removal/material/habitat closure, and retained terminal histories. That is
more than an adapter around the new kernel. A five-point assertion is not yet
supported by an exact change surface or settled initialization/write declaration.
Do not omit the final-parent checks to make the estimate fit.

## Option B: construction prerequisite, then book settlement — recommended

**Proposed prerequisite (target three points):** a named construction-plan/bundle
law and one pure lifecycle application path. Existing stellar request producers
and their physical calculations remain unchanged. Their current frame resolver
and stellar map constructor prepare ordinary bundles; a separately tagged,
validated non-stellar bundle requires neither position nor mass. Allocation and
component installation use `ecs/spawn` and `ecs/put-components` once per accepted
creation. Validate the complete planned batch before returning a new world;
invalid input must not return partially consumed request columns, advanced IDs
or partially created entities. This describes pure-result atomicity, not durable
storage transactions.

The same slice must select the narrow declarative distinction between **initial
components on fresh IDs**, **owned updates to existing IDs**, and **reaping**.
Keep the current fan-out single-writer check intact. Declare serial ongoing
writers explicitly when added; a constructor declaration may not become a general
permission to overwrite another owner's existing cells. No broad rewrite of all
boot-time or player allocation sites is necessary merely to qualify the current
seven lifecycle request channels. If even this bounded distinction requires a
general registry redesign, refine its design/estimate before code.

Meaningful RED boundary: an explicit non-stellar bundle reaches the actual
materializer with exactly its approved columns and no stellar defaults; malformed
bundle or attempted existing-ID update produces no accepted world result;
multiple creations allocate unique IDs and update archetypes consistently;
existing absolute and parent-relative stellar requests retain their exact current
frame behavior, request draining and second-call idempotence. Existing useful
controls include `test/domain/formation_test.clj:349–439`,
`test/domain/disk_evolution_test.clj:243–280` and the single-writer check in
`test/architecture_test.clj:63–71`. Unit-created bundles are proof of this
construction contract, not proof of natural biology. Hot-path changes require
matched existing-adapter measurement under AGENTS, in a later released slot.

**Following settlement (target five points, must be re-sized against final design):**
consume the qualified pure kernel and construction boundary, give one serial
owner the book/cohort columns, retain immutable rejected and accepted outcomes,
allocate the book once on first observed life event, advance an event cursor and
first-cause index, process the specified running-account order, and validate the
complete account/entity/event result. The book has no physical columns and
survives parent reaping. There is no separate debit/refund protocol. Cursor/history
work must be proportional to new events plus tracked requests/parents, not a
per-parent scan of the whole ledger.

**Important dependency correction for a future plan:** section 8 places “boundary
closure” in the later natural-producer row, but section 7.2 requires invalid
existing accounts to retire before any action. Once the settlement can retain an
active account, final consumed/material/habitat checks and terminal disposition
cannot be deferred to an optional later producer. They must be covered by the
settlement's admission/closure contract, or the earlier slice must expose no live
account integration until that dependent guard is present. It may receive a
validated decision from a named pure guard, but the caller must prove that
validation corresponds to the final folded parent and cannot be bypassed. This
assessment does not select new habitat thresholds.

The later natural-origin producer can then own discovery-to-request conversion
from actual retained causes, with immutable proposals only. It must not write
the book, create the cohort, debit carbon, or replace the final-parent check.
Native ordinary-genesis proof, eligibility of current material, useful account
lifetime, kinetics/resource action and inspector presentation remain separate
unfulfilled boundaries.

## Dependencies and decision needed

1. Qualify/review the current pure kernel before a consumer relies on it; the
   focused pass alone is not the full gate or hosted code review.
2. Review a small amendment to the existing §7.2/§8 design selecting the
   constructor/serial declaration contract and clarifying where mandatory closure
   lives. Preserve the parent specification's unfinished acceptance.
3. Only then size/admit an implementation child through canonical Rheos. This
   assessment creates neither a parallel owner nor a new feature law.

## Exact inspection provenance

Inspected worktree HEAD at closure: `d80c83783fd2fc974b0698e60dfc1b2a41361979`.
The following source/design bytes equal that HEAD; the kernel's four pending
implementation files were not used as a substitute for an existing ECS integration.
No source, board, ledger, test, native or Git mutation was performed for this
assessment. Only this Markdown artifact was written.

| Path | SHA256 |
| --- | --- |
| `docs/notes/2026-10-07-first-represented-life-boundary.md` | `8ee8fa3de5a797895d57a474240632cf1f9dd1f3b3f7fbf7aa5b857c46b931c2` |
| `docs/designs/resolution-regimes-and-scale-coupling.md` | `2424f33cf0528e38fcce8c0f1b98e5136bc87414d0b348cee33cdf86bd524b31` |
| `src/domain/genesis/bootstrap.clj` | `15edc3ec5aee925ab80259c55cb11fa4f44308692f74e5ac18948380c68a2dd9` |
| `src/domain/genesis/tick.clj` | `d2159f6416cab06048c0b3876e495578c1f61e2b4ac4a70d59eaa49113f2e4ee` |
| `src/domain/genesis/systems.clj` | `277c975226f3e12fc15134df8129b237797f396fefc053c72d8759500bdb6225` |
| `src/domain/stellar/seeder.clj` | `518b8d07f281a8df746e555d0d556f68da2232af2f569820fd04d5a777a7c884` |
| `src/domain/ecs/core.clj` | `ed27a70001b77bad3ba2437ed38ef5b9bbf2ca8f1a7c4aa5a4b97ab10f2f0970` |
| `src/domain/ecs/registry.clj` | `de335cb32c29d14b2f2de90ceac4149331260d67379840089f2b5991652b9b5f` |
| `src/domain/ecs/event.clj` | `9f208199624dcd1bb94a40c9059dbd88b631f45380155bbdec39022803c1e1c7` |
| `src/domain/ecs/tick.clj` | `e5077815d7c35739af8bc53ca57b2fbb4f0dffb588e6c3a1f57c44914855ffd8` |
| `src/domain/ecs/components.clj` | `3c72ee71d368b1296434e22ae2870a3c661fcfc96f288f07b8be9156e03f2051` |
