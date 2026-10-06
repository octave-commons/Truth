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

(deftest invalid-host-offset-is-rejected-before-the-domain-boundary
  (let [[world _ _] (moving-pair)
        original-follow player/focus-follow
        queued-update #(-> %
                           (assoc :test/queued true)
                           (player/update-observer player/narrow-focus 2.0))
        expected (dissoc (queued-update world) :tick)]
    (doseq [offset [nil [] [0.0 0.0] [0.0 0.0 0.0 1.0]
                    [##NaN 0.0 0.0] [0.0 ##Inf 0.0] [0.0 0.0 ##-Inf]
                    ["bad" 0.0 0.0] {:x 0.0 :y 0.0 :z 0.0}]]
      (testing (str "invalid host focus-offset " (pr-str offset))
        (let [domain-offsets (atom [])
              {:keys [inputs published errors service config]}
              (with-redefs [player/focus-follow
                            (fn [w supplied-offset]
                              (swap! domain-offsets conj supplied-offset)
                              (original-follow w supplied-offset))]
                (run-iterations world 2
                                {:config {:focus-offset offset}
                                 :intents [queued-update]
                                 :tick-fn ecs/advance-tick}))]
          (is (empty? @domain-offsets) "invalid host data never enters focus-follow")
          (is (= [expected expected] (mapv #(dissoc % :tick) inputs))
              "rejection preserves the complete drained world, including attention and physics")
          (is (= [1 2] (mapv :tick published)) "the existing guard keeps ticking")
          (is (re-find #"focus-offset" errors) "the rejected boundary is named in the error")
          (is (identical? offset (:focus-offset config)) "no silent host correction or clamp")
          (is (empty? service))
          (is (nil? (:ui/error-state config))))))))

(deftest valid-host-offsets-preserve-focus-and-physical-state
  (let [[world _ _] (moving-pair)
        observer (player/get-observer world)]
    (doseq [offset [[0.0 0.0 0.0] [3 -2 1] '(2.0 3.0 -4.0)]]
      (testing (str "finite host coordinates " offset)
        (let [{:keys [inputs published errors]}
              (run-iterations world 2 {:config {:focus-offset offset}
                                       :tick-fn identity})
              expected-focus (sp/v+ (player/observer-position world) offset)]
          (is (= [expected-focus expected-focus] (mapv focus-position inputs)))
          (is (= inputs published))
          (doseq [input inputs]
            (is (= (physical-columns world) (physical-columns input)))
            (is (= (dissoc observer :focus-position)
                   (dissoc (player/get-observer input) :focus-position))))
          (is (= "" errors)))))))

(deftest absent-host-offset-still-defaults-to-zero
  (let [[world _ _] (moving-pair)
        offset [3.0 -2.0 1.0]
        {:keys [inputs config errors]}
        (run-iterations world 3
                        {:config {:focus-offset offset}
                         :tick-fn identity
                         :after-publish (fn [n _ cfg]
                                          (when (= n 1) (swap! cfg dissoc :focus-offset)))})]
    (is (not (contains? config :focus-offset)) "exercise a genuinely absent host key")
    (is (= [(sp/v+ (player/observer-position world) offset)
            (player/observer-position world)
            (player/observer-position world)]
           (mapv focus-position inputs)))
    (is (= "" errors))))

(deftest corrected-host-offset-recovers-on-the-next-iteration
  (let [[world _ _] (moving-pair)
        offset [3.0 -2.0 1.0]
        {:keys [inputs published errors]}
        (run-iterations world 3
                        {:config {:focus-offset [##NaN 0.0 0.0]}
                         :tick-fn ecs/advance-tick
                         :after-publish (fn [n queued cfg]
                                          (when (= n 1)
                                            (swap! cfg assoc :focus-offset offset)
                                            (swap! queued assoc :test/queued true)))})]
    (is (= (dissoc world :tick) (dissoc (first inputs) :tick))
        "the invalid offset cannot corrupt the first consumer snapshot")
    (is (= [1 2 3] (mapv :tick published)))
    (is (= [nil true true] (mapv :test/queued inputs)))
    (is (= (repeat 2 (sp/v+ (player/observer-position world) offset))
           (map focus-position (rest inputs))))
    (is (= 1 (count (re-seq #"focus-offset" errors)))
        "only the invalid iteration emits a boundary error")))

(deftest tracking-mode-does-not-consume-the-manual-offset
  (let [[world _ _] (moving-pair)
        {:keys [inputs published errors]}
        (run-iterations world 2 {:config {:mode :follow-selection
                                          :focus-offset [##NaN 0.0 0.0]}
                                 :tick-fn identity})]
    (is (= [world world] inputs published))
    (is (= "" errors))))
