---
category: "specs"
labels: ["perf", "phase0", "spec"]
write-id: "1784777249604-0.5an90nmmww73fho5h6t"
source: "kanban/tasks/perf-tick-residual-gap-to-60fps.md"
title: "Perf: residual tick-cost gap to the 16.6 ms 60 fps budget @1000"
priority: "P1"
status: "in_progress"
estimate: "3"
uuid: "perf-tick-residual-gap-to-60fps"
created_at: "2026-07-10T00:00:00Z"
---

# Perf: residual tick-cost gap to the 16.6 ms 60 fps budget @1000

> Successor to the closed umbrella `kanban/tasks/perf-60fps-parallel-tick.md`.
> The concrete perf sub-tasks that umbrella tracked have landed:
> `persistent-neighbor-cache.md` (done), `spec-neighbor-cache-fan-out-lane.md`,
> `spec-soa-primitive-array-physics-cache.md`. This card carries only the
> **remaining gap** to a sustained 16.6 ms/tick at 1000 particles.

**Problem:** tick cost at 1000 particles is still above the 60 fps budget after
the cache/SoA/fan-out work. Exact current number must be re-measured — the
ledger holds conflicting figures (~17.6 ms and ~44 ms) taken in different
contexts/dates, so this card must **not** assume one.

**First slice (this is a profiling-then-fix task, not a blind optimization):**
1. `pm2 stop gates-of-truth-dev` (bench hygiene — core contention skews numbers),
   then `bin/bench :phase0` / `bin/bench :profile` to get a current, honest
   per-segment breakdown @500 and @1000. Restart the dev service after.
2. Identify the single largest remaining serial/hot segment.
3. Scope ONE ≤5-point fix against it; if the fix is itself >5, split.

**Done when (plus global DoD):**
- A current profiling breakdown is recorded on this card (segment → ms @1000).
- The next concrete optimization is scoped (or implemented if ≤5) with a
  before/after `bin/bench` delta.
- Physics unchanged: byte-equivalence / windowed-equivalence contract holds
  (see `persistent-neighbor-cache.md` §4 pattern).

---
Triage 2026-07-10: sized 3 but first slice is profiling, not a blind fix. Moved to breakdown to capture current benchmark baseline and scope the single largest hot segment.

Triage 2026-07-10: already scoped 3pt with clear first slice (profile, identify hot segment, scope fix). Moved to ready.

BASELINE 2026-07-22 (dev service stopped, criterium means, this branch incl. voxel/chemistry work): tick-world 100p=5.2ms, 500p=25.1-27.1ms, 1000p=55.5-59.1ms — budget 16.6ms missed ~3.4x @1000. Named system segments @500: merged-hydro-em 2.6ms, stellar-structure 0.92, density 0.79, gravity 0.65, regime 0.65, classifier 0.59, temperature 0.59, motion 0.57, collision 0.40 (sum ~8.8ms). full parallel step-physics 11-15ms; tick-world 25-27ms => ~11ms UNATTRIBUTED GAP between sum-of-parts and full tick (serialization/barrier/write-set merge overhead per bench interpretation guide) — bigger than any named system. advance-tick+spatial-index 3.4ms; non-physics overhead 1.3ms. Dispatching analysis agent to attribute the gap and scope one fix. ready -> in_progress (already).

ATTRIBUTION 2026-07-22 (analysis agent, warm-JIT probes — bench/gates_of_truth/bench/{attribution,equivalence,folddelta}.clj, WORKING TREE ONLY, uncommitted): the "~11ms gap" is now fully attributed. The card's 8.8ms named sum covered only 9 of 39 systems; the true all-systems sequential sum @500 is 22.5–27ms. Tick anatomy @500 (ms, warm means): spatial-index 2.6–3.4 | physics-soa build 1.1–1.4 | parallel system execution 10.4–14.3 | write-set fold 1.8–2.7 (serial, post-barrier) | post-physics serial (summary/stats/pacing/promotions/ecology) 1.2–1.6 | future spawn/barrier orchestration 0.06–0.15 | conflict check 0.01. Per-system isolated :run: hydro-em 5.2, gravity 4.9, structure 3.3, integrator 3.2, neighbor-cache 2.9, remaining 34 systems sum ~3.9. Key structural facts: (1) orchestration is FREE (0.06ms noop probe) — not the gap; (2) the big-5 in parallel take 11.9–13.1ms vs 5.2ms isolated max — ~2.3× concurrent slowdown = CPU saturation + memory contention, NOT scheduling waste; (3) inner par-mapv fan-out is load-bearing: forcing it sequential makes run-parallel WORSE (26.5ms ≈ no speedup); (4) CHM query-cache cold-vs-warm vs no-cache all within noise — not a contention point. The machine is genuinely CPU-bound during the fan-out; the only remaining serial fat was the fold.

