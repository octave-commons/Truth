(ns domain.commitment-test
  "The First Narrowing, child B: the commitment horizon — capture at
   binding >= law/capture-threshold + ready-to-commit?, the canonical
   :event/world-commitment threshold event, palette re-arm, inert unchosen
   worlds, and the planetary time-lock data hook
   (kanban/tasks/narrowing-commitment-horizon.md; design
   docs/designs/the-first-narrowing-star-to-planet.md §3-4,
   docs/designs/commitment-and-resonance.md §4.2, §5.1). Fixtures follow
   domain.narrowing-test: a frozen world, the system's :run called directly,
   assertions on the write-set; the serial ledger emit
   (domain.genesis.tick/emit-commitment-event) is called directly on the
   folded world, the emit-handoff-event precedent."
  (:require
   [clojure.test :refer [deftest is testing]]
   [domain.ecs.components :as c]
   [domain.ecs.core :as ecs]
   [domain.ecs.event :as event]
   [domain.ecs.registry :as reg]
   [domain.genesis.tick :as tick]
   [domain.narrowing :as narrowing]
   [domain.player :as player]
   [law.narrowing :as law]
   [shape.spatial :as sp]))

(defn- world-with-observer
  "An empty world with an observer at the origin and the arc at
   `:arc/genesis-planets-formed` (ready-to-commit?'s arc half). Returns
   [world obs-eid]."
  []
  (let [[w obs-eid] (player/spawn-observer (ecs/empty-world) (sp/vec3 0.0 0.0 0.0))]
    [(assoc w :arc/current :arc/genesis-planets-formed)
     obs-eid]))

(defn- spawn-candidate
  "Spawn a candidate world (c/planet-candidate + c/position) at `pos`.
   Returns [world eid]."
  [world pos]
  (let [[w eid] (ecs/spawn world)]
    [(ecs/put-components w eid {c/planet-candidate {:planet-id eid}
                                c/position         pos})
     eid]))

(defn- run-system
  "Run the commitment system's :run fn directly and return its write-set."
  [world]
  ((:run (narrowing/commitment-system)) world))

(defn- apply-write-set
  "Fold a write-set {ctype {eid value}} into the world — a test-local mimic of
   the end-of-tick fold."
  [world ws]
  (reduce-kv (fn [w ctype eid->v]
               (reduce-kv (fn [w' eid v] (ecs/put-component w' eid ctype v))
                          w eid->v))
             world ws))

(defn- bound-world
  "A ready world: observer, arc at planets-formed, one candidate bound on the
   observer at `binding-value`. Returns [world obs-eid weid]."
  [binding-value]
  (let [[w0 obs-eid] (world-with-observer)
        [w1 weid] (spawn-candidate w0 (sp/vec3 1.0e10 0.0 0.0))
        w (ecs/put-component w1 obs-eid c/binding {weid binding-value})]
    [w obs-eid weid]))

(deftest capture-emits-commitment-at-threshold
  (testing "binding at/above capture-threshold on a ready world: the write-set
            stamps :committed, re-arms the palette, and engages the time-lock"
    (let [[w obs-eid weid] (bound-world 0.9)
          ws (run-system w)]
      (is (= :committed (get-in ws [c/commitment-state weid]))
          "the captured world is marked :committed")
      (is (= law/planetary-palette (get-in ws [c/palette obs-eid]))
          "the observer's slots re-arm in place to the planetary palette")
      (is (law/time-lock? (get-in ws [c/time-lock weid]))
          "the committed world carries a valid time-lock record")
      (testing "the serial post-fold step appends the canonical §4.2 event"
        (let [folded (-> (apply-write-set w ws)
                         event/with-ledger
                         tick/emit-commitment-event)
              events (vec (event/events-of-kind folded :event/world-commitment))]
          (is (= 1 (count events)) "exactly one world-commitment event")
          (is (= {:world  weid
                  :arc    :arc/genesis-planets-formed
                  :reason :habitable}
                 (get-in (first events) [:payload :data]))
              "the payload is the §4.2 contract under :data (emit-threshold shape)"))))))

(deftest no-commit-below-threshold
  (testing "binding below capture-threshold: no capture, empty write-set"
    (let [[w _ _] (bound-world 0.5)]
      (is (= {} (run-system w)))))
  (testing "binding just below the horizon does not cross it"
    (let [[w _ _] (bound-world (- law/capture-threshold 1.0e-9))]
      (is (= {} (run-system w))))))

(deftest no-commit-when-not-ready
  (testing "binding past threshold but the arc has not reached planet
            formation: no capture"
    (let [[w0 obs-eid] (world-with-observer)
          w1 (assoc w0 :arc/current :arc/genesis-accretion)
          [w2 weid] (spawn-candidate w1 (sp/vec3 1.0e10 0.0 0.0))
          w (ecs/put-component w2 obs-eid c/binding {weid 0.95})]
      (is (= {} (run-system w)))))
  (testing "ready-to-commit? degrades honestly: arc gate + M5 candidate record"
    (let [[w _ weid] (bound-world 0.9)]
      (is (narrowing/ready-to-commit? w weid))
      (is (not (narrowing/ready-to-commit? (assoc w :arc/current :arc/genesis-ignition) weid)))
      (is (not (narrowing/ready-to-commit? w 99999))
          "a world without the M5 candidate record is not a stabilized candidate"))))

(deftest palette-rearms-on-commit
  (testing "the six slots re-arm IN PLACE to Atmosphere/Hydrography/Tectonics/
            Orbit/Biosphere/Culture; Resonance carries over untouched"
    (let [[w0 obs-eid] (world-with-observer)
          w1 (player/update-observer w0 #(assoc % :resonance 7.0))
          [w2 weid] (spawn-candidate w1 (sp/vec3 1.0e10 0.0 0.0))
          w (ecs/put-component w2 obs-eid c/binding {weid 0.9})
          ws (run-system w)
          palette (get-in ws [c/palette obs-eid])]
      (is (= :planetary (:active palette)))
      (is (= {1 :atmosphere 2 :hydrography 3 :tectonics
              4 :orbit 5 :biosphere 6 :culture}
             (:slots palette))
          "same six slot keys, planetary abilities")
      (is (law/palette? palette) "the re-armed palette satisfies the law schema")
      (is (not (contains? ws c/observer))
          "the observer component is NOT written: :resonance carries over")
      (is (= 7.0 (:resonance (player/get-observer (apply-write-set w ws))))
          "Resonance is still the observer's after the fold"))))

(deftest commitment-fires-exactly-once
  (testing "once :committed exists the system emits nothing, forever — the
            horizon cannot be re-crossed, and the ledger event cannot double"
    (let [[w _ weid] (bound-world 0.9)
          ws1 (run-system w)
          folded (apply-write-set w ws1)]
      (is (some? (get-in ws1 [c/commitment-state weid])) "first tick captures")
      (is (= {} (run-system folded))
          "second tick: a :committed world anywhere short-circuits the system")
      (testing "un-binding post-capture is impossible: even with binding gone,
                commitment-state is never rewritten or cleared"
        (let [unbound (ecs/put-component folded (first (ecs/entities-with folded c/observer)) c/binding {})]
          (is (= {} (run-system unbound)))
          (is (= :committed (ecs/get-component unbound weid c/commitment-state)))))
      (testing "the serial emit is idempotent across ticks"
        (let [once (-> folded event/with-ledger tick/emit-commitment-event)
              twice (tick/emit-commitment-event once)]
          (is (= 1 (count (event/events-of-kind twice :event/world-commitment)))))))))

(deftest unchosen-worlds-marked-inert
  (testing "every unchosen candidate world is marked :inert in the same
            write-set — visible, no longer interactive"
    (let [[w0 obs-eid] (world-with-observer)
          [w1 wa] (spawn-candidate w0 (sp/vec3 1.0e10 0.0 0.0))
          [w2 wb] (spawn-candidate w1 (sp/vec3 2.0e10 0.0 0.0))
          w (ecs/put-component w2 obs-eid c/binding {wa 0.9 wb 0.4})
          ws (run-system w)]
      (is (= :committed (get-in ws [c/commitment-state wa])))
      (is (= :inert (get-in ws [c/commitment-state wb]))
          "the unchosen candidate goes non-interactive")
      (is (law/commitment-state? (get-in ws [c/commitment-state wb])))))
  (testing "two worlds both past threshold: the deepest well captures, the
            other goes inert — you can only fall one way"
    (let [[w0 obs-eid] (world-with-observer)
          [w1 wa] (spawn-candidate w0 (sp/vec3 1.0e10 0.0 0.0))
          [w2 wb] (spawn-candidate w1 (sp/vec3 2.0e10 0.0 0.0))
          w (ecs/put-component w2 obs-eid c/binding {wa 0.9 wb 0.95})
          ws (run-system w)]
      (is (= :committed (get-in ws [c/commitment-state wb])))
      (is (= :inert (get-in ws [c/commitment-state wa]))))))

(deftest time-lock-engaged
  (testing "capture stamps the §5.1 planetary time-lock record on the
            committed world: base rate 1 s/s, immediate neighborhood,
            everything outside sub-cycled"
    (let [[w _ weid] (bound-world 0.9)
          ws (run-system w)
          lock (get-in ws [c/time-lock weid])]
      (is (= {:locked?       true
              :captured-tick 0
              :base-rate     1.0
              :neighborhood  :immediate
              :outside       :sub-cycled}
             lock))
      (is (law/time-lock? lock)))))

(deftest single-writer-preserved
  (testing "the new components each have exactly one writer across the whole
            registry; the invariant still holds"
    (is (= {} (reg/write-conflicts reg/systems)))
    (is (= [:commitment] (get (reg/writers-by-component reg/systems) c/commitment-state)))
    (is (= [:commitment] (get (reg/writers-by-component reg/systems) c/palette)))
    (is (= [:commitment] (get (reg/writers-by-component reg/systems) c/time-lock)))
    (is (= (reg/registry-writes :commitment)
           (:writes (narrowing/commitment-system)))
        "the emitter's :writes is sourced from the registry, not restated")))
