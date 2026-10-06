# Gate endpoint evidence: what already exists before travel

Date: 2026-10-06. Status: recovered corpus/source evidence, not a Gate law.
GPL-3.0-or-later. Local source: `96b19f086707388e43f272e5ec87a3a7378d4c4c`.
Later cross-branch evidence: [PR20 audit at full source revision](https://github.com/octave-commons/Truth/blob/5740c2783a5689752c7bed0d45f923081e8c2ce3/docs/notes/2026-10-06-gate-producer-contract-gaps.md).
That audit is referenced, not copied or merged into this planning base.

## Corpus authority

| Tier | Exact source | Recovered requirement or limitation |
| --- | --- | --- |
| Original user | [Origin transcript](designs/2026.06.25.16.41.16-001-the-core-vision-truth-as-a-physics-first.md), lines 92–96 | Both worlds have learned the technology; devices already exist at the other end. Travel is explicitly fictional. The text supplies no build recipe or energy price. |
| Original user | Same transcript, lines 151–168 | Time locks when Gates are created; final avatar choice occurs when discovered; temporary control is available once civilization arises. These statements do not identify one shared transition. |
| Assistant elaboration, not adopted executable law | Same transcript, lines 111–147 | The wording “independently” earned technology, a relational graph, energy-budget reachability and destination resolution on activation are elaborations. They are not numerical policy or evidence that a receiving history has been simulated. |
| Adopted phase direction | [Master design](../designs/gates-of-truth-world-gen-phases.md), lines 132–165 and 198–205 | Earned avatar convergence; discovery, construction and activation are named; a civilization may never reach Gates. Civilization existence alone is insufficient. |
| Adopted UX direction | [UX architecture](../designs/ux-architecture.md), lines 109, 184 and 261–285 | Lock time at discovery; Gates can outlast their civilization; ghost entries must be real graph entries. This does not specify whether abandoned infrastructure is operable. |
| Open research/design | [Research synthesis](../designs/simulation-methods-research.md), lines 303–323 and 435–438 | Gate travel is fictional; the connecting quantum-prior logic remains an open design question. No quantitative reachability rule is supplied. |
| Adopted economy, implemented consumer | [Commitment design](../designs/commitment-and-resonance.md), line 34; [economy](../../src/domain/player/economy.clj), lines 8–16 | Discovery yields 100 Agency and 8 first-category Resonance. These are awards, not construction or activation costs. |

## Current identity and producer frontier

[ECS core](../../src/domain/ecs/core.clj), lines 14–32, allocates local numeric
entity IDs from each world map's `:next-id`. Equal IDs in independently created
maps do not identify the same device. A participating planetary world is also
not synonymous with that containing ECS store: the store already holds many
physical bodies. No Gate-specific cross-history reference authority was found.

[The event record](../../src/domain/ecs/event.clj), lines 11–35, supplies a UUID,
tick, kind, involved entities, payload and optional causal event reference.
It can retain provenance; an event's shape alone does not prove its claim.
[Commitment emission](../../src/domain/genesis/tick.clj), lines 149–165, is an
existing example of emitting a milestone from actual component state after the
fold. It is not a Gate producer and does not define a Gate event payload.

The pinned audit separates Gate reward/text/mood consumers from absent actor,
civilization, device, connection and traversal producers. Its hardcoded
“0 of 6 thresholds” and ghost-name findings remain evidence of missing producers,
not authority for six admission predicates or existing receiver records.

## Boundaries carried into the proposal

- Retain separate histories for the two endpoints. Treat independent earning as
  a proposed provenance requirement grounded in the two-world user rule and
  assistant elaboration; do not invent a research model or a general ban on
  later knowledge transfer between already connected civilizations.
- Historical technology/construction is distinct from current device existence,
  operability, reachability, activation and completed traversal.
- Creation versus discovery clock timing, destination history before resolution,
  build/activation prices, ghost-device operability and embodiment ordering are
  unresolved. No named UI string, event reward or fictional quantum vocabulary
  resolves them.

The [endpoint proposal](../designs/gate-endpoint-provenance.md) addresses only
identity, evidence and vocabulary. Any later scientific model needs verified
primary research; this corpus recovery does not pretend such a model exists.
