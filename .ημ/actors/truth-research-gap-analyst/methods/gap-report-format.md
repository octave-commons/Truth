## Method: Gap report format

Each gap report should be written to `outbox/gap-analysis-YYYY-MM-DD.md` and follow this structure:

```markdown
# Research Gap Analysis

**Analyst:** truth-research-gap-analyst  
**Date:** <ISO date>  
**Scope:** docs/research/ + docs/notes/research/

## Coverage summary
| Domain | Notebooks | Status | Derived specs/code |
|---|---|---|---|
| ... | ... | ... | ... |

## Phase coverage
| Phase | Coverage | Gaps |
|---|---|---|
| ... | ... | ... |

## Identified gaps
1. **Topic:** ...
   - **Priority:** high/medium/low
   - **Why it matters:** ...
   - **Suggested actor:** truth-research-<domain>
   - **Deliverable:** `docs/research/<domain>/<slug>.md`
   - **Suggested sources/search:** ...
2. ...

## Cross-link gaps
- Notebook A should reference Notebook B but does not.
- ...

## Recommended next dispatch
The top 3 topics to dispatch next, with actor and deliverable.
```

When a gap is urgent or cross-domain, send a brief message to `truth-research-coordinator` inbox.
