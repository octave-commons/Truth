(ns law.ecs-dsl
  "Malli contracts for the ECS + event ledger DSL."
  (:require
   [clojure.string :as str]
   [malli.core :as m]))

;; Intentional: Malli schema definitions in this namespace use PascalCase to
;; mirror the domain-model convention for schema/type names; renaming them
;; would break every spec and consumer that references them.
#_{:splint/disable [naming/lisp-case]}
(def SimpleName
  [:fn {:error/message "Expected a simple symbol"}
   simple-symbol?])

;; Intentional: PascalCase Malli schema name.
#_{:splint/disable [naming/lisp-case]}
(def DocString
  [:and string? [:fn {:error/message "Docstring must be non-blank"}
                 #(not (clojure.string/blank? %))]])

;; Intentional: PascalCase Malli schema name.
#_{:splint/disable [naming/lisp-case]}
(def ComponentRef
  [:or keyword? SimpleName])

;; Intentional: PascalCase Malli schema name.
#_{:splint/disable [naming/lisp-case]}
(def ComponentDef
  [:map
   [:name SimpleName]
   [:doc DocString]
   [:schema any?]
   [:key keyword?]])

;; Intentional: PascalCase Malli schema name.
#_{:splint/disable [naming/lisp-case]}
(def EventOptions
  [:map
   [:entity-count {:optional true} pos-int?]
   [:reversible? {:optional true} boolean?]])

;; Intentional: PascalCase Malli schema name.
#_{:splint/disable [naming/lisp-case]}
(def EventDef
  [:map
   [:name SimpleName]
   [:doc DocString]
   [:payload-schema any?]
   [:key keyword?]
   [:options EventOptions]])

;; Intentional: PascalCase Malli schema name.
#_{:splint/disable [naming/lisp-case]}
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
