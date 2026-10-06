# Focus callback RED checkpoint

Source base: `6944df4ec266ec25059c89181fe3d07522d076b3`.
Card: `focus-follows-pilot`, reopened REVIEW → IN_PROGRESS through canonical
Rheos on 2026-10-06; estimate remains 3. The original body is preserved.

After the benchmark owner explicitly released JVM execution at 20:19:49 UTC:

```sh
clojure -J-Xms256m -J-Xmx2g -M:test -n infra.render.input-test
```

Final RED: **9 tests, 86 assertions, 23 failures, 0 errors**, process exit 1.
The six earlier tests and the new held-flight-key control pass. Failures are
the new arrow and comma/period callback regressions: GLFW repeats and release
apply extra focus actions, rather than retaining the single physical press.

The tests construct and invoke the real `GLFWKeyCallback`, free the native
callback allocation in `finally`, and use the production `IntentAtom` and
`drain-intents` path. They need no window, display, native key injection, or
running dev service. They verify the render callback does not publish world
changes, preserve concurrent published data through the queued action, and
assert Spark position/velocity and other state survive focus adjustment.

`red.log` is the first observation. Test-local shadowing warnings were then
removed and formatting applied; the final rerun (`red-final.log`) gives the
same 23 failures. Final clj-kondo: **0 warnings, 0 errors**. `git diff --check`
passed. No production source changed and no native approach/commit/sculpt
acceptance is claimed.

SHA-256 `red-final.log`:
`49ca1bd873d605a912a48147f11f4777653fb1ee66a360a2de2d97746e87c83f`.

Root must commit this observed RED before the minimal production guard change.
