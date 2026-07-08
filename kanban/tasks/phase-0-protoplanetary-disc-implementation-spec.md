---
uuid: "phase-0-protoplanetary-disc-implementation-spec"
title: "Phase 0 Protoplanetary Disc Implementation Spec"
status: "done"
priority: "P0"
labels: ["specs", "phase0", "em"]
created_at: "2026-07-02T19:35:28.970460416Z"
source: "kanban/tasks/phase-0-protoplanetary-disc-implementation-spec.md"
category: "specs"
---

# Phase 0 Protoplanetary Disc Implementation Spec

**Status:** draft — Phases 1–4 completed, 5–9 pending  
**Goal:** Close the gap between the coupled gravity–hydro–MHD design and the current N-body clump substrate so that nebular collapse produces rotationally supported, magnetically braced discs rather than radial point-mass pile-ups.  
**Principle:** No shortcuts, no hacks. Every new physics term is added as a conserved quantity, a failing test, and only then an implementation. The single ECS substrate is never bypassed.

***

## 1. Current state (ground truth from code)

The codebase implements the following physics on the ECS substrate:

- Mutual N-body gravity (`domain.orbital.system`).
- Inelastic overlap merging (`domain.stellar/stellar-merge-handler`) with angular-momentum and impact-heating conservation.
- Scalar thermodynamics: ideal-gas pressure, virial heating, radiative cooling, fusion ignition (`domain.stellar`).
- Per-clump magnetic field with flux-freeze on anisotropic oblate contraction, magnetic-pressure diagnostics, Alfvén speed, resistive decay, SPH curl estimate, Lorentz acceleration, and magnetic braking (`domain.em`).
- Hydrodynamic pressure-gradient acceleration via SPH cubic-spline kernel (`domain.hydro`).
- Angular momentum, spin, oblateness, and rotation-axis components conserved through collapse and merger (`domain.stellar`).
- A four-number regime classifier: plasma β, Mach, Alfvén Mach, Jeans ratio (`domain.regime`).
- Rendering hooks for temperature colour, regime tint, magnetic field lines, oblate bodies, and HUD (`infra.render`).

What is **not** implemented, and is required for discs:

- Toomre Q or any disc-stability criterion.
- Accretion/sink particles; stars still form primarily by overlap merge.
- Proper induction equation: `B` is amplified by flux-freeze during contraction and damped by resistive decay; it is not fully advected by the velocity field.
- Spatial field operators (`shape.field` does not exist).
- Renderer parity: `infra.render/run-window` calls `orbital/orbital-system` directly and uses `bodies-from-world`, so the standalone window does not run Phase 0 physics or show fog/field lines.

The symptom — gas still collapses to a central clump without a thin disc — is now the expected result of missing **rotational-support classification**, **sink-particle accretion**, and a true **induction advection** step.

***

## 2. Physical correctness criteria

A correct implementation must satisfy these invariants, each of which becomes a test:

1. **Angular momentum conservation.** Total linear and angular momentum of the matter subsystem are constant in the absence of external torques. Mergers, collapse, and accretion must conserve the combined orbital + spin angular momentum of the participants.
2. **Rotational support.** A contracting, rotating cloud must spin up (`L = Iω`, `I` decreasing) and flatten along the rotation axis because radial collapse increases centrifugal equatorial support.
3. **Pressure support.** Gas pressure resists compression: `−∇p/ρ` provides a restoring acceleration that slows collapse and supports tenuous envelopes.
4. **Magnetic braking.** A poloidal field threading a rotating cloud exerts a tension torque that transports angular momentum outward along field lines, slowing the central spin.
5. **Disc criterion.** A region is a disc when it is rotationally supported (`v_φ` dominates `v_r`), geometrically thin (`h/r ≪ 1`), and gravitationally stable or unstable according to Toomre Q.
6. **Flux freezing.** In the ideal-MHD limit, magnetic flux through a material surface is conserved; field strength scales with density as `B ∝ ρ^(2/3)` for isotropic contraction and as `B ∝ ρ` for collapse along field lines.

***

## 3. Implementation phases

Each phase follows the project order: **Malli schema in `law/` → failing test in `test/domain/` → minimal implementation in `domain/`**. Phases are ordered by physical dependency, not by visual payoff.

### Phase 1 — Angular momentum as a first-class conserved quantity

**Status:** completed  
**Goal:** Every matter entity carries specific angular momentum; the total is conserved through collapse and merger.

**Schema (`law/`):**
- `law.stellar/angular-momentum-schema`: vector `[Lx Ly Lz]` in kg·m²/s.
- `law.stellar/spin-schema`: optional spin rate vector `[ωx ωy ωz]` rad/s.
- Update `law.stellar/nebula-cloud-schema` to require `:angular-momentum`.

