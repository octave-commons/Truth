(ns support.worlds
  "Shared test-world fixtures and fan-out helpers.

   NOT a test namespace — it defines no `deftest`, and its name deliberately
   does not end in `-test` so `cognitect.test-runner` (which discovers suites by
   the `.*-test$` namespace pattern) will not treat it as one.

   Why this exists: 59 of the tree's 74 jscpd clones were in `test/`, because
   there was no shared fixture namespace at all — every focus/promotion test
   rebuilt the same observer world and the same write-set helpers verbatim. Card:
   `kanban/tasks/static-analysis-jscpd-test-fixtures.md`.

   The helpers here reproduce PRODUCTION timing exactly — a system's `:run` is
   invoked against a frozen world and its write-set folded at one barrier, which
   is what `domain.ecs.tick/run-parallel` does. Do not add a helper that folds
   writes as it goes; that would let a test pass on Jacobi-inconsistent state
   the real tick can never produce."
  (:require
   [domain.ecs.core :as ecs]
   [domain.player :as player]
   [shape.spatial :as sp]))

(def sample-ledger
  "A canonical regional-cell statistical ledger: 1e27 kg of cold H/He gas with
   non-zero linear and angular momentum and a weak seeded field.

   Every component is deliberately non-zero so a conservation test cannot pass
   by dropping a term."
  {:mass             1.0e27
   :velocity         (sp/vec3 1.0 2.0 3.0)
   :angular-momentum (sp/vec3 0.0 0.0 1.0e30)
   :mean-b           (sp/vec3 0.0 0.0 1.0e-9)
   :temperature      15.0
   :composition      {:H 0.74 :He 0.24 :Z 0.02}})

(defn world-with-observer
  "An empty world with an observer at the origin, attention shell overridden to
   `immediate-r` (m) and a regional shell at 4x that."
  [immediate-r]
  (let [[w _eid] (player/spawn-observer (ecs/empty-world) (sp/vec3 0.0 0.0 0.0))]
    (player/update-observer w
                            (fn [obs]
                              (assoc obs
                                     :focus-position (sp/vec3 0.0 0.0 0.0)
                                     :attention-shell {:immediate-r immediate-r
                                                       :regional-r (* 4.0 immediate-r)})))))

(defn run-system
  "Run `system`'s `:run` fn against `world` and return its write-set.

   `system` is the constructed system map (e.g.
   `(promotion/focus-zone-system)`), not a system id — the caller names the
   system it is testing, so this helper never has to know the registry."
  [system world]
  ((:run system) world))

(defn apply-write-set
  "Fold a write-set `{component {eid value}}` onto `world` as component writes —
   the same landing the tick performs when it materializes a fan-out write-set."
  [world ws]
  (reduce-kv (fn [w ct entries]
               (reduce-kv (fn [w' eid v] (ecs/put-component w' eid ct v)) w entries))
             world ws))
