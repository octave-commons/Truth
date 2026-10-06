;; Read-only production admission audit of one immutable published world.
(require '[domain.ecs.core :as ee]
         '[domain.ecs.components :as ec]
         '[domain.player :as ep]
         '[domain.stellar.classifier.planet :as epl]
         '[domain.stellar.classifier.candidate :as eca]
         '[domain.narrowing :as en]
         '[shape.spatial :as esp])
(let [service @infra.dev.window/service-state
      w @(:world service)
      g #(ee/get-component w %1 %2)
      stars (epl/stellar-bodies w)
      oid (ep/observer-entity w)]
  {:wall-ms (System/currentTimeMillis) :tick (:tick w) :sim-dt (:sim/dt w)
   :arc (:arc/current w) :fixture? (:demo/fixture? w)
   :states (frequencies (vals (get-in w [:components ec/matter-state])))
   :binding (g oid ec/binding) :palette (g oid ec/palette)
   :observer (ep/get-observer w)
   :thrust (:player/thrust w)
   :camera (into {} @(:camera service))
   :config (select-keys @(:config service) [:mode :selection :follow-eid :ui/active-domain :ui/cursor-free?])
   :service-error (:error service) :ui-error (:ui/error-state @(:config service))
   :production-handoff-write-set ((:run (eca/handoff-system)) w)
   :bodies
   (mapv (fn [eid]
           (let [parent (epl/dominant-attractor w eid stars)
                 candidate (g eid ec/planet-candidate)
                 stored-star (when-let [sid (:star-id candidate)]
                               (@#'epl/star-record w sid))]
             {:eid eid :matter-state (g eid ec/matter-state)
              :material-class (g eid ec/material-class)
              :orbit-stable (g eid ec/orbit-stable)
              :atmosphere-class (g eid ec/atmosphere-class)
              :mass (g eid ec/mass) :position (g eid ec/position)
              :velocity (g eid ec/velocity)
              :current-parent parent
              :current-orbit (when parent (@#'eca/candidate-orbit-elements w parent eid))
              :per-body-eligible? (when parent (@#'eca/eligible-candidate? w parent eid))
              :stored-candidate candidate
              :stored-star stored-star
              :current-orbit-around-stored-star (when stored-star (@#'eca/candidate-orbit-elements w stored-star eid))
              :ready-to-commit? (boolean (en/ready-to-commit? w eid))
              :distance-to-spark-au (when-let [p (g eid ec/position)]
                                      (/ (esp/dist p (g oid ec/position)) 1.495978707e11))}))
         (sort (ee/entities-with w ec/material-class)))})
