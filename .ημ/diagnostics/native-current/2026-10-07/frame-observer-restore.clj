;; Root-operated fallback/verification. No GL calls and no world/input writes.
;; The callback normally restores itself after one capture or its first error.
(let [record-var (ns-resolve 'user 'truth-native-current-frame-observer)
      _ (assert record-var "No hardware frame observer installation")
      {:keys [state guard restore! scene-var original-scene]} @record-var]
  (locking guard
    (when (:active? @state) (restore! :operator-restored))
    (assert (identical? @scene-var original-scene) "Scene root is not restored")
    (assert (:original-root-restored? @state) "Missing restoration evidence")
    {:restored true :result @state}))
