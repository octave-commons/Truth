(ns infra.render.input
  "GLFW input setup for the dev renderer. Keeps the action palette (key →
   intervention) and observer focus controls in one place so the binding and the
   on-screen legend can never drift."
  (:require
   [clojure.math :as math] [shape.spatial :as sp]
   [infra.camera :as cam]
   [infra.input :as input]
   [domain.player :as player])
  (:import
   (org.lwjgl.glfw GLFW GLFWKeyCallback GLFWCursorPosCallback GLFWScrollCallback GLFWMouseButtonCallback)))

(defn- move-focus-by
  "Shift the observer's focus volume by `dpos` (world metres)."
  [world dpos]
  (if-let [obs (player/get-observer world)]
    (input/handle-input world :move-focus (sp/v+ (:focus-position obs) dpos))
    world))

(def action-palette
  "The player's paid actions. `setup-input` dispatches key presses from this list
   and the HUD renders the legend from the same list, so a new action cannot be
   wired without appearing on screen."
  [{:label "Well"     :keycap "G"   :glfw GLFW/GLFW_KEY_G :shift? false :kind :warp/well
    :accent [0.45 0.85 1.0]  :hint "pull matter in"}
   {:label "Repulsor" :keycap "G+s" :glfw GLFW/GLFW_KEY_G :shift? true  :kind :warp/repulsor
    :accent [1.0 0.6 0.3]    :hint "push matter out (shift+G)"}
   {:label "Heat"     :keycap "H"   :glfw GLFW/GLFW_KEY_H :shift? false :kind :heat/source
    :accent [1.0 0.45 0.25]  :hint "warm gas — resist collapse"}
   {:label "Cool"     :keycap "J"   :glfw GLFW/GLFW_KEY_J :shift? false :kind :heat/sink
    :accent [0.6 0.85 1.0]   :hint "chill gas — trigger collapse"}])

(defn action-for-key
  "The palette entry a key/shift press triggers, or nil. Shift must match exactly
   so G and shift+G map to different actions."
  [glfw-key shift?]
  (some (fn [a] (when (and (= glfw-key (:glfw a)) (= (boolean shift?) (:shift? a))) a))
        action-palette))

(defn- player-key
  "Map a key press to a focus / drift / release action on the world's observer.
   Arrows drift the focus volume, , / . narrow / widen it, Space releases the
   spark to drift toward the system."
  [world-atom k]
  (let [step 3.0e15]
    (condp = k
      GLFW/GLFW_KEY_LEFT   (swap! world-atom move-focus-by [(- step) 0.0 0.0])
      GLFW/GLFW_KEY_RIGHT  (swap! world-atom move-focus-by [step 0.0 0.0])
      GLFW/GLFW_KEY_UP     (swap! world-atom move-focus-by [0.0 0.0 (- step)])
      GLFW/GLFW_KEY_DOWN   (swap! world-atom move-focus-by [0.0 0.0 step])
      GLFW/GLFW_KEY_COMMA  (swap! world-atom input/handle-input :narrow-focus)
      GLFW/GLFW_KEY_PERIOD (swap! world-atom input/handle-input :widen-focus)
      GLFW/GLFW_KEY_SPACE  (swap! world-atom input/handle-input :release)
      nil)))

(defn- look-sensitivity
  "Current look sensitivity from config, with default."
  [config]
  (double (:look-sensitivity config 0.02)))

(defn- manual-look?
  "True when the camera is in manual look mode and the cursor is not captured by UI."
  [config]
  (and (= :manual (:mode config :manual))
       (not (:ui/active-domain config))
       (not (:ui/cursor-free? config))))

(defn- significant-move?
  "True if cursor delta exceeds `threshold` in either axis."
  [dx dy threshold]
  (or (> (abs (double dx)) threshold)
      (> (abs (double dy)) threshold)))

