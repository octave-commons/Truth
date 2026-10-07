;; Offline derivation from immutable captured metadata, never a runtime form.
(require '[clojure.edn :as edn] '[clojure.set :as set])
(def base ".ημ/diagnostics/body-trails/hardware-follow-2026-10-07/")
(defn read-edn [path] (edn/read-string {:default tagged-literal} (slurp (str base path))))
(def frames (mapv #(read-edn (format "captures/pid-131581-frame-%02d.edn" %)) (range 1 7)))
(defn target [frame] (first (vals (:targets frame))))
(defn alpha-by-time [frame]
  ;; Match recorded vertex positions to recorded physical samples using the
  ;; exact production units.clj:world->render coordinate conversion (scale1e15).
  ;; No trail generation/classification is reproduced here: actual vertices
  ;; were already compared to production project-trails on the render thread.
  (let [t (target frame)
        alphas (into {} (map (juxt :position :alpha) (:actual-vertices t)))]
    (into {} (map (fn [sample]
                    [(:time sample) (get alphas (mapv #(/ (double %) 1.0e15) (:position sample)))])
                  (get-in t [:history :samples])))))
(defn frame-summary [frame]
  (let [t (target frame) vertices (:actual-vertices t)
        points (keep :framebuffer vertices)
        xs (map first points) ys (map second points)
        alphas (map :alpha vertices)]
    {:capture (:capture frame) :kind (:active-kind frame) :entity (:eid t)
     :projection-input (:projection-input frame)
     :separate-published-read (:published-read-after-scene frame)
     :framebuffer [(:width frame) (:height frame)] :wall-ms (:wall-ms frame)
     :history-samples (count (get-in t [:history :samples]))
     :selected-vertex-count (count vertices)
     :selected-screen-bounds {:x [(apply min xs) (apply max xs)]
                              :y [(apply min ys) (apply max ys)]}
     :selected-alpha-range [(apply min alphas) (apply max alphas)]
     :budget (:budget frame)
     :actual-trails-equal-production-projection? (:actual-trails-equal-production-projection? frame)
     :gl (:gl frame)}))
(defn aging [group]
  (let [[a b c] group maps (mapv alpha-by-time group)
        times (mapv (comp set keys) maps)
        chosen-time (:time (last (get-in (target a) [:history :samples])))
        missing (mapv #(count (filter nil? (vals %))) maps)]
    (assert (every? zero? missing) "Recorded sample does not match an actual projected vertex")
    {:entity (:eid (target a)) :captures (mapv :capture group)
     :sim-seconds-first-to-half (- (get-in b [:projection-input :genesis/sim-time])
                                  (get-in a [:projection-input :genesis/sim-time]))
     :sim-seconds-first-to-last (- (get-in c [:projection-input :genesis/sim-time])
                                  (get-in a [:projection-input :genesis/sim-time]))
     :first-history-times-retained-in-half (count (set/intersection (first times) (second times)))
     :first-history-times-retained-in-last (count (set/intersection (first times) (last times)))
     :tracked-real-sample-time chosen-time
     :recorded-alpha-by-capture (mapv #(get % chosen-time :not-retained) maps)
     :all-retained-first-to-half-alphas-decrease?
     (every? (fn [time] (< (get (second maps) time) (get (first maps) time)))
             (set/intersection (first times) (second times)))}))
(let [audit (read-edn "restore-audit.stdout")
      state (get-in audit [:restore :state])
      result {:evidence-tier :derived-from-recorded-native-metadata
              :frames (mapv frame-summary frames)
              :aging [(aging (subvec frames 0 3)) (aging (subvec frames 3 6))]
              :active-wall-ms (- (:closed-wall-ms state) (:started-wall-ms state))
              :state (select-keys state [:phase :active? :capture-attempts :errors
                                         :camera-restored? :view-fields-restored?
                                         :scene-root-restored? :bodies-config-restored?
                                         :body-calls :scene-calls :unpaired-frames :raw-gl-samples])
              :post-restoration-audit (:audit audit)
              :limits "Geometry/age-out and visual readability are separate; diagnostic follow changes attention; no manual/FPS/Gate claim."}]
  (spit (str base "derived.edn") (str (pr-str result) "\n"))
  (prn (select-keys result [:aging :active-wall-ms :state])))
