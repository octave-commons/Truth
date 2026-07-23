(ns domain.voxel.load
  "The first REAL save/load path of the §7.3 save story (card
   kanban/tasks/voxel-field-bias-persistence.md; design
   docs/designs/planetary-voxel-substrate.md §7.3 EXTENDED 2026-07-22):
   load = regenerate seed + replay field-diffs + replay voxel diffs.

   SAVE SHAPE: {:candidate :field-diffs :voxel-diffs} — the
   `:planet-candidate` record (regenerates the macro field seed through
   `domain.interior/seed-field`, bit-for-bit), the ordered
   `law.voxel/field-diff-schema` stream (macro-field biases: the op IS
   the diff), and the ordered `law.voxel/edit-diff-schema` stream (voxel
   deviations). A resolved band is transient attention state — its
   in-band deviations persist ONLY through demotion fold-back
   (`domain.voxel.focus`), so `save-state` is defined on an
   already-demoted (or never-resolved) world and reads the two
   component-carried diff vectors verbatim.

   REPLAY COMPOSITION (order is load-bearing): field-diffs re-apply
   FIRST — `domain.voxel.sculpt/apply-op` per record, in stream order,
   the same pure deterministic fold the live tick performed — and voxel
   diffs then replay onto the BIASED field
   (`domain.voxel.band/materialize`). The bias is what an unresolved
   world must persist (no voxel trace exists for it); on a resolved
   world the same replay keeps the field and the band-derived diffs
   consistent across sessions, closing the field/band divergence hole
   the Voxel 4 review found.

   Pure throughout: no I/O, no atoms, no clocks. Serialization
   (EDN/nippy) is an `infra/` concern layered over these functions."
  (:require
   [domain.ecs.components :as c]
   [domain.ecs.core :as ecs]
   [domain.interior :as interior]
   [domain.voxel.band :as band]
   [domain.voxel.sculpt :as sculpt]
   [law.voxel :as voxel]))

(defn replay-field-diffs
  "Fold an ordered `law.voxel/field-diff-schema` vector into `field`:
   re-apply each record's op through `domain.voxel.sculpt/apply-op`, in
   stream order — exactly the fold the live `:voxel-focus` tick
   performed, so the replayed field is the live field bit-for-bit.
   Every record is re-validated before it applies (the
   `domain.interior/validate-field!` precedent): a corrupt save fails
   loudly at load, never downstream as a silent field divergence."
  [field field-diffs]
  (reduce (fn [field fdiff]
            (when-not (voxel/field-diff? fdiff)
              (throw (ex-info "domain.voxel.load: field-diff fails law.voxel/field-diff-schema"
                              {:field-diff fdiff})))
            (:field (sculpt/apply-op field (:op fdiff))))
          field
          field-diffs))

(defn save-state
  "The save representation of the committed world `eid` in `world`:
   {:candidate :field-diffs :voxel-diffs} — the candidate record plus
   the two ordered diff streams (empty vectors when nothing has biased
   or edited the world). A pure read of the component store.
   PRECONDITION: the world's band is demoted or was never resolved — a
   resolved band's in-band deviations persist only through demotion
   fold-back, so saving a live band would drop them. Throws when `eid`
   carries no `c/planet-candidate`: there is nothing to regenerate the
   seed from."
  [world eid]
  (let [candidate (ecs/get-component world eid c/planet-candidate)]
    (when-not candidate
      (throw (ex-info "domain.voxel.load/save-state: entity carries no c/planet-candidate — nothing to regenerate the seed from"
                      {:eid eid})))
    (when (ecs/get-component world eid c/voxel-band)
      (throw (ex-info "domain.voxel.load/save-state: live c/voxel-band present — demote before saving or in-band deviations are silently dropped"
                      {:eid eid})))
    {:candidate    candidate
     :field-diffs  (vec (or (ecs/get-component world eid c/voxel-field-diffs) []))
     :voxel-diffs  (vec (or (ecs/get-component world eid c/voxel-edit-diffs) []))}))

(defn load-state
  "Load = regenerate seed + replay field-diffs + replay voxel diffs
   (design §7.3, extended). `save` is the `save-state` shape
   {:candidate :field-diffs :voxel-diffs}.

   Without `offsets`, returns {:field :voxel-diffs} — the whole
   persistent state of an UNRESOLVED world: the field with every bias
   replayed, and the voxel-diff stream for later materialization.

   With band `offsets` (the canonical grid offsets of the volume to
   re-materialize — the demoted band's sorted offsets), also returns
   :voxels: `domain.voxel.band/materialize` over the BIASED field with
   the voxel diffs replayed. Round-trip guarantee: `save-state` of a
   demoted world followed by `load-state` yields the live field exactly
   and the pre-demote band map-for-map.

   Every voxel diff is re-validated alongside the field-diffs: the two
   streams are the entire persistence story, and a malformed record
   fails loudly at load, not mid-materialization."
  ([save]
   (load-state save nil))
  ([{:keys [candidate field-diffs voxel-diffs] :as save} offsets]
   (when-not candidate
     (throw (ex-info "domain.voxel.load/load-state: save carries no :candidate record"
                     {:save save})))
   (doseq [diff voxel-diffs]
     (when-not (voxel/edit-diff? diff)
       (throw (ex-info "domain.voxel.load/load-state: voxel diff fails law.voxel/edit-diff-schema"
                       {:diff diff}))))
   (let [field (-> (interior/seed-field candidate)
                   (replay-field-diffs field-diffs))]
     (cond-> {:field field :voxel-diffs voxel-diffs}
       offsets (assoc :voxels (band/materialize field voxel-diffs offsets))))))
