(ns domain.stellar-classification-test
  "M5 Handoff Phase 1: material + thermal classification. Makes planet
   categories explicit and testable from composition and two-body
   temperature — no orbit integration, no atmosphere physics. See
   kanban/tasks/ecology-m5-phase1-planet-classification.md."
  (:require
   [clojure.math :as math]
   [clojure.test :refer [deftest testing is]]
   [domain.ecs.core :as ecs]
   [domain.ecs.components :as c]
   [domain.stellar.classifier.planet :as cls-planet]
   [law.stellar :as law]))

;; --- material-class -----------------------------------------------------

(deftest rocky-planet-classified-by-composition
  (testing "a high-metal/rock, low-H/He body under 1e25 kg is :rocky"
    (let [composition {:Fe 0.32 :Ni 0.02 :Si 0.30 :Mg 0.20 :O 0.10 :H 0.05 :He 0.01}]
      (is (= :rocky (cls-planet/material-class composition law/earth-mass 288.0))))))

(deftest gas-giant-classified-by-hydrogen
  (testing "a high-H/He body over 1e25 kg is :gaseous"
    (let [composition {:H 0.75 :He 0.24 :O 0.01}]
      (is (= :gaseous (cls-planet/material-class composition law/jupiter-mass 120.0))))))

(deftest icy-planet-classified-by-volatiles
  (testing "an ice/volatile-dominant body under 5e25 kg is :icy"
    (let [composition {:O 0.55 :H 0.20 :C 0.10 :N 0.10 :Si 0.05}
          cold 40.0] ;; cold enough that O/C/N condense as ices (Lodders Tc)
      (is (= :icy (cls-planet/material-class composition (* 3.0 law/earth-mass) cold))))))

(deftest mixed-when-no-class-strongly-applies
  (testing "a balanced composition with no dominant category is :mixed"
    (let [composition {:Fe 0.15 :Si 0.15 :H 0.30 :He 0.10 :O 0.30}]
      (is (= :mixed (cls-planet/material-class composition law/earth-mass 288.0))))))

;; --- thermal-band ---------------------------------------------------------

(deftest thermal-band-computed-from-orbit
  (testing "a rocky body at 1 AU from a Sun-like star lands in the temperate band"
    ;; T_eff = (L(1-A) / (16 π σ a²))^0.25 with A=0.3 (rocky albedo) ≈ 255 K,
    ;; the well-known Earth equilibrium-temperature figure.
    (let [band (cls-planet/thermal-band law/solar-luminosity law/au :rocky)]
      (is (= :temperate band))))
  (testing "the same star/albedo far out (30 AU) is frozen"
    (is (= :frozen (cls-planet/thermal-band law/solar-luminosity (* 30.0 law/au) :rocky))))
  (testing "close-in (0.1 AU) is hot"
    (is (= :hot (cls-planet/thermal-band law/solar-luminosity (* 0.1 law/au) :rocky)))))

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
        sys     (cls-planet/classification-system)
        ws      ((:run sys) w)]
    (testing "sole writer of material-class, thermal-band, and orbit-stable"
      (is (= :classification (:id sys)))
      (is (= #{c/material-class c/thermal-band c/orbit-stable
               c/atmosphere-class c/retained-species} (:writes sys))))
    (testing "the planet is classified; the star is not a classification target"
      (is (= :rocky (get-in ws [c/material-class planet])))
      (is (= :temperate (get-in ws [c/thermal-band planet])))
      (is (nil? (get-in ws [c/material-class star]))))))

