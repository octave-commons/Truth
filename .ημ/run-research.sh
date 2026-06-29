#!/usr/bin/env bash
set -euo pipefail
ACTOR_ID="${1:-}"
MESSAGE="${2:-}"
[[ -z "$ACTOR_ID" ]] && exit 1

WORKSPACE_ROOT="/home/err/spaces/Truth"
# Source env
if [[ -f "$WORKSPACE_ROOT/.eta-mu/.env" ]]; then
  while IFS="=" read -r key val; do
    [[ -z "$key" || "$key" == \#* ]] && continue
    export "$key=$val"
  done < "$WORKSPACE_ROOT/.eta-mu/.env"
fi
cd "$WORKSPACE_ROOT"
ACTOR_DIR="$WORKSPACE_ROOT/.eta-mu/actors/$ACTOR_ID"
[[ ! -d "$ACTOR_DIR" ]] && exit 1

SCRIPT_DIR="/home/err/.agents/skills/eta-mu-actor-agent/scripts"
"$SCRIPT_DIR/compile-prompt.sh" "$ACTOR_ID"

SESSION_UUID=$(cat /proc/sys/kernel/random/uuid)
TS=$(date -u +%Y-%m-%dT%H-%M-%S)
SESSION_DIR="$ACTOR_DIR/sessions/$TS-$SESSION_UUID"
mkdir -p "$SESSION_DIR"

PAYLOAD_MSG_FILE=""
if [[ -n "$MESSAGE" ]]; then
  PAYLOAD_MSG_FILE="$SESSION_DIR/turn-001-in.md"
  cat > "$PAYLOAD_MSG_FILE" <<EOF
---
from: user
to: $ACTOR_ID
session: $SESSION_UUID
kind: command
---

$MESSAGE
EOF
  cp "$PAYLOAD_MSG_FILE" "$ACTOR_DIR/inbox/$TS-$SESSION_UUID.md"
fi

PROMPT="Your session folder is $SESSION_DIR. Your actor inbox is at $ACTOR_DIR/inbox/."
if [[ -n "$MESSAGE" ]]; then
  PROMPT="$PROMPT There is a command message at $ACTOR_DIR/inbox/$TS-$SESSION_UUID.md. $MESSAGE"
fi

ETA_MU_OUT="$SESSION_DIR/eta-mu-run.log"

cat > "$SESSION_DIR/session.edn" <<EOF
{:session/id "$SESSION_UUID"
 :session/created-at "$(date -u +%Y-%m-%dT%H:%M:%SZ)"
 :session/actor-id "$ACTOR_ID"
 :session/status :running
 :session/dispatch :eta-mu
 :session/directory "$WORKSPACE_ROOT"
 :session/message-file "${PAYLOAD_MSG_FILE:-}"
 :session/eta-mu-run-log "$ETA_MU_OUT"}
EOF

echo "Starting research: $ACTOR_ID"

# Run eta-mu with explicit API key
ZAI_API_KEY="a94d9d29d3e94019b876bad65eda6cfc.MtwYcjQcx5P1mozr" \
eta-mu -p --no-session ${ETA_MU_FLAGS:-} \
  --append-system-prompt "$ACTOR_DIR/AGENT.md" \
  "$PROMPT" > "$ETA_MU_OUT" 2>&1

echo "Research complete: $ACTOR_ID"
