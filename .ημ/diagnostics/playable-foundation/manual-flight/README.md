# Native manual flight and approach evidence

Source loaded: `81207e7c65212685456ebe0faff3cac1cec50613`.
Run date: 2026-10-06 UTC. This is an ordinary default nebula world created by
`DISPLAY=:1 TRUTH_DEMO_PORT=7894 clojure -M:demo serve`. No fixture, planted
planet, forced phase transition, direct runtime position/velocity change, or
REPL world tuning was used. Readback forms dereference one published world and
call existing production predicates. Physical controls and tuning came through
native X11 keyboard/mouse input to the existing GLFW window.

## What was exercised

- R selected ordinary manual mode. W/D and later A/S/Space exercised existing
  camera-relative thrust; release cleared the intent and damping slowed flight.
- Tab and mouse movement exercised the existing captured-look path. Mouse
  heading changed, while spark orientation stayed identity as expected: the
  rotational substrate does not yet include the later input/torque cards.
- Actual Spark panel controls changed damping to 0.8, then thrust displacement
  from 3e14 m/tick to its documented 1e12 m/tick floor. Actual View panel controls
  changed look sensitivity to 0.2842170943040401. Values were verified from the
  published world/config, rather than inferred from intended clicks.
- Focus was raised with native comma inputs and subsequently eased with period
  while preparing distant travel. Final focus was 0.25; the final far-distance
  flight measurement is not a sustained-focus/binding test.

`native-inputs.log` records input times after the initial movement segment.
Intent text such as “17 clicks” describes sent input, not accepted changes.
Several rapid clicks were not reflected in the later value under this loaded
software-rendering session. Later measurements verify the actual settings.
The fine-burst log says “W20s”; its exact duration includes the read-only client
latency and is bracketed by its logged start/release timestamps.

## Measured approach

The navigation target was the naturally formed planet 1012, Rutaex. Read-only
coordinates helped aim real mouse input. This is evidence of control behavior,
not proof that a player can find this route using the current HUD alone.

| Readback | Tick | Distance to 1012 | Actual state |
|---|---:|---:|---|
| approach-007.edn | 6957 | 264,914.58 AU | Before coarse W burst; D=3e14, damping=0.8 |
| approach-008.edn | 7265 | 30,464.18 AU | After 30 s native W and release; spark nearly stopped |
| approach-009.edn | 7473 | 28,648.92 AU | After another 2.5 s correction; best saved distance |
| approach-010.edn | 7774 | 38,107.80 AU | Native fine floor confirmed: D=1e12, damping=0.8 |
| approach-011.edn | 7966 | 44,288.84 AU | Before fine thrust; target continued moving during setup |
| approach-fine-held.edn | 8053 | 46,751.32 AU | W held; spark about 241.6 m/s, target about 1.21 km/s |
| approach-012.edn | 8104 | 48,264.53 AU | Released; thrust nil, binding empty, spark nearly stopped |

The minimum setting did not pursue this moving target successfully. Coarse
travel did work. Intermediate thrust settings were not tested for capture, and
these observations do not prove that manual approach is impossible. No sample
reached the 1 AU overlap boundary. Commitment, local resolution, and paid
sculpting remain unverified. `approach-held-W.edn` was named before its read
finished; the recorded thrust is nil, so it must not be cited as held-key proof.
`approach-fine-held.edn` does contain a non-nil actual thrust vector.

At the final audit, tick 8976, `eligibility-final.edn` records the real production
handoff write-set as `{}`. All 12 planets fail current admission: 1001–1009 have
a bound parent but fail the production per-body predicate; 1010–1012 have no
current bound dominant attractor. Planet 1012 has `orbit-stable=false` and no
current two-body orbit around its historically recorded star. Its prior
candidate record persists, as intentionally documented in
`domain.stellar.classifier.candidate`. Production `ready-to-commit?` reads the
arc and that stored record and therefore remains true for 1010/1012. That is a
historical-record admission contract, not evidence of fresh physical eligibility.
No commitment was forced. Binding remains empty and the service is idle.

## Media and visual limits

| Native video | Duration | Companion GIF | Playback |
|---|---:|---|---:|
| manual-flight.mp4 | 90 s | manual-flight-overview.gif | 6×, entire window |
| manual-controls.mp4 | 30 s | manual-controls-overview.gif | 3×, entire window |
| manual-approach.mp4 | 90 s | manual-approach-overview.gif | 6×, entire window |

The approach video covers coarse travel/correction, not the later fine-floor
attempt, which has raw readbacks and the confirmed knob screenshot. GIFs use
960-pixel width and 8 fps, with no omitted interval; `media-probe.json` records
codec/dimensions/durations. Original videos are native 1280×720 captures.

Actual screenshots show dense upper-left telemetry, cyan wire clusters that
compete with body visibility, little heading/depth guidance, and event/actions
text overlap in the initial view. No resolved planet globe was established by
this manual attempt. Debug follow/zoom inspection was not performed in this
segment. Runtime rate is not an isolated benchmark: software rendering,
recording, and other simulation/gate work were concurrent.

## Reproduction and retention

The forms `snapshot.clj`, `approach-snapshot.clj`, and
`eligibility-snapshot.clj` are read-only. `snapshot.clj` emits a tagged Camera
record; local EDN consumers can use a default tag reader, or inspect the later
approach snapshots whose camera is an ordinary map. The eligibility probe's
first attempt mistakenly treated stellar entity IDs as records and failed;
the corrected diagnostic calls the existing `star-record` helper. The error
was in the diagnostic, not the live simulation, and did not mutate the world.

The first service launch used the wrong display `:3` before correcting to the
allocated `:1`. `failed-display-start.log` preserves that harness failure. Only
that owned failed process was terminated; the corrected session ran normally.

The retained service was last observed idle with all keys released:

- Display `:1`; native window `2097159`, “Gates of Truth — Dev Window”.
- Loopback nREPL `7894`; Java PID `2372912`; Xvfb PID `2368830`.
- Native service source `81207e7`; manual mode; mouse captured; no UI panel.
- Camera distance 50 render units; look sensitivity 0.2842170943040401;
  thrust displacement 1e12 m/tick; damping 0.8; focus 0.25.
- Escape was intentionally not exercised because the parent requested the
  world remain available. No current service/UI error was returned.

`CLOSED-FILES.txt` is the exact repository-relative checkpoint inventory.
`SHA256SUMS` covers those closed files except itself. `capture-manifest.json`
records exclusions. Active `runtime.log`, `xvfb.log`, and the display handle are
excluded; `response-probe*` belongs to another lane and is also excluded.
Do not glob-stage this directory. Future observations should use a new segment
rather than changing this closed evidence set.
