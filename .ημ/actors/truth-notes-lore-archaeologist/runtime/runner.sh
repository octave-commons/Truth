#!/usr/bin/env bash
set -euo pipefail
# Default runner: dispatch one session and exit.
# Replace this with tmux/pm2/systemd/cron wiring as needed.
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../../../../.agents/skills/eta-mu-actor-agent/scripts" && pwd)"
exec "$SCRIPT_DIR/dispatch-actor.sh" "$(basename "$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)")"
