# Manual focus consumer cadence — observed RED

Source: `85f307ffd8b017f7d4c3d498490227eeaf087d93`, branch `codex/truth-focus-cadence`.
Production `src/` has no diff. Root owns the RED checkpoint before implementation.

`JAVA_OPTS='-Xms128m -Xmx1g' clojure -M:test -n infra.dev.focus-cadence-test`
exited 1: **6 tests, 70 assertions, 24 expected failures, 0 errors**. The process
was reaped. Exact command, source, heap, start/end times and exit are in
`red-start.json` and `red-end.json`.

The actual serial `sim-loop` runs without any render frame. Its test tick uses
production `ecs.tick/run-parallel`, `integrator-system` and `binding-system`.
Two bodies start half an AU apart and move together at 2 AU/tick. With zero and
0.25-AU frame shifts, binding decays `[0.02, 0.018, 0.016, 0.014]` because focus
stays behind after the first tick. Expected binding is `[0.02, 0.04, 0.06, 0.08]`.

The 24 failures cover 18 missing consumer alignment/overlap/accrual assertions,
2 current-offset assertions, 1 current-manual-mode override, 2 held/error-paused
attention updates, and 1 missing visible follow-failure report. Physical columns,
actual integrator motion, queued thrust/radius/intensity, tracking mode, absent
observer/position and loop continuation controls pass. An injected AssertionError
pins the existing intent failure containment contract before moving the call.

Static checks all exit 0: clj-kondo zero warnings/errors, touched-file cljfmt,
Splint zero style warnings, and diff whitespace. The `:splint` alias retains its
configured src/test/test-native paths, so this invocation checked 292 files;
it was broader than a touched-file check, not a full strict gate. Commands and
logs are in `red-static.json`. No full test suite, strict gate or benchmark ran.
Independent source review by board_scout found no blocking test or contract issue.

## Deliberate boundary

Refresh attention **after intent drain, before the simulation fold**, using current
host manual mode and offset. Tracking camera focus remains authoritative in its
mode. Preserve the existing pure position-only follow law and failure containment.
No new ECS writer, simulation clock, physical state mutation, force or camera law.

Published focus still represents the pre-fold body position: this is not a
post-fold prediction/recenter repair. Sculpt/action anchors already calculated
during drain or render remain outside this correction. The live manual
fly-bind-commit-voxel acceptance is still open.

Native motivation is frozen in the separately owned focus-input bundle at
`23-post-fix-manual-before.edn` (actually sampled during held thrust) and
`24-post-fix-manual-released.edn`: focus gaps 2935.70295 AU and 601.66186 AU.
The corresponding |v|dt values, 791.65820 AU and 294.42490 AU, are displacement
proxies, not exact per-fold motion: those snapshots omit the contemporaneous
COM frame shift. This RED establishes cadence as one cause without attributing
the entire native gap to cadence or claiming a complete approach remedy.

RED log SHA256: `7afc60499cd968ea1b31dc60b08a48f7a45ede8f132b1e0cc5a2b17f50ae9114`.
Test source SHA256: `4d2019a18d4f5c66ea1f49082af1ce28301fbb4e38d952bd5ad90072e8176f68`.
