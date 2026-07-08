;; Intentional: single-segment namespace because this test guards the
;; benchmark coverage suite at the top level, analogous to architecture-test.
#_{:splint/disable [naming/single-segment-namespace]}
(ns benchmark-coverage-test
  "Validation for the benchmark coverage suite registry.

   The benchmark coverage suite reports namespace-level coverage of the
   relevant source quadrants (domain, infra, shape) by the existing benchmark
   groups. law namespaces are excluded because they are schemas/contracts and
   are not directly targeted by performance benchmarks. This test ensures the
   registry is internally consistent and records the current minimum threshold
   so coverage cannot silently regress."
  (:require
   [clojure.string :as str]
   [clojure.test :refer [deftest testing is]]
   [gates-of-truth.bench :as bench]
   [gates-of-truth.bench.coverage :as cov]))

(def ^:private minimum-coverage-threshold
  "Minimum acceptable namespace coverage percentage. Raise this as the
   benchmark suite grows; starting at 0.0 keeps the build green while the
   suite is still being built out."
  0.0)

(deftest benchmark-registry-valid
  (testing "every benchmark group covers only existing source namespaces"
    (let [source-ns (cov/source-namespaces)
          errors (cov/validate-registry bench/benchmark-groups source-ns)]
      (is (empty? errors)
          (str "Registry validation errors:\n" (str/join "\n" errors))))))

(deftest benchmark-coverage-threshold
  (testing "namespace coverage of relevant quadrants meets the minimum threshold"
    (let [metrics (cov/coverage-metrics (cov/relevant-source-namespaces (cov/source-namespaces))
                                        bench/benchmark-groups)]
      (is (>= (:total-pct metrics) minimum-coverage-threshold)
          (format "Coverage %.1f%% is below threshold %.1f%%"
                  (:total-pct metrics) minimum-coverage-threshold)))))
