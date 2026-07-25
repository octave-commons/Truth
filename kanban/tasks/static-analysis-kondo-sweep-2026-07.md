---
category: "specs"
labels: ["specs", "static-analysis", "clj-kondo"]
write-id: "1784985250920-0.jbhjwfprecj6pu18ntv"
source: "kanban/tasks/static-analysis-kondo-sweep-2026-07.md"
title: "clj-kondo regression sweep: 50 warnings back after the six kondo cards closed"
priority: "P1"
status: "done"
estimate: "5"
uuid: "static-analysis-kondo-sweep-2026-07"
created_at: "2026-07-24T00:00:00Z"
---

# clj-kondo: 50 → 0

> Parent: `kanban/tasks/static-analysis-regression-2026-07-24.md`
> Regressed: the six `static-analysis-clj-kondo-*` cards (all `done`)

50 warnings, **0 errors**. `.clj-kondo/config.edn` states the house policy
outright — "promote a `:warning` to `:error` once the codebase is clean for that
rule" — which is the argument for fixing or explicitly excluding, not letting
warnings sit.

## 1. Config exclusion — 12 warnings, zero code churn

`binding` is a first-class domain noun in this codebase: component `c/binding`,
`c/binding-scar`, schema `law.narrowing/binding?`, fns `binding-step`,
`binding-system`, `deepest-binding`, the write-set key `:binding`, and the
design doc "The First Narrowing". `src/domain/narrowing.clj:132`'s docstring
documents the return as `{:binding {eid -> [0,1]} :scars {...}}` — renaming the
local desynchronises the code from the map key it destructures.

And `clojure.core/binding` is a **macro**: a local of that name can never be
accidentally invoked as the core form. The shadow is inert.

`.clj-kondo/config.edn` currently has `:shadowed-var {:level :warning}` with **no
exclusion list**. Add `:exclude [binding]` with the reason inline.

Covers: `src/domain/narrowing.clj:132,167,177,224,259,312,365`,
`src/infra/render/hud.clj:258`,
`test/domain/narrowing_test.clj:59,90,102,187`.

## 2. The one genuinely dangerous shadow

`src/law/atmosphere.clj:91,108` — the local `species-mass` is a **`double` (kg)**
while the same-named var at `:21` is a **map** `{:CO2 … :N2 …}`. Same name,
incompatible types, same namespace; callers pass an *element* of the var
(`classifier.clj:~615` does `(get atmosphere/species-mass %)`).

Rename the params to `m-species`. Pure arithmetic fns, positional callers
unaffected.

## 3. Same-namespace shadows — follow existing house style

`src/domain/stellar/classifier.clj` defines `material-class` (`:340`) and
`thermal-band` (`:389`), then shadows both as params at `:396`, `:549`, `:560`,
`:609`, `:838`, `:846`. The locals genuinely hold what the same-named fns
return, which is *more* confusing than a core shadow — a reader may believe
`(material-class x)` is callable in that scope.

The house alias already exists: `mclass` at `src/domain/interior.clj:136` and in
`classifier.clj` itself. Use `mclass` / `tband`. Note `:609` is a map
destructure keyed on `:material-class` / `:thermal-band`, so it needs
`{mclass :material-class tband :thermal-band :keys [mass radius temperature]}`
rather than a plain rename.

Same pattern: `src/domain/voxel/carve.clj:341,619` — `band-info` shadows the
`defn-` at `:606`; the real call site at `:723` already uses `binf`.

## 4. Mechanical renames (~24 sites)

Highest value: **`src/infra/render/passes.clj:95`**, where the shadow has
*already forced a workaround in live code* —
`(doseq [[name v] uniforms] (set-uniform! program (clojure.core/name name) v))`.
Rename to `uname` and the qualification disappears.

Others: `ref` → `fe-ref` (`interior.clj:139`) / `seed` (`interior.clj:296`,
`sculpt.clj:265`), `name` → `layer-name` (`interior.clj:193`) / `uname`
(`passes.clj:31,80`), `chunk` → `batch` (`voxel/queue.clj:109`), `key` →
`cache-key` (`render/asset.clj:134`), `count` → `n` (`render/material.clj:44`),
`comp` → `cmp` (`differentiation_test.clj:136`, `formation_test.clj:301`),
`field` → `fld` (`voxel_sculpt_test.clj:119` — a real hazard: dropping the param
would silently fall through to the ns-level fixture and the test would pass
vacuously). Plus `[[k v]]` → `[[_ v]]` at `band.clj:89`, `carve.clj:322`,
`sculpt.clj:362`; `get-in` single-key at `scene/voxel.clj:75` and
`pilot_resolve_seam_test.clj:106`; redundant fn wrapper at
`narrowing_test.clj:166`; `_obs-eid` at `pilot_resolve_seam_test.clj:200`.

