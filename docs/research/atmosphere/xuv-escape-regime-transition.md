# XUV-Driven Atmospheric Escape: Regime Transitions

**Domain:** atmosphere | **Phase:** cross-phase (affects all planet types)
**Date:** 2026-06-28 | **Author:** truth-research-atmosphere
**Status:** draft
**Primary sources:** arXiv:0811.0006, arXiv:2104.08832, arXiv:2502.08398, arXiv:2510.23857

---

## 1. Research Question

For the Gates of Truth simulation, we need to model atmospheric mass loss
driven by stellar XUV (X-ray + EUV) irradiation. Two (or three) distinct
regimes exist, each with different scaling laws. The critical question:
**what dimensionless parameter or flux threshold controls the transition
between the energy-limited and recombination-limited regimes, and how does
it depend on planetary properties?**

This matters because:
- Overestimating mass loss in the recombination-limited regime yields
  planets that are stripped too aggressively.
- Underestimating mass loss in the energy-limited regime preserves
  atmospheres that should be lost.
- The regime determines whether Lyα and He 10830Å absorption is detectable.

---

## 2. Literature Survey

### 2.1 Murray-Clay, Chiang & Murray (2009) — The Foundational Model

The seminal 1D radiation-hydrodynamic model of atmospheric escape from hot
Jupiters. Includes photoionization heating, Lyα cooling, H₃⁺ cooling,
ionization balance, tidal gravity, and stellar wind confinement.

> **Key finding:** For UV fluxes F_UV < 10⁴ erg cm⁻² s⁻¹, the mass loss
> rate is approximately **energy-limited** and scales as Ṁ ∝ F_UV^0.9.
> For larger fluxes (typical of T Tauri stars), radiative losses and
> recombination force Ṁ to increase more slowly as Ṁ ∝ F_UV^0.6.

**Citation:** Murray-Clay, R., Chiang, E., & Murray, N. (2009).
"Atmospheric Escape from Hot Jupiters." ApJ, 693(1), 23.
arXiv:0811.0006. DOI:10.1088/0004-637X/693/1/23

The transition flux F_UV ~ 10⁴ erg cm⁻² s⁻¹ corresponds to the point where
radiative cooling (primarily Lyα) becomes comparable to the XUV heating rate.
Below this threshold, essentially all absorbed XUV energy goes into PdV work
(escaping kinetic energy + gravitational binding). Above it, a significant
fraction is radiated away before it can accelerate the wind.

### 2.2 Owen & Jackson (2012) — Two-Regime Classification

Owen & Jackson formalized the distinction between the two primary regimes
and showed that the transition depends on whether the recombination timescale
is shorter or longer than the flow (advection) timescale.

**Citation:** Owen, J.E. & Jackson, A.P. (2012). "Photoevaporation flows
from exoplanets—I. Hydrodynamic models." MNRAS, 425(4), 2931-2949.
DOI:10.1111/j.1365-2966.2012.21481.x

### 2.3 Owen & Alvarez (2016) — Unified Framework

Extended the analysis to show that the energy-limited formula is actually
an **upper bound** that only holds when radiative cooling is negligible.
Introduced the concept that the transition is controlled by the ratio of
the XUV heating timescale to the radiative cooling timescale.

**Citation:** Owen, J.E. & Alvarez, M.A. (2016). "UV Driven Evaporation
of Close-in Planets: Energy-limited, Recombination-limited, and
Photon-limited Flows." ApJ, 816(1), 34.
DOI:10.3847/0004-637X/816/1/34

### 2.4 Lampón et al. (2021) — First Observational Evidence of All Three Regimes

This paper provides the **first observational evidence** of all three
hydrodynamic escape regimes by analyzing He I 10830Å and Lyα absorption:

| Planet | Regime | Evidence |
|--------|--------|----------|
| HD 209458 b | Energy-limited | Mass loss consistent with η ~ 0.1-0.4 |
| HD 189733 b | Recombination-limited | Observed mass loss < energy-limited prediction |
| GJ 3470 b | Photon-limited | Mass loss limited by available ionizing photons |

