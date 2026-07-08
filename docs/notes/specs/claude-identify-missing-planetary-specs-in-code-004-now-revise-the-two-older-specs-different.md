● Now revise the two older specs. Differentiation (M4) first — align to the
  element-resolved model and gate bounce/graze:

● Update(docs/specs/phase0-chemistry-differentiation.md)
  ⎿  Added 7 lines, removed 2 lines
       1  # Phase 0 Chemistry & Differentiation Spec
       2  
       3 -**Status:** draft                                              
       4 -**Goal:** Make composition a first-class simulation driver in P
         -hase 0: it should determine material class, collision outcome, 
         -and the rough volatile budget a planet carries to Phase 1.     
       3 +**Status:** ready for implementation                           
       4 +**Milestone:** M4 in `docs/specs/epic-phase0-physics-honesty.md
         +`                                                              
       5 +**Goal:** Make composition a first-class simulation driver in P
         +hase 0: it should determine material class, collision outcome, 
         +and the rough volatile budget a planet carries to Phase 1.     
       6  **Principle:** Chemistry is not decorative. The same mass fract
          ions that describe a nebula clump must also describe a finished
           planet, and the transition between them is governed by tempera
          ture and gravity.
       7  
       8 +> **Model update (2026-07-06):** This spec predates the element
         +-resolved composition model. The `:metals` lump referenced belo
         +w is **retired** (`law/composition.clj:9`); composition is now 
         +an explicit element map (`:H :He :O :C :Fe :Si …`), and materia
         +l groups (`:rock :metal :ice :gas`) are **derived on demand** v
         +ia `domain.chemistry/bulk-categories` — see `nebular-chemistry-
         +realspec.md`. Read §2 in that light: "metals" means the derived
         + metal category, not a stored key.                             
       9 +                                                               
      10 +> **Already wired (do not rebuild):** malleability (`law.stella
         +r/malleability`) and the merge/shatter branch (`stellar-merge-h
         +andler`, `stellar.clj:2254` → `shatter-bodies`, `stellar.clj:22
         +15`) exist and are load-bearing; merges apply mass-weighted com
         +position blend + impact heating (`integrator.clj:181`). M4 adds
         + the **missing** pieces: the differentiation system, the volati
         +le budget, volatile loss on hot merges, and density-biased frag
         +ment composition. Bounce/graze is deferred (D7).               
      11 +                                                               
      12  ---
      13  
      14  ## 1. Background from notes

● Update(docs/specs/phase0-chemistry-differentiation.md)
  ⎿  Added 7 lines, removed 8 lines
      102  - Fragments inherit composition biased by density: metal/silic
           ate enriched toward larger fragments; volatiles prefer smaller
            fragments.
      103  - Emit `:event/fragmentation` to ledger.
      104  
      105 -### 4.3 Bounce / graze                                        
      105 +### 4.3 Bounce / graze — DEFERRED (D7)                        
      106  
      107 -Conditions:                                                   
      108 -- `malleability` low but kinetic energy below fragmentation th
          -reshold.                                                      
      109 -- Impact parameter large (grazing collision).                 
      107 +**Status:** deferred, capability-gated. The current model is m
          +erge-or-shatter only; a grazing elastic outcome is a third bra
          +nch.                                                          
      108  
      111 -Actions:                                                      
      112 -- Apply elastic-ish impulse conserving momentum and angular mo
          -mentum.                                                       
      113 -- Convert a fraction of relative kinetic energy into thermal e
          -nergy.                                                        
      114 -- Do not merge.                                               
      109 +**Trigger (activate when):** brittle, high-speed **grazing** i
          +mpacts (large impact parameter, low bound energy) are demonstr
          +ably mishandled by the merge-or-shatter binary — measured as a
          +n unphysically high merger rate for high-impact-parameter cold
          + collisions in a standard run. Until that is observed, merge-o
          +r-shatter is an acceptable simplification.                    
      110  
      111 +When promoted:                                                
      112 +- Condition: `malleability` low, kinetic energy below fragment
          +ation threshold, impact parameter large.                      
      113 +- Apply an elastic-ish impulse conserving momentum and angular
          + momentum; convert a fraction of relative KE to heat; do not m
          +erge.                                                         
      114 +                                                              
      115  ---
      116  
      117  ## 5. Differentiation

