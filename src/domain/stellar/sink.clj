(ns domain.stellar.sink
  "Sink / protostar formation and accretion zones: isolation criterion, Bondi
   capture radius, and competitive accretion."
  (:require
   [clojure.math :as math] [law.stellar                  :as law]
   [domain.stellar.thermodynamics :as thermo]
   [domain.ecs.core              :as ecs]
   [domain.ecs.components        :as c]
   [domain.ecs.tick              :as tick]
   [shape.spatial                :as sp]))

(def ^:const feeding-zone-factor
  "How many gas smoothing-lengths wide a freshly-condensed body's gravitational
   feeding zone is. The toy resolution cannot resolve real gas accretion onto a
   core, so a condensing body latches a capture radius this many times its gas
   smoothing length and sweeps up neighbours by literal overlap (the merge
   handler keeps the larger zone). Captured from the diffuse GAS radius at the
   instant of condensation — before Structure's KH contraction shrinks the
   photosphere — so the zone stays wide enough for a core to assemble.

   The zone must span ~twice the initial inter-parcel spacing (≈ extent/N^(1/3))
   so the first overdense body reaches SEVERAL neighbours and runs away, rather
   than just touching its nearest one. At the condensation smoothing length
   (≈0.1·0.004·extent) that is a factor of a few hundred. This constant is the
   floor — validated to ignite the default ~10³-parcel production cloud (a star
   by ~t=180) where sequential forms nothing at all; `create-world` raises it for
   coarser (fewer-parcel) clouds via `sink/resolution-feeding-zone-factor`. Below it
   the cloud condenses into a sub-stellar debris/protostar swarm that fragments
   instead of assembling a core (design §7c)." 50.0)

(defn sink-exclusion-zones
  "Precompute the accretion radii and positions of all existing sinks (bodies
   that have condensed out of the nebula). Returns a seq of {:position :radius}
   maps for use in the isolation criterion. Pure — reads only from the frozen
   snapshot."
  [world]
  (let [sinks (ecs/entities-with world c/matter-state c/accretion-radius c/position)]
    (mapv (fn [eid]
            {:position (ecs/get-component world eid c/position)
             :radius   (double (or (ecs/get-component world eid c/accretion-radius) 0.0))})
          sinks)))

(defn within-existing-sink?
  "True when a parcel's position falls inside any existing sink's accretion
   radius. This is the isolation criterion from Federrath et al. (2010) — a
   parcel can only condense if it is NOT within an existing sink's zone.
   Prevents the cloud from condensing wholesale after the first sink forms."
  [parcel-pos sink-zones]
  (when (and parcel-pos (seq sink-zones))
    (some (fn [{:keys [position radius]}]
            (let [d (sp/dist parcel-pos position)]
              (< d (double (or radius 0.0)))))
          sink-zones)))

(defn bondi-radius
  "Bondi accretion radius: r = G·M / c_s². Grows with mass — a forming star's
   capture zone expands as it accretes. Returns 0 for non-positive inputs."
  [mass snd-spd]
  (let [m (double (or mass 0.0))
        cs (double (or snd-spd 0.0))]
    (if (and (pos? m) (pos? cs))
      (/ (* law/G m) (* cs cs))
      0.0)))

