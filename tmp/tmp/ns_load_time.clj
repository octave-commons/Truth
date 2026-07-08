(ns tmp.ns-load-time
  (:require
   [clojure.java.io :as io]
   [clojure.string :as str]))

(defn- ns-from-file
  [f]
  (let [rel (str/replace (.getPath f) (str (.getPath (io/file "test")) java.io.File/separator) "")]
    (when (str/ends-with? rel ".clj")
      (-> rel
          (str/replace #"\.clj$" "")
          (str/replace (re-pattern java.io.File/separator) ".")
          (str/replace #"_" "-")))))

(defn- load-one
  [n]
  (let [start (System/nanoTime)
        _     (require (symbol n))
        ms    (/ (- (System/nanoTime) start) 1e6)]
    (println (format "%-52s %9.1f ms" n ms))
    [n ms]))

(defn -main []
  (let [files (sort-by #(.getPath %) (filter #(.isFile %) (file-seq (io/file "test"))))
        nss   (keep ns-from-file files)
        times (doall (map load-one nss))
        total (reduce (fn [sum [_ ms]] (+ sum ms)) 0 times)]
    (println (format "\nTotal require time: %.1f ms (%.2f s)" total (/ total 1000.0)))
    (println "\nSlowest 15 namespace loads:")
    (doseq [[n ms] (take 15 (sort-by second #(compare %2 %1) times))]
      (println (format "  %-52s %9.1f ms" n ms)))
    (shutdown-agents)))