## 5. Deletions (3)

- `src/infra/camera/navigation/tether.clj:52` — `shape.spatial` require.
  Confirmed truly unused: line 52 is the file's only mention, no qualified use,
  no reader conditionals. Orphaned when `blend-toward-binding` was removed
  (see the file's own GAPS docstring at `:33-40`).
- `src/domain/physics/cache/neighbor.clj:144-154` — private `attach-r2`.
  Superseded by `attach-pair-terms` (`:156`), a strict superset whose docstring
  at `:158` cross-references it — **reword that docstring** or it dangles.
- `test/domain/voxel_focus_test.clj:81-86` — private `run-ticks`, zero callers;
  siblings `run-tick` and `run-until-drained` are the ones tests actually use.

## 6. Do NOT blindly underscore

`src/domain/interior.clj:463` — unused binding `kind`. The caller at `:507`
*computes* it (`:hotspot`/`:polar-ice`/`:downwelling`/`:background`) and
`resource-cell` drops it because `law.voxel/resource-cell-schema`
(`src/law/voxel.clj:121-131`) has no `:kind` key.
`src/domain/voxel/band.clj:80-86` documents the downstream consequence: "The
cell records carry no `:kind` (slice 1 schema), so the override is derived from
their element content."

This is `CLAUDE.md`'s "coded but never ticked" case. Mark it `UNUSED-PENDING`
with a pointer to the schema gap (see
`static-analysis-unused-pending-convention.md`). Adding `:kind` to the schema
and retiring the element-content inference is a design change, not a lint fix.

## Done when

- [ ] `clj-kondo --lint src test` → 0 warnings, 0 errors.
- [ ] Every exclusion in `.clj-kondo/config.edn` carries an inline reason.
- [ ] `clojure -M:test` still 879 tests / 0 failures — in particular after the
      three numeric-hot-path sites: `classifier.clj:609` destructure rewrite,
      the `neighbor.clj` deletion, and `carve.clj` `band-info` → `binf`.

---
## Outcome (2026-07-25)

**clj-kondo 50 → 0/0**, and warnings are now BLOCKING in `bin/analyze` (the config's
own stated policy: promote a `:warning` once the tree is clean for that rule).

- §1 config exclusion: `:shadowed-var {:exclude [binding]}` with the reason inline —
  12 warnings, zero code churn.
- §2 the dangerous shadow: `law.atmosphere`'s `species-mass` params → `m-species`.
  A `double` (kg) shadowing a *map* of the same name in the same namespace.
- §3 same-namespace shadows: `material-class`/`thermal-band` → `mclass`/`tband`. Now
  in `domain.stellar.classifier.planet` (Wave 1 split the file after this card was
  written). The map-destructure site became
  `{mclass :material-class tband :thermal-band :keys [mass radius temperature]}` as
  specified. `carve.clj`'s `band-info` → `binf`, matching the live call site.
- §4 ~24 mechanical renames, including `passes.clj`'s, where the shadow had already
  forced `(clojure.core/name name)` in live code — now `(name uname)`.
- §5 three deletions: the `shape.spatial` require in `tether.clj`; private `attach-r2`
  in `neighbor.clj` (its dangling docstring cross-reference reworded); the dead test
  helper `run-ticks`.
- §6 `interior.clj`'s `kind` marked `UNUSED-PENDING`, NOT underscored, with the
  `law.voxel/resource-cell-schema` gap and `band.clj`'s element-content inference
  documented at the site.

### Two extra warnings this card predates

Wave 1's classifier split left two unused requires —
`domain.stellar.classifier.candidate` in `genesis/tick.clj` and `domain.chemistry` in
`classifier/candidate.clj`. Both were referenced only from comments/docstrings. Requires
dropped; the prose references rewritten to fully-qualified names so they still resolve
for a reader.

### One `:redundant-ignore` suppression

Set to `:off` in `.clj-kondo/config.edn`, with the reason inline: clj-kondo does not
implement `:clojure-lsp/unused-public-var`, so it cannot tell whether one of this
tree's `#_{:clj-kondo/ignore [:clojure-lsp/unused-public-var]}` markers did anything
and false-reports "Redundant ignore" on some of them. Costs nothing — no ignore form
in this tree targets a clj-kondo linter.

### Verification

`clj-kondo --lint src test` → 0 errors, 0 warnings.
`clojure -M:test` → 879 / 15486 / 0 failures, including after the three numeric
hot-path sites this card flagged.

**One regression was caught and fixed by the suite**, exactly where this card warned
it would be: the `comp` → `cmp` rename in `differentiation_test.clj` missed one site,
which then resolved to `clojure.core/comp` and made
`(chem/volatile-budget comp 1.1e24)` compare `0.0 < 0.0`. Every renamed scope was
re-audited for leftover bare symbols afterward.
---