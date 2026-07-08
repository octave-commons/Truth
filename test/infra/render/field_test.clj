(ns infra.render.field-test
  "Epistemic tests for the pure froxel-field construction: the splat must show
   the same structure the SPH kernel sees. One parcel renders as a radially
   symmetric M4 blob; parcels closer than their support merge into a
   continuous lobe; denser gas reads denser in the grid. All headless — no GL."
  (:require
   [clojure.math :as math] [clojure.test :refer [deftest testing is]]
   [domain.hydro :as hydro]
   [infra.render.field :as field]
   [law.render :as lr]))

(defn- alpha-at
  "Density (alpha) channel of voxel [x y z] in the RGBA float array."
  [^floats data res [x y z]]
  (aget data (+ 3 (* 4 (+ (long x) (* (long res) (+ (long y) (* (long res) (long z)))))))))

(defn- voxel-centers
  "Seq of [[x y z] center-vec] for every voxel of an R³ grid over [bmn bmx]."
  [res bmn bmx]
  (let [cs (mapv #(/ (- %2 %1) (double res)) bmn bmx)]
    (for [z (range res) y (range res) x (range res)]
      [[x y z]
       (mapv (fn [mn c i] (+ mn (* (+ i 0.5) c))) bmn cs [x y z])])))

(defn- dist3 [[ax ay az] [bx by bz]]
  (math/sqrt (+ (math/pow (- ax bx) 2) (math/pow (- ay by) 2) (math/pow (- az bz) 2))))

(defn- nearest-voxel
  "Grid index of the voxel whose center is closest to point `p`."
  [res bmn bmx p]
  (first (apply min-key #(dist3 (second %) p) (voxel-centers res bmn bmx))))

(def ^:private single-parcel
  [{:p [0.0 0.0 0.0] :h 4.0 :col [1.0 1.0 1.0] :dens 1.0}])

(deftest test-splat-single-parcel-radial-symmetry
  (testing "one parcel bakes to a radially symmetric blob that dies at its support"
    (let [res 17
          gain 2.4
          {:keys [data box-min box-max]} (field/splat-field single-parcel res gain)
          ;; box is [-4.5, 4.5]³ (p ± h, 0.5 pad floor), so the centre voxel
          ;; is centered approximately on the parcel
          center [(quot res 2) (quot res 2) (quot res 2)]
          [cx cy cz] center]
      (is (pos? (alpha-at data res center)) "peak voxel is filled")
      (doseq [k [3 6]]
        (let [along (mapv #(alpha-at data res %)
                          [[(+ cx k) cy cz] [(- cx k) cy cz]
                           [cx (+ cy k) cz] [cx (- cy k) cz]
                           [cx cy (+ cz k)] [cx cy (- cz k)]])]
          (is (< (- (apply max along) (apply min along)) 1e-4)
              (str "same-radius voxels agree at offset " k))))
      (let [profile (mapv #(alpha-at data res [(+ cx %) cy cz]) (range (inc (quot res 2))))]
        (is (every? (fn [[a b]] (>= (+ a 1e-7) b)) (partition 2 1 profile))
            "alpha is non-increasing with radius"))
      (doseq [[i center-pos] (voxel-centers res box-min box-max)]
        (when (>= (dist3 center-pos [0.0 0.0 0.0]) 4.0)
          (is (zero? (alpha-at data res i))
              (str "voxel " i " outside the kernel support is empty")))))))

(deftest test-splat-profile-matches-kernel-shape
  (testing "voxel alpha equals gain · dens · kernel-shape at the voxel center — the physics profile, verbatim"
    (let [res 17
          gain 2.4
          {:keys [data box-min box-max]} (field/splat-field single-parcel res gain)]
      (doseq [[i center-pos] (voxel-centers res box-min box-max)]
        (let [r (dist3 center-pos [0.0 0.0 0.0])
              expected (* gain 1.0 (hydro/kernel-shape (* r r) 4.0))]
          (is (< (abs (- (alpha-at data res i) expected)) 1e-5)
              (str "voxel " i " carries the M4 falloff")))))))

(deftest test-splat-two-parcels-merge-ridge
  (testing "parcels separated by 1·h merge into one continuous lobe"
    (let [res 24
          gain 2.4
          pts [{:p [0.0 0.0 0.0] :h 4.0 :col [1.0 1.0 1.0] :dens 1.0}
               {:p [4.0 0.0 0.0] :h 4.0 :col [1.0 1.0 1.0] :dens 1.0}]
          {:keys [data box-min box-max]} (field/splat-field pts res gain)
          segment (for [t (range 0.0 4.01 0.5)]
                    (nearest-voxel res box-min box-max [t 0.0 0.0]))
          alphas (mapv #(alpha-at data res %) segment)
          peak (alpha-at data res (nearest-voxel res box-min box-max [0.0 0.0 0.0]))
          mid  (alpha-at data res (nearest-voxel res box-min box-max [2.0 0.0 0.0]))]
      (is (every? pos? alphas) "the density ridge between the parcels never drops to zero")
      (is (> mid (* 0.4 peak)) "the midpoint saddle stays a substantial fraction of the peak")))
  (testing "parcels separated by 2.5·h stay distinct blobs"
    (let [res 24
          gain 2.4
          pts [{:p [0.0 0.0 0.0] :h 4.0 :col [1.0 1.0 1.0] :dens 1.0}
               {:p [10.0 0.0 0.0] :h 4.0 :col [1.0 1.0 1.0] :dens 1.0}]
          {:keys [data box-min box-max]} (field/splat-field pts res gain)
          gap (alpha-at data res (nearest-voxel res box-min box-max [5.0 0.0 0.0]))]
      (is (zero? gap) "the gap between disjoint supports is empty"))))

(deftest test-splat-domain-density-ordering
  (testing "denser gas (by the tick's SPH density) reads denser in the froxel grid"
    (let [res 24
          gain 2.4
          parcels (map-indexed (fn [i dens]
                                 {:p [(* 12.0 i) 0.0 0.0] :h 2.0
                                  :col [1.0 1.0 1.0] :dens dens})
                               [0.2 0.4 0.6 0.8])
          {:keys [data box-min box-max]} (field/splat-field (vec parcels) res gain)
          alphas (mapv (fn [{:keys [p]}]
                         (alpha-at data res (nearest-voxel res box-min box-max p)))
                       parcels)]
      (is (apply < alphas) "froxel alpha preserves the parcels' density order"))))

(deftest test-splat-bounds-pad
  (testing "the box covers every parcel's full support, padded"
    (let [pts [{:p [0.0 0.0 0.0] :h 4.0 :col [1.0 1.0 1.0] :dens 1.0}
               {:p [10.0 2.0 -3.0] :h 1.0 :col [1.0 1.0 1.0] :dens 0.5}]
          [bmn bmx] (field/splat-bounds pts)]
      (doseq [{:keys [p h]} pts]
        (is (every? true? (map <= bmn (mapv #(- % h) p))) "box-min is at or below p - h")
        (is (every? true? (map >= bmx (mapv #(+ % h) p))) "box-max is at or above p + h"))))
  (testing "no gas means no field"
    (is (nil? (field/splat-field [] 32 2.4)))))

(deftest test-cull-gas-outliers
  (testing "small clouds are never culled"
    (let [pts (mapv (fn [i] {:p [(double i) 0.0 0.0] :h 1.0}) (range 7))]
      (is (= pts (field/cull-gas-outliers pts)))))
  (testing "a single flung parcel is dropped, the cluster survives"
    ;; the cull is percentile-based, so the outlier must be a sub-5% tail of
    ;; the population: 27 clustered parcels + 1 escapee
    (let [cluster (for [x (range 3) y (range 3) z (range 3)]
                    {:p [(double x) (double y) (double z)] :h 1.0})
          outlier {:p [300.0 0.0 0.0] :h 1.0}
          kept (field/cull-gas-outliers (vec (concat cluster [outlier])))]
      (is (= (vec cluster) kept) "outlier culled, dense cluster intact"))))

(deftest test-ionization-tint
  (testing "neutral gas keeps its temperature color; ionized gas shifts blue-white"
    (let [warm [0.9 0.5 0.2]]
      (is (= warm (field/ionization-tint warm 0.0)))
      (let [plasma (field/ionization-tint warm 1.0)]
        (is (> (nth plasma 2) (nth warm 2)) "blue channel rises with ionization")
        (is (< (nth plasma 0) (nth warm 0)) "red channel falls toward the plasma tint")))))

(deftest test-density-norm-band
  (testing "log-band mapping from physical density to [0,1] visual factor"
    (is (zero? (field/density-norm 1e-21)) "band floor")
    (is (= 1.0 (field/density-norm 1e-12)) "band ceiling")
    (is (< (abs (- (field/density-norm 1e-18) (/ 1.0 3.0))) 1e-12)
        "seed density sits a third of the way up the band")
    (is (zero? (field/density-norm 1e-30)) "far below band clamps to 0")
    (is (= 1.0 (field/density-norm 1.0)) "far above band clamps to 1")))

(deftest test-default-volume-config-valid
  (testing "the shipped defaults satisfy the law.render volume-config schema"
    (is (lr/valid-volume-config? field/default-volume-config))))
