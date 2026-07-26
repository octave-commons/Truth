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

;; UNUSED-PENDING: Disc/stellar-structure physics implemented ahead of the system that consumes
;; it — no write-set emitter reads these yet.
;; See kanban/tasks/phase-0-protoplanetary-disc-implementation-spec.md
#_{:clj-kondo/ignore [:clojure-lsp/unused-public-var]}
(def ^:const disk-formation-threshold
  "Minimum specific angular momentum (m²/s) for disk formation.
   Below this, material accretes directly. Above, a disk forms.
   Typical value: ~10¹⁵ m²/s for a solar-mass star at 0.1 AU."
  1.0e15)

(def ^:const disk-fragment-threshold
  "Disk-to-star mass ratio above which the disk becomes gravitationally unstable
   and fragments into planetary embryos. Literature suggests this threshold is
   much higher for low-mass stars: a 0.25 M_sun host requires q ~ 0.7 (cold) to
   ~ 1.4 (irradiated). We use 0.7 as a gameplay-compromise that still allows
   gas-giant formation without starving the star. See
   kanban/tasks/star-growth-disk-fragmentation-nebula-transparency-spec.md."
  0.7)

(def ^:const binary-fragment-threshold
  "Disk-to-star mass ratio above which the disk fragments into a stellar
   companion (binary). Realistically the disk must approach or exceed the
   star's mass before it becomes non-Keplerian and unstable to companion
   formation."
  1.0)

(def ^:const max-gi-fragments-per-disk
  "Maximum number of direct gravitational-instability fragments a single disk
   is allowed to spawn before it is forced to settle."
  3)

(def ^:const gi-fragment-mass-cap
  "Direct GI fragments are capped below the deuterium-burning limit so they
   always classify as :gas-giant, not brown dwarf or protostar."
  (* 0.5 law/deuterium-burning-mass))

(def ^:const fragment-placement-floor-m
  "Physical floor (m) for disk-fragment spawn radius: 0.3 AU. Placement is
   decoupled from the global bulk `dt` (multi-timescale design §3.5): compact
   fragments are sub-stepped by the integrator, so the old dt-resolvable floor
   (`disc/resolvable-orbit-radius` at the global dt → ~162 AU live) is retired.
   The floor keeps fragments outside the radius where the sub-stepper's K clamp
   (4096) would bind (design §3.3): at 1 AU around 1 M☉ with dt = 80 yr and
   f_orb = 1/20 the sub-stepper already demands K = 1600; placing below ~0.3 AU
   would push K past the clamp and silently degrade perturbation fidelity."
  (* 0.3 law/au))

(def ^:const max-fragmentation-disk-radius
  "Largest disk outer radius (m) at which fragmentation may fire: 100 AU. A
   'disk' larger than this is not a protostellar disk — it is the still-
   collapsing rotating clump, and spawning fragments into it births planets
   at 10³–10⁵ AU, unbound from birth (docs/research/physics/cluster-dispersal-
   integration-heating.md §3.2: every FORMED event in the probe log appeared
   at 3,474–71,440 AU from the nearest stellar body). The gate blocks BOTH
   fragmentation branches while r-disk exceeds the band; the disk may still
   fragment later once collapse compactifies it. The same band gates the
   binary branch: a companion spawned from a clump-scale 'disk' suffers the
   identical unbound-birth defect, and a real ≤100 AU disk still permits
   wide-companion placement out to 0.5×100 AU."
  (* 100.0 law/au))

(def ^:const hill-dominance-factor
  "Host-dominance ratio for the Hill-stable spawn clamp: fragments spawn only
   where μ_host/r² exceeds this factor × the local tidal acceleration — the
   same 100× ratio the integrator's sub-step gate applies
   (domain.integrator.kinematics/substep-dominance-factor). A local literal,
   not a require, to keep domain.stellar free of a domain.integrator
   dependency."
  100.0)

(def ^:const fragment-drop-log-interval-ticks
  "Minimum ticks between loud log lines for a blocked/skipped fragment spawn,
   per disk per reason — the drop must be visible (a silent gate reads as
   'no disks ever fragment') but must not spam every tick (mirrors the
   kinematics K-clamp println precedent, with a throttle)."
  1000)

