(ns domain.stellar.classifier.planet
  "M5 handoff Phases 1-3: material/thermal classification, orbit stability, and
   atmosphere retention — plus the `:classification` write-set system that folds
   all three into one fan-out emitter.

   Split out of the former `domain.stellar.classifier` on 2026-07-24 (see
   `kanban/tasks/static-analysis-split-classifier.md`). Pure classification from
   composition/mass and two-body equilibrium temperature; no orbit integration.
   `domain.stellar.classifier.candidate` reads from here; nothing here reads
   from it."
  (:require
   [clojure.math             :as math]
   [law.stellar              :as law]
   [law.atmosphere           :as atmosphere]
   [domain.chemistry         :as chemistry]
   [domain.ecs.core          :as ecs]
   [domain.ecs.components    :as c]
   [domain.orbital.stability :as stability]
   [shape.spatial            :as sp]))

;; --- M5 handoff Phase 1: material + thermal classification ------------------
;; See kanban/tasks/ecology-m5-phase1-planet-classification.md and parent
;; kanban/tasks/ecology-water-gate-snowline.md §3.1-3.2. Pure classification
;; from composition/mass and two-body equilibrium temperature only — no orbit
;; integration, no atmosphere physics. This is the real material/thermal gate
;; that replaces the trivially-satisfied `habitability-score > 0.2` scalar.

(def ^:const rocky-max-mass
  "Upper mass bound (kg) for the :rocky material class (parent §3.1)." 1.0e25)

(def ^:const icy-max-mass
  "Upper mass bound (kg) for the :icy material class (parent §3.1)." 5.0e25)

(def ^:const gas-giant-min-mass
  "Lower mass bound (kg) for the :gaseous material class (parent §3.1)." 1.0e25)

(defn material-class
  "Bulk material class of a body from its element-resolved `composition` map
   (domain.chemistry/bulk-categories at `temperature`), plus `mass` (kg), per
   parent §3.1:

     :rocky    metal+rock > 50%, H+He < 25%, mass < 1e25 kg
     :icy      ice/volatiles > 50%, mass < 5e25 kg
     :gaseous  H+He > 50%, mass > 1e25 kg
     :mixed    none of the above strongly

   Uses the DERIVED bulk categories (metal/rock/ice fractions from Lodders
   condensation temperatures), never a stored `:metals` key — composition is
   the real element map (H, He, O, Si, Fe, ...)."
  [composition mass temperature]
  (let [m (double mass)
        {:keys [rock metal ice]} (chemistry/bulk-categories composition temperature)
        rock-metal (+ (double rock) (double metal))
        h-he (+ (double (get composition :H 0.0))
                (double (get composition :He 0.0)))]
    (cond
      (and (> h-he 0.5) (> m gas-giant-min-mass))            :gaseous
      (and (> rock-metal 0.5) (< h-he 0.25) (< m rocky-max-mass)) :rocky
      (and (> (double ice) 0.5) (< m icy-max-mass))           :icy
      :else                                                   :mixed)))

(def material-albedo
  "Coarse Bond albedo by material class — ice and cloud tops reflect more
   sunlight than bare rock/metal. A single rough number per class, not a
   wavelength-resolved model (parent §3.2). Public: reused by the M5 Phase 4
   handoff gate (`eligible-candidate?`/`build-candidate-record`) so the
   equilibrium temperature used to admit a `:planet-candidate` is computed
   with the exact same albedo `classify-body-equilibrium-temp` already
   uses — one source of truth, not a re-derived constant."
  {:rocky   0.3
   :icy     0.5
   :gaseous 0.5
   :mixed   0.3})

(defn equilibrium-temperature
  "Two-body radiative equilibrium temperature (K):
     T_eff = (L (1 - A) / (16 π σ a²))^0.25
   for a star of luminosity `L` (W), orbital separation `a` (m), and Bond
   albedo `albedo` (parent §3.2)."
  [L a albedo]
  (let [l (double L) aa (double a) alb (double albedo)]
    (math/pow (/ (* l (- 1.0 alb))
                 (* 16.0 math/PI law/stefan-boltzmann aa aa))
              0.25)))

