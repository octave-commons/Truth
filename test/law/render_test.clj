(ns law.render-test
  "Tests for Malli schemas in law.render."
  (:require
   [clojure.test :refer [deftest testing is]]
   [law.render :as lr]))

(deftest test-render-context-schema
  (testing "Valid and invalid render contexts"
    (is (lr/valid-render-context? {:scale 1.0e15
                                   :camera {}
                                   :viewport {:width 1280 :height 720}}))
    (is (not (lr/valid-render-context? {:scale 1.0e15
                                        :camera {}
                                        :viewport {:width 1280}}))
        "missing height is invalid")
    (is (not (lr/valid-render-context? {:camera {}
                                        :viewport {:width 1 :height 1}}))
        "missing scale is invalid")))

(deftest test-render-shape-schema
  (testing "Render shape schema accepts expected shapes"
    (is (lr/valid-render-shape? {:render-mode :body
                                 :position [0.0 0.0 0.0]
                                 :radius 1.0
                                 :color [1.0 0.0 0.0]}))
    (is (lr/valid-render-shape? {:render-mode :particle
                                 :position [0.0 0.0 0.0]}))
    (is (not (lr/valid-render-shape? {:render-mode :unknown
                                      :position [0.0 0.0 0.0]}))
        "unknown render mode is invalid")
    (is (not (lr/valid-render-shape? {:render-mode :body
                                      :position [0.0 0.0]}))
        "position must be 3D")))

(def ^:private example-volume-config
  {:kappa 1.2 :emission-scale 1.1 :scatter-scale 3.8 :jitter 1.0
   :visual-h-scale 4.0 :visual-h-min 1.5 :splat-gain 2.4})

(deftest test-volume-config-schema
  (testing "Volume config accepts the full knob map and rejects broken ones"
    (is (lr/valid-volume-config? example-volume-config))
    (is (not (lr/valid-volume-config? (dissoc example-volume-config :kappa)))
        "missing knob is invalid")
    (is (not (lr/valid-volume-config? (assoc example-volume-config :splat-gain -1.0)))
        "splat gain must be positive")
    (is (lr/valid-volume-config? (assoc example-volume-config :jitter 0.0))
        "jitter may be zero (disabled)")))

(deftest test-volume-descriptor-schema
  (testing "Volume descriptor covers texture, box, program, lights, config"
    (let [light {:pos [0.0 0.0 0.0] :col [1.0 0.9 0.8] :temp 5000.0 :intensity 12.0}
          descriptor {:tex 3
                      :box-min [-4.5 -4.5 -4.5]
                      :box-max [4.5 4.5 4.5]
                      :program 7
                      :lights [light]
                      :config example-volume-config}]
      (is (lr/valid-volume-descriptor? descriptor))
      (is (lr/valid-volume-descriptor? (assoc descriptor :lights []))
          "no lights is a valid (unlit) volume")
      (is (not (lr/valid-volume-descriptor? (dissoc descriptor :tex)))
          "texture id is required")
      (is (not (lr/valid-volume-descriptor? (assoc descriptor :box-min [0.0 0.0])))
          "box corners must be 3D")
      (is (not (lr/valid-volume-descriptor? (dissoc descriptor :config)))
          "config is required — the pass must not fall back to hidden literals"))))
