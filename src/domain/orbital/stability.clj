(ns domain.orbital.stability
  "M5 handoff Phase 2: analytic orbit-stability proxy for candidate planets.
   See kanban/tasks/ecology-m5-phase2-orbit-stability.md and parent
   kanban/tasks/ecology-water-gate-snowline.md §3.3.

   This is DELIBERATELY an analytic proxy, not a 10 Myr two-body integration
   (parent §6 Phase 4 note: the integration is a refinement gated on the proxy
   proving too coarse). It reads a single snapshot's position/velocity/mass and
   derives standard two-body orbital elements (semi-major axis, eccentricity)
   via vis-viva, then checks three cheap, physically-motivated gates: a
   periapsis floor near the star, an apoapsis ceiling for the system, and a
   Hill-radius separation from sibling candidates. All pure — no ECS, no I/O."
  (:require
   [clojure.math :as math]
   [shape.spatial :as sp]
   [law.stellar :as law]))

(def ^:const star-radius-margin-factor
  "Periapsis safety margin in units of the star's own radius: a stable orbit's
   closest approach must clear the star's radius PLUS this many stellar radii
   (parent §3.3: \"star radius + 5 stellar radii\"), keeping the planet well
   outside the star's radiative/tidal reach rather than merely outside its
   photosphere." 5.0)

(def ^:const max-apoapsis-au
  "Apoapsis ceiling (AU) — a candidate whose orbit ranges beyond this is
   treated as drifting out of the system rather than durably bound (parent
   §3.3)." 100.0)

(def ^:const hill-radius-safety-factor
  "A candidate is considered too close to a sibling candidate if their current
   separation is under this many Hill radii (parent §3.3: \"10 Hill radii\")."
  10.0)

(defn two-body-elements
  "Two-body orbital elements from a relative position `r-vec` (m) and relative
   velocity `v-vec` (m/s) around a central standard gravitational parameter
   `mu` (= G·M, m³/s²), via vis-viva:

       ε = v²/2 - μ/r            (specific orbital energy)
       a = -μ/(2ε)               (semi-major axis, only defined for ε < 0)
       h = |r × v|                (specific angular momentum)
       e = √(1 - h²/(μ·a))       (eccentricity)
       periapsis = a(1-e), apoapsis = a(1+e)

   Returns `{:semi-major-axis :eccentricity :periapsis :apoapsis}`, or `nil`
   for a degenerate, unbound, or exactly parabolic relative state (ε ≥ 0) —
   there is no periapsis/apoapsis to test in that case, so callers should
   treat `nil` as automatically unstable. Uses the plain (unsoftened) two-body
   μ = G·M: the periapsis floor this proxy tests against sits many stellar
   radii out, far beyond any Plummer softening length, where the softened
   field is indistinguishable from Keplerian."
  [r-vec v-vec mu]
  (let [r  (sp/len r-vec)
        v  (sp/len v-vec)
        mu (double mu)]
    (when (and (pos? r) (pos? mu))
      (let [eps (- (/ (* v v) 2.0) (/ mu r))]
        (when (neg? eps)
          (let [a  (/ (- mu) (* 2.0 eps))
                h  (sp/len (sp/cross r-vec v-vec))
                e2 (max 0.0 (- 1.0 (/ (* h h) (* mu a))))
                e  (math/sqrt e2)]
            {:semi-major-axis a
             :eccentricity    e
             :periapsis       (* a (- 1.0 e))
             :apoapsis        (* a (+ 1.0 e))}))))))

(defn- close-approach?
  "True when `planet` and `other` (each `{:position :mass}`) are separated by
   less than `hill-radius-safety-factor` times the larger of their two Hill
   radii around `star` — a cheap proxy for a future close encounter without
   propagating either orbit forward."
  [planet other star]
  (let [star-mass (double (:mass star))
        r-planet  (sp/dist (:position planet) (:position star))
        r-other   (sp/dist (:position other) (:position star))
        hr-planet (law/hill-radius (:mass planet) star-mass r-planet)
        hr-other  (law/hill-radius (:mass other) star-mass r-other)
        hr        (max hr-planet hr-other)
        sep       (sp/dist (:position planet) (:position other))]
    (and (pos? hr) (< sep (* hill-radius-safety-factor hr)))))

(defn orbit-stability
  "Analytic orbit-stability proxy (pure). `planet` is
   `{:position :velocity :mass}`; `star` is
   `{:position :velocity :mass :radius}`; `other-candidates` is a seq of
   `{:position :mass}` for the OTHER candidate planets in the system
   (excluding `planet` itself).

   Stable iff all three hold:
     1. periapsis > star radius + `star-radius-margin-factor` stellar radii.
     2. apoapsis  < `max-apoapsis-au` AU.
     3. no other candidate is within `hill-radius-safety-factor` Hill radii.

   An unbound (or exactly parabolic) relative orbit around the star is
   immediately unstable — vis-viva has no periapsis/apoapsis to test there."
  [planet star other-candidates]
  (let [star-radius (double (or (:radius star) 0.0))
        mu          (* law/G (double (:mass star)))
        r-vec       (sp/v- (:position planet) (:position star))
        v-vec       (sp/v- (:velocity planet) (or (:velocity star) [0.0 0.0 0.0]))
        elements    (two-body-elements r-vec v-vec mu)
        periapsis-floor   (+ star-radius (* star-radius-margin-factor star-radius))
        apoapsis-ceiling  (* max-apoapsis-au law/au)]
    (boolean
     (and elements
          (> (:periapsis elements) periapsis-floor)
          (< (:apoapsis elements) apoapsis-ceiling)
          (not-any? #(close-approach? planet % star) other-candidates)))))
