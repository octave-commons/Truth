(ns infra.render-test
  "Tests for the single Phase 0 render projection (infra.render). These cover the
   pure geometry/colour fns that turn the ECS world into render shapes — regime
   tinting, volumetric fog, and magnetic field lines. GL calls are not exercised."
  (:require
   [clojure.math :as math]
   [clojure.test :refer [deftest testing is]]
   [domain.ecs.core :as ecs]
   [domain.ecs.components :as c]
   [domain.stellar :as stellar]
   [domain.genesis :as genesis]
   [domain.player :as player]
   [infra.camera :as cam]
   [infra.input :as input]
   [infra.render :as r]
   [infra.render.field :as rfield]
   [infra.render.units :as units]))

(deftest test-tint-color
  (testing "Tinting keeps colours in [0,1] and shifts by regime"
    (is (every? #(<= 0.0 % 1.0) (r/tint-color [0.8 0.6 0.9] :mhd-dominated)))
    (is (= [0.55 0.45 0.75] (r/tint-color [0.55 0.45 0.75] :gravity-hydro))
        "gravity-hydro is the neutral tint")
    (let [warm (r/tint-color [0.5 0.5 0.5] :gravitationally-unstable)]
      (is (> (first warm) (nth warm 2)) "collapsing clumps read warmer (red > blue)"))))

(deftest test-field-line
  (testing "A clump with a field yields two endpoints straddling its centre"
    (let [seg (r/field-line [0.0 0.0 0.0] 1.0 [0.0 0.0 1.0e-9])]
      (is (= 2 (count seg)))
      (is (every? #(= :line (:render-mode %)) seg))
      (is (neg? (nth (:position (first seg)) 2)))
      (is (pos? (nth (:position (second seg)) 2)))))
  (testing "A stronger (amplified) field draws a brighter line"
    (let [weak   (r/field-line [0.0 0.0 0.0] 1.0 [0.0 0.0 1.0e-9])
          strong (r/field-line [0.0 0.0 0.0] 1.0 [0.0 0.0 1.0e-3])]
      (is (> (nth (:color (first strong)) 2) (nth (:color (first weak)) 2)))))
  (testing "No field means no line"
    (is (nil? (r/field-line [0.0 0.0 0.0] 1.0 [0.0 0.0 0.0])))))

(deftest test-nebula-fog
  (testing "Fog puffs are tagged :particle and lie within the extent"
    (let [fog (r/nebula-fog {:center [0.0 0.0 0.0] :extent 5.0
                             :color [0.5 0.4 0.7] :count 50})]
      (is (= 50 (count fog)))
      (is (every? #(= :particle (:render-mode %)) fog))
      (is (every? #(pos? (:size %)) fog))
      (is (every? #(<= (math/sqrt (apply + (map * (:position %) (:position %)))) 5.0001) fog)))))

(deftest test-phase0-projection
  (testing "Gas contributes to froxel volume, protostar → body + field line, star → shaded body"
    (let [[w1 _] (stellar/spawn-clump (ecs/empty-world)
                                      {:position [0.0 0.0 0.0] :mass 1e28 :radius 1e13
                                       :matter-state :nebula})
          [w2 _] (stellar/spawn-clump w1
                                      {:position [2e15 0.0 0.0] :mass 2e30 :radius 1e14
                                       :matter-state :protostar})
          [w3 _] (stellar/spawn-clump w2
                                      {:position [4e16 0.0 0.0] :mass 2e30 :radius 1e9
                                       :matter-state :star})
          shapes (r/phase0-bodies-from-world w3)
          modes  (frequencies (map :render-mode shapes))]
      (is (pos? (get modes :body 0))     "protostar + star produce shaded bodies")
      (is (pos? (get modes :line 0))     "the protostar produces magnetic field lines"))))

(deftest test-nebula-density-visualization
  (testing "Froxel gas samples carry density in volume builder"
    (let [base (ecs/empty-world)
          [w1 _] (stellar/spawn-clump base
                                      {:position [0.0 0.0 0.0] :mass 1e28 :radius 1e14
                                       :matter-state :nebula :density 1e-18 :temperature 12.0})
          ctx  (units/make-context (cam/make-camera) {:width 1 :height 1})
          pts  (#'r/render-samples ctx w1 rfield/default-volume-config)]
      (is (seq pts) "nebula produces gas samples for the froxel texture")
      (is (every? #(number? (:dens %)) pts)
          "every gas sample carries a density value")))
  (testing "Higher-density samples read smaller (they sample a tighter area)"
    (let [sparse (r/nebula-fog {:center [0.0 0.0 0.0] :extent 1.0 :support 1.0
                                :color [1.0 1.0 1.0] :count 50 :seed 7 :density 0.1})
          dense  (r/nebula-fog {:center [0.0 0.0 0.0] :extent 1.0 :support 1.0
                                :color [1.0 1.0 1.0] :count 50 :seed 7 :density 0.9})
          mean (fn [xs] (/ (reduce + xs) (count xs)))]
      (is (> (mean (map :size sparse)) (mean (map :size dense)))
          "low-density fog puffs are larger than high-density puffs")))
  (testing "Temperature colour varies with temperature"
    (let [cold (r/temp-color 10.0)
          hot  (r/temp-color 1e4)]
      (is (< (first cold) (first hot)) "hot gas reads redder/warmer than cold gas")
      (is (not= cold hot) "different temperatures produce different colours"))))

;; --- Physics-coupled size and colour -----------------------------------------

(deftest test-phys->render-radius
  (testing "Render radius rises with physical radius but stays log-compressed"
    (let [ctx    (units/make-context (cam/make-camera) {:width 1 :height 1})
          gas    (units/phys->render-radius ctx 6e13)
          planet (units/phys->render-radius ctx 3e14)
          giant  (units/phys->render-radius ctx 3e15)]
      (is (< gas planet giant) "bigger physical body → bigger on screen")
      (is (>= gas 0.001) "tiny bodies clamp to a visible minimum")
      (is (<= giant 60.0) "huge bodies are log-compressed rather than linear")
      (is (< giant 100.0) "even a giant stays modest on screen")))
  (testing "Non-positive radius is the visible minimum, never zero/NaN"
    (let [ctx (units/make-context (cam/make-camera) {:width 1 :height 1})]
      (is (= 0.001 (units/phys->render-radius ctx 0.0)))
      (is (= 0.001 (units/phys->render-radius ctx nil))))))

(deftest test-composition->material-color
  (testing "Element-resolved composition drives material colour via bulk categories"
    ;; Fe/Si condense to rock+metal below ~1300 K; O freezes to ice below 170 K;
    ;; H/He stay gas. Colour is derived from the condensation partition, not from
    ;; retired :metals/:ice keys.
    (let [rock (r/composition->material-color {:Fe 0.5 :Si 0.5} 300.0)
          ice  (r/composition->material-color {:O 1.0} 100.0)
          gas  (r/composition->material-color {:H 0.75 :He 0.25} 5000.0)]
      (is (every? #(<= 0.0 % 1.0) rock))
      (is (> (first rock) (nth rock 2)) "rock is warm (red > blue)")
      (is (> (nth ice 2) (first ice)) "ice is cold (blue > red)")
      (is (not= rock gas) "different composition → different colour")))
  (testing "The same rocky composition renders rock when cold, not gas-tan"
    ;; regression: composition used to read retired keys → every body rendered
    ;; gas-tan regardless of composition.
    (let [rock (r/composition->material-color {:Fe 0.5 :Si 0.5} 300.0)
          gas  (r/composition->material-color {:H 0.75 :He 0.25} 5000.0)]
      (is (< (first rock) (first gas))
          "cold Fe/Si is darker than pale H/He gas — not identical tan"))))

(deftest test-body-render-color
  (testing "Cold bodies show material; hot bodies glow thermally"
    (let [rocky-cold (r/body-render-color 200.0 {:Fe 0.5 :Si 0.5})
          rocky-hot  (r/body-render-color 5.0e6 {:Fe 0.5 :Si 0.5})
          material   (r/composition->material-color {:Fe 0.5 :Si 0.5} 200.0)]
      (is (= rocky-cold material) "a cold rocky body is its material colour")
      (is (not= rocky-hot material) "a hot body crossfades toward thermal colour"))))

;; --- Player interface (spark, reticle, HUD, input) ---------------------------

(deftest test-player-overlay-shapes
  (testing "Observer yields a spark point + focus reticle ring"
    (let [w      (genesis/create-world {:gas-count 10})
          ctx    (units/make-context (cam/make-camera) {:width 1 :height 1})
          shapes (r/player-overlay-shapes ctx w)
          sparks (filter #(= :particle (:render-mode %)) shapes)
          ring   (filter #(= :line (:render-mode %)) shapes)]
      (is (= 1 (count sparks)) "one spark point")
      (is (pos? (count ring)) "focus reticle drawn as line segments")
      (is (even? (count ring)) "line endpoints come in pairs")))
  (testing "No observer → no overlay"
    (let [ctx (units/make-context (cam/make-camera) {:width 1 :height 1})]
      (is (= [] (r/player-overlay-shapes ctx (ecs/empty-world)))))))

(deftest test-coherence-color
  (testing "Coherent reads cool/teal, fading reads warm/red"
    (let [hi (r/coherence-color :highly-coherent)
          lo (r/coherence-color :fading)]
      (is (> (nth hi 1) (nth hi 0)) "coherent: green over red")
      (is (> (nth lo 0) (nth lo 2)) "fading: red over blue"))))

(deftest test-hud-rects-from-world
  (testing "Coherence fill stays within its track; HUD empty without an observer"
    (let [w     (genesis/create-world {:gas-count 10})
          rects (r/hud-rects-from-world w)
          track (first rects)
          fill  (second rects)]
      (is (>= (count rects) 2))
      (is (<= (:x1 fill) (+ 1e-9 (:x1 track))) "fill never exceeds the track width")
      (is (= [] (r/hud-rects-from-world (ecs/empty-world)))))))

(deftest test-hud-text-from-world
  (testing "Phase 0 worlds expose a clock/stats panel; bare worlds none"
    (let [w     (genesis/tick-world (genesis/create-world {:gas-count 10}))
          lines (r/hud-text-from-world w)]
      (is (seq lines) "phase0 world produces stat lines")
      (is (every? (comp string? :text) lines))
      (is (some #(re-find #"yr" (:text %)) lines) "clock line carries a time unit")
      (is (some #(re-find #"Msun" (:text %)) lines) "mass line present")
      (is (= [] (r/hud-text-from-world (ecs/empty-world)))))))

(deftest test-focus-input-moves-and-resizes
  (testing "handle-input drives the observer focus (the player's controls)"
    (let [w   (genesis/create-world {:gas-count 10})
          obs (player/get-observer w)
          r0  (:focus-radius obs)
          p0  (:focus-position obs)
          w1  (input/handle-input w :move-focus (mapv + p0 [3e15 0.0 0.0]))
          w2  (input/handle-input w :narrow-focus)]
      (is (not= p0 (:focus-position (player/get-observer w1))) "move-focus shifts focus")
      (is (< (:focus-radius (player/get-observer w2)) r0) "narrow-focus shrinks the volume"))))

(deftest test-oblate-body-projection
  (testing "Rotating protostars are projected with oblateness and rotation axis"
    (let [[w _eid] (stellar/spawn-clump (ecs/empty-world)
                                        {:position [0.0 0.0 0.0]
                                         :velocity [0.0 0.0 0.0]
                                         :mass 2e30
                                         :radius 1e15
                                         :matter-state :protostar
                                         :angular-momentum [0.0 0.0 1e45]})
          w2 (stellar/collapse-system w)
          shapes (r/phase0-bodies-from-world w2)
          bodies (filter #(= :body (:render-mode %)) shapes)]
      (is (seq bodies) "protostar produces a shaded body")
      (let [body (first bodies)]
        (is (< (:oblateness body) 1.0) "body is oblate")
        (is (= [0.0 0.0 1.0] (:rotation-axis body)) "rotation axis aligns with L")))))

(deftest test-model-matrix-oblate
  (testing "Oblate model matrix scales z differently than x/y"
    (let [m (var-get (resolve 'infra.render/model-matrix))
          mat-sph (m [0.0 0.0 0.0] 2.0)
          mat-obl (m [0.0 0.0 0.0] 2.0 0.5 [0.0 0.0 1.0])
          ;; Frobenius norm of upper-left 3x3: for axis z, z-scale is 1, x/y are 2
          sph-scale-sum (reduce + (for [i [0 1 2 4 5 6 8 9 10]]
                                    (* (aget mat-sph i) (aget mat-sph i))))
          obl-scale-sum (reduce + (for [i [0 1 2 4 5 6 8 9 10]]
                                    (* (aget mat-obl i) (aget mat-obl i))))]
      ;; spherical has equal scales: 2² + 2² + 2² = 12
      (is (< (Math/abs (- sph-scale-sum 12.0)) 1e-6))
      ;; oblate has two scales of 2 and one of 1: 4 + 4 + 1 = 9
      (is (< (Math/abs (- obl-scale-sum 9.0)) 1e-6))
      ;; z-aligned body leaves z-scale at index 10 as the polar scale
      (is (< (Math/abs (- (aget mat-obl 10) 1.0)) 1e-6)))))

;; --- Sprite LOD -------------------------------------------------------------

(deftest test-classify-body-lod
  (testing "Close bodies stay solid; distant ones become sprites"
    (let [camera (cam/make-camera 50.0)
          near   {:render-mode :body :position [0.0 0.0 0.0] :radius 10.0}
          far    {:render-mode :body :position [0.0 0.0 500.0] :radius 1.0}
          [solids sprites] (#'r/classify-body-lod [near far] camera 1280 720 nil)]
      (is (= 1 (count solids)))
      (is (= 1 (count sprites)))
      (is (= :body (:render-mode (first solids))))
      (is (= :sprite (:render-mode (first sprites))))
      (is (pos? (:size (first sprites))) "sprite gets a pixel size")))
  (testing "Non-body shapes pass through unchanged"
    (let [camera (cam/make-camera 50.0)
          line   {:render-mode :line :position [0.0 0.0 0.0]}
          [solids sprites] (#'r/classify-body-lod [line] camera 1280 720 nil)]
      (is (= 1 (count solids)))
      (is (zero? (count sprites)))))
  (testing "A body large enough on screen stays solid even at distance"
    (let [camera (cam/make-camera 50.0)
          huge   {:render-mode :body :position [0.0 0.0 200.0] :radius 50.0}
          [solids sprites] (#'r/classify-body-lod [huge] camera 1280 720 nil)]
      (is (= 1 (count solids)))
      (is (zero? (count sprites)))))
  (testing "Bright stars produce larger sprites than dim bodies at the same distance"
    (let [camera (cam/make-camera 50.0)
          dim    {:render-mode :body :kind :planet :position [0.0 0.0 500.0] :radius 1.0 :brightness 0.3}
          bright {:render-mode :body :kind :star   :position [0.0 0.0 500.0] :radius 1.0 :brightness 3.0}
          [_ dim-sprites]    (#'r/classify-body-lod [dim]    camera 1280 720 nil)
          [_ star-sprites]   (#'r/classify-body-lod [bright] camera 1280 720 nil)]
      (is (= 1 (count dim-sprites)))
      (is (= 1 (count star-sprites)))
      (is (> (:size (first star-sprites)) (:size (first dim-sprites)))
          "luminous stars get a bigger point sprite"))))

(deftest test-body-brightness
  (testing "Stars scale with luminosity; non-stars are dim"
    (let [[w eid] (stellar/spawn-clump (ecs/empty-world)
                                       {:position [0.0 0.0 0.0]
                                        :mass 2e30 :radius 6.957e8
                                        :matter-state :star
                                        :temperature 5800.0})
          w (ecs/put-component w eid c/luminosity 3.828e26)
          b (r/body-brightness w eid :star)]
      (is (>= b 1.0) "a sun-like star is at least unit brightness")
      (is (< (r/body-brightness (ecs/empty-world) 999 :planet) 0.5)
          "planets are dim"))))
