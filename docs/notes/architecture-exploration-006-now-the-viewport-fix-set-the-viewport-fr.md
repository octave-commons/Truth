● Now the viewport fix — set the viewport from the actual framebuffer size each frame:
● ToolUse(Edit)
⎿ The file /home/err/spaces/Truth/src/infra/render.clj has been updated successfully. (file state is current in your context — no need to Read it back)
● Now make the window pass the real framebuffer size (HiDPI/resize-aware) to `render-scene`:
● ToolUse(Edit)
⎿ The file /home/err/spaces/Truth/src/infra/dev/window.clj has been updated successfully. (file state is current in your context — no need to Read it back)
● Now replace the jumpy tiered pacing with a continuous function of formation progress (addresses "shouldn't be jumps, should be dynamic, scale too much"). Dynamic `dt` stays large through the Myr-scale collapse/contraction and only refines once tight planetary orbits form:
● ToolUse(Edit)
⎿ The file /home/err/spaces/Truth/src/domain/phase0.clj has been updated successfully. (file state is current in your context — no need to Read it back)
❯ 
● Now wire the continuous pacing into `create-world` and `tick-world`:
● ToolUse(Edit)
⎿ The file /home/err/spaces/Truth/src/domain/phase0.clj has been updated successfully. (file state is current in your context — no need to Read it back)
● ToolUse(Edit)
⎿ The file /home/err/spaces/Truth/src/domain/phase0.clj has been updated successfully. (file state is current in your context — no need to Read it back)
● Now rate-limit the protostar contraction — the genuine flaw making ignition ~1000× too fast. It currently contracts a fixed fraction *per tick* regardless of `dt`; instead it should contract on a Kelvin-Helmholtz-like timescale so ignition spreads over sim-time:
● ToolUse(Read)
⎿ 265	(defn collapse-system
  266	  "A protostar — a clump that has accreted past the star-forming mass — contracts
  267	   each tick under self-gravity: radius shrinks, density rises, and virial heating
  268	   drives core temperature and pressure toward ignition (Kelvin–Helmholtz
  269	   contraction). Its frozen-in magnetic field amplifies as B ∝ ρ^(2/3).
  270	
  271	   Diffuse gas does NOT collapse in place here — it assembles by N-body gravity
  272	   and accretion (collisions). Only the resolved star-forming core contracts."
  273	  [{:keys [phase0/collapse-fraction] :or {collapse-fraction 0.5} :as world}]
  274	  (reduce
● ToolUse(Edit)
⎿ The file /home/err/spaces/Truth/src/domain/stellar.clj has been updated successfully. (file state is current in your context — no need to Read it back)
● I wrapped the `reduce` in a `let`, so I need to close the extra paren at the end of `collapse-system`:
● ToolUse(Read)
⎿ 295	                c'          (:polar-radius shape)
  296	                new-density (oblate-density mass a' c')
  297	                r-eq        (equivalent-radius a' c')
  298	                new-temp    (virial-temperature mass r-eq)
  299	                new-press   (self-gravity-pressure mass r-eq)
  300	                new-spin    (:spin shape)
  301	                new-axis    (:rotation-axis shape)
  302	                anisotropy  (- 1.0 (:oblateness shape))
  303	                new-b       (when-let [b (:b-field region)]
  304	                              (em/flux-freeze b (:density region) new-density anisotropy))]
  305	            (cond-> w
  306	              true  (ecs/put-component eid c/radius         a')
  307	              true  (ecs/put-component eid c/density        new-density)
  308	              true  (ecs/put-component eid c/temperature    new-temp)
  309	              true  (ecs/put-component eid c/pressure       new-press)
  310	              true  (ecs/put-component eid c/spin           new-spin)
  311	              true  (ecs/put-component eid c/oblateness     (:oblateness shape))
  312	              true  (ecs/put-component eid c/rotation-axis  new-axis)
  313	              new-b (ecs/put-component eid c/b-field        new-b)))
  314	          w)))
  315	   world
  316	   (ecs/entities-with world c/matter-state c/temperature c/density c/radius c/mass)))
