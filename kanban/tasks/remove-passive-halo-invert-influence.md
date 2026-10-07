---
description: "Current implementation scope: docs/designs/player-active-influence.md, admitted at reviewed planning e77b325 on 2026-10-07, 5 points. Grounded integration, Done when, Dependencies and earlier proposal notices below are preserved superseded history. The current admission comment controls scope and pending verification; no completed gameplay qualification is claimed."
category: "specs"
labels: ["domain", "physics", "player", "spark-redesign"]
write-id: "1791408238198-0.t3r8k3689dr9u7ptal8"
points: "5"
source: "kanban/tasks/remove-passive-halo-invert-influence.md"
title: "Invert influence: remove the passive spark halo, strengthen paid wells"
priority: "P1"
status: "in_progress"
estimate: "5"
design: "docs/designs/player-active-influence.md"
uuid: "remove-passive-halo-invert-influence"
created_at: "2026-07-23T00:00:00Z"
---

# Invert influence: remove the passive spark halo, strengthen paid wells

> Spark-redesign card 2 of 4 (owner decision 2026-07-23). By default the SYSTEM
> moves the spark more than the spark moves the system — without spending quanta.
> The spark's passive world-pull goes away; the player's paid gravity-well
> abilities get stronger to compensate.

## Grounded integration (from design investigation, cite file:line)
**Remove the passive halo (clean single-writer/single-consumer channel):**
- Delete `observer-acceleration-system`, `halo-mass`, `default-halo-mass-factor`
  from `src/domain/player/influence.clj`.
- Delete the `:observer-accel` registry entry (`src/domain/ecs/registry.clj:182-185`)
  and its emitter call in `src/domain/genesis/systems.clj:43`.
- Remove `c/accel-observer` from the integrator accumulate vector
  (`src/domain/integrator/base.clj:16-18`) and the `:integrator` `:reads`
  (`registry.clj:169`). Grep confirms nothing else reads `c/accel-observer`.

