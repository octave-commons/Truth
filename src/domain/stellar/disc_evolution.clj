(ns domain.stellar.disc-evolution
  "Protoplanetary disk evolution: viscous accretion, angular-momentum transfer,
   gravitational-instability fragmentation, and sub-grid planet seeding."
  (:require
   [clojure.math :as math] [law.stellar                   :as law]
   [law.composition               :as lcomp]
   [domain.ecs.core               :as ecs]
   [domain.ecs.components         :as c]
   [domain.ecs.registry           :as reg]
   [domain.stellar.disc           :as disc]
   [domain.stellar.geometry       :as geometry]
   [domain.stellar.sink           :as sink]
   [domain.planet-formation       :as pf]
   [shape.spatial                 :as sp]))

(def ^:const disk-formation-threshold
  "Minimum specific angular momentum (m²/s) for disk formation.
   Below this, material accretes directly. Above, a disk forms.
   Typical value: ~10¹⁵ m²/s for a solar-mass star at 0.1 AU."
  1.0e15)

(def ^:const disk-fragment-threshold
  "Disk-to-star mass ratio above which the disk becomes gravitationally unstable
   and fragments into planetary embryos. From Toomre instability: Q = c_s Ω / (π G Σ) < 1.
   Empirically, M_disk/M_star > 0.1 triggers fragmentation."
  0.1)

(def ^:const binary-fragment-threshold
  "Disk-to-star mass ratio above which the disk fragments into a stellar companion.
   Much more massive disk needed for binary formation. ~0.5 M_star."
  0.5)

(def ^:const max-gi-fragments-per-disk
  "Maximum number of direct gravitational-instability fragments a single disk
   is allowed to spawn before it is forced to settle."
  3)

(def ^:const gi-fragment-mass-cap
  "Direct GI fragments are capped below the deuterium-burning limit so they
   always classify as :gas-giant, not brown dwarf or protostar."
  (* 0.5 law/deuterium-burning-mass))

(defn- put-tracked
  "`ecs/put-component` on disk-evolution's internal working world, recording the
   written cell in the `::disk-ws` write-set accumulator carried on the world
   map. The emitter returns the accumulated write-set (later writes to the same
   cell win); the working world itself is discarded — it exists only so the
   pass's later steps (viscous transfer, fragmentation, planet seeding) can read
   the earlier steps' this-tick disk state."
  [w eid ctype v]
  (-> (ecs/put-component w eid ctype v)
      (update ::disk-ws assoc-in [ctype eid] v)))

