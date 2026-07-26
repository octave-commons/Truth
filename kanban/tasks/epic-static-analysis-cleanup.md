---
uuid: "epic-static-analysis-cleanup"
title: "Epic: Static Analysis Cleanup"
status: "breakdown"
priority: "P1"
labels: ["specs", "static-analysis", "epic"]
created_at: "2026-07-08T02:24:29.644598782Z"
source: "kanban/tasks/epic-static-analysis-cleanup.md"
category: "specs"
estimate: 89
---

# Epic: Static Analysis Cleanup

**Status:** roadmap  
**Scope:** converge the entire Gates of Truth source tree so that every advisory tool in `bin/analyze` becomes clean, meaningful, and gating-ready. This is not a cosmetic pass; it protects the single-substrate architecture and prevents the warning budget from hiding real bugs.  
**Member specs:**
- `kanban/tasks/static-analysis-clj-kondo-cleanup.md` — shadowed vars, unused bindings, unresolved requires, docstrings
- `kanban/tasks/static-analysis-splint-idiom-cleanup.md` — `clojure.math`, `condp`, `inc`/`dec`, idiomatic forms
- `kanban/tasks/static-analysis-structural-cleanup.md` — god namespaces, mega-functions, parameter bloat, fan-out, undocumented public fns
- `kanban/tasks/static-analysis-dead-code-cleanup.md` — unused public vars, genuine dead code, false positives
- `kanban/tasks/spec-fix-jscpd-reporting-in-bin-analyze.md` — fix `bin/analyze` so jscpd emits actionable duplication reports
- `kanban/tasks/spec-cljfmt-formatting-cleanup.md` — formatting consistency across `src` and `test`

***

## 1. Why this exists

`bin/analyze` currently reports:

| Tool | Findings | Gating? |
|------|----------|---------|
| clj-kondo | 146 warnings | No (advisory) |
| structural smell | 14 HARD breaches, 81 undocumented public fns | Optional under `--strict` |
| Splint | 404 style warnings | No (advisory) |
| clojure-lsp | 100+ unused public vars | No (advisory) |
| jscpd | 51 clones, reporting fixed | No (advisory) |
| cljfmt | files need formatting | No (advisory) |

This volume is dangerous. Every warning is a candidate for masking the next real regression. The goal is to drive advisory counts to zero — or, where a warning is a false positive, to suppress it explicitly with a reason — so the tools can become blocking without noise.

***

## 2. Guiding principles

1. **Correctness first.** A warning that reveals a latent bug is fixed; a warning that is structurally required is suppressed with a comment and a `clj-kondo`/`splint` ignore directive.
2. **No mega-fixes.** Each PR addresses one member spec or one namespace; this keeps reviews small and tests green.
3. **Architecture tests remain authoritative.** No cleanup may relax `test/architecture_test.clj` or introduce cross-quadrant imports.
4. **Performance is a correctness property.** Refactoring must not regress hot paths; measure with `bin/bench` when a touched namespace is in the tick pipeline.
5. **Dead code is removed, not commented out.** If a public var is genuinely unused and has no downstream consumer, delete it. If it is a public API surface, document it.

***

## 3. Work breakdown

### Phase A — Tooling honesty (M0)
- Fix the jscpd output in `bin/analyze` so clone reports show files, lines, and duplicated tokens. **Status: implemented in this session; see `kanban/tasks/spec-fix-jscpd-reporting-in-bin-analyze.md` and `kanban/tasks/static-analysis-jscpd-reporting.md`.**
- Add a `bin/analyze --verbose` mode or per-tool summary that prints file:line context for every clone.

### Phase B — Mechanical warnings (M1)
- **Splint idiom pass:** replace `Math/*` interop with `clojure.math`, `(+ 1 x)` with `(inc x)`, `cond` with `condp` where applicable, etc.
- **clj-kondo lint pass:** remove unused bindings, fix shadowed vars, add missing requires, add misplaced docstrings.
- **cljfmt pass:** apply `bin/analyze --fix` once the other tools are stable.

