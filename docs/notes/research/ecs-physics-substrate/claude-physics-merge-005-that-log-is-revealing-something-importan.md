● That log is revealing something important. The running dev window does **not** use the code I changed. Look at `infra.dev.server`:
  
  ```clojure
  [domain.particles.phase0 :as phase0]   ; NOT domain.phase0
  [infra.render :as render]              ; NOT infra.render.phase0-renderer
  ```
  
  The live app runs the **particle / particle-mesh path** (`domain.particles.phase0` + `infra.render`), while I put the EM/regime/field work into the **ECS path** (`domain.phase0` + `infra.render.phase0_renderer`) — which is what the *tests* exercise, but not what's on screen. So none of my changes are visible in that window. Let me confirm by reading the particle path before saying more.
● ToolUse(Read)
⎿ 1	(ns domain.particles.phase0
  2	  "Particle-based Phase 0: a statistical gas cloud that collapses under its own
  3	   gravity, fragments into protostars, and flattens into a disk through
  4	   inelastic accretion.
  5	
  6	   This is the high-scale representation of stellar formation. The gas cloud is
  7	   a flat primitive-array field (domain.particles.field) accelerated by a
  8	   particle-mesh Poisson solver. Massive accreted clumps are promoted to resolved
  9	   ECS entities so the same rendering, observer, and habitability machinery can
  10	   attach to them.
  11	
  12	   Units are natural: G = 1, cloud radius ~ O(10), particle mass ~ O(0.001),
  13	   so collapse happens in hundreds of steps and is watchable."
  14	  (:require
  15	   [domain.particles.field  :as field]
  16	   [domain.particles.pm     :as pm]
  17	   [domain.ecs.core         :as ecs]
  18	   [domain.ecs.components   :as c]
  19	   [domain.ecs.event        :as event]
  20	   [domain.player           :as player]
  21	   [domain.stellar          :as stellar]
  22	   [domain.physics.collision :as collision]
  23	   [law.stellar             :as law]
  24	   [shape.spatial           :as sp]))
  25	
  26	;; --- Natural-unit defaults --------------------------------------------------
  27	
  28	(def ^:const default-cap 2048)
  29	(def ^:const default-grid 32)
  30	(def ^:const default-box 40.0)
  31	(def ^:const default-cloud-r 10.0)
  32	(def ^:const default-particle-mass 0.001)
  33	(def ^:const default-r0 0.3)
  34	(def ^:const default-m0 0.001)
  35	(def ^:const default-dt 0.3)
  36	(def ^:const default-spin 0.04)
  37	(def ^:const default-turb 0.05)
  38	(def ^:const default-seeds 4)
  39	(def ^:const default-seed-r 2.0)
  40	(def ^:const sink-threshold 0.02)
  ──── (315 lines hidden) ──────────────────────────────────────────────
  356	
  357	(defn particle-bodies
  358	  "Project live particles into the stylised render shape used by infra.render.
  359	   Gas particles are rendered as large diffuse fog clouds — the dominant visual
  360	   element of the nebula."
  361	  [world]
  362	  (let [f (:phase0/field world)
  363	        ^doubles px (.px f) ^doubles py (.py f) ^doubles pz (.pz f)
  364	        ^doubles mass (.mass f) ^doubles radius (.radius f)
  365	        cap (.cap f)]
  366	    (vec
  367	     (for [i (range cap)
  368	           :when (pos? (aget mass i))]
  369	       {:entity   i
  370	        :position [(aget px i) (aget py i) (aget pz i)]
  371	        :radius   (max 0.1 (aget radius i))
  372	        :size     (+ 60.0 (* 60.0 (Math/random)))
  373	        :color    [0.75 0.55 0.95]
  374	        :kind     :nebula}))))
  375	
