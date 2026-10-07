# Closed attempt 02 — independent offline audit

The ordinary native attempt completed one measured aim at natural target 1010 and two released W pulses, then stopped cleanly at T=1253.942272s. It did not reach binding, commitment, sculpting, embodiment or a Gate. Two noncontracting sampled intervals support the bounded stop; they do not establish that flight or capture is impossible.

## Provenance and complete journal coverage

- Source: b395c4049718f7ce015ddf25fc0a192d97821373; all 186 launch/source-manifest hashes still match current bytes. The original 17-file preparation (16 checksums), six runtime pins, and separate 11-file operator preparation (10 checksums) match.
- The later thin root client has its own review-before-use record and SHA256 38835672f94d70467bd4f8aa2d15413ad1d21c611d8d9b1c2db556ef614b2b02. It is not part of the original launch bundle.
- Exactly 209 snapshot IDs, 0001–0209, cover 3,532,597 UTF-8 bytes and 4,317 raw EDN message maps with no gaps or overlap. All 420 request-journal events and all snapshot request/result/consumed operations agree. Every request returned one complete value and done record; there were no nREPL error messages.
- Every sampled world reports the same world atom/window, ordinary nebula, fixture false, and zero same-world global commitment. Host camera/config sampling is separate from the published-world read.
- 707 raw/preparation input files, 5,762,617 bytes, were hashed and rechecked. The JSON includes a complete path/byte/SHA256 map for later lossless packaging. Its canonical inventory digest is 2f911acab6721330c2fe6f3f5336813dc3e6bb0f3f6ec793384e07ba38992c15.

## Inputs and natural target

There were 102 successful supervisor IPC operations: {'inspect': 58, 'tap': 2, 'click': 7, 'look': 29, 'frame': 2, 'aim-start': 1, 'aim-end': 1, 'hold': 2}. The actual sequence was R, Spark open, Fine, Cruise, three individually acknowledged halvings to D=3.75e13 m/tick, Spark close, Tab lock, four ±200-pixel calibration gestures returning to the baseline, 25 aiming gestures, two W pulses, and operator stop. No retarget, follow-camera command, fixture, world setter or achievement injection is recorded.

The read-only poller completed 22 polls. Polls 00–20 reported no planets; poll 21 first reported 12 planets at tick 4228, stored/current-ready 1010 and 1012, and fresh handoff 1010. This is the first observation, not an exact birth time. The attempt02 poller already corrects attempt01's ready-before-commit reason-priority bug; all actual commitment counts here remained zero. It selected no target.

Two further coast reads at ticks 4383 and 4476 preceded the root's explicit target choice. The pre-aim screen used observed rates 5.133187, 5.520876, 5.425529 ticks/s, N*=25 and D≈250.672017 AU/tick: coast fraction 0.050388697, combined fraction 0.089349030. These heuristic screens passed without guaranteeing capture.

## Aim and input acknowledgement

Target 1010 was admitted at T=825.635122s, tick 4686: stored candidate, actual ready-to-commit, fresh handoff, finite geometry and global commitment zero. Admission occurred after 800s but before the 960s cutoff, with 644.365s remaining against the original 1470s work deadline.

The aim used 25 native relative looks and 26 geometry rows. Independent vector/camera arithmetic reproduces the final tick-5000 error of 0.004112773256 degrees at yaw −117.38°, pitch 29.92°. It took 69.668829s internally and 70.195566s for the outer client. This is successful orientation at one observation, not continuous interception.

There were 45 input-acknowledgement groups and 88 readbacks. The distribution by number of reads is {1: 16, 2: 18, 3: 8, 4: 3}. Three aim gestures required four reads; the greater-than-three observation allowance was actually exercised. This does not establish the cause of any earlier failed run.

## Thrust, coast endpoints and stop

Both pulses requested 2 seconds. Native keydown→keyup command intervals were 2.017459s and 2.001565s; recorded release completion spans were 2.181123s and 2.061728s. Requested duration is not an exact native-duration claim.

