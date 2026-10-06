(require '[clojure.edn :as edn]
         '[clojure.pprint :as pprint]
         '[criterium.core :as criterium])
(let [counts (criterium/outlier-count 0 1 2 3)
      printed (with-out-str (pprint/pprint counts))
      restored (edn/read-string printed)
      explicit {:record-type "criterium.core.OutlierCount" :fields (into {} counts)}
      explicit-printed (with-out-str (pprint/pprint explicit))]
  (prn {:actual-class (.getName (class counts))
        :record? (record? counts)
        :printed printed
        :roundtrip-is-record? (record? restored)
        :original-equal-roundtrip? (= counts restored)
        :explicit explicit
        :all-fields-preserved? (= (into {} counts) (:fields explicit))
        :explicit-roundtrip-equal? (= explicit (edn/read-string explicit-printed))}))
