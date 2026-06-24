(ns law.registry
  "Registry for resources of a single type, governed by a Contract.
   Guarantees:
   - every item satisfies resource-contract
   - ids are unique
   - index is consistent with items"
  (:require
    [law.contract :as contract]))

(defrecord Registry
  [resource-contract items index])

(defn ->registry
  "Create an empty Registry for resources governed by `resource-contract`."
  [resource-contract]
  (->Registry resource-contract [] {}))

(defn- ensure-unique-id!
  [index id resource]
  (when (contains? index id)
    (throw (ex-info "Registry duplicate id"
                    {:kind    ::duplicate-id
                     :id      id
                     :attempt resource}))))

(defn- validate-resource!
  [resource-contract resource]
  (let [id (:id resource)]
    (when (nil? id)
      (throw (ex-info "Registry resource missing :id"
                      {:kind     ::missing-id
                       :resource resource})))
    (let [claim {:id       id
                 :shape-id (:shape-id resource-contract)
                 :value    resource}
          res   (contract/validate resource-contract claim)]
      (when (not= contract/ok res)
        (throw (ex-info "Registry contract violation"
                        {:kind     ::contract-violation
                         :issues   (:issues res)
                         :resource resource}))))))

(defn add
  "Add a resource to the registry. Validates and enforces unique id."
  [^Registry reg resource]
  (let [{:keys [resource-contract items index]} reg
        id (:id resource)]
    (validate-resource! resource-contract resource)
    (ensure-unique-id! index id resource)
    (let [idx (count items)]
      (->Registry
        resource-contract
        (conj items resource)
        (assoc index id idx)))))

(defn get-by-id
  "Lookup a resource by id. Returns nil if not present."
  [^Registry reg id]
  (let [{:keys [items index]} reg
        idx (get index id)]
    (when (some? idx)
      (nth items idx))))

(defn rebuild-index
  "Rebuild the id -> idx index from the items vector."
  [^Registry reg]
  (let [{:keys [resource-contract items]} reg
        new-index (into {}
                        (map-indexed
                          (fn [idx {:keys [id]}]
                            (when (nil? id)
                              (throw (ex-info "Registry item missing :id while rebuilding index"
                                              {:kind  ::missing-id
                                               :index idx})))
                            [id idx])
                          items))]
    (->Registry resource-contract items new-index)))
