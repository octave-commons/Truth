(ns infra.menu-test
  "Pure-logic tests for the top menu bar: action folding, hit-testing, and the
   layout invariants the window loop relies on."
  (:require
   [clojure.test :refer [deftest is testing]]
   [domain.ecs.core :as ecs]
   [domain.ecs.components :as c]
   [domain.player :as player]
   [domain.stellar :as stellar]
   [infra.camera :as cam]
   [infra.menu :as menu]))

(def base (cam/default-camera-settings))

(deftest toggle-domain-opens-and-closes
  (testing "toggling the same domain opens then closes it"
    (let [opened (menu/apply-action base [:ui/toggle-domain :view])
          closed (menu/apply-action opened [:ui/toggle-domain :view])]
      (is (= :view (:ui/active-domain opened)))
      (is (nil? (:ui/active-domain closed)))))
  (testing "switching domains replaces the active one"
    (let [cfg (-> base
                  (menu/apply-action [:ui/toggle-domain :view])
                  (menu/apply-action [:ui/toggle-domain :spark]))]
      (is (= :spark (:ui/active-domain cfg))))))

(deftest setting-scale-clamps
  (testing "look sensitivity scales down and clamps at the floor"
    (let [cfg (reduce (fn [c _] (menu/apply-action c [:setting/scale :look-sensitivity 0.8 0.005 1.0]))
                      base (range 100))]
      (is (>= (:look-sensitivity cfg) 0.005))
      (is (<= (:look-sensitivity cfg) 0.005001))))
  (testing "look sensitivity scales up and clamps at the ceiling"
    (let [cfg (reduce (fn [c _] (menu/apply-action c [:setting/scale :look-sensitivity 1.25 0.005 1.0]))
                      base (range 100))]
      (is (<= (:look-sensitivity cfg) 1.0))))
  (testing "one step actually moves the value"
    (let [before (:look-sensitivity base)
          after  (:look-sensitivity (menu/apply-action base [:setting/scale :look-sensitivity 0.8 0.005 1.0]))]
      (is (< after before)))))

(deftest cycle-mode-advances
  (is (not= (:mode base) (:mode (menu/apply-action base [:camera/cycle-mode])))))

(deftest unknown-action-is-identity
  (is (= base (menu/apply-action base [:no/such-action 1 2]))))

(deftest bar-hits-cover-every-domain
  (testing "every canonical domain has a clickable tab in the bar"
    (let [{:keys [hits]} (menu/menu-hud base {} 1280.0 720.0)
          toggles (->> hits (map :action) (filter #(= :ui/toggle-domain (first %))) (map second) set)]
      (is (= (set (map :id menu/domains)) toggles)))))

(deftest view-panel-exposes-steppers
  (testing "the open View panel yields -/+ hits for each camera setting plus a mode cycle"
    (let [cfg (menu/apply-action base [:ui/toggle-domain :view])
          {:keys [hits regions]} (menu/menu-hud cfg {} 1280.0 720.0)
          actions (set (map :action hits))]
      (is (contains? actions [:camera/cycle-mode]))
      (doseq [r menu/view-rows]
        (is (contains? actions (:dec r)) (str "missing dec for " (:key r)))
        (is (contains? actions (:inc r)) (str "missing inc for " (:key r))))
      (testing "the panel registers a mouse-capture region below the bar"
        (is (some #(> (double (:y1 %)) menu/bar-h) regions))))))

(deftest hit-and-region-testing
  (let [{:keys [hits regions]} (menu/menu-hud base {} 1280.0 720.0)
        first-tab (first hits)]
    (testing "a point inside the first tab resolves to its action"
      (let [cx (/ (+ (:x0 first-tab) (:x1 first-tab)) 2.0)
            cy (/ (+ (:y0 first-tab) (:y1 first-tab)) 2.0)]
        (is (= (:action first-tab) (:action (menu/hit-at hits cx cy))))))
    (testing "a point in the bar strip is over a capture region; one below is not"
      (is (menu/over-regions? regions 100.0 5.0))
      (is (not (menu/over-regions? regions 640.0 400.0))))))

(deftest entities-viewer-lists-resolved-bodies
  (testing "the Entities panel lists resolved bodies with formatted mass/radius"
    (let [[w _] (stellar/spawn-clump (ecs/empty-world)
                                     {:position [0.0 0.0 0.0]
                                      :mass 2e30 :radius 6.957e8
                                      :matter-state :star
                                      :temperature 5800.0})
          cfg (menu/apply-action base [:ui/toggle-domain :entities])
          {:keys [hits text]} (menu/menu-hud cfg w 1280.0 720.0)]
      (is (seq (filter #(re-find #"Star" (:text %)) text)) "panel shows a star row")
      (is (seq (filter #(= [:ui/select-entity 0] (:action %)) hits))
          "each row is clickable to select the entity")))
  (testing "select-entity action sets the config selection"
    (is (= 7 (:selection (menu/apply-action base [:ui/select-entity 7]))))))

(deftest spark-panel-formats-double-agency
  (testing "Spark panel accepts observer agency as a Double without throwing"
    (let [[w _] (player/spawn-observer (ecs/empty-world) [0.0 0.0 0.0])
          w     (-> w
                    (ecs/update-component 0 :component/observer
                                          #(-> %
                                               (assoc :coherence 50.0
                                                      :max-coherence 100.0
                                                      :agency 7.5
                                                      :focus-intensity 0.75)))
                    (assoc :next-id 1))
          cfg (menu/apply-action base [:ui/toggle-domain :spark])
          {:keys [text]} (menu/menu-hud cfg w 1280.0 720.0)]
      (is (seq (filter #(re-find #"Agency" (:text %)) text)) "panel shows agency line")
      (is (seq (filter #(re-find #"7 quanta" (:text %)) text)) "agency floors to integer quanta"))))
