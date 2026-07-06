# Stellar Nebula Mass Hierarchy: Replacing the `:debris` Bucket

**Domain:** physics | **Phase:** 0  
**Date:** 2026-07-06 | **Author:** opencode-session `ses_0ca845358ffeph0V0fRneN4WZy`  
**Status:** draft  
**Primary sources:** Krumholz (2014), Hennebelle & Chabrier (2008, 2009), Spiegel et al. (2010), Johansen et al. (2014), Pearson & McCaughrean (2023), Cui et al. (2026), Krumholz et al. (2016), Whitworth (2018), De Furio et al. (2024)

---

## 1. Research Question

Phase 0 currently routes every self-gravitating condensation below the deuterium-burning limit into a single matter-state, `:debris`. That bucket spans roughly four orders of magnitude in mass — from a single gas parcel (~2 M_J) up to just below brown-dwarf ignition (~13 M_J) — and pays out as if it were one kind of event. The result is runaway, repetitive agency income from transient fragments that form, merge, and are re-absorbed.

We need a literature-grounded mass ladder that:

1. Replaces `:debris` with physically meaningful classes.
2. Keeps the simulation honest about its resolution limits.
3. Gives the agency/resonance economy distinct thresholds to reward.
4. Maps cleanly onto the existing ECS classifier in `domain.stellar`.

---

## 2. Literature Survey

### 2.1 The opacity floor: how small can a star-forming fragment be?

The minimum mass of an object that can form directly by gravitational fragmentation of a molecular cloud is set by the **opacity limit**: a fragment must be able to radiate away its gravitational binding energy on a dynamical time. If it is too small, it cannot cool fast enough and pressure support halts collapse (Low & Lynden-Bell 1976; Rees 1976).

> **Key finding:** The opacity-limited minimum fragment is typically **~0.003–0.01 M_☉** (roughly **3–10 M_J**), depending on metallicity, dust properties, and the exact cooling assumption.

Modern radiation-MHD simulations that resolve the opacity limit consistently produce a turnover in the substellar mass function near a few Jupiter masses. Krumholz et al. (2016) show that radiative feedback limits further fragmentation and that the IMF peak lies ~20–30× above the opacity limit because warmed gas around low-mass fragments is unable to escape and is instead accreted. De Furio et al. (2024), using JWST/NIRCam observations of NGC 2024, identify a turnover near **~3 M_J**, consistent with the opacity limit.

**Citation:** Krumholz, M. R., Myers, A. T., Klein, R. I., & McKee, C. F. (2016). "What Physics Determines the Peak of the IMF? Insights from the Structure of Cores in Radiation-Magnetohydrodynamic Simulations." *MNRAS*, 460, 3272–3283. arXiv:1603.04557. DOI:10.1093/mnras/stw1236

**Citation:** De Furio, M., Meyer, M. R., Greene, T., et al. (2024). "Identification of a turnover in the initial mass function of a young stellar cluster down to 0.5 M_J." *ApJL*, 944, L5. arXiv:2409.04624. DOI:10.3847/2041-8213/adb96a

### 2.2 The deuterium and hydrogen burning boundaries

The IAU working definition places the planet/brown-dwarf boundary at the **deuterium-burning mass limit**, and the brown-dwarf/star boundary at the **hydrogen-burning minimum mass**.

Spiegel, Burrows, & Milsom (2010) calculate that the deuterium limit depends on helium abundance, metallicity, and the fraction of primordial deuterium that must combust, but **13 M_J** is a robust rule of thumb:

> **Key finding:** For a wide range of models, 50% of the initial deuterium burns for masses of **~(13.0 ± 0.8) M_J**; the full range is **~11–16 M_J**.

The hydrogen-burning minimum mass is close to **0.075–0.08 M_☉** (~75–80 M_J). Objects between the two limits burn deuterium, contract until electron degeneracy pressure halts them, and then cool forever: brown dwarfs.

**Citation:** Spiegel, D. S., Burrows, A., & Milsom, J. A. (2010). "The Deuterium-Burning Mass Limit for Brown Dwarfs and Giant Planets." *ApJ*, 727, 57. arXiv:1008.5150. DOI:10.1088/0004-637X/727/1/57

