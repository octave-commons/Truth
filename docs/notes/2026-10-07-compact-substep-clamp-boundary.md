# Compact substep clamp: observed limit and measurement boundary

2026-10-07. **Source-only research; proposed measurement, not implementation
admission or numerical qualification.** The [recorded scope](../../.ημ/diagnostics/compact-clamp-research/root-scope.json)
belongs to native-verification owner `f369c598-279c-498d-a64f-45d2ce16ad34`,
InProgress, estimate 3 at scope recording. This note reads inherited production
`b395c4049718f7ce015ddf25fc0a192d97821373` at evidence parent
`7f1fbcae786a7aa4c6b1cb2eed5d79dd71bb6e58` (closed PR51).
The [source audit](../../.ημ/diagnostics/compact-clamp-research/source-audit.json)
pins the files, line references, bounded searches and installed Clojure evidence.
No instrumentation, project namespace, JVM, test or native session was run for
this research. The separately owned current native attempt was not contacted.

## 1. What the closed run establishes

The unchanged [runtime stdout](../../.ημ/diagnostics/natural-flight-profile/runs/attempt-01/002-runtime.stdout)
contains **4,893 occurrences** of `K clamp binds:` in 331,439 bytes. The first
three intact records report entity 1002 demanding 14,447, entity 1001 demanding
29,472, and entity 1003 demanding 7,082 substeps; each applies 4,096. Concurrent
printing interleaves later records. This count is a literal marker count, not a
validated per-body census or maximum demand.

The warning has no tick, timestep or branch identifier. The [closed run report](../../.ημ/diagnostics/natural-flight-profile/RESULT.md)
records coarse observations, six polling gaps over 60 seconds, no aim/look/hold
commands, and no sampled fresh handoff. It does **not** establish that a fresh
target never existed. Its diagnostic target gate was stricter than the game's
retained-ready contract. Neither the warnings nor that sampling establish
ejection causality, failed flight, or failed binding.

## 2. Exact current numerical condition

For ordinary finite positive inputs, [kinematics.clj lines 140–173](../../src/domain/integrator/kinematics.clj)
implements the following once at entry to each compact-body advance:

```text
T(r, μ) = 2π sqrt(r³/μ)
a_eff   = max(μ/r², supplied :accel or 0)
dt_local = min(0.05 T, sqrt(2 × 0.03 × ε_world / a_eff))
demand  = long(ceil(dt / dt_local))
K       = max(1, min(4096, demand))
h       = dt / K
```

`dt`, `T`, `dt_local`, `h` are seconds; `r` and `ε_world` are metres; `μ` is
m³/s²; acceleration is m/s². `T` is the circular timescale at current separation,
not a period fitted from the body's eccentric orbit. The clamp logs when
`demand > 4096` and continues over the **full** `dt`; it neither rejects the step
nor changes the world clock. This equation is not an added validation policy for
nonfinite or degenerate inputs.

Only `:planet`, `:gas-giant` and `:stellar-remnant` currently enter compact
subcycling. Stars/protostars can be parents but do not themselves enter it;
formation intermediates and the Spark are also outside this set. The parent is
the nearest eligible stellar body by frozen distance, not necessarily the
classifier's attractor (kinematics lines 93–138).

| Actual path | Count inputs and advance | Meaning of a binding clamp |
| --- | --- | --- |
| WH, `compact-advance` lines 255–292 | A valid `dominance-gate` must satisfy `μ/r² > 100 |a_tidal|`; count gets current `r`, `μ = G(M_parent + m_body)`, world softening, and no `:accel`. Each of K steps performs Kepler half-drift, frozen tidal kick, Kepler half-drift; the parent is composed using its due-dependent Euler advance. | The central two-body drift is solved by the existing Kepler routine across the full subarc. K limits perturbation-split resolution; a warning alone is not proof that the central orbit is under-resolved. Solver tolerance, frozen-parent/tidal assumptions and outer composition still matter. |
| Embedded KDK, lines 337–399 | Gate failure, degenerate geometry or no parent reaches this path. Count additionally gets the magnitude of **fresh tree gravity at the starting position plus frozen non-gravity channels**. With no resolvable parent, its map uses `r = ε_world`, `μ = |a₀| ε_world²`. KDK reevaluates gravity at intermediate positions in the frozen source tree; other channels stay fixed. | K caps force-sampling resolution along a curved trajectory at `h = dt/K`. It does not inherit the exact central Kepler-drift claim. The no-parent path must be measured too. |

