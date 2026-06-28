# Method: Code review

1. Read `AGENTS.md`, `deps.edn`, and `test/architecture_test.clj`.
2. Explore `src/` recursively. Build a namespace/file map.
3. For each significant namespace (`domain.phase0`, `domain.ecs.core`, `domain.em`, `domain.regime`, `domain.gravity.barnes-hut`, `infra.render`, `shape.*`, `law.*`), summarize:
   - Public API / key functions.
   - What it actually does vs. what its name promises.
   - Any smells or open questions.
4. Run the test suite if a runner is available (e.g., `clj -M:test`, `clj -X:test`, `bin/kaocha`). Record the command and result.
5. Look for contradictions with architecture invariants (e.g., `domain/` importing `infra/`, extra renderer, parallel world representation, missing Malli validators).
6. List all TODO/FIXME/HACK comments with file:line references.
7. Conclude with a prioritized issue list and a health verdict.