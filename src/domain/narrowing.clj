(ns domain.narrowing
  "Gravitational binding: the continuous observer<->world coupling that is the
   mechanical substance of becoming gravitationally bound to one world
   (The First Narrowing, child A — kanban/tasks/narrowing-binding-mechanic.md;
   design docs/designs/the-first-narrowing-star-to-planet.md §2).

   `binding-step` is the pure one-tick update over the observer's `c/binding`
   map {world-eid -> [0,1]}:

   - ACCRUES while the observer's attention-shell immediate radius overlaps a
     candidate world AND focus is sustained. The only focus/attention signals
     that exist today are the observer's `:focus-position`, `:focus-intensity`,
     and `:attention-shell` (there is no per-world Focus (Q) verb yet — see
     GAPS in the `:binding` system docstring), so 'sustained focus on world W'
     is modelled as: focus-position within the immediate radius of W, with
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
   the parent epic's later child)."
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
   the attention-shell immediate-radius overlap test of design §2.1."
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
   focus intensity, attention shell), its own prior one-tick-stale
   `c/binding`/`c/binding-scar` output (ordinary Jacobi lag, like
   `:neighbor-cache`), and every candidate world's position.

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
             r          (or (get-in obs [:attention-shell :immediate-r])
                            (player/probability-collapse-radius obs))
             focused    (if (focus-sustained? obs)
                          (filterv #(focus-overlap? focus (ecs/get-component world % c/position) r)
                                   (candidate-worlds world))
                          [])
             {:keys [binding scars]}
             (binding-step {:binding (or (ecs/get-component world obs-eid c/binding) {})
                            :scars   (or (ecs/get-component world obs-eid c/binding-scar) {})
                            :focused-eids focused})]
         {c/binding      {obs-eid binding}
          c/binding-scar {obs-eid scars}})
       {}))})
