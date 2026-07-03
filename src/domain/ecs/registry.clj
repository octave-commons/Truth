(ns domain.ecs.registry
  "System registry + the single-writer invariant.

   This is the data backbone of the double-buffer ECS (see
   `docs/notes/2026.06.26-ecs-double-buffer-single-writer-spec.md`). Each system
   declares the component types it READS and the component types it WRITES. The
   load-bearing rule is Rule 2 of the spec: *every component type is written by
   exactly one system*. Once that holds, per-tick write-sets are provably
   disjoint, so the end-of-tick fold is conflict-free and each system can run on
   its own thread with no locks.

   IMPORTANT: the `systems` vector below declares the CURRENT (Gauss–Seidel)
   reality, not the target ownership. The current pipeline violates single-writer
   in 10 places (position, density, pressure, radius, matter-state,
   accretion-radius, hydro-accel, temperature, spin, b-field). So
   `write-conflicts` is intentionally non-empty today: it is the migration
   to-do list. Each migration step (spec §9) deletes a second writer here until
   `(write-conflicts systems)` is `{}`, at which point `assert-single-writer!`
   is wired into startup.

   No tick logic lives here — only the declaration and its validation."
  (:require
    [clojure.string :as str]
    [domain.ecs.components :as c]))

;; ---------------------------------------------------------------------------
;; The registry — current reality of the 12-system Gauss–Seidel pipeline.
;;
;; :reads / :writes are sets of component-type keywords (domain.ecs.components).
;; A component appearing in :writes means the system assoc's OR dissoc's that
;; component column — removal is a write (single owner clears its own staleness).
;; Discrete-event handlers (e.g. stellar merge) are NOT systems and are excluded;
;; collision-detection itself writes no component state, it only emits events.
;; ---------------------------------------------------------------------------

