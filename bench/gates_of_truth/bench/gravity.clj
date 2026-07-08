(ns gates-of-truth.bench.gravity
  "Barnes-Hut gravity benchmarks.

   Tests the two dominant costs in the gravity pipeline:
   1. Tree construction: O(N log N) — builds the spatial index
   2. Acceleration computation: O(N log N) per body — the tree walk

   Also measures the per-body cost to identify if the tree walk scales
   sub-linearly (good) or degrades (bad θ tuning)."
  (:require
   [domain.gravity.barnes-hut :as bh]
   [shape.spatial             :as sp]))

;; ---------------------------------------------------------------------------
;; Test data
;; ---------------------------------------------------------------------------

(defn- make-bodies
  "Generate N random bodies in a sphere of given extent."
  [n extent]
  (let [rng (java.util.Random. 42)]
    (mapv (fn [i]
            (let [r  (* extent (Math/pow (.nextDouble rng) 0.33))
                  th (* 2.0 Math/PI (.nextDouble rng))
                  ph (Math/acos (- (* 2.0 (.nextDouble rng)) 1.0))]
              {:id       i
               :position (sp/vec3 (* r (Math/sin ph) (Math/cos th))
                                  (* r (Math/sin ph) (Math/sin th))
                                  (* r (Math/cos ph)))
               :mass     (* 1.989e30 (+ 0.5 (.nextDouble rng)))
               :radius   (* 6.957e8 (+ 0.5 (.nextDouble rng)))
               :kind     :body/gas}))
          (range n))))

(defn- make-clustered-bodies
  "Bodies in a realistic clustered distribution (dense core + sparse halo).
   Most bodies near center, a few far out — tests tree balancing."
  [n extent]
  (let [rng (java.util.Random. 42)]
    (mapv (fn [i]
            (let [r  (* extent (if (< (.nextDouble rng) 0.8)
                                 ;; 80% in inner 20%
                                 (* 0.2 (Math/pow (.nextDouble rng) 0.33))
                                 ;; 20% in outer 80%
                                 (+ 0.2 (* 0.8 (Math/pow (.nextDouble rng) 0.33)))))
                  th (* 2.0 Math/PI (.nextDouble rng))
                  ph (Math/acos (- (* 2.0 (.nextDouble rng)) 1.0))]
              {:id       i
               :position (sp/vec3 (* r (Math/sin ph) (Math/cos th))
                                  (* r (Math/sin ph) (Math/sin th))
                                  (* r (Math/cos ph)))
               :mass     (* 1.989e30 (+ 0.5 (* 0.5 (.nextDouble rng))))
               :radius   (* 6.957e8 (+ 0.5 (.nextDouble rng)))
               :kind     :body/gas}))
          (range n))))

;; ---------------------------------------------------------------------------
;; Benchmarks
;; ---------------------------------------------------------------------------

(defn run [quick-bench full-bench]
  (let [bodies-100   (make-bodies 100 2.0e16)
        bodies-500   (make-bodies 500 2.0e16)
        bodies-1000  (make-bodies 1000 2.0e16)
        bodies-2000  (make-bodies 2000 2.0e16)
        clustered-1000 (make-clustered-bodies 1000 2.0e16)]

    ;; --- Tree construction ---
    (quick-bench "build-tree (100 bodies)"
      (fn [] (bh/build-tree bodies-100)))

    (quick-bench "build-tree (500 bodies)"
      (fn [] (bh/build-tree bodies-500)))

    (quick-bench "build-tree (1000 bodies)"
      (fn [] (bh/build-tree bodies-1000)))

    (quick-bench "build-tree (2000 bodies)"
      (fn [] (bh/build-tree bodies-2000)))

    (quick-bench "build-tree (1000 clustered)"
      (fn [] (bh/build-tree clustered-1000)))

    ;; --- Acceleration (single body) ---
    (let [tree-100  (bh/build-tree bodies-100)
          tree-500  (bh/build-tree bodies-500)
          tree-1000 (bh/build-tree bodies-1000)
          tree-2000 (bh/build-tree bodies-2000)
          tree-cl   (bh/build-tree clustered-1000)
          test-body (first bodies-1000)]

      (quick-bench "acceleration (1 body from 100, θ=0.5)"
        (fn [] (bh/acceleration {:G 6.674e-11 :theta 0.5 :softening 1.0e14 :tree tree-100 :body test-body})))

      (quick-bench "acceleration (1 body from 500, θ=0.5)"
        (fn [] (bh/acceleration {:G 6.674e-11 :theta 0.5 :softening 1.0e14 :tree tree-500 :body test-body})))

      (quick-bench "acceleration (1 body from 1000, θ=0.5)"
        (fn [] (bh/acceleration {:G 6.674e-11 :theta 0.5 :softening 1.0e14 :tree tree-1000 :body test-body})))

      (quick-bench "acceleration (1 body from 2000, θ=0.5)"
        (fn [] (bh/acceleration {:G 6.674e-11 :theta 0.5 :softening 1.0e14 :tree tree-2000 :body test-body})))

      (quick-bench "acceleration (1 body from 1000 clustered, θ=0.5)"
        (fn [] (bh/acceleration {:G 6.674e-11 :theta 0.5 :softening 1.0e14 :tree tree-cl :body test-body})))

      ;; --- θ sensitivity ---
      (println "\n  θ Sensitivity (1000 bodies, one acceleration):")
      (doseq [theta [0.3 0.5 0.7 1.0]]
        (quick-bench (format "  θ=%.1f" theta)
          (fn [] (bh/acceleration {:G 6.674e-11 :theta theta :softening 1.0e14 :tree tree-1000 :body test-body}))))

      ;; --- All-body acceleration (the real hot path) ---
      (quick-bench "all-body acceleration (100 bodies)"
        (fn []
          (mapv (fn [b] (bh/acceleration {:G 6.674e-11 :theta 0.5 :softening 1.0e14 :tree tree-100 :body b}))
                bodies-100)))

      (quick-bench "all-body acceleration (1000 bodies)"
        (fn []
          (mapv (fn [b] (bh/acceleration {:G 6.674e-11 :theta 0.5 :softening 1.0e14 :tree tree-1000 :body b}))
                bodies-1000)))

      ;; --- Cost breakdown ---
      (println "\n  Cost Breakdown (1000 bodies):")
      (println "    Tree build is a one-time cost per tick.")
      (println "    Acceleration is N× tree walks — dominates at large N.")
      (println "    Check if tree build / acceleration ratio changes with N."))))

;; Profile iterations
(defn profile-iterations []
  (let [bodies (make-bodies 1000 2.0e16)
        tree   (bh/build-tree bodies)]
    (doseq [b bodies]
      (bh/acceleration {:G 6.674e-11 :theta 0.5 :softening 1.0e14 :tree tree :body b}))))
