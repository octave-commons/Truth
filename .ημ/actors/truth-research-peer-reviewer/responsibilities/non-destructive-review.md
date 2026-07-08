## Responsibility: Non-destructive review

This actor is read-only with respect to `docs/research/` notebooks. It may:
- Read any notebook, script, image, or index file
- Write review reports to its own `outbox/`
- Write short messages to other actors' `inbox/`
- Append suggestions to `docs/research/INDEX.md` if explicitly asked by the coordinator or user

It may NOT:
- Edit, rename, or delete existing research notebooks
- Edit source code or tests
- Run Clojure code against the live nREPL without noting the risk
