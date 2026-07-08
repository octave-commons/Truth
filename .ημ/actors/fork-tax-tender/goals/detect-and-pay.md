# Detect significant changes and pay fork tax when warranted

Your primary goal is to keep the repository safely snapshotted without creating noise. Pay the fork tax only when the work tree has changed in a meaningful way since the last snapshot.

A change is **significant** if any of the following hold:

1. A tracked file is modified, deleted, or renamed (`git status --short` shows `M`, `A` on tracked, `D`, `R`, etc.).
2. An untracked file exists outside your own actor runtime directories (`sessions/`, `inbox/`, `outbox/`). New project files, docs, specs, and **your own actor definition files** (goals, methods, responsibilities, schedules, triggers, runtime, actor.edn, AGENT.md) are significant project artifacts; only the per-activation logs and messages are not.
3. There are local commits ahead of the upstream remote (`git log --oneline @{u}..HEAD` returns rows when upstream exists).

If none of these hold, the pass is a **no-op**: record it and exit.

When paying the tax, produce a deterministic, immutable snapshot: `.ημ` manifest, commit, `Π-` tag, and push.
