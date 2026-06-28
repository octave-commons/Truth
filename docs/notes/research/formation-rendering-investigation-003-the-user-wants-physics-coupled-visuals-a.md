● ToolUse(AskUserQuestion)
⎿ Your questions have been answered: "What formation outcome do you want it to look like?"="I want the render sizes coupled to physics. I am not trying to make any out come specifically. I want it to feel real and make sense logically read the @docs/ grok what this is. the planets are going to be voxels. the visuals *MUST* be tied to their composition and physics. ", "How much should I change now?"="Fix all 4 issues". You can now continue with these answers in mind.
● The user wants physics-coupled visuals and points me to the docs. Let me read them before touching anything.
● ToolUse(Bash)
⎿ /home/err/spaces/Truth/docs/designs/gates-of-truth-world-gen-phases.md
  /home/err/spaces/Truth/docs/designs/phase0-volumetric-renderer.md
  /home/err/spaces/Truth/docs/designs/phase0-coupled-physics-and-regime-classifier.md
  /home/err/spaces/Truth/docs/designs/truth-phase-0-stellar-nebula-design.md
  /home/err/spaces/Truth/docs/notes/2026.06.25.16.41.16.md
  /home/err/spaces/Truth/docs/notes/2026.06.25.22.11.59.md
  /home/err/spaces/Truth/docs/notes/2026.06.23.20.01.16.md
  /home/err/spaces/Truth/docs/notes/2026.06.25.22.13.14.md
  /home/err/spaces/Truth/docs/specs/phase0-protoplanetary-disk-implementation.md
● ToolUse(Read)
⎿ 1	# Gates of Truth — Phase 0 Stellar Nebula Slice
  2	
  3	This document commits the implementation design for the first playable slice of **Gates of Truth**: the stellar nebula phase in which the player witnesses a star system form in front of them and learns, mostly without explicit tutorialization, what kind of being they are in this universe. The current repository already defines the four-quadrant architecture (`domain/`, `infra/`, `shape/`, `law/`), the requirement that domain logic stay pure, and the expectation that new systems be specified with law, tests, and then implementation.[cite:6]
  4	
  5	## Purpose
  6	
  7	Phase 0 must achieve five things at once:
  8	
  9	1. Show a universe-forming process directly and convincingly.
  10	2. Establish the underlying physics substrate that later phases continue rather than replace.
  11	3. Introduce the player’s mode of agency as limited, subtle, and awe-oriented.
  12	4. Teach the control language of the game without breaking immersion.
  13	5. Produce a star system outcome that can continue toward habitability or gracefully fail into a witnessed conclusion.
  14	
  15	The design target is not a menu-driven setup flow. It is an observable cosmological event sequence that the player can mostly watch, gently influence, and emotionally inhabit.
  16	
  17	## Slice Definition
  18	
  19	### Entry condition
  20	
  21	The slice begins with a nebular mass distribution already present in simulation space. The player appears as a coherent spark-like observer embedded in that field.
  22	
  23	### Exit condition
  24	
  25	The slice ends when one of the following becomes true:
  26	- A stable star forms and the surrounding system resolves into candidate planets and non-planets under the astronomical definition of a planet.
  27	- The nebula fails to produce a viable life-bearing trajectory and the simulation transitions into a witnessed conclusion.
  28	- The player’s attention/coherence can no longer sustain meaningful focus on the region, and the spark releases into another nebular site.
  29	
  30	### Planet criterion
  31	
  32	A body counts as a planet in this phase only when it satisfies the design’s intended astronomical meaning:
  33	- It is in hydrostatic equilibrium.
  34	- It has cleared its orbital neighborhood.
  35	
  36	The player does not need this definition presented as UI text. The system uses it internally to determine which bodies survive phase transition.
  37	
  38	## Experience Goals
  39	
  40	### 1. A universe forms visibly
  ──── (331 lines hidden) ──────────────────────────────────────────────
  372	## Implementation Readiness
  373	
  374	This slice is ready to move into the project’s normal build discipline:
  375	
  376	1. Write `law/` schemas for nebula, star, body, player-spark, focus volume, and slice state.
  377	2. Write failing tests for collapse, heating, ignition, and coherence transitions.
  378	3. Implement the smallest pure `domain/stellar.clj` and `domain/chemistry.clj` needed to satisfy those tests.
  379	4. Build a thin `infra/` renderer that makes the sequence legible and beautiful.
  380	5. Iterate on feel without breaking the law/domain separation.
  381	
  382	## Final framing
  383	
  384	Phase 0 should make the player feel this:
  385	
  386	A universe is not being selected.
  387	A universe is becoming.
  388	The player is present inside that becoming.
  389	They may guide it, but they do not own it.
  390	And whether the first world lives or fails, they are still there to witness the light.
  391	
