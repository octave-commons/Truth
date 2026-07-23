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
    :ns     'domain.stellar.geometry
    :reads  #{c/matter-state c/mass c/radius c/density c/position c/temperature
              c/pressure c/oblateness c/angular-momentum}
    :writes #{c/radius c/density c/oblateness c/rotation-axis}}

    ;; Pressure is a pure equation of state P = ρ k_B T / m_H — every former
   ;; writer recomputed the identical ideal-gas pressure, so one EOS system owns
   ;; it and derives it from density + temperature (spec §4 derivations).
   {:id     :eos
    :ns     'domain.stellar.geometry
    :reads  #{c/density c/temperature}
    :writes #{c/pressure}}

    ;; Merged hydro/EM force system: one neighbor walk computes both the SPH
    ;; pressure-gradient acceleration and the MHD-lite Lorentz acceleration,
    ;; plus the magnetic-braking torque. Eliminates the duplicate gradient
    ;; computation of the separate hydro and em-lorentz systems.
   {:id     :hydro-em
    :ns     'domain.mhd.force
    :reads  #{c/matter-state c/position c/density c/pressure c/mass c/radius
              c/b-field c/velocity c/angular-momentum c/rotation-axis
              c/ionization-fraction c/neighbor-cache}
    :writes #{c/accel-pressure c/accel-lorentz c/torque-em}}

    ;; Neighbor cache: fan-out builder for the SPH kernel + curl/pressure-grad
    ;; values shared by hydro and EM-Lorentz. One-tick-stale Jacobi lag — the
    ;; cache is built from the frozen input world and read by consumers in the
    ;; same tick's parallel fan-out. Replaces the world-key `:genesis/neighbor-cache`
    ;; and the serial `future` pre-phase in `step-physics`.
   {:id     :neighbor-cache
    :ns     'domain.physics.cache.neighbor
    :reads  #{c/matter-state c/position c/mass c/radius c/neighbor-cache}
    :writes #{c/neighbor-cache}}

;; Jeans-collapse was removed from the pipeline; accretion-radius is now
   ;; written by the classifier (sole writer of both matter-state and accretion-radius).

    ;; The classifier is the SOLE writer of matter-state AND accretion-radius:
    ;; the authentic formation state machine (Jeans+mass+ignition) with throttled
    ;; condensation. Subsumes the old classify system, jeans-collapse, and fusion.
   {:id     :classifier
    :ns     'domain.stellar.classifier
    :reads  #{c/matter-state c/mass c/radius c/density c/temperature
              c/pressure c/composition c/promotion-signal c/disc-tag}
    :writes #{c/matter-state c/accretion-radius}}

    ;; M5 handoff Phases 1 + 2 + 3: material + thermal classification, the
    ;; analytic orbit-stability proxy, AND atmosphere retention. SOLE writer
    ;; of material-class, thermal-band, orbit-stable, atmosphere-class, AND
    ;; retained-species — pure composition/mass + two-body equilibrium-
    ;; temperature tags, periapsis/apoapsis/Hill-radius gates, and a coarse
    ;; Jeans-escape-ratio atmosphere verdict for planet-candidate bodies
    ;; (parent kanban/tasks/ecology-water-gate-snowline.md §3.1-3.3, §4).
    ;; Orbit stability is a snapshot proxy, NOT a 10 Myr two-body integration
    ;; (see kanban/tasks/ecology-m5-phase2-orbit-stability.md); atmosphere
    ;; retention is a one-shot formation-time verdict against thermal escape
    ;; only, NOT the ongoing per-tick XUV mass-loss the `:atmosphere-escape`
    ;; system below models (see kanban/tasks/ecology-m5-phase3-atmosphere-
    ;; retention.md and docs/research/atmosphere/planetary-atmosphere-
    ;; retention-classifier.md). All three phases are folded into one system
    ;; because each reuses the same candidate scan and central-star lookup,
    ;; keeping reads minimal and write-conflicts empty.
   {:id     :classification
    :ns     'domain.stellar.classifier
    :reads  #{c/matter-state c/mass c/composition c/temperature c/position
              c/velocity c/radius c/luminosity}
    :writes #{c/material-class c/thermal-band c/orbit-stable
              c/atmosphere-class c/retained-species}}

    ;; M5 handoff Phase 4: the `:planet-candidate` output record + handoff
    ;; gate (parent kanban/tasks/ecology-water-gate-snowline.md §2, §5). SOLE
    ;; writer of `c/planet-candidate`. Reuses the material-class/thermal-band/
    ;; orbit-stable/atmosphere-class/retained-species already written by
    ;; `:classification` above (one Jacobi-lag tick stale, same as every
    ;; other cross-system read in this fan-out) rather than re-deriving them,
    ;; and reads `c/absorb-merge` as the "system not yet settled" proxy — a
    ;; pending, unresolved collision merge in flight. See
    ;; kanban/tasks/ecology-m5-phase4-handoff-event.md.
   {:id     :handoff
    :ns     'domain.stellar.classifier
    :reads  #{c/matter-state c/mass c/composition c/position c/velocity
              c/radius c/luminosity c/material-class c/thermal-band
              c/orbit-stable c/atmosphere-class c/retained-species
              c/angular-momentum c/rotation-axis c/oblateness c/b-field
              c/spin c/absorb-merge}
    :writes #{c/planet-candidate}}

     ;; Seed-and-grow condensation: :nebula → :planetesimal transitions spawn a
     ;; small physical seed instead of promoting the whole parcel. Emits the spawn
     ;; request, the parent parcel's mass-flux-condense debit, and a one-shot
     ;; condensation-seeded marker. The integrator folds the debit.
   {:id     :condensation-seeder
    :ns     'domain.stellar.seeder
    :reads  #{c/matter-state c/mass c/density c/position c/velocity
              c/radius c/composition c/temperature c/condensation-seeded c/disc-tag}
    :writes #{c/spawn-request-condense c/mass-flux-condense c/condensation-seeded}}

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
              c/heat-intervention c/comp-burn c/comp-depletion c/temperature
              c/angular-momentum c/spin c/torque-em c/torque-disk
              c/mass-flux-flare c/mass-flux-xuv c/mass-flux-disk
              c/mass-flux-transfer c/mass-flux-condense
              c/absorb-merge c/absorb-accrete c/wind-heating
              c/lod-tick-phase}
    :writes #{c/position c/velocity c/mass c/temperature c/ionization-fraction c/composition c/comp-condensed
              c/angular-momentum c/spin c/consumed-transfer c/consumed-ablation}}

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
    :ns            'domain.stellar.fusion
    :reads         #{c/matter-state c/temperature c/pressure c/composition
                     c/density c/radius c/mass c/luminosity}
    :writes        #{c/promotion-signal}}

   ;; Sink formation: absorbs nearby gas parcels into sinks. Emits
   ;; absorb.accrete + consumed.accrete; the integrator reads absorb-accrete and
   ;; applies mass/velocity/position/angmom changes; disk-evolution reads it to
   ;; grow disk-mass (one-tick Jacobi delay, spec §5). Runs in the parallel
   ;; fan-out (was a post-fold barrier).
   {:id            :sink-formation
    :ns            'domain.stellar.sink
    :reads         #{c/matter-state c/accretion-radius c/position c/mass
                     c/velocity c/disk-mass c/disk-angular-mom c/luminosity
                     c/temperature c/consumed-accrete}
    :writes        #{c/absorb-accrete c/consumed-accrete}}

   ;; :collapse is fully retired: its writes were dissolved into Structure (shape),
   ;; :thermal (virial temperature), :em (spin), and :field (b-field).

   {:id     :fusion
    :ns     'domain.stellar.fusion
    :reads  #{c/matter-state c/temperature c/pressure c/composition
              c/promotion-signal}
    :writes #{c/luminosity}}

   ;; Panchromatic SED: computes per-band luminosities from T_eff and log g.
   ;; Reads fusion's one-tick-stale luminosity and structure's one-tick-stale
   ;; radius — ordinary Jacobi lag, NOT an ordering requirement.
   {:id     :stellar-sed
    :ns     'domain.stellar.fusion
    :reads  #{c/matter-state c/luminosity c/radius c/mass}
    :writes #{c/sed-bands}}

   ;; Stellar atmosphere shells: 4-layer profile (photosphere → corona).
   ;; Reads one-tick-stale luminosity/radius/b-field — Jacobi lag, no ordering.
   {:id     :atmosphere-shells
    :ns     'domain.stellar.fusion
    :reads  #{c/matter-state c/luminosity c/radius c/mass c/b-field}
    :writes #{c/atmosphere-shells}}

   ;; Deuterium depletion: emits comp.depletion (the keys to zero, just :D) for
   ;; hot bodies (T > 1e6 K). One-way gate. A plain fan-out emitter now; the
   ;; integrator owns composition and applies the gate (spec §7.5).
   {:id     :deuterium-depletion
    :ns     'domain.stellar.fusion
    :reads  #{c/matter-state c/temperature c/composition}
    :writes #{c/comp-depletion}}

   ;; XUV atmospheric escape: planetary mass loss from stellar XUV. A fan-out
   ;; emitter — mass loss → mass-flux.xuv (integrator owns mass), plus the
   ;; diagnostic atmosphere-escape (its own column).
   {:id     :xuv-atmospheric-escape
    :ns     'domain.atmosphere
    :reads  #{c/matter-state c/mass c/radius c/position c/sed-bands c/luminosity}
    :writes #{c/mass-flux-xuv c/atmosphere-escape}}

    ;; Stellar wind: each luminous body carries a radial wind profile (mass-loss
    ;; rate, launch speed, ram pressure, ionization, coronal temperature). The
    ;; profile drives the wind-ablation system, which heats/ionizes/ablates nearby
    ;; gas instead of spawning ballistic parcels. Sole writer of c/wind-profile.
   {:id     :stellar-wind
    :ns     'domain.stellar.wind
    :reads  #{c/matter-state c/mass c/radius c/luminosity
              c/atmosphere-shells c/sed-bands c/b-field}
    :writes #{c/wind-profile}}

    ;; Wind ablation: stellar wind ram pressure heats, ionizes, and ablates nearby
    ;; :nebula parcels. Emits c/wind-heating (temperature delta, ionization rate,
    ;; mass loss) on affected parcels and updates c/wind-mass-lost on the source
    ;; star as a diagnostic ledger. No ballistic parcels are spawned.
   {:id     :wind-ablation
    :ns     'domain.stellar.wind
    :reads  #{c/wind-profile c/matter-state c/position c/velocity c/mass c/radius
              c/density c/temperature c/ionization-fraction c/b-field}
    :writes #{c/wind-heating c/wind-mass-lost}}

    ;; Stellar flares: episodic CMEs. Emits the loss (mass-flux.flare), recoil
    ;; (dv.flare), the CME parcel (spawn-request.flare) and the XUV boost
   ;; (flare-boost). A fan-out emitter (was a serial barrier).
   {:id     :stellar-flare
    :ns     'domain.stellar.wind
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
    :ns     'domain.stellar.disc-evolution
    :reads  #{c/matter-state c/mass c/disk-mass c/disk-angular-mom
              c/radius c/position c/velocity c/absorb-accrete c/luminosity
              c/disk-mass-flux c/disk-l-flux
              c/composition c/planets-seeded c/disc-tag c/rotation-axis
              c/disk-regime c/disk-fragments-spawned}
    :writes #{c/disk-mass c/disk-angular-mom c/mass-flux-disk c/torque-disk
              c/spawn-request-disk c/spawn-request-planet c/planets-seeded
              c/disk-regime c/disk-fragments-spawned}}

   ;; Mass transfer: Bondi–Hoyle–Lyttleton sink accretion and Roche-lobe overflow.
   ;; Sinks are resolved bodies only (not nebula gas — that would be an O(N)
   ;; neighbour-query storm). Emits self-owned c/mass-flux-transfer (signed Δm)
   ;; and c/dv-transfer (Δp/m) influences on donor AND sink, which the integrator
   ;; folds through its generic :mass / :velocity-delta accumulate — no bespoke
   ;; routing. A single system owns c/accretion-rate, c/mass-flux-transfer,
   ;; c/dv-transfer, c/roche-lobe and c/mass-transfer-rate; it merges the internal
   ;; BHL and RLOF write-sets.
   {:id     :mass-transfer
    :ns     'domain.mass-transfer
    :reads  #{c/mass c/position c/velocity c/temperature c/matter-state
              c/accretion-rate c/accretion-radius c/binary-pair c/radius
              c/luminosity}
    :writes #{c/accretion-rate c/mass-flux-transfer c/dv-transfer
              c/disk-mass-flux c/disk-l-flux c/roche-lobe c/mass-transfer-rate}}

   ;; LOD scheduler: assigns observer-centric detail levels to stars/planets.
    ;; Fan-out emitter (was a cargo-cult barrier — already single-writer).
   {:id     :lod-scheduler
    :ns     'domain.lod
    :reads  #{c/matter-state c/position c/observer}
    :writes #{c/lod-level c/lod-tick-phase}}

   ;; Magnetosphere coupling: computes magnetopause standoff from wind ram pressure.
    ;; Fan-out emitter (was a cargo-cult barrier — already single-writer).
   {:id     :magnetosphere-coupling
    :ns     'domain.em.magnetosphere
    :reads  #{c/matter-state c/position c/radius c/b-field c/ram-pressure c/ionization-fraction c/mass}
    :writes #{c/magnetosphere}}

   ;; :thermal retired — temperature is now owned by the integrator, which reuses
   ;; domain.stellar.temperature/temperature-system's virial/radiative derivation and layers the
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
    :ns     'domain.stellar.disc
    :reads  #{c/matter-state c/position c/velocity c/mass c/oblateness}
    :writes #{c/disc-tag}}

    ;; The Field owner: b-field via conserved frozen flux Φ = B·R² (B = Φ/R²
    ;; amplifies as the radius contracts) plus Ohmic decay. Subsumes collapse's
    ;; flux-freezing and em's b-field decay.
   {:id     :field
    :ns     'domain.em.field
    :reads  #{c/b-field c/radius c/matter-state c/frozen-flux}
    :writes #{c/b-field c/frozen-flux}}

   ;; Small-body sink: marks unbound :planetesimal/:gas-giant/:brown-dwarf past
   ;; the system edge for reaping (consumed at world-construction). Sole writer
   ;; of consumed.escape.
   {:id     :debris-reaper
    :ns     'domain.debris
    :reads  #{c/matter-state c/position c/velocity c/mass}
    :writes #{c/consumed-escape}}

   ;; Player Focus dual-representation: promotes overlapping regional cells
   ;; into resolved clumps, and demotes previously-promoted clumps that have
   ;; left the immediate focus radius back into their source cell. One system
   ;; because both directions write c/statistical-mass. c/field-zone is set
   ;; only at spawn time (via the spawn spec's :extra-components, like
   ;; c/matter-state/c/body-kind on every other spawn-request.* type) rather
   ;; than through this system's per-tick write-set, but is declared here as
   ;; the sole owner since no other system ever writes it.
   {:id     :focus-zone
    :ns     'domain.genesis.promotion
    :reads  #{c/observer c/position c/field-zone c/statistical-mass
              c/matter-state c/mass c/velocity c/angular-momentum
              c/promoted-from-cell c/radius c/temperature c/composition c/b-field}
    :writes #{c/field-zone c/statistical-mass c/spawn-request-promotion c/consumed-demote}}

   ;; The First Narrowing, child A: gravitational binding. Reads the observer's
   ;; focus/attention state and every candidate world's position, and writes the
   ;; observer's {world-eid -> [0,1]} coupling plus its permanent sunk-cost
   ;; scar tally. Reads its own prior output one tick stale (ordinary Jacobi
   ;; lag, like :neighbor-cache). Binding is exposed as data the :focus-zone
   ;; promotion/demotion machinery could read later; it does not rewire it.
    {:id     :binding
     :ns     'domain.narrowing
     :reads  #{c/observer c/position c/planet-candidate c/binding c/binding-scar}
     :writes #{c/binding c/binding-scar}}

    ;; The First Narrowing, child B: the commitment horizon. Reads the
    ;; observer's one-tick-stale c/binding (Jacobi output of :binding) and the
    ;; M5 planet-candidate records; on capture writes the write-once commitment
    ;; marker (:committed on the captured world, :inert on unchosen
    ;; candidates), the re-armed Phase 1 planetary palette on the observer, and
    ;; the planetary time-lock record on the committed world. Also reads its
    ;; own prior output (idempotency) and the `:arc/current` world key for the
    ;; readiness gate — world keys are not declarable here. The canonical
    ;; :event/world-commitment ledger event is appended serially post-fold by
    ;; domain.genesis.tick/emit-commitment-event, reacting to the
    ;; c/commitment-state marker (the emit-handoff-event precedent).
    {:id     :commitment
     :ns     'domain.narrowing
     :reads  #{c/observer c/binding c/planet-candidate c/commitment-state
               c/palette c/time-lock}
     :writes #{c/commitment-state c/palette c/time-lock}}

    ;; Voxel 3: the focus-driven voxel band on the committed world. Reads
    ;; the observer's focus, the committed world's candidate record and
    ;; position (one tick Jacobi-stale, like every cross-system read), and
    ;; its own four columns one tick stale. Sole writer of all four: the
    ;; field seed cache, the resolved band, the deferred edit queue, and
    ;; the accumulated edit-diff save representation. Band retargets and
    ;; demotion fold-back drain through the budgeted queue
    ;; (law.voxel/edit-budget-ms-per-tick) — one system because promotion
    ;; and demotion write the same columns (the :focus-zone precedent).
    ;; Voxel 4: also reads `c/voxel-sculpt-request` (one tick stale, the
    ;; producer-suffixed request channel) and folds the paid sculpt ops
    ;; into the field it owns + the queue it owns.
    ;; Voxel 5: also reads `c/voxel-carve-request` (one tick stale, the
    ;; collision-carve request channel) and folds its plans + melt/vapor
    ;; cooling into `:apply-edits` jobs, provenance `:collision`.
    {:id     :voxel-focus
     :ns     'domain.voxel.focus
     :reads  #{c/observer c/position c/planet-candidate c/commitment-state
               c/voxel-field c/voxel-band c/voxel-edit-queue
               c/voxel-edit-diffs c/voxel-sculpt-request
               c/voxel-carve-request}
     :writes #{c/voxel-field c/voxel-band c/voxel-edit-queue
               c/voxel-edit-diffs}}

    ;; Voxel 4: god-scale sculpting (design planetary-voxel-substrate.md
    ;; §5 tier 1). Translates the paid ops on the `:voxel/sculpt-ops`
    ;; world key (the `:genesis/interventions` precedent — world keys are
    ;; not declarable here) into the producer-suffixed request component
    ;; the `:voxel-focus` fold consumes one Jacobi tick later. Sole writer
    ;; of `c/voxel-sculpt-request`; reads its own prior output one tick
    ;; stale to auto-clear drained requests.
    {:id     :voxel-sculpt
     :ns     'domain.voxel.sculpt
     :reads  #{c/commitment-state c/voxel-sculpt-request}
     :writes #{c/voxel-sculpt-request}}

    ;; Voxel 5: collision shock → voxel carving (design
    ;; planetary-voxel-substrate.md §6). Classifies absorb-merge packets
    ;; on the committed world (the durable collision record — the ledger
    ;; event is diffed away at the write-set boundary) through the
    ;; `law.crater` scaling laws into carve plans / disruption reports.
    ;; Sole writer of `c/voxel-carve-request`; reads its own prior output
    ;; one tick stale for the `:seen` idempotency set (the absorb-merge
    ;; channel is sticky — collision-detection never clears it).
    {:id     :voxel-carve
     :ns     'domain.voxel.carve
     :reads  #{c/commitment-state c/planet-candidate c/voxel-field
               c/voxel-band c/absorb-merge c/position c/velocity
               c/voxel-carve-request}
     :writes #{c/voxel-carve-request}}

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

(defn registry-writes
  "Return the declared :writes set for a system :id from the registry.
   Sourcing the emitter's :writes from the registry keeps the emitter and the
   single-writer declaration from drifting."
  [id]
  (some #(when (= id (:id %)) (:writes %)) systems))

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
