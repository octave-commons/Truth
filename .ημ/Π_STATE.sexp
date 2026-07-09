((pi "0.63.1")
 (repo "/home/err/spaces/Truth")
 (branch "main")
 (head "05a73f5ec5565fee1c53a4b30d71b7f5f6bdceaf")
 (tag "Π-20260709172832")
 (ts "2026-07-09T17:28:32Z")
 (host "spaces/Truth")
 (origin "fork-tax-tender/41d31c64-768a-4291-9d8b-d7dd3f0f10f3")
 (dod "pay fork tax on significant changes")
 (manifest [
   "receipts.edn"
   "src/infra/dev/window/loop.clj"
   "src/infra/inspect/overlay.clj"
   "src/infra/render/scene/setup.clj"
   "src/infra/render/window.clj"
   "test/infra/inspect_test.clj"
   ".ημ/Π_STATE.sexp"
   ".ημ/Π_LAST.md"
   ".ημ/Π_MANIFEST.sexp"
 ])
 (owner "fork-tax-tender")
 (note "fork-tax-tender detected significant working-tree changes in the Gates of Truth repository and paid the fork tax. Scope: camera-relative render-origin support to fix single-precision jitter on close-up bodies far from world origin, adaptive halo segment subdivision for smooth selection rings at high zoom, and associated overlay/dev-window changes; plus inspect-test updates for the adaptive ring. All stageable source changes and receipt meta-state have been committed and tagged.")
 (verification
   (test "clojure -M:test:test-runner -g infra")
   (result "86 tests, 8302 assertions, 0 failures, 0 errors")
   (test "clojure -M:test:test-runner -g architecture")
   (result "6 tests, 23 assertions, 0 failures, 0 errors")
   (test "clj-kondo --lint src test")
   (result "0 errors, 0 warnings"))
 (concurrent nil)
 (blockers nil))
