#!/usr/bin/env bash
set -euo pipefail
# Polls inbox for this actor. Run this in tmux/screen/systemd for a long-lived actor.
# Usage: $ACTOR_DIR/runtime/poll-inbox.sh <session-dir>
exec "$SKILL_SCRIPTS/poll-inbox.sh" "$ACTOR_ID" "${1:-}"
