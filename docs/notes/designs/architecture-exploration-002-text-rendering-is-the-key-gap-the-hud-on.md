● ToolUse(Read)
⎿ 900	(defn player-overlay-shapes
  901	  "Render shapes for the player's spark and focus volume: a bright point at the
  902	   observer position and a reticle ring at the focus, tinted by coherence. Empty
  903	   when the world has no observer (e.g. bare test worlds)."
  904	  [world scale]
  905	  (if-let [obs (player/get-observer world)]
  906	    (let [scl   (fn [p] (mapv #(/ (double %) scale) p))
  907	          fpos  (scl (:focus-position obs))
  908	          fr    (/ (double (:focus-radius obs)) scale)
  909	          spark (scl (:position obs))
  910	          col   (coherence-color (player/decoherence-state obs))]
  911	      (into [{:position spark :color [0.85 0.96 1.0]
  912	              :size (+ 28.0 (* 44.0 (double (:focus-intensity obs 0.5))))
  913	              :render-mode :particle}]
  914	            (ring-segments fpos (max 0.5 fr) col 48)))
  915	    []))
  916	
  917	(defn hud-rects-from-world
  918	  "HUD rectangles (NDC) for the observer: a coherence track + fill (tinted by
  919	   decoherence state) bottom-left, and a thin focus-intensity bar above it.
  920	   Empty when there is no observer."
  921	  [world]
  922	  (if-let [obs (player/get-observer world)]
  923	    (let [coh  (double (or (:coherence obs) 0.0))
  924	          mx   (double (or (:max-coherence obs) 1.0))
  925	          frac (max 0.0 (min 1.0 (/ coh (max 1e-9 mx))))
  926	          fi   (double (or (:focus-intensity obs) 0.5))
  927	          col  (conj (coherence-color (player/decoherence-state obs)) 0.92)
  928	          x0 -0.96 x1 -0.46 y0 -0.93 y1 -0.89]
  929	      [{:x0 x0 :y0 y0 :x1 x1 :y1 y1 :color [0.10 0.10 0.16 0.65]}                 ;; coherence track
  930	       {:x0 x0 :y0 y0 :x1 (+ x0 (* (- x1 x0) frac)) :y1 y1 :color col}            ;; coherence fill
  931	       {:x0 x0 :y0 -0.875 :x1 (+ x0 (* (- x1 x0) fi)) :y1 -0.86                   ;; focus intensity
  932	        :color [0.70 0.86 1.0 0.85]}])
  933	    []))
  934	
  935	(defn phase0-bodies-from-world
  936	  "Project Phase 0 ECS matter entities into stylized, view-scaled render shapes,
  937	   coloured by TEMPERATURE so the thermal field is visible:
  938	     :nebula    → one soft fog puff (the diffuse cloud)
  939	     :protostar → a compact bright cloud + a magnetic field line (contracting core)
  ──── (70 lines hidden) ──────────────────────────────────────────────
  1010	              [{:entity      eid
  1011	                :position    center
  1012	                :radius      (phys->render-radius r-phys)
  1013	                :color       color
  1014	                :kind        state
  1015	                :oblateness  ob
  1016	                :rotation-axis axis
  1017	                :render-mode :body}])))
  1018	       (ecs/entities-with world c/position c/matter-state))))))
  1019	
  1020	(defn render-scene
  1021	  "Render a frame with volumetric fog particles and glowing 3D massive bodies.
  1022	   `bodies` is a sequence of render maps; `:render-mode` may be `:particle`
  1023	   (soft fog puff) or `:body` (shaded sphere). Default is `:body`."
  1024	  [{:keys [body-program particle-program line-program hud-program hud]} mesh-world camera width height bodies time]
  1025	  (GL11/glEnable GL11/GL_DEPTH_TEST)
  1026	  (GL11/glClearColor 0.02 0.02 0.05 1.0)
  1027	  (GL11/glClear (bit-or GL11/GL_COLOR_BUFFER_BIT GL11/GL_DEPTH_BUFFER_BIT))
  1028	  (let [proj (perspective 60.0 (/ width (float height)) 0.1 10000.0)
  1029	        view (look-at (:position camera) (:target camera) (sp/vec3 0.0 1.0 0.0))
● ToolUse(Read)
⎿ 260	
  261	(def ^:private hud-fragment-shader
  262	  "#version 330 core
  263	   out vec4 FragColor;
  264	   uniform vec4 hudColor;
  265	   void main() { FragColor = hudColor; }")
  266	
  267	(defn create-hud-program []
  268	  (println "Compiling HUD shaders...")
  269	  (link-program (compile-shader hud-vertex-shader GL20/GL_VERTEX_SHADER)
  270	                (compile-shader hud-fragment-shader GL20/GL_FRAGMENT_SHADER)))
  271	
  272	(defn- hud-quad-floats [x0 y0 x1 y1]
  273	  (float-array [x0 y0  x1 y0  x1 y1   x0 y0  x1 y1  x0 y1]))
  274	
  275	(defn render-hud
  276	  "Draw a list of HUD rectangles. Each rect is {:x0 :y0 :x1 :y1 :color [r g b a]}
  277	   in NDC. No-op without a program or rects."
  278	  [hud-program rects]
  279	  (when (and hud-program (pos? (int hud-program)) (seq rects))
  280	    (GL20/glUseProgram hud-program)
  281	    (GL11/glEnable GL11/GL_BLEND)
  282	    (GL11/glBlendFunc GL11/GL_SRC_ALPHA GL11/GL_ONE_MINUS_SRC_ALPHA)
  283	    (GL11/glDepthMask false)
  284	    (let [loc (GL20/glGetUniformLocation hud-program "hudColor")]
  285	      (doseq [{:keys [x0 y0 x1 y1 color]} rects]
  286	        (let [[r g b a] color
  287	              data (hud-quad-floats x0 y0 x1 y1)
  288	              fb   (BufferUtils/createFloatBuffer (count data))
  289	              vao  (GL30/glGenVertexArrays)
  290	              vbo  (GL15/glGenBuffers)]
  291	          (doseq [f data] (.put fb (float f)))
  292	          (.flip fb)
  293	          (GL30/glBindVertexArray vao)
  294	          (GL15/glBindBuffer GL15/GL_ARRAY_BUFFER vbo)
  295	          (GL15/glBufferData GL15/GL_ARRAY_BUFFER fb GL15/GL_STATIC_DRAW)
  296	          (GL20/glVertexAttribPointer 0 2 GL11/GL_FLOAT false 0 0)
  297	          (GL20/glEnableVertexAttribArray 0)
  298	          (GL20/glUniform4f loc (float r) (float g) (float b) (float (or a 1.0)))
  299	          (GL11/glDrawArrays GL11/GL_TRIANGLES 0 6)
● ToolUse(Bash)
⎿ 600:  (GLFW/glfwWindowHint GLFW/GLFW_CONTEXT_VERSION_MAJOR 3)
  601:  (GLFW/glfwWindowHint GLFW/GLFW_CONTEXT_VERSION_MINOR 3)
  610:    (GLFW/glfwMakeContextCurrent window)
  1120:    (GLFW/glfwMakeContextCurrent window)
  1126:        color   (GL11/glGenTextures)
  1129:    ;; color texture
  1130:    (GL11/glBindTexture GL11/GL_TEXTURE_2D color)
  1131:    (GL11/glTexImage2D GL11/GL_TEXTURE_2D 0 GL11/GL_RGBA width height 0 GL11/GL_RGBA GL11/GL_UNSIGNED_BYTE nil)
  1132:    (GL11/glTexParameteri GL11/GL_TEXTURE_2D GL11/GL_TEXTURE_MIN_FILTER GL11/GL_LINEAR)
  1133:    (GL11/glTexParameteri GL11/GL_TEXTURE_2D GL11/GL_TEXTURE_MAG_FILTER GL11/GL_LINEAR)
  1134:    (GL30/glFramebufferTexture2D GL30/GL_FRAMEBUFFER GL30/GL_COLOR_ATTACHMENT0 GL11/GL_TEXTURE_2D color 0)
  1161:  "Render the current world to a PNG file using an offscreen OpenGL context.
❯ 
● ToolUse(Bash)
⎿  :deps {org.clojure/clojure {:mvn/version "1.11.1"}
          org.lwjgl/lwjgl {:mvn/version "3.3.3"}
          org.lwjgl/lwjgl-glfw {:mvn/version "3.3.3"}
          org.lwjgl/lwjgl-opengl {:mvn/version "3.3.3"}
          org.lwjgl/lwjgl-stb {:mvn/version "3.3.3"}
          org.lwjgl/lwjgl$natives-linux {:mvn/version "3.3.3"}
          org.lwjgl/lwjgl-glfw$natives-linux {:mvn/version "3.3.3"}
          org.lwjgl/lwjgl-opengl$natives-linux {:mvn/version "3.3.3"}
          org.lwjgl/lwjgl-stb$natives-linux {:mvn/version "3.3.3"}}
  ---SERVER---
  (ns infra.dev.server
    "Development service entry point.
  
     Starts a dedicated GLFW window on a background thread and an nREPL
     server so you can connect from another terminal/Emacs/Cursive and
     mutate the running simulation in real time.
  
     Run:
       clj -M:dev
  
     Connect from a second terminal:
       clj -M:repl --connect localhost:7888
  
     Then explore:
       (require '[infra.dev.window :as w])
       @(:camera @w/service-state)
       (swap! (:camera @w/service-state) assoc :distance 400.0)
       (w/reload-shaders!)
       (w/take-screenshot! \"/tmp/truth-dev.png\")
  
     Camera controls in the window:
       C              cycle camera mode (manual / track-largest-cluster / fit-all)
       [ / ]          decrease / increase fit margin
       R              reset camera and settings
       LMB drag       orbit (manual modes)
       scroll         adjust distance"
    (:require
      [nrepl.server     :as nrepl]
      [infra.dev.window :as window]
      [infra.render     :as render]
  ──── (12 lines hidden) ──────────────────────────────────────────────
    (let [world  (atom (phase0/create-world))
          _      (window/start! world
                   {:tick-fn            phase0/tick-world
                    :bodies-fn          render/phase0-bodies-from-world
                    :camera             (render/make-camera 60.0)
                    :sim-frame-interval 4
                    :on-step            (fn [w]
                                          (when (zero? (mod (:tick w) 40))
                                            (println (phase0/field-report w)))
                                          (if (:phase0/active w)
                                            w
                                            (phase0/create-world)))})
          server (nrepl/start-server :port 7888 :bind "127.0.0.1")]
      (println "nREPL server listening on 127.0.0.1:7888")
      (.addShutdownHook
        (Runtime/getRuntime)
        (Thread. #(do (window/stop!)
                      (nrepl/stop-server server)
                      (println "Dev service shut down."))))
      @(promise)))
● Text rendering is the key gap — the HUD only draws solid NDC rectangles, no glyphs. But `lwjgl-stb` is already a dependency, so `STBEasyFont` gives us ASCII text cheaply. Let me check the HUD shader and how the clock value currently flows.
● ToolUse(Read)
⎿ 240	
  241	(def ^:private line-fragment-shader
  242	  "#version 330 core
  243	   in vec3 vColor;
  244	   out vec4 FragColor;
  245	   void main() { FragColor = vec4(vColor, 0.85); }")
  246	
  247	(defn create-line-program []
  248	  (println "Compiling line shaders...")
  249	  (link-program (compile-shader line-vertex-shader GL20/GL_VERTEX_SHADER)
  250	                (compile-shader line-fragment-shader GL20/GL_FRAGMENT_SHADER)))
  251	
  252	;; --- HUD overlay (2D, screen-space) -----------------------------------------
  253	;; Filled rectangles given directly in normalized device coordinates [-1,1], so
  254	;; the coherence bar and focus indicator sit fixed on screen regardless of camera.
  255	
  256	(def ^:private hud-vertex-shader
  257	  "#version 330 core
  258	   layout(location = 0) in vec2 aPos;
  259	   void main() { gl_Position = vec4(aPos, 0.0, 1.0); }")
  260	
  261	(def ^:private hud-fragment-shader
● ToolUse(Read)
⎿ 1095	                       0.1)]
  1096	            (GL20/glUniformMatrix4fv (GL20/glGetUniformLocation body-program "model") false model)
  1097	            (GL20/glUniform3f (GL20/glGetUniformLocation body-program "color") (float r) (float g) (float b))
  1098	            (GL20/glUniform1f (GL20/glGetUniformLocation body-program "glow") (float glow))
  1099	            (GL11/glDrawArrays GL11/GL_TRIANGLES 0 (:count mesh-world))))
  1100	        (GL30/glBindVertexArray 0)))
  1101	    ;; ---- pass 3: 2D HUD overlay (coherence, focus) ----
  1102	    (render-hud hud-program hud)
  1103	    (GL20/glUseProgram 0)
  1104	    (GL11/glDisable GL11/GL_BLEND)))
  1105	
  1106	(defn render-bodies
● ToolUse(Read)
⎿ 110	(deftest test-coherence-color
  111	  (testing "Coherent reads cool/teal, fading reads warm/red"
  112	    (let [hi (r/coherence-color :highly-coherent)
  113	          lo (r/coherence-color :fading)]
  114	      (is (> (nth hi 1) (nth hi 0)) "coherent: green over red")
  115	      (is (> (nth lo 0) (nth lo 2)) "fading: red over blue"))))
  116	
  117	(deftest test-hud-rects-from-world
  118	  (testing "Coherence fill stays within its track; HUD empty without an observer"
  119	    (let [w     (phase0/create-world {:gas-count 10})
  120	          rects (r/hud-rects-from-world w)
  121	          track (first rects)
  122	          fill  (second rects)]
  123	      (is (>= (count rects) 2))
  124	      (is (<= (:x1 fill) (+ 1e-9 (:x1 track))) "fill never exceeds the track width")
  125	      (is (= [] (r/hud-rects-from-world (ecs/empty-world)))))))
  126	
  127	(deftest test-focus-input-moves-and-resizes
  128	  (testing "handle-input drives the observer focus (the player's controls)"
  129	    (let [w   (phase0/create-world {:gas-count 10})
● I have a clear picture now. The architecture is clean: one ECS world, one renderer, pacing currently fixed (`sim-frame-interval 4`, `dt 1e12`). The key facts:
  
  - **No text rendering exists** — the HUD only draws solid NDC rectangles (coherence/focus bars). But `lwjgl-stb` is already a dependency, so `STBEasyFont` gives us ASCII glyphs that I can feed through the *existing* solid-color HUD shader (no new shader needed).
  - `:phase0/sim-time`, `:phase0/complexity`, and `:phase0/time-scale` already exist in the world, but `time-scale` is currently vestigial (computed, not applied). That's the natural hook for an adaptive clock.
  
  Before I build this out across the renderer, window loop, and domain, let me lock down the gamification feel — these drive the whole layout and pacing and are expensive to redo.
● ToolUse(AskUserQuestion)
⎿ Your questions have been answered: "How dramatic should the time-dilation be as the system gains complexity (nebula → protostar → ignition → planets)?"="Strong dilation (Recommended)" selected preview:
  nebula     ~50,000 yr/s
  protostar  ~2,000 yr/s
  ignition   ~50 yr/s
  planets    ~5 yr/s, "Which readouts do you want on the HUD panel? (the clock is always included)"="Average temperature, Entity / body counts, Total system mass, Current phase name", "Where should the stats panel sit, given the coherence/focus bars are bottom-left?"="Top-left (Recommended)". You can now continue with these answers in mind.
● Strong dilation + all four stats + top-left. That settles the design. Two findings that shape the implementation:
  
  1. **Strong dilation forces adaptive `dt`.** To advance only ~5 yr/s smoothly at planet stage, the integration step itself must shrink to ~years — a fixed `dt=1e12` (≈31,000 yr) would make late game advance one giant jump every ~100 minutes (frozen). So `dt` (and the softening tied to it) must scale down with formation progress. This also *is* "smaller time steps," and finer steps are strictly more stable, so it's safe.
  
  Let me confirm the integrator's dt-sensitivity and check what tests pin the current `time-scale`/`complexity` contracts before I touch them.
● ToolUse(Bash)
⎿ test/domain/phase0_test.clj:49:(deftest test-time-scale
  test/domain/phase0_test.clj:50:  (testing "Time scale starts around centuries per tick and slows as complexity rises"
  test/domain/phase0_test.clj:52:          ts0 (:phase0/time-scale w0)]
  test/domain/phase0_test.clj:53:      (is (< 1e10 ts0 1e12) "initial time-scale is nebular-scale centuries per tick")
  test/domain/phase0_test.clj:54:      (is (> (stellar/time-scale-from-complexity 1)
  test/domain/phase0_test.clj:55:             (stellar/time-scale-from-complexity 50)))))
  test/domain/phase0_test.clj:59:                 (assoc :phase0/time-scale 1e10))
  test/domain/phase0_test.clj:63:      ;; directly, but we can verify the time-scale is being read and the
  test/domain/phase0_test.clj:177:      (is (> (:phase0/sim-time final) 0.0))
  test/domain/physics/collision_test.clj:83:                               (assoc :sim/dt 1.0)))
● ToolUse(Read)
⎿ 1	(ns domain.orbital.system
  2	  "Orbital physics system for Gates of Truth.
  3	   Queries position + velocity + mass components via ECS.
  4	   Returns world with updated position + velocity components."
  5	  (:require
  6	    [domain.ecs.core        :as ecs]
  7	    [domain.ecs.components  :as c]
  8	    [domain.ecs.parallel    :as par]
  9	    [domain.gravity.barnes-hut :as bh]
  10	    [domain.orbital.integrator :as integrator]
  11	    [shape.spatial          :as sp]))
  12	
  13	(defn- world->bodies
  14	  "Project ECS world into a seq of body maps for the Barnes-Hut tree."
  15	  [world]
  16	  (map (fn [[eid comps]]
  17	         {:id       eid
  18	          :mass     (comps c/mass)
  19	          :radius   (comps c/radius)
  20	          :kind     (comps c/body-kind)
  21	          :position (comps c/position)
  22	          :velocity (comps c/velocity)})
  23	       (ecs/all-of world c/position c/velocity c/mass c/radius c/body-kind)))
  24	
  25	(defn- apply-body-back
  26	  "Write updated position and velocity for eid back into world."
  27	  [world eid body]
  28	  (-> world
  29	      (ecs/put-component eid c/position (:position body))
  30	      (ecs/put-component eid c/velocity (:velocity body))))
  31	
  32	(defn- total-acceleration
  33	  "Gravitational acceleration from the Barnes–Hut tree plus any pre-computed
  34	   hydrodynamic pressure-gradient acceleration stored on `c/hydro-accel`."
  35	  [G theta softening world tree body]
  36	  (let [grav (bh/acceleration G theta softening tree body)
  37	        hydro (or (ecs/get-component world (:id body) c/hydro-accel)
  38	                  [0.0 0.0 0.0])]
  39	    (sp/v+ grav hydro)))
  40	
  ──── (5 lines hidden) ──────────────────────────────────────────────
  46	   The Barnes–Hut tree is immutable once built, so per-body accelerations are
  47	   computed in parallel (pmap) across cores — the single most expensive part of
  48	   the tick — and the results applied sequentially. `softening` is the Plummer
  49	   length passed to the gravity kernel."
  50	  ([G theta dt] (orbital-system G theta dt bh/default-softening))
  51	  ([G theta dt softening]
  52	   (fn [world]
  53	     (let [bodies  (world->bodies world)
  54	           tree    (bh/build-tree bodies)
  55	           updated (par/par-mapv
  56	                    (fn [body]
  57	                      (integrator/leapfrog-step
  58	                       body
  59	                       (fn [b] (total-acceleration G theta softening world tree b))
  60	                       dt))
  61	                    bodies)]
  62	       (reduce (fn [w body] (apply-body-back w (:id body) body))
  63	               world
  64	               updated)))))
  65	
