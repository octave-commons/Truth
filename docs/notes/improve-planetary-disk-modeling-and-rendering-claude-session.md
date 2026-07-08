 ▐▛███▜▌   Claude Code v2.1.202
▝▜█████▛▘  Opus 4.8 · Claude Team
  ▘▘ ▝▝    ~/spaces/Truth

 ▎ Extended: Fable 5 is included in your weekly limit
 ▎ Through July 12, you can use up to 50% of your weekly usage limit on Fable 5. If you hit your limit, you can continue on Fable 5 with usage credits. Fable 5 draws down usage faster than Opus 4.8. Run /model and select Fable to use it. Learn more

❯ /clear                                                                        

❯ My head is hurting... we have all of these notes on planetary growth, I think 
  we've made a lot of progress.                                                 
                                                                                
  ## Disk rendering                                                             
  - disks render with an offset?                                                
  - I don't like the particles                                                  
  - It's maybe the cause of some lag? though we've done a lot of work.          
  - we are still forming huge planets, though we're getting better.             
  (immediately a 400 earth mass terrestrial planet forms in the dev sim)        
  - no planetecimals/astroids ever form (they should form first, and actual     
  planets form from collisions of these bodies)                                 
  - Even if astroids did form, they would probably never colldide.              
                                                                                
  ## Task                                                                       
                                                                                
  Grounding yourself in our existing notes, research, designs, and specs.       
  Create a plan to improve our plantary disk model, improve performance         
  and render the disks more cleanly.                                            
                                                                                
                                                                                
  The disk should look like clouds still, in large part. Like clouds, like the  
  nebular parcels already do, except a color, at least for there very first     
  phase of matter before they've condensed.                                     
                                                                                
  What keeps getting me is that I want this to be right but I know there        
  isn't a right cause we don't know.                                            
                                                                                
  What we have now though, is not satsifying. No astroid belt can form, no      
  moons, no earth sized planets.                                                
                                                                                
  At this phase we are trying to resolve closer and closer to life.             
  We need to be in a space, such that when life forms, the game can focus on    
  that planet, and model the star system outside of it statistically.           
                                                                                
  Like there were N astroids that survived the chaotic phase of planetary       
  growth in X belts.                                                            
  Or there are commets, which I don't even know how we're modeling that one.    
                                                                                
  Physically, they don't come from the planetary disk in the same way.          
  They are balls of mostly water, I don't think we have research on this one    
  yet.                                                                          
                                                                                
  We have an opencode server, you can dispatch an research actor agent to fill  
  in that gap.                                                                  
                                                                                
  I'm squirrelin out here, when stuff gets this big... well I like to keep      
  myself in that zone of discomfort. Feels like I work with you, you do         
  research, you build stuff I don't understand. I eventually figure it out as I 
  look at the sim.                                                              
                                                                                
  So it's important that it renders in a way that communicates what is going on 
  clearly.                                                                      
                                                                                
  And, if the disk rendering is accurate to what is actually going on in        
  the sim. It needs a lot of work. It has no rotation, it just kinda grows and  
  shrinks a lot, it doesn't look like it's following physics. It                
  doesn't *feel* physically correct, at all.                                    
                                                                                

● This is a meaty, exciting problem. Before I write a plan I want to ground
  myself hard in three things: (1) what the current planetary-formation code
  actually does, (2) how the disk actually renders today (the offset, the
  particles, the lack of rotation), and (3) the real physics of
  planetesimal/asteroid/comet formation — since that's the science the fix has
  to respect.

  Let me fan out and gather all three in parallel.

● 3 agents finished (ctrl+o to expand)
   ├ Explore (Map planetary formation code) · 22 tool uses · 101.0k tokens
   │ ⎿  Done
   ├ Explore (Map disk rendering path) · 23 tool uses · 59.8k tokens
   │ ⎿  Done
   └ Agent (Research planetesimal/comet formation)

