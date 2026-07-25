---
category: "specs"
labels: ["specs", "static-analysis", "epic-static-analysis-cleanup", "cleanup"]
write-id: "1784985306121-0.nasyf1jis6c3hab02b"
source: "kanban/tasks/static-analysis-dead-code-law-schemas.md"
title: "Dead code cleanup: law.* contracts and schemas"
priority: "P2"
status: "done"
estimate: "5"
uuid: "static-analysis-dead-code-law-schemas"
created_at: "2026-07-07T00:00:00Z"
---

# Dead code cleanup: law.* contracts and schemas

Parent: `kanban/tasks/static-analysis-dead-code-cleanup.md` (dead-code epic)

Scope: Phase 4 of the dead-code cleanup spec.

- Triage the 67 unused public vars across `law.*` namespaces (`law.sed`, `law.ledger`, `law.stellar`, `law.mass-transfer`, `law.composition`, `law.ecology`, `law.field`, `law.system-specs`, `law.plasma`).
- Delete genuinely orphaned helpers, constants, and duplicated contracts.
- Mark intended public schemas and contracts as `^:api` with docstrings.
- ~~Make internal helpers `^:private` where appropriate.~~ **Struck 2026-07-24: zero vars qualify.** clojure-lsp's `unused-public-var` only fires when there are no *cross-namespace* references, so a var used inside its own namespace is still flagged — same-file usage is the discriminator, not a signal that the var should be private. Of all 353 findings only 18 have >1 mention in their own file, and every non-facade candidate here is a false match: `law/stellar/schema.clj:96` `orbit-stable?` (second hit is the keyword `[:orbit-stable? :boolean]` at `:134`), `law/crater.clj:45` `k1-gravity-water` (docstring prose at `:302`), `law/crater.clj:108` `complex-depth-coeff` (its own `UNUSED-PENDING` cross-reference at `:116`), `law/crater.clj:313` `collision-regime-schema` (docstring at `:374`).
- Update namespace docstrings if public API surface changes.

Done when:
- `clojure-lsp diagnostics | grep unused-public-var | grep 'src/law/'` returns nothing or only documented `^:api` surface.
- `clojure -M:test` is green.

---
Triage 2026-07-10: scoped 5pt, clear per-var triage (delete/private/^:api). Ready for implementation.
---

---
## Correction (2026-07-25)

**The "make internal helpers `^:private`" line item has zero qualifying vars — drop
it.** clojure-lsp's `unused-public-var` only fires when there are no *cross-namespace*
references, so a var used inside its own namespace is still flagged; same-file usage is
the discriminator. Every candidate in this card's scope was hand-checked and is a false
match (`law.stellar.schema/orbit-stable?`'s second hit is the keyword
`[:orbit-stable? :boolean]`; `law.crater/k1-gravity-water` and
`complex-depth-coeff`'s are docstring prose and an `UNUSED-PENDING` cross-reference).

The vars in this card's scope are now marked `^:export` — declared `law/` vocabulary
with no consumer yet. Superseded by
`kanban/tasks/static-analysis-lsp-config-dead-vars.md`.
---