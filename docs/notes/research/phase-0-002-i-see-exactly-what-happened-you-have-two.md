● ToolUse(Read)
⎿ 1	(ns domain.ecs.components
  2	  "Canonical component type keywords for Gates of Truth.
  3	   No logic here — just the vocabulary.
  4	   Every system queries these exact keywords.")
  5	
  6	;; --- Spatial ----------------------------------------------------------------
  7	(def position  :component/position)
  8	(def velocity  :component/velocity)
  9	(def mass      :component/mass)
  10	(def radius    :component/radius)
  11	
  12	;; --- Orbital ----------------------------------------------------------------
  13	(def elements  :component/elements)
  14	(def orbit-ref :component/orbit-ref)
  15	
  16	;; --- Physical ---------------------------------------------------------------
  17	(def force-accum :component/force-accum)
  18	(def body-kind   :component/body-kind)
  19	
  20	;; --- Atmosphere -------------------------------------------------------------
  21	(def atmos-cell  :component/atmos-cell)
  22	
  23	;; --- Biome ------------------------------------------------------------------
  24	(def biome-cell  :component/biome-cell)
  25	
  26	;; --- Civilization -----------------------------------------------------------
  27	(def civilization :component/civilization)
  28	(def territory    :component/territory)
  29	
  30	;; --- Render -----------------------------------------------------------------
  31	(def renderable   :component/renderable)
  32	(def cell-id      :component/cell-id)
  33	
  34	;; --- Myth engine ------------------------------------------------------------
  35	(def facet-vector :component/facet-vector)
  36	(def favor        :component/favor)
  37	(def scribe       :component/scribe)
  38	
● ToolUse(Bash)
⎿ src/domain/ecs/components.clj
  src/domain/ecs/core.clj
  src/domain/ecs/dsl.clj
  src/domain/ecs/event.clj
  src/domain/ecs/ledger.clj
  src/domain/ecs/rewindable.clj
  src/domain/ecs/timeline.clj
  src/domain/gravity/barnes_hut.clj
  src/domain/orbital/integrator.clj
  src/domain/orbital/kepler.clj
  src/domain/orbital/system.clj
  src/domain/physics/collision.clj
  src/domain/physics/collision_response.clj
  src/domain/world_bootstrap.clj
  src/infra/dev/server.clj
  src/infra/dev/window.clj
  src/infra/main.clj
  src/infra/render.clj
  src/law/contract.clj
  src/law/ecs_dsl.clj
  src/law/ledger.clj
  src/law/registry.clj
❯ 
● I see exactly what happened. You have **two parallel simulations that don't share a world model**, and `infra/main.clj` is currently straddling both. Here's the split:
  
  ## Path B — the ECS engine substrate (committed, the baseline)
  This is what the bootstrap + LWJGL-pivot commits built. It's data-oriented and generic:
  
  - `domain/ecs/*` — world, `components.clj` (a body = entity id → `:component/position`, `:mass`, `:velocity`…), a query DSL, an **event ledger**, and a **rewindable timeline**
  - `domain/gravity/barnes_hut.clj` — Barnes-Hut N-body
  - `domain/orbital/*` — Kepler elements, integrator, orbital system
  - `domain/physics/*` — collision detection + elastic/inelastic-merge response
  - `domain/world_bootstrap.clj` — wires systems into a tickable, rewindable world
  - `law/contract.clj · ecs_dsl.clj · ledger.clj · registry.clj`, plus `infra/render.clj` + `infra/dev/*`
  
  This is the "one substrate, same physics functions at every scale" that your design doc keeps insisting on — **but it has no nebula, no coherence, no narrator.** It only runs a 3-body Sun/Earth/Moon demo rendered to a PNG.
  
  ## Path A — the Phase 0 narrative sim (new, all untracked)
  This is everything you just added this session, and it directly realizes the *experience* in the design doc:
  
  - `domain/phase0.clj` — a `Phase0World` **defrecord** orchestrator: coherence, time-compression-by-complexity, threshold-event detection, narrator discovery, soft endings (success/fadeout/dispersal/sterile)
  - `domain/stellar.clj` — Jeans collapse, compression heating, fusion ignition, accretion
  - `domain/chemistry.clj`, `domain/player.clj` (the spark/coherence), `law/stellar.clj`, `infra/render/phase0_renderer.clj`, `test/domain/phase0_test.clj`
  
  **But it reinvents state and ticking.** A body here is a plain map inside a `:bodies` vector inside a defrecord, ticked by its own `phase0/tick-world` loop. It reuses *only* `barnes-hut` from Path B and ignores the ECS world, the event ledger, the timeline/rewind, the collision system, and the orbital integrators entirely.
  
  ## The actual conflict
  There are two incompatible answers to "what is a body and how does a tick happen":
  
  | | Path B (ECS) | Path A (Phase 0) |
  |---|---|---|
  | Body | entity id + components | map in a `:bodies` vector |
  | World | `ecs/empty-world` | `Phase0World` defrecord |
  | Tick | composed systems + timeline | hand-rolled `tick-world` |
  | Physics reuse | full | only barnes-hut |
  | Rewind/event ledger | yes | no |
  
  `infra/main.clj` even shows the fork: default arg → Path B demo; `"phase0"` arg → Path A console sim.
  
  ## My recommendation
  Your own design doc settles this: *"the voxel representation doesn't change between phases… the same functions operate on different magnitudes."* That's an argument for **one substrate (Path B), with Phase 0 as a content/config layer on top of it** — not a second engine.
  
  Concretely that means: keep Path A's *genuinely new and good* pieces — `law/stellar.clj` constants/thresholds, `chemistry.clj`, and the Phase-0 *concepts* (coherence, complexity→time-scale, threshold events, narrator, habitability, soft endings) — and re-express them as **ECS components + systems** running on the existing world, ledger, and timeline. Then delete the `Phase0World` defrecord, the body-as-map model, and the hand-rolled tick loop.
  
  Before I touch anything, one fork is yours to call:
● ToolUse(AskUserQuestion)
⎿ Your questions have been answered: "How do you want to reconcile the two simulation paths?"="Converge on ECS (Recommended)". You can now continue with these answers in mind.
● Converge on ECS it is. Let me read the ECS API surface and the remaining Path A pieces so I can plan a faithful port.
● ToolUse(Read)
⎿ 1	(ns domain.ecs.core
  2	  "Entity-Component store.
  3	   Pure data — no atoms, no IO, no side effects.
  4	
  5	   Storage layout:
  6	     :components  {component-key {entity-id value}}
  7	     :archetypes  {entity-id     #{component-key ...}}
  8	     :alive       #{entity-id}
  9	     :next-id     long")
  10	
  11	;; ---- World -----------------------------------------------------------------
  12	
  13	(defn empty-world
  14	  "Return a fresh empty world map."
  15	  []
  16	  {:components {}
  17	   :archetypes {}
  18	   :alive      #{}
  19	   :next-id    0
  20	   :tick       0})
  21	
  22	;; ---- Entity lifecycle ------------------------------------------------------
  23	
  24	(defn spawn
  25	  "Allocate a new entity. Returns [world' entity-id]."
  26	  [world]
  27	  (let [eid (:next-id world)]
  28	    [(-> world
  29	         (update :next-id inc)
  30	         (update :alive conj eid)
  31	         (assoc-in [:archetypes eid] #{}))
  32	     eid]))
  33	
  34	(defn alive?
  35	  "True if entity exists and has not been despawned."
  36	  [world eid]
  37	  (contains? (:alive world) eid))
  38	
  39	(defn despawn
  40	  "Remove all components and mark entity dead."
  ──── (98 lines hidden) ──────────────────────────────────────────────
  139	  "Run a single system (fn [world] world') over the world."
  140	  [world system-fn]
  141	  (system-fn world))
  142	
  143	(defn run-systems
  144	  "Run a seq of systems in order over the world."
  145	  [world system-fns]
  146	  (reduce run-system world system-fns))
  147	
  148	(defn advance-tick
  149	  "Increment the world's logical tick counter."
  150	  [world]
  151	  (update world :tick inc))
  152	
  153	(defn tick
  154	  "Advance the world by one tick: run all systems, increment :tick."
  155	  [world system-fns]
  156	  (-> (run-systems world system-fns)
  157	      (advance-tick)))
  158	
● ToolUse(Read)
⎿ 1	(ns domain.ecs.event
  2	  "Minimal event dispatch and handler registry.
  3	   All state lives in the world map. No global atoms.
  4	   Events are discrete, meaningful occurrences — not continuous state updates.
  5	   Position changes are NOT events. Collisions, deaths, interactions are.
  6	
  7	   Ledger is an append-only vector on the world at :ledger.
  8	   Event handlers are pure (fn [world event] world') registered at :handlers.
  9	   Rewind handlers are registered at :rewind-handlers.")
  10	
  11	(defrecord Event
  12	  [id       ;; UUID — unique per event
  13	   tick     ;; long — tick on which this occurred
  14	   kind     ;; keyword — e.g. :event/collision :event/death :event/trade
  15	   entities ;; #{entity-id} — all entities involved
  16	   payload  ;; map — event-specific data
  17	   cause])  ;; nil | event-id — causal chain
  18	
  19	(defn new-event-id [] (java.util.UUID/randomUUID))
  20	
  21	(defn ->event
  22	  "Construct a raw event map. Required keys: :tick :kind :entities.
  23	   Optional: :payload :cause :id"
  24	  [{:keys [tick kind entities payload cause id] :as m}]
  25	  (when-not (and tick kind (set? entities))
  26	    (throw (ex-info "Event requires :tick, :kind, :entities (set)"
  27	                    {:kind ::invalid-event :data m})))
  28	  (->Event (or id (new-event-id))
  29	           tick
  30	           kind
  31	           entities
  32	           (or payload {})
  33	           cause))
  34	
  35	(defn with-ledger
  36	  "Add an empty ledger to a world map."
  37	  [world]
  38	  (assoc world
  39	    :ledger   {:events [] :cursor 0}
  40	    :handlers {}
  ──── (43 lines hidden) ──────────────────────────────────────────────
  84	        handler  (get-in world' [:handlers (:kind event)])]
  85	    (if handler
  86	      (handler world' event)
  87	      world')))
  88	
  89	(defn dispatch-all
  90	  "Dispatch a seq of events in order."
  91	  [world events]
  92	  (reduce dispatch world events))
  93	
  94	(defn events-since
  95	  "All raw events in the world ledger at or after tick t."
  96	  [world t]
  97	  (filter #(>= (:tick %) (long t)) (get-in world [:ledger :events])))
  98	
  99	(defn events-of-kind
  100	  "All raw events in the world ledger of a given kind."
  101	  [world kind]
  102	  (filter #(= (:kind %) kind) (get-in world [:ledger :events])))
  103	
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
  12	(def ^:const fusion-temp-threshold 1e7) ;; Fusion ignition temperature K
  13	(def ^:const fusion-pressure-threshold 1e25) ;; Fusion ignition pressure Pa
  14	
  15	;; --- Matter States ---
  16	
  17	(def matter-state-schema
  18	  "Schema for matter in various states from nebula to planet"
  19	  {:id          uuid?
  20	   :position    vector? ;; [x y z]
  21	   :velocity    vector? ;; [vx vy vz]
  22	   :mass        pos?
  23	   :radius      pos?
  24	   :temperature pos?
  25	   :density     pos?
  26	   :composition map? ;; {:H 0.75 :He 0.24 :metals 0.01}
  27	   :state       keyword? ;; :nebula :protostar :star :planet :debris
  28	   :luminosity  number?
  29	   :pressure    number?})
  30	
  31	(def nebula-cloud-schema
  32	  "Statistical representation of unfocused nebular region"
  33	  {:id          uuid?
  34	   :center      vector?
  35	   :extent      pos? ;; radius of cloud
  36	   :total-mass  pos?
  37	   :temperature pos?
  38	   :density     pos?
  39	   :composition map?
  40	   :angular-momentum vector?
  ──── (81 lines hidden) ──────────────────────────────────────────────
  122	  [{:keys [age central-star bodies nebula]}]
  123	  (cond
  124	    (and nebula (nil? central-star) (< age 1e12))
  125	    :phase-0/nebula-collapse
  126	    
  127	    (and central-star (not (fusion-possible? central-star)))
  128	    :phase-0/protostar
  129	    
  130	    (and central-star (fusion-possible? central-star) (< (count bodies) 3))
  131	    :phase-0/ignition
  132	    
  133	    (and central-star (>= (count bodies) 3))
  134	    :phase-0/accretion
  135	    
  136	    (and central-star 
  137	         (> (count (filter #(planet? % bodies) bodies)) 0))
  138	    :phase-0/planets-formed
  139	    
  140	    :else
  141	    :phase-0/dispersed))
