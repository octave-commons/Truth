---
category: "specs"
labels: ["specs", "static-analysis", "dead-code", "tooling"]
write-id: "1784985257969-0.cat5wf0cuxjbmn7yja"
source: "kanban/tasks/static-analysis-lsp-config-dead-vars.md"
title: "353 unused-public-vars: 48 are false positives with no .lsp config, 12 are genuinely dead, 0 need defn-"
priority: "P2"
status: "done"
estimate: "5"
uuid: "static-analysis-lsp-config-dead-vars"
created_at: "2026-07-24T00:00:00Z"
---

# Dead code: config first, then twelve deletions

> Parent: `kanban/tasks/static-analysis-regression-2026-07-24.md`
> Corrects: `kanban/tasks/static-analysis-dead-code-cleanup.md` §1 (says 157;
> actual 353)

**There is no `.lsp/config.edn`** — only `.lsp/.cache/`. All lint config lives in
`.clj-kondo/config.edn`, which has hooks for the DSL macros but **no
`:linters {:clojure-lsp/unused-public-var {...}}` block at all.** That is the
single highest-leverage gap: **261 of 353 (74%) fall to configuration and one
policy call** rather than per-var review.

Distribution: `src/law` 132, `src/domain` 111, `src/infra` 63, `test/domain` 33,
`bench` 14.

## Buckets

| Bucket | Count | Disposition |
|---|---|---|
| Facade re-export alias | 199 | `static-analysis-facade-prune.md` |
| False positive — DSL macro-generated | 33 | `.lsp/config.edn` |
| False positive — bench dynamic `ns-resolve` | 14 | `.lsp/config.edn` |
| False positive — `defprotocol` method | 1 | `.lsp/config.edn` |
| LAW/SCHEMA vocabulary | 59 | leave, mark as API surface |
| ECS component reserved vocabulary | 14 | leave (card already `done`) |
| **GENUINELY DEAD** | **12** | **delete** |
| Incomplete-not-abandoned | 21 | `static-analysis-unused-pending-convention.md` |
| Should-be-private | **0** | — |

## 1. Create `.lsp/config.edn` — 48 false positives

```clojure
{:linters {:clojure-lsp/unused-public-var
           {:exclude-when-defined-by #{domain.ecs.dsl/defcomponent
                                       domain.ecs.dsl/defevent}
            :exclude-regex #{"gates-of-truth\\.bench\\..*/(run|profile-iterations)"}
            :exclude #{domain.ecs.rewindable/snapshot}}}}
```

- **33 macro-generated.** From `defcomponent`/`defevent` — *not*
  `defsystem`/`defreaction`/`defaggregate`/`defprojection`/`defrewind` (those are
  listed at `dev/smell_report.clj:51` for a different rule, arity exemption, and
  generate no flagged vars). Confirmed by two independent signals: the reported
  column is `1` (form start, not var name) and the generated name never appears
  literally in the file. Split: `ledger_test` 16, `dsl_test` 9, `rewind_test` 8.
- **14 bench.** `bench/gates_of_truth/bench.clj:106,177` resolve `run` and
  `profile-iterations` via `(ns-resolve (the-ns ns-sym) 'run)`. Reached only
  dynamically.
- **1 protocol.** `src/domain/ecs/rewindable.clj:18` — `snapshot` is a
  `defprotocol` method; it can be neither privatized nor deleted.

**Do NOT exclude `test/`.** All 33 test findings are macro output; **zero
hand-written test vars are flagged.** The macro-scoped exclusion is strictly
better — a path exclusion would buy nothing and blind us to future genuine test
dead code.

## 2. Delete — 12 vars, verified zero references in `src`/`test`/`bench`/`dev`, no card, no pending comment

1. `src/law/ledger.clj:100` `entries-for`
2. `src/law/ledger.clj:106` `entries-of-kind`
3. `src/law/ledger.clj:118` `events-since` — superseded by
   `domain.ecs.event/events-since` (`src/domain/ecs/event.clj:96`), which is live
