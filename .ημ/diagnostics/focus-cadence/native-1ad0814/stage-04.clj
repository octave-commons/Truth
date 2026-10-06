(let [d @user/truth-cadence-reload s (:old d)
      stop (atom false)
      make-threads @(ns-resolve 'infra.dev.window.lifecycle 'make-service-threads)
      launch @(ns-resolve 'infra.dev.window.lifecycle 'launch-service!)
      resource-keys [:body-program :line-program :sprite-program :hud-program
                     :volume-program :particle-program :mesh :cube-mesh
                     :ui/applied-cursor]]
  (assert (= :stopped-loaded (:stage d)))
  (assert (nil? @infra.dev.window.lifecycle/service-state))
  (assert (and (not (.isAlive (:thread s))) (not (.isAlive (:sim-thread s)))))
  (assert (identical? (:stopped-world d) @(:world s)) "World changed while stopped")
  (assert (= (:stopped-camera d) @(:camera s)))
  (assert (= (apply dissoc (:stopped-config d) resource-keys)
             (apply dissoc @(:config s) resource-keys)) "Host config drift")
  (assert (= (:pending-intents d) (.size (:queue d))) "Pending queue drift")
  (let [threads (make-threads (:world-intents s) (:camera s) (:config s)
                             stop (:world s) (:queue d))]
    (launch infra.dev.window.lifecycle/service-state threads (:world s)
            (:world-intents s) (:camera s) (:config s) stop)
    (let [new @infra.dev.window.lifecycle/service-state]
      (doseq [k [:world :camera :config :world-intents]]
        (assert (identical? (k s) (k new)) (str "Replaced " k)))
      (swap! user/truth-cadence-reload assoc :stage :restarted :new new)
      {:restarted true :world-identity (System/identityHashCode (:world new))
       :tick (:tick @(:world new))
       :old-workers-alive? [(.isAlive (:thread s)) (.isAlive (:sim-thread s))]
       :new-workers-alive? [(.isAlive (:thread new)) (.isAlive (:sim-thread new))]})))
