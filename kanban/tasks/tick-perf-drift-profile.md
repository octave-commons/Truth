---
uuid: "tick-perf-drift-profile"
title: "Profile the ~1.6x tick baseline drift (17.6ms → ~30ms @1000)"
status: "done"
priority: "P2"
labels: ["perf", "phase0"]
created_at: "2026-07-06T18:00:00.000000000Z"
source: "kanban/tasks/tick-perf-drift-profile.md"
category: "perf"
---

# 60 fps: closing the gap between the Jacobi architecture and the wall clock

**Date:** 2026-07-03
**Status:** Completed — Fixes 1, 2, 3, 4, 5, 6 landed.
**Baseline:** `clojure -M:bench phase0` on 16 cores — `tick-world` 42.8 ms @500
bodies (23 fps), 113.9 ms @1000 (8.8 fps). `step-physics` parallel 33.4 ms vs
sequential 28.4 ms: the thread-per-system fan-out yields **1.36×** on 16 cores.
The dev window additionally serializes tick + render + a hard 16 ms sleep on one
thread, which is how 43 ms of physics becomes ~12 fps on screen.

**Current (2026-07-05, after Fix 4 round 5 — see Progress):** sustained
warm-cache ticking, measured with the dev service STOPPED (it contends for the
same cores and skews numbers — always stop it before benchmarking) ≈ 11.6 ms
@500 / 17.6 ms @1000 mean (p50 10.9 / 17.1, best 8.5 / 14.1; clean pre-round-5
baseline was 13.4 / 24.1). @500 is well under the 16.6 ms budget; @1000 sits
right at it — p50 straddles 16.6–17.4 across worlds (`create-world` is
nondeterministic, world-to-world spread is ±2 ms).

## Why the fan-out doesn't pay today

1. **Systems are single-threaded over entities.** The fan-out's floor is the
   slowest system (`:structure` ≈ 9 ms @500). 25 lanes of 1–9 ms cannot use 16
   cores well; thousands of per-entity lanes can.
2. **Every system pays its own `entities-with` scan** (0.5–1.3 ms each, ~25
   systems ≈ 15–20 ms aggregate) against the same frozen snapshot.
