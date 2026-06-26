❯ 
● Leapfrog (symplectic) — tier-quantized `dt` changes (only at phase transitions) are safe for it. And the only test pinning the time contract is `test-time-scale` (asserts `time-scale` ∈ [1e10,1e12] and monotonic decrease) — I'll update that contract deliberately since the semantics change from "sim-s per tick" to "sim-s per real-second" (the clock rate).
  
  Let me read the test sections I'll need to update and confirm the player/observer HUD shape.
● ToolUse(Read)
⎿ 40	    (is (not (law/fusion-possible? {:temperature 2e7 :pressure 1e14 :composition {:H 0.02}})))
  41	    (is (law/fusion-possible? {:temperature 2e7 :pressure 1e14 :composition {:H 0.75}}))))
  42	
  43	(deftest test-hydrostatic-equilibrium
  44	  (testing "Self-gravity rounds bodies above the mass threshold"
  45	    (is (law/hydrostatic-equilibrium? {:mass 6e24}))
  46	    (is (not (law/hydrostatic-equilibrium? {:mass 1e20})))
  47	    (is (not (law/hydrostatic-equilibrium? {:mass nil})))))
  48	
  49	(deftest test-time-scale
  50	  (testing "Time scale starts around centuries per tick and slows as complexity rises"
  51	    (let [w0 (phase0/create-world)
  52	          ts0 (:phase0/time-scale w0)]
  53	      (is (< 1e10 ts0 1e12) "initial time-scale is nebular-scale centuries per tick")
  54	      (is (> (stellar/time-scale-from-complexity 1)
  55	             (stellar/time-scale-from-complexity 50)))))
  56	
  57	  (testing "Physics systems use the scaled dt"
  58	    (let [w0 (-> (phase0/create-world)
  59	                 (assoc :phase0/time-scale 1e10))
  60	          systems (phase0/physics-systems w0)
  61	          orbital (first systems)]
  62	      ;; The orbital system closure captures effective-dt. We can't inspect it
  63	      ;; directly, but we can verify the time-scale is being read and the
  64	      ;; thermal system is passed a scaled dt by checking the function arity.
  65	       ;; Ten systems: hydro, gravity, collision, classify, collapse, fusion,
  66	       ;; thermal, regime, EM, recenter.
  67	       (is (= 10 (count systems)))
  68	      (is (fn? orbital)))))
  69	
  70	(deftest test-orbital-motion-advances
  71	  (testing "Ring clumps move when the world ticks"
  72	    (let [w0 (phase0/create-world)
  73	          eids (ecs/entities-with w0 c/matter-state c/position)
  74	          before (into {} (map (juxt identity #(ecs/get-component w0 % c/position))) eids)
  75	          w1 (phase0/tick-world w0)
  76	          ;; some particles merge (accrete) and despawn in a tick; compare only
  77	          ;; survivors that still have a position
  78	          after (into {} (keep (fn [eid]
  79	                                 (when-let [p (ecs/get-component w1 eid c/position)]
● ToolUse(Read)
⎿ 160	                                 :regions []} 1e20)))))
  161	
  162	;; --- Full arc ---------------------------------------------------------------
  163	
  164	(deftest test-full-simulation
  165	  (testing "A gas cloud collapses and a star + other bodies emerge by accretion"
  166	    (let [w0 (phase0/create-world {:gas-count 400})
  167	          ;; run until a star ignites from the gas or we exhaust the budget
  168	          final (loop [w w0 i 0]
  169	                  (if (or (> i 260) (:star? (phase0/system-summary w))
  170	                          (not (:phase0/active w)))
  171	                    w
  172	                    (recur (phase0/tick-world w) (inc i))))
  173	          summ (phase0/system-summary final)]
  174	      (is (:star? summ) "a star should ignite from the collapsing cloud")
  175	      (is (> (:resolved-count summ) 1)
  176	          "other bodies (planets/debris) should condense alongside the star")
  177	      (is (> (:phase0/sim-time final) 0.0))
  178	      (is (not= :initializing (:phase0/phase final)))
  179	      (let [coh (:coherence (player/get-observer final))]
● Now I'll confirm the tail of `render-hud` (depth-mask handling) and the render namespace's existing imports/requires before writing the text subsystem.
● ToolUse(Read)
⎿ 299	          (GL11/glDrawArrays GL11/GL_TRIANGLES 0 6)
  300	          (GL30/glBindVertexArray 0)
  301	          (GL15/glDeleteBuffers vbo)
  302	          (GL30/glDeleteVertexArrays vao))))
  303	    (GL11/glDepthMask true)
  304	    (GL11/glDisable GL11/GL_BLEND)
  305	    (GL20/glUseProgram 0)))
  306	
  307	;; ---------------------------------------------------------------------------
  308	;; Sphere mesh
  309	;; ---------------------------------------------------------------------------
  310	
  311	(defn- subdivide-icosahedron []
  312	  (let [t     (/ (+ 1.0 (Math/sqrt 5.0)) 2.0)
  313	        verts [[-1.0 t 0.0] [1.0 t 0.0] [-1.0 (- t) 0.0] [1.0 (- t) 0.0]
  314	               [0.0 -1.0 t] [0.0 1.0 t] [0.0 -1.0 (- t)] [0.0 1.0 (- t)]
● ToolUse(Read)
⎿ 1	(ns infra.render
  2	  "Minimal LWJGL + OpenGL renderer for Gates of Truth.
  3	   Renders ECS bodies as wireframe spheres with a controllable camera."
  4	  (:require
  5	    [domain.ecs.core :as ecs]
  6	    [domain.ecs.components :as c]
  7	    [domain.orbital.system :as orbital]
  8	    [domain.phase0 :as phase0]
  9	    [domain.player :as player]
  10	    [domain.em :as em]
  11	    [shape.spatial :as sp])
  12	  (:import
  13	    (org.lwjgl.glfw GLFW Callbacks GLFWErrorCallback GLFWKeyCallback GLFWCursorPosCallback GLFWScrollCallback)
  14	    (org.lwjgl.opengl GL GL11 GL15 GL20 GL30)
  15	    (org.lwjgl.stb STBImageWrite)
  16	    (org.lwjgl.system MemoryUtil)
  17	    (org.lwjgl BufferUtils)
  18	    (java.nio ByteBuffer)))
  19	
  20	;; ---------------------------------------------------------------------------
  21	;; Math helpers
  22	;; ---------------------------------------------------------------------------
  23	
  24	(defn- deg->rad [d] (* d (/ Math/PI 180.0)))
  25	
  26	(defn- normalize [[x y z]]
  27	  (let [len (Math/sqrt (+ (* x x) (* y y) (* z z)))]
  28	    (if (zero? len)
  29	      [0.0 0.0 1.0]
  30	      [(/ x len) (/ y len) (/ z len)])))
  31	
  32	(defn- cross [[ax ay az] [bx by bz]]
  33	  [(- (* ay bz) (* az by))
  34	   (- (* az bx) (* ax bz))
  35	   (- (* ax by) (* ay bx))])
  36	
  37	(defn- perspective [fov-deg aspect near far]
  38	  (let [f (/ 1.0 (Math/tan (/ (deg->rad fov-deg) 2.0)))
  39	        nf (/ 1.0 (- near far))]
  40	    (float-array [(/ f aspect) 0.0 0.0 0.0
● Everything checks out: `render.clj` already requires `domain.phase0`, imports `org.lwjgl.stb` and `java.nio ByteBuffer`, and `render-hud` properly restores depth-mask/blend. Now I'll implement.
  
  Let me start with the domain layer — adaptive pacing, stats, and wiring into the tick.
● ToolUse(Edit)
⎿ The file /home/err/spaces/Truth/src/domain/phase0.clj has been updated successfully. (file state is current in your context — no need to Read it back)
● Now update `create-world` to seed the clock from the nebula tier:
● ToolUse(Edit)
⎿ The file /home/err/spaces/Truth/src/domain/phase0.clj has been updated successfully. (file state is current in your context — no need to Read it back)
❯ 
● Now wire the adaptive pacing + stats into `tick-world`:
● ToolUse(Edit)
⎿ The file /home/err/spaces/Truth/src/domain/phase0.clj has been updated successfully. (file state is current in your context — no need to Read it back)
● ToolUse(Edit)
⎿ The file /home/err/spaces/Truth/src/domain/phase0.clj has been updated successfully. (file state is current in your context — no need to Read it back)
● Now remove the dead `time-scale-from-complexity` from `stellar.clj` (its only callers were `create-world`, now repurposed, and one test I'll rewrite):
● ToolUse(Edit)
⎿ The file /home/err/spaces/Truth/src/domain/stellar.clj has been updated successfully. (file state is current in your context — no need to Read it back)
● Now the window's pacing loop — replace fixed frame-skip with a wall-clock accumulator that realizes the world's reported rate:
● ToolUse(Edit)
⎿ The file /home/err/spaces/Truth/src/infra/dev/window.clj has been updated successfully. (file state is current in your context — no need to Read it back)
● ToolUse(Edit)
⎿ The file /home/err/spaces/Truth/src/infra/dev/window.clj has been updated successfully. (file state is current in your context — no need to Read it back)
● Now the renderer text subsystem. First add `STBEasyFont` to imports:
● ToolUse(Edit)
⎿ The file /home/err/spaces/Truth/src/infra/render.clj has been updated successfully. (file state is current in your context — no need to Read it back)
❯ 
● Now add the text rendering functions and HUD text projection. I'll insert them right after `render-hud` (line 305):
● ToolUse(Edit)
⎿ The file /home/err/spaces/Truth/src/infra/render.clj has been updated successfully. (file state is current in your context — no need to Read it back)
● Now hook `render-text` into `render-scene`'s HUD pass:
● ToolUse(Edit)
⎿ The file /home/err/spaces/Truth/src/infra/render.clj has been updated successfully. (file state is current in your context — no need to Read it back)
● ToolUse(Edit)
⎿ The file /home/err/spaces/Truth/src/infra/render.clj has been updated successfully. (file state is current in your context — no need to Read it back)
● There's also `render.clj`'s own internal render-loop (the standalone `run-window`) that builds the phase0 HUD. Let me update it to pass `hud-text` too:
● ToolUse(Read)
⎿ 1290	  "Render the current world to a PNG file using an offscreen OpenGL context.
  1291	   Returns the path of the written image. Auto-detects Phase 0 worlds."
  1292	  ([world-atom path]
  1293	   (render-to-file world-atom path {}))
  1294	  ([world-atom path {:keys [tick-fn bodies-fn camera camera-mode]}]
  1295	   (println "Rendering offscreen frame to" path)
  1296	   (init-glfw)
  1297	   (let [width   1280
  1298	         height  720
  1299	         window  (create-offscreen-window width height)
  1300	         body-program     (create-program)
  1301	         particle-program (create-particle-program)
  1302	         line-program     (create-line-program)
  1303	         hud-program      (create-hud-program)
  1304	         sphere  (make-sphere-mesh 3)
  1305	         mesh    (upload-mesh sphere)
  1306	         fbo     (create-fbo width height)]
  1307	     (let [w @world-atom
  1308	           phase0?   (contains? w :phase0/phase)
  1309	           tick-fn   (or tick-fn
  1310	                         (if phase0?
  1311	                           phase0/tick-world
  1312	                           (orbital/orbital-system 6.674e-11 0.5 0.5)))
  1313	           bodies-fn (or bodies-fn
  1314	                         (if phase0?
  1315	                           phase0-bodies-from-world
  1316	                           bodies-from-world))
  1317	           w (swap! world-atom tick-fn)
  1318	           ;; Frame the whole system: snap an auto-fit camera to the world unless
  1319	           ;; the caller supplied one explicitly.
  1320	           camera  (or camera
  1321	                       (if phase0?
  1322	                         (update-camera-for-world
  1323	                           (make-camera 60.0) w
  1324	                           (assoc (default-camera-settings)
● ToolUse(Read)
⎿ 1325	                                  :mode (or camera-mode :fit-all) :smoothing 1.0))
  1326	                         (make-camera)))
  1327	           bodies (bodies-fn w)
  1328	           hud    (when phase0? (hud-rects-from-world w))]
  1329	       (GL30/glBindFramebuffer GL30/GL_FRAMEBUFFER (:fbo fbo))
  1330	       (render-scene {:body-program body-program :particle-program particle-program
  1331	                      :line-program line-program :hud-program hud-program :hud hud}
  1332	                     mesh camera width height bodies 0.0))
  1333	     (GL11/glFlush)
  1334	     (let [pixels  (read-pixels width height)
  1335	           flipped (flip-rgba-vertical pixels width height)]
  1336	       (STBImageWrite/stbi_write_png path width height 4 flipped (* width 4)))
  1337	     (GL30/glBindFramebuffer GL30/GL_FRAMEBUFFER 0)
  1338	     (GLFW/glfwDestroyWindow window)
  1339	     (GLFW/glfwTerminate)
  1340	     (GLFW/glfwSetErrorCallback nil)
  1341	     path)))
  1342	
  1343	(defn run-window [world-atom]
  1344	  (println "Initializing GLFW...")
  1345	  (init-glfw)
  1346	  (let [width          1280
  1347	        height         720
  1348	        window         (create-window width height "Gates of Truth — 3D View")
  1349	        camera         (atom (make-camera))
  1350	        keys           (atom {})
  1351	        body-program   (create-program)
  1352	        particle-program (create-particle-program)
● ToolUse(Edit)
⎿ The file /home/err/spaces/Truth/src/infra/render.clj has been updated successfully. (file state is current in your context — no need to Read it back)
● ToolUse(Read)
⎿ 1352	        keys           (atom {})
  1353	        body-program   (create-program)
  1354	        particle-program (create-particle-program)
  1355	        sphere         (make-sphere-mesh 2)
  1356	        mesh           (upload-mesh sphere)
  1357	        config-atom    (atom (default-camera-settings))]
  1358	    (println "Window created, entering render loop...")
  1359	    (setup-input window camera keys config-atom)
  1360	    (loop []
  1361	      (when (not (GLFW/glfwWindowShouldClose window))
  1362	        (GLFW/glfwPollEvents)
  1363	        ;; Simulate one tick per frame
  1364	        (swap! world-atom (fn [w] ((orbital/orbital-system 6.674e-11 0.5 0.5) w)))
  1365	        (swap! camera update-camera-for-world @world-atom @config-atom)
  1366	        (let [bodies (bodies-from-world @world-atom)]
  1367	          (render-scene {:body-program body-program :particle-program particle-program}
  1368	                        mesh @camera width height bodies 0.0))
  1369	        (GLFW/glfwSwapBuffers window)
  1370	        (Thread/sleep 16)
  1371	        (recur)))
  1372	    (println "Shutting down renderer...")
  1373	    (GLFW/glfwDestroyWindow window)
  1374	    (Callbacks/glfwFreeCallbacks window)
  1375	    (GLFW/glfwTerminate)
  1376	    (GLFW/glfwSetErrorCallback nil)))
❯ 
● The bare `run-window` demo has no HUD program, so it's fine. Now update the tests. First, replace the `test-time-scale` contract in `phase0_test.clj`:
● ToolUse(Edit)
⎿ The file /home/err/spaces/Truth/test/domain/phase0_test.clj has been updated successfully. (file state is current in your context — no need to Read it back)
● Now check whether `phase0_test` still uses the `stellar` alias (I removed its only reference) and add the render HUD-text test:
● ToolUse(Bash)
⎿ === stellar usage in phase0_test ===
  7:   [domain.stellar          :as stellar]
  10:   [law.stellar             :as law]
  22:      (is (> (stellar/gravitational-collapse-rate region) 0))
  23:      (is (stellar/jeans-unstable? region))))
  26:      (is (not (stellar/jeans-unstable? region))))))
  31:      (is (> (stellar/virial-temperature m 1e9)
  32:             (stellar/virial-temperature m 1e10)))
  33:      (is (> (stellar/self-gravity-pressure m 1e9)
  34:             (stellar/self-gravity-pressure m 1e10))))))
  133:    (let [gain (player/coherence-gain-from-event :stellar-ignition 0.5)]
  154:(deftest test-stellar-merge
  159:                                              stellar/stellar-merge-handler))
  160:          [w1 _]  (stellar/spawn-clump base {:position [0 0 0]   :mass 2e30 :radius 1.0})
  161:          [w2 _]  (stellar/spawn-clump w1   {:position [0.5 0 0] :mass 1e30 :radius 1.0})
  === render_test ns + tail ===
  (ns infra.render-test
    "Tests for the single Phase 0 render projection (infra.render). These cover the
     pure geometry/colour fns that turn the ECS world into render shapes — regime
     tinting, volumetric fog, and magnetic field lines. GL calls are not exercised."
    (:require
     [clojure.test :refer [deftest testing is]]
     [domain.ecs.core :as ecs]
     [domain.ecs.components :as c]
     [domain.stellar :as stellar]
     [domain.phase0 :as phase0]
     [domain.player :as player]
     [infra.render :as r]))
  
  (deftest test-tint-color
    (testing "Tinting keeps colours in [0,1] and shifts by regime"
● `stellar` is still used. Now add the HUD-text test next to the existing HUD-rects test:
● ToolUse(Edit)
⎿ The file /home/err/spaces/Truth/test/infra/render_test.clj has been updated successfully. (file state is current in your context — no need to Read it back)
● Now let me run the test suite to validate the domain/render changes (GL is not exercised in tests):
● ToolUse(Bash)
⎿ Running tests in #{"test"}
  Syntax error reading source at (infra/render.clj:321:61).
  Metadata can only be applied to IMetas
  
  Full report at:
  /tmp/clojure-16272659233656304549.edn
