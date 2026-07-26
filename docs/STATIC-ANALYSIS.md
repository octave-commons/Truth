# Static Analysis

Deterministic, rule-based code-quality tooling for Gates of Truth. No LLM in the
loop — same source in, same findings out — so results are reproducible locally
and gateable in CI.

## One command

```bash
bin/analyze            # full report; exits non-zero only on blocking findings
bin/analyze --strict   # CI mode: also fail on structural HARD breaches
bin/analyze --fix      # apply safe auto-fixes (cljfmt formatting)
```

CI runs `bin/analyze --strict` plus the architecture invariants
(`.github/workflows/static-analysis.yml`).

## The six tools

| # | Tool | Detects | Config | Gating |
|---|------|---------|--------|--------|
| 1 | **clj-kondo** | bugs, unused/dead bindings, redundant forms, shadowing, idiom anti-patterns | `.clj-kondo/config.edn` | **errors AND warnings block** |
| 2 | **smell_report** | god namespaces, mega-functions, parameter bloat, fan-out, undocumented public fns | `dev/smell_report.clj` | **HARD blocks (`--strict`)** |
| 3 | **Splint** | non-idiomatic forms (kibit successor) | `.splint.edn` | **blocks** |
| 4 | **clojure-lsp** | project-wide dead code (unused public vars) | `.lsp/config.edn` | **blocks** |
| 5 | **jscpd** | copy-paste duplication (token-based) | `.jscpd.json` | **blocks above `threshold`** |
| 6 | **cljfmt** | formatting consistency | `deps.edn :cljfmt` | **blocks** |

### The gating contract (2026-07-25)

All six are gating. Five of them (1, 3, 4, 6, and the structural HARD tier) were
at **exactly zero** when they were promoted, and that is the only reason they were
promoted — a gate turned on while findings remain is a gate that gets merged past.

