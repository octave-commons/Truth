# Phase 0 Jeans-Driven Formation Spec

> **⚠️ SUPERSEDED (2026-07-03) by
> [`genesis-formation-authoritative.md`](./genesis-formation-authoritative.md).**
> This spec's mass-tier path — promoting a gas parcel to `:planet` once it is
> heavy enough (`law/mass-class`) — is the "lie dressed as emergence" the
> authoritative formation physics forbids: planets are **sub-grid** and are
> seeded by a core-accretion prescription on the disk's solid surface density
> (`domain.planet-formation`), never by a mass threshold on a gas parcel.
> `law/mass-class` and its callers (`stellar/classify-system`,
> `jeans-collapse-system`) are **unwired** from the production pipeline; the live
> transition is `stellar/classify-next-state` (density + Jeans + fusion gates,
> no `:planet` tier). Retained for historical reference and the tests that pin
> the old behaviour. Where this doc and the authoritative spec disagree, the
> authoritative spec wins.

**Status:** superseded (was: implemented)  
**Goal:** Replace collision-driven gas growth with Jeans-instability-driven collapse. Gas sample particles become resolved bodies only when self-gravity overcomes thermal pressure. Collisions then handle merging of already-resolved bodies.

**Principle:** Collision merging should not create new resolved bodies out of gas. Jeans instability is the single transition mechanism from gaseous sample particle to physical object. One ECS substrate; one renderer; no parallel world models.

---

## 1. Current state (ground truth from code)

`domain.phase0/seed-nebula` creates `gas-count` equal-mass particles:

- `pmass = total-mass / gas-count` (fixed for the life of the particle)
- `prad  = extent * 0.004` (initial smoothing/collision radius)
- Initial density `rho = pmass / ((4/3) π prad³)`
- Initial temperature ~12 K
- All particles are `:matter-state :nebula`

`domain.physics.collision/collision-detection-system` detects sphere overlaps among all resolved bodies with `c/position`, `c/radius`, `c/mass`, `c/velocity`, and `c/accretion-radius`. It emits `:event/collision` for every overlapping pair. `:nebula` gas particles are excluded from collision detection.

`domain.stellar/stellar-merge-handler` merges the smaller body into the larger, summing mass, conserving momentum, blending composition, and setting temperature from mass-weighted average + impact heating. This handler is registered for `:event/collision`.

`domain.stellar/classify-system` sets `:matter-state` from mass using `law.stellar/mass-class`, which takes the fixed `:phase0/gas-particle-mass` as a reference. The user override is simple: any clump heavier than one gas sample is at least `:debris`:

- `≤ gas-particle-mass` → `:nebula`
- `> gas-particle-mass` → `:debris`
- `≥ 6e28 kg` → `:planet`
- `≥ 1e30 kg` → `:protostar`

`:star` is reached through `fusion-system` once a `:protostar` core crosses ignition temperature and pressure.

`domain.stellar/collapse-system` contracts only `:protostar` cores using `oblate-collapse-shape`, raising density and temperature adiabatically.

The result is that the first resolved bodies appear because two gas particles **touched and merged**, not because either became Jeans-unstable. Debris and planets are effectively recolored gas clumps.

---

## 2. Physical model

### 2.1 What is fixed?

The only fixed property of a `:nebula` sample particle is its **mass**. Everything else — radius, density, pressure — is derived from the local gas state.

### 2.2 Adaptive radius from SPH density

A Lagrangian gas parcel of fixed mass `m` that finds itself in a dense region must represent that same mass in a smaller volume. The SPH density estimate gives the local density:

```
ρᵢ = Σⱼ mⱼ W(rᵢⱼ, hᵢ)
```

The smoothing length `hᵢ` and the particle's effective radius `rᵢ` follow from density:

```
hᵢ = η (mᵢ / ρᵢ)^(1/3)
rᵢ = hᵢ / 2
```

where `η` is a dimensionless SPH neighbor-count constant (typically ~1.2). For this implementation we use the existing convention `h = 2 × radius`, so:

```
rᵢ = (η/2) (mᵢ / ρᵢ)^(1/3)
```

**Consequences:**

- High-pressure/high-density regions → smaller particle radii.
- Low-pressure/low-density regions → larger particle radii.
- The particle's physical volume is inversely related to its SPH density.
- This couples the visual size, the smoothing length, and the Jeans criterion self-consistently.

