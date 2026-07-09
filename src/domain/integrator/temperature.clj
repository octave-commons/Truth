(ns domain.integrator.temperature
  "Temperature write-sets for the unified integrator."
  (:require
   [domain.ecs.core :as ecs]
   [domain.ecs.components :as c]
   [domain.intervention :as intervention]
   [domain.stellar.temperature :as temperature]
   [domain.integrator.base :as base]
   [law.stellar :as law]
   [shape.spatial :as sp]))

(defn- absorb-temp-delta
  "Temperature change for `eid` from absorb-merge packets: mass-weighted
   temperature blend plus impact heating (kinetic energy → thermal). Returns the
   final blended temperature, or nil if no merge packets target this entity.
   The impact heating formula is ΔT = E_lost · m_H / (M_total · 2.5 · k_B),
   matching the serial merge handler (stellar.clj:1837)."
  [world eid]
  (let [pkts (filter :temperature (base/absorb-packets-for world eid))]
    (when (seq pkts)
      (let [m0  (double (or (ecs/get-component world eid c/mass) 0.0))
            t0  (double (or (ecs/get-component world eid c/temperature) 0.0))
            v0  (or (ecs/get-component world eid c/velocity) base/zero3)
            ;; mass-weighted temperature blend
            {:keys [t-blend total-m]}
            (reduce (fn [acc p]
                      (let [m (double (:mass p 0.0))
                            t (double (:temperature p 0.0))]
                        (-> acc
                            (update :t-blend + (* t m))
                            (update :total-m + m))))
                    {:t-blend (* t0 m0) :total-m m0}
                    pkts)
            base-t (if (pos? total-m) (/ t-blend total-m) t0)
            ;; impact heating: kinetic energy lost in inelastic collision
            dv-sum (reduce (fn [dv p]
                             (let [vs (or (:velocity p) base/zero3)]
                               (sp/v+ dv (sp/v- v0 vs))))
                           base/zero3 pkts)
            ms-sum (reduce + (map :mass pkts))
            e-lost (* 0.5 (/ (* m0 ms-sum) (+ m0 ms-sum))
                      (sp/dot dv-sum dv-sum))
            impact-dt (/ (* e-lost law/m-H) (* (+ m0 ms-sum) 2.5 law/k-B))]
        (+ base-t impact-dt)))))

(defn- apply-wind-heating
  "Add wind-heating temperature deltas on top of the base temperature cell."
  [base-cell world]
  (merge base-cell
         (into {}
               (keep (fn [[eid wh]]
                       (when-let [d (:wind-heating/delta-t wh)]
                         (let [t0 (double (or (get base-cell eid)
                                              (ecs/get-component world eid c/temperature)
                                              3.0))]
                           [eid (+ t0 (double d))]))))
               (get-in world [:components c/wind-heating] {}))))

(defn- absorb-merge-temperatures
  "Compute {eid temperature} for entities that absorbed merge packets."
  [world]
  (let [merge-eids (set (keys (get-in world [:components c/absorb-merge] {})))]
    (when (seq merge-eids)
      (persistent!
       (reduce (fn [acc eid]
                 (if-let [t (absorb-temp-delta world eid)]
                   (assoc! acc eid t)
                   acc))
               (transient {}) merge-eids)))))

(defn- ease-heat-interventions
  "Apply every heat-intervention contribution to a temperature cell."
  [cell world t0-fn]
  (reduce-kv
   (fn [cell eid cs]
     (let [t0 (double (t0-fn eid))]
       (assoc cell eid (intervention/apply-thermal-contributions t0 cs))))
   cell
   (get-in world [:components c/heat-intervention] {})))

(defn temperature-ws
  "Temperature. The base value is the virial/radiative derivation owned by
    `domain.stellar.temperature/temperature-system` (cores heat by Kelvin–Helmholtz contraction,
   worlds reach radiative equilibrium, diffuse gas is left at its background);
   the integrator then applies the player's heat.intervention ease on top — so
   a heat source/sink eases the freshly-derived temperature (as the old serial
   `apply-thermal-interventions` eased the post-fold value). Reusing the tested
   derivation keeps the formula unchanged (§9 non-goal).

   Absorb-merge packets from collision merges are blended AFTER the virial
   derivation: the mass-weighted temperature blend plus impact heating. This is
   a one-tick Jacobi delay — the merged body's radius (used by virial) won't
   update until structure re-derives it next tick.

   When `:lod/throttle-ticks?` is true, only due entities are kept in the final
   temperature cell."
  [world dt]
  (let [base ((:run (temperature/temperature-system dt)) world)
        base-cell (apply-wind-heating (get base c/temperature {}) world)
        merged-temps (absorb-merge-temperatures world)
        heats (get-in world [:components c/heat-intervention] {})
        cell (cond
               ;; both heat interventions and merge blends
               (and (seq heats) (seq merged-temps))
               (ease-heat-interventions (merge base-cell merged-temps) world
                                        #(or (get merged-temps %)
                                             (get base-cell %)
                                             (ecs/get-component world % c/temperature)
                                             intervention/min-temp))
               ;; only heat interventions
               (seq heats)
               (ease-heat-interventions base-cell world
                                        #(or (get base-cell %)
                                             (ecs/get-component world % c/temperature)
                                             intervention/min-temp))
               ;; only merge blends
               (seq merged-temps)
               (merge base-cell merged-temps)
               :else base-cell)
        due (set (base/due-entities world (keys cell)))
        cell' (into {} (filter (fn [[eid _]] (due eid)) cell))]
    (if (or (seq heats) (seq merged-temps))
      {c/temperature cell'}
      (assoc base c/temperature cell'))))
