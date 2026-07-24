(ns domain.gravity.pair-softening-test
  "Per-pair softening kernel tests (kanban/tasks/compact-pair-softening.md).

   Live failure being fixed: at the live world softening (5e14 m ≈ 3342 AU) the
   0.1·ε dead-zone (334 AU) zeroes star–planet and planet–planet gravity — the
   compact population is dynamically sterile and spawned planets eject. With the
   pair law (ε_pair = max(ε_i, ε_j), ε_compact = c/radius, dead-zone 0.1·ε_pair)
   compact–compact gravity switches on at ≈ Newtonian strength while gas–gas
   and gas–compact pairs behave exactly as before.

   Kernel tests attach the :eps the species law (law/body-softening) computes,
   as literals, so the kernels are tested independently of the law."
  (:require
   [clojure.test :refer [deftest is testing]]
   [domain.ecs.components :as c]
   [domain.ecs.core :as ecs]
   [domain.gravity.barnes-hut :as bh]
   [domain.orbital.system :as orbital]
   [domain.physics.cache.soa :as pcache-soa]
   [domain.spatial.index :as spatial]
   [domain.stellar.seeder :as seeder]
   [law.stellar :as law]
   [shape.spatial :as sp]))

(def ^:private world-eps
  "Live Plummer softening (m): ~3342 AU, the pacing-soft-max ceiling."
  5.0e14)

(def ^:private r-5au (* 5.0 law/au))

(defn- compact-body
  "A resolved compact body map with the species ε the law assigns (= :radius)."
  [id mass radius matter-state position]
  {:id id :mass mass :radius radius :kind :body/test
   :matter-state matter-state :eps (double radius)
   :position position :velocity [0.0 0.0 0.0]})

(defn- gas-parcel
  "A :nebula gas parcel map; the species law keeps the world ε."
  [id mass position]
  {:id id :mass mass :radius 1.0e13 :kind :body/gas
   :matter-state :nebula :eps world-eps
   :position position :velocity [0.0 0.0 0.0]})

