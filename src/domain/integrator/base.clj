(ns domain.integrator.base
  "Shared primitives and the influence registry for the integrator subsystems."
  (:require
   [domain.ecs.core :as ecs]
   [domain.ecs.components :as c]
   [shape.spatial :as sp]))

(def zero3 [0.0 0.0 0.0])

(def influence-registry
  "Declarative map: each additive physical field → the influence components that
   contribute to it (summed, then scaled). The integrator reads THIS, not
   hardcoded knowledge — adding a force/heat/torque/mass source is one line here
   plus its single-writer emitter. Non-additive fields (temperature, composition,
   spin) are derived by the per-field updaters below and documented in :derived."
  {:velocity         {:accumulate [c/accel-gravity c/accel-pressure c/accel-lorentz
                                   c/accel-observer c/accel-warp]
                      :compose :sum :scale :dt}
   :angular-momentum {:accumulate [c/torque-em c/torque-disk]
                      :compose :sum :scale :dt}
   :mass             {:accumulate [c/mass-flux-flare
                                   c/mass-flux-xuv c/mass-flux-disk
                                   c/mass-flux-transfer c/mass-flux-condense]
                      :compose :sum :scale :raw}
   :velocity-delta   {:accumulate [c/dv-flare c/dv-transfer]
                      :compose :sum :scale :raw}
   :temperature      {:influences [c/heat-intervention]
                      :derived "virial (cores) / radiative (worlds) + intervention ease"}
   :composition      {:influences [c/comp-burn c/comp-depletion]
                      :derived "comp.burn replaces, comp.depletion zeroes"}
   :position         {:influences [c/frame-offset]
                      :derived "x + v·dt − frame-offset (COM Galilean shift)"}
   :spin             {:derived "L / I (moment of inertia)"}})

(def accel-sources
  "Acceleration influences summed into velocity."
  (get-in influence-registry [:velocity :accumulate]))

(def dv-sources
  "Velocity-delta influences applied directly to velocity."
  (get-in influence-registry [:velocity-delta :accumulate]))

(def mass-flux-sources
  "Mass-flux influences summed into mass."
  (get-in influence-registry [:mass :accumulate]))

(def torque-sources
  "Torque influences summed into angular momentum."
  (get-in influence-registry [:angular-momentum :accumulate]))

(defn sum-vec-influences
  "Σ of the vector influence components `ctypes` on `eid` (missing ⇒ zero)."
  [world eid ctypes]
  (reduce (fn [acc ct] (sp/v+ acc (or (ecs/get-component world eid ct) zero3)))
          zero3 ctypes))

(defn sum-scalar-influences
  "Σ of the scalar influence components `ctypes` on `eid` (missing ⇒ 0)."
  [world eid ctypes]
  (reduce (fn [acc ct] (+ acc (double (or (ecs/get-component world eid ct) 0.0))))
          0.0 ctypes))

(defn absorb-packets-for
  "All absorb-accrete and absorb-merge packets targeting `eid`."
  [world eid]
  (let [matches (fn [m] (get m eid))]
    (into (or (matches (get-in world [:components c/absorb-accrete] {})) [])
          (or (matches (get-in world [:components c/absorb-merge] {})) []))))

(defn due-entity?
  "True if `eid` should be advanced this tick according to its LOD tick phase.
   Entities without a `c/lod-tick-phase` are always due (backward-compatible).
   Throttling is only active when the world has `:lod/throttle-ticks?` true."
  [world tick eid]
  (if (:lod/throttle-ticks? world)
    (if-let [{:keys [period phase]}
             (ecs/get-component world eid c/lod-tick-phase)]
      (zero? (mod (- (long tick) (long phase))
                  (long (or period 1))))
      true)
    true))

(defn due-entities
  "Filter `eids` to those due this tick. Reads `c/lod-tick-phase` and the
   world's `:tick` when `:lod/throttle-ticks?` is true."
  [world eids]
  (let [tick (long (or (:tick world) 0))]
    (filterv #(due-entity? world tick %) eids)))
