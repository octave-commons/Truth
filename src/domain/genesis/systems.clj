(ns domain.genesis.systems
  "Phase 0 physics system wiring. Builds the ordered system table that the
   double-buffer tick fan-out runs each frame."
  (:require
   [domain.em :as em]
   [domain.intervention :as intervention]
   [domain.player :as player]
   [domain.integrator :as integ]
   [domain.stellar.geometry :as geometry]
   [domain.stellar.classifier :as classifier]
   [domain.stellar.seeder :as seeder]
   [domain.stellar.fusion :as fusion]
   [domain.chemistry :as chemistry]
   [domain.stellar.wind :as wind]
   [domain.atmosphere :as atmosphere]
   [domain.stellar.disc :as disc]
   [domain.regime :as regime]
   [domain.physics.collision :as collision]
   [domain.stellar.sink :as sink]
   [domain.stellar.disc-evolution :as disc-evolution]
   [domain.mass-transfer :as mt]
   [domain.lod :as lod]
   [domain.ecology :as ecology]
   [domain.debris :as debris]
   [domain.orbital.system :as orbital]
   [domain.mhd.force :as mfd]
   [domain.physics.cache :as cache]))

;; Ongoing physics that is not specific to formation moved to its proper owner:
;;   xuv-atmospheric-escape-system → domain.atmosphere
;;   lod-scheduler                 → domain.lod
;;   magnetosphere-coupling-system → domain.em

(defn- ^:private physics-force-systems
  "Secondary force emitters + integrator."
  [dt]
  [(intervention/warp-acceleration-system)
   (player/observer-acceleration-system)
   (intervention/thermal-intervention-system)
   (integ/integrator-system dt)])

(defn- ^:private physics-transform-systems
  "Geometry / structure / EOS."
  []
  [(geometry/structure-system)
   (geometry/eos-system)])

(defn- ^:private physics-formation-systems
  "Classifier, seeding, fusion, chemistry, wind, disc, and regime."
  [dt]
  [(classifier/classifier-system)
   (seeder/condensation-seeder-system)
   (em/field-system dt)
   (fusion/fusion-system)
   (fusion/stellar-sed-system)
   (fusion/atmosphere-shells-system)
   (chemistry/nucleosynthesis-system dt)
   (fusion/deuterium-depletion-system)
   (wind/stellar-wind-system)
   (wind/wind-ablation-system)
   (wind/stellar-flare-system)
   (atmosphere/xuv-atmospheric-escape-system)
   (disc/disc-identification-system)
   (regime/regime-system)])

(defn- ^:private physics-lifecycle-systems
  "Collision, promotion, sink, disk, mass transfer, LOD, magnetosphere, ecology,
   debris, and neighbor cache."
  []
  [(collision/collision-detection-system)
   (fusion/fusion-promotion-system)
   (sink/sink-formation-system)
   (disc-evolution/disk-evolution-system)
   (mt/mass-transfer-system)
   (cache/neighbor-cache-system)
   (lod/lod-scheduler)
   (em/magnetosphere-coupling-system)
   (ecology/ecology-system)
   (debris/debris-reaper-system)])

(defn physics-systems-parallel
  "The transform systems as NATIVE write-set systems for the double-buffer
   fan-out (`domain.ecs.tick/run-parallel`). Every entry is
   `{:id kw :writes #{ctype ...} :run (fn [frozen] write-set)}` — each emits
   only the component types it exclusively owns, sourced from its
   registry-declared `:writes` (spec Fix 3: zero `tick/legacy-system` wraps, so
   no per-system world copy or diff).

   EXCLUDES `recenter`, which is not a system at all any more: the integrator
   subtracts the one-tick-stale COM frame-offset (a world scalar set in
   tick-world) from every new position (spec §6)."
  [{:keys [sim/G sim/theta sim/dt sim/softening sim/cutoff] :as _params}]
  (let [soft (or softening 1e14)
        cut  (or cutoff (* 0.1 soft))]
    (into [(orbital/gravity-acceleration G theta soft cut)
           (mfd/merged-hydro-em-system dt)]
          (concat (physics-force-systems dt)
                  (physics-transform-systems)
                  (physics-formation-systems dt)
                  (physics-lifecycle-systems)))))
