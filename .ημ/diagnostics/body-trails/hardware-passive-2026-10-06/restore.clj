;; Verify/restore diagnostic-owned callables only. No GL or simulation writes.
(let [control @(ns-resolve 'user 'truth-passive-trail-observer)
      {:keys [state guard restore! scene-var original-scene config-atom
              body-key-present? original-bodies]} control]
  (locking guard
    (when (:active? @state) (restore! :operator-restored))
    (assert (identical? @scene-var original-scene) "Scene root not restored")
    (assert (= body-key-present? (contains? @config-atom :bodies-fn)) "Projection key presence changed")
    (assert (identical? (:bodies-fn @config-atom infra.render/bodies-from-world) original-bodies)
            "Projection callable not restored")
    {:restored true :state @state}))
