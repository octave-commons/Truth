;; Local diagnostic only: one connection, no retained server session/Var/setter.
(ns snapshot-client
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [nrepl.core :as nrepl])
  (:import [java.io FileOutputStream]
           [java.nio.charset StandardCharsets]
           [java.nio.file Files StandardCopyOption]
           [java.security MessageDigest]))

(def ^:private snapshot-sha "dc0764b98da98e63a8a9c182885e3db8d4c87228a2a4d222a0d77b4b5af5e99b")
(def ^:private response-cap (* 4 1024 1024))
(def ^:private run-cap (* 128 1024 1024))
(def ^:private request-cap 2048)

(defn- sha256 [data]
  (apply str (map #(format "%02x" (bit-and % 255))
                  (.digest (MessageDigest/getInstance "SHA-256") data))))

(defn- read-small! [file limit]
  (assert (<= 1 (.length file) limit) "Missing/oversized local IPC data")
  (edn/read-string (slurp file)))

(defn- publish-operational! [file text]
  ;; Mutable single-slot IPC only. Immutable facts are in the two journals.
  (let [temp (io/file (str file ".tmp"))]
    (spit temp text)
    (Files/move (.toPath temp) (.toPath file)
                (into-array StandardCopyOption [StandardCopyOption/ATOMIC_MOVE
                                                StandardCopyOption/REPLACE_EXISTING]))))

(defn- append-entry! [journal value]
  (with-open [out (FileOutputStream. journal true)]
    (.write out (.getBytes (str (pr-str value) "\n") StandardCharsets/UTF_8))
    (.flush out)))

(defn- raw-position [raw file]
  ;; Failure metadata must describe a partial native write, not merely the last
  ;; successfully flushed message. Secondary inspection errors are data only.
  (try
    {:byte-end (.position (.getChannel ^FileOutputStream raw)) :position-source :channel}
    (catch Throwable channel-error
      (try
        {:byte-end (Files/size (.toPath file)) :position-source :file
         :channel-position-error (str channel-error)}
        (catch Throwable file-error
          {:position-source :unavailable :channel-position-error (str channel-error)
           :file-position-error (str file-error)})))))