Both count paths use **world** softening. Pair softening separately affects the
dominance subtraction and gravity kernel (lines 229–253 and 306–335). WH's tidal
subtraction uses prepared drift-predicted positions and the lagged acceleration
channels. KDK samples a frozen tree, not moving source trajectories. Neither
path's description guarantees an error bound under arbitrary clamp demand,
changing timesteps/worlds, tree approximation or encounters.

The [integration design §3.3](../designs/multi-timescale-integration.md) explicitly
chooses and logs the ceiling (lines 144–171). Its Kepler-drift accuracy statement
is specific to that split, not a universal KDK guarantee. Historical §9 prose
(lines 338–345) describes frozen total force; the current implementation instead
reevaluates gravity along the embedded trajectory. This note preserves that
distinction rather than treating the older prose as executed evidence.

## 3. Existing coverage and its limit

The source-read coverage includes:

- [Orbital regression lines 330–341](../../test/domain/orbital/multi_timescale_regression_test.clj):
  the 5 AU, 80-year case explicitly requires K between 100 and 200 and below the
  clamp. It does not qualify binding-clamp accuracy.
- [Universal substep tests](../../test/domain/integrator/universal_substep_test.clj):
  gate-fail fixed-field regression (lines 202–232), independent uniform-field
  full-time coverage (387–450), no-parent trajectory (452–516), and outer
  composition/SoA parity (524 onward). The no-parent expectation partly
  replicates KDK; agreement there is not independent clamp convergence evidence.

The bounded search for `substep-count`, `substep-max-k` and `K clamp binds` in
`test/**/*.clj` found these references, not an explicit binding-clamp convergence
or error-envelope test. This is a source coverage finding; no tests were rerun,
and it is not a claim to have exhaustively classified every numerical assertion.
The related canonical cards previously read were `universal-compact-substepping`
(Review 5) and `orbit-integration-regression-tests` (Review 5). Their status is
provenance, not permission to change their laws. This follow-up remains on the
explicitly recorded research owner; raising K, pacing changes and stellar
advancement are outside its scope.

## 4. Proposed observation of the actual prepared call

[Genesis `step-physics`, lines 78–100](../../src/domain/genesis/tick.clj), builds
query and physics-SoA caches before the shared-world fan-out and strips them
after the fold. Thus a later published-world read cannot recover the actual
prepared arrays or prove the branch taken. The smallest useful outer boundary is
the existing private [`run-integrator-phases`, lines 38–65](../../src/domain/integrator.clj):
its argument is that prepared world and its return is the actual integrator
write-set. A future diagnostic could use **five pass-through wrappers**:

| Existing function | Actual evidence to retain for selected bodies |
| --- | --- |
| `run-integrator-phases` | Call generation, exact prepared-world identity while active, tick/time/dt, selected input rows, and selected final position/velocity writes. |
| `compact-advance` | Actual body/parent/due flag and summed body-acceleration arguments; actual nil fallthrough or pre-outer-composition `[velocity position]` return. |
| `dominance-gate` | Actual returned gate map, including relative position/velocity, μ, tidal acceleration, parent acceleration and parent state, or actual nil. |
| `embedded-substep` | Actual starting position/velocity, frozen non-gravity vector and nullable parent; actual pre-outer-composition return. |
| `substep-count` | Actual dt/orbit-map arguments and actual returned K, attributed to the enclosing WH or KDK call. |

