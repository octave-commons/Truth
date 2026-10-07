# Isolated committed-clock arithmetic

These are research calculations at Truth base
`98b847ce75491bdccacf2ec3a7476d451270bf49`, not a game simulation, production
regression suite or implemented clock. `bb arithmetic.clj` uses Java-number
semantics through SCI without loading a project namespace or a native window.

The final `arithmetic.clj`, `arithmetic.stdout.edn`, `arithmetic.stderr.txt` and
`execution.json` form the current, all-success example. The note separates
allowed steps from successfully published steps; this probe does not implement
or verify that host feedback. The illustrative 10 ms quantum/four-step cap are
not selected production parameters. Pause input is a **raw** wall interval;
`total-active-wall-ns` excludes it.

`initial-*` preserves the first execution, whose raw interval was ambiguously
named `active-wall-ns`. `renamed-*` preserves an intermediate naming correction
that also incorrectly renamed the active-total output label. The final version
corrects that label. Both earlier executions succeeded arithmetically; their
naming limitations are not presented as production failures or fixed gameplay.
The original execution command referred to the then-current `arithmetic.clj`;
each prefixed source is the exact source matching that record's SHA256.

All epochs/positions are illustrative. No native readback is embedded or
claimed. The two earlier results are retained for provenance, not repeated
qualification. The closure inventory records source/output hashes and review.
