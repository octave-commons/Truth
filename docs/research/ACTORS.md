## Deep Research Actors

Gates of Truth has a family of ημ actors that periodically conduct deep academic
research into every domain the simulation touches. They are non-destructive — they
only write to `docs/research/` and never modify source code.

| Actor | Domain | Schedule | Topics |
|-------|--------|----------|--------|
| `truth-research-cosmology` | Cosmology | 48h | Stellar physics, galaxy formation, nucleosynthesis, CMB, dark matter |
| `truth-research-geology` | Geology | 48h | Plate tectonics, mantle convection, volcanism, cratering, erosion |
| `truth-research-biology` | Biology | 48h | Ecology, evolution, astrobiology, Lotka-Volterra, abiogenesis |
| `truth-research-atmosphere` | Atmosphere | 48h | Radiative transfer, climate, Hadley cell, greenhouse, clouds |
| `truth-research-physics` | Physics | 48h | SPH, N-body, MHD, orbital mechanics, numerical methods |
| `truth-research-culture` | Culture | 48h | Agent-based social models, mythogenesis, civilization dynamics |
| `truth-research-coordinator` | Cross-domain | 72h | Index maintenance, topic assignment, gap analysis, synthesis |
| `truth-research-peer-reviewer` | Quality | on-demand | Run notebooks, review text/figures/code/citations, suggest future topics |
| `truth-research-gap-analyst` | Cross-domain | on-demand | Compare coverage to simulation needs, identify missing research and cross-links |

#### Quality & Analysis Actors

| Actor | Domain | Schedule | Topics |
|-------|--------|----------|--------|
| `truth-research-peer-reviewer` | Quality | on-demand | Run notebooks, review text/figures/code/citations, suggest future topics |
| `truth-research-gap-analyst` | Cross-domain | on-demand | Compare coverage to simulation needs, identify missing research and cross-links |

### Recent outputs

- 2026-07-22 — `truth-research-geology` (cratering): `docs/research/2026-07-22-crater-scaling-laws-for-voxel-carving.md` — selected + parameterized scaling-law set for Voxel 5 (`collision-shock-voxel-carving`), resolves design gap §7.6.

These actors do not produce primary research notebooks. They read the existing research, write structured reports to their own outboxes, and feed recommendations back to the coordinator or domain actors.

### Research Output Quality Loop

1. Domain actors produce notebooks in `docs/research/<domain>/`.
2. `truth-research-peer-reviewer` runs executable artifacts, checks citations and figures, and writes review reports.
3. `truth-research-gap-analyst` periodically compares the full index to the simulation phase map and user notes, then identifies missing topics.
4. Both can send messages to `truth-research-coordinator` with recommended next dispatches.
5. The coordinator updates `docs/research/INDEX.md` and assigns new topics to domain actors.

## Dispatching Research

```bash
# Manual dispatch (uses eta-mu CLI)
~/.agents/skills/eta-mu-actor-agent/scripts/dispatch-actor-eta-mu.sh truth-research-cosmology "Research primordial nucleosynthesis yields for Phase 0 composition."

# Check status
~/.agents/skills/eta-mu-actor-agent/scripts/actor-status.sh truth-research-cosmology

# Install automated timers (one-time)
systemctl --user enable truth-research-cosmology.timer
systemctl --user start truth-research-cosmology.timer
```

### Research Output

All research notebooks are written to `docs/research/<domain>/` and indexed in
`docs/research/INDEX.md`. Each notebook includes:
- Governing equations in LaTeX
- Clojure pseudocode mapped to ECS patterns
- Charts and visualizations
- Validation against published benchmarks
- A promotion path to `domain/` code

See `.agents/skills/deep-research/SKILL.md` for the full research protocol.
