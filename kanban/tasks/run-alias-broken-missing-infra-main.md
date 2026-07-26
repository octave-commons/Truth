---
uuid: "run-alias-broken-missing-infra-main"
title: "`clojure -M:run` has been broken since 0a9343a — `src/infra/main.clj` does not exist"
status: "ready"
priority: "P2"
labels: ["specs", "tooling", "infra", "docs"]
created_at: "2026-07-25T00:00:00Z"
source: "kanban/tasks/run-alias-broken-missing-infra-main.md"
category: "specs"
estimate: 3
---

# `clojure -M:run` dangles: `infra.main` is gone

Found 2026-07-25 while trying to run the live smoke test that
`kanban/tasks/static-analysis-regression-2026-07-24.md` called for. Pre-existing and
unrelated to that work.

## The failure

```
$ clojure -M:run demo
Execution error (FileNotFoundException) at clojure.main/main (main.java:40).
Could not locate infra/main__init.class, infra/main.clj or infra/main.cljc on classpath.
```

`deps.edn` has `:run {:main-opts ["-m" "infra.main"]}`, but `src/infra/main.clj` does
not exist and is **not in `HEAD`** — `git cat-file -e HEAD:src/infra/main.clj` fails.
Last touched by `0a9343a` ("Π: Snapshot of Phase 0 physics and research
optimizations"), where it appears to have been dropped.

## Why it matters

`CLAUDE.md` documents both forms as the way to run the simulation:

```bash
clojure -M:run                               # Phase 0 console simulation
clojure -M:run demo                          # render one frame to /tmp/truth-view.png
```

Neither works. The headless-PNG path is also the only render verification that does
not need a GLFW window, so its absence is why the static-analysis epic had to fall
back to loading every render namespace plus `clj-kondo`'s `:unresolved-symbol` at
`:error` to argue the render path survived a 179-alias facade prune. That argument
held, but a frame render would have been better evidence.

`/tmp/truth-view.png` exists dated 2026-07-23, so something rendered a frame recently
— worth checking whether the entry point moved rather than vanished (e.g. into
`infra.dev.window`, or a `bin/` script) and the alias simply was not updated.

## Work

1. Establish what `infra.main` did — check `git show 0a9343a^:src/infra/main.clj` and
   the commits around it.
2. Decide: restore it, or repoint `:run` at whatever superseded it.
3. Whichever way, `clojure -M:run demo` must render a frame headlessly again, because
   that is the cheapest render smoke test in the tree and CI could run it.
4. Fix `CLAUDE.md` if the invocation changes.

## Done when

- [ ] `clojure -M:run` starts the console simulation.
- [ ] `clojure -M:run demo` writes a PNG.
- [ ] `CLAUDE.md`'s Commands section matches reality.
- [ ] Consider adding the demo render to CI — it is a genuine end-to-end check that
      the ECS → render path still produces pixels, which no current test covers.