(def systems
  "Declared systems with their reads/writes. Order is irrelevant to the
   invariant — it exists only to enumerate systems and validate disjointness."
  [;; The Structure owner: shape + compactness. radius and density are one
   ;; geometric fact, so one system owns the pair (branching on matter-state:
   ;; gas SPH / solid material density / KH oblate contraction). Subsumes the
   ;; radius+density writes of the old density-system, jeans-collapse, collapse.
   {:id     :structure
    :ns     'domain.stellar
    :reads  #{c/matter-state c/mass c/radius c/density c/position c/temperature
              c/pressure c/oblateness c/angular-momentum}
    :writes #{c/radius c/density c/oblateness c/rotation-axis}}

   ;; Pressure is a pure equation of state P = ρ k_B T / m_H — every former
   ;; writer recomputed the identical ideal-gas pressure, so one EOS system owns
   ;; it and derives it from density + temperature (spec §4 derivations).
   {:id     :eos
    :ns     'domain.stellar
    :reads  #{c/density c/temperature}
    :writes #{c/pressure}}

   {:id     :hydro
    :ns     'domain.hydro
    :reads  #{c/matter-state c/position c/density c/pressure c/mass c/radius}
    :writes #{c/accel-pressure}}

   ;; Jeans-collapse was removed from the pipeline; accretion-radius is now
   ;; written by the classifier (sole writer of both matter-state and accretion-radius).

   ;; The classifier is the SOLE writer of matter-state AND accretion-radius:
   ;; the authentic formation state machine (Jeans+mass+ignition) with throttled
   ;; condensation. Subsumes the old classify system, jeans-collapse, and fusion.
   {:id     :classifier
    :ns     'domain.stellar
    :reads  #{c/matter-state c/mass c/radius c/density c/temperature
              c/pressure c/composition c/promotion-signal}
    :writes #{c/matter-state c/accretion-radius}}

   ;; Gravity is split out of the old orbital system: the Barnes–Hut tree-walk
   ;; emits the accel.gravity contribution on its own thread, and the thin motion
   ;; integrator sums all accel.* contributions and advances position/velocity.
   {:id     :gravity
    :ns     'domain.orbital.system
    :reads  #{c/position c/velocity c/mass c/radius c/body-kind}
    :writes #{c/accel-gravity}}

   ;; Player paid warp-space force (gravity well / repulsor): a placed, decaying
   ;; transient that writes its own accel channel, summed by motion.
   {:id     :warp
    :ns     'domain.intervention
    :reads  #{c/position c/mass}
    :writes #{c/accel-warp}}

   ;; The single integrator (domain.integrator): sole writer of position +
   ;; velocity (and, as the unified-physical-state migration lands,
   ;; mass/angular-momentum/spin/temperature/composition). Sums every accel.*
   ;; contribution and advances the body; applies the COM frame-offset. See
   ;; docs/notes/specs/2026.06.29-unified-physical-state-integrator-spec.md.
   {:id     :integrator
    :ns     'domain.integrator
    :reads  #{c/position c/velocity c/mass c/radius c/body-kind
              c/accel-gravity c/accel-pressure c/accel-lorentz c/accel-observer
              c/accel-warp c/frame-offset
              c/matter-state c/density c/luminosity c/sed-bands c/composition
              c/heat-intervention c/comp-burn c/comp-depletion
              c/angular-momentum c/spin c/torque-em c/torque-disk
              c/mass-flux-wind c/mass-flux-flare c/mass-flux-xuv c/mass-flux-disk
              c/dv-wind c/dv-flare c/absorb-merge c/absorb-accrete}
    :writes #{c/position c/velocity c/temperature c/composition
              c/angular-momentum c/spin c/mass}}

   ;; The observer pull-toward-focus nudge: a fan-out emitter (was serial in
   ;; tick-world). Sole writer of accel.observer; the integrator sums it.
   {:id     :observer-accel
    :ns     'domain.player
    :reads  #{c/position c/mass c/observer}
    :writes #{c/accel-observer}}

   ;; Player heat source/sink: emits the per-body temperature ease the integrator
   ;; applies (was the serial apply-thermal-interventions). Sole writer.
   {:id     :thermal-intervention
    :ns     'domain.intervention
    :reads  #{c/position c/matter-state c/temperature}
    :writes #{c/heat-intervention}}

   ;; Barrier systems: run SERIALLY after the fold, so they are exempt from the
   ;; single-writer invariant (like event handlers — spec §6). collision emits
   ;; discrete events whose merge handler despawns; recenter is a global COM
   ;; reduction over the whole folded world.
   ;; Collision detection is now a fan-out emitter (B3): its handler emits
   ;; c/absorb-merge, c/consumed-merge, and c/spawn-request-shatter — all
   ;; single-writer. Runs in the parallel fan-out, not as a serial barrier.
   {:id            :collision-detection
     :ns            'domain.physics.collision
     :reads         #{c/position c/radius c/matter-state c/accretion-radius
                      c/velocity c/mass c/angular-momentum
                      c/temperature c/composition c/body-kind}
     :writes        #{c/absorb-merge c/consumed-merge c/spawn-request-shatter}
     :emits-events? true}

   ;; Fusion promotion: emits c/promotion-signal for protostars that now meet
   ;; fusion conditions. Runs in the parallel fan-out (was a post-fold barrier).
   ;; One-tick Jacobi delay — classifier + fusion read the signal next tick.
   {:id            :fusion-promotion
     :ns            'domain.stellar
     :reads         #{c/matter-state c/temperature c/pressure c/composition
                       c/density c/radius c/mass c/luminosity}
     :writes        #{c/promotion-signal}}

   ;; Sink formation: absorbs nearby gas parcels into sinks. Emits
   ;; absorb.accrete + consumed.accrete; the integrator reads absorb-accrete and
   ;; applies mass/velocity/position/angmom changes; disk-evolution reads it to
   ;; grow disk-mass (one-tick Jacobi delay, spec §5). Runs in the parallel
   ;; fan-out (was a post-fold barrier).
   {:id            :sink-formation
    :ns            'domain.stellar
    :reads         #{c/matter-state c/accretion-radius c/position c/mass
                      c/velocity c/disk-mass c/disk-angular-mom c/luminosity
                      c/temperature c/consumed-accrete}
    :writes        #{c/absorb-accrete c/consumed-accrete}}

   ;; :collapse is fully retired: its writes were dissolved into Structure (shape),
   ;; :thermal (virial temperature), :em (spin), and :field (b-field).

   {:id     :fusion
    :ns     'domain.stellar
    :reads  #{c/matter-state c/temperature c/pressure c/composition
              c/promotion-signal}
    :writes #{c/luminosity}}

   ;; Panchromatic SED: computes per-band luminosities from T_eff and log g.
   ;; Reads luminosity (from fusion) and radius (from structure). Must run AFTER
   ;; fusion so luminosity is settled.
   {:id     :stellar-sed
    :ns     'domain.stellar
    :reads  #{c/matter-state c/luminosity c/radius c/mass}
    :writes #{c/sed-bands}}

   ;; Stellar atmosphere shells: 4-layer profile (photosphere → corona).
   ;; Reads luminosity, radius, mass, b-field. Must run AFTER fusion and field.
   {:id     :atmosphere-shells
    :ns     'domain.stellar
    :reads  #{c/matter-state c/luminosity c/radius c/mass c/b-field}
    :writes #{c/atmosphere-shells}}

   ;; Deuterium depletion: emits comp.depletion (the keys to zero, just :D) for
   ;; hot bodies (T > 1e6 K). One-way gate. A plain fan-out emitter now; the
   ;; integrator owns composition and applies the gate (spec §7.5).
   {:id     :deuterium-depletion
    :ns     'domain.stellar
    :reads  #{c/matter-state c/temperature c/composition}
    :writes #{c/comp-depletion}}

   ;; XUV atmospheric escape: planetary mass loss from stellar XUV. A fan-out
   ;; emitter — mass loss → mass-flux.xuv (integrator owns mass), plus the
   ;; diagnostic atmosphere-escape (its own column).
   {:id     :xuv-atmospheric-escape
    :ns     'domain.genesis
    :reads  #{c/matter-state c/mass c/radius c/position c/sed-bands c/luminosity}
    :writes #{c/mass-flux-xuv c/atmosphere-escape}}

   ;; Stellar wind: stars shed mass as plasma. Owns its reservoir; emits the loss
   ;; (mass-flux.wind), recoil (dv.wind), the parcel (spawn-request.wind) and the
   ;; ablation reap (consumed.wind). A fan-out emitter (was a serial barrier).
   {:id     :stellar-wind
    :ns     'domain.stellar
    :reads  #{c/matter-state c/mass c/radius c/position c/velocity c/wind-reservoir
              c/atmosphere-shells c/sed-bands c/accretion-radius c/composition c/b-field}
    :writes #{c/wind-reservoir c/mass-flux-wind c/dv-wind
              c/spawn-request-wind c/consumed-wind}}

   ;; Stellar flares: episodic CMEs. Emits the loss (mass-flux.flare), recoil
   ;; (dv.flare), the CME parcel (spawn-request.flare) and the XUV boost
   ;; (flare-boost). A fan-out emitter (was a serial barrier).
   {:id     :stellar-flare
    :ns     'domain.stellar
    :reads  #{c/matter-state c/mass c/radius c/position c/velocity
              c/rotation-axis c/accretion-radius c/composition c/b-field}
    :writes #{c/mass-flux-flare c/dv-flare c/spawn-request-flare c/flare-boost}}

   ;; Disk evolution: viscous accretion + gravitational instability →
   ;; planets/binaries. Emits mass-flux.disk + torque.disk influences; the
   ;; integrator owns mass/angmom/spin. Fragment spawns emit
   ;; c/spawn-request-disk (materialized next tick by materialize-lifecycle).
   ;; Reads c/absorb-accrete from sink-formation (one-tick Jacobi delay).
   ;; Runs in the parallel fan-out (was a post-fold barrier).
    {:id     :disk-evolution
     :ns     'domain.stellar
     :reads  #{c/matter-state c/mass c/disk-mass c/disk-angular-mom
               c/radius c/position c/velocity c/absorb-accrete c/luminosity
               c/composition c/planets-seeded c/disc-tag c/rotation-axis}
     :writes #{c/disk-mass c/disk-angular-mom c/mass-flux-disk c/torque-disk
               c/spawn-request-disk c/spawn-request-planet c/planets-seeded}}

   ;; LOD scheduler: assigns observer-centric detail levels to stars/planets.
    ;; Fan-out emitter (was a cargo-cult barrier — already single-writer).
    {:id     :lod-scheduler
     :ns     'domain.genesis
     :reads  #{c/matter-state c/position c/observer}
     :writes #{c/lod-level}}

   ;; Magnetosphere coupling: computes magnetopause standoff from wind ram pressure.
    ;; Fan-out emitter (was a cargo-cult barrier — already single-writer).
    {:id     :magnetosphere-coupling
     :ns     'domain.genesis
     :reads  #{c/matter-state c/position c/radius c/b-field c/ram-pressure c/ionization-fraction c/mass}
     :writes #{c/magnetosphere}}

   ;; :thermal retired — temperature is now owned by the integrator, which reuses
   ;; stellar/temperature-system's virial/radiative derivation and layers the
   ;; heat.intervention ease on top (spec §7.4-7.5).

   ;; Nucleosynthesis emits comp.burn: the burned (H→He) composition for stars and
   ;; ignited protostars (dt-bounded). The integrator owns composition and applies
   ;; the burn then the deuterium gate (spec §7.5).
   {:id     :nucleosynthesis
    :ns     'domain.chemistry
    :reads  #{c/matter-state c/composition c/temperature c/mass}
    :writes #{c/comp-burn}}

    {:id     :regime
     :ns     'domain.regime
     :reads  #{c/matter-state c/density c/temperature c/b-field c/disc-tag}
     :writes #{c/regime}}

    ;; Disc identification: tags non-star bodies relative to the central star as
    ;; :disc, :envelope, :outflow, or nil. Sole writer of c/disc-tag (Part 2).
    {:id     :disc-identification
     :ns     'domain.stellar
     :reads  #{c/matter-state c/position c/velocity c/mass c/oblateness}
     :writes #{c/disc-tag}}

     ;; EM is split: the Lorentz force and magnetic braking are computed together
    ;; in one pass over EM-active entities; the integrator owns angular-momentum/
    ;; spin and adds the torque. Resistive flux decay (b-field) stays on field.
    {:id     :em-lorentz
     :ns     'domain.em
     :reads  #{c/b-field c/radius c/position c/density c/angular-momentum c/matter-state}
     :writes #{c/accel-lorentz c/torque-em}}

    ;; The Field owner: b-field via conserved frozen flux Φ = B·R² (B = Φ/R²
   ;; amplifies as the radius contracts) plus Ohmic decay. Subsumes collapse's
   ;; flux-freezing and em's b-field decay.
   {:id     :field
    :ns     'domain.em
    :reads  #{c/b-field c/radius c/matter-state c/frozen-flux}
    :writes #{c/b-field c/frozen-flux}}

   ;; recenter is no longer a system: the integrator subtracts a one-tick-stale
   ;; COM frame-offset (a world scalar set in tick-world) from every new position
   ;; — a pure Galilean shift, not a post-fold position write (spec §6).
   ])

(defn fan-out-systems
  "Systems that run in the parallel fan-out (everything not marked :barrier).
   With all former barriers converted to fan-out emitters (Part C), this returns
   ALL systems."
  [sys]
  (filterv #(not= :barrier (:stage %)) sys))

(defn all-systems
  "All systems, including barrier systems. Used by write-conflicts to enforce
   the invariant over EVERY system — no exemptions (spec §1)."
  [sys]
  sys)

;; ---------------------------------------------------------------------------
;; Validation
;; ---------------------------------------------------------------------------

(defn writers-by-component
  "Return {component-type [system-id ...]} across the registry — every system
   that writes each component, in registry order."
  [sys]
  (reduce (fn [m {:keys [id writes]}]
            (reduce (fn [m ct] (update m ct (fnil conj []) id))
                    m
                    writes))
          {}
          sys))

(defn write-conflicts
  "Return {component-type [system-id ...]} for every component written by MORE
   THAN ONE system — across ALL systems, including barriers. There are no
   exemptions (spec §1, unified-physical-state-integrator-spec.md). `{}` means
   the invariant holds."
  [sys]
  (into (sorted-map)
        (filter (fn [[_ ids]] (> (count ids) 1)))
        (writers-by-component (all-systems sys))))

(defn malformed-entries
  "Return [{:id .. :problems [..]}] for registry entries that are structurally
   invalid: missing/duplicate :id, missing :reads/:writes, or non-`:component/*`
   keywords in either set."
  [sys]
  (let [ids   (map :id sys)
        dupes (set (for [[id n] (frequencies ids) :when (> n 1)] id))
        component-kw? (fn [k] (and (keyword? k) (= "component" (namespace k))))]
    (->> sys
         (keep (fn [{:keys [id reads writes] :as entry}]
                 (let [problems
                       (cond-> []
                         (nil? id)            (conj "missing :id")
                         (contains? dupes id) (conj (str "duplicate :id " id))
                         (not (set? reads))   (conj "missing/!set :reads")
                         (not (set? writes))  (conj "missing/!set :writes")
                         (and (set? reads)  (not-every? component-kw? reads))
                         (conj (str "non-component reads: "
                                    (remove component-kw? reads)))
                         (and (set? writes) (not-every? component-kw? writes))
                         (conj (str "non-component writes: "
                                    (remove component-kw? writes))))]
                   (when (seq problems)
                     {:id (or id (:ns entry)) :problems problems})))))))

(defn format-conflicts
  "Human-readable single-writer violation report — the to-do list."
  [conflicts]
  (if (empty? conflicts)
    "single-writer invariant holds: every component has exactly one writer."
    (str "single-writer INVARIANT VIOLATED — "
         (count conflicts) " component(s) have multiple writers:\n"
         (str/join "\n"
           (for [[ct ids] conflicts]
             (format "  %-28s written by %d systems: %s"
                     ct (count ids) (str/join ", " ids)))))))

(defn assert-single-writer!
  "Throw if the registry violates single-writer across ALL systems (no barrier
   exemptions). Already wired into `architecture-test` and every boot; the
   migration (spec §7) reduces `write-conflicts` to `{}` incrementally."
  ([] (assert-single-writer! systems))
  ([sys]
   (let [bad (malformed-entries sys)]
     (when (seq bad)
       (throw (ex-info "Malformed system registry entries" {:malformed bad}))))
   (let [conflicts (write-conflicts sys)]
     (when (seq conflicts)
       (throw (ex-info (format-conflicts conflicts) {:conflicts conflicts})))
     sys)))
