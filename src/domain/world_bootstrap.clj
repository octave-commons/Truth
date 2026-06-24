(ns domain.world-bootstrap
  "Bootstrap a world with orbital + collision systems and handlers.
   This is the entry point for composing subsystems."
  (:require
    [domain.ecs.core                  :as ecs]
    [domain.ecs.event                 :as event]
    [domain.ecs.timeline              :as timeline]
    [domain.orbital.system            :as orbital]
    [domain.physics.collision         :as collision]
    [domain.physics.collision-response :as response]))

(defn bootstrap
  "Create a fully wired world ready to tick.
   opts: {:G double :theta double :dt double :merge? bool}"
  [{:keys [G theta dt merge?]
    :or   {G 6.674e-11 theta 0.5 dt 1.0 merge? false}}]
  (let [world (-> (ecs/empty-world)
                  (event/with-ledger)
                  (event/with-handlers)
                  (event/register-handler :event/collision
                                          (if merge?
                                            response/inelastic-merge-handler
                                            response/elastic-bounce-handler))
                  (assoc :sim/G G :sim/theta theta :sim/dt dt))]
    world))

(defn make-systems
  "Return the ordered system pipeline for one tick.
   Order matters: orbital physics first, then collision detection."
  [{:keys [sim/G sim/theta sim/dt] :as world}]
  [(orbital/orbital-system G theta dt)
   collision/collision-detection-system])

(defn make-timeline
  "Create a rewindable timeline from a bootstrapped world."
  [world]
  (let [G     (:sim/G world)
        theta (:sim/theta world)
        dt    (:sim/dt world)
        fwd   (make-systems world)
        bwd   [(orbital/orbital-system G theta (- dt))]]
    (timeline/->timeline world fwd bwd)))
