# Closed attempt03 lossless publication map

The physical cadence experiment failed before thrust; its closed evidence remains complete. See [the offline audit](attempt-03-audit.md). This package reduces auxiliary path count by storing small original files as plain UTF-8 JSONL records. It does not summarize, normalize, evaluate or discard their bytes, and it gives no native-review credit.

The explicit [input inventory](attempt-03-publication-inputs.json) names 594 originals (6,111,533 bytes): all450 files in bounded-look-200/runs/attempt-03, all133 outer-client files, seven named adjacent release/calibration/poller/scope records, a byte-exact copy of the external root closure catalog, and the three new offline audit files. The [map](attempt-03-AUXILIARY-MAP.json) forms a disjoint complete partition: 32 direct originals and 562 packed originals. All221 empty outputs are represented explicitly.

Every original above8192 bytes, every non-UTF8 original and the PNG remains direct. Core raw journals, identity/state/closure, controller/result files and named provenance are also direct irrespective of size. No original local file was deleted or changed. No prior committed archive, preparation file, board card, event ledger or receipt is rewritten or packaged.

## Reading a packed original

Look up its exact worktree-relative path in the map. A packed row gives archive, one-based line number and exact UTF-8 byte offsets. Decode that JSONL record, then encode its `text` field as UTF-8; the resulting byte count and SHA256 must equal the row. The fixed record fields are `path`, `bytes`, `sha256`, `encoding` and `text`. Empty output is `text: ""`. This is content storage, not a conversion of EDN, event or board semantics. A fresh checkout needs no unpacking to read these records and can reconstruct each file independently without executing it.

The archive totals are 433 run entries and 129 outer-client entries. All583 source paths in the offline audit resolve through this map. The root closure catalog's original absolute location, exact hash and local copied path are retained. Existing frozen preparation references remain ordinary committed files and are individually hash-checked here; publication ancestry comes from the root catalog, not a new Git/API query.

## Verification and publication

[The proof](attempt-03-packaging-proof.json) records exact roundtrip, safe paths, fixed schema, unique complete partition, original hash preservation and reference checks. The saved [packager](attempt-03-package.py) consumes the explicit inventory and refuses to overwrite package outputs. Its first invocation stopped on duplicate preparation-reference bookkeeping after producing only the two archives. [That failed invocation](attempt-03-package-attempt1.json) preserves its original source and archive hashes; the repaired pass deduplicates identical references and reuses only byte-identical archives. Its limited directory enumeration only checks that the two named closed trees match that inventory; it never selects arbitrary neighboring files.

`attempt-03-PUBLICATION-FILES.txt` is the exact direct-original plus package-artifact staging list; `attempt-03-SHA256SUMS` hashes every path in it except the checksum file itself. Packed originals must not also be staged separately. Root will verify this mapping independently, stage it, and add canonical card/ledger/provenance paths separately. No Git, provider, JVM or native call occurs in this packaging step. All original review material remains available as plain text or its direct PNG; actual reviewer coverage must still be assessed honestly.
