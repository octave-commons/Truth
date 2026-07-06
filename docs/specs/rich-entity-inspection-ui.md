# Rich Entity Inspection UI

**Status:** design  
**Scope:** replace the text-only inspector card in `src/infra/inspect.clj` with a visual, pane-based inspection panel for selected ECS entities.  
**Goal:** let the player/scientist verify simulation state at a glance as physics models grow more complex, without leaving the 3D view.

---

## 1. Background and constraints

The current inspector (`infra.inspect/inspector-card`) draws a single anchored HUD card beside the selected body. It is text-only: name, mass, radius, temperature, speed, luminosity, composition string, regime, and entity id. As Phase 0 adds chemistry, orbital hierarchy, thermal evolution, and stellar winds, a flat text list becomes unreadable.

This design keeps the card anchored to the selected body but turns it into a compact, multi-pane panel rendered with the existing LWJGL HUD primitives (`infra.render/render-hud` + `render-text`). It must:

- run on the render thread only;
- read the ECS world read-only;
- reuse the existing `infra.render.units` coordinate pipeline;
- not introduce new external UI libraries (LWJGL only);
- remain optional/toggleable so the current minimal card can stay for low-end paths.

---

## 2. Panel layout and widgets

The panel is a vertical column of **panes**. Each pane is a self-contained widget that knows how to measure its own height given a fixed width. The panel is anchored beside the selected body and clamped on-screen, exactly like the current card.

| Pane | Purpose | Data source |
|---|---|---|
| **Header** | Name, state badge, mass/radius badges | `c/matter-state`, `c/mass`, `c/radius`, naming |
| **Composition** | Mass-fraction bars for H, He, metals, ice, volatiles | `c/composition` |
| **Thermal / Radius timeline** | Sparklines of temperature, radius, luminosity over recent ticks | world history or entity-local `:inspect/history` component |
| **Orbital elements** | a, e, i, period, plus a mini orbit map | `c/elements`, `c/orbit-ref`, derived state |
| **Children / Hierarchy** | Star → disk → planets → moons; click focuses | `c/orbit-ref`, spatial proximity, mass hierarchy |
| **Event history** | Last N state transitions, merges, wind events | world `:ledger` or entity-local `:inspect/events` |
| **Raw ECS components** | Collapsible key/value table of all components on the entity | ECS entity map |

### 2.1 Panel mock (ASCII)

```text
+-- Vetharion — star --------------------------+
| [STAR]  1.04 Msun   0.98 Rsun   5 800 K      |
+-----------------------------------------------+
| COMPOSITION                                   |
| H    ████████████████████████████  0.72       |
| He   ██████████                      0.26     |
| metals ██                            0.015    |
| ice  █                              0.005     |
+-----------------------------------------------+
| THERMAL / RADIUS                              |
| temp  ┌─╲╱╲╱────────────────────┐ 5.8e3 K     |
| rad   ┌╲───────────────────────┐ 6.9e8 m      |
| lum   ┌────────╲╱╲────────────┐ 1.0 Lsun      |
+-----------------------------------------------+
| ORBIT                                         |
| a 0.00 AU   e 0.000   i 0.0°                  |
| period —  [central body]                      |
| .  ·    ·           ☉                         |
+-----------------------------------------------+
| CHILDREN                                      |
| > disk Azurath        0.03 Msun               |
|   · planet Kethor     1.2 Mearth              |
|     · moon —          0.03 Mearth             |
|   · planet Silmari    8.4 Mearth              |
+-----------------------------------------------+
| EVENTS                                        |
| t+12.4 Myr  ignition → star                   |
| t+ 9.1 Myr  wind parcel launched              |
| t+ 4.0 Myr  protostar formed                  |
+-----------------------------------------------+
| RAW COMPONENTS  [v]                           |
| :component/mass        2.07e30                |
| :component/radius      6.82e8                 |
| ...                                           |
+-----------------------------------------------+
```

### 2.2 Header pane

- Title: `display-label` (name + state).
- State badge: colored pill using `inspect/state-color`.
- Three value chips: mass, radius, temperature (using existing formatters).
- Height: fixed.

### 2.3 Composition pane

- Render each non-zero element as a horizontal bar.
- Bar length proportional to mass fraction.
- Color-coded:
  - **H** — pale blue-white `#e8f4ff`
  - **He** — pale yellow `#fff8d6`
  - **metals** — warm grey-brown `#9e8a78`
  - **ice / H2O / volatiles** — cyan `#b8e6f5`
  - fallback — grey
- Show numeric fraction to two decimals.
- If composition sums to < 0.5 (sparse gas parcel), show a dim note: "low-density mixture".

