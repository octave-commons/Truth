(ns domain.interior-test
  "Voxel 2: planet-candidate -> macro geology field seed
   (kanban/tasks/planet-candidate-to-voxel-seed.md). Fixtures are full
   `:planet-candidate` records in the shape
   `domain.stellar.classifier/build-candidate-record` emits; assertions on
   determinism, mass conservation, thermal/environment agreement, and
   slice-1 law schema conformance of every emitted record."
  (:require
   [clojure.test :refer [deftest is testing]]
   [domain.interior :as interior]
   [law.stellar.schema :as stellar-schema]
   [law.voxel :as voxel]))

;; --- Fixtures (gated-candidate shapes) -----------------------------------------

(def rocky-habitable
  "Earth-like rocky temperate candidate (the common case)."
  {:planet-id              101
   :star-id                1
   :material-class         :rocky
   :thermal-band           :temperate
   :equilibrium-temperature 288.0
   :semi-major-axis        1.496e11
   :eccentricity           0.0167
   :orbit-stable?          true
   :atmosphere-class       :substantial
   :retained-species       #{:N2 :H2O :CO2}
   :bulk-composition       {:Fe 0.30 :Ni 0.02 :Si 0.16 :Mg 0.14 :O 0.30
                            :S 0.03 :Al 0.015 :Ca 0.015 :Na 0.002 :C 0.001
                            :H 0.001 :He 0.0}
   :angular-momentum       [0.0 0.0 5.86e33]
   :rotation-axis          [0.0 0.3978 0.9174]
   :oblateness             0.0034
   :surface-gravity        9.81
   :core-dynamo?           true
   :magnetic-field         [3.0e-5 0.0 0.0]
   :formation-events       [:evt-1]})

(def icy-frozen
  "Europa-like icy frozen candidate: ice-formers ~50% of bulk."
  {:planet-id              102
   :star-id                1
   :material-class         :icy
   :thermal-band           :frozen
   :equilibrium-temperature 102.0
   :semi-major-axis        7.8e11
   :eccentricity           0.009
   :orbit-stable?          true
   :atmosphere-class       :thin
   :retained-species       #{:H2O}
   :bulk-composition       {:O 0.45 :C 0.03 :N 0.02 :Si 0.16 :Mg 0.14
                            :Fe 0.05 :Ni 0.005 :S 0.02 :Ca 0.02 :Al 0.01
                            :Na 0.001 :H 0.069 :He 0.0}
   :angular-momentum       [0.0 0.0 2.0e31]
   :rotation-axis          [0.0 0.0 1.0]
   :oblateness             0.0
   :surface-gravity        1.315
   :core-dynamo?           false
   :magnetic-field         [0.0 0.0 0.0]
   :formation-events       [:evt-2]})

(def mixed-warm
  "Warm mixed-class candidate (undifferentiated bulk, moderate metals)."
  {:planet-id              103
   :star-id                1
   :material-class         :mixed
   :thermal-band           :warm
   :equilibrium-temperature 380.0
   :semi-major-axis        9.0e10
   :eccentricity           0.10
   :orbit-stable?          true
   :atmosphere-class       :thin
   :retained-species       #{:CO2}
   :bulk-composition       {:Fe 0.15 :Ni 0.01 :Si 0.20 :Mg 0.18 :O 0.25
                            :C 0.08 :S 0.04 :Al 0.02 :Ca 0.02 :Na 0.001
                            :H 0.039 :He 0.01 :N 0.01}
   :angular-momentum       [0.0 0.0 1.0e32]
   :rotation-axis          [0.0 0.2 0.98]
   :oblateness             0.001
   :surface-gravity        4.0
   :core-dynamo?           false
   :magnetic-field         [0.0 0.0 0.0]
   :formation-events       [:evt-3]})

