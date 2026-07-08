● These two specs predate the realspecs — they still use the retired :metals
  lump. I have everything I need now. Let me build the deliverable set. Starting
  with the spine: the epic rewritten as a sequenced roadmap with a
  capability-gate register (every deferral gets a concrete trigger).

● Write(docs/specs/epic-phase0-physics-honesty.md)
  ⎿  Added 127 lines, removed 105 lines
        1 -# Epic: Phase 0 Physics Honesty — Chemistry, Disks, Plasma, an
          -d Inspection                                                  
        1 +# Roadmap: Phase 0 Physics Honesty — Chemistry, Disks, Plasma,
          + Inspection                                                   
        2  
        3 -**Status:** specification                                     
        4 -**Scope:** tie together four related specs so the simulation n
          -o longer emits planetesimals from stars, tracks real element c
          -omposition, treats stellar wind as plasma, and gives the playe
          -r a visual way to verify it all.                              
        5 -**Related specs:**                                            
        6 -- `docs/specs/nebular-chemistry-realspec.md`                  
        7 -- `docs/specs/protoplanetary-disk-planet-formation-realspec.md
          -`                                                             
        8 -- `docs/specs/stellar-wind-plasma-state-realspec.md`          
        9 -- `docs/specs/rich-entity-inspection-ui-realspec.md`          
        3 +**Status:** roadmap                                           
        4 +**Supersedes:** the original `epic-phase0-physics-honesty.md` 
          +epic (kept in git history).                                   
        5 +**Scope:** sequence every step needed so Phase 0 stops emittin
          +g planetesimals from stars, tracks real element composition en
          +d-to-end, grows planets by accretion, treats stellar wind as p
          +lasma, and lets the player see it — **including the work curre
          +ntly deferred, each with an explicit trigger.**               
        6  
        7 +**Member specs (current):**                                   
        8 +- `docs/specs/nebular-chemistry-realspec.md` — composition dat
          +a model, condensation, consumers                              
        9 +- `docs/specs/metal-enrichment-and-seeding-realspec.md` — wher
          +e metals come from (root fix)                                 
       10 +- `docs/specs/protoplanetary-disk-planet-formation-realspec.md
          +` — disk regime + GI fragmentation + seeder wiring            
       11 +- `docs/specs/core-accretion-physics-realspec.md` — accretion-
          +derived planet mass + sub-grid planetesimals                  
       12 +- `docs/specs/radial-disk-structure-realspec.md` — Σ(r)/T(r) p
          +rofile (deferred capability, gated)                           
       13 +- `docs/specs/phase0-chemistry-differentiation.md` — malleabil
          +ity, collision chemistry, differentiation                     
       14 +- `docs/specs/phase0-habitability-handoff.md` — `:planet-candi
          +date` contract                                                
       15 +- `docs/specs/stellar-wind-plasma-state-realspec.md` — hot ion
          +ized wind parcels                                             
       16 +- `docs/specs/rich-entity-inspection-ui-realspec.md` — visual 
          +verification panel                                            
       17 +                                                              
       18  ---
       19  
       13 -## 1. Motivation                                              
       20 +## 1. Why this exists                                         
       21  
       15 -Phase 0 currently has four interlocking credibility problems: 
       22 +Phase 0 has a credibility chain, and today it is broken at the
          + first link:                                                  
       23  
       17 -1. **Composition is fake.** Every parcel is seeded with `{:H 0
          -.75 :He 0.24 :metals 0.01}`. Metals have no origin, no element
          - breakdown, and no consequence.                               
       18 -2. **Planets and planetesimals spawn from the star's disk as e
          -xplicit bodies.** A disk can emit `:planetesimal`, `:gas-giant
          -`, or `:brown-dwarf` entities directly, which is physically th
          -e wrong channel for planet formation.                         
       19 -3. **Stars emit cold neutral gas.** Wind parcels are `:nebula`
          - with a token ionization tag; they feel hydro and EM as if the
          -y were molecular gas.                                         
       20 -4. **The player cannot see any of this.** The inspector is a t
          -ext card with a composition string.                           
       24 +1. **Metals never exist.** Every parcel is seeded with primord
          +ial (zero-metal) composition (`domain/genesis.clj:70`, `domain
          +/stellar.clj:2320`). O, C, Fe, Si, Mg are permanently 0 in a l
          +ive run.                                                      
       25 +2. Because metals never exist, the **condensation partition** 
          +(`c/comp-condensed`) is derived every tick but describes nothi
          +ng, and it has **no consumers** anyway.                       
       26 +3. Because nothing reads the partition, **render material colo
          +r** silently reads retired keys (`:metals :ice :H2O`) that no 
          +longer exist, so every body renders gas-tan (`infra/render.clj
          +:816`).                                                       
       27 +4. **Planet mass is a disk-fraction heuristic** (`min(0.3·annu
          +lus-mass …)`, `planet_formation.clj:177`), not accretion-deriv
          +ed, and **planet composition is a static lookup table** (`plan
          +et_formation.clj:73`) that ignores where the planet formed.   
       28 +5. **Differentiation, volatile budget, and the habitability ha
          +ndoff contract** are specified but coded-not-wired or missing.
       29  
       22 -This epic fixes all four by grounding the code in the research
          - notebooks and giving the player a visual verification tool.  
       30 +This roadmap fixes the chain in dependency order and gives eve
          +ry deferred item a trigger, so "later" is never a dead end.   
       31  
       32  ---
       33  
       26 -## 2. Epic-level invariants                                   
       34 +## 2. Locked decisions (2026-07-06 design session)            
       35  
       28 -1. **One composition source of truth.** `c/composition` is an 
          -explicit element map. All other composition views (`c/comp-con
          -densed`, `:planet-type`, render bars) are derived.            
       36 +| # | Decision | Consequence |                                
       37 +|---|----------|-------------|                                
       38 +| 1 | **Metallicity default = Population-I floor** (Z=0.0167, 
          +Asplund 2009). `:primordial` is an opt-in preset via a `:genes
          +is/metallicity` world-creation knob. | Metals exist from tick 
          +0; the whole downstream chain becomes live. |                 
       39 +| 2 | **Every deferral is capability-gated.** No item is defer
          +red without a named precondition **and** a trigger describing 
          +when it becomes active. | See §5 register. |                  
       40 +| 3 | **Migration is deferred behind the radial disk profile**
          + (D2 ⟵ D1). | Seeded planets stay static until Σ(r)/T(r) exist
          +s. |                                                          
       41 +| 4 | **Condensation uses a smooth sigmoid**, `f_solid = 1/(1+
          +exp((T−Tc)/ΔT))`, ΔT≈30 K; categories (rock/ice/metal) derived
          + on demand from elements + T. No molecular network, no pressur
          +e scaling yet. | M2 fidelity bounded; D3/D8 gated. |          
       42 +| 5 | **Planet mass is accretion-derived**: critical core mass
          + + viscous-supply-limited runaway gas accretion. Pebble accret
          +ion is deferred. | M3. |                                      
       43 +| 6 | **Planetesimal formation (streaming instability) is sub-
          +grid**: a solid-surface-density + formation-timescale gate fee
          +ding the seeder; no explicit clumps. | M3; explicit clumping i
          +s D9. |                                                       
       44 +| 7 | **The `:planet-candidate` handoff contract is built now*
          +* as Phase 0's canonical output; only the downstream *consumer
          +* is capability-gated. | M5; consumer is D-consumer. |        
       45 +                                                              
       46 +---                                                           
       47 +                                                              
       48 +## 3. Epic-level invariants (unchanged from the original epic,
          + restated)                                                    
       49 +                                                              
       50 +1. **One composition source of truth.** `c/composition` is an 
          +explicit element map. `c/comp-condensed`, `:planet-type`, and 
          +render bars are all derived.                                  
       51  2. **Planets come only from core accretion.** `:planet` entiti
           es are created only by `domain.planet-formation/planet-seeds`.
            Direct disk fragmentation produces only `:gas-giant` embryos 
           under strict GI conditions.
       30 -3. **Plasma is a state, not a matter-state.** Stellar ejecta r
          -emains `:nebula` but carries `c/ionization-fraction` near 1.0 
          -and high temperature. EM coupling scales with ionization.     
       31 -4. **Render verifies physics.** The rich inspector exposes com
          -position bars, thermal history, disk regime, hierarchy, and ev
          -ents so the player can see that the above rules hold.         
       52 +3. **Plasma is a state, not a matter-state.** Stellar ejecta s
          +tays `:nebula` but carries `c/ionization-fraction` near 1.0; E
          +M coupling scales with ionization.                            
       53 +4. **Render verifies physics.** The rich inspector exposes com
          +position, thermal history, disk regime, hierarchy, and events.
       54  5. **Mass, momentum, angular momentum, and element fractions a
           re conserved** across every transformation.
       55  
       56  ---
       57  
       36 -## 3. Cross-spec dependencies                                 
       58 +## 4. Milestone sequence                                      
       59  
       38 -```                                                           
       39 -nebular-chemistry-realspec                                    
       40 -    ├── provides: explicit element composition, c/comp-condens
          -ed                                                            
       41 -    ├── consumes: c/temperature (for condensation)            
       42 -    └── feeds: disk-planet-formation-realspec, stellar-wind-pl
          -asma-state-realspec, rich-inspection-ui-realspec              
       60 +Each milestone lists its spec, entry precondition, exit/verifi
          +cation, and the gap findings it closes. Milestones are ordered
          + by dependency; do not start one before its predecessor's exit
          + criteria hold.                                               
       61  
       44 -protoplanetary-disk-planet-formation-realspec                 
       45 -    ├── provides: c/disk-regime, :planet entities             
       46 -    ├── consumes: chemistry (composition, condensed inventory)
       47 -    └── feeds: rich-inspection-ui-realspec                    
       62 +### M0 — Composition substrate (LARGELY DONE)                 
       63 +- **Spec:** nebular-chemistry-realspec §3–5.                  
       64 +- **State:** element-resolved `c/composition`, mass-weighted `
          +blend-compositions`, H→He `burn-step`/`nucleosynthesis-system`
          +, deuterium depletion, and `c/comp-condensed` derivation are i
          +mplemented and folded in the tick (`integrator.clj:485`, `regi
          +stry.clj:104`, `chemistry.clj:308`). Schemas in `law/compositi
          +on.clj`.                                                      
       65 +- **Remaining:** none blocking; this is the foundation M1–M5 b
          +uild on.                                                      
       66  
       49 -stellar-wind-plasma-state-realspec                            
       50 -    ├── provides: hot ionized wind parcels, c/photoionization 
       51 -    ├── consumes: chemistry (star composition), em (ionization
          --weighted Lorentz)                                            
       52 -    └── feeds: rich-inspection-ui-realspec                    
       67 +### M1 — Metals exist (ROOT FIX)                              
       68 +- **Spec:** metal-enrichment-and-seeding-realspec.            
       69 +- **Closes:** chemistry gap #1 (metals never seeded), and unbl
          +ocks #2/#3.                                                   
       70 +- **Entry:** M0.                                              
       71 +- **Work:** add `:genesis/metallicity` knob (`:population-i` d
          +efault, `:primordial` opt-in); seed `seed-clump`/`spawn-clump`
          + and every spawn spec (wind, disk fragment, shatter, flare) an
          +d every absorb/accrete packet with an explicit `:composition`.
       72 +- **Exit:** in a live run every `:nebula` parcel has nonzero O
          +/C/Fe/Si summing to Z≈0.0167; `composition-sums-to-unity` hold
          +s after seeding, blending, and accretion.                     
       73  
       54 -rich-entity-inspection-ui-realspec                            
       55 -    ├── provides: visual verification panel                   
       56 -    └── consumes: all of the above                            
       57 -```                                                           
       74 +### M2 — Condensation & consumers                             
       75 +- **Spec:** nebular-chemistry-realspec (revised) §6–8.        
       76 +- **Closes:** chemistry gaps #2 (comp-condensed has no consume
          +rs), #3 (render reads retired keys), #7 (hard-step condensatio
          +n).                                                           
       77 +- **Entry:** M1.                                              
       78 +- **Work:** sigmoid `partition-solids`; derive `bulk-categorie
          +s` on demand; fix `composition->material-color` to read `bulk-
          +categories`; make planet seeding consume `c/comp-condensed`.  
       79 +- **Exit:** `partition-solids-water-ice-at-snow-line` matches 
          +Lodders; a live run renders bodies by material class (rock/ice
          +/metal/gas), not uniform tan.                                 
       80  
       59 ----                                                           
       81 +### M3 — Core accretion physics                               
       82 +- **Spec:** core-accretion-physics-realspec + protoplanetary-d
          +isk-planet-formation-realspec (revised).                      
       83 +- **Closes:** planet gaps #1 (streaming instability), #2 (runa
          +way gas accretion), #3 (condensation-sequence composition), #5
          + (seeder ignores regime σ_solid), #7 (Gammie α(β)).           
       84 +- **Entry:** M2 (needs `c/comp-condensed` for composition & so
          +lid budget).                                                  
       85 +- **Work:** critical-core-mass + viscous-supply-limited runawa
          +y gas accretion; sub-grid planetesimal-formation timescale (St
          +okes + metallicity gate) as the seeder's solid feed; seeded-pl
          +anet composition from the condensation sequence at formation r
          +adius; derive Gammie α from cooling β.                        
       86 +- **Exit:** planet mass emerges from accretion (not a disk fra
          +ction); planet composition varies with formation radius relati
          +ve to snow line; `planet-seeds-are-only-planet-source` and con
          +servation tests still pass.                                   
       87  
       61 -## 4. New components summary                                  
       88 +### M4 — Differentiation & collision chemistry                
       89 +- **Spec:** phase0-chemistry-differentiation (revised).       
       90 +- **Closes:** chemistry gaps #4 (differentiation unwired), #6 
          +(collision composition/volatile loss), #7 volatile-budget.    
       91 +- **Entry:** M2.                                              
       92 +- **Work:** wire `differentiation-system` (molten bodies, `mal
          +leability > 0.8`); add `c/volatile-budget`; merges lose volati
          +les by impact temperature; fragments inherit density-biased co
          +mposition.                                                    
       93 +- **Exit:** `molten-body-differentiates`, `volatiles-lost-in-h
          +ot-collision`, `differentiation-conserves-mass` pass.         
       94  
       63 -| Component | Spec | Owner | Purpose |                        
       64 -|-----------|------|-------|---------|                        
       65 -| `c/comp-condensed` | chemistry | `domain.integrator` | deriv
          -ed solid/gas element partition |                              
       66 -| `c/disk-regime` | disk/planet | `domain.stellar` | Toomre Q,
          - cooling beta, regime, solid surface density |                
       67 -| `c/disk-fragments-spawned` | disk/planet | `domain.stellar` 
          -| cap on GI fragment spawns |                                 
       68 -| `c/photoionization` | plasma | `domain.stellar` | ionization
          - influence from stellar EUV/XUV |                             
       69 -| `c/inspect-history` | UI | `domain.inspect-history` | rollin
          -g entity state samples |                                      
       95 +### M5 — Habitability handoff contract                        
       96 +- **Spec:** phase0-habitability-handoff (revised).            
       97 +- **Closes:** chemistry gap #5 (handoff contract missing).    
       98 +- **Entry:** M3 + M4 (material class, thermal band, volatile b
          +udget feed the record).                                       
       99 +- **Work:** build the full `:planet-candidate` record + `:phas
          +e0-handoff` event; material-class/thermal-band/atmosphere-clas
          +s/retained-species/orbit-stability.                           
      100 +- **Exit:** `handoff-record-contains-required-keys` passes; a 
          +settled solar-analog run emits a candidate record with all key
          +s.                                                            
      101  
       71 ----                                                           
      102 +### M6 — Rich inspection verifies it all                      
      103 +- **Spec:** rich-entity-inspection-ui-realspec.               
      104 +- **Entry:** M1–M5 (there must be real state to show).        
      105 +- **Exit:** inspector renders composition bars, thermal sparkl
          +ine, disk regime, hierarchy, and events for a selected body.  
      106  
       73 -## 5. New/changed systems summary                             
      107 +**Plasma track (parallel, independent of M1–M6 ordering except
          + M6):** `stellar-wind-plasma-state-realspec` (hot ionized wind
          +, ionization-weighted Lorentz, plasma cooling). It shares only
          + the composition dependency (M1) — wind parcels carry star com
          +position. Slot it after M1, in parallel with M2–M4.           
      108  
       75 -| System | Spec | Change |                                    
       76 -|--------|------|--------|                                    
       77 -| `domain.chemistry` | chemistry | partition-solids, bulk-cate
          -gories, blend, burn, condensed-inventory |                    
       78 -| `domain.integrator/composition-ws` | chemistry | apply blend
          -s/burn; derive `c/comp-condensed` |                           
       79 -| `domain.stellar/disk-evolution-system` | disk/planet | compu
          -te `c/disk-regime`; restrict fragments to `:gas-giant` |      
       80 -| `domain.planet-formation/planet-seeds` | disk/planet | use `
          -c/disk-regime` and explicit element composition |             
       81 -| `domain.stellar/stellar-wind-system` | plasma | hot ionized 
          -parcels; carry star composition |                             
       82 -| `domain.em/lorentz-acceleration-system` | plasma | scale by 
          -`c/ionization-fraction` |                                     
       83 -| `domain.stellar/eos-system` or new `plasma-cooling-system` |
          - plasma | radiative cooling of hot parcels |                  
       84 -| `domain.inspect-history` | UI | append samples to `c/inspect
          --history` |                                                   
       85 -| `infra.inspect` | UI | rich panel layout + rendering |      
       86 -                                                              
      109  ---
      110  
       89 -## 6. Data flow example: a star forming and enriching its neig
          -hborhood                                                      
      111 +## 5. Deferred capability register (every "later" has a trigge
          +r)                                                            
      112  
       91 -1. A Population-I cloud collapses. Every `:nebula` parcel carr
          -ies the full solar element map.                               
       92 -2. A parcel crosses the H-burning limit and becomes `:protosta
          -r`, then `:star`.                                             
       93 -3. The star's `c/composition` is updated by H→He burning in `d
          -omain.integrator`.                                            
       94 -4. The star launches a wind parcel via `stellar-wind-system`: 
       95 -   - `:matter-state :nebula`                                  
       96 -   - `:temperature 1.5e6`                                     
       97 -   - `:ionization-fraction 0.95`                              
       98 -   - `:composition` = star's post-burn composition            
       99 -5. The hot parcel radiatively cools and interacts weakly with 
          -the magnetic field because it is ionized.                     
      100 -6. Nearby `:nebula` parcels are photoionized by the star's XUV
          -, raising their `c/ionization-fraction`.                      
      101 -7. A disk forms around the star. `disk-evolution-system` compu
          -tes `c/disk-regime`.                                          
      102 -8. If the disk is massive and fast-cooling, it spawns one `:ga
          -s-giant` embryo (capped below deuterium limit).               
      103 -9. After disk maturity, `planet-seeds` runs once, producing `:
          -planet` entities with composition derived from `c/comp-condens
          -ed` at the snow line.                                         
      104 -10. The player opens the rich inspector on the star and sees: 
          -composition bars, wind event history, disk regime, planet hier
          -archy.                                                        
      113 +Deferrals are legitimate only with a **precondition** (what mu
          +st exist first) and a **trigger** (the observable that says "n
          +ow"). Anything not in this table is in-scope for M1–M6.       
      114  
      115 +| ID | Capability | Precondition | Trigger (activate when…) | 
          +Spec |                                                        
      116 +|----|-----------|--------------|--------------------------|--
          +----|                                                         
      117 +| **D1** | Radial disk structure Σ(r)/T(r), per-annulus regime
          +s | M2–M3 landed | the single-annulus Toomre Q at fixed `disk-
          +outer-temperature=100 K` misclassifies a disk that a radial pr
          +ofile would resolve — i.e. a test disk that should fragment in
          + its outer annulus but not globally | radial-disk-structure-re
          +alspec (drafted, status: deferred) |                          
      118 +| **D2** | Planet migration (Type I/II torque) | **D1** (torqu
          +e needs Σ(r), T(r)) | D1 landed **and** ≥1 seeded planet coexi
          +sts with a non-dissipated disk | new spec at trigger time |   
      119 +| **D3** | Pressure-dependent condensation Tc(P) | **D1** (nee
          +ds local pressure) | D1 landed; fold Tc(P) into `partition-sol
          +ids` | fold into nebular-chemistry-realspec at trigger |      
      120 +| **D4** | SN enrichment events, metallicity-dependent yields 
          +| a star reaching a core-collapse end-state exists in-sim (no 
          +stellar death is modelled yet) | `stellar-death`/remnant event
          +s are emitted (see stellar-winds spec) — then `supernova-enric
          +hment` fires on them | fold into metal-enrichment-and-seeding-
          +realspec at trigger |                                         
      121 +| **D5** | Multi-star / circumbinary disks | a bound stellar b
          +inary can persist (GI binary threshold already exists, `stella
          +r.clj:1677`) | a bound binary survives > N ticks sharing disk 
          +mass | new spec at trigger |                                  
      122 +| **D6** | Pebble accretion branch | **D1** (Stokes number nee
          +ds local Σ_gas, T) | D1 landed **and** planetesimal-only accre
          +tion (M3) fails to grow a gas giant within the disk lifetime i
          +n a solar-analog run | fold into core-accretion-physics-realsp
          +ec at trigger |                                               
      123 +| **D7** | Collision bounce/graze outcome | M4 merge/shatter w
          +ired | brittle high-speed **grazing** impacts are demonstrably
          + mishandled by the merge-or-shatter binary (measured merger ra
          +te for high impact-parameter cold collisions) | fold into phas
          +e0-chemistry-differentiation at trigger |                     
      124 +| **D8** | Full C/O molecular network (CO/CO₂/H₂O/CH₄/NH₃) | M
          +2 categories + Phase 1 atmosphere chemistry | atmospheric C/O 
          +becomes an observable a consumer reads (Phase 1 exists) | new 
          +spec at trigger |                                             
      125 +| **D9** | Explicit streaming-instability clumping (particles)
          + | M3 sub-grid timescale wired | the sub-grid timescale cannot
          + reproduce inspectable planetesimal-belt structure the player 
          +needs | new spec at trigger |                                 
      126 +| **D10** | Radiogenic/isotope heating, interior EoS | M4 diff
          +erentiation | Phase 1 planetary geology consumes interior stat
          +e | new spec at trigger (`domain.interior`) |                 
      127 +| **D-consumer** | Phase-1 consumer of `:planet-candidate` | M
          +5 contract emitted | Phase 1 (planetary cooling / prebiotic ch
          +emistry) begins | Phase 1 epic |                              
      128 +                                                              
      129 +Helium enrichment (research §2.3) is **not deferred — it is a 
          +decision**: the Pop-I floor already carries the enriched heliu
          +m value, so no separate tracking is needed in Phase 0.        
      130 +                                                              
      131  ---
      132  
      108 -## 7. Implementation order                                    
      133 +## 6. Small-gap task cards (no dedicated spec; tracked in `kan
          +ban/tasks/`)                                                  
      134  
      110 -1. **Chemistry/composition** — it is the foundation. Everythin
          -g else consumes it.                                           
      111 -2. **Stellar wind plasma** — fixes the most visible "stars emi
          -t gas" problem.                                               
      112 -3. **Disk/planet formation cleanup** — stops the wrong bodies 
          -from spawning.                                                
      113 -4. **Rich inspector** — built last so it can display all the n
          -ew state.                                                     
      135 +These are discrete fixes that live inside a milestone but are 
          +worth their own card:                                         
      136  
      137 +- **render-material-color-element-keys** — `composition->mater
          +ial-color` reads retired `:metals/:ice/:H2O`; switch to `bulk-
          +categories`. (M2; live bug.)                                  
      138 +- **disk-regime-tag-unification** — `disk-regime-map` (`stella
          +r.clj:1568`) and `disc-regime` (`stellar.clj:1544`) emit two d
          +ifferent tag vocabularies; neither matches `law/field` closed 
          +sets. Unify. (M3.)                                            
      139 +- **law-planet-formation-namespace** — create `src/law/planet_
          +formation.clj`; relocate `snow-line-temperature`, `ice-enhance
          +ment-factor`, `proto-solar-metal-frac` out of `domain/planet_f
          +ormation.clj:14` per the constants-in-`law/` convention. (M3.)
      140 +- **gammie-alpha-beta-coupling** — derive `disk-viscous-alpha`
          + from cooling β instead of the `^:const` (`stellar.clj:1419`).
          + (M3.)                                                        
      141 +- **ecology-water-gate-snowline** — `moisture-from-composition
          +` (`ecology.clj:383`) counts raw `:H`, trivially satisfying `h
          +as-water`; gate on condensed `:ice` category + temperature. (M
          +4.)                                                           
      142 +                                                              
      143  ---
      144  
      117 -## 8. Acceptance criteria                                     
      145 +## 7. Acceptance criteria (epic-level)                        
      146  
      119 -- [ ] All `:nebula` parcels carry explicit element composition
          -.                                                             
      120 -- [ ] `c/comp-condensed` is derived and conserved through accr
          -etion/merging.                                                
      121 -- [ ] Stellar wind parcels launch hot and ionized with star co
          -mposition.                                                    
      122 -- [ ] Lorentz acceleration scales with ionization fraction; ne
          -utral parcels feel no EM force.                               
      123 -- [ ] Disk fragmentation produces only `:gas-giant` embryos an
          -d only under `Q < 1` and `β < 3`.                             
      124 -- [ ] `:planet` entities are produced only by `domain.planet-f
          -ormation/planet-seeds`.                                       
      125 -- [ ] Rich inspector renders composition bars, sparklines, dis
          -k regime, hierarchy, and events.                              
      126 -- [ ] `clojure -M:test` passes with new tests for each subsyst
          -em.                                                           
      127 -- [ ] `clojure -M:cljfmt check` is clean.                     
      147 +- [ ] `:genesis/metallicity` knob exists; default run has nonz
          +ero metals (M1).                                              
      148 +- [ ] Bodies render by material class from live composition (M
          +2).                                                           
      149 +- [ ] `c/comp-condensed` has at least one consumer (planet see
          +ding + render) (M2).                                          
      150 +- [ ] Planet mass is accretion-derived; composition varies wit
          +h formation radius (M3).                                      
      151 +- [ ] Disk fragmentation produces only `:gas-giant` embryos, o
          +nly under Q<1 ∧ β<3 (existing, keep green).                   
      152 +- [ ] Molten bodies differentiate; hot collisions lose volatil
          +es (M4).                                                      
      153 +- [ ] `:phase0-handoff` emits a full `:planet-candidate` recor
          +d (M5).                                                       
      154 +- [ ] Rich inspector renders composition, disk regime, hierarc
          +hy, events (M6).                                              
      155 +- [ ] Every deferred item in §5 has a live trigger condition (
          +this doc).                                                    
      156 +- [ ] `clojure -M:test` green; `bin/analyze --strict` clean af
          +ter each milestone.                                           
      157  
      158  ---
      159  
      131 -## 9. Risks and mitigations                                   
      160 +## 8. Risks and mitigations                                   
      161  
      162  | Risk | Mitigation |
      163  |------|------------|
      135 -| Composition maps bloat ECS memory | Store as plain maps; onl
          -y track 17 elements; derive molecules/categories on demand. | 
      136 -| Plasma cooling destabilizes the cloud | Cap cooling per tick
          -; test with the dominant-star suite. |                        
      137 -| Restricting GI fragments reduces planet diversity | Sub-grid
          - planet seeder still produces terrestrial/ice/gas giants from 
          -disk solids. |                                                
      138 -| Rich inspector hurts frame rate | Make it optional; only rec
          -ord history for selected/focused entities initially. |        
      139 -| Tests break across many files | Implement spec by spec; run 
          -full test suite after each. |                                 
      164 +| Composition maps bloat ECS memory | Plain maps, 17 elements;
          + categories/molecules derived on demand, never stored. |      
      165 +| Deriving `c/comp-condensed` every tick is costly | Derive on
          +ly for resolved bodies; it is already folded once per tick — m
          +easure before caching (nebular-chemistry-realspec open Q, now 
          +resolved: on-demand). |                                       
      166 +| Accretion-derived mass destabilises seeding tests | Implemen
          +t M3 behind the existing one-shot seeder gate; keep conservati
          +on tests as the contract. |                                   
      167 +| Restricting GI fragments reduces planet diversity | Sub-grid
          + seeder still produces terrestrial/ice/gas-giant from disk sol
          +ids (M3). |                                                   
      168 +| A deferral's trigger never fires (dead capability) | Each tr
          +igger in §5 is an **observable in a standard run or test**, no
          +t a calendar date; if a trigger cannot be observed, the capabi
          +lity is genuinely unneeded. |                                 
      169  
      170  ---
      171  
      143 -## 10. Decisions (epic level)                                 
      172 +## 9. Open questions (epic level)                             
      173  
      145 -1. **Seeded planets stay static on their initial orbits.** Mig
          -ration via disk torque is deferred until after this epic.     
      146 -2. **Add a `:genesis/metallicity` world-creation knob** with `
          -:primordial` (BBN) and `:population-i` (solar) presets. Defaul
          -t is `:population-i`.                                         
      147 -3. **Dust opacity/cooling use an effective dust-to-gas ratio**
          - derived from `c/comp-condensed` solids. Full radiative transf
          -er is out of scope for Phase 0.                               
      148 -4. **Rich inspector stays selected-body only.** A global debug
          - overlay is a future infra/debug tool, not this epic.         
      149 -                                                              
      150 -## 11. Open questions (epic level)                            
      151 -                                                              
      152 -(none)                                                        
      174 +(none — the round-1/round-2 design session resolved the spine;
          + per-spec open questions are resolved in each spec's decisions
          + section with capability-gated conditions.)                   

