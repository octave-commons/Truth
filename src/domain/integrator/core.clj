(ns domain.integrator.core
  "Main integrator body: mass, composition, ionization, rotation, and
   condensed composition write-sets."
  (:require
   [domain.ecs.core :as ecs]
   [domain.ecs.components :as c]
   [domain.ecs.parallel :as par]
   [domain.chemistry :as chemistry]
   [domain.stellar.thermodynamics :as thermo]
   [domain.integrator.base :as base]
   [domain.integrator.temperature :as itemp]
   [law.stellar :as law]
   [shape.spatial :as sp]))

(defn- depleted-donors
  "Return {eid true} for gradual-transfer donors whose new mass would fall below
   `floor-mass`. Scoped to the c/mass-flux-transfer channel (a donor is a body
   that lost mass to accretion/overflow this tick) so ordinary mass loss (wind,
   flare, XUV) never triggers reaping. Reaped via c/consumed-transfer."
  [world floor-mass]
  (let [floor (double (or floor-mass 0.0))]
    (reduce-kv (fn [acc eid dm]
                 (let [dm (double dm)
                       m0 (double (or (ecs/get-component world eid c/mass) 0.0))]
                   (if (and (neg? dm) (pos? m0) (< (+ m0 dm) floor))
                     (assoc acc eid true)
                     acc)))
               {}
               (get-in world [:components c/mass-flux-transfer] {}))))

