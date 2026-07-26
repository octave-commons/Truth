---
category: "specs"
labels: ["specs", "static-analysis", "epic-static-analysis-cleanup", "cleanup"]
write-id: "1784985269880-0.nwudvqw98peyz99d2zh"
source: "kanban/tasks/static-analysis-splint-idiom-cleanup.md"
title: "Splint idiom cleanup"
priority: "P2"
status: "done"
estimate: "2"
uuid: "static-analysis-splint-idiom-cleanup"
created_at: "2026-07-07T00:00:00Z"
---

# Spec: Splint Idiom Cleanup

**Status:** accepted  
**Parent epic:** [`kanban/tasks/epic-static-analysis-cleanup.md`](epic-static-analysis-cleanup.md)  
**Scope:** Drive the 404 Splint style warnings reported by `clojure -M:splint` to zero (or documented, reasoned suppression) across `src/` and `test/`.  
**Labels:** `static-analysis`, `epic-static-analysis-cleanup`

***

## 1. Title and scope

This is the mechanical-idiom member spec of the `epic-static-analysis-cleanup`. It covers only the Splint linter output. Other member specs handle:

- `kanban/tasks/static-analysis-clj-kondo-cleanup.md` — clj-kondo bugs, shadowed vars, unused bindings, unresolved requires.
- `kanban/tasks/static-analysis-structural-cleanup.md` — god namespaces, mega-functions, parameter bloat, fan-out, undocumented public fns.
- `kanban/tasks/static-analysis-dead-code-cleanup.md` — unused public vars reported by clojure-lsp.
- `kanban/tasks/static-analysis-jscpd-reporting.md` — fix `bin/analyze` so jscpd emits actionable clone reports.
- `kanban/tasks/static-analysis-cljfmt-cleanup.md` — formatting consistency across `src` and `test`.

Baseline: `bin/analyze` reports **404 Splint style warnings** across **54 files**.

***

## 2. Rule inventory and counts

| Rule ID | Count | % | Category | Notes |
|---------|-------|---|----------|-------|
| `style/prefer-clj-math` | 323 | 79.95 | Math interop | `Math/*` → `clojure.math/*` |
| `style/plus-one` | 13 | 3.22 | Arithmetic idioms | `(+ 1 x)` → `(inc x)` |
| `lint/catch-throwable` | 8 | 1.98 | Exception handling | All in `src/infra/dev/window.clj` |
| `naming/lisp-case` | 7 | 1.73 | Naming | `SimpleName` → `simple-name` etc. |
| `style/multiple-arity-order` | 6 | 1.49 | Function shape | Fewest args first |
| `style/pos-checks` | 4 | 0.99 | Predicate idioms | `(> x 0)` → `(pos? x)` |
| `style/is-eq-order` | 4 | 0.99 | Test idioms | Expected first in `is` |
| `lint/fn-wrapper` | 4 | 0.99 | Function wrapper | `#(f %)` → `f` |
| `style/plus-zero` | 3 | 0.74 | Arithmetic idioms | `(+ 0 x)` → `x` |
| `lint/identical-branches` | 3 | 0.74 | Control flow | Collapse identical branches |
| `style/when-not-call` | 2 | 0.50 | Control flow | `(when (not x) ...)` → `(when-not x ...)` |
| `style/prefer-for-with-literals` | 2 | 0.50 | Collection idioms | `map #(vector ...)` → `for` |
| `style/prefer-clj-string` | 2 | 0.50 | String interop | `.endsWith` → `clojure.string/ends-with?` |
| `style/assoc-assoc` | 2 | 0.50 | Map update | Nested `assoc` → `assoc-in` |
| `style/apply-str` | 2 | 0.50 | String joining | `(apply str (map ...))` → `clojure.string/join` |
| `naming/conversion-functions` | 2 | 0.50 | Naming | `to` → `->` in conversion names |
| `lint/not-empty?` | 2 | 0.50 | Collection idioms | `(not (empty? coll))` → `(seq coll)` |
| `lint/loop-empty-when` | 2 | 0.50 | Control flow | `(loop [] (when (not x) ... (recur)))` → `while` |
| `lint/let-when` | 2 | 0.50 | Control flow | `(let [x y] (when x ...))` → `(when-let [x y] ...)` |
| `style/redundant-nested-call` | 1 | 0.25 | Arithmetic idioms | `(* a (* b c))` → `(* a b c)` |
| `style/prefer-condp` | 1 | 0.25 | Control flow | Repeated `contains?` in `cond` |
| `style/minus-one` | 1 | 0.25 | Arithmetic idioms | `(- x 1)` → `(dec x)` |
| `style/def-fn` | 1 | 0.25 | Definition style | `(def name (fn ...))` → `(defn name ...)` |
| `naming/single-segment-namespace` | 1 | 0.25 | Naming | `architecture-test` namespace |
| `naming/record-name` | 1 | 0.25 | Naming | `AABB` → `Aabb` |
| `lint/let-if` | 1 | 0.25 | Control flow | `(let [x y] (if x ...))` → `(if-let [x y] ...)` |
| `lint/into-literal` | 1 | 0.25 | Collection idioms | `(into [] ...)` → `(vec ...)` |
| `lint/if-same-truthy` | 1 | 0.25 | Control flow | `(if x x y)` → `(or x y)` |
| `lint/if-not-both` | 1 | 0.25 | Control flow | `(if (not x) y z)` → `(if-not x y z)` |
| `lint/if-else-nil` | 1 | 0.25 | Control flow | `(if x y nil)` → `(when x y)` |
| **Total** | **404** | **100.00** | | |

