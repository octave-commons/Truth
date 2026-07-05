(ns shape.core
  "Core category-level shapes for the engine.
   - Shape: describes form/kind, never validity
   - Claim: assertion that a value instantiates a Shape in some context
   - simple id helpers for resources and shapes.
   These are intentionally pure data constructors with no validation."
  (:import (java.util UUID)))

;; --- Id helpers -------------------------------------------------------------

(defn new-shape-id
  "Allocate a new opaque identifier for a Shape."
  []
  (UUID/randomUUID))

(defn new-claim-id
  "Allocate a new opaque identifier for a Claim."
  []
  (UUID/randomUUID))

(defn new-resource-id
  "Allocate a new opaque identifier for any resource (contract, registry, etc.)."
  []
  (UUID/randomUUID))

;; --- Records ----------------------------------------------------------------

(defrecord Shape
           [id kind form name description])

(defn ->shape
  "Construct a Shape from a map. Required keys: :id, :kind, :form."
  [{:keys [id kind form name description] :as m}]
  (when-not (and id kind form)
    (throw (ex-info "Shape requires :id, :kind, and :form"
                    {:kind ::invalid-shape :shape m})))
  (->Shape id kind form name description))

(defrecord Claim
           [id shape-id value context asserted-by])

(defn ->claim
  "Construct a Claim tying a Shape to a concrete value in a context.
   Required keys: :id, :shape-id, :value."
  [{:keys [id shape-id value context asserted-by] :as m}]
  (when-not (and id shape-id (contains? m :value))
    (throw (ex-info "Claim requires :id, :shape-id, and :value"
                    {:kind ::invalid-claim :claim m})))
  (->Claim id shape-id value context asserted-by))
