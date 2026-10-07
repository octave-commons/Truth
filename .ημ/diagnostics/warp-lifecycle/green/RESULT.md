# Warp lifecycle GREEN qualification

The production change restores the existing one-point lifecycle contract under
`fix-warp-disabled-stale-write`. Both warp branches now use the existing
`tick/contribution-write-set` with the prior warp recipients, wrapped in the
old explicit empty-column result shape. Missing recipients receive the existing
removal sentinel; the fold removes their component and archetype membership.
The registry adds only the required `c/accel-warp` self-read. Force arithmetic,
iteration order, predicted positions, cap, paid placement, expiry policy and
other component owners are unchanged. The frozen integrator still applies the
legitimate final prior-channel Jacobi kick.

Final production SHA256:
- `src/domain/intervention.clj`:
  `eed4697f04bf96ce8bdabc0e08b64c63bd296a0d9cc958b49574bbef643c74a8`.
- `src/domain/ecs/registry.clj`:
  `eb5df3121fac4eb1c2e4bb1cb94b5e13984409938e3dffa3b4a6ad59a292a3dc`.

## Actual qualification sequence

| Run | Outcome | Source boundary |
| --- | --- | --- |
| Focused attempt 1 | 36 tests, 225 assertions, 1 failure, 0 errors | Initial helper-only change omitted the old empty-column shape; all new lifecycle cases passed |
| Focused attempt 2 | 36 tests, 225 assertions, 0 failures/errors | Final production bytes; original RED test bytes |
| Full ordinary suite | 948 tests, 16,775 assertions, 0 failures/errors | Same final production and original RED test bytes |
| All-six strict attempt 1 | Exit 1, only cljfmt blocked | Three indentation lines in the new regression test; all other stages passed |
| Focused attempt 3 | 36 tests, 225 assertions, 0 failures/errors | Final production and corrected test formatting |
| All-six strict attempt 2 | Exit 0, all six stages passed | Final production and corrected test formatting |

Each command, captured source diff, before/after source hashes, process ID,
completion time, log hash and exit is recorded beside this report. All owned
qualification processes were reaped. Heap was bounded to 256 MiB initial /
2 GiB maximum; the parent-owned native process could overlap these correctness
runs. Their durations do not support isolated performance claims.

The initial focused failure is retained in `focused.*` and `attempt1/`; it
caught output-shape compatibility in an existing test, not a failure of the new
removal tests. The minimal merge preserves that existing shape in both branches
without changing the helper or tests. Independent source review covered both
the original removal change and the final compatibility correction and found
no blocker. This is local review, not hosted approval.

The first strict failure and the targeted formatter diagnostic remain intact.
With explicit parent authorization, only one leading space was added on live
test lines 39, 110 and 111. `preformat-warp_lifecycle_test.clj` retains original
SHA `2904ab8158f595ab22bf0638df0100784c9acb94989b42a659d13efbf0153b0c`.
`format-correction.json` records the new test SHA
`3c15b3fae0194f0d413a347224d6e7f835af90825c16781a699859bb472393ad`
and exact reverse-byte proof; `format-forms.log` proves equal parsed forms.
Frozen RED artifacts, input snapshots and the benchmark adapter were not changed.
The prior full run is accurately bound to its original test bytes; the later
canonical gate on the committed final source is a separate result, not claimed
by this report.

## Boundaries

The separately checkpointed BEFORE evidence remains immutable. Matched AFTER
ran separately with those same frozen inputs after the parent's verified native
pause; its own `../after/RESULT.md` records an unresolved broad tick slowdown. No native
input, force/cost/clock redesign, other-emitter fix, board transition, hosted
approval or completed ordinary gameplay route is claimed. The earlier
read-only collateral-emitter audit remains outside this repair.
