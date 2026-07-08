(ns law.mass-transfer.schema
  "Mass transfer schemas and contracts."
  (:require
   [law.contract :as contract]))

(def accretion-radius-schema
  "Capture radius and ambient conditions for a sink."
  [:map
   [:sink/r-acc number?]
   [:sink/r-bondi number?]
   [:sink/ambient-density number?]
   [:sink/ambient-cs number?]
   [:sink/relative-velocity number?]])

(def accretion-rate-schema
  "Mass flux and regime for a sink."
  [:map
   [:sink/dot-m number?]
   [:sink/dot-m-this-tick number?]
   [:sink/efficiency number?]
   [:sink/regime keyword?]])

(def binary-pair-schema
  "A relation entity linking a donor and an accretor."
  [:map
   [:binary-pair/donor int?]
   [:binary-pair/accretor int?]
   [:orbit/semi-major-axis number?]
   [:orbit/eccentricity number?]])

(def roche-lobe-schema
  "Roche-lobe geometry and overflow state."
  [:map
   [:roche-lobe/radius number?]
   [:roche-lobe/overfilling number?]
   [:roche-lobe/overflow? boolean?]])

(def mass-transfer-rate-schema
  "Signed rate and accreted fraction for RLOF."
  [:map
   [:mass-transfer/rate number?]
   [:mass-transfer/accreted-fraction number?]])

(def accretion-radius-contract
  "Capture radius and ambient conditions for a sink."
  (contract/->contract
   {:id       :law.mass-transfer/accretion-radius
    :shape-id :law.mass-transfer/accretion-radius
    :kind     :type
    :schema   accretion-radius-schema}))

(def accretion-rate-contract
  "Mass flux and regime for a sink."
  (contract/->contract
   {:id       :law.mass-transfer/accretion-rate
    :shape-id :law.mass-transfer/accretion-rate
    :kind     :type
    :schema   accretion-rate-schema}))

(def binary-pair-contract
  "A relation entity linking a donor and an accretor."
  (contract/->contract
   {:id       :law.mass-transfer/binary-pair
    :shape-id :law.mass-transfer/binary-pair
    :kind     :type
    :schema   binary-pair-schema}))

(def roche-lobe-contract
  "Roche-lobe geometry and overflow state."
  (contract/->contract
   {:id       :law.mass-transfer/roche-lobe
    :shape-id :law.mass-transfer/roche-lobe
    :kind     :type
    :schema   roche-lobe-schema}))

(def mass-transfer-rate-contract
  "Signed rate and accreted fraction for RLOF."
  (contract/->contract
   {:id       :law.mass-transfer/mass-transfer-rate
    :shape-id :law.mass-transfer/mass-transfer-rate
    :kind     :type
    :schema   mass-transfer-rate-schema}))