(defn- bodies->soa
  "Build a minimal `:genesis/physics-soa` cache from body maps, carrying the
    per-entity :eps array the production cache now fills."
  [bodies]
  (let [n (count bodies)]
    {:eids   (vec (map :id bodies))
     :n      n
     :mass   (double-array (map :mass bodies))
     :radius (double-array (map #(or (:radius %) 0.0) bodies))
     :eps    (double-array (map #(double (or (:eps %) -1.0)) bodies))
     :px     (double-array (map #(double (nth (:position %) 0)) bodies))
     :py     (double-array (map #(double (nth (:position %) 1)) bodies))
     :pz     (double-array (map #(double (nth (:position %) 2)) bodies))
     :vx     (double-array n)
     :vy     (double-array n)
     :vz     (double-array n)}))

;; --- 2. Kernel, compact pair: star + planet 5 AU apart -----------------------

(deftest compact-pair-force-switches-on
  (testing "star–planet at 5 AU with world ε = 3342 AU: the pair ε is the star's
            radius (≪ r), so the force is ≈ Newtonian GM/r² — where the legacy
            scalar kernel zeroed it inside the 334-AU dead-zone"
    (let [star   (compact-body :star law/solar-mass law/solar-radius :star
                               [0.0 0.0 0.0])
          planet (compact-body :planet law/jupiter-mass 7.0e7 :planet
                               [r-5au 0.0 0.0])
          a-newton (/ (* law/G law/solar-mass) (* r-5au r-5au))]
      (testing "map-tree path"
        (let [tree (bh/build-tree [star planet])
              acc  (bh/acceleration {:G law/G :theta 0.5 :softening world-eps
                                     :tree tree :body planet})]
          (is (not= [0.0 0.0 0.0] acc)
              "force is no longer zeroed by the world dead-zone")
          (is (neg? (double (nth acc 0)))
              "planet accelerates toward the star (−x)")
          (is (< (abs (- (sp/len acc) a-newton)) (* 0.01 a-newton))
              (str "within 1% of Newtonian; |a| = " (sp/len acc)
                   " vs GM/r² = " a-newton))))
      (testing "SoA path (production)"
        (let [soa (bodies->soa [star planet])
              acc (get (bh/acceleration-for-soa {:G law/G :theta 0.5
                                                 :softening world-eps
                                                 :soa soa :self-id nil})
                       :planet)]
          (is (not= [0.0 0.0 0.0] acc))
          (is (< (abs (- (sp/len acc) a-newton)) (* 0.01 a-newton))
              (str "SoA within 1% of Newtonian; |a| = " (sp/len acc))))))))

;; --- 3. Momentum symmetry ----------------------------------------------------

(deftest pair-force-momentum-symmetric
  (testing "m_i·a_i = −m_j·a_j: the symmetric pair ε keeps Newton's third law
            exact for a compact pair"
    (let [star   (compact-body :star law/solar-mass law/solar-radius :star
                               [0.0 0.0 0.0])
          planet (compact-body :planet law/jupiter-mass 7.0e7 :planet
                               [r-5au 0.0 0.0])
          tree   (bh/build-tree [star planet])
          a-p    (bh/acceleration {:G law/G :theta 0.5 :softening world-eps
                                   :tree tree :body planet})
          a-s    (bh/acceleration {:G law/G :theta 0.5 :softening world-eps
                                   :tree tree :body star})
          f-p    (sp/v* a-p law/jupiter-mass)
          f-s    (sp/v* a-s law/solar-mass)
          f-mag  (sp/len f-p)]
      (is (pos? f-mag))
      (is (< (/ (sp/len (sp/v+ f-p f-s)) f-mag) 1.0e-12)
          "forces are equal and opposite"))))

;; --- 4. Gas–gas UNCHANGED -----------------------------------------------------

(deftest gas-gas-pairs-byte-identical
  (testing "two :nebula parcels: ε_pair = world ε, dead-zone 0.1·world ε — the
            legacy scalar kernel exactly"
    (let [g1 (gas-parcel :g1 1.0e28 [0.0 0.0 0.0])
          g2 (gas-parcel :g2 1.0e28 [3.0e14 0.0 0.0])
          tree (bh/build-tree [g1 g2])
          soa  (bodies->soa [g1 g2])]
      (testing "outside the dead-zone the force equals the softened Plummer
                value bit-for-bit (same arithmetic as the legacy kernel)"
        (let [acc-map (bh/acceleration {:G law/G :theta 0.5 :softening world-eps
                                        :tree tree :body g2})
              acc-soa (get (bh/acceleration-for-soa {:G law/G :theta 0.5
                                                     :softening world-eps
                                                     :soa soa :self-id nil})
                           :g2)
              dx    -3.0e14
              d2    (+ (* dx dx) (* world-eps world-eps))
              inv-r (* d2 (Math/sqrt d2))
              scale (/ (* law/G 1.0e28) inv-r)
              expected [(* dx scale) 0.0 0.0]]
          (is (= expected acc-map) "map path bit-identical")
          (is (= expected acc-soa) "SoA path bit-identical")))
      (testing "inside the dead-zone (r < 0.1·world ε = 5e13) the force is zero"
        (let [g2-close (assoc g2 :position [1.0e13 0.0 0.0])
              tree (bh/build-tree [g1 g2-close])
              soa  (bodies->soa [g1 g2-close])]
          (is (= [0.0 0.0 0.0]
                 (bh/acceleration {:G law/G :theta 0.5 :softening world-eps
                                   :tree tree :body g2-close}))
              "map path")
          (is (= [0.0 0.0 0.0]
                 (get (bh/acceleration-for-soa {:G law/G :theta 0.5
                                                :softening world-eps
                                                :soa soa :self-id nil})
                      :g2))
              "SoA path"))))))

;; --- 5. Gas–compact inside the world dead-zone: still zero --------------------

(deftest gas-compact-pair-keeps-world-dead-zone
  (testing "gas–star at 200 AU (< 334-AU world dead-zone): ε_pair = max(world ε,
            star radius) = world ε, so the pair is still force-free — local gas
            interacts with compact bodies through the accretion channels, not
            raw gravity (behavior preserved)"
    (let [star (compact-body :star law/solar-mass law/solar-radius :star
                             [0.0 0.0 0.0])
          gas  (gas-parcel :gas 1.0e28 [(* 200.0 law/au) 0.0 0.0])
          tree (bh/build-tree [star gas])
          soa  (bodies->soa [star gas])]
      (is (= [0.0 0.0 0.0]
             (bh/acceleration {:G law/G :theta 0.5 :softening world-eps
                               :tree tree :body gas}))
          "map path")
      (is (= [0.0 0.0 0.0]
             (get (bh/acceleration-for-soa {:G law/G :theta 0.5
                                            :softening world-eps
                                            :soa soa :self-id nil})
                  :gas))
          "SoA path"))))

;; --- 6. Planet–planet: the card's done-when -----------------------------------

(deftest planet-planet-pair-attracts
  (testing "two planets 5 AU apart measurably attract (ε_pair = 7e7 m ≪ r)"
    (let [p1 (compact-body :p1 law/jupiter-mass 7.0e7 :planet [0.0 0.0 0.0])
          p2 (compact-body :p2 law/jupiter-mass 7.0e7 :planet [r-5au 0.0 0.0])
          a-newton (/ (* law/G law/jupiter-mass) (* r-5au r-5au))]
      (testing "map-tree path"
        (let [tree (bh/build-tree [p1 p2])
              acc  (bh/acceleration {:G law/G :theta 0.5 :softening world-eps
                                     :tree tree :body p2})]
          (is (not= [0.0 0.0 0.0] acc))
          (is (< (abs (- (sp/len acc) a-newton)) (* 0.01 a-newton))
              (str "≈ Newtonian; |a| = " (sp/len acc)))))
      (testing "SoA path"
        (let [soa (bodies->soa [p1 p2])
              acc (get (bh/acceleration-for-soa {:G law/G :theta 0.5
                                                 :softening world-eps
                                                 :soa soa :self-id nil})
                       :p2)]
          (is (not= [0.0 0.0 0.0] acc))
          (is (< (abs (- (sp/len acc) a-newton)) (* 0.01 a-newton))))))))

;; --- Integration: gravity-acceleration end-to-end, both gravity paths --------

(deftest gravity-acceleration-emits-newtonian-compact-pair
  (testing "the ECS gravity system (map-tree fallback AND production SoA path)
            writes ≈ Newtonian accel-gravity for a star–planet pair at 5 AU
            under live world ε — the 0/12-planets-bound live failure"
    (let [[w _star]  (seeder/spawn-clump
                      (ecs/empty-world)
                      {:position [0.0 0.0 0.0] :velocity [0.0 0.0 0.0]
                       :mass law/solar-mass :radius law/solar-radius
                       :matter-state :star :temperature 5800.0})
          [w planet] (seeder/spawn-clump
                      w
                      {:position [r-5au 0.0 0.0] :velocity [0.0 0.0 0.0]
                       :mass law/jupiter-mass :radius 7.0e7
                       :matter-state :planet :temperature 300.0})
          w       (assoc w :sim/G law/G :sim/softening world-eps :sim/dt 1.0)
          sys     (orbital/gravity-acceleration law/G 0.5 world-eps)
          a-newton (/ (* law/G law/solar-mass) (* r-5au r-5au))]
      (let [w-map (spatial/spatial-index w)
            acc   (get-in ((:run sys) w-map) [c/accel-gravity planet])]
        (is (some? acc) "map-tree path emits accel-gravity for the planet")
        (is (< (abs (- (sp/len acc) a-newton)) (* 0.01 a-newton))
            (str "map-tree path ≈ Newtonian; |a| = " (some-> acc sp/len))))
      (let [w-soa (-> w spatial/spatial-index pcache-soa/build-physics-soa)
            acc   (get-in ((:run sys) w-soa) [c/accel-gravity planet])]
        (is (some? acc) "SoA path emits accel-gravity for the planet")
        (is (< (abs (- (sp/len acc) a-newton)) (* 0.01 a-newton))
            (str "SoA path ≈ Newtonian; |a| = " (some-> acc sp/len)))
        (is (neg? (double (nth acc 0)))
            "toward the star")))))
