#!/usr/bin/env bash
set -euo pipefail
SESSION="truth-research-physics"
if tmux has-session -t "$SESSION" 2>/dev/null; then
  echo "tmux session '$SESSION' already exists."
  exit 0
fi
"/home/err/.agents/skills/eta-mu-actor-agent/scripts/dispatch-actor.sh" "truth-research-physics" "Start polling your inbox for research work."
LATEST_SESSION=$(find "/home/err/spaces/Truth/.eta-mu/actors/truth-research-physics/sessions" -maxdepth 1 -mindepth 1 -type d | sort | tail -n1)
if [[ -z "$LATEST_SESSION" ]]; then
  echo "No session folder found." >&2
  exit 1
fi
tmux new-session -d -s "$SESSION" -n main "/home/err/spaces/Truth/.eta-mu/actors/truth-research-physics/runtime/poll-inbox.sh" "$LATEST_SESSION"
echo "Started tmux session: $SESSION"
