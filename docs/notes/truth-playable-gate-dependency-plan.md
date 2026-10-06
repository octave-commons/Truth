# Playable Truth: current frontier and the next Gate boundary

(己, p=1.00) Repository research and planning, 2026-10-06. Card
`a2b46a04-d541-4714-aed9-bf07d9a9c923`, parent
`embodied-character-voxel-mode`. Status: proposed sequence for review;
implementation and gameplay completion are separate evidence. GPL-3.0-or-later.

(汝, p=1.00) The current requested outcome is a beautiful, playable game whose
existing simulated world can narrow into embodied action and an activated Gate.
The July design remains the behavioral reference; today's request makes its
unbuilt horizon active planning work.

## Evidence frontier

| Boundary | Tier and evidence | What remains to prove |
| --- | --- | --- |
| Native launch and stellar formation | (世, p=0.99) Observed: the [continuous native capture](../demo/README.md#continuous-formation) records cloud → core → protostar → star in one ordinary run. The broken legacy `:run` entry point has a separate repair card. | Repaired console/PNG commands, native close behavior, and reproducible launch instructions. |
| Player input changes the live world | (世, p=0.99) Observed by the runtime agent: actual `Shift+G` in the native Xvfb window spent 52→37 agency. [State at tick 1417](../../.ημ/diagnostics/playable-foundation/repulsor-state.edn) has `:fixture? false`, a repulsor born at tick 1375, 374 warp-acceleration entries, and no runtime/UI error. | The screenshot precedes the applied state; it does not prove the visual strength/readability of the effect. This observation proves neither flight nor terrain interaction. |
| Naturally formed reachable planet | (己, p=0.99) Historical investigation: [`planet-orbit-circularization-blocker`](../../kanban/tasks/planet-orbit-circularization-blocker.md) records repaired integration defects and remaining formation/survival failures. Current native observation reported zero planets. A fresh isolated executable probe subsequently reproduced moving-parent and duplicate-recentering errors at spawn materialization; see the reopened [`formation-placement-v2`](../../kanban/tasks/formation-placement-v2.md). | Repair the reproduced materialization contract, then trace an ordinary seeded run through birth, survival, and admission. The isolated defect reproduction does not prove why the earlier native sample had zero planets. |
| Fly → focus → bind → commit → voxel → sculpt | (己, p=0.99) Derived from code and prior reviewed cards: `flight-no-jump-accel`, `focus-follows-pilot`, `voxel-band-render-path`, and `voxel-sculpt-verb-palette-wiring` connect the individual boundaries. [`pilot_resolve_seam_test.clj`](../../test/domain/pilot_resolve_seam_test.clj) covers important seams. | A single native session using real input and a naturally admitted world. Prior unit/seam evidence and staged worlds do not establish that session. |
| Phase 2: life | (己, p=0.99) Observed code: [`domain.ecology.state`](../../src/domain/ecology/state.clj) advances a scalar ecology through abiotic, prebiotic, prokaryotic, eukaryotic, multicellular, and complex phases; [`domain.ecology.system`](../../src/domain/ecology/system.clj) owns the component and event path. | A physical planet reaching this path naturally, player-visible causal feedback, and a researched implementation contract for richer biology. The existing scalar model is not evidence of species or society. |
| Phases 3–5: sentience, civilization, avatar convergence | (己, p=0.95) Derived gap: the [master design](../designs/gates-of-truth-world-gen-phases.md) describes these stages, while the inspected runtime's [`detect-arc`](../../src/domain/arc.clj) currently ends its forward ladder at life emergence. No dedicated society/avatar implementation was found in the inspected domain/law paths. | Research → design → small cards defining entities, causal events, player agency, narrowing criteria, and an embodied control boundary within the same ECS. |
| Phase 6: Gate discovery, activation, synchronized time | (己, p=0.99) Observed distinction: Gate event names already have reward/notification handlers. [`domain.narrowing/time-lock-record`](../../src/domain/narrowing.clj) explicitly calls its current planetary lock a data hook whose cadence actuation is later work. | A Gate-producing domain transition, player activation, embodied consequence, and implemented time synchronization. Notification handlers and a lock record do not supply those mechanisms. |

## First reproduced boundary: parent-relative formation materialization

(世, p=0.99) The runtime investigator's fresh
[seam probe](../../.ημ/diagnostics/playable-foundation/formation-spawn-frame-probe.clj)
and [output](../../.ημ/diagnostics/playable-foundation/formation-spawn-frame-before.edn)
supplied the first specific failing contract: a GI fragment planned at 2.999879 AU materialized
999.550877 AU from its host after the host advanced 1000 AU, because the packet
had no parent-relative anchor. The binary branch likewise moved from a planned
4.999698 AU to 998.939730 AU. A core seed with a parent-relative packet acquired
exactly 10 AU of relative-position error under a 10 AU recenter offset. This
contradicts the existing placement card's ≤100 AU birth acceptance without
changing its disk-scale, Hill, or velocity-pairing rules.

(己, p=1.00) Rheos reopened existing 3-point `formation-placement-v2` from
review to in progress. Its scoped correction gives GI/binary packets the same
parent-relative materialization contract as core seeds and applies the frame
shift once. Red moving-parent and nonzero-recenter tests precede implementation;
absolute spawn behavior remains a regression control. The grounding is
[integration design §§3.0 and 3.4](../designs/multi-timescale-integration.md).
The card records the fresh observations separately from missing historical
scratchpad artifacts. No new card duplicates the already-owned acceptance.

(己, p=0.97) After that repair, the current-head natural formation evidence
belongs to verification card `f369c598-279c-498d-a64f-45d2ce16ad34`, underneath
the existing physics parent `planet-orbit-circularization-blocker`. Keep the
parent an umbrella and select further ≤5-point repairs only from new evidence.

1. (己, p=0.95) Run the existing ECS genesis path with recorded seed, revision,
   parameters, and tick/time budget. Start with the historical 12,000-tick probe
   horizon, recording whether the run actually reaches a comparable formation
   stage; use a second seed when assessing a claimed formation fix. Record
   elapsed time and sampling cadence so a timeout is not reported as failure of
   the laws. Preserve raw observations under `.ημ/diagnostics`.
2. (己, p=0.98) Trace the first failing boundary: stars/disks → planet births →
   bound survival → candidate eligibility. Capture host id, birth radius,
   disk radius/mass/angular momentum, `dt`, integration path, survival, and the
   existing classifier inputs. Evaluate the actual production predicates;
   do not create a second candidate classifier in diagnostics.
3. (己, p=0.98) Reuse the relevant existing card: formation suppression or
   placement → `formation-placement-v2`; disk angular momentum →
   `sink-absorb-angular-momentum-renormalization`; compact survival →
   `universal-compact-substepping`; stellar encounter heating →
   `star-substep-heating`; classification pairing →
   `central-star-nearest-attractor` / `stability-softened-elements`.
   Verify the exact identity and current implementation with Rheos before
   reopening any card; old titles describe earlier diagnoses.
4. (己, p=0.99) Make the smallest reproduced repair satisfy a failing contract,
   then rerun the same seed and measurement. The existing
   [integration design](../designs/multi-timescale-integration.md) and
   [formation investigation](../research/physics/cluster-dispersal-integration-heating.md)
   explain prior work; their missing scratchpad artifacts limit independent
   reproduction of the old numbers. Preserve that uncertainty.

(己, p=0.99) Candidate admission is stricter than a body being called a planet:
[`eligible-candidate?`](../../src/domain/stellar/classifier/candidate.clj)
checks material, orbit stability, atmosphere, mass, H/He fraction, eccentricity,
and equilibrium temperature. Weakening those gates to make a screenshot would
erase the very contract the gameplay depends on.

## Visible payoff and the remaining ladder

(己, p=0.97) Once a natural candidate exists, use the same native session to fly
within focus range, observe increasing binding and commitment, see its voxel
band, and trigger `T` / `Shift+T` / `Y` with recorded Resonance and terrain diffs.
Capture both pixels and causal state. The current verification card owns this
observation; it does not close the older review cards automatically.

(己, p=0.95) Visual work can then proceed in parallel with formation research:
`body-trails-ringbuffer` is an independent 5-point readability slice;
`mote-of-light-shader` is 5 points after orientation. Their common flight design
lost its original scratchpad research. The newly tracked
[rotation note](../research/physics/spark-rotation-integration.md) grounds only
rotation; restore relevant rendering/sampling evidence before claiming the
other cards ready. Full force channels and chase camera are each 8 points and
must split before implementation.

(己, p=0.98) Keep the Gate horizon sequenced as evidence contracts: a living
world with meaningful interventions → represented social actors and causal
culture → civilization and an earned avatar set → embodied agency in the
existing voxel world → an actual Gate discovery/activation transition → time
synchronization. This is a dependency hypothesis for future research, not an
implementation specification. Only the first currently missing boundary is
scoped here.

(己, p=0.99) `character-scale-mining-construction` remains explicitly blocked
on embodiment despite the earlier voxel substrate cards being done. The master
design permits sterile and ungated histories, so eventual playable-Gate
acceptance needs a reproducible supported successful scenario alongside honest
failure outcomes; it must not require every simulated world to be forced through
the same ending.

(己, p=1.00) Next action: pin the two parent-relative materialization defects
with red regression tests under reopened card `formation-placement-v2`.
