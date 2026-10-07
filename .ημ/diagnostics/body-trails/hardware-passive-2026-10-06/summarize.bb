#!/usr/bin/env bb
(require '[clojure.edn :as edn])

(let [directory (or (first *command-line-args*) ".")
      state (edn/read-string {:default (fn [_ value] value)}
                             (slurp (str directory "/captures/observer-state.edn")))
      captures (:captures state)
      times (mapv #(get-in % [:projection-input :genesis/sim-time]) captures)
      tracked (into {} (map (fn [[kind data]] [kind (:time (last (get-in data [:history :samples])))])
                            (:targets (first captures))))
      rows (mapv
            (fn [capture]
              (assoc (select-keys capture [:capture :wall-ms :projection-input :published-read-after-scene
                                          :budget :tagged-trail-vertex-count :scene-line-count :gl :width :height])
                     :targets
                     (into {}
                           (map
                             (fn [[kind data]]
                               (let [vertices (:actual-vertices data)
                                     screen (keep :framebuffer vertices)
                                     xs (map first screen) ys (map second screen)
                                     samples (get-in data [:history :samples])
                                     retained (first (filter #(= (tracked kind) (:time %)) samples))
                                     ;; Same physical->render scale as the recorded production context.
                                     sample-render (when retained (mapv #(/ (double %) 1.0e15) (:position retained)))
                                     alphas (distinct (map :alpha (filter #(= sample-render (:position %)) vertices)))]
                                 [kind {:eid (:eid data) :matter-state (:matter-state data)
                                        :stored-samples (count samples)
                                        :sample-time-range [(some-> samples first :time) (some-> samples last :time)]
                                        :actual-segments (/ (count vertices) 2)
                                        :actual-alpha-range (when (seq vertices)
                                                              [(apply min (map :alpha vertices))
                                                               (apply max (map :alpha vertices))])
                                        :projected-bounds-px (when (seq screen)
                                                               [(apply min xs) (apply min ys) (apply max xs) (apply max ys)])
                                        :tracked-sample-time (tracked kind)
                                        :tracked-sample-retained? (boolean retained)
                                        :tracked-sample-actual-alphas (vec alphas)}]))
                             (:targets capture)))))
            captures)
      result {:kind :derived-from-closed-passive-observations
              :state (select-keys state [:pid :world-atom-identity :window :phase :active? :capture-attempts
                                         :body-calls :scene-calls :unpaired-frames :targets :errors
                                         :scene-root-restored? :bodies-config-restored?])
              :elapsed-capture-wall-ms (when (seq captures) (- (:wall-ms (last captures)) (:wall-ms (first captures))))
              :observed-sim-span (when (seq times) (- (last times) (first times)))
              :history-horizon 6.3e11
              :gl-samples (count (:raw-gl-samples state))
              :all-gl-zero? (every? #(= [0] (:codes %)) (:raw-gl-samples state))
              :captures rows
              :limits ["Projected bounds are endpoint bounds, not isolated pixel attribution or clipping coverage."
                       "Tracked alpha comes from actual scene vertices matched to retained sample position; empty means absent from vertices."
                       "No screenshot timing or scene-call count establishes native FPS."
                       "No input, camera, time, body, HUD or magnetic-visibility intervention occurred."]}]
  (spit (str directory "/derived-summary.edn") (str (pr-str result) "\n"))
  (prn result))
