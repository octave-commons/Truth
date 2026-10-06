;; Isolated bootstrap determinism check; never touches the live service.
(require '[domain.genesis :as g]
         '[domain.ecs.components :as c])
(let [a (g/create-world {:gas-count 4 :seed 41})
      b (g/create-world {:gas-count 4 :seed 43})
      components [c/position c/velocity c/mass c/matter-state]]
  (prn {:case :create-world-seed-forwarding
        :requested-seeds [41 43]
        :gas-count 4
        :same-components? (= (select-keys (:components a) components)
                             (select-keys (:components b) components))
        :same-positions? (= (get-in a [:components c/position])
                            (get-in b [:components c/position]))
        :first-position (first (get-in a [:components c/position]))}))
(shutdown-agents)
