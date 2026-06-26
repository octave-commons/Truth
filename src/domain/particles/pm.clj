(ns domain.particles.pm
  "Particle-Mesh gravity. Mass is deposited onto a periodic cubic grid with
   Cloud-In-Cell weighting, the Poisson equation ∇²φ = 4πGρ is solved in Fourier
   space, the acceleration g = -∇φ is finite-differenced on the grid, and finally
   interpolated (CIC) back to each particle.

   Cost per step is O(N) deposit/interp + two 3D FFTs (O(G log G)). The grid side
   must be a power of two. Sub-grid collapse (forming actual stars/planets) is
   handled separately by close-range accretion — PM gravity only resolves
   structure down to about one cell."
  (:require [domain.particles.fft :as fft]))

(set! *unchecked-math* :warn-on-boxed)
(set! *warn-on-reflection* true)

(defrecord Mesh [^long n ^double box ^double h ^double g-const
                 ^doubles re ^doubles im
                 ^doubles gx ^doubles gy ^doubles gz
                 ^doubles green])

(defn- build-green
  "Precompute the Fourier-space Green's function -4πG/k² for every cell
   (0 at the k=0 mode). k_i = 2π·f_i/(n·h)."
  [^long n ^double h ^double g-const]
  (let [tot   (* n n n)
        green (double-array tot)
        l     (* (double n) h)
        k0    (/ (* 2.0 Math/PI) l)
        freq  (long-array n)]
    (dotimes [i n] (aset freq i (long (if (<= i (quot n 2)) i (- i n)))))
    (dotimes [z n]
      (dotimes [y n]
        (dotimes [x n]
          (let [fx (* k0 (aget freq x))
                fy (* k0 (aget freq y))
                fz (* k0 (aget freq z))
                ksq (+ (* fx fx) (* fy fy) (* fz fz))
                idx (+ (* (+ (* z n) y) n) x)]
            (aset green idx
                  (if (zero? ksq) 0.0 (/ (* -4.0 Math/PI g-const) ksq)))))))
    green))

(defn make-mesh
  "Create a particle-mesh of side n (power of two) spanning a cube of physical
   length `box` centred on the origin, with gravitational constant g-const."
  [n box g-const]
  (let [n   (long n)
        box (double box)
        tot (* n n n)
        h   (/ box (double n))]
    (->Mesh n box h (double g-const)
            (double-array tot) (double-array tot)
            (double-array tot) (double-array tot) (double-array tot)
            (build-green n h g-const))))

(defn- wrap ^long [^long i ^long n]
  (let [m (rem i n)] (if (neg? m) (+ m n) m)))

