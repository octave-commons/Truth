# Terminal life-account revision correction

Native review 5446390998 / comment 4210309826 identified a supplied-history
contradiction: accepted close revision 2 admitted current closed revision 3.
The accepted-close guard now requires equality, preserving older nonterminal
history, absent-history limits, rejected entries and other-account entries.
No domain transition, accounting arithmetic, ECS or gameplay path changed.

RED commit `5822d2a85b54a520393eb04d508a2d4994186777` observed 35 tests,
457 assertions, exactly 3 failures and no errors. Focused GREEN passed all
35 tests and 457 assertions. The first canonical full gate passed 941 tests /
16,196 assertions but failed formatting; this failure is preserved. One test
indentation correction and the fresh second gate passed the same full suite
and all six strict analysis gates. The second canonical gate admitted Review
in 96.378 seconds. Root verified 304 source pins unchanged and all 16 observed
owned process identities absent after reaping.

`evidence.jsonl` preserves every file in the new review-fix diagnostic tree,
including native finding/review, preparation, RED, both canonical gate attempts,
formatter execution, raw outputs, ownership journals and closure records.
`MAP.json` maps each original path to its exact content hash and archive line.
The original files remain locally intact. The committed `RED-OBSERVED.json`
is retained as the readable failing checkpoint as well as inside the archive.
This package adds no review approval; the corrected head needs fresh review.
