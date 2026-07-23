(ns domain.voxel.carve
  "Collision shock → voxel carving (Voxel 5, kanban/tasks/collision-shock-
   voxel-carving.md; design docs/designs/planetary-voxel-substrate.md §6).

   Impacts carve, melt, vaporize, and re-cool voxels using scaling-law
   approximations of shock physics — NO hydrocode. The constants are
   transcribed in `law.crater` from
   docs/research/2026-07-22-crater-scaling-laws-for-voxel-carving.md
   (Schmidt & Housen 1987 / Collins, Melosh & Marcus 2005 π-group
   dimensions; Pierazzo et al. 1997 energy-scaled melt — NOT
   Bjorkman-Holsapple point-source; Benz & Asphaug 1999 Q*_D gating;
   Kraus, Senft & Stewart 2011 ice fits; Croft 1985 complex conversion).
   Error bars live on the `law.crater` constants and are NOT re-derived
   here.

   INPUT CHANNEL — the existing collision pipeline, not a new one:
   `:collision-detection` (`domain.physics.collision`) emits
   `:event/collision` whose registered merge handler writes
   `c/absorb-merge` on the survivor. That packet ({:mass :velocity
   :position :composition :temperature} of the impactor) is the durable,
   tick-crossing record of the collision — the ledger event itself is
   diffed away at the write-set boundary (domain.genesis.tick §M5 note),
   and the packet is STICKY (the collision system never clears it, so the
   `:voxel-carve` system keeps an idempotency `:seen` set on its own
   request component — a genuinely new collision replaces the packet, a
    sticky one is already in `:seen`). The impactor's radius is NOT in the
    packet: diameter is recovered from mass ÷ class density
    (`law.crater/impactor-density-kg-per-m3`, a marked game first model).

   STICKY-PACKET COUPLING (reviewer-flagged hazard): this carving path
   DEPENDS on the absorb-merge packet being sticky — the `:seen` set is
   the only thing between one crater and one-crater-per-tick. The same
   stickiness means the integrator currently RE-APPLIES the packet's
   mass/momentum every tick (pre-existing behavior, verified: 100 → 110
   → 120 kg over two folds — an integrator-side issue, NOT this card's).
   If that double-apply is ever fixed by CLEARING the packet after
   consumption, carving silently loses its input channel; such a fix must
   either hand the packet to a durable per-collision record or move the
   `:seen` bookkeeping to whoever clears. Named here so the two changes
   cannot pass review independently.

   SUB-CATASTROPHIC CONSTRAINT (owner): when Q meets or exceeds the
   Benz & Asphaug Q*_D disruption threshold the classifier still
   CLASSIFIES, but the carving path handles only the cratering branch —
   disruption outcomes land in the request's `:disruptions` vector as
   `law.crater/disruption-report-schema` records naming the missing
   pipeline (fragmentation / magma-ocean FSM). That report IS the stop.

   KNOWN GAPS (honest, noted for later cards):
   - COMPLEX-CRATER RELAXATION is not carved: the transient bowl is
     excavated verbatim even in the `:complex-crater` regime (widened
     rim / reduced depth / uplift per Croft 1985 pending; flagged on
     `derive-edits`, constants marked UNUSED-PENDING in law.crater).
    - A collision on a committed world with NO resolved band produces no
      voxel edits: design §6's off-focus consequence (the macro geology
      field's melt-fraction scalar / `:env/magma-ocean` FSM flip) is not
      wired — the plan classifies and drops. The `c/voxel-field-diffs`
      stream (card voxel-field-bias-persistence) does NOT cover this:
      it persists sculpt-op field biases, and a no-band collision has no
      macro-field consequence wired at all — there is nothing to persist
      until the consequence itself exists (the melt-fraction scalar is
      the card that closes this).
   - Vapor voxels are tagged `:state :vapor` in the band (the schema's
     transient bookkeeping state); their mass should leave the solid
     field for the target's atmosphere/escaping debris (design §6 step 2)
     — that transport is not wired, so cooling condenses them back to
     `:solid` in place (mass-conserving first model).
   - Ejecta-blanket redeposition (research §5.3 item 5) is not yet
     carved: excavated mass is removed, not redistributed.
   - One absorb-merge packet per survivor per tick: a second collision
     on the same body in the same tick overwrites the first packet (the
     merge handler conj-counts one packet) — rare, and the overwrite is
     the collision system's contract, not this namespace's."
  (:require
   [domain.ecs.components :as c]
   [domain.ecs.core :as ecs]
   [domain.ecs.registry :as reg]
   [domain.interior :as interior]
   [domain.voxel.band :as band]
   [domain.voxel.queue :as queue]
   [law.composition :as comp]
   [law.crater :as law]
   [law.interior :as law-int]
   [shape.spatial :as sp]))

