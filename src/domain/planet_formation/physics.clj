(ns domain.planet-formation.physics
  "Disk physics for the core-accretion planet seeder: sound speed, equilibrium
   temperature, snow line, MMSN surface density, and core-accretion mass assembly."
  (:require
   [clojure.math :as math]
   [law.stellar :as law]))

(def ^:const snow-line-temperature 170.0)

(def ^:const proto-solar-metal-frac 0.015)

(def ^:const ice-enhancement-factor 3.5)

(def ^:const min-seed-mass-solar
  "Minimum seed mass in solar masses; bodies below this are not seeded."
  1.0e-8)

(def ^:const max-seed-mass-solar
  "Maximum seed mass in solar masses; caps runaway gas giants."
  13.0)

(def ^:const critical-core-mass-kg
  "Pollack-critical core mass (~10 M⊕). Beyond the ice line a core this heavy
   opens a gap, halts pebble flux, and triggers runaway gas accretion — the only
   route to a planet far heavier than the local isolation mass."
  (* 10.0 law/earth-mass))

(def ^:const runaway-gas-fraction
  "Fraction of the remaining disk gas a runaway giant draws into its envelope
   once its core passes `critical-core-mass-kg`. Tunable proxy for the runaway
   gas-accretion phase; caps a giant at a plausible sub-brown-dwarf mass."
  0.2)

;; --- Condensation seed mass (seed-and-grow small bodies) ---------------------
;; Real planetesimal-formation models (streaming instability) produce clumps of
;; ~100-km bodies, ~1e15–1e18 kg. We fix a toy-scale seed at the high end of that
;; range (~10× Chicxulub) so it is safely above the parcel ULP and numerically
;; stable as a parcel debit, while staying far below a gas parcel (4e27 kg).
;; Growth after seeding is collisional / rare BHL capture, not rapid runaway.
(def ^:const condensation-seed-mass-kg 1.0e16)

(defn condensation-seed-mass
  "Return the fixed physical seed mass for a condensation event. Overridable via
   `:genesis/condensation-seed-mass-kg` on the world."
  [world]
  (double (or (:genesis/condensation-seed-mass-kg world)
              condensation-seed-mass-kg)))

(defn sound-speed
  "Adiabatic sound speed c_s = √(γ k_B T / m_H) for a thin disc. m/s."
  [temperature]
  (if (pos? (double temperature))
    (math/sqrt (/ (* 1.6666667 law/k-B (double temperature)) law/m-H))
    0.0))

(defn equilibrium-temperature
  "Blackbody equilibrium temperature (K) at orbital radius `r` for a star of
   luminosity `L`, with Bond albedo `A`:  T = (L(1-A) / (16 π σ r²))^(1/4).

   This is a planet's seed temperature — it replaces a fixed literal so a world's
   habitability follows its orbit. (A sun-luminosity star gives ~255 K at 1 AU;
   the liquid-water band 273–373 K sits at ~0.47–0.87 AU.) Falls back to 250 K
   when L or r is non-positive."
  [luminosity r albedo]
  (let [L (double (or luminosity 0.0))
        r (double (or r 0.0))
        A (double albedo)]
    (if (and (pos? L) (pos? r))
      (math/pow (/ (* L (- 1.0 A))
                   (* 16.0 math/PI law/stefan-boltzmann r r))
                0.25)
      250.0)))

(defn snow-line-radius
  "Radius where equilibrium T = 170 K for a blackbody at luminosity L:
   r = sqrt(L / (16 π σ T⁴)). Beyond it, water ice condenses and the solid
   surface density jumps ~3.5×."
  [luminosity]
  (let [T snow-line-temperature]
    (math/sqrt (/ (double luminosity)
                  (* 16.0 math/PI law/stefan-boltzmann (math/pow T 4))))))

(defn solid-surface-density
  "Solid (dust+ice) surface density at radius r: Σ_gas·Z, ice-enhanced beyond
   the snow line by ~3.5×. Z = metal fraction (~0.015 proto-solar)."
  [sigma-gas r snow-line metal-frac]
  (* sigma-gas (double metal-frac)
     (if (> (double r) (double snow-line)) ice-enhancement-factor 1.0)))

(defn mmsn-sigma0
  "Normalization Σ₀ (kg/m²) of a minimum-mass-solar-nebula gas profile
   Σ(r) = Σ₀·(r/AU)^(−3/2) that carries total mass `disk-m` between `r-in` and
   `r-out`. From ∫Σ·2πr dr = disk-m:

       Σ₀ = disk-m / (4π · AU^{3/2} · (√r_out − √r_in))

   Using a physical radial profile (rather than equal mass per annulus) is what
   makes the derived isolation mass sane: inner annuli are small in area, so
   equal-mass binning inflates their surface density and, with it, any mass
   derived from it. 0 for degenerate input."
  [disk-m r-in r-out]
  (let [dm (double disk-m) ri (double r-in) ro (double r-out)]
    (if (and (pos? dm) (< 0.0 ri) (< ri ro))
      (/ dm (* 4.0 math/PI (math/pow law/au 1.5)
               (- (math/sqrt ro) (math/sqrt ri))))
      0.0)))

(defn mmsn-sigma
  "Gas surface density (kg/m²) at radius `r` for the MMSN profile with
   normalization `s0` (see `mmsn-sigma0`): Σ(r) = s0·(r/AU)^(−3/2)."
  [s0 r]
  (let [r (double r)]
    (if (pos? r)
      (* (double s0) (math/pow (/ r law/au) -1.5))
      0.0)))

(defn core-accretion-timescale
  "Time to build a ~10 M⊕ core at r (Pollack 1996 parameterization): τ ∝
   1/Σ_solid, scaled by orbital period. Returns seconds."
  [r sigma-solid star-mass]
  (let [r-m (double (or r 0.0))
        sig (double (or sigma-solid 0.0))
        M   (double (or star-mass 0.0))]
    (if (and (pos? r-m) (pos? sig) (pos? M))
      (let [period (* 2.0 math/PI (math/sqrt (/ (* r-m r-m r-m) (* law/G M))))]
        (* period (/ 1.0 (* 0.01 sig 1.0e5)))) ;; calibrated: Σ in kg/m²
      Double/POSITIVE_INFINITY)))

(defn planet-mass
  "Compute final planet mass from core mass and possible runaway gas envelope.
   Returns `{:giant? :mass-kg :core-m :gas-m}` — the core/envelope split lets
   the seeder mass-weight the planet's composition between condensed solids
   (core) and captured nebular gas (envelope)."
  [{:keys [core-m beyond?]} disk-m]
  (let [giant? (and beyond? (>= core-m critical-core-mass-kg))
        gas-m (if giant?
                (max 0.0
                     (min (* runaway-gas-fraction disk-m)
                          (- (* max-seed-mass-solar law/solar-mass) core-m)))
                0.0)
        mass-kg (-> (+ core-m gas-m)
                    (max (* min-seed-mass-solar law/solar-mass))
                    (min disk-m (* max-seed-mass-solar law/solar-mass)))]
    {:giant? giant? :mass-kg mass-kg :core-m core-m :gas-m gas-m}))
