(ns law.voxel
  "Schemas for the planetary voxel substrate — the `law/` vocabulary the whole
   voxel ladder speaks (design docs/designs/planetary-voxel-substrate.md §2, §3,
   §7; card kanban/tasks/voxel-substrate-law-schema.md).

   Units: SI throughout — density kg/m³, temperature K, cohesion Pa, lengths m,
   velocities m/s. Schemas only: no behavior, no systems, no components.
   Slices 2-6 of the epic build on these types."
  (:require
   [malli.core :as m]
   [law.composition :as comp]))

;; --- Materials (open set, design §7.4) ----------------------------------------

(def seed-materials
  "Seed mineral categories for voxel `:material`. Intentionally an OPEN set
   (design §7.4): basalt/granite/ore/ice/regolith are named examples, not a
   closed taxonomy — later slices add categories derived from
   `law.composition` element buckets (rock-formers / ice-formers) plus a
   condensation/differentiation history. Schemas therefore accept ANY keyword;
   this set exists so seeders and tests have a shared starting vocabulary."
  #{:basalt :granite :ore :ice :regolith})

(def material-schema
  "Voxel material/mineral category: any keyword. OPEN set per design §7.4 —
   `seed-materials` holds the named examples, but the schema deliberately does
   NOT close the enum so new categories land without a schema migration."
  :keyword)

;; --- Voxel record (design §2.1) ------------------------------------------------

(def voxel-state-schema
  "Voxel matter state: the matter-state ladder scoped to one voxel.
   `:suspended` covers fragmented rubble/ejecta in flight (design §2.1's
   'fragmented-rubble' names the same rung from the collision side)."
  [:enum :solid :melt :vapor :suspended])

(def voxel-schema
  "One voxel of resolved crust/interior (design §2.1).

   :material    keyword, open set (see `seed-materials`)
   :density     kg/m³, positive
   :temperature K, non-negative — a derived/re-checked quantity on mass/state
                change, never silently drifting (design §2.1 merge-bug note)
   :state       `voxel-state-schema`
   :cohesion    Pascals (SI), non-negative — shear strength binding the voxel
                to its neighbours; drives rubble-pile integrity and
                construction stability (design §2.1, §5). Unit chosen over a
                dimensionless 0..1 so collision/scaling-law physics (§6) can
                compare it directly against material-strength literature."
  [:map
   [:material material-schema]
   [:density [:and :double [:> 0]]]
   [:temperature [:and :double [:>= 0]]]
   [:state voxel-state-schema]
   [:cohesion [:and :double [:>= 0]]]])

(def voxel?
  "Predicate: does `value` satisfy `law.voxel/voxel-schema`?"
  (m/validator voxel-schema))

;; --- Regions -------------------------------------------------------------------

(def region-schema
  "A spherical region of a body's interior: body-centric centre (m) and
   radius (m). The shared target identifier for coarse geology field cells
   and edit diffs (design §7.3's save strategy keys diffs against regions
   that the field seed regenerates deterministically)."
  [:map
   [:center [:tuple :double :double :double]]
   [:radius [:and :double [:> 0]]]])

(def region?
  "Predicate: does `value` satisfy `law.voxel/region-schema`?"
  (m/validator region-schema))

;; --- Macro geology field records (design §2.2, §3) ------------------------------

(def plate-schema
  "One tectonic plate of the macro geology field (design §2.2): a boundary
   polygon of body-centric surface vertices (m) and the plate's relative
   velocity (m/s). `:kind` is optional coarse classification for seeders."
  [:map
   [:id :keyword]
   [:boundary [:vector {:min 3} [:tuple :double :double :double]]]
   [:velocity [:tuple :double :double :double]]
   [:kind {:optional true} [:enum :continental :oceanic :mixed]]])

(def plate?
  "Predicate: does `value` satisfy `law.voxel/plate-schema`?"
  (m/validator plate-schema))

(def mantle-convection-cell-schema
  "One cell of the mantle-convection pattern (design §2.2): body-centric
   centre (m), cell radius (m), flow sense (`:upwelling` / `:downwelling`),
   and flow speed (m/s). Upwelling cells seed rifts and hotspots; downwelling
   cells seed convergent margins — the same sites the resource field
   enriches (design §3)."
  [:map
   [:id :keyword]
   [:center [:tuple :double :double :double]]
   [:radius [:and :double [:> 0]]]
   [:flow [:enum :upwelling :downwelling]]
   [:speed [:and :double [:>= 0]]]])

(def mantle-convection-cell?
  "Predicate: does `value` satisfy `law.voxel/mantle-convection-cell-schema`?"
  (m/validator mantle-convection-cell-schema))

(def element-density-schema
  "Density per element (kg/m³) keyed by `law.composition/element-set` —
   reusing the existing element vocabulary rather than a parallel mineral
   taxonomy (design §3: a voxel's material is a category DERIVED from local
   element mass fractions)."
  [:map-of (into [:enum] (sort comp/element-set)) [:and :double [:>= 0]]])

(def element-density?
  "Predicate: does `value` satisfy `law.voxel/element-density-schema`?"
  (m/validator element-density-schema))

(def resource-cell-schema
  "One cell of the coarse resource field that exists everywhere on a
   committed world (design §3): a region, the conserved total ore-bearing
   mass it holds (kg), and density-per-element (kg/m³) resolving to concrete
   ore-bearing voxels under focus — consuming from `:total-mass` exactly like
   Regional→Immediate promotion conserves mass (commitment-and-resonance.md
   §5.5)."
  [:map
   [:region region-schema]
   [:total-mass [:and :double [:> 0]]]
   [:density-per-element element-density-schema]])

(def resource-cell?
  "Predicate: does `value` satisfy `law.voxel/resource-cell-schema`?"
  (m/validator resource-cell-schema))

(def macro-layer-schema
  "One differentiated interior layer of the macro geology field seed
   (core / mantle / crust / ice-shell; design §4, §7 layer-template gap —
   the honest first model lives in `domain.interior`, its constants in
   `law.interior`). Radii are body-centric metres, mass kg, density kg/m³,
   temperature K. Layers are emitted inside-out and mass-balanced: their
   masses sum to the seeded body's derived mass exactly (up to double
   rounding), so the field conserves the candidate's mass by construction."
  [:map
   [:name :keyword]
   [:inner-radius [:and :double [:>= 0]]]
   [:outer-radius [:and :double [:> 0]]]
   [:mass [:and :double [:> 0]]]
   [:density [:and :double [:> 0]]]
   [:temperature [:and :double [:>= 0]]]])

