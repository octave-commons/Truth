# Open Questions — next-steps exploration (2026-07-24)

Live human-readable view over `ledger/questions.edn` (append-only, numbered) and
`ledger/answers.edn` (numbered, references questions via `:answers-q`). The EDN
files are source of truth; this doc is the dashboard.

## Situation snapshot
- **Branch:** `spark-gravity-bound-body`. HEAD = `cbe80dd` (formation-survival trio,
  committed 2026-07-24 after suite-green verification — Q1/A2 resolved).
- Previous HEAD `b0b3b4a` (spark card 4). The trio (80 files, +7259/-588) is now the
  clean verified base + rollback point. **Suite green** (871 tests / 15457 assert / 0 fail — Q5/A1).
- **Cards in `review`:** `universal-compact-substepping`, `formation-placement-v2`,
  `sink-absorb-angular-momentum-renormalization`.
- **Upstream blocker:** `planet-orbit-circularization-blocker` (todo) — no
  `c/planet-candidate` ever emerges → binding/commitment/voxel loop unreachable.
- **Physics trend:** planet survival 1/25, up from 0/∞. 1022 born bound at 558 AU,
  circularizes (e 0.137→0.053). 1024 born bound at 32 AU, stripped in <500 ticks.

## Questions
| # | audience | status | question |
|---|----------|--------|----------|
| 1 | user | ANSWERED (A2) | Commit dirty trio first? → Yes; committed cbe80dd. |
| 2 | user | ANSWERED (A3) | Priority thread? → (a) direct-merge L renormalization. |
| 3 | user | ANSWERED (A4) | Subagent autonomy? → Investigate + implement WITH review; must ask questions relentlessly. |
| 4 | user | ANSWERED (A5) | North star? → Yes, push for candidate emergence. |
| 5 | exploration | ANSWERED (A1) | Does the suite pass with the uncommitted diff? → Yes. |
| 6 | exploration | ANSWERED (A101) | Direct-merge L → disk-radius leak? → NO. Sink fix (cbe80dd) is complete & correctly scoped. |
| 7 | exploration | QUEUED | 1024 velocity/energy timeline birth→strip; close-encounter degradation or real tide? → after A returns. |
| 100 | exploration | ANSWERED (A101) | Merge-route raw L leak into disk-radius? → NO (separate c/spin component). |
| 102 (note) | exploration | SUPERSEDED | A's suspect: disc-seeder planetesimal path → REFUTED by probe (bodies are :planet, disc-tag nil). |
| 103 | exploration | ANSWERED (A6) | Is disc-seeder the kAU-birth mechanism? → NO (probe /tmp/probe3.out). |
| 104 | user | LIKELY MOOT | Add disc-classify proximity gate? → probably N/A; kAU bodies aren't disc-tagged. |
| 8 | exploration | IN PROGRESS | Real spawn path of :planet-state, disc-tag-nil, kAU bodies at t~4100? → subagent A redirected. |

## In flight
- **Subagent A** (sonnet, medium): PIVOTED. Original thread (a) refuted (A101).
  Now tracing the real emitter of :planet-state kAU bodies (Q8): disc-evolution
  GI/binary fragmentation vs planet_formation core-accretion vs reclassification.
  Instrumenting spawn sites + re-probing seed 42 → tick 4100. Reserved IDs 100–199.

## Key pivot (2026-07-24)
Thread (a) as the user chose it (direct-merge L renorm) is **refuted as the kAU
mechanism** — the sink fix is already complete. Evidence: probe shows the 12
kAU-birth bodies are `:planet`/`disc-tag nil`/`dominant-attractor nil`, so neither
the sink absorb channel nor the disc-condensation seeder placed them. The real
emitter is still open (Q8). Goal (candidate emergence) unchanged; means shifted.

## PIVOT 2 (2026-07-24 ~13:20): it's a fling, not a placement bug
probe6 (tick 4040): eids 1001-1012 at 24.8-58.4 AU (correct disk-scale birth).
probe4 (tick 4100): same eids at 1897-71999 AU, 7-42 km/s (escape 3.76 km/s).
=> POST-BIRTH FLING within ~60 ticks. Placement + sink-L are fine. Real defect is
in the integrator/sub-stepper (universal-compact-substepping territory).
- Subagent A: DORMANT (stalled on finished probe; two refuted placement hypotheses).
- Subagent B: launched, scoped to integrator fling (Q9), reserved IDs 200-299,
  synchronous probes. Instrument kinematics-cell for eids 1001-1012, ticks 4030-4100.

