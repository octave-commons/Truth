(ns domain.chemistry
  "Chemistry and elemental composition for stellar and planetary formation.
   Tracks elemental abundance, molecular formation, and phase transitions.

   Composition is an explicit element map (see `law.composition/element-set`).
   Molecules and bulk categories (gas, rock, ice, metal) are derived on demand.",
  (:require
   [clojure.math :as math] [clojure.set]
   [domain.ecs.core        :as ecs]
   [domain.ecs.components   :as c]
   [law.chemistry           :as lchem]
   [law.composition         :as lcomp]
   [law.atmosphere          :as atmosphere]
   [law.stellar             :as law]))

;; --- Element re-exports -------------------------------------------------------

;; UNUSED-PENDING: Chemistry evolution paths: composition tables and transformation fns exist,
;; but no tick calls them — `domain.genesis/physics-systems-parallel` has no
;; chemistry-evolution emitter yet. CLAUDE.md names this class explicitly.
;; See kanban/tasks/phase-0-chemistry-differentiation-spec.md
#_{:clj-kondo/ignore [:clojure-lsp/unused-public-var]}
(defn solar-composition
  "Population-I (solar) element composition map."
  []
  lcomp/solar-composition)

;; UNUSED-PENDING: Chemistry evolution paths: composition tables and transformation fns exist,
;; but no tick calls them — `domain.genesis/physics-systems-parallel` has no
;; chemistry-evolution emitter yet. CLAUDE.md names this class explicitly.
;; See kanban/tasks/phase-0-chemistry-differentiation-spec.md
#_{:clj-kondo/ignore [:clojure-lsp/unused-public-var]}
(defn primordial-composition
  "Primordial BBN element composition map."
  []
  lcomp/primordial-composition)

;; --- Element properties ------------------------------------------------------

(def element-properties
  "Physical properties of tracked elements: atomic mass (u), boiling and
   melting points (K) at ~1 bar. Used for rough phase estimation."
  {:H   {:mass 1.008   :boiling-point 20.28   :melting-point 14.01}
   :He  {:mass 4.003   :boiling-point 4.22    :melting-point 0.95}
   :D   {:mass 2.014   :boiling-point 23.67   :melting-point 18.73}
   :He3 {:mass 3.016   :boiling-point 3.19    :melting-point 0.0}
   :Li7 {:mass 7.016   :boiling-point 1603    :melting-point 453.65}
   :C   {:mass 12.011  :boiling-point 3915    :melting-point 3550}
   :N   {:mass 14.007  :boiling-point 77.36   :melting-point 63.15}
   :O   {:mass 15.999  :boiling-point 90.20   :melting-point 54.36}
   :Ne  {:mass 20.180  :boiling-point 27.07   :melting-point 24.56}
   :Na  {:mass 22.990  :boiling-point 1156    :melting-point 370.87}
   :Mg  {:mass 24.305  :boiling-point 1363    :melting-point 923}
   :Al  {:mass 26.982  :boiling-point 2792    :melting-point 933.47}
   :Si  {:mass 28.085  :boiling-point 3538    :melting-point 1687}
   :S   {:mass 32.06   :boiling-point 717.8   :melting-point 388.4}
   :Ca  {:mass 40.078  :boiling-point 1757    :melting-point 1115}
   :Fe  {:mass 55.845  :boiling-point 3134    :melting-point 1811}
   :Ni  {:mass 58.693  :boiling-point 3186    :melting-point 1728}})

;; --- Composition transforms --------------------------------------------------

(defn blend-compositions
  "Mass-weighted blend of two composition maps.
   Returns a normalized composition map. Missing elements default to 0."
  [c1 m1 c2 m2]
  (let [m1 (double m1)
        m2 (double m2)
        total (+ m1 m2)]
    (if (pos? total)
      (let [inv (/ 1.0 total)
            ks (into (set (keys c1)) (keys c2))]
        (reduce (fn [m k]
                  (let [v (+ (* m1 (double (get c1 k 0.0)))
                             (* m2 (double (get c2 k 0.0))))]
                    (assoc m k (* v inv))))
                {} ks))
      {})))

(defn burn-composition
  "Convert a fraction `f` of the current hydrogen into helium, conserving mass.
   Returns a new composition map."
  [composition f]
  (let [f (double f)
        h (double (get composition :H 0.0))
        dH (* h f)]
    (if (pos? h)
      (-> composition
          (assoc :H (max 0.0 (- h dH)))
          (update :He (fnil + 0.0) dH))
      composition)))

