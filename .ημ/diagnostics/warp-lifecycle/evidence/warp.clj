(ns evidence.warp
  "Finite warp lifecycle costs using the existing Criterium and phase0 adapters."
  (:require
   [clojure.edn :as edn]
   [clojure.java.io :as io]
   [criterium.core]
   [domain.ecs.components :as c]
   [domain.ecs.tick :as tick]
   [domain.intervention :as iv]
   [domain.physics.cache :as cache]
   [domain.stellar.merge :as stellar-merge]
   [domain.warp-lifecycle-test :as fixture]
   [gates-of-truth.bench :as bench]
   [gates-of-truth.bench.phase0 :as phase0])
  (:import
   [java.nio.file Files]
   [java.security MessageDigest]
   [java.util HexFormat UUID]))

(def ^:private baseline-sha
  "e541dd5df5ff208818d216c9083b73693216409c73599b6ab63be0f641c83dc0")

(defn- sha256 [path]
  (.formatHex (HexFormat/of)
              (.digest (MessageDigest/getInstance "SHA-256")
                       (Files/readAllBytes (.toPath (io/file path))))))

(defn- write-new! [path data]
  (when (.exists (io/file path))
    (throw (ex-info "Evidence already exists" {:path path})))
  (let [text (str (pr-str data) "\n")
        readback (try {:value (edn/read-string text)}
                      (catch Exception error {:error error}))]
    (when-not (and (nil? (:error readback)) (= data (:value readback)))
      (let [rejected (str path ".rejected-" (UUID/randomUUID) ".edn")]
        (io/make-parents rejected)
        (spit rejected text)
        (throw (ex-info "Evidence failed exact EDN round-trip"
                        {:rejected rejected} (:error readback)))))
    (io/make-parents path)
    (spit path text)))

