# Lossless publication of auxiliary native evidence

The closed run and outer clients produced 707 files containing 5,253,176 bytes,
including 272 empty files. Twenty core or larger files remain normal repository
files. The other 687 files are represented without byte changes in two UTF-8
JSONL archives:

- [run-auxiliary.jsonl](run-auxiliary.jsonl): 564 entries.
- [outer-client-auxiliary.jsonl](outer-client-auxiliary.jsonl): 123 entries.

[AUXILIARY-MAP.json](AUXILIARY-MAP.json) gives every logical original path,
length, SHA256, and either its direct location or its archive and one-based line.
All paths are relative to this diagnostic directory. Each archive line is one
schema-version 1 object with `path`, `bytes`, `sha256`, `encoding` and `content`.
Decode the JSON string and encode it as UTF-8 to recover the exact original
bytes, including empty files, CRLF, Unicode and final-newline state. Do not
normalize, strip, or parse/reformat the contained JSON or EDN.

The original local files remain untouched. They had never been committed; this
is their first publication representation. Historical paths in request logs,
controller output and audit input manifests deliberately remain their original
logical paths. Use the map to resolve an archived path rather than rewriting
the historical record. The frozen preparation and canonical Rheos card/ledger
are ordinary files and are not part of these archives.

The raw message/request journals, operation journal, state and closure, process
launch identity, full game output, final snapshot, and aim controller/result
remain directly readable. Every run/client file larger than 8192 bytes also stays
direct. The derived `__pycache__/runner.cpython-312.pyc` is excluded as a generated
cache, not a source or observation; its local bytes were left unchanged.

Root compared the complete input inventory with the direct/packed partition,
rejected duplicate/absolute/traversing paths, strictly decoded and re-encoded
every archived file, checked length/hash/raw bytes against the original, and
rechecked all original hashes after packaging. This verifies storage equivalence,
not the run's correctness. The independent native audit has a separate purpose.

Packaging reduces changed-path count below the included review file ceiling; it
does not reduce semantic input or excuse truncation. The 4.16 MB raw message journal
is still present. A reviewer reporting unread or truncated material has not
qualified complete review merely because fewer paths changed. No review path
exclusion or paid-credit setting was added.