### 2.4 Thermal / radius timeline pane

- Three mini sparklines sharing one horizontal time axis.
- Each sparkline is a polyline of N recent samples.
- Y axis auto-scaled per metric, logarithmic for radius/luminosity when dynamic range is large.
- Current value printed at the right edge.
- A small dot marks state-transition ticks.
- Metrics:
  - `temperature` (K)
  - `radius` (m)
  - `luminosity` (W) when present
  - optionally `mass` (kg)

### 2.5 Orbital elements pane

- If `c/elements` exists, show a, e, i, Ω, ω, period.
- If `c/orbit-ref` exists, show the parent name and parent state.
- Mini orbit map: a top-down ellipse with the parent at one focus, current true anomaly marked.
- For unbound/outflow bodies, show a short hyperbola segment and flag "unbound".

### 2.6 Children / hierarchy pane

- Build a shallow tree from the selected entity outward.
- Primary sort key: mass descending.
- Indent children under parents using `· ` prefixes.
- Clicking a row dispatches `[:ui/select-entity eid]`, reusing `infra.menu/apply-action`.
- Cap visible rows at 16 with a "+N more" line.

### 2.7 Event history pane

- Last 8 entity-relevant events from the world ledger.
- Events to surface:
  - matter-state transitions
  - merge/accretion events
  - wind/flare ejection
  - planet/disk fragmentation
  - stellar ignition
- Each row: simulation time + short sentence.

### 2.8 Raw ECS components pane

- Collapsible (default: collapsed).
- Two-column key/value table.
- Vectors/maps printed as EDN, truncated to keep rows short.
- Use a monospaced scale (1.0) and dim color so it reads as debug output.

---

## 3. Composition rendering

Composition is the first place where text fails: "H 0.74 He 0.24" does not convey proportion. The widget draws horizontal stacked or grouped bars.

### 3.1 Bar strategy

Use grouped bars: one row per element. This preserves readability when fractions are small and when the number of elements grows.

A single stacked bar is acceptable as a compact alternative when vertical space is constrained. The implementation exposes both and the panel chooses grouped by default.

### 3.2 Color map

Store the color map as a pure function in `infra.inspect` so the renderer and any future legend share it.

```clojure
(def element-color
  "Color [r g b] for a composition element key."
  {:H         [0.91 0.96 1.00]
   :He        [1.00 0.97 0.84]
   :metals    [0.62 0.50 0.40]
   :ice       [0.75 0.85 0.95]
   :H2O       [0.65 0.82 0.94]
   :volatiles [0.80 0.90 0.98]
   :default   [0.70 0.70 0.70]})
```

### 3.3 Data flow

```text
┌─────────────────┐     ┌──────────────────┐     ┌─────────────────────┐
│  c/composition  │────▶│ inspect/fmt-comp │────▶│ sorted [{k frac}]   │
│  map on entity  │     │ + element-color  │     │                     │
└─────────────────┘     └──────────────────┘     └─────────────────────┘
                                                          │
                                                          ▼
                                             ┌────────────────────────┐
                                             │ render-hud rectangles  │
                                             │ + render-text labels   │
                                             └────────────────────────┘
```

---

## 4. Temporal state: sparklines

The render thread does not own simulation history, so the panel cannot demand a global log. Two sources are reasonable:

1. **Entity-local `:inspect/history` component** (a rolling buffer written by a tiny `domain.inspect-history` system, or injected by `infra.dev.window` as an intent). Each tick appends `{tick mass temp radius lum state}`. Size capped at 256 samples.
2. **World ledger** for discrete events only (transitions, ejections).

### 4.1 Sparkline algorithm

- Input: vector of samples `[{:tick :mass :temperature :radius :luminosity :state}]`.
- Window: last N samples where N depends on panel width (default 128).
- Scale each metric independently: `y = (log10(v) - log10(min)) / (log10(max) - log10(min))` when dynamic range > 10×, else linear.
- Render as `:line` segments or as a filled polygon using the HUD program. Filled polygons look better and reuse `render-hud` (rects are already triangles).
- Transition dots: small accent rectangles at sample indices where `state` changed.

### 4.2 Sparkline mock

```text
TEMPERATURE
8800 ┤        ╭─╮
6600 ┤      ╭─╯ ╰╮
4400 ┤──────╯     ╰──
2200 ┤
     └┬──┬──┬──┬──┬──▶
     t-128            now
```

---

## 5. Multi-selection and hierarchical systems

The current selection model is a single entity id in `config :selection`. The rich panel extends this without replacing it:

