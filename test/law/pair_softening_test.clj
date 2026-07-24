(ns law.pair-softening-test
  "Species/pair softening law tests (kanban/tasks/compact-pair-softening.md).

   The species rule: a RESOLVED COMPACT body (matter-state ∈ compact vocabulary)
   softens at its physical radius — a point mass at its real size. Gas parcels
   and stateless bodies keep the world cloud ε. The pair rule ε_pair = max(ε_i,
   ε_j) keeps the pair force Newton's-third-law exact."
  (:require
   [clojure.test :refer [deftest is testing]]
   [law.stellar :as law]))

(deftest body-softening-species-rule
  (testing "every resolved compact matter-state softens at c/radius"
    (doseq [ms [:condensed-core :planetesimal :gas-giant :brown-dwarf
                :protostar :star :planet :stellar-remnant]]
      (is (= 7.0e7 (law/body-softening ms 7.0e7 5.0e14))
          (str ms " is a compact body: ε = radius, not the cloud ε"))))
  (testing "a gas parcel (:nebula) keeps the world softening"
    (is (= 5.0e14 (law/body-softening :nebula 2.0e14 5.0e14))))
  (testing "a stateless body (no matter-state) keeps the world softening"
    (is (= 5.0e14 (law/body-softening nil 1.0 5.0e14)))))

(deftest pair-softening-rule
  (testing "ε_pair = max(ε_i, ε_j) — symmetric, so the pair force is Newton's-
            third-law exact (momentum conservation)"
    (is (= 5.0e14 (law/pair-softening 5.0e14 7.0e7)))
    (is (= 5.0e14 (law/pair-softening 7.0e7 5.0e14))
        "symmetric in argument order")
    (is (= 6.96e8 (law/pair-softening 6.96e8 7.0e7))
        "star–planet pair: the larger compact radius")
    (is (= 5.0e14 (law/pair-softening 5.0e14 5.0e14))
        "gas–gas pair: the world ε, identical to the legacy scalar kernel")))

(deftest softening-cutoff-fraction-value
  (testing "the pair dead-zone is 0.1·ε_pair"
    (is (= 0.1 law/softening-cutoff-fraction))))
