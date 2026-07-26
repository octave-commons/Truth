(ns domain.voxel-load-test
  "Field-bias persistence (card kanban/tasks/voxel-field-bias-persistence.md):
   the first REAL save/load round-trips of the §7.3 save story, in the
   frozen-world rigor of `domain.voxel-sculpt-test` — build the world, run
   the `:voxel-sculpt` + `:voxel-focus` systems against the SAME frozen
   world, fold the merged write-set, then `domain.voxel.load/save-state` →
   `load-state` and assert bit-for-bit equality with the live state.

   Exact equality is asserted everywhere: the seed is deterministic,
   `domain.voxel.sculpt/apply-op` is pure, and replay performs the same
   folds in the same order — there is no sampling noise to excuse
   tolerance."
  (:require
   [clojure.test :refer [deftest is testing]]
   [domain.ecs.components :as c]
   [domain.ecs.core :as ecs]
   [domain.ecs.tick :as tick]
   [domain.interior :as interior]
   [domain.interior-test :as fixtures]
   [domain.player :as player]
   [domain.voxel.focus :as focus]
   [domain.voxel.load :as load]
   [domain.voxel.sculpt :as sculpt]
   [law.narrowing :as law-narrowing]
   [law.voxel :as voxel]
   [shape.spatial :as sp]))

;; --- Fixtures (the domain.voxel-sculpt-test pattern) ---------------------------

(def ^:private field
  "The seeded macro geology field of the rocky-habitable candidate."
  (interior/seed-field fixtures/rocky-habitable))

(def ^:private radius-m
  (:radius-m field))

(defn- world-with-committed-planet
  "An empty world with a Resonance-loaded, planetary-palette-armed
   observer and one committed world entity at the origin carrying the
   rocky-habitable candidate record. Returns [world world-eid obs-eid]."
  [{:keys [focus-offset focus-radius focus-intensity coherence resonance]
    :or {focus-offset 500.0 focus-radius 400.0 focus-intensity 1.0
         coherence 0.8 resonance 20.0}}]
  (let [focus-pos (sp/vec3 (+ radius-m focus-offset) 0.0 0.0)
        [w obs-eid] (player/spawn-observer (ecs/empty-world) focus-pos)
        w (player/update-observer
           w assoc
           :focus-position focus-pos
           :focus-radius focus-radius
           :focus-intensity focus-intensity
           :coherence coherence
           :resonance resonance
           :attention-shell {:immediate-r 1.0e3 :regional-r 4.0e3})
        w (ecs/put-component w obs-eid c/palette law-narrowing/planetary-palette)
        [w world-eid] (ecs/spawn w)
        w (ecs/put-components
           w world-eid
           {c/position          (sp/vec3 0.0 0.0 0.0)
            c/planet-candidate  fixtures/rocky-habitable
            c/commitment-state  :committed})]
    [w world-eid obs-eid]))

(defn- run-tick
  "One production-faithful tick for the sculpt+focus pair: BOTH systems
   run against the SAME frozen world (the parallel fan-out), their
   write-sets merge, and the sculpt-op world key clears SERIALLY
   post-fold. Returns world'."
  [world]
  (let [ws (merge ((:run (sculpt/sculpt-system)) world)
                  ((:run (focus/voxel-focus-system)) world))]
    (sculpt/clear-sculpt-ops (tick/apply-write-set world ws))))

(defn- run-ticks
  "Fold `n` ticks of the pair."
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
   fixture focus. Returns [world world-eid]."
  []
  (let [[w eid _] (world-with-committed-planet {})]
    [(run-until-drained w eid 50) eid]))

(defn- withdraw-focus
  "Move the observer's focus 2 km above the surface — beyond the 1 km
   immediate radius, so no band target exists."
  [world]
  (player/update-observer
   world assoc :focus-position (sp/vec3 (+ radius-m 2.0e3) 0.0 0.0)))

(defn- fold-op
  "Request `verb` at `magnitude` and fold it fully: two ticks (fan-out,
   then the one-Jacobi-tick-stale fold) plus a drain for the derived
   edits. Returns world'."
  [world eid verb magnitude]
  (-> world
      (sculpt/request-op verb magnitude)
      (run-ticks 2)
      (run-until-drained eid 50)))

;; --- 1. Unresolved world: the field bias round-trips through save/load ----------

