(ns domain.planet-formation.seed
  "Seeding context, annuli physics, viability filtering, and the planet-seeds
   orchestration for the core-accretion planet seeder (Part 4 of the Genesis
   Formation spec)."
  (:require
   [clojure.math :as math]
   [law.stellar :as law]
   [law.composition :as lcomp]
   [domain.ecs.core :as ecs]
   [domain.ecs.components :as c]
   [domain.planet-formation.physics :as pfph]
   [domain.planet-formation.composition :as pfc]
   [domain.planet-formation.orbit :as pfo]
   [shape.spatial :as sp]))

(def ^:const disk-maturity-seconds
  "Disk age (seconds) after which core-accretion planet seeding is allowed."
  3.156e13) ;; 1 Myr default

(def ^:const min-planet-orbit-radius-au
  "Inner cutoff for planet seeding, in AU."
  0.1)

(def ^:const planet-seeding-outer-au
  "Outer edge of the planet-seeding disk, in AU; normalized to a Neptune-like extent."
  30.0)

(def ^:const planet-seeding-annuli
  "Number of logarithmic annuli used to sample the disk for planet seeds."
  12)

(defn- make-annuli
  "Build logarithmically spaced annuli between r-in and r-out."
  [r-in r-out]
  (let [log-min (math/log10 r-in)
        log-max (math/log10 (max r-in r-out))]
    (vec (for [i (range planet-seeding-annuli)]
           (let [a0 (math/pow 10.0 (+ log-min (* i (/ (- log-max log-min) planet-seeding-annuli))))
                 a1 (math/pow 10.0 (+ log-min (* (inc i) (/ (- log-max log-min) planet-seeding-annuli))))
                 mid (* 0.5 (+ a0 a1))]
             {:r-inner a0 :r-outer a1 :r mid})))))

(defn- seed-context
  "Return the full seeding context for star/world, or nil if seeding should not run."
  [world star]
  (let [disk-age (- (:genesis/sim-time world 0.0)
                    (:genesis/star-ignition-time world 0.0))
        maturity (double (or (:genesis/disk-maturity world) disk-maturity-seconds))
        already? (boolean (ecs/get-component world star c/planets-seeded))
        M-star (double (or (ecs/get-component world star c/mass) 0.0))
        L-star (double (or (ecs/get-component world star c/luminosity) 0.0))
        disk-m (double (or (ecs/get-component world star c/disk-mass) 0.0))
        disk-L (or (ecs/get-component world star c/disk-angular-mom) [0.0 0.0 0.0])
        star-pos (or (ecs/get-component world star c/position) [0.0 0.0 0.0])
        star-v (or (ecs/get-component world star c/velocity) [0.0 0.0 0.0])
        star-axis (or (ecs/get-component world star c/rotation-axis) [0.0 0.0 1.0])]
    (when (and (not already?)
               (> disk-age maturity)
               (pos? M-star)
               (pos? disk-m))
      (let [disk-regime (or (ecs/get-component world star c/disk-regime)
                            {:solid-surface-density 0.0 :snow-line (pfph/snow-line-radius L-star)})
            snow-line (double (:snow-line disk-regime))
            disk-composition (or (ecs/get-component world star c/composition)
                                 lcomp/solar-composition)
            Z (lcomp/metallicity disk-composition)
            r-in (max (* min-planet-orbit-radius-au law/au)
                      (* 3.0 (double (or (ecs/get-component world star c/radius) 1.0e9))))
            r-out (* planet-seeding-outer-au law/au)]
         {:disk-age disk-age :maturity maturity :M-star M-star :L-star L-star
          :disk-m disk-m :disk-L disk-L :star-pos star-pos :star-v star-v
          :star-axis star-axis :snow-line snow-line :Z Z :r-in r-in :r-out r-out
          :disk-composition disk-composition
          :s0 (pfph/mmsn-sigma0 disk-m r-in r-out)
          :annuli (make-annuli r-in r-out)}))))

(defn- annulus-physics
  "Compute physical quantities for one annulus: area, surface densities,
   accretion time, and core mass."
  [ann M-star s0 snow-line Z]
  (let [r (double (:r ann))
        area (* math/PI (- (* (:r-outer ann) (:r-outer ann))
                           (* (:r-inner ann) (:r-inner ann))))
        sigma-gas (pfph/mmsn-sigma s0 r)
        sigma-solid (pfph/solid-surface-density sigma-gas r snow-line Z)
        tau (pfph/core-accretion-timescale r sigma-solid M-star)
        beyond? (> r snow-line)
        m-iso (law/isolation-mass r sigma-solid M-star)
        core-m (min m-iso (* sigma-solid area))]
    {:r r :area area :sigma-gas sigma-gas :sigma-solid sigma-solid
     :tau tau :beyond? beyond? :core-m core-m}))

(defn- seed-viable?
  "True when an annulus has enough solids, can accrete in time, and is spaced
   from already seeded radii."
  [{:keys [sigma-solid tau core-m r]} disk-age occupied]
  (and (pos? sigma-solid)
       (< tau disk-age)
       (>= core-m (* pfph/min-seed-mass-solar law/solar-mass))
       (every? #(> (abs (- (math/log10 r) (math/log10 %))) 0.15) occupied)))

(defn planet-seeds
  "Compute planet seed specs and disk debit for a star+disk.

   Returns {:spawns [[star-eid spec] ...] :disk-m disk-m' :disk-L disk-L'}
   or nil if seeding conditions are not met.

   `world` is the frozen snapshot; `star` is the star entity id."
  [world star]
  (when-let [ctx (seed-context world star)]
    (let [{:keys [M-star L-star disk-m disk-L star-pos star-v star-axis
                  snow-line Z s0 annuli disk-age disk-composition]} ctx]
      (loop [anns annuli
             spawns []
             disk-m' disk-m
             disk-L' disk-L
             occupied []]
        (if (empty? anns)
          {:spawns spawns :disk-m disk-m' :disk-L disk-L'}
          (let [ann (first anns)
                phys (annulus-physics ann M-star s0 snow-line Z)
                r (:r phys)]
            (if (seed-viable? phys disk-age occupied)
              (let [{:keys [mass-kg core-m gas-m]} (pfph/planet-mass phys disk-m')
                    ptype (pfc/planet-type r (:sigma-solid phys) snow-line (/ mass-kg law/solar-mass))
                    ;; Local midplane temperature at the formation radius: the
                    ;; same blackbody the snow-line model uses (albedo 0), so
                    ;; T(r) = 170 K exactly at the snow line and the condensed
                    ;; inventory flips ice-bearing there (decision §9.1).
                    disk-temp (pfph/equilibrium-temperature L-star r 0.0)
                    composition (pfc/planet-composition disk-composition disk-temp core-m gas-m)
                    spec (pfo/build-planet-spec {:r r :mass-kg mass-kg :ptype ptype
                                                 :composition composition
                                                 :tick (:tick world) :star star}
                                                {:L-star L-star :pos star-pos :vel star-v
                                                 :axis star-axis :M-star M-star
                                                 :softening (:sim/softening world)})
                    L-removed (sp/v* disk-L' (/ mass-kg (max 1.0 disk-m')))]
                (recur (rest anns)
                       (conj spawns [star spec])
                       (- disk-m' mass-kg)
                       (sp/v- disk-L' L-removed)
                       (conj occupied r)))
              (recur (rest anns) spawns disk-m' disk-L' occupied))))))))
