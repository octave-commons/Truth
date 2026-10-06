(ns infra.dev.focus-cadence-test
  "Manual attention must reach simulation consumers independently of rendering."
  (:require
   [clojure.test :refer [deftest is testing]]
   [domain.ecs.components :as c]
   [domain.ecs.core :as ecs]
   [domain.ecs.tick :as tick]
   [domain.integrator :as integrator]
   [domain.narrowing :as narrowing]
   [domain.player :as player]
   [infra.camera :as camera]
   [infra.dev.window.loop :as loop]
   [infra.render.units :as units]
   [law.narrowing :as law]
   [shape.spatial :as sp])
  (:import
   (java.io StringWriter)
   (java.util.concurrent ConcurrentLinkedQueue)))

(def ^:private au law/world-focus-radius)

(defn- moving-pair
  "The real integrator moves both bodies; their separation remains half an AU."
  []
  (let [[w spark] (player/spawn-observer (ecs/empty-world) [(* 0.5 au) 0.0 0.0])
        [w planet] (ecs/spawn w)
        velocity [(* 2.0 au) 0.0 0.0]]
    [(-> w
         (ecs/put-component spark c/velocity velocity)
         (ecs/put-components planet {c/position [0.0 0.0 0.0]
                                     c/velocity velocity
                                     c/mass 1.0
                                     c/radius 1.0
                                     c/body-kind :body/planet
                                     c/planet-candidate {:planet-id planet}}))
     spark planet]))

(defn- physical-tick
  "Use the production frozen fan-out, physical writer and binding consumer."
  [world]
  (tick/run-parallel (ecs/advance-tick world)
                     [(integrator/integrator-system 1.0)
                      (narrowing/binding-system)]))

(defn- run-iterations
  "Run the actual host loop, stopping after a bounded number of publications.

   No renderer runs and no focus-follow intent is supplied by this harness.
   Watches only stop the loop or deliver the next ordinary host input."
  [world iterations {:keys [config intents after-publish tick-fn]
                     :or {config {} intents [] tick-fn physical-tick}}]
  (let [world-atom (atom world)
        queue (ConcurrentLinkedQueue.)
        queued (loop/->IntentAtom queue world-atom)
        stop (atom false)
        inputs (atom [])
        published (atom [])
        service (atom {})
        errors (StringWriter.)
        cfg (atom (merge {:mode :manual
                          :focus-offset [0.0 0.0 0.0]
                          :tick-fn (fn [w]
                                     (swap! inputs conj w)
                                     (tick-fn w))}
                         config))]
    (doseq [intent intents]
      (swap! queued intent))
    (add-watch world-atom ::stop-after-publications
               (fn [_ _ _ w]
                 (let [n (count (swap! published conj w))]
                   (when after-publish (after-publish n queued cfg))
                   (when (>= n iterations) (reset! stop true)))))
    (try
      (binding [*err* errors]
        (loop/sim-loop {:world-atom world-atom :intent-queue queue
                        :config-atom cfg :stop-atom stop :service-state service}))
      {:inputs @inputs :published @published :config @cfg
       :service @service :errors (str errors)}
      (finally
        (remove-watch world-atom ::stop-after-publications)))))

(defn- focus-position [world]
  (:focus-position (player/get-observer world)))

(defn- physical-columns [world]
  (select-keys (:components world)
               [c/position c/velocity c/orientation c/angular-velocity
                c/mass c/radius c/body-kind]))

(deftest no-render-flight-keeps-binding-consumers-aligned
  (doseq [frame-offset [[0.0 0.0 0.0] [(* 0.25 au) 0.0 0.0]]]
    (testing (str "co-moving bodies, per-fold frame translation " frame-offset)
      (let [[world spark planet] (moving-pair)
            world (assoc world :genesis/frame-offset frame-offset)
            {:keys [inputs published errors]} (run-iterations world 4 {})
            depths (mapv #(get (ecs/get-component % spark c/binding) planet 0.0)
                         published)]
        (is (= 4 (count inputs)))
        (is (= "" errors))
        (is (= [1 2 3 4] (mapv :tick published)))
        (doseq [[before input] (map vector (cons world published) inputs)]
          (is (= (physical-columns before) (physical-columns input))
              "serial attention preparation cannot move either physical body")
          (is (= (player/observer-position input) (focus-position input))
              "each frozen consumer snapshot uses the current Spark position")
          (is (narrowing/focus-overlap? (focus-position input)
                                        (ecs/get-component input planet c/position)
                                        law/world-focus-radius)))
        (doseq [[n depth] (map vector (range 1 5) depths)]
          (is (< (abs (- (* n law/accrual-rate) depth)) 1.0e-12)
              (str "binding accrues every actual tick, observed " depths)))
        (is (not= (player/observer-position world)
                  (player/observer-position (last published)))
            "the fixture really moves through the production integrator")
        (is (not= (focus-position (last published))
                  (player/observer-position (last published)))
            "the contract is pre-fold alignment, not post-fold focus prediction")))))

