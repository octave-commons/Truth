---
category: "specs"
labels: ["domain", "infra", "render", "spark-flight"]
write-id: "1791333279947-0.05o8k25sc1gwmjeeh60c"
source: "kanban/tasks/body-trails-ringbuffer.md"
title: "Motion trails on star, planets, and spark (ring-buffer component + line render)"
priority: "P2"
status: "in_progress"
estimate: "5"
uuid: "body-trails-ringbuffer"
created_at: "2026-07-23T00:00:00Z"
---

# Motion trails on significant bodies + spark

> spark-flight epic, Wave 4 card 8 of 10. Design: `docs/designs/spark-flight-and-camera.md` §6.1.
> Fading trails behind the star, planets/protoplanets, and the mote — the
> perspective anchor that lets the owner judge whether flight and camera feel
> right. NO dependency on the flight cards: can be pulled forward at any time for
> grounding.

## Grounded integration (from design investigation, cite file:line)
- No trail/path component exists today (confirmed — none in `domain.ecs`, none in
  the renderer). Add a ring-buffer trail component (fixed capacity N positions)
  written by ONE system for the eligible bodies. Eligibility: star + planets +
  spark — gate on `c/body-kind` / matter-state so dust/fragments are excluded and
  a dense nebula stays legible.
- **Sample at a sim-time cadence, not per render frame** (`CLAUDE.md` Time model;
  `.agents/skills/physics-dt-unit-mismatch/`) — a per-tick or per-render sampler
  changes trail length with the clock. Store world positions; the trail writer is
  the sole writer of the trail component (`registry.clj:536`).
- Render via the renderer's `:line` path (raw positions, NOT the `:body`
  model-matrix path — see `CLAUDE.md` Coordinates; body vs particle/line split).
  Fade alpha along the strip (oldest → transparent). Precedent for particle/line
  overlay rendering in `src/infra/render/scene/` (spark particle at
  `scene/hud.clj:29-46`; bodies at `scene/bodies.clj`).
- Convert world→render units with `phase0-view-scale = 1e15`
  (`projection.clj:17-19`). Cap total segments across all trailed bodies for perf.

## Done when (player-visible via live pm2 window)
- The star, each planet, and the mote leave a visible fading trail showing their
  recent path; orbits and your own flight path are readable.
- Trail length is stable across time-rate changes (sim-time sampled).
- Dust/fragments are NOT trailed (scene stays legible).
- `clojure -M:test` + architecture-test + `bin/analyze --strict` green;
  `write-conflicts {}`.

## Risks
Segment-count blowup with many bodies (cap it); trails through the wrong render
path (must be `:line`/raw, not `:body`); sim-time sampling (recurring dt bug).
Choose N and cadence so trails are long enough to read but cheap.

## Dependencies
None. Can land any time. Pairs well with card 9 for visual grounding.

---

Root reviewed existing TODO estimate5 scope against accepted flight design section6.1 and fresh grounded note docs/notes/research/2026-10-06-body-trails-rendering.md; exact bounded contract in docs/notes/2026-10-06-body-trails-plan.md. Design will link both before code. One registered history writer, no new phase/position writes. Default64 actual samples/body,1e10sim-second cadence,6.3e11s horizon. Huge dt records at mostone observed sample, arithmetic deadline skip with visible metadata, no invented path. Everyfold recenter shifts retained and new observed positions once, even unsampled ticks. Eligibility excludes bare collision planetesimals and nebula; filter before historywork. Existing line path gets actual pervertexopacity through buffer/shader, defaultnontrailalpha0.85 preserved. Deterministic4096segment budget with requested/rendered/dropped diagnostics; currentstate head connection. Root clarification: compare sampling only at common real observation times, no general step-partition invariance claim. Use portable .cljc for new pure history/frame/opacity logic where practical. RED tests cover real frozen-snapshot integrator+trail recenter, eligibility/timebounds/largegap, registryownership, geometry/alpha/cap. Root commits failing tests before source. Full gates, isolated before/after benchmark and normalnative star/planet/player fading evidence remain required; no gameplay completion inferred.