(deftest unresolved-world-field-bias-round-trips
  (testing "three sculpt ops on an UNRESOLVED world (focus withdrawn, no
             band): the biases leave ZERO voxel trace but persist as
             field-diffs — save -> load reproduces the live field exactly,
             while a field-diff-less load diverges (the hole this card
             closes)"
    (let [[w0 eid _] (world-with-committed-planet {})
          w-cold     (run-ticks (withdraw-focus w0) 2)
          _          (is (nil? (ecs/get-component w-cold eid c/voxel-band))
                         "fixture sanity: no band under a withdrawn focus")
          w-live     (-> w-cold
                         (fold-op eid :uplift 1.0)
                         (fold-op eid :erosion 0.5)
                         (fold-op eid :volcanism 0.75))
          live-field (ecs/get-component w-live eid c/voxel-field)
          fdiffs     (ecs/get-component w-live eid c/voxel-field-diffs)]
      (is (= 3 (count fdiffs)) "one field-diff per folded op")
      (is (every? voxel/field-diff? fdiffs)
          "every emitted field-diff validates law.voxel/field-diff-schema")
      (is (= [:uplift :erosion :volcanism]
             (mapv (comp :verb :op) fdiffs))
          "stream order == fold order")
      (is (empty? (ecs/get-component w-live eid c/voxel-edit-diffs))
          "ZERO voxel diffs — an unresolved world leaves no voxel trace")
      (is (not= live-field field)
          "fixture sanity: the live field genuinely diverged from the seed")
      (let [save (load/save-state w-live eid)
            loaded (load/load-state save)]
        (is (= live-field (:field loaded))
            "save -> load: the replayed field is the live field, bit-for-bit")
        (is (= [] (:voxel-diffs loaded))))
      (let [seed-only (load/load-state {:candidate fixtures/rocky-habitable
                                        :field-diffs [] :voxel-diffs []})]
        (is (not= live-field (:field seed-only))
            "WITHOUT the field-diffs the load evaporates the bias — the Voxel 4 save hole")))))

;; --- 2. Resolved world: field AND band consistent after load ---------------------

(deftest resolved-world-field-and-band-round-trip
  (testing "a sculpt op on a RESOLVED world, then demote (the honest save
             path for a live band): save -> load reproduces the live field
             exactly AND the pre-demote band map-for-map — no cross-session
             field/band divergence"
    (let [[w-prom eid] (promote-fully)
          w-live       (fold-op w-prom eid :uplift 1.0)
          live-field   (ecs/get-component w-live eid c/voxel-field)
          band-before  (:voxels (ecs/get-component w-live eid c/voxel-band))
          _            (is (seq band-before) "fixture sanity: band resolved")
          w-demoted    (-> w-live withdraw-focus (run-until-drained eid 50))
          _            (is (nil? (ecs/get-component w-demoted eid c/voxel-band))
                           "the band fully demoted — its deviations are diffs now")
          save         (load/save-state w-demoted eid)
          _            (is (= 1 (count (:field-diffs save)))
                           "the one folded op persisted as one field-diff")
          _            (is (seq (:voxel-diffs save))
                           "the sculpt edits persisted as voxel diffs at fold-back")
          loaded       (load/load-state save (vec (sort (keys band-before))))]
      (is (= live-field (:field loaded))
          "the loaded field is the live field, bit-for-bit")
      (is (= band-before (:voxels loaded))
          "the loaded band is the pre-demote band, map-for-map — field and band consistent"))))

;; --- 3. Replay order preserved across interleaved ops and voxel edits ------------

(deftest replay-order-preserved-across-interleaved-ops-and-edits
  (testing "uplift -> a direct voxel edit (provenance :mine, standing in
             for any interleaved edit) -> erosion: the two streams keep
             their drain/record order, and save -> load reproduces BOTH
             the twice-biased field and the interleaved band state"
    (let [[w-prom eid] (promote-fully)
          w-op1        (fold-op w-prom eid :uplift 1.0)
          band1        (ecs/get-component w-op1 eid c/voxel-band)
          victim       (first (sort (keys (:voxels band1))))
          edited       (assoc (get (:voxels band1) victim)
                              :density 5000.0 :material :ore)
          w-edit       (ecs/put-component
                        w-op1 eid c/voxel-edit-queue
                        [{:kind       :apply-edits
                          :edits      [{:offset victim :after edited}]
                          :provenance :mine
                          :region     (get-in band1 [:spec :region])}])
          w-edited     (run-until-drained w-edit eid 50)
          w-live       (fold-op w-edited eid :erosion 1.0)
          live-field   (ecs/get-component w-live eid c/voxel-field)
          band-before  (:voxels (ecs/get-component w-live eid c/voxel-band))
          w-demoted    (-> w-live withdraw-focus (run-until-drained eid 50))
          save         (load/save-state w-demoted eid)]
      (is (= [:uplift :erosion]
             (mapv (comp :verb :op) (:field-diffs save)))
          "field-diff stream order == op fold order")
      (is (some #(= :mine (:provenance %)) (:voxel-diffs save))
          "the interleaved :mine edit persisted in drain/record order")
      (is (every? voxel/edit-diff? (:voxel-diffs save))
          "every voxel diff validates law.voxel/edit-diff-schema")
      (let [loaded (load/load-state save (vec (sort (keys band-before))))]
        (is (= live-field (:field loaded))
            "the twice-biased field replays bit-for-bit")
        (is (= band-before (:voxels loaded))
            "the interleaved band state replays map-for-map")
        (is (= edited (get (:voxels loaded) victim))
            "the interleaved edit landed exactly where the live world put it")))))

