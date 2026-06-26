# Phase 0 SPH Density Field Spec

**Status:** completed — density-system implemented, verified, all tests pass  
**Goal:** Make SPH density a true neighbor-sum (`ρᵢ = Σⱼ mⱼ W`) so the pressure/composition field actually varies across the nebula, replacing the current degenerate `mass / sphere-volume` estimate.  
**Principle:** The field must be real before fog or Jeans-driven formation can mean anything. One ECS substrate; one renderer; no parallel world models.

---

## 1. Current state (ground truth from code)

`domain.hydro` already has:

- A momentum-conserving SPH pressure-gradient acceleration using the cubic-spline kernel gradient.
- A hydro-system that reads `:component/density` and `:component/pressure` and writes `:component/hydro-accel`.

`domain.stellar` seeds nebula clumps with:

- Equal particle mass `pmass = total-mass / gas-count`.
- A fixed visual/collision radius `prad = extent * 0.004`.
- Density computed once as `body-density = mass / ((4/3)π r³)`.
- Pressure from `ideal-gas-pressure`.

`domain.stellar/thermal-system` keeps `:nebula` temperature fixed at its seeded value and recomputes pressure from density. Because density never changes for `:nebula`, pressure stays uniform across the cloud.

The result is a degenerate field: every gas particle has identical density, identical pressure, and identical temperature. The SPH pressure gradient reduces to a short-range anti-overlap repulsion with no large-scale structure.

## 1b. Star ignition regression

A separate but related defect prevented the central protostar from ever being classified as a `:star`. The contraction code (`collapse-system`) set temperature directly from the virial theorem, but `thermal-system` ran afterwards and clamped the body toward the CMB floor (3 K). Because the protostar started near 10 K, every tick reset its temperature to ~3 K, so adiabatic compression heating never accumulated and fusion thresholds (`T > 1e7 K`, `P > 1e12 Pa`) were never reached. The fix applies adiabatic compression heating from the previous tick's temperature, bounded below by the virial temperature, and rederives pressure from the ideal gas law.

---

## 2. Physical correctness criteria

A correct SPH-as-fluid implementation must satisfy these invariants, each captured by a test:

1. **Kernel normalization.** The cubic-spline kernel integrates to 1 over its support in 3D.
2. **Self-density is non-zero.** An isolated particle has a finite SPH density estimate equal to its own mass times the kernel at `r = 0`.
3. **Density rises with crowding.** A particle with more neighbors within its smoothing length has a higher density estimate than an isolated particle of the same mass.
4. **Uniform distribution reproduces mean density.** A regular grid or glass-like equal-mass sampling returns a density close to the mean cloud density.
5. **Pressure follows density.** After the density update, pressure is recomputed from the new density and the (fixed-for-nebula) temperature via the ideal gas law.
6. **Pipeline order is correct.** Density is computed from current positions, then pressure is updated, then the hydro force uses those fresh values.
7. **No regression in momentum conservation.** The existing antisymmetric pressure-gradient tests still pass.

---

## 3. Implementation plan

### Phase 1 — Kernel and density primitives in `domain.hydro`

**Status:** completed  
**Goal:** Add the missing cubic-spline kernel `W(r,h)` and a pure `sph-density` function.

**Changes (`src/domain/hydro.clj`):**

- Added `cubic-spline-w` — the dimensionless M4 cubic spline `W(q)`.
- Added `kernel` — `W(r,h)` with 3D normalization `8/(π h³)`.
- Added `sph-density` — `ρᵢ = Σⱼ mⱼ W(rᵢⱼ, h)` where `h = 2 × particle-radius`.

**Tests (`test/domain/hydro_test.clj`):**

- `test-kernel-normalization`: numerical integral of `W` over a sphere equals 1.
- `test-self-density`: an isolated particle returns `m × W(0, h)`.
- `test-density-rises-with-crowding`: a particle with two close neighbors has higher density than an isolated one.

### Phase 2 — `density-system` updates `:component/density` and `:component/pressure`

**Status:** completed  
**Goal:** Run an SPH density pass before the pressure-gradient force so the hydro force sees a real field.

**Changes (`src/domain/hydro.clj`):**

- Added `density-system [dt] (fn [world] ...)`:
  - Operates only on `:nebula` particles (resolved bodies keep their body-density).
  - For each particle, finds neighbors within `h = 2 × radius`.
  - Computes `ρ` with `sph-density`.
  - Recomputes `P = ideal-gas-pressure ρ T` using `law.stellar/ideal-gas-pressure`.
  - Writes both back to the ECS world if finite.

**Changes (`src/domain/phase0.clj`):**

- Inserted `(hydro/density-system effective-dt)` as the first system in `physics-systems`, before `hydro/hydro-system`.

**Tests:**

- `test-density-system-updates-nebula-density`: after running `density-system`, a clump spawned in a crowd has a different density than its seed value.
- `test-density-system-preserves-resolved-body-density`: a `:planet` is not overwritten by the SPH density pass.
- `test-density-pressure-consistent`: after `density-system`, pressure equals `ideal-gas-pressure` of the new density and temperature.

