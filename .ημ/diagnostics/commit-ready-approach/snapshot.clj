;; Read-only diagnostic. Evaluates to a function; creates no server Vars/hooks.
(require '[clojure.string :as string]
         '[domain.ecs.core :as ecs]
         '[domain.ecs.components :as c]
         '[domain.player :as player]
         '[domain.stellar.classifier.planet :as planet]
         '[domain.stellar.classifier.candidate :as candidate]
         '[domain.narrowing :as narrowing]
         '[law.narrowing :as law]
         '[shape.spatial :as spatial]
         '[infra.menu :as menu]
         '[infra.menu.widgets :as widgets]
         '[domain.arc :as arc]
         '[infra.dev.window :as window]
         '[infra.render :as render])

(fn [expected]
  (let [read-proc (fn [path]
                    (with-open [reader (java.io.RandomAccessFile. path "r")]
                      (let [buffer (byte-array 4096)
                            n (.read reader buffer)]
                        (assert (< 0 n 4096) "Incomplete/oversized proc read")
                        (let [value (String. buffer 0 n java.nio.charset.StandardCharsets/UTF_8)]
                          (assert (string/ends-with? value "\n") "Incomplete proc line")
                          value))))
        stat (read-proc "/proc/self/stat")
        fields (string/split (subs stat (+ 2 (.lastIndexOf stat ")"))) #"\s+")
        _ (assert (= (:pid expected) (.pid (java.lang.ProcessHandle/current))) "Wrong JVM")
        _ (assert (= (:boot expected) (string/trim (read-proc "/proc/sys/kernel/random/boot_id"))) "Wrong boot")
        _ (assert (= (:start expected) (nth fields 19)) "Wrong process start")
        _ (assert (= (:cwd expected) (.getCanonicalPath (java.io.File. "."))) "Wrong cwd")
        _ (assert (= (:display expected) (System/getenv "DISPLAY")) "Wrong display")
        _ (assert (= (:port expected) (System/getenv "TRUTH_DEMO_PORT")) "Wrong port")
        ;; Only initial inspection waits for ordinary asynchronous GL setup.
        ;; Later inspections fail immediately instead of accepting a replacement.
        s (loop [attempt 0]
            (let [service @window/service-state
                  cfg (some-> service :config deref)]
              (when-not (:world expected)
                (println "TRUTH_APPROACH_READY" attempt (boolean (:window service))
                         (boolean (:body-program cfg)) (boolean (:volume-program cfg))))
              (if (and (not (:world expected))
                       (not (and (:window service) (:body-program cfg) (:volume-program cfg)))
                       (not (:error service)) (not (:ui/error-state cfg))
                       (< attempt 100))
                (do (Thread/sleep 100) (recur (inc attempt)))
                service)))
        _ (assert s "No service")
        _ (assert (and (:window s) (pos? (:window s))) "No window")
        world-id (System/identityHashCode (:world s))
        _ (when (:world expected) (assert (= (:world expected) world-id) "World atom replaced"))
        _ (when (:window expected) (assert (= (:window expected) (:window s)) "Window replaced"))
        w @(:world s)
        cfg @(:config s)
        camera @(:camera s)
        _ (assert (and (:body-program cfg) (:volume-program cfg)) "Renderer setup incomplete")
        _ (assert (= :nebula (:demo/scenario w)) "Not ordinary nebula")
        _ (assert (false? (:demo/fixture? w)) "Fixture/unknown provenance")
        _ (assert (identical? (:tick-fn cfg) arc/tick-genesis) "Tick changed")
        _ (assert (identical? (:bodies-fn cfg) render/phase0-bodies+fields) "Projection changed")
        _ (assert (<= (.maxMemory (Runtime/getRuntime)) 2147483648) "Heap exceeds 2 GiB")
        _ (assert (and (.isAlive ^Thread (:thread s)) (.isAlive ^Thread (:sim-thread s))) "Worker dead")
        _ (assert (not @(:stop s)) "Service stopped")
        _ (assert (nil? (:error s)) "Service error")
        _ (assert (nil? (:ui/error-state cfg)) "UI error")
        oid (player/observer-entity w)
        observer (player/get-observer w)
        getc #(ecs/get-component w %1 %2)
        position (getc oid c/position)
        velocity (getc oid c/velocity)
        stars (planet/stellar-bodies w)
        handoff ((:run (candidate/handoff-system)) w)
        committed (->> (get-in w [:components c/commitment-state])
                       (keep (fn [[eid state]] (when (= :committed state) eid)))
                       sort vec)
        ids (->> (ecs/entities-with w c/matter-state c/position)
                 (filter #(contains? #{:planet :gas-giant} (getc % c/matter-state)))
                 sort vec)
        menu-data (menu/menu-hud cfg w 1280 720)
        thrust-knob (first (filter #(= :genesis/spark-flight-displacement (:key %)) widgets/spark-knobs))
        actions {(widgets/knob-action thrust-knob :down) "thrust-down"
                 (widgets/knob-action thrust-knob :up) "thrust-up"
                 [:ui/toggle-domain :spark] "spark"
                 [:spark/flight-range :fine] "fine"
                 [:spark/flight-range :cruise] "cruise"}]
    ;; Tiny framing protocol for the external operator, not a world serializer.
    (println "TRUTH_APPROACH_ID" world-id (:window s))
    (println "TRUTH_APPROACH_VIEW" (name (:mode cfg)) (boolean (:ui/cursor-free? cfg)))
    (println "TRUTH_INPUT_STATE"
             (name (:mode cfg)) (boolean (:ui/cursor-free? cfg))
             (if-let [active (:ui/active-domain cfg)] (name active) "none")
             (get w :genesis/spark-flight-displacement player/default-displacement-per-tick)
             (:yaw camera) (:pitch camera) (:look-sensitivity cfg 0.02)
             (boolean (and (= :manual (:mode cfg))
                           (not (:ui/cursor-free? cfg)) (not (:ui/active-domain cfg))
                           (= org.lwjgl.glfw.GLFW/GLFW_CURSOR_DISABLED (:ui/applied-cursor cfg))))
             (contains? cfg :pick-request))
    (println "TRUTH_INPUT_CURSOR" (first (:cursor cfg)) (second (:cursor cfg)))
    (apply println "TRUTH_FLIGHT"
           (concat [(:tick w) (:genesis/sim-time w) (:sim/dt w) oid]
                   position velocity (or (:player/thrust w) [nil nil nil])))
    (apply println "TRUTH_COMMITMENT" (:tick w) (count committed) committed)
    (println "TRUTH_THRUST_KNOB" (:down thrust-knob) (:up thrust-knob) (:lo thrust-knob) (:hi thrust-knob))
    (doseq [eid (take 128 ids)
            :let [p (getc eid c/position)
                  v (getc eid c/velocity)]]
      (apply println "TRUTH_COMMIT_TARGET"
             (concat [eid (name (getc eid c/matter-state))
                      (some? (getc eid c/planet-candidate))
                      (boolean (narrowing/ready-to-commit? w eid))
                      (contains? (get handoff c/planet-candidate) eid)]
                     p (or v [nil nil nil]))))
    (doseq [{:keys [id displacement]} widgets/spark-flight-ranges]
      (println "TRUTH_INPUT_PRESET" (name id) displacement))
    (doseq [{:keys [action x0 y0 x1 y1]} (:hits menu-data)
            :let [label (get actions action)]
            :when label]
      (println "TRUTH_APPROACH_HIT" label (int (/ (+ x0 x1) 2)) (int (/ (+ y0 y1) 2))))
    {:observation :one-published-world-not-the-binding-consumer-input
     :wall-ms (System/currentTimeMillis)
     :identity {:pid (:pid expected) :boot (:boot expected) :start (:start expected)
                :world-atom world-id :window (:window s) :display (:display expected)
                :max-heap-bytes (.maxMemory (Runtime/getRuntime))}
     :world (select-keys w [:tick :sim/dt :genesis/sim-time :arc/current :demo/scenario :demo/fixture?])
     :state-counts (frequencies (vals (get-in w [:components c/matter-state])))
     :spark {:eid oid :position position :velocity velocity :observer observer
             :thrust (:player/thrust w)
             :binding (getc oid c/binding) :scar (getc oid c/binding-scar)
             :commitment (getc oid c/commitment-state) :palette (getc oid c/palette)
             :displacement (get w :genesis/spark-flight-displacement player/default-displacement-per-tick)
             :retention (get w :genesis/spark-damping-retention player/default-damping-retention)}
     :global-commitment {:tick (:tick w) :count (count committed) :eids committed}
     :target-admission :stored-candidate-and-current-ready-to-commit
     :production-handoff-write-set handoff
     :body-coverage {:total (count ids) :recorded (min 128 (count ids)) :omitted (max 0 (- (count ids) 128))}
     :bodies (mapv (fn [eid]
                     (let [p (getc eid c/position)
                           v (getc eid c/velocity)
                           parent (planet/dominant-attractor w eid stars)]
                       {:eid eid :matter-state (getc eid c/matter-state)
                        :position p :velocity v
                        :relative-position (when position (spatial/v- p position))
                        :relative-velocity (when (and velocity v) (spatial/v- v velocity))
                        :physical-distance-au (when position (/ (spatial/dist p position) 1.495978707e11))
                        :published-focus-overlap? (when (:focus-position observer)
                                                    (narrowing/focus-overlap? (:focus-position observer) p law/world-focus-radius))
                        :overlap-radius law/world-focus-radius
                        :current-parent parent
                        :per-body-eligible? (when parent (@#'candidate/eligible-candidate? w parent eid))
                        :stored-candidate (getc eid c/planet-candidate)
                        :ready-to-commit? (boolean (narrowing/ready-to-commit? w eid))
                        :commitment (getc eid c/commitment-state)}))
                   (take 128 ids))
     :host-sampled-separately {:config (select-keys cfg [:mode :selection :follow-eid :focus-offset :ui/active-domain :ui/cursor-free?
                                                               :cursor :look-sensitivity :ui/applied-cursor :pick-request])
                              :camera (into {} camera)
                              :menu (select-keys menu-data [:text :hits :regions])}
     :limits [:published-focus-may-lag-physical-output :no-sustained-consumer-overlap-proof
              :stored-candidate-is-not-fresh-handoff :no-native-fps-claim]}))
