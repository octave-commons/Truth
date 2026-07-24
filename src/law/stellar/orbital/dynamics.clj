(ns law.stellar.orbital.dynamics
  "Orbital-dynamics computations: Plummer gravity, circular and virial speeds,
   Hill radius, isolation mass, and orbital-clearing predicates."
  (:require
   [clojure.math :as math]
   [law.stellar.orbital.constants :as constants]))

(def ^:const compact-matter-states
  "Matter states of RESOLVED COMPACT bodies for gravitational softening
   (kanban/tasks/compact-pair-softening.md): a compact body is a point mass at
   its physical radius, so its Plummer ε is c/radius — never the gas cloud's
   smoothing length (GADGET per-species practice). Everything else — :nebula
   gas parcels and stateless bodies — keeps the world softening."
  #{:condensed-core :planetesimal :gas-giant :brown-dwarf
    :protostar :star :planet :stellar-remnant})

(def ^:const softening-cutoff-fraction
  "The gravitational dead-zone radius as a fraction of the pair softening:
   pairs closer than 0.1·ε_pair contribute zero acceleration. For gas pairs
   (ε_pair = world ε) this is exactly the legacy scalar dead-zone."
  0.1)

(defn body-softening
  "The Plummer softening length ε (m) of ONE body under the species rule
   (kanban/tasks/compact-pair-softening.md):

   - RESOLVED COMPACT body (matter-state ∈ `compact-matter-states`): c/radius —
     the body is a point mass at its physical size (star ε ≈ 7e8 m, planet
     ε ≈ 7e7 m), so compact–compact gravity switches on inside what used to be
     the world dead-zone.
   - :nebula gas parcel or stateless body: the world `:sim/softening` — the
     cloud smoothing length, byte-identical to the legacy scalar kernel."
  [matter-state radius world-softening]
  (if (contains? compact-matter-states matter-state)
    (double (or radius 0.0))
    (double (or world-softening 0.0))))

(defn pair-softening
  "The momentum-symmetric pair softening ε_pair = max(ε_i, ε_j) (m).

   A pair-symmetric ε keeps the pair force Newton's-third-law exact; an
   asymmetric per-body ε would not. The pair dead-zone is
   `softening-cutoff-fraction` · ε_pair."
  [eps-i eps-j]
  (max (double (or eps-i 0.0)) (double (or eps-j 0.0))))

(defn softened-circular-speed
  "Circular-orbit speed (m/s) around mass `M` at radius `r` in the Plummer-
   softened gravity the integrator actually applies:

       v_c² = G M r² / (r² + ε²)^{3/2}

   Reduces to Kepler √(GM/r) for r ≫ ε and to the harmonic-core speed Ω·r
   (Ω = √(GM/ε³)) for r ≪ ε — a circular orbit is exact in BOTH regimes, so a
   body launched with this speed is bound and orbits at ANY radius. The
   unsoftened √(GM/r), by contrast, overshoots the softened field's grip by
   ~(ε/r)^{3/2} inside the softening length: a fragment placed at r ≪ ε with
   Keplerian speed feels almost no pull and leaves the system ballistically.

   PAIRING RULE (multi-timescale design §3.5): this is the spawn speed for
   bodies on the symplectic-Euler path (gas, :protostar companions), whose
   integrator applies the softened law. Sub-stepped compact bodies
   (:planet/:gas-giant/:stellar-remnant) take the Wisdom–Holman path, whose
   drift supplies the exact NEWTONIAN central term and strips the softened
   parent pull from the kick — their spawn speed must be
   `newtonian-circular-speed` or the drift reads the state as a near-radial
   plunge (the e≈1 regression of 2026-07-23)."
  [M r softening]
  (let [M (double (or M 0.0))
        r (double (or r 0.0))
        e (double (or softening 0.0))
        d2 (+ (* r r) (* e e))]
    (if (and (pos? M) (pos? r) (pos? d2))
      (math/sqrt (/ (* constants/G M r r) (math/pow d2 1.5)))
      0.0)))

