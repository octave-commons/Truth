---
uuid: "static-analysis-clj-kondo-cleanup"
title: "Spec: Static Analysis Cleanup — clj-kondo Mechanical Warnings"
status: "accepted"
priority: "P2"
labels: ["specs", "static-analysis", "epic-static-analysis-cleanup"]
created_at: "2026-07-07T00:00:00Z"
source: "kanban/tasks/static-analysis-clj-kondo-cleanup.md"
category: "specs"
estimate: 13
---

# Spec: Static Analysis Cleanup — clj-kondo Mechanical Warnings

> **Parent epic:** `kanban/tasks/epic-static-analysis-cleanup.md`  
> **Scope:** drive the clj-kondo portion of `bin/analyze` to zero warnings (or documented, justified suppressions).  
> **Baseline:** `clj-kondo --lint src test` reports **146 warnings + 1 info = 147 findings** across 47 source/test files.

This is a member spec of the *Static Analysis Cleanup* epic. It covers only the clj-kondo mechanical findings: shadowed vars, unused bindings, unresolved namespaces, misplaced docstrings, and related anti-patterns. Sibling specs handle Splint idioms, structural smells, dead code, jscpd, and cljfmt.

***

## 1. Why this spec exists

`clj-kondo` is the closest thing this project has to a compiler that catches real bugs: unresolved namespaces, redefined vars, shadowed core functions, and dead requires. With 147 findings, the signal is drowning in noise, and the next genuine error risks being missed. This spec makes the cleanup deterministic and reviewable.

***

## 2. Warning category inventory

| Category | Count | Level | Top files | Notes |
|----------|-------|-------|-----------|-------|
| `unused-binding` | 46 | warning | `domain.em`, `domain.genesis`, `infra.dev.window`, `infra.render`, `test/*` | Local names that are bound but never read. |
| `shadowed-var` | 43 | warning | `infra.render`, `infra.render.shader`, `infra.dev.actor-dashboard`, `domain.stellar`, `domain.integrator` | Locals shadow `clojure.core` or project vars. |
| `unused-namespace` | 15 | warning | `infra.render`, `test/*`, `domain.ecology`, `domain.ecs.tick` | `:require` entries with no usage. |
| `unused-value` | 10 | warning | `infra.render` (8), `test/*` | Return values or string literals discarded. |
| `misplaced-docstring` | 7 | warning | `infra.render` (6), `infra.render` (1) | Docstrings placed after a binding vector instead of the function name. |
| `unused-private-var` | 7 | warning | `infra.render` (3), `domain.genesis`, `domain.gravity.barnes-hut`, `domain.integrator` | Private helpers that are now dead. |
| `unused-referred-var` | 5 | warning | `test/*` | `:refer [testing]` imported but unused. |
| `unresolved-namespace` | 3 | warning | `infra.render.shader`, `test/domain.condensation-seeder-test`, `test/domain.mass-transfer-test` | Symbol uses a namespace not currently required. |
| `not-empty?` | 2 | warning | `domain.integrator` (2) | `(not (empty? x))` should be `(seq x)`. |
| `reduce-without-init` | 2 | warning | `test/domain.stellar-test` (2) | `reduce` without explicit init value. |
| `redundant-fn-wrapper` | 2 | warning | `infra.dev.actor-dashboard` (2) | `#(f %)` => `f`. |
| `unused-import` | 2 | warning | `infra.dev.actor-dashboard` | `java.nio.file.Files` / `Paths` imported but unused. |
| `used-underscored-binding` | 1 | warning | `domain.gravity.barnes-hut` | A `_name` binding is actually read. |
| `redefined-var` | 1 | warning | `infra.render` | `create-volume-program` defined twice (likely caused by misplaced docstring). |
| `redundant-nested-call` | 1 | info | `law.mass-transfer` | `(* (* x y) z)` style; flagged as info, not warning. |
| **Total** | **147** | 146 warnings + 1 info | | |

The full list of file:line locations is available by running `clj-kondo --lint src test`.

***

## 3. Per-category remediation strategy

For every category, the default action is **fix the code**; suppress only when the finding is a documented false positive.