(defn- update-camera-look
  "Apply a cursor delta to camera yaw/pitch."
  [camera dx dy sens]
  (-> camera
      (update :yaw #(+ % (* dx sens)))
      (update :pitch #(max -89.0 (min 89.0 (- % (* dy sens)))))
      cam/update-camera-position))

(defn- zoom-factor
  "Multiplicative zoom factor for one scroll notch."
  [yoffset config]
  (let [zsens (double (:zoom-sensitivity config 10.0))
        frac  (min 0.5 (max 0.005 (* 0.012 zsens)))]
    (math/pow (- 1.0 frac) yoffset)))

(defn- update-camera-zoom
  "Apply a scroll delta to camera distance."
  [camera yoffset config]
  (let [zmin (double (:zoom-min config 10.0))
        k    (zoom-factor yoffset config)]
    (-> camera
        (update :distance #(max zmin (min 2000.0 (* % k))))
        cam/update-camera-position)))

(defn- reset-camera!
  "Reset camera and config to defaults."
  [camera-atom config-atom]
  (reset! camera-atom (cam/make-camera))
  ;; merge (not reset!) so window resources survive a camera reset.
  (swap! config-atom merge (cam/default-camera-settings))
  (println "Camera reset"))

(defn- dispatch-palette-action!
  "Trigger an action request if `key` matches a palette entry at the given shift state."
  [config-atom glfw-key shift?]
  (when-let [a (action-for-key glfw-key shift?)]
    (swap! config-atom assoc :action-request {:kind (:kind a)})))

(def ^:private config-key-handlers
  "Data table for camera/UI key presses. Each entry is {:handler :label :fmt}."
  {GLFW/GLFW_KEY_C
   {:handler cam/cycle-camera-mode :label "Camera mode" :fmt (comp str :mode)}
   GLFW/GLFW_KEY_TAB
   {:handler #(update % :ui/cursor-free? not) :label "UI cursor" :fmt #(if (:ui/cursor-free? %) "free" "locked")}
   GLFW/GLFW_KEY_LEFT_BRACKET
   {:handler #(cam/adjust-fit-margin % 0.9) :label "Fit margin" :fmt :fit-margin}
   GLFW/GLFW_KEY_RIGHT_BRACKET
   {:handler #(cam/adjust-fit-margin % 1.1) :label "Fit margin" :fmt :fit-margin}})

(defn- key-callback
  "GLFW key callback for the dev renderer."
  [window camera-atom keys-atom config-atom world-atom]
  (proxy [GLFWKeyCallback] []
    (invoke [window key scancode action mods]
      (when (= action GLFW/GLFW_PRESS)
        (swap! keys-atom assoc key true))
      (when (= action GLFW/GLFW_RELEASE)
        (swap! keys-atom dissoc key))
      (when (and (= key GLFW/GLFW_KEY_ESCAPE) (= action GLFW/GLFW_PRESS))
        (GLFW/glfwSetWindowShouldClose window true))
      (when (= action GLFW/GLFW_PRESS)
        (when-let [{:keys [handler label fmt]} (config-key-handlers key)]
          (swap! config-atom handler)
          (println label ":" (fmt @config-atom)))
        (when (= key GLFW/GLFW_KEY_R)
          (reset-camera! camera-atom config-atom))
        (dispatch-palette-action! config-atom key (pos? (bit-and (int mods) GLFW/GLFW_MOD_SHIFT))))
      (when world-atom
        (player-key world-atom key)))))

(defn- cursor-callback
  "GLFW cursor position callback for look/orbit dragging."
  [window camera-atom config-atom cursor dragged?]
  (let [last-pos (atom [0.0 0.0])
        fst      (atom true)]
    (proxy [GLFWCursorPosCallback] []
      (invoke [window x y]
        (reset! cursor [x y])
        (swap! config-atom assoc :cursor [x y])
        (if @fst
          (do (reset! last-pos [x y]) (reset! fst false))
          (let [[lx ly] @last-pos
                dx (- x lx)
                dy (- y ly)]
            (reset! last-pos [x y])
            (let [cfg  @config-atom
                  sens (look-sensitivity cfg)]
              (when (and (manual-look? cfg) (significant-move? dx dy 0.5))
                (reset! dragged? true)
                (swap! camera-atom update-camera-look dx dy sens))
              (when (and (not (manual-look? cfg))
                         (= (GLFW/glfwGetMouseButton window GLFW/GLFW_MOUSE_BUTTON_LEFT) GLFW/GLFW_PRESS))
                (when (significant-move? dx dy 1.5)
                  (reset! dragged? true))
                (swap! camera-atom update-camera-look dx dy sens)))))))))

(defn- mouse-button-callback
  "GLFW mouse button callback. A left click without drag becomes a pick request."
  [window config-atom cursor dragged?]
  (proxy [GLFWMouseButtonCallback] []
    (invoke [window button action mods]
      (when (= button GLFW/GLFW_MOUSE_BUTTON_LEFT)
        (cond
          (= action GLFW/GLFW_PRESS)   (reset! dragged? false)
          (= action GLFW/GLFW_RELEASE) (when-not @dragged?
                                         (let [[cx cy] @cursor]
                                           (swap! config-atom assoc
                                                  :pick-request {:x cx :y cy}))))))))

(defn- scroll-callback
  "GLFW scroll callback for camera zoom."
  [camera-atom config-atom]
  (proxy [GLFWScrollCallback] []
    (invoke [window xoffset yoffset]
      (swap! camera-atom update-camera-zoom yoffset @config-atom))))

(defn setup-input
  "Install GLFW input callbacks. With a `:world-atom` key, also wires the player's
   focus controls (arrows / , . / Space) onto the world's observer."
  [{:keys [window camera-atom keys-atom config-atom world-atom]}]
  (GLFW/glfwSetKeyCallback window (key-callback window camera-atom keys-atom config-atom world-atom))
  (let [cursor (atom [0.0 0.0])
        dragged? (atom false)]
    (GLFW/glfwSetCursorPosCallback window (cursor-callback window camera-atom config-atom cursor dragged?))
    (GLFW/glfwSetMouseButtonCallback window (mouse-button-callback window config-atom cursor dragged?)))
  (GLFW/glfwSetScrollCallback window (scroll-callback camera-atom config-atom)))
