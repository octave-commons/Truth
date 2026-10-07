# Canonical committed-source review gate

Root ran the canonical Rheos `in_progress → review` move for
`fix-warp-disabled-stale-write` at exact commit
`4f4eb63ec07ffd4678c3bb635f7c11e4b6c30223`. The configured build gate ran
`clojure -M:test` followed by `bin/analyze --strict`; it was not bypassed or
substituted with an earlier result.

`gate.stdout:235–236` records **948 tests, 16,775 assertions, 0 failures,
0 errors**. All six strict stages completed: clj-kondo, structural smells,
Splint, clojure-lsp, jscpd and cljfmt. The final verdict is **no blocking
findings** and the log confirms the canonical move to **Review**. Structural
warnings and duplication reports remain in the raw output; passing the gate
does not mean those reports are empty. `gate.stderr` retains the intentional
`boom {:x 1}` exception emitted by
`test/infra/dev/window_test.clj:113–123`; it is not a test failure.

The command process PID 366674 exited 0 and was reaped at
`2026-10-07T01:19:29.538077+00:00`. Before/after HEAD and six source, regression,
dependency, configuration and analyzer hashes match. The runtime was the
reviewed upstream CLI
`83c6b397278d418d69ce6509b8c3d9fe87e88cca9141b28143d9efb5d78a75a7`.
Heap bounds were 256 MiB initial / 2 GiB maximum. The retained native process
ran concurrently, with its observer restored before this gate; this is
correctness/static qualification, not an isolated timing result.

`prepared-command.json` is preserved as the earlier unstarted preparation;
`command.json` and `result.json` record the subsequent actual run. Raw logs
are unchanged. This new exact-source full run includes the final three-line
test formatting correction and does not rewrite the earlier qualification
boundary or failed attempts.

The card's other-disabled-emitter audit is recorded separately in
`../other-disabled-emitter-audit.md`, with a canonical comment and receipt.
Observer halo disable is exposed through ordinary settings; thermal expiry
and conditional observer-component removal have narrower consequences than
blanket ghost-force claims. No collateral repair or new task was added.

Review is a board state, not hosted approval or Done. The complete original and
reverse timing pairs remain visible: the initial broad penalty did not
reproduce in the reverse pair, but this does not prove neutrality, speedup or
regression absence. No native gameplay, frame-rate or Gate completion is
claimed. Root owns the final evidence commit and publication.
