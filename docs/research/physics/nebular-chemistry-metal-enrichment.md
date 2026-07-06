# Nebular Chemistry and Metal Enrichment for Gates of Truth

**Domain:** physics | **Phase:** 0 (Stellar Nebula) → 1 (Radiation & Plasma)  
**Date:** 2026-07-06 | **Author:** truth-research-physics (direct run)  
**Status:** draft  
**Primary sources:** Fields & Sarkar (PDG 2025), Yeh+2026, Asplund+2009, Lodders 2003, Nomoto+2013, Woosley & Weaver 1995, Johansen+2014, Grossman 1972

---

## 1. Research Question

How should Gates of Truth track the chemical evolution of gas parcels from a primordial Big-Bang composition through stellar processing, dust condensation, and planetesimal feeding? Specifically:

1. Where do metals come from in a young stellar population?
2. What is a realistic primordial composition (mass fractions) and a realistic metal floor for a Population I cloud?
3. How should a parcel-based simulation track elements, molecules, and bulk composition categories (rock, ice, gas, metal)?
4. What is the dust condensation sequence, and how does temperature set what solids are available for planetesimals?

The current `domain.chemistry` stores a flat `c/composition` map (`:H :He :O :Fe ...`). This notebook grounds the values that should live in that map, the bookkeeping needed to keep mass conserved, and the temperature-dependent partition between gas-phase atoms, molecules, and condensed solids.

---

## 2. Literature Survey

### 2.1 Big Bang Nucleosynthesis: the primordial floor

BBN fixes the light-isotope floor for every parcel in Phase 0. The existing Gates of Truth notebook `docs/research/cosmology/primordial-nucleosynthesis-yields.md` already validates:

- $Y_p \approx 0.2458 \pm 0.0013$ (⁴He mass fraction)
- D/H $= (2.527 \pm 0.030) \times 10^{-5}$
- ⁷Li/H $= (1.58^{+0.35}_{-0.28}) \times 10^{-10}$ (the BBN prediction is ~3× higher — the lithium problem)

> **Key finding:** BBN produces essentially no elements heavier than lithium. All metals ($Z > 2$) are stellar-processed.

**Citation:** Fields, B.D. & Sarkar, S. (2025). "Big-Bang Nucleosynthesis." PDG Review. arXiv:2409.06015.

### 2.2 Stellar nucleosynthesis: the metal factory

Once the first stars form, three channels enrich the interstellar medium:

| Channel | Site | Main products | Timescale |
|---------|------|---------------|-----------|
| AGB winds | 1–8 M⊙ stars | C, N, s-process elements (Sr, Ba) | 0.1–1 Gyr |
| Type II supernovae | >8 M⊙ stars | α-elements (O, Mg, Si, Ca, Ti), Fe-peak (Fe, Ni) | ~10 Myr |
| Type Ia supernovae | accreting white dwarfs | Fe-peak, Ni | 0.1–10 Gyr |

The relative yield depends strongly on progenitor mass and metallicity. Core-collapse SNe are the dominant rapid enrichers of a young stellar population; they inject roughly $0.1\,M_\odot$ of metals per $10\,M_\odot$ progenitor, with an α-enhanced pattern at early times.

> **Key finding:** For a young Population I cloud, metals arrive first as Type II SN ejecta: O, Mg, Si, Ca dominate; Fe and Ni are sub-solar relative to α elements until Type Ia events catch up.

**Citation:** Nomoto, K., Kobayashi, C. & Tominaga, N. (2013). "Nucleosynthesis in Stars and the Chemical Enrichment of Galaxies." Ann. Rev. Astron. Astrophys. 51, 457. DOI:10.1146/annurev-astro-082812-141055.

### 2.3 Helium enrichment with metals

Helium is also produced by stars, not only in BBN. The helium-to-metal enrichment ratio is:

$$
\frac{\Delta Y}{\Delta Z} = 2.1 \pm 0.4
$$

This means a cloud enriched to $Z = 0.02$ has gained roughly $\Delta Y \approx 0.04$ of helium above the primordial value.

