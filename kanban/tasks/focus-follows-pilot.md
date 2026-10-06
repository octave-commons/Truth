---
category: "specs"
labels: ["domain", "infra", "player", "spark", "spark-flight", "narrowing"]
write-id: "1791322107463-0.swxlfepxctp4fbmmgax"
source: "kanban/tasks/focus-follows-pilot.md"
title: "Focus follows the pilot: bind/resolve/aim a planet while manually flying"
priority: "P1"
status: "in_progress"
estimate: "3"
uuid: "focus-follows-pilot"
created_at: "2026-07-23T00:00:00Z"
---

# Focus follows the pilot

> spark-flight epic, Wave 0 (fast-path to playable loop), THE LINCHPIN. Design:
> `docs/designs/spark-flight-and-camera.md` §7.5. This is the single reason "I fly
> toward a planet but it never resolves into voxels": the resolve pipeline keys off
> the focus point, and focus only auto-tracks in NON-manual camera modes. Fix that
> and the whole voxel payoff becomes reachable while piloting.

## Grounded integration (from design investigation, cite file:line)
- Focus auto-tracks a target ONLY in non-manual camera modes:
  `sync-observer-focus-to-camera` snaps `:focus-position` to the camera target
  every frame (`src/infra/dev/window/loop.clj:128-140`). In `:manual` it does
  not — so while flying, focus is frozen.
- Manual focus aiming is ~20,000× too coarse to be usable: arrows move focus
  3.0e15 m/press (`src/infra/render/input.clj:76-84`) vs `world-focus-radius`
  ≈ 1 AU ≈ 1.5e11 m (`src/law/narrowing.clj`). Hand-aiming a planet is
  impractical; the camera-follow path is currently the only route.
- The resolve pipeline that consumes focus: binding accrues on focus/body overlap
  (`domain.narrowing`, `c/binding` system, `narrowing.clj:113-156`) → commitment
  (`narrowing.clj:319-376`) → voxel band populates+renders
  (`domain.voxel.focus`, `infra.render.scene.voxel`). All gated on focus overlap.