**Also remove the now-superseded spring binding** (gravity replaces it — but only
once card 4 makes the spark a real body; if card 4 hasn't landed, KEEP the spring
until it does so the spark isn't stranded — coordinate ordering, see deps):
- `domain.narrowing/spark-binding-step` + `observer-motion-step`
  (`src/domain/narrowing.clj:301-342`) and their call site
  (`src/infra/dev/window/loop.clj:309-327`).

**Strengthen paid wells (pure constant tuning):**
- `domain.intervention/warp-acceleration-system` is the template
  (`src/domain/intervention.clj:112-148`) — sole writer of `c/accel-warp`, same
  `influence-reference`/`halo-reach-factor` the old halo used.
- Raise `default-well-mass-factor` (`intervention.clj:51-56`, now 0.5) and tune
  `action-cost` (`intervention.clj:32-35`) / radius / ttl so a placed well is the
  player's real lever. Optionally scale with formation-progress or resonance.

## Done when (player-visible)
- The spark no longer pulls distant bodies toward itself just by being looked at.
- Placing a `:warp/well` still gathers matter, now at higher potency.
- `clojure -M:test` + architecture-test + `bin/analyze --strict` green;
  `write-conflicts {}`.

## Dependencies
Card 1 (dark-matter halo) must be LIVE — removing the passive halo without a
replacement binding force strands the early-collapse nebula. Spring removal is
gated on card 4 (don't strand the spark). Well-strengthening is independent.

---

2026-10-07 design refinement only; Todo2 is historical authored state, not proof of current Ready admission. Source/history recovery found no chosen post-redesign paid-well compensation. Write a bounded research-backed design preserving BOTH owner outcomes: remove passive focus-centered force and strengthen deliberate paid fields. Proposed experiment selects default mass factor0.5->1.0, retains15 agency cost,4e15m Plummer scale,600tick TTL, current falloff/decay/cap/lifecycle, and explicitly includes repulsor symmetry. This is a proposed toy gameplay default for review and later measurement, not empirically calibrated balance or an always-double acceleration claim. Enumerate current emitter/registry/integrator/SoA/orbital/facade/bootstrap/menu/readout removals; preserve physical Spark gravity, dark-matter field and shared paid influence helpers. Acceptance requires actual pipeline absence of passive force under focus changes, stronger uncapped matched-input paid effect with cap/payment/no-funds/expiry invariants, and later ordinary G gathering evidence. Correct stale scope and conservation/ejection claims in the design rather than silently narrowing the task. No production code, new feature admission, state transition, tuning runtime or Done claim. Detailed implementation size remains to be assessed against full scope after design review; owner body/state/estimate remain unchanged here.

2026-10-07 planning-only design closure: docs/designs/player-active-influence.md (SHA256 d7f4489cb0620b212dbb01e24dd1cbe33a2cf9c26e9ec6d54dbcd727adae67c1) links the recovered July5 field investigation, July23 owner decision and current production contracts. Proposal retains BOTH passive-force removal and stronger paid fields; selects shared well/repulsor default factor0.5 to1.0 with15 agency,4e15m scale and600 simulation ticks unchanged. Doubled uncapped matched-input force is distinct from saturation or observed trajectory/calibration. Full retirement includes current predictor/orbital consumers, facade/bootstrap/menu/readout; shared paid helpers, physical Spark gravity and dark matter stay. Natural earned G gathering, payment/lifecycle/cap controls and normal gates remain future acceptance. Full implementation is proposed5 points because the historical2 understates complete scope; no estimate/status/frontmatter admission is changed here. Root and independent intent_research source/design review found no planning blocker; command spelling was clarified to clojure -M:demo serve. Source/history audit verifies28 pinned baseline files and23 local links/fragments. No production/test edits, JVM/native run, empirical tuning, completed acceptance or implementation approval. The five-line flight-design link qualifies historical passive-halo wording. Prior body, comments and event history are preserved.

2026-10-07 review5439081018/comment4204274023 fixed through canonical descriptive frontmatter: the top-of-card notice expressly marks Grounded integration, Done when, and Dependencies as superseded historical scope, points to docs/designs/player-active-influence.md for proposed scope/qualification, and preserves Todo pending normal admission. Existing body remains provenance and must not be used as current implementation instructions. PROCESS.md freezes bodies after breakdown and directs later updates through Rheos; no manual body rewrite or status/estimate change. Design status also makes this precedence explicit. This is clarification only, not acceptance or implementation approval.

2026-10-07 root accepts current Ready criteria on reviewed planning e77b3255212945602b96e1451347cbf9bc966676: canonical gatePASS7checks,0unresolved,CodeRabbit+MiMo exact-head approvals,1planningcohort. Shared Rheos PR6 fb202a9 passes its own fullcode gate; exact qualified CLI4af36dce consumed from originalworktree with dependencies, not copied/merged/installed. Rootrelease25286aa1 and independentconsumptionaudit establish capability. Native canonical frontmatter now points5 and existingestimate5 plus design=docs/designs/player-active-influence.md; points introduced for planning/UI discoverability, synchronizedestimate avoids historical2, not an engine sizingrequirement. Admit COMPLETE five-point scope: retire all passive attention-halo consumers, raise paid well/repulsor default0.5->1, preserve15agency/4e15m/600ticks/sharedPlummer/physicalgravity/darkmatter/lifecycle. RED beforeGREEN; actualpipeline,matchednumerics,economics,menu,fullsuite/6strict,cost and ordinaryearnedG visiblegathering remain required. Historical body/proposalcomments preserve provenance and never override revieweddesign. LegacyTodo authored directly has no historicalReadyproof; explicit currentcriteria acceptance precedes legalWIPTodo->InProgress. No fabricatedReady event, force-status or localboardparser. No JVM/native release: currentnativeexec69944 owns lane; no empiricalbalance/Gate/Done claim.

---