;; UNUSED-PENDING: Chemistry evolution paths: composition tables and transformation fns exist,
;; but no tick calls them — `domain.genesis/physics-systems-parallel` has no
;; chemistry-evolution emitter yet. CLAUDE.md names this class explicitly.
;; See kanban/tasks/phase-0-chemistry-differentiation-spec.md
#_{:clj-kondo/ignore [:clojure-lsp/unused-public-var]}
(defn enrich-composition
  "Add metals to a composition map. `delta-mz` is the added metal mass and
   `yield-map` maps element keywords to their fractional yield of that metal
   mass (must sum to ≈ 1.0). Returns a normalized composition map."
  [composition delta-mz yield-map]
  (let [base-m 1.0
        new-m  (+ base-m (double delta-mz))
        inv    (/ 1.0 new-m)]
    (reduce-kv (fn [m k v]
                 (let [added (* (double delta-mz) (double (get yield-map k 0.0)))
                       new-v (* (+ (double v) added) inv)]
                   (assoc m k new-v)))
               {}
               composition)))

(defn wind-composition
  "Surface composition of a wind parcel launched from a star.
   For now this is the star's current composition; later it can mask
   gravitational settling or radiative levitation."
  [star-composition]
  star-composition)

;; --- Phase determination -----------------------------------------------------

(defn solid-fraction
  "Smooth condensed (solid) fraction of an element at `temperature` (K) whose
   50%-condensation temperature is `tc` (K): the logistic

     s(T) = 1 / (1 + exp((T − Tc) / ΔT)),  ΔT = law.composition/condensation-width

   s(Tc) = 0.5 (half condensed), s → 1 for T ≪ Tc, s → 0 for T ≫ Tc. Replaces
   the hard `(< T Tc)` step (spec §6.1, decision §10.3): real condensation is
   a nucleation-and-growth process spread over ~ΔT around Tc, so an element at
   T ≈ Tc is split proportionally between phases, not all-or-nothing."
  [temperature tc]
  (/ 1.0 (+ 1.0 (math/exp (/ (- (double temperature) (double tc))
                             lcomp/condensation-width)))))

(defn partition-solids
  "Partition a composition map into solid and gas phases at `temperature` (K)
   using the Lodders condensation sequence (law.composition/condensation-
   temperatures) and the sigmoid `solid-fraction` (spec §6.1): each element's
   mass fraction v is split into v·s (solid) and v·(1−s) (gas), so an element
   near its Tc contributes to BOTH phases and the split conserves element mass
   exactly. Returns `{:solid element-map :gas element-map}` with both maps
   normalized independently so each sums to 1.0. Derived on demand, never
   cached (decision §10.2)."
  [composition temperature]
  (let [temp (double temperature)
        [solid gas]
        (reduce-kv (fn [[s g] k v]
                     (let [tc (double (get lcomp/condensation-temperatures k 50.0))
                           fs (solid-fraction temp tc)
                           v  (double v)
                           vs (* v fs)
                           vg (* v (- 1.0 fs))]
                       [(if (pos? vs) (assoc s k vs) s)
                        (if (pos? vg) (assoc g k vg) g)]))
                   [{} {}]
                   composition)]
    {:solid (lcomp/normalize solid)
     :gas   (lcomp/normalize gas)}))

(defn bulk-categories
  "Return the fractional bulk categories `{:gas :rock :metal :ice}` for a
   composition at `temperature`, normalized to sum to 1.0.

   Each element's mass fraction is split by the sigmoid `solid-fraction`
   (spec §6.1): the condensed share is classed by condensate kind — C/N/O →
   ice, Fe/Ni → metal, other rock-formers → rock, frozen gas-formers (H/He/Ne,
   no solid category) → gas — and the uncondensed share is always gas. Derives
   fractions from the original composition — NOT from `partition-solids`,
   whose :solid/:gas maps are each independently normalized and so cannot
   report the solid/gas split."
  [composition temperature]
  (let [temp  (double temperature)
        total (double (reduce + 0.0 (vals composition)))
        classify (fn [k]
                   (condp contains? k
                     lcomp/ice-formers :ice
                     #{:Fe :Ni} :metal
                     lcomp/rock-formers :rock
                     :gas)) ;; frozen gas-former
        sums (reduce-kv (fn [m k v]
                          (let [tc (double (get lcomp/condensation-temperatures k 50.0))
                                fs (solid-fraction temp tc)
                                v  (double v)]
                            (-> m
                                (update (classify k) + (* v fs))
                                (update :gas + (* v (- 1.0 fs))))))
                        {:gas 0.0 :rock 0.0 :metal 0.0 :ice 0.0}
                        composition)]
    (if (pos? total)
      (let [inv (/ 1.0 total)]
        {:gas   (* (:gas sums) inv)
         :rock  (* (:rock sums) inv)
         :metal (* (:metal sums) inv)
         :ice   (* (:ice sums) inv)})
      {:gas 0.0 :rock 0.0 :metal 0.0 :ice 0.0})))

