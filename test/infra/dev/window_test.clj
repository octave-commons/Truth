(ns infra.dev.window-test
  "Tests for the dev window's error-recovery helpers."
  (:require
   [clojure.java.io :as io]
   [clojure.string :as str]
   [clojure.test :refer [deftest is testing use-fixtures]]
   [domain.ecs.core :as ecs]
   [domain.ecs.event :as event]
   [infra.dev.window]))

(use-fixtures :each
  (fn [test]
    (test)
    ;; Clean up any dumps this test wrote.
    (let [dir (io/file "/tmp" "gates-of-truth" "dumps")]
      (when (.exists dir)
        (doseq [f (.listFiles dir)]
          (when (str/starts-with? (.getName f) "truth-error-")
            (.delete f)))))))

(deftest dump-error-artifacts-persists-world-and-ledger
  (testing "a world + its ledger are written to timestamped EDN files"
    (let [w     (-> (ecs/empty-world)
                    (assoc :tick 42)
                    (event/with-ledger)
                    (event/emit {:tick 42 :kind :test/thud}))
          dump  (@#'infra.dev.window/dump-error-artifacts! w)]
      (is (.exists (io/file (:world-path dump))))
      (is (.exists (io/file (:ledger-path dump))))
      (is (.exists (io/file (:meta-path dump))))
      (is (= 42 (-> (slurp (:world-path dump)) read-string :tick)))
      (is (= :test/thud (-> (slurp (:ledger-path dump)) read-string :events first :kind)))
      (is (= 42 (-> (slurp (:meta-path dump)) read-string :tick))))))

(deftest dump-error-artifacts-graceful-on-failure
  (testing "if writing fails the function returns an error map instead of throwing"
    (let [w (assoc (ecs/empty-world) :tick 1)]
      (with-redefs [spit (fn [& _] (throw (ex-info "disk full" {})))]
        (let [dump (@#'infra.dev.window/dump-error-artifacts! w)]
          (is (contains? dump :error)))))))

(deftest log-frame-error-includes-tick-and-paths
  (testing "the error log line contains the failing tick and dump paths"
    (let [out  (java.io.StringWriter.)
          err  (ex-info "boom" {:x 1})
          dump {:world-path "/tmp/w.edn" :ledger-path "/tmp/l.edn"}]
      (binding [*err* out]
        (@#'infra.dev.window/log-frame-error! err 123 dump))
      (let [line (str out)]
        (is (re-find #"tick=123" line))
        (is (re-find #"world=/tmp/w\.edn" line))
        (is (re-find #"ledger=/tmp/l\.edn" line))))))
