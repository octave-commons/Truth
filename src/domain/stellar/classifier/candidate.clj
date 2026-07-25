(ns domain.stellar.classifier.candidate
  "M5 handoff Phase 4: the `:planet-candidate` output record and the `:handoff`
   write-set system that gates its emission.

   Split out of the former `domain.stellar.classifier` on 2026-07-24 (see
   `kanban/tasks/static-analysis-split-classifier.md`). This stage re-derives
   nothing that `domain.stellar.classifier.planet` (Phases 1-3) already wrote —
   it reads material-class/thermal-band/orbit-stable/atmosphere-class/
   retained-species off the frozen snapshot, one Jacobi tick stale, and assembles
   the candidate contract for bodies that also clear the stricter per-planet
   table."
  (:require
   [law.stellar                        :as law]
   [domain.ecs.core                    :as ecs]
   [domain.ecs.components              :as c]
   [domain.orbital.stability           :as stability]
   [domain.stellar.classifier.planet   :as planet]
   [shape.spatial                      :as sp]))
;; --- M5 handoff Phase 4: planet-candidate record + handoff gate -------------
;; See kanban/tasks/ecology-m5-phase4-handoff-event.md and parent
;; kanban/tasks/ecology-water-gate-snowline.md §2, §5. This is the FINAL M5
;; stage: it does not re-derive anything `classification-system` (Phases 1-3)
;; already wrote — it reads material-class/thermal-band/orbit-stable/
;; atmosphere-class/retained-species off the frozen snapshot (one tick
;; Jacobi-stale, same as every other cross-system read in this fan-out) and
;; assembles the full `:planet-candidate` contract (parent §5) for every body
;; that ALSO clears the stricter per-planet §2 table (mass, bound low-
;; eccentricity orbit, 150-400 K equilibrium temperature, <95% H/He by mass,
;; at least :thin atmosphere retention) — a planet-candidate is a STRICT
;; subset of a merely material-classified body. Emission of the whole batch
;; is additionally gated on the SYSTEM-level §2 criteria (a :star exists, at
;; least one eligible candidate exists, and no collision merge is currently
;; in flight); while that gate is false the write-set is simply `{}` for this
;; tick, so any previously-recorded candidates are left exactly as they were
;; (persist, never retracted) rather than being erased. The ledger
;; `:event/phase0-handoff` append itself is NOT done from inside this fan-out
;; — like every other real ledger append in this codebase (see
;; `domain.genesis.tick/emit-promotion-events`), dispatching an event from
;; inside a write-set `:run` only mutates a scratch snapshot that is diffed
;; away (see `domain.physics.collision/collision-detection-system`'s 0-arity
;; form for the same pattern with `:event/collision`); the ledger append is
;; therefore `domain.genesis.tick/emit-handoff-event`, a serial post-fold
;; step that reacts to this system's `c/planet-candidate` output, so there is
;; exactly one place that decides "has the gate fired" (here) and one place
;; that reacts to it (the ledger step).

(def ^:const candidate-max-eccentricity
  "Eccentricity ceiling for a planet-candidate's orbit (parent §2 table:
   \"Bound to the star; eccentricity < 0.4\")." 0.4)

(def ^:const candidate-min-temperature
  "Lower equilibrium-temperature bound (K) for a planet-candidate (parent §2
   table: \"between 150 K and 400 K\")." 150.0)

(def ^:const candidate-max-temperature
  "Upper equilibrium-temperature bound (K) for a planet-candidate (parent §2
   table)." 400.0)

(def ^:const candidate-max-h-he-fraction
  "H+He mass-fraction ceiling for a planet-candidate (parent §2 table:
   \"Not > 95% H/He by mass\")." 0.95)

