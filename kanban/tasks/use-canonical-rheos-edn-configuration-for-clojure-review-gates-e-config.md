---
category: "tasks"
labels: "hygiene, workflow"
type: "task"
write-id: "1791313457394-0.r0zo9wehyumt5etxdi"
points: "2"
title: "Use canonical Rheos EDN configuration for Clojure review gates"
priority: "P1"
status: "review"
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

Canonical gated review transition completed on source revision a3609f6931951f8e011950ec5eab0a24265e93cd. Rheos actually executed clojure -M:test: 899 tests, 15635 assertions, 0 failures, 0 errors; then bin/analyze --strict: exit 0, no blocking findings. Move in_progress to review exited 0. Exact log: .ημ/diagnostics/playable-foundation/rheos-review-config-a3609f6.log. CLI SHA256 c16255ab69e158d34536d9c8c72ad1234f3b0abc41e0408a1a270c167dca141d; config SHA256 15aaca2bdd649d2c354e43d87ca944ab01452c616cbee2e85bdf42f7ffb8c262. This is fresh canonical gate evidence, not reuse of prior suite output. Hosted review and final done policy still apply.

Fresh published-head qualification requested by PR8 review: exact detached 56f2a19d02085b3f2e86c9926b837bc990859ef8 remained clean before/after clojure -M:test (899 tests,15635 assertions,0 failures/errors) and bin/analyze --strict (exit0,no blocking findings). Logs:.ημ/diagnostics/playable-foundation/full-suite-56f2a19.log SHA25676d2039b672d411bdc61c8e97503f1966f29ac6cd3228c7d6e1e89c057e85427; strict-56f2a19.log SHA2566e415e131ded633e27f95d1144374b02868d69224bb06c10950cc8d8c204ab61. This dated evidence supplements the earlier a3609f6 gated move; append-only transition history is preserved. Later PR heads require their own hosted checks and review evidence.
---