● ToolUse(Read)
⎿ 1	# Phase 0 Protoplanetary Disc Implementation Spec
  2	
  3	**Status:** draft  
  4	**Goal:** Close the gap between the coupled gravity–hydro–MHD design and the current N-body clump substrate so that nebular collapse produces rotationally supported, magnetically braced discs rather than radial point-mass pile-ups.  
  5	**Principle:** No shortcuts, no hacks. Every new physics term is added as a conserved quantity, a failing test, and only then an implementation. The single ECS substrate is never bypassed.
  6	
  7	---
  8	
  9	## 1. Current state (ground truth from code)
  10	
  11	The codebase implements the following physics on the ECS substrate:
  12	
  13	- Mutual N-body gravity (`domain.orbital.system`).
  14	- Inelastic overlap merging (`domain.stellar/stellar-merge-handler`).
  15	- Scalar thermodynamics: ideal-gas pressure, virial heating, radiative cooling, fusion ignition (`domain.stellar`).
  16	- Per-clump magnetic field with flux-freeze on spherical contraction, magnetic-pressure diagnostics, Alfvén speed, and resistive decay (`domain.em`).
  17	- A four-number regime classifier: plasma β, Mach, Alfvén Mach, Jeans ratio (`domain.regime`).
  18	- Rendering hooks for temperature colour, regime tint, and magnetic field lines (`infra.render`).
  19	
  20	What is **not** implemented, and is required for discs:
  21	
  22	- Angular momentum as an ECS component (it appears only in `law.stellar/nebula-cloud-schema`, line 74, but has no component or system using it).
  23	- Conservation of angular momentum through collapse or merger.
  24	- Rotational support: collapse is purely radial; no oblateness or spin-up.
  25	- Hydrodynamic pressure-gradient acceleration (`−∇p/ρ`).
  26	- Lorentz force on velocities / angular momentum (`(∇×B)×B/μ₀`).
  27	- Toomre Q or any disc-stability criterion.
  28	- Accretion/sink particles; stars form by overlap merge.
  29	- Proper induction equation: `B` is amplified only by flux-freeze during contraction and damped by resistive decay; it is not advected by the velocity field.
  30	- Spatial field operators (`shape.field` does not exist).
  31	- Renderer parity: `infra.render/run-window` calls `orbital/orbital-system` directly and uses `bodies-from-world`, so the live window does not run Phase 0 physics or show fog/field lines.
  32	
  33	The symptom — gas collapses to a central clump without a disc — is the expected result of missing rotational support, missing pressure support, and missing magnetic torque.
  34	
  35	---
  36	
  37	## 2. Physical correctness criteria
  38	
  39	A correct implementation must satisfy these invariants, each of which becomes a test:
  40	
  ──── (242 lines hidden) ──────────────────────────────────────────────
  283	
  284	---
  285	
  286	## 4. What is explicitly out of scope for this spec
  287	
  288	These are correct physics but belong to later phases or other specs:
  289	
  290	- Planet interior convection / core dynamo (`domain.interior`) — depends on `shape.field` and a finished disc.
  291	- Atmosphere hydrostatics and stellar wind (`domain.atmosphere`) — post-disc, post-Phase 0 handoff.
  292	- Non-ideal MHD (ambipolar diffusion, Ohmic dissipation, Hall effect) — add after ideal MHD is tested.
  293	- Radiative transfer / M1 closure for `E_rad` — keep the coarse radiation model until thermal physics is refactored.
  294	
  295	---
  296	
  297	## 5. First deliverable
  298	
  299	The first concrete deliverable is **Phase 1**: angular momentum as an ECS component with conservation laws in mergers and collapse. It unblocks every subsequent phase and has no dependencies on grid operators.
  300	
  301	Next action: approve this spec, then I will write the Malli schemas, failing tests, and implementation for Phase 1.
  302	