(defn condensed-inventory
  "Combine `partition-solids` and `bulk-categories` into a single report:
   `{:solid {...} :gas {...} :categories {...}}`."
  [composition temperature]
  {:solid      (:solid (partition-solids composition temperature))
   :gas        (:gas (partition-solids composition temperature))
   :categories (bulk-categories composition temperature)})

;; UNUSED-PENDING: Chemistry evolution paths: composition tables and transformation fns exist,
;; but no tick calls them — `domain.genesis/physics-systems-parallel` has no
;; chemistry-evolution emitter yet. CLAUDE.md names this class explicitly.
;; See kanban/tasks/phase-0-chemistry-differentiation-spec.md
#_{:clj-kondo/ignore [:clojure-lsp/unused-public-var]}
(defn material-phase
  "Determine phase of material (gas, liquid, solid) based on conditions.
   This is a rough element-by-element estimate; `partition-solids` is preferred
   for composition-wide condensation."
  [element temperature pressure]
  (let [props (get element-properties element)
        mp (:melting-point props)
        bp (:boiling-point props)
        pressure-factor (math/log10 (max 1 (/ pressure 101325)))]
    (cond
      (< temperature (* mp (inc (* 0.1 pressure-factor)))) :solid
      (> temperature (* bp (inc (* 0.1 pressure-factor)))) :gas
      :else :liquid)))

;; --- Molecular formation (derived) ------------------------------------------

;; UNUSED-PENDING: Chemistry evolution paths: composition tables and transformation fns exist,
;; but no tick calls them — `domain.genesis/physics-systems-parallel` has no
;; chemistry-evolution emitter yet. CLAUDE.md names this class explicitly.
;; See kanban/tasks/phase-0-chemistry-differentiation-spec.md
#_{:clj-kondo/ignore [:clojure-lsp/unused-public-var]}
(defn can-form-molecules?
  "Check if temperature allows molecular formation."
  [temperature _element1 _element2]
  (let [bond-energy-scale 5000]
    (< temperature bond-energy-scale)))

(defn molecular-composition
  "Calculate molecular composition based on temperature and elements.
   Returns a map of derived molecules (H2O, CO2, NH3, CH4, H2) with mass
   fractions taken from the element budget. This is a diagnostic, not a
   component."
  [elemental-comp temperature _pressure]
  (cond
    (> temperature 3000)
    elemental-comp

    (> temperature 1000)
    (let [h (:H elemental-comp 0)]
      (assoc elemental-comp :H2 (* 0.5 h)))

    :else
    (let [h (:H elemental-comp 0)
          o (:O elemental-comp 0)
          c (:C elemental-comp 0)
          n (:N elemental-comp 0)]
      (merge elemental-comp
             {:H2O (min (* h 0.5) (* o 2))
              :CO2 (min c (* o 0.5))
              :NH3 (min n (* h 0.33))
              :CH4 (min c (* h 0.25))}))))

;; UNUSED-PENDING: Chemistry evolution paths: composition tables and transformation fns exist,
;; but no tick calls them — `domain.genesis/physics-systems-parallel` has no
;; chemistry-evolution emitter yet. CLAUDE.md names this class explicitly.
;; See kanban/tasks/phase-0-chemistry-differentiation-spec.md
#_{:clj-kondo/ignore [:clojure-lsp/unused-public-var]}
(defn bulk-composition-category
  "Categorize a body based on its bulk composition at a temperature.
   Returns one of `:gas-giant`, `:ice-giant`, `:rocky`, `:metallic`, `:mixed`."
  [composition temperature]
  (let [{:keys [gas rock metal ice]} (bulk-categories composition temperature)
        total (+ gas rock metal ice)]
    (cond
      (> (/ gas total) 0.9) :gas-giant
      (> (/ (+ gas ice) total) 0.5) :ice-giant
      (> (/ rock total) 0.7) :rocky
      (> (/ metal total) 0.3) :metallic
      :else :mixed)))

