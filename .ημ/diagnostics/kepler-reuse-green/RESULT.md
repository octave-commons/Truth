# Qualified Kepler allocation reduction

(己, p=1.0) The final source reuses `z`, `c2` and `c3` within each Newton
iteration. The original residual function remains solely for bracket
evaluation; the unchanged Newton residual and conditional derivative are
evaluated over local terms. This removes repeated Stumpff work without a
cross-iteration cache, extra tuple, function dispatch or physics change.

(己, p=1.0) Final solver SHA256 is
`61009ec34e7e2317059a1f5a824bd58c94db4793206a4ad096a5b3a860ea3c8d`.
The qualified tree is baseline `a96dfd7`, this solver patch and two recorded
test indentation corrections. Initial guesses, brackets, arithmetic grouping,
convergence/caps, exceptions and final f/g remain unchanged. The pre-existing
negative-time bracket defect is deliberately preserved in this identity-only
scope.

## Accepted scope of the evidence

(己, p=1.0) The measured benefit is **reduced allocated bytes in the tested
public propagation batches**. Two source-pinned comparisons, including one
reverse-order pair, observed:

| Group | Observed allocation reduction | Final bytes per batch in both runs |
| --- | ---: | ---: |
| Elliptic, 7 inputs × 16 steps | 10.65% | 335153.84 |
| Near-parabolic, 3 × 16 | 10.59–12.73% | 149457.12 |
| Hyperbolic, 2 × 16 | 14.59–18.34% | 110865.12 |

(己, p=1.0) All before/after wall-time mean intervals overlap. CPU and compact
fold observations vary: the first attempt-2 hyperbolic CPU reading increased
59.05%, then the reverse pair decreased 18.73%; stationary compact means
changed from +25.20% to −26.13%, while moving compact means changed from
+1.98% to +18.31%. These results do not establish lower latency, faster compact
folds, native FPS gains or closure of the 16.6 ms initial-nebula tick budget.
No additional benchmark repetitions are proposed on the current evidence.

(己, p=1.0) Measurement commands reused the existing Criterium adapter,
settings and workload definitions. Owned native JVMs were verified paused
and had zero CPU delta within cost windows; other host load and short-loop
measurement limits remain explicit. The final reverse pair used a clean
detached baseline checkout, with all source/workload/fixture hashes verified
before and after. This evidence does not claim that the lost older native
service resumed; root owns separate lifecycle records for the new hardware
service.

## Numerical and static qualification

(己, p=1.0) The focused identity/physics checks passed 21 tests and 596
assertions. The immutable fixture covers 14 public cases in both time
directions, exact successful and declared failure outcomes, signed zero and
invalid-input behavior. Four real compact map/SoA scenarios preserve component
trajectories across 12 folds each and assert actual dominance-gate admission.
Independent analytic, energy, angular-momentum and reversal checks also pass.

(己, p=1.0) Final full qualification completed with stable source hashes:

- `clojure -M:test`: **940 tests / 16537 assertions / 0 failures / 0 errors**, exit 0.
- `bin/analyze --strict`: **all six stages pass**, exit 0; duplication is 1.53%, below 1.7%.
- Explicit JVM heap: `-Xms256m -Xmx2g`. All validation processes exited and were reaped.

(己, p=1.0) The earlier strict run failed only on two test indentation lines.
Those exact corrections and before/after hashes are retained; no blanket
formatting, suppression, oracle regeneration or test-form change occurred.
The final full strict run is distinct from that preserved failure.
Validation may overlap the native game, so its durations are not performance
measurements. `full-validation/source.json`, source patch, command records and
complete logs bind the qualification to exact bytes before the root commit.

## Evidence map and preservation

(己, p=1.0) The original committed baseline lives under
`.ημ/diagnostics/kepler-cost/`. Its historical manifests remain unchanged;
their original test-file hashes are recoverable at `a96dfd7`, before the two
formatting-only corrections. The fixture hash remains
`16e02fe904a97572756d6613483a707da76cdc9b297c81973fe2ef1d2dd56aa4`.

- `ATTEMPT1-RESULT.md` and `ATTEMPT1-SHA256SUMS`: rejected dispatch-based form,
  its exact source, allocation increases, costs and raw observations.
- `attempt2/RESULT.md`: the local-term implementation's first comparison,
  including the unfavorable CPU/compact observations.
- `attempt2/reverse-pair/RESULT.md`: the single resolving reverse comparison
  and qualified allocation-only conclusion.
- `full-validation/`: final full suite and strict gate on the final source.
- `CLOSED-FILES.txt`, `SHA256SUMS`, `STAGING.txt`: exact final inventory, hashes
  and root staging paths, excluding the untracked raw baseline CPU duplicates.

(己, p=1.0) Repeated raw CPU endpoint snapshots are stored as deterministic
gzip with original-byte SHA256 and byte-equality checks. No failed observation
is deleted or converted into a pass. The current parent performance card
remains in progress because its broader budget and native gameplay outcomes
remain open. Root owns commit, stacked publication and subsequent integration.

(己, p=1.0) `raw-whitespace-audit.json` identifies four trailing-space
diagnostics in captured diff context: two lines in the formatter's original
failure output and two in the full validation source patch. The single-space
blank context lines are preserved as patch/output bytes. The actual source
files pass their diff check and final formatting gate; this is not a blanket
whitespace-clean claim for raw artifacts. Root's six lifecycle JSON records
are included byte-for-byte, including the unsuccessful earlier service resume.
