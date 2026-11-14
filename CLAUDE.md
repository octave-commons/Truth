# CLAUDE.md

@AGENTS.md

<!--
AGENTS.md is the single source of truth for every agent harness. Claude Code
imports it here so it loads even when a parent directory has its own CLAUDE.md.
Anything below this comment must be true only for Claude Code.
-->

## Claude Code

- Project rules live in `AGENTS.md` above. Do not restate them here, and do not
  let `/init` append a second copy; if it does, replace the copy with the import.
- Your native auto-memory directory for this checkout is the shared store
  described in AGENTS.md › Shared memory. Write memories in that format so the
  OpenCode bridge can read them.
- This repository checks in no `.claude/` directory: no project hooks, settings,
  slash commands or `.claude/skills/`. Per the root `CONTRACT.edn`, Claude
  discovers the project-local skills in `.agents/skills/` through AGENTS.md ›
  Agent skills; read the listed `SKILL.md` when its trigger applies.
