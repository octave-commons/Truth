(ns domain.lod
  "Observer-centric level-of-detail scheduling over the ECS substrate. Assigns
   c/lod-level to stars and planets based on distance from the player's focus,
   controlling which fidelity-sensitive systems are relevant at each level.
   Observer-relative and ongoing (not a formation-loop concern), so it lives
   here rather than in the genesis loop.

     :local  — within ~0.5 AU: full detail (atmosphere shells, XUV escape, CME)
     :system — within ~5 AU:   band luminosities and steady winds
     :galaxy — beyond ~5 AU:   coarse SED only

   Pure data transformation; no IO."
  (:require
   [domain.ecs.core       :as ecs]
   [domain.ecs.components :as c]
   [domain.ecs.registry   :as reg]
   [domain.player         :as player]
   [shape.spatial         :as sp]))

(def ^:const lod-local-radius
  "Distance (m) within which entities are at :local LOD. ~0.5 AU."
  7.5e10)

(def ^:const lod-system-radius
  "Distance (m) within which entities are at :system LOD. ~5 AU."
  7.5e11)

(defn- registry-writes
  "This system's declared :writes from domain.ecs.registry — sourced from the
   registry so the emitter and the single-writer declaration cannot drift."
  [id]
  (some #(when (= id (:id %)) (:writes %)) reg/systems))

(defn lod-scheduler
  "Double-buffer write-set system: SOLE writer of c/lod-level — assigns :local,
   :system, or :galaxy to every star and planet based on distance from the
   player observer's focus position. Emits only the levels that CHANGED (an
   unchanged level writes nothing); with no observer it emits nothing."
  []
  {:id     :lod-scheduler
   :writes (registry-writes :lod-scheduler)
   :run
   (fn [world]
     (if-let [obs (player/get-observer world)]
       (let [focus (:focus-position obs [0.0 0.0 0.0])
             eids (filterv (fn [eid]
                             (let [st (ecs/get-component world eid c/matter-state)]
                               (or (= :star st) (= :planet st))))
                           (ecs/entities-with world c/matter-state c/position))]
         {c/lod-level
          (into {}
                (keep (fn [eid]
                        (let [pos  (ecs/get-component world eid c/position)
                              dist (sp/dist focus pos)
                              level (cond
                                      (< dist lod-local-radius)  :local
                                      (< dist lod-system-radius) :system
                                      :else                       :galaxy)]
                          (when (not= level (ecs/get-component world eid c/lod-level))
                            [eid level]))))
                eids)})
       {}))})
