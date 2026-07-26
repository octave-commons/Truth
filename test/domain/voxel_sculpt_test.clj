(ns domain.voxel-sculpt-test
  "Voxel 4: god-scale sculpting ops (palette → field bias), in the frozen-
   world rigor of `domain.voxel-focus-test`: build the world, run the
   `:voxel-sculpt` and `:voxel-focus` systems' :run fns against the SAME
   frozen world (the parallel fan-out), fold the merged write-set, clear
   the op key serially — production timing exactly. Card:
   kanban/tasks/voxel-god-scale-sculpting-ops.md; design
   docs/designs/planetary-voxel-substrate.md §5 (macro-drives-local).

   Conservation precision: sculpt edits REDISTRIBUTE band mass (never
   create it). The moved amounts are exact by construction, but the band-
   wide double sums suffer FP non-associativity, so band/resource totals
   assert a relative tolerance of 1e-9 — the `domain.interior` layer-mass
   precedent ('up to double rounding'). Volcanism touches no density at
   all, so its band-mass invariance is EXACT."
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
   [domain.voxel.focus :as focus]
   [domain.voxel.queue :as queue]
   [domain.voxel.sculpt :as sculpt]
   [law.narrowing :as law-narrowing]
   [law.voxel :as voxel]
   [shape.spatial :as sp]))

;; --- Fixtures --------------------------------------------------------------------

(def ^:private field
  "The seeded macro geology field of the rocky-habitable candidate."
  (interior/seed-field fixtures/rocky-habitable))

(def ^:private radius-m
  (:radius-m field))

(defn- world-with-committed-planet
  "An empty world with an observer (Resonance-loaded, planetary palette
   armed) and one committed world entity at the origin carrying the
   rocky-habitable candidate record. Focus hovers `focus-offset` metres
   above the +x surface point — the sculpt anchor is +x. Returns
   [world world-eid obs-eid]."
  [{:keys [focus-offset focus-radius focus-intensity coherence resonance palette]
    :or {focus-offset 500.0 focus-radius 400.0 focus-intensity 1.0
         coherence 0.8 resonance 20.0 palette law-narrowing/planetary-palette}}]
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
        w (if palette
            (ecs/put-component w obs-eid c/palette palette)
            w)
        [w world-eid] (ecs/spawn w)
        w (ecs/put-components
           w world-eid
           {c/position          (sp/vec3 0.0 0.0 0.0)
            c/planet-candidate  fixtures/rocky-habitable
            c/commitment-state  :committed})]
    [w world-eid obs-eid]))

(defn- run-tick
  "One production-faithful tick for the sculpt+band pair: BOTH systems run
   against the SAME frozen world (the parallel fan-out), their write-sets
   merge (disjoint component types), and the sculpt-op world key clears
   SERIALLY post-fold. Returns [world' merged-write-set]."
  [world]
  (let [ws (merge ((:run (sculpt/sculpt-system)) world)
                  ((:run (focus/voxel-focus-system)) world))]
    [(sculpt/clear-sculpt-ops (tick/apply-write-set world ws)) ws]))

(defn- run-ticks
  "Fold `n` ticks of the pair."
  [world n]
  (loop [w world i 0]
    (if (< i n)
      (recur (first (run-tick w)) (inc i))
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
        (recur (first (run-tick w)) (inc i))))))

(defn- promote-fully
  "A world whose band has completely materialized under the default
   fixture focus. Returns [world world-eid obs-eid]."
  ([] (promote-fully {}))
  ([fixture]
   (let [[w eid obs-eid] (world-with-committed-planet fixture)]
     [(run-until-drained w eid 50) eid obs-eid])))

(defn- withdraw-focus
  "Move the observer's focus 2 km above the surface — beyond the 1 km
   immediate radius, so no band target exists."
  [world]
  (player/update-observer
   world assoc :focus-position (sp/vec3 (+ radius-m 2.0e3) 0.0 0.0)))

(defn- resource-mass-sum
  "Total `:total-mass` across the field's resource cells."
  [fld]
  (reduce + 0.0 (map (fn [cell] (double (:total-mass cell))) (:resources fld))))

;; --- 1. Uplift biases the field and drives derived local edits under the band ----

