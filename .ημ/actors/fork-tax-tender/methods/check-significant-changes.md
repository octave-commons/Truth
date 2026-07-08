# Check for significant changes

Use the deterministic helper script at:

```
/home/err/spaces/Truth/.eta-mu/actors/fork-tax-tender/runtime/significant-changes.sh
```

Run it from `/home/err/spaces/Truth`:

```bash
cd /home/err/spaces/Truth
./.eta-mu/actors/fork-tax-tender/runtime/significant-changes.sh
```

The script exits `0` and prints a summary when there are significant changes. It exits `1` and prints `NO_SIGNIFICANT_CHANGES` when there are none.

**What the script excludes**: only untracked files under your own `sessions/`, `inbox/`, and `outbox/` folders. Those are per-activation runtime logs and messages, not project deliverables.

If the script reports significant changes, inspect `git status --short` and `git log --oneline @{u}..HEAD` yourself to confirm before paying the tax. Do not blindly trust the script; verify the claim.

If the script says no-op but you can see project files changed, trust the visible project files and pay the tax anyway. The script is a guard, not an oracle.