● Now the habitability handoff spec — confirm "build full contract now", gate
  only the consumer, fix stale refs:

● Update(docs/specs/phase0-habitability-handoff.md)
  ⎿  Added 7 lines, removed 2 lines
       1  # Phase 0 Habitability Handoff Spec
       2  
       3 -**Status:** draft                                              
       4 -**Goal:** Define when Phase 0 ends and what information a survi
         -ving planet carries into Phase 1 (planetary cooling, atmosphere
         - formation, prebiotic chemistry).                              
       3 +**Status:** ready for implementation                           
       4 +**Milestone:** M5 in `docs/specs/epic-phase0-physics-honesty.md
         +`                                                              
       5 +**Goal:** Define when Phase 0 ends and what information a survi
         +ving planet carries into Phase 1 (planetary cooling, atmosphere
         + formation, prebiotic chemistry).                              
       6  **Principle:** Phase 0 does not generate life; it produces phys
          ically grounded planet candidates from which life can plausibly
           emerge. The handoff is a data contract, not a cinematic.
       7  
       8 +> **Decision (2026-07-06):** The full `:planet-candidate` recor
         +d (§5) is **built now** as Phase 0's canonical *output*, even t
         +hough no Phase-1 consumer exists yet. It is fed by material cla
         +ss (M3/M5), thermal band (M5), and the volatile budget (M4). On
         +ly the downstream **consumer** is capability-gated (**D-consume
         +r**, triggered when Phase 1 begins). Building the contract now 
         +makes Phase 0's deliverable concrete and testable rather than a
         + vague scalar.                                                 
       9 +                                                               
      10 +> **Model update:** references to `domain.phase0` are stale — p
         +hysics is `domain.genesis`, narrative is `domain.arc` (see `gen
         +esis-arc-separation.md`). Composition is the element-resolved m
         +ap; "metals + silicates" below means the derived `:metal`+`:roc
         +k` categories from `domain.chemistry/bulk-categories`, not a st
         +ored `:metals` key. The interim handoff that exists today is th
         +e scalar path (`arc/ready-to-narrow?` + `habitability/habitable
         +-worlds`); M5 replaces it with the structured record.          
      11 +                                                               
      12  ---
      13  
      14  ## 1. Background from notes

● Update(docs/specs/phase0-habitability-handoff.md)
  ⎿  Added 2 lines, removed 1 line
      188  - `sterile-ending-does-not-emit-handoff`: `:sterile` or `:disp
           ersal` endings produce no candidate records.
      189  
      190  **Implementation:**
      191 -- Add `domain.phase0/handoff-system` that runs after `classify
          --system`.                                                     
      191 +- Add `domain.genesis/handoff-system` (not `domain.phase0` — r
          +enamed) that runs after `classify-system`, wired into the para
          +llel tick as a fan-out emitter (one writer for the handoff/can
          +didate components).                                           
      192  - Update `world-ending` to distinguish `:success` (handoff emi
           tted) from `:sterile`/`:dispersal`/`:fadeout`.
      193 +- **Orbit-stability (§3.3):** start with an analytic proxy (pe
          +riapsis vs. star radius, apoapsis bound, Hill-radius separatio
          +n) rather than a full 10-Myr two-body integration; the integra
          +tion is a refinement gated on the proxy proving too coarse. Th
          +is keeps M5 affordable inside the fixed-60Hz tick.            
      194  
      195  ---
      196  

● Now the small-gap task cards. Let me get a timestamp and create them.

  Ran 1 shell command (ctrl+o to expand)

● Write(kanban/tasks/render-material-color-element-keys.md)
  ⎿  Wrote 20 lines to kanban/tasks/render-material-color-element-keys.md
       1 ---
       2 uuid: "render-material-color-element-keys"
       3 title: "Render material color reads retired composition keys (li
         ve bug)"
       4 status: "todo"
       6 labels: ["fix", "phase0", "render", "chemistry"]
       7 created_at: "2026-07-06T16:21:51.000000000Z"
       8 source: "docs/specs/nebular-chemistry-realspec.md"
       9 category: "fix"
      10 ---
     … +20 lines (ctrl+o to expand)

