(ns domain.ecs.components
  "Canonical component type keywords for Gates of Truth.
   No logic here — just the vocabulary.
   Every system queries these exact keywords.")

;; --- Spatial ----------------------------------------------------------------
(def position  :component/position)
(def velocity  :component/velocity)
(def mass      :component/mass)
(def radius    :component/radius)
;; `accretion-radius` is the gravitational feeding-zone radius of a star-forming
;; body. It is set when a clump becomes a protostar and, unlike `radius`, does
;; NOT shrink as the photosphere contracts — so a star keeps sweeping up nearby
;; gas instead of becoming a pinpoint the cloud streams through. nil for gas.
(def accretion-radius :component/accretion-radius)

;; --- Orbital ----------------------------------------------------------------
(def elements  :component/elements)
(def orbit-ref :component/orbit-ref)

;; --- Physical ---------------------------------------------------------------
(def force-accum :component/force-accum)
(def body-kind   :component/body-kind)

;; --- Stellar / matter state -------------------------------------------------
;; Thermodynamic + chemical state carried by resolved matter, from nebular gas
;; through protostar, star, and planet. The same components describe a clump of
;; gas and a finished world — only the magnitudes change.
(def temperature  :component/temperature)  ;; kelvin
(def density      :component/density)       ;; kg/m^3
(def pressure     :component/pressure)      ;; pascal
(def composition  :component/composition)   ;; {:H 0.7346 :He 0.2485 ...} mass fractions
(def comp-condensed :component/comp.condensed) ;; {:solid element-map :gas element-map}
(def luminosity   :component/luminosity)    ;; watts (0 until fusion)
(def matter-state :component/matter-state)  ;; :nebula :condensed-core :planetesimal :gas-giant :brown-dwarf :planet :protostar :star :stellar-remnant

;; --- Planet classification (M5 handoff Phase 1) -----------------------------
;; First structured "planet candidate" tags: composition/mass and two-body
;; equilibrium temperature only — no orbit integration, no atmosphere physics.
;; See kanban/tasks/ecology-m5-phase1-planet-classification.md.
(def material-class :component/material-class) ;; :rocky :icy :gaseous :mixed
(def thermal-band   :component/thermal-band)    ;; :frozen :cold :temperate :warm :hot

;; --- Orbit stability (M5 handoff Phase 2) -----------------------------------
;; Analytic stability proxy (periapsis/apoapsis/Hill-radius gates), NOT a
;; 10 Myr two-body integration. See
;; kanban/tasks/ecology-m5-phase2-orbit-stability.md.
(def orbit-stable :component/orbit-stable?) ;; bool — analytic stability proxy

;; --- Atmosphere retention (M5 handoff Phase 3) ------------------------------
;; Coarse Jeans-escape-ratio classifier: how much atmosphere (if any) a
;; candidate body retains, and which volatile species. Downstream of
;; material-class/thermal-band (Phase 1). See
;; kanban/tasks/ecology-m5-phase3-atmosphere-retention.md and
;; docs/research/atmosphere/planetary-atmosphere-retention-classifier.md.
(def atmosphere-class :component/atmosphere-class) ;; :none :thin :substantial :thick
(def retained-species :component/retained-species) ;; #{:H2 :He :H2O :N2 :CO2}

;; --- Field / MHD ------------------------------------------------------------
;; The electromagnetic layer. `b-field` is the magnetic field vector (tesla, SI)
;; frozen into a clump; `regime` is the dominant-physics tag the classifier
;; writes each tick (:gravity-hydro :mhd-dominated :gravitationally-unstable ...).
(def b-field      :component/b-field)       ;; [bx by bz] tesla
(def regime       :component/regime)        ;; keyword, see domain.regime/classify
;; `frozen-flux` is the magnetic flux Φ = B·R² (tesla·m², vector) frozen into a
;; condensed body. It is conserved as the body contracts — so B = Φ/R² amplifies
;; as the radius shrinks (flux freezing) — and decays only by Ohmic resistivity.
;; Owned by the Field system (domain.em); the reference that makes B a derivation.
(def frozen-flux  :component/frozen-flux)   ;; [Φx Φy Φz] tesla·m²

;; --- Rotational / disc geometry ---------------------------------------------
;; `angular-momentum` is the total orbital+spin L of the clump (kg m²/s).
;; `spin` is the body-fixed angular velocity vector (rad/s).
;; `oblateness` is the polar/equatorial axis ratio c/a (1 = spherical).
;; `rotation-axis` is the unit vector along L; used to orient the flattened body.
;; `disc-tag` classifies a body's kinematic relationship to the central star:
;;   :disc      — rotationally supported (v_tang > 2 |v_rad|, h/r < 0.3, bound)
;;   :envelope  — radially infalling, bound
;;   :outflow   — unbound / hyperbolic
;;   nil       — not associated with a disc
(def angular-momentum :component/angular-momentum) ;; [Lx Ly Lz]
(def spin             :component/spin)             ;; [ωx ωy ωz]
(def oblateness       :component/oblateness)       ;; double in (0,1]
(def rotation-axis    :component/rotation-axis)    ;; unit [nx ny nz]
(def disc-tag         :component/disc-tag)         ;; :disc | :envelope | :outflow | nil

;; --- Hydrodynamics ----------------------------------------------------------
;; `hydro-accel` is the pressure-gradient acceleration a = -∇p/ρ (m/s²).
;; Computed by `domain.hydro` and consumed by `domain.orbital.system`.
;; LEGACY accumulator: in the sequential pipeline hydro writes it, em adds the
;; Lorentz force into it, and the orbital integrator reads it. The double-buffer
;; model decomposes it into the single-writer `accel/*` contributions below
;; (see the double-buffer spec §4); `hydro-accel` is retired once hydro and em
;; emit their own contributions.
(def hydro-accel :component/hydro-accel) ;; [ax ay az]

;; --- Neighbor cache (shared by hydro + EM) ----------------------------------
;; One `law.field/neighbor-cache-entry-schema` value per hydro/EM-active entity.
;; Built/refreshed by the `:neighbor-cache` fan-out system; hydro and em-lorentz
;; read the one-tick-stale snapshot entry. Moving the cache from a world key to a
;; component eliminates the last serial pre-phase in `step-physics`.
(def neighbor-cache :component/neighbor-cache)

;; --- Acceleration contributions (double-buffer accumulator inputs) ----------
;; Each force-emitter owns ONE of these; the motion integrator reads them all
;; and SUMS them into the net acceleration before advancing velocity/position.
;; Summation is commutative, so the fan-out order is irrelevant. See the
;; double-buffer spec §3–§4.
(def accel-gravity  :component/accel.gravity)  ;; [ax ay az] Barnes–Hut self-gravity
(def accel-pressure :component/accel.pressure) ;; [ax ay az] SPH pressure gradient (hydro)
(def accel-lorentz  :component/accel.lorentz)  ;; [ax ay az] Lorentz / magnetic (em)
(def accel-observer :component/accel.observer) ;; [ax ay az] observer pull-toward-focus (player)
;; `accel.warp` is the player's PAID warp-space intervention: a placed, decaying
;; gravity well or repulsor that bends nearby bodies (domain.intervention). A
;; distinct single-writer channel from accel.observer — observation is the free
;; gentle nudge, warp is the spent, stronger, transient force. Motion sums it.
(def accel-warp     :component/accel.warp)     ;; [ax ay az] player warp-space force

;; --- Influence contributions (unified-integrator inputs) --------------------
;; The single integrator (domain.ecs.integrator) is the sole writer of physical
;; state; every other system is a pure emitter that writes ONE influence
;; component the integrator reads and composes (spec
;; docs/notes/specs/2026.06.29-unified-physical-state-integrator-spec.md §3-4).
;; Accel contributions (accel.*) are declared above with the legacy accumulators.
;;
;; Torque contributions → angular-momentum (summed, ×dt by the integrator):
(def torque-em   :component/torque.em)   ;; [Lx Ly Lz] magnetic-braking torque (em)
(def torque-disk :component/torque.disk) ;; [Lx Ly Lz] disk→star spin-up torque (disk-evolution)
;; Heat contribution → temperature (player thermal interventions, eased per tick):
(def heat-intervention :component/heat.intervention) ;; target-temp ease payload {:target :ease}
;; Composition contributions → composition (integrator owns the blend):
(def comp-burn      :component/comp.burn)      ;; replacement composition after H→He burn (nucleosynthesis)
(def comp-depletion :component/comp.depletion) ;; #{element-keys} to zero (deuterium-depletion)
;; Mass-flux contributions → mass (summed Δm; one per source, all single-writer):
(def mass-flux-flare :component/mass-flux.flare) ;; kg Δm from flare ejection (negative)
(def mass-flux-xuv  :component/mass-flux.xuv)  ;; kg Δm from XUV atmospheric escape (negative)
(def mass-flux-disk :component/mass-flux.disk) ;; kg Δm from disk→star viscous transfer (positive)
(def mass-flux-transfer :component/mass-flux.transfer) ;; kg Δm from gradual mass transfer (BHL sink accretion + Roche-lobe overflow); signed, written on both donor (−) and sink/accretor (+)
;; Absorb contributions → full N→1 merge/accretion blend on the survivor. Each is
;; a vector of absorbed-body state maps the integrator folds into the survivor's
;; physical fields (conservative blend); the absorbed bodies carry a consumed
;; marker reaped at world-construction (spec §5).
(def absorb-merge   :component/absorb.merge)   ;; [{:mass :velocity :position :composition :temperature :angular-momentum :radius :accretion-radius} ...] (collision)
(def absorb-accrete :component/absorb.accrete) ;; [{:mass :velocity :position :angular-momentum} ...] (sink gas accretion)
;; Velocity-delta contributions → velocity (a per-tick Δv, applied after accel;
;; recoil from ejecting a flare parcel, momentum-conserving). One per source.
(def dv-flare :component/dv.flare) ;; [dvx dvy dvz] flare ejection recoil
(def dv-transfer :component/dv.transfer) ;; [dvx dvy dvz] recoil/gain from gradual mass transfer (Δp/m, momentum-conserving); written on both donor and sink/accretor
;; Frame-offset → position (recenter as a one-tick-stale COM Galilean shift):
(def frame-offset :component/frame-offset)     ;; [dx dy dz] subtracted from every position
;; Disk-feed contributions (sink accretion → the disk owner). The accretion
;; system reads these and folds them into disk-mass / disk-angular-mom.
(def disk-mass-flux :component/disk-mass-flux)  ;; kg accreted to the disk this tick (sink)
(def disk-l-flux    :component/disk-l-flux)     ;; [Lx Ly Lz] accreted disk L this tick (sink)
;; Spawn requests, materialized at world-construction (spec §5). Each value is a
;; vector of seed-spec maps (as `domain.stellar.seeder/spawn-clump` expects); an optional
;; :extra-components map on a spec is applied to the new entity after spawning.
;; One request component per spawning source so single-writer holds.
(def spawn-request-flare     :component/spawn-request.flare)
(def spawn-request-accretion :component/spawn-request.accretion)
(def spawn-request-shatter   :component/spawn-request.shatter)
(def spawn-request-disk      :component/spawn-request.disk)   ;; disk fragment spawns (binary/planet)
(def spawn-request-planet    :component/spawn-request.planet) ;; sub-grid planet seeder (Part 4)
(def spawn-request-condense  :component/spawn-request.condense) ;; small-body seed from gas condensation
;; Lifecycle markers, reaped/materialized at world-construction (spec §5). Each
;; consumed marker has a single owner so single-writer holds; the reaper removes
;; any entity carrying ANY consumed.* marker.
(def consumed-merge :component/consumed.merge)  ;; absorbed body, reaped (collision)
(def consumed-accrete :component/consumed.accrete) ;; absorbed gas parcel, reaped (sink)
(def consumed-escape :component/consumed.escape) ;; unbound debris past the system edge, reaped (debris-reaper)
(def consumed-transfer :component/consumed.transfer) ;; donor drained below floor by gradual mass transfer, reaped (integrator)
(def consumed-ablation :component/consumed.ablation) ;; bound body ablated below mass floor, reaped (integrator)

;; Condensation seeding: one-shot marker per gas parcel so it does not seed
;; repeatedly, and the dedicated mass-flux influence the integrator folds.
(def condensation-seeded :component/condensation.seeded)
(def mass-flux-condense  :component/mass-flux.condense)  ;; kg debited from parent parcel at seeding

;; Fusion promotion signal: post-fold barrier emits this; classifier and
;; fusion-system read it on the next tick's snapshot (spec §7). Single writer.
(def promotion-signal :component/promotion.signal)

;; --- Observer (the player spark) --------------------------------------------
;; The quantum-oscillation player is a singleton entity carrying this component.
;; Holds coherence, focus volume, and witnessed-event memory — see domain.player.
(def observer     :component/observer)

;; --- Dual-representation / focus zones (Phase 1) ----------------------------
;; `field-zone` tags an entity as :immediate, :regional, or :global.
;; `statistical-mass` is the mass budgeted in a regional/global cell.
;; `attention-shell` is the observer's immediate/regional focus radii.
(def field-zone       :component/field-zone)        ;; :immediate | :regional | :global
(def statistical-mass :component/statistical-mass) ;; kg, bookkeeping during promotion
(def attention-shell  :component/attention-shell)    ;; {:immediate-r m :regional-r m}

;; --- Narrative presence (Phase 1) -------------------------------------------
;; Observer-side narrative state: current mood, last utterance tick, and the
;; set of topics already touched. Written only by domain.narrative.
(def narrative-state :component/narrative-state)

;; --- Stellar SED / atmosphere (Phase 1) -------------------------------------
;; Panchromatic spectral energy distribution and layered stellar atmospheres.
;; Derived from: docs/research/phase1-radiation-plasma-truth.md §2-3
(def sed-bands          :component/sed-bands)          ;; {:gamma W :xray W :euv W ...} per-band luminosity
(def atmosphere-shells  :component/atmosphere-shells)  ;; [{:layer/id :temperature :electron-density ...} ...]
(def wind-profile       :component/wind-profile)       ;; {:wind/dot-m :wind/v-escape :wind/ram-pressure :wind/reference-r :wind/luminosity-xuv :wind/ionization :wind/corona-t}
(def atmosphere-escape  :component/atmosphere-escape)   ;; {:regime :xuv-flux :mass-loss-rate}
(def event-source       :component/event-source)       ;; {:kind :payload} — flare/CME event
(def lod-level          :component/lod-level)           ;; :galaxy :system :local — observer-centric fidelity
(def lod-tick-phase   :component/lod-tick-phase)     ;; {:level :local|:system|:galaxy :period 1|2|4 :phase tick}
(def ionization-fraction :component/ionization-fraction) ;; 0..1 — plasma ionization state
(def ram-pressure       :component/ram-pressure)        ;; Pascals — wind/impact ram pressure
(def flare-boost        :component/flare-boost)         ;; {:factor :decay-tick} — transient XUV enhancement
(def magnetosphere      :component/magnetosphere)       ;; {:compression :standoff-distance} — planetary magnetosphere state
(def disk-mass          :component/disk-mass)            ;; kg — protoplanetary disk mass
(def disk-angular-mom   :component/disk-angular-mom)    ;; [Lx Ly Lz] — disk angular momentum vector
(def disk-regime        :component/disk.regime)         ;; {:toomre-q :cooling-beta :regime :solid-surface-density :snow-line}
(def disk-fragments-spawned :component/disk-fragments-spawned) ;; int — direct GI fragments already spawned
(def planets-seeded     :component/planets-seeded)      ;; bool — a star whose disk has run the one-shot planet sub-grid
(def planet-type        :component/planet-type)         ;; :terrestrial | :ice-giant | :gas-giant — sub-grid planet class

;; --- Mass transfer (gradual accretion) --------------------------------------
;; Rate-limited, partial debit/credit for sink accretion and Roche-lobe overflow.
;; See kanban/tasks/gradual-mass-transfer-spec.md.
(def accretion-rate     :component/accretion-rate)     ;; {:sink/dot-m :sink/dot-m-this-tick :sink/efficiency :sink/regime}
(def sink-identity      :component/sink-identity)      ;; {:sink/softening-length :sink/created-at-tick}
(def binary-pair        :component/binary-pair)        ;; {:binary-pair/donor :binary-pair/accretor :orbit/semi-major-axis :orbit/eccentricity}
(def roche-lobe         :component/roche-lobe)         ;; {:roche-lobe/radius :overfilling :overflow?}
(def mass-transfer-rate :component/mass-transfer-rate) ;; {:mass-transfer/rate :mass-transfer/accreted-fraction}
(def wind-heating       :component/wind-heating)       ;; {:wind-heating/delta-t :ionization-rate :mass-loss :source-eid}
(def wind-mass-lost     :component/wind-mass-lost)     ;; kg accumulated ablated mass (ledger)

;; --- Biome / Ecology --------------------------------------------------------
(def biome-cell  :component/biome-cell)
(def ecology     :component/ecology)      ;; {:moisture :temp :biomass :complexity :stability :phase :seeded :record}

;; --- Civilization -----------------------------------------------------------
(def civilization :component/civilization)
(def territory    :component/territory)

;; --- Render -----------------------------------------------------------------
(def renderable   :component/renderable)
(def cell-id      :component/cell-id)

;; --- Myth engine ------------------------------------------------------------
(def facet-vector :component/facet-vector)
(def favor        :component/favor)
(def scribe       :component/scribe)
