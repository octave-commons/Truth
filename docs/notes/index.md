# docs/notes index

Notes organized by category. Originals archived in `archive/`.

## Organization

```
docs/notes/
├── designs/      ← Architecture explorations, vision docs, design sessions
├── research/     ← Physics merge, investigations, implementation debugging
├── specs/        ← Technical specifications, ECS contracts, μ-specs, protocols
├── archive/      ← Original unsplit exports
└── index.md      ← This file
```

---

## designs/ (11 files)

Architecture and vision explorations:

- `2026.06.25.16.41.16-001-the-core-vision-truth-as-a-physics-first.md` — Core vision: Truth as a physics-first universe
- `2026.06.25.16.41.16-002-why-this-matters.md` — Why this matters
- `architecture-exploration-001-*.md` — Simulation timing, clock UI, mass/temp readouts
- `architecture-exploration-002-*.md` — Text rendering gap, STBEasyFont for HUD
- `architecture-exploration-003-*.md` — Leapfrog symplectic, tier-quantized dt
- `architecture-exploration-004-*.md` — Type metadata on nil literals
- `architecture-exploration-005-*.md` — Missing glViewport, HiDPI bug
- `architecture-exploration-006-*.md` — Viewport fix from framebuffer size
- `phase-0-design-exploration-001-*.md` — Phase 0 design kickoff
- `phase-0-design-exploration-002-*.md` — Stellar physics system creation
- `phase-0-design-exploration-003-*.md` — Normalize reference fix

---

## research/ (25 files)

Physics implementation, investigations, debugging sessions:

### claude-physics-merge (12 files)
- `claude-physics-merge-001-*.md` — MHD equations, regime diagnostics
- `claude-physics-merge-002-*.md` — Planet interior + atmosphere physics
- `claude-physics-merge-003-*.md` — N-body substrate understanding
- `claude-physics-merge-004-*.md` — Test validation (88 pass)
- `claude-physics-merge-005-*.md` — Dev window code divergence discovery
- `claude-physics-merge-006-*.md` — Three parallel pieces mapping
- `claude-physics-merge-007-*.md` — ECS path cleanup, renderer test
- `claude-physics-merge-008-*.md` — ECS nebula enrichment
- `claude-physics-merge-009-*.md` — Nebula completion, star system emergence
- `claude-physics-merge-010-*.md` — Parallelism tasks, domain.phase0 rework
- `claude-physics-merge-011-*.md` — Harness emergence monitoring
- `claude-physics-merge-012-*.md` — Malleability constant to law.stellar

### formation-rendering-investigation (6 files)
- `formation-rendering-investigation-001-*.md` — Planet/sun formation jumps
- `formation-rendering-investigation-002-*.md` — Collision detection + renderer
- `formation-rendering-investigation-003-*.md` — Physics-coupled visuals
- `formation-rendering-investigation-004-*.md` — Complete fix picture
- `formation-rendering-investigation-005-*.md` — All four issues fixed (134 tests green)
- `formation-rendering-investigation-006-*.md` — Player/observer model, spark HUD

### phase-0 implementation (7 files)
- `phase-0-001-*.md` — Two simulation paths problem
- `phase-0-002-*.md` — Path A vs Path B split diagnosis
- `phase-0-003-*.md` — Full picture of both paths
- `phase-0-004-*.md` — Convergence plan
- `phase-0-005-*.md` — Renderer retargeting to ECS
- `phase-0-006-*.md` — PM2 dev server divergence
- `phase-0-007-*.md` — Exit 143 timeout debugging

---

## specs/ (21 files)

Technical specifications, schemas, contracts:

### ECS & Core Architecture (from 2026.06.23.20.01.16)
- `001-best-fit-stack.md` — Technology stack selection
- `002-μ0-shapes-claims-contracts.md` — Shape system, claims, contracts
- `003-shapecore-shapes-claims-events-ids.md` — shape.core implementation
- `004-lawledger-immutable-event-ledger-merkle.md` — Immutable event ledger
- `005-μ4-spatial-primitives-3d-vectors-aabbs-o.md` — 3D vectors, AABBs, octants
- `006-shapespatial-vectors-aabbs-octants-bodie.md` — Spatial primitives impl
- `007-finishing-domaingravitybarnes-hut.md` — Barnes-Hut gravity
- `008-the-ecs-contract.md` — ECS contract specification
- `009-the-event-model.md` — Event model design
- `010-domainecsrewindable-the-protocol.md` — Rewindable ECS protocol
- `011-lawecs_dslclj.md` — ECS DSL in law namespace
- `012-μ-specs-first.md` — μ-spec methodology
- `013-μ-specs-domainecscore_testclj.md` — ECS core test specs

### Physics & Formation Models
- `2026.06.25.22.11.59-001-core-model.md` — Core physics model
- `2026.06.25.22.11.59-002-protostar-*.md` — Protostar collapse to first burning
- `2026.06.25.22.13.14.md` — Additional physics notes
- `2026.06.26-authentic-phase0-formation-physics.md` — Authentic Phase 0 formation physics

### ECS Double Buffer & Recent
- `2026.06.26-ecs-double-buffer-single-writer-spec.md` — Double buffer single-writer spec
- `2026.06.26.14.15.03.md` — Session notes
- `2026.06.27.00.24.01.md` — Session notes
- `2026.06.27.17.56.01.md` — Session notes
