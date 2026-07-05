(ns domain.em-field-substrate-test
  "μ for the research-grounded EM substrate: wind and flare parcels must carry the
   star's magnetic field sampled at their launch point via `em/net-field-at`,
   making net-field-at load-bearing in the live sim (previously renderer-only).
   This is the Phase-1 magnetised-outflow / magnetosphere-coupling on-ramp —
   see docs/research/phase1-radiation-plasma-truth.md §5 (winds) and §6 (CMEs)."
  (:require
   [clojure.test :refer [deftest is testing]]
   [domain.ecs.core     :as ecs]
   [domain.ecs.components :as c]
   [domain.ecs.tick     :as tick]
   [domain.em           :as em]
   [domain.stellar      :as stellar]
   [domain.genesis       :as genesis]
   [shape.spatial       :as sp]
   [law.stellar         :as law]))

(defn- v≈ [a b tol] (< (sp/len (sp/v- a b)) tol))

(defn- star-world
  "A single magnetised star primed to shed one parcel this tick (reservoir
   already holds more than one parcel mass)."
  []
  (let [[w eid] (ecs/spawn (ecs/empty-world))]
    [(-> w
         (assoc :sim/dt 1.0e12 :tick 1
                :genesis/wind-parcel-mass 1.0e26)
         (ecs/put-components eid
                             {c/matter-state   :star
                              c/mass           law/solar-mass
                              c/radius         1.0e9
                              c/position       (sp/vec3 0.0 0.0 0.0)
                              c/velocity       (sp/vec3 0.0 0.0 0.0)
                              c/temperature    1.0e7
                              c/density        1.0e3
                              c/pressure       1.0e13
                              c/composition    {:H 0.7 :He 0.3}
                              c/b-field        (sp/vec3 0.0 0.0 0.1)
                              c/wind-reservoir 2.0e26}))
     eid]))

(defn- launched-parcel [world star]
  (->> (ecs/entities-with world c/matter-state c/b-field)
       (filter #(and (not= % star)
                     (= :nebula (ecs/get-component world % c/matter-state))))
       first))

(deftest wind-parcel-carries-launch-point-field
  (let [[w star] (star-world)
        sources  (em/field-sources w)              ;; pre-launch sources (the star)
        ws       ((:run (stellar/stellar-wind-system)) w)
        w'       (-> (tick/apply-write-set w ws)
                     (genesis/materialize-lifecycle))
        parcel   (launched-parcel w' star)]
    (is (some? parcel) "a wind parcel was launched")
    (let [b   (ecs/get-component w' parcel c/b-field)
          pos (ecs/get-component w' parcel c/position)]
      (testing "parcel B equals net-field-at sampled at its launch point"
        (is (v≈ b (em/net-field-at pos sources nil) 1.0e-12)))
      (testing "the field is the real sampled vector — nonzero and finite"
        (is (pos? (sp/len b)))
        (is (every? #(Double/isFinite (double %)) b))))))

(deftest flare-parcel-carries-launch-point-field
  (let [[w star] (star-world)
        w        (assoc w :genesis/flare-period 1 :genesis/flare-mass-factor 1.0)
        sources  (em/field-sources w)
        ws       ((:run (stellar/stellar-flare-system)) w)
        w'       (-> (tick/apply-write-set w ws)
                     (genesis/materialize-lifecycle))
        parcel   (launched-parcel w' star)]
    (is (some? parcel) "a flare/CME parcel was launched")
    (let [b   (ecs/get-component w' parcel c/b-field)
          pos (ecs/get-component w' parcel c/position)]
      (is (v≈ b (em/net-field-at pos sources nil) 1.0e-12)
          "CME parcel carries the launch-point field"))))

(deftest net-field-at-dominated-by-nearest-star
  (testing "near a star's surface the sampled field is dominated by that star"
    (let [[w _star] (star-world)
          sources  (em/field-sources w)
          surf     (sp/vec3 1.0e9 0.0 0.0)         ;; one stellar radius out
          far      (sp/vec3 1.0e13 0.0 0.0)]        ;; far field
      (is (> (sp/len (em/net-field-at surf sources nil))
             (sp/len (em/net-field-at far sources nil)))
          "field falls off with distance (1/r^3 dipole)"))))
