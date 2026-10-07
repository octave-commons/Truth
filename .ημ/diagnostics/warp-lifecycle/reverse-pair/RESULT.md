# Warp lifecycle reverse-order comparison

Candidate `3d912230bc8743463d0c59b75ce312e5688a0dc0` ran AFTER first in its
worktree, followed by clean baseline
`14bd31890acdab3b9983c6780295b42f4a065c55` running BEFORE in the separate
`truth-warp-cost-baseline` worktree. Both fresh JVMs used the exact existing
five-case adapter, frozen fixtures, Criterium defaults and 256 MiB / 2 GiB heap
arguments. PID 277752 and PID 288358 each exited 0 and were reaped; the pair
closed at 2026-10-07 01:05:52 UTC. No extra case or benchmark followed.

| Case | Reverse baseline BEFORE mean [interval] | Reverse candidate AFTER mean [interval] |
| --- | ---: | ---: |
| Registered Phase 0, 500 gas particles | 27.6287 [22.8594–32.3494] ms | 27.1960 [23.0901–34.6201] ms |
| No well / no prior cells | 459.704 [338.524–652.935] ns | 563.837 [477.305–674.501] ns |
| 500 active recipients | 352.418 [298.961–493.093] µs | 295.119 [283.720–304.710] µs |
| Expired well / 500 prior cells | 0.525014 [0.389261–0.764305] µs | 273.751 [233.074–365.614] µs |
| 250 live / 500 prior recipients | 374.709 [280.884–562.426] µs | 361.394 [351.970–370.000] µs |

Intervals are the returned Criterium mean intervals, not console quantiles.
`comparison.json` includes both entire pairs without pooling or selecting only
favorable runs. The original pair remains unchanged: broad means were
19.1907→34.8343 ms (+81.5%) with separated intervals. In reverse order they are
27.6287→27.1960 ms (−1.57%) with overlapping intervals. The large initial broad
penalty **did not reproduce in this reverse pair**. The active and partial
mean directions also reverse, with overlapping intervals. No speedup,
performance-neutrality proof, universal regression absence or rendered FPS
claim follows from these short, variable-host measurements.

The reproducible behavioral difference is the required clearing: the repaired
snapshot removes all 500 expired contributions and exactly 250 departed ones,
retaining 250 live partial recipients, agency 85 and other physical columns.
Baseline continues to retain stale entries. Candidate expired clearing means
are 0.274–0.297 ms across the two runs for 500 cells; the buggy baseline's
near-empty operation is not a correctness target. Candidate partial means are
0.361–0.385 ms. These are finite workload observations, not a general world-size
bound or a claim that the overall tick meets its frame budget.

## Isolation, source and remaining uncertainty

The reviewed runner SHA is
`fb5a8b333eaa22e4b63baee00dcc39a7bdf5c81b3a3ba3bb8f44280d552fd452`.
It pins both commits and 12 source/input hashes in each checkout, requires clean
tracked source, and permits only the approved candidate's three-indent test
correction with exact reverse-byte verification. The production adapter
`deba8114…` and fixture `2c275009…` are unchanged. Each process's outcome is saved
before post-run guards. Both source guards and native stopped-identity guards
passed before and after both runs; source and frozen input hashes did not drift.

Root verified and stopped the same owned native PID 131581/start125503/boot
`b04f7fb6-ea65-4b47-8866-73e24a6887f6` across BOTH runs, with its observer restored.
The runner only inspected the process identity/state; it never signalled or
changed a service. Root was notified immediately after the pair reaped to resume
that same process. Root resumed the verified same process at 01:09:56.920178 UTC
with SIGCONT, observing state S afterward; `native-resume-root.json` records
the unchanged PID/start/boot/cwd. Pause/resume provenance is separate from
performance data.

External five-second sampling was symmetric across the pair and did not alter
the benchmark adapter. During candidate/baseline sample intervals, maximum
runnable counts were 62/47 on the 22-logical-CPU host; aggregate busy fractions
were 38.1%/57.4% and iowait fractions 2.34%/1.83%. Sampled available memory minima
were about 13.0/11.3 GiB and swap used maxima about 8.1/8.5 GiB. The benchmark
processes each had zero sampled major-fault increment, with peak sampled RSS
about 1.0 GiB. These counters include setup and warmup across all five cases;
no per-case timestamps, GC trace or causal profile were added. Host load remains
a limitation, not a substitute explanation. Raw counters and bounded summaries
remain inspectable; lifetime `ps` CPU percentages are not used as pass criteria.

The appropriate conclusion is restored lifecycle correctness with measured
cleanup work; the original broad slowdown was not reproduced in reverse order,
while broad timing attribution remains unresolved. Both original and reverse results
must remain visible. Further implementation, canonical transition gate and
hosted review decisions belong to root; no board transition, native gameplay
acceptance or complete user-goal claim is made by this evidence.

Independent source review approved only the runner's stated guards and bounded
execution. Independent offline comparison review verified both pairs and the
limited non-reproduction claim; neither review is hosted approval.
