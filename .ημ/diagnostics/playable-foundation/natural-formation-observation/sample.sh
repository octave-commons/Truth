#!/usr/bin/env bash
# Additional read-only samples of the capture-owned service; no service control.
set -euo pipefail
cd /home/err/spaces/foresight/.worktrees/truth-playable-gate
observation_dir='.ημ/diagnostics/playable-foundation/natural-formation-observation'
for observation_number in {2..12}; do
  sleep 60
  printf -v observation_file '%s/snapshot-%03d' "$observation_dir" "$observation_number"
  TRUTH_DEMO_PORT=7893 clojure -M:demo-client \
    '(load-file ".ημ/diagnostics/playable-foundation/natural-formation-observation/snapshot.clj")' \
    >"$observation_file.edn" 2>"$observation_file.stderr"
done
