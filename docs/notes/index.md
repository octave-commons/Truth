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

## research/ (6 topics, 45 raw chunks + 6 synthesis READMEs)

Physics implementation, investigations, debugging sessions, and nebula-to-life architecture. The raw conversational chunks are now grouped into topic subdirectories; each subdirectory has a `README.md` formalizing the topic without conversational fragments.

### Topic subdirectories
- `deep-research-brief/` — 8-section physics research program (3 raw chunks)
- `ecs-physics-substrate/` — unifying physics under one ECS substrate (12 raw chunks)
- `stellar-mergers-accretion/` — star mergers, shell feeding, binary mass transfer (7 raw chunks)
- `formation-rendering/` — physics-coupled visuals and observer-centric rendering (6 raw chunks)
- `phase0-nebula/` — Phase 0 stellar nebula collapse and two-path divergence (7 raw chunks)
- `hops315-fsm/` — HOPS-315 case study and nebula-to-life FSM architecture (10 raw chunks)

### Archived research topics (already in docs/research/)
- `claude-physics-merge` → see `ecs-physics-substrate/`; formal notebooks in `docs/research/physics/`
- `formation-rendering-investigation` → see `formation-rendering/`; no formal notebook yet
- `phase-0` → see `phase0-nebula/`; formal notebooks in `docs/research/physics/`

---

## specs/ (33 files)

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
- `2026.06.29-unified-physical-state-integrator-spec.md` — Unified physical-state integrator spec
- `2026.06.30-retire-step-physics-implementation-plan.md` — Retire `:step/physics` implementation plan
- `2026.06.26.14.15.03.md` — Session notes
- `2026.06.27.00.24.01.md` — Session notes
- `2026.06.27.17.56.01.md` — Session notes

### Planetary specs (from claude-identify-missing-planetary-specs-in-code)
- `claude-identify-missing-planetary-specs-in-code-001-*.md` — Review docs and identify missing specs/research
- `claude-identify-missing-planetary-specs-in-code-002-*.md` — Retired `:metals` vs current model
- `claude-identify-missing-planetary-specs-in-code-003-*.md` — Metal enrichment & seeding (M1)
- `claude-identify-missing-planetary-specs-in-code-004-*.md` — Differentiation (M4) alignment
- `claude-identify-missing-planetary-specs-in-code-005-*.md` — Priority: planet generation & biogenesis
- `claude-identify-missing-planetary-specs-in-code-006-*.md` — Render tests to element-resolved model
- `claude-identify-missing-planetary-specs-in-code-007-*.md` — Full test suite background run
- `claude-identify-missing-planetary-specs-in-code-008-*.md` — Inner planet temperature update
- `claude-identify-missing-planetary-specs-in-code-009-*.md` — Converged review verdict

### 2026.07.06.22.04.23 plan
- `2026.07.06.22.04.23-plan-001-*.md` — Context and plan
