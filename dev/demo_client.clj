(ns demo-client
  "Small nREPL client for the isolated demo service; never targets the dev port."
  (:require [nrepl.core :as nrepl]))

(defn -main
  "Evaluate one supplied form on the demo server and fail on remote errors."
  [& [code]]
  (try
    (when (nil? code)
      (throw (IllegalArgumentException. "Usage: clojure -M:demo-client <form>")))
    (with-open [connection (nrepl/connect :host "127.0.0.1"
                                          :port (Integer/parseInt (or (System/getenv "TRUTH_DEMO_PORT") "7890")))]
      (let [responses (vec (nrepl/message (nrepl/client connection 40000)
                                          {:op "eval" :code code}))]
        (doseq [response responses]
          (when-let [out (:out response)] (print out))
          (when-let [value (:value response)] (println value))
          (when-let [err (:err response)] (binding [*out* *err*] (print err)))
          (when (some #{"eval-error" "error"} (:status response))
            (throw (ex-info "Demo evaluation failed" (select-keys response [:ex :root-ex :status])))))
        (when-not (some #(some #{"done"} (:status %)) responses)
          (throw (ex-info "Demo evaluation did not finish before timeout" {})))))
    (finally (shutdown-agents))))
