;; Read-only published-world navigation evidence. Never advances or mutates world.
(require '[domain.ecs.core :as ae]
         '[domain.ecs.components :as ac]
         '[domain.player :as ap]
         '[domain.stellar.classifier.planet :as apl]
         '[domain.stellar.classifier.candidate :as aca]
         '[domain.narrowing :as an]
         '[law.narrowing :as aln]
         '[shape.spatial :as asp]
         '[domain.naming :as name])
(let [service @infra.dev.window/service-state
      w @(:world service)
      oid (ap/observer-entity w)
      obs (ap/get-observer w)
      g #(ae/get-component w %1 %2)
      p (g oid ac/position)
      v (g oid ac/velocity)
      stars (apl/stellar-bodies w)]
  {:wall-ms (System/currentTimeMillis) :tick (:tick w) :sim-dt (:sim/dt w)
   :fixture? (:demo/fixture? w) :arc (:arc/current w)
   :states (frequencies (vals (get-in w [:components ac/matter-state])))
   :spark-position p :spark-velocity v :thrust (:player/thrust w)
   :focus-position (:focus-position obs) :focus-intensity (:focus-intensity obs)
   :binding (g oid ac/binding) :palette (g oid ac/palette)
   :flight-displacement (:genesis/spark-flight-displacement w ap/default-displacement-per-tick)
   :damping-retention (:genesis/spark-damping-retention w ap/default-damping-retention)
   :camera (into {} @(:camera service))
   :config (select-keys @(:config service) [:mode :selection :follow-eid :ui/active-domain :ui/cursor-free? :focus-offset])
   :service-error (:error service) :ui-error (:ui/error-state @(:config service))
   :planets
   (->> (ae/entities-with w ac/matter-state)
        (filter #(contains? #{:planet :gas-giant} (g % ac/matter-state)))
        (map (fn [eid]
               (let [target (g eid ac/position)
                     d (asp/v- target p)
                     r (asp/len d)
                     parent (apl/dominant-attractor w eid stars)]
                 {:eid eid :name (name/body-name eid)
                  :position target :velocity (g eid ac/velocity)
                  :relative-position d :distance-au (/ r 1.495978707e11)
                  :heading-yaw-deg (* (/ 180.0 Math/PI) (Math/atan2 (- (nth d 1)) (- (nth d 0))))
                  :heading-pitch-deg (* (/ 180.0 Math/PI) (Math/asin (- (/ (nth d 2) r))))
                  :candidate (g eid ac/planet-candidate)
                  :currently-eligible? (when parent (@#'aca/eligible-candidate? w parent eid))
                  :focus-radius aln/world-focus-radius
                  :focus-overlap? (an/focus-overlap? (:focus-position obs) target aln/world-focus-radius)
                  :ecology (g eid ac/ecology) :commitment (g eid ac/commitment-state)})))
        (sort-by :distance-au) vec)})
