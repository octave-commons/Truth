(ns law.crater
  "Crater scaling-law constants and schemas for the collision → shock →
   voxel-carving pipeline (Voxel 5, kanban/tasks/collision-shock-voxel-
   carving.md; design docs/designs/planetary-voxel-substrate.md §6).

   EVERY scaling constant here is TRANSCRIBED, not derived, from
   docs/research/2026-07-22-crater-scaling-laws-for-voxel-carving.md (the
   §6 constants table), which selected: Schmidt & Housen 1987 / Holsapple &
   Schmidt π-group framework in the Collins, Melosh & Marcus 2005
   dimensional form; Croft 1985 complex-crater conversion; Pierazzo et al.
   1997 energy-scaled melt (NOT Bjorkman-Holsapple point-source — research
   §9 item 3 explains why); Benz & Asphaug 1999 Q*_D disruption gating;
   Kraus, Senft & Stewart 2011 CTH fits for ice targets. Citations ride
   with each constant.

   ERROR BARS THE RESEARCH FLAGS (honoured downstream, flagged here):
   - K1 = 1.161 is a factor-1.5 constant (Collins et al. 2005 quote the
     range 0.8–1.5): crater diameters ±40%, volumes ±factor ~3.
   - K_s (strength-regime prefactor) is the WORST-constrained constant in
     the set: factor ~2 (research §9 item 7). It only matters for sub-100 m
     carving into strong targets — exactly the 64 m voxel regime.
   - Vapor volume is the weakest fit: the f_v ∈ [0.1, 0.3] efficiency is a
     disposition choice, factor ~3, meaningful only for U ≳ 25 km/s
     (research §9 item 8).
   - Melt volume ±factor 2 (research §9 item 2); excavation/ejecta
     geometry ±factor 2 (item 9); dispersal factor 2× is a game convention
     over Benz & Asphaug (item 10).

   Units: SI throughout — m, kg, m/s, J, J/kg, K, Pa. Impact angles θ are
   measured FROM THE HORIZONTAL (90° = vertical), the Collins et al. 2005
   convention."
  (:require
   [malli.core :as m]
   [law.voxel :as voxel]))

;; --- Crater-dimension fits (Collins, Melosh & Marcus 2005, MAPS 40, 817–840) ---

(def ^:const k1-gravity-rock
  "Gravity-regime prefactor for competent rock: D_tc = K1·(ρ_i/ρ_t)^(1/3)·
   L^0.78·U^0.44·g^(−0.22)·sin^(1/3)θ. Collins et al. 2005; Schmidt &
   Housen 1987. FACTOR-1.5 CONSTANT: Collins et al. quote 'a best estimate
   within a range of 0.8 to 1.5' — diameters ±40%, volumes ±factor ~3."
  1.161)

(def ^:const k1-gravity-water
  "Gravity-regime prefactor for water targets. Schmidt & Housen 1987 via
   Collins et al. 2005."
  1.365)

(def ^:const k-strength
  "Strength-regime prefactor: π_V = K_s·π_3^(−μ/2) (Holsapple 1993, Annu.
   Rev. Earth Planet. Sci. 21, 333–373). FACTOR-~2 CONSTANT — the worst-
   constrained in the whole set (research §9 item 7); matters exactly at
   the sub-100 m carving scale of the 64 m voxel grid. Adopted for
   continuity with the gravity fit's π-form constant."
  1.6)

(def ^:const mu-rock
  "Coupling-parameter velocity exponent μ for competent rock (C = a·U^μ·ρ^ν,
   momentum/energy point-source limits μ ∈ [1/3, 2/3]). Schmidt & Housen
   1987; Holsapple & Schmidt 1987."
  0.55)

(def ^:const mu-ice
  "Adopted μ for cold H₂O ice. UNRESOLVED in the literature: Croft 1981
   fit 0.64; Senft & Stewart 2008 / Kraus et al. 2011 work rock-like at
   0.55. Adopted 0.55; the difference shifts D ∝ U^0.44 → U^0.51, small
   over the game's velocity range (research §9 item 4)."
  0.55)

(def ^:const exponent-L-gravity 0.78)   ;; Collins et al. 2005 (rock μ=0.55 fit)
(def ^:const exponent-U-gravity 0.44)   ;; Collins et al. 2005
(def ^:const exponent-g-gravity -0.22)  ;; Collins et al. 2005

