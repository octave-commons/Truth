(ns domain.gravity.barnes-hut-test
  (:require
   [clojure.math :as math]
   [clojure.test :refer [deftest is testing]]
   [shape.spatial :as spatial]
   [domain.gravity.barnes-hut :as bh]))

(def ^:const G 6.67408e-11)

(defn- bodies->soa
  "Build a minimal `:genesis/physics-soa` cache from body maps. The :eps
   array defaults to -1.0 (no species data → legacy scalar softening), so
   fixtures that never set :eps exercise the pre-pair-law kernel exactly."
  [bodies]
  (let [n (count bodies)]
    {:eids   (vec (map :id bodies))
     :n      n
     :mass   (double-array (map :mass bodies))
     :radius (double-array (map #(or (:radius %) 0.0) bodies))
     :eps    (double-array (map #(double (or (:eps %) -1.0)) bodies))
     :px     (double-array (map #(double (nth (:position %) 0)) bodies))
     :py     (double-array (map #(double (nth (:position %) 1)) bodies))
     :pz     (double-array (map #(double (nth (:position %) 2)) bodies))
     :vx     (double-array n)
     :vy     (double-array n)
     :vz     (double-array n)}))

(deftest bh-single-body
  (let [b    (spatial/->body
              {:id 1 :mass 1.0 :radius 1.0 :kind :body/test
               :position (spatial/vec3 1.0 2.0 3.0)
               :velocity (spatial/vec3 0.0 0.0 0.0)})
        tree (bh/build-tree [b])]
    (is (= :leaf (:type tree)))
    (is (= 1.0 (:mass tree)))
    (is (= [1.0 2.0 3.0] (:com tree)))))

(deftest bh-two-bodies
  (let [b1   (spatial/->body
              {:id 1 :mass 2.0 :radius 1.0 :kind :body/test
               :position (spatial/vec3 0.0 0.0 0.0)
               :velocity (spatial/vec3 0.0 0.0 0.0)})
        b2   (spatial/->body
              {:id 2 :mass 3.0 :radius 1.0 :kind :body/test
               :position (spatial/vec3 1.0 0.0 0.0)
               :velocity (spatial/vec3 0.0 0.0 0.0)})
        tree (bh/build-tree [b1 b2])]
    (is (= :internal (:type tree)))
    (is (= 5.0 (double (:mass tree))))
    (let [[cx _ _] (:com tree)]
      (is (< (abs (- cx 0.6)) 1e-9)))))

(deftest single-body-force
  (testing "With one other body, BH force matches direct Newtonian force"
    (let [sun   (spatial/->body
                 {:id 1 :mass 1.0e6 :radius 1.0 :kind :body/star
                  :position (spatial/vec3 0.0 0.0 0.0)
                  :velocity (spatial/vec3 0.0 0.0 0.0)})
          earth (spatial/->body
                 {:id 2 :mass 1.0 :radius 1.0 :kind :body/planet
                  :position (spatial/vec3 10.0 0.0 0.0)
                  :velocity (spatial/vec3 0.0 0.0 0.0)})
          tree  (bh/build-tree [sun earth])
          θ     0.1
          acc   (bh/acceleration {:G G :theta θ :tree tree :body earth})
          r     10.0
          a-mag (/ (* G (:mass sun)) (* r r))
          expected (spatial/vec3 (- a-mag) 0.0 0.0)]
      (is (<= (spatial/dist acc expected) 1.0e-9)))))

(deftest symmetric-cancelation
  (testing "Equal masses symmetrically arranged around a center have ~0 net acceleration"
    (let [b1 (spatial/->body {:id 1 :mass 1.0 :radius 1.0 :kind :body/test
                              :position (spatial/vec3 -1.0 0.0 0.0)
                              :velocity (spatial/vec3 0.0 0.0 0.0)})
          b2 (spatial/->body {:id 2 :mass 1.0 :radius 1.0 :kind :body/test
                              :position (spatial/vec3  1.0 0.0 0.0)
                              :velocity (spatial/vec3 0.0 0.0 0.0)})
          center (spatial/->body {:id 3 :mass 1.0 :radius 1.0 :kind :body/test
                                  :position (spatial/vec3 0.0 0.0 0.0)
                                  :velocity (spatial/vec3 0.0 0.0 0.0)})
          tree (bh/build-tree [b1 b2 center])
          θ   0.5
          acc (bh/acceleration {:G G :theta θ :tree tree :body center})]
      (is (<= (spatial/len acc) 1.0e-6)))))

(deftest test-soa-acceleration-matches-body-path
  (testing "acceleration-for-soa matches acceleration for 50 random bodies"
    (let [rng (java.util.Random. 7)
          bodies (mapv (fn [i]
                         {:id i
                          :mass (+ 0.5 (.nextDouble rng))
                          :radius 1.0
                          :kind :body/gas
                          :position [(+ (- (* 100.0 (.nextDouble rng)) 50.0))
                                     (+ (- (* 100.0 (.nextDouble rng)) 50.0))
                                     (+ (- (* 100.0 (.nextDouble rng)) 50.0))]
                          :velocity [0.0 0.0 0.0]})
                       (range 50))
          tree (bh/build-tree bodies)
          soa (bodies->soa bodies)
          θ 0.5
          soa-result (bh/acceleration-for-soa {:G G :theta θ :softening 1.0e-4 :soa soa :self-id nil})]
      (doseq [body bodies]
        (let [expected (bh/acceleration {:G G :theta θ :softening 1.0e-4 :tree tree :body body})
              actual (get soa-result (:id body))]
          (is (< (spatial/dist actual expected) 1.0e-9)
              (str "eid " (:id body) " diverges: expected " expected ", got " actual)))))))

(deftest test-soa-self-gravity-zero
  (testing "Single isolated body in SoA returns zero acceleration"
    (let [b {:id :only :mass 1.0 :radius 1.0 :kind :body/gas
             :position [1.0 2.0 3.0] :velocity [0.0 0.0 0.0]}
          _tree (bh/build-tree [b])
          soa (bodies->soa [b])]
      (is (= [0.0 0.0 0.0] (get (bh/acceleration-for-soa {:G G :theta 0.5 :softening 1.0e-4 :soa soa :self-id nil}) :only))))))

(deftest test-soa-empty-tree
  (testing "Empty tree returns zero acceleration for all SoA eids"
    (let [b {:id :lonely :mass 1.0 :radius 1.0 :kind :body/gas
             :position [1.0 2.0 3.0] :velocity [0.0 0.0 0.0]}
          soa (bodies->soa [b])]
      (is (= [0.0 0.0 0.0] (get (bh/acceleration-for-soa {:G G :theta 0.5 :softening 1.0e-4 :soa soa :self-id nil}) :lonely))))))

(deftest test-body-map-gravity-cutoff
  (testing "Pairs inside the pair dead-zone (0.1·ε_pair) contribute zero acceleration"
    (let [heavy {:id 1 :mass 1.0e30 :radius 1.0e9 :kind :body/star :eps 1.0e8
                 :position [0.0 0.0 0.0] :velocity [0.0 0.0 0.0]}
          probe {:id 2 :mass 1.0 :radius 1.0e6 :kind :body/gas :eps 1.0e8
                 :position [5.0e6 0.0 0.0] :velocity [0.0 0.0 0.0]}
          theta 0.5]
      (is (= [0.0 0.0 0.0]
             (bh/acceleration {:G G :theta theta :softening 1.0e8
                               :tree (bh/build-tree [heavy probe]) :body probe}))
          "5e6 < 0.1·ε_pair (1e7) → inside the dead zone, no gravity")
      (let [far (assoc probe :position [1.0e10 0.0 0.0])]
        (is (not= [0.0 0.0 0.0]
                  (bh/acceleration {:G G :theta theta :softening 1.0e8
                                    :tree (bh/build-tree [heavy far]) :body far}))
            "1e10 ≫ 0.1·ε_pair → gravity on")))))

(deftest test-cutoff-legacy-scalar-default
  (testing "Bodies without species :eps soften at the scalar :softening, so the
            dead-zone is 0.1·softening — the legacy scalar kernel exactly"
    (let [heavy {:id 1 :mass 1.0e30 :radius 1.0e9 :kind :body/star
                 :position [0.0 0.0 0.0] :velocity [0.0 0.0 0.0]}
          probe {:id 2 :mass 1.0 :radius 1.0e6 :kind :body/gas
                 :position [5.0e6 0.0 0.0] :velocity [0.0 0.0 0.0]}
          theta 0.5
          soft  1.0e8]
      (is (= [0.0 0.0 0.0]
             (bh/acceleration {:G G :theta theta :softening soft
                               :tree (bh/build-tree [heavy probe]) :body probe}))
          "5e6 < 0.1·soft (1e7) → dead zone")
      (let [far (assoc probe :position [1.0e10 0.0 0.0])]
        (is (not= [0.0 0.0 0.0]
                  (bh/acceleration {:G G :theta theta :softening soft
                                    :tree (bh/build-tree [heavy far]) :body far}))
            "1e10 ≫ 1e7 → gravity on")))))

(deftest test-soa-gravity-cutoff
  (testing "SoA path also suppresses gravity inside the pair dead-zone"
    (let [heavy {:id :heavy :mass 1.0e30 :radius 1.0e9 :kind :body/star :eps 1.0e8
                 :position [0.0 0.0 0.0] :velocity [0.0 0.0 0.0]}
          probe {:id :probe :mass 1.0 :radius 1.0e6 :kind :body/gas :eps 1.0e8
                 :position [5.0e6 0.0 0.0] :velocity [0.0 0.0 0.0]}
          theta 0.5
          soft 1.0e8]
      (is (= [0.0 0.0 0.0]
             (get (bh/acceleration-for-soa {:G G :theta theta :softening soft
                                            :soa (bodies->soa [heavy probe]) :self-id nil})
                  :probe))
          "SoA probe inside the pair dead-zone feels no gravity")
      (let [far (assoc probe :position [5.0e9 0.0 0.0])]
        (is (not= [0.0 0.0 0.0]
                  (get (bh/acceleration-for-soa {:G G :theta theta :softening soft
                                                 :soa (bodies->soa [heavy far]) :self-id nil})
                       :probe))
            "SoA probe outside the pair dead-zone feels gravity")))
    (testing "an SoA cache without species :eps (all -1.0) uses the scalar
              softening dead-zone, byte-identical to the legacy kernel"
      (let [heavy {:id :heavy :mass 1.0e30 :radius 1.0e9 :kind :body/star
                   :position [0.0 0.0 0.0] :velocity [0.0 0.0 0.0]}
            probe {:id :probe :mass 1.0 :radius 1.0e6 :kind :body/gas
                   :position [5.0e9 0.0 0.0] :velocity [0.0 0.0 0.0]}]
        (is (= [0.0 0.0 0.0]
               (get (bh/acceleration-for-soa {:G G :theta 0.5 :softening 1.0e11
                                              :soa (bodies->soa [heavy probe]) :self-id nil})
                    :probe))
            "5e9 < 0.1·1e11 → dead zone under the legacy scalar")))))

(deftest test-non-finite-position-throws-not-stack-overflow
  (testing "A NaN or Infinite body position throws a descriptive ex-info from
            build-tree instead of blowing the stack in insert-body-into-node"
    (let [ok  (spatial/->body {:id 1 :mass 1.0 :radius 1.0 :kind :body/test
                               :position (spatial/vec3 0.0 0.0 0.0)
                               :velocity (spatial/vec3 0.0 0.0 0.0)})
          bad (spatial/->body {:id 2 :mass 1.0 :radius 1.0 :kind :body/test
                               :position [Double/NaN 0.0 0.0]
                               :velocity (spatial/vec3 0.0 0.0 0.0)})]
      (try
        (bh/build-tree [ok bad])
        (is false "expected an ex-info to be thrown")
        (catch clojure.lang.ExceptionInfo e
          (is (= 2 (:id (ex-data e))))
          (is (= ::bh/non-finite-position (:kind (ex-data e)))))))))

(deftest test-non-finite-soa-position-throws-not-stack-overflow
  (testing "A NaN or Infinite SoA position throws a descriptive ex-info from
            build-tree-from-soa instead of blowing the stack in
            insert-idx-into-node"
    (let [ok  {:id :ok :mass 1.0 :radius 1.0 :kind :body/gas
               :position [0.0 0.0 0.0] :velocity [0.0 0.0 0.0]}
          bad {:id :bad :mass 1.0 :radius 1.0 :kind :body/gas
               :position [0.0 Double/POSITIVE_INFINITY 0.0] :velocity [0.0 0.0 0.0]}
          soa (bodies->soa [ok bad])]
      (try
        (bh/build-tree-from-soa soa)
        (is false "expected an ex-info to be thrown")
        (catch clojure.lang.ExceptionInfo e
          (is (= :bad (:eid (ex-data e))))
          (is (= ::bh/non-finite-position (:kind (ex-data e)))))))))

(deftest test-subnormal-mass-leaf-com-finite
  (testing "A leaf with only sub-Double/MIN_NORMAL mass returns a finite COM"
    (let [tiny (spatial/->body {:id 1 :mass 1.0e-309 :radius 1.0 :kind :body/gas
                                :position (spatial/vec3 1.0 2.0 3.0)
                                :velocity (spatial/vec3 0.0 0.0 0.0)})
          tree (bh/build-tree [tiny])]
      (is (= :leaf (:type tree)))
      (is (every? #(Double/isFinite (double %)) (:com tree)))
      (is (= 1.0e-309 (:mass tree))))))

(deftest test-subnormal-mass-does-not-poison-internal-com
  (testing "A subnormal-mass leaf does not make the internal node COM infinite"
    (let [tiny  (spatial/->body {:id 1 :mass 1.0e-309 :radius 1.0 :kind :body/gas
                                 :position (spatial/vec3 1.0 1.0 1.0)
                                 :velocity (spatial/vec3 0.0 0.0 0.0)})
          heavy (spatial/->body {:id 2 :mass 1.0e28 :radius 1.0e9 :kind :body/star
                                 :position (spatial/vec3 0.0 0.0 0.0)
                                 :velocity (spatial/vec3 0.0 0.0 0.0)})
          tree  (bh/build-tree [heavy tiny])
          acc   (bh/acceleration {:G G :theta 0.5 :softening 1.0e-4 :tree tree :body tiny})]
      (is (= :internal (:type tree)))
      (is (every? #(Double/isFinite (double %)) (:com tree)))
      (is (every? #(Double/isFinite (double %)) acc)))))

(deftest test-subnormal-mass-soa-com-finite
  (testing "SoA path with sub-Double/MIN_NORMAL mass returns finite COM and acceleration"
    (let [tiny  {:id :tiny :mass 1.0e-309 :radius 1.0 :kind :body/gas
                 :position [1.0 1.0 1.0] :velocity [0.0 0.0 0.0]}
          heavy {:id :heavy :mass 1.0e28 :radius 1.0e9 :kind :body/star
                 :position [0.0 0.0 0.0] :velocity [0.0 0.0 0.0]}
          soa   (bodies->soa [heavy tiny])
          accs  (bh/acceleration-for-soa {:G G :theta 0.5 :softening 1.0e-4 :soa soa :self-id nil})]
      (is (every? #(Double/isFinite (double %)) (get accs :tiny))))))

(deftest test-cutoff-preserves-distant-gravity
  (testing "Pairs separated by more than the 0.1·ε_pair dead-zone feel the full
            softened force"
    (let [sun   (spatial/->body
                 {:id 1 :mass 1.0e6 :radius 1.0 :kind :body/star
                  :position (spatial/vec3 0.0 0.0 0.0)
                  :velocity (spatial/vec3 0.0 0.0 0.0)})
          earth (spatial/->body
                 {:id 2 :mass 1.0 :radius 1.0 :kind :body/planet
                  :position (spatial/vec3 10.0 0.0 0.0)
                  :velocity (spatial/vec3 0.0 0.0 0.0)})
          tree  (bh/build-tree [sun earth])
          theta 0.1
          soft  1.0e-4
          ;; Hand-computed Plummer value, mirroring the kernel arithmetic:
          ;; no :eps on these bodies → ε_pair = scalar soft, 10 ≫ 0.1·1e-4.
          dx    -10.0
          d2    (+ (* dx dx) (* soft soft))
          inv-r (* d2 (math/sqrt d2))
          scale (/ (* G 1.0e6) inv-r)
          expected [(* dx scale) 0.0 0.0]]
      (is (= expected
             (bh/acceleration {:G G :theta theta :softening soft :tree tree :body earth}))
          "distant bodies feel the full softened Plummer force"))))
