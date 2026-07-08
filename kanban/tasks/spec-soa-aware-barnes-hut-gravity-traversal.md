---
uuid: "spec-soa-aware-barnes-hut-gravity-traversal"
title: "Spec: SoA-aware Barnes–Hut gravity traversal"
status: "todo"
priority: "P1"
labels: ["specs", "gravity", "gravity"]
created_at: "2026-07-02T19:35:28.972686898Z"
source: "kanban/tasks/spec-soa-aware-barnes-hut-gravity-traversal.md"
category: "specs"
---

# Spec: SoA-aware Barnes–Hut gravity traversal

**Status:** draft  
**Target:** Eliminate per-body allocation in the gravity hot path and reduce `gravity-acceleration` wall time by reading positions/masses directly from the `:phase0/physics-soa` primitive arrays.  
**Scope:** `src/domain/gravity/barnes_hut.clj`, `src/domain/orbital/system.clj`, `src/domain/physics/cache.clj`, tests.

## 1. Goal

The current `gravity-acceleration` builds body maps from the SoA cache (`soa->bodies`) and then calls `bh/acceleration`. `acceleration` destructures `:position` into `[px py pz]` and passes a `self-id` to skip self-gravity at leaf nodes. This still allocates:
- one 3-vector per body in `soa->bodies`, and
- one stack frame per tree node in the recursive `traverse-fast`.

This spec adds a new fast path that walks the tree directly against the SoA arrays, avoiding all per-target and per-node allocation.

## 2. Design

### 2.1 New API: `acceleration-for-soa`

```clojure
(defn acceleration-for-soa
  "Gravitational acceleration for every entity in the SoA cache.

   Returns a map {eid [ax ay az]} computed by walking the Barnes–Hut tree
   once per target entity. Reads target positions and source positions/masses
   directly from the SoA arrays.

   `tree` must have been built from the same spatial items that produced `soa`.
   `self-id` is the eid to skip at leaf nodes."
  [G theta softening tree soa self-id]
  ...)
```

Signature intentionally mirrors `bh/acceleration` but takes `soa` and `self-id` instead of a body map.

### 2.2 Internal traversal

Implement `traverse-soa` as an explicit-stack scalar walk (similar to `traverse-stack`) that:
- Takes target coordinates `px py pz` and `self-eid`.
- Uses a `java.util.ArrayDeque` for nodes.
- Accumulates `[ax ay az]` into a `double-array[3]`.
- At internal nodes, compares `s² < θ²·d²` using `(:aabb-side node)` and `(:com node)`.
- At leaf nodes, skips any body whose `:id` equals `self-eid`.
- For accepted sources, reads source position/mass from the body map stored in the leaf (the tree already carries these; we do not change tree storage).

This keeps the tree data structure unchanged; only the traversal is new.

### 2.3 Tree construction

`build-tree` still consumes body maps. The spatial index already builds `:phase0/spatial-items` as body maps; reuse those to build the tree. The SoA cache is built from ECS components in the same order, but the tree does not need to be rebuilt from SoA arrays.

### 2.4 Orbital system integration

Modify `domain.orbital.system/gravity-acceleration`:
- If `:phase0/physics-soa` is present, call `bh/acceleration-for-soa` once per eid in `:eids`.
- Build the result cell map `{eid [ax ay az]}`.
- Fall back to the existing body-map path when SoA is absent.

Remove or deprecate `soa->bodies` once the new path is verified.

## 3. Correctness requirements

1. Self-gravity is zero: for every eid, its own contribution is skipped.
2. Result matches the existing `bh/acceleration` body-map path within absolute tolerance `1e-9 m/s²` for a representative nebula.
3. Tree may contain bodies not in the SoA cache (observer, player) — only compute acceleration for SoA eids; skip non-SoA bodies at leaves by `:id` mismatch.
4. Empty tree → zero acceleration for all eids.

## 4. Performance target

- Reduce `gravity-acceleration` 500-particle time from ~4–10 ms to <3 ms.
- Reduce object allocation rate in gravity enough to lower GC pressure in the full tick.

## 5. Tests

Add to `test/domain/gravity/barnes_hut_test.clj`:
- `test-soa-acceleration-matches-body-path`: generate 50 random bodies, build tree, compare `acceleration` vs `acceleration-for-soa` for each.
- `test-soa-self-gravity-zero`: isolated single body returns `[0 0 0]`.
- `test-soa-empty-tree`: empty tree returns `[0 0 0]`.

Add to `test/domain/orbital/system_test.clj`:
- `test-gravity-acceleration-soa-path`: full `gravity-acceleration` run with SoA cache produces same result as without.

## 6. Promotion path

1. Implement `traverse-soa` and `acceleration-for-soa` in `domain.gravity.barnes-hut`.
2. Add unit tests.
3. Wire into `domain.orbital.system/gravity-acceleration`.
4. Remove `soa->bodies` if no longer used.
5. Run `clj -M:test`, `clj -M:bench :phase0`, `clj -M:cljfmt check`, `clj -M:splint` on changed files.

## 7. Risks

- The traversal is duplicated; `traverse-stack` and `traverse-soa` may drift. Keep them side-by-side only until SoA path is validated, then remove the slower one.
- Self-id handling must match the body path exactly.
- Leaf bodies carry `:id` from `:phase0/spatial-items`; ensure observer/player ids never collide with SoA eids.
