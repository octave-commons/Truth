---
uuid: "static-analysis-dead-code-cleanup"
title: "Dead Code Audit: clojure-lsp unused-public-var cleanup"
status: "breakdown"
priority: "P2"
estimate: 15
labels: ["specs", "static-analysis", "epic-static-analysis-cleanup", "cleanup"]
created_at: "2026-07-07T00:00:00Z"
source: "kanban/tasks/static-analysis-dead-code-cleanup.md"
category: "specs"
---

# Dead Code Audit: `clojure-lsp` unused-public-var cleanup

**Parent epic:** `kanban/tasks/epic-static-analysis-cleanup.md`  
**Status:** accepted  
**Scope:** Triage every `clojure-lsp/unused-public-var` finding in `src/` and `test/` and converge the tree so that only intentionally-public API surface remains. This is a child of the static-analysis epic and focuses exclusively on dead-code diagnostics.

## 1. Current state

A recent run of `bin/analyze` (`clojure-lsp diagnostics`) reports **157** unused public vars across **30** namespaces. They are grouped below by quadrant (law, domain, infra, test) and by namespace. The counts are the starting point; the triage sections explain what should happen to each group.

| Category | Namespace | Count | Notes |
|----------|-----------|-------|-------|
| law | `law.sed` | 11 | SED bands, solar constants, and unused Malli contracts |
| law | `law.ledger` | 3 | Read helpers: `entries-for`, `entries-of-kind`, `events-since` |
| law | `law.stellar` | 8 | Schema definitions and `planet?` predicate |
| law | `law.mass-transfer` | 14 | Constants, helpers, and validators |
| law | `law.composition` | 3 | `gas-giants`, `eta-10`, `omega-b-h2` |
| law | `law.ecology` | 4 | Phases map, scalar predicate, contracts |
| law | `law.field` | 9 | Thresholds and schema/contract definitions |
| law | `law.system-specs` | 8 | System-level Malli specs for Phase 1+ |
| law | `law.plasma` | 7 | Constants and event/payload contracts |
| domain | `domain.orbital.integrator` | 1 | `step-all` |
| domain | `domain.stellar` | 4 | `luminosity-from-fusion`, `radiation-equilibrium-temperature`, `disk-formation-threshold`, `disk-sound-speed` |
| domain | `domain.pacing` | 1 | `bulk-dynamical-time` |
| domain | `domain.ecs.ledger` | 2 | `empty-ledger`, `append` |
| domain | `domain.planet-formation` | 2 | `proto-solar-metal-frac`, `sound-speed` |
| domain | `domain.ecology` | 4 | `prokaryotic?`, `eukaryotic?`, `multicellular?`, `complex?` |
| domain | `domain.ecs.components` | 13 | Unused component keywords (see §4) |
| domain | `domain.chemistry` | 10 | Composition helpers and predicates |
| domain | `domain.mass-transfer` | 1 | `systems` |
| domain | `domain.ecs.rewindable` | 1 | `snapshot` |
| domain | `domain.ecs.core` | 1 | `has-component?` |
| domain | `domain.player` | 1 | `approach-focus` |
| domain | `domain.ecs.event` | 1 | `dispatch-all` |
| domain | `domain.em` | 2 | `magnetic-torque`, `external-field-at` |
| infra | `infra.menu` | 2 | `escape-action`, `confirm-close-hud` |
| infra | `infra.render` | 3 | `clear-phase0-render-cache!`, `render-bodies`, `run-window` |
| infra | `infra.render.shader` | 1 | `invalidate-program!` |
| infra | `infra.dev.window` | 5 | `reload-shaders!`, `reload-mesh!`, `reset-camera!`, `take-screenshot!`, `service-info` |
| infra | `infra.render.units` | 2 | `valid-context?`, `render->phys-radius` |
| test | `test/domain/ecs/rewind_test` | 8 | DSL-generated event/component vars |
| test | `test/domain/ecs/dsl_test` | 9 | DSL-generated component/event vars |
| test | `test/domain/ecs/ledger_test` | 16 | DSL-generated event/component vars |
| **Total** | | **157** | |