;; --- 4. Collision carve diffs still replay (regression) ---------------------------

(deftest collision-provenance-diffs-still-replay
  (testing "collision-provenance carve edits (:after nil) persist and
             replay through the SAME load path, and a collision emits NO
             field-diff — carving never biases the macro field (the
             no-band carve consequence stays unwired; carve.clj KNOWN
             GAPS), so the loaded field is the untouched seed"
    (let [[w-prom eid] (promote-fully)
          band0        (ecs/get-component w-prom eid c/voxel-band)
          victims      (into [] (take 3) (sort (keys (:voxels band0))))
          w-carve      (ecs/put-component
                        w-prom eid c/voxel-edit-queue
                        [{:kind       :apply-edits
                          :edits      (mapv (fn [o] {:offset o :after nil}) victims)
                          :provenance :collision
                          :region     (get-in band0 [:spec :region])}])
          w-live       (run-until-drained w-carve eid 50)
          band-before  (:voxels (ecs/get-component w-live eid c/voxel-band))
          w-demoted    (-> w-live withdraw-focus (run-until-drained eid 50))
          save         (load/save-state w-demoted eid)]
      (is (empty? (:field-diffs save))
          "a collision emits no field-diff — the carve gap is unwired, not half-persisted")
      (is (some #(= :collision (:provenance %)) (:voxel-diffs save))
          "the carve diff persisted")
      (let [loaded (load/load-state save (vec (sort (keys band-before))))]
        (is (= field (:field loaded))
            "no field bias: the loaded field is the regenerated seed exactly")
        (is (= band-before (:voxels loaded))
            "the carved band replays map-for-map, nil entries included")))))

;; --- 5. Schema discipline: every emitted record validates; corrupt saves fail loud

(deftest schema-validation-and-loud-failures
  (testing "an untouched world saves empty streams and loads back to the
             seed field exactly"
    (let [[w eid] (promote-fully)
          w-demoted (-> w withdraw-focus (run-until-drained eid 50))
          save (load/save-state w-demoted eid)]
      (is (= [] (:field-diffs save)))
      (is (= [] (:voxel-diffs save))
          "an untouched band demotes to zero diffs — regenerate-from-seed covers it")
      (is (= field (:field (load/load-state save))))))
  (testing "every emitted field-diff record validates, and its op validates
             law.voxel/sculpt-op-schema"
    (let [[w0 eid _] (world-with-committed-planet {})
          w-live (-> (run-ticks (withdraw-focus w0) 2)
                     (fold-op eid :uplift 1.0))
          fdiffs (ecs/get-component w-live eid c/voxel-field-diffs)]
      (is (seq fdiffs))
      (is (every? voxel/field-diff? fdiffs))
      (is (every? (comp voxel/sculpt-op? :op) fdiffs))))
  (testing "a malformed field-diff fails loudly at load, never as a silent
             field divergence"
    (is (thrown? clojure.lang.ExceptionInfo
                 (load/load-state {:candidate fixtures/rocky-habitable
                                   :field-diffs [{:op {:verb :not-a-verb} :tick 0}]
                                   :voxel-diffs []})))
    (is (thrown? clojure.lang.ExceptionInfo
                 (load/load-state {:candidate fixtures/rocky-habitable
                                   :field-diffs [{:op {:verb :uplift} :tick 0}]
                                   :voxel-diffs []}))))
  (testing "a malformed voxel diff fails loudly at load"
    (is (thrown? clojure.lang.ExceptionInfo
                 (load/load-state {:candidate fixtures/rocky-habitable
                                   :field-diffs []
                                   :voxel-diffs [{:region :not-a-region}]}))))
  (testing "save-state throws when the entity carries no candidate record —
             nothing to regenerate the seed from"
    (let [[w eid] (ecs/spawn (ecs/empty-world))]
      (is (thrown? clojure.lang.ExceptionInfo
                   (load/save-state w eid)))))
  (testing "save-state throws on a live band — demote before saving or
             in-band deviations are silently dropped"
    (let [[w eid] (promote-fully)]
      (is (some? (ecs/get-component w eid c/voxel-band))
          "fixture sanity: the band is resolved")
      (is (thrown? clojure.lang.ExceptionInfo
                   (load/save-state w eid))))))
