;; Diagnostic lifecycle recovery only. No world reset, replacement or mutation.
(let [old @infra.dev.window/service-state
      world-atom (:world old)
      camera @(:camera old)
      config @(:config old)
      options (assoc (select-keys config [:width :height :subdivisions :tick-fn :bodies-fn
                                          :volumetric? :volume-res :volume-config
                                          :sim-frame-interval :on-step]) :camera camera)
      before {:world-identity (System/identityHashCode world-atom)
              :tick (:tick @world-atom) :window (:window old)
              :config (select-keys config [:mode :selection :follow-eid :focus-offset
                                            :ui/active-domain :ui/cursor-free?])
              :camera camera}]
  (infra.dev.window/stop!)
  (assert (not (.isAlive (:thread old))) "Old render thread still alive")
  (assert (not (.isAlive (:sim-thread old))) "Old sim thread still alive: refuse second writer")
  (let [stopped-tick (:tick @world-atom)
        old-shaders @infra.render.shader/program-cache]
    ;; Old unshared context owns these IDs; do not delete them in a new context.
    ;; The leaked old window/context remains for explicit later cleanup.
    (reset! infra.render.shader/program-cache {})
    (infra.render.volume/reset-volume-cache!)
    (infra.dev.window/start! world-atom options)
    (let [new @infra.dev.window/service-state]
      (assert (identical? world-atom (:world new)))
      (assert (<= stopped-tick (:tick @(:world new))))
      (intern 'user 'truth-native-recovery {:old old :before before :stopped-tick stopped-tick})
      {:recovery :existing-stop-start-same-world-atom
       :before before :stopped-tick stopped-tick
       :old-workers-alive? [(.isAlive (:thread old)) (.isAlive (:sim-thread old))]
       :discarded-stale-shaders old-shaders :volume-cache-discarded? true
       :new-world-identity (System/identityHashCode (:world new))
       :new-tick (:tick @(:world new))
       :new-workers-alive? [(.isAlive (:thread new)) (.isAlive (:sim-thread new))]
       :new-config (select-keys @(:config new) [:mode :selection :follow-eid :focus-offset
                                               :ui/active-domain :ui/cursor-free?])
       :new-camera @(:camera new)})))
