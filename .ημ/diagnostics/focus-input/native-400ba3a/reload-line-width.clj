;; Root-authorized only changed production namespace; no world/config/cache edits.
(let [s @infra.dev.window/service-state w (:world s)
      before {:wall-ms (System/currentTimeMillis) :tick (:tick @w)
              :world-identity (System/identityHashCode w)
              :monitor @(:state @(ns-resolve 'user 'truth-native-observer))}]
  (require 'infra.render.scene.setup :reload)
  {:runtime-transition {:from "22f762f61b14db8da477e8a49bd000585bee11a1"
                        :to "85f307f" :only-namespace 'infra.render.scene.setup
                        :delta "glLineWidth1.5 to1.0 and explanatory comment"}
   :before before :after {:wall-ms (System/currentTimeMillis) :tick (:tick @w)
                         :world-identity (System/identityHashCode (:world @infra.dev.window/service-state))}
   :world-atom-preserved? (identical? w (:world @infra.dev.window/service-state))
   :render-thread-alive? (.isAlive (:thread s))})