(def ^:private e
  "Canonical voxel edge (m) — local alias."
  law/sub-voxel-diameter-m)

(def ^:private e3
  "Canonical voxel volume (m³)."
  (* e e e))

(def ^:private G
  "Gravitational constant (SI) — surface-gravity fallback when the
   candidate record is unavailable."
  6.674e-11)

;; --- Regime classifier (pure; research §3 decision table) -------------------------

(defn coupling-parameter
  "C = a·U^μ·ρ_i^ν — the single scalar ordering late-stage crater flow
   (Holsapple & Schmidt 1987, JGR 92:B7). Debug/logging quantity; the
   classifier works from the dimensional fits below (the same fits with
   constants unfolded)."
  [{:keys [a U rho-i]} mu]
  (* (double a) (Math/pow (double U) mu) (Math/pow (double rho-i) 0.40)))

(defn disruption-q-star
  "Q*_D (J/kg) of a target of radius `R-t` (m), density `rho-t` (kg/m³)
   and `material-class` — the Benz & Asphaug 1999 catastrophic-disruption
   threshold, SI transcription (research §3):
   Q*_D = Q₀·(R/0.01 m)^a + B·10⁻⁷·(ρ/1000)·(R/0.01 m)^b.
   Head-on values; grazing raises Q*_D up to ×10 (conservative)."
  [R-t rho-t material-class]
  (let [{:keys [Q0-J-per-kg B a b]} (case material-class
                                      :ice law/disruption-ice
                                      law/disruption-basalt)
        x (/ (double R-t) 0.01)]
    (+ (* (double Q0-J-per-kg) (Math/pow x (double a)))
       (* (double B) 1.0e-7 (/ (double rho-t) 1000.0)
          (Math/pow x (double b))))))

(defn transient-diameter-gravity
  "Gravity-regime transient crater diameter (m): the Collins, Melosh &
   Marcus 2005 fit, D_tc = K1·(ρ_i/ρ_t)^(1/3)·L^0.78·U^0.44·g^(−0.22)·
   sin^(1/3)θ. K1 is a FACTOR-1.5 constant (see `law.crater/k1-gravity-rock`)."
  [{:keys [L U theta rho-i]} {:keys [rho-t g]}]
  (* law/k1-gravity-rock
     (Math/pow (/ (double rho-i) (double rho-t)) (/ 1.0 3.0))
     (Math/pow (double L) law/exponent-L-gravity)
     (Math/pow (double U) law/exponent-U-gravity)
     (Math/pow (double g) law/exponent-g-gravity)
     (Math/pow (Math/sin (double theta)) (/ 1.0 3.0))))

(defn transient-diameter-strength
  "Strength-regime transient crater diameter (m): π_V = K_s·π_3^(−μ/2)
   unfolded (Holsapple 1993). K_s is the WORST-constrained constant in the
   set, factor ~2 (see `law.crater/k-strength`)."
  [{:keys [m-i U theta]} {:keys [Y rho-t material-class]}]
  (let [mu (if (= :ice material-class) law/mu-ice law/mu-rock)]
    (* law/k-strength
       (Math/pow (/ (double m-i) (double rho-t)) (/ 1.0 3.0))
       (Math/pow (/ (double Y) (* (double rho-t) (double U) (double U)))
                 (* -0.5 mu))
       (Math/pow (Math/sin (double theta)) (/ 1.0 3.0)))))

(defn final-diameter
  "Final rim diameter (m) from transient diameter: simple craters
   D_fr = 1.25·D_tc (Grieve & Garvin 1984); complex D_fr = 1.17·
   D_tc^1.13·D_sc^(−0.13) with the Croft 1985 fit PUBLISHED IN KM —
   converted at the boundary."
  [d-tc d-sc]
  (if (< (double d-tc) (double d-sc))
    (* law/simple-final-factor (double d-tc))
    (* 1000.0 law/complex-coeff
       (Math/pow (/ (double d-tc) 1000.0) law/complex-exponent-tc)
       (Math/pow (/ (double d-sc) 1000.0) law/complex-exponent-sc))))

(defn simple-complex-transition-m
  "Simple→complex transition diameter (m): D_sc = 3200·(9.81/g) — Dence
   1965, gravity-scaled; adopted for ice too (Schenk 2002)."
  [g]
  (* law/simple-complex-D-earth-m (/ law/earth-surface-gravity (double g))))

(defn excavation-volume
  "Excavated volume (m³): V_exc = (π/80)·D_tc³ — paraboloid disposition,
   ±factor 2 (`law.crater/excavation-volume-coeff`)."
  [d-tc]
  (* law/excavation-volume-coeff (double d-tc) (double d-tc) (double d-tc)))

