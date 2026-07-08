(ns infra.input-test
  "Player input dispatch: controls translate to observer updates on the world."
  (:require
   [clojure.test :refer [deftest testing is]]
   [infra.input     :as input]
   [domain.genesis  :as genesis]
   [domain.player   :as player]))

(deftest test-input-handling
  (testing "Controls operate on the observer in the world"
    (let [w        (genesis/create-world {:gas-count 4})
          before   (:focus-radius (player/get-observer w))
          narrowed (input/handle-input w :narrow-focus)
          moved    (input/handle-input w :move-focus [1e15 1e15 0])]
      (is (< (:focus-radius (player/get-observer narrowed)) before))
      (is (= [1e15 1e15 0] (:focus-position (player/get-observer moved)))))))