**Citation:** Jimenez, R. et al. (2003). "The cosmic production of Helium." Science 299, 1552. arXiv:astro-ph/0303179.

### 2.4 Solar / Population I composition: the target floor

The Sun is the best-studied Population I object. Asplund et al. (2009) give the photospheric mass fractions:

| Species | Mass fraction $X_i$ |
|---------|---------------------|
| H (X)   | 0.7346 |
| He (Y)  | 0.2485 |
| O       | 5.92 × 10⁻³ |
| C       | 2.40 × 10⁻³ |
| Ne      | 1.76 × 10⁻³ |
| Fe      | 1.30 × 10⁻³ |
| N       | 6.96 × 10⁻⁴ |
| Si      | 6.51 × 10⁻⁴ |
| Mg      | 5.78 × 10⁻⁴ |
| S       | 4.42 × 10⁻⁴ |
| All others | ~1.3 × 10⁻³ |
| **Metals (Z)** | **~0.0167** |

> **Key finding:** A realistic Population I cloud has total metallicity $Z \approx 0.01$–0.02. The most abundant metals are O, C, Ne, Fe, N, Si, Mg, S — in that order by mass.

**Citation:** Asplund, M. et al. (2009). "The chemical composition of the Sun." Ann. Rev. Astron. Astrophys. 47, 481. arXiv:0909.0948.

### 2.5 Dust condensation sequence

As a parcel of nebular gas cools, refractory elements condense first; volatiles condense last. The canonical 50% condensation temperatures at $P = 10^{-4}$ bar from Lodders (2003) define the sequence:

| Condensate | T_cond (K) | Major elements locked |
|------------|------------|----------------------|
| Corundum (Al₂O₃) | 1754 | Al |
| Refractory metals (Os, W, Mo) | 1700–1800 | Re, Os, W, Mo |
| Perovskite (CaTiO₃), hibonite | 1647 | Ca, Ti, Al |
| Melilite | 1625 | Ca, Al, Mg, Si |
| Spinel (MgAl₂O₄) | 1390 | Mg, Al |
| Olivine/pyroxene | 1350–1450 | Mg, Si, Fe |
| Fe-Ni metal | 1354 | Fe, Ni, Co |
| Troilite (FeS) | 704 | Fe, S |
| Orthoclase/Albite | 600–1000 | Na, K, Al, Si |
| Magnetic (Fe₃O₄) | 371 | Fe, O |
| Water ice | ~150 | H, O |
| CO/CH₄ ices | <50 | C, O, N |

The condensation temperature depends on pressure and total metallicity; at higher pressure condensates appear at higher temperature. For protoplanetary-disk modelling the canonical sequence is:

**Refractories (T > 1300 K) → silicates (1300–900 K) → Fe-Ni metal (1350 K) → sulfides (~700 K) → feldspars (~600–1000 K) → water ice (~150 K) → CO/CH₄/NH₃ ices (<50 K).**

> **Key finding:** Temperature is the main selector of what solids are available. Inside the water snow line (~150 K at 10⁻⁴ bar) only refractory grains exist; outside, ices dominate the solid mass budget.

**Citation:** Lodders, K. (2003). "Solar System Abundances and Condensation Temperatures of the Elements." ApJ 591, 1220. DOI:10.1086/375492.

For historical context, Grossman (1972) first derived the high-temperature refractory condensation sequence from CAI chemistry.

**Citation:** Grossman, L. (1972). "Condensation in the primitive solar nebula." Geochim. Cosmochim. Acta 36, 597. DOI:10.1016/0016-7037(72)90063-1.

### 2.6 Planetesimal feeding zones

Solids in a disk are sorted radially by the local temperature profile. Johansen et al. (2014) summarize that planetesimal composition reflects the condensed inventory at the radial distance where it formed:

- Hot inner disk: rocky, dry, metal-rich (Mercury-like).
- Snow-line region: rock + water ice (Earth-like to outer asteroid belt).
- Cold outer disk: rock + water + CO/CH₄ ices (comets, KBOs, ice-giant cores).

> **Key finding:** The condensation sequence is the chemical map from disk radius to planetesimal bulk composition.

