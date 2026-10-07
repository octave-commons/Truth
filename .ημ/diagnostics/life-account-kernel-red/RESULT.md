# Witnessed RED: pure life-account kernel

One authorized focused JVM loaded both new namespaces and completed:
**25 tests, 320 assertions, 144 failures, 0 errors**. This is a real RED outcome,
not a missing-namespace or test-discovery failure. All 144 reported failures are
in `domain.life-account-test`; the executable law tests reported none.

The two domain functions still return explicit `not-implemented` rejections.
Failures cover the absent exact budget/witness, expected stock/revision changes,
replay/rejection retention, and closure behavior. Some no-mutation assertions
already pass against these inert placeholders; that is not kernel completion.

Command: `clojure -J-Xms256m -J-Xmx2g -M:test -n law.life-account-test -n domain.life-account-test`.
`JAVA_TOOL_OPTIONS=-Xms256m -Xmx2g` was explicitly supplied. Wall timeout:180s.
PID 968366; start 2026-10-07T15:08:24.096905+00:00; end 2026-10-07T15:08:32.977676+00:00;
elapsed 8.880383s; exit 1. The child was waited/reaped
and its `/proc/968366` path was absent afterward. The wrapper itself
completed exit0 after recording the child’s exit1. All four input hashes stayed
identical. Root was notified immediately that the JVM resource window was free.

Full logs and exact argv live in `attempt-01/`. stderr contains only the two
JVM-options notices; no warning is hidden as an acceptance pass. Touched-file
clj-kondo ended at zero errors/warnings after the two disclosed draft corrections.
No full suite, all-six strict, performance, ECS, natural-life or native acceptance
is claimed. Existing root admission/card/ledger/receipt changes were preserved.
