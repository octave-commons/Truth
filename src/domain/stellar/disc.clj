(ns domain.stellar.disc
  "Disc identification, kinematics, and stability diagnostics.
   Tags non-star bodies relative to the central star as :disc, :envelope, or
   :outflow, and provides pure helpers for disk radius, viscous timescale,
   Toomre Q, and fragmentation criteria."
  (:require
   [clojure.math :as math] [law.stellar                   :as law]
   [law.composition               :as lcomp]
   [domain.planet-formation       :as pf]
   [domain.ecs.core               :as ecs]
   [domain.ecs.components         :as c]
   [domain.stellar.thermodynamics :as thermo]
   [shape.spatial                 :as sp]))

(defn- unit [v]
  (let [l (sp/len v)] (if (pos? l) (sp/v* v (/ 1.0 l)) v)))

(defn disc-classify
  "Classify a body's kinematic relationship to a central star as one of:
     :disc      — rotationally supported (v_tang > 2|v_rad|, h/r < 0.3, bound)
     :envelope  — radially infalling, still gravitationally bound
     :outflow   — unbound / hyperbolic relative to the star
     nil        — central star itself or missing data

   Uses the region map from `thermo/entity->region` plus the central star's
   position, velocity, and mass. The h/r estimate is taken from oblateness
   (c/a ≈ 1 - h/r for a thin disc)."
  [region central-star]
  (let [{:keys [position velocity mass matter-state]} region
        {:keys [star-pos star-v star-m]} central-star
        valid-vec? (fn [v] (and (vector? v) (= 3 (count v)) (every? number? v)))]
    (when (and (valid-vec? position)
               (valid-vec? velocity)
               (valid-vec? star-pos)
               (valid-vec? star-v)
               (number? star-m)
               (pos? (double star-m))
               (not= matter-state :star))
      (let [r  (sp/v- position star-pos)
            v  (sp/v- velocity star-v)
            d  (sp/len r)]
        (when (pos? d)
          (let [rhat    (unit r)
                vr      (sp/dot v rhat)
                v-perp  (sp/v- v (sp/v* rhat vr))
                vt      (sp/len v-perp)
                v2      (sp/len2 v)
                mu      (* law/G (+ (double (or mass 0.0)) star-m))
                grav-bound?  (<= v2 (/ (* 2.0 mu) d))
                h-over-r (max 0.0 (min 1.0 (- 1.0 (double (or (:oblateness region) 1.0)))))]
            (cond
              (not grav-bound?)                :outflow
              (and (> vt (* 2.0 (abs vr)))
                   (< h-over-r 0.3))      :disc
              :else                       :envelope)))))))

(defn in-disc?
  "True if `eid` currently carries the `:disc` tag."
  [world eid]
  (= :disc (ecs/get-component world eid c/disc-tag)))

