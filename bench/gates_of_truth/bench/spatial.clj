(ns gates-of-truth.bench.spatial
  "Spatial index benchmarks.

   The spatial index (octree) is shared by gravity, collision, hydro, and EM.
   Tests:
   1. Index construction from different distributions
   2. Radius queries with varying radii
   3. Nearest-neighbor queries
   4. Query performance vs brute-force baseline"
  (:require
   [domain.spatial.index  :as idx]
   [domain.gravity.barnes-hut :as bh]
   [shape.spatial         :as sp]))

;; ---------------------------------------------------------------------------
;; Test data
;; ---------------------------------------------------------------------------

(defn- make-uniform-points
  "N points uniformly distributed in a cube."
  [n extent]
  (let [rng (java.util.Random. 42)]
    (mapv (fn [i]
            {:id       i
             :eid      i
             :position (sp/vec3 (* extent (- (* 2.0 (.nextDouble rng)) 1.0))
                                (* extent (- (* 2.0 (.nextDouble rng)) 1.0))
                                (* extent (- (* 2.0 (.nextDouble rng)) 1.0)))
             :mass     1.0e20})
          (range n))))

(defn- make-clustered-points
  "N points: 80% in inner 20% of extent, 20% in outer 80%."
  [n extent]
  (let [rng (java.util.Random. 42)]
    (mapv (fn [i]
            (let [inner? (< (.nextDouble rng) 0.8)
                  r (* extent (if inner?
                                (* 0.2 (Math/pow (.nextDouble rng) 0.33))
                                (+ 0.2 (* 0.8 (Math/pow (.nextDouble rng) 0.33)))))
                  th (* 2.0 Math/PI (.nextDouble rng))
                  ph (Math/acos (- (* 2.0 (.nextDouble rng)) 1.0))]
              {:id       i
               :eid      i
               :position (sp/vec3 (* r (Math/sin ph) (Math/cos th))
                                  (* r (Math/sin ph) (Math/sin th))
                                  (* r (Math/cos ph)))
               :mass     1.0e20}))
          (range n))))

(defn- brute-force-within-radius
  "O(N) neighbor search baseline."
  [items pos r]
  (let [r2 (* (double r) (double r))]
    (filterv (fn [item]
               (<= (sp/len2 (sp/v- pos (:position item))) r2))
             items)))

(defn- brute-force-nearest
  "O(N) nearest neighbor baseline."
  [items pos self-eid]
  (let [best (volatile! Double/POSITIVE_INFINITY)]
    (doseq [item items]
      (when (not= (:eid item) self-eid)
        (let [d (sp/dist pos (:position item))]
          (when (< d @best) (vreset! best d)))))
    @best))

;; ---------------------------------------------------------------------------
;; Benchmarks
;; ---------------------------------------------------------------------------

