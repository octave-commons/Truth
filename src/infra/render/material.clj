(ns infra.render.material
  "Material records: `{:program :uniforms :mesh :blend :depth}` describing one
   render pass as data.

   Before this namespace, each pass in `infra.render.scene.setup` repeated its
   own `glUseProgram` / `glGetUniformLocation` / `glUniform*` boilerplate,
   with the blend/depth toggle hand-rolled around it. A material record names
   the same information declaratively; `draw-material!` is the one place that
   turns a record into GL calls, via `infra.render.passes` for state and
   `infra.render.asset` for the program id.

   Per the parent spec, this deliberately does NOT add runtime uniform
   reflection — `:uniforms` is a plain map (or a no-arg thunk returning one)
   that the caller already knows the shape of; `passes/bind-uniforms!`
   sniffes GL call shape from value shape, nothing more."
  (:require
   [infra.render.asset :as asset]
   [infra.render.passes :as passes])
  (:import
   (org.lwjgl.opengl GL11 GL20 GL30)))

(defn material
  "Construct a material record. `:program` is either a program name (resolved
   via `infra.render.asset/program-id` at draw time) or an already-resolved GL
   program id, so a material can be built once per frame from a pass that has
   already looked its program up; `:uniforms` is a map of uniform name to
   value, or a no-arg fn returning that map for per-frame dynamic values;
   `:mesh` is a mesh-cache entry `{:vao :count ...}`; `:blend` is a
   `infra.render.passes/set-blend!` mode; `:depth` is `{:write? :test?}`
   (both default true — solid, depth-tested geometry)."
  [{:keys [program uniforms mesh blend depth]}]
  {:program program
   :uniforms (or uniforms {})
   :mesh mesh
   :blend blend
   :depth (merge {:write? true :test? true} depth)})

;; ---------------------------------------------------------------------------
;; GL call seams (redefable in tests)
;; ---------------------------------------------------------------------------

(defn- gl-use-program! [id] (GL20/glUseProgram id))
(defn- gl-bind-vao! [vao] (GL30/glBindVertexArray vao))
(defn- gl-draw-arrays! [mode count] (GL11/glDrawArrays mode 0 (int count)))

(defn draw-material!
  "Draw one instance of material record `mat`. `extra-uniforms` merges over
   (and overrides) the material's own `:uniforms` for this instance — e.g. a
   per-body `model` matrix and `color` the caller computes per draw call.
   `draw-mode` defaults to `GL_TRIANGLES`. No-op if the material's program
   has not been compiled yet or its mesh is absent."
  ([mat] (draw-material! mat {}))
  ([mat extra-uniforms] (draw-material! mat extra-uniforms GL11/GL_TRIANGLES))
  ([{:keys [program uniforms mesh blend depth]} extra-uniforms draw-mode]
   (let [pid (if (keyword? program) (asset/program-id program) program)]
     (when (and pid (pos? (int pid)) mesh)
       (passes/set-blend! blend)
       (passes/set-depth-write! (:write? depth true))
       (when (contains? depth :test?) (passes/set-depth-test! (:test? depth)))
       (gl-use-program! pid)
       (let [base (if (fn? uniforms) (uniforms) uniforms)
             u    (merge base extra-uniforms)]
         (passes/bind-uniforms! pid u))
       (gl-bind-vao! (:vao mesh))
       (gl-draw-arrays! draw-mode (:count mesh))
       (gl-bind-vao! 0)))))
