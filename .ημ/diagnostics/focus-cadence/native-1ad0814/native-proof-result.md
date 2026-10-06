# Native focus-cadence result

Derived independently from the closed readbacks on 2026-10-06. The complete
numeric summary and SHA256 identities of the inputs are in
[`native-proof-summary.edn`](native-proof-summary.edn). Raw logs are unchanged.
This analysis made no live calls or changes to source, world, camera or config.

## Observed result and source

The actual native simulation consumed **1,772 aligned manual-focus inputs**,
with **zero prepared-object identity misses, alignment failures, or probe
errors**. There were **1,460 consecutive advancing input pairs with unchanged
completed-render-frame count**, and both inputs were aligned in every such
pair. This establishes the scoped repair: manual attention reaches the
pre-fold consumer snapshot independently of completed render frames.

Source is `1ad081475364cc3b75db76142204cbf54b5bf60c`, with the loop loaded by
absolute path and SHA256
`f9e860f7398141d0c0d0cf4bd0bd11dba7141f7edd4b4916f8d0051efc62ea0b`.
The source provenance record separately names the published evidence head
`b402939931575e377ae4409d9cd4061dff11df06`. The native world atom remained
**1675959387** through the diagnostic restart and observation. Installation
readback was at tick **24390**; restoration readback was at tick **26162**.
See [source provenance](source-before-reload.json),
[installation](native-proof-install.log), and
[restoration with closed counters](native-proof-restored.log).

| Closed cumulative observation | Result |
| --- | ---: |
| Focus returns on the owned sim thread | 1,772 |
| Tick inputs started / prepared-object matches / alignment passes | 1,772 each |
| Identity misses / alignment failures / non-applicable inputs | 0 each |
| Tick calls completed / original tick failures | 1,771 / 0 |
| Advancing aligned pairs between completed render frames | 1,460 |
| Original render-frame calls completed | 311 |
| Diagnostic interval | 430.794 seconds |
| Probe errors / evicted probe-error rows | 0 / 0 |

The probe compares the exact world object returned by the normal focus law
with the object received by the normal tick function. Its offset is the actual
argument passed to that focus law. It does not run another focus update inside
the tick observer. Both originals receive their normal arguments exactly once.
The observed interval includes diagnostic and capture overhead; it is not an
isolated throughput benchmark.

## Ordinary controls and actual Fine thrust

[Native input receipts](native-inputs.jsonl) record successful normal window
events: held Right and release, Left, comma, period, Spark-menu clicks, and held
D and release. These are host command receipts, not exact sim-event timestamps.
The following independent readbacks establish their effects:

| Readback | Tick | Observed state |
| --- | ---: | --- |
| [After Right](arrow-right-readback.log) | 24781 | Offset `[1.495978707e10, 0, 0]` m (0.1 AU), radius `5e15` m, intensity `0.5`; 391 alignment passes and no failures at this point |
| [Left then narrow](center-narrow-readback.log) | 24866 | Offset returned to zero; radius `2.5e15` m and intensity `1.0` |
| [Widen and open Spark](after-menu-request.log) | 25170 | Manual mode and zero offset; radius restored to `5e15` m and intensity `0.5`; 780 alignment passes and no failures |
| [Fine confirmed](fine-confirmed.log) | 25842 | Effective displacement `1e7`, retention `0.97` |
| [After Fine flight](fine-flight-readback.log) | 25878 | Displacement remains `1e7`; thrust is nil after release |

The Fine-flight snapshot retains **128 completed input/output rows**, input
ticks **25750–25877**. All have matching prepared-world identity and aligned
focus on the actual sim thread. Within that ring, **11 consecutive inputs at
ticks 25849–25859** carry non-nil thrust
`[1.0, 6.123233995736766e-17, 0.0]`. Five share completed-render-frame count
257 and six share count 258. The adjacent retained input at tick **25848** has
nil thrust; tick **25860** again has nil thrust. All these inputs remain aligned.
The D command receipts are approximately 3.030 seconds apart; the sim rows
establish actual applied input separately from that host timing.

This proves that ordinary Fine selection and held/released movement reached
the real input/tick path while attention stayed aligned. It does not infer a
travel distance from changing world coordinates or claim a successful target
approach. The earlier `:thrust-scale nil` field in
[fine-menu-readback.log](fine-menu-readback.log) is not the effective
displacement setting; the positive Fine evidence is the later explicit
`1e7` displacement readback.

## Output lag remains explicit

In all 128 retained Fine-flight rows and all 128 rows in the final ring, output
focus equals input focus, while the output physical position changes. The final
ring covers input ticks **26034–26161** (calls **1644–1771**). Every one of its
128 output positions differs from its output focus. That is the documented
pre-fold contract: integration and frame recentering occur after attention
preparation.

For the held-D rows, Euclidean separation between output position and output
focus is approximately **3.73604e11–3.73757e11 m**. For the final ring it is
**3.70896e11–3.72199e11 m**. These are coordinate separations computed directly
from each row's output vectors. They include physical advancement and COM
recentering; they are **not travelled distance, thrust displacement, or distance
to a planet**. No post-fold focus prediction or queued-action-anchor repair is
claimed.

## GL, bounded storage, restoration and media

The owner-thread probe retained **11 GL samples**, at completed frame counts
`1, 30, 60, 90, 120, 150, 180, 210, 240, 270, 300`. Every raw sample is `[0]`.
There are zero nonzero codes, truncated drains, evicted GL rows or probe errors.
The recorded owner is `gates-of-truth-dev-window`, native GLFW handle
**132406392702304**. Error reads consume GL flags; this is sampled evidence,
not continuous coverage of every draw.

The final sim ring retains 128 rows and reports **1,643 earlier rows evicted**:
`128 + 1643 = 1771` completed observations. Cumulative input/alignment counters
remain intact beyond the ring. **One original tick was still in flight when
recording closed**: `1772 − 1771 − 0 = 1`. Its input alignment is counted, but
no completion or output is claimed for that call. No prepared return was left
unconsumed at closure. The earlier Fine-flight snapshot independently preserves
its useful held-D rows after they were evicted from the final ring.

The restoration record confirms original focus and frame Var roots and exact
tick-key presence restored. The restorer also asserts original tick-value
identity; it updates only that config key and preserves unrelated live config.
Observation closes before hook removal, and the final state is `:restored`.

Independent `ffprobe` inspection reports:

| Actual window recording | Dimensions | Duration | Recorded frames | Capture rate |
| --- | --- | ---: | ---: | ---: |
| [Controls](cadence-controls.mp4) | 1280×720 | 120 s | 1,440 | 12/s |
| [Fine flight](cadence-fine-flight.mp4) | 1280×720 | 90 s | 1,080 | 12/s |

**12/s is the capture/encoded frame rate, not native render FPS.** These
recordings cover portions of the longer diagnostic interval; repeated captured
frames are expected. Root owns final media closure and the remaining service
audit. This result establishes the scoped native attention and input behavior;
manual planetary overlap, commitment, sculpting, visible Gate entry, and overall
gameplay completion remain unverified.
