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

(deftest discarded-unit-is-strictly-less-than-one
  (let [budget {:carbon-units 1000000000000000000000N
                :allocation 1000000000000000 :origin-units 10000000000
                :stocks (:stocks account) :witness witness}
        result {:accepted? true :reason :origin :budget budget}]
    (is (law/budget-result? result))
    (doseq [fraction [{:numerator 1 :denominator 1}
                      {:numerator 2 :denominator 1}
                      {:numerator -1 :denominator 2}
                      {:numerator 0 :denominator 0}]]
      (is (not (law/budget-result? (assoc-in result [:budget :witness :discarded-unit] fraction))))
      (is (not (law/account? (assoc-in account [:witness :discarded-unit] fraction)))))))

(deftest accepted-history-requires-valid-request-and-matching-accepted-revision
  (let [outcome {:op-id (:op-id request) :accepted? true :reason :origin :revision 1}
        entry {:request request :outcome outcome}
        valid {(:op-id request) entry}]
    (is (law/history? valid))
    (doseq [bad [(assoc-in entry [:request :expected-revision] -1)
                 (update entry :request dissoc :material)
                 (assoc-in entry [:outcome :revision] 2)
                 (assoc-in entry [:outcome :revision] 0)
                 (assoc-in entry [:outcome :reason] :invalid-request)]]
      (is (not (law/history? {(:op-id request) bad}))))
    (is (not (law/history? {(:op-id request)
                            (-> entry
                                (assoc-in [:request :expected-revision] 2)
                                (assoc-in [:outcome :revision] 3))})))))

(deftest absent-account-check-is-local-to-supplied-same-account-acceptance
  (let [outcome {:op-id (:op-id request) :accepted? true :reason :origin :revision 1}
        context {:account nil :requested-account account-id
                 :history {(:op-id request) {:request request :outcome outcome}}}]
    (is (not (law/operation-context? context)))
    (is (law/operation-context? (assoc context :account account)))
    (is (law/operation-context? (assoc context :history {})))
    (is (law/operation-context? (assoc context :requested-account [:life-origin/v1 1011 cause])))
    (is (law/operation-context? (-> context
                                    (assoc-in [:history (:op-id request) :outcome :accepted?] false)
                                    (assoc-in [:history (:op-id request) :outcome :revision] 0)
                                    (assoc-in [:history (:op-id request) :outcome :reason] :zero-origin))))))

(deftest accepted-close-pins-the-terminal-revision-without-requiring-complete-history
  (let [close-request (-> request
                          (dissoc :material)
                          (assoc :op-id [account-id :close 1] :kind :close
                                 :expected-revision 1 :reason :parent-removed))
        close-entry {:request close-request
                     :outcome {:op-id (:op-id close-request) :accepted? true
                               :reason :closed-account :revision 2}}
        closed (assoc account :revision 2 :status :closed
                      :exported (:opening account) :stocks {:U 0 :S 0 :B 0 :W 0}
                      :terminal {:reason :parent-removed
                                 :destination :unresolved-physical-boundary
                                 :inventory (:stocks account)})
        context {:account closed :requested-account account-id
                 :history {(:op-id close-request) close-entry}}]
    (is (law/history? (:history context)))
    (doseq [[revision accepted?] [[1 false] [2 true] [3 false]]]
      (let [supplied (assoc closed :revision revision)]
        (is (law/account? supplied))
        (is (= accepted? (law/operation-context? (assoc context :account supplied)))
            (str "Accepted close at revision 2, supplied terminal revision " revision))))
    (testing "equality applies only to supplied same-account accepted close entries"
      (let [origin-entry {:request request
                          :outcome {:op-id (:op-id request) :accepted? true
                                    :reason :origin :revision 1}}
            extent-request (-> close-request
                               (dissoc :reason)
                               (assoc :op-id [account-id :extent 2] :kind :extent
                                      :amounts {:q 0 :g 0 :L 0}))
            extent-entry {:request extent-request
                          :outcome {:op-id (:op-id extent-request) :accepted? true
                                    :reason :extent :revision 2}}
            rejected-close (assoc close-entry :outcome
                                  {:op-id (:op-id close-request) :accepted? false
                                   :reason :stale-revision :revision 1})
            other-id [:life-origin/v1 1011 cause]
            other-close (-> close-entry
                            (assoc-in [:request :account] other-id)
                            (assoc-in [:request :op-id] [other-id :close 1])
                            (assoc-in [:outcome :op-id] [other-id :close 1]))]
        (doseq [[label revision entry]
                [["older origin" 2 origin-entry]
                 ["older extent" 3 extent-entry]
                 ["absent history" 3 nil]
                 ["rejected close" 2 rejected-close]
                 ["other-account close" 3 other-close]]]
          (let [history (if entry {(get-in entry [:request :op-id]) entry} {})]
            (is (law/history? history) label)
            (is (law/operation-context? (assoc context :account (assoc closed :revision revision)
                                               :history history))
                label)))))))

(deftest comparison-tags-do-not-collide-with-user-data
  (is (not (law/same-payload? -0.0 [:binary64 Long/MIN_VALUE])))
  (is (not (law/same-payload? #{-0.0} #{0.0})))
  (is (not (law/same-payload? {-0.0 :value} {0.0 :value})))
  (is (not (law/same-payload? (float -0.0) -0.0)))
  (is (not (law/same-payload? [1 2] '(1 2))))
  (is (law/same-payload? {:a #{1 2} :b [-0.0]} {:b [-0.0] :a #{2 1}})))
