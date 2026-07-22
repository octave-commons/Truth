(ns domain.genesis.promotion
  "The `:focus-zone` fan-out emitter: promotes statistical regional cells
   (`domain.field/spawn-regional-cell`) into resolved clumps when the
   observer's immediate focus overlaps them, and demotes previously-promoted
   clumps back into their source cell when focus withdraws. Promotion and
   demotion are ONE system/id because both write `c/statistical-mass` — two
   ids would trip `domain.ecs.registry/write-conflicts` (spec:
   kanban/tasks/phase-0-player-focus-b-focus-zone-system.md).

   Sole writer of `#{c/field-zone c/statistical-mass c/spawn-request-promotion
   c/consumed-demote}`. `c/field-zone` is never emitted through this system's
   per-tick write-set map directly — it is stamped on a promoted clump via the
   spawn spec's `:extra-components` (alongside `c/promoted-from-cell`),
   applied by `domain.genesis.bootstrap/spawn-entity` at world-construction,
   the same mechanism every other `spawn-request.*` type uses to set a new
   entity's initial component values (e.g. `:matter-state`/`:body-kind` in
   `domain.stellar.seeder/seed-spec`). That is why this system can drop all
   `c/matter-state`/`c/body-kind` WRITES from its own write-set: the classifier
   remains the sole per-tick writer of `c/matter-state`, and the initial state
   of a brand-new entity is not a same-tick write conflict with it (the entity
   does not exist in the frozen snapshot the classifier read).

   Demotion is scoped to bodies THIS system itself promoted (tagged
   `c/field-zone :immediate` + `c/promoted-from-cell`), not to the wider
   simulation — demoting ordinary formation bodies that simply happen to be
   far from the observer is regional/global sub-cycling, explicitly out of
   scope for this phase (parent epic). A promoted clump that leaves the
   immediate radius with no recent threshold event is folded back into its
   source cell — mass, COM velocity, and angular momentum are conserved
   exactly; the cell's mean-b and temperature absorb the clump's field and
   thermal state (including its rotational kinetic energy, via the cell's
   effective moment of inertia) as mass-weighted / energy-weighted averages
   — and reaped in the same tick's `materialize-lifecycle` pass.

   A cell promoted in full (the only mode implemented: whole-cell sampling,
   not partial) is debited to `:mass 0.0`. That intentionally also gates
   re-promotion — `promotable-cells` requires a positive mass, so a spent cell
   is not re-selected every tick until demotion credits mass back into it.

   The threshold-event-delay logic (`recent-threshold-entities`) is kept
   verbatim from the false-start predecessor of this namespace: a body
   involved in a collision/ignition/etc. event THIS tick is never demoted."
  (:require
   [domain.ecs.core :as ecs]
   [domain.ecs.components :as c]
   [domain.ecs.event :as event]
   [domain.ecs.registry :as reg]
   [domain.player :as player]
   [domain.stellar.geometry :as geometry]
   [domain.stellar.thermodynamics :as thermo]
   [law.stellar :as law]
   [shape.spatial :as sp]))

(def ^:private threshold-event-kinds
  "Event kinds that block demotion for the entity involved."
  #{:event/stellar-ignition
    :event/protostar-formation
    :event/planet-formation
    :event/condensed-core-formation
    :event/planetesimal-formation
    :event/gas-giant-formation
    :event/brown-dwarf-formation
    :event/collision
    :event/phase-transition
    :event/life-emergence
    :event/gate-discovery})

(defn- immediate-radius
  "Immediate focus radius from the observer's attention shell."
  [obs]
  (or (get-in obs [:attention-shell :immediate-r])
      (player/probability-collapse-radius obs)))

(defn- focus-position
  "Observer focus position."
  [obs]
  (or (:focus-position obs) [0.0 0.0 0.0]))