(defn melt-sheet-thickness
  "Melt sheet thickness (m): t_m = 4·V_melt/(π·D_tc²) — Collins et al.
   2005 eq. 31."
  [v-melt d-tc]
  (if (pos? (double d-tc))
    (/ (* 4.0 (double v-melt))
       (* Math/PI (double d-tc) (double d-tc)))
    0.0))

(defn melt-vapor-volumes
  "{:melt :vapor} volumes (m³) for `imp` into `tgt` — Collins et al. 2005
   energy scaling for rock (Pierazzo et al. 1997 showed melt follows
   ENERGY scaling, NOT the Bjorkman & Holsapple 1987 point-source
   exponent), Kraus, Senft & Stewart 2011 CTH fits for ice (U enters in
   KM/S there). Zero below the material's melt threshold. Error bars:
   melt ±factor 2; vapor ±factor 3 (see law.crater)."
  [{:keys [m-i U theta rho-i]} {:keys [material-class T rho-t]}]
  (let [sin-t (Math/sin (double theta))]
    (if (= :ice material-class)
      (if (< (double U) law/ice-melt-threshold-U)
        {:melt 0.0 :vapor 0.0}
        (let [v-p   (/ (double m-i) (double rho-i))
              t-r   (/ (double T) 273.0)
              u-km  (/ (double U) 1000.0)
              ;; The Kraus et al. 2011 vapor fit is valid only above
              ;; 8 km/s — below `law.crater/ice-vapor-threshold-U` the
              ;; vapor volume is ZERO (the melt+vapor fit still applies,
              ;; ±50% in 5–8 km/s, so melt = the combined fit there and
              ;; the floor is not undercounted).
              v-vap (if (< (double U) law/ice-vapor-threshold-U)
                      0.0
                      (* v-p law/ice-vapor-coeff
                         (+ t-r law/ice-vapor-T-offset)
                         (Math/pow u-km law/ice-vapor-U-exponent)
                         (Math/pow sin-t law/ice-vapor-angle-exponent)))
              v-mv  (* v-p law/ice-melt-coeff
                       (+ t-r law/ice-melt-T-offset)
                       (Math/pow u-km law/ice-melt-U-exponent)
                       (Math/pow sin-t law/ice-melt-angle-exponent))]
          {:vapor (double v-vap)
           :melt  (double (max 0.0 (- v-mv v-vap)))}))
      (if (< (double U) law/melt-threshold-U-rock)
        {:melt 0.0 :vapor 0.0}
        (let [energy (* 0.5 (double m-i) (double U) (double U))]
          {:melt  (* law/melt-coeff-rock energy sin-t)
           :vapor (if (>= (double U) law/vapor-threshold-U-rock)
                    (* law/vapor-efficiency
                       (/ (* energy sin-t)
                          (* (double rho-t) law/vapor-energy-silicate-J-per-kg)))
                    0.0)})))))

(defn- shock-disposition
  "Melt/vapor sub-classification (research §3): threshold velocities per
   material class."
  [U material-class]
  (let [[u-melt u-vapor] (if (= :ice material-class)
                           [law/ice-melt-threshold-U law/ice-vapor-threshold-U]
                           [law/melt-threshold-U-rock law/vapor-threshold-U-rock])]
    (cond
      (< (double U) u-melt)  :none
      (< (double U) u-vapor) :melt
      :else                  :melt+vapor)))

(defn- cratering-detail
  "The cratering branch of the decision table (research §3 branches 5–8):
   D_tc = min(D_gravity, D_strength) — whichever physics arrests crater
   growth first wins (Holsapple 1993) — then basin / strength / simple /
   complex. `band` (optional) carries {:volume :extent} of the resolved
   focus band for the basin test."
  [imp tgt q q-star]
  (let [d-g   (transient-diameter-gravity imp tgt)
        d-s   (transient-diameter-strength imp tgt)
        d-tc  (min d-g d-s)
        d-sc  (simple-complex-transition-m (:g tgt))
        d-fr  (final-diameter d-tc d-sc)
        {:keys [melt vapor]} (melt-vapor-volumes imp tgt)
        band  (:band tgt)
        basin (and (some? band)
                   (or (> melt (double (:volume band)))
                       (> d-fr (* 2.0 (double (:extent band))))))
        regime (cond
                 basin           :basin-magma-ocean
                 (< d-s d-g)     :strength-crater
                 (< d-tc d-sc)   :simple-crater
                 :else           :complex-crater)]
    {:regime        regime
     :q             (double q)
     :q-star        (double q-star)
     :d-gravity     (double d-g)
     :d-strength    (double d-s)
     :d-tc          (double d-tc)
     :d-sc          (double d-sc)
     :d-fr          (double d-fr)
     :d-exc         (* law/excavation-depth-fraction d-tc)
     :v-exc         (excavation-volume d-tc)
     :v-melt        melt
     :v-vapor       vapor
     :t-melt        (melt-sheet-thickness melt d-tc)
     :shock         (shock-disposition (:U imp) (:material-class tgt))}))

