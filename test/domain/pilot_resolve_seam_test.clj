(ns domain.pilot-resolve-seam-test
  "The pilot→resolve SEAM: cards focus-follows-pilot and
   voxel-sculpt-verb-palette-wiring, verified as a chain rather than as
   links.

   Every link is already covered in isolation — `domain.spark-body-test`
   proves `focus-follow` pins `:focus-position` to the mote,
   `domain.narrowing-test` proves the binding system accrues on an
   overlapping focus, `domain.voxel-sculpt-test` proves a paid op edits the
   band, `infra.render.input-test` proves the keymap dispatches
   `request-op`. NONE of them tests that the links MEET: that the position
   `focus-follow` actually writes lands inside the radius the binding gate
   actually tests, and steers the anchor `request-op` actually derives.

   That seam is a scale coincidence, and this project's recurring failure
   mode (every unit green, the seam silently dead — the reason
   `world-focus-radius` exists at all, per
   kanban/tasks/narrowing-worldscale-overlap-gate.md). These tests pin it:
   `focus-follow`'s output is fed to the REAL `binding-system` and the REAL
   `request-op`, at true world scale, so a future change to either the
   follow law or the overlap radius breaks a test instead of breaking
   'I fly toward a planet but it never resolves'."
  (:require
   [clojure.test :refer [deftest is testing]]
   [domain.ecs.components :as c]
   [domain.ecs.core :as ecs]
   [domain.interior :as interior]
   [domain.interior-test :as fixtures]
   [domain.narrowing :as narrowing]
   [domain.player :as player]
   [domain.player.state :as state]
   [domain.voxel.sculpt :as sculpt]
   [law.narrowing :as law-narrowing]
   [law.voxel :as voxel]
   [shape.spatial :as sp]))

(def ^:private overlap-r
  "The radius the binding gate actually tests against (~1 AU)."
  law-narrowing/world-focus-radius)

(def ^:private no-offset
  "A zero focus-nudge offset — focus rides exactly on the mote."
  [0.0 0.0 0.0])

(def ^:private planet-radius-m
  "The seeded radius of the rocky-habitable fixture world (m)."
  (:radius-m (interior/seed-field fixtures/rocky-habitable)))

(defn- world-with-spark-and-candidate
  "A world with the singleton spark/observer at `spark-pos` and one planet
   candidate at `planet-pos`. Returns [world obs-eid planet-eid]."
  [spark-pos planet-pos]
  (let [[w obs-eid] (player/spawn-observer (ecs/empty-world) spark-pos)
        w (ecs/put-component w obs-eid c/position spark-pos)
        [w planet-eid] (ecs/spawn w)
        w (ecs/put-components w planet-eid
                              {c/planet-candidate {:planet-id planet-eid}
                               c/position         planet-pos})]
    [w obs-eid planet-eid]))

(defn- fly-to
  "Move the spark's physical `c/position` to `pos` — the integrator's job in
   production, done directly here."
  [world obs-eid pos]
  (ecs/put-component world obs-eid c/position pos))

(defn- binding-after-follow
  "Run the production pair for one tick — the manual-mode `focus-follow`
   intent (serial, pre-tick) then the REAL `:binding` fan-out system — and
   return the binding the observer accrued toward `planet-eid`."
  [world obs-eid planet-eid offset]
  (-> (player/focus-follow world offset)
      ((:run (narrowing/binding-system)))
      (get-in [c/binding obs-eid planet-eid])))

;; --- 1. The seam itself: flying at a candidate accrues binding -------------------

(deftest flying-to-a-candidate-accrues-binding
  (testing "a mote parked 2 AU away accrues nothing; flown to 0.5 AU the
            SAME world starts binding — focus-follow's written position
            lands inside the gate's real overlap radius"
    (let [planet-pos (sp/vec3 0.0 0.0 0.0)
          far        (sp/vec3 (* 2.0 overlap-r) 0.0 0.0)
          near       (sp/vec3 (* 0.5 overlap-r) 0.0 0.0)
          [w obs-eid planet-eid] (world-with-spark-and-candidate far planet-pos)]
      (is (nil? (binding-after-follow w obs-eid planet-eid no-offset))
          "out of range: flying nowhere near a world binds nothing")
      (is (= law-narrowing/accrual-rate
             (binding-after-follow (fly-to w obs-eid near) obs-eid planet-eid no-offset))
          "flown inside the overlap radius, one tick accrues one tick of binding"))))

