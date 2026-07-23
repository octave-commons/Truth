(ns domain.spark-body-test
  "Spark-redesign card 4 (kanban/tasks/spark-as-gravity-bound-body.md): the
   spark is a real, gravity-bound ECS body.

   - spawn-observer writes the first-class columns (c/position c/velocity
     c/mass c/radius c/body-kind :spark) and deliberately NO
     c/matter-state/c/accretion-radius/c/composition.
   - Gravity and the integrator move it with no special case.
   - c/mass and c/radius are re-derived each tick from
     :genesis/formation-progress by the EXISTING writers' body-kind :spark
     branches (single-writer: :integrator owns c/mass, :structure owns
     c/radius).
   - c/position is the single live position source; the c/observer map
     carries no :position key."
  (:require
   [clojure.test :refer [deftest testing is]]
   [domain.ecs.components :as c]
   [domain.ecs.core :as ecs]
   [domain.ecs.registry :as reg]
   [domain.ecs.tick :as tick]
   [domain.genesis :as genesis]
   [domain.integrator.core :as intcore]
   [domain.player :as player]
   [domain.stellar.geometry :as geometry]
   [law.spark :as law-spark]
   [shape.spatial :as sp]))

;; --- Spawn: first-class columns, matter-state exclusions --------------------

(deftest spark-spawns-as-a-real-body
  (let [[w eid] (player/spawn-observer (ecs/empty-world) (sp/vec3 1.0e15 0.0 0.0))]
    (testing "the physical columns exist and are the progress-0 resolve values"
      (is (= [1.0e15 0.0 0.0] (ecs/get-component w eid c/position)))
      (is (= [0.0 0.0 0.0] (ecs/get-component w eid c/velocity)))
      (is (== 0.0 (ecs/get-component w eid c/mass))
          "pre-formation mass is explicitly 0 — a test particle")
      (is (== law-spark/default-initial-radius (ecs/get-component w eid c/radius))
          "pre-formation radius is the large diffuse extent")
      (is (= :spark (ecs/get-component w eid c/body-kind))))
    (testing "NO matter-state/accretion-radius/composition — auto-excluded from
              collision, hydro, classifier, sink-formation, disc evolution"
      (is (nil? (ecs/get-component w eid c/matter-state)))
      (is (nil? (ecs/get-component w eid c/accretion-radius)))
      (is (nil? (ecs/get-component w eid c/composition))))
    (testing "the observer map carries no :position key — one live source"
      (is (not (contains? (player/get-observer w) :position)))
      (is (= [1.0e15 0.0 0.0] (player/observer-position w))
          "observer-position reads the c/position column"))))

;; --- Gravity moves it (end-to-end through the real tick) ---------------------

(deftest gravity-acts-on-the-spark
  (testing "a displaced spark falls into the dark-matter halo well at the
            origin through the ordinary gravity + kinematics path (gas
            gravity frozen so the scripted run is deterministic; the halo is
            a fixed field, independent of :sim/G)"
    (let [w0 (-> (genesis/create-world {:gas-count 4})
                 (assoc :sim/G 0.0
                        :genesis/adaptive-pacing? false))
          obs-eid (player/observer-entity w0)
          _ (is (some? obs-eid) "create-world spawns the observer")
          w0 (ecs/put-component w0 obs-eid c/position (sp/vec3 1.0e15 0.0 0.0))
          ticks (iterate genesis/tick-world w0)
          dist #(sp/len (player/observer-position %))
          ;; tick 0->1 is a pure COM frame-shift (Jacobi lag: this tick's
          ;; accels apply from tick 2), so the infall baseline is tick 1.
          d1 (dist (nth ticks 1))
          d4 (dist (nth ticks 4))
          d8 (dist (nth ticks 8))
          vx8 (first (ecs/get-component (nth ticks 8) obs-eid c/velocity))]
      (is (< d8 d4 d1)
          "the spark's distance from the well centre shrinks tick over tick — gravity moved it")
      (is (neg? vx8)
          "the integrator accumulated halo acceleration into c/velocity"))))

;; --- Resolve interpolation: the body-kind :spark branches ---------------------

(defn- spark-world
  "An empty world with a spark and `:genesis/formation-progress` set to `p`."
  [p]
  (let [[w eid] (player/spawn-observer (ecs/empty-world) (sp/vec3 0.0 0.0 0.0))]
    [(assoc w :genesis/formation-progress (double p)) eid]))

(deftest mass-branch-interpolates-on-formation-progress
  (testing "the :integrator mass writer's :spark branch tracks progress"
    (let [[w eid] (spark-world 0.5)
          ws (intcore/mass-ws w)
          target (law-spark/spark-mass 0.5 law-spark/default-final-mass)]
      (is (== target (get-in ws [c/mass eid]))))
    (testing "at progress 0 the branch emits nothing (mass already 0)"
      (let [[w eid] (spark-world 0.0)
            ws (intcore/mass-ws w)]
        (is (nil? (get-in ws [c/mass eid])))))
    (testing "world override :genesis/spark-final-mass wins over the law default"
      (let [[w eid] (spark-world 1.0)
            w (assoc w :genesis/spark-final-mass 5.0e21)
            ws (intcore/mass-ws w)]
        (is (== 5.0e21 (get-in ws [c/mass eid])))))
    (testing "plateau-then-step inputs yield monotonic outputs, no oscillation"
      (let [masses (mapv (fn [p]
                           (let [[w eid] (spark-world p)]
                             (get-in (intcore/mass-ws w) [c/mass eid]
                                     (ecs/get-component w eid c/mass))))
                         [0.0 0.0 0.3 0.3 0.3 0.7 0.7 1.0])]
        (is (apply <= masses))))))

