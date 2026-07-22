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

;; --- Edit diffs (design §7.3: field-seed + edit diff save strategy) -------------

(def voxel-edit-schema
  "One voxel-level change inside an edit diff: the voxel-local integer grid
   offset within the target region, and the voxel state after the edit
   (nil = carved away). `:before` is optional — the regenerable field seed
   already determines it; persist it only when the pre-edit state diverged
   from the seed (e.g. an earlier diff).

   Grid convention (load-bearing, design §7.2): offsets index the SEED'S
   canonical grid — one fixed voxel edge length per world, pinned by the
   seed generator (slice 2) and never varying per band or per focus depth.
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
