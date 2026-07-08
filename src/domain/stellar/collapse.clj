(ns domain.stellar.collapse
  "Jeans instability and gravitational collapse: gas-to-core condensation and
   Kelvin–Helmholtz contraction of protostellar cores."
  (:require
   [clojure.math :as math] [law.stellar                  :as law]
   [domain.stellar.thermodynamics :as thermo]
   [domain.stellar.structure      :as structure]
   [domain.em                    :as em]
   [domain.ecs.core              :as ecs]
   [domain.ecs.components        :as c]))

(defn gravitational-collapse-rate
  "Collapse rate for a region based on the Jeans instability. Returns 1/s if the
   region is larger than its Jeans length (unstable), else 0."
  [{:keys [density temperature radius]}]
  (let [cs (math/sqrt (/ (* law/k-B temperature) law/m-H))
        jeans-length (* cs (math/sqrt (/ math/PI (* law/G density))))
        collapse-time (math/sqrt (/ (* 3 math/PI) (* 32 law/G density)))]
    (if (> radius jeans-length)
      (/ 1.0 collapse-time)
      0.0)))

(defn jeans-unstable?
  "True if a region is gravitationally unstable and will tend to collapse."
  [region]
  (pos? (gravitational-collapse-rate region)))

(defn jeans-length
  "Jeans length λ_J = c_s √(π / (G ρ)) for a gas of sound speed c_s and density ρ.
   Returns 0 for non-positive inputs."
  [density temperature]
  (let [rho (double (or density 0.0))
        cs  (thermo/sound-speed temperature)]
    (if (pos? rho)
      (* cs (math/sqrt (/ math/PI (* law/G rho))))
      0.0)))

(defn- jeans-promote-entity
  "Promote one Jeans-unstable :nebula parcel to a resolved body, shrinking its
   radius and latching a temporary feeding zone."
  [w eid gas-mass]
  (if (not= :nebula (ecs/get-component w eid c/matter-state))
    w
    (let [mass (double (ecs/get-component w eid c/mass))
          rho (double (ecs/get-component w eid c/density))
          temp (double (ecs/get-component w eid c/temperature))
          r (double (ecs/get-component w eid c/radius))
          lam-j (jeans-length rho temp)
          state (law/mass-class mass gas-mass)]
      (if (and (> r lam-j) (not= state :nebula))
        (let [r' (case state
                   (:planetesimal) (structure/sphere-radius mass structure/debris-material-density)
                   (:gas-giant :brown-dwarf) (structure/sphere-radius mass structure/planet-material-density)
                   :protostar r)
              rho' (/ mass (* (/ 4.0 3.0) math/PI r' r' r'))
              press' (law/ideal-gas-pressure rho' temp)
              ;; Keep the original gas smoothing length as a feeding zone so
              ;; nearby promoted bodies can merge before orbital spreading.
              accr (* 50.0 r)]
          (cond-> w
            true (ecs/put-component eid c/matter-state state)
            true (ecs/put-component eid c/radius r')
            true (ecs/put-component eid c/density rho')
            true (ecs/put-component eid c/pressure press')
            true (ecs/put-component eid c/accretion-radius accr)
            true (ecs/remove-component eid c/hydro-accel)))
        w))))

