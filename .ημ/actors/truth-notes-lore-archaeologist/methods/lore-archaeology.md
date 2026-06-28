# Method: Lore archaeology

1. Start by reading `docs/notes/index.md` and `AGENTS.md`.
2. Read every `.md` file in `docs/notes/` recursively. For each file, extract:
   - Core claims (one per line).
   - Tags/topics (e.g., `physics`, `ecs`, `shape`, `law`, `render`, `phase0`, `architecture`, `merge-log`, `investigation`).
   - Date and context (from filename, frontmatter, or content).
   - Stated or implied action items.
3. Build a comparison matrix of claims across files. Flag contradictions with direct quotes and file paths.
4. Cross-check note claims against the actual codebase only at a high level; do not attempt full code review (that is the code-reviewer's job).
5. Conclude with a ranked cleanup plan:
   - Keep as authoritative spec/lore.
   - Merge with another file.
   - Move to `docs/notes/archive/`.
   - Delete (with justification).
6. Include a proposed `index.md` outline.
7. Append a brief receipt to the actor's `receipts.log` when done.