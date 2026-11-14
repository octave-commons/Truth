# Operations runbook

Operator reference for agents and humans: commands, the pm2 dev service, the
native demo and captures, and the actor dashboard. The rules these commands
serve live in `AGENTS.md`.

## Commands

```bash
clojure -M:test                              # full suite, cognitect runner (CI); includes architecture guards
clojure -M:test -n domain.stellar-test       # one test namespace
clojure -M:test -v domain.stellar-test/a-var # one test var
bin/test <group>                             # grouped runners: unit integration domain infra law shape render architecture

bin/analyze                                  # static analysis report (six tools; docs/STATIC-ANALYSIS.md)
bin/analyze --strict                         # CI mode; must exit 0
bin/analyze --fix                            # auto-format with cljfmt

bin/coverage --text                          # cloverage text summary (fast; docs/TESTING.md)
bin/bench-coverage                           # static namespace-level benchmark coverage
bin/bench :ecs :gravity                      # criterium benchmarks
bin/mutate                                   # mutation testing (Heretic, local-only, domain/law/shape)

clojure -M:dev                               # native dev window + nREPL 127.0.0.1:7888 (normally run by pm2)
clojure -M:demo list|check|serve [scenario]  # native tour; nREPL 127.0.0.1:7890
bin/demo-capture docs/demo/<new-dir>         # private Xvfb display: real PNG/MP4 captures
bin/formation-capture [<new-dir>] [seconds]  # private Xvfb display: formation run capture (default 360 s)
clj -M:dashboard                             # ημ actor dashboard on 127.0.0.1:7889
```

Benchmark groups (`bench/gates_of_truth/bench.clj`): `:ecs :spatial :gravity
:collision :hydro :em :tick :phase0 :render`; `:profile` enables hot-loop mode
for external profilers. With no arguments `bin/bench` runs every group. Stop
`gates-of-truth-dev` before benchmarking.

The legacy `:run` alias targets the absent `infra.main` namespace; it is not a
launch route.

## Dev service (pm2)

`dev/ecosystem.config.js` defines three pm2 apps:

| app | command | purpose |
|---|---|---|
| `gates-of-truth-dev` | `clj -M:dev` | GLFW window running Phase 0 + nREPL on 127.0.0.1:7888 |
| `truth-notebook` | `jupyter lab --port 8888` | Jupyter Lab with the clojupyter kernel |
| `truth-actor-dashboard` | `clj -M:dashboard` | actor dashboard on 127.0.0.1:7889 |

`gates-of-truth-dev` runs continuously on purpose. pm2 watching is off, so edits
do not reload:

- Load code changes with `pm2 restart gates-of-truth-dev` (starts a fresh
  nebula), or hot-reload through the nREPL to keep the live world.
- Read logs without blocking: `pm2 logs gates-of-truth-dev --lines 30 --nostream`
  (omit `--nostream` only to tail forever).
- "Address already in use" from `clj -M:dev` means the pm2 instance already owns
  port 7888; do not start a second one.

From a REPL connected to 7888:

```clojure
(require '[infra.dev.window :as w])
(w/reload-shaders!)                       ; after editing infra.render shaders
(w/take-screenshot! "/tmp/truth-dev.png") ; offscreen path with its own
                                          ; bodies-fn/camera, not the live window
```

`take-screenshot!` does not capture the live menu/inspector surface. Use
`bin/demo-capture` for full window/UI captures.

## Native demo and captures

`clojure -M:demo check` validates the seven scenario/arc contracts (`nebula`,
`protostar`, `ignition`, `accretion`, `planets`, `life`, `dispersed`).
`clojure -M:demo serve [scenario]` opens the native tour (`serve life` stages the
labeled life fixture). Later arcs are explicitly labeled, frozen ECS fixtures.

`bin/demo-capture <new-output-directory>` records the real window on a private
Xvfb display: PNGs, MP4s with a full-duration GIF for each, ignition, accretion
and planet camera orbits, and a native navigation tour driven by real mouse
input. The destination must not exist. `TRUTH_DEMO_PORT` (default 7890) must be
free; set it to isolate a capture from an already-running demo. Coverage,
prerequisites, limitations and the gallery (every recording as a GIF plus the
17 screenshots of the 2026-10-06 capture) are in `docs/demo/README.md`.

## Actor dashboard

`src/infra/dev/actor_dashboard.clj` serves an auto-refreshing (30 s) page on
<http://127.0.0.1:7889/> showing each actor's purpose, session counts,
inbox/outbox activity, live process status and recent research notebooks. Run
it with `clj -M:dashboard` or the `truth-actor-dashboard` pm2 app.

The tracked actor definitions live in `.ημ/actors/`, but the dashboard still
reads the gitignored `.eta-mu/actors/` path and needs a path migration before it
can discover them (`README.md` › Actor Dashboard). Research actor schedules and
topics: `docs/research/ACTORS.md`.