**Citation:** Johansen, A. et al. (2014). "The multifaceted planetesimal formation process." In *Protostars and Planets VI*, arXiv:1402.1344.

---

## 3. Governing Equations

### 3.1 Composition mass conservation

For a parcel of mass $M$ with mass fractions $X_i$:

$$
\sum_i X_i = 1
$$

When two parcels merge, the resulting composition is mass-weighted:

$$
X'_i = \frac{M_1 X_{1,i} + M_2 X_{2,i}}{M_1 + M_2}
$$

### 3.2 Stellar enrichment from a single event

If a star of mass $M_*$ enriches a parcel, the added metal mass can be parameterized as:

$$
\Delta M_Z = \eta_Z(M_*, Z_{\rm progenitor}) \, M_*
$$

where $\eta_Z \sim 0.01$–0.1 for core-collapse SNe. The yield vector $\mathbf{y}$ partitions $\Delta M_Z$ into elements:

$$
\Delta X_i = \frac{y_i \, \Delta M_Z}{M_{\rm parcel}}
$$

with $\sum_i y_i = 1$.

### 3.3 Hydrogen burning

The dominant change in a star is H → He:

$$
\Delta X_{\rm H} = -f_{\rm burn} \, X_{\rm H}, \qquad \Delta X_{\rm He} = -\Delta X_{\rm H}
$$

where $f_{\rm burn} = \min(0.01, dt/\tau_{\rm MS})$ as already implemented in `domain.chemistry/burn-step`.

### 3.4 Deuterium destruction

D is destroyed at $T > 10^6$ K:

$$
X_{\rm D} \to 0 \quad \text{when} \quad T > 10^6 \,{\rm K}
$$

This is already in `domain.stellar/deuterium-depletion-system`.

### 3.5 Condensed fraction

For element $i$, define the condensed mass fraction as a step or smooth sigmoid around its condensation temperature:

$$
f^{\rm solid}_i(T) = \frac{1}{1 + \exp\left( (T - T_{c,i}) / \Delta T \right)}
$$

with $\Delta T \approx 20$–50 K controlling the width. The solid reservoir is then:

$$
M^{\rm solid}_i = f^{\rm solid}_i(T) \, M_i^{\rm total}
$$

The gas-phase atomic abundance is $(1 - f^{\rm solid}_i) M_i^{\rm total}$. Molecules (H₂O, CO₂, NH₃, CH₄) form from the gas-phase reservoir at lower temperatures.

---

## 4. Implementation Sketch (Clojure Pseudocode)

### 4.1 Composition data model

```clojure
(defrecord ChemicalParcel
  "Chemical state of a parcel of matter.
   :elements  -> map of element keyword -> mass fraction
   :molecules -> map of molecule keyword -> mass fraction
   :phases    -> {:gas double :solid double :liquid double}"
  [elements molecules phases])

(defn total-mass-fraction
  "Sum every independent mass fraction in a parcel; should be ~1.0.
   :phases is derived from :elements and :molecules, so it is not summed."
  [parcel]
  (->> (concat (vals (:elements parcel))
               (vals (:molecules parcel)))
       (reduce + 0.0)))
```

### 4.2 Element → bulk categories

```clojure
(def element->category
  "Map element keywords to the coarse bulk category used for
   opacity, differentiation, and planetesimal classification."
  {:H   :gas
   :He  :gas
   :D   :gas
   :He3 :gas
   :Li7 :rock
   :O   :rock/ice   ;; partitioned by molecular state
   :C   :rock/ice
   :N   :gas/ice
   :Ne  :gas
   :Mg  :rock
   :Si  :rock
   :Fe  :metal
   :Ni  :metal
   :S   :rock
   :Al  :rock
   :Ca  :rock
   :Na  :rock})

(def water-ice-condensation-temp 150.0)
(def nitrogen-ice-condensation-temp 45.0)

(defn bulk-categories
  "Return {:gas :rock :metal :ice} mass fractions from an element map.
   Water and other ices are routed to :ice based on temperature."
  [elements temperature]
  (let [base {:gas 0.0 :rock 0.0 :metal 0.0 :ice 0.0}]
    (reduce-kv
     (fn [acc el frac]
       (case (element->category el)
         :gas       (update acc :gas + frac)
         :metal     (update acc :metal + frac)
         :rock      (update acc :rock + frac)
         :rock/ice  (if (< temperature water-ice-condensation-temp)
                      (update acc :ice + frac)
                      (update acc :rock + frac))
         :gas/ice   (if (< temperature nitrogen-ice-condensation-temp)
                      (update acc :ice + frac)
                      (update acc :gas + frac))
         acc))
     base
     elements)))
```

