(ns domain.profile-test
  "Coverage tests for the benchmark profiling helpers."
  (:require
   [clojure.test :refer [deftest is]]
   [domain.profile :as profile]))

(deftest profile-key-builds-keyword
  (is (= :gravity/build (profile/profile-key :gravity :build)))
  (is (= :collision/detect (profile/profile-key :collision :detect))))

(deftest with-profile-merges-when-enabled
  (let [w {:genesis/profile-subsystems? true}
        w' (profile/with-profile w {:gravity/build 100})]
    (is (= {:gravity/build 100} (:genesis/_profile w')))))

(deftest with-profile-merges-existing-profile
  (let [w {:genesis/profile-subsystems? true :genesis/_profile {:gravity/build 100}}
        w' (profile/with-profile w {:gravity/build 50 :collision/detect 30})]
    (is (= {:gravity/build 150 :collision/detect 30} (:genesis/_profile w')))))

(deftest with-profile-is-noop-when-disabled
  (let [w {}]
    (is (= w (profile/with-profile w {:gravity/build 100})))))

(deftest with-profile-is-noop-when-nil
  (let [w {:genesis/profile-subsystems? true}]
    (is (= w (profile/with-profile w nil)))))

(deftest timing-measures-nanos
  (let [[result dt] (profile/timing #(inc 2))]
    (is (= 3 result))
    (is (number? dt))
    (is (pos? dt))))

(deftest profile-section-accumulates-when-enabled
  (let [w {:genesis/profile-subsystems? true}
        result (profile/profile-section w :test/sleep
                                        (fn [_] (Thread/sleep 1) {:x 1}))]
    (is (= 1 (:x result)))
    (is (number? (get-in result [:genesis/_profile :test/sleep])))
    (is (pos? (get-in result [:genesis/_profile :test/sleep])))))

(deftest profile-section-is-noop-when-disabled
  (let [w {}
        result (profile/profile-section w :test/sleep
                                        (fn [_] {:x 1}))]
    (is (= {:x 1} result))
    (is (nil? (:genesis/_profile result)))))

(deftest profile-section-preserves-non-map-result
  (let [w {:genesis/profile-subsystems? true}
        result (profile/profile-section w :test/const (fn [_] 42))]
    (is (= 42 result))))

(deftest profile-sections-runs-sequence
  (let [w {:genesis/profile-subsystems? true}
        result (profile/profile-sections w
                                         [[:a (fn [w] (assoc w :a 1))]
                                          [:b (fn [w] (assoc w :b 2))]])]
    (is (= 1 (:a result)))
    (is (= 2 (:b result)))
    (is (number? (get-in result [:genesis/_profile :a])))
    (is (number? (get-in result [:genesis/_profile :b])))))

(deftest profile-sections-disabled-runs-without-profiling
  (let [w {}
        result (profile/profile-sections w
                                         [[:a (fn [w] (assoc w :a 1))]
                                          [:b (fn [w] (assoc w :b 2))]])]
    (is (= 1 (:a result)))
    (is (= 2 (:b result)))
    (is (nil? (:genesis/_profile result)))))

(deftest profile-sections-discards-timings-if-final-not-map
  (let [w {:genesis/profile-subsystems? true}
        result (profile/profile-sections w
                                         [[:a (fn [_] 1)]
                                          [:b (fn [_] 2)]])]
    (is (= 2 result))))
