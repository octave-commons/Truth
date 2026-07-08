#!/usr/bin/env bash
set -euo pipefail

# significant-changes.sh
# Exits 0 with a summary if the repo has significant uncommitted or unpushed changes.
# Exits 1 if no significant changes are found.
# Run from the repository root.

WORKSPACE_ROOT="/home/err/spaces/Truth"
cd "$WORKSPACE_ROOT"

# Actor runtime bookkeeping paths to ignore (only per-activation logs/messages).
ACTOR_DIR_REAL=".ημ/actors/fork-tax-tender"
ACTOR_DIR_SYMLINK=".eta-mu/actors/fork-tax-tender"
IGNORE_SUBDIRS=("sessions/" "inbox/" "outbox/")

# Use git status --short -z to capture both staged and unstaged changes without quoting.
mapfile -d '' STATUS_LINES < <(git status --short -z || true)

SIGNIFICANT_FILES=()

for line in "${STATUS_LINES[@]}"; do
  [[ -z "$line" ]] && continue

  # First two bytes are status codes, then a space, then the path.
  index_status="${line:0:1}"
  worktree_status="${line:1:1}"
  # Skip the separating space at position 2.
  file_path="${line:3}"

  # If untracked (both index and worktree are '?'), only count if outside actor bookkeeping.
  if [[ "$index_status" == "?" && "$worktree_status" == "?" ]]; then
    ignored=false
    for sub in "${IGNORE_SUBDIRS[@]}"; do
      if [[ "$file_path" == "$ACTOR_DIR_REAL/$sub"* || "$file_path" == "$ACTOR_DIR_SYMLINK/$sub"* ]]; then
        ignored=true
        break
      fi
    done
    if [[ "$ignored" == false ]]; then
      SIGNIFICANT_FILES+=("$file_path")
    fi
  elif [[ "$index_status" != " " || "$worktree_status" != " " ]]; then
    # Staged or worktree change (modified, added, deleted, renamed, etc.).
    SIGNIFICANT_FILES+=("$file_path")
  fi
done

# Check for unpushed commits if upstream exists.
UNPUSHED_COMMITS=0
if git rev-parse --abbrev-ref --symbolic-full-name '@{u}' > /dev/null 2>&1; then
  mapfile -t UNPUSHED_LINES < <(git log --oneline '@{u}..HEAD' 2>/dev/null || true)
  UNPUSHED_COMMITS=${#UNPUSHED_LINES[@]}
fi

if [[ ${#SIGNIFICANT_FILES[@]} -eq 0 && "$UNPUSHED_COMMITS" -eq 0 ]]; then
  echo "NO_SIGNIFICANT_CHANGES"
  exit 1
fi

echo "SIGNIFICANT_CHANGES"
echo "tracked_or_project_files: ${#SIGNIFICANT_FILES[@]}"
for f in "${SIGNIFICANT_FILES[@]}"; do
  echo "  - $f"
done
echo "unpushed_commits: $UNPUSHED_COMMITS"
exit 0