● ToolUse(Edit)
⎿ The file /home/err/spaces/Truth/src/domain/stellar.clj has been updated successfully. (file state is current in your context — no need to Read it back)
● Now diffuse the cloud (longer free-fall → slower collapse), add the contraction-time knob, and raise the initial spin to encourage a rotationally-supported disk. Let me update `create-world`:
● ToolUse(Read)
⎿ 155	  [world summ]
  156	  (let [eids   (ecs/entities-with world c/mass)
  157	        [m mt peak]
  158	        (reduce (fn [[m mt peak] eid]
  159	                  (let [mass (double (or (ecs/get-component world eid c/mass) 0.0))
  160	                        t    (double (or (ecs/get-component world eid c/temperature) 0.0))]
  161	                    [(+ m mass) (+ mt (* mass t)) (max peak t)]))
  162	                [0.0 0.0 0.0] eids)]
  163	    {:total-mass-kg   m
  164	     :total-mass-msun (/ m solar-mass)
  165	     :avg-temp        (if (pos? m) (/ mt m) 0.0)
  166	     :peak-temp       peak
  167	     :body-count      (:body-count summ)
  168	     :resolved-count  (:resolved-count summ)
  169	     :star-count      (count (:stars summ))
  170	     :planet-count    (:planet-count summ)}))
  171	
  172	;; --- World construction -----------------------------------------------------
  173	
  174	(defn create-world
  175	  "Bootstrap a Phase 0 world ready to tick."
  176	  ([] (create-world {}))
  177	   ([{:keys [G theta dt softening nebula-mass nebula-radius collapse-fraction gas-count]
  178	      ;; `softening` is matched to the timestep: with dt=1e12 s and a central
  179	      ;; core up to a few×1e30 kg, the dynamical time at the Plummer length must
  180	      ;; exceed dt or close passes inject energy and eject gas (the cloud
  181	      ;; "evaporates"). ε ≳ (G·M·dt²)^(1/3) ≈ 5e14 m keeps the system bound.
  182	      :or   {G law/G theta 0.5 dt 1e12 softening 5.0e14
  183	             nebula-mass 4e30 nebula-radius 1.5e16 collapse-fraction 0.5
  184	             gas-count 1000}}]
  185	   (let [neb    (pacing-for 0.0 0.0)
  186	         base   (-> (ecs/empty-world)
  187	                    (event/with-ledger)
  188	                    (event/register-handler :event/collision
  189	                                            stellar/stellar-merge-handler)
  190	                    (assoc :sim/G G :sim/theta theta :sim/dt dt :sim/softening softening
  191	                           :phase0/sim-time          0.0
  192	                           :phase0/time-scale        (:rate neb)
  193	                           :phase0/rate-yr           (:rate-yr neb)
  194	                           :phase0/stats             nil
  195	                           :phase0/complexity        0
  196	                           :phase0/phase             :initializing
  197	                           :phase0/active            true
  198	                           :phase0/collapse-fraction collapse-fraction))
  199	         seeded (seed-nebula base nebula-mass nebula-radius {:gas-count gas-count})
  200	         [w _]  (player/spawn-observer seeded (sp/vec3 0 0 (* nebula-radius 2)))]
  201	     w)))
  202	
❯ 
● ToolUse(Edit)
⎿ The file /home/err/spaces/Truth/src/domain/phase0.clj has been updated successfully. (file state is current in your context — no need to Read it back)
● Now update the `test-pacing`/`test-stats` tests for the new continuous API:
● ToolUse(Edit)
⎿ The file /home/err/spaces/Truth/test/domain/phase0_test.clj has been updated successfully. (file state is current in your context — no need to Read it back)
● Now run the full suite to confirm correctness:
● ToolUse(Bash)
⎿ Testing infra.render-test
  
  Testing law.contract-test
  
  Testing law.ledger-test
  
  Testing law.registry-test
  
  Testing shape.core-test
  
  Testing shape.spatial-test
  
  Ran 140 tests containing 372 assertions.
  3 failures, 0 errors.