> **Key finding:** HD 209458 b (moderate XUV, massive planet) is
> energy-limited. HD 189733 b (higher XUV, closer orbit) is
> recombination-limited. GJ 3470 b (low-mass, high XUV from M-dwarf)
> is photon-limited.

**Citation:** Lampón, M. et al. (2021). "Evidence of energy-, recombination-,
and photon-limited escape regimes in giant planet H/He atmospheres." A&A,
648, L7. arXiv:2104.08832. DOI:10.1051/0004-6361/202140423

### 2.5 Mitani, Nakatani & Kuiper (2025) — Analytic Efficiency Model

Introduces physically motivated temperatures and timescales to derive an
analytic model for mass-loss efficiency that works across both regimes.
The model predicts efficiency η as a function of planetary and stellar
parameters.

**Citation:** Mitani, H., Nakatani, R. & Kuiper, R. (2025). "Physically
motivated analytic model of energy efficiency for EUV-driven atmospheric
escape of close-in exoplanets." A&A, 695, A153. arXiv:2502.08398.
DOI:10.1051/0004-6361/202452749

### 2.6 Broome, Murray-Clay, McCann & Owen (2025) — Wind-AE Code

Open-source 1D Parker Wind photoevaporation model (Wind-AE) based on
Murray-Clay et al. (2009). Key findings:
- R_XUV ~ 1.1-1.8 R_P for hot Jupiters at low flux
- R_XUV >> R_P for sub-Neptunes/super-Earths (low escape velocity)
- For high escape velocities AND large fluxes: radiative cooling is
  significant and energy-limited overestimates Ṁ

**Citation:** Broome, M. et al. (2025). "Wind-AE: A Fast, Open-source 1D
Photoevaporation Code with Metal and Multi-frequency X-ray Capabilities."
arXiv:2510.23857.

### 2.7 Caldiroli et al. (2025) — Flare Impact on Regime Switching

Shows that M-dwarf flares cause planets to oscillate between energy-limited
and recombination-limited regimes. The **proportion of time** a planet
spends in each regime depends on orbital separation.

**Citation:** Caldiroli, A. et al. (2025). "Why M-dwarf flares have limited
impact on the atmospheric evaporation of sub-Neptunes and Earth-sized
planets." A&A, 702, A112. arXiv:2506.08014.

### 2.8 Lammer et al. (2013) — Blow-Off Criteria

Investigates the conditions under which hydrogen-rich super-Earths experience
hydrodynamic blow-off. Key result: Roche lobe overflow becomes important
for close-in planets with inflated atmospheres.

**Citation:** Lammer, H. et al. (2013). "Probing the Blow-Off Criteria of
Hydrogen-Rich Super-Earths." MNRAS, 430(2), 1247-1256. arXiv:1210.0793.

---

## 3. Governing Equations

### 3.1 Energy-Limited Mass Loss Rate

The standard energy-limited formula:

$$\dot{M}_{\rm el} = \frac{\epsilon \pi R_p^3 F_{\rm XUV}}{G M_p K_{\rm tide}}$$

where:
- $\epsilon$ = heating efficiency (typically ~0.1-0.4)
- $R_p$ = planetary radius (specifically $R_{\rm XUV}$, the XUV absorption radius)
- $F_{\rm XUV}$ = incident XUV flux at orbital distance $a$: $F_{\rm XUV} = L_{\rm XUV} / (4\pi a^2)$
- $G$ = gravitational constant
- $M_p$ = planetary mass
- $K_{\rm tide}$ = Roche lobe correction factor (Erkaev et al. 2007):

$$K_{\rm tide} = 1 - \frac{3}{2\xi} + \frac{1}{2\xi^3}, \quad \xi = \frac{R_{\rm Roche}}{R_p}$$

### 3.2 Recombination-Limited Mass Loss Rate

When radiative cooling and recombination dominate:

$$\dot{M}_{\rm rec} \approx 4\pi R_p^2 \rho_{\rm sonic} c_s$$

where the sonic point density is set by the balance between photoionization
and recombination. From Murray-Clay et al. (2009), the scaling transitions
from Ṁ ∝ F^0.9 to Ṁ ∝ F^0.6.

### 3.3 Photon-Limited Mass Loss Rate

When essentially every incident photon produces an escaping atom:

$$\dot{M}_{\rm phot} \approx \frac{4\pi a^2 F_{\rm XUV} m_H}{h\nu_{\rm eff}}$$

where $h\nu_{\rm eff}$ is the mean energy per ionizing photon.

### 3.4 The Transition Criterion

The transition between energy-limited and recombination-limited is controlled
by comparing the **recombination timescale** $t_{\rm rec}$ to the
**flow/advection timescale** $t_{\rm flow}$:

$$t_{\rm rec} = \frac{1}{n_e \alpha_B}$$

$$t_{\rm flow} \approx \frac{R_p}{c_s}$$

where $\alpha_B \approx 2.6 \times 10^{-13}$ cm³ s⁻¹ is the Case B
recombination coefficient for hydrogen at T ~ 10⁴ K, and $n_e$ is the
electron density at the base of the flow.

**The dimensionless transition parameter:**

$$\mathcal{R} \equiv \frac{t_{\rm rec}}{t_{\rm flow}} = \frac{c_s}{n_e \alpha_B R_p}$$

- $\mathcal{R} \gg 1$: recombination is slow → **energy-limited**
  (all absorbed energy goes into escape)
- $\mathcal{R} \ll 1$: recombination is fast → **recombination-limited**
  (radiative losses dominate)

Equivalently, this can be expressed in terms of the incident XUV flux.
The critical flux where the transition occurs:

$$F_{\rm XUV, crit} \approx \frac{4 \alpha_B m_H c_s^3}{\epsilon \sigma_{\rm PI}} \cdot \frac{1}{f_{\rm ion}}$$

where $\sigma_{\rm PI}$ is the photoionization cross section and $f_{\rm ion}$
is the ionization fraction at the base. For typical hot Jupiter parameters,
this gives:

$$F_{\rm XUV, crit} \sim 10^4 \; {\rm erg \; cm^{-2} \; s^{-1}}$$

This matches Murray-Clay et al. (2009)'s empirical finding.

### 3.5 Roche Lobe Overflow (Blow-Off)

A third consideration: when the planet's atmosphere expands to fill its Roche
lobe, mass loss is enhanced by tidal effects. The Roche lobe radius:

$$R_{\rm Roche} \approx a \left(\frac{M_p}{3 M_*}\right)^{1/3}$$

For close-in sub-Neptunes, $R_{\rm XUV}$ can approach $R_{\rm Roche}$,
triggering Roche lobe overflow even at moderate XUV fluxes (Lammer et al. 2013).

---

## 4. Implementation Sketch (Clojure Pseudocode)

### 4.1 Malli Schemas (law/)

```clojure
(def xuv-escape-regime-schema
  [:enum :energy-limited :recombination-limited :photon-limited :blow-off])

(def xuv-escape-params-schema
  [:map
   [:M_p    number?]    ; planet mass (g)
   [:R_p    number?]    ; planet radius (cm)
   [:R_xuv  number?]    ; XUV absorption radius (cm)
   [:a      number?]    ; orbital distance (cm)
   [:M_star number?]    ; stellar mass (g)
   [:F_xuv  number?]    ; incident XUV flux (erg cm^-2 s^-1)
   [:L_xuv  number?]    ; stellar XUV luminosity (erg s^-1)
   [:epsilon [:maybe number]] ; heating efficiency (nil = auto)
   [:mu     number?]])  ; mean molecular weight (1.0 for H, 1.35 for solar comp)

(def xuv-escape-result-schema
  [:map
   [:regime      xuv-escape-regime-schema]
   [:M_dot       number?]  ; mass loss rate (g s^-1)
   [:efficiency  number?]  ; actual efficiency
   [:F_xuv_crit  number?]  ; critical flux for transition
   [:R_tide      number?]  ; Roche lobe correction])
```

