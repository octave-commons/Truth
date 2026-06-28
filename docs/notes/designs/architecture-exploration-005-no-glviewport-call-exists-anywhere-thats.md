● ToolUse(Read)
⎿ 269	   contraction). Its frozen-in magnetic field amplifies as B ∝ ρ^(2/3).
  270	
  271	   Diffuse gas does NOT collapse in place here — it assembles by N-body gravity
  272	   and accretion (collisions). Only the resolved star-forming core contracts."
  273	  [{:keys [phase0/collapse-fraction] :or {collapse-fraction 0.5} :as world}]
  274	  (reduce
  275	   (fn [w eid]
  276	     (let [region (entity->region world eid)
  277	           {:keys [mass radius matter-state]} region]
  278	        (if (and (= :protostar matter-state) radius mass)
  279	          (let [L           (or (ecs/get-component world eid c/angular-momentum) [0.0 0.0 0.0])
  280	                o           (or (ecs/get-component world eid c/oblateness) 1.0)
  281	                floor       (law/main-sequence-radius mass)
  282	                shape       (oblate-collapse-shape mass L radius o collapse-fraction floor)
  283	                a'          (:equatorial-radius shape)
  284	                c'          (:polar-radius shape)
  285	                new-density (oblate-density mass a' c')
  286	                r-eq        (equivalent-radius a' c')
  287	                new-temp    (virial-temperature mass r-eq)
  288	                new-press   (self-gravity-pressure mass r-eq)
  289	                new-spin    (:spin shape)
  290	                new-axis    (:rotation-axis shape)
  291	                anisotropy  (- 1.0 (:oblateness shape))
  292	                new-b       (when-let [b (:b-field region)]
  293	                              (em/flux-freeze b (:density region) new-density anisotropy))]
  294	            (cond-> w
  295	              true  (ecs/put-component eid c/radius         a')
  296	              true  (ecs/put-component eid c/density        new-density)
  297	              true  (ecs/put-component eid c/temperature    new-temp)
  298	              true  (ecs/put-component eid c/pressure       new-press)
  299	              true  (ecs/put-component eid c/spin           new-spin)
  300	              true  (ecs/put-component eid c/oblateness     (:oblateness shape))
  301	              true  (ecs/put-component eid c/rotation-axis  new-axis)
  302	              new-b (ecs/put-component eid c/b-field        new-b)))
  303	          w)))
  304	   world
  305	   (ecs/entities-with world c/matter-state c/temperature c/density c/radius c/mass)))
  306	
  307	(defn classify-system
  308	  "Set each clump's matter-state from the mass it has accreted from the cloud
  ──── (30 lines hidden) ──────────────────────────────────────────────
  339	(defn fusion-system
  340	  "Any body whose temperature, pressure, and composition cross the ignition
  341	   thresholds becomes a star: it emits light and stops contracting."
  342	  [world]
  343	  (reduce
  344	   (fn [w eid]
  345	     (let [region (entity->region world eid)]
  346	       (if (and (not= :star (:matter-state region))
  347	                (law/fusion-possible? region))
  348	         (let [lum (star-luminosity region)]
  349	           (-> w
  350	               (ecs/put-component eid c/luminosity   lum)
  351	               (ecs/put-component eid c/matter-state :star)))
  352	         w)))
  353	   world
  354	   (ecs/entities-with world c/matter-state c/temperature c/pressure c/composition)))
  355	
  356	;; --- Radiation from stars ---------------------------------------------------
  357	
  358	(defn irradiance-at
● No `glViewport` call exists anywhere — that's the bottom-left-quadrant bug: the framebuffer is larger than the assumed 1280×720 (HiDPI/window resize), but we never set the viewport or update it on resize, so GL keeps drawing into the original 1280×720 region. Let me read the physics/law files and the render-scene head before fixing.
● ToolUse(Read)
⎿ 1	(ns law.stellar
  2	  "Contracts and schemas for stellar nebula, star formation, and planetary bodies.
  3	   Defines the physical laws governing Phase 0 of Gates of Truth."
  4	  (:require
  5	   [law.contract :as contract]))
  6	
  7	;; Physical constants
  8	(def ^:const G 6.674e-11) ;; Gravitational constant m³/kg·s²
  9	(def ^:const k-B 1.380649e-23) ;; Boltzmann constant J/K
  10	(def ^:const m-H 1.6735e-27) ;; Hydrogen mass kg
  11	(def ^:const stefan-boltzmann 5.670374419e-8) ;; Stefan-Boltzmann constant W/m²·K⁴
  12	(def ^:const fusion-temp-threshold 1e7) ;; Fusion ignition temperature K (hydrogen burning)
  13	(def ^:const fusion-pressure-threshold 1e12) ;; Fusion ignition pressure Pa (stellar-core scale)
  14	(def ^:const rounding-mass-threshold 3e20) ;; kg — above this self-gravity pulls a body into hydrostatic roundness
  15	(def ^:const solar-mass 1.989e30) ;; kg
  16	(def ^:const solar-radius 6.957e8) ;; m
  17	
  18	(defn main-sequence-radius
  19	  "Approximate zero-age main-sequence radius (m) for a star of `mass`, from the
  20	   broken power law R/R_sun ≈ (M/M_sun)^0.8 below a solar mass and ^0.57 above.
  21	   This is the FLOOR a contracting protostar settles to: a star is small and
  22	   dense, but it is NOT a point. Without a floor the toy collapse halves the
  23	   radius every tick to ~1e9 m, which collapses the accretion cross-section and
  24	   produces the jarring 'pinpoint star' the cloud streams straight through."
  25	  [mass]
  26	  (let [m (/ (double (or mass solar-mass)) solar-mass)
  27	        m (max m 1e-3)]
  28	    (* solar-radius (Math/pow m (if (< m 1.0) 0.8 0.57)))))
  29	
  30	;; --- Accretion mass hierarchy (Phase 0 emergent formation) ---
  31	;; A clump's matter-state follows the mass it has accreted from the gas cloud.
  32	;; These are the toy-scale boundaries between diffuse gas, a planetesimal/debris
  33	;; clump, a planet-scale body, and a star-forming core. They are RELATIVE tiers
  34	;; for a few-solar-mass cloud, not literal Earth/Sun masses.
  35	(def ^:const debris-mass-threshold 1.2e28) ;; kg — gas → planetesimal/debris
  36	(def ^:const planet-mass-threshold 6e28)   ;; kg — debris → planet-scale
  37	(def ^:const star-mass-threshold   1.0e30) ;; kg — planet → star-forming core (dominant)
  38	
  39	(defn mass-class
  40	  "Classify an accreted clump's matter-state purely from its mass. A clump that
  ──── (126 lines hidden) ──────────────────────────────────────────────
  167	    :name     "Matter State"
  168	    :description "Physical state of matter from nebula to planet"}))
  169	
  170	(def nebula-cloud-contract
  171	  (contract/->contract
  172	   {:id       ::nebula-cloud
  173	    :shape-id ::nebular-region
  174	    :kind     :type
  175	    :schema   nebula-cloud-schema
  176	    :name     "Nebula Cloud"
  177	    :description "Statistical representation of nebular gas cloud"}))
  178	
  179	(def stellar-system-contract
  180	  (contract/->contract
  181	   {:id       ::stellar-system
  182	    :shape-id ::star-system
  183	    :kind     :type
  184	    :schema   stellar-system-schema
  185	    :name     "Stellar System"
  186	    :description "Complete star system in formation"}))
