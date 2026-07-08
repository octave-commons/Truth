(ns domain.stellar.wind
  "Stellar winds, ablation, and flares. Stars emit a radial wind profile that
   heats, ionizes, and ablates nearby :nebula parcels instead of spawning
   ballistic parcels. Episodic flares/CMEs add transient mass-loss and XUV boosts."
  (:require
   [clojure.math :as math] [law.stellar                   :as law]
   [law.sed                       :as lsed]
   [law.composition               :as lcomp]
   [domain.em                     :as em]
   [domain.ecs.core               :as ecs]
   [domain.ecs.components         :as c]
   [domain.ecs.tick               :as tick]
   [domain.profile                :as profile]
   [domain.stellar.thermodynamics :as thermo]
   [shape.spatial                 :as sp]))

(def ^:const speed-of-light 2.99792458e8) ;; m/s

(defn- wind-direction
  "A deterministic-but-varying outward unit vector for a wind ejection, seeded by
   entity id + tick (no Math/random — banned, it would break resume). Uniform-ish
   over the sphere so successive ejections fan out instead of streaming one way."
  [eid tick]
  (let [h     (abs (long (hash [eid tick])))
        theta (* 2.0 math/PI (/ (double (mod h 1009)) 1009.0))
        z     (- (* 2.0 (/ (double (mod (quot h 1009) 1013)) 1013.0)) 1.0)
        r     (math/sqrt (max 0.0 (- 1.0 (* z z))))]
    [(* r (math/cos theta)) (* r (math/sin theta)) z]))

