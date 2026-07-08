● Good — clear direction: build Phase 1 and 2 together, make the parcels the
  disk, and write the design doc first. Let me set up tracking and ground myself
  in the exact files I'll be touching before I write the doc.

  Read 2 files (ctrl+o to expand)

● Now I have the exact culprit in hand — mass-kg (min (* 0.3 ann-mass) ...) with
  ann-mass = disk-m/12. A ~0.05 M☉ disk yields ~420 M⊕ per planet. That's the
  400 M⊕ terrestrial, precisely. The fix is to replace that line with the
  isolation mass.

  Let me set up task tracking and write the design doc.
● Now the design doc — the durable artifact grounding everything.

● Write(docs/designs/planetary-disk-rework.md)
  ⎿  Wrote 206 lines to docs/designs/planetary-disk-rework.md
       1 # Design: Planetary Disk Rework — the disk becomes matter
       2 
       3 **Status:** design (decided 2026-07-07)
       5 **Relates to:** `docs/specs/radial-disk-structure-realspec.md` (
         deferred — this
       6 supersedes/realizes its intent), `docs/specs/seed-and-grow-conde
         nsation-realspec.md`,
       7 `docs/specs/protoplanetary-disk-planet-formation-realspec.md`,
       9 `docs/designs/resolution-regimes-and-scale-coupling.md`,
      10 `docs/designs/ux-architecture.md`.
     … +309 lines (ctrl+o to expand)

● Design doc written. Now the receipt (house rule), then straight into Phase 1.
  Let me leave the trace and read the exact render code I'm about to fix.

  Read 1 file, ran 1 shell command (ctrl+o to expand)

● The offset bug is exactly as diagnosed: rotate-axis-angle is applied to
  (:position p) — the absolute position center + offset — rotating it about the
  world origin. Let me fix it cleanly and dedupe the two copies at the same time
  (jscpd would flag the duplication).