● The docs are decisive. The implementation spec already names my exact diagnosis as a known gap, and sets a hard rule: **"No shortcuts, no hacks. Every new physics term is added as a conserved quantity, a failing test, and only then an implementation."** And the design doc confirms planets become voxels and visuals must read physically. Let me read the remaining pieces I need to fix things in that discipline.
● ToolUse(Read)
⎿ 1	(ns domain.em
  2	  "Electromagnetic / MHD-lite layer for Phase 0.
  3	
  4	   The substrate is N-body: each resolved clump carries a single magnetic field
  5	   vector (component `c/b-field`) rather than a grid of cells. So the field
  6	   operations here are the per-body reductions of the full MHD equations:
  7	
  8	     - flux freezing      : ideal induction under spherical compression,
  9	                            B ∝ ρ^(2/3)  (equivalently B ∝ 1/r²).
  10	     - magnetic pressure  : P_B = |B|² / (2μ₀), the support term that opposes
  11	                            gravity in the momentum equation.
  12	     - resistive decay    : the non-ideal η∇²B hook, reduced to dB/dt = -ηB/L².
  13	                            Negligible in diffuse gas, real only in dense cores —
  14	                            which is exactly where non-ideal MHD matters.
  15	     - Lorentz force      : (∇ × B) × B / μ₀, applied to velocities via the
  16	                            orbital integrator (a = f/ρ).
  17	     - magnetic braking   : poloidal field threading a rotating clump exerts a
  18	                            torque that transports angular momentum outward.
  19	
  20	   All formulas are SI (see law.field). Pure data transformation; no IO."
  21	  (:require
  22	   [law.field         :as lf]
  23	   [law.stellar       :as ls]
  24	   [domain.hydro      :as hydro]
  25	   [shape.spatial     :as sp]
  26	   [domain.ecs.core   :as ecs]
  27	   [domain.ecs.parallel :as par]
  28	   [domain.ecs.components :as c]))
  29	
  30	;; --- Pure field physics -----------------------------------------------------
  31	
  32	(defn curl-estimate
  33	  "Estimate (∇ × B) at a clump from neighboring b-field vectors using an SPH-like
  34	   curl formula. Returns a vector in T/m. Zero neighbors → zero curl.
  35	
  36	   Uses the symmetric SPH curl: (∇ × B)_i = Σ_j m_j/ρ_j (B_i - B_j) × ∇_i W_ij."
  37	  [b-field density position neighbors]
  38	  (if (or (not (lf/finite-vec3? b-field))
  39	          (not (pos? (double density))))
  40	    [0.0 0.0 0.0]
  ──── (239 lines hidden) ──────────────────────────────────────────────
  280	                               (if new-L
  281	                                 (-> w'
  282	                                     (ecs/put-component eid c/angular-momentum new-L)
  283	                                     (ecs/put-component eid c/spin new-spin))
  284	                                 w')))
  285	                           world
  286	                           updates1)
  287	          ;; Resistive decay
  288	          updates2 (par/par-mapv
  289	                     (fn [eid]
  290	                       [eid (resistive-decay (ecs/get-component world1 eid c/b-field)
  291	                                             (ecs/get-component world1 eid c/radius)
  292	                                             dt)])
  293	                     eids)]
  294	      (reduce (fn [w [eid b]]
  295	                (if (lf/bounded-b-field? b)
  296	                  (ecs/put-component w eid c/b-field b)
  297	                  w))
  298	              world1
  299	              updates2))))
