(ns domain.genesis.summary
  "Observable summaries and read-only reports over the Phase 0 world."
  (:require
   [clojure.math :as math] [domain.ecs.core :as ecs]
   [domain.ecs.components :as c]
   [domain.ecs.parallel :as par]
   [domain.stellar.thermodynamics :as thermo]
   [shape.spatial :as sp]))

;; --- Observable progress ----------------------------------------------------

(defn thermal-progress
  "Smooth 0→1 measure of how far the system has climbed from cold nebular gas
   (~10 K) toward fusion ignition (~1e7 K), on a log-temperature ramp."
  [peak-temp]
  (let [t (max 10.0 (double (or peak-temp 10.0)))]
    (max 0.0 (min 1.0 (/ (- (math/log10 t) 1.0) 6.0)))))

(def ^:private solar-mass 1.989e30)

(defn- stats-aux
  "Single-pass accumulator for stats-of. Returns [m mt peak stars bins disk-mass resv-mass].
   The per-entity component reads fan out in parallel (order-preserving), then
   the fold walks the projections in eid order — the same floating-point
   accumulation order as the serial walk it replaces."
  [world eids]
  (let [cells (par/par-mapv
               (fn [eid]
                 [eid
                  (double (or (ecs/get-component world eid c/mass) 0.0))
                  (double (or (ecs/get-component world eid c/temperature) 0.0))
                  (ecs/get-component world eid c/matter-state)
                  (double (or (ecs/get-component world eid c/disk-mass) 0.0))
                  (double (or (ecs/get-component world eid c/wind-mass-lost) 0.0))])
               eids)]
    (reduce (fn [[m mt peak stars bins disk-mass wind-mass] [eid mass t st disk wind]]
              [(+ m mass)
               (+ mt (* mass t))
               (max peak t)
               (if (= :star st) (conj stars eid) stars)
               (if (= :star st)
                 (let [m-msun (/ mass 1.989e30)]
                   (cond
                     (< m-msun 0.1)  (update bins 0 inc)
                     (< m-msun 0.5)  (update bins 1 inc)
                     (< m-msun 1.0)  (update bins 2 inc)
                     (< m-msun 2.0)  (update bins 3 inc)
                     (< m-msun 5.0)  (update bins 4 inc)
                     (< m-msun 10.0) (update bins 5 inc)
                     (< m-msun 50.0) (update bins 6 inc)
                     :else           (update bins 7 inc)))
                 bins)
               (+ disk-mass disk)
               (+ wind-mass wind)])
            [0.0 0.0 0.0 [] (vec (repeat 8 0)) 0.0 0.0]
            cells)))

(defn stats-of
  "Observable readouts for the HUD, tallied once per tick from the post-physics
   world and a precomputed `summ`: total mass (kg and solar masses),
   mass-weighted mean temperature, peak temperature, and the body/resolved/
   star/planet counts. Pure; cached on the world so the renderer reads it
   cheaply every frame instead of re-walking the entity set at 60 Hz."
  [world summ]
  (let [eids   (ecs/entities-with world c/mass)
        [m mt peak _stars bins disk-mass wind-mass] (stats-aux world eids)
        lod-freq (frequencies (vals (get-in world [:components c/lod-level] {})))
        total     (+ m disk-mass wind-mass)
        resolved-mass (reduce (fn [acc r]
                                (if (= :nebula (:matter-state r))
                                  acc
                                  (+ acc (double (or (:mass r) 0.0)))))
                              0.0 (:regions summ))]
    {:total-mass-kg     total
     :total-mass-msun   (/ total solar-mass)
     :bulk-mass-kg      m
     :disk-mass-kg      disk-mass
     :wind-mass-kg      wind-mass
     :resolved-fraction (if (pos? m) (/ resolved-mass m) 0.0)
     :avg-temp          (if (pos? m) (/ mt m) 0.0)
     :peak-temp         peak
     :body-count        (:body-count summ)
     :resolved-count    (:resolved-count summ)
     :star-count        (count (:stars summ))
     :planet-count      (:planet-count summ)
     ;; Phase 1 stats
     :xuv-escape-count  (count (get-in world [:components c/atmosphere-escape] {}))
     :sed-band-count    (count (get-in world [:components c/sed-bands] {}))
     :lod-local         (get lod-freq :local 0)
     :lod-system        (get lod-freq :system 0)
     :lod-galaxy        (get lod-freq :galaxy 0)
     :imf-bins          bins
     :disk-count        (reduce-kv (fn [n _eid dm]
                                     (if (pos? (double (or dm 0.0))) (inc n) n))
                                   0
                                   (get-in world [:components c/disk-mass] {}))}))