### 2.3 What is Jeans instability?

A uniform gas sphere of mass `M`, radius `R`, density `ρ`, temperature `T` is gravitationally unstable when its radius exceeds the Jeans length:

```
λ_J = c_s * sqrt(π / (G ρ))
c_s = sqrt(γ k_B T / m_H)   (adiabatic sound speed)
```

Equivalently, the Jeans mass is the mass contained within a sphere of radius `λ_J / 2`:

```
M_J = (4/3) π ρ (λ_J / 2)³
```

A gas parcel collapses when `M > M_J` (or `R > λ_J`). The collapse timescale is the free-fall time:

```
t_ff = sqrt(3π / (32 G ρ))
```

### 2.4 Equal-mass particles

Because every `:nebula` particle has the same mass `pmass`, Jeans instability depends on local density and temperature. Crowding raises density, which:

1. Increases pressure (at fixed temperature).
2. Shrinks the particle radius via `r ∝ ρ^(-1/3)`.
3. Lowers the Jeans length `λ_J ∝ ρ^(-1/2)`.

The radius shrinks more slowly than the Jeans length (`ρ^(-1/3)` vs `ρ^(-1/2)`), so dense enough particles inevitably become Jeans-unstable. This means collapse preferentially occurs in filaments and cores.

### 2.5 Resolved bodies from gas

When a `:nebula` particle becomes Jeans-unstable:

1. It is no longer a diffuse gas sample.
2. It transitions to a resolved body based on its mass:
   - `< planet threshold` → `:debris` (small planetesimal)
   - `< star threshold` → `:planet` (sub-stellar core)
   - `≥ star threshold` → `:protostar` (star-forming core)
3. Its radius shrinks from the gas sample radius to a body radius consistent with its new density class.
4. It stops participating in SPH density and pressure forces.
5. It becomes eligible for collision merging with other resolved bodies.

### 2.6 Collision scope

Collision detection and response apply only to resolved bodies (`:debris`, `:planet`, `:protostar`, `:star`). Gas particles never collide or merge with anything. They can become Jeans-unstable independently.

Collision merging uses the existing `stellar-merge-handler`, which already handles mass-weighted merging of resolved bodies.

---

## 3. Invariants

Each invariant becomes a test:

1. **Gas-gas collisions do not occur.** No `:event/collision` is emitted for a pair of `:nebula` particles regardless of overlap.
2. **Jeans-unstable gas promotes to a resolved body.** A `:nebula` particle with `R > λ_J` transitions out of `:nebula` after `jeans-collapse-system`.
3. **Stable gas stays gas.** A `:nebula` particle with `R < λ_J` remains `:nebula`.
4. **Promotion uses mass thresholds.** A Jeans-unstable particle with mass `≥ star-mass-threshold` becomes `:protostar`; below planet threshold becomes `:debris`.
5. **Promoted bodies stop participating in SPH.** After promotion, `density-system` and `hydro-system` ignore the entity.
6. **Resolved bodies still merge on collision.** Two `:debris` particles that overlap produce a collision event and merge.
7. **Adaptive radius is consistent.** After `density-system`, a particle's radius satisfies `r ≈ (η/2) (m / ρ)^(1/3)`.
8. **Dense gas has smaller radius.** A particle with higher SPH density has a smaller radius than an identical-mass particle with lower SPH density.
9. **No regression in star ignition.** A promoted `:protostar` still reaches fusion thresholds through `collapse-system`.
10. **Render shows the transition.** A newly promoted body renders as a shaded body instead of fog.

---

## 4. Implementation plan

### Phase 1 — Remove gas from collision scope

**Status:** complete  
**Goal:** Ensure only resolved bodies can collide.

**Changes (`src/domain/physics/collision.clj`):**

- Modify `collidable-bodies` to filter out entities whose `:matter-state` is `:nebula`.
- Update docstring to state that collision is for resolved bodies only.

**Tests (`test/domain/physics/collision_test.clj`):**

- `test-gas-particles-do-not-collide`: two overlapping `:nebula` particles produce no collision event.
- `test-resolved-bodies-still-collide`: two overlapping `:debris` particles produce a collision event.

### Phase 2 — Adaptive radius from SPH density

**Status:** complete  
**Goal:** Make radius the derived quantity and density/pressure the primary fields.