***

## 3. Per-category remediation strategy and examples

### 3.1 Math interop — `style/prefer-clj-math` (323)

Replace all `Math/*` interop with the equivalent `clojure.math` function. Add `(:require [clojure.math :as math])` to namespaces that need it, or use the fully-qualified `clojure.math/...` form. Most names are identical; the exceptions are noted below.

| Before | After | Notes |
|--------|-------|-------|
| `Math/sqrt` | `clojure.math/sqrt` | |
| `Math/pow` | `clojure.math/pow` | |
| `Math/sin` / `Math/cos` / `Math/tan` | `clojure.math/sin` / `cos` / `tan` | |
| `Math/PI` / `Math/E` | `clojure.math/PI` / `clojure.math/E` | Constants |
| `Math/exp` | `clojure.math/exp` | |
| `Math/log10` / `Math/log1p` | `clojure.math/log10` / `clojure.math/log1p` | |
| `Math/ceil` / `Math/floor` | `clojure.math/ceil` / `clojure.math/floor` | |
| `Math/atan2` / `Math/acos` | `clojure.math/atan2` / `clojure.math/acos` | |
| `Math/cbrt` | `clojure.math/cbrt` | |
| `Math/toRadians` | `clojure.math/to-radians` | Different name |

Examples:

```clojure
;; Before
(Math/sqrt (Math/pow x 2))

;; After
(clojure.math/sqrt (clojure.math/pow x 2))

;; Before
(Math/toRadians theta)

;; After
(clojure.math/to-radians theta)
```

Combined with `style/redundant-nested-call`:

```clojure
;; Before
(* rlof-pols-A (/ M-donor (orbital-period a M-donor 1.0)) (* delta delta delta))

;; After
(* rlof-pols-A (/ M-donor (orbital-period a M-donor 1.0)) delta delta delta)
```

### 3.2 Arithmetic idioms

| Rule | Before | After | Notes |
|------|--------|-------|-------|
| `style/plus-one` | `(+ 1 x)` | `(inc x)` | Only when `1` is the first operand |
| `style/minus-one` | `(- x 1)` | `(dec x)` | |
| `style/plus-zero` | `(+ 0 x)` / `(+ x 0)` | `x` | Remove the no-op |
| `style/redundant-nested-call` | `(* a (* b c))` | `(* a b c)` | Applies to variadic ops that are already variadic |

### 3.3 Control flow

| Rule | Before | After |
|------|--------|-------|
| `lint/let-when` | `(let [x y] (when x ...))` | `(when-let [x y] ...)` |
| `lint/let-if` | `(let [x y] (if x then else))` | `(if-let [x y] then else)` |
| `lint/not-empty?` | `(not (empty? coll))` | `(seq coll)` |
| `style/when-not-call` | `(when (not x) ...)` | `(when-not x ...)` |
| `lint/if-not-both` | `(if (not x) then else)` | `(if-not x then else)` |
| `lint/if-else-nil` | `(if x then nil)` | `(when x then)` |
| `lint/if-same-truthy` | `(if x x y)` | `(or x y)` |
| `lint/loop-empty-when` | `(loop [] (when (not x) body (recur)))` | `(while (not x) body)` |
| `style/prefer-condp` | `(cond (contains? set-a k) :a (contains? set-b k) :b :else :c)` | `(condp contains? k set-a :a set-b :b :c)` |
| `lint/identical-branches` | `((>= R 10.0) :energy-limited (>= R 1.0) :energy-limited)` | `((or (>= R 10.0) (>= R 1.0)) :energy-limited)` |

