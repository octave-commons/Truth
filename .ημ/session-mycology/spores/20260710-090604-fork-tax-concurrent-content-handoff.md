---
status: rejected
reviewed: 2026-07-11T04:17:45Z
reviewer-session: d1353e32-3b03-4879-ad0b-5446092b7212
created: 2026-07-10T09:06:04Z
source-session: 2026-07-10T08-54-05-1adee44c-4971-4b8c-869c-b45674a18dd6
source-task: Pay fork tax on significant changes
p-efficiency: 0.6
p-friction: 0.7
p-skill-candidate: 0.8
p-recurrence: 0.45
p-generalizable: 0.45
p-worth-promoting: 0.45
promoted-to: ""
rejected-reason: "Too narrow: specific to the eta-mu fork-tax-tender workflow and concurrent shared-workspace modifications. The HEAD-drift handling is useful in that context but does not generalize to a standalone skill."
---

## Problem
In a shared workspace, another agent or process can commit the bulk of the project changes while the fork-tax-tender is still verifying or generating handoff artifacts. The original .ημ artifacts become stale (wrong parent head, wrong changed-files list) and the snapshot would misrepresent the repository state.

## Pattern
1. After any verification step, re-read `git rev-parse HEAD` to detect drift.
2. If the HEAD moved, re-run the smallest relevant verification against the new HEAD.
3. Regenerate `.ημ/Π_STATE.sexp`, `.ημ/Π_LAST.md`, and `.ημ/Π_MANIFEST.sexp` using the new HEAD as the parent.
4. If the content commit and handoff commit are separate, tag the content commit with its own timestamp tag and the handoff commit with the current activation timestamp tag.
5. Push `main` and all tags.
6. Leave any changes that appeared *after* the new content commit untouched for the next fork-tax check.

## Better path
Always treat verification/manifest generation as a second phase and re-check HEAD before staging. Use `git -c core.quotepath=false` for any git command that emits paths containing `.ημ`/Unicode characters. Keep the content commit separate from the handoff-artifact commit so each has a clear, deterministic tag.

## Receipt refs
- 2026-07-10T09:06:04Z fork-tax receipt
- Content commit 56da23a, handoff commit 054249e
