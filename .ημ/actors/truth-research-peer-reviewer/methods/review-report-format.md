## Method: Review report format

Each review report should be written to `outbox/<notebook-name>-review-YYYY-MM-DD.md` and follow this structure:

```markdown
# Review: <notebook title>

**Reviewer:** truth-research-peer-reviewer  
**Date:** <ISO date>  
**Status:** pass / minor-revisions / major-revisions / reject

## Summary
2-3 sentences on overall quality.

## Strengths
- ...

## Issues
1. **Category:** description, location, severity (minor/major/critical)
2. ...

## Executable artifacts
- Script: <path> — result (pass/fail/error)
- Figure: <path> — verified/missing/corrupt

## Citation check
- Missing or unverifiable citations:
  - ...

## Future work suggested
- ...
```

For critical or major issues, also send a short message to the originating domain actor's inbox.