(defn- wind-step
  "Compute one star's wind profile for `stellar-wind-system`.
   Returns a map with :eid and :wind-profile, or nil for non-stars."
  [ctx eid]
  (let [{:keys [world k]} ctx]
    (when (= :star (ecs/get-component world eid c/matter-state))
      (let [M (double (or (ecs/get-component world eid c/mass) 0.0))
            R (double (or (ecs/get-component world eid c/radius) 0.0))]
        (when (and (pos? M) (pos? R))
          (let [region   (thermo/entity->region world eid)
                L        (double (thermo/star-luminosity region))
                v-esc    (math/sqrt (/ (* 2.0 law/G M) R))
                shells   (ecs/get-component world eid c/atmosphere-shells)
                sed      (ecs/get-component world eid c/sed-bands)
                corona-t (when shells
                           (some #(when (= :corona (:layer/id %)) (:temperature %)) shells))
                L-xuv    (when sed (lsed/xuv-luminosity (:bands sed)))
                T-escape (/ (* law/m-H v-esc v-esc) (* 2.0 law/k-B))
                corona-t (double (or corona-t (max 1.5e6 T-escape)))
                v-wind   (if (pos? T-escape)
                           (* v-esc (math/sqrt (/ corona-t T-escape)))
                           v-esc)
                L-drive  (double (or L-xuv L))
                mdot     (if (pos? v-esc) (/ (* k L-drive) (* v-esc 2.99792458e8)) 0.0)
                reference-r (max R (* 2.0 R))
                ram      (if (and (pos? mdot) (pos? v-wind) (pos? reference-r))
                           (/ (* mdot v-wind) (* 4.0 math/PI reference-r reference-r))
                           0.0)
                ion      (min 1.0 (max 0.3 (/ corona-t 1.0e6)))]
            {:eid eid
             :profile {:wind/dot-m          mdot
                       :wind/v-escape       v-wind
                       :wind/ram-pressure   ram
                       :wind/reference-r    reference-r
                       :wind/luminosity-xuv (double (or L-xuv 0.0))
                       :wind/ionization     ion
                       :wind/corona-t       corona-t}}))))))

(defn stellar-wind-system
  "Write-set emitter: each luminous star carries a radial `c/wind-profile`.

   The profile holds the steady mass-loss rate, launch speed, reference ram
   pressure, XUV luminosity, ionization fraction, and coronal temperature. No
   ballistic wind parcels are spawned; ablation is handled by
   `wind-ablation-system` in the next tick."
  []
  {:id     :stellar-wind
   :writes #{c/wind-profile}
   :run
   (fn [world]
     (profile/profile-sections
      world
      [[:stellar-wind/scan
        (fn [w]
          {:world w
           :k     (double (:genesis/wind-rate-scale w 1.0))
           :dt    (double (or (:sim/dt w) 1.0e12))
           :stars (ecs/entities-with w c/matter-state c/mass c/radius
                                     c/position c/velocity)})]
       [:stellar-wind/compute
        (fn [ctx]
          (assoc ctx :results (vec (keep (partial wind-step ctx) (:stars ctx)))))]
       [:stellar-wind/write-set
        (fn [{:keys [world results]}]
          (let [;; clear stale profiles from stars that stopped shining
                cleared (reduce (fn [ws eid]
                                  (assoc-in ws [c/wind-profile eid] tick/removed))
                                {} (keys (get-in world [:components c/wind-profile])))]
            (reduce
             (fn [ws {:keys [eid profile]}]
               (assoc-in ws [c/wind-profile eid] profile))
             cleared
             results)))]]))})
(defn- ablation-constants
  "Constants used by wind ablation for a single star."
  [world dt star-eid]
  (let [star-lum (double (or (ecs/get-component world star-eid c/luminosity) 0.0))
        cap-frac (double (or (:genesis/wind-energy-cap-fraction world) 0.05))]
    {:h (double (or (:genesis/gas-smoothing-radius world) 6.0e13))
     :eta (double (or (:genesis/wind-ablation-efficiency world) 0.05))
     :min-mass (double (or (:genesis/wind-ablation-min-mass world)
                           (* 1.0e-6 (:genesis/gas-particle-mass world 4.0e27))))
     :max-frac (double (or (:genesis/wind-max-mass-loss-frac world) 0.05))
     :cap-energy (* cap-frac (max 0.0 star-lum) dt)
     :cv (* 2.5 law/k-B (/ 1.0 law/m-H))
     :dt dt}))

(defn- parcel-heating
  "Compute wind-heating for one parcel, or nil if it is unaffected."
  [world star-eid star-pos profile parcel-eid constants]
  (when (= :nebula (ecs/get-component world parcel-eid c/matter-state))
    (let [{:keys [h eta min-mass max-frac cv dt]} constants
          p-pos (ecs/get-component world parcel-eid c/position)
          r (sp/dist p-pos star-pos)
          r-eff (max r (* 0.1 h))
          mdot (double (:wind/dot-m profile 0.0))
          v-w (double (:wind/v-escape profile 0.0))
          ram (if (and (pos? mdot) (pos? v-w) (pos? r-eff))
                (/ (* mdot v-w) (* 4.0 math/PI r-eff r-eff))
                0.0)
          rho (double (or (ecs/get-component world parcel-eid c/density) 1.0e-16))
          parcel-m (double (or (ecs/get-component world parcel-eid c/mass) 1.0e24))
          radius (double (or (ecs/get-component world parcel-eid c/radius) 6.0e13))
          area (* math/PI radius radius)
          dm-raw (if (and (pos? ram) (pos? rho))
                   (* eta ram area dt (/ 1.0 rho))
                   0.0)
          dm-capped (min dm-raw (* max-frac parcel-m))
          dm (if (> parcel-m min-mass) dm-capped 0.0)
          delta-t (if (and (pos? ram) (pos? rho) (pos? cv))
                    (* eta ram dt (/ 1.0 (* rho cv)))
                    0.0)
          ion-rate (if (pos? delta-t) (/ delta-t 1.0e6) 0.0)]
      (when (pos? dm)
        {:wind-heating/delta-t delta-t
         :wind-heating/ionization-rate ion-rate
         :wind-heating/mass-loss dm
         :wind-heating/source-eid star-eid}))))

(defn- ablate-parcel
  "Compute wind ablation for all nebula parcels from a star's profile.
   Returns a map from parcel-eid to heating influence."
  [world dt star-eid star-pos profile]
  (let [constants (ablation-constants world dt star-eid)]
    (loop [parcels (ecs/entities-with world c/matter-state c/position c/mass c/radius
                                      c/density c/temperature)
           heated 0.0
           ws {}]
      (if-let [parcel (and (< heated (:cap-energy constants)) (first parcels))]
        (let [h (parcel-heating world star-eid star-pos profile parcel constants)]
          (recur (rest parcels)
                 (if h
                   (+ heated (* (:wind-heating/mass-loss h) (:cv constants)
                                (:wind-heating/delta-t h)))
                   heated)
                 (if h (assoc ws parcel h) ws)))
        ws))))

(defn- ablation-scan
  [world]
  {:world world
   :dt (double (or (:sim/dt world) 1.0e12))
   :k (double (or (:genesis/wind-interaction-factor world) 5.0))
   :stars (ecs/entities-with world c/wind-profile c/matter-state c/position c/mass
                             c/luminosity)})

(defn- ablation-results
  [{:keys [world dt k stars]}]
  (let [h (double (or (:genesis/gas-smoothing-radius world) 6.0e13))]
    {:world world
     :results
     (for [star-eid stars
           :let [profile (ecs/get-component world star-eid c/wind-profile)
                 star-pos (ecs/get-component world star-eid c/position)
                 interaction (* k h)]
           :when (some? profile)]
       {:star star-eid
        :ablations (into {} (filter (fn [[eid _]]
                                      (<= (sp/dist (ecs/get-component world eid c/position) star-pos)
                                          interaction))
                                    (ablate-parcel world dt star-eid star-pos profile)))})}))

(defn- ablation-write
  [{:keys [world results]}]
  (let [cleared (reduce (fn [ws eid]
                          (assoc-in ws [c/wind-heating eid] tick/removed))
                        {} (keys (get-in world [:components c/wind-heating])))]
    (reduce (fn [ws {:keys [star ablations]}]
              (let [total-dm (reduce + 0.0 (map :wind-heating/mass-loss (vals ablations)))
                    ws' (if (pos? total-dm)
                          (assoc-in ws [c/wind-mass-lost star] total-dm)
                          ws)]
                (reduce (fn [ws'' [eid h]]
                          (assoc-in ws'' [c/wind-heating eid] h))
                        ws'
                        ablations)))
            cleared
            results)))

(defn wind-ablation-system
  "Fan-out emitter: a star's wind profile ablates nearby `:nebula` parcels.

   Writes `c/wind-heating` on affected parcels and `c/wind-mass-lost` on the
   source star as a diagnostic ledger. Affected parcels are those within a small
   multiple of the gas smoothing length; mass loss is capped per tick and by a
   minimum mass floor to prevent runaway."
  []
  {:id     :wind-ablation
   :writes #{c/wind-heating c/wind-mass-lost}
   :run
   (fn [world]
     (profile/profile-sections
      world
      [[:wind-ablation/scan (fn [_] (ablation-scan world))]
       [:wind-ablation/compute ablation-results]
       [:wind-ablation/write-set ablation-write]]))})

(defn- flare-params
  "Gather tunable constants for stellar-flare-system."
  [world]
  (let [period (long (:genesis/flare-period world 0))]
    (when (pos? period)
      {:period period
       :dt (double (or (:sim/dt world) 1.0e12))
       :tick (or (:tick world) 0)
       :p-mass (double (or (:genesis/wind-parcel-mass world)
                           (some-> (:genesis/gas-particle-mass world) (* 0.25))
                           1.0e27))
       :m-fac (double (:genesis/flare-mass-factor world 3.0))
       :v-fac (double (:genesis/flare-speed-factor world 0.4))
       :t-fac (double (:genesis/flare-temp-factor world 3.0))
       :gas-r (double (or (:genesis/gas-smoothing-radius world) 6.0e13))
       :floor (* 0.5 law/hydrogen-burning-mass)
       :sources (em/field-sources world)})))

(defn- flare-for-star
  "Return a flare event map for `eid`, or nil if the star does not fire."
  [world params sources eid]
  (let [{:keys [period dt tick p-mass m-fac v-fac t-fac gas-r floor]} params]
    (when (and (= :star (ecs/get-component world eid c/matter-state))
               (zero? (mod (abs (long (hash [:flare eid tick]))) period)))
      (let [M (double (or (ecs/get-component world eid c/mass) 0.0))
            R (double (or (ecs/get-component world eid c/radius) 0.0))
            fm (* m-fac p-mass)]
        (when (and (pos? R) (> (- M fm) floor))
          (let [region (thermo/entity->region world eid)
                v-esc (math/sqrt (/ (* 2.0 law/G M) R))
                acc (double (or (ecs/get-component world eid c/accretion-radius)
                                (* 100.0 R)))
                v-fl (min v-esc (/ (* v-fac acc) (max 1.0 dt)))
                axis (let [a (ecs/get-component world eid c/rotation-axis)]
                       (if (and a (pos? (sp/len a))) a (wind-direction eid tick)))
                sign (if (even? (mod (abs (long (hash [eid tick]))) 2)) 1.0 -1.0)
                rhat (sp/v* axis sign)
                pos (ecs/get-component world eid c/position)
                vel (ecs/get-component world eid c/velocity)
                ppos (sp/v+ pos (sp/v* rhat R))
                pvel (sp/v+ vel (sp/v* rhat v-fl))
                dv (sp/v* rhat (- (* (/ fm (- M fm)) v-fl)))]
            {:eid eid
             :mass-flux (- fm)
             :dv dv
             :boost {:factor (* t-fac 10.0)
                     :decay-tick (+ tick (long (max 1.0 (/ 3.6e3 (max 1.0 dt)))))}
             :spawn {:position ppos :velocity pvel :mass fm :radius gas-r
                     :matter-state :nebula
                     :composition (or (:composition region) lcomp/solar-composition)
                     :b-field (em/net-field-at ppos sources nil)
                     :temperature (max 3.0 (* t-fac (thermo/virial-temperature M R)))}}))))))

(defn- clear-stale-flares
  "Remove leftover flare mass-flux and recoil from stars that did not fire."
  [world]
  (let [prior (keys (get-in world [:components c/mass-flux-flare]))]
    (reduce (fn [ws eid]
              (-> ws
                  (assoc-in [c/mass-flux-flare eid] tick/removed)
                  (assoc-in [c/dv-flare eid] tick/removed)))
            {}
            prior)))

(defn- flare-write-set
  "Fold a sequence of flare events into a write-set."
  [world fires]
  (reduce (fn [ws {:keys [eid mass-flux dv boost spawn]}]
            (-> ws
                (assoc-in [c/mass-flux-flare eid] mass-flux)
                (assoc-in [c/dv-flare eid] dv)
                (assoc-in [c/flare-boost eid] boost)
                (assoc-in [c/spawn-request-flare eid] [spawn])))
          (clear-stale-flares world)
          fires))

(defn stellar-flare-system
  "Fan-out emitter (winds spec phase B): episodic coronal mass ejections.
   Occasionally a star flings a larger, hotter blob along its rotation axis — a
   bright flare/CME riding on top of the steady wind. Mass is debited directly
   from the star (parcel mass = debit, conserved), and the drift is velocity-
   capped exactly like the wind (`v ≤ flare-speed-factor · feeding-zone / dt`), so
   flares never blow the system apart. Bipolar: successive flares alternate poles.

   Tunables: `:genesis/flare-period` (ticks between flares per star; 0 disables),
   `:genesis/flare-mass-factor` (× wind-parcel mass), `:genesis/flare-speed-factor`
   (drift per tick as a fraction of the feeding zone), `:genesis/flare-temp-factor`
   (× virial temperature, for brightness). A flare never fires if it would pull
   the star below half the hydrogen-burning mass — flares decorate, they don't
   demote."
  []
  {:id     :stellar-flare
   :writes #{c/mass-flux-flare c/dv-flare c/spawn-request-flare c/flare-boost}
   :run
   (fn [world]
     (when-let [params (flare-params world)]
       (let [stars (ecs/entities-with world c/matter-state c/mass c/radius
                                      c/position c/velocity)]
         (flare-write-set world
                          (keep #(flare-for-star world params (:sources params) %) stars)))))})