Both observed thrust vectors match the camera-derived direction [0.3985969690, 0.7696289233, −0.4987903134] within 1e-9. Each pulse began with nil thrust, eventually acknowledged nonnil thrust, and acknowledged nil on the third post-release read. Camera and D remained unchanged in all sampled post-aim states. The before→after tick spans of 12 and 16 include publication/queue/release latency; they are not exact counts of thrust-accepted ticks.

| Pulse | Before / held / released / coast A / coast B ticks | Before AU | Released AU | Coast A AU | Coast B AU |
| --- | --- | ---: | ---: | ---: | ---: |
| 1 | 5665 / 5671 / 5677 / 5803 / 5914 | 176824.779268 | 176824.203616 | 177176.907151 | 178616.512879 |
| 2 | 6294 / 6303 / 6310 / 6396 / 6473 | 183565.901743 | 183577.131795 | 183233.792419 | 184142.797791 |

The root's +1791.733610877 AU and +576.896047751 AU are correctly pre-keydown→second-coast changes. Released-snapshot→second-coast changes are +1792.309262614 AU and +565.665996586 AU. Both definitions remain noncontracting. Pulse 1 briefly contracted by 0.575652 AU before its released snapshot; pulse 2 contracted by 343.339375 AU between release and its first coast. These transient decreases remain in the evidence.

Important operator gaps were 86.010s from first ready poll completion to admission, 135.320s from aim-client completion to the first W request, and 131.421s between W requests. Natural motion continued through those gaps. Distances use paired world positions; no constant target velocity or velocity×dt orbital displacement was assumed.

## Images and cleanup

The two unchanged 1280×720 PNGs were viewed directly:

- frame-1791369829536127035-02a8d2de.png visibly reports tick 432, zero stars and zero planets; nearest preceding snapshot was tick 430.
- frame-1791371010177321862-d4589e86.png visibly reports tick 6665, one star and twelve planets; the separate disks row reports five disks. The last independent snapshot was tick 6473, about 40.7 seconds before the frame request. Its pixels show distant glyphs and nebula, not a target-specific close-up or temporal fade measurement.

All 66 spawned native children were reaped before closed.json: 65 exited 0, the game exited 143 during deliberate cleanup. All 53 aim subclients exited 0. All 52 recorded outer clients exited 0; the stop client reaped after the supervisor's closed record, as expected for the caller awaiting closure. No pending key, uncertain release, cleanup error or unreaped child remains in the closed state. Root separately recorded all four principal PIDs absent at 2026-10-07T11:04:04.750152+00:00; this audit did not probe live processes.

The run used its private :1/7898 surface, 2 GiB game heap and 256 MiB snapshot worker. It closed before the 1470/1500-second work/total bounds, using 1/3 aims, 29/360 looks, 2/20 W pulses and 2/3 frames. The only nonempty recorded stderr is Xvfb's 2,480-byte display-selection/keymap warnings. Runtime stdout contains 7,839 literal K-clamp messages and the final stop marker. Those messages are not causal proof of ejection or approach failure; no GL-error sampling, hardware FPS or isolated-performance claim is made.

## Preserved unsuccessful preparations and calculations

The missing outer-log-directory preflight is retained separately and predates the real run; root records it failed before Popen. The first offline range parser's ValueError is disclosed in first-pulse-screen.json, but no separate raw traceback is present in the audited input set. The original pulse-one-analysis.json retains its missing-kind result (last_hold null); pulse-one-interval.json retains the correction. Their corrected geometry and range arithmetic were reproduced independently here. The older malformed preparation manifest and exact repair evidence also remain unchanged.

This audit performed 14,978 static checks and changed no raw evidence. It establishes successful bounded orientation/input and truthful cleanup, with unsuccessful capture in this limited trial. It does not establish a general flight limitation, physical impossibility, or completion of the user's playable-Gate goal.
