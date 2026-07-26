(ns infra.render.input-test
  "Action-palette dispatch (card voxel-sculpt-verb-palette-wiring) and the
   focus-nudge offset law (card focus-follows-pilot)."
  (:require
   [clojure.test :refer [deftest testing is]]
   [domain.ecs.components :as c]
   [domain.ecs.core :as ecs]
   [domain.player :as player]
   [infra.render.hud :as hud]
   [infra.render.input :as rinput]
   [law.narrowing :as law-narrowing]
   [shape.spatial :as sp])
  (:import
   (org.lwjgl.glfw GLFW)))

;; --- Fixtures ------------------------------------------------------------------

(defn- armed-world
  "An observer (Resonance 20, planetary palette armed) at the origin with its
   focus at `focus-pos`, plus one committed world entity at the origin.
   Returns [world world-eid obs-eid]."
  [focus-pos]
  (let [[w obs-eid] (player/spawn-observer (ecs/empty-world) (sp/vec3 0.0 0.0 0.0))
        w (player/update-observer w assoc
                                  :focus-position focus-pos
                                  :resonance 20.0)
        w (ecs/put-component w obs-eid c/palette law-narrowing/planetary-palette)
        [w world-eid] (ecs/spawn w)
        w (ecs/put-components w world-eid
                              {c/position         (sp/vec3 0.0 0.0 0.0)
                               c/commitment-state :committed})]
    [w world-eid obs-eid]))

;; --- Key bindings ----------------------------------------------------------------

(deftest sculpt-verb-keys
  (testing "T/Shift+T/Y map to uplift/volcanism/erosion — and collide with
            NOTHING in the design §7 binding map (movement WASD/Space/LCtrl,
            abilities G/Shift+G/H/J, focus arrows + ,/., shell C/R/L/[/]/Tab/Esc,
            reserved Q/E/F)"
    (is (= :uplift (:sculpt (rinput/action-for-key GLFW/GLFW_KEY_T false))))
    (is (= :volcanism (:sculpt (rinput/action-for-key GLFW/GLFW_KEY_T true))))
    (is (= :erosion (:sculpt (rinput/action-for-key GLFW/GLFW_KEY_Y false))))
    (is (= :warp/well (:kind (rinput/action-for-key GLFW/GLFW_KEY_G false))))
    (is (= :warp/repulsor (:kind (rinput/action-for-key GLFW/GLFW_KEY_G true))))
    (is (nil? (rinput/action-for-key GLFW/GLFW_KEY_Y true))
        "shift+Y is unbound — no accidental double-mapping"))
  (testing "every palette entry has a unique key+shift pair"
    (let [pairs (map (juxt :glfw :shift?) rinput/action-palette)]
      (is (= (count pairs) (count (distinct pairs)))))))

;; --- Dispatch --------------------------------------------------------------------

(deftest sculpt-dispatch-calls-request-op-at-the-focus-point
  (testing "pressing T on an armed, committed world spends Resonance and
            enqueues the paid op anchored on the focus point"
    (let [[w world-eid _] (armed-world (sp/vec3 1.0e12 0.0 0.0))
          world-atom (atom w)
          config-atom (atom {})
          _ (@#'infra.render.input/dispatch-palette-action!
             config-atom world-atom GLFW/GLFW_KEY_T false)
          w' @world-atom
          ops (:voxel/sculpt-ops w')]
      (is (nil? (:action-request @config-atom))
          "sculpt verbs never touch the intervention path")
      (is (= 1 (count ops)))
      (let [op (first ops)]
        (is (= :uplift (:verb op)))
        (is (= 0.5 (:magnitude op)))
        (is (= world-eid (:target op)))
        (is (= [1.0 0.0 0.0] (:anchor op))
            "the anchor is the sub-focus direction over the committed world"))
      (is (== 18.0 (:resonance (player/get-observer w')))
          "Resonance spent at op-cost base 1.0 + 2.0×0.5"))))

(deftest sculpt-dispatch-is-gated-by-the-palette-phase
  (testing "before commitment (no committed world, genesis palette) a sculpt
            key press is a no-op — request-op gates, the caller never
            pre-checks"
    (let [[w _obs-eid] (player/spawn-observer (ecs/empty-world) (sp/vec3 0.0 0.0 0.0))
          w (player/update-observer w assoc :resonance 20.0)
          world-atom (atom w)
          config-atom (atom {})]
      (@#'infra.render.input/dispatch-palette-action!
       config-atom world-atom GLFW/GLFW_KEY_T false)
      (is (= w @world-atom) "world unchanged — no op, no spend"))))

(deftest intervention-dispatch-unchanged
  (testing "the four Phase 0 verbs still become :action-request on the config"
    (let [world-atom (atom (ecs/empty-world))
          config-atom (atom {})]
      (@#'infra.render.input/dispatch-palette-action!
       config-atom world-atom GLFW/GLFW_KEY_H false)
      (is (= {:kind :heat/source} (:action-request @config-atom)))
      (is (= (ecs/empty-world) @world-atom)
          "interventions are placed by the window loop, not the key callback"))))

;; --- Focus nudge (the offset law) -------------------------------------------------

(deftest arrow-nudge-edits-the-offset-not-the-position
  (testing "arrows accumulate :focus-offset in the config at 0.1 ×
            world-focus-radius per press and never touch the world — the
            manual-mode focus-follow law applies mote + offset, so nudge and
            auto-follow commute"
    (let [config-atom (atom {})
          world (first (armed-world (sp/vec3 1.0e12 0.0 0.0)))
          world-atom (atom world)
          pk @#'infra.render.input/player-key]
      (pk config-atom world-atom GLFW/GLFW_KEY_RIGHT)
      (pk config-atom world-atom GLFW/GLFW_KEY_RIGHT)
      (pk config-atom world-atom GLFW/GLFW_KEY_UP)
      (is (= [(* 2.0 rinput/focus-nudge-step) 0.0 (- rinput/focus-nudge-step)]
             (:focus-offset @config-atom)))
      (is (== (* 0.1 law-narrowing/world-focus-radius) rinput/focus-nudge-step)
          "the step is scaled to the binding-overlap radius (~0.1 AU), not 3e15 m")
      (is (= world @world-atom) "the world is untouched by a nudge"))))

;; --- HUD legend --------------------------------------------------------------------

(deftest hud-rows-key-off-the-palette-phase
  (testing "sculpt rows light only when the planetary palette is active with
            the verb's ability armed; the genesis verbs are always available"
    (let [[w _ _] (armed-world (sp/vec3 1.0e12 0.0 0.0))
          row-state @#'infra.render.hud/action-row-state
          uplift (first (filter #(= :uplift (:sculpt %)) rinput/action-palette))
          well   (first (filter #(= :warp/well (:kind %)) rinput/action-palette))]
      (is (:available? (row-state w uplift))
          "armed planetary palette → lit")
      (is (== 2.0 (:cost (row-state w uplift)))
          "the legend shows the Resonance op-cost, not the agency table")
      (is (:available? (row-state w well)))
      (let [[w2 _] (player/spawn-observer (ecs/empty-world) (sp/vec3 0.0 0.0 0.0))]
        (is (not (:available? (row-state w2 uplift)))
            "no c/palette (pre-commitment) → dim")))))