(deftest uplift-biases-field-and-sculpts-band
  (testing "a paid uplift op under a resolved band: the request lands on
            the channel one tick, the NEXT tick the field is biased
            (exactly the pure apply-uplift result) and the derived edits
            drain into the band with :sculpt provenance — mass moved
            deep→shallow, redistributed never created"
    (let [[w-prom eid _] (promote-fully)
          band0  (ecs/get-component w-prom eid c/voxel-band)
          mass0  (band/voxels-mass (:voxels band0))
          field0 (ecs/get-component w-prom eid c/voxel-field)
          w-req  (sculpt/request-op w-prom :uplift 1.0)
          _      (is (= 1 (count (:voxel/sculpt-ops w-req)))
                     "the paid op is on the actuation key")
          [w1 _] (run-tick w-req)
          ops    (ecs/get-component w1 eid c/voxel-sculpt-request)]
      (is (= 1 (count ops)) "the request component carries the op after tick 1")
      (is (= :uplift (:verb (first ops))))
      (is (= field0 (ecs/get-component w1 eid c/voxel-field))
          "the field is NOT yet biased at tick 1 — the fold reads the request one Jacobi tick stale")
      (let [[w2 _] (run-tick w1)
            field2 (ecs/get-component w2 eid c/voxel-field)
            band2  (ecs/get-component w2 eid c/voxel-band)
            mass2  (band/voxels-mass (:voxels band2))]
        (is (= (:field (sculpt/apply-uplift field0 (first ops))) field2)
            "the biased field is exactly the pure apply-uplift result")
        (is (empty? (ecs/get-component w2 eid c/voxel-edit-queue))
            "the derived edits drained within budget the same tick")
        (is (seq (:touched band2)) "edits landed under the band")
        (is (every? #(= :sculpt %) (vals (:touched band2)))
            "every touched voxel carries :sculpt provenance — the queue channel, never a god-finger poke")
        (let [denser (filter (fn [[o v]] (> (double (:density v))
                                            (double (:density (get (:voxels band0) o)))))
                             (:voxels band2))
              lighter (filter (fn [[o v]] (< (double (:density v))
                                             (double (:density (get (:voxels band0) o)))))
                              (:voxels band2))]
          (is (seq denser) "shallow voxels gained mass")
          (is (seq lighter) "deep voxels lost mass"))
        (is (< (Math/abs (- mass2 mass0)) (* 1.0e-9 mass0))
            "band mass redistributed, not created (exact up to double rounding)")
        (let [[w3 _] (run-tick w2)]
          (is (= field2 (ecs/get-component w3 eid c/voxel-field))
              "the op folds EXACTLY ONCE — the request clears after its fold tick"))))))

;; --- 2. Erosion redistributes resource-cell and band mass -------------------------

(deftest erosion-transports-mass-never-creates
  (testing "a paid erosion op: the donor resource cell exports mass to the
            nearest other cell (field `:total-mass` sum invariant), and the
            band's donor column tops feed the rim columns — the field
            effect is exactly apply-erosion's, band mass conserved"
    (let [[w-prom eid _] (promote-fully)
          band0  (ecs/get-component w-prom eid c/voxel-band)
          mass0  (band/voxels-mass (:voxels band0))
          field0 (ecs/get-component w-prom eid c/voxel-field)
          sum0   (resource-mass-sum field0)
          w-req  (sculpt/request-op w-prom :erosion 1.0)
          [w1 _] (run-tick w-req)
          ops    (ecs/get-component w1 eid c/voxel-sculpt-request)
          [w2 _] (run-tick w1)
          field2 (ecs/get-component w2 eid c/voxel-field)
          band2  (ecs/get-component w2 eid c/voxel-band)
          mass2  (band/voxels-mass (:voxels band2))]
      (is (= :erosion (:verb (first ops))))
      (is (= (:field (sculpt/apply-erosion field0 (first ops))) field2)
          "the biased field is exactly the pure apply-erosion result")
      (let [changed (filter (fn [[c0 c2]] (not= (:total-mass c0) (:total-mass c2)))
                            (map vector (:resources field0) (:resources field2)))]
        (is (= 2 (count changed))
            "exactly two cells changed: the donor and the receiver")
        (is (== (reduce + 0.0 (map (fn [[c0 _]] (double (:total-mass c0))) changed))
                (reduce + 0.0 (map (fn [[_ c2]] (double (:total-mass c2))) changed)))
            "the pair's combined mass is EXACTLY invariant — one debit, one credit of the same double"))
      (is (< (Math/abs (- (resource-mass-sum field2) sum0)) (* 1.0e-12 sum0))
          "field resource mass transported, not created")
      (is (< (Math/abs (- mass2 mass0)) (* 1.0e-9 mass0))
          "band mass redistributed, not created")
      (is (every? #(= :sculpt %) (vals (:touched band2)))
          "every touched voxel carries :sculpt provenance")
      (is (seq (filter (fn [[o v]] (< (double (:density v))
                                      (double (:density (get (:voxels band0) o)))))
                       (:voxels band2)))
          "donor column tops lost mass")
      (is (seq (filter (fn [[o v]] (> (double (:density v))
                                      (double (:density (get (:voxels band0) o)))))
                       (:voxels band2)))
          "rim columns gained the sediment"))))

;; --- 3. Volcanism melts without touching mass --------------------------------------

(deftest volcanism-heats-and-melts-mass-invariant
  (testing "a paid volcanism op at magnitude 1: the nearest upwelling cell
            doubles its speed, every resolved crust voxel (seed 513 K, all
            within the 512 m disc) gains 2000 K and crosses the 1400 K melt
            threshold — state :melt, cohesion 0 — and band mass is EXACTLY
            invariant (heat is the paid effect, not mass)"
    (let [[w-prom eid _] (promote-fully)
          band0  (ecs/get-component w-prom eid c/voxel-band)
          mass0  (band/voxels-mass (:voxels band0))
          field0 (ecs/get-component w-prom eid c/voxel-field)
          w-req  (sculpt/request-op w-prom :volcanism 1.0)
          [w1 _] (run-tick w-req)
          ops    (ecs/get-component w1 eid c/voxel-sculpt-request)
          [w2 _] (run-tick w1)
          field2 (ecs/get-component w2 eid c/voxel-field)
          ;; A whole-band melt is ~4 chunks of edits: they drain one 2 ms
          ;; chunk per tick (the budgeted-spill discipline), so walk the
          ;; queue before asserting on the band.
          w-drained (run-until-drained w2 eid 20)
          band2  (ecs/get-component w-drained eid c/voxel-band)
          mass2  (band/voxels-mass (:voxels band2))]
      (is (= :volcanism (:verb (first ops))))
      (is (= (:field (sculpt/apply-volcanism field0 (first ops))) field2)
          "the biased field is exactly the pure apply-volcanism result")
      (let [cells0 (filter #(= :upwelling (:flow %)) (:convection field0))
            cells2 (filter #(= :upwelling (:flow %)) (:convection field2))
            sped   (filter (fn [[c0 c2]] (not= (:speed c0) (:speed c2)))
                           (map vector cells0 cells2))]
        (is (= 1 (count sped)) "exactly one upwelling cell was boosted")
        (is (== 2.0 (/ (double (:speed (second (first sped))))
                       (double (:speed (ffirst sped)))))
            "speed × (1 + lever × magnitude) = ×2 at magnitude 1"))
      (is (== mass0 mass2)
          "band mass EXACTLY invariant — no density was touched")
      (is (every? (fn [v] (= :melt (:state v)))
                  (filter some? (vals (:voxels band2))))
          "every resolved voxel crossed the melt threshold")
      (is (every? (fn [v] (== 0.0 (double (:cohesion v))))
                  (filter some? (vals (:voxels band2))))
          "molten voxels carry zero shear strength")
      (is (every? (fn [[o v]] (== (+ (double (:temperature (get (:voxels band0) o)))
                                     voxel/sculpt-volcanism-thermal-lever-k)
                                  (double (:temperature v))))
                  (:voxels band2))
          "every voxel gained exactly magnitude × the thermal lever"))))

;; --- 4. Macro-drives-local: no band resolved → field changes, ZERO voxel edits -----

(deftest no-band-field-changes-zero-edits
  (testing "the macro-drives-local rule: with the focus withdrawn (no band
            resolved), a paid uplift op STILL biases the field — and emits
            ZERO voxel edits, ZERO diffs, an empty queue: the unresolved
            world carries the change statistically"
    (let [[w0 eid _] (world-with-committed-planet {})
          w-cold   (run-ticks (withdraw-focus w0) 2)
          _        (is (nil? (ecs/get-component w-cold eid c/voxel-band))
                       "fixture sanity: no band under a withdrawn focus")
          field0   (ecs/get-component w-cold eid c/voxel-field)
          w-req    (sculpt/request-op w-cold :uplift 1.0)
          [w1 _]   (run-tick w-req)
          [w2 _]   (run-tick w1)
          field2   (ecs/get-component w2 eid c/voxel-field)]
      (is (some? field0) "the field seed is cached even without a band")
      (is (not= field0 field2) "the field still biased — the op is a field effect first")
      (is (nil? (ecs/get-component w2 eid c/voxel-band))
          "still no band")
      (is (empty? (ecs/get-component w2 eid c/voxel-edit-queue))
          "ZERO voxel edits enqueued")
      (is (empty? (ecs/get-component w2 eid c/voxel-edit-diffs))
          "ZERO diffs — nothing persisted for an unresolved region"))))

;; --- 5. Derived edits flow through the queue chunked, provenance :sculpt ------------

(deftest edits-flow-chunked-with-sculpt-provenance
  (testing "edits->sculpt-jobs splits an oversized edit set into
            budget-fitting :apply-edits jobs — every one :sculpt
            provenance, none exceeding max-edits-per-job or the 2 ms
            budget (NEVER the oversized-head escape), concatenation
            preserving input order"
    (let [n     (+ (* 2 voxel/max-edits-per-job) 100)
          edits (mapv (fn [i] {:offset [i 0 0] :after nil}) (range n))
          jobs  (sculpt/edits->sculpt-jobs edits {:center [0.0 0.0 1.0] :radius 1.0})]
      (is (= 3 (count jobs)) "2 full chunks + the 100-edit remainder")
      (is (every? #(= :apply-edits (:kind %)) jobs))
      (is (every? #(= :sculpt (:provenance %)) jobs)
          "every job carries :sculpt provenance")
      (is (every? #(<= (count (:edits %)) voxel/max-edits-per-job) jobs)
          "no job exceeds max-edits-per-job")
      (is (every? #(<= (queue/job-cost-ms %) voxel/edit-budget-ms-per-tick) jobs)
          "every chunk fits the 2 ms budget")
      (is (= edits (into [] (mapcat :edits) jobs))
          "chunking preserves the derived edit order"))))

;; --- 6. Resonance cost law and the spend/gating actuation ---------------------------

(deftest resonance-cost-monotone-and-spend-wiring
  (testing "op-cost is strictly monotone in magnitude for every verb, and
            positive throughout"
    (doseq [verb [:uplift :erosion :volcanism]]
      (is (< (sculpt/op-cost verb 0.1)
             (sculpt/op-cost verb 0.5)
             (sculpt/op-cost verb 1.0))
          (str verb ": cost rises with magnitude"))
      (is (pos? (sculpt/op-cost verb 1.0e-9)) (str verb ": no free op")))
    (is (thrown? clojure.lang.ExceptionInfo (sculpt/op-cost :not-a-verb 0.5))
        "an unknown verb fails loudly, never a free op"))
  (testing "request-op spends exactly the cost in Resonance and stamps the
            paid op; the same op re-requested beyond means is a no-op"
    (let [[w0 _eid _] (world-with-committed-planet {:resonance 4.0})
          cost   (sculpt/op-cost :uplift 1.0)
          w-req  (sculpt/request-op w0 :uplift 1.0)]
      (is (== 3.0 cost) "uplift at magnitude 1: base 1 + 2×1")
      (is (== (- 4.0 cost) (double (:resonance (player/get-observer w-req))))
          "Resonance debited exactly the cost")
      (is (= 1 (count (:voxel/sculpt-ops w-req))) "the op is queued")
      (let [op (first (:voxel/sculpt-ops w-req))]
        (is (voxel/sculpt-op? op) "the op validates law.voxel/sculpt-op-schema")
        (is (== cost (double (:cost op))) "the paid cost is stamped on the op"))
      (let [w-again (sculpt/request-op w-req :uplift 1.0)]
        (is (identical? w-req w-again)
            "1.0 Resonance left < 3.0 cost: the world is returned UNCHANGED")))
    (let [[w0 _ _] (world-with-committed-planet {:palette law-narrowing/genesis-palette})]
      (is (identical? w0 (sculpt/request-op w0 :uplift 1.0))
          "the Genesis palette has no Tectonics slot — gated"))
    (let [[w0 _ _] (world-with-committed-planet {:palette nil})]
      (is (identical? w0 (sculpt/request-op w0 :uplift 1.0))
          "no palette at all — gated"))))

;; --- 7. Single-writer / declared reads+writes ----------------------------------------

(deftest single-writer-registry-declarations
  (testing "the registry holds: no component has more than one writer"
    (is (= {} (reg/write-conflicts reg/systems))))
  (testing "the :voxel-sculpt system's :writes are sourced from the registry
            and own exactly the request channel — no c/matter-state, none of
            :voxel-focus's columns"
    (is (= (reg/registry-writes :voxel-sculpt)
           (:writes (sculpt/sculpt-system))))
    (is (= #{c/voxel-sculpt-request} (:writes (sculpt/sculpt-system))))
    (is (not (contains? (:writes (sculpt/sculpt-system)) c/matter-state))))
  (testing ":voxel-focus declares the request channel in its :reads and still
            owns exactly its four columns"
    (let [entry (some #(when (= :voxel-focus (:id %)) %) reg/systems)]
      (is (contains? (:reads entry) c/voxel-sculpt-request)
          "the fold's read is declared — no undeclared side-channel")
      (is (= #{c/voxel-field c/voxel-band c/voxel-edit-queue c/voxel-edit-diffs
               c/voxel-field-diffs}
             (:writes entry))))))
