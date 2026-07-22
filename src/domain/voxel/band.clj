(ns domain.voxel.band
  "Pure focus-band math for the voxel substrate (Voxel 3,
   kanban/tasks/voxel-focus-promotion-demotion.md; design
   docs/designs/planetary-voxel-substrate.md §7.2 RESOLVED 2026-07-22:
   focus-driven dynamic band).

   The band is the resolved representation of the SAME mass the macro
   geology field (`domain.interior/seed-field`) describes statistically:
   materializing a band does NOT debit the field (the field is a pure,
   regenerable function — there is nothing to debit), and demoting a band
   does NOT rewrite the field (fold-back expresses itself as edit-diff
   records appended to the world's diff vector — the field-seed + edit-diff
   save strategy of design §7.3). The conservation invariant is therefore
   REPRESENTATIONAL: band ≡ field + diffs over the band volume, exactly, and
   every function here is built so that equality holds by construction —
   voxels are sampled from the field deterministically, and diffs carry
   deviations verbatim.

   COORDINATES: everything in this namespace is BODY-CENTRIC — voxel grid
   offsets [i j k] index the canonical grid anchored at the body centre
   (voxel centre = [(i+0.5)·e (j+0.5)·e (k+0.5)·e], e =
   `law.voxel/canonical-voxel-edge-m`), matching `law.voxel/region-schema`'s
   body-centric convention. The system (`domain.voxel.focus`) translates
   the observer's absolute focus position into the body frame before
   calling here.

   DEPTH FOLLOWS FOCUS (the owner decision): band depth is
   `law.voxel/focus-band-depth-reference-m × coherence × focus-intensity` —
   the same `observation-effect` product that scales how strongly attention
   resolves reality in the horizontal focus cone, projected downward. The
   horizontal extent follows the focus cone (`focus-radius × coherence`,
   `domain.player.focus/probability-collapse-radius`). The two levers of
   `narrow-focus`/`widen-focus` are exactly these: tighten the cone, and
   intensity — hence depth — rises. Both extents are clamped so the band
   never exceeds `law.voxel/focus-band-max-voxels` voxels (performance is a
   correctness property): a deeper band narrows, a wider band shallows —
   attention is the resource and the voxel budget is its exchange rate."
  (:require
   [law.composition :as comp]
   [law.interior :as law-int]
   [law.voxel :as voxel]
   [shape.spatial :as sp]))

(def ^:private e
  "Canonical voxel edge (m) — local alias."
  voxel/canonical-voxel-edge-m)

(def ^:private half-diagonal-m
  "Half the voxel body diagonal (m): the radius that bounds one voxel from
   its centre."
  (* e (/ (Math/sqrt 3.0) 2.0)))

;; --- Small vec helpers (body-centric, allocation-light where hot) --------------

(defn- normalize
  "Unit vector of `v`; `fallback` when `v` is near-zero."
  [v fallback]
  (let [l (sp/len v)]
    (if (> l 1.0e-12)
      (mapv (fn [x] (double (/ x l))) v)
      fallback)))

;; --- Voxel seeding from the macro field ------------------------------------------

(defn layer-at
  "The macro layer of `field` containing body-centric radius `r` (m). Radii
   at or beyond the body surface map to the outermost shell (surface voxels
   whose centres poke up to e/2 above `:radius-m` still sample the crust);
   the layers are emitted inside-out, so the shell is last."
  [field r]
  (let [layers (:layers field)]
    (or (some (fn [layer]
                (when (and (<= (:inner-radius layer) r)
                           (< r (:outer-radius layer)))
                  layer))
              layers)
        (last layers))))