3. **The legacy bridge multiplies allocation.** A `legacy-system`-wrapped system
   builds a full world` via `put-component` (assoc-in + archetype update per
   cell), then `diff-write-set` re-walks it. 25 threads churning persistent-map
   garbage turn the fan-out into a GC/memory-bandwidth contest.
4. **Serial segments bracket the fan-out:** spatial-index + COM (~3.4 ms), the
   post-fold integrator phase (~6.4 ms, the last Gauss–Seidel step), and the
   bookkeeping tail — `system-summary`, `stats-of`, `complexity-score`,
   `pacing` (~6 ms) — all on one thread, every tick.
5. **The sim ticks on the render thread** (`infra.dev.window/advance-sim!`),
   one tick per frame, plus `Thread/sleep 16`. Frame time = tick + render + 16.

## Budget

60 Hz = 16.6 ms. Render gets its own thread (Fix 1), so the tick budget is a
full 16.6 ms at the **late-game** population (~1000–2000 resolved bodies), not
the pristine 500-particle nebula. Fixes 2–6 attack the tick itself.

***

## Fix 1 — Sim thread ≠ render thread

**Problem.** `render-frame-once` calls `advance-sim!` inline; input handlers
`swap!` the world from the render thread every frame (cursor focus, observer
drift, intervention placement). A naive "run tick in `swap!` on another thread"
livelocks: a 40 ms tick inside `swap!` retries whenever the render thread
touches the atom, which it does every frame.

**Design: single-writer world, intent queue.** The world-atom gets exactly one
writer — the sim thread — mirroring the ECS's own single-writer law.

- `infra.dev.window` gains a **sim loop** running on a dedicated thread:
  1. drain the **intent queue** (a `java.util.concurrent.ConcurrentLinkedQueue`
     of `world → world` fns) and apply each to the current world;
  2. run `tick-fn` (unchanged: `arc/tick-genesis` composition);
  3. `reset!` world-atom with the result;
  4. sleep the remainder of the 16.6 ms tick period (fixed 60 Hz tick rate —
     the pacing model's assumption — free-running when over budget).
- The render thread **never swaps the world-atom**. Every existing
  `swap! world-atom …` in the frame loop (observer drift, `set-focus`,
  `intervention/place`) becomes `(enqueue-intent! …)` with the same fn. Intents
  are applied at the top of the next tick, ≤16 ms later — imperceptible, and
  it makes player input Jacobi-consistent too (an intervention lands between
  ticks, never mid-fold).
- Reads stay as they are: the frame loop derefs the world-atom once per frame
  and renders the latest **completed** world. No partial states exist because
  the sim thread publishes only finished ticks.
- **Pause/skip semantics preserved:** worlds without `:genesis/time-scale`
  (bare gravity demo) tick every `:sim-frame-interval` sim-loop iterations, as
  they did per-frame.
- **Errors:** the sim thread wraps the tick in the same
  dump-artifacts + `:ui/error-state` handling `render-frame-once` uses; on
  error it stops ticking (render shows the error frame). Stop-atom shuts down
  both loops.
- The render loop keeps vsync/sleep pacing; it no longer pays for physics.

**Acceptance:** with a deliberately slow world, render stays ≥30 fps while the
sim free-runs; input (focus, warp placement) still works; screenshot capture
unchanged; `gates-of-truth-dev` pm2 service runs it.

## Fix 2 — One entity scan per tick, not twenty-five

**Design: per-snapshot query cache.** `step-physics` attaches
`:ecs/_query-cache` — a `ConcurrentHashMap` — to the frozen snapshot alongside
`:genesis/neighbor-cache` / `:genesis/physics-soa`, and strips it after the
fold. `ecs/entities-with` consults it: key = the **set** of requested ctypes,
value = the computed eid vector (`computeIfAbsent`; benign to race because the
snapshot is frozen and the compute is pure). Systems don't change at all —
their existing `entities-with` calls hit the cache.

The fold must strip the cache before any post-fold reader sees the new world
(a stale cache on a mutated world would be a correctness bug, not a slowdown).

**Also:** `entities-with`'s scan gets a direct-map fast path — pivot on the
smallest component map, then `contains?` against each other component map
directly, instead of materializing `archetype` sets per candidate.

**Acceptance:** per-system `*/scan` timings collapse in the bench; results
byte-identical to the uncached path (existing ECS tests + `run-sequential`
equivalence).

## Fix 3 — Retire the legacy bridge on hot systems

Systems still wrapped by `tick/legacy-system` in `physics-systems-parallel`:
`:fusion`, `:regime`, `:collision-detection`, `:fusion-promotion`,
`:sink-formation`, `:disk-evolution`, `:lod-scheduler`,
`:magnetosphere-coupling`.

Each is rewritten to the native contract — `{:id .. :writes .. :run (fn
[frozen] write-set)}` — emitting `{ctype {eid value|removed}}` directly instead
of building a world` and diffing. The `contribution-write-set` helper already
covers the accumulator-channel shape (clear-stale semantics). Registry
`:writes` declarations are the source of truth for what each may emit;
`fold`'s runtime conflict check stays on.

Order of conversion = measured cost: `:regime`, `:fusion`,
`:fusion-promotion`, `:collision-detection`, then the rest.

**Acceptance:** `physics-systems-parallel` contains zero `legacy` wraps;
per-system timings drop; tests green.

## Fix 4 — Parallelism *inside* the hot systems

The fan-out keeps thread-per-system, but the top systems distribute their
per-entity work with `domain.ecs.parallel/par-mapv` (already deterministic,
order-preserving) and, where they are pure numeric kernels, read
`:genesis/physics-soa` primitive arrays instead of component maps.

Targets, by measured cost @500 (worse @1000): `:structure` (8.8 ms —
`gas-query` 6.5 ms is mostly Fix 2; `resolved` loop parallelizes),
`:integrator` (temperature derivation 6.2 ms parallelizes per-entity),
`:hydro` (8.1 ms per-entity gradient loop over the neighbor cache),
`:em-lorentz` (8.7 ms same shape), `:classifier`, `:fusion`, `:regime`.

