;; Intentional: this test is intentionally a single-segment namespace because it
;; guards the project's top-level architectural invariants and is referenced as
;; such in CI and AGENTS.md.
#_{:splint/disable [naming/single-segment-namespace]}
(ns architecture-test
  "Structural guardrails that enforce the project's load-bearing invariants at
   test time, so an architectural regression fails CI rather than silently
   creating a second reality. See AGENTS.md › Architecture Invariants."
  (:require
   [clojure.test :refer [deftest testing is]]
   [clojure.java.io :as io]
   [clojure.string :as str]
   [domain.ecs.registry :as reg]))

(defn- clj-files [dir]
  (->> (io/file dir)
       file-seq
       (filter #(and (.isFile %) (str/ends-with? (.getName %) ".clj")))))

(deftest single-ecs-substrate
  (testing "Phase 0 runs on the ONE ECS world (domain.genesis) — no parallel sim"
    ;; A separate particle/array world model (the old domain.particles path,
    ;; marked by its own world keys) is forbidden: phases are content layers over
    ;; the single ECS substrate, never parallel simulations. If you are
    ;; reintroducing such a path, you are violating the core architecture — stop
    ;; and read AGENTS.md › Single Simulation Substrate.
    (let [forbidden #"domain\.particles|:genesis/mode|:genesis/field|:genesis/mesh"
          offenders (->> (clj-files "src")
                         (filter #(re-find forbidden (slurp %)))
                         (mapv #(.getPath %)))]
      (is (empty? offenders)
          (str "Parallel-world markers found in: " offenders))))

  (testing "domain.genesis is the sole Phase 0 world bootstrap"
    (is (.exists (io/file "src/domain/genesis.clj")))))

(deftest one-renderer
  (testing "There is a single Phase 0 renderer (infra.render), no orphan copy"
    (is (not (.exists (io/file "src/infra/render/phase0_renderer.clj")))
        "infra.render.phase0-renderer was consolidated into infra.render")))

(deftest domain-never-imports-infra
  (testing "Pure domain code never depends on infra (one-way dependency)"
    (let [offenders (->> (clj-files "src/domain")
                         (filter #(re-find #"\[\s*infra\.|require.*infra\." (slurp %)))
                         (mapv #(.getPath %)))]
      (is (empty? offenders)
          (str "domain namespaces importing infra: " offenders)))))

;; --- ECS single-writer ownership (double-buffer spec, §2 Rule 2) ------------
;; See docs/notes/2026.06.26-ecs-double-buffer-single-writer-spec.md.
;; The invariant: every component type has exactly one fan-out writer, so the
;; per-tick fan-out is conflict-free and lock-free. The migration is COMPLETE —
;; every contended component now has a single owner:
;;   position/velocity → motion; accel.* → gravity/hydro/em; pressure → eos;
;;   matter-state → classifier; accretion-radius → jeans; spin → em;
;;   radius/density/oblateness/rotation-axis → structure; temperature → thermal;
;;   b-field/frozen-flux → field. collapse and density-system are retired from
;;   the fan-out. The invariant is now ENFORCED (not a shrinking baseline).

(deftest system-registry-well-formed
  (testing "every registry entry has a unique :id and component-keyword reads/writes"
    (is (empty? (reg/malformed-entries reg/systems))
        (str "Malformed registry entries: " (reg/malformed-entries reg/systems)))))

(deftest single-writer-ownership-holds
  (testing "every component has exactly one fan-out writer — invariant enforced"
    (is (empty? (reg/write-conflicts reg/systems))
        (reg/format-conflicts (reg/write-conflicts reg/systems))))
  (testing "assert-single-writer! passes (the boot-time guard)"
    (is (= reg/systems (reg/assert-single-writer! reg/systems)))))

;; --- Genesis / arc separation (kanban/tasks/genesis-arc-separation-physics-substrate-vs-player-arc.md) --------
;; The physical genesis loop (domain.genesis) is portable and arc-agnostic; the
;; narrative arc (domain.arc) is a separate story-state layer; ongoing physics
;; that is not formation-specific lives with its proper owner.

(deftest ongoing-physics-lives-in-proper-owners
  (testing "systems evicted from the genesis loop resolve in their new namespaces"
    (require 'domain.atmosphere 'domain.lod 'domain.em 'domain.habitability
             'infra.input 'domain.arc)
    (is (some? (resolve 'domain.atmosphere/xuv-atmospheric-escape-system)))
    (is (some? (resolve 'domain.lod/lod-scheduler)))
    (is (some? (resolve 'domain.em/magnetosphere-coupling-system)))
    (is (some? (resolve 'domain.habitability/habitability-of)))
    (is (some? (resolve 'domain.habitability/habitable-worlds)))
    (is (some? (resolve 'infra.input/handle-input))))
  (testing "the narrative arc lives in domain.arc, not the physics loop"
    (is (some? (resolve 'domain.arc/detect-arc)))
    (is (some? (resolve 'domain.arc/genesis-ending)))
    (is (some? (resolve 'domain.arc/advance-arc))))
  (testing "the evicted names no longer live in domain.genesis"
    (require 'domain.genesis)
    (is (nil? (resolve 'domain.genesis/xuv-atmospheric-escape-system)))
    (is (nil? (resolve 'domain.genesis/lod-scheduler)))
    (is (nil? (resolve 'domain.genesis/magnetosphere-coupling-system)))
    (is (nil? (resolve 'domain.genesis/handle-input)))
    (is (nil? (resolve 'domain.genesis/habitable-worlds)))
    (is (nil? (resolve 'domain.genesis/detect-phase)))
    (is (nil? (resolve 'domain.genesis/world-ending)))))
