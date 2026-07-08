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
| 1 | **clj-kondo** | bugs, unused/dead bindings, redundant forms, shadowing, idiom anti-patterns | `.clj-kondo/config.edn` | **errors block** |
| 2 | **smell_report** | god namespaces, mega-functions, parameter bloat, fan-out, undocumented public fns | `dev/smell_report.clj` | **HARD blocks (`--strict`)** |
| 3 | **Splint** | non-idiomatic forms (kibit successor) | `deps.edn :splint` | advisory |
| 4 | **clojure-lsp** | project-wide dead code (unused public vars) | built-in | advisory |
| 5 | **jscpd** | copy-paste duplication (token-based) | `.jscpd.json` | advisory |
| 6 | **cljfmt** | formatting consistency | `deps.edn :cljfmt` | advisory |

"Advisory" tools are printed but don't fail the build while the tree converges.
Promote one to blocking by adding its name to the `FAIL` set in `bin/analyze`.

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

Current HARD offenders: `infra.render` (2038 loc), `domain.stellar` (1351 loc).
These are known and tracked — tighten thresholds as namespaces are split.

### Vocabulary namespace exception

`domain.ecs.components` is a pure vocabulary namespace: it defines the canonical
`:component/*` keywords used by every ECS system. It is exempt from the
`namespace-vars` and `missing-docstrings` thresholds because splitting it would
fragment the shared vocabulary and create import noise across every system. The
exemption is explicit in `dev/smell_report.clj` (`vocabulary-namespaces`).

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
