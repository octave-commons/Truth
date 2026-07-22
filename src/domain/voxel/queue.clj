(ns domain.voxel.queue
  "The deferred voxel-edit queue (Voxel 3,
   kanban/tasks/voxel-focus-promotion-demotion.md; design
   docs/designs/planetary-voxel-substrate.md §7.1 RESOLVED 2026-07-22:
   deferred edit queue with a hard 2 ms/tick cap).

   ONE drain path for every voxel edit: band promotion/demotion chunks and
   the sculpt/mine/construct/collision edits of slices 4-6 all enqueue into
   the same ordered vector (`c/voxel-edit-queue` on the committed world
   entity) and drain through `drain` under
   `law.voxel/edit-budget-ms-per-tick`.

   THE BUDGET IS ESTIMATED WORK, NEVER WALL-CLOCK TIME: every job's cost is
   a pure function of its payload (`job-cost-ms`), so the drain is
   deterministic and tests drive it with fake costs — no clocks anywhere.

   ORDERING IS LOAD-BEARING: design §7.3's replay rule is 'diffs persist as
   an ordered vector, replayed in drain order', so the queue is strict FIFO
   with head-of-line blocking — a job that does not fit the remaining
   budget stays at the head and everything behind it waits. A job whose
   estimated cost exceeds the WHOLE budget drains alone (the only way it
   can ever run); band promotion/demotion never produces such jobs because
   the system chunks them to `law.voxel/edit-chunk-voxels`.

   Jobs are maps `{:kind ... :cost-ms? ...}`. Multi-tick jobs (the band
   retarget) return a REMAINDER from their apply fn, which is re-queued at
   the head and continues draining while budget remains. This namespace is
   generic: it knows costs and ordering, not voxel semantics — the apply
   fn is supplied by the caller (`domain.voxel.focus`)."
  (:require
   [law.voxel :as voxel]))

(defn job-cost-ms
  "Estimated drain cost (ms) of `job`: its explicit `:cost-ms` when present
   (the test/fake-cost channel), else the per-voxel model —
   `law.voxel/edit-cost-base-ms` + `edit-cost-per-voxel-ms` × the job's
   voxel count (`:voxel-count` key, or the size of `:edits`/`:pending`,
   defaulting to one chunk for a band retarget)."
  [job]
  (double
   (or (:cost-ms job)
       (+ voxel/edit-cost-base-ms
          (* voxel/edit-cost-per-voxel-ms
             (double (or (:voxel-count job)
                         (some #(when-let [xs (% job)] (count xs))
                               [:edits :pending])
                         voxel/edit-chunk-voxels)))))))

(defn drain
  "Drain `state`'s `:queue` (an ordered job vector) against `budget-ms`,
   threading `state` through `apply-job` —

     apply-job :: state → job → [state' remainder-or-nil]

   where a non-nil remainder is re-queued at the HEAD and keeps draining
   while budget remains (multi-tick jobs make one chunk of progress per
   step; they must always make progress — a zero-cost job that always
   returns a remainder would spin forever, which is a caller bug, not a
   queue case). Returns `state'` with the remaining `:queue` and
   `:spent-ms` — the total estimated cost drained this call, guaranteed
   `<= budget-ms` unless a single oversized head drained alone (the
   documented overshoot rule). Pure; no clocks."
  [state budget-ms apply-job]
  (loop [s state spent 0.0]
    (if-let [job (first (:queue s))]
      (let [cost (job-cost-ms job)]
        (if (or (<= (+ spent cost) budget-ms)
                (zero? spent))
          (let [[s' remainder] (apply-job (update s :queue subvec 1) job)
                s'' (if remainder
                      (update s' :queue #(into [remainder] %))
                      s')]
            (recur s'' (+ spent cost)))
          (assoc s :spent-ms spent)))
      (assoc s :spent-ms spent))))

(defn enqueue
  "Append `job` to `queue`, replacing any queued job of a kind in
   `replace-kinds` (the retarget self-replacement rule: a newer focus
   target supersedes an in-flight retarget, and the fresh job re-reads the
   live band state, so the stale one can only do harm — draining it would
   re-resolve a band the focus has already left)."
  [queue replace-kinds job]
  (conj (into [] (remove #(contains? replace-kinds (:kind %))) queue) job))

(defn edits->jobs
  "Split `edits` (an ordered collection of `law.voxel/voxel-edit-schema`
   maps) into an ordered vector of `:apply-edits` jobs of at most
   `law.voxel/max-edits-per-job` edits each — the producer-side chunking
   tool for slices 4-6. Jobs are ATOMIC in the drain, so enqueueing a whole
   crater as one job forces the oversized-head escape and blows the 2 ms
   cap; enqueuing the chunks this returns keeps every tick inside budget.
   ORDER IS PRESERVED ACROSS CHUNKS: `partition-all` cuts in collection
   order and the queue is strict FIFO, so draining the returned vector
   applies edits in exactly the input order (load-bearing for §7.3 replay:
   drain order is replay order). `opts` carries the job's `:provenance`
   (required — validated against `law.voxel/edit-provenance-schema`) and
   `:region` (the out-of-band diff target; required by the system when any
   edit lands outside the live band). Every edit is schema-validated here —
   fail at enqueue, not mid-drain."
  [edits {:keys [provenance region] :as _opts}]
  (when-not (voxel/edit-provenance? provenance)
    (throw (ex-info "domain.voxel.queue/edits->jobs: provenance fails law.voxel/edit-provenance-schema"
                    {:provenance provenance})))
  (doseq [edit edits]
    (when-not (voxel/voxel-edit? edit)
      (throw (ex-info "domain.voxel.queue/edits->jobs: edit fails law.voxel/voxel-edit-schema"
                      {:edit edit}))))
  (mapv (fn [chunk]
          (cond-> {:kind       :apply-edits
                   :edits      (vec chunk)
                   :provenance provenance}
            (some? region) (assoc :region region)))
        (partition-all voxel/max-edits-per-job edits)))
