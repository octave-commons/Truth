---
category: "specs"
labels: ["domain", "infra", "render", "spark-flight"]
write-id: "1791325421315-0.i4cgynxic5pzxnnm86u"
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

Planning follow-up: Incoming3-point child UUID3a501e16-f8fe-41a3-bdd1-760e503dd84d proposes an ordinary View magnetic-visibility control (fresh-window Off), covering both dipole loops and short star/protostar vectors. Design docs/designs/magnetic-field-visibility.md links native pixels/source grounding at docs/notes/2026-10-06-magnetic-visibility-evidence.md; frozen media remains at607c4361032816ecb0fafd57f8c77946eea7d898. This child addresses an observed obstruction to star/planet trail readability without changing trail history/alpha, physics, camera or selection. HUD layout and quiet telemetry retain existing flight-hud-and-cues child ownership. Parent stays in_progress and visual fading acceptance remains open; child stays Incoming for planning review, not implementation.

Applied the merged upstream Rheos comment formatter from open-hax/rheos PR2 (ef3c4abf1ea75199486f693e9470df3fec88dd49; CLI SHA256 83c6b397278d418d69ce6509b8c3d9fe87e88cca9141b28143d9efb5d78a75a7) for Truth PR18 review comment 4200839294. Existing body/comment content and section types are preserved; separators now remain horizontal rules rather than turning final prose into a heading. Historical ledger bytes are retained, and this repair is one new canonical comment event. Status, scope and acceptance remain unchanged.

---