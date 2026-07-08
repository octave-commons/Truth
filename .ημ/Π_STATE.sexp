((pi "0.63.1")
 (repo "/home/err/spaces/Truth")
 (branch "main")
 (head "c1a3a68491024d8ff0a5692aa65d48343c4f88d5")
 (tag "Π-20260708231149")
 (ts "2026-07-08T23:11:49Z")
 (host "spaces/Truth")
 (origin "fork-tax-tender")
 (dod "pay fork tax on significant changes")
 (manifest [
  ".ημ/actors/truth-research-atmosphere/AGENT.md"
  ".ημ/actors/truth-research-atmosphere/actor.edn"
  ".ημ/actors/truth-research-atmosphere/runtime/systemd.timer"
  ".ημ/actors/truth-research-biology/AGENT.md"
  ".ημ/actors/truth-research-biology/actor.edn"
  ".ημ/actors/truth-research-biology/runtime/systemd.timer"
  ".ημ/actors/truth-research-coordinator/AGENT.md"
  ".ημ/actors/truth-research-coordinator/actor.edn"
  ".ημ/actors/truth-research-coordinator/runtime/systemd.timer"
  ".ημ/actors/truth-research-cosmology/AGENT.md"
  ".ημ/actors/truth-research-cosmology/actor.edn"
  ".ημ/actors/truth-research-cosmology/runtime/systemd.timer"
  ".ημ/actors/truth-research-culture/AGENT.md"
  ".ημ/actors/truth-research-culture/actor.edn"
  ".ημ/actors/truth-research-culture/runtime/systemd.timer"
  ".ημ/actors/truth-research-geology/AGENT.md"
  ".ημ/actors/truth-research-geology/actor.edn"
  ".ημ/actors/truth-research-geology/runtime/systemd.timer"
  ".ημ/actors/truth-research-physics/AGENT.md"
  ".ημ/actors/truth-research-physics/actor.edn"
  ".ημ/actors/truth-research-physics/runtime/systemd.timer"
  ".ημ/session-mycology/ledger.md"
  ".ημ/session-mycology/spores/20260708-172700-physics-dt-unit-mismatch.md"
  "README.md"
  "receipts.edn"
  ".ημ/Π_LAST.md"
  ".ημ/Π_MANIFEST.sexp"
  ".ημ/Π_STATE.sexp"
 ])
 (owner "fork-tax-tender")
 (note "Π snapshot: research actors interval 48h→24h and OnCalendar 10:00 daily, README update, receipts/ledger append, new physics-dt-unit-mismatch spore.")
 (verification
   (test "edn + markdown sanity")
   (result "actor.edn files valid EDN; no Clojure code changed since prior passing test run"))
 (concurrent nil)
 (blockers nil))