● Update(src/infra/render.clj)
  ⎿  Added 30 lines, removed 34 lines
      1184            :render-mode :particle}))
      1185       (range cnt))))
      1186  
      1187 +(defn- tilt-disk-to-normal                                   
      1188 +  "Rotate disk particles from the generated xy-plane onto the
           + disk's actual                                               
      1189 +   orientation `normal`. Each particle is rotated about its o
           +ffset from `center`                                          
      1190 +   (not its absolute world position), so a tilted disk stays 
           +centred on its host.                                         
      1191 +   Rotating the absolute position instead sends `p → R·(cente
           +r + offset)`, which                                          
      1192 +   slides the whole disk off the star by `R·center − center` 
           +— the offset bug."                                           
      1193 +  [particles center normal]                                  
      1194 +  (let [normal-u (unit-vec normal)                           
      1195 +        ez       [0.0 0.0 1.0]                               
      1196 +        dot      (sp/dot normal-u ez)]                       
      1197 +    (if (> (Math/abs dot) 0.999)                             
      1198 +      particles ;; already (anti-)aligned with z, no tilt nee
           +ded                                                          
      1199 +      (let [axis  (unit-vec (sp/cross ez normal-u))          
      1200 +            angle (Math/acos (max -1.0 (min 1.0 dot)))]      
      1201 +        (mapv (fn [p]                                        
      1202 +                (assoc p :position                           
      1203 +                       (vec (sp/v+ center                    
      1204 +                                   (rotate-axis-angle        
      1205 +                                    (sp/v- (:position p) cent
           +er) axis angle)))))                                          
      1206 +              particles)))))                                 
      1207 +                                                             
      1208  (defn- disk-particles
      1209    "Protoplanetary disk as a flat torus of particles, determin
            istic and cached.
      1210     `center` in render units; `r-inner`/`r-outer` in render un
            its; `normal` is
     ...
      1216    (let [eid    (long (or seed 1))
      1217          ckey   [eid :disk cnt]
      1218          params {:center center :r-inner r-inner :r-outer r-ou
            ter
      1198 -                :color color :disk-mass disk-mass}]          
      1219 +                :color color :disk-mass disk-mass}           
      1220 +        build  (fn []                                        
      1221 +                 (let [thick   (max 0.02 (* 0.08 (- (double r
           +-outer) (double r-inner))))                                  
      1222 +                       raw     (disk-particles* (assoc params
           + :thickness thick) cnt seed)                                 
      1223 +                       rotated (tilt-disk-to-normal raw cente
           +r normal)]                                                   
      1224 +                   (swap! disk-cache assoc ckey (assoc params
           + :particles rotated))                                        
      1225 +                   rotated))]                                
      1226      (if-let [cached (get @disk-cache ckey)]
      1227        (if (= (dissoc cached :particles) params)
      1228          (:particles cached)
      1202 -        (let [thick  (max 0.02 (* 0.08 (- (double r-outer) (d
           -ouble r-inner))))                                            
      1203 -              raw    (disk-particles* (assoc params :thicknes
           -s thick) cnt seed)                                           
      1204 -              ;; Rotate from xy-plane to the actual disk orie
           -ntation                                                      
      1205 -              normal-u (unit-vec normal)                     
      1206 -              ez       [0.0 0.0 1.0]                         
      1207 -              dot      (sp/dot normal-u ez)                  
      1208 -              rotated  (if (> (Math/abs dot) 0.999)          
      1209 -                         raw  ;; already (anti-)aligned, no r
           -otation needed                                               
      1210 -                         (let [axis  (unit-vec (sp/cross ez n
           -ormal-u))                                                    
      1211 -                               angle (Math/acos (max -1.0 (mi
           -n 1.0 dot)))]                                                
      1212 -                           (mapv (fn [p]                     
      1213 -                                   (assoc p :position        
      1214 -                                          (vec (rotate-axis-a
           -ngle                                                         
      1215 -                                                (:position p)
           - axis angle))))                                              
      1216 -                                 raw)))]                     
      1217 -          (swap! disk-cache assoc ckey (assoc params :particl
           -es rotated))                                                 
      1218 -          rotated))                                          
      1219 -      (let [thick  (max 0.02 (* 0.08 (- (double r-outer) (dou
           -ble r-inner))))                                              
      1220 -            raw    (disk-particles* (assoc params :thickness 
           -thick) cnt seed)                                             
      1221 -            normal-u (unit-vec normal)                       
      1222 -            ez       [0.0 0.0 1.0]                           
      1223 -            dot      (sp/dot normal-u ez)                    
      1224 -            rotated  (if (> (Math/abs dot) 0.999)            
      1225 -                       raw                                   
      1226 -                       (let [axis  (unit-vec (sp/cross ez nor
           -mal-u))                                                      
      1227 -                             angle (Math/acos (max -1.0 (min 
           -1.0 dot)))]                                                  
      1228 -                         (mapv (fn [p]                       
      1229 -                                 (assoc p :position          
      1230 -                                        (vec (rotate-axis-ang
           -le                                                           
      1231 -                                              (:position p) a
           -xis angle))))                                                
      1232 -                               raw)))]                       
      1233 -        (swap! disk-cache assoc ckey (assoc params :particles
           - rotated))                                                   
      1234 -        rotated))))                                          
      1229 +        (build))                                             
      1230 +      (build))))                                             
      1231  
      1232  ;; --- Player spark + focus reticle (the interactive overlay)
             ------------------
      1233  

● Offset bug fixed and the two duplicated branches collapsed into one.

  Now the highest-value fix: the isolation mass. Let me look at law/stellar.clj
  to add the physics where constants live, then wire it into the seeder.

  Read 1 file (ctrl+o to expand)

● Now I'll add the isolation-mass law. It belongs in law/stellar.clj with the
  other physical constants. Let me insert it after softened-circular-speed.

