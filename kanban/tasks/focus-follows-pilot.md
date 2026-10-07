---
category: "specs"
labels: ["domain", "infra", "player", "spark", "spark-flight", "narrowing"]
write-id: "1791395267342-0.0q2x70v6i6c3uwyu377"
source: "kanban/tasks/focus-follows-pilot.md"
title: "Focus follows the pilot: bind/resolve/aim a planet while manually flying"
priority: "P1"
status: "review"
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

2026-10-06 integrated source1ad081475364cc3b75db76142204cbf54b5bf60c passes clojure -M:test:934tests15983assertions0failures/errors,exit0; bin/analyze --strict all6blocking stages pass,exit0. Closed9file/8hash proof .ημ/diagnostics/focus-cadence/full-1ad0814. Production source matches GREEN021ef9d and unchanged committed RED10f1a81 test. Native service ran concurrently, so duration is not isolated performance evidence. Native pre-fold cadence and bounded actual-host cost remain next; published lag and queued-action anchor limits remain. No transition or full manual fly-bind-commit-voxel/sculpt/Gate completion.

2026-10-06 PR19 review repair on exact source b402939931575e377ae4409d9cd4061dff11df06, scoped to the existing InProgress 3-point card.

CURRENT IMPLEMENTATION; supersedes the historical problem/implementation descriptions above without rewriting their evidence: the opening and Grounded integration claims that manual focus is frozen describe the pre-Wave-0 defect. The Implementation notes and Work notes dated 2026-07-23 describe the former render-frame enqueue mechanism; they are historical, not current instructions. Since committed cadence correction 021ef9d, infra.dev.window.loop/sim-loop drains ordinary intents through shared guarded application, then prepares manual focus from the current Spark position plus host focus-offset before each frozen simulation fold. The render loop no longer enqueues manual focus-follow. Tracking camera modes retain camera-target focus. Arrow nudges alter only the host offset; the PRESS guard applies each arrow/comma/period action once per physical press. Current attention follows every simulation iteration, rather than remaining frozen or waiting for a render frame. Published focus may still precede the fold's physical movement/recentering, and previously captured sculpt/action anchors are not recomputed. Ordinary manual fly-overlap-bind-commit-voxel/sculpt/Gate acceptance remains open; no completion or new native evidence is claimed here.

Verified CodeRabbit review5435441692: thread PRRT_kwDOTDahac6psBWz/comment4201223882/cr-comment:v1:8f4f5b3d02fea9a8d731e9b2 concerns the superseded prose above; body item cr-comment:v1:e80529ff9d690313af12854c identifies the unvalidated host focus-offset crossing at loop.clj:137. AGENTS requires a named Malli validator at this boundary. Reuse the existing finite-three-coordinate predicate, add a focus-specific named compiled validator, and reject malformed/nonfinite offsets inside the existing guarded attention update before entering the domain function. Preserve the absent-key zero default, valid list/vector offsets, queued updates, tracking mode, physical state and visible failure containment. RED will run the actual sim-loop/IntentAtom boundary with malformed offsets, valid/default controls and recovery after a valid host correction. No new focus law, clamp, fallback, camera, world writer or pacing rule. Draft tests only until the cost owner releases JVM execution; root must commit observed RED before production edits. Canonical Rheos comment via reviewed dedicated upstream CLI83c6b397; no status transition or hosted settlement.

2026-10-06 actual PR19 focus-offset RED at unchanged production b402939931575e377ae4409d9cd4061dff11df06: clojure -J-Xms256m -J-Xmx2g -M:test -n infra.dev.focus-cadence-test exited1; 11 tests,164 assertions,23 expected failures,0 errors. Malformed offsets enter the pure domain before validation, nonfinite offsets corrupt attention, and the existing errors do not name the focus-offset boundary. Valid sequential coordinates, absent-key zero default, continuation, later valid-setting recovery and tracking controls pass. No missing-symbol or runner error supplies RED. JVM4149723 reaped. Touched kondo0 errors/warnings and diff check pass; formatting/full/strict/native gates not claimed. Evidence .ημ/diagnostics/focus-contract-review/red/VERDICT.md; raw log SHA256 2e6421ab6c4872cb553ebd184a5e96e52aefaa7db2229893bfe0c0d9c924a4eb. Root must commit this RED before production changes. Original manual gameplay acceptance remains open; no transition or hosted settlement.

