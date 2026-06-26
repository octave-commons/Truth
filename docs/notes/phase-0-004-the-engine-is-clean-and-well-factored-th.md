● ToolUse(Read)
⎿ 1	(ns domain.gravity.barnes-hut
  2	  "Barnes–Hut n-body gravity in 3D, using an octree over AABBs.
  3	   - build-tree: bodies -> tree
  4	   - acceleration: G θ tree body -> vec3 acceleration on body."
  5	  (:require
  6	    [shape.spatial :as sp]))
  7	
  8	;; --- Node representation ----------------------------------------------------
  9	
  10	(def ^:private min-aabb-size 1.0e-9)
  11	
  12	(defn- leaf-node
  13	  [bb body-or-bodies]
  14	  (let [bodies (if (sequential? body-or-bodies)
  15	                 (vec body-or-bodies)
  16	                 [body-or-bodies])
  17	        total  (double (reduce + (map :mass bodies)))]
  18	    {:type   :leaf
  19	     :aabb   bb
  20	     :bodies bodies
  21	     :mass   total
  22	     :com    (if (pos? total)
  23	               (sp/v* (reduce (fn [acc b]
  24	                                 (sp/v+ acc (sp/v* (:position b) (:mass b))))
  25	                               (sp/vec3 0.0 0.0 0.0)
  26	                               bodies)
  27	                      (/ 1.0 total))
  28	               (sp/center bb))}))
  29	
  30	(defn- internal-node [bb children mass com]
  31	  {:type     :internal
  32	   :aabb     bb
  33	   :children children
  34	   :mass     mass
  35	   :com      com})
  36	
  37	(defn internal-node? [node] (= (:type node) :internal))
  38	(defn leaf-node?     [node] (= (:type node) :leaf))
  39	
  40	;; --- Tree building ----------------------------------------------------------
  ──── (139 lines hidden) ──────────────────────────────────────────────
  180	
  181	    (internal-node? node)
  182	    (let [s (sp/max-side (:aabb node))
  183	          d (sp/dist pos (:com node))]
  184	      (if (or (zero? d) (< (/ s d) theta))
  185	        (sp/v+ acc (accel-from-mass G pos (:mass node) (:com node)))
  186	        (reduce (fn [a child] (traverse G theta pos a child))
  187	                acc
  188	                (:children node))))))
  189	
  190	(defn acceleration
  191	  "Compute gravitational acceleration on `body` from all bodies in `tree`.
  192	   G     — gravitational constant
  193	   theta — Barnes–Hut opening angle (default 0.5)"
  194	  ([G tree body]
  195	   (acceleration G default-theta tree body))
  196	  ([G theta tree body]
  197	   (let [pos (with-meta (:position body) {:id (:id body)})]
  198	     (traverse G theta pos (sp/vec3 0.0 0.0 0.0) tree))))
  199	
❯ 
● The engine is clean and well-factored — the convergence path is clear. Here's the plan I'll execute, keeping the suite green:
  
  1. **Components** — add `temperature/density/pressure/composition/luminosity/matter-state` + an `observer` component to the ECS vocabulary.
  2. **Stellar physics → ECS systems** — keep the pure physics fns in `stellar.clj`, retire the `defrecord`/`:bodies`-vector tick, add `thermal-system` + `fusion-system` and a stellar-aware merge handler (reusing the existing collision system).
  3. **Player → observer system** — keep coherence fns pure, store the spark as an `observer` component, drive it from the ledger.
  4. **Phase 0 orchestrator** — `phase0.clj` becomes an ECS bootstrap + tick driver (like `world_bootstrap`), not a parallel engine.
  5. **Tests + main** — rewrite `phase0_test` against the ECS model; unify `main.clj` onto one world.
  
  Let me track and start.