Closed isolated body-trails Phase0 comparison: BEFORE clean e71b12f1f5fe070695fd1cd82cded2296a9f862d in truth-validation; AFTER reviewed LOD-corrected3614d34854bd0953ea12aef4aae1e3642b5b9c4b in truth-motion-trails. Both unchanged bin/bench :phase0 runs exit0 and reaped; durations414.360s and375.428s. Identical runner/dependency/benchmark hashes, selected JVM environment, affinity; OpenJDK21.0.12.1,22processors,6GiBmaxheap. Other owned heavy simulation/native/gate work held; retained2372912 SIGSTOP; unrelated host services documented/untouched. Complete37case comparison and raw stdout/stderr/resources/source/environment evidence: .ημ/diagnostics/body-trails/benchmark-comparison.md and benchmark-comparison.json, before/after logs+start/end metadata+timing. Explicit15file benchmark-CLOSED-FILES.txt,14/14 checksums verified. Mixed result: tick1004.5→4.6ms; tick50026.3→21.2ms; tick100052.2→47.8ms; critical50024.1→23.5ms;10-tick sequence218.1→299.5ms(+37.3%, reported ranges175.9–285.3 vs204.1–367.5ms). No blanket performance-pass/speedup claim: one pair, broad quantile ranges, ten-tick increase remains a signal to assess. Suite exercises fresh/early nebula worlds, not mature populated histories/GPU render cost. Focused correctness61tests292assertions0failures, independent source reviews no findings. Full stacked-source suite/strict and actual native visible fading remain pending; no review transition requested.

Timing clarification (means; before → after):
- 100 gas particles, one tick: 4.5 → 4.6 ms.
- 500 gas particles, one tick: 26.3 → 21.2 ms.
- 1,000 gas particles, one tick: 52.2 → 47.8 ms.
- 500-particle critical path: 24.1 → 23.5 ms.
- Ten consecutive ticks: 218.1 → 299.5 ms, +37.3%. Reported quantile ranges: 175.9–285.3 ms before, 204.1–367.5 ms after.
The ten-tick increase remains unresolved. One before/after pair with overlapping ranges does not establish a general speedup or a narrow regression bound. Both full runs exited 0; actual native fading and stacked-source full validation remain pending.

Native line-pass RED checkpoint prepared on integrated source 400ba3a (current HEAD recorded in red-2-start.json). The fresh ordinary game driver callback names GL_INVALID_VALUE in glLineWidth on its owning render thread. Source provenance proves glLineWidth(1.5) and the forward-compatible core hint already exist at e71b12f and blame to 63666769 (2026-07-08); this is an existing invalid call exposed by native verification, not a newly introduced width in the trail change. Khronos OpenGL 4.5 core Appendix D.2.1 disallows widths above1 in that context.

Independent private-Xvfb production-pass test: 1 test, 9 assertions, 2 expected failures, zero errors; both legacy/default-alpha and fading-alpha lines return GL1281 while actual nonblack pixels, real shader compilation, context flags, and readback checks pass. No production source modification; git diff HEAD -- src is empty. Native suite is explicitly invoked outside the headless test path, with all six static tools receiving its source path. Final native fmt/Splint and kondo checks pass. First heap-start failure and broader tool-style failure are preserved as non-passing probes; unrelated existing dev/smell_report style forms were not rewritten or suppressed.

Closed evidence: .ημ/diagnostics/body-trails/native-gl-line/red-verdict.md, red-2.log, source-provenance.json, copied owner callback attribution/restoration with hashes, and CLOSED-FILES.txt / SHA256SUMS. Root owns RED commit before a minimal explicit1.0 line-width repair; no context/profile/opacity/render-path change proposed. Card remains in_progress; no native gameplay completion or review transition claimed.

