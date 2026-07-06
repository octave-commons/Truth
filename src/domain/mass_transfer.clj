(ns domain.mass-transfer
  "Gradual mass transfer systems: Bondi–Hoyle–Lyttleton sink accretion and
   Roche-lobe overflow. These are pure snapshot-reading, write-set-emitting
   systems that produce c/mass-flux events for the integrator to apply.

   See docs/specs/gradual-mass-transfer-realspec.md.",
  (:require
   [domain.ecs.core       :as ecs]
   [domain.ecs.components  :as c]
   [domain.ecs.parallel    :as par]
   [domain.spatial.index   :as spatial]
   [law.mass-transfer     :as lmt]
   [law.stellar           :as law]
   [shape.spatial         :as sp]))

(def ^:private zero3 [0.0 0.0 0.0])

;; ---------------------------------------------------------------------------
;; Helpers
;; ---------------------------------------------------------------------------

(defn- donor-mass
  "Mass of donor entity `eid` in `world`, or 0.0."
  [world eid]
  (double (or (ecs/get-component world eid c/mass) 0.0)))

(defn- donor-density
  "Best-effort density for a donor parcel (kg/m³)."
  [world eid]
  (double (or (ecs/get-component world eid c/density)
              (let [m (donor-mass world eid)
                    r (double (or (ecs/get-component world eid c/radius) 1.0))]
                (if (pos? r) (/ (* 3.0 m) (* 4.0 Math/PI r r r)) 0.0))
              0.0)))

(defn- donor-smooth-length
  "Smoothing length for a donor parcel. Fallback to radius if absent."
  [world eid]
  (double (or (ecs/get-component world eid c/accretion-radius)
              (ecs/get-component world eid c/radius)
              1.0)))

(defn- sound-speed
  "Isothermal sound speed c_s = √(k_B T / μ m_H) for molecular gas (μ=2.33). m/s."
  [temperature]
  (let [T (double (or temperature 10.0))]
    (if (pos? T)
      (Math/sqrt (/ (* law/k-B T) (* 2.33 law/m-H)))
      0.0)))

(defn- relative-velocity
  "|v_sink - v_donor| (m/s)."
  [world sink-eid donor-eid]
  (let [v-sink  (or (ecs/get-component world sink-eid c/velocity) zero3)
        v-donor (or (ecs/get-component world donor-eid c/velocity) zero3)]
    (sp/len (sp/v- v-sink v-donor))))

(defn- zone-average
  "Mass-weighted average of a scalar quantity over donor eids."
  [donors value-fn]
  (let [{:keys [num den]}
        (reduce (fn [{:keys [num den]} donor]
                  (let [m (:mass donor 0.0)
                        v (value-fn donor)]
                    {:num (+ num (* m v))
                     :den (+ den m)}))
                {:num 0.0 :den 0.0}
                donors)]
    (if (pos? den) (/ num den) 0.0)))

;; ---------------------------------------------------------------------------
;; Accretion radius system
;; ---------------------------------------------------------------------------