(def ^:const transient-depth-factor
  "d_tc = D_tc/(2√2) — transient crater depth. Collins et al. 2005 eq. 25.
   UNUSED-PENDING: the complex-crater relaxation pass (widened rim,
   reduced final depth, central uplift) is a known simplification in
   `domain.voxel.carve/derive-edits` — see its docstring."
  0.3535534)

(def ^:const excavation-depth-fraction
  "d_exc = D_tc/10 — maximum excavation depth. Melosh 1989, Impact
   Cratering (Oxford UP), §5."
  0.1)

(def ^:const excavation-volume-coeff
  "V_exc = (π/80)·D_tc³ — paraboloid of diameter D_tc, depth d_exc.
   DISPOSITION, ±factor 2 (research §9 item 9): this is the volume
   converted to ejecta; the rest of the transient cavity is displaced,
   not ejected."
  0.0392699)

(def ^:const simple-final-factor
  "D_fr = 1.25·D_tc — simple-crater final rim diameter. Grieve & Garvin
   1984 analytical collapse model, fit in Collins et al. 2005."
  1.25)

(def ^:const complex-coeff
  "D_fr[km] = 1.17·D_tc[km]^1.13·D_sc[km]^(−0.13) — complex-crater final
   rim. Croft 1985, Proc. LPSC 15, via Collins et al. 2005. THE FIT IS
   PUBLISHED IN KM — convert at the boundary."
  1.17)

(def ^:const complex-exponent-tc 1.13)  ;; Croft 1985
(def ^:const complex-exponent-sc -0.13) ;; Croft 1985

(def ^:const complex-depth-coeff
  "d_fr[km] = 0.4·D_fr[km]^0.3 — complex final depth. Herrick et al. 1997
   Venus relation, via Collins et al. 2005. Published in KM.
   UNUSED-PENDING: complex-crater relaxation is a known simplification in
   `domain.voxel.carve/derive-edits` — see its docstring."
  0.4)

(def ^:const complex-depth-exponent
  "Herrick et al. 1997. UNUSED-PENDING: see `complex-depth-coeff`."
  0.3)

(def ^:const simple-complex-D-earth-m
  "Simple→complex transition diameter on Earth (m). Dence 1965 via Collins
   et al. 2005; scales with inverse surface gravity: D_sc = 3200·(9.81/g).
   Icy satellites follow the same gravity trend (Schenk 2002, GRL 29), so
   the same formula is adopted for ice."
  3200.0)

(def ^:const earth-surface-gravity
  "9.81 m/s² — the reference g in D_sc = 3200·(9.81/g)."
  9.81)

;; --- Melt and vapor volumes (Pierazzo et al. 1997 energy scaling) --------------

(def ^:const melt-coeff-rock
  "V_melt = coeff·E·sinθ [m³, E in J] for rock. Collins et al. 2005 eq. 30,
   fit to O'Keefe & Ahrens 1982, Pierazzo et al. 1997, Pierazzo & Melosh
   2000 hydrocode results and Grieve & Cintala 1992 terrestrial craters.
   ENERGY SCALING (μ = 2/3): Pierazzo et al. 1997 showed the Bjorkman &
   Holsapple 1987 point-source limit does NOT apply to melt/vapor —
   do not implement the point-source exponent (research §9 item 3).
   Validity U > 12 km/s, comparable impactor/target densities. ±FACTOR 2."
  8.9e-12)

(def ^:const melt-energy-granite-J-per-kg
  "ε_m = 5.2 MJ/kg — specific energy of the Rankine–Hugoniot state whose
   isentropic release ends on the 1-bar liquidus (granite). Pierazzo et
   al. 1997."
  5.2e6)

(def ^:const melt-threshold-U-rock
  "Incipient melt velocity (m/s) for rock, similar-density impactor/target.
   O'Keefe & Ahrens 1982 via Collins et al. 2005. NOTE: plastic-work
   heating contributes ~35% of melt below ~12.5 km/s — the hard threshold
   masks a soft shoulder (Kurosawa & Genda 2018; research §9 item 2)."
  1.2e4)

(def ^:const vapor-energy-silicate-J-per-kg
  "ε_v — specific energy for silicate vaporization. Ahrens & O'Keefe 1972 /
   Pierazzo et al. 1997 (critical-entropy method)."
  1.3e7)