4. `src/law/mass_transfer.clj:39` `earth-mass` — duplicate of `law.stellar/earth-mass`
5. `src/law/mass_transfer.clj:43` `jupiter-mass` — same duplication
6. `src/domain/ecs/ledger.clj:11` `empty-ledger`
7. `src/domain/ecs/ledger.clj:20` `append`
8. `src/domain/ecs/event.clj:91` `dispatch-all`
9. `src/domain/orbital/integrator.clj:27` `step-all` — the ns is used
   (`src/domain/orbital/system.clj:10`) but only `leapfrog-step`; `step-all`
   builds its own Barnes–Hut tree per tick, which the parallel-tick migration
   replaced
10. `src/domain/pacing.clj:101` `bulk-dynamical-time`
11. `src/domain/stellar/wind.clj:17` `speed-of-light` — orphan `^:const`, never
    used in its own file, no `law` counterpart; a stray transcription
12. `src/infra/render/units.clj:28` `valid-context?` — note its sibling
    `render->phys-radius` (`:74`) is **not** dead, see the UNUSED-PENDING card

## 3. Leave and mark — 73 vocabulary/contract vars

`law.sed` 11, `law.system-specs` 8, `law.crater` 8, `law.plasma` 7,
`law.mass-transfer` 7, `law.field` 4, `law.chemistry` 4, `law.field.schema` 3,
`law.narrative` 2, `law.composition` 2, `law.stellar.schema` 2,
`law.narrowing` 1, plus 14 ECS components.

Two worth noting:
- **`law.sed` is wired, not orphaned.** It is required by
  `src/domain/stellar/fusion.clj:7`, `src/domain/atmosphere.clj:11`,
  `src/domain/stellar/temperature.clj:7`, `src/domain/stellar/wind.clj:7`. Only
  these 11 specific vars are unconsumed. Backed by
  `docs/research/INDEX.md:27` → `phase1-radiation-plasma-truth.md §2-3` and
  `docs/research/atmosphere/planetary-atmosphere-retention-classifier.md:591`.
- `src/law/system_specs.clj`'s own ns docstring says outright: "These are NOT
  executable specs — they are documentation contracts."
- `src/law/narrowing.clj:97` `phase-1-unlock-costs` — docstring reads "Data for
  the allocation/respec card; nothing consumes it yet." Self-documenting.
- The 14 ECS component vars are already covered by
  `static-analysis-component-vocabulary-exception.md` (`done`), which
  establishes `domain.ecs.components` as a documented structural exception.
  Verified: both the var *and* the keyword (`:component/civilization` etc.) have
  zero hits outside the defining file, so they are not reached by indirection.

## 4. Correction: zero vars qualify for `defn-`

`clojure-lsp`'s `unused-public-var` only fires when there are **no references
from other namespaces** — a var used inside its own ns is still flagged. So
same-file usage is the discriminator. Of all 353, only 18 have >1 mention in
their own file, and all 6 non-facade candidates were hand-checked and are false
matches:

- `src/law/stellar/schema.clj:96` `orbit-stable?` — second hit is the keyword
  `[:orbit-stable? :boolean]` at `:134`
- `src/law/crater.clj:45` `k1-gravity-water` — docstring prose at `:302`
- `src/law/crater.clj:108` `complex-depth-coeff` — the `UNUSED-PENDING`
  cross-reference at `:116`
- `src/law/crater.clj:313` `collision-regime-schema` — docstring at `:374`
- `src/domain/mass_transfer.clj:395` `systems` — ns docstring at `:2,4`
- `src/domain/ecs/rewindable.clj:18` `snapshot` — protocol method

**Drop the "make internal helpers `^:private`" line item** from
`static-analysis-dead-code-law-schemas.md` and
`static-analysis-dead-code-domain-infra.md`. It has zero qualifying vars.

## Done when

- [ ] `.lsp/config.edn` exists; `grep -E 'dsl_test|rewind_test|ledger_test'` over
      the diagnostics output is empty.
