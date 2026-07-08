(ns domain.stellar.fusion
  "Fusion promotion, stellar luminosity, SED bands, atmosphere shells, and
   deuterium depletion. Owns the c/promotion-signal, c/luminosity, c/sed-bands,
   c/atmosphere-shells, and c/comp-depletion columns."
  (:require
   [clojure.math :as math] [law.stellar                   :as law]
   [law.sed                       :as lsed]
   [domain.ecs.core               :as ecs]
   [domain.ecs.components         :as c]
   [domain.ecs.registry           :as reg]
   [domain.ecs.tick               :as tick]
   [domain.profile                :as profile]
   [domain.stellar.thermodynamics :as thermo]))

;; --- Deuterium depletion (Phase 0) ------------------------------------------
;; D is the most fragile isotope — destroyed at T > 10⁶ K, well below fusion
;; temperatures. Every star that forms destroys its D. This is a ONE-WAY gate:
;; D never re-appears once destroyed. Sub-stellar bodies retain primordial D.
;; Source: docs/research/cosmology/primordial-nucleosynthesis-yields.md §8

(def ^:const deuterium-destruction-temp
  "Temperature (K) above which deuterium is destroyed. 10⁶ K — well below
   fusion ignition (10⁷ K) but above any planetary/stellar photosphere."
  1.0e6)

