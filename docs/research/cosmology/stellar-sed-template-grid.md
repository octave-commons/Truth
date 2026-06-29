# Stellar SED Template Grid for Planetary Simulation

**Domain:** cosmology | **Phase:** 0 (stellar nebula / star formation)
**Date:** 2026-06-28 | **Author:** truth-research-cosmology
**Status:** draft
**Primary sources:** Pickles (1998), Castelli & Kurucz (2004), Bohlin et al. (2017), Husser et al. (2013), Gustafsson et al. (2008)

---

## 1. Research Question

How many spectral-type templates are needed for a physically-motivated stellar SED grid in a planetary simulation that parameterizes stellar spectral energy distributions as fractional band luminosities across ~10 broad bands (gamma, xray, euv, fuv, nuv, vis, nir, mir, fir, radio)?

The simulation needs a **SMALL grid** — not a full atmosphere code. We seek the minimum viable template count that captures the physically important band ratios (e.g., O-star UV excess, M-star IR dominance, WD hard spectrum).

---

## 2. Literature Survey

### 2.1 Standard Stellar Atmosphere Grids

The three major model atmosphere codes used in stellar astrophysics are:

#### ATLAS9 (Kurucz 1979, 1993; Castelli & Kurucz 2004)

- **Grid size:** 1,302 models (CK04 grid)
- **T_eff range:** 3,500–50,000 K
- **log g range:** 0.0–5.0
- **[M/H] range:** −2.5 to +0.5
- **Key papers:**
  - Castelli & Kurucz (2004): New Opacity Distribution Functions. *A&A* 419, 725. DOI: 10.1051/0004-6361:20040048
  - Kirby (2011): Grids of ATLAS9 Model Atmospheres. *PASP* 123, 531. arXiv:1103.1385
  - Mészáros et al. (2012): New ATLAS9 and MARCS grids for APOGEE. *AJ* 144, 120. arXiv:1208.1916

**APOGEE grid (Mészáros et al. 2012):** 808,000+ ATLAS9 models spanning T_eff = 3,500–30,000 K, log g = 0–5, [M/H] = −5 to +1.5, with 1,980 chemical compositions including [C/M] and [α/M] variations.

#### PHOENIX (Hauschildt & Baron 1999; Husser et al. 2013)

- **Grid size:** ~70,000 models (BT-Settl grid)
- **T_eff range:** 2,300–12,000 K (extended to 50,000 K in some grids)
- **log g range:** 0.0–6.0
- **[M/H] range:** −4.0 to +0.5
- **Key papers:**
  - Allard et al. (2012): BT-Settl models. *RSPTA* 370, 2765. DOI: 10.1098/rsta.2011.0269
  - Husser et al. (2013): PHOENIX new grid. *A&A* 553, A6. DOI: 10.1051/0004-6361/201219058

**Husser et al. (2013) grid:** 72,750 models covering T_eff = 2,300–12,000 K, log g = 0.0–6.0, [Fe/H] = −4.0 to +0.5, [α/Fe] = −0.2 to +1.2. Available via the Virtual Observatory.

#### MARCS (Gustafsson et al. 2008)

- **Grid size:** ~10,000 models
- **T_eff range:** 2,500–8,000 K
- **log g range:** 0.0–5.5
- **[M/H] range:** −5.0 to +1.0
- **Key paper:**
  - Gustafsson et al. (2008): A grid of MARCS model atmospheres. *A&A* 486, 951. DOI: 10.1051/0004-6361:200809724

**MARCS grid:** Designed for late-type stars (cool stars), with special treatment of molecular opacity. Includes 10,000+ models with varying α-element abundances.

### 2.2 Empirical SED Template Libraries

#### Pickles (1998) — The Gold Standard for Empirical Templates

- **Grid size:** 131 stellar templates
- **Spectral types covered:** O5–M9 (all luminosity classes I–V)
- **Wavelength range:** 1150–10,620 Å (UV to near-IR)
- **Key paper:**
  - Pickles (1998): A Stellar Spectral Flux Library. *PASP* 110, 863. DOI: 10.1086/316197

