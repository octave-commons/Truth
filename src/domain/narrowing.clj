(ns domain.narrowing
  "Gravitational binding: the continuous observer<->world coupling that is the
   mechanical substance of becoming gravitationally bound to one world
   (The First Narrowing, child A — kanban/tasks/narrowing-binding-mechanic.md;
   design docs/designs/the-first-narrowing-star-to-planet.md §2).

   `binding-step` is the pure one-tick update over the observer's `c/binding`
   map {world-eid -> [0,1]}:

   - ACCRUES while the observer's focus overlaps a candidate world's OWN
     world-scale radius (`law.narrowing/world-focus-radius`, NOT the
     system-wide `:attention-shell :immediate-r` used by `:focus-zone` — see
     kanban/tasks/narrowing-worldscale-overlap-gate.md) AND focus is
     sustained. The only focus/attention signals that exist today are the
     observer's `:focus-position` and `:focus-intensity` (there is no
     per-world Focus (Q) verb yet — see GAPS in the `:binding` system
     docstring), so 'sustained focus on world W' is modelled as:
     focus-position within `world-focus-radius` of W, with
     `:focus-intensity` at or above `law.narrowing/focus-intensity-floor`.
     Accrual is small per tick, so binding only builds under focus that STAYS.
   - Rate scales with habitability/resolution and in-world resonance IF such
     signals are supplied (`:signals {eid {:habitability h :resonance r}}`);
     both default to neutral 1.0. No per-world habitability/resonance economy
     exists yet — the pure function takes them as inputs so a later card can
     wire real signals without changing the step.
   - DECAYS slowly (sticky) when attention is elsewhere entirely.
   - ZERO-SUM: while any one world accrues, every other bound world decays at
     the additional `zero-sum-decay-rate` — you can only fall one way.
   - SUNK COST: binding LOST before capture is tallied into `c/binding-scar`
     at `scar-fraction` — a permanent, never-refunded record of spent
     attention. Modelled as a separate tally rather than asymmetric
     accrue/decay rates (the alternative on the card): stickiness is already
     the asymmetric-rate story, and the scar makes the sunk cost explicit,
     inspectable data the Agency economy can later charge against. The actual
     Agency deduction is the verb-wiring card's job; this card only records
     the scar.

   Cost curves (design §2.2, decision (a) on the card): the SHAPE is the
   world's literal escape-energy proxy GM/R (specific energy, J/kg — the
   potential-well depth, one R-factor up from the M5 surface gravity GM/R^2);
   only the Agency-unit scale is tuned. Nudge/Perturb cost falls with binding
   (leverage from closeness); Release/Widen cost rises linearly with binding
   times the well depth — free at binding 0, a full climb out of the well at
   capture. Wiring these to actual verbs (Q/R, Agency spend) is a later card;
   they are exposed here as pure functions of (binding, world-params).

    The `:binding` system is the sole writer of `c/binding` and
    `c/binding-scar`, a double-buffer fan-out emitter in the style of
    `:focus-zone`. It exposes binding as DATA the focus-zone promotion/demotion
    system could read later; it does NOT rewire promotion/demotion (that is
    the parent epic's later child).

    Child B (kanban/tasks/narrowing-commitment-horizon.md; design §3-4) adds
    the COMMITMENT HORIZON: the `:commitment` system watches the binding map
    and, when the deepest-bound world's coupling crosses
    `law.narrowing/capture-threshold` while `ready-to-commit?` holds, fires
    exactly once — stamping `c/commitment-state` (`:committed` on the captured
    world, `:inert` on every unchosen candidate), re-arming the observer's six
    hotbar slots IN PLACE to the Phase 1 planetary palette (`c/palette`), and
    engaging the planetary time-lock (`c/time-lock`). The canonical
    `:event/world-commitment` ledger event is appended SERIALLY after the fold
    by `domain.genesis.tick/emit-commitment-event` (same precedent as
    `emit-handoff-event` — a ledger dispatch from inside a write-set `:run` is
    diffed away at the component-type boundary). Capture is hard-irreversible:
    `c/commitment-state` is write-once, so no second commitment can ever fire
    and un-binding post-capture is impossible at the system level (pre-capture
    withdrawal is child A's cost curve + scar, already shipped)."
  (:require
   [domain.ecs.components :as c]
   [domain.ecs.core :as ecs]
   [domain.ecs.registry :as reg]
   [domain.player :as player]
   [law.narrowing :as law]
   [law.stellar :as law-stellar]
   [shape.spatial :as sp]))

;; --- Pure derivations ---------------------------------------------------------

(defn escape-energy-proxy
  "Specific escape energy GM/R (J/kg) of a world with `mass` (kg) and `radius`
   (m) — its potential-well depth, the M5 surface-gravity estimate one
   R-factor up. 0.0 when `radius` is missing or non-positive (mirrors
   domain.stellar.classifier/surface-gravity's degenerate case)."
  [mass radius]
  (let [r (double (or radius 0.0))]
    (if (pos? r)
      (/ (* law-stellar/G (double (or mass 0.0))) r)
      0.0)))

(defn focus-overlap?
  "True when `world-pos` lies within `radius` of the observer's `focus-pos` —
   the world-scale overlap test of design §2.1. Callers pass
   `law.narrowing/world-focus-radius` (a candidate's own planetary scale), NOT
   the observer's `:attention-shell :immediate-r` (the unrelated whole-system
   `:focus-zone` regional-cell radius) — see
   kanban/tasks/narrowing-worldscale-overlap-gate.md."
  [focus-pos world-pos radius]
  (<= (sp/dist focus-pos world-pos) (double radius)))

(defn focus-sustained?
  "True when the observer map's `:focus-intensity` is at or above the sustained
   floor — the strongest 'Focus (Q) is held' signal that exists today."
  [obs]
  (>= (double (:focus-intensity obs 0.5)) law/focus-intensity-floor))

;; --- The pure step -------------------------------------------------------------

(defn- clamp01
  "Clamp `x` into [0,1]."
  [x]
  (-> (double x) (max 0.0) (min 1.0)))

(defn binding-step
  "Advance observer->world binding by one tick. Pure.

   Options map:
     :binding        {world-eid -> [0,1]}       current coupling (default {})
     :scars          {world-eid -> sunk tally}  permanent sunk record (default {})
     :focused-eids   seq of world-eids the observer's sustained, overlapping
                     focus is on THIS tick (default none)
     :signals        {world-eid {:habitability h :resonance r}} — accrual-rate
                     multipliers, both defaulting to neutral 1.0 until a real
                     per-world habitability/resonance economy exists
     :accrual-rate   per-tick gain on a focused world (default law/accrual-rate)
     :decay-rate     per-tick sticky decay on an unfocused world
     :zero-sum-decay-rate  ADDITIONAL per-tick decay on every unfocused world
                     while any world is focused (attention is zero-sum)
     :scar-fraction  fraction of LOST binding tallied into :scars

   Returns {:binding {eid -> [0,1]} :scars {eid -> tally}}. Worlds that decay
   to zero are dropped from :binding; their scar persists forever."
  [{:keys [binding scars focused-eids signals
           accrual-rate decay-rate zero-sum-decay-rate scar-fraction]
    :or {binding {} scars {} focused-eids [] signals {}
         accrual-rate law/accrual-rate
         decay-rate law/decay-rate
         zero-sum-decay-rate law/zero-sum-decay-rate
         scar-fraction law/scar-fraction}}]
  (let [focused    (set focused-eids)
        attending? (boolean (seq focused))
        eids       (into (set (keys binding)) focused)]
    (reduce
     (fn [acc eid]
       (let [b0 (double (get binding eid 0.0))
             {:keys [habitability resonance]
              :or {habitability 1.0 resonance 1.0}} (get signals eid)
             b1 (if (contains? focused eid)
                  (clamp01 (+ b0 (* accrual-rate habitability resonance)))
                  (clamp01 (- b0 decay-rate (if attending? zero-sum-decay-rate 0.0))))]
         (-> acc
             (update :binding (fn [m] (if (pos? b1) (assoc m eid b1) (dissoc m eid))))
             (update :scars (fn [m] (if (< b1 b0)
                                      (update m eid (fnil + 0.0) (* scar-fraction (- b0 b1)))
                                      m))))))
     {:binding binding :scars scars}
     eids)))

;; --- Cost curves (design §2.2) --------------------------------------------------

(defn nudge-cost
  "Agency cost of a Nudge/Perturb on a world at `binding` (clamped to [0,1]).
   Falls linearly with binding — leverage from closeness (design §2.2): at
   capture the observer acts at (- 1 nudge-leverage) of the base cost.
   `world-params` is accepted for symmetry with `release-cost` and for the
   verb-wiring card (well-keyed perturb costs); the curve is currently keyed
   to binding only."
  [binding _world-params]
  (* law/nudge-base-cost (- 1.0 (* law/nudge-leverage (clamp01 binding)))))

(defn release-cost
  "Agency cost of Release/Widen on a world at `binding`, from `world-params`
   {:mass kg :radius m}. The LITERAL escape-energy-proxy shape: the fraction
   `binding` of the full specific escape energy GM/R, scaled to Agency units
   by law/release-tuning. Free at binding 0; at capture it is a full climb
   out of the well. Heavier/deeper-well worlds are genuinely harder to leave
   at the same binding."
  [binding {:keys [mass radius]}]
  (* law/release-tuning (escape-energy-proxy mass radius) (clamp01 binding)))

;; --- System ---------------------------------------------------------------------

(defn- candidate-worlds
  "Every entity carrying a `c/planet-candidate` record — the resolved worlds
   binding can couple to."
  [world]
  (ecs/entities-with world c/planet-candidate c/position))

(defn binding-system
  "The `:binding` write-set system (double-buffer fan-out, in the style of
   `:focus-zone`). SOLE writer of `c/binding` and `c/binding-scar`; both live
   on the singleton observer entity, keyed by candidate-world eid.

   Reads the frozen snapshot only: the observer component (focus position,
   focus intensity — NOT the attention shell, which is the unrelated
   `:focus-zone` regional-cell radius), its own prior one-tick-stale
   `c/binding`/`c/binding-scar` output (ordinary Jacobi lag, like
   `:neighbor-cache`), and every candidate world's position, tested against
   `law.narrowing/world-focus-radius`.

   GAPS (machinery the card assumes that does not exist yet — intentionally
   NOT built here):
   - There is no per-world Focus (Q) verb, Nudge/Perturb verb, Release/Widen
     (R) verb, and no Agency-spend wiring. 'Sustained focus' is read from the
     observer's `:focus-position` + `:focus-intensity`; the cost curves are
     pure functions nothing calls yet.
   - Habitability/resolution and in-world resonance signals are passed to
     `binding-step` as neutral defaults; no per-world signal economy exists.
   - Binding does NOT yet feed promotion/demotion (`:focus-zone`): it is
     exposed as data that system could read in a later child of the epic."
  []
  {:id     :binding
   :ns     'domain.narrowing
   :writes (reg/registry-writes :binding)
   :run
   (fn [world]
     (if-let [obs-eid (player/observer-entity world)]
       (let [obs        (ecs/get-component world obs-eid c/observer)
             focus      (or (:focus-position obs) [0.0 0.0 0.0])
             focused    (if (focus-sustained? obs)
                          (filterv #(focus-overlap? focus (ecs/get-component world % c/position)
                                                     law/world-focus-radius)
                                   (candidate-worlds world))
                          [])
             {:keys [binding scars]}
             (binding-step {:binding (or (ecs/get-component world obs-eid c/binding) {})
                            :scars   (or (ecs/get-component world obs-eid c/binding-scar) {})
                            :focused-eids focused})]
          {c/binding      {obs-eid binding}
           c/binding-scar {obs-eid scars}})
        {}))})