(defn jeans-collapse-system
  "Promote Jeans-unstable `:nebula` gas particles to resolved bodies. A fixed-mass
   gas sample becomes a physical object when self-gravity overcomes thermal
   pressure. The new state is set from the particle's mass via `law/mass-class`,
   its radius is shrunk to a resolved-body density, and it is removed from the
   SPH gas pool so it can only grow further by collision merging with other
   resolved bodies.

   A freshly promoted body keeps the gas sample's original radius as its
   `c/accretion-radius` for one tick. This gives bodies that formed side-by-side
   in a dense filament a chance to merge gravitationally before orbital motion
   spreads them apart; without it each promoted body is a pinpoint that rarely
   touches another."
  [world]
  (let [eids (ecs/entities-with world c/matter-state c/position c/density
                                c/radius c/temperature c/mass)
        gas-mass (:genesis/gas-particle-mass world)]
    (reduce #(jeans-promote-entity %1 %2 gas-mass) world eids)))

(defn- collapse-entity
  "Contract one protostar under self-gravity: KH oblate collapse, adiabatic
   heating, and flux-freezing of the frozen-in magnetic field."
  [world-snapshot w eid frac]
  (let [region (thermo/entity->region world-snapshot eid)
        {:keys [mass radius matter-state temperature density]} region]
    (if (and (= :protostar matter-state) radius mass)
      (let [L (or (ecs/get-component world-snapshot eid c/angular-momentum) [0.0 0.0 0.0])
            o (or (ecs/get-component world-snapshot eid c/oblateness) 1.0)
            floor (law/main-sequence-radius mass)
            shape (thermo/oblate-collapse-shape {:mass mass
                                                 :angular-momentum L
                                                 :equatorial-radius radius
                                                 :oblateness o
                                                 :collapse-fraction frac
                                                 :floor floor})
            a' (:equatorial-radius shape)
            c' (:polar-radius shape)
            new-density (thermo/oblate-density mass a' c')
            r-eq (thermo/equivalent-radius a' c')
            t-vir (thermo/virial-temperature mass r-eq)
            t-adiabatic (thermo/compression-heating (max (double (or temperature 3.0)) t-vir)
                                                    density new-density)
            new-temp (max t-vir t-adiabatic)
            new-press (law/ideal-gas-pressure new-density new-temp)
            new-spin (:spin shape)
            new-axis (:rotation-axis shape)
            anisotropy (- 1.0 (:oblateness shape))
            new-b (when-let [b (:b-field region)]
                    (em/flux-freeze b density new-density anisotropy))]
        (cond-> w
          true (ecs/put-component eid c/radius a')
          true (ecs/put-component eid c/density new-density)
          true (ecs/put-component eid c/temperature new-temp)
          true (ecs/put-component eid c/pressure new-press)
          true (ecs/put-component eid c/spin new-spin)
          true (ecs/put-component eid c/oblateness (:oblateness shape))
          true (ecs/put-component eid c/rotation-axis new-axis)
          new-b (ecs/put-component eid c/b-field new-b)))
      w)))

(defn collapse-system
  "A protostar — a clump that has accreted past the star-forming mass — contracts
   each tick under self-gravity: radius shrinks, density rises, and compression
   heating drives core temperature and pressure toward ignition (Kelvin–Helmholtz
   contraction). Its frozen-in magnetic field amplifies as B ∝ ρ^(2/3).

   Diffuse gas does NOT collapse in place here — it assembles by N-body gravity
   and accretion (collisions). Only the resolved star-forming core contracts.

   Contraction is RATE-LIMITED to the Kelvin–Helmholtz timescale, not a fixed
   fraction per tick: the equivalent radius relaxes toward the main-sequence
   floor as 1 − e^(−dt/τ), where τ = `:genesis/contraction-time` (default ~30
   Myr). With a fixed fraction the core reached the floor in a handful of ticks
   and ignited in ~50 kyr; rate-limiting spreads the ignition event over tens of
   Myr of simulation time, independent of how large `dt` is, while
   `collapse-fraction` remains a hard per-tick cap for stability.

   Temperature is heated adiabatically from the body's previous temperature as
   it compresses, then bounded below by the virial temperature so the core does
   not cool below the gravitational binding energy scale. Pressure follows from
   the ideal gas law."
  [{:keys [genesis/collapse-fraction genesis/contraction-time sim/dt]
    :or   {collapse-fraction 0.5 contraction-time 9.5e14 dt 1.0e12} :as world}]
  (let [frac (min (double collapse-fraction)
                  (- 1.0 (math/exp (- (/ (double dt) (double contraction-time))))))]
    (reduce #(collapse-entity world %1 %2 frac)
            world
            (ecs/entities-with world c/matter-state c/temperature c/density c/radius c/mass))))
