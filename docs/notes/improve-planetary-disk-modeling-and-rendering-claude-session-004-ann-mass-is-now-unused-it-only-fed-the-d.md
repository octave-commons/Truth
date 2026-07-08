● ann-mass is now unused (it only fed the deleted gate). Let me remove it and
  re-run:

● Update(src/domain/planet_formation.clj)
  ⎿  Removed 1 line
      244                                       a1 (Math/pow 10.0 (+ log-
           min (* (inc i) (/ (- log-max log-min) planet-seeding-annuli)))
           )
      245                                       mid (* 0.5 (+ a0 a1))]
      246                                   {:r-inner a0 :r-outer a1 :r m
           id})))
      247 -            ann-mass    (/ disk-m planet-seeding-annuli)      
      247              s0          (mmsn-sigma0 disk-m r-in r-out)]
      248          (loop [anns annuli
      249                 spawns []

  Ran 1 shell command (ctrl+o to expand)

● Planets now seed and the mass is bounded — but inner terrestrials come out ~16
  M⊕, because the seeder squeezes the entire disk mass into 0.1–5 AU, making it
  ~10× denser than MMSN. Real disks extend to ~30 AU. The fix is to normalize
  the profile (and seed) over the disk's physical extent. Let me check how disk
  radius is derived:

  Searched for 1 pattern (ctrl+o to expand)

● The problem is r-out = 5 AU — squeezing all disk mass inside 5 AU makes it
  ~10× MMSN. Real disks extend to ~30 AU. Extending it both fixes the density
  and lets ice-giants seed at Neptune-like distances. Let me make it a named
  constant and widen it:

● Update(src/domain/planet_formation.clj)
  ⎿  Added 3 lines
      26  (def ^:const proto-solar-metal-frac 0.015)
      27  (def ^:const ice-enhancement-factor 3.5)
      28  (def ^:const min-planet-orbit-radius-au 0.1)
      29 +(def ^:const planet-seeding-outer-au 30.0) ;; seed + normalize 
         +the disk profile out                                           
      30 +                                           ;; to a realistic Ne
         +ptune-like extent, so                                          
      31 +                                           ;; mass isn't over-c
         +oncentrated inside 5 AU                                        
      32  (def ^:const planet-seeding-annuli 12)
      33  (def ^:const min-seed-mass-solar 1.0e-8) ;; ≈ 0.003 M⊕ (~0.2 Mo
          on) — a real minimum
      34                                           ;; body; isolation mas
          s governs above it.

● Update(src/domain/planet_formation.clj)
  ⎿  Added 1 line, removed 1 line
      239                                                 lcomp/solar-com
           position))
      240              r-in        (max (* min-planet-orbit-radius-au law
           /au)
      241                               (* 3.0 (double (or (ecs/get-compo
           nent world star c/radius) 1.0e9))))
      242 -            r-out       (* 5.0 law/au)                        
      242 +            r-out       (* planet-seeding-outer-au law/au)    
      243              log-min     (Math/log10 r-in)
      244              log-max     (Math/log10 (max r-in r-out))
      245              annuli      (vec (for [i (range planet-seeding-ann
           uli)]

  Ran 1 shell command (ctrl+o to expand)

● All 28 formation tests green. Now let me check I haven't broken adjacent tests
  — disk evolution, integration, and the architecture guards:

  Ran 1 shell command (ctrl+o to expand)

● Phase 2 physics is in and green across formation, disk-evolution, integration,
  and architecture guards. Let me lock it in with the full suite and static
  analysis before moving on.

  Ran 1 shell command (ctrl+o to expand)

● While the full suite runs, let me run static analysis and update task status.

  Ran 1 shell command (ctrl+o to expand)

● Analysis passes with no blocking findings; only formatting needs a touch-up.
  Let me format just my changed files rather than the whole tree (there's
  pre-existing unrelated dirt). Let me find the cljfmt invocation:

  Searched for 1 pattern, ran 1 shell command (ctrl+o to expand)

● Let me check on the full test suite:

  Read 1 file, ran 1 shell command (ctrl+o to expand)

● Full suite is still running (I'll be notified when it lands). Let me use the
  time to read the exact render code I'll need for the cloud-disk change, so I'm
  ready to implement the moment tests confirm green.

  Read 1 file (ctrl+o to expand)

● render-samples reads hydro/gas-samples — all gas parcels, disc-tagged
  included. So the disc material likely already renders as volumetric cloud; the
  600 sprites are a redundant fabrication on top. The decisive question for
  retiring them is whether the live dev window actually runs the volume path.
  Let me check that and how gas-samples exposes disc membership.

  Searched for 1 pattern (ctrl+o to expand)

● Background command "Run full test suite" completed (exit code 0)
● Full suite: exit 0, all green. And the render findings are decisive and better
  than I feared:

  1. The live dev window already runs the volumetric fog path (:volumetric? true
  by default → frame-volume). So the disc-tagged gas parcels — which orbit —
  already render as volumetric clouds in the live window. Real rotation and the
  cloud look are already there, underneath the sprites.
  2. The 600 point-sprites are a pure redundant fabrication on top — the
  disliked, offset-prone, laggy layer.

  So retiring the sprites is exactly right and low-risk: the fog keeps showing
  the disk, as orbiting clouds. Let me read the sprite-emission block and remove
  it.

  Read 1 file (ctrl+o to expand)

● Now I'll retire the sprites in both the :star and :protostar cases — keep the
  body and field lines, drop the 600-particle disk (the fog already shows the
  orbiting disc parcels):

● Update(src/infra/render.clj)
  ⎿  Added 7 lines, removed 23 lines
      1499                                :brightness    brightness
      1500                                :surface       (:surface app)
      1501                                :accent        (:accent app)
      1502 -                              :seed          (:seed app)}    
      1503 -                  disk-m     (double (or (ecs/get-component w
           -orld eid c/disk-mass) 0.0))                                  
      1504 -                  mass-kg    (double (or (ecs/get-component w
           -orld eid c/mass) 1.0e30))                                    
      1505 -                  disk-L     (or (ecs/get-component world eid
           - c/disk-angular-mom) [0.0 0.0 0.0])]                         
      1502 +                              :seed          (:seed app)}]   
      1503                (concat
      1504                 [body]
      1508 -                 ;; Stellar corona / disk glow is also part o
           -f the froxel volume                                          
      1509 -                 ;; when present; we only draw the body and f
           -ield lines here.                                             
      1510 -               (field-line center core-r (ecs/get-component w
           -orld eid c/b-field))                                         
      1511 -               (when (pos? disk-m)                           
      1512 -                 (let [r-disk (stellar/disk-radius           
      1513 -                               (/ (sp/len disk-L) (max 1.0 di
           -sk-m))                                                       
      1514 -                               mass-kg)                      
      1515 -                       r-out  (min 12.0 (max (* 3.0 core-r)  
      1516 -                                             (/ (double r-dis
           -k) (:scale ctx))))                                           
      1517 -                       r-in   (* 1.3 core-r)                 
      1518 -                       d-col  (mapv (fn [c] (min 1.0 (* (+ 0.
           -4 (* 0.6 c)) 0.90))) s-col)]                                 
      1519 -                   (disk-particles {:center    center        
      1520 -                                    :r-inner   r-in          
      1521 -                                    :r-outer   r-out         
      1522 -                                    :normal    disk-L        
      1523 -                                    :color     d-col         
      1524 -                                    :disk-mass disk-m        
      1525 -                                    :cnt      600            
      1526 -                                    :seed      eid})))))     
      1505 +                 ;; The protoplanetary disk is NOT drawn as s
           +prites here: the                                             
      1506 +                 ;; disc-tagged gas parcels are baked into th
           +e ray-marched froxel                                         
      1507 +                 ;; volume like the rest of the nebula, so th
           +e disk renders as a                                          
      1508 +                 ;; rotating coloured cloud (its real orbitin
           +g matter) in one                                             
      1509 +                 ;; fullscreen pass — no fabricated torus, no
           + per-frame VBO churn.                                        
      1510 +               (field-line center core-r (ecs/get-component w
           +orld eid c/b-field))))                                       
      1511  
      1512              :protostar
      1513               ;; A contracting core: render radius follows the
             physical radius

