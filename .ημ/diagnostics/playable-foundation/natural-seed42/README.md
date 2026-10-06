# Natural seed 42 headless observation

This is a new pure production simulation, separate from the earlier native UI
trajectory. The actual initial options are `{:seed 42 :gas-count 1000}`; all other
world options come from the unchanged production defaults. No body, phase,
candidate, focus, or camera state is injected or changed after creation.

Implementation reference: `81207e7c65212685456ebe0faff3cac1cec50613`.
Launch HEAD: `cfd11fb512f72517ece7a0c86a6f8f8c8f48e180`. Before requiring the
simulation namespaces, the runner checked that tracked `src` and `deps.edn`
matched the implementation reference exactly. Subsequent repository edits do
not reload this process's namespaces. The raw first record preserves both IDs,
selected seed, parameters, Java version, and start time.

```sh
clojure -J-Xms512m -J-Xmx4g -M \
  .ημ/diagnostics/playable-foundation/natural-formation-observation/observe-natural.clj \
  42 .ημ/diagnostics/playable-foundation/natural-seed42 \
  > .ημ/diagnostics/playable-foundation/natural-seed42/runner.log \
  2> .ημ/diagnostics/playable-foundation/natural-seed42/runner.stderr
```

The runner advances only `domain.arc/tick-genesis`, for at most 12,000 ticks or
10,800,000 ms (three hours). It also stops if production deactivates the world.
It emits raw snapshots every 100 ticks, at each newly observed planet state,
departure from planet state, and new ledger event. `:event/planet-formation`
records identify actual formation events; transient gas-giant classifications
must not be counted as planet formation. All current parent, orbit, candidate,
and handoff fields come from existing production predicates and systems.

`survived-ticks` in raw body records means time since first observed planet-like
state. It does not itself prove uninterrupted planet state, binding, or orbital
stability. Derived summaries use actual formation events and retain current
state, parent, eligibility, and stored-candidate fields separately. The current
stored candidate component can outlive actual eligibility.

```sh
bb .ημ/diagnostics/playable-foundation/natural-formation-observation/summarize-natural.clj \
  .ημ/diagnostics/playable-foundation/natural-seed42/observations.edn
```

The same summary command also accepts the seed 43 observations. A summary made
while the run is active is a checkpoint, not final acceptance. Timing is an
operational budget observation while other diagnostics/native rendering may be
running, never an isolated performance benchmark.
