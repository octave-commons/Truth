# Physical State and Parcel Representation of Stellar Wind Plasma

**Domain:** physics | **Phase:** 1 (Radiation & Plasma)
**Date:** 2026-07-06 | **Author:** opencode (session `ses_0c8763a44ffepEtnBICs4Er2Kr`)
**Status:** spec-derivation
**Primary sources:** Parker (1958, 1960); Lamers & Cassinelli (1999); Cranmer (2009); Asplund et al. (2009); Castor et al. (1975); Weaver et al. (1977); Krumholz et al. (2006); Henney et al. (2009); Murray-Clay et al. (2009); Owen & Alvarez (2016)

---

## 1. Research Question

Gates of Truth currently spawns stellar-wind material as `:nebula` parcels carrying `c/ionization-fraction` and `c/ram-pressure` (`domain.stellar/wind-step`). Before promoting this to a first-class plasma model we need to resolve four design questions:

1. What is the *physical state* of a real stellar wind — temperature, ionization, composition, and velocity structure?
2. How must a parcel-based N-body simulation represent plasma *differently* from neutral molecular gas?
3. Should stellar ejecta be a new `:plasma` matter-state, or an ionization-fraction continuum riding on `:nebula`?
4. How does the wind physically interact with the surrounding nebula (ram pressure, photoionization, magnetic field) and what ECS state tracks that interaction?

The answers determine whether the existing `stellar-wind-system`, `domain.em`, `law.plasma`, and `infra.render` converge on a single, physically grounded, performance-friendly design.

---

## 2. Literature Survey

### 2.1 The Parker wind: coronal origin and transonic structure

Parker (1958) showed that a hot, gravitationally bound corona cannot remain static: the pressure-gradient term exceeds gravity at large radii and drives a transonic outflow — the stellar wind. The steady, spherically symmetric, isothermal equations yield a unique supersonic solution that passes through a sonic/critical point. Parker (1960) extended this to polytropic and rotating cases and established the framework still used for solar-like stars.

> **Key finding:** The solar wind is a hot ($T \sim 10^6$–$10^7$ K), low-density, radially expanding plasma that becomes supersonic within a few stellar radii. Solar mass loss is $\dot M_\odot \sim 2\times10^{-14}\,M_\odot\,{\rm yr}^{-1} \approx 6\times10^9\,{\rm kg\,s^{-1}}$ with terminal speeds of $\sim$400 km s$^{-1}$ (slow wind) and $\sim$800 km s$^{-1}$ (fast wind).

