#!/usr/bin/env bash
set -euo pipefail
# Polls inbox for truth-research-geology and dispatches via OpenCode for each new message.
# Run in tmux/screen/systemd for a long-lived actor.
ACTOR_ID="truth-research-geology"
ACTOR_DIR="/home/err/spaces/Truth/.eta-mu/actors/$ACTOR_ID"
INBOX="$ACTOR_DIR/inbox"
PROCESSED=0

echo "Polling inbox for $ACTOR_ID..."
while true; do
  for msg in "$INBOX"/*.md; do
    [[ -f "$msg" ]] || continue
    msg_name=$(basename "$msg")
    echo "[$(date -u +%Y-%m-%dT%H:%M:%SZ)] Processing: $msg_name"
    body=$(sed '1,/^---$/d' "$msg" | sed '1,/^---$/d')
    "/home/err/.agents/skills/eta-mu-actor-agent/scripts/dispatch-actor.sh" "$ACTOR_ID" "$body"
    mv "$msg" "$msg.done"
    PROCESSED=$((PROCESSED + 1))
    echo "  Dispatched. Total processed: $PROCESSED"
  done
  sleep 60
done
