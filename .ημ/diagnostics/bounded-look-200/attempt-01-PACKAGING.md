# Closed bounded-look-200 attempt 01: lossless publication map

This packages **new, previously uncommitted auxiliary evidence only** from the
failed closed attempt 01. Every original remains unchanged locally. It does not
touch the active attempt 02, frozen preparation, game source, board or ledgers.
The [closed audit](closure-audit.md) retains the failure and its limits: four
young-world 200-pixel calibration looks, followed by an operator menu request
while cursor-free was false; no target admission, aim, W, approach or commitment.

## Complete readable evidence

The [mapping](attempt-01-AUXILIARY-MAP.json) accounts for all **335** audit
inputs, **1,384,602 original bytes**: 48 direct files
and 287 packed files. The original audit inventory digest is
`febd9b70981860391a4ead361fbf84c2b88e68191fb28d1ddd1e294b7ba77754`. The two archives are ordinary UTF-8 JSONL:

- [Run auxiliary](attempt-01-run-auxiliary.jsonl): 151 entries.
- [Outer-client auxiliary](attempt-01-client-auxiliary.jsonl): 136 entries.

Each line has the fixed fields `path`, `bytes`, `sha256`, `content`. JSON escaping
preserves newlines, CRLF and empty files; decoding `content` and encoding UTF-8
reconstructs the original bytes. All 109 empty
originals remain represented. No EDN/event content is parsed or reinterpreted.
Every original larger than 8,192 bytes, PNG and non-UTF-8 file stays direct, as
do the state/closure, key provenance and selected failure outputs. Raw snapshot
journals, operations, full game stdout and the PNG remain direct and inspectable.

For a fresh checkout, use each mapping entry's direct `path`, or the named
archive and one-based `line`. Check safe relative paths, unique entries, byte
count and SHA-256 before optional extraction into a **new directory**; never
overwrite existing evidence. The mapping provides `direct_paths` and
`new_direct_paths` explicitly. All 335 audit references resolve through existing
tracked files, those new direct paths and the two archives, with no unaccounted
input. The 186 production references are already tracked and their recorded
hashes were separately checked. The two audit reports and these packaging
artifacts must also accompany publication; they are not part of the historical
335-input digest. Canonical card/ledger records remain direct and outside this map.

The [packaging proof](attempt-01-packaging-proof.json) records independent decode
and exact-byte comparison of every archive member, the direct/packed bijection,
safe paths and original-byte stability. The [saved script](attempt-01-package.py)
shows the transformation; it refuses existing packaging outputs and is not a
runtime or game tool. No originals were deleted or renamed.

Fewer paths do **not** mean fewer review-input bytes or completed review. A native
reviewer must still inspect the full journals, archives, image and relevant
provenance, and disclose any inaccessible input. No file-cap, runtime-budget,
approval, performance or gameplay qualification is claimed by this packaging.