## 2. Triage strategy

Every finding must fall into one of four buckets. No var may be left silently flagged.

### 2.1 Delete genuinely unused code

If a public var is:

- not referenced anywhere in `src/` or `test/` (verified with `grep` / `rg` across the whole tree), **and**
- not part of a public API the project intends to expose, **and**
- not produced by a DSL macro that is expected to generate public vars,

then it is dead code and should be deleted. Examples include:

- Internal helpers that were extracted or inlined elsewhere and never privatized.
- Stubs from earlier phases that were never wired into the tick pipeline.
- Duplicate constants that moved to another namespace.

**Verification rule:** before deleting any var, run `rg -F <var-name> src test` from the repo root and confirm zero references outside the definition. If the definition is in a `law` namespace, also search `src/domain` for qualified usages (`law.foo/bar`) and `test` for aliased usages.

### 2.2 Make internal helpers `^:private`

If a var is used only inside its own namespace, it should be made private by adding `^:private` metadata to the `def`/`defn`/`defrecord`.

- For `def`: `(def ^:private foo ...)`.
- For `defn`: `(defn ^:private foo ...)` or `(defn- foo ...)`.
- For `defrecord`: `(defrecord ^:private Foo ...)`.

After marking, `clojure-lsp` will no longer report it as an unused public var. This is the preferred fix for implementation helpers that are not intended for cross-namespace use.

### 2.3 Keep public API surface and mark it intentionally public

If a var is genuinely unused in the current codebase but is part of the intended public API (e.g., render entry points, dev REPL commands, future-phase contracts), it should be kept and explicitly marked so the warning is understood as a deliberate surface-area decision.

Conventions for this project:

- Add `^:api` metadata to the var: `(def ^:api foo ...)` or `(defn ^:api foo ...)`.
- Add a docstring that states the var is public API, why it is public, and what consumers are expected.
- Where appropriate, add a `clj-kondo` suppression with a comment explaining the intentional surface.

Examples likely to be API surface:

- `infra.render/run-window` — top-level dev window launcher.
- `infra.dev.window/reload-shaders!`, `reload-mesh!`, `reset-camera!`, `take-screenshot!`, `service-info` — REPL/dev tools.
- `domain.ecs.components/*` — many component keywords are future-facing vocabulary even if not yet queried.
- `law.system-specs/*` — Malli specs that describe future systems and may not be referenced yet.

### 2.4 Keep DSL-generated test vars and document why they are flagged

The `defcomponent` and `defevent` macros in `domain.ecs.dsl` generate several vars per form: the keyword var, a `*-schema` var, a `*-validator` var, a `*?` predicate, and an `emit-*` constructor for events. The test files `test/domain/ecs/dsl_test.clj`, `test/domain/ecs/rewind_test.clj`, and `test/domain/ecs/ledger_test.clj` are full of these generated vars.

clojure-lsp reports many of them as unused because they are only referenced by the macro-generated keyword or the tests exercise only the constructor, not the schema/validator/predicate. They are not dead code; they are the macro's intended expansion surface.

Resolution:

- Do **not** delete them.
- Do **not** mark them `^:private`, because the DSL macro always emits public vars and tests rely on them.
- Add a namespace-level comment explaining the DSL generation.
- Optionally add a `clojure-lsp/unused-public-var` clj-kondo config entry for the affected test namespaces, or per-var `^:clj-kondo/ignore` metadata, with a comment explaining that these are macro-generated test fixtures.

## 3. Specific candidates for removal

This section highlights high-confidence removal targets. The list is not exhaustive; every finding in §1 must still be triaged individually.

### 3.1 `domain.ecs.components` — unused component keywords

The following 13 component keywords are currently defined but never referenced anywhere in the codebase (verified by `rg`):

