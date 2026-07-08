(ns infra.dev.actor-dashboard
  "Lightweight web dashboard for the ημ actor system.

    Serves a single auto-refreshing HTML page that shows every actor under
    `.eta-mu/actors/`, its sessions, inbox/outbox activity, and recent research
    notebook output. The dashboard itself uses only the JDK built-in HttpServer;
    research notebooks are rendered to HTML with CommonMark.

    Run:
      clj -M:dashboard

    Then open http://localhost:7889/"
  (:require
   [clojure.edn :as edn]
   [clojure.java.io :as io]
   [clojure.string :as str])
  (:import
   [com.sun.net.httpserver HttpServer HttpHandler HttpExchange]
   [java.io File]
   [java.lang ProcessHandle]
   [java.net InetSocketAddress]
   [java.time Instant LocalDateTime ZoneId]
   [java.time.format DateTimeFormatter]
   [org.commonmark.ext.gfm.tables TablesExtension]
   [org.commonmark.parser Parser]
   [org.commonmark.renderer.html HtmlRenderer]))

(defn- actor-dir
  "Return the workspace actor root directory."
  []
  (io/file ".eta-mu" "actors"))

(defn- list-directories
  "Return the immediate child directories of a directory as Files."
  [^File dir]
  (when (.isDirectory dir)
    (filter #(.isDirectory ^File %) (.listFiles dir))))

(defn- read-actor-edn
  "Read actor.edn as a Clojure map, or nil if missing/unreadable."
  [^File dir]
  (let [f (io/file dir "actor.edn")]
    (when (.exists f)
      (try
        (edn/read-string (slurp f :encoding "UTF-8"))
        (catch Exception _
          nil)))))

(defn- count-files
  "Count non-hidden files in a directory."
  [^File dir]
  (if (.isDirectory dir)
    (count (filter #(and (.isFile ^File %) (not (str/starts-with? (.getName ^File %) ".")))
                   (.listFiles dir)))
    0))

(defn- file-mtime
  "Return last-modified epoch millis for a file, or nil."
  [^File f]
  (when (.exists f) (.lastModified f)))

(defn- newest-files
  "Return up to n newest files in a directory as [name mtime] pairs."
  [^File dir n]
  (if (.isDirectory dir)
    (->> (.listFiles dir)
         (filter #(.isFile ^File %))
         (filter #(not (str/starts-with? (.getName ^File %) ".")))
         (sort-by file-mtime >)
         (take n)
         (map #(vector (.getName ^File %) (file-mtime ^File %))))
    []))

(defn- session-status
  "Read :session/status from session.edn, defaulting to :unknown."
  [^File session-dir]
  (let [f (io/file session-dir "session.edn")]
    (if (.exists f)
      (try
        (get (edn/read-string (slurp f :encoding "UTF-8")) :session/status :unknown)
        (catch Exception _ :unknown))
      :unknown)))

(defn- session-pid
  "Read :session/dispatch-pid from session.edn, or nil."
  [^File session-dir]
  (let [f (io/file session-dir "session.edn")]
    (when (.exists f)
      (try
        (get (edn/read-string (slurp f :encoding "UTF-8")) :session/dispatch-pid)
        (catch Exception _ nil)))))

(defn- process-alive?
  "Return true if a process with pid is currently running."
  [pid]
  (when pid
    (try
      (let [process (ProcessHandle/of pid)]
        (and (some? process) (.isAlive ^ProcessHandle process)))
      (catch Exception _ false))))

(defn- format-epoch
  "Format epoch millis as a compact local ISO string."
  [ms]
  (when ms
    (try
      (let [dt (LocalDateTime/ofInstant (Instant/ofEpochMilli ms) (ZoneId/systemDefault))]
        (.format dt (DateTimeFormatter/ofPattern "yyyy-MM-dd HH:mm:ss")))
      (catch Exception _ (str ms)))))

(defn- actor-state
  "Build a state map for a single actor directory."
  [^File dir]
  (let [edn (read-actor-edn dir)
        sessions (list-directories (io/file dir "sessions"))
        inbox (io/file dir "inbox")
        outbox (io/file dir "outbox")
        sessions-with-status (map (fn [s]
                                    {:id (.getName s)
                                     :status (session-status s)
                                     :pid (session-pid s)
                                     :alive? (process-alive? (session-pid s))
                                     :log "session.edn"})
                                  sessions)]
    {:id (name (:actor/id edn (keyword (.getName dir))))
     :name (or (:actor/name edn) (name (:actor/id edn)))
     :purpose (:actor/purpose edn "")
     :inbox-count (count-files inbox)
     :outbox-count (count-files outbox)
     :session-count (count sessions)
     :sessions sessions-with-status
     :recent-outbox (newest-files outbox 5)
     :recent-inbox (newest-files inbox 3)}))

(defn- all-actors
  "Return a sorted list of actor state maps."
  []
  (->> (list-directories (actor-dir))
       (map actor-state)
       (sort-by :id)))

(defn- recent-research-notebooks
  "Return the newest markdown files under docs/research/."
  [n]
  (let [dir (io/file "docs" "research")]
    (->> (file-seq dir)
         (filter #(and (.isFile ^File %)
                       (str/ends-with? (.getName ^File %) ".md")
                       (not= "INDEX.md" (.getName ^File %))
                       (not= "ACTORS.md" (.getName ^File %))))
         (sort-by file-mtime >)
         (take n)
         (map (fn [f]
                {:path (.getPath f)
                 :name (.getName f)
                 :mtime (file-mtime f)})))))

(defn- html-escape
  "Escape HTML entities."
  [s]
  (when s
    (-> (str s)
        (str/replace "&" "&amp;")
        (str/replace "<" "&lt;")
        (str/replace ">" "&gt;")
        (str/replace "\"" "&quot;"))))

(def ^:private md-extensions
  "GFM extensions enabled for notebook rendering (tables, etc.)."
  [(TablesExtension/create)])

(def ^:private md-parser
  "Shared CommonMark parser for notebook rendering, with GFM table support."
  (.build (.extensions (Parser/builder) md-extensions)))

(def ^:private md-renderer
  "Shared CommonMark HTML renderer for notebook rendering, with GFM table support."
  (.build (.extensions (HtmlRenderer/builder) md-extensions)))

(defn- markdown->html
  "Render a Markdown string to HTML using CommonMark."
  [^String s]
  (when s
    (.render md-renderer (.parse md-parser s))))

(defn- actor-row
  "Render a table row for an actor summary."
  [{:keys [id purpose inbox-count outbox-count session-count]}]
  (str "<tr>"
       "<td>" (html-escape id) "</td>"
       "<td>" (html-escape purpose) "</td>"
       "<td>" session-count "</td>"
       "<td>" inbox-count "</td>"
       "<td>" outbox-count "</td>"
       "</tr>"))

(defn- session-row
  "Render a session detail row."
  [{:keys [id status alive?]} actor-id]
  (let [status-color (case status
                       :running "#4caf50"
                       :completed "#2196f3"
                       :failed "#f44336"
                       :unknown "#9e9e9e"
                       "#9e9e9e")
        alive-text (if alive? "alive" "dead")]
    (str "<tr>"
         "<td>" (html-escape actor-id) "</td>"
         "<td>" (html-escape id) "</td>"
         "<td><span style='color:" status-color "'>" (html-escape (str status)) "</span></td>"
         "<td>" alive-text "</td>"
         "</tr>")))

(defn- file-list
  "Render a small list of recent files."
  [files]
  (if (seq files)
    (str "<ul>"
         (str/join "" (map (fn [[file-name mtime]]
                             (str "<li><code>" (html-escape file-name) "</code> — " (format-epoch mtime) "</li>"))
                           files))
         "</ul>")
    "<p><em>None</em></p>"))

(defn- dashboard-html
  "Generate the full dashboard HTML."
  []
  (let [actors (all-actors)
        notebooks (recent-research-notebooks 10)]
    (str "<!DOCTYPE html><html><head>"
         "<meta charset='utf-8'>"
         "<meta http-equiv='refresh' content='30'>"
         "<title>Gates of Truth — Actor Dashboard</title>"
         "<style>"
         "body{font-family:system-ui,-apple-system,sans-serif;margin:2rem;background:#0f172a;color:#e2e8f0}"
         "h1,h2{color:#38bdf8}"
         "table{border-collapse:collapse;width:100%;margin:1rem 0;background:#1e293b}"
         "th,td{padding:0.6rem 1rem;text-align:left;border-bottom:1px solid #334155}"
         "th{background:#0f172a;color:#94a3b8}"
         "tr:hover{background:#334155}"
         "code{background:#0f172a;padding:0.1rem 0.4rem;border-radius:0.25rem}"
         "ul{line-height:1.6}"
         ".section{margin:2rem 0;padding:1rem;background:#1e293b;border-radius:0.5rem}"
         ".status-green{color:#4caf50}.status-red{color:#f44336}.status-blue{color:#2196f3}"
         "</style></head><body>"
         "<h1>ημ Actor Dashboard</h1>"
         "<p>Auto-refreshes every 30 seconds. Last update: " (format-epoch (System/currentTimeMillis)) "</p>"
         "<div class='section'><h2>Actors</h2><table>"
         "<tr><th>Actor</th><th>Purpose</th><th>Sessions</th><th>Inbox</th><th>Outbox</th></tr>"
         (str/join "" (map actor-row actors))
         "</table></div>"
         "<div class='section'><h2>Sessions</h2><table>"
         "<tr><th>Actor</th><th>Session</th><th>Status</th><th>Process</th></tr>"
         (str/join "" (mapcat (fn [a] (map #(session-row % (:id a)) (:sessions a))) actors))
         "</table></div>"
         "<div class='section'><h2>Recent Outbox</h2>"
         (str/join "" (map (fn [a]
                             (str "<h3>" (html-escape (:id a)) "</h3>"
                                  (file-list (:recent-outbox a))))
                           actors))
         "</div>"
         "<div class='section'><h2>Recent Research Notebooks</h2><ul>"
         (str/join "" (map (fn [{:keys [path mtime] :as nb}]
                             (let [notebook-name (:name nb)]
                               (str "<li><a href='/notebook?path=" path "'>"
                                    (html-escape notebook-name)
                                    "</a> — " (format-epoch mtime) "</li>")))
                           notebooks))
         "</ul></div>"
         "<div class='section'><h2>Quick Commands</h2><pre>"
         "actor-status truth-research-physics\n"
         "actor-status truth-research-cosmology\n"
         "actor-status truth-research-peer-reviewer\n"
         "actor-status truth-research-gap-analyst\n"
         "</pre></div>"
         "</body></html>")))

(defn- send-html
  "Send an HTML response."
  [^HttpExchange exchange status body]
  (let [body-bytes (.getBytes body "UTF-8")]
    (.sendResponseHeaders exchange status (alength body-bytes))
    (with-open [os (.getResponseBody exchange)]
      (.write os body-bytes))))

(defn- dashboard-handler
  "HttpHandler for the root dashboard."
  []
  (reify HttpHandler
    (handle [_ exchange]
      (try
        (send-html exchange 200 (dashboard-html))
        (catch Exception e
          (send-html exchange 500 (str "<pre>" (html-escape (pr-str e)) "</pre>")))
        (finally
          (.close exchange))))))

(defn- notebook-handler
  "HttpHandler to serve a single research notebook by path query parameter."
  []
  (reify HttpHandler
    (handle [_ exchange]
      (try
        (let [query (some-> exchange .getRequestURI .getQuery)
              path (when query
                     (java.net.URLDecoder/decode (second (re-matches #"path=(.*)" query)) "UTF-8"))
              f (io/file path)]
          (if (and f (.exists f) (.isFile f) (str/starts-with? (.getCanonicalPath f) (.getCanonicalPath (io/file "docs" "research"))))
            (let [body (slurp f :encoding "UTF-8")
                  html (str "<!DOCTYPE html><html><head><meta charset='utf-8'><style>"
                            "body{font-family:system-ui,-apple-system,sans-serif;max-width:60rem;margin:2rem auto;line-height:1.6;background:#0f172a;color:#e2e8f0;padding:0 1rem}"
                            "a{color:#38bdf8}"
                            "h1,h2,h3,h4{color:#38bdf8;border-bottom:1px solid #334155;padding-bottom:.3rem}"
                            "pre{background:#1e293b;padding:1rem;overflow:auto;border-radius:.5rem}"
                            "code{background:#1e293b;padding:.1rem .3rem;border-radius:.25rem}"
                            "pre code{background:transparent;padding:0}"
                            "img{max-width:100%}"
                            "table{border-collapse:collapse;width:100%}"
                            "th,td{border:1px solid #334155;padding:.4rem}"
                            "th{background:#1e293b}"
                            "blockquote{border-left:4px solid #334155;margin:0;padding-left:1rem;color:#94a3b8}"
                            "</style></head><body><a href='/'>← Dashboard</a><hr>"
                            (markdown->html body)
                            "</body></html>")]
              (send-html exchange 200 html))
            (send-html exchange 404 "<h1>Not found</h1>")))
        (catch Exception e
          (send-html exchange 500 (str "<pre>" (html-escape (pr-str e)) "</pre>")))
        (finally
          (.close exchange))))))

(defn start!
  "Start the dashboard HTTP server on the given port and block."
  ([] (start! 7889))
  ([port]
   (let [server (HttpServer/create (InetSocketAddress. "127.0.0.1" port) 0)]
     (.createContext server "/" (dashboard-handler))
     (.createContext server "/notebook" (notebook-handler))
     (.setExecutor server nil)
     (.start server)
     (println (str "Actor dashboard listening on http://127.0.0.1:" port "/"))
     (.addShutdownHook (Runtime/getRuntime)
                       (Thread. #(do (.stop server 0)
                                     (println "Dashboard shut down."))))
     @(promise))))

(defn -main
  "Entry point for the actor dashboard."
  [& args]
  (start! (or (some-> (first args) parse-long) 7889)))