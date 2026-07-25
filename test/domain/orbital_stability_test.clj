(ns domain.orbital-stability-test
  "M5 Handoff Phase 2: analytic orbit-stability proxy. See
   kanban/tasks/ecology-m5-phase2-orbit-stability.md and parent
   kanban/tasks/ecology-water-gate-snowline.md §3.3."
  (:require
   [clojure.math :as math]
   [clojure.test :refer [deftest testing is]]
   [domain.ecs.core :as ecs]
   [domain.ecs.components :as c]
   [domain.orbital.stability :as stability]
   [domain.stellar.classifier.planet :as cls-planet]
   [law.stellar :as law]))

(def ^:private softening
  "A softening length far below 1 AU — well inside the regime where
   `softened-circular-speed` and plain Kepler agree, but still the mandated
   softened-field helper for building a physically valid circular-orbit
   fixture." 1.0e7)

(def ^:private star
  {:position [0.0 0.0 0.0]
   :velocity [0.0 0.0 0.0]
   :mass     law/solar-mass
   :radius   law/solar-radius})

;; --- two-body-elements -------------------------------------------------------

(deftest circular-orbit-is-stable
  (testing "a circular orbit at 1 AU (periapsis = apoapsis = 1 AU) is stable"
    (let [v-circ (law/softened-circular-speed law/solar-mass law/au softening)
          planet {:position [law/au 0.0 0.0]
                  :velocity [0.0 v-circ 0.0]
                  :mass     law/earth-mass}]
      (is (true? (stability/orbit-stability planet star []))))))

(deftest plunging-orbit-is-unstable
  (testing "a purely radial infall trajectory has zero angular momentum, so
            eccentricity 1 and periapsis 0 — unstable regardless of apoapsis"
    (let [r        law/au
          mu       (* law/G law/solar-mass)
          v-esc    (math/sqrt (/ (* 2.0 mu) r))
          v-radial (* 0.3 v-esc) ;; bound (< v-esc) but wholly radial
          planet   {:position [r 0.0 0.0]
                    :velocity [(- v-radial) 0.0 0.0]
                    :mass     law/earth-mass}]
      (is (false? (stability/orbit-stability planet star []))))))

(deftest close-planet-pair-is-unstable
  (testing "an otherwise-stable circular orbit is marked unstable when a
            sibling candidate sits within 10 Hill radii"
    (let [v-circ (law/softened-circular-speed law/solar-mass law/au softening)
          planet {:position [law/au 0.0 0.0]
                  :velocity [0.0 v-circ 0.0]
                  :mass     law/earth-mass}
          sibling {:position [(+ law/au 1.0e8) 0.0 0.0]
                   :mass     law/earth-mass}]
      ;; Sanity: the same orbit alone (no siblings) is stable.
      (is (true? (stability/orbit-stability planet star [])))
      (is (false? (stability/orbit-stability planet star [sibling]))))))

;; --- classification-system fan-out (orbit-stable component) -----------------

(deftest classification-system-writes-orbit-stable
  (let [base     (ecs/empty-world)
        [w star-eid]   (ecs/spawn base)
        [w planet-eid] (ecs/spawn w)
        v-circ   (law/softened-circular-speed law/solar-mass law/au softening)
        w (-> w
              (ecs/put-components star-eid {c/matter-state :star
                                            c/mass law/solar-mass
                                            c/radius law/solar-radius
                                            c/luminosity law/solar-luminosity
                                            c/position [0.0 0.0 0.0]
                                            c/velocity [0.0 0.0 0.0]
                                            c/composition {:H 0.71 :He 0.27 :metals 0.02}
                                            c/temperature 5778.0})
              (ecs/put-components planet-eid {c/matter-state :planet
                                              c/mass law/earth-mass
                                              c/position [law/au 0.0 0.0]
                                              c/velocity [0.0 v-circ 0.0]
                                              c/composition {:Fe 0.32 :Ni 0.02 :Si 0.30
                                                             :Mg 0.20 :O 0.10 :H 0.05 :He 0.01}
                                              c/temperature 288.0}))
        sys      (cls-planet/classification-system)
        ws       ((:run sys) w)]
    (testing "sole writer of material-class, thermal-band, orbit-stable,
              atmosphere-class, AND retained-species"
      (is (= #{c/material-class c/thermal-band c/orbit-stable
               c/atmosphere-class c/retained-species} (:writes sys))))
    (testing "the circular-orbit planet is marked stable"
      (is (true? (get-in ws [c/orbit-stable planet-eid])))
      (is (nil? (get-in ws [c/orbit-stable star-eid]))))))