**Changes (`src/domain/hydro.clj`):**

- Update `density-system` to recompute each `:nebula` particle's radius from its SPH density after computing `ρ`:
  ```clojure
  r' = (η/2) * (m / ρ)^(1/3)
  ```
  Clamp `r'` to a sensible range to avoid runaway shrinking/expansion:
  - Minimum: some fraction of the seed radius so a particle can't vanish.
  - Maximum: some multiple of the seed radius so isolated particles don't inflate without bound.
- Update the docstring for `density-system` to describe the adaptive radius.
- Leave pressure computation unchanged: `P = ideal-gas-pressure ρ T`.

**Changes (`src/domain/hydro.clj`) — `hydro-system`:**

- `hydro-system` already uses `(hydro-active? state)` to filter only `:nebula`/`:protostar`. Ensure it continues to use the updated radius for neighbor searches and pair smoothing length.

**Tests (`test/domain/hydro_test.clj`):**

- `test-density-system-updates-radius`: after `density-system`, a crowded particle has a smaller radius than an isolated particle of the same mass.
- `test-radius-density-consistent`: `r³ * ρ` is proportional to `m` for equal-mass particles.

### Phase 3 — Add Jeans collapse system

**Status:** complete  
**Goal:** Promote Jeans-unstable gas particles to resolved bodies.

**Changes (`src/domain/stellar.clj`):**

- Add `jeans-collapse-system [dt]`:
  - For each `:nebula` particle with `c/mass c/radius c/density c/temperature`:
    - Compute sound speed `c_s` from temperature.
    - Compute Jeans length `λ_J` from density and `c_s`.
    - If `radius > λ_J`:
      - Determine target state from mass via `law/mass-class`.
      - If target is `:nebula` (mass below debris threshold), keep as gas but log a warning — this should not happen if thresholds are consistent.
      - Otherwise:
        - Set `:matter-state` to target.
        - Compute a new body radius consistent with the resolved density class:
          - `:debris` / `:planet`: target density ~1000–3000 kg/m³.
          - `:protostar`: keep a larger, diffuse radius that will then contract via `collapse-system`.
        - Recompute `:density` from mass and new radius.
        - Recompute `:pressure` from ideal gas law at current temperature.
        - Remove `:hydro-accel` if present.

**Changes (`src/domain/phase0.clj`):**

- Insert `stellar/jeans-collapse-system` into `physics-systems` after `hydro/hydro-system` and before `stellar/classify-system`.
- Move `collision/collision-detection-system` after `orbital/orbital-system` and before `stellar/collapse-system` so it only sees resolved bodies.
- Final order:
  1. `hydro/density-system`
  2. `hydro/hydro-system`
  3. `stellar/jeans-collapse-system`
  4. `stellar/classify-system`
  5. `domain.orbital/orbital-system`
  6. `collision/collision-detection-system`
  7. `stellar/collapse-system`
  8. `stellar/fusion-system`
  9. `stellar/thermal-system`
  10. `regime/regime-system`
  11. `em/em-system`
  12. `recenter-system`

**Open question:** Should `classify-system` still exist? If `jeans-collapse-system` already assigns the correct state on promotion, `classify-system` becomes redundant except for collision-merged bodies whose mass may cross a threshold. Keep it as a post-collision reclassification pass.

**Tests (`test/domain/stellar_test.clj`):**

- `test-jeans-unstable-gas-promotes`: a dense cold `:nebula` particle with `R > λ_J` becomes `:debris`/`:planet`/`:protostar`.
- `test-stable-gas-remains-gas`: a warm diffuse `:nebula` particle with `R < λ_J` stays `:nebula`.
- `test-promoted-body-ignored-by-hydro`: after promotion, the entity is not in `hydro-active?`.

### Phase 4 — Adjust seeding for observable collapse

**Status:** complete  
**Goal:** Make sure the seeded cloud actually produces Jeans-unstable regions without requiring collisions.

**Analysis:**

With equal particle mass, the SPH density of a particle rises with the number of neighbors within its smoothing length. Because radius now adapts to density, dense regions shrink and become Jeans-unstable naturally.

The initial seed radius `prad = extent * 0.004` is a starting smoothing length. After the first `density-system` pass:

- Isolated particles in the diffuse halo will have low density and large radius.
- Particles in seeded overdensity centres will have high density and small radius.

