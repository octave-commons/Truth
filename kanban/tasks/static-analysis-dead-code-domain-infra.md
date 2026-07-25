---
category: "specs"
labels: ["specs", "static-analysis", "epic-static-analysis-cleanup", "cleanup"]
write-id: "1784985308707-0.ij4dux6fgvep7e1lp4r"
source: "kanban/tasks/static-analysis-dead-code-domain-infra.md"
title: "Dead code cleanup: domain.* and infra.* + final verification"
priority: "P2"
status: "done"
estimate: "5"
uuid: "static-analysis-dead-code-domain-infra"
created_at: "2026-07-07T00:00:00Z"
---

# Dead code cleanup: domain.* and infra.* + final verification

Parent: `kanban/tasks/static-analysis-dead-code-cleanup.md` (dead-code epic)

Scope: Phases 5–6 of the dead-code cleanup spec.

- Triage the remaining 44 `domain.*` and 13 `infra.*` findings.
- Decide per-function: delete or mark `^:api`. **Correction 2026-07-24: "privatize" is not a real option here — zero vars qualify.** clojure-lsp's `unused-public-var` only fires when there are no *cross-namespace* references, so a var used inside its own namespace is still flagged; same-file usage is the discriminator, not evidence the var should be `defn-`. The two non-facade candidates in this scope are false matches: `domain/mass_transfer.clj:395` `systems` (hits at `:2,4` are the ns docstring) and `domain/ecs/rewindable.clj:18` `snapshot` (a `defprotocol` method, which can be neither privatized nor deleted).
- Note also that the count in this card predates the discovery that **199 of 353 findings are facade re-export aliases**, handled separately by `static-analysis-facade-prune.md`.
- Check runtime/dev bindings for `infra.dev.window` and `infra.menu` functions.
- Keep future-phase chemistry/stellar/em helpers as `^:api` if appropriate.
- Run final verification so only documented `^:api` surface remains.
- Update `docs/STATIC-ANALYSIS.md` with the suppression conventions.
- Optional: add a CI step or `bin/analyze` gate.

Done when:
- `clojure-lsp diagnostics | grep unused-public-var | grep -E 'src/domain|src/infra'` returns nothing or only documented `^:api` surface.
- `clojure -M:test` is green.
- `test/architecture_test.clj` still passes.
- `bin/bench` shows no regression if hot-path namespaces were touched.
- `docs/STATIC-ANALYSIS.md` is updated.

---
Triage 2026-07-10: scoped 5pt, clear exit criteria. Ready for implementation.
---

---
## Correction (2026-07-25)

**The "make internal helpers `^:private`" line item has zero qualifying vars — drop
it**, for the reason recorded on
`kanban/tasks/static-analysis-dead-code-law-schemas.md`: clojure-lsp flags a var used
only inside its own namespace, so same-file usage is the discriminator and every
candidate here is a false match.

Vars in this card's scope were triaged into delete / `^:export` / `UNUSED-PENDING` by
`kanban/tasks/static-analysis-lsp-config-dead-vars.md`, which drove the count to 0.
---