### 4.2 Regime Selector (domain/)

```clojure
(defn xuv-escape-regime
  "Determine the atmospheric escape regime for a planet.
   Returns a map with :regime, :M_dot, :efficiency, :F_xuv_crit."
  [{:keys [M_p R_p R_xuv a M_star F_xuv L_xuv epsilon mu]
    :or {epsilon 0.15 mu 1.35}}]
  (let [;; Physical constants
        G      6.674e-8        ; gravitational constant
        k_B    1.381e-16       ; Boltzmann constant
        m_H    1.673e-24       ; hydrogen mass
        alpha_B 2.6e-13        ; Case B recombination coeff (cm^3 s^-1)
        sigma_PI 6.3e-18       ; H photoionization cross section (cm^2)
        
        ;; Derived quantities
        R_roche (* a (Math/pow (/ M_p (* 3 M_star)) 1/3))
        K_tide  (let [xi (/ R_roche R_p)]
                  (- 1.0 (/ 3.0 (* 2 xi)) (/ 1.0 (* 2 (Math/pow xi 3)))))
        
        ;; Sound speed in ionized H (T ~ 10^4 K)
        T_xuv   1.0e4          ; typical XUV-heated temperature
        c_s     (Math/pow (* 2 k_B T_xuv (* mu m_H)) 0.5)
        
        ;; Photoionization timescale
        t_pi    (/ 1.0 (* F_xuv (/ sigma_PI (* 13.6 1.602e-12))))
                ; = hν / (F_xuv * sigma_PI) — time between ionizations per atom
        
        ;; Recombination timescale at sonic point
        ;; Using approximate base density from XUV absorption
        n_base  (/ F_xuv (* sigma_PI c_s 13.6 1.602e-12))
                ; approximate electron density at sonic point
        t_rec   (/ 1.0 (* n_base alpha_B))
        
        ;; Flow timescale
        t_flow  (/ R_xuv c_s)
        
        ;; Dimensionless regime parameter
        R_ratio (/ t_rec t_flow)
        
        ;; Critical XUV flux for transition
        ;; From balancing photoionization rate with recombination rate
        F_xuv_crit (* 4 alpha_B m_H (Math/pow c_s 3)
                      (/ 1.0 (* epsilon sigma_PI)))
        
        ;; Energy-limited mass loss rate
        M_dot_el (/ (* epsilon Math/PI (Math/pow R_xuv 3) F_xuv)
                    (* G M_p K_tide))
        
        ;; Recombination-limited scaling: M_dot ∝ F^0.6 / (R_p^0.5)
        ;; Normalized to match energy-limited at F_xuv_crit
        M_dot_rec (* M_dot_el (Math/pow (/ F_xuv F_xuv_crit) -0.3))
        
        ;; Photon-limited: every photon produces an escaping atom
        h_nu_eff (* 20.0 1.602e-12)  ; ~20 eV mean photon energy
        M_dot_phot (/ (* Math/PI (Math/pow a 2) F_xuv m_H) h_nu_eff)
        
        ;; Select regime and compute final M_dot
        regime  (cond
                  ;; Roche lobe overflow check
                  (>= R_xuv (* 0.9 R_roche))
                  :blow-off
                  
                  ;; Energy-limited: R_ratio >> 1 (recombination slow)
                  (> R_ratio 1.0)
                  :energy-limited
                  
                  ;; Recombination-limited: R_ratio << 1, F_xuv moderate
                  (and (< R_ratio 1.0) (> F_xuv (* 0.01 F_xuv_crit)))
                  :recombination-limited
                  
                  ;; Photon-limited: very high F_xuv
                  :else
                  :photon-limited)
        
        M_dot  (case regime
                 :energy-limited        M_dot_el
                 :recombination-limited M_dot_rec
                 :photon-limited        M_dot_phot
                 :blow-off              (* M_dot_el 2.0)) ; enhanced by tide
        ]
    
    {:regime      regime
     :M_dot       M_dot
     :efficiency  (/ M_dot M_dot_el)
     :F_xuv_crit  F_xuv_crit
     :R_tide      K_tide
     :R_ratio     R_ratio}))
```