(defn run [quick-bench full-bench]
  (let [uniform-100   (make-uniform-points 100 1.0e14)
        uniform-500   (make-uniform-points 500 1.0e14)
        uniform-1000  (make-uniform-points 1000 1.0e14)
        uniform-5000  (make-uniform-points 5000 1.0e14)
        clustered-1000 (make-clustered-points 1000 1.0e14)]

    ;; --- Index construction ---
    (quick-bench "octree build (100 uniform)"
      (fn [] (idx/build uniform-100)))

    (quick-bench "octree build (500 uniform)"
      (fn [] (idx/build uniform-500)))

    (quick-bench "octree build (1000 uniform)"
      (fn [] (idx/build uniform-1000)))

    (quick-bench "octree build (5000 uniform)"
      (fn [] (idx/build uniform-5000)))

    (quick-bench "octree build (1000 clustered)"
      (fn [] (idx/build clustered-1000)))

    ;; --- Radius queries ---
    (let [tree-100  (idx/build uniform-100)
          tree-500  (idx/build uniform-500)
          tree-1000 (idx/build uniform-1000)
          tree-5000 (idx/build uniform-5000)
          tree-cl   (idx/build clustered-1000)
          q-point   (:position (first uniform-1000))
          ;; Different radii: small (few neighbors), medium, large (many)
          r-small   1.0e12
          r-medium  1.0e13
          r-large   1.0e14]

      ;; Small radius
      (quick-bench "within-radius r=1e12 (100 uniform)"
        (fn [] (idx/within-radius tree-100 q-point r-small)))

      (quick-bench "within-radius r=1e12 (1000 uniform)"
        (fn [] (idx/within-radius tree-1000 q-point r-small)))

      (quick-bench "within-radius r=1e12 (5000 uniform)"
        (fn [] (idx/within-radius tree-5000 q-point r-small)))

      ;; Medium radius
      (quick-bench "within-radius r=1e13 (100 uniform)"
        (fn [] (idx/within-radius tree-100 q-point r-medium)))

      (quick-bench "within-radius r=1e13 (1000 uniform)"
        (fn [] (idx/within-radius tree-1000 q-point r-medium)))

      (quick-bench "within-radius r=1e13 (5000 uniform)"
        (fn [] (idx/within-radius tree-5000 q-point r-medium)))

      ;; Large radius
      (quick-bench "within-radius r=1e14 (1000 uniform)"
        (fn [] (idx/within-radius tree-1000 q-point r-large)))

      ;; Clustered
      (quick-bench "within-radius r=1e13 (1000 clustered)"
        (fn [] (idx/within-radius tree-cl q-point r-medium)))

      ;; --- Octree vs brute-force ---
      (println "\n  Octree vs Brute-Force (1000 uniform, r=1e13):")

      (quick-bench "  octree within-radius"
        (fn [] (idx/within-radius tree-1000 q-point r-medium)))

      (quick-bench "  brute-force within-radius"
        (fn [] (brute-force-within-radius uniform-1000 q-point r-medium)))

      ;; Verify same results
      (let [octree-result (set (map :id (idx/within-radius tree-1000 q-point r-medium)))
            brute-result  (set (map :id (brute-force-within-radius uniform-1000 q-point r-medium)))]
        (when (not= octree-result brute-result)
          (println "  WARNING: octree and brute-force disagree!")))

      ;; --- Nearest neighbor ---
      (quick-bench "nearest-dist (100 uniform)"
        (fn [] (idx/nearest-dist tree-100 q-point 0)))

      (quick-bench "nearest-dist (1000 uniform)"
        (fn [] (idx/nearest-dist tree-1000 q-point 0)))

      (quick-bench "nearest-dist (5000 uniform)"
        (fn [] (idx/nearest-dist tree-5000 q-point 0)))

      ;; --- Octree vs brute-force for nearest ---
      (println "\n  Octree vs Brute-Force nearest-dist (1000 uniform):")

      (quick-bench "  octree nearest-dist"
        (fn [] (idx/nearest-dist tree-1000 q-point 0)))

      (quick-bench "  brute-force nearest-dist"
        (fn [] (brute-force-nearest uniform-1000 q-point 0)))

      ;; --- All-body queries (the realistic workload) ---
      (quick-bench "all-body within-radius r=1e13 (100 uniform)"
        (fn []
          (mapv (fn [p] (idx/within-radius tree-100 (:position p) r-medium))
                uniform-100)))

      (quick-bench "all-body within-radius r=1e13 (1000 uniform)"
        (fn []
          (mapv (fn [p] (idx/within-radius tree-1000 (:position p) r-medium))
                uniform-1000)))

      ;; --- Scaling summary ---
      (println "\n  Spatial Index Scaling:")
      (println "    Build: O(N log N) expected.")
      (println "    Query: depends on radius and local density.")
      (println "    Clustered distributions may degrade if tree is unbalanced.")
      (println "    Brute-force O(N) baseline — octree should win at N > ~100."))))

(defn profile-iterations []
  (let [items (make-uniform-points 1000 1.0e14)
        tree  (idx/build items)
        r     1.0e13]
    (doseq [p items]
      (idx/within-radius tree (:position p) r))))