● ToolUse(Bash)
⎿ FAIL in (test-full-simulation) (phase0_test.clj:195)
  A gas cloud collapses and a star + other bodies emerge by accretion
  a star should ignite from the collapsing cloud
  expected: (:star? summ)
    actual: false
  
  Testing domain.physics.collision-test
  
  Testing domain.regime-test
  
  Testing domain.stellar-test
  
  FAIL in (test-collapse-floors-at-main-sequence) (stellar_test.clj:241)
  A protostar contracts toward, but never below, its main-sequence radius
  radius settles AT the floor
  expected: (< (Math/abs (- r floor)) (* 0.01 floor))
    actual: (not (< 9.192370141033161E14 6978904.806382341))
  
  FAIL in (test-collapse-floors-at-main-sequence) (stellar_test.clj:243)
  A protostar contracts toward, but never below, its main-sequence radius
  contraction has stopped
  expected: (< (Math/abs (- r (ecs/get-component (stellar/collapse-system w') eid c/radius))) (* 1.0E-6 floor))
    actual: (not (< 9.67109549880375E11 697.8904806382341))
  
  Testing infra.render-test
  
  Testing law.contract-test
  
  Testing law.ledger-test
  
  Testing law.registry-test
● Both failures are expected consequences of the deliberate slowdown: formation now takes far longer than the tests' budgets, and contraction is gradual rather than one-shot. Let me look at those tests and update their contracts:
● ToolUse(Read)
⎿ 222	      (is (< (law-ns 5e29) (law-ns 1.989e30))
  223	          "a lower-mass star is smaller")
  224	      (is (> (law-ns 4e30) (law-ns 1.989e30))
  225	          "a higher-mass star is larger"))))
  226	
  227	(deftest test-collapse-floors-at-main-sequence
  228	  (testing "A protostar contracts toward, but never below, its main-sequence radius"
  229	    (let [floor   ((requiring-resolve 'law.stellar/main-sequence-radius) 2e30)
  230	          base    (ecs/empty-world)
  231	          [w eid] (stellar/spawn-clump base {:position [0.0 0.0 0.0]
  232	                                             :velocity [0.0 0.0 0.0]
  233	                                             :mass 2e30
  234	                                             :radius 1e15
  235	                                             :matter-state :protostar
  236	                                             :angular-momentum [0.0 0.0 0.0]})
  237	          ;; many contraction steps — old code would halve to a point each tick
  238	          w'      (nth (iterate stellar/collapse-system w) 80)
  239	          r       (ecs/get-component w' eid c/radius)]
  240	      (is (>= r (* 0.999 floor)) "radius does not collapse below the floor")
  241	      (is (< (Math/abs (- r floor)) (* 0.01 floor)) "radius settles AT the floor")
  242	      ;; one more step does not shrink it further
  243	      (is (< (Math/abs (- r (ecs/get-component (stellar/collapse-system w') eid c/radius)))
  244	             (* 1e-6 floor))
  245	          "contraction has stopped"))))
  246	
  247	;; --- Accretion radius (stars keep eating) ------------------------------------
  248	
  249	(deftest test-accretion-radius-set-on-protostar
  250	  (testing "A clump reaching star-forming mass freezes its radius as a feeding zone"
  251	    (let [base    (ecs/empty-world)
● Both are deliberate behavior changes (slower formation, gradual contraction). I'll update the tests to assert the invariants in regimes that exercise them within budget — they shouldn't be coupled to the production pacing defaults. First, the contraction-floor test (set a fast contraction regime so it still reaches the floor, validating the invariant + the new rate-limit machinery):
● ToolUse(Edit)