### Phase C — Structural cleanup (M2)
- **God namespaces:** split `domain.stellar` and `infra.render` into coherent sub-namespaces or systems; move render programs/shaders into `infra.render.*` sub-modules.
- **Mega-functions:** decompose functions >80 lines; extract pure helpers; move the largest functions down in arity order.
- **Undocumented public fns:** add docstrings to every public var; if a var is internal, make it `^:private` or move it to a `-impl` namespace.
- **Parameter bloat / fan-out:** introduce context maps; reduce namespace imports where possible.

### Phase D — Dead code (M3)
- Audit every `clojure-lsp/unused-public-var` finding.
- Delete genuinely unused code; convert false positives (test helpers, DSL-generated vars, public API) into explicitly-marked surface area.

### Phase E — Gating (M4)
- Once counts are near zero, flip the advisory tools to blocking in `bin/analyze`.
- Update `docs/STATIC-ANALYSIS.md` with the new contract and failure modes.

***

## 4. Milestones and exit criteria

| Milestone | Entry | Exit criteria |
|-----------|-------|---------------|
| M0 — Tooling honesty | Now | `bin/analyze` prints actionable jscpd clone details; `--verbose` or equivalent exists |
| M1 — Mechanical warnings | M0 | clj-kondo warnings ≤ 10, Splint warnings ≤ 10, cljfmt clean, tests green |
| M2 — Structural cleanup | M1 | zero HARD structural breaches, zero undocumented public fns, tests green |
| M3 — Dead code | M1 | every clojure-lsp unused-public-var finding is either deleted, used, or explicitly suppressed with reason |
| M4 — Gating | M2 + M3 | `bin/analyze --strict` is clean; advisory tools become blocking in `bin/analyze` |

***

## 5. Small-gap task cards (tracked in `kanban/tasks/`)

- **jscpd-reporting-fix** — make `bin/analyze` show clone details, not just `Clone found (clojure):`.  
  Source: `kanban/tasks/spec-fix-jscpd-reporting-in-bin-analyze.md`
- **clj-kondo-shadowed-var-sweep** — address shadowed `clojure.core` vars across `src/` and `test/`.  
  Source: `kanban/tasks/static-analysis-clj-kondo-cleanup.md`
- **splint-clojure-math-sweep** — replace `Math/*` interop with `clojure.math` throughout.  
  Source: `kanban/tasks/static-analysis-splint-idiom-cleanup.md`
- **structural-god-namespace-split** — reduce `domain.stellar` and `infra.render` below HARD thresholds.  
  Source: `kanban/tasks/static-analysis-structural-cleanup.md`
- **dead-code-audit** — triage every `clojure-lsp/unused-public-var` finding.  
  Source: `kanban/tasks/static-analysis-dead-code-cleanup.md`
- **cljfmt-formatting-pass** — run `bin/analyze --fix` once the mechanical tools are stable.  
  Source: `kanban/tasks/spec-cljfmt-formatting-cleanup.md`

***

## 6. Acceptance criteria (epic-level)

- [ ] `bin/analyze` shows file:line details for every jscpd clone.
- [ ] `clj-kondo --lint src test` reports zero warnings (or every remaining warning has a documented suppression).
- [ ] `clojure -M:splint` reports zero warnings (or documented suppressions).
- [ ] `clojure -M:cljfmt check src test` passes.
- [ ] `bin/analyze --strict` passes (no HARD structural breaches, no undocumented public fns).
- [ ] `clojure-lsp diagnostics` reports zero unused public vars (or every remaining one is documented API surface).
- [ ] `clojure -M:test` is green after each milestone.
- [ ] `docs/STATIC-ANALYSIS.md` is updated with the new gating policy and suppression conventions.

***

## 7. Risks and mitigations