- `elements`
- `orbit-ref`
- `force-accum`
- `event-source`
- `sink-identity`
- `biome-cell`
- `civilization`
- `territory`
- `renderable`
- `cell-id`
- `facet-vector`
- `favor`
- `scribe`

**Decision:** Keep them but mark them as intentional future-phase vocabulary. They are not dead code; they are reserved vocabulary in the single ECS substrate. Use `^:api` metadata and a docstring on each, or group them under a comment block that explains they are reserved for Phase 1+ and the warning is expected. Deleting them would create churn when the later phases arrive and would break the canonical component vocabulary.

Exception: if any of these is a duplicate of an already-existing component keyword (e.g., `event-source` vs. the `:event-source` field elsewhere), delete the duplicate.

### 3.2 `law.*` contracts and schemas

Many `law.*` contracts are currently unused because the systems that should consume them are not yet calling `malli.core/validator` or `malli.core/explain`. Examples:

- `law.system-specs/*` — all 8 system specs.
- `law.plasma/wind-profile-contract`, `plasma-wind-contract`, `atmosphere-escape-contract`, `event-source-contract`.
- `law.sed/sed-band-contract`, `sed-profile-contract`, `atmosphere-shell-contract`, `atmosphere-profile-contract`.
- `law.stellar/nebula-cloud-contract`, `stellar-system-contract`.
- `law.ecology/ecology-contract`, `ecology-extended-contract`.

**Decision:** Most of these are intended public API. Mark them with `^:api` and a docstring explaining they are schemas for future-phase validation. Only delete a contract if it has been superseded by another schema in the same namespace or is clearly a copy-paste leftover.

### 3.3 `infra.render` and `infra.dev.window` — dev / render entry points

The following are likely intended public API and should be kept with `^:api`:

- `infra.render/clear-phase0-render-cache!`
- `infra.render/render-bodies`
- `infra.render/run-window`
- `infra.render.shader/invalidate-program!`
- `infra.dev.window/reload-shaders!`
- `infra.dev.window/reload-mesh!`
- `infra.dev.window/reset-camera!`
- `infra.dev.window/take-screenshot!`
- `infra.dev.window/service-info`

`infra.render.units/render->phys-radius` and `infra.render.units/valid-context?` may be internal helpers that happen to be public. If they are only used inside `infra.render.units`, mark them `^:private`.

### 3.4 `domain.chemistry` — composition helpers

The following functions are flagged as unused. Several (`solar-composition`, `primordial-composition`, `enrich-composition`, `material-phase`, `can-form-molecules?`, `bulk-composition-category`, `potential-atmosphere`, `differentiate-composition`, `fusion-products`, `supernova-enrichment`) look like they belong to the chemistry layer that feeds the Phase 0/1 simulation.

**Decision:** Verify whether each is referenced by the current tick pipeline or only by tests. If a function is only used by tests and not the production pipeline, either:

- keep it as public API (if it is a chemistry primitive), or
- delete it (if it is a leftover from a superseded design).

Use `rg` and `clojure-lsp find-references` to confirm before acting.

### 3.5 `domain.mass-transfer` and `law.mass-transfer` — mass-transfer plumbing

`domain.mass-transfer/systems` and several `law.mass-transfer` helpers (`momentum-of-mass`, `add-momentum`, `scale-momentum`, validators) are flagged.

**Decision:** The gradual-mass-transfer work is active. Do not delete any mass-transfer function without checking `kanban/tasks/gradual-mass-transfer-spec.md` and the callers in `domain.phase0` / `domain.stellar`. If a function is genuinely orphaned, delete it; otherwise mark it `^:api` or make it private.

### 3.6 `domain.ecs.ledger` and `law.ledger` — ledger helpers

`domain.ecs.ledger/empty-ledger` and `append` may appear unused because `domain.ecs.event` initializes the ledger with inline `->Ledger` calls. If they are truly unused, consider deleting them or making the event namespace use them.