### 4.3 ECS System Integration

```clojure
(defrecord AtmosphericEscape [regime M-dot efficiency])

(defn atmospheric-escape-system
  "ECS system: compute atmospheric mass loss each tick.
   Attached as a component on planet entities with :xuv-irradiation."
  [world entity]
  (let [planet   (get-entity world entity)
        xuv-data (:xuv-irradiation planet)
        result   (xuv-escape-regime xuv-data)]
    (-> world
        (update-component entity :atmosphere
                          (fn [atm]
                            (-> atm
                                (update :mass - (* (:M_dot result) dt))
                                (assoc :escape-regime (:regime result)))))
        (update-component entity :atmospheric-escape
                          merge result))))
```

### 4.4 Regime Map Helper

```clojure
(defn regime-label
  "Human-readable regime label for rendering."
  [regime]
  (case regime
    :energy-limited        "EL  (∝ F^0.9)"
    :recombination-limited "RL  (∝ F^0.6)"
    :photon-limited        "PL  (∝ F^1.0)"
    :blow-off              "BO  (Roche overflow)"))

(defn mass-loss-slope
  "Exponent α in Ṁ ∝ F^α for the given regime."
  [regime]
  (case regime
    :energy-limited        0.9
    :recombination-limited 0.6
    :photon-limited        1.0
    :blow-off              0.9))
```

---

## 5. Toy Model / Numerical Experiment

### 5.1 Setup

Test the regime selector against the three benchmark planets from
Lampón et al. (2021):

| Parameter | HD 209458 b | HD 189733 b | GJ 3470 b |
|-----------|-------------|-------------|-----------|
| M_p (M_J) | 0.714 | 1.13 | 0.043 |
| R_p (R_J) | 1.38 | 1.14 | 0.36 |
| a (AU) | 0.047 | 0.031 | 0.035 |
| M_star (M_☉) | 1.15 | 0.82 | 0.51 |
| F_XUV (erg/cm²/s) | ~10⁴·⁵ | ~10⁵ | ~10⁵·⁵ |
| Observed regime | Energy-limited | Recombination-limited | Photon-limited |

### 5.2 Expected Results

The regime selector should classify:
- **HD 209458 b**: energy-limited (high mass, moderate XUV, high binding energy)
- **HD 189733 b**: recombination-limited (high XUV, moderate mass)
- **GJ 3470 b**: photon-limited (low mass, very high XUV from M-dwarf)

### 5.3 Regime Diagram

```
F_XUV (erg/cm²/s)
    |
10⁶ |                              PHOTON-LIMITED
    |                         ╱
10⁵ |              RECOMB.-  ╱
    |              LIMITED  ╱
10⁴ |                  ╱  ╱
    |      ENERGY-   ╱  ╱
10³ |      LIMITED  ╱  ╱
    |             ╱  ╱
10² |            ╱ ╱
    |           ╱╱
10¹ |──────────╱─────────────────────
    |        ╱
    +──────────────────────────────── M_p (M_J)
        0.01  0.1    1     10

    Critical transition: F_crit ~ 10⁴ erg/cm²/s
    (shifts with planet mass and radius)
```

---

## 6. Validation

- [ ] Reproduces Murray-Clay et al. (2009) transition at F_UV ~ 10⁴ erg/cm²/s
- [ ] Correctly classifies HD 209458 b as energy-limited
- [ ] Correctly classifies HD 189733 b as recombination-limited
- [ ] Correctly classifies GJ 3470 b as photon-limited
- [ ] Ṁ matches energy-limited formula within ~10% for low-flux cases
- [ ] Ṁ scales as F^0.6 in recombination-limited regime
- [ ] Roche lobe correction K_tide → 1 for large orbital separations
- [ ] Heating efficiency η ~ 0.1-0.4 for typical hot Jupiters

