;; Diagnostic characterization of the current callback, not a proposed input law.
;; Runs in its own bounded JVM. No GLFW init, window, GL context or ECS world.
(require '[infra.menu :as menu]
         '[infra.render.input])
(import '(org.lwjgl.glfw GLFW GLFWMouseButtonCallback))

(let [hits (:hits (menu/menu-hud {} nil 1280.0 720.0))
      spark (some #(when (= [:ui/toggle-domain :spark] (:action %)) %) hits)
      _ (assert spark "Production top bar must expose Spark")
      point [(/ (+ (:x0 spark) (:x1 spark)) 2.0)
             (/ (+ (:y0 spark) (:y1 spark)) 2.0)]
      cases [{:id :release-without-press
              :steps [:release :consume] :domain :spark :consumes 1}
             {:id :balanced-click
              :steps [:press :release :consume] :domain :spark :consumes 1}
             {:id :extra-release-before-consumption
              :steps [:press :release :release :consume] :domain :spark :consumes 1}
             {:id :extra-release-after-consumption
              :steps [:press :release :consume :release :consume] :domain nil :consumes 2}
             {:id :drag-release-control
              :steps [:press :mark-dragged :release :consume] :domain nil :consumes 0}]
      results
      (mapv
       (fn [{:keys [id steps domain consumes]}]
         (let [config (atom {:mode :manual :ui/cursor-free? true})
               cursor (atom point)
               dragged? (atom false)
               consumed (atom 0)
               trace (atom [])
               ^GLFWMouseButtonCallback callback
               (@#'infra.render.input/mouse-button-callback 0 config cursor dragged?)]
           (try
             (doseq [step steps]
               (case step
                 :press (.invoke callback 0 GLFW/GLFW_MOUSE_BUTTON_LEFT GLFW/GLFW_PRESS 0)
                 :release (.invoke callback 0 GLFW/GLFW_MOUSE_BUTTON_LEFT GLFW/GLFW_RELEASE 0)
                 :mark-dragged (reset! dragged? true)
                 :consume
                 (when-let [{:keys [x y]} (:pick-request @config)]
                   ;; Explicit pure menu-consumption seam, not a render frame:
                   ;; use the production hit resolution and action function.
                   (let [hit (menu/hit-at hits x y)]
                     (assert (= [:ui/toggle-domain :spark] (:action hit)))
                     (swap! config #(-> %
                                        (dissoc :pick-request)
                                        (menu/apply-action (:action hit))))
                     (swap! consumed inc))))
               (swap! trace conj {:step step :pick (:pick-request @config)
                                  :domain (:ui/active-domain @config) :dragged? @dragged?}))
             (let [result {:id id :trace @trace :domain (:ui/active-domain @config)
                           :consumes @consumed :pending-pick? (contains? @config :pick-request)}]
               (assert (= domain (:domain result)) (str "Unexpected callback result: " id))
               (assert (= consumes (:consumes result)) (str "Unexpected consumption count: " id))
               (assert (false? (:pending-pick? result)) (str "Unconsumed pick: " id))
               result)
             (finally (.free callback)))))
       cases)]
  (prn {:observation :direct-production-callback-invocation
        :production-base "b395c4049718f7ce015ddf25fc0a192d97821373"
        :window-created? false :glfw-initialized? false :world-created? false
        :menu-hit point :cases results
        :limits [:not-x11-event-delivery :not-glfw-event-polling
                 :explicit-menu-consumption-not-render-frame
                 :not-causality-proof-for-the-closed-native-attempt]}))