- **Fix:** in `:manual` mode, drive `:focus-position` from the mote — its
  `c/position`, or better its aim/velocity heading a short distance ahead — so
  flying up to a planet accrues binding and lets `G/H/J`/sculpt land on it. Keep
  arrows/`,`/`.` as manual fine-tune overrides (and/or rescale their step to the
  focus radius so they're actually usable). `:focus-position`/`:focus-radius` live
  in the `c/observer` map (`src/domain/player/state.clj:17`); keep the single
  writer discipline for however focus is updated.

## Done when (player-visible via live pm2 window)
- Fly the mote toward a formed planet in manual mode: focus rides with you, the
  binding readout climbs, the world commits, and its voxel band renders — WITHOUT
  switching to a debug/follow camera mode.
- Manual arrow focus nudges are usable (steps scaled to the focus radius), not
  effectively no-ops.
- `clojure -M:test` + architecture-test + `bin/analyze --strict` green.

## Risks
Deciding the focus-follow law (mote position vs aim-lead) affects feel — live-tune.
Must not double-write `:focus-position` (manual override vs auto-follow — pick a
single resolution order). Interacts with the camera work later; keep it simple now
(follow the mote), refine with the chase camera (card 6).

## Dependencies
None hard (works with today's teleport movement). Pairs with `flight-no-jump-accel`
and `voxel-sculpt-verb-palette-wiring` to complete the Wave 0 playable loop.

## Implementation notes (2026-07-23, Wave 0 build)

- **Law chosen:** position-only, no aim-lead — every manual-mode frame the window
  loop enqueues `player/focus-follow` with the player's persistent
  `:focus-offset`, pinning `:focus-position` = spark `c/position` + offset
  (`src/domain/player/focus.clj`, call site `src/infra/dev/window/loop.clj`).
  Lead/tuning deliberately deferred to live pm2 tuning + the chase camera
  (Wave 3 card 6).
- **Resolution order:** there is no competing writer to order against — arrows
  edit the config-side `:focus-offset` (`src/infra/render/input.clj`), never the
  position, so nudge and auto-follow commute and the nudge always lands.
  `:focus-position` keeps one writer per mode (follow intent in `:manual`,
  camera-target sync in tracking modes).
- **Arrow step rescaled** from 3.0e15 m to `focus-nudge-step` = 0.1 ×
  `law.narrowing/world-focus-radius` (~0.1 AU ≈ 1.5e10 m).

## Work notes (2026-07-23)

Implemented + live-verified. `domain.player.focus/focus-follow` (pure,
reads c/position, writes only the c/observer map) enqueued every manual-
mode frame through the intent queue; focus = spark pos + config
`:focus-offset`. No competing writer: arrows edit the OFFSET (commutes
with auto-follow, always lands), step rescaled 3e15 → 0.1 ×
world-focus-radius (~0.1 AU). Tracking modes keep the camera-target sync.
Live: focus rides the spark at ~1-tick lag (verified diff ~2e12 m at
1e26-scale positions during the flight-fling investigation).

## Verification pass (2026-07-24)

Re-verified against the branch; implementation intact. Full suite green
(879 tests / 15486 assertions, 0 failures). Neither this card's files
(`src/domain/player/focus.clj`, `src/infra/render/input.clj`) appear in the
`bin/analyze --strict` HARD-breach list or the cljfmt drift list.

**Gap found and closed:** every LINK was unit-tested but the SEAM was not —
nothing asserted that the `:focus-position` `focus-follow` writes lands
inside the radius the binding gate actually tests
(`law.narrowing/world-focus-radius`, ~1 AU — NOT the 4.0e15 m attention
shell). That is a scale coincidence, and it is exactly the failure mode
`narrowing-worldscale-overlap-gate` was written to fix. New
`test/domain/pilot_resolve_seam_test.clj` feeds `focus-follow`'s real output
to the real `binding-system`: a mote 2 AU out accrues nothing, flown to
0.5 AU the same world binds, and parked it climbs monotonically. It also
pins the arrow-step rescale as a regression (the old 3.0e15 m step is
asserted to MISS) and pins that a freshly spawned spark's default
`:focus-intensity` 0.5 sits exactly ON `focus-intensity-floor` 0.5 — lower
that default and flying at a planet silently stops resolving forever.

**Not green:** `bin/analyze --strict` fails, but for 4 pre-existing HARD
breaches in unrelated namespaces (`domain.stellar.classifier`,
`law.stellar`, `derive-edits`, `voxel-focus-system`) owned by
`epic-static-analysis-cleanup` (which documents this exact baseline). Not
caused by this card and not fixable within it.

---
2026-10-06 scoped 3-point acceptance repair, authorized by root: native period press changed focus intensity 1.0 to 0.25 because infra.render.input/key-callback calls player-key for GLFW_PRESS, GLFW_REPEAT, and GLFW_RELEASE. Existing design docs/designs/spark-flight-and-camera.md section 7.5 and this card require usable manual arrow/comma/period overrides; no focus law, camera, physics, or pacing change is authorized. RED scope: invoke the actual GLFWKeyCallback with press/repeat/release, verify one focus adjustment per physical press and ordinary held-key tracking, use the production IntentAtom and drain-intents to verify publication is queued and position/velocity remain unchanged. Native observation/provenance is in .ημ/diagnostics/playable-foundation/manual-flight/; research note docs/notes/2026-10-06-playable-gate-route-audit.md records the defect on the integrating branch. Hold JVM execution until the benchmark owner releases; root must commit observed RED before production code. Acceptance for the original full manual fly-bind-commit-voxel chain remains outstanding after this narrow repair.

2026-10-06 observed RED at source base 6944df4 after explicit benchmark release: clojure -J-Xms256m -J-Xmx2g -M:test -n infra.render.input-test exited1; 9 tests,86 assertions,23 expected failures,0 errors. Real GLFW callback repeats/release add extra arrow and queued comma/period actions. Existing tests and held-flight-key control pass. Final scoped clj-kondo0 warnings/errors; formatting applied and diff check clean. Evidence .ημ/diagnostics/focus-input/red-final.log and red-verdict.md; final log SHA256 49ca1bd873d605a912a48147f11f4777653fb1ee66a360a2de2d97746e87c83f. Production source unchanged. Waiting for root RED commit before minimal guard repair; original full manual fly-bind-commit-voxel acceptance remains open.

2026-10-06 GREEN against committed RED558476c: added only GLFW_PRESS guard to existing player-key dispatch. Held movement-key tracking and all focus/camera/physics/palette semantics unchanged. Focused callback + window + architecture command passed20 tests131 assertions0 failures0 errors, exit0; cljfmt0, kondo0 warnings/errors, Splint1.24.0 checked2 files0 warnings, diff check clean. Evidence .ημ/diagnostics/focus-input/green-focused.log and green-verdict.md; log SHA256 a51be989b4b651a3fc09d0851e3dfb45367d2090a339bdc060da67158c98b6f7. All focused JVMs reaped; root owns checkpoint and integrated full gates. No native input performed; full manual fly-bind-commit-voxel acceptance remains open.

2026-10-06 root-authorized continuation of existing 3-point acceptance repair at source 85f307f, isolated codex/truth-focus-cadence. Observed ordinary native manual focus/body gap was 2935.70 AU while thrusting and 601.66 AU after release; render approximately 1.3 fps versus simulation approximately 6.9 ticks/sec. Current render-enqueued focus closure reads position at drain time, but there is no refresh on intervening sim iterations. Scoped correction: after serial intent drain and before the frozen simulation fold, resolve manual focus from the current published Spark position plus current host focus-offset using existing player/focus-follow; tracking camera mode retains camera-target focus. Remove redundant render-enqueued manual follow. RED must exercise the actual sim-loop with actual integrator and binding across multiple no-render ticks, current mode/offset, queued radius/intensity changes, missing observer/position, error/hold behavior, and containment of follow failures without losing drained changes. No new ECS writer, alternate clock, force, teleport, camera law, or position writes. This closes consumer cadence only: published focus still reflects pre-fold position, and sculpt/action anchors already captured during intent drain/render remain a separate limitation. Full native manual fly-bind-commit-voxel acceptance remains open. Root must commit observed RED before production changes.

2026-10-06 observed focus cadence RED at unchanged production 85f307f: actual sim-loop without render frames plus actual integrator/binding fan-out ran 6 tests,70 assertions,24 expected failures,0 errors,exit1. Co-moving bodies remain half an AU apart but binding decays [0.02,0.018,0.016,0.014] under both zero and nonzero COM shifts. Current offset/manual-mode and follow-failure visibility also fail; physical/input/tracking/missing-data/continuation controls pass. Kondo and cljfmt pass; configured Splint check covered292files with0warnings; diff check passes. Independent source review has no blocker. Evidence .ημ/diagnostics/focus-cadence/red-verdict.md; red.log SHA256 7afc60499cd968ea1b31dc60b08a48f7a45ede8f132b1e0cc5a2b17f50ae9114. Root checkpoint required before source fix. Published one-tick/recenter lag and already queued sculpt/action anchors remain explicit limits; original live acceptance still open.

2026-10-06 GREEN after committed RED10f1a817: shared existing private guarded intent application between drain and after-drain manual focus preparation; current cfg mode/offset now reaches each pre-fold snapshot; removed only render manual follow enqueue. No pure focus law, physical writer, tracking camera or clock change. Focused6 namespaces45tests282assertions0failures/errors exit0; touched kondo/fmt/Splint3files0warnings/diff check all pass. Independent review no blockers. Closed RED11checksums unchanged; new evidence .ημ/diagnostics/focus-cadence/green/verdict.md; focused log SHA256 1d4a2b31808a54e4914a598833e6c85a6bedc177c5d055c29379dc4a45916f1e. Root owns checkpoint/full gates/native follow-up. Published one-tick/recenter lag and previously captured sculpt/action anchors remain explicit limits; full live card acceptance still open.
2026-10-06 closed native control evidence, with original manual gameplay acceptance still open. Ordinary seed-42 nebula launched from 22f762f (production source equivalent to 400ba3a), using real GLFW input and published readbacks. Right held and released leaves exactly one 0.1-AU offset; comma changes focus intensity 0.5 to 1.0 once; period held/released changes 1.0 to 0.5 once. Snapshot03 already contains the subsequent comma action, so it proves unchanged arrow offset, not pre-comma intensity. Actual menu clicks preserve Cruise 3e14 -> Fine 1e7 -> Cruise 3e14, retention 0.97 and focus. This verifies the narrow PRESS-guard repair above RED558476c; no new focus law was introduced.

Evidence: .ημ/diagnostics/focus-input/native-400ba3a/README.md, snapshots02-09, controls.mp4, controls-full-window-8x.gif, manifest.json, and SHA256SUMS. The closed inventory has92 paths including its hash file, with91 hashed files verified. Active runtime.log/xvfb.log are excluded. Integrated full source400ba3a previously passed928 tests15913 assertions and all six strict gates; no fresh transition gate is claimed by this comment.

Remaining acceptance is substantial: no manual fly-overlap-bind-commit-voxel or sculpt success. Actual later manual snapshot23 completed during held D at tick12029 (despite its requested-before filename): vx46535.8m/s and focus-to-Spark lag2935.70AU; released snapshot24 at12102 has vx17307.1m/s and lag601.66AU. These measured lags include software rendering and concurrent verification load; they do not establish a universal cadence claim. Read-only cadence investigation must distinguish missed render-frame refresh from ordinary frozen-snapshot motion. Do not substitute follow-camera attention or fixture placement. Card remains in_progress.

2026-10-06 root locally composed committed cadence GREEN021ef9d with frozen native evidence PR17 head74fe6df. Source changes remain exactly guarded pre-fold manual attention preparation, its regression tests and explanatory docs. Git conflict resolution preserves both parents of every ledger as ordered line subsequences and every card comment; newest preexisting write-id retained until this canonical append. No card transition or native runtime replacement; integrated full suite and strict gate start next.
---