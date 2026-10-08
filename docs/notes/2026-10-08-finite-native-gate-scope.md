# Evidence for the finite native Gate slice

## Accepted outcome and its limits

The human approved the following finite milestone on October 7, 2026, recovered
and verified against the originating chat on October 8:

> Deliver one native vertical slice using the existing documented board: the
> user controls one player in the live ECS world, reaches one real Gate through
> ordinary input, interacts with it, and gets a visible effect. Verify the actual
> running slice and capture the result. Broader progression, civilization, and
> the wider visual polish program are later milestones.

This replaces the earlier open-ended completion condition for the current batch.
It does not declare the game complete, approve a particular starting scenario,
or waive the single ECS, review, test, provenance or native evidence contracts.
The scene and interaction choices below are proposals for planning review.

The verified acceptance is human message
`01a11970-34bc-7e03-85fa-46b8b6bbc8a2` in coordinating chat
`01a11961-2908-7982-965b-07ab15bc1235`; the owning Truth chat is
`01a11256-ee16-7131-ba20-8a2367cefc11`. The local acceptance artifact has SHA256
`c21ccfecb08390b3529ec77b2b6b8ff352b0b1f74816bcdf29b4d469dc2e8a18`.
These locators record authorization provenance; they are not runtime evidence.

## Observed source and prior design

Source was inspected at planning head
`98b847ce75491bdccacf2ec3a7476d451270bf49` and retained native head
`5ce5decae9ab756082d7e693834b4904a4c33e42`. They are different revisions;
sibling PR implementations must not be treated as already integrated.

| Boundary | Evidence | Implication |
| --- | --- | --- |
| Controlled physical player | `src/domain/player/state.clj`, `src/domain/player/flight.clj`, `src/infra/dev/window/loop.clj` | Spark position is an ECS component, advanced through the existing integrator. A second avatar simulation is unnecessary for a deliberately selected Spark slice. |
| Ordinary queued input | `src/infra/render/input.clj`, `src/infra/dev/window/loop.clj` (`IntentAtom`) | Reuse the serial current-world input boundary; a notification or direct verification-time world write is not player interaction. |
| Initial world and lifetime | `src/domain/genesis/bootstrap.clj`, `src/domain/arc.clj`, `src/infra/dev/server.clj` | The supported production start is a nebula. The default dev service may replace an inactive world. A live Gate scene needs an explicit supported initializer and lifetime policy. |
| Existing demonstrations | `dev/demo.clj` | Later arcs are marked fixtures and use identity ticking. Their screenshots cannot establish this live slice. |
| Gate representation | `src/domain/ecs/components.clj`, `src/domain/player/economy.clj`, `src/infra/render/scene/bodies.clj` | Discovery rewards and notification consumers exist; no Gate device producer, local interaction state or renderer projection is supplied. |
| Flight scale | `src/infra/menu/widgets.clj`, `src/domain/player/flight.clj` | Fine displacement has a `1e7` metre/tick floor. Human-scale movement is not established by selecting the existing preset. Scene distance, timestep, displacement and camera scale must agree. |

The existing
[endpoint proposal at its inspected revision](https://github.com/octave-commons/Truth/blob/01f0909b0c1cd5b86c274add98dfbf1b1146c2a4/docs/designs/gate-endpoint-provenance.md)
distinguishes world/history, civilization, device and construction identity from
current operability, a receiver-bound activation attempt and traversal. Its
[grounding note](https://github.com/octave-commons/Truth/blob/01f0909b0c1cd5b86c274add98dfbf1b1146c2a4/docs/notes/2026-10-06-gate-endpoint-evidence.md)
preserves the original requirement that both participating worlds have learned
Gate technology and that a receiver exists before travel. Neither document
already defines an authored starting-history policy or local power-up law.

The broader [world-generation design](../designs/gates-of-truth-world-gen-phases.md)
and [previous route audit](2026-10-06-playable-gate-route-audit.md) remain relevant
to later natural progression. They are not proof that the newly finite slice
must simulate its entire prehistory during the acceptance run.

## Derived planning choices, not observed gameplay

- Explicitly select the existing Spark as this slice's controlled player. This
  does not satisfy the separate evolved-person or avatar-lineage horizon.
- Propose a supported, mutable authored starting scene, identified as authored
  initial conditions. Never backdate simulated discovery/construction events or
  award progression credit for producers that did not execute.
- Propose one real device's **local energization**, distinct from connection,
  receiver-bound activation and traversal. Its effect must follow persistent
  Gate state in the ECS; no destination, receiver or discovery reward is implied.
- Start outside physical interaction range and require ordinary movement into
  range. Camera/focus proximity alone cannot satisfy the interaction predicate.

An independent source critique supports this interpretation with those explicit
limits (SHA256 `5e407c395820553008523c6ae7ec371a170e64ba78771328e216f6e9a2841dbe`).
It supplies design criticism, not hosted PR approval or implementation admission.
The canonical inventory audit is
`76e56283db5915ba5d54036f44dd03ced0a4fb2e1952c6adfdd371d4f9fe9984`.

## Fixed ownership and deferred work

The current batch uses Gate endpoint owner
`8e79b9ff-1cd4-4eb9-8a31-80b892fd0c98`, player/Gate umbrella
`embodied-character-voxel-mode`, and native evidence owner
`f369c598-279c-498d-a64f-45d2ce16ad34`. Their historical scopes and states remain
visible. A narrowly scoped design child supplies the missing starting-scene and
local-operation contract; it does not duplicate the endpoint provenance design.

Natural life/culture/civilization generation, avatar genealogy, Grow, mining,
construction gameplay, cross-world transport, the complete historical clock,
save/menu program and wider art work are later batches. Earlier PRs and failed
native attempts stay preserved; none receives completion credit from this plan.

Keep at most three expensive checkouts or independent snapshot jobs in flight.
Before a full snapshot or dependency install, require at least 20 GiB available.
Use one owned finite local JVM/native lane, preserve failures, and verify cleanup.
No merge, auto-merge, rebase or history rewrite is authorized.