### 2.3 The brown-dwarf desert and the planet/brown-dwarf boundary

Cui et al. (2026) analyse a combined radial-velocity/astrometry sample of 55 substellar companions to FGK stars and report a **brown-dwarf desert at ~30 M_J**:

> **Key finding:** There is a distinct dip in the occurrence-rate distribution near **30 M_J**, with different metallicity and eccentricity trends on either side, suggesting two formation channels: core accretion at lower masses and turbulent fragmentation/ejection at higher masses.

This does not replace the deuterium limit as a physical boundary, but it is a strong population-level signal that the mass range 13–30 M_J is relatively rare. For a game classification whose purpose is to label *what the observer is seeing*, 30 M_J is a useful secondary threshold between "giant planet / super-Jovian" and "brown dwarf".

**Citation:** Cui, K., Xiao, G.-Y., Feng, F., et al. (2026). "A universal brown dwarf desert formed between planets and stars." *PNAS*, 123, e2524764123. arXiv:2603.01808. DOI:10.1073/pnas.2524764123

### 2.4 Planetesimals: the solid rung below direct gas fragmentation

Below the opacity limit, objects cannot form directly from turbulent gas fragmentation. They must grow through solids in a protoplanetary disk. Johansen et al. (2014) review the planetesimal-formation pathway:

> **Key finding:** Streaming instability and gravitational collapse of pebble filaments produce planetesimals with contracted radii of **100–500 km**, corresponding to masses of roughly **10^18–10^21 kg** (Ceres-like). These are the seeds of terrestrial planets and the solid cores of gas giants.

This mass range is **far below** the resolution of a kilo-parcel Phase 0 cloud, where one gas parcel is ~4×10^27 kg. Any in-game `:planetesimal` state is therefore a placeholder for the unresolved solid-growth ladder, not a resolved body.

**Citation:** Johansen, A., Blum, J., Tanaka, H., et al. (2014). "The multifaceted planetesimal formation process." *Protostars and Planets VI*, University of Arizona Press. arXiv:1402.1344. DOI:10.2458/azu_uapress_9780816531240-ch024

### 2.5 The low-mass end of the IMF

Pearson & McCaughrean (2023) used JWST to identify 540 free-floating planetary-mass candidates in the Trapezium Cluster down to **0.6 M_J**, showing that the mass function extends smoothly below the opacity limit with no sharp cut-off. Whitworth (2018) and Caballero (2018) review the nomenclature: isolated non-deuterium-burning substellar objects share the same turbulent-fragmentation channel as brown dwarfs, but their existence below the classical opacity limit implies that ejection, photoerosion, or binary decay can produce them.

**Citation:** Pearson, S. G., & McCaughrean, M. J. (2023). "Jupiter Mass Binary Objects in the Trapezium Cluster." arXiv:2310.01231.

**Citation:** Whitworth, A. (2018). "Brown Dwarf Formation: Theory." arXiv:1811.06833.

---

## 3. Governing Equations

### 3.1 Jeans mass (fragmentation threshold)

For an isothermal gas of sound speed $c_s$ and density $\rho$, the Jeans mass is

$$
M_J = \frac{\pi^{5/2}}{6} \frac{c_s^3}{\sqrt{G^3 \rho}}
      \approx \frac{\pi^{5/2}}{6} \frac{\left(\frac{\gamma k_B T}{\mu m_H}\right)^{3/2}}
                              {\sqrt{G^3 \rho}} .
$$

A region that is Jeans-unstable and optically thick ($\rho \gtrsim 10^{-10}$ kg m⁻³) can form a hydrostatic core.

### 3.2 Deuterium-burning mass

No closed-form analytic threshold; Spiegel et al. (2010) give an empirical minimum

$$
M_{\rm D\ burn} \approx 13\ M_J \approx 2.6\times10^{28}\ {\rm kg}
                      \approx 0.013\ M_\odot .
$$

### 3.3 Hydrogen-burning minimum mass

$$
M_{\rm H\ burn} \approx 0.075\text{--}0.08\ M_\odot
                      \approx 1.5\times10^{29}\ {\rm kg}
                      \approx 80\ M_J .
