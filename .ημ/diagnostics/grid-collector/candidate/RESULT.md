# Grid collector candidate: allocation benefit, mixed downstream timing

This report records one baseline → candidate → return-baseline sequence
(**A → B → A₂**) under the existing `perf-tick-residual-gap-to-60fps` scope.
The candidate's serial query batches allocate substantially less than both
baseline observations. Neighbor-rebuild and full-tick timing remains mixed.
The coordinator's retention decision is limited to this measured allocation
benefit. The full suite and all six strict stages passed; downstream latency
nonregression, native FPS and the 16.6 ms tick budget are not established.

## Source and measurement identity

The original baseline was committed as
`8f953d2bcfab2fa2b3fef8a390157a4629eab9e0`, with production inherited from
`b395c4049718f7ce015ddf25fc0a192d97821373`. The candidate and return-baseline
wrappers record that same HEAD, so the **source hashes**, not HEAD alone,
distinguish the measured implementations.

| Input | SHA-256 |
| --- | --- |
| A / A₂ `src/domain/spatial/index.clj` | `280941b79defe025dbdd7c1478940b956c1e0dc9bd270fa4c66f5170f1a94701` |
| B collector source, also saved as [source-index.clj](source-index.clj) | `3685ddefe8ccedd3af3716678684a041ec50e1a08b1cbc89d8025ebfa3b8437a` |
| Original A evidence adapter | `0baaa761a1d343802b8a320870d9860ec53a24fda99956b0c43bbb1a8554be74` |
| B / A₂ [evidence adapter](../evidence/grid.clj) | `51a4fc1ac47a53d4b488df72ca7713b1cec010752f1070b971ce508a1db299de` |
| Unchanged [frozen observations](../fixtures.edn) | `c9eb7f817e658f956a0418fb7ee325dd04bbdf1ccd9806e84ab795c154ed9a74` |
| Unchanged public-query contract tests | `ec50c5b77126e4f2f9d7e4658cc9fce833338f2c7c5837ee58a1c3764c0420d9` |

The only production change is private `collect-grid-range`: replace `vec` over
the nested `for` with the same x/y/z/item `doseq` traversal, accumulating into a
transient vector held in a local volatile, then returning its persistent vector.
The cell bounds, predicate-before-radius test and distance arithmetic are
unchanged. No consumer, factory, force law, neighbor budget or tick ordering is
changed. The adapter extension permits exactly the candidate collector hash and
checks the original oracle's baseline hash; workload construction, observation
comparison and measurement closures are unchanged.

The [return-run restoration record](../reverse-baseline/source-restored.json)
confirms the original candidate bytes were restored after A₂. It does not claim
that the candidate was committed or accepted at measurement time.

## All six A → B → A₂ results

Each cell gives **mean [returned mean interval] in milliseconds**. Query cases
are per **32-query batch**, neighbor cases per rebuild, and Phase 0 cases per
tick. Links lead to every raw case, including samples, options, warmup and
execution counts. These are three separate fresh JVM runs, not paired samples
from one statistical experiment.