## PIVOT 3 / ROOT CAUSE FOUND (2026-07-24 ~14:30, subagent B): spawn-seam stale anchor
NOT an integrator fling (A7/q9 reframed). tick.clj:169-172 `tick-physics` runs
step-physics (advances the star ~35 AU/tick in formation-era) BEFORE
materialize-lifecycle. planet-seeds specs bake ABSOLUTE position/velocity against
the star's PRE-tick position; by materialization the star has moved ~35 AU, so a
planet meant for a 0.13 AU orbit (correct ~43.7 km/s circular speed) is born 35 AU
from the real star → 43 km/s is now 11x escape → instant ejection. §3.0 stale-anchor
class reborn at the spawn seam the multi-timescale design never touched.

FIX (uncommitted, reviewed by orchestrator — clean, opt-in via :spawn-parent):
- orbit.clj build-planet-spec returns :spawn-parent/:rel-position/:rel-velocity.
- bootstrap.clj resolve-spawn-parent re-anchors on the parent's CURRENT state.
- test/domain/formation_test.clj regression: fails 6/6 without fix, passes with it.
- Ordering claim confirmed in tick.clj:169-172. Suite re-run in progress (orchestrator).
RESULT: seed-42 bound planets 0/12 → 6/12 at tick 4100 (card done-bar met).
RESIDUAL (q202, USER decision): inner 1001-1004 + outer 1011-1012 still flung —
likely genuine 4-star scattering (dominance ratio 1-60 < 100x). Pursue now or new card?
- Subagent B: DONE. Subagent A: dormant. IDs 200-299 used by B.

## Candidate-emergence check (2026-07-24 ~15:00, subagent C, IDs 300-399)
User chose: check candidate emergence (north star) over chasing residual scattering.
C tracing full gate (e<0.4 + orbit-stable + 150-400K temp) for the 6 survivors
(eids 1005-1010, 1.6-9.3 AU). Cheap-gate-first (temp), no blind long runs.
Hypothesis to confirm/refute: 0.28 Msun star's HZ ~0.1-0.2 AU => 1.6-9.3 AU
survivors too COLD => need inner planets => residual scattering becomes critical.

## Candidate check result (2026-07-24 ~16:00, orchestrator-run probe)
Gate table (seed 42, tick 4100, eids 1005-1010): all :planet, warm/hot (342-799K),
e 0.12-0.55. TEMPERATURE REFUTED as blocker (star 491 = 261 Lsun @ 0.28 Msun, an
over-luminosity bug that warms them; Q12 for the user). THE WALL: c/orbit-stable
false for ALL 6, incl 1010 (e=0.165, 342K temperate, apo 12.5 AU). 0 candidates.
orbit-stability = 3 analytic gates (peri floor, 100 AU apo cap, 10-Hill sibling
separation) vs each body's dominant-attractor. Gates 1&2 pass from the numbers;
Q11 probe running to decide: Hill-packing (6 planets in 1.6-9 AU) vs wrong/nil parent.
Subagents A/B/C all abandoned (stalled on background probes); orchestrator driving directly.

## MILESTONE (2026-07-24 ~17:40): first planet-candidate in sim history
Two root-cause fixes this session unblocked candidate emergence (seed 42):
- b7909fa spawn-seam stale-anchor: 0/12 -> 6/12 planets survive formation era.
- 3542b2c Hill-ejecta gate: exclude unbound bodies from orbit-stability sibling
  set -> all 6 orbit-stable -> eid 1010 eligible -> c/planet-candidate [1010] WRITTEN.
Suite 873/15468 green. The binding->commitment->voxel loop is now REACHABLE.

## Over-luminosity resolved: it's a deliberate toy (Q12/Q14, A15/A16)
star-luminosity is a toy clamp ([1e26,1e29]W, mass-independent, all stars ~261 Lsun),
NOT a bug. 1010's temperate 342K is a toy artifact. CLEAN single-function seam =>
LOW lock-in (downstream gates are physical K). Owner: toy OK as provisional, focus
gameplay loop now, don't lock in. Real model carded: stellar-mass-luminosity-real-model.md.
GUARD: key gameplay on the semantic gate (c/planet-candidate), not on "1010 is at 9 AU".

## Deferred / open follow-ups
- Residual scattering: 6/12 planets (inner 0.13-0.5 AU + outer) still eject.
  Becomes CRITICAL when real luminosity lands (true HZ ~0.1-0.2 AU = inner planets).
- Real stellar M-L model (carded, research-first, deferred behind gameplay loop).
- disc.clj / regime.clj still use most-massive central-star (A102) — real, low-pri.

## Subagent note
All 3 sonnet subagents stalled by backgrounding multi-minute genesis probes;
orchestrator drove the diagnosis directly with synchronous file-redirected probes.
Lesson: subagents on this repo must run genesis probes in FOREGROUND.

## NOW: gameplay loop focus (owner 2026-07-24)
Next: verify fly -> bind -> commit -> voxel end-to-end against candidate 1010.
Live pm2 window (gates-of-truth-dev) runs STALE code (pre b7909fa/3542b2c) — needs
restart or nREPL reload to run the new physics. (Restart = fresh nebula; confirm first.)