(defonce ^:private fragment-drop-log-state
  ;; {[eid reason] -> last-logged-tick} throttle state for log-fragment-drop!.
  (atom {}))

(defn- log-fragment-drop!
  "Loud, throttled log for a blocked fragment spawn: at most one line per
   [eid reason] per `fragment-drop-log-interval-ticks` ticks."
  [tick eid reason detail]
  (let [t (long (or tick 0))
        k [eid reason]
        last-tick (get @fragment-drop-log-state k)]
    (when (or (nil? last-tick) (>= (- t (long last-tick))
                                   fragment-drop-log-interval-ticks))
      (swap! fragment-drop-log-state assoc k t)
      (println "[disk-evolution] fragment spawn blocked:" (name reason)
               "eid" eid detail))))

(defn- tidal-perturbers
  "Tidal perturber {:mass :dist} pairs for a disk host at `host-pos`, from the
   pass's pre-collected `perturbers` ({:eid :mass :pos}) — every massive body
   in the world except the host itself, gas parcels included (the embedded-
   phase clump tide is the field that fails the dominance gate, research §3.3)."
  [host-eid host-pos perturbers]
  (if (nil? host-pos)
    []
    (into [] (comp (remove #(= host-eid (:eid %)))
                   (keep (fn [{:keys [mass pos]}]
                           (when pos
                             (let [d (sp/dist host-pos pos)]
                               (when (pos? d)
                                 {:mass mass :dist d}))))))
          perturbers)))

(defn- hill-clamped-spawn-radius
  "Spawn radius (m) for a fragment of the disk host at `eid`, Hill-clamped — or
   `nil` when the clamp falls below the placement floor, in which case the spawn
   is dropped this tick (logged) and the caller must return its working world
   unchanged. NEVER violate `fragment-placement-floor-m`.

   `radius-fraction` is the branch's share of the disk annulus (binary 0.5, GI
   0.3) — that fraction and `branch` are the ONLY things the two fragment
   branches disagree about. This is shared SAFETY logic: it was duplicated
   verbatim across both branches, so fixing one silently left the other wrong
   (jscpd; card kanban/tasks/static-analysis-jscpd-src-extractions.md ranked it
   the highest drift risk of the six).

   Takes a single map because the honest positional arity is 8, which is the
   HARD parameter-bloat gate (`dev/smell_report.clj`)."
  [{:keys [world' tick eid host-mass radius-fraction r-disk-m perturbers branch]}]
  (let [;; Physical disk radius (the branch's share of the annulus), floored at
        ;; 0.3 AU so fragments never land inside the physical placement floor
        ;; where the sub-stepper's K clamp would bind (design §3.3/§3.5).
        ;; Placement is decoupled from the global bulk dt — see
        ;; `fragment-placement-floor-m`.
        r-orbit-raw (max (* (double radius-fraction) (max 1.0e10 r-disk-m))
                         fragment-placement-floor-m)
        ;; Hill-stable clamp: cap the spawn radius where the host's pull still
        ;; dominates the local tidal field by `hill-dominance-factor`
        ;; (research §3.3).
        r-hill-max  (disc/tidal-dominance-radius
                     host-mass hill-dominance-factor
                     (tidal-perturbers eid (ecs/get-component world' eid c/position)
                                       perturbers))
        r-orbit     (min r-orbit-raw r-hill-max)]
    (if (< r-orbit fragment-placement-floor-m)
      ;; The clamp crossed the floor in a tight tide: skip the spawn this tick
      ;; (retry later) — never violate the floor.
      (do (log-fragment-drop! tick eid :hill-clamp-below-floor
                              {:branch branch
                               :r-hill-max-m r-hill-max
                               :floor-m fragment-placement-floor-m})
          nil)
      r-orbit)))

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
       Two guards (research §3.2–3.3, kanban/tasks/formation-placement-v2.md):
       the disk-scale gate blocks fragmentation entirely while r-disk exceeds
       `max-fragmentation-disk-radius` (a clump-scale 'disk' is still collapsing;
       spawning into it births unbound planets at kAU), and the Hill-stable
       clamp caps the spawn radius at `disc/tidal-dominance-radius` (skipping
       the spawn — never violating the floor — when the clamp would cross
       `fragment-placement-floor-m`). Blocked spawns log loudly, throttled per
       disk per reason (`log-fragment-drop!`).

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
        ;; One cheap scan per tick shared by every disk host (never a per-disk
        ;; neighbour query — cf. kinematics/stellar-parents): every massive
        ;; body is a potential tidal perturber for the Hill-stable spawn
        ;; clamp, gas parcels included — the embedded-phase clump tide is the
        ;; field that fails the dominance gate (research §3.3).
        spawn-perturbers
        (into [] (keep (fn [e]
                         (when-let [m (ecs/get-component world e c/mass)]
                           (when (pos? (double m))
                             {:eid  e
                              :mass (double m)
                              :pos  (ecs/get-component world e c/position)}))))
              (ecs/entities-with world c/position c/mass))
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
                             (put-tracked eid c/disk-fragments-spawned fragments-spawned))
                       ;; Post-viscous disk radius, shared by both fragmentation
                       ;; branches for the disk-scale gate and spawn placement.
                      r-disk-now (disc/disk-radius (/ (sp/len disk-L') (max 1.0 disk-m')) M')
                       ;; Disk-scale gate (research §3.2): a "disk" beyond
                       ;; `max-fragmentation-disk-radius` is the still-collapsing
                       ;; clump, not a protostellar disk — fragmentation into it
                       ;; births unbound planets at kAU.
                      disk-scale-ok? (<= r-disk-now max-fragmentation-disk-radius)]
                   ;; Check for gravitational instability
                  (cond
                     ;; Binary formation: massive disk fragments into companion
                    (> ratio binary-fragment-threshold)
                    (if-not disk-scale-ok?
                      (do (log-fragment-drop! (:tick world) eid :clump-scale-disk
                                              {:branch :binary
                                               :r-disk-m r-disk-now
                                               :gate-m max-fragmentation-disk-radius})
                          w')
                      (let [companion-m (* 0.3 disk-m') ;; companion gets 30% of disk
                             ;; Place companion at half the disk radius, but never
                            r-orbit     (hill-clamped-spawn-radius
                                         {:world' w' :tick (:tick world) :eid eid
                                          :host-mass M' :radius-fraction 0.5
                                          :r-disk-m r-disk-now
                                          :perturbers spawn-perturbers
                                          :branch :binary})]
                        (if (nil? r-orbit)
                          w'
                          (let [;; Circular orbit speed in the SOFTENED field the
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
                            w'''))))

                     ;; GI fragment: fast-cooling disk spawns a gas-giant embryo only
                    (and (> ratio disk-fragment-threshold)
                         (= :fragmenting (:regime regime-map))
                         (< fragments-spawned max-gi-fragments-per-disk))
                    (if-not disk-scale-ok?
                      (do (log-fragment-drop! (:tick world) eid :clump-scale-disk
                                              {:branch :gi
                                               :r-disk-m r-disk-now
                                               :gate-m max-fragmentation-disk-radius})
                          w')
                      (let [embryo-m-raw (* 0.1 disk-m')
                            embryo-m (min embryo-m-raw gi-fragment-mass-cap)
                            r-orbit     (hill-clamped-spawn-radius
                                         {:world' w' :tick (:tick world) :eid eid
                                          :host-mass M' :radius-fraction 0.3
                                          :r-disk-m r-disk-now
                                          :perturbers spawn-perturbers
                                          :branch :gi})]
                        (if (nil? r-orbit)
                          w'
                          (let [;; NEWTONIAN circular speed, not softened: the GI
                                 ;; fragment spawns as :gas-giant and is sub-stepped
                                 ;; by the integrator's Wisdom–Holman path, whose drift
                                 ;; applies the exact Newtonian central term — the
                                 ;; spawn velocity must match that law (design §3.5
                                 ;; pairing rule; softened-circular here produced the
                                 ;; e≈1 near-radial plunge regression of 2026-07-23).
                                v-orbit   (law/newtonian-circular-speed M' r-orbit)
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
                              w')))))

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
