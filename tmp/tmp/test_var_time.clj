(ns tmp.test-var-time
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

(defn- test-vars-in-ns
  [n]
  (filter #(:test (meta %)) (vals (ns-interns (find-ns (symbol n))))))

(defn- run-var
  [v]
  (let [start (System/nanoTime)
        ret   (t/test-vars [v])
        ms    (/ (- (System/nanoTime) start) 1e6)
        ns    (str (ns-name (:ns (meta v))))
        name  (str (:name (meta v)))]
    (println (format "%-52s %-45s %9.1f ms"
                     ns name ms))
    [ns name ms ret]))

(defn -main []
  (let [files (sort-by #(.getPath %) (filter #(.isFile %) (file-seq (io/file "test"))))
        nss   (keep ns-from-file files)
        _     (doseq [n nss] (require (symbol n)))
        vars  (doall (mapcat test-vars-in-ns nss))
        times (doall (map run-var vars))
        total (reduce (fn [sum [_ _ ms _]] (+ sum ms)) 0 times)]
    (println (format "\nTotal test-var time: %.1f ms (%.2f s)" total (/ total 1000.0)))
    (println "\nSlowest 25 individual deftests:")
    (doseq [[ns name ms _] (take 25 (sort-by #(nth % 2) #(compare %2 %1) times))]
      (println (format "  %-52s %-45s %9.1f ms" ns name ms)))
    (println "\nSlowest namespaces (sum of vars):")
    (doseq [[ns ms] (take 15 (sort-by val #(compare %2 %1) (reduce (fn [m [n _ ms _]] (update m n (fnil + 0) ms)) {} times)))]
      (println (format "  %-52s %9.1f ms" ns ms)))
    (shutdown-agents)))
