(ns adaptive-material
  "Record a continuous material experiment and a detailed shadow continuation."
  (:require [clojure.java.io :as io] [clojure.edn :as edn] [clojure.string :as str]
            [material-experiment :as exp] [domain.genesis :as genesis]
            [domain.material.resolution :as resolution]
            [domain.material :as material] [domain.ecs.core :as ecs]
            [domain.ecs.components :as c] [shape.spatial :as sp]))

(defn json
  "Encode this experiment's finite EDN value subset as JSON."
  [x]
  (cond (nil? x) "null" (string? x) (pr-str x) (keyword? x) (pr-str (name x))
        (boolean? x) (str x) (number? x) (str x)
        (map? x) (str "{" (str/join "," (map (fn [[k v]] (str (json (if (keyword? k) (name k) (str k))) ":" (json v))) x)) "}")
        (sequential? x) (str "[" (str/join "," (map json x)) "]")
        :else (throw (ex-info "Unsupported JSON value" {:value x}))))

(defn snapshot!
  "Save exact ECS columns and a read-only smooth-surface input, including cached cells."
  [out index world phase]
  (let [cells (resolution/observed-cells world)
        data {:tick (:tick world) :time-s (:genesis/sim-time world) :phase phase
              :account (resolution/account world)
              :handoffs (:material/handoffs world [])
              :cells (mapv #(select-keys % [:source-id :p :v :r :m :cell :damage :cached?]) cells)}]
    (spit (format "%s/frame-%04d.edn" out index)
          (pr-str (select-keys world [:components :tick :genesis/sim-time :material/view-scale :material/handoffs])))
    (spit (format "%s/frame-%04d.json" out index) (json data))))

(defn projectile
  "Declared experimental intervention: add three finite iron carriers on an incoming track."
  [world speed distance]
  (let [items (resolution/observed-cells world) mass (reduce + (map :m items))
        center (sp/v* (reduce sp/v+ [0.0 0.0 0.0] (map #(sp/v* (:p %) (:m %)) items)) (/ 1.0 mass))
        vel (sp/v* (reduce sp/v+ [0.0 0.0 0.0] (map #(sp/v* (:v %) (:m %)) items)) (/ 1.0 mass))]
    (reduce (fn [w offset]
              (exp/add-cell w (sp/v+ center (sp/v+ [(- distance) 20.0 30.0] offset))
                            (sp/v+ vel [speed 0.0 0.0]) {:iron 1.0} 40.0 205.0))
            world [[0.0 0.0 0.0] [-40.0 0.0 0.0] [0.0 40.0 0.0]])))

(defn checkpoint
  "Persist physical ECS state and declared parameters, excluding runtime handlers/caches."
  [world]
  (select-keys world
               (concat [:components :alive :archetypes :next-id :tick
                        :genesis/sim-time :genesis/active :genesis/adaptive-pacing?]
                       (filter #(#{"material" "sim"} (namespace %)) (keys world)))))

(defn shadow!
  "Compare cached translation with detailed evolution from exactly the same state."
  [world out]
  (spit (str out "/coarse-entry.edn") (pr-str (checkpoint world)))
  (let [proxy (first (ecs/entities-with world c/material-packet))
        detailed (assoc (resolution/unpack world proxy) :material/coarse-enabled? false)
        coarse (assoc world :material/max-coarse-seconds 1e9 :material/drift-cell-fraction 1e9)
        steps (long (/ 30.0 (:sim/dt world)))
        bench (fn [w]
                (let [start (System/nanoTime)
                      result (nth (iterate genesis/tick-world w) steps)]
                  {:world result :seconds (/ (- (System/nanoTime) start) 1e9)}))
        a (bench detailed) b (bench coarse)
        ca (resolution/observed-cells (:world a)) cb (resolution/observed-cells (:world b))
        errors (map #(sp/dist (:p %1) (:p %2)) ca cb)
        diagnostic {:interval-s 30.0 :steps steps :fine-wall-s (:seconds a) :coarse-wall-s (:seconds b)
                    :body-count-fine (:physics-bodies (resolution/account detailed))
                    :body-count-coarse (:physics-bodies (resolution/account coarse))
                    :coarse-final-bodies (:physics-bodies (resolution/account (:world b)))
                    :coarse-final-packets (:coarse-bodies (resolution/account (:world b)))
                    :rms-position-error-m (Math/sqrt (/ (reduce + (map #(* % %) errors)) (count errors)))
                    :max-position-error-m (reduce max errors)
                    :cell-edge-m (get-in (first ca) [:cell :edge-m])
                    :fine-energy-change-J (- (:energy-J (resolution/account (:world a))) (:energy-J (resolution/account detailed)))
                    :coarse-energy-change-J (- (:energy-J (resolution/account (:world b))) (:energy-J (resolution/account coarse)))}]
    (spit (str out "/shadow.json") (json diagnostic))
    (println :shadow diagnostic) (flush)))

(defn -main
  "Assemble, introduce one slow impact, wait for quiet classification, then disturb again."
  [& [out]]
  (.mkdirs (io/file out))
  (try
    (loop [w (assoc (exp/initial-world) :sim/dt 0.5
                   :material/coarse-enabled? false :material/quiet-rms-m-s 0.045
                   :material/quiet-binding-ratio 0.05 :material/quiet-seconds 40.0
                   :material/lookahead-s 180.0)
           i 0 frame 0 first-impact? false second-impact? false coarse-at nil done-at nil]
      (let [t (:genesis/sim-time w) handoffs (:material/handoffs w [])
            coarse? (seq (ecs/entities-with w c/material-packet))
            coarse-at (or coarse-at (when coarse? t))
            first-now? (and (not first-impact?) (>= t 2400.0))
            second-now? (and coarse-at (not second-impact?) (>= t (+ coarse-at 30.0)))
            phase (cond second-impact? "reactivated impact and debris"
                        coarse? "coarse aggregate / retained geometry"
                        first-impact? "slow impact, reaccretion and settling"
                        :else "material assembly")
            sample? (or (zero? (mod i 60)) first-now? second-now?
                        (and (seq handoffs) (= (:tick w) (:tick (last handoffs)))))
            _ (when sample? (snapshot! out frame w phase))
            _ (when (zero? (mod i 600))
                (println :progress i t phase (select-keys (resolution/account w) [:physics-bodies :active-cells :coarse-bodies])
                         :handoffs (count handoffs)) (flush))
            _ (when (and coarse? (= t coarse-at)) (shadow! w out))
            w (cond first-now? (-> (projectile w 0.12 260.0)
                                   (assoc :sim/dt 0.25 :material/coarse-enabled? false))
                    second-now? (projectile w 2.5 650.0)
                    :else w)
            w (cond-> w (and first-impact? (> t 3000.0)) (assoc :material/coarse-enabled? true))
            done-at (or done-at (when second-now? (+ t 400.0)))]
        (cond
          (and done-at (>= t done-at))
          (do (snapshot! out (inc frame) w "post-impact material")
              (spit (str out "/final.edn") (pr-str (checkpoint w)))
              (spit (str out "/handoffs.json") (json (:material/handoffs w [])))
              (println :complete t :handoffs (:material/handoffs w)))
          (> t 7000.0)
          (do (spit (str out "/incomplete.edn") (pr-str w))
              (throw (ex-info "Quiet classifier did not reach the requested cycle" {:time t})))
          :else (recur (genesis/tick-world w) (inc i) (if sample? (inc frame) frame)
                       (or first-impact? first-now?) (or second-impact? second-now?) coarse-at done-at))))
    (finally (shutdown-agents))))
