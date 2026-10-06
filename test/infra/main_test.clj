(ns infra.main-test
  "Regression contracts for the console and PNG launch routes removed in 0a9343a."
  (:require
   [clojure.edn :as edn]
   [clojure.test :refer [deftest is testing]]
   [domain.arc :as arc]
   [domain.genesis :as genesis]
   [infra.main :as main]
   [infra.render :as render]))

(deftest run-alias-resolves-an-entry-point
  (is (= ["-m" "infra.main"]
         (get-in (edn/read-string (slurp "deps.edn")) [:aliases :run :main-opts])))
  (is (fn? main/-main)))

(deftest console-advances-the-real-world-and-stops-at-its-budget
  (let [world (genesis/create-world {:gas-count 4})
        result (atom nil)
        output (with-redefs [genesis/create-world (constantly world)]
                 (with-out-str
                   (reset! result (main/run-phase0-simulation 2))))]
    (is (= 2 (:tick @result)))
    (is (pos? (:genesis/sim-time @result)))
    (is (keyword? (:arc/current @result)))
    (is (re-find #"tick budget reached" output))
    (is (re-find #"t=0" output))))

(deftest console-preserves-terminal-world
  (let [world (genesis/create-world {:gas-count 4})
        result (atom nil)
        output (with-redefs [genesis/create-world (constantly world)
                             arc/genesis-ending (constantly {:message "Recorded ending"})
                             arc/tick-genesis (fn [_] (throw (ex-info "Must not tick" {})))]
                 (with-out-str
                   (reset! result (main/run-phase0-simulation 2))))]
    (is (zero? (:tick @result)))
    (is (re-find #"Recorded ending" output))))

(deftest png-route-uses-the-real-genesis-and-existing-renderer
  (let [world (genesis/create-world {:gas-count 4})
        capture (atom nil)]
    (with-redefs [genesis/create-world (constantly world)
                  render/render-to-file (fn [world-atom path options]
                                          (reset! capture [@world-atom path options])
                                          path)]
      (is (= "/tmp/truth-view.png" (main/run-render-demo))))
    (let [[rendered path options] @capture]
      (is (= world rendered))
      (is (= "/tmp/truth-view.png" path))
      (is (= arc/tick-genesis (:tick-fn options)))
      (is (= render/phase0-bodies+fields (:bodies-fn options))))))

(deftest cli-dispatches-only-documented-modes
  (let [calls (atom [])
        shutdowns (atom 0)]
    (with-redefs [main/run-phase0-simulation #(swap! calls conj [:console %])
                  main/run-render-demo #(swap! calls conj [:png])
                  shutdown-agents #(swap! shutdowns inc)]
      (main/-main)
      (main/-main "console" "2")
      (main/-main "demo")
      (is (= [[:console 1000] [:console 2] [:png]] @calls))
      (testing "invalid modes, extra arguments, and invalid budgets never start a run"
        (doseq [args [["unknown"] ["demo" "unexpected"] ["console" "-1"]
                      ["console" "1.5"] ["console" "2" "unexpected"]]]
          (is (thrown-with-msg? clojure.lang.ExceptionInfo #"Usage:"
                                (apply main/-main args)))))
      (is (= 8 @shutdowns)))))
