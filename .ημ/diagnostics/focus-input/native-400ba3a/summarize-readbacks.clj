(require '[clojure.edn :as edn] '[clojure.java.io :as io])
(def base ".ημ/diagnostics/focus-input/native-400ba3a/")
(defn readback [file]
  (with-open [r (java.io.PushbackReader. (io/reader (str base file)))]
    (loop [last-map nil]
      (let [x (edn/read {:eof ::end} r)]
        (if (= x ::end) last-map (recur (if (map? x) x last-map)))))))
(defn interval [af bf]
  (let [a (readback af) b (readback bf) am (:monitor a) bm (:monitor b)
        seconds (/ (- (:wall-ms b) (:wall-ms a)) 1000.0)
        frames (- (:frames bm) (:frames am))]
    {:from af :to bf :seconds seconds :ticks [(:tick a) (:tick b)]
     :ticks-per-second (/ (- (:tick b) (:tick a)) seconds)
     :render-calls frames :achieved-frames-per-second (/ frames seconds)
     :mean-original-render-call-wall-ms (/ (- (:total-call-ns bm) (:total-call-ns am)) (* 1e6 frames))}))
(defn len [v] (Math/sqrt (reduce + (map #(* % %) v))))
(let [x (readback "21-natural-eligibility.edn") dt (:sim-dt x) au 1.495978707e11]
  (prn
   {:native-source "400ba3a (launch HEAD22f762f; production src/deps identical)"
    :environment "Xvfb1280x720 llvmpipe, simultaneous simulation+capture+clients+bounded observer; not isolated perf or GPU timing"
    :intervals [(interval "00-before-input.edn" "12-before-visible-motion.edn")
                (interval "17-star-follow.edn" "20-planet-follow.edn")]
    :controls
    (mapv (fn [f] (let [x (readback f)] {:file f :tick (:tick x)
                                       :offset (get-in x [:config :focus-offset])
                                       :intensity (get-in x [:observer :focus-intensity])
                                       :radius (get-in x [:observer :focus-radius])
                                       :D (:effective-displacement x) :retention (:effective-retention x)}))
          ["02-arrow-held.edn" "03-arrow-released.edn" "04-comma.edn" "05-period-held.edn"
           "06-period-released.edn" "07-cruise-default.edn" "08-fine.edn" "09-cruise-restored.edn"])
    :current-eligibility {:tick (:tick x) :dt dt :binding (:binding x)
                          :fresh-handoff (:production-handoff-write-set x)
                          :fresh-eligible-count (count (filter :per-body-eligible? (:bodies x)))}
    :derived-residence
    {:assumptions "Instantaneous constant velocities and fixed dt; one-radius drift over43ticks from gate center. Not an orbit displacement integration or guarantee."
     :capture-accrual-ticks 43 :one-radius-relative-speed-budget-m-per-s (/ au (* 43 dt))
     :stored-candidates
     (mapv (fn [p] (let [parent (:current-parent p) relative (when parent (mapv - (:velocity p) (:velocity parent)))]
                     {:eid (:eid p) :distance-au (:distance-to-spark-au p)
                      :currently-eligible? (:per-body-eligible? p)
                      :absolute-speed-m-per-s (len (:velocity p))
                      :linearized-absolute-step-au (/ (* dt (len (:velocity p))) au)
                      :parent-id (:id parent)
                      :relative-parent-speed-m-per-s (when relative (len relative))
                      :linearized-relative-parent-step-au (when relative (/ (* dt (len relative)) au))}))
           (filterv :stored-candidate (:bodies x)))}}))
