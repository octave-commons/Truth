#!/usr/bin/env bash
set -euo pipefail
# Dispatch one research session via OpenCode and exit.
exec "/home/err/.agents/skills/eta-mu-actor-agent/scripts/dispatch-actor.sh" "truth-research-culture" "$@"
