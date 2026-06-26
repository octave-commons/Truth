# Phase 0 Volumetric Renderer — Design Document

## Problem Statement

The current `infra.render.phase0-renderer` draws every ECS entity — regardless
of its `matter-state` — as a single OpenGL point sprite via `GL_POINTS`.  A
diffuse nebula clump with a radius of `1e17` metres and a collapsed protostar
one AU across are both rendered identically: a single circle on screen, sized
by `(Math/log10 (+ 1 radius))`.

The domain model is correct.  `domain.stellar` gives every entity a physical
`:radius`, `:density`, `:temperature`, and `:pressure`.  Jeans instability is
computed from real thermodynamics.  The state transition

```
:nebula → :protostar → :star
```

is driven by physics, not scripts.  The renderer silently discards the
volumetric information that distinguishes a cloud from a point, and the nebula
draw pass that was partially written (`nebula-particles`, `nebula-program`,
`nebula-shader-source`) is never called from `render-frame`.

The fix is to honour the distinction the domain already makes.

---

## Core Principle

> **The renderer must reflect the ontological difference between a volume and a
> point.**

A `:nebula` or `:protostar` entity is a region of space.  Its `:radius` is the
physical extent of that region.  The correct visual representation is a
volumetric cloud whose glow fills that extent.

A `:star` or `:planet` entity is a collapsed body.  Its visual radius on screen
is an artistic choice (logarithmic brightness), not a spatial claim.  A point
sprite is appropriate here.

---

## Domain → Render Mapping

| `matter-state`     | Physical nature              | Render strategy                        | Shader         | Blend mode      |
|--------------------|------------------------------|----------------------------------------|----------------|-----------------|
| `:nebula`          | Large diffuse gas volume     | N distributed soft sprites over radius | nebula shader  | Additive `ONE ONE` |
| `:protostar`       | Contracting dense core       | Smaller sprite cloud + bright centre   | nebula shader  | Additive `ONE ONE` |
| `:star`            | Fusing point source          | Single bright sprite                   | stellar shader | Alpha `SRC_ALPHA ONE_MINUS_SRC_ALPHA` |
| `:planet`          | Solid/liquid rounded body    | Single sprite, no glow                 | stellar shader | Alpha `SRC_ALPHA ONE_MINUS_SRC_ALPHA` |

---

## Render Pass Architecture

`render-frame` must issue two ordered draw passes per frame.

### Pass 1 — Volumetric cloud pass

**Which entities:** All entities whose `matter-state` is `:nebula` or
`:protostar`.

**What to draw:** For each such entity, generate a cloud of `N` sample points
distributed inside a sphere of the entity's `:radius`, centred on its
`:position`.  This is what `nebula-particles` already does — it just needs to
be wired in and driven from ECS data rather than a standalone map.

**N (sample count):** Scale with focus level and radius.  A rough guide:

```clojure
(defn cloud-sample-count [region focus-level]
  (int (* 500 (+ 0.2 focus-level)
           (Math/log10 (+ 10 (:radius region))))))
```

Keep N low enough to stay real-time.  500–2000 per cloud is sufficient at
nebular scale.  Individual particles are semi-transparent; their accumulation
produces the volume.

**Shader:** Use `nebula-shader-source` with a Gaussian alpha falloff (see
below).

**Blend mode:** `GL_ONE / GL_ONE` (additive).  Many low-alpha particles
accumulate brightness in dense regions and fade to nothing at the edges.  This
is what produces the volumetric cloud appearance without ray-marching.

**Draw order:** Back to front by distance from camera, or simply draw all cloud
particles together before stellar bodies.  Because additive blending commutes,
draw order within the cloud pass does not affect correctness.

### Pass 2 — Stellar body pass

**Which entities:** All entities whose `matter-state` is `:star` or `:planet`.

**What to draw:** One point sprite per entity, as currently implemented in
`body-to-particle`.

**Shader:** `vertex-shader-source` + `fragment-shader-source` (existing).

**Blend mode:** `GL_SRC_ALPHA / GL_ONE_MINUS_SRC_ALPHA` (standard alpha
compositing).  Stars and planets composite cleanly on top of the cloud layer.

---

## Shader Changes

### `nebula-shader-source` — replace hard disc with Gaussian falloff