(def ^:const vapor-efficiency
  "f_v — the near-isobaric-core fraction that fully vaporizes: V_vapor ≈
   f_v·E·sinθ/(ρ_t·ε_v). Bjorkman & Holsapple 1987 impedance-matching
   geometry. WEAKEST FIT IN THE SET: the published range is [0.1, 0.3]
   (FACTOR ~3, research §9 item 8) and it is a disposition choice, not a
   published fit; meaningful only for U ≳ 25 km/s. Adopted midpoint 0.2."
  0.2)

(def ^:const vapor-threshold-U-rock
  "Incipient vaporization velocity (m/s) for rock — order-of-magnitude
   (incipient→complete spans ~25–40 km/s). Pierazzo et al. 1997."
  2.5e4)

;; --- H₂O ice fits (Kraus, Senft & Stewart 2011, Icarus 214, 724–738) -----------
;; CTH + 5-phase H₂O EOS hydrocode fits. U ENTERS IN KM/S (transcribe
;; carefully). T_r = T_target/273 K. V_P = impactor volume. Valid U > 8 km/s
;; (±50% deviation 5–8 km/s; negligible melt < 5 km/s). Cross-check: ice melt
;; ≈ 10× rock melt at the same energy (Pierazzo et al. 1997).

(def ^:const ice-vapor-coeff 1.0e-4)      ;; V_vapor = V_P·coeff·(T_r+0.07)·U^1.7·sin^0.6 θ
(def ^:const ice-vapor-T-offset 0.07)
(def ^:const ice-vapor-U-exponent 1.7)
(def ^:const ice-vapor-angle-exponent 0.6)

(def ^:const ice-melt-coeff 2.8e-4)       ;; V_melt+vapor = V_P·coeff·(T_r+0.40)·U^1.6·sin^0.7 θ; melt = difference
(def ^:const ice-melt-T-offset 0.4)
(def ^:const ice-melt-U-exponent 1.6)
(def ^:const ice-melt-angle-exponent 0.7)

(def ^:const ice-melt-threshold-U
  "Little melt below 5 km/s (m/s). Kraus et al. 2011 fits are valid above
   8 km/s and ±50% in 5–8 km/s."
  5.0e3)

(def ^:const ice-vapor-threshold-U
  "Ice vapor fits valid above 8 km/s (m/s). Kraus et al. 2011."
  8.0e3)

;; --- Disruption thresholds (Benz & Asphaug 1999, Icarus 142, 5–20, Table III) ---
;; Q̄*_D = Q₀·(R/1 cm)^a + B·ρ·(R/1 cm)^b, cgs (erg/g, g/cm³). SI transcription
;; (research §3): Q*_D [J/kg] = Q₀_SI·(R/0.01 m)^a + B·10⁻⁷·(ρ_t/1000)·
;; (R/0.01 m)^b with R in metres, ρ_t in kg/m³. Weakest bodies are ~200–300 m
;; diameter. Head-on values adopted; grazing raises Q*_D up to ×10 (B&A 1999)
;; — conservative.

(def disruption-basalt
  "Basalt at U_ref 5 km/s: {:Q0-J-per-kg :B (cgs) :a :b}. Benz & Asphaug
   1999 Table III."
  {:Q0-J-per-kg 9.0e3 :B 0.5 :a -0.36 :b 1.36 :U-ref 5.0e3})

(def disruption-ice
  "Ice at U_ref 3 km/s. Benz & Asphaug 1999 Table III."
  {:Q0-J-per-kg 1.6e3 :B 1.2 :a -0.39 :b 1.26 :U-ref 3.0e3})

(def ^:const disruption-shatter-fraction
  "Q*_S ≈ ½·Q*_D — the shattering-without-dispersal floor (strength-regime
   onset, Benz & Asphaug 1999)."
  0.5)

(def ^:const disruption-dispersal-factor
  "Q ≥ 2·Q*_D ⇒ full dispersal. GAME CONVENTION over Benz & Asphaug's
   Q*_D (largest remnant = half mass) — research §9 item 10."
  2.0)

;; --- Angle dependence ------------------------------------------------------------

(def ^:const angle-default-rad
  "Most probable impact angle: 45° (Shoemaker 1962). The fallback when an
   impact geometry carries no direction."
  0.7853982)