$$

### 3.4 Opacity-limited minimum fragment mass

$$
M_{\rm op} \sim 0.003\ M_\odot \sim 3\ M_J
              \sim 6\times10^{27}\ {\rm kg} .
$$

---

## 4. Implementation Sketch (Clojure Pseudocode)

```clojure
(ns law.stellar
  "Mass thresholds for the resolved Phase 0 matter-state ladder.
   All values are in SI kg; they correspond to the physical boundaries
   reviewed in §2-§3.")

(def ^:const solar-mass 1.989e30)
(def ^:const jupiter-mass 1.898e27)

;; Physical thresholds
(def ^:const opacity-limit-mass        (* 0.003 solar-mass)) ;; ~3 M_J
(def ^:const deuterium-burning-mass    (* 0.013 solar-mass)) ;; ~13 M_J
(def ^:const hydrogen-burning-mass     (* 0.080 solar-mass)) ;; ~80 M_J

;; Convenience aliases for the brown-dwarf desert (Cui et al. 2026)
(def ^:const brown-dwarf-desert-mass   (* 30.0 jupiter-mass)) ;; ~0.015 M_☉

(defn substellar-mass-class
  "Classify a resolved (non-nebula) body purely by mass.
   Returns one of :planetesimal, :gas-giant, :brown-dwarf, :protostar.
   A protostar is any body above the hydrogen-burning limit that has not
   yet ignited; ignition is decided separately by fusion conditions."
  [mass]
  (cond
    (>= mass hydrogen-burning-mass)              :protostar
    (>= mass brown-dwarf-desert-mass)            :brown-dwarf
    (>= mass opacity-limit-mass)                 :gas-giant
    :else                                        :planetesimal))
```

```clojure
(ns domain.stellar
  "Classifier transition logic with the new mass ladder.")

(defn classify-next-state
  "Pure transition function for one body's matter-state.
   Condensation (nebula -> resolved) is gated by Jeans instability and the
   optically-thick core density, exactly as before.  Once resolved, the body
   is sorted into the mass ladder reviewed above.  Ignition and brown-dwarf
   stalling are unchanged."
  [{:keys [matter-state mass density temperature] :as region}
   gas-particle-mass sink-zones]
  (case matter-state
    :star        ... ;; unchanged hysteresis logic
    :brown-dwarf (if (>= mass law/deuterium-burning-mass) :brown-dwarf
                     (law/substellar-mass-class mass))
    :protostar   ... ;; unchanged: star if fusion + H-burn mass, brown dwarf if stalled
    :gas-giant    (if (>= mass law/deuterium-burning-mass) :brown-dwarf
                     (law/substellar-mass-class mass))
    :planetesimal (if (>= mass law/deuterium-burning-mass) :brown-dwarf
                     (law/substellar-mass-class mass))
    ;; :nebula condensation branch
    (if (and (jeans-unstable? region)
             (or (>= (double (or density 0.0)) core-condensation-density)
                 (> (double mass) (double gas-particle-mass)))
             (not (within-existing-sink? (:position region) sink-zones)))
      (if (>= mass law/deuterium-burning-mass)
        :protostar
        (law/substellar-mass-class mass))
      :nebula)))
```

```clojure
(ns domain.genesis
  "Per-body promotion events now distinguish every rung of the ladder.")

(defn- promotion-event-kind
  [old-state new-state]
  (case new-state
    :star          (when (= old-state :protostar)     :event/stellar-ignition)
    :protostar     (when (or (nil? old-state)
                             (= old-state :nebula))   :event/protostar-formation)
    :brown-dwarf   (when (or (= old-state :nebula)
                             (= old-state :gas-giant)
                             (= old-state :planetesimal))
                                                :event/brown-dwarf-formation)
    :gas-giant     (when (or (= old-state :nebula)
                             (= old-state :planetesimal))
                                                :event/gas-giant-formation)
    :planetesimal  (when (= old-state :nebula)         :event/planetesimal-formation)
    :planet        (when (= old-state :nebula)         :event/planet-formation)
    nil))
```

