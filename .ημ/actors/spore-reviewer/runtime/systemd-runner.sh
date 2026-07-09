#!/usr/bin/env bash
set -euo pipefail

# systemd-runner.sh
# Synchronous runner for the spore-reviewer actor under systemd.
# Compiles AGENT.md, records a session, runs opencode run synchronously,
# and captures the OpenCode session id.

ACTOR_ID="spore-reviewer"
MESSAGE="Review the incubated skill spores in this project's .eta-mu/session-mycology/spores/ and the ledger .eta-mu/session-mycology/ledger.md. Score each spore on p-recurrence, p-generalizable, and p-worth-promoting. Promote any spore with p-worth-promoting >= 0.8 to a full skill under ~/.agents/skills/<name>/SKILL.md using the standard skill template. Reject spores that are too narrow or stale, updating their frontmatter with a reason. Write a review receipt to .eta-mu/session-mycology/review-receipts.edn. Do not promote a spore during the same session that created it."

# Resolve workspace root.
WORKSPACE_ROOT="/home/err/spaces/Truth"
cd "$WORKSPACE_ROOT"

ACTOR_DIR="$WORKSPACE_ROOT/.eta-mu/actors/$ACTOR_ID"
SKILL_SCRIPTS="/home/err/.agents/skills/eta-mu-actor-agent/scripts"

# Compile the prompt.
"$SKILL_SCRIPTS/compile-prompt.sh" "$ACTOR_ID"

# Create session folder.
SESSION_UUID=$(uuidgen 2>/dev/null || cat /proc/sys/kernel/random/uuid)
TS=$(date -u +%Y-%m-%dT%H-%M-%S)
SESSION_DIR="$ACTOR_DIR/sessions/$TS-$SESSION_UUID"
mkdir -p "$SESSION_DIR"

# Record incoming message.
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

# Initial session metadata.
CREATED_AT="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
cat > "$SESSION_DIR/session.edn" <<EOF
{:session/id "$SESSION_UUID"
 :session/created-at "$CREATED_AT"
 :session/actor-id "$ACTOR_ID"
 :session/status :running
 :session/opencode-session-id nil
 :session/directory "$WORKSPACE_ROOT"
 :session/message-file "$PAYLOAD_MSG_FILE"}
EOF

# Install the compiled prompt as an OpenCode agent.
AGENT_INSTALL_DIR="$HOME/.config/opencode/agent"
mkdir -p "$AGENT_INSTALL_DIR"
cp "$ACTOR_DIR/AGENT.md" "$AGENT_INSTALL_DIR/$ACTOR_ID.md"

# Read password from systemd unit file.
PASSWORD=$("$SKILL_SCRIPTS/read-server-password.sh" opencode-server.service)
export OPENCODE_SERVER_PASSWORD="$PASSWORD"

PAYLOAD="Your session folder is $SESSION_DIR. Your actor inbox is at $ACTOR_DIR/inbox/. There is a command message waiting at $ACTOR_DIR/inbox/$TS-$SESSION_UUID.md . $MESSAGE"

OPENCODE_OUT="$SESSION_DIR/opencode-run.log"

# Run synchronously so systemd can wait/restart on failure.
opencode run \
  --attach http://127.0.0.1:8097 \
  --agent "$ACTOR_ID" \
  "$PAYLOAD" > "$OPENCODE_OUT" 2>&1

# Capture the session id from the log.
OC_SESSION_ID=$(grep -oE '[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}' "$OPENCODE_OUT" 2>/dev/null | head -n1 || true)
OC_SESSION_ID="${OC_SESSION_ID:-unknown}"

# Update session metadata.
cat > "$SESSION_DIR/session.edn" <<EOF
{:session/id "$SESSION_UUID"
 :session/created-at "$CREATED_AT"
 :session/actor-id "$ACTOR_ID"
 :session/status :completed
 :session/opencode-session-id "$OC_SESSION_ID"
 :session/directory "$WORKSPACE_ROOT"
 :session/message-file "$PAYLOAD_MSG_FILE"
 :session/opencode-run-log "$OPENCODE_OUT"}
EOF

echo "Completed actor: $ACTOR_ID"
echo "Session folder:  $SESSION_DIR"
echo "OpenCode session: $OC_SESSION_ID"
