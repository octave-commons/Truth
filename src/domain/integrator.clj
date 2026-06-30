(ns domain.integrator
  "The single integrator — sole writer of physical state.

   Spec: docs/notes/specs/2026.06.29-unified-physical-state-integrator-spec.md.

   §0 (the one sentence): there is ONE writer of physical state — this integrator
   — which reads, each tick, the previous snapshot plus a set of INFLUENCE
   components registered against the physical fields they affect; every other
   system is a pure, snapshot-reading, single-component-writing emitter, and
   nothing runs serially \"after the fold.\"

   Per §8 the integrator is structured as a set of per-field updaters that share
   one owner (one registry entry, one `:writes` set). It owns the dynamical and
   contended physical fields:

     position, velocity   ← Σ accel.* · dt   + frame-offset + accretion momentum
     mass                 ← Σ mass-flux.*    + absorbed mass
     angular-momentum     ← L + Σ torque.*   + absorbed L ;  spin ← L/I (derived)
     temperature          ← virial / radiative + heat.intervention + impact heat
     composition          ← comp.burn then comp.depletion + absorbed blend

   The pure stateless derivations that already have a clean single owner stay as
   their own fan-out systems (structure→radius/density, eos→pressure,
   field→b-field, fusion→luminosity) — §8 Q2: a separate system IS the single
   owner of its component, so single-writer holds either way; keeping them
   isolated keeps each field's logic small and avoids touching tuned formulas
   (§9 non-goal). This namespace owns only the fields that were contended or
   accumulated across multiple writers."
  (:require
   [domain.ecs.core       :as ecs]
   [domain.ecs.components  :as c]
   [domain.stellar        :as stellar]
   [domain.intervention   :as intervention]
   [law.stellar           :as law]
   [shape.spatial         :as sp]))

(def ^:private zero3 [0.0 0.0 0.0])

;; ---------------------------------------------------------------------------
;; The influence registry (§4)
;; ---------------------------------------------------------------------------
;; Declarative map: each additive physical field → the influence components that
;; contribute to it (summed, then scaled). The integrator reads THIS, not
;; hardcoded knowledge — adding a force/heat/torque/mass source is one line here
;; plus its single-writer emitter. Non-additive fields (temperature, composition,
;; spin) are derived by the per-field updaters below and documented in :derived.
(def influence-registry
  {:velocity         {:accumulate [c/accel-gravity c/accel-pressure c/accel-lorentz
                                   c/accel-observer c/accel-warp]
                      :compose :sum :scale :dt}
   :angular-momentum {:accumulate [c/torque-em c/torque-disk]
                      :compose :sum :scale :dt}
   :mass             {:accumulate [c/mass-flux-wind c/mass-flux-flare
                                   c/mass-flux-xuv c/mass-flux-disk]
                      :compose :sum :scale :raw}
   :velocity-delta   {:accumulate [c/dv-wind c/dv-flare]
                      :compose :sum :scale :raw}
   :temperature      {:influences [c/heat-intervention]
                      :derived "virial (cores) / radiative (worlds) + intervention ease"}
   :composition      {:influences [c/comp-burn c/comp-depletion]
                      :derived "comp.burn replaces, comp.depletion zeroes"}
   :position         {:influences [c/frame-offset]
                      :derived "x + v·dt − frame-offset (COM Galilean shift)"}
   :spin             {:derived "L / I (moment of inertia)"}})

(defn- sum-vec-influences
  "Σ of the vector influence components `ctypes` on `eid` (missing ⇒ zero)."
  [world eid ctypes]
  (reduce (fn [acc ct] (sp/v+ acc (or (ecs/get-component world eid ct) zero3)))
          zero3 ctypes))

(defn- sum-scalar-influences
  "Σ of the scalar influence components `ctypes` on `eid` (missing ⇒ 0)."
  [world eid ctypes]
  (reduce (fn [acc ct] (+ acc (double (or (ecs/get-component world eid ct) 0.0))))
          0.0 ctypes))

;; ---------------------------------------------------------------------------
;; The integrator system
;; ---------------------------------------------------------------------------

(def ^:private accel-sources
  (get-in influence-registry [:velocity :accumulate]))

(def ^:private dv-sources
  (get-in influence-registry [:velocity-delta :accumulate]))

(def ^:private mass-flux-sources
  (get-in influence-registry [:mass :accumulate]))

;; --- Per-field updaters (each returns a write-set fragment) ------------------

