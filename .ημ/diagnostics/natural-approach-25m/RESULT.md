# Natural planets observed; approach stopped at native menu input

The independently reviewed 25-minute follow-up reached **12 naturally formed
planets**. Planet 1010 was currently eligible in the actual production handoff
calculation at tick 4366 and remained per-body eligible through the final
tick-5676 readback. **No thrust was applied and no pursuit was attempted.**
The run failed while switching from Fine to Cruise: after a recorded Spark-panel
click, the next production menu layout did not expose Cruise. The supervisor
refused the action and performed its owned cleanup.

## Provenance and successful observations

Production source remained `b395c4049718f7ce015ddf25fc0a192d97821373`.
The three-line budget amendment was independently reviewed by root and
`runtime_scout`; snapshot bytes were unchanged from the first attempt.
Preparation is committed at `f2e8b24b171f5459dc49287ad840504d3357c22a`.
The preparation commit followed launch because the first staging attempt
misinterpreted directory-relative inventory names and failed before staging;
[preparation-publication.json](preparation-publication.json) preserves that
chronology. Source review and frozen hashes preceded launch.

The new ordinary seed-42, 1,000-gas world used the production demo nebula route,
production tick and projection, a private Xvfb display `:1`, loopback port 7897,
and a 2 GiB JVM. No fixture, forced birth, replay, teleport, follow-camera hook,
physical clock adjustment or live source reload was used. The retained primary
game and physical desktop were not targeted.

Fresh identities: supervisor 1834869/start 1895785, Xvfb 1834876/start 1895802,
game 1834893/start 1895824; boot `b04f7fb6-ea65-4b47-8866-73e24a6887f6`;
world atom 2035966977, GLFW window 140191357092992, X window 2097159.
[closure.json](closure.json) confirms all 186 production hashes and three frozen
preparation hashes remained unchanged, with no production-source diff.

| Readback | Tick | Observation |
| --- | ---: | --- |
| `008-inspect.stdout` | 428 | Actual R input selected Manual; ordinary protostar formation |
| `015-inspect.stdout` | 814 | Actual Fine action selected `D=1e7 m/tick`, retention 0.97 |
| `022-inspect.stdout` | 1654 | Panel closed, cursor locked, thrust nil |
| `024-inspect.stdout` | 2614 | Single +100-pixel native look changed yaw −90°→−89°; no planets |
| `026-inspect.stdout` | 3804 | No planets; accretion |
| `027-inspect.stdout` | 4366 | 12 planets; current handoff selected 1010, 144,070.553 AU from Spark |
| `039-inspect.stdout` | 5107 | 1010 still eligible, 152,708.022 AU away; requested bulk rotation not fully reflected |
| `042-inspect.stdout` | 5531 | Manual, cursor free, no active panel; 1010 at 157,604.388 AU |
| `045-inspect.stdout` | 5676 | No active panel, hence no Cruise hit region; 1010 at 159,277.039 AU |

The first saved natural-planet observation occurred at 05:50:09.577 UTC,
751.706 seconds after supervisor startup. It is an observation time, not an
exact birth timestamp. The preceding no-planet sample was at 05:48:19.747.
This run did not sample the intervening formation ledger event. Planetary
coverage was complete for these 12 bodies, below the snapshot's explicit cap.

At tick 4366 the nearer 1011/1012 bodies retained historical candidate records
but had no current parent; 1010 was chosen using current production eligibility.
The physical heading calculation and operator rationale are in
[operator-notes.jsonl](runs/attempt-01/operator-notes.jsonl). This was intended
numerically assisted diagnostic piloting, not evidence of unaided navigation.

## Input failure and limits

The single 100-pixel calibration matched 0.01°/pixel. Later bounded relative
look commands requested a total −2739 pixels X and −5348 pixels Y from the
calibrated −89°/−20° camera. Their intended endpoint was −116.39°/+33.48°;
the actual tick-5107 endpoint was **−112.79°/+1.60°**. All tool commands exited
zero, but their requested displacement was not fully reflected in the camera.
No claim of successful alignment follows from command exit codes. X11 clipping,
recentring or event delivery remain possible causes, not recorded diagnoses.

After a real Tab input freed the cursor, `042-inspect.stdout` supplied the
current Spark hit region. Command 043 moved to its centre and used `click 1`;
command 044 then ran unconditional key/button release. Both exited zero.
The following 045 inspection still reported `:ui/active-domain nil`. Request
`1791352491103477381-f62639e6` therefore failed with
`AssertionError('Requested menu control not currently visible')` before any
Cruise click was sent. Fine remained selected.

A source audit identifies a **hypothesis**, not proven runtime causality:
the driver sends a complete press/release click followed by another `mouseup 1`;
the production mouse callback can create a pick request on any delivered left
release without requiring a preceding press. If both releases are consumed in
separate frames, a toggle could open and close the panel. Zero accepted clicks
is also compatible with the saved menu observations. Earlier similar commands
did successfully open the panel. Event-level evidence is needed before changing
production code or calling this a game regression.

Two operator submission errors are also retained in the closure record. A frame
client was invoked while the 039 inspection held the operator lock; it failed
before creating a request. The inspection completed successfully. A subsequent
Spark client after the Cruise failure was refused because the attempt was no
longer ready, also before creating a request. Neither produced native input;
neither is counted as a successful request or hidden from the outcome.

The only saved image is the earlier
[Fine-selection frame](runs/attempt-01/frame-1791351594182669882-67e4b27a.png).
It predates natural planets and does not illustrate the later menu failure.
No video was requested in this follow-up. The first attempt's separate full
30-second MP4/GIF pair remains linked from its own report.

## Closure and next diagnostic

The supervisor recorded **failed**, not success, at
**05:54:59.593089 UTC**, after **1041.722 seconds**, below its 1500-second
absolute limit. It recorded no cleanup error, budget overrun or unreaped child.
All 46 children have matching reap records: 45 exit zero and the game exits 143
after intentional owned termination. All three owned long-lived process IDs
were absent afterward. Of 29 accepted requests, 28 succeeded and one failed.
Cleanup does not establish the application's Escape lifecycle contract.

The final state retains empty binding, nil commitment/palette and nil thrust.
No physical approach, overlap, commitment, voxel resolution, sculpt, embodiment
or Gate was demonstrated. The increasing separation is observation while idle,
not a failed pursuit experiment. Runtime stdout also preserves kinematics
substep-clamp warnings; this is no isolated performance or numerical-accuracy
qualification.

The next useful work is a **short, separate input reproduction**: establish
whether duplicate releases can toggle a menu twice, distinguish native event
delivery from source callback behavior, and verify incremental look response.
Do not rerun this archived driver for another long approach before resolving
its input ambiguity. Production changes still require their own causal evidence
and admission; this verification owner remains In Progress, estimate 3.
