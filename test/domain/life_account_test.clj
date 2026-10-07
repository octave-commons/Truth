(ns domain.life-account-test
  "Acceptance tests for supplied-data accounting, not a natural-life fixture."
  (:require
   [clojure.test :refer [deftest is testing]]
   [domain.life-account :as account]
   [law.life-account :as law]))

(def ^:private cause #uuid "10000000-0000-0000-0000-000000000001")
(def ^:private account-id [:life-origin/v1 1010 cause])
(def ^:private material {:mass-kg 2000000.0 :composition {:C 0.5 :O 0.5}})
(def ^:private opening-stocks
  {:U 0 :S 9990000000000 :B 10000000000 :W 990000000000000})
(def ^:private witness
  {:mass-bits "413e848000000000"
   :composition-bits {:C "3fe0000000000000" :O "3fe0000000000000"}
   :discarded-unit {:numerator 0 :denominator 1}})
(def ^:private opened
  {:id account-id :lineage [account-id :cohort 0] :revision 1 :status :open
   :opening 1000000000000000 :stocks opening-stocks :exported 0
   :source material :witness witness :terminal nil})

(defn- request [kind sequence-number revision payload]
  (merge {:op-id [account-id (if (= kind :origin) :origin :test) sequence-number]
          :account account-id :expected-revision revision :kind kind
          :cause cause :interval nil :rule-version :life-origin/v1}
         payload))

(defn- origin [sequence-number supplied-material]
  (request :origin sequence-number 0 {:material supplied-material}))

(defn- retain [history result]
  (if-let [entry (:retain result)]
    (assoc history (get-in entry [:request :op-id]) entry)
    history))

(defn- total [stocks]
  (reduce +' 0 (vals stocks)))

(defn- expect-rejection [before operation reason]
  (let [result (account/apply-operation before {} operation)]
    (is (= :rejected (:disposition result)))
    (is (= reason (:reason result)))
    (is (= before (:account result)))
    (is (empty? (:effects result)))
    (is (= operation (get-in result [:retain :request])))
    (is (false? (get-in result [:outcome :accepted?])))
    (is (= (:outcome result) (get-in result [:retain :outcome])))
    result))

(deftest capped-origin-has-an-exact-nonadditive-partition
  (let [result (account/origin-budget material)
        budget (:budget result)]
    (is (law/budget-result? result))
    (is (true? (:accepted? result)))
    (is (= 1000000000000000000000N (:carbon-units budget)))
    (is (= 1000000000000000 (:allocation budget)))
    (is (= 10000000000 (:origin-units budget)))
    (is (= opening-stocks (:stocks budget)))
    (is (= witness (:witness budget)))
    (is (= (:allocation budget) (total (:stocks budget))))))

(deftest exact-product-is-floored-before-any-binary64-rounding
  (let [input {:mass-kg 0.3 :composition {:C 0.3 :O 0.7}}
        result (account/origin-budget input)
        budget (:budget result)]
    ;; IEEE-754 0.3 squared is below the decimal 0.09. Rounding the product
    ;; first and multiplying by 1e15 incorrectly produces 90000000000000.
    (is (true? (:accepted? result)))
    (is (= 89999999999999 (:carbon-units budget)))
    (is (= 89999999 (:allocation budget)))
    (is (= 899 (:origin-units budget)))
    (is (= {:U 0 :S 899100 :B 899 :W 89100000} (:stocks budget)))
    (is (= {:numerator 9837549616616482200413696917N
            :denominator 9903520314283042199192993792N}
           (get-in budget [:witness :discarded-unit])))
    (is (= "3fd3333333333333" (get-in budget [:witness :mass-bits])))
    (is (= "3fd3333333333333" (get-in budget [:witness :composition-bits :C])))))

(deftest very-large-carbon-and-subnormal-input-do-not-overflow-or-round-up
  (let [large (account/origin-budget {:mass-kg 1.0e20
                                      :composition {:C 0.5 :O 0.5}})
        tiny (account/origin-budget {:mass-kg Double/MIN_VALUE
                                     :composition {:C 1.0}})]
    (is (true? (:accepted? large)))
    (is (= 50000000000000000000000000000000000N
           (get-in large [:budget :carbon-units])))
    (is (= opening-stocks (get-in large [:budget :stocks])))
    (is (false? (:accepted? tiny)))
    (is (= :zero-origin (:reason tiny)))
    (is (zero? (get-in tiny [:budget :carbon-units])))
    (is (zero? (get-in tiny [:budget :origin-units])))
    (is (= "0000000000000001" (get-in tiny [:budget :witness :mass-bits])))
    (is (= 30517578125N (get-in tiny [:budget :witness :discarded-unit :numerator])))))

(deftest exact-input-sum-is-checked-without-renormalization
  (doseq [[composition expected]
          [[{:C 0.5 :O 0.4} :composition-sum]
           [{:C 0.5 :O 0.500001} :composition-sum]
           [{:O 1.0} :missing-carbon]
           [{:C 0.0 :O 1.0} :missing-carbon]
           [{:C 0.5 :unknown 0.5} :invalid-material]]]
    (let [input (assoc material :composition composition)
          result (account/origin-budget input)]
      (is (false? (:accepted? result)))
      (is (= expected (:reason result)))
      (is (nil? (:budget result)))))
  (let [input (assoc material :composition {:C 0.5 :O 0.5000009})]
    (is (true? (:accepted? (account/origin-budget input)))))
  (doseq [mass [Double/NaN Double/POSITIVE_INFINITY Double/NEGATIVE_INFINITY
                0.0 -0.0 -1.0 nil 1000]]
    (is (= :invalid-material
           (:reason (account/origin-budget (assoc material :mass-kg mass)))))))

(deftest origin-is-first-accepted-operation-and-retains-input-bits
  (let [supplied (assoc-in material [:composition :H] -0.0)
        operation (origin 10 supplied)
        result (account/apply-operation nil {} operation)
        next-account (:account result)]
    (is (law/result? result))
    (is (= :accepted (:disposition result)))
    (is (= 1 (:revision next-account)))
    (is (= account-id (:id next-account)))
    (is (= [account-id :cohort 0] (:lineage next-account)))
    (is (= opening-stocks (:stocks next-account)))
    (is (= supplied (:source next-account)))
    (is (= "8000000000000000" (get-in next-account [:witness :composition-bits :H])))
    (is (= operation (get-in result [:retain :request])))
    (is (= (:outcome result) (get-in result [:retain :outcome])))
    (is (= [{:kind :origin :account account-id :lineage [account-id :cohort 0]
             :carbon-units 10000000000}]
           (:effects result)))
    (is (= -0.0 (get-in operation [:material :composition :H])))))

(deftest extent-transfer-conserves-carbon-with-growth-before-loss
  (let [operation (request :extent 1 1 {:amounts {:q 20 :g 12 :L 7}})
        result (account/apply-operation opened {} operation)]
    (is (law/result? result))
    (is (= :accepted (:disposition result)))
    (is (= {:U 0 :S 9989999999980 :B 10000000005 :W 990000000000015}
           (get-in result [:account :stocks])))
    (is (= 2 (get-in result [:account :revision])))
    (is (= (:opening opened) (total (get-in result [:account :stocks]))))
    (is (= opened (assoc opened :stocks opening-stocks))))
  (let [small (assoc opened :opening 10 :stocks {:U 0 :S 8 :B 2 :W 0})
        operation (request :extent 2 1 {:amounts {:q 8 :g 8 :L 10}})
        result (account/apply-operation small {} operation)]
    (is (= :accepted (:disposition result)))
    (is (= {:U 0 :S 0 :B 0 :W 10} (get-in result [:account :stocks])))))

(deftest zero-transfer-is-an-accepted-operation-not-a-replay
  (let [operation (request :extent 1 1 {:amounts {:q 0 :g 0 :L 0}})
        result (account/apply-operation opened {} operation)]
    (is (= :accepted (:disposition result)))
    (is (= opening-stocks (get-in result [:account :stocks])))
    (is (= 2 (get-in result [:account :revision])))
    (is (true? (get-in result [:outcome :accepted?])))
    (is (= operation (get-in result [:retain :request])))))

(deftest bounded-extents-are-rejected-without-clipping
  (doseq [amounts [{:q -1 :g 0 :L 0}
                   {:q 1/2 :g 0 :L 0}
                   {:q 1.0 :g 0 :L 0}
                   {:q 1 :g 0}
                   {:q 0 :g Double/NaN :L 0}]]
    (expect-rejection opened (request :extent 1 1 {:amounts amounts}) :invalid-request))
  (doseq [amounts [{:q 1 :g 2 :L 0}
                   {:q 9990000000001 :g 0 :L 0}
                   {:q 0 :g 0 :L 10000000001}]]
    (expect-rejection opened (request :extent 1 1 {:amounts amounts}) :extent-bounds)))

(deftest deterministic-running-account-prevents-double-spend
  (let [small (assoc opened :opening 10 :stocks {:U 0 :S 8 :B 2 :W 0})
        first-op (request :extent 1 1 {:amounts {:q 6 :g 3 :L 0}})
        first-result (account/apply-operation small {} first-op)
        current (:account first-result)
        history (retain {} first-result)
        stale (account/apply-operation current history
                                       (request :extent 2 1 {:amounts {:q 6 :g 3 :L 0}}))
        overdraw (account/apply-operation current history
                                          (request :extent 3 2 {:amounts {:q 6 :g 3 :L 0}}))]
    (is (= {:U 0 :S 2 :B 5 :W 3} (:stocks current)))
    (is (= :stale-revision (:reason stale)))
    (is (= :extent-bounds (:reason overdraw)))
    (is (= current (:account stale) (:account overdraw)))
    (is (= 2 (:revision current)))
    (is (= 10 (total (:stocks current))))))

(deftest revision-arithmetic-remains-arbitrary-precision
  (let [n 1000000000000000000000000000000N
        current (assoc opened :revision n)
        operation (request :extent n n {:amounts {:q 0 :g 0 :L 0}})
        result (account/apply-operation current {} operation)]
    (is (= :accepted (:disposition result)))
    (is (= (inc n) (get-in result [:account :revision])))
    (is (= opening-stocks (get-in result [:account :stocks])))))

(deftest valid-extents-preserve-nonnegative-stock-and-exact-total
  (let [small (assoc opened :opening 10 :stocks {:U 0 :S 8 :B 2 :W 0})]
    (doseq [[q g loss] [[0 0 0] [0 0 2] [1 0 1] [1 1 3]
                        [4 2 1] [7 3 5] [8 0 2] [8 8 0] [8 8 10]]]
      (let [result (account/apply-operation small {}
                                            (request :extent 1 1 {:amounts {:q q :g g :L loss}}))
            stocks (get-in result [:account :stocks])]
        (is (= :accepted (:disposition result)))
        (is (= 10 (total stocks)))
        (is (every? #(and (integer? %) (not (neg? %))) (vals stocks)))
        (is (= (- 8 q) (:S stocks)))
        (is (zero? (:U stocks)))))))

(deftest malformed-caller-state-fails-before-transaction-admission
  (let [operation (request :extent 1 1 {:amounts {:q 0 :g 0 :L 0}})]
    (doseq [bad [(update opened :stocks dissoc :U)
                 (assoc-in opened [:stocks :S] -1)
                 (update-in opened [:stocks :B] inc)
                 (assoc opened :revision -1)]]
      (is (thrown-with-msg? clojure.lang.ExceptionInfo #"Invalid supplied account"
                            (account/apply-operation bad {} operation))))
    (is (thrown-with-msg? clojure.lang.ExceptionInfo #"Invalid supplied history"
                          (account/apply-operation opened {(:op-id operation) {}} operation)))))

(deftest missing-stock-and-cross-account-operations-do-not-borrow-a-balance
  (expect-rejection nil (request :extent 1 0 {:amounts {:q 0 :g 0 :L 0}})
                    :missing-account)
  (let [other-id [:life-origin/v1 1011 cause]
        operation (-> (request :extent 1 1 {:amounts {:q 0 :g 0 :L 0}})
                      (assoc :account other-id :op-id [other-id :test 1]))]
    (expect-rejection opened operation :account-mismatch)))

(deftest exact-retry-precedes-current-revision-and-closure-checks
  (let [operation (origin 10 material)
        first-result (account/apply-operation nil {} operation)
        history (retain {} first-result)
        close-op (request :close 1 1 {:reason :parent-removed})
        close-result (account/apply-operation (:account first-result) history close-op)
        history (retain history close-result)
        closed (:account close-result)]
    (is (= :accepted (:disposition close-result)))
    (doseq [[retry expected] [[operation (:outcome first-result)]
                              [close-op (:outcome close-result)]]]
      (let [result (account/apply-operation closed history retry)]
        (is (= :replayed (:disposition result)))
        (is (= expected (:outcome result)))
        (is (= closed (:account result)))
        (is (nil? (:retain result)))
        (is (empty? (:effects result)))))
    (is (= 2 (:revision closed)))
    (is (= :closed (:status closed)))
    (is (= 1000000000000000 (:exported closed)))
    (is (= opening-stocks (get-in closed [:terminal :inventory])))))

(deftest conflicting-id-does-not-replace-retained-outcome
  (let [operation (origin 10 (assoc-in material [:composition :H] -0.0))
        first-result (account/apply-operation nil {} operation)
        history (retain {} first-result)
        changed (assoc-in operation [:material :composition :H] 0.0)
        result (account/apply-operation (:account first-result) history changed)]
    (testing "equal numeric zeros have distinct binary64 request provenance"
      (is (= :conflict (:disposition result)))
      (is (= :conflicting-reuse (:reason result)))
      (is (= (:outcome first-result) (:outcome result)))
      (is (= (:account first-result) (:account result)))
      (is (nil? (:retain result)))
      (is (empty? (:effects result)))
      (is (= operation (get-in history [(:op-id operation) :request]))))))

(deftest rejected-origin-is-retained-without-reserving-an-account
  (let [tiny-op (origin 10 {:mass-kg Double/MIN_VALUE :composition {:C 1.0}})
        rejection (account/apply-operation nil {} tiny-op)
        history (retain {} rejection)
        retry (account/apply-operation nil history tiny-op)
        accepted (account/apply-operation nil history (origin 11 material))]
    (is (= :zero-origin (:reason rejection)))
    (is (nil? (:account rejection)))
    (is (zero? (get-in rejection [:outcome :revision])))
    (is (= tiny-op (get-in rejection [:retain :request])))
    (is (= :replayed (:disposition retry)))
    (is (= (:outcome rejection) (:outcome retry)))
    (is (nil? (:account retry)))
    (is (nil? (:retain retry)))
    (is (= :accepted (:disposition accepted)))
    (is (= 1 (get-in accepted [:account :revision])))
    (is (= 2 (count (retain history accepted))))))

(deftest accepted-same-account-history-cannot-fund-a-second-opening-with-missing-state
  (let [operation (origin 10 material)
        accepted (account/apply-operation nil {} operation)
        history (retain {} accepted)]
    (is (= :accepted (:disposition accepted)))
    (doseq [next-request [operation (origin 11 material)]]
      (is (thrown-with-msg? clojure.lang.ExceptionInfo #"Invalid supplied account/history pair"
                            (account/apply-operation nil history next-request))))
    (is (= 1 (count history)))
    (is (= (:outcome accepted) (get-in history [(:op-id operation) :outcome])))
    (is (= 1 (get-in accepted [:account :revision])))))

(deftest supplied-history-cannot-be-newer-closed-or-from-different-opening-material
  (let [operation (origin 10 material)
        accepted (account/apply-operation nil {} operation)
        current (:account accepted)
        history (retain {} accepted)
        action (request :extent 1 1 {:amounts {:q 0 :g 0 :L 0}})
        advanced (account/apply-operation current history action)
        newer-history (retain history advanced)
        close-op (request :close 2 1 {:reason :parent-removed})
        closed (account/apply-operation current history close-op)
        closed-history (retain history closed)
        changed-source (assoc-in current [:source :composition] {:C 0.25 :O 0.75})]
    (doseq [[supplied-account supplied-history]
            [[current newer-history]
             [(assoc current :revision 2) closed-history]
             [changed-source history]]]
      (is (thrown-with-msg? clojure.lang.ExceptionInfo #"Invalid supplied account/history pair"
                            (account/apply-operation supplied-account supplied-history operation))))
    (is (= current (:account accepted)))
    (is (= 1 (count history)))
    (is (= 2 (count newer-history) (count closed-history)))
    (testing "missing history is not evidence of contradiction or completeness"
      (is (= :accepted (:disposition (account/apply-operation current {} action)))))))

(deftest new-invalid-key-cannot-claim-a-retained-operation
  (doseq [operation [(dissoc (origin 10 material) :op-id)
                     (assoc (origin 10 material) :op-id [account-id :origin -1])
                     (assoc (origin 10 material) :account [:life-origin/v1 1011 cause])]]
    (let [result (account/apply-operation nil {} operation)]
      (is (= :rejected (:disposition result)))
      (is (= :invalid-identity (:reason result)))
      (is (nil? (:account result)))
      (is (nil? (:outcome result)))
      (is (nil? (:retain result)))
      (is (empty? (:effects result))))))

(deftest rejected-nonfinite-material-replays-by-raw-bits
  (let [nan-a (Double/longBitsToDouble 9221120237041090561)
        nan-b (Double/longBitsToDouble 9221120237041090562)
        operation (origin 10 (assoc material :mass-kg nan-a))
        rejected (account/apply-operation nil {} operation)
        history (retain {} rejected)
        retry (account/apply-operation nil history operation)
        conflict (account/apply-operation nil history
                                          (assoc-in operation [:material :mass-kg] nan-b))]
    (is (= :invalid-request (:reason rejected)))
    (is (law/history? history))
    (is (= 1 (count history)))
    (is (= :replayed (:disposition retry)))
    (is (= (:outcome rejected) (:outcome retry)))
    (is (nil? (:retain retry)))
    (is (= :conflict (:disposition conflict)))
    (is (= (:outcome rejected) (:outcome conflict)))
    (is (nil? (:retain conflict)))
    (is (nil? (:account rejected)))
    (is (= 9221120237041090561
           (Double/doubleToRawLongBits (get-in history [(:op-id operation) :request :material :mass-kg]))))))

(deftest rejected-unordered-payloads-retain-raw-bits-in-members-and-keys
  (let [nan-a (Double/longBitsToDouble 9221120237041090561)
        nan-b (Double/longBitsToDouble 9221120237041090562)]
    (doseq [[first-payload changed-payload]
            [[#{-0.0} #{0.0}]
             [{:nested {-0.0 :value}} {:nested {0.0 :value}}]
             [#{nan-a} #{nan-b}]
             [{:nested {nan-a :value}} {:nested {nan-b :value}}]
             [{:nested #{[-0.0]}} {:nested #{[0.0]}}]]]
      (let [operation (origin 10 first-payload)
            rejected (account/apply-operation nil {} operation)
            history (retain {} rejected)
            retry (account/apply-operation nil history operation)
            conflicting (account/apply-operation nil history (origin 10 changed-payload))]
        (is (= :invalid-request (:reason rejected)))
        (is (= :replayed (:disposition retry)))
        (is (= :conflict (:disposition conflicting)))
        (is (= :conflicting-reuse (:reason conflicting)))
        (is (= (:outcome rejected) (:outcome retry) (:outcome conflicting)))
        (is (nil? (:retain retry)))
        (is (nil? (:retain conflicting)))
        (is (empty? (:effects retry)))
        (is (empty? (:effects conflicting)))
        (is (nil? (:account retry)))
        (is (nil? (:account conflicting)))))))

(deftest closure-exports-once-and-new-requests-cannot-reopen
  (let [close-op (request :close 1 1 {:reason :material-changed})
        result (account/apply-operation opened {} close-op)
        closed (:account result)
        history (retain {} result)]
    (is (law/result? result))
    (is (= :accepted (:disposition result)))
    (is (= {:U 0 :S 0 :B 0 :W 0} (:stocks closed)))
    (is (= :unresolved-physical-boundary (get-in closed [:terminal :destination])))
    (is (= :material-changed (get-in closed [:terminal :reason])))
    (is (= opening-stocks (get-in closed [:terminal :inventory])))
    (is (= (:opening opened) (:exported closed)))
    (doseq [operation [(origin 11 material)
                       (request :extent 2 2 {:amounts {:q 0 :g 0 :L 0}})
                       (request :close 3 2 {:reason :parent-removed})]]
      (let [rejected (account/apply-operation closed history operation)]
        (is (= :closed (:reason rejected)))
        (is (= closed (:account rejected)))
        (is (empty? (:effects rejected)))
        (is (= operation (get-in rejected [:retain :request])))))))
