(ns infra.render.asset
  "Centralized GL resource lifecycle: program, mesh, and texture caches with
   uniform invalidate!/dispose! semantics.

   Program compile/link stays in `infra.render.shader` — it needs the
   `law.render` schema check and GL20 shader-object calls that are specific to
   GLSL compilation — but this namespace is the single front door the rest of
   the renderer uses to look up, invalidate, and tear down every cached GL
   resource, program, mesh, or texture alike. Domain-projection caches
   (`infra.render.scene.bodies/phase0-bodies-cache`, the volume namespace's
   froxel-texture cache) are NOT here by design: they cache render *shapes*
   derived from world state, not raw GL handles, and stay with their owning
   pass (see the parent spec's Open Question 2)."
  (:require
   [infra.render.shader :as shader])
  (:import
   (org.lwjgl.opengl GL11 GL15 GL30)))

;; ---------------------------------------------------------------------------
;; Mesh cache: mesh-key -> {:vao :vbo :ebo :count}
;; ---------------------------------------------------------------------------

(def ^:private mesh-cache-atom
  "mesh-key -> {:vao :vbo :ebo :count}. `:ebo` is optional (non-indexed
   meshes omit it)."
  (atom {}))

(def mesh-cache
  "Public read handle on the mesh cache atom, for inspection and tests."
  mesh-cache-atom)

(defn mesh!
  "Get-or-create the cached mesh entry for `mesh-key`. On a cache miss, calls
   the no-arg `build-fn` (expected to upload the mesh and return
   `{:vao :vbo :ebo :count}`) and caches the result."
  [mesh-key build-fn]
  (if-let [entry (get @mesh-cache-atom mesh-key)]
    entry
    (let [entry (build-fn)]
      (swap! mesh-cache-atom assoc mesh-key entry)
      entry)))

;; ---------------------------------------------------------------------------
;; Texture cache: texture-key -> {:id :width :height}
;; ---------------------------------------------------------------------------

(def ^:private texture-cache-atom
  "texture-key -> {:id :width :height}."
  (atom {}))

(def texture-cache
  "Public read handle on the texture cache atom, for inspection and tests."
  texture-cache-atom)

(defn texture!
  "Get-or-create the cached texture entry for `texture-key`. On a cache miss,
   calls the no-arg `build-fn` (expected to upload the texture and return
   `{:id :width :height}`) and caches the result."
  [texture-key build-fn]
  (if-let [entry (get @texture-cache-atom texture-key)]
    entry
    (let [entry (build-fn)]
      (swap! texture-cache-atom assoc texture-key entry)
      entry)))

;; ---------------------------------------------------------------------------
;; Program cache (delegates storage + compilation to infra.render.shader)
;; ---------------------------------------------------------------------------

(defn program-id
  "Return the cached GL program id for `program-name`, or nil if not yet
   compiled."
  [program-name]
  (shader/program-id program-name))

(defn program-entry
  "Return the cached `{:id :hash}` entry for `program-name`, or nil."
  [program-name]
  (get @shader/program-cache program-name))

;; ---------------------------------------------------------------------------
;; GL teardown seams (redefable in tests so cache-clearing logic can be
;; verified without a live OpenGL context)
;; ---------------------------------------------------------------------------

(defn delete-mesh-gl!
  "Delete the GPU buffers/arrays owned by a cached mesh entry."
  [{:keys [vao vbo ebo]}]
  (when vbo (GL15/glDeleteBuffers (int vbo)))
  (when ebo (GL15/glDeleteBuffers (int ebo)))
  (when vao (GL30/glDeleteVertexArrays (int vao))))

(defn delete-texture-gl!
  "Delete the GPU texture owned by a cached texture entry."
  [{:keys [id]}]
  (when id (GL11/glDeleteTextures (int id))))

;; ---------------------------------------------------------------------------
;; Invalidation (recompile/rebuild on next use) and disposal (full teardown)
;; ---------------------------------------------------------------------------

(defn invalidate-programs!
  "Delete every cached program and clear the program cache; the next
   `infra.render.shader/compile-program!` call recompiles it."
  []
  (shader/invalidate-all!))

(defn invalidate-meshes!
  "Delete every cached mesh's GL buffers and clear the mesh cache; the next
   `mesh!` call for a given key rebuilds it."
  []
  (doseq [[_key entry] @mesh-cache-atom] (delete-mesh-gl! entry))
  (reset! mesh-cache-atom {}))

(defn invalidate-textures!
  "Delete every cached texture and clear the texture cache; the next
   `texture!` call for a given key rebuilds it."
  []
  (doseq [[_key entry] @texture-cache-atom] (delete-texture-gl! entry))
  (reset! texture-cache-atom {}))

(defn invalidate-all!
  "Invalidate every asset cache: programs, meshes, and textures all rebuild
   on next use. Used by shader hot-reload and offscreen render entry points
   that must not reuse handles from a stale GL context."
  []
  (invalidate-programs!)
  (invalidate-meshes!)
  (invalidate-textures!))

(defn dispose-asset!
  "Tear down a single cached asset. `kind` is `:program`, `:mesh`, or
   `:texture`; `cache-key` is the cache key (program name, mesh key, texture
   key)."
  [kind cache-key]
  (case kind
    :program (shader/invalidate-program! cache-key)
    :mesh    (when-let [entry (get @mesh-cache-atom cache-key)]
               (delete-mesh-gl! entry)
               (swap! mesh-cache-atom dissoc cache-key))
    :texture (when-let [entry (get @texture-cache-atom cache-key)]
               (delete-texture-gl! entry)
               (swap! texture-cache-atom dissoc cache-key))
    nil))

(defn dispose-all!
  "Full GL teardown: delete every cached program, mesh, and texture. Call at
   context shutdown (e.g. offscreen render cleanup), not per-frame."
  []
  (invalidate-all!))
