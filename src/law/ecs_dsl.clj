(ns law.ecs-dsl
  "Malli contracts for the ECS + event ledger DSL."
  (:require
    [malli.core :as m]))

(def SimpleName
  [:fn {:error/message "Expected a simple symbol"}
   simple-symbol?])

(def DocString
  [:and string? [:fn {:error/message "Docstring must be non-blank"}
                 #(not (clojure.string/blank? %))]])

(def ComponentRef
  [:or keyword? SimpleName])

(def ComponentDef
  [:map
   [:name SimpleName]
   [:doc DocString]
   [:schema any?]
   [:key keyword?]])

(def EventOptions
  [:map
   [:entity-count {:optional true} pos-int?]
   [:reversible? {:optional true} boolean?]])

(def EventDef
  [:map
   [:name SimpleName]
   [:doc DocString]
   [:payload-schema any?]
   [:key keyword?]
   [:options EventOptions]])

(def SystemDef
  [:map
   [:name SimpleName]
   [:doc DocString]
   [:query [:vector ComponentRef]]])

(def validate-component-def
  (m/validator ComponentDef))

(def validate-event-def
  (m/validator EventDef))

(def validate-system-def
  (m/validator SystemDef))

(defn assert-component-def!
  "Throw if a component DSL form is invalid."
  [x]
  (when-not (validate-component-def x)
    (throw (ex-info "Invalid component definition"
                    {:kind ::invalid-component-def
                     :value x})))
  x)

(defn assert-event-def!
  "Throw if an event DSL form is invalid."
  [x]
  (when-not (validate-event-def x)
    (throw (ex-info "Invalid event definition"
                    {:kind ::invalid-event-def
                     :value x})))
  x)

(defn assert-system-def!
  "Throw if a system DSL form is invalid."
  [x]
  (when-not (validate-system-def x)
    (throw (ex-info "Invalid system definition"
                    {:kind ::invalid-system-def
                     :value x})))
  x)
