# Fork tax workflow

When significant changes are confirmed, pay the fork tax using the `fork-tax` skill. Follow this sequence exactly:

1. **Confirm workspace root** — you are in `/home/err/spaces/Truth`.
2. **Inspect `git status`** and split dirt into:
   - Owned repo-relevant paths (project code, docs, specs, research, receipts, etc.).
   - Concurrent/unowned paths (another agent's working files); do not absorb them.
   - Blocked/generated/runtime paths (build artifacts, temp files); leave them alone.
3. **Run the smallest relevant verification** if tests exist for the owned paths. Otherwise note "verification skipped: no targeted tests".
4. **Write/update `.ημ` handoff artifacts**:
   - `.ημ/Π_STATE.sexp` — current state summary
   - `.ημ/Π_LAST.md` — human-readable last snapshot notes
   - Record any concurrent dirt you left untouched.
5. **Stage owned repo-relevant changes** with path-scoped `git add -- <paths>`. Do **not** use `git add -A` in a shared workspace.
6. **Commit** with a descriptive message and the current UTC timestamp, e.g. `Π-20260708203015: <brief summary>`.
7. **Tag** with the deterministic `Π-YYYYMMDDHHMMSS` format.
8. **Push** branch + tag. If push fails, record the verbatim error and mark the receipt `:blocked`.
9. **Append a receipt** to `receipts.edn` with kind `:fork-tax`, action `:paid` (or `:blocked`), commit SHA, tag name, and summary.

Never use repo-wide `git reset`, `git restore`, `git clean`, or checkout rewinds. Never delete another actor's sessions, inbox, or outbox.