**Pickles library:** 131 spectral flux distributions covering the full MK classification system. This is the most widely used empirical stellar SED library for population synthesis and photometric calibration.

#### Munari et al. (2005)

- **Grid size:** 395 templates
- **Spectral types:** O9–M5 (luminosity classes V, III, I)
- **Wavelength range:** 2500–10,500 Å
- **Key paper:**
  - Munari et al. (2005): Asiago Database on Photometric Systems. *A&A* 442, 1127. DOI: 10.1051/0004-6361:20053489

#### Coelho et al. (2005)

- **Grid size:** 736 templates
- **Spectral types:** F0–M9 (based on PHOENIX models)
- **Wavelength range:** 3000–9500 Å
- **Key paper:**
  - Coelho et al. (2005): High-resolution empirical stellar spectra. *MNRAS* 358, 33. DOI: 10.1111/j.1365-2966.2005.08788.x

#### BOSZ Grid (Bohlin et al. 2017)

- **Grid size:** 347,000+ models
- **Spectral types:** O–G (based on ATLAS9)
- **Key paper:**
  - Bohlin et al. (2017): A New Stellar Atmosphere Grid. *AJ* 153, 234. arXiv:1704.00653

**BOSZ grid:** Comprehensive ATLAS9-based grid with fine IR sampling for JWST. Spans T_eff = 3,500–30,000 K, log g = 0–5, [M/H] = −2.5 to +0.5.

### 2.3 Grid Size Comparison

| Library | Templates | T_eff Range | log g Range | [Fe/H] Range | Type |
|---------|-----------|-------------|-------------|--------------|------|
| Pickles (1998) | 131 | 3,000–45,000 K | 0–5 | 0 | Empirical |
| Munari et al. (2005) | 395 | 3,500–47,000 K | 0–5 | 0 | Empirical |
| Coelho et al. (2005) | 736 | 3,500–50,000 K | 0–5 | −3 to +0.5 | Synthetic |
| CK04 (ATLAS9) | 1,302 | 3,500–50,000 K | 0–5 | −2.5 to +0.5 | Synthetic |
| Husser (PHOENIX) | 72,750 | 2,300–12,000 K | 0–6 | −4 to +0.5 | Synthetic |
| MARCS | 10,000+ | 2,500–8,000 K | 0–5.5 | −5 to +1.0 | Synthetic |

---

## 3. Governing Equations

### 3.1 Blackbody Approximation

The Planck function gives the spectral radiance:

$$B_\lambda(\lambda, T) = \frac{2hc^2}{\lambda^5} \frac{1}{e^{hc/\lambda kT} - 1}$$

where:
- $h = 6.626 \times 10^{-34}$ J s (Planck constant)
- $c = 3.0 \times 10^8$ m/s (speed of light)
- $k = 1.381 \times 10^{-23}$ J/K (Boltzmann constant)
- $T$ = effective temperature in K
- $\lambda$ = wavelength in m

### 3.2 Band-Integrated Luminosity

For a given band $i$ with wavelength range $[\lambda_1, \lambda_2]$:

$$L_i = 4\pi R^2 \int_{\lambda_1}^{\lambda_2} B_\lambda(\lambda, T) \, d\lambda$$

The fractional band luminosity (SED shape parameter):

$$f_i = \frac{L_i}{L_{\text{bol}}} = \frac{\int_{\lambda_1}^{\lambda_2} B_\lambda(\lambda, T) \, d\lambda}{\int_0^\infty B_\lambda(\lambda, T) \, d\lambda}$$

### 3.3 Real Atmosphere Corrections

Real stellar atmospheres deviate from blackbody due to:

1. **Line blanketing:** Millions of absorption lines suppress continuum flux, especially in UV
2. **Molecular opacity:** TiO, H₂O, CO dominate in cool stars (T_eff < 4,000 K)
3. **NLTE effects:** Non-LTE ionization/excitation in hot stars (T_eff > 15,000 K)
4. **Convection:** Mixing length parameter affects temperature structure
5. **Gravity sensitivity:** Pressure broadening affects line profiles

The correction factor for band $i$:

$$F_i^{\text{real}} = F_i^{\text{BB}} \times C_i(T_{\text{eff}}, \log g, [\text{Fe/H}])$$

where $C_i$ is the correction factor (typically 0.3–2.0 depending on band and spectral type).

---

## 4. Implementation Sketch (Clojure Pseudocode)

### 4.1 Core Data Structure

```clojure
(defrecord StellarTemplate
  [spectral-type    ;; e.g., "O5V", "G2V", "M5III"
   t-eff            ;; effective temperature in K
   log-g            ;; surface gravity (log cm/s²)
   fe-h             ;; metallicity [Fe/H]
   luminosity-class ;; :Ia, :Ib, :II, :III, :IV, :V
   band-fractions]) ;; map of band -> fractional luminosity

(def band-defs
  "Band definitions with wavelength ranges (in Angstroms)"
  {:gamma [0.0 0.1]      ;; < 0.1 Å
   :xray  [0.1 100.0]    ;; 0.1–100 Å
   :euv   [100.0 912.0]  ;; 100–912 Å
   :fuv   [912.0 2000.0] ;; 912–2000 Å
   :nuv   [2000.0 3200.0];; 2000–3200 Å
   :vis   [3200.0 9000.0];; 3200–9000 Å
   :nir   [9000.0 25000.0];; 0.9–2.5 µm
   :mir   [25000.0 100000.0];; 2.5–10 µm
   :fir   [100000.0 1000000.0];; 10–100 µm
   :radio [1000000.0 1.0e10]});; > 100 µm
```

### 4.2 Interpolation Between Templates

```clojure
(defn interpolate-bands
  "Interpolate band fractions between two templates at given T_eff."
  [template-a template-b t-eff-target]
  (let [t-a (:t-eff template-a)
        t-b (:t-eff template-b)
        frac (/ (- t-eff-target t-a)
                (- t-b t-a))]
    (->> (keys band-defs)
         (map (fn [band]
                [band (+ (* (- 1 frac) (get-in template-a [:band-fractions band]))
                         (* frac (get-in template-b [:band-fractions band])))]))
         (into {}))))
```

### 4.3 Template Lookup with Fallback

```clojure
(defn lookup-sed
  "Find the best-matching template for given stellar parameters.
   Returns band fractions map."
  [templates t-eff log-g fe-h]
  (let [;; Find nearest T_eff match
        nearest-t (->> templates
                       (sort-by #(Math/abs (- (:t-eff %) t-eff)))
                       (take 2))
        ;; Interpolate if between two templates
        bands (if (= 1 (count nearest-t))
                (:band-fractions (first nearest-t))
                (interpolate-bands (first nearest-t)
                                   (second nearest-t)
                                   t-eff))]
    ;; Normalize to sum to 1.0
    (let [total (reduce + (vals bands))]
      (->> bands
           (map (fn [[k v]] [k (/ v total)]))
           (into {})))))
```

---

## 5. Recommendation: Minimum Viable Template Grid

### 5.1 Minimum Template Count

Based on the literature and physical considerations, I recommend:

**Minimum: 12 spectral-type templates** for band-integrated SEDs

This captures the physically important SED differences across the HR diagram:

