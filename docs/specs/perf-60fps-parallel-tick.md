# 60 fps: closing the gap between the Jacobi architecture and the wall clock

**Date:** 2026-07-03
**Status:** In progress — Fixes 1, 2, 3, 5, 6 landed; Fix 4 in progress.
**Baseline:** `clojure -M:bench phase0` on 16 cores — `tick-world` 42.8 ms @500
bodies (23 fps), 113.9 ms @1000 (8.8 fps). `step-physics` parallel 33.4 ms vs
sequential 28.4 ms: the thread-per-system fan-out yields **1.36×** on 16 cores.
The dev window additionally serializes tick + render + a hard 16 ms sleep on one
thread, which is how 43 ms of physics becomes ~12 fps on screen.

**Current (2026-07-03, after Fix 4 rounds 2–3 — see Progress):** sustained
warm-cache ticking (the number the live sim sees) ≈ 15.6 ms @500 / 27.1 ms
@1000 (was 21 / 44 this morning). Cold-cache `tick-world` is roughly unchanged
(~19 @500 / ~49 @1000) because the forced full cache rebuild dominates cold
ticks.

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

---

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

---

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
  (`docs/specs/persistent-neighbor-cache.md`): `:genesis/neighbor-cache`
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

### Remaining bottlenecks (measured @1000 bodies, warm snapshot, tick 16; sustained ≈ 27 ms)

- **`gravity` ≈ 9 ms on its thread — the fan-out floor.** Project bodies from
  SoA 0.7 + private predicted-position tree build 3.0 (parallel-by-octant) +
  BH walks 6.9 (par-mapv, 41 ms serial — parallelizes fine; tree is healthy,
  max depth 9). Next lever: walk from the SoA arrays without allocating 1000
  body maps, and/or amortize the predicted tree.
- **`rebuild-neighbor-cache` ≈ 5 ms warm** (now overlapped with the ~1 ms SoA
  build, so ~4 ms of it is still exposed serial time). Entries average ONE
  neighbor (self; sub-threshold gas by design) — the cost is per-entity
  overhead: only ~49% of entries pass the 0.1·h reuse skin (h = 0.013·d_nn is
  tiny), each miss pays a `query-nearest` descent (~27 µs) + grid query, and
  Malli `neighbor-cache-entry?` runs ~2×/entity/tick (~3 µs each). Widening
  the skin trades against the byte-equal-for-20-ticks equivalence test;
  gating the per-tick Malli behind a flag is the cheap safe cut.
- `em-lorentz` ≈ 3 ms; `spatial-index` ≈ 2.2 ms serial pre; post-fold tail
  (summary 0.3 + stats 0.3 + pacing 1.0 + events/materialize) ≈ 2.5 ms;
  everything else ≤ 2 ms.

Warm best-of-5 `tick-world` @1000 ≈ 24 ms (sustained mean 27 — the tail above
p50 is GC + the every-10th-tick full rebuild). To reach 16.6 ms @1000 the
remaining moves are gravity's walk/tree, the neighbor-cache miss rate, and
overlapping `spatial-index` with the COM/advance segment.

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
