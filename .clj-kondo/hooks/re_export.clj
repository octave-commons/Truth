(ns hooks.re-export
  "clj-kondo hook for `(re-export src-ns/var)` macros that forward a public var
   into the current namespace. Rewrites the call into `(def name sym)` so the
   linter registers the exported name."
  (:require [clj-kondo.hooks-api :as api]))

(defn re-export
  [{:keys [node]}]
  (let [[_ sym-node] (:children node)
        s (str (api/sexpr sym-node))
        name (symbol (last (clojure.string/split s #"/")))]
    {:node (api/list-node [(api/token-node 'def)
                           (api/token-node name)
                           sym-node])}))
