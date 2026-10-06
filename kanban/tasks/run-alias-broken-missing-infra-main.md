---
category: "specs"
labels: ["specs", "tooling", "infra", "docs"]
write-id: "1791309968710-0.lftvwkku24hdzvxbu9"
source: "kanban/tasks/run-alias-broken-missing-infra-main.md"
title: "`clojure -M:run` has been broken since 0a9343a — `src/infra/main.clj` does not exist"
priority: "P2"
status: "in_progress"
estimate: "3"
uuid: "run-alias-broken-missing-infra-main"
created_at: "2026-07-25T00:00:00Z"
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

---
2026-10-06 playable-game wave: claim the existing 3-point launch repair in isolated truth-playable-gate worktree. Recover the deleted entry point from history; restore the documented console simulation and headless demo PNG paths, and expose the existing native game launch through an explicit documented command. Reconcile any default-command change against this card before changing behavior. Acceptance: executable launch paths, command tests, native-window smoke evidence, accurate README/CLAUDE/AGENTS launch instructions, full applicable static/test gates. This is runtime/tooling repair of the existing ECS and renderer, not a second game path. Leave the duplicate fix-run-alias-missing-infra-main unclaimed; root coordinates source commits and review.

2026-10-06 implementation scope resolved by runtime history recovery and root approval: restore infra.main with console default capped at 1000 ticks, explicit console [ticks] for finite runs, demo PNG through genesis/create-world and the existing renderer, and visible failure for invalid arguments. Native-window launch remains the existing clojure -M:dev and clojure -M:demo serve routes documented alongside the repaired commands; no duplicate native launch mode or new nREPL dependency. Red checkpoint: new entry-point test cannot load absent infra.main, confirming the intended missing implementation. Source changes proceed after root records the red checkpoint.

2026-10-06 independent local preparation review of green commit b5a7e39 by board-scout: no blocking correctness or architecture finding. Verified no-argument console default1000, finite explicit budget, termination without reseeding, same genesis/create-world plus arc/tick-genesis, existing renderer tick exactly once for demo, and worker-pool cleanup on error/normal CLI exit. README/CLAUDE/AGENTS distinguish console/native and OpenGL/Xvfb prerequisites. One test-strength finding at test/infra/main_test.clj:62: call-history assertion precedes invalid-argument cases, so add unchanged-history assertion after that loop to substantiate the test wording that no run starts. Root informed. This local review is preparation, not hosted approval or permission to mark done; full gates and canonical PR policy remain.
---