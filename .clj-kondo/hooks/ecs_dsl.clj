(ns hooks.ecs-dsl
  "clj-kondo hooks teaching the linter about the domain.ecs.dsl macros.
   Each macro is rewritten into the def/defn forms it actually expands to, so
   clj-kondo resolves the generated vars (->event ctors, name? preds, the
   world/rows/event bindings) instead of reporting them as unresolved symbols.
   Keep in sync with src/domain/ecs/dsl.clj."
  (:require [clj-kondo.hooks-api :as api]))

(defn- sym [s] (api/token-node (symbol s)))
(defn- vec-of [syms] (api/vector-node (mapv sym syms)))
(defn- defn* [name-node argvec body] (api/list-node (list* (sym "defn") name-node argvec body)))

(defn defcomponent
  "(defcomponent name doc schema) => name, name-schema, name-validator, name?"
  [{:keys [node]}]
  (let [[_ name _doc schema] (:children node)
        s (str (api/sexpr name))]
    {:node (api/list-node
            [(sym "do")
             (api/list-node [(sym "def") name (api/token-node nil)])
             (api/list-node [(sym "def") (sym (str s "-schema")) schema])
             (api/list-node [(sym "def") (sym (str s "-validator")) (api/token-node nil)])
             (defn* (sym (str s "?")) (vec-of ["value"]) [(sym "value")])])}))

(defn defevent
  "(defevent name doc payload-schema opts) => name, *-payload-schema,
   *-payload-validator, ->name ctor, emit-name."
  [{:keys [node]}]
  (let [[_ name _doc payload-schema] (:children node)
        s (str (api/sexpr name))]
    {:node (api/list-node
            [(sym "do")
             (api/list-node [(sym "def") name (api/token-node nil)])
             (api/list-node [(sym "def") (sym (str s "-payload-schema")) payload-schema])
             (api/list-node [(sym "def") (sym (str s "-payload-validator")) (api/token-node nil)])
             ;; ctor/emitter are multi-arity at runtime — varargs avoids
             ;; spurious arity warnings at call sites.
             (defn* (sym (str "->" s)) (vec-of ["&" "_args"]) [])
             (defn* (sym (str "emit-" s)) (vec-of ["&" "_args"]) [])])}))

(defn defsystem
  "(defsystem name doc opts [world rows] & body) => (defn name [world]
   (let [rows nil] body))."
  [{:keys [node]}]
  (let [[_ name _doc _opts bindings & body] (:children node)
        [world rows] (:children bindings)]
    {:node (defn* name (api/vector-node [world])
             [(api/list-node
               (list* (sym "let")
                      (api/vector-node [rows (api/token-node nil)])
                      body))])}))

(defn- two-arg-defn
  "Rewrite a (defmacro name doc <event-kind?> [a b] & body) form whose binding
   vector is the 4th or 5th child into (defn name [a b] body)."
  [node bindings-idx]
  (let [children (:children node)
        name (nth children 1)
        bindings (nth children bindings-idx)
        body (drop (inc bindings-idx) children)]
    {:node (defn* name bindings body)}))

;; defreaction/defrewind: (name doc event-kind [world event] & body) -> bindings at idx 4
(defn defreaction [{:keys [node]}] (two-arg-defn node 4))
(defn defrewind   [{:keys [node]}] (two-arg-defn node 4))
;; defprojection: (name doc event-kind {opts} [acc event] & body) -> bindings at idx 5
(defn defprojection [{:keys [node]}] (two-arg-defn node 5))
;; defaggregate: (name doc {opts} [acc event] & body) -> bindings at idx 4
(defn defaggregate  [{:keys [node]}] (two-arg-defn node 4))