(defn thermal-band
  "Coarse thermal band for a body of material class `mclass` at orbital
   separation `a` (m) from a star of luminosity `L` (W). Computes the two-body
   equilibrium temperature (`equilibrium-temperature`) with a coarse
   composition-based Bond albedo, then buckets it per parent §3.2:
     :frozen < 150 K, :cold 150-250 K, :temperate 250-350 K,
     :warm 350-450 K, :hot > 450 K."
  [L a mclass]
  (let [albedo (get material-albedo mclass 0.3)
        t-eff (equilibrium-temperature L a albedo)]
    (cond
      (< t-eff 150.0) :frozen
      (< t-eff 250.0) :cold
      (< t-eff 350.0) :temperate
      (< t-eff 450.0) :warm
      :else            :hot)))

(def ^:private non-classifiable-states
  "Matter states that are not planet-candidate bodies and are excluded from
   material/thermal classification: diffuse gas and the central star itself."
  #{:nebula :star :protostar :stellar-remnant})

(defn- star-record
  "The `{:id :matter-state :position :velocity :mass :radius :luminosity}`
   map for one star entity, or nil when data is incomplete."
  [world eid]
  (when-let [pos (ecs/get-component world eid c/position)]
    (when-let [mass (ecs/get-component world eid c/mass)]
      {:id         eid
       :matter-state (ecs/get-component world eid c/matter-state)
       :position   pos
       :velocity   (or (ecs/get-component world eid c/velocity) [0.0 0.0 0.0])
       :mass       (double mass)
       :radius     (double (or (ecs/get-component world eid c/radius) 0.0))
       :luminosity (double (or (ecs/get-component world eid c/luminosity) 0.0))})))

(defn stellar-bodies
  "Every `:star`/`:protostar` entity in `world` (the candidate parent
   population), as eids. One scan per tick, shared by all per-body parent
   lookups — never a per-body re-scan."
  [world]
  (filterv #(contains? #{:star :protostar}
                       (ecs/get-component world % c/matter-state))
           (ecs/entities-with world c/matter-state c/mass)))

