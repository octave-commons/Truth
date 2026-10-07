# Lossless life-kernel evidence publication

This package represents **143 new diagnostic originals / 1,048,832 bytes**:
22 direct originals and 121 packed originals. Every original remains
byte-for-byte unchanged locally. The 66 paths already changed against the
actual PR38 base are preserved as normal Git history; no old path is packed,
deleted or rewritten to reduce this count. Authorized source/card/ledger changes
remain direct. Historical manifests are kept unchanged and resolve their packed
original paths through this map.

[AUXILIARY-MAP.json](AUXILIARY-MAP.json) is a complete disjoint partition.
Each packed entry identifies its original repository-relative path, byte count,
SHA256, one-based JSONL line and UTF-8 record byte span in
[auxiliary.jsonl](auxiliary.jsonl). Decode the `content` field and encode it as
UTF-8: newlines, Unicode, trailing whitespace and 16 empty files survive
exactly. Nothing is summarized, truncated or omitted from the archived evidence.
[packaging-proof.json](packaging-proof.json) records the verified byte equality
for every original and the [one-shot script](package-evidence.py) records how it
was produced. The script does not run tests, call Rheos, mutate Git, or touch
source/runtime state.

All new raw diagnostics are either direct or packed, including complete failed
and successful logs, commands, process observations, source snapshots, canonical
readbacks, and receipt append evidence. Human-readable results, final gate logs,
key proofs and original manifests are direct for convenient review. A historical
path appearing only in the archive is retrievable evidence, not a broken/omitted
input. An extractor must use a new empty destination and validate each byte count
and hash; never overwrite an existing original.

[STAGING.txt](STAGING.txt) contains exactly 37 proposed GREEN-commit paths:
8 already tracked modifications plus 29 new paths.
The cumulative diff against `98b847ce75491bdccacf2ec3a7476d451270bf49` contains **95 paths**. This is a
representation decision only: it does not claim a particular hosted reviewer
actually consumed every record or confer review approval. The existing upstream
MiMo caller still has a 45-minute review job; no 120-minute option was added.
[SHA256SUMS](SHA256SUMS) hashes every staged path except itself. The actual Git
diff byte count is reported separately after staging to avoid a self-referential
manifest. A fresh hosted review must still establish complete input coverage.
