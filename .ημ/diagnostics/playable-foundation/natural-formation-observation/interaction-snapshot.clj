;; Read-only state at the native interaction boundary.
(require '[domain.player.state :as interaction-player]
         '[domain.ecs.core :as interaction-ecs]
         '[domain.ecs.components :as interaction-c]
         '[domain.narrowing :as interaction-narrowing])
(let [service @infra.dev.window/service-state
      world @(:world service)
      observer (interaction-player/observer-entity world)
      selection (:selection @(:config service))
      selected-position (when selection (interaction-ecs/get-component world selection interaction-c/position))
      candidate-eids (interaction-ecs/entities-with world interaction-c/planet-candidate)
      columns [interaction-c/binding interaction-c/binding-scar
               interaction-c/commitment-state interaction-c/palette interaction-c/time-lock
               interaction-c/field-zone interaction-c/attention-shell
               interaction-c/voxel-field interaction-c/voxel-band
               interaction-c/voxel-edit-queue interaction-c/voxel-edit-diffs
               interaction-c/voxel-field-diffs interaction-c/voxel-sculpt-request
               interaction-c/ecology interaction-c/civilization]]
  {:wall-ms (System/currentTimeMillis)
   :tick (:tick world) :sim-time (:genesis/sim-time world) :arc (:arc/current world)
   :observer-eid observer
   :observer (interaction-player/get-observer world)
   :observer-position (interaction-ecs/get-component world observer interaction-c/position)
   :observer-velocity (interaction-ecs/get-component world observer interaction-c/velocity)
   :selected-position selected-position
   :selected-velocity (when selection (interaction-ecs/get-component world selection interaction-c/velocity))
   :focus-overlap? (when selected-position
                     (interaction-narrowing/focus-overlap?
                      (:focus-position (interaction-player/get-observer world))
                      selected-position law.narrowing/world-focus-radius))
   :deepest-binding (interaction-narrowing/deepest-binding world)
   :candidate-readiness (into {} (map #(vector % (interaction-narrowing/ready-to-commit? world %))) candidate-eids)
   :columns (select-keys (:components world) columns)
   :regions (:regions world)
   :region-keys (filterv #(re-find #"region|narrow|voxel|focus|commit" (str %)) (keys world))
   :window (select-keys @(:config service) [:selection :mode :follow-eid :ui/active-domain :ui/cursor-free?])
   :ledger-events (mapv #(into {} %) (get-in world [:ledger :events]))})
