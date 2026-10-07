# Composed demo and gameplay source qualification

Source: `8487159bfcf6f7d805018e270174c9f3cd35e8ea`, an ancestry-preserving
merge of qualified warp parent `76ba6a46139e4de2d769aed0d32f61d4025681d3`
and demo repair parent `c7cc34a8248a615f39eeefd5ba8ac841b65e49fa`.
The merge has no `src/`, `test/` or `test-native/` delta against its first
parent. It adds the already-reviewed demo failure handling, dedicated demo-test
alias and CI call, atomic capture diagnostic directories, and documentation.
This qualification adds no implementation or admission decision.

## Observed results

The authorized sequence ran once, with each process reaped before the next
started. Every command exited **0**.

| Check | Result |
| --- | --- |
| `clojure -J-Xms256m -J-Xmx2g -M:demo-test` | 6 tests, 23 assertions, 0 failures/errors |
| `clojure -J-Xms256m -J-Xmx2g -M:test` | 948 tests, 16,775 assertions, 0 failures/errors |
| `bin/analyze --strict` | All six stages passed; no blocking findings |
| `clj-kondo --lint dev/demo.clj dev/demo_client.clj dev/demo_test.clj` | 0 errors, 0 warnings |
| `bash -n bin/demo-capture` | Passed |
| `bash -n bin/formation-capture` | Passed |

The demo tests exercise the actual 300 × 100 ms timeout in
`prepare-formation!`; neither the deadline nor `Thread/sleep` was replaced.
They also cover missing client input without connecting, absent service,
restoration after a failed/interrupted wait, and successful paused setup.
These are isolated host contracts, not a new native game or formation run.

The strict command runs clj-kondo, structural smells, Splint, clojure-lsp,
jscpd and cljfmt. Existing nonblocking structural warnings and the duplication
report remain in raw output. The full suite's stderr retains the intentional
`boom {:x 1}` error-log fixture; the test summary reports zero errors.

`source-before.json` records all **342 tracked source/configuration/document
hashes** in the declared scope. HEAD, clean source diff and all hashes match
before and after every stage. Each stage contains its exact command, bounded
environment, PID, raw stdout/stderr, exit, reap time, log hashes and post-guard.
`sequence-result.json` records the complete sequence. The final command was
reaped at `2026-10-07T01:37:36.017486+00:00`; no qualification JVM is retained.

Both `JAVA_TOOL_OPTIONS` and `_JAVA_OPTIONS` were set to 256 MiB initial /
2 GiB maximum, with explicit equivalent `-J` flags on the two direct Clojure
commands. The root-owned native PID 131581 was reported running unchanged
source `9c5c889` concurrently. This lane did not contact, restart, reload,
capture or control it. Durations are not isolated performance measurements.

## Compatibility and limits

`alias-audit.edn` verifies that `:test` and `:native-render-test` are unchanged
from the first parent, while `:demo-test` is added. There is no separate
`:native-test` alias in either revision; the existing native path is
`test-native`. The native GL suite was not executed by this qualification.

`launch-paragraph.txt` preserves the existing documentation for the real
console route, offscreen PNG route and interactive demo/dev routes
(`docs/demo/README.md:388–394`). `production-merge-diff.txt` is the empty
source/test delta described above. The original PR7 diagnostics are already in
the merged ancestry; this bundle does not copy or reinterpret their captures.

The existing verification owner
`f369c598-279c-498d-a64f-45d2ce16ad34` remains **In Progress, 3 points**.
The canonical comment records these results without claiming a Rheos state
transition, hosted approval, new native gameplay, manual approach, binding,
commitment, sculpt, graceful exit or a Gate. Existing native observations stay
bound to their original loaded source and world. Root owns commit and
publication of this evidence.
