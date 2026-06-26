# docs/notes index

Large agent-conversation exports have been split into smaller, topic-bounded chunks. Originals are archived in `archive/`.

## 2026.06.23.20.01.16.md

- `2026.06.23.20.01.16-001-best-fit-stack.md` (927 lines) — Best-fit stack
- `2026.06.23.20.01.16-002-μ0-shapes-claims-contracts.md` (491 lines) — μ0 – Shapes, Claims, Contracts
- `2026.06.23.20.01.16-003-shapecore-shapes-claims-events-ids.md` (379 lines) — `shape.core`: Shapes, Claims, Events, ids
- `2026.06.23.20.01.16-004-lawledger-immutable-event-ledger-merkle.md` (451 lines) — `law.ledger`: immutable event ledger + Merkle-ish root
- `2026.06.23.20.01.16-005-μ4-spatial-primitives-3d-vectors-aabbs-o.md` (541 lines) — μ4 – Spatial primitives (3D vectors, AABBs, octants, bodies)
- `2026.06.23.20.01.16-006-shapespatial-vectors-aabbs-octants-bodie.md` (446 lines) — `shape.spatial`: vectors, AABBs, octants, bodies
- `2026.06.23.20.01.16-007-finishing-domaingravitybarnes-hut.md` (500 lines) — Finishing `domain.gravity.barnes-hut`
- `2026.06.23.20.01.16-008-the-ecs-contract.md` (345 lines) — The ECS contract
- `2026.06.23.20.01.16-009-the-event-model.md` (533 lines) — The event model
- `2026.06.23.20.01.16-010-domainecsrewindable-the-protocol.md` (437 lines) — `domain.ecs.rewindable` — the protocol
- `2026.06.23.20.01.16-011-lawecs_dslclj.md` (418 lines) — `law/ecs_dsl.clj`
- `2026.06.23.20.01.16-012-μ-specs-first.md` (337 lines) — μ-specs first
- `2026.06.23.20.01.16-013-μ-specs-domainecscore_testclj.md` (445 lines) — μ-specs — `domain/ecs/core_test.clj`

## 2026.06.25.16.41.16.md

- `2026.06.25.16.41.16-001-the-core-vision-truth-as-a-physics-first.md` (304 lines) — The Core Vision: Truth as a Physics-First Universe
- `2026.06.25.16.41.16-002-why-this-matters.md` (540 lines) — Why this matters

## 2026.06.25.22.11.59.md

- `2026.06.25.22.11.59-001-core-model.md` (511 lines) — Core model
- `2026.06.25.22.11.59-002-protostar-from-collapsing-core-to-first.md` (494 lines) — Protostar: from collapsing core to first burning

## architecture-exploration.md

- `architecture-exploration-001-the-simulation-is-moving-too-fast-for-wh.md` (303 lines) — The simulation is moving too fast for what I want. I want the time steps to be smaller, I want there to be a clock available to the user that is aware of how time will get slower and faster as the observable complexity increases. I want to have a UI for how much mass is in the solar system, I want to know average temperature, entity counts. The simulation looks good and we are gamifying it now. We want to create a sense of awe as the star system forms from the nebula, so it has to happen slow enough to be explored but fast enough controlling it is hard.
- `architecture-exploration-002-text-rendering-is-the-key-gap-the-hud-on.md` (327 lines) — Text rendering is the key gap — the HUD only draws solid NDC rectangles, no glyphs. But `lwjgl-stb` is already a dependency, so `STBEasyFont` gives us ASCII text cheaply. Let me check the HUD shader and how the clock value currently flows.
- `architecture-exploration-003-leapfrog-symplectic-tier-quantized-dt-ch.md` (301 lines) — Leapfrog (symplectic) — tier-quantized `dt` changes (only at phase transitions) are safe for it. And the only test pinning the time contract is `test-time-scale` (asserts `time-scale` ∈ [1e10,1e12] and monotonic decrease) — I'll update that contract deliberately since the semantics change from "sim-s per tick" to "sim-s per real-second" (the clock rate).
- `architecture-exploration-004-type-metadata-cant-attach-to-a-nil-liter.md` (326 lines) — Type metadata can't attach to a `nil` literal. Bind a typed local instead:
- `architecture-exploration-005-no-glviewport-call-exists-anywhere-thats.md` (312 lines) — No `glViewport` call exists anywhere — that's the bottom-left-quadrant bug: the framebuffer is larger than the assumed 1280×720 (HiDPI/window resize), but we never set the viewport or update it on resize, so GL keeps drawing into the original 1280×720 region. Let me read the physics/law files and the render-scene head before fixing.
- `architecture-exploration-006-now-the-viewport-fix-set-the-viewport-fr.md` (193 lines) — Now the viewport fix — set the viewport from the actual framebuffer size each frame:

## claude-physics-merge.md

- `claude-physics-merge-001-docsnotes20260625221159md-you-can-think.md` (329 lines) — @docs/notes/2026.06.25.22.11.59.md You can think of Phase 0 as “turning on” a coupled gravity–hydro–MHD–thermal system and then watching which regime dominates at each point in space. Below is a compact set of equations you can actually implement, plus the dimensionless diagnostics that tell you whether gravity, pressure, magnetic fields, or radiation are in charge locally, and how that carries through into planet interior convection, tectonics, and atmosphere–field coupling. [wwwmpa.mpa-garching.mpg](https://wwwmpa.mpa-garching.mpg.de/~henk/mhd12.pdf)
- `claude-physics-merge-002-let-me-read-the-rest-of-the-physics-note.md` (356 lines) — Let me read the rest of the physics note (planet interior + atmosphere) and the components namespace so the doc grounds in the real ECS component vocabulary.
- `claude-physics-merge-003-now-i-understand-the-substrate-its-n-bod.md` (325 lines) — Now I understand the substrate: it's N-body point-like clumps with thermodynamic state, not a spatial grid. Let me read the existing tests so I don't break them, and confirm what behavior is asserted.
- `claude-physics-merge-004-all-88-pass-the-existing-sim-still-ignit.md` (329 lines) — All 88 pass — the existing sim still ignites and forms planets with EM in the loop. Now tests for the two new namespaces:
- `claude-physics-merge-005-that-log-is-revealing-something-importan.md` (350 lines) — That log is revealing something important. The running dev window does **not** use the code I changed. Look at `infra.dev.server`:
- `claude-physics-merge-006-now-i-have-the-complete-map-there-are-ac.md` (308 lines) — Now I have the complete map. There are actually **three** parallel pieces, not two:
- `claude-physics-merge-007-clean-only-the-ecs-path-and-one-renderer.md` (319 lines) — Clean — only the ECS path and one renderer remain. Now restore test coverage for the ported render logic with a proper `infra.render` test:
- `claude-physics-merge-008-enrich-the-ecs-nebula-iterate-until-we-s.md` (301 lines) — enrich the ECS nebula, iterate until we see a star system emerge
- `claude-physics-merge-009-done-the-ecs-nebula-is-now-full-lush-and.md` (322 lines) — Done — the ECS nebula is now full, lush, and a star system visibly emerges, all on the single ECS path.
- `claude-physics-merge-010-now-mark-the-parallelism-tasks-done-and.md` (304 lines) — Now mark the parallelism tasks done and rework `domain.phase0` — the emergent seeder and pipeline. Let me read the current seeding block to replace it exactly:
- `claude-physics-merge-011-now-update-the-harness-to-watch-emergenc.md` (304 lines) — Now update the harness to watch emergence with the field readout, and run it:
- `claude-physics-merge-012-now-add-the-malleability-constant-to-law.md` (9 lines) — Now add the malleability constant to `law.stellar`:

## formation-rendering-investigation.md

- `formation-rendering-investigation-001-the-formation-of-planets-and-suns-jumps.md` (321 lines) — The formation of planets and suns jumps around a lot, I'm not sure what is going on if our gas rendering is misleading, or what is going on. Only one sun ever spawns, every mass passes through the sun. The cloud of gas that makes the stars and the planets seems to be smaller than the bodies they spawn. The sun, I'm not sure, that could be right, fusion triggering would make it get bigger but... it still doesn't look good for a game.
- `formation-rendering-investigation-002-now-let-me-look-at-the-collision-detecti.md` (329 lines) — Now let me look at the collision detection and the renderer.
- `formation-rendering-investigation-003-the-user-wants-physics-coupled-visuals-a.md` (326 lines) — The user wants physics-coupled visuals and points me to the docs. Let me read them before touching anything.
- `formation-rendering-investigation-004-i-have-a-complete-picture-now-the-fixes.md` (330 lines) — I have a complete picture now. The fixes are interlocking and your spec mandates law→test→implementation, so I'll work in dependency order and verify with both the test suite and the headless probe. Let me track the work and start implementing.
- `formation-rendering-investigation-005-all-four-issues-are-fixed-with-tests-and.md` (321 lines) — All four issues are fixed, with tests, and the full suite is green (134 tests, 345 assertions, 0 failures). Here's the before → after.
- `formation-rendering-investigation-006-now-i-understand-the-playerobserver-mode.md` (230 lines) — Now I understand the player/observer model. I'll tighten the visuals and add an interactive interface: the player **spark**, a **focus reticle** tinted by coherence, **keyboard control** of focus/drift/release, and a **HUD coherence bar**. Let me start with the visual fixes — gas shows temperature (physics), bodies show composition, and tone down the star bloom:

## phase-0-design-exploration.md

- `phase-0-design-exploration-001-docsdesigns-docsnotes20260625164116md-ex.md` (320 lines) — @docs/designs/ @docs/notes/2026.06.25.16.41.16.md explore this work and help me out here. I want to see phase 0 complete, from a nebula, to a planet forming to be sculpted for life.
- `phase-0-design-exploration-002-now-let-me-create-the-stellar-physics-sy.md` (321 lines) — Now let me create the stellar physics system. I'll start by creating the law schema for stellar objects:
- `phase-0-design-exploration-003-let-me-also-fix-the-normalize-reference.md` (240 lines) — Let me also fix the normalize reference in phase0.clj:

## phase-0.md

- `phase-0-001-docsdesignstruth-phase-0-stellar-nebula.md` (324 lines) — @docs/designs/truth-phase-0-stellar-nebula-design.md @docs/notes/2026.06.25.16.41.16.md we got started here, but we created 2 simulation paths.
- `phase-0-002-i-see-exactly-what-happened-you-have-two.md` (341 lines) — I see exactly what happened. You have **two parallel simulations that don't share a world model**, and `infra/main.clj` is currently straddling both. Here's the split:
- `phase-0-003-i-now-have-the-full-picture-of-both-path.md` (351 lines) — I now have the full picture of both paths. Before I start moving code, let me confirm how the project builds and tests so I can keep it green as I converge.
- `phase-0-004-the-engine-is-clean-and-well-factored-th.md` (343 lines) — The engine is clean and well-factored — the convergence path is clear. Here's the plan I'll execute, keeping the suite green:
- `phase-0-005-the-renderer-is-untracked-path-a-code-co.md` (329 lines) — The renderer is untracked Path A code coupled to the deleted defrecord model. It's never required by `main` (which uses `infra.render`), but I shouldn't leave it referencing a model that no longer exists. Let me see its model-facing helpers and retarget just that seam to the ECS world.
- `phase-0-006-now-i-see-the-actual-split-the-thing-pm2.md` (300 lines) — Now I see the actual split. The thing PM2 launches — `clj -M:dev` → `infra.dev.server` — never touched my converged code. It does:
- `phase-0-007-exit-143-the-150s-timeout-killed-it-with.md` (144 lines) — Exit 143 = the 150s timeout killed it with no output — something hung or was pathologically slow. Let me isolate with just the small correctness test (no 50k loop) and capture everything.

