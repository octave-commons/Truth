#!/usr/bin/env bash
set -euo pipefail
capture_dir=$(cd "$(dirname "$0")" && pwd)
printf '%s xdotool' "$(date -u +%Y-%m-%dT%H:%M:%S.%NZ)" >> "$capture_dir/native-inputs.log"
printf ' %q' "$@" >> "$capture_dir/native-inputs.log"
printf '\n' >> "$capture_dir/native-inputs.log"
exec env DISPLAY=:2 xdotool "$@"
