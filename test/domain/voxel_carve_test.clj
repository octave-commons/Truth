(ns domain.voxel-carve-test
  "Voxel 5: collision shock → voxel carving, in the frozen-world rigor of
   `domain.voxel-sculpt-test`: build the world, run the `:voxel-carve`,
   `:voxel-sculpt` and `:voxel-focus` systems' :run fns against the SAME
   frozen world (the parallel fan-out), fold the merged write-set —
   production timing exactly. Card:
   kanban/tasks/collision-shock-voxel-carving.md; design
   docs/designs/planetary-voxel-substrate.md §6; constants transcribed
   from docs/research/2026-07-22-crater-scaling-laws-for-voxel-carving.md
   (never re-derived).

   The collision input is the STICKY `c/absorb-merge` channel (the merge
   handler's durable record — the ledger event is diffed away at the
   write-set boundary), written directly into the frozen fixture world,
   the same way voxel tests inject `c/voxel-edit-queue`."
  (:require
   [clojure.test :refer [deftest is testing]]
   [domain.ecs.components :as c]
   [domain.ecs.core :as ecs]
   [domain.ecs.registry :as reg]
   [domain.ecs.tick :as tick]
   [domain.interior :as interior]
   [domain.interior-test :as fixtures]
   [domain.player :as player]
   [domain.voxel.band :as band]
   [domain.voxel.carve :as carve]
   [domain.voxel.focus :as focus]
   [domain.voxel.sculpt :as sculpt]
   [law.crater :as law]
   [law.voxel :as voxel]
   [shape.spatial :as sp]))

;; --- Fixtures --------------------------------------------------------------------

(def ^:private field
  "The seeded macro geology field of the rocky-habitable candidate."
  (interior/seed-field fixtures/rocky-habitable))

(def ^:private radius-m
  (:radius-m field))

(def ^:private impact-mass
  "The L = 68 m iron impactor (kg): D_tc ≈ 2.5 km at 30 km/s vertical —
   a ~2400-voxel carve, two queue chunks (the multi-tick drain), with
   ~20 melt voxels and ~13 vapor voxels. Classifier numbers pinned in
   `classifier-decision-table` below."
  (* (/ 4.0 3.0) Math/PI 34.0 34.0 34.0 7800.0))

(defn- world-with-committed-planet
  "An empty world with an observer and one committed world entity at the
   origin carrying the rocky-habitable candidate record. The default
   focus resolves a WIDE, SHALLOW band (h-r ≈ 911 m, depth 720 m, 7680
   voxels — pinned empirically) centred on +x, so the test crater's
   excavation depth and melt floor fit the band while its rim spills
   out-of-band (exercising the immediate-diff path). Returns
   [world world-eid obs-eid]."
  [{:keys [focus-offset focus-radius focus-intensity coherence]
    :or {focus-offset 500.0 focus-radius 1000.0 focus-intensity 0.072
         coherence 1.0}}]
  (let [focus-pos (sp/vec3 (+ radius-m focus-offset) 0.0 0.0)
        [w obs-eid] (player/spawn-observer (ecs/empty-world) focus-pos)
        w (player/update-observer
           w assoc
           :focus-position focus-pos
           :focus-radius focus-radius
           :focus-intensity focus-intensity
           :coherence coherence
           :attention-shell {:immediate-r 1.0e3 :regional-r 4.0e3})
        [w world-eid] (ecs/spawn w)
        w (ecs/put-components
           w world-eid
           {c/position          (sp/vec3 0.0 0.0 0.0)
            c/planet-candidate  fixtures/rocky-habitable
            c/commitment-state  :committed})]
    [w world-eid obs-eid]))

(defn- iron-packet
  "An absorb-merge packet for an iron impactor striking the +x surface
   point head-on at 30 km/s (the merge-packet shape:
   `domain.stellar.merge`). `mass` and `U` vary per test."
  [mass U]
  {:mass             (double mass)
   :velocity         [(- (double U)) 0.0 0.0]
   :position         [(+ radius-m 34.0) 0.0 0.0]
   :angular-momentum [0.0 0.0 0.0]
   :composition      {:Fe 1.0}
   :temperature      300.0})

