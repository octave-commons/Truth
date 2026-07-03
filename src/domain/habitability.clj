(ns domain.habitability
  "Cross-epoch habitability scoring over the ECS substrate. Whether a resolved
   world can harbour life is not a property of the formation loop — it can be
   asked at any point in the game — so the scoring and candidate search live
   here rather than in the genesis loop. Reads the genesis world summary and
   defers the per-body score to the chemistry model.

   Pure data transformation; no IO."
  (:require
   [domain.chemistry :as chemistry]
   [domain.genesis   :as genesis]))

(defn habitability-of
  "Habitability score of a resolved body region for the chemistry model."
  [region]
  (chemistry/habitability-score region))

(defn habitable-worlds
  "Resolved planet regions with non-trivial habitability potential — the
   candidate worlds for the soft handoff toward a narrower perspective (see
   `domain.arc/ready-to-narrow?`)."
  [world]
  (->> (:planets (genesis/system-summary world))
       (filter #(> (habitability-of %) 0.2))))
