(ns domain.physics.collision-response
  "Event handlers for :event/collision.
   Register these with domain.ecs.event/register-handler.

   Two built-in responses:
   - elastic-bounce-handler: conserves momentum + KE
   - inelastic-merge-handler: merges two bodies into one"
  (:require
    [domain.ecs.core       :as ecs]
    [domain.ecs.components :as c]
    [domain.ecs.event      :as event]
    [shape.spatial         :as sp]))

(defn- get-body
  "Pull position, velocity, mass for eid from world."
  [world eid]
  {:position (ecs/get-component world eid c/position)
   :velocity (ecs/get-component world eid c/velocity)
   :mass     (double (ecs/get-component world eid c/mass))})

(defn- put-body
  "Write position and velocity for eid back into world."
  [world eid {:keys [position velocity]}]
  (-> world
      (ecs/put-component eid c/position position)
      (ecs/put-component eid c/velocity velocity)))

(defn- separate-bodies
  "Push A and B apart along normal so they no longer overlap."
  [world eid-a eid-b normal depth mass-a mass-b]
  (let [total-inv-mass (+ (/ 1.0 mass-a) (/ 1.0 mass-b))
        correction     (sp/v* normal (/ depth total-inv-mass))
        pos-a          (ecs/get-component world eid-a c/position)
        pos-b          (ecs/get-component world eid-b c/position)
        pos-a' (sp/v- pos-a (sp/v* correction (/ 1.0 mass-a)))
        pos-b' (sp/v+ pos-b (sp/v* correction (/ 1.0 mass-b)))]
    (-> world
        (ecs/put-component eid-a c/position pos-a')
        (ecs/put-component eid-b c/position pos-b'))))

(defn elastic-bounce-handler
  "Handle :event/collision with a perfectly elastic impulse response."
  [world event]
  (let [{:keys [eid-a eid-b normal depth]} (:payload event)
        ba (get-body world eid-a)
        bb (get-body world eid-b)
        va    (:velocity ba) ma (double (:mass ba))
        vb    (:velocity bb) mb (double (:mass bb))
        rel-v (sp/v- va vb)
        vn    (sp/dot rel-v normal)]
    (if (>= vn 0.0)
      world
      (let [j       (/ (* -2.0 vn) (+ (/ 1.0 ma) (/ 1.0 mb)))
            impulse (sp/v* normal j)
            va'     (sp/v+ va (sp/v* impulse (/ 1.0 ma)))
            vb'     (sp/v- vb (sp/v* impulse (/ 1.0 mb)))
            world'  (separate-bodies world eid-a eid-b normal
                                     (double depth) ma mb)]
        (-> world'
            (ecs/put-component eid-a c/velocity va')
            (ecs/put-component eid-b c/velocity vb'))))))

(defn inelastic-merge-handler
  "Handle :event/collision by merging the smaller body into the larger."
  [world event]
  (let [{:keys [eid-a eid-b]} (:payload event)
        ba (get-body world eid-a)
        bb (get-body world eid-b)
        ma (double (:mass ba))
        mb (double (:mass bb))
        [eid-large eid-small bl bs ml ms]
        (if (>= ma mb)
          [eid-a eid-b ba bb ma mb]
          [eid-b eid-a bb ba mb ma])
        total-mass (+ ml ms)
        p          (sp/v+ (sp/v* (:velocity bl) ml)
                          (sp/v* (:velocity bs) ms))
        v'         (sp/v* p (/ 1.0 total-mass))
        rl         (double (ecs/get-component world eid-large c/radius))
        rs         (double (ecs/get-component world eid-small c/radius))
        r'         (Math/cbrt (+ (* rl rl rl) (* rs rs rs)))]
    (-> world
        (ecs/put-component eid-large c/mass   total-mass)
        (ecs/put-component eid-large c/radius r')
        (ecs/put-component eid-large c/velocity v')
        (ecs/despawn eid-small))))
