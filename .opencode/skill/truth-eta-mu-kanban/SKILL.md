---
name: truth-eta-mu-kanban
description: "Use the eta-mu kanban CLI to track specs and tasks in the Gates of Truth workspace"
triggers:
  - "eta-mu kanban"
  - "kanban list"
  - "truth kanban"
  - "track this spec"
license: GPL-3.0-or-later
compatibility:
  - opencode >=1.0.0
metadata:
  author: opencode
  version: 1
---

# Skill: eta-mu Kanban for Gates of Truth

## Goal

Operate the `eta-mu kanban` CLI against the Gates of Truth markdown-backed board. List, search, update, and comment on tasks without leaving the terminal.

## When to Use This Skill

- The user asks to list, find, count, or search kanban tasks.
- The user wants to start, review, or complete a spec/task.
- The user wants to add a task for a new spec or feature.
- The user says anything about `eta-mu kanban` or the Truth board.

## When NOT to Use This Skill

- The user wants deep academic research (use `deep-research`).
- The user wants to edit spec technical content — point them at the spec file under `docs/specs/` instead.

## Board Layout

```
kanban/
  openhax.kanban.json   # config: tasksDir, boardFile, FSM
  tasks/                # one markdown file per task/spec
  epics/                # epic-level cards (manually maintained)
```

Current config (`kanban/openhax.kanban.json`):

```json
{
  "tasksDir": "./tasks",
  "boardFile": ".kanban/board.json",
  "fsm": "promethean"
}
```

All commands below assume you run them from the workspace root and pass `--config kanban/openhax.kanban.json`.

## Daily Commands

### See the board

```bash
eta-mu kanban list --config kanban/openhax.kanban.json
eta-mu kanban count --config kanban/openhax.kanban.json
```

### Find a task

```bash
eta-mu kanban find <uuid> --config kanban/openhax.kanban.json
eta-mu kanban search "sph" --config kanban/openhax.kanban.json
```

### Start / move / complete work

```bash
# Preferred: update the status frontmatter directly
eta-mu kanban frontmatter <uuid> status in_progress --config kanban/openhax.kanban.json
eta-mu kanban frontmatter <uuid> status review --config kanban/openhax.kanban.json
eta-mu kanban frontmatter <uuid> status done --config kanban/openhax.kanban.json

# Alternative dedicated command
eta-mu kanban update-status <uuid> in_progress --config kanban/openhax.kanban.json
```

Valid statuses: `icebox`, `incoming`, `accepted`, `breakdown`, `ready`, `todo`, `in_progress`, `review`, `document`, `done`, `rejected`.

### Record progress

```bash
eta-mu kanban comment <uuid> "Implemented density-system tests, all green." --config kanban/openhax.kanban.json
```

### View parsed task content

```bash
eta-mu kanban content <uuid> --config kanban/openhax.kanban.json
```

## Task File Format

Every task is a markdown file with YAML frontmatter:

```md
---
uuid: "phase-0-sph-density-field-spec"
title: "Phase 0 SPH Density Field Spec"
status: "done"
priority: "P0"
labels: ["specs", "phase0", "sph"]
created_at: "2026-07-02T19:34:08Z"
source: "docs/specs/phase0-sph-density-field.md"
category: "specs"
---

# Phase 0 SPH Density Field Spec

> Original spec: `docs/specs/phase0-sph-density-field.md`

This kanban card tracks the spec. Edit the original spec for technical detail; use this card for status, priority, and work notes.
```

Frontmatter rules:
- `uuid` must be unique and URL-safe (kebab-case).
- `status` must be a valid kanban status token.
- `priority` is `P0`/`P1`/`P2`/`P3`.
- `source` points back to the spec or design doc.
- `category` groups cards (`specs`, `tasks`, `epics`, `docs`).

## Adding a New Spec Task

1. Create or identify the spec under `docs/specs/<slug>.md`.
2. Create `kanban/tasks/<slug>.md` with frontmatter and a link back to the spec.
3. Run `eta-mu kanban list --config kanban/openhax.kanban.json` to verify it appears.

## Converting Existing Specs in Bulk

If the spec directory is out of sync with the board, regenerate task cards from `docs/specs/*.md`:

```bash
bb kanban/scripts/generate-spec-tasks.clj
```

This script reads each spec, extracts title/status, and writes a kanban card in `kanban/tasks/`. Review the generated cards before committing; do not blindly overwrite cards that already have comments or manual status updates.

## Web UI (optional)

```bash
eta-mu kanban serve --config kanban/openhax.kanban.json --port 8791
```

Open http://127.0.0.1:8791 for a draggable board view.

## Common Gotchas

- **Default tasksDir is `docs/agile/tasks`**. Truth overrides this in `kanban/openhax.kanban.json`; always pass `--config` or run from the workspace root where the config lives.
- **Status must be canonical**. Use `in_progress`, not `doing`; use `review`, not `in_review`.
- **UUIDs are file-scoped**. Changing a UUID breaks board history; choose them once.
- **Keep specs and cards separate**. Technical detail lives in `docs/specs/`; status, priority, and progress notes live in `kanban/tasks/`.

## Verification Checklist

After any board change:

```bash
eta-mu kanban count --config kanban/openhax.kanban.json
eta-mu kanban list --config kanban/openhax.kanban.json
```

`count` shows column totals; `list` surfaces any cards whose frontmatter failed to parse (they will appear without a title or with odd status).

## References

- Canonical example workspace: `~/spaces/eta-mu/kanban/`
- Legacy kanban package: `~/spaces/eta-mu/packages/legacy/kanban/`
- Truth specs: `docs/specs/`
- Truth kanban config: `kanban/openhax.kanban.json`
