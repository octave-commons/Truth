;; Deterministic code-smell / "dung heap" detector for Gates of Truth.
;;
;; clj-kondo finds *bugs*; Splint finds *non-idiomatic forms*. Neither measures
;; structural decay — god namespaces, mega-functions, parameter bloat,
;; undocumented public API, runaway coupling. This script does, by reducing over
;; clj-kondo's analysis export (a pure, deterministic data dump).
;;
;; Usage (see bin/analyze):
;;   clj-kondo --lint src test \
;;     --config '{:output {:analysis {:var-definitions {:meta true} :arglists true}
;;                         :format :edn}}' \
;;     | clojure -M dev/smell_report.clj [--strict]
;;
;; Reads the analysis EDN on stdin. Prints a sorted report. With --strict (CI),
;; exits 1 if any HARD threshold is breached; otherwise always exits 0.
(ns smell-report
  (:require [clojure.edn :as edn]
            [clojure.string :as str]))

;; Thresholds. :warn = surfaced in report; :hard = fails --strict (CI gate).
;; Calibrated against the current tree (render.clj=2037 LOC, stellar.clj=1351
;; are the known offenders we want flagged). Tighten as the codebase converges.
(def thresholds
  {:namespace-loc {:warn 500  :hard 1200}   ; god namespace
   :namespace-vars {:warn 30  :hard 60}     ; too many responsibilities
   :function-loc {:warn 40    :hard 80}     ; mega-function
   :params       {:warn 5     :hard 8}      ; argument bloat
   :fan-out      {:warn 18    :hard 30}})   ; efferent coupling

(defn- project-file? [f]
  (and f (or (str/starts-with? f "src/") (str/starts-with? f "test/"))))