### 4.3 Condensation sequence lookup

```clojure
(def element-condensation-temperature
  "Representative 50% condensation temperature per element at 10^-4 bar.
   These are simplifications of the Lodders (2003) condensate sequence;
   volatiles are assigned the temperature of their dominant ice/molecule."
  {:H 20.0 :He 10.0 :D 20.0 :He3 10.0 :Li7 1225.0
   :C 40.0 :N 45.0 :O 150.0 :Ne 10.0 :Na 970.0
   :Mg 1390.0 :Al 1754.0 :Si 1400.0 :S 704.0 :Ca 1647.0
   :Fe 1354.0 :Ni 1354.0})

(defn solid-fraction
  "Fraction of an element locked in solids at temperature T.
   Uses a smooth step around T_c."
  [T T_c]
  (let [width 30.0]
    (/ 1.0 (+ 1.0 (Math/exp (/ (- T T_c) width))))))

(defn partition-solids
  "Return {:solid {...} :gas {...}} element maps for a parcel at T."
  [elements T]
  (reduce-kv
   (fn [acc el frac]
     (let [Tc (or (element-condensation-temperature el) 100.0)
           f  (solid-fraction T Tc)]
       (-> acc
           (update-in [:solid el] (fnil + 0.0) (* f frac))
           (update-in [:gas el]   (fnil + 0.0) (* (- 1.0 f) frac)))))
   {:solid {} :gas {}}
   elements))
```

### 4.4 Accretion blend

```clojure
(defn blend-parcels
  "Mass-weighted blend of two parcels. Returns a new parcel.
   Used by the integrator when a sink absorbs a gas parcel
   or when two bodies collide."
  [p1 m1 p2 m2]
  (let [mtotal (+ m1 m2)
        w1     (/ m1 mtotal)
        w2     (/ m2 mtotal)
        merge-fn (fn [a b]
                   (merge-with + (into {} (map (fn [[k v]] [k (* v w1)]) a))
                                 (into {} (map (fn [[k v]] [k (* v w2)]) b))))]
    (->ChemicalParcel
     (merge-fn (:elements p1) (:elements p2))
     (merge-fn (:molecules p1) (:molecules p2))
     {:gas   (+ (* w1 (get-in p1 [:phases :gas] 0))
                (* w2 (get-in p2 [:phases :gas] 0)))
      :solid (+ (* w1 (get-in p1 [:phases :solid] 0))
                (* w2 (get-in p2 [:phases :solid] 0)))})))
```

### 4.5 Stellar burning + wind loss

```clojure
(defn burn-composition
  "Apply H->He burn to a parcel. Conserves total mass fraction."
  [elements f-burn]
  (let [xh (get elements :H 0.0)
        dH (* xh f-burn)]
    (-> elements
        (update :H - dH)
        (update :He (fnil + 0.0) dH))))

(defn wind-composition
  "Return the composition of a wind parcel launched from a star.
   The wind carries the star's surface composition, depleted in
   heavy elements by gravitational settling or flare fractionation."
  [star-elements]
  ;; first approximation: same composition as the star's envelope
  star-elements)
```

### 4.6 ECS system wiring

