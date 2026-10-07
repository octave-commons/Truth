# Sculpt input queue cost qualification

The matched baseline and candidate completed in separate fresh JVMs with the same frozen adapter, EDN fixture, four real queue workloads and default Criterium quick settings. Every world/config/result output in `checks.edn` is byte-identical between runs. The baseline used the RED parent host adapters; the candidate used the qualified GREEN host adapters. This intentionally aligned fixture compares work with identical outputs; the focused tests separately establish the repaired stale-aim semantics.

Both executions finished within the unchanged 170-second work / 180-second total limits, with zero cleanup errors, no unknown process membership, no changed source/preparation pins, and all observed child identities reaped/absent. Baseline `baseline-02`:57.637810s, PGID1846490/start6228964; candidate `candidate-01`:58.335865s, PGID1866501/start6247786. Root independently checked all14 artifact hashes per run and process absence. This closes the single owned JVM lane.

|Workload|Median thread CPU, µs/invocation, baseline → candidate|Criterium mean, µs/invocation, baseline → candidate|Allocated bytes/invocation, baseline → candidate|
|---|---:|---:|---:|
|empty|0.025 → 0.047|0.040 → 0.061|72.112 → 136.112|
|legacy-16|5.628 → 4.466|5.634 → 4.816|6792.112 → 6856.112|
|sculpt-1|4.481 → 10.048|7.938 → 15.492|10344.112 → 23784.112|
|sculpt-8|39.381 → 91.242|33.649 → 119.103|82400.112 → 189528.112|

The candidate adds64 bytes to the empty/legacy drain paths. One contextual Sculpt adds13,440 bytes, and eight add107,128 bytes, including the new intent/context/validation path. Median CPU increases for the on-demand Sculpt cases (about2.24× for one and2.32× for eight); the eight-action Criterium means are about3.54×. These increases remain visible. There was no predeclared numeric pass threshold; this record establishes measured cost, not a universal performance pass or speedup. The coordinator accepts publication for review because this is one serial user-action queue, not a per-entity loop; eight actions measured under0.1ms median CPU on this host, while output correctness and fail-closed intent validation are retained.

Criterium outlier variance is high in most cases, and20 thread samples show broad timing ranges. Endpoint load censuses do not establish continuously isolated host load. The legacy timing reduction is not a claimed optimization. [metrics.json](metrics.json) retains intervals, medians, ranges, quartiles and median absolute deviations; the packed raw records retain every sample. The measurements include identical enqueue/drain/assertion/output-sink overhead and exclude fixture/queue initialization. They do not measure simulation ticks, render work, FPS, a Gate, native flight or gameplay.

## Failure preservation

The original `action-aim-host-cost/runs/baseline-01` completed its four workloads but failed in supervisor cleanup when unrelated `/proc/1/exe` was unreadable. It lacked durable child identity and a final execution record; it remains FAILED and was never used as the comparison baseline. The sibling supervisor repair qualifies optional metadata handling, durable ownership and fail-closed cleanup with12 offline fake-process tests. All original preparation, failed output, root audit, repair RED, passing offline qualification and both actual runs are included losslessly in the accompanying archive. Local originals remain untouched.

## Publication and provenance

The JSONL archive stores exact UTF-8 text with original path, byte length and SHA256; the map supplies line/offset lookup. Decoding each text string and re-encoding UTF-8 recovers the original bytes, including empty files. The packaging script verifies an exact round trip for every input before freezing. This is a publication representation, not a new board or runtime authority. Current source correctness remains the prior focused56tests/544assertions and full967tests/17006assertions plus all6strict stages, with source/test pins unchanged after these measurements. No additional production changes or test reruns were made for this evidence-only publication.
