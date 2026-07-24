(ns domain.orbital.kepler
  "Two-body Kepler orbit utilities."
  (:require [clojure.math :as math]
            [shape.spatial :as sp]))

(def ^:const two-pi (* 2.0 math/PI))

(defn kepler-period
  "Orbital period T = 2π √(a³/GM)."
  [^double a ^double GM]
  (* two-pi (math/sqrt (/ (* a a a) GM))))

(defn mean-anomaly
  "Mean anomaly M(t) = 2π(t - t0)/T, wrapped to [0, 2π)."
  [^double t ^double t0 ^double T]
  (mod (* two-pi (/ (- t t0) T)) two-pi))

(defn eccentric-anomaly
  "Solve Kepler's equation M = E - e*sin(E) by Newton–Raphson."
  ([^double M ^double e]
   (eccentric-anomaly M e 1e-10 50))
  ([^double M ^double e ^double tol ^long max-iter]
   (loop [E M, i 0]
     (let [dE (/ (- E (* e (math/sin E)) M)
                 (- 1.0 (* e (math/cos E))))]
       (cond
         (< (abs dE) tol) E
         (>= i max-iter)
         (throw (ex-info "eccentric-anomaly: no convergence"
                         {:M M :e e :E E :i i}))
         :else (recur (- E dE) (inc i)))))))

(defn true-anomaly
  "True anomaly ν from eccentric anomaly E and eccentricity e."
  [^double E ^double e]
  (* 2.0 (math/atan2
          (* (math/sqrt (+ 1.0 e)) (math/sin (* E 0.5)))
          (* (math/sqrt (- 1.0 e)) (math/cos (* E 0.5))))))

(defn orbital-state
  "Compute position and velocity in the orbital plane from classic elements."
  [{:keys [^double a ^double e ^double i
           ^double Ω ^double ω ^double t0 ^double GM]}
   ^double t]
  (let [T   (kepler-period a GM)
        M   (mean-anomaly t t0 T)
        E   (eccentric-anomaly M e)
        ν   (true-anomaly E e)
        r   (/ (* a (- 1.0 (* e e)))
               (+ 1.0 (* e (math/cos ν))))
        px  (* r (math/cos ν))
        py  (* r (math/sin ν))
        h   (math/sqrt (* GM a (- 1.0 (* e e))))
        vx  (/ (* (- (math/sin ν)) GM) h)
        vy  (/ (* (+ e (math/cos ν)) GM) h)
        cΩ  (math/cos Ω) sΩ (math/sin Ω)
        cω  (math/cos ω) sω (math/sin ω)
        ci  (math/cos i)  si (math/sin i)
        Rxx (- (* cΩ cω) (* sΩ sω ci))
        Rxy (- (- (* cΩ sω)) (* sΩ cω ci))
        Ryx (+ (* sΩ cω) (* cΩ sω ci))
        Ryy (+ (- (* sΩ sω)) (* cΩ cω ci))
        Rzx (* sω si)
        Rzy (* cω si)]
    {:position [(+ (* Rxx px) (* Rxy py))
                (+ (* Ryx px) (* Ryy py))
                (+ (* Rzx px) (* Rzy py))]
     :velocity [(+ (* Rxx vx) (* Rxy vy))
                (+ (* Ryx vx) (* Ryy vy))
                (+ (* Rzx vx) (* Rzy vy))]}))

;; ---------------------------------------------------------------------------
;; Closed-form state propagation (universal variables / Stumpff functions)
;; ---------------------------------------------------------------------------

