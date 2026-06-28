# Method: Research Coordination

## Agenda Setting

1. Read `docs/designs/gates-of-truth-world-gen-phases.md` for current phase priorities
2. Read `docs/research/INDEX.md` for existing coverage
3. Identify gaps: what does the simulation need that isn't researched yet?
4. Prioritize by: (a) current phase needs, (b) cross-domain dependencies, (c) foundational prerequisites

## Dispatch Protocol

1. Write a research brief to the target actor's `inbox/`
2. Wait for the actor to produce output (check `outbox/` and `sessions/`)
3. Review the output for quality and completeness
4. Update the master index
5. Send cross-references to related actors if needed

## Quality Criteria

- [ ] All claims cited
- [ ] Governing equations in LaTeX
- [ ] Clojure pseudocode provided
- [ ] Toy model or validation against published values
- [ ] Charts generated
- [ ] Promotion path to domain code is clear
- [ ] Cross-references to related research added

## Synthesis Protocol

When multiple domain actors complete related research:

1. Read all completed notebooks
2. Identify shared assumptions and potential conflicts
3. Write a synthesis document in `docs/research/cross-domain/`
4. Update the master index with cross-references
5. Notify the user or code actors of actionable findings
