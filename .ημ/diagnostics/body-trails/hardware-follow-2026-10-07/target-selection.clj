;; READ ONLY. Execute once only after root releases/resumes the fresh service.
;; Published input world and camera are separate reads; this is not a render frame.
(require '[clojure.string :as str]
         '[domain.ecs.components :as c]
         '[domain.ecs.core :as ecs]
         '[infra.render.units :as units]
         '[infra.camera :as camera]
         '[law.trail :as trail-law]
         '[shape.spatial :as sp]
         'infra.dev.window.lifecycle)
(let [read-proc (fn [path]
                  (with-open [r (java.io.RandomAccessFile. path "r")]
                    (let [buffer (byte-array 4096)
                          n (.read r buffer)]
                      (assert (< 0 n 4096) "Empty or oversized proc line")
                      (let [value (String. buffer 0 n java.nio.charset.StandardCharsets/UTF_8)]
                        (assert (str/ends-with? value "\n") "Incomplete proc line")
                        value))))
      pid (.pid (java.lang.ProcessHandle/current))
      service @infra.dev.window.lifecycle/service-state
      _ (assert (= 131581 pid) "Unexpected fresh process")
      _ (assert (= "b04f7fb6-ea65-4b47-8866-73e24a6887f6"
                   (str/trim (read-proc "/proc/sys/kernel/random/boot_id"))) "Unexpected boot")
      stat (read-proc "/proc/self/stat")
      stat-fields (str/split (subs stat (+ 2 (.lastIndexOf stat ")"))) #"\s+")
      _ (assert (= "125503" (nth stat-fields 19)) "Process start changed")
      _ (assert (= "/home/err/spaces/foresight/.worktrees/truth-validation"
                   (System/getProperty "user.dir")) "Unexpected cwd")
      _ (assert (= ":0" (System/getenv "DISPLAY")) "Unexpected display")
      _ (assert (= "7896" (System/getenv "TRUTH_DEMO_PORT")) "Unexpected port")
      _ (assert (= 919497450 (System/identityHashCode (:world service))) "Unexpected world")
      _ (assert (= 137768861339584 (:window service)) "Unexpected native window")
      _ (assert (and (.isAlive ^Thread (:thread service))
                     (.isAlive ^Thread (:sim-thread service))
                     (not @(:stop service))) "Service workers unavailable")
      world @(:world service)
      cfg @(:config service)
      cam @(:camera service)
      _ (assert (and (nil? (:error service)) (nil? (:ui/error-state cfg))) "Service has errors")
      _ (assert (and (= :nebula (:demo/scenario world))
                     (false? (:demo/fixture? world))) "Not the natural nebula world")
      previous-var (ns-resolve 'user 'truth-native-current-frame-observer)
      _ (assert previous-var "Prior frame observer missing")
      previous @previous-var
      _ (assert (and (not (:active? @(:state previous)))
                     (identical? @(:scene-var previous) (:original-scene previous)))
                "Prior renderer hook not restored")
      _ (assert (nil? (ns-resolve 'user 'truth-follow-trail-observer)) "Follow observer already exists")
      candidates (->> (get-in world [:components c/matter-state])
                      (filter (fn [[_ state]] (#{:star :planet} state)))
                      (sort-by key) vec)
      selected (take 64 candidates)
      dimensions {:width 2536 :height 1490}
      ctx (units/make-context cam dimensions)
      rows (mapv
            (fn [[eid kind]]
              (let [position (ecs/get-component world eid c/position)
                    radius (ecs/get-component world eid c/radius)
                    history (ecs/get-component world eid c/motion-trail)
                    valid? (trail-law/valid-trail? history)
                    ready? (and position (number? radius) (pos? radius)
                                valid? (<= 2 (count (:samples history))))]
                (merge {:eid eid :kind kind :body-kind (ecs/get-component world eid c/body-kind)
                        :position position :physical-radius radius :history history
                        :production-valid-history? valid? :framing-ready? (boolean ready?)}
                       (when ready?
                         (let [r (apply max (map #(sp/dist position (:position %)) (:samples history)))
                               ru (/ r (:scale ctx))
                               minimum (camera/min-approach-distance
                                        (units/phys->body-render-radius ctx radius))
                               fit (camera/distance-for-radius ru 60.0 1.6)]
                           {:planning-history-radius-metres r :planning-history-radius-render-units ru
                            :planning-fit-distance fit :planning-minimum-distance minimum
                            :planning-chosen-distance (max minimum fit)}))))) selected)]
  {:pid pid :boot-id "b04f7fb6-ea65-4b47-8866-73e24a6887f6" :start-ticks 125503
   :wall-ms (System/currentTimeMillis) :world-atom-identity 919497450 :window (:window service)
   :world (select-keys world [:tick :genesis/sim-time :sim/dt :genesis/frame-offset
                             :arc/current :demo/scenario :demo/fixture?])
   :camera-separate-read cam
   :view-fields (into {} (map (fn [k] [k {:present? (contains? cfg k) :value (get cfg k)}])
                             [:mode :selection :follow-eid :zoom-min]))
   :planning-framebuffer-from-prior-capture dimensions
   :dimensions-are-current-frame? false :candidate-count (count candidates)
   :returned-count (count rows) :omitted-count (- (count candidates) (count rows))
   :candidates rows :claim "Read-only candidate/history snapshot; no follow or capture executed."})
