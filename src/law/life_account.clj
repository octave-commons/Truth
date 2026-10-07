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
(def ^:private result-shape? (m/validator ::result options))

(def ^:export budget-result?
  "Validate the named numerical-budget result shape."
  (m/validator ::budget-result options))

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

   A rejected request need not pass its payload schema. Its valid identity and
   matching outcome remain mandatory; persistence is the caller's obligation."
  [value]
  (and (history-shape? value)
       (every? (fn [[op-id {:keys [request outcome]}]]
                 (and (identity? request)
                      (= op-id (:op-id request) (:op-id outcome))))
               value)))

(defn result?
  "Validate the named transition result and any returned current account."
  [value]
  (and (result-shape? value)
       (or (nil? (:account value)) (account? (:account value)))))