**Tests (`test/domain/`):**
- `angular-momentum-conserved-in-merge`: two clumps collide and merge; total `L` before equals total `L` after.
- `angular-momentum-conserved-in-collapse`: a contracting protostar spins up such that `I₁ω₁ = I₂ω₂`.
- `seed-cloud-has-net-angular-momentum`: `phase0/create-world` yields a non-zero total `L` about the z-axis.

**Implementation (`domain/`):**
- Add `c/angular-momentum` and `c/spin` to `domain.ecs.components`.
- Compute angular momentum for seeded clumps from their initial velocity field: `L = m (r × v)`.
- Rewrite `domain.stellar/stellar-merge-handler` to conserve `L_total = L_orbital + L_spin` of the merged body.
- Rewrite `domain.stellar/collapse-system` to update `c/angular-momentum` and `c/spin` as `I` changes.

**Verification:** `clj -M:test` passes; `domain.phase0-test` asserts net angular momentum is non-zero and conserved over a fixed number of ticks.  
**Next:** Phase 5 — Disc classifier: Toomre Q and geometry.

***

### Phase 2 — Rotational geometry: oblate collapse and spin-up

**Status:** completed  
**Goal:** Collapse is no longer spherically symmetric; a rotating clump flattens into a disc geometry.

**What changed:**
- Added `c/rotation-axis` component and `law.stellar/rotation-axis-schema`.
- Added oblate spheroid helpers in `domain.stellar`:
  - `equivalent-radius`, `oblate-density`, `oblate-moment-of-inertia`, `rotation-axis`, `spin-from-angular-momentum-oblate`, `oblate-collapse-shape`.
- `collapse-system` now conserves volume (mass) while shrinking the equivalent spherical radius; it solves self-consistently for the new equatorial radius, polar radius, oblateness, and spin.
- `em/flux-freeze` uses the `anisotropy` parameter in collapse: more oblate collapse stretches the field more along the rotation axis.
- `infra.render` renders oblate bodies using a non-uniform scale matrix aligned to the rotation axis.

**Tests added:**
- `domain.stellar-test/test-equivalent-radius`
- `domain.stellar-test/test-oblate-density-conserves-mass`
- `domain.stellar-test/test-rotation-axis`
- `domain.stellar-test/test-collapse-flattens-rotating-clump` (polar < equatorial)
- `domain.stellar-test/test-collapse-mass-conserved`
- `domain.stellar-test/test-anisotropic-flux-freeze`
- `infra.render-test/test-oblate-body-projection`
- `infra.render-test/test-model-matrix-oblate`

**Next:** Phase 3 — hydrodynamic pressure-gradient acceleration.

### Phase 3 — Hydrodynamic pressure-gradient acceleration

**Status:** completed  
**Goal:** Gas pressure resists compression and drives expansion where gradients are steep.

**What changed:**
- Added `c/hydro-accel` component and `law.field/hydro-accel-schema`.
- Created `domain.hydro` namespace with pure SPH pressure-gradient acceleration:
  - Cubic-spline (M4) kernel and its gradient.
  - Symmetric pressure term `P_i/ρ_i² + P_j/ρ_j²` so the pairwise force is antisymmetric and conserves momentum.
  - `pressure-gradient-acceleration` computes `a = −∑ m_j (P_i/ρ_i² + P_j/ρ_j²) ∇W_ij`.
  - `sound-speed` helper `c_s = √(γP/ρ)`.
- Added `hydro-system` to `domain.phase0/physics-systems`; it runs before `orbital-system` so that `c/hydro-accel` is available during the Leapfrog step.
- Modified `domain.orbital.system` so that the total acceleration used by the integrator is `gravity + hydro-accel`.

**Tests added:**
- `domain.hydro-test/test-cubic-spline-gradient`
- `domain.hydro-test/test-pressure-term`
- `domain.hydro-test/test-uniform-pressure-zero-accel`
- `domain.hydro-test/test-high-pressure-pushes-outward`
- `domain.hydro-test/test-low-pressure-compressed`
- `domain.hydro-test/test-momentum-conservation-pair`
- `domain.hydro-test/test-sound-speed`
- `domain.hydro-test/test-hydro-system-stores-acceleration`
- `domain.hydro-test/test-hydro-system-pressure-gradient`
- Updated `domain.phase0-test/test-time-scale` to expect ten systems.

**Notes / interim choices:**
- Smoothing length is `h = (h_i + h_j)/2` using particle radii. This is a pragmatic proxy for local inter-particle spacing on the N-body substrate; it will be replaced by a density-based or grid-based length once `shape.field` is implemented.
- `hydro-system` currently uses an all-pairs neighbor search with a cutoff at `2h`. This is `O(n²)` and is explicitly marked as the interim pre-`shape.field` approach in the spec.
- The hydro acceleration is held constant during the Leapfrog step (both kicks use the value computed at the start of the tick). This is a first-order time-lag error that will be removed when hydro is integrated with a true field solver.

