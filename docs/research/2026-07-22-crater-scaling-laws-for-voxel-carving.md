# Crater Scaling-Law Set for Collision → Shock → Voxel Carving (Voxel 5)

**Date:** 2026-07-22
**Author:** research agent (read-only on `src/`)
**Card:** `kanban/tasks/collision-shock-voxel-carving` (Voxel 5)
**Resolves:** `docs/designs/planetary-voxel-substrate.md` §7.6 ("Scaling-law source")
**Status:** selected + parameterized, ready to transcribe into `law/` constants
**Domain actor:** `truth-research-geology` (cratering)

---

## 0. The selected set (summary)

**Selection:** the Schmidt & Housen (1987) / Holsapple & Schmidt (1982, 1987)
π-group coupling-parameter framework, in the *dimensional* form implemented and
regressed by **Collins, Melosh & Marcus (2005)** for transient/final crater
dimensions, with **Bjorkman & Holsapple (1987)** melt/vapor energy scaling as
corrected to energy-scaling form by **Pierazzo, Vickery & Melosh (1997)**
(the Collins et al. `V_m = 8.9×10⁻¹² E sinθ` fit), **Kraus, Senft & Stewart
(2011)** for H₂O-ice targets, **Benz & Asphaug (1999)** for catastrophic-
disruption thresholds, and **Croft (1985)** / **Dence (1965)** for the
simple→complex transition.