;; --- Atmospheric retention ---------------------------------------------------

;; UNUSED-PENDING: Chemistry evolution paths: composition tables and transformation fns exist,
;; but no tick calls them — `domain.genesis/physics-systems-parallel` has no
;; chemistry-evolution emitter yet. CLAUDE.md names this class explicitly.
;; See kanban/tasks/phase-0-chemistry-differentiation-spec.md
#_{:clj-kondo/ignore [:clojure-lsp/unused-public-var]}
(defn escape-velocity
  "Calculate escape velocity for a body. Thin wrapper over
   `law.atmosphere/escape-velocity` (v_esc = sqrt(2GM/R))."
  [mass radius]
  (atmosphere/escape-velocity mass radius))

(defn can-retain-gas?
  "Check if body can retain a gas based on temperature and escape velocity.

   Uses the RMS thermal-velocity convention (`law.atmosphere/thermal-
   velocity-rms`, v_th = sqrt(3 k_B T / m)) with a uniform Jeans-ratio
   threshold of 6 for every species. `domain.stellar.classifier.planet/
   atmosphere-class` (M5 handoff Phase 3) shares this exact v_th convention via
   `law.atmosphere` but uses species-differentiated thresholds (6 for H2/He,
   3 for heavier secondary volatiles) — see that ns and the research note
   docs/research/atmosphere/planetary-atmosphere-retention-classifier.md §3.4
   for why a single v_th convention now backs both checks."
  [body-mass body-radius gas-element temperature]
  (let [molecular-mass (get-in element-properties [gas-element :mass] 1.0)
        species-mass-kg (* molecular-mass 1.66e-27)
        jeans-parameter (atmosphere/retention-ratio body-mass body-radius
                                                    temperature species-mass-kg)]
    (> jeans-parameter 6)))

;; UNUSED-PENDING: Chemistry evolution paths: composition tables and transformation fns exist,
;; but no tick calls them — `domain.genesis/physics-systems-parallel` has no
;; chemistry-evolution emitter yet. CLAUDE.md names this class explicitly.
;; See kanban/tasks/phase-0-chemistry-differentiation-spec.md
#_{:clj-kondo/ignore [:clojure-lsp/unused-public-var]}
(defn potential-atmosphere
  "Determine what atmosphere a body can retain from its element composition."
  [body-mass body-radius composition temperature]
  (reduce (fn [atmo element]
            (if (and (> (get composition element 0) 0.001)
                     (can-retain-gas? body-mass body-radius element temperature))
              (assoc atmo element (get composition element))
              atmo))
          {}
          [:H :He :N :O :Ne :H2O :CO2 :NH3 :CH4]))

;; --- Differentiation ---------------------------------------------------------

(def ^:private oxygen-per-silicon
  "Mass of oxygen bound per unit mass of silicon in silicate rock (SiO₂:
   2·15.999/28.085). Used to split the oxygen budget between rock and free
   oxygen/water."
  (/ (* 2.0 (get-in element-properties [:O :mass]))
     (get-in element-properties [:Si :mass])))

(def ^:private oxygen-per-carbon
  "Mass of oxygen bound per unit mass of carbon as CO/CO₂ (CO: 15.999/12.011 —
   the conservative single-O bound)."
  (/ (get-in element-properties [:O :mass])
     (get-in element-properties [:C :mass])))

(defn- oxygen-partition
  "Split the oxygen mass fraction of `composition` into
   `{:tied-c :tied-si :free}`: oxygen bound to carbon (CO/CO₂), oxygen bound to
   silicon (silicate rock), and the remainder available as free oxygen/water
   (spec §2.2). Carbon claims first, then silicon; never negative."
  [composition]
  (let [o   (double (get composition :O 0.0))
        c   (double (get composition :C 0.0))
        si  (double (get composition :Si 0.0))
        tied-c  (min o (* oxygen-per-carbon c))
        tied-si (min (- o tied-c) (* oxygen-per-silicon si))
        free    (max 0.0 (- o tied-c tied-si))]
    {:tied-c tied-c :tied-si tied-si :free free}))

