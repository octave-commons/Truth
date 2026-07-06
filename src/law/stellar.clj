(ns law.stellar
  "Contracts and schemas for stellar nebula, star formation, and planetary bodies.
   Defines the physical laws governing Phase 0 of Gates of Truth."
  (:require
   [law.contract :as contract]))

;; Physical constants
(def ^:const G 6.674e-11) ;; Gravitational constant m³/kg·s²
(def ^:const k-B 1.380649e-23) ;; Boltzmann constant J/K
(def ^:const m-H 1.6735e-27) ;; Hydrogen mass kg
(def ^:const stefan-boltzmann 5.670374419e-8) ;; Stefan-Boltzmann constant W/m²·K⁴
(def ^:const fusion-temp-threshold 1e7) ;; Fusion ignition temperature K (hydrogen burning)
(def ^:const fusion-pressure-threshold 1e12) ;; Fusion ignition pressure Pa (stellar-core scale)
(def ^:const rounding-mass-threshold 3e20) ;; kg — above this self-gravity pulls a body into hydrostatic roundness
(def ^:const solar-mass 1.989e30) ;; kg
(def ^:const solar-radius 6.957e8) ;; m
(def ^:const solar-luminosity 3.828e26) ;; W
(def ^:const earth-mass 5.972e24) ;; kg
(def ^:const jupiter-mass 1.898e27) ;; kg
(def ^:const au 1.495978707e11) ;; m — astronomical unit

;; --- Real stellar/sub-stellar mass boundaries (authentic formation fate) -----
;; The physical thresholds that decide a contracting core's destiny. These are
;; NOT toy tiers — they are the actual opacity limit, deuterium-burning limit,
;; brown-dwarf desert, and hydrogen-burning minimum mass.
;;
;; Source: docs/research/physics/stellar-nebula-mass-hierarchy.md

;; Opacity-limited minimum mass for direct turbulent fragmentation of a
;; molecular cloud. Bodies below this cannot condense directly from the gas and
;; must form by solid growth in a disk. ~3 M_Jupiter.
(def ^:const opacity-limit-mass      (* 0.003 solar-mass))

;; Deuterium-burning limit: the conventional planet / brown-dwarf boundary.
;; ~13 M_Jupiter.
(def ^:const deuterium-burning-mass  (* 0.013 solar-mass))

;; Brown-dwarf desert: a population dip in the substellar companion mass
;; function near ~30 M_Jupiter (Cui et al. 2026). Useful as a secondary
;; classifier threshold between gas-giant embryos and brown dwarfs.
(def ^:const brown-dwarf-desert-mass (* 30.0 jupiter-mass))

;; Hydrogen-burning minimum mass: the brown-dwarf / star boundary. ~80 M_Jupiter.
(def ^:const hydrogen-burning-mass   (* 0.08 solar-mass))

(defn substellar-mass-class
  "Classify a resolved, non-nebula body below the hydrogen-burning limit into a
   literature-grounded mass ladder.

     :planetesimal  < opacity limit          (< ~3 M_J)   proxy for unresolved solids
     :gas-giant     opacity limit to desert   (~3–30 M_J) giant-planet / super-Jovian
     :brown-dwarf   desert to H-burning       (~30–80 M_J)
     :protostar     ≥ hydrogen-burning mass, pre-ignition

   See docs/research/physics/stellar-nebula-mass-hierarchy.md."
  [mass]
  (let [m (double (or mass 0.0))]
    (cond
      (>= m hydrogen-burning-mass)              :protostar
      (>= m brown-dwarf-desert-mass)            :brown-dwarf
      (>= m opacity-limit-mass)                 :gas-giant
      :else                                     :planetesimal)))

(defn ideal-gas-pressure
  "Pressure of a gas region from the ideal gas law: P = ρ k_B T / m_H."
  [density temperature]
  (/ (* density k-B temperature) m-H))

