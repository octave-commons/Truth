;; Root-operated diagnostic cleanup. No world, queue, camera or physics changes.
;; Refuse to overwrite another owner. Restore exact tick-key presence/value and
;; Var roots, while retaining unrelated live configuration changes.
(let [record-var (ns-resolve 'user 'truth-focus-cadence-native-proof)
      _ (assert record-var "No native proof installation record")
      {:keys [state guard last-follow service config-atom focus-var frame-var
              original-focus original-frame tick-present? tick-value
              focus-wrapper frame-wrapper tick-wrapper restore-tick]} @record-var]
  (assert (= :installed (:installation @state)) "No active successful installation")
  (assert (identical? @focus-var focus-wrapper) "Focus root now belongs to another owner")
  (assert (identical? @frame-var frame-wrapper) "Frame root now belongs to another owner")
  (assert (and (contains? @config-atom :tick-fn)
               (identical? (:tick-fn @config-atom) tick-wrapper))
          "Tick function now belongs to another owner")
  ;; Freeze observations first. Already captured wrappers still call their
  ;; originals unchanged, but cannot append after this closed snapshot.
  (locking guard
    (let [s @state]
      (swap! state assoc :closed? true :closed-wall-ms (System/currentTimeMillis)
             :sim-completion-rows-unobserved-at-close
             (- (:sim-calls-started s) (:sim-calls-completed s) (:sim-calls-failed s))
             :prepared-return-discarded-at-close? (some? @last-follow))
      (reset! last-follow nil)))
  (swap! config-atom
         (fn [cfg]
           (assert (identical? (:tick-fn cfg) tick-wrapper) "Concurrent tick-hook change")
           (restore-tick cfg)))
  (alter-var-root frame-var
                  (fn [f]
                    (assert (identical? f frame-wrapper) "Concurrent frame-hook change")
                    original-frame))
  (alter-var-root focus-var
                  (fn [f]
                    (assert (identical? f focus-wrapper) "Concurrent focus-hook change")
                    original-focus))
  (assert (identical? @frame-var original-frame))
  (assert (identical? @focus-var original-focus))
  (assert (= tick-present? (contains? @config-atom :tick-fn)))
  (assert (identical? tick-value (:tick-fn @config-atom)))
  (swap! state assoc :installation :restored)
  {:restored true
   :world-atom-identity (System/identityHashCode (:world service))
   :tick (:tick @(:world service))
   :tick-key-presence-restored? (= tick-present? (contains? @config-atom :tick-fn))
   :focus-root-restored? (identical? @focus-var original-focus)
   :frame-root-restored? (identical? @frame-var original-frame)
   :final-counters @state})