(defn material-groups
  "Disjoint bulk material groups of an element composition (spec §2.2, adapted
   to the element-resolved model): `{:volatiles :silicates :metals :organics}`
   mass fractions, each atom counted exactly once so the groups sum to the
   composition total.

   - `:metals`    Fe + Ni (core material)
   - `:silicates` Si + O tied to Si, plus the other lithophile rock-formers
                  (Mg Al Ca Na S Li7)
   - `:organics`  C + O tied to C (the prebiotic carbon budget)
   - `:volatiles` H He D He3 Ne + N + free oxygen (gas + ice inventory)"
  [composition]
  (let [{:keys [tied-c tied-si free]} (oxygen-partition composition)
        g (fn [& ks] (reduce (fn [acc k] (+ acc (double (get composition k 0.0)))) 0.0 ks))]
    {:volatiles (+ (g :H :He :D :He3 :Ne :N) free)
     :silicates (+ (g :Si :Mg :Al :Ca :Na :S :Li7) tied-si)
     :metals    (g :Fe :Ni)
     :organics  (+ (g :C) tied-c)}))

(defn volatile-fraction
  "The volatile mass fraction of `composition`: H/He + ices + free oxygen +
   organics, single-counted (`:volatiles` + `:organics` of `material-groups`).
   This is the spec §2.2 volatile row (H + He + H₂O-proxy + CO/CO₂-proxy) made
   disjoint so it can be multiplied by mass without double-counting."
  [composition]
  (let [{:keys [volatiles organics]} (material-groups composition)]
    (+ volatiles organics)))

(defn volatile-budget
  "Volatile inventory of a body in kg: `volatile-fraction` × `mass`. Feeds the
   M5 habitability handoff as `:volatile-budget-kg`."
  [composition mass]
  (* (volatile-fraction composition) (double (or mass 0.0))))

(defn layer-fractions
  "Equilibrium differentiated layer partition of a fully molten body:
   `{:core :mantle :volatile}` mass fractions — core = metals (Fe+Ni sink),
   mantle = silicates/rock, volatile = H/He + ices + organics (rises or
   escapes, spec §5 table). Normalized so the three fractions sum to exactly
   1.0: the layers are a partition of the body's mass, so total layer mass
   equals body mass."
  [composition]
  (let [{:keys [metals silicates volatiles organics]} (material-groups composition)
        volatile (+ volatiles organics)
        total    (+ metals silicates volatile)]
    (if (pos? total)
      (let [inv (/ 1.0 total)]
        {:core (* metals inv) :mantle (* silicates inv) :volatile (* volatile inv)})
      {:core 0.0 :mantle 0.0 :volatile 0.0})))

(defn- volatile-layer-composition
  "Element map of the volatile layer (H He D He3 Ne C N + free oxygen),
   normalized to sum to 1. The surface of a fully differentiated body."
  [composition]
  (let [free (:free (oxygen-partition composition))]
    (lcomp/normalize
     (cond-> (select-keys composition [:H :He :D :He3 :Ne :C :N])
       (pos? free) (assoc :O free)))))

(defn differentiate-layers
  "One-tick differentiation step for a molten body (malleability > 0.8). The
   layer partition itself is the composition's equilibrium `layer-fractions` —
   the mass is always somewhere, so fractions always sum to 1 — while
   `:degree` eases 0 → 1 over `law.chemistry/differentiation-timescale` and
   `:surface-composition` interpolates from the bulk composition (degree 0,
   uniform) toward the volatile-layer composition (degree 1, segregated).
   `current` is the body's previous c/differentiated-layers value (nil when
   freshly molten)."
  [composition current dt]
  (let [{:keys [core mantle volatile]} (layer-fractions composition)
        degree  (min 1.0 (+ (double (or (:degree current) 0.0))
                            (/ (double dt) lchem/differentiation-timescale)))
        crust   (volatile-layer-composition composition)
        ks      (into (set (keys composition)) (keys crust))
        surface (reduce (fn [m k]
                          (let [b (double (get composition k 0.0))
                                s (double (get crust k 0.0))
                                v (+ (* (- 1.0 degree) b) (* degree s))]
                            (if (pos? v) (assoc m k v) m)))
                        {} ks)]
    {:core-fraction     core
     :mantle-fraction   mantle
     :volatile-fraction volatile
     :degree            degree
     :surface-composition (lcomp/normalize surface)}))