● ToolUse(Read)
⎿ 1	(ns domain.stellar-test
  2	  "Tests for the stellar nebula/star formation domain helpers and ECS systems.
  3	   These are epistemic contracts: every physical invariant asserted here must
  4	   hold before downstream systems (disc formation, EM, regime) can be trusted."
  5	  (:require
  6	   [clojure.test :refer [deftest testing is]]
  7	   [domain.stellar :as stellar]
  8	   [domain.em      :as em]
  9	   [domain.ecs.core :as ecs]
  10	   [domain.ecs.event :as event]
  11	   [domain.ecs.components :as c]
  12	   [domain.physics.collision :as collision]
  13	   [shape.spatial :as sp]))
  14	
  15	;; --- Angular momentum helpers ------------------------------------------------
  16	
  17	(deftest test-orbital-angular-momentum
  18	  (testing "L = m (r × v) is perpendicular to the orbital plane"
  19	    (let [m 1e30
  20	          r [1e15 0.0 0.0]
  21	          v [0.0 2e3 0.0]
  22	          L (stellar/orbital-angular-momentum m r v)]
  23	      (is (= 0.0 (first L)))
  24	      (is (= 0.0 (second L)))
  25	      (is (pos? (nth L 2)))
  26	      (is (< (Math/abs (- (nth L 2) (* m 1e15 2e3))) 1e20)))))
  27	
  28	(deftest test-moment-of-inertia-sphere
  29	  (testing "I = (2/5) M R² for a uniform sphere"
  30	    (is (< (Math/abs (- (stellar/moment-of-inertia 1.0 1.0) 0.4)) 1e-12))
  31	    (is (< (Math/abs (- (stellar/moment-of-inertia 2e30 1e9) (* 0.4 2e30 1e18))) 1e20))))
  32	
  33	(deftest test-spin-from-angular-momentum
  34	  (testing "ω = L/I about the rotation axis"
  35	    (let [L [0.0 0.0 1e40]
  36	          I (stellar/moment-of-inertia 2e30 1e9)
  37	          w (stellar/spin-from-angular-momentum [0.0 0.0 1e40] 2e30 1e9)]
  38	      (is (= 0.0 (first w)))
  39	      (is (= 0.0 (second w)))
  40	      (is (pos? (nth w 2)))
  ──── (167 lines hidden) ──────────────────────────────────────────────
  208	                                             :matter-state :protostar
  209	                                             :angular-momentum [0.0 0.0 1e45]})
  210	          w2   (stellar/collapse-system w)
  211	          ob   (ecs/get-component w2 eid c/oblateness)]
  212	      (is (some? ob))
  213	      (is (< ob 1.0) "oblateness drops below spherical"))))
  214	
  215	(deftest test-nonrotating-collapse-stays-spherical
  216	  (testing "A non-rotating protostar stays spherical"
  217	    (let [base (ecs/empty-world)
  218	          [w eid] (stellar/spawn-clump base {:position [0.0 0.0 0.0]
  219	                                             :velocity [0.0 0.0 0.0]
  220	                                             :mass 2e30
  221	                                             :radius 1e15
  222	                                             :matter-state :protostar
  223	                                             :angular-momentum [0.0 0.0 0.0]})
  224	          w2   (stellar/collapse-system w)
  225	          ob   (ecs/get-component w2 eid c/oblateness)]
  226	      (is (< (Math/abs (- ob 1.0)) 1e-12)))))
  227	
❯ 
● This is a substantial, interlocking set of physics changes, and your own spec demands the law→test→implementation discipline. Let me look at the last pieces I need (components, field laws, the gravity softening default) so my fixes fit the existing conventions.
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
  20	;; --- Stellar / matter state -------------------------------------------------
  21	;; Thermodynamic + chemical state carried by resolved matter, from nebular gas
  22	;; through protostar, star, and planet. The same components describe a clump of
  23	;; gas and a finished world — only the magnitudes change.
  24	(def temperature  :component/temperature)  ;; kelvin
  25	(def density      :component/density)       ;; kg/m^3
  26	(def pressure     :component/pressure)      ;; pascal
  27	(def composition  :component/composition)   ;; {:H 0.75 :He 0.24 ...} mass fractions
  28	(def luminosity   :component/luminosity)    ;; watts (0 until fusion)
  29	(def matter-state :component/matter-state)  ;; :nebula :protostar :star :planet :debris
  30	
  31	;; --- Field / MHD ------------------------------------------------------------
  32	;; The electromagnetic layer. `b-field` is the magnetic field vector (tesla, SI)
  33	;; frozen into a clump; `regime` is the dominant-physics tag the classifier
  34	;; writes each tick (:gravity-hydro :mhd-dominated :gravitationally-unstable ...).
  35	(def b-field      :component/b-field)       ;; [bx by bz] tesla
  36	(def regime       :component/regime)        ;; keyword, see domain.regime/classify
  37	
  38	;; --- Rotational / disc geometry ---------------------------------------------
  39	;; `angular-momentum` is the total orbital+spin L of the clump (kg m²/s).
  40	;; `spin` is the body-fixed angular velocity vector (rad/s).
  ──── (16 lines hidden) ──────────────────────────────────────────────
  57	
  58	;; --- Atmosphere -------------------------------------------------------------
  59	(def atmos-cell  :component/atmos-cell)
  60	
  61	;; --- Biome ------------------------------------------------------------------
  62	(def biome-cell  :component/biome-cell)
  63	
  64	;; --- Civilization -----------------------------------------------------------
  65	(def civilization :component/civilization)
  66	(def territory    :component/territory)
  67	
  68	;; --- Render -----------------------------------------------------------------
  69	(def renderable   :component/renderable)
  70	(def cell-id      :component/cell-id)
  71	
  72	;; --- Myth engine ------------------------------------------------------------
  73	(def facet-vector :component/facet-vector)
  74	(def favor        :component/favor)
  75	(def scribe       :component/scribe)
  76	