(defn- cell-material
  "Voxel material implied by a resource cell's `:density-per-element`:
   ice-former-dominated cells read as `:ice`, metal-enriched (Fe+Ni ≥
   `metal-ore-share`) as `:ore`, anything else as undifferentiated
   `:regolith`. The cell records carry no `:kind` (slice 1 schema), so the
   override is derived from their element content — deterministic, and
   consistent with the seeder's enrichment directions (polar cells hold the
   ice-formers, convergent margins the metals)."
  [cell]
  (let [dpe   (:density-per-element cell)
        total (reduce + 0.0 (map (fn [[k v]] (double v)) (sort-by key dpe)))
        share (fn [els] (if (pos? total)
                          (/ (reduce + 0.0 (map (fn [el] (double (get dpe el 0.0)))
                                                (sort els)))
                             total)
                          0.0))]
    (cond
      (> (share comp/ice-formers) 0.5) :ice
      (>= (share #{:Fe :Ni}) 0.25)     :ore
      :else                            :regolith)))

(defn- material-at
  "Voxel material at body-centric centre `c` in `layer`: the resource-cell
   override when `c` sits inside a coarse resource cell's region, else the
   layer default. First model — slices 4+ refine with condensation history."
  [field c layer]
  (or (some (fn [cell]
              (when (<= (sp/dist c (get-in cell [:region :center]))
                        (get-in cell [:region :radius]))
                (cell-material cell)))
            (:resources field))
      (case (:name layer)
        :core      :ore
        :mantle    :basalt
        :crust     :basalt
        :ice-shell :ice
        :basalt)))

(defn seed-voxel
  "The seed voxel at body-centric centre `c` ([x y z] m) — the state the
   deterministic field regenerates for that point, BEFORE any edit-diff
   replay. Density and temperature come from the containing macro layer
   (uniform per layer — the documented first model), state is `:solid`
   (melt enters through edits/collision carving, not the seed), cohesion is
   the layer reference (`law.interior/layer-cohesion-reference-pa`).
   Validates `law.voxel/voxel-schema` by construction."
  [field c]
  (let [r     (sp/len c)
        layer (layer-at field r)]
    {:material    (material-at field c layer)
     :density     (double (:density layer))
     :temperature (double (:temperature layer))
     :state       :solid
     :cohesion    (double (get law-int/layer-cohesion-reference-pa
                               (:name layer) 1.0e7))}))

(defn voxel-center
  "Body-centric centre ([x y z] m) of canonical grid offset `[i j k]`."
  [[i j k]]
  [(* (+ (double i) 0.5) e)
   (* (+ (double j) 0.5) e)
   (* (+ (double k) 0.5) e)])

;; --- Edit-diff replay ------------------------------------------------------------

(defn replay-diffs
  "Fold an ordered `law.voxel/edit-diff-schema` vector into
   `{offset voxel-or-nil}` — later diffs win, per design §7.3's ordered
   replay. A nil `:after` means the voxel was carved away."
  [diffs]
  (reduce (fn [m diff]
            (reduce (fn [m edit] (assoc m (:offset edit) (:after edit)))
                    m
                    (:delta diff)))
          {}
          diffs))

(defn materialize
  "The resolved voxels of band volume `offsets` from `field` with `diffs`
   replayed on top: seed state everywhere, diff deviations verbatim. Every
   offset is present in the result; a replayed CARVE (nil `:after`) yields
   a nil value, the same representation an `:apply-edits` carve leaves in
   the live band — so demote → re-promote round-trips map-for-map. Load =
   regenerate seed + replay diffs (§7.3)."
  [field diffs offsets]
  (let [replayed (replay-diffs diffs)]
    (into {}
          (map (fn [offset]
                 [offset (if (contains? replayed offset)
                           (get replayed offset)
                           (seed-voxel field (voxel-center offset)))]))
          offsets)))

;; --- Band geometry ----------------------------------------------------------------

(defn- immediate-radius
  "Observer immediate focus radius (attention shell, collapse-radius
   fallback) — the reach test for surface overlap, the same rule the
   `:focus-zone` promotion uses horizontally."
  [obs]
  (or (get-in obs [:attention-shell :immediate-r])
      (* (double (:focus-radius obs)) (double (:coherence obs)))))

