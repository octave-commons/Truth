(ns domain.voxel.focus
  "The `:voxel-focus` fan-out system (Voxel 3,
   kanban/tasks/voxel-focus-promotion-demotion.md; design
   docs/designs/planetary-voxel-substrate.md §7 RESOLVED 2026-07-22): the
   Player Focus promotion/demotion conservation machinery extended one
   level deeper — a voxel band materializes under the observer's focus from
   the committed world's macro geology field and demotes back, conserving
   mass and composition exactly (the representational invariant: band ≡
   field + diffs over the band volume; see `domain.voxel.band`).

   GATED ON COMMITMENT: with no `:committed` `c/commitment-state` anywhere,
   the system emits NOTHING — the band is the crossed-horizon world's
   interior, not a Phase 0 feature. On the first tick a committed world is
   seen, its `domain.interior/seed-field` is cached as `c/voxel-field`
   (the regenerable seed, stored once so materialization never re-derives
   it per tick).

    ONE SYSTEM FOR ALL FIVE COMPONENTS — field, band, queue, diffs,
    field-diffs — for the same reason `:focus-zone` is one system for
    promotion AND demotion: both directions write the same columns, and
    two ids would trip `domain.ecs.registry/write-conflicts`. The system
    reads its own prior output one tick stale (ordinary Jacobi lag, like
    `:neighbor-cache`).

   THE QUEUE IS THE SINGLE DRAIN PATH (owner decision §7.1): every voxel
   edit — band retargets here, sculpt/mine/construct/collision edits in
   slices 4-6 — is a job in `c/voxel-edit-queue`, drained under
   `law.voxel/edit-budget-ms-per-tick` by `domain.voxel.queue/drain`.
   Retargets are chunked to `law.voxel/edit-chunk-voxels` so the cap holds
   for band churn exactly as for later edits; a big retarget visibly sweeps
   over several ticks. Producers enqueue by writing their OWN
   producer-suffixed request component (the `accel.*` influence-registry
   pattern — one writer per component); this system's `:reads` grows to
   fold them, exactly as the integrator's accumulate lists grow. Voxel 4
   is the first such producer: `c/voxel-sculpt-request` (written by the
   `:voxel-sculpt` system, `domain.voxel.sculpt`) folds as a field bias
   plus derived `:apply-edits` jobs, provenance `:sculpt`. Tests inject
   jobs by writing `c/voxel-edit-queue` directly into the frozen fixture
   world.

   EDIT DIFFS ARE COMPONENTS, NOT LEDGER EVENTS (owner decision §7.3): the
   accumulated `law.voxel/edit-diff-schema` vector is the world's SAVE
   REPRESENTATION — continuous state read back every tick for seed+replay
   materialization, written by exactly one system, and precisely the kind
    of per-tick-resident state the double-buffer component store exists
    for. Ledger events are discrete occurrences consumed by handlers
    (`emit-handoff-event` precedent); nothing CONSUMES a diff event — the
    diffs themselves are the state. Demotion emits a diff per provenance
    group per chunk, and ONLY for deviations from the regenerated seed:
    untouched regions emit nothing because regenerate-from-seed covers them.

    FIELD-DIFFS ARE THE MACRO HALF OF THE SAME STORY (§7.3 EXTENDED
    2026-07-22, card voxel-field-bias-persistence): every sculpt op the
    fold applies to the field appends one `law.voxel/field-diff-schema`
    record — the op verbatim plus the fold tick — to
    `c/voxel-field-diffs`, in fold order. The op IS the diff: replay
    re-applies the same pure `domain.voxel.sculpt/apply-op` in stream
    order and reproduces the bias bit-for-bit, so a load
    (`domain.voxel.load`) is regenerate seed + replay field-diffs +
    replay voxel diffs, and no cross-session field/band divergence
    survives it.

   Reads the observer, the committed world's `c/planet-candidate` /
   `c/position`, and its own four components. Never writes `c/matter-state`
   or any other system's column."
   (:require
    [domain.ecs.components :as c]
    [domain.ecs.core :as ecs]
    [domain.ecs.registry :as reg]
    [domain.ecs.tick :as tick]
    [domain.interior :as interior]
    [domain.player :as player]
    [domain.voxel.band :as band]
    [domain.voxel.carve :as carve]
    [domain.voxel.queue :as queue]
    [domain.voxel.sculpt :as sculpt]
    [law.voxel :as voxel]
    [shape.spatial :as sp]))

