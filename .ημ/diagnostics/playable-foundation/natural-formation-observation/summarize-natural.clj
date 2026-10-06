;; Read diagnostic EDN only. All physical classifications were emitted by
;; production predicates in observe-natural.clj; this summary never reclassifies.
(require '[clojure.edn :as edn]
         '[clojure.pprint :as pp]
         '[clojure.string :as str])

(let [path (first *command-line-args*)
      text (slurp path)
      lines (str/split text #"\n" -1)
      ;; The writer flushes complete newline-terminated records. A concurrent
      ;; read can contain an unfinished final record; retain only complete ones.
      records (mapv edn/read-string (butlast lines))
      start (first records)
      last-observation (last records)
      current-bodies (into {} (map (juxt :eid identity) (:bodies last-observation)))
      formation-events
      (vec (for [observation records
                 event (:new-ledger-events observation)
                 :when (= :event/planet-formation (:kind event))
                 :let [eid (get-in event [:payload :data :eid])
                       birth (first (filter #(= eid (:eid %)) (:bodies observation)))
                       current (get current-bodies eid)
                       sampled-bodies (keep (fn [record]
                                              (when (and (:tick record)
                                                         (>= (:tick record) (:tick event)))
                                                (first (filter #(= eid (:eid %))
                                                               (:bodies record)))))
                                            records)]]
             {:eid eid :event-id (:id event) :birth-tick (:tick event)
              :birth-observation-tick (:tick observation)
              :birth-body-observed? (some? birth)
              :birth-state (:matter-state birth)
              :birth-parent-id (get-in birth [:parent :id])
              :birth-bound-radius-au (:bound-parent-radius-au birth)
              :birth-eligible? (:eligible-candidate? birth)
              :current-observation-tick (:tick last-observation)
              :ticks-since-birth (- (:tick last-observation) (:tick event))
              :current-state (:matter-state current)
              :current-alive? (:alive? current)
              :current-parent-id (get-in current [:parent :id])
              :current-bound-radius-au (:bound-parent-radius-au current)
              :current-eligible? (:eligible-candidate? current)
              :current-stored-candidate? (some? (:stored-candidate current))
              :recorded-body-sample-count (count sampled-bodies)
              :bound-at-every-recorded-sample? (every? :parent sampled-bodies)}))
      radii (keep :birth-bound-radius-au formation-events)]
  (pp/pprint
   {:kind :derived-natural-observation-summary
    :input path :complete-record-count (count records)
    :ignored-incomplete-final-record? (not (str/ends-with? text "\n"))
    :run start
    :latest (select-keys last-observation [:kind :tick :elapsed-ms :sim-time :sim-dt
                                           :arc :states :stored-candidate-count
                                           :planet-count :stop-reason :ledger-kinds])
    :formation-event-count (count formation-events)
    :births-with-bound-parent (count radii)
    :birth-bound-radius-range-au (when (seq radii) [(apply min radii) (apply max radii)])
    :formed-bodies-currently-planets
    (count (filter #(= :planet (:current-state %)) formation-events))
    :formed-bodies-currently-bound (count (filter :current-parent-id formation-events))
    :formed-bodies-currently-eligible (count (filter :current-eligible? formation-events))
    :formed-bodies-currently-stored-candidates
    (count (filter :current-stored-candidate? formation-events))
    :formed-bodies-bound-at-every-recorded-sample
    (count (filter :bound-at-every-recorded-sample? formation-events))
    :formation-bodies formation-events
    :limitation "Birth samples are exact event ticks; later rows are sampled current state. Continued presence/binding between samples is not implied. Transient gas giants without planet-formation events are excluded."}))
