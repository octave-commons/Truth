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

;; --- Real stellar/sub-stellar mass boundaries (authentic formation fate) -----
;; The two physical thresholds that decide a contracting core's destiny. These
;; are NOT toy tiers — they are the actual deuterium- and hydrogen-burning limits.
(def ^:const deuterium-burning-mass (* 0.013 solar-mass))
;; ~2.59e28 kg (~13 M_Jupiter). Below this, no fusion of any kind → planet/debris.
;; Between this and the hydrogen limit → brown dwarf: burns deuterium, then
;; contraction is halted by electron degeneracy before hydrogen can ignite.
(def ^:const hydrogen-burning-mass  (* 0.08 solar-mass))
;; ~1.59e29 kg (~80 M_Jupiter). At/above this a contracting core reaches the
;; ~1e7 K needed for sustained hydrogen fusion → a true main-sequence star.

(defn ideal-gas-pressure
  "Pressure of a gas region from the ideal gas law: P = ρ k_B T / m_H."
  [density temperature]
  (/ (* density k-B temperature) m-H))

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
  "Classify an accreted clump's matter-state purely from its mass. A clump that
   has reached star-forming mass becomes a :protostar — 'big and hot', contracting
   — and only the fusion test promotes it to a true :star.

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
       (> m pm)                     :debris
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

;; --- Matter States ---

(def matter-state-schema
  "Schema for matter in various states from nebula to planet"
  {:id          uuid?
   :position    vector? ;; [x y z]
   :velocity    vector? ;; [vx vy vz]
   :mass        pos?
   :radius      pos?
   :temperature pos?
   :density     pos?
   :composition map? ;; {:H 0.75 :He 0.24 :metals 0.01}
   :state       keyword? ;; :nebula :protostar :star :planet :debris
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