(def ^:const capture-velocity-dispersion
  "Characteristic ambient velocity scale (m/s) — the combined thermal sound speed
   + turbulent/infall dispersion of nebular gas — used as the denominator floor in
   the Bondi capture radius. Bounds the radius so a cold, slow core does not get an
   unbounded feeding zone; ~0.25 km/s ≈ the sound speed of ~10 K molecular gas." 250.0)

(defn pending-absorbed-mass
  "Σ mass of the absorb packets (accrete + merge) already emitted for `eid` and
   sitting in the snapshot awaiting the integrator. With the integrator in the
   fan-out (spec Fix 5) an absorbed parcel's mass lands on the sink one tick
   after its packet is emitted; anything that gates on the sink's mass must
   count that in-flight generation or the Bondi runaway doubles its doubling
   time (each capture generation would wait a tick to enlarge the reach).
   Reads only snapshot channels — Jacobi-pure prediction, like the gravity
   drift predictor."
  [world eid]
  (reduce (fn [acc ct]
            (reduce (fn [a p] (+ a (double (or (:mass p) 0.0))))
                    acc
                    (get-in world [:components ct eid] [])))
          0.0
          [c/absorb-accrete c/absorb-merge]))

(defn effective-accretion-radius
  "The gravitational capture / feeding radius actually in force for a sink this
   tick (used by sink-formation to decide which gas it swallows): the LARGER of its
   frozen condensation feeding zone (set once by the classifier, the sole writer of
   c/accretion-radius) and its mass-dependent Bondi radius r = GM/c_s². Because the
   Bondi term grows ∝ M, the most massive core reaches — and captures — the most
   gas, so it accretes fastest and RUNS AWAY, funnelling the cloud into one dominant
   star instead of a swarm of equal cores (spec Part 1a, Bonnell/Bate competitive
   accretion). The mass includes the snapshot's in-flight absorb packets
   (`pending-absorbed-mass`) so the runaway is not slowed by the integrator's
   one-tick application lag. Read-only: never writes the component, so the
   single-writer invariant on c/accretion-radius holds."
  [world eid]
  (let [frozen (double (or (ecs/get-component world eid c/accretion-radius) 0.0))]
    (if (false? (:genesis/competitive-accretion? world))
      frozen ;; disabled → fixed condensation zone (the pre-Part-1a fragmenting behaviour)
      (let [m   (+ (double (or (ecs/get-component world eid c/mass) 0.0))
                   (pending-absorbed-mass world eid))]
        ;; The capture denominator is the ambient velocity DISPERSION of the gas
        ;; relative to the core — the cold nebular sound speed plus turbulence
        ;; (~0.25 km/s). Deliberately NOT the sink's own temperature (a protostar is
        ;; hot, c_s ~ 10⁵ m/s, but it is the GAS being captured whose thermal energy
        ;; resists capture), and NOT the sink's bulk speed in the world frame: in a
        ;; coherently collapsing/rotating cloud a core moves WITH its local gas, so
        ;; the Bondi–Hoyle relative velocity is the turbulent dispersion, not the
        ;; core's absolute speed. Using absolute speed collapses the radius for every
        ;; fast-moving core and defeats the runaway (the cloud just fragments).
        (max frozen (bondi-radius m capture-velocity-dispersion))))))

(defn resolution-feeding-zone-factor
  "Feeding-zone factor scaled to the cloud's resolution: a core must bridge the
   initial inter-parcel spacing (≈ extent/N^(1/3)) to capture neighbours, and the
   spacing/smoothing-length ratio grows as the parcel count shrinks. Returns the
   `feeding-zone-factor` floor for the default kilo-parcel cloud and larger for
   coarser clouds, so condensed bodies assemble a core at any resolution."
  [gas-count]
  (let [n (double (max 1 (or gas-count 1000)))]
    (max feeding-zone-factor (/ 500.0 (math/pow n (/ 1.0 3.0))))))

(def ^:const disk-formation-radius
  "Effective centrifugal radius (m) at which captured material joins a
   protostar/star disk — 10 AU. Material falling from the Bondi/capture radius
   (~10⁴ AU) carries far too much angular momentum to form a compact
   protoplanetary disk; in reality it sheds that angular momentum to the
   collapsing envelope (shocks, gravitational torques) and lands at ~1–10 AU.
   This is the project's single decision on what survives capture: BOTH capture
   channels renormalize to this radius — the gradual BHL gas channel
   (domain.mass-transfer/donor-flux) and the whole-parcel absorb channel
   (`absorb-packet`). Do not introduce a second value."
  1.5e12)

(defn disk-angular-momentum-from-radius
  "Angular momentum vector for mass `m` placed in a Keplerian disk at `radius`
   around a mass `M` sink: |L| = m·√(G·M·radius). Direction follows the
   captured parcel's raw orbital angular momentum about the sink (`dpos`,
   `v-rel`); if that is zero, default to +z.

   The shed difference between the parcel's raw capture-scale orbital L and
   this formation-scale L is carried off by envelope torques during infall
   (sub-grid) — deliberately NOT deposited in the sink's bulk spin or the disk;
   storing it would spin protostars toward breakup and re-inflate derived disk
   radii to the clump scale (the kAU-disk birth defect,
   kanban/tasks/sink-absorb-angular-momentum-renormalization.md). This mirrors
   the BHL channel's accounting, where the donor gas keeps its orbital motion
   and only formation-scale L is deposited."
  [m M radius dpos v-rel]
  (let [j (Math/sqrt (* law/G (double M) (double radius)))
        L-raw (thermo/orbital-angular-momentum 1.0 dpos v-rel)
        L-len (sp/len L-raw)
        target-L (* (double m) j)]
    (if (pos? L-len)
      (sp/v* L-raw (/ target-L L-len))
      [0.0 0.0 target-L])))