(defn- public-defn? [d]
  (and (#{'clojure.core/defn 'clojure.core/defmacro} (:defined-by d))
       (not (:private d))
       (not (get-in d [:meta :private]))))

(defn- arity [d]
  ;; Widest fixed arity; variadic adds one for the rest arg.
  (let [fixed (apply max 0 (:fixed-arities d))]
    (if (:varargs-min-arity d) (inc (:varargs-min-arity d)) fixed)))

(defn- file-loc [f]
  (try (count (str/split-lines (slurp f))) (catch Exception _ 0)))

(defn analyze [{:keys [var-definitions namespace-definitions namespace-usages]}]
  (let [defs (filter #(project-file? (:filename %)) var-definitions)
        nsds (filter #(project-file? (:filename %)) namespace-definitions)
        by-ns (group-by :ns defs)]
    {:god-namespaces
     (->> nsds
          (map (fn [{:keys [name filename]}]
                 {:ns name :file filename
                  :loc (file-loc filename)
                  :vars (count (by-ns name))}))
          (filter #(or (>= (:loc %) (get-in thresholds [:namespace-loc :warn]))
                       (>= (:vars %) (get-in thresholds [:namespace-vars :warn]))))
          (sort-by :loc >))

     :long-functions
     (->> defs
          (filter #(= 'clojure.core/defn (:defined-by %)))
          (map (fn [d] (assoc d :loc (inc (- (:end-row d) (:row d))))))
          (filter #(>= (:loc %) (get-in thresholds [:function-loc :warn])))
          (sort-by :loc >))

     :param-bloat
     (->> defs
          (filter #(#{'clojure.core/defn 'clojure.core/defmacro} (:defined-by %)))
          (map (fn [d] (assoc d :arity (arity d))))
          (filter #(>= (:arity %) (get-in thresholds [:params :warn])))
          (sort-by :arity >))

     :missing-docstrings
     (->> defs (filter public-defn?) (filter #(str/blank? (str (:doc %))))
          (sort-by (juxt :filename :row)))

     :high-fan-out
     (->> namespace-usages
          (filter #(project-file? (:filename %)))
          ;; group dependency edges by source namespace
          (group-by :from)
          (map (fn [[from uses]]
                 {:ns from :fan-out (count (distinct (map :to uses)))}))
          (filter #(>= (:fan-out %) (get-in thresholds [:fan-out :warn])))
          (sort-by :fan-out >))}))

;; --- rendering --------------------------------------------------------------

(defn- hard? [metric v] (>= v (get-in thresholds [metric :hard])))
(defn- tag [metric v] (if (hard? metric v) "HARD" "warn"))
(def ^:private bar "────────────────────────────────────────────────────────")

(defn- loc->str [f r] (str f ":" r))

(defn report [{:keys [god-namespaces long-functions param-bloat
                      missing-docstrings high-fan-out]}]
  (let [hard (atom 0)]
    (letfn [(note-hard! [n] (swap! hard + n))]
      (println bar)
      (println "CODE-SMELL / STRUCTURAL REPORT  (dev/smell_report.clj)")
      (println bar)

      (println (format "\n● GOD NAMESPACES  (loc≥%d vars≥%d | HARD loc≥%d vars≥%d)"
                       (get-in thresholds [:namespace-loc :warn])
                       (get-in thresholds [:namespace-vars :warn])
                       (get-in thresholds [:namespace-loc :hard])
                       (get-in thresholds [:namespace-vars :hard])))
      (if (seq god-namespaces)
        (doseq [{:keys [ns file loc vars]} god-namespaces
                :let [h (or (hard? :namespace-loc loc) (hard? :namespace-vars vars))]]
          (when h (note-hard! 1))
          (println (format "  [%s] %-34s %4d loc  %3d vars  (%s)"
                           (if h "HARD" "warn") ns loc vars file)))
        (println "  none"))

      (println (format "\n● MEGA-FUNCTIONS  (loc≥%d | HARD loc≥%d)"
                       (get-in thresholds [:function-loc :warn])
                       (get-in thresholds [:function-loc :hard])))
      (if (seq long-functions)
        (doseq [{:keys [name loc filename row]} long-functions]
          (when (hard? :function-loc loc) (note-hard! 1))
          (println (format "  [%s] %-30s %4d loc  %s"
                           (tag :function-loc loc) name loc (loc->str filename row))))
        (println "  none"))

      (println (format "\n● PARAMETER BLOAT  (arity≥%d | HARD arity≥%d)"
                       (get-in thresholds [:params :warn])
                       (get-in thresholds [:params :hard])))
      (if (seq param-bloat)
        (doseq [{:keys [name arity filename row]} param-bloat]
          (when (hard? :params arity) (note-hard! 1))
          (println (format "  [%s] %-30s %2d params  %s"
                           (tag :params arity) name arity (loc->str filename row))))
        (println "  none"))

      (println (format "\n● HIGH FAN-OUT  (deps≥%d | HARD deps≥%d)"
                       (get-in thresholds [:fan-out :warn])
                       (get-in thresholds [:fan-out :hard])))
      (if (seq high-fan-out)
        (doseq [{:keys [ns fan-out]} high-fan-out]
          (when (hard? :fan-out fan-out) (note-hard! 1))
          (println (format "  [%s] %-34s depends on %2d namespaces"
                           (tag :fan-out fan-out) ns fan-out)))
        (println "  none"))

      (println "\n● UNDOCUMENTED PUBLIC FNS  (AGENTS.md: docstrings mandatory)")
      (if (seq missing-docstrings)
        (doseq [{:keys [name filename row]} missing-docstrings]
          (println (format "  [warn] %-30s %s" name (loc->str filename row))))
        (println "  none"))

      (println (str "\n" bar))
      (println (format "HARD breaches: %d | undocumented public fns: %d"
                       @hard (count missing-docstrings)))
      (println bar)
      @hard)))

(let [analysis (:analysis (edn/read-string (slurp *in*)))
      strict? (some #{"--strict"} *command-line-args*)
      hard (report (analyze analysis))]
  (flush)
  (System/exit (if (and strict? (pos? hard)) 1 0)))