The Jeans length scales as `λ_J ∝ ρ^(-1/2)`, while the adaptive radius scales as `r ∝ ρ^(-1/3)`. Because `ρ^(-1/2)` falls faster than `ρ^(-1/3)`, sufficiently dense particles will satisfy `r > λ_J` and collapse.

We may need to tune:

- The SPH neighbor constant `η`.
- The seed radius `prad`.
- The overdensity seed strength in `seed-nebula`.

so that collapse occurs on observable timescales.

**Decision:** Start with `η = 1.2` (standard SPH) and the existing seed parameters. Adjust if the cloud fails to produce resolved bodies in a reasonable number of ticks.

### Phase 5 — Renderer and regime updates

**Status:** complete  
**Goal:** Ensure promoted bodies render correctly and regimes reflect the new dynamics.

**Changes (`src/infra/render.clj`):**

- No changes needed; `phase0-bodies-from-world` already branches on `:matter-state`.
- A newly promoted `:debris`/`:planet` will render as a shaded body automatically.
- The adaptive gas radius will also affect the fog support size, making dense filaments visually tighter.

**Changes (`src/domain/regime.clj`):**

- Regime classification already reads density, pressure, temperature, b-field. It should continue to work.
- The `:gravitationally-unstable` regime may become more common as Jeans-unstable gas appears.

### Phase 6 — Verification

**Status:** complete  
**Goal:** Confirm the new pipeline produces formation without gas-gas collisions.

**Tests:**

- `test-no-gas-collisions-in-nebula`: run `phase0/tick-world` on a fresh cloud for N ticks; assert no `:event/collision` involves two `:nebula` entities.
- `test-resolved-bodies-form-without-collision`: a dense seeded region produces at least one `:debris`/`:planet`/`:protostar` after a few ticks without any collision events.
- `test-star-ignition-still-works`: a promoted `:protostar` reaches fusion thresholds through `collapse-system`.

**Verification command:** `clj -M:test` → all pass.

---

## 5. Open questions / decisions

- **Should `:debris` and `:planet` also contract?** Currently only `:protostar` contracts. For this spec, keep contraction protostar-only; planets/debris remain at their promotion radius and accrete via collision.
- **What density does a promoted body get?** Use a target density by class:
  - `:debris` ~ 2000 kg/m³ (rock/ice rubble)
  - `:planet` ~ 1000 kg/m³ (gas-giant-like or molten)
  - `:protostar` ~ seed density or slightly higher, then contracts
- **What temperature on promotion?** Keep the gas temperature; contraction heating happens in `collapse-system` for protostars. Planets/debris cool radiatively.
- **Gas removed from SPH on promotion?** Yes — resolved bodies do not participate in `density-system` or `hydro-system`.
- **Collision merging of gas with resolved body?** No. Gas never collides. A resolved body moving through gas does not accrete it directly; the gas must independently become Jeans-unstable. (Future: add Bondi-Hoyle accretion if desired.)
- **Adaptive radius clamps?** Yes — prevent particles from shrinking to zero or expanding without bound. Exact clamp values to be determined during implementation.

---

## 6. Verification log

| Step | Command / Check | Expected | Actual | Date |
|------|-----------------|----------|--------|------|
| Gas removed from collision | `test-gas-particles-do-not-collide` | no collision events for gas-gas pairs | pass | 2026-06-26 |
| Resolved bodies still collide | `test-resolved-bodies-still-collide` | collision events for debris-debris pairs | pass | 2026-06-26 |
| Adaptive radius | `test-density-system-updates-radius` | crowded particle smaller than isolated | pass | 2026-06-26 |
| Radius-density consistency | `test-radius-density-consistent` | `r³ * ρ` ∝ `m` | pass | 2026-06-26 |
| Jeans-unstable gas promotes | `test-jeans-unstable-gas-promotes` | `:nebula` → resolved body | pass | 2026-06-26 |
| Stable gas stays gas | `test-stable-gas-remains-gas` | remains `:nebula` | pass | 2026-06-26 |
| No gas in hydro after promotion | `test-promoted-body-ignored-by-hydro` | not hydro-active | pass | 2026-06-26 |
| Full suite | `clj -M:test` | all pass | 159 tests, 412 assertions, 0 failures | 2026-06-26 |

