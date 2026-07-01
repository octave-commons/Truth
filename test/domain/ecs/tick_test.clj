(ns domain.ecs.tick-test
  (:require
    [clojure.test :refer [deftest is testing]]
    [domain.ecs.core :as ecs]
    [domain.ecs.tick :as tick]))

(def ca :component/a)
(def cb :component/b)
(def cc :component/c)

(defn- world-2
  "Two entities 0 and 1, each carrying components a=10/11 and b=20/21."
  []
  (let [[w e0] (ecs/spawn (ecs/empty-world))
        [w e1] (ecs/spawn w)]
    [(-> w
         (ecs/put-component e0 ca 10) (ecs/put-component e0 cb 20)
         (ecs/put-component e1 ca 11) (ecs/put-component e1 cb 21))
     e0 e1]))

;; --- write-set folding ------------------------------------------------------

(deftest apply-write-set-assoc-and-remove
  (let [[w e0 e1] (world-2)
        w' (tick/apply-write-set w {ca {e0 99}
                                    cc {e1 7}
                                    cb {e0 tick/removed}})]
    (testing "assoc updates an existing cell"
      (is (= 99 (ecs/get-component w' e0 ca))))
    (testing "assoc adds a new component (and updates the archetype index)"
      (is (= 7 (ecs/get-component w' e1 cc)))
      (is (contains? (ecs/archetype w' e1) cc)))
    (testing "removed sentinel dissocs the cell and de-indexes it"
      (is (nil? (ecs/get-component w' e0 cb)))
      (is (not (contains? (ecs/archetype w' e0) cb))))
    (testing "untouched cells are preserved"
      (is (= 11 (ecs/get-component w' e1 ca))))))

;; --- order independence (the whole point) -----------------------------------

(defn- sys [id ctype f]
  ;; A write-set system that maps f over every cell of `ctype` in the frozen world.
  {:id id :writes #{ctype}
   :run (fn [w] {ctype (reduce-kv (fn [m eid v] (assoc m eid (f v)))
                                  {} (get-in w [:components ctype]))})})

(deftest parallel-equals-sequential-and-is-order-independent
  (let [[w] (world-2)
        a-inc  (sys :a-inc ca inc)
        b-dbl  (sys :b-dbl cb #(* 2 %))
        c-set  {:id :c-set :writes #{cc}
                :run (fn [_] {cc {0 :x 1 :y}})}
        systems [a-inc b-dbl c-set]
        par  (tick/run-parallel w systems)
        seqv (tick/run-sequential w systems)
        ;; reversed and rotated orders must yield identical worlds
        rev  (tick/run-parallel w (reverse systems))
        rot  (tick/run-parallel w [c-set a-inc b-dbl])]
    (testing "parallel == sequential for disjoint systems"
      (is (= (:components par) (:components seqv))))
    (testing "result is independent of system order"
      (is (= (:components par) (:components rev)))
      (is (= (:components par) (:components rot))))
    (testing "each system applied its transform reading the ORIGINAL snapshot"
      (is (= {0 11 1 12} (get-in par [:components ca])))   ;; inc of 10,11
      (is (= {0 40 1 42} (get-in par [:components cb])))   ;; double of 20,21
      (is (= {0 :x 1 :y} (get-in par [:components cc]))))))

(deftest systems-cannot-see-each-others-writes
  ;; b reads a from the frozen snapshot; even if a is rewritten this tick,
  ;; b must see the OLD a (Jacobi, one-tick latency).
  (let [[w] (world-2)
        set-a {:id :set-a :writes #{ca} :run (fn [_] {ca {0 1000 1 1000}})}
        copy-a-to-c {:id :copy :writes #{cc}
                     :run (fn [frozen]
                            {cc (get-in frozen [:components ca])})}
        out (tick/run-parallel w [set-a copy-a-to-c])]
    (is (= {0 1000 1 1000} (get-in out [:components ca])) "a was rewritten")
    (is (= {0 10 1 11} (get-in out [:components cc]))
        "c copied the FROZEN a (10,11), not set-a's same-tick write (1000)")))

;; --- conflict detection (runtime single-writer) -----------------------------

(deftest conflicts-are-detected-and-policied
  (let [[w] (world-2)
        w1 {:id :w1 :writes #{ca} :run (fn [_] {ca {0 1}})}
        w2 {:id :w2 :writes #{ca} :run (fn [_] {ca {0 2}})}]
    (testing "default :throw rejects two writers of the same component type"
      (is (thrown-with-msg? clojure.lang.ExceptionInfo #"single-writer"
            (tick/run-parallel w [w1 w2]))))
    (testing "colliding-ctypes names the offenders"
      (is (= {ca [:w1 :w2]}
             (tick/colliding-ctypes [[:w1 {ca {0 1}}] [:w2 {ca {0 2}}]]))))
    (testing ":last-wins resolves in seq order (transitional migration policy)"
      (is (= 2 (-> (tick/run-parallel w [w1 w2] :on-conflict :last-wins)
                   (ecs/get-component 0 ca)))))))

;; --- legacy bridge ----------------------------------------------------------

(deftest legacy-system-extracts-masked-write-set
  (let [[w e0 _e1] (world-2)
        ;; A legacy system that legitimately writes `a`, but ALSO scribbles on
        ;; `b` which it does not own. Only the `a` change must survive.
        legacy (fn [world]
                 (-> world
                     (ecs/put-component e0 ca 111)
                     (ecs/put-component e0 cb -1)))    ;; undeclared write
        s   (tick/legacy-system :legacy #{ca} legacy)
        ws  ((:run s) w)]
    (testing "diff is restricted to owned component types"
      (is (= {ca {e0 111}} ws))
      (is (not (contains? ws cb)) "undeclared write to b is dropped"))
    (testing "folding the masked write-set leaves b untouched"
      (let [out (tick/run-parallel w [s])]
        (is (= 111 (ecs/get-component out e0 ca)))
        (is (= 20  (ecs/get-component out e0 cb)))))))

(deftest legacy-bridge-captures-removals
  (let [[w e0 e1] (world-2)
        drop-a (fn [world] (ecs/remove-component world e0 ca))
        s      (tick/legacy-system :drop #{ca} drop-a)
        out    (tick/run-parallel w [s])]
    (is (= {ca {e0 tick/removed}} ((:run s) w)) "removal surfaces as sentinel")
    (is (nil? (ecs/get-component out e0 ca)))
    (is (= 11 (ecs/get-component out e1 ca)) "other cells untouched")))
