;; Existing Truth-model probes, not a new planetary solver.
(require '[clojure.pprint :as pp]
         '[domain.interior :as interior]
         '[domain.interior-test :as fixtures]
         '[domain.voxel.carve :as carve]
         '[domain.voxel-carve-test]
         '[domain.ecs.core :as ecs]
         '[domain.ecs.components :as c]
         '[domain.ecology.system :as ecology])

(defn geology-probe
  "Characterize the current deterministic geology seed without evolving physics."
  [id candidate]
  (let [f (interior/seed-field candidate)]
    {:id id :mode :static-seed :environment (:environment f)
     :surface-temperature-K (get-in f [:thermal :surface-temperature])
     :radius-m (:radius-m f) :mass-kg (:mass-kg f)
     :layers (count (:layers f)) :plates (count (:plates f))
     :convection-cells (count (:convection f))
     :layer-mass-residual-kg (- (reduce + 0.0 (map :mass (:layers f))) (:mass-kg f))
     :water-mass-kg :not-tracked :atmosphere-fluid-evolution :not-implemented}))

(defn ecology-probe
  "Observe the existing ecology emitter under an explicit composition/pressure control."
  [id composition pressure]
  (let [[w eid] (ecs/spawn (ecs/empty-world))
        w (ecs/put-components w eid {c/matter-state :planet c/mass 5.97e24
                                    c/temperature 288.0 c/pressure pressure
                                    c/composition composition})
        ws ((:run (ecology/ecology-system)) w)
        e (get-in ws [c/ecology eid])]
    {:id id :mode :single-ecs-emitter :input-composition composition
     :pressure-Pa pressure :adopted? (some? e) :moisture (:moisture e)
     :phase (:phase e) :liquid-water-inventory :not-supplied}))

(defn impact-probe
  "Fold an injected production collision packet through the existing voxel systems."
  []
  ;; Reuse the test fixture exactly: inject the production sticky collision packet,
  ;; then run the existing three frozen-world emitters and production fold.
  ;; Access to private fixture vars is intentionally research-only.
  (let [[w eid _] ((ns-resolve 'domain.voxel-carve-test 'promote-fully))
        mass @(ns-resolve 'domain.voxel-carve-test 'impact-mass)
        packet ((ns-resolve 'domain.voxel-carve-test 'iron-packet) mass 30000.0)
        start (ecs/put-component w eid c/absorb-merge [packet])
        run-tick (ns-resolve 'domain.voxel-carve-test 'run-tick)]
    (loop [world start i 0 rows []]
      (let [band (ecs/get-component world eid c/voxel-band)
            voxels (:voxels band)
            states (frequencies (map :state (filter some? (vals voxels))))
            req (ecs/get-component world eid c/voxel-carve-request)
            row {:fold i :excavated (count (filter nil? (vals voxels)))
                 :states states :queue-jobs (count (ecs/get-component world eid c/voxel-edit-queue))
                 :plans (mapv #(select-keys % [:regime :d-tc :d-fr :v-melt :v-vapor]) (:plans req))}
            rows (conj rows row)]
        (if (= i 8)
          {:id :iron-impact-68m-30kms :mode :injected-collision-ecs-folds
           :impactor-mass-kg mass :impactor-speed-m-s 30000.0
           :rows rows :fluid-ejecta :not-implemented :atmospheric-vapor-transfer :not-implemented}
          (recur (run-tick world) (inc i) rows))))))

(let [base fixtures/rocky-habitable
      cases [(geology-probe :temperate-rocky base)
             (geology-probe :volatile-poor-temperate
                            (assoc base :bulk-composition {:Si 0.25 :Mg 0.2 :O 0.45 :Fe 0.1}))
             (geology-probe :frozen-icy fixtures/icy-frozen)
             (ecology-probe :dry-silicate {:Si 0.25 :Mg 0.2 :O 0.45 :Fe 0.1} 101325.0)
             (ecology-probe :dry-silicate-near-vacuum {:Si 0.25 :Mg 0.2 :O 0.45 :Fe 0.1} 1.0)
             (ecology-probe :no-h-no-o {:Si 0.5 :Fe 0.5} 101325.0)
             (impact-probe)]]
  (pp/pprint {:source-revision "58b0fd91ea65b7a4b3a1f571f22f3d394374a871"
              :scope :existing-model-characterization :scenarios cases}))
(shutdown-agents)
