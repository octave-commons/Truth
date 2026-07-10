---
uuid: "static-analysis-splint-naming-structure"
title: "Splint cleanup: naming and structural rules"
status: "rejected"
priority: "P2"
estimate: 3
labels: ["specs", "static-analysis", "epic-static-analysis-cleanup"]
created_at: "2026-07-07T00:00:00Z"
source: "kanban/tasks/static-analysis-splint-naming-structure.md"
category: "specs"
---

# Splint cleanup: naming and structural rules

> Parent: `kanban/tasks/static-analysis-splint-idiom-cleanup.md`

Fix naming and structural rules reported by Splint. These changes can affect public APIs, so suppress with documented reasons where renaming would break downstream consumers.

## Scope

### Naming rules

- `naming/lisp-case` — rename local/schema helpers to `lisp-case`, or suppress if part of a public DSL.
- `naming/conversion-functions` — `to` → `->` in conversion names, or suppress if public.
- `naming/record-name` — `AABB` → `Aabb`, or suppress if the project keeps acronyms uppercase.
- `naming/single-segment-namespace` — add a segment to `architecture-test`, or suppress if intentional.

### Structural rules

- `style/multiple-arity-order` — reorder multi-arity `defn`s from fewest to most arguments.
  - `src/shape/spatial.clj` (`vec3`)
  - `src/domain/physics/collision.clj` (`collision-detection-system`)
  - `src/domain/regime.clj` (`regime-system`)
  - `src/domain/stellar.clj` (`fusion-promotion-system`, `sink-formation-system`, and one other)
- `style/def-fn` — convert `(def name (fn ...))` to `(defn name ...)`.

### Where to suppress instead of fix

- Public API names.
- Well-established acronym conventions.
- Intentionally single-segment test namespaces.

Every suppression must include a `;; Intentional:` or `;; Suppressed:` comment explaining the reason.

## Done when

- `clojure -M:splint` reports ≤ 15 warnings.
- `clojure -M:test` passes.
- Any public API rename keeps a `^:deprecated` alias for one release, or is documented as a suppression.

---
Triage 2026-07-10 → rejected: consolidated into static-analysis-splint-idiom-cleanup (18-warning remainder too small to justify separate cards).
---
