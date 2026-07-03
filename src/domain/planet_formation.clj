(ns domain.planet-formation
  "Planet sub-grid seeder (Part 4 of the Genesis Formation spec).

   Core-accretion prescription on a protoplanetary disk's solid surface density.
   Pure functions only — the caller (disk-evolution-system) owns the disk-mass
   and disk-angular-mom components and materializes the spawn requests."
  (:require
   [law.stellar           :as law]
   [law.composition       :as lcomp]
   [domain.ecs.core       :as ecs]
   [domain.ecs.components  :as c]
   [shape.spatial         :as sp]))

(def ^:const snow-line-temperature 170.0)
(def ^:const proto-solar-metal-frac 0.015)
(def ^:const ice-enhancement-factor 3.5)
(def ^:const min-planet-orbit-radius-au 0.1)
(def ^:const planet-seeding-annuli 12)
(def ^:const min-seed-mass-solar 1.0e-6)
(def ^:const max-seed-mass-solar 13.0)
(def ^:const disk-maturity-seconds 3.156e13) ;; 1 Myr default

(defn- unit [v]
  (let [l (sp/len v)] (if (pos? l) (sp/v* v (/ 1.0 l)) v)))

(defn sound-speed
  "Adiabatic sound speed c_s = √(γ k_B T / m_H) for a thin disc. m/s."
  [temperature]
  (if (pos? (double temperature))
    (Math/sqrt (/ (* 1.6666667 law/k-B (double temperature)) law/m-H))
    0.0))

(defn snow-line-radius
  "Radius where equilibrium T = 170 K for a blackbody at luminosity L:
   r = sqrt(L / (16 π σ T⁴)). Beyond it, water ice condenses and the solid
   surface density jumps ~3.5×."
  [luminosity]
  (let [T snow-line-temperature]
    (Math/sqrt (/ (double luminosity)
                  (* 16.0 Math/PI law/stefan-boltzmann (Math/pow T 4))))))

(defn solid-surface-density
  "Solid (dust+ice) surface density at radius r: Σ_gas·Z, ice-enhanced beyond
   the snow line by ~3.5×. Z = metal fraction (~0.015 proto-solar)."
  [sigma-gas r snow-line metal-frac]
  (* sigma-gas (double metal-frac)
     (if (> (double r) (double snow-line)) ice-enhancement-factor 1.0)))

(defn core-accretion-timescale
  "Time to build a ~10 M⊕ core at r (Pollack 1996 parameterization): τ ∝
   1/Σ_solid, scaled by orbital period. Returns seconds."
  [r sigma-solid star-mass]
  (let [r-m (double (or r 0.0))
        sig (double (or sigma-solid 0.0))
        M   (double (or star-mass 0.0))]
    (if (and (pos? r-m) (pos? sig) (pos? M))
      (let [period (* 2.0 Math/PI (Math/sqrt (/ (* r-m r-m r-m) (* law/G M))))]
        (* period (/ 1.0 (* 0.01 sig 1.0e5)))) ;; calibrated: Σ in kg/m²
      Double/POSITIVE_INFINITY)))

(defn planet-type
  "Classify a seeded planet by location and solid surface density.
     :terrestrial  — inside snow line, rocky
     :ice-giant    — beyond snow line, moderate mass
     :gas-giant    — beyond snow line, runaway gas capture possible"
  [r sigma-solid snow-line mass-solar]
  (let [beyond? (> (double r) (double snow-line))]
    (cond
      (and beyond? (> mass-solar 0.3)) :gas-giant
      beyond?                          :ice-giant
      :else                            :terrestrial)))

(defn planet-composition
  "Return a plausible composition map for a planet type."
  [ptype]
  (case ptype
    :terrestrial {:H 0.0 :He 0.0 :metals 0.40 :silicates 0.45 :volatiles 0.15}
    :ice-giant   {:H 0.15 :He 0.05 :metals 0.10 :silicates 0.20 :ices 0.50}
    :gas-giant   {:H 0.70 :He 0.28 :metals 0.02 :silicates 0.0 :ices 0.0}
    {:H lcomp/primordial-H :He lcomp/primordial-He :metals 0.0}))