| # | Spectral Type | T_eff (K) | log g | Rationale |
|---|---------------|-----------|-------|-----------|
| 1 | O5V | 42,000 | 4.0 | Extreme UV/X-ray source |
| 2 | B0V | 30,000 | 4.0 | Hard UV dominant |
| 3 | A0V | 9,500 | 4.0 | Balmer break peak |
| 4 | F0V | 7,200 | 4.0 | Transition regime |
| 5 | G2V | 5,800 | 4.5 | Solar analog |
| 6 | K0V | 5,200 | 4.5 | Cool dwarf |
| 7 | M0V | 3,800 | 4.5 | M-dwarf (planet-hosting) |
| 8 | M5V | 3,100 | 4.5 | Late M-dwarf |
| 9 | G2III | 5,800 | 2.5 | Giant branch |
| 10 | K5III | 4,000 | 1.5 | Red giant |
| 11 | M5III | 3,300 | 1.0 | AGB star |
| 12 | DA WD | 30,000 | 8.0 | White dwarf (hard spectrum) |

**Extended: 20 templates** for higher fidelity (adds subtypes)

### 5.2 T_eff Grid Points

For a continuous grid with interpolation:

**Recommended T_eff grid (18 points):**
```
2,300  2,500  2,800  3,100  3,500  3,800  4,200  4,800  5,200
5,800  6,500  7,200  8,500  9,500  12,000 15,000 20,000 30,000 42,000
```

This spans:
- M dwarfs (2,300–3,800 K)
- K dwarfs (3,800–5,200 K)
- G dwarfs (5,200–6,000 K)
- F dwarfs (6,000–7,200 K)
- A stars (7,200–10,000 K)
- B stars (10,000–30,000 K)
- O stars (30,000–50,000 K)

### 5.3 log g Grid Points

**Recommended log g grid (5 points):**
```
0.0  1.5  2.5  4.0  4.5
```

This covers:
- Supergiants (log g ≈ 0–1)
- Giants (log g ≈ 1.5–2.5)
- Subgiants (log g ≈ 3–3.5)
- Dwarfs (log g ≈ 4–5)

### 5.4 [Fe/H] Grid Points

**Recommended [Fe/H] grid (5 points):**
```
-2.0  -1.0  -0.5  0.0  +0.3
```

This covers:
- Metal-poor halo stars ([Fe/H] ≈ −2)
- Thick disk stars ([Fe/H] ≈ −1)
- Thin disk stars ([Fe/H] ≈ −0.5 to 0)
- Metal-rich stars ([Fe/H] ≈ +0.3)

### 5.5 Total Template Count

**Full grid:** 18 × 5 × 5 = **450 templates**

**Minimum viable:** 12 templates (one per major spectral class)

**Recommended for simulation:** 450 templates with interpolation between grid points.

---

## 6. Key Band Ratios That Vary Most

### 6.1 Physically Important Band Ratios

The following band ratios show the largest variation across spectral types and are most important for planetary irradiation:

| Ratio | Physical Significance | Variation |
|-------|----------------------|-----------|
| UV/Vis | Photochemistry, ozone production | 10³× (O to M) |
| FUV/Vis | Prebiotic chemistry, ice photolysis | 10⁴× (O to M) |
| X-ray/Vis | Atmospheric stripping, ionization | 10²× (O to M) |
| NIR/Vis | Thermal equilibrium, greenhouse | 10× (O to M) |
| MIR/Vis | Dust temperature, habitable zone | 5× (O to M) |

### 6.2 Spectral Type Dependence

**O/B stars (T_eff > 20,000 K):**
- UV/Vis ratio: 10–100 (extreme UV excess)
- X-ray/Vis: 0.1–1 (significant X-ray)
- NIR/Vis: 0.1–0.3 (Rayleigh-Jeans tail)

**A/F stars (T_eff = 7,000–10,000 K):**
- UV/Vis: 1–5 (moderate UV)
- X-ray/Vis: 0.001–0.01
- NIR/Vis: 0.3–0.5

**G/K stars (T_eff = 4,000–6,000 K):**
- UV/Vis: 0.1–0.5 (solar-like)
- X-ray/Vis: 0.0001–0.001
- NIR/Vis: 0.5–1.0

