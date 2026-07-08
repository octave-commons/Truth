# docs/notes/research — Topic Index

This directory contains the raw, conversational research notes that feed the formal research notebooks in `docs/research/`. Notes are grouped by topic; each subdirectory holds the original chunk files, and the `README.md` in each topic is the cleaned, formalized synthesis with conversational fragments removed.

## Topic Subdirectories

- `deep-research-brief/` — The original Deep Research Brief: an 8-section physics research program spanning stellar radiation, disk microphysics, planetary geology, asteroids, impacts, climate, galaxy context, and observer-centric LOD.
- `ecs-physics-substrate/` — The unification of gravity, hydro, MHD-lite, thermal, and mass-transfer physics under one ECS substrate. The canonical example of the single-world invariant.
- `stellar-mergers-accretion/` — Star-star mergers, shell feeding, Roche-lobe overflow, and binary mass transfer; how stars grow and feed one another.
- `formation-rendering/` — Physics-coupled rendering of nebulae, disks, and planet/sun formation; keeping the renderer consuming ECS state.
- `phase0-nebula/` — The Phase 0 milestone: collapse of a stellar nebula to a stable star and candidate planets, plus the discovered two-path divergence and cleanup.
- `hops315-fsm/` — The HOPS-315 protostar case study and the derived nebula-to-life FSM architecture (Matter, Role, Environment, Atmosphere/EM, Biosphere) plus Phase 0→1 handoff projection.

## How to Use

1. **For the conversation trail:** read the numbered chunks inside each topic subdirectory.
2. **For the formalized intent:** read the topic `README.md`.
3. **For the literature-grounded implementation research:** see the corresponding notebook in `docs/research/`.

## Raw → Formal → Research Pipeline

```
docs/notes/research/<topic>/           ← conversational chunks (raw source)
docs/notes/research/<topic>/README.md ← formalized synthesis (this layer)
docs/research/<domain>/<slug>.md       ← academic research notebook
```

Research actors dispatch from the formalized synthesis and write only to `docs/research/`.