(defn disc-identification-system
  "Double-buffer write-set system: SOLE writer of c/disc-tag.

   Tags every non-star body relative to the most massive :star or :protostar
   in the world. Runs after the regime-system so regime tags are available,
   but disc-tag is independent and has its own single-writer column."
  []
  {:id     :disc-identification
   :writes #{c/disc-tag}
   :run    (fn [world]
             (let [candidates (filterv #(let [s (ecs/get-component world % c/matter-state)]
                                          (or (= s :star) (= s :protostar)))
                                       (ecs/entities-with world c/matter-state c/mass))
                   central    (when (seq candidates)
                                (apply max-key #(ecs/get-component world % c/mass) candidates))
                   central-star (when central
                                  {:star-pos (ecs/get-component world central c/position)
                                   :star-v   (ecs/get-component world central c/velocity)
                                   :star-m   (double (or (ecs/get-component world central c/mass) 0.0))})
                   eids       (ecs/entities-with world c/matter-state c/position c/velocity c/mass)]
               (if-not central
                 {c/disc-tag {}}
                 {c/disc-tag
                  (into {}
                        (keep (fn [eid]
                                (let [region (thermo/entity->region world eid)
                                      region (assoc region :oblateness
                                                    (or (ecs/get-component world eid c/oblateness) 1.0))]
                                  (when-let [tag (disc-classify region central-star)]
                                    [eid tag]))))
                        eids)})))})

;; --- Disk geometry and stability diagnostics ---------------------------------

(defn disk-radius
  "Outer radius (m) of a centrifugally-supported disk from specific angular
   momentum: r_disk = j² / (G M). The disk forms where rotation balances gravity."
  [specific-angular-momentum mass]
  (let [j (double (or specific-angular-momentum 0.0))
        M (double (or mass 0.0))]
    (if (and (pos? j) (pos? M))
      (/ (* j j) (* law/G M))
      0.0)))

(def ^:const disk-viscous-alpha
  "Shakura-Sunyaev viscosity parameter. When the disk is self-gravitating,
   gravitoturbulence and global spiral modes can drive effective α up to ~0.1.
   We use 0.05 so the disk drains onto the star faster than the old 0.01 value,
   but not so fast that the disk vanishes in one tick."
  0.05)

(def ^:const disk-sound-speed
  "Characteristic sound speed in a protoplanetary disk (m/s). ~300 m/s at 1 AU."
  300.0)

(def ^:const disk-outer-temperature
  "Characteristic temperature (K) of the outer disk annulus used for Toomre Q
   and cooling-time estimates."
  100.0)

(def ^:const disk-radius-max
  "Maximum outer radius used for viscous-disk physics. Real disks rarely exceed
   ~1000 AU before they become gravitationally unstable or are truncated by the
   surrounding cloud; allowing the disk radius to grow to the full centrifugal
   radius from a 2e16 m nebula produces a multi-Gyr viscous timescale and stalls
   star growth."
  1.5e14) ;; 1000 AU

(defn disk-viscous-timescale
  "Viscous timescale (s) for a protoplanetary disk: t_visc = R² / (α c_s H)
   where H = c_s/Ω is the disk scale height. For a Keplerian disk at radius R:
   t_visc ~ R² / (α c_s² / Ω_K) ~ R^(3/2) / (α c_s²) × √(G M)."
  [dsk-rad mass]
  (let [R (double (min disk-radius-max (or dsk-rad 0.0)))
        M (double (or mass 0.0))]
    (if (and (pos? R) (pos? M))
      ;; t_visc = R^(3/2) / (α × c_s^2) × √(G M / R)  ≈  R² / (α × c_s × H)
      ;; Simplified: t_visc ~ 1e6 yr × (R/AU)^1.5 × (M_sun/M)^0.5
      (let [R-au (/ R 1.5e11)
            M-msun (/ M thermo/solar-mass-kg)]
        (* 1.0e6 3.15e7 ;; 1 Myr in seconds
           (math/pow (max 0.01 R-au) 1.5)
           (math/pow (max 0.01 M-msun) -0.5)
           (/ 0.01 disk-viscous-alpha)))
      1.0e13))) ;; fallback: ~300 kyr

(def ^:const min-fragment-orbit-periods
  "A fragment (planet embryo / binary companion) must be placed on an orbit whose
   period spans at least this many integration steps. Below it the leapfrog step
   (`x' = x + v·dt`) overshoots the whole orbit in one tick, so the integrator
   flings the fragment off on a near-straight line at its Keplerian speed instead
   of letting it orbit — the 'debris flung everywhere' ejection. 50 steps/orbit
   keeps the orbit resolved and the fragment bound." 50.0)

(defn resolvable-orbit-radius
  "Smallest orbital radius around mass `M` whose period is ≥ `min-periods`·`dt`.
   T = 2π√(r³/GM) ≥ min-periods·dt  ⇒  r ≥ ∛(GM·(min-periods·dt / 2π)²). A fragment
   placed at this radius (on a circular orbit) is bound AND resolvable at the
   current timestep, so it stays in the system rather than being ejected."
  [M dt min-periods]
  (let [M  (double (or M 0.0))
        dt (double (or dt 0.0))
        k  (double (or min-periods 1.0))]
    (if (and (pos? M) (pos? dt))
      (math/cbrt (* law/G M (math/pow (/ (* k dt) (* 2.0 math/PI)) 2)))
      0.0)))

(defn toomre-q
  "Toomre Q = c_s · Ω / (π G Σ) for a disc annulus. Estimates:
     c_s  = adiabatic sound speed at temperature T
     Ω    = Keplerian angular speed √(GM / r³)
     Σ    = surface density M_disc / (π r²)  (thin-disc approximation)
   Returns +∞ when Σ is zero."
  [star-mass disc-mass radius temperature]
  (let [M (double (or star-mass 0.0))
        m (double (or disc-mass 0.0))
        r (double (or radius 0.0))
        T (double (or temperature 0.0))]
    (if (and (pos? M) (pos? m) (pos? r) (pos? T))
      (let [cs    (thermo/sound-speed T)
            Omega (math/sqrt (/ (* law/G M) (* r r r)))
            Sigma (/ m (* math/PI r r))]
        (if (pos? Sigma)
          (/ (* cs Omega) (* math/PI law/G Sigma))
          Double/POSITIVE_INFINITY))
      Double/POSITIVE_INFINITY)))

(defn cooling-time-ratio
  "Gammie (2001) cooling-time to dynamical-time ratio: t_cool / Ω⁻¹.
   Estimates t_cool ≈ (Σ c_s²) / (2 σ T⁴) in seconds and Ω = √(GM/r³).
   Lower values mean faster cooling → fragmentation when Q < 1."
  [star-mass disc-mass radius temperature]
  (let [M (double (or star-mass 0.0))
        m (double (or disc-mass 0.0))
        r (double (or radius 0.0))
        T (double (or temperature 0.0))]
    (if (and (pos? M) (pos? m) (pos? r) (pos? T))
      (let [cs    (thermo/sound-speed T)
            Sigma (/ m (* math/PI r r))
            Omega (math/sqrt (/ (* law/G M) (* r r r)))
            t-cool (if (pos? Sigma)
                     (/ (* Sigma cs cs)
                        (* 2.0 law/stefan-boltzmann T T T T))
                     0.0)]
        (if (pos? Omega)
          (* t-cool Omega)
          Double/POSITIVE_INFINITY))
      Double/POSITIVE_INFINITY)))

(defn disc-regime
  "Map Toomre Q and cooling time to a disc stability regime keyword.
   Only valid for rotationally-supported disc material; callers must check
   c/disc-tag = :disc first."
  [star-mass disc-mass radius temperature]
  (let [Q (toomre-q star-mass disc-mass radius temperature)
        cool-ratio (cooling-time-ratio star-mass disc-mass radius temperature)]
    (if (Double/isFinite Q)
      (cond
        (> Q 1.0)                         :stable-disc
        (and (<= Q 1.0) (< cool-ratio 3.0)) :gravitationally-unstable
        :else                             :unstable-no-fragment)
      :stable-disc)))

(defn disk-regime-map
  "Compute the scalar disk-regime map for a star+disk.
   Returns {:toomre-q :cooling-beta :regime :solid-surface-density :snow-line}.

   Accepts a single options map:
     {:star-mass :disk-mass :disk-radius :luminosity :composition}."
  [{:keys [star-mass disk-mass luminosity composition]
    disk-radius-local :disk-radius}]
  (let [T disk-outer-temperature
        Q (toomre-q star-mass disk-mass disk-radius-local T)
        beta (cooling-time-ratio star-mass disk-mass disk-radius-local T)
        snow-line (pf/snow-line-radius luminosity)
        sigma-gas (if (and (pos? disk-mass) (pos? disk-radius-local))
                    (/ disk-mass (* math/PI disk-radius-local disk-radius-local))
                    0.0)
        Z (lcomp/metallicity composition)
        sigma-solid (if (pos? sigma-gas)
                      (* sigma-gas Z
                         (if (> disk-radius-local snow-line)
                           pf/ice-enhancement-factor
                           1.0))
                      0.0)
        regime (cond
                 (and (> Q 1.5) (pos? sigma-solid)) :core-accretion-zone
                 (> Q 1.0) :stable-disc
                 (and (<= Q 1.0) (< beta 3.0)) :fragmenting
                 :else :gravito-turbulent)]
    {:toomre-q Q
     :cooling-beta beta
     :regime regime
     :solid-surface-density sigma-solid
     :snow-line snow-line}))
