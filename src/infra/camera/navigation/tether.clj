(ns infra.camera.navigation.tether
  "The binding camera tether: the gradual auto-tether that follows binding
   depth toward the bound world (The First Narrowing, child C —
   kanban/tasks/narrowing-frame-handoff.md; design
   docs/designs/the-first-narrowing-star-to-planet.md §6, §8.4).

   Pure, in the style of infra.camera.navigation.tracking: given a camera, the
   live ECS world, and options it returns a new camera value. Actuation (the
   per-frame swap!) lives in infra.dev.window.loop.

   Semantics:
   - STRENGTH is `binding / law.narrowing/capture-threshold`, clamped to
     [0,1]. The tether is therefore FULLY engaged exactly when binding reaches
     the capture threshold, so the capture tick itself changes nothing in the
     camera path — there is no jump-cut at capture by construction (the
     `:event/world-commitment` event is never read here; only the continuous
     `c/binding` map is).
   - The pull is a per-frame lerp of the camera target toward the bound
     world's render position and of the orbit distance toward a close framing
     distance, both at rate `(* tether-rate strength)`. Small rates make both
     engagement and RE-engagement gentle: when the player releases the flight
     keys after fighting out to some distance, the tether eases the frame back
     instead of snapping it.
   - FIGHTABLE (design §8.4, 'always player-releasable'): `:input-active?`
     true means the player is flying this frame and input wins outright — the
     camera is returned unchanged. Mouse look never fights: it writes
     yaw/pitch, which the tether never touches, so looking around while the
     frame tightens always works.

   GAPS (honest slice notes):
   - This namespace's own `tether-step` is actuated in :manual mode only
     (`infra.dev.window.loop`). :fit-all, :follow-selection, and
     :track-largest-cluster each compute their own target/distance from the
     live bodies and do NOT reconcile with the binding tether — a prior
     reconciliation (`blend-toward-binding`,
     narrowing-tether-default-camera-modes) unconditionally overrode the
     player's scroll-zoom and produced a target/distance feedback bounce
     while bound (camera-bind-blend-regression-fix); it was removed rather
     than patched. The tightening-frame feel outside :manual returns via the
     camera following the gravity-bound spark, not a camera-side blend.
   - The tether reads the ONE deepest-bound world. Pre-capture oscillation
     between two near-equal candidates would seesaw the pull; the zero-sum
     decay in domain.narrowing makes that state transient, so no smoothing of
     target identity is built here."
  (:require
   [domain.ecs.components :as c]
   [domain.ecs.core :as ecs]
   [domain.narrowing :as narrowing]
   [infra.camera.navigation.input :as input]
   [infra.camera.navigation.tracking :as tracking]
   [infra.camera.projection :as p]
   [shape.spatial :as sp]))

(def ^:const tether-rate
  "Per-frame lerp rate of the tether pull at full engagement (strength 1.0).
   At ~60 fps the half-time of the pull is ~0.23 s — felt as a tightening
   frame, never a cut."
  0.05)

(def ^:const frame-margin
  "Desired orbit distance at full engagement, in world render radii: close
   enough that the bound world fills the frame, far enough not to clip (the
   min-approach floor of 2.5 radii still applies via tracking/min-approach-distance).
   Delegates to `tracking/frame-margin`, the same constant :manual mode uses."
  tracking/frame-margin)

(defn tether-strength
  "Tether engagement in [0,1] for a binding depth `b`: `b / capture-threshold`
   clamped. Reaches 1.0 exactly at the capture threshold, so the tether is
   already fully engaged when `:event/world-commitment` fires — capture is
   not a camera event. Delegates to `domain.narrowing/tether-strength` (the
   shared binding-depth reading; the spark's own spring tether that used to
   share it was deleted in spark-redesign card 4 — the spark is a
   gravity-bound ECS body now)."
  [b]
  (narrowing/tether-strength b))

(defn deepest-binding
  "The [world-eid binding] pair with the greatest binding depth on the
   observer's `c/binding` map, or nil when there is no observer or no binding.
   Delegates to `domain.narrowing/deepest-binding`."
  [world]
  (narrowing/deepest-binding world))

(defn- vlerp
  "Component-wise lerp between 3-vectors a and b by t."
  [a b t]
  (mapv #(+ %1 (* (- %2 %1) t)) a b))

(defn tether-step
  "Advance the binding tether by one frame. Pure: returns a new Camera.

   Returns `camera` unchanged when the player is fighting
   (`:input-active?`), when there is no bound world, or when the bound world
   has no position. Otherwise lerps the target toward the bound world's
   render position and the distance toward `(* frame-margin r-ru)` (floored
   by tracking/min-approach-distance), both at `(* tether-rate strength)`
   where strength is tether-strength of the deepest binding. Every quantity
   is continuous in (binding, camera), so no state of the world — including
   the capture tick — produces a jump."
  [camera world {:keys [input-active?] :as _opts}]
  (if input-active?
    camera
    (if-let [[eid b] (deepest-binding world)]
      (if-let [pos (ecs/get-component world eid c/position)]
        (let [s        (tether-strength b)]
          (if (<= s 0.0)
            camera
            (let [t        (* tether-rate s)
                  target   (mapv #(/ (double %) p/phase0-view-scale) pos)
                  r-ru     (/ (double (or (ecs/get-component world eid c/radius) 0.0))
                              p/phase0-view-scale)
                  desired  (max (* frame-margin r-ru)
                                (tracking/min-approach-distance r-ru))]
              (-> camera
                  (assoc :target (vlerp (:target camera [0.0 0.0 0.0]) target t)
                         :distance (+ (:distance camera)
                                      (* t (- desired (:distance camera)))))
                  input/update-camera-position))))
        camera)
      camera)))
