# Closed attempt03: re-aim to cadence boundary

Observed evidence integrity passes. The intended physical-flight measurement failed before its first W request. Supervisor closure is `operator-stop`; cadence outcome is `failed` with the preserved heading assertion. These are different records, not a successful approach.

| Saved boundary (UTC) | Observation |
| --- | --- |
| 2026-10-07T11:55:09.166082+00:00 | Snapshot0177, tick5056, heading 0.09185855999° |
| 2026-10-07T11:55:09.471235+00:00 | Aim controller records that geometry |
| 2026-10-07T11:55:10.039920+00:00 | Inner aim-end CLI reaped |
| 2026-10-07T11:55:10.114256+00:00 | Outer re-aim process reaped |
| 2026-10-07T11:55:22.608426+00:00 | Cadence first inspect command starts |
| 2026-10-07T11:55:23.270826+00:00 | Snapshot0178, tick5105, derived heading 0.60317082805° |
| 2026-10-07T11:55:23.458684+00:00 | Cadence requests owned stop after assertion |
| 2026-10-07T11:55:25.058211+00:00 | All owned runtime children closed |

The camera stayed at yaw−103.78°, pitch26.3°. The selected target and Spark relative endpoints changed across49 simulation ticks. Range increased by 2.515214e+14m, and the relative endpoint chord was 3.668669e+14m. The receive-to-receive interval was 14.104744s. Of that, 12.494170s elapsed after the outer aim exited and before the cadence inspect began; the cadence inspect command itself took 0.842588s. This is a material operator handoff gap, not evidence that the fresh inspection alone consumed14s.

The measured endpoint heading-error increase averages 0.036251°/receive-second. Linear interpolation would use the initial0.40814° margin in about 11.26s, but neither the exact threshold-crossing time nor future angular rate was observed. A subsequent separately reviewed experiment can remove the handoff gap by completing released baseline reads before its one aim, then immediately applying the unchanged fresh-heading guard. This run does not justify widening that guard or claiming a successful future pulse.

## Integrity and closure


- 178 successful reads;5041 decoded nREPL messages;4,479,322 raw UTF-8 bytes. Every per-request span is contiguous and exact; all178 request/result/consume records agree, all terminal statuses are done, and the persistent connection closes after178. Each snapshot carries the same world1308072265/window134192227468400.

- 91 successful runner operations:48inspect,29look,7click,2tap,2aim-start,2aim-end,1frame; no hold request. All published thrust vectors are nil. Global commitment count is zero in all178 frames.

- 61 spawned children have matching reap receipts;37 outer clients and 58 aim/cadence CLI children have exit0 reap receipts. The cadence controller itself failed by assertion, reported by root tool86609 rc1. Cleanup flags, uncertain inputs and unreaped lists are empty. All four exact owned PID paths were independently absent at audit under the same boot; 157 distinct recorded child/outer PID paths were also absent.

- Current186 production-file bytes and six preparation pins match the run's preserved hashes. This is offline hash verification, not new native execution or Git-based ancestry proof.

- The automatic formation poller made15 reads, with start intervals 30.000235–30.089173s. It observed no ready candidate at tick3989 and four at tick4096, then stopped without selection. No exact formation instant is inferred. First actual supervisor admission of root-selected1010 was tick4545, 133.493570s after the last poll's elapsed reading.

## Coverage limits

Selected1010 remained stored+ready and fresh-handoff-false in the two cited snapshots. Two successful finite aims establish only single-observation orientation. Zero holds means no measured physical approach, binding, commitment, sculpt, embodiment or Gate. Sparse snapshots and host readbacks are not consumer-fold or native-FPS proof. Complete input hashes and per-read raw byte spans are in [the JSON audit](attempt-03-audit.json); the original journals and all client evidence remain unchanged.