(def macro-layer?
  "Predicate: does `value` satisfy `law.voxel/macro-layer-schema`?"
  (m/validator macro-layer-schema))

;; --- Edit diffs (design §7.3: field-seed + edit diff save strategy) -------------

(def ^:const canonical-voxel-edge-m
  "The ONE canonical voxel edge length (m) of a seeded world's voxel grid
   (design §7.2): pinned here by the seed generator (slice 2,
   `domain.interior/seed-field`) and never varying per band or per focus
   depth. This is the grid `voxel-edit-schema`'s `:offset` indexes — the
   load-bearing constant that docstring refers to. 64 m: coarse enough that
   a focus band stays cheap, fine enough that mining/sculpting reads as
   terrain; a deliberate first pin, tunable only with a save migration."
  64.0)

(def voxel-edit-schema
  "One voxel-level change inside an edit diff: the voxel-local integer grid
   offset within the target region, and the voxel state after the edit
   (nil = carved away). `:before` is optional — the regenerable field seed
   already determines it; persist it only when the pre-edit state diverged
   from the seed (e.g. an earlier diff).

    Grid convention (load-bearing, design §7.2): offsets index the SEED'S
    canonical grid — one fixed voxel edge length per world, pinned by the
    seed generator (slice 2) as `canonical-voxel-edge-m` and never varying
    per band or per focus depth.
    Re-materialization at any focus depth must replay diffs onto that same
    canonical grid or the offsets are silently wrong."
  [:map
   [:offset [:tuple :int :int :int]]
   [:after [:maybe voxel-schema]]
   [:before {:optional true} [:maybe voxel-schema]]])

(def voxel-edit?
  "Predicate: does `value` satisfy `law.voxel/voxel-edit-schema`?"
  (m/validator voxel-edit-schema))

(def edit-provenance-schema
  "What made an edit: god-scale sculpting, character-scale mining or
   construction, or a collision's shock-carving pipeline (design §5, §6)."
  [:enum :sculpt :mine :construct :collision])

(def edit-provenance?
  "Predicate: does `value` satisfy `law.voxel/edit-provenance-schema`?"
  (m/validator edit-provenance-schema))

(def edit-diff-schema
  "The persisted unit of a player/collision edit against the deterministically
   regenerable macro-field seed (owner decision 2026-07-22, design §7.3) —
   load = regenerate seed + replay diffs. Carries the target region, the
   voxel delta, provenance, and the tick the edit was enqueued. This record
   is the whole persistence story: unpersisted regions round-trip through
   the macro field plus these diffs without loss (design §7.2).

   `:body` is optional for now — the committed world is the implicit default
   — but collision carving (slice 5) hits arbitrary bodies, so multi-body
   saves key diff collections by body id; carry `:body` explicitly whenever
   the target is not the committed world.

   Replay order within one `:tick` is collection order: diffs persist as an
   ordered vector, and the budgeted drain (design §7.1) appends in drain
   order, so overlapping regions in the same tick replay exactly as applied."
  [:map
   [:region region-schema]
   [:body {:optional true} :int]
   [:delta [:vector {:min 1} voxel-edit-schema]]
   [:provenance edit-provenance-schema]
   [:tick [:and :int [:>= 0]]]])