(defn newtonian-circular-speed
  "Circular-orbit speed (m/s) around mass `M` at radius `r` in unsoftened
   Newtonian gravity: v_c = √(GM/r).

   The spawn speed for SUB-STEPPED compact bodies (:planet/:gas-giant/
   :stellar-remnant): the Wisdom–Holman sub-stepper advances their relative
   state with the exact Newtonian two-body term (μ = G·(M + m)), so a
   consistent spawn is Newtonian-circular regardless of how large the world's
   Plummer ε is. Bodies on the symplectic-Euler path must use
   `softened-circular-speed` instead — see its docstring for the pairing rule."
  [M r]
  (let [M (double (or M 0.0))
        r (double (or r 0.0))]
    (if (and (pos? M) (pos? r))
      (math/sqrt (/ (* constants/G M) r))
      0.0)))

(defn hill-radius
  "Hill (Roche) radius of a body of mass `m` orbiting mass `M-star` at radius `a`:
   R_H = a·(m / 3 M_star)^(1/3) — the reach of the body's gravity against the
   star's tide, and the natural width unit of its feeding zone. Metres."
  [m M-star a]
  (let [m (double (or m 0.0))
        M (double (or M-star 0.0))
        a (double (or a 0.0))]
    (if (and (pos? m) (pos? M) (pos? a))
      (* a (math/pow (/ m (* 3.0 M)) (/ 1.0 3.0)))
      0.0)))

(defn isolation-mass
  "Planetesimal isolation mass (kg): the mass a growing body reaches once it has
   swept up all the solids in its feeding zone and can grow no further from local
   material. This is the physical cap that prevents runaway growth from producing
   an arbitrarily large planet out of one annulus.

   Solving M_iso = 2π·a·(2·B·R_H)·Σ_solid self-consistently with
   R_H = a·(M_iso/3M_star)^(1/3) gives (Lissauer 1993):

       M_iso = [4π·B·a²·Σ_solid]^(3/2) / (3·M_star)^(1/2)

   where B = `feeding-zone-hill-factor`. M_iso ∝ Σ_solid^(3/2)·a³·M_star^(−1/2):
   ~0.05–0.15 M⊕ at 1 AU (sub-Mars), rising to several–10 M⊕ at ~5 AU beyond the
   ice line where Σ_solid jumps ~3–4×. A body may exceed this ONLY via runaway
   gas accretion after reaching pebble-isolation mass beyond the ice line.

   `a` in metres, `sigma-solid` in kg/m², `M-star` in kg. 0 for degenerate input."
  [a sigma-solid M-star]
  (let [a  (double (or a 0.0))
        s  (double (or sigma-solid 0.0))
        M  (double (or M-star 0.0))]
    (if (and (pos? a) (pos? s) (pos? M))
      (/ (math/pow (* 4.0 math/PI constants/feeding-zone-hill-factor a a s) 1.5)
         (math/sqrt (* 3.0 M)))
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
      (math/sqrt (/ (* constants/G M) R))
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
      (/ (* constants/G M r) (math/pow d2 1.5))
      0.0)))

(def ^:const default-dark-matter-mass-factor
  "Default static-halo mass, as a multiple of `:genesis/nebula-mass`: the
   dark-matter background is deliberately MORE massive than the collapsing
   nebula (owner decision — kanban/tasks/dark-matter-static-halo.md) so the
   well is deep enough to hold onto infall-momentum debris that would
   otherwise fling past the system edge. First-pass guess, overridable per
   world via `:genesis/dark-matter-mass-factor`; needs live-window tuning
   against SPH collapse (too deep stalls accretion/disk formation)."
  3.0)

(def ^:const default-dark-matter-scale-factor
  "Default static-halo Plummer scale radius, as a fraction of
   `:genesis/nebula-radius` — about half the initial nebula radius, so the
   halo's peak pull (at a/√2, see `plummer-acceleration`) sits well inside
   the collapsing cloud. Overridable per world via
   `:genesis/dark-matter-scale-factor`."
  0.5)

(defn orbital-cleared?
  "Test if a body has cleared its orbital neighborhood."
  [{:keys [mass orbital-radius]} other-bodies]
  ;; Simplified Stern-Levison parameter
  (let [hill-r (* orbital-radius (math/pow (/ mass (* 3 constants/solar-mass)) 0.333))
        nearby (filter #(< (- (:orbital-radius %) orbital-radius) (* 2 hill-r))
                       other-bodies)
        nearby-mass (reduce + 0 (map :mass nearby))]
    (> mass (* 100 nearby-mass)))) ;; dominates by factor of 100

(defn planet?
  "Full astronomical definition of a planet."
  [body other-bodies]
  (and (constants/hydrostatic-equilibrium? body)
       (orbital-cleared? body other-bodies)
       (not (constants/fusion-possible? body))))
