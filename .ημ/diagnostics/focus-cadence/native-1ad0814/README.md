# Focus cadence: retained native world and host-loop cost

Closed 2026-10-06. Owner: `focus-follows-pilot`, still In Progress3.
GPL-3.0-or-later. This is diagnostic evidence, not a new runtime or a gameplay
completion claim. No Gate, embodiment or manual planet commitment is claimed.

## Result

The live native simulation accepted 1,772 manual-focus inputs, all with exact
prepared-world identity and correctly aligned pre-fold attention. There were
1,460 advancing aligned input pairs between completed render frames, zero
identity/alignment failures and zero probe errors. The retained Fine-flight
snapshot includes 11 actual held-D inputs and neutral thrust immediately before
and after. All 11 sampled owner-thread GL drains were `[0]`.

Read [the independently derived native result](native-proof-result.md) and its
[numeric summary](native-proof-summary.edn) for exact counters, raw-input hashes,
bounded-ring evictions, the one tick in flight at closure and the remaining
post-fold lag. The probe compares normal function boundaries without applying
another focus update; each original is called exactly once. It adds diagnostic
overhead and does not measure native FPS.

The detached host-loop measurement on the stopped, actual immutable world
observed a median paired manual-mode cost of **37.900 microseconds CPU** and
**8,432 allocated bytes** per iteration. Tracking allocations were unchanged;
mixed tracking CPU differences do not establish a speedup. Read the separate
[cost result](host-loop-cost-result.md), [plan](host-loop-cost-plan.md) and raw
[measurement](host-loop-cost.log). The identity-tick harness intentionally
measures host-boundary work, not physics or rendering throughput.

## Full recordings

| Recording | Original | Full interval preview |
| --- | --- | --- |
| Offset, narrow and widen controls | [120-second MP4](cadence-controls.mp4) | [6x GIF](cadence-controls-full-6x.gif) |
| Fine selection, held D and release | [90-second MP4](cadence-fine-flight.mp4) | [6x GIF](cadence-fine-flight-full-6x.gif) |

MP4s are actual uncropped 1280×720 X11 window captures at 12 capture frames/s;
that rate is **not native render FPS**. The full-interval GIFs are uncropped,
scaled to 960×540, sampled at 8 frames/s and played at 6x speed. GIF centisecond
quantization yields 20.01 and 15.01 seconds. There are no omitted clips or
generated game frames. Encoder logs and [ffprobe results](media-inspection.json)
are retained. These videos cover portions of the longer 430.794-second probe.

## Runtime composition and retained-world restart

The ordinary seed42 world was never regenerated, teleported, fixture-loaded or
rewritten for this proof. The original Java service was launched from source
`22f762f` with production baseline `400ba3a`; the previously verified line-pass
namespace was reloaded from `85f307f`. This proof then loaded only the committed
window-loop file from `1ad081475364cc3b75db76142204cbf54b5bf60c`, SHA256
`f9e860f7398141d0c0d0cf4bd0bd11dba7141f7edd4b4916f8d0051efc62ea0b`.
The domain focus edit in that source is documentation-only; its executable
body did not change. This is a disclosed composed process, not a clean launch
of every file at the evidence head `b402939931575e377ae4409d9cd4061dff11df06`.

The [reviewed procedure](procedure.md), four `stage-0N.clj` scripts and their
logs record cleanup and restart. Both old workers were independently confirmed
dead. The current and known historical window callbacks/contexts were retired
on their owning render thread; stale GPU host handles were dropped before the
replacement context. No global GLFW termination or process-global error-callback
cleanup is claimed. The world atom, actual stopped world value at tick23086,
camera/config atoms, IntentAtom and pending queue were retained. The renderer's
local frame/input/timing state and simulation pacing counter restarted, and the
window changed from X11 2097165 to2097171 (GLFW132406392702304). The screen was
interrupted during this procedure. It is diagnostic use of existing private
lifecycle helpers, not a repaired public restart API or portable GLFW thread
contract.

## Closure and honest failed probes

The proof closed at tick26162 and restored exact original focus/frame Var roots
and tick-hook presence/value. [The final read-only audit](final-service-audit-v2.log)
at tick28343 confirms world identity1675959387, both new workers alive, both old
workers stopped, the intended loop root, all proof hooks restored, no service/UI
error, manual mode, zero focus offset, closed drawer, Fine1e7, neutral thrust and
an empty pending-intent queue. [The final actual screenshot](final-window.png)
was taken slightly earlier, at tick28202; it is not synchronized with that audit.
Java3083624 and owned Xvfb3082301 on display:2 were retained for further work.
The older separate Java2372912 on display:1 remained explicitly paused.

The first final audit queried the world-owned Fine key in host config and
correctly failed its assertion. Its unchanged [script](final-service-audit.clj)
and [failure](final-service-audit.log) remain here. The corrected v2 reads the
world key; no runtime change was made to get a pass. Earlier
`fine-menu-readback.log` similarly names a nonexistent thrust-scale field;
`fine-confirmed.log` is the actual positive selection evidence. Menu screenshots
can precede input consumption: `spark-menu.png` is not proof that its requested
drawer was already open. Use the later explicit readbacks.

`source-before-reload.json` is a historical preparation record and deliberately
retains its then-pending status. Later stage/probe/audit records supply completion.
Active runtime logs, later JFR work, and unowned artifacts are outside this closed
bundle. `CLOSED-FILES.txt` is the explicit inventory, and `SHA256SUMS` hashes every
listed file except itself. Run `sha256sum -c SHA256SUMS` from this directory.

Manual planet approach/overlap, binding, commitment, paid sculpting, actual Gate
production and a polished playable game remain open. The independent native
report's root-closure-pending sentence records its handoff time; this later
README and final audit close only the evidence work described here.