(defn- absorb-mass-delta
  "Sum of absorbed bulk mass for `eid` from absorb-accrete and absorb-merge
   packets. Skips disk-route packets (disk-evolution handles those)."
  [world eid]
  (let [acc (fn [acc eid' packets]
              (if (= eid' eid)
                (reduce + acc (map :mass (remove :disk-route packets)))
                acc))]
    (+ (reduce-kv acc 0.0 (get-in world [:components c/absorb-accrete] {}))
       (reduce-kv acc 0.0 (get-in world [:components c/absorb-merge] {})))))

(defn- absorb-angmom-sum
  "Sum of absorbed angular momentum for `eid` from absorb packets."
  [world eid]
  (let [acc (fn [acc eid' packets]
              (if (= eid' eid)
                (reduce sp/v+ acc (map :angular-momentum packets))
                acc))]
    (reduce-kv acc base/zero3
               (merge (get-in world [:components c/absorb-accrete] {})
                      (get-in world [:components c/absorb-merge] {})))))

(defn- absorb-comp-blend
  "Mass-weighted composition blend for `eid` from absorb-merge packets.
   Returns the blended composition map, or nil if no merge packets target this
   entity."
  [world eid]
  (let [pkts (filter :composition (base/absorb-packets-for world eid))]
    (when (seq pkts)
      (let [m0  (double (or (ecs/get-component world eid c/mass) 0.0))
            c0  (or (ecs/get-component world eid c/composition) {})
            {:keys [comp-acc total-m]}
            (reduce (fn [acc p]
                      (let [m (double (:mass p 0.0))
                            c (or (:composition p) {})]
                        (-> acc
                            (update :total-m + m)
                            (update :comp-acc
                                    (fn [ca]
                                      (reduce-kv
                                       (fn [result k v]
                                         (assoc result k (+ (get result k 0.0) (* v m))))
                                       (or ca {})
                                       c))))))
                    {:comp-acc (reduce-kv (fn [m k v] (assoc m k (* v m0))) {} c0)
                     :total-m m0}
                    pkts)]
        (when (pos? total-m)
          (let [inv (/ 1.0 total-m)]
            (reduce-kv (fn [m k v] (assoc m k (* v inv))) {} comp-acc)))))))

(defn- merge-volatile-loss
  "Volatile blow-off plan for merge survivor `eid` (chemistry spec §7 Phase 4):
   `{:composition :lost-fraction}` from the mass-weighted blend and the
   post-impact temperature (`domain.integrator.temperature/merged-temperature`
   — the same temperature the integrator writes, so the gate and the heating
   can never disagree). Above the law.chemistry thresholds the ice-volatile
   and/or H/He inventory is stripped and the composition renormalized; below
   them the blend passes through with `:lost-fraction` 0.0. nil when no merge
   packets target `eid`."
  [world eid]
  (when-let [blended (absorb-comp-blend world eid)]
    (chemistry/strip-volatiles
     blended
     (double (or (itemp/merged-temperature world eid)
                 (ecs/get-component world eid c/temperature)
                 0.0)))))

(defn- volatile-loss-fraction
  "Fraction of the merged body's total mass driven off as volatiles by a hot
   merge this tick; 0.0 for gentle/cold merges and non-merge entities."
  [world eid]
  (double (or (:lost-fraction (merge-volatile-loss world eid)) 0.0)))

(defn- ablated-bodies
  "Return {eid true} for bound bodies whose new mass would fall at or below
   `law/ablation-floor`. Bound states are every resolved matter-state except
   :nebula. A bound core never re-dissolves to gas; at total ablation it is
   despawned and its conserved mass lives in shed parcels / the companion."
  [world floor cell]
  (let [floor (double (or floor 0.0))]
    (reduce-kv (fn [acc eid m1]
                 (let [st (ecs/get-component world eid c/matter-state)]
                   (if (and (not= :nebula st)
                            (some? st)
                            (<= (double m1) floor))
                     (assoc acc eid true)
                     acc)))
               {}
               cell)))

(defn mass-ws
  "Mass. m' = max(0, m + Σ mass-flux.* + Σ absorb-mass) — the per-source mass
   fluxes (stellar wind/flare loss, XUV escape, disk→star viscous transfer) and
   the accretion/merge mass from absorb-accrete/merge packets are summed and
   applied. Only bodies with a flux or absorb packet this tick are rewritten.
   When `:lod/throttle-ticks?` is true, only due entities are advanced."
  [world]
  (let [eids      (base/due-entities world (ecs/entities-with world c/mass))
        absorbs   (merge (get-in world [:components c/absorb-accrete] {})
                         (get-in world [:components c/absorb-merge] {}))
        new-mass  (fn [eid]
                    (let [m0        (double (ecs/get-component world eid c/mass))
                          dm        (base/sum-scalar-influences world eid base/mass-flux-sources)
                          dm-a      (absorb-mass-delta world eid)
                          dm-wind   (double (or (:wind-heating/mass-loss
                                                 (ecs/get-component world eid c/wind-heating))
                                                0.0))
                          dm-t      (+ dm dm-a (- dm-wind))
                          ;; hot merges drive off volatiles: the escaped mass
                          ;; leaves with the stripped composition (core-ws).
                          lost      (* (volatile-loss-fraction world eid) (+ m0 dm-a))]
                      (max 0.0 (- (+ m0 dm-t) lost))))
        cell      (into {}
                        (keep (fn [eid]
                                (let [dm        (base/sum-scalar-influences world eid base/mass-flux-sources)
                                      dm-a      (absorb-mass-delta world eid)
                                      dm-wind   (double (or (:wind-heating/mass-loss
                                                             (ecs/get-component world eid c/wind-heating))
                                                            0.0))
                                      dm-t      (+ dm dm-a (- dm-wind))]
                                  (when-not (zero? dm-t)
                                    [eid (new-mass eid)]))))
                        eids)
        ;; Absorb packets may target entities that lacked a scalar mass-flux
        ;; influence but still gained mass. Ensure they are included.
        extra     (into {}
                        (keep (fn [eid]
                                (when-not (contains? cell eid)
                                  (let [dm-a (absorb-mass-delta world eid)]
                                    (when-not (zero? dm-a)
                                      [eid (new-mass eid)])))))
                        (keys absorbs))
        depleted  (depleted-donors world 0.0)
        ablated   (ablated-bodies world law/ablation-floor (merge cell extra))
        mass-write   (if (empty? (merge cell extra)) {} {c/mass (merge cell extra)})]
    (cond-> mass-write
      (seq depleted) (assoc c/consumed-transfer depleted)
      (seq ablated)  (assoc c/consumed-ablation ablated))))

(defn ionization-ws
  "Ionization-fraction. The integrator applies wind-heating ionization increments
   on top of the snapshot value, clamped to [0,1]. Only due entities are updated
   when `:lod/throttle-ticks?` is true."
  [world]
  (let [heats (get-in world [:components c/wind-heating] {})
        due? (set (base/due-entities world (keys heats)))
        updates (into {}
                      (keep (fn [[eid wh]]
                              (when (due? eid)
                                (when-let [d (:wind-heating/ionization-rate wh)]
                                  (let [i0 (double (or (ecs/get-component world eid c/ionization-fraction) 0.0))
                                        i1 (max 0.0 (min 1.0 (+ i0 (double d))))]
                                    (when (not= i0 i1)
                                      [eid i1]))))))
                      heats)]
    (when (seq updates)
      {c/ionization-fraction updates})))

(defn composition-ws
  "Composition. The integrator owns the blend: start from the snapshot
   composition, apply the H→He burn (comp.burn replaces it for burning cores),
   then the deuterium gate (comp.depletion zeroes :D for hot bodies). Only due
   entities are updated when `:lod/throttle-ticks?` is true.

   Absorb-merge packets from collision merges are blended BEFORE burn/depletion:
   the mass-weighted composition of the survivor and the absorbed body, with
   volatiles driven off first when the post-impact temperature crosses the
   law.chemistry blow-off thresholds (the escaped mass is debited in mass-ws)."
  [world]
  (let [burns (get-in world [:components c/comp-burn] {})
        deps  (get-in world [:components c/comp-depletion] {})
        merge-eids (set (keys (get-in world [:components c/absorb-merge] {})))
        ;; blend compositions from merge packets first, then strip volatiles
        ;; driven off by the impact heating (hot merges only; cold merges blend)
        merged-comps (when (seq merge-eids)
                       (persistent!
                        (reduce (fn [acc eid]
                                  (if-let [cmp (:composition (merge-volatile-loss world eid))]
                                    (assoc! acc eid cmp)
                                    acc))
                                (transient {}) merge-eids)))
        ;; then apply burn/depletion on top
        all-eids (into (into (set (keys burns)) (keys deps)) merge-eids)
        due (set (base/due-entities world all-eids))]
    (if (empty? all-eids)
      {}
      {c/composition
       (into {}
             (keep (fn [eid]
                     (when (due eid)
                       (when-let [base (or (get merged-comps eid)
                                           (get burns eid)
                                           (ecs/get-component world eid c/composition))]
                         [eid (reduce (fn [c k] (assoc c k 0.0))
                                      base
                                      (get deps eid #{}))]))))
             all-eids)})))

(defn comp-condensed-ws
  "Derived partition of each body's composition into solid and gas phases at
   its current temperature. Writes :component/comp.condensed for every due
   entity that has both composition and temperature."
  [world]
  (let [eids (base/due-entities world (ecs/entities-with world c/composition c/temperature))]
    (if (seq eids)
      {c/comp-condensed
       (into {}
             (map (fn [eid]
                    (let [comp-ws (ecs/get-component world eid c/composition)
                          temp (double (or (ecs/get-component world eid c/temperature) 0.0))]
                      [eid (chemistry/partition-solids comp-ws temp)])))
             eids)}
      {})))

(defn rotation-ws
  "Angular momentum + spin. L' = L + Σ torque.* + Σ absorb-L (the torque
   influences are per-step ΔL — magnetic braking, disk spin-up; the absorb
   packets carry the absorbed parcels' angular momentum). Spin is derived
   ω = L'/I. Only due entities are advanced when `:lod/throttle-ticks?` is true."
  [world]
  (let [eids    (base/due-entities world (ecs/entities-with world c/angular-momentum c/mass c/radius))
        pairs   (par/par-mapv
                 (fn [eid]
                   (let [L   (or (ecs/get-component world eid c/angular-momentum) base/zero3)
                         dL  (base/sum-vec-influences world eid base/torque-sources)
                         dLa (absorb-angmom-sum world eid)
                         L'  (sp/v+ (sp/v+ L dL) dLa)
                         m   (ecs/get-component world eid c/mass)
                         r   (ecs/get-component world eid c/radius)
                         spin' (thermo/spin-from-angular-momentum L' m r)]
                     [eid L' spin']))
                 eids)]
    (reduce (fn [ws [eid L' spin']]
              (-> ws
                  (assoc-in [c/angular-momentum eid] L')
                  (assoc-in [c/spin eid] spin')))
            {}
            pairs)))
