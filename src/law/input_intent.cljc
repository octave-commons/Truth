(ns law.input-intent
  "Named boundaries for opt-in contextual input on the existing host queue."
  (:require
   [malli.core :as m]))

(def registry
  "EDN schema data for contextual payloads, iteration context and submission.

   Offset values remain raw until the manual action's observer-aware boundary.
   The entry schema validates payload shape; the host owns nominal dispatch."
  {::contextual-entry [:map {:closed true}
                       [:update 'ifn?]]
   ::iteration-context [:map {:closed true}
                        [:manual? :boolean]
                        [:focus-offset :any]]
   ::submission-options [:map {:closed true}
                         [:submit-contextual! {:optional true} 'fn?]]})

(def ^:private schema-options
  {:registry (merge (m/default-schemas) registry)})

(def contextual-entry?
  "Compiled payload-shape validator, applied after the host record discriminator."
  (m/validator ::contextual-entry schema-options))

(def iteration-context?
  "Compiled structural validator that preserves explicit invalid offset values."
  (m/validator ::iteration-context schema-options))

(def submission-options?
  "Compiled opt-in validator: absent is legacy; supplied nil is invalid."
  (m/validator ::submission-options schema-options))