● Update(src/infra/render.clj)
  ⎿  Added 4 lines, removed 21 lines
      1516               ;; volume; only the body, field line, and disk a
            re drawn here.
      1517              (let [render-r   (units/phys->body-render-radius 
            ctx r-phys)
      1518                    brightness (body-brightness world eid state
            )
      1519 -                  app        (body-appearance :protostar nil 
           -temp false eid)                                              
      1520 -                  disk-m     (double (or (ecs/get-component w
           -orld eid c/disk-mass) 0.0))                                  
      1521 -                  mass-kg    (double (or (ecs/get-component w
           -orld eid c/mass) 1.0e30))                                    
      1522 -                  disk-L     (or (ecs/get-component world eid
           - c/disk-angular-mom) [0.0 0.0 0.0])]                         
      1519 +                  app        (body-appearance :protostar nil 
           +temp false eid)]                                             
      1520                (concat
      1521                 [{:entity        eid
      1522                   :position      center
     ...
      1531                   :surface       (:surface app)
      1532                   :accent        (:accent app)
      1533                   :seed          (:seed app)}]
      1537 -               (field-line center render-r (ecs/get-component
           - world eid c/b-field))                                       
      1538 -               (when (pos? disk-m)                           
      1539 -                 (let [r-disk (stellar/disk-radius           
      1540 -                               (/ (sp/len disk-L) (max 1.0 di
           -sk-m))                                                       
      1541 -                               mass-kg)                      
      1542 -                       r-out  (min 12.0 (max (* 3.0 render-r)
      1543 -                                             (/ (double r-dis
           -k) (:scale ctx))))                                           
      1544 -                       r-in   (* 1.3 render-r)               
      1545 -                       d-col  (mapv (fn [c] (min 1.0 (* (+ 0.
           -35 (* 0.65 c)) 0.85))) color)]                               
      1546 -                   (disk-particles {:center    center        
      1547 -                                    :r-inner   r-in          
      1548 -                                    :r-outer   r-out         
      1549 -                                    :normal    disk-L        
      1550 -                                    :color     d-col         
      1551 -                                    :disk-mass disk-m        
      1552 -                                    :cnt      600            
      1553 -                                    :seed      eid})))))     
      1534 +                 ;; Disk drawn as froxel cloud from the disc-
           +tagged gas parcels,                                          
      1535 +                 ;; not sprites — see the :star branch.      
      1536 +               (field-line center render-r (ecs/get-component
           + world eid c/b-field))))                                     
      1537  
      1538               ;; substellar / planet → shaded body sized by ph
            ysical radius,
      1539               ;; coloured by composition crossfading to therma
            l glow, surfaced by

