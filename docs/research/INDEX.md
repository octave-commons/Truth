# Deep Research Index

**Last updated:** 2026-06-28
**Maintained by:** truth-research-coordinator actor

This index catalogs all research notebooks produced by the deep research actor family.
Each notebook follows the `deep-research` skill format with LaTeX, Clojure pseudocode,
charts, and a promotion path to domain code.

## How to Use

- **Finding research:** Browse by domain or search for specific topics.
- **Adding research:** Domain actors append new entries. The coordinator updates cross-references.
- **Status:** `draft` → `validated` → `promoted`
  - **draft:** Initial research, may have gaps
  - **validated:** Reviewed by coordinator, cross-checked against literature
  - **promoted:** Implementation code exists in `src/domain/`

## Cosmology

| Notebook | Status | Phase | Key Finding | Sources |
|----------|--------|-------|-------------|---------|
| primordial-nucleosynthesis-yields.md | validated | 0 | Y_p=0.247, D/H=2.53e-5, Li7 problem (3× gap). Primordial comp: H=0.753 He=0.247 | PDG 2025, Yeh+2026 |
| bbn_yields.ipynb | validated | 0 | Clojure BBN calculator with ASCII charts, 4/4 validation PASS | PDG 2025 |

**Actor:** truth-research-cosmology
**Schedule:** Every 48h
**Topics:** Stellar physics, galaxy formation, primordial nucleosynthesis, CMB, dark matter/energy, IMF, stellar populations

## Geology

| Notebook | Status | Phase | Key Finding | Sources |
|----------|--------|-------|-------------|---------|
| *(no entries yet)* | | | | |

**Actor:** truth-research-geology
**Schedule:** Every 48h
**Topics:** Plate tectonics, mantle convection, volcanism, cratering, erosion, mineralogy, planetary differentiation

## Biology

| Notebook | Status | Phase | Key Finding | Sources |
|----------|--------|-------|-------------|---------|
| *(no entries yet)* | | | | |

**Actor:** truth-research-biology
**Schedule:** Every 48h
**Topics:** Ecology, evolution, astrobiology, Lotka-Volterra, nutrient cycles, abiogenesis, biosignatures

## Atmosphere

| Notebook | Status | Phase | Key Finding | Sources |
|----------|--------|-------|-------------|---------|
| *(no entries yet)* | | | | |

**Actor:** truth-research-atmosphere
**Schedule:** Every 48h
**Topics:** Radiative transfer, climate, Hadley cell, greenhouse effect, atmospheric escape, cloud microphysics

## Physics

| Notebook | Status | Phase | Key Finding | Sources |
|----------|--------|-------|-------------|---------|
| *(no entries yet)* | | | | |

**Actor:** truth-research-physics
**Schedule:** Every 48h
**Topics:** SPH methods, N-body gravity, MHD, orbital mechanics, numerical methods, error analysis

## Culture

| Notebook | Status | Phase | Key Finding | Sources |
|----------|--------|-------|-------------|---------|
| *(no entries yet)* | | | | |

**Actor:** truth-research-culture
**Schedule:** Every 48h
**Topics:** Agent-based social models, settlement patterns, cultural evolution, mythogenesis, language evolution, collapse dynamics

## Cross-Domain

| Notebook | Status | Domains | Key Finding | Sources |
|----------|--------|---------|-------------|---------|
| *(no entries yet)* | | | | |

**Actor:** truth-research-coordinator
**Schedule:** Every 72h

---

## Research Coverage by Simulation Phase

| Phase | Description | Primary Domains | Coverage |
|-------|-------------|-----------------|----------|
| 0 | Stellar Nebula → Solar System | cosmology, physics | — |
| 1 | Radiation & Plasma | atmosphere, physics | — |
| 2 | Rocky Body Formation | geology, physics | — |
| 3 | Tectonics & Atmosphere | geology, atmosphere | — |
| 4 | Ocean & Climate | atmosphere, biology | — |
| 5 | Biosphere | biology, atmosphere | — |
| 6 | Civilization & Myth | culture, biology | — |

## Actor Status

```bash
# Check all research actor status
~/.agents/skills/eta-mu-actor-agent/scripts/actor-status.sh truth-research-cosmology
~/.agents/skills/eta-mu-actor-agent/scripts/actor-status.sh truth-research-geology
~/.agents/skills/eta-mu-actor-agent/scripts/actor-status.sh truth-research-biology
~/.agents/skills/eta-mu-actor-agent/scripts/actor-status.sh truth-research-atmosphere
~/.agents/skills/eta-mu-actor-agent/scripts/actor-status.sh truth-research-physics
~/.agents/skills/eta-mu-actor-agent/scripts/actor-status.sh truth-research-culture
~/.agents/skills/eta-mu-actor-agent/scripts/actor-status.sh truth-research-coordinator
```
