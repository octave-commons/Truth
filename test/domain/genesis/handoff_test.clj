(ns domain.genesis.handoff-test
  "M5 Handoff Phase 4: the :planet-candidate record + :event/phase0-handoff
   ledger event. See kanban/tasks/ecology-m5-phase4-handoff-event.md and
   parent kanban/tasks/ecology-water-gate-snowline.md §2, §5."
  (:require
   [clojure.test :refer [deftest testing is]]
   [domain.arc :as arc]
   [domain.ecs.core :as ecs]
   [domain.ecs.components :as c]
   [domain.ecs.event :as event]
   [domain.genesis :as genesis]
   [domain.stellar.classifier :as classifier]
   [law.stellar :as law]
   [law.stellar.schema :as schema]))

(def ^:private softening
  "A softening length far below 1 AU — the same fixture convention as
   `domain.orbital-stability-test`: well inside the regime where
   `softened-circular-speed` and plain Kepler agree." 1.0e7)

(defn- world-with-star-and-candidate
  "A world with a settled `:star` and one fully-classified, eligible
   `:planet` candidate at 1 AU on a circular orbit — everything
   `handoff-system` needs, with Phases 1-3's components already set (as they
   would be one Jacobi tick after `classification-system` ran)."
  []
  (let [base        (ecs/empty-world)
        [w star]    (ecs/spawn base)
        [w planet]  (ecs/spawn w)
        v-circ      (law/softened-circular-speed law/solar-mass law/au softening)
        w (-> w
              (ecs/put-components
               star
               {c/matter-state :star
                c/mass         law/solar-mass
                c/radius       law/solar-radius
                c/luminosity   law/solar-luminosity
                c/position     [0.0 0.0 0.0]
                c/velocity     [0.0 0.0 0.0]
                c/composition  {:H 0.71 :He 0.27 :metals 0.02}
                c/temperature  5778.0})
              (ecs/put-components
               planet
               {c/matter-state      :planet
                c/mass               law/earth-mass
                c/radius             6.371e6
                c/position           [law/au 0.0 0.0]
                c/velocity           [0.0 v-circ 0.0]
                c/composition        {:Fe 0.32 :Ni 0.02 :Si 0.30 :Mg 0.20
                                      :O 0.10 :H 0.05 :He 0.01}
                c/material-class     :rocky
                c/thermal-band       :temperate
                c/orbit-stable       true
                c/atmosphere-class   :thin
                c/retained-species   #{:N2}
                c/angular-momentum   [0.0 0.0 1.0e30]
                c/rotation-axis      [0.0 0.0 1.0]
                c/oblateness         0.95
                c/spin               [0.0 0.0 7.29e-5]
                c/b-field            [0.0 0.0 1.0e-5]}))]
    {:world w :star star :planet planet}))

;; --- handoff-system (fan-out gate) -------------------------------------------

(deftest handoff-emits-when-star-and-planet-exist
  (testing "handoff-system writes a :planet-candidate when the star + candidate
            criteria hold, and the serial ledger step then appends
            :event/phase0-handoff"
    (let [{:keys [world planet]} (world-with-star-and-candidate)
          sys   (classifier/handoff-system)
          ws    ((:run sys) world)]
      (is (= :handoff (:id sys)))
      (is (= #{c/planet-candidate} (:writes sys)))
      (is (contains? (get ws c/planet-candidate) planet)
          "the eligible planet is written into the c/planet-candidate write-set")
      (let [world' (-> world
                       (ecs/put-component planet c/planet-candidate
                                          (get-in ws [c/planet-candidate planet]))
                       (event/with-ledger)
                       (assoc :tick 0))
            world'' (genesis/emit-handoff-event world')]
        (is (seq (event/events-of-kind world'' :event/phase0-handoff))
            "the ledger gained a :event/phase0-handoff event")))))

(deftest handoff-record-contains-required-keys
  (testing "the built :planet-candidate record satisfies the full parent §5
            schema — every required key is present with the right shape"
    (let [{:keys [world star planet]} (world-with-star-and-candidate)
          star-map (classifier/central-star world)
          record   (classifier/build-candidate-record world star-map planet)]
      (is (true? (schema/planet-candidate? record))
          (str "record failed schema: " record))
       (is (= (set (keys record))
              #{:planet-id :star-id :material-class :thermal-band
                :equilibrium-temperature :semi-major-axis :eccentricity
                :orbit-stable? :atmosphere-class :retained-species
                :volatile-budget-kg :differentiated-layers
                :bulk-composition :angular-momentum :rotation-axis
                :oblateness :surface-gravity :core-dynamo? :magnetic-field
                :formation-events}))
      (is (= planet (:planet-id record)))
      (is (= star (:star-id record))))))

;; --- sterile ending emits nothing --------------------------------------------

(deftest sterile-ending-does-not-emit-handoff
  (testing "a world with no candidate planets never gets a :planet-candidate,
            never emits :event/phase0-handoff, and its ending is :sterile"
    (let [w (assoc (genesis/create-world {:gas-count 20})
                   :arc/current :arc/genesis-planets-formed)
          sys (classifier/handoff-system)
          ws  ((:run sys) w)]
      (is (empty? (get ws c/planet-candidate {}))
          "no candidate exists yet, so handoff-system writes nothing")
      (is (empty? (event/events-of-kind w :event/phase0-handoff)))
      (is (= :sterile (:type (arc/genesis-ending w)))))))