(defn- absorb-packet
  "Build one absorb-accrete packet for a parcel being swallowed by a sink.

   Linear momentum accounting is untouched: the packet carries the parcel's raw
   `:velocity`/`:mass` and the integrator's COM-preserving blend applies them
   exactly as before. Only the DISK-route angular-momentum term is
   renormalized: a disk-routed packet stores formation-scale L
   (`disk-angular-momentum-from-radius` at `disk-formation-radius`, 10 AU)
   instead of the raw capture-scale m·(r_rel × v_rel) (capture happens at the
   Bondi/feeding radius, ~10⁴ AU, whose j²/(GM) ≈ 10³–10⁵ AU birthed the
   kAU-disk defect). The shed L rides off with the collapsing envelope
   (sub-grid torques — see `disk-angular-momentum-from-radius`); non-disk-route
   packets (solid bodies merged straight into the sink's bulk) keep the raw
   orbital L, which correctly spins up the bulk."
  [world sink-p sink-v sink-m disk-former? eid]
  (let [m (double (or (ecs/get-component world eid c/mass) 0.0))
        v (or (ecs/get-component world eid c/velocity) [0 0 0])
        p (or (ecs/get-component world eid c/position) [0 0 0])
        pstate (ecs/get-component world eid c/matter-state)
        r-rel (sp/v- p sink-p)
        v-rel (sp/v- v sink-v)
        ;; Diffuse gas and small planetesimals are routed through the disk around a
        ;; protostar/star so they can participate in viscous accretion and planet
        ;; formation. Swallowed gas-giant embryos, brown dwarfs, and protostellar
        ;; fragments are merged directly into the sink (spec Part 1a competitive
        ;; accretion — fragments are swallowed, not re-disked).
        disk-route (and disk-former?
                        (or (= :nebula pstate)
                            (= :planetesimal pstate)))
        L-p (if disk-route
              (disk-angular-momentum-from-radius m sink-m disk-formation-radius r-rel v-rel)
              (thermo/orbital-angular-momentum m r-rel v-rel))]
    {:mass m :velocity v :position p
     :angular-momentum L-p
     :disk-route disk-route}))

(defn- absorb-packets
  "Build the absorb-accrete packet vector for the parcels a sink swallows this
   tick, instead of directly writing position/velocity/mass/disk-mass (spec §5).
   The integrator reads absorb-accrete next tick and applies COM-preserving
   velocity/position/mass changes; disk-evolution reads it to grow
   disk-mass/disk-angular-mom. Pure — reads only the frozen snapshot.

   Only diffuse :nebula gas is routed through the disk (so it can form a
   rotationally-supported accretion disk around a protostar/star). Swallowed
   solid/degenerate bodies (:planetesimal, :gas-giant, :brown-dwarf,
   :protostar, etc.) are merged directly into the sink's mass, preserving
   hierarchical competitive accretion without inflating the disk past its
   fragmentation threshold."
  [world sink-eid parcels]
  (let [sink-p (or (ecs/get-component world sink-eid c/position) [0 0 0])
        sink-v (or (ecs/get-component world sink-eid c/velocity) [0 0 0])
        sink-m (double (or (ecs/get-component world sink-eid c/mass) 0.0))
        sink-state (ecs/get-component world sink-eid c/matter-state)
        disk-former? (contains? #{:protostar :star} sink-state)]
    (mapv #(absorb-packet world sink-p sink-v sink-m disk-former? %) parcels)))

(declare imf-accretion-bias stellar-feedback-temperature hash01 feedback-radius)

(defn- sink-candidate?
  "True if `parcel-eid` is a valid hierarchical capture target for `sink-eid`
   this tick, respecting IMF bias and stellar feedback."
  [world sink-eid sink-m sink-pos sink-acc parcel-eid consumed star-data bias tick]
  (and (not= parcel-eid sink-eid)
       (ecs/alive? world parcel-eid)
       (not (contains? consumed parcel-eid))
       (let [pstate (ecs/get-component world parcel-eid c/matter-state)
             pmass  (double (or (ecs/get-component world parcel-eid c/mass) 0.0))
             competitive? (not (false? (:genesis/competitive-accretion? world)))]
         (and
           ;; Only smaller solid/degenerate bodies are captured; gas accretes via
           ;; mass_transfer's gradual BHL channel to avoid double-counting.
          (or (and (#{:planetesimal :gas-giant :brown-dwarf} pstate) (< pmass sink-m))
              (and competitive? (= :protostar pstate) (< pmass sink-m)))
          (let [pos  (ecs/get-component world parcel-eid c/position)
                dist (sp/dist sink-pos pos)]
            (and (< dist sink-acc)
                 (< (hash01 (hash [parcel-eid sink-eid tick])) bias)
                 (if (= :nebula pstate)
                    ;; Stellar feedback: reject gas heated above Jeans temperature.
                   (< (stellar-feedback-temperature pos star-data feedback-radius) 1.0e4)
                   true)))))))

(defn- sink-formation-context
  "Build the read-only context needed for sink-formation: clear stale absorb
   packets, sorted sinks, candidate parcels, feedback star data, and the set of
   parcels already marked consumed."
  [world]
  (let [w0 (update world :components dissoc c/absorb-accrete)]
    {:w0 w0
     :sinks (->> (ecs/entities-with world c/matter-state c/accretion-radius c/position c/mass)
                 (sort-by #(double (or (ecs/get-component world % c/mass) 0.0)) #(compare %2 %1))
                 vec)
     :gas-parcels (ecs/entities-with world c/matter-state c/position c/mass c/velocity)
     :star-data (mapv (fn [eid]
                        {:pos (ecs/get-component world eid c/position)
                         :lum (double (or (ecs/get-component world eid c/luminosity) 0.0))})
                      (filterv #(= :star (ecs/get-component world % c/matter-state))
                               (ecs/entities-with world c/matter-state c/position c/luminosity)))
     :consumed0 (set (keys (get-in world [:components c/consumed-accrete] {})))
     :tick (or (:tick world) 0)}))

(defn- accrete-one-sink
  "Return an updated `[absorbs consumed]` accumulator after processing a single
   sink. Reads only from the frozen snapshot; the actual writes are folded later."
  [world {:keys [w0 gas-parcels star-data tick]} [absorbs consumed] sink-eid]
  (if-not (ecs/alive? world sink-eid)
    [absorbs consumed]
    (let [sink-pos (ecs/get-component world sink-eid c/position)
          sink-m (double (or (ecs/get-component world sink-eid c/mass) 0.0))
          sink-acc (effective-accretion-radius w0 sink-eid)
          bias (imf-accretion-bias sink-m)
          nearby (filterv #(sink-candidate? world sink-eid sink-m sink-pos sink-acc %
                                            consumed star-data bias tick)
                          gas-parcels)]
      (if (seq nearby)
        [(assoc absorbs sink-eid (absorb-packets world sink-eid nearby))
         (into consumed nearby)]
        [absorbs consumed]))))

(defn- sink-formation-write-set
  "Fold absorb packets into the contribution write-set for c/absorb-accrete and
   emit c/consumed-accrete markers for any newly claimed parcels."
  [world absorbs consumed consumed0]
  (let [new-consumed (reduce disj consumed consumed0)
        prior-absorb (keys (get-in world [:components c/absorb-accrete] {}))]
    (cond-> (tick/contribution-write-set c/absorb-accrete absorbs prior-absorb)
      (seq new-consumed)
      (assoc c/consumed-accrete (into {} (map (fn [eid] [eid true])) new-consumed)))))

(defn sink-formation-system
  "Double-buffer write-set system: every sink absorbs :nebula gas parcels within
   its gravitational capture zone. Three Phase 1 additions:

   1. IMF bias: accretion probability is mass-dependent — high-mass sinks
      accrete less efficiently, steering toward the Kroupa/Salpeter IMF.
   2. Stellar feedback: UV radiation from nearby stars heats gas parcels,
      suppressing Jeans collapse in their vicinity (feedback radius ~0.5 AU).
   3. Disk formation: angular momentum of accreted material is tracked in
      c/disk-angular-mom and c/disk-mass.

   Emits absorb-accrete influence + consumed-accrete lifecycle markers (spec §5)
   instead of directly writing contended physical state. Stale absorb-accrete
   entries not re-emitted this tick get the `removed` sentinel (the integrator
   consumed last tick's; lingering packets would double-count) — and the Bondi
   feeding radius is computed WITHOUT the snapshot's in-flight accrete packets,
   matching the clear-first legacy path. Parcels claimed by one sink this tick
   are tracked locally so a later (smaller) sink cannot double-claim them.

   0-arity returns the native write-set system for the fan-out; 1-arity applies
   the emitted write-set to `world` and returns the updated world — a
   convenience for benches, tests, and REPL use."
  ([]
   {:id     :sink-formation
    :writes #{c/absorb-accrete c/consumed-accrete}
    :run
    (fn [world]
      (let [{:keys [sinks consumed0] :as ctx} (sink-formation-context world)
            [absorbs consumed] (reduce (partial accrete-one-sink world ctx)
                                       [{} consumed0]
                                       sinks)]
        (sink-formation-write-set world absorbs consumed consumed0)))})
  ([world] (tick/apply-write-set world ((:run (sink-formation-system)) world))))

;; --- Stellar formation: IMF, disks, feedback (Phase 1) ----------------------
;; Three improvements to the formation pipeline:
;; 1. IMF bias: mass-dependent accretion efficiency steers toward Kroupa distribution
;; 2. Disk formation: angular momentum of accreted material → protoplanetary disk
;; 3. Stellar feedback: UV heating suppresses Jeans collapse near hot stars

(defn hash01
  "Deterministic [0,1) value from an integer key — for stable, non-random
   per-entity decisions. Used by IMF bias for accretion probability."
  [n]
  (/ (double (mod (* (inc (long n)) 2654435761) 1000003)) 1000003.0))

(defn imf-accretion-bias
  "Mass-dependent accretion efficiency bias from the Kroupa IMF.
   Returns a factor in (0, 1] that multiplies the accretion probability.
   Low-mass sinks accrete efficiently (factor ~1); high-mass sinks are
   suppressed (factor < 1) to steer toward the observed IMF slope.

   Kroupa slopes: α₀ = -0.3 (m < 0.08 M☉), α₁ = -1.3 (0.08-0.5 M☉),
   α₂ = -2.3 (m > 0.5 M☉, Salpeter). We use the INVERSE of the slope
   as the bias: high-mass sinks have positive exponent → factor < 1."
  [mass]
  (let [m (double (or mass 0.0))
        m-msun (/ m thermo/solar-mass-kg)]
    (cond
      (< m-msun 0.08) 1.0                    ;; brown dwarf regime: no suppression
      (< m-msun 0.5)  (math/pow (/ 0.5 m-msun) 0.15) ;; gentle suppression
      (< m-msun 2.0)  (math/pow (/ 1.0 m-msun) 0.25) ;; moderate suppression
      :else            (math/pow (/ 2.0 m-msun) 0.4)))) ;; strong suppression for O-stars

(defn stellar-feedback-temperature
  "Temperature added to a gas parcel by UV radiation from nearby stars.
   Uses the bolometric luminosity of all stars within a feedback radius.
   Returns the additional temperature (K) from UV heating."
  [gas-pos star-data fb-radius]
  (reduce (fn [acc {:keys [pos lum]}]
            (let [d (sp/dist gas-pos pos)]
              (if (< d fb-radius)
                ;; UV heating: F = L/(4πd²), ΔT = F·dt/(m·c_p) simplified
                ;; Use a calibrated scaling: ΔT ∝ L/d²
                (let [F (/ (double lum) (* 4.0 math/PI d d))]
                  (+ acc (* 0.01 F))) ;; calibrated to suppress Jeans collapse
                acc)))
          0.0 star-data))

(def ^:const feedback-radius
  "Distance (m) within which stellar UV feedback suppresses Jeans collapse.
   ~0.5 AU — the photoevaporation radius of a typical HII region."
  7.5e10)
