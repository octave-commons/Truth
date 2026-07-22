(ns domain.voxel-focus-test
  "Voxel 3: the CANONICAL named tests for the `:voxel-focus` promotion/
   demotion round-trip (card: kanban/tasks/voxel-focus-promotion-demotion.md),
   in the style of `domain.focus-conservation-test`: frozen world, run the
   system's `:run` fn, assert the write-set — then fold and re-run to walk
   the budgeted queue.

   Precision notes: band voxels are sampled verbatim from the deterministic
   field seed (`domain.voxel.band/seed-voxel`) and demotion diffs carry
   deviations verbatim, so mass and composition (mass per material — the
   substrate's own resolution; element-level composition lives in the
   resource field, which fold-back never rewrites) are conserved EXACTLY,
   and demote → re-promote round-trips map-for-map. Exact equality is
   asserted everywhere; there is no sampling noise to excuse tolerance."
  (:require
   [clojure.set]
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
   [law.voxel :as voxel]
   [shape.spatial :as sp]))

;; --- Fixtures --------------------------------------------------------------------

(def ^:private field
  "The seeded macro geology field of the rocky-habitable candidate — the
   same record the system caches as `c/voxel-field` on first sight of the
   committed world."
  (interior/seed-field fixtures/rocky-habitable))

(def ^:private radius-m
  (:radius-m field))

(defn- world-with-committed-planet
  "An empty world with an observer and one committed world entity at the
   origin carrying the rocky-habitable candidate record. Focus starts at
   `focus-offset` metres above the +x surface point; the attention shell is
   pinned small (1 km immediate) so moving the focus 2 km out withdraws the
   band deterministically."
  [{:keys [focus-offset focus-radius focus-intensity coherence committed?]
    :or {focus-offset 500.0 focus-radius 400.0 focus-intensity 1.0
         coherence 0.8 committed? true}}]
  (let [focus-pos (sp/vec3 (+ radius-m focus-offset) 0.0 0.0)
        [w _obs-eid] (player/spawn-observer (ecs/empty-world) focus-pos)
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
           (cond-> {c/position          (sp/vec3 0.0 0.0 0.0)
                    c/planet-candidate  fixtures/rocky-habitable}
             committed? (assoc c/commitment-state :committed)))]
    [w world-eid]))

(defn- run-system
  "Run the `:voxel-focus` system's :run fn directly and return its write-set."
  [world]
  ((:run (focus/voxel-focus-system)) world))

(defn- run-tick
  "Run the system and fold its write-set — the same landing
   `tick/apply-write-set` performs in the fan-out (handles the `removed`
   sentinel). Returns [world' write-set]."
  [world]
  (let [ws (run-system world)]
    [(tick/apply-write-set world ws) ws]))

(defn- run-ticks
  "Fold `n` ticks of the system. Each step is computed once."
  [world n]
  (loop [w world i 0]
    (if (< i n)
      (recur (first (run-tick w)) (inc i))
      w)))

(defn- run-until-drained
  "Fold ticks until the committed world's edit queue is empty (the band
   retarget has fully materialized / folded), or `max-ticks` is exceeded —
   which fails loudly rather than hanging on a queue bug. Always runs at
   least one tick: a fresh world has no queue component at all, which is
   'nothing enqueued YET', not 'drained'."
  [world world-eid max-ticks]
  (loop [w world i 0]
    (let [queue-empty? (and (pos? i)
                            (empty? (ecs/get-component w world-eid c/voxel-edit-queue)))]
      (if (or (>= i max-ticks) queue-empty?)
        w
        (recur (first (run-tick w)) (inc i))))))

(defn- promote-fully
  "A world whose band has completely materialized under the default
   fixture focus. Returns [world world-eid]."
  ([] (promote-fully {}))
  ([fixture]
   (let [[w eid] (world-with-committed-planet fixture)]
     [(run-until-drained w eid 50) eid])))

(defn- withdraw-focus
  "Move the observer's focus 2 km above the surface — beyond the 1 km
   immediate radius, so the band target becomes nil."
  [world]
  (player/update-observer
   world assoc :focus-position (sp/vec3 (+ radius-m 2.0e3) 0.0 0.0)))

