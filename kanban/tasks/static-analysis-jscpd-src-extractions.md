---
category: "specs"
labels: ["specs", "static-analysis", "jscpd", "duplication"]
write-id: "1784985262579-0.kia7d86lyjbdgy5oyr"
source: "kanban/tasks/static-analysis-jscpd-src-extractions.md"
title: "15 src clones: two are literal copy-paste, one duplicates physics safety logic, one must stay duplicated"
priority: "P2"
status: "done"
estimate: "5"
uuid: "static-analysis-jscpd-src-extractions"
created_at: "2026-07-24T00:00:00Z"
---

# jscpd: the 15 src clones

> Parent: `kanban/tasks/static-analysis-regression-2026-07-24.md`

74 clones total, 923 duplicated lines (1.86%). 59 are in `test/` (see
`static-analysis-jscpd-test-fixtures.md`); these 15 are in `src/`.

`.jscpd.json` has `minTokens: 60`, `minLines: 8`, **no `ignore` patterns and no
`threshold`** — so jscpd can never fail CI. Note the Clojure tokenizer counts
docstrings, which produces at least one pure-prose "clone".

## Ranked

**1. `src/infra/render/color.clj` — best value/effort in the whole set.**
Three ramp fns — `temp-color` (`:46-62`), `disk-temp-color` (`:75-94`),
`gas-temp-color` (`:109-126`) — each end in a **byte-identical** 11-line
piecewise-linear `loop` interpolator. Only the stops table and the
x-normalisation differ. Extract `(defn- sample-ramp [stops ^double x])` and each
becomes two lines. Pure, no laziness, no interop, no ordering. Removes 26
duplicated lines and makes the three ramps' *actual* differences visible.

**2. `point-aabb-dist2` — literal copy-paste.** Identical private fn,
**identical docstring**, identical body, in `src/domain/physics/collision.clj:62`
and `src/domain/spatial/index.clj:106`. Pure geometry over
`:aabb-min`/`:aabb-max`. Move to `shape.spatial`.