### `unused-binding` (46)
- **Fix:** remove the binding, or use it if it was intended to be used.
- **Fallback:** rename to `_name` if the binding is kept for destructuring shape or documentation.
- **Exception:** macro-generated bindings (e.g., DSL hooks) may need a `{:clj-kondo/ignore [:unused-binding]}` annotation with a comment.

### `shadowed-var` (43)
- **Fix:** rename the local. Prefer domain-specific names (`body-count`, `shader-name`, `entity-key`) over short names that clash with `clojure.core` (`count`, `name`, `key`, `comp`, `new`).
- **Project-var shadowing:** if a local shadows another project var (`domain.integrator/mass-ws`, `domain.stellar/disk-radius`, `law.stellar/hill-radius`), rename to remove ambiguity even if the outer var is not currently in scope.
- **Exception:** intentional shadowing in a tight DSL or macro may be suppressed with a one-line justification.

### `unused-namespace` (15)
- **Fix:** remove the unused `:require`.
- **Check:** verify the namespace is not loaded for side effects (e.g., protocol extension, multimethod implementation). If it is, convert the require to a `:require [the.ns]` with no alias/refer and add a comment explaining the side effect; clj-kondo will still warn, so use a suppression.
- **Test namespaces:** many test files require `shape.spatial` or `law.ecology` and never use them; remove them.

### `unused-value` (10)
- **Fix:** the `infra.render` values are string literals that look like docstrings but were placed as body expressions. Move them into proper docstrings (or delete if duplicated). Test values are explanatory comments that should be moved to `testing`/`is` docstrings or logged.
- **Check:** ensure the value is not an implicit assertion; if it is, wrap it in `(is ...)` or `(testing ...)`.

### `misplaced-docstring` (7)
- **Fix:** move the docstring string immediately after the function name in `defn` / `defmulti` / `defmacro`, before the parameter vector.
- **Example:**
  ```clojure
  ;; before
  (defn foo [x]
    "Does a thing."
    ...)

  ;; after
  (defn foo "Does a thing." [x]
    ...)
  ```
- The `redefined-var` at `infra.render:1613` is almost certainly caused by a docstring misplaced before the second arity of a multi-arity defn; fixing the docstring should resolve the redefinition.

### `unused-private-var` (7)
- **Fix:** delete genuinely dead helpers.
- **Check:** if a private var is kept as a reference implementation, test utility, or future hook, add a docstring that says why and mark it with `^:clj-kondo/ignore [:unused-private-var]`, or convert to public API if appropriate.

### `unused-referred-var` (5)
- **Fix:** remove `testing` from `:refer` in the affected test namespaces; it is already available through `clojure.test :refer [testing]` if needed, or use the fully qualified `clojure.test/testing`.
- **Project convention:** do not bulk-import `testing` in every test file; use the standard `use-fixtures` + `deftest` pattern.

### `unresolved-namespace` (3)
- **Fix:** add the missing `:require`.
  - `infra.render.shader` uses `malli.core` but does not require it.
  - `test/domain.condensation-seeder-test` and `test/domain.mass-transfer-test` use `domain.spatial.index` but do not require it.
- **Verify:** after adding the require, run `clj-kondo` again to confirm the symbol resolves and no new `unused-namespace` appears.

### `not-empty?` (2)
- **Fix:** replace `(not (empty? coll))` with `(seq coll)` per project style and clj-kondo idiom.

### `reduce-without-init` (2)
- **Fix:** supply an explicit initial value. If the collection is guaranteed non-empty, still prefer an explicit init for readability and safety.

### `redundant-fn-wrapper` (2)
- **Fix:** replace `#(f %)` with `f` when the function is already the right arity.

### `unused-import` (2)
- **Fix:** remove `Files` and `Paths` imports from `infra.dev.actor-dashboard` if they are unused; otherwise qualify `java.nio.file.Files` at the call site and drop the import.

### `used-underscored-binding` (1)
- **Fix:** remove the leading underscore from `_self-id` in `domain.gravity.barnes-hut` because it is read.

### `redefined-var` (1)
- **Fix:** resolve after the misplaced docstrings in `infra.render` are fixed; if it remains, delete the duplicate definition or rename.

