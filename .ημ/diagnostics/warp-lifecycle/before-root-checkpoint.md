# Root baseline checkpoint

Observed 2026-10-07T00:43:44.922948+00:00.

Root independently verified all 38 BEFORE hashes and 15 committed RED hashes, the 44-path staging inventory, no production/test delta, and byte-prefix preservation of receipt, reflection and Rheos event ledgers.

The first staged whitespace check reported one diagnostic in the exact captured Clojure exception: `before/capture-attempt1/exception.edn:67: new blank line at EOF`. The commit command had not run. Its raw bytes and recorded checksum are preserved. A second check excluding only that raw exception passes; this is an artifact-format observation, not a source or static-analysis gate exclusion.

The five-case baseline is accepted for matched comparison. It does not qualify the bug as correct or assert rendered frame performance. Production remains unchanged at this checkpoint.