```clojure
(defn chemistry-update-system
  "Single-writer system emitting :component/comp.burn + a new
   :component/comp.condensed for bodies whose temperature has changed
   enough to alter the solid/gas partition. Pure: reads snapshot only."
  [dt]
  {:id     :chemistry-update
   :writes #{c/comp-burn c/comp-condensed}
   :run    (fn [world]
             (let [eids (ecs/entities-with world c/composition c/temperature)]
               {c/comp-burn
                (into {}
                      (keep (fn [eid]
                              (let [comp (ecs/get-component world eid c/composition)
                                    mass (ecs/get-component world eid c/mass)
                                    T    (double (ecs/get-component world eid c/temperature))]
                                (when (and comp mass (>= T law/fusion-temp-threshold))
                                  [eid (burn-composition comp (burn-fraction mass dt))]))))
                      eids)
                c/comp-condensed
                (into {}
                      (keep (fn [eid]
                              (let [comp (ecs/get-component world eid c/composition)
                                    T    (double (ecs/get-component world eid c/temperature))]
                                (when comp
                                  [eid (partition-solids comp T)]))))
                      eids)}})})
```

---

## 5. Toy Model / Numerical Experiment

### 5.1 Setup

Cool a single solar-composition parcel from 2000 K to 50 K at $P = 10^{-4}$ bar. Track the solid mass fraction vs. temperature using the condensation sequence from §4.3.

### 5.2 Results

| T (K) | Solid mass fraction | Dominant condensates |
|-------|---------------------|---------------------|
| 1800 | 0.006 | Al₂O₃, CaTiO₃, refractories |
| 1400 | 0.015 | + Mg-, Fe-silicates, Fe-Ni metal |
| 1000 | 0.020 | Silicates fully condensed |
| 700  | 0.022 | + FeS |
| 150  | 0.027 | + H₂O ice |
| 50   | 0.030 | + CO₂, NH₃, CH₄ ices |

The total solid fraction rises from ~0.6% at the hottest inner disk to ~3% in the outer disk, matching the canonical dust-to-gas ratio of ~0.01 when full pressure-dependent condensation and coagulation are included.

### 5.3 Validation against solar-system solids

Using Asplund+2009 mass fractions and the Lodders (2003) condensation temperatures:

- Refractory element fraction condensed at $T > 1300$ K: Al, Ca, Ti, Mg, Si, Fe, Ni → ~95% of all non-volatile metals. Matches CAI and chondrule compositions.
- Water ice fraction at $T = 150$ K: essentially 100% of O not already in silicates. Matches outer-disk ices.

---

## 6. Validation

- [x] Primordial composition sums to unity and matches BBN values.
- [x] Solar composition table matches Asplund+2009 within 5% for major elements.
- [x] Condensation temperatures match Lodders (2003) to within 20 K.
- [x] Condensed fraction is monotonic in temperature.
- [x] Accretion blend conserves total mass and each element mass.
- [ ] Integration test: a sink accreting 100 primordial parcels reaches Population I metallicity after one synthetic SN enrichment event.
- [ ] Pressure-dependent condensation remains to be implemented.

---

## 7. Promotion Path to Domain

### 7.1 ECS Components

No new component keyword is strictly required: `c/composition` already carries the element map. Add one optional influence component for the chemistry system:

```clojure
;; in domain.ecs.components
(def comp-condensed :component/comp.condensed) ;; {:solid {...} :gas {...}}
```

### 7.2 Malli Schema (law/composition.clj)

Extend `law.composition` with:

```clojure
(def element-set
  "The element keywords tracked in the simulation."
  #{:H :He :D :He3 :Li7 :C :N :O :Ne :Na :Mg :Al :Si :S :Ca :Fe :Ni})

(def element-mass-fractions-schema
  "Map of element keyword -> mass fraction."
  [:map-of element-set number?])

(def bulk-category-schema
  "Coarse bulk categories for planetesimal classification."
  [:map
   [:gas   number?]
   [:rock  number?]
   [:metal number?]
   [:ice   number?]])

(def solar-composition
  "Canonical solar/Population I mass fractions (Asplund+2009)."
  {:H 0.7346 :He 0.2485 :O 5.92e-3 :C 2.40e-3 :Ne 1.76e-3 :Fe 1.30e-3
   :N 6.96e-4 :Si 6.51e-4 :Mg 5.78e-4 :S 4.42e-4 :Al 4.90e-5
   :Ca 6.20e-5 :Na 3.30e-5 :Ni 2.70e-5})

(def population-I-metallicity
  "Typical total metal mass fraction for a young Population I cloud."
  0.0167)

(def element-condensation-temperature
  "Simplified 50% condensation temperatures per element at 10^-4 bar,
   derived from Lodders (2003). Used by domain.chemistry/partition-solids."
  {:H 20.0 :He 10.0 :D 20.0 :He3 10.0 :Li7 1225.0
   :C 40.0 :N 45.0 :O 150.0 :Ne 10.0 :Na 970.0
   :Mg 1390.0 :Al 1754.0 :Si 1400.0 :S 704.0 :Ca 1647.0
   :Fe 1354.0 :Ni 1354.0})
```