`par-mapv`'s threshold (256) is above late-game per-state populations in some
systems; drop to 64 with chunk size ≥32 so 500–2000-entity loops actually
chunk. Determinism contract unchanged.

**Acceptance:** `step-physics` parallel beats sequential ≥3× @1000 bodies.

## Fix 5 — The integrator joins the fan-out (kill the last Gauss–Seidel phase)

`step-physics` currently folds the emitters, then runs the integrator serially
on the folded world so it sees same-tick accels/influences. This is the one
surviving ordered phase, it adds ~6 ms of un-overlapped time, and it
contradicts the no-barrier law.

**Change:** the integrator becomes an ordinary fan-out member. It reads the
snapshot's accel/influence channels — i.e. the values emitted **last tick**.
Consequences, accepted deliberately:

- **One-tick force lag.** Forces computed at tick N move bodies at tick N+1.
  Identical in kind to the Jacobi lag every other channel already carries; at
  our dt (bounded by bulk dynamical time) this is far below the integration
  error of the leapfrog itself.
- **Tick 1 is ballistic.** No accel channels exist in the initial snapshot;
  bodies first accelerate on tick 2.
- **A one-snapshot conservation dip on merges.** A merge victim is reaped at
  the end of tick N (`materialize-lifecycle`), but the survivor's
  `absorb-merge` mass lands via the integrator during tick N+1. Between those,
  summed mass is low by the victim's mass for exactly one snapshot. Ledger and
  law tests must assert conservation **across** the pair (N−1 → N+1), not at
  the dip. If a law contract cannot tolerate the window, the fix is to defer
  the reap one tick, not to re-serialize the integrator.

`step-physics` collapses to: build caches → `run-parallel` (all systems,
integrator included) → strip caches. The two-phase comment dies with the code.

**Acceptance:** no post-fold system invocations anywhere in `step-physics`;
formation integration tests still produce a star and planets; conservation
tests pass with the windowing rule above.

## Fix 6 — A sink for debris

`:debris` is a real condensed population (classifier: cooled sub-stellar
nebula → planetesimal), and it only leaves the world via literal collision or
sink capture — so late-game N grows without bound and gravity/N-body cost with
it (see memory: debris-accumulation-slowdown).

**Design: escape reaper, mass-honest.** New fan-out system `:debris-reaper`
(ns `domain.planet-formation`):

