#!/usr/bin/env bash
set -euo pipefail
# Run a research actor via OpenCode.
ACTOR_ID="${1:-}"
MESSAGE="${2:-}"
if [[ -z "$ACTOR_ID" ]]; then
  echo "Usage: $0 <actor-id> [message]" >&2
  exit 1
fi
WORKSPACE_ROOT="/home/err/spaces/Truth"
cd "$WORKSPACE_ROOT"
ACTOR_DIR="$WORKSPACE_ROOT/.eta-mu/actors/$ACTOR_ID"
if [[ ! -d "$ACTOR_DIR" ]]; then
  echo "Actor not found: $ACTOR_DIR" >&2
  exit 1
fi
SCRIPT_DIR="/home/err/.agents/skills/eta-mu-actor-agent/scripts"
exec "$SCRIPT_DIR/dispatch-actor.sh" "$ACTOR_ID" "$MESSAGE"
