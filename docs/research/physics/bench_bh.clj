(ns bench-bh
  "Quick benchmark of the existing domain.gravity.barnes-hut kernel.
   Run with:
     clj -Sdeps '{:paths [\"src\" \"docs/research/physics\"]}' -M -m bench-bh"
  (:require
   [domain.gravity.barnes-hut :as bh])
  (:import
   [java.util Random]))

(defn rand-bodies
  [n ^Random rng]
  (mapv (fn [id]
          {:id id
           :mass 1.0
           :position [(+ 0.0 (.nextGaussian rng))
                      (+ 0.0 (.nextGaussian rng))
                      (+ 0.0 (.nextGaussian rng))]})
        (range n)))

(defn bench-tree-build
  [bodies]
  (let [t0 (System/nanoTime)
        tree (bh/build-tree bodies)
        t1 (System/nanoTime)]
    [tree (/ (- t1 t0) 1e6)]))

(defn bench-accel
  [G theta softening tree body]
  (let [t0 (System/nanoTime)
        acc (bh/acceleration {:G G :theta theta :softening softening :tree tree :body body})
        t1 (System/nanoTime)]
    [acc (/ (- t1 t0) 1e6)]))

(defn -main []
  (let [n 500
        rng (Random. 42)
        bodies (rand-bodies n rng)
        thetas [0.3 0.5 0.7 1.0]
        ;; Warm up
        _ (dotimes [_ 10]
            (bh/build-tree bodies)
            (let [tree (bh/build-tree bodies)]
              (doseq [b bodies] (bh/acceleration {:G 1.0 :theta 0.5 :softening 1e-3 :tree tree :body b}))))
        [tree t-build] (bench-tree-build bodies)]
    (println (format "N=%d" n))
    (println (format "Tree build: %.3f ms" t-build))
    (println)
    (println "| theta | total per tick (ms) | per-body mean (ms) |")
    (println "|------:|--------------------:|-------------------:|")
    (doseq [theta thetas]
      (let [tree (bh/build-tree bodies)
            times (mapv (fn [b] (second (bench-accel 1.0 theta 1e-3 tree b))) bodies)
            total (reduce + times)
            mean (/ total n)]
        (println (format "| %.2f | %.3f | %.4f |" theta total mean))))))