### 3.4 Exception handling — `lint/catch-throwable` (8)

All 8 occurrences are in `src/infra/dev/window.clj`. The rule warns that `catch Throwable` is too broad. Remediation options:

1. Replace with the most specific exception type that the wrapped code can actually throw (e.g., `Exception`, `RuntimeException`, `java.util.concurrent.ExecutionException`).
2. If the intent is genuinely a defensive catch-all for a top-level window loop, suppress inline with a comment explaining why any failure must be swallowed.

### 3.5 Naming

| Rule | Before | After | Recommendation |
|------|--------|-------|----------------|
| `naming/lisp-case` | `SimpleName`, `DocString`, `ComponentRef` | `simple-name`, `doc-string`, `component-ref` | Rename if these are internal schema helpers. If they are part of a public DSL, suppress with reason. |
| `naming/conversion-functions` | `ready-to-narrow?` | `ready->narrow?` | Rename if the function is internal. If public, consider suppressing or deprecating the old name. |
| `naming/record-name` | `AABB` | `Aabb` | `AABB` is a common acronym. If the project convention keeps acronyms uppercase, suppress with reason. |
| `naming/single-segment-namespace` | `architecture-test` | `invariants.architecture-test` or similar | Add a segment; suppress if the test namespace convention is intentionally single-segment. |

### 3.6 Function arity order — `style/multiple-arity-order` (6)

Reorder `defn` arities from fewest arguments to most arguments. Affected namespaces:

- `src/shape/spatial.clj` (`vec3`)
- `src/domain/physics/collision.clj` (`collision-detection-system`)
- `src/domain/regime.clj` (`regime-system`)
- `src/domain/stellar.clj` (`fusion-promotion-system`, `sink-formation-system`, and one other)

### 3.7 Predicate and test idioms

| Rule | Before | After | Notes |
|------|--------|-------|-------|
| `style/pos-checks` | `(> x 0)` | `(pos? x)` | Use the predicate when the comparison is exactly `> 0` |
| `style/is-eq-order` | `(is (= actual expected))` | `(is (= expected actual))` | Style convention in `clojure.test`: expected value first |

### 3.8 Collection and map idioms

| Rule | Before | After |
|------|--------|-------|
| `style/prefer-for-with-literals` | `(map #(vector % x) coll)` | `(for [item coll] [item x])` |
| `style/prefer-clj-string` | `(.endsWith s suffix)` | `(clojure.string/ends-with? s suffix)` |
| `style/apply-str` | `(apply str (map f coll))` | `(clojure.string/join (map f coll))` |
| `style/assoc-assoc` | `(assoc node :children (assoc (:children node) idx child))` | `(assoc-in node [:children idx] child)` |
| `lint/into-literal` | `(into [] (take 100 coll))` | `(vec (take 100 coll))` |

### 3.9 Function wrappers — `lint/fn-wrapper` (4)

Replace unnecessary wrappers with first-class functions:

```clojure
;; Before
#(file-mtime %)

;; After
file-mtime

;; Before
(fn [w] ((orbital/orbital-system 6.674E-11 0.5 0.5) w))

;; After
(orbital/orbital-system 6.674E-11 0.5 0.5)
```

### 3.10 Definition style — `style/def-fn` (1)

```clojure
;; Before
(def default-tick-fn (fn [world] ...))

;; After
(defn default-tick-fn [world] ...)
```

***

## 4. Prioritized namespace list

