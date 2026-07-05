(ns law.ledger-test
  (:require
   [clojure.test :refer [deftest is testing]]
   [domain.ecs.event :as event]
   [law.ledger :as ledger]))

(deftest empty-ledger-is-valid
  (testing "An empty ledger has the genesis hash and is valid"
    (let [l (ledger/empty-ledger)]
      (is (= [] (:entries l)))
      (is (string? (:head-hash l)))
      (is (ledger/valid-chain? l)))))

(deftest append-builds-chain
  (testing "Appending events builds a hash chain"
    (let [l    (ledger/empty-ledger)
          e    (event/->event {:tick 1 :kind :test :entities #{1}})
          l'   (ledger/append l e)
          e2   (event/->event {:tick 2 :kind :test :entities #{2}})
          l''  (ledger/append l' e2)]
      (is (= 1 (count (:entries l'))))
      (is (= 2 (count (:entries l''))))
      (is (ledger/valid-chain? l''))
      (is (not= (:head-hash l') (:head-hash l''))))))

(deftest tampered-chain-is-invalid
  (testing "Modifying an event breaks chain validity"
    (let [l   (ledger/empty-ledger)
          e   (event/->event {:tick 1 :kind :test :entities #{1}})
          l'  (ledger/append l e)
          bad (update-in l' [:entries 0 :event :kind] (constantly :hacked))]
      (is (not (ledger/valid-chain? bad))))))

(deftest merkle-root-summarizes-ledger
  (testing "Merkle root changes when events change"
    (let [l1  (ledger/empty-ledger)
          e1  (event/->event {:tick 1 :kind :a :entities #{1}})
          l1' (ledger/append l1 e1)
          e2  (event/->event {:tick 1 :kind :b :entities #{1}})
          l2' (ledger/append l1 e2)]
      (is (not= (ledger/merkle-root l1')
                (ledger/merkle-root l2'))))))

(deftest snapshot-storage-and-retrieval
  (testing "Snapshots are stored by tick and retrievable"
    (let [l  (ledger/empty-ledger)
          l' (ledger/store-snapshot l 5 {:foo :bar})]
      (is (= [5 {:foo :bar}] (ledger/nearest-snapshot l' 10)))
      (is (= [5 {:foo :bar}] (ledger/nearest-snapshot l' 5)))
      (is (nil? (ledger/nearest-snapshot l' 4))))))
