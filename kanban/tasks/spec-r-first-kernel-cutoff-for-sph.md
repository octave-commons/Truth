---
uuid: "spec-r-first-kernel-cutoff-for-sph"
title: "Spec: r²-First Kernel Cutoff for SPH"
status: "todo"
priority: "P1"
labels: ["specs", "sph", "performance"]
created_at: "2026-07-02T19:35:28.965299077Z"
source: "kanban/tasks/spec-r-first-kernel-cutoff-for-sph.md"
category: "specs"
---

# Spec: r²-First Kernel Cutoff for SPH

**Status:** draft  
**Target:** Reduce per-pair kernel cost in SPH density, pressure-gradient, and EM curl.  
**Scope:** `src/domain/hydro.clj`, `src/domain/em.clj`, `src/domain/physics/cache.clj`, tests

## 1. Goal

The cubic-spline SPH kernel is zero outside `r > h`. Currently `sph-density`, `pressure-gradient-acceleration`, and `curl-estimate` compute `r = sqrt(dx²+dy²+dz²)` for every neighbor, including those beyond `h`. For sparse/diffuse regions many neighbors lie outside the support and are rejected after an expensive square root. This refactor moves the cutoff to squared distances so rejected pairs skip `sqrt` entirely.

## 2. Changes

### 2.1 `domain.hydro/kernel`

Add a squared-distance arity:

```clojure
(defn kernel
  "Cubic-spline SPH kernel W(r,h). Zero outside r > h."
  ([r h] ...)
  ([r2 h] ...))   ;; NEW: accept r² directly; return 0 if r2 >= h²
```

The existing `(kernel r h)` arity remains for backward compatibility and delegates to `(kernel (* r r) h)`.

### 2.2 `domain.hydro/kernel-gradient`

Add a squared-distance arity:

```clojure
(defn kernel-gradient
  "Gradient ∇W(r_ij, h). Returns zero for r=0 or r > h."
  ([r-ij h] ...)
  ([r2-vec h] ...))   ;; NEW: [rx ry rz] and r²; avoid sqrt for r2 >= h²
```

Return `[0.0 0.0 0.0]` when `r2 >= h²` before any `sqrt`.

### 2.3 Neighbor cache

`domain.physics.cache` should store, for each neighbor:
- `:r2` squared distance from central particle
- existing `:gradient` (for hydro, computed with pair `h_ij = r_i + r_j`)
- existing `:curl-gradient` (for EM, computed with `h = 0.5(r_j+1)`)

Cache builder computes `r2` once and reuses it for both gradient flavors.

### 2.4 Consumers

- `sph-density`: use `r2` from cache entry; call `(kernel r2 h)`.
- `pressure-gradient-acceleration`: use `r2` from cache; call `(kernel-gradient [rx ry rz] r2 h)`.
- `curl-estimate`: use `r2` from cache; call `(kernel-gradient [rx ry rz] r2 h)`.
- All callers that currently compute `r` should be updated to pass `r2`.

### 2.5 Tests

- Existing kernel normalization and gradient tests must still pass.
- Add direct tests for the `r2` arities: `kernel(r2,h)` matches `kernel(sqrt(r2),h)` within tolerance; same for gradient.
- Ensure hydro/EM cache parity tests still pass.

## 3. Acceptance Criteria

1. All 247 tests pass.
2. `clj -M:bench :phase0` shows `tick-world (500 particles)` ≤ current baseline (≈29.8 ms) OR hydro+EM combined cost reduced.
3. No behavioral change: density/pressure/acceleration/curl results match pre-refactor within numerical tolerance.
4. Code passes `clj -M:cljfmt check` and introduces no new splint warnings.

## 4. Promotion Path

1. Update `kernel` and `kernel-gradient` with `r2` arities.
2. Update cache builder to compute/store `r2` and use it when building gradients.
3. Update `sph-density`, `pressure-gradient-acceleration`, `curl-estimate` to consume `r2`.
4. Update any bench/test helpers that call kernel directly.
5. Verify tests + benchmarks.