### `redundant-nested-call` (1)
- **Fix:** simplify nested `(* (* a b) c)` to `(* a b c)` in `law.mass-transfer`. This is an info-level finding, so address it in the same mechanical pass.

***

## 4. Prioritized namespace list

Tackle namespaces in this order. The ordering is based on warning count, architectural risk (hot-path / god-namespace), and the likelihood that fixing one file removes several findings at once.

| Priority | Namespace | File | Warnings | Risk / why first |
|----------|-----------|------|----------|------------------|
| 1 | `infra.render` | `src/infra/render.clj` | 28 | God namespace; render hot path; contains misplaced docstrings, shadowed `count`, unused values, dead private vars. |
| 2 | `domain.stellar` | `src/domain/stellar.clj` | 10 | Large simulation namespace; hot path; shadowed `new`, `comp`, `bound?`, and a local disk-radius shadow. |
| 3 | `domain.integrator` | `src/domain/integrator.clj` | 9 | Core tick pipeline; performance-sensitive; shadowed `key`/`comp`/`cond`, `not-empty?`, dead private var. |
| 4 | `infra.render.shader` | `src/infra/render/shader.clj` | 8 | Shadowed `name` (5×), unresolved `malli.core`, unused binding. |
| 5 | `infra.dev.actor-dashboard` | `src/infra/dev/actor_dashboard.clj` | 7 | Dev UI; shadowed `name`/`bytes`, redundant fn wrappers, unused imports. |
| 6 | `domain.genesis` | `src/domain/genesis.clj` | 6 | World bootstrap; unused bindings, dead private helper. |
| 7 | `infra.dev.window` | `src/infra/dev/window.clj` | 5 | Dev window; shadowed `meta`, unused destructured bindings. |
| 8 | `domain.em` | `src/domain/em.clj` | 4 | EM physics; unused bindings (`pos`, `rx`, `ry`, `rz`). |
| 9 | `test/*` | various | 38 | Many small fixes across tests; safe to batch by category rather than by file. |
| 10 | remaining `src/*` | various | 12 | Smaller namespaces, one or two fixes each. |

The namespace list is not a mandate to fix one file per PR; use it to batch reviews. The mechanical categories (e.g., all `unused-namespace`) can be fixed across many files in a single PR if the change is trivial and tests stay green.

***

## 5. Phased execution plan

All phases belong to **M1 — Mechanical warnings** in the parent epic. Each phase is small enough to be a single PR or a focused review unit.

### M1.1 — Correctness & require hygiene
**Focus:** fix the findings that can hide real bugs or break compilation in subtle ways.

- `unresolved-namespace` (3)
- `redefined-var` (1)
- `unused-import` (2)
- `unused-namespace` (15)
- `unused-referred-var` (5)
- `misplaced-docstring` (7)
- `redundant-nested-call` (1) info

**Exit criteria:**
- `clj-kondo --lint src test` no longer reports these categories.
- `clojure -M:test` passes.
- No new cross-quadrant imports are introduced (architecture test still passes).

### M1.2 — Dead private code
**Focus:** remove genuinely unused private vars and imports that survived M1.1.

- `unused-private-var` (7)
- Any remaining `unused-import` / `unused-referred-var` from M1.1

**Exit criteria:**
- These categories are zero.
- If a private var is kept, it has a docstring and a `{:clj-kondo/ignore [...]}` suppression with a reason.
- Tests green.

### M1.3 — Unused bindings & values
**Focus:** clean up the largest category and the most visible noise.

- `unused-binding` (46)
- `unused-value` (10)
- `used-underscored-binding` (1)

**Exit criteria:**
- These categories are zero.
- Remaining bindings intentionally kept are renamed with `_` prefix and documented.
- Tests green.

### M1.4 — Shadowed vars & idiom cleanup
**Focus:** the most invasive rename pass; do this after the tree is otherwise stable so reviews stay focused.

- `shadowed-var` (43)
- `not-empty?` (2)
- `reduce-without-init` (2)
- `redundant-fn-wrapper` (2)

**Exit criteria:**
- These categories are zero.
- Public API names are unchanged; only local bindings are renamed.
- Tests green; a short live run with the dev window shows no regressions.

