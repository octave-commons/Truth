(ns domain.naming-test
  (:require
   [clojure.test :refer [deftest testing is]]
   [domain.naming :as naming]))

(deftest body-names-are-deterministic-and-distinct
  (testing "same eid → same name, across calls"
    (is (= (naming/body-name 42) (naming/body-name 42)))
    (is (= (naming/body-name "uuid-a") (naming/body-name "uuid-a"))))
  (testing "names are capitalized, pronounceable-length strings"
    (doseq [eid (range 50)]
      (let [n (naming/body-name eid)]
        (is (string? n))
        (is (<= 3 (count n) 16))
        (is (Character/isUpperCase (char (first n)))))))
  (testing "distinct eids mostly get distinct names"
    (let [names (map naming/body-name (range 200))]
      (is (> (count (distinct names)) 150)
          "collisions stay rare across a system's worth of bodies"))))

(deftest display-label-shows-name-and-kind
  (is (= (str (naming/body-name 7) " — star")
         (naming/display-label 7 :star)))
  (is (.endsWith ^String (naming/display-label 7 :planet) "planet"))
  (testing "unknown states fall back to their keyword name"
    (is (.endsWith ^String (naming/display-label 7 :weird) "weird"))))