(defn- run-tick
  "One production-faithful tick for the carve+sculpt+band triple: ALL
   systems run against the SAME frozen world (the parallel fan-out),
   their write-sets merge (disjoint component types), and the sculpt-op
   world key clears SERIALLY post-fold. Returns world'."
  [world]
  (let [ws (merge ((:run (sculpt/sculpt-system)) world)
                  ((:run (carve/carve-system)) world)
                  ((:run (focus/voxel-focus-system)) world))]
    (sculpt/clear-sculpt-ops (tick/apply-write-set world ws))))

(defn- run-ticks
  "Fold `n` ticks of the triple."
  [world n]
  (loop [w world i 0]
    (if (< i n)
      (recur (run-tick w) (inc i))
      w)))

(defn- run-until-drained
  "Fold ticks until the committed world's edit queue is empty, or
   `max-ticks` is exceeded — fails loudly rather than hanging on a queue
   bug. Always runs at least one tick."
  [world world-eid max-ticks]
  (loop [w world i 0]
    (let [queue-empty? (and (pos? i)
                            (empty? (ecs/get-component w world-eid c/voxel-edit-queue)))]
      (if (or (>= i max-ticks) queue-empty?)
        w
        (recur (run-tick w) (inc i))))))

(defn- promote-fully
  "A world whose band has completely materialized under the default
   fixture focus. Returns [world world-eid obs-eid]."
  ([] (promote-fully {}))
  ([fixture]
   (let [[w eid obs-eid] (world-with-committed-planet fixture)]
     [(run-until-drained w eid 100) eid obs-eid])))

(defn- band-voxels
  [world world-eid]
  (:voxels (ecs/get-component world world-eid c/voxel-band)))

(defn- voxels-by-state
  "Count of non-nil band voxels per `:state`."
  [world world-eid]
  (reduce (fn [m v] (update m (:state v) (fnil inc 0)))
          {}
          (filter some? (vals (band-voxels world world-eid)))))

;; --- The classifier decision table (research §3) -----------------------------------