(defn- population [departing? ttl]
  (reduce (fn [world i]
            (let [leaving? (and departing? (even? i))]
              (first (#'fixture/body world
                                     (if leaving? [27.0 0.0 0.0] [5.0 0.0 0.0])
                                     (if leaving? [2.0 0.0 0.0] [0.0 0.0 0.0])))))
          (#'fixture/paid-world :warp/well ttl)
          (range 500)))

(def ^:private handler-reference
  {:callable 'domain.stellar.merge/stellar-merge-handler})

(defn- encode-phase0 [world]
  ;; The ordinary world contains one runtime callback. Preserve its identity
  ;; explicitly; reject any different/additional callable rather than dropping it.
  (assert (= #{:event/collision} (set (keys (:handlers world))))
          "Unexpected phase0 handler set")
  (assert (identical? stellar-merge/stellar-merge-handler
                      (get-in world [:handlers :event/collision]))
          "Unexpected collision handler")
  (assoc-in world [:handlers :event/collision] handler-reference))

(defn- decode-phase0 [data]
  (let [world (:phase0 data)]
    (assert (= (:handler-source-sha256 data)
               (sha256 "src/domain/stellar/merge.clj")) "Collision handler source changed")
    (assert (= {:event/collision handler-reference} (:handlers world))
            "Unexpected frozen handler representation")
    (assoc-in world [:handlers :event/collision] stellar-merge/stellar-merge-handler)))

(defn- capture! [path]
  (assert (= baseline-sha (sha256 "src/domain/intervention.clj"))
          "Capture fixtures only from unchanged baseline")
  (let [clean (assoc (population false 100) :genesis/interventions [])
        active (#'fixture/physics-step (population false 100) true)
        expired (#'fixture/physics-step (population false 1) true)
        partial-world (nth (iterate #(#'fixture/physics-step % true)
                                    (population true 100)) 2)]
    (write-new! path
                {:source-sha256 baseline-sha
                 :source-head "e434a8384e9e87342b0a1012b6b87b33acbb3a46"
                 :handler-source-sha256 (sha256 "src/domain/stellar/merge.clj")
                 :phase0 (encode-phase0 (#'phase0/make-medium-world))
                 :warp [[:clean clean] [:active active]
                        [:expired expired] [:partial partial-world]]})))

(defn- prepare [label world]
  (let [snapshot (cache/build-physics-soa world)
        run (:run (iv/warp-acceleration-system))
        ws (run snapshot)
        cell (get ws c/accel-warp {})
        live (into #{} (keep (fn [[eid value]]
                              (when-not (tick/removed? value) eid))) cell)
        prior (set (keys (get-in snapshot [:components c/accel-warp])))
        expected ({:clean [0 0] :active [500 500]
                   :expired [500 0] :partial [500 250]} label)
        actual [(count prior) (count live)]
        _ (assert (= expected actual) (str "Wrong workload " label " " actual))
        folded (tick/apply-write-set snapshot ws)
        observations {:label label :prior-count (count prior) :live-count (count live)
                      :removed-count (count (filter tick/removed? (vals cell)))
                      :folded-count (count (get-in folded [:components c/accel-warp]))
                      :paid-agency (-> world :components (get c/observer) vals first :agency)
                      :well-count (count (:genesis/interventions world))
                      :physical-state-unchanged?
                      (= (select-keys (:components snapshot)
                                      [c/position c/velocity c/mass c/accel-gravity])
                         (select-keys (:components folded)
                                      [c/position c/velocity c/mass c/accel-gravity]))}]
    (assert (= 85.0 (:paid-agency observations)) "Fixture did not pay placement cost")
    (assert (:physical-state-unchanged? observations) "Emitter/fold mutated another owner")
    {:observe observations
     :f #(tick/apply-write-set snapshot (run snapshot))}))

(defn- measure! [output label f]
  ;; Invoke the repository's existing quick benchmark adapter verbatim.
  (let [result (#'bench/quick-bench label f)
        counts (:outliers result)
        _ (assert (instance? criterium.core.OutlierCount counts)
                  "Unexpected Criterium outlier representation")
        metrics (-> (select-keys result [:mean :variance :lower-q :upper-q
                                         :sample-mean :sample-variance :samples
                                         :sample-count :execution-count :total-time
                                         :warmup-executions :warmup-time :options
                                         :outliers :outlier-variance])
                    (assoc :outliers {:record-type "criterium.core.OutlierCount"
                                      :fields (into {} counts)}))]
    (write-new! (str output "/" label ".edn") {:label label :criterium metrics})
    label))

(defn- benchmark! [action fixtures output]
  (let [data (edn/read-string (slurp fixtures))
        medium-world (decode-phase0 data)
        prepared (mapv (fn [[label world]] (prepare label world)) (:warp data))
        observations (mapv :observe prepared)
        _ (assert (= baseline-sha (:source-sha256 data)) "Unrecognized frozen fixture source")
        _ (when (= action "before")
            (assert (= baseline-sha (sha256 "src/domain/intervention.clj"))
                    "BEFORE source changed"))
        expected-folded (if (= action "before") [0 500 500 500] [0 500 0 250])
        _ (assert (= expected-folded (mapv :folded-count observations))
                  "Lifecycle outcome is not the declared BEFORE/AFTER behavior")
        selected (atom [])
        skipped (atom [])
        callback (fn [label f]
                   (if (= "tick-world (500 particles)" label)
                     (swap! selected conj (measure! output "phase0-500" f))
                     (swap! skipped conj label)))]
    (write-new! (str output "/inputs.edn")
                {:action action :fixtures-sha256 (sha256 fixtures)
                 :warp-source-sha256 (sha256 "src/domain/intervention.clj")
                 :observations observations
                 :phase0-requested-gas-count 500
                 :phase0-world-hash (hash (:phase0 data))})
    ;; Only replace the world factory with the EXACT captured output of that
    ;; factory. The registered closure and all ordinary group setup still run.
    ;; with-redefs restores the factory after the group, including exceptions.
    (with-redefs-fn {#'phase0/make-medium-world (constantly medium-world)}
      #(phase0/run callback callback))
    (assert (= ["phase0-500"] @selected) "Registered label selection changed")
    (doseq [{:keys [observe f]} prepared]
      (measure! output (name (:label observe)) f))
    (write-new! (str output "/coverage.edn")
                {:registered-selected @selected :registered-skipped @skipped
                 :warp-selected (mapv (comp :label :observe) prepared)
                 :claims "Five measured cases only; setup profiles are not new Criterium cases or FPS evidence."})))

(defn -main
  "Capture baseline fixtures or measure five finite cases; never touch a native service."
  [action fixtures output]
  (try
    (case action
      "capture" (capture! fixtures)
      "before" (benchmark! action fixtures output)
      "after" (benchmark! action fixtures output)
      (throw (ex-info "Unknown action" {:action action})))
    (finally (shutdown-agents))))