**Justification (one paragraph).** This is a real-time 64 m voxel game, not a
hydrocode: we need closed-form laws that evaluate in microseconds per impact,
have every constant published with validity ranges, and degrade gracefully at
the extremes a game actually hits (meter-scale blasts to planet-reshaping
impacts). The Collins et al. (2005) dimensional set is exactly the
Schmidt–Housen π-scaling with the fitted constants already folded into SI-form
power laws — it is the most battle-tested public implementation of this
literature (it backs the Imperial/Purdue Earth Impact Effects calculators), it
quotes its own uncertainty (the 1.161 constant is "a best estimate within a
range of 0.8 to 1.5"), and it covers crater size, depth, melt, and simple/
complex conversion in one self-consistent system. The Bjorkman–Holsapple
point-source melt scaling is adopted *only* through its energy-scaling
correction (Pierazzo et al. 1997 showed the point-source limit does not apply
to melt/vapor — a genuine literature contradiction, see §9). Ice gets its own
fit set (Kraus et al. 2011) because ice melt volumes are ~10× rock's at the
same energy and its excavation exhibits a "hot plug" disposition the rock laws
do not predict. Nothing in the set requires per-cell shock tracking: every
output is a volume, a length, or a tag fraction that maps directly onto the
design's carve/melt-tag/cool voxel ops (design §6) and its 2 ms/tick edit
budget.

---

## 1. Framework: π-groups and the coupling parameter

Holsapple & Schmidt (1982, *JGR* 87:B3, 1849–1870) and Schmidt & Housen
(1987, *Int. J. Impact Eng.* 5, 543–560) reduce cratering to dimensionless
groups (SI, impactor radius `a`, diameter `L = 2a`, velocity `U`, impactor
density `ρ_i`, target density `ρ_t`, target strength `Y`, gravity `g`):

```
π_V = D_tc (ρ_t / m_i)^(1/3)          ; crater volume (as a diameter measure)
π_2 = 1.61 g L / U²                   ; gravity-scaled size (inverse)
π_3 = Y / (ρ_t U²)                    ; strength-scaled stress
π_4 = ρ_i / ρ_t                       ; density ratio
```

The **coupling parameter** (Holsapple & Schmidt 1987, *JGR* 92:B7, 6350–6376)
is the single scalar that determines the late-stage flow:

```
C = k1 · a · U^μ · ρ_i^ν
```

with `μ ∈ [1/3, 2/3]` between the momentum and energy point-source limits.
Fitted exponents per target material:

| Target material | μ | ν | Source |
|---|---|---|---|
| Competent rock (basalt, granite) | 0.55 | 0.40 | Schmidt & Housen 1987; Holsapple & Schmidt 1987 |
| Regolith / dry sand | 0.41 | 0.40 | Schmidt & Housen 1987 |
| Water | ≈0.55 (as rock; Collins water constant uses rock exponents) | 0.40 | Schmidt & Housen 1987 via Collins et al. 2005 |
| Cold H₂O ice | 0.55 (adopted) — but see §9: Croft (1981) fit 0.64 | 0.40 | adopted; Senft & Stewart 2008; Croft 1981 |

Holsapple & Schmidt (1987): low-dissipation materials μ ≈ 0.6, high-dissipation
materials μ ≈ 0.4; this is the theoretical grounding of the fitted values.

For the game, the π-groups are the *classifier inputs* and the coupling
parameter is the *logging/debug quantity*; the actual diameters come from the
dimensional fits below (they are the same fits with constants unfolded).

---

## 2. Crater dimensions — SELECTED FIT (Collins et al. 2005)

Collins, Melosh & Marcus (2005, *Meteorit. Planet. Sci.* 40, 817–840), from
Holsapple & Schmidt (1982), Schmidt & Housen (1987), Gault (1974).

### 2.1 Gravity regime (all planetary impacts larger than a few hundred m)

```
D_tc = K1 · (ρ_i/ρ_t)^(1/3) · L^0.78 · U^0.44 · g^(−0.22) · sin^(1/3)(θ)
```

- `D_tc` transient crater diameter, measured at the pre-impact surface [m]
- `θ` impact angle **measured from the horizontal** (90° = vertical)
- `K1 = 1.161` competent rock (quoted valid range **0.8–1.5**, i.e. ±40%)
- `K1 = 1.365` water targets (Schmidt & Housen 1987, via Collins et al. 2005)
- Exponents 0.78 / 0.44 / −0.22 are the μ = 0.55, ν = 0.4 rock fit; for
  regolith (μ = 0.41) the equivalent fitted gravity exponents are
  `L^0.83 · U^0.34 · g^(−0.17)` (π_V ∝ π_2^(−0.17), Schmidt & Housen 1987).

Equivalent π-form (verified by unfolding the Collins constants):
`π_V = 1.6 · π_2^(−0.22)` for competent rock at π_4 = 1.

### 2.2 Strength regime (small/fast impacts into strong or low-g targets)

Holsapple (1993, *Annu. Rev. Earth Planet. Sci.* 21, 333–373) gives the
strength-regime form `π_V = K_s · π_3^(−μ/2)`; unfolded:

```
D_s = K_s · (m_i/ρ_t)^(1/3) · (Y / (ρ_t U²))^(−0.275) · sin^(1/3)(θ)   (μ = 0.55)
```

- `K_s ≈ 1.6`, adopted for continuity with the gravity fit's π-form constant.
  **This is the least-constrained constant in the whole set (factor ~2).**
  Collins et al. (2005) never need it (all terrestrial impacts are gravity
  regime); the game needs it only for sub-100 m carvings into strong material.
- **Classifier rule:** `D_tc = min(D_tc_gravity, D_s)` — whichever physics
  arrests crater growth first wins (Holsapple 1993). A simpler order-of-magnitude
  crossover check: gravity regime when `D_tc ≫ Y/(ρ_t g)` (Melosh 1989, ch. 5).

### 2.3 Transient depth and excavation geometry

- Transient crater depth: `d_tc = D_tc / (2√2) ≈ 0.354 · D_tc` (Collins et al.
  2005, eq. 25).
- Maximum excavation depth: `d_exc ≈ D_tc / 10` (Melosh 1989, *Impact
  Cratering*, Oxford UP, §5).
- Excavated volume (game disposition: paraboloid of diameter `D_tc`, depth
  `d_exc`): `V_exc = (π/2) · (D_tc/2)² · d_exc = π D_tc³ / 80`. This is the
  volume converted to ejecta blanket (§4), **±factor 2**; the remainder of the
  transient cavity is displaced/compressed, not ejected.

### 2.4 Simple → complex conversion

- Transition diameter `D_sc`: **3.2 km on Earth** (Dence 1965; Collins et al.
  2005). Scales with inverse surface gravity:
  `D_sc = 3200 · (9.81 / g)` m. Icy satellites follow the same gravity trend
  (Schenk 2002, *GRL* 29), so the same formula is adopted for ice.
- Simple (`D_tc` converted, small): `D_fr = 1.25 · D_tc` (Grieve & Garvin 1984
  analytical collapse model, fit in Collins et al. 2005).
- Complex (`D_tc` in km, `D_sc` in km): `D_fr = 1.17 · D_tc^1.13 · D_sc^(−0.13)`
  (Croft 1985, *Proc. LPSC* 15, via Collins et al. 2005).
- Complex final depth (km, Herrick et al. 1997 Venus relation):
  `d_fr = 0.4 · D_fr^0.3`.

---

## 3. Regime classifier decision table

Evaluate **in order**; first matching branch wins. Inputs: impactor
(m_i, L, ρ_i, U, θ), target (M_t, R_t, ρ_t, Y, g, material class, crust voxel
band extent if focused).

| # | Test | Resulting regime | Voxel disposition |
|---|---|---|---|
| 1 | `Q = ½ m_i U² / M_t ≥ 2·Q*_D(R_t)` | **Catastrophic disruption** | No crater carve. Fragmentation pipeline (design §6 step 2d): fragment-size/ejecta-speed distribution → new rubble bodies / reaccumulated aggregate |
| 2 | `Q*_D ≤ Q < 2·Q*_D` | **Disruption (marginal)** | As #1 but largest remnant ≈ half target mass; body becomes rubble-pile voxel aggregate |
| 3 | `Q*_S ≤ Q < Q*_D` | **Shattering, no dispersal** | Crater carve + target-wide cohesion reset toward rubble (fragments cannot escape; Benz & Asphaug 1999) |
| 4 | `Q < Q*_S` and `m_i U² ≥ 4·E_bind`-scale merging condition (impact velocity < ~v_esc, comparable masses) | **Merging/accretion** | No carve; bodies merge, retain interior structure where melt pooled (design §6) |
| 5 | cratering: `V_melt > V_band` (melt exceeds the resolved crust band) or `D_fr > 2 × band extent` | **Basin / magma-ocean-scale** | Off-focus: macro geology field melt-fraction scalar only; on-focus: band-wide `:env/magma-ocean` transition, not per-voxel carve (design §6) |
| 6 | cratering: `min(D_g, D_s) = D_s` | **Strength regime** | Carve bowl from `D_s` fit (K_s fit, factor-2) |
| 7 | cratering: `D_tc < D_sc(g)` | **Simple crater** | Carve paraboloid `D_tc × d_tc`; final rim `D_fr = 1.25 D_tc`; breccia+melt lens on floor |
| 8 | else | **Complex crater** | Carve `D_tc` transient, then relax to `D_fr, d_fr` (central uplift/terrace morphologically; voxel-wise: widened bowl + reduced depth + coherent floor melt sheet) |

Melt/vapor sub-classification applied inside regimes 6–8 (drives the melt-tag
volume, §5):

| Test | Shock disposition |
|---|---|
| `U < U_melt` | No melt tags; excavation + brecciation only |
| `U_melt ≤ U < U_vapor` | Melt volume per §5; no vapor |
| `U ≥ U_vapor` | Melt + vapor volumes per §5; vapor removed from solid field |

Threshold velocities (incipient, vs. similar-density impactor/target):

| Quantity | Basalt/granite | H₂O ice | Source |
|---|---|---|---|
| `U_melt` | 12 km/s | ~5 km/s (little melt below) | O'Keefe & Ahrens 1982 via Collins 2005; Kraus et al. 2011 |
| `U_vapor` | ~25–40 km/s (incipient→complete) | ~8–15 km/s | Pierazzo et al. 1997 (critical-entropy method); Kraus et al. 2011 (fits valid > 8 km/s) |

**Q\* disruption thresholds** (Benz & Asphaug 1999, *Icarus* 142, 5–20,
Table III; `Q̄*_D = Q₀ (R/1 cm)^a + B ρ (R/1 cm)^b`, cgs: erg/g, g/cm³):

| Material | U_ref | Q₀ (erg/g) | Q₀ (J/kg, SI) | B (erg cm³/g²) | a | b |
|---|---|---|---|---|---|---|
| Basalt | 5 km/s | 9.0×10⁷ | 9.0×10³ | 0.5 | −0.36 | 1.36 |
| Ice | 3 km/s | 1.6×10⁷ | 1.6×10³ | 1.2 | −0.39 | 1.26 |

SI transcription: `Q*_D [J/kg] = Q₀_SI · (R/0.01 m)^a + B·10⁻⁷ · (ρ_t/1000) · (R/0.01 m)^b`
with R in metres, ρ_t in kg/m³. Weakest bodies are ~200–300 m diameter (min of
Q*_D(R)); angle dependence of Q*_D is ~factor 10 between head-on and 75°
(Benz & Asphaug 1999) — adopt head-on values, note conservatism.

---

## 4. Angle dependence

- Most probable impact angle: **45°** (Shoemaker 1962, in *Physics and
  Astronomy of the Moon*). Use 45° as the default when angle is unknown.
- Crater dimensions: `∝ sin^(1/3)(θ)` (Gault & Wedekind 1978, *Proc. LPSC* 9;
  adopted by Collins et al. 2005).
- Melt volume: `∝ sin(θ)` (Collins et al. 2005, from Pierazzo & Melosh 2000
  and Ivanov & Artemieva 2002 — melt ∝ transient crater volume). Valid θ ≳ 15°;
  below ~15° it overestimates melt by ~factor 2.
- Ice: melt `sin^0.7(θ)`, vapor `sin^0.6(θ)` (Kraus et al. 2011); Pierazzo &
  Melosh (2000) give `sin^0.8(θ)` for rock — a mild contradiction (§9); Collins
  `sin θ` splits the difference and is adopted for rock.
- **Contradiction worth knowing:** Artemieva & Lunine (2003, *Icarus* 164)
  find *more* melt at shallower angles for icy bodies with porous surfaces —
  an outlier driven by porosity; not adopted.

---

## 5. Melt and vapor volume model + voxel disposition

### 5.1 Rock (SELECTED)

```
V_melt = 8.9×10⁻¹² · E · sin(θ)          [m³, E in J]      (rock)
```

Collins et al. 2005 eq. 30, fit to O'Keefe & Ahrens (1982), Pierazzo et al.
(1997), Pierazzo & Melosh (2000) hydrocode results and Grieve & Cintala (1992)
terrestrial-crater observations. Underlying form: `V_m/V_i ∝ U²/ε_m` (energy
scaling, μ = 2/3 — Pierazzo et al. 1997 showed melt follows energy scaling,
**not** the Bjorkman & Holsapple 1987 point-source exponent) with
`ε_m = 5.2 MJ/kg` for granite (specific energy of the Rankine–Hugoniot state
whose isentropic release ends on the 1-bar liquidus; Pierazzo et al. 1997).
Validity: `U > 12 km/s`, comparable impactor/target densities. **±factor 2.**

```
V_vapor ≈ f_v · E · sin(θ) / (ρ_t · ε_v)      ε_v = 1.3×10⁷ J/kg (silicate)
```

`f_v ≈ 0.1–0.3` (only the near-isobaric-core volume fully vaporizes;
impedance-matching geometry, Bjorkman & Holsapple 1987; critical-entropy
vapor thresholds of Ahrens & O'Keefe 1972/Pierazzo et al. 1997). **This is the
weakest fit in the set: factor ~3, and only meaningful for U ≳ 25 km/s.**

### 5.2 H₂O ice (SELECTED — Kraus, Senft & Stewart 2011, Icarus 214, 724–738)

CTH + 5-phase H₂O EOS hydrocode fits, U in **km/s**, T_r = T_target/273 K
(reduced temperature), V_P = impactor volume; valid U > 8 km/s (±50% deviation
in 5–8 km/s, negligible melt < 5 km/s):

```
V_vapor      = V_P · 1.0×10⁻⁴ · (T_r + 0.07) · U^1.7 · sin^0.6(θ)
V_melt+vapor = V_P · 2.8×10⁻⁴ · (T_r + 0.40) · U^1.6 · sin^0.7(θ)
V_melt = V_melt+vapor − V_vapor
```

Cross-check at the same energy: ice melt ≈ **10× rock melt** (Pierazzo et al.
1997, noted by Collins et al. 2005) — consistent with these fits.

### 5.3 Voxel disposition of melt/vapor (recommendation)

1. **Vapor** → remove voxels from the solid field; add mass to the target's
   atmosphere field if gravitationally bound, else to an escaping debris/gas
   field (design §6 step 2). Tag `:state :vapor` only as a transient bookkeeping
   state inside the edit job — it never persists.
2. **Melt (rock, simple craters)** → melt mixes into the breccia lens: tag floor
   voxels down to `d_exc` as `:state :melt` with volume capped at `V_melt`,
   laid as a sheet of thickness `t_m = 4 V_melt / (π D_tc²)` (Collins et al.
   2005 eq. 31) on the crater floor.
3. **Melt (rock, complex craters)** → coherent melt sheet of uniform thickness
   `t_m` across the floor (diameter ≈ `D_tc`, Croft 1985).
4. **Melt (ice)** → **hot plug**: less than half the melt is ejected during
   transient-crater formation; the rest concentrates in a central plug on the
   crater floor for E ≲ 2×10²⁰ J and U ≲ 5 km/s; at larger E/U, discontinuous
   excavation in ice concentrates essentially all remaining melt into the
   central plug (Kraus et al. 2011; Senft & Stewart 2009). Game rule: place
   `V_melt_retained ≈ 0.5–1.0 · V_melt` (±) in a central plug of diameter
   `≈ 0.3 D_fr`, not a uniform sheet.
5. **Ejecta** → excavated volume `V_exc` is redeposited as an annular
   `:regolith`/breccia blanket (lower density, low cohesion) extending to
   `≈ 2.5 R_fr`, thickness profile `t(r) = t₀ (r/R_fr)^(−3)` with `t₀` set by
   volume conservation against `V_exc − V_melt_retained` (McGetchin et al.
   1973 ejecta-blanket form; blanket geometry ±50%).

### 5.4 Cooling back to solids (design §6 step 4)

Per the design's merge-bug rule, voxel temperature is **re-derived each tick
from stored enthalpy/mass/state, never stored drifting**. Melt voxels cool by:

- **Conduction into the substrate** (default): `τ_cond ≈ h² / κ`,
  `κ_rock ≈ 1×10⁻⁶ m²/s`, `κ_ice ≈ 1×10⁻⁶ m²/s` (order unity; standard thermal
  diffusivities). `h` = local melt-sheet/plug thickness.
- **Radiation from an exposed pool surface**: `τ_rad = ρ c_p h ΔT / (ε σ T⁴)`,
  `c_p ≈ 1000 J/kg/K` (rock), emissivity ε ≈ 0.9.

On solidification: melt voxels convert to solid rock categories — basaltic
crust / impact-melt breccia (new `:material` keyword, high cohesion), or for
ice, to liquid water (hydro layer) if above the local freezing criterion, else
re-frozen ice. Giant-impact branch: if `V_melt > V_band`, flip the environment
FSM to `:env/magma-ocean` instead of tagging voxels (design §6; FSM ownership
in `nebula-to-life-fsm.md` §4.3).

---

## 6. Constants — directly transcribable into `law/` (SI)

```edn
{:crater/k1-gravity-rock            1.161        ;; Collins et al. 2005 (range 0.8-1.5); Schmidt & Housen 1987
 :crater/k1-gravity-water           1.365        ;; Schmidt & Housen 1987 via Collins et al. 2005
 :crater/k-strength                 1.6          ;; π_V = K_s π_3^(-μ/2), Holsapple 1993 form; FACTOR-2 constant
 :crater/mu-rock                    0.55         ;; Schmidt & Housen 1987
 :crater/mu-regolith                0.41         ;; Schmidt & Housen 1987
 :crater/mu-ice                     0.55         ;; adopted; Croft 1981 finds 0.64 — see §9
 :crater/nu                         0.40         ;; Schmidt & Housen 1987 (rock/regolith)
 :crater/exponent-L-gravity         0.78         ;; Collins et al. 2005 (rock)
 :crater/exponent-U-gravity         0.44
 :crater/exponent-g-gravity        -0.22
 :crater/exponent-angle-diameter    0.3333333    ;; sin^(1/3)θ, Gault & Wedekind 1978
 :crater/transient-depth-factor     0.3535534    ;; d_tc = D_tc/(2√2), Collins et al. 2005
 :crater/excavation-depth-fraction  0.1          ;; d_exc = D_tc/10, Melosh 1989
 :crater/excavation-volume-coeff    0.0392699    ;; V_exc = (π/80) D_tc³, paraboloid disposition
 :crater/simple-final-factor        1.25         ;; D_fr = 1.25 D_tc, Grieve & Garvin 1984 via Collins 2005
 :crater/complex-coeff              1.17         ;; D_fr = 1.17 D_tc^1.13 D_sc^-0.13 (km), Croft 1985
 :crater/complex-exponent-tc        1.13
 :crater/complex-exponent-sc       -0.13
 :crater/complex-depth-coeff        0.4          ;; d_fr = 0.4 D_fr^0.3 (km), Herrick et al. 1997
 :crater/complex-depth-exponent     0.3
 :crater/simple-complex-D-earth-m   3200.0       ;; Dence 1965; scale D_sc ∝ 1/g
 :melt/coeff-rock                   8.9e-12      ;; V_melt = coeff · E · sinθ, Collins et al. 2005
 :melt/energy-granite-J-per-kg      5.2e6        ;; ε_m, Pierazzo et al. 1997
 :melt/threshold-U-rock             1.2e4        ;; m/s, O'Keefe & Ahrens 1982
 :vapor/energy-silicate-J-per-kg    1.3e7        ;; ε_v, Pierazzo et al. 1997 / Ahrens & O'Keefe
 :vapor/efficiency-range            [0.1 0.3]    ;; f_v, near-core fraction — FACTOR-3 fit
 :vapor/threshold-U-rock            2.5e4        ;; m/s, incipient, order-of-magnitude
 :ice/vapor-coeff                   1.0e-4       ;; Kraus et al. 2011 (U in km/s!)
 :ice/vapor-T-offset                0.07
 :ice/vapor-U-exponent              1.7
 :ice/vapor-angle-exponent          0.6
 :ice/melt-coeff                    2.8e-4       ;; melt+vapor; subtract vapor for melt
 :ice/melt-T-offset                 0.4
 :ice/melt-U-exponent               1.6
 :ice/melt-angle-exponent           0.7
 :ice/melt-threshold-U              5.0e3        ;; m/s; fits valid > 8 km/s, ±50% 5-8 km/s
 :ice/melt-retained-fraction        [0.5 1.0]    ;; hot plug, Kraus et al. 2011
 :ice/hot-plug-diameter-fraction    0.3          ;; of D_fr — disposition recommendation
 :disruption/basalt {:Q0-J-per-kg 9.0e3 :B 0.5 :a -0.36 :b 1.36 :U-ref 5.0e3}   ;; Benz & Asphaug 1999 (cgs B; see §3 formula)
 :disruption/ice    {:Q0-J-per-kg 1.6e3 :B 1.2 :a -0.39 :b 1.26 :U-ref 3.0e3}
 :disruption/dispersal-factor       2.0          ;; Q ≥ 2·Q*_D ⇒ full dispersal (game convention over B&A)
 :ejecta/blanket-radius-factor      2.5          ;; × R_fr, McGetchin et al. 1973
 :ejecta/blanket-thickness-exponent -3.0
 :angle/default-rad                 0.7853982    ;; 45°, Shoemaker 1962
 :thermal/kappa-rock                1.0e-6       ;; m²/s
 :thermal/kappa-ice                 1.0e-6
 :thermal/heat-capacity-rock        1000.0       ;; J/kg/K
 :thermal/emissivity                0.9}
```

---

## 7. Clojure pseudocode — classifier + volume functions

```clojure
(ns domain.collision.scaling
  "Voxel 5: regime classifier + bulk outcome volumes.
   All SI. θ measured from horizontal. Constants from law.crater (EDN above).")

(def ^:private ^:const two-sqrt-2 (* 2 (Math/sqrt 2.0)))

(defn coupling-parameter
  "C = a U^μ ρ_i^ν — the single scalar ordering late-stage crater flow
   (Holsapple & Schmidt 1987). Logged for debug; classifier uses π_2/π_3."
  [{:keys [a U rho-i]} {:keys [mu nu]}]
  (* a (Math/pow U mu) (Math/pow rho-i nu)))

(defn pi-groups
  [{:keys [L U]} {:keys [Y rho-t g]}]
  {:pi-2 (/ (* 1.61 g L) (* U U))
   :pi-3 (/ Y (* rho-t U U))})

(defn transient-diameter-gravity
  "Collins et al. 2005 gravity-regime fit (rock exponents)."
  [{:keys [L U theta]} {:keys [rho-i rho-t g]} C]
  (* (:crater/k1-gravity-rock C)
     (Math/pow (/ rho-i rho-t) (/ 1.0 3.0))
     (Math/pow L (:crater/exponent-L-gravity C))
     (Math/pow U (:crater/exponent-U-gravity C))
     (Math/pow g (:crater/exponent-g-gravity C))
     (Math/pow (Math/sin theta) (/ 1.0 3.0))))

(defn transient-diameter-strength
  "π_V = K_s π_3^(-μ/2) unfolded (Holsapple 1993). Factor-2 prefactor."
  [{:keys [m-i U theta]} {:keys [Y rho-t]} C]
  (* (:crater/k-strength C)
     (Math/pow (/ m-i rho-t) (/ 1.0 3.0))
     (Math/pow (/ Y (* rho-t U U)) (* -0.5 (:crater/mu-rock C)))
     (Math/pow (Math/sin theta) (/ 1.0 3.0))))

(defn classify-regime
  "Decision table of §3, branches 1-8 (merging test elided — reuse the
   existing collision event's binding check)."
  [{:keys [m-i U] :as imp} {:keys [M-t R-t material] :as tgt} C band]
  (let [Q      (/ (* 0.5 m-i U U) M-t)
        q-star (disruption-q-star R-t material C) ;; Benz & Asphaug 1999, §3
        q-shatter (* 0.5 q-star)]                 ;; Q*_S ≈ Q*_D strength-regime floor (B&A99)
    (cond
      (>= Q (* (:disruption/dispersal-factor C) q-star)) :catastrophic-disruption
      (>= Q q-star)                                      :disruption-marginal
      (>= Q q-shatter)                                   :shattering-no-dispersal
      :else
      (let [d-g   (transient-diameter-gravity imp tgt C)
            d-s   (transient-diameter-strength imp tgt C)
            d-tc  (min d-g d-s)
            d-sc  (* (:crater/simple-complex-D-earth-m C) (/ 9.81 (:g tgt)))
            v-m   (melt-volume imp tgt C)
            basin (or (> v-m (:volume band))
                      (> d-tc (* 2 (:extent band))))]
        (cond
          basin                :basin-or-magma-ocean
          (< d-tc d-sc)        (if (< d-s d-g) :simple-strength :simple-gravity)
          :else                :complex)))))

(defn final-diameter
  "Croft 1985 / Grieve & Garvin 1984 conversion (diameters in m; the
   Croft fit is published in km — convert at the boundary)."
  [d-tc d-sc]
  (if (< d-tc d-sc)
    (* 1.25 d-tc)
    (* 1000.0 1.17 (Math/pow (/ d-tc 1000.0) 1.13) (Math/pow (/ d-sc 1000.0) -0.13))))

(defn melt-volume
  "Collins 2005 (rock) or Kraus 2011 (ice). Zero below threshold."
  [{:keys [m-i U theta] :as imp} {:keys [material T rho-t]} C]
  (case material
    :ice (if (< U (:ice/melt-threshold-U C)) 0.0
           (let [v-p (/ m-i (:rho-i imp))           ;; impactor volume
                 Tr  (/ T 273.0)
                 u-km (/ U 1000.0)                  ;; fit is in km/s
                 v-vap (* v-p (:ice/vapor-coeff C) (+ Tr (:ice/vapor-T-offset C))
                          (Math/pow u-km (:ice/vapor-U-exponent C))
                          (Math/pow (Math/sin theta) (:ice/vapor-angle-exponent C)))
                 v-mv  (* v-p (:ice/melt-coeff C) (+ Tr (:ice/melt-T-offset C))
                          (Math/pow u-km (:ice/melt-U-exponent C))
                          (Math/pow (Math/sin theta) (:ice/melt-angle-exponent C)))]
             {:vapor v-vap :melt (max 0.0 (- v-mv v-vap))}))
    ;; rock default
    (if (< U (:melt/threshold-U-rock C)) {:vapor 0.0 :melt 0.0}
      (let [E (* 0.5 m-i U U)]
        {:melt  (* (:melt/coeff-rock C) E (Math/sin theta))
         :vapor (if (>= U (:vapor/threshold-U-rock C))
                  (* 0.2 (/ (* E (Math/sin theta))
                            (* rho-t (:vapor/energy-silicate-J-per-kg C))))
                  0.0)}))))

(defn excavation-volume [d-tc C]
  (* (:crater/excavation-volume-coeff C) d-tc d-tc d-tc))
```

The carve job then enqueues (per the 2 ms/tick budget of `law.voxel`):
paraboloid removal over `D_tc × d_exc`, ejecta-blanket `:regolith` deposit,
melt tags per §5.3, and a cooling schedule per §5.4 — all as ordered
`edit-diff` chunks with `:provenance :collision`.

---

## 8. Worked example — 1 km iron impactor, 20 km/s, 45°, basalt target

Inputs: L = 1000 m (a = 500 m), ρ_i = 7800 kg/m³, U = 2.0×10⁴ m/s,
θ = 45° from horizontal, target basalt ρ_t = 2700 kg/m³, Y = 1×10⁷ Pa,
g = 9.81 m/s² (Earth-like).

| Quantity | Value | Notes |
|---|---|---|
| Impactor mass m_i | 4.08×10¹² kg | (4/3)π a³ ρ_i |
| Kinetic energy E | 8.17×10²⁰ J | ½ m U² |
| Coupling parameter C | 4.2×10⁶ (mixed units) | a U^0.55 ρ_i^0.4; ordering scalar only |
| π_2 = 1.61 g L/U² | 3.97×10⁻⁵ | ≪ 1 ⇒ gravity-dominated |
| π_3 = Y/(ρ_t U²) | 9.3×10⁻⁹ | |
| D_gravity | **15.2 km** | Collins fit, K1 = 1.161, sin^(1/3)45° = 0.891 |
| D_strength | ≈ 297 km | min() picks gravity regime, consistent |
| Regime | **complex crater** (D_tc > D_sc = 3.2 km) | |
| D_tc (transient) | **15.2 km** | |
| d_tc | 5.4 km | D_tc/(2√2) |
| d_exc | 1.5 km | D_tc/10 |
| V_exc | 1.4×10¹¹ m³ | π/80 · D_tc³ |
| D_fr (final rim) | **21.8 km** | Croft: 1.17·15.233^1.13·3.2^-0.13 (km) |
| d_fr (final depth) | 1.0 km | 0.4·D_fr^0.3 (km), Herrick 1997 |
| V_melt | **5.1×10⁹ m³ (≈5 km³)** | 8.9e-12·E·sin45°; U = 20 > 12 km/s ✓ |
| Melt sheet t_m | 28 m | 4 V_m/(π D_tc²), complex-crater sheet |
| V_vapor | ~1–4×10⁹ m³ | f_v·E sinθ/(ρ_t ε_v), f_v 0.1–0.3 — factor-3 |
| Q = E/M_t | n/a (planet-scale target) | no disruption check triggered |

**Sanity checks.**
- Against Meteor Crater scaling (50 m iron, 12.8 km/s → 1.2 km): scaling by
  L^0.78 U^0.44 gives (20)^0.78 × (1.5625)^0.44 × 1.2 km ≈ 15 km — same ballpark
  as the direct fit (the residual reflects angle/simple-vs-complex bookkeeping).
  ✓
- Known rule of thumb: a 1 km stony/iron impactor at ~20 km/s → ~10–20 km
  crater on Earth. Our 21.8 km final is at the top of that range, consistent
  with the dense impactor. ✓
- Chicxulub anchor (E ~ 10²³–10²⁴ J): the melt law gives 10³–10⁴ km³ melt,
  bracketing published Chicxulub melt estimates. ✓
- Voxel budget: V_exc ≈ 1.4×10¹¹ m³ ≈ 5×10⁸ canonical 64 m voxels — far beyond
  the 8192-voxel focus-band cap, so this exact event is a **macro-field /
  basin-scale op (branch 5)** if it hits an unfocused region, or a
  multi-second queued carve if focused (the design's "crater visibly forms
  over ~a second" feature). The voxel-level carve path is for craters up to a
  few km across; the same fits drive both dispositions.

---

## 9. Honest error bars and literature contradictions

1. **K1 = 1.161 is a factor-1.5 constant.** Collins et al. 2005 state it
   outright: "best estimate within a range of 0.8 to 1.5." Crater diameters:
   ±40%; volumes (cubed): ±factor ~3.
2. **Melt volume: ±factor 2** (Collins et al. 2005; Pierazzo & Melosh 2000
   scatter, angle < 15° overestimates ×2). Plastic-work heating contributes
   ~35% of melt below ~12.5 km/s and the power law breaks down below ~10 km/s
   (Kurosawa & Genda 2018; Manske et al. 2022) — the hard 12 km/s threshold
   masks a soft shoulder.
3. **Point-source vs energy scaling for melt (resolved contradiction).**
   Bjorkman & Holsapple (1987) proposed V_melt ∝ U^(3μ) with μ = 0.55–0.6
   (point-source). Pierazzo, Vickery & Melosh (1997, *Icarus* 127, 408–423)
   showed the point-source limit does not apply to melt/vapor and that
   **energy scaling (μ = 2/3)** fits hydrocode data — agreeing with Ahrens &
   O'Keefe (1977). The selected Collins fit is the energy-scaling form. Do
   **not** implement the Bjorkman–Holsapple exponent for melt.
4. **Ice μ: 0.55 vs 0.64 (unresolved, adopted 0.55).** Croft (1981, *LPSC*
   12) fit μ ≈ 0.64 to ice cratering experiments; Senft & Stewart (2008,
   *MAPS* 43, 1993–2013) and Kraus et al. (2011) work in the
   Holsapple–Schmidt framework consistent with rock-like μ ≈ 0.55. The
   difference shifts D ∝ U^0.44 → U^0.51 — small over the game's velocity
   range; adopt 0.55 and note the ambiguity.
5. **Angle exponent for melt: sin θ (adopted) vs sin^0.8 (Pierazzo & Melosh
   2000) vs sin^0.6–0.7 (Kraus 2011, ice).** Material-dependent; the adopted
   sin θ is the middle of the range for rock.
6. **Artemieva & Lunine (2003) outlier:** porous icy surfaces melt *more* at
   shallow angles — opposite sign to everyone else. Not adopted; flagged as a
   porosity effect the current material model (no porosity field on voxels
   yet) cannot express.
7. **Strength-regime prefactor K_s: factor ~2, worst-constrained constant.**
   It only matters for sub-100 m carving into strong targets — exactly the
   64 m voxel regime, so expect to tune it against feel.
8. **Vapor volume: factor ~3.** The f_v ∈ [0.1, 0.3] efficiency is a
   disposition choice, not a published fit; published work gives entropy
   thresholds, not closed-form vapor volumes for arbitrary angle.
9. **Excavation volume / ejecta blanket geometry: ±factor 2** (the π/80·D³
   paraboloid and r^-3 blanket are dispositions chosen for conservation and
   simplicity, bracketed by Melosh 1989's excavation-flow results).
10. **Q\* dispersal factor 2×** is a game convention over Benz & Asphaug's
    Q*_D (largest remnant = half mass); grazing impacts raise Q*_D by up to
    ×10 (B&A 1999) — head-on values are conservative.

## 10. Primary sources

- Holsapple, K. A. & Schmidt, R. M. 1982. "On the scaling of crater dimensions
  2: Impact processes." *JGR* 87(B3), 1849–1870.
- Holsapple, K. A. & Schmidt, R. M. 1987. "Point source solutions and coupling
  parameters in cratering mechanics." *JGR* 92(B7), 6350–6376.
- Schmidt, R. M. & Housen, K. R. 1987. "Some recent advances in the scaling of
  impact and explosion cratering." *Int. J. Impact Eng.* 5, 543–560.
- Holsapple, K. A. 1993. "The scaling of impact processes in planetary
  sciences." *Annu. Rev. Earth Planet. Sci.* 21, 333–373.
- Housen, K. R., Schmidt, R. M. & Holsapple, K. A. 1983. "Crater ejecta
  scaling laws." *JGR* 88(B3), 2485–2499.
- Bjorkman, M. D. & Holsapple, K. A. 1987. "Velocity scaling impact melt
  volume." *Int. J. Impact Eng.* 5, 155–163.
- Pierazzo, E., Vickery, A. M. & Melosh, H. J. 1997. "A reevaluation of impact
  melt production." *Icarus* 127, 408–423.
- Pierazzo, E. & Melosh, H. J. 2000. "Melt production in oblique impacts."
  *Icarus* 145, 252–261.
- Collins, G. S., Melosh, H. J. & Marcus, R. A. 2005. "Earth Impact Effects
  Program." *Meteorit. Planet. Sci.* 40, 817–840.
- Kraus, R. G., Senft, L. E. & Stewart, S. T. 2011. "Impacts onto H₂O ice:
  Scaling laws for melting, vaporization, excavation, and final crater size."
  *Icarus* 214, 724–738.
- Kraus, R. G. & Stewart, S. T. 2010. "Impact induced melting and vaporization
  on icy planetary bodies." *LPSC* 41, #2693.
- Senft, L. E. & Stewart, S. T. 2008. "Impact crater formation in icy layered
  terrains on Mars." *Meteorit. Planet. Sci.* 43, 1993–2013.
- Benz, W. & Asphaug, E. 1999. "Catastrophic disruptions revisited." *Icarus*
  142, 5–20.
- Croft, S. K. 1985. "The scaling of complex craters." *Proc. LPSC* 15,
  C828–C842.
- Croft, S. K. 1981. "The excavation stage of impact cratering: a comparison
  of seven craters in ice." *LPSC* 12, 196–198.
- Gault, D. E. & Wedekind, J. A. 1978. "Experimental studies of oblique
  impact." *Proc. LPSC* 9, 3843–3875.
- Grieve, R. A. F. & Garvin, J. B. 1984. "A geometric model for excavation and
  modification at terrestrial simple craters." *JGR* 89, 11561–11572.
- Grieve, R. A. F. & Cintala, M. J. 1992. "An analysis of differential impact
  melt-crater scaling." *Meteoritics* 27, 526–538.
- Herrick, R. R. et al. 1997. (Venus crater depth–diameter, via Collins 2005.)
- Melosh, H. J. 1989. *Impact Cratering: A Geologic Process.* Oxford UP.
- Shoemaker, E. M. 1962. In *Physics and Astronomy of the Moon* (45° most
  probable angle).
- O'Keefe, J. D. & Ahrens, T. J. 1982 (melt threshold ~12 km/s, via Collins
  2005); Ahrens, T. J. & O'Keefe, J. D. 1972, *Moon* 4, 214–249
  (critical-entropy method); 1977, in *Impact and Explosion Cratering*,
  639–656.
- Kurosawa, K. & Genda, H. 2018 (plastic-work melt contribution, via Manske
  et al. 2022); Manske, L. et al. 2022, *JGR Planets* 127 (melt-production
  scaling revisited).
- Artemieva, N. & Lunine, J. 2003. *Icarus* 164, 471–480 (porous-ice shallow
  angle outlier).
- McGetchin, T. R. et al. 1973 (ejecta blanket r^-3 form, via Melosh 1989).
- Dence, M. R. 1965 (simple/complex classification, 3.2 km Earth, via Collins
  2005); Schenk, P. M. 2002, *GRL* 29 (icy-satellite transition follows
  gravity trend).