● Let me read the accretion/collision system and empirically measure the current time-to-ignition so I can tune against real numbers.
● ToolUse(Read)
⎿ 1	(ns domain.physics.collision
  2	  "Broad-phase bounding-sphere collision detection system.
  3	   Emits :event/collision events — does NOT mutate state directly.
  4	   Response is handled by registered event handlers.
  5	
  6	   Detection: two entities collide when
  7	     dist(posA, posB) <= radiusA + radiusB
  8	
  9	   Broad phase is a uniform spatial hash: bodies are bucketed into a grid whose
  10	   cell side is twice the largest body radius, so any overlapping pair lands in
  11	   the same or an adjacent cell. This is ~O(n) for a well-distributed cloud
  12	   (thousands of accreting gas particles), versus the naive O(n²) all-pairs scan."
  13	  (:require
  14	    [domain.ecs.core       :as ecs]
  15	    [domain.ecs.components :as c]
  16	    [domain.ecs.event      :as event]
  17	    [shape.spatial         :as sp]))
  18	
  19	(defn- collidable-bodies
  20	  "Project world into vec of [eid position radius velocity] for all entities
  21	   that have position, radius, mass, and velocity components.
  22	
  23	   For a star-forming body the effective collision radius is its gravitational
  24	   feeding zone (`c/accretion-radius`) rather than its contracted photosphere,
  25	   so it keeps sweeping up gas instead of becoming a pinpoint sink."
  26	  [world]
  27	  (->> (ecs/all-of world c/position c/radius c/mass c/velocity)
  28	       (mapv (fn [[eid comps]]
  29	               [eid (comps c/position)
  30	                (double (or (comps c/accretion-radius) (comps c/radius)))
  31	                (comps c/velocity)]))))
  32	
  33	(defn- cell-of
  34	  [^double cell-size [x y z]]
  35	  [(long (Math/floor (/ (double x) cell-size)))
  36	   (long (Math/floor (/ (double y) cell-size)))
  37	   (long (Math/floor (/ (double z) cell-size)))])
  38	
  39	(def ^:private neighbor-offsets
  40	  (vec (for [dx [-1 0 1] dy [-1 0 1] dz [-1 0 1]] [dx dy dz])))
  ──── (71 lines hidden) ──────────────────────────────────────────────
  112	        tick   (:tick world)
  113	        pairs  (detect-pairs bodies dt)]
  114	    (reduce (fn [w {:keys [eid-a eid-b pos-a pos-b
  115	                            rad-a rad-b depth normal]}]
  116	              (event/dispatch w
  117	                (event/->event
  118	                  {:tick     tick
  119	                   :kind     :event/collision
  120	                   :entities #{eid-a eid-b}
  121	                   :payload  {:eid-a  eid-a
  122	                              :eid-b  eid-b
  123	                              :pos-a  pos-a
  124	                              :pos-b  pos-b
  125	                              :rad-a  rad-a
  126	                              :rad-b  rad-b
  127	                              :depth  depth
  128	                              :normal normal}})))
  129	            world
  130	            pairs)))
  131	
