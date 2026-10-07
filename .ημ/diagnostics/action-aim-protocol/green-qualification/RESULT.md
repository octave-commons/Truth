# Sculpt contextual-input GREEN qualification

**Canonical gate passed; `focus-follows-pilot` is Review, estimate 3.** Exact source/test pins are in [VERDICT.json](VERDICT.json). The reviewed production loop/input stayed unchanged throughout qualification. The root-approved changes after the initial source freeze affect only the new host test.

| Run | Observed result | Interpretation |
| --- | --- | --- |
| Focused01 | 56 tests / 544 assertions / 2 failures / 0 errors | Wrong test expectation: all verbs assumed uplift cost. Production law prices uplift, volcanism and erosion differently. Raw failed bytes preserved. |
| Focused02 | 56 / 544 / 0 / 0 | Explicit expected balances 18.0, 17.0 and 18.5 pass. |
| Canonical gate01 | Full967 / 17006 / 0 / 0; move refused exit3 | Two owned Math/sqrt style warnings and formatting of the owned test. |
| Canonical gate02 | Full967 / 17006 / 0 / 0; all six strict gates; move exit0 | Explicit clojure.math require/two sqrt calls plus formatter indentation. No production or behavioral expectation change. |

The final canonical command ran `clojure -M:test` and then `bin/analyze --strict` through verified upstream Rheos. It closed at16:25:50.864486Z in100.100s, with mainPGID1410395 reaped, all17 observed process identities absent, no timeout and all304 input pins unchanged. Heap was256MiB/2GiB. Raw [stdout](canonical-gate-02/gate.stdout), [stderr](canonical-gate-02/gate.stderr) and [execution record](canonical-gate-02/gate-execution.json) retain the complete evidence. The stderr `boom` trace is the existing intentional window-test fixture; both full suites report zero test errors. Duplication findings remained within the configured threshold; this is a passed gate, not a claim of zero duplicate code.

The original committed RED remains13 absent-API failures plus a separately passing real legacy stale-anchor characterization. Its absent-API guard had prevented the new per-verb matrix from evaluating; the later oracle defect does not retroactively change that observed RED. Older bundle manifests and source freezes remain historical and immutable; current test bytes are intentionally different, with both exact pre-repair versions retained. The malformed first BB format-fix command is also preserved as a diagnostic parse failure, followed by a successful corrected command.

This establishes the callback/IntentAtom/serial-world behavior and static qualification. It does not establish native Sculpt play, host cost or FPS. [HOST-COST-PROTOCOL.md](HOST-COST-PROTOCOL.md) is a proposed next measurement only. No further JVM run, hosted review, commit, push or merge occurred here.
