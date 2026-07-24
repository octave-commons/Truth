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
| 6 | exploration | IN PROGRESS | Where is the 'direct-merge packet channel' keeping raw L; is t~4100 kAU pop from it? → subagent A (IDs 100–199). |
| 7 | exploration | QUEUED | 1024 velocity/energy timeline birth→strip; close-encounter degradation or real tide? → after A returns. |

## In flight
- **Subagent A** (sonnet, medium): thread (a) direct-merge L renormalization —
  investigate merge-route L → disk-radius leak, implement fix with review, no commit.
  Reserved ledger ID block 100–199.
