(ns domain.gravity.barnes-hut-test
  (:require
   [clojure.test :refer [deftest is testing]]
   [shape.spatial :as spatial]
   [domain.gravity.barnes-hut :as bh]))

(def ^:const G 6.67408e-11)

(defn- bodies->soa
  "Build a minimal `:genesis/physics-soa` cache from body maps."
  [bodies]
  (let [n (count bodies)]
    {:eids   (vec (map :id bodies))
     :n      n
     :mass   (double-array (map :mass bodies))
     :radius (double-array (map #(or (:radius %) 0.0) bodies))
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
      (is (< (Math/abs (- cx 0.6)) 1e-9)))))

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
          acc   (bh/acceleration G θ tree earth)
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
          acc (bh/acceleration G θ tree center)]
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
          soa-result (bh/acceleration-for-soa G θ 1.0e-4 soa nil)]
      (doseq [body bodies]
        (let [expected (bh/acceleration G θ 1.0e-4 tree body)
              actual (get soa-result (:id body))]
          (is (< (spatial/dist actual expected) 1.0e-9)
              (str "eid " (:id body) " diverges: expected " expected ", got " actual)))))))

(deftest test-soa-self-gravity-zero
  (testing "Single isolated body in SoA returns zero acceleration"
    (let [b {:id :only :mass 1.0 :radius 1.0 :kind :body/gas
             :position [1.0 2.0 3.0] :velocity [0.0 0.0 0.0]}
          tree (bh/build-tree [b])
          soa (bodies->soa [b])]
      (is (= [0.0 0.0 0.0] (get (bh/acceleration-for-soa G 0.5 1.0e-4 soa nil) :only))))))

(deftest test-soa-empty-tree
  (testing "Empty tree returns zero acceleration for all SoA eids"
    (let [b {:id :lonely :mass 1.0 :radius 1.0 :kind :body/gas
             :position [1.0 2.0 3.0] :velocity [0.0 0.0 0.0]}
          soa (bodies->soa [b])]
      (is (= [0.0 0.0 0.0] (get (bh/acceleration-for-soa G 0.5 1.0e-4 soa nil) :lonely))))))
