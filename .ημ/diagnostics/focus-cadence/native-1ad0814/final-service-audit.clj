;; Read-only closure audit. No input, GL, world, lifecycle or config mutation.
(let [service @infra.dev.window.lifecycle/service-state
      reload-state @user/truth-cadence-reload
      proof user/truth-focus-cadence-native-proof
      config @(:config service)
      world @(:world service)
      observer (domain.player/get-observer world)
      checks {:same-world? (= 1675959387 (System/identityHashCode (:world service)))
              :retained-world? (identical? (:world (:old reload-state)) (:world service))
              :old-workers-stopped? (every? #(not (.isAlive ^Thread %))
                                           ((juxt :thread :sim-thread) (:old reload-state)))
              :new-workers-live? (every? #(.isAlive ^Thread %)
                                        ((juxt :thread :sim-thread) service))
              :new-loop-root? (identical? (:new-sim-loop reload-state)
                                         @#'infra.dev.window.loop/sim-loop)
              :focus-root-restored? (identical? (:original-focus proof) @(:focus-var proof))
              :frame-root-restored? (identical? (:original-frame proof) @(:frame-var proof))
              :tick-key-presence-restored? (= (:tick-present? proof) (contains? config :tick-fn))
              :tick-value-restored? (identical? (:tick-value proof) (:tick-fn config))
              :proof-closed? (:closed? @(:state proof))
              :proof-restored? (= :restored (:installation @(:state proof)))
              :service-error-free? (nil? (:error service))
              :ui-error-free? (nil? (:ui/error-state config))
              :neutral-thrust? (nil? (:player/thrust world))
              :manual? (= :manual (:mode config))
              :fine? (= 1e7 (:genesis/spark-flight-displacement config))
              :zero-offset? (= [0.0 0.0 0.0] (:focus-offset config))}]
  (assert (every? true? (vals checks)) (pr-str checks))
  {:wall-ms (System/currentTimeMillis)
   :world-atom-identity (System/identityHashCode (:world service))
   :tick (:tick world) :window (:window service) :checks checks
   :settings (select-keys config [:mode :focus-offset :ui/active-domain :ui/cursor-free?
                                 :genesis/spark-flight-displacement :genesis/spark-damping-retention])
   :observer (select-keys observer [:focus-position :focus-radius :focus-intensity :coherence])
   :position (domain.player/observer-position world)
   :camera (select-keys @(:camera service) [:target :distance :yaw :pitch])
   :pending-intents (.size (:queue reload-state))})
