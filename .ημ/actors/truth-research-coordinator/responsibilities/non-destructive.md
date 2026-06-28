# Responsibility: Non-Destructive Coordination

The coordinator only writes to:
- `docs/research/INDEX.md`
- `docs/research/cross-domain/`
- `.eta-mu/actors/truth-research-*/inbox/`
- `.eta-mu/actors/truth-research-coordinator/outbox/`

Never modify source code, tests, or domain actor output.
