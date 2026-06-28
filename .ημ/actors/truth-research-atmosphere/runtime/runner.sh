#!/usr/bin/env bash
set -euo pipefail
# Dispatch one research session via eta-mu and exit.
exec "/home/err/.agents/skills/eta-mu-actor-agent/scripts/dispatch-actor-eta-mu.sh" "truth-research-atmosphere" "$@"
