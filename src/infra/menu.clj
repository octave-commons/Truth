(ns infra.menu
  "Top menu bar and sub-view panels — the shell's domain navigation.

   Pure layout. `menu-hud` returns {:rects :text :hits :regions}:
     :rects / :text  NDC / pixel draw lists consumed by infra.render
     :hits           framebuffer-pixel click targets {:x0 :y0 :x1 :y1 :action}
     :regions        framebuffer-pixel rects that capture the mouse (bar + open
                     panel), so the window loop can suppress world hover/pick there

   The window loop resolves a click by finding the :hit under the cursor and
   folding its :action into the config with `apply-action`. The canonical domain
   set and their Phase-0 availability follow docs/designs/ux-architecture.md — all
   seven domains are always shown; locked ones are dimmed but still open an
   (evocative) read-only panel (rule 10: always show what you cannot yet reach)."
  (:require
   [domain.player :as player]
   [infra.menu.widgets :as widgets]
   [infra.menu.panels :as panels]))

(def bar-h widgets/bar-h)
(def domains widgets/domains)
(def col-bar widgets/col-bar)
(def view-rows widgets/view-rows)
(def spark-knobs widgets/spark-knobs)

(def apply-action widgets/apply-action)
(def world-action widgets/world-action)
(defn menu-hud
  "Lay out the top bar and the open sub-view panel for the current `cfg` and
   `world` over an `w`×`h` framebuffer. Returns {:rects :text :hits :regions}."
  [cfg world ^double w ^double h]
  (let [rect-fn (fn [x0 y0 x1 y1 color] (widgets/rect-ctx {:w w :h h} {:x0 x0 :y0 y0 :x1 x1 :y1 y1 :color color}))
        active (:ui/active-domain cfg)
        obs (when (= active :spark) (player/get-observer world))
        pad 12.0
        rects (atom [(rect-fn 0.0 0.0 w bar-h col-bar)])
        text (atom [])
        hits (atom [])
        regions (atom [{:x0 0.0 :y0 0.0 :x1 w :y1 bar-h}])
        ctx {:rects rects :text text :hits hits :regions regions :rect-fn rect-fn :w w :pad pad}]
    (widgets/bar-tabs-ctx ctx active)
    (when active
      (let [py0 (+ bar-h 8.0)]
        (cond
          (= active :view)     (panels/view-panel-ctx ctx py0 cfg)
          (= active :entities) (panels/entities-panel-ctx ctx py0 world)
          obs                  (panels/spark-panel-ctx ctx py0 world obs)
          :else                (panels/read-only-panel-ctx ctx py0 active world))))
    {:rects @rects :text @text :hits @hits :regions @regions}))

(defn hit-at
  "The first hit region containing framebuffer point (x, y), or nil."
  [hits ^double x ^double y]
  (some (fn [{:keys [x0 y0 x1 y1] :as h}]
          (when (and (>= x (double x0)) (<= x (double x1))
                     (>= y (double y0)) (<= y (double y1)))
            h))
        hits))

(defn over-regions?
  "True when framebuffer point (x, y) falls inside any mouse-capture region."
  [regions ^double x ^double y]
  (boolean
   (some (fn [{:keys [x0 y0 x1 y1]}]
           (and (>= x (double x0)) (<= x (double x1))
                (>= y (double y0)) (<= y (double y1))))
         regions)))
