;; Pure reproduction of the actual camera -> queued focus -> binding seam.
;; The fixture moves a candidate to supply input frames; no physics is replaced
;; and the live capture service is never accessed. Proposed follow-focus
;; contract: selecting a moving world should target that world for attention.
(require '[domain.ecs.core :as ecs]
         '[domain.ecs.components :as c]
         '[domain.player :as player]
         '[domain.narrowing :as narrowing]
         '[domain.stellar.seeder :as seeder]
         '[infra.camera :as camera]
         '[infra.dev.window.loop :as window-loop]
         '[infra.render.units :as units]
         '[law.narrowing :as law]
         '[shape.spatial :as sp])

(let [[world body] (seeder/spawn-clump (ecs/empty-world)
                                     {:position [0.0 0.0 0.0]
                                      :velocity [0.0 0.0 0.0]
                                      :mass 1.0e24 :radius 6.0e6
                                      :matter-state :planet})
      [world observer] (player/spawn-observer world [0.0 0.0 0.0])
      world (ecs/put-component world body c/planet-candidate {:planet-id body})
      camera0 (assoc (camera/make-camera 10.0) :target [0.0 0.0 0.0])
      settings (assoc (camera/default-camera-settings)
                       :mode :follow-selection :follow-eid body)
      moved (ecs/put-component world body c/position [(* 10.0 law/world-focus-radius) 0.0 0.0])
      camera1 (camera/update-camera-for-world camera0 moved settings)
      context1 (units/make-context camera1 {:width 1280 :height 720})
      focused (@#'window-loop/sync-observer-focus-to-camera moved camera1 context1 :follow-selection)
      target (ecs/get-component moved body c/position)
      focus (:focus-position (player/get-observer focused))
      binding-output ((:run (narrowing/binding-system)) focused)
      ;; A captured camera point also goes stale while the intent is queued.
      exact-camera (assoc camera0 :target (mapv #(/ % camera/phase0-view-scale) target))
      exact-context (units/make-context exact-camera {:width 1280 :height 720})
      advanced (ecs/put-component moved body c/position [(* 20.0 law/world-focus-radius) 0.0 0.0])
      delayed (@#'window-loop/sync-observer-focus-to-camera advanced exact-camera exact-context :follow-selection)
      ;; Existing manual follow resolves the physical observer at application,
      ;; unlike the captured camera coordinates. Preserve this law in a fix.
      near-body (ecs/put-component moved observer c/position
                                  (sp/v+ target [(* 0.5 law/world-focus-radius) 0.0 0.0]))
      manual (player/focus-follow near-body [0.0 0.0 0.0])]
  (prn {:case :selected-body-moves-before-frame
        :expected-selected-world-overlap? true
        :actual-overlap? (narrowing/focus-overlap? focus target law/world-focus-radius)
        :focus-distance-au (/ (sp/dist focus target) law/world-focus-radius)
        :binding-output binding-output
        :spark-position-preserved? (= (ecs/get-component moved observer c/position)
                                     (ecs/get-component focused observer c/position))})
  (prn {:case :exact-render-target-goes-stale-in-intent-queue
        :expected-selected-world-overlap? true
        :actual-overlap? (narrowing/focus-overlap?
                         (:focus-position (player/get-observer delayed))
                         (ecs/get-component delayed body c/position) law/world-focus-radius)
        :focus-distance-au (/ (sp/dist (:focus-position (player/get-observer delayed))
                                       (ecs/get-component delayed body c/position))
                              law/world-focus-radius)})
  (prn {:case :manual-flight-control
        :overlap? (narrowing/focus-overlap? (:focus-position (player/get-observer manual))
                                           target law/world-focus-radius)
        :binding-output ((:run (narrowing/binding-system)) manual)
        :spark-position-preserved? (= (ecs/get-component near-body observer c/position)
                                     (ecs/get-component manual observer c/position))}))
(shutdown-agents)