(def edit-diff?
  "Predicate: does `value` satisfy `law.voxel/edit-diff-schema`?"
  (m/validator edit-diff-schema))

;; --- Edit-queue budget (design §7.1, RESOLVED 2026-07-22) -----------------------

(def ^:const edit-budget-ms-per-tick
  "Hard cap on voxel-edit drain work per tick, in milliseconds (design §7.1,
   owner resolution 2026-07-22): edits enqueue; a budgeted drain applies at
   most this much work per tick and spills the rest to later ticks, so a big
   impact's crater visibly FORMS over ~a second. Declared here from day one
   as the tunable; the queue itself is a later slice."
  2.0)

;; --- Edit-queue cost model (design §7.1, Voxel 3) -----------------------------
;; The drain is BUDGETED IN ESTIMATED MILLISECONDS, never wall-clock time: every
;; job's cost is a pure function of its payload (explicit `:cost-ms`, else the
;; per-voxel model below), so tests drive the same drain with fake costs and the
;; live tick never calls a clock.

(def ^:const edit-cost-base-ms
  "Fixed estimated cost (ms) of draining any one edit-queue job — the
   bookkeeping overhead of applying a job independent of its voxel count."
  0.05)

(def ^:const edit-cost-per-voxel-ms
  "Estimated cost (ms) per voxel touched by an edit-queue job. 1 µs/voxel:
   the order of a handful of map operations on one voxel record."
  1.0e-3)

(def edit-chunk-voxels
  "Voxels per promotion/demotion chunk: the most voxel work one drained job
   may do while staying inside `edit-budget-ms-per-tick` under the cost
   model. The `:voxel-focus` system splits band promotion/demotion into
   chunks of this size at enqueue/step time, so the 2 ms cap holds for band
   churn exactly as it does for later sculpt/mine/collision edits — a big
   retarget visibly sweeps over several ticks."
  (long (Math/floor (/ (- edit-budget-ms-per-tick edit-cost-base-ms)
                       edit-cost-per-voxel-ms))))

(def max-edits-per-job
  "GUIDANCE FOR PRODUCERS (slices 4-6): the most edits one `:apply-edits`
   job may carry while its estimated cost stays inside the per-tick budget
   — equal to `edit-chunk-voxels` by construction of the cost model. Jobs
   are ATOMIC in the drain: a producer that enqueues more edits than this
   in one job forces the oversized-head escape and silently exceeds the
   2 ms cap for that tick. Split larger edit sets with
   `domain.voxel.queue/edits->jobs`, which preserves apply order across the
   chunks it emits."
  edit-chunk-voxels)

;; --- Focus band (design §7.2 RESOLVED 2026-07-22: focus-driven dynamic band) ---

(def ^:const focus-band-depth-reference-m
  "Band depth (m) at observation-effect 1.0 (design §7.2, owner resolution
   2026-07-22): the resolved band's depth is
   `focus-band-depth-reference-m × coherence × focus-intensity` — the SAME
   product (`domain.player.focus/observation-effect`) that scales how
   strongly attention resolves reality in the horizontal focus cone,
   projected downward. Deeper focus literally deepens the world: 10 km at
   full effect, crust-reference order (`law.interior/crust-thickness-
   reference-m` is 30 km), clamped per world by the shell thickness and by
   `focus-band-max-voxels`."
  1.0e4)

(def ^:const focus-band-max-voxels
  "Hard cap on resolved voxels in the focus band (performance is a
   correctness property): the band's horizontal extent AND depth are
   clamped against the count bound n(h, d) ≤ π(h/e + ½)² × layers(d) —
   the ½-cell rim margin and the radial-window layer count make the cap
   REAL (a plain π(h/e)²(d/e) estimate undercounts by ~5% and leaked
   8568 > 8192 before the margin was added; `domain.voxel.band/band-target`
   derives both clamps from the bound and
   `domain.voxel-focus-test/band-respects-voxel-cap` pins it). A deep band
   narrows and a wide band shallows — attention is the resource, and the
   voxel budget is its exchange rate. 8192 voxels ≈ 4 chunks at
   `edit-chunk-voxels`."
  8192)

(def ^:const focus-band-min-horizontal-edges
  "Minimum horizontal extent of the band, in canonical voxel edges: the band
   never resolves thinner than a 4×4 voxel patch no matter how deep the
   focus drives it."
  4)
