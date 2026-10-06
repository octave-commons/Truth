# Focus-offset boundary: full qualification

The complete ordinary suite and strict analysis ran sequentially on the final
source described in `attempt1/*-command.json`, with unchanged source/test/design
hashes verified again in each result. Both processes exited 0 and were reaped.

| Gate | Actual result | Completion (UTC, 2026-10-06) |
| --- | --- | --- |
| `clojure -J-Xms256m -J-Xmx2g -M:test` | 939 tests, 16,077 assertions, zero failures/errors | 23:43:27.592762 |
| `bin/analyze --strict` | All six blocking stages passed | 23:44:54.107377 |

Strict evidence: clj-kondo zero errors/warnings; structural zero HARD breaches
and zero undocumented public functions; Splint 292 files / zero style warnings;
clojure-lsp no unused public vars; jscpd 1.53% duplicated lines within the
configured 1.7% ratchet; cljfmt all files formatted. Existing nonblocking
structural warnings and clone details remain in the raw log. No exclusions,
threshold changes or suppression were added. No failed full-gate attempt or
source repair was needed.

The final law source includes the single docstring blank line added after the
focused test; the full suite and strict gate both ran that final source. The
committed RED test file is byte-identical. The implementation changes only the
named offset law and its guarded host use; design §7.5 documents it. The current
head is the RED checkpoint `2b30e5055bc793e4c9152425356f535ba3e5d2f6` plus those
recorded working-tree changes, not an invented GREEN commit identity.

The JVM heap was bounded to 2 GiB, including child JVMs through recorded
`JAVA_TOOL_OPTIONS`. Root explicitly allowed concurrent qualification in the
disjoint Kepler checkout and a retained hardware native service. Durations are
correctness-run provenance, not isolated performance or gameplay evidence.
No native input, world mutation, benchmark, board transition, hosted settlement,
commit or push occurred in this implementation lane. The existing card remains
InProgress / 3 and its full manual gameplay acceptance remains open.
