(ns domain.naming
  "Deterministic body names.

   A body's name is derived purely from its entity id, so it is stable for the
   body's whole life — the clump that condenses, contracts, ignites and burns
   keeps one name through every matter-state. Names are syllabic and evocative
   rather than catalog numbers: the explorer should read like a mythology in
   the making, not a database dump (ux-architecture.md: the shell explains the
   world; Journal>Mythology grows from what the player witnessed).

   Pure data; no IO, no randomness (hash-seeded — resume-safe)."
  (:require [clojure.string :as str]))

(def ^:private onsets
  ["k" "v" "th" "s" "m" "n" "r" "l" "z" "sh" "d" "t" "ph" "kh" "b" "g"])

(def ^:private nuclei
  ["a" "e" "i" "o" "u" "ae" "ia" "ei" "au" "y" "oa" "ua"])

(def ^:private codas
  ["" "n" "r" "l" "s" "th" "m" "x" "sh" ""])

(defn- pick [coll h shift]
  (nth coll (mod (bit-shift-right (long h) (long shift)) (count coll))))

(defn body-name
  "Deterministic syllabic name for entity `eid`. Two or three syllables chosen
   by the eid's hash; same eid always yields the same name."
  [eid]
  (let [h  (Math/abs (long (hash [:body-name eid])))
        n  (+ 2 (mod h 2))
        syl (fn [i]
              (str (pick onsets h (* i 7))
                   (pick nuclei h (+ 3 (* i 7)))
                   (if (= i (dec n)) (pick codas h (+ 5 (* i 7))) "")))
        raw (apply str (map syl (range n)))]
    (str/capitalize raw)))

(def state-titles
  "Display title per matter-state — what a body IS, for lists and cards."
  {:nebula      "gas"
   :protostar   "protostar"
   :star        "star"
   :brown-dwarf "brown dwarf"
   :planet      "planet"
   :debris      "planetesimal"})

(defn display-label
  "\"Name — title\" line for a body, e.g. \"Vetharion — star\"."
  [eid state]
  (str (body-name eid) " — " (get state-titles state (some-> state name))))
