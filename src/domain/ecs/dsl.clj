(ns domain.ecs.dsl
  "Thin ECS + event ledger DSL.
   Macros declare component keys, event constructors, systems, reactions,
   projections, aggregates, and rewind handlers.
   Runtime stays plain ECS maps and pure functions."
  (:require
   [malli.core :as m]
   [law.ecs-dsl :as law]
   [domain.ecs.core :as ecs]
   [domain.ecs.event :as evt]))

(defn query-rows
  "Return [eid {component-key value ...}] rows for every entity
   that has all requested component keys."
  [world component-keys]
  (mapv (fn [eid]
          [eid (into {}
                     (map (fn [k] [k (ecs/get-component world eid k)]))
                     component-keys)])
        (apply ecs/entities-with world component-keys)))

(defn install-reaction
  "Install a reaction var using its :event/kind metadata."
  [world reaction-var]
  (let [kind (:event/kind (meta reaction-var))]
    (when-not kind
      (throw (ex-info "Reaction var is missing :event/kind metadata"
                      {:kind ::missing-event-kind
                       :var reaction-var})))
    (evt/register-handler world kind @reaction-var)))

(defn- component-key
  [sym]
  (keyword "component" (name sym)))

(defn- event-key
  [sym]
  (keyword "event" (name sym)))

(defmacro defcomponent
  "Define a component key, its schema, validator, and predicate var."
  [nm doc schema]
  (let [k             (component-key nm)
        schema-sym    (symbol (str nm "-schema"))
        validator-sym (symbol (str nm "-validator"))
        pred-sym      (symbol (str nm "?"))]
    (law/assert-component-def!
     {:name nm
      :doc doc
      :schema schema
      :key k})
    `(do
       (def ~schema-sym ~schema)
       (def ~validator-sym (m/validator ~schema-sym))
       (def ~nm ~k)
       (alter-meta! (var ~nm) assoc
                    :doc ~doc
                    :ecs/kind :component
                    :ecs/key ~k
                    :ecs/schema '~schema-sym)
       (defn ~pred-sym
         ~doc
         [value#]
         (~validator-sym value#)))))

(defmacro defevent
  "Define an event kind, payload schema, constructor, and emitter."
  [nm doc payload-schema options]
  (let [k             (event-key nm)
        schema-sym    (symbol (str nm "-payload-schema"))
        validator-sym (symbol (str nm "-payload-validator"))
        ctor-sym      (symbol (str "->" nm))
        emit-sym      (symbol (str "emit-" nm))
        entity-count  (:entity-count options)
        rev?          (:reversible? options false)]
    (law/assert-event-def!
     {:name nm
      :doc doc
      :payload-schema payload-schema
      :key k
      :options options})
    `(do
       (def ~schema-sym ~payload-schema)
       (def ~validator-sym (m/validator ~schema-sym))
       (def ~nm ~k)
       (alter-meta! (var ~nm) assoc
                    :doc ~doc
                    :ecs/kind :event
                    :event/key ~k
                    :event/payload-schema '~schema-sym
                    :event/entity-count ~entity-count
                    :event/reversible? ~rev?)
       (defn ~ctor-sym
         {:doc ~doc
          :ecs/kind :event-constructor}
         ([tick# entities# payload#]
          (~ctor-sym tick# entities# payload# nil))
         ([tick# entities# payload# cause#]
          (let [~'entities-set (set entities#)]
            ~@(when entity-count
                [(list 'when-not (list '= entity-count '(count entities-set))
                       (list 'throw (list 'ex-info "Invalid entity count for event"
                                          {:kind ::invalid-entity-count
                                           :event k
                                           :expected entity-count
                                           :actual '(count entities-set)})))])
            (when-not (~validator-sym payload#)
              (throw (ex-info "Invalid event payload"
                              {:kind ::invalid-event-payload
                               :event ~k
                               :payload payload#})))
            (evt/->event {:tick tick#
                          :kind ~k
                          :entities ~'entities-set
                          :payload payload#
                          :cause cause#}))))
       (defn ~emit-sym
         {:doc ~doc
          :ecs/kind :event-emitter}
         ([world# entities# payload#]
          (~emit-sym world# entities# payload# nil))
         ([world# entities# payload# cause#]
          (evt/dispatch world#
                        (~ctor-sym (:tick world#)
                                   entities#
                                   payload#
                                   cause#)))))))

(defmacro defsystem
  "Define an ECS query system with its read-only query and body."
  [nm doc {:keys [query]} [world-sym rows-sym] & body]
  (law/assert-system-def!
   {:name nm
    :doc doc
    :query query})
  `(defn ~nm
     {:doc ~doc
      :ecs/kind :system
      :ecs/query '~query}
     [~world-sym]
     (let [~rows-sym (query-rows ~world-sym [~@query])]
       ~@body)))

(defmacro defreaction
  "Define an event reaction handler for `event-kind`."
  [nm doc event-kind [world-sym event-sym] & body]
  `(defn ~nm
     {:doc ~doc
      :ecs/kind :reaction
      :event/kind ~event-kind}
     [~world-sym ~event-sym]
     ~@body))

(defmacro defprojection
  "Define a ledger projection over events of `event-kind`, starting from `init`."
  [nm doc event-kind {:keys [init]} [acc-sym event-sym] & body]
  (let [event-k (when (resolve event-kind)
                  (-> event-kind resolve deref))]
    `(defn ~nm
       {:doc ~doc
        :ecs/kind :projection
        :projection/event-kind ~(or event-k event-kind)
        :projection/init (fn [] ~init)}
       [~acc-sym ~event-sym]
       ~@body)))

(defmacro defaggregate
  "Define a ledger aggregate tracking a set of event kinds from `init`."
  [nm doc {:keys [tracked init]} [acc-sym event-sym] & body]
  (let [tracked-ks (mapv #(when (resolve %)
                            (-> % resolve deref))
                         tracked)]
    `(defn ~nm
       {:doc ~doc
        :ecs/kind :aggregate
        :aggregate/tracked ~tracked-ks
        :aggregate/init ~init}
       [~acc-sym ~event-sym]
       ~@body)))

(defmacro defrewind
  "Define a rewind handler for events of `event-kind`."
  [nm doc event-kind [world-sym event-sym] & body]
  `(defn ~nm
     {:doc ~doc
      :ecs/kind :rewind
      :event/kind ~event-kind}
     [~world-sym ~event-sym]
     ~@body))