(defn strip-volatiles
  "Drive volatiles off a merged body whose post-impact temperature is `t` (K).
   Above `law.chemistry/ice-volatile-loss-temperature` the ice-volatile
   inventory (C + its bound O, N, free oxygen) is lost; above
   `hhe-volatile-loss-temperature` all primordial H/He (H He D He3 Ne) is lost
   as well. Returns `{:composition renormalized :lost-fraction f}` where `f` is
   the fraction of the body's total mass that escaped (0.0 below both
   thresholds). Oxygen bound into silicate rock always stays."
  [composition t]
  (let [temp (double t)
        {:keys [tied-c free]} (oxygen-partition composition)
        ice? (>= temp lchem/ice-volatile-loss-temperature)
        hhe? (>= temp lchem/hhe-volatile-loss-temperature)
        drop-ks (cond-> []
                  hhe? (into [:H :He :D :He3 :Ne])
                  ice? (into [:C :N]))
        dropped (reduce (fn [acc k] (+ acc (double (get composition k 0.0)))) 0.0 drop-ks)
        lost-o  (if ice? (+ tied-c free) 0.0)
        lost    (+ dropped lost-o)]
    (if (pos? lost)
      {:composition   (lcomp/normalize
                       (cond-> (apply dissoc composition drop-ks)
                         ice? (-> (assoc :O (max 0.0 (- (double (get composition :O 0.0))
                                                        lost-o)))
                                  (as-> m (if (pos? (:O m)) m (dissoc m :O))))))
       :lost-fraction lost}
      {:composition composition :lost-fraction 0.0})))

(def ^:private non-differentiating-states
  "Matter states that never differentiate: diffuse gas and stars (plasma, not
   density-layered rock). Mirrors the classifier's planet-candidate set."
  #{:nebula :star :protostar :stellar-remnant})

(defn differentiation-system
  "Write-set emitter: SOLE writer of `c/differentiated-layers` and
   `c/volatile-budget` (chemistry spec §5, §7 Phase 3-4).

   Every body with composition + mass gets its `c/volatile-budget` refreshed
   (kg of H/He + ices + free oxygen + organics) — cold future planets included,
   since the M5 handoff reads it. Bodies whose temperature puts them above
   `law.chemistry/differentiation-malleability-min` (molten, spec §3) advance
   their `c/differentiated-layers` one tick (`differentiate-layers`); cold
   bodies are simply not written — a body without the component is
   undifferentiated, and a body that cools below the threshold keeps its last
   layer record frozen. Runs as a Jacobi fan-out emitter reading the
   integrator-owned temperature/composition one tick stale — the
   'after thermal-system' ordering of spec §7 Phase 3 is satisfied because
   temperature is now integrator-owned (the :thermal system is retired) and
   every fan-out read is one tick stale by construction."
  [dt]
  {:id     :differentiation
   :writes #{c/differentiated-layers c/volatile-budget}
   :reads  #{c/matter-state c/composition c/mass c/temperature
             c/differentiated-layers}
   :run
   (fn [world]
     (let [eids (ecs/entities-with world c/matter-state c/composition c/mass)
           budget-cell
           (into {}
                 (map (fn [eid]
                        [eid (volatile-budget (ecs/get-component world eid c/composition)
                                              (ecs/get-component world eid c/mass))]))
                 eids)
           layer-cell
           (into {}
                 (keep (fn [eid]
                         (let [state (ecs/get-component world eid c/matter-state)
                               temp  (double (or (ecs/get-component world eid c/temperature) 0.0))]
                           (when (and (not (contains? non-differentiating-states state))
                                      (> (law/malleability temp)
                                         lchem/differentiation-malleability-min))
                             [eid (differentiate-layers
                                   (ecs/get-component world eid c/composition)
                                   (ecs/get-component world eid c/differentiated-layers)
                                   dt)]))))
                 eids)]
       (cond-> (if (empty? budget-cell) {} {c/volatile-budget budget-cell})
         (seq layer-cell) (assoc c/differentiated-layers layer-cell))))})

;; --- Prebiotic chemistry -----------------------------------------------------