(defn softened-circular-speed
  "Circular-orbit speed (m/s) around mass `M` at radius `r` in the Plummer-
   softened gravity the integrator actually applies:

       v_c² = G M r² / (r² + ε²)^{3/2}

   Reduces to Kepler √(GM/r) for r ≫ ε and to the harmonic-core speed Ω·r
   (Ω = √(GM/ε³)) for r ≪ ε — a circular orbit is exact in BOTH regimes, so a
   body launched with this speed is bound and orbits at ANY radius. The
   unsoftened √(GM/r), by contrast, overshoots the softened field's grip by
   ~(ε/r)^{3/2} inside the softening length: a fragment placed at r ≪ ε with
   Keplerian speed feels almost no pull and leaves the system ballistically."
  [M r softening]
  (let [M (double (or M 0.0))
        r (double (or r 0.0))
        e (double (or softening 0.0))
        d2 (+ (* r r) (* e e))]
    (if (and (pos? M) (pos? r) (pos? d2))
      (Math/sqrt (/ (* G M r r) (Math/pow d2 1.5)))
      0.0)))

(defn virial-speed
  "Characteristic gravitational speed √(G·M/R) (m/s) of a self-gravitating cloud
   of mass `M` and radius `R` — the velocity scale that balances self-gravity.

   The natural yardstick for any external influence on the cloud: velocity
   kicks well below it shepherd matter, kicks well above it unbind matter
   (escape speed from the edge is only √2 × this). 0 for a degenerate scale."
  [M R]
  (let [M (double (or M 0.0))
        R (double (or R 0.0))]
    (if (and (pos? M) (pos? R))
      (Math/sqrt (/ (* G M) R))
      0.0)))

(defn plummer-acceleration
  "Gravitational acceleration magnitude (m/s²) at distance `r` from the centre
   of a Plummer sphere of mass `M` and scale radius `a`:

       g(r) = G·M·r / (r² + a²)^{3/2}

   The field of a LARGE, DIFFUSE body of mass — a dark-matter-halo-like
   presence: zero at the centre (the enclosed mass vanishes), peak pull
   2·G·M/(3√3·a²) at r = a/√2, Keplerian G·M/r² far outside. It is the same
   softened field family `softened-circular-speed` orbits (v_c²/r = g).

   Because the field is conservative, a STATIC halo can only deepen the local
   potential well — it binds and gathers matter and can never pump a body past
   escape speed. Only moving or re-concentrating the halo does work on the
   system. `M` must be the mass MAGNITUDE (≥ 0); callers flip the direction for
   repulsive fields."
  [M a r]
  (let [M  (double (or M 0.0))
        a  (double (or a 0.0))
        r  (double (or r 0.0))
        d2 (+ (* r r) (* a a))]
    (if (and (pos? M) (pos? r) (pos? d2))
      (/ (* G M r) (Math/pow d2 1.5))
      0.0)))

(defn main-sequence-radius
  "Approximate zero-age main-sequence radius (m) for a star of `mass`, from the
   broken power law R/R_sun ≈ (M/M_sun)^0.8 below a solar mass and ^0.57 above.
   This is the FLOOR a contracting protostar settles to: a star is small and
   dense, but it is NOT a point. Without a floor the toy collapse halves the
   radius every tick to ~1e9 m, which collapses the accretion cross-section and
   produces the jarring 'pinpoint star' the cloud streams straight through."
  [mass]
  (let [m (/ (double (or mass solar-mass)) solar-mass)
        m (max m 1e-3)]
    (* solar-radius (Math/pow m (if (< m 1.0) 0.8 0.57)))))

;; --- Accretion mass hierarchy (Phase 0 emergent formation) ---
;; A clump's matter-state follows the mass it has accreted from the gas cloud.
;; These are the toy-scale boundaries between diffuse gas, a planetesimal/debris
;; clump, a planet-scale body, and a star-forming core. They are RELATIVE tiers
;; for a few-solar-mass cloud, not literal Earth/Sun masses.
;;
;; `debris-mass-threshold` is a fallback default. In practice Phase 0 overrides
;; it with the actual fixed gas-particle mass, because any clump larger than one
;; gas sample is already a resolved body.
(def ^:const debris-mass-threshold 1.2e28) ;; kg — gas → planetesimal/debris
(def ^:const planet-mass-threshold 6e28)   ;; kg — debris → planet-scale
(def ^:const star-mass-threshold   1.0e30) ;; kg — planet → star-forming core (dominant)