That is not hypothetical here. `static-analysis` failed on **33 consecutive pushes to
`main`** between 2026-07-10 and 2026-07-21 and every one of them landed anyway, while
twelve kanban cards sat `done` with the findings they closed already back. The failure
mode was **direct pushes**, not merged PRs — there has only ever been one PR (#1), and
it targeted a feature branch. The post-mortem is
`kanban/tasks/static-analysis-regression-2026-07-24.md`.

**Fixed 2026-07-25.** `main` is now a protected branch requiring the `analyze` check,
with `enforce_admins: true` — so a red run blocks everyone, which is the only setting
that constrains the failure mode above. Verified: a throwaway PR with a red `analyze`
was refused with "the base branch policy prohibits the merge".

So the rule is:

- **A red `bin/analyze --strict` is a blocker, not a backlog item.** Fix it, or add
  a documented per-site suppression (below) with its reason.
- If you truly cannot fix it, **open a regression card**. Do not demote a tier back
  to advisory, and never leave a card `done` whose finding has returned.
- jscpd is the one number that is a *ratchet at today's level* rather than zero.
  `.jscpd.json` says so explicitly and names the card that lowers it. Lower it as
  clones come out; never raise it.

## Structural thresholds ("dung heaps")

`dev/smell_report.clj` reduces over clj-kondo's analysis export. Thresholds
(`warn` surfaces in the report, `hard` fails `--strict`) live at the top of that
file:

| Metric | warn | hard |
|--------|------|------|
| namespace LOC | 500 | 1200 |
| namespace vars | 30 | 60 |
| function LOC | 40 | 80 |
| arity (params) | 5 | 8 |
| fan-out (deps) | 18 | 30 |

**Zero HARD offenders as of 2026-07-25.** (The previous note here named
`infra.render` and `domain.stellar` by loc; both are gone, and the loc figures were
measured by the pre-2026-07-25 metric anyway — see below.)

`loc` counts **code lines**: blank lines, comment-only lines, and the docstring span
are excluded, and the raw line count is reported alongside it. Before this the
metric counted every line, so the mandatory docstrings and design-note comment
blocks this codebase requires were themselves scored as complexity — two of the
four HARD breaches on 2026-07-24 were docstrings, not code.

### Vocabulary namespace exception

`domain.ecs.components` is a pure vocabulary namespace: it defines the canonical
`:component/*` keywords used by every ECS system. It is exempt from the
`namespace-vars` and `missing-docstrings` thresholds because splitting it would
fragment the shared vocabulary and create import noise across every system. The
exemption is explicit in `dev/smell_report.clj` (`vocabulary-namespaces`).

## Suppression conventions

Every suppression in this tree carries its reason **at the site**. A bare
suppression is a regression waiting to happen — see
`kanban/tasks/static-analysis-regression-2026-07-24.md` for what that costs.

### `^:export` — declared API surface

clojure-lsp's `unused-public-var` linter honours `^:export` metadata natively.
Use it for a var that is **deliberately public with no internal consumer**:

- `law/` schemas, contracts, validators, and physical constants. `law/` IS the
  project's vocabulary — a declared noun that nothing reads *yet* is the point of
  the layer, not dead code.
- Debug/tooling accessors, backward-compatibility aliases, and predicate families
  whose siblings are live (a family with holes reads worse than a complete one).

```clojure
(def ^:export xuv-bands
  "The XUV (X-ray + EUV) bands that drive atmospheric escape."
  [:xray :euv])
```

Cheap to add, which is the risk: `^:export` is a claim that the var is *finished
and offered*. It is not a way to quiet a var you have not thought about.

### `UNUSED-PENDING` — incomplete but intended

CLAUDE.md names this class outright: "Several features are coded but never ticked
… they look dead but are incomplete, not abandoned." Those get the
`UNUSED-PENDING` marker, and the marker is deliberately verbose, because pending
work should read as pending:

```clojure
;; UNUSED-PENDING: <what is missing, in one line>
;; See kanban/tasks/<card>.md
#_{:clj-kondo/ignore [:clojure-lsp/unused-public-var]}
(defn dipole-moment ...)
```

Required parts: the `UNUSED-PENDING` token, one line saying what is missing, and
a `kanban/tasks/…` or `docs/…` cross-reference. Without the cross-reference it is
indistinguishable from rot.

**Mechanism note, verified 2026-07-25 — do not re-derive this the hard way:**

| form | works? |
|---|---|
| `^:export` var metadata | **yes** |
| `#_{:clj-kondo/ignore [:clojure-lsp/unused-public-var]}` discard form | **yes** |
| `^{:clj-kondo/ignore [...]}` inline var metadata | no |
| `:exclude-when-defined-by #{domain.ecs.dsl/defcomponent}` | **no** — `.clj-kondo/hooks/ecs_dsl.clj` rewrites the macro into plain `def`, so the analysis records `:defined-by clojure.core/def` and the macro identity is gone |
| ns-level `{:splint/disable [...]}` metadata (splint 1.24.0) | no |
| `#_{:splint/disable [rule]}` discard form | **yes**, and it covers nested forms |

### `.lsp/config.edn` and `.splint.edn`

Namespace-wide exclusions live in `.lsp/config.edn`, each with its reason inline.
Only false positives belong there — dynamic resolution (`bin/bench`'s
`ns-resolve`), documented nREPL entry points, `defprotocol` methods, and
macro-expansion output. `.splint.edn` disables **no rule globally**; splint
suppressions are per-form.

## The DSL hooks

`domain.ecs.dsl` defines `defcomponent`/`defevent`/`defsystem`/etc. clj-kondo
can't expand custom macros, so `.clj-kondo/hooks/ecs_dsl.clj` rewrites each into
the `def`/`defn` forms it actually produces. Without these, the linter reports
~28 false-positive "unresolved symbol" errors. **Keep the hooks in sync with
`src/domain/ecs/dsl.clj`** — if you add or change a DSL macro, update the hook.

## Editor integration

clojure-lsp reads `.clj-kondo/config.edn` directly, so editor diagnostics match
CI with no extra setup.

## Running tools individually

```bash
clj-kondo --lint src test                              # 1
clj-kondo --lint src test \
  --config '{:output {:analysis {:var-definitions {:meta true} :arglists true} :format :edn}}' \
  | clojure -M dev/smell_report.clj --strict           # 2
clojure -M:splint                                      # 3
clojure-lsp diagnostics                                # 4
npx jscpd@4                                            # 5
clojure -M:cljfmt check src test                       # 6
```
