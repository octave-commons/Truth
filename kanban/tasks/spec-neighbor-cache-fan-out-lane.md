# Spec: Neighbor-Cache Fan-Out Lane

**Status:** in progress  
**Target:** Remove the last serial pre-phase from `step-physics` by making the neighbor cache a first-class fan-out system, moving the ~2.6 ms serial cost into the parallel lane load and allowing the tick budget to approach 16.6 ms at 1000 bodies.  
**Scope:** `src/domain/physics/cache/neighbor.clj`, `src/domain/physics/cache.clj`, `src/domain/ecs/components.clj`, `src/domain/ecs/registry.clj`, `src/domain/genesis/systems.clj`, `src/domain/genesis/tick.clj`, `src/domain/hydro/common.clj`, `src/domain/hydro/density.clj`, `src/domain/hydro/pressure.clj`, `src/domain/em/lorentz.clj`, tests.

## 1. Problem

`step-physics` currently builds `:genesis/neighbor-cache` in a `future` before the fan-out:

```clojure
nb-fut  (future
          (:genesis/neighbor-cache
           (pcache/rebuild-neighbor-cache
            world
            (when-not (:genesis/invalidate-neighbor-cache? world)
              (:genesis/neighbor-cache world))
            (:tick world))))
```

The future is the last serial pre-phase. It cannot overlap anything except the SoA build (already overlapped) and the query-cache attach, so its cost is exposed on the critical path. Measurement: ~2.6 ms @1000 warm bodies.

## 2. Design

### 2.1 Component

Add a new component `c/neighbor-cache` (`:component/neighbor-cache`) holding one `law.field/neighbor-cache-entry-schema` value per entity.

### 2.2 System

Create `:neighbor-cache` in `domain.physics.cache.neighbor`:

```clojure
{:id     :neighbor-cache
 :ns     'domain.physics.cache.neighbor
 :reads  #{c/matter-state c/position c/velocity c/mass c/radius
           c/density c/pressure c/temperature c/b-field
           c/neighbor-cache}
 :writes #{c/neighbor-cache}
 :run    (fn [world] ... write-set ...)}
```

The system reads the **one-tick-stale** `c/neighbor-cache` components from the snapshot to decide reuse, and writes the **current** cache entries into the write-set. Consumers (hydro, em-lorentz) also read the snapshot's `c/neighbor-cache`, so they see the one-tick-stale cache — exactly the Jacobi lag every other channel carries.

### 2.3 Physics semantics

- Hydro density/pressure and EM Lorentz forces now use neighbor identities and gradients from **tick N−1** evaluated at **tick N−1 positions** (because the cache entry is not refreshed after being written). This is a deliberate one-tick lag.
- The cache entry still contains the current entity's own position/velocity/state because the system recomputes the entry each tick from the snapshot components. Only neighbor identities and gradients are stale.
- This is acceptable because SPH density and magnetic curl are low-pass fields; a one-tick lag (dt bounded by bulk dynamical time) is below the integration error of the leapfrog.

### 2.4 System ordering

The `:neighbor-cache` system must appear **before** `:hydro` and `:em-lorentz` in the declared system list only so the registry is readable; fan-out order is irrelevant. Both systems read the same snapshot, so they cannot observe each other's writes.

### 2.5 Step-physics

Remove the `nb-fut` pre-phase. `step-physics` becomes:

```clojure
[world]
(let [systems (systems/physics-systems-parallel world)]
  (-> world
      (ecs/with-query-cache)
      (pcache/build-physics-soa)
      (tick/run-parallel systems)
      (ecs/strip-query-cache)
      (pcache/strip-physics-soa)))
```

`:genesis/neighbor-cache` is no longer assoc'd onto the world; the persistent cache is now the `c/neighbor-cache` component map.

## 3. Acceptance Criteria

1. `domain.ecs.registry` declares `:neighbor-cache` as the sole writer of `c/neighbor-cache`.
2. Hydro and EM Lorentz systems read `c/neighbor-cache` components instead of `(:genesis/neighbor-cache world)`.
3. `step-physics` contains no `future` cache rebuild; the cache is built by the fan-out.
4. `clojure -M:test` green.
5. `clojure -M:bench phase0` shows `tick-world @1000` reduced by at least the exposed cache cost (~2–3 ms).
6. Formation integration tests still produce stars/planets; the one-tick cache lag does not break the substellar ladder.
7. Cache-equivalence tests are rewritten to assert equivalence **across** a pair of ticks, not within a single tick (the cache is now intentionally one-tick stale).

## 4. Risks

- One-tick stale gradients may visibly shift shock/instability thresholds if dt is large. Monitor `formation-integration-test` and `domain.hydro-test`.
- The cache is now an ordinary component; any accidental second writer breaks the single-writer invariant. Registry must be updated.
- `domain.physics.cache/build-neighbor-cache` and `rebuild-neighbor-cache` are used by tests; their return contract changes from world-with-`:genesis/neighbor-cache` to world-with-`c/neighbor-cache` components.

## 5. Verification

- Run `test/domain/physics/cache_test.clj` after updating it to read components.
- Run `test/domain/hydro_test.clj` and `test/domain/em_lorentz_test.clj`.
- Run `test/domain/formation_integration_test.clj`.
- Compare `clojure -M:bench phase0` before/after.