(defn kinematics-ws
  "Position + velocity. v' = v + (Σ accel.*)·dt + Σ dv.* ; x' = x + v'·dt
   (symplectic Euler), then the one-tick-stale COM frame-offset is subtracted from
   position (a pure Galilean shift, §6). Force contributions come from the
   emitters (gravity/hydro/em/observer/warp); the Δv contributions are ejection
   recoils (wind/flare). The integrator never evaluates the force field itself."
  [world dt]
  (let [foff (or (:phase0/frame-offset world) zero3)
        eids (ecs/entities-with world c/position c/velocity
                                c/mass c/radius c/body-kind)]
    (reduce
     (fn [ws eid]
       (let [a   (sum-vec-influences world eid accel-sources)
             dv  (sum-vec-influences world eid dv-sources)
             v   (ecs/get-component world eid c/velocity)
             x   (ecs/get-component world eid c/position)
             v'  (sp/v+ (sp/v+ v (sp/v* a dt)) dv)
             x'  (sp/v- (sp/v+ x (sp/v* v' dt)) foff)]
         (-> ws
             (assoc-in [c/velocity eid] v')
             (assoc-in [c/position eid] x'))))
     {}
     eids)))

(defn mass-ws
  "Mass. m' = max(0, m + Σ mass-flux.*) — the per-source mass fluxes (stellar
   wind/flare loss, XUV escape, disk→star viscous transfer) summed and applied.
   Only bodies with a flux this tick are rewritten; every other body's mass is
   untouched. The integrator owns mass (spec §5: mass becomes an accumulated
   field like velocity); accretion/merge mass is folded in by the absorb blend."
  [world]
  (let [eids (ecs/entities-with world c/mass)
        cell (into {}
                   (keep (fn [eid]
                           (let [dm (sum-scalar-influences world eid mass-flux-sources)]
                             (when-not (zero? dm)
                               [eid (max 0.0 (+ (double (ecs/get-component world eid c/mass)) dm))]))))
                   eids)]
    (if (empty? cell) {} {c/mass cell})))

(defn temperature-ws
  "Temperature. The base value is the virial/radiative derivation owned by
   `stellar/temperature-system` (cores heat by Kelvin–Helmholtz contraction,
   worlds reach radiative equilibrium, diffuse gas is left at its background);
   the integrator then applies the player's heat.intervention ease on top — so
   a heat source/sink eases the freshly-derived temperature (as the old serial
   `apply-thermal-interventions` eased the post-fold value). Reusing the tested
   derivation keeps the formula unchanged (§9 non-goal)."
  [world dt]
  (let [base ((:run (stellar/temperature-system dt)) world)
        base-cell (get base c/temperature {})
        heats     (get-in world [:components c/heat-intervention] {})]
    (if (empty? heats)
      base
      (let [;; bodies the base derivation skips (e.g. :nebula) but which still
            ;; receive a heat ease keep their snapshot temperature as the base.
            with-eased
            (reduce-kv
             (fn [cell eid cs]
               (let [t0 (double (or (get base-cell eid)
                                    (ecs/get-component world eid c/temperature)
                                    intervention/min-temp))]
                 (assoc cell eid (intervention/apply-thermal-contributions t0 cs))))
             base-cell
             heats)]
        {c/temperature with-eased}))))

(defn composition-ws
  "Composition. The integrator owns the blend: start from the snapshot
   composition, apply the H→He burn (comp.burn replaces it for burning cores),
   then the deuterium gate (comp.depletion zeroes :D for hot bodies). Only bodies
   carrying an influence this tick are rewritten — every other body's composition
   is untouched (spec §7.5; burn + depletion no longer co-write composition)."
  [world]
  (let [burns (get-in world [:components c/comp-burn] {})
        deps  (get-in world [:components c/comp-depletion] {})
        eids  (into (set (keys burns)) (keys deps))]
    (if (empty? eids)
      {}
      {c/composition
       (into {}
             (keep (fn [eid]
                     (let [base (or (get burns eid)
                                    (ecs/get-component world eid c/composition))]
                       (when base
                         [eid (reduce (fn [c k] (assoc c k 0.0))
                                      base
                                      (get deps eid #{}))]))))
             eids)})))

(def ^:private torque-sources
  (get-in influence-registry [:angular-momentum :accumulate]))

(defn rotation-ws
  "Angular momentum + spin. L' = L + Σ torque.* (the torque influences are
   per-step ΔL — magnetic braking, disk spin-up — so they are summed raw, not
   scaled by dt). Spin is the derivation ω = L'/I (uniform sphere at the
   equatorial radius, identical to the oblate form since radius == a). The
   integrator owns both, ending the em/disk co-write of angular-momentum (§7.5)."
  [world]
  (let [eids (ecs/entities-with world c/angular-momentum c/mass c/radius)]
    (reduce
     (fn [ws eid]
       (let [L  (or (ecs/get-component world eid c/angular-momentum) zero3)
             dL (sum-vec-influences world eid torque-sources)
             L' (sp/v+ L dL)
             m  (ecs/get-component world eid c/mass)
             r  (ecs/get-component world eid c/radius)
             spin' (stellar/spin-from-angular-momentum L' m r)]
         (-> ws
             (assoc-in [c/angular-momentum eid] L')
             (assoc-in [c/spin eid] spin'))))
     {}
     eids)))

(defn integrator-system
  "Write-set system: the single owner of the dynamical/contended physical fields.
   Composes the per-field updaters (each writes a disjoint set of components, so
   the fragments merge cleanly). Sole writer of position, velocity, temperature,
   composition, angular-momentum, spin (and, as the lifecycle milestone lands,
   mass)."
  [dt]
  {:id     :integrator
   :writes #{c/position c/velocity c/mass c/temperature c/composition
             c/angular-momentum c/spin}
   :run    (fn [world]
             (merge (kinematics-ws world dt)
                    (mass-ws world)
                    (temperature-ws world dt)
                    (composition-ws world)
                    (rotation-ws world)))})