(defn- evaluate! [connection raw snapshot expected request until progress output]
  (let [{:keys [id timeout-ms]} request
        _ (assert (= snapshot-sha (sha256 (Files/readAllBytes (.toPath snapshot)))) "Snapshot bytes changed")
        code (str "((load-file " (pr-str (.getCanonicalPath snapshot)) ") " (pr-str expected) ")")
        client (nrepl/client connection timeout-ms)]
    (loop [responses (seq (nrepl/message client {:op "eval" :id id :code code}))
           values 0]
      (assert (< (System/nanoTime) until) "Snapshot request deadline elapsed")
      (assert responses "Snapshot ended without done")
      (let [response (first responses)
            encoded (.getBytes (str (pr-str response) "\n") StandardCharsets/UTF_8)
            size (alength encoded)
            before (.position (.getChannel ^FileOutputStream raw))
            after (+ before size)]
        (when-not (and (<= (- after (:byte-start @progress)) response-cap) (<= after run-cap))
          (swap! progress assoc :rejected-message-bytes size :rejected-message-sha256 (sha256 encoded))
          (throw (ex-info "Response cap exceeded; rejected message not fully archived" {})))
        (swap! progress assoc :pending-message {:byte-start before :encoded-bytes size
                                               :sha256 (sha256 encoded) :flush-confirmed false})
        (.write ^FileOutputStream raw encoded)
        (.flush ^FileOutputStream raw)
        (swap! progress #(-> % (dissoc :pending-message) (assoc :byte-end after) (update :messages inc)))
        (when-let [text (:out response)] (.append ^StringBuilder output ^String text))
        (when-let [text (:value response)] (.append ^StringBuilder output (str text "\n")))
        (assert (= id (:id response)) "Response id mismatch")
        (let [status (set (:status response))
              values (+ values (if (contains? response :value) 1 0))]
          (assert (and (every? #{"done"} status) (not (:ex response)) (not (:root-ex response))
                       (empty? (:err response))) "Remote error/unexpected status; raw message retained")
          (if (contains? status "done")
            (let [matches (vec (re-seq #"(?m)^TRUTH_APPROACH_ID (\d+) (\d+)$" (str output)))
                  _ (assert (and (= 1 values) (= 1 (count matches))) "Incomplete/duplicate snapshot frame")
                  [_ world window] (first matches)
                  observed {:world (Long/parseLong world) :window (Long/parseLong window)}]
              (assert (or (not (:world expected)) (= observed (select-keys expected [:world :window])))
                      "Snapshot identity changed")
              (merge expected observed))
            (recur (next responses) values)))))))

(defn- snapshot! [connection raw journal snapshot expected request directory deadline]
  (let [{:keys [id timeout-ms]} request
        started (System/nanoTime)
        until (min deadline (+ started (* 1000000 timeout-ms)))
        offset (.position (.getChannel ^FileOutputStream raw))
        progress (atom {:byte-start offset :byte-end offset :messages 0})
        output (StringBuilder.)]
    (append-entry! journal {:event :request-start :id id :request request :expected expected
                            :byte-start offset :wall-ms (System/currentTimeMillis)})
    (try
      (let [next-expected (evaluate! connection raw snapshot expected request until progress output)
            summary (merge @progress {:event :request-finish :id id :outcome :ok :expected next-expected
                                      :elapsed-ms (/ (- (System/nanoTime) started) 1e6)})]
        (append-entry! journal (assoc summary :raw-bytes (- (:byte-end summary) offset)))
        (publish-operational! (io/file directory "snapshot-current.stdout") (str output))
        (publish-operational! (io/file directory "snapshot-current.result") (str id " ok\n"))
        next-expected)
      (catch Throwable error
        (let [actual (raw-position raw (io/file directory "snapshot-messages.edn"))
              last-confirmed (:byte-end @progress)
              end (:byte-end actual)
              pending (:pending-message @progress)
              summary (merge @progress actual
                             {:event :request-failure :id id :outcome :error
                              :last-confirmed-byte-end last-confirmed
                              :byte-end end :raw-bytes (when end (- end offset))
                              :raw-span-end-known? (some? end)
                              :pending-message (when pending
                                                 (assoc pending :bytes-present (when end (- end (:byte-start pending)))
                                                        :complete-encoding? (and end (= end (+ (:byte-start pending) (:encoded-bytes pending))))))
                              :elapsed-ms (/ (- (System/nanoTime) started) 1e6)
                              :error {:class (.getName (class error)) :message (.getMessage error)}})]
          (try
            (append-entry! journal summary)
            (catch Throwable journal-error (.addSuppressed error journal-error)))
          (try
            (publish-operational! (io/file directory "snapshot-current.result") (str id " error\n"))
            (catch Throwable result-error (.addSuppressed error result-error)))
          (throw error))))))

(defn- serve! [here directory]
  (assert (= (.getCanonicalFile (io/file here "runs")) (.getParentFile directory)) "Wrong run directory")
  (let [config (read-small! (io/file directory "snapshot-client-config.edn") 4096)
        {:keys [expected remaining-ms]} config
        _ (assert (= #{:expected :remaining-ms} (set (keys config))))
        _ (assert (= #{:pid :boot :start :cwd :display :port} (set (keys expected))))
        _ (assert (and (integer? remaining-ms) (<= 1 remaining-ms 1470000)))
        _ (assert (= (:cwd expected) (.getCanonicalPath (io/file here "../../.."))))
        port (Integer/parseInt (:port expected))
        _ (assert (and (< 1024 port 65536) (not (contains? #{7888 7890 7896} port))))
        deadline (+ (System/nanoTime) (* 1000000 remaining-ms))
        snapshot (io/file here "snapshot.clj")
        journal (io/file directory "snapshot-requests.edn")
        raw-file (io/file directory "snapshot-messages.edn")]
    (assert (= snapshot-sha (sha256 (Files/readAllBytes (.toPath snapshot)))))
    (doseq [file [journal raw-file]] (assert (.createNewFile file) "Refusing to overwrite evidence"))
    (try
      (with-open [connection (nrepl/connect :host "127.0.0.1" :port port)
                  raw (FileOutputStream. raw-file true)]
        (append-entry! journal {:event :connection-open :pid (.pid (java.lang.ProcessHandle/current))
                                :expected expected :wall-ms (System/currentTimeMillis)})
        (publish-operational! (io/file directory "snapshot-client-ready") "ready\n")
        (loop [number 1 expected expected]
          (assert (< (System/nanoTime) deadline) "Client absolute work deadline elapsed")
          (if (.exists (io/file directory "snapshot-client-close"))
            (append-entry! journal {:event :connection-close :completed (dec number)
                                    :byte-end (.position (.getChannel raw)) :wall-ms (System/currentTimeMillis)})
            (let [file (io/file directory "snapshot-current-request.edn")
                  request (when (.exists file) (read-small! file 512))
                  id (:id request)
                  previous (format "%04d" (dec number))]
              (if (or (nil? request) (= id previous))
                (do (Thread/sleep 20) (recur number expected))
                (do
                  (assert (and (<= number request-cap) (= id (format "%04d" number))) "Request count/order violated")
                  (assert (= #{:id :timeout-ms} (set (keys request))) "Unexpected request fields")
                  (assert (and (integer? (:timeout-ms request)) (<= 1 (:timeout-ms request) 35000)))
                  (recur (inc number) (snapshot! connection raw journal snapshot expected request directory deadline))))))))
      (catch Throwable error
        (try
          (append-entry! journal {:event :worker-failure :wall-ms (System/currentTimeMillis)
                                  :error {:class (.getName (class error)) :message (.getMessage error)}})
          (catch Throwable journal-error (.addSuppressed error journal-error)))
        (throw error)))))

(let [here (.getParentFile (.getCanonicalFile (io/file *file*)))
      [run-directory & extra] *command-line-args*]
  (try
    (assert (and run-directory (empty? extra)) "Expected one fresh run directory")
    (serve! here (.getCanonicalFile (io/file run-directory)))
    (finally (shutdown-agents))))
