(ns domain.ecology.math
  "Tiny clamped-arithmetic helpers used by the ecology model.

   Ecology metrics are normalised to [0,1], so all additive changes are clamped
   to keep the state machine well-behaved.")

(defn clamp01 "Clamp `x` to the closed interval [0.0, 1.0]." [x]
  (max 0.0 (min 1.0 (double x))))

(defn +c "Add `delta` to `x` and clamp the result to [0.0, 1.0]." [x delta]
  (clamp01 (+ (double x) (double delta))))

(defn -c "Subtract `delta` from `x` and clamp the result to [0.0, 1.0]." [x delta]
  (clamp01 (- (double x) (double delta))))