(ns domain.life-account
  "Exact carbon partitions and immutable, caller-owned account transitions.

   This kernel does not validate habitat, allocate entities, discover causes or
   persist outcomes. See kanban/tasks/life-account-origin-kernel.md."
  (:require
   [law.life-account :as law])
  (:import [java.math BigInteger]))

(defn- power-of-two [exponent]
  (.shiftLeft BigInteger/ONE (int exponent)))

(defn- exact-binary64 [value]
  (let [bits (Double/doubleToRawLongBits value)
        exponent (bit-and 2047 (unsigned-bit-shift-right bits 52))
        fraction (bit-and bits 4503599627370495)
        significand (if (zero? exponent) fraction (+ 4503599627370496 fraction))
        shift (if (zero? exponent) -1074 (- exponent 1075))
        signed (if (neg? bits) (- significand) significand)]
    (if (neg? shift)
      (/ (bigint signed) (power-of-two (- shift)))
      (*' signed (power-of-two shift)))))

(defn- fraction-data [value]
  (if (ratio? value)
    {:numerator (numerator value) :denominator (denominator value)}
    {:numerator value :denominator 1}))

(defn- raw-bits [value]
  (format "%016x" (Double/doubleToRawLongBits value)))

(defn- material-budget [{:keys [mass-kg composition]}]
  (let [exact-units (*' (exact-binary64 mass-kg)
                        (exact-binary64 (:C composition)) 1000000000000000N)
        {numerator-value :numerator denominator-value :denominator} (fraction-data exact-units)
        carbon (quot numerator-value denominator-value)
        allocation (min (quot carbon 1000000) 1000000000000000)
        substrate (quot allocation 100)
        origin (quot substrate 1000)]
    {:carbon-units carbon :allocation allocation :origin-units origin
     :stocks {:U 0 :S (- substrate origin) :B origin :W (- allocation substrate)}
     :witness {:mass-bits (raw-bits mass-kg)
               :composition-bits (into {} (map (fn [[element value]]
                                                [element (raw-bits value)]))
                                       composition)
               :discarded-unit (fraction-data (- exact-units carbon))}}))

(defn- checked-budget [result]
  (if (law/budget-result? result)
    result
    (throw (ex-info "Invalid produced origin budget" {:type ::invalid-budget :result result}))))

(defn origin-budget
  "Return the exact integer origin partition and binary64 input witnesses.

   Physical inputs must be finite binary64 values with an exact fraction sum
   within 1/1000000 of unity. No renormalization or rounded double product is
   used. A zero origin exposes its numerical budget but is not admitted."
  [material]
  (checked-budget
   (cond
     (not (law/material? material))
     {:accepted? false :reason :invalid-material}

     (> (abs (- (reduce + 0 (map exact-binary64 (vals (:composition material)))) 1))
        1/1000000)
     {:accepted? false :reason :composition-sum}

     (not (pos? (get-in material [:composition :C] 0.0)))
     {:accepted? false :reason :missing-carbon}

     :else
     (let [budget (material-budget material)
           accepted? (pos? (:origin-units budget))]
       {:accepted? accepted? :reason (if accepted? :origin :zero-origin) :budget budget}))))

(defn- same-request? [left right]
  (cond
    (or (double? left) (double? right))
    (and (double? left) (double? right)
         (= (Double/doubleToRawLongBits left) (Double/doubleToRawLongBits right)))

    (and (map? left) (map? right))
    (and (= (set (keys left)) (set (keys right)))
         (every? (fn [[field value]] (same-request? value (get right field))) left))

    (and (sequential? left) (sequential? right))
    (and (= (count left) (count right)) (every? true? (map same-request? left right)))

    :else (= left right)))

(defn- checked-result [result]
  (if (law/result? result)
    result
    (throw (ex-info "Invalid produced account result" {:type ::invalid-result :result result}))))

(defn- diagnostic [account reason]
  {:disposition :rejected :reason reason :account account
   :outcome nil :retain nil :effects []})

(defn- decided [account request accepted? reason effects]
  (let [outcome {:op-id (:op-id request) :accepted? accepted? :reason reason
                 :revision (if account (:revision account) 0)}]
    {:disposition (if accepted? :accepted :rejected) :reason reason :account account
     :outcome outcome :retain {:request request :outcome outcome} :effects effects}))

(defn- replay [account entry request]
  (let [same? (same-request? (:request entry) request)]
    {:disposition (if same? :replayed :conflict)
     :reason (if same? :replayed :conflicting-reuse)
     :account account :outcome (:outcome entry) :retain nil :effects []}))

(defn- open-account [request]
  (let [{:keys [accepted? reason budget]} (origin-budget (:material request))]
    (if-not accepted?
      (decided nil request false reason [])
      (let [id (:account request)
            lineage [id :cohort 0]
            account {:id id :lineage lineage :revision 1 :status :open
                     :opening (:allocation budget) :stocks (:stocks budget)
                     :exported 0 :source (:material request) :witness (:witness budget)
                     :terminal nil}]
        (decided account request true :origin
                 [{:kind :origin :account id :lineage lineage
                   :carbon-units (:origin-units budget)}])))))

(defn- transfer [account request]
  (let [{:keys [q g L]} (:amounts request)
        {:keys [S B W]} (:stocks account)]
    (if (and (<= g q S) (<= L (+' B g)))
      (let [next-account (-> account
                             (update :revision inc')
                             (assoc-in [:stocks :S] (- S q))
                             (assoc-in [:stocks :B] (- (+' B g) L))
                             (assoc-in [:stocks :W] (+' W (- q g) L)))]
        (decided next-account request true :extent
                 [{:kind :extent :account (:id account) :amounts (:amounts request)}]))
      (decided account request false :extent-bounds []))))

(defn- close-account [account request]
  (let [inventory (:stocks account)
        exported (reduce +' 0 (vals inventory))
        next-account (-> account
                         (update :revision inc')
                         (assoc :status :closed :stocks {:U 0 :S 0 :B 0 :W 0}
                                :exported exported
                                :terminal {:reason (:reason request)
                                           :destination :unresolved-physical-boundary
                                           :inventory inventory}))]
    (decided next-account request true :closed-account
             [{:kind :close :account (:id account) :exported exported}])))

(defn- new-operation [account request]
  (cond
    (not (law/request? request)) (decided account request false :invalid-request [])
    (and account (not= (:id account) (:account request)))
    (decided account request false :account-mismatch [])
    (= :closed (:status account)) (decided account request false :closed [])
    (not= (:expected-revision request) (if account (:revision account) 0))
    (decided account request false :stale-revision [])
    :else
    (case (:kind request)
      :origin (if account
                (decided account request false :already-open [])
                (open-account request))
      :extent (if account (transfer account request) (decided nil request false :missing-account []))
      :close (if account (close-account account request) (decided nil request false :missing-account [])))))

(defn apply-operation
  "Propose an account transition with immutable outcome and retention data.

   Malformed caller state throws before transaction admission. For valid state,
   identity precedes bit-exact retry lookup, then payload/current-state checks.
   A new valid-key rejection must be retained by the later caller; this function
   neither persists it nor claims the supplied history is globally complete.
   Supplied accepted history cannot justify an absent current account."
  [account history request]
  (when (and (some? account) (not (law/account? account)))
    (throw (ex-info "Invalid supplied account" {:type ::invalid-account})))
  (when-not (law/history? history)
    (throw (ex-info "Invalid supplied history" {:type ::invalid-history})))
  (checked-result
   (if-not (law/identity? request)
     (diagnostic account :invalid-identity)
     (do
       (when-not (law/operation-context? {:account account :history history
                                         :requested-account (:account request)})
         (throw (ex-info "Invalid supplied account/history pair"
                         {:type ::invalid-account-history})))
       (if-let [entry (get history (:op-id request))]
         (replay account entry request)
         (new-operation account request))))))
