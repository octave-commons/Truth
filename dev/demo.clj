(ns demo
  "Reproducible native-window tour over Truth's existing ECS and renderer.

   Nebula runs the real physics pipeline. Later arcs are frozen, labeled ECS
   fixtures for inspecting the existing render/UI surfaces, not formation runs."
  (:require [domain.arc :as arc]
            [domain.ecs.core :as ecs]
            [domain.ecs.components :as c]
            [domain.ecology :as ecology]
            [domain.genesis :as genesis]
            [domain.player :as player]
            [domain.stellar.seeder :as seeder]
            [infra.camera :as camera]
            [infra.dev.window :as window]
            [infra.menu :as menu]
            [infra.render :as render]
            [law.stellar :as stellar]
            [law.ecology.schema :as eco-schema]
            [nrepl.server :as nrepl]))

(def scenarios
  "Tour order and expected narrative arcs."
  [[:nebula :arc/genesis-nebula-collapse]
   [:protostar :arc/genesis-protostar]
   [:ignition :arc/genesis-ignition]
   [:accretion :arc/genesis-accretion]
   [:planets :arc/genesis-planets-formed]
   [:life :arc/life-emergence]
   [:dispersed :arc/genesis-dispersed]])

(defn- empty-genesis []
  (let [world (genesis/create-world {:gas-count 1})]
    (reduce ecs/despawn world (ecs/entities-with world c/matter-state))))

(defn- add-body [world state mass radius temperature position]
  (seeder/spawn-clump world
                      {:matter-state state :mass mass :radius radius
                       :temperature temperature :position position
                       :body-kind :body/rock :angular-momentum [0.0 0.0 0.0]}))

(defn- stellar-world [state]
  (let [[world eid] (add-body (empty-genesis) state stellar/solar-mass
                              stellar/solar-radius
                              (if (= state :star) 5778.0 1600.0) [0.0 0.0 0.0])]
    [(ecs/put-component world eid c/luminosity
                        (if (= state :star) stellar/solar-luminosity 1.0e25)) eid]))

(defn- planetary-world [living?]
  (let [[world _] (stellar-world :star)
        [world eid] (add-body world :planet stellar/earth-mass 6.371e6
                              290.0 [stellar/au 0.0 0.0])
        world (-> world
                  (ecs/put-component eid c/planet-type :terrestrial)
                  (ecs/put-component eid c/composition
                                     {:Fe 0.32 :Ni 0.02 :Si 0.30 :Mg 0.20
                                      :O 0.10 :H 0.05 :He 0.01}))
        eco (ecology/make-ecology {:phase :prokaryotic :seeded true
                                   :moisture 0.7 :biomass 0.3 :complexity 0.1})]
    (assert (every? (fn [[k pred]] (pred (get eco k))) eco-schema/ecology-schema))
    [(cond-> world living? (ecs/put-component eid c/ecology eco)) eid]))

(defn- accretion-world []
  (let [[world star] (stellar-world :star)
        [world _] (add-body world :planetesimal 1.0e22 1.0e6 600.0 [stellar/au 0.0 0.0])
        [world _] (add-body world :planetesimal 2.0e22 1.5e6 400.0 [0.0 (* 1.7 stellar/au) 0.0])]
    [(-> world
         (ecs/put-component star c/disk-mass 1.0e28)
         (ecs/put-component star c/disk-angular-mom [0.0 0.0 1.0e43])) star]))

