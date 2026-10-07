(ns domain.warp-lifecycle-test
  "Paid warp lifetimes through the production emitter, fold and integrator."
  (:require
   [clojure.test :refer [deftest is testing]]
   [domain.ecs.components :as c]
   [domain.ecs.core :as ecs]
   [domain.ecs.tick :as tick]
   [domain.integrator :as integrator]
   [domain.intervention :as iv]
   [domain.physics.cache :as cache]
   [domain.player :as player]
   [shape.spatial :as sp]))

(def ^:private zero3 [0.0 0.0 0.0])
(def ^:private background [0.0 0.125 0.0])

(defn- body [world position velocity]
  (let [[world eid] (ecs/spawn world)]
    [(ecs/put-components world eid
                         {c/position position c/velocity velocity c/mass 1.0 c/radius 1.0
                          c/body-kind :gas
                          c/accel-gravity background})
     eid]))

(defn- paid-world [kind ttl]
  (let [[world _] (player/spawn-observer (ecs/empty-world) [1000.0 0.0 0.0])
        funded (-> world
                   (assoc :sim/dt 1.0 :genesis/nebula-mass 1.0e10
                          :genesis/nebula-radius 100.0)
                   (player/update-observer assoc :agency 100.0))]
    (iv/place funded kind zero3 {:radius 10.0 :ttl ttl})))

(defn- physics-step [world soa?]
  ;; Match the production fresh-cache → parallel emit/fold → expiry barrier.
  ;; Deliberately isolate warp from other emitters, retaining an independent
  ;; gravity contribution to prove clearing one owner does not erase another.
  (let [snapshot (cond-> world soa? cache/build-physics-soa)]
    (-> (tick/run-parallel snapshot [(iv/warp-acceleration-system)
                                     (integrator/integrator-system 1.0)])
        cache/strip-physics-soa
        (update :tick inc)
        iv/expire-interventions)))

(defn- value [world eid component]
  (ecs/get-component world eid component))

(defn- close-vector? [expected actual]
  (and (= 3 (count actual))
       (every? true? (map #(<= (abs (- %1 %2)) 1.0e-10) expected actual))))

(deftest paid-warp-expiry-and-removal-clear-the-folded-channel
  (doseq [soa? [false true]
          kind [:warp/well :warp/repulsor]
          removal [:expiry :remove]]
    (testing (str {:soa? soa? :kind kind :removal removal})
      (let [[world eid] (body (paid-world kind (if (= :expiry removal) 1 100))
                              [5.0 0.0 0.0] zero3)
            emitted (physics-step world soa?)
            carried (value emitted eid c/accel-warp)
            disabled (if (= :remove removal)
                       (assoc emitted :genesis/interventions [])
                       emitted)
            cleared (physics-step disabled soa?)
            coast (physics-step cleared soa?)]
        (is (= 85.0 (:agency (player/get-observer world))) "real placement pays the unchanged cost")
        (is (if (= :warp/well kind) (neg? (first carried)) (pos? (first carried)))
            "a paid well pulls and a paid repulsor pushes")
        (is (= background (value emitted eid c/velocity))
            "new emission cannot kick the same frozen tick")
        (is (empty? (:genesis/interventions disabled)))
        (is (close-vector? (-> (value disabled eid c/velocity) (sp/v+ background) (sp/v+ carried))
                           (value cleared eid c/velocity))
            "the prior channel still supplies its legitimate final Jacobi kick")
        (is (nil? (value cleared eid c/accel-warp))
            "the first disabled fold removes the stored contribution")
        (is (not (contains? (get-in cleared [:archetypes eid]) c/accel-warp))
            "removal also updates the real ECS archetype")
        (is (= (get-in world [:components c/accel-gravity])
               (get-in coast [:components c/accel-gravity]))
            "the other owner's contribution survives")
        (is (close-vector? (sp/v+ (value cleared eid c/velocity) background)
                           (value coast eid c/velocity))
            "subsequent motion receives no repeated ghost kick")
        (is (close-vector? (sp/v+ (value cleared eid c/position)
                                  (value coast eid c/velocity))
                           (value coast eid c/position)))
        (is (= 85.0 (:agency (player/get-observer coast))) "expiry does not charge again")))))

(deftest a-moving-recipient-leaves-a-live-warp-without-stranding-force
  (doseq [soa? [false true]
          kind [:warp/well :warp/repulsor]]
    (testing (str {:soa? soa? :kind kind})
      (let [[world departing] (body (paid-world kind 100) [27.0 0.0 0.0] [2.0 0.0 0.0])
            [world remaining] (body world [5.0 0.0 0.0] zero3)
            first-fold (physics-step world soa?)
            ;; After three seconds the departing body has crossed the 30 m
            ;; reach in both cached-prediction and snapshot-position paths.
            outside (nth (iterate #(physics-step % soa?) first-fold) 3)
            coast (physics-step outside soa?)]
        (is (some? (value first-fold departing c/accel-warp)))
        (is (some? (value first-fold remaining c/accel-warp)))
        (is (> (sp/len (value outside departing c/position)) 30.0))
        (is (seq (:genesis/interventions outside)) "the paid well itself remains active")
        (is (nil? (value outside departing c/accel-warp)) "only the departing recipient clears")
        (is (pos? (sp/len (value outside remaining c/accel-warp)))
            "the in-range recipient retains a live force")
        (is (close-vector? (sp/v+ (value outside departing c/velocity) background)
                           (value coast departing c/velocity)))
        (is (close-vector? (-> (value outside remaining c/velocity)
                               (sp/v+ background)
                               (sp/v+ (value outside remaining c/accel-warp)))
                           (value coast remaining c/velocity))
            "the same integrator continues to consume the valid active force")
        (is (= (get-in world [:components c/accel-gravity])
               (get-in coast [:components c/accel-gravity])))))))

(deftest active-paid-warps-retain-their-decaying-force
  (doseq [soa? [false true]
          kind [:warp/well :warp/repulsor]]
    (testing (str {:soa? soa? :kind kind})
      (let [[world eid] (body (paid-world kind 100) [5.0 0.0 0.0] zero3)
            first-fold (physics-step world soa?)
            second-fold (physics-step first-fold soa?)
            first-force (value first-fold eid c/accel-warp)
            second-force (value second-fold eid c/accel-warp)]
        (is (seq (:genesis/interventions second-fold)))
        (is (< 0.0 (sp/len second-force) (sp/len first-force))
            "the existing finite lifetime continues to fade a live contribution")
        (is (if (= :warp/well kind) (neg? (first second-force)) (pos? (first second-force))))
        (is (close-vector? (-> background (sp/v+ background) (sp/v+ first-force))
                           (value second-fold eid c/velocity)))
        (is (= 85.0 (:agency (player/get-observer second-fold))))))))
