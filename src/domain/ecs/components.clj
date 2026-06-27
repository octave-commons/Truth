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
(def composition  :component/composition)   ;; {:H 0.75 :He 0.24 ...} mass fractions
(def luminosity   :component/luminosity)    ;; watts (0 until fusion)
(def matter-state :component/matter-state)  ;; :nebula :protostar :star :planet :debris

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
(def angular-momentum :component/angular-momentum) ;; [Lx Ly Lz]
(def spin             :component/spin)             ;; [ωx ωy ωz]
(def oblateness       :component/oblateness)       ;; double in (0,1]
(def rotation-axis    :component/rotation-axis)    ;; unit [nx ny nz]

;; --- Hydrodynamics ----------------------------------------------------------
;; `hydro-accel` is the pressure-gradient acceleration a = -∇p/ρ (m/s²).
;; Computed by `domain.hydro` and consumed by `domain.orbital.system`.
;; LEGACY accumulator: in the sequential pipeline hydro writes it, em adds the
;; Lorentz force into it, and the orbital integrator reads it. The double-buffer
;; model decomposes it into the single-writer `accel/*` contributions below
;; (see the double-buffer spec §4); `hydro-accel` is retired once hydro and em
;; emit their own contributions.
(def hydro-accel :component/hydro-accel) ;; [ax ay az]

;; --- Acceleration contributions (double-buffer accumulator inputs) ----------
;; Each force-emitter owns ONE of these; the motion integrator reads them all
;; and SUMS them into the net acceleration before advancing velocity/position.
;; Summation is commutative, so the fan-out order is irrelevant. See the
;; double-buffer spec §3–§4.
(def accel-gravity  :component/accel.gravity)  ;; [ax ay az] Barnes–Hut self-gravity
(def accel-pressure :component/accel.pressure) ;; [ax ay az] SPH pressure gradient (hydro)
(def accel-lorentz  :component/accel.lorentz)  ;; [ax ay az] Lorentz / magnetic (em)

;; --- Observer (the player spark) --------------------------------------------
;; The quantum-oscillation player is a singleton entity carrying this component.
;; Holds coherence, focus volume, and witnessed-event memory — see domain.player.
(def observer     :component/observer)

;; --- Atmosphere -------------------------------------------------------------
(def atmos-cell  :component/atmos-cell)

;; --- Biome ------------------------------------------------------------------
(def biome-cell  :component/biome-cell)

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
