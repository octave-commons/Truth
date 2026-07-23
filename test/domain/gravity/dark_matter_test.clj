(ns domain.gravity.dark-matter-test
  "μ for the static dark-matter halo (kanban/tasks/dark-matter-static-halo.md):
   a STATIC background Plummer well fixed at the world origin. Verifies the
   field shape (toward-origin pull, zero at centre), the fan-out wiring
   (sole writer of accel.dark-matter, no write-conflict, integrator reads it),
   and the acceptance signal — a body given outward speed that would escape a
   bare gravity-free coast is instead held bound over many steps by the halo."
  (:require
   [clojure.test :refer [deftest is testing]]
   [clojure.math :as math]
   [law.stellar          :as law]
   [domain.ecs.core      :as ecs]
   [domain.ecs.components :as c]
   [domain.ecs.registry   :as reg]
   [domain.ecs.tick       :as tick]
   [domain.gravity.dark-matter :as dm]
   [shape.spatial         :as sp]))

;; A reasonably-scaled halo for these tests: nebula-like mass, nebula-like
;; scale radius — same order as `law/default-dark-matter-mass-factor` /
;; `default-dark-matter-scale-factor` applied to a 4e30 kg / 3e16 m nebula.
(def ^:private M (* law/default-dark-matter-mass-factor 4.0e30))
(def ^:private A (* law/default-dark-matter-scale-factor 3.0e16))

;; --- field shape -------------------------------------------------------------

(deftest acceleration-points-toward-origin-with-plummer-magnitude
  (testing "offset along +x: pulls in -x, magnitude matches plummer-acceleration"
    (let [r    3.0e16
          pos  (sp/vec3 r 0.0 0.0)
          acc  (dm/dark-matter-acceleration M A pos)
          want (law/plummer-acceleration M A r)]
      (is (some? acc))
      (is (< (nth acc 0) 0.0) "pulls back toward the origin")
      (is (< (abs (nth acc 1)) 1e-30))
      (is (< (abs (nth acc 2)) 1e-30))
      (is (< (Math/abs (- (sp/len acc) want)) (* 1e-9 want))
          "magnitude matches law.stellar/plummer-acceleration")))
  (testing "off-axis offset also points toward the origin"
    (let [pos (sp/vec3 1.0e16 2.0e16 0.0)
          acc (dm/dark-matter-acceleration M A pos)
          dir (sp/v* pos (/ 1.0 (sp/len pos)))
          adir (sp/v* acc (/ 1.0 (sp/len acc)))]
      ;; acc should be anti-parallel to pos (dir · adir ≈ -1)
      (is (< (Math/abs (+ 1.0 (reduce + (map * dir adir)))) 1e-6))))
  (testing "at the origin the field is zero (nil, no pull)"
    (is (nil? (dm/dark-matter-acceleration M A (sp/vec3 0.0 0.0 0.0))))))

;; --- fan-out wiring -----------------------------------------------------------

(defn- run-emitter
  "Run the dark-matter emitter's write-set and fold it onto `world`."
  [world]
  (tick/apply-write-set world ((:run (dm/dark-matter-acceleration-system)) world)))

(defn- world-with-body
  "A world (nebula-mass/radius set so the default factors apply) with one body
   at `pos`."
  [pos]
  (let [[w b] (ecs/spawn (ecs/empty-world))
        w     (ecs/put-components w b {c/position pos c/mass 1.0e24})]
    [(assoc w :genesis/nebula-mass 4.0e30 :genesis/nebula-radius 3.0e16) b]))

(deftest emitter-writes-sole-accel-channel
  (testing "the emitter writes accel.dark-matter for a body away from the origin"
    (let [[w b] (world-with-body (sp/vec3 2.0e16 0.0 0.0))
          w'    (run-emitter w)
          acc   (ecs/get-component w' b c/accel-dark-matter)]
      (is (some? acc))
      (is (< (nth acc 0) 0.0))))
  (testing "disabling the halo (mass factor 0) yields an empty write-set"
    (let [[w b] (world-with-body (sp/vec3 2.0e16 0.0 0.0))
          w     (assoc w :genesis/dark-matter-mass-factor 0.0)
          w'    (run-emitter w)]
      (is (nil? (ecs/get-component w' b c/accel-dark-matter))))))

(deftest registry-single-writer-and-integrator-wiring
  (testing "no fan-out write conflict is introduced"
    (is (empty? (reg/write-conflicts reg/systems))
        (reg/format-conflicts (reg/write-conflicts reg/systems))))
  (testing "the integrator reads accel.dark-matter so the halo pull is integrated"
    (is (contains? (->> reg/systems (filter #(= :integrator (:id %))) first :reads)
                    c/accel-dark-matter))))

;; --- acceptance signal: bound vs. escape --------------------------------------

(defn- leapfrog-run
  "Symplectic-Euler-integrate a point body under the static halo's field alone
   for `n` steps of `dt`, starting at `pos0`/`vel0`. Returns the max distance
   from the origin ever reached. `mass'` 0.0 for the halo disables it
   (M -> 0), isolating whether the field alone is what binds the body."
  [halo-M pos0 vel0 dt n]
  (loop [pos pos0 vel vel0 step 0 max-r (sp/len pos0)]
    (if (>= step n)
      max-r
      (let [a    (or (dm/dark-matter-acceleration halo-M A pos) [0.0 0.0 0.0])
            vel' (sp/v+ vel (sp/v* a dt))
            pos' (sp/v+ pos (sp/v* vel' dt))]
        (recur pos' vel' (inc step) (max max-r (sp/len pos')))))))

(deftest bound-orbit-vs-ejection
  (let [r0        3.0e15                       ;; well inside the halo's scale radius
        ;; Escape speed of the Plummer potential Φ(r) = -GM/√(r²+a²):
        ;; v_esc = √(2GM / √(r0²+A²)).
        v-esc     (math/sqrt (/ (* 2.0 law/G M) (math/sqrt (+ (* r0 r0) (* A A)))))
        pos0      (sp/vec3 r0 0.0 0.0)
        ;; Pure radial OUTWARD infall-momentum kick, below escape speed.
        vel-bound (sp/vec3 (* 0.5 v-esc) 0.0 0.0)
        dt        3.0e10
        n         4000]
    (testing "without the halo the same kick coasts away unbounded"
      (let [r-max (leapfrog-run 0.0 pos0 vel-bound dt n)]
        (is (> r-max (* 5.0 r0))
            "no field at all ⇒ ballistic straight-line coast, far past r0")))
    (testing "with the halo the body stays bound (orbits, doesn't run away)"
      (let [r-max (leapfrog-run M pos0 vel-bound dt n)]
        (is (< r-max (* 5.0 r0))
            (str "halo should hold the body near its launch radius, got r-max="
                 r-max))))))
