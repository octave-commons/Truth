# Primordia replication under resource costs: source audit and blocked pilot

Date: 2026-10-23. Status: **draft; simulation evidence unavailable**.
Tracks [Truth #63](https://github.com/octave-commons/Truth/issues/63).
Upstream: [Chokyotager/Primordia](https://github.com/Chokyotager/Primordia),
frozen at `1c93703ae33b0016d48ca59d22a5703835bfe1e6`.
Truth base: `58b0fd91ea65b7a4b3a1f571f22f3d394374a871`.

## Answer and evidence boundary

**Whether unseeded Primordia worlds produce sustained replicating lineages under
energy and matter costs remains unknown in this investigation.** No simulation
completed, so emergence frequency, latency, survival probability and a cost
threshold are unmeasured. This is a runtime-blocked study, not an observed failure
to replicate. Simulation exposure is **0 epochs across 0 completed seeds**.

The reproducible contributions are a pinned source audit, a bounded acquisition
harness, operational classification criteria, and actual preflight records.
Seven Python integrity tests passed. Neither those tests nor the source audit
reproduce Primordia's scientific results.

A significant source finding changes how future output must be interpreted:
**the built-in replicator assay disables physics**. Passing a constrained config
to `assay` does not test replication under that config's resource constraints.

## Provenance and scope

The [source manifest](primordia/source-manifest.json) identifies 21 inspected
upstream files. Their exact Git blob SHA-1 values were independently verified
from fetched bytes; [verified-source.json](primordia/evidence/2026-10-23/verified-source.json)
also records SHA-256. The local source inspection directory is an API-fetched
subset, not a runnable checkout. No upstream source was modified or vendored into
Truth. Attribution: Primordia, Copyright (c) 2026 ChocoParrot, MIT;
[upstream license copy](primordia/UPSTREAM-LICENSE.txt).

Reviewed Truth's `AGENTS.md`, `CLAUDE.md`, research index and actors, recent
receipts and session-mycology lessons, and the biology actor's research protocol.
The referenced `.agents/skills/deep-research/SKILL.md` is absent from the
reviewed Git tree; the actor protocol exists but cannot substitute for the
missing full skill. No Claude-memory tool or local shared project-memory store
is exposed here. Actor timer/process status has not been verified.

The [prior planning document](https://drive.google.com/file/d/1gXgthYe0zres65SpjpEsq_5F7qwdW2dl/view)
was read using the Google Drive plugin. This study follows its separation of
environmental conditions, biological evidence, and fidelity decisions. The
narrower issue authorizes standalone acquisition and a draft PR. No native
interface, Truth simulation changes, new live world, or fidelity classifier is
implemented. The issue's separately dispatched Knoxx run has no verified result
in this report; no new agent was dispatched.

## What the pinned implementation actually measures

All links below identify the frozen revision.

| Observation | Source and implication |
|---|---|
| Resource flags alone are insufficient | [params.rs](https://github.com/Chokyotager/Primordia/blob/1c93703ae33b0016d48ca59d22a5703835bfe1e6/crates/primordia-core/src/params.rs#L175): `energy_on = enabled && !pair_concat`; matter also excludes `pair_concat`. Validate resolved interaction mode as well as flags. |
| Resource-constrained defaults exist | [config.rs](https://github.com/Chokyotager/Primordia/blob/1c93703ae33b0016d48ca59d22a5703835bfe1e6/crates/primordia-core/src/config.rs#L396): `primordia-2d-sun` inherits energy enabled, upkeep base 1 + 0.02 per tape byte, internal E=600, free F=500, matter enabled with L/D/A pools 8/8/24. |
| Copying has active costs | [isa.rs](https://github.com/Chokyotager/Primordia/blob/1c93703ae33b0016d48ca59d22a5703835bfe1e6/crates/primordia-core/src/isa.rs#L103): CPY/WR cost 2 energy units by default. [exec.rs](https://github.com/Chokyotager/Primordia/blob/1c93703ae33b0016d48ca59d22a5703835bfe1e6/crates/primordia-core/src/exec.rs#L183) takes a destination/executor monomer and returns overwritten material; failure to obtain the required class can prevent a write. DIVIDE transfers an existing tape segment; ALLOC creates a header, so births alone do not prove copied programs. |
| Unseeded does not mean empty | [State::init](https://github.com/Chokyotager/Primordia/blob/1c93703ae33b0016d48ca59d22a5703835bfe1e6/crates/primordia-core/src/state.rs#L136) fills occupied sites with random bytecode. Default occupancy is 0.5, length 64, maximum length 128. No designed ancestral sequence is inserted by that path. Initial genomes could already contain copying functionality. |
| Built-in assay removes resource costs | [analysis.rs](https://github.com/Chokyotager/Primordia/blob/1c93703ae33b0016d48ca59d22a5703835bfe1e6/crates/primordia-telemetry/src/analysis.rs#L55): `sandbox_params` starts from `paper-0d`, uses two slots and no mutation, and carries over maximum tape length and step budget. Energy, matter and chemistry remain off. One execution slice is scored on nonzero source positions; identity >=0.9 is labeled a replicator. |
| Detection is sampled | [telemetry/lib.rs](https://github.com/Chokyotager/Primordia/blob/1c93703ae33b0016d48ca59d22a5703835bfe1e6/crates/primordia-telemetry/src/lib.rs#L594) assays only the top 100 exact genomes at configured intervals. `replicators` counts passing genome types in that sample, not the whole population. `first_replicator` is first sampled sandbox detection, not demonstrated birth or true emergence time. |
| Parentage is incomplete for copying | [exec.rs](https://github.com/Chokyotager/Primordia/blob/1c93703ae33b0016d48ca59d22a5703835bfe1e6/crates/primordia-core/src/exec.rs#L287) assigns parent, depth and new lineage when creating a child; ordinary writes over an occupied tape do not do so. [world.rs](https://github.com/Chokyotager/Primordia/blob/1c93703ae33b0016d48ca59d22a5703835bfe1e6/crates/primordia-core/src/world.rs#L325) logs newborns, including a 32-bit tape hash. Hash agreement or increasing depth alone cannot establish genotype inheritance. |

Energy upkeep is actually deducted by
[absorb_upkeep](https://github.com/Chokyotager/Primordia/blob/1c93703ae33b0016d48ca59d22a5703835bfe1e6/crates/primordia-core/src/phases.rs#L676).
Matter exchanges have explicit ledgers, including sources/sinks. Conservation
means accounted transfers, not that every free pool stays constant or that all
chemistry is a closed system. The initial reserve is substantial and must be
distinguished from continuing resource acquisition.

The horizon also matters: the default sun period is 2,000 epochs, starvation
death and maximum age are disabled by default, and dormancy can preserve occupied
sites. A short population plateau is weak evidence for reproduction or survival.

## Existing public evidence inventory

This inventory covers the pinned repository and its documents, not all public
conversation, unpublished run directories, or an exhaustive literature review.

| Input or statement | Regime | Evidence available here |
|---|---|---|
| `t3_minimal_replicator_paper` | Seeded positive control, resource-free paper mode | Source asserts exact copying of a 64-byte tape after inserting `12 13 15 c2`. Upstream README reports pass; not executed here. |
| `runs/t10-sweep.toml` | Unseeded random soup, paper mode | Specifies 1,500 epochs, seeds 2–7. Paper mode disables energy, chemistry, matter and other physics. Six configured runs are not six available trajectories. |
| `runs/live-m4.toml` + `runs/seed-501.jsonl` | Seeded, constrained, with later live-rule additions | 400 epoch-zero seed events, all with `mirror=true` and energy 2,000. README explicitly calls the live world seeded. These inputs cannot establish spontaneous emergence. |
| README tests/benchmark table and DESIGN observations | Mixed self-reported validation and seeded live observations | No corresponding run outputs are committed. README's “see results” entries are not a trajectory dataset. |
| Unseeded constrained multi-seed trajectories | The target regime | None inspected or produced in this study. Frequency and survival remain unknown. |

No percentage of “all public evidence” can be assigned from these inputs.
The supplied reproducible recipes separate paper-mode emergence from seeded
constrained ecology; neither supplies the missing constrained emergence dataset.

## Operational classifications for this study

These are falsifiable study conventions, not universal definitions of life.
Each decision must attach run/config hashes, genome and entity scope, epoch
window, control comparison and an evidence/confidence statement. Labels can
coexist; lack of evidence returns `unknown`.

| Label | Required evidence | Insufficient evidence / control |
|---|---|---|
| `executable-copying` | A specified genome passes an execution copying assay; report exact identity rule and environment. | The upstream resource-free assay can support this label only. Compare seeded minimal genome and null `00 00`. |
| `verified-replication` | A traced candidate makes a functional copy under the resolved constrained physics; the copied program itself executes and makes a further copy. Account for energy/material debits, retained identity and confounding neighboring writers. | HUD counts, ALLOC births, depth or a sandbox-positive genome are insufficient. Compare no-usable-energy and high-upkeep arms, plus inactive/shuffled candidate controls in a targeted follow-up. |
| `self-sustaining-lineage` | At least three verified successive copying generations, descendant reproduction after the original ancestor is absent, and persistence through at least two full 2,000-epoch forcing periods without seed/reseed interventions. Establish continuing resource income rather than survival on injected reserves. | This is a deliberately bounded criterion. A 1,000-epoch pilot cannot satisfy the forcing-window condition. Need identity-linked traces and matched nulls. |
| `heritable-variation` | A descendant sequence change persists through at least two further verified transmissions with retained copying function. Quantify parent–offspring dependence against shuffled parent assignments and report sample size. | Random mutation, co-location, species clustering or static sequence diversity alone. |
| `unknown` | Missing runtime, incomplete checkpoints, inadequate identity links, failed controls, censored runs or unsupported observables. | Do not relabel missing observations as zero probability or proof of absence. |

For an epoch window T, define the seed-level event indicator I_s(T) only after
review establishes the chosen label. Then
`p_hat(T) = sum(I_s(T))/n_completed`; publish a two-sided 95% Wilson interval
and incomplete/censored seeds separately. Report first verified latency per seed,
and the number of still-at-risk seeds at each horizon. Do not discard
non-detections when presenting latency. For sandbox detections the same
statistics must be explicitly labeled **sampled copying detection**, with
50-epoch sampling and top-100 coverage limits. They do not estimate sustained
replication probability.

With n=0, frequency and confidence interval are null. Even a future 0/4 at a
fixed fully observed horizon would have a Wilson upper bound about 0.49;
a four-seed pilot cannot establish a general transition threshold.

## Predeclared experiment and resource bounds

[experiment.py](primordia/experiment.py) uses only the Python 3.11+ standard
library and the actual pinned CLI. It uses CPU golden execution, two Rayon
threads, headless mode and fresh output directories. It never supplies an
intervention file or resumes a world. No GPU parity is claimed.

| Stage / arm | Seeds | Epochs each | Deliberate changes |
|---|---|---:|---|
| Seeded controls | golden T3 + CLI assay | single execution tests | Source-defined ancestor; explicitly resource-free. Null assay is `00 00`. |
| Deterministic constrained smoke | 11 | 20 | 51×51 sites; hashes every 10; replay must compare both nonempty checkpoints. |
| Unseeded baseline | 2,3,4,5 | 1,000 | `primordia-2d-sun`, hybrid interaction, energy/matter true, upkeep 1 + 0.02/byte, CPY=2, pools 8/8/24. |
| High upkeep | same four | 1,000 | Base upkeep=8, other baseline settings unchanged. |
| No usable energy | same four | 1,000 | E=F=0, absorb_alpha=0, sun/vents off, chemistry off; energy costs and matter remain enabled. This composite ablation removes chemical energy as well as sunlight. |
| Optional longer cost pilot | same four per combination | 5,000 | Cartesian grid: upkeep base [1,4,8] × CPY cost [2,8,32]; fixed per-byte upkeep and matter pools. 36 runs. |

51 is divisible by 3 as required by `Config::validate`. These overrides were
checked against the source schema and parsed as TOML locally; **upstream Rust
config validation has not run here**. Entire resolved configs are written by
Primordia and checked against every requested override after execution. Defaults
not listed above are frozen by the pinned source and captured in those configs.
Positive costs, hybrid interaction and enabled resource flags are required.

The default wall budget is 900 seconds for build, controls and acquisition;
each job is capped at 180 seconds. A first failed gate stops subsequent jobs.
Timeouts terminate the command process group and preserve logs. Use the longer
budgets below for a cold Rust build. The optional sweep is a separate command and
budget. Neither stage establishes a matter-scarcity threshold: pools are fixed,
and an upkeep/CPY sweep is not a universal energy threshold.

Telemetry is sampled every 50 epochs (10 for the smoke), state hashes every 10, and a final snapshot
is requested. The runner records command, runtime/status, host and toolchain
logs, resolved config, interventions, upstream manifest, and SHA-256 of raw
outputs including Parquet and snapshots. It rejects incomplete epoch windows,
ledger violations, missing required trajectories/snapshots, and vacuous replays.
`validated` means acquisition checks passed, **not biological validation**.
`observed_replicator` remains null pending trace review.

The built-in top-100 assay cannot exclude rare copiers or pinpoint emergence.
Baseline initialization is reproducible from config/seed, but the normal run
does not provide an exhaustive epoch-zero assay in this harness. A later
candidate therefore requires an epoch-zero reconstruction/assay before claiming
it arose after initialization.

### Actual execution matrix

Raw records: [records.json](primordia/evidence/2026-10-23/records.json),
[invocation.txt](primordia/evidence/2026-10-23/invocation.txt),
[result.json](primordia/evidence/2026-10-23/result.json).

| Work item | Planned | CLI jobs started | Completed | Artifact validated |
|---|---:|---:|---:|---:|
| Preset inventory | 1 | 0 | 0 | 0 |
| Seeded golden test | 1 | 0 | 0 | 0 |
| Positive / null assays | 2 | 0 | 0 | 0 |
| Constrained smoke + replay | 1 run + replay | 0 | 0 | 0 |
| Baseline | 4 | 0 | 0 | 0 |
| High upkeep | 4 | 0 | 0 | 0 |
| No-energy ablation | 4 | 0 | 0 | 0 |
| Optional 36-run sweep | declared only | 0 | 0 | 0 |
| Python harness integrity suite | 7 tests | separate Python execution | 7 passed | contracts only |

The acquisition harness itself was invoked once and exited 2 at preflight;
all 17 planned CLI jobs were recorded as blocked, never started.
Per-job simulation runtimes are zero because they never launched, not because
the model ran instantaneously. The preflight hardware is Linux x86_64,
AMD EPYC 9V74, 3 logical CPUs; Python version and platform are in
[host.json](primordia/evidence/2026-10-23/host.json).
[Cgroup limits](primordia/evidence/2026-10-23/resource-limits.json) constrain
the container to 2 CPU equivalents and 8 GiB RAM.
[environment-checks.json](primordia/evidence/2026-10-23/environment-checks.json)
preserves actual checks:

- Git clone and a bounded `git ls-remote` failed: `CONNECT tunnel failed, response 503`.
- `cargo` and `rustc` are unavailable on PATH.
- `/dev/dri` is absent. No adapter was exercised.
- No Rust build, resolved upstream config, simulation manifest, state hash,
  replay, assay, or Parquet trajectory was produced.

The source audit used the GitHub connector successfully; that does not establish
shell Git connectivity. Required `bin/analyze --strict` and Truth's Clojure
checks could not run in this partial API-fetched checkout; neither Clojure CLI
nor a full checkout is available. No passing CI status is asserted.
Actual verification logs: [seven Python tests](primordia/evidence/2026-10-23/tests.txt)
and [unavailable Truth analysis gate](primordia/evidence/2026-10-23/truth-analysis-gate.txt).

## Reproduce on a capable runner

Install a current Rust toolchain supporting edition 2024, Python >=3.11,
Git/network access and platform dependencies needed to compile the pinned
workspace (including its viewer crate). The headless CPU command does not need
a display, but the CLI still depends on viewer/GPU crates at build time.
Keep the downloaded upstream MIT license and attribution intact.

From a full Truth checkout containing this branch:

```bash
git clone https://github.com/Chokyotager/Primordia.git ../Primordia
git -C ../Primordia checkout --detach 1c93703ae33b0016d48ca59d22a5703835bfe1e6
python3 -W error docs/research/biology/primordia/test_experiment.py

# Fresh output path each time; exits nonzero on blockers, timeout or failed gates.
python3 docs/research/biology/primordia/experiment.py run \
  --source ../Primordia --out /tmp/primordia-smoke-001 \
  --stage smoke --budget-seconds 1800 --job-seconds 600

python3 docs/research/biology/primordia/experiment.py run \
  --source ../Primordia --out /tmp/primordia-pilot-001 \
  --stage pilot --budget-seconds 1800 --job-seconds 600

# Review controls and smoke replay before allocating a longer campaign.
python3 docs/research/biology/primordia/experiment.py run \
  --source ../Primordia --out /tmp/primordia-cost-sweep-001 \
  --stage sweep --budget-seconds 1800 --job-seconds 600
```

To inspect all configurations without launching Rust:

```bash
python3 docs/research/biology/primordia/experiment.py plan \
  --out /tmp/primordia-plan-001 --stage pilot
```

The CLI also supports `batch <sweep.toml> --cpu`; the runner uses individual
`run` commands so each seed has its own timeout/status. A matching baseline
batch input is [baseline-batch.toml](primordia/baseline-batch.toml), with the same
four seeds and explicit settings. Run it from the upstream checkout after
substituting the absolute path to this file:

```bash
./target/release/primordia batch /absolute/path/to/baseline-batch.toml --cpu
./target/release/primordia presets
./target/release/primordia assay "12 13 15 c2" --config paper-0d
./target/release/primordia replay /tmp/primordia-pilot-001/runs/smoke-s11 --cpu
```

Direct `batch` lacks the Python runner's process budget and integrity gates;
run it under an external wall-time limit. Do not reuse a populated run directory:
upstream appends events and may leave old artifacts.

## Review procedure and smallest justified follow-up

First restore a runnable CPU environment and execute only controls and the
20-epoch smoke/replay (the pilot stops immediately if they fail). Then execute
the four matched seed arms. Retain complete raw directories outside Git with
an immutable archive URL and SHA-256; records currently point only to the
local campaign directory, and no external archive was uploaded.

If candidates are detected, inspect assay rows with exact genomes, raw
metrics/ledgers, life trajectories, lineage births and snapshots together.
Reconstruct the epoch-zero world to distinguish an initial random copier from
later emergence. Isolate a candidate in a downstream, separately versioned
constrained challenge, with a trace of executor, destination, source/target
genomes, energy and monomer changes. Track overwrites as well as newborns;
current birth telemetry cannot establish all copying ancestry. Require a copied
descendant to copy again before promoting beyond executable-copying. Keep
source changes for that instrumentation separate from this unmodified baseline.
Resource costs must remain active in the challenge; the existing `assay`
command cannot supply this test.

If the small pilot has no detected candidates, report the actual completed
seed×epoch exposure and sampling limit before widening the world or horizon.
Do not tune a failed arm into success while retaining its original label.

Against the five questions in [Primordia's spec](https://github.com/Chokyotager/Primordia/blob/1c93703ae33b0016d48ca59d22a5703835bfe1e6/primorida_spec.md#1-overview):

1. **Cost-dependent spontaneous replication:** unresolved; execute the pilot,
   then trace candidate copying and only subsequently refine a bounded cost interval.
2. **Bonded bodies/division of labor:** untested; counts and kin clusters would
   still require group reproduction and functional perturbation evidence.
3. **Chirality:** untested; mirrored seed events in live runs do not demonstrate
   spontaneous symmetry breaking. Later work needs matched mirror/twin controls.
4. **Geometry/resource ecology:** only one 2D sunlight geometry is predeclared
   here; no dimension or vent/sun comparison is inferred.
5. **Open-endedness:** untested; neither a short run nor a first takeover proves
   indefinitely increasing complexity.

Questions retained for Choco, without sending any message: which exact unseeded
config/seed/horizon produced the reported rapid emergence; were resource costs
active outside pair-concat mode; and which existing traces link copying
overwrites to functional descendants? An archived positive trajectory would be
the smallest new evidence that could narrow the current uncertainty.

