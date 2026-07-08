(ns law.render
  "Malli schemas for renderer data structures. Pure schemas — no GL imports."
  (:require
   [malli.core :as m]
   [malli.registry :as mr]))

(def glsl-type
  "Valid GLSL type names for uniforms, inputs, and outputs. Includes vector
   and scalar base types; array uniforms are represented as [type n] vectors."
  [:orn
   [:base [:enum :vec2 :vec3 :vec4 :mat3 :mat4 :float :int :sampler2D :sampler3D]]
   [:array [:tuple [:enum :vec2 :vec3 :vec4 :float :int] :int]]])

(def shader-stage
  "Data shape for one shader stage (vertex or fragment)."
  [:map
   [:inputs    [:map-of :keyword glsl-type]]
   [:uniforms  [:map-of :keyword glsl-type]]
   [:outputs   [:map-of :keyword glsl-type]]
   [:source    :string]])

(def program-def
  "A shader program definition: named pair of vertex + fragment stages."
  [:map
   [:name      :keyword]
   [:version   :string]
   [:vertex    shader-stage]
   [:fragment  shader-stage]])

(def vec3
  "A 3-vector of doubles — render-space positions, colors, box corners."
  [:tuple :double :double :double])

(def viewport
  "Screen pixel rectangle."
  [:map
   [:width :int]
   [:height :int]])

(def render-context
  "Coordinate-transform context for one view."
  [:map
   [:scale :double]
   [:camera :any]
   [:viewport viewport]])

(def render-shape
  "A unit-agnostic shape ready for the renderer. All positions/radii are in
   render units; color is unitless RGB."
  [:map
   [:render-mode [:enum :body :sprite :particle :line :volume :hud]]
   [:position vec3]
   [:radius {:optional true} :double]
   [:color {:optional true} vec3]
   [:glow {:optional true} :double]
   [:brightness {:optional true} :double]
   [:label {:optional true} :string]])

(def volume-light
  "One point light scattered by the ray-marched gas volume: render-space
   position, RGB color, source temperature (K), and unitless intensity."
  [:map
   [:pos vec3]
   [:col vec3]
   [:temp :double]
   [:intensity :double]])

(def volume-config
  "Tuning knobs for the volumetric fog pipeline. :kappa, :emission-scale,
   :scatter-scale, and :jitter are ray-march shader uniforms; :visual-h-scale
   and :visual-h-min shape the render-space smoothing support of each gas
   sample; :splat-gain scales the SPH kernel-shape weight when baking the
   froxel texture (the M4 spline integrates to ~0.39 over its unit support vs
   ~0.96 for the legacy quadratic falloff, so gain ≈ 2.4 restores comparable
   integrated emission/opacity)."
  [:map
   [:kappa [:and :double [:> 0]]]
   [:emission-scale [:and :double [:> 0]]]
   [:scatter-scale [:and :double [:> 0]]]
   [:jitter [:and :double [:>= 0.0]]]
   [:visual-h-scale [:and :double [:> 0]]]
   [:visual-h-min [:and :double [:> 0]]]
   [:splat-gain [:and :double [:> 0]]]])

(def volume-descriptor
  "The per-frame volume pass input assembled by infra.render/frame-volume:
   GL texture id, render-space bounding box, shader program id, scatter
   lights, and the tuning config the pass should render with."
  [:map
   [:tex :int]
   [:box-min vec3]
   [:box-max vec3]
   [:program :int]
   [:lights [:vector volume-light]]
   [:config volume-config]])

(def registry
  "Malli registry for render schemas."
  {:law.render/glsl-type         glsl-type
   :law.render/shader-stage      shader-stage
   :law.render/program-def       program-def
   :law.render/viewport          viewport
   :law.render/render-context    render-context
   :law.render/render-shape      render-shape
   :law.render/volume-light      volume-light
   :law.render/volume-config     volume-config
   :law.render/volume-descriptor volume-descriptor})

(def ^:private composite-registry
  (mr/composite-registry (mr/registry (m/default-schemas)) registry))

(defn valid-program-def?
  "True if x is a valid shader program definition."
  [x]
  (m/validate program-def x {:registry composite-registry}))

(defn valid-render-context?
  "True if x is a valid render-context value."
  [x]
  (m/validate render-context x {:registry composite-registry}))

(defn valid-render-shape?
  "True if x is a valid render-shape value."
  [x]
  (m/validate render-shape x {:registry composite-registry}))

(defn valid-volume-config?
  "True if x is a valid volume-config value."
  [x]
  (m/validate volume-config x {:registry composite-registry}))

(defn valid-volume-descriptor?
  "True if x is a valid volume-descriptor value."
  [x]
  (m/validate volume-descriptor x {:registry composite-registry}))
