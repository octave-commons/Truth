(ns law.ledger
  "Hash-chained append-only event ledger.

   Each entry:
     {:event      Event
      :tick       long
      :entry-hash string  ;; SHA-256(prev-hash + canonical(event))
      :prev-hash  string}

   The chain is valid iff every entry's hash equals
   SHA-256(prev-hash || canonical-event).

   Snapshots store a materialized world state at a given tick,
   keyed by tick number, for O(1) seek anchoring."
  (:require
    [domain.ecs.event :as event])
  (:import
    (java.security MessageDigest)
    (java.nio.charset StandardCharsets)))

;; ---------------------------------------------------------------------------
;; Hashing
;; ---------------------------------------------------------------------------

(def ^:private genesis-hash
  "0000000000000000000000000000000000000000000000000000000000000000")

(defn- sha256
  "SHA-256 of a string. Returns lowercase hex string."
  [^String s]
  (let [md     (MessageDigest/getInstance "SHA-256")
        bytes  (.digest md (.getBytes s StandardCharsets/UTF_8))]
    (apply str (map #(format "%02x" (bit-and % 0xff)) bytes))))

(defn- canonical
  "Deterministic string representation of an event for hashing.
   Uses pr-str — consistent within a JVM session."
  [event]
  (pr-str (dissoc event :id)))

(defn- compute-entry-hash
  "Compute hash for a new entry given previous hash and event."
  [prev-hash event]
  (sha256 (str prev-hash (canonical event))))

;; ---------------------------------------------------------------------------
;; Ledger record
;; ---------------------------------------------------------------------------

(defrecord Ledger
  [entries   ;; vector of entry maps (append-only)
   head-hash ;; hash of last entry (or genesis-hash if empty)
   snapshots ;; {tick -> world-snapshot}
   ])

(defn empty-ledger
  "Construct an empty ledger."
  []
  (->Ledger [] genesis-hash {}))

;; ---------------------------------------------------------------------------
;; Append
;; ---------------------------------------------------------------------------

(defn append
  "Append an event to the ledger. Returns new Ledger.
   Computes and stores the chain hash for this entry."
  [^Ledger ledger event]
  (let [prev  (:head-hash ledger)
        h     (compute-entry-hash prev event)
        entry {:event      event
               :tick       (:tick event)
               :prev-hash  prev
               :entry-hash h}]
    (-> ledger
        (update :entries conj entry)
        (assoc  :head-hash h))))

;; ---------------------------------------------------------------------------
;; Snapshot store
;; ---------------------------------------------------------------------------

(defn store-snapshot
  "Store a world snapshot at tick t. Used as seek anchors."
  [^Ledger ledger t snapshot]
  (assoc-in ledger [:snapshots t] snapshot))

(defn nearest-snapshot
  "Find the snapshot with the largest tick <= target-tick.
   Returns [snapshot-tick snapshot] or nil."
  [^Ledger ledger target-tick]
  (->> (:snapshots ledger)
       (filter #(<= (key %) target-tick))
       (sort-by key)
       last))

;; ---------------------------------------------------------------------------
;; Query
;; ---------------------------------------------------------------------------

(defn entries-for
  "All ledger entries involving entity eid."
  [^Ledger ledger eid]
  (filter #(contains? (get-in % [:event :entities]) eid)
          (:entries ledger)))

(defn entries-of-kind
  "All ledger entries of a given event kind."
  [^Ledger ledger kind]
  (filter #(= (get-in % [:event :kind]) kind)
          (:entries ledger)))

(defn entries-between
  "All ledger entries with tick in [from-tick to-tick] inclusive."
  [^Ledger ledger from-tick to-tick]
  (filter #(<= from-tick (:tick %) to-tick)
          (:entries ledger)))

(defn events-since
  "Convenience: raw events since tick t."
  [^Ledger ledger t]
  (map :event (filter #(>= (:tick %) t) (:entries ledger))))

;; ---------------------------------------------------------------------------
;; Chain verification
;; ---------------------------------------------------------------------------

(defn valid-chain?
  "Verify the integrity of the hash chain."
  [^Ledger ledger]
  (loop [entries (:entries ledger)
         prev    genesis-hash]
    (if-let [{:keys [event prev-hash entry-hash]} (first entries)]
       (if (and (= prev-hash prev)
                (= entry-hash (compute-entry-hash prev event)))
        (recur (next entries) entry-hash)
        false)
      true)))

;; ---------------------------------------------------------------------------
;; Merkle root
;; ---------------------------------------------------------------------------

(defn merkle-root
  "Compute a Merkle root over all entry hashes."
  [^Ledger ledger]
  (let [hashes (mapv :entry-hash (:entries ledger))]
    (loop [hs hashes]
      (cond
        (empty? hs)      genesis-hash
        (= 1 (count hs)) (first hs)
        :else
        (let [pairs (partition-all 2 hs)
              next  (mapv (fn [[a b]] (sha256 (str a (or b a)))) pairs)]
          (recur next))))))
