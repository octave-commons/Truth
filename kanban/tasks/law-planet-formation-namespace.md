---
uuid: "law-planet-formation-namespace"
title: "Create law/planet_formation.clj; relocate constants out of domain"
status: "todo"
priority: "P2"
labels: ["fix", "phase0", "chemistry"]
created_at: "2026-07-06T16:21:51.000000000Z"
source: "docs/specs/core-accretion-physics-realspec.md"
category: "fix"
---

# Create law/planet_formation.clj and relocate constants

> Milestone M3. Spec: `docs/specs/core-accretion-physics-realspec.md` §9; disk realspec §9 promotion table.

The realspec promotion tables call for `src/law/planet_formation.clj`, but it does not exist. Planet-formation constants live as raw `def`s in the **domain** file (`snow-line-temperature`, `ice-enhancement-factor`, `proto-solar-metal-frac` at `planet_formation.clj:14-16`), violating the project's "constants + schemas live in `law/`" convention. Some pieces landed in `law/field` instead (`toomre-q-schema`, `cool-dyn-ratio-schema`, `disc-regime-tags`), but there is no solid-surface-density schema and no planet-formation-history schema.

**Fix:** create `src/law/planet_formation.clj`; move the constants there; add `disk-regime-schema`, `solid-surface-density-schema`, and the M3 constants (`M_crit`, `Z_crit`). Update `domain.planet-formation` to require them.

**Done when:** no planet-formation physical constant is defined in `domain/`; `architecture_test` stays green; schemas validate the regime + surface-density components.
