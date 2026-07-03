(ns domain.ecs.tick
  "The double-buffer tick — the Jacobi update for the ECS substrate.

   Spec: docs/notes/2026.06.26-ecs-double-buffer-single-writer-spec.md.

   One tick reads a frozen snapshot of world N and produces world N+1. Every
   system runs concurrently on its own thread, reading ONLY the frozen snapshot
   and returning a WRITE-SET — the changes it makes to the component types it
   exclusively owns. The write-sets are folded into the next world at a single
   end-of-tick barrier. Because no system observes another's same-tick writes,
   system order is irrelevant and the fan-out is lock-free.

   A write-set is `{component-type {entity-id value-or-`removed`}}` — the same
   shape as the world's `:components` store, so folding is a plain merge. The
   `removed` sentinel means \"this entity no longer carries my component\" (a
   single owner is responsible for clearing its own stale entries).

   Systems are maps: `{:id kw :writes #{ctype ...} :run (fn [frozen] write-set)}`.
   The `:writes` set is the runtime echo of the single-writer invariant
   (domain.ecs.registry): two systems writing the same component type is a
   conflict, caught here at the barrier.

   This namespace is write-set-native. `legacy-system` adapts an existing
   `(fn [world] world')` system into this contract by diffing its output against
   the frozen snapshot and masking to its owned component types — the migration
   bridge until each system is rewritten to emit a write-set directly."
  (:require
   [domain.ecs.core :as ecs]))

;; Sentinel: an owner declares this entity no longer has the component.
;; A distinct object so it can never collide with a legitimate component value.
(def removed (Object.))

(defn removed? [v] (identical? v removed))

;; ---------------------------------------------------------------------------
;; Folding write-sets
;; ---------------------------------------------------------------------------

(defn apply-write-set
  "Fold one write-set `{ctype {eid value-or-removed}}` onto `world`. Pure.
   A top-level `:genesis/_profile` entry, if present, is merged into the
   world's profile map rather than treated as a component."
  [world ws]
  (let [world (if-let [prof (:genesis/_profile ws)]
                (update world :genesis/_profile (fnil merge-with + {}) prof)
                world)]
    (reduce-kv
     (fn [w ctype eid->v]
       (reduce-kv
        (fn [w eid v]
          (if (removed? v)
            (ecs/remove-component w eid ctype)
            (ecs/put-component w eid ctype v)))
        w eid->v))
     world
     (dissoc ws :genesis/_profile))))

(defn colliding-ctypes
  "Given `[[system-id write-set] ...]`, return `{ctype [system-id ...]}` for any
   component type written by more than one system. The transient
   `:genesis/_profile` key is excluded from conflict detection."
  [labeled-wsets]
  (->> labeled-wsets
       (reduce (fn [m [id ws]]
                 (reduce (fn [m ctype] (update m ctype (fnil conj []) id))
                         m
                         (disj (set (keys ws)) :genesis/_profile)))
               {})
       (into (sorted-map)
             (filter (fn [[_ ids]] (> (count ids) 1))))))

(defn fold
  "Fold `[[system-id write-set] ...]` into `world` and return the next world.

   `:on-conflict` (default `:throw`) enforces single-writer at runtime: if two
   systems wrote the same component type, throw. `:last-wins` instead lets later
   systems in the seq overwrite earlier ones — a transitional policy for running
   the still-contended legacy pipeline before migration completes (spec §9)."
  [world labeled-wsets & {:keys [on-conflict] :or {on-conflict :throw}}]
  (when (= on-conflict :throw)
    (let [conflicts (colliding-ctypes labeled-wsets)]
      (when (seq conflicts)
        (throw (ex-info "write-set conflict — single-writer violated at runtime"
                        {:conflicts conflicts})))))
  (reduce (fn [w [_id ws]] (apply-write-set w ws)) world labeled-wsets))

;; ---------------------------------------------------------------------------
;; Parallel fan-out
;; ---------------------------------------------------------------------------

(defn run-parallel
  "Run `systems` concurrently on the frozen `world`; fold their write-sets into
   the next world and return it.

   Each system's `:run` receives the SAME immutable `world` and must return a
   write-set touching only its `:writes`. One `future` per system; the only
   synchronization is the deref barrier. Does NOT advance `:tick` or apply
   discrete events — the caller owns the barrier phase (events, recenter, swap)."
  [world systems & {:keys [on-conflict] :or {on-conflict :throw}}]
  (let [futs  (mapv (fn [{:keys [id run]}] [id (future (run world))]) systems)
        wsets (mapv (fn [[id f]] [id (deref f)]) futs)]
    (fold world wsets :on-conflict on-conflict)))

(defn run-sequential
  "Reference implementation: fold write-sets in order on a single thread. Same
   result as `run-parallel` when write-sets are disjoint — used to prove
   order-independence and as a fallback when threading is undesirable."
  [world systems & {:keys [on-conflict] :or {on-conflict :throw}}]
  (let [wsets (mapv (fn [{:keys [id run]}] [id (run world)]) systems)]
    (fold world wsets :on-conflict on-conflict)))

;; ---------------------------------------------------------------------------
;; Legacy bridge — run existing (fn [world] world') systems in the fan-out
;; ---------------------------------------------------------------------------

(defn diff-write-set
  "Write-set of the changes from `before` to `after`, restricted to `owned`
   component types. Added/changed cells → their new value; cells present in
   `before` but gone in `after` → `removed`."
  [before after owned]
  (reduce
   (fn [ws ctype]
     (let [b (get-in before [:components ctype] {})
           a (get-in after  [:components ctype] {})]
       (if (identical? a b)
         ws
         (let [changed (reduce-kv (fn [m eid v]
                                    (if (= v (get b eid)) m (assoc m eid v)))
                                  {} a)
               gone    (reduce-kv (fn [m eid _]
                                    (if (contains? a eid) m (assoc m eid removed)))
                                  changed b)]
           (cond-> ws (seq gone) (assoc ctype gone))))))
   {}
   owned))

(defn contribution-write-set
  "Build a single-component write-set for an accumulator contribution.

   `cell` is the freshly-computed `{eid value}` for this tick. Any entity in
   `prior` (the entity-ids that carried `ctype` in the frozen snapshot) that is
   absent from `cell` gets the `removed` sentinel, so a body that stopped
   contributing this tick has its stale contribution cleared rather than left to
   linger. Returns `{ctype cell'}`, or `{}` if there is nothing to write."
  [ctype cell prior]
  (let [cell' (reduce (fn [m eid] (if (contains? cell eid) m (assoc m eid removed)))
                      cell
                      prior)]
    (if (seq cell') {ctype cell'} {})))

(defn legacy-system
  "Adapt a legacy `(fn [world] world')` system into a write-set system that
   reads the frozen snapshot and emits only changes to `owned` component types.
   Any change the legacy fn made OUTSIDE `owned` is silently dropped — ownership
   is enforced at the boundary, so a mis-declared system can't corrupt another's
   columns through the fan-out.

   When the input world has `:genesis/profile-subsystems?` true, any
   `:genesis/_profile` accumulated by the legacy fn is carried onto the returned
   write-set so the benchmark harness can report subsystem timings."
  [id owned sysfn]
  {:id     id
   :writes owned
   :run    (fn [world]
             (let [after (sysfn world)
                   ws    (diff-write-set world after owned)]
               (if (:genesis/profile-subsystems? world)
                 (assoc ws :genesis/_profile (or (:genesis/_profile after) {}))
                 ws)))})