- **Primary selection**: the entity whose panel is open (unchanged).
- **Hover breadcrumb**: the menu `:entities` panel and the children pane can both set selection.
- **Lock group**: `Ctrl+click` in the 3D view or in the children pane adds an entity to a `:selection/group` set in config. The panel shows a thin header "N selected" and exposes bulk readouts (total mass, count by state).
- **Hierarchy navigation**: clicking a child row retargets the panel to that child and tethers the camera via the existing `:follow-selection` mode.

### 5.1 Hierarchy discovery

There is no explicit parent component. Build hierarchy on demand from:

- `c/orbit-ref`: explicit parent reference.
- If no `orbit-ref`, use the nearest more-massive body within a bound threshold as a provisional parent.
- Gas/disk particles and consumed entities are excluded.

This keeps the panel honest about what the simulation actually models, while remaining useful before a full hierarchical orbital system exists.

### 5.2 Multi-selection data flow

```text
3D click ─┬─► pick-entity ─┬─ plain ─► set :selection
          │                └─ ctrl  ─► conj :selection/group
          │
menu row click ─────────────► :ui/select-entity eid
          │
children row click ─────────► :ui/select-entity eid
```

---

## 6. Technical implementation

### 6.1 Immediate-mode panels

The panel is immediate mode: every frame the render thread calls `infra.inspect/rich-inspector-panel`, which returns `{:rects :text :lines :hits}` exactly like `infra.menu/menu-hud`. No retained widget state lives in GL; layout is recomputed each frame from the current world and config.

This matches the existing `render-hud` / `render-text` contract and avoids adding a scene graph.

### 6.2 Font rendering

Continue using `STBEasyFont` through `infra.render/render-text`. The panel needs three scales:

- `2.0` section headers and primary values
- `1.4` body text and bar labels
- `1.0` raw ECS / debug table

`STBEasyFont` is fixed-width enough for layout; measure text width with `(* 6.0 scale (count s))` as `infra.menu` already does.

### 6.3 Layout

Use a simple box model implemented in `infra.inspect.layout`:

```clojure
(defrecord Box [x y w h])
(defn inset [box dx dy] ...)
(defn split-v [box h] [top-box remaining-box])
(defn stack [box children] ...)
```

Each widget returns `[box {:rects [...] :text [...] :hits [...]}]`. The panel composes widgets top-down.

Panel width: 360 px (configurable via `:ui/inspector-width`).
Padding: 12 px.
Pane gap: 8 px.

### 6.4 Caching

Two caches keep the render thread fast:

1. **History buffer**: maintained as a component on tracked entities. The render thread only reads it.
2. **Hierarchy cache**: invalidated when `(:tick world)` changes, computed on demand and memoized in a transient local during the frame.
3. **Text triangulation**: `STBEasyFont` work is small; no extra cache needed unless profiling shows otherwise.

No GPU resources are retained between frames. `render-hud` creates and deletes VAO/VBO per draw call already; this is acceptable for a low-count UI panel.

### 6.5 New namespaces

Keep the work in `infra/` because it is rendering and I/O.

- `infra.inspect` — extended with panel layout and widget helpers.
- `infra.inspect.layout` — box model and measurement (small, pure).
- `infra.inspect.history` — optional entity-local rolling history (domain side, pure).

No new files under `domain/` are required unless we choose to store history as a real ECS component.

---

## 7. Integration points

### 7.1 With `infra.render`

`render-hud` and `render-text` already consume `{:rects [...] :text [...]}`. The rich panel returns the same shape. The only addition is a second draw list for panel hit regions.

`infra.render/render-scene` signature is unchanged; the panel data is folded into `hud` and `hud-text` in `infra.dev.window`.

### 7.2 With `infra.menu`

- `menu/apply-action` already handles `[:ui/select-entity eid]`. Reuse it.
- The panel's clickable rows (children, raw-components toggle) emit the same action shape.
- Add `:ui/toggle-inspector` action to `menu/apply-action` so the Entities menu can switch between minimal and rich mode.
- The panel hit regions must be added to the mouse-capture regions used by `menu/over-regions?` so the world hover/pick is suppressed while the cursor is over the panel.

### 7.3 With `infra.dev.window`

In `render-frame-once`, replace the current `card` construction:

```clojure
;; current
(let [card (when sel (inspect/inspector-card ctx w sel bodies))]
  ...)

;; new
(let [panel (when sel (inspect/rich-inspector-panel ctx cfg w sel bodies fb-w fb-h))]
  ...)
```

Then merge panel rects/text/hits/regions into the frame's HUD and input handling.