(defn- restore-focus
  "Return the observer's focus to the fixture's 500 m hover — the band
   target is live again."
  [world]
  (player/update-observer
   world assoc :focus-position (sp/vec3 (+ radius-m 500.0) 0.0 0.0)))

;; --- 1. Band materializes under focus on a committed world -------------------------

(deftest band-materializes-under-focus-on-committed-world
  (testing "a committed world under focus: the field seed is cached, the
            band materializes in budgeted chunks, every voxel is schema-valid"
    (let [[w0 eid] (world-with-committed-planet {})
          [w1 ws1] (run-tick w0)]
      (is (= field (get-in ws1 [c/voxel-field eid]))
          "the field seed is cached on the committed world on the first tick")
      (is (some #(= :retarget (:kind %)) (get-in ws1 [c/voxel-edit-queue eid] []))
          "a retarget job was enqueued — the queue is the single drain path")
      (let [band1 (ecs/get-component w1 eid c/voxel-band)]
        (is (some? band1) "the band appears with its target spec on the first chunk")
        (is (<= (count (:voxels band1)) voxel/edit-chunk-voxels)
            "the first chunk respects the budget: at most edit-chunk-voxels voxels"))
      (let [w (run-until-drained w1 eid 50)
            b   (ecs/get-component w eid c/voxel-band)]
        (is (empty? (ecs/get-component w eid c/voxel-edit-queue))
            "the queue fully drains")
        (is (pos? (count (:voxels b))) "the band materialized voxels")
        (is (every? voxel/voxel? (filter some? (vals (:voxels b))))
            "every materialized voxel validates law.voxel/voxel-schema")
        (is (= 8000.0 (get-in b [:spec :depth-m]))
            "depth = reference × coherence × intensity = 1e4 × 0.8 × 1.0")))))

;; --- 2. No band without commitment (the gate) ---------------------------------------

(deftest no-band-without-commitment
  (testing "an uncommitted candidate world under the same focus: the system
            emits NOTHING — no field, no band, no queue, no diffs"
    (let [[w _eid] (world-with-committed-planet {:committed? false})
          ws (run-system w)]
      (is (= {} ws)
          "the whole subsystem is gated on the crossed commitment horizon"))))

;; --- 3. Band depth follows focus intensity ------------------------------------------

(deftest band-depth-follows-focus-intensity
  (testing "identical worlds but for focus-intensity: the deeper focus
            resolves a strictly deeper band — depth = reference × coherence
            × intensity exactly"
    (let [[w-deep ed] (world-with-committed-planet {:focus-intensity 1.0})
          [w-shallow es] (world-with-committed-planet {:focus-intensity 0.25})
          [w-deep' _] (run-tick w-deep)
          [w-shallow' _] (run-tick w-shallow)
          deep (get-in (ecs/get-component w-deep' ed c/voxel-band) [:spec :depth-m])
          shallow (get-in (ecs/get-component w-shallow' es c/voxel-band) [:spec :depth-m])]
      (is (= 8000.0 deep) "1e4 × 0.8 coherence × 1.0 intensity")
      (is (= 2000.0 shallow) "1e4 × 0.8 coherence × 0.25 intensity")
      (is (> deep shallow) "deeper focus = deeper resolved band"))))

;; --- 4. Promotion conserves mass and composition vs the seed field -------------------

(deftest promotion-conserves-mass-and-composition
  (testing "a fully materialized band: total mass and mass-per-material equal
            the seed field's over the same offsets, EXACTLY (voxels are
            sampled verbatim from the seed; sums pinned to sorted-offset order)"
    (let [[w eid] (promote-fully)
          b    (ecs/get-component w eid c/voxel-band)
          seed (into {} (map (fn [offset]
                               [offset (band/seed-voxel field (band/voxel-center offset))]))
                     (keys (:voxels b)))]
      (is (= (band/voxels-mass seed) (band/voxels-mass (:voxels b)))
          "band mass == seed field mass over the band volume, exactly")
      (is (= (band/voxels-material-masses seed)
             (band/voxels-material-masses (:voxels b)))
          "mass per material == the seed field's, exactly"))))

;; --- 5. Demotion conserves mass fold-back ---------------------------------------------

(deftest demotion-conserves-mass-fold-back
  (testing "an edited band demoted: the field + diffs representation after
            the fold carries exactly the mass the band carried before it —
            per offset, the diff (or the seed, for untouched offsets) holds
            the folded voxel verbatim"
    (let [[w eid] (promote-fully)
          b0   (ecs/get-component w eid c/voxel-band)
          victim (first (sort (keys (:voxels b0))))
          edited (assoc (get (:voxels b0) victim) :density 5000.0 :material :ore)
          w-edit (ecs/put-component
                  w eid c/voxel-edit-queue
                  [{:kind       :apply-edits
                    :edits      [{:offset victim :after edited}]
                    :provenance :sculpt
                    :region     (get-in b0 [:spec :region])}])
          w-edited (first (run-tick w-edit))
          band-before (ecs/get-component w-edited eid c/voxel-band)
          mass-before (band/voxels-mass (:voxels band-before))
          offsets (sort (keys (:voxels band-before)))
          w-demoted (-> w-edited withdraw-focus (run-until-drained eid 50))
          diffs (ecs/get-component w-demoted eid c/voxel-edit-diffs)
          replayed (band/replay-diffs diffs)
          mass-after (reduce + 0.0
                             (map (fn [offset]
                                    (band/voxel-mass
                                     (if (contains? replayed offset)
                                       (get replayed offset)
                                       (band/seed-voxel field (band/voxel-center offset)))))
                                  offsets))]
      (is (nil? (ecs/get-component w-demoted eid c/voxel-band))
          "the band was fully demoted")
      (is (= edited (get (:voxels band-before) victim))
          "fixture sanity: the edit drained into the band before demotion")
      (is (= mass-before mass-after)
          "band mass before fold == field+diffs mass after fold, exactly"))))

;; --- 6. Edit diffs: emitted for the edited region, NOTHING for the untouched ---------

(deftest edit-diffs-only-for-deviations
  (testing "demoting an UNTOUCHED band appends zero diffs — the regenerate-
            from-seed path covers untouched regions"
    (let [[w eid] (promote-fully)
          w' (-> w withdraw-focus (run-until-drained eid 50))]
      (is (empty? (ecs/get-component w' eid c/voxel-edit-diffs))
          "no deviation, no diff — nothing is persisted for untouched voxels")))
  (testing "demoting a band with ONE edited voxel appends exactly one diff
            whose delta holds exactly that offset — schema-valid, provenance
            carried from the edit job"
    (let [[w eid] (promote-fully)
          b0   (ecs/get-component w eid c/voxel-band)
          victim (first (sort (keys (:voxels b0))))
          edited (assoc (get (:voxels b0) victim) :density 5000.0 :material :ore)
          w-edit (ecs/put-component
                  w eid c/voxel-edit-queue
                  [{:kind       :apply-edits
                    :edits      [{:offset victim :after edited}]
                    :provenance :sculpt
                    :region     (get-in b0 [:spec :region])}])
          w' (-> w-edit run-tick first withdraw-focus (run-until-drained eid 50))
          diffs (ecs/get-component w' eid c/voxel-edit-diffs)]
      (is (= 1 (count diffs)) "one diff for the one edited region")
      (let [diff (first diffs)]
        (is (voxel/edit-diff? diff) "the diff validates law.voxel/edit-diff-schema")
        (is (= :sculpt (:provenance diff)) "provenance carried from the edit job")
        (is (= [{:offset victim :after edited}] (:delta diff))
            "the delta is exactly the deviation — every untouched voxel emits nothing")))))

;; --- 7. Demote -> re-promote round-trip is lossless -------------------------------------

(deftest demote-repromote-round-trip-is-lossless
  (testing "promote, edit, demote, re-promote: the re-materialized band
            equals the pre-demote band map-for-map — seed + diff replay
            reproduces every voxel, edited and untouched alike"
    (let [[w eid] (promote-fully)
          b0   (ecs/get-component w eid c/voxel-band)
          victim (first (sort (keys (:voxels b0))))
          edited (assoc (get (:voxels b0) victim) :density 5000.0 :material :ore)
          w-edit (ecs/put-component
                  w eid c/voxel-edit-queue
                  [{:kind       :apply-edits
                    :edits      [{:offset victim :after edited}]
                    :provenance :sculpt
                    :region     (get-in b0 [:spec :region])}])
          w-edited (first (run-tick w-edit))
          band-before (:voxels (ecs/get-component w-edited eid c/voxel-band))
          w-demoted (-> w-edited withdraw-focus (run-until-drained eid 50))
          w-repromoted (-> w-demoted restore-focus (run-until-drained eid 50))
          band-after (:voxels (ecs/get-component w-repromoted eid c/voxel-band))]
      (is (some? band-after) "the band re-materialized when focus returned")
      (is (= band-before band-after)
          "demote -> re-promote returns the same voxels, losslessly")
      (is (= edited (get band-after victim))
          "the edit survived the round trip through the diff"))))

;; --- 8. The budgeted queue drains <= budget and spills ----------------------------------

(deftest queue-drains-within-budget-and-spills
  (testing "fake-cost jobs: the drain applies jobs in FIFO order while the
            cumulative estimated cost fits the budget, and spills the rest —
            no wall-clock time anywhere"
    (let [applied (atom [])
          apply-job (fn [s job]
                      (swap! applied conj (:id job))
                      [s nil])
          jobs [{:id :a :cost-ms 1.0}
                {:id :b :cost-ms 1.5}
                {:id :c :cost-ms 0.4}]
          state (queue/drain {:queue jobs} 2.0 apply-job)]
      (is (= [:a] @applied) "only :a fit under 2.0 ms (1.0 + 1.5 spills)")
      (is (= 1.0 (:spent-ms state)) "spent is the estimated cost, not a clock")
      (is (= [:b :c] (mapv :id (:queue state)))
          "spilled jobs keep FIFO order for the next tick")))
  (testing "a job costlier than the whole budget drains ALONE (the only way
            it can ever run); the next job waits"
    (let [applied (atom [])
          apply-job (fn [s job] (swap! applied conj (:id job)) [s nil])
          state (queue/drain {:queue [{:id :big :cost-ms 5.0}
                                      {:id :small :cost-ms 0.1}]}
                             2.0 apply-job)]
      (is (= [:big] @applied) "the oversized head drained")
      (is (= [:small] (mapv :id (:queue state))) "the rest spilled")))
  (testing "a multi-tick job's remainder re-queues at the head and resumes
            while budget remains"
    (let [steps (atom [])
          apply-job (fn [s job]
                      (let [n (inc (long (:progress job 0)))]
                        (swap! steps conj n)
                        [s (when (< n 3) (assoc job :progress n :cost-ms 0.5))]))
          state (queue/drain {:queue [{:id :long :cost-ms 0.5 :progress 0}]}
                             2.0 apply-job)]
      (is (= [1 2 3] @steps) "three 0.5 ms steps drained in one 2.0 ms tick")
      (is (empty? (:queue state)) "the job completed")))
  (testing "enqueue replaces superseded retargets, keeping everything else"
    (let [q (queue/enqueue [{:kind :apply-edits :id :edit}
                            {:kind :retarget :spec :old}]
                           #{:retarget}
                           {:kind :retarget :spec :new})]
      (is (= [:edit] (mapv :id (filter #(= :apply-edits (:kind %)) q)))
          "foreign jobs are untouched")
      (is (= [:new] (mapv :spec (filter #(= :retarget (:kind %)) q)))
          "the stale retarget is replaced, not duplicated"))))

;; --- 9. The band respects the hard voxel cap (nit 1) -------------------------------------

(deftest band-respects-voxel-cap
  (testing "the default fixture: the fully materialized band never exceeds
            law.voxel/focus-band-max-voxels (the pre-fix h-cap leaked 8568 >
            8192 by ignoring the +e/2 radial rim and grid discretization)"
    (let [[w eid] (promote-fully)
          b (ecs/get-component w eid c/voxel-band)]
      (is (<= (count (:voxels b)) voxel/focus-band-max-voxels)
          "default band within the cap")
      (is (pos? (count (:voxels b))) "and non-empty")))
  (testing "the max-depth case (full coherence × full intensity, wide cone):
            depth clamps to the voxel-budget depth cap and the count still
            respects the cap — a deep band narrows, never overflows"
    (let [[w eid] (promote-fully {:focus-intensity 1.0 :coherence 1.0
                                  :focus-radius 1.0e4})
          b (ecs/get-component w eid c/voxel-band)]
      (is (= 8128.0 (get-in b [:spec :depth-m]))
          "depth = e × (max-layers − 1) = 64 × 127 — the deepest band whose
           minimum 4×4 patch still satisfies the count bound")
      (is (<= (count (:voxels b)) voxel/focus-band-max-voxels)
          "max-depth band within the cap"))))

;; --- 10. Carve (:after nil) round-trip is lossless (nit 2) -------------------------------

(deftest carve-round-trip-is-lossless
  (testing "promote, CARVE three voxels (:after nil), demote, re-promote:
            the nil entries round-trip map-for-map exactly like value
            edits — the diff carries the carve verbatim"
    (let [[w eid] (promote-fully)
          b0   (ecs/get-component w eid c/voxel-band)
          victims (into [] (take 3) (sort (keys (:voxels b0))))
          w-edit (ecs/put-component
                  w eid c/voxel-edit-queue
                  [{:kind       :apply-edits
                    :edits      (mapv (fn [o] {:offset o :after nil}) victims)
                    :provenance :sculpt
                    :region     (get-in b0 [:spec :region])}])
          w-edited (first (run-tick w-edit))
          band-before (:voxels (ecs/get-component w-edited eid c/voxel-band))]
      (is (every? (fn [o] (and (contains? band-before o)
                               (nil? (get band-before o))))
                  victims)
          "fixture sanity: the carve drained — offsets present, voxels nil")
      (let [w-demoted (-> w-edited withdraw-focus (run-until-drained eid 50))
            diffs (ecs/get-component w-demoted eid c/voxel-edit-diffs)]
        (is (= 1 (count diffs)) "one diff for the carved region")
        (is (= (set victims) (set (map :offset (:delta (first diffs)))))
            "the delta holds exactly the carved offsets")
        (is (every? #(nil? (:after %)) (:delta (first diffs)))
            "every delta entry is a carve (:after nil)")
        (let [w-repromoted (-> w-demoted restore-focus (run-until-drained eid 50))
              band-after (:voxels (ecs/get-component w-repromoted eid c/voxel-band))]
          (is (= band-before band-after)
              "carve demote -> re-promote returns the same voxels, nil
               entries included, map-for-map"))))))

;; --- 11. Provenance groups fold per chunk (nit 3) ----------------------------------------

(deftest provenance-groups-fold-per-chunk
  (testing "two edits with DIFFERENT provenances folded in the same demote
            chunk emit two diff records with disjoint deltas; replay order
            between them is irrelevant"
    (let [[w eid] (promote-fully)
          b0   (ecs/get-component w eid c/voxel-band)
          [v1 v2] (into [] (take 2) (sort (keys (:voxels b0))))
          e1   (assoc (get (:voxels b0) v1) :density 5000.0 :material :ore)
          e2   (assoc (get (:voxels b0) v2) :material :ice)
          region (get-in b0 [:spec :region])
          w-edit (ecs/put-component
                  w eid c/voxel-edit-queue
                  [{:kind :apply-edits :edits [{:offset v1 :after e1}]
                    :provenance :sculpt :region region}
                   {:kind :apply-edits :edits [{:offset v2 :after e2}]
                    :provenance :mine :region region}])
          w-edited (first (run-tick w-edit))
          w-demoted (-> w-edited withdraw-focus (run-until-drained eid 50))
          diffs (ecs/get-component w-demoted eid c/voxel-edit-diffs)
          by-prov (into {} (map (fn [d] [(:provenance d) d])) diffs)]
      (is (= 2 (count diffs)) "one diff per provenance group, not per edit")
      (is (= #{:sculpt :mine} (set (map :provenance diffs)))
          "both provenances carried")
      (is (= [{:offset v1 :after e1}] (get-in by-prov [:sculpt :delta]))
          "the sculpt delta holds exactly the sculpt edit")
      (is (= [{:offset v2 :after e2}] (get-in by-prov [:mine :delta]))
          "the mine delta holds exactly the mine edit")
      (is (empty? (clojure.set/intersection
                   (set (map :offset (get-in by-prov [:sculpt :delta])))
                   (set (map :offset (get-in by-prov [:mine :delta])))))
          "the deltas are disjoint")
      (is (= (band/replay-diffs diffs) (band/replay-diffs (reverse diffs)))
          "replay order between disjoint-provenance diffs is irrelevant"))))

;; --- 12. edits->jobs chunks large edit sets, order-preserving (nit 4) ---------------------

(deftest edits-jobs-chunks-preserve-order
  (testing "an edit set larger than law.voxel/max-edits-per-job splits into
            budget-fitting jobs whose concatenated edits equal the input in
            order — and draining them applies in exactly that order"
    (let [n     (+ (* 2 voxel/max-edits-per-job) 100)
          edits (mapv (fn [i] {:offset [i 0 0] :after nil}) (range n))
          jobs  (queue/edits->jobs edits {:provenance :sculpt})]
      (is (= 3 (count jobs)) "2 full chunks + the 100-edit remainder")
      (is (every? (fn [j] (<= (count (:edits j)) voxel/max-edits-per-job))
                  jobs)
          "no job exceeds max-edits-per-job")
      (is (every? (fn [j] (<= (queue/job-cost-ms j)
                              voxel/edit-budget-ms-per-tick))
                  jobs)
          "every chunk fits the 2 ms budget — no oversized-head escape")
      (is (= edits (into [] (mapcat :edits) jobs))
          "chunking preserves the input edit order")
      (let [applied (atom [])
            apply-job (fn [s job]
                        (swap! applied into (map :offset (:edits job)))
                        [s nil])
            one-tick (queue/drain {:queue jobs} voxel/edit-budget-ms-per-tick
                                  apply-job)]
        (is (= (mapv :offset (subvec edits 0 voxel/max-edits-per-job)) @applied)
            "one 2.0 ms chunk drains per tick; the rest spills — the cap holds")
        (is (= 2 (count (:queue one-tick))) "two chunks spilled")
        (let [drained (loop [s one-tick n 0]
                        (if (or (empty? (:queue s)) (> n 10))
                          s
                          (recur (queue/drain (dissoc s :spent-ms)
                                              voxel/edit-budget-ms-per-tick
                                              apply-job)
                                 (inc n))))]
          (is (= (mapv :offset edits) @applied)
              "drain order == input order, across chunk boundaries and ticks")
          (is (empty? (:queue drained)) "every chunk eventually drains")))))
  (testing "edits->jobs validates: a bad provenance or a malformed edit
            throws at enqueue time, not mid-drain"
    (is (thrown? clojure.lang.ExceptionInfo
                 (queue/edits->jobs [{:offset [0 0 0] :after nil}]
                                    {:provenance :not-a-provenance})))
    (is (thrown? clojure.lang.ExceptionInfo
                 (queue/edits->jobs [{:offset ["x" 0 0] :after nil}]
                                    {:provenance :sculpt})))))

;; --- 13. Single-writer / registry declarations -------------------------------------------

(deftest single-writer-registry-declarations
  (testing "the registry holds: no component has more than one writer"
    (is (= {} (reg/write-conflicts reg/systems))))
  (testing "the system's :writes are sourced from the registry — declaration
            and emitter cannot drift"
    (is (= (reg/registry-writes :voxel-focus)
           (:writes (focus/voxel-focus-system))))
    (is (= #{c/voxel-field c/voxel-band c/voxel-edit-queue c/voxel-edit-diffs}
           (:writes (focus/voxel-focus-system)))
        "exactly the four voxel columns, nothing else — no c/matter-state")))