2026-10-06 PR19 review repair GREEN after committed RED2b30e5055bc793e4c9152425356f535ba3e5d2f6. Actual manual host preparation now invokes named compiled Malli law.narrowing/focus-offset? before domain focus-follow, inside the existing guarded update. Reuses finite-vec3?; list/vector support and absent-key zero default are preserved; explicit nil, malformed and nonfinite values reject visibly without clamping config or corrupting the drained world. No new focus/physics/camera law. Design section7.5 names the boundary and links RED. Independent board_scout source review verified actual validator use and found no blockers; no hosted approval is implied. Focused5 namespaces37tests313assertions0failures/errors exit0. Final source, including the public docstring spacing correction, then passed full ordinary suite939tests16077assertions0failures/errors and all6 strict stages, both exit0; jscpd1.53percent within1.7percent. Exact commands/source hashes/logs are in .ημ/diagnostics/focus-contract-review/green and full; full tests log904f684bb5a1e9aa1f5faa11efe6f078adeadeb2185ff64be986ec98bb4e5808, strict log39848a6572115cab5454b6c6118eb2a3c40e97bb7352c32943587e390b83a197. All owned JVMs reaped. RED11files/10hashes unchanged. Qualification overlapped other root-authorized work; no isolated performance/native acceptance claim. Historical-prose supersession above remains the current implementation statement. Card stays InProgress3, full manual fly-bind-commit-voxel/sculpt/Gate acceptance open; root owns publication and review settlement.

2026-10-07 root-authorized design-only continuation (at most3 points) of this existing InProgress3 owner, based on exact760ea79d017f4e012943789f896dfdfdb0a173b1. No body/status change or implementation admission. Proposed §7.5 amendment and docs/notes/2026-10-07-manual-sculpt-aim-timing.md separate current source facts from a manual sculpt consumption-time aim contract.

Current seam: actual T/Shift+T/Y callback queues request-op; sim-loop drains it before preparing manual focus; request-op can combine prior published focus with current target position. Proposed first slice resolves each new manual sculpt aim from that serial world plus the single mode/offset snapshot for its simulation iteration, before existing domain gates/spending/record creation. Existing controls, committed-target selection, costs, magnitude, radius/intensity, FIFO payments, tracking path, created records, physical writers and Jacobi/barrier timing remain. G/H/J renderer-captured placement is a separate known seam outside this first slice.

Proposed boundaries require design review: same host snapshot for all requests sharing a drain (including mid-drain arrivals), mode/offset changes after that read taking effect next iteration, manual precedence over earlier queued camera position, and visible rejection of invalid offset or unavailable Spark position before charging rather than stale-aim fallback. Missing observer/commitment and denied palette/Resonance retain existing no-op behavior. Config/queue/world have no joint atomic capture; no keypress-exact aim claim. Smallest uniform host mechanism remains unadmitted.

Later RED matrix must use real callback + IntentAtom + sim-loop after actual integrator movement/recentering, establish current relative aim, preserve FIFO/focus-shape/one-charge and immutable records, pin snapshot versus mid-drain host changes and tracking, and exercise failures/recovery/domain gates. This planning task writes no tests/source and performs no JVM/native/runtime action. Existing original manual fly-bind-commit-voxel/sculpt/Gate acceptance remains open. Root owns review, checkpoint and publication; no new unrelated card or feature readiness claim.

2026-10-07 planning clarification, superseding the timing/mechanism choices in the earlier design-continuation comment without rewriting it. Root selected PERSISTENT attention preparation inside the single guarded contextual sculpt operation for review. This is proposed design, not active source or implementation admission.