(defn accretion-radius-system
  "Compute c/accretion-rate (including capture radius) for every sink.

   Reads: c/mass, c/position, c/velocity, c/temperature (sink), and nearby
   :nebula parcels' c/mass, c/velocity, c/density, c/temperature.
   Writes: c/accretion-rate.

   0-arity returns the ECS system map; 1-arity returns a write-set."
  ([]
   {:id     :mass-transfer-radius
    :ns     'domain.mass-transfer
    :reads  #{c/mass c/position c/velocity c/temperature c/matter-state}
    :writes #{c/accretion-rate}
    :run    accretion-radius-system})
   ([world]
    (let [sinks (ecs/entities-with world c/mass c/position c/velocity c/matter-state)
          dt    (double (or (:genesis/dt world) 1.0))
          gas-pred (fn [item] (= :nebula (:matter-state item)))
          results
          (par/par-mapv
           (fn [sink-eid]
             (let [M       (double (or (ecs/get-component world sink-eid c/mass) 0.0))
                   pos     (or (ecs/get-component world sink-eid c/position) zero3)
                   T-sink  (double (or (ecs/get-component world sink-eid c/temperature) 10.0))
                   c-s     (sound-speed T-sink)
                   r-capt  (lmt/capture-radius M c-s 0.0)
                   zone    (spatial/query-within-radius world pos r-capt gas-pred)
                   rho-inf (zone-average zone #(donor-density world (:id %)))
                   v-rel   (zone-average zone #(relative-velocity world sink-eid (:id %)))
                   r-acc   (lmt/capture-radius M c-s v-rel)
                   dot-m   (lmt/bhl-accretion-rate M rho-inf c-s v-rel)
                   rate    {:sink/r-acc r-acc
                            :sink/r-bondi (lmt/bondi-radius M c-s)
                            :sink/dot-m dot-m
                            :sink/dot-m-this-tick (* dot-m dt)
                            :sink/efficiency 1.0
                          :sink/regime (lmt/accretion-regime c-s v-rel)
                          :sink/ambient-density rho-inf
                          :sink/ambient-cs c-s
                          :sink/relative-velocity v-rel}]
              [sink-eid rate]))
          sinks)]
     {c/accretion-rate (into {} results)})))

;; ---------------------------------------------------------------------------
;; Sink accretion flux system
;; ---------------------------------------------------------------------------

(defn sink-accretion-flux-system
  "Produce c/mass-flux events for gradual sink accretion.

   Reads: c/accretion-radius, c/accretion-rate, c/mass, c/position, c/velocity.
   Writes: c/mass-flux (vector of events on each sink).

   Each event describes a debit from one donor and credit to the sink. The
   integrator applies delta-m to both sink and donor, and delta-p to both.

   0-arity returns the ECS system map; 1-arity returns a write-set."
  ([]
   {:id     :mass-transfer-flux
    :ns     'domain.mass-transfer
    :reads  #{c/mass c/position c/velocity c/accretion-radius c/accretion-rate
              c/matter-state c/temperature}
    :writes #{c/mass-flux}
    :run    sink-accretion-flux-system})
   ([world]
    (let [sinks (ecs/entities-with world c/mass c/position c/velocity c/accretion-rate)
          tick  (long (or (:tick world) 0))
          dt    (double (or (:genesis/dt world) 1.0))
          cap   (double (or (:genesis/accretion-fraction-cap world)
                            lmt/default-accretion-fraction-cap))
          donor-cap (double (or (:genesis/donor-fraction-cap world)
                                lmt/default-donor-fraction-cap))
          gas-pred (fn [item] (= :nebula (:matter-state item)))]
      (reduce
       (fn [ws sink-eid]
         (let [M      (double (or (ecs/get-component world sink-eid c/mass) 0.0))
               pos    (or (ecs/get-component world sink-eid c/position) zero3)
               v-sink (or (ecs/get-component world sink-eid c/velocity) zero3)
               rate   (or (ecs/get-component world sink-eid c/accretion-rate) {})
               r-acc  (double (:sink/r-acc rate 0.0))
               dot-m  (double (:sink/dot-m rate 0.0))
               zone   (remove #(= (:id %) sink-eid)
                              (spatial/query-within-radius world pos r-acc gas-pred))
               zone-mass (reduce + 0.0 (map :mass zone))
               proposed  (lmt/capped-delta-mass
                          {:dot-m dot-m :dt dt
                           :gas-mass zone-mass
                           :donor-mass zone-mass
                           :accretion-fraction-cap cap
                           :donor-fraction-cap donor-cap})]
           (if (or (zero? proposed) (empty? zone))
             ws
             (let [events (loop [remaining proposed
                                 donors    (sort-by :mass > zone)
                                 events    []]
                            (if (or (empty? donors) (zero? remaining))
                              events
                              (let [donor     (first donors)
                                    donor-eid (:id donor)
                                    dm-avail  (* donor-cap (double (:mass donor)))
                                    dm        (min remaining dm-avail)
                                    v-donor   (or (:velocity donor) zero3)
                                    p-donor   (lmt/momentum-of-mass (- dm) v-donor)
                                    p-sink    (lmt/momentum-of-mass dm v-donor)]
                                (recur (- remaining dm)
                                       (rest donors)
                                       (into events
                                             [{:mass-flux/kind :bhl
                                               :mass-flux/sink-id sink-eid
                                               :mass-flux/donor-id donor-eid
                                               :mass-flux/delta-m dm
                                               :mass-flux/delta-p p-sink
                                               :mass-flux/tick tick
                                               :mass-flux/accretion-zone-density
                                               (double (:sink/ambient-density rate 0.0))}
                                              {:mass-flux/kind :bhl
                                               :mass-flux/sink-id sink-eid
                                               :mass-flux/donor-id donor-eid
                                               :mass-flux/delta-m (- dm)
                                               :mass-flux/delta-p p-donor
                                               :mass-flux/tick tick
                                               :mass-flux/accretion-zone-density
                                               (double (:sink/ambient-density rate 0.0))}])))))]
               (assoc ws c/mass-flux {sink-eid events})))))
       {}
       sinks))))
(defn roche-lobe-system
  "Compute c/roche-lobe and c/mass-transfer-rate for binary-pair entities, and
   emit c/mass-flux events for conservative overflow.

   Reads: c/binary-pair, c/mass, c/radius, c/position, c/velocity.
   Writes: c/roche-lobe, c/mass-transfer-rate, c/mass-flux.

   0-arity returns the ECS system map; 1-arity returns a write-set."
  ([]
   {:id     :roche-lobe
    :ns     'domain.mass-transfer
    :reads  #{c/binary-pair c/mass c/radius c/position c/velocity}
    :writes #{c/roche-lobe c/mass-transfer-rate c/mass-flux}
    :run    roche-lobe-system})
  ([world]
   (let [pairs (ecs/entities-with world c/binary-pair)
         tick  (long (or (:tick world) 0))
         dt    (double (or (:genesis/dt world) 1.0))]
     (reduce
      (fn [ws pair-eid]
        (let [pair   (or (ecs/get-component world pair-eid c/binary-pair) {})
              donor  (long (:binary-pair/donor pair))
              accr   (long (:binary-pair/accretor pair))
              a      (double (:orbit/semi-major-axis pair 0.0))
              M-d    (donor-mass world donor)
              M-a    (donor-mass world accr)
              R-d    (double (or (ecs/get-component world donor c/radius) 0.0))
              R-L    (lmt/roche-lobe-radius a M-d M-a)
              delta  (lmt/roche-overfilling R-d R-L)
              overflow? (pos? delta)
              rate   (if overflow?
                       (lmt/ritter-isothermal-rate M-d a R-d R-L)
                       0.0)
              rate-map {:mass-transfer/rate rate
                        :mass-transfer/accreted-fraction lmt/default-accreted-fraction}
              ws'    (-> ws
                         (assoc-in [c/roche-lobe pair-eid]
                                   {:roche-lobe/radius R-L
                                    :roche-lobe/overfilling delta
                                    :roche-lobe/overflow? overflow?})
                         (assoc-in [c/mass-transfer-rate pair-eid] rate-map))
              dm     (max 0.0 (min (* (- rate) dt) (* 0.25 M-d)))]
          (if (zero? dm)
            ws'
            (let [v-d (or (ecs/get-component world donor c/velocity) zero3)
                  v-a (or (ecs/get-component world accr c/velocity) zero3)
                  event-donor {:mass-flux/kind :rlof
                               :mass-flux/binary-pair-id pair-eid
                               :mass-flux/donor-eid donor
                               :mass-flux/accretor-eid accr
                               :mass-flux/delta-m (- dm)
                               :mass-flux/delta-p (lmt/momentum-of-mass (- dm) v-d)
                               :mass-flux/tick tick
                               :mass-flux/roche-overfilling delta
                               :mass-flux/accreted-fraction lmt/default-accreted-fraction}
                  event-accr  {:mass-flux/kind :rlof
                               :mass-flux/binary-pair-id pair-eid
                               :mass-flux/donor-eid donor
                               :mass-flux/accretor-eid accr
                               :mass-flux/delta-m dm
                               :mass-flux/delta-p (lmt/momentum-of-mass dm v-a)
                               :mass-flux/tick tick
                               :mass-flux/roche-overfilling delta
                               :mass-flux/accreted-fraction lmt/default-accreted-fraction}]
              (-> ws'
                  (assoc-in [c/mass-flux pair-eid]
                            [event-donor event-accr]))))))
      {}
      pairs))))

;; ---------------------------------------------------------------------------
;; Public system map
;; ---------------------------------------------------------------------------

(defn mass-transfer-system
  "Return the combined mass-transfer fan-out system map.

   A single registry entry owns c/accretion-rate, c/mass-flux, c/roche-lobe,
   and c/mass-transfer-rate so the single-writer invariant holds. Internally
   it runs the BHL radius/flux pass and the Roche-lobe pass and merges their
   write-sets."
  []
  {:id     :mass-transfer
   :ns     'domain.mass-transfer
   :reads  #{c/mass c/position c/velocity c/temperature c/matter-state
             c/accretion-rate c/binary-pair c/radius}
   :writes #{c/accretion-rate c/mass-flux c/roche-lobe c/mass-transfer-rate}
   :run    (fn [world]
             (merge-with into
                         (accretion-radius-system world)
                         (sink-accretion-flux-system world)
                         (roche-lobe-system world)))})

(defn systems
  "Compatibility alias. Returns a vector containing `mass-transfer-system`."
  []
  [(mass-transfer-system)])
