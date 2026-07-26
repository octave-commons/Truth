---
category: "specs"
labels: ["specs", "static-analysis", "cljfmt", "formatting"]
write-id: "1784985229922-0.7yl6cgn9gbxbrpqekld"
source: "kanban/tasks/static-analysis-cljfmt-2026-07.md"
title: "cljfmt: 24 files — runs LAST, because a third of them are rewritten by earlier waves"
priority: "P3"
status: "done"
estimate: "1"
uuid: "static-analysis-cljfmt-2026-07"
created_at: "2026-07-24T00:00:00Z"
---

# cljfmt pass

> Parent: `kanban/tasks/static-analysis-regression-2026-07-24.md`
> Folds: `static-analysis-cljfmt-cleanup.md` (`ready`) and
> `spec-cljfmt-formatting-cleanup.md` (`todo`) — duplicates of each other.

`clojure -M:cljfmt check src test` fails on **24 files** (7 at the 2026-07-10
triage).

```
src/domain/genesis/bootstrap.clj        src/domain/gravity/dark_matter.clj
src/domain/hydro/density.clj            src/domain/integrator/temperature.clj
src/domain/interior.clj                 src/domain/physics/cache/soa.clj
src/domain/planet_formation/seed.clj    src/domain/spatial/index.clj
src/domain/stellar/classifier.clj       src/domain/stellar/disc_evolution.clj
src/domain/voxel/carve.clj              src/domain/voxel/focus.clj
src/domain/voxel/sculpt.clj             src/infra/dev/actor_dashboard.clj
src/infra/render/window.clj             src/law/field/schema.clj
test/domain/genesis/handoff_test.clj    test/domain/gravity/dark_matter_test.clj
test/domain/orbital/multi_timescale_regression_test.clj
test/domain/stellar_test.clj            test/domain/voxel_carve_test.clj
test/infra/render/material_test.clj     test/infra/render/scene/voxel_test.clj
test/infra/render_test.clj
```

## Sequencing is the whole point of this card

**Run this LAST.** At least 8 of the 24 are files that Waves 1-3 rewrite:
`classifier.clj` (split into three namespaces), `carve.clj` (`derive-edits`
decomposed, 37 `Math/*` sites), `focus.clj`, `interior.clj` (33 `Math/*` sites),
`disc_evolution.clj` (clone extraction), `spatial/index.clj`
(`point-aabb-dist2` moved out), `voxel_carve_test.clj` (15 `Math/*` sites),
`scene/voxel_test.clj` (the `distinct?` bug fix).

Formatting first is pure churn and makes every subsequent diff harder to read.

## Scope

- `bin/analyze --fix` (which runs `clojure -M:cljfmt fix src test`).
- Review the diff before committing. cljfmt is mechanical but this is 24 files;
  a formatting commit that also contains a semantic change is the worst kind of
  commit to bisect through.
- Commit separately from any semantic change.

## Done when

- [ ] `clojure -M:cljfmt check src test` passes.
- [ ] The formatting commit contains formatting only.
- [ ] `clojure -M:test` still 879 tests / 0 failures.
- [ ] cljfmt is added to the `FAIL` set in `bin/analyze`
      (`static-analysis-ratchet-branch-protection.md`) so this cannot drift back.

---
## Outcome (2026-07-25)

`clojure -M:cljfmt fix src test` applied LAST, as this card sequenced it — after
Waves 1–3 had finished rewriting the files, so the formatting pass was not churn.

27 files reformatted (not 24: Waves 2–3 touched more files than the original count
assumed). `clojure -M:cljfmt check src test` → "All source files formatted correctly".
cljfmt is now BLOCKING in `bin/analyze`, and verified to refuse a deliberately
misindented form.

Note for whoever gates a wider scope later: `bin/analyze` checks `src test`, but
`dev` and `bench` are NOT formatted — `clojure -M:cljfmt check src test dev bench`
reports 11 further files, mostly `quick-bench` indentation in `bench/`. Out of scope
here because the gate does not cover them; recorded on the umbrella card.

`clojure -M:test` → 879 / 15486 / 0 failures after formatting.
---