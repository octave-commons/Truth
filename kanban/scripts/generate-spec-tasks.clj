(require '[clojure.string :as str]
         '[clojure.java.io :as io])

(defn slugify [s]
  (-> s
      (str/lower-case)
      (str/replace #"[^a-z0-9]+" "-")
      (str/replace #"^-+|-+$" "")
      (str/replace #"-+$" "")))

(defn parse-spec [file]
  (let [lines (str/split-lines (slurp file))
        title (or (some #(when (str/starts-with? % "# ") (str/trim (subs % 2))) lines)
                  (str/replace (.getName file) #"\.md$" ""))
        status-line (some #(when (str/starts-with? (str/lower-case (str/trim %)) "**status:**") %) lines)
        status-raw (when status-line
                     (str/trim (str/replace status-line #"(?i)^\*\*status:\*\*\s*" "")))
        source (str "docs/specs/" (.getName file))]
    {:title title
     :status-raw status-raw
     :source source
     :uuid (slugify title)}))

(defn ->kanban-status [status-raw]
  (let [s (str/lower-case (or status-raw ""))]
    (cond
      (or (str/includes? s "implemented") (str/includes? s "completed")) "done"
      (str/includes? s "ready for implementation") "ready"
      (str/includes? s "inventory complete") "document"
      :else "todo")))

(defn ->priority [status-raw title]
  (let [s (str/lower-case (or status-raw ""))
        t (str/lower-case title)]
    (cond
      (or (str/includes? s "implemented") (str/includes? s "completed")) "P0"
      (str/includes? t "complete planet formation") "P0"
      (str/includes? t "protoplanetary") "P0"
      (str/includes? t "jeans") "P0"
      (str/includes? t "sph density") "P0"
      (str/includes? t "sink") "P0"
      (str/includes? t "chemistry") "P1"
      (str/includes? t "habitability") "P1"
      (str/includes? t "narrator") "P1"
      (str/includes? t "player focus") "P1"
      (str/includes? t "render") "P2"
      (str/includes? t "notes-to-specs") "P2"
      :else "P1")))

(defn ->labels [title]
  (let [t (str/lower-case title)]
    (cond-> ["specs"]
      (str/includes? t "phase 0") (conj "phase0")
      (str/includes? t "stage 2") (conj "stage2")
      (str/includes? t "hydro") (conj "hydro")
      (str/includes? t "em") (conj "em")
      (str/includes? t "gravity") (conj "gravity")
      (str/includes? t "renderer") (conj "render")
      (str/includes? t "render") (conj "render")
      (str/includes? t "sph") (conj "sph")
      (str/includes? t "barnes") (conj "gravity")
      (str/includes? t "cache") (conj "performance")
      (str/includes? t "kernel") (conj "performance")
      (str/includes? t "profiling") (conj "performance")
      (str/includes? t "narrator") (conj "myth")
      (str/includes? t "player") (conj "player")
      (str/includes? t "habitability") (conj "handoff")
      (str/includes? t "chemistry") (conj "chemistry")
      (str/includes? t "sink") (conj "sink")
      (str/includes? t "notes") (conj "docs"))))

(defn yaml-quoted [s]
  (str "\"" (str/replace s "\"" "\\\"") "\""))

(defn yaml-list [items]
  (str "[" (str/join ", " (map #(str "\"" % "\"") items)) "]"))

(defn generate-task [{:keys [title status-raw source uuid]}]
  (let [status (->kanban-status status-raw)
        priority (->priority status-raw title)
        labels (->labels title)
        now (str (java.time.Instant/now))]
    (str "---\n"
         "uuid: " (yaml-quoted uuid) "\n"
         "title: " (yaml-quoted title) "\n"
         "status: " (yaml-quoted status) "\n"
         "priority: " (yaml-quoted priority) "\n"
         "labels: " (yaml-list labels) "\n"
         "created_at: " (yaml-quoted now) "\n"
         "source: " (yaml-quoted source) "\n"
         "category: " (yaml-quoted "specs") "\n"
         "---\n\n"
         "# " title "\n\n"
         "> Original spec: `" source "`\n\n"
         "This kanban card tracks the spec. Edit the original spec for technical detail; use this card for status, priority, and work notes.\n")))

(let [specs-dir (io/file "docs/specs")
      tasks-dir (io/file "kanban/tasks")]
  (.mkdirs tasks-dir)
  (doseq [file (sort (.listFiles specs-dir))
          :when (str/ends-with? (.getName file) ".md")]
    (let [spec (parse-spec file)
          task-content (generate-task spec)
          task-file (io/file tasks-dir (str (:uuid spec) ".md"))]
      (spit task-file task-content)
      (println "Created" (.getPath task-file)))))
