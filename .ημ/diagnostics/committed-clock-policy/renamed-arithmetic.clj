;; Isolated arithmetic research, not production physics or an executed RED test.
;; GPL-3.0-or-later.
(require '[clojure.pprint :as pp])

(def h (/ 1.0 60.0))
(defn precision-row [t]
  {:epoch-seconds t :ulp-seconds (Math/ulp (double t))
   :requested-step-seconds h :observed-single-delta (- (+ t h) t)
   :observed-sixty-step-delta (- (nth (iterate #(+ % h) t) 60) t)})

;; Exact integer accounting example with caller-supplied 10 ms quantum and
;; four-step budget; neither constant is a proposed production integration step.
(def quantum-ns 10000000)
(def max-steps 4)
(defn credit-step [{:keys [credit-ns elapsed-ns] :as state}
                  {:keys [wall-interval-ns paused?]}]
  (if paused?
    (assoc state :used-ns 0 :steps 0 :paused? true)
    (let [available (+ credit-ns wall-interval-ns)
          steps (min max-steps (quot available quantum-ns))
          used (* steps quantum-ns)]
      {:credit-ns (- available used) :elapsed-ns (+ elapsed-ns used)
       :used-ns used :steps steps :paused? false})))

(let [precision (mapv precision-row [0.0 1.0e10 1.0e12 1.0e14 4.0e14 1.0e15])
      inputs [{:wall-interval-ns 20000000}
              {:wall-interval-ns 20000000}
              {:wall-interval-ns 100000000}
              {:wall-interval-ns 9000000000 :paused? true}
              {:wall-interval-ns 0}
              {:wall-interval-ns 0}]
      trace (vec (rest (reductions credit-step {:credit-ns 0 :elapsed-ns 0} inputs)))
      expected-active 140000000
      finite-position (mapv (fn [x] {:position-metres x :ulp-metres (Math/ulp (double x))
                                    :requested-delta-metres 1.0
                                    :observed-delta-metres (- (+ x 1.0) x)})
                            [1.0e12 1.0e16 1.0e17 1.0e20])]
  (assert (zero? (:observed-sixty-step-delta (nth precision 4))))
  (assert (= expected-active (:elapsed-ns (peek trace))))
  (assert (zero? (:credit-ns (peek trace))))
  (assert (= (:credit-ns (nth trace 2)) (:credit-ns (nth trace 3))))
  (pp/pprint {:kind :isolated-arithmetic-not-gameplay
              :runtime {:java (System/getProperty "java.version")
                        :clojure (clojure-version)}
              :precision precision :credit-inputs inputs :credit-trace trace
              :total-wall-interval-ns expected-active
              :total-consumed-ns (:elapsed-ns (peek trace))
              :remaining-credit-ns (:credit-ns (peek trace))
              :position-precision-illustrations finite-position}))
