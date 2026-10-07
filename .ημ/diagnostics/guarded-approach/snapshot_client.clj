;; Local diagnostic process only. No server Var, session, setter or input hook.
(ns snapshot-client
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [nrepl.core :as nrepl])
  (:import [java.nio.charset StandardCharsets]
           [java.nio.file Files StandardCopyOption]
           [java.security MessageDigest]))

(def ^:private snapshot-sha "45d752a5122e498da1f4bbc39dd5233e07d3662a5c55a0e58311d72339bae03a")
(def ^:private response-cap (* 4 1024 1024))
(def ^:private run-cap (* 16 1024 1024))

(defn- sha256 [file]
  (let [digest (.digest (MessageDigest/getInstance "SHA-256")
                        (Files/readAllBytes (.toPath file)))]
    (apply str (map #(format "%02x" (bit-and % 255)) digest))))

(defn- read-small! [file limit]
  (assert (<= 1 (.length file) limit) "Missing/oversized diagnostic request")
  (edn/read-string (slurp file)))

(defn- write-new! [file text]
  (assert (.createNewFile file) "Refusing to overwrite diagnostic evidence")
  (spit file text))

(defn- publish! [file text]
  (let [temp (io/file (str file ".tmp"))]
    (write-new! temp text)
    (Files/move (.toPath temp) (.toPath file)
                (into-array StandardCopyOption [StandardCopyOption/ATOMIC_MOVE]))))

(defn- evaluate! [connection snapshot expected request directory deadline prior-bytes]
  (let [{:keys [id timeout-ms]} request
        _ (assert (= #{:id :timeout-ms} (set (keys request))) "Unexpected request fields")
        _ (assert (and (integer? timeout-ms) (<= 1 timeout-ms 35000)) "Invalid request deadline")
        _ (assert (= snapshot-sha (sha256 snapshot)) "Snapshot bytes changed")
        started (System/nanoTime)
        until (min deadline (+ started (* 1000000 timeout-ms)))
        code (str "((load-file " (pr-str (.getCanonicalPath snapshot)) ") " (pr-str expected) ")")
        ;; New lazy response cursor per completed request, same single connection.
        ;; Its wait is per-message; the supervisor enforces the whole-request
        ;; deadline and stops this process/attempt on timeout, without retry.
        client (nrepl/client connection timeout-ms)
        output (StringBuilder.)
        raw-file (io/file directory (str id ".messages.edn"))
        out-file (io/file directory (str id ".stdout"))
        err-file (io/file directory (str id ".stderr"))]
    (doseq [file [raw-file out-file err-file]]
      (assert (.createNewFile file) "Response evidence already exists"))
    (with-open [raw (io/writer raw-file)
                out (io/writer out-file)
                err (io/writer err-file)]
      (loop [responses (seq (nrepl/message client {:op "eval" :id id :code code}))
             request-bytes 0 values 0 messages 0]
        (assert (< (System/nanoTime) until) "Snapshot request deadline elapsed")
        (assert responses "Snapshot ended without done")
        (let [response (first responses)
              encoded (str (pr-str response) "\n")
              size (alength (.getBytes encoded StandardCharsets/UTF_8))
              total (+ request-bytes size)
              _ (assert (and (<= total response-cap) (<= (+ prior-bytes total) run-cap))
                        (str "Response cap exceeded; rejected message bytes=" size
                             ", prior request bytes=" request-bytes ", prior run bytes=" prior-bytes))
              _ (.write raw encoded)
              _ (.flush raw)
              status (set (:status response))
              values (+ values (if (contains? response :value) 1 0))]
          (when-let [text (:out response)]
            (.append output ^String text)
            (.write out ^String text))
          (when-let [text (:value response)]
            (.write out (str text "\n")))
          (when-let [text (:err response)]
            (.write err ^String text))
          (.flush out)
          (.flush err)
          (assert (= id (:id response)) "Response id mismatch")
          (assert (and (every? #{"done"} status)
                       (not (:ex response)) (not (:root-ex response))
                       (empty? (:err response))) "Remote error/unexpected status")
          (if (contains? status "done")
            (let [matches (vec (re-seq #"(?m)^TRUTH_APPROACH_ID (\d+) (\d+)$" (str output)))
                  _ (assert (and (= 1 values) (= 1 (count matches))) "Incomplete/duplicate snapshot frame")
                  [_ world window] (first matches)
                  observed {:world (Long/parseLong world) :window (Long/parseLong window)}]
              (assert (or (not (:world expected)) (= observed (select-keys expected [:world :window])))
                      "Snapshot identity changed")
              {:expected (merge expected observed) :bytes total :messages (inc messages)
               :elapsed-ms (/ (- (System/nanoTime) started) 1e6)})
            (recur (next responses) total values (inc messages))))))))

(defn- serve! [here directory]
  (assert (= (.getCanonicalFile (io/file here "runs")) (.getParentFile directory)) "Wrong run directory")
  (let [config (read-small! (io/file directory "snapshot-client-config.edn") 4096)
        {:keys [expected remaining-ms]} config
        _ (assert (= #{:expected :remaining-ms} (set (keys config))))
        _ (assert (= #{:pid :boot :start :cwd :display :port} (set (keys expected))))
        _ (assert (and (integer? remaining-ms) (<= 1 remaining-ms 270000)))
        _ (assert (= (:cwd expected) (.getCanonicalPath (io/file here "../../.."))))
        port (Integer/parseInt (:port expected))
        _ (assert (and (< 1024 port 65536) (not (contains? #{7888 7890 7896} port))))
        deadline (+ (System/nanoTime) (* 1000000 remaining-ms))
        snapshot (io/file here "snapshot.clj")
        responses (io/file directory "snapshot-responses")]
    (assert (= snapshot-sha (sha256 snapshot)) "Snapshot bytes changed")
    (with-open [connection (nrepl/connect :host "127.0.0.1" :port port)]
      (publish! (io/file directory "snapshot-client-ready") "ready\n")
      (loop [number 1 expected expected bytes-so-far 0]
        (assert (< (System/nanoTime) deadline) "Client absolute work deadline elapsed")
        (when-not (.exists (io/file directory "snapshot-client-close"))
          (let [id (format "%04d" number)
                file (io/file directory "snapshot-requests" (str id ".edn"))]
            (if (.exists file)
              (do
                (assert (<= number 128) "Snapshot request cap exceeded; no request omitted silently")
                (let [request (read-small! file 512)
                      _ (assert (= id (:id request)) "Nonsequential request id")
                      result (try
                               (evaluate! connection snapshot expected request responses deadline bytes-so-far)
                               (catch Throwable error
                                 (write-new! (io/file responses (str id ".error.edn"))
                                             (str (pr-str {:class (.getName (class error))
                                                           :message (.getMessage error)}) "\n"))
                                 (publish! (io/file responses (str id ".result")) "error\n")
                                 (throw error)))]
                  (write-new! (io/file responses (str id ".meta.edn")) (str (pr-str result) "\n"))
                  (publish! (io/file responses (str id ".result")) "ok\n")
                  (recur (inc number) (:expected result) (+ bytes-so-far (:bytes result)))))
              (do (Thread/sleep 20) (recur number expected bytes-so-far)))))))))

(let [here (.getParentFile (.getCanonicalFile (io/file *file*)))
      [run-directory & extra] *command-line-args*]
  (try
    (assert (and run-directory (empty? extra)) "Expected one fresh run directory")
    (serve! here (.getCanonicalFile (io/file run-directory)))
    (finally (shutdown-agents))))
