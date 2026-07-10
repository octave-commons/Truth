---
uuid: "static-analysis-splint-arithmetic-control"
title: "Splint cleanup: arithmetic and control-flow idioms"
status: "rejected"
priority: "P2"
estimate: 2
labels: ["specs", "static-analysis", "epic-static-analysis-cleanup"]
created_at: "2026-07-07T00:00:00Z"
source: "kanban/tasks/static-analysis-splint-arithmetic-control.md"
category: "specs"
---

# Splint cleanup: arithmetic and control-flow idioms

> Parent: `kanban/tasks/static-analysis-splint-idiom-cleanup.md`

Fix arithmetic and control-flow idioms reported by Splint, excluding the bulk math interop sweep.

## Scope

### Arithmetic idioms

- `style/plus-one` — `(+ 1 x)` → `(inc x)`
- `style/minus-one` — `(- x 1)` → `(dec x)`
- `style/plus-zero` — `(+ 0 x)` / `(+ x 0)` → `x`
- `style/redundant-nested-call` — `(* a (* b c))` → `(* a b c)` (where not already handled by the math sweep)

### Control-flow idioms

- `lint/let-when` / `lint/let-if` → `when-let` / `if-let`
- `lint/not-empty?` → `seq`
- `style/when-not-call` → `when-not`
- `lint/if-not-both` → `if-not`
- `lint/if-else-nil` → `when`
- `lint/if-same-truthy` → `or`
- `lint/loop-empty-when` → `while`
- `style/prefer-condp` → `condp contains?`
- `lint/identical-branches` — collapse duplicate branches

## Done when

- `clojure -M:splint` reports ≤ 45 warnings.
- `clojure -M:test` passes.

---
Triage 2026-07-10 → rejected: consolidated into static-analysis-splint-idiom-cleanup (18-warning remainder too small to justify separate cards).
---
