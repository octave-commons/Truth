# Focus callback GREEN checkpoint

RED checkpoint: `558476c`, 9 tests / 86 assertions / 23 failures / 0 errors.
Production change: `src/infra/render/input.clj` guards the existing `player-key`
dispatch with `GLFW_PRESS`. Press/release held-key bookkeeping is unchanged.
No focus arithmetic, arrow distance, movement mapping, palette dispatch,
camera, pacing, position, or velocity ownership changed.

```sh
clojure -J-Xms256m -J-Xmx2g -M:test -n infra.render.input-test -n infra.dev.window-test -n architecture-test
```

Result: **20 tests, 131 assertions, 0 failures, 0 errors**, process exit 0.
The actual GLFW callback regressions now pass, as do the real queued-intent
window tests and architecture checks. The tests are headless callback invocation,
not native physical input or completed manual planet approach.

Touched-file checks (`src/infra/render/input.clj` and
`test/infra/render/input_test.clj`): cljfmt check exit 0; clj-kondo 0 errors and
0 warnings; Splint 1.24.0 checked 2 files with 0 warnings, exit 0.
`git diff --check` passed. All focused JVMs were reaped and the runtime owner
was told the resource window is released. No full suite, full strict gate,
benchmark, or native run was attempted here; those belong to the integrated
checkpoint coordinated by root.

SHA-256 `green-focused.log`:
`a51be989b4b651a3fc09d0851e3dfb45367d2090a339bdc060da67158c98b6f7`.

Existing `focus-follows-pilot` remains IN_PROGRESS. This repair does not close
its full manual fly → bind → commit → rendered terrain acceptance.