;; --- Spark<->world spring tether (spark-planet-binding, approach B) -----------
;; The observer ("spark") is NOT an ECS body; its state is the singleton
;; c/observer map (domain.player.state/create-observer). These fns are pure
;; steps called from the infra dev loop alongside domain.player.focus/drift —
;; NOT a fan-out emitter (there is no per-tick component write-set here; see
;; kanban/tasks/spark-planet-binding.md's "follow the drift precedent"
;; instruction). Shared with the camera's binding tether
;; (infra.camera.navigation.tether), which delegates its own tether-strength /
;; deepest-binding to these so both the frame and the spark fully engage at
;; the same instant, from the same reading of binding depth.

(defn tether-strength
  "Tether/spring engagement in [0,1] for binding depth `b`: `b /
   capture-threshold`, clamped. Reaches 1.0 exactly at the capture threshold,
   so both the camera tether and the spark spring are already fully engaged
   when `:event/world-commitment` fires — capture is not a jump-cut for
   either."
  [b]
  (-> (/ (double (or b 0.0)) (double law/capture-threshold))
      (max 0.0)
      (min 1.0)))

(defn deepest-binding
  "The [world-eid binding] pair with the greatest binding depth on the
   observer's `c/binding` map, or nil when there is no observer or no
   binding — you fall into the deepest well first."
  [world]
  (when-let [obs-eid (player/observer-entity world)]
    (let [binding (ecs/get-component world obs-eid c/binding)]
      (when (seq binding)
        (apply max-key (fn [[_ b]] (double b)) binding)))))

(defn spring-accel
  "Spring acceleration (m/s^2) pulling the spark from `pos` (with current
   `velocity`) toward `target-pos`, scaled by tether strength `s` in [0,1]
   (see `tether-strength`). The effective spring constant `k` scales linearly
   with `s` (`law/spark-spring-k * s`); damping is set to the CRITICAL value
   for that `k` (`2*sqrt(k)`, floored at `law/spark-min-damping`) so the
   approach never overshoots at any engagement level, including the free
   (s=0) case where only residual velocity bleeds off."
  [pos velocity target-pos s]
  (let [s (-> (double s) (max 0.0) (min 1.0))
        k (* law/spark-spring-k s)
        c (max law/spark-min-damping (* 2.0 (Math/sqrt k)))
        d (sp/v- target-pos pos)]
    (sp/v- (sp/v* d k) (sp/v* velocity c))))

(defn spark-binding-step
  "Advance the observer's spring-bound position by one step. Pure.

   `observer` reads `:position` and `:spark-velocity` (defaults to zero).
   `target-pos` is the deepest-bound world's PREDICTED position (a
   `domain.physics.cache/predicted-position-fn` read in the caller — aims
   where the body WILL be, cutting Jacobi lag), or nil when there is nothing
   to pull toward. `strength` is `tether-strength` of the deepest binding
   (0 when unbound). `dt` is the elapsed seconds for this step — paced like
   `domain.player.focus/drift` on WALL-CLOCK dt, the spark's own felt
   responsiveness, not the physics fan-out's dilated `:sim/dt` (the spark is a
   player-experienced motion, not a simulated body).

   Semi-implicit Euler: v' = v + a*dt; pos' = pos + v'*dt. When `target-pos`
   is nil, the target collapses to the current position (zero spring
   displacement) so only damping acts — existing velocity decays toward rest
   instead of the spark snapping or drifting forever. Returns `observer` with
   `:position` and `:spark-velocity` replaced."
  [{:keys [position spark-velocity] :as observer} target-pos strength dt]
  (let [v0     (or spark-velocity (sp/vec3 0 0 0))
        dt     (double dt)
        target (or target-pos position)
        a      (spring-accel position v0 target strength)
        v1     (sp/v+ v0 (sp/v* a dt))
        pos1   (sp/v+ position (sp/v* v1 dt))]
    (assoc observer :position pos1 :spark-velocity v1)))

(defn observer-motion-step
  "One frame of the spark's spring-tether motion — the exclusive owner of the
   observer's spring integration, called from the infra dev loop alongside
   `domain.player.focus/drift`. Pure.

   Returns `observer` UNCHANGED when `:input-active?` is true: the player is
   actively flying (WASD) this frame, and player input wins outright — same
   rule `infra.camera.navigation.tether/tether-step` follows for the camera.
   The spark never fights the player. Otherwise delegates to
   `spark-binding-step` with `:target-pos`, `:strength`, and `:dt` from
   `opts`."
  [observer {:keys [target-pos strength dt input-active?]}]
  (if input-active?
    observer
    (spark-binding-step observer target-pos strength dt)))

;; --- Commitment horizon (child B) --------------------------------------------

(defn ready-to-commit?
  "True when the capture gate's readiness half holds for `world-eid`: the arc
   has reached planet formation (`:arc/genesis-planets-formed` or
   `:arc/life-emergence`) AND the world is a stabilized candidate (carries the
   M5 `c/planet-candidate` record).

   NOTE (gap): canon `domain.arc/ready-to-narrow?` (commitment-and-resonance.md
   §4.1) is unreachable from this fan-out namespace — domain.arc requires
   domain.genesis, which requires domain.genesis.systems, which requires THIS
   namespace; requiring domain.arc here would close a dependency cycle. The
   arc half is therefore read directly from the `:arc/current` world key (one
   tick stale inside the fan-out — ordinary Jacobi lag, like every cross-system
   read), and the habitable-world half degrades honestly to the M5
   planet-candidate record: that record IS the component-level stabilized-
   candidate contract, and it is the only thing binding can accrue on. The
   chemistry-scored `habitability/habitable-worlds` refinement is unreachable
   for the same cycle reason."
  [world world-eid]
  (and (#{:arc/genesis-planets-formed :arc/life-emergence} (:arc/current world))
       (some? (ecs/get-component world world-eid c/planet-candidate))))

(defn time-lock-record
  "The planetary time-lock record stamped on the committed world at capture
   (commitment-and-resonance.md §5.1): base rate 1 s/s for the committed world
   and its immediate neighborhood, everything outside sub-cycled. This is the
   minimal DATA HOOK — the existing cadence mechanisms (world-level `:sim/dt`
   pacing, and `c/lod-tick-phase` owned solely by `:lod-scheduler`) cannot be
   written from this system without violating single-writer, so actuating the
   lock against them is a later card."
  [tick]
  {:locked?       true
   :captured-tick (long (or tick 0))
   :base-rate     1.0
   :neighborhood  :immediate
   :outside       :sub-cycled})

(defn- committed-world-eid
  "The eid of the already-committed world, or nil. A `:committed`
   `c/commitment-state` anywhere is the hard-irreversible marker: the horizon
   has been crossed for this world-line and no second commitment may fire."
  [world]
  (some (fn [[eid state]] (when (= :committed state) eid))
        (get-in world [:components c/commitment-state] {})))

(defn- captured-eid
  "The deepest-bound world at or past `law/capture-threshold` that is ready to
   commit, or nil. You fall into the deepest well first."
  [world binding]
  (->> binding
       (filter (fn [[eid b]] (and (>= (double b) law/capture-threshold)
                                  (ready-to-commit? world eid))))
       (sort-by (fn [[_ b]] (- (double b))))
       ffirst))

(defn commitment-system
  "The `:commitment` write-set system (double-buffer fan-out, in the style of
   `:binding` above). SOLE writer of `c/commitment-state`, `c/palette`, and
   `c/time-lock`. Reads the frozen snapshot only: the observer entity, its
   one-tick-stale `c/binding` (ordinary Jacobi lag), every candidate world's
   `c/planet-candidate`, and its own prior write-once output.

   On capture — deepest-bound world at or past `law/capture-threshold` while
   `ready-to-commit?` holds — fires EXACTLY ONCE:
   - `c/commitment-state`: `:committed` on the captured world, `:inert` on
     every unchosen candidate. This is the per-world interactivity marker;
     nothing else in the codebase marks per-world interactivity today (gap
     noted on the card).
   - `c/palette` on the observer: the six slots re-armed IN PLACE to the Phase
     1 planetary palette (`law/planetary-palette`). Resonance CARRIES OVER
     automatically: it lives in the observer component (`:resonance`), which
     no fan-out system writes, so the palette swap never touches it. Nothing
     is unallocated because Resonance was never allocated into domain-side
     slots — the Genesis palette is currently the infra-side keymap
     `infra.render.input/action-palette`, so `c/palette` first appears AT
     capture; modelling the pre-capture Genesis palette domain-side
     (`law/genesis-palette`) and the Resonance respec flow are later cards.
   - `c/time-lock` on the captured world: the §5.1 data hook; cadence
     actuation is a later card (see `time-lock-record`).

   Irreversibility: `c/commitment-state` is write-once — when a `:committed`
   world exists this system emits nothing, forever. The `:binding` system
   keeps running post-capture (harmless stale coupling data), but the horizon
   cannot be re-crossed and cannot be uncrossed.

   The canonical `:event/world-commitment` ledger event
   (commitment-and-resonance.md §4.2) is NOT emitted here — a ledger dispatch
   from inside a write-set `:run` is diffed away at the component-type
   boundary. It is appended SERIALLY after the fold by
   `domain.genesis.tick/emit-commitment-event`, reacting to the
   `c/commitment-state :committed` marker this system writes (the exact
   `emit-handoff-event` precedent)."
  []
  {:id     :commitment
   :ns     'domain.narrowing
   :writes (reg/registry-writes :commitment)
   :run
   (fn [world]
     (if (committed-world-eid world)
       {}
       (if-let [obs-eid (player/observer-entity world)]
         (let [binding  (or (ecs/get-component world obs-eid c/binding) {})
               captured (captured-eid world binding)]
           (if-not captured
             {}
             (let [unchosen (remove #(= captured %)
                                    (ecs/entities-with world c/planet-candidate))]
               {c/commitment-state (into {captured :committed}
                                         (map (fn [eid] [eid :inert]))
                                         unchosen)
                c/time-lock        {captured (time-lock-record (:tick world))}
                c/palette          {obs-eid law/planetary-palette}})))
         {})))})
