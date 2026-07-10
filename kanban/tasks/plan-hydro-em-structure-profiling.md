---
uuid: "plan-hydro-em-structure-profiling"
title: "Plan: Hydro / EM / structure profiling"
status: "done"
priority: "P1"
labels: ["specs", "hydro", "em", "performance"]
created_at: "2026-07-02T19:35:28.964538735Z"
source: "kanban/tasks/plan-hydro-em-structure-profiling.md"
category: "specs"
---

# Plan: Hydro / EM / structure profiling

**Status:** draft  
**Goal:** Identify the dominant sub-costs inside the hydro, EM-Lorentz, and structure systems so the next optimization targets the right loops.  
**Scope:** `bench/gates_of_truth/bench/phase0.clj`, `src/domain/hydro.clj`, `src/domain/em.clj`, `src/domain/stellar.clj` (structure-system), `src/domain/spatial/index.clj`.

## 1. Motivation

Current per-system step-physics times (500 particles, recent run):
- `:hydro`              5.7–7.1 ms
- `:em-lorentz`        11.0–12.0 ms
- `:structure`          8.1–11.3 ms
- `:integrator`         8.1–12.0 ms
- `:gravity`            4.4–9.8 ms

These are the five largest contributors. While the SoA cache and a SoA-aware gravity traversal help gravity and integration, hydro/EM/structure remain large. We need per-sub-function timing before attempting another rewrite.

## 2. Questions to answer

For each of hydro, EM-Lorentz, and structure:
1. How much time is spent in neighbor discovery vs. pair-loop arithmetic?
2. How much time is spent building the spatial index and uniform grid each tick?
3. How much time is spent in `Math/sqrt`, vector allocation, and ECS component lookups?
4. What is the effective neighbor count distribution? Are we doing O(N²) work for a few dense clumps?
5. For EM: what fraction is the curl estimate vs. Lorentz force assembly vs. magnetic braking?
6. For structure: what fraction is gas SPH vs. resolved-body structure derivation?

## 3. Instrumentation plan

### 3.1 Benchmark harness extensions

Extend `bench/gates_of_truth/bench/phase0.clj` to emit a nested timing map:

```clojure
{:hydro {:neighbor-query 1.2
         :gradient-arith 3.5
         :cache-lookup 0.3
         :write-set 0.4}
 :em-lorentz {...}
 :structure {...}
 :spatial-index {:octree-build 2.1
                 :grid-build 0.8
                 :item-projection 1.2}}
```

Use `System/nanoTime` inside the system `:run` functions via a temporary wrapper. Do not commit the wrapper; instead expose a benchmark-only flag `:phase0/profile-subsystems?`.

### 3.2 Temporary inline timers (benchmark-only)

In each system, wrap candidate hot sections with `(when (:phase0/profile-subsystems? world) (System/nanoTime) ...)` and store cumulative nanos on a transient world key `:phase0/_profile`.

Suggested sections:

**hydro (`pressure-acceleration`):**
- `entity->hydro-data` + ECS queries
- `cache-neighbors-and-gradients`
- `pressure-gradient-acceleration` pair loop
- write-set assembly

**em (`lorentz-acceleration-system`):**
- EM-active data projection
- neighbor/curl cache lookup
- `curl-estimate`
- Lorentz force assembly
- magnetic braking torque

**structure (`stellar/structure-system`):**
- gas branch (`gas-structure`) projection + SPH density
- resolved-body branch
- write-set assembly

**spatial index (`spatial/spatial-index`):**
- ECS projection into `:phase0/spatial-items`
- octree build
- uniform grid build

### 3.3 Statistical output

After N ticks, print:
- Mean and max per-section time.
- Neighbor-count histogram (0–5, 6–15, 16–50, 50+).
- Pair-loop rate: total neighbor interactions per millisecond.

## 4. Non-goals

- No production code paths will be permanently slowed by profiling.
- No ECS schema changes.
- No new dependencies.

## 5. Deliverables

1. A profiling commit/branch with `bench/gates_of_truth/bench/phase0.clj` extended and temporary timer wrappers.
2. A short report (inline in the benchmark output or a `perf_report_*.txt`) listing:
   - per-section times,
   - neighbor distribution,
   - recommended next target.

## 6. Next action after profiling

Use the report to choose one of:
- SoA-ize hydro/EM pair loops (project arrays once, share across neighbors).
- Reduce spatial-index build cost (e.g., skip uniform grid when it does not help).
- Throttle non-physics systems (structure/eos/classifier) by LOD or tick skipping.

---
Triage 2026-07-10 (todo→in_review): DONE-IN-CODE per code check — domain/profile.clj profile-section gated by :genesis/profile-subsystems?; systems wrapped; bench/phase0 harness prints per-subsystem ms. Staged for close pending sign-off.

2026-07-10 → DONE (triage batch-close): implemented + test-covered in code; owner approved close on triage evidence.
---
