# Guarded persistent snapshot smoke — closed result

The fresh ordinary native game completed all twelve requested operations through the frozen [PR45 preparation](https://github.com/octave-commons/Truth/pull/45). The controller passed and the supervisor closed by an owned operator stop. Production is unchanged from `b395c4049718f7ce015ddf25fc0a192d97821373`; this result qualifies the diagnostic control path before physical flight.

## Observed sequence

The [controller result](completion-client-runs/attempt-01/result.json) records two additional inspections before input, R into Manual, balanced Spark drawer open/close, Tab lock, +100X/−100X/+100Y/−100Y, and Tab free/lock. All twelve request results succeeded. Yaw changed −90→−89→−90 degrees and pitch −20→−21→−20, using the actual 0.01-degree/pixel setting and unchanged 0.02-degree tolerance.

Sixteen input observations include four early unmatched reads, each followed by a matching read. The controller waited; it did not replay the gestures. There was no thrust or target selection.

## Snapshot and timing evidence

The [independent audit](attempt-01-audit.json) reparses all 270 preserved nREPL messages across 29 sequential requests: matching IDs, exactly one value and terminal `done`, no remote error/stderr, and exact reconstructed stdout and metadata byte counts. Total encoded response size was 152,369 bytes; largest request 11,124 bytes. All remain within the frozen limits.

One worker process, PID2250969, served the entire sequence. The pinned source has one connection scope around its request loop, and all evaluations preserve the same game/world/window identity. The 29 per-evaluation nREPL session IDs are not connection IDs. No separate socket capture was performed.

The native run lasted 35.198 seconds, from 06:51:24.904 to 06:52:00.102 UTC on 2026-10-07; the controller lasted 35.871 seconds. Supervisor-observed snapshot roundtrip median was 111.67 ms (61.36–316.21 ms). These describe this young-world functional run, not an isolated performance benchmark, mature-world latency guarantee, or native FPS result.

## Ownership and closure

The private runtime used DISPLAY `:1`, loopback port7898, game PID2248633, world identity1380350231 and GLFW window125476463786560. All24 unique child processes and14 controller clients were reaped. The snapshot worker exited0; game143 was intentional owned termination. Root separately checked that supervisor2248619, Xvfb2248627, game2248633 and worker2250969 were absent. The [closure](closure.json) records no cleanup errors, unreaped children, mouse uncertainty or budget overrun. This is not an Escape lifecycle test.

All186 production hashes, five executable/preparation pins and nine preparation-manifest entries remain unchanged. Canonical Rheos appended one result comment; card body/status/estimate and historical comment/event prefixes remain intact. The retained primary received no input or mutation.

## Limits and next measurement

This smoke exercises successful request handling. It does not inject timeout/error/cap failures, qualify a 25-minute lease, inspect mature planets, or demonstrate physical approach, binding, commitment, embodiment, Gate activation or visual quality. It produced no screenshot/video. Earlier failed runs remain unchanged in PR42/43.

The next measurement is a separately scoped and frozen ordinary physical approach using acknowledged small mouse steps and bounded flight holds. The closed run is never resumed. [CLOSED-FILES.txt](CLOSED-FILES.txt) and [CLOSED-SHA256SUMS](CLOSED-SHA256SUMS) identify this exact publication snapshot, including the then-current append-only ledgers. The raw stop-client stdout preserves its original additional blank line at EOF (line58); all other changed paths pass the whitespace check.
