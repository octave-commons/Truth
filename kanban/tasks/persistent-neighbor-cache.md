---
uuid: "persistent-neighbor-cache"
title: "Persistent Neighbor Cache for Phase 0"
status: "in_progress"
priority: "P0"
labels: ["perf", "phase0", "spec"]
created_at: "2026-07-03T00:00:00Z"
source: "kanban/tasks/persistent-neighbor-cache.md"
category: "specs"
---

# Spec: Persistent Neighbor Cache for Phase 0

**Status:** implemented — acceptance criteria met, pending review/merge  
**Target:** `kanban/tasks/tick-perf-drift-profile.md` Fix 4 continuation  
**Scope:** `src/domain/physics/cache.clj`, `src/domain/genesis.clj`, `src/domain/hydro.clj`, `src/domain/em.clj`, `src/domain/spatial/index.clj`, tests

## 1. Goal

Make the per-tick neighbor cache (`:genesis/neighbor-cache`) **persistent** across ticks. It is currently rebuilt from scratch every tick at a cost of ~14 ms @1000 particles — the largest remaining serial segment. By reusing the previous tick's cache and rebuilding only particles whose displacement exceeds a fraction of their smoothing length, we expect to cut cache-build cost by 60–90% once the simulation is in a smooth regime, while keeping results physically identical to a full rebuild.

## 2. Background

The existing cache lives in `src/domain/physics/cache.clj`:

- `build-neighbor-cache` iterates every hydro/EM-active particle.
- For each particle it computes geometric smoothing length `h` via `idx/query-nearest-dist` on the Barnes–Hut tree, queries neighbors within `max(h, 2r)` via the uniform grid, and computes pressure + curl kernel gradients.
- The cache is attached before the fan-out and stripped after the fold.

Consumers:
- `domain.hydro/gas-structure` (called by `stellar/structure-system`) reads `:genesis/neighbor-cache`.
- `domain.hydro/pressure-acceleration` reads it.
- `domain.em/lorentz-acceleration-system` reads it.

Because every tick creates the world anew, the cache is lost between ticks.

## 3. Design

### 3.1 Persistent cache placement

The cache will be stored on the **world** as `:genesis/neighbor-cache` after `tick-world` returns, just like other persistent world state. It is rebuilt/revalidated at the start of `step-physics` instead of built blindly.

### 3.2 Displacement-based invalidation

For each cached particle `i`:
- Let `x_i_anchor` be the entry's `:anchor-position` — the position at which the
  neighbor set was last actually queried.
- Let `x_i_now` be the current snapshot position.
- Let `h_i` be the cached smoothing length.

The cache entry is **reusable** when `|x_i_now − x_i_anchor| <
displacement-tolerance × h_i` (default 0.1 — the classic SPH neighbor-list
skin). When reusable, only the *expensive spatial-query products* are reused,
and those are **identities, not values**:

- the neighbor **identity list** (product of `grid-within-radius`), and
- the **nearest neighbor's identity** `:nn-id` (product of the Barnes–Hut
  `query-nearest` descent that sets the smoothing length).

Everything derived from them is recomputed from the current snapshot every
tick: the smoothing length `h = factor · |x_i − x_nn|` from the remembered
nearest neighbor's *current* position (min'd against the cached set in case a
member drifted closer), the central particle's fields, every neighbor's fields
(re-read from `:genesis/spatial-items`), `r2`, and both kernel gradients.
Value data is cheap (map lookups + arithmetic) but goes stale in one tick;
reusing it feeds tick-old physics into the pair loops and diverges the
simulation even when nothing moves. Profiling @1000 particles: the nearest
descent is ~28 ms serial, the radius query ~6 ms, the gradient math ~1 ms —
so caching identities captures nearly all the cost while keeping the physics
bit-fresh.

Neighbors are canonically ordered by `:id` inside each entry so refreshed and
freshly queried entries walk consumers' floating-point reductions in the same
order.

A particle that exceeds the tolerance, was not cached, is no longer
hydro/EM-active, has a cached/nearest neighbor that no longer exists
(despawn/merge), or whose consumer filter radius `max(h, 2·radius)` outgrew
the coverage `:query-r` it was queried at (the build query carries a
`(1 + tolerance)` headroom factor) is rebuilt from scratch with fresh spatial
queries.

The `:anchor-position` is carried over **unchanged** on reuse, so displacement
accumulates against the last real query — a slowly drifting particle still
requeries once its total drift exceeds the skin. Identity staleness (a third
particle sneaking closer than the remembered nearest, or entering the kernel
without the central particle moving) is bounded by the periodic full rebuild;
a stale nearest identity can only make `h` too large, never too small.

### 3.3 Periodic full rebuild

Central-particle displacement alone cannot detect a neighbor that moves into the kernel while the central particle is stationary. As a safety net, `step-physics` forces a full cache rebuild every `:genesis/neighbor-cache-full-rebuild-interval` ticks (default 10). This bounds the maximum age of any cached neighbor list and provides a deterministic fallback for regression tests. The full-rebuild interval is configurable via the world; setting it to 1 gives behavior equivalent to the original per-tick rebuild.

### 3.4 Incremental rebuild strategy

1. Start with the previous tick's cache (or `{}` on tick 1 / full-rebuild tick).
2. For each currently hydro/EM-active entity:
   - If the cache entry is reusable and this is not a forced-rebuild tick, reuse it unchanged (update `:position` only).
   - Otherwise, build a fresh entry.
