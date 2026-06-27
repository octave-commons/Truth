(ns domain.ecs.registry
  "System registry + the single-writer invariant.

   This is the data backbone of the double-buffer ECS (see
   `docs/notes/2026.06.26-ecs-double-buffer-single-writer-spec.md`). Each system
   declares the component types it READS and the component types it WRITES. The
   load-bearing rule is Rule 2 of the spec: *every component type is written by
   exactly one system*. Once that holds, per-tick write-sets are provably
   disjoint, so the end-of-tick fold is conflict-free and each system can run on
   its own thread with no locks.

   IMPORTANT: the `systems` vector below declares the CURRENT (Gauss–Seidel)
   reality, not the target ownership. The current pipeline violates single-writer
   in 10 places (position, density, pressure, radius, matter-state,
   accretion-radius, hydro-accel, temperature, spin, b-field). So
   `write-conflicts` is intentionally non-empty today: it is the migration
   to-do list. Each migration step (spec §9) deletes a second writer here until
   `(write-conflicts systems)` is `{}`, at which point `assert-single-writer!`
   is wired into startup.

   No tick logic lives here — only the declaration and its validation."
  (:require
    [clojure.string :as str]
    [domain.ecs.components :as c]))

;; ---------------------------------------------------------------------------
;; The registry — current reality of the 12-system Gauss–Seidel pipeline.
;;
;; :reads / :writes are sets of component-type keywords (domain.ecs.components).
;; A component appearing in :writes means the system assoc's OR dissoc's that
;; component column — removal is a write (single owner clears its own staleness).
;; Discrete-event handlers (e.g. stellar merge) are NOT systems and are excluded;
;; collision-detection itself writes no component state, it only emits events.
;; ---------------------------------------------------------------------------

