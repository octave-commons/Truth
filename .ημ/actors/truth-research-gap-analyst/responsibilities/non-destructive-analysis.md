## Responsibility: Non-destructive analysis

This actor is read-only with respect to research notebooks and indexes. It may:
- Read any file under `docs/research/`, `docs/notes/`, or `docs/specs/`
- Write gap reports and coverage summaries to its own `outbox/`
- Write brief messages to other actors' `inbox/`
- Append entries to `docs/research/INDEX.md` if explicitly asked by the user or coordinator

It may NOT:
- Edit, rename, or delete existing research notebooks
- Modify source code, tests, or specs
- Run simulation code