● The two code maps are back and they're clarifying. The physics research
  (comets + planetesimal formation) is still running in the background. While it
  finishes, here's what I've already confirmed — and it lines up almost exactly
  with your gut feeling that "this isn't following physics."

  What's actually going on

  The 400-Earth-mass planet is a mass-assignment bug, not physics. There are two
  separate body-creation systems and they disagree:

  - condensation-seeder-system (the recent "seed-and-grow" work) does the right
  thing — it spawns real ~10 km, 10¹⁶ kg planetesimals and debits that mass from
  the parent gas parcel. This is good.
  - But planet-formation/planet-seeds — a totally separate "sub-grid" seeder
  that fires once when a star's disk matures — just grabs 0.3 × (disk_mass / 12)
  per planet, capped at 13 solar masses. It computes a proper
  solid-surface-density (sigma-solid, gated by metallicity) but then ignores it
  and drops a mass fraction of the whole disk. That's your instant 400 M⊕
  "terrestrial." There is no isolation-mass cap — the one piece of physics that
  stops runaway growth.

  Planetesimals can never collide because the collision test is literal sphere
  overlap (dist ≤ r₁ + r₂) with no swept/continuous detection, over 31,000-year
  timesteps, at AU separations. Two 10 km rocks will never be found overlapping
  in a single step. So the seed-and-grow bodies are stillborn — they can't grow
  into planets or asteroid belts.

  The disk has no rotation because it isn't matter. The "disk" is a scalar — one
  disk-mass number plus an angular-momentum vector, stored on the star. It
  literally can only grow and shrink. There are no orbiting parcels to rotate.
  That's exactly why it feels physically wrong.

  The rendering

  The offset bug is confirmed and I know the exact cause. The disk is 600
  fabricated point-sprites. When the disk is tilted, the tilt rotation is
  applied to each particle's absolute world position about the origin, giving
  R·center + R·offset — so the whole disk slides off the star by R·center − 
  center. The fix is one-line-conceptual: rotate only the local offset, then
  re-add center.

  The particles are their own render invention — not the sim, not the nebula
  fog. And the froxel volumetric-fog path (what makes the nebula look like
  clouds) is per-sample color-agnostic: it would happily render disk material as
  tinted clouds if we feed it colored samples. That's the clean path to what
  you're describing.

  Still pending

  The physics research agent is filling the real gap — planetesimal formation
  (streaming instability), isolation mass numbers, asteroid-belt survival, and
  comets (where they actually come from, since they don't form from the local
  disk the way planets do). I want those numbers in hand before I write the
  plan, because they determine the specific thresholds and stages.

  I'll deliver the full plan — formation model + performance + cloud rendering —
  as soon as that lands. Sit in the discomfort a moment longer; it's coming.

✻ Sautéed for 6m 38s

● Agent "Research planetesimal/comet formation" finished · 5m 30s

● The research agent stopped partway — it finished dust→planetesimals (and I
  need to confirm comets) but still owes me isolation mass, the ice line, and
  asteroid belts. Those are exactly the numbers that set the thresholds in the
  plan. Let me resume it and have it deliver the complete brief.
  ⎿  Agent "a651062e1f66aa51f" was stopped (completed); resumed it in the 
     background with your message. You'll be notified when it finishes. 
     Output: /tmp/claude-1000/-home-err-spaces-Truth/14bfe6b9-913c-4ea7-b37
     0-70c7afec6226/tasks/a651062e1f66aa51f.output

● Research agent resumed — it's assembling the complete brief now. I'll write
  the full plan the moment it lands. Nothing more useful for me to do until
  then; I won't spin on it.

✻ Brewed for 28s

● Agent "Research planetesimal/comet formation" finished · 6m 38s

● The research agent has fallen into a confused orchestration loop — it spawned
  its own sub-agents and keeps reporting on their status instead of emitting the
  actual findings. It's done real web research (two long runs), so the content
  exists in its transcript; I just need to salvage it rather than keep
  ping-ponging. Let me extract the substance directly.

  Ran 1 shell command (ctrl+o to expand)

● The transcript confirms the sub-agents ran but their findings weren't
  consolidated. Let me pull the actual answer content — the sub-agent results
  with the real numbers.

  Ran 1 shell command (ctrl+o to expand)

● Agent "Research growth and isolation mass" finished · 38s

● Agent "Research asteroid belt formation and survival" finished · 35s

● Agent "Research terrestrial vs giant cores and ice line" finished · 39s

