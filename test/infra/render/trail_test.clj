(ns infra.render.trail-test
  "Trail projection, bounded coverage, and real line-opacity transport."
  (:require
   [clojure.test :refer [deftest is testing]]
   [domain.ecs.components :as c]
   [domain.ecs.core :as ecs]
   [infra.camera :as camera]
   [infra.render :as render]
   [infra.render.scene.setup :as setup]
   [infra.render.shader :as shader]
   [infra.render.units :as units]
   [law.render :as law]))

(def ^:private trail-component :component/motion-trail)

(defn- api [sym]
  (try (requiring-resolve sym)
       (catch java.io.FileNotFoundException _ nil)))

(defn- near? [a b]
  (< (abs (- (double a) (double b))) 1.0e-6))

(defn- history [times]
  {:samples (mapv (fn [sample-time] {:time sample-time :position [(* 2.0 sample-time) 0.0 0.0]}) times)
   :next-at 100.0 :skipped-deadlines 0})

(defn- trail-world []
  (let [[w eid] (ecs/spawn (assoc (ecs/empty-world) :genesis/sim-time 10.0))
        components {c/matter-state :planet c/body-kind :body/planet
                    c/position [20.0 0.0 0.0] c/radius 6.0e6 c/mass 6.0e24
                    c/temperature 300.0 c/composition {:Fe 1.0}
                    trail-component (history [0.0 4.0])}]
    [(reduce-kv #(ecs/put-component %1 eid %2 %3) w components) eid]))

(deftest pure-geometry-and-line-adapters-exist
  (doseq [sym '[shape.trail/segments shape.trail/budget-segments
                infra.render.scene.trails/project-trails
                infra.render.mesh/make-line-mesh]]
    (is (some? (api sym)) (str "The trail render contract requires " sym))))

(deftest existing-scene-exposes-real-trail-geometry
  (let [[w eid] (trail-world)
        shapes (filter #(= eid (:trail/entity %))
                       (render/phase0-bodies-from-world w 2.0))]
    (is (= 4 (count shapes)) "two adjacent segments including the current head")
    (is (= #{[[0.0 0.0 0.0] [4.0 0.0 0.0]]
             [[4.0 0.0 0.0] [10.0 0.0 0.0]]}
           (set (partition 2 (map :position shapes)))))
    (is (every? #(= :line (:render-mode %)) shapes))))

(deftest opacity-is-a-real-render-contract
  (testing "line shader accepts an opacity value instead of discarding it"
    (is (= :float (get-in shader/line-program [:vertex :inputs :aAlpha])))
    (is (= :float (get-in shader/line-program [:vertex :outputs :vAlpha])))
    (is (= :float (get-in shader/line-program [:fragment :inputs :vAlpha]))))
  (testing "render shape opacity must be a finite fraction"
    (let [shape {:render-mode :line :position [0.0 0.0 0.0] :color [1.0 1.0 1.0]}]
      (is (law/valid-render-shape? (assoc shape :alpha 0.25)))
      (is (not (law/valid-render-shape? (assoc shape :alpha 1.1))))
      (is (not (law/valid-render-shape? (assoc shape :alpha -0.1))))
      (is (not (law/valid-render-shape? (assoc shape :alpha Double/NaN)))))))

(deftest timestamp-fade-connects-current-state-without-invented-points
  (when-let [segments (api 'shape.trail/segments)]
    (let [before (history [0.0 4.0])
          pairs (segments before {:time 10.0 :position [20.0 0.0 0.0]})
          vertices (vec (mapcat identity pairs))]
      (is (= 2 (count pairs)))
      (is (= [[0.0 0.0 0.0] [8.0 0.0 0.0]
              [8.0 0.0 0.0] [20.0 0.0 0.0]] (mapv :position vertices)))
      (is (every? true? (map near? [0.0 0.34 0.34 0.85] (map :alpha vertices))))
      (is (= before (history [0.0 4.0])) "projection did not write history")
      (is (empty? (segments (history [10.0]) {:time 10.0 :position [20.0 0.0 0.0]})))
      (is (empty? (segments (history []) {:time 10.0 :position [20.0 0.0 0.0]}))))))

(deftest projection-scales-then-recenters-raw-vertices-once
  (when-let [project (api 'infra.render.scene.trails/project-trails)]
    (let [[world eid] (trail-world)
          ctx (units/make-context 2.0 (camera/make-camera) {:width 1280 :height 720})
          result (project ctx world)
          shapes (:shapes result)
          shifted (@#'setup/shift-bodies shapes [1.0 2.0 3.0])]
      (is (= 4 (count shapes)))
      (is (every? #(= eid (:trail/entity %)) shapes))
      (is (every? #(= :line (:render-mode %)) shapes))
      (is (= #{[[-1.0 -2.0 -3.0] [3.0 -2.0 -3.0]]
               [[3.0 -2.0 -3.0] [9.0 -2.0 -3.0]]}
             (set (partition 2 (map :position shifted)))))
      (is (= {:requested 2 :rendered 2 :dropped 0} (:summary result))))))

(deftest budget-keeps-pairs-and-shares-newest-coverage
  (when-let [budget (api 'shape.trail/budget-segments)]
    (let [point (fn [x] {:position [x 0.0 0.0] :alpha 0.5})
          body (fn [eid spark?]
                 {:entity eid :spark? spark?
                  :segments [[(point 0.0) (point 1.0)]
                             [(point 1.0) (point 2.0)]
                             [(point 2.0) (point 3.0)]]})
          bodies [(body 20 false) (body 30 true) (body 10 false)]
          result (budget bodies 4)
          selected (:segments result)]
      (is (= {:requested 9 :rendered 4 :dropped 5} (:summary result)))
      (is (= [30 10 20 30] (mapv :entity selected)))
      (is (every? #(= 2 (count (:vertices %))) selected))
      (is (= [3.0 3.0 3.0 2.0]
             (mapv #(first (:position (second (:vertices %)))) selected)))
      (is (= result (budget (reverse bodies) 4)) "stable allocation across map/query order")
      (is (= {:requested 9 :rendered 9 :dropped 0}
             (:summary (budget bodies 4096)))))))

(deftest global-default-segment-budget-is-enforced
  (when-let [project (api 'infra.render.scene.trails/project-trails)]
    (let [world (reduce
                 (fn [w _]
                   (let [[w eid] (ecs/spawn w)]
                     (-> w
                         (ecs/put-component eid c/matter-state :planet)
                         (ecs/put-component eid c/position [128.0 0.0 0.0])
                         (ecs/put-component eid trail-component
                                            (history (mapv double (range 64)))))))
                 (assoc (ecs/empty-world) :genesis/sim-time 64.0)
                 (range 70))
          result (project (units/make-context 1.0 (camera/make-camera)
                                              {:width 1280 :height 720}) world)]
      (is (= {:requested 4480 :rendered 4096 :dropped 384} (:summary result)))
      (is (= 8192 (count (:shapes result))))
      (is (= 70 (count (set (map :trail/entity (:shapes result)))))))))

(deftest line-buffer-carries-opacity-and-preserves-legacy-default
  (when-let [make-mesh (api 'infra.render.mesh/make-line-mesh)]
    (let [{:keys [buffer] vertex-count :count}
          (make-mesh [{:position [1.0 2.0 3.0] :color [0.1 0.2 0.3] :alpha 0.25}
                      {:position [4.0 5.0 6.0] :color [0.4 0.5 0.6]}])
          values (vec (repeatedly (.remaining buffer) #(.get buffer)))]
      (is (= 2 vertex-count))
      (is (= 14 (clojure.core/count values)) "position3 + RGB3 + opacity1 per line vertex")
      (is (every? true? (map near? [1.0 2.0 3.0 0.1 0.2 0.3 0.25
                                    4.0 5.0 6.0 0.4 0.5 0.6 0.85] values))))))