| Rank | Namespace | Count | Priority | Notes |
|------|-----------|-------|----------|-------|
| 1 | `src/domain/stellar.clj` | 98 | P1 | Largest hit; mostly `prefer-clj-math` in stellar evolution code. |
| 2 | `src/infra/render.clj` | 47 | P1 | Heavy math + control-flow idioms; renderer is hot path — test after changes. |
| 3 | `src/domain/planet_formation.clj` | 28 | P2 | Math-heavy. |
| 4 | `src/domain/orbital/kepler.clj` | 21 | P2 | Orbital math; ensure precision is preserved. |
| 5 | `src/infra/dev/window.clj` | 11 | P2 | `catch-throwable` plus `fn-wrapper`; affects dev window only. |
| 6 | `src/law/stellar.clj` | 10 | P2 | Math functions. |
| 7 | `src/law/mass_transfer.clj` | 10 | P2 | Math functions. |
| 8 | `src/domain/genesis.clj` | 10 | P2 | Math functions. |
| 9 | `src/domain/chemistry.clj` | 10 | P2 | `prefer-condp` + math. |
| 10 | `src/domain/gravity/barnes_hut.clj` | 9 | P2 | Performance-critical gravity; review carefully. |
| 11 | `src/domain/em.clj` | 9 | P2 | Math in EM systems. |
| 12 | `test/domain/stellar_test.clj` | 8 | P3 | Test-only. |
| 13 | `test/domain/regime_test.clj` | 8 | P3 | Test-only. |
| 14 | `test/domain/formation_test.clj` | 8 | P3 | Test-only. |
| 15 | `src/infra/inspect.clj` | 7 | P3 | `if-same-truthy` + math. |
| 16 | `src/law/ecs_dsl.clj` | 7 | P3 | `lisp-case` naming. |
| 17 | `test/domain/formation_integration_test.clj` | 7 | P3 | Test-only. |
| 18 | `test/domain/dominant_star_test.clj` | 7 | P3 | Test-only. |
| 19 | `test/domain/genesis_test.clj` | 7 | P3 | Test-only. |
| 20 | `src/domain/spatial/index.clj` | 6 | P3 | `multiple-arity-order` + math. |
| ... | (remaining 35 files) | ≤ 5 each | P3 | Mechanical per-file passes. |

**Total namespaces with findings:** 54.

***

## 5. Phased execution plan

### Phase 1 — Math interop bulk sweep (P1)

- Address all 323 `style/prefer-clj-math` findings across `src/` and `test/`.
- Add `(:require [clojure.math :as math])` where missing; otherwise use `math/...`.
- Tackle the top two namespaces first: `domain/stellar.clj` and `infra/render.clj`.
- **Exit criteria:** `clojure -M:splint` reports ≤ 81 warnings; tests green.

### Phase 2 — Arithmetic and control-flow idioms (P2)

- Fix `plus-one`, `minus-one`, `plus-zero`, `redundant-nested-call`.
- Fix `let-when`, `let-if`, `not-empty?`, `when-not-call`, `if-not-both`, `if-else-nil`, `if-same-truthy`, `prefer-condp`, `loop-empty-when`.
- **Exit criteria:** Splint reports ≤ 45 warnings; tests green.

### Phase 3 — Naming and structural rules (P2)

- Fix `lisp-case`, `conversion-functions`, `record-name`, `single-segment-namespace`, `multiple-arity-order`, `def-fn`.
- Where a rename would break a public API, add inline suppression with a reason comment instead.
- **Exit criteria:** Splint reports ≤ 15 warnings; tests green.

### Phase 4 — Predicate, test, collection idioms (P3)

- Fix `pos-checks`, `is-eq-order`, `prefer-for-with-literals`, `prefer-clj-string`, `apply-str`, `assoc-assoc`, `into-literal`, `fn-wrapper`, `identical-branches`, `catch-throwable`.
- **Exit criteria:** Splint reports ≤ 5 warnings; tests green.

### Phase 5 — Final suppression and gating readiness (P3)

- Triage any remaining warnings; suppress only with documented reasons.
- Update `docs/STATIC-ANALYSIS.md` with the Splint gating policy.
- **Exit criteria:** `clojure -M:splint` reports zero warnings (or only documented suppressions).

***

## 6. Acceptance criteria and test commands

### Acceptance criteria

- [ ] `clojure -M:splint` returns with zero warnings.
- [ ] Any remaining warning is suppressed inline with `#_:splint/disable` and preceded by a comment explaining why the idiom is required.
- [ ] `clojure -M:test` passes after every phase.
- [ ] `bin/analyze` Splint count is zero.
- [ ] `test/architecture_test.clj` still passes (no cross-quadrant imports introduced).
- [ ] Performance-critical namespaces touched by this work (`domain.gravity.barnes-hut`, `domain.stellar`, `infra.render`) are benchmarked with `bin/bench` if their hot paths changed.
- [ ] `docs/STATIC-ANALYSIS.md` is updated to reflect that Splint is now a gating tool (or zero-warning advisory).

### Test commands

```bash
# Primary gate
clojure -M:splint

# Regression suite
clojure -M:test

# Full analysis report
bin/analyze

# CI strict mode
bin/analyze --strict
```