● ToolUse(Bash)
⎿ (Bash completed with no output)
● Step 1 — extend the component vocabulary.
● ToolUse(Edit)
⎿ The file /home/err/spaces/Truth/src/domain/ecs/components.clj has been updated successfully. (file state is current in your context — no need to Read it back)
● Step 2a — fix the hydrostatic-equilibrium law. The current formula is physically broken (it failed a real Earth-mass body in the tests). The design doc's open question #2 explicitly invites a proxy, so I'll use the astronomically sound one: self-gravity rounds a body above a mass threshold.
● ToolUse(Edit)
⎿ The file /home/err/spaces/Truth/src/law/stellar.clj has been updated successfully. (file state is current in your context — no need to Read it back)
● ToolUse(Edit)
⎿ The file /home/err/spaces/Truth/src/law/stellar.clj has been updated successfully. (file state is current in your context — no need to Read it back)
● Step 2c — rewrite `domain/stellar.clj`: keep the pure physics, drop the `defrecord`-coupled tick loop, and express collapse/thermal/fusion as ECS systems on the shared world.
● ToolUse(Write)
⎿ The file /home/err/spaces/Truth/src/domain/stellar.clj has been updated successfully. (file state is current in your context — no need to Read it back)
● Step 3 — rewrite `domain/player.clj`: the spark becomes a plain `observer` component (no `defrecord`), with the coherence loop driven by the world's event ledger.
● ToolUse(Write)
⎿ The file /home/err/spaces/Truth/src/domain/player.clj has been updated successfully. (file state is current in your context — no need to Read it back)
❯ 
● Step 4a — fix the observer's event read to use the current tick only (the `events-since` cursor would double-count). 
  
  Let me update `observer-system`:
● ToolUse(Edit)
⎿ The file /home/err/spaces/Truth/src/domain/player.clj has been updated successfully. (file state is current in your context — no need to Read it back)
● Step 4b — the collapse→ignition physics needs to actually reach the thresholds. I'll drive the contracting core by its **virial temperature** and self-gravity pressure (both rise as radius shrinks), and set a reachable, realistic ignition pressure. Let me adjust the law threshold and rework `collapse-system`.
● ToolUse(Edit)
⎿ The file /home/err/spaces/Truth/src/law/stellar.clj has been updated successfully. (file state is current in your context — no need to Read it back)
● ToolUse(Edit)
⎿ The file /home/err/spaces/Truth/src/domain/stellar.clj has been updated successfully. (file state is current in your context — no need to Read it back)
● ToolUse(Edit)
⎿ The file /home/err/spaces/Truth/src/domain/stellar.clj has been updated successfully. (file state is current in your context — no need to Read it back)
● Step 4c — rewrite `domain/phase0.clj` as the ECS bootstrap + tick driver (replacing the `Phase0World` defrecord engine entirely).
● ToolUse(Write)
⎿ The file /home/err/spaces/Truth/src/domain/phase0.clj has been updated successfully. (file state is current in your context — no need to Read it back)
● Let me verify the new domain code compiles and actually produces a star before I touch tests and main.
● ToolUse(Bash)
⎿ ticks= 1 phase= :phase-0/dispersed star?= false planets= 7 bodies= 7 coh= 0.8148000000000001 ending= :dispersal
❯ 
● It runs, but there's a logic bug: `classify-system` is freezing the collapsing core into a planet after its first contraction (virial temp is still low early in collapse), which kills the path to ignition. Classification should only apply to *stable* nebular clumps, never to a protostar mid-collapse.
● ToolUse(Edit)
⎿ The file /home/err/spaces/Truth/src/domain/stellar.clj has been updated successfully. (file state is current in your context — no need to Read it back)
● ToolUse(Bash)
⎿ t= 0 phase= :initializing star?= false planets= 7 bodies= 7 coh= 0.800 end= nil
  t= 5 phase= :phase-0/protostar star?= false planets= 7 bodies= 7 coh= 0.754 end= nil
  t= 10 phase= :phase-0/protostar star?= false planets= 7 bodies= 7 coh= 0.678 end= nil
  t= 15 phase= :phase-0/protostar star?= false planets= 7 bodies= 7 coh= 0.602 end= nil
  t= 20 phase= :phase-0/protostar star?= false planets= 7 bodies= 7 coh= 0.526 end= nil
  t= 25 phase= :phase-0/protostar star?= false planets= 7 bodies= 7 coh= 0.450 end= nil
  t= 30 phase= :phase-0/protostar star?= false planets= 7 bodies= 7 coh= 0.374 end= nil
  t= 35 phase= :phase-0/protostar star?= false planets= 7 bodies= 7 coh= 0.298 end= nil
  t= 40 phase= :phase-0/protostar star?= false planets= 7 bodies= 7 coh= 0.222 end= nil
  t= 45 phase= :phase-0/protostar star?= false planets= 7 bodies= 7 coh= 0.146 end= nil
  t= 50 phase= :phase-0/protostar star?= false planets= 7 bodies= 7 coh= 0.070 end= nil
  t= 52 phase= :phase-0/protostar star?= false planets= 7 bodies= 7 coh= 0.040 end= :fadeout
  DONE
