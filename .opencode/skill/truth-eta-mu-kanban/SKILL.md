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
- The user wants to create a brand new spec from scratch (use `spec-driven-dev`).

## Board Layout

```
kanban/
  openhax.kanban.json   # config: tasksDir, boardFile, FSM
  tasks/                # one markdown file per task/spec (specs live here)
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

Every task is a markdown file with YAML frontmatter. Specs and tasks are stored together in `kanban/tasks/`. The card body holds the full technical detail, while the frontmatter tracks status, priority, and work notes.

```md
---
uuid: "phase-0-sph-density-field-spec"
title: "Phase 0 SPH Density Field Spec"
status: "done"
priority: "P0"
labels: ["specs", "phase0", "sph"]
created_at: "2026-07-02T19:34:08Z"
source: "kanban/tasks/phase-0-sph-density-field-spec.md"
category: "specs"
---

# Phase 0 SPH Density Field Spec

Full technical detail lives here, in the card body.
```

Frontmatter rules:
- `uuid` must be unique and URL-safe (kebab-case).
- `status` must be a valid kanban status token.
- `priority` is `P0`/`P1`/`P2`/`P3`.
- `source` points to the canonical card location.
- `category` groups cards (`specs`, `tasks`, `epics`, `docs`).

## Adding a New Spec

1. Create `kanban/tasks/<slug>.md` with frontmatter and the full spec content in the body.
2. Run `eta-mu kanban list --config kanban/openhax.kanban.json` to verify it appears.

## Converting Existing Specs in Bulk

If the spec directory is out of sync with the board, migrate spec content directly into the corresponding kanban cards and then delete the old spec files. Do not leave cards that only link to a separate spec.

## Web UI (optional)

```bash
eta-mu kanban serve --config kanban/openhax.kanban.json --port 8791
```

Open http://127.0.0.1:8791 for a draggable board view.

## Common Gotchas

- **Default tasksDir is `docs/agile/tasks`**. Truth overrides this in `kanban/openhax.kanban.json`; always pass `--config` or run from the workspace root where the config lives.
- **Status must be canonical**. Use `in_progress`, not `doing`; use `review`, not `in_review`.
- **UUIDs are file-scoped**. Changing a UUID breaks board history; choose them once.
- **Keep technical detail in the card body**. Do not make cards that only link to a separate spec file; the kanban card is the spec.

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
- Truth specs/tasks: `kanban/tasks/`
- Truth kanban config: `kanban/openhax.kanban.json`