***

## 7. Suppression policy

Splint supports two suppression mechanisms: inline `#_:splint/disable` and a project-wide `.splint.edn` configuration file.

### Inline suppression (one-off)

```clojure
;; Intentional: AABB is a conventional acronym for axis-aligned bounding box.
#_:splint/disable naming/record-name
(defrecord AABB [aabb-min aabb-max])
```

Disable multiple rules or a whole genre for the next form:

```clojure
#_{:splint/disable [naming style]}
(def SimpleName [:fn {:error/message "Expected a simple symbol"} simple-symbol?])
```

Inline suppression is appropriate for a single form that cannot be rewritten without breaking a public API or a well-established convention.

### Global suppression (`.splint.edn`)

Create `.splint.edn` at the repo root for rules that are noisy across the project:

```clojure
{;; Example only — do not disable prefer-clj-math globally; fix it.
 naming/record-name {:enabled false}}
```

Do **not** globally disable `style/prefer-clj-math`. That rule is the bulk of the work and should be fixed. A global suppression is only justified after explicit discussion.

### When to suppress instead of fix

- **Public API names:** `naming/lisp-case`, `naming/conversion-functions`, `naming/record-name` if the name is a documented public API and renaming would break downstream consumers.
- **Test namespace conventions:** `naming/single-segment-namespace` if the test namespace is intentionally single-segment.
- **Acronyms:** `naming/record-name` for `AABB` if the project convention is all-caps for acronyms.
- **Performance-critical primitive math:** `style/prefer-clj-math` if `Math/*` is used in a primitive-math hot path where `clojure.math` would introduce boxing. Measure with `bin/bench` first and document the finding.
- **Defensive catch-all exceptions:** `lint/catch-throwable` only if the code is a top-level safety net that intentionally catches any failure to keep a window/loop alive.

### Required suppression comment format

Every suppression must carry a `;; Intentional:` or `;; Suppressed:` comment explaining why the warning is not a bug. The comment must appear immediately before the `#_:splint/disable` directive.

***

## 8. Risks and mitigations

| Risk | Mitigation |
|------|------------|
| `clojure.math` functions differ in return type or precision from `Math/*` | Run the full test suite and targeted numeric tests after each namespace change. |
| Reordering multi-arity `defn`s changes dispatch or type hints | Add tests covering each arity after reordering. |
| Renaming public vars breaks downstream consumers | Keep old names as `^:deprecated` aliases for one release, or suppress instead of renaming. |
| `catch-throwable` changes alter error handling | Ensure the new exception type still catches the failures that the window loop needs to survive. |
| Mechanical fixes in `infra.render` regress hot-path performance | Run `bin/bench` before and after; keep `Math/*` only where `clojure.math` introduces boxing and is documented. |

***

## 9. References

- Parent epic: `kanban/tasks/epic-static-analysis-cleanup.md`
- Static analysis overview: `docs/STATIC-ANALYSIS.md`
- Splint configuration docs: https://github.com/NoahTheDuke/splint/blob/main/docs/configuration.md
- Clojure math API: https://clojure.github.io/clojure/clojure.math-api.html

## 10. Breakdown into ≤5-point tasks

This work has been broken down into ≤5-point sub-tasks. Each sub-task is tracked as a separate kanban card and represents one focused PR or work session. The original 13-point estimate is preserved across the children.

| Sub-task UUID | Title | Points | Covers |
|---|---|---|---|
| `static-analysis-splint-math` | Splint cleanup: math interop bulk sweep | 5 | Phase 1 — `style/prefer-clj-math` (323 findings) |
| `static-analysis-splint-arithmetic-control` | Splint cleanup: arithmetic and control-flow idioms | 2 | Phase 2 — arithmetic and control-flow idioms |
| `static-analysis-splint-naming-structure` | Splint cleanup: naming and structural rules | 3 | Phase 3 — naming, arity order, and `def-fn` |
| `static-analysis-splint-predicate-test-collections` | Splint cleanup: predicate, test, and collection idioms | 2 | Phase 4 — predicate, test, collection, and wrapper idioms |
| `static-analysis-splint-final-gate` | Splint cleanup: final suppression and gating readiness | 1 | Phase 5 — final triage, suppression, and gating docs |
| **Total** | | **13** | |

## 11. Estimate

**Story points:** 13