● The core stalls as a protostar — as contraction heats it, it becomes Jeans-*stable* again and stops before pressure crosses ignition. Physically a protostar keeps contracting (Kelvin–Helmholtz) until it ignites. Fix: Jeans instability *initiates* collapse on a nebular clump, but a protostar then contracts monotonically until fusion flips it to a star.
● ToolUse(Edit)
⎿ The file /home/err/spaces/Truth/src/domain/stellar.clj has been updated successfully. (file state is current in your context — no need to Read it back)
● ToolUse(Bash)
⎿ t= 31 phase= :phase-0/planets-formed star?= true planets= 6 bodies= 7 coh= 0.514 end= :sterile
  t= 32 phase= :phase-0/planets-formed star?= true planets= 6 bodies= 7 coh= 0.497 end= :sterile
  t= 33 phase= :phase-0/planets-formed star?= true planets= 6 bodies= 7 coh= 0.480 end= :sterile
  t= 34 phase= :phase-0/planets-formed star?= true planets= 6 bodies= 7 coh= 0.464 end= :sterile
  t= 35 phase= :phase-0/planets-formed star?= true planets= 6 bodies= 7 coh= 0.447 end= :sterile
  t= 36 phase= :phase-0/planets-formed star?= true planets= 6 bodies= 7 coh= 0.430 end= :sterile
  t= 37 phase= :phase-0/planets-formed star?= true planets= 6 bodies= 7 coh= 0.414 end= :sterile
  t= 38 phase= :phase-0/planets-formed star?= true planets= 6 bodies= 7 coh= 0.397 end= :sterile
  t= 39 phase= :phase-0/planets-formed star?= true planets= 6 bodies= 7 coh= 0.380 end= :sterile
  t= 40 phase= :phase-0/planets-formed star?= true planets= 6 bodies= 7 coh= 0.364 end= :sterile
  t= 41 phase= :phase-0/planets-formed star?= true planets= 6 bodies= 7 coh= 0.347 end= :sterile
  t= 42 phase= :phase-0/planets-formed star?= true planets= 6 bodies= 7 coh= 0.330 end= :sterile
  t= 43 phase= :phase-0/planets-formed star?= true planets= 6 bodies= 7 coh= 0.313 end= :sterile
  t= 44 phase= :phase-0/planets-formed star?= true planets= 6 bodies= 7 coh= 0.297 end= :sterile
  t= 45 phase= :phase-0/planets-formed star?= true planets= 6 bodies= 7 coh= 0.280 end= :sterile
  t= 46 phase= :phase-0/planets-formed star?= true planets= 6 bodies= 7 coh= 0.263 end= :sterile
  t= 47 phase= :phase-0/planets-formed star?= true planets= 6 bodies= 7 coh= 0.247 end= :sterile
  t= 48 phase= :phase-0/planets-formed star?= true planets= 6 bodies= 7 coh= 0.230 end= :sterile
  t= 49 phase= :phase-0/planets-formed star?= true planets= 6 bodies= 7 coh= 0.213 end= :sterile
  t= 50 phase= :phase-0/planets-formed star?= true planets= 6 bodies= 7 coh= 0.197 end= :sterile
  t= 51 phase= :phase-0/planets-formed star?= true planets= 6 bodies= 7 coh= 0.180 end= :sterile
  t= 52 phase= :phase-0/planets-formed star?= true planets= 6 bodies= 7 coh= 0.163 end= :sterile
  t= 53 phase= :phase-0/planets-formed star?= true planets= 6 bodies= 7 coh= 0.146 end= :sterile
  t= 54 phase= :phase-0/planets-formed star?= true planets= 6 bodies= 7 coh= 0.130 end= :sterile
  t= 55 phase= :phase-0/planets-formed star?= true planets= 6 bodies= 7 coh= 0.113 end= :sterile
  t= 56 phase= :phase-0/planets-formed star?= true planets= 6 bodies= 7 coh= 0.096 end= :sterile
  t= 57 phase= :phase-0/planets-formed star?= true planets= 6 bodies= 7 coh= 0.080 end= :sterile
  t= 58 phase= :phase-0/planets-formed star?= true planets= 6 bodies= 7 coh= 0.063 end= :sterile
  t= 59 phase= :phase-0/planets-formed star?= true planets= 6 bodies= 7 coh= 0.046 end= :fadeout
  === DONE ending= :fadeout
● The full arc now works: **nebula → protostar collapse → fusion ignition (~tick 31) → 6 planets form → coherence drains → graceful fadeout.** That's the design's emergent sequence, and the sterile/fadeout ending is a legitimate soft-boundary outcome.
  
  Now let me rewrite the test against the ECS model. Let me see the current test first.
