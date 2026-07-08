---

## Cross-Topic Synthesis

After all sections, produce a synthesis that answers:

1. In what ORDER should these layers be implemented, and why?
2. What are the critical dependencies between layers (e.g., must have
   SED before atmospheric escape; must have streaming instability before
   planet formation)?
3. What "vibes-based" simplifications are most tempting and most dangerous
   to make in each layer?
4. What is the minimum viable physically grounded implementation of each
   layer that preserves scientific plausibility without full simulation?

---

## Phased Roadmap

Produce two roadmaps:

### Research Roadmap (what to study in what order)

Phase 1 — Radiation and plasma (SED, winds, ionization)
Phase 2 — Disk microphysics (dust, pebbles, SI, migration)
Phase 3 — Planetary geology (interior, tectonics, volcanism, voxels)
Phase 4 — Collision physics (scaling laws, fragments, reaccretion)
Phase 5 — Climate and hydrology (pre-biosphere ground state)
Phase 6 — Galaxy context (star population, event environment)
Phase 7 — LOD and event/statistics mode (observer-centric architecture)

For each phase: key papers to read, key models to understand, key
approximations to validate.

### Implementation Roadmap (what to build in what order)

For each phase, list:
- New Malli schemas in src/law/
- New ECS components (defrecord in src/domain/)
- New systems (functions ending in -system in src/domain/)
- New namespaces to create
- Existing namespaces to modify
- Failing tests to write first (red phase)
- Invariants to preserve (architecture law references)

---

## Common Pitfalls to Warn About

Explicitly call out and explain each of the following anti-patterns:

1. Modeling stellar wind as neutral cold gas parcels.
2. Using a scalar brightness instead of a panchromatic SED.
3. Assuming planet composition is uniform "rock + gas" without
   condensation sequence.
4. Using rigid-body collision instead of shock-physics scaling.
5. Assuming tectonics is the default: explain what prevents plate tectonics
   on most planets.
6. Using scripted event rates instead of emergent statistics.
7. Seeding the galaxy with identical star types.
8. Treating dark energy as relevant at sub-galactic scales.

---

## Output Requirements

- Minimum 5,000 words of prose (excluding code, schemas, charts).
- At minimum 8 charts or diagrams (Plotly or Mermaid).
- At minimum 30 citations from primary or authoritative sources
  (arXiv, NASA, peer-reviewed journals).
- Every recommendation grounded in specific Truth source files
  read from GitHub.
- All Malli schemas written in valid Clojure syntax.
- All ECS component proposals written as valid Clojure defrecord stubs.
- All system proposals written as valid Clojure function signatures
  with docstrings.
- Tests written using kaocha-compatible syntax.
```

