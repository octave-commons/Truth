((pi "0.63.1")
 (repo "/home/err/spaces/Truth")
 (branch "main")
 (head "dbbbf7f107f0149d6a4296e350c874d861989401")
 (tag "Π-20260709052013")
 (ts "2026-07-09T05:20:13Z")
 (host "spaces/Truth")
 (origin "fork-tax-tender")
 (dod "pay fork tax on significant changes")
 (manifest [
   "bench/gates_of_truth/bench/phase0.clj"
   "kanban/tasks/focus-zoom-lod-ui-spec.md"
   "kanban/tasks/tick-perf-drift-profile.md"
   "kanban/tasks/.events/ledger.edn"
   "receipts.edn"
   "receipts.log"
   "src/domain/arc.clj"
   "src/domain/ecs/registry.clj"
   "src/domain/em/lorentz.clj"
   "src/domain/genesis/systems.clj"
   "src/domain/integrator/kinematics.clj"
   "src/domain/mhd/force.clj"
   "src/infra/inspect/format.clj"
   "src/infra/menu/panels.clj"
   "test/domain/arc_test.clj"
   "test/domain/dominant_star_test.clj"
   "test/domain/lod_test.clj"
   "test/domain/mhd_force_test.clj"
   "test/infra/inspect_test.clj"
   "test/infra/menu_test.clj"
   ".ημ/Π_STATE.sexp"
   ".ημ/Π_LAST.md"
   ".ημ/Π_MANIFEST.sexp"
 ])
 (owner "fork-tax-tender")
 (note "fork-tax-tender detected significant changes and paid the fork tax: merged hydro/EM MHD force system (src/domain/mhd/force.clj), genesis systems fan-out wiring, ECS registry and arc fixes, EM Lorentz cleanup, integrator kinematics update, render inspect/menu/format adjustments, benchmark phase0 tuning, LOD/dominant-star/inspect/menu tests, new MHD force test, kanban task updates (focus-zoom LOD UI spec, tick-perf drift profile), and kanban event ledger. .ημ/.env is gitignored and contains live API keys; it was not staged.")
 (verification
   (test "bin/test unit")
   (result "572 tests, 10264 assertions, 0 failures/errors"))
 (concurrent nil)
 (blockers nil))