**Next:** Phase 4 — Lorentz force and magnetic braking.


### Phase 4 — Lorentz force and magnetic braking

**Status:** completed  
**Goal:** Magnetic fields exert real forces and torques on gas, not only diagnostics.

**What changed:**
- Added `law.field/magnetic-torque-schema`.
- Added `sp/cross` to `shape.spatial`.
- Extended `domain.em` with:
  - `curl-estimate`: SPH curl of `B` from neighbouring clumps.
  - `lorentz-force-density`: `f = (∇ × B) × B / μ₀`.
  - `lorentz-acceleration`: `a = f / ρ`.
  - `magnetic-torque`: `τ = r × f`.
  - `magnetic-braking-torque`: phenomenological poloidal-field braking torque aligned with the rotation axis, clamped to remove at most 1% of `|L|` per tick.
- Rewrote `em-system` to compute Lorentz acceleration and magnetic braking, add the acceleration to `c/hydro-accel` (so `orbital-system` applies it alongside gravity and hydro), update `c/angular-momentum` and `c/spin` from braking, and finally apply resistive decay.
- Removed the cyclic dependency on `domain.stellar` by inlining the oblate spin formula in `em-system`.

**Tests added:**
- `domain.em-lorentz-test/test-curl-estimate-zero-for-uniform-field`
- `domain.em-lorentz-test/test-lorentz-force-perpendicular-to-b`
- `domain.em-lorentz-test/test-lorentz-acceleration-positive`
- `domain.em-lorentz-test/test-magnetic-braking-opposes-spin`
- `domain.em-lorentz-test/test-em-system-applies-lorentz-acceleration`
- `domain.em-lorentz-test/test-em-system-brakes-spin`
- `domain.em-lorentz-test/test-em-system-conserves-b-field-bounds`

**Notes / interim choices:**
- The curl estimate uses the same SPH kernel as `domain.hydro` and is subject to the same pre-`shape.field` limitations.
- Magnetic braking is clamped per tick to avoid sign reversal and numerical blow-up with the large dynamical timestep. This is a deliberate sub-timestep stabilisation, not the physical Alfvén-wave crossing time.

**Next:** Phase 5 — Disc classifier: Toomre Q and geometry.


### Phase 5 — Disc classifier: Toomre Q and geometry

**Status:** next  
**Goal:** The regime classifier recognises rotationally supported discs and distinguishes stable discs from unstable ones.

**Schema (`law/`):**
- `law.field/toomre-q-schema`: positive double.
- Add `:stable-disc`, `:unstable-disc`, `:rotation-supported` regime tags to `law.field/regime-schema`.

**Tests (`test/domain/`):**
- `keplerian-disc-has-high-q`: a thin Keplerian disc has `Q > 1`.
- `massive-disc-is-unstable`: a self-gravitating disc has `Q < 1`.
- `classifier-tags-disc`: `domain.regime/classify` returns `:stable-disc` or `:unstable-disc` for rotationally supported geometry.

**Implementation (`domain/`):**
- Extend `domain.regime` with:
  - `toomre-q`: `Q = c_s κ / (π G Σ)`, using local surface density `Σ`, sound speed `c_s`, and epicyclic frequency `κ`.
  - `rotation-supported?`: `v_φ > 2 v_r` and `h/r < 0.3`.
- Update `domain.regime/classify` to consider `Q` and rotational support.
- Use angular momentum and spin from Phase 1 to compute `v_φ` and `κ`.

**Verification:** Render test asserts that `:stable-disc` regions receive a distinct tint/geometry.

***

### Phase 6 — Accretion / sink-particle star formation

**Goal:** Stars and planets grow by accretion from a disc, not by inelastic overlap merging.

**Schema (`law/`):**
- `law.stellar/sink-particle-schema`: mass, radius, angular momentum, accretion rate.
- `law.stellar/accretion-disc-schema`: inner/outer radius, surface-density profile.

**Tests (`test/domain/`):**
- `sink-accretes-mass`: a sink particle gains mass as small clumps pass within its accretion radius.
- `sink-conserves-angular-momentum`: accreted mass adds its orbital `L` to the sink.
- `no-overlap-merge-for-sinks`: clumps inside a sink radius are removed and added to the sink, not merged pairwise.

**Implementation (`domain/`):**
- Add `c/sink-particle` component.
- Replace or augment `domain.stellar/stellar-merge-handler` with an accretion system:
  - A clump becomes a sink when it crosses the star-formation threshold.
  - Smaller clumps within the sink radius are accreted and removed from ECS.
  - The sink inherits mass, momentum, and angular momentum.