(def systems
  "Declared systems with their reads/writes. Order is irrelevant to the
   invariant — it exists only to enumerate systems and validate disjointness."
  [;; The Structure owner: shape + compactness. radius and density are one
   ;; geometric fact, so one system owns the pair (branching on matter-state:
   ;; gas SPH / solid material density / KH oblate contraction). Subsumes the
   ;; radius+density writes of the old density-system, jeans-collapse, collapse.
   {:id     :structure
    :ns     'domain.stellar
    :reads  #{c/matter-state c/mass c/radius c/density c/position c/temperature
              c/pressure c/oblateness c/angular-momentum}
    :writes #{c/radius c/density c/oblateness c/rotation-axis}}

   ;; Pressure is a pure equation of state P = ρ k_B T / m_H — every former
   ;; writer recomputed the identical ideal-gas pressure, so one EOS system owns
   ;; it and derives it from density + temperature (spec §4 derivations).
   {:id     :eos
    :ns     'domain.stellar
    :reads  #{c/density c/temperature}
    :writes #{c/pressure}}

   {:id     :hydro
    :ns     'domain.hydro
    :reads  #{c/matter-state c/position c/density c/pressure c/mass c/radius}
    :writes #{c/accel-pressure}}

   ;; Jeans-collapse now contributes only the feeding/accretion radius; shape
   ;; (radius/density) is the Structure owner's, matter-state the classifier's.
   {:id     :jeans-collapse
    :ns     'domain.stellar
    :reads  #{c/matter-state c/position c/density c/radius c/temperature c/mass}
    :writes #{c/accretion-radius}}

   ;; The classifier is the SOLE writer of matter-state: the authentic formation
   ;; state machine (Jeans+mass+ignition). Subsumes the old classify system and
   ;; the matter-state writes of jeans-collapse and fusion.
   {:id     :classifier
    :ns     'domain.stellar
    :reads  #{c/matter-state c/mass c/radius c/density c/temperature
              c/pressure c/composition}
    :writes #{c/matter-state}}

   ;; Gravity is split out of the old orbital system: the Barnes–Hut tree-walk
   ;; emits the accel.gravity contribution on its own thread, and the thin motion
   ;; integrator sums all accel.* contributions and advances position/velocity.
   {:id     :gravity
    :ns     'domain.orbital.system
    :reads  #{c/position c/velocity c/mass c/radius c/body-kind}
    :writes #{c/accel-gravity}}

   {:id     :motion
    :ns     'domain.orbital.system
    :reads  #{c/position c/velocity c/mass c/radius c/body-kind
              c/accel-gravity c/accel-pressure c/accel-lorentz}
    :writes #{c/position c/velocity}}

   ;; Barrier systems: run SERIALLY after the fold, so they are exempt from the
   ;; single-writer invariant (like event handlers — spec §6). collision emits
   ;; discrete events whose merge handler despawns; recenter is a global COM
   ;; reduction over the whole folded world.
   {:id            :collision-detection
    :ns            'domain.physics.collision
    :stage         :barrier
    :reads         #{c/position c/radius c/matter-state c/accretion-radius}
    :writes        #{}
    :emits-events? true}

   ;; :collapse is fully retired: its writes were dissolved into Structure (shape),
   ;; :thermal (virial temperature), :em (spin), and :field (b-field).

   {:id     :fusion
    :ns     'domain.stellar
    :reads  #{c/matter-state c/temperature c/pressure c/composition}
    :writes #{c/luminosity}}

   {:id     :thermal
    :ns     'domain.stellar
    :reads  #{c/matter-state c/temperature c/density c/radius c/mass c/position c/luminosity}
    :writes #{c/temperature}}

   {:id     :regime
    :ns     'domain.regime
    :reads  #{c/matter-state c/density c/temperature c/b-field}
    :writes #{c/regime}}

   ;; EM is split: the Lorentz force is its own contribution emitter; braking
   ;; (angular-momentum/spin) and resistive flux decay (b-field) stay on the
   ;; legacy em system until the rotation/field integrators are introduced.
   {:id     :em-lorentz
    :ns     'domain.em
    :reads  #{c/b-field c/radius c/position c/density c/matter-state}
    :writes #{c/accel-lorentz}}

   ;; :em owns spin as the derived ω = L/I (it already computes it after applying
   ;; magnetic-braking torque). Transitional home for the rotation owner.
   {:id     :em
    :ns     'domain.em
    :reads  #{c/b-field c/radius c/position c/density c/angular-momentum c/mass}
    :writes #{c/angular-momentum c/spin}}

   ;; The Field owner: b-field via conserved frozen flux Φ = B·R² (B = Φ/R²
   ;; amplifies as the radius contracts) plus Ohmic decay. Subsumes collapse's
   ;; flux-freezing and em's b-field decay.
   {:id     :field
    :ns     'domain.em
    :reads  #{c/b-field c/radius c/matter-state c/frozen-flux}
    :writes #{c/b-field c/frozen-flux}}

   {:id     :recenter
    :ns     'domain.phase0
    :stage  :barrier
    :reads  #{c/position c/mass}
    :writes #{c/position}}])

(defn fan-out-systems
  "Systems that run in the parallel fan-out (everything not marked :barrier).
   The single-writer invariant applies only to these — barrier systems run
   serially after the fold and may write freely (spec §6)."
  [systems]
  (filterv #(not= :barrier (:stage %)) systems))

;; ---------------------------------------------------------------------------
;; Validation
;; ---------------------------------------------------------------------------

(defn writers-by-component
  "Return {component-type [system-id ...]} across the registry — every system
   that writes each component, in registry order."
  [systems]
  (reduce (fn [m {:keys [id writes]}]
            (reduce (fn [m ct] (update m ct (fnil conj []) id))
                    m
                    writes))
          {}
          systems))

(defn write-conflicts
  "Return {component-type [system-id ...]} for every component written by MORE
   THAN ONE FAN-OUT system — i.e. the single-writer violations. Barrier systems
   are exempt (they run serially after the fold). `{}` means the invariant holds.
   This is the migration to-do list while non-empty."
  [systems]
  (into (sorted-map)
        (filter (fn [[_ ids]] (> (count ids) 1)))
        (writers-by-component (fan-out-systems systems))))

(defn malformed-entries
  "Return [{:id .. :problems [..]}] for registry entries that are structurally
   invalid: missing/duplicate :id, missing :reads/:writes, or non-`:component/*`
   keywords in either set."
  [systems]
  (let [ids   (map :id systems)
        dupes (set (for [[id n] (frequencies ids) :when (> n 1)] id))
        component-kw? (fn [k] (and (keyword? k) (= "component" (namespace k))))]
    (->> systems
         (keep (fn [{:keys [id reads writes] :as entry}]
                 (let [problems
                       (cond-> []
                         (nil? id)            (conj "missing :id")
                         (contains? dupes id) (conj (str "duplicate :id " id))
                         (not (set? reads))   (conj "missing/!set :reads")
                         (not (set? writes))  (conj "missing/!set :writes")
                         (and (set? reads)  (not-every? component-kw? reads))
                         (conj (str "non-component reads: "
                                    (remove component-kw? reads)))
                         (and (set? writes) (not-every? component-kw? writes))
                         (conj (str "non-component writes: "
                                    (remove component-kw? writes))))]
                   (when (seq problems)
                     {:id (or id (:ns entry)) :problems problems})))))))

(defn format-conflicts
  "Human-readable single-writer violation report — the to-do list."
  [conflicts]
  (if (empty? conflicts)
    "single-writer invariant holds: every component has exactly one writer."
    (str "single-writer INVARIANT VIOLATED — "
         (count conflicts) " component(s) have multiple writers:\n"
         (str/join "\n"
           (for [[ct ids] conflicts]
             (format "  %-28s written by %d systems: %s"
                     ct (count ids) (str/join ", " ids)))))))

(defn assert-single-writer!
  "Throw if the registry violates single-writer. NOT yet wired into startup —
   the current pipeline fails this by design. Call it once migration (spec §9)
   has reduced `write-conflicts` to `{}`; from then on it guards every boot."
  ([] (assert-single-writer! systems))
  ([systems]
   (let [bad (malformed-entries systems)]
     (when (seq bad)
       (throw (ex-info "Malformed system registry entries" {:malformed bad}))))
   (let [conflicts (write-conflicts systems)]
     (when (seq conflicts)
       (throw (ex-info (format-conflicts conflicts) {:conflicts conflicts})))
     systems)))