(defn scenario-world
  "Construct and validate one tour world; return world and camera subject."
  [id]
  (when-not (contains? (set (map first scenarios)) id)
    (throw (ex-info "Unknown demo scenario" {:scenario id})))
  (let [[world subject] (case id
                          :nebula [(genesis/create-world) nil]
                          :protostar (stellar-world :protostar)
                          :ignition (stellar-world :star)
                          :accretion (accretion-world)
                          :planets (planetary-world false)
                          :life (planetary-world true)
                          :dispersed [(empty-genesis) nil])
        summ (genesis/system-summary world)
        world (-> world
                  genesis/assert-seed-contracts!
                  (assoc :genesis/_prev-summary summ
                         :genesis/stats (genesis/stats-of world summ))
                  arc/advance-arc
                  (assoc :demo/scenario id :demo/fixture? (not= id :nebula)))]
    (assert (= (:arc/current world) (second (first (filter #(= id (first %)) scenarios)))))
    {:world (cond-> world
              (not= id :nebula) (update :arc/quest #(str "STAGED ECS FIXTURE: " %)))
     :subject subject}))

(defn select!
  "Replace only the demo service's world and frame the requested scenario."
  [id]
  (let [{:keys [world subject]} (scenario-world id)
        {:keys [config camera] :as service} @window/service-state
        r (when subject (ecs/get-component world subject c/radius))]
    (when-not service (throw (ex-info "Demo window is not running" {})))
    (swap! config assoc :tick-fn identity :on-step identity)
    ;; Replacement rides the same intent queue as input: the sim thread owns
    ;; publication, so an in-flight tick cannot overwrite the replacement.
    (reset! (:world-intents service) world)
    (loop [attempt 0]
      (when-not (= id (:demo/scenario @(:world service)))
        (when (>= attempt 300)
          (throw (ex-info "Simulation did not accept demo world" {:scenario id})))
        (Thread/sleep 100)
        (recur (inc attempt))))
    (swap! config #(-> %
                       (dissoc :selection :follow-eid :ui/active-domain :ui/error-state :zoom-min)
                       (assoc :ui/cursor-free? true :volumetric? (= id :nebula)
                              :mode (if subject :follow-selection :fit-all)
                              :tick-fn (if (= id :nebula) arc/tick-genesis identity))))
    (when subject
      (swap! config menu/apply-action [:ui/select-entity subject])
      (reset! camera (-> (camera/make-camera (* 5.0 (/ r camera/phase0-view-scale)))
                         (assoc :target (mapv #(/ % camera/phase0-view-scale)
                                              (ecs/get-component world subject c/position)))
                         camera/update-camera-position)))
    {:scenario id :fixture? (:demo/fixture? world) :arc (:arc/current world)}))

(defn closeup!
  "Place the observer at the selected fixture body for a true-scale macro view.

   Uses the normal manual camera. The inspector/follow view has a minimum
   approach distance; this explicit fixture view isolates the globe for capture."
  []
  (let [{:keys [world world-intents config camera]} @window/service-state
        eid (:selection @config)
        pos (ecs/get-component @world eid c/position)
        radius (ecs/get-component @world eid c/radius)]
    (when-not (and (:demo/fixture? @world) pos radius)
      (throw (ex-info "Select a fixture body before requesting a closeup" {})))
    (swap! world-intents ecs/put-component (player/observer-entity @world) c/position pos)
    (swap! config #(-> % (dissoc :selection :follow-eid :ui/active-domain :zoom-min)
                       (assoc :mode :manual)))
    (reset! camera (-> (camera/make-camera (* 5.0 (/ radius camera/phase0-view-scale)))
                       (assoc :target (mapv #(/ % camera/phase0-view-scale) pos))
                       camera/update-camera-position))
    :fixture-closeup))

(defn orbit!
  "Orbit the current native camera for eight seconds; physics policy is unchanged."
  []
  (let [camera (:camera @window/service-state)]
    (dotimes [_ 160]
      (swap! camera #(-> % (update :yaw + 0.35) camera/update-camera-position))
      (Thread/sleep 50))))

(defn panel!
  "Open an existing menu panel through its normal action reducer."
  [id]
  (when-not (contains? (set (map :id menu/domains)) id)
    (throw (ex-info "Unknown demo panel" {:panel id})))
  (swap! (:config @window/service-state) dissoc :ui/active-domain)
  (swap! (:config @window/service-state) menu/apply-action [:ui/toggle-domain id])
  id)

(defn -main
  "List/check scenarios or serve the native demo with nREPL on loopback port 7890."
  [& [command scenario]]
  (try
    (case command
      "list" (doseq [[id expected] scenarios] (println (name id) expected))
      "check" (doseq [[id _] scenarios]
                (println id (select-keys (:world (scenario-world id))
                                         [:arc/current :demo/fixture?])))
      "serve" (let [{:keys [world]} (scenario-world :nebula)
                    server (nrepl/start-server :bind "127.0.0.1" :port 7890)]
                (window/start! (atom world) {:tick-fn arc/tick-genesis
                                             :bodies-fn render/phase0-bodies+fields
                                             :camera (camera/make-camera 60.0)
                                             :width 1280 :height 720})
                (select! (keyword (or scenario "nebula")))
                (.addShutdownHook (Runtime/getRuntime)
                                  (Thread. #(do (window/stop!) (nrepl/stop-server server))))
                (println "Truth demo ready: nREPL 127.0.0.1:7890")
                @(promise))
      (throw (ex-info "Usage: clojure -M:demo list|check|serve [scenario]" {})))
    (finally (shutdown-agents))))