● All the physics is in now — the sub-agents delivered their findings directly.
  I have isolation-mass numbers, growth modes, the ice line, asteroid-belt
  survival, and the comet reservoirs. Combined with the two code maps, I can see
  the whole shape of this. Let me give you the plan.

  ---
  Plan: making the disk matter

  The one root cause behind all four symptoms

  Everything you're feeling traces to a single design decision: the disk isn't 
  matter, it's a scalar. Right now the "disk" is one number (disk-mass) plus an
  angular-momentum vector, stored on the star. That's the disease, and your four
  symptoms are all downstream of it:

  - "No rotation, just grows and shrinks" → correct, because there's nothing
  orbiting. A scalar can only get bigger or smaller.
  - "Renders with an offset / I don't like the particles" → the renderer
  fabricates 600 fake point-sprites in a torus from that scalar, and rotates
  them about the world origin (the offset bug).
  - "400-Earth-mass planet forms instantly" → a separate seeder (planet-seeds)
  just hands each planet 0.3 × disk_mass / 12, ignoring the physics. No
  isolation-mass cap.
  - "No planetesimals/asteroids, and they'd never collide anyway" → the good
  seed-and-grow system does make real 10 km planetesimals, but growth is gated
  on literal sphere-overlap over 31,000-year steps at AU distances. It never
  happens. They're stillborn.

  So the plan is not four fixes. It's one reframe with three phases.

  The reframe

  You already have orbiting matter: the disc-tagged gas parcels. They spin about
  z, they're bound, disc-identification-system already labels them :disc. The
  scalar disk is a second, redundant abstraction layered on top of them — and
  it's the fake one.

  ▎ The disc-tagged parcels ARE the disk. Render them as colored volumetric 
  ▎ cloud (the froxel fog already tints per-sample). Grow solids by accreting 
  ▎ from these parcels within a feeding zone, capped at isolation mass. Retire 
  ▎ the scalar-fiat planet seeder.

  This is fully consistent with your "single ECS substrate" rule — it's a
  content layer over parcels that already exist, not a new engine. And it
  happens to match a spec you already deferred:
  docs/specs/radial-disk-structure-realspec.md explicitly wanted to "replace the
  single-annulus scalar disk regime."

  Once you do this, rotation is free and physically real (the parcels orbit),
  the cloud look is free (fog path), and you have an actual substrate of solids
  to grow planets from.

  ---
  Phase 1 — Rendering (immediate payoff, low risk)

  You want to see it, so this goes first.

  1. Fix the offset bug. Confirmed cause: tilt rotation is applied to each
  particle's absolute position about the origin (R·center + R·offset), sliding
  the disk off the star by R·center − center. Fix: rotate only the local offset,
  then re-add center. One conceptual change, two call sites.
  2. Kill the 600 point-sprites; render disc-tagged parcels as colored cloud.
  Feed disc-tagged parcels into the froxel fog as tinted samples (:col = a
  dust/metallicity color, :dens ∝ local surface density). This is exactly your
  "clouds like the nebula parcels, except a color." It rides the persistent 3D
  froxel texture that's already cheap and updated in place — which also removes 
  the per-frame VBO pack/alloc/upload/delete churn that's a real slice of your
  lag.
  3. Rotation now shows for free because you're rendering the actual orbiting
  parcels, not a static fabricated torus.

  At the end of Phase 1 you have a rotating, colored, physically-placed cloud
  disk and less lag — before touching formation physics.

  Phase 2 — Formation physics (the heart of it)

  The research gives me the exact levers.

  1. Retire the fiat planet-seeds mass assignment. Delete 0.3 × disk_mass / 12.
  Planets are no longer placed; they grow.
  2. Planetesimals first (already exists). Keep condensation-seeder-system
  making ~10¹⁶ kg / ~10 km planetesimals from the densest disc parcels. This is
  the right first stage — streaming instability births planetesimals at ~10–100
  km, which is what this models.
  3. Sub-grid accretional growth, NOT literal collision. This is the key fix. A
  planetesimal grows by sweeping up the solids in its feeding zone (~8–10 Hill
  radii wide) at the oligarchic rate (dM/dt ∝ M^(2/3)), drawing from the local
  solid surface density (Σ_gas · Z, ×3–4 beyond the ice line). No two 10 km
  rocks ever need to be caught overlapping.
  4. Isolation mass caps runaway growth — the physics you're missing. A body
  stops growing when it clears its feeding zone:
    - ~0.1 M⊕ at 1 AU (sub-Mars — this alone kills the 400 M⊕ terrestrial),
    - ~several–10 M⊕ at 5 AU (beyond the ice line, where solids jump).
    - M_iso ∝ Σ_solid^(3/2) · a³ · M_star^(−1/2).
  5. A 400 M⊕ planet becomes legitimate but rare. Only a core that reaches
  pebble isolation mass (~20 M⊕, beyond the ice line) triggers runaway gas 
  accretion and pulls gas from the whole disk to become a giant — and only if
  disk gas remains (lifetime ~1–10 Myr). Inner rocky planets stay ~0.1–1 M⊕ and
  finish via a ~100 Myr giant-impact phase. That's the real dichotomy.
  6. Asteroid belts as survivors, not failures. A belt is an annulus where a
  nearby giant's resonances stir eccentricities so collision velocities (~5
  km/s) exceed the accretion threshold — bodies shatter instead of merging, so
  the annulus never reaches isolation mass. Model it statistically: the belt
  retains N planetesimals, ~99.9% mass depleted (real belt ≈ 4×10⁻⁴ M⊕), with
  Kirkwood-gap structure from Jupiter's 3:1, 5:2, 2:1 resonances.

  Phase 3 — Comets + the "zoom to life" architecture

  1. Comets are a separate icy reservoir (your research gap, now filled). They
  do not form from the local planetary disk. They're icy planetesimals born
  beyond the ice line, then scattered by the giants into two reservoirs:
    - Kuiper belt / scattered disk (~30–50 AU) → short-period, Jupiter-family
  comets;
    - Oort cloud (~2,000–100,000 AU) → long-period comets.
    - Composition: "dirty snowballs" — water ice + CO/CO₂/NH₃/CH₄ + dust; nuclei
  ~1–50 km.
    - Model as a statistical reservoir (count + composition + orbital-family),
  not resolved bodies.
  2. The statistical census — this is how "focus on the life planet" works.
  Introduce a system-level census of populations: surface-density profile Σ(r),
  size-frequency power laws for each belt, N surviving planetesimals,
  comet-reservoir counts. Bodies that matter — the life-bearing planet, the
  major planets — stay resolved ECS entities; everything else collapses into
  these statistical components. That's your "N asteroids survived in X belts"
  made real, and it's what lets the game zoom to one planet while the rest of
  the system stays coherent and cheap.

  ---
  Where this leaves the genuine uncertainty

  You said it yourself — there's no "right," because we don't know. What I can
  promise is physically plausible and internally consistent: planetesimals
  first, isolation mass capping growth, giants only beyond the ice line with
  gas, belts as stirred survivors, comets as a distinct icy halo. That's a
  defensible universe, and it'll feel like one because the disk actually orbits.

  I want to check two things with you before I start building and writing this
  up as a design doc.