| Case | A: before | B: candidate | A₂: return before |
| --- | ---: | ---: | ---: |
| Uniform 1000, 32 queries | [1.6366 [1.3510, 2.0357]](../before/grid-uniform-1000.edn) | [0.9926 [0.9611, 1.0363]](../after/grid-uniform-1000.edn) | [1.5794 [1.3689, 2.0286]](../return-before/grid-uniform-1000.edn) |
| Clustered 1000, 32 queries | [2.0461 [2.0145, 2.0974]](../before/grid-clustered-1000.edn) | [1.6547 [1.4390, 1.9429]](../after/grid-clustered-1000.edn) | [1.9701 [1.8739, 2.0652]](../return-before/grid-clustered-1000.edn) |
| Neighbor rebuild 500 | [5.9069 [4.5398, 7.2582]](../before/neighbor-rebuild-500.edn) | [4.8741 [3.5394, 6.1874]](../after/neighbor-rebuild-500.edn) | [3.6873 [3.1757, 4.4393]](../return-before/neighbor-rebuild-500.edn) |
| Neighbor rebuild 1000 | [12.2580 [8.8600, 17.2889]](../before/neighbor-rebuild-1000.edn) | [15.1519 [12.0056, 18.1758]](../after/neighbor-rebuild-1000.edn) | [11.1744 [7.8045, 16.2485]](../return-before/neighbor-rebuild-1000.edn) |
| Registered Phase 0 tick 500 | [25.9058 [21.2438, 32.3720]](../before/phase0-500.edn) | [28.1052 [23.0279, 35.5730]](../after/phase0-500.edn) | [23.9512 [18.9256, 31.4878]](../return-before/phase0-500.edn) |
| Registered Phase 0 tick 1000 | [60.5016 [46.1503, 80.1989]](../before/phase0-1000.edn) | [44.8048 [37.8571, 59.2546]](../after/phase0-1000.edn) | [58.3817 [41.5506, 81.7181]](../return-before/phase0-1000.edn) |

The uniform query's candidate interval lies below both baseline intervals. The
clustered query's candidate mean is lower than both baselines, but its interval
overlaps A₂. These observations apply to the recorded query batches; they do not
establish a general latency improvement across workloads.

All four downstream candidate intervals overlap both baseline intervals.
Neighbor 500 improves against A but worsens against A₂. Neighbor 1000 and tick
500 have higher candidate means than either baseline: neighbor 1000 is **23.6%
and 35.6% higher**, and tick 500 is **8.5% and 17.3% higher**, against A and A₂
respectively. Tick 1000 has a lower candidate mean than either baseline.
These adverse downstream means remain unresolved. Overlap does not prove equivalence or rule
out a regression. The return measurement, authorized by the preserved
[mixed-result decision](REVERSE-DECISION.md), does not erase the adverse means
or establish a causal explanation for them. No further repeat-until-positive
measurement is implied.

## Serial allocation evidence

These are current-thread allocated **bytes per 32-query batch**, from 100
post-warmup repetitions using Criterium's consumption helper. The figures include
that common consumption overhead. They are not all-thread allocation or retained
heap measurements.

| Query distribution | A bytes/batch | B bytes/batch | A₂ bytes/batch | B reduction versus A / A₂ |
| --- | ---: | ---: | ---: | ---: |
| Uniform | 3,699,009.84 | 1,072,705.84 | 3,694,913.84 | 71.00% / 70.97% |
| Clustered | 4,905,081.12 | 1,899,449.12 | 4,900,985.12 | 61.28% / 61.24% |

The allocation reduction is present against both baseline observations; B was
measured once. Caller-thread CPU measurements are also preserved in the two raw
query files. Neither caller-thread CPU nor allocation is extrapolated to the
parallel neighbor/tick paths, for which the adapter deliberately reports no such
counter. Lower query allocation alone does not settle downstream acceptability.

## What the equivalence evidence establishes

Candidate [preflight](preflight/focused.result.json) completed with **44 tests,
775 assertions, zero failures or errors**, as recorded in
[focused.stdout](preflight/focused.stdout). The unchanged tests cover ordinary
`build-grid` vector buckets, hand-calculated cell and bucket order, original item
identity, predicate visitation and filtering order, predicate-before-distance,
inclusive cutoff, zero-radius self, empty/disjoint ranges and the world-facing
query route. The result remains eager and vector-valued. They do not prove the
same internal lazy-sequence/chunk realization behavior for hand-crafted lazy or
side-effecting bucket collections; this report makes no such compatibility claim.

Before each timed run, the adapter reconstructed the same seed-42 factories and
required exact equality with the frozen observation record. A, B and A₂ record
successful matches in their respective [before](../before/inputs.edn),
[after](../after/inputs.edn) and [return-before](../return-before/inputs.edn)
inputs. That checks ordered public query IDs, initial input fingerprints, actual
fresh neighbor write sets and four successive component-state fingerprints for
each Phase 0 size. It does not regenerate an oracle from the candidate.

