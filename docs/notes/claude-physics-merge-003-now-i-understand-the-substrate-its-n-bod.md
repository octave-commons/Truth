● ToolUse(Read)
● ToolUse(Read)
⎿ 1	(ns law.contract
  2	  "Contracts govern which Claims over Shapes are admissible.
  3	   They do not perform side-effects; they return facts about validity
  4	   that other layers can record in the ledger."
  5	  (:require
  6	    [shape.core :as shape]
  7	    [clojure.set :as set]))
  8	
  9	(defrecord Contract
  10	  [id shape-id kind schema name description on-true on-false on-any])
  11	
  12	(defn ->contract
  13	  "Construct a Contract. Required keys:
  14	   - :id
  15	   - :shape-id
  16	   - :kind       (e.g. :type or :quality)
  17	   - :schema     (validation description)
  18	   Optional: :name, :description, :on-true, :on-false, :on-any"
  19	  [{:keys [id shape-id kind schema name description on-true on-false on-any] :as m}]
  20	  (when-not (and id shape-id kind schema)
  21	    (throw (ex-info "Contract requires :id, :shape-id, :kind, and :schema"
  22	                    {:kind ::invalid-contract :contract m})))
  23	  (->Contract id shape-id kind schema name description on-true on-false on-any))
  24	
  25	;; --- Validation -------------------------------------------------------------
  26	
  27	(def ^:const ok ::ok)
  28	(def ^:const violation ::violation)
  29	
  30	(defn- value-keys
  31	  "Utility: safe read of top-level keys for a claim value; non-maps return empty set."
  32	  [v]
  33	  (if (map? v) (set (keys v)) #{}))
  34	
  35	(defn- validate-schema
  36	  "Validate `value` against simple predicate schema:
  37	   schema = {k predicate}, all predicates must return truthy.
  38	   Returns nil on success, or a seq of issue maps."
  39	  [schema value]
  40	  (reduce
  ──── (25 lines hidden) ──────────────────────────────────────────────
  66	(defn validate
  67	  "Validate a Claim against a Contract.
  68	   Returns either ::ok or a violation map.
  69	   For :type contracts, no extra keys are allowed.
  70	   For :quality contracts, extra keys are allowed if explicit constraints hold."
  71	  [^Contract c ^shape.core.Claim claim]
  72	  (let [{:keys [kind schema]} c
  73	        value        (:value claim)
  74	        base-issues  (validate-schema schema value)
  75	        extra-issues (if (= kind :type)
  76	                       (type-extra-keys schema value)
  77	                       [])
  78	        issues       (into base-issues extra-issues)]
  79	    (if (seq issues)
  80	      {:result   violation
  81	       :contract c
  82	       :claim    claim
  83	       :issues   issues}
  84	      ok)))
  85	
⎿ 1	(ns infra.render.phase0-renderer
  2	  "LWJGL 3D renderer for Phase 0: Stellar Nebula visualization.
  3	   Renders nebula clouds, forming stars, accretion disks, and the player sprite."
  4	  (:require
  5	   [shape.spatial         :as sp]
  6	   [domain.ecs.core       :as ecs]
  7	   [domain.ecs.components  :as c]
  8	   [domain.player         :as player]
  9	   [domain.stellar        :as stellar])
  10	  (:import
  11	   [org.lwjgl.opengl GL46 GL11 GL15 GL20 GL30]))
  12	
  13	;; --- Shader Sources ---
  14	
  15	(def vertex-shader-source
  16	  "#version 330 core
  17	   layout (location = 0) in vec3 aPos;
  18	   layout (location = 1) in vec3 aColor;
  19	   layout (location = 2) in float aSize;
  20	   
  21	   out vec3 fragColor;
  22	   out float pointSize;
  23	   
  24	   uniform mat4 projection;
  25	   uniform mat4 view;
  26	   uniform vec3 cameraPos;
  27	   
  28	   void main() {
  29	       vec4 worldPos = vec4(aPos, 1.0);
  30	       gl_Position = projection * view * worldPos;
  31	       
  32	       // Point size based on distance and intrinsic size
  33	       float distance = length(cameraPos - aPos);
  34	       pointSize = aSize / (1.0 + distance * 0.000001);
  35	       gl_PointSize = clamp(pointSize, 1.0, 100.0);
  36	       
  37	       fragColor = aColor;
  38	   }")
  39	
  40	(def fragment-shader-source
  ──── (379 lines hidden) ──────────────────────────────────────────────
  420	                           false view-matrix)
  421	  (GL20/glUniform3fv (GL20/glGetUniformLocation shader-program "cameraPos")
  422	                     (float-array camera-position))
  423	  (GL20/glUniform1f (GL20/glGetUniformLocation shader-program "time") time)
  424	  (GL20/glUniform1f (GL20/glGetUniformLocation shader-program "glow") 1.0)
  425	  (when-let [body-particles (seq (world->body-particles world))]
  426	    (let [{:keys [vao count]} (create-vao body-particles)]
  427	      (GL30/glBindVertexArray vao)
  428	      (GL11/glDrawArrays GL11/GL_POINTS 0 count)
  429	      (GL30/glBindVertexArray 0)))
  430	
  431	  ;; --- Player sprite (always on top) ---
  432	  (when-let [obs (player/get-observer world)]
  433	    (GL20/glUniform1f (GL20/glGetUniformLocation shader-program "glow") 2.0)
  434	    (let [{:keys [vao count]} (create-vao [(sprite-particle obs)])]
  435	      (GL30/glBindVertexArray vao)
  436	      (GL11/glDrawArrays GL11/GL_POINTS 0 count)
  437	      (GL30/glBindVertexArray 0)))
  438	
  439	  (assoc renderer :time (+ time 0.016)))
❯ 
● Now I understand the substrate: it's N-body point-like clumps with thermodynamic state, not a spatial grid. Let me read the existing tests so I don't break them, and confirm what behavior is asserted.
● ToolUse(Read)
⎿ 1	(ns domain.phase0-test
  2	  "Tests for Phase 0: Stellar Nebula — the ECS-based simulation.
  3	   The world is a single ECS world; Phase 0 is a composition layer over it."
  4	  (:require
  5	   [clojure.test :refer [deftest testing is]]
  6	   [domain.phase0           :as phase0]
  7	   [domain.stellar          :as stellar]
  8	   [domain.chemistry        :as chemistry]
  9	   [domain.player           :as player]
  10	   [law.stellar             :as law]
  11	   [domain.ecs.core         :as ecs]
  12	   [domain.ecs.event        :as event]
  13	   [domain.ecs.components    :as c]
  14	   [domain.physics.collision :as collision]
  15	   [shape.spatial           :as sp]))
  16	
  17	;; --- Pure physics -----------------------------------------------------------
  18	
  19	(deftest test-gravitational-collapse
  20	  (testing "A diffuse, massive, cold region is Jeans-unstable"
  21	    (let [region {:density 1e-18 :temperature 10 :radius 1e17}]
  22	      (is (> (stellar/gravitational-collapse-rate region) 0))
  23	      (is (stellar/jeans-unstable? region))))
  24	  (testing "A small dense warm region is stable against collapse"
  25	    (let [region {:density 5500 :temperature 300 :radius 1e5}]
  26	      (is (not (stellar/jeans-unstable? region))))))
  27	
  28	(deftest test-virial-collapse-drives-ignition
  29	  (testing "Virial temperature and self-gravity pressure rise as a core contracts"
  30	    (let [m 2e30]
  31	      (is (> (stellar/virial-temperature m 1e9)
  32	             (stellar/virial-temperature m 1e10)))
  33	      (is (> (stellar/self-gravity-pressure m 1e9)
  34	             (stellar/self-gravity-pressure m 1e10))))))
  35	
  36	(deftest test-fusion-ignition
  37	  (testing "Fusion needs temperature, pressure, and hydrogen above threshold"
  38	    (is (not (law/fusion-possible? {:temperature 1e6 :pressure 1e24 :composition {:H 0.75}})))
  39	    (is (not (law/fusion-possible? {:temperature 2e7 :pressure 1e8  :composition {:H 0.75}})))
  40	    (is (not (law/fusion-possible? {:temperature 2e7 :pressure 1e14 :composition {:H 0.02}})))
  ──── (139 lines hidden) ──────────────────────────────────────────────
  180	                         c/luminosity 0.0})
  181	                      (assoc :phase0/phase :phase-0/planets-formed))]
  182	      (is (= :success (:type (phase0/world-ending w))))))
  183	
  184	  (testing "Exhausted coherence yields a graceful fadeout"
  185	    (let [w (-> (phase0/create-world)
  186	                (player/update-observer #(assoc % :coherence 0.01)))]
  187	      (is (= :fadeout (:type (phase0/world-ending w)))))))
  188	
  189	;; --- Input ------------------------------------------------------------------
  190	
  191	(deftest test-input-handling
  192	  (testing "Controls operate on the observer in the world"
  193	    (let [w        (phase0/create-world)
  194	          before   (:focus-radius (player/get-observer w))
  195	          narrowed (phase0/handle-input w :narrow-focus)
  196	          moved    (phase0/handle-input w :move-focus [1e15 1e15 0])]
  197	      (is (< (:focus-radius (player/get-observer narrowed)) before))
  198	      (is (= [1e15 1e15 0] (:focus-position (player/get-observer moved)))))))
  199	
● ToolUse(Bash)
⎿ (ns infra.render.phase0-renderer-test
    "Tests for the Phase 0 renderer's pure geometry projection fns.
  
     These tests verify that the renderer correctly maps the domain's
     matter-state ontology onto the two render passes: volumetric clouds for
     :nebula/:protostar and point sprites for :star/:planet."
    (:require
     [clojure.test :refer [deftest testing is]]
     [domain.ecs.core :as ecs]
     [domain.ecs.event :as event]
     [domain.player :as player]
     [domain.stellar :as stellar]
     [infra.render.phase0-renderer :as r]))
  
  (deftest test-cloud-sample-count-scales-with-radius-and-focus
    (testing "Larger regions and higher focus receive more samples"
      (let [small {:radius 1e10}
            large {:radius 1e17}]
        (is (< (r/cloud-sample-count small 0.0)
               (r/cloud-sample-count large 0.0)))
        (is (< (r/cloud-sample-count large 0.0)
               (r/cloud-sample-count large 1.0))))))
  
  (deftest test-nebula-particles-are-distributed-in-radius
    (testing "Generated cloud samples lie inside the physical extent"
      (let [extent 1e17
            particles (r/nebula-particles
                       {:center [0.0 0.0 0.0]
                        :extent extent
                        :density 0.5
                        :composition {:H 0.75 :He 0.24}
                        :count 100})]
        (is (= 100 (count particles)))
        (is (every? #(<= (Math/sqrt (apply + (map * (:position %) (:position %))))
                         extent)
                    particles))
        (is (every? #(= 3 (count (:color %))) particles))
        (is (every? #(float? (:density %)) particles))
        (is (every? #(pos? (:size %)) particles)))))
  
  ──── (16 lines hidden) ──────────────────────────────────────────────
            :main-opts ["-m" "cognitect.test-runner"]
            :exec-fn cognitect.test-runner.api/test}
     :repl {:extra-deps {nrepl/nrepl {:mvn/version "1.0.0"}}
            :main-opts ["-m" "nrepl.cmdline"]}
     :dev  {:extra-deps {nrepl/nrepl {:mvn/version "1.0.0"}}
            :main-opts ["-m" "infra.dev.server"]}
     :run {:main-opts ["-m" "infra.main"]}}}
  ---LEDGER EVENT API---
  19:(defn new-event-id [] (java.util.UUID/randomUUID))
  21:(defn ->event
  35:(defn with-ledger
  43:(defn with-handlers
  48:(defn register-handler
  54:(defn install-reaction
  64:(defn install-rewind
  73:(defn emit
  79:(defn dispatch
  89:(defn dispatch-all
  94:(defn events-since
  99:(defn events-of-kind
● This is an N-body substrate (point-like clumps with thermodynamic state), not a grid — so "fields" are per-entity, and the honest, implementable physics is: a magnetic field vector per clump, **flux-freezing** (B ∝ ρ^⅔ as a clump compresses), magnetic-pressure support, a **non-ideal resistive decay** step (matters only in dense cores — correct), and the **regime classifier** (β, Mach, M_A, Jeans) the design doc made the keystone. Then render regime + field lines.
  
  Key test constraints I must honor: `test-time-scale` asserts the system count (will go 6→8), and `test-full-simulation` must still ignite a star + form planets. The magnetic terms are dynamically negligible at these masses (β ≫ 1 — which the classifier will *correctly report*), so they won't block ignition. Let me build it.
● ToolUse(Write)
⎿ File created successfully at: /home/err/spaces/Truth/src/law/field.clj (file state is current in your context — no need to Read it back)
● ToolUse(Write)
⎿ File created successfully at: /home/err/spaces/Truth/src/domain/em.clj (file state is current in your context — no need to Read it back)
● ToolUse(Write)
⎿ File created successfully at: /home/err/spaces/Truth/src/domain/regime.clj (file state is current in your context — no need to Read it back)
● I botched the end of that file (placeholder constants that don't exist). Let me rewrite it cleanly — the Jeans constants live in `law.stellar`.
● ToolUse(Write)
⎿ The file /home/err/spaces/Truth/src/domain/regime.clj has been updated successfully. (file state is current in your context — no need to Read it back)
● Now add the two new components:
● ToolUse(Edit)
⎿ The file /home/err/spaces/Truth/src/domain/ecs/components.clj has been updated successfully. (file state is current in your context — no need to Read it back)
❯ 
● Now wire EM into `domain.stellar`: seed a field, gate collapse on magnetic support, and flux-freeze B during contraction.
● ToolUse(Edit)
⎿ The file /home/err/spaces/Truth/src/domain/stellar.clj has been updated successfully. (file state is current in your context — no need to Read it back)
● ToolUse(Edit)
⎿ The file /home/err/spaces/Truth/src/domain/stellar.clj has been updated successfully. (file state is current in your context — no need to Read it back)
● ToolUse(Edit)
⎿ The file /home/err/spaces/Truth/src/domain/stellar.clj has been updated successfully. (file state is current in your context — no need to Read it back)
● ToolUse(Edit)
⎿ The file /home/err/spaces/Truth/src/domain/stellar.clj has been updated successfully. (file state is current in your context — no need to Read it back)
● Now wire the two new systems into the Phase 0 tick pipeline in the documented order:
● ToolUse(Edit)
⎿ The file /home/err/spaces/Truth/src/domain/phase0.clj has been updated successfully. (file state is current in your context — no need to Read it back)
● ToolUse(Edit)
⎿ The file /home/err/spaces/Truth/src/domain/phase0.clj has been updated successfully. (file state is current in your context — no need to Read it back)
● Now update the test that asserts the system count, then run tests before touching the renderer:
● ToolUse(Edit)
⎿ The file /home/err/spaces/Truth/test/domain/phase0_test.clj has been updated successfully. (file state is current in your context — no need to Read it back)
❯ 
● ToolUse(Bash)
⎿ Testing domain.ecs.core-test
  
  Testing domain.ecs.dsl-test
  
  Testing domain.ecs.event-test
  
  Testing domain.ecs.ledger-test
  
  Testing domain.ecs.rewind-test
  
  Testing domain.gravity.barnes-hut-test
  
  Testing domain.orbital.system-test
  
  Testing domain.particles.fft-test
  
  Testing domain.particles.field-test
  
  Testing domain.particles.phase0-test
  
  Testing domain.particles.pm-test
  
  Testing domain.phase0-test
  
  Testing domain.physics.collision-test
  
  Testing infra.render.phase0-renderer-test
  
  Testing law.contract-test
  
  Testing law.ledger-test
  
  Testing law.registry-test
  
  Testing shape.core-test
  
  Testing shape.spatial-test
  
  Ran 88 tests containing 203 assertions.
  0 failures, 0 errors.
