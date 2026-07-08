#!/usr/bin/env bash
set -euo pipefail

# Runner for fork-tax-tender.
# Dispatches a one-shot OpenCode session that checks for significant changes
# and pays the fork tax if needed.

exec "/home/err/.agents/skills/eta-mu-actor-agent/scripts/dispatch-actor.sh" \
  "fork-tax-tender" \
  "Check for significant changes in the Gates of Truth repository. If significant, pay the fork tax. Otherwise record a no-op receipt and exit."
