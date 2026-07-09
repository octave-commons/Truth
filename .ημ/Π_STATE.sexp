((pi "0.63.1")
 (repo "/home/err/spaces/Truth")
 (branch "main")
 (head "e9ad32a5bb341de7a90ac1cc92e783fc71aae4a2")
 (tag "Π-20260709012039")
 (ts "2026-07-09T01:20:39Z")
 (host "spaces/Truth")
 (origin "fork-tax-tender")
 (dod "pay fork tax on significant changes")
 (manifest [
   "README.md"
   "deps.edn"
   "docs/TESTING.md"
   "kanban/tasks/spec-neighbor-cache-fan-out-lane.md"
   "kanban/tasks/tick-perf-drift-profile.md"
   "receipts.edn"
   "receipts.log"
   "src/domain/ecs/registry.clj"
   "src/domain/em/lorentz.clj"
   "src/domain/genesis/systems.clj"
   "src/domain/genesis/tick.clj"
   "src/domain/hydro/common.clj"
   "src/domain/hydro/density.clj"
   "src/domain/hydro/pressure.clj"
   "src/domain/physics/cache.clj"
   "src/domain/physics/cache/neighbor.clj"
   "src/infra/dev/actor_dashboard.clj"
   "src/law/field/schema.clj"
   "test/domain/em_lorentz_test.clj"
   "test/domain/formation_integration_test.clj"
   "test/domain/hydro_test.clj"
   "test/domain/physics/cache_test.clj"
   "test/test_runner.clj"
 ])
 (owner "fork-tax-tender")
 (note "fork-tax-tender detected significant changes and paid the fork tax: neighbor-cache fan-out migration wired through registry, genesis systems, hydro/EM, and law.field schema; group-aware test runner added with deps.edn aliases, README, and docs/TESTING.md; actor dashboard session sorting fixed; receipts and ledger appended. .ημ/.env is gitignored and contains live API keys; it was not staged.")
 (verification
   (test "clojure -M:test:architecture-test, clojure -M:test:full-test, clojure -M:test")
   (result "617 tests, 15134 assertions, 0 failures/errors"))
 (concurrent nil)
 (blockers nil))