● ToolUse(Read)
⎿ 1	(ns domain.phase0-test
  2	  "Tests for Phase 0: Stellar Nebula simulation"
  3	  (:require
  4	   [clojure.test :refer [deftest testing is]]
  5	   [domain.phase0 :as phase0]
  6	   [domain.stellar :as stellar]
  7	   [domain.chemistry :as chemistry]
  8	   [domain.player :as player]
  9	   [law.stellar :as law]))
  10	
  11	(deftest test-nebula-creation
  12	  (testing "Nebula initial conditions"
  13	    (let [nebula (stellar/create-nebula 2e31 1e17 10.0)]
  14	      (is (= (:total-mass nebula) 2e31))
  15	      (is (= (:extent nebula) 1e17))
  16	      (is (= (:temperature nebula) 10.0))
  17	      (is (> (:density nebula) 0))
  18	      (is (= (get-in nebula [:composition :H]) 0.75)))))
  19	
  20	(deftest test-gravitational-collapse
  21	  (testing "Jeans instability and collapse"
  22	    (let [nebula {:density 1e-18
  23	                 :temperature 10
  24	                 :mass 2e31
  25	                 :radius 1e17}
  26	          collapse-rate (stellar/gravitational-collapse-rate nebula)]
  27	      (is (> collapse-rate 0) "Massive nebula should collapse"))))
  28	
  29	(deftest test-fusion-ignition
  30	  (testing "Fusion threshold detection"
  31	    (let [cold-body {:temperature 1e6
  32	                    :pressure 1e24
  33	                    :composition {:H 0.75}}
  34	          hot-body {:temperature 1.5e7
  35	                   :pressure 2e25
  36	                   :composition {:H 0.75}}]
  37	      (is (not (law/fusion-possible? cold-body)))
  38	      (is (law/fusion-possible? hot-body)))))
  39	
  40	(deftest test-hydrostatic-equilibrium
  ──── (109 lines hidden) ──────────────────────────────────────────────
  150	                                        :composition {:H2O 0.1}}]))
  151	          failed-world (-> (phase0/create-world)
  152	                         (assoc-in [:player-sprite :coherence] 0.01))
  153	          ending-success (phase0/handle-world-end successful-world)
  154	          ending-failure (phase0/handle-world-end failed-world)]
  155	      
  156	      (is (= (:type ending-success) :success))
  157	      (is (= (:type ending-failure) :fadeout)))))
  158	
  159	(deftest test-input-handling
  160	  (testing "Player input controls"
  161	    (let [world (phase0/create-world)
  162	          focused-world (phase0/handle-input world :narrow-focus)
  163	          moved-world (phase0/handle-input world :move-focus [1e15 1e15 0])]
  164	      
  165	      (is (< (get-in focused-world [:player-sprite :focus-radius])
  166	             (get-in world [:player-sprite :focus-radius])))
  167	      
  168	      (is (= (get-in moved-world [:player-sprite :focus-position])
  169	             [1e15 1e15 0])))))
