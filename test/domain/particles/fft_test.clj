(ns domain.particles.fft-test
  "Tests for the radix-2 FFT used by the particle-mesh gravity solver."
  (:require [clojure.test :refer [deftest testing is]]
            [domain.particles.fft :as fft]))

(deftest test-fft-1d-roundtrip
  (testing "A forward+inverse 1D FFT recovers the original signal (up to the unnormalised scale n)"
    (let [n 16
          re (double-array (mapv #(Math/sin (* 2.0 Math/PI (/ % n))) (range n)))
          im (double-array n)
          orig (vec re)]
      (fft/fft-1d! re im n -1.0)
      (fft/fft-1d! re im n 1.0)
      ;; inverse has no normalisation, so divide by n
      (is (< (apply max (map #(Math/abs (- %1 (/ %2 (double n)))) orig (vec re))) 1e-14)))))

(deftest test-fft-3d-roundtrip
  (testing "A forward+inverse 3D FFT recovers the original grid (up to the unnormalised scale n³)"
    (let [n 16
          tot (* n n n)
          re (double-array tot)
          im (double-array tot)]
      (dotimes [i tot] (aset re i (Math/sin (* 0.1 i))))
      (let [orig (vec re)]
        (fft/fft-3d! re im n -1.0)
        (fft/fft-3d! re im n 1.0)
        ;; inverse has no normalisation, so divide by n³
        (is (< (apply max (map #(Math/abs (- %1 (/ %2 (double tot)))) orig (vec re))) 1e-14))))))

(deftest test-fft-dc-mode
  (testing "A constant signal transforms to a single non-zero DC bin"
    (let [n 8
          re (double-array n 1.0)
          im (double-array n)]
      (fft/fft-1d! re im n -1.0)
      (is (< (Math/abs (- (aget re 0) n)) 1e-12))
      (is (< (apply max (map #(Math/abs %) (rest (vec re)))) 1e-12)))))
