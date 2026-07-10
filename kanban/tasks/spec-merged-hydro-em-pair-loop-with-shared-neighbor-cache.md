---
uuid: "spec-merged-hydro-em-pair-loop-with-shared-neighbor-cache"
title: "Spec: Merged Hydro/EM Pair Loop with Shared Neighbor Cache"
status: "done"
priority: "P1"
labels: ["specs", "hydro", "em", "performance"]
created_at: "2026-07-02T19:35:28.965731772Z"
source: "kanban/tasks/spec-merged-hydro-em-pair-loop-with-shared-neighbor-cache.md"
category: "specs"
---

# Spec: Merged Hydro/EM Pair Loop with Shared Neighbor Cache

**Status:** draft  
**Target:** Phase 0 tick budget (≤16.6 ms at 500 particles)  
**Scope:** `src/domain/hydro.clj`, `src/domain/em.clj`, `src/domain/spatial/index.clj`, `src/domain/integrator.clj`, `src/law/field.clj`, `src/law/plasma.clj`, tests

## 1. Goal

Eliminate duplicate neighbor discovery and kernel-gradient computation between the SPH hydro pass (`density-system`, `hydro-system`, `gas-structure`) and the MHD-lite Lorentz pass (`em-lorentz`). Both currently traverse the same spatial structure for the same particles with the same smoothing length. Sharing a per-particle neighbor list + kernel-gradient cache is expected to cut the combined hydro+EM cost by 30–50%.

## 2. Background

Current systems:
- `domain.hydro/density-system`: per `:nebula` particle, queries neighbors within geometric smoothing length `h = factor * d_nn`, computes SPH density ρ, pressure, adaptive radius.
- `domain.hydro/hydro-system`: per hydro-active particle, queries neighbors within `2r`, computes pressure-gradient acceleration.
- `domain.hydro/gas-structure`: same as density-system but emits radius/density write-set.
- `domain.em/lorentz-acceleration-system`: per EM-active particle with `:b-field`, queries neighbors within `2r`, computes `∇×B` via SPH curl, then Lorentz acceleration and magnetic-braking torque.

Research finding (mhd-em-lorentz-optimization.md): the dominant EM cost is redundant neighbor/gradient work relative to hydro. The physically defensible fix is to merge the pair loops and reuse hydro's neighbor list and kernel gradients for the curl.

## 3. Design

### 3.1 New shared cache component

Introduce a transient cache stored on the world during the physics tick only:

```clojure
:phase0/neighbor-cache
  {eid {:position [...]
        :h double
        :neighbors [nbr-map ...]
        :gradients [[gx gy gz] ...]   ;; ∇W for each neighbor, central-particle frame
        }}
```

The cache is **not** an ECS component; it lives on the world map as plumbing, rebuilt each tick by `domain.spatial.index` or a new `domain.physics.cache` namespace, and discarded before the next tick. This preserves the ECS single-substrate invariant.

### 3.2 Cache builder system

Add a new physics system `:neighbor-cache` that runs before `:hydro`, `:structure`, and `:em-lorentz`:

- Inputs: `:phase0/spatial-grid`, `:phase0/spatial-tree`, ECS `c/position`, `c/radius`, `c/matter-state`, `c/b-field`.
- For each hydro-active or EM-active entity, compute smoothing length `h` (using tree nearest-neighbor distance), query the uniform grid for neighbors within `h`, and compute/store kernel gradients.
- Use `par/par-mapv` for the per-particle loop.
- The cache contains **all particles** that could be neighbors so hydro and EM can filter by `:matter-state` without re-querying.

### 3.3 Refactor hydro to consume cache

- `smoothing-length` continues to use the tree for nearest-neighbor distance (faster than grid shell expansion).
- `sph-density`, `pressure-gradient-acceleration` accept a precomputed neighbor list.
- `density-system`, `hydro-system`, `gas-structure` read from `:phase0/neighbor-cache` instead of issuing new spatial queries.

### 3.4 Refactor EM to consume cache

- `curl-estimate` accepts the same neighbor list plus the precomputed gradients.
- `lorentz-acceleration-system` reads cache entry for each EM-active entity.
- Add a physical gate: only compute full curl/Lorentz force when local plasma beta or Alfvén Mach indicates magnetic dominance. Otherwise emit zero acceleration (magnetic braking still applies via separate cheap torque).

### 3.5 New law schemas

In `law/field.clj` or `law/plasma.clj`:

- `mhd-regime?` predicate using plasma beta `β = P_thermal / P_B` and Alfvén Mach `ℳ_A = v / v_A`.
- `lorentz-acceleration-cap` limiting `|a_L|` to `v_A² / R`.

### 3.6 Tests

- `test/domain/em_lorentz_test.clj`: verify that cached curl matches previous on-the-fly curl for same neighbors.
- `test/domain/hydro_test.clj`: verify density/pressure-gradient unchanged when cache is used.
- `test/domain/phase0_test.clj`: full simulation still passes with cache enabled.
- Performance: `clj -M:bench :phase0` shows combined hydro+EM cost reduction.

## 4. Acceptance Criteria

1. All 230 tests pass.
2. `clj -M:bench :phase0` reports `tick-world (500 particles)` ≤ current baseline (≈30 ms) with hydro+EM combined cost reduced by ≥20%.
3. No new ECS components are added; cache is transient world plumbing.
4. Code passes `clj -M:cljfmt check` and `clj -M:splint`.

## 5. Open Questions

- Should the cache be built for **all** particles or only hydro/EM active? Building all simplifies reuse and costs O(N) grid queries.
- Should kernel gradients be stored per neighbor, or should we store only neighbor references and recompute gradients on demand? Storage is cheap (~500 × 32 neighbors × 3 doubles = ~384 KB) and recomputation is the dominant cost.
- Where does the cache live after the physics tick? It should be stripped in `tick-world` so downstream systems (summary, pacing) do not carry it.

## 6. Promotion Path

1. Add `domain.physics.cache` namespace.
2. Modify `domain.spatial.index/spatial-index` to also build neighbor cache (or add separate builder).
3. Update `domain.hydro` functions and systems to read cache.
4. Update `domain.em` `curl-estimate` and `lorentz-acceleration-system` to read cache.
5. Add law predicates/caps.
6. Add tests and benchmarks.
7. Review + revise.

---
Triage 2026-07-10 (todo→in_review): DONE-IN-CODE per code check — cache/neighbor.clj shared cache; em/lorentz curl-estimate-from-cache gated by mhd-regime?; hydro reads it; cache-parity tests. Staged for close pending sign-off.

2026-07-10 → DONE (triage batch-close): implemented + test-covered in code; owner approved close on triage evidence.
---
