● Update the render tests to the element-resolved model and new arity:

● Update(test/infra/render_test.clj)
  ⎿  Added 18 lines, removed 8 lines
      106        (is (= 0.001 (units/phys->render-radius ctx nil))))))
      107  
      108  (deftest test-composition->material-color
      109 -  (testing "Composition drives material colour"               
      110 -    (let [rock (r/composition->material-color {:metals 1.0})  
      111 -          ice  (r/composition->material-color {:ice 1.0})     
      112 -          gas  (r/composition->material-color {:H 0.75 :He 0.2
          -5})]                                                          
      109 +  (testing "Element-resolved composition drives material colou
          +r via bulk categories"                                        
      110 +    ;; Fe/Si condense to rock+metal below ~1300 K; O freezes t
          +o ice below 170 K;                                            
      111 +    ;; H/He stay gas. Colour is derived from the condensation 
          +partition, not from                                           
      112 +    ;; retired :metals/:ice keys.                             
      113 +    (let [rock (r/composition->material-color {:Fe 0.5 :Si 0.5
          +} 300.0)                                                      
      114 +          ice  (r/composition->material-color {:O 1.0} 100.0) 
      115 +          gas  (r/composition->material-color {:H 0.75 :He 0.2
          +5} 5000.0)]                                                   
      116        (is (every? #(<= 0.0 % 1.0) rock))
      117        (is (> (first rock) (nth rock 2)) "rock is warm (red > b
           lue)")
      118        (is (> (nth ice 2) (first ice)) "ice is cold (blue > red
           )")
      116 -      (is (not= rock gas) "different composition → different c
          -olour"))))                                                    
      119 +      (is (not= rock gas) "different composition → different c
          +olour")))                                                     
      120 +  (testing "The same rocky composition renders rock when cold,
          + not gas-tan"                                                 
      121 +    ;; regression: composition used to read retired keys → eve
          +ry body rendered                                              
      122 +    ;; gas-tan regardless of composition.                     
      123 +    (let [rock (r/composition->material-color {:Fe 0.5 :Si 0.5
          +} 300.0)                                                      
      124 +          gas  (r/composition->material-color {:H 0.75 :He 0.2
          +5} 5000.0)]                                                   
      125 +      (is (< (first rock) (first gas))                        
      126 +          "cold Fe/Si is darker than pale H/He gas — not ident
          +ical tan"))))                                                 
      127  
      128  (deftest test-body-render-color
      129    (testing "Cold bodies show material; hot bodies glow thermal
           ly"
      120 -    (let [rocky-cold (r/body-render-color 200.0 {:metals 1.0})
      121 -          rocky-hot  (r/body-render-color 5.0e6 {:metals 1.0})
      122 -          material   (r/composition->material-color {:metals 1
          -.0})]                                                         
      130 +    (let [rocky-cold (r/body-render-color 200.0 {:Fe 0.5 :Si 0
          +.5})                                                          
      131 +          rocky-hot  (r/body-render-color 5.0e6 {:Fe 0.5 :Si 0
          +.5})                                                          
      132 +          material   (r/composition->material-color {:Fe 0.5 :
          +Si 0.5} 200.0)]                                               
      133        (is (= rocky-cold material) "a cold rocky body is its ma
           terial colour")
      134        (is (not= rocky-hot material) "a hot body crossfades tow
           ard thermal colour"))))
      135  

● Now a test that the default world seeds metals (the actual unlock). Let me
  check the genesis test file:

  Ran 1 shell command (ctrl+o to expand)

● Update(test/domain/genesis_test.clj)  ⎿  Added 1 line
      10     [domain.chemistry        :as chemistry]
      11     [domain.player           :as player]
      12     [law.stellar             :as law]
      13 +   [law.composition         :as lcomp]                         
      14     [domain.ecs.core         :as ecs]
      15     [domain.ecs.event        :as event]
      16     [domain.ecs.components    :as c]

● Update(test/domain/genesis_test.clj)
  ⎿  Added 24 lines
      29      (let [region {:density 5500 :temperature 300 :radius 1e5}]
      30        (is (not (stellar/jeans-unstable? region))))))
      31  
      32 +(defn- first-parcel-composition                                
      33 +  "Composition of the first matter parcel in a freshly seeded w
         +orld."                                                         
      34 +  [world]                                                      
      35 +  (let [eid (first (ecs/entities-with world c/matter-state c/co
         +mposition))]                                                   
      36 +    (ecs/get-component world eid c/composition)))              
      37 +                                                               
      38 +(deftest test-metallicity-seeding                              
      39 +  (testing "Default world seeds the Population-I floor so metal
         +s exist from tick 0"                                           
      40 +    ;; This is the unlock for planet formation: planet-seeds de
         +rives solid                                                    
      41 +    ;; surface density from Z = metallicity(star composition); 
         +a metal-free                                                   
      42 +    ;; nebula gives Z≈0, sigma-solid≈0, and NO planets ever see
         +d.                                                             
      43 +    (let [w (genesis/create-world {:gas-count 20})             
      44 +          comp (first-parcel-composition w)]                   
      45 +      (is (= :population-i (:genesis/metallicity w)))          
      46 +      (is (> (lcomp/metallicity comp) 0.01) "cloud carries sola
         +r metals (Z≈0.0167)")                                          
      47 +      (is (> (double (get comp :Fe 0.0)) 0.0) "iron is present 
         +for rocky cores")                                              
      48 +      (is (> (double (get comp :Si 0.0)) 0.0) "silicon is prese
         +nt for silicates")                                             
      49 +      (is (lcomp/composition-sums-to-unity? comp))))           
      50 +  (testing ":primordial preset seeds a metal-free first-generat
         +ion cloud"                                                     
      51 +    (let [w (genesis/create-world {:gas-count 20 :metallicity :
         +primordial})                                                   
      52 +          comp (first-parcel-composition w)]                   
      53 +      (is (< (lcomp/metallicity comp) 1e-4) "no metals in a pri
         +mordial cloud")                                                
      54 +      (is (lcomp/composition-sums-to-unity? comp)))))          
      55 +                                                               
      56  (deftest test-virial-collapse-drives-ignition
      57    (testing "Virial temperature and self-gravity pressure rise a
          s a core contracts"
      58      (let [m 2e30]

● Now run the affected tests to check the changes hold:

  Ran 1 shell command (ctrl+o to expand)

● Both pass. Now the critical end-to-end check — does the seeder actually
  produce planets now? Let me run the formation and planet-formation suites (the
  agent flagged formation_integration_test as the end-to-end seeding test):

  Ran 1 shell command (ctrl+o to expand)

● All green. Now let me directly prove the unlock: the same star seeds planets
  with a Pop-I composition but seeds nothing with a primordial one. Let me check
  the ECS mutation API, then run the comparison.

  Read 1 file, ran 2 shell commands (ctrl+o to expand)

● Write(/tmp/claude-1000/-home-err-spaces-Truth/6acadd8f-ee31-4957-9317-ca96deb4
       d2e4/scratchpad/seed-check.clj)
  ⎿  Wrote 42 lines (ctrl+o to expand)

  Ran 1 shell command (ctrl+o to expand)

● Zero for both — my synthetic world is mis-scaled, not a code problem (the
  integration test seeds fine). Let me mirror the known-good setup from
  formation_integration_test:

  Ran 2 shell commands (ctrl+o to expand)

● My synthetic disk was 100× too massive (min-core-m blows up). Let me mirror
  the known-good scale (disk-mass 1e27) and vary only the star's composition —
  that isolates the metallicity effect on seeding:

● Update(/tmp/claude-1000/-home-err-spaces-Truth/6acadd8f-ee31-4957-9317-ca96deb
        4d2e4/scratchpad/seed-check.clj)
  ⎿  Added 3 lines, removed 3 lines (ctrl+o to expand)

  Ran 1 shell command (ctrl+o to expand)

● Definitive proof of the unlock: the identical star seeds a terrestrial planet 
  (Fe=0.30, Si=0.16 — rocky/metal-rich) + an ice-giant with Pop-I composition,
  and zero planets with primordial. Now confirm the render fix colors that
  terrestrial planet as rock, not gas-tan:

  Ran 1 shell command (ctrl+o to expand)

● Both bugs surface: the render fn read retired keys and bulk-categories itself
  is broken — partition-solids normalizes each partition to sum to 1.0, so
  gas-frac reads as 1.0 whenever any gas element exists. Everything reads as
  gas. Let me check for existing tests before fixing bulk-categories:

  Ran 1 shell command (ctrl+o to expand)

● Let me read the existing bulk-categories test to make sure my fix keeps its
  contract:

  Read 1 file (ctrl+o to expand)

● The existing test only checks sum-to-one, which my fix preserves.
  partition-solids' independent normalization is intentional (each partition is
  a valid composition) — the bug is that bulk-categories derives fractions from
  those normalized maps. Let me fix bulk-categories to classify from the
  original composition:

● Update(src/domain/chemistry.clj)
  ⎿  Added 29 lines, removed 20 lines
      122  
      123  (defn bulk-categories
      124    "Return the fractional bulk categories `{:gas :rock :metal :
           ice}` for a
      125 -   composition at `temperature`. Categories are derived from t
          -he condensed                                                  
      126 -   partition; gas is the gas fraction, rock/ice/metal split th
          -e solids."                                                    
      125 +   composition at `temperature`, normalized to sum to 1.0.    
      126 +                                                              
      127 +   Each element's mass fraction is classed by whether it is co
          +ndensed at                                                    
      128 +   `temperature` (Lodders `Tc`): condensed C/N/O → ice, Fe/Ni 
          +→ metal, other                                                
      129 +   rock-formers → rock; everything gaseous (and condensed gas-
          +formers like frozen                                           
      130 +   H/He/Ne, which have no solid category) → gas. Derives fract
          +ions from the                                                 
      131 +   original composition — NOT from `partition-solids`, whose :
          +solid/:gas maps are                                           
      132 +   each independently normalized and so cannot report the soli
          +d/gas split."                                                 
      133    [composition temperature]
      128 -  (let [{:keys [solid gas]} (partition-solids composition temp
          -erature)                                                      
      129 -        gas-frac (double (reduce + 0.0 (vals gas)))           
      130 -        solid-frac (- 1.0 gas-frac)                           
      131 -        solid-sum (double (reduce + 0.0 (vals solid)))        
      132 -        frac-of-solid (if (pos? solid-sum)                    
      133 -                        (fn [els]                             
      134 -                          (/ (double (reduce + 0.0 (map #(get 
          -solid % 0.0) els)))                                           
      135 -                             solid-sum))                      
      136 -                        (constantly 0.0))                     
      137 -        ice-frac (* solid-frac (frac-of-solid lcomp/ice-former
          -s))                                                           
      138 -        metal-frac (* solid-frac (frac-of-solid #{:Fe :Ni}))  
      139 -        rock-frac (* solid-frac (frac-of-solid (clojure.set/di
          -fference                                                      
      140 -                                                lcomp/rock-for
          -mers                                                          
      141 -                                                #{:Fe :Ni})))]
      142 -    {:gas   gas-frac                                          
      143 -     :rock  rock-frac                                         
      144 -     :metal metal-frac                                        
      145 -     :ice   ice-frac}))                                       
      134 +  (let [temp  (double temperature)                            
      135 +        total (double (reduce + 0.0 (vals composition)))      
      136 +        classify (fn [k]                                      
      137 +                   (let [tc (double (get lcomp/condensation-te
          +mperatures k 50.0))]                                          
      138 +                     (if (< temp tc)                          
      139 +                       (cond                                  
      140 +                         (contains? lcomp/ice-formers k)  :ice
      141 +                         (contains? #{:Fe :Ni} k)         :met
          +al                                                            
      142 +                         (contains? lcomp/rock-formers k) :roc
          +k                                                             
      143 +                         :else                            :gas
          +) ;; frozen gas-former                                        
      144 +                       :gas)))                                
      145 +        sums (reduce-kv (fn [m k v] (update m (classify k) + (
          +double v)))                                                   
      146 +                        {:gas 0.0 :rock 0.0 :metal 0.0 :ice 0.
          +0}                                                            
      147 +                        composition)]                         
      148 +    (if (pos? total)                                          
      149 +      (let [inv (/ 1.0 total)]                                
      150 +        {:gas   (* (:gas sums) inv)                           
      151 +         :rock  (* (:rock sums) inv)                          
      152 +         :metal (* (:metal sums) inv)                         
      153 +         :ice   (* (:ice sums) inv)})                         
      154 +      {:gas 0.0 :rock 0.0 :metal 0.0 :ice 0.0})))             
      155  
      156  (defn condensed-inventory
      157    "Combine `partition-solids` and `bulk-categories` into a sin
           gle report:

  Ran 1 shell command (ctrl+o to expand)

● Colors now differentiate: terrestrial reads warm grey-brown [0.63 0.56 0.47],
  gas stays tan. Let me run the affected suites plus the architecture guard to
  confirm no regressions from the bulk-categories change:
  Ran 1 shell command (ctrl+o to expand)

● All green including the architecture guard (infra→domain.chemistry is legal).
  Let me run the full suite to catch anything depending on the old defaults:

  Ran 1 shell command (ctrl+o to expand)

● Full suite is running. While it does, let me check whether biogenesis will
  actually fire for a seeded planet — the user wants to observe that next. Let
  me look at the habitability → life-emergence wiring:

  Read 1 file, ran 1 shell command (ctrl+o to expand)

● The wiring reveals the next blocker for biogenesis. Let me check the
  full-suite result and then verify whether a seeded planet actually passes the
  ecology-adoption gate.

  Read 1 file, ran 1 shell command (ctrl+o to expand)

● Suite still running (output buffered). Let me verify the biogenesis-gate
  hypothesis independently — does a seeded terrestrial planet pass
  planet-habitable?'s score > 0.2 gate at its seed temperature of 250 K?

  Ran 1 shell command (ctrl+o to expand)

● Confirmed: a seeded terrestrial planet at its 250 K / no-pressure seed state
  scores exactly 0.20, just under the > 0.2 adoption gate — so no ecology is
  adopted and biogenesis can't start. That's the next blocker, and it's a tuning
  decision you'll want to own. Let me confirm the full suite passed before I
  report.

  Ran 1 shell command (ctrl+o to expand)