(defn- recent-threshold-entities
  "Entity ids involved in threshold events on the current tick."
  [world]
  (let [this-tick (:tick world)]
    (->> (event/events-since world this-tick)
         (filter #(= (:tick %) this-tick))
         (filter #(threshold-event-kinds (:kind %)))
         (mapcat #(seq (:entities %)))
         (set))))

;; --- Shared cell lookups -----------------------------------------------------

(defn- regional-cell?
  "True when `eid` is a regional statistical cell (carries `c/field-zone
   :regional` — the marker `domain.field/spawn-regional-cell` stamps)."
  [world eid]
  (= :regional (ecs/get-component world eid c/field-zone)))

(defn- all-cells
  "Every regional-cell entity in `world`."
  [world]
  (->> (ecs/entities-with world c/statistical-mass c/field-zone c/position)
       (filterv #(regional-cell? world %))))

(defn- nearest-cell
  "The regional cell in `cells` closest to `position`, or nil if `cells` is empty."
  [world cells position]
  (when (seq cells)
    (apply min-key #(sp/dist position (ecs/get-component world % c/position)) cells)))

;; --- Promotion ---------------------------------------------------------------

(defn- promotable-cells
  "Regional cells within `r` of `focus` carrying a positive ledger mass. A
   fully-debited cell (`:mass 0.0`) is excluded so it is not re-promoted every
   tick until demotion credits mass back into it."
  [world focus r]
  (->> (all-cells world)
       (filterv (fn [eid]
                  (and (pos? (double (:mass (ecs/get-component world eid c/statistical-mass))))
                       (<= (sp/dist focus (ecs/get-component world eid c/position)) r))))))

(defn- cell-radius
  "Radius of a uniform-density sphere carrying `mass` — the promoted clump's
   physical size (no cell-extent field exists on the statistical ledger)."
  [mass]
  (geometry/sphere-radius mass geometry/debris-material-density))

(defn- promotion-spec
  "One `c/spawn-request-promotion` seed spec sampled from the WHOLE ledger of
   `cell-eid` at `position`, conserving mass, COM velocity, and angular
   momentum. Stamps `c/promoted-from-cell` and `c/field-zone :immediate` via
   `:extra-components` so demotion can find its way home without a spatial
   lookup."
  [cell-eid ledger position]
  (let [mass (double (:mass ledger))]
    {:position         position
     :velocity         (:velocity ledger)
     :mass             mass
     :radius           (cell-radius mass)
     :temperature      (:temperature ledger)
     :composition      (:composition ledger)
     :matter-state     :planetesimal
     :body-kind        :body/rocky
     :angular-momentum (:angular-momentum ledger)
     :extra-components {c/promoted-from-cell cell-eid
                        c/field-zone         :immediate}}))

(defn- promotion-write-set
  "Write-set entries for promoting every cell in `cells`: one spawn-request
   spec per cell, plus a full-mass debit of its ledger."
  [world cells]
  (reduce (fn [ws eid]
            (let [ledger (ecs/get-component world eid c/statistical-mass)
                  pos    (ecs/get-component world eid c/position)]
              (-> ws
                  (update-in [c/spawn-request-promotion eid] (fnil conj []) (promotion-spec eid ledger pos))
                  (assoc-in [c/statistical-mass eid] (assoc ledger :mass 0.0)))))
          {} cells))

;; --- Demotion -----------------------------------------------------------------

(defn- promoted-clumps
  "Every resolved body this system has previously promoted (carries
   `c/promoted-from-cell`)."
  [world]
  (ecs/entities-with world c/matter-state c/position c/velocity c/mass
                     c/angular-momentum c/field-zone c/promoted-from-cell))

(defn- demotable?
  "True when `eid` is a promoted clump (`c/field-zone :immediate`) currently
   outside the immediate radius and not blocked by a same-tick threshold event."
  [world focus r blocked eid]
  (and (= :immediate (ecs/get-component world eid c/field-zone))
       (not (contains? blocked eid))
       (> (sp/dist focus (ecs/get-component world eid c/position)) r)))

(defn- target-cell
  "The cell to credit a demoted body into: its `c/promoted-from-cell` back-
   pointer if that cell still exists, else the nearest regional cell."
  [world cells eid]
  (let [pf (ecs/get-component world eid c/promoted-from-cell)]
    (if (and pf (regional-cell? world pf))
      pf
      (nearest-cell world cells (ecs/get-component world eid c/position)))))

(defn- body-snapshot
  "The demoted body's mass/velocity/angular-momentum/radius/temperature/b-field,
   read from the frozen snapshot."
  [world eid]
  {:mass             (ecs/get-component world eid c/mass)
   :velocity         (ecs/get-component world eid c/velocity)
   :angular-momentum (ecs/get-component world eid c/angular-momentum)
   :radius           (double (or (ecs/get-component world eid c/radius) 0.0))
   :temperature      (double (or (ecs/get-component world eid c/temperature) 0.0))
   :b-field          (ecs/get-component world eid c/b-field)})

(defn- thermal-energy
  "Ideal-gas thermal energy (3/2) N k_B T of `mass` at `temperature`, with
   N = mass / m_H (law.stellar/m-H, law.stellar/k-B — SI)."
  [mass temperature]
  (* 1.5 (/ (double mass) law/m-H) law/k-B (double temperature)))

(defn- temperature-for-energy
  "Invert `thermal-energy`: the temperature of `mass` holding `energy` J."
  [mass energy]
  (if (pos? (double mass))
    (/ (double energy) (* 1.5 (/ (double mass) law/m-H) law/k-B))
    0.0))

(defn- rotational-energy
  "Rotational kinetic energy |L|²/(2I) of a body with `angular-momentum`,
   `mass`, and `radius` (uniform-sphere moment of inertia)."
  [angular-momentum mass radius]
  (let [I (thermo/moment-of-inertia mass radius)]
    (if (pos? I)
      (/ (sp/dot angular-momentum angular-momentum) (* 2.0 I))
      0.0)))

(defn- credit-ledger
  "Fold a demoted `body` (`body-snapshot`) into cell `ledger`: mass, COM
   velocity, and angular momentum are conserved exactly; `mean-b` is a mass-
   weighted average of the two field values; `temperature` is re-derived from
   the combined ideal-gas thermal energy budget, which additionally absorbs
   the body's rotational kinetic energy (the ordered spin of a demoted clump
   becomes disordered heat in the statistical cell, since the cell no longer
   resolves rotation)."
  [ledger body]
  (let [old-m (double (:mass ledger))
        add-m (double (:mass body))
        new-m (+ old-m add-m)
        w-old (/ old-m new-m)
        w-add (/ add-m new-m)
        e-old (thermal-energy old-m (:temperature ledger))
        e-add (+ (thermal-energy add-m (:temperature body))
                 (rotational-energy (:angular-momentum body) add-m (:radius body)))]
    (-> ledger
        (assoc :mass new-m)
        (assoc :velocity (sp/v+ (sp/v* (:velocity ledger) w-old)
                                (sp/v* (:velocity body) w-add)))
        (assoc :angular-momentum (sp/v+ (:angular-momentum ledger) (:angular-momentum body)))
        (assoc :mean-b (sp/v+ (sp/v* (:mean-b ledger) w-old)
                              (sp/v* (or (:b-field body) (:mean-b ledger)) w-add)))
        (assoc :temperature (temperature-for-energy new-m (+ e-old e-add))))))

(defn- demote
  "Reduce over promoted clumps: `{:ws write-set-so-far :ledgers {cell ledger'}}`.
   `ledger-of` resolves a cell's CURRENT ledger — the promotion write-set's
   pending debit if this tick already touched it, else the frozen snapshot —
   so a cell promoted and credited in the same tick sees its own debit first."
  [world focus r blocked cells promo-ws]
  (let [ledger-of (fn [ledgers cell]
                    (or (get ledgers cell)
                        (get-in promo-ws [c/statistical-mass cell])
                        (ecs/get-component world cell c/statistical-mass)))]
    (reduce
     (fn [{:keys [ws ledgers] :as acc} eid]
       (if (demotable? world focus r blocked eid)
         (if-let [cell (target-cell world cells eid)]
           (let [ledger (ledger-of ledgers cell)
                 body   (body-snapshot world eid)]
             {:ws      (assoc-in ws [c/consumed-demote eid] true)
              :ledgers (assoc ledgers cell (credit-ledger ledger body))})
           acc)
         acc))
     {:ws {} :ledgers {}}
     (promoted-clumps world))))

;; --- System -------------------------------------------------------------------

(defn focus-zone-system
  "The `:focus-zone` write-set system (double-buffer fan-out, spec: see
   namespace docstring). Sole writer of `#{c/field-zone c/statistical-mass
   c/spawn-request-promotion c/consumed-demote}`. `:writes` is sourced from
   the registry by its caller (`domain.genesis.systems`) so the declaration
   and the emitter cannot drift."
  []
  {:id     :focus-zone
   :ns     'domain.genesis.promotion
   :writes (reg/registry-writes :focus-zone)
   :run
   (fn [world]
     (if-let [obs (player/get-observer world)]
       (let [focus     (focus-position obs)
             r         (immediate-radius obs)
             blocked   (recent-threshold-entities world)
             cells     (all-cells world)
             promo-ws  (promotion-write-set world (promotable-cells world focus r))
             {:keys [ws ledgers]} (demote world focus r blocked cells promo-ws)]
         (cond-> promo-ws
           (seq (get ws c/consumed-demote)) (assoc c/consumed-demote (get ws c/consumed-demote))
           (seq ledgers) (update c/statistical-mass (fnil merge {}) ledgers)))
       {}))})