● The sprites are retired. Now
  disk-particles/disk-particles*/tilt-disk-to-normal/disk-cache are likely dead
  code (clj-kondo flags unused privates). Let me check for any remaining
  references before removing them:

  Ran 1 shell command (ctrl+o to expand)

● All five (disk-cache, rotate-axis-angle, disk-particles*, tilt-disk-to-normal,
  disk-particles) form a dead cluster referencing only each other. Let me read
  the exact span and remove it:

  Read 1 file (ctrl+o to expand)

● I'll remove the entire dead sprite cluster (lines 1116–1230):

● Update(src/infra/render.clj)
  ⎿  Added 6 lines, removed 115 lines
      1113                            az [0.0 60.0 120.0 180.0 240.0 300.
            0]] [shell az])))))
      1114            sources))))
      1115  
      1116 -;; --- Protoplanetary disk (particles in the disk plane) ----
           --------------------                                          
      1117 -;; A flat torus of particles around a star or protostar that 
           -has accreted a disk.                                         
      1118 -;; Oriented by the disk angular-momentum vector; sized by dis
           -k-radius; coloured                                           
      1119 -;; by the host star's spectral type, warmed and dimmed. Parti
           -cles are denser                                              
      1120 -;; near the inner edge (where the surface density is highest)
           - and sparser at the                                          
      1121 -;; outer rim, so the disk reads as a bright core fading into 
           -a wispy edge.                                                
      1116 +;; --- Protoplanetary disk ----------------------------------
           +--------------------                                         
      1117 +;; The disk is NOT a sprite layer. Its matter is the disc-tag
           +ged gas parcels,                                             
      1118 +;; which orbit the star and are baked into the ray-marched fr
           +oxel volume like the                                         
      1119 +;; rest of the nebula — so it renders as a rotating coloured 
           +cloud (real orbiting                                         
      1120 +;; matter) rather than a fabricated static torus. See `phase0
           +-bodies-from-world*`                                         
      1121 +;; (the :star / :protostar branches) and `render-samples`.   
      1122  
      1123 -(def ^:private disk-cache                                    
      1124 -  "Per-entity disk particle cache. Keyed by [eid :disk count]
           -; validated against                                          
      1125 -   the inputs that drive the particle cloud so it invalidates
           - when the disk                                               
      1126 -   accretes, shrinks, or changes orientation."               
      1127 -  (atom {}))                                                 
      1128 -                                                             
      1129 -(defn- rotate-axis-angle                                     
      1130 -  "Rodrigues' rotation: rotate 3-vector `v` by `angle` (radia
           -ns) around unit                                              
      1131 -   axis `k`. Pure, no side effects."                         
      1132 -  [v k angle]                                                
      1133 -  (let [[vx vy vz] v                                         
      1134 -        [kx ky kz] k                                         
      1135 -        c  (Math/cos angle)                                  
      1136 -        s  (Math/sin angle)                                  
      1137 -        ;; k × v                                             
      1138 -        cx (- (* ky vz) (* kz vy))                           
      1139 -        cy (- (* kz vx) (* kx vz))                           
      1140 -        cz (- (* kx vy) (* ky vx))                           
      1141 -        ;; k · v                                             
      1142 -        kd (+ (* kx vx) (* ky vy) (* kz vz))                 
      1143 -        ;; v cosθ + (k×v) sinθ + k(k·v)(1-cosθ)              
      1144 -        f  (- 1.0 c)]                                        
      1145 -    [(+ (* vx c) (* cx s) (* kx kd f))                       
      1146 -     (+ (* vy c) (* cy s) (* ky kd f))                       
      1147 -     (+ (* vz c) (* cz s) (* kz kd f))]))                    
      1148 -                                                             
      1149 -(defn- disk-particles*                                       
      1150 -  "Actual disk particle generation; split for caching."      
      1151 -  [{:keys [center r-inner r-outer color thickness disk-mass]}
           - cnt seed]                                                   
      1152 -  (let [[cx cy cz] center                                    
      1153 -        rng  (java.util.Random. (long (or seed 1)))          
      1154 -        n-rings  (int (max 3 (Math/sqrt (double cnt))))      
      1155 -        _n-per   (int (Math/ceil (/ (double cnt) (double n-ri
           -ngs))))                                                      
      1156 -        r-span   (max 1.0e-6 (- (double r-outer) (double r-in
           -ner)))                                                       
      1157 -        thick    (double (or thickness 0.05))                
      1158 -        _disk-m  (double (or disk-mass 1.0e25))]             
      1159 -    (mapv                                                    
      1160 -     (fn [_]                                                 
      1161 -       (let [;; Radial position: bias toward inner edge (surf
           -ace density ∝ 1/r)                                           
      1162 -              ;; so the bright core reads denser than the dif
           -fuse outer rim.                                              
      1163 -             r-frac  (Math/pow (.nextDouble rng) 0.6)        
      1164 -             r       (+ (double r-inner) (* r-frac r-span))  
      1165 -             theta   (* 2.0 Math/PI (.nextDouble rng))       
      1166 -              ;; Vertical spread: thin Gaussian flaring outwa
           -rd                                                           
      1167 -             z-spread (* thick r-frac (.nextGaussian rng))   
      1168 -             px (+ cx (* r (Math/cos theta)))                
      1169 -             py (+ cy (* r (Math/sin theta)))                
      1170 -             pz (+ cz z-spread)                              
      1171 -              ;; Surface density drives opacity: brighter at 
           -inner edge                                                   
      1172 -             sigma   (- 1.0 (* 0.6 r-frac))                  
      1173 -               ;; Particle size: larger dusty grains so the d
           -isk is visible even                                          
      1174 -               ;; at moderate zoom; sigma keeps inner edge de
           -nser.                                                        
      1175 -             sz      (+ 4.5 (* 3.0 sigma) (* 1.2 (.nextDouble
           - rng)))                                                      
      1176 -               ;; Colour: warm stellar tint mixed with browni
           -sh dust; inner disk                                          
      1177 -               ;; brighter, outer rim cooler and more attenua
           -ted.                                                         
      1178 -             dust    [0.78 0.62 0.42]                        
      1179 -             col     (mapv (fn [c d] (max 0.0 (min 1.0 (+ (* 
           -c (+ 0.45 (* 0.55 sigma))) (* d (- 0.85 (* 0.35 sigma))))))) 
           -color dust)]                                                 
      1180 -         {:position [px py pz]                               
      1181 -          :color    col                                      
      1182 -          :size     sz                                       
      1183 -          :density  (float (* 0.55 sigma))                   
      1184 -          :render-mode :particle}))                          
      1185 -     (range cnt))))                                          
      1186 -                                                             
      1187 -(defn- tilt-disk-to-normal                                   
      1188 -  "Rotate disk particles from the generated xy-plane onto the
           - disk's actual                                               
      1189 -   orientation `normal`. Each particle is rotated about its o
           -ffset from `center`                                          
      1190 -   (not its absolute world position), so a tilted disk stays 
           -centred on its host.                                         
      1191 -   Rotating the absolute position instead sends `p → R·(cente
           -r + offset)`, which                                          
      1192 -   slides the whole disk off the star by `R·center − center` 
           -— the offset bug."                                           
      1193 -  [particles center normal]                                  
      1194 -  (let [normal-u (unit-vec normal)                           
      1195 -        ez       [0.0 0.0 1.0]                               
      1196 -        dot      (sp/dot normal-u ez)]                       
      1197 -    (if (> (Math/abs dot) 0.999)                             
      1198 -      particles ;; already (anti-)aligned with z, no tilt nee
           -ded                                                          
      1199 -      (let [axis  (unit-vec (sp/cross ez normal-u))          
      1200 -            angle (Math/acos (max -1.0 (min 1.0 dot)))]      
      1201 -        (mapv (fn [p]                                        
      1202 -                (assoc p :position                           
      1203 -                       (vec (sp/v+ center                    
      1204 -                                   (rotate-axis-angle        
      1205 -                                    (sp/v- (:position p) cent
           -er) axis angle)))))                                          
      1206 -              particles)))))                                 
      1207 -                                                             
      1208 -(defn- disk-particles                                        
      1209 -  "Protoplanetary disk as a flat torus of particles, determin
           -istic and cached.                                            
      1210 -   `center` in render units; `r-inner`/`r-outer` in render un
           -its; `normal` is                                             
      1211 -   the disk angular-momentum vector (particles are generated 
           -in the xy-plane                                              
      1212 -   then rotated to match); `color` is the host's spectral col
           -our, warmed.                                                 
      1213 -   The cache key includes the entity id and particle count; v
           -alidation checks                                             
      1214 -   the physics inputs so the cloud refreshes when the disk ev
           -olves."                                                      
      1215 -  [{:keys [center r-inner r-outer normal color disk-mass cnt 
           -seed]}]                                                      
      1216 -  (let [eid    (long (or seed 1))                            
      1217 -        ckey   [eid :disk cnt]                               
      1218 -        params {:center center :r-inner r-inner :r-outer r-ou
           -ter                                                          
      1219 -                :color color :disk-mass disk-mass}           
      1220 -        build  (fn []                                        
      1221 -                 (let [thick   (max 0.02 (* 0.08 (- (double r
           --outer) (double r-inner))))                                  
      1222 -                       raw     (disk-particles* (assoc params
           - :thickness thick) cnt seed)                                 
      1223 -                       rotated (tilt-disk-to-normal raw cente
           -r normal)]                                                   
      1224 -                   (swap! disk-cache assoc ckey (assoc params
           - :particles rotated))                                        
      1225 -                   rotated))]                                
      1226 -    (if-let [cached (get @disk-cache ckey)]                  
      1227 -      (if (= (dissoc cached :particles) params)              
      1228 -        (:particles cached)                                  
      1229 -        (build))                                             
      1230 -      (build))))                                             
      1231 -                                                             
      1123  ;; --- Player spark + focus reticle (the interactive overlay)
             ------------------
      1124  
      1125  (defn- ring-segments

