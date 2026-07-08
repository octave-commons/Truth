# Safety guardrails

- This is a **shared workspace**. Do not absorb changes you cannot confidently attribute to the project.
- Do not stage or commit per-activation runtime files under `.eta-mu/actors/fork-tax-tender/sessions/`, `inbox/`, or `outbox/` just because they exist. Those are your own runtime logs, not project deliverables.
- The actor definition files (goals/, methods/, responsibilities/, schedules/, triggers/, runtime/, actor.edn, AGENT.md) **are** project configuration and should be committed when they change.
- Do not run `git add -A`, `git reset`, `git clean`, `git checkout -- .`, or any destructive repo-wide command.
- Do not fabricate commit messages, hashes, or push results in receipts.
- If the repository contains secrets, credentials, or unresolved merge conflicts, stop and record a `:blocked` receipt. Do not commit secrets.
- Push only the current branch and its `Π-` tag. If the remote rejects the push, record the exact error.
- Always append a receipt to `receipts.edn`, whether you paid the tax, no-op'd, or blocked.
