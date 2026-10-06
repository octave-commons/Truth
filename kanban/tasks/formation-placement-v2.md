---
category: "specs"
labels: ["domain", "physics", "genesis", "multi-timescale", "blocker"]
write-id: "1791316600924-0.pchxzrj42gxh4q0jig"
source: "kanban/tasks/formation-placement-v2.md"
title: "Formation placement v2: no spawns at clump-scale radii (disk-scale gate + Hill-stable clamp)"
priority: "P1"
status: "review"
estimate: "3"
uuid: "formation-placement-v2"
created_at: "2026-07-23T00:00:00Z"
---

# Formation placement v2

> Formation-survival era, card 2. Research:
> `docs/research/physics/cluster-dispersal-integration-heating.md` §3.2.
> Live evidence: every FORMED event in the probe ejection log appears at
> 3,474–71,440 AU from the nearest stellar body, unbound from birth. Card
> `fragment-placement-decouple-dt` placed fragments at "physical disk radius"
> assuming r-disk ≈ 2–100 AU; the live r-disk at formation time is
> clump-collapse-fed and 10³–10⁵ AU.

## Grounded integration (cite file:line)
- `domain.stellar.disc-evolution` spawn sites (binary branch ~:180-200, GI
  branch ~:227-245): `r-orbit = max(0.5·|0.3·(max 1e10 r-disk-now), fragment-
  placement-floor-m)` — r-disk-now = `disc/disk-radius` from the disk's
  angular-momentum budget (`disc.clj`). When the disk is the whole rotating
  clump, this is kAU.
- Two independent guards (implement both, they fail differently):
  1. **Disk-scale gate:** fragmentation only fires when `r-disk` is in a
     plausible protostellar band (≲ 100 AU). A clump-scale "disk" is not a
     disk — it hasn't finished collapsing; spawning planets into it is the
     birth defect. Where does the over-large L come from? Investigate
     `domain.stellar.disc/disc-identification-system` (is it matching
     clump-scale rotation?) as part of this card — the gate is the fix, the
     identification audit is the understanding.
  2. **Hill-stable clamp:** cap `r-orbit` at the radius where the host's
     pull still dominates the local tidal field (the same dominance ratio
     the integrator uses, 100×), so even a passing disk spawns into a
     survivable orbit. Below the existing 0.3 AU floor, above nothing.
- Velocity assignment stays as-is (Newtonian-circular, the pairing rule).

## Done when (player-visible via live pm2 window)
- Probe (`scratchpad/cluster_probe.clj` style): every FORMED event appears
  ≤ 100 AU from its host star (no kAU births), across ≥2 seeded runs.
- Combined with `universal-compact-substepping`: ≥1 planet stays bound
  through the formation era.
- `bin/test` green; new test pins the gate + clamp at the spawn sites.

## Risks
The disk-scale gate may simply DELAY fragmentation (disks compactify as
collapse proceeds) — verify planets still form at all. The Hill clamp can
push spawns below the 0.3 AU floor in tight tides — resolve by skipping the
spawn this tick (retry later), never by violating the floor. Interacts with
binary-formation tuning (companions want wider orbits than planets — the
gate band may differ per branch; document the choice).

## Dependencies
`fragment-placement-decouple-dt` (landed). Sibling of
`universal-compact-substepping` — either unblocks candidates only together.

---
Review reopened 2026-10-06 for two executable materialization defects found by runtime_scout and accepted for repair by root. Existing acceptance requires every FORMED event at <=100 AU from its host; a GI request planned at 2.999879 AU materialized at 999.550877 AU (unbound) when its host advanced 1000 AU before materialization, because GI and binary packets omit :spawn-parent/:rel-position/:rel-velocity. Separately, a core parent-relative seed under 10 AU :genesis/frame-offset has exactly 10 AU relative-position error: current post-integrator parent resolution is followed by a second recenter. Scope remains estimate 3: uniform parent-relative packets for GI/binary and exactly-once recenter for resolved parent-relative materialization; preserve absolute-spawn behavior, existing physical disk/Hill gates and velocity pairing. Grounding: docs/designs/multi-timescale-integration.md sections 3.0 (current parent-relative composition) and 3.4 (uniform frame shift once), with existing cluster-dispersal research section 3.2. Historical scratchpad probe artifacts remain missing, so these are fresh executable seam reproductions, not a claim to have rerun old research. Runtime scout will preserve script/output under .ημ/diagnostics/playable-foundation and write red moving-parent/nonzero-recenter regression tests; root commits red before source changes. This slice does not certify the two-seed live planet survival acceptance or complete the parent blocker.

2026-10-06 completed natural formation evidence for the reopened placement review. Two untouched production runs from source 81207e7, actual create-world seeds 43 and 42 with 1000 gas parcels, each reached tick 12000 and exited 0 with empty stderr. Every actual planet-formation event had a production-bound parent in its exact birth snapshot: seed 43 has 24 births at tick 4102; seed 42 has 12 at 4038 and 12 at 4907. All 48 birth radii lie in 0.130426–24.325336 AU, below this card's 100 AU limit. At the final horizon both retain all 24 formed planets; seed 43 has 19 bound at all 93 recorded post-birth samples, and seed 42 has 20 bound at all recorded samples (98 for its first cohort, 84 for its second). These are periodic/event-sampled survivors, not proof of binding between samples. Current eligible counts are 1 and 0, distinct from 4 and 6 stored candidate components. Natural life-emergence events also occurred; neither run claims manual approach, commitment or sculpt. Evidence note: docs/notes/2026-10-06-two-seed-natural-formation.md. Raw observations, final summaries, completion records, deterministic console-log archives and verified checksums are under .ημ/diagnostics/playable-foundation/natural-seed43 and natural-seed42. Substep-clamp warnings remain visible; parallel console lines interleave, so no parsed maximum demand is claimed. No forced formation, injected bodies, tuning, hot reload, classifier duplication or performance benchmark. Card stays in_progress until root checkpoints evidence and runs fresh canonical review gates on that exact revision.
---