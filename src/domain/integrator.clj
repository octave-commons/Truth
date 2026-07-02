(ns domain.integrator
  "The single integrator — sole writer of physical state.

   Spec: docs/notes/specs/2026.06.29-unified-physical-state-integrator-spec.md.

   §0 (the one sentence): there is ONE writer of physical state — this integrator
   — which reads, each tick, the previous snapshot plus a set of INFLUENCE
   components registered against the physical fields they affect; every other
   system is a pure, snapshot-reading, single-component-writing emitter, and
   nothing runs serially \"after the fold.\"

   Per §8 the integrator is structured as a set of per-field updaters that share
   one owner (one registry entry, one `:writes` set). It owns the dynamical and
   contended physical fields:

     position, velocity   ← Σ accel.* · dt   + frame-offset + accretion momentum
     mass                 ← Σ mass-flux.*    + absorbed mass
     angular-momentum     ← L + Σ torque.*   + absorbed L ;  spin ← L/I (derived)
     temperature          ← virial / radiative + heat.intervention + impact heat
     composition          ← comp.burn then comp.depletion + absorbed blend

   The pure stateless derivations that already have a clean single owner stay as
   their own fan-out systems (structure→radius/density, eos→pressure,
   field→b-field, fusion→luminosity) — §8 Q2: a separate system IS the single
   owner of its component, so single-writer holds either way; keeping them
   isolated keeps each field's logic small and avoids touching tuned formulas
   (§9 non-goal). This namespace owns only the fields that were contended or
   accumulated across multiple writers."
  (:require
   [domain.ecs.core       :as ecs]
   [domain.ecs.components  :as c]
   [domain.ecs.parallel    :as par]
   [domain.profile         :as profile]
   [domain.stellar        :as stellar]
   [domain.intervention   :as intervention]
   [law.stellar           :as law]
   [shape.spatial         :as sp]))

(def ^:private zero3 [0.0 0.0 0.0])

;; ---------------------------------------------------------------------------
;; The influence registry (§4)
;; ---------------------------------------------------------------------------
;; Declarative map: each additive physical field → the influence components that
;; contribute to it (summed, then scaled). The integrator reads THIS, not
;; hardcoded knowledge — adding a force/heat/torque/mass source is one line here
;; plus its single-writer emitter. Non-additive fields (temperature, composition,
;; spin) are derived by the per-field updaters below and documented in :derived.
(def influence-registry
  {:velocity         {:accumulate [c/accel-gravity c/accel-pressure c/accel-lorentz
                                   c/accel-observer c/accel-warp]
                      :compose :sum :scale :dt}
   :angular-momentum {:accumulate [c/torque-em c/torque-disk]
                      :compose :sum :scale :dt}
   :mass             {:accumulate [c/mass-flux-wind c/mass-flux-flare
                                   c/mass-flux-xuv c/mass-flux-disk]
                      :compose :sum :scale :raw}
   :velocity-delta   {:accumulate [c/dv-wind c/dv-flare]
                      :compose :sum :scale :raw}
   :temperature      {:influences [c/heat-intervention]
                      :derived "virial (cores) / radiative (worlds) + intervention ease"}
   :composition      {:influences [c/comp-burn c/comp-depletion]
                      :derived "comp.burn replaces, comp.depletion zeroes"}
   :position         {:influences [c/frame-offset]
                      :derived "x + v·dt − frame-offset (COM Galilean shift)"}
   :spin             {:derived "L / I (moment of inertia)"}})

(defn- sum-vec-influences
  "Σ of the vector influence components `ctypes` on `eid` (missing ⇒ zero)."
  [world eid ctypes]
  (reduce (fn [acc ct] (sp/v+ acc (or (ecs/get-component world eid ct) zero3)))
          zero3 ctypes))

(defn- sum-scalar-influences
  "Σ of the scalar influence components `ctypes` on `eid` (missing ⇒ 0)."
  [world eid ctypes]
  (reduce (fn [acc ct] (+ acc (double (or (ecs/get-component world eid ct) 0.0))))
          0.0 ctypes))

