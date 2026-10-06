(let [s @infra.dev.window.lifecycle/service-state
      old-recovery (some-> (ns-resolve 'user 'truth-native-recovery) deref)
      historical (:old old-recovery)
      observer (some-> (ns-resolve 'user 'truth-native-observer) deref)
      frame-var (ns-resolve 'infra.dev.window.loop 'render-frame-once)]
  (assert s "No retained service")
  (assert (= 1675959387 (System/identityHashCode (:world s))) "Wrong world")
  (assert (and (.isAlive (:thread s)) (.isAlive (:sim-thread s))))
  (assert (nil? (:error s)))
  (assert (nil? (:ui/error-state @(:config s))))
  (when observer
    (assert (identical? @(:target observer) (:original observer))
            "Prior frame observer is still installed"))
  (when historical
    (assert (and (not (.isAlive (:thread historical)))
                 (not (.isAlive (:sim-thread historical))))
            "Historical window still has a live worker")
    (assert (not= (:window historical) (:window s))))
  (assert (nil? (ns-resolve 'user 'truth-cadence-reload)) "Already staged")
  (intern 'user 'truth-cadence-reload
          (atom {:stage :prepared :old s :historical historical
                 ;; Capture before load-file re-evaluates the IntentAtom type.
                 :queue (.-queue ^infra.dev.window.loop.IntentAtom (:world-intents s))
                 :old-sim-loop @#'infra.dev.window.loop/sim-loop
                 :old-drain-intents @(ns-resolve 'infra.dev.window.loop 'drain-intents)
                 :frame-var frame-var :original-frame @frame-var
                 :teardown (promise) :claimed (atom false)}))
  {:staged true :tick (:tick @(:world s)) :window (:window s)
   :world-identity (System/identityHashCode (:world s))})