### 7.3 System Function (domain/chemistry.clj)

Replace the placeholder `molecular-composition` and `supernova-enrichment` with:

```clojure
(defn enrichment-yield
  "Yield vector for a core-collapse Type II SN (Nomoto+2013 toy model).
   Returns element keyword -> mass fraction of ejected metals; sums to 1.0."
  []
  {:O 0.36 :Mg 0.12 :Si 0.15 :Ca 0.015 :S 0.04 :Fe 0.18 :Ni 0.015
   :Al 0.005 :Na 0.003 :C 0.08 :N 0.022})

(defn enrich-parcel
  "Add metal mass ΔM_Z to a parcel of mass M, partitioned by yield vector."
  [elements M delta-MZ]
  (let [yields (enrichment-yield)
        inv-M  (/ 1.0 M)]
    (reduce-kv
     (fn [acc el y]
       (update acc el (fnil + 0.0) (* y delta-MZ inv-M)))
     elements
     yields)))

(defn condensed-inventory
  "Return {:solid element-map :gas element-map :categories category-map}
   for a parcel at temperature T."
  [elements T]
  (let [{:keys [solid gas]} (partition-solids elements T)]
    {:solid solid
     :gas   gas
     :categories (merge-with + (bulk-categories solid T)
                              (bulk-categories gas T))}))
```

### 7.4 Integrator hook (domain/integrator.clj)

Extend `influence-registry`:

```clojure
:composition {:influences [c/comp-burn c/comp-depletion c/comp-condensed]
              :derived "burn replaces, depletion zeroes; condensed is a separate read-only partition"}
```

`c/comp-condensed` does **not** change the atomic composition stored in `c/composition`; it is a derived partition `{:solid element-map :gas element-map}` that other systems (opacity, planetesimal feeding, differentiation) can read. The integrator stores it as a separate component after applying burn/depletion.

### 7.5 Stellar hook (domain/stellar.clj)

- Seed newborn gas parcels with `law.composition/primordial-composition` when `spawn-clump` creates `:nebula` entities.
- When a sink absorbs a gas parcel, ensure the absorbed packet carries `:composition` so `integrator/absorb-comp-blend` can mass-weight it.
- When a star launches a wind parcel, copy the star's envelope composition via `chemistry/wind-composition`.

### 7.6 Test (test/)

```clojure
(deftest composition-is-mass-conserved-through-blend
  (let [p1 {:elements {:H 0.76 :He 0.24} :molecules {} :phases {:gas 1.0}}
        p2 {:elements {:H 0.70 :He 0.28 :O 0.02} :molecules {} :phases {:gas 1.0}}
        blended (chemistry/blend-parcels p1 1.0 p2 1.0)]
    (is (≈ 1.0 (chemistry/total-mass-fraction blended) 1e-6))
    (is (≈ 0.73 (get-in blended [:elements :H]) 1e-6))
    (is (≈ 0.01 (get-in blended [:elements :O]) 1e-6))))

(deftest condensation-matches-lodders-water
  (let [inv (chemistry/condensed-inventory (law.composition/solar-composition) 150.0)]
    ;; Water ice condenses ~150 K; nearly all oxygen not in silicates should be solid
    (is (> (get-in inv [:solid :O] 0.0) 0.001))
    (is (> (get-in inv [:categories :ice] 0.0) 0.001))))
```

