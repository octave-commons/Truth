---
write-id: "1784745173438-0.aqlhcoacp5myg00ar9z"
status: "done"
---

Triage 2026-07-22 (Claude): reconciled board vs. body. Card body already declares Status: done. Verified in tree: src/domain/physics/cache/neighbor.clj exists; domain.ecs.registry declares :neighbor-cache as sole writer of c/neighbor-cache; no serial genesis/neighbor-cache rebuild remains in genesis/tick or systems. Full suite green (642 tests / 13463 assertions, 0 failures) at HEAD 8fbd078. Moving incoming -> done.