(ns domain.hydro.common
  "Shared utilities and constants for the SPH hydrodynamic subsystems."
  (:require
   [law.field :as lf]
   [domain.ecs.core :as ecs]
   [domain.ecs.components :as c]
   [domain.spatial.index :as idx]))

(def ^:const sph-h-factor
  "SPH smoothing length as a multiple of a parcel's distance to its NEAREST
   neighbour: h = sph-h-factor · d_nn. The smoothing length is GEOMETRIC (set by
   neighbour spacing, not by density), so it is unconditionally stable — there is
   no ρ→h→ρ feedback and hence no h→0 / ρ→∞ runaway (the bug a density-based
   adaptive radius caused). It is also responsive: as the cloud collapses, d_nn
   shrinks, h shrinks, and the SPH density rises ∝ 1/h³ — so a real collapse (not
   an iteration artifact) carries dense regions across the condensation gate. The
   small factor keeps a diffuse parcel's self-density a few× below the gate, so
   condensation needs a genuine ~1.5–2× local compression." 0.013)

(def ^:const sph-h-min
  "Absolute floor on the smoothing length (m), a final guard so a coincident pair
   cannot produce an infinite density." 1.0e9)

(defn entity->hydro-data
  "Project an ECS entity into the map the SPH functions expect."
  [world eid]
  {:eid         eid
   :position    (ecs/get-component world eid c/position)
   :velocity    (ecs/get-component world eid c/velocity)
   :mass        (ecs/get-component world eid c/mass)
   :radius      (ecs/get-component world eid c/radius)
   :density     (ecs/get-component world eid c/density)
   :pressure    (ecs/get-component world eid c/pressure)
   :temperature (ecs/get-component world eid c/temperature)
   :state       (ecs/get-component world eid c/matter-state)})

(defn hydro-active?
  "Pressure-gradient dynamics matter for diffuse and contracting gas, not for
   solid debris or fusion-supported stars."
  [state]
  (lf/hydro-em-active? state))

(defn cache-neighbors-and-gradients
  "Return [neighbors gradients] for `data` using the transient neighbor cache
   when present, otherwise query the spatial index. `radius-fn` produces the
   query radius from the particle data; `state-pred` filters neighbors by
   matter state. The returned `gradients` is nil when the cache is not used.

   Accepts a single options map:
     {:world :data :radius-fn :state-pred :gradient-key}."
   [{:keys [world data radius-fn state-pred gradient-key]}]
   (let [h (double (radius-fn data))]
     (if-let [entry (ecs/get-component world (:eid data) c/neighbor-cache)]
       (let [hh2 (* h h)
             nbrs (filterv #(and (state-pred (:matter-state %))
                                 (<= (double (:r2 %)) hh2))
                           (:neighbors entry))
             grads (mapv gradient-key nbrs)]
         [nbrs grads])
       [(idx/within-radius (:genesis/spatial-tree world) (:position data) h
                           #(state-pred (:matter-state %))) nil])))

