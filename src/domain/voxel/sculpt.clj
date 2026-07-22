(ns domain.voxel.sculpt
  "God-scale sculpting ops (Voxel 4, kanban/tasks/voxel-god-scale-sculpting-
   ops.md; design docs/designs/planetary-voxel-substrate.md §5 tier 1 — the
   macro-drives-local rule): the Phase 1 ability palette biases the MACRO
   geology field, and local voxel edits fall out of the field change ONLY
   where the focus band is resolved. Never a direct god-finger voxel poke.

   THREE VERBS, THREE HONEST FIELD CARRIERS (the field has no surface
   height field — each op steers what the field honestly carries):
   - :uplift    (Tectonics)   — the plate nearest the anchor gains a
     tangential velocity push TOWARD the anchor (convergence), and the
     nearest DOWNWELLING convection cell speeds up (subduction pairing).
     Local fall-out: bulk density moves deep→shallow within each affected
     column (total mass conserved per column; per-material masses are NOT
     transported — composition drift is a known coarse-model trade-off).
   - :erosion   (Hydrography) — the resource cell nearest the anchor exports
     a magnitude-scaled fraction of its `:total-mass` to the nearest other
     cell (the resource field's `:total-mass` conservation discipline:
     transported, never created). Local fall-out: bulk density moves off
     donor column tops onto the rim columns' tops (sediment; same
     composition-drift trade-off as uplift).
   - :volcanism (Tectonics)   — the nearest UPWELLING convection cell speeds
     up (melt delivery). Local fall-out: in-disc voxels gain paid heat;
     voxels crossing `law.voxel/sculpt-melt-temperature-k` turn `:melt`
     (cohesion 0). Mass untouched — heat is the paid effect.

   MASS HONESTY: every local edit REDISTRIBUTES band mass (per-column for
   uplift, column-to-rim for erosion) or leaves it exactly invariant
   (volcanism). Nothing here creates or destroys mass.

   ACTUATION (the `domain.intervention/place` precedent): `request-op` is a
   pure, serial, pre-tick world→world' called from infra (or tests) — it
   gates on the observer's `c/palette` (the verb's ability must be armed),
   spends Resonance through `player/spend-resonance` (mirrors the agency
   spend pattern; Resonance accrual existed, this is the first spend), and
   appends the paid op to the `:voxel/sculpt-ops` world key. The
   `:voxel-sculpt` fan-out system translates that key into the
   `c/voxel-sculpt-request` component — the producer-suffixed request
   channel `domain.voxel.focus`'s docstring declares — and
   `clear-sculpt-ops` drops the key serially post-fold (the
   `intervention/expire-interventions` precedent), so each op folds
   exactly once (one Jacobi tick later, in `:voxel-focus`).

   KNOWN GAP (honest, same posture as Narrowing A): the palette VERBS are
   still infra-side — no keymap dispatches `request-op` yet (the Phase 0
   keymap is `infra.render.input/action-palette`). The domain-side
   actuation path here is the contract that infra card wires to. A second
   gap: field biases live on the cached `c/voxel-field` and are NOT part of
   the §7.3 field-seed + edit-diff save story (diffs record voxel
   deviations, not field bias) — persisting field bias across loads is a
   later card."
  (:require
   [domain.ecs.components :as c]
   [domain.ecs.core :as ecs]
   [domain.ecs.registry :as reg]
   [domain.ecs.tick :as tick]
   [domain.player :as player]
   [domain.voxel.band :as band]
   [domain.voxel.queue :as queue]
   [law.interior :as law-int]
   [law.voxel :as voxel]
   [shape.spatial :as sp]))

(def ^:private e
  "Canonical voxel edge (m) — local alias."
  voxel/canonical-voxel-edge-m)

(def ^:private e3
  "Canonical voxel volume (m³)."
  (* e e e))

;; --- Small vec helpers --------------------------------------------------------