Final draft: docs/notes/2026-10-07-manual-sculpt-aim-timing.md and the proposed §7.5 subsection. Dev-window input explicitly opts into submission of a named, validated contextual envelope on the SAME queue/drain/guard. Each receives only immutable mode/offset projected from the one iteration config snapshot C_n and the serial world W_j after earlier intents. Existing unary/IFn entries (including map/keyword callables), reset intents, and public plain-Atom/no-capability dispatch retain their behavior. Invalid supplied capability/context never falls back to direct/stale-focus dispatch. No callable/Atom identity detection, generic command registry, all-entry conversion, hidden host dereference, or new queue.

Chosen semantics: missing observer returns W_j unchanged before aim validation. Otherwise, in manual mode validate offset/position availability, apply existing focus-follow to prepare P_j, then invoke unchanged request-op in the SAME guarded operation. Invalid offset/missing position or an exception preserves pre-operation W_j and uses existing stderr diagnostics. Valid-aim domain denial returns P_j with refreshed attention but no charge or paid record; it is not a promise that W_j is unchanged. Later queued readers intentionally see refreshed attention. Later reset/camera writes still execute FIFO; final after-drain prep remains. Tracking performs the existing request-op without prep. No new domain arity or duplicated gate logic. Controls, prices, magnitude, committed-target selection, physical ownership, paid-record geometry and Jacobi/barrier timing stay unchanged; focus/mode changes do not retroactively recompute paid records.

The note now compares pre/post refresh, blanket per-intent refresh, and the selected explicit contextual entry. It retains action-local aim as a considered alternative only. Same-C_n timing for mid-drain arrivals and host changes is explicit, with no cross-store atomic or keypress-exact claim. RED must exercise opted-in actual callback + queue + host loop, legacy compatibility/interleaving, real movement/recentering, validation precedence, prepared-versus-original world outcomes, payment once, and recovery. A bounded 3-point repair is credible only with this limited adapter and existing domain composition; broader queue/API work requires another breakdown before admission. No source/tests/runtime/native work, status transition or readiness claim. Root owns publication and hosted review.

2026-10-07 bounded prose clarification to the preceding planning comment: select C_n mode first. The early missing-observer no-op before aim validation applies ONLY in manual mode. Nonmanual/tracking dispatch delegates directly to unchanged request-op, with no new observer shortcut; its existing verb/magnitude validation still precedes its observer gate. The note mechanism, RED matrix and design paragraph now state this consistently. Persistent manual preparation, all other proposed semantics, InProgress3 status and implementation non-admission are unchanged. No source/test/runtime work or history rewrite.

2026-10-07 documentation-only PR36 review correction at 47571e474e24838b94e0902900ce9cc882f71d76. Verified full MiMo review5436893734 and native thread PRRT_kwDOTDahac6pu08y/comment4202416214: the pinned-source table in docs/notes/2026-10-07-manual-sculpt-aim-timing.md cited loop.clj lines 357-360, which are menu/tracking code. Corrected only that range to 361-364, the actual action-request/observer-focus/intervention-place block at pinned source760ea79d. Current loop bytes equal the pinned source (SHA2563994a14ddb3fd052e7e9e4f20c4995422905d01f89a1d43877f634a8482c4067). The row substance, proposed design, implementation non-admission and historical card body/comments remain unchanged. Local relative-link existence and design-fragment checks pass; no source, tests, config, runtime, native actions or JVM execution. Card stays InProgress3. Root owns parent composition, commit/push, new-head hosted qualification and native review settlement; this comment is not a hosted pass or thread resolution.