(defn habitability-score
  "Calculate rough habitability potential."
  [{:keys [temperature pressure composition]}]
  (let [has-water (or (> (get composition :H2O 0) 0.001)
                      (> (+ (double (get composition :O 0.0))
                            (double (get composition :H 0.0))) 0.01))
        temp-ok (and (> temperature 273) (< temperature 373))
        has-carbon (> (get composition :C 0) 0.0001)
        has-nitrogen (> (get composition :N 0) 0.0001)
        pressure-ok (and (> pressure 1000) (< pressure 1e8))]
    (cond
      (and has-water temp-ok has-carbon has-nitrogen pressure-ok) 1.0
      (and has-water (or temp-ok pressure-ok)) 0.5
      has-water 0.2
      :else 0.0)))

;; --- Stellar nucleosynthesis -------------------------------------------------

;; UNUSED-PENDING: Chemistry evolution paths: composition tables and transformation fns exist,
;; but no tick calls them — `domain.genesis/physics-systems-parallel` has no
;; chemistry-evolution emitter yet. CLAUDE.md names this class explicitly.
;; See kanban/tasks/phase-0-chemistry-differentiation-spec.md
#_{:clj-kondo/ignore [:clojure-lsp/unused-public-var]}
(defn fusion-products
  "Calculate composition changes from fusion. Legacy helper; prefer `burn-step`
   for ECS-tick-safe H→He conversion."
  [initial-comp temperature fusion-rate dt]
  (if (> temperature 1e7)
    (let [h-consumed (* fusion-rate dt 0.007)
          he-produced (* h-consumed 0.993)]
      (-> initial-comp
          (update :H #(max 0 (- % h-consumed)))
          (update :He #(+ % he-produced))))
    initial-comp))

;; UNUSED-PENDING: Chemistry evolution paths: composition tables and transformation fns exist,
;; but no tick calls them — `domain.genesis/physics-systems-parallel` has no
;; chemistry-evolution emitter yet. CLAUDE.md names this class explicitly.
;; See kanban/tasks/phase-0-chemistry-differentiation-spec.md
#_{:clj-kondo/ignore [:clojure-lsp/unused-public-var]}
(defn supernova-enrichment
  "Model heavy element enrichment from stellar death."
  [composition stellar-mass]
  (let [metal-factor (math/log10 (/ stellar-mass 1.989e30))]
    (reduce (fn [c el]
              (if-not (#{:H :He} el)
                (update c el #(* % (inc metal-factor)))
                c))
            composition
            (keys composition))))

;; --- Live nucleosynthesis system ---------------------------------------------

(def ^:private main-sequence-lifetime-sun
  "The Sun's main-sequence H-burning lifetime ≈ 10 Gyr, in seconds."
  3.156e17)

(def ^:private max-burn-fraction-per-tick
  "Hard ceiling on the fraction of a body's current H burned in one tick."
  0.01)

(defn burn-step
  "Bounded, dt-correct H→He burn for one tick. Conserves mass fraction."
  [composition mass dt]
  (let [h (double (get composition :H 0.0))]
    (if (pos? h)
      (let [tau-ms (* main-sequence-lifetime-sun
                      (math/pow (/ (double mass) law/solar-mass) -2.5))
            f-burn (min max-burn-fraction-per-tick (/ (double dt) tau-ms))
            dH     (* h f-burn)]
        (-> composition
            (assoc :H (max 0.0 (- h dH)))
            (update :He (fnil + 0.0) dH)))
      composition)))

(defn nucleosynthesis-system
  "Write-set emitter: sole writer of :component/comp.burn."
  [dt]
  {:id     :nucleosynthesis
   :writes #{c/comp-burn}
   :run
   (fn [world]
     (let [eids (ecs/entities-with world c/matter-state c/composition c/mass)
           cell (into {}
                      (keep (fn [eid]
                              (let [state (ecs/get-component world eid c/matter-state)
                                    composition  (ecs/get-component world eid c/composition)
                                    mass  (ecs/get-component world eid c/mass)
                                    temp  (double (or (ecs/get-component world eid c/temperature) 0.0))]
                                (when (and composition mass
                                           (contains? #{:star :protostar} state)
                                           (>= temp law/fusion-temp-threshold))
                                  [eid (burn-step composition mass dt)]))))
                      eids)]
       {c/comp-burn cell}))})
