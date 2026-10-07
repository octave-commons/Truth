(ns law.input-intent-test
  "Structural input boundaries must not change later semantic validation order."
  (:require
   [clojure.edn :as edn]
   [clojure.test :refer [deftest is testing]]
   [law.input-intent :as intent]))

(deftest named-schema-data-round-trips
  (is (= #{::intent/contextual-entry ::intent/iteration-context
           ::intent/submission-options}
         (set (keys intent/registry))))
  (is (= intent/registry (edn/read-string (pr-str intent/registry)))))

(deftest payload-shape-preserves-the-callable-contract
  (doseq [callable [identity :selected {:selected {:accepted true}}]]
    (is (intent/contextual-entry? {:update callable})))
  (doseq [entry [nil {} {:update nil} {:update 7}
                 {:update identity :extra :not-in-contract}]]
    (is (not (intent/contextual-entry? entry)))))

(deftest context-is-structural-before-observer-aware-validation
  (testing "a raw offset may be invalid without prematurely rejecting tracking or no observer"
    (doseq [manual? [true false]
            offset [nil [] [1.0 2.0 3.0] '(1 2 3) [##NaN 0.0 0.0]]]
      (is (intent/iteration-context? {:manual? manual? :focus-offset offset}))))
  (doseq [context [nil {} {:manual? true} {:focus-offset nil}
                   {:manual? nil :focus-offset nil}
                   {:manual? :manual :focus-offset nil}
                   {:manual? true :focus-offset nil :config (atom {})}]]
    (is (not (intent/iteration-context? context)))))

(deftest optional-capability-distinguishes-absence-from-invalid-presence
  (is (intent/submission-options? {}))
  (is (intent/submission-options? {:submit-contextual! identity}))
  (doseq [options [nil {:submit-contextual! nil} {:submit-contextual! false}
                   {:submit-contextual! :keyword} {:submit-contextual! {}}
                   {:submit-contextual! identity :extra true}]]
    (is (not (intent/submission-options? options)))))
