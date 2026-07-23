---
category: "specs"
labels: ["perf", "phase0", "spec"]
write-id: "1784782942136-0.m7j3xc4nc8ck26nepa"
source: "kanban/tasks/perf-big5-shared-neighbor-pass.md"
title: "Perf: big-5 shared neighbor pass (staleness-budgeted)"
priority: "P1"
status: "done"
estimate: "5"
uuid: "perf-big5-shared-neighbor-pass"
created_at: "2026-07-22T00:00:00Z"
---

# Perf: big-5 shared neighbor pass (staleness-budgeted)

> Parent: `kanban/tasks/perf-tick-residual-gap-to-60fps.md` (attribution slice
> done 2026-07-22: residual gap is big-5 CPU work, not overhead).

**Finding (from attribution):** `:structure`, `hydro-em`, and `:neighbor-cache`
all walk the same neighbor sets per tick — three full pair-loops over the same
geometry. Isolated max 5.2 ms; ~2.3× concurrent slowdown under saturation.

**Owner decision 2026-07-22:** staleness-budgeted shared pass approved;
byte-equivalence relaxed to a **windowed-equivalence contract** — worlds must
match within a documented epsilon over a tick window (see
`persistent-neighbor-cache.md` §4 pattern for the equivalence-test shape).

## Scope

- ONE shared SoA pair-loop producing the neighbor/pair data all three
  consumers read (structure, hydro-em, neighbor-cache) — one walk instead of
  three.
- Density (and any other budget-safe consumer) may go **stale within a
  documented budget** (recompute every N ticks, or on accumulated displacement
  exceeding a threshold — pick the honest trigger and document it).
- Windowed-equivalence contract: epsilon + window documented in the code and
  on this card; equivalence bench proves worlds stay within it (extend
  `bench/gates_of_truth/bench/equivalence.clj`).
- Physics constants in `law/`; the budget is a tunable from day one.

## Done when

- One pair-walk instead of three; staleness budget wired with tunable in `law/`.
- Windowed-equivalence bench passes with the documented epsilon/window.
- `bin/bench :phase0` before/after delta recorded here (dev service stopped;
  pin claims to @1000 — box noise is ±3 ms @500).
- `clojure -M:test` + `architecture-test` green; `write-conflicts {}`.

---
Created 2026-07-22: scoped by the attribution agent, approved by owner
(staleness-budgeted shared pass over byte-exact shared walk).
---

## IMPLEMENTED 2026-07-22 (working tree, uncommitted)

**Shared-walk design.** The `:neighbor-cache` fan-out system's entry build
(`domain.physics.cache.neighbor`) IS the one shared pair walk: per cache entry
it now attaches, in the same pass that computes r2, the in-kernel pair kernel
gradient `:grad` (h_ij = r_i + r_j, computed once — receipts 2026-07-09 already
showed cache-side gradients beat consumer-side recompute by ~11 ms), and, for
gas parcels, the staleness-budgeted `:density-estimate` summed post-pass with
the same `sph-density-from-cache` the consumer used (bit-equal on the same
inputs). The merged hydro/EM force cell reads `:grad` (zero kernel evals on the
hot path; the on-demand recompute is the cold fallback and is bit-equal); the
Structure gas branch reads `:density-estimate` in O(1) — its neighbor walk is
gone. No new component: the pair products ride `c/neighbor-cache`, sole-written
by `:neighbor-cache`, already read by both consumers (one-tick Jacobi lag
unchanged). Registry/system `:reads` for `:structure` now declare the
`c/neighbor-cache` read that already existed.

**Staleness trigger + law/ tunables.** Estimate recomputed on ANY of: fresh
neighbor query; displacement > 0.05·h from the estimate anchor; h drift > 5%
relative (self-term ∝ h⁻³; also the neighbor-approaching signal); parcel mass
drift > 5% relative (self-term ∝ m — mass-transfer moves density without
moving the parcel; found via the equivalence bench, see below); age ≥ 4 ticks.
Knobs: `law.field/density-stale-displacement-fraction` (0.05) and
`law.field/density-stale-max-ticks` (4), world-overridable via
`:genesis/density-stale-displacement-fraction` /
`:genesis/density-stale-max-ticks`; max-ticks 1 = fresh mode (equivalence
reference).

**Windowed equivalence** (`bench/gates_of_truth/bench/equivalence.clj`
`windowed-equivalence-report`, old byte-equivalence path untouched): 24-tick
window, budgeted vs fresh-mode worlds in lockstep, {100,500,1000}p. Epsilons
(relative): position/velocity 1e-3, mass 1e-9, density/pressure/radius/
temperature 5e-2. Observed max drift: density/pressure 6.5e-3 @100, 4.3e-3
@500, 1.4e-2 @1000 — bounded oscillation (spike → recompute → drop), all PASS;
position/velocity/mass/radius/temperature drift EXACTLY 0.0 over the whole
window at all sizes (the lag has not propagated to dynamics). First-pass
budget (displacement+age only) FAILED this bench with 5.8× density spikes on
isolated parcels — h-drift and mass-drift triggers were added in response;
that is the documented evidence trail for the trigger set.