● User answered Claude's questions:
  ⎿  · Where do you want me to start building? → Both together
     · How should the disk relate to the gas parcels? → Parcels ARE the disk 
     (Recommended)
     · Should I write this up as a repo design doc first? → Yes — design doc, 
     then build (Recommended)

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

● Now let me verify render.clj still compiles cleanly and has no newly-unused
  references:

  Ran 1 shell command (ctrl+o to expand)

● render compiles. Let me confirm the stellar/sp aliases are still used
  elsewhere in render (they fed the deleted code) and run the render tests:

  Ran 1 shell command (ctrl+o to expand)

● stellar/ is now unused in render (0 refs) — its require is dead. Let me find
  and remove it:

  Ran 1 shell command (ctrl+o to expand)

● Update(src/infra/render.clj)
  ⎿  Removed 1 line
      12     [domain.pacing :as pacing]
      13     [domain.player :as player]
      14     [domain.intervention :as intervention]
      15 -   [domain.stellar :as stellar]                                
      15     [domain.chemistry :as chemistry]
      16     [domain.ecology :as ecology]
      17     [domain.em :as em]

  Ran 1 shell command (ctrl+o to expand)

● Both compile. Let me run the analysis to confirm the render changes are clean,
  and verify with a live screenshot from the dev sim:

  Ran 1 shell command (ctrl+o to expand)

● All the warnings are pre-existing in other files (chemistry, em, genesis,
  integrator…) — none in my changed files (render, planet_formation,
  law/stellar). My changes are lint-clean and the verdict was no blocking
  findings.

  Let me leave a receipt, reconcile the task list, and record what's genuinely
  done vs. still ahead.

  Ran 1 shell command (ctrl+o to expand)