(deftest current-offset-follows-drained-input-without-resetting-focus-shape
  (let [[world _ _] (moving-pair)
        offset [(* 0.1 au) 0.0 0.0]
        radius (:focus-radius (player/get-observer world))
        {:keys [inputs]} (run-iterations
                          world 3
                          {:intents [#(player/update-observer % player/narrow-focus 2.0)]
                           :after-publish
                           (fn [n queued cfg]
                             (when (= n 1)
                               (swap! cfg assoc :focus-offset offset)
                               (swap! queued player/update-observer player/widen-focus 2.0)
                               (swap! queued player/set-thrust [0.0 1.0 0.0])))})]
    (is (= [1.0 0.5 0.5] (mapv #(-> % player/get-observer :focus-intensity) inputs)))
    (is (= [(/ radius 2.0) radius radius]
           (mapv #(-> % player/get-observer :focus-radius) inputs)))
    (is (= [nil [0.0 1.0 0.0] [0.0 1.0 0.0]] (mapv :player/thrust inputs)))
    (doseq [input (rest inputs)]
      (is (= (sp/v+ (player/observer-position input) offset) (focus-position input))
          "updated host offset lands without needing another render frame"))))

(deftest current-mode-resolves-queued-camera-focus
  (let [[world _ _] (moving-pair)
        cam (assoc (camera/make-camera) :target [8.0 0.0 0.0])
        ctx (units/make-context cam {:width 640 :height 480})
        target (units/render->world ctx (:target cam))
        camera-intent #(@#'loop/sync-observer-focus-to-camera % cam ctx :follow-selection)
        {:keys [inputs]} (run-iterations
                          world 4
                          {:config {:mode :follow-selection}
                           :intents [camera-intent]
                           :after-publish
                           (fn [n queued cfg]
                             (case n
                               1 (do (swap! cfg assoc :mode :manual)
                                     (swap! queued camera-intent))
                               2 (do (swap! cfg assoc :mode :follow-selection)
                                     (swap! queued camera-intent))
                               nil))})]
    (is (= target (focus-position (first inputs))))
    (is (= (player/observer-position (second inputs)) (focus-position (second inputs)))
        "current manual mode wins after an already queued tracking-frame intent")
    (is (= [target target] (mapv focus-position (drop 2 inputs)))
        "tracking focus remains the camera target, independent of Spark motion")))

(deftest absent-observer-or-physical-position-is-a-no-op
  (let [[world spark _] (moving-pair)]
    (doseq [w [(ecs/empty-world) (ecs/remove-component world spark c/position)]]
      (let [{:keys [inputs published errors]} (run-iterations w 2 {:tick-fn identity})]
        (is (= [w w] inputs published))
        (is (= "" errors))))))

(deftest held-or-error-paused-ticks-still-prepare-attention-without-motion
  (let [[world _ _] (moving-pair)
        offset [(* 0.1 au) 0.0 0.0]]
    (doseq [config [{:sim-frame-interval 3} {:ui/error-state {:test/paused true}}]]
      (let [{:keys [inputs published errors]}
            (run-iterations world 3
                            {:config config :tick-fn identity
                             :after-publish (fn [n queued cfg]
                                              (when (= n 1)
                                                (swap! cfg assoc :focus-offset offset)
                                                (swap! queued assoc :test/queued true)))})]
        (is (= (if (:ui/error-state config) 0 1) (count inputs)))
        (is (every? #(= (physical-columns world) (physical-columns %)) published))
        (is (= [nil true true] (mapv :test/queued published)))
        (is (= (sp/v+ (player/observer-position world) offset)
               (focus-position (last published))))
        (is (= "" errors))))))

(deftest attention-failure-is-contained-without-losing-drained-changes
  (let [[world _ _] (moving-pair)
        {:keys [inputs published errors service config]}
        (with-redefs [player/focus-follow (fn [_ _] (throw (AssertionError. "focus failure probe")))]
          (run-iterations world 2
                          {:intents [#(assoc % :test/queued true)]
                           :tick-fn ecs/advance-tick}))]
    (is (= [1 2] (mapv :tick published)) "a failed attention update cannot kill the loop")
    (is (= [true true] (mapv :test/queued inputs)) "already drained input is retained")
    (is (= (mapv #(dissoc % :test/queued :tick) inputs)
           (repeat 2 (dissoc world :tick)))
        "the failed update cannot partly replace the world")
    (is (re-find #"focus failure probe" errors) "the failed update remains visible")
    (is (empty? service))
    (is (nil? (:ui/error-state config)) "attention failure retains intent-drop semantics")))
