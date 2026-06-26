(ns domain.chemistry
  "Chemistry and elemental composition for stellar and planetary formation.
   Tracks elemental abundance, molecular formation, and phase transitions.")

;; --- Elements and Abundance ---

(def solar-abundance
  "Solar system elemental abundance by mass fraction"
  {:H   0.7346   ;; Hydrogen
   :He  0.2485   ;; Helium  
   :O   0.00592  ;; Oxygen
   :C   0.00240  ;; Carbon
   :Ne  0.00176  ;; Neon
   :Fe  0.00130  ;; Iron
   :N   0.00070  ;; Nitrogen
   :Si  0.00065  ;; Silicon
   :Mg  0.00058  ;; Magnesium
   :S   0.00044  ;; Sulfur
   :Ar  0.00008  ;; Argon
   :Ca  0.00006  ;; Calcium
   :Al  0.00005  ;; Aluminum
   :Na  0.00004  ;; Sodium
   :Ni  0.00003}) ;; Nickel

(def element-properties
  "Physical properties of elements"
  {:H  {:mass 1.008   :boiling-point 20.28   :melting-point 14.01}
   :He {:mass 4.003   :boiling-point 4.22    :melting-point 0.95}
   :O  {:mass 15.999  :boiling-point 90.20   :melting-point 54.36}
   :C  {:mass 12.011  :boiling-point 3915    :melting-point 3550}
   :N  {:mass 14.007  :boiling-point 77.36   :melting-point 63.15}
   :Ne {:mass 20.180  :boiling-point 27.07   :melting-point 24.56}
   :Si {:mass 28.085  :boiling-point 3538    :melting-point 1687}
   :Fe {:mass 55.845  :boiling-point 3134    :melting-point 1811}
   :Mg {:mass 24.305  :boiling-point 1363    :melting-point 923}
   :S  {:mass 32.06   :boiling-point 717.8   :melting-point 388.4}})

;; --- Molecular Formation ---

(defn can-form-molecules?
  "Check if temperature allows molecular formation"
  [temperature element1 element2]
  (let [bond-energy-scale 5000] ;; Rough scale for molecular bonds in K
    (< temperature bond-energy-scale)))

(defn molecular-composition
  "Calculate molecular composition based on temperature and elements"
  [elemental-comp temperature pressure]
  (cond
    ;; Very hot - everything is atomic/ionized
    (> temperature 3000)
    elemental-comp
    
    ;; Warm - simple molecules form
    (> temperature 1000)
    (-> elemental-comp
        (assoc :H2 (* 0.5 (:H elemental-comp 0)))
        (update :H #(* % 0.5)))
    
    ;; Cool - complex molecules and ices possible
    :else
    (let [h (:H elemental-comp 0)
          o (:O elemental-comp 0)
          c (:C elemental-comp 0)
          n (:N elemental-comp 0)]
      (merge elemental-comp
             {:H2O (min (* h 0.5) (* o 2))  ;; Water
              :CO2 (min c (* o 0.5))        ;; Carbon dioxide
              :NH3 (min n (* h 0.33))       ;; Ammonia
              :CH4 (min c (* h 0.25))       ;; Methane
              }))))

;; --- Phase Determination ---

(defn material-phase
  "Determine phase of material (gas, liquid, solid) based on conditions"
  [element temperature pressure]
  (let [props (get element-properties element)
        mp (:melting-point props)
        bp (:boiling-point props)
        ;; Pressure adjustment (simplified Clausius-Clapeyron)
        pressure-factor (Math/log10 (max 1 (/ pressure 101325)))]
    (cond
      (< temperature (* mp (+ 1 (* 0.1 pressure-factor)))) :solid
      (> temperature (* bp (+ 1 (* 0.1 pressure-factor)))) :gas
      :else :liquid)))

(defn bulk-composition-category
  "Categorize a body based on its bulk composition"
  [composition]
  (let [volatiles (+ (get composition :H 0)
                     (get composition :He 0)
                     (get composition :H2O 0)
                     (get composition :NH3 0)
                     (get composition :CH4 0))
        rocks (+ (get composition :Si 0)
                (get composition :O 0)
                (get composition :Mg 0)
                (get composition :Fe 0)
                (get composition :Al 0)
                (get composition :Ca 0))
        metals (+ (get composition :Fe 0)
                 (get composition :Ni 0))
        total (reduce + (vals composition))]
    (cond
      (> (/ volatiles total) 0.9) :gas-giant
      (> (/ volatiles total) 0.5) :ice-giant
      (> (/ rocks total) 0.7) :rocky
      (> (/ metals total) 0.3) :metallic
      :else :mixed)))

;; --- Atmospheric Retention ---

(defn escape-velocity
  "Calculate escape velocity for a body"
  [mass radius]
  (Math/sqrt (/ (* 2 6.674e-11 mass) radius)))

(defn can-retain-gas?
  "Check if body can retain a gas based on temperature and escape velocity"
  [body-mass body-radius gas-element temperature]
  (let [v-escape (escape-velocity body-mass body-radius)
        molecular-mass (get-in element-properties [gas-element :mass] 1.0)
        ;; Maxwell-Boltzmann thermal velocity
        v-thermal (Math/sqrt (/ (* 3 1.38e-23 temperature) 
                               (* molecular-mass 1.66e-27)))
        ;; Jeans parameter - need v_escape > 6 * v_thermal for long-term retention
        jeans-parameter (/ v-escape v-thermal)]
    (> jeans-parameter 6)))

(defn potential-atmosphere
  "Determine what atmosphere a body can retain"
  [body-mass body-radius composition temperature]
  (reduce (fn [atmo element]
            (if (and (> (get composition element 0) 0.001)
                    (can-retain-gas? body-mass body-radius element temperature))
              (assoc atmo element (get composition element))
              atmo))
          {}
          [:H :He :N :O :Ne :Ar :H2O :CO2 :NH3 :CH4]))

;; --- Differentiation ---

(defn differentiate-composition
  "Model gravitational differentiation of a molten/hot body"
  [composition temperature radius]
  (if (> temperature 1500) ;; Hot enough for differentiation
    {:core (select-keys composition [:Fe :Ni])
     :mantle (select-keys composition [:Si :Mg :O :Al])
     :crust (select-keys composition [:Si :O :Al :Ca :Na])
     :atmosphere (select-keys composition [:H :He :N :O :H2O :CO2])}
    {:mixed composition}))

;; --- Prebiotic Chemistry ---

(defn habitability-score
  "Calculate rough habitability potential"
  [{:keys [temperature pressure composition atmosphere]}]
  (let [has-water (> (get composition :H2O 0) 0.001)
        temp-ok (and (> temperature 273) (< temperature 373))
        has-carbon (> (+ (get composition :C 0)
                        (get composition :CO2 0)
                        (get composition :CH4 0)) 0.0001)
        has-nitrogen (> (+ (get composition :N 0)
                          (get composition :NH3 0)) 0.0001)
        pressure-ok (and (> pressure 1000) (< pressure 1e8))]
    (cond
      (and has-water temp-ok has-carbon has-nitrogen pressure-ok) 1.0
      (and has-water (or temp-ok pressure-ok)) 0.5
      has-water 0.2
      :else 0.0)))

;; --- Stellar Nucleosynthesis ---

(defn fusion-products
  "Calculate composition changes from fusion"
  [initial-comp temperature fusion-rate dt]
  (if (> temperature 1e7)
    (let [;; Simplified hydrogen burning (PP chain)
          h-consumed (* fusion-rate dt 0.007) ;; mass deficit
          he-produced (* h-consumed 0.993)]
      (-> initial-comp
          (update :H #(max 0 (- % h-consumed)))
          (update :He #(+ % he-produced))))
    initial-comp))

(defn supernova-enrichment
  "Model heavy element enrichment from stellar death"
  [composition stellar-mass]
  ;; More massive stars produce more metals
  (let [metal-factor (Math/log10 (/ stellar-mass 1.989e30))]
    (reduce (fn [comp element]
              (if (not (#{:H :He} element))
                (update comp element #(* % (+ 1 metal-factor)))
                comp))
            composition
            (keys composition))))