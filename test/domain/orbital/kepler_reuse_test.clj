(ns domain.orbital.kepler-reuse-test
  "Numerical contracts for a performance-only Stumpff reuse change."
  (:require
   [clojure.edn :as edn]
   [clojure.test :refer [deftest is testing]]
   [domain.orbital.kepler :as kepler]
   [domain.orbital.kepler-workload :as workload]
   [shape.spatial :as sp]))

(defn- baseline []
  (edn/read-string (slurp "test/fixtures/kepler-reuse-b402939.edn")))

(deftest public-trajectories-match-pinned-baseline-bits
  (is (= (:public (baseline)) (workload/public-oracle))
      "Every state or declared failure matches the source-pinned baseline; no tolerance relaxation")
  (is (= (:edges (baseline)) (workload/edge-oracle))))

(deftest compact-fold-components-match-pinned-baseline-bits
  (let [actual (workload/compact-oracle)]
    (is (= (:compact (baseline)) actual)
        "Map and SoA writer trajectories preserve every component after each real fold")
    (doseq [{:keys [path scenario gates]} actual]
      (is (every? :pass? gates) (str path " " scenario " must exercise actual Kepler path"))
      (when (= scenario :moving-recentered)
        (is (pos? (sp/len (:frame-offset (first gates))))
            "The spatial index actually computes a nonzero recentering offset")))))

(deftest circular-state-matches-independent-analytic-solution
  (doseq [dt [0.001 0.2 1.5 3.0 6.0]]
    (let [actual (kepler/propagate 1.0 [1.0 0.0 0.0] [0.0 1.0 0.0] dt)
          expected (workload/circular-state dt)]
      (is (< (sp/dist (:position actual) (:position expected)) 1.0e-8))
      (is (< (sp/dist (:velocity actual) (:velocity expected)) 1.0e-8)))))

(deftest positive-trajectories-conserve-energy-and-angular-momentum
  (doseq [{:keys [id mu r v] :as input} workload/cases]
    (testing (name id)
      (let [{:keys [states error]} (workload/public-trajectory input 1.0)
            initial {:position r :velocity v}
            e0 (workload/energy mu initial)
            energy-scale (/ mu (sp/len r))
            h0 (workload/angular-momentum initial)
            h-scale (sp/len h0)]
        (is (nil? error) (str "Positive-time baseline must be a usable trajectory: " error))
        (is (= (inc workload/trajectory-steps) (count states)))
        (doseq [state states]
          (is (< (/ (abs (- (workload/energy mu state) e0)) energy-scale) 1.0e-8)
              "Dimensionless absolute energy error also covers nearly parabolic E≈0")
          (is (< (/ (sp/dist (workload/angular-momentum state) h0) h-scale) 1.0e-8)))))))

(deftest positive-time-velocity-reversal-retraces-the-public-orbit
  (doseq [{:keys [id mu r v dt]} workload/cases]
    (testing (name id)
      (let [forward (kepler/propagate mu r v dt)
            backward (kepler/propagate mu (:position forward)
                                       (sp/v* (:velocity forward) -1.0) dt)]
        (is (< (/ (sp/dist (:position backward) r) (sp/len r)) 1.0e-7))
        (is (< (/ (sp/dist (:velocity backward) (sp/v* v -1.0)) (sp/len v)) 1.0e-7))))))

(deftest zero-time-and-invalid-input-contracts-are-preserved
  (let [r [0.0 -0.0 0.0] v [1.0 2.0 3.0]]
    (is (= (workload/numeric-bits {:position r :velocity v})
           (workload/numeric-bits (kepler/propagate -1.0 r v 0.0)))
        "Existing zero-dt branch returns before invalid-mu/zero-radius checks")
    (is (thrown-with-msg? clojure.lang.ExceptionInfo #"non-positive gravitational parameter"
                          (kepler/propagate 0.0 [1.0 0.0 0.0] v 1.0)))
    (is (thrown-with-msg? clojure.lang.ExceptionInfo #"degenerate relative state"
                          (kepler/propagate 1.0 r v 1.0)))))