(defn mass-class
  "UNWIRED / HISTORICAL (Genesis Formation spec Part 7.1). Classify an accreted
   clump's matter-state purely from its mass, including a `:planet` tier.

   This mass-tier path — 'promote a gas parcel to :planet when it is heavy
   enough' — is the 'lie dressed as emergence' the authoritative formation
   physics forbids: planets are SUB-GRID and are seeded by a core-accretion
   prescription on the disk's solid surface density (domain.planet-formation),
   never by a mass threshold on a gas parcel. Its only callers,
   `domain.stellar/classify-system` and `jeans-collapse-system`, are NOT in the
   production pipeline (`genesis/physics-systems-parallel`); the live path is
   `classify-next-state` (density + Jeans + fusion gates, no :planet tier) plus
   the Part 4 seeder. Kept only for the historical tests that pin its behaviour;
   do not wire into new formation code.

   `gas-particle-mass` is the fixed mass of one equal-mass nebula sample. Any
   clump heavier than that is resolved debris (or larger), because it is no
   longer a single gas sample. If omitted, `debris-mass-threshold` is used."
  ([mass] (mass-class mass debris-mass-threshold))
  ([mass gas-particle-mass]
   (let [m  (double (or mass 0.0))
         pm (double (or gas-particle-mass debris-mass-threshold))]
     (cond
       (>= m star-mass-threshold)   :protostar
       (>= m planet-mass-threshold) :planet
       (> m pm)                     :planetesimal
       :else                        :nebula))))

;; --- Material response (collision malleability) ---
(def ^:const melt-temperature 1500.0)
;; K — above this a body is molten/malleable and deforms (merges) on impact;
;; well below it the body is brittle and shatters into debris when struck hard.

(defn malleability
  "0 (cold, brittle) … 1 (molten, malleable) from temperature. Drives whether a
   hard impact shatters a body or is absorbed by plastic deformation (merge)."
  [temperature]
  (max 0.0 (min 1.0 (/ (double (or temperature 0.0)) melt-temperature))))

(def ^:const shatter-malleability-max 0.5)
;; Below this the colder body is brittle: a hard enough impact shatters it
;; instead of merging. At/above it the body is molten and absorbs the impact.
(def ^:const shatter-dv-threshold 5.0e3)
;; m/s — relative impact speed above which a brittle body shatters rather than
;; merges. Gentle contacts always merge regardless of temperature.
(def ^:const shatter-min-mass 1.0e24)
;; kg — bodies below this always merge; fragmenting negligible masses isn't worth it.

;; --- Matter States ---

(def matter-state-schema
  "Schema for matter in various states from nebula to planet"
  {:id          (some-fn uuid? integer?) ;; ECS entity ids are integers; UUIDs also ok
   :position    vector? ;; [x y z]
   :velocity    vector? ;; [vx vy vz]
   :mass        pos?
   :radius      pos?
   :temperature pos?
   :density     pos?
   :composition map? ;; {:H 0.75 :He 0.24 :metals 0.01}
   :state       keyword? ;; :nebula :planetesimal :gas-giant :brown-dwarf :protostar :star :planet
   :luminosity  number?
   :pressure    number?})

(def nebula-cloud-schema
  "Statistical representation of unfocused nebular region"
  {:id          uuid?
   :center      vector?
   :extent      pos? ;; radius of cloud
   :total-mass  pos?
   :temperature pos?
   :density     pos?
   :composition map?
   :angular-momentum vector?
   :turbulence  number? ;; 0.0 to 1.0
   :focus-level number? ;; 0.0 (statistical) to 1.0 (fully resolved)
   })

(def angular-momentum-schema
  "Specific angular momentum vector [Lx Ly Lz] in kg m²/s."
  vector?)

(def spin-schema
  "Body-fixed angular velocity vector [ωx ωy ωz] in rad/s."
  vector?)