- [ ] The 12 dead vars are deleted (not commented out — epic §2 principle 5).
- [ ] The 73 vocabulary vars are marked as intentional API surface.
- [ ] `clojure-lsp diagnostics | grep -c unused-public-var` → 0, or every
      remainder is documented.
- [ ] `clojure -M:test` still 879 tests / 0 failures.
- [ ] `clojure -M:test -n architecture-test` green.

---
## Outcome (2026-07-25)

**353 → 0.** `clojure-lsp diagnostics | grep unused-public-var` is empty, and the
check is now BLOCKING in `bin/analyze`.

| action | n |
|---|---|
| false positives excluded in `.lsp/config.edn` | 48 |
| facade re-export aliases deleted (`-facade-prune`) | 179 |
| genuinely dead vars deleted | 14 |
| `^:export` — declared API surface | 97 |
| `UNUSED-PENDING` markers | 33 |

### §1 correction — `:exclude-when-defined-by` does not work here

This card specified `:exclude-when-defined-by #{domain.ecs.dsl/defcomponent
domain.ecs.dsl/defevent}` for the 33 macro-generated test vars. **It has no effect.**
`.clj-kondo/hooks/ecs_dsl.clj` rewrites those macros into plain `def`/`defn`, so the
analysis records `:defined-by clojure.core/def`. The three DSL test namespaces are
excluded wholesale instead, with the cost written into `.lsp/config.edn`: a dead
hand-written var added to one of those three files would go unreported. Still far
narrower than excluding all of `test/`, which this card rightly refused.

The other 15 exclusions are as specified and verified: 14 bench vars reached only via
`(ns-resolve (the-ns ns) 'run)` / `'profile-iterations` at `bench.clj:106,178,211`,
and `domain.ecs.rewindable/snapshot` (a `defprotocol` method).

**One exclusion this card did not anticipate:** the 6 `infra.dev.window` vars
(`reload-shaders!`, `take-screenshot!`, `service-state`, `service-info`,
`reload-mesh!`, `reset-camera!`). They have no static caller and never will — a human
at the nREPL prompt on `127.0.0.1:7888` is the caller, and `CLAUDE.md:44-45` documents
them as the way to drive the live window. The alias-prune would have deleted them and
silently broken the documented dev workflow.

### §2 — 12 deleted as specified, plus 2 found during the work

All 12 verified independently (zero references across `src`/`test`/`bench`/`dev`)
before deletion. Two more surfaced:

- `infra.render/material` — a facade alias that survived the automated prune only
  because the in-file-use guard false-positived: the var and the *namespace alias*
  `material` share a name, so `material/draw-material!` counted as a use of the var.
- `infra.render.window/run-window` — self-described "Legacy single-threaded window
  loop", superseded by `infra.dev.window.loop`. Its deletion cascaded into the private
  `render-one-frame!`, the `infra.render.input` require, and the `Callbacks` import.
  `infra.render.window` itself stays: 8 live public fns including the headless
  `render-to-file` PNG path.

### §3 — 73 vocabulary vars → 97 `^:export`

Marked with `^:export` rather than left bare, so the linter stays at zero and every
future finding in those namespaces is real. Count rose from 73 because the facade
prune made previously-masked vars visible.

### §4 — confirmed, and the two child cards corrected

Zero vars qualify for `defn-`. The line item is struck from
`static-analysis-dead-code-law-schemas.md` and
`static-analysis-dead-code-domain-infra.md`.

### Verification

`clojure -M:test` → 879 / 15486 / 0 failures. `architecture-test` green.
All render/dev namespaces load (`infra.dev.window.loop`, `infra.render`,
`infra.render.scene`, `infra.inspect`, `infra.menu`, `infra.camera`,
`infra.render.window`, `infra.dev.window`), and no `resolve`/`requiring-resolve` of a
pruned name exists anywhere in `src`/`dev`/`bench`.
---