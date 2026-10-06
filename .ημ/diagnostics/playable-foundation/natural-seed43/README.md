# Natural seed 43 formation observation

(己, p=1.00) This diagnostic executes the production simulation loaded from
`81207e7c65212685456ebe0faff3cac1cec50613`. The runner checks that revision and
clean `src` / `deps.edn` before requiring production namespaces. It then creates
the world with `genesis/create-world {:seed 43 :gas-count 1000}` and advances it
only with `arc/tick-genesis`. It never reloads source. Later checkout edits do
not change this process's loaded code.

```sh
clojure -J-Xmx4g -M .ημ/diagnostics/playable-foundation/natural-seed43/observe.clj > .ημ/diagnostics/playable-foundation/natural-seed43/runner.log 2> .ημ/diagnostics/playable-foundation/natural-seed43/runner.stderr
```

(己, p=1.00) The budgets are 12,000 ticks and 10,800,000 ms of wall time.
Observation occurs every 100 ticks and on the first appearance/departure of a
planet-state entity or a new production ledger event. Candidate success is
recorded without stopping the survival observation. The process stops at its
budget or when production marks the simulation inactive; errors remain visible
on stderr. This is a physics/gameplay observation, not a benchmark.

(己, p=1.00) `observations.edn` contains one appended raw EDN observation per
line. It calls production `dominant-attractor`, `candidate-orbit-elements`,
`eligible-candidate?`, and `handoff-system`; no replacement classifier is used.
Stored candidates are recorded separately because the production component can
persist after current eligibility changes. A missing bound parent remains nil.
Distances to all current stars are raw geometry and do not invent a birth host.

(己, p=1.00) `new-planet-eids` means first observed in one of the production
`:planet`, `:gas-giant`, or `:planetesimal` states. It includes an existing cloud
body briefly passing through a gas-giant classification on its way to becoming
a brown dwarf or star; it does not assert a new entity was materialized.
`survived-ticks` is elapsed time since that first state observation, and must be
read with the current `:alive?`, `:matter-state`, `:parent`, and orbit elements;
it alone proves neither continuous planetary identity nor continuous binding.
The raw events and each subsequent snapshot preserve those distinctions.

(己, p=1.00) Every observation is an evolving record. Completion and a second
seed's formation/survival result must be established from the final stop record,
not inferred from this runner's existence or an intermediate candidate count.