● Let me empirically measure the current formation timeline (sim-time to protostar and to ignition) while I work on the concrete fixes:
● ToolUse(Bash)
⎿ IGNITION at tick 114  sim-time 3.156e+06 yr  (protostar first seen ~3.105e+06 yr)  resolved=23 dt=2.0e+08
● Key finding: **ignition happens at sim-time ≈ 3.16 Myr** (protostar at ~3.1 Myr, so the contraction-to-fusion "ignition event" is only ~50 kyr). You want ~1 Gyr for the phase and ~50 Myr for ignition — so the physics is resolving ~300× too fast overall, and the contraction ~1000× too fast. That's governed by the cloud's free-fall time (set by its density) and the contraction rate. Let me read those.
● ToolUse(Read)
⎿ 107	(defn oblate-collapse-shape
  108	  "Given a clump's mass, conserved angular momentum L, current equatorial
  109	   radius `a`, current oblateness `o`, and collapse fraction, return the new
  110	   shape as {:equatorial-radius :polar-radius :oblateness :spin :rotation-axis}.
  111	
  112	   Mass is conserved by shrinking the equivalent spherical radius
  113	   r_eq = (a² c)^(1/3) by (1 - collapse-fraction), then solving for the new
  114	   equatorial radius and oblateness self-consistently: spin depends on a,
  115	   oblateness depends on spin, and a depends on oblateness via fixed volume.
  116	
  117	   `floor` is a hard lower bound on the equivalent radius (the main-sequence
  118	   radius for a star-forming core); the body contracts toward it and stops,
  119	   instead of halving to a point every tick."
  120	  ([mass L a o collapse-fraction] (oblate-collapse-shape mass L a o collapse-fraction 0.0))
  121	  ([mass L a o collapse-fraction floor]
  122	  (let [c       (if (pos? (double o)) (* a o) a)
  123	        r-eq    (equivalent-radius a c)
  124	        r-eq'   (max (double floor) (* r-eq (- 1.0 collapse-fraction)))
  125	        axis    (rotation-axis L)
  126	        ;; iterative self-consistent solve for a' and o'
  127	        [a' o' spin'] (loop [o-i (max 0.05 (min 1.0 (double o))) n 0]
  128	                        (let [a-i    (/ r-eq' (Math/pow o-i (/ 1.0 3.0)))
  129	                              spin-i (spin-from-angular-momentum-oblate L mass a-i)
  130	                              o-next (max 0.05 (min 1.0 (oblateness-from-spin spin-i a-i)))]
  131	                          (if (or (>= n 4) (< (Math/abs (- o-next o-i)) 1e-6))
  132	                            [a-i o-next spin-i]
  133	                            (recur o-next (inc n)))))]
  134	    {:equatorial-radius a'
  135	     :polar-radius      (* a' o')
  136	     :oblateness        o'
  137	     :spin              spin'
  138	     :rotation-axis     axis})))
  139	
  140	(defn gravitational-collapse-rate
  141	  "Collapse rate for a region based on the Jeans instability. Returns 1/s if the
  142	   region is larger than its Jeans length (unstable), else 0."
  143	  [{:keys [density temperature radius]}]
  144	  (let [sound-speed   (Math/sqrt (/ (* law/k-B temperature) law/m-H))
  145	        jeans-length  (* sound-speed (Math/sqrt (/ Math/PI (* law/G density))))
  146	        collapse-time (Math/sqrt (/ (* 3 Math/PI) (* 32 law/G density)))]
  ──── (15 lines hidden) ──────────────────────────────────────────────
  162	  "Characteristic temperature of a self-gravitating gas sphere from the virial
  163	   theorem: T ≈ G M m_H / (k_B R). As a collapsing core contracts (R shrinks),
  164	   this rises — it is what carries a protostar toward ignition."
  165	  [mass radius]
  166	  (/ (* law/G mass law/m-H) (* law/k-B radius)))
  167	
  168	(defn self-gravity-pressure
  169	  "Central pressure of a self-gravitating uniform sphere: P ≈ G M² / ((4/3π) R⁴).
  170	   Rises steeply as the core contracts."
  171	  [mass radius]
  172	  (/ (* law/G mass mass) (* (/ 4.0 3.0) Math/PI (Math/pow radius 4))))
  173	
  174	(defn radiative-cooling-delta
  175	  "Temperature drop (K) over dt from radiating as a grey body, with a crude
  176	   optical-depth correction so dense regions cool slowly. The drop is clamped
  177	   to an exponential decay toward the CMB floor so the large dynamical timestep
  178	   does not instantly freeze small bodies."
  179	  [{:keys [temperature radius density]} dt]
  180	  (let [surface-area  (* 4.0 Math/PI radius radius)
  181	        optical-depth (* density radius 1e-20)
❯ 
● I now understand the contraction flaw: `collapse-system` shrinks the protostar by a fixed *fraction per tick* (`collapse-fraction 0.5`) regardless of `dt`, so it reaches the main-sequence floor and ignites in a handful of ticks (~50 kyr) — that's why the ignition event is ~1000× too fast. And the cloud's free-fall time (set by density) makes the whole collapse ~3 Myr.
  
  This is a large, coupled tuning problem. Let me check the render scale and viewport insertion point, then execute in priority order.
● ToolUse(Bash)
⎿ === view scale ===
  569:(def ^:const phase0-view-scale
  692:    (let [bodies (bodies->render world phase0-view-scale)
  710:    (let [bodies (bodies->render world phase0-view-scale)
  1073:  ([world] (phase0-bodies-from-world world phase0-view-scale))
  1149:(defn render-scene
  === render-scene head ===
                ;; size IS its luminosity. Render a small bright core sized by
                ;; log-luminosity, wrapped in a volumetric corona + field line.
                (let [lum    (double (or (ecs/get-component world eid c/luminosity) 1.0e26))
                      core-r (-> (+ 0.6 (* 0.28 (Math/log10 (/ (max 1.0 lum) 1.0e26))))
                                 (max 0.6) (min 3.0))
                      body   {:entity      eid
                              :position    center
                              :radius      core-r
                              :color       [1.0 0.93 0.82]
                              :kind        state
                              :oblateness  ob
                              :rotation-axis axis
                              :render-mode :body}]
                  (concat
                   [body]
                   (nebula-fog {:center center :extent (* core-r 3.0)
                                :color [0.85 0.80 0.55] :count 70 :seed eid})
                   (field-line center core-r (ecs/get-component world eid c/b-field))))
  
                :protostar
                ;; A contracting core: render radius follows the physical radius
● ToolUse(Read)
⎿ 567	;; ---------------------------------------------------------------------------
  568	
  569	(def ^:const phase0-view-scale
  570	  "World metres per render unit for the Phase 0 view."
  571	  1.0e15)
  572	
  573	(def ^:private camera-modes [:manual :track-largest-cluster :fit-all])
  574	
● Now I have the full picture. This is a large coupled problem; let me execute in priority order — concrete fixes first, then principled physics changes I'll measure offline. Let me read the exact `render-scene` opening to insert the viewport call:
● ToolUse(Read)
⎿ 1149	(defn render-scene
  1150	  "Render a frame with volumetric fog particles and glowing 3D massive bodies.
  1151	   `bodies` is a sequence of render maps; `:render-mode` may be `:particle`
  1152	   (soft fog puff) or `:body` (shaded sphere). Default is `:body`."
  1153	  [{:keys [body-program particle-program line-program hud-program hud hud-text]} mesh-world camera width height bodies time]
  1154	  (GL11/glEnable GL11/GL_DEPTH_TEST)
  1155	  (GL11/glClearColor 0.02 0.02 0.05 1.0)
  1156	  (GL11/glClear (bit-or GL11/GL_COLOR_BUFFER_BIT GL11/GL_DEPTH_BUFFER_BIT))
  1157	  (let [proj (perspective 60.0 (/ width (float height)) 0.1 10000.0)
  1158	        view (look-at (:position camera) (:target camera) (sp/vec3 0.0 1.0 0.0))
  1159	        particles (filterv #(= :particle (:render-mode %)) bodies)
  1160	        lines     (filterv #(= :line (:render-mode %)) bodies)
  1161	        bodies    (remove #(#{:particle :line} (:render-mode %)) bodies)]
  1162	    ;; ---- pass 1: volumetric fog particles (additive, soft depth) ----