(defn solve!
  "Compute gravitational acceleration on every particle and write it into
   ax/ay/az. Particle positions px/py/pz are in physical units centred on the
   origin; `np` is the live particle count."
  [^Mesh mesh ^doubles px ^doubles py ^doubles pz ^doubles mass np
   ^doubles ax ^doubles ay ^doubles az]
  (let [np  (long np)
        n   (long (.n mesh))
        h   (double (.h mesh))
        tot (long (* n n n))
        half (double (* 0.5 (.box mesh)))
        ^doubles re  (.re mesh) ^doubles im (.im mesh)
        ^doubles gx  (.gx mesh) ^doubles gy (.gy mesh) ^doubles gz (.gz mesh)
        ^doubles green (.green mesh)]
    ;; clear grids
    (java.util.Arrays/fill re 0.0)
    (java.util.Arrays/fill im 0.0)
    ;; --- CIC mass deposit ---
    (dotimes [p np]
      (let [m  (aget mass p)]
        (when (pos? m)
          (let [cx (/ (+ (aget px p) half) h)
                cy (/ (+ (aget py p) half) h)
                cz (/ (+ (aget pz p) half) h)
                i0 (long (Math/floor cx))
                j0 (long (Math/floor cy))
                k0 (long (Math/floor cz))
                fx (- cx i0) fy (- cy j0) fz (- cz k0)
                i1 (wrap (inc i0) n) j1 (wrap (inc j0) n) k1 (wrap (inc k0) n)
                i0 (wrap i0 n) j0 (wrap j0 n) k0 (wrap k0 n)]
            (dotimes [oct 8]
              (let [xi (if (zero? (bit-and oct 1)) i0 i1)
                    yj (if (zero? (bit-and oct 2)) j0 j1)
                    zk (if (zero? (bit-and oct 4)) k0 k1)
                    wx (if (zero? (bit-and oct 1)) (- 1.0 fx) fx)
                    wy (if (zero? (bit-and oct 2)) (- 1.0 fy) fy)
                    wz (if (zero? (bit-and oct 4)) (- 1.0 fz) fz)
                    idx (+ (* (+ (* zk n) yj) n) xi)]
                (aset re idx (+ (aget re idx) (* m wx wy wz)))))))))
    ;; --- Poisson solve in Fourier space ---
    (fft/fft-3d! re im n -1.0)
    (dotimes [idx tot]
      (let [gfac (aget green idx)]
        (aset re idx (* (aget re idx) gfac))
        (aset im idx (* (aget im idx) gfac))))
    (fft/fft-3d! re im n 1.0)
    ;; φ = re/tot ; build acceleration g = -∇φ by central differences
    (let [inv-tot (/ 1.0 (double tot))
          inv2h   (/ 1.0 (* 2.0 h))]
      (dotimes [z n]
        (dotimes [y n]
          (dotimes [x n]
            (let [idx  (+ (* (+ (* z n) y) n) x)
                  xp   (+ (* (+ (* z n) y) n) (wrap (inc x) n))
                  xm   (+ (* (+ (* z n) y) n) (wrap (dec x) n))
                  yp   (+ (* (+ (* z n) (wrap (inc y) n)) n) x)
                  ym   (+ (* (+ (* z n) (wrap (dec y) n)) n) x)
                  zp   (+ (* (+ (* (wrap (inc z) n) n) y) n) x)
                  zm   (+ (* (+ (* (wrap (dec z) n) n) y) n) x)]
              (aset gx idx (* (- (* (aget re xm) inv-tot) (* (aget re xp) inv-tot)) inv2h))
              (aset gy idx (* (- (* (aget re ym) inv-tot) (* (aget re yp) inv-tot)) inv2h))
              (aset gz idx (* (- (* (aget re zm) inv-tot) (* (aget re zp) inv-tot)) inv2h))))))
    ;; --- CIC interpolate acceleration back to particles ---
    (dotimes [p np]
      (let [cx (/ (+ (aget px p) half) h)
            cy (/ (+ (aget py p) half) h)
            cz (/ (+ (aget pz p) half) h)
            i0 (long (Math/floor cx))
            j0 (long (Math/floor cy))
            k0 (long (Math/floor cz))
            fx (- cx i0) fy (- cy j0) fz (- cz k0)
            i1 (wrap (inc i0) n) j1 (wrap (inc j0) n) k1 (wrap (inc k0) n)
            i0 (wrap i0 n) j0 (wrap j0 n) k0 (wrap k0 n)
            sx (double-array 1) sy (double-array 1) sz (double-array 1)]
          (dotimes [oct 8]
            (let [xi (if (zero? (bit-and oct 1)) i0 i1)
                  yj (if (zero? (bit-and oct 2)) j0 j1)
                  zk (if (zero? (bit-and oct 4)) k0 k1)
                  wx (if (zero? (bit-and oct 1)) (- 1.0 fx) fx)
                  wy (if (zero? (bit-and oct 2)) (- 1.0 fy) fy)
                  wz (if (zero? (bit-and oct 4)) (- 1.0 fz) fz)
                  w  (* wx wy wz)
                  idx (+ (* (+ (* zk n) yj) n) xi)]
              (aset sx 0 (+ (aget sx 0) (* w (aget gx idx))))
              (aset sy 0 (+ (aget sy 0) (* w (aget gy idx))))
              (aset sz 0 (+ (aget sz 0) (* w (aget gz idx))))))
          (aset ax p (aget sx 0))
          (aset ay p (aget sy 0))
          (aset az p (aget sz 0)))))))