(defn deuterium-depletion-system
  "Write-set emitter: sole writer of :component/comp.depletion — the set of
   composition keys to zero for any body whose temperature exceeds
   deuterium-destruction-temp (just :D). One-way gate; the integrator owns
   :component/composition and applies the burn (comp.burn) then this depletion
   (spec §7.5). A pure snapshot-reading fan-out emitter (no longer a serial
   barrier). Auto-clears the influence when a body cools back below the gate
   (harmless: D is already gone)."
  []
  {:id     :deuterium-depletion
   :writes #{c/comp-depletion}
   :run    (fn [world]
             (let [eids (ecs/entities-with world c/matter-state c/temperature c/composition)
                   cell (into {}
                              (keep (fn [eid]
                                      (let [T    (double (or (ecs/get-component world eid c/temperature) 0.0))
                                            composition (ecs/get-component world eid c/composition)]
                                        (when (and (> T deuterium-destruction-temp)
                                                   (pos? (double (:D composition 0.0))))
                                          [eid #{:D}]))))
                              eids)]
               (tick/contribution-write-set
                c/comp-depletion cell
                (keys (get-in world [:components c/comp-depletion])))))})

;; --- Fusion promotion and luminosity ----------------------------------------

(defn fusion-system
  "Double-buffer write-set system: SOLE writer of c/luminosity.

   Reads fusion-promotion's one-tick-stale c/promotion-signal and applies its
   :luminosity value. Falls back to computing luminosity from scratch when
   there is no signal (initial ignition before a signal has propagated). Emits
   only the luminosities that CHANGED; a body whose fusion has ceased keeps its
   stale luminosity (never removed — same as the legacy path)."
  []
  {:id     :fusion
   :writes (reg/registry-writes :fusion)
   :run
   (fn [world]
     (profile/profile-sections
      world
      [[:fusion/scan
        (fn [w]
          {:promotions (get-in w [:components c/promotion-signal] {})
           :eids       (ecs/entities-with w c/matter-state c/temperature c/pressure c/composition)})]
       [:fusion/burn
        (fn [{:keys [promotions eids]}]
          {c/luminosity
           (into {}
                 (keep (fn [eid]
                         (let [region (thermo/entity->region world eid)
                               sig    (get promotions eid)
                               lum    (if sig
                                        (:luminosity sig)
                                        (when (law/fusion-possible? region)
                                          (thermo/star-luminosity region)))]
                           (when (and lum
                                      (not= lum (ecs/get-component world eid c/luminosity)))
                             [eid lum]))))
                 eids)})]]))})

(defn- promotion-signal
  "Return the promotion-signal map for `eid`, or nil if none is needed."
  [world eid]
  (let [state (ecs/get-component world eid c/matter-state)
        region (thermo/entity->region world eid)]
    ;; Intentional: protostars that ignite and existing stars with stale zero
    ;; luminosity both emit the same promotion signal; the identical branches are
    ;; the correct unified behaviour, not duplication.
    #_{:splint/disable [lint/identical-branches]}
    (cond
      (and (= :protostar state) (law/fusion-possible? region))
      {:promotion :star
       :luminosity (thermo/star-luminosity region)}

      (and (= :star state) (law/fusion-possible? region)
           (let [lum (double (or (ecs/get-component world eid c/luminosity) 0.0))]
             (zero? lum)))
      {:promotion :star
       :luminosity (thermo/star-luminosity region)}

      :else nil)))

(defn fusion-promotion-system
  "Double-buffer write-set system: SOLE writer of c/promotion-signal — emits a
   signal for protostars that now meet fusion conditions (and for stars with
   stale zero luminosity).

   Instead of directly writing c/matter-state and c/luminosity (conflicting with
   classifier and fusion respectively — spec §7), it emits a signal that both
   systems read on the NEXT tick's frozen snapshot. The one-tick latency is
   accepted (§2). Signals not re-emitted this tick are cleared with the
   `removed` sentinel (single owner clears its own staleness).

   0-arity returns the native write-set system for the fan-out; 1-arity applies
   the emitted write-set to `world` and returns the updated world — a
   convenience for benches, tests, and REPL use."
  ([]
   {:id     :fusion-promotion
    :writes (reg/registry-writes :fusion-promotion)
    :run
    (fn [world]
      (profile/profile-sections
       world
       [[:fusion-promotion/scan
         (fn [w]
           {:prior (keys (get-in w [:components c/promotion-signal] {}))
            :eids  (ecs/entities-with w c/matter-state c/temperature c/pressure
                                      c/composition c/density c/radius c/mass)})]
        [:fusion-promotion/evaluate
         (fn [{:keys [prior eids]}]
           {:prior prior
            :signals (into {}
                           (keep (fn [eid]
                                   (when-let [sig (promotion-signal world eid)]
                                     [eid sig])))
                           eids)})]
        [:fusion-promotion/write-set
         (fn [{:keys [prior signals]}]
           (tick/contribution-write-set c/promotion-signal signals prior))]]))})
  ([world] (tick/apply-write-set world ((:run (fusion-promotion-system)) world))))

;; --- Panchromatic SED (Phase 1) ---------------------------------------------
;; Stars emit radiation across the full EM spectrum. The SED shape is set by
;; T_eff and log g. A scalar bolometric luminosity obscures band-dependent
;; effects: XUV drives atmospheric escape, FUV/NUV affect photochemistry,
;; IR regulates climate. This system computes per-band luminosities from
;; pre-tabulated spectral templates (law.sed/spectral-templates).
;; Source: docs/research/phase1-radiation-plasma-truth.md §2

(defn stellar-sed-system
  "Double-buffer write-set system: SOLE writer of c/sed-bands for :star entities.
   Computes T_eff from L and R (Stefan-Boltzmann), selects the nearest spectral
   template from law.sed, and scales band fractions by L_bol.

   Precondition: entity is :star with positive luminosity and radius.
   Postcondition: c/sed-bands is a map of band-keyword → Watts, summing to L_bol.
   When c/flare-boost is active (tick < decay-tick), XUV bands are multiplied
   by the boost factor. This models transient XUV enhancement from stellar flares."
  []
  {:id     :stellar-sed
   :writes #{c/sed-bands}
   :run    (fn [world]
             (let [tick  (or (:tick world) 0)
                   eids  (ecs/entities-with world c/matter-state c/luminosity c/radius)
                   stars (filterv #(= :star (ecs/get-component world % c/matter-state)) eids)
                   bands (into {}
                               (keep (fn [eid]
                                       (let [L    (double (or (ecs/get-component world eid c/luminosity) 0.0))
                                             R    (double (or (ecs/get-component world eid c/radius) 0.0))
                                             teff (thermo/effective-temperature L R)
                                             logg (if (pos? R)
                                                    (math/log10 (/ (* law/G (double (ecs/get-component world eid c/mass)) 1000.0)
                                                                   (* R R)))
                                                    4.5)]
                                         (when (pos? L)
                                           (let [base-bands (lsed/select-sed-bands teff logg L)
                                                 ;; Apply flare XUV boost if active
                                                 boost (ecs/get-component world eid c/flare-boost)]
                                             (if (and boost (< tick (long (:decay-tick boost 0))))
                                               (let [f (double (:factor boost 1.0))]
                                                 (-> base-bands
                                                     (update :xray #(* (double %) f))
                                                     (update :euv  #(* (double %) f))))
                                               base-bands))))))
                               stars)]
               {c/sed-bands bands}))})

;; --- Stellar atmosphere shells (Phase 1) ------------------------------------
;; Real stars have stratified atmospheres: photosphere, chromosphere, transition
;; region, corona. Each layer has distinct temperature, density, ionization, and
;; magnetic field. The corona is the source of XUV radiation and stellar winds.
;; This system derives a 4-layer profile from T_eff, log g, and B-field.
;; Source: docs/research/phase1-radiation-plasma-truth.md §3

(defn- atmosphere-from-teff
  "Build a 4-layer atmosphere profile from stellar parameters.
   Returns a vector of shell maps ordered photosphere → corona."
  [teff logg b-field]
  (let [;; Photosphere: T ≈ T_eff, dense, partially ionized
        photosphere {:layer/id            :photosphere
                     :temperature         teff
                     :electron-density    (* 1.0e17 (math/pow (/ teff 5800.0) 2.5))
                     :ionization-fraction (min 1.0 (max 0.01 (/ teff 1.0e4)))
                     :b-field             (or b-field [0.0 0.0 0.0])
                     :height              0.0}
        ;; Chromosphere: T ~ 10^4 K, rising temperature, strong emission lines
        chromosphere {:layer/id            :chromosphere
                      :temperature         (max teff 1.0e4)
                      :electron-density    (* 1.0e16 (math/pow (/ teff 5800.0) 1.5))
                      :ionization-fraction (min 1.0 (max 0.1 (/ teff 6.0e3)))
                      :b-field             (or b-field [0.0 0.0 0.0])
                      :height             (* 5.0e5 (max 1.0 (/ 4.5 logg)))}
        ;; Transition region: steep T gradient, high ionization
        transition {:layer/id            :transition
                    :temperature         (max (* 2.0 teff) 1.0e5)
                    :electron-density    (* 1.0e14 (math/pow (/ teff 5800.0) 0.5))
                    :ionization-fraction 0.9
                    :b-field             (or b-field [0.0 0.0 0.0])
                    :height             (* 2.0e6 (max 1.0 (/ 4.5 logg)))}
        ;; Corona: hot (1-3 × 10^6 K), low density, fully ionized, XUV source
        ;; Corona temperature scales with stellar activity (hotter stars → hotter corona)
        corona-t  (min 3.0e7 (max 1.0e6 (* 200.0 teff)))
        corona    {:layer/id            :corona
                   :temperature         corona-t
                   :electron-density    (* 1.0e12 (math/pow (/ teff 5800.0) 0.3))
                   :ionization-fraction 1.0
                   :b-field             (or b-field [0.0 0.0 0.0])
                   :height             (* 1.0e8 (max 1.0 (/ 4.5 logg)))}]
    [photosphere chromosphere transition corona]))

(defn atmosphere-shells-system
  "Double-buffer write-set system: SOLE writer of c/atmosphere-shells for :star
   entities. Derives a four-layer atmosphere profile (photosphere, chromosphere,
   transition, corona) from T_eff, log g, and magnetic field.

   Precondition: entity is :star with positive luminosity and radius.
   Postcondition: c/atmosphere-shells is a vector of 4 shell maps, each with
   :layer/id, :temperature, :electron-density, :ionization-fraction, :b-field, :height."
  []
  {:id     :atmosphere-shells
   :writes #{c/atmosphere-shells}
   :run    (fn [world]
             (let [eids (ecs/entities-with world c/matter-state c/luminosity c/radius c/mass)
                   stars (filterv #(= :star (ecs/get-component world % c/matter-state)) eids)
                   shells (into {}
                                (keep (fn [eid]
                                        (let [L     (double (or (ecs/get-component world eid c/luminosity) 0.0))
                                              R     (double (or (ecs/get-component world eid c/radius) 0.0))
                                              M     (double (or (ecs/get-component world eid c/mass) 0.0))
                                              B     (ecs/get-component world eid c/b-field)
                                              teff  (thermo/effective-temperature L R)
                                              logg  (if (pos? R)
                                                      (math/log10 (/ (* law/G M 1000.0) (* R R)))
                                                      4.5)]
                                          (when (pos? teff)
                                            [eid (atmosphere-from-teff teff logg B)]))))
                                stars)]
               {c/atmosphere-shells shells}))})