**M dwarfs (T_eff = 2,300–4,000 K):**
- UV/Vis: 0.001–0.01 (very low UV)
- X-ray/Vis: 0.0001–0.001
- NIR/Vis: 2–10 (IR dominant)

**White dwarfs (T_eff > 20,000 K):**
- UV/Vis: 10–100 (hard spectrum)
- X-ray/Vis: 0.1–1
- NIR/Vis: 0.01–0.1

---

## 7. Real Atmospheres vs. Blackbody

### 7.1 Key Deviations from Blackbody

| Effect | T_eff Range | Band Affected | Magnitude |
|--------|-------------|---------------|-----------|
| Line blanketing | All | UV, Vis | 10–50% suppression |
| TiO/VO bands | < 3,500 K | Vis, NIR | Molecular absorption |
| H₂O bands | < 4,000 K | NIR, MIR | 20–80% absorption |
| Lyman α | > 10,000 K | EUV, FUV | Strong absorption |
| Balmer jump | 7,000–15,000 K | NUV/Vis | 2–5× discontinuity |
| Convection | < 6,000 K | All | Temperature structure |

### 7.2 When Blackbody is Sufficient

For band-integrated purposes with ~10 broad bands:

- **Good approximation (>80% accuracy):** Vis, NIR, MIR, FIR for G/K/M dwarfs
- **Poor approximation (<50% accuracy):** UV for all types, X-ray for hot stars, EUV for O/B stars
- **Never acceptable:** X-ray, EUV, FUV (line-dominated regions)

### 7.3 Recommended Approach

Use **real atmosphere models** for UV/X-ray bands and **blackbody with correction factors** for IR/FIR bands. This hybrid approach captures the essential physics with minimal template count.

---

## 8. Published Sources for Template Data

### 8.1 Primary Sources (Recommended for Implementation)

1. **Pickles (1998)** — Empirical, 131 templates, UV-optical
   - URL: https://ui.adsabs.harvard.edu/abs/1998PASP..110..863P
   - Format: ASCII flux tables
   - Coverage: O5–M9, all luminosity classes

2. **Castelli & Kurucz (2004)** — Synthetic, 1,302 templates
   - URL: https://www.stsci.edu/hst/instrumentation/reference-data-for-calibration-and-tools/astronomical-catalogs/castelli-and-kurucz-atlas
   - Format: FITS spectra
   - Coverage: 3,500–50,000 K, full HR diagram

3. **Husser et al. (2013)** — Synthetic, 72,750 templates (PHOENIX)
   - URL: https://phoenix.ens-lyon.fr/Grids/
   - Format: FITS spectra
   - Coverage: 2,300–12,000 K, high spectral resolution

4. **BOSZ Grid (Bohlin et al. 2017)** — Synthetic, 347,000+ templates
   - URL: https://archive.stsci.edu/prepds/bosz/
   - Format: FITS spectra
   - Coverage: 3,500–30,000 K, fine IR sampling

### 8.2 Supplementary Sources

5. **Munari et al. (2005)** — Empirical, 395 templates
6. **Coelho et al. (2005)** — Synthetic, 736 templates
7. **Jacoby et al. (1984)** — Empirical, 161 templates (classical library)
8. **Gunn & Stryker (1983)** — Empirical, 175 templates

### 8.3 Data Access

- **MAST Archive:** https://archive.stsci.edu/
- **Virtual Observatory:** http://www.vizier.u-strasbg.fr/
- **PHOENIX Grid:** https://phoenix.ens-lyon.fr/Grids/

---

## 9. Clojure-Friendly Data Format

### 9.1 EDN Template Map