```clojure
hud      (-> (vec (render/hud-rects-from-world w))
             ...
             (into (:rects panel)))
hud-text (concat ... (:text panel))
regions  (into (:regions menu) (:regions panel))
over-ui? (boolean (and cur-sx
                       (or (menu/over-regions? (:regions menu) cur-sx cur-sy)
                           (menu/over-regions? (:regions panel) cur-sx cur-sy))))
```

Click resolution uses `menu/hit-at` against the union of `:hits menu` and `:hits panel`.

### 7.4 With `infra.inspect`

The existing `inspector-card` is preserved as `inspect/minimal-card`. `rich-inspector-panel` is added alongside it. A config flag `:ui/rich-inspector?` selects which is rendered.

---

## 8. Clojure pseudocode

### 8.1 Layout box model

```clojure
(ns infra.inspect.layout
  "Immediate-mode box model for the rich inspector panel.")

(defrecord Box [^double x ^double y ^double w ^double h])

(defn inset [box dx dy]
  (->Box (+ (:x box) dx) (+ (:y box) dy)
         (- (:w box) dx dx) (- (:h box) dy dy)))

(defn split-v [{:keys [x y w h] :as box} ^double h1]
  [(->Box x y w h1)
   (->Box x (+ y h1) w (- h h1))])

(defn text-width [s scale]
  (* 6.0 scale (count (str s))))
```

### 8.2 Panel entry point

```clojure
(ns infra.inspect
  (:require
   ...
   [infra.inspect.layout :as lo]))

(def ^:const panel-w 360.0)
(def ^:const pad 12.0)
(def ^:const pane-gap 8.0)

(defn rich-inspector-panel
  "Return {:rects :text :hits :regions} for the rich inspector of `eid`.
   `ctx` supplies camera/viewport; `bodies` are this frame's projected shapes."
  [ctx cfg world eid bodies fb-w fb-h]
  (when-let [shape (selected-shape bodies eid)]
    (let [anchor  (units/render->screen ctx (:position shape))
          [bx by] (or anchor [(* 0.5 fb-w) (* 0.5 fb-h)])
          h0      (+ (:height (:viewport ctx)) 0.0)
          panel   (layout-panel ctx cfg world eid bodies)
          ph      (:height panel)
          ;; place to the right, flip left if needed, clamp below top HUD
          x0      (if (> bx (* 0.62 fb-w))
                    (max pad (- bx panel-w 28.0))
                    (max pad (min (- fb-w panel-w pad) (+ bx 28.0))))
          y0      (max 252.0 (min (- fb-h ph pad) (- by (* 0.5 ph))))
          box     (->Box x0 y0 panel-w ph)]
      (translate-panel box panel fb-w fb-h))))
```

### 8.3 Widget helpers

```clojure
(defn header-pane
  [world eid box]
  (let [state   (ecs/get-component world eid c/matter-state)
        title   (naming/display-label eid state)
        tcol    (state-color state)
        mass    (fmt-mass (ecs/get-component world eid c/mass)
                          (#{:star :protostar} state))
        radius  (fmt-radius (ecs/get-component world eid c/radius)
                            (#{:star :protostar} state))
        temp    (some-> (ecs/get-component world eid c/temperature)
                        (format "%.0f K"))
        [top _] (lo/split-v box 28.0)]
    {:rects [{:x0 ... :y0 ... :x1 ... :y1 ... :color [0.08 0.12 0.22 0.90]}
             {:x0 ... :y0 ... :x1 ... :y1 ... :color tcol}]
     :text  [{:text title :x (+ x pad) :y (+ y 6) :scale 2.0 :color tcol}
             {:text (str mass "   " radius "   " (or temp "—"))
              :x (+ x pad) :y (+ y 34) :scale 1.4 :color value-col}]
     :height 56.0}))

(defn composition-pane
  [world eid box]
  (let [comp (ecs/get-component world eid c/composition)
        rows (sort-by (fn [[_ v]] (- (double v))) (filter #(pos? (double (val %))) comp))
        bar-h 14.0 row-h 22.0]
    (reduce (fn [acc [k frac]]
              (let [y (+ (:y box) 24.0 (* (:i acc) row-h))
                    bw (* (- (:w box) pad pad 60.0) (double frac))
                    col (element-color k :default)]
                (-> acc
                    (update :rects conj
                            {:x0 ... :y0 ... :x1 ... :y1 ... :color (conj col 0.85)})
                    (update :text conj
                            {:text (format "%-7s %.2f" (name k) frac)
                             :x (+ (:x box) pad) :y y :scale 1.3 :color value-col})
                    (update :i inc))))
            {:rects [] :text [] :i 0}
            rows)))

(defn sparkline-pane
  [samples box metric color label fmt]
  (let [height 48.0
        plot   (lo/inset box pad 4.0)
        pts    (scale-samples samples metric (:w plot) height)
        poly   (polyline-rects pts plot color)]
    {:rects poly
     :text  [{:text label :x (:x plot) :y (:y box) :scale 1.3 :color dim-col}
             {:text (fmt (last-value samples metric))
              :x (- (:x box) (:w box) pad 4.0) :y (:y box) :scale 1.3 :color value-col}]
     :height height}))
```

