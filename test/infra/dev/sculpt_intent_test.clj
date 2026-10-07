(ns infra.dev.sculpt-intent-test
  "Ordinary sculpt consumption must use one host context and the current serial world."
  (:require
   [clojure.math :as math]
   [clojure.string :as str]
   [clojure.test :refer [deftest is testing]]
   [domain.ecs.components :as c]
   [domain.ecs.core :as ecs]
   [domain.ecs.tick :as tick]
   [domain.integrator :as integrator]
   [domain.player :as player]
   [infra.dev.window.loop :as loop]
   [infra.render.input :as input]
   [law.narrowing :as narrowing]
   [shape.spatial :as sp])
  (:import
   (java.io StringWriter)
   (java.util.concurrent ConcurrentLinkedQueue)
   (org.lwjgl.glfw GLFW GLFWKeyCallback)))

(def ^:private zero-offset [0.0 0.0 0.0])

(defn- contextual-api
  "Fail explicitly for absent admitted APIs; never substitute a test implementation."
  []
  (let [names {:enqueue ['infra.dev.window.loop 'enqueue-contextual!]
               :record ['infra.dev.window.loop '->ContextualIntent]
               :request ['infra.render.input 'request-sculpt]}
        found (into {} (map (fn [[k [n s]]] [k (ns-resolve n s)])) names)
        missing (keep (fn [[k v]] (when-not v k)) found)]
    (when (is (empty? missing) (str "Missing admitted contextual APIs: " (vec missing)))
      found)))

(defn- armed-world
  "A moving physical Spark and committed planet; stale attention is deliberately distinct."
  []
  (let [[w observer] (player/spawn-observer (ecs/empty-world) [10.0 0.0 0.0])
        [w planet] (ecs/spawn w)]
    [(-> w
         (ecs/put-component observer c/velocity [2.0 6.0 0.0])
         (ecs/put-component observer c/palette narrowing/planetary-palette)
         (player/update-observer assoc :resonance 20.0
                                 :focus-position [100.0 200.0 0.0])
         (ecs/put-components planet {c/position [0.0 0.0 0.0]
                                     c/velocity [2.0 -1.0 0.0]
                                     c/mass 1.0 c/radius 1.0
                                     c/body-kind :body/planet
                                     c/commitment-state :committed}))
     observer planet]))

(defn- physical-columns [w]
  (select-keys (:components w)
               [c/position c/velocity c/mass c/radius c/body-kind
                c/orientation c/angular-velocity]))

