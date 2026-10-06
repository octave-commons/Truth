# Two seeded natural formation runs through 12,000 ticks

Both runs completed normally at the agreed tick horizon, with exit code 0 and
empty stderr. Across the 48 actual planet-formation events, every exact birth
snapshot had a production-classified bound parent at less than 25 AU, below
`formation-placement-v2`'s 100 AU acceptance limit. At least 19 bodies in each
run remained bound at every recorded post-birth sample. This is sampled survival
over the tested horizon, not continuous orbital certification or native manual
fly → commit → sculpt acceptance.

| Observation | Seed 43 | Seed 42 |
| --- | --- | --- |
| Supplied world options | `{:seed 43 :gas-count 1000}` | `{:seed 42 :gas-count 1000}` |
| Exact formation events | 24 at tick 4,102 | 12 at tick 4,038; 12 at tick 4,907 |
| Bound parents at birth | 24 / 24 | 24 / 24 |
| Birth parent-distance range | 0.130426056670–24.325335979755 AU | 0.130426056667–24.325335979751 AU |
| Formed bodies still `:planet` at tick 12,000 | 24 / 24 | 24 / 24 |
| Currently bound at tick 12,000 | 19 | 20 |
| Bound at every recorded post-birth sample | 19 across 93 samples each | 20; first cohort has 98 samples, second has 84 |
| Currently eligible at the final snapshot | 1 | 0 |
| Stored candidate components at the final snapshot | 4 | 6 |
| Life-emergence events | 2 at tick 11,328, bodies 1012 and 1024 | 1 at tick 11,280, body 1012 |
| Final arc | `:arc/life-emergence` | `:arc/life-emergence` |
| Wall-clock duration | 2,908,342 ms | 2,535,999 ms |
| Stop reason | `:tick-budget` | `:tick-budget` |

The life events carry `{:from :prebiotic :to :prokaryotic}`. These are observed
production events, not proof of later biosphere, character, or Gate mechanics.
Stored candidates are persistent components and must not be substituted for
current eligibility. Earlier transient gas-giant states without formation events
are excluded from all formation counts above.

Both simulations use the implementation at
`81207e7c65212685456ebe0faff3cac1cec50613`. Seed 43 loaded at that exact HEAD.
Seed 42 launched at `cfd11fb512f72517ece7a0c86a6f8f8c8f48e180` after verifying
tracked `src` and `deps.edn` were identical to the pinned implementation. The
loaded processes were never reloaded as the checkout changed. Each starts with
`genesis/create-world` and advances only `arc/tick-genesis`; no forced formation,
body injection, phase mutation, or physics tuning occurs. The supplied options
and actual initial world parameters are retained in the first raw record.

The fixed budget is 12,000 ticks or 10,800,000 ms, with a 4 GiB heap cap.
Observations occur every 100 ticks and whenever the runner sees a new planet-like
state, departure from that state set, a new ledger event, or a stop. Exact birth
rows therefore coincide with their formation event ticks. Later binding is
sampled, not checked for continuity between samples. These concurrent operational
runs are not performance benchmarks. The earlier native seed 42 trajectory is
separate UI evidence and is not substituted for this headless run.

The raw logs retain substep-clamp warnings: 47,388 text markers in seed 43 and
45,165 in seed 42. The pinned integrator's ceiling is 4,096 substeps. Parallel
logging interleaves numeric fields, so no parsed maximum demand or per-body
warning count is claimed. The serial EDN observation streams parsed completely;
all listed closed-artifact checksums verified.
The console logs contain trailing whitespace from concurrent output. Their
committed `runner.log.gz` archives use deterministic `gzip -n -c`; each directory's
`runner-log-provenance.edn` records compressed and decoded hashes and confirms
decoded bytes equal the retained, untracked `runner.log`. The raw observation
streams and event records were not rewritten.

Evidence:

- [Seed 43 final summary](../../.ημ/diagnostics/playable-foundation/natural-seed43/final-summary.edn),
  [completion record](../../.ημ/diagnostics/playable-foundation/natural-seed43/completion.edn),
  [raw observations](../../.ημ/diagnostics/playable-foundation/natural-seed43/observations.edn),
  [checksums](../../.ημ/diagnostics/playable-foundation/natural-seed43/SHA256SUMS).
- [Seed 42 final summary](../../.ημ/diagnostics/playable-foundation/natural-seed42/final-summary.edn),
  [completion record](../../.ημ/diagnostics/playable-foundation/natural-seed42/completion.edn),
  [raw observations](../../.ημ/diagnostics/playable-foundation/natural-seed42/observations.edn),
  [invocation and semantics](../../.ημ/diagnostics/playable-foundation/natural-seed42/README.md),
  [checksums](../../.ημ/diagnostics/playable-foundation/natural-seed42/SHA256SUMS).
- [Shared diagnostic runner](../../.ημ/diagnostics/playable-foundation/natural-formation-observation/observe-natural.clj)
  and [EDN summarizer](../../.ημ/diagnostics/playable-foundation/natural-formation-observation/summarize-natural.clj).
  They call or summarize existing production predicates; they introduce no
  classifier or board implementation.

The formation card remains in progress pending fresh canonical review gates on
the selected source revision. Native manual approach, commitment, rendered voxel
resolution, and sculpt verification remain separate open work.
