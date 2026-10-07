# Historical preparation packaging

Publication uses `PUBLICATION-FILES.txt`: 28 paths, comprising22 direct frozen
paths plus six packaging artifacts. The original69-path preparation manifest
`SHA256SUMS` remains byte-identical (`40abcd2d7c209c5545a25023371e3cfe8e36ce3df3034ddef7c11e2818adec81`).
All originals remain locally available. No source, test, PLAN, pin or old evidence
was changed to package the history.

`historical-preparation.jsonl` contains47 historical paths/428030 original bytes.
Its452290 bytes encode one JSON object per original file, including exact UTF8 text,
original path, byte length and SHA256. `HISTORY-MAP.json` records each row's byte
start/end, and the partition into22 direct and47 packed paths. Every encoded text
round-tripped to the original bytes, including whitespace and failed output.
`package-history.py` is the one-shot filesystem-only program actually used;
`packaging-proof.json` records its result. It is not a runtime launch command.

Current runnable sources, tests, PLAN, flat preparation pins, copy/static proof
and the final passing `green-qualified/` logs stay direct. Packed material includes
all earlier REDs and intermediate qualification attempts, including unsuccessful
ones. This is a publication representation, not deletion or replacement of those
observations. The initial and amended scope records remain at their existing
linked canonical locations in the parent diagnostic directory.

A fresh checkout has each historical path either directly or in the map. To audit
an absent historical path, read its mapped JSONL byte span, decode JSON, encode
its `utf8` field as UTF8, and compare byte length and SHA256 with both the row and
original frozen manifest. Do not claim `sha256sum -c SHA256SUMS` succeeds on a
fresh checkout without reconstructing the packed paths in a separate audit
location. The runtime's flat `preparation-hashes.json` references only direct
files and needs no unpacking. No historical attempt is to be rerun as a result
of this packaging.