FIX IMPLEMENTED 2026-07-22 (working tree, uncommitted): completion-order overlapped fold in `domain.ecs.tick/run-parallel` — write-sets now fold on the calling thread AS each system's future completes (LinkedBlockingQueue), overlapping the ~2.4ms serial fold with the tail of the still-running systems instead of starting cold at the barrier. `:last-wins` keeps declaration-order semantics; system exceptions re-raised as ExecutionException exactly as future-deref did; `:genesis/_profile` merged in declaration order so even bench diagnostics are deterministic. Equivalence evidence (persistent-neighbor-cache §4 pattern): 12 consecutive ticks from {100,500,1000}p × {profiling off,on} — normalized worlds (event UUIDs + profile timings stripped, both inherently nondeterministic across ANY two runs) IDENTICAL in all 6 configs vs the pre-change implementation; all :components maps identical. Tests: 777/14372 green (baseline), architecture-test 6/23 green, write-conflicts {}. Criterium deltas (noisy box, ±3ms noise floor): @1000 run-parallel 33.2–35.6 NEW vs 35.8–37.3 OLD, tick-world 40.6–41.0 NEW vs 42.9–47.8 OLD — never worse, trend −1..−4ms @1000; @500 within noise.

NEXT SLICE (recommended): the residual gap is now dominated by total CPU WORK in the big-5, not overhead. Candidates in rank order: (1) hydro/gas-structure (:structure, 3.3ms) and :hydro-em (5.2ms) and :neighbor-cache (2.9ms) all walk the same neighbor sets — a shared SoA pair-loop or a staleness-budgeted density pass (needs windowed-equivalence decision, physics-adjacent); (2) SoA-ify the hydro-em pair loop (positions/density/pressure already partially in SoA); (3) bench hygiene — background pm2 tenants (knoxx etc.) put a ±3ms noise floor over @500 signals; future bench cards should quiesce them or pin to @1000 where signal > noise.

First slice complete + reviewed 2026-07-22. ATTRIBUTION @500 (probe-grounded): the ~11ms 'gap' was 30 unmeasured systems — true all-systems sequential sum 22.5-27ms; big-5 (hydro-em 5.2, gravity 4.9, structure 3.3, integrator 3.2, neighbor-cache 2.9) suffer ~2.3x concurrent slowdown under CPU/memory saturation. Orchestration overhead falsified (0.06ms); CHM convoy falsified; nested par-mapv oversubscription falsified (kill-switch made it WORSE — inner fan-out load-bearing). FIX LANDED: completion-order overlapped fold (fold hides under the big-5 tail; commutative by single-writer disjointness; conflict reports sorted back to declaration order; exceptions = ExecutionException, orphaned-future semantics identical to old). Equivalence: 12 ticks x {100,500,1000}p x {profile on/off}, :components byte-identical all 6 configs. Bench @1000: tick-world 40.6-41.0 new vs 42.9-47.8 old (never worse, trend -1..-4ms; box noise ±3ms). Review PASS-WITH-NITS (stale ns docstring + interrupt assumption both fixed). RESIDUAL GAP = big-5 CPU work: next slice scoped — shared SoA pair-loop / staleness-budgeted density pass across structure+hydro-em+neighbor-cache (they walk the same neighbor sets; needs windowed-equivalence decision); bench hygiene: pin future bench cards to @1000. Probes committed in bench/gates_of_truth/bench/{attribution,equivalence,folddelta}.clj. Card STAYS in_progress — residual gap remains (55-59ms @1000 baseline vs 16.6 budget), next slice is the big-5 neighbor-set sharing.
---