| Risk | Mitigation |
|------|------------|
| Mechanical refactors break physics or rendering | Change one namespace at a time; run the full suite and a short live run after each. |
| Splitting god namespaces changes public API | Keep old vars as thin `^:deprecated` aliases during transition; remove in a follow-up. |
| Suppressing warnings hides real bugs | Every suppression must carry a comment explaining why it is safe. |
| Dead-code deletion removes test utilities | Never delete test vars that are generated by DSLs without updating the generator. |
| jscpd fix is blocked by node/npx environment | Provide a fallback in `bin/analyze` that prints raw jscpd JSON or fails gracefully. |

***

## 8. Open questions

1. Should the structural-smell thresholds be tightened (e.g., warn at 300 LOC) after the initial cleanup, or are the current thresholds the right long-term policy?  
2. Should `clojure-lsp` unused-public-var diagnostics become blocking in `bin/analyze`, or remain advisory because generated DSL vars create false positives?  
3. Is there a preferred way to run `bin/analyze` in CI when some tools require Node (`npx jscpd`) and JVM (`clojure`)? Should we document a containerized invocation?

***

## Estimate

**Total epic estimate: 89 story points** (reconciled: 65 child points sum + 6 coordination/integration overhead = 71 → next Fibonacci number up).

### Child spec breakdown

| Member spec | Status | Estimate | Notes |
|-------------|--------|----------|-------|
| `kanban/tasks/spec-fix-jscpd-reporting-in-bin-analyze.md` | Implemented | 1 | Fix already done; remainder is documentation/review. |
| `kanban/tasks/static-analysis-clj-kondo-cleanup.md` | Open | 13 | 147 findings across 47 files; hot-path namespaces require careful review. |
| `kanban/tasks/static-analysis-splint-idiom-cleanup.md` | Open | 13 | 404 warnings across 54 files; high-volume but mechanical; needs numeric/semantic verification. |
| `kanban/tasks/static-analysis-structural-cleanup.md` | Open | 21 | Namespace splits, mega-function decomposition, 81 docstrings; highest risk and review load. |
| `kanban/tasks/static-analysis-dead-code-cleanup.md` | Open | 15 | 157 unused vars; per-var triage with `rg` and API-surface decisions; split into four ≤5-point sub-tasks. |
| `kanban/tasks/spec-cljfmt-formatting-cleanup.md` | Open | 2 | Single formatting pass; dependent on other mechanical passes. |
| **Child sum** | | **65** | |
| **Epic coordination / integration / gating overhead** | | **+6** | Cross-PR sequencing, merge-conflict resolution, architecture-test guardrails, final `--strict` gate and `docs/STATIC-ANALYSIS.md` update. |
| **Reconciled epic total** | | **89** | Sum rounds up to the next Fibonacci number (≥21 rule): 89. |

---
Triage 2026-07-10 progress (bin/analyze ground truth): DONE — clj-kondo 0/0 (all 6 kondo cards), structural 0 HARD/0 undoc (structural-cleanup, decompose-mega, document-privatize, rationalize-genesis, split-stellar-disc-wind), jscpd reporting fixed. REMAINING — splint 18 warnings (~1-2pt, over-fragmented across 6 cards), dead-code 312 unused-public-var (suppress-vs-delete triage, the big one), cljfmt 7 files (~2pt, now Ready), then final-validation/final-gate capstones. Epic is ~80% done by card count.

Triage 2026-07-10: ~80% complete; remaining splint/dead-code/cljfmt work tracked by children. Moved to breakdown as umbrella tracking.

Triage 2026-07-24 — THE 2026-07-10 FOOTER ABOVE IS NO LONGER TRUE. It is kept as historical record, not deleted. The tree regressed and no card was reopened:

| Tool | 2026-07-10 | 2026-07-24 |
|------|-----------|-----------|
| clj-kondo | 0 warnings / 0 errors | 50 warnings / 0 errors |
| structural HARD | 0 | **4** |
| Splint | 18 | 147 |
| clojure-lsp unused-public-var | 312 | 353 |
| jscpd clones | reporting fixed | 74 (1.86%) |
| cljfmt | 7 files | 24 files |

Root cause is not the findings, it is the gate: **every `static-analysis` CI run has failed since 2026-07-11** — eight consecutive, across `main` pushes and PRs — including PR #1 (`cae2668`), merged red. `.github/workflows/static-analysis.yml:39` runs `bin/analyze --strict` correctly; nothing requires it to pass. Two weeks of red let a genuinely broken conservation check sit on main (`src/law/field/schema.clj:195` — `#(rel-close? %1 %2)` over `(map vector ...)` receives one arg, so momentum and angular-momentum were never compared; fixed on `spark-gravity-bound-body` at `:268`).

Owner decision 2026-07-24: the regressed children (`static-analysis-clj-kondo-*` ×6, `-decompose-mega-functions`, `-structural-cleanup`, `-split-stellar-core`) **stay `done` as history**; new cards carry the work. See `kanban/tasks/static-analysis-regression-2026-07-24.md` for the full breakdown and the thirteen children.

Also recorded there, so it is not misread later: the four `rejected` Splint cards were rejected as **card consolidation only** ("18-warning remainder too small to justify separate cards"), not as a decision to skip Splint. §8 open question 2 is now answered — dead-code diagnostics stay advisory for now; cljfmt and clj-kondo warnings are promoted to blocking.

Resolution 2026-07-25 — the table above is now cleared, and the gate is a real gate:

| Tool | 2026-07-10 | 2026-07-24 | 2026-07-25 | gating |
|------|-----------|-----------|-----------|--------|
| clj-kondo | 0/0 | 50 warnings | **0/0** | errors **and warnings** block |
| structural HARD | 0 | 4 | **0** | blocks (`--strict`) |
| Splint | 18 | 147 | **0** | **blocks** |
| clojure-lsp unused-public-var | 312 | 353 | **0** | **blocks** |
| jscpd clones | reporting fixed | 74 (1.86%) | 65 (1.62%) | **blocks** above `.jscpd.json` `threshold` |
| cljfmt | 7 files | 24 files | **0** | **blocks** |

`bin/analyze --strict` exits 0. `clojure -M:test` holds at 879 tests / 15486
assertions / 0 failures. Each newly-blocking class was verified to REFUSE an injected
finding — the gate is not verified until it has refused something, and it has, four
times.

**§8 open question 2 is answered differently than the 2026-07-24 note assumed.**
Dead-code diagnostics did NOT stay advisory: they went to zero, so they are blocking
too, along with Splint and jscpd. Driving a tool to zero *before* promoting it is the
whole mechanism — a gate promoted while findings remain is a gate that gets merged
past, which is exactly what happened here.

**The root cause is now CLOSED.** `main` is a protected branch requiring the `analyze`
check with `enforce_admins: true` (applied 2026-07-25 with owner approval), and it was
verified by refusal: a throwaway PR with a red `analyze` was rejected with "the base
branch policy prohibits the merge".

**Correction to the 2026-07-24 note above:** it says PR #1 (`cae2668`) merged red into
`main`. It did not — PR #1 was `worktree-integration-seam-tests →
spark-gravity-bound-body`, and it is the only PR this repo has ever had. The actual
bypass was **33 consecutive direct pushes to `main`** by an admin between 2026-07-10
and 2026-07-21, every one with `static-analysis` red. That is why `enforce_admins: true`
was the load-bearing setting rather than an optional hardening: required status checks
alone would not have blocked a single one of those 33.

Note `main` is red on its own merits today (2 clj-kondo errors + structural HARD), so
with protection live it is unpushable until this branch merges — which is what makes it
green. Full detail: `kanban/tasks/static-analysis-regression-2026-07-24.md`.

---
