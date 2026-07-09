(ns domain.mass-transfer
  "Gradual mass transfer systems: Bondi–Hoyle–Lyttleton sink accretion and
   Roche-lobe overflow. These are pure snapshot-reading, write-set-emitting
   systems. They emit self-owned c/mass-flux-transfer (signed Δm) and
   c/dv-transfer (Δp/m recoil) influences on BOTH the donor and the sink; the
   integrator folds them through its uniform :mass / :velocity-delta accumulate,
   exactly like stellar-wind/flare mass loss — no cross-entity event routing.

   See docs/specs/gradual-mass-transfer-realspec.md.",
  (:require
   [domain.ecs.core       :as ecs]
   [domain.ecs.components  :as c]
   [domain.ecs.parallel    :as par]
   [domain.ecs.tick       :as tick]
   [domain.spatial.index   :as spatial]
   [domain.stellar        :as stellar]
   [law.mass-transfer     :as lmt]
   [law.stellar           :as lst]
   [shape.spatial         :as sp]))

(def ^:private zero3 [0.0 0.0 0.0])

;; Only resolved bodies act as accretion sinks. Nebula gas parcels are NOT
;; sinks — gas-on-gas capture at parcel resolution is handled by SPH + the
;; merge/collision path, and treating every parcel as a BHL sink means one
;; spatial neighbour query per parcel per tick (an O(N) storm). Sinks are the
;; handful of condensed bodies (see law.stellar state ladder).
(def ^:private sink-states
  #{:condensed-core :planetesimal :gas-giant :brown-dwarf :protostar :star :planet})

(def ^:private gas-pred
  "Predicate matching nebula gas parcels."
  #(= :nebula (:matter-state %)))

;; ---------------------------------------------------------------------------
;; Helpers
;; ---------------------------------------------------------------------------

(defn- add-flux!
  "Accumulate a signed mass delta `dm` and velocity delta `dv` for entity `eid`
   into a running write-set `ws` (self-owned influences the integrator folds via
   its generic :mass / :velocity-delta accumulate). Same component type written
   to both donor and sink ids keeps the single-writer invariant."
  [ws eid dm dv]
  (-> ws
      (update-in [c/mass-flux-transfer eid] (fnil + 0.0) (double dm))
      (update-in [c/dv-transfer eid] (fnil sp/v+ zero3) dv)))

(defn- add-disk!
  "Route accreted gas mass `dm` and its orbital angular momentum `l` to a
   disk-forming sink's protoplanetary disk (c/disk-mass-flux / c/disk-l-flux,
   read and folded into c/disk-mass / c/disk-angular-mom by disk-evolution).
   Gas captured by a protostar/star does NOT land directly in the stellar core —
   it forms a rotationally-supported disk that then feeds the star viscously
   (spec Part 1a / M3). The single writer of both channels is mass-transfer."
  [ws sink-eid dm l]
  (-> ws
      (update-in [c/disk-mass-flux sink-eid] (fnil + 0.0) (double dm))
      (update-in [c/disk-l-flux sink-eid] (fnil sp/v+ zero3) l)))

(defn- hash01
  "Deterministic [0,1) value from an integer key (for stable, non-random IMF /
   feedback acceptance — mirrors stellar's sink-formation hashing)."
  [n]
  (/ (double (mod (* (+ 1 (long n)) 2654435761) 1000003)) 1000003.0))

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

(defn- relative-velocity
  "|v_sink - v_donor| (m/s)."
  [world sink-eid donor-eid]
  (let [v-sink  (or (ecs/get-component world sink-eid c/velocity) zero3)
        v-donor (or (ecs/get-component world donor-eid c/velocity) zero3)]
    (sp/len (sp/v- v-sink v-donor))))

(defn- zone-average
  "Mass-weighted average of a scalar quantity over donor eids."
  [donors value-fn]
  (let [{:keys [total den]}
        (reduce (fn [{:keys [total den]} donor]
                  (let [m (:mass donor 0.0)
                        v (value-fn donor)]
                    {:total (+ total (* m v))
                     :den (+ den m)}))
                {:total 0.0 :den 0.0}
                donors)]
    (if (pos? den) (/ total den) 0.0)))

(defn- find-sink-eids
  "Return all resolved-body eids that can act as BHL accretion sinks."
  [world]
  (->> (ecs/entities-with world c/mass c/position c/velocity c/matter-state)
       (filterv #(contains? sink-states (ecs/get-component world % c/matter-state)))))

(defn- sink-accretion-rate
  "Compute the c/accretion-rate map for a single sink eid."
  [world sink-eid dt c-s]
  (let [M       (double (or (ecs/get-component world sink-eid c/mass) 0.0))
        pos     (or (ecs/get-component world sink-eid c/position) zero3)
        r-acc   (stellar/effective-accretion-radius world sink-eid)
        zone    (spatial/query-within-radius world pos r-acc gas-pred)
        rho-inf (zone-average zone #(donor-density world (:id %)))
        v-rel   (zone-average zone #(relative-velocity world sink-eid (:id %)))
        dot-m   (lmt/bhl-accretion-rate M rho-inf c-s v-rel)]
    {:sink/r-acc r-acc
     :sink/r-bondi (lmt/bondi-radius M c-s)
     :sink/dot-m dot-m
     :sink/dot-m-this-tick (* dot-m dt)
     :sink/efficiency 1.0
     :sink/regime (lmt/accretion-regime c-s v-rel)
     :sink/ambient-density rho-inf
     :sink/ambient-cs c-s
     :sink/relative-velocity v-rel}))

(defn- star-feedback-data
  "Return [{:pos :lum}] for all luminous :star bodies; used for UV heating cut."
  [world]
  (->> (ecs/entities-with world c/matter-state c/position c/luminosity)
       (filterv #(= :star (ecs/get-component world % c/matter-state)))
       (mapv (fn [eid]
               {:pos (ecs/get-component world eid c/position)
                :lum (double (or (ecs/get-component world eid c/luminosity) 0.0))}))))

(defn- sink-accretion-context
  "Return per-sink scalar values needed for BHL flux computation."
  [world sink-eid]
  (let [M      (double (or (ecs/get-component world sink-eid c/mass) 0.0))
        pos    (or (ecs/get-component world sink-eid c/position) zero3)
        v-sink (or (ecs/get-component world sink-eid c/velocity) zero3)
        sstate (ecs/get-component world sink-eid c/matter-state)
        disk?  (contains? #{:protostar :star} sstate)
        rate   (or (ecs/get-component world sink-eid c/accretion-rate) {})
        r-acc  (double (:sink/r-acc rate 0.0))
        dot-m  (double (:sink/dot-m rate 0.0))
        bias   (stellar/imf-accretion-bias M)]
    {:M M :pos pos :v-sink v-sink :disk? disk?
     :r-acc r-acc :dot-m dot-m :bias bias}))

(defn- accretion-zone
  "Return gas donors within r-acc of sink that pass feedback and IMF bias cuts."
  [world sink-eid pos r-acc bias star-data tick]
  (->> (spatial/query-within-radius world pos r-acc gas-pred)
       (remove #(= (:id %) sink-eid))
       (filterv #(and (< (stellar/stellar-feedback-temperature
                          (:position %) star-data stellar/feedback-radius)
                         1.0e4)
                      (< (hash01 (hash [(:id %) sink-eid tick])) bias)))))

(def ^:private disk-formation-radius
  "Captured gas is placed at this centrifugal radius when routed to a
   protostar/star disk. Gas falling from the Bondi radius (~10⁴ AU) carries far
   too much angular momentum to form a compact protoplanetary disk; in reality it
   loses angular momentum in the collapsing envelope and lands at ~1–10 AU. We
   use 10 AU as the effective disk-formation radius."
  1.5e12)

(defn- disk-angular-momentum-from-radius
  "Return angular momentum vector for mass `dm` placed in a Keplerian disk at
   `radius` around a mass `M` sink. Direction follows the captured parcel's
   orbital angular momentum around the sink; if that is zero, default to +z."
  [dm M radius dpos v-rel]
  (let [j (Math/sqrt (* lst/G M radius))
        L-raw (stellar/orbital-angular-momentum 1.0 dpos v-rel)
        L-len (sp/len L-raw)
        target-L (* dm j)]
    (if (pos? L-len)
      (sp/v* L-raw (/ target-L L-len))
      [0.0 0.0 target-L])))

(defn- donor-flux
  "Add flux for one donor parcel to the running write-set. If disk? is true,
   gas is routed to the disk at a compact formation radius; otherwise it is
   merged into the sink core."
  [world sink-eid pos M v-sink disk? donor dm ws]
  (let [donor-eid (:id donor)
        dpos      (or (:position donor) zero3)
        v-donor   (or (ecs/get-component world donor-eid c/velocity) zero3)
        r-rel     (sp/v- dpos pos)
        v-rel     (sp/v- v-donor v-sink)]
    (if disk?
      (-> ws
          (add-disk! sink-eid dm
                     (disk-angular-momentum-from-radius dm M disk-formation-radius r-rel v-rel))
          (add-flux! donor-eid (- dm) zero3))
      (-> ws
          (add-flux! sink-eid dm (if (pos? M) (sp/v* v-donor (/ dm M)) zero3))
          (add-flux! donor-eid (- dm) zero3)))))

(defn- accrete-donors
  "Drain up to `remaining` mass from `donors` (sorted), returning updated ws."
  [world sink-eid pos M v-sink disk? donor-cap remaining donors ws]
  (if (or (empty? donors) (<= remaining 0.0))
    ws
    (let [donor    (first donors)
          donor-m  (double (:mass donor))
          dm-avail (* donor-cap donor-m)
          dm       (min remaining dm-avail)]
      (recur world sink-eid pos M v-sink disk? donor-cap
             (- remaining dm)
             (rest donors)
             (donor-flux world sink-eid pos M v-sink disk? donor dm ws)))))

(defn- sink-accretion-flux
  "Process one sink's BHL accretion and return the updated write-set."
  [world sink-eid dt tick cap donor-cap star-data ws]
  (let [{:keys [M pos v-sink disk? r-acc dot-m bias]}
        (sink-accretion-context world sink-eid)
        zone     (accretion-zone world sink-eid pos r-acc bias star-data tick)
        zone-mass (reduce + 0.0 (map :mass zone))
        proposed (lmt/capped-delta-mass
                  {:dot-m dot-m :dt dt
                   :gas-mass zone-mass
                   :donor-mass zone-mass
                   :accretion-fraction-cap cap
                   :donor-fraction-cap donor-cap})]
    (if (or (<= proposed 0.0) (empty? zone))
      ws
      (accrete-donors world sink-eid pos M v-sink disk? donor-cap
                      proposed (sort-by :mass > zone) ws))))

(defn- roche-pair-state
  "Compute Roche geometry and overflow rate for a binary pair."
  [world pair-eid]
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
        rate   (if overflow? (lmt/ritter-isothermal-rate M-d a R-d R-L) 0.0)]
    {:donor donor :accr accr :M-d M-d :M-a M-a :R-d R-d
     :R-L R-L :delta delta :overflow? overflow? :rate rate}))

(defn- add-roche-fluxes
  "Emit conservative mass/dv transfer for an overflowing binary pair."
  [world donor accr M-d M-a dm ws]
  (let [v-d      (or (ecs/get-component world donor c/velocity) zero3)
        v-a      (or (ecs/get-component world accr c/velocity) zero3)
        dv-donor (if (pos? M-d) (sp/v* v-d (/ (- dm) M-d)) zero3)
        dv-accr  (if (pos? M-a) (sp/v* v-a (/ dm M-a)) zero3)]
    (-> ws
        (add-flux! donor (- dm) dv-donor)
        (add-flux! accr dm dv-accr))))

(defn- roche-pair-write-set
  "Write-set for one binary pair, including roche-lobe and any overflow flux."
  [world pair-eid dt ws]
  (let [{:keys [donor accr M-d M-a R-L delta overflow? rate]}
        (roche-pair-state world pair-eid)
        rate-map {:mass-transfer/rate rate
                  :mass-transfer/accreted-fraction lmt/default-accreted-fraction}
        ws'      (-> ws
                     (assoc-in [c/roche-lobe pair-eid]
                               {:roche-lobe/radius R-L
                                :roche-lobe/overfilling delta
                                :roche-lobe/overflow? overflow?})
                     (assoc-in [c/mass-transfer-rate pair-eid] rate-map))
        dm       (max 0.0 (min (* (- rate) dt) (* 0.25 M-d)))]
    (if (zero? dm)
      ws'
      (add-roche-fluxes world donor accr M-d M-a dm ws'))))

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
    :reads  #{c/mass c/position c/velocity c/temperature c/matter-state
              c/accretion-radius}
    :writes #{c/accretion-rate}
    :run    accretion-radius-system})
  ([world]
   (let [sinks (find-sink-eids world)
         dt    (double (or (:sim/dt world) 1.0))
         c-s   stellar/capture-velocity-dispersion]
     {c/accretion-rate
      (->> sinks
           (par/par-mapv #(vector % (sink-accretion-rate world % dt c-s)))
           (into {}))})))

;; ---------------------------------------------------------------------------
;; Sink accretion flux system
;; ---------------------------------------------------------------------------

(defn sink-accretion-flux-system
  "Rate-limited (gradual) BHL accretion of nebula gas onto resolved sinks — the
   SOLE gas→sink channel (stellar's whole-parcel absorb no longer takes gas, so
   there is one path and mass is conserved, M3). Reuses stellar's competitive
   feeding zone, IMF bias, and UV feedback so the emergent formation dynamics
   match the removed whole-parcel path; only the transfer is now gradual.

   Routing mirrors the old absorb-packets: gas captured by a protostar/star is
   disk-routed (c/disk-mass-flux / c/disk-l-flux → the disk grows, then feeds the
   star viscously); gas captured by a small sink (planetesimal/gas-giant/brown-
   dwarf) is merged directly into its core (c/mass-flux-transfer). Donors lose
   mass at their own velocity (c/mass-flux-transfer debit).

   Reads: c/accretion-rate, c/mass, c/position, c/velocity, c/matter-state,
   c/luminosity (stars, for feedback).
   Writes: c/mass-flux-transfer, c/dv-transfer, c/disk-mass-flux, c/disk-l-flux.

   0-arity returns the ECS system map; 1-arity returns a write-set."
  ([]
   {:id     :mass-transfer-flux
    :ns     'domain.mass-transfer
    :reads  #{c/mass c/position c/velocity c/accretion-radius c/accretion-rate
              c/matter-state c/temperature c/luminosity}
    :writes #{c/mass-flux-transfer c/dv-transfer c/disk-mass-flux c/disk-l-flux}
    :run    sink-accretion-flux-system})
  ([world]
   (let [dt        (double (or (:sim/dt world) 1.0))
         tick      (long (or (:tick world) 0))
         cap       (double (or (:genesis/accretion-fraction-cap world) lmt/default-accretion-fraction-cap))
         donor-cap (double (or (:genesis/donor-fraction-cap world) lmt/default-donor-fraction-cap))
         star-data (star-feedback-data world)]
     (reduce
      (fn [ws sink-eid] (sink-accretion-flux world sink-eid dt tick cap donor-cap star-data ws))
      {}
      (ecs/entities-with world c/mass c/position c/velocity c/accretion-rate)))))

;; ---------------------------------------------------------------------------
;; Roche-lobe system
;; ---------------------------------------------------------------------------

(defn roche-lobe-system
  "Compute c/roche-lobe and c/mass-transfer-rate for binary-pair entities, and
   emit conservative-overflow influences.

   Reads: c/binary-pair, c/mass, c/radius, c/position, c/velocity.
   Writes: c/roche-lobe, c/mass-transfer-rate, and c/mass-flux-transfer /
   c/dv-transfer on the donor (debit) and accretor (credit).

   0-arity returns the ECS system map; 1-arity returns a write-set."
  ([]
   {:id     :roche-lobe
    :ns     'domain.mass-transfer
    :reads  #{c/binary-pair c/mass c/radius c/position c/velocity}
    :writes #{c/roche-lobe c/mass-transfer-rate c/mass-flux-transfer c/dv-transfer}
    :run    roche-lobe-system})
  ([world]
   (let [dt (double (or (:sim/dt world) 1.0))]
     (reduce (fn [ws pair-eid] (roche-pair-write-set world pair-eid dt ws))
             {}
             (ecs/entities-with world c/binary-pair)))))

;; ---------------------------------------------------------------------------
;; Public system map
;; ---------------------------------------------------------------------------

(defn mass-transfer-system
  "Return the combined mass-transfer fan-out system map.

   A single registry entry owns c/accretion-rate, c/mass-flux-transfer,
   c/dv-transfer, c/disk-mass-flux, c/disk-l-flux, c/roche-lobe, and
   c/mass-transfer-rate so the single-writer invariant holds. Internally it runs
   the BHL radius/flux pass and the Roche-lobe pass and merges their write-sets."
  []
  {:id     :mass-transfer
   :ns     'domain.mass-transfer
   :reads  #{c/mass c/position c/velocity c/temperature c/matter-state
             c/accretion-rate c/accretion-radius c/binary-pair c/radius
             c/luminosity}
   :writes #{c/accretion-rate c/mass-flux-transfer c/dv-transfer
             c/disk-mass-flux c/disk-l-flux c/roche-lobe c/mass-transfer-rate}
   :run    (fn [world]
             ;; Merge the internal passes, then rewrite each owned component as a
             ;; contribution write-set: entities that carried it last tick but do
             ;; NOT this tick get the `removed` sentinel. Influence components are
             ;; MERGED (not replaced) by apply-write-set, so without this a sink's
             ;; flux would linger and re-apply every tick → runaway over-accretion.
             (let [ws (merge-with into
                                  (accretion-radius-system world)
                                  (sink-accretion-flux-system world)
                                  (roche-lobe-system world))]
               (reduce (fn [acc ct]
                         (merge acc (tick/contribution-write-set
                                     ct (get ws ct {})
                                     (keys (get-in world [:components ct] {})))))
                       {}
                       [c/accretion-rate c/mass-flux-transfer c/dv-transfer
                        c/disk-mass-flux c/disk-l-flux c/roche-lobe
                        c/mass-transfer-rate])))})

(defn systems
  "Compatibility alias. Returns a vector containing `mass-transfer-system`."
  []
  [(mass-transfer-system)])
