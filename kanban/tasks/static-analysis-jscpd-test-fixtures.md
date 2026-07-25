---
uuid: "static-analysis-jscpd-test-fixtures"
title: "59 of 74 clones are in test/ because there is no shared fixture namespace at all"
status: "ready"
priority: "P3"
labels: ["specs", "static-analysis", "jscpd", "testing"]
created_at: "2026-07-24T00:00:00Z"
source: "kanban/tasks/static-analysis-jscpd-test-fixtures.md"
category: "specs"
estimate: 5
---

# One fixture namespace removes ~two-thirds of the test duplication

> Parent: `kanban/tasks/static-analysis-regression-2026-07-24.md`

**80% of the duplication is in `test/`, and the cause is structural:** there is
no shared fixture namespace. `test/` contains only `architecture_test.clj`,
`benchmark_coverage_test.clj`, `test_runner.clj`, and the
`domain/ infra/ law/ shape/` trees. No `test/support`, no `test/helpers`, no
fixtures ns. Every test file rebuilds its worlds from scratch.

The de-facto helpers already exist — they were just copy-pasted instead of
shared. `test/domain/focus_conservation_test.clj:39-58` defines
`world-with-observer`, `run-system` and `apply-write-set`, and the 29-line clone
`focus_conservation_test.clj:28-56` ↔ `focus_zone_test.clj:17-45` is those three
functions duplicated verbatim into the sibling file. That is the template.

## Test clone families

| family | clones |
|---|---|
| `hydro_test` ↔ `mhd_force_test` + hydro self-clones | 11 |
| `em_lorentz_test` ↔ `mhd_force_test` + self | 9 |
| `condensation_seeder_test` (all self, all off one block) | 6 |
| `voxel_*_test` (carve/load/sculpt/focus cross) | 7 |
| `stellar_wind_test` + `stellar_test` | 6 |
| `focus_conservation_test` ↔ `focus_zone_test` (+ `field_cell_test`) | 4 |
| scattered one-offs (orbital, integrator, gravity, genesis, camera, inspect/menu, mass_transfer, lod, formation, commitment/narrowing, cache/soa_cache, collision) | 16 |

## Proposed `test/support/worlds.clj` (~150 lines)

| helper | removes |
|---|---|
| `two-clump-world` — the `spawn-clump` pair at `[0,0,0]` and `[1e14,0,0]`, mass 1e28, radius 2e14, `:nebula`, with `:b-field`/`:density`/`:pressure`/`:angular-momentum` as opts | the em_lorentz ↔ mhd_force family (9) and hydro ↔ mhd_force (4). Compare `test/domain/em_lorentz_test.clj:55-74` against `test/domain/mhd_force_test.clj:55-73` — identical except indentation and one binding name |
| `nebula-pair-world` — the temperature-only variant | the 8 hydro self-clones, all pointing at `test/domain/hydro_test.clj:265-284` |
| `condensation-world` — the `ecs/put-components` + `:genesis/gas-particle-mass` block at `test/domain/condensation_seeder_test.clj:41-53` | all 6 condensation_seeder self-clones |
| `world-with-observer` / `run-system` / `apply-write-set` — promoted verbatim from `focus_conservation_test.clj:39-58` | the 3 focus clones |

That is ~26 of 59. Adding a voxel-band fixture (7) and a stellar-wind fixture
(6) reaches **~39, i.e. ~66% of the test clones from one namespace**.

The remaining ~20 are genuinely scattered one-offs and are not worth chasing.

## Preconditions — check before writing

1. **`test/architecture_test.clj`** may enforce namespace-naming or
   dependency-direction rules over `test/`. Read it first.
2. **`test/test_runner.clj`** discovers suites by pattern, and the grouped
   runners in `deps.edn` (`:domain-test -g domain`, etc.) go through it. Confirm
   a non-`_test` namespace under `test/` is not picked up as a suite.
   `test_runner.clj` already lives there, so this is probably fine — verify,
   don't assume.

## Also: make jscpd capable of failing

`.jscpd.json` currently has **no `threshold` and no `ignore`**, so the tool is
purely advisory by construction. Once under the new number:

- Add `"threshold": <current>` so duplication can only go down.
- Add an `ignore` entry for the docstring clone
  (`src/infra/dev/server.clj:19-30` ↔ `src/infra/dev/window.clj:17-28` is the
  camera keybinding legend inside both ns docstrings, not code). Better still,
  have `server.clj` cross-reference `infra.dev.window` — the two have **already
  drifted**: `server.clj` lacks the `reload-mesh!` line.

## Done when

- [ ] `test/support/worlds.clj` exists and the four helper families use it.
- [ ] `npx jscpd` reports ≤ ~20 clones, ≤ ~0.5% duplicated lines.
- [ ] `.jscpd.json` carries a `threshold` and the docstring `ignore`.
- [ ] `clojure -M:test` still 879 tests / 0 failures, and the grouped runners
      (`clojure -M:test -g domain`) still discover the same suites.