(deftest classifier-decision-table
  (testing "worked example (research §8): 1 km Fe at 20 km/s, 45°, basalt —
            D_tc ≈ 15.2 km, melt ≈ 5.1e9 m³, complex crater. Tolerances are
            the transcription check, NOT the error bars: K1 is a factor-1.5
            constant (diameters ±40%) and melt ±factor 2 in the literature."
    (let [m-i (* (/ 4.0 3.0) Math/PI 500.0 500.0 500.0 7800.0)
          imp {:m-i m-i :L 1000.0 :U 2.0e4 :theta (/ Math/PI 4.0) :rho-i 7800.0}
          tgt {:M-t 5.97e24 :R-t 6.371e6 :g 9.81 :rho-t 2700.0 :Y 1.0e7
               :T 288.0 :material-class :rock}
          r   (carve/classify imp tgt)]
      (is (= :complex-crater (:regime r)))
      (is (< (Math/abs (- (:d-tc r) 1.52e4)) (* 0.02 1.52e4))
          (str "D_tc " (:d-tc r) " vs research 15.2 km"))
      (is (< (Math/abs (- (:d-fr r) 2.18e4)) (* 0.02 2.18e4))
          (str "D_fr " (:d-fr r) " vs research 21.8 km (Croft)"))
      (is (< (Math/abs (- (:v-melt r) 5.1e9)) (* 0.02 5.1e9))
          (str "V_melt " (:v-melt r) " vs research 5.1e9 m³"))
      (is (zero? (:v-vapor r)) "20 km/s is below the 25 km/s rock vapor threshold")
      (is (= :melt (:shock r)))))

  (testing "strength regime: small/slow impactor into strong, low-g target —
            min(D_g, D_s) picks D_s (Holsapple 1993), shock :none below the
            melt threshold"
    (let [m-i (* (/ 4.0 3.0) Math/PI 5.0 5.0 5.0 3000.0)
          imp {:m-i m-i :L 10.0 :U 100.0 :theta (/ Math/PI 4.0) :rho-i 3000.0}
          tgt {:M-t 1.0e15 :R-t 1.0e4 :g 0.01 :rho-t 3000.0 :Y 1.0e7
               :T 200.0 :material-class :rock}
          r   (carve/classify imp tgt)]
      (is (= :strength-crater (:regime r)))
      (is (< (:d-strength r) (:d-gravity r)))
      (is (= :none (:shock r)))
      (is (zero? (:v-melt r)))))

  (testing "simple gravity crater: the system-test impactor classifies simple"
    (let [imp {:m-i impact-mass :L 68.0 :U 3.0e4 :theta (/ Math/PI 2.0) :rho-i 7800.0}
          tgt {:M-t (:mass-kg field) :R-t radius-m :g 9.81 :rho-t 2800.0 :Y 1.0e7
               :T 288.0 :material-class :rock}
          r   (carve/classify imp tgt)]
      (is (= :simple-crater (:regime r)))
      (is (< (Math/abs (- (:d-tc r) 2509.0)) (* 0.02 2509.0)))
      (is (= :melt+vapor (:shock r)) "30 km/s exceeds the 25 km/s vapor threshold")
      (is (pos? (:v-vapor r)))))

  (testing "angle dependence: dimensions ∝ sin^(1/3)θ, melt ∝ sinθ (Gault &
            Wedekind 1978; Collins et al. 2005)"
    (let [mk (fn [theta]
               {:m-i impact-mass :L 68.0 :U 3.0e4 :theta theta :rho-i 7800.0})
          tgt {:M-t (:mass-kg field) :R-t radius-m :g 9.81 :rho-t 2800.0
               :Y 1.0e7 :T 288.0 :material-class :rock}
          r45 (carve/classify (mk (/ Math/PI 4.0)) tgt)
          r90 (carve/classify (mk (/ Math/PI 2.0)) tgt)]
      (is (< (:d-tc r45) (:d-tc r90)))
      (is (< (Math/abs (- (/ (:v-melt r45) (:v-melt r90))
                          (Math/sin (/ Math/PI 4.0))))
             1.0e-9))))

  (testing "ice target routes to the Kraus, Senft & Stewart 2011 CTH fits"
    (let [imp {:m-i 1.0e9 :L 130.0 :U 1.0e4 :theta (/ Math/PI 2.0) :rho-i 9.17e2}
          tgt {:M-t 1.0e22 :R-t 1.5e6 :g 1.3 :rho-t 1000.0 :Y 1.0e6
               :T 100.0 :material-class :ice}
          r   (carve/classify imp tgt)]
      (is (pos? (:v-melt r)) "10 km/s into ice melts (fits valid > 8 km/s)")
      (is (pos? (:v-vapor r)))
      (is (= :melt+vapor (:shock r)))))

  (testing "sub-threshold ice impact: 6 km/s into ice melts (5–8 km/s band,
            ±50%) but vaporizes EXACTLY nothing — the Kraus vapor fit is
            valid only above 8 km/s and is gated on it"
    (let [imp {:m-i 1.0e9 :L 130.0 :U 6.0e3 :theta (/ Math/PI 2.0) :rho-i 9.17e2}
          tgt {:M-t 1.0e22 :R-t 1.5e6 :g 1.3 :rho-t 1000.0 :Y 1.0e6
               :T 100.0 :material-class :ice}
          r   (carve/classify imp tgt)]
      (is (= :melt (:shock r)))
      (is (zero? (:v-vapor r)) "no vapor below the 8 km/s ice fit validity")
      (is (pos? (:v-melt r)) "the melt floor is not undercounted by the gate")))

  (testing "impactor material class: the ice gate requires HYDROGEN — a
            chondritic high-O silicate is :rock, not :ice (bare-O gating
            would give it the 917 kg/m³ ice density and a wrong diameter)"
    (is (= :rock (carve/impactor-material-class
                  {:O 0.53 :Si 0.25 :Mg 0.15 :Fe 0.07}))
        "anhydrous SiO2-order composition: >50% O, zero H -> rock")
    (is (= :ice (carve/impactor-material-class
                 {:O 0.55 :H 0.08 :C 0.20 :Si 0.10 :Fe 0.07}))
        "H-bearing volatile-rich composition -> ice")
    (is (= :iron (carve/impactor-material-class {:Fe 0.9 :Ni 0.1})))
    (is (= :rock (carve/impactor-material-class nil))))

  (testing "disruption gate (Benz & Asphaug 1999): Q vs Q*_D on a 200 m body —
            the weakest size (research §3). Branches scale Q through the
            shatter/Q*_D/2×Q*_D boundaries; Q*_D itself is a transcription
            check (~3.3e2 J/kg for basalt at R = 100 m)."
    (let [R-t 100.0
          rho-t 2700.0
          M-t (* (/ 4.0 3.0) Math/PI R-t R-t R-t rho-t)
          q*  (carve/disruption-q-star R-t rho-t :rock)
          U   5.0e3
          tgt {:M-t M-t :R-t R-t :g 0.03 :rho-t rho-t :Y 1.0e7
               :T 200.0 :material-class :rock}
          imp-at (fn [q-target]
                   (let [m-i (/ (* 2.0 q-target M-t) (* U U))]
                         {:m-i m-i :L 10.0 :U U :theta (/ Math/PI 2.0)
                          :rho-i 3000.0}))]
      (is (< (Math/abs (- q* 3.3e2)) (* 0.2 3.3e2))
          (str "Q*_D " q* " J/kg — Benz & Asphaug 1999 basalt @ 5 km/s, R=100 m"))
      (is (= :catastrophic-disruption
             (:regime (carve/classify (imp-at (* 2.5 q*)) tgt))))
      (is (= :disruption-marginal
             (:regime (carve/classify (imp-at (* 1.2 q*)) tgt))))
      (let [r (carve/classify (imp-at (* 0.75 q*)) tgt)]
        (is (:shattering? r) "Q*_S ≤ Q < Q*_D shatters without dispersal")
        (is (contains? #{:strength-crater :simple-crater :complex-crater}
                       (:regime r))))
      (let [r (carve/classify (imp-at (* 0.1 q*)) tgt)]
        (is (not (:shattering? r)))))))

