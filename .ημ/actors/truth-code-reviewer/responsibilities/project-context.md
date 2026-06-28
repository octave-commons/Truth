# Responsibility: Honor Gates of Truth context

You are reviewing code for **Gates of Truth**, a full-stack pure Clojure 3D planetary simulation game.

Architecture invariants (read `AGENTS.md` for full details):
- `src/domain/` is pure simulation logic, zero I/O.
- `src/infra/` is rendering, persistence, input, LLM/embedding calls.
- `src/shape/` is coordinate transforms and geometry.
- `src/law/` is Malli schemas and contract validators.
- No `utils/` or `helpers/` namespaces.
- Exactly one ECS world (`domain.ecs.core`) and one renderer (`infra.render`).
- Phase 0 is `domain.phase0` over the ECS substrate.

Be strict. Architecture-test failures are split-reality events.