(defn formation-progress
  "Fraction of the original nebula mass now bound into stars and planets, in [0,1].

   Spark-redesign metric (kanban/tasks/formation-progress-metric.md): a single
   scalar that drives the spark's resolution curve. Pure read off a precomputed
   system summary `summ` and the world's `:genesis/nebula-mass`. Intermediate
   matter-states (condensed-core, protostar, planetesimal, etc.) are
   deliberately EXCLUDED — only `:star` and `:planet` count — so the signal
   plateaus while mass climbs through intermediate states and steps at
   promotion events; card 4 (spark resolution) must be designed knowing this.
   (:resolved-fraction in `stats-of` already covers all-resolved-mass.)
   Clamped to [0,1] for finite masses so an over-massive injected body can
   never push it above 1. Returns 0.0 when the world carries no
   `:genesis/nebula-mass`."
  [world summ]
  (let [bound (->> (concat (:stars summ) (:planets summ))
                   (reduce (fn [acc r] (+ acc (double (or (:mass r) 0.0)))) 0.0))
        neb   (double (or (:genesis/nebula-mass world) 0.0))]
    (if (pos? neb)
      (max 0.0 (min 1.0 (/ bound neb)))
      0.0)))

(defn system-summary
  "Tally the world's resolved matter into the shape used for complexity, phase
   detection, and habitability. Single-pass over entities with matter-state+mass."
  [world]
  (let [eids    (ecs/entities-with world c/matter-state c/mass)
        ;; The projection (9 component reads per entity) fans out in parallel;
        ;; par-mapv preserves eid order so the tallied vectors are identical to
        ;; the serial walk's.
        regions (par/par-mapv #(thermo/entity->region world %) eids)]
    (loop [stars    []
           planets  []
           resolved []
           i        0]
      (if (= i (count regions))
        {:body-count     (count regions)
         :resolved-count (count resolved)
         :star?          (boolean (seq stars))
         :fusion?        (boolean (seq stars))
         :planet-count   (count planets)
         :stars          stars
         :planets        planets
         :regions        regions}
        (let [r  (nth regions i)
              st (:matter-state r)]
          (recur (if (= :star st) (conj stars r) stars)
                 (if (= :planet st) (conj planets r) planets)
                 (if (= :nebula st) resolved (conj resolved r))
                 (inc i)))))))

(defn center-of-mass
  "Mass-weighted centre of mass of every positioned body, or [0 0 0] when empty.
   A global reduction over the snapshot — the recenter frame-offset (spec §6, §8)."
  [world]
  (let [eids (ecs/entities-with world c/position c/mass)]
    (if (seq eids)
      (let [[sx sy sz m]
            (reduce (fn [[ax ay az am] eid]
                      (let [[x y z] (ecs/get-component world eid c/position)
                            mm (double (ecs/get-component world eid c/mass))]
                        [(+ ax (* (double x) mm)) (+ ay (* (double y) mm))
                         (+ az (* (double z) mm)) (+ am mm)]))
                    [0.0 0.0 0.0 0.0] eids)]
        (if (pos? m) [(/ sx m) (/ sy m) (/ sz m)] [0.0 0.0 0.0]))
      [0.0 0.0 0.0])))

(defn field-report
  "A one-line readout of the live fields for insight: tick/phase, body counts,
   temperature range, peak magnetic field, and the regime histogram."
  [world]
  (let [eids    (ecs/entities-with world c/matter-state)
        temps   (keep #(ecs/get-component world % c/temperature) eids)
        bmags   (keep #(some-> (ecs/get-component world % c/b-field) sp/len) eids)
        regimes (frequencies (keep #(ecs/get-component world % c/regime) eids))
        summ    (system-summary world)]
    (format "t=%-4d %-22s | bodies=%-4d resolved=%-3d star=%-5s planets=%d | T=%.0f..%.1e K | Bmax=%.1e T | %s"
            (:tick world) (name (or (:arc/current world) :genesis/ticking))
            (:body-count summ) (:resolved-count summ)
            (str (:star? summ)) (:planet-count summ)
            (double (reduce min 1.0e30 temps)) (double (reduce max 0.0 temps))
            (double (reduce max 0.0 bmags))
            (pr-str regimes))))
