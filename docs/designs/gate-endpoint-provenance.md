# Gate endpoint identity and provenance

Date: 2026-10-06. Status: proposed planning contract; review pending, no
implementation admission. GPL-3.0-or-later.
Owner: Incoming three-point child `8e79b9ff-1cd4-4eb9-8a31-80b892fd0c98` of
`embodied-character-voxel-mode`. [Card](../../kanban/tasks/specify-independently-earned-gate-endpoints-and-lifecycle-identities-92fd0c98.md).
Grounding: [endpoint evidence](../notes/2026-10-06-gate-endpoint-evidence.md),
including original user statements and the pinned PR20 audit.

## Bounded outcome

Make “a receiving device already exists in another capable world” an inspectable
claim. The proposal defines semantic references and provenance obligations, not
an executable schema, registry, construction recipe or travel predicate. It
does not create the required histories. All choices below are proposed until
reviewed; recovered source requirements retain their tier in the evidence note.

## Distinct references and facts

| Concept | Proposed meaning / required provenance | Must not be substituted |
| --- | --- | --- |
| Participating world | Reference distinguishes its planetary world and containing simulated history; stable across save/reload and distinct for separate histories. Allocation authority is not yet implemented. | A display name, generation seed alone, or bare local ECS ID. |
| Civilization | Historical producer identity within that world, with links to its actual actor/history records once those producers exist. | A scalar complex-life tag, narrator claim or observer identity. |
| Device / endpoint | World-qualified device identity plus attributable construction evidence. Preserve historical identity after loss; existence and operating state remain separately evidenced. | Civilization identity, a discovered technology, a UI row or the visit request. |
| Technology evidence | Identified occurrence and causal history showing that this world acquired the required capability. The capability law is a later responsibility. | A newly emitted generic discovery/reward event with no producer evidence. |
| Construction evidence | Identified completion occurrence linking the device, its world and actual producing civilization/history. Recipe and material accounting remain unspecified here. | Knowledge alone, a proposed build action or an arrival that creates its receiver. |
| Connection | Separate relation between two identified endpoints, subject to a later connection law. | Two construction records treated as automatically reachable. |
| Activation attempt | Separate occurrence referencing initiator, source and intended receiver; eventual result and cost evidence refer to that same attempt. No charge/retry policy is chosen. | Device identity reused as an operation ID, or an attempt treated as success. |
| Traversal | Separate outcome involving an actual traveler and its source/destination references. Embodiment and transfer policy remain missing. | Discovery, activation or avatar injection. |

These are semantic reference roles, not names of new ECS components or a second
world representation. Reuse the existing event UUID/payload/cause model for
evidence references. Do not require one physical person to be the sole builder:
an institutional process is possible, but its producer model is not supplied.
Identity allocation and persistence must be explicitly designed before executable
cross-world schemas can be admitted.

“Independently earned” here proposes separate attributable endpoint histories.
It does not invent a ban on technology transfer after contact. Each receiving
device must pre-exist the attempted traversal, with its own causal construction
evidence; the request may not synthesize that prerequisite. Historical evidence
alone does not answer whether the device still exists or can operate now.

## Worked review cases (not runtime fixtures)

| Given | What the proposed contract can establish | What remains unproved |
| --- | --- | --- |
| Worlds A and B each retain technology and device-completion evidence attributable to their own histories. | Two historically attested endpoints. | Current operability, relation, affordable activation and traversal. |
| A requests B, but B has no constructed device evidence. | Receiver prerequisite is unsupported. | No receiver may be inferred or created by this request. |
| B has technology evidence but no construction occurrence. | Capability claim only. | Device construction and every later stage. |
| Both histories allocate entity 7. | Distinct devices when world-qualified; bare ID is ambiguous. | A canonical cross-history allocation/persistence implementation. |
| The same completion evidence is presented twice. | The same historical occurrence, not two devices or two completions. | Reward/debit idempotency and transport retry policy; event identity alone does not implement them. |
| A device was constructed and later destroyed. | Its construction remains historical evidence; destruction does not erase its identity. | It cannot be treated as currently existing solely from the old record. |
| A civilization vanishes and infrastructure remains. | History and surviving infrastructure are distinct facts, consistent with the ghost-node direction. | Power, maintenance, consent and any other operability policy are unspecified, not new requirements chosen here. |

## Unresolved policy and ownership

| Boundary | Existing coordination owner / missing responsibility |
| --- | --- |
| Creation/discovery/activation clock boundary | `committed-clock-executable-policy` coordinates temporal semantics; its first local-lock scope does not implement the eventual network clock. Original creation wording and adopted discovery wording remain unresolved. |
| Actor and civilization provenance | `life-to-represented-actor-spec` owns the first actor boundary only. Civilization, research and construction producers need later phase contracts; they are not supplied by this child. |
| Destination history before full resolution | Original pre-existing receiver requirement versus assistant statistical-collapse sketch needs an explicit causal-history policy. No current owner supplies it; do not fabricate that history at arrival. |
| Prices and connection law | No accepted build recipe, energy unit, debit/refund rule, relational metric or receiver-operability law. Future design ownership remains unassigned. Discovery awards are not prices. |
| Temporary/final embodiment and traversal | Existing embodied epic retains the horizon. Avatar choice timing and post-Gate actions remain unresolved; this child provides vocabulary only. |

The actor and clock cards are semantic references, not blocking dependencies
between independent planning tasks. Their absence of production mechanisms,
together with the unassigned policies above, does block any claim that this
contract admits a functioning Gate producer. Parent phase-spec prerequisites
remain intact; no implementation stories are created here.

## Verification and exit

Review the evidence tiers, identity table and worked cases against the cited
corpus and source. Check that no two concepts collapse into a flag, that repeated
evidence is not new history, and that unknown policy is never reported as a
successful connection. Verify links, UUID parentage and unchanged Incoming state
through Rheos. The draft may close this bounded planning question only after
review; no test fixture, prose trace or review approval is a native Gate result.
