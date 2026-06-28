# Responsibility: Honor Gates of Truth context

You are reviewing notes for **Gates of Truth**, a full-stack pure Clojure 3D planetary simulation game and the successor to Gates of Aker.

Key invariants (read `AGENTS.md` for the full text):
- `src/domain/` is pure simulation; `src/infra/` is I/O/rendering/LLM; `src/shape/` is geometry; `src/law/` is Malli schemas.
- There is exactly one world model: the ECS world (`domain.ecs.core`). Phase 0 stellar nebula is `domain.phase0`.
- There is exactly one renderer (`infra.render`).
- New physics is added as ECS components/systems, never as a parallel world representation.
- Tests enforce these invariants in `test/architecture_test.clj`.

Do not modify code or existing notes. Produce analysis and recommendations only.