# Test Coverage & Mutation Testing

Two complementary questions about the test suite:

- **Coverage** (`cloverage`) — *was this code executed by a test?* Fast, mature,
  runs in CI.
- **Mutation testing** (`heretic`) — *if I break this code, does a test notice?*
  Slower, sharper, experimental. Run locally.

Coverage is necessary but not sufficient: a line can be executed by a test that
asserts nothing about it. Mutation testing finds those blind spots — a *surviving*
mutant is code you run but never actually verify.

## Coverage — `bin/coverage`

```bash
bin/coverage            # HTML + codecov.json + text summary -> target/coverage/
bin/coverage --text     # text summary only (fast; no HTML) — for CI/terminals
bin/coverage -n 'domain\.stellar' -t 'domain\.stellar-test'   # scope to one ns
```

Extra args pass through to `cloverage.coverage` (`clojure -M:coverage --help`).
Output is git-ignored (`target/coverage/`). The whole tree is measured — every
test namespace runs headless, including the infra/GL suites.

Deps live in the `:coverage` alias (`cloverage 1.2.4`). Nothing about coverage
touches the normal build.

## Mutation testing — `bin/mutate`

Heretic ([parenstech/heretic](https://github.com/parenstech/heretic)) maps
tests→code with ClojureStorm, mutates a source form, then re-runs **only** the
tests that cover it. Killed = a test failed (good). Survived = all tests passed
despite the mutation (a gap).

```bash
bin/mutate                 # collect (if stale) + mutate + report
bin/mutate collect         # (re)build the test->code coverage map only
bin/mutate mutate          # mutate + report (assumes coverage collected)
bin/mutate survivors       # surviving mutants from the last run (triaged)
bin/mutate no-coverage     # forms no indexed test reaches
bin/mutate watch           # continuous mutation testing on file changes
bin/mutate clean           # drop the cached coverage index
bin/mutate mutate --files src/domain/stellar.clj   # scope to one file
```

Config: `heretic.edn`. Operators via `:preset`
(`:fast` 16 · `:standard` 36 (default) · `:minimal` 31 · `:comprehensive` 81).

### Scope and safety

- **Pure layers only.** We instrument and mutate `domain`, `law`, `shape`.
  `infra` is rendering/GL/IO — mutating it is slow and low-signal, so it is left
  out (see `:heretic` alias `instrumentOnlyPrefixes` + `heretic.edn`
  `:instrument-prefixes`). The infra/GL and architecture test namespaces are in
  `:exclude-test-namespaces` for the same reason.
- **Non-destructive.** `mutate`/`watch` run in a disposable copy
  (`.heretic-sandbox/`). Your working tree is only ever read; an interrupted run
  leaves nothing behind.
- **Isolated compiler.** ClojureStorm *replaces* the Clojure compiler, but only
  inside the `:heretic` alias (`:classpath-overrides {org.clojure/clojure nil}`).
  The normal build, tests, and REPL are untouched.
- **`rsync` recommended.** With it, the sandbox and coverage index are reused
  incrementally between runs. Without it, every run does a full copy + full
  re-collect (much slower). It is installed here.

### Caveats

Heretic is **pre-1.0 and self-described as "not ready for use"** — API and
behavior may change. It is pinned to a specific commit in `deps.edn`
(`io.github.parenstech/heretic :git/sha …`). It is intentionally **not** wired
into CI: coverage collection under instrumentation is expensive, and the tool is
experimental. Use it locally to harden a namespace, then read `bin/mutate
survivors`.

## CI

`.github/workflows/coverage.yml` runs `bin/coverage --text` on push/PR as an
**advisory** report (matching the advisory-tools convention in `bin/analyze`) —
it surfaces the coverage table in the log but does not gate the build. To make it
a gate later, add `--fail-threshold N` to the `bin/coverage` invocation (cloverage
exits non-zero below `N`% total coverage) once a baseline is agreed.
