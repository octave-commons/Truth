(ns domain.dominant-star-test
  "Genesis Formation spec Part 1a — competitive accretion must funnel a
   collapsing clump into ONE dominant star rather than fragmenting it into a swarm
   of ~equal marginal cores.

   TDD under SYNTHETIC conditions (no 10³-tick emergent run needed): a tight,
   cold, Jeans-unstable clump of equal-mass gas parcels, driven through the real
   `genesis/tick-world` pipeline for a few dozen ticks. The mechanism is the
   mass-dependent Bondi capture radius (`stellar/effective-accretion-radius`): a
   core's gravitational reach grows ∝ M, so the most massive core accretes fastest
   and runs away. Toggling `:genesis/competitive-accretion?` isolates the
   mechanism — ON funnels the clump into one dominant star; OFF (the pre-fix fixed
   feeding zone) fragments it."
  (:require
   [clojure.test          :refer [deftest testing is]]
   [domain.genesis        :as genesis]
   [domain.ecs.core       :as ecs]
   [domain.ecs.components  :as c]
   [domain.stellar        :as stellar]))

(def msun 1.989e30)

(defn- hash01 [n]
  (/ (double (mod (* (+ 1 (long n)) 2654435761) 1000003)) 1000003.0))

(defn- collapsing-clump
  "A world holding `n` cold, dense, Jeans-unstable gas parcels packed into a
   sphere of radius `clump-r` around the origin, denser toward the centre. The gas
   smoothing radius is realistically small (frozen condensation zone ≈ a couple of
   inter-parcel spacings), so a dominant core can only emerge via mass-dependent
   competitive accretion, not because the fixed zone already engulfs the clump."
  [{:keys [n pmass clump-r dens temp dt gsr competitive?]
    :or {n 64 pmass 6.5e28 clump-r 3.0e13 dens 1.0e-9 temp 15.0 dt 5.0e10
         gsr 9.0e10 competitive? true}}]
  (let [w0 (-> (genesis/create-world {:gas-count 4 :nebula-mass 4.0e20 :nebula-radius 2.0e16})
               (assoc :genesis/adaptive-pacing? false :sim/dt dt
                      :genesis/competitive-accretion? competitive?
                      :genesis/gas-particle-mass pmass
                      :genesis/gas-smoothing-radius gsr
                      :genesis/feeding-zone-factor (stellar/resolution-feeding-zone-factor n)))]
    (reduce (fn [w i]
              (let [u (hash01 (+ i 1)) v (hash01 (+ i 101)) t (hash01 (+ i 201))
                    rr (* clump-r (Math/cbrt u))
                    ct (- (* 2.0 v) 1.0) st (Math/sqrt (max 0.0 (- 1.0 (* ct ct))))
                    ph (* 2.0 Math/PI t)
                    pos [(* rr st (Math/cos ph)) (* rr st (Math/sin ph)) (* rr ct)]
                    d (* dens (+ 1.0 (* 3.0 (- 1.0 (/ rr clump-r)))))
                    [w2 e] (ecs/spawn w)]
                (ecs/put-components w2 e
                                    {c/matter-state :nebula c/mass pmass c/radius (* 0.5 clump-r)
                                     c/density d c/temperature temp c/position pos
                                     c/velocity [0.0 0.0 0.0]
                                     c/composition {:H 0.75 :He 0.25 :metals 0.0}
                                     c/luminosity 0.0})))
            w0 (range n))))

(defn- masses-solar [w]
  (sort > (map #(/ (double (or (ecs/get-component w % c/mass) 0.0)) msun)
               (ecs/entities-with w c/matter-state c/mass))))

(defn- run [w ticks] (nth (iterate genesis/tick-world w) ticks))

(deftest competitive-accretion-yields-one-dominant-star
  (testing "a collapsing clump funnels into exactly ONE dominant star (> 0.5 M☉)"
    (let [w      (run (collapsing-clump {}) 45)
          ms     (masses-solar w)
          total  (reduce + 0.0 ms)
          top    (first ms)
          n-dom  (count (filter #(> % 0.5) ms))
          n-star (count (filter #(= :star (ecs/get-component w % c/matter-state))
                                (ecs/entities-with w c/matter-state c/mass)))
          summ   (genesis/system-summary w)]
      (is (= 1 n-dom) (str "exactly one body should exceed 0.5 M☉; masses=" (mapv #(format "%.3f" %) (take 6 ms))))
      (is (> top 0.5) "the dominant star is comfortably above 0.5 M☉")
      (is (:star? summ) "the dominant core has ignited")
      (is (<= 1 n-star 2) (str "star count settles to 1–2, not a swarm; got " n-star))
      (is (> (/ top total) 0.2) "the dominant holds a large share of the cloud's mass"))))

(deftest without-competitive-accretion-the-cloud-fragments
  (testing "the SAME clump with competitive accretion OFF fragments into a swarm"
    (let [w     (run (collapsing-clump {:competitive? false}) 45)
          ms    (masses-solar w)
          total (reduce + 0.0 ms)
          top   (first ms)
          n-dom (count (filter #(> % 0.5) ms))]
      (is (zero? n-dom) "no dominant star forms without competitive accretion")
      (is (< top 0.35) (str "the largest core stays marginal; got " (format "%.3f" top)))
      (is (< (/ top total) 0.15) "mass stays spread across many equal cores"))))

(deftest competitive-accretion-concentrates-mass
  (testing "competitive accretion concentrates far more mass into its largest core
            than the fragmenting baseline, from an identical initial clump"
    (let [on   (run (collapsing-clump {:competitive? true}) 45)
          off  (run (collapsing-clump {:competitive? false}) 45)
          top-on  (first (masses-solar on))
          top-off (first (masses-solar off))
          n-on  (count (ecs/entities-with on c/matter-state c/mass))
          n-off (count (ecs/entities-with off c/matter-state c/mass))]
      (is (> top-on (* 2.0 top-off)) "ON grows a much larger dominant core than OFF")
      (is (< n-on n-off) "ON leaves far fewer surviving bodies (mass merged into the winner)"))))

(deftest effective-accretion-radius-grows-with-mass
  (testing "the effective capture radius increases with sink mass (Bondi ∝ M)"
    (let [mk (fn [mass]
               (let [[w e] (ecs/spawn (ecs/empty-world))]
                 (-> w
                     (ecs/put-components e {c/accretion-radius 1.0e12
                                            c/mass mass
                                            c/temperature 1.0e6 ;; hot sink — must NOT shrink the zone
                                            c/velocity [0.0 0.0 0.0]})
                     (stellar/effective-accretion-radius e))))
          r-small (mk 1.0e29)
          r-big   (mk 1.0e30)]
      (is (> r-big r-small) "a more massive sink has a larger capture radius")
      (is (>= r-small 1.0e12) "never smaller than the frozen condensation zone (floor)"))))

(deftest effective-accretion-radius-respects-the-toggle
  (testing "with competitive accretion disabled the effective radius is the frozen zone"
    (let [[w e] (ecs/spawn (ecs/empty-world))
          w (-> (assoc w :genesis/competitive-accretion? false)
                (ecs/put-components e {c/accretion-radius 1.0e12 c/mass 1.0e30
                                       c/temperature 15.0 c/velocity [0.0 0.0 0.0]}))]
      (is (= 1.0e12 (stellar/effective-accretion-radius w e))))))
