(ns demo-test
  "Regression contracts for the native demo's input and setup failure paths."
  (:require [clojure.test :refer [deftest is run-tests testing]]
            [demo :as demo]
            [demo-client :as client]
            [domain.ecs.components :as c]
            [infra.camera :as camera]
            [infra.dev.window :as window]
            [nrepl.core :as nrepl]))

(defn- seed-world [_]
  {:world {:components {c/position {1 [-1.0e15 0.0 0.0] 2 [1.0e15 0.0 0.0]}
                        c/mass {1 1.0e30 2 1.0e30}}}})

(defn- service [config world]
  {:config (atom config) :world world :world-intents (atom nil)
   :camera (atom (camera/make-camera 60.0))})

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

(defn -main
  "Run demo regression contracts and exit nonzero when any contract fails."
  [& _]
  (let [{:keys [fail error]} (run-tests 'demo-test)]
    (shutdown-agents)
    (System/exit (if (zero? (+ fail error)) 0 1))))