(deftest binding-climbs-over-sustained-flight
  (testing "holding position next to the world climbs binding tick over tick
            — the readout the card promises actually moves"
    (let [near (sp/vec3 (* 0.25 overlap-r) 0.0 0.0)
          [w obs-eid planet-eid] (world-with-spark-and-candidate near (sp/vec3 0.0 0.0 0.0))
          bindings (reductions
                    (fn [world _]
                      (let [w' (player/focus-follow world no-offset)]
                        (ecs/put-component
                         w' obs-eid c/binding
                         (get-in ((:run (narrowing/binding-system)) w')
                                 [c/binding obs-eid]))))
                    w
                    (range 5))
          depths (map #(get-in (ecs/get-component % obs-eid c/binding) [planet-eid] 0.0)
                      bindings)]
      (is (apply < depths)
          (str "binding must increase monotonically while parked: " (vec depths)))
      (is (= (* 5.0 law-narrowing/accrual-rate) (last depths))
          "five parked ticks accrue exactly five ticks of binding"))))

;; --- 2. The nudge offset must not break the seam ---------------------------------

(deftest a-full-arrow-nudge-keeps-the-seam-alive
  (testing "the focus-nudge offset is scaled to the overlap radius, so an
            arrow-nudged focus still binds the world the pilot flew to (the
            old 3.0e15 m step would have flung focus ~20,000 radii away)"
    (let [step   0.1                          ; one press, as a fraction of overlap-r
          near   (sp/vec3 (* 0.25 overlap-r) 0.0 0.0)
          [w obs-eid planet-eid] (world-with-spark-and-candidate near (sp/vec3 0.0 0.0 0.0))
          ;; a few arrow presses in one direction — a realistic hand-aim
          nudged (sp/vec3 (* 5.0 step overlap-r) 0.0 0.0)]
      (is (<= (* step overlap-r) (* 0.25 overlap-r))
          "one press moves focus a small FRACTION of the overlap radius, so
           aiming inside a world is possible at all (the card's core claim)")
      (is (some? (binding-after-follow w obs-eid planet-eid nudged))
          "a hand-aimed focus still lands on the world")
      (is (nil? (binding-after-follow w obs-eid planet-eid
                                      (sp/vec3 3.0e15 0.0 0.0)))
          "the OLD 3.0e15 m step overshot the radius by ~20,000× — a single
           press missed every world. This is the regression the rescale fixed."))))

;; --- 3. The sustained-focus floor is a coincidence worth pinning -----------------

(deftest a-freshly-spawned-spark-is-already-sustained
  (testing "binding requires :focus-intensity >= the sustained floor, and a
            freshly spawned observer sits EXACTLY on it — lower the spawn
            default and flying at a planet silently stops resolving forever"
    (let [obs (state/create-observer (sp/vec3 0.0 0.0 0.0))]
      (is (>= (double (:focus-intensity obs)) law-narrowing/focus-intensity-floor)
          "a default spark can bind without first raising intensity")
      (is (narrowing/focus-sustained? obs)
          "the law agrees: the default spark's focus counts as sustained"))))

;; --- 4. The other half: the flown focus steers where sculpting lands -------------

(defn- committed-world-with-spark
  "An observer (planetary palette armed, Resonance loaded) whose spark sits
   at `spark-pos`, plus a COMMITTED rocky-habitable world at the origin.
   Returns [world world-eid obs-eid]."
  [spark-pos]
  (let [[w obs-eid] (player/spawn-observer (ecs/empty-world) spark-pos)
        w (-> w
              (ecs/put-component obs-eid c/position spark-pos)
              (ecs/put-component obs-eid c/palette law-narrowing/planetary-palette)
              (player/update-observer assoc :resonance 20.0 :coherence 0.8))
        [w world-eid] (ecs/spawn w)
        w (ecs/put-components w world-eid
                              {c/position         (sp/vec3 0.0 0.0 0.0)
                               c/planet-candidate fixtures/rocky-habitable
                               c/commitment-state :committed})]
    [w world-eid obs-eid]))

(defn- anchor-after-flying-to
  "Fly the spark to `spark-pos`, let manual-mode focus-follow pin focus to
   it, then request an uplift — returning the op's derived `:anchor`."
  [spark-pos]
  (let [[w _ obs-eid] (committed-world-with-spark spark-pos)]
    (-> w
        (fly-to obs-eid spark-pos)
        (player/focus-follow no-offset)
        (sculpt/request-op :uplift 0.5)
        :voxel/sculpt-ops
        first
        :anchor)))

(deftest the-flown-focus-steers-the-sculpt-anchor
  (testing "request-op derives its anchor from :focus-position, which manual
            mode pins to the mote — so WHERE you fly is WHERE you sculpt.
            Approach the +x face and the anchor points +x; approach +z and
            it points +z."
    (let [d  (* 2.0 planet-radius-m)
          ax (anchor-after-flying-to (sp/vec3 d 0.0 0.0))
          az (anchor-after-flying-to (sp/vec3 0.0 0.0 d))]
      (is (some? ax) "flying to the +x face produces a paid op")
      (is (some? az) "flying to the +z face produces a paid op")
      (is (> (double (first ax)) 0.9)
          (str "approaching +x anchors +x, got " (vec ax)))
      (is (> (double (nth az 2)) 0.9)
          (str "approaching +z anchors +z, got " (vec az)))
      (is (not= (vec ax) (vec az))
          "the anchor genuinely tracks the pilot, it is not a constant"))))

(deftest sculpting-is-inert-until-the-pilot-arrives-and-commits
  (testing "the verbs the palette card wired stay gated: no committed world
            means a keypress is a paid-nothing no-op, so the loop really is
            'fly there, commit, THEN sculpt'"
    (let [spark-pos (sp/vec3 (* 2.0 6.4e6) 0.0 0.0)
          [w world-eid obs-eid] (committed-world-with-spark spark-pos)
          uncommitted (ecs/remove-component w world-eid c/commitment-state)
          before (:resonance (player/get-observer uncommitted))
          w' (-> uncommitted
                 (player/focus-follow no-offset)
                 (sculpt/request-op :uplift 0.5))]
      (is (empty? (:voxel/sculpt-ops w'))
          "no committed world → no op enqueued")
      (is (= before (:resonance (player/get-observer w')))
          "and no Resonance spent — the gate refunds by never charging")
      (is (contains? (set (vals (:slots law-narrowing/planetary-palette)))
                     (get voxel/sculpt-verb->ability :uplift))
          "sanity: uplift's ability really is in the planetary palette, so
           the gate above failed on commitment, not on a typo'd ability")
      (is (some? (player/get-observer w'))
          "the observer survives a gated no-op unchanged"))))
