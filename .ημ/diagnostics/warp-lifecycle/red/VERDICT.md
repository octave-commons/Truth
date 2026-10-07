# Warp lifecycle RED checkpoint

Source HEAD: `9c5c889d8923c76bf9a9530a8bd18cd72f37a93f`.
Production `git diff -- src` is empty. Existing owner
`fix-warp-disabled-stale-write` is In Progress, one point, canonically classified
as the mechanical restoration of an existing contribution-lifetime contract.

Corrected focused command (256 MiB initial, 2 GiB maximum heap):
`clojure -J-Xms256m -J-Xmx2g -M:test -n domain.warp-lifecycle-test -n domain.intervention-test`
completed 2026-10-07T00:14:39.275635Z, exit 1. PID 206714 was reaped.
**27 tests, 195 assertions, 32 failures, 0 errors.**

The three new tests exercise paid `intervention/place`, real warp emission,
`tick/run-parallel` and the actual physical integrator. Both ECS-map and fresh
SoA paths run. The eight expiry/removal cases fail stored-channel removal,
archetype removal and subsequent ghost-kick assertions (24 failures). The four
partial departure cases fail departed-channel removal and subsequent motion
(8 failures), while another recipient remains under the live well/repulsor.
Valid active force, unchanged payment, finite decay, independent gravity,
physical departure and the legitimate final one-tick Jacobi kick all pass.
The 24 pre-existing intervention tests pass. No missing function or runner error
supplies RED. This isolated deterministic fixture is not native gameplay proof.

The first run is preserved in `attempt1/`: 27 tests, 195 assertions, 46 failures,
0 errors, exit 1, PID 199085 reaped at 00:13:15.830213Z. It exposed the intended
failures plus a fixture omission: the map integrator requires `c/body-kind`,
whereas SoA admission uses position, velocity, mass and radius. Adding only the
missing `:gas` body-kind eliminated the 14 invalid fixture/control failures.
Original source bytes match the attempt1 command/result hash; no failed evidence
was discarded. The corrected test is SHA256
`2904ab8158f595ab22bf0638df0100784c9acb94989b42a659d13efbf0153b0c`.
Corrected raw log is SHA256
`855e489d1f273ce415dfd10ced48b265f563834bba338d388b4d5d85b17093cd`.

Independent source review by board_scout found no blocker; root separately read
and accepted the current tests. Neither review substitutes for hosted approval.
Touched clj-kondo reports zero errors/warnings and ordinary diff-check passes.
No full suite, all-six strict, formatting gate, benchmark, native test or GREEN
claim is made. All source hashes in each execution manifest are stable across
that execution. Root owns RED commit and GREEN grant.

Read-only collateral audit (no added fixes):
- `src/domain/intervention.clj:255`: thermal-intervention empty branch emits
  `{c/heat-intervention {}}`; active branch uses contribution-write-set. It can
  retain prior thermal contributions when all thermal interventions disappear.
- `src/domain/player/influence.clj:24`: missing observer or nonpositive halo
  factor returns `{c/accel-observer {}}`, retaining carried entries.
- `src/domain/player/flight.clj:107`: missing observer returns an empty thrust
  column; stale cells can remain if observer-component removal leaves the
  physical entity alive. Despawning an entity is a separate cleanup path.
- `src/domain/stellar/disc.clj:83`: no-central-star branch emits empty disc tags;
  this is a classification lifecycle rather than a force channel and is outside
  this repair. These are source findings, not fresh runtime reproductions.

GREEN must declare the new prior-column `c/accel-warp` read. Current
`predicted-position-fn` reads cached prediction arrays or `c/position`; its
fallback does not directly read `c/velocity`. The SoA builder owns the velocity
read that produced those arrays. Preserve that distinction when reviewing the
registry; do not change unrelated declarations here.
