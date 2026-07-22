(ns domain.stellar-classification-test
  "M5 Handoff Phase 1: material + thermal classification. Makes planet
   categories explicit and testable from composition and two-body
   temperature — no orbit integration, no atmosphere physics. See
   kanban/tasks/ecology-m5-phase1-planet-classification.md."
  (:require
   [clojure.test :refer [deftest testing is]]
   [domain.ecs.core :as ecs]
   [domain.ecs.components :as c]
   [domain.stellar.classifier :as classifier]
   [law.stellar :as law]))

;; --- material-class -----------------------------------------------------

(deftest rocky-planet-classified-by-composition
  (testing "a high-metal/rock, low-H/He body under 1e25 kg is :rocky"
    (let [composition {:Fe 0.32 :Ni 0.02 :Si 0.30 :Mg 0.20 :O 0.10 :H 0.05 :He 0.01}]
      (is (= :rocky (classifier/material-class composition law/earth-mass 288.0))))))

(deftest gas-giant-classified-by-hydrogen
  (testing "a high-H/He body over 1e25 kg is :gaseous"
    (let [composition {:H 0.75 :He 0.24 :O 0.01}]
      (is (= :gaseous (classifier/material-class composition law/jupiter-mass 120.0))))))

(deftest icy-planet-classified-by-volatiles
  (testing "an ice/volatile-dominant body under 5e25 kg is :icy"
    (let [composition {:O 0.55 :H 0.20 :C 0.10 :N 0.10 :Si 0.05}
          cold 40.0] ;; cold enough that O/C/N condense as ices (Lodders Tc)
      (is (= :icy (classifier/material-class composition (* 3.0 law/earth-mass) cold))))))

(deftest mixed-when-no-class-strongly-applies
  (testing "a balanced composition with no dominant category is :mixed"
    (let [composition {:Fe 0.15 :Si 0.15 :H 0.30 :He 0.10 :O 0.30}]
      (is (= :mixed (classifier/material-class composition law/earth-mass 288.0))))))

;; --- thermal-band ---------------------------------------------------------

(deftest thermal-band-computed-from-orbit
  (testing "a rocky body at 1 AU from a Sun-like star lands in the temperate band"
    ;; T_eff = (L(1-A) / (16 π σ a²))^0.25 with A=0.3 (rocky albedo) ≈ 255 K,
    ;; the well-known Earth equilibrium-temperature figure.
    (let [band (classifier/thermal-band law/solar-luminosity law/au :rocky)]
      (is (= :temperate band))))
  (testing "the same star/albedo far out (30 AU) is frozen"
    (is (= :frozen (classifier/thermal-band law/solar-luminosity (* 30.0 law/au) :rocky))))
  (testing "close-in (0.1 AU) is hot"
    (is (= :hot (classifier/thermal-band law/solar-luminosity (* 0.1 law/au) :rocky)))))

;; --- classification-system fan-out ----------------------------------------

(deftest classification-system-writes-material-class-and-thermal-band
  (let [base    (ecs/empty-world)
        [w star] (ecs/spawn base)
        [w planet] (ecs/spawn w)
        w (-> w
              (ecs/put-components star {c/matter-state :star
                                        c/mass law/solar-mass
                                        c/luminosity law/solar-luminosity
                                        c/position [0.0 0.0 0.0]
                                        c/composition {:H 0.71 :He 0.27 :metals 0.02}
                                        c/temperature 5778.0})
              (ecs/put-components planet {c/matter-state :planet
                                          c/mass law/earth-mass
                                          c/position [law/au 0.0 0.0]
                                          c/composition {:Fe 0.32 :Ni 0.02 :Si 0.30
                                                         :Mg 0.20 :O 0.10 :H 0.05 :He 0.01}
                                          c/temperature 288.0}))
        sys     (classifier/classification-system)
        ws      ((:run sys) w)]
    (testing "sole writer of material-class, thermal-band, and orbit-stable"
      (is (= :classification (:id sys)))
      (is (= #{c/material-class c/thermal-band c/orbit-stable} (:writes sys))))
    (testing "the planet is classified; the star is not a classification target"
      (is (= :rocky (get-in ws [c/material-class planet])))
      (is (= :temperate (get-in ws [c/thermal-band planet])))
      (is (nil? (get-in ws [c/material-class star]))))))
