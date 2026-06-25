(Π-state
  (repo    octave-commons/Truth)
  (branch  main)
  (status  :clean)
  (tests   "clj -M:test => 55 tests, 120 assertions, 0 failures, 0 errors")
  (manifest
    [AGENTS.md
     deps.edn
     .gitignore
     .clj-kondo/imports/metosin/malli/config.edn
     dev/ecosystem.config.js
     src/infra/main.clj
     src/infra/render.clj
     src/infra/dev/server.clj
     src/infra/dev/window.clj])
  (blockers [])
  (residual [.clj-kondo/.cache/ .lsp/ hs_err_pid*.log receipts.log]))