- Seed a small accretion disc from the sink's angular momentum.

**Verification:** `domain.phase0-test/test-full-simulation` still ignites a star and forms planets, but now via accretion.

***

### Phase 7 — Proper induction equation

**Goal:** Magnetic field is advected and stretched by the velocity field, not only compressed by collapse.

**Schema (`law/`):**
- `law.field/induction-coefficient-schema`: diffusivity `η`, Reynolds number.

**Tests (`test/domain/`):**
- `flux-conserved-in-ideal-limit`: with `η = 0`, magnetic flux through a material loop is constant.
- `field-stretches-with-shear`: a velocity shear increases `B` along the stretching direction.

**Implementation (`domain/`):**
- Replace the current `em-system` with a true induction step:
  - `∂B/∂t = ∇×(v×B) − ∇×(η∇×B)`.
  - On the N-body substrate, approximate `∇×(v×B)` via SPH curl estimate.
- Keep `em/flux-freeze` as the anisotropic compression limit used by `collapse-system`.
- Add `∇·B` monitoring (not necessarily cleaning yet); assert `|∇·B|` stays bounded in tests.

**Verification:** `domain.em-test` passes induction tests.

***

### Phase 8 — `shape.field` spatial operator protocol

**Goal:** Replace N-body approximations of `∇p`, `∇×B`, etc. with grid-correct operators, polymorphic over grid type as resolved in the design doc.

**Schema (`law/`):**
- `law.field/grid-type-schema`: `:geodesic-shell`, `:voxel-lattice`, `:particle`.
- `law.field/operator-contract-schema`: grad/div/curl/Laplacian signatures.

**Tests (`test/domain/`):**
- `grad-of-linear-field-is-constant`: analytic test on each grid type.
- `curl-of-constant-vector-is-zero`.
- `div-curl-is-zero` for smooth fields.
- `flux-conserved-across-lod-boundary`: when a clump is promoted/demoted between particle and grid representations, magnetic flux and angular momentum are conserved.

**Implementation (`domain/`/`shape/`):**
- Create `shape.field` with protocol `FieldGrid` and implementations for:
  - particle/SPH kernels (the current N-body substrate),
  - geodesic shell (round bodies),
  - voxel lattice (gas volumes).
- Migrate `domain.hydro`, `domain.em`, and `domain.regime` to call `shape.field` operators.
- Implement frame-transform invariants in `law.field`.

**Verification:** All existing tests still pass; new grid-convergence tests demonstrate identical physical results across grid types for simple fields.

***

### Phase 9 — Renderer parity

**Goal:** The live dev window and the screenshot path run the same Phase 0 physics and render the same visuals.

**Tests (`test/infra/`):**
- `run-window-uses-phase0-tick`: `infra.render/run-window` calls `phase0/tick-world`.
- `run-window-renders-phase0-bodies`: `infra.render/run-window` uses `phase0-bodies-from-world`.
- `dev-window-input-cycles-camera-modes`: camera key bindings function.

**Implementation (`infra/`):**
- Update `infra.render/run-window` to accept a tick function and bodies function; default to Phase 0.
- Ensure `infra.dev.window` and `infra.dev.server` already use Phase 0 functions (they do; fix the standalone `run-window`).
- Add camera mode key bindings already implemented (`C`, `[`, `]`, `R`).

**Verification:** Manual smoke test: `clj -M:dev` shows fog, field lines, and regime tints.

***

## 4. What is explicitly out of scope for this spec

These are correct physics but belong to later phases or other specs:

- Planet interior convection / core dynamo (`domain.interior`) — depends on `shape.field` and a finished disc.
- Atmosphere hydrostatics and stellar wind (`domain.atmosphere`) — post-disc, post-Phase 0 handoff.
- Non-ideal MHD (ambipolar diffusion, Ohmic dissipation, Hall effect) — add after ideal MHD is tested.
- Radiative transfer / M1 closure for `E_rad` — keep the coarse radiation model until thermal physics is refactored.
- Player focus and dual representation — see `phase0-player-focus-and-dual-representation.md`.
- Narrator presence — see `phase0-narrator-presence.md`.
- Habitability handoff criteria — see `phase0-habitability-handoff.md`.
- Chemistry-driven collision outcomes and differentiation — see `phase0-chemistry-differentiation.md`.

***

## 5. First deliverable

Phases 1–4 are complete and the test suite is green. The first concrete remaining deliverable is **Phase 5**: Toomre Q and disc-geometry classification. It builds directly on the angular-momentum, hydro, and EM work already in place and has no dependencies on `shape.field`.

Next action: approve Phase 5, then write the Malli schemas, failing tests, and implementation for disc classification.
