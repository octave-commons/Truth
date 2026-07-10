---
uuid: "static-analysis-splint-predicate-test-collections"
title: "Splint cleanup: predicate, test, and collection idioms"
status: "rejected"
priority: "P2"
estimate: 2
labels: ["specs", "static-analysis", "epic-static-analysis-cleanup"]
created_at: "2026-07-07T00:00:00Z"
source: "kanban/tasks/static-analysis-splint-predicate-test-collections.md"
category: "specs"
---

# Splint cleanup: predicate, test, and collection idioms

> Parent: `kanban/tasks/static-analysis-splint-idiom-cleanup.md`

Fix predicate, test, collection, and string idioms reported by Splint.

## Scope

### Predicate and test idioms

- `style/pos-checks` — `(> x 0)` → `(pos? x)`
- `style/is-eq-order` — `(is (= actual expected))` → `(is (= expected actual))`

### Collection and map idioms

- `style/prefer-for-with-literals` — `(map #(vector % x) coll)` → `(for [item coll] [item x])`
- `style/prefer-clj-string` — `.endsWith` → `clojure.string/ends-with?`
- `style/apply-str` — `(apply str (map f coll))` → `clojure.string/join`
- `style/assoc-assoc` — nested `assoc` → `assoc-in`
- `lint/into-literal` — `(into [] ...)` → `vec`

### Function wrappers

- `lint/fn-wrapper` — `#(f %)` → `f`, and similar unnecessary wrappers.

### Exception handling

- `lint/catch-throwable` — replace with the most specific exception type, or suppress with documented reason if the catch-all is intentional defensive code.

## Done when

- `clojure -M:splint` reports ≤ 5 warnings.
- `clojure -M:test` passes.
- Any `catch-throwable` suppression explains why the broad catch is required for the window/loop to survive.

---
Triage 2026-07-10 → rejected: consolidated into static-analysis-splint-idiom-cleanup (18-warning remainder too small to justify separate cards).
---
