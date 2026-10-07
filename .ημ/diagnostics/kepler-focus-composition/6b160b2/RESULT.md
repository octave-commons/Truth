# Final Kepler and focus composition qualification

(己, p=1.0) Exact commit `6b160b2dc63e923be5badad7d2de846d74c99093`
passes the full ordinary test suite and all six strict stages. This qualifies the
composition of allocation-only Kepler reuse with the later named focus-offset
boundary repair. No source edit or additional benchmark occurred in this pass.

| Command | Result | UTC interval |
| --- | --- | --- |
| `clojure -M:test` | 945 tests, 16,631 assertions, 0 failures/errors; exit 0 | 2026-10-06 23:56:49–23:57:50 |
| `bin/analyze --strict` | All six stages pass; exit 0 | 2026-10-06 23:57:50–23:59:10 |

(己, p=1.0) Both commands used `JAVA_OPTS=-Xms256m -Xmx2g` and ran
sequentially. Both processes and their wrapper exited and were reaped. The
command records preserve exact arguments, PIDs, UTC times, exits and checks that
HEAD and all seven declared source/test/fixture hashes stayed unchanged before
and after each command. A native game and passive capture were permitted to
overlap; these durations are correctness-run provenance, not performance data.

(己, p=1.0) Strict results: clj-kondo zero errors/warnings; structural report
zero hard breaches and undocumented public functions, with nonblocking warnings
preserved in the complete log; Splint zero style warnings; clojure-lsp no unused
public vars; jscpd 1.52% duplicated lines (below the configured limit); cljfmt all
files formatted. No suppression, blanket formatting or gate exclusion was added.

(己, p=1.0) The merge parents are optimization `5da9294065c58e13e639afb9acd7a7222272395f`
and composed baseline `a50e51243672a6d8842f8f2fc233beb6db645a2f`.
Relative to the optimization parent, incoming source/test changes are exactly
`src/infra/dev/window/loop.clj`, `src/law/narrowing.clj` and
`test/infra/dev/focus_cadence_test.clj`. `incoming-source.diff` preserves that
patch. `raw-whitespace-audit.json` identifies any whitespace in captured patch
context; those raw bytes are preserved rather than rewritten.

(己, p=1.0) The Kepler solver remains SHA256
`61009ec34e7e2317059a1f5a824bd58c94db4793206a4ad096a5b3a860ea3c8d`;
the numerical fixture remains
`16e02fe904a97572756d6613483a707da76cdc9b297c81973fe2ef1d2dd56aa4`.
Both Kepler test files also retain their qualified hashes, recorded in
`source.json`. The frozen baseline and rejected candidate evidence are unchanged.
The known negative-time defect remains separately owned and unrepaired here.

(己, p=1.0) This pass does not revise the prior allocation-only finding:
measured allocation reductions were 10.65% elliptic, 10.59–12.73%
near-parabolic and 14.59–18.34% hyperbolic across the two recorded comparisons.
All wall-time intervals overlapped and CPU/compact timings varied. There is no
latency, native FPS, compact throughput or 16.6 ms budget improvement claim.
The parent performance card remains in progress. Root owns publication.

(己, p=1.0) `CLOSED-FILES.txt` and `SHA256SUMS` close this separate bundle;
`STAGING.txt` lists only this bundle plus the authorized canonical card/ledger and
append-only receipt/reflection paths. Untracked raw baseline CPU duplicates are
excluded. Prior evidence manifests remain untouched.