(defn- disk-evolution-pass
  "The disk-evolution computation on a working copy of the frozen snapshot;
   every component write goes through `put-tracked` so the accumulated
   `::disk-ws` IS the system's write-set. See `disk-evolution-system`.

   1. Absorb-accrete processing: reads c/absorb-accrete packets from sink-formation
      and adds disk-routed mass/angmom to c/disk-mass and c/disk-angular-mom (spec §5).
   2. Viscous accretion: transfers disk mass to the star at Ṁ = M_disk / t_visc.
      Angular momentum is conserved — the star spins up, disk shrinks.
    3. Disk-instability fragmentation: when M_disk/M_star > 0.1 (Toomre) the disk
       sheds a self-gravitating clump (sub-stellar embryo via `substellar-mass-class`);
       > 0.5 → a stellar companion (:protostar). Emits c/spawn-request-disk.

   4. Sub-grid planet seeder (spec Part 4): once a dominant :star's disk has
      matured (disk-age > :genesis/disk-maturity) and has NOT yet been seeded,
      `planet-formation/planet-seeds` converts the disk's solid surface density
      into :planet entities by a core-accretion prescription (NOT by merging gas
      parcels — canonical note §1, beat 6). Emits c/spawn-request-planet, sets
      the one-shot c/planets-seeded flag, and debits the consumed mass/angular
      momentum from c/disk-mass / c/disk-angular-mom (conservation).

   Fragment/planet spawns are materialized next tick by materialize-lifecycle
   (one-tick Jacobi delay). Runs in the parallel fan-out (was a post-fold
   barrier)."
  [world]
  (let [dt  (double (or (:sim/dt world) 1.0e12))
        eps (double (or (:sim/softening world) 0.0))
        ;; Incorporate disk-routed absorb-accrete packets from sink-formation
        ;; (solid bodies still captured whole by the hierarchical path).
        world (reduce-kv
               (fn [w eid packets]
                 (let [disk-pkts (filter :disk-route packets)]
                   (if (seq disk-pkts)
                     (let [add-mass (reduce + 0.0 (map :mass disk-pkts))
                           add-L    (reduce sp/v+ [0.0 0.0 0.0] (map :angular-momentum disk-pkts))
                           old-dm   (double (or (ecs/get-component w eid c/disk-mass) 0.0))
                           old-L    (or (ecs/get-component w eid c/disk-angular-mom) [0.0 0.0 0.0])]
                       (-> w
                           (put-tracked eid c/disk-mass (+ old-dm add-mass))
                           (put-tracked eid c/disk-angular-mom (sp/v+ old-L add-L))))
                     w)))
               world
               (get-in world [:components c/absorb-accrete] {}))
        ;; Incorporate gradual gas accretion routed to the disk by
        ;; domain.mass-transfer (c/disk-mass-flux / c/disk-l-flux). This is the
        ;; gas→disk channel that replaced sink-formation's whole-parcel gas
        ;; swallowing (M3); disk-evolution is the sole writer of c/disk-mass and
        ;; c/disk-angular-mom, so folding it here keeps single-writer.
        world (let [dmf (get-in world [:components c/disk-mass-flux] {})
                    dlf (get-in world [:components c/disk-l-flux] {})]
                (reduce
                 (fn [w eid]
                   (let [add-mass (double (or (get dmf eid) 0.0))]
                     (if (pos? add-mass)
                       (let [add-L  (or (get dlf eid) [0.0 0.0 0.0])
                             old-dm (double (or (ecs/get-component w eid c/disk-mass) 0.0))
                             old-L  (or (ecs/get-component w eid c/disk-angular-mom) [0.0 0.0 0.0])]
                         (-> w
                             (put-tracked eid c/disk-mass (+ old-dm add-mass))
                             (put-tracked eid c/disk-angular-mom (sp/v+ old-L add-L))))
                       w)))
                 world
                 (keys dmf)))
        evolve
        (fn [w eid]
          (if-not (ecs/alive? w eid)
            w
            (let [M       (double (or (ecs/get-component w eid c/mass) 0.0))
                  disk-m  (double (or (ecs/get-component w eid c/disk-mass) 0.0))
                  disk-L  (or (ecs/get-component w eid c/disk-angular-mom) [0.0 0.0 0.0])
                  disk-j  (sp/len disk-L)]
              (if-not (and (pos? M) (pos? disk-m))
                w
                (let [ratio    (/ disk-m M)
                      ;; Disk outer radius from angular momentum
                      r-disk   (disc/disk-radius (/ disk-j (max 1.0 disk-m)) M)
                      t-visc   (disc/disk-viscous-timescale r-disk M)
                      ;; Viscous accretion rate: Ṁ = M_disk / t_visc × dt
                      mdot-visc (* disk-m (/ dt t-visc))
                      dm        (min mdot-visc (* 0.05 disk-m)) ;; cap at 5% per tick
                      disk-m'   (- disk-m dm)
                      M'        (+ M dm)
                      ;; Angular momentum transfer: star spins up, disk shrinks
                      ;; L_disk scales with disk mass (assuming same specific L)
                      L-transfer (if (pos? disk-m)
                                   (sp/v* disk-L (/ dm disk-m))
                                   [0.0 0.0 0.0])
                      disk-L'   (sp/v- disk-L L-transfer)
                       ;; Emit influences (integrator owns mass/angmom/spin — spec §7.5)
                      w' (-> w
                             (put-tracked eid c/disk-mass disk-m')
                             (put-tracked eid c/disk-angular-mom disk-L')
                             (put-tracked eid c/mass-flux-disk dm)
                             (put-tracked eid c/torque-disk L-transfer))
                      ;; Disk regime (scalar per star) for tests and planet seeder
                      L-star    (double (or (ecs/get-component w eid c/luminosity) 0.0))
                      composition (or (ecs/get-component w eid c/composition) lcomp/solar-composition)
                      fragments-spawned (long (or (ecs/get-component w eid c/disk-fragments-spawned) 0))
                      regime-map (-> (disc/disk-regime-map {:star-mass M
                                                            :disk-mass disk-m
                                                            :disk-radius r-disk
                                                            :luminosity L-star
                                                            :composition composition})
                                     (merge (get-in world [:test/disk-regime eid] {})))
                      w' (-> w'
                             (put-tracked eid c/disk-regime regime-map)
                             (put-tracked eid c/disk-fragments-spawned fragments-spawned))]
                  ;; Check for gravitational instability
                  (cond
                    ;; Binary formation: massive disk fragments into companion
                    (> ratio binary-fragment-threshold)
                    (let [companion-m (* 0.3 disk-m') ;; companion gets 30% of disk
                          r-disk-now  (disc/disk-radius (/ (sp/len disk-L') (max 1.0 disk-m')) M')
                          ;; Place companion at half the disk radius, but never inside
                          ;; the dt-resolvable radius (else the integrator flings it).
                          r-orbit     (max (* 0.5 (max 1.0e10 r-disk-now))
                                           (disc/resolvable-orbit-radius M' dt disc/min-fragment-orbit-periods))
                          ;; Circular orbit speed in the SOFTENED field the
                          ;; integrator applies — the unsoftened √(GM/r) at an
                          ;; r-orbit inside the Plummer length ejected every
                          ;; fragment at several × the cloud escape speed.
                          v-orbit     (law/softened-circular-speed M' r-orbit eps)
                          ;; Random orbital phase
                          angle       (* 2.0 math/PI (sink/hash01 (hash [eid (:tick world) :binary])))
                          pos         (ecs/get-component w' eid c/position)
                          offset      [(* r-orbit (math/cos angle))
                                       (* r-orbit (math/sin angle))
                                       0.0]
                          comp-pos    (sp/v+ pos offset)
                          comp-vel    (sp/v+ (ecs/get-component w' eid c/velocity)
                                             [(* (- v-orbit) (math/sin angle))
                                              (* v-orbit (math/cos angle))
                                              0.0])
                          ;; Emit spawn request (materialized next tick by materialize-lifecycle)
                          spawn-spec  {:position comp-pos :velocity comp-vel
                                       :mass companion-m
                                       :radius (geometry/sphere-radius companion-m 1.0e3)
                                       :matter-state :protostar
                                       :composition (or (ecs/get-component w' eid c/composition)
                                                        law.composition/solar-composition)
                                       :temperature 1000.0}
                          w'' (put-tracked w' eid c/spawn-request-disk [spawn-spec])
                          ;; Update disk after fragmentation
                          w''' (-> w''
                                   (put-tracked eid c/disk-mass (- disk-m' companion-m))
                                   (put-tracked eid c/disk-angular-mom
                                                (sp/v* disk-L' (/ (- disk-m' companion-m)
                                                                  (max 1.0 disk-m')))))]
                      w''')

                    ;; GI fragment: fast-cooling disk spawns a gas-giant embryo only
                    (and (> ratio disk-fragment-threshold)
                         (= :fragmenting (:regime regime-map))
                         (< fragments-spawned max-gi-fragments-per-disk))
                    (let [embryo-m-raw (* 0.1 disk-m')
                          embryo-m (min embryo-m-raw gi-fragment-mass-cap)
                          r-disk-now (disc/disk-radius (/ (sp/len disk-L') (max 1.0 disk-m')) M')
                          ;; never inside the dt-resolvable radius (else it is flung)
                          r-orbit   (max (* 0.3 (max 1.0e10 r-disk-now))
                                         (disc/resolvable-orbit-radius M' dt disc/min-fragment-orbit-periods))
                          ;; softened-field circular speed — see binary branch
                          v-orbit   (law/softened-circular-speed M' r-orbit eps)
                          angle     (* 2.0 math/PI (sink/hash01 (hash [eid (:tick world) :planet])))
                          pos       (ecs/get-component w' eid c/position)
                          offset    [(* r-orbit (math/cos angle))
                                     (* r-orbit (math/sin angle))
                                     0.0]
                          epos      (sp/v+ pos offset)
                          evel      (sp/v+ (ecs/get-component w' eid c/velocity)
                                           [(* (- v-orbit) (math/sin angle))
                                            (* v-orbit (math/cos angle))
                                            0.0])
                          ;; Emit spawn request (materialized next tick by materialize-lifecycle)
                          spawn-spec {:position epos :velocity evel
                                      :mass embryo-m
                                      :radius (geometry/sphere-radius embryo-m geometry/planet-material-density)
                                      :matter-state :gas-giant
                                      :composition (or (ecs/get-component w' eid c/composition)
                                                       law.composition/solar-composition)
                                      :temperature 300.0}

                          w'' (put-tracked w' eid c/spawn-request-disk [spawn-spec])
                          w''' (-> w''
                                   (put-tracked eid c/disk-mass (- disk-m' embryo-m))
                                   (put-tracked eid c/disk-angular-mom
                                                (sp/v* disk-L' (/ (- disk-m' embryo-m)
                                                                  (max 1.0 disk-m'))))
                                   (put-tracked eid c/disk-fragments-spawned (inc fragments-spawned)))]
                      (if (>= embryo-m law/opacity-limit-mass)
                        w'''
                        w'))

                    ;; Just viscous evolution, no fragmentation
                    :else w'))))))
        world-evolved
        (reduce
         evolve
         world
         (filterv (fn [eid]
                    (let [dm (double (or (ecs/get-component world eid c/disk-mass) 0.0))]
                      (pos? dm)))
                  (ecs/entities-with world c/matter-state c/mass c/disk-mass)))]
    ;; Sub-grid planet seeder (Part 4): a one-shot per mature disk. Reads the
    ;; POST-viscous disk state (world-evolved) so its mass/angular-momentum debit
    ;; composes with this tick's viscous transfer and conservation holds. Only
    ;; seeds around a dominant :star; `planet-seeds` guards on disk maturity and
    ;; the c/planets-seeded flag, returning nil when it must not fire yet.
    (reduce
     (fn [w star]
       (let [res (pf/planet-seeds w star)]
         (if (and res (seq (:spawns res)))
           (-> w
               (put-tracked star c/disk-mass (:disk-m res))
               (put-tracked star c/disk-angular-mom (:disk-L res))
               (put-tracked star c/planets-seeded true)
               (put-tracked star c/spawn-request-planet (mapv second (:spawns res))))
           w)))
     world-evolved
     (filterv (fn [eid]
                (and (= :star (ecs/get-component world-evolved eid c/matter-state))
                     (pos? (double (or (ecs/get-component world-evolved eid c/disk-mass) 0.0)))
                     (nil? (ecs/get-component world-evolved eid c/spawn-request-disk))))
              (ecs/entities-with world-evolved c/matter-state c/mass c/disk-mass)))))

(defn disk-evolution-system
  "Double-buffer write-set system: evolves protoplanetary disks on the viscous
   timescale and triggers planet/binary formation via gravitational instability
   (see `disk-evolution-pass` for the physics). Sole writer of c/disk-mass,
   c/disk-angular-mom, c/mass-flux-disk, c/torque-disk, c/spawn-request-disk,
   c/spawn-request-planet, and c/planets-seeded.

   The pass runs on an internal working copy of the frozen snapshot (its later
   steps read its earlier steps' this-tick disk state); only the accumulated
   write-set leaves the emitter — no world diff.

   0-arity returns the native write-set system for the fan-out; 1-arity applies
   the pass to `world` and returns the updated world — a convenience for
   benches, tests, and REPL use."
  ([]
   {:id     :disk-evolution
    :writes (reg/registry-writes :disk-evolution)
    :run    (fn [world] (get (disk-evolution-pass world) ::disk-ws {}))})
  ([world] (dissoc (disk-evolution-pass world) ::disk-ws)))
