;; Read one immutable published world from the existing demo service.
;; No tick, atom mutation, camera change, alternate classifier, or model.
(require '[domain.ecs.core :as observe-ecs]
         '[domain.ecs.components :as observe-c]
         '[domain.stellar.classifier.planet :as observe-planet]
         '[domain.stellar.classifier.candidate :as observe-candidate]
         '[domain.stellar.disc :as observe-disc]
         '[shape.spatial :as observe-sp]
         '[domain.planet-formation.seed :as observe-seed])

(let [service @infra.dev.window/service-state
      world @(:world service)
      component #(observe-ecs/get-component world %1 %2)
      stars (observe-planet/stellar-bodies world)
      matter (get-in world [:components observe-c/matter-state])
      disk-entries (filter (fn [[_ mass]] (pos? (double mass)))
                           (get-in world [:components observe-c/disk-mass]))
      planet-eids (filter #(contains? #{:planet :gas-giant :planetesimal} (val %)) matter)
      candidate-eids (set (concat (map key planet-eids)
                                  (observe-ecs/entities-with world observe-c/material-class)))
      events (get-in world [:ledger :events])]
  {:wall-ms (System/currentTimeMillis)
   :observation-segment :player-observation
   :interaction-start-wall-ms 1791311817000
   :service-error (:error service)
   :ui-error (:ui/error-state @(:config service))
   :tick (:tick world)
   :arc (:arc/current world)
   :fixture? (:demo/fixture? world)
   :sim-time (:genesis/sim-time world)
   :ignition-time (:genesis/star-ignition-time world)
   :disk-age (- (:genesis/sim-time world) (:genesis/star-ignition-time world))
   :disk-maturity (:genesis/disk-maturity world)
   :sim-dt (:sim/dt world)
   :active? (:genesis/active world)
   :states (frequencies (vals matter))
   :positive-disk-count (count disk-entries)
   :disc-tag-count (count (get-in world [:components observe-c/disc-tag]))
   :disks
   (mapv (fn [[eid mass]]
           (let [context (@#'observe-seed/seed-context world eid)
                 seeds (observe-seed/planet-seeds world eid)]
             {:eid eid :matter-state (component eid observe-c/matter-state)
              :mass (component eid observe-c/mass)
              :radius (component eid observe-c/radius)
              :luminosity (component eid observe-c/luminosity)
              :disk-mass mass
              :disk-angular-mom (component eid observe-c/disk-angular-mom)
              :disk-radius (observe-disc/disk-radius
                            (/ (observe-sp/len (component eid observe-c/disk-angular-mom)) mass)
                            (component eid observe-c/mass))
              :disk-regime (component eid observe-c/disk-regime)
              :planets-seeded (component eid observe-c/planets-seeded)
              :fragments-spawned (component eid observe-c/disk-fragments-spawned)
              :seed-context (when context (select-keys context [:disk-age :maturity :M-star :L-star :disk-m :Z :r-in :r-out]))
              :seed-result? (some? seeds)
              :seed-spec-count (count (:spawns seeds))}))
         disk-entries)
   :candidate-input-count (count candidate-eids)
   :planet-count (count planet-eids)
   :candidate-bodies
   (mapv (fn [eid]
           (let [parent (observe-planet/dominant-attractor world eid stars)
                 material (component eid observe-c/material-class)]
             {:eid eid :matter-state (component eid observe-c/matter-state)
              :mass (component eid observe-c/mass)
              :position (component eid observe-c/position)
              :velocity (component eid observe-c/velocity)
              :material-class material
              :thermal-band (component eid observe-c/thermal-band)
              :orbit-stable (component eid observe-c/orbit-stable)
              :atmosphere-class (component eid observe-c/atmosphere-class)
              :ecology (component eid observe-c/ecology)
              :commitment-state (component eid observe-c/commitment-state)
              :parent parent
              :orbit-elements (when parent (@#'observe-candidate/candidate-orbit-elements world parent eid))
              :equilibrium-temperature (when (and parent material)
                                         (observe-planet/classify-body-equilibrium-temp world parent eid material))
              :eligible-candidate? (when parent (@#'observe-candidate/eligible-candidate? world parent eid))
              :candidate-component (component eid observe-c/planet-candidate)}))
         (sort candidate-eids))
   :handoff-write-set ((:run (observe-candidate/handoff-system)) world)
   :ledger-count (count events)
   :ledger-kinds (frequencies (map :kind events))
   :ledger-events (mapv #(into {} %) events)})