---

## 7. Promotion Path to Domain

### 7.1 ECS Components

```clojure
(defrecord XUVIrradiation [F-xuv L-xuv a])
(defrecord AtmosphericEscape [regime M-dot efficiency F-xuv-crit K-tide])
(defrecord AtmosphericEnvelope [mass composition escape-history])
```

### 7.2 Malli Schema (law/)

```clojure
(def xuv-escape-regime
  [:enum :energy-limited :recombination-limited :photon-limited :blow-off])

(def mass-loss-rate
  [:map
   [:M-dot      [:and number? [:>= 0]]]    ; g/s
   [:regime     xuv-escape-regime]
   [:efficiency [:and number? [:>= 0] [:<= 1]]]])
```

### 7.3 System Function (domain/)

```clojure
(defn compute-atmospheric-escape
  "Pure function: given planet state and XUV flux, return escape rates."
  [planet xuv-flux]
  ;; implementation as in §4.2
  )
```

### 7.4 Test (test/)

```clojure
(deftest regime-classification-benchmark
  (testing "HD 209458 b is energy-limited"
    (is (= :energy-limited
           (:regime (xuv-escape-regime hd209458b-params)))))
  (testing "HD 189733 b is recombination-limited"
    (is (= :recombination-limited
           (:regime (xuv-escape-regime hd189733b-params)))))
  (testing "GJ 3470 b is photon-limited"
    (is (= :photon-limited
           (:regime (xuv-escape-regime gj3470b-params))))))
```

---

## 8. Open Questions

1. **Heating efficiency ε variation**: How does ε depend on metallicity?
   Higher metallicity → more coolants → lower ε. Frelikh & Murray-Clay
   (2025) show H₃⁺ cooling is significant at the molecular layer.

2. **Multi-frequency XUV**: Real stellar spectra span 13.6–2000 eV. The
   Wind-AE code handles this; our simplified model assumes a single
   effective energy. Need to decide on resolution for the simulation.

3. **Time-dependent effects**: During flares, planets oscillate between
   regimes. Caldiroli et al. (2025) show the fractional enhancement
   depends on orbital separation. Need to model time-variable XUV.

4. **Composition effects**: He/H ratio affects recombination rate and
   mean molecular weight. The He 10830Å line is the primary observational
   probe. Should we track He separately?

5. **Core-powered mass loss**: Misener et al. (2026, arXiv:2605.02766)
   show that for young sub-Neptunes, bolometric heating from the cooling
   interior can dominate over XUV photoevaporation. This is a separate
   mechanism from the XUV-driven regimes studied here.

6. **Magnetic field effects**: Shaikhislamov et al. (2015) show that
   intrinsic magnetic fields modify the outflow geometry and can suppress
   escape on the nightside. Not yet modeled.

---

## 9. References

1. Murray-Clay, R., Chiang, E., & Murray, N. (2009). "Atmospheric Escape
   from Hot Jupiters." ApJ, 693(1), 23. arXiv:0811.0006.
   DOI:10.1088/0004-637X/693/1/23

2. Owen, J.E. & Jackson, A.P. (2012). "Photoevaporation flows from
   exoplanets—I. Hydrodynamic models." MNRAS, 425(4), 2931-2949.
   DOI:10.1111/j.1365-2966.2012.21481.x

3. Owen, J.E. & Alvarez, M.A. (2016). "UV Driven Evaporation of Close-in
   Planets: Energy-limited, Recombination-limited, and Photon-limited
   Flows." ApJ, 816(1), 34. DOI:10.3847/0004-637X/816/1/34

4. Lampón, M. et al. (2021). "Evidence of energy-, recombination-, and
   photon-limited escape regimes in giant planet H/He atmospheres." A&A,
   648, L7. arXiv:2104.08832. DOI:10.1051/0004-6361/202140423

