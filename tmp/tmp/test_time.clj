(ns tmp.test-time
  (:require
   [clojure.java.io :as io]
   [clojure.string :as str]
   [clojure.test :as t]))

(defn- ns-from-file
  [f]
  (let [rel (str/replace (.getPath f) (str (.getPath (io/file "test")) java.io.File/separator) "")]
    (when (str/ends-with? rel ".clj")
      (-> rel
          (str/replace #"\.clj$" "")
          (str/replace (re-pattern java.io.File/separator) ".")
          (str/replace #"_" "-")))))

(defn- run-one
  [n]
  (let [start (System/nanoTime)
        ret   (t/test-ns (find-ns (symbol n)))
        ms    (/ (- (System/nanoTime) start) 1e6)]
    (println (format "%-50s %9.1f ms  tests=%4d pass=%5d fail=%d error=%d"
                     n ms (:test ret 0) (:pass ret 0) (:fail ret 0) (:error ret 0)))
    [n ms ret]))

(defn -main []
  (let [files (sort-by #(.getPath %) (filter #(.isFile %) (file-seq (io/file "test"))))
        nss   (keep ns-from-file files)
        _     (doseq [n nss] (require (symbol n)))
        times (doall (map run-one nss))
        total (reduce (fn [sum [_ ms _]] (+ sum ms)) 0 times)]
    (println (format "\nTotal test-running time: %.1f ms (%.2f s)" total (/ total 1000.0)))
    (println "\nSlowest 15 namespaces:")
    (doseq [[n ms _ret] (take 15 (sort-by second #(compare %2 %1) times))]
      (println (format "  %-50s %9.1f ms" n ms)))
    (shutdown-agents)))