(defn classify
  "The research §3 decision table, evaluated IN ORDER (first match wins):

     imp :: {:m-i kg :L m :U m/s :theta rad-from-horizontal :rho-i kg/m³}
     tgt :: {:M-t kg :R-t m :g m/s² :rho-t kg/m³ :Y Pa :T K
             :material-class (:rock|:ice) :band? {:volume m³ :extent m}}

   Returns the classification record: `:regime` is a
   `law.crater/collision-regime-schema` keyword; cratering regimes also
   carry the full geometry/volume detail. Branches 1–2 (disruption) and
   branch 5 (basin/magma-ocean) classify but are NOT carved by this card —
   the `:voxel-carve` system reports them as
   `law.crater/disruption-report-schema`. Branch 4 (merging) is the
   collision handler's own merge path, upstream of this classifier."
  [imp tgt]
  (let [q      (/ (* 0.5 (double (:m-i imp)) (double (:U imp)) (double (:U imp)))
                  (double (:M-t tgt)))
        q-star (disruption-q-star (:R-t tgt) (:rho-t tgt) (:material-class tgt))
        q-shat (* law/disruption-shatter-fraction q-star)]
    (cond
      (>= q (* law/disruption-dispersal-factor q-star))
      {:regime :catastrophic-disruption :q (double q) :q-star (double q-star)}

      (>= q q-star)
      {:regime :disruption-marginal :q (double q) :q-star (double q-star)}

      (>= q q-shat)
      (assoc (cratering-detail imp tgt q q-star) :shattering? true)

      :else
      (cratering-detail imp tgt q q-star))))

;; --- Impactor/target derivation from the collision packet --------------------------