`law.ledger/entries-for`, `entries-of-kind`, `events-since` are in a law namespace; if they have no consumers, either delete them or make them private if used only inside `law.ledger`.

### 3.7 `domain.ecology` — life-stage predicates

`prokaryotic?`, `eukaryotic?`, `multicellular?`, `complex?` are flagged. These are Phase 1+ biology predicates. Keep them as `^:api` unless the design has moved them to `law.ecology` or another namespace.

## 4. Phased execution plan

This work is intentionally broken into small PRs so each one can be reviewed and tested in isolation. No PR should touch more than one quadrant or more than one namespace unless the changes are purely mechanical (adding `^:private` or `^:api` metadata).

### Phase 1 — Tooling baseline (1 PR)

**Goal:** Make the current state reproducible and safe to iterate on.

- [ ] Capture the exact `clojure-lsp diagnostics` output in this spec (done above).
- [ ] Add a `clojure-lsp` configuration file or `clj-kondo` hooks so that `^:api` and `^:private` are respected and DSL-generated vars can be suppressed systematically.
- [ ] Add a helper script or alias that prints the unused-public-var count per namespace, e.g., `clojure-lsp diagnostics | grep unused-public-var | awk ...`.
- [ ] Run the full test suite (`clojure -M:test`) and record baseline green.

**Exit criteria:**

- The output of `clojure-lsp diagnostics | grep unused-public-var` is reproducible and matches the table in §1.
- `clojure -M:test` is green.

### Phase 2 — Test namespace DSL suppressions (1 PR)

**Goal:** Remove the noise from macro-generated test vars.

- [ ] In `test/domain/ecs/dsl_test.clj`, `rewind_test.clj`, and `ledger_test.clj`, add namespace-level comments explaining that `defcomponent`/`defevent` generate public vars and some are intentionally unused.
- [ ] Add a `clj-kondo` config for these test namespaces (or `^:clj-kondo/ignore` on the generated vars) to suppress the macro-generated false positives.
- [ ] Verify the 33 test-namespace findings are gone from the diagnostic output.

**Exit criteria:**

- `clojure-lsp diagnostics | grep unused-public-var | grep -E 'dsl_test|rewind_test|ledger_test'` returns nothing.
- `clojure -M:test` is green.

### Phase 3 — `domain.ecs.components` future-facing vocabulary (1 PR)

**Goal:** Resolve the 13 component-keyword findings.

- [ ] For each of the 13 unused component keywords, decide keep vs. delete based on the Phase 0/1 roadmap.
- [ ] For kept keywords, add `^:api` metadata and a docstring explaining the reserved phase.
- [ ] Delete any keyword that is genuinely redundant or abandoned.
- [ ] Update this spec to record the final kept/deleted list.

**Exit criteria:**

- `clojure-lsp diagnostics | grep unused-public-var | grep 'domain.ecs.components'` returns nothing or only documented API surface.
- `clojure -M:test` is green.
- `test/architecture_test.clj` still passes.

### Phase 4 — `law.*` contracts and schemas (1 PR)

**Goal:** Resolve the 67 law findings.

- [ ] For each `law.*` namespace, triage its flagged vars into: delete, private, or `^:api`.
- [ ] Prefer `^:api` for schemas and contracts that describe future systems.
- [ ] Delete only obvious orphans.
- [ ] Update the namespace docstrings if the public API surface changes.

**Exit criteria:**

- `clojure-lsp diagnostics | grep unused-public-var | grep 'src/law/'` returns nothing or only documented API surface.
- `clojure -M:test` is green.

### Phase 5 — `domain.*` and `infra.*` cleanup (1–2 PRs)

**Goal:** Resolve the remaining 44 domain and 13 infra findings.

