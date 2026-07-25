(ns law.mass-transfer.validate
  "Mass transfer validators."
  (:require
   [law.contract :as contract]
   [law.mass-transfer.schema :as schema]))

(defn ^:export validate-accretion-radius
  "Validate x against the accretion-radius contract."
  [x] (contract/validate schema/accretion-radius-contract x))

(defn ^:export validate-accretion-rate
  "Validate x against the accretion-rate contract."
  [x] (contract/validate schema/accretion-rate-contract   x))

(defn ^:export validate-binary-pair
  "Validate x against the binary-pair contract."
  [x] (contract/validate schema/binary-pair-contract      x))

(defn ^:export validate-roche-lobe
  "Validate x against the roche-lobe contract."
  [x] (contract/validate schema/roche-lobe-contract       x))

(defn ^:export validate-mass-transfer-rate
  "Validate x against the mass-transfer-rate contract."
  [x] (contract/validate schema/mass-transfer-rate-contract x))
