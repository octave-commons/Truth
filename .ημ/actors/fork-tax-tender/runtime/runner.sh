#!/usr/bin/env bash
set -euo pipefail

# Runner for fork-tax-tender.
# Dispatches a one-shot OpenCode session that checks for significant changes
# and pays the fork tax if needed.

exec "/home/err/spaces/Truth/.eta-mu/actors/fork-tax-tender/runtime/systemd-runner.sh"