**Bench** (`bin/bench :phase0`, dev service stopped, same box): tick-world
@1000 mean 35.8 ms (33.2–39.4) → 33.4 ms (31.7–35.0), −2.4 ms; @500 16.9 →
15.2 ms (within ±3 ms noise, consistent direction); @100 3.2 → 3.3 ms (noise).
Per-system profile @500: hydro-em 8.57→5.46 ms, structure 6.16→2.53 ms
(gas-query 5.52→2.30 — residual is the per-entity projection, not a walk),
neighbor-cache 1.55→0.85 ms; big-3 total 16.3→8.8 ms.

**Tests:** 783 tests / 14949 assertions, 0 failures 0 errors (baseline
777/14372; +6 new cache-test budget/pair-term tests). architecture-test 6/23
green. write-conflicts {}.

**Deviation (owner-approved relaxation applied):**
`persistent-cache-matches-full-rebuild` (formation-integration-test) asserts
BYTE equality between persistent-cache and full-rebuild worlds; any real
staleness budget breaks that in the last ulp (a carried estimate embeds
compute-time h/m, positions drift even at dt=0). The test now pins both worlds
to fresh mode (`:genesis/density-stale-max-ticks 1`) — its subject is cache
identity-reuse equivalence, which it still asserts byte-exactly; budget
behaviour is pinned by the new cache-test budget tests and the
windowed-equivalence contract. Not weakened: same 10-tick byte assertion, same
two cache modes.

## REVIEW NITS CLOSED 2026-07-22 (PASS-WITH-NITS, deviation accepted)

- **Nit A (doc lie):** the displacement trigger now scales with `:density-h`
  (the kernel support the stale estimate was actually summed over), matching
  the docstring — that is the physically honest scale: once the parcel moves a
  fraction of the support it summed, the geometry the estimate describes has
  shifted. `(:h prev-entry)` was the identity skin's scale, a different
  contract. Doc extended to say exactly this.
- **Nit B (coverage hole):** three new cache-test tests pin the bench-found
  triggers — mass-drift >5% forces refresh, h-drift >5% forces refresh
  (nearest neighbor moved 11% closer; precondition asserted ≥5%), and
  sub-threshold h+mass drift with all other triggers quiet carries the
  estimate unchanged. Deleting either trigger branch now fails the suite.
- **Nit C (window mid-rise):** windowed bench extended to 48 ticks at all
  sizes. @1000 tail: 1.44e-2@24 → 1.45e-2@28 → 4.9e-3@32 → 5.8e-3@36 →
  5.0e-3@40 → 1.49e-2@44 → 4.8e-3@48 — bounded oscillation (spike → recompute
  → drop), NOT a monotonic rise; the tick-24 "max spike" was the first
  oscillation crest. Max density drift 1.49e-2 vs eps 5e-2; position/velocity/
  mass/radius/temperature drift exactly 0.0 over all 48 ticks at all sizes.
  Contract PASS.

Re-verified: 786 tests / 14960 assertions 0 failures 0 errors;
architecture-test 6/23 green; write-conflicts {}. Dev service stopped for
bench hygiene (left stopped; orchestrator restarts).

---
Complete + reviewed 2026-07-22. :neighbor-cache's entry build is now the ONE shared pair walk — attaches :grad (kernel gradient, bit-equal vs old consumer math, pinned) + staleness-budgeted :density-estimate for gas; hydro-em reads :grad (zero hot-path kernel evals), structure's gas walk DELETED (O(1) estimate read). Staleness triggers: displacement > 0.05 x density-h (nit A: honest kernel-support scale), h-drift > 5%, mass-drift > 5%, age >= 4 — tunables in law.field, max-ticks 1 = fresh reference mode; ALL trigger branches now unit-tested (nit B). Windowed-equivalence 48 ticks: density drift oscillates 1.49e-2 max vs 5e-2 eps (spikes crest and FALL — bounded, nit C), pos/vel/mass/radius/temp drift EXACTLY 0.0 all 48 ticks all sizes. Bench @1000: tick-world 35.8 -> 33.4ms (-2.4, above ±3ms noise? borderline but consistent direction); big-3 @500: 16.3 -> 8.8ms (hydro-em 8.6->5.5, structure 6.2->2.5, neighbor-cache 1.5->0.9). Also fixed: :structure's previously-undeclared c/neighbor-cache read now declared (latent violation caught). Flagged deviation (byte-exact cache test pinned to fresh mode) review-ACCEPTED per owner pre-approval. Suite 786/14960 green; architecture green; write-conflicts {}. in_progress -> done.
---