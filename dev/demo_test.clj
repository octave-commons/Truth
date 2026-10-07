(ns demo-test
  "Regression contracts for the native demo's input and setup failure paths."
  (:require [clojure.test :refer [deftest is run-tests testing]]
            [demo :as demo]
            [demo-client :as client]
            [domain.ecs.components :as c]
            [infra.camera :as camera]
            [infra.dev.window :as window]
            [infra.dev.window.loop :as loop]
            [nrepl.core :as nrepl]))

(defn- seed-world [_]
  {:world {:components {c/position {1 [-1.0e15 0.0 0.0] 2 [1.0e15 0.0 0.0]}
                        c/mass {1 1.0e30 2 1.0e30}}}})

(defn- service [config world]
  {:config (atom config) :world world :world-intents (atom nil)
   :camera (atom (camera/make-camera 60.0))})

(defn- queued-service [config initial-world]
  (let [world (atom initial-world)
        queue (java.util.concurrent.ConcurrentLinkedQueue.)]
    (assoc (service config world)
           :world-intents (loop/->IntentAtom queue world)
           :intent-queue queue)))

(deftest missing-form-does-not-connect
  (let [connections (atom 0)]
    (with-redefs [nrepl/connect (fn [& _] (swap! connections inc)
                                  (throw (ex-info "Unexpected connection" {})))
                  shutdown-agents (fn [])]
      (is (thrown-with-msg? IllegalArgumentException #"Usage:.*demo-client"
                            (client/-main)))
      (is (zero? @connections)))))

(deftest stopped-service-does-not-construct-a-world
  (let [seeds (atom 0)]
    (with-redefs [window/service-state (atom nil)
                  demo/scenario-world (fn [_] (swap! seeds inc) {})]
      (is (thrown-with-msg? clojure.lang.ExceptionInfo #"Demo window is not running"
                            (demo/prepare-formation!)))
      (is (zero? @seeds)))))

(deftest wait-failure-restores-exact-pause-settings
  (doseq [prior [{:tick-fn inc :on-step dec :mode :manual}
                 {:tick-fn nil :mode :manual}
                 {:mode :manual}]]
    (testing (str "preserve present, nil, and absent settings: " (keys prior))
      (let [failure (ex-info "World read failed" {})
            reads (atom 0)
            config (atom prior)
            world (reify clojure.lang.IDeref
                    (deref [_]
                      (if (= 1 (swap! reads inc))
                        {}
                        (do (swap! config assoc :independent-change :preserved)
                            (throw failure)))))
            s (assoc (service prior world) :config config)
            original-camera @(:camera s)]
        (with-redefs [window/service-state (atom s) demo/scenario-world seed-world]
          (is (identical? failure (try (demo/prepare-formation!)
                                       (catch Exception e e))))
          (is (= (assoc prior :independent-change :preserved) @config))
          (is (= original-camera @(:camera s))))))))

(deftest timeout-restores-running-pipeline
  (let [prior {:tick-fn inc :on-step dec :mode :manual}
        s (service prior (atom {}))]
    (with-redefs [window/service-state (atom s) demo/scenario-world seed-world]
      ;; Exercise the actual 30-second deadline without a simulation consumer.
      (is (thrown-with-msg? clojure.lang.ExceptionInfo #"Simulation did not accept"
                            (demo/prepare-formation!)))
      (is (= prior @(:config s))))))

(deftest interrupted-wait-restores-absent-settings
  (let [s (service {:mode :manual} (atom {}))]
    (with-redefs [window/service-state (atom s) demo/scenario-world seed-world]
      (.interrupt (Thread/currentThread))
      (try
        (is (thrown? InterruptedException (demo/prepare-formation!)))
        (is (= {:mode :manual} @(:config s)))
        (finally (Thread/interrupted))))))