Current fragment shader discards at `dist > 0.5` and uses a linear
`1.0 - smoothstep` alpha.  This produces a hard-edged disc.

Replace with:

```glsl
#version 330 core
in vec3 fragColor;
out vec4 FragColor;

uniform float time;
uniform float density;

float hash(vec3 p) {
    return fract(sin(dot(p, vec3(12.9898, 78.233, 45.543))) * 43758.5453);
}

void main() {
    vec2 coord = gl_PointCoord - vec2(0.5);
    float dist = length(coord);

    // Gaussian falloff — no hard edge, contributes across the whole sprite
    float sigma = 0.25;
    float alpha = density * exp(-(dist * dist) / (2.0 * sigma * sigma));

    // Mild noise to break up regularity
    float n = hash(vec3(coord * 8.0, time * 0.05));
    alpha *= (0.7 + 0.3 * n);

    if (alpha < 0.001) discard;

    // Shift colour slightly toward emission lines at higher density
    vec3 color = fragColor * (1.0 + 0.4 * n * density);
    FragColor = vec4(color, alpha);
}
```

The key change: `exp(-dist²)` instead of `1 - dist`.  A Gaussian never hits
zero at the sprite edge — it just gets very small — so overlapping sprites
blend continuously rather than tiling as visible discs.

### Point size for cloud samples

Cloud sample sprites should be large — much larger than stellar point sprites —
because each sample represents a region of gas, not a body.  A reasonable
default:

```glsl
// In the vertex shader, for the nebula pass:
float base_size = aSize * 0.01;  // aSize is the entity's physical radius
float dist_factor = 1.0 / (1.0 + distance * 0.0000001);
gl_PointSize = clamp(base_size * dist_factor, 8.0, 256.0);
```

Clamp min to 8px so sparse clouds are still visible from far away.

---

## `render-frame` Restructure

```clojure
(defn render-frame
  [{:keys [shader-program nebula-program projection-matrix view-matrix
           camera-position time] :as renderer}
   world]

  (GL11/glClearColor 0.01 0.01 0.02 1.0)
  (GL11/glClear (bit-or GL11/GL_COLOR_BUFFER_BIT GL11/GL_DEPTH_BUFFER_BIT))
  (GL11/glEnable GL46/GL_PROGRAM_POINT_SIZE)

  ;; --- Pass 1: Volumetric cloud pass (additive) ---
  (GL11/glEnable GL11/GL_BLEND)
  (GL11/glBlendFunc GL11/GL_ONE GL11/GL_ONE)
  (GL11/glDepthMask false)   ; clouds don't write depth

  (GL20/glUseProgram nebula-program)
  ;; ... set uniforms: projection, view, cameraPos, time ...
  (let [cloud-particles (world->cloud-particles world (player-focus-level world))]
    (when (seq cloud-particles)
      (let [{:keys [vao count]} (create-vao cloud-particles)]
        (GL30/glBindVertexArray vao)
        (GL11/glDrawArrays GL11/GL_POINTS 0 count)
        (GL30/glBindVertexArray 0))))

  ;; --- Pass 2: Stellar body pass (alpha composite) ---
  (GL11/glBlendFunc GL11/GL_SRC_ALPHA GL11/GL_ONE_MINUS_SRC_ALPHA)
  (GL11/glDepthMask true)

  (GL20/glUseProgram shader-program)
  ;; ... set uniforms ...
  (let [body-particles (world->body-particles world)]
    (when (seq body-particles)
      (let [{:keys [vao count]} (create-vao body-particles)]
        (GL30/glBindVertexArray vao)
        (GL11/glDrawArrays GL11/GL_POINTS 0 count)
        (GL30/glBindVertexArray 0))))

  ;; --- Player sprite (always on top) ---
  (when-let [obs (player/get-observer world)]
    (GL20/glUniform1f (GL20/glGetUniformLocation shader-program "glow") 2.0)
    (let [{:keys [vao count]} (create-vao [(sprite-particle obs)])]
      (GL30/glBindVertexArray vao)
      (GL11/glDrawArrays GL11/GL_POINTS 0 count)
      (GL30/glBindVertexArray 0)))

  (assoc renderer :time (+ time 0.016)))
```

---

## New Function: `world->cloud-particles`

This replaces the dead `nebula-particles` helper with a version that reads
directly from ECS:

```clojure
(defn world->cloud-particles
  "For every volumetric entity (:nebula, :protostar), generate a cloud of
   distributed sample sprites across its physical radius."
  [world focus-level]
  (->> (ecs/entities-with world c/matter-state c/position c/radius)
       (filter (fn [eid]
                 (#{:nebula :protostar}
                  (ecs/get-component world eid c/matter-state))))
       (mapcat (fn [eid]
                 (let [region (stellar/entity->region world eid)
                       n      (cloud-sample-count region focus-level)]
                   (nebula-particles
                    {:center      (:position region)
                     :extent      (:radius region)
                     :density     (/ (:density region) 1e-18) ; normalise to ~0..1
                     :composition (:composition region)
                     :focus-level focus-level}))))))
```

The normalisation of `:density` into the 0–1 range passed to the shader as the
`density` uniform is important: raw SI density values (kg/m³) are tiny numbers
and would produce invisible sprites.  Tune the normalisation constant against
the actual density range produced by `domain.stellar/body-density` at nebular
scales.

---

## `world->body-particles` — gate on matter-state

The existing function should filter to only collapsed bodies:

```clojure
(defn world->body-particles [world]
  (->> (ecs/entities-with world c/matter-state c/position)
       (filter (fn [eid]
                 (#{:star :planet}
                  (ecs/get-component world eid c/matter-state))))
       (map (fn [eid]
              (body-to-particle
               {:position    (ecs/get-component world eid c/position)
                :radius      (or (ecs/get-component world eid c/radius) 1.0)
                :temperature (or (ecs/get-component world eid c/temperature) 3.0)
                :state       (ecs/get-component world eid c/matter-state)
                :luminosity  (or (ecs/get-component world eid c/luminosity) 0.0)})))))
```

Without this gate, a `:nebula` entity that is simultaneously drawn as a cloud
in Pass 1 would also draw as a point sprite in Pass 2, producing a bright dot
at the cloud's centre of mass.

---

## Visual Progression Through Phase 0

With these changes, Phase 0 should read visually as a continuous physical
process rather than a collection of objects appearing and disappearing:

1. **Opening** — The screen is filled by one or more large, dim, diffuse clouds
   of blue-violet hydrogen and red-orange helium.  The gas occupies most of the
   viewport.  There are no points, only volumes.

2. **Collapse begins** — One cloud brightens and contracts.  Its sample cloud
   becomes denser toward the centre as `:radius` shrinks under `collapse-system`.
   The Gaussian accumulation makes the core noticeably brighter than the
   envelope without any hard boundary.

3. **Protostar** — The central cloud is small and very bright.  The outer
   envelope is still visible as a dim halo.  This is the Kelvin-Helmholtz
   contraction phase.

4. **Ignition** — The entity transitions to `:star`.  It exits the cloud pass
   and enters the stellar pass: the diffuse halo disappears and a sharp bright
   point with a corona glow takes its place.  The surrounding ring clumps are
   still clouds.

5. **Accretion disk / planets** — Ring clumps contract or stabilise.  Those
   that stabilise transition to `:planet` and become sharp points.  Those still
   collapsing remain clouds until they either merge or cool.

---

## Performance Notes

- **VAO recreation per frame** — `create-vao` allocates a new VAO and VBOs
  every frame.  This works for prototyping but will produce GC pressure at
  scale.  For the nebula pass specifically, cloud geometry changes slowly
  relative to frame rate.  A future optimisation is to cache the cloud VAO and
  only regenerate it when the underlying entity's `:radius` or `:density`
  changes by more than a threshold.

- **Sample count budget** — Keep total cloud sprites (summed across all nebula
  entities) under ~5000 for stable 60Hz on modest hardware.  The
  `cloud-sample-count` formula above should be tuned against this budget.

- **Depth writes** — Cloud sprites must not write to the depth buffer
  (`glDepthMask false` during Pass 1).  If they do, cloud sprites can occlude
  stellar body sprites even though the cloud is semi-transparent.

---

## Files Affected

| File | Change |
|------|--------|
| `src/infra/render/phase0_renderer.clj` | Add `world->cloud-particles`; gate `world->body-particles` on `:star`/`:planet`; restructure `render-frame` into two passes; update `nebula-shader-source` to Gaussian falloff; fix blend modes |
| No domain changes required | The domain model is already correct |
