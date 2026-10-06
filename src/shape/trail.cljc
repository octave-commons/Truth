(ns shape.trail
  "Portable trail geometry and deterministic bounded segment allocation."
  (:require [law.trail :as law]))

(defn segments
  "Project timestamp-faded pairs from history to the actual published head.

   Validates law.trail data; no history mutation or interpolated orbit samples.
   Zero-length pairs are omitted, including an unchanged current head."
  [history head]
  (when-not (and (law/valid-trail? history) (law/valid-sample? head))
    (throw (ex-info "Invalid trail projection input" {:history history :head head})))
  (let [samples (:samples history)
        oldest (:time (first samples))
        span (when oldest (- (:time head) oldest))
        vertex (fn [{:keys [position] sample-time :time}]
                 {:position position
                  :alpha (* law/head-opacity
                            (if (pos? span)
                              (max 0.0 (min 1.0 (/ (- sample-time oldest) span)))
                              1.0))})]
    (if (seq samples)
      (into []
            (comp (remove (fn [[a b]] (= (:position a) (:position b))))
                  (map #(mapv vertex %)))
            (partition 2 1 (conj samples head)))
      [])))

(defn budget-segments
  "Allocate newest complete pairs round-robin, spark first then stable IDs.

   Returns geometry plus exact requested/rendered/dropped counts for the infra
   diagnostic edge. Does not join vertices belonging to different bodies."
  [bodies limit]
  (when-not (and (integer? limit) (not (neg? limit)))
    (throw (ex-info "Trail segment budget must be a nonnegative integer" {:limit limit})))
  (let [ordered (sort-by (juxt #(if (:spark? %) 0 1) :entity) bodies)
        requested (reduce + 0 (map #(count (:segments %)) ordered))
        queues (mapv #(assoc % :segments (rseq (vec (:segments %)))) ordered)
        selected (loop [queues queues result []]
                   (if (or (>= (count result) limit) (empty? queues))
                     result
                     (let [available (filterv #(seq (:segments %)) queues)
                           round (take (- limit (count result)) available)]
                       (if (empty? round)
                         result
                         (recur (mapv #(update % :segments next) available)
                                (into result
                                      (map (fn [{:keys [entity] remaining :segments}]
                                             {:entity entity :vertices (first remaining)}))
                                      round))))))
        rendered (count selected)]
    {:segments selected
     :summary {:requested requested :rendered rendered :dropped (- requested rendered)}}))