Root admits the concrete PR36 manual-sculpt contextual-intent slice under existing focus-follows-pilot InProgress3. Fresh canonical pr-flow status at exact7188fbab1400589c5a3ccadb560df6b5120a5f9b PASS:7checks,currentCR/MiMo,one available-agent cohort,all findings settled; authenticated optional Codex quota6029473068 creates no approval credit. Root read proposal7779796849a5c17de2ecccfbaf5d1d9824506d65f6b4069dfc7889e05c82fd0c and actual loop/input/focus/position boundaries. Accept THREE production paths: named pure law.input-intent.cljc, nominal ContextualIntent beside existing IntentAtom in loop.clj, explicit submit-contextual! capability in input.clj. Same queue/FIFO/Throwable guard; one immutable projected mode/offset per drain; manual current serial Spark+offset preparation before unchanged request-op; tracking delegates unchanged. Preserve legacy IFn/map/keyword/reset/plain-Atom dispatch, absent versus invalid capability, manual observer/offset/position failure order, prepared-world denial versus exact prior-world exception, one charge and physical ownership. Retain3points; broader queue/protocol/domain API requires rescope. Author laws/tests first; no production GREEN before meaningful observed RED and committed checkpoint. Native operator owns current resource lane: NO JVM/native/tests requiring JVM until root releases after closure. Offline authoring and lightweight file/static checks permitted. Full ordinary suite,actual host cost,all6 strict gates and later code-stage review remain required; original natural fly/bind/sculpt/Gate acceptance open. No merge or auto-merge authority.

2026-10-07 admitted sculpt-context RED checkpoint preparation. Root reviewed the complete laws, tests and execution and accepted this observed RED before production implementation. At source7188fbab1400589c5a3ccadb560df6b5120a5f9b, one bounded256MiB/2GiB Java21 run of the seven proposed focused namespaces completed56tests380assertions with13 expected missing-contextual-API failures and0errors, exit1/reaped in11.74s. All13 failures explicitly name absent enqueue/record/request APIs; their deeper contextual spec bodies have not yet executed. The separate real legacy callback -> IntentAtom -> sim-loop -> production integrator characterization passed its assertions that the paid anchor follows stale published focus and differs from current Spark-relative aim under zero and nonzero frame shifts. This is a passing compatibility/baseline observation, not thirteen numerical failures or a native gameplay trace.

New named EDN/Malli laws plus two test namespaces cover the admitted nominal payload/capability, legacy IFn/map/keyword/reset behavior, one immutable iteration context, FIFO worlds, persistent preparation/payment/denial/rollback, press-only dispatch and tracking validation order. Production loop/input remain byte-identical (3994a14d/d8af828b); all304 pinned source/test inputs remained unchanged during execution. Kondo0errors/0warnings and parser-only reads pass. MainPID1192905 and tools.deps child1192912 are absent. Existing window-test boom stderr is intentional and preserved; the earlier pin inventory missing resources directory is also preserved, not called a product failure.

Closed evidence: .ημ/diagnostics/action-aim-protocol/red; manifest SHA25646ae4efcf18c45278951d78045697460444938f8a63be530d647e5543743b431,19ownedpaths/18hashes reverified. Root authorizes explicit RED commit followed by the already-admitted three-path GREEN offline; no new JVM/full-suite/strict/native run while the life-account gate owns that slot, and no push or hosted action. InProgress3 and original natural fly/bind/sculpt acceptance remain unchanged. Root owns next resource release and publication.

Sculpt focused GREEN attempt1 at frozen loop ef70bad85801842314c31afb5fd4875ca0e6ce3f50e694f864a9fe4ab55fb789 / input67b098214f84c75e2436e14880b953ed70bc33ace4034ad460edf918be1ab59f FAILED:56tests544assertions,2failures0errors. Both new callback-matrix expectations incorrectly assume uplift price for volcanism and erosion; actual balances17 and18.5 match unchanged law.voxel coefficients at magnitude0.5. Expected uplift18 passes. Prior RED missing-API guards had not reached this body. No production/test repair or further test/gate has run. OwnedJava21 PID/PGID1328076 reaped/absent at16:13:39.522568Z after11.3166s; all304pins unchanged. Raw stdout/stderr/execution and immutable input text saved under .ημ/diagnostics/action-aim-protocol/green-qualification. Canonical InProgress3 retained; Review gate not invoked. Root informed of narrow explicit per-verb expectation repair proposal; no native/push.

