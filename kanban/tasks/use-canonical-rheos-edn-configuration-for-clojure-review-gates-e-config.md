---
category: "tasks"
labels: "hygiene, workflow"
type: "task"
write-id: "1791310696346-0.4szbbl558no3e15695"
points: "2"
title: "Use canonical Rheos EDN configuration for Clojure review gates"
priority: "P1"
status: "in_progress"
uuid: "rheos-clojure-build-gate-config"
created_at: "2026-10-06T18:16:03.338Z"
---

# Use the canonical Rheos Clojure build gate

## Outcome

Truth's in_progress to review transition invokes the repository's Clojure tests
and strict static analysis through the upstream Rheos engine.

## Scope

- Replace the deprecated JSON board config with one EDN config using the
  supported Promethean overlay and a config-relative working directory.
- Configure `clojure -M:test` followed by `bin/analyze --strict`.
- Document the required EDN-capable canonical CLI, the verified local artifact
  hash, and direct CLI commands. Preserve task identity, ledger and FSM law.
- This is configuration hygiene; no simulation feature or upstream law change.

## Non-goals

- No repository-local board parser, validator, wrapper, alternate workflow,
  global package upgrade, gate bypass, or changes to other repositories.
- No status promotion before the exact source revision clears its gates.

## Acceptance criteria

- Canonical CLI explicitly loads and auto-discovers the same EDN config.
- Existing board cards and columns remain visible after config migration.
- The upstream Promethean overlay preserves its transition/WIP semantics and
  runs the two real Truth commands from the config directory.
- A failing command refuses a transition with nonzero exit and unchanged card
  and ledger; a passing command admits it. Isolated upstream artifact evidence
  already proves this mechanism; actual Truth transitions await root's tests.

## Verification

Upstream canonical CLI: `/home/err/spaces/eta-mu/packages/rheos/dist/cli.cjs`,
SHA256 `c16255ab69e158d34536d9c8c72ad1234f3b0abc41e0408a1a270c167dca141d`.
Isolated EDN probe walked every forward intake hop. Its failing Clojure command
returned exit 3 without changing card/ledger hashes or executing a later
sentinel command. Its passing Clojure assertion admitted review. The existing
compiled upstream suite ran 138 tests / 476 assertions with zero failures or
errors; it warned that source-map-support is unavailable. These are upstream
capability checks, not a claim that Truth's full gates already passed.

---
Implemented the scoped config hygiene: sole root openhax.kanban.edn extends canonical Promethean, runs clojure -M:test then bin/analyze --strict, resolves cwd from config directory. Removed JSON to prevent competing/default ambiguity. AGENTS and PROCESS document direct EDN-capable upstream CLI, verified artifact SHA256, and no local substitute. Canonical read-board output before migration, explicit EDN, and automatic EDN discovery compare byte-identical (SHA256 b88e967f455f6039d4fac522f0c80e1e13dc57a61993608a3f468b074b7b8ccb). Preserved actual isolated command pass/fail logs, unchanged-card/ledger manifests and runtime provenance at .ημ/diagnostics/playable-foundation/rheos-build-gate/. No actual Truth review move yet: root owns exact-revision full tests/strict lane; this card remains in_progress until those transitions can run lawfully.
---