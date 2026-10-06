;; Reproduce the existing formation-placement-v2 materialization defects.
;; Run from repository root:
;; clojure -Sdeps '{:paths ["src" "resources" "test"]}' -M .ημ/diagnostics/playable-foundation/formation-spawn-frame-probe.clj
;; This is an isolated deterministic ECS seam probe, not a natural formation run.
(require '[domain.disk-evolution-test]
         '[domain.genesis :as genesis]
         '[domain.ecs.core :as ecs]
         '[domain.ecs.components :as c]
         '[domain.planet-formation.orbit :as orbit]
         '[domain.orbital.stability :as stability]
         '[law.stellar :as law]
         '[law.composition :as composition]
         '[shape.spatial :as sp])

(doseq [[disk-fraction label] [[0.8 :moving-host-gi-fragment]
                                [1.2 :moving-host-binary-companion]]]
  (let [[world host] (@#'domain.disk-evolution-test/fragmenting-ws
                      (* disk-fraction law/solar-mass) (* 10.0 law/au) {})
        request (first (ecs/get-component world host c/spawn-request-disk))
        moved (ecs/put-component world host c/position [(* 1000.0 law/au) 0.0 0.0])
        result (genesis/materialize-lifecycle moved)
        child (first (remove #{host} (ecs/entities-with result c/matter-state)))
        relative (sp/v- (ecs/get-component result child c/position)
                        (ecs/get-component result host c/position))
        velocity (sp/v- (ecs/get-component result child c/velocity)
                        (ecs/get-component result host c/velocity))]
    (prn {:case label
          :request-parent (:spawn-parent request)
          :request-radius-au (/ (sp/len (:position request)) law/au)
          :actual-radius-au (/ (sp/len relative) law/au)
          :elements (stability/two-body-elements relative velocity (* law/G law/solar-mass))})))

(let [[world host] (@#'domain.disk-evolution-test/star-with-disk
                    (* 0.1 law/solar-mass) (* 10.0 law/au))
      request (orbit/build-planet-spec
               {:r law/au :mass-kg law/earth-mass :ptype :rocky
                :composition composition/solar-composition :tick 1000 :star host}
               {:L-star law/solar-luminosity :pos [0.0 0.0 0.0] :vel [0.0 0.0 0.0]
                :axis [0.0 0.0 1.0] :M-star law/solar-mass :softening 0.0})
      moved (-> world
                (ecs/put-component host c/position [(* 1000.0 law/au) 0.0 0.0])
                (ecs/put-component host c/spawn-request-planet [request])
                (assoc :genesis/frame-offset [(* 10.0 law/au) 0.0 0.0]))
      result (genesis/materialize-lifecycle moved)
      child (first (remove #{host} (ecs/entities-with result c/matter-state)))
      relative (sp/v- (ecs/get-component result child c/position)
                      (ecs/get-component result host c/position))]
  (prn {:case :parent-relative-seed-with-frame-shift
        :expected-relative (:rel-position request)
        :actual-relative relative
        :error-au (/ (sp/dist relative (:rel-position request)) law/au)}))

(shutdown-agents)
