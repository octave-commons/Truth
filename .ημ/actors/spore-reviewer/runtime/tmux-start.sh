#!/usr/bin/env bash
set -euo pipefail
# Starts a long-lived tmux session for spore-reviewer that polls its inbox.
SESSION="spore-reviewer"
if tmux has-session -t "$SESSION" 2>/dev/null; then
  echo "tmux session '$SESSION' already exists."
  exit 0
fi
# First dispatch creates the session folder and OpenCode session.
"/home/err/.agents/skills/eta-mu-actor-agent/scripts/dispatch-actor.sh" "spore-reviewer" "Start polling your inbox for work."
# Find the most recent session folder.
LATEST_SESSION=$(find "/home/err/.eta-mu/actors/spore-reviewer/sessions" -maxdepth 1 -mindepth 1 -type d | sort | tail -n1)
if [[ -z "$LATEST_SESSION" ]]; then
  echo "No session folder found." >&2
  exit 1
fi
# Attach a watcher to that session.
tmux new-session -d -s "$SESSION" -n main "/home/err/.eta-mu/actors/spore-reviewer/runtime/poll-inbox.sh" "$LATEST_SESSION"
echo "Started tmux session: $SESSION"