● Write(kanban/tasks/disk-regime-tag-unification.md)
  ⎿  Wrote 24 lines to kanban/tasks/disk-regime-tag-unification.md
       1 ---
       2 uuid: "disk-regime-tag-unification"
       3 title: "Unify divergent disk-regime tag vocabularies"
       4 status: "todo"
       5 priority: "P1"
       7 created_at: "2026-07-06T16:21:51.000000000Z"
       8 source: "docs/specs/protoplanetary-disk-planet-formation-realspe
         c.md"
       9 category: "fix"
      10 ---
     … +24 lines (ctrl+o to expand)

● Write(kanban/tasks/law-planet-formation-namespace.md)
  ⎿  Wrote 20 lines to kanban/tasks/law-planet-formation-namespace.md
       1 ---
       2 uuid: "law-planet-formation-namespace"
       3 title: "Create law/planet_formation.clj; relocate constants out 
         of domain"
       4 status: "todo"
       6 labels: ["fix", "phase0", "chemistry"]
       7 created_at: "2026-07-06T16:21:51.000000000Z"
       8 source: "docs/specs/core-accretion-physics-realspec.md"
       9 category: "fix"
      10 ---
     … +24 lines (ctrl+o to expand)

● Write(kanban/tasks/gammie-alpha-beta-coupling.md)
  ⎿  Wrote 20 lines to kanban/tasks/gammie-alpha-beta-coupling.md
       1 ---
       2 uuid: "gammie-alpha-beta-coupling"
       3 title: "Derive disk viscous alpha from cooling beta (Gammie stea
         dy state)"
       4 status: "todo"
       6 labels: ["fix", "phase0", "chemistry"]
       7 created_at: "2026-07-06T16:21:51.000000000Z"
       8 source: "docs/specs/core-accretion-physics-realspec.md"
       9 category: "fix"
      10 ---
     … +22 lines (ctrl+o to expand)

● Write(kanban/tasks/ecology-water-gate-snowline.md)
  ⎿  Wrote 20 lines to kanban/tasks/ecology-water-gate-snowline.md
       1 ---
       2 uuid: "ecology-water-gate-snowline"
       3 title: "Ecology water/habitability gate is trivially satisfied"
       4 status: "todo"
       5 priority: "P2"
       6 labels: ["fix", "phase0", "chemistry", "handoff"]
       7 created_at: "2026-07-06T16:21:51.000000000Z"
       8 source: "docs/specs/phase0-habitability-handoff.md"
       9 category: "fix"
      10 ---
     … +22 lines (ctrl+o to expand)

● Task cards written. Let me check how the spec-task generator behaves before
  deciding whether to run it (status changes should propagate to the board).

  Ran 1 shell command (ctrl+o to expand)

● The generator overwrites all spec cards from scratch (and would reset the 2
  manually-curated in_progress cards). I'll run its exact logic but scoped to
  only the specs I authored/changed, so curated state is untouched. Let me write
  a scoped variant.
● Write(/tmp/claude-1000/-home-err-spaces-Truth/6acadd8f-ee31-4957-9317-ca96deb4
       d2e4/scratchpad/gen-cards.clj)
  ⎿  Wrote 92 lines (ctrl+o to expand)

  Ran 1 shell command (ctrl+o to expand)

● 6 spec cards generated. Now bump the 2 existing cards (differentiation,
  habitability) to ready to match their revised specs:

  Ran 1 shell command (ctrl+o to expand)

● Update(kanban/tasks/phase-0-chemistry-differentiation-spec.md)
  ⎿  Added 1 line, removed 1 line
      1  ---
      2  uuid: "phase-0-chemistry-differentiation-spec"
      3  title: "Phase 0 Chemistry & Differentiation Spec"
      4 -status: "todo"                                                  
      4 +status: "ready"                                                 
      5  priority: "P1"
      6  labels: ["specs", "phase0", "em", "chemistry"]
      7  created_at: "2026-07-02T19:35:28.966686229Z"

