(ns domain.particles.fft
  "In-place radix-2 Cooley–Tukey FFT over primitive double arrays, and a 3D
   transform built from it. Used by the particle-mesh Poisson solver.

   Arrays are flat `double[]`: separate real and imaginary buffers. The 3D grid
   is cubic of side n (a power of two), indexed idx = (z*n + y)*n + x with x the
   fastest-varying axis."
  (:import [java.lang Math]))

(set! *unchecked-math* :warn-on-boxed)
(set! *warn-on-reflection* true)

(defn- bit-reverse!
  "Gold–Rader in-place bit-reversal permutation of a contiguous length-n line."
  [^doubles re ^doubles im ^long n]
  (loop [i 1 j 0]
    (when (< i n)
      (let [j2 (long (loop [bit (bit-shift-right n 1) j (long j)]
                       (if (zero? (bit-and j bit))
                         (bit-or j bit)
                         (recur (bit-shift-right bit 1) (bit-xor j bit)))))]
        (when (< i j2)
          (let [tr (aget re i) ti (aget im i)]
            (aset re i (aget re j2)) (aset im i (aget im j2))
            (aset re j2 tr) (aset im j2 ti)))
        (recur (inc i) j2)))))

(def ^:private twiddle-cache
  "Cache of [n sign] -> [^doubles wr ^doubles wi] twiddle tables (length n/2)."
  (atom {}))

(defn- twiddles [^long n ^double sign]
  (or (get @twiddle-cache [n sign])
      (let [half (quot n 2)
            wr   (double-array half)
            wi   (double-array half)]
        (dotimes [k half]
          (let [ang (* sign 2.0 Math/PI (/ (double k) (double n)))]
            (aset wr k (Math/cos ang))
            (aset wi k (Math/sin ang))))
        (let [tw [wr wi]]
          (swap! twiddle-cache assoc [n sign] tw)
          tw))))

(defn fft-1d!
  "In-place 1D FFT of contiguous arrays re/im of length n (power of two).
   sign = -1.0 forward, +1.0 inverse. No normalization is applied."
  [^doubles re ^doubles im ^long n ^double sign]
  (bit-reverse! re im n)
  (let [tw (twiddles n sign)
        ^doubles wr (nth tw 0)
        ^doubles wi (nth tw 1)]
    (loop [len 2]
      (when (<= len n)
        (let [half (quot len 2)
              step (quot n len)]
          (loop [start 0]
            (when (< start n)
              (loop [k 0]
                (when (< k half)
                  (let [w   (* k step)
                        twr (aget wr w)
                        twi (aget wi w)
                        a   (+ start k)
                        b   (+ start k half)
                        vr  (aget re b) vi (aget im b)
                        tr  (- (* twr vr) (* twi vi))
                        ti  (+ (* twr vi) (* twi vr))
                        ur  (aget re a) ui (aget im a)]
                    (aset re a (+ ur tr)) (aset im a (+ ui ti))
                    (aset re b (- ur tr)) (aset im b (- ui ti))
                    (recur (inc k)))))
              (recur (+ start len))))
          (recur (* len 2)))))))

(defn- line-fft!
  "Copy a strided line of length n into scratch, FFT it, write it back."
  [^doubles re ^doubles im base stride n sign ^doubles sre ^doubles sim]
  (let [base (long base) stride (long stride) n (long n) sign (double sign)]
    (dotimes [t n]
      (let [idx (+ base (* t stride))]
        (aset sre t (aget re idx))
        (aset sim t (aget im idx))))
    (fft-1d! sre sim n sign)
    (dotimes [t n]
      (let [idx (+ base (* t stride))]
        (aset re idx (aget sre t))
        (aset im idx (aget sim t))))))

(defn fft-3d!
  "In-place 3D FFT of a cubic grid (side n) held in flat re/im arrays.
   sign = -1.0 forward, +1.0 inverse (no normalization)."
  [^doubles re ^doubles im n sign]
  (let [n   (long n)
        sign (double sign)
        n2  (* n n)
        sre (double-array n)
        sim (double-array n)]
    ;; along x (stride 1)
    (dotimes [z n] (dotimes [y n] (line-fft! re im (* (+ (* z n) y) n) 1 n sign sre sim)))
    ;; along y (stride n)
    (dotimes [z n] (dotimes [x n] (line-fft! re im (+ (* z n2) x) n n sign sre sim)))
    ;; along z (stride n*n)
    (dotimes [y n] (dotimes [x n] (line-fft! re im (+ (* y n) x) n2 n sign sre sim)))))