;; ---------------------------------------------------------------------------
;; The integrator system
;; ---------------------------------------------------------------------------

(def ^:private accel-sources
  (get-in influence-registry [:velocity :accumulate]))

(def ^:private dv-sources
  (get-in influence-registry [:velocity-delta :accumulate]))

(def ^:private mass-flux-sources
  (get-in influence-registry [:mass :accumulate]))

;; --- Absorb-accrete processing (sink-formation / collision emissions) -------
;; c/absorb-accrete is {eid [{:mass :velocity :position :angular-momentum ...}]}
;; — the absorbed parcels' state. The integrator blends it into the survivor's
;; fields (spec §5). c/absorb-merge is the same shape for collision merges.
;; Each per-field updater below reads the same data and applies its field.

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

(defn- absorb-vec-composite
  "Mass-weighted composite of velocity (or position) for `eid` from absorb
   packets. Returns `[composite-vec total-mass]` for the absorbed material,
   or [zero3 0] if no packets target this entity."
  [world eid key]
  (reduce-kv
   (fn [[v-sum m-sum] eid' packets]
     (if (= eid' eid)
       (reduce (fn [[v-sum m-sum] p]
                 (let [m (double (:mass p 0.0))
                       v (get p key [0 0 0])]
                   [(sp/v+ v-sum (sp/v* v m)) (+ m-sum m)]))
               [v-sum m-sum] packets)
       [v-sum m-sum]))
   [zero3 0.0]
   (merge (get-in world [:components c/absorb-accrete] {})
          (get-in world [:components c/absorb-merge] {}))))

(defn- absorb-angmom-sum
  "Sum of absorbed angular momentum for `eid` from absorb packets."
  [world eid]
  (let [acc (fn [acc eid' packets]
              (if (= eid' eid)
                (reduce sp/v+ acc (map :angular-momentum packets))
                acc))]
    (reduce-kv acc zero3
               (merge (get-in world [:components c/absorb-accrete] {})
                      (get-in world [:components c/absorb-merge] {})))))

(defn- absorb-packets-for
  "All absorb-accrete and absorb-merge packets targeting `eid`."
  [world eid]
  (let [matches (fn [m] (get m eid))]
    (into (or (matches (get-in world [:components c/absorb-accrete] {})) [])
          (or (matches (get-in world [:components c/absorb-merge] {})) []))))

(defn- absorb-temp-delta
  "Temperature change for `eid` from absorb-merge packets: mass-weighted
   temperature blend plus impact heating (kinetic energy → thermal). Returns the
   final blended temperature, or nil if no merge packets target this entity.
   The impact heating formula is ΔT = E_lost · m_H / (M_total · 2.5 · k_B),
   matching the serial merge handler (stellar.clj:1837)."
  [world eid]
  (let [pkts (filter :temperature (absorb-packets-for world eid))]
    (when (seq pkts)
      (let [m0  (double (or (ecs/get-component world eid c/mass) 0.0))
            t0  (double (or (ecs/get-component world eid c/temperature) 0.0))
            v0  (or (ecs/get-component world eid c/velocity) zero3)
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
                             (let [vs (or (:velocity p) zero3)]
                               (sp/v+ dv (sp/v- v0 vs))))
                           zero3 pkts)
            ms-sum (reduce + (map :mass pkts))
            e-lost (* 0.5 (/ (* m0 ms-sum) (+ m0 ms-sum))
                      (sp/dot dv-sum dv-sum))
            impact-dt (/ (* e-lost law/m-H) (* (+ m0 ms-sum) 2.5 law/k-B))]
        (+ base-t impact-dt)))))

(defn- absorb-comp-blend
  "Mass-weighted composition blend for `eid` from absorb-merge packets.
   Returns the blended composition map, or nil if no merge packets target this
   entity."
  [world eid]
  (let [pkts (filter :composition (absorb-packets-for world eid))]
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

;; --- Per-field updaters (each returns a write-set fragment) ------------------

(defn- com-blend
  "Mass-weighted centroid of survivor `[v0 x0]` with mass `m0` and absorbed
   packets `pkts`. Returns `[v-blend x-blend total-mass]`."
  [v0 x0 m0 pkts]
  (let [v0m (sp/v* v0 m0)
        x0m (sp/v* x0 m0)
        {:keys [vn xn total-m]}
        (reduce (fn [acc p]
                  (let [m (double (:mass p 0.0))
                        v (or (:velocity p) zero3)
                        x (or (:position p) zero3)]
                    (-> acc
                        (update :vn sp/v+ (sp/v* v m))
                        (update :xn sp/v+ (sp/v* x m))
                        (update :total-m + m))))
                {:vn v0m :xn x0m :total-m m0}
                pkts)]
    (if (pos? total-m)
      (let [inv (/ 1.0 total-m)]
        [(sp/v* vn inv) (sp/v* xn inv) total-m])
      [v0 x0 m0])))

(defn kinematics-ws
  "Position + velocity. v' = v + (Σ accel.*)·dt + Σ dv.*; x' = x + v'·dt
   (symplectic Euler), then the one-tick-stale COM frame-offset is subtracted from
   position (a pure Galilean shift, §6). Absorb-accrete/merge packets are blended
   for COM preservation — the absorbed mass's momentum shifts the survivor."
  [world dt]
  (let [foff (or (:phase0/frame-offset world) zero3)
        eids (ecs/entities-with world c/position c/velocity
                                c/mass c/radius c/body-kind)
        absorbs (merge (get-in world [:components c/absorb-accrete] {})
                       (get-in world [:components c/absorb-merge] {}))
        profiling? (:phase0/profile-subsystems? world)
        ;; Phase 1: accumulate accelerations from all influence cells.
        force-fn #(into {} (par/par-mapv
                            (fn [eid] [eid (sum-vec-influences world eid accel-sources)])
                            eids))
        [forces dt-force] (if profiling?
                            (profile/timing force-fn)
                            [(force-fn) nil])
        ;; Phase 2: symplectic leapfrog + COM blend from absorbed packets.
        leapfrog-fn #(reduce (fn [ws [eid v x]]
                               (-> ws
                                   (assoc-in [c/velocity eid] v)
                                   (assoc-in [c/position eid] x)))
                             {}
                             (par/par-mapv
                              (fn [eid]
                                (let [a   (get forces eid zero3)
                                      dv  (sum-vec-influences world eid dv-sources)
                                      v   (ecs/get-component world eid c/velocity)
                                      x   (ecs/get-component world eid c/position)
                                      m0  (double (or (ecs/get-component world eid c/mass) 0.0))
                                      v1  (sp/v+ (sp/v+ v (sp/v* a dt)) dv)
                                      x1  (sp/v- (sp/v+ x (sp/v* v1 dt)) foff)]
                                  (if-let [pkts (get absorbs eid)]
                                    (let [[v-blend x-blend _] (com-blend v1 x1 m0 pkts)]
                                      [eid v-blend x-blend])
                                    [eid v1 x1])))
                              eids))
        [ws dt-leap] (if profiling?
                       (profile/timing leapfrog-fn)
                       [(leapfrog-fn) nil])]
    (if profiling?
      (assoc ws :phase0/_profile
             (merge-with + (or (:phase0/_profile ws) {})
                         {:integrator/force-accum (double dt-force)
                          :integrator/leapfrog (double dt-leap)}))
      ws)))

