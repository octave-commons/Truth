;; Diagnostic installation only; root reviews and evaluates this on owned 7895.
;; Originals receive unchanged arguments exactly once; their results/exceptions
;; are preserved. Recording is bounded and never writes the world or input queue.
;; glGetError consumes the owner's error flags; every raw sample is retained in
;; a bounded ring, with cumulative nonzero/truncation/drop counters.
(require 'domain.player 'infra.dev.window.lifecycle 'infra.dev.window.loop
         'shape.spatial)

(let [reload-var (ns-resolve 'user 'truth-cadence-reload)
      _ (assert reload-var "Missing lifecycle provenance")
      reload-state @@reload-var
      service @infra.dev.window.lifecycle/service-state
      _ (assert (= :restarted (:stage reload-state)) "Service has not been restarted")
      _ (assert service "No native service")
      _ (assert (= 1675959387 (System/identityHashCode (:world service))) "Wrong world")
      _ (assert (identical? (:world (:old reload-state)) (:world service)))
      _ (assert (and (.isAlive ^Thread (:thread service))
                     (.isAlive ^Thread (:sim-thread service))) "A native worker is dead")
      _ (assert (identical? (:new-sim-loop reload-state) @#'infra.dev.window.loop/sim-loop)
                "Unexpected sim-loop root")
      _ (assert (nil? (:error service)))
      config-atom (:config service)
      config @config-atom
      _ (assert (nil? (:ui/error-state config)))
      _ (assert (nil? (ns-resolve 'user 'truth-focus-cadence-native-proof))
                "Proof already has an installation record; do not double-install")
      focus-var (ns-resolve 'domain.player 'focus-follow)
      frame-var (ns-resolve 'infra.dev.window.loop 'render-frame-once)
      original-focus @focus-var
      original-frame @frame-var
      tick-present? (contains? config :tick-fn)
      tick-value (:tick-fn config)
      original-tick (get config :tick-fn @(ns-resolve 'infra.dev.window.loop 'default-tick-fn))
      _ (assert (every? ifn? [original-focus original-frame original-tick]))
      guard (Object.)
      last-follow (atom nil)
      state (atom {:source "1ad081475364cc3b75db76142204cbf54b5bf60c"
                   :started-wall-ms (System/currentTimeMillis)
                   :world-atom-identity (System/identityHashCode (:world service))
                   :closed? false :installation :preparing
                   :focus-returns 0 :focus-returns-on-sim 0
                   :sim-calls-started 0 :sim-calls-completed 0 :sim-calls-failed 0
                   :prepared-identity-matches 0 :prepared-identity-misses 0
                   :alignment-passes 0 :alignment-failures 0 :alignment-not-applicable 0
                   :same-frame-pairs 0 :same-frame-advanced-pairs 0
                   :same-frame-aligned-advanced-pairs 0
                   :render-frames 0 :sim-rows [] :sim-rows-dropped 0
                   :gl-rows [] :gl-samples 0 :gl-rows-dropped 0
                   :gl-nonzero-samples 0 :gl-error-code-count 0 :gl-truncated-samples 0
                   :probe-errors [] :probe-error-count 0 :probe-errors-dropped 0})
      append-row (fn [s rows-key dropped-key row cap]
                   (let [rows (get s rows-key)]
                     (-> s
                         (assoc rows-key (vec (take-last cap (conj rows row))))
                         (update dropped-key + (if (>= (count rows) cap) 1 0)))))
      observe! (fn [scope f]
                 (try
                   (locking guard
                     (when-not (:closed? @state) (f)))
                   (catch Throwable error
                     ;; This catch covers only diagnostic work, never an original call.
                     (locking guard
                       (when-not (:closed? @state)
                         (swap! state
                                #(-> %
                                     (update :probe-error-count inc)
                                     (append-row :probe-errors :probe-errors-dropped
                                                 {:scope scope :error (str error)
                                                  :wall-ms (System/currentTimeMillis)} 16))))))))
      focus-wrapper
      (fn [world offset]
        (let [out (original-focus world offset)]
          (observe! :focus-return
                    (fn []
                      (swap! state update :focus-returns inc)
                      (when (identical? (Thread/currentThread) (:sim-thread service))
                        (swap! state update :focus-returns-on-sim inc)
                        ;; One world reference only, cleared on consumption/restoration.
                        (reset! last-follow {:world out :offset offset}))))
          out))
      tick-wrapper
      (fn [world]
        (let [entry (volatile! nil)]
          (observe! :tick-input
                    (fn []
                      (swap! state update :sim-calls-started inc)
                      (let [prepared @last-follow
                            _ (reset! last-follow nil)
                            on-sim? (identical? (Thread/currentThread) (:sim-thread service))
                            matched? (and on-sim? (some? prepared)
                                          (identical? world (:world prepared)))
                            position (domain.player/observer-position world)
                            observer (domain.player/get-observer world)
                            focus (:focus-position observer)
                            offset (:offset prepared)
                            applicable? (and matched? position focus offset)
                            expected (when applicable? (shape.spatial/v+ position offset))
                            aligned? (and applicable? (= expected focus))
                            before @state
                            previous (:last-sim-entry before)
                            frame (:render-frames before)
                            same-frame? (and previous (= frame (:render-frames previous)))
                            advanced? (and same-frame? (number? (:tick world))
                                           (number? (:tick previous))
                                           (> (:tick world) (:tick previous)))
                            row {:call (:sim-calls-started before)
                                 :wall-ms (System/currentTimeMillis)
                                 :thread (.getName (Thread/currentThread)) :on-sim-thread? on-sim?
                                 :tick (:tick world) :sim-time (:genesis/sim-time world)
                                 :dt (:sim/dt world) :frame-offset (:genesis/frame-offset world)
                                 :prepared-identity-match? (boolean matched?)
                                 :offset offset :position position :focus focus :expected-focus expected
                                 :alignment-applicable? (boolean applicable?) :aligned? (boolean aligned?)
                                 :focus-radius (:focus-radius observer) :focus-intensity (:focus-intensity observer)
                                 :thrust (:player/thrust world) :render-frames frame}]
                        (vreset! entry row)
                        (swap! state
                               #(-> %
                                    (update (if matched? :prepared-identity-matches :prepared-identity-misses) inc)
                                    (update (cond aligned? :alignment-passes
                                                  applicable? :alignment-failures
                                                  :else :alignment-not-applicable) inc)
                                    (update :same-frame-pairs + (if same-frame? 1 0))
                                    (update :same-frame-advanced-pairs + (if advanced? 1 0))
                                    (update :same-frame-aligned-advanced-pairs +
                                            (if (and advanced? aligned? (:aligned? previous)) 1 0))
                                    (assoc :last-sim-entry
                                           (select-keys row [:tick :render-frames :aligned?])))))))
          (try
            (let [out (original-tick world)]
              (observe! :tick-output
                        (fn []
                          (swap! state
                                 #(-> %
                                      (update :sim-calls-completed inc)
                                      (append-row :sim-rows :sim-rows-dropped
                                                  (assoc (or @entry {:input-probe-unavailable? true})
                                                         :outcome :returned
                                                         :output-tick (:tick out)
                                                         :output-position (domain.player/observer-position out)
                                                         :output-focus (:focus-position (domain.player/get-observer out)))
                                                  128)))))
              out)
            (catch Throwable error
              (observe! :tick-failure
                        #(swap! state
                                (fn [s]
                                  (-> s
                                      (update :sim-calls-failed inc)
                                      (append-row :sim-rows :sim-rows-dropped
                                                  (assoc (or @entry {:input-probe-unavailable? true})
                                                         :outcome :original-threw :error (str error)) 128)))))
              (throw error)))))
      frame-wrapper
      (fn [args]
        (let [out (original-frame args)]
          (observe! :render-frame
                    (fn []
                      (assert (identical? (Thread/currentThread) (:thread service))
                              "GL probe called outside the owned render thread")
                      (let [frame (:render-frames (swap! state update :render-frames inc))]
                        (when (or (= frame 1) (zero? (mod frame 30)))
                          (assert (= (:window args) (org.lwjgl.glfw.GLFW/glfwGetCurrentContext))
                                  "Original frame did not return with its native context current")
                          (let [codes (loop [codes []]
                                        (let [code (org.lwjgl.opengl.GL11/glGetError)
                                              next-codes (conj codes code)]
                                          (if (or (zero? code) (= 16 (count next-codes)))
                                            next-codes
                                            (recur next-codes))))
                                nonzero (count (remove zero? codes))
                                truncated? (not (zero? (peek codes)))
                                row {:frame frame :wall-ms (System/currentTimeMillis)
                                     :thread (.getName (Thread/currentThread))
                                     :window (:window args) :raw-gl-errors codes
                                     :drain-truncated? truncated?}]
                            (swap! state
                                   #(-> %
                                        (update :gl-samples inc)
                                        (update :gl-nonzero-samples + (if (pos? nonzero) 1 0))
                                        (update :gl-error-code-count + nonzero)
                                        (update :gl-truncated-samples + (if truncated? 1 0))
                                        (append-row :gl-rows :gl-rows-dropped row 128))))))))
          out))
      restore-tick (fn [cfg]
                     (if tick-present? (assoc cfg :tick-fn tick-value) (dissoc cfg :tick-fn)))
      control {:state state :guard guard :last-follow last-follow :service service
               :config-atom config-atom :focus-var focus-var :frame-var frame-var
               :original-focus original-focus :original-frame original-frame
               :original-tick original-tick :tick-present? tick-present? :tick-value tick-value
               :focus-wrapper focus-wrapper :frame-wrapper frame-wrapper :tick-wrapper tick-wrapper
               :restore-tick restore-tick}]
  (intern 'user 'truth-focus-cadence-native-proof control)
  (try
    (alter-var-root focus-var (fn [f] (assert (identical? f original-focus)) focus-wrapper))
    (alter-var-root frame-var (fn [f] (assert (identical? f original-frame)) frame-wrapper))
    (swap! config-atom
           (fn [cfg]
             (assert (= tick-present? (contains? cfg :tick-fn)) "Tick presence changed during install")
             (assert (identical? tick-value (:tick-fn cfg)) "Tick function changed during install")
             (assoc cfg :tick-fn tick-wrapper)))
    (swap! state assoc :installation :installed)
    {:installed true :world-atom-identity (System/identityHashCode (:world service))
     :tick (:tick @(:world service)) :sim-row-cap 128 :gl-row-cap 128
     :gl-sample-every-frames 30 :max-gl-codes-per-sample 16
     :limits ["Readback observes pre-fold inputs and separate tick outputs; it does not predict published focus."
              "GL error reads consume owner-thread flags and preserve nonzero/truncated/drop counters."
              "Diagnostic locking and bounded recording add overhead; this is not a benchmark."]}
    (catch Throwable error
      ;; Roll back only our installed hooks, preserving unrelated config changes.
      (locking guard
        (swap! state assoc :closed? true :installation :failed :install-error (str error))
        (reset! last-follow nil))
      (swap! config-atom #(if (identical? (:tick-fn %) tick-wrapper) (restore-tick %) %))
      (alter-var-root frame-var #(if (identical? % frame-wrapper) original-frame %))
      (alter-var-root focus-var #(if (identical? % focus-wrapper) original-focus %))
      (throw error))))