;; --- Thermal / cooling (research §5.4) -------------------------------------------

(def ^:const thermal-kappa-rock
  "Thermal diffusivity of rock (m²/s) — order unity standard value.
   Conduction cooling timescale τ_cond ≈ h²/κ (research §5.4)."
  1.0e-6)

(def ^:const melt-cooling-time-constant-s
  "Per-voxel melt cooling time constant (s): τ = e²/κ with e the canonical
   64 m voxel edge and κ = thermal-kappa-rock — the conductive timescale of
   one voxel's own thickness (research §5.4: τ_cond ≈ h²/κ, h the local
   melt-sheet thickness; the voxel edge is the honest local h for a
   voxel-tagged sheet). ~4.1e9 s ≈ 130 yr: melt floors visibly persist,
   then re-cool. Ice κ is the same order (1e-6, research §5.4)."
  (/ (* voxel/canonical-voxel-edge-m voxel/canonical-voxel-edge-m)
     thermal-kappa-rock))

(def ^:const melt-tag-temperature-rock-k
  "Tag temperature (K) of shock-melted rock voxels — basaltic liquidus
   order, the same reference as law.voxel/sculpt-melt-temperature-k (no
   per-material melt model yet)."
  1.4e3)

(def ^:const melt-tag-temperature-ice-k
  "Tag temperature (K) of shock-melted ice voxels — the H₂O melt point."
  273.15)

(def ^:const vapor-tag-temperature-rock-k
  "Tag temperature (K) of vaporized silicate voxels — silicate vapor
   order. Bookkeeping tag only (see the vapor disposition note on
   `domain.voxel.carve`)."
  3.0e3)

(def ^:const vapor-tag-temperature-ice-k
  "Tag temperature (K) of vaporized H₂O voxels — the 1-bar boiling point
   of water, the honest steam-order reference for a bookkeeping tag
   (Kraus et al. 2011 vapor is supercritical at impact pressures, but the
   tag only needs to sit above the ice melt point so cooling relaxes it
   through the right sequence)."
  3.7315e2)

;; --- Game first-model constants (NOT literature — marked honestly) ----------------

(def impactor-density-kg-per-m3
  "Impactor bulk density by material class (kg/m³) — GAME FIRST MODEL, not
   a research constant: the absorb-merge packet carries mass and
   composition but no radius, so the classifier recovers impactor diameter
   from mass ÷ an assumed class density. Fe meteorite / chondrite / ice-Ih
   orders."
  {:iron 7.8e3 :rock 3.0e3 :ice 9.17e2})

(def ^:const impactor-ice-hydrogen-floor
  "GAME FIRST MODEL: an impactor classifies `:ice` only when its hydrogen
   mass share exceeds this floor — water ice is H₂O, so a hydrogen-free
   composition cannot be an ice body no matter its oxygen share (a bare-O
   gate misclassifies anhydrous silicates: SiO₂-order compositions are
   >50% O by mass). See `domain.voxel.carve/impactor-material-class`."
  0.05)

(def ^:const sub-voxel-diameter-m
  "Craters whose transient diameter falls below one canonical voxel edge
   cannot be represented on the grid — the carve no-ops HONESTLY (no
   edits), it does not round up to a one-voxel poke."
  voxel/canonical-voxel-edge-m)

;; --- Schemas ------------------------------------------------------------------------

(def scaling-material-class-schema
  "Target/impactor material class for the scaling laws: `:rock` covers
   basalt/granite/regolith (the Collins et al. 2005 rock fits); `:ice`
   routes to the Kraus et al. 2011 CTH fits. Water targets reuse the rock
   exponents with k1-gravity-water (research §1) — not yet a class here."
  [:enum :rock :ice])

(def crater-regime-schema
  "The cratering-branch regimes of the research §3 decision table
   (branches 6–8). Disruption-branch outcomes
   (:catastrophic-disruption / :disruption-marginal / :shattering /
   :basin-magma-ocean) classify but never produce a carve plan — planetary
   disruption is beyond Voxel 5."
  [:enum :strength-crater :simple-crater :complex-crater])

(def collision-regime-schema
  "Full classifier output vocabulary (research §3 decision table, branches
   1–8; merging/accretion branch 4 is the collision handler's merge path,
   not a classifier output)."
  [:enum :catastrophic-disruption :disruption-marginal :shattering
   :basin-magma-ocean :strength-crater :simple-crater :complex-crater])