● The pure-function tests mostly survive; the engine-shaped ones need rewriting for ECS. First let me remove the now-dead duplicate phase detector from `law/stellar.clj` (we converged on `phase0/detect-phase`).
● ToolUse(Edit)
⎿ The file /home/err/spaces/Truth/src/law/stellar.clj has been updated successfully. (file state is current in your context — no need to Read it back)
❯ 
● Now rewrite the test suite against the ECS model.
● ToolUse(Write)
⎿ The file /home/err/spaces/Truth/test/domain/phase0_test.clj has been updated successfully. (file state is current in your context — no need to Read it back)
● Now unify `infra/main.clj` onto the single world model — the console sim runs the converged ECS Phase 0, with the renderer demo kept as an explicit subcommand.
● ToolUse(Write)
⎿ The file /home/err/spaces/Truth/src/infra/main.clj has been updated successfully. (file state is current in your context — no need to Read it back)
● Let me check the orphaned Path A renderer before deciding its fate.
● ToolUse(Bash)
⎿ (ns infra.render.phase0-renderer
    "LWJGL 3D renderer for Phase 0: Stellar Nebula visualization.
     Renders nebula clouds, forming stars, accretion disks, and the player sprite."
    (:require
     [shape.spatial :as sp])
    (:import
     [org.lwjgl.opengl GL46 GL11 GL15 GL20 GL30]
     [org.lwjgl.system MemoryStack]
     [java.nio FloatBuffer IntBuffer]))
  
  ;; --- Shader Sources ---
  
  (def vertex-shader-source
    "#version 330 core
     layout (location = 0) in vec3 aPos;
     layout (location = 1) in vec3 aColor;
     layout (location = 2) in float aSize;
     
     out vec3 fragColor;
     out float pointSize;
     
     uniform mat4 projection;
     uniform mat4 view;
     uniform vec3 cameraPos;
     
     void main() {
         vec4 worldPos = vec4(aPos, 1.0);
         gl_Position = projection * view * worldPos;
         
         // Point size based on distance and intrinsic size
         float distance = length(cameraPos - aPos);
         pointSize = aSize / (1.0 + distance * 0.000001);
         gl_PointSize = clamp(pointSize, 1.0, 100.0);
         
         fragColor = aColor;
     }")
  
  (def fragment-shader-source
    "#version 330 core
     in vec3 fragColor;
● ToolUse(Bash)
⎿ 280:(defn render-frame
  ---refs to old model---
  321:  (when-let [star (:central-star stellar-system)]
  331:  (let [particles (map body-to-particle (:bodies stellar-system))
● ToolUse(Read)
⎿ 280	(defn render-frame
  281	  "Render one frame of Phase 0"
  282	  [{:keys [shader-program nebula-program projection-matrix view-matrix 
  283	           camera-position time] :as renderer}
  284	   {:keys [stellar-system player-sprite]}]
  285	  
  286	  ;; Clear and setup
  287	  (GL11/glClearColor 0.01 0.01 0.02 1.0)
  288	  (GL11/glClear (bit-or GL11/GL_COLOR_BUFFER_BIT GL11/GL_DEPTH_BUFFER_BIT))
  289	  (GL11/glEnable GL11/GL_BLEND)
  290	  (GL11/glBlendFunc GL11/GL_SRC_ALPHA GL11/GL_ONE_MINUS_SRC_ALPHA)
  291	  (GL11/glEnable GL11/GL_PROGRAM_POINT_SIZE)
  292	  
  293	  ;; Render nebula if present
  294	  (when-let [nebula (:nebula stellar-system)]
  295	    (GL20/glUseProgram nebula-program)
  296	    (GL20/glUniformMatrix4fv (GL20/glGetUniformLocation nebula-program "projection")
  297	                            false projection-matrix)
  298	    (GL20/glUniformMatrix4fv (GL20/glGetUniformLocation nebula-program "view")
  299	                            false view-matrix)
  300	    (GL20/glUniform1f (GL20/glGetUniformLocation nebula-program "time") time)
  301	    (GL20/glUniform1f (GL20/glGetUniformLocation nebula-program "density") 
  302	                     (:density nebula))
  303	    
  304	    (let [particles (nebula-particles nebula)
  305	          {:keys [vao count]} (create-vao particles)]
  306	      (GL30/glBindVertexArray vao)
  307	      (GL11/glDrawArrays GL11/GL_POINTS 0 count)
  308	      (GL30/glBindVertexArray 0)))
  309	  
  310	  ;; Render stellar bodies
  311	  (GL20/glUseProgram shader-program)
  312	  (GL20/glUniformMatrix4fv (GL20/glGetUniformLocation shader-program "projection")
  313	                          false projection-matrix)
  314	  (GL20/glUniformMatrix4fv (GL20/glGetUniformLocation shader-program "view")
  315	                          false view-matrix)
  316	  (GL20/glUniform3fv (GL20/glGetUniformLocation shader-program "cameraPos")
  317	                     (float-array camera-position))
  318	  (GL20/glUniform1f (GL20/glGetUniformLocation shader-program "time") time)
  319	  
  ──── (4 lines hidden) ──────────────────────────────────────────────
  324	    (let [{:keys [vao count]} (create-vao [(body-to-particle star)])]
  325	      (GL30/glBindVertexArray vao)
  326	      (GL11/glDrawArrays GL11/GL_POINTS 0 count)
  327	      (GL30/glBindVertexArray 0)))
  328	  
  329	  ;; Render other bodies
  330	  (GL20/glUniform1f (GL20/glGetUniformLocation shader-program "glow") 1.0)
  331	  (let [particles (map body-to-particle (:bodies stellar-system))
  332	        {:keys [vao count]} (create-vao particles)]
  333	    (GL30/glBindVertexArray vao)
  334	    (GL11/glDrawArrays GL11/GL_POINTS 0 count)
  335	    (GL30/glBindVertexArray 0))
  336	  
  337	  ;; Render player sprite
  338	  (GL20/glUniform1f (GL20/glGetUniformLocation shader-program "glow") 2.0)
  339	  (let [{:keys [vao count]} (create-vao [(sprite-particle player-sprite)])]
  340	    (GL30/glBindVertexArray vao)
  341	    (GL11/glDrawArrays GL11/GL_POINTS 0 count)
  342	    (GL30/glBindVertexArray 0))
  343	  