(def ^:const core-dynamo-min-omega
  "Coarse angular-speed floor (rad/s) above which a convective interior is
   assumed to spin fast enough to sustain a magnetic dynamo (parent §5
   `:core-dynamo?`). Set below Earth's ~7.29e-5 rad/s sidereal rate so both
   Earth-like rocky rotators and faster gas giants qualify. This is a
   deliberately coarse Phase-0 proxy — NOT a Christensen-scaling-law dynamo
   number — matching the rest of this namespace's one-shot formation-time
   verdicts." 5.0e-5)

(defn- stable-star
  "`central-star` restricted to a body that has actually reached `:star`
   (parent §2 criterion 1) — a protostar does not satisfy the handoff gate,
   even though `central-star` itself (shared with Phases 1-3) also accepts
   protostars for thermal-band purposes."
  [world]
  (let [star (planet/central-star world)]
    (when (= :star (:matter-state star))
      star)))

(defn- h-he-fraction
  "H+He mass fraction of a `composition` map (missing species default 0)."
  [composition]
  (+ (double (get composition :H 0.0))
     (double (get composition :He 0.0))))

(defn- candidate-orbit-elements
  "Two-body orbital elements of candidate `eid` relative to `star`, or nil
   if position/velocity are not resolvable or the orbit is unbound."
  [world star eid]
  (when-let [pos (ecs/get-component world eid c/position)]
    (let [vel (or (ecs/get-component world eid c/velocity) [0.0 0.0 0.0])
          mu  (* law/G (double (:mass star)))
          r-vec (sp/v- pos (:position star))
          v-vec (sp/v- vel (:velocity star))]
      (stability/two-body-elements r-vec v-vec mu))))

(defn- eligible-candidate?
  "True when candidate `eid` clears every per-planet handoff test in the
   parent §2 table: mass above `law/rounding-mass-threshold`, a bound orbit
   with eccentricity < `candidate-max-eccentricity`, an equilibrium
   temperature in [`candidate-min-temperature` `candidate-max-temperature`],
   composition under `candidate-max-h-he-fraction` H+He by mass, and at
   least `:thin` atmosphere retention. Reads Phase 1-3's already-written
   material-class/orbit-stable/atmosphere-class rather than re-deriving
   them."
  [world star eid]
  (boolean
   (when-let [mass (ecs/get-component world eid c/mass)]
     (when-let [mclass (ecs/get-component world eid c/material-class)]
       (when (and (some? (ecs/get-component world eid c/orbit-stable))
                  (ecs/get-component world eid c/orbit-stable)
                  (not= :none (ecs/get-component world eid c/atmosphere-class))
                  (> (double mass) law/rounding-mass-threshold)
                  (<= (h-he-fraction (or (ecs/get-component world eid c/composition) {}))
                      candidate-max-h-he-fraction))
         (when-let [elements (candidate-orbit-elements world star eid)]
           (when-let [t-eff (planet/classify-body-equilibrium-temp world star eid mclass)]
             (and (< (:eccentricity elements) candidate-max-eccentricity)
                  (<= candidate-min-temperature t-eff candidate-max-temperature)))))))))

(defn- convective-interior?
  "Coarse proxy for a differentiated, convective interior: any resolved
   material class except `:mixed` (an undifferentiated, no-strong-category
   rubble pile)."
  [material-class]
  (contains? #{:rocky :icy :gaseous} material-class))

(defn core-dynamo?
  "Coarse Phase-0 estimate of `:core-dynamo?` (parent §5): true when the body
   has a plausibly convective interior (`convective-interior?`) AND is
   rotating faster than `core-dynamo-min-omega`. `spin` is the body-fixed
   angular-velocity vector (`c/spin`, rad/s), possibly nil."
  [material-class spin]
  (and (convective-interior? material-class)
       (some? spin)
       (>= (sp/len spin) core-dynamo-min-omega)))

(defn surface-gravity
  "Surface gravity g = GM/R² (m/s²) for `mass` (kg) and `radius` (m). 0.0 when
   `radius` is missing or non-positive (avoids a division by zero for a body
   whose structure hasn't resolved yet)."
  [mass radius]
  (let [r (double (or radius 0.0))]
    (if (pos? r)
      (/ (* law/G (double (or mass 0.0))) (* r r))
      0.0)))

(defn- formation-events-for
  "Every ledger event (by `:id`) whose `:entities` set includes `eid` — the
   threshold events that shaped this body (parent §5 `:formation-events`).
   Reads the ledger directly off `world` (a top-level world key, like
   `:genesis/spatial-tree`, not a component — see
   `domain.ecs.registry`'s docstring on what `:reads` covers)."
  [world eid]
  (mapv :id
        (filter #(contains? (:entities %) eid)
                (get-in world [:ledger :events] []))))

(defn build-candidate-record
  "Assemble the full `:planet-candidate` record (parent §5) for candidate
   `eid` relative to `star`. Every field is either a component Phases 1-3
   already wrote, or a direct pure derivation from mass/radius/composition/
   angular-momentum/spin/b-field already carried by the body — nothing here
   is invented data.

   Forward-compat note (planetary-voxel phase, not implemented by this
   card): `:bulk-composition`, `:thermal-band`, `:atmosphere-class`/
   `:retained-species`, `:rotation-axis`, and `:surface-gravity` are exactly
   the fields a future per-planet voxel-world phase would seed geography/
   chemistry/atmosphere generation from."
  [world star eid]
  (let [mclass (ecs/get-component world eid c/material-class)
        elements (candidate-orbit-elements world star eid)
        mass   (ecs/get-component world eid c/mass)
        radius (ecs/get-component world eid c/radius)
        spin   (ecs/get-component world eid c/spin)]
    {:planet-id              eid
     :star-id                (:id star)
     :material-class          mclass
     :thermal-band            (ecs/get-component world eid c/thermal-band)
     :equilibrium-temperature (planet/classify-body-equilibrium-temp world star eid mclass)
     :semi-major-axis         (:semi-major-axis elements)
     :eccentricity            (:eccentricity elements)
     :orbit-stable?           (boolean (ecs/get-component world eid c/orbit-stable))
     :atmosphere-class        (ecs/get-component world eid c/atmosphere-class)
     :retained-species        (or (ecs/get-component world eid c/retained-species) #{})
     :volatile-budget-kg      (ecs/get-component world eid c/volatile-budget)
     :differentiated-layers   (ecs/get-component world eid c/differentiated-layers)
     :bulk-composition        (or (ecs/get-component world eid c/composition) {})
     :angular-momentum        (or (ecs/get-component world eid c/angular-momentum) [0.0 0.0 0.0])
     :rotation-axis           (or (ecs/get-component world eid c/rotation-axis) [0.0 0.0 1.0])
     :oblateness              (ecs/get-component world eid c/oblateness)
     :surface-gravity         (surface-gravity mass radius)
     :core-dynamo?            (core-dynamo? mclass spin)
     :magnetic-field          (or (ecs/get-component world eid c/b-field) [0.0 0.0 0.0])
     :formation-events        (formation-events-for world eid)}))

(defn- system-settled?
  "Proxy for parent §2 criterion 3 (\"no unresolved catastrophic collisions
   pending\"): true when no entity currently carries `c/absorb-merge` — an
   in-flight, not-yet-folded collision merge. This is a snapshot proxy, like
   the Phase 2 orbit-stability check, not a 10 Myr forward lookahead."
  [world]
  (empty? (ecs/entities-with world c/absorb-merge)))

(defn handoff-system
  "Double-buffer write-set system: SOLE writer of `c/planet-candidate` (M5
   handoff Phase 4). Jacobi fan-out emitter — reads the frozen snapshot only.

   Emission is gated on the FULL parent §2 criteria: a `:star` (not merely a
   `:protostar`) exists (`stable-star`), at least one body clears every
   per-planet test in the §2 table (`eligible-candidate?`), and the system
   is not mid-collision (`system-settled?`). When all three hold, every
   currently-eligible candidate's full `:planet-candidate` record
   (`build-candidate-record`) is written this tick. When any one is false,
   the write-set is `{}` — candidates already recorded on a prior tick are
   left untouched (persist), never retracted.

   The ledger `:event/phase0-handoff` append is a SEPARATE serial step
   (`domain.genesis.tick/emit-handoff-event`) that reacts to this system's
   output after the fold — see this namespace's Phase 4 section docstring
   for why events are never dispatched from inside a write-set `:run`."
  []
  {:id     :handoff
   :writes #{c/planet-candidate}
   :reads  #{c/matter-state c/mass c/composition c/position c/velocity
             c/radius c/luminosity c/material-class c/thermal-band
             c/orbit-stable c/atmosphere-class c/retained-species
             c/angular-momentum c/rotation-axis c/oblateness c/b-field
             c/spin c/absorb-merge c/volatile-budget c/differentiated-layers}
   :run
   (fn [world]
     (if-let [_primary (stable-star world)]
       (let [stars (planet/stellar-bodies world)
             eids  (ecs/entities-with world c/material-class)
             ;; Multi-timescale card 4: eligibility and the candidate record
             ;; are evaluated against each body's OWN dominant attractor
             ;; (restricted to bodies whose parent has actually reached :star
             ;; — parent §2 criterion 1 reads "bound to the star"); the
             ;; system-level `stable-star` above only gates "a :star exists".
             body-parents (into {} (keep (fn [eid]
                                           (when-let [p (planet/dominant-attractor world eid stars)]
                                             (when (= :star (:matter-state p))
                                               [eid p]))))
                                eids)
             eligible (filterv #(and (contains? body-parents %)
                                     (eligible-candidate? world (get body-parents %) %))
                               eids)]
         (if (and (seq eligible) (system-settled? world))
           {c/planet-candidate
            (into {} (map (fn [eid] [eid (build-candidate-record
                                          world (get body-parents eid) eid)])) eligible)}
           {}))
       {}))})