(deftest orbit-stability-ignores-unbound-ejecta-in-sibling-set
  (testing "a bound planet stays orbit-stable when a distant UNBOUND massive
            body (ejected brown-dwarf) is present — such a body is not a
            co-orbiting sibling and its huge distance-scaled Hill radius must
            not poison the Hill close-approach gate (candidate-emergence bug,
            2026-07-24: eid 191 brown-dwarf at 1.4e5 AU forced orbit-stable
            false for every real inner planet)."
    (let [base       (ecs/empty-world)
          [w star]   (ecs/spawn base)
          [w planet] (ecs/spawn w)
          [w bd]     (ecs/spawn w)
          v-circ     (math/sqrt (/ (* law/G law/solar-mass) law/au))
          w (-> w
                (ecs/put-components star {c/matter-state :star
                                          c/mass law/solar-mass
                                          c/radius 6.957e8
                                          c/luminosity law/solar-luminosity
                                          c/position [0.0 0.0 0.0]
                                          c/velocity [0.0 0.0 0.0]
                                          c/composition {:H 0.71 :He 0.27 :metals 0.02}
                                          c/temperature 5778.0})
                ;; bound, near-circular at 1 AU: passes periapsis/apoapsis gates
                (ecs/put-components planet {c/matter-state :planet
                                            c/mass law/earth-mass
                                            c/position [law/au 0.0 0.0]
                                            c/velocity [0.0 v-circ 0.0]
                                            c/composition {:Fe 0.32 :Ni 0.02 :Si 0.30
                                                           :Mg 0.20 :O 0.10 :H 0.05 :He 0.01}
                                            c/temperature 288.0})
                ;; UNBOUND brown-dwarf flung to 1e5 AU at 5 km/s (escape there
                ;; from 1 Msun is ~133 m/s) — no dominant-attractor. Without the
                ;; fix its Hill radius (~2.6e4 AU) × 10 spuriously overlaps the
                ;; planet 1e5 AU away and forces orbit-stable false.
                (ecs/put-components bd {c/matter-state :brown-dwarf
                                        c/mass (* 0.05 law/solar-mass)
                                        c/position [(* 1.0e5 law/au) 0.0 0.0]
                                        c/velocity [0.0 5000.0 0.0]
                                        c/composition {:H 0.75 :He 0.24 :O 0.01}
                                        c/temperature 800.0}))
          sys (cls-planet/classification-system)
          ws  ((:run sys) w)]
      (is (true? (get-in ws [c/orbit-stable planet]))
          "the bound circular planet is orbit-stable despite the unbound ejecta")
      (is (nil? (get-in ws [c/orbit-stable bd]))
          "the unbound brown-dwarf has no dominant-attractor -> omitted from the verdict"))))

;; --- atmosphere-class (M5 handoff Phase 3) ----------------------------------
;; See kanban/tasks/ecology-m5-phase3-atmosphere-retention.md and the
;; grounding research note docs/research/atmosphere/planetary-atmosphere-
;; retention-classifier.md, which supersedes the parent card's rougher
;; formulas where they conflict (RMS thermal velocity, not most-probable
;; speed; see that note's §3.4).
;;
;; DEVIATION FROM THE CARD (flagged in the card's own 2026-07-22 triage note
;; and the research note §6.1): the card's literal `moon-like-loses-
;; atmosphere` test does NOT return `:none` under grounded Jeans physics — a
;; real Moon (M=7.34e22 kg, R=1.737e6 m, T_eff≈250K) computes retention
;; ratios of r(N2)=5.03, r(CO2)=6.31, r(H2O)=4.04, all above the heavy-
;; species threshold of 3, landing in `:thin`. The Moon's real airlessness is
;; volatile-poor formation (giant-impact origin) plus non-thermal solar-wind
;; sputtering — both outside Jeans-escape scope by construction, not a bug
;; here. Replaced with `hot-fragment-loses-atmosphere`, a genuinely airless
;; small/hot body (M=5e20 kg, R=3e5 m, T=600K) that cleanly returns `:none`.

(deftest earth-like-retains-n2
  (testing "Earth-like mass/radius/temperature retains a thick N2/CO2/H2O atmosphere"
    (let [result (cls-planet/atmosphere-class
                  {:mass law/earth-mass :radius 6.371e6 :temperature 255.0
                   :material-class :rocky :thermal-band :temperate})]
      (is (= :thick (:atmosphere-class result)))
      (is (contains? (:retained-species result) :N2)))))

(deftest gas-giant-retains-h2
  (testing "Jupiter-like mass/radius/temperature retains its primordial H2/He envelope"
    (let [result (cls-planet/atmosphere-class
                  {:mass law/jupiter-mass :radius 6.9911e7 :temperature 110.0
                   :material-class :gaseous :thermal-band :frozen})]
      (is (= :thick (:atmosphere-class result)))
      (is (contains? (:retained-species result) :H2)))))

(deftest hot-fragment-loses-atmosphere
  (testing "a small, hot rocky fragment (M=5e20kg, R=300km, T=600K) is cleanly airless"
    ;; See the deviation note above: this replaces the card's literal
    ;; moon-like-loses-atmosphere test with a body that is unambiguously
    ;; below the Volkov et al. (2011) hydrodynamic-blow-off floor (r<3 for
    ;; every candidate species), rather than a real-world edge case whose
    ;; airlessness is driven by non-thermal effects this classifier does not
    ;; model.
    (let [result (cls-planet/atmosphere-class
                  {:mass 5.0e20 :radius 3.0e5 :temperature 600.0
                   :material-class :rocky :thermal-band :hot})]
      (is (= :none (:atmosphere-class result)))
      (is (empty? (:retained-species result))))))
