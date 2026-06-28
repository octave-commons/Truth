❯ 
● ToolUse(Read)
⎿ 257	            (ecs/put-component big c/composition comp)
  258	            (ecs/put-component big c/temperature temp')
  259	            (ecs/put-component big c/density     dens')
  260	            (ecs/put-component big c/pressure    (ideal-gas-pressure dens' temp'))
  261	            (ecs/despawn small)))
  262	      world)))
  263	
  264	;; --- Nebula seeding ---------------------------------------------------------
  265	
  266	(def default-composition
  267	  "Primordial nebular composition by mass fraction (H/He dominated)."
  268	  {:H 0.75 :He 0.24 :metals 0.01})
● ToolUse(Edit)
⎿ The file /home/err/spaces/Truth/src/domain/stellar.clj has been updated successfully. (file state is current in your context — no need to Read it back)
● ToolUse(Edit)
⎿ The file /home/err/spaces/Truth/src/domain/stellar.clj has been updated successfully. (file state is current in your context — no need to Read it back)
● Now mark the parallelism tasks done and rework `domain.phase0` — the emergent seeder and pipeline. Let me read the current seeding block to replace it exactly:
● ToolUse(TaskUpdate)
⎿ Updated task #6 status
● ToolUse(TaskUpdate)
⎿ Updated task #7 status
● ToolUse(TaskUpdate)
⎿ Updated task #8 status
● ToolUse(Read)
⎿ 22	   [shape.spatial           :as sp]))
  23	
  24	;; --- Nebula seeding ---------------------------------------------------------
  25	
  26	(defn- circular-speed
  27	  "Keplerian circular speed about a central mass at radius r."
  28	  [central-mass r]
  29	  (Math/sqrt (/ (* law/G central-mass) (max r 1.0))))
  30	
  31	(defn- gas-particle-spec
  32	  "Component spec for one diffuse gas particle, on a slightly sub-Keplerian,
  33	   turbulent, flattened-disk orbit about the core. Mass is below the rounding
  34	   threshold so it stays nebular gas (fog) and is never classified a planet; it
  35	   orbits, accretes, and renders — the lush body of the nebula."
  36	  [^java.util.Random rng core-mass r-out flatten]
  37	  (let [u     (.nextDouble rng)
  38	        r     (* r-out (Math/sqrt (+ 0.03 (* 0.97 u)))) ; avoid a hole at centre
  39	        ang   (* 2.0 Math/PI (.nextDouble rng))
  40	        x     (* r (Math/cos ang))
  41	        y     (* r (Math/sin ang))
  42	        z     (* r flatten 2.0 (- (.nextDouble rng) 0.5))
  43	        vc    (circular-speed core-mass r)
  44	        sub   0.92                              ; sub-Keplerian → slow inspiral
  45	        jit   (fn [] (* vc 0.06 2.0 (- (.nextDouble rng) 0.5)))]
  46	    {:position    (sp/vec3 x y z)
  47	     :velocity    (sp/vec3 (+ (* (- vc) (Math/sin ang) sub) (jit))
  48	                           (+ (* vc (Math/cos ang) sub) (jit))
  49	                           (jit))
  50	     :mass        1.0e20      ; < law/rounding-mass-threshold (stays gas)
  51	     :radius      8.0e12
  52	     :temperature 12.0
  53	     :body-kind   :body/gas
  54	     :composition {:H 0.74 :He 0.24 :metals 0.02}}))
  55	
  56	(defn seed-nebula
  57	  "Seed a forming system on the single ECS world: one dense central core that
  58	   collapses to a star, a few stable planet-seed clumps on inclined orbits, and
  59	   a rotating, turbulent disk of `gas-count` diffuse gas particles. All are ECS
  60	   entities. Deterministic (seeded RNG) so runs and tests reproduce."
  61	  ([world total-mass extent] (seed-nebula world total-mass extent {}))
  ──── (40 lines hidden) ──────────────────────────────────────────────
  102	;; --- World construction -----------------------------------------------------
  103	
  104	(defn create-world
  105	  "Bootstrap a Phase 0 world ready to tick."
  106	  ([] (create-world {}))
  107	   ([{:keys [G theta dt nebula-mass nebula-radius collapse-fraction gas-count]
  108	      :or   {G law/G theta 0.5 dt 5e11
  109	             nebula-mass 2e31 nebula-radius 1.5e16 collapse-fraction 0.5
  110	             gas-count 1500}}]
  111	   (let [base   (-> (ecs/empty-world)
  112	                    (event/with-ledger)
  113	                    (event/register-handler :event/collision
  114	                                            stellar/stellar-merge-handler)
  115	                    (assoc :sim/G G :sim/theta theta :sim/dt dt
  116	                           :phase0/sim-time          0.0
  117	                           :phase0/time-scale        (stellar/time-scale-from-complexity 0)
  118	                           :phase0/complexity        0
  119	                           :phase0/phase             :initializing
  120	                           :phase0/active            true
  121	                           :phase0/collapse-fraction collapse-fraction))
● ToolUse(Edit)
⎿ The file /home/err/spaces/Truth/src/domain/phase0.clj has been updated successfully. (file state is current in your context — no need to Read it back)
● Now update `create-world` (sane dt, softening, emergent gas-count) and pass it through:
● ToolUse(Edit)
⎿ The file /home/err/spaces/Truth/src/domain/phase0.clj has been updated successfully. (file state is current in your context — no need to Read it back)
● Now reorder the tick pipeline (move → accrete → classify → collapse → fuse → cool → tag → field) and pass softening:
● ToolUse(Read)
⎿ 100	                           :phase0/phase             :initializing
  101	                           :phase0/active            true
  102	                           :phase0/collapse-fraction collapse-fraction))
  103	         seeded (seed-nebula base nebula-mass nebula-radius {:gas-count gas-count})
  104	         [w _]  (player/spawn-observer seeded (sp/vec3 0 0 (* nebula-radius 2)))]
  105	     w)))
  106	
  107	;; --- Observable summary -----------------------------------------------------
  108	
  109	(defn system-summary
  110	  "Tally the world's resolved matter into the shape used for complexity, phase
  111	   detection, and habitability."
  112	  [world]
  113	  (let [eids    (ecs/entities-with world c/matter-state c/mass)
  114	        regions (mapv #(stellar/entity->region world %) eids)
  115	        stars   (filterv #(= :star (:matter-state %)) regions)
  116	        non-stars (remove #(= :star (:matter-state %)) regions)
  117	        planets (filterv law/hydrostatic-equilibrium? non-stars)]
  118	    {:body-count   (count regions)
  119	     :star?        (boolean (seq stars))
  120	     :fusion?      (boolean (seq stars))
  121	     :planet-count (count planets)
  122	     :stars        stars
  123	     :planets      planets
  124	     :regions      regions}))
  125	
  126	(defn detect-phase
  127	  "Detect the current phase of the forming system from its summary."
  128	  [{:keys [star? planet-count body-count regions]} sim-time]
  129	  (let [nebula?    (some #(= :nebula (:matter-state %)) regions)
  130	        protostar? (some #(= :protostar (:matter-state %)) regions)]
  131	    (cond
  132	      (and star? (pos? planet-count)) :phase-0/planets-formed
  133	      (and star? (>= body-count 3))   :phase-0/accretion
  134	      star?                           :phase-0/ignition
  135	      protostar?                      :phase-0/protostar
  136	      (zero? body-count)              :phase-0/dispersed
  137	      (and nebula? (< sim-time 1e18)) :phase-0/nebula-collapse
  138	      :else                           :phase-0/dispersed)))
  139	
  ──── (2 lines hidden) ──────────────────────────────────────────────
  142	(defn- emit-threshold
  143	  "Emit a threshold event into the ledger at the world's current tick."
  144	  [world kind data]
  145	  (event/dispatch world
  146	    (event/->event {:tick     (:tick world)
  147	                    :kind     kind
  148	                    :entities #{}
  149	                    :payload  {:data data}})))
  150	
  151	(defn physics-systems
  152	  "The ordered physical systems run each tick (everything except the observer,
  153	    which must run after complexity and events are known).
  154	
  155	   The base tick `dt` is multiplied by the world's current `phase0/time-scale`
  156	   so that the integrator advances in simulation seconds, not wall-clock
  157	   seconds.  Without this scaling, the orbital step is too small to see at
  158	   nebular scales while `sim-time` races ahead."
  159	  [{:keys [sim/G sim/theta sim/dt]}]
  160	  (let [effective-dt dt]
  161	    ;; `dt` (`:sim/dt`) is the real integration step in seconds — chosen as a
● ToolUse(Edit)
⎿ The file /home/err/spaces/Truth/src/domain/phase0.clj has been updated successfully. (file state is current in your context — no need to Read it back)
● ToolUse(Read)
⎿ 159	   seconds.  Without this scaling, the orbital step is too small to see at
  160	   nebular scales while `sim-time` races ahead."
  161	  [{:keys [sim/G sim/theta sim/dt]}]
  162	  (let [effective-dt dt]
  163	    ;; `dt` (`:sim/dt`) is the real integration step in seconds — chosen as a
  164	    ;; fraction of the cloud's orbital/free-fall time so the N-body dynamics
  165	    ;; actually resolve (collapse, rotation, accretion). It is NOT multiplied by
  166	    ;; the display `time-scale`: that produced ~1e20 s steps which made orbital
  167	    ;; motion degenerate. `time-scale` now only colours sim-time reporting.
  168	    ;; Tick order (see the coupled-physics design doc): gravity first, then the
  169	    ;; regime classifier tags each clump, then the EM step (non-ideal flux
  170	    ;; evolution), then hydro/collapse (which flux-freezes the field as clumps
  171	    ;; contract and is braked by magnetic support), then fusion, thermal,
  172	    ;; planet classification, and accretion.
  173	    [(orbital/orbital-system G theta effective-dt)
  174	     regime/regime-system
  175	     (em/em-system effective-dt)
  176	     stellar/collapse-system
  177	     stellar/fusion-system
  178	     (stellar/thermal-system effective-dt)
  179	     stellar/classify-system
  180	     collision/collision-detection-system]))
● ToolUse(Edit)
⎿ The file /home/err/spaces/Truth/src/domain/phase0.clj has been updated successfully. (file state is current in your context — no need to Read it back)
● The sim rework is in. Now the temperature coloring + jitter fix in the renderer. Let me read the current projection block:
● ToolUse(Read)
⎿ 432	;; an ignited star, and planets settling out.
  433	
  434	(def ^:const phase0-view-scale
  435	  "World metres per render unit for the Phase 0 view."
  436	  1.0e15)
  437	
  438	(defn- matter-color [state]
  439	  (case state
  440	    :star      [1.0 0.92 0.25]
  441	    :protostar [1.0 0.55 0.15]
  442	    :planet    [0.25 0.5 1.0]
  443	    :nebula    [0.45 0.35 0.65]
  444	    :debris    [0.6 0.6 0.6]
  445	    [0.7 0.7 0.7]))
  446	
  447	(defn- matter-visual-radius [state]
  448	  (case state
  449	    :star 2.0 :protostar 1.5 :planet 1.0 :debris 0.5 :nebula 0.4
  450	    1.0))
  451	
  452	(def ^:private nebula-base-color [0.55 0.45 0.75])
  453	
  454	(def regime-tint
  455	  "Per-regime colour multiplier so hue reads the dominant physics of a clump:
  456	   warm = collapsing under gravity, violet-blue = magnetically dominated,
  457	   neutral = ordinary gas + gravity."
  458	  {:gravitationally-unstable [1.30 0.85 0.65]
  459	   :mhd-dominated            [0.70 0.75 1.35]
  460	   :gravity-hydro            [1.00 1.00 1.00]})
  461	
  462	(defn tint-color
  463	  "Multiply [r g b] by a regime tint, clamped to [0,1]."
  464	  [color regime]
  465	  (let [t (get regime-tint regime [1.0 1.0 1.0])]
  466	    (mapv (fn [c m] (max 0.0 (min 1.0 (* (double c) (double m))))) color t)))
  467	
  468	(defn- player-focus-level
  469	  "Observer attention in 0..1, used to scale the fog sample budget."
  470	  [world]
  471	  (if-let [obs (player/get-observer world)]
  ──── (38 lines hidden) ──────────────────────────────────────────────
  510	
  511	(def ^:private volumetric-states #{:nebula :protostar})
  512	
  513	(defn phase0-bodies-from-world
  514	  "Project Phase 0 ECS matter entities into stylized, view-scaled render shapes:
  515	   regime-tinted volumetric fog plus magnetic field lines for nebular/protostellar
  516	   clumps, and shaded spheres for resolved stars/planets/debris.
  517	
  518	   This is the ONLY Phase 0 render projection. There is a single ECS world model
  519	   behind it — see infra.main and domain.phase0."
  520	  ([world] (phase0-bodies-from-world world phase0-view-scale))
  521	  ([world scale]
  522	   (let [focus      (player-focus-level world)
  523	         dense-mass 1.0e24]   ; core / planet-seed clumps; gas is far lighter
  524	     (vec
  525	      (mapcat
  526	       (fn [eid]
  527	         (let [state   (ecs/get-component world eid c/matter-state)
  528	               [x y z] (ecs/get-component world eid c/position)
  529	               center  [(/ x scale) (/ y scale) (/ z scale)]]
❯ 
● ToolUse(Read)
⎿ 529	               center  [(/ x scale) (/ y scale) (/ z scale)]]
  530	           (if (volumetric-states state)
  531	             (let [regime (ecs/get-component world eid c/regime)
  532	                   color  (tint-color nebula-base-color regime)
  533	                   mass   (or (ecs/get-component world eid c/mass) 0.0)
  534	                   extent (max 0.4 (/ (or (ecs/get-component world eid c/radius) scale)
  535	                                      scale))]
  536	               (if (or (= state :protostar) (>= (double mass) dense-mass))
  537	                 ;; dense clump: a compact bright cloud + a magnetic field line
  538	                 (concat
  539	                  (nebula-fog {:center center :extent extent :color color
  540	                               :count  (fog-sample-count extent focus)})
  541	                  (field-line center extent (ecs/get-component world eid c/b-field)))
  542	                 ;; diffuse gas particle: a single soft puff (the lush bulk)
  543	                 [{:position center :color color
  544	                   :size (+ 22.0 (* 30.0 (Math/random)))
  545	                   :render-mode :particle}]))
  546	             [{:entity      eid
  547	               :position    center
  548	               :radius      (matter-visual-radius state)
  549	               :color       (matter-color state)
  550	               :kind        state
  551	               :render-mode :body}])))
  552	       (ecs/entities-with world c/position c/matter-state))))))
  553	
  554	(defn render-scene
  555	  "Render a frame with volumetric fog particles and glowing 3D massive bodies.
  556	   `bodies` is a sequence of render maps; `:render-mode` may be `:particle`
  557	   (soft fog puff) or `:body` (shaded sphere). Default is `:body`."
  558	  [{:keys [body-program particle-program line-program]} mesh-world camera width height bodies time]
● Now rewrite the whole render-projection block: temperature coloring, deterministic (non-shimmering) fog, and per-state shapes (gas→puff, protostar→cloud+field line, bodies→spheres):
● ToolUse(Edit)
⎿ The file /home/err/spaces/Truth/src/infra/render.clj has been updated successfully. (file state is current in your context — no need to Read it back)
● ToolUse(Edit)
⎿ The file /home/err/spaces/Truth/src/infra/render.clj has been updated successfully. (file state is current in your context — no need to Read it back)
● Now add a live field readout for insight, wire it into the dev loop, then update my iteration harness to watch emergence:
● ToolUse(Read)
⎿ 196	        time-scale (stellar/time-scale-from-complexity complexity)
  197	        phase      (detect-phase summ (:phase0/sim-time world2))
  198	        world3     (cond-> world2
  199	                     (and (:star? summ) (not (:star? prev)))
  200	                     (emit-threshold :event/stellar-ignition (first (:stars summ)))
  201	
  202	                     (> (:planet-count summ) (:planet-count prev))
  203	                     (emit-threshold :event/planet-formation (first (:planets summ)))
  204	
  205	                     (not= phase prev-phase)
  206	                     (emit-threshold :event/phase-transition {:from prev-phase :to phase}))
  207	        world4     (assoc world3
  208	                     :phase0/complexity complexity
  209	                     :phase0/time-scale time-scale
  210	                     :phase0/phase      phase
  211	                     :phase0/sim-time   (+ (:phase0/sim-time world3) dt))
  212	        world5     ((player/observer-system effective-dt) world4)
  213	        obs        (player/get-observer world5)]
  214	    (assoc world5 :phase0/active
  215	           (and (player/can-interact? obs)
