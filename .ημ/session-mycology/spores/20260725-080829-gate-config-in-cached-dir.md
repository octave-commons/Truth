---
status: incubating
created: 2026-07-25T13:08:29.706929847Z
source-session: /home/err/spaces/Truth
source-task: Promoted 6 static-analysis tools from advisory to blocking
p-efficiency: 0.5
p-friction: 0.5
p-skill-candidate: 0.85
promoted-to: ""
rejected-reason: ""
---

## Problem
A newly-created gate config (.lsp/config.edn) lived in a directory the repo gitignores wholesale (.lsp/), so it was green locally and would have been absent in CI -- turning a just-promoted blocking check permanently red

## Pattern
Before promoting any analysis tool to blocking, run git check-ignore -v on its config file. Tool configs conventionally live in dot-directories that also hold caches, and the usual .gitignore entry is the whole directory

## Candidate skill outline
- Name suggestion
- Trigger phrases
- Key steps or rules
- Anti-patterns to avoid

## Better path
Add the config, then immediately: git check-ignore -v <config>; if ignored, narrow the rule to the cache subpath (dir/* + !dir/config) rather than the directory. Then verify with git add -n

## Receipt refs
- none