● Update(kanban/tasks/phase-0-habitability-handoff-spec.md)
  ⎿  Added 1 line, removed 1 line
      1  ---
      2  uuid: "phase-0-habitability-handoff-spec"
      3  title: "Phase 0 Habitability Handoff Spec"
      4 -status: "todo"                                                  
      4 +status: "ready"                                                 
      5  priority: "P1"
      6  labels: ["specs", "phase0", "handoff"]
      7  created_at: "2026-07-02T19:35:28.967130946Z"

● Let me verify cross-references resolve and nothing dangles.

  Ran 1 shell command (ctrl+o to expand)

● All cross-references resolve; cards generated. Let me record the receipt for
  this work.

  Ran 1 shell command (ctrl+o to expand)

● Done. Everything is written, cross-referenced, and the board reflects it.
  Here's what I produced — all documentation/planning, no source changes (your
  mandate was specs + roadmap + tasks).

  The roadmap spine

  docs/specs/epic-phase0-physics-honesty.md — rewritten from a flat epic into a
  sequenced roadmap:
  - M0→M6 milestones in dependency order, each with entry precondition,
  exit/verification, and the exact gap findings it closes. The chain is: metals
  exist (M1) → condensation & consumers (M2) → accretion physics (M3) →
  differentiation & collisions (M4) → habitability contract (M5) → rich
  inspector (M6).
  - A deferred-capability register (D1–D10 + D-consumer) — this is the part that
  answers your "conditionless deferrals are smelly" point directly. Every
  deferred item now has a precondition and a trigger phrased as an observable
  (in a standard run or test), never a date. E.g. migration (D2) activates when
  the radial-disk profile lands and a seeded planet coexists with a live disk;
  the radial profile itself (D1) activates when the single-annulus Toomre-Q
  provably misclassifies a radially-unstable disk.

  New specs (significant gaps)

  - metal-enrichment-and-seeding-realspec.md — the root fix.
  :genesis/metallicity knob (Pop-I default), explicit composition on every
  spawn/packet, SN enrichment held as live-uncalled mechanism behind D4.
  - core-accretion-physics-realspec.md — accretion-derived planet mass (M_crit +
  viscous runaway gas), sub-grid streaming-instability timescale,
  condensation-sequence composition, Gammie α(β).
  - radial-disk-structure-realspec.md — the D1 capability spec (status:
  deferred), the linchpin that unblocks D2/D3/D6.

  Revised specs

  - nebular-chemistry-realspec.md — sigmoid condensation, a Consumers §6.5
  section (the render-color bug + planet-seeding), all open questions resolved
  with gates.
  - protoplanetary-disk-planet-formation-realspec.md — scoped to disk-side,
  points at the M3 specs, all 7 open questions resolved.
  - phase0-chemistry-differentiation.md & phase0-habitability-handoff.md —
  realigned to the element-resolved model (dropped the retired :metals lump),
  stale domain.phase0 refs fixed, bounce/graze gated (D7), full
  :planet-candidate contract confirmed as build-now.

  Tasks

  5 small-gap cards (render-material-color-element-keys,
  disk-regime-tag-unification, law-planet-formation-namespace,
  gammie-alpha-beta-coupling, ecology-water-gate-snowline) + 6 spec-tracking
  cards generated via a scoped run of your generator so the 2 manually-curated
  in_progress cards were left untouched.

  Every open question from both realspecs and both research docs is now resolved
  — either built into a milestone or deferred with a trigger.

  Two things worth your call before I go further:
  1. The render color bug (M2) is live — bodies render uniform gas-tan today.
  It's a self-contained ~10-line fix. Want me to do that one now, independent of
  the rest?
  2. M1 (metals exist) is the highest-leverage next step and unblocks the whole
  chain. Want me to draft the failing tests + implementation, or leave
  everything at the spec stage for now?

✻ Baked for 15m 34s

