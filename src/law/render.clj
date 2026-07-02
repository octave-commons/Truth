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
   [:position [:tuple :double :double :double]]
   [:radius {:optional true} :double]
   [:color {:optional true} [:tuple :double :double :double]]
   [:glow {:optional true} :double]
   [:label {:optional true} :string]])

(def registry
  "Malli registry for render schemas."
  {:law.render/glsl-type       glsl-type
   :law.render/shader-stage    shader-stage
   :law.render/program-def     program-def
   :law.render/viewport        viewport
   :law.render/render-context  render-context
   :law.render/render-shape    render-shape})

(def ^:private composite-registry
  (mr/composite-registry (mr/registry (m/default-schemas)) registry))

(defn valid-program-def? [x]
  (m/validate program-def x {:registry composite-registry}))

(defn valid-render-context? [x]
  (m/validate render-context x {:registry composite-registry}))

(defn valid-render-shape? [x]
  (m/validate render-shape x {:registry composite-registry}))
