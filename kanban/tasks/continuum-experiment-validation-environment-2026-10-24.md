# Continuum experiment: required tooling unavailable

Status: blocked; no merge or full static-gate completion claimed.

`bin/analyze --strict` exits 2 because clj-kondo, Clojure CLI and clojure-lsp
are absent from PATH. `bin/bench :ecs` exits 127 at Clojure dependency resolution.
The recovered jar runtime supports the focused suite and actual native runs,
but is not substituted for either repository gate. Restore the official CLI
and analysis/benchmark dependencies, run both gates, and address findings before
merging the experiment overlay. Do not demote or suppress any gate.

Evidence: `docs/demo/continuum-impact-2026-10-24/strict-analysis.log` and
`benchmark-gate.log`. Focused suite: 43 tests / 157 assertions, zero failures
or errors, retained separately in `final-checks.log`.
