---
uuid: "stellar-wind-plasma-state-spec"
title: "Stellar Wind Plasma State Spec"
status: "todo"
priority: "P1"
labels: ["specs"]
created_at: "2026-07-08T02:24:29.856296905Z"
source: "kanban/tasks/stellar-wind-plasma-state-spec.md"
category: "specs"
---

# Stellar Wind Plasma State Spec

**Status:** specification  
**Scope:** represent stellar ejecta as plasma via `c/ionization-fraction` on `:nebula` parcels, scale EM coupling with ionization, and compute wind properties from coronal physics.  
**Depends on:** `docs/research/physics/stellar-wind-plasma-state.md`

***

## 1. Goal

Stars do not emit cold neutral gas. The current `stellar-wind-system` spawns `:nebula` parcels and tags them with ionization fraction, but the parcels behave like molecular gas in hydro, EM, and rendering. This spec makes plasma a first-class physical state while keeping the matter-state hierarchy simple.

Key outcomes:
- Wind parcels are `:nebula` with high ionization fraction and high temperature.
- EM forces scale continuously with ionization (ambipolar diffusion proxy).
- Wind ram pressure and photoionization can heat/ionize ambient `:nebula` parcels.
- Rendering distinguishes plasma (glowing, ion-tinted) from cold neutral gas.

***

## 2. Invariants

1. There is **no** `:plasma` matter-state. Plasma behavior is encoded by `c/ionization-fraction` ∈ [0, 1] on `:nebula` parcels.
2. Stellar wind parcels are launched with `ionization-fraction ≥ 0.9` and `temperature ≥ 10^6 K`.
3. Cold molecular `:nebula` has `ionization-fraction ≈ 0.0`.
4. Lorentz acceleration on a parcel is multiplied by its ionization fraction.
5. Ram pressure from a wind parcel affects only parcels within a finite interaction radius.
6. Mass, momentum, and composition are conserved between star and launched parcel.

***

## 3. Component changes

### 3.1 `c/ionization-fraction`

Already exists. Semantics:
- 0.0 fully neutral
- 1.0 fully ionized
- Values in between represent partially ionized gas where neutrals drift through ions.

### 3.2 `c/wind-profile`

Replaces spawned wind parcels. Each `:star` entity carries a radial wind profile:

```clojure
{:wind/dot-m          ;; kg/s, mass-loss rate
 :wind/v-escape       ;; m/s, launch speed
 :wind/ram-pressure   ;; kg/(m s²), at reference radius
 :wind/reference-r    ;; m
 :wind/luminosity-xuv ;; W
 :wind/ionization     ;; 0..1
 :wind/corona-t       ;; K}
```

Derived from `c/atmosphere-shells`/`c/sed-bands` or fallback values when shells are absent.

### 3.3 `c/wind-heating`

A transient influence component written onto nearby `:nebula` parcels by `wind-ablation-system`. Fields:

```clojure
{:wind-heating/delta-t        ;; K applied this tick
 :wind-heating/ionization-rate ;; per tick
 :wind-heating/mass-loss       ;; kg (ablating mass)
 :wind-heating/source-eid      ;; star id}
```

### 3.4 `c/temperature`

Hot parcels are heated by `c/wind-heating`; cooling is handled by `plasma-cooling-system`.

***

## 4. Wind launch physics

The star emits a **wind profile**, not discrete parcels.

### 4.1 Mass-loss rate

```clojure
Mdot = k * L_xuv / (v_esc * c)
```

where:
- `L_xuv` is XUV luminosity from `c/sed-bands`;
- `v_esc = sqrt(2 G M / R)`;
- `c` speed of light;
- `k` calibration knob (`:genesis/wind-rate-scale`).

### 4.2 Launch speed

Parker-wind scaling from coronal temperature:

```clojure
v_wind = v_esc * sqrt(T_corona / T_escape)
T_escape = (m_H * v_esc^2) / (2 k_B)
```

If no atmosphere shells exist, fall back to `v_esc`.

### 4.3 Ionization fraction

```clojure
ionization = min(1.0 max(0.5 T_corona / 1e6))
```

For Phase 0 (no shells), use `0.3` as a minimal ionization proxy.

### 4.4 Ram-pressure profile

At radius `r` from the star:

```clojure
P_ram(r) = dot-M * v_wind / (4 π r²)
```

### 4.5 Composition

Wind carries the star's surface `c/composition` (post-burn), not a neutral default.

***

## 5. EM coupling

### 5.1 Lorentz force scaling

```clojure
(defn lorentz-effective
  "Scale Lorentz acceleration by ionization fraction."
  [a ionization]
  (sp/v* a (double ionization)))
```

Neutral parcels (`ionization < 0.01`) skip Lorentz acceleration entirely.

### 5.2 Magnetic field of wind parcel

Wind parcels carry the launch-point `c/b-field` from `em/net-field-at`. As they expand, the field decays by resistive decay in `domain.em/field-system`.

***

## 6. Wind–nebula interaction

### 6.1 Ram-pressure ablation

For each `:nebula` parcel within `interaction-radius` of a star:

```clojure
P_ram = dot-M * v_wind / (4 π r²)
abla-m = η_ablate * P_ram / (rho * v_orb) * A_cross * dt
```

where `η_ablate` is a small efficiency (default 0.05), `A_cross` is the parcel's cross-sectional area, and `v_orb` is the relative orbital speed. The parcel loses `delta-m` mass and gains `delta-T = eta_heat * P_ram / (rho * c_v) * dt` in temperature. Both are written as `c/wind-heating` influences.