(defn- stumpff
  "Stumpff functions [c2(z) c3(z)], z = α·χ².

   Analytic forms away from zero, power series near it:
     c2 = Σ (−z)^k/(2k+2)!,  c3 = Σ (−z)^k/(2k+3)!
   Valid for elliptical (z > 0), parabolic (z = 0), and hyperbolic (z < 0)
   orbits alike — the series glue is why the universal formulation never
   branches on orbit type in its caller."
  [^double z]
  (cond
    (> z 1.0e-7)
    (let [sz (math/sqrt z)]
      [(/ (- 1.0 (math/cos sz)) z)
       (/ (- sz (math/sin sz)) (* sz sz sz))])

    (< z -1.0e-7)
    (let [sz (math/sqrt (- z))]
      [(/ (- (math/cosh sz) 1.0) (- z))
       (/ (- (math/sinh sz) sz) (* sz sz sz))])

    :else
    (loop [k 0, term2 (/ 1.0 2.0), term3 (/ 1.0 6.0)
           c2 (/ 1.0 2.0), c3 (/ 1.0 6.0)]
      (let [k' (inc k)
            term2' (* term2 (/ (- z) (* (+ (* 2.0 k') 1.0) (+ (* 2.0 k') 2.0))))
            term3' (* term3 (/ (- z) (* (+ (* 2.0 k') 2.0) (+ (* 2.0 k') 3.0))))
            c2' (+ c2 term2')
            c3' (+ c3 term3')]
        (if (or (> k 40)
                (and (< (abs term2') (* 1.0e-18 (abs c2')))
                     (< (abs term3') (* 1.0e-18 (abs c3')))))
          [c2' c3']
          (recur k' term2' term3' c2' c3'))))))

(defn- universal-anomaly
  "Solve the universal Kepler equation for the universal anomaly χ:

       √μ·dt = χ³·c3(z) + (r·v/√μ)·χ²·c2(z) + r·χ·(1 − z·c3(z))

   with z = α·χ², α = 2/r − v²/μ, r·v the dot product of the initial state,
   and c2/c3 the Stumpff functions (Vallado Algorithm 8). dF/dχ:

       r(χ) = χ²·c2 + (r·v/√μ)·χ·(1 − z·c3) + r·(1 − z·c2)

   Since dF/dχ = r(χ) > 0 (position magnitude), F is strictly monotonic and
   the root is unique, so a bracketed Newton (bracket by doubling, bisect
   whenever the Newton step leaves the bracket) converges for ANY state —
   near-parabolic infall included — with no case analysis. Throws ex-info if
   the bracket cannot be established or the iteration cap is hit — a loud
   failure, never a fallback step-size or clamped guess (house rule)."
  [mu dt r0n rv alpha]
  (let [mu (double mu) dt (double dt) r0n (double r0n)
        rv (double rv) alpha (double alpha)
        sqmu (math/sqrt mu)
        target (* sqmu dt)
        s (math/signum target)
        F (fn [chi]
            (let [z (* alpha chi chi)
                  [c2 c3] (stumpff z)]
              (- (+ (* chi chi chi c3)
                    (* (/ rv sqmu) chi chi c2)
                    (* r0n chi (- 1.0 (* z c3))))
                 target)))
        dF (fn [chi]
             (let [z (* alpha chi chi)
                   [c2 c3] (stumpff z)]
               (+ (* chi chi c2)
                  (* (/ rv sqmu) chi (- 1.0 (* z c3)))
                  (* r0n (- 1.0 (* z c2))))))
        chi0 (cond
               (> alpha 1.0e-12) (* sqmu dt alpha)
               (< alpha -1.0e-12)
               (let [a-inv (- alpha)
                     arg (/ (* -2.0 mu alpha dt)
                            (+ rv (* (math/signum dt)
                                     (math/sqrt (/ mu a-inv))
                                     (- 1.0 (* r0n alpha)))))]
                 (if (pos? arg)
                   (* (math/signum dt)
                      (math/sqrt (/ 1.0 a-inv))
                      (math/log arg))
                   (/ target r0n)))
               :else (/ target r0n))
        [lo hi] (loop [lo 0.0
                       hi (* s (max (abs chi0) (/ (abs target) r0n) 1.0))
                       i 0]
                  (let [flo (F lo)
                        fhi (F hi)]
                    (cond
                      (<= (abs flo) (* 1.0e-10 (+ 1.0 (abs target)))) [lo lo]
                      (or (and (neg? (* s flo)) (pos? (* s fhi)))
                          (and (pos? (* s flo)) (neg? (* s fhi)))) [lo hi]
                      (>= i 100)
                      (throw (ex-info "propagate: cannot bracket the universal anomaly"
                                      {:mu mu :dt dt :r0 r0n :rv rv :alpha alpha
                                       :lo lo :hi hi :flo flo :fhi fhi}))
                      :else (recur hi (* 2.0 hi) (inc i)))))]
    (if (= lo hi)
      lo
      (loop [chi (min (max chi0 (min lo hi)) (max lo hi))
             lo lo, hi hi, i 0]
        (let [fchi (F chi)]
          (cond
            (<= (abs fchi) (* 1.0e-10 (+ 1.0 (abs target)))) chi
            (>= i 128)
            (throw (ex-info "propagate: universal-anomaly Newton iteration did not converge"
                            {:mu mu :dt dt :r0 r0n :rv rv :alpha alpha
                             :chi chi :F fchi :iterations i}))
            :else
            (let [[lo' hi'] (if (pos? (* s fchi))
                              (if (pos? s) [lo chi] [chi hi])
                              (if (pos? s) [chi hi] [lo chi]))
                  newton (- chi (/ fchi (dF chi)))
                  chi' (if (and (> newton (min lo' hi')) (< newton (max lo' hi')))
                         newton
                         (* 0.5 (+ lo' hi')))]
              (recur chi' lo' hi' (inc i)))))))))

(defn- fg-state
  "Lagrange f/g coefficient state after solving for universal anomaly χ:
   `{:r1n :f :g :fdot :gdot}`. The propagated radius r(χ) = dF/dχ falls out
   of the same Stumpff terms, so the f/g coefficients and the new radius come
   from one evaluation. Keys: chi, z = α·χ², c2/c3 Stumpff values, sqmu = √μ,
   rv = r0·v0, dt, r0n."
  [{:keys [chi z c2 c3 sqmu rv dt r0n]}]
  (let [zc3 (* z c3)
        r1n (+ (* chi chi c2)
               (* (/ rv sqmu) chi (- 1.0 zc3))
               (* r0n (- 1.0 (* z c2))))]
    {:r1n r1n
     :f (- 1.0 (/ (* chi chi c2) r0n))
     :g (- dt (/ (* chi chi chi c3) sqmu))
     :fdot (* (/ sqmu (* r1n r0n)) chi (- zc3 1.0))
     :gdot (- 1.0 (/ (* chi chi c2) r1n))}))

(defn propagate
  "Closed-form two-body state propagation by `dt` seconds via universal
   variables with Stumpff functions — exact (to Newton-iteration tolerance)
   for elliptical, near-parabolic, and hyperbolic relative orbits alike.

   `r0`/`v0` are the relative position (m) and velocity (m/s) of a body with
   respect to its parent at propagation start; `mu` = G·(M_parent + m_body)
   (m³/s²). Returns `{:position r1 :velocity v1}`, the relative state advanced
   by `dt`. Zero discretization error at any step size — this is the
   `drift_Kep` half of the Wisdom–Holman split inside
   `domain.integrator.kinematics` (design
   docs/designs/multi-timescale-integration.md §3.1), and it is why the
   sub-step count K there never needs to be exactly right.

   `dt` = 0 returns the input state unchanged. Throws ex-info if the
   universal-anomaly solve fails to converge or the arc reaches the origin —
   NO fallback, NO stop-gap (house rule): a propagator that silently degrades
   would re-introduce the secular eccentricity drift it exists to eliminate."
  [mu r0 v0 dt]
  (let [mu (double mu)
        dt (double dt)
        r0n (sp/len r0)]
    (cond
      (zero? dt) {:position r0 :velocity v0}
      (not (pos? mu))
      (throw (ex-info "propagate: non-positive gravitational parameter" {:mu mu}))
      (not (pos? r0n))
      (throw (ex-info "propagate: degenerate relative state (|r0| = 0)" {:r0 r0}))
      :else
      (let [rv (sp/dot r0 v0)
            alpha (- (/ 2.0 r0n) (/ (sp/len2 v0) mu))
            chi (universal-anomaly mu dt r0n rv alpha)
            [c2 c3] (stumpff (* alpha chi chi))
            {:keys [r1n f g fdot gdot]}
            (fg-state {:chi chi :z (* alpha chi chi) :c2 c2 :c3 c3
                       :sqmu (math/sqrt mu) :rv rv :dt dt :r0n r0n})
            _ (when-not (pos? r1n)
                (throw (ex-info "propagate: propagation reached the origin (collision)"
                                {:mu mu :dt dt :r0 r0 :v0 v0})))]
        {:position (sp/v+ (sp/v* r0 f) (sp/v* v0 g))
         :velocity (sp/v+ (sp/v* r0 fdot) (sp/v* v0 gdot))}))))