● Everything is code-complete, tested, and clean. Here's where things stand.

  Done and verified (Phases 1–2)

  Rendering
  - Offset bug fixed, then the sprites retired entirely. I first fixed the
  tilt-rotation offset, then confirmed the decisive fact: the live dev window
  already runs the volumetric fog, and the disc-tagged gas parcels — which orbit
  — are already baked into it. The 600 point-sprites were a pure redundant
  layer on top. So I deleted them (and the whole dead disk-particles cluster + a
  now-unused require). The disk now renders as the froxel-fog cloud of its 
  actual orbiting parcels — real rotation, the cloud look you asked for, and no
  more per-frame VBO churn (the lag you suspected).

  Formation physics
  - The 400 M⊕ planet is dead, replaced by real physics. planet-seeds no longer
  hands each planet 0.3 × disk/12. New in law/stellar: isolation-mass (M_iso ∝ 
  Σ_solid^(3/2)·a³·M★^(−1/2)) and hill-radius. Planets now grow to their local
  isolation mass — ~sub-Earth at 1 AU, rising outward.
  - I also fixed a feeding bug underneath it: the disk was normalized as
  equal-mass-per-annulus (unphysically dense inside), so I gave it a proper MMSN
  Σ(r) ∝ r^(−3/2) profile and widened seeding to 30 AU (Neptune-like).
  - Giants still form legitimately: a core past ~10 M⊕ beyond the ice line
  triggers runaway gas accretion and draws down disk gas — the only route to a
  giant.

  Verification: full clojure -M:test suite green; new tests cover the
  isolation-mass formula, the MMSN mass conservation, and a regression that a
  massive (0.05 M☉) disk no longer spawns a hundreds-of-Earth-mass terrestrial.
  bin/analyze — no blocking findings, my files lint-clean. Design doc at
  docs/designs/planetary-disk-rework.md, receipts logged. Nothing committed
  (your call).

  Honest about what's not yet done

  - Planetesimals still don't dynamically grow into planets. The seeder now
  produces physically-sized planets, but the seed-and-grow planetesimals from
  condensation still don't accrete tick-by-tick into them. That continuous
  feeding-zone growth system is the deeper Phase-2 piece (task #7) — it's what
  makes "planetesimals first, then grow" emerge rather than be placed.
  - Comets, asteroid belts as survivors, and the statistical census (your "zoom
  to the life planet, model the rest statistically") are Phase 3 (task #8),
  documented but not built.

  To see it

  The render change needs a code reload to show in your live window, and this is
  exactly the part you verify by looking. Two ways:

● User answered Claude's questions:
  ⎿  · How do you want to load and view the render change? → Restart dev service
     (fresh nebula)
     · Do you want the disk cloud tinted a distinct color? → Let me look first, 
     then decide

● Restarting the dev service it is. Let me check the documented restart
  procedure first, then do it.

  Ran 1 shell command, recalled 1 memory (ctrl+o to expand)

● Service is online (restart #61 — the pre-existing Barnes-Hut accumulation,
  unrelated). Let me wait for bootstrap, confirm it stays up (i.e., my code
  loaded without error), and check the logs.

  Ran 1 shell command (ctrl+o to expand)

● The service is up and stable — 26 s uptime with no re-crash, new code loaded,
  nREPL listening on 7888. No render or compile errors in the log; the only
  error entry is the old pre-existing Barnes-Hut NaN from hours ago. The
  :star/:protostar render branches I edited compile and load fine.

  You've got a fresh nebula now. Here's what to watch for as it collapses:

  - The disk should appear as a rotating, flattened cloud of the actual orbiting
  gas parcels — part of the same volumetric fog as the nebula, no separate
  sprite torus, no offset sliding it off the star. It'll grow and turn, not just
  pulse in and out.
  - When planets seed (after the disk matures, ~1 Myr sim-time), you should not
  see an instant 400 M⊕ terrestrial. Inner planets should come out
  sub-Earth-to-super-Earth; genuine giants should only appear beyond the ice 
  line.

  I've left task #6 (visual confirmation) open since that's genuinely yours to
  eyeball — the automated half (full suite green, analysis clean, service loads
  and runs) is done. If you'd rather I grab a screenshot once a star actually
  forms, say the word and I'll drive the nREPL to capture one.

  A couple of honest notes so you know exactly what you're looking at:

  - The disk color is still temperature-based (warm near the star), like the
  nebula. You said you'd look first — so look, and if it doesn't read as
  distinct "first-phase" matter, I'll tint it (task is queued).
  - The seed-and-grow planetesimals still don't dynamically accrete into planets
  yet — the seeder now places physically-sized planets, but the "planetesimals
  form first, then grow by collision/accretion" emergence is the next real piece
  (task #7). So you won't see an asteroid belt survive a growth phase yet;
  that's Phase 3 (task #8) with the comets and the statistical census.

  Nothing's committed — it's all in the working tree for you to look at and
  reverse if the disk cloud doesn't feel right. What do you see?

✻ Baked for 16m 1s