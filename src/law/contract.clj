(ns law.contract
  "Contracts govern which Claims over Shapes are admissible.
   They do not perform side-effects; they return facts about validity
   that other layers can record in the ledger."
  (:require
    ^:clj-kondo/ignore [shape.core :as shape]
    [clojure.set :as set]))

(defrecord Contract
  [id shape-id kind schema nm description on-true on-false on-any])

(defn ->contract
  "Construct a Contract. Required keys:
   - :id
   - :shape-id
   - :kind       (e.g. :type or :quality)
   - :schema     (validation description)
   Optional: :name, :description, :on-true, :on-false, :on-any"
  [{:keys [id shape-id kind schema description on-true on-false on-any] nm :name :as m}]
  (when-not (and id shape-id kind schema)
    (throw (ex-info "Contract requires :id, :shape-id, :kind, and :schema"
                    {:kind ::invalid-contract :contract m})))
  (->Contract id shape-id kind schema nm description on-true on-false on-any))

;; --- Validation -------------------------------------------------------------

(def ^:const ok ::ok)
(def ^:const violation ::violation)

(defn- value-keys
  "Utility: safe read of top-level keys for a claim value; non-maps return empty set."
  [v]
  (if (map? v) (set (keys v)) #{}))

(defn- validate-schema
  "Validate `value` against simple predicate schema:
   schema = {k predicate}, all predicates must return truthy.
   Returns nil on success, or a seq of issue maps."
  [schema value]
  (reduce
    (fn [acc [k pred]]
      (let [v (get value k ::missing)]
        (cond
          (= v ::missing)
          (conj acc {:path [k] :value nil :reason :missing})

          (not (pred v))
          (conj acc {:path [k] :value v :reason :invalid})

          :else acc)))
    []
    schema))

(defn- type-extra-keys
  "For :type contracts, extra keys beyond schema are violations."
  [schema value]
  (let [allowed (set (keys schema))
        actual  (value-keys value)
        extra   (set/difference actual allowed)]
    (map (fn [k]
           {:path   [k]
            :value  (get value k)
            :reason :unexpected-key})
         extra)))

(defn validate
  "Validate a Claim against a Contract.
   Returns either ::ok or a violation map.
   For :type contracts, no extra keys are allowed.
   For :quality contracts, extra keys are allowed if explicit constraints hold."
  [^Contract c ^shape.core.Claim claim]
  (let [{:keys [kind schema]} c
        value        (:value claim)
        base-issues  (validate-schema schema value)
        extra-issues (if (= kind :type)
                       (type-extra-keys schema value)
                       [])
        issues       (into base-issues extra-issues)]
    (if (seq issues)
      {:result   violation
       :contract c
       :claim    claim
       :issues   issues}
      ok)))