(defn impactor-material-class
  "Impactor material class from its bulk composition (element mass
   fractions): Fe+Ni majority → `:iron`; ice-former-dominated WITH
   hydrogen → `:ice`; else `:rock`. Nil composition defaults to `:rock`
   (the chondrite order).

   THE ICE GATE REQUIRES HYDROGEN (share >
   `law.crater/impactor-ice-hydrogen-floor`): water ice is H₂O, and
   `law.composition/ice-formers` is #{:C :N :O} — a bare-O gate
   misclassifies anhydrous silicates as ice (a chondritic/SiO₂-order
   composition is >50% O by mass, and would get the 917 kg/m³ ice density
   and a wrong impactor diameter). Carbonaceous chondrites carry a few %
   H; an O-dominated, H-free composition is rock. Used for density
   (`law.crater/impactor-density-kg-per-m3`); `:iron` targets scale with
   the rock fits."
  [composition]
  (let [total (reduce + 0.0 (map (fn [[k v]] (double v)) (sort-by key composition)))]
    (if (pos? total)
      (let [share (fn [els] (/ (reduce + 0.0 (map (fn [el] (double (get composition el 0.0)))
                                                  (sort els)))
                               total))]
        (cond
          (and (> (share comp/ice-formers) 0.5)
               (> (share #{:H}) law/impactor-ice-hydrogen-floor)) :ice
          (>= (share #{:Fe :Ni}) 0.5)                             :iron
          :else                                                   :rock))
      :rock)))

(defn- packet->classify-inputs
  "Build the classifier's `imp`/`tgt` from an absorb-merge `packet` on the
   committed world: relative velocity against the world's own velocity,
   impact angle from the surface normal at the contact point (sinθ =
   |v̂_rel·n̂|, θ measured from horizontal), impactor diameter recovered
   from mass ÷ class density, and the target's bulk figures from the
   seeded macro field + candidate record."
  [world target-eid packet field candidate band-info]
  (let [target-pos (or (ecs/get-component world target-eid c/position) [0.0 0.0 0.0])
        target-vel (or (ecs/get-component world target-eid c/velocity) [0.0 0.0 0.0])
        v-rel      (sp/v- (mapv double (:velocity packet)) (mapv double target-vel))
        U          (sp/len v-rel)
        anchor     (let [d (sp/v- (mapv double (:position packet)) (mapv double target-pos))
                         l (sp/len d)]
                     (if (> l 1.0e-12)
                       (mapv #(/ % l) d)
                       [1.0 0.0 0.0]))
        sin-t      (if (> U 1.0e-12)
                     (min 1.0 (Math/abs (/ (sp/dot v-rel anchor) U)))
                     0.0)
        theta      (Math/asin sin-t)
        imp-class  (impactor-material-class (:composition packet))
        rho-i      (double (get law/impactor-density-kg-per-m3 imp-class 3.0e3))
        m-i        (double (:mass packet))
        L          (* 2.0 (Math/cbrt (/ (* 3.0 m-i) (* 4.0 Math/PI rho-i))))
        shell      (last (:layers field))
        tgt-class  (if (= :ice-shell (:name shell)) :ice :rock)
        R-t        (double (:radius-m field))
        M-t        (double (:mass-kg field))
        g          (double (or (:surface-gravity candidate)
                               (/ (* G M-t) (* R-t R-t))))]
    {:anchor anchor
     :theta  theta
     :U      U
     :imp    {:m-i m-i :L L :U U :theta theta :rho-i rho-i}
     :tgt    {:M-t M-t :R-t R-t :g g
              :rho-t (double (:density shell))
              :Y (double (get law-int/layer-cohesion-reference-pa
                              (:name shell) 1.0e7))
              :T (double (get-in field [:thermal :surface-temperature] 288.0))
              :material-class tgt-class
              :band band-info}}))

;; --- Carve edit derivation (pure: plan + field + voxels -> ordered edits) -----------

(defn- crater-region
  "Bounding `law.voxel/region-schema` sphere of the carve volume: centred
   halfway down the excavation + melt column under the surface point,
   padded by one voxel edge."
  [anchor surface-point d-tc d-exc t-melt]
  (let [half-col (/ (+ (double d-exc) (double t-melt)) 2.0)]
    {:center (mapv double (sp/v- surface-point (sp/v* anchor half-col)))
     :radius (double (+ (Math/sqrt (+ (* (/ (double d-tc) 2.0) (/ (double d-tc) 2.0))
                                      (* half-col half-col)))
                        e))}))

(defn- melt-tag-temperature
  "Shock-melt tag temperature (K) for a voxel `:material` — ice melts at
   the H₂O point, everything else at the basaltic-liquidus reference."
  [material]
  (if (= :ice material)
    law/melt-tag-temperature-ice-k
    law/melt-tag-temperature-rock-k))

(defn- vapor-tag-temperature
  "Shock-vapor tag temperature (K) for a voxel `:material` — ice vapor is
   steam, not silicate vapor."
  [material]
  (if (= :ice material)
    law/vapor-tag-temperature-ice-k
    law/vapor-tag-temperature-rock-k))

(defn derive-edits
  "The ordered voxel edits that carve `plan` into the world: the
   excavation paraboloid (diameter `:d-tc`, depth `:d-exc`, Collins/
   Melosh geometry) is removed (`:after nil`); the innermost `:v-vapor`
   of bowl volume is tagged `:state :vapor` instead (cohesion 0 — removed
   from the SOLID field, per the vapor disposition note in this ns's
   header); the melt floor down to `:t-melt` below the excavation depth,
   capped at `:v-melt`, is tagged `:state :melt` (cohesion 0, shock-melt
   tag temperature).

   KNOWN SIMPLIFICATION — COMPLEX-CRATER RELAXATION IS NOT CARVED: in the
   `:complex-crater` regime the TRANSIENT bowl (diameter `:d-tc`, depth
   `:d-exc` = D_tc/10) is excavated verbatim; the computed `:d-fr` (Croft
   1985 conversion) is carried on the plan but IGNORED here. Design §6 /
   research §2.4 call for the bowl to then relax — widened rim D_fr,
   reduced final depth d_fr = 0.4·D_fr^0.3 (km), central uplift/terraces
   — and `law.crater/transient-depth-factor`, `complex-depth-coeff` and
   `complex-depth-exponent` are transcribed but UNUSED-PENDING that
   relaxation card. The carved result is therefore narrower and deeper
   than a real complex crater; the excavation VOLUME (π/80·D_tc³, the
   ejecta budget) is unaffected by relaxation, so mass disposition stays
   honest — only the final morphology is simple-shaped.

   `voxels` is the resolved band's voxel map; offsets it does not cover
   fall back to the regenerated seed (`domain.voxel.band/seed-voxel`), so
   the crater can reach past the band — in-band edits update the band,
   out-of-band edits append as diffs (the `domain.voxel.focus` machinery,
   design §7.3). Voxels already carved (nil in the band) are skipped for
   tagging — there is no rock there to melt.

   Sub-voxel craters no-op HONESTLY: `:d-tc` below the canonical voxel
   edge yields no edits at all, never a rounded-up one-voxel poke.
   Emission order is sorted-offset (the queue's replay-order discipline)."
  [plan field voxels]
  (if (< (double (:d-tc plan)) law/sub-voxel-diameter-m)
    []
    (let [anchor     (:anchor plan)
          R          (double (:radius-m field))
          surface    (mapv double (sp/v* anchor R))
          d-tc       (double (:d-tc plan))
          d-exc      (double (:d-exc plan))
          t-melt     (double (:melt-sheet-thickness plan))
          r-crater   (/ d-tc 2.0)
          reach      (+ (Math/sqrt (+ (* r-crater r-crater)
                                      (* (+ d-exc t-melt) (+ d-exc t-melt))))
                        e)
          [sx sy sz] surface
          offsets    (for [i (range (long (Math/floor (/ (- sx reach) e)))
                                    (inc (long (Math/floor (/ (+ sx reach) e)))))
                           j (range (long (Math/floor (/ (- sy reach) e)))
                                    (inc (long (Math/floor (/ (+ sy reach) e)))))
                           k (range (long (Math/floor (/ (- sz reach) e)))
                                    (inc (long (Math/floor (/ (+ sz reach) e)))))]
                       [i j k])
          classify-cell
          (fn [offset]
            (let [c    (band/voxel-center offset)
                  s    (sp/dot c anchor)
                  h    (- R s)
                  perp (sp/len (sp/v- c (sp/v* anchor s)))]
              (cond
                ;; inside the excavation paraboloid
                (and (<= 0.0 h d-exc)
                     (<= perp (* r-crater (Math/sqrt (max 0.0 (- 1.0 (/ h d-exc)))))))
                {:offset offset :kind :bowl :perp perp :h h}

                ;; the melt floor band below the bowl
                (and (< d-exc h (+ d-exc (max t-melt e)))
                     (<= perp r-crater))
                {:offset offset :kind :floor :perp perp :h h}

                :else nil)))
          cells      (into [] (keep classify-cell) offsets)
          bowl       (into [] (filter #(= :bowl (:kind %))) cells)
          floor      (into [] (filter #(= :floor (:kind %))) cells)
          n-vapor    (min (count bowl)
                          (long (Math/round (/ (double (:v-vapor plan)) e3))))
          vapor-set  (into #{}
                           (map :offset)
                           (take n-vapor
                                 (sort-by (fn [cell] [(:perp cell) (:h cell) (:offset cell)])
                                          bowl)))
          n-melt     (min (count floor)
                          (long (Math/round (/ (double (:v-melt plan)) e3))))
          melt-set   (into #{}
                           (map :offset)
                           (take n-melt
                                 (sort-by (fn [cell] [(:h cell) (:perp cell) (:offset cell)])
                                          floor)))
          live-voxel (fn [offset]
                       (if (contains? voxels offset)
                         (get voxels offset)
                         (band/seed-voxel field (band/voxel-center offset))))
          edits      (into {}
                           (keep (fn [{:keys [offset kind]}]
                                   (case kind
                                     :bowl
                                     (if (contains? vapor-set offset)
                                       (when-let [v (live-voxel offset)]
                                         [offset {:offset offset
                                                  :after (assoc v
                                                                :state :vapor
                                                                :cohesion 0.0
                                                                :temperature (vapor-tag-temperature (:material v)))}])
                                       [offset {:offset offset :after nil}])
                                     :floor
                                     (when (and (contains? melt-set offset)
                                                (some? (live-voxel offset)))
                                       (let [v (live-voxel offset)]
                                         [offset {:offset offset
                                                  :after (assoc v
                                                                :state :melt
                                                                :cohesion 0.0
                                                                :temperature (melt-tag-temperature (:material v)))}]))))
                                 cells))]
      (mapv #(get edits %) (sort (keys edits))))))

;; --- Cooling (design §6 step 4; research §5.4) ---------------------------------------

(defn cooling-edits
  "Re-cooling edits for every `:melt`/`:vapor` voxel in `voxels` (the
   resolved band): temperature relaxes EXPONENTIALLY toward the voxel's
   seed temperature with the conductive time constant
   `law.crater/melt-cooling-time-constant-s` (τ = e²/κ — the honest first
   model; research §5.4), and a voxel at or below its material's melt
   temperature re-solidifies: `:melt`/`:vapor` → `:solid` with its SEED
   cohesion restored (impact-melt breccia reads as intact rock at this
   resolution). Vapor condensing in place is the mass-conserving first
   model — atmospheric transport of vapor mass is the gap noted in this
   ns's header. `dt` is the simulation seconds elapsed this tick; zero or
   negative `dt` yields no edits. Ordered sorted-offset."
  [field voxels dt]
  (if (not (pos? (double dt)))
    []
    (let [decay (Math/exp (- (/ (double dt) law/melt-cooling-time-constant-s)))]
      (into []
            (keep (fn [offset]
                    (when-let [v (get voxels offset)]
                      (when (#{:melt :vapor} (:state v))
                        (let [seed (band/seed-voxel field (band/voxel-center offset))
                              t0   (double (:temperature v))
                              ts   (double (:temperature seed))
                              t'   (+ ts (* (- t0 ts) decay))]
                          (when-not (== t' t0)
                            {:offset offset
                             :after  (if (<= t' (melt-tag-temperature (:material v)))
                                       (assoc v
                                              :state :solid
                                              :temperature (double t')
                                              :cohesion (double (:cohesion seed)))
                                       (assoc v :temperature (double t')))}))))))
            (sort (keys voxels))))))

;; --- Job folds (called by the :voxel-focus fold — the single queue writer) -----------

(defn fold-plans
  "Chunk each carve plan's derived edits into budget-fitting `:apply-edits`
   jobs with provenance `:collision` (`domain.voxel.queue/edits->jobs` —
   never the oversized-head escape). Returns [] when the band is
   unresolved: the plan classifies and DROPS — the off-focus macro-field
   consequence (design §6's melt-fraction scalar / magma-ocean FSM flip)
   is the gap named in this ns's header."
  [field voxel-band plans]
  (if (or (empty? plans)
          (nil? voxel-band)
          (empty? (:voxels voxel-band)))
    []
    (into []
          (mapcat (fn [plan]
                    (queue/edits->jobs (derive-edits plan field (:voxels voxel-band))
                                       {:provenance :collision
                                        :region     (:region plan)})))
          plans)))

(defn cooling-jobs
  "Chunk this tick's cooling edits into `:apply-edits` jobs (provenance
   `:collision` — cooling is the collision's aftermath), or [] when the
   band carries no melt/vapor voxels. Enqueued every tick while melt
   persists: each tick's derivation reads the CURRENT band, so
   already-drained steps are not repeated and pending ones recompute
   idempotently."
  [field voxel-band dt]
  (let [voxels (:voxels voxel-band)]
    (if (empty? voxels)
      []
      (queue/edits->jobs (cooling-edits field voxels dt)
                         {:provenance :collision
                          :region     (get-in voxel-band [:spec :region])}))))

;; --- The :voxel-carve fan-out system --------------------------------------------------

(defn- committed-world-eid
  "The eid of the committed world, or nil — the same hard-irreversible
   marker scan `domain.voxel.focus` performs (duplicated per the
   `domain.voxel.sculpt` precedent: the scan must not become a shared
   util — it IS the commitment gate)."
  [world]
  (some (fn [[eid state]] (when (= :committed state) eid))
        (get-in world [:components c/commitment-state] {})))

(defn- band-info
  "The basin-check band record {:volume m³ :extent m} of the resolved
   band, or nil."
  [voxel-band]
  (when (and voxel-band (seq (:voxels voxel-band)))
    {:volume (* (count (:voxels voxel-band)) e3)
     :extent (* 2.0 (double (get-in voxel-band [:spec :h-r] 0.0)))}))

(defn- classify-packet
  "Classify one absorb-merge packet on the committed world into
   {:plan | :report} — nil when the packet carries no impact energy
   (a zero-relative-velocity contact is the merge path's business, not a
   carving)."
  [world target-eid packet field candidate band-info tick]
  (let [{:keys [anchor theta U imp tgt]}
        (packet->classify-inputs world target-eid packet field candidate band-info)]
    (when (pos? (double U))
      (let [result (classify imp tgt)
            regime (:regime result)]
        (if (contains? #{:strength-crater :simple-crater :complex-crater}
                       regime)
          (let [surface (mapv double (sp/v* anchor (double (:radius-m field))))
                plan    {:anchor               (mapv double anchor)
                         :theta                (double theta)
                         :regime               regime
                         :material-class       (:material-class tgt)
                         :d-tc                 (:d-tc result)
                         :d-fr                 (:d-fr result)
                         :d-exc                (:d-exc result)
                         :v-exc                (:v-exc result)
                         :v-melt               (:v-melt result)
                         :v-vapor              (:v-vapor result)
                         :melt-sheet-thickness (:t-melt result)
                         :impact-energy        (* 0.5 (double (:m-i imp)) U U)
                         :region               (crater-region anchor surface
                                                                (:d-tc result)
                                                                (:d-exc result)
                                                                (:t-melt result))
                         :tick                 (long tick)}]
            (cond->
             (when (>= (:d-tc plan) law/sub-voxel-diameter-m)
               (when-not (law/carve-plan? plan)
                 (throw (ex-info "domain.voxel.carve: plan fails law.crater/carve-plan-schema"
                                 {:plan plan})))
               {:plan plan})
              (:shattering? result)
              (assoc :report {:regime          :shattering
                              :q               (:q result)
                              :q-star          (:q-star result)
                              :impactor-mass   (double (:m-i imp))
                              :impact-velocity (double U)
                              :anchor          (mapv double anchor)
                              :note            (str "Q*_S <= Q < Q*_D: crater carved (unless "
                                                    "sub-voxel), but the target-wide cohesion "
                                                    "reset toward rubble (research §3 branch 3, "
                                                    "Benz & Asphaug 1999) is not wired — beyond "
                                                    "Voxel 5.")
                              :tick            (long tick)})))
          (let [report {:regime          regime
                        :q               (double (:q result 0.0))
                        :q-star          (double (:q-star result 0.0))
                        :impactor-mass   (double (:m-i imp))
                        :impact-velocity (double U)
                        :anchor          (mapv double anchor)
                        :note            (case regime
                                           :catastrophic-disruption
                                           (str "Q >= 2·Q*_D: planetary disruption is beyond "
                                                "Voxel 5 — the fragmentation pipeline (design §6 "
                                                "step 2d) is not wired. Classified and stopped.")
                                           :disruption-marginal
                                           (str "Q >= Q*_D: marginal disruption is beyond Voxel 5 "
                                                "— no crater carve; the rubble-pile reaccumulation "
                                                "pipeline is not wired. Classified and stopped.")
                                           :basin-magma-ocean
                                           (str "Melt exceeds the resolved band / final rim exceeds "
                                                "2× band extent: the band-wide :env/magma-ocean FSM "
                                                "transition and the off-focus macro-field "
                                                "melt-fraction scalar (design §6) are not wired. "
                                                "Classified and stopped.")
                                           (str "Non-cratering regime " regime
                                                ": classified and stopped."))
                        :tick            (long tick)}]
            (when-not (law/disruption-report? report)
              (throw (ex-info "domain.voxel.carve: report fails law.crater/disruption-report-schema"
                              {:report report})))
            {:report report}))))))

(defn carve-system
  "The `:voxel-carve` write-set system (double-buffer fan-out). SOLE
   writer of `c/voxel-carve-request` on the committed world: classifies
   every absorb-merge packet it has not seen before (the packet channel
   is sticky — see this ns's header) into a `law.crater` carve plan (or
   disruption report), persisting `:seen` for idempotency. Reads its own
   prior output one Jacobi tick stale (the `domain.voxel.sculpt`
   precedent). The `:voxel-focus` fold consumes `:plans` one tick later;
   `:disruptions` are the sub-catastrophic stop — data, not edits.
   Emits NOTHING when no world is committed, when the committed world
   carries no candidate/field to classify against, or when no collision
   has occurred (empty plans, empty reports, unchanged seen — the
   'no collision event -> no work' rule)."
  []
  {:id     :voxel-carve
   :ns     'domain.voxel.carve
   :writes (reg/registry-writes :voxel-carve)
   :run
   (fn [world]
     (if-let [eid (committed-world-eid world)]
       (let [candidate (ecs/get-component world eid c/planet-candidate)
             field0    (ecs/get-component world eid c/voxel-field)
             field     (or field0 (when candidate (interior/seed-field candidate)))]
         (if (nil? field)
           {}
           (let [prior    (ecs/get-component world eid c/voxel-carve-request)
                 seen0    (set (:seen prior))
                 packets  (or (ecs/get-component world eid c/absorb-merge) [])
                 fresh    (into [] (remove #(contains? seen0 %)) packets)
                 tick     (long (or (:tick world) 0))
                 binf     (band-info (ecs/get-component world eid c/voxel-band))
                 outcomes (into [] (keep #(classify-packet world eid % field candidate binf tick))
                                fresh)
                 plans    (into [] (keep :plan) outcomes)
                 reports  (into []
                                (comp (keep :report)
                                      (distinct))
                                outcomes)
                 ;; Reports ACCUMULATE: a disruption report is the
                 ;; sub-catastrophic stop — wiping it one tick later would
                 ;; make the stop unobservable. Bounded by the collision
                 ;; count on the committed world (rare).
                 reports' (into [] (distinct (concat (:disruptions prior) reports)))
                 seen'    (into seen0 fresh)]
             (if (and (empty? plans) (empty? reports') (= seen' seen0) (nil? prior))
               {}
               {c/voxel-carve-request
                {eid (let [request {:plans       plans
                                    :disruptions reports'
                                    :seen        seen'}]
                       (when-not (law/carve-request? request)
                         (throw (ex-info "domain.voxel.carve: request fails law.crater/carve-request-schema"
                                         {:request request})))
                       request)}}))))
       {}))})
