(ns domain.mass-transfer
  "Gradual mass transfer systems: Bondi–Hoyle–Lyttleton sink accretion and
   Roche-lobe overflow. These are pure snapshot-reading, write-set-emitting
   systems that produce c/mass-flux events for the integrator to apply.

   See docs/specs/gradual-mass-transfer-realspec.md.",
  (:require
   [domain.ecs.core       :as ecs]
   [domain.ecs.components  :as c]
   [domain.ecs.parallel    :as par]
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
  "Compute c/accretion-radius and c/accretion-rate for every sink.

   Reads: c/mass, c/position, c/velocity, c/temperature (sink), and nearby
   :nebula parcels' c/mass, c/velocity, c/density, c/temperature.
   Writes: c/accretion-radius, c/accretion-rate.

   0-arity returns the ECS system map; 1-arity returns a write-set."
  ([]
   {:id     :mass-transfer-radius
    :ns     'domain.mass-transfer
    :reads  #{c/mass c/position c/velocity c/temperature c/matter-state}
    :writes #{c/accretion-radius c/accretion-rate}
    :run    accretion-radius-system})
  ([world]
   (let [sinks (ecs/entities-with world c/mass c/position c/velocity c/matter-state)
         gas   (ecs/entities-with world c/mass c/position c/velocity c/matter-state c/temperature)
         dt    (double (or (:genesis/dt world) 1.0))
         results
         (par/par-mapv
          (fn [sink-eid]
            (let [M       (double (or (ecs/get-component world sink-eid c/mass) 0.0))
                  pos     (or (ecs/get-component world sink-eid c/position) zero3)
                  T-sink  (double (or (ecs/get-component world sink-eid c/temperature) 10.0))
                  c-s     (sound-speed T-sink)
                  ;; Collect nearby gas parcels (naive O(N·M); spatial index later)
                  nearby  (filterv
                           (fn [donor-eid]
                             (and (not= donor-eid sink-eid)
                                  (= :nebula (ecs/get-component world donor-eid c/matter-state))
                                  (let [d-pos (or (ecs/get-component world donor-eid c/position) zero3)
                                        r     (sp/len (sp/v- d-pos pos))
                                        r-b   (lmt/bondi-radius M c-s)
                                        h     (donor-smooth-length world donor-eid)
                                        r-acc (max r-b (* lmt/default-softening-factor h))]
                                    (< r r-acc))))
                           gas)
                  rho-inf (zone-average nearby #(donor-density world (:eid %)))
                  v-rel   (zone-average nearby #(relative-velocity world sink-eid (:eid %)))
                  r-acc   (lmt/capture-radius M c-s v-rel)
                  dot-m   (lmt/bhl-accretion-rate M rho-inf c-s v-rel)
                  rate    {:sink/dot-m dot-m
                           :sink/dot-m-this-tick (* dot-m dt)
                           :sink/efficiency 1.0
                           :sink/regime (lmt/accretion-regime c-s v-rel)}
                  radius  {:sink/r-acc r-acc
                           :sink/r-bondi (lmt/bondi-radius M c-s)
                           :sink/ambient-density rho-inf
                           :sink/ambient-cs c-s
                           :sink/relative-velocity v-rel}]
              [sink-eid radius rate]))
          sinks)]
     (reduce (fn [ws [eid radius rate]]
               (-> ws
                   (assoc-in [c/accretion-radius eid] radius)
                   (assoc-in [c/accretion-rate eid] rate)))
             {}
             results))))

;; ---------------------------------------------------------------------------
;; Sink accretion flux system
;; ---------------------------------------------------------------------------

(defn sink-accretion-flux-system
  "placeholder."
  ([_] {}))
(defn roche-lobe-system
  "Compute c/roche-lobe and c/mass-transfer-rate for binary-pair entities, and
   emit c/mass-flux events for conservative overflow.

   Reads: c/binary-pair, c/mass, c/radius, c/position, c/velocity.
   Writes: c/roche-lobe, c/mass-transfer-rate, c/mass-flux."
  [world]
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
                 ;; Conservative: mass lost by donor, gained by accretor.
                 ;; Momentum debit from donor, credit to accretor.
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
     pairs)))

;; ---------------------------------------------------------------------------
;; Public system maps
;; ---------------------------------------------------------------------------

(defn systems
  "Return the mass-transfer fan-out systems as ECS registry maps.
   Each map has an extra :run key so it can be passed directly to
   `domain.ecs.tick/run-parallel`."
  []
  [{:id     :mass-transfer-radius
    :ns     'domain.mass-transfer
    :reads  #{c/mass c/position c/velocity c/temperature c/matter-state}
    :writes #{c/accretion-radius c/accretion-rate}
    :run    accretion-radius-system}
   {:id     :mass-transfer-flux
    :ns     'domain.mass-transfer
    :reads  #{c/mass c/position c/velocity c/accretion-radius c/accretion-rate
              c/matter-state c/temperature}
    :writes #{c/mass-flux}
    :run    sink-accretion-flux-system}
   {:id     :roche-lobe
    :ns     'domain.mass-transfer
    :reads  #{c/binary-pair c/mass c/radius c/position c/velocity}
    :writes #{c/roche-lobe c/mass-transfer-rate c/mass-flux}
    :run    roche-lobe-system}])
