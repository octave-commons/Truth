#!/usr/bin/env bash
set -euo pipefail
# Polls inbox for this actor. Run this in tmux/screen/systemd for a long-lived actor.
# Usage: /home/err/.eta-mu/actors/spore-reviewer/runtime/poll-inbox.sh <session-dir>
exec "/home/err/.agents/skills/eta-mu-actor-agent/scripts/poll-inbox.sh" "spore-reviewer" "${1:-}"