### 8.4 Input handling

```clojure
(defn inspector-hit-at
  "Find the first panel hit at (x,y), or nil."
  [hits x y]
  (menu/hit-at hits x y))

(defn apply-inspector-action
  "Fold an inspector :action into cfg."
  [cfg action]
  (case (first action)
    :inspect/select-entity
    (menu/apply-action cfg [:ui/select-entity (second action)])

    :inspect/toggle-raw
    (update cfg :ui/inspector-raw? not)

    :inspect/toggle-mode
    (update cfg :ui/rich-inspector? not)

    cfg))
```

---

## 9. Data flow diagram

```text
                           render frame
                               │
                               ▼
        ┌──────────────────────────────────────┐
        │   infra.dev.window/render-frame-once │
        └──────────────────────────────────────┘
                          │
          ┌───────────────┼───────────────┐
          ▼               ▼               ▼
   ┌────────────┐  ┌────────────┐  ┌──────────────┐
   │ bodies-fn  │  │ menu-hud   │  │ rich-inspector│
   │ (existing) │  │ (existing) │  │ panel        │
   └────────────┘  └────────────┘  └──────────────┘
          │               │               │
          ▼               ▼               ▼
   shape list       {:rects :text     {:rects :text
                    :hits :regions}   :hits :regions}
                          │               │
                          ▼               ▼
                   ┌──────────────────────────┐
                   │ merge into hud/hud-text  │
                   │ merge into input regions │
                   └──────────────────────────┘
                          │
                          ▼
              ┌───────────────────────┐
              │ render/render-scene   │
              │ render/render-hud     │
              │ render/render-text    │
              └───────────────────────┘
```

---

## 10. Implementation task list

- [ ] 1. Create `infra.inspect.layout` with `Box`, `inset`, `split-v`, `text-width`.
- [ ] 2. Add `inspect/element-color` and refactor `inspect/fmt-comp` to return structured data.
- [ ] 3. Implement `inspect/header-pane`, `inspect/composition-pane`, `inspect/sparkline-pane` with isolated tests.
- [ ] 4. Add entity-local history source:
  - either a `domain.inspect-history` system that writes `:component/inspect.history`, or
  - an intent-based rolling cache built in `infra.dev.window` after each tick.
- [ ] 5. Implement `inspect/orbit-pane` using `domain.orbital.kepler` helpers.
- [ ] 6. Implement `inspect/children-pane` using `c/orbit-ref` + mass-proximity fallback.
- [ ] 7. Implement `inspect/events-pane` reader for world ledger transitions.
- [ ] 8. Implement `inspect/raw-components-pane` collapsible table.
- [ ] 9. Compose `inspect/rich-inspector-panel` and keep `inspect/inspector-card` as `inspect/minimal-card`.
- [ ] 10. Add `:ui/rich-inspector?` and `:ui/inspector-raw?` config keys; default `false`.
- [ ] 11. Wire panel into `infra.dev.window/render-frame-once`: merge rects/text/regions/hits.
- [ ] 12. Add `:inspect/toggle-mode` and `:inspect/select-entity` handling; extend `menu/apply-action` where appropriate.
- [ ] 13. Add `Ctrl+click` support in `infra.render/setup-input` to populate `:selection/group`.
- [ ] 14. Write tests for layout arithmetic, color map, and pane heights.
- [ ] 15. Run `clj -M:test` and fix architecture/lint issues.
- [ ] 16. Update `docs/specs/notes-synthesis-index.md` with a link to this spec.

---

## 11. Open questions

1. Should history live as a real ECS component (`:component/inspect.history`) or as a render-side cache keyed by `[tick eid]`? A real component survives world serialization; a render cache is cheaper and avoids another single-writer.
2. Should the panel support dragging to reposition, or is anchor-to-body sufficient?
3. Should composition use a stacked bar instead of grouped bars when space is tight?

These are deferred to the implementation phase and can be resolved without changing the architecture above.
