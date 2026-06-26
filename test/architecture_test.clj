(ns architecture-test
  "Structural guardrails that enforce the project's load-bearing invariants at
   test time, so an architectural regression fails CI rather than silently
   creating a second reality. See AGENTS.md › Architecture Invariants."
  (:require
   [clojure.test :refer [deftest testing is]]
   [clojure.java.io :as io]
   [clojure.string :as str]))

(defn- clj-files [dir]
  (->> (io/file dir)
       file-seq
       (filter #(and (.isFile %) (str/ends-with? (.getName %) ".clj")))))

(deftest single-ecs-substrate
  (testing "Phase 0 runs on the ONE ECS world (domain.phase0) — no parallel sim"
    ;; A separate particle/array world model (the old domain.particles path,
    ;; marked by its own world keys) is forbidden: phases are content layers over
    ;; the single ECS substrate, never parallel simulations. If you are
    ;; reintroducing such a path, you are violating the core architecture — stop
    ;; and read AGENTS.md › Single Simulation Substrate.
    (let [forbidden #"domain\.particles|:phase0/mode|:phase0/field|:phase0/mesh"
          offenders (->> (clj-files "src")
                         (filter #(re-find forbidden (slurp %)))
                         (mapv #(.getPath %)))]
      (is (empty? offenders)
          (str "Parallel-world markers found in: " offenders))))

  (testing "domain.phase0 is the sole Phase 0 world bootstrap"
    (is (.exists (io/file "src/domain/phase0.clj")))))

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