5. Mitani, H., Nakatani, R. & Kuiper, R. (2025). "Physically motivated
   analytic model of energy efficiency for EUV-driven atmospheric escape
   of close-in exoplanets." A&A, 695, A153. arXiv:2502.08398.
   DOI:10.1051/0004-6361/202452749

6. Broome, M. et al. (2025). "Wind-AE: A Fast, Open-source 1D
   Photoevaporation Code with Metal and Multi-frequency X-ray
   Capabilities." arXiv:2510.23857.

7. Caldiroli, A. et al. (2025). "Why M-dwarf flares have limited impact
   on the atmospheric evaporation of sub-Neptunes and Earth-sized
   planets." A&A, 702, A112. arXiv:2506.08014.

8. Lammer, H. et al. (2013). "Probing the Blow-Off Criteria of
   Hydrogen-Rich Super-Earths." MNRAS, 430(2), 1247-1256.
   arXiv:1210.0793.

9. Shaikhislamov, I.F. et al. (2015). "Atmosphere expansion and mass loss
   of close-orbit giant exoplanets heated by stellar XUV." ApJ, 795(2),
   132. arXiv:1506.03548.

10. Frelikh, R. & Murray-Clay, R. (2025). "Efficiency of Hydrodynamic
    Atmospheric Escape in Hot Jupiters and Super Earths."
    arXiv:2511.15787.

11. Misener, W. et al. (2026). "Characterizing the
    bolometric-photoevaporative transition in young sub-Neptunes with
    radiation-hydrodynamic simulations." arXiv:2605.02766.

12. Ballabio, G. & Owen, J.E. (2025). "Understanding what helium
    absorption tells us about atmospheric escape from exoplanets."
    arXiv:2501.06149.

---

## 10. Typical XUV Flux Values

### Solar-type stars (G2V, L_XUV ~ 10²⁷–10³⁰ erg/s)

| Distance (AU) | F_XUV (erg/cm²/s) | Regime (Hot Jupiter) | Regime (Super-Earth) |
|---------------|-------------------|---------------------|---------------------|
| 0.01 | ~10⁶–10⁷ | Recombination-limited | Photon-limited |
| 0.03 | ~10⁵–10⁶ | Recombination-limited | Recombination-limited |
| 0.05 | ~10⁴·⁵–10⁵ | Energy-limited | Recombination-limited |
| 0.1 | ~10³·⁵–10⁴ | Energy-limited | Energy-limited |
| 0.5 | ~10²–10³ | Energy-limited | Energy-limited |
| 1.0 | ~10¹·⁵–10²·⁵ | Energy-limited | Energy-limited |

### M-dwarf stars (M3V, L_XUV ~ 10²⁸–10³⁰ erg/s, but closer HZ)

| Distance (AU) | F_XUV (erg/cm²/s) | Regime (Sub-Neptune) | Regime (Earth-like) |
|---------------|-------------------|---------------------|---------------------|
| 0.01 | ~10⁶–10⁷ | Photon-limited | Photon-limited |
| 0.05 | ~10⁴·⁵–10⁵·⁵ | Recombination-limited | Energy-limited |
| 0.1 | ~10³·⁵–10⁴·⁵ | Energy-limited | Energy-limited |
| 0.2 | ~10³–10⁴ | Energy-limited | Energy-limited |

### Key transitions

- **Energy → Recombination**: F_XUV ~ 10⁴ erg/cm²/s (Murray-Clay 2009)
- **Recombination → Photon**: F_XUV ~ 10⁵·⁵ erg/cm²/s (Lampón 2021)
- **Blow-off**: R_XUV/R_Roche > 0.5–0.9 (Lammer 2013)

---

## Cross-references

- See `docs/research/physics/orbital-mechasics.md` for orbital distance
  calculations that feed into F_XUV.
- See `docs/research/cosmology/stellar-xuv-evolution.md` (to be written)
  for time-dependent L_XUV evolution of different stellar types.
- Couples to `domain.atmosphere` (atmospheric composition tracking)
  and `domain.ecs.core` (entity-component for planet state).
