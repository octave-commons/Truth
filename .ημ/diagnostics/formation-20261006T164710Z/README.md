# Live formation capture evidence

This directory records the 360-second native run embedded in the gallery.
The world is the unmodified default seeded nebula, advanced by the existing
`domain.arc/tick-genesis`. It starts paused at tick zero for shader/frame
readiness, then resumes normal physics. The volume pass remains enabled.

- `initial-state.edn`: tick-zero cloud and initial fixed camera.
- `progress.edn`: read-only snapshots about once per wall-clock second.
- `formation-events.edn`: an intermediate sample of the actual threshold
  ledger, including entity 491's condensation at tick 354 and ignition at
  tick 952. This is a sample, not the complete final event ledger.
- `stellar-state.edn`: intermediate readout of the first formed star.
- `system-reframe.edn`: result of the eight-second camera ease, after about
  242 seconds of sampled live progression. Physics continues during it.
- `final-state.edn`: two stars, 904 nebula particles, and no runtime error.
- `video-probe.json`: full 360-second H.264 source at 1280×720.
- `media-validation.json`: decoded GIF durations, loops, distinct frames,
  PNG dimensions, camera checks, and all gallery image targets.

The initial capture observer queried the wrong simulation-time key, leaving
`:sim-time nil` in early progress samples. The native HUD still recorded the
correct clock throughout. The observer was corrected to `:genesis/sim-time`
when the camera helper was loaded; historical observations are preserved.
Neither change altered the physics or parameters. The final producer uses
the corrected key and automatically schedules the reframe at 240 seconds.

The 8× overview preserves the complete source interval; its slower excerpts
are source seconds 24–44 at half speed, 70–92 at normal speed, and 260–340 at
4×. GIFs and PNGs are actual captured pixels, not generated illustrations.

A separate four-second end-to-end producer smoke run is recorded in
`../formation-20261006T165547Z/`. It verified tick-zero readiness, resumed
physics, progress recording, GIF encoding, error checks, and owned cleanup.