(defn- kinematics-ws-soa
  "SoA-aware position + velocity updater. Reads positions/velocities/masses from
   the `:phase0/physics-soa` primitive arrays, sums acceleration contributions
   directly from their component cell maps, and produces the standard write-set
   for position and velocity. Falls back to the ECS path when the cache is absent."
  [world dt soa]
  (let [foff        (or (:phase0/frame-offset world) zero3)
        [fox foy foz] foff
        {:keys [eids n mass px py pz vx vy vz]} soa
        absorbs     (merge (get-in world [:components c/absorb-accrete] {})
                           (get-in world [:components c/absorb-merge] {}))
        dt          (double dt)
        fox         (double fox)
        foy         (double foy)
        foz         (double foz)
        accel-cells (mapv #(get-in world [:components %]) accel-sources)
        dv-cells    (mapv #(get-in world [:components %]) dv-sources)
        sum-vec     (fn [cells eid]
                      (reduce (fn [[ax ay az] cell]
                                (if-let [v (get cell eid)]
                                  [(+ ax (double (nth v 0)))
                                   (+ ay (double (nth v 1)))
                                   (+ az (double (nth v 2)))]
                                  [ax ay az]))
                              [0.0 0.0 0.0]
                              cells))
        profiling?  (:phase0/profile-subsystems? world)
        ;; Phase 1: accumulate accelerations from all influence cells.
        force-fn    #(into {} (par/par-mapv
                               (fn [idx]
                                 (let [eid (nth eids idx)]
                                   [eid (sum-vec accel-cells eid)]))
                               (range n)))
        [forces dt-force] (if profiling?
                            (profile/timing force-fn)
                            [(force-fn) nil])
        ;; Phase 2: symplectic leapfrog + COM blend from absorbed packets.
        leapfrog-fn #(reduce (fn [ws [eid v x]]
                               (-> ws
                                   (assoc-in [c/velocity eid] v)
                                   (assoc-in [c/position eid] x)))
                             {}
                             (par/par-mapv
                              (fn [idx]
                                (let [eid        (nth eids idx)
                                      [ax ay az] (get forces eid [0.0 0.0 0.0])
                                      [dvx dvy dvz] (sum-vec dv-cells eid)
                                      vx0        (aget ^doubles vx idx)
                                      vy0        (aget ^doubles vy idx)
                                      vz0        (aget ^doubles vz idx)
                                      px0        (aget ^doubles px idx)
                                      py0        (aget ^doubles py idx)
                                      pz0        (aget ^doubles pz idx)
                                      m0         (aget ^doubles mass idx)
                                      vx1        (+ vx0 (* ax dt) dvx)
                                      vy1        (+ vy0 (* ay dt) dvy)
                                      vz1        (+ vz0 (* az dt) dvz)
                                      px1        (- (+ px0 (* vx1 dt)) fox)
                                      py1        (- (+ py0 (* vy1 dt)) foy)
                                      pz1        (- (+ pz0 (* vz1 dt)) foz)]
                                  (if-let [pkts (get absorbs eid)]
                                    (let [[v-blend x-blend _] (com-blend [vx1 vy1 vz1] [px1 py1 pz1] m0 pkts)]
                                      [eid v-blend x-blend])
                                    [eid [vx1 vy1 vz1] [px1 py1 pz1]])))
                              (range n)))
        [ws dt-leap] (if profiling?
                       (profile/timing leapfrog-fn)
                       [(leapfrog-fn) nil])]
    (if profiling?
      (assoc ws :phase0/_profile
             (merge-with + (or (:phase0/_profile ws) {})
                         {:integrator/force-accum (double dt-force)
                          :integrator/leapfrog (double dt-leap)}))
      ws)))