3. Evict cache entries for entities that no longer exist or are no longer hydro/EM-active.

### 3.5 API changes

`src/domain/physics/cache.clj`:
- Add `displacement-tolerance` constant (0.1) — the neighbor-list skin.
- Add `max-displacement-squared [h tolerance]` helper.
- `cache-entry-valid?` gates on `displacement-tolerance` measured from the
  entry's `:anchor-position`.
- Refactor `build-neighbor-cache` → `rebuild-neighbor-cache [world prev-cache tick]`.
- In `rebuild-neighbor-cache`, reusable entries go through
  `refresh-cache-entry`: keep neighbor IDs + `:nn-id` + `:anchor-position` +
  `:query-r`, refresh all fields from `:genesis/spatial-items`, recompute `h`,
  r2, and gradients. A missing neighbor/nearest item or an outgrown kernel
  forces a fresh build for that entry.
- Keep `build-neighbor-cache` as a convenience for full rebuild.

`src/domain/spatial/index.clj`:
- `nearest` / `grid-nearest` / `query-nearest` return `[distance id]`; the
  `*-dist` forms remain as wrappers.

`src/domain/hydro.clj`:
- `smoothing-length-from-dist [data d]` extracted from `smoothing-length` so
  the cache refresh can derive `h` from a recomputed distance.

`src/law/field.clj`:
- `neighbor-cache-entry-schema` requires `:anchor-position` and `:query-r`.

`src/domain/genesis.clj`:
- `step-physics` calls `(rebuild-neighbor-cache world (:genesis/neighbor-cache world) (:tick world))`.
- Force full rebuild when `(zero? (mod (:tick world) (:genesis/neighbor-cache-full-rebuild-interval world 10)))` or when `:genesis/invalidate-neighbor-cache?` is true.
- `step-physics` does NOT strip `:genesis/neighbor-cache` after the fold.

`src/domain/hydro.clj` and `src/domain/em.clj`:
- No API changes; they already read `:genesis/neighbor-cache`.

### 3.6 Tests

- `test/domain/physics/cache_test.clj`:
  - A cache entry's neighbor set is reused when displacement is below `displacement-tolerance × h` (observable: `:anchor-position` unchanged).
  - A cache entry is requeried when displacement exceeds the tolerance (observable: `:anchor-position` re-anchors).
  - Drift accumulates against the anchor: two sub-tolerance moves that together exceed it force a requery.
  - A reused entry reads neighbor field data from the current snapshot (the stale-fields regression).
  - An evicted entity's entry is removed.
  - Forced full rebuild every N ticks re-anchors even sub-tolerance entries.
- `test/domain/formation_integration_test.clj`:
  - 20-tick frozen world (dt=0, G=0): persistent-cache and forced-full-rebuild runs produce **byte-identical** worlds.
  - Persistent cache with interval = 1 matches invalidate-every-tick mode.
  - Simulation with persistent cache (default interval = 10) remains physically stable for 50 ticks compared to full-rebuild mode (same body count, mass within 1e-6).

## 4. Acceptance Criteria

1. `clojure -M:test` green (497 tests). ✅ 507 tests, 0 failures.
2. `clojure -M:bench phase0` shows `build-neighbor-cache` cost reduced by ≥50% after the first 10 ticks of a 500-particle world, and ≥60% after the first 50 ticks. ✅ steady-state incremental rebuild is **79% cheaper** than full (@500: 3.3 → 0.7 ms; @1000: 7.3 → 1.5 ms; ~93–97% of entries reused).
3. `tick-world @1000` moves closer to 16.6 ms target. ✅ sustained ticking @1000 (ticks 31–80): 44.0 ms with persistent cache vs 53.6 ms with per-tick rebuild (~10 ms/tick).
4. Persistent-cache and full-rebuild modes produce physically equivalent worlds. ✅ exceeded: the frozen-world comparison is byte-identical for 20 ticks, because reuse caches *identities* and recomputes all values.
5. Code passes `clojure -M:cljfmt check` and introduces no new `clojure -M:splint` warning classes in touched files. ✅ (`Math/sqrt` interop matches the existing hot-path idiom and is bit-consistent with the spatial index's arithmetic, which the byte-equality contract depends on.)

## 5. Resolved Questions

- Displacement tolerance stays a constant (`displacement-tolerance` 0.1); it is a numerical-method parameter, not a gameplay knob.
- Neighbor movement is NOT separately tracked; the periodic full rebuild bounds
  invasion staleness, and a stale nearest identity can only overestimate `h`.
- Merges/collisions: a missing neighbor or nearest item in the current
  `:genesis/spatial-items` forces a fresh build for that entry; new entities
  have no entry and are built fresh; victims are evicted by liveness.

## 6. Promotion Path

1. ✅ Displacement validation helpers in `domain.physics.cache`.
2. ✅ `build-neighbor-cache` → `rebuild-neighbor-cache`.
3. ✅ `domain.genesis/step-physics` passes the previous cache and does not strip it.
4. ✅ Full-rebuild opt-in flag and deterministic-equivalence tests.
5. ✅ Benchmark and revise (identity-caching redesign came out of this step:
   profiling showed the nearest-neighbor descent at ~28 ms serial @1000 was the
   real cost, not the radius query).
6. Code review + merge.