;; --- World lookups ---------------------------------------------------------------

(defn- committed-world-eid
  "The eid of the committed world, or nil — the same hard-irreversible
   marker scan `domain.narrowing` uses: a `:committed` `c/commitment-state`
   anywhere means the horizon has been crossed."
  [world]
  (some (fn [[eid state]] (when (= :committed state) eid))
        (get-in world [:components c/commitment-state] {})))

;; --- Job execution (the queue's apply fns) ----------------------------------------

(defn- validate-diff!
  "Throw `ex-info` when `diff` fails `law.voxel/edit-diff-schema` — the
   `domain.interior/validate-field!` precedent: a malformed diff would
   corrupt every regenerate-and-replay load downstream, so fail at
   construction, not at replay."
  [diff]
  (when-not (voxel/edit-diff? diff)
    (throw (ex-info "domain.voxel.focus: emitted diff fails law.voxel/edit-diff-schema"
                    {:diff diff})))
  diff)

(defn- validate-field-diff!
  "Throw `ex-info` when `fdiff` fails `law.voxel/field-diff-schema` —
   the `validate-diff!` precedent applied to the macro half of the save
   stream: a malformed field-diff would corrupt every load's field
   replay, so fail at construction, not at replay."
  [fdiff]
  (when-not (voxel/field-diff? fdiff)
    (throw (ex-info "domain.voxel.focus: emitted field-diff fails law.voxel/field-diff-schema"
                    {:field-diff fdiff})))
  fdiff)

(defn- step-retarget-demote
  "One demote chunk of a `:retarget` job: fold up to
   `law.voxel/edit-chunk-voxels` resolved offsets back into the field +
   diff representation. Lazily snapshots the pending offsets from the LIVE
   band on the first step, so a retarget issued over a partially-promoted
   band folds exactly what is resolved. When the band is empty the demote
   is complete: the stale band record is cleared and the job either
   finishes (spec nil — a pure withdraw) or rolls into the promote phase."
  [field tick state job]
  (let [spec (:spec job)
        b    (:band state)]
    (if (or (nil? b) (empty? (:voxels b)))
      [(assoc state :band nil)
       (when (some? spec)
         (assoc job :phase :promote :pending nil :voxel-count 0))]
      (let [pending  (or (:pending job) (vec (sort (keys (:voxels b)))))
            taken    (into [] (take voxel/edit-chunk-voxels pending))
            left     (subvec pending (min (count pending) voxel/edit-chunk-voxels))
            replayed (band/replay-diffs (:diffs state))
            {:keys [voxels' touched' diffs]}
            (band/fold-chunk field replayed tick (:voxels b) (:touched b) taken)]
        [(-> state
             (assoc :band (assoc b :voxels voxels' :touched touched'))
             (update :diffs into (map validate-diff! diffs)))
         (assoc job :phase :demote :pending left :voxel-count (count taken))]))))

(defn- step-retarget-promote
  "One promote chunk of a `:retarget` job: materialize up to
   `law.voxel/edit-chunk-voxels` offsets of the target volume as seed +
   replay (`domain.voxel.band/materialize`). The full offset vector is
   enumerated once, lazily, and carried on the job. The band record appears
   on the first chunk with its final `:spec` — a partially-materialized
   band already answers 'what is the target' so a steady focus never
   re-enqueues. Throws when the live band carries a DIFFERENT spec: the
   retarget replacement rule (`maybe-enqueue-retarget`) makes that
   unreachable, so reaching it is a contract violation, not a case."
  [field state job]
  (let [spec (:spec job)
        b    (:band state)]
    (when (and (some? b) (not= (:spec b) spec))
      (throw (ex-info "domain.voxel.focus: promote chunk over a band with a different spec — stale retarget was not replaced"
                      {:band-spec (:spec b) :job-spec spec})))
    (let [pending (or (:pending job) (band/band-offsets field spec))
          taken   (into [] (take voxel/edit-chunk-voxels pending))
          left    (subvec pending (min (count pending) voxel/edit-chunk-voxels))
          fresh   (band/materialize field (:diffs state) taken)
          b'      (-> (or b {:spec spec :voxels {} :touched {}})
                      (update :voxels merge fresh))]
      [(assoc state :band b')
       (when (seq left)
         (assoc job :phase :promote :pending left :voxel-count (count taken)))])))

(defn- step-apply-edits
  "An `:apply-edits` job — the channel slices 4-6 (sculpt/mine/construct/
   collision) enqueue into. Edits landing on resolved band voxels update
   the band in place and record their provenance in `:touched` (their diffs
   are emitted at fold-back, grouped by provenance); edits anywhere else
   append a diff IMMEDIATELY — an unresolved region needs no materialization
   to be edited, because the diff IS the edit. `:after nil` carves: the
   band keeps the offset with a nil voxel so fold-back sees the deviation.
   Validates every edit against `law.voxel/voxel-edit-schema` and requires
   the job's `:region` when any edit lands outside the band."
  [tick state job]
  (doseq [edit (:edits job)]
    (when-not (voxel/voxel-edit? edit)
      (throw (ex-info "domain.voxel.focus: :apply-edits job fails law.voxel/voxel-edit-schema"
                      {:edit edit :job job}))))
  (let [b        (:band state)
        in-band? (fn [edit] (and (some? b)
                                 (contains? (:voxels b) (:offset edit))))
        {in true out false} (group-by in-band? (:edits job))
        b' (when (some? b)
             (reduce (fn [b edit]
                       (-> b
                           (assoc-in [:voxels (:offset edit)] (:after edit))
                           (assoc-in [:touched (:offset edit)] (:provenance job))))
                     b in))
        out-diff (when (seq out)
                   (when-not (:region job)
                     (throw (ex-info "domain.voxel.focus: :apply-edits job with out-of-band edits requires :region"
                                     {:job job})))
                   (validate-diff!
                    {:region     (:region job)
                     :delta      (mapv #(select-keys % [:offset :after]) out)
                     :provenance (:provenance job)
                     :tick       (long (or tick 0))}))]
    [(cond-> (assoc state :band b')
       out-diff (update :diffs conj out-diff))
     nil]))

(defn- apply-job
  "The `domain.voxel.queue/drain` apply fn: dispatch one job by `:kind`,
   returning [state' remainder-or-nil]."
  [field tick state job]
  (case (:kind job)
    :retarget    (let [phase (or (:phase job)
                                 (if (some? (:band state)) :demote :promote))]
                   (if (= :promote phase)
                     (step-retarget-promote field state job)
                     (step-retarget-demote field tick state job)))
    :apply-edits (step-apply-edits tick state job)
    (throw (ex-info "domain.voxel.focus: unknown edit-queue job kind"
                    {:job job}))))

;; --- Target tracking ---------------------------------------------------------------

(defn- maybe-enqueue-retarget
  "Enqueue a `:retarget` job when the focus target `target` has moved off
   the live band's spec. The RETARGET REPLACEMENT RULE: a superseding
   retarget replaces any in-flight one (`domain.voxel.queue/enqueue`) — the
   fresh job re-reads the live band, so the stale one could only re-resolve
   volume the focus already left. An in-flight retarget for the SAME target
   is left alone: it is already doing exactly the right work (mid-promote
   bands carry the target spec, so a steady focus satisfies
   `(= (:spec band) target)` and never reaches here)."
  [queue band target]
  (let [inflight (first (filter #(= :retarget (:kind %)) queue))]
    (if (and (not= (:spec band) target)
             (not (and inflight (= (:spec inflight) target))))
      (queue/enqueue queue #{:retarget} {:kind :retarget :spec target})
      queue)))

;; --- System ------------------------------------------------------------------------

(defn voxel-focus-system
  "The `:voxel-focus` write-set system (double-buffer fan-out). Sole writer
   of `#{c/voxel-field c/voxel-band c/voxel-edit-queue c/voxel-edit-diffs
   c/voxel-field-diffs}`.
   `:writes` is sourced from the registry so the declaration and the
   emitter cannot drift (the `:focus-zone` precedent). Emits NOTHING when
   no world is committed — the whole subsystem is gated on the crossed
   commitment horizon (owner decision: the band is only meaningful on a
   committed world)."
  []
  {:id     :voxel-focus
   :ns     'domain.voxel.focus
   :writes (reg/registry-writes :voxel-focus)
   :run
   (fn [world]
     (if-let [eid (committed-world-eid world)]
       (let [candidate (ecs/get-component world eid c/planet-candidate)
             position  (ecs/get-component world eid c/position)]
         (when-not candidate
           (throw (ex-info "domain.voxel.focus: committed world carries no c/planet-candidate — the handoff precedes commitment"
                           {:eid eid})))
         (when-not position
           (throw (ex-info "domain.voxel.focus: committed world carries no c/position"
                           {:eid eid})))
          (let [obs     (player/get-observer world)
                field0  (ecs/get-component world eid c/voxel-field)
                seed    (or field0 (interior/seed-field candidate))
                band0   (ecs/get-component world eid c/voxel-band)
                queue0  (or (ecs/get-component world eid c/voxel-edit-queue) [])
                diffs0  (or (ecs/get-component world eid c/voxel-edit-diffs) [])
                field-diffs0 (or (ecs/get-component world eid c/voxel-field-diffs) [])
                ;; Voxel 4: paid sculpt ops arrive one Jacobi tick stale on
                ;; the producer-suffixed request channel and fold into the
                ;; three columns this system owns — the field (biased), the
                ;; queue (derived local edits enqueued under the band), and
                ;; the field-diff stream (one record per folded op, stamped
                ;; with the fold tick: the op IS the diff, §7.3 extended).
                 ops     (ecs/get-component world eid c/voxel-sculpt-request)
                 {:keys [field jobs field-diffs]} (sculpt/fold-ops seed band0 ops)
                 field-diffs' (into field-diffs0
                                    (comp (map #(assoc % :tick (long (or (:tick world) 0))))
                                          (map validate-field-diff!))
                                    field-diffs)
                 ;; Voxel 5: collision carve plans arrive one Jacobi tick
                 ;; stale on the producer-suffixed request channel and fold
                 ;; into `:apply-edits` jobs, provenance `:collision`; melt/
                 ;; vapor voxels in the band re-cool every tick through the
                 ;; same channel (design §6 step 4). ORDER IS LOAD-BEARING:
                 ;; cooling jobs enqueue BEFORE carve jobs so the carve wins
                 ;; per voxel — a pre-existing :melt voxel inside a fresh
                 ;; bowl must end carved (nil), not resurrected to cooled
                 ;; :solid by a later-draining cooling edit (the queue is
                 ;; strict FIFO, later job wins per voxel).
                 carve-req    (ecs/get-component world eid c/voxel-carve-request)
                 carve-jobs   (carve/fold-plans field band0 (:plans carve-req))
                 cooling-jobs (carve/cooling-jobs field band0
                                                  (double (or (:sim/dt world) 0.0)))
                 target  (when (and obs (:focus-position obs))
                           (band/band-target obs field
                                             (sp/v- (:focus-position obs) position)))
                 queue1  (maybe-enqueue-retarget queue0 band0 target)
                 queue2  (into queue1 (concat jobs cooling-jobs carve-jobs))
                state'  (queue/drain {:band band0 :diffs diffs0 :queue queue2}
                                     voxel/edit-budget-ms-per-tick
                                     (fn [s job] (apply-job field (:tick world) s job)))
                band'   (:band state')
                queue'  (:queue state')
                diffs'  (:diffs state')]
            (cond-> {}
              (or (nil? field0)
                  (not (identical? seed field)))
              (assoc c/voxel-field {eid field})

              (not (identical? band0 band'))
              (assoc c/voxel-band {eid (if (nil? band') tick/removed band')})

              (or (not (identical? queue0 queue2))
                  (not (identical? queue2 queue')))
              (assoc c/voxel-edit-queue {eid queue'})

              (not (identical? diffs0 diffs'))
              (assoc c/voxel-edit-diffs {eid diffs'})

              (not (identical? field-diffs0 field-diffs'))
              (assoc c/voxel-field-diffs {eid field-diffs'}))))
        {}))})
