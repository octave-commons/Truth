(ns domain.profile
  "Benchmark-only profiling helpers for Phase 0 physics systems.

   These functions wrap hot sections with System/nanoTime accumulators stored
   on the transient world key :genesis/_profile. They are pure (do not mutate the
   simulation state) and are no-ops when :genesis/profile-subsystems? is false or
   absent.")

(defn profile-key
  "Return a profile key that distinguishes phases of a system."
  [system phase]
  (keyword (str (name system) "/" (name phase))))

(defn with-profile
  "Assoc `profile` onto `world` under :genesis/_profile, merging with any
   existing profile. Returns `world` unchanged if profiling is disabled."
  [world profile]
  (if (and profile (:genesis/profile-subsystems? world))
    (assoc world :genesis/_profile
           (merge-with + (or (:genesis/_profile world) {}) profile))
    world))

(defn timing
  "Run `(f)`, returning `[result elapsed-nanos]`."
  [f]
  (let [t0 (System/nanoTime)
        result (f)
        t1 (System/nanoTime)]
    [result (- t1 t0)]))

(defn profile-section
  "Run `(f world)`, accumulating elapsed nanos under `key` in :genesis/_profile.
   Returns the result of `f` unchanged. When `f` returns a map, the elapsed
   time is merged into that map's `:genesis/_profile` so the benchmark harness
   can report subsystem breakdowns."
  [world key f]
  (if (:genesis/profile-subsystems? world)
    (let [[result dt] (timing #(f world))]
      (if (map? result)
        (assoc result :genesis/_profile
               (merge-with + (or (:genesis/_profile result) {}) {key (double dt)}))
        result))
    (f world)))

(defn profile-sections
  "Run a sequence of `[key f]` pairs, accumulating each elapsed time under its
   key. Returns the result of the last `f`. When profiling is enabled the final
   result must be associative; if it is not, the accumulated timings are
   discarded rather than throwing."
  [world sections]
  (if (:genesis/profile-subsystems? world)
    (let [[w' prof] (reduce (fn [[w prof] [key f]]
                              (let [[w' dt] (timing #(f w))]
                                [w' (merge-with + prof {key (double dt)})]))
                            [world {}]
                            sections)]
      (if (map? w')
        (assoc w' :genesis/_profile
               (merge-with + (or (:genesis/_profile w') {}) prof))
        w'))
    (reduce (fn [w [_ f]] (f w)) world sections)))
