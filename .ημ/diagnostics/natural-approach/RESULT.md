# Ordinary native approach attempt 01 — target unavailable

The first bounded attempt verified native Manual/Fine selection and ongoing
ordinary accretion. **It did not attempt a planetary approach:** every saved
world readback had zero natural planetary targets, through tick 3748. The later
full-window frame at tick 3825 also displayed zero planets. No movement thrust,
look adjustment, binding, commitment, voxel resolution, sculpt or Gate was
demonstrated. This is an inconclusive target-availability result, not evidence
that approach is impossible or that formation is broken.

## Reproduction and provenance

- Production source: `b395c4049718f7ce015ddf25fc0a192d97821373`.
- Ordinary `clojure -J-Xms256m -J-Xmx2g -M:demo serve nebula`, seed 42 and
  1,000 initial gas bodies. Production tick and projection were checked by
  identity; no fixture, injected body, reposition, forced birth, replay,
  follow-camera hook or runtime source reload was used.
- Fresh owned game PID 1751684/start 1801297, Xvfb PID 1751667/start 1801276,
  supervisor PID 1751650/start 1801268, private display `:1`, loopback port
  7897, world atom 243870065 and GLFW window 139884669648016.
- Boot identity: `b04f7fb6-ea65-4b47-8866-73e24a6887f6`. Actual JVM maximum
  heap was 2,147,483,648 bytes. Raw child identities, commands, request results
  and exits are in [operations.jsonl](runs/attempt-01/operations.jsonl).
- [closure.json](closure.json) confirms all 186 pinned production-file hashes
  and the three preparation hashes remained unchanged. Source comparison to
  the base is empty for `src`, `dev`, `resources`, and `deps.edn`.
- The retained primary PID 131581, its physical display and PM2 service were
  not targeted by this driver. This run supplies no fresh primary-world health
  or identity verification.

## Observations

| Saved observation | UTC time | Tick | Result |
| --- | --- | ---: | --- |
| Initial world | 05:22:12.541 | 43 | Fit-all, 1,000 nebula bodies, no planets |
| Manual readback | 05:26:12.778 | 2122 | Real R input took effect; one star, one protostar, one brown dwarf |
| Fine readback | 05:27:08.382 | 2444 | Actual Spark/Fine menu action; displacement `1e7 m/tick`, retention `0.97` |
| Cursor readback | 05:28:55.885 | 3115 | Spark panel closed, cursor locked, manual mode retained |
| Formation check | 05:30:06.637 | 3521 | One star, two protostars, no planetary targets |
| Final world | 05:30:50.471 | 3748 | Accretion, no planets, empty current handoff and binding, nil commitment/palette/thrust |
| Later native frame | 05:31:05.316 capture completion | 3825 | Accretion, zero planets shown, new dense-core notification visible |

The only actual input requests were **R, Spark panel open, Fine, Spark panel
close, Tab**. Every action attempted unconditional key/button release. No
movement hold or mouse-look request was submitted; a missing target was not
substituted with blind movement. The initial default Cruise displacement was
`3e14 m/tick`; Fine changed it to `1e7 m/tick` through the ordinary UI.

Snapshots are single immutable published worlds, while camera/config/menu are
separate host reads. Published focus may lag the binding consumer's physical
input. These snapshots therefore do not prove sustained consumer overlap.
World readbacks and framebuffer captures are also not paired render samples.

## Native media

- [Initial fit-all frame](runs/attempt-01/frame-1791350553045550839-9f6d4356.png).
- [Actual Fine menu frame](runs/attempt-01/frame-1791350843063057093-e4328024.png).
- [Final manual frame](runs/attempt-01/frame-1791351064481852325-e44ebf3b.png).
- [30-second full-window MP4](runs/attempt-01/video-1791350907528454311-4c41241e.mp4)
  and [full-duration GIF](runs/attempt-01/video-1791350907528454311-4c41241e.gif).

The MP4 contains all 360 frames at 1280×720 and 12 capture frames/s. Its GIF
companion preserves all 30 seconds and 360 frames at 640×360, without cropping.
Media dimensions/durations are verified by ffprobe in `closure.json`. Capture
cadence is not measured game FPS. Xvfb/software rendering and concurrent host
load provide no hardware performance result.

## Closure and next measurement

[closed.json](runs/attempt-01/closed.json) records operator stop at
**05:31:14.312677 UTC**, **561.625 seconds** after supervisor start. There were
no cleanup errors, unreaped children or budget overrun. All three owned process
IDs were absent after closure. Helpers/video were reaped before Java and Xvfb;
release was attempted before display teardown. This is owned-process cleanup,
not proof that the application's Escape lifecycle is correct.

Prior closed evidence in commit `7a607e7d9c8f7183baa2c14ddd82f3de69da48ff`
puts natural births near ticks 4038–4169: seed 42/43 pure runs on `81207e7`
first formed planets after 808.664/750.865 seconds; a native run on `a3609f6`
bracketed first birth between 430.511 and 431.512 seconds after its tick-zero
monitor sample. The native predecessor had a startup pause and camera reframe,
and the headless runs had different host conditions. These are formation
observations, not equivalent-route timing comparisons or a promised birth time.

Relevant historical paths are `playable-foundation/natural-seed42/observations.edn`,
`playable-foundation/natural-seed43/observations.edn`,
`playable-foundation/natural-formation-observation/birth-snapshot.edn`, and
`formation-20261006T182457Z/progress.edn` beneath `.ημ/diagnostics/` in that
evidence commit.

The next measurement is a separately prepared and independently reviewed
**1,500-second total / 1,470-second work** attempt on the same production source.
The attempt-01 driver and declared deadline remain unchanged. A larger finite
window gives natural formation and ordinary piloting more time; it changes
neither gameplay acceptance nor the prohibition on manufactured targets.

The canonical verification owner remains **In Progress, estimate 3**. Existing
source qualification is separately pinned; this evidence-only work adds no
production change and claims no new full-suite, strict-gate or hosted approval.
