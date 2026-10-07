;; Verify/restore diagnostic-owned callables and original camera/view fields.
;; No GL or direct simulation writes; normal follow effects cannot be undone.
(let [control @(ns-resolve 'user 'truth-follow-trail-observer)
      {:keys [state guard restore! scene-var original-scene config-atom
              body-key-present? original-bodies]} control]
  (locking guard
    (when (:active? @state) (restore! :operator-restored))
    (assert (identical? @scene-var original-scene) "Scene root not restored")
    (assert (= body-key-present? (contains? @config-atom :bodies-fn)) "Projection key presence changed")
    (assert (identical? (:bodies-fn @config-atom infra.render/bodies-from-world) original-bodies)
            "Projection callable not restored")
    (assert (:camera-restored? @state) "Camera restoration not recorded")
    (assert (:view-fields-restored? @state) "View field restoration not recorded")
    {:restored true :state @state}))