❯ 
● Now the picture is clear, and it changes things. There are **two parallel Phase 0 implementations** in this repo:
  
  | | ECS path | Particle path |
  |---|---|---|
  | Sim | `domain.phase0` | `domain.particles.phase0` |
  | Renderer | `infra.render.phase0_renderer` | `infra.render` |
  | Gas model | N-body clumps (entities) | 2048-particle field + particle-mesh Poisson solver |
  | What I added EM to | ✅ this one | ❌ |
  | **What the live window runs** | ❌ | ✅ **this one** |
  
  I put the magnetic field, flux-freezing, regime classifier, and field-line rendering into the **ECS path** — which is what all the unit tests and the design doc reference, and it's correct and green (101 tests). But `infra.dev.server` boots `domain.particles.phase0` + `infra.render`, so **none of it shows up in the window you're watching.** The particle path is gravity-only (though it already seeds spin `0.04` + turbulence `0.05`, which is why it rotates).
  
  Let me read the live render path and the particle field struct to scope what porting would take, then I'll lay out the choice rather than guess.
● ToolUse(Read)
⎿ 1	(ns infra.render
  2	  "Minimal LWJGL + OpenGL renderer for Gates of Truth.
  3	   Renders ECS bodies as wireframe spheres with a controllable camera."
  4	  (:require
  5	    [domain.ecs.core :as ecs]
  6	    [domain.ecs.components :as c]
  7	    [domain.orbital.system :as orbital]
  8	    [domain.particles.phase0 :as pphase0]
  9	    [shape.spatial :as sp])
  10	  (:import
  11	    (org.lwjgl.glfw GLFW Callbacks GLFWErrorCallback GLFWKeyCallback GLFWCursorPosCallback GLFWScrollCallback)
  12	    (org.lwjgl.opengl GL GL11 GL15 GL20 GL30)
  13	    (org.lwjgl.stb STBImageWrite)
  14	    (org.lwjgl.system MemoryUtil)
  15	    (org.lwjgl BufferUtils)
  16	    (java.nio ByteBuffer)))
  17	
  18	;; ---------------------------------------------------------------------------
  19	;; Math helpers
  20	;; ---------------------------------------------------------------------------
  21	
  22	(defn- deg->rad [d] (* d (/ Math/PI 180.0)))
  23	
  24	(defn- normalize [[x y z]]
  25	  (let [len (Math/sqrt (+ (* x x) (* y y) (* z z)))]
  26	    (if (zero? len)
  27	      [0.0 0.0 1.0]
  28	      [(/ x len) (/ y len) (/ z len)])))
  29	
  30	(defn- cross [[ax ay az] [bx by bz]]
  31	  [(- (* ay bz) (* az by))
  32	   (- (* az bx) (* ax bz))
  33	   (- (* ax by) (* ay bx))])
  34	
  35	(defn- perspective [fov-deg aspect near far]
  36	  (let [f (/ 1.0 (Math/tan (/ (deg->rad fov-deg) 2.0)))
  37	        nf (/ 1.0 (- near far))]
  38	    (float-array [(/ f aspect) 0.0 0.0 0.0
  39	                  0.0 f 0.0 0.0
  40	                  0.0 0.0 (* (+ far near) nf) -1.0
  ──── (590 lines hidden) ──────────────────────────────────────────────
  631	        mesh   (upload-mesh sphere)]
  632	    (println "Window created, entering render loop...")
  633	    (setup-input window camera keys)
  634	    (loop []
  635	      (when (not (GLFW/glfwWindowShouldClose window))
  636	        (GLFW/glfwPollEvents)
  637	        ;; Simulate one tick per frame
  638	        (swap! world-atom (fn [w] ((orbital/orbital-system 6.674e-11 0.5 0.5) w)))
  639	        (let [bodies (bodies-from-world @world-atom)]
  640	          (render-scene {:body-program body-program :particle-program particle-program}
  641	                        mesh @camera width height bodies 0.0))
  642	        (GLFW/glfwSwapBuffers window)
  643	        (Thread/sleep 16)
  644	        (recur)))
  645	    (println "Shutting down renderer...")
  646	    (GLFW/glfwDestroyWindow window)
  647	    (Callbacks/glfwFreeCallbacks window)
  648	    (GLFW/glfwTerminate)
  649	    (GLFW/glfwSetErrorCallback nil)))
  650	
● I now have the full picture, and I owe you a straight answer before doing more work.
  
  ## The situation
  
  What you're watching in that pm2 window is the **particle path**, and my EM/field work went into the **ECS path**. Concretely, the live `render-scene` draws gas from `domain.particles.phase0/particle-bodies`, which colours every gas particle a fixed purple `[0.75 0.55 0.95]` and runs gravity-only physics (`particles.field` + `particles.pm`). My magnetic field, flux-freezing, regime classifier, and field-line renderer all live in `domain.phase0` / `infra.render.phase0_renderer`, which `infra.dev.server` never loads.
  
  So: **my changes are real, tested (101 green), and match the design doc — but they are invisible in the window you're running.** I should have checked what the dev server actually boots before building. That's on me.
  
  There is partial overlap: the particle path promotes massive sinks into ECS bodies via `stellar/spawn-clump`, which I changed — so *resolved* stars/planets now carry a B field and can be classified. But the gas bulk (the thousands of fog particles that dominate the view) has no field and isn't coloured by one.
  
  Two side notes from the logs: the dev process fully exits and pm2 restarts it roughly every ~10 min (↺33, 2.2 GB resident) — looks like a crash/OOM, separate from the in-sim nebula reset. Worth investigating on its own.
  
  ## The choice
  
  To make fields visible in what you're watching, the work has to move into the particle path. Let me scope it accurately first, then you decide.
● ToolUse(Read)
⎿ 1	(ns domain.particles.field
  2	  "The gas particle field for Phase 0 — a flat primitive-array buffer of up to
  3	   `cap` particles. Gravity comes from the particle-mesh solver; close-range
  4	   inelastic accretion (spatial-hash merging) provides the dissipation that lets
  5	   a rotating cloud collapse into stars and flatten into a disk, and lets the
  6	   cloud fragment into multiple protostars.
  7	
  8	   A dead/absorbed particle is marked by mass = 0 and skipped everywhere.
  9	   Particle 'kind' is derived from mass at render time, not stored.
  10	
  11	   Mutable in place for performance: step! mutates the arrays and returns the
  12	   same Field. This buffer is the design's statistical field; massive accreted
  13	   clumps are what later get promoted to resolved bodies."
  14	  (:require [domain.particles.pm :as pm])
  15	  (:import [java.util HashMap ArrayList]))
  16	
  17	(set! *unchecked-math* :warn-on-boxed)
  18	(set! *warn-on-reflection* true)
  19	
  20	(defrecord Field [^long cap ^double r0 ^double m0
  21	                  ^doubles px ^doubles py ^doubles pz
  22	                  ^doubles vx ^doubles vy ^doubles vz
  23	                  ^doubles mass ^doubles radius
  24	                  ^doubles ax ^doubles ay ^doubles az])
  25	
  26	(defn make-field [cap r0 m0]
  27	  (let [cap (long cap)]
  28	    (->Field cap (double r0) (double m0)
  29	             (double-array cap) (double-array cap) (double-array cap)
  30	             (double-array cap) (double-array cap) (double-array cap)
  31	             (double-array cap) (double-array cap)
  32	             (double-array cap) (double-array cap) (double-array cap))))
  33	
  34	(defn radius-for-mass
  35	  "Visual/physical radius from mass, assuming constant density (r ∝ m^(1/3))."
  36	  ^double [^double r0 ^double m0 ^double m]
  37	  (* r0 (Math/cbrt (/ m m0))))
  38	
  39	(defn live-count
  40	  "Number of still-existing particles (mass > 0)."
  ──── (10 lines hidden) ──────────────────────────────────────────────
  51	
  52	(defn seed-cloud!
  53	  "Fill the field with a rotating, turbulent spherical cloud of `n` particles of
  54	   radius `cloud-r`, plus `n-seeds` Gaussian over-density blobs that gravity
  55	   amplifies into separate protostars (so the cloud can fragment into
  56	   binaries/multiples). `spin` sets solid-body rotation ω about z; `turb` sets
  57	   random velocity dispersion. `rng` is a java.util.Random for reproducibility."
  58	  [^Field f {:keys [n cloud-r spin turb particle-mass n-seeds seed-r ^java.util.Random rng]
  59	             :or   {n-seeds 3}}]
  60	  (let [^doubles px (.px f) ^doubles py (.py f) ^doubles pz (.pz f)
  61	        ^doubles vx (.vx f) ^doubles vy (.vy f) ^doubles vz (.vz f)
  62	        ^doubles mass (.mass f) ^doubles radius (.radius f)
  63	        n        (long n)
  64	        cloud-r  (double cloud-r)
  65	        spin     (double spin)
  66	        turb     (double turb)
  67	        pmass    (double particle-mass)
  68	        seed-r   (double (or seed-r (* 0.18 cloud-r)))
  69	        r0       (.r0 f) m0 (.m0 f)
  70	        ;; centres of the over-density seeds
⎿ [Request interrupted by user]
❯ What are you talking about? I restart that, yes. that is normal. When changes are made. There should be no reason why there are two different plaths. everything should be the ECS. I thought we dealt with this, make this impossible to happen again, one path. ONE PATH the ECS path, what you just did, make it impossible to do this again, update the readme, the agent's guidances, the documentation, clean this up. This is how I view the code you're working on.
● You're absolutely right, and I hear you. Two world models is exactly the violation of the single-ECS-substrate principle we already committed to. Let me converge everything onto the ECS path and make the particle path impossible to reintroduce. First I need to see the remaining entrypoints so I rewire correctly rather than half-converge.
● ToolUse(Read)
⎿ 1	(ns infra.dev.window
  2	  "Live development window service.
  3	
  4	   Runs a GLFW/OpenGL window on a dedicated daemon thread. The window
  5	   continuously renders whatever is in the shared `world-atom`, using the
  6	   shared `camera-atom` and a reloadable `renderer-config-atom`.
  7	
  8	   Connect from another REPL (e.g. `clj -M:repl --connect localhost:7888`)
  9	   and mutate the atoms to see changes in real time.
  10	
  11	   Examples:
  12	     (require '[infra.dev.window :as w])
  13	     (reset! (:world @w/service-state) my-world)
  14	     (swap! (:camera @w/service-state) assoc :distance 400.0)
  15	     (w/reload-shaders!)   ; recompile after editing infra.render shader vars
  16	     (w/reload-mesh! 3)    ; change sphere subdivision level
  17	     (w/take-screenshot! \"/tmp/truth-dev.png\")"
  18	  (:require
  19	    [domain.orbital.system :as orbital]
  20	    [infra.render          :as render])
  21	  (:import
  22	    (org.lwjgl.glfw GLFW)
  23	    (org.lwjgl.opengl GL15 GL20 GL30)))
  24	
  25	(defonce service-state
  26	  (atom nil))
  27	
  28	(defn- delete-program [program]
  29	  (when (and program (pos? program))
  30	    (GL20/glDeleteProgram program)))
  31	
  32	(defn- delete-mesh [{:keys [vao vbo]}]
  33	  (when (and vao (pos? vao))
  34	    (GL30/glDeleteVertexArrays vao))
  35	  (when (and vbo (pos? vbo))
  36	    (GL15/glDeleteBuffers vbo)))
  37	
  38	(defn- ensure-resources [config-atom]
  39	  (swap! config-atom
  40	         (fn [{:keys [body-program particle-program mesh subdivisions requested-subdivisions] :as cfg}]
  ──── (144 lines hidden) ──────────────────────────────────────────────
  185	  "Request a screenshot and block until it has been written to `path`.
  186	   The actual readback happens on the window thread, so this may take up
  187	   to one frame plus file I/O time."
  188	  [path]
  189	  (when-let [config-atom (:config @service-state)]
  190	    (let [result (promise)]
  191	      (swap! config-atom assoc :screenshot-request {:path path :result result})
  192	      @result
  193	      path)))
  194	
  195	(defn service-info
  196	  "Return a read-only summary of the running service."
  197	  []
  198	  (when-let [s @service-state]
  199	    {:running? true
  200	     :thread   (.getName (:thread s))
  201	     :world    (identical? (:world s) (some-> s :world deref))
  202	     :camera   @(:camera s)
  203	     :config   (select-keys @(:config s) [:width :height :subdivisions])}))
  204	
● ToolUse(Read)
⎿ 1	(ns infra.main
  2	  "Entry point for Gates of Truth — Phase 0: Stellar Nebula.
  3	
  4	   The game and the render demo share ONE world model: the ECS world. Phase 0 is
  5	   a composition layer over it (see domain.phase0). There is no separate
  6	   simulation path."
  7	  (:require
  8	    [domain.phase0         :as phase0]
  9	    [domain.player         :as player]
  10	    [domain.ecs.components :as c]
  11	    [domain.ecs.core       :as ecs]
  12	    [infra.render          :as render]))
  13	
  14	;; --- Render demo ------------------------------------------------------------
  15	
  16	(defn- body
  17	  "Insert a celestial body entity into the world."
  18	  [world eid kind mass radius pos vel]
  19	  (-> world
  20	      (ecs/put-component eid c/body-kind kind)
  21	      (ecs/put-component eid c/mass     mass)
  22	      (ecs/put-component eid c/radius   radius)
  23	      (ecs/put-component eid c/position pos)
  24	      (ecs/put-component eid c/velocity vel)))
  25	
  26	(defn make-demo-world
  27	  "A small Sun/Earth/Moon world for exercising the renderer."
  28	  []
  29	  (atom
  30	    (-> (ecs/empty-world)
  31	        (body :sun   :body/star   1000.0 5.0  [0.0 0.0 0.0]  [0.0 0.0 0.0])
  32	        (body :earth :body/planet 10.0   2.0  [50.0 0.0 0.0] [0.0 0.0 4.47])
  33	        (body :moon  :body/moon   0.1    0.5  [58.0 0.0 0.0] [0.0 0.0 5.57]))))
  34	
  35	(defn run-render-demo []
  36	  (println "Rendering Sun/Earth/Moon demo frame...")
  37	  (let [world (make-demo-world)
  38	        path  "/tmp/truth-view.png"]
  39	    (render/render-to-file world path)
  40	    (println "Saved frame to" path)))
  ──── (18 lines hidden) ──────────────────────────────────────────────
  59	                         (double (:coherence obs))
  60	                         (/ (:phase0/sim-time w) 3.15e7))))
  61	      (if-let [ending (phase0/world-ending w)]
  62	        (do
  63	          (println "\n=== SIMULATION END ===")
  64	          (println (:message ending))
  65	          (when (= (:type ending) :success)
  66	            (println "Habitable worlds formed:" (count (:worlds ending)))))
  67	        (if (or (> i 1000) (not (:phase0/active w)))
  68	          (println "\nPhase 0 simulation complete.")
  69	          (do (Thread/sleep 30)
  70	              (recur (phase0/tick-world w) (inc i))))))))
  71	
  72	(defn -main
  73	  "Launch Gates of Truth. `demo` renders a frame; default runs Phase 0."
  74	  [& args]
  75	  (if (= (first args) "demo")
  76	    (run-render-demo)
  77	    (run-phase0-simulation)))
  78	
