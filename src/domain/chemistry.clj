(ns domain.chemistry
  "Chemistry and elemental composition for stellar and planetary formation.
   Tracks elemental abundance, molecular formation, and phase transitions.

   Composition is an explicit element map (see `law.composition/element-set`).
   Molecules and bulk categories (gas, rock, ice, metal) are derived on demand.",
  (:require
   [clojure.set]
   [domain.ecs.core        :as ecs]
   [domain.ecs.components   :as c]
   [law.composition         :as lcomp]
   [law.stellar             :as law]))

;; --- Element re-exports -------------------------------------------------------

(defn solar-composition
  "Population-I (solar) element composition map."
  []
  lcomp/solar-composition)

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
            keys (into (set (keys c1)) (keys c2))]
        (reduce (fn [m k]
                  (let [v (+ (* m1 (double (get c1 k 0.0)))
                             (* m2 (double (get c2 k 0.0))))]
                    (assoc m k (* v inv))))
                {} keys))
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

(defn partition-solids
  "Partition a composition map into solid and gas phases at `temperature` (K)
   using the Lodders condensation sequence (law.composition/condensation-
   temperatures). Returns `{:solid element-map :gas element-map}` with both
   maps normalized independently so each sums to 1.0."
  [composition temperature]
  (let [temp (double temperature)
        [solid gas]
        (reduce-kv (fn [[s g] k v]
                     (let [tc (double (get lcomp/condensation-temperatures k 50.0))]
                       (if (< temp tc)
                         [(assoc s k v) g]
                         [s (assoc g k v)])))
                   [{} {}]
                   composition)]
    {:solid (lcomp/normalize solid)
     :gas   (lcomp/normalize gas)}))

(defn bulk-categories
  "Return the fractional bulk categories `{:gas :rock :metal :ice}` for a
   composition at `temperature`, normalized to sum to 1.0.

   Each element's mass fraction is classed by whether it is condensed at
   `temperature` (Lodders `Tc`): condensed C/N/O → ice, Fe/Ni → metal, other
   rock-formers → rock; everything gaseous (and condensed gas-formers like frozen
   H/He/Ne, which have no solid category) → gas. Derives fractions from the
   original composition — NOT from `partition-solids`, whose :solid/:gas maps are
   each independently normalized and so cannot report the solid/gas split."
  [composition temperature]
  (let [temp  (double temperature)
        total (double (reduce + 0.0 (vals composition)))
        classify (fn [k]
                   (let [tc (double (get lcomp/condensation-temperatures k 50.0))]
                     (if (< temp tc)
                       (cond
                         (contains? lcomp/ice-formers k)  :ice
                         (contains? #{:Fe :Ni} k)         :metal
                         (contains? lcomp/rock-formers k) :rock
                         :else                            :gas) ;; frozen gas-former
                       :gas)))
        sums (reduce-kv (fn [m k v] (update m (classify k) + (double v)))
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

(defn material-phase
  "Determine phase of material (gas, liquid, solid) based on conditions.
   This is a rough element-by-element estimate; `partition-solids` is preferred
   for composition-wide condensation."
  [element temperature pressure]
  (let [props (get element-properties element)
        mp (:melting-point props)
        bp (:boiling-point props)
        pressure-factor (Math/log10 (max 1 (/ pressure 101325)))]
    (cond
      (< temperature (* mp (+ 1 (* 0.1 pressure-factor)))) :solid
      (> temperature (* bp (+ 1 (* 0.1 pressure-factor)))) :gas
      :else :liquid)))

;; --- Molecular formation (derived) ------------------------------------------

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

(defn escape-velocity
  "Calculate escape velocity for a body."
  [mass radius]
  (Math/sqrt (/ (* 2 6.674e-11 mass) radius)))

(defn can-retain-gas?
  "Check if body can retain a gas based on temperature and escape velocity."
  [body-mass body-radius gas-element temperature]
  (let [v-escape (escape-velocity body-mass body-radius)
        molecular-mass (get-in element-properties [gas-element :mass] 1.0)
        v-thermal (Math/sqrt (/ (* 3 1.38e-23 temperature)
                                (* molecular-mass 1.66e-27)))
        jeans-parameter (/ v-escape v-thermal)]
    (> jeans-parameter 6)))

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

(defn differentiate-composition
  "Model gravitational differentiation of a molten/hot body."
  [composition temperature _radius]
  (if (> temperature 1500)
    {:core (select-keys composition [:Fe :Ni])
     :mantle (select-keys composition [:Si :Mg :O :Al])
     :crust (select-keys composition [:Si :O :Al :Ca :Na])
     :atmosphere (select-keys composition [:H :He :N :O])}
    {:mixed composition}))

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

(defn supernova-enrichment
  "Model heavy element enrichment from stellar death."
  [composition stellar-mass]
  (let [metal-factor (Math/log10 (/ stellar-mass 1.989e30))]
    (reduce (fn [c el]
              (if (not (#{:H :He} el))
                (update c el #(* % (+ 1 metal-factor)))
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
                      (Math/pow (/ (double mass) law/solar-mass) -2.5))
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
                                    comp  (ecs/get-component world eid c/composition)
                                    mass  (ecs/get-component world eid c/mass)
                                    temp  (double (or (ecs/get-component world eid c/temperature) 0.0))]
                                (when (and comp mass
                                           (contains? #{:star :protostar} state)
                                           (>= temp law/fusion-temp-threshold))
                                  [eid (burn-step comp mass dt)]))))
                      eids)]
       {c/comp-burn cell}))})
