(ns domain.regime
  "Pure dimensionless-number classifier — the keystone of the Phase 0 physics
   core (see docs/designs/phase0-coupled-physics-and-regime-classifier.md).

   Given a cell/clump's field state, decide which physics dominates locally, so
   upstream systems and the renderer can spend detail and colour where it
   matters. The classifier never mutates field state; it only reads and tags.

   The dimensionless numbers (all SI):
     β     = P_gas / P_B      gas vs magnetic pressure
     ℳ     = |v| / c_s        flow vs sound speed (shocks)
     M_A   = |v| / v_A        flow vs Alfvén speed
     L/λ_J = jeans ratio      gravity vs pressure support"
  (:require
   [law.field             :as lf]
   [law.stellar           :as ls]
   [domain.em             :as em]
   [shape.spatial         :as sp]
   [domain.ecs.core       :as ecs]
   [domain.ecs.parallel   :as par]
   [domain.ecs.components  :as c]))

;; --- Dimensionless numbers --------------------------------------------------

(defn sound-speed
  "Adiabatic sound speed c_s = √(γp/ρ)  (SI). m/s."
  [{:keys [pressure density]}]
  (if (and pressure density (pos? (double density)) (pos? (double pressure)))
    (Math/sqrt (/ (* lf/gamma (double pressure)) (double density)))
    0.0))

(defn plasma-beta
  "β = P_gas / P_B. Infinite when there is no field (gas wholly dominates)."
  [{:keys [pressure b-field]}]
  (let [p-b (em/magnetic-pressure b-field)]
    (if (pos? p-b)
      (/ (double (or pressure 0.0)) p-b)
      Double/POSITIVE_INFINITY)))

(defn mach
  "Flow Mach number ℳ = |v| / c_s. Zero at rest; infinite if c_s is zero."
  [{:keys [velocity] :as cell}]
  (let [c-s (sound-speed cell)
        v   (if velocity (sp/len velocity) 0.0)]
    (cond
      (pos? c-s) (/ v c-s)
      (pos? v)   Double/POSITIVE_INFINITY
      :else      0.0)))

(defn alfven-mach
  "Alfvén-Mach number M_A = |v| / v_A. Infinite when the field is negligible
   (passively advected); zero at rest."
  [{:keys [velocity density b-field]}]
  (let [v-a (em/alfven-speed b-field density)
        v   (if velocity (sp/len velocity) 0.0)]
    (cond
      (pos? v-a) (/ v v-a)
      (pos? v)   Double/POSITIVE_INFINITY
      :else      0.0)))

(defn jeans-ratio
  "L/λ_J for a clump — at or above 1 it is Jeans-unstable. Same Jeans length as
   domain.stellar/gravitational-collapse-rate, reported as a smooth ratio."
  [{:keys [density temperature radius]}]
  (if (and density temperature radius
           (pos? (double density)) (pos? (double temperature)))
    (let [c-s          (Math/sqrt (/ (* ls/k-B (double temperature)) ls/m-H))
          jeans-length (* c-s (Math/sqrt (/ Math/PI (* ls/G (double density)))))]
      (if (pos? jeans-length)
        (/ (double radius) jeans-length)
        0.0))
    0.0))

;; --- Classification ---------------------------------------------------------

(defn numbers
  "All dimensionless diagnostics for a cell, as a plain map."
  [cell]
  {:beta        (plasma-beta cell)
   :mach        (mach cell)
   :alfven-mach (alfven-mach cell)
   :jeans-ratio (jeans-ratio cell)})

(defn classify
  "Return {:regime <tag> :numbers {...}} for a cell. The dominant-physics tag:

     :gravitationally-unstable L/λ_J ≥ 1           (gravity overwhelms pressure)
     :mhd-dominated            β < 1 AND M_A ≤ 1   (field shapes the flow)
     :gravity-hydro            otherwise           (gas pressure + gravity)

   Gravitational instability is checked first: β compares the field to *thermal*
   pressure, but a Jeans-unstable clump's decisive fact is that it tends to
   collapse, so that tag wins even when the field is locally strong. (Whether
   the field actually halts that collapse is the separate magnetic-support /
   mass-to-flux test in domain.em, which the collapse system applies.)

   These are the nebula-scale tags; interior/disc tags (:convective,
   :stable-disc, :tectonically-dead) attach later from the same machinery."
  [cell]
  (let [n  (numbers cell)
        b  (:beta n)
        ma (:alfven-mach n)
        jr (:jeans-ratio n)
        tag (cond
              (>= jr lf/jeans-unstable)               :gravitationally-unstable
              (and (< b lf/beta-magnetized)
                   (<= ma lf/alfven-mach-magnetized)) :mhd-dominated
              :else                                   :gravity-hydro)]
    {:regime tag :numbers n}))

;; --- ECS projection + system ------------------------------------------------

(defn entity->cell
  "Project an entity's components into the map the classifier reads."
  [world eid]
  {:density     (ecs/get-component world eid c/density)
   :temperature (ecs/get-component world eid c/temperature)
   :pressure    (ecs/get-component world eid c/pressure)
   :radius      (ecs/get-component world eid c/radius)
   :velocity    (ecs/get-component world eid c/velocity)
   :b-field     (ecs/get-component world eid c/b-field)})

(defn regime-system
  "Tag every matter entity with its dominant-physics regime for this tick.
   Runs after gravity and EM so β and M_A reflect the current field, and before
   hydro so collapse/render can read the tag. Stores :component/regime.

   Per-entity classification is pure, so it is computed in parallel and the tags
   folded back sequentially."
  [world]
  (let [eids (ecs/entities-with world c/matter-state c/density c/temperature)
        tags (par/par-mapv
              (fn [eid] [eid (:regime (classify (entity->cell world eid)))])
              eids)]
    (reduce (fn [w [eid tag]] (ecs/put-component w eid c/regime tag))
            world
            tags)))
