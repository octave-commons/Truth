● Spine done. Now the root-fix new spec — metal enrichment & seeding (M1):

● Write(docs/specs/metal-enrichment-and-seeding-realspec.md)
  ⎿  Wrote 136 lines to docs/specs/metal-enrichment-and-seeding-realspec.md
       1 # Metal Enrichment and Seeding Spec
       3 **Status:** ready for implementation
         honesty.md`
       5 **Scope:** make metals *exist* in a live simulation — seed cloud
         ity` world-creation knob, and thread explicit `:composition` thr
         richment events behind a concrete trigger.
         nrichment.md`, `docs/specs/nebular-chemistry-realspec.md` (eleme
         nt model).
       8 ---
      10 ## 1. The gap this closes
     … +206 lines (ctrl+o to expand)

● Now revise the nebular-chemistry realspec — sigmoid condensation, consumers,
  resolved open questions:

● Update(docs/specs/nebular-chemistry-realspec.md)
  ⎿  Added 3 lines, removed 2 lines
      1  # Nebular Chemistry and Composition Spec
      2  
      3 -**Status:** specification                                       
      4 -**Scope:** replace the `:metals` lump in `c/composition` with ex
        -plicit elements, track primordial vs. enriched composition, and 
        -derive condensed solids from temperature.                       
      3 +**Status:** ready for implementation                            
      4 +**Milestone:** M0 (substrate, done) + M2 (condensation & consume
        +rs) in `docs/specs/epic-phase0-physics-honesty.md`              
      5 +**Scope:** the composition *data model* — explicit elements, the
        + derived temperature-dependent solid/gas partition, and the cons
        +umers that read it. Where metals *come from* (seeding, enrichmen
        +t) is `docs/specs/metal-enrichment-and-seeding-realspec.md`.    
      6  **Depends on:** `docs/research/physics/nebular-chemistry-metal-e
         nrichment.md`
      7  
      8  ---

● Update(docs/specs/nebular-chemistry-realspec.md)
  ⎿  Added 3 lines, removed 2 lines
      100  
      101  - `(solar-composition)` → Population-I element map.
      102  - `(primordial-composition)` → BBN element map.
      103 -- `(partition-solids element-map temperature)` → `{:solid ... 
          -:gas ...}`.                                                   
      104 -- `(bulk-categories element-map temperature)` → `{:gas :rock :
          -metal :ice}`.                                                 
      103 +- `(solid-fraction temperature Tc)` → smooth condensed fractio
          +n, **sigmoid** `1/(1+exp((T−Tc)/ΔT))`, ΔT≈30 K (research §4.3)
          +. **Replaces the current hard `(< temp tc)` step** (`chemistry
          +.clj:105`).                                                   
      104 +- `(partition-solids element-map temperature)` → `{:solid ... 
          +:gas ...}`, per-element split by `solid-fraction` against the 
          +Lodders `Tc` table. Elements partly condensed at `T≈Tc` are sp
          +lit proportionally, not all-or-nothing.                       
      105 +- `(bulk-categories element-map temperature)` → `{:gas :rock :
          +metal :ice}`, **derived on demand** from the partition (never 
          +stored — resolves open Q2).                                   
      106  - `(condensed-inventory element-map temperature)` → combines t
           he above.
      107  - `(blend-compositions c1 m1 c2 m2)` → mass-weighted element m
           ap.
      108  - `(burn-composition element-map fraction)` → H → He conversio
           n, conserving mass.

● Update(docs/specs/nebular-chemistry-realspec.md)
  ⎿  Added 13 lines, removed 7 lines
      109  - `(enrich-composition element-map mass delta-mz yield-map)` →
            add metals from an event.
      110  - `(wind-composition star-composition)` → surface composition 
           of a wind parcel.
      111  
      112 -### 6.2 `domain.stellar`                                      
      112 +### 6.2 `domain.stellar` (seeding — see metal-enrichment-and-s
          +eeding-realspec)                                              
      113  
      114 -- `spawn-clump` seeds `:nebula` parcels with `law.composition/
          -primordial-composition` (or Population-I when configured).    
      115 -- All spawn specs (`wind`, `disk fragment`, `shatter`, `flare`
          -) carry an explicit `:composition`.                           
      116 -- Absorb/accrete packets carry `:composition` so the integrato
          -r can blend.                                                  
      114 +Seeding and per-spawn composition are specified in full in `me
          +tal-enrichment-and-seeding-realspec.md` (M1). Summary: `spawn-
          +clump` seeds the Pop-I floor by default (`:genesis/metallicity
          +`); all spawn specs and accrete packets carry explicit `:compo
          +sition`.                                                      
      115  
      116  ### 6.3 `domain.integrator`
      117  
      120 -- Register `c/comp-condensed` as a derived component in the in
          -fluence registry.                                             
      121 -- In `composition-ws`, after applying `c/comp-burn` and absorb
          -ed blends, compute and write `c/comp-condensed` from the body'
          -s temperature.                                                
      118 +- Register `c/comp-condensed` as a derived component in the in
          +fluence registry. **(Done: `integrator.clj:485`, `registry.clj
          +:104`.)**                                                     
      119 +- In `composition-ws`, after applying `c/comp-burn` and absorb
          +ed blends, compute and write `c/comp-condensed` from the body'
          +s temperature. **(Done.)** Derive for resolved bodies only; do
          + not cache (resolves open Q1 — on-demand is cheap enough at on
          +e fold/tick).                                                 
      120  
      121  ### 6.4 `domain.em`
      122  
      125 -- Lorentz acceleration scales with `c/ionization-fraction`. Ne
          -utral parcels (`ionization < 0.01`) feel zero Lorentz force.  
      123 +- Lorentz acceleration scales with `c/ionization-fraction`. Ne
          +utral parcels (`ionization < 0.01`) feel zero Lorentz force. *
          +*(Appears wired: `em.clj:146,378`.)**                         
      124  
      125 +### 6.5 Consumers of `c/comp-condensed` (M2 — this is the miss
          +ing half)                                                     
      126 +                                                              
      127 +The partition is derived every tick but has **no readers** tod
          +ay. M2 gives it consumers:                                    
      128 +                                                              
      129 +- **`infra.render/composition->material-color`** (`render.clj:
          +816`) currently reads retired keys `:metals :ice :H2O :volatil
          +es`, which do not exist in the element map — so every body ren
          +ders gas-tan. **Fix:** read `chemistry/bulk-categories` (`:roc
          +k :metal :ice :gas`) and map those to color. This is a live si
          +lent bug (task card `render-material-color-element-keys`).    
      130 +- **`domain.planet-formation/planet-seeds`** must set seeded-p
          +lanet bulk composition from `c/comp-condensed` at the formatio
          +n radius (see core-accretion-physics-realspec), not the static
          + per-type lookup at `planet_formation.clj:73`.                
      131 +- **Disk GI fragments** (`stellar.clj:1717`) copy parent `c/co
          +mposition` verbatim; that is acceptable for gas-giant embryos 
          +(they capture gas, not condensate), so no change required — bu
          +t note it explicitly so it is a decision, not an oversight.   
      132 +                                                              
      133  ---
      134  
      135  ## 7. Tests

● Update(docs/specs/nebular-chemistry-realspec.md)
  ⎿  Added 6 lines, removed 3 lines
      164  2. **Supernova enrichment:** a background parameter (`:genesis
           /metallicity`) for Phase 0; explicit ledger events are deferre
           d to galactic-scale chemistry in later phases.
      165  3. **Helium enrichment:** not tracked in Phase 0; the Populati
           on-I floor already includes the enriched helium value.
      166  
      167 -## 10. Open questions                                         
      167 +## 10. Resolved questions (were open)                         
      168  
      169 -1. What is the performance cost of deriving `c/comp-condensed`
          - for every resolved body every tick?                          
      170 -2. Should molecules (`H2O`, `CO2`, etc.) be derived on demand 
          -or cached as a separate component?                            
      169 +1. **Perf cost of deriving `c/comp-condensed` per body per tic
          +k** → **On demand, no cache.** It is folded once per tick for 
          +resolved bodies only; measure before optimizing. If a profile 
          +ever shows it hot, cache is a follow-up, not a precondition.  
      170 +2. **Molecules derived on demand vs cached component** → **On 
          +demand.** `bulk-categories`/molecule views are pure functions 
          +of `(element-map, T)`; never stored. Keeps ECS memory flat (ep
          +ic risk §8).                                                  
      171 +3. **Sigmoid vs hard-step condensation** → **Sigmoid**, ΔT≈30 
          +K (§6.1).                                                     
      172 +4. **Pressure-dependent Tc** → **Deferred (D3)**, gated on the
          + radial disk profile (radial-disk-structure-realspec) which su
          +pplies local pressure. Single 10⁻⁴-bar Lodders table until the
          +n.                                                            
      173 +5. **Full C/O molecular network** → **Deferred (D8)**, gated o
          +n a Phase-1 atmosphere-chemistry consumer of C/O.             

● Now the new core-accretion-physics spec (M3):

● Write(docs/specs/core-accretion-physics-realspec.md)
  ⎿  Wrote 139 lines to docs/specs/core-accretion-physics-realspec.md
      1 # Core Accretion Physics Spec
      3 **Status:** ready for implementation
      4 **Milestone:** M3 in `docs/specs/epic-phase0-physics-honesty.md`
      5 **Scope:** make planet mass and composition *emerge from accretio
         a sub-grid planetesimal-formation timescale (streaming instabili
        ty as a gate, not particles), a critical-core-mass + viscous-supp
         composition for seeded planets.
      6 **Depends on:** `docs/research/physics/protoplanetary-disks-plane
        t-formation.md`, `docs/specs/nebular-chemistry-realspec.md` (need
        mati
     … +222 lines (ctrl+o to expand)

● Now the deferred-capability spec — radial disk structure (D1), the linchpin
  that unblocks migration, pressure condensation, and pebble accretion:

● Write(docs/specs/radial-disk-structure-realspec.md)  ⎿  Wrote 97 lines to docs/specs/radial-disk-structure-realspec.md
      1 # Radial Disk Structure Spec (deferred capability D1)
      2 
      4 **Trigger (activate when):** the single-annulus disk regime — one
         Toomre Q evaluated at a fixed `disk-outer-temperature = 100 K` (
        `stellar.clj:1443,1554`) — demonstrably misclassifies a disk that
        uter* annulus is Toomre-unstable and fast-cooling while its globa
        l/average Q is stable, so the scalar classifier reports `:stable-
        disc` and refuses to fragment where a real disk would. When such 
      5 **Precondition:** M2–M3 landed (there is a solid budget and a wor
        king single-annulu
     … +128 lines (ctrl+o to expand)

● Now revise the disk realspec to point at the new M3 specs and resolve its open
  questions:

● Update(docs/specs/protoplanetary-disk-planet-formation-realspec.md)
  ⎿  Added 3 lines, removed 2 lines
      1  # Protoplanetary Disk and Planet Formation Spec
      2  
      3 -**Status:** specification                                       
      4 -**Scope:** ground the existing disk fragmentation and planet-see
        -ding code in the research findings; prevent substellar embryos f
        -rom spawning like planetesimals from disks; keep core accretion 
        -sub-grid.                                                       
      3 +**Status:** ready for implementation                            
      4 +**Milestone:** M3 in `docs/specs/epic-phase0-physics-honesty.md`
      5 +**Scope:** the **disk-side** contract — disk-regime classificati
        +on, GI fragmentation rules, and the seeder's wiring. The **plane
        +t-growth physics** (accretion-derived mass, sub-grid planetesima
        +ls, condensation-sequence composition, Gammie α(β)) is `docs/spe
        +cs/core-accretion-physics-realspec.md`. The **radial profile** t
        +hat would replace the single-annulus regime is deferred: `docs/s
        +pecs/radial-disk-structure-realspec.md` (D1).                   
      6  **Depends on:** `docs/research/physics/protoplanetary-disks-plan
         et-formation.md`
      7  
      8  ---

● Update(docs/specs/protoplanetary-disk-planet-formation-realspec.md)
  ⎿  Added 8 lines, removed 3 lines
      108  
      109  ## 6. Planet seeding rules
      110  
      111 -`domain.planet-formation/planet-seeds` remains the **one-shot*
          -* sub-grid seeder. Changes:                                   
      111 +`domain.planet-formation/planet-seeds` remains the **one-shot*
          +* sub-grid seeder. The disk-side contract here requires:      
      112  
      113  1. Use `c/disk-regime` `:solid-surface-density` rather than re
           computing it from disc bodies.
      114 -2. Use the new explicit element composition to choose `:terres
          -trial`/`:ice-giant`/`:gas-giant`.                             
      115 -3. Debit disk mass and angular momentum from the star's disk c
          -omponents.                                                    
      114 +2. Debit disk mass and angular momentum from the star's disk c
          +omponents.                                                    
      115  
      116 +The seeder's *growth physics* — accretion-derived mass (critic
          +al core mass + viscous-supply-limited runaway gas), sub-grid p
          +lanetesimal-formation timescale, and composition drawn from `c
          +/comp-condensed` at the formation radius — is specified in `co
          +re-accretion-physics-realspec.md`. That spec is what turns the
          + current `min(0.3·annulus-mass …)` heuristic and the static `p
          +lanet-composition` lookup into emergent outcomes.             
      117 +                                                              
      118 +**Also in M3 (disk-side cleanups, task cards):**              
      119 +- **Gammie α(β):** derive `disk-viscous-alpha` (`stellar.clj:1
          +419`) from the cooling β instead of a `^:const` (`α = 1/((9/4)
          +γ(γ−1)β)`). Card `gammie-alpha-beta-coupling`.                
      120 +- **Regime-tag unification:** `disk-regime-map` (`stellar.clj:
          +1568`) and `disc-regime` (`stellar.clj:1544`) emit two diverge
          +nt tag vocabularies, neither fully covered by `law/field` clos
          +ed sets (`field.clj:70,75`); `:streaming-zone` is never emitte
          +d. Unify to one tag set and add it to the schema. Card `disk-r
          +egime-tag-unification`.                                       
      121 +                                                              
      122  ---
      123  
      124  ## 7. System responsibilities

● Update(docs/specs/protoplanetary-disk-planet-formation-realspec.md)
  ⎿  Added 8 lines, removed 3 lines
      168  2. **Fragment mass cap:** direct GI fragments are capped below
            the deuterium-burning limit to guarantee `:gas-giant` classif
           ication.
      169  3. **Planet seeder remains one-shot** per star+disk; repeated 
           seeding is deferred until disk dissipation and migration are m
           odeled.
      170  
      171 -## 11. Open questions                                         
      171 +## 11. Resolved questions (were open)                         
      172  
      173 -1. How many GI fragments should a single disk be allowed to sp
          -awn before we force it to settle into a binary or stable state
          -?                                                             
      174 -2. Should migration be modeled after planet seeding, or are se
          -eded planets static on their initial orbits?                  
      173 +1. **GI fragment cap** → default **3** per star (§5.1), then t
          +he disk must settle to a binary (above the 0.5 mass ratio) or 
          +a stable state. Revisit only if runs show unphysical fragment 
          +swarms.                                                       
      174 +2. **Migration after seeding, or static?** → **Static now; mig
          +ration deferred (D2)**, gated on the radial disk profile (D1) 
          +— the torque prescription needs `Σ(r)`, `T(r)`. Seeded planets
          + hold their initial orbits until then.                        
      175 +3. **Annulus-resolved surface density (radial profile)** → **D
          +eferred (D1)**, `radial-disk-structure-realspec.md`, with a co
          +ncrete trigger (scalar Q misclassifies a radially-unstable dis
          +k).                                                           
      176 +4. **Pebble vs planetesimal accretion** → planetesimal accreti
          +on now (M3); **pebble deferred (D6)**, gated on D1 + a demonst
          +rated failure to grow giants in time.                         
      177 +5. **Composition fidelity (inherit disk C/O, ice fraction)** →
          + **built in M3** via `c/comp-condensed` at the formation radiu
          +s (core-accretion-physics-realspec §6); CO/CO₂ lines are D8.  
      178 +6. **GI fragment survival / rapid migration into star** → fold
          +s into migration (**D2**).                                    
      179 +7. **Multi-star disks** → **Deferred (D5)**, gated on a persis
          +tent bound stellar binary sharing disk mass.                  