(deftest interrupted-handoff-restores-settings-without-cancelling-queued-world
  (doseq [prior [{:tick-fn inc :on-step dec :mode :manual}
                 {:tick-fn nil :mode :manual}
                 {:mode :manual}]]
    (testing (str "production intent handoff with prior settings: " (keys prior))
      (let [old-world {:published :original}
            world (atom old-world)
            queue (java.util.concurrent.ConcurrentLinkedQueue.)
            intents (loop/->IntentAtom queue world)
            s (assoc (service prior world) :world-intents intents)
            original-camera @(:camera s)]
        (with-redefs [window/service-state (atom s) demo/scenario-world seed-world]
          (.interrupt (Thread/currentThread))
          (try
            (is (thrown? InterruptedException (demo/prepare-formation!)))
            (is (= prior @(:config s)))
            (is (= original-camera @(:camera s)))
            (is (identical? old-world @world))
            (is (= 1 (.size queue))
                "restoring settings does not cancel the pending world replacement")
            (swap! intents assoc :after-failure :preserved)
            (is (identical? old-world @world)
                "a later intent also waits for the serial consumer")
            (let [drained (@#'infra.dev.window.loop/drain-intents @world queue)]
              (is (identical? old-world @world)
                  "draining prepares the replacement before publication")
              (reset! world drained)
              (is (= (:world (seed-world :nebula))
                     (dissoc @world :demo/capture :after-failure)))
              (is (string? (:demo/capture @world)))
              (is (= :preserved (:after-failure @world)))
              (is (.isEmpty queue))
              (is (= prior @(:config s))))
            (finally (Thread/interrupted))))))))

(deftest successful-formation-stays-paused-and-wide
  (let [world (atom {})
        s (service {:tick-fn inc :on-step dec :mode :manual :selection 1} world)]
    (add-watch (:world-intents s) ::accept-world (fn [_ _ _ queued-world] (reset! world queued-world)))
    (with-redefs [window/service-state (atom s) demo/scenario-world seed-world]
      (let [result (demo/prepare-formation!) cfg @(:config s)]
        (is (:paused? result))
        (is (= (:capture result) (:demo/capture @world)))
        (is (= identity (:tick-fn cfg) (:on-step cfg)))
        (is (= {:mode :fit-all :smoothing 0.0 :volumetric? true}
               (select-keys cfg [:mode :smoothing :volumetric?])))
        (is (not (contains? cfg :selection)))
        (is (pos? (:distance @(:camera s))))))))

(deftest interrupted-selection-restores-settings-without-cancelling-handoff
  (doseq [prior [{:tick-fn inc :on-step dec :mode :manual}
                 {:tick-fn nil :mode :manual}
                 {:mode :manual}]]
    (testing (str "selection restores prior settings: " (keys prior))
      (let [old-world {:demo/scenario :nebula}
            requested-world {:demo/scenario :life :demo/fixture? true}
            {:keys [config camera world world-intents intent-queue] :as s}
            (queued-service prior old-world)
            original-camera @camera]
        (with-redefs [window/service-state (atom s)
                      demo/scenario-world (constantly {:world requested-world})]
          (.interrupt (Thread/currentThread))
          (try
            (is (thrown? InterruptedException (demo/select! :life)))
            (is (= prior @config))
            (is (= original-camera @camera))
            (is (identical? old-world @world))
            (is (= 1 (.size intent-queue)))
            (swap! world-intents assoc :later-input :preserved)
            (reset! world (@#'infra.dev.window.loop/drain-intents @world intent-queue))
            (is (= (assoc requested-world :later-input :preserved) @world)
                "restoration retains the replacement and subsequent FIFO input")
            (is (.isEmpty intent-queue))
            (finally (Thread/interrupted))))))))

(deftest selection-wait-failure-restores-settings-and-preserves-original-error
  (let [prior {:tick-fn inc :on-step dec :mode :manual}
        old-world {:demo/scenario :nebula}
        failure (ex-info "Selection world read failed" {:stage :wait})
        {:keys [config camera world intent-queue] :as s} (queued-service prior old-world)
        original-camera @camera
        failing-read (reify clojure.lang.IDeref
                       (deref [_]
                         (swap! config assoc :independent-change :preserved)
                         (throw failure)))]
    (with-redefs [window/service-state (atom (assoc s :world failing-read))
                  demo/scenario-world (constantly {:world {:demo/scenario :life}})]
      (is (identical? failure (try (demo/select! :life) (catch Exception e e))))
      (is (= (assoc prior :independent-change :preserved) @config))
      (is (= original-camera @camera))
      (is (identical? old-world @world))
      (is (= 1 (.size intent-queue))
          "the read failure follows enqueue and does not cancel the replacement"))))

(defn -main
  "Run demo regression contracts and exit nonzero when any contract fails."
  [& _]
  (let [{:keys [fail error]} (run-tests 'demo-test)]
    (shutdown-agents)
    (System/exit (if (zero? (+ fail error)) 0 1))))
