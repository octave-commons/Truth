(ns law.voxel-test
  "Round-trip coverage for the voxel-substrate law schemas: representative
   records pass every validator, perturbed records fail."
  (:require
   [clojure.test :refer [deftest testing is]]
   [law.voxel :as voxel]))

(def ^:private representative-voxel
  {:material    :basalt
   :density     3000.0
   :temperature 288.0
   :state       :solid
   :cohesion    1.0e7})

(def ^:private representative-region
  {:center [0.0 0.0 6.371e6]
   :radius 5.0e4})

(deftest voxel-schema-round-trip
  (testing "a representative voxel validates"
    (is (voxel/voxel? representative-voxel)))
  (testing "every seed material is a valid material"
    (is (every? #(voxel/voxel? (assoc representative-voxel :material %))
                voxel/seed-materials)))
  (testing "material is an OPEN set — unlisted keywords validate (design §7.4)"
    (is (voxel/voxel? (assoc representative-voxel :material :olivine))))
  (testing "perturbations fail"
    (is (not (voxel/voxel? (assoc representative-voxel :material "basalt"))))
    (is (not (voxel/voxel? (assoc representative-voxel :density -1.0))))
    (is (not (voxel/voxel? (assoc representative-voxel :temperature -4.0))))
    (is (not (voxel/voxel? (assoc representative-voxel :state :plasma))))
    (is (not (voxel/voxel? (assoc representative-voxel :cohesion -1.0))))
    (is (not (voxel/voxel? (dissoc representative-voxel :state))))))

(deftest region-schema-round-trip
  (testing "a representative region validates"
    (is (voxel/region? representative-region)))
  (testing "perturbations fail"
    (is (not (voxel/region? (assoc representative-region :radius 0.0))))
    (is (not (voxel/region? (assoc representative-region :center [0.0 0.0]))))))

(deftest plate-schema-round-trip
  (let [plate {:id       :plate-pacifica
               :boundary [[1.0e6 0.0 0.0] [0.0 1.0e6 0.0] [-1.0e6 0.0 1.0e3]]
               :velocity [0.01 0.0 0.0]
               :kind     :oceanic}]
    (testing "a representative plate validates (kind optional)"
      (is (voxel/plate? plate))
      (is (voxel/plate? (dissoc plate :kind))))
    (testing "perturbations fail"
      (is (not (voxel/plate? (assoc plate :boundary [[0.0 0.0 0.0]]))))
      (is (not (voxel/plate? (assoc plate :velocity [0.01 0.0]))))
      (is (not (voxel/plate? (assoc plate :kind :granitic))))
      (is (not (voxel/plate? (dissoc plate :id)))))))

(deftest mantle-convection-cell-schema-round-trip
  (let [cell {:id     :cell-1
              :center [1.0e5 -2.0e5 3.0e6]
              :radius 5.0e5
              :flow   :upwelling
              :speed  1.0e-9}]
    (testing "a representative convection cell validates"
      (is (voxel/mantle-convection-cell? cell))
      (is (voxel/mantle-convection-cell? (assoc cell :flow :downwelling))))
    (testing "perturbations fail"
      (is (not (voxel/mantle-convection-cell? (assoc cell :flow :sideways))))
      (is (not (voxel/mantle-convection-cell? (assoc cell :speed -1.0))))
      (is (not (voxel/mantle-convection-cell? (assoc cell :radius 0.0)))))))

(deftest resource-cell-schema-round-trip
  (let [cell {:region              representative-region
              :total-mass          1.0e18
              :density-per-element {:Fe 12.0 :Si 40.0 :O 30.0}}]
    (testing "a representative resource-field cell validates"
      (is (voxel/resource-cell? cell)))
    (testing "density keys come from law.composition/element-set"
      (is (not (voxel/resource-cell?
                (assoc cell :density-per-element {:unobtainium 1.0})))))
    (testing "perturbations fail"
      (is (not (voxel/resource-cell? (assoc cell :total-mass 0.0))))
      (is (not (voxel/resource-cell?
                (assoc cell :density-per-element {:Fe -1.0}))))
      (is (not (voxel/resource-cell? (dissoc cell :region)))))))

(deftest edit-diff-schema-round-trip
  (let [sculpt-diff {:region     representative-region
                     :delta      [{:offset [3 -1 0]
                                   :after  (assoc representative-voxel
                                                  :material :granite)}]
                     :provenance :sculpt
                     :tick       4200}
        collision-diff
        {:region     representative-region
         :delta      [{:offset [0 0 0] :after nil}
                      {:offset [0 0 1]
                       :before representative-voxel
                       :after  (assoc representative-voxel
                                      :state :melt :temperature 1800.0
                                      :cohesion 0.0)}]
         :provenance :collision
         :tick       9001}]
    (testing "a representative sculpt diff validates"
      (is (voxel/edit-diff? sculpt-diff)))
    (testing "a representative collision diff validates (carve + melt-tag)"
      (is (voxel/edit-diff? collision-diff)))
    (testing "all provenances validate"
      (is (every? #(voxel/edit-diff? (assoc sculpt-diff :provenance %))
                  [:sculpt :mine :construct :collision])))
    (testing "perturbations fail"
      (is (not (voxel/edit-diff? (assoc sculpt-diff :provenance :explosion))))
      (is (not (voxel/edit-diff? (assoc sculpt-diff :delta []))))
      (is (not (voxel/edit-diff? (assoc sculpt-diff :tick -1))))
      (is (not (voxel/edit-diff? (assoc-in sculpt-diff [:delta 0 :after]
                                           {:material :granite}))))
      (is (not (voxel/edit-diff? (assoc-in sculpt-diff [:delta 0 :offset]
                                           [3 -1]))))
      (is (not (voxel/edit-diff? (dissoc sculpt-diff :region)))))))

(deftest edit-budget-constant-is-declared
  (testing "design §7.1 resolution: 2 ms/tick budget lives in law/ from day one"
    (is (= 2.0 voxel/edit-budget-ms-per-tick))))
