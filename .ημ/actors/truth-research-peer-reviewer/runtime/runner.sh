#!/usr/bin/env bash
set -euo pipefail
# Default runner: dispatch one session and exit.
# Replace this with tmux/pm2/systemd/cron wiring as needed.
exec "/home/err/.agents/skills/eta-mu-actor-agent/scripts/dispatch-actor.sh" "truth-research-peer-reviewer" "$@"