### M1.5 — Verification & suppression
**Focus:** get to zero and lock it in.

- Run `clj-kondo --lint src test`.
- For any remaining finding, add a `#_{:clj-kondo/ignore [...]}` annotation and a code comment explaining the justification.
- Run the full suite: `clojure -M:test`, `bin/analyze`, and `bin/bench` if a hot-path namespace was touched.
- Add a CI note: once this spec is complete, `bin/analyze` can promote clj-kondo warnings to blocking by uncommenting/adding `clj-kondo: warnings present` to the `FAIL` array.

**Exit criteria:**
- `clj-kondo --lint src test` reports **zero warnings and zero info** (or only documented suppressions).
- `bin/analyze` advisory section for clj-kondo is empty.
- All tests green.

***

## 6. Acceptance criteria and test commands

### Acceptance criteria
- [ ] `clj-kondo --lint src test` returns **zero warnings** and **zero info**; any remaining finding is explicitly suppressed with a comment and a reason.
- [ ] `clojure -M:test` is green after every M1.x phase.
- [ ] No architecture test regressions (`test/architecture_test.clj` still passes).
- [ ] No new warnings introduced in namespaces not touched by the cleanup.
- [ ] Public API names are preserved; only local bindings and private helpers are renamed/removed.
- [ ] `bin/analyze` clj-kondo section reports no advisory findings.
- [ ] Suppression inventory is documented in this spec or in a code comment at the suppression site.

### Test commands
```bash
# 1. Core lint command (the spec's exit gate)
clj-kondo --lint src test

# 2. Full test suite (must be green after each phase)
clojure -M:test

# 3. Full analysis sweep (clj-kondo is the blocking section; rest are advisory siblings)
bin/analyze

# 4. Strict mode (also gates structural smells, but useful for final sign-off)
bin/analyze --strict

# 5. Performance check if a hot-path namespace was touched
# (stop the pm2 dev service first, see AGENTS.md)
bin/bench
```

***

## 7. Suppression policy

Use `#_{:clj-kondo/ignore [...]}` only when the alternative is worse than the warning. Every suppression must be justified in a comment on the same line or directly above the ignored form.

### When to suppress
- **Macro-generated code** that clj-kondo cannot see through (e.g., ECS DSL generated vars) — but prefer a `.clj-kondo/hooks` fix if possible.
- **Side-effect-only requires** where the namespace is loaded for protocol/multimethod extension, not for direct symbol use.
- **Intentional shadowing** in a tiny scope where renaming would harm readability (rare; document why).
- **External API shape** that the linter cannot know (e.g., a Java interop call whose class is only available at runtime).

### How to suppress
```clojure
;; Suppress a single form
#_{:clj-kondo/ignore [:unused-binding]}
(let [shape-only _texture-coords]
  ...)

;; Suppress multiple linters on a form
#_{:clj-kondo/ignore [:unused-namespace :unused-referred-var]}
(ns my.ns
  (:require [some.side-effect-ns :refer [never-used]]))

;; Suppress on the namespace itself (e.g., a side-effect-only require)
(ns ^{:clj-kondo/ignore [:unused-namespace]} my.ns
  (:require [some.side-effect-ns]))
```

### What never to do
- Do not use `{:clj-kondo/ignore [:all]}` or a broad `:clj-kondo/ignore` without listing the specific linter keys.
- Do not suppress a finding to avoid understanding it.
- Do not suppress across an entire file at the top unless the entire file is a known false-positive surface (e.g., a generated file).

### Suppression inventory
Keep a running list in this spec as suppressions are added:

| File | Linter | Reason |
|------|--------|--------|
| | | (populate during implementation) |

***

## 8. Risks and mitigations

| Risk | Mitigation |
|------|------------|
| Renaming locals in `infra.render` or `domain.integrator` breaks rendering or the tick pipeline | Change one category at a time; run tests and a short dev-window live run after each phase. |
| Removing a `:require` that was loaded for side effects changes behavior | Check every `ns` form for protocol/multimethod extensions before deleting a require. |
| Fixing a `misplaced-docstring` exposes a `redefined-var` or arity mismatch | Review the full defn after moving the docstring; add tests if the function is public. |
| Large rename pass creates merge conflicts with active physics work | Coordinate with the phase-0 physics pipeline owners; do M1.4 last and keep PRs small. |