**Rationale:** 404 findings across 54 files is a substantial mechanical cleanup. The bulk (323, ~80%) is the highly mechanical `Math/*` → `clojure.math` replacement, but the remaining 81 findings span control-flow, naming, arity order, exception handling, and test idioms, several of which require semantic judgment rather than simple substitution. The math changes carry a real risk of numeric or return-type regressions, multi-arity reordering and public var renames can break downstream consumers, and the spec mandates per-phase green tests plus `bin/bench` review for hot-path namespaces. These risk and review factors push the estimate above a simple mechanical 8-point pass.

**Breakdown by phase:**

| Phase | Description | Points |
|-------|-------------|--------|
| Phase 1 | Math interop bulk sweep (`style/prefer-clj-math`) | 5 |
| Phase 2 | Arithmetic and control-flow idioms | 2 |
| Phase 3 | Naming and structural rules | 3 |
| Phase 4 | Predicate, test, and collection idioms | 2 |
| Phase 5 | Final suppression, gating, and documentation | 1 |
| **Total** | | **13** |

---
Triage 2026-07-10: PARTIAL — 18 splint warnings remain (from ~404). Breakdown: 8 lint/catch-throwable (loop.clj top-level error frames — likely SUPPRESS, not fix), 2 style/prefer-clj-math, 2 style/def-fn, 1 each pos-checks/plus-one/loop-empty-when/let-if/into-literal/fn-wrapper. Files: loop.clj(11), mass_transfer.clj(4), lorentz/focus/economy(1 each). This is ~1-2pt total — recommend collapsing the 5 splint sub-cards into a single residual pass.

Triage 2026-07-10: CONSOLIDATED — this is now the single splint residual card (owner call). Absorbs the former sub-cards (splint-math, -arithmetic-control, -naming-structure, -predicate-test-collections) and the gating card (-final-gate), all rejected as over-fragmentation for an 18-warning remainder. Scope: clear/suppress the 18 remaining splint warnings (8 catch-throwable in loop.clj → documented suppression; 10 idiom fixes across mass_transfer/lorentz/focus/economy) and update docs/STATIC-ANALYSIS.md splint gating policy. ~2pt.

Triage 2026-07-10: 18 warnings remain; scope collapsed to a single residual pass. Moved to breakdown to resize/re-scope to ~2pt and absorb over-fragmented sub-cards.

Triage 2026-07-10: scope collapsed to ~2pt residual (18 warnings). Estimate updated to 2. Moved to ready.
---

---
Triage 2026-07-24 — COUNT STALE, superseded by `kanban/tasks/static-analysis-splint-sweep-2026-07.md`. Splint reports **147 warnings, not 18** — the residual grew back with the voxel and M5 work. Shape of the regrowth: **112 are `style/prefer-clj-math`** concentrated in 13 files (`voxel/carve.clj` 37, `interior.clj` 33, `voxel_carve_test.clj` 15, `voxel/band.clj` 13), which is stragglers against a house convention **75 files already follow** — not a new idiom being imposed. Verified safe against the Clojure 1.11.1 source: the fns are `:inline` and `clojure.math/PI` is `^{:const true}`, so there is no hot-loop cost.

Recording for the record, since four sibling cards carry `status: rejected` and could be misread as a policy decision: `-splint-math`, `-splint-arithmetic-control`, `-splint-naming-structure` and `-splint-final-gate` were rejected as **card consolidation only** ("18-warning remainder too small to justify separate cards"). Splint was never descoped. This card remained the owner and still is.

Two findings the successor card carries that a bulk sweep would get wrong: `lint/fn-wrapper` at `src/infra/dev/window/loop.clj:90` is **not** an eta-reduction (it moves `orbital-system` from per-tick to namespace-load time) and must be rejected; and `test/infra/render/scene/voxel_test.clj:81` is a **real bug, not style** — single-arg `distinct?` always returns true, so that assertion has never tested anything.
---

---
## Superseded (2026-07-25)

Superseded by `kanban/tasks/static-analysis-splint-sweep-2026-07.md`, which did the
work. This card's count of 18 was stale — the real figure was 147, of which 112 were
`style/prefer-clj-math`.

Splint is now at **0 warnings** and is a BLOCKING tool in `bin/analyze`.

Recorded because it is easy to misread: the four `rejected` Splint child cards
(`-splint-math`, `-splint-arithmetic-control`, `-splint-naming-structure`,
`-splint-final-gate`) were rejected as **card consolidation only** — "18-warning
remainder too small to justify separate cards" — never as a decision to skip Splint.
---