- [ ] `domain.chemistry` — decide per-function; keep or delete.
- [ ] `domain.stellar` — keep `luminosity-from-fusion` and `radiation-equilibrium-temperature` as `^:api` if they are Phase 1+ primitives; delete `disk-formation-threshold` and `disk-sound-speed` if they are duplicated elsewhere.
- [ ] `domain.ecs.ledger` / `domain.ecs.event` / `domain.ecs.core` — consolidate ledger helpers; delete or privatize as needed.
- [ ] `domain.em` — keep or privatize `magnetic-torque`, `external-field-at`.
- [ ] `infra.render` / `infra.dev.window` / `infra.render.units` — mark dev entry points `^:api`; privatize internal helpers.
- [ ] `infra.menu` — keep or privatize `escape-action`, `confirm-close-hud` based on actual menu usage.

**Exit criteria:**

- `clojure-lsp diagnostics | grep unused-public-var | grep -E 'src/domain|src/infra'` returns nothing or only documented API surface.
- `clojure -M:test` is green.
- `bin/bench` shows no regression on the hot tick path (if touched).

### Phase 6 — Final verification and gating (1 PR)

**Goal:** Make the cleanup stick.

- [ ] Run `clojure-lsp diagnostics | grep unused-public-var` and confirm the only remaining findings are documented `^:api` surface.
- [ ] Add a CI step or `bin/analyze` gate that fails if new unused public vars appear (optional at this milestone; gating is handled by the parent epic in Phase E).
- [ ] Update `docs/STATIC-ANALYSIS.md` with the suppression conventions (DSL-generated vars, `^:api` markers).
- [ ] Update this spec to mark all phases done.

**Exit criteria:**

- `clojure-lsp diagnostics | grep unused-public-var` returns only documented `^:api` surface vars.
- `clojure -M:test` is green.
- `test/architecture_test.clj` still passes.

## Breakdown into ≤5-point tasks

The cleanup is split into four self-contained sub-tasks, each small enough to be a focused PR or work session. Each sub-task is tracked by its own kanban card in `kanban/tasks/`.

| UUID | Title | Scope | Estimate |
|------|-------|-------|----------|
| `static-analysis-dead-code-tooling-dsl` | Dead code cleanup: tooling baseline and test DSL suppressions | Phases 1–2: reproducible diagnostics, clj-kondo config, DSL-generated test var suppressions | 3 |
| `static-analysis-dead-code-ecs-components` | Dead code cleanup: ECS component future-facing vocabulary | Phase 3: keep/delete decisions for the 13 unused component keywords | 2 |
| `static-analysis-dead-code-law-schemas` | Dead code cleanup: law.* contracts and schemas | Phase 4: triage 67 law.* findings | 5 |
| `static-analysis-dead-code-domain-infra` | Dead code cleanup: domain.* and infra.* + final verification | Phases 5–6: remaining domain/infra findings, final docs, gating | 5 |
| **Total** | | | **15** |

## 5. Acceptance criteria

- [ ] `clojure-lsp diagnostics | grep unused-public-var` returns only vars that are explicitly marked `^:api` with a docstring explaining why they are public.
- [ ] Every `^:api` var is listed in a "documented API surface" section of this spec or its parent epic.
- [ ] No genuinely unused public var remains.
- [ ] No internal helper remains public if it is only used inside its own namespace.
- [ ] DSL-generated test vars are suppressed or documented, not deleted.
- [ ] `clojure -M:test` passes after every phase.
- [ ] `test/architecture_test.clj` passes (no cross-quadrant violations introduced).
- [ ] Hot-path performance (`bin/bench`) is unchanged if any tick-pipeline namespace is touched.
- [ ] `docs/STATIC-ANALYSIS.md` is updated with the new suppression conventions.

## 6. Risks and mitigations

