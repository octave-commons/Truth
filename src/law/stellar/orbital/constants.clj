(ns law.stellar.orbital.constants
  "Physical constants, unit scales, and threshold-based classification helpers
   for Phase 0 stellar/orbital mechanics."
  (:require
   [clojure.math :as math]))

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

;; White-dwarf radius scale: approximate degenerate radius floor for a
;; stellar remnant. Used as the structure floor for :stellar-remnant.
(defn white-dwarf-radius
  "Approximate white-dwarf radius (m) for a remnant of `mass`.
   Scales roughly as M^(-1/3) with a solar-mass floor near 0.01 R_sun."
  [mass]
  (let [m (max 1e-3 (/ (double (or mass solar-mass)) solar-mass))]
    (* 0.01 solar-radius (math/pow m (- (/ 1.0 3.0))))))

;; Ablation floor: when a bound body's mass drops below this, it is despawned.
;; Chosen as a small fraction of the deuterium-burning limit.
(def ^:const ablation-floor (* 1.0e-3 deuterium-burning-mass))

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

;; Feeding-zone half-width in Hill radii. An oligarch clears solids out to a few
;; Hill radii on each side; the canonical spacing is ~2√3 (Lissauer 1993).
(def ^:const feeding-zone-hill-factor 3.46) ;; ≈ 2√3

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
   `domain.stellar.classifier.state/classifier-system` and `jeans-collapse-system`, are NOT in the
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
    (* solar-radius (math/pow m (if (< m 1.0) 0.8 0.57)))))

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
