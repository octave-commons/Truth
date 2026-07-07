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

### Dispatching Research

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