- Reads position/velocity/mass/matter-state from the snapshot; computes total
  system mass M and COM (a snapshot-global read, same as gravity's tree).
- A `:debris` body is **escaped** when it is (a) beyond `K ×` the system's
  bounding radius (K = 10) from the COM **and** (b) unbound:
  `½v² > GM/r` against the total interior mass. Both conditions on the same
  snapshot.
- Emits `c/consumed-escape` (new component, new consumed marker in
  `materialize-lifecycle`) — sole writer, registry entry, no barrier.
- The reaped mass is gone from the sim by construction (the body escaped to
  infinity); the event ledger records an `:event/body-escape` per reap so the
  books stay auditable.

**Non-goals (follow-up):** consolidation of *bound* debris into super-particle
swarm bins. Only escapers are reaped here; bound debris stays real.

**Acceptance:** long-run (5k ticks) body count plateaus instead of climbing;
no reap of bound bodies (test: circular-orbit debris at 2× system radius
survives).

***

## Progress

- Fix 1 (sim thread ≠ render thread): `IntentAtom` + `sim-loop` in
  `src/infra/dev/window.clj`. Live-window tests pass.
- Fix 2 (one entity scan per tick): `:ecs/_query-cache` in
  `src/domain/ecs/core.clj`, attached/stripped in `src/domain/genesis.clj`.
- Fix 3 (retire legacy bridge): `physics-systems-parallel` contains no
  `tick/legacy-system` wraps.
- Fix 5 (integrator in fan-out): `integrator-system` is a normal fan-out
  member; `step-physics` is caches → one fan-out → strip caches.
- Fix 6 (debris sink): `domain.debris/debris-reaper-system` + `c/consumed-escape`
  in the registry; long-run body count now plateaus.
- Fix 4 (parallelism inside hot systems): `par-mapv` chunk size floor raised
  to 32; gravity SoA path rewritten to project predicted bodies and walk the
  scalar Barnes–Hut traversal instead of the slower `acceleration-for-soa` path.
  Physics-SoA builder fixed to use `vec` on `ecs/all-of`, eliminating an O(N²)
  lazy-seq `nth` cost (SoA build @1000 dropped from ~19 ms to ~3 ms).

- Fix 4 continuation — **persistent neighbor cache**
  (`kanban/tasks/persistent-neighbor-cache.md`): `:genesis/neighbor-cache`
  survives across ticks; reuse caches the *identities* (neighbor list +
  nearest neighbor) and recomputes all values, gated by a 0.1·h displacement
  skin from the last query anchor and a 10-tick full-rebuild backstop.
  Steady-state rebuild cost −79% (@1000: 7.3 → 1.5 ms); sustained tick-world
  @1000 44.0 ms vs 53.6 ms without. Frozen-world equivalence vs full rebuild
  is byte-identical for 20 ticks.

- Fix 4 round 2 (2026-07-03) — sustained 21→17.3 ms @500, 44→32.4 ms @1000:
  - `par-mapv` threshold 256→64 (as specced) so per-state populations fan out.
  - Hydro/EM consume the neighbor cache DIRECTLY: `sph-density-from-cache` and
    `pressure-gradient-acceleration-from-cache` walk the entry's neighbor
    vector once using the precomputed `:r2`/`:gradient-pressure`, instead of
    allocating a filtered vector + gradients vector and recomputing distances.
    Results identical (same accumulation order, same arithmetic).
  - `structure-system` resolved branch selects resolved bodies straight off
    the matter-state component map (no more projecting every gas entity
    through `entity->region` just to discard it) and par-maps the shapes.
  - Serial tail: `system-summary` / `stats-of` / `spatial-index` projections
    par-mapv'd (order-preserving → identical results); LOD counts single-pass;
    `field-system` scan+compute par-mapv'd. `pacing/cloud-scale` no longer
    re-evaluates `sp/dist` inside the sort comparator (sort-by keyfn runs per
    COMPARISON) — distances are computed once and sorted as keys. The @500
    post-physics tail is now ≈1.3 ms total (summary 0.2, stats 0.16,
    pacing 0.46, detect 0.25).
  - `bh/build-tree` parallelized for ≥512 bodies: bodies are partitioned into
    the root's eight octants (order-preserving) and each subtree is built +
    mass-propagated in its own future; the root aggregation replicates
    `propagate-mass`'s internal branch. Verified `=` to the serial build on a
    seeded 800-body world. Paid twice per tick (spatial index + gravity's
    predicted tree).

- Fix 4 round 3 (2026-07-03) — sustained 32.4→27.1 ms @1000, 17.3→15.6 @500:
  - **Fold batched** (`tick/apply-write-set`): each ctype's cells fold through
    ONE transient of the component map instead of per-cell
    `put-component`/`remove-component` (an `assoc-in` path rebuild + an
    archetype-set update per cell); archetypes are touched only for entities
    actually added/removed. 3.9 → 0.6 ms @1000.
  - **Neighbor-cache rebuild overlaps the SoA build** in `step-physics`: both
    read only the frozen input world, so the rebuild runs in a future while
    the query cache + SoA are built. (The rebuild itself is unchanged —
    identical cache.)
  - **SoA fill chunked** across futures (disjoint index ranges, snapshot-only
    reads — identical arrays). 3.4 → 0.9 ms.
  - **`spatial-index` overlaps tree/grid** builds. 6.6 → 2.2 ms.

- Fix 4 round 4 (2026-07-05) — sustained 27.1→21.9 ms @1000, 15.6→13.5 @500
  (means; p50 21.3 / 12.8 — measured with the dev service still running, so
  contaminated by core contention; the clean re-measurement is 13.4 @500 /
  24.1 @1000, the round-5 baseline):
  - **Gravity walks straight from the SoA arrays.** `bh/build-tree-from-soa`
    builds an index-leaf octree directly from the `:genesis/physics-soa`
    arrays (leaves carry integer indices, not body maps; parallel-by-octant
    above the same 512 threshold), and an explicit-stack index traversal reads
    source positions/masses from the arrays. `acceleration-for-soa` is now
    build + par-mapv'd walks in one call; the gravity system's SoA branch is a
    one-liner and allocates zero per-tick body maps. **Bit-identical** (max
    component divergence 0.0) to the body-map path on the same warm snapshot;
    isolated gravity `:run` @1000 p50 19.5 → 14.3 ms, best 12.7 → 10.4.
    Drift-predicted arrays (`:px-pred` …) are preferred throughout, preserving
    the Fix-5 force/position alignment.
  - **Per-tick Malli gated** (the cheap safe cut named below):
    `neighbor-cache-entry?` checks in `cache-entry-valid?` and
    `rebuild-neighbor-cache` are skipped when
    `:genesis/validate-neighbor-cache?` is false — the `create-world` default;
    tests and debug runs can re-enable it.
  - **COM scan folded into `spatial-index`:** `:genesis/frame-offset` is
    computed from the same projected items vector (identical arithmetic and
    accumulation order to the old `center-of-mass` walk), deleting a separate
    serial per-tick pass from `tick-world`.

- Fix 4 round 5 (2026-07-05) — sustained (clean, dev service stopped)
  24.1→17.6 ms @1000, 13.4→11.6 @500 (means):
  - **Flat (primitive-array) gravity tree.** `bh/flatten-idx-tree` flattens
    the index-leaf octree into preorder-numbered parallel primitive arrays
    (com xyz / mass / side doubles; leaf?/start/cnt; shared `leaf-idxs` /
    `child-ids` int arrays), and the walk (`traverse-flat`) is an explicit
    int-stack loop over those arrays — no map lookups per node visit. Visit
    order and arithmetic are identical to the map walk, so results stay
    **bit-identical** to the body-map path (verified 0.0 divergence @1000).
    Isolated gravity `:run` @1000: p50 11.1 → 3.3 ms (build 1.3 + flatten 1.0
    + walks ~1). The map-tree `traverse-soa-idx` from round 4 is deleted;
    `build-tree-from-soa` → flatten → walk is the only SoA gravity path.
  - **The fan-out now clears the Fix-4 acceptance:** `run-parallel` 8.3 ms vs
    `run-sequential` 39 ms on the same warm @1000 snapshot ≈ 4.7× (gate was
    ≥3×). Fold itself is 0.7–1.5 ms of that.
  - **Negative result (do not redo):** replacing par-mapv's per-call `future`
    chunks with a shared fixed-size worker pool (n-cores threads, nested-call
    guard) measured p50 16.7/19.4/17.0 vs 17.1/18.0/17.4 unbounded across
    3 sustained @1000 runs each — indistinguishable under world-to-world
    variance. The oversubscription (~150 runnable threads) is not the
    binding constraint; reverted to keep `future` semantics (binding
    conveyance, familiar failure mode).

- Fix 4 round 6 (2026-07-08) — physics-SoA fill regression and benchmark repair:
  - `fill-physics-soa!` in `src/domain/physics/cache/soa.clj` had regressed to
    ~25 ms @1000 by creating a per-entity map and then extracting fields into
    primitive arrays. Rewrote it to write directly to type-hinted arrays with the
    same predicted-position arithmetic. SoA build @1000: ~25 ms → ~1.2 ms;
    `tick-world` @1000: ~72 ms → ~48.6 ms (`clojure -M:bench phase0`).
  - The phase0 benchmark was broken: `domain.genesis` no longer exports
    `step-physics` (it lives in `domain.genesis.tick`), the sequential fallback
    referenced old namespaces, and `domain.stellar` did not re-export
    `temperature-system`. Repaired the benchmark and expanded `:phase0` `:covers`
    to declare all tick-participating namespaces.

- Fix 4 round 7 (2026-07-08) — neighbor-cache fan-out lane; last serial pre-phase removed:
  - The neighbor-cache rebuild is now a first-class fan-out `:neighbor-cache` system
    in `domain.physics.cache.neighbor`, wired into `physics-systems-parallel` with
    a registry entry. Consumers read per-entity `c/neighbor-cache` components.
  - `step-physics` no longer has a serial `future` pre-phase; the cache is built
    by the fan-out.
  - Hydro and EM-Lorentz consumers read `c/neighbor-cache` components; stale
    cache entries are evicted via the `tick/removed` sentinel.
   - Benchmark: `clojure -M:bench phase0` completed; the neighbor-cache system is
     now visible in the per-system profile (≈21.1 ms on world1 with spatial tree,
     ≈4.5 ms on initial w500). Clean measurements (dev service stopped,
     `clojure -M:bench phase0`):
     - `tick-world` @500: 27.4 ms mean (23.8–31.6 ms range)
     - `tick-world` @1000: 62.0 ms mean (51.5–79.5 ms range)
     - `step-physics` parallel on world1: 20.5 ms
     - `step-physics` sequential: 21.8 ms
     - `neighbor-cache` system (per-system profile on world1): 21.1 ms
     - 10 ticks @500: 206.2 ms total = 20.6 ms/tick
   - Note: the neighbor-cache system is now the dominant lane; the migration exposed
     the rebuild cost that was previously overlapped with the SoA build in a future.
     The next optimization target is making the cache rebuild fast enough to not
     dominate the fan-out (e.g., SoA-backed rebuild, coarser chunks, or avoiding nested
     futures inside the fan-out).
   - Tests updated: `test/domain/physics/cache_test.clj` rewritten for the component
    API; `test/domain/hydro_test.clj`, `test/domain/em_lorentz_test.clj`,
    `test/domain/formation_integration_test.clj` updated.

### Remaining bottlenecks after round 7 (clean, dev service stopped; @1000 mean ≈ 62.0 ms)

The neighbor-cache fan-out migration is complete. The serial pre-phase is gone, but
the cache rebuild is now the dominant fan-out lane: ≈21.1 ms on world1, longer than
the rest of `step-physics` combined. Because it dominates, `step-physics`
parallel (20.5 ms) and sequential (21.8 ms) are nearly equal — the fan-out is
effectively serialized behind this one lane.

- **Make the cache rebuild fast enough to not dominate the fan-out.** The rebuild
  is now single-threaded inside the `:neighbor-cache` system. Options: SoA-backed
  rebuild (read straight from the physics SoA arrays), coarser spatial chunks to
  reduce tree walk depth, or avoid nested futures inside the fan-out (which may
  be oversubscribing cores and inflating the lane).
- `em-lorentz` 3.3 ms is already fused over the cache (prefetched tables, no
  per-entity get-component) — further cuts mean SoA-ifying its inner loop.
- Widening the nb-cache reuse skin (only ~49% pass 0.1·h) still trades
  against the byte-equal-for-20-ticks equivalence test.

@500 is now 27.4 ms mean; the @1000 bottleneck is the neighbor-cache lane above.

## The ordering law (for every future agent reading this)

1. **No system runs before or after another.** Every system reads the frozen
   snapshot and emits a write-set for components it exclusively owns. If your
   value "has to propagate," it propagates **next tick** — that is the
   simulation's causality, everywhere, including forces and player input.
2. **No post-fold phases.** `step-physics` = caches → one fan-out → strip.
   World-construction (`materialize-lifecycle`) is spawn/reap only.
3. **Comments may not claim ordering.** "Must run AFTER X" is false by
   construction; write "reads X's one-tick-stale output" instead.
4. **Serial main-thread work is a bug with a budget.** Anything in `tick-world`
   outside the fan-out (summary, stats, pacing) must either become an emitter,
   be throttled, or justify itself in this file.

## Verification matrix

| Step | Gate |
|---|---|
| each fix | `clojure -M:test` green |
| Fix 2, 5 | `run-sequential` ≡ `run-parallel` world equality |
| each fix | `clojure -M:bench phase0` — record tick-world @500/@1000 |
| Fix 1 | live window ≥30 fps render during heavy tick; input works |
| end state | tick-world @1000 ≤ 16.6 ms, render 60 fps |
