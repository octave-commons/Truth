---
uuid: "spec-soa-primitive-array-physics-cache"
title: "Spec: SoA Primitive-Array Physics Cache"
status: "todo"
priority: "P1"
labels: ["specs", "performance"]
created_at: "2026-07-02T19:35:28.973115615Z"
source: "kanban/tasks/spec-soa-primitive-array-physics-cache.md"
category: "specs"
---

# Spec: SoA Primitive-Array Physics Cache

**Status:** draft  
**Target:** Reduce ECS map indirection in gravity/SPH/EM hot paths to approach 16.6 ms/tick at 500 particles.  
**Scope:** `src/domain/physics/cache.clj`, `src/domain/gravity/barnes_hut.clj`, `src/domain/orbital/system.clj`, `src/domain/hydro.clj`, `src/domain/em.clj`, `src/domain/integrator.clj`, `src/domain/phase0.clj`, tests

## 1. Goal

The ECS world stores components as `{ctype {eid value}}` persistent hash maps. Every hot-path read goes through `get-in`, which is O(log₃₂ N) and allocates intermediate objects. The research notebook `phase0-tick-loop-optimization.md` recommends adding a transient **Structure-of-Arrays (SoA)** physics cache rebuilt each tick from the ECS, used by the numerical kernels, with results written back through the existing write-set mechanism.

This spec implements that cache for the dominant Phase 0 systems: gravity, hydro density/pressure, EM curl/Lorentz, and motion integration.

## 2. Design

### 2.1 New transient cache

Add a second transient world key `:phase0/physics-soa` that holds flat primitive arrays and lookup maps:

```clojure
{:phase0/physics-soa
  {:eids     [eid ...]                ;; entity ids in array order
   :n        int                      ;; count of entities
   :mass     double-array
   :radius   double-array
   :px py pz double-arrays
   :vx vy vz double-arrays
   }}
```

The cache is built once per tick after `spatial-index` and stripped before the tick returns. It is **not** an ECS component.

### 2.2 Builder

Extend `domain.physics.cache` with:

```clojure
(defn build-physics-soa
  "Build an SoA cache from the ECS world for all physics-active entities."
  [world]
  ...)

(defn strip-physics-soa
  "Remove the SoA cache from the world."
  [world]
  (dissoc world :phase0/physics-soa))
```

The builder reads all required components in a single `ecs/all-of` projection, so it issues one ECS lookup per entity rather than one per component. Include all entities with `c/position`, `c/velocity`, `c/mass`, `c/radius`. Validation against `law.field/physics-soa-schema` runs by default and can be disabled by setting `:phase0/validate-soa? false` on the world.

### 2.3 Gravity

- `domain.gravity.barnes-hut/acceleration` already accepts body maps. Keep that API.
- Add a new fast path in `domain.orbital.system/gravity-acceleration` that builds body maps directly from the SoA cache (avoiding ECS `get-component` per body) and stores results into an `accel-gravity` cell map keyed by eid.
- The Barnes–Hut tree can be built from the SoA cache instead of re-projecting ECS components.

### 2.4 Hydro / EM

- The neighbor cache already centralizes neighbor discovery. Keep it.
- Where consumers currently call `ecs/get-component` repeatedly (e.g., density-system reducing over `eids`), instead project from the SoA cache or use the neighbor cache entries that already carry projected data.
- Focus the SoA win on **motion integration** and **gravity**, which currently do `ecs/get-component` for every entity and every acceleration source.

### 2.5 Integrator

- Add an SoA-aware motion integration path:
  - Read positions/velocities/masses from SoA arrays.
  - Sum acceleration contributions from SoA arrays or precomputed cell maps.
  - Write updated positions/velocities back through the standard write-set.
- Keep the existing ECS-only path as fallback when `:phase0/physics-soa` is absent.

### 2.6 Phase0 tick

Insert after `spatial-index`:
1. Build `:phase0/neighbor-cache`.
2. Build `:phase0/physics-soa`.

After physics:
1. Strip both caches.

## 3. Acceptance Criteria

1. All tests pass.
2. `clj -M:bench :phase0` reports `tick-world (500 particles)` reduced below current baseline (≈31 ms).
3. No ECS schema changes; cache is transient world plumbing.
4. Code passes `clj -M:cljfmt check` and introduces no new splint warnings.

## 4. Promotion Path

1. Add SoA builder to `domain.physics.cache`.
2. Add SoA-aware gravity path in `domain.orbital.system`.
3. Add SoA-aware integrator path in `domain.integrator`.
4. Wire builders/strippers in `domain.phase0/step-physics`.
5. Add tests that verify SoA and ECS paths produce identical results.
6. Benchmark and review.

## 5. Risks

- The ECS world is the single source of truth; the SoA cache must be rebuilt every tick and never mutated directly by systems.
- Primitive arrays require careful handling of `nil`/missing fields.
- The win may be smaller than hoped if the JVM JIT already optimizes the ECS map lookups.