(defn band-target
  "The band spec for observer `obs` over a seeded `field`, given the
   BODY-CENTRIC focus position `f-rel` (absolute focus minus the body
   centre), or nil when the focus volume does not overlap the world's
   surface (surface gap beyond the immediate radius).

   Spec map:
     :dir      unit body-centre → sub-focus direction
     :anchor   sub-focus surface point (dir × :radius-m)
     :h-r      horizontal band radius (m) — focus-radius × coherence,
               clamped to [min-edges × e, the voxel-budget cap at depth]
     :depth-m  band depth (m) — focus-band-depth-reference-m × coherence ×
               focus-intensity, clamped to [e, min(shell thickness, the
               voxel-budget depth cap)]
     :region   bounding `law.voxel/region-schema` sphere of the band
               volume (the diff target identifier)

   THE VOXEL CAP IS REAL, NOT ASPIRATIONAL: both clamps derive from the
   count bound n(h, d) ≤ π(h/e + ½)² × layers(d), where layers(d) =
   ⌊(d + e/2)/e⌋ + 1 counts the accepted radial window [R−d, R+e/2] and
   the ½-cell rim margin covers grid discretization of the disc (the
   bound the pre-fix h-cap ignored — it undercounted by ~5% and the
   'hard' cap leaked). `max-depth` is the deepest d whose MINIMUM
   horizontal patch still satisfies the bound; `h-cap` inverts the same
   bound at the chosen depth. So (count (band-offsets spec)) ≤
   `law.voxel/focus-band-max-voxels` holds by construction, and a test
   pins it (`domain.voxel-focus-test/band-respects-voxel-cap`).

   A degenerate focus at the body centre falls back to the +z pole —
   deterministic, and unreachable in practice (the observer cannot focus
   on the centre of the world they stand on)."
  [obs field f-rel]
  (let [R           (double (:radius-m field))
        d           (sp/len f-rel)
        surface-gap (max 0.0 (- d R))]
    (when (<= surface-gap (immediate-radius obs))
      (let [dir        (normalize f-rel [0.0 0.0 1.0])
            anchor     (mapv double (sp/v* dir R))
            shell      (last (:layers field))
            shell-th   (- R (:inner-radius shell))
            effect     (* (double (:coherence obs)) (double (:focus-intensity obs)))
            min-edges  (double voxel/focus-band-min-horizontal-edges)
            max-layers (long (Math/floor (/ (double voxel/focus-band-max-voxels)
                                            (* Math/PI (+ min-edges 0.5) (+ min-edges 0.5)))))
            max-depth  (* e (max min-edges (dec max-layers)))
            depth      (-> (* voxel/focus-band-depth-reference-m effect)
                           (min (max e shell-th) max-depth)
                           (max (min e shell-th)))
            layers     (inc (long (Math/floor (/ (+ depth (* 0.5 e)) e))))
            h-min      (* e min-edges)
            h-cap      (* e (max min-edges
                                 (- (Math/sqrt (/ (double voxel/focus-band-max-voxels)
                                                  (* Math/PI layers)))
                                    0.5)))
            h-r        (-> (* (double (:focus-radius obs)) (double (:coherence obs)))
                           (min h-cap)
                           (max (min h-min h-cap)))
            half-d     (/ depth 2.0)
            region     {:center (mapv double (sp/v- anchor (sp/v* dir half-d)))
                        :radius (double (+ (Math/sqrt (+ (* h-r h-r) (* half-d half-d)))
                                           e))}]
        {:dir     dir
         :anchor  anchor
         :h-r     (double h-r)
         :depth-m (double depth)
         :region  region}))))

(defn band-offsets
  "Sorted vector of canonical grid offsets `[i j k]` inside band volume
   `spec`: voxels whose centre lies within horizontal distance `:h-r` of the
   anchor axis AND radially within [:radius-m − :depth-m, :radius-m + e/2]
   of the body centre. Deterministic; the enumeration order (lexicographic
   offset sort) is the chunk/diff order downstream. Hot loop: scalar
   arithmetic only, no vector allocation per cell."
  [field spec]
  (let [R      (double (:radius-m field))
        depth  (double (:depth-m spec))
        h-r    (double (:h-r spec))
        [dx dy dz] (:dir spec)
        [ax ay az] (:anchor spec)
        r-lo   (- R depth)
        r-hi   (+ R (* 0.5 e))
        r-lo2  (* r-lo r-lo)
        r-hi2  (* r-hi r-hi)
        h-r2   (* h-r h-r)
        [cx cy cz] (get-in spec [:region :center])
        rad    (double (get-in spec [:region :radius]))
        i0 (long (Math/floor (/ (- cx rad) e)))  i1 (long (Math/floor (/ (+ cx rad) e)))
        j0 (long (Math/floor (/ (- cy rad) e)))  j1 (long (Math/floor (/ (+ cy rad) e)))
        k0 (long (Math/floor (/ (- cz rad) e)))  k1 (long (Math/floor (/ (+ cz rad) e)))
        out (transient [])]
    (loop [i i0]
      (when (<= i i1)
        (let [x (* (+ i 0.5) e)]
          (loop [j j0]
            (when (<= j j1)
              (let [y (* (+ j 0.5) e)]
                (loop [k k0]
                  (when (<= k k1)
                    (let [z   (* (+ k 0.5) e)
                          r2  (+ (* x x) (* y y) (* z z))
                          ux  (- x ax) uy (- y ay) uz (- z az)
                          par (+ (* ux dx) (* uy dy) (* uz dz))
                          perp2 (- (+ (* ux ux) (* uy uy) (* uz uz)) (* par par))]
                      (when (and (<= r-lo2 r2 r-hi2) (<= perp2 h-r2))
                        (conj! out [(int i) (int j) (int k)]))
                      (recur (inc k)))))
                (recur (inc j))))))
        (recur (inc i))))
    (vec (sort (persistent! out)))))

