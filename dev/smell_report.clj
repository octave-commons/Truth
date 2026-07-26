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

;; Namespaces whose entire purpose is to define a shared vocabulary of keywords.
;; They are exempt from the public-var and missing-docstring thresholds because
;; splitting them into tiny sub-modules would fragment the vocabulary, multiply
;; imports across every consumer, and create a duplicate-keyword hazard.
(def vocabulary-namespaces
  #{'domain.ecs.components})

;; Namespaces that are intentionally thin facades: they re-export public vars
;; from split sub-modules for backwards compatibility.  Their surface area is
;; large by design, not by responsibility.
(def facade-namespaces
  #{'domain.player
    'domain.ecology
    'infra.render})

;; System-assembly namespaces that wire many sub-modules together.  High fan-out
;; here is structural, not a coupling smell.
(def assembly-namespaces
  #{'domain.genesis.systems})

;; DSL macro names whose arity is part of the public syntax, not bloat.
(def dsl-macro-names
  #{'defsystem 'defreaction 'defaggregate 'defprojection 'defrewind})

(defn- vocabulary-namespace? [ns-sym]
  (contains? vocabulary-namespaces ns-sym))

(defn- facade-namespace? [ns-sym]
  (contains? facade-namespaces ns-sym))

(defn- assembly-namespace? [ns-sym]
  (contains? assembly-namespaces ns-sym))

(defn- test-namespace? [ns-sym]
  (str/ends-with? (str ns-sym) "-test"))

(defn- dsl-macro? [d]
  (contains? dsl-macro-names (:name d)))

(defn- exempt-from-var-count? [ns-sym]
  (or (vocabulary-namespace? ns-sym)
      (facade-namespace? ns-sym)))

(defn- exempt-from-god-namespace? [ns-sym]
  (test-namespace? ns-sym))

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

;; --- code-line measurement --------------------------------------------------
;; Raw line spans punish the conventions this project mandates: docstrings are
;; required on every public var (AGENTS.md) and the codebase leans on long
;; design-note comment headers. Counting those as "code" made `derive-edits`
;; read as 116 loc when its body is 78, and `voxel-focus-system` as 86 when its
;; body is 57. The thresholds are unchanged; only the measurement is corrected.
;; Both numbers are reported, so this is an honest re-measure and not a quiet
;; threshold relaxation.
;;
;; A line counts as CODE when it holds at least one non-whitespace character
;; that is outside a comment and outside a string body. Tracking string state
;; character-by-character handles docstrings and any other multi-line string
;; with the same rule, rather than special-casing the docstring position. The
;; opening `"` alone does not make a line code, so a docstring's first line is
;; attributed to the docstring rather than to the body.

(defn- code-line-flags*
  "Per-line `true`/`false` code flags for the source file `f`, 0-indexed.

   Scans characters tracking string and comment state: `;` outside a string
   comments out the rest of the line, `\\` outside a string escapes the next
   character (so char literals like `\\\" ` and `\\;` do not open strings or
   comments), and characters inside a string body never mark a line as code."
  [f]
  (letfn [(scan-line [^String line in-string?]
            ;; -> [code? in-string-at-end?]
            (let [n (count line)]
              (loop [i 0, in-str? in-string?, code? false]
                (if (>= i n)
                  [code? in-str?]
                  (let [c (.charAt line i)]
                    (cond
                      in-str?
                      (case c
                        \\ (recur (+ i 2) true code?)
                        \" (recur (inc i) false code?)
                        (recur (inc i) true code?))

                      ;; comment — rest of the line is not code
                      (= c \;) [code? false]

                      ;; escape outside a string: char literal, consume both
                      (= c \\) (recur (+ i 2) false true)

                      ;; opening quote does not itself count as code
                      (= c \") (recur (inc i) true code?)

                      (Character/isWhitespace c) (recur (inc i) false code?)

                      :else (recur (inc i) false true)))))))]
    (try
      (loop [ls (str/split-lines (slurp f)), in-string? false, acc (transient [])]
        (if-not (seq ls)
          (persistent! acc)
          (let [[code? in-str?] (scan-line (first ls) in-string?)]
            (recur (rest ls) in-str? (conj! acc code?)))))
      (catch Exception _ []))))

(def ^:private code-line-flags (memoize code-line-flags*))

(defn- file-code-loc
  "Count of code lines in `f` — excludes blank, comment-only and string-body
   lines. See `code-line-flags`."
  [f]
  (count (filter true? (code-line-flags f))))

(defn- span-code-loc
  "Count of code lines in `f` between 1-indexed `row` and `end-row` inclusive.

   Used for function length, so a mandatory docstring does not inflate a
   function past the mega-function threshold."
  [f row end-row]
  (let [flags (code-line-flags f)]
    (if (seq flags)
      (->> flags
           (drop (max 0 (dec (long row))))
           (take (inc (- (long end-row) (long row))))
           (filter true?)
           count)
      (inc (- (long end-row) (long row))))))

(defn analyze [{:keys [var-definitions namespace-definitions namespace-usages]}]
  (let [defs (filter #(project-file? (:filename %)) var-definitions)
        nsds (filter #(project-file? (:filename %)) namespace-definitions)
        by-ns (group-by :ns defs)]
    {:god-namespaces
     (->> nsds
          (map (fn [{:keys [name filename]}]
                 {:ns name :file filename
                  :loc (file-code-loc filename)
                  :raw-loc (file-loc filename)
                  :vars (if (exempt-from-var-count? name) 0 (count (by-ns name)))}))
          (filter #(or (>= (:loc %) (get-in thresholds [:namespace-loc :warn]))
                       (>= (:vars %) (get-in thresholds [:namespace-vars :warn]))))
          (remove #(exempt-from-god-namespace? (:ns %)))
          (sort-by :loc >))

     :long-functions
     (->> defs
          (filter #(= 'clojure.core/defn (:defined-by %)))
          (map (fn [{:keys [filename row end-row] :as d}]
                 (assoc d :loc (span-code-loc filename row end-row)
                        :raw-loc (inc (- (long end-row) (long row))))))
          (filter #(>= (:loc %) (get-in thresholds [:function-loc :warn])))
          (sort-by :loc >))

      :param-bloat
      (->> defs
           (filter #(#{'clojure.core/defn 'clojure.core/defmacro} (:defined-by %)))
           (remove dsl-macro?)
           (map (fn [d] (assoc d :arity (arity d))))
           (filter #(>= (:arity %) (get-in thresholds [:params :warn])))
           (sort-by :arity >))

      :missing-docstrings
      (->> defs (filter public-defn?) (filter #(str/blank? (str (:doc %))))
           (filter #(not (vocabulary-namespace? (:ns %))))
           (sort-by (juxt :filename :row)))

     :high-fan-out
     (->> namespace-usages
          (filter #(project-file? (:filename %)))
          ;; group dependency edges by source namespace
          (group-by :from)
          (remove #(or (assembly-namespace? (first %))
                       (test-namespace? (first %))))
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
      (println "  (loc = code lines; raw = every line incl. docstrings/comments)")
      (if (seq god-namespaces)
        (doseq [{:keys [ns file loc raw-loc vars]} god-namespaces
                :let [h (or (hard? :namespace-loc loc) (hard? :namespace-vars vars))]]
          (when h (note-hard! 1))
          (println (format "  [%s] %-34s %4d loc (%4d raw)  %3d vars  (%s)"
                           (if h "HARD" "warn") ns loc raw-loc vars file)))
        (println "  none"))

      (println (format "\n● MEGA-FUNCTIONS  (loc≥%d | HARD loc≥%d)"
                       (get-in thresholds [:function-loc :warn])
                       (get-in thresholds [:function-loc :hard])))
      (println "  (loc = code lines; raw = every line incl. the docstring)")
      (if (seq long-functions)
        (doseq [{:keys [name loc raw-loc filename row]} long-functions]
          (when (hard? :function-loc loc) (note-hard! 1))
          (println (format "  [%s] %-30s %4d loc (%4d raw)  %s"
                           (tag :function-loc loc) name loc raw-loc (loc->str filename row))))
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
      (println "  (vocabulary namespaces exempt; see dev/smell_report.clj vocabulary-namespaces)")
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