(defn central-star
  "The most massive :star or :protostar in `world`, as
   `{:id :matter-state :position :velocity :mass :radius :luminosity}`, or
   nil if none exists yet. Mirrors `domain.stellar.disc/disc-identification-
   system`'s central-body lookup. Mass/radius/velocity feed the M5 Phase 2
   orbit-stability proxy (`domain.orbital.stability/orbit-stability`)
   alongside the luminosity Phase 1 already reads for thermal-band; `:id`
   and `:matter-state` feed the M5 Phase 4 handoff gate
   (`domain.stellar.classifier.candidate/handoff-system`), which needs to know the
   star's own entity id (for `:planet-candidate`'s `:star-id`) and whether
   it has actually reached `:star` (not merely `:protostar`).

   NOTE (multi-timescale card 4): this is the SYSTEM-level primary only —
   retained for the system-level handoff criterion (a :star exists) and
   legacy callers. Per-body orbit/thermal/eligibility evaluation uses
   `dominant-attractor`: in a multi-star field a planet is governed by its
   NEAREST BOUND star, not the biggest star across the cloud."
  [world]
  (let [candidates (stellar-bodies world)]
    (when (seq candidates)
      (let [eid (apply max-key #(ecs/get-component world % c/mass) candidates)]
        (star-record world eid)))))

(defn dominant-attractor
  "The star `eid` is governed by: the `:star`/`:protostar` with the lowest
   bound two-body specific energy relative to the body (ε = u²/2 − μ/r < 0,
   unsoftened μ — the velocity pairing rule, design §3.5: classified planets
   are the sub-stepped population, which lives under the integrator's exact
   Newtonian drift). Ties broken by distance. Returns the `star-record` map,
   or nil when the body is bound to no star (a hyperbolic interloper is
   legitimately not a planet-candidate — multi-timescale card 4)."
  [world eid stars]
  (when-let [pos (ecs/get-component world eid c/position)]
    (let [vel (or (ecs/get-component world eid c/velocity) [0.0 0.0 0.0])]
      (->> stars
           (remove #(= % eid))
           (keep #(star-record world %))
           (keep (fn [star]
                   (let [r-vec (sp/v- pos (:position star))
                         v-vec (sp/v- vel (:velocity star))
                         r (sp/len r-vec)
                         mu (* law/G (:mass star))]
                     (when (pos? r)
                       (let [energy (- (/ (sp/len2 v-vec) 2.0) (/ mu r))]
                         (when (neg? energy)
                           {:star star :energy energy :r r}))))))
           (sort-by (juxt :energy :r))
           first
           :star))))

(defn- classify-body-material
  [world eid]
  (when-let [composition (ecs/get-component world eid c/composition)]
    (when-let [mass (ecs/get-component world eid c/mass)]
      (let [temperature (double (or (ecs/get-component world eid c/temperature) 0.0))]
        (material-class composition mass temperature)))))

(defn- classify-body-thermal
  [world star eid mclass]
  (when star
    (when-let [pos (ecs/get-component world eid c/position)]
      (when-let [star-pos (:position star)]
        (let [a (sp/dist pos star-pos)]
          (when (pos? a)
            (thermal-band (:luminosity star) a mclass)))))))

;; --- M5 handoff Phase 2: orbit stability (analytic proxy) --------------------
;; See kanban/tasks/ecology-m5-phase2-orbit-stability.md and parent
;; kanban/tasks/ecology-water-gate-snowline.md §3.3. Folded into
;; `classification-system` rather than a separate `:stability` system: it needs
;; the exact same candidate-body scan and the same central-star lookup this
;; system already does for material/thermal classification, so extending the
;; one write-set keeps reads minimal and `reg/write-conflicts` empty without a
;; second fan-out emitter duplicating the scan.

(defn- candidate-snapshot
  "`{:position :mass}` for a candidate body, or nil if either is missing."
  [world eid]
  (when-let [pos (ecs/get-component world eid c/position)]
    (when-let [mass (ecs/get-component world eid c/mass)]
      {:position pos :mass mass})))

(defn- classify-body-stability
  "Run the analytic orbit-stability proxy for one candidate against its
   dominant-attractor `star` (per-body parent, card 4) and every OTHER
   candidate in `candidates` (a map of eid -> `{:position :mass}`). nil
   (omitted from the write-set) when the star or this body's own
   velocity/mass/position are not yet resolvable."
  [world star eid candidates]
  (when star
    (when-let [pos (ecs/get-component world eid c/position)]
      (when-let [mass (ecs/get-component world eid c/mass)]
        (let [vel (or (ecs/get-component world eid c/velocity) [0.0 0.0 0.0])
              others (keep (fn [[oid data]] (when (not= oid eid) data)) candidates)]
          (stability/orbit-stability {:position pos :velocity vel :mass mass}
                                     star others))))))

;; --- M5 handoff Phase 3: atmosphere retention --------------------------------
;; See kanban/tasks/ecology-m5-phase3-atmosphere-retention.md, parent
;; kanban/tasks/ecology-water-gate-snowline.md §4, and the grounding research
;; note docs/research/atmosphere/planetary-atmosphere-retention-classifier.md
;; (which supersedes the parent card's rougher formulas — most-probable-speed
;; v_th and the literal moon-like test — where they conflict; see that note's
;; §3.4 and §6.1). Folded into `classification-system` for the same reason
;; Phase 2 was: it is a pure downstream consumer of material-class/thermal-
;; band computed in the very same fan-out, so extending one write-set keeps
;; `reg/write-conflicts` empty without a second emitter re-scanning candidates.

(defn- candidate-species
  "Chemically plausible atmospheric volatiles for a body of material class
   `mclass` in thermal band `tband` (research note §3.3): gaseous bodies retain
   a primordial H2/He envelope; rocky/icy/mixed bodies are gated to secondary
   volatiles (N2, CO2), with H2O added only when the thermal band is warm enough
   that water is not locked up as surface/subsurface ice (:temperate/:warm/:hot;
   the same snowline boundary Phase 1 already uses for thermal-band)."
  [mclass tband]
  (if (= mclass :gaseous)
    #{:H2 :He}
    (cond-> #{:N2 :CO2}
      (contains? #{:temperate :warm :hot} tband) (conj :H2O))))

(defn- representative-species-mass
  "Single dominant-species molecular mass (kg) used for the overall
   atmosphere-class bucket (research note §4.2): the H/He mean mass for
   gaseous bodies, otherwise CO2 for :hot bodies (Venus-like, secondary CO2
   atmosphere dominant) or N2 for cooler bodies (Earth/Titan-like default)."
  [mclass tband]
  (if (= mclass :gaseous)
    atmosphere/h2-he-mean-mass
    (if (= tband :hot)
      (:CO2 atmosphere/species-mass)
      (:N2 atmosphere/species-mass))))

(defn- species-retention-threshold
  "Retention-ratio threshold for `species`: the higher H2/He bar (early-XUV
   exposure) or the lower heavy-secondary-volatile bar (research note §3.4)."
  [species]
  (if (contains? #{:H2 :He} species)
    atmosphere/h-he-retention-ratio
    atmosphere/heavy-retention-ratio))

(defn- atmosphere-bucket
  "Bucket a representative retention ratio into a coarse atmosphere-class
   (research note §3.4): `:none` r<3, `:thin` 3-6, `:substantial` 6-10,
   `:thick` r>=10."
  [ratio]
  (cond
    (< ratio atmosphere/thin-ratio-floor)         :none
    (< ratio atmosphere/substantial-ratio-floor)  :thin
    (< ratio atmosphere/thick-ratio-floor)        :substantial
    :else                                         :thick))

(defn atmosphere-class
  "Coarse Phase-0 atmosphere-retention classifier (M5 handoff Phase 3), pure
   function of quantities already resolved by handoff time:

     `{:mass M :radius R :temperature T :material-class mc :thermal-band tb}`
     => `{:atmosphere-class :none|:thin|:substantial|:thick
          :retained-species #{:H2 :He :H2O :N2 :CO2}}`

   Uses the classical Jeans escape-parameter ratio
   `r = v_esc/v_th = sqrt(2GM/R) / sqrt(3 k_B T / m)` (RMS thermal speed,
   `law.atmosphere/retention-ratio` — the same v_th convention as
   `domain.chemistry/can-retain-gas?`, see that fn's docstring and the
   research note §3.4 for the reconciliation). The composition gate
   (`candidate-species`) runs FIRST: a species must be chemically plausible
   for this material-class/thermal-band before its retention ratio is even
   checked, so a volatile-poor rocky body cannot be credited with a thick
   CO2 atmosphere just because its gravity is high enough in principle.

   This is a one-shot formation-time verdict against THERMAL escape only —
   it does not model non-thermal loss (solar-wind sputtering, no-
   magnetosphere pickup), which is what actually strips real ambiguous
   bodies like the Moon or Mercury (research note §6.1, §8.2); do not read
   `:thin`/`:substantial` as \"confirmed has a bound atmosphere.\""
  [{mclass :material-class tband :thermal-band :keys [mass radius temperature]}]
  (let [candidates (candidate-species mclass tband)
        retained   (into #{}
                         (filter #(> (atmosphere/retention-ratio
                                      mass radius temperature
                                      (get atmosphere/species-mass %))
                                     (species-retention-threshold %)))
                         candidates)
        mu         (representative-species-mass mclass tband)
        ratio      (atmosphere/retention-ratio mass radius temperature mu)]
    {:atmosphere-class (atmosphere-bucket ratio)
     :retained-species retained}))

(defn classify-body-equilibrium-temp
  "Two-body equilibrium temperature (K) for a candidate body, mirroring
   `classify-body-thermal` but returning the raw temperature instead of its
   bucketed thermal-band — the input `atmosphere-class` needs (research note
   §4.2), not the coarse label. nil when the star or this body's position
   is not yet resolvable. Public: also the M5 Phase 4 handoff gate's source
   for the §2 planet-candidate table's 150-400 K temperature test."
  [world star eid mclass]
  (when star
    (when-let [pos (ecs/get-component world eid c/position)]
      (when-let [star-pos (:position star)]
        (let [a (sp/dist pos star-pos)]
          (when (pos? a)
            (equilibrium-temperature (:luminosity star) a
                                     (get material-albedo mclass 0.3))))))))

(defn- classify-body-atmosphere
  "Run `atmosphere-class` for one candidate body, or nil (omitted from the
   write-set) when mass/radius/material-class/thermal-band/temperature are
   not all resolvable yet."
  [world star eid mclass tband]
  (when (and mclass tband)
    (when-let [mass (ecs/get-component world eid c/mass)]
      (when-let [radius (ecs/get-component world eid c/radius)]
        (when-let [t-eff (classify-body-equilibrium-temp world star eid mclass)]
          (atmosphere-class {:mass mass :radius radius :temperature t-eff
                             :material-class mclass :thermal-band tband}))))))

(defn classification-system
  "Double-buffer write-set system: SOLE writer of `c/material-class`,
   `c/thermal-band`, `c/orbit-stable`, `c/atmosphere-class`, AND
   `c/retained-species` (M5 handoff Phases 1-3). Jacobi fan-out emitter —
   reads the frozen snapshot only, writes all five component types for every
   planet-candidate body (any matter-state other than nebula/protostar/
   star/stellar-remnant) that has composition, mass, and a resolvable
   position relative to the central star. Orbit stability is an ANALYTIC
   PROXY (`domain.orbital.stability/orbit-stability`) — periapsis/apoapsis
   bounds plus Hill-radius separation from sibling candidates — NOT a 10 Myr
   two-body integration. Atmosphere retention (`atmosphere-class`) is a
   one-shot Jeans-escape-ratio verdict, not an ongoing mass-loss simulation
   (that is `domain.atmosphere`'s xuv-atmospheric-escape-system) — see that
   fn's docstring. Bodies missing required data (or with no central star
   yet) are simply omitted from the write-set this tick, not defaulted."
  []
  {:id     :classification
   :writes #{c/material-class c/thermal-band c/orbit-stable
             c/atmosphere-class c/retained-species}
   :reads  #{c/matter-state c/mass c/composition c/temperature c/position
             c/velocity c/radius c/luminosity}
   :run
   (fn [world]
     (let [stars (stellar-bodies world)
           eids (filterv #(not (contains? non-classifiable-states
                                          (ecs/get-component world % c/matter-state)))
                         (ecs/entities-with world c/matter-state c/mass))
           ;; Multi-timescale card 4: every per-body verdict (thermal band,
           ;; orbit stability, equilibrium temperature) is evaluated against
           ;; the body's OWN dominant attractor, not the system-primary
           ;; `central-star` — in a multi-star field the primary is the wrong
           ;; parent for most bodies.
           body-parents (into {} (keep (fn [eid]
                                         (when-let [p (dominant-attractor world eid stars)]
                                           [eid p])))
                              eids)
           materials (into {} (keep (fn [eid]
                                      (when-let [mclass (classify-body-material world eid)]
                                        [eid mclass])))
                           eids)
           thermals (into {} (keep (fn [eid]
                                     (when-let [band (classify-body-thermal
                                                      world (get body-parents eid) eid
                                                      (get materials eid :mixed))]
                                       [eid band])))
                          eids)
           ;; The Hill close-approach test (orbit-stability gate 3) must see only
           ;; genuine co-orbiting sibling candidates, i.e. bodies BOUND to a star
           ;; (`body-parents` = those with a dominant-attractor). An UNBOUND body
           ;; — a hyperbolic ejected planet or brown-dwarf flung to 10^5 AU —
           ;; carries a Hill radius ∝ its orbital distance (R_H = a·(m/3M)^⅓), so
           ;; at 1.4×10^5 AU it spans ~5×10^4 AU and its 10-R_H exclusion zone
           ;; (~5×10^5 AU) spuriously "overlaps" every real inner planet, forcing
           ;; c/orbit-stable false for the whole system. Such a body is not a
           ;; co-orbiting sibling and cannot threaten a bound orbit; exclude it.
           candidates (into {} (keep (fn [eid]
                                       (when (contains? body-parents eid)
                                         (when-let [snap (candidate-snapshot world eid)]
                                           [eid snap]))))
                            eids)
           stabilities (into {} (keep (fn [eid]
                                        (when-some [ok (classify-body-stability
                                                        world (get body-parents eid) eid candidates)]
                                          [eid ok])))
                             eids)
           atmospheres (into {} (keep (fn [eid]
                                        (when-let [verdict (classify-body-atmosphere
                                                            world (get body-parents eid) eid
                                                            (get materials eid)
                                                            (get thermals eid))]
                                          [eid verdict])))
                             eids)
           atmosphere-classes (into {} (keep (fn [[eid v]] [eid (:atmosphere-class v)])) atmospheres)
           retained-species-map (into {} (keep (fn [[eid v]] [eid (:retained-species v)])) atmospheres)]
       {c/material-class materials
        c/thermal-band   thermals
        c/orbit-stable   stabilities
        c/atmosphere-class atmosphere-classes
        c/retained-species retained-species-map}))})
