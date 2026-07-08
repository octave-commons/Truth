● Now the key regression test — a massive (but physically realistic) disk must
  not spawn a hundreds-of-Earth-mass terrestrial:

● Update(test/domain/formation_test.clj)
  ⎿  Added 31 lines
      264                  (str "beyond snow line → giant, got " (:planet
           -type spec)))
      265              (is (= :terrestrial (:planet-type spec))
      266                  (str "inside snow line → terrestrial, got " (:
           planet-type spec)))))))))
      267 +                                                              
      268 +(deftest terrestrials-stay-small-on-a-massive-disk            
      269 +  (testing "isolation mass caps inner rocky planets — a massiv
          +e (0.05 M☉) disk                                              
      270 +            must NOT produce a hundreds-of-Earth-mass terrestr
          +ial (the reported bug)"                                       
      271 +    (let [[w star] (build-disk-world {:disk-mass 1.0e29})  ;; 
          +~0.05 M☉, ~1.7e4 M⊕                                           
      272 +          res (pf/planet-seeds w star)                        
      273 +          snow (pf/snow-line-radius law/solar-luminosity)     
      274 +          star-pos (ecs/get-component w star c/position)]     
      275 +      (is (seq (:spawns res)))                                
      276 +      (doseq [[_ spec] (:spawns res)]                         
      277 +        (let [r (sp/dist (:position spec) star-pos)           
      278 +              m-earth (/ (:mass spec) law/earth-mass)]        
      279 +          (when (<= r snow)                                   
      280 +            (is (< m-earth 10.0)                              
      281 +                (str "terrestrial at " (/ r au) " AU is " m-ea
          +rth                                                           
      282 +                     " M⊕ — isolation mass must cap it far bel
          +ow the old ~400 M⊕"))))))))                                   
      283 +                                                              
      284 +(deftest giant-cores-can-still-form-beyond-ice-line           
      285 +  (testing "beyond the ice line, runaway gas accretion still b
          +uilds a real giant                                            
      286 +            (isolation mass must not strangle giant formation)
          +"                                                             
      287 +    (let [[w star] (build-disk-world {:disk-mass 1.0e29})     
      288 +          res (pf/planet-seeds w star)                        
      289 +          snow (pf/snow-line-radius law/solar-luminosity)     
      290 +          star-pos (ecs/get-component w star c/position)      
      291 +          giants (for [[_ spec] (:spawns res)                 
      292 +                       :let [r (sp/dist (:position spec) star-
          +pos)]                                                         
      293 +                       :when (> r snow)]                      
      294 +                   spec)]                                     
      295 +      (is (seq giants) "at least one body seeds beyond the ice
          + line")                                                       
      296 +      (is (some #(> (/ (:mass %) law/earth-mass) 10.0) giants)
      297 +          "at least one giant grows past ~10 M⊕ via runaway ga
          +s accretion"))))                                              

● Now implement — the MMSN profile helpers and the isolation-mass-capped seeder:

● Update(src/domain/planet_formation.clj)
  ⎿  Added 14 lines, removed 1 line
      27  (def ^:const ice-enhancement-factor 3.5)
      28  (def ^:const min-planet-orbit-radius-au 0.1)
      29  (def ^:const planet-seeding-annuli 12)
      30 -(def ^:const min-seed-mass-solar 1.0e-6)                       
      30 +(def ^:const min-seed-mass-solar 1.0e-8) ;; ≈ 0.003 M⊕ (~0.2 Mo
         +on) — a real minimum                                           
      31 +                                         ;; body; isolation mas
         +s governs above it.                                            
      32  (def ^:const max-seed-mass-solar 13.0)
      33  (def ^:const disk-maturity-seconds 3.156e13) ;; 1 Myr default
      34  
      35 +(def ^:const critical-core-mass-kg                             
      36 +  "Pollack-critical core mass (~10 M⊕). Beyond the ice line a c
         +ore this heavy                                                 
      37 +   opens a gap, halts pebble flux, and triggers runaway gas acc
         +retion — the only                                              
      38 +   route to a planet far heavier than the local isolation mass.
         +"                                                              
      39 +  (* 10.0 law/earth-mass))                                     
      40 +                                                               
      41 +(def ^:const runaway-gas-fraction                              
      42 +  "Fraction of the remaining disk gas a runaway giant draws int
         +o its envelope                                                 
      43 +   once its core passes `critical-core-mass-kg`. Tunable proxy 
         +for the runaway                                                
      44 +   gas-accretion phase; caps a giant at a plausible sub-brown-d
         +warf mass."                                                    
      45 +  0.2)                                                         
      46 +                                                               
      47  ;; --- Condensation seed mass (seed-and-grow small bodies) ----
          -----------------
      48  ;; Real planetesimal-formation models (streaming instability) p
          roduce clumps of
      49  ;; ~100-km bodies, ~1e15–1e18 kg. We fix a toy-scale seed at th
          e high end of that

● Now add the MMSN profile helpers:

● Update(src/domain/planet_formation.clj)
  ⎿  Added 27 lines
      110    (* sigma-gas (double metal-frac)
      111       (if (> (double r) (double snow-line)) ice-enhancement-fac
           tor 1.0)))
      112  
      113 +(defn mmsn-sigma0                                             
      114 +  "Normalization Σ₀ (kg/m²) of a minimum-mass-solar-nebula gas
          + profile                                                      
      115 +   Σ(r) = Σ₀·(r/AU)^(−3/2) that carries total mass `disk-m` be
          +tween `r-in` and                                              
      116 +   `r-out`. From ∫Σ·2πr dr = disk-m:                          
      117 +                                                              
      118 +       Σ₀ = disk-m / (4π · AU^{3/2} · (√r_out − √r_in))       
      119 +                                                              
      120 +   Using a physical radial profile (rather than equal mass per
          + annulus) is what                                             
      121 +   makes the derived isolation mass sane: inner annuli are sma
          +ll in area, so                                                
      122 +   equal-mass binning inflates their surface density and, with
          + it, any mass                                                 
      123 +   derived from it. 0 for degenerate input."                  
      124 +  [disk-m r-in r-out]                                         
      125 +  (let [dm (double disk-m) ri (double r-in) ro (double r-out)]
      126 +    (if (and (pos? dm) (< 0.0 ri) (< ri ro))                  
      127 +      (/ dm (* 4.0 Math/PI (Math/pow law/au 1.5)              
      128 +               (- (Math/sqrt ro) (Math/sqrt ri))))            
      129 +      0.0)))                                                  
      130 +                                                              
      131 +(defn mmsn-sigma                                              
      132 +  "Gas surface density (kg/m²) at radius `r` for the MMSN prof
          +ile with                                                      
      133 +   normalization `s0` (see `mmsn-sigma0`): Σ(r) = s0·(r/AU)^(−
          +3/2)."                                                        
      134 +  [s0 r]                                                      
      135 +  (let [r (double r)]                                         
      136 +    (if (pos? r)                                              
      137 +      (* (double s0) (Math/pow (/ r law/au) -1.5))            
      138 +      0.0)))                                                  
      139 +                                                              
      140  (defn core-accretion-timescale
      141    "Time to build a ~10 M⊕ core at r (Pollack 1996 parameteriza
           tion): τ ∝
      142     1/Σ_solid, scaled by orbital period. Returns seconds."

● Now the core change — wire the MMSN profile and isolation mass into the seeder
  loop:

● Update(src/domain/planet_formation.clj)
  ⎿  Added 2 lines, removed 1 line
      244                                       a1 (Math/pow 10.0 (+ log-
           min (* (inc i) (/ (- log-max log-min) planet-seeding-annuli)))
           )
      245                                       mid (* 0.5 (+ a0 a1))]
      246                                   {:r-inner a0 :r-outer a1 :r m
           id})))
      247 -            ann-mass    (/ disk-m planet-seeding-annuli)]     
      247 +            ann-mass    (/ disk-m planet-seeding-annuli)      
      248 +            s0          (mmsn-sigma0 disk-m r-in r-out)]      
      249          (loop [anns annuli
      250                 spawns []
      251                 disk-m' disk-m

● Update(src/domain/planet_formation.clj)
  ⎿  Added 1 line, removed 1 line
      256              (let [ann       (first anns)
      257                    area      (* Math/PI (- (* (:r-outer ann) (:
           r-outer ann))
      258                                            (* (:r-inner ann) (:
           r-inner ann))))
      259 -                  sigma-gas (if (pos? area) (/ ann-mass area) 
          -0.0)                                                          
      259 +                  sigma-gas (mmsn-sigma s0 (:r ann))          
      260                    sigma-solid (solid-surface-density sigma-gas
            (:r ann) snow-line Z)
      261                    tau       (core-accretion-timescale (:r ann)
            sigma-solid M-star)
      262                    min-core-m (* 1.0e24 (Math/pow (max 0.1 sigm
           a-solid) 1.5))

● Now replace the fiat mass block with isolation-mass-capped growth + the
  runaway-gas giant channel:

● Update(src/domain/planet_formation.clj)
  ⎿  Added 19 lines, removed 4 lines
      266                    spaced?   (every? #(> (Math/abs (- (Math/log
           10 (:r ann)) (Math/log10 %))) 0.15)
      267                                      occupied)]
      268                (if (and enough? spaced?)
      269 -                (let [mass-kg (min (* 0.3 ann-mass)           
      270 -                                   (* max-seed-mass-solar law/
          -solar-mass)                                                   
      271 -                                   disk-m')                   
      272 -                      mass-kg (max mass-kg (* min-seed-mass-so
          -lar law/solar-mass))                                          
      269 +                (let [beyond?  (> (double (:r ann)) snow-line)
      270 +                      ;; A body grows to its local ISOLATION M
          +ASS — the solids in                                           
      271 +                      ;; its feeding zone — never more than th
          +e annulus holds.                                              
      272 +                      ;; This is the physical cap the old fiat
          + 0.3·disk/12 lacked.                                          
      273 +                      m-iso    (law/isolation-mass (:r ann) si
          +gma-solid M-star)                                             
      274 +                      core-m   (min m-iso (* sigma-solid area)
          +)                                                             
      275 +                      ;; Runaway gas accretion: only a core pa
          +st the critical mass                                          
      276 +                      ;; and beyond the ice line opens a gap a
          +nd draws down the                                             
      277 +                      ;; remaining disk gas into a giant envel
          +ope. This is the ONLY                                         
      278 +                      ;; route to a planet far heavier than it
          +s isolation mass.                                             
      279 +                      giant?   (and beyond? (>= core-m critica
          +l-core-mass-kg))                                              
      280 +                      gas-m    (if giant?                     
      281 +                                 (max 0.0                     
      282 +                                      (min (* runaway-gas-frac
          +tion disk-m')                                                 
      283 +                                           (- (* max-seed-mass
          +-solar law/solar-mass) core-m)))                              
      284 +                                 0.0)                         
      285 +                      mass-kg  (-> (+ core-m gas-m)           
      286 +                                   (max (* min-seed-mass-solar
          + law/solar-mass))                                             
      287 +                                   (min disk-m' (* max-seed-ma
          +ss-solar law/solar-mass)))                                    
      288                        ptype   (planet-type (:r ann) sigma-soli
           d snow-line (/ mass-kg law/solar-mass))
      289                        dens    (planet-material-density-by-type
            ptype)
      290                        rad     (sphere-radius mass-kg dens)

● Now let me run the formation tests to check the physics changes compile and
  pass:

  Ran 1 shell command (ctrl+o to expand)

● The min-core-m gate is the problem — it scales as sigma-solid^1.5, so on a
  dense disk it exceeds ann-mass and blocks all seeding. Now that isolation mass
  exists, that ad-hoc gate is obsolete. Let me restructure so the gate uses the
  real physics. Let me read the current loop:

  Read 1 file (ctrl+o to expand)

● Let me restructure — move the physics up and make the gate use isolation mass
  instead of the obsolete min-core-m:

● API Error: Server error mid-response. The response above may be 
  incomplete.

✻ Churned for 23m 11s

❯ continue                                                                      

● Update(src/domain/planet_formation.clj)
  ⎿  Added 10 lines, removed 9 lines
      259                    sigma-gas (mmsn-sigma s0 (:r ann))
      260                    sigma-solid (solid-surface-density sigma-gas
            (:r ann) snow-line Z)
      261                    tau       (core-accretion-timescale (:r ann)
            sigma-solid M-star)
      262 -                  min-core-m (* 1.0e24 (Math/pow (max 0.1 sigm
          -a-solid) 1.5))                                                
      262 +                  beyond?   (> (double (:r ann)) snow-line)   
      263 +                  ;; A body grows to its local ISOLATION MASS 
          +— the solids in its                                           
      264 +                  ;; feeding zone — never more than the annulu
          +s actually holds.                                             
      265 +                  ;; This is the physical cap the old fiat 0.3
          +·disk/12 lacked.                                              
      266 +                  m-iso     (law/isolation-mass (:r ann) sigma
          +-solid M-star)                                                
      267 +                  core-m    (min m-iso (* sigma-solid area))  
      268 +                  ;; Seed where solids exist, a core can form 
          +in the disk's                                                 
      269 +                  ;; lifetime, and the isolation mass clears a
          + meaningful body.                                             
      270                    enough?   (and (pos? sigma-solid)
      271                                   (< tau disk-age)
      265 -                                 (>= ann-mass min-core-m))    
      272 +                                 (>= core-m (* min-seed-mass-s
          +olar law/solar-mass)))                                        
      273                    spaced?   (every? #(> (Math/abs (- (Math/log
           10 (:r ann)) (Math/log10 %))) 0.15)
      274                                      occupied)]
      275                (if (and enough? spaced?)
      269 -                (let [beyond?  (> (double (:r ann)) snow-line)
      270 -                      ;; A body grows to its local ISOLATION M
          -ASS — the solids in                                           
      271 -                      ;; its feeding zone — never more than th
          -e annulus holds.                                              
      272 -                      ;; This is the physical cap the old fiat
          - 0.3·disk/12 lacked.                                          
      273 -                      m-iso    (law/isolation-mass (:r ann) si
          -gma-solid M-star)                                             
      274 -                      core-m   (min m-iso (* sigma-solid area)
          -)                                                             
      275 -                      ;; Runaway gas accretion: only a core pa
          -st the critical mass                                          
      276 +                (let [;; Runaway gas accretion: only a core pa
          +st the critical mass                                          
      277                        ;; and beyond the ice line opens a gap a
           nd draws down the
      278                        ;; remaining disk gas into a giant envel
           ope. This is the ONLY
      279                        ;; route to a planet far heavier than it
           s isolation mass.