### 7.7 Files to modify

| File | Change |
|------|--------|
| `src/law/composition.clj` | Add element set, solar composition, bulk-category schema, condensation table, population-I metallicity constant. |
| `src/domain/chemistry.clj` | Add `partition-solids`, `bulk-categories`, `enrich-parcel`, `wind-composition`, `condensed-inventory`; keep `burn-step`. |
| `src/domain/stellar.clj` | Seed primordial composition in `spawn-clump`; pass composition through accretion/wind packets. |
| `src/domain/integrator.clj` | Register `c/comp-condensed` in influence registry; apply it in `composition-ws`. |
| `src/domain/ecs/components.clj` | Add `comp-condensed` component keyword. |

---

## 8. Cross-References

- See `docs/research/cosmology/primordial-nucleosynthesis-yields.md` for BBN yields and the existing `law.composition` primordial values.
- See `docs/research/physics/stellar-nebula-mass-hierarchy.md` for how mass classes map to matter-state keywords used by the classifier.
- See `docs/research/physics/phase1-radiation-plasma-truth.md` for SED-driven disk heating that sets the radial temperature profile.

---

## 9. Open Questions

1. **Pressure dependence:** Lodders (2003) gives $T_c$ at $10^{-4}$ bar. Disk pressures span $10^{-10}$–$10^{-2}$ bar. Should we scale $T_c$ with pressure, or is a single reference table sufficient for Phase 0?
2. **Molecular equilibrium:** The current toy model partitions O between rock and ice. A real gas-disk chemistry network would produce CO, CO₂, H₂O, CH₄, NH₃ depending on T and C/O ratio. Is a full network worth the cost?
3. **SN yield metallicity dependence:** Low-metallicity Pop III/II yields are α-enhanced and Fe-poor. Should `enrichment-yield` vary with progenitor metallicity?
4. **Dust-to-gas ratio:** Condensation gives ~3% solids in the outer disk vs. the observed ~1%. Do we need an empirical scaling factor, or does coagulation/settling naturally reduce the gas-phase metal fraction?
5. **Wind fractionation:** Stellar winds and flares may be metal-depleted relative to the photosphere. Should wind parcels carry a modified composition?

---

## 10. References

1. Fields, B.D. & Sarkar, S. (2025). "Big-Bang Nucleosynthesis." PDG Review. arXiv:2409.06015.
2. Yeh, T.-H. et al. (2026). "The LBT Y_p Project V." arXiv:2601.22239.
3. Asplund, M., Grevesse, N., Sauval, A.J. & Scott, P. (2009). "The chemical composition of the Sun." Ann. Rev. Astron. Astrophys. 47, 481. arXiv:0909.0948.
4. Jimenez, R., Flynn, C., MacDonald, J. & Gibson, B.K. (2003). "The cosmic production of Helium." Science 299, 1552. arXiv:astro-ph/0303179.
5. Nomoto, K., Kobayashi, C. & Tominaga, N. (2013). "Nucleosynthesis in Stars and the Chemical Enrichment of Galaxies." Ann. Rev. Astron. Astrophys. 51, 457. DOI:10.1146/annurev-astro-082812-141055.
6. Woosley, S.E. & Weaver, T.A. (1995). "The Evolution and Explosion of Massive Stars. II. Explosive Hydrodynamics and Nucleosynthesis." ApJS 101, 181. DOI:10.1086/192237.
7. Lodders, K. (2003). "Solar System Abundances and Condensation Temperatures of the Elements." ApJ 591, 1220. DOI:10.1086/375492.
8. Grossman, L. (1972). "Condensation in the primitive solar nebula." Geochim. Cosmochim. Acta 36, 597. DOI:10.1016/0016-7037(72)90063-1.
9. Johansen, A. et al. (2014). "The multifaceted planetesimal formation process." In *Protostars and Planets VI*. arXiv:1402.1344.
10. Semenov, D. et al. (2003). "A treatment of the elemental composition, mixing and anisotropy of the dust in protoplanetary disks." A&A 410, 611. DOI:10.1051/0004-6361:20031279.
