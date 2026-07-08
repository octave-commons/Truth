(ns infra.dev.window
  "Live development window service.

   Runs a GLFW/OpenGL window on a dedicated daemon thread. The window
   continuously renders whatever is in the shared `world-atom`, using the
   shared `camera-atom` and a reloadable `renderer-config-atom`.

   Connect from another REPL (e.g. `clj -M:repl --connect localhost:7888`)
   and mutate the atoms to see changes in real time.

   Examples:
     (require '[infra.dev.window :as w])
     (reset! (:world @w/service-state) my-world)
     (swap! (:camera @w/service-state) assoc :distance 400.0)
     (w/reload-shaders!)   ; recompile after editing infra.render shader vars
     (w/reload-mesh! 3)    ; change sphere subdivision level
     (w/take-screenshot! \"/tmp/truth-dev.png\")

     Camera controls in the window:
     C              cycle camera mode (manual / track-largest-cluster / fit-all)
     [ / ]          decrease / increase fit margin
     R              reset camera and settings
     W / S          move the mote forward / backward relative to the camera
     A / D          strafe the mote left / right
     mouse          rotate the camera around the mote (third-person)
     scroll         adjust distance"
  (:require
   [infra.dev.window.lifecycle :as lifecycle]))

(def service-state lifecycle/service-state)
(def start! lifecycle/start!)
(def stop! lifecycle/stop!)
(def reload-shaders! lifecycle/reload-shaders!)
(def reload-mesh! lifecycle/reload-mesh!)
(def reset-camera! lifecycle/reset-camera!)
(def take-screenshot! lifecycle/take-screenshot!)
(def service-info lifecycle/service-info)