(defn mass-ws
  "Mass. m' = max(0, m + Σ mass-flux.* + Σ absorb-mass) — the per-source mass
   fluxes (stellar wind/flare loss, XUV escape, disk→star viscous transfer) and
   the accretion/merge mass from absorb-accrete/merge packets are summed and
   applied. Only bodies with a flux or absorb packet this tick are rewritten."
  [world]
  (let [eids     (ecs/entities-with world c/mass)
        absorbs  (merge (get-in world [:components c/absorb-accrete] {})
                        (get-in world [:components c/absorb-merge] {}))
        cell     (into {}
                       (keep (fn [eid]
                               (let [dm   (sum-scalar-influences world eid mass-flux-sources)
                                     dm-a (absorb-mass-delta world eid)
                                     dm-t (+ dm dm-a)]
                                 (when-not (zero? dm-t)
                                   [eid (max 0.0 (+ (double (ecs/get-component world eid c/mass)) dm-t))]))))
                       eids)
        ;; Absorb packets may target entities that lacked mass-flux but still
        ;; gained mass from accretion. Ensure they are included even if not in
        ;; the entities-with-mass loop above (though they will be, since
        ;; the survivor always carries c/mass).
        extra (into {}
                    (keep (fn [eid]
                            (when-not (contains? (into #{} (keys cell)) eid)
                              (let [dm-a (absorb-mass-delta world eid)]
                                (when-not (zero? dm-a)
                                  [eid (max 0.0 (+ (double (ecs/get-component world eid c/mass)) dm-a))])))))
                    (keys absorbs))]
    (if (empty? (merge cell extra)) {} {c/mass (merge cell extra)})))

(defn temperature-ws
  "Temperature. The base value is the virial/radiative derivation owned by
   `stellar/temperature-system` (cores heat by Kelvin–Helmholtz contraction,
   worlds reach radiative equilibrium, diffuse gas is left at its background);
   the integrator then applies the player's heat.intervention ease on top — so
   a heat source/sink eases the freshly-derived temperature (as the old serial
   `apply-thermal-interventions` eased the post-fold value). Reusing the tested
   derivation keeps the formula unchanged (§9 non-goal).

   Absorb-merge packets from collision merges are blended AFTER the virial
   derivation: the mass-weighted temperature blend plus impact heating. This is
   a one-tick Jacobi delay — the merged body's radius (used by virial) won't
   update until structure re-derives it next tick."
  [world dt]
  (let [base ((:run (stellar/temperature-system dt)) world)
        base-cell (get base c/temperature {})
        heats     (get-in world [:components c/heat-intervention] {})
        ;; absorb-merge packets: blend temperature from collision merges
        merge-eids (set (keys (get-in world [:components c/absorb-merge] {})))
        merged-temps (when (seq merge-eids)
                       (persistent!
                        (reduce (fn [acc eid]
                                  (if-let [t (absorb-temp-delta world eid)]
                                    (assoc! acc eid t)
                                    acc))
                                (transient {}) merge-eids)))]
    (cond
      ;; both heat interventions and merge blends
      (and (not (empty? heats)) (seq merged-temps))
      (let [with-eased
            (reduce-kv
             (fn [cell eid cs]
               (let [t0 (double (or (get merged-temps eid)
                                    (get base-cell eid)
                                    (ecs/get-component world eid c/temperature)
                                    intervention/min-temp))]
                 (assoc cell eid (intervention/apply-thermal-contributions t0 cs))))
             (merge base-cell merged-temps)
             heats)]
        {c/temperature with-eased})

      ;; only heat interventions
      (not (empty? heats))
      (let [with-eased
            (reduce-kv
             (fn [cell eid cs]
               (let [t0 (double (or (get base-cell eid)
                                    (ecs/get-component world eid c/temperature)
                                    intervention/min-temp))]
                 (assoc cell eid (intervention/apply-thermal-contributions t0 cs))))
             base-cell
             heats)]
        {c/temperature with-eased})

      ;; only merge blends
      (seq merged-temps)
      {c/temperature (merge base-cell merged-temps)}

      :else base)))

(defn composition-ws
  "Composition. The integrator owns the blend: start from the snapshot
   composition, apply the H→He burn (comp.burn replaces it for burning cores),
   then the deuterium gate (comp.depletion zeroes :D for hot bodies). Only bodies
   carrying an influence this tick are rewritten — every other body's composition
   is untouched (spec §7.5; burn + depletion no longer co-write composition).

   Absorb-merge packets from collision merges are blended BEFORE burn/depletion:
   the mass-weighted composition of the survivor and the absorbed body."
  [world]
  (let [burns (get-in world [:components c/comp-burn] {})
        deps  (get-in world [:components c/comp-depletion] {})
        merge-eids (set (keys (get-in world [:components c/absorb-merge] {})))
        ;; blend compositions from merge packets first
        merged-comps (when (seq merge-eids)
                       (persistent!
                        (reduce (fn [acc eid]
                                  (if-let [cmp (absorb-comp-blend world eid)]
                                    (assoc! acc eid cmp)
                                    acc))
                                (transient {}) merge-eids)))
        ;; then apply burn/depletion on top
        all-eids (into (into (set (keys burns)) (keys deps)) merge-eids)]
    (if (empty? all-eids)
      {}
      {c/composition
       (into {}
             (keep (fn [eid]
                     (let [base (or (get merged-comps eid)
                                    (get burns eid)
                                    (ecs/get-component world eid c/composition))]
                       (when base
                         [eid (reduce (fn [c k] (assoc c k 0.0))
                                      base
                                      (get deps eid #{}))]))))
             all-eids)})))

(def ^:private torque-sources
  (get-in influence-registry [:angular-momentum :accumulate]))

(defn rotation-ws
  "Angular momentum + spin. L' = L + Σ torque.* + Σ absorb-L (the torque
   influences are per-step ΔL — magnetic braking, disk spin-up; the absorb
   packets carry the absorbed parcels' angular momentum). Spin is derived
   ω = L'/I."
  [world]
  (let [eids    (ecs/entities-with world c/angular-momentum c/mass c/radius)
        absorbs (merge (get-in world [:components c/absorb-accrete] {})
                       (get-in world [:components c/absorb-merge] {}))
        pairs   (par/par-mapv
                 (fn [eid]
                   (let [L   (or (ecs/get-component world eid c/angular-momentum) zero3)
                         dL  (sum-vec-influences world eid torque-sources)
                         dLa (absorb-angmom-sum world eid)
                         L'  (sp/v+ (sp/v+ L dL) dLa)
                         m   (ecs/get-component world eid c/mass)
                         r   (ecs/get-component world eid c/radius)
                         spin' (stellar/spin-from-angular-momentum L' m r)]
                     [eid L' spin']))
                 eids)]
    (reduce (fn [ws [eid L' spin']]
              (-> ws
                  (assoc-in [c/angular-momentum eid] L')
                  (assoc-in [c/spin eid] spin')))
            {}
            pairs)))

(defn integrator-system
  "Write-set system: the single owner of the dynamical/contended physical fields.
   Composes the per-field updaters (each writes a disjoint set of components, so
   the fragments merge cleanly). Sole writer of position, velocity, temperature,
   composition, angular-momentum, spin (and, as the lifecycle milestone lands,
   mass). Uses the `:phase0/physics-soa` cache for kinematics when present.

   Each major phase is wrapped with `profile/profile-section`; their
   `:phase0/_profile` maps are merged into the returned write-set so the
   benchmark harness can report subsystem timings."
  [dt]
  {:id     :integrator
   :writes #{c/position c/velocity c/mass c/temperature c/composition
             c/angular-momentum c/spin}
   :run    (fn [world]
             (let [kin  (if-let [soa (:phase0/physics-soa world)]
                          (kinematics-ws-soa world dt soa)
                          (kinematics-ws world dt))
                   mass (profile/profile-section
                         world :integrator/mass
                         (fn [_world] (mass-ws world)))
                   temp (profile/profile-section
                         world :integrator/temperature
                         (fn [_world] (temperature-ws world dt)))
                   comp (profile/profile-section
                         world :integrator/composition
                         (fn [_world] (composition-ws world)))
                   rot  (profile/profile-section
                         world :integrator/rotation
                         (fn [_world] (rotation-ws world)))
                   profile (apply merge-with +
                                  (map #(or (:phase0/_profile %) {}) [kin mass temp comp rot]))]
               (cond-> (merge kin mass temp comp rot)
                 (seq profile) (assoc :phase0/_profile profile))))})