(defn- normalize
  "Unit vector of `v`; `fallback` when `v` is near-zero."
  [v fallback]
  (let [l (sp/len v)]
    (if (> l 1.0e-12)
      (mapv (fn [x] (double (/ x l))) v)
      fallback)))

;; --- Cost law (pure) -----------------------------------------------------------

(defn op-cost
  "Resonance cost of sculpt `verb` at `magnitude` ∈ (0,1]:
   base + per-magnitude × magnitude (`law.voxel/sculpt-op-cost-coefficients`)
   — strictly monotone in magnitude by construction. Throws on an unknown
   verb: a misspelled verb is a contract violation, not a free op."
  [verb magnitude]
  (let [coeffs (get voxel/sculpt-op-cost-coefficients verb)]
    (when-not coeffs
      (throw (ex-info "domain.voxel.sculpt/op-cost: unknown sculpt verb"
                      {:verb verb})))
    (+ (double (:base coeffs))
       (* (double (:per-magnitude coeffs)) (double magnitude)))))

;; --- Field lookup helpers ------------------------------------------------------

(defn- plate-center
  "Centroid of a plate's boundary polygon (body-centric m, ~surface)."
  [plate]
  (let [vs (:boundary plate)
        n  (double (count vs))]
    (mapv (fn [axis] (double (/ (reduce + 0.0 (map #(nth % axis) vs)) n)))
          [0 1 2])))

(defn- nearest-by
  "The record in `xs` whose `(key-fn record)` unit direction is nearest the
   unit `anchor` (angular proximity), or nil on empty."
  [key-fn anchor xs]
  (->> xs
       (map (fn [x] [x (sp/dist (normalize (key-fn x) [1.0 0.0 0.0]) anchor)]))
       (sort-by second)
       first
       first))

;; --- Field effects (pure: field + op -> {:field :delta}) -----------------------

(defn apply-uplift
  "Uplift's field bias: the plate nearest the anchor gains a tangential
   velocity push TOWARD the anchor of magnitude ×
   `law.voxel/sculpt-plate-velocity-lever` ×
   `law.interior/plate-speed-reference-m-per-s` (convergence — the
   statistical steer toward crustal thickening), and the nearest
   DOWNWELLING convection cell's speed multiplies by (1 + lever ×
   magnitude). Velocities and flow speeds are not mass: the field's mass
   budget is untouched. Returns {:field :delta}; validates the touched
   records (fail loudly — the `domain.interior/validate-field!`
   precedent)."
  [field op]
  (let [anchor  (:anchor op)
        mag     (double (:magnitude op))
        plate   (nearest-by plate-center anchor (:plates field))
        _       (when-not plate
                  (throw (ex-info "domain.voxel.sculpt/apply-uplift: field carries no plates"
                                  {:op op})))
        cell    (nearest-by :center anchor
                            (filter #(= :downwelling (:flow %)) (:convection field)))
        _       (when-not cell
                  (throw (ex-info "domain.voxel.sculpt/apply-uplift: field carries no downwelling cell"
                                  {:op op})))
        pc      (plate-center plate)
        push    (normalize (sp/v- (sp/v* anchor (sp/len pc)) pc) [1.0 0.0 0.0])
        dv      (sp/v* push (* mag voxel/sculpt-plate-velocity-lever
                               law-int/plate-speed-reference-m-per-s))
        factor  (+ 1.0 (* voxel/sculpt-convection-speed-lever mag))
        plate'  (update plate :velocity
                        (fn [v] (mapv double (sp/v+ v dv))))
        cell'   (update cell :speed #(* factor (double %)))]
    (when-not (voxel/plate? plate')
      (throw (ex-info "domain.voxel.sculpt/apply-uplift: biased plate fails law.voxel/plate-schema"
                      {:plate plate'})))
    (when-not (voxel/mantle-convection-cell? cell')
      (throw (ex-info "domain.voxel.sculpt/apply-uplift: biased cell fails law.voxel/mantle-convection-cell-schema"
                      {:cell cell'})))
    {:field (-> field
                (update :plates (fn [ps] (mapv #(if (= (:id %) (:id plate)) plate' %) ps)))
                (update :convection (fn [cs] (mapv #(if (= (:id %) (:id cell)) cell' %) cs))))
     :delta {:verb         :uplift
             :plate        (:id plate)
             :velocity-bias (mapv double dv)
             :cell         (:id cell)
             :speed-factor (double factor)}}))

(defn apply-erosion
  "Erosion's field bias: the resource cell nearest the anchor exports
   magnitude × `law.voxel/sculpt-erosion-cell-mass-fraction` of its
   `:total-mass` to the nearest OTHER cell — sediment transport at field
   scale. `:density-per-element` scales with each cell's mass ratio (share-
   preserving, `domain.interior/resource-cell` inverted). The field's total
   resource mass is invariant up to double rounding: mass is TRANSPORTED,
   never created. Returns {:field :delta}; validates the touched cells."
  [field op]
  (let [anchor   (:anchor op)
        mag      (double (:magnitude op))
        cells    (:resources field)
        donor    (nearest-by (comp :center :region) anchor cells)
        _        (when-not donor
                   (throw (ex-info "domain.voxel.sculpt/apply-erosion: field carries no resource cells"
                                   {:op op})))
        receiver (nearest-by (comp :center :region)
                             (normalize (get-in donor [:region :center]) [1.0 0.0 0.0])
                             (remove #(= % donor) cells))
        _        (when-not receiver
                   (throw (ex-info "domain.voxel.sculpt/apply-erosion: no second resource cell to deposit into"
                                   {:op op})))
        moved    (* mag voxel/sculpt-erosion-cell-mass-fraction
                    (double (:total-mass donor)))
        rescale  (fn [cell mass']
                   (let [ratio (/ (double mass') (double (:total-mass cell)))]
                     (-> cell
                         (assoc :total-mass (double mass'))
                         (update :density-per-element
                                 (fn [dpe] (into {} (map (fn [[k v]] [k (double (* ratio (double v)))])
                                                        dpe)))))))
        donor'    (rescale donor (- (double (:total-mass donor)) moved))
        receiver' (rescale receiver (+ (double (:total-mass receiver)) moved))]
    (when-not (voxel/resource-cell? donor')
      (throw (ex-info "domain.voxel.sculpt/apply-erosion: biased donor fails law.voxel/resource-cell-schema"
                      {:cell donor'})))
    (when-not (voxel/resource-cell? receiver')
      (throw (ex-info "domain.voxel.sculpt/apply-erosion: biased receiver fails law.voxel/resource-cell-schema"
                      {:cell receiver'})))
    {:field (update field :resources
                    (fn [rs] (mapv (fn [r] (cond
                                             (= r donor)    donor'
                                             (= r receiver) receiver'
                                             :else          r))
                                   rs)))
     :delta {:verb      :erosion
             :donor     (get-in donor [:region :center])
             :receiver  (get-in receiver [:region :center])
             :moved-kg  (double moved)}}))

(defn apply-volcanism
  "Volcanism's field bias: the nearest UPWELLING convection cell's speed
   multiplies by (1 + `law.voxel/sculpt-convection-speed-lever` ×
   magnitude) — the statistical steer toward melt delivery under the
   anchor. Flow speed is not mass. Returns {:field :delta}; validates the
   touched cell."
  [field op]
  (let [anchor (:anchor op)
        mag    (double (:magnitude op))
        cell   (nearest-by :center anchor
                           (filter #(= :upwelling (:flow %)) (:convection field)))
        _      (when-not cell
                 (throw (ex-info "domain.voxel.sculpt/apply-volcanism: field carries no upwelling cell"
                                 {:op op})))
        factor (+ 1.0 (* voxel/sculpt-convection-speed-lever mag))
        cell'  (update cell :speed #(* factor (double %)))]
    (when-not (voxel/mantle-convection-cell? cell')
      (throw (ex-info "domain.voxel.sculpt/apply-volcanism: biased cell fails law.voxel/mantle-convection-cell-schema"
                      {:cell cell'})))
    {:field (update field :convection
                    (fn [cs] (mapv #(if (= (:id %) (:id cell)) cell' %) cs)))
     :delta {:verb         :volcanism
             :cell         (:id cell)
             :speed-factor (double factor)}}))

(defn apply-op
  "Dispatch one `law.voxel/sculpt-op-schema` op to its field effect.
   Throws on an unknown verb (fail loudly — a malformed op in the request
   channel is a contract violation, not a no-op)."
  [field op]
  (when-not (voxel/sculpt-op? op)
    (throw (ex-info "domain.voxel.sculpt/apply-op: op fails law.voxel/sculpt-op-schema"
                    {:op op})))
  (case (:verb op)
    :uplift    (apply-uplift field op)
    :erosion   (apply-erosion field op)
    :volcanism (apply-volcanism field op)))

;; --- Local edit derivation (macro-drives-local: resolved band only) ------------

(defn- op-basis
  "Tangent basis [t1 t2] at unit `anchor` — the column coordinates of the
   influence disc."
  [anchor]
  (let [ref (if (< (Math/abs (nth anchor 0)) 0.9) [1.0 0.0 0.0] [0.0 1.0 0.0])
        t1  (normalize (sp/cross anchor ref) [0.0 1.0 0.0])]
    [t1 (sp/cross anchor t1)]))

(defn- column-id
  "Quantized tangent-plane column key of body-centric centre `c` under
   basis [t1 t2]: [round(c·t1/e) round(c·t2/e)] — voxels sharing a column
   sit above one another along the anchor axis, up to grid quantization."
  [t1 t2 c]
  [(long (Math/round (/ (sp/dot c t1) e)))
   (long (Math/round (/ (sp/dot c t2) e)))])

(defn- band-columns
  "The resolved, non-carved band voxels grouped by `column-id` under
   [anchor t1 t2], each column sorted SHALLOW→DEEP along the anchor axis
   (descending c·anchor). Deterministic: offsets iterate in sorted order,
   ties break by offset."
  [voxels anchor t1 t2]
  (->> (sort (keys voxels))
       (keep (fn [offset]
               (when-let [v (get voxels offset)]
                 (let [c (band/voxel-center offset)]
                   {:offset offset :voxel v :center c
                    :par    (sp/dot c anchor)
                    :perp   (sp/len (sp/v- c (sp/v* anchor (sp/dot c anchor))))
                    :col    (column-id t1 t2 c)}))))
       (group-by :col)
       (into {}
             (map (fn [[k xs]] [k (vec (sort-by (fn [x] [(- (double (:par x))) (:offset x)])
                                                xs))])))))

(defn- density-delta-edits
  "Materialize `{offset signed-Δdensity}` into `law.voxel/voxel-edit-schema`
   maps over `voxels`: each entry's voxel with the delta applied, in
   sorted-offset order, skipping zero deltas and density-nonpositive
   results (the floor discipline makes the latter unreachable — reaching
   it is a caller bug, and a silent skip here would hide a conservation
   break... so throw instead)."
  [voxels deltas]
  (mapv (fn [offset]
          (let [v   (get voxels offset)
                d   (double (get deltas offset))
                rho (+ (double (:density v)) d)]
            (when-not (pos? rho)
              (throw (ex-info "domain.voxel.sculpt: density delta would drain a voxel below zero mass — conservation bug"
                              {:offset offset :voxel v :delta d})))
            {:offset offset :after (assoc v :density (double rho))}))
        (sort (map key (remove (fn [[_ d]] (zero? (double d))) deltas)))))

(defn- derive-uplift-edits
  "Uplift's local fall-out: within each in-disc column, move magnitude ×
   `law.voxel/sculpt-mass-move-fraction` of the DEEPEST voxel's
   displaceable mass to the SHALLOWEST voxel — the crust thickens upward,
   column mass exactly redistributed. Single-voxel columns have nowhere to
   move mass from and are skipped."
  [op columns radius]
  (let [mag (double (:magnitude op))]
    (->> (vals columns)
         (filter (fn [col] (and (<= (double (:perp (first col))) radius)
                                (< 1 (count col)))))
         (reduce
          (fn [deltas col]
            (let [top   (first col)
                  deep  (peek col)
                  displaceable (* (band/voxel-mass (:voxel deep))
                                  (- 1.0 voxel/sculpt-donor-mass-floor-fraction))
                  dm    (* mag voxel/sculpt-mass-move-fraction displaceable)
                  dd    (/ dm e3)]
              (-> deltas
                  (update (:offset deep) (fnil - 0.0) dd)
                  (update (:offset top) (fnil + 0.0) dd))))
          {}))))

(defn- derive-erosion-edits
  "Erosion's local fall-out: each in-disc column's TOP voxel exports
   magnitude × `law.voxel/sculpt-mass-move-fraction` of its displaceable
   mass; the total is spread evenly over the tops of the
   `law.voxel/sculpt-erosion-recipient-fraction` of columns FARTHEST from
   the anchor axis (the local lowlands). Removed sum == added sum by
   construction: sediment transport, never creation."
  [op columns radius]
  (let [mag    (double (:magnitude op))
        cols   (vals columns)
        donors (filter (fn [col] (<= (double (:perp (first col))) radius)) cols)
        n-rec  (max 1 (long (Math/ceil (* voxel/sculpt-erosion-recipient-fraction
                                          (double (count cols))))))
        recipients (->> cols
                        (sort-by (fn [col] [(- (double (:perp (first col))))
                                            (:col col)]))
                        (take n-rec))
        removed (reduce (fn [m col]
                          (let [top (first col)
                                dm  (* mag voxel/sculpt-mass-move-fraction
                                       (band/voxel-mass (:voxel top))
                                       (- 1.0 voxel/sculpt-donor-mass-floor-fraction))]
                            (assoc m (:offset top) dm)))
                        {} donors)
        total  (reduce + 0.0 (map (fn [[k v]] (double v)) (sort removed)))
        gain   (/ total (double (count recipients)) e3)]
    (reduce (fn [deltas col]
              (update deltas (:offset (first col)) (fnil + 0.0) gain))
            (reduce-kv (fn [deltas offset dm]
                         (update deltas offset (fnil - 0.0) (/ (double dm) e3)))
                       {} removed)
            recipients)))

(defn- derive-volcanism-edits
  "Volcanism's local fall-out: every in-disc voxel gains magnitude ×
   `law.voxel/sculpt-volcanism-thermal-lever-k` kelvin; voxels crossing
   `law.voxel/sculpt-melt-temperature-k` transition `:solid` → `:melt` and
   lose their shear strength (cohesion 0 — molten rock does not bind).
   Density and material are untouched: band mass is exactly invariant."
  [op voxels anchor radius]
  (let [mag (double (:magnitude op))
        dt  (* mag voxel/sculpt-volcanism-thermal-lever-k)]
    (into []
          (keep (fn [offset]
                  (when-let [v (get voxels offset)]
                    (let [c    (band/voxel-center offset)
                          perp (sp/len (sp/v- c (sp/v* anchor (sp/dot c anchor))))]
                      (when (<= perp radius)
                        (let [t' (+ (double (:temperature v)) dt)]
                          {:offset offset
                           :after  (cond-> (assoc v :temperature (double t'))
                                     (>= t' voxel/sculpt-melt-temperature-k)
                                     (assoc :state :melt :cohesion 0.0))}))))))
          (sort (keys voxels)))))

(defn derive-edits
  "The local voxel edits that fall out of `op` over the resolved `band`
   (`{:spec :voxels}` or nil/empty — then []): the macro-drives-local rule.
   Only voxels whose centre lies within magnitude ×
   `law.voxel/sculpt-influence-radius-reference-m` of the op's anchor axis
   are touched, and ONLY resolved voxels are touched — an unresolved region
   carries the field change statistically and emits NOTHING (no diff, no
   edit). Edits come out in sorted-offset order (the queue's replay-order
   discipline)."
  [op band]
  (let [voxels (:voxels band)]
    (if (empty? voxels)
      []
      (let [anchor (:anchor op)
            [t1 t2] (op-basis anchor)
            radius (* (double (:magnitude op))
                      voxel/sculpt-influence-radius-reference-m)]
        (case (:verb op)
          :uplift    (density-delta-edits voxels
                                          (derive-uplift-edits op (band-columns voxels anchor t1 t2) radius))
          :erosion   (density-delta-edits voxels
                                          (derive-erosion-edits op (band-columns voxels anchor t1 t2) radius))
          :volcanism (derive-volcanism-edits op voxels anchor radius))))))

;; --- The fold: field bias + derived edits -> queued jobs -----------------------

(defn edits->sculpt-jobs
  "Chunk derived `edits` into budget-fitting `:apply-edits` jobs with
   provenance `:sculpt` (`domain.voxel.queue/edits->jobs` — NEVER the
   oversized-head escape). `region` is the band's spec region, the
   out-of-band diff target: edits are all in-band at enqueue time, but a
   retarget can demote the band before they drain, and then the region is
   what the immediate diff is keyed against."
  [edits region]
  (queue/edits->jobs edits {:provenance :sculpt :region region}))

(defn fold-ops
  "Fold paid sculpt `ops` into the macro `field` and the resolved `band`:
   each op biases the field (`apply-op`) and derives its local edits over
   the band (`derive-edits`), chunked into `:apply-edits` jobs. Returns
   {:field field' :jobs [...]}. With no ops the field passes through
   IDENTICALLY and no jobs are produced; with ops and no band the field
   still changes and ZERO edits are produced — macro-drives-local."
  [field band ops]
  (if (empty? ops)
    {:field field :jobs []}
    (let [region (get-in band [:spec :region])]
      (reduce (fn [{:keys [field jobs]} op]
                (let [biased (apply-op field op)
                      edits  (derive-edits op band)]
                  {:field (:field biased)
                   :jobs  (into jobs (edits->sculpt-jobs edits region))}))
              {:field field :jobs []}
              ops))))

;; --- Actuation (serial, pre-tick — the domain.intervention/place precedent) ----

(defn- committed-world-eid
  "The eid of the committed world, or nil — the same hard-irreversible
   marker scan `domain.voxel.focus` performs (duplicated here because
   focus depends on this namespace; the scan must not become a shared
   util — it IS the commitment gate)."
  [world]
  (some (fn [[eid state]] (when (= :committed state) eid))
        (get-in world [:components c/commitment-state] {})))

(defn- validate-op!
  "Throw `ex-info` when `op` fails `law.voxel/sculpt-op-schema` — a
   malformed op would fold garbage into the field one tick later, so fail
   at actuation, not mid-fold."
  [op]
  (when-not (voxel/sculpt-op? op)
    (throw (ex-info "domain.voxel.sculpt: op fails law.voxel/sculpt-op-schema"
                    {:op op})))
  op)

(defn request-op
  "Spend Resonance to request a god-scale sculpt op of `verb` at
   `magnitude` ∈ (0,1], centred on the observer's sub-focus direction over
   the committed world. The actuation entry point (the
   `domain.intervention/place` pattern): pure world→world', called
   serially between ticks by infra input (or tests).

   Gates — ALL must hold, else the world is returned unchanged (the
   caller never pre-checks):
   - an observer and a committed world exist
   - the verb's palette ability (`law.voxel/sculpt-verb->ability`) is
     armed in the observer's `c/palette` under `:active :planetary`
   - the observer can afford `op-cost` Resonance

   On success: spends the Resonance (`player/spend-resonance`) and appends
   the paid, schema-validated op to the `:voxel/sculpt-ops` world key,
   stamped with the cost and the current tick. Throws on an unknown verb
   or an out-of-range magnitude — those are caller bugs, not gates."
  [world verb magnitude]
  (when-not (voxel/sculpt-verb? verb)
    (throw (ex-info "domain.voxel.sculpt/request-op: unknown sculpt verb"
                    {:verb verb})))
  (when-not (and (number? magnitude) (pos? (double magnitude)) (<= (double magnitude) 1.0))
    (throw (ex-info "domain.voxel.sculpt/request-op: magnitude outside (0,1]"
                    {:verb verb :magnitude magnitude})))
  (let [obs      (player/get-observer world)
        obs-eid  (player/observer-entity world)
        eid      (committed-world-eid world)
        ability  (get voxel/sculpt-verb->ability verb)
        palette  (when obs-eid (ecs/get-component world obs-eid c/palette))
        cost     (op-cost verb magnitude)]
    (if (and obs eid
             (= :planetary (:active palette))
             (contains? (set (vals (:slots palette))) ability)
             (player/can-afford-resonance? obs cost))
      (let [anchor (normalize (sp/v- (:focus-position obs)
                                     (ecs/get-component world eid c/position))
                              [0.0 0.0 1.0])
            op     (validate-op! {:verb      verb
                                  :magnitude (double magnitude)
                                  :anchor    anchor
                                  :target    eid
                                  :cost      (double cost)
                                  :tick      (long (or (:tick world) 0))})]
        (-> world
            (player/update-observer player/spend-resonance cost)
            (update :voxel/sculpt-ops (fnil conj []) op)))
      world)))

(defn clear-sculpt-ops
  "Barrier step: drop the `:voxel/sculpt-ops` world key after the fan-out
   that translated it into `c/voxel-sculpt-request` (the
   `intervention/expire-interventions` precedent, wired into
   `domain.genesis.tick/tick-physics`) — each op folds exactly once, one
   Jacobi tick after actuation. Pure: world → world'."
  [world]
  (cond-> world
    (contains? world :voxel/sculpt-ops)
    (dissoc :voxel/sculpt-ops)))

;; --- System --------------------------------------------------------------------

(defn sculpt-system
  "The `:voxel-sculpt` write-set system (double-buffer fan-out). Sole
   writer of `c/voxel-sculpt-request`: translates the paid ops on the
   `:voxel/sculpt-ops` world key (the `:genesis/interventions` precedent —
   world keys are not declarable in the registry's :reads) into the
   request component on the committed world, auto-clearing the stale
   request when the key drains (`tick/contribution-write-set`). Emits
   NOTHING when no world is committed — the whole subsystem is gated on
   the crossed commitment horizon. Every op is re-validated at this
   boundary: the world key is an injection point, and a malformed op
   failing loudly here beats a corrupt field fold one tick later."
  []
  {:id     :voxel-sculpt
   :ns     'domain.voxel.sculpt
   :writes (reg/registry-writes :voxel-sculpt)
   :run
   (fn [world]
     (if-let [eid (committed-world-eid world)]
       (let [ops (get world :voxel/sculpt-ops)]
         (doseq [op ops]
           (when-not (voxel/sculpt-op? op)
             (throw (ex-info "domain.voxel.sculpt: :voxel/sculpt-ops entry fails law.voxel/sculpt-op-schema"
                             {:op op}))))
         (tick/contribution-write-set
          c/voxel-sculpt-request
          (if (seq ops) {eid (vec ops)} {})
          (keys (get-in world [:components c/voxel-sculpt-request]))))
       {}))})
