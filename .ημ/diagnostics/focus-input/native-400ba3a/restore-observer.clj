(let [{:keys [target original wrapper state]} @(ns-resolve 'user 'truth-native-observer)]
  (assert (identical? @target wrapper) "Refuse to overwrite another render callable")
  (alter-var-root target (constantly original))
  {:restored true :final-counters @state})
