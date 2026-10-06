(ns domain.trail
  "Portable simulation-time observations; never predicts an unobserved path."
  (:require [law.trail :as law]))

(defn eligible?
  "True for positioned sparks, stars, and explicitly resolved planet bodies."
  [{:keys [position body-kind matter-state]}]
  (and (some? position)
       (or (= :spark body-kind)
           (contains? #{:star :planet :gas-giant} matter-state)
           (and (= :body/planet body-kind) (not= :nebula matter-state)))))

(defn- shift-sample [offset sample]
  (update sample :position #(mapv - % offset)))

(defn advance-history
  "Rebase each fold and append at most one observed point at a due deadline.

   Validates law.trail boundaries. Missed deadlines advance arithmetically;
   published time expires the tail even when a step exceeds the whole horizon."
  [history {:keys [dt position frame-offset] sample-time :time :as observation}
   {:keys [capacity cadence horizon] :as options}]
  (when-not (and (or (nil? history) (law/valid-trail? history))
                 (law/valid-observation? observation)
                 (law/valid-options? options))
    (throw (ex-info "Invalid motion-trail input"
                    {:history history :observation observation :options options})))
  (let [next-at (or (:next-at history) sample-time)
        due? (>= sample-time next-at)
        slots (if due? (inc (long (quot (- sample-time next-at) cadence))) 0)
        deadline (if due? (+ next-at (* slots cadence)) next-at)
        cutoff (- (+ sample-time dt) horizon)
        shifted (mapv #(shift-sample frame-offset %) (:samples history))
        observed (cond-> shifted
                   due? (conj (shift-sample frame-offset {:time sample-time :position position})))
        result {:samples (->> observed
                              (filter #(>= (:time %) cutoff))
                              (take-last capacity)
                              vec)
                :next-at deadline
                :skipped-deadlines (+ (:skipped-deadlines history 0)
                                      (max 0 (dec slots)))}]
    (when-not (and (law/finite-number? cutoff)
                   (> deadline sample-time)
                   (law/valid-trail? result))
      (throw (ex-info "Motion-trail clock or frame exceeded numerical range"
                      {:observation observation :result result})))
    result))