(def oblateness-schema
  "Polar/equatorial axis ratio c/a. 1 is spherical; smaller values are flatter discs."
  (some-fn nil? #(and (number? %) (<= 0.0 % 1.0))))

(def rotation-axis-schema
  "Unit vector [nx ny nz] along the body's angular momentum / spin axis."
  vector?)

(def accretion-radius-schema
  "Gravitational feeding-zone radius (m) of a star-forming body. Larger than the
   photosphere: it is the capture radius within which gas is accreted, and it
   does NOT shrink when the photosphere contracts. nil for ordinary gas clumps."
  (some-fn nil? pos?))

(def stellar-system-schema
  "Container for all bodies in a forming star system"
  {:id           uuid?
   :age          number? ;; seconds since formation began
   :central-star (some-fn nil? map?) ;; nil until star forms
   :bodies       sequential? ;; all other bodies
   :nebula       (some-fn nil? map?) ;; remaining nebula if any
   :time-scale   pos? ;; current time compression factor
   :complexity   number? ;; observable complexity metric
   })

;; --- Thresholds and Phase Transitions ---

(defn hydrostatic-equilibrium?
  "Test if a body is massive enough that its self-gravity overcomes material
   strength and pulls it into a round (hydrostatic) shape — the first half of
   the astronomical definition of a planet. We use a mass-threshold proxy rather
   than a full pressure-balance integration (see design open question #2)."
  [{:keys [mass]}]
  (boolean (and mass (> mass rounding-mass-threshold))))

(defn fusion-possible?
  "Test if conditions allow fusion ignition"
  [{:keys [temperature pressure composition]}]
  (and (> temperature fusion-temp-threshold)
       (> pressure fusion-pressure-threshold)
       (> (get composition :H 0) 0.1))) ;; at least 10% hydrogen

(def ^:const fusion-sustain-temp-threshold 7e6)
;; K — once ignited, a star keeps fusing down to this temperature before it
;; extinguishes. Ignition needs 1e7 K (fusion-temp-threshold); sustaining a
;; running fusion core needs less. This gap is the HYSTERESIS that stops a
;; marginal star (one sitting right on the 0.08 M☉ / 1e7 K knife-edge) from
;; flickering :star↔:protostar every time a wind dip nudges T across 1e7.

(defn fusion-sustaining?
  "Test if an ALREADY-IGNITED star still sustains fusion. Hysteresis below
   `fusion-possible?`: a burning star keeps fusing down to
   `fusion-sustain-temp-threshold`, so a small transient dip in T or mass does
   not extinguish it. Real main-sequence stars do not wink out when they shed a
   little wind mass."
  [{:keys [temperature pressure composition]}]
  (and (> temperature fusion-sustain-temp-threshold)
       (> pressure fusion-pressure-threshold)
       (> (get composition :H 0) 0.1)))

(defn orbital-cleared?
  "Test if a body has cleared its orbital neighborhood"
  [{:keys [mass orbital-radius]} other-bodies]
  ;; Simplified Stern-Levison parameter
  (let [hill-radius (* orbital-radius (Math/pow (/ mass (* 3 1.989e30)) 0.333))
        nearby (filter #(< (- (:orbital-radius %) orbital-radius) (* 2 hill-radius))
                       other-bodies)
        nearby-mass (reduce + 0 (map :mass nearby))]
    (> mass (* 100 nearby-mass)))) ;; dominates by factor of 100

(defn planet?
  "Full astronomical definition of a planet"
  [body other-bodies]
  (and (hydrostatic-equilibrium? body)
       (orbital-cleared? body other-bodies)
       (not (fusion-possible? body))))

;; --- Contracts ---

(def matter-state-contract
  (contract/->contract
   {:id       ::matter-state
    :shape-id ::stellar-body
    :kind     :type
    :schema   matter-state-schema
    :name     "Matter State"
    :description "Physical state of matter from nebula to planet"}))

(def nebula-cloud-contract
  (contract/->contract
   {:id       ::nebula-cloud
    :shape-id ::nebular-region
    :kind     :type
    :schema   nebula-cloud-schema
    :name     "Nebula Cloud"
    :description "Statistical representation of nebular gas cloud"}))

(def stellar-system-contract
  (contract/->contract
   {:id       ::stellar-system
    :shape-id ::star-system
    :kind     :type
    :schema   stellar-system-schema
    :name     "Stellar System"
    :description "Complete star system in formation"}))