(deftest radius-branch-interpolates-on-formation-progress
  (testing "the :structure writer's :spark branch tracks progress"
    (let [[w eid] (spark-world 0.5)
          ws ((:run (geometry/structure-system)) w)
          target (law-spark/spark-radius 0.5
                                         law-spark/default-initial-radius
                                         law-spark/default-final-radius)]
      (is (== target (get-in ws [c/radius eid]))))
    (testing "at progress 0 the branch emits nothing (radius already initial)"
      (let [[w eid] (spark-world 0.0)
            ws ((:run (geometry/structure-system)) w)]
        (is (nil? (get-in ws [c/radius eid])))))
    (testing "the radius shrinks monotonically as progress rises"
      (let [radii (mapv (fn [p]
                          (let [[w eid] (spark-world p)]
                            (get-in ((:run (geometry/structure-system)) w) [c/radius eid]
                                    (ecs/get-component w eid c/radius))))
                        [0.0 0.25 0.5 0.5 0.75 1.0])]
        (is (apply >= radii))
        (is (== law-spark/default-final-radius (last radii))
            "fully formed: the small dense moonlet")))))

(deftest resolve-curve-pure-endpoints
  (testing "law.spark clamps and hits the documented endpoints"
    (is (== 0.0 (law-spark/spark-mass 0.0 1.0e20)))
    (is (== 1.0e20 (law-spark/spark-mass 1.0 1.0e20)))
    (is (== 0.0 (law-spark/spark-mass -1.0 1.0e20)) "clamped below 0")
    (is (== 1.0e20 (law-spark/spark-mass 2.0 1.0e20)) "clamped above 1")
    (is (== 1.0e12 (law-spark/spark-radius 0.0 1.0e12 4.0e5)))
    (is (== 4.0e5 (law-spark/spark-radius 1.0 1.0e12 4.0e5)))))

;; --- Single-writer ------------------------------------------------------------

(deftest single-writer-holds-for-spark-columns
  (testing "c/mass and c/radius each still have exactly ONE writer — the spark
            branches folded into the existing systems, no new system"
    (is (= {} (reg/write-conflicts reg/systems)))
    (is (= [:integrator] (get (reg/writers-by-component reg/systems) c/mass)))
    (is (= [:structure] (get (reg/writers-by-component reg/systems) c/radius)))))

;; --- WASD drift composes with gravity -----------------------------------------

(deftest drift-writes-the-position-column-only
  (testing "manual flight translates c/position directly and leaves c/velocity
            to the integrator — thrust and gravity add, nothing fights"
    (let [[w eid] (player/spawn-observer (ecs/empty-world) (sp/vec3 0.0 0.0 0.0))
          w (ecs/put-component w eid c/velocity (sp/vec3 7.0 0.0 0.0))
          w' (player/drift w (sp/vec3 0.0 1.0e15 0.0) 0.5)]
      (is (= [0.0 5.0e14 0.0] (player/observer-position w'))
          "position moved by velocity * dt")
      (is (= [7.0 0.0 0.0] (ecs/get-component w' eid c/velocity))
          "velocity column untouched by input — gravity's integral is preserved")
      (is (not (contains? (player/get-observer w') :position))
          "still no shadow position in the observer map"))))

;; --- Load-time repair hook ----------------------------------------------------

(deftest repair-observer-columns-restores-legacy-worlds
  (let [legacy-pos [3.0e15 0.0 0.0]
        [w eid] (ecs/spawn (ecs/empty-world))
        ;; a pre-card-4 world: observer map carries a shadow :position, no columns
        w (ecs/put-component w eid c/observer
                             (assoc (player/create-observer legacy-pos) :position legacy-pos))
        w' (player/repair-observer-columns w)]
    (testing "the columns are seeded, position from the legacy map key"
      (is (= legacy-pos (ecs/get-component w' eid c/position)))
      (is (= [0.0 0.0 0.0] (ecs/get-component w' eid c/velocity)))
      (is (== 0.0 (ecs/get-component w' eid c/mass)))
      (is (== law-spark/default-initial-radius (ecs/get-component w' eid c/radius)))
      (is (= :spark (ecs/get-component w' eid c/body-kind))))
    (testing "the legacy shadow :position is stripped — one live source"
      (is (not (contains? (player/get-observer w') :position))))
    (testing "idempotent: a second pass changes nothing"
      (is (= w' (player/repair-observer-columns w'))))
    (testing "healthy worlds pass through untouched"
      (let [[hw _] (player/spawn-observer (ecs/empty-world) [1.0 0.0 0.0])]
        (is (= hw (player/repair-observer-columns hw)))))
    (testing "worlds without an observer pass through"
      (is (= (ecs/empty-world) (player/repair-observer-columns (ecs/empty-world)))))))

;; --- The halo does not pull the spark itself -----------------------------------

(deftest halo-excludes-the-spark-itself
  (testing "the observer-accel emitter never writes accel.observer on the
            observer entity — a body does not pull itself"
    (let [focus (sp/vec3 0.0 0.0 0.0)
          [w obs-eid] (player/spawn-observer (ecs/empty-world) focus)
          w (player/update-observer w #(player/set-focus % focus 1.0e15 0.5))
          [w b] (ecs/spawn w)
          w (ecs/put-components w b {c/position (sp/vec3 1.0e15 0.0 0.0) c/mass 1.0e28})
          w (assoc w :sim/dt 1.0e12 :tick 1)
          ws ((:run (player/observer-acceleration-system)) w)
          applied (tick/apply-write-set w ws)]
      (is (some? (ecs/get-component applied b c/accel-observer))
          "an ordinary body in reach still feels the halo")
      (is (nil? (ecs/get-component applied obs-eid c/accel-observer))
          "the spark itself is excluded"))))
