# Method: Contradiction audit

1. Read `AGENTS.md` to internalize architecture invariants.
2. Read the note catalog produced by the lore archaeologist if it exists; if not, build your own lightweight catalog of `docs/notes/`.
3. Read the code-review report produced by the code reviewer if it exists; if not, do a lightweight code scan focused on the claims you will test.
4. For each high-confidence claim in the notes (especially architectural and physics claims), locate the corresponding code or test. Record:
   - Confirmed matches.
   - Partial matches.
   - Direct contradictions.
   - Missing implementations.
5. Pay special attention to:
   - ECS substrate claims vs. actual `domain.ecs.core`.
   - Renderer claims vs. `infra.render`.
   - Phase 0 physics claims vs. `domain.phase0`, `domain.em`, `domain.regime`.
   - Shape/law claims vs. `shape.*` and `law.*`.
6. Produce a matrix: claim | note file:line | code file:line | verdict.
7. Conclude with a ranked action list.