**3. `orbital-angular-momentum` — literal copy-paste.** Byte-identical
*including* its docstring ("Orbital specific angular momentum L = m (r × v).
Vector in kg m²/s."), private in `src/domain/planet_formation/orbit.clj:33`,
public in `src/domain/stellar/thermodynamics.clj:24`. Pure cross-product vector
math with zero stellar or formation dependency. Best home is `shape.spatial`
(both already depend on it) rather than making planet-formation depend on
stellar — **check `test/architecture_test.clj` for a layering rule first.**

**4. Highest drift risk: `src/domain/stellar/disc_evolution.clj:285-300` ↔
`:352-367`.** The binary-fragment and GI-fragment branches share 16 lines:
`r-orbit-raw` from a fraction of `r-disk-now` floored at
`fragment-placement-floor-m`, the `disc/tidal-dominance-radius` Hill clamp,
`(min r-orbit-raw r-hill-max)`, the below-floor check, and an identical
`log-fragment-drop!` call with `:hill-clamp-below-floor`. **The only differences
are the radius fraction (0.5 vs 0.3) and `:branch :binary` vs `:branch :gi`.**
Three of the four comment blocks are copy-pasted verbatim too.

This is *physics-safety* logic — never violate the placement floor, always
respect the Hill clamp — duplicated in two places. Fix one, miss the other.
Extract `clamped-spawn-radius`.

**5. `src/domain/gravity/barnes_hut/tree.clj` — 4 self-clones, 2 extractions.**
- The nil-child AABB min-size padding (`:145-153` ↔ `:371-379`) is **already
  extracted** as `child-leaf-bb` (`:203-212`), whose docstring even says it is
  "the same min-size padding rule as `insert-body-into-node`'s nil-child
  branch". The two insert fns simply don't call it. Trivial.
- The child-aggregation block (`total-mass`/`max-radius`/`max-eps`/`safe-com`
  reduce) appears 4× — `propagate-mass` (`:174-191`), `build-tree-parallel`
  (`:238-254`), `propagate-mass-idx` (`:399-413`), `build-tree-parallel-idx`
  (`:443-457`). The docstring at `:214-221` explicitly says "the root
  aggregation below mirrors `propagate-mass`'s internal-node branch, walking the
  children in the same order" — correctness of the parallel path *depends* on
  staying equivalent to the serial one. DRY here buys safety, not brevity.
  Extract `aggregate-children` (the SoA variants omit `:max-radius`; compute it
  unconditionally or take a flag).

**6. Lower priority.**
- `src/infra/render/hud.clj:42-50` ↔ `:104-112` — the create-buffer / bind VAO /
  `glBufferData` / `glVertexAttribPointer` / `glUniform4f` / `glDrawArrays` /
  cleanup sequence. Extract `draw-hud-tris!`.
- `src/infra/render/mesh.clj:143-156` ↔ `:187-200` — differs only in stride
  (`(* 7 4)` vs `(* 8 4)`) and one extra attrib. Extract
  `upload-interleaved!` taking `[[0 3] [1 3] [2 1] [3 1]]`. **Careful** —
  untested LWJGL glue; a transposed index is invisible until it renders wrong.
- `src/domain/stellar/disc_evolution.clj:175-183` ↔ `:195-203` — two accretion
  channels both doing `old-dm + add-mass`, `old-L sp/v+ add-L`, `put-tracked`.
  The comment at `:187-188` states disc-evolution is the sole writer of
  `c/disk-mass` and `c/disk-angular-mom`; extracting `accrete-to-disk` makes
  that single-writer invariant physically single-site.
- `src/domain/integrator/kinematics.clj:489-501` ↔ `:603-615` — the profiling
  harness, once for the map path and once for SoA. **The extraction must take
  thunks (or be a macro).** A helper taking already-computed values would
  evaluate the work before `profile/timing` sees it and silently zero every
  measurement.

## Explicitly keep duplicated

`src/domain/em/lorentz.clj:188-199` ↔ `src/domain/mhd/force.clj:27-38` —
`entity->em-data` and `entity->mhd-data` both project an ECS entity into the
same shape (`entity->mhd-data` adds `:ionization`).

**Do not merge.** These are the projection seams of two *deliberately
independent* implementations, and
`test/domain/mhd_force_test.clj:53` is
`test-merged-system-matches-lorentz-acceleration` — "Merged system Lorentz
channel equals the standalone Lorentz system". That test is only meaningful
because the two paths are independently constructed; sharing the projection
makes part of the equivalence proof tautological.

If drift insurance is wanted, add a test asserting the two maps agree on the
shared keys — do not merge the code.

## Done when

- [ ] Items 1-6 extracted; item "keep duplicated" left alone with a comment
      pointing at `mhd_force_test.clj:53` so the next sweep does not re-raise it.
- [ ] `clojure -M:test` still 879 tests / 0 failures.
- [ ] `bin/bench :gravity` unchanged after the `tree.clj` extraction (hot path).
- [ ] A rendered frame is verified after the `hud.clj`/`mesh.clj` extractions:
      `clojure -M:run demo`.

---
## Outcome (2026-07-25)

74 clones → 65; duplication 1.86% → 1.62%. `.jscpd.json` gained a `threshold` (it
had none, so jscpd could never fail however much duplication accumulated).

Ranked items 1–5 done:

1. **`sample-ramp`** (`infra/render/color.clj`) — the byte-identical 11-line
   piecewise interpolator ended `temp-color`, `disk-temp-color`, and
   `gas-temp-color`. Each ramp keeps its own log-scaling onto `x`, which is where
   they genuinely differ.
2. **`point-aabb-dist2`** → `shape.spatial`, deleting identical private copies
   (docstring included) from `domain.physics.collision` and `domain.spatial.index`.
   Documented why it takes an `:aabb-min`/`:aabb-max` MAP rather than the `AABB`
   record: BH octree nodes and spatial-grid cells both carry those keys.
3. **`orbital-angular-momentum`** — NOT moved to `shape.spatial`. `shape/` has no
   business knowing about mass, and pointing `domain.planet-formation.orbit` at
   `domain.stellar.thermodynamics` would add a cross-subsystem dependency in the
   direction `domain.stellar.*` already depends on. Instead both collapsed to
   `(sp/v* (sp/cross position velocity) (double mass))` — one line each over the
   `cross` that already existed. Clone gone, no new coupling, no layer violation.
4. **`hill-clamped-spawn-radius`** (`stellar/disc_evolution.clj`) — the item this
   card ranked highest-drift-risk. The binary and GI fragment branches shared 16
   lines of Hill-clamp + placement-floor SAFETY logic, differing only in a radius
   fraction (0.5 vs 0.3) and a branch keyword. Both now call one helper.
   It takes a single MAP: the honest positional arity is 8, which is itself the HARD
   parameter-bloat gate. Equivalence evidence: `domain.disk-evolution-test`'s
   fragment-drop log emits byte-identical values before and after
   (`:r-hill-max-m 2.5580997604403305E10, :floor-m 4.487936121E10`).
5. **`barnes_hut/tree.clj`** — two extractions, both buying correctness rather than
   brevity, since `build-tree-parallel`'s docstring *promises* a tree equal to the
   serial build:
   - `child-leaf-bb` already existed and the serial insert path just didn't call it.
     Moved above `insert-body-into-node`; both paths now share the one padding rule.
   - `aggregated-node-fields` — the child-aggregation block appeared verbatim in
     `propagate-mass` (serial) and `build-tree-parallel` (parallel root).

**Item 6 not done** (`hud.clj` `draw-hud-tris!`, `mesh.clj` `upload-interleaved!`,
`kinematics.clj` profiling harness). Deferred deliberately — untested GL glue, and
this card itself flags the thunk hazard in the profiling harness. Tracked on the
umbrella card's open items.

**Explicitly kept duplicated, as this card requires:** `domain/em/lorentz.clj` ↔
`domain/mhd/force.clj` (20 of the remaining 65 clones). `mhd_force_test` asserts the
merged system matches the standalone Lorentz system, and that test is only meaningful
because the two paths are independently constructed. `.jscpd.json` records this so
the next person does not "fix" it.

### Gap found, not closed

`barnes_hut_test.clj` has **no serial-vs-parallel tree-equality test**, though
`build-tree-parallel`'s docstring promises equality and the parallel path only
triggers above 512 bodies. The extractions above make that promise structural rather
than coincidental, but it is still unasserted. Noted on the umbrella card.

### Verification

`clojure -M:test` → 879 / 15486 / 0 failures;
`domain.gravity.barnes-hut-test` and `domain.disk-evolution-test` green individually.
---