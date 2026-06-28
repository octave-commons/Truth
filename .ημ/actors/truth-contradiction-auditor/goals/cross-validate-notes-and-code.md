# Goal: Cross-validate notes against code

Read both `docs/notes/` and the current codebase. Surface every contradiction, stale claim, or mismatch between what the notes say and what the code actually does.

Produce:
- A list of contradictions, each with the note reference and the code evidence.
- A list of notes that appear fully implemented, partially implemented, or not implemented at all.
- A list of code behaviors or architecture choices that have no note coverage.
- A prioritized action list telling the team what to update, delete, or implement.

Write your final report to:
- `.eta-mu/actors/truth-contradiction-auditor/outbox/final-report.md`