Sculpt contextual-input GREEN qualification: corrected explicit per-verb test balances 18.0/17.0/18.5 after preserved focused attempt01 oracle failure; focused02 passed 56 tests/544 assertions, 0 failures/errors. Canonical InProgress→Review gate01 then passed full suite 967 tests/17006 assertions, 0 failures/errors, but refused on two owned Math/sqrt style findings and formatting of test/infra/dev/sculpt_intent_test.clj. All19 observed child PIDs absent/reaped; production source pins unchanged. Root authorized test-only clojure.math/sqrt require/calls plus formatting (current test SHA256 2bb8dd3c4a5204444576161463060c44702af211cf315f338882dafdb7828ab8); touched BB formatter and kondo pass. Raw gate01 and pre-repair test bytes remain preserved under .ημ/diagnostics/action-aim-protocol/green-qualification. One canonical retry follows; no native or performance claim, no original RED relabeling.

Sculpt contextual-input GREEN qualified at RED commit f0c4710a8306aa634af9f49859c11451515ed912 plus reviewed production loop ef70bad85801842314c31afb5fd4875ca0e6ce3f50e694f864a9fe4ab55fb789 and input 67b098214f84c75e2436e14880b953ed70bc33ace4034ad460edf918be1ab59f. Corrected focused02: 56 tests/544 assertions/0 failures/errors. After preserved gate01 style refusal and authorized test-only math/format correction (test SHA2bb8dd3c4a5204444576161463060c44702af211cf315f338882dafdb7828ab8), canonical gate02 ran clojure -M:test (967/17006/0F0E) then all six bin/analyze --strict gates successfully. Rheos admitted in_progress→review at2026-10-07T16:25:50Z. MainPGID1410395 and all17 observed child identities absent/reaped;304source/test/deps pins unchanged; heap256MiB/2GiB;100.100s under1500s bound. Evidence .ημ/diagnostics/action-aim-protocol/green-qualification. Original RED remains13 absent-API failures plus independent stale-anchor characterization; the guarded matrix had not executed its later faulty per-verb oracle. Failed focused/gate/format commands and exact prior bytes preserved. No native capture, host-loop cost, FPS, hosted successor approval, merge or Done claim. Performance characterization remains a separate bounded qualification before publication decisions.

Root releases one baseline-only host cost attempt under repaired frozen profile d8c19cf431d757a64684055459b066424d83b11f6e8dc48620972d6d2c48e4fe after source and12offlinefakeproc/process tests review. 170swork/180stotal includingcleanup, no automaticretry/extension. Originalfailedbaseline preserved; supervisor1e166af5 fixes optionalmetadataPermissionError, durablyjournalsownership, unknownmembershipfailsclosure. Nativecursorattemptclosed17:30:46; rootclosure7a896979 andall259PIDsabsent reverified. All182productionpins unchanged. Command python3 .ημ/diagnostics/action-aim-host-cost-02/supervisor.py baseline baseline-02. Candidate separate release only after qualifiedbaseline. RootownssolefiniteJVMlane; retainedprimaryuntouched. No performance or gameplay claim before evidence.

Host-cost repaired baseline-02 COMPLETE57.637810s exit0 at17:40:47UTC, durablePGID1846490/start6228964 reaped and child1846497absent. All14artifacthashes/prep/source verified; no cleanup errors/unknown membership. Root separately releases one candidate-01 against exact baseline checks and frozen d8c19cf4, 170swork180total, no retry/extension. Four actualFIFO cases/defaultCriterium and exactwholeoutputEDN guards unchanged. No performance claim until matched result reviewed.

Matched host-cost baseline02 and candidate01 completed in57.638s/58.336s under170work180total, each all14artifacthashes verified and ownedPIDs reaped/absent. Checks.edn byte-identical. MedianCPU microseconds empty0.025→0.047, legacy16 5.63→4.47, sculpt1 4.48→10.05, sculpt8 39.38→91.24. Allocations +64B empty/legacy,+13440B oneSculpt,+107128B eightSculpt. These are measured increases, no speedup/FPS/tickclaim; variance high and no predeclared numericthreshold. Root accepts publication for review of bounded on-demand serialinputcost with correctnessguards retained. Costbundle includes118originals826803B losslessly, includingfailedfirstsupervisor/RED/repair/actualruns; rawfilesunchanged. Existing focused56/544/full967/17006/all6strictPASS sourcepins remain unchanged. No new JVM or native release; no gameplaycompletion.

---