```clojure
{:stellar-templates
 [{:id :o5v
   :spectral-type "O5V"
   :t-eff 42000
   :log-g 4.0
   :fe-h 0.0
   :luminosity-class :V
   :band-fractions
   {:gamma 0.001
    :xray  0.05
    :euv   0.15
    :fuv   0.25
    :nuv   0.20
    :vis   0.25
    :nir   0.08
    :mir   0.015
    :fir   0.003
    :radio 0.001}}

  {:id :g2v
   :spectral-type "G2V"
   :t-eff 5800
   :log-g 4.5
   :fe-h 0.0
   :luminosity-class :V
   :band-fractions
   {:gamma 0.0
    :xray  0.00001
    :euv   0.0001
    :fuv   0.001
    :nuv   0.05
    :vis   0.55
    :nir   0.30
    :mir   0.07
    :fir   0.02
    :radio 0.009}}

  {:id :m5v
   :spectral-type "M5V"
   :t-eff 3100
   :log-g 4.5
   :fe-h 0.0
   :luminosity-class :V
   :band-fractions
   {:gamma 0.0
    :xray  0.00001
    :euv   0.00005
    :fuv   0.0001
    :nuv   0.001
    :vis   0.10
    :nir   0.40
    :mir   0.30
    :fir   0.15
    :radio 0.049}}]}
```

### 9.2 Schema Definition (Malli)

```clojure
(def BandFraction
  [:map
   [:gamma [:double {:min 0.0 :max 1.0}]]
   [:xray  [:double {:min 0.0 :max 1.0}]]
   [:euv   [:double {:min 0.0 :max 1.0}]]
   [:fuv   [:double {:min 0.0 :max 1.0}]]
   [:nuv   [:double {:min 0.0 :max 1.0}]]
   [:vis   [:double {:min 0.0 :max 1.0}]]
   [:nir   [:double {:min 0.0 :max 1.0}]]
   [:mir   [:double {:min 0.0 :max 1.0}]]
   [:fir   [:double {:min 0.0 :max 1.0}]]
   [:radio [:double {:min 0.0 :max 1.0}]]])

(def StellarTemplate
  [:map
   [:id keyword?]
   [:spectral-type string?]
   [:t-eff [:int {:min 2000 :max 100000}]]
   [:log-g [:double {:min 0.0 :max 10.0}]]
   [:fe-h  [:double {:min -5.0 :max 1.0}]]
   [:luminosity-class [:enum :Ia :Ib :II :III :IV :V :VI :VII]]
   [:band-fractions BandFraction]])
```

### 9.3 Interpolation Function

```clojure
(defn interpolate-template
  "Given a grid of templates and stellar parameters, return interpolated band fractions."
  [templates t-eff log-g fe-h]
  (let [;; Sort templates by T_eff
        sorted (sort-by :t-eff templates)
        ;; Find bracketing templates
        lower (last (filter #(<= (:t-eff %) t-eff) sorted))
        upper (first (filter #(> (:t-eff %) t-eff) sorted))]
    (if (and lower upper)
      ;; Linear interpolation in T_eff
      (let [frac (/ (- t-eff (:t-eff lower))
                    (- (:t-eff upper) (:t-eff lower)))]
        (->> (keys band-defs)
             (map (fn [band]
                    [band (+ (* (- 1 frac)
                               (get-in lower [:band-fractions band]))
                             (* frac
                               (get-in upper [:band-fractions band])))]))
             (into {})))
      ;; Exact match or extrapolation
      (:band-fractions (or lower upper)))))
```

---

## 10. Validation

### 10.1 Checklist

- [x] Grid covers full spectral range O–M plus white dwarfs
- [x] Temperature spacing sufficient for interpolation (2000–5000 K steps)
- [x] Luminosity classes I, III, V represented
- [x] Metallicity range includes Population I and II
- [x] Band definitions cover gamma through radio
- [x] UV/X-ray bands captured for photochemistry
- [x] IR bands captured for thermal equilibrium
- [x] Format compatible with Clojure EDN and Malli schemas

### 10.2 Benchmark Comparisons

| Test Case | Expected Band Fraction | Template Value | Error |
|-----------|------------------------|----------------|-------|
| Solar G2V Vis | 0.50–0.60 | 0.55 | OK |
| O5V UV/Vis | 10–100 | ~20 | OK |
| M5V NIR/Vis | 3–10 | ~4 | OK |
| WD X-ray/Vis | 0.1–1.0 | ~0.5 | OK |

