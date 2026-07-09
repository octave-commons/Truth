(ns domain.player.system
  "Observer ECS system driven by the event ledger."
  (:require
   [domain.ecs.event :as event]
   [domain.player.state :as state]
   [domain.player.economy :as economy]
   [domain.player.focus :as focus]))

(def ^{:private true} event-kind->coherence "Map ledger event kinds to the coherence-gain / agency-gain categories."
  #:event{:phase-transition :phase-transition
          :planetesimal-formation :planetesimal-formation
          :life-emergence :life-emergence
          :protostar-formation :protostar-formation
          :planet-formation :planet-formation
          :collision :collision
          :gate-discovery :gate-discovery
          :stellar-ignition :stellar-ignition
          :brown-dwarf-formation :brown-dwarf-formation
          :nebula-collapse :nebula-collapse
          :gas-giant-formation :gas-giant-formation
          :condensed-core-formation :condensed-core-formation})

(defn observer-system "ECS system: drains/restores the observer's coherence based on the events that\n   landed in the ledger since it last looked, and the world's current observable\n   complexity (read from :genesis/complexity), accrues agency from those events,\n   and caches the resolved observation verbs (observation-effect / collapse-radius)\n   for the renderer. Player-facing arc TEXT (quest / observation note / event\n   notification) is produced by `domain.arc/advance-arc`, not here — that keeps\n   the observer/coherence loop free of any dependency on the narrative layer." [dt] (fn [world] (if-let [obs (state/get-observer world)] (let [complexity (get world :genesis/complexity 0) this-tick (:tick world) new-events (->> (event/events-since world this-tick) (filter (fn* [p1__248#] (= (:tick p1__248#) this-tick))) (keep (fn* [p1__249#] (event-kind->coherence (:kind p1__249#))))) obs1 (-> (economy/apply-coherence obs dt complexity new-events) (economy/accrue-agency new-events) (economy/accrue-resonance new-events) (assoc :last-tick this-tick) (update :time-witnessed + dt)) obs' (assoc obs1 :observation-effect (focus/observation-effect obs1) :collapse-radius (focus/probability-collapse-radius obs1))] (state/put-observer world obs')) world)))