;; --- The carve pipeline through the queue -----------------------------------------

(deftest carve-drains-through-queue-over-multiple-ticks
  (let [[w eid _] (promote-fully)
        w (ecs/put-component w eid c/absorb-merge [(iron-packet impact-mass 3.0e4)])
        w1 (run-tick w)
        req1 (ecs/get-component w1 eid c/voxel-carve-request)]
    (testing "tick 1: the packet classifies into a schema-valid plan on the
              request channel; nothing enqueued yet (one Jacobi tick stale)"
      (is (= 1 (count (:plans req1))))
      (is (law/carve-plan? (first (:plans req1))))
      (is (= :simple-crater (:regime (first (:plans req1)))))
      (is (empty? (:disruptions req1)))
      (is (empty? (ecs/get-component w1 eid c/voxel-edit-queue))))
    (let [w2 (run-tick w1)
          queue2 (ecs/get-component w2 eid c/voxel-edit-queue)
          nils2 (count (filter nil? (vals (band-voxels w2 eid))))]
      (testing "tick 2: the fold enqueued the carve as chunked :apply-edits
                jobs (provenance :collision, each within the per-job edit
                cap) and the first chunk ALREADY drained — the crater
                visibly FORMS over multiple ticks (owner-endorsed)"
        (is (seq queue2) "a ~2000-voxel carve spills past one tick's budget")
        (is (pos? nils2) "first-chunk excavation has already nil'd voxels")
        (is (every? #(= :apply-edits (:kind %)) queue2))
        (is (every? #(= :collision (:provenance %)) queue2))
        (is (every? #(<= (count (:edits %)) voxel/max-edits-per-job) queue2)))
      (let [w3 (run-tick w2)
            w-done (run-until-drained w3 eid 50)
              voxels (band-voxels w-done eid)
              states (voxels-by-state w-done eid)
              diffs  (ecs/get-component w-done eid c/voxel-edit-diffs)]
          (testing "drained: excavation nils bowl voxels; melt floor tagged
                    :melt cohesion 0; vapor core tagged :vapor"
            (is (empty? (ecs/get-component w-done eid c/voxel-edit-queue)))
            (is (< 100 (count (filter nil? (vals voxels))))
                "a crater's worth of in-band voxels carved")
            (is (pos? (long (get states :melt 0))) "melt floor tagged")
            (is (every? #(= 0.0 (:cohesion %))
                        (filter #(= :melt (:state %)) (filter some? (vals voxels)))))
            (is (pos? (long (get states :vapor 0))) "vapor core suspended"))
          (testing "the rim beyond the band appended collision-provenance
                    diffs immediately (design §7.3 out-of-band path)"
            (is (seq diffs))
            (is (every? #(= :collision (:provenance %)) diffs))
            (is (every? voxel/edit-diff? diffs)))
          (testing "touched provenance in the band is :collision"
            (is (every? #(= :collision %)
                        (vals (:touched (ecs/get-component w-done eid c/voxel-band))))))))))

(deftest cooling-recools-melt-to-solid
  (let [[w eid _] (promote-fully)
        w (ecs/put-component w eid c/absorb-merge [(iron-packet impact-mass 3.0e4)])
        w (run-ticks w 6)
        _ (is (empty? (ecs/get-component w eid c/voxel-edit-queue))
              "fixture sanity: the carve drained")
        _ (is (pos? (long (get (voxels-by-state w eid) :melt 0)))
              "fixture sanity: melt voxels exist before cooling")
        ;; ~10 cooling time constants per tick: melt re-solidifies promptly.
        w (assoc w :sim/dt (* 10.0 law/melt-cooling-time-constant-s))
        w (run-ticks w 4)
        voxels (band-voxels w eid)]
    (is (zero? (long (get (voxels-by-state w eid) :melt 0)))
        ":melt → :solid once below the melt temperature")
    (is (zero? (long (get (voxels-by-state w eid) :vapor 0)))
        ":vapor condenses to :solid in place (mass-conserving first model)")
    (let [resolidified (->> (filter some? (vals voxels))
                            (filter #(= :solid (:state %)))
                            (filter #(< 1.3e3 (:temperature %) 1.41e3)))]
      (is (every? #(pos? (:cohesion %)) resolidified)
          "re-solidified voxels recover their seed cohesion"))))

;; --- The honest no-ops and stops ---------------------------------------------------

(deftest carve-wins-over-cooling-inside-bowl
  (testing "a pre-existing :melt voxel inside a fresh carve bowl ends CARVED
            (nil), not resurrected to cooled :solid — cooling jobs enqueue
            before carve jobs in the same tick so the later-draining carve
            wins per voxel"
    (let [[w eid _] (promote-fully)
          anchor [1.0 0.0 0.0]
          voxels (band-voxels w eid)
          ;; A bowl voxel OUTSIDE the ~13-voxel near-axis vapor core: mid-
          ;; range perp, shallow h — carved to nil, never vapor-tagged.
          target-offset (->> (sort (keys voxels))
                             (filter (fn [offset]
                                       (let [v (get voxels offset)]
                                         (when (some? v)
                                           (let [c    (band/voxel-center offset)
                                                 s    (sp/dot c anchor)
                                                 h    (- radius-m s)
                                                 perp (sp/len (sp/v- c (sp/v* anchor s)))]
                                             (and (<= 0.0 h 100.0)
                                                  (<= 300.0 perp 600.0)))))))
                             first)
          _ (is (some? target-offset) "fixture sanity: an in-bowl voxel exists")
          band0 (ecs/get-component w eid c/voxel-band)
          melt-voxel (assoc (get voxels target-offset)
                            :state :melt :cohesion 0.0 :temperature 1.4e3)
          w (ecs/put-component w eid c/voxel-band
                               (assoc-in band0 [:voxels target-offset] melt-voxel))
          ;; Cooling WOULD solidify the tagged voxel this tick if it won.
          w (assoc w :sim/dt (* 10.0 law/melt-cooling-time-constant-s))
          w (ecs/put-component w eid c/absorb-merge [(iron-packet impact-mass 3.0e4)])
          w (run-ticks w 6)]
      (is (empty? (ecs/get-component w eid c/voxel-edit-queue))
          "fixture sanity: everything drained")
      (is (nil? (get (band-voxels w eid) target-offset))
          "carved, not cooled-solid"))))


(deftest sub-voxel-impact-no-ops
  (let [[w eid _] (promote-fully)
        before (band-voxels w eid)
        ;; L = 0.5 m iron at 30 km/s: D_tc ≈ 54 m < the 64 m voxel edge.
        tiny-mass (* (/ 4.0 3.0) Math/PI 0.25 0.25 0.25 7800.0)
        w (ecs/put-component w eid c/absorb-merge [(iron-packet tiny-mass 3.0e4)])
        w (run-ticks w 4)]
    (is (empty? (:plans (ecs/get-component w eid c/voxel-carve-request)))
        "a sub-voxel crater produces no plan")
    (is (empty? (ecs/get-component w eid c/voxel-edit-queue))
        "and enqueues no edits — it does not round up to a one-voxel poke")
    (is (= before (band-voxels w eid)))))

(deftest no-collision-no-work
  (let [[w eid _] (promote-fully)
        w (run-ticks w 3)]
    (is (nil? (ecs/get-component w eid c/voxel-carve-request))
        "no absorb-merge packets -> no request at all")
    (is (empty? (ecs/get-component w eid c/voxel-edit-queue)))))

(deftest uncommitted-world-no-work
  (let [[w _ _] (world-with-committed-planet {})
        eids (ecs/entities-with w c/commitment-state)
        w (reduce (fn [w eid] (ecs/put-component w eid c/commitment-state :candidate))
                  w eids)]
    (is (empty? ((:run (carve/carve-system)) w))
        "no :committed world -> the system emits nothing")))

(deftest unresolved-band-classifies-but-does-not-edit
  (testing "collision on a committed world WITHOUT a resolved band (focus
            held off the surface, so no band ever materializes): the plan
            classifies but no voxel edits are produced — the macro-field
            consequence (design §6 melt-fraction scalar / magma-ocean FSM)
            is the documented gap"
    (let [[w eid _] (world-with-committed-planet {:focus-offset 2.0e3})
          w (ecs/put-component w eid c/absorb-merge [(iron-packet impact-mass 3.0e4)])
          w1 (run-tick w)
          req1 (ecs/get-component w1 eid c/voxel-carve-request)
          w4 (run-ticks w1 3)]
      (is (nil? (ecs/get-component w4 eid c/voxel-band))
          "fixture sanity: the band never resolved")
      (is (= 1 (count (:plans req1))) "the collision still classifies to a plan")
      (is (empty? (:plans (ecs/get-component w4 eid c/voxel-carve-request)))
          "the fold consumed the plan")
      (is (empty? (ecs/get-component w4 eid c/voxel-edit-queue))
          "no band -> the fold drops the plan: no edits")
      (is (empty? (ecs/get-component w4 eid c/voxel-edit-diffs))))))

(deftest disruption-stops-and-reports
  (let [[w eid _] (promote-fully)
        ;; Half the target's mass at 30 km/s: Q ≫ 2·Q*_D.
        w (ecs/put-component w eid c/absorb-merge
                             [(iron-packet (* 0.5 (:mass-kg field)) 3.0e4)])
        w (run-ticks w 3)
        req (ecs/get-component w eid c/voxel-carve-request)]
    (is (empty? (:plans req)) "planetary disruption is beyond Voxel 5 — no carve")
    (is (= [:catastrophic-disruption] (mapv :regime (:disruptions req)))
        "the classifier still classifies — the report IS the stop")
    (is (empty? (ecs/get-component w eid c/voxel-edit-queue)))))

(deftest absorb-merge-staleness-does-not-recarve
  (testing "the absorb-merge packet is STICKY (collision-detection never
            clears it): the :seen idempotency set must prevent a re-carve
            every subsequent tick"
    (let [[w eid _] (promote-fully)
          w (ecs/put-component w eid c/absorb-merge [(iron-packet impact-mass 3.0e4)])
          w (run-ticks w 6)
          _ (is (empty? (ecs/get-component w eid c/voxel-edit-queue))
                "fixture sanity: the carve drained")
          carved (count (filter nil? (vals (band-voxels w eid))))
          _ (is (pos? carved) "fixture sanity: the carve happened")
          w (run-ticks w 6)]
      (is (= carved (count (filter nil? (vals (band-voxels w eid)))))
          "the same packet never carves twice"))))

;; --- Single-writer / declared reads+writes ------------------------------------------

(deftest single-writer-and-declared-channels
  (is (empty? (reg/write-conflicts reg/systems)))
  (testing ":voxel-carve is the sole writer of c/voxel-carve-request and
            declares every channel it touches"
    (let [entry (some #(when (= :voxel-carve (:id %)) %) reg/systems)]
      (is (= #{c/voxel-carve-request} (:writes entry)))
      (is (every? (:reads entry)
                  [c/commitment-state c/planet-candidate c/voxel-field
                   c/voxel-band c/absorb-merge c/position c/velocity
                   c/voxel-carve-request]))))
  (testing ":voxel-focus declares the carve request channel in its reads"
    (let [entry (some #(when (= :voxel-focus (:id %)) %) reg/systems)]
      (is (contains? (:reads entry) c/voxel-carve-request))
      (is (not (contains? (:writes entry) c/voxel-carve-request))))))
