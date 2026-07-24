(ns domain.planet-formation.orbit
  "Orbit-state construction, angular momentum, and surface temperature for
   seeded planets."
  (:require
   [clojure.math :as math]
   [law.stellar :as law]
   [domain.ecs.components :as c]
   [shape.spatial :as sp]
   [domain.planet-formation.physics :as pfph]
   [domain.planet-formation.composition :as pfc]))

(def ^:const planet-bond-albedo
  "Coarse Bond albedo for a seeded planet's equilibrium temperature. A tunable
   proxy (Earth≈0.3); a composition-derived albedo is a later refinement."
  0.3)

(def ^:const planet-greenhouse-warming
  "Greenhouse offset (K) added to a planet's equilibrium temperature to estimate
   its surface temperature. Earth's is ~33 K; without it, an Earth-analog reads
   its 255 K equilibrium value — below the liquid-water band — and no world is
   ever warm enough to host life. Tunable proxy for a real atmosphere/pressure
   greenhouse model (deferred)."
  35.0)

(defn- unit [v]
  (let [l (sp/len v)] (if (pos? l) (sp/v* v (/ 1.0 l)) v)))

(defn- sphere-radius
  "Radius of a uniform sphere of `mass` at material `density`."
  [mass density]
  (math/pow (/ (* 3.0 (double mass)) (* 4.0 math/PI (double density))) (/ 1.0 3.0)))

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
  (/ (double (mod (* (inc (long n)) 2654435761) 1000003)) 1000003.0))

(defn- circular-orbit-state
  "Return [position velocity] for a circular orbit of radius r around star."
  [r v-circ star-pos star-v star-axis star tick]
  (let [phase (* 2.0 math/PI (hash01 (hash [star r tick])))
        e1 (unit (sp/cross star-axis [1.0 0.0 0.0]))
        e1' (if (pos? (sp/len e1)) e1 [0.0 1.0 0.0])
        e2 (sp/cross e1' (unit star-axis))
        pos (sp/v+ star-pos
                   (sp/v+ (sp/v* e1' (* r (math/cos phase)))
                          (sp/v* e2 (* r (math/sin phase)))))
        vel (sp/v+ star-v
                   (sp/v+ (sp/v* e1' (* (- v-circ) (math/sin phase)))
                          (sp/v* e2 (* v-circ (math/cos phase)))))]
    [pos vel]))

(defn surface-temperature
  "A seeded planet's surface temperature: blackbody equilibrium at radius `r`
   plus a greenhouse offset (see `planet-greenhouse-warming`). This is the
   temperature habitability and rendering read."
  [luminosity r albedo]
  (+ (pfph/equilibrium-temperature luminosity r albedo) planet-greenhouse-warming))

(defn build-planet-spec
  "Build a planet seed spec from computed orbital and physical properties.
   `planet` keys: :r, :mass-kg, :ptype, :composition (the local-disk-derived
   element map from `domain.planet-formation.composition/planet-composition`),
   :tick, :star.
   `host` keys: :L-star, :pos, :vel, :axis, :M-star, :softening (accepted for
   caller compatibility; unused — the spawn speed is Newtonian, not softened).

   Also returns `:spawn-parent star` + `:rel-position`/`:rel-velocity` (the
   orbit state relative to the star) alongside the absolute :position/
   :velocity — needed because `materialize-lifecycle` runs AFTER
   `step-physics`, so the star has already moved (10s of AU/tick in the
   formation-era cluster) by the time this spec becomes an entity;
   `domain.genesis.bootstrap/spawn-entity` re-anchors on the parent's CURRENT
   state using these, the spawn-seam analogue of design
   `docs/designs/multi-timescale-integration.md` §3.0's stale-anchor fix."
  [{:keys [r mass-kg ptype composition tick star]}
   {:keys [L-star pos vel axis M-star softening]}]
  (let [_ softening
        dens (pfc/planet-material-density-by-type ptype)
        rad (sphere-radius mass-kg dens)
        ;; Newtonian circular speed: spawned :planets are sub-stepped by the
        ;; integrator's Wisdom–Holman path (exact Newtonian drift), so the
        ;; spawn velocity must pair with that law, not the softened field
        ;; (design §3.5 pairing rule).
        v-circ (law/newtonian-circular-speed M-star r)
        [position velocity] (circular-orbit-state r v-circ pos vel axis star tick)
        rel-pos (sp/v- position pos)
        rel-vel (sp/v- velocity vel)]
    {:position position
     :velocity velocity
     :spawn-parent star
     :rel-position rel-pos
     :rel-velocity rel-vel
     :mass mass-kg
     :radius rad
     :matter-state :planet
     :body-kind :body/planet
     :planet-type ptype
     :composition composition
     :temperature (surface-temperature L-star r planet-bond-albedo)
     :extra-components {c/planet-type ptype
                        c/angular-momentum (orbital-angular-momentum mass-kg rel-pos rel-vel)}}))