● Update(src/law/stellar.clj)
  ⎿  Added 42 lines
       87        (Math/sqrt (/ (* G M r r) (Math/pow d2 1.5)))
       88        0.0)))
       89  
       90 +;; Feeding-zone half-width in Hill radii. An oligarch clears s
          +olids out to a few                                            
       91 +;; Hill radii on each side; the canonical spacing is ~2√3 (Lis
          +sauer 1993).                                                  
       92 +(def ^:const feeding-zone-hill-factor 3.46) ;; ≈ 2√3          
       93 +                                                              
       94 +(defn hill-radius                                             
       95 +  "Hill (Roche) radius of a body of mass `m` orbiting mass `M-
          +star` at radius `a`:                                          
       96 +   R_H = a·(m / 3 M_star)^(1/3) — the reach of the body's grav
          +ity against the                                               
       97 +   star's tide, and the natural width unit of its feeding zone
          +. Metres."                                                    
       98 +  [m M-star a]                                                
       99 +  (let [m (double (or m 0.0))                                 
      100 +        M (double (or M-star 0.0))                            
      101 +        a (double (or a 0.0))]                                
      102 +    (if (and (pos? m) (pos? M) (pos? a))                      
      103 +      (* a (Math/pow (/ m (* 3.0 M)) (/ 1.0 3.0)))            
      104 +      0.0)))                                                  
      105 +                                                              
      106 +(defn isolation-mass                                          
      107 +  "Planetesimal isolation mass (kg): the mass a growing body r
          +eaches once it has                                            
      108 +   swept up all the solids in its feeding zone and can grow no
          + further from local                                           
      109 +   material. This is the physical cap that prevents runaway gr
          +owth from producing                                           
      110 +   an arbitrarily large planet out of one annulus.            
      111 +                                                              
      112 +   Solving M_iso = 2π·a·(2·B·R_H)·Σ_solid self-consistently wi
          +th                                                            
      113 +   R_H = a·(M_iso/3M_star)^(1/3) gives (Lissauer 1993):       
      114 +                                                              
      115 +       M_iso = [4π·B·a²·Σ_solid]^(3/2) / (3·M_star)^(1/2)     
      116 +                                                              
      117 +   where B = `feeding-zone-hill-factor`. M_iso ∝ Σ_solid^(3/2)
          +·a³·M_star^(−1/2):                                            
      118 +   ~0.05–0.15 M⊕ at 1 AU (sub-Mars), rising to several–10 M⊕ a
          +t ~5 AU beyond the                                            
      119 +   ice line where Σ_solid jumps ~3–4×. A body may exceed this 
          +ONLY via runaway                                              
      120 +   gas accretion after reaching pebble-isolation mass beyond t
          +he ice line.                                                  
      121 +                                                              
      122 +   `a` in metres, `sigma-solid` in kg/m², `M-star` in kg. 0 fo
          +r degenerate input."                                          
      123 +  [a sigma-solid M-star]                                      
      124 +  (let [a  (double (or a 0.0))                                
      125 +        s  (double (or sigma-solid 0.0))                      
      126 +        M  (double (or M-star 0.0))]                          
      127 +    (if (and (pos? a) (pos? s) (pos? M))                      
      128 +      (/ (Math/pow (* 4.0 Math/PI feeding-zone-hill-factor a a
          + s) 1.5)                                                      
      129 +         (Math/sqrt (* 3.0 M)))                               
      130 +      0.0)))                                                  
      131 +                                                              
      132  (defn virial-speed
      133    "Characteristic gravitational speed √(G·M/R) (m/s) of a self
           -gravitating cloud
      134     of mass `M` and radius `R` — the velocity scale that balanc
           es self-gravity.

● Let me update task status, then write the test and wire the isolation mass
  into the seeder.

● Now the test. Let me find where law/planet-formation tests live.

  Searched for 2 patterns, read 1 file, listed 1 directory (ctrl+o to expand)
● The physics here has a subtlety I need to get right: isolation mass depends on
  the local surface density, and the current seeder computes sigma-gas as
  disk_mass/12 per annulus (equal mass per annulus over log-spaced radii) —
  which gives unphysical inner densities. To make isolation mass produce sane,
  radially-varying planet masses, I also need the disk to follow a proper MMSN
  profile Σ(r) ∝ r^(−3/2). Let me write the tests first (house rule), then
  implement both.

● Update(test/domain/formation_test.clj)
  ⎿  Added 43 lines
      124    (testing "a moderate-mass body beyond the snow line is an :i
           ce-giant"
      125      (is (= :ice-giant (pf/planet-type (* 5.0 au) 5000.0 (* 2.7
            au) 0.05)))))
      126  
      127 +;; --- Part 4: isolation mass caps runaway growth ------------
          +----------------                                              
      128 +                                                              
      129 +(deftest hill-radius-scales-with-mass-and-distance            
      130 +  (testing "Hill radius grows with orbital distance; Earth's i
          +s ~0.01 AU"                                                   
      131 +    (let [rh1 (law/hill-radius law/earth-mass law/solar-mass a
          +u)                                                            
      132 +          rh5 (law/hill-radius law/earth-mass law/solar-mass (
          +* 5.0 au))]                                                   
      133 +      (is (pos? rh1))                                         
      134 +      (is (> rh5 rh1) "farther orbit → larger Hill radius")   
      135 +      (is (< 0.005 (/ rh1 au) 0.02) (str "Earth R_H " (/ rh1 a
          +u) " AU")))))                                                 
      136 +                                                              
      137 +(deftest isolation-mass-sub-earth-at-1-au                     
      138 +  (testing "MMSN-like solids (~7 g/cm²) at 1 AU give a sub-Ear
          +th isolation mass"                                            
      139 +    (let [m (law/isolation-mass au 70.0 law/solar-mass)]      
      140 +      (is (pos? m))                                           
      141 +      (is (< (/ m law/earth-mass) 1.0) (str "M_iso " (/ m law/
          +earth-mass) " M⊕")))))                                        
      142 +                                                              
      143 +(deftest isolation-mass-monotonic                             
      144 +  (testing "isolation mass rises with surface density and with
          + orbital radius"                                              
      145 +    (is (> (law/isolation-mass au 140.0 law/solar-mass)       
      146 +           (law/isolation-mass au 70.0 law/solar-mass)))      
      147 +    (is (> (law/isolation-mass (* 5.0 au) 70.0 law/solar-mass)
      148 +           (law/isolation-mass au 70.0 law/solar-mass)))))    
      149 +                                                              
      150 +(deftest isolation-mass-zero-on-degenerate                    
      151 +  (testing "degenerate inputs give zero, never NaN"           
      152 +    (is (zero? (law/isolation-mass 0.0 70.0 law/solar-mass))) 
      153 +    (is (zero? (law/isolation-mass au 0.0 law/solar-mass)))   
      154 +    (is (zero? (law/isolation-mass au 70.0 0.0)))))           
      155 +                                                              
      156 +(deftest mmsn-profile-conserves-disk-mass                     
      157 +  (testing "∫Σ(r)·2πr dr over the disk returns the total disk 
          +mass"                                                         
      158 +    (let [disk-m 1.0e28 r-in (* 0.1 au) r-out (* 30.0 au)     
      159 +          s0 (pf/mmsn-sigma0 disk-m r-in r-out)               
      160 +          ;; numerically integrate the profile                
      161 +          n 4000                                              
      162 +          dr (/ (- r-out r-in) n)                             
      163 +          total (reduce + 0.0                                 
      164 +                        (for [i (range n)]                    
      165 +                          (let [r (+ r-in (* (+ i 0.5) dr))]  
      166 +                            (* (pf/mmsn-sigma s0 r) 2.0 Math/P
          +I r dr))))]                                                   
      167 +      (is (< (Math/abs (- (/ total disk-m) 1.0)) 0.01)        
      168 +          (str "integrated mass ratio " (/ total disk-m)))))) 
      169 +                                                              
      170  ;; --- Part 4: the seeder over a built disc ------------------
           ----------------
      171  
      172  (defn- build-disk-world

