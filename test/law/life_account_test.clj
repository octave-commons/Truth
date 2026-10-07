(ns law.life-account-test
  "Executable shape and invariant boundaries for the carbon account."
  (:require
   [clojure.edn :as edn]
   [clojure.test :refer [deftest is testing]]
   [law.life-account :as law]))

(def ^:private cause #uuid "10000000-0000-0000-0000-000000000001")
(def ^:private account-id [:life-origin/v1 1010 cause])
(def ^:private material {:mass-kg 2000000.0 :composition {:C 0.5 :O 0.5}})
(def ^:private witness
  {:mass-bits "413e848000000000"
   :composition-bits {:C "3fe0000000000000" :O "3fe0000000000000"}
   :discarded-unit {:numerator 0 :denominator 1}})
(def ^:private account
  {:id account-id :lineage [account-id :cohort 0] :revision 1 :status :open
   :opening 1000000000000000 :stocks {:U 0 :S 9990000000000 :B 10000000000
                                    :W 990000000000000}
   :exported 0 :source material :witness witness :terminal nil})
(def ^:private request
  {:op-id [account-id :origin 10] :account account-id :expected-revision 0
   :kind :origin :cause cause :interval nil :rule-version :life-origin/v1
   :material material})

(deftest named-schema-data-round-trips
  (is (= law/registry (edn/read-string (pr-str law/registry))))
  (is (every? #(contains? law/registry %)
              [::law/material ::law/account ::law/request ::law/history
               ::law/result ::law/budget-result])))

(deftest material-reuses-elements-and-finite-binary64-fractions
  (is (law/material? material))
  (is (law/material? {:mass-kg Double/MIN_VALUE :composition {:C 1.0}}))
  (doseq [invalid [(assoc material :mass-kg Double/NaN)
                   (assoc material :mass-kg Double/POSITIVE_INFINITY)
                   (assoc material :mass-kg 0.0)
                   (assoc material :mass-kg -1.0)
                   (assoc material :mass-kg 2)
                   (assoc material :composition {:C 0.5 :unknown 0.5})
                   (assoc material :composition {:C 1.1})
                   (assoc material :composition {:C -0.1 :O 1.0})
                   (assoc material :composition {:C Double/NaN})
                   (dissoc material :composition)]]
    (is (false? (law/material? invalid)) (pr-str invalid))))

(deftest shape-does-not-pretend-to-prove-the-exact-material-sum
  (is (law/material? (assoc material :composition {:C 0.5 :O 0.4})))
  (is (law/identity? request))
  (is (law/identity? (assoc request :expected-revision -1)))
  (is (not (law/request? (assoc request :expected-revision -1))))
  (is (not (law/identity? (assoc request :account [:life-origin/v1 1011 cause]))))
  (is (not (law/identity? (dissoc request :op-id)))))

(deftest account-laws-reject-missing-negative-fractional-and-unbalanced-stocks
  (is (law/account? account))
  (doseq [invalid [(update account :stocks dissoc :U)
                   (assoc-in account [:stocks :B] -1)
                   (assoc-in account [:stocks :B] 1/2)
                   (assoc-in account [:stocks :B] 0.0)
                   (update-in account [:stocks :S] inc)
                   (assoc account :revision 0)
                   (assoc account :revision 1.5)
                   (assoc account :exported 1)
                   (assoc account :lineage [account-id :cohort 1])
                   (assoc account :status :closed)]]
    (is (not (law/account? invalid)) (pr-str invalid)))
  (testing "revision is not limited to primitive long range; v1 allocation is capped"
    (let [large 1000000000000000000000000000000N]
      (is (law/account? (assoc account :revision large)))
      (is (not (law/account? (assoc account :opening large
                                   :stocks {:U 0 :B 0 :S large :W 0})))))))

(deftest closed-account-is-history-not-another-active-stock
  (let [closed (assoc account :revision 2 :status :closed
                      :exported (:opening account)
                      :stocks {:U 0 :S 0 :B 0 :W 0}
                      :terminal {:reason :parent-removed
                                 :destination :unresolved-physical-boundary
                                 :inventory (:stocks account)})]
    (is (law/account? closed))
    (is (not (law/account? (assoc-in closed [:stocks :B] 1))))
    (is (not (law/account? (assoc-in closed [:terminal :inventory :B] 0))))
    (is (not (law/account? (assoc-in closed [:terminal :destination] :food))))))

(deftest request-shapes-do-not-coerce-or-ignore-operation-payloads
  (is (law/request? request))
  (doseq [invalid [(assoc request :interval [0.0 1.0])
                   (assoc request :rule-version :life-origin/v2)
                   (assoc request :cause #uuid "10000000-0000-0000-0000-000000000002")
                   (assoc request :op-id [account-id :other 10])
                   (assoc request :amounts {:q 0 :g 0 :L 0})
                   (dissoc request :material)]]
    (is (not (law/request? invalid)))))

(deftest retained-rejection-can-contain-invalid-payload-but-not-invalid-identity
  (let [bad (assoc request :expected-revision -1)
        outcome {:op-id (:op-id bad) :accepted? false
                 :reason :invalid-request :revision 0}
        entry {:request bad :outcome outcome}]
    (is (law/history? {(:op-id bad) entry}))
    (is (not (law/history? {(:op-id bad) (assoc entry :request (dissoc bad :op-id))})))
    (is (not (law/history? {(:op-id bad) (assoc-in entry [:outcome :op-id]
                                                [account-id :origin 11])})))))
