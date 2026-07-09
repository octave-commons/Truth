((pi "0.63.1")
 (repo "/home/err/spaces/Truth")
 (branch "main")
 (head "732204d708690b5b102c6b3d7910611cf708d8ba")
 (tag "Π-20260709041532")
 (ts "2026-07-09T04:15:32Z")
 (host "spaces/Truth")
 (origin "fork-tax-tender")
 (dod "pay fork tax on significant changes")
 (manifest [
   "AGENTS.md"
   "README.md"
   "CLAUDE.md"
   "CONTRACT.edn"
   "receipts.edn"
   ".github/workflows/test.yml"
   "bin/test"
   "docs/TESTING.md"
   "docs/research/INDEX.md"
   "docs/agile/"
   "docs/research/physics/phase0-neighbor-cache-curl-optimization.md"
   "docs/research/physics/phase0_neighbor_cache_curl_toy.py"
   "docs/research/physics/phase0_neighbor_cache_curl_toy.svg"
   "docs/research/physics/phase0_neighbor_cache_curl_toy_summary.txt"
   "kanban/tasks/tick-perf-drift-profile.md"
   "kanban/tasks/focus-zoom-lod-ui-spec.md"
   "src/domain/arc.clj"
   "src/domain/ecs/components.clj"
   "src/domain/ecs/registry.clj"
   "src/domain/em/lorentz.clj"
   "src/domain/hydro/common.clj"
   "src/domain/hydro/pressure.clj"
   "src/domain/integrator.clj"
   "src/domain/integrator/base.clj"
   "src/domain/integrator/core.clj"
   "src/domain/integrator/kinematics.clj"
   "src/domain/integrator/temperature.clj"
   "src/domain/lod.clj"
   "src/domain/physics/cache/neighbor.clj"
   "src/domain/player/system.clj"
   "src/infra/dev/window/loop.clj"
   "src/infra/inspect/format.clj"
   "src/infra/menu/panels.clj"
   "src/infra/render.clj"
   "src/infra/render/hud.clj"
   "src/infra/render/input.clj"
   "src/infra/render/mesh.clj"
   "src/infra/render/scene/setup.clj"
   "src/law/field.clj"
   "src/law/field/schema.clj"
   "test/domain/physics/cache_test.clj"
   "test/domain/player_test.clj"
   ".ημ/Π_STATE.sexp"
   ".ημ/Π_LAST.md"
   ".ημ/Π_MANIFEST.sexp"
 ])
 (owner "fork-tax-tender")
 (note "fork-tax-tender detected significant changes and paid the fork tax: ECS integrator refactor (base/core/kinematics/temperature), EM Lorentz cleanup, hydro common/pressure adjustments, neighbor-cache refinements, LOD and player system updates, render HUD/input/mesh/scene fixes, law/field schema additions, README and TESTING docs updated, CI test workflow and bin/test added, research notebook and kanban tasks for neighbor-cache curl optimization and focus-zoom LOD UI spec. .ημ/.env is gitignored and contains live API keys; it was not staged.")
 (verification
   (test "bin/test unit")
   (result "562 tests, 10215 assertions, 0 failures/errors (infra.dev.window-test intentionally throws an exception that is caught and reported as a non-failure)"))
 (concurrent nil)
 (blockers nil))