### 6.2 Interaction radius

Limited to a small multiple of the parcel smoothing length (default 5h) so heating stays local and the whole nebula does not flash-ionize.

### 6.3 Photoionization

Stars with `c/sed-bands` emit ionizing photons. Within a Strömgren-like radius:

```clojure
ionization-rate ∝ L_xuv / r²
```

Apply as an influence `c/photoionization` that the integrator blends into `c/ionization-fraction`.

### 6.4 Energy cap

Total wind energy deposited per star per tick is capped at a fraction of the star's radiative luminosity integrated over the tick, preventing runaway heating of the nebula.

***

## 7. System responsibilities

### 7.1 `domain.stellar/stellar-wind-system`

- Read `c/atmosphere-shells` and `c/sed-bands`.
- Compute `mdot`, `v_wind`, `ionization`, and the radial `c/wind-profile`.
- No longer emits `c/spawn-request-wind` or `c/wind-reservoir` parcels.

### 7.2 `domain.stellar/wind-ablation-system` (new)

- For each `:star`, sample nearby `:nebula` parcels within the interaction radius.
- Compute `P_ram(r)` and write `c/wind-heating` influences.
- Debit ablated mass from parcels; add it to a stellar `c/wind-mass-lost` ledger (mass is conserved but leaves the resolved gas reservoir).

### 7.3 `domain.stellar/eos-system` or new `plasma-cooling-system`

- Cool hot parcels via the piecewise bremsstrahlung + recombination proxy.
- Cap cooling so parcels don't drop below CMB in one tick.

### 7.4 `domain.em/em-system` / `lorentz-acceleration-system`

- Scale Lorentz acceleration by `c/ionization-fraction`.
- Skip when ionization is effectively zero.

### 7.5 `infra.render`

- Color `:nebula` parcels by temperature and ionization: neutral = purple/blue, ionized = red/pink glow.
- Render the wind envelope as a faint radial emission tied to `c/wind-profile` when atmosphere shells are present (deferred visual).

***

## 8. Tests

1. `wind-profile-is-ionized-and-hot` — assert `ionization > 0.5`, corona `temperature > 1e6`.
2. `neutral-parcel-feels-no-lorentz-force` — verify acceleration is zero.
3. `wind-mass-loss-matches-star-composition` — ablated gas carries star composition, not default neutral.
4. `ram-pressure-affects-nearby-gas` — nearby parcel temperature rises, but parcels beyond interaction radius are unchanged.
5. `mass-conserved-in-ablation` — mass debited from gas equals mass recorded in star's wind ledger.
6. `energy-cap-prevents-flash-heating` — total wind heating per star per tick stays below the cap.

***

## 9. Promotion path

| File | Change |
|------|--------|
| `src/domain/stellar.clj` | Replace `stellar-wind-system` parcel spawning with `wind-profile` computation. Delete `c/wind-reservoir`, `c/spawn-request-wind`, `c/mass-flux-wind`, `c/dv-wind`. Add `wind-ablation-system`. No fallback: the parcel-based wind model is removed entirely. |
| `src/domain/stellar.clj` or new `src/domain/plasma.clj` | Add piecewise radiative cooling for parcels heated by wind. |
| `src/domain/em.clj` | Scale Lorentz accel by ionization. |
| `src/domain/ecs/components.clj` | Add `wind-profile`, `wind-heating`, `wind-mass-lost`. |
| `src/infra/render.clj` | Color gas by ionization fraction; optional wind-envelope visual. |
| `src/domain/ecs/registry.clj` | Update system `:reads`/`writes` for new components if needed. |
| `test/domain/stellar_wind_test.clj` | Update plasma-state tests for field model. |

***

## 10. Decisions

1. **Equation of state:** use different mean molecular weights (`μ = 0.5` fully ionized, `μ = 2.3` neutral molecular) via the ideal-gas pressure helper. Keep `γ = 5/3` for both in Phase 0.
2. **Photoionization:** add a new `c/photoionization` influence component; do not fold it into thermal-intervention.
3. **Ram-pressure interaction:** ablate mass and heat locally in Phase 0; bulk momentum coupling is deferred.
4. **Rendering:** color `:nebula` by temperature + ionization, with ionized parcels getting a red/pink emissive glow.
5. **Wind model:** flux/ram-pressure-field model replaces parcel spawning.

## 11. Open questions

1. **Radiative cooling function:** use a piecewise bremsstrahlung + recombination proxy. For T ≳ 10⁴ K, Λ ∝ nₑ² T^(-1/2); below 10⁴ K cooling shuts off. A full collisional-ionization-equilibrium table is deferred.
2. **Non-thermal velocity dispersion:** bulk velocity is sufficient for Phase 0; no dispersion term.
3. **Flash-heating prevention:** cap total wind energy per star per tick and keep ram-pressure heating local (within a few smoothing lengths) with a low efficiency factor.
4. **Parcel vs. field model:** adopt the flux/ram-pressure-field model. Stellar wind is no longer emitted as discrete `:nebula` parcels; instead each star writes a `c/wind-profile` (Ṁ, v_w, ram-pressure vs. r) and a `c/wind-heating` influence that ablates nearby gas gradually. This avoids parcel proliferation and is consistent with the gradual-mass-transfer direction.