;; --- Fold-back (demotion) ---------------------------------------------------------

(defn- chunk-region
  "Bounding `law.voxel/region-schema` sphere of the voxel centres `cs`
   (body-centric): centroid plus the farthest centre, padded by a voxel
   half-diagonal. The diff's target identifier — the region the seed
   regenerates for replay."
  [cs]
  (let [n   (double (count cs))
        ctr (mapv (fn [axis] (double (/ (reduce + 0.0 (map #(nth % axis) cs)) n)))
                  [0 1 2])
        r   (reduce (fn [m c] (max m (sp/dist ctr c))) 0.0 cs)]
    {:center ctr
     :radius (double (+ r half-diagonal-m))}))

(defn fold-chunk
  "Fold `offsets` of the resolved band back into the field + edit-diff
   representation (demotion of one chunk). For each offset the CURRENT band
   voxel is compared against the regenerated seed + prior-diff replay:
   deviations are emitted as one `law.voxel/edit-diff-schema` record per
   provenance group (provenance from the band's `:touched` map — untouched
   voxels are seed-equal by construction and emit NOTHING, so unpersisted
   regions round-trip through the regenerate-from-seed path alone).
   `:before` is omitted: the seed + replay already determines it.

   Returns {:voxels' :touched' :diffs} — the band remainder after the fold.
   `replayed` must be `replay-diffs` of every diff accumulated BEFORE this
   fold (earlier chunks of the same demotion cover disjoint offsets, so
   threading the running diff vector is equivalent)."
  [field replayed tick voxels touched offsets]
  (let [deviations (into []
                         (keep (fn [offset]
                                 (let [current (get voxels offset)
                                       seeded  (if (contains? replayed offset)
                                                 (get replayed offset)
                                                 (seed-voxel field (voxel-center offset)))]
                                   (when (not= current seeded)
                                     {:offset offset :after current}))))
                         offsets)
        by-prov   (group-by (fn [edit] (get touched (:offset edit) :sculpt))
                            deviations)
        diffs     (into []
                        (map (fn [[provenance delta]]
                               {:region     (chunk-region (map (fn [edit]
                                                                 (voxel-center (:offset edit)))
                                                               delta))
                                :delta      (mapv #(select-keys % [:offset :after]) delta)
                                :provenance provenance
                                :tick       (long (or tick 0))}))
                        by-prov)]
    {:voxels' (reduce dissoc voxels offsets)
     :touched' (reduce dissoc touched offsets)
     :diffs   diffs}))

;; --- Conservation accounting (test-facing, exact by construction) ---------------

(defn voxel-mass
  "Mass (kg) of one voxel: density × canonical voxel volume."
  [v]
  (* (double (:density v)) e e e))

(defn voxels-mass
  "Total mass (kg) of a voxel map, summed over its SORTED offsets so the
   double-addition order is pinned (spec-stable, the `domain.interior`
   FP-order hardening rule). Carved (nil) entries contribute nothing."
  [voxels]
  (reduce + 0.0 (keep (fn [offset] (when-let [v (get voxels offset)]
                                     (voxel-mass v)))
                      (sort (keys voxels)))))

(defn voxels-material-masses
  "Mass (kg) per `:material` of a voxel map — composition accounting at the
   substrate's own resolution (element-level composition lives in the
   resource field, which fold-back never rewrites). Carved (nil) entries
   contribute nothing."
  [voxels]
  (reduce (fn [m v] (update m (:material v) (fnil + 0.0) (voxel-mass v)))
          {}
          (filter some? (vals voxels))))