```clojure
(ns domain.player
  "Agency/resonance payouts now reward each distinct threshold.")

(defn agency-gain-from-event [event-type]
  (case event-type
    :planetesimal-formation  2.0
    :gas-giant-formation     4.0
    :brown-dwarf-formation   8.0
    :protostar-formation    12.0
    :stellar-ignition       25.0
    :planet-formation       10.0
    ...))

(defn resonance-gain-from-event [event-type]
  (case event-type
    :planetesimal-formation  1
    :gas-giant-formation     1
    :brown-dwarf-formation   1
    :protostar-formation     1
    :stellar-ignition        2
    :planet-formation        1
    ...))
```

---

## 5. Toy Model / Numerical Experiment

### 5.1 Setup

Use the physical thresholds from §3 to map mass to matter-state and compare with the simulation's default gas-parcel mass.

### 5.2 Results

| Boundary | Mass (kg) | Mass (M_J) | Mass (M_☉) | Notes |
|----------|-----------|------------|------------|-------|
| One default gas parcel (4 M_☉ / 1000) | 4.0×10²⁷ | 2.1 | 0.0020 | Smallest resolved unit |
| Opacity limit (M_op) | 6.0×10²⁷ | 3.1 | 0.0030 | Direct fragmentation floor |
| Deuterium-burning limit | 2.6×10²⁸ | 13.6 | 0.013 | Planet / brown-dwarf boundary |
| Brown-dwarf desert (Cui+2026) | 5.7×10²⁸ | 30 | 0.029 | Population dip |
| Hydrogen-burning minimum mass | 1.6×10²⁹ | 83.8 | 0.080 | Star threshold |

With the proposed ladder, a single default gas parcel condenses as a `:planetesimal` (it is below the opacity limit and is the engine's stand-in for the unresolved solid-growth ladder). As parcels merge and cross the opacity limit, the body becomes a `:gas-giant`; crossing the deuterium limit makes it a `:brown-dwarf`; crossing the hydrogen limit makes it a `:protostar`; fusion makes it a `:star`.

### 5.3 Validation against literature

- The deuterium limit (13 M_J) and hydrogen limit (80 M_J) match Spiegel et al. (2010) and the IAU convention.
- The opacity limit (~3 M_J) matches De Furio et al. (2024) and Krumholz et al. (2016).
- The brown-dwarf desert at ~30 M_J matches Cui et al. (2026).
- The planetesimal mass range (10¹⁸–10²¹ kg) is far below one gas parcel; the `:planetesimal` state is therefore a *resolution-limited proxy* and is documented as such.

---

## 6. Validation Checklist

- [x] Matches published mass boundaries for deuterium and hydrogen burning.
- [x] Matches published opacity-limited minimum fragment mass.
- [x] Brown-dwarf desert threshold taken from a recent observational study.
- [x] Planetesimal range grounded in streaming-instability literature.
- [ ] Implementation passes `test/architecture_test.clj` (single ECS substrate preserved).
- [ ] Implementation passes updated `domain.player-test` agency/resonance contracts.
- [ ] Live sim no longer pays repeatedly for the same transient `:planetesimal` -> merge cycle.

---

## 7. Promotion Path to Domain

### 7.1 ECS Components

No new ECS components are required. `c/matter-state` already carries the keyword. The schema update below widens the allowed set of keywords.

### 7.2 Malli Schema (law/)

```clojure
(def matter-state-schema
  "Schema for matter in various states from nebula to star"
  {:id          (some-fn uuid? integer?)
   :position    vector?
   :velocity    vector?
   :mass        pos?
   :radius      pos?
   :temperature pos?
   :density     pos?
   :composition map?
   :state       [:enum :nebula :planetesimal :gas-giant :brown-dwarf
                          :protostar :star :planet]
   :luminosity  number?
   :pressure    number?})
```

### 7.3 System Function (domain/)

The only systems that need change are:

- `law.stellar/substellar-mass-class` (new helper).
- `domain.stellar/classify-next-state` (replace `:debris` with mass-ladder branches).
- `domain.genesis/promotion-event-kind` (emit per-rung events).
- `domain.player/agency-gain-from-event` and `resonance-gain-from-event` (payout table).
- `domain.stellar/jeans-collapse-system` and `classify-system` (stop using the historical `law/mass-class` `:debris/:planet` tiers if still wired anywhere in the live path).

### 7.4 Test (test/)

```clojure
(deftest substellar-mass-ladder-boundaries
  (let [law law.stellar]
    (is (= :planetesimal (law/substellar-mass-class (* 0.002 law/solar-mass))))
    (is (= :gas-giant    (law/substellar-mass-class (* 0.005 law/solar-mass))))
    (is (= :brown-dwarf  (law/substellar-mass-class (* 0.020 law/solar-mass))))
    (is (= :protostar    (law/substellar-mass-class (* 0.100 law/solar-mass))))))
```

---

## 8. Open Questions

1. **Single-parcel naming:** A 2 M_J gas parcel is below the opacity limit and cannot be a real planetesimal. Should the lowest rung be called `:pebble` or `:fragment` instead of `:planetesimal`? The choice is cosmetic as long as the documentation states the proxy nature.
2. **Brown-dwarf desert as a classifier boundary:** Using 30 M_J to split `:gas-giant` from `:brown-dwarf` is observationally motivated but not a hard phase transition. Should the classifier instead use only the deuterium limit (13 M_J) and reserve the 30 M_J boundary for an optional `:super-jovian` tier?
3. **Disk-seeded planets:** The sub-grid planet seeder currently spawns `:planet` with a `:planet-type`. Should `:planet` remain a separate formation channel, or should disk-seeded giant planets also pass through `:gas-giant`? Recommendation: keep `:planet` for core-accretion products; direct gas-fragment giants are `:gas-giant`.
4. **Repeated formation suppression:** The classifier already throttles nebula->resolved transitions to one body per tick, but fragments can still oscillate if they shed mass and re-condense. Should the agency/resonance system track per-entity lifetime promotion records to prevent paying for the same material twice?

---

## 9. References

1. Krumholz, M. R. (2014). "The Big Problems in Star Formation: the Star Formation Rate, Stellar Clustering, and the Initial Mass Function." *Physics Reports*, 539, 49. arXiv:1402.0867. DOI:10.1016/j.physrep.2014.02.001
2. Hennebelle, P., & Chabrier, G. (2008). "Analytical theory for the initial mass function: CO clumps and prestellar cores." *ApJ*, 684, 395. arXiv:0805.0691. DOI:10.1086/589916
3. Hennebelle, P., & Chabrier, G. (2009). "Analytical theory for the initial mass function: II. Properties of the flow." *ApJ*, 702, 1428. arXiv:0907.2765. DOI:10.1088/0004-637X/702/2/1428
4. Spiegel, D. S., Burrows, A., & Milsom, J. A. (2010). "The Deuterium-Burning Mass Limit for Brown Dwarfs and Giant Planets." *ApJ*, 727, 57. arXiv:1008.5150. DOI:10.1088/0004-637X/727/1/57
5. Johansen, A., Blum, J., Tanaka, H., et al. (2014). "The multifaceted planetesimal formation process." *Protostars and Planets VI*. arXiv:1402.1344. DOI:10.2458/azu_uapress_9780816531240-ch024
6. Pearson, S. G., & McCaughrean, M. J. (2023). "Jupiter Mass Binary Objects in the Trapezium Cluster." arXiv:2310.01231.
7. Cui, K., Xiao, G.-Y., Feng, F., et al. (2026). "A universal brown dwarf desert formed between planets and stars." *PNAS*, 123, e2524764123. arXiv:2603.01808. DOI:10.1073/pnas.2524764123
8. Krumholz, M. R., Myers, A. T., Klein, R. I., & McKee, C. F. (2016). "What Physics Determines the Peak of the IMF?" *MNRAS*, 460, 3272. arXiv:1603.04557. DOI:10.1093/mnras/stw1236
9. Whitworth, A. (2018). "Brown Dwarf Formation: Theory." arXiv:1811.06833.
10. De Furio, M., Meyer, M. R., Greene, T., et al. (2024). "Identification of a turnover in the initial mass function of a young stellar cluster down to 0.5 M_J." *ApJL*, 944, L5. arXiv:2409.04624. DOI:10.3847/2041-8213/adb96a
