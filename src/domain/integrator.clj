(ns domain.integrator
  "The single integrator — sole writer of physical state.\n\n   Spec: docs/notes/specs/2026.06.29-unified-physical-state-integrator-spec.md.\n\n   §0 (the one sentence): there is ONE writer of physical state — this integrator\n   — which reads, each tick, the previous snapshot plus a set of INFLUENCE\n   components registered against the physical fields they affect; every other\n   system is a pure, snapshot-reading, single-component-writing emitter, and\n   nothing runs serially \"after the fold.\"\n\n   This namespace is a thin facade and orchestrator: the actual per-field\n   updaters live in `domain.integrator.base`, `domain.integrator.kinematics`,\n   `domain.integrator.temperature`, and `domain.integrator.core`."
  (:require
   [domain.ecs.components :as c]
   [domain.profile :as profile]
   [domain.integrator.core :as core]
   [domain.integrator.kinematics :as kinematics]
   [domain.integrator.temperature :as temperature]
   [domain.integrator.base :as base]
   [domain.ecs.core :as ecs]
   [law.material :as material]
   [domain.material.rotation :as material-rotation]
   [domain.continuum :as continuum]))

(defn- material-energy-ws [world]
  (reduce (fn [ws eid]
            (let [cell (ecs/get-component world eid c/material-cell)
                  energy (+ (ecs/get-component world eid c/internal-energy)
                            (base/sum-scalar-influences world eid
                              (get-in base/influence-registry [:internal-energy :accumulate])))
                  thermal (material/thermal-state cell energy)]
              (-> ws (assoc-in [c/internal-energy eid] energy)
                  (assoc-in [c/material-thermal eid] thermal)
                  (assoc-in [c/temperature eid] (:temperature thermal)))))
          {} (ecs/entities-with world c/material-cell c/internal-energy)))

;; ---------------------------------------------------------------------------
;; Re-exports from domain.integrator.base
;; ---------------------------------------------------------------------------

;; ---------------------------------------------------------------------------
;; Re-exports from domain.integrator.core
;; ---------------------------------------------------------------------------

(def mass-ws
  "Mass write-set. m' = max(0, m + Σ mass-flux.* + Σ absorb-mass)."
  core/mass-ws)

(def composition-ws
  "Composition write-set."
  core/composition-ws)

;; ---------------------------------------------------------------------------
;; Re-exports from domain.integrator.kinematics
;; ---------------------------------------------------------------------------

;; ---------------------------------------------------------------------------
;; Re-exports from domain.integrator.temperature
;; ---------------------------------------------------------------------------

;; ---------------------------------------------------------------------------
;; Phase orchestration
;; ---------------------------------------------------------------------------

(defn- run-integrator-phases
  "Compose the per-field updaters and merge their profiles."
  [world dt]
  (let [kin  (if-let [soa (:genesis/physics-soa world)]
               (kinematics/kinematics-ws-soa world dt soa)
               (kinematics/kinematics-ws world dt))
        mass (profile/profile-section
              world :integrator/mass
              (fn [_world] (core/mass-ws world)))
        temp (profile/profile-section
              world :integrator/temperature
              (fn [_world] (temperature/temperature-ws world dt)))
        ion  (profile/profile-section
              world :integrator/ionization
              (fn [_world] (core/ionization-ws world)))
        comp-ws (profile/profile-section
                 world :integrator/composition
                 (fn [_world] (core/composition-ws world)))
        cond-ws (profile/profile-section
                 world :integrator/comp-condensed
                 (fn [_world] (core/comp-condensed-ws world)))
        rot  (profile/profile-section
              world :integrator/rotation
              (fn [_world] (core/rotation-ws world)))
        profile (apply merge-with +
                       (map #(or (:genesis/_profile %) {}) [kin mass temp ion comp-ws cond-ws rot]))]
    (cond-> (merge-with merge kin mass temp ion comp-ws cond-ws rot (material-energy-ws world) (material-rotation/orientation-ws world dt) (continuum/integrator-ws world dt))
      (seq profile) (assoc :genesis/_profile profile))))

(defn integrator-system
  "Write-set system: the single owner of the dynamical/contended physical fields.
   Composes the per-field updaters (each writes a disjoint set of components, so
   the fragments merge cleanly). Sole writer of position, velocity, mass,
   temperature, ionization-fraction, composition, angular-momentum, and spin.
   Uses the `:genesis/physics-soa` cache for kinematics when present.\n\n   Each major phase is wrapped with `profile/profile-section`; their\n   `:genesis/_profile` maps are merged into the returned write-set so the\n   benchmark harness can report subsystem timings."
  [dt]
  {:id     :integrator
   :writes #{c/continuum-region c/position c/velocity c/mass c/temperature c/ionization-fraction c/composition c/comp-condensed
             c/angular-momentum c/spin c/consumed-transfer c/internal-energy c/material-thermal c/material-orientation}
   :reads  #{c/continuum-region c/material-packet c/material-orientation c/lod-tick-phase c/material-cell c/internal-energy c/heat-material c/accel-material}
   :run    (fn [world] (run-integrator-phases world dt))})
