(let [d @user/truth-cadence-reload s (:old d)]
  (assert (true? (:ok (deref (:teardown d) 10000 {:ok false}))) "Teardown incomplete")
  (infra.dev.window.lifecycle/stop!)
  (assert (not (.isAlive (:thread s))) "Old renderer still alive")
  (assert (not (.isAlive (:sim-thread s))) "Old sim still alive; refuse second writer")
  (assert (nil? @infra.dev.window.lifecycle/service-state))
  (swap! user/truth-cadence-reload assoc
         :stopped-world @(:world s) :stopped-camera @(:camera s)
         :stopped-config @(:config s) :pending-intents (.size (:queue d)))
  ;; Cache IDs belong to the destroyed contexts; never delete them in a new one.
  (reset! infra.render.shader/program-cache {})
  (infra.render.volume/reset-volume-cache!)
  (when-let [asset-ns (find-ns 'infra.render.asset)]
    (doseq [name '[mesh-cache texture-cache]]
      (when-let [v (ns-resolve asset-ns name)] (reset! @v {}))))
  ;; GPU slots and applied-native-cursor bookkeeping are not user settings.
  (swap! (:config s) #(-> %
                         (assoc :body-program nil :line-program nil :sprite-program nil
                                :hud-program nil :volume-program nil :particle-program nil
                                :mesh nil :cube-mesh nil)
                         (dissoc :ui/applied-cursor)))
  (load-file "/home/err/spaces/foresight/.worktrees/truth-focus-cadence/src/infra/dev/window/loop.clj")
  (swap! user/truth-cadence-reload assoc
         :stage :stopped-loaded
         :new-sim-loop @#'infra.dev.window.loop/sim-loop
         :new-drain-intents @(ns-resolve 'infra.dev.window.loop 'drain-intents))
  {:stopped true :tick (:tick @(:world s))
   :world-identity (System/identityHashCode (:world s))
   :pending-intents (.size (:queue d))})