Each wrapper must call its captured original exactly once with unchanged
arguments and return the identical object or rethrow the identical original
Throwable. It must not write the world or replace an integration path. Capture
the selected SoA primitive values immediately, including predicted positions;
do not retain mutable array references as historical evidence. Also retain
selected actual influence-channel values, frame offset, due policy and bounded
absorption inputs needed to interpret outer composition. Count/flag any omitted
absorption detail rather than claiming a complete reconstruction.

**Derived afterward:** the two local-step criteria, demanded K and `h` can be
recomputed from the pinned formula and captured count arguments. They are not
observed local variables. Actual returned K and the gate result are observed.
KDK's initial acceleration magnitude is in its actual count map, but this
five-wrapper proposal does not observe the complete initial gravity vector or
every internal force sample. The final write-set includes outer `dv.*`, frame
subtraction and absorption blending; a reconstructed decomposition is derived.
It is not the final published world after all other systems and reaping.

The SoA implementation processes all rows; due indices limit force accumulation
and parent advance (kinematics lines 554–603). Do not use its broader public
docstring's “only due entities” claim to filter the diagnostic. Entity work uses
[chunked futures](../../src/domain/ecs/parallel.clj), so an outer Java ThreadLocal
alone cannot identify nested calls. A proposed generation must validate the
**same world object**, establish branch-local context on the actual worker, and
clear it in `finally`. A no-parent embedded call has no preceding gate call;
gate failure followed by embedded work is a distinct valid sequence. Overlap or
missing attribution must invalidate completeness, not be guessed from IDs.

## 5. Qualification and finite limits before any installation

Var replacement is **not yet verified**. `deps.edn` selects Clojure 1.11.1 for
the normal demo. The installed 1.11.1 `clojure/core.clj` documentation states that
direct-linked call sites are unaffected by Var redefinition; its hash and exact
excerpt are preserved in the source audit. No direct-link setting is declared
in the normal demo alias, but this does not prove how existing call sites were
compiled. The proposed Vars lack `^:redef`/`^:dynamic`. Reading current compiler
options or successfully replacing a Var root would not prove interception.

Before native use, a separately admitted, detached qualification must run the
existing real prepared call chain for WH, gate-fail KDK and no-parent KDK, with
unwrapped/wrapped raw numerical outputs equal and each expected wrapper actually
hit. It must include the parallel entity path and original-error preservation.
Missing sentinel hits fail qualification; no silent namespace reload is allowed
to manufacture coverage. This is proposed instrumentation qualification, not
an executed test or natural-world progression evidence.

The smallest proposed native observation is four explicit compact-body IDs plus
their actual parents, at most 64 completed outer calls or 30 seconds, whichever
arrives first, and at most 256 body/call records. Use fixed scalar/vector fields
and explicit bounded absorption-detail counts; retain no full world/tree after
its active call. A 4 MiB **encoded export** ceiling can be checked after capture;
it is not a literal JVM heap bound. Report omissions, partial records and an
oversize export as such. Do no per-body I/O or serialization and do not wrap
`gravity-at` or each Kepler drift, which would multiply observation work by K.

Install unarmed, arm only at an outer-call boundary, and restore roots only if
the installed wrappers still own them. An observer bookkeeping failure must
preserve the original physics result/error while marking the diagnostic failed;
in-flight work remains partial until accounted for. A broad `with-redefs` around
asynchronous scheduling is insufficient: roots could restore before the work
runs. A deadline stops admission of records, not a blocked JVM operation.

Record unwrapped/pass-through/collecting call durations and observer self-time
under labeled conditions, including ongoing native/render load. That can expose
observer cost; it cannot establish an isolated speedup, native FPS or zero
scheduling effects. If a natural branch is absent, report it unobserved rather
than force a world into it. This note admits none of these hooks: the next
decision is whether to scope the bounded qualification, while leaving numerical
laws and the current native attempt unchanged.