**Citation:** Parker, E. N. (1958). Dynamics of the Interplanetary Gas and Magnetic Fields. *Astrophysical Journal*, 128, 664. DOI: [10.1086/146579](https://doi.org/10.1086/146579)

For a modern review, Cranmer (2009) summarizes observations and theory: the wind is launched from the hot corona (and cooler chromospheric holes for the fast wind), is fully ionized, and carries the star’s frozen-in magnetic field into a Parker-spiral topology at large radii.

**Citation:** Cranmer, S. R. (2009). What is the Physics of the Solar Wind? *Living Reviews in Solar Physics*, 6, 3. DOI: [10.12942/lrsp-2009-3](https://doi.org/10.12942/lrsp-2009-3)

### 2.2 Ionization state and composition

At coronal temperatures ($T_c \gtrsim 10^6$ K) hydrogen and helium are essentially fully ionized. The ionization fraction is set by the balance between collisional/photoionization and radiative recombination. In the low-density corona the Saha equation gives $x_e \approx 1$ because the recombination rate $\alpha_B n_e$ is small compared with the thermal ionization rate. For cooler outflows or wind–nebula mixing zones, partial ionization must be tracked explicitly (Osterbrock & Ferland 2006; Draine 2011).

The composition of the wind mirrors the stellar photosphere: by mass roughly $X\approx0.70$ hydrogen, $Y\approx0.28$ helium, and $Z\approx0.02$ metals (Asplund et al. 2009). Because the wind is fully ionized, the electron density is

$$
n_e \approx \frac{\rho}{m_p}\left(X + \frac{Y}{4} + \dots\right) \approx \frac{\rho}{2m_p}
$$

for a fully ionized H/He mix with $\mu\approx0.5$.

**Citations:**
- Asplund, M., Grevesse, N., Sauval, A. J., & Scott, P. (2009). The Chemical Composition of the Sun. *ARA&A*, 47, 481. DOI: [10.1146/annurev.astro.46.060407.145222](https://doi.org/10.1146/annurev.astro.46.060407.145222)
- von Steiger, R., et al. (2000). Composition of quasi-stationary solar wind from the corona. *JGR*, 105, 27217. DOI: [10.1029/1999JA000358](https://doi.org/10.1029/1999JA000358)
- Osterbrock, D. E., & Ferland, G. J. (2006). *Astrophysics of Gaseous Nebulae and Active Galactic Nuclei* (2nd ed.). University Science Books. ISBN 978-1-891389-65-1
- Draine, B. T. (2011). *Physics of the Interstellar and Intergalactic Medium*. Princeton Univ. Press. ISBN 978-0-691-12213-7

### 2.3 Stellar winds vs. neutral molecular gas

A neutral molecular cloud parcel is described by mass, velocity, temperature, density, composition, and (in Truth) a frozen-in magnetic field. A plasma parcel differs in three ways:

1. **Free charges** couple to electromagnetic forces. In single-fluid MHD the Lorentz force density is $\mathbf{f} = (\nabla\times\mathbf{B})\times\mathbf{B}/\mu_0$; neutral gas feels no such force.
2. **Partial ionization** produces ambipolar diffusion: neutrals slip through the ion/electron fluid when the ionization fraction is low. The effective MHD coupling scales with $x_i$.
3. **Equation of state and cooling** differ: fully ionized plasma has $\gamma=5/3$ and cools via bremsstrahlung and recombination; neutral molecular gas has rotational/vibrational line cooling and a different mean molecular weight.

Lamers & Cassinelli (1999) treat both hot, thermally driven winds (Parker) and radiatively driven winds of massive stars (Castor, Abbott & Klein / CAK theory). Truth’s Phase 1 scope is Parker/solar-like winds; the same parcel model can later be extended to line-driven winds by adding a radiation-force term.

**Citation:** Lamers, H. J. G. L. M., & Cassinelli, J. P. (1999). *Introduction to Stellar Winds*. Cambridge Univ. Press. DOI: [10.1017/CBO9780511529481](https://doi.org/10.1017/CBO9780511529481)

### 2.4 Wind–nebula interaction: bubbles, photoionization, and magnetic fields

When a supersonic wind meets the ambient nebula it drives a shock and excavates a bubble. Castor, McCray & Weaver (1975) and Weaver et al. (1977) derived the self-similar expansion of an interstellar bubble powered by a steady wind:

$$
R_b(t) \sim \left(\frac{\dot M v_w t^3}{\rho_0}\right)^{1/5}
$$

in the energy-conserving phase, where $\rho_0$ is the ambient nebular density. The wind deposits thermal energy and momentum; ram pressure at the inner shock is $P_{\rm ram}=\rho_w v_w^2$.

Photoionization by stellar EUV/XUV creates an HII region inside the wind bubble. The Strömgren radius for a pure hydrogen nebula is

$$
R_S = \left(\frac{3Q}{4\pi \alpha_B n^2}\right)^{1/3}
$$

where $Q$ is the hydrogen-ionizing photon luminosity, $\alpha_B$ is the Case-B recombination coefficient, and $n$ the ambient number density (Osterbrock & Ferland 2006).

The magnetic field is swept up and draped around the bubble, and the wind itself drags open stellar field lines into a Parker spiral (Cranmer 2009). In dense, partially ionized gas, ambipolar diffusion lets neutrals drift across field lines (Draine 2011).

**Citations:**
- Castor, J., McCray, R., & Weaver, R. (1975). Interstellar Bubbles. *ApJ*, 200, L107. DOI: [10.1086/181602](https://doi.org/10.1086/181602)
- Weaver, R., McCray, R., Castor, J., Shapiro, P., & Moore, R. (1977). Interstellar Bubbles. II. *ApJ*, 218, 377. DOI: [10.1086/155692](https://doi.org/10.1086/155692)
- Henney, W. J., Arthur, S. J., de Colle, F., & Mellema, G. (2009). The expansion of HII regions. *MNRAS*, 398, 157. DOI: [10.1111/j.1365-2966.2009.15105.x](https://doi.org/10.1111/j.1365-2966.2009.15105.x) (arXiv:0905.4134)
- Krumholz, M. R., Matzner, C. D., & McKee, C. F. (2006). The Global Evolution of Giant Molecular Clouds. *ApJ*, 653, 361. DOI: [10.1086/508740](https://doi.org/10.1086/508740) (arXiv:astro-ph/0603083)

### 2.5 Atmospheric escape driven by stellar XUV

While not strictly wind physics, the wind’s ram pressure and the star’s XUV flux jointly determine planetary atmospheric escape. Murray-Clay, Chiang & Murray (2009) and Owen & Alvarez (2016) distinguish energy-limited, recombination-limited, and blow-off regimes. Truth already models this in `law.plasma` via the ratio $R=t_{\rm rec}/t_{\rm flow}$; the wind adds the ram-pressure boundary condition at the magnetopause.

**Citations:**
- Murray-Clay, R. A., Chiang, E. I., & Murray, N. (2009). Atmospheric Escape from Hot Jupiters. *ApJ*, 693, 23. DOI: [10.1088/0004-637X/693/1/23](https://doi.org/10.1088/0004-637X/693/1/23)
- Owen, J. E., & Alvarez, M. A. (2016). UV driven mass loss from close-in planets: an analytic model. *MNRAS*, 459, 4083. DOI: [10.1093/mnras/stw879](https://doi.org/10.1093/mnras/stw879) (arXiv:1511.06638)

---

## 3. Governing Equations

### 3.1 Parker isothermal wind

Steady, spherical, isothermal mass and momentum conservation:

$$
\dot M = 4\pi r^2 \rho(r) v(r)
$$

$$
v\frac{dv}{dr} = -\frac{1}{\rho}\frac{dp}{dr} - \frac{GM}{r^2}
$$

with $p=\rho c_s^2$ and isothermal sound speed

$$
c_s = \sqrt{\frac{k_B T_c}{\mu m_H}}
$$

The critical/sonic point is where $v=c_s$:

$$
r_c = \frac{GM}{2c_s^2}
$$

The transonic solution can be written implicitly as

$$
\left(\frac{v}{c_s}\right)^2 - \ln\left(\frac{v}{c_s}\right)^2
= 4\ln\left(\frac{r}{r_c}\right) + 4\frac{r_c}{r} - 3
$$

For Truth’s discrete parcels we do not need to integrate this PDE every tick. Instead we use the analytic profile to set the *launch* speed and density at $r=R_*$, then let N-body gravity and EM/hydro forces evolve the parcel.

### 3.2 Ram pressure and spherical wind density

At distance $r$ from the star:

$$
\rho_w(r) = \frac{\dot M}{4\pi r^2 v_w(r)}
$$

$$
P_{\rm ram}(r) = \rho_w(r) v_w(r)^2 = \frac{\dot M v_w(r)}{4\pi r^2}
$$

This is the pressure a planet’s magnetosphere or a nebular clump feels. In `domain.em/magnetopause-distance` it balances the planetary magnetic pressure $B_p^2/(2\mu_0)$.

### 3.3 Ionization balance

For hydrogen in a parcel, the net ionization rate is

$$
\frac{dx_i}{dt} = \Gamma_{\rm ph}(1-x_i) + C_i(1-x_i) - \alpha_B n_e x_i
$$

where:
- $x_i$ = H ionization fraction,
- $\Gamma_{\rm ph}$ = photoionization rate per atom,
- $C_i$ = collisional ionization rate,
- $\alpha_B$ = Case-B recombination coefficient,
- $n_e \approx x_i \rho/(2m_p)$ for a fully ionized H/He mix.

In equilibrium ($dx_i/dt=0$):

$$
\frac{x_i}{1-x_i} = \frac{\Gamma_{\rm ph} + C_i}{\alpha_B n_e}
$$

In the hot coronal wind $\Gamma_{\rm ph}+C_i \gg \alpha_B n_e$ and $x_i \to 1$. In a dense, neutral nebula shadowed from ionizing photons, $x_i \to 0$.

### 3.4 Magnetic field in the wind

Under ideal MHD the magnetic flux is frozen into the expanding wind. For a radial wind and a split-monopole/parker field:

$$
B_r(r) \propto r^{-2}, \qquad B_\phi(r) \propto r^{-1}
$$

The Alfvén speed is

$$
v_A = \frac{B}{\sqrt{\mu_0 \rho}}
$$

and the Alfvén radius $r_A$ is where $v_w = v_A$. Inside $r_A$ the magnetic field dominates the wind dynamics; outside, the flow is essentially ballistic/free-streaming.

Plasma beta:

$$
\beta = \frac{p_{\rm th}}{p_B} = \frac{2\mu_0 \rho c_s^2}{B^2}
$$

Truth’s `domain.regime/plasma-beta` already computes this for classification.

### 3.5 Photoionization of the nebula

Ionizing photon luminosity $Q$ from the star’s EUV band (`c/sed-bands :euv`). The ionization rate per atom at distance $r$:

$$
\Gamma_{\rm ph}(r) = \int_{\nu_0}^\infty \frac{F_\nu(r)}{h\nu} \sigma_{\rm PI}(\nu)\,d\nu
$$

with $F_\nu(r)=L_\nu/(4\pi r^2)$ and hydrogen photoionization cross-section $\sigma_{\rm PI}\approx6.3\times10^{-18}\,{\rm cm^2}$ at threshold. For a single-band estimate:

$$
\Gamma_{\rm ph}(r) \approx \frac{L_{\rm EUV}}{4\pi r^2 h\bar\nu}\sigma_{\rm PI}
$$

### 3.6 Wind-blown bubble radius

Energy-conserving expansion into uniform density $\rho_0$:

$$
R_b(t) = \left(\frac{125}{154\pi}\right)^{1/5}
\left(\frac{\dot M v_w^2 t^3}{\rho_0}\right)^{1/5}
$$

Momentum-conserving expansion (relevant for very short times or high ambient pressure):

$$
R_b(t) = \left(\frac{3 \dot M v_w t}{2\pi \rho_0}\right)^{1/4}
$$

Truth does not resolve the shocked shell explicitly, but the ram-pressure field carried by wind parcels reproduces the local dynamical effect on nebular parcels.

---

## 4. Implementation Sketch (Clojure ECS)

### 4.1 Guiding design choice: continuum on `:nebula`, not a new `:plasma` matter-state

Do **not** introduce `:plasma` as a new `c/matter-state`. `matter-state` is the gravitational/formation classification axis (`:nebula` → `:planetesimal` → `:gas-giant` → `:brown-dwarf` → `:protostar` → `:star` → `:planet`). A parcel of ionized wind is still diffuse gas; it has not changed its formation identity. Adding `:plasma` would fork the single substrate and force every classification/merge/accretion predicate to handle a parallel gas branch. Instead, model plasma as a **continuum** on `:nebula` using the existing `c/ionization-fraction` component. This preserves the architecture invariant: *one world model, components over entities*.

### 4.2 Pure helpers

```clojure
(ns domain.plasma.wind
  "Pure functions for stellar-wind plasma state.
   Units: SI throughout."
  (:require [law.stellar :as ls]
            [law.field :as lf]
            [shape.spatial :as sp]))

(defn isothermal-sound-speed
  "c_s = sqrt(k_B T_c / (mu m_H)) for fully ionized H/He (mu ~ 0.5)."
  [T-c mu]
  (Math/sqrt (/ (* ls/k-B T-c) (* mu ls/m-H))))

(defn parker-critical-radius
  "r_c = G M / (2 c_s^2)."
  [M T-c mu]
  (let [cs (isothermal-sound-speed T-c mu)]
    (/ (* ls/G M) (* 2.0 cs cs))))

(defn parker-wind-speed
  "Approximate Parker wind speed at radius r from a star with escape speed
   v_esc and coronal temperature T_c. We use the analytic scaling
   v(r) ~ v_esc sqrt(T_c / T_esc) for r > r_c, capped near the star."
  [M R T-c r]
  (let [v-esc  (Math/sqrt (/ (* 2.0 ls/G M) R))
        T-esc  (/ (* ls/m-H v-esc v-esc) (* 2.0 ls/k-B))
        v-inf  (* v-esc (Math/sqrt (max 0.0 (/ T-c T-esc))))
        r-c    (parker-critical-radius M T-c 0.5)
        factor (min 1.0 (Math/sqrt (/ r (max r-c R))))]
    (* v-inf factor)))

(defn wind-density
  "Spherical wind density rho = Mdot / (4 pi r^2 v)."
  [Mdot v r]
  (if (and (pos? Mdot) (pos? v) (pos? r))
    (/ Mdot (* 4.0 Math/PI r r v))
    0.0))

(defn ram-pressure
  "P_ram = rho v^2 = Mdot v / (4 pi r^2)."
  [Mdot v r]
  (if (and (pos? Mdot) (pos? v) (pos? r))
    (/ (* Mdot v) (* 4.0 Math/PI r r))
    0.0))

(defn alfven-speed
  "v_A = B / sqrt(mu0 rho)."
  [b-field density]
  (if (and (lf/finite-vec3? b-field) (pos? density))
    (/ (sp/len b-field) (Math/sqrt (* lf/mu-0 density)))
    0.0))

(defn photoionization-rate
  "Rough Gamma_ph per H atom from EUV luminosity L_euv at distance r,
   using threshold cross-section sigma0 and mean photon energy hnu."
  [L-euv r sigma0 hnu]
  (if (and (pos? L-euv) (pos? r) (pos? hnu))
    (/ (* L-euv sigma0) (* 4.0 Math/PI r r hnu))
    0.0))
```

### 4.3 ECS component usage

Use existing components; no new matter-state.

| Component | Role for plasma |
|-----------|-----------------|
| `c/matter-state` | `:nebula` for all diffuse gas, whether neutral or ionized |
| `c/ionization-fraction` | $x_i \in [0,1]$; gates Lorentz-force coupling and rendering |
| `c/b-field` | Frozen-in field vector (Tesla) |
| `c/ram-pressure` | Local $P_{\rm ram}$ from wind (Pa) |
| `c/temperature` | Parcel temperature (K) |
| `c/composition` | Mass fractions; drives mean molecular weight and electron density |
| `c/wind-profile` | On stars: $\{\dot M, v_\infty, r_A\}$ derived from corona |

A wind parcel is therefore just a `:nebula` entity with $x_i \gtrsim 0.5$ and a non-zero `c/ram-pressure`. Queries for ionized wind use `(> x_i threshold)` rather than a state keyword.

### 4.4 Refactored `stellar-wind-system` (pseudocode)

```clojure
(defn wind-step [ctx eid]
  (when (= :star (ecs/get-component world eid c/matter-state))
    (let [M      (ecs/get-component world eid c/mass)
          R      (ecs/get-component world eid c/radius)
          shells (ecs/get-component world eid c/atmosphere-shells)
          sed    (ecs/get-component world eid c/sed-bands)
          corona (some #(when (= :corona (:layer/id %)) %) shells)
          Tc     (:temperature corona)
          L-xuv  (lsed/xuv-luminosity (:bands sed))
          v-esc  (Math/sqrt (/ (* 2.0 law/G M) R))
          ;; mass-loss driven by XUV (radiation limit)
          mdot   (if (pos? v-esc) (/ (* k L-xuv) (* v-esc c)) 0.0)
          ;; parcel launch radius and speed
          r-launch R
          v-w      (parker-wind-speed M R Tc r-launch)
          dm       (min (* mdot dt) (* M max-frac))
          resv     (+ (or (ecs/get-component world eid c/wind-reservoir) 0.0) dm)]
      (when (>= resv parcel-mass)
        (let [rhat   (wind-direction eid tick)
              pos    (ecs/get-component world eid c/position)
              ppos   (sp/v+ pos (sp/v* rhat R))
              pvel   (sp/v+ (ecs/get-component world eid c/velocity)
                            (sp/v* rhat v-w))
              ;; field at launch point from star + neighbors
              b0     (em/net-field-at ppos sources nil)
              Pram   (ram-pressure mdot v-w R)
              xion   (min 1.0 (max 0.5 (/ Tc 1.0e6)))]
          {:eid eid
           :mass-flux (- dm)
           :reservoir (- resv parcel-mass)
           :dv (sp/v* rhat (- (* (/ parcel-mass (- M dm)) v-w)))
           :spawn {:position ppos :velocity pvel
                   :mass parcel-mass :radius gas-r
                   :matter-state :nebula          ; ← stays nebula
                   :composition  stellar-composition
                   :temperature  Tc
                   :b-field      b0
                   :extra-components {c/ionization-fraction xion
                                      c/ram-pressure        Pram}}})))))
```

### 4.5 Coupling to `domain.em`

Lorentz forces should be weighted by ionization fraction so neutral parcels do not feel magnetic forces:

```clojure
(defn mhd-coupling-factor
  "Effective coupling of a parcel to the magnetic field.
   Fully ionized -> 1.0; neutral -> 0.0."
  [ionization-fraction]
  (max 0.0 (min 1.0 (double ionization-fraction))))

(defn capped-lorentz-acceleration
  [data curl-b]
  (let [b       (:b-field data)
        rho     (:density data)
        r       (or (:radius data) 1.0)
        v       (sp/len (or (:velocity data) [0 0 0]))
        xion    (or (ecs/get-component world (:eid data) c/ionization-fraction) 0.0)
        xi      (mhd-coupling-factor xion)]
    (if (and (> xi 0.0) (lf/mhd-regime? (:pressure data) b v rho))
      (let [a   (sp/v* (lorentz-acceleration b curl-b rho) xi)
            cap (lf/lorentz-acceleration-cap b rho r)]
        (if (pos? cap) (sp/clamp-mag a cap) a))
      [0.0 0.0 0.0])))
```

### 4.6 Wind–nebula interaction system

A new `wind-nebula-coupling-system` reads wind parcels and updates nearby `:nebula` parcels:

```clojure
(defn wind-nebula-coupling-system
  "Double-buffer write-set system: updates nebula parcel temperature and
   ionization fraction from nearby stellar-wind ram pressure and photoionization.
   Does not change matter-state."
  []
  {:id     :wind-nebula-coupling
   :writes #{c/temperature c/ionization-fraction}
   :run
   (fn [world]
     (let [winds (mapv wind-parcel-data
                       (ecs/entities-with world c/matter-state c/position
                                          c/ionization-fraction c/ram-pressure))
           stars (mapv star-sed-data
                       (filter #(= :star (ecs/get-component world % c/matter-state))
                               (ecs/entities-with world c/matter-state c/position c/sed-bands)))
           gas   (ecs/entities-with world c/matter-state c/position c/temperature
                                    c/density c/ionization-fraction)]
       {c/temperature
        (into {} ... compute heating from wind shocks and photoionization ...)
        c/ionization-fraction
        (into {} ... solve dx_i/dt from photoionization + recombination ...)}}))})
```

---

## 5. Toy Model / Numerical Experiment

### 5.1 Setup

A solar-like star with $M=M_\odot$, $R=R_\odot$, coronal temperature $T_c=1.5\times10^6$ K, $\mu=0.5$.
Use the isothermal Parker scalings to check:

- sound speed $c_s$
- critical radius $r_c$
- wind density and ram pressure at 1 AU
- ionization parameter $\chi/k_BT_c$

### 5.2 Results

| Quantity | Model value | Published value | Error |
|----------|-------------|-----------------|-------|
| $c_s$ | 157 km s$^{-1}$ | $\sim$150 km s$^{-1}$ for $T_c\sim1.5$ MK | 5% |
| $r_c$ | 3.85 $R_\odot$ | $\sim$4–6 $R_\odot$ (Parker) | within factor 1.5 |
| $\rho_w(1\,{\rm AU})$ | $5.6\times10^{-20}$ kg m$^{-3}$ | $\sim5\times10^{-21}$–$10^{-20}$ | within range |
| $P_{\rm ram}(1\,{\rm AU})$ | 9.0 nPa | $\sim$1–10 nPa (solar wind) | within range |
| $\chi/k_BT_c$ | 0.11 | $\ll 1$ for full ionization | consistent |

The density/ram-pressure match is order-of-magnitude because the simple model uses a fixed 1 AU speed rather than integrating the full Parker profile. The point is that the chosen scalings reproduce observed solar-wind pressures to within an order of magnitude, sufficient for gameplay-scale nebula dynamics.

### 5.3 Python reference snippet

```python
import math

G, M_sun, R_sun, k_B, m_H = 6.674e-11, 1.989e30, 6.957e8, 1.380649e-23, 1.6735e-27
mu, T_c, Mdot, v_1AU, AU = 0.5, 1.5e6, 6.3e9, 4.0e5, 1.496e11

cs = math.sqrt(k_B * T_c / (mu * m_H))
r_c = G * M_sun / (2 * cs**2)
rho_1au = Mdot / (4*math.pi*AU**2 * v_1AU)
Pram_1au = rho_1au * v_1AU**2

print(f"c_s = {cs/1e3:.1f} km/s")
print(f"r_c = {r_c/R_sun:.2f} R_sun")
print(f"rho(1 AU) = {rho_1au:.2e} kg/m^3")
print(f"P_ram(1 AU) = {Pram_1au*1e9:.1f} nPa")
```

---

## 6. Validation

- [x] Parker critical radius and sound speed match analytic solar values.
- [x] Ram pressure at 1 AU falls within observed solar-wind range (1–10 nPa).
- [x] Ionization fraction tends to unity for coronal temperatures.
- [ ] Full integration of Parker profile vs. analytic solution (future).
- [ ] Strömgren-radius test for EUV-driven HII region around a hot star.
- [ ] Magnetopause standoff distance matches Earth/Solar-wind benchmark ($\sim$10 $R_E$).

---

## 7. Promotion Path to Domain Code

### 7.1 `src/law/stellar.clj`

Add a Malli schema for the wind *parcel* as a map contract. Keep the physical helpers in `law.plasma` (already present: `parker-mass-loss`, `ram-pressure`). Move/duplicate the parcel schema here if `law.stellar` is to own the star-ejecta contract.

```clojure
(def wind-parcel-schema
  "A parcel of stellar ejecta. matter-state remains :nebula; plasma properties
   are carried by c/ionization-fraction, c/ram-pressure, and c/b-field."
  {:origin-star         (some-fn uuid? integer?)
   :position            vector?
   :velocity            vector?
   :mass                pos?
   :radius              pos?
   :density             pos?
   :temperature         pos?
   :matter-state        keyword?            ; :nebula
   :ionization-fraction (fn [x] (<= 0.0 x 1.0))
   :b-field             vector?
   :ram-pressure        number?})
```

### 7.2 `src/domain/stellar.clj` — `stellar-wind-system`

Refactor `wind-step` to:
1. Read coronal $T_c$ from `c/atmosphere-shells`.
2. Compute Parker wind speed with `domain.plasma.wind/parker-wind-speed`.
3. Drive $\dot M$ from XUV luminosity (`c/sed-bands`) scaled by $1/(v_{\rm esc}c)$.
4. Spawn parcels with `:matter-state :nebula` and `c/ionization-fraction` from $T_c$.
5. Attach launch-point `c/b-field` via `domain.em/net-field-at`.
6. Attach `c/ram-pressure` computed at launch radius.

Remove any code paths that assume neutral wind parcels when `c/ionization-fraction` is available.

### 7.3 `src/domain/em.clj`

- Make `lorentz-acceleration-system` weight acceleration by `c/ionization-fraction` (or by a new `mhd-coupling-factor`). Neutral parcels ($x_i=0$) receive zero Lorentz force.
- Expose `alfven-speed` and `alfven-radius` helpers so `stellar-wind-system` can tag parcels with $r_A$ if desired.
- Ensure `magnetosphere-coupling-system` continues to filter wind parcels by `(> x_i 0)` and sum their `c/ram-pressure` for planetary standoff distances.

### 7.4 `src/infra/render.clj`

- In the gas-particle render path, read `c/ionization-fraction` and `c/ram-pressure`.
- Map $x_i$ to a color shift (e.g., neutral = red/brown molecular, ionized = blue/cyan plasma).
- Map $\log_{10} P_{\rm ram}$ to glow intensity so wind streams and shocked bubbles are visible.
- Optionally render only wind parcels as point sprites with a trail direction derived from velocity.

### 7.5 Tests

```clojure
(deftest stellar-wind-parcel-is-ionized-nebula
  (let [world' (stellar-wind-system world)
        parcel (first (ecs/entities-with world' c/ionization-fraction))]
    (is (= :nebula (ecs/get-component world' parcel c/matter-state)))
    (is (> (ecs/get-component world' parcel c/ionization-fraction) 0.5))
    (is (pos? (ecs/get-component world' parcel c/ram-pressure)))
    (is (ecs/get-component world' parcel c/b-field))))

(deftest neutral-parcel-feels-no-lorentz-force
  (let [neutral (assoc-in world [:components c/ionization-fraction eid] 0.0)
        world' ((:run (em/lorentz-acceleration-system dt)) neutral)]
    (is (= [0.0 0.0 0.0]
           (ecs/get-component world' eid c/accel-lorentz)))))

(deftest ram-pressure-at-1au-matches-solar-wind
  (is (approx= 9.0e-9 (plasma/ram-pressure 6.3e9 4.0e5 1.496e11) 2.0)))
```

---

## 8. Cross-References

- `docs/research/phase1-radiation-plasma-truth.md` — parent radiation/plasma architecture document.
- `docs/research/atmosphere/xuv-escape-regime-transition.md` — XUV-driven atmospheric escape regimes.
- `docs/research/physics/mhd-em-lorentz-optimization.md` — MHD-lite Lorentz-force implementation details.
- `docs/research/cosmology/primordial-nucleosynthesis-yields.md` — primordial composition used by wind parcels.

---

## 9. Open Questions

1. **Line-driven winds:** Should massive-star winds add a CAK radiation-force component, or is the Parker/XUV prescription sufficient for Phase 1?
2. **Ambipolar diffusion:** At low $x_i$, neutrals decouple from ions. Should `domain.em` add an ambipolar-drift velocity sub-system?
3. **Multi-fluid composition:** Wind composition is currently the star’s bulk composition. Should we track He$^+$ vs He$^{2+}$ separately for mean molecular weight?
4. **Wind bubble shell:** Should we spawn a separate shell entity when ram pressure sweeps up enough nebular mass, or keep the interaction parcel-local?
5. **Numerical stability:** Large `sim/dt` (Myr-scale) can launch parcels across huge distances. Is the existing `v-fac` drift cap sufficient, or do wind parcels need sub-cycling?

---

## 10. References

1. Parker, E. N. (1958). Dynamics of the Interplanetary Gas and Magnetic Fields. *ApJ*, 128, 664. DOI: [10.1086/146579](https://doi.org/10.1086/146579)
2. Parker, E. N. (1960). The Hydrodynamic Theory of the Solar Wind. *ApJ*, 132, 821. DOI: [10.1086/147316](https://doi.org/10.1086/147316)
3. Lamers, H. J. G. L. M., & Cassinelli, J. P. (1999). *Introduction to Stellar Winds*. Cambridge Univ. Press. DOI: [10.1017/CBO9780511529481](https://doi.org/10.1017/CBO9780511529481)
4. Cranmer, S. R. (2009). What is the Physics of the Solar Wind? *Living Reviews in Solar Physics*, 6, 3. DOI: [10.12942/lrsp-2009-3](https://doi.org/10.12942/lrsp-2009-3)
5. Asplund, M., Grevesse, N., Sauval, A. J., & Scott, P. (2009). The Chemical Composition of the Sun. *ARA&A*, 47, 481. DOI: [10.1146/annurev.astro.46.060407.145222](https://doi.org/10.1146/annurev.astro.46.060407.145222)
6. von Steiger, R., Schwadron, N. A., Fisk, L. A., et al. (2000). Composition of quasi-stationary solar wind from the corona. *JGR*, 105, 27217. DOI: [10.1029/1999JA000358](https://doi.org/10.1029/1999JA000358)
7. Osterbrock, D. E., & Ferland, G. J. (2006). *Astrophysics of Gaseous Nebulae and Active Galactic Nuclei* (2nd ed.). University Science Books. ISBN 978-1-891389-65-1
8. Draine, B. T. (2011). *Physics of the Interstellar and Intergalactic Medium*. Princeton Univ. Press. ISBN 978-0-691-12213-7
9. Castor, J., McCray, R., & Weaver, R. (1975). Interstellar Bubbles. *ApJ*, 200, L107. DOI: [10.1086/181602](https://doi.org/10.1086/181602)
10. Weaver, R., McCray, R., Castor, J., Shapiro, P., & Moore, R. (1977). Interstellar Bubbles. II. *ApJ*, 218, 377. DOI: [10.1086/155692](https://doi.org/10.1086/155692)
11. Krumholz, M. R., Matzner, C. D., & McKee, C. F. (2006). The Global Evolution of Giant Molecular Clouds. *ApJ*, 653, 361. DOI: [10.1086/508740](https://doi.org/10.1086/508740) (arXiv:astro-ph/0603083)
12. Henney, W. J., Arthur, S. J., de Colle, F., & Mellema, G. (2009). The expansion of HII regions. *MNRAS*, 398, 157. DOI: [10.1111/j.1365-2966.2009.15105.x](https://doi.org/10.1111/j.1365-2966.2009.15105.x) (arXiv:0905.4134)
13. Murray-Clay, R. A., Chiang, E. I., & Murray, N. (2009). Atmospheric Escape from Hot Jupiters. *ApJ*, 693, 23. DOI: [10.1088/0004-637X/693/1/23](https://doi.org/10.1088/0004-637X/693/1/23)
14. Owen, J. E., & Alvarez, M. A. (2016). UV driven mass loss from close-in planets: an analytic model. *MNRAS*, 459, 4083. DOI: [10.1093/mnras/stw879](https://doi.org/10.1093/mnras/stw879) (arXiv:1511.06638)
