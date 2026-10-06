;; PLANNED DIAGNOSTIC ONLY. Root must approve and operate the lifecycle stages.
;; Run only in the :stopped-loaded slot prepared by truth-cadence-reload.
;; This uses the actual sim-loop and its existing finite-publication test runner.
;; No native input, physics tick, production-world write or renderer is invoked.
(require 'domain.ecs.components 'domain.player
         'infra.dev.window.lifecycle 'infra.dev.window.loop)

(let [state-var (ns-resolve 'user 'truth-cadence-reload)
      _ (assert state-var "Lifecycle preparation state is missing")
      state-atom @state-var
      d @state-atom
      old (:old d)
      world-atom (:world old)
      config-atom (:config old)
      camera-atom (:camera old)
      queue (:queue d)
      snapshot (:stopped-world d)
      config @config-atom
      resource-keys [:body-program :line-program :sprite-program :hud-program
                     :volume-program :particle-program :mesh :cube-mesh
                     :ui/applied-cursor]
      pending (vec (.toArray ^java.util.concurrent.ConcurrentLinkedQueue queue))
      loop-var (ns-resolve 'infra.dev.window.loop 'sim-loop)
      drain-var (ns-resolve 'infra.dev.window.loop 'drain-intents)
      variants {:before {:loop (:old-sim-loop d) :drain (:old-drain-intents d)}
                :after {:loop (:new-sim-loop d) :drain (:new-drain-intents d)}}
      guard! (fn []
               (assert (= :stopped-loaded (:stage @state-atom)) "Wrong lifecycle stage")
               (assert (nil? @infra.dev.window.lifecycle/service-state) "Native service must remain stopped")
               (assert @(:stop old) "Original stop flag must remain set")
               (assert (not (.isAlive ^Thread (:thread old))) "Original render worker is still alive")
               (assert (not (.isAlive ^Thread (:sim-thread old))) "Original sim worker is still alive")
               (assert (identical? old (:old @state-atom)) "Original service references changed")
               (assert (identical? queue (:queue @state-atom)) "Original intent queue changed")
               (assert (identical? snapshot @world-atom) "Production world value changed")
               (assert (identical? config @config-atom) "Production config value changed")
               (assert (identical? (:stopped-camera d) @camera-atom) "Production camera value changed")
               (assert (= pending (vec (.toArray ^java.util.concurrent.ConcurrentLinkedQueue queue)))
                       "Original pending intents changed")
               (assert (identical? (:new-sim-loop d) @loop-var) "Repaired sim-loop root was not restored")
               (assert (identical? (:new-drain-intents d) @drain-var) "Repaired drain root was not restored"))
      _ (guard!)
      _ (assert (= (apply dissoc (:stopped-config d) resource-keys)
                   (apply dissoc config resource-keys))
                "Lifecycle cleanup changed user settings rather than only retired GL resources")
      _ (assert (every? fn? (mapcat vals (vals variants))) "Missing original/repaired callables")
      _ (assert (not (identical? (:old-sim-loop d) (:new-sim-loop d))) "The loop was not reloaded")
      _ (assert (not (identical? (:old-drain-intents d) (:new-drain-intents d))) "The drain was not reloaded")
      _ (assert (map? snapshot) "Stopped world must be a complete ECS map")
      _ (assert (not (:demo/fixture? snapshot)) "Refuse a demo fixture as mature natural-world evidence")
      _ (assert (= 1 (count (get-in snapshot [:components domain.ecs.components/observer])))
                "A singleton observer is required to measure manual attention")
      _ (assert (some? (domain.player/observer-position snapshot)) "The observer has no physical position")
      _ (assert (nil? (:ui/error-state config)) "Do not measure a service with an unresolved UI error")
      _ (assert (= (:pending-intents d) (count pending)) "Queue changed after lifecycle stop")
      ^java.lang.management.ThreadMXBean cpu-bean (java.lang.management.ManagementFactory/getThreadMXBean)
      _ (assert (.isCurrentThreadCpuTimeSupported cpu-bean) "Current-thread CPU timing is unavailable")
      _ (assert (.isThreadCpuTimeEnabled cpu-bean) "CPU timing is disabled; do not change global settings")
      _ (assert (instance? com.sun.management.ThreadMXBean cpu-bean) "Thread allocation timing is unavailable")
      ^com.sun.management.ThreadMXBean allocation-bean cpu-bean
      _ (assert (.isThreadAllocatedMemorySupported allocation-bean) "Thread allocation counting is unsupported")
      _ (assert (.isThreadAllocatedMemoryEnabled allocation-bean) "Allocation counting is disabled; do not enable it here")
      thread-id (.getId (Thread/currentThread))
      test-path "/home/err/spaces/foresight/.worktrees/truth-focus-cadence/test/infra/dev/focus_cadence_test.clj"
      test-bytes (java.nio.file.Files/readAllBytes
                  (java.nio.file.Paths/get test-path (make-array String 0)))
      test-hash (format "%064x" (java.math.BigInteger.
                                 1 (.digest (java.security.MessageDigest/getInstance "SHA-256") test-bytes)))
      _ (assert (= "4d2019a18d4f5c66ea1f49082af1ce28301fbb4e38d952bd5ad90072e8176f68" test-hash)
                "Existing host-loop runner changed after the verified RED/GREEN run")
      _ (load-file test-path)
      runner @(ns-resolve 'infra.dev.focus-cadence-test 'run-iterations)
      controls (select-keys config [:focus-offset :sim-frame-interval])
      gc-state (fn []
                 (mapv (fn [^java.lang.management.GarbageCollectorMXBean gc]
                         {:name (.getName gc) :collections (.getCollectionCount gc)
                          :collection-ms (.getCollectionTime gc)})
                       (java.lang.management.ManagementFactory/getGarbageCollectorMXBeans)))
      measure (fn [mode variant publications phase pair]
                (guard!)
                (let [fns (get variants variant)
                      sample
                      (with-redefs-fn {loop-var (:loop fns) drain-var (:drain fns)}
                        (fn []
                          (let [cpu0 (.getCurrentThreadCpuTime cpu-bean)
                                alloc0 (.getThreadAllocatedBytes allocation-bean thread-id)
                                wall0 (System/nanoTime)
                                result (runner snapshot publications
                                               {:config (assoc controls :mode mode)
                                                :tick-fn identity})
                                wall1 (System/nanoTime)
                                alloc1 (.getThreadAllocatedBytes allocation-bean thread-id)
                                cpu1 (.getCurrentThreadCpuTime cpu-bean)
                                final-world (peek (:published result))]
                            (assert (= publications (count (:published result)) (count (:inputs result)))
                                    "The actual loop did not execute every requested identity tick")
                            (assert (= "" (:errors result)) "The detached loop reported an intent failure")
                            (assert (empty? (:service result)) "The detached loop reported a service error")
                            (assert (nil? (:ui/error-state (:config result))) "The detached loop entered its error state")
                            (assert (= (dissoc snapshot :components) (dissoc final-world :components))
                                    "Detached measurement changed clocks, frame data or other world metadata")
                            (assert (= (dissoc (:components snapshot) domain.ecs.components/observer)
                                       (dissoc (:components final-world) domain.ecs.components/observer))
                                    "Detached measurement changed physical or other non-attention components")
                            (assert (= (if (and (= :after variant) (= :manual mode))
                                         (domain.player/get-observer
                                          (domain.player/focus-follow snapshot (:focus-offset controls [0.0 0.0 0.0])))
                                         (domain.player/get-observer snapshot))
                                       (domain.player/get-observer final-world))
                                    "Attention result differs from the expected source variant")
                            (assert (<= 0 cpu0 cpu1) "CPU counter is invalid")
                            (assert (<= 0 alloc0 alloc1) "Allocation counter is invalid")
                            {:mode mode :variant variant :phase phase :pair pair
                             :publications publications
                             :wall-ns (- wall1 wall0) :cpu-ns (- cpu1 cpu0)
                             :allocated-bytes (- alloc1 alloc0)
                             :cpu-ns-per-publication (/ (double (- cpu1 cpu0)) publications)
                             :bytes-per-publication (/ (double (- alloc1 alloc0)) publications)})))]
                  (guard!)
                  sample))
      started-ms (System/currentTimeMillis)
      gc-before (gc-state)
      measurements
      (mapv (fn [mode]
              {:mode mode
               :warmup (mapv #(measure mode % 64 :warmup nil) [:before :after])
               :samples (vec (mapcat (fn [[pair order]]
                                       (mapv #(measure mode % 128 :measurement pair) order))
                                     [[1 [:before :after]]
                                      [2 [:after :before]]
                                      [3 [:before :after]]]))})
            [:manual :follow-selection])]
  (guard!)
  {:measurement-kind :detached-host-loop-observation
   :source-before "10f1a81767d7d704a5b36e454b6557089fa4a77e"
   :source-after "1ad081475364cc3b75db76142204cbf54b5bf60c"
   :native-original-loop-source "400ba3a (loop source identical to 10f1a817)"
   :runner-source-sha256 test-hash
   :started-wall-ms started-ms :finished-wall-ms (System/currentTimeMillis)
   :measurement-thread (.getName (Thread/currentThread))
   :shared-heap? true :physics-stepped? false :native-fps-measured? false
   :render-intents :none :tick-operation :identity
   :controls controls :original-mode (:mode config)
   :fixture (assoc (select-keys snapshot [:tick :genesis/sim-time :sim/dt
                                          :genesis/frame-offset :demo/fixture? :demo/scenario])
                   :alive-count (count (:alive snapshot))
                   :observer-count (count (get-in snapshot [:components domain.ecs.components/observer]))
                   :world-identity (System/identityHashCode snapshot)
                   :world-atom-identity (System/identityHashCode world-atom)
                   :config-atom-identity (System/identityHashCode config-atom)
                   :queue-identity (System/identityHashCode queue))
   :gc-before gc-before :gc-after (gc-state)
   :measurements measurements
   :preserved-production-state? true
   :preserved-user-settings? true
   :new-loop-roots-restored? true
   :limits ["No-render identity ticks isolate host preparation; this is not native gameplay throughput."
            "Current-thread CPU excludes loop sleep but shares the JVM heap and JIT; report paired spread."
            "Harness setup/watch/sample bookkeeping is identical in both variants and included in measured cost."
            "The complete native world stays in memory, including handlers and records; no serialization or stripping."]})
