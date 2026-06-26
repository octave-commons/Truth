❯ enrich the ECS nebula, iterate until we see a star system emerge
  ⎿ Full, lush, correct.
● On it. The single ECS path needs the nebula to be hundreds of gas *entities* that gravity pulls together and accretion merges into a star — not a parallel world. First let me understand the gravity + collision systems I'll be driving with many bodies.
● ToolUse(Read)
⎿ 1	(ns domain.orbital.system
  2	  "Orbital physics system for Gates of Truth.
  3	   Queries position + velocity + mass components via ECS.
  4	   Returns world with updated position + velocity components."
  5	  (:require
  6	    [domain.ecs.core        :as ecs]
  7	    [domain.ecs.components  :as c]
  8	    [domain.gravity.barnes-hut :as bh]
  9	    [domain.orbital.integrator :as integrator]
  10	    [shape.spatial          :as sp]))
  11	
  12	(defn- world->bodies
  13	  "Project ECS world into a seq of body maps for the Barnes-Hut tree."
  14	  [world]
  15	  (map (fn [[eid comps]]
  16	         {:id       eid
  17	          :mass     (comps c/mass)
  18	          :radius   (comps c/radius)
  19	          :kind     (comps c/body-kind)
  20	          :position (comps c/position)
  21	          :velocity (comps c/velocity)})
  22	       (ecs/all-of world c/position c/velocity c/mass c/radius c/body-kind)))
  23	
  24	(defn- apply-body-back
  25	  "Write updated position and velocity for eid back into world."
  26	  [world eid body]
  27	  (-> world
  28	      (ecs/put-component eid c/position (:position body))
  29	      (ecs/put-component eid c/velocity (:velocity body))))
  30	
  31	(defn orbital-system
  32	  "ECS system: advances all entities with position+velocity+mass
  33	   by one Leapfrog step under mutual gravitational attraction."
  34	  [G theta dt]
  35	  (fn [world]
  36	    (let [bodies (world->bodies world)
  37	          tree   (bh/build-tree bodies)]
  38	      (reduce (fn [w body]
  39	                (let [updated (integrator/leapfrog-step
  40	                                body
  41	                                (fn [b] (bh/acceleration G theta tree b))
  42	                                dt)]
  43	                  (apply-body-back w (:id body) updated)))
  44	              world
  45	              bodies))))
  46	