(defn- anchor-at [w planet focus]
  (let [relative (sp/v- focus (ecs/get-component w planet c/position))
        length (math/sqrt (reduce + (map #(* % %) relative)))]
    (if (zero? length) [0.0 0.0 1.0] (mapv #(/ % length) relative))))

(defn- expected-anchor [w planet offset]
  (anchor-at w planet (sp/v+ (player/observer-position w) offset)))

(defn- close-vector? [a b]
  (and (= 3 (count a) (count b))
       (every? #(< (abs (double %)) 1.0e-12) (map - a b))))

(defn- physical-tick [w]
  (tick/run-parallel (ecs/advance-tick w) [(integrator/integrator-system 1.0)]))

(defn- run-loop
  "Run the actual serial host and integrator with finite publication-based termination."
  [world config iterations prepare after-publish]
  (let [published (atom world)
        queue (ConcurrentLinkedQueue.)
        intents (loop/->IntentAtom queue published)
        stop (atom false)
        inputs (atom [])
        outputs (atom [])
        service (atom {})
        errors (StringWriter.)
        cfg (atom (assoc config :tick-fn (fn [w]
                                           (swap! inputs conj w)
                                           (physical-tick w))))
        host {:published published :queue queue :intents intents :config cfg}]
    (prepare host)
    (add-watch published ::bounded-publications
               (fn [_ _ _ w]
                 (let [n (count (swap! outputs conj w))]
                   (when after-publish (after-publish host n))
                   (when (>= n iterations) (reset! stop true)))))
    (try
      (binding [*err* errors]
        (loop/sim-loop {:world-atom published :intent-queue queue :config-atom cfg
                        :stop-atom stop :service-state service}))
      {:inputs @inputs :published @outputs :errors (str errors) :service @service}
      (finally
        (remove-watch published ::bounded-publications)))))

(defn- with-key-callback
  "Construct/free the actual GLFW callback without creating or addressing a window."
  [{:keys [config intents]} options f]
  (let [held-keys (atom {})
        camera (atom {})
        ^GLFWKeyCallback callback
        (if (nil? options)
          (@#'input/key-callback 0 camera held-keys config intents)
          (@#'input/key-callback 0 camera held-keys config intents options))]
    (try
      (f callback held-keys)
      (finally (.free callback)))))

(defn- press-and-release! [^GLFWKeyCallback callback glfw-key mods]
  (doseq [action [GLFW/GLFW_PRESS GLFW/GLFW_REPEAT GLFW/GLFW_RELEASE]]
    (.invoke callback 0 glfw-key 0 action mods)))

(defn- press! [host options]
  (with-key-callback host options
    (fn [callback _] (press-and-release! callback GLFW/GLFW_KEY_T 0))))

(defn- drain [world entries context]
  (let [queue (ConcurrentLinkedQueue.)
        errors (StringWriter.)]
    (doseq [entry entries] (.add queue entry))
    {:world (binding [*err* errors]
              (@#'loop/drain-intents world queue context))
     :errors (str errors)
     :empty? (.isEmpty queue)}))

(deftest legacy-callback-characterizes-published-stale-aim
  (testing "baseline evidence only: the explicitly legacy route retains its focus-based contract"
    (doseq [frame-offset [zero-offset [4.0 3.0 -2.0]]]
      (let [[w _ planet] (armed-world)
            {:keys [inputs published errors]}
            (run-loop (assoc w :genesis/frame-offset frame-offset) {:mode :manual} 2
                      (constantly nil)
                      (fn [host n] (when (= n 1) (press! host nil))))
            source (first published)
            paid (first (:voxel/sculpt-ops (second inputs)))
            stale-relative (sp/v- (:focus-position (player/get-observer source))
                                  (ecs/get-component source planet c/position))
            stale-length (math/sqrt (reduce + (map #(* % %) stale-relative)))
            stale-anchor (mapv #(/ % stale-length) stale-relative)]
        (is (= "" errors))
        (is (not= (player/observer-position w) (player/observer-position source)))
        (is (close-vector? stale-anchor (:anchor paid)))
        (is (not (close-vector? (expected-anchor source planet zero-offset) (:anchor paid)))
            "this is real geometric disagreement, not an absent contextual API")
        (is (= (physical-columns source) (physical-columns (second inputs))))))))

(deftest opted-in-callback-aims-from-the-current-physical-publication
  (when-let [{:keys [enqueue]} (contextual-api)]
    (doseq [frame-offset [zero-offset [4.0 3.0 -2.0]]
            offset [zero-offset [0.0 0.0 3.0] '(0.0 0.0 3.0)]]
      (let [[w _ planet] (armed-world)
            {:keys [inputs published errors service]}
            (run-loop (assoc w :genesis/frame-offset frame-offset)
                      {:mode :manual :focus-offset offset} 2 (constantly nil)
                      (fn [{:keys [intents] :as host} n]
                        (when (= n 1)
                          (press! host {:submit-contextual! (partial enqueue intents)}))))
            source (first published)
            action-input (second inputs)
            ops (:voxel/sculpt-ops action-input)]
        (is (= "" errors))
        (is (empty? service))
        (is (= 1 (count ops)))
        (is (= planet (:target (first ops))))
        (is (= 1 (:tick (first ops))))
        (is (close-vector? (expected-anchor source planet offset) (:anchor (first ops))))
        (is (= 18.0 (:resonance (player/get-observer action-input))))
        (is (= (physical-columns source) (physical-columns action-input)))
        (is (not= (player/observer-position w) (player/observer-position source)))))))

(deftest nominal-payloads-and-legacy-callables-share-one-failure-guard
  (when-let [{:keys [record]} (contextual-api)]
    (let [w {:next-world {:step :keyword}}
          mapped {:step :map}
          final {:step :ordinary-map}
          context {:manual? true :focus-offset zero-offset}
          {:keys [world errors] queue-empty? :empty?}
          (drain w [:next-world
                    {{:step :keyword} mapped}
                    {:update identity mapped final}
                    (record nil)
                    (assoc (record (fn [world _] world)) :extra true)
                    (record (fn [_ _] 7))
                    (record (fn [_ _] (throw (AssertionError. "contextual failure"))))
                    #(assoc % :later :preserved)] context)]
      (is (= (assoc final :later :preserved) world))
      (is (str/includes? errors "contextual"))
      (is queue-empty?))
    (testing "a structural context failure is contained without invoking its update"
      (let [w {:unchanged true}
            called (atom false)
            entry (record (fn [world _] (reset! called true) world))]
        (doseq [context [nil {} {:manual? nil :focus-offset zero-offset}
                         {:manual? true}]]
          (let [{:keys [world errors]} (drain w [entry] context)]
            (is (identical? w world))
            (is (str/includes? errors "context"))
            (is (false? @called))))))))

(deftest existing-swap-reset-arities-retain-their-queue-results
  (let [world (atom {:original true})
        queue (ConcurrentLinkedQueue.)
        intents (loop/->IntentAtom queue world)]
    (is (= {:reset true} (reset! intents {:reset true})))
    (doseq [result [(swap! intents #(assoc % :unary true))
                    (swap! intents dissoc :absent)
                    (swap! intents update :one (fnil inc 0))
                    (swap! intents assoc :two 2)
                    (swap! intents assoc :three 3 :four 4)]]
      (is (= {:original true} result)))
    (is (= {:original true} @world))
    (is (= {:reset true :unary true :one 1 :two 2 :three 3 :four 4}
           (@#'loop/drain-intents @world queue)))
    (is (.isEmpty queue))))

(deftest explicit-callback-submission-retains-press-and-release-semantics
  (when-let [{:keys [enqueue]} (contextual-api)]
    (doseq [[glfw-key mods verb remaining] [[GLFW/GLFW_KEY_T 0 :uplift 18.0]
                                            [GLFW/GLFW_KEY_T GLFW/GLFW_MOD_SHIFT :volcanism 17.0]
                                            [GLFW/GLFW_KEY_Y 0 :erosion 18.5]]]
      (let [[w _ _] (armed-world)
            published (atom w)
            queue (ConcurrentLinkedQueue.)
            intents (loop/->IntentAtom queue published)
            config (atom {:mode :manual})
            host {:intents intents :config config}]
        (with-key-callback host {:submit-contextual! (partial enqueue intents)}
          (fn [callback held]
            (press-and-release! callback glfw-key mods)
            (is (empty? @held))))
        (is (= 1 (.size queue)))
        (is (identical? w @published))
        (is (= {:mode :manual} @config))
        (let [drained (@#'loop/drain-intents w queue
                                             {:manual? true :focus-offset zero-offset})]
          (is (= [verb] (mapv :verb (:voxel/sculpt-ops drained))))
          (is (= remaining (:resonance (player/get-observer drained)))))))))

(deftest invalid-capability-rejects-before-callback-installation
  (when (contextual-api)
    (doseq [value [nil false {} :not-a-function]]
      (let [entered (atom false)
            sentinel (ex-info "callback constructor reached" {})
            outcome (try
                      (with-redefs-fn
                        {#'input/key-callback
                         (fn [& _] (reset! entered true) (throw sentinel))}
                        #(input/setup-input {:window 0 :submit-contextual! value}))
                      :unexpected-return
                      (catch clojure.lang.ExceptionInfo e e))]
        (is (instance? clojure.lang.ExceptionInfo outcome))
        (is (not (identical? sentinel outcome)))
        (is (false? @entered)
            "the sentinel prevents any GLFW setter if validation regresses")))))

(deftest throwing-submission-never-falls-back-to-direct-dispatch
  (when (contextual-api)
    (let [[w _ _] (armed-world)
          published (atom w)
          failure (ex-info "submission failed" {})
          host {:intents published :config (atom {})}]
      (with-key-callback host {:submit-contextual! (fn [_] (throw failure))}
        (fn [^GLFWKeyCallback callback _]
          (let [outcome (try
                          (.invoke callback 0 GLFW/GLFW_KEY_T 0 GLFW/GLFW_PRESS 0)
                          :unexpected-return
                          (catch clojure.lang.ExceptionInfo e e))]
            (.invoke callback 0 GLFW/GLFW_KEY_T 0 GLFW/GLFW_RELEASE 0)
            (is (identical? failure outcome))
            (is (identical? w @published))))))))

(deftest manual-validation-precedence-and-tracking-delegation
  (when-let [{:keys [record request]} (contextual-api)]
    (let [[w observer _] (armed-world)
          empty-world (ecs/empty-world)
          manual {:manual? true :focus-offset zero-offset}
          entry (fn [verb] (record #(request %1 %2 verb 0.5)))
          invalid-verb (entry :not-a-verb)]
      (doseq [[world context message]
              [[w (assoc manual :focus-offset nil) "focus-offset"]
               [w (assoc manual :focus-offset [##NaN 0.0 0.0]) "focus-offset"]
               [(ecs/remove-component w observer c/position) manual "position"]
               [w manual "unknown sculpt verb"]
               [empty-world {:manual? false :focus-offset nil} "unknown sculpt verb"]]]
        (let [result (drain world [invalid-verb] context)]
          (is (identical? world (:world result)))
          (is (str/includes? (:errors result) message))))
      (let [result (drain empty-world [invalid-verb] (assoc manual :focus-offset nil))]
        (is (identical? empty-world (:world result)))
        (is (= "" (:errors result))))
      (let [result (drain w [invalid-verb (entry :uplift)
                             #(assoc % :later-input true)] manual)]
        (is (= [:uplift] (mapv :verb (:voxel/sculpt-ops (:world result)))))
        (is (= 18.0 (:resonance (player/get-observer (:world result)))))
        (is (:later-input (:world result)))
        (is (= (physical-columns w) (physical-columns (:world result))))))))

(deftest tracking-retains-magnitude-validation-before-its-observer-gate
  (when-let [{:keys [record request]} (contextual-api)]
    (let [w (ecs/empty-world)
          entry (record #(request %1 %2 :uplift 0.0))
          tracking (drain w [entry] {:manual? false :focus-offset nil})
          manual (drain w [entry] {:manual? true :focus-offset nil})]
      (is (identical? w (:world tracking)))
      (is (str/includes? (:errors tracking) "magnitude outside"))
      (is (identical? w (:world manual)))
      (is (= "" (:errors manual))))))

(deftest coincident-prepared-focus-keeps-the-domain-anchor-convention
  (when-let [{:keys [record request]} (contextual-api)]
    (let [[w _ _] (armed-world)
          {:keys [world errors]} (drain w [(record #(request %1 %2 :uplift 0.5))]
                                        {:manual? true :focus-offset [-10.0 0.0 0.0]})]
      (is (= "" errors))
      (is (= [0.0 0.0 1.0] (:anchor (first (:voxel/sculpt-ops world)))))
      (is (= (physical-columns w) (physical-columns world))))))

(deftest valid-aim-denied-payment-retains-prepared-attention
  (when-let [{:keys [record request]} (contextual-api)]
    (let [[w observer planet] (armed-world)
          offset [1.0 2.0 3.0]
          entry (record #(request %1 %2 :uplift 0.5))]
      (doseq [source [(ecs/remove-component w planet c/commitment-state)
                      (ecs/remove-component w observer c/palette)
                      (player/update-observer w assoc :resonance 0.0)]]
        (let [{:keys [world errors]} (drain source [entry]
                                            {:manual? true :focus-offset offset})]
          (is (= "" errors))
          (is (= (sp/v+ (player/observer-position source) offset)
                 (:focus-position (player/get-observer world))))
          (is (= (player/update-observer source assoc :focus-position
                                         (sp/v+ (player/observer-position source) offset))
                 world))
          (is (empty? (:voxel/sculpt-ops world))))))))

(deftest fifo-payment-and-later-readers-use-persistent-preparation
  (when-let [{:keys [enqueue]} (contextual-api)]
    (let [[w _ _] (armed-world)
          w (player/update-observer w assoc :resonance 3.0)
          published (atom w)
          queue (ConcurrentLinkedQueue.)
          intents (loop/->IntentAtom queue published)
          host {:intents intents :config (atom {:mode :manual})}
          options {:submit-contextual! (partial enqueue intents)}
          seen (atom [])]
      (swap! intents player/update-observer player/narrow-focus 2.0)
      (press! host options)
      (swap! intents (fn [world]
                       (swap! seen conj world)
                       (player/update-observer world assoc :focus-position [900.0 0.0 0.0])))
      (press! host options)
      (swap! intents (fn [world] (swap! seen conj world) world))
      (let [drained (@#'loop/drain-intents w queue
                                           {:manual? true :focus-offset zero-offset})
            observer (player/get-observer drained)]
        (is (= 1 (count (:voxel/sculpt-ops drained))))
        (is (= (:voxel/sculpt-ops (first @seen)) (:voxel/sculpt-ops drained)))
        (is (= 1.0 (:resonance observer)))
        (is (= (/ (:focus-radius (player/get-observer w)) 2.0) (:focus-radius observer)))
        (is (= 1.0 (:focus-intensity observer)))
        (is (= (player/observer-position w) (:focus-position observer)))
        (is (= drained (second @seen)) "the denied second action still refreshed attention")
        (is (= (physical-columns w) (physical-columns drained)))))))

(deftest one-config-snapshot-governs-mid-drain-arrivals
  (when-let [{:keys [enqueue]} (contextual-api)]
    (doseq [[first-mode next-mode] [[:manual :follow-selection]
                                    [:follow-selection :manual]]]
      (let [[w _ planet] (armed-world)
            first-offset [1.0 2.0 3.0]
            next-offset [-2.0 0.0 1.0]
            {:keys [inputs published errors]}
            (run-loop
             w {:mode first-mode :focus-offset first-offset} 2
             (fn [{:keys [intents config] :as host}]
               (let [options {:submit-contextual! (partial enqueue intents)}]
                 (swap! intents (fn [world]
                                  (swap! config assoc :mode next-mode :focus-offset next-offset)
                                  (press! host options)
                                  world))
                 (press! host options)))
             (fn [{:keys [intents] :as host} n]
               (when (= n 1)
                 (press! host {:submit-contextual! (partial enqueue intents)}))))
            first-ops (:voxel/sculpt-ops (first inputs))
            later-ops (:voxel/sculpt-ops (second inputs))
            first-focus (if (= :manual first-mode)
                          (sp/v+ (player/observer-position w) first-offset)
                          (:focus-position (player/get-observer w)))
            source (first published)
            next-focus (if (= :manual next-mode)
                         (sp/v+ (player/observer-position source) next-offset)
                         (:focus-position (player/get-observer source)))]
        (is (= "" errors))
        (is (= 2 (count first-ops)) "the mid-drain callback reaches the same drain")
        (is (= 3 (count later-ops)))
        (is (= first-ops (subvec (vec later-ops) 0 2)))
        (is (every? #(close-vector? (anchor-at w planet first-focus)
                                    (:anchor %)) first-ops))
        (is (close-vector? (anchor-at source planet next-focus)
                           (:anchor (last later-ops))))))))

(deftest absent-and-explicit-nil-host-settings-remain-distinct
  (when-let [{:keys [enqueue]} (contextual-api)]
    (doseq [[config paid?] [[{} true] [{:mode nil :focus-offset nil} true]
                            [{:mode :manual :focus-offset nil} false]]]
      (let [[w _ _] (armed-world)
            {:keys [inputs]} (run-loop
                              w config 1
                              (fn [{:keys [intents] :as host}]
                                (press! host {:submit-contextual! (partial enqueue intents)}))
                              nil)]
        (is (= paid? (boolean (seq (:voxel/sculpt-ops (first inputs))))))))))

(deftest reset-and-later-camera-input-remain-ordered-around-contextual-actions
  (when-let [{:keys [enqueue]} (contextual-api)]
    (let [[w _ _] (armed-world)
          published (atom w)
          queue (ConcurrentLinkedQueue.)
          intents (loop/->IntentAtom queue published)
          host {:intents intents :config (atom {:mode :manual})}
          options {:submit-contextual! (partial enqueue intents)}
          before-reset (atom nil)
          replacement (player/update-observer w assoc :resonance 2.0)
          camera-focus [900.0 800.0 700.0]]
      (press! host options)
      (swap! intents (fn [world] (reset! before-reset world) world))
      (reset! intents replacement)
      (press! host options)
      (swap! intents player/update-observer assoc :focus-position camera-focus)
      (let [drained (@#'loop/drain-intents w queue
                                           {:manual? true :focus-offset zero-offset})]
        (is (= 18.0 (:resonance (player/get-observer @before-reset))))
        (is (= 0.0 (:resonance (player/get-observer drained))))
        (is (= 1 (count (:voxel/sculpt-ops drained))) "reset drops the old world's record")
        (is (= camera-focus (:focus-position (player/get-observer drained)))
            "later camera input still runs; the existing final loop preparation is separate")
        (is (= (physical-columns w) (physical-columns drained)))))))
