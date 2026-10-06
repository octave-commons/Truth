(ns infra.flight-precision-test
  "Wave 0 precision controls, exercised through menu intents and real physics.

   Design: docs/designs/spark-flight-and-camera.md §3.5. The isolated-body
   bounds do not establish native capture of a moving planetary target."
  (:require
   [clojure.math :as math]
   [clojure.test :refer [deftest is testing]]
   [domain.ecs.components :as c]
   [domain.ecs.core :as ecs]
   [domain.ecs.tick :as tick]
   [domain.integrator :as integrator]
   [domain.player :as player]
   [infra.camera :as camera]
   [infra.menu :as menu]
   [law.narrowing :as narrowing]))

(def ^:private displacement-key :genesis/spark-flight-displacement)
(def ^:private fine-displacement 1.0e7)
(def ^:private cruise-displacement 3.0e14)
(def ^:private panel-config
  (assoc (camera/default-camera-settings) :mode :manual :ui/active-domain :spark))

(defn- spark-fixture []
  (player/spawn-observer (ecs/empty-world) [0.0 0.0 0.0]))

(defn- panel [world]
  (menu/menu-hud panel-config world 1280.0 720.0))

(defn- labeled-control [hud label]
  (when-let [text (some #(when (= label (:text %)) %) (:text hud))]
    (when-let [hit (menu/hit-at (:hits hud) (+ (:x text) 2.0) (+ (:y text) 2.0))]
      {:text text :action (:action hit)})))

(defn- displacement-step [world factor]
  (some (fn [{:keys [action]}]
          (when (and (= :spark/knob (first action))
                     (= displacement-key (nth action 2))
                     (= factor (nth action 5)))
            action))
        (:hits (panel world))))

(defn- select-minimum [world]
  ;; The rendered stepper supplies its real clamp and default. No test-only D.
  (let [intent (menu/world-action (displacement-step world 0.5))]
    (nth (iterate intent world) 80)))

(defn- close-to? [expected actual]
  (<= (abs (- expected actual)) (* 1.0e-5 (max 1.0 (abs expected)))))

(defn- x [world eid component]
  (first (ecs/get-component world eid component)))

(defn- physics-step [world dt input]
  (tick/run-parallel
   (player/set-thrust (assoc world :sim/dt dt) input)
   [(player/thrust-acceleration-system) (integrator/integrator-system dt)]))

(defn- advance [world dt input n]
  (reduce (fn [w _] (physics-step w dt input)) world (range n)))

(defn- settling-ticks [retention]
  ;; Dominant root of the released Jacobi response: z²-z+(1-r)=0.
  ;; At r=.999 this requires >16,000 ticks; the old 1,000-tick probe would
  ;; leave a substantial tail. Keep <1e-7 of that mode, plus ten guard ticks.
  (let [root (/ (+ 1.0 (math/sqrt (- 1.0 (* 4.0 (- 1.0 retention))))) 2.0)]
    (+ 10 (long (math/ceil (/ (math/log 1.0e-7) (math/log root)))))))

(deftest cruise-and-fine-are-real-panel-intents
  (let [[world eid] (spark-fixture)
        world (ecs/put-component world eid c/velocity [123.0 -4.0 5.0])
        hud (panel world)]
    (doseq [[label displacement] [["Cruise" cruise-displacement]
                                  ["Fine" fine-displacement]]]
      (let [control (labeled-control hud label)]
        (is (some? control) (str label " must be a visible, clickable Spark control"))
        (when control
          (let [intent (menu/world-action (:action control))]
            (is (fn? intent) "the real action must produce a queued world intent")
            (when intent
              (let [updated (intent world)]
                (is (= displacement (get updated displacement-key)))
                (is (= (assoc world displacement-key displacement) updated)
                    "range selection must preserve every other world value, including momentum and focus")
                (is (= panel-config (menu/apply-action panel-config (:action control)))
                    "range selection must not change the camera or shell mode")
                (is (some #(= (format "%.1e" displacement) (:text %)) (:text (panel updated)))
                    "the numeric setting stays visible")))))))))

(deftest preset-indication-follows-the-actual-value
  (let [[world _] (spark-fixture)
        colors (fn [w]
                 (let [hud (panel w)]
                   (mapv #(get-in (labeled-control hud %) [:text :color]) ["Cruise" "Fine"])))
        default-colors (colors world)
        fine-colors (colors (assoc world displacement-key fine-displacement))
        custom-colors (colors (assoc world displacement-key (* 2.0 fine-displacement)))]
    (is (every? some? default-colors) "both preset labels must be visible")
    (when (every? some? default-colors)
      (is (not= (first default-colors) (second default-colors)) "the default selects Cruise")
      (is (= (reverse default-colors) fine-colors) "Fine selects the other choice")
      (is (= (second default-colors) (first custom-colors) (second custom-colors))
          "custom displacement selects neither preset"))))

(deftest actual-stepper-reaches-fine-without-changing-cruise
  (let [[world _] (spark-fixture)
        floor (select-minimum world)
        up (menu/world-action (displacement-step floor 2.0))
        ceiling (nth (iterate up floor) 80)]
    (is (= cruise-displacement player/default-displacement-per-tick))
    (is (= fine-displacement (get floor displacement-key)) "the actual menu minimum must reach Fine")
    (is (= 1.0e16 (get ceiling displacement-key)) "existing cruise range ceiling is preserved")
    (is (= (dissoc world displacement-key) (dissoc floor displacement-key)))
    (is (= (* 2.0 fine-displacement) (get (up floor) displacement-key))
        "the same half/double stepper remains useful at fine range")))

(deftest actual-menu-minimum-bounds-pulse-and-release-through-jacobi-physics
  (doseq [dt [1.0e7 4.138029443e9 1.0e12]
          retention [0.80 0.97 0.999]]
    (testing (str "constant dt=" dt ", retention=" retention)
      (let [[initial eid] (spark-fixture)
            initial (-> (select-minimum initial)
                        (assoc :genesis/spark-damping-retention retention))
            displacement (get initial displacement-key)
            ticks (settling-ticks retention)
            accepted (physics-step initial dt [1.0 0.0 0.0])
            moving (physics-step accepted dt nil)
            pulse (advance moving dt nil ticks)
            ;; Exact settled held-thrust fixture: v=D/h, net old channel=0.
            ;; All later motion, including eight held ticks, uses production
            ;; systems; there are no position/velocity writes after this setup.
            steady-fixture (ecs/put-components initial eid
                                               {c/velocity [(/ displacement dt) 0.0 0.0]
                                                c/accel-thrust [0.0 0.0 0.0]})
            held (advance steady-fixture dt [1.0 0.0 0.0] 8)
            released (advance held dt nil ticks)
            coast (- (x released eid c/position) (x held eid c/position))
            expected-coast (/ (* retention fine-displacement) (- 1.0 retention))]
        (is (zero? (x accepted eid c/position)) "one accepted intent has the real initial channel delay")
        (is (pos? (x moving eid c/position)) "the next tick consumes the emitted acceleration")
        (is (close-to? (* 8.0 displacement) (x held eid c/position))
            "held thrust maintains the terminal displacement through the real channel fold")
        (is (close-to? fine-displacement (x pulse eid c/position)) "settled one-tick pulse is Fine D")
        (is (< (x pulse eid c/position) (* 0.1 narrowing/world-focus-radius))
            "one fine input pulse fits well inside the planet binding neighborhood")
        (is (close-to? expected-coast coast) "steady release includes the delayed prior channel")
        (is (< coast (* 0.1 narrowing/world-focus-radius)) "fine release coast stays below one tenth radius")
        (is (< (abs (* dt (x released eid c/velocity))) (* 1.0e-5 displacement))
            "the tolerance-based horizon has actually settled")))))

(deftest unequal-timesteps-retain-the-prior-channel-kick
  (testing "characterization only: fixed-dt pulse bounds do not imply adaptive invariance"
    (doseq [ratio [0.5 2.0]]
      (let [[initial eid] (spark-fixture)
            initial (select-minimum initial)
            dt 1.0e7
            alpha (- 1.0 player/default-damping-retention)
            displacement (get initial displacement-key)
            emitted (physics-step initial dt [1.0 0.0 0.0])
            changed (physics-step emitted (* ratio dt) nil)
            returned (physics-step changed dt nil)]
        (is (close-to? (* alpha displacement ratio ratio) (x changed eid c/position))
            "the first moving displacement scales with the squared timestep ratio")
        (is (close-to? (* alpha displacement (+ (* ratio ratio) ratio)) (x returned eid c/position))
            "the following tick carries the velocity before the delayed brake arrives")))))
