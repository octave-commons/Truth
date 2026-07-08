# Formation Rendering and Physics-Coupled Visuals

**Topic:** Rendering planet/sun formation as a physics-coupled visual system, not a cinematic layer.  
**Source:** `formation-rendering-investigation` conversation chunks (6 files).  
**Status:** Renderer issues fixed; formal research on physics-coupled visualization not yet in `docs/research/`.

## Core Principle

The visuals **must** be tied to composition and physics. The renderer consumes the ECS world as pure data and must not introduce visual state that diverges from the simulation. Render size, color, temperature tint, magnetic field lines, and oblateness all derive from ECS components.

## Fixed Issues

1. **Collision detection parity** — `domain.physics.collision` was not coupled to the visual body sizes, so planets appeared to merge before/after the physics said they should.
2. **Renderer consumes wrong world** — `infra.render/run-window` was calling `orbital/orbital-system` directly and using `bodies-from-world`, bypassing Phase 0 physics and fog/field-line rendering.
3. **Visuals decoupled from composition** — planets must be voxels and their visual properties must read from physical components (temperature, composition, density, angular momentum, oblateness).
4. **Player/observer mode** — the camera and rendering pipeline must distinguish between free-floating observer and embodied player viewpoints.

## Required ECS Components for Rendering

| Visual Property | ECS Component | Notes |
|---|---|---|
| Position, size | `position`, `radius`, `mass` | Direct mapping |
| Temperature color | `temperature` | Blackbody or false-color ramp |
| Regime tint | `regime` | MHD-dominated, gravitationally-unstable, etc. |
| Magnetic field lines | `b-field` | Visualized as field-line glyphs |
| Oblateness / spin | `angular-momentum`, `spin` | Rotational flattening |
| Nebula density | `density` + geodesic grid | Fog/volume rendering |

## LOD and Observer-Centric Rendering

- Nearby bodies render as voxels with full physical detail.
- Mid-distance bodies render as coarse spheres with physical state mapped to color/size.
- Far bodies and nebula are volumetric fields sampled by ray marching.
- The camera is an observer entity with its own `position` and `orientation` components.

## Gaps vs. Existing Research

No research notebook currently exists for rendering or physics-coupled visualization. The rendering architecture is documented in design docs (`docs/designs/phase0-volumetric-renderer.md`) but not grounded in literature on real-time volumetric rendering, participating media, or game-engine LOD techniques.

## Connections to Other Topics

- `ecs-physics-substrate` is the source of the ECS components the renderer consumes.
- `phase0-nebula` defines the physical phenomena that need visualization.
- `hops315-fsm` defines the discrete state labels that can drive visual style changes.
- `deep-research-brief` Section 8 asks for the observer-centric LOD architecture.

## Open Questions

- What is the minimum voxel resolution that still conveys composition visually?
- How do we render optically thick nebulae without a full radiative-transfer solve?
- What is the right fallback when a body is too small to resolve as voxels?
- How do we visualize magnetic fields without cluttering the player view?