***

## 9. Open questions

1. Should we add a clj-kondo `:config-in-ns` rule to silence known macro-generated warnings centrally, or keep per-site suppressions?
2. Does `infra.render` need a separate structural cleanup PR before the mechanical fixes, or can they proceed in parallel?
3. Once clj-kondo is clean, should `bin/analyze` treat clj-kondo *warnings* as blocking immediately, or wait until all sibling specs are complete?

***

## 10. Estimate

**Story points:** 13

**Rationale:**
- **Volume:** 147 findings (146 warnings + 1 info) across roughly 51 files — a large mechanical cleanup with a nontrivial review surface.
- **Mechanical vs. semantic:** Most categories (`unused-binding`, `unused-namespace`, `unused-value`, `misplaced-docstring`, `unused-import`, `unused-referred-var`, `not-empty?`, `reduce-without-init`, `redundant-fn-wrapper`, `redundant-nested-call`, `used-underscored-binding`) are mechanical. However, the 43 `shadowed-var` findings require local renames that need semantic judgment, and the 3 `unresolved-namespace` plus 1 `redefined-var` findings are correctness risks that must be verified rather than blindly patched.
- **Risk:** Hot-path namespaces (`infra.render`, `domain.integrator`, `domain.stellar`) are involved. Renaming shadowed locals there or moving docstrings can alter behavior if done incorrectly. Removing `:require` entries risks dropping side-effect-only loads (protocol/multimethod extensions). The architecture test (`test/architecture_test.clj`) must stay green after every phase.
- **Review load:** The namespace list is prioritized, but 51 files still means a broad PR review. M1.4 in particular benefits from focused review because renaming is invasive even when local.
- **Verification overhead:** The spec requires `clojure -M:test` green after each of the five M1.x phases, plus a final `bin/analyze` and optional `bin/bench` if hot paths were touched. Each phase adds a test cycle and lint check.

**Breakdown by phase:**

| Phase | Findings | Points | Notes |
|-------|----------|--------|-------|
| M1.1 — Correctness & require hygiene | 34 | 3 | Requires checking side-effect requires and resolving missing namespaces; moderate judgment. |
| M1.2 — Dead private code | 7 | 1 | Deleting dead helpers and remaining imports; low risk. |
| M1.3 — Unused bindings & values | 57 | 5 | Largest phase, mostly mechanical removals, but must verify nothing intended is dropped. |
| M1.4 — Shadowed vars & idiom cleanup | 49 | 3 | Most invasive rename pass; needs careful review in hot-path namespaces. |
| M1.5 — Verification & suppression | 0 | 1 | Final zeroing, documented suppressions, and CI lock-in. |
| **Total** | **147** | **13** | |

***

## 11. Breakdown into ≤5-point tasks

This work is tracked as a parent kanban card (`kanban/tasks/static-analysis-clj-kondo-cleanup.md`) and split into the following child tasks, each ≤5 story points:

| UUID | Title | Estimate | Phase | Notes |
|------|-------|----------|-------|-------|
| `static-analysis-clj-kondo-correctness` | clj-kondo: correctness & require hygiene | 3 | M1.1 | Bug-hiding categories: unresolved namespaces, redefined vars, misplaced docstrings, require hygiene. |
| `static-analysis-clj-kondo-dead-code` | clj-kondo: dead private code removal | 1 | M1.2 | Remove/document 7 unused private vars and leftover imports. |
| `static-analysis-clj-kondo-unused-bindings` | clj-kondo: unused bindings & values cleanup | 5 | M1.3 | Largest phase: 46 unused bindings, 10 unused values, 1 underscored binding. |
| `static-analysis-clj-kondo-shadowed-vars` | clj-kondo: shadowed vars & idiom cleanup | 3 | M1.4 | Invasive local renames in hot-path namespaces plus idiom fixes. |
| `static-analysis-clj-kondo-verification` | clj-kondo: final verification & suppression lock-in | 1 | M1.5 | Zero out, document suppressions, and lock CI gate. |
| **Total** | | **13** | | |