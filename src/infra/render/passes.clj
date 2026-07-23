(ns infra.render.passes
  "GL pass-state helpers: blend, depth, cull, and uniform binding.

   Every render pass used to repeat the same handful of raw GL calls
   (`glEnable GL_BLEND` / `glBlendFunc` / `glDepthMask` / …) inline, so a typo
   in one pass silently diverged from the others. This namespace names the
   handful of states a pass actually needs — a blend mode, a depth-write/test
   toggle, a cull mode, a uniform value — and is the single place that
   translates them into GL calls.

   The GL calls themselves are split into private one-line seams
   (`gl-enable!`, `gl-blend-func!`, …) purely so tests can `with-redefs` them
   to assert *which* GL state a pass requested without a live OpenGL context."
  (:import
   (org.lwjgl.opengl GL11 GL20)))

;; ---------------------------------------------------------------------------
;; GL call seams (redefable in tests; the only functions that touch GL here)
;; ---------------------------------------------------------------------------

(defn- gl-enable! [cap] (GL11/glEnable cap))
(defn- gl-disable! [cap] (GL11/glDisable cap))
(defn- gl-blend-func! [src dst] (GL11/glBlendFunc src dst))
(defn- gl-depth-mask! [write?] (GL11/glDepthMask (boolean write?)))
(defn- gl-cull-face! [mode] (GL11/glCullFace mode))
(defn- gl-uniform1i! [loc v] (GL20/glUniform1i loc (int v)))
(defn- gl-uniform1f! [loc v] (GL20/glUniform1f loc (float v)))
(defn- gl-uniform3f! [loc a b c] (GL20/glUniform3f loc (float a) (float b) (float c)))
(defn- gl-uniform4f! [loc a b c d] (GL20/glUniform4f loc (float a) (float b) (float c) (float d)))
(defn- gl-uniform-matrix4fv! [loc m] (GL20/glUniformMatrix4fv loc false m))
(defn- gl-uniform-location [program name] (GL20/glGetUniformLocation program name))

;; ---------------------------------------------------------------------------
;; Blend / depth / cull state
;; ---------------------------------------------------------------------------

(defn set-blend!
  "Set the blend state for `mode`: `:alpha` (standard SRC_ALPHA over),
   `:additive` (light-scattering additive), or `nil`/`:none` to disable
   blending entirely."
  [mode]
  (case mode
    :alpha    (do (gl-enable! GL11/GL_BLEND)
                  (gl-blend-func! GL11/GL_SRC_ALPHA GL11/GL_ONE_MINUS_SRC_ALPHA))
    :additive (do (gl-enable! GL11/GL_BLEND)
                  (gl-blend-func! GL11/GL_ONE GL11/GL_ONE_MINUS_SRC_ALPHA))
    (gl-disable! GL11/GL_BLEND)))

(defn set-depth-write!
  "Enable or disable writes to the depth buffer for the current pass."
  [write?]
  (gl-depth-mask! write?))

(defn set-depth-test!
  "Enable or disable the depth test for the current pass."
  [enabled?]
  (if enabled? (gl-enable! GL11/GL_DEPTH_TEST) (gl-disable! GL11/GL_DEPTH_TEST)))

(defn set-cull!
  "Set face culling for `mode`: `:back`, `:front`, or `nil`/`:none` to
   disable culling."
  [mode]
  (case mode
    :back  (do (gl-enable! GL11/GL_CULL_FACE) (gl-cull-face! GL11/GL_BACK))
    :front (do (gl-enable! GL11/GL_CULL_FACE) (gl-cull-face! GL11/GL_FRONT))
    (gl-disable! GL11/GL_CULL_FACE)))

;; ---------------------------------------------------------------------------
;; Uniform binding
;; ---------------------------------------------------------------------------

(defn- set-uniform!
  "Set one uniform by GLSL-shape sniffing on `v`: 16 floats -> mat4, 4 -> vec4,
   3 -> vec3, integer -> int, else float. `v` may be a Clojure sequence OR a
   primitive array (e.g. the raw `float[]` model matrices `infra.render.math`
   builds); both are normalized to a vector before their length is sniffed.
   Array uniforms and samplers are out of scope — pass their locations
   explicitly via `bind-uniforms!` callers that already know the shape (e.g.
   the volume pass's light arrays)."
  [program name v]
  (let [loc (gl-uniform-location program name)
        v   (if (number? v) v (vec v))]
    (cond
      (and (sequential? v) (= 16 (count v))) (gl-uniform-matrix4fv! loc (float-array v))
      (and (sequential? v) (= 4 (count v)))  (let [[a b c d] v] (gl-uniform4f! loc a b c d))
      (and (sequential? v) (= 3 (count v)))  (let [[a b c] v] (gl-uniform3f! loc a b c))
      (int? v)                               (gl-uniform1i! loc v)
      :else                                  (gl-uniform1f! loc (double v)))))

(defn bind-uniforms!
  "Set every `{uniform-name value}` pair in `uniforms` on the currently bound
   `program`. Dispatches the GL call shape from the value's own shape (see
   `set-uniform!`); `program` must already be current (`glUseProgram`)."
  [program uniforms]
  (doseq [[name v] uniforms]
    (set-uniform! program (clojure.core/name name) v)))