(def shock-disposition-schema
  "Melt/vapor sub-classification inside the cratering regimes (research §3):
   `:none` below the melt threshold, `:melt` between melt and vapor
   thresholds, `:melt+vapor` at or above the vapor threshold."
  [:enum :none :melt :melt+vapor])

(def carve-plan-schema
  "One collision's voxel-carving plan: the record the `:voxel-carve`
   fan-out system derives from an absorb-merge packet (classifier + bulk
   volumes, all SI) and the `:voxel-focus` fold translates into
   `:apply-edits` jobs with provenance `:collision`.

   :anchor          unit body-centric impact direction (target centre →
                    impact point)
   :theta           impact angle from horizontal (rad)
   :regime          `crater-regime-schema`
   :material-class  target material class (`scaling-material-class-schema`)
   :d-tc :d-fr :d-exc
                    transient/final-rim diameter and excavation depth (m)
   :v-exc :v-melt :v-vapor
                    excavated / melt / vapor volumes (m³) — error bars per
                    this ns's header (diameters ±40%, melt ±factor 2,
                    vapor ±factor 3)
   :melt-sheet-thickness
                    t_m = 4·V_melt/(π·D_tc²) (m) — Collins et al. 2005
                    eq. 31; the melt floor tag depth
   :impact-energy   ½·m_i·U² (J) — the classifier input, carried for the
                    record
   :region          bounding `law.voxel/region-schema` of the carve — the
                    out-of-band diff target (design §7.3)
   :tick            the tick the plan was classified"
  [:map
   [:anchor [:tuple :double :double :double]]
   [:theta [:and :double [:>= 0]]]
   [:regime crater-regime-schema]
   [:material-class scaling-material-class-schema]
   [:d-tc [:and :double [:> 0]]]
   [:d-fr [:and :double [:> 0]]]
   [:d-exc [:and :double [:> 0]]]
   [:v-exc [:and :double [:>= 0]]]
   [:v-melt [:and :double [:>= 0]]]
   [:v-vapor [:and :double [:>= 0]]]
   [:melt-sheet-thickness [:and :double [:>= 0]]]
   [:impact-energy [:and :double [:>= 0]]]
   [:region voxel/region-schema]
   [:tick [:and :int [:>= 0]]]])

(def carve-plan?
  "Predicate: does `value` satisfy `law.crater/carve-plan-schema`?"
  (m/validator carve-plan-schema))

(def disruption-report-schema
  "A classified NON-cratering collision outcome (research §3 branches 1–5):
   reported, never carved. `:regime` is one of the non-cratering
   `collision-regime-schema` values; `:note` names the missing pipeline
   (fragmentation, magma-ocean FSM transition) the outcome is parked on."
  [:map
   [:regime [:enum :catastrophic-disruption :disruption-marginal :shattering
             :basin-magma-ocean]]
   [:q [:and :double [:>= 0]]]
   [:q-star [:and :double [:>= 0]]]
   [:impactor-mass [:and :double [:>= 0]]]
   [:impact-velocity [:and :double [:>= 0]]]
   [:anchor [:tuple :double :double :double]]
   [:note :string]
   [:tick [:and :int [:>= 0]]]])

(def disruption-report?
  "Predicate: does `value` satisfy `law.crater/disruption-report-schema`?"
  (m/validator disruption-report-schema))

(def carve-request-schema
  "The `c/voxel-carve-request` component value on the committed world:
   `:plans` await the `:voxel-focus` fold (one Jacobi tick stale);
   `:disruptions` ACCUMULATE — the reported non-cratering outcomes are the
   sub-catastrophic stop (planetary disruption is beyond Voxel 5), and a
   stop that vanishes one tick later is no stop at all; `:seen` is the
   idempotency set of absorb-merge packets already classified (the packet
   channel is sticky — `domain.physics.collision` never clears it — so the
   system records what it has consumed)."
  [:map
   [:plans [:vector carve-plan-schema]]
   [:disruptions [:vector disruption-report-schema]]
   [:seen [:set :map]]])

(def carve-request?
  "Predicate: does `value` satisfy `law.crater/carve-request-schema`?"
  (m/validator carve-request-schema))
