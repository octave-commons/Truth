(ns domain.field
  "Regional-cell substrate for the dual-representation focus-zone system
   (Player Focus, child A — see
   kanban/tasks/phase-0-player-focus-a-statistical-substrate.md).

   A regional cell is an ordinary ECS entity carrying `c/statistical-mass` +
   `c/field-zone :regional` + `c/position`, and deliberately carries NO
   `c/matter-state`. Every existing sweep over matter (gravity, hydro, the
   regime classifier, the integrator) filters its entity query on
   `c/matter-state`, so a cell that never gets that component is structurally
   invisible to them — free isolation, no exclusion list to maintain.

   Homed as its own namespace rather than folded into
   `domain.genesis.promotion`: that file is a false start (see the parent
   epic's discovery note in
   kanban/tasks/phase-0-player-focus-promotion-demotion.md) slated for a full
   rewrite in child B, and the cell constructor is a substrate concern the
   rewrite should not have to carry or re-derive.

   Nothing here ticks: no system reads or writes these components yet. That is
   child B's `:focus-zone` fan-out emitter."
  (:require
   [domain.ecs.core :as ecs]
   [domain.ecs.components :as c]
   [law.field.schema :as lf]))

(defn regional-cell-components
  "Component map for one regional-cell entity, built from `ledger` (a
   `law.field/statistical-cell-schema` map: :mass :velocity
   :angular-momentum :mean-b :temperature :composition) and `position`
   ([x y z] metres). Throws `ex-info` if `ledger` fails the schema — a
   malformed ledger would otherwise corrupt promotion/demotion mass accounting
   silently downstream."
  [ledger position]
  (let [cell {:statistical-mass ledger
              :field-zone       :regional
              :position         position}]
    (when-not (lf/regional-cell? cell)
      (throw (ex-info "domain.field/regional-cell-components: fails law.field/regional-cell-schema"
                      {:ledger ledger :position position})))
    {c/statistical-mass ledger
     c/field-zone       :regional
     c/position         position}))

(defn spawn-regional-cell
  "Spawn one regional-cell entity from `ledger` + `position` on `world`.
   Returns `[world' eid]`. The entity carries no `c/matter-state`, so it is
   never seen by gravity, SPH hydro, the regime classifier, or the
   integrator (spec: dual-representation Phase 1)."
  [world ledger position]
  (let [[w eid] (ecs/spawn world)]
    [(ecs/put-components w eid (regional-cell-components ledger position)) eid]))
