(Π-state
  (repo    octave-commons/Truth)
  (branch  main)
  (status  :dirty)
  (tests   "clj -M:test => 94 tests, 249 assertions, 0 failures, 0 errors")
  (manifest
    [.gitignore
     .ημ/Π_STATE.sexp
     .ημ/Π_LAST.md
     AGENTS.md
     README.md
     docs/designs/phase0-coupled-physics-and-regime-classifier.md
     docs/notes/2026.06.25.22.11.59.md
     docs/notes/2026.06.25.22.13.14.md
     src/domain/ecs/components.clj
     src/domain/ecs/parallel.clj
     src/domain/em.clj
     src/domain/gravity/barnes_hut.clj
     src/domain/orbital/system.clj
     src/domain/phase0.clj
     src/domain/physics/collision.clj
     src/domain/player.clj
     src/domain/regime.clj
     src/domain/stellar.clj
     src/infra/dev/server.clj
     src/infra/dev/window.clj
     src/infra/render.clj
     src/law/field.clj
     src/law/stellar.clj
     test/architecture_test.clj
     test/domain/em_test.clj
     test/domain/phase0_test.clj
     test/domain/physics/collision_test.clj
     test/domain/regime_test.clj
     test/infra/render_test.clj])
  (deleted
    [src/domain/particles/fft.clj
     src/domain/particles/field.clj
     src/domain/particles/phase0.clj
     src/domain/particles/pm.clj
     src/infra/render/phase0_renderer.clj
     test/domain/particles/fft_test.clj
     test/domain/particles/field_test.clj
     test/domain/particles/phase0_test.clj
     test/domain/particles/pm_test.clj])
  (blockers [])
  (residual [.agents/]))