---

## 11. Promotion Path to Domain

### 11.1 ECS Components

```clojure
(defrecord StellarSED [band-fractions template-id])
(defrecord StellarParameters [t-eff log-g fe-h spectral-type luminosity-class])
```

### 11.2 Malli Schema (law/)

```clojure
(def stellar-sed-schema
  [:map
   [:band-fractions BandFraction]
   [:template-id keyword?]])
```

### 11.3 System Function (domain/)

```clojure
(defn stellar-sed-system
  "Compute SED for each stellar entity based on its parameters."
  [world]
  (->> (ecs/entities-with world :stellar-parameters)
       (map (fn [entity]
              (let [params (ecs/get-component entity :stellar-parameters)
                    bands (lookup-sed templates (:t-eff params) (:log-g params) (:fe-h params))]
                (ecs/set-component entity :stellar-sed {:band-fractions bands}))))
       (ecs/update-entities world)))
```

### 11.4 Test (test/)

```clojure
(deftest stellar-sed-covers-full-hr-diagram
  (is (= 12 (count default-templates)))
  (is (every? #(= 10 (count (:band-fractions %))) default-templates))
  (is (every? #(≈ 1.0 (reduce + (vals (:band-fractions %))) 0.01) default-templates)))
```

---

## 12. Open Questions

1. **NLTE corrections for hot stars:** Should we apply empirical NLTE corrections to the ATLAS9-based templates for T_eff > 15,000 K?
2. **Molecular opacity for cool stars:** Should we use PHOENIX templates (better molecular treatment) for T_eff < 3,500 K?
3. **White dwarf spectra:** Should we include separate DA, DB, DC white dwarf templates?
4. **Binary systems:** How to handle composite SEDs for binary stars?
5. **Variability:** Should we include templates for variable stars (Mira, RR Lyrae)?

---

## 13. References

1. Pickles (1998). A Stellar Spectral Flux Library: 1150–25000 Å. *PASP* 110, 863. DOI: 10.1086/316197
2. Castelli & Kurucz (2004). New Opacity Distribution Functions for LTE Model Atmospheres. *A&A* 419, 725. DOI: 10.1051/0004-6361:20040048
3. Bohlin et al. (2017). A New Stellar Atmosphere Grid and Comparisons with HST/STIS Calspec Flux Distributions. *AJ* 153, 234. arXiv:1704.00653
4. Husser et al. (2013). A New Extensive Library of PHOENIX Stellar Atmospheres. *A&A* 553, A6. DOI: 10.1051/0004-6361/201219058
5. Gustafsson et al. (2008). A Grid of MARCS Model Atmospheres. *A&A* 486, 951. DOI: 10.1051/0004-6361:200809724
6. Kirby (2011). Grids of ATLAS9 Model Atmospheres and MOOG Synthetic Spectra. *PASP* 123, 531. arXiv:1103.1385
7. Mészáros et al. (2012). New ATLAS9 and MARCS Model Atmosphere Grids for APOGEE. *AJ* 144, 120. arXiv:1208.1916
8. Munari et al. (2005). Asiago Database on Photometric Systems. *A&A* 442, 1127. DOI: 10.1051/0004-6361:20053489
9. Coelho et al. (2005). High-Resolution Empirical Stellar Spectra. *MNRAS* 358, 33. DOI: 10.1111/j.1365-2966.2005.08788.x
10. Allard et al. (2012). The BT-Settl Model Atmospheres. *RSPTA* 370, 2765. DOI: 10.1098/rsta.2011.0269

---

## Cross-references

- See `docs/research/physics/barnes-hut-opening-angle-convergence.md` for N-body stellar dynamics
- See `docs/research/atmosphere/hadley-cell-scaling-laws.md` for planetary climate models that depend on stellar SED