The native line-pass defect is GREEN above RED checkpoint923fd6d: the only production change is explicit GL11/glLineWidth1.0 with a context-restriction comment. Context selection, real shader/alpha/mesh/draw path and cleanup are unchanged. The same private-Xvfb regression passes1 test/9 assertions with zero failures/errors and actual pixels for both legacy and fading lines. Focused passes/shader/trail tests pass17 tests/72 assertions. A requested nonexistent mesh-test namespace was not selected and is not counted; real line packing is covered in trail-test. Changed-source/native fmt, Splint and kondo are clean; independent source review found no blocker. All owned JVMs reaped.

Evidence is .ημ/diagnostics/body-trails/native-gl-line/green-verdict.md plus complete green-native/green-focused logs/start/end metadata and green-static.json. Separate GREEN-CLOSED-FILES.txt and GREEN-SHA256SUMS preserve the earlier RED manifest unchanged. Root owns final source commit/full strict gate. This repairs the confirmed preexisting native GL error, not the entire gameplay acceptance; active ordinary-world source reload and visible fading/readability remain separately observed by its owner. Card stays in_progress.

2026-10-06 closed ordinary-world native evidence and same-world GL repair validation. Bundle .ημ/diagnostics/focus-input/native-400ba3a/ contains source/run manifest,91 verified hashes, four full-window MP4/GIF pairs, actual history/projection readbacks and bounded renderer observations. Snapshot11 at tick4641 has24 planets/two stars plus Spark:27 histories with63 samples each,1701 requested/rendered segments and0 dropped. Snapshot18 at8342 has36 planets/three stars plus Spark:40 histories,2519 segments,0 dropped, alpha0..0.85. Actual line program12/hash -1142320420 matches compiled source. Normal D motion leaves a visible cyan Spark trail; later manual screenshots show disappearance after release/history aging. Large cyan dipole loops are magnetic fields, not motion trails. Field loops and inspection text still obscure fine star/planet fading; do not claim their full player-readable orbital-path acceptance is closed.

Initial native GL samples returned1281. The bounded owning-thread KHR_debug callback named GL_INVALID_VALUE in glLineWidth and was restored/freed. Root RED923fd6d and GREENab02026 are the separate production native regression evidence. Namespace-only setup reload from launch22f762f to merged85f307f occurred at ticks11485->11486 in identical world atom1675959387. First post-reload sample2400 retained[1281,0], consistent with undrained earlier state (inference). Fourteen later sampled frames2430..2820 each returned[0], with no later nonzero sample and no probe/UI/service error. Original render callable restored at closure.

Software llvmpipe wide-view interval:1.284 achieved FPS and6.865 sim ticks/s; close inspection:5.351 FPS and8.509 ticks/s. Renderer-call wall durations include swap/sleep. Capture, sim, clients, diagnostic overhead and later root gates overlapped, so these are not isolated performance/GPU results. Pure mature-world writer/projection/packing costs are separately labeled and never applied writes. Operator Escape interrupted the initial loop; verified same-world lifecycle recovery, stale-window leak and host-config reset are explicitly recorded. Full visual readability and clock-dependent trail acceptance remain open; card remains in_progress.

2026-10-07 native evidence update, acceptance remains open. Hardware passive observation at ticks24989/25092/25195 used actual projection-input worlds and :trail/entity geometry for natural star258 and planet1001, spanning6.315335196689e11 simulated seconds. A fixed sample faded from alpha0.83756 to about0.407 and expired; actual full-scene line budgets dropped no segments, nine GL samples were zero, and original projection/renderer callables were restored. All three full PNGs were independently inspected. Fit-all path extents were only about1–3 pixels amid magnetic glyphs, so user-legible fading is not proved. Closed30file/29hash bundle: .ημ/diagnostics/body-trails/hardware-passive-2026-10-06. A separately reviewed bounded follow-camera diagnostic was prepared but DID NOT EXECUTE: its process guard found PID4070721 absent before starting a client. Closed14file/13hash preparation/failure bundle: hardware-follow-2026-10-06. No camera/input/selection/physics change resulted from that attempt. Separate reboot evidence shows current host boot00:21:39UTC; both prior native PIDs are absent. Exact earlier exit cause and lost-world recovery are not established. Preserve open native visual acceptance; no completion or status transition.

---