(def gaseous-candidate
  "A :gaseous candidate that technically clears the M5 gate (95% H/He
   ceiling) but has no solid surface — the seed must refuse it (design §4)."
  {:planet-id              104
   :star-id                1
   :material-class         :gaseous
   :thermal-band           :temperate
   :equilibrium-temperature 280.0
   :semi-major-axis        3.0e11
   :eccentricity           0.05
   :orbit-stable?          true
   :atmosphere-class       :thick
   :retained-species       #{:H2 :He}
   :bulk-composition       {:H 0.80 :He 0.15 :O 0.02 :C 0.01 :Si 0.01
                            :Fe 0.005 :Mg 0.005}
   :angular-momentum       [0.0 0.0 1.0e38]
   :rotation-axis          [0.0 0.0 1.0]
   :oblateness             0.06
   :surface-gravity        20.0
   :core-dynamo?           true
   :magnetic-field         [1.0e-4 0.0 0.0]
   :formation-events       [:evt-4]})

;; --- Fixture sanity ---------------------------------------------------------------

(deftest fixtures-are-valid-candidates
  (testing "every fixture satisfies the M5 handoff record schema"
    (doseq [c [rocky-habitable icy-frozen mixed-warm gaseous-candidate]]
      (is (stellar-schema/planet-candidate? c)
          (str "fixture fails planet-candidate-schema: " (:planet-id c))))))

;; --- Determinism (THE contract) -----------------------------------------------------

(deftest seed-determinism
  (testing "same candidate -> identical field, bit-for-bit, across two calls"
    (doseq [c [rocky-habitable icy-frozen mixed-warm]]
      (is (= (interior/seed-field c) (interior/seed-field c))
          (str "non-deterministic seed for candidate " (:planet-id c)))))
  (testing "different candidates -> different fields"
    (is (not= (interior/seed-field rocky-habitable)
              (interior/seed-field icy-frozen)))
    (is (not= (interior/seed-field rocky-habitable)
              (interior/seed-field mixed-warm))))
  (testing "the canonical voxel edge is the pinned law constant"
    (is (= voxel/canonical-voxel-edge-m
           (:canonical-voxel-edge-m (interior/seed-field rocky-habitable))))))

;; --- Mass conservation ---------------------------------------------------------------

(deftest mass-conservation
  (doseq [c [rocky-habitable icy-frozen mixed-warm]]
    (testing (str "candidate " (:planet-id c))
      (let [field      (interior/seed-field c)
            layer-mass (reduce + 0.0 (map :mass (:layers field)))]
        (is (< (abs (- layer-mass (:mass-kg field))) (* 1.0e-9 (:mass-kg field)))
            "layer masses sum to the derived body mass")
        (let [shell-mass    (:mass (last (:layers field)))
              resource-mass (reduce + 0.0 (map :total-mass (:resources field)))]
          (is (< (abs (- resource-mass shell-mass)) (* 1.0e-9 shell-mass))
              "resource cells partition the crust/shell mass exactly"))
        (is (pos? (:mass-kg field)))
        (is (pos? (:radius-m field)))))))

;; --- Thermal / environment agreement ----------------------------------------------------

(deftest thermal-agreement
  (testing "surface temperature is the candidate's equilibrium temperature"
    (doseq [c [rocky-habitable icy-frozen mixed-warm]]
      (is (= (double (:equilibrium-temperature c))
             (get-in (interior/seed-field c) [:thermal :surface-temperature])))))
  (testing "nil equilibrium temperature falls back to the thermal-band midpoint"
    (is (= 300.0 (get-in (interior/seed-field
                          (assoc rocky-habitable :equilibrium-temperature nil))
                         [:thermal :surface-temperature])))
    (is (= 100.0 (get-in (interior/seed-field
                          (assoc icy-frozen :equilibrium-temperature nil))
                         [:thermal :surface-temperature]))))
  (testing "environment agrees with the §4.3 ladder the future domain.environment owns"
    (is (= :env/temperate-habitable (:environment (interior/seed-field rocky-habitable))))
    (is (= :env/icy-volatile-world  (:environment (interior/seed-field icy-frozen))))
    (is (= :env/crusted-volcanic    (:environment (interior/seed-field mixed-warm))))
    (is (= :env/magma-ocean         (:environment (interior/seed-field
                                                   (assoc rocky-habitable :thermal-band :hot))))))
  (testing "interior temperatures are at or above the surface and band-capped"
    (doseq [c [rocky-habitable icy-frozen mixed-warm]]
      (let [field (interior/seed-field c)
            t-surf (get-in field [:thermal :surface-temperature])]
        (is (>= (get-in field [:thermal :core-temperature]) t-surf))
        (doseq [layer (:layers field)]
          (is (>= (:temperature layer) t-surf)))))))

;; --- Schema conformance of every emitted record -------------------------------------------

(deftest schema-conformance
  (doseq [c [rocky-habitable icy-frozen mixed-warm]]
    (testing (str "candidate " (:planet-id c))
      (let [field (interior/seed-field c)]
        (doseq [layer (:layers field)]
          (is (voxel/macro-layer? layer) (str "layer: " layer)))
        (doseq [plate (:plates field)]
          (is (voxel/plate? plate) (str "plate: " plate)))
        (doseq [cell (:convection field)]
          (is (voxel/mantle-convection-cell? cell) (str "cell: " cell)))
        (doseq [cell (:resources field)]
          (is (voxel/resource-cell? cell) (str "resource cell: " cell))
          (is (voxel/element-density? (:density-per-element cell))))))))

;; --- Non-circular anchors -----------------------------------------------------

(deftest earth-anchor
  (testing "rocky-habitable derives radius/mass within 20% of Earth"
    (let [field (interior/seed-field rocky-habitable)]
      (is (< (abs (- (:radius-m field) 6.371e6)) (* 0.2 6.371e6))
          (str "derived radius off Earth anchor: " (:radius-m field)))
      (is (< (abs (- (:mass-kg field) 5.972e24)) (* 0.2 5.972e24))
          (str "derived mass off Earth anchor: " (:mass-kg field))))))

(deftest icy-density-floor
  (testing "icy fixture derives a sane density (>= 1500 kg/m³; Enceladus 1610, Europa 3010)"
    (is (>= (:mean-density (interior/seed-field icy-frozen)) 1500.0))))

;; --- Resource-field structure (design §7.4 qualitative steer) ------------------------------

(defn- cell-iron-share
  "Fe+Ni share of a resource cell's density-per-element (kg/m³ normalized).
   Reduction over (sort-by key ...) so the addition order is spec-stable."
  [cell]
  (let [dpe   (:density-per-element cell)
        total (reduce + 0.0 (map val (sort-by key dpe)))]
    (/ (+ (double (get dpe :Fe 0.0)) (double (get dpe :Ni 0.0))) total)))

(deftest resource-enrichment-pattern
  (let [field     (interior/seed-field rocky-habitable)
        resources (:resources field)]
    (testing "some cells are iron-enriched above the background share"
      (let [shares (map cell-iron-share resources)
            hi     (apply max shares)
            lo     (apply min shares)]
        (is (> hi lo))))
    (testing "the convergent-margin enrichment is the strongest iron signal"
      (let [by-iron (sort-by cell-iron-share resources)
            richest (last by-iron)
            dpe     (:density-per-element richest)]
        (is (pos? (double (get dpe :Fe 0.0))))))))

(deftest polar-ice-cells
  (let [field (interior/seed-field icy-frozen)
        ice-only (filterv (fn [cell]
                            (every? (fn [[e _]] (contains? #{:C :N :O} e))
                                    (:density-per-element cell)))
                          (:resources field))]
    (testing "the icy frozen world seeds pure ice-former cells (polar caps)"
      (is (seq ice-only)))
    (testing "the temperate rocky world seeds none"
      (is (empty? (filterv (fn [cell]
                             (every? (fn [[e _]] (contains? #{:C :N :O} e))
                                     (:density-per-element cell)))
                           (:resources (interior/seed-field rocky-habitable))))))))

;; --- Input contract -------------------------------------------------------------------------

(deftest gaseous-candidates-are-refused
  (testing ":gaseous has no solid surface to seed (design §4) — fail loudly"
    (is (thrown? clojure.lang.ExceptionInfo
                 (interior/seed-field gaseous-candidate)))))

(deftest layer-stack-shape
  (testing "rocky worlds seed core/mantle/crust inside-out with growing radii"
    (let [layers (:layers (interior/seed-field rocky-habitable))]
      (is (= [:core :mantle :crust] (mapv :name layers)))
      (is (zero? (:inner-radius (first layers))))
      (is (apply <= (mapcat (juxt :inner-radius :outer-radius) layers)))))
  (testing "icy worlds seed an ice shell"
    (is (= :ice-shell (:name (last (:layers (interior/seed-field icy-frozen))))))))
