# Native precision-control verification

Source `24cbffd40fcc22e77cb967fa9da2b65e67be509c`; ordinary live nebula, private
Xvfb `:2`, loopback `7895`, 2 GiB maximum heap. Real X11 keyboard/mouse input
was the only runtime mutation. Read-only nREPL snapshots inspected published
worlds. No fixture, body placement, phase advance or console tuning was used.

At 1280×720 the actual Spark panel visibly exposes Cruise and Fine alongside the
numeric thrust value and existing stepper. Native clicks verified:

| Snapshot | Tick | Displacement m/tick | Highlight |
| --- | ---: | ---: | --- |
| before-fine | 1394 | 3e14 | Cruise |
| after-fine | 1567 | 1e7 | Fine |
| custom | 1719 | 2e7 | neither |
| back-cruise | 1837 | 3e14 | Cruise |

Retention stayed `.97`, camera mode stayed manual and all readbacks had no
service/UI error. Matching full-window PNGs and complete raw EDN are preserved.
`verification-summary.edn` records actual preset colors, settings and velocities.

An actual two-second W hold at Cruise followed by release produced 2710.84 m/s
at tick 1963. Selecting Fine during that coast yielded D=1e7 and a nonzero
22.93 m/s at tick 2114. These are different ticks in a live gravitational world:
the observation supports ongoing coast, while the focused tests establish that
the setting intent itself changes only D. No moving-target capture is claimed.

`precision-controls.mp4` records the full window for 120 seconds at 24 fps.
`precision-controls-full.gif` preserves the entire interval and full 1280×720
frame at 3× playback speed (40.01 seconds, 8 fps), with no crop. Escape was
pressed after the video ended and is preserved separately in the input log,
two service readbacks, a PNG and X11 window-state output.

Escape stopped the render thread. The native window remained mapped, and the
simulation/nREPL service continued from tick 2272 to 3799 without a reported
error. This is not evidence of whole-service quit; the intended dev-service
lifecycle is a separate documentation/source question. After those observations,
only owned service PID2798625 and display PID2797145 were terminated and reaped;
the service printed `Dev window stopped.` and port7895 became free. Retained
service PID2372912 remained stopped (`T`) before/after and was never signalled.

See `run.edn` for provenance and limitations, `CLOSED-FILES.txt` for the closed
capture inventory, and `SHA256SUMS` for content hashes. No performance benchmark,
formed-world approach, binding, commitment or sculpt is claimed by this capture.
