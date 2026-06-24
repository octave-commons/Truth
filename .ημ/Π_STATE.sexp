(Π-state
  (repo    octave-commons/Truth)
  (branch  main)
  (status  :clean)
  (tests   "clj -M:test => 55 tests, 120 assertions, 0 failures, 0 errors")
  (manifest
    [deps.edn
     .gitignore
     docs/notes/2026.06.23.20.01.16.md
     src/shape/core.clj
     src/shape/spatial.clj
     src/law/contract.clj
     src/law/ecs_dsl.clj
     src/law/ledger.clj
     src/law/registry.clj
     src/domain/ecs/components.clj
     src/domain/ecs/core.clj
     src/domain/ecs/dsl.clj
     src/domain/ecs/event.clj
     src/domain/ecs/ledger.clj
     src/domain/ecs/rewindable.clj
     src/domain/ecs/timeline.clj
     src/domain/gravity/barnes_hut.clj
     src/domain/orbital/integrator.clj
     src/domain/orbital/kepler.clj
     src/domain/orbital/system.clj
     src/domain/physics/collision.clj
     src/domain/physics/collision_response.clj
     src/domain/world_bootstrap.clj
     test/domain/ecs/core_test.clj
     test/domain/ecs/dsl_test.clj
     test/domain/ecs/event_test.clj
     test/domain/ecs/ledger_test.clj
     test/domain/ecs/rewind_test.clj
     test/domain/gravity/barnes_hut_test.clj
     test/domain/orbital/system_test.clj
     test/domain/physics/collision_test.clj
     test/law/contract_test.clj
     test/law/ledger_test.clj
     test/law/registry_test.clj
     test/shape/core_test.clj
     test/shape/spatial_test.clj])
  (blockers [])
  (residual [receipts.log]))