### Phase 3 — Integration and regression

**Status:** completed  
**Goal:** Verify the change does not break existing physics contracts.

- Updated `test/domain/phase0_test.clj` to expect 11 systems instead of 10.
- Ran `clj -M:test`: 146 tests, 382 assertions, 0 failures, 0 errors.

### Phase 4 — Async code review and follow-up fixes

**Status:** completed  
**Goal:** Dispatch a review agent and address findings.

**Findings from async code reviewer (k2p7):**

1. **Pair smoothing length bug in `pressure-gradient-acceleration`.** The pair smoothing length was computed as `0.5*(r_i+r_j)`, which is half the value consistent with the per-particle smoothing length `h_i = 2*r_i`. Neighbors found within the `2*r` cutoff could therefore contribute zero pressure force. Fixed by using `h_ij = r_i + r_j`.
2. **Stale `c/hydro-accel` lifecycle.** `hydro-system` only writes `c/hydro-accel` for hydro-active entities, but `orbital.system` adds any existing `c/hydro-accel` to every body. A clump that transitioned to `:debris`/`:planet`/`:star` would keep its last acceleration. Fixed by clearing `c/hydro-accel` from all non-hydro-active entities at the start of `hydro-system`.
3. **Docstring unit slip.** `kernel-gradient` claimed units of `1/volume`; corrected to `1/length⁴`.

**Additional tests added:**

- `test-pair-smoothing-length`: a neighbor at distance `1.5*r` (between the old half-support and the correct full support) produces a non-zero antisymmetric force pair.
- `test-hydro-accel-cleared-for-resolved-bodies`: when an entity transitions from `:nebula` to `:planet`, `c/hydro-accel` is removed.

**Verification:** `clj -M:test` → 148 tests, 387 assertions, 0 failures, 0 errors.

### Phase 5 — Volumetric fog visualizer

**Status:** completed  
**Goal:** Make the nebula render as a continuous volumetric cloud whose opacity varies with SPH density, replacing the misleading hard-edged gas-particle disc.

**Changes (`src/infra/render.clj`):**

- Replaced constant-alpha particle disc with a Gaussian falloff in the fragment shader: `alpha = density * exp(-2.0 * d²)`.
- Added a per-particle `:density` attribute (vertex array) so each fog puff carries its own local density multiplier.
- Updated `nebula-fog` to accept and forward `:density`; bound the density value from the ECS `:component/density` scaled into `[0.1, 2.0]`.
- Added `infra.render-test/test-nebula-density-visualization` to assert that nebula particles carry `:density` for the shader.

**Verification:** `clj -M:test` → 150 tests, 393 assertions, 0 failures, 0 errors.

---

## 4. Open questions / decisions

- **Smoothing length:** Use `h = 2 × radius` (the existing neighbor cutoff) as a global, constant smoothing length for the equal-radius nebula seeding. Adaptive smoothing (`h ∝ n_neigh^(-1/3)`) is a future refinement once the field is proven to vary.
- **Neighbor search cutoff:** Same as smoothing length support — `2 × radius`. The existing `neighbors-within` helper in `domain.hydro` already uses this cutoff.
- **Self-contribution:** Include the particle itself in the density sum, as standard SPH does. This gives a finite isolated-particle density and improves continuity.
- **Pressure update ownership:** `density-system` owns the pressure update immediately after density, because `hydro-system` needs consistent `P` and `ρ`. `thermal-system` will later recompute the same pressure for `:nebula` (temperature held constant), so the two stay in sync.

---

## 5. Verification log

| Step | Command / Check | Expected | Actual | Date |
|------|-----------------|----------|--------|------|
| Kernel normalization | `test-kernel-normalization` | ∫ W dV ≈ 1 | pass | 2026-06-26 |
| Crowding density | `test-density-rises-with-crowding` | crowded > isolated | pass | 2026-06-26 |
| Density system updates nebula | `test-density-system-updates-nebula-density` | density changes | pass | 2026-06-26 |
| Resolved bodies preserved | `test-density-system-preserves-resolved-body-density` | body-density unchanged | pass | 2026-06-26 |
| Pressure consistency | `test-density-pressure-consistent` | P = ρkT/m_H | pass | 2026-06-26 |
| Pipeline integration | `clj -M:test` | all pass | 149 tests, 389 assertions, 0 failures | 2026-06-26 |
| Architecture guard | `test/architecture_test.clj` | pass | pass | 2026-06-26 |
| Pair smoothing length | `test-pair-smoothing-length` | non-zero force at 1.5*r | pass | 2026-06-26 |
| Hydro-accel lifecycle | `test-hydro-accel-cleared-for-resolved-bodies` | cleared on deactivation | pass | 2026-06-26 |
| Star ignition | `test-collapse-heats-toward-fusion` | protostar reaches fusion thresholds | pass | 2026-06-26 |
| Volumetric fog density | `test-nebula-density-visualization` | nebula fog particles carry `:density` | pass | 2026-06-26 |
| Full suite | `clj -M:test` | all pass | 150 tests, 393 assertions, 0 failures | 2026-06-26 |