● ToolUse(Read)
⎿ 1	(ns domain.physics.collision
  2	  "Broad-phase bounding-sphere collision detection system.
  3	   Emits :event/collision events — does NOT mutate state directly.
  4	   Response is handled by registered event handlers.
  5	
  6	   Detection: two entities collide when
  7	     dist(posA, posB) <= radiusA + radiusB
  8	
  9	   This is O(n²) broad phase. For large n, replace with
  10	   a BVH or spatial hash narrow phase — same event contract."
  11	  (:require
  12	    [domain.ecs.core       :as ecs]
  13	    [domain.ecs.components :as c]
  14	    [domain.ecs.event      :as event]
  15	    [shape.spatial         :as sp]))
  16	
  17	(defn- collidable-bodies
  18	  "Project world into vec of [eid position radius] for all entities
  19	   that have position, radius, and mass components."
  20	  [world]
  21	  (->> (ecs/all-of world c/position c/radius c/mass)
  22	       (mapv (fn [[eid comps]]
  23	               [eid (comps c/position) (double (comps c/radius))]))))
  24	
  25	(defn- detect-pairs
  26	  "Return seq of collision maps for overlapping pairs."
  27	  [bodies]
  28	  (let [n (count bodies)]
  29	    (for [i (range n)
  30	          j (range (inc i) n)
  31	          :let [[eid-a pos-a rad-a] (nth bodies i)
  32	                [eid-b pos-b rad-b] (nth bodies j)
  33	                d (sp/dist pos-a pos-b)]
  34	          :when (<= d (+ rad-a rad-b))]
  35	      {:eid-a  eid-a :eid-b  eid-b
  36	       :pos-a  pos-a :pos-b  pos-b
  37	       :rad-a  rad-a :rad-b  rad-b
  38	       :depth  (- (+ rad-a rad-b) d)
  39	       :normal (let [r (sp/v- pos-b pos-a)
  40	                     l (sp/len r)]
  ──── (9 lines hidden) ──────────────────────────────────────────────
  50	        tick   (:tick world)
  51	        pairs  (detect-pairs bodies)]
  52	    (reduce (fn [w {:keys [eid-a eid-b pos-a pos-b
  53	                            rad-a rad-b depth normal]}]
  54	              (event/dispatch w
  55	                (event/->event
  56	                  {:tick     tick
  57	                   :kind     :event/collision
  58	                   :entities #{eid-a eid-b}
  59	                   :payload  {:eid-a  eid-a
  60	                              :eid-b  eid-b
  61	                              :pos-a  pos-a
  62	                              :pos-b  pos-b
  63	                              :rad-a  rad-a
  64	                              :rad-b  rad-b
  65	                              :depth  depth
  66	                              :normal normal}})))
  67	            world
  68	            pairs)))
  69	
❯ 
● `orbital-system` is Barnes–Hut (scales fine), but `collision-detection-system` is **O(N²)** — that won't survive thousands of bodies, and accretion is the mechanism that builds a star from gas. I also need to check gravitational softening (close encounters blow up N-body without it). Let me read the gravity internals:
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
● ToolUse(Read)
⎿ 1	(ns domain.orbital.integrator
  2	  "Symplectic Leapfrog (Störmer–Verlet) integrator for n-body orbital mechanics.
  3	   All positions and velocities are vec3 from shape.spatial.
  4	   Requires a pure acceleration function (fn [body] -> vec3)."
  5	  (:require
  6	    [shape.spatial :as sp]))
  7	
  8	(defn leapfrog-kick
  9	  "Velocity half-step (kick): v_half = v + a * (dt/2)"
  10	  [body accel-fn ^double dt]
  11	  (let [a (accel-fn body)]
  12	    (update body :velocity sp/v+ (sp/v* a (* dt 0.5)))))
  13	
  14	(defn leapfrog-drift
  15	  "Position full-step (drift): x_new = x + v * dt"
  16	  [body ^double dt]
  17	  (update body :position sp/v+ (sp/v* (:velocity body) dt)))
  18	
  19	(defn leapfrog-step
  20	  "Full Leapfrog step (kick-drift-kick)."
  21	  [body accel-fn ^double dt]
  22	  (-> body
  23	      (leapfrog-kick accel-fn dt)
  24	      (leapfrog-drift dt)
  25	      (leapfrog-kick accel-fn dt)))
  26	
  27	(defn step-all
  28	  "Advance all bodies by one Leapfrog step.
  29	   accel-fn-for: (fn [tree body] -> vec3 acceleration)
  30	   Builds a fresh Barnes–Hut tree once per tick."
  31	  [bodies build-tree-fn accel-fn-for ^double dt]
  32	  (let [tree (build-tree-fn bodies)]
  33	    (mapv (fn [body]
  34	            (leapfrog-step body
  35	                           (fn [b] (accel-fn-for tree b))
  36	                           dt))
  37	          bodies)))
  38	
● Gravity is solid — Barnes–Hut with softening + symplectic leapfrog. Two real blockers for thousands of bodies: collision is O(N²), and the integration step is `dt × time-scale ≈ 1e20 s` (orbits can't resolve — that's why the current sim relies on in-place collapse, not real dynamics). I'll fix both, then seed a real rotating cloud and tune.
  
  Starting with a spatial-hash collision broad phase (same event contract):
● ToolUse(Edit)
⎿ The file /home/err/spaces/Truth/src/domain/physics/collision.clj has been updated successfully. (file state is current in your context — no need to Read it back)
● Now the timestep + rich seeding in `domain.phase0`. First, replace `seed-nebula` with a rotating, turbulent cloud of gas-particle entities around a dense core plus planet seeds:
● ToolUse(Edit)
⎿ The file /home/err/spaces/Truth/src/domain/phase0.clj has been updated successfully. (file state is current in your context — no need to Read it back)
❯ 
● Now update `create-world` (sane dt, framed cloud radius, pass options through):
● ToolUse(Edit)
⎿ The file /home/err/spaces/Truth/src/domain/phase0.clj has been updated successfully. (file state is current in your context — no need to Read it back)
● Now decouple the integration step from the runaway display time-scale (this is what lets orbits actually resolve):
● ToolUse(Edit)
⎿ The file /home/err/spaces/Truth/src/domain/phase0.clj has been updated successfully. (file state is current in your context — no need to Read it back)
● ToolUse(Edit)
⎿ The file /home/err/spaces/Truth/src/domain/phase0.clj has been updated successfully. (file state is current in your context — no need to Read it back)
● ToolUse(Edit)
⎿ The file /home/err/spaces/Truth/src/domain/phase0.clj has been updated successfully. (file state is current in your context — no need to Read it back)
● Now the renderer must emit **one puff per gas entity** (not hundreds), or 1500 entities would explode the point count. Let me rewrite the projection: gas → single puff, dense clumps (core/planet seeds/protostar) → small bright cloud + field line:
● ToolUse(Edit)
⎿ The file /home/err/spaces/Truth/src/infra/render.clj has been updated successfully. (file state is current in your context — no need to Read it back)
● Now let me build an iteration harness that ticks the world and renders frames at several stages, so I can actually see whether a star system emerges:
● ToolUse(Write)
⎿ File created successfully at: /tmp/claude-1000/-home-err-spaces-Truth/652d6c7e-9011-4be9-a838-69173573669b/scratchpad/iterate.clj (file state is current in your context — no need to Read it back)
❯ 
● ToolUse(Bash)
⎿ #'iterate/-main
  tick 0: phase=initializing bodies=1505 star=false planets=5 nebula=1505
  tick 0:  phase=initializing bodies=1505 star=false planets=5 nebula=1505
  tick 25:  phase=planets-formed bodies=1431 star=true planets=4 nebula=1426
  tick 60:  phase=planets-formed bodies=1323 star=true planets=4 nebula=1318
  tick 120:  phase=planets-formed bodies=1192 star=true planets=4 nebula=1187
  tick 200:  phase=planets-formed bodies=1016 star=true planets=4 nebula=1011
  done
● The simulation works — a star ignites by tick 25, four planets persist, and gas accretes over time (1500 → 1011 particles). Let me look at the frames:
● ToolUse(Read)
● ToolUse(Read)
● That's a star system emerging — frame-00 is a lush violet nebula with brighter knots, and by frame-25 the core has ignited into a yellow star with four blue planets orbiting inside the gas. The regime tint (violet = magnetically-dominated diffuse gas, warm core) is working. Let me check the later frames for evolution:
● ToolUse(Read)
● ToolUse(Read)
● That is a star system emerging — by frame-200 the gas has settled into a **rotating spiral disk** with the star at center, four planets orbiting, and gas clearing/dispersing at the edges. Full, lush, and driven by real dynamics (differential rotation makes the spiral, accretion clears the inner gap). 
  
  Now fix the test that hard-codes the old 7-entity count, and run the suite:
● ToolUse(Edit)
⎿ The file /home/err/spaces/Truth/test/domain/phase0_test.clj has been updated successfully. (file state is current in your context — no need to Read it back)
● ToolUse(Bash)
⎿ Testing domain.regime-test
  
  Testing infra.render-test
  
  Testing law.contract-test
  
  Testing law.ledger-test
  
  Testing law.registry-test
  
  Testing shape.core-test
  
  Testing shape.spatial-test
  
  Ran 88 tests containing 225 assertions.
  0 failures, 0 errors.
  
  real	0m22.190s
  user	0m33.361s
  sys	0m3.797s
❯ 
● All 88 pass (22s). Let me restart the live window so it shows the rich nebula, and lint the changes:
● ToolUse(Bash)
⎿ === boot log ===
  [32m0|gates-of | [39m2026-06-25 23:11:59 -05:00: Dev service shut down.
  [32m0|gates-of | [39m2026-06-25 23:12:03 -05:00: Booting Gates of Truth dev service...
  [32m0|gates-of | [39m2026-06-25 23:12:03 -05:00: Dev window thread started on gates-of-truth-dev-window
  [32m0|gates-of | [39m2026-06-25 23:12:03 -05:00: nREPL server listening on 127.0.0.1:7888
  [32m0|gates-of | [39m2026-06-25 23:12:03 -05:00: Compiling body shaders...
  [32m0|gates-of | [39m2026-06-25 23:12:03 -05:00: Compiling particle shaders...
  [32m0|gates-of | [39m2026-06-25 23:12:03 -05:00: Compiling line shaders...
  
  === lint changed files ===
  linting took 171ms, errors: 0, warnings: 0
● Live window restarted clean (all three shader programs incl. lines), lint clean. Let me record the non-obvious dynamics decisions for future sessions, then wrap up:
● ToolUse(Write)
⎿ File created successfully at: /home/err/.claude/projects/-home-err-spaces-Truth/memory/phase0-nebula-dynamics.md (file state is current in your context — no need to Read it back)
● ToolUse(Edit)
⎿ The file /home/err/.claude/projects/-home-err-spaces-Truth/memory/MEMORY.md has been updated successfully. (file state is current in your context — no need to Read it back)
