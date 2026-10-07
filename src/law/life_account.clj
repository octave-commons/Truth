(ns law.life-account
  "Named contracts for the pure integer carbon account.

   This JVM boundary retains arbitrary-precision integers and binary64 material
   values. It neither discovers habitat conditions nor allocates ECS entities."
  (:require
   [law.composition :as composition]
   [malli.core :as m]))

(def ^:export registry
  "Serializable, registry-keyed Malli shapes for account transactions.

   Cross-field conservation and identity checks are named predicates below;
   exact material conversion and sum tolerance belong to the domain kernel."
  {::units [:and 'integer? [:>= 0]]
   ::positive-units [:and ::units [:> 0]]
   ::binary64 [:double {:min (- Double/MAX_VALUE) :max Double/MAX_VALUE}]
   ::fraction [:double {:min 0.0 :max 1.0}]
   ::element (into [:enum] (sort composition/element-set))
   ::material [:map {:closed true}
               [:mass-kg [:and ::binary64 [:> 0.0]]]
               [:composition [:map-of {:min 1} ::element ::fraction]]]
   ::account-id [:tuple [:= :life-origin/v1] ::units :uuid]
   ::op-id [:tuple ::account-id :keyword ::units]
   ::stocks [:map {:closed true}
             [:U ::units] [:S ::units] [:B ::units] [:W ::units]]
   ::amounts [:map {:closed true}
              [:q ::units] [:g ::units] [:L ::units]]
   ::rational [:map {:closed true}
               [:numerator ::units] [:denominator ::positive-units]]
   ::bits [:re "^[0-9a-f]{16}$"]
   ::witness [:map {:closed true}
              [:mass-bits ::bits]
              [:composition-bits [:map-of ::element ::bits]]
              [:discarded-unit ::rational]]
   ::budget [:map {:closed true}
             [:carbon-units ::units]
             [:allocation ::units]
             [:origin-units ::units]
             [:stocks ::stocks]
             [:witness ::witness]]
   ::terminal [:map {:closed true}
               [:reason :keyword]
               [:destination [:= :unresolved-physical-boundary]]
               [:inventory ::stocks]]
   ::account [:map {:closed true}
              [:id ::account-id]
              [:lineage [:tuple ::account-id [:= :cohort] [:= 0]]]
              [:revision ::positive-units]
              [:status [:enum :open :closed]]
              [:opening [:and ::positive-units [:<= 1000000000000000]]]
              [:stocks ::stocks]
              [:exported ::units]
              [:source ::material]
              [:witness ::witness]
              [:terminal [:maybe ::terminal]]]
   ::identity [:map
               [:op-id ::op-id]
               [:account ::account-id]]
   ::request [:map {:closed true}
              [:op-id ::op-id]
              [:account ::account-id]
              [:expected-revision ::units]
              [:kind [:enum :origin :extent :close]]
              [:cause :uuid]
              [:interval [:maybe [:tuple ::binary64 ::binary64]]]
              [:rule-version [:= :life-origin/v1]]
              [:material {:optional true} ::material]
              [:amounts {:optional true} ::amounts]
              [:reason {:optional true} :keyword]]
   ::outcome [:map {:closed true}
              [:op-id ::op-id]
              [:accepted? :boolean]
              [:reason :keyword]
              [:revision ::units]]
   ::retention [:map {:closed true}
                [:request :map]
                [:outcome ::outcome]]
   ::history [:map-of ::op-id ::retention]
   ::operation-context [:map {:closed true}
                        [:account [:maybe ::account]]
                        [:history ::history]
                        [:requested-account ::account-id]]
   ::effect [:map
             [:kind [:enum :origin :extent :close]]
             [:account ::account-id]]
   ::result [:map {:closed true}
             [:disposition [:enum :accepted :rejected :replayed :conflict]]
             [:reason :keyword]
             [:account [:maybe ::account]]
             [:outcome [:maybe ::outcome]]
             [:retain [:maybe ::retention]]
             [:effects [:vector ::effect]]]
   ::budget-result [:map {:closed true}
                    [:accepted? :boolean]
                    [:reason :keyword]
                    [:budget {:optional true} ::budget]]})

(def ^:private options
  {:registry (merge (m/default-schemas) registry)})

(def ^:private material-shape? (m/validator ::material options))
(def ^:private identity-shape? (m/validator ::identity options))
(def ^:private account-shape? (m/validator ::account options))
(def ^:private request-shape? (m/validator ::request options))
(def ^:private history-shape? (m/validator ::history options))
(def ^:private context-shape? (m/validator ::operation-context options))
(def ^:private result-shape? (m/validator ::result options))

(def ^:private budget-result-shape? (m/validator ::budget-result options))

(defn- fractional-unit? [{numerator-value :numerator denominator-value :denominator}]
  (< numerator-value denominator-value))

(defn budget-result?
  "Validate a numerical budget, including the strictly fractional remainder.

   A zero-origin rejection may expose its computed budget. Invalid material
   exposes no budget; an accepted result always has positive origin stock."
  [value]
  (and (budget-result-shape? value)
       (if-let [{:keys [allocation origin-units stocks witness]} (:budget value)]
         (and (<= allocation 1000000000000000)
              (= allocation (reduce +' 0 (vals stocks)))
              (zero? (:U stocks))
              (= origin-units (:B stocks))
              (fractional-unit? (:discarded-unit witness))
              (= (:accepted? value) (pos? origin-units)))
         (false? (:accepted? value)))))

(defn material?
  "Validate finite binary64 material using canonical elemental membership.

   This shape check reuses mass-fraction semantics; it does not claim the exact
   sum-to-unity tolerance or a spendable carbon inventory has been established."
  [value]
  (and (material-shape? value)
       (every? composition/mass-fraction? (vals (:composition value)))))

(defn identity?
  "Recognize an operation identity that can have a retained outcome.

   Remaining payload fields may be invalid; rejection of such a payload still
   returns an immutable retention proposal for this well-formed identity."
  [request]
  (and (identity-shape? request)
       (= (:account request) (first (:op-id request)))))

(defn account?
  "Validate exact stocks, conservation, lineage and terminal-state invariants.

   No physical signature, habitat or global parent-slot lookup is performed."
  [value]
  (and (account-shape? value)
       (material? (:source value))
       (fractional-unit? (get-in value [:witness :discarded-unit]))
       (= (:lineage value) [(:id value) :cohort 0])
       (= (:opening value)
          (+' (:exported value) (reduce +' 0 (vals (:stocks value)))))
       (case (:status value)
         :open (and (zero? (:exported value)) (nil? (:terminal value)))
         :closed (and (every? zero? (vals (:stocks value)))
                      (= (:opening value) (:exported value)
                         (reduce +' 0 (vals (get-in value [:terminal :inventory]))))))))

(defn request?
  "Validate operation payload shape without deciding current-state admission.

   Extent bounds against stocks, exact material sums and revision admission are
   domain decisions. There is no clock or habitat policy in this predicate."
  [value]
  (and (request-shape? value)
       (identity? value)
       (or (nil? (:interval value)) (apply <= (:interval value)))
       (case (:kind value)
         :origin (and (= :origin (second (:op-id value)))
                      (= (:cause value) (nth (:account value) 2))
                      (nil? (:interval value))
                      (material? (:material value))
                      (not-any? #(contains? value %) [:amounts :reason]))
         :extent (and (contains? value :amounts)
                      (not-any? #(contains? value %) [:material :reason]))
         :close (and (contains? value :reason)
                     (not-any? #(contains? value %) [:material :amounts])))))

(defn history?
  "Validate supplied immutable outcome entries and their operation keys.

   Accepted entries require a well-formed request and an adjacent accepted
   revision. Rejected payloads may be invalid. This checks supplied structure,
   not past stock admission, physical cause or completeness of global history."
  [value]
  (and (history-shape? value)
       (every? (fn [[op-id {:keys [request outcome]}]]
                 (and (identity? request)
                      (= op-id (:op-id request) (:op-id outcome))
                      (or (false? (:accepted? outcome))
                          (and (request? request)
                               (= (:revision outcome) (inc' (:expected-revision request)))
                               (= (:reason outcome)
                                  (case (:kind request)
                                    :origin :origin
                                    :extent :extent
                                    :close :closed-account))
                               (or (not= :origin (:kind request))
                                   (zero? (:expected-revision request)))))))
               value)))

(defn result?
  "Validate current state, retained outcome and disposition relationships.

   Historical retries may report an older revision than the current account;
   new accepted operations must report the resulting current revision."
  [value]
  (let [{:keys [account outcome retain effects disposition]} value]
    (and (result-shape? value)
         (or (nil? account) (account? account))
         (or (nil? retain)
             (and (= outcome (:outcome retain))
                  (history? {(:op-id outcome) retain})))
         (case disposition
           :accepted (and account retain (true? (:accepted? outcome))
                          (= (:revision account) (:revision outcome))
                          (= (:reason value) (:reason outcome)))
           :rejected (and (empty? effects)
                          (if outcome
                            (and retain (false? (:accepted? outcome)))
                            (nil? retain)))
           (:replayed :conflict) (and outcome (nil? retain) (empty? effects))))))

(defn- payload-key [value]
  (cond
    (double? value) [:binary64 (Double/doubleToRawLongBits value)]
    (instance? Float value) [:binary32 (Float/floatToRawIntBits value)]
    (map? value) [:map (frequencies (map (fn [[field item]]
                                           [(payload-key field) (payload-key item)])
                                         value))]
    (set? value) [:set (frequencies (map payload-key value))]
    (vector? value) [:vector (mapv payload-key value)]
    (list? value) [:list (mapv payload-key value)]
    (sequential? value) [:sequence (mapv payload-key value)]
    :else [:scalar value]))

(defn same-payload?
  "Compare immutable Clojure data with raw floating-point bits at every position.

   Tags prevent user values from colliding with encoded nodes. Frequencies retain
   unordered multiplicity, including distinct NaN keys/members with equal bits.
   This private comparison form is not serialization or a persisted identifier;
   opaque or mutable objects are outside the portable-request contract."
  [left right]
  (= (payload-key left) (payload-key right)))

(defn- entry-consistent? [account account-id {:keys [request outcome]}]
  (or (not= account-id (:account request))
      (false? (:accepted? outcome))
      (and account
           (<= (:revision outcome) (:revision account))
           (or (not= :close (:kind request))
               (and (= :closed (:status account))
                    (= (:revision outcome) (:revision account))))
           (or (not= :origin (:kind request))
               (same-payload? (:material request) (:source account))))))

(defn operation-context?
  "Reject locally provable contradictions between supplied account and history.

   Same-account acceptance requires current state at least as recent, terminal
   state at the accepted close revision, and unchanged opening material.
   Account/history retain their separate validators; absence of entries never
   proves completeness."
  [{:keys [account history requested-account] :as context}]
  (and (context-shape? context)
       (every? #(entry-consistent? account (if account (:id account) requested-account) %)
               (vals history))))
