# Grid collector baseline preparation

(己, p=1.0) This is baseline-only authoring at `b395c4049718f7ce015ddf25fc0a192d97821373`
under existing InProgress3 `perf-tick-residual-gap-to-60fps`. Root owns canonical
scope admission, native quiescing, every JVM/test/benchmark run, the meaningful
baseline checkpoint, and any later production permission. No baseline result,
GREEN implementation, native FPS claim, or completed performance gate exists yet.
The only proposed later production target is private
`domain.spatial.index/collect-grid-range`. No implementation is in this branch.

## Characterization contract

`test/domain/spatial/grid_query_contract_test.clj` calls only public queries and
grid construction. It pins a hand-calculated cell/bucket order independent of ID
and insertion order, original item instances, predicate visitation/filter order,
predicate application even for out-of-sphere items in visited cells, exact cutoff,
zero radius/self, empty and disjoint ranges, and the world-facing query route.
These tests are expected to pass on the baseline. A failing characterization is
a blocker to understand; it is not evidence that an optimization is needed.
Existing spatial/cache/formation tests remain in place.

RED for this no-behavior-change slice means the actual measured cost and open
16.6 ms budget, not deliberately wrong physics or an assertion about private
implementation call counts. After any candidate, both exact compact numerical
outputs and public tests must still agree before benefit is assessed.

## Six measured cases, unchanged production inputs

The adapter is `evidence/grid.clj`; the source-controlled `settings` map is the
complete proposed benchmark settings record. It reuses the repository's private
`gates-of-truth.bench/quick-bench` adapter and its default Criterium options.
It preserves samples, option values, warmup/execution counts, intervals, and the
known OutlierCount record's type and all fields using the already-used encoding.

1. `grid-uniform-1000`: existing spatial benchmark factory, seed42, 1000 items,
   extent1e14, grid cells1e13; query the first16 item positions at radii1e13/4e13.
2. `grid-clustered-1000`: same batch and dimensions, existing clustered factory.
3. `neighbor-rebuild-500`: actual `neighbor-cache-system` on the existing medium
   Phase0 factory output, advanced once, with real spatial index/query-cache/SoA.
4. `neighbor-rebuild-1000`: same production path on the existing large factory.
5. `phase0-500`: the exact registered `phase0/run` tick closure.
6. `phase0-1000`: the exact registered `phase0/run` tick closure.

Each public grid batch has32 queries; timing units are per batch, not per query.
Only these serial batches receive current-thread CPU/allocation observations
(100 post-warmup repetitions through Criterium's consumption helper). Neighbor
rebuilds and full ticks use parallel workers, so caller-only allocation would be
misleading and is not reported for them. Factories and index setup run outside
the measured closures. The grid query returns full item vectors; it is not
replaced by a count-only operation. No alternate spatial query is implemented.

The existing Phase0 group still performs its normal setup and profiles. All
unselected benchmark labels are recorded as skipped, not silently called covered.
Its two world factories are temporarily rebound to their own prebuilt outputs;
`with-redefs-fn` restores them even on failure. This occurs only in a separate
benchmark JVM, never the native game. The original closure still calls the
production `genesis/tick-world`; no tick copy or new phase is introduced.

## Compact frozen evidence

The `capture` action refuses any collector source other than SHA256
`280941b79defe025dbdd7c1478940b956c1e0dc9bd270fa4c66f5170f1a94701`.
It writes a new compact EDN fixture with settings, relevant source hashes, item
fingerprints, actual ordered query IDs, initial component fingerprints/parameters,
actual neighbor write-set fingerprints, and four successive complete component
fingerprints per Phase0 size. It does not freeze a giant whole-world dump.

Before timing, `baseline` reconstructs the same factory inputs and requires exact
fixture/settings/source/observations equality. Floating-point fingerprints use
raw IEEE-754 bits, map/set entries are deterministically ordered, and vectors and
lists retain distinct tags. Only the observer's randomly generated `:id` is
removed from the comparison projection, never from a running world. The initial
parameter record omits function-bearing handler maps and the event ledger, while
recording handler keys and event count separately. Component-window fingerprints
cover components only, not complete ledger/host-state equivalence. Unknown
fingerprint value types fail visibly. A future mismatch writes actual compact
observations and stops; do not recapture the oracle to bless changed behavior.

The factories deliberately create benchmark scenes. They are neither evidence
of mature natural formation nor gameplay acceptance. No native service is read,
paused, resumed, reset, or mutated by this adapter. Preserve the mature JFR's
active-load limits; its86/1858 collector samples justify investigation, not an
assumed speedup. Preserve any noisy or negative measured outcome.

## Root-run commands after review and exclusive-resource release

Run from the isolated worktree root with explicit 2 GiB maximum heap. The inline
alias only adds the diagnostic namespace directory and main entry point; existing
`:test` or `:bench` dependencies/paths supply the official runners and Criterium.

```sh
clojure -J-Xms256m -J-Xmx2g -M:test -n domain.spatial.grid-query-contract-test -n domain.spatial.index-test -n domain.physics.cache-test

clojure -J-Xms256m -J-Xmx2g -Sdeps '{:aliases {:grid-evidence {:extra-paths [".ημ/diagnostics/grid-collector"] :main-opts ["-m" "evidence.grid"]}}}' -M:bench:grid-evidence capture .ημ/diagnostics/grid-collector/fixtures.edn

clojure -J-Xms256m -J-Xmx2g -Sdeps '{:aliases {:grid-evidence {:extra-paths [".ημ/diagnostics/grid-collector"] :main-opts ["-m" "evidence.grid"]}}}' -M:bench:grid-evidence baseline .ημ/diagnostics/grid-collector/fixtures.edn .ημ/diagnostics/grid-collector/before
```

Root's external runner must pin the exact reviewed HEAD plus source/test/adapter
hashes, capture command/environment allowlist/exit/timestamps and host/process
load, assert the verified owned native process stays paused, and reap each JVM.
No adapter function signals any process. Fresh candidate measurement must use
identical fixtures/settings and a reviewed narrowly widened source guard; this
baseline-only adapter intentionally rejects modified source today. Run a matched
reverse order only if needed to resolve a material comparison, not until a
preferred result appears. No fabricated latency/budget threshold is introduced.

Preparation verification and run results remain separate. Static checks cannot
claim test execution, Criterium success, source-equivalence runtime success, or
natural-game acceptance. Root will commit the observed baseline before authorizing
any production change.