(defn planet-material-density-by-type
  "Mean material density (kg/m³) for a planet type."
  [ptype]
  (case ptype
    :terrestrial 5.0e3
    :ice-giant   1.6e3
    :gas-giant   1.0e3
    1.0e3))

(defn- sphere-radius
  "Radius of a uniform sphere of `mass` at material `density`."
  [mass density]
  (Math/pow (/ (* 3.0 (double mass)) (* 4.0 Math/PI (double density))) (/ 1.0 3.0)))

(defn- orbital-angular-momentum
  "Orbital specific angular momentum L = m (r × v). Vector in kg m²/s."
  [mass position velocity]
  (let [[x y z] position
        [vx vy vz] velocity
        m (double mass)]
    [(* m (- (* y vz) (* z vy)))
     (* m (- (* z vx) (* x vz)))
     (* m (- (* x vy) (* y vx)))]))

(defn- hash01
  "Deterministic [0,1) value from an integer key."
  [n]
  (/ (double (mod (* (+ 1 (long n)) 2654435761) 1000003)) 1000003.0))

(defn planet-seeds
  "Compute planet seed specs and disk debit for a star+disk.

   Returns {:spawns [[star-eid spec] ...] :disk-m disk-m' :disk-L disk-L'}
   or nil if seeding conditions are not met.

   `world` is the frozen snapshot; `star` is the star entity id."
  [world star]
  (let [disk-age  (- (:genesis/sim-time world 0.0)
                     (:genesis/star-ignition-time world 0.0))
        maturity  (double (or (:genesis/disk-maturity world) disk-maturity-seconds))
        already?  (boolean (ecs/get-component world star c/planets-seeded))
        M-star    (double (or (ecs/get-component world star c/mass) 0.0))
        L-star    (double (or (ecs/get-component world star c/luminosity) 0.0))
        disk-m    (double (or (ecs/get-component world star c/disk-mass) 0.0))
        disk-L    (or (ecs/get-component world star c/disk-angular-mom) [0.0 0.0 0.0])
        star-pos  (or (ecs/get-component world star c/position) [0.0 0.0 0.0])
        star-v    (or (ecs/get-component world star c/velocity) [0.0 0.0 0.0])
        star-axis (or (ecs/get-component world star c/rotation-axis) [0.0 0.0 1.0])]
    (when (and (not already?)
               (> disk-age maturity)
               (pos? M-star)
               (pos? disk-m))
      (let [disc-bodies (filterv
                         #(= :disc (ecs/get-component world % c/disc-tag))
                         (ecs/entities-with world c/disc-tag c/position c/mass))
            snow-line   (snow-line-radius L-star)
            r-in        (max (* min-planet-orbit-radius-au law/au)
                             (* 3.0 (double (or (ecs/get-component world star c/radius) 1.0e9))))
            r-out       (if (seq disc-bodies)
                          (max (* 1.5 r-in)
                               (reduce max 0.0
                                       (map #(sp/dist star-pos (ecs/get-component world % c/position))
                                            disc-bodies)))
                          (* 5.0 law/au))
            log-min     (Math/log10 r-in)
            log-max     (Math/log10 (max r-in r-out))
            annuli      (vec (for [i (range planet-seeding-annuli)]
                               (let [a0 (Math/pow 10.0 (+ log-min (* i (/ (- log-max log-min) planet-seeding-annuli))))
                                     a1 (Math/pow 10.0 (+ log-min (* (inc i) (/ (- log-max log-min) planet-seeding-annuli))))
                                     mid (* 0.5 (+ a0 a1))]
                                 {:r-inner a0 :r-outer a1 :r mid})))
            in-annulus? (fn [pos ann]
                          (let [d (sp/dist star-pos pos)]
                            (and (>= d (:r-inner ann)) (< d (:r-outer ann)))))]
        (loop [anns annuli
               spawns []
               disk-m' disk-m
               disk-L' disk-L
               occupied []]
          (if (empty? anns)
            {:spawns spawns :disk-m disk-m' :disk-L disk-L'}
            (let [ann       (first anns)
                  bodies    (filterv #(in-annulus? (ecs/get-component world % c/position) ann) disc-bodies)
                  ann-mass  (reduce + 0.0 (map #(double (or (ecs/get-component world % c/mass) 0.0)) bodies))
                  area      (* Math/PI (- (* (:r-outer ann) (:r-outer ann))
                                          (* (:r-inner ann) (:r-inner ann))))
                  sigma-gas (if (pos? area) (/ ann-mass area) 0.0)
                  sigma-solid (solid-surface-density sigma-gas (:r ann) snow-line proto-solar-metal-frac)
                  tau       (core-accretion-timescale (:r ann) sigma-solid M-star)
                  min-core-m (* 1.0e24 (Math/pow (max 0.1 sigma-solid) 1.5))
                  enough?   (and (pos? sigma-solid)
                                 (< tau disk-age)
                                 (>= ann-mass min-core-m))
                  spaced?   (every? #(> (Math/abs (- (Math/log10 (:r ann)) (Math/log10 %))) 0.15)
                                    occupied)]
              (if (and enough? spaced?)
                (let [mass-kg (min (* 0.3 ann-mass)
                                   (* max-seed-mass-solar law/solar-mass)
                                   disk-m')
                      mass-kg (max mass-kg (* min-seed-mass-solar law/solar-mass))
                      ptype   (planet-type (:r ann) sigma-solid snow-line (/ mass-kg law/solar-mass))
                      dens    (planet-material-density-by-type ptype)
                      rad     (sphere-radius mass-kg dens)
                      phase   (* 2.0 Math/PI (hash01 (hash [star (:r ann) (:tick world)])))
                      ;; Circular speed in the SOFTENED field the integrator
                      ;; applies. AU-scale orbits sit far inside the Plummer
                      ;; length, where unsoftened Kepler (~30 km/s at 1 AU)
                      ;; would launch every planet straight out of the system.
                      v-circ  (law/softened-circular-speed
                               M-star (:r ann) (:sim/softening world))
                      e1      (unit (sp/cross star-axis [1.0 0.0 0.0]))
                      e1'     (if (pos? (sp/len e1)) e1 [0.0 1.0 0.0])
                      e2      (sp/cross e1' (unit star-axis))
                      pos     (sp/v+ star-pos
                                     (sp/v+ (sp/v* e1' (* (:r ann) (Math/cos phase)))
                                            (sp/v* e2 (* (:r ann) (Math/sin phase)))))
                      vel     (sp/v+ star-v
                                     (sp/v+ (sp/v* e1' (* (- v-circ) (Math/sin phase)))
                                            (sp/v* e2 (* v-circ (Math/cos phase)))))
                      spec    {:position pos
                               :velocity vel
                               :mass mass-kg
                               :radius rad
                               :matter-state :planet
                               :body-kind :body/planet
                               :planet-type ptype
                               :composition (planet-composition ptype)
                               :temperature 250.0
                               :extra-components {c/planet-type ptype
                                                  c/angular-momentum
                                                  (orbital-angular-momentum mass-kg
                                                                            (sp/v- pos star-pos)
                                                                            (sp/v- vel star-v))}}
                      L-removed (sp/v* disk-L' (/ mass-kg (max 1.0 disk-m')))]
                  (recur (rest anns)
                         (conj spawns [star spec])
                         (- disk-m' mass-kg)
                         (sp/v- disk-L' L-removed)
                         (conj occupied (:r ann))))
                (recur (rest anns) spawns disk-m' disk-L' occupied)))))))))