| Risk | Mitigation |
|------|------------|
| Deleting a var that a downstream tool or consumer uses | Run `rg -F <var-name> src test` before deleting. For law vars, also search for qualified usages in `domain`. When in doubt, keep and mark `^:api`. |
| Making a function private that is used via reflection or a macro | Check macro expansion and test usage. If a macro expands to the qualified symbol, `^:private` will break it. |
| Marking a dev-only var `^:api` and then later changing it | Document `^:api` as "experimental / dev-only" in the docstring; no API stability guarantee until Phase E of the epic. |
| Deleting a DSL-generated test var by accident | Never edit macro-generated vars directly; suppress them via clj-kondo config. |
| Breaking the single-substrate architecture during cleanup | Run `test/architecture_test.clj` after every phase. Do not introduce `infra` imports into `domain`. |
| Regressing tick performance by removing a small helper that was inlined | Measure with `bin/bench` if the touched namespace is in the tick pipeline. |
| Suppressing too many warnings hides real dead code | Every suppression must carry a comment. Review suppressions quarterly. |

## 7. Open questions

1. Should the project adopt a project-wide `^:api` convention, or is it enough to use docstrings and clj-kondo suppressions?  
2. Should `clojure-lsp` be configured to ignore `^:api`-marked vars automatically, or should each suppression be explicit?  
3. For `domain.ecs.components`, should there be a single `^:api` docstring on the namespace declaring all component keywords as public vocabulary, or should each keyword be marked individually?  
4. Are any of the `infra.dev.window` functions bound to key events at runtime via `infra.input`? If so, they must stay public and be marked `^:api`.

## Estimate

**Story points: 15**

Rationale: the cleanup touches **157 unused public vars across 30 namespaces**, so the volume alone makes this larger than a single sitting. Each var must be triaged into one of four buckets (delete, `^:private`, `^:api`, or DSL-generated suppression), and every deletion requires `rg` verification against downstream consumers in both `src/` and `test/`. The risk is elevated in `law.*` and `domain.*` namespaces where qualified cross-namespace usage is common, and in `domain.ecs.components` where future-phase vocabulary must be preserved rather than removed. The six-phase execution plan maps to four small PRs (Phases 1–2, 3, 4, and 5–6 combined), each requiring a full test run (`clojure -M:test`), architecture-test compliance (`test/architecture_test.clj`), and, for hot-path changes, a `bin/bench` check. That combination of breadth, per-item verification, and multi-PR verification overhead places the task in the upper-middle range of the Fibonacci scale. The point total is raised from 13 to 15 to match the phase-level effort breakdown and the four sub-tasks below.

| Phase | Findings | Relative effort | Notes |
|-------|----------|-----------------|-------|
| Phase 1 — Tooling baseline | 0 | 1 | Reproducible diagnostics, clj-kondo config, helper script, baseline test run |
| Phase 2 — Test DSL suppressions | 33 | 2 | Mechanical namespace-level clj-kondo suppressions for macro-generated vars |
| Phase 3 — ECS components vocabulary | 13 | 2 | Keep vs. delete decision on reserved future-phase keywords, mark kept `^:api` |
| Phase 4 — `law.*` contracts/schemas | 67 | 5 | Highest triage complexity; many schemas are public API despite no current consumer |
| Phase 5 — `domain.*` and `infra.*` | 57 | 4 | Mixed delete/private/API decisions; requires checking runtime/dev bindings |
| Phase 6 — Final verification | 0 | 1 | Confirm only `^:api` surface remains, update docs, optional CI gate |

(End of spec)

---
Triage 2026-07-10: OPEN — clojure-lsp reports 312 unused-public-var (147 domain, 102 law, 49 infra, ~14 bench). Most are intentional-by-design (law schemas/contracts = public API; ECS future-facing vocabulary; render facade; bench entry points; unwired features). Real work = per-var triage: delete genuine dead code vs mark ^:api / suppress. This is the single largest remaining static-analysis slice; children (domain-infra, law-schemas, ecs-components, tooling-dsl) partition it sensibly — keep them.

Triage 2026-07-10: 15pt epic partitioned into four ≤5pt children (tooling-dsl, ecs-components, law-schemas, domain-infra). Moved to breakdown as umbrella.
---
