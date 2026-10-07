# Other disabled emitters: source audit

(己, p=0.99) Read-only audit for the existing one-point
`fix-warp-disabled-stale-write` acceptance, against committed
`4f4eb63ec07ffd4678c3bb635f7c11e4b6c30223`. No additional repair, test run,
native input, new task or readiness decision is part of this audit.

The owner-removal contract is explicit in
`docs/notes/specs/2026.06.26-ecs-double-buffer-single-writer-spec.md:121–122`.
`apply-write-set` merges per-entity cells rather than replacing a column
(`src/domain/ecs/tick.clj:44`); `contribution-write-set` emits removal sentinels
for prior recipients absent from the fresh result (`tick.clj:222`). The unified
integrator design routes observer and thermal effects through these owned
contributions (`docs/notes/specs/2026.06.29-unified-physical-state-integrator-spec.md:205–206`).

## Registered paths and reachability

**Observer halo — ordinary disable control reaches the stale branch.**
`src/domain/player/influence.clj:24` returns `{c/accel-observer {}}` when the
observer is absent or the halo factor is nonpositive. The active branch clears
departed recipients correctly. This emitter is still wired into the actual
physics systems (`src/domain/genesis/systems.clj:48`), declared in the registry
(`src/domain/ecs/registry.clj:210`), and consumed by the live acceleration sum
(`src/domain/integrator/base.clj:16–18`). The Spark menu exposes the halo factor
with a zero lower bound (`src/infra/menu/widgets.clj:129–131`); its action travels
through the existing world intent (`src/infra/dev/window/loop.clj:371–375`).
After a nonzero emission, disabling that control retains the old force under
the merge fold. This is a source-derived consequence, not a new native or test
reproduction. `test/domain/observer_influence_test.clj:104` tests an initially
disabled world, not emit-then-disable. The registry also omits the active
branch's prior `c/accel-observer` read; that is an audit finding only.

**Thermal intervention — stale ownership is confirmed; distinguish expiry
from early removal.** `src/domain/intervention.clj:249–275` uses the prior-cell
helper only while thermal interventions exist. Its no-intervention branch
retains prior `c/heat-intervention`. The emitter is live at
`src/domain/genesis/systems.clj:53`, and temperature consumes the stored payload
at `src/domain/integrator/temperature.clj:76–84,103–121`. Paid Heat/Cool actions
are exposed by H/J (`src/infra/render/input.clj:41–44`) and use normal
`intervention/place` (`src/infra/dev/window/loop.clj:361–364`).

Ordinary TTL expiry runs **after** the emitter/fold
(`src/domain/genesis/tick.clj:167–172`). At the expiry tick, an in-range thermal
source still emits a list with `:ease 0.0`: `thermal-contributions` tests range,
not positive decay (`src/domain/intervention.clj:218–235`). The subsequent
empty branch therefore retains this zero-ease payload, rather than necessarily
the previous nonzero ease. Do not describe ordinary expiry as proved repeated
nonzero heating. Nor is zero ease universally inert: the consumer still calls
`apply-thermal-contributions`, which clamps the result to `[3, 1e7]` K
(`intervention.clj:237–247`); a later base temperature outside that range would
still be constrained. Removing the source earlier while nonzero ease is stored
would repeat that easing, but this audit found no ordinary UI remove-source
operation. Active partial recipient loss already uses the removal helper.
The thermal registry entry omits its active branch's prior
`c/heat-intervention` read (`src/domain/ecs/registry.clj:228–231`).

**Player thrust — conditional lifecycle gap, not ordinary key release.**
`src/domain/player/flight.clj:79–107` promises auto-clear without an observer
but returns an empty column in that branch. The live registered emitter and
integrator are wired at `src/domain/genesis/systems.clj:51,54` and
`src/domain/integrator/base.clj:16–18`. Stale thrust can survive if only the
observer component is removed while its physical body remains. This audit
found no ordinary input path that makes that change. Normal entity despawn
removes all its component cells (`src/domain/ecs/core.clj:39–49`), so despawn
alone is not this counterexample. Releasing flight input retains the observer
and computes the intended damping through the active branch. The existing test
checks a fresh empty world, not a formerly thrusting surviving body
(`test/domain/spark_body_test.clj:209–224`). Its prior thrust read is already
declared (`src/domain/ecs/registry.clj:220–224`).

## Existing ownership and disposition

Canonical Rheos `search-tasks` / `read-task` reads used the reviewed upstream
CLI `83c6b397278d418d69ce6509b8c3d9fe87e88cca9141b28143d9efb5d78a75a7`
and this checkout's `openhax.kanban.edn`; no board parser or manual state edit.

| Finding | Existing canonical owner at audit | Scope boundary |
| --- | --- | --- |
| Live passive halo | `remove-passive-halo-invert-influence`, **TODO, 2 points** | Already owns removal of this emitter and stronger paid wells. The warp card's historical “being deleted” text is not current source truth. Coordinate with that owner; do not silently add removal or force tuning here. |
| Thrust absence branch | `flight-no-jump-accel`, **In Progress, 3 points** | Existing thrust owner; this conditional lifecycle gap is not an already reproduced ordinary key-release failure or permission to widen the current precision repair. |
| Thermal absence branch | No dedicated matching owner recovered | Targeted searches for `thermal`, `heat`, `Heat`, `temperature`, `intervention`, `source`, `sink`, `stale`, and `integrator` found no matching thermal-lifecycle task. This is not an exhaustive absence proof or admission to implementation; canonical triage/association must precede a repair. |

The existing warp acceptance is satisfied by recording these findings, not by
fixing all emitters under its one-point scope. Any follow-up needs its own
admission and actual emit/fold/disable/fold/consumer regression, preserving the
legitimate final Jacobi carry. The earlier RED audit also noted a separate
empty `disc-tag` classification branch; it is not a force/heat repair and is
not expanded here. No feature policy, clock, force law or temperature clamp is
changed by this note.