Floating values in fingerprints retain raw IEEE-754 bits. Map/set entries are
ordered for hashing; vector and list content retain separate tags. The observer's
random UUID is omitted only from the comparison projection; absent/nil observer
columns remain distinct, and measured worlds are not modified. This is content
and numerical evidence, **not complete representation equivalence**: record
classes are treated as maps and collection metadata is not represented. Handler
callables and ledger contents are not fingerprinted as complete runtime state;
the initial record separately records handler keys and ledger event count.
Component windows do not prove all world-level fields, arbitrary long-run
trajectories, or mature natural-world behavior. Item identity is directly tested
for the contract fixture, not encoded by a SHA-256 content fingerprint.

## Run ownership and qualifications

| Run | JVM PID | UTC timing window, 2026-10-07 | Completion |
| --- | ---: | --- | --- |
| A | 1500297 | 04:38:46.833–04:40:34.469 | [Exit 0, reaped, inputs stable](../baseline/bench-run/result.json) |
| B | 1561384 | 04:50:17.978–04:52:07.645 | [Exit 0, reaped, inputs stable](bench-run/result.json) |
| A₂ | 1585389 | 04:54:00.056–04:56:15.579 | [Exit 0, reaped, inputs stable](../reverse-baseline/bench-run/result.json) |

All used explicit 256 MiB / 2 GiB heaps, the same six closures/settings and the
same frozen observations. Each wrapper verified root-owned native PID 131581,
start tick 125503, boot identity and working directory before suspending it.
It stayed stopped with zero sampled CPU delta during each timing window, then
was resumed in `finally`. Other host activity was not eliminated; load samples
are retained. The existing Phase 0 group's setup/profiles are separate from the
six Criterium cases. Each timed case has six samples; in particular the 1000-body
tick uses two executions per sample in each run, so its measured window is short
and its intervals broad. No isolated whole-host performance claim is made.

The candidate's [post-resume native read](bench-run/native-after.stdout) and
the return-baseline's [post-resume read](../reverse-baseline/bench-run/native-after.stdout)
retain world atom identity 919497450 and window 137768861339584, both live
workers, nil service/UI errors, production tick/projection functions and a
non-fixture nebula. Their before/after ticks advance 200736→200763 and
202364→202393 respectively. These are suspension/resumption checks, not a native
test of the candidate: no live code reload, world replacement or control input
was performed. The candidate remains headless measurement source.

**Full qualification passed.** The ordinary suite completed **953 tests and
16,800 assertions, zero failures and zero errors**; its JVM PID 1610035 ran
04:57:18.068–04:58:00.793 UTC and was reaped with exit 0. See the
[complete suite output](gates/full-suite.stdout) and
[completion record](gates/full-suite.result.json). All six strict stages then
passed: clj-kondo had zero errors/warnings, structural smells had zero HARD findings, Splint
had zero findings, unused analysis found none, duplication was 1.52% of lines,
and formatting was clean. Strict PID 1613934 ran 04:58:00.793–04:59:07.735 UTC
and was reaped with exit 0; see [strict output](gates/strict.stdout) and
[completion record](gates/strict.result.json). The
[source/command manifest](gates/inputs.json) and both completion records bind
the qualification to unchanged pinned inputs and HEAD. These durations with the
resumed native service are correctness/static qualification evidence, not
additional performance measurements.

The [append-only baseline erratum](../BASELINE-ERRATUM.md) corrects the frozen
baseline report's “isolated profile” attribution: the prior 86/1858 native JFR
sample observation was collected under active host load. It motivates this
experiment but predicts neither its benefit nor native FPS. Historical baseline
bytes and measurements remain unchanged.

This sequence does not demonstrate 16.6 ms budget attainment, improved native
FPS, rendering latency or playable progression. Candidate tick 1000 remains
44.80 ms on average, about 2.70 times that budget. After qualification and an
independent assessment, the coordinator retained the candidate for its measured
allocation benefit only. This bounded decision does not turn the unresolved
neighbor/tick timing concerns into a nonregression result or complete the
broader performance-budget card.
