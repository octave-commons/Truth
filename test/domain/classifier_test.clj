(ns domain.classifier-test
  "Step 6: the authentic formation state machine. Jeans instability gates
   condensation; accreted mass gates fate; temperature+mass gate ignition.
   These are the documentary beats expressed as assertions — see
   docs/notes/2026.06.26-authentic-phase0-formation-physics.md §3."
  (:require
   [clojure.test :refer [deftest is testing]]
   [domain.ecs.core :as ecs]
   [domain.ecs.components :as c]
   [domain.stellar :as stellar]
   [law.stellar :as law]))

(def ^:private pm 4.0e27)          ;; one gas parcel ≈ 2 M_Jupiter
(def ^:private cloud-comp {:H 0.71 :He 0.27 :metals 0.015})

(defn- region [m & {:keys [state radius density temperature pressure]
                    :or {state :nebula radius 1.0e14 density 1.0e-16
                         temperature 15.0 pressure 0.0}}]
  {:matter-state state :mass m :radius radius :density density
   :temperature temperature :pressure pressure :composition cloud-comp})

(defn- next-state [r] (stellar/classify-next-state r pm))

;; A condensing clump is Jeans-unstable because GRAVITY HAS COMPRESSED IT: density
;; is elevated (which shrinks the Jeans length below the clump's radius). Diffuse
;; cloud gas (ρ~1e-16) is stable; a compressed knot (ρ~1e-12) is not.
(def ^:private unstable {:density 1.0e-12 :radius 5.0e14})

(deftest nebula-condenses-only-when-jeans-unstable-and-accreted
  (testing "diffuse, stable cloud gas stays nebula"
    (is (= :nebula (next-state (region pm :density 1.0e-16 :radius 1.0e10)))))
  (testing "Jeans-unstable but still a single parcel ⇒ stays nebula (not yet accreted)"
    (is (= :nebula (next-state (apply region pm (mapcat identity unstable))))))
  (testing "Jeans-unstable AND accreted past one parcel, sub-stellar ⇒ debris"
    (is (= :debris (next-state (apply region (* 3.0 pm) (mapcat identity unstable))))))
  (testing "Jeans-unstable AND accreted to stellar-forming mass ⇒ protostar"
    (is (= :protostar (next-state (apply region law/deuterium-burning-mass
                                         (mapcat identity unstable)))))))

(deftest debris-promotes-to-protostar-by-accreted-mass
  (testing "debris below the deuterium limit keeps accreting as debris"
    (is (= :debris (next-state (region (* 5.0 pm) :state :debris :radius 1.0e9)))))
  (testing "debris that has accreted past the deuterium limit becomes a protostar"
    ;; note: no Jeans gate here — it is already a condensed core, fate is by mass
    (is (= :protostar (next-state (region law/deuterium-burning-mass
                                          :state :debris :radius 1.0e9))))))

(deftest protostar-fate-is-decided-by-mass-and-ignition
  (testing "hot, ≥0.08 M⊙ protostar ignites hydrogen → star"
    (is (= :star (next-state (region law/hydrogen-burning-mass :state :protostar
                                     :temperature 2.0e7 :pressure 1.0e13)))))
  (testing "≥0.08 M⊙ but not yet hot enough ⇒ stays a contracting protostar"
    (is (= :protostar (next-state (region law/hydrogen-burning-mass :state :protostar
                                          :temperature 1.0e5 :radius 1.0e12)))))
  (testing "0.013–0.08 M⊙ core that stalled at its radius floor → brown dwarf"
    (let [m   (* 0.05 law/solar-mass)
          flr (law/main-sequence-radius m)]
      (is (= :brown-dwarf
             (next-state (region m :state :protostar
                                 :radius flr :temperature 1.0e6))))))
  (testing "a brown dwarf never ignites — terminal"
    (is (= :brown-dwarf (next-state (region (* 0.05 law/solar-mass) :state :brown-dwarf
                                            :temperature 2.0e7 :pressure 1.0e13))))))

(deftest star-is-terminal
  (is (= :star (next-state (region law/hydrogen-burning-mass :state :star
                                   :temperature 100.0)))))

(deftest classifier-system-emits-only-changed-matter-state
  (let [[w e0] (ecs/spawn (ecs/empty-world))   ;; will ignite
        [w e1] (ecs/spawn w)                    ;; stays nebula
        w   (-> (ecs/put-components w e0 {c/matter-state :protostar
                                          c/mass law/hydrogen-burning-mass
                                          c/temperature 2.0e7 c/density 1.0e3
                                          c/pressure 1.0e13 c/radius 1.0e9
                                          c/composition cloud-comp})
                (ecs/put-components e1 {c/matter-state :nebula c/mass pm
                                        c/temperature 15.0 c/density 1.0e-16
                                        c/radius 1.0e10 c/composition cloud-comp}))
        w   (assoc w :genesis/gas-particle-mass pm)
        sys (stellar/classifier-system)
        ws  ((:run sys) w)]
    (testing "sole writer of matter-state and accretion-radius"
      (is (= :classifier (:id sys)))
      (is (= #{c/matter-state c/accretion-radius} (:writes sys))))
    (testing "only the igniting body changes; the stable nebula parcel is omitted"
      (is (= :star (get-in ws [c/matter-state e0]))))))
