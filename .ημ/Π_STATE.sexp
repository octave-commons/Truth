((pi "0.63.1")
 (repo "/home/err/spaces/Truth")
 (branch "main")
 (head "47e73f7de4071fce94d31288fa227d8b89f09976")
 (tag "Π-20260708221336")
 (ts "2026-07-08T22:13:36Z")
 (host "spaces/Truth")
 (origin "fork-tax-tender")
 (dod "pay fork tax on significant changes")
 (manifest [
  ".ημ/actors/fork-tax-tender/runtime/systemd-runner.sh"
  ".ημ/Π_LAST.md"
  ".ημ/Π_MANIFEST.sexp"
  ".ημ/Π_STATE.sexp"
  "receipts.edn"
  "src/domain/mass_transfer.clj"
  "src/infra/dev/actor_dashboard.clj"
 ])
 (owner "fork-tax-tender")
 (note "Π snapshot: absorb mass-transfer dt key migration, actor-dashboard session detail view, fork-tax-tender runtime created-at fix, and concurrently appended receipt.")
 (verification
   (test "clj -M:test")
   (result "617 tests, 0 failures, 0 errors"))
 (concurrent nil)
 (blockers nil))
