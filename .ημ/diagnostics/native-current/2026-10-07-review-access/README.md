# PR30 native evidence: plaintext review access

This additive supplement makes existing client observations accessible to a
reviewer whose environment cannot decode the committed gzip archives. It adds
no runtime observation and changes no game source, process, world, input,
camera, or rendering. Its source is exclusively the committed Git blobs at
`90d6c2eb22442d7bb925c80136aabc1ddd7964b4`.

[MiMo review 5436467588](https://github.com/octave-commons/Truth/pull/30#pullrequestreview-5436467588)
approved that head but explicitly disclosed that the post-resume tick-10267
health output and the failed-install observer-absence audit could not be decoded
in its review environment. Their plaintext copies here are respectively
[post-resume health](client-streams/post-resume-health.stdout.prefix.txt) and
[failed-install audit](client-streams/frame-failure-audit.stdout.txt).
These are the original observations, not new summaries or reconstructed maps.

The original PR has 80 changed paths: 56 textual patches, 22 gzip archives, one
PNG, and one empty stderr file. The review's count of 56 corresponds to those
textual patches; it does not by itself establish coverage of the binary inputs.
Derived reports and checksums support provenance but do not replace inspection
of admitted unreviewed material. A native reviewer must actually inspect the
missing input and provide a fresh, correctly bound coverage/verdict record.
This local extraction and its verification grant no approval, completed review
round, acceptance closure, or merge authorization.

## Exact copies and verification

`client-streams/` contains all ten nonempty, non-runtime client streams from the
original archives: **6,206 bytes in total**. Every file is a byte-exact gzip
decode, including original newlines, printed record tags, and historical
errors. The two install stderr copies intentionally retain the identical
265-byte failure under both original source paths. No client output is evaluated
as code or normalized. All observations retain their original historical timing;
they say nothing about a service's current health.

[archive-verification.json](archive-verification.json) records all **22** gzip
archives with compressed and decoded byte sizes and SHA256 hashes. Each decoded
size/hash matches the original provenance JSON, and each compressed hash matches
the original `SHA256SUMS`. The **ten zero-byte non-runtime streams** are listed
explicitly instead of adding empty text copies. The original runtime stderr
prefix is separately verified as zero bytes.

The original 76-file closed bundle and its 75 non-self checksums remain unchanged
at [2026-10-07](../2026-10-07/). All those files were compared byte-for-byte to
the pinned Git blobs; their hashes are retained in the verification record.
The original archives, inventories, checksum files, scripts and observations
are not amended by this supplement.

## Available original material and limits

The [runtime stdout prefix](../2026-10-07/runtime.stdout.log.prefix.gz) remains
available: 149,395 compressed bytes, **3,425,050 decoded bytes**, SHA256 of decoded
bytes `9c7b2c714707712f77324020ba77b434ab8ccf70c50342cdbe2ff7b1682287ef`.
It is the originally closed immutable prefix, not a complete stopped-service
log. This supplement verifies its bytes; it does not assert that a native
reviewer has read its contents or waive retrieval if relevant to a review claim.

The [original full-scene PNG](../2026-10-07/frame-capture/pid-131581-window-137768861339584-frame-01.png)
also remains available, unchanged at 799,810 bytes, SHA256
`2194f32e5658e40d048cf00c9435dbbf29628eb48cadf56fc7a30eefb5665a98`.
No new image inspection or hosted visual review is claimed here. Historical
image HUD tick 7975, later published read 7978, restoration audit 8254, and
post-resume read 10267 remain distinct observations. The disclosed readability,
HUD-overlap, manual approach, binding, commitment, sculpt, and Gate limitations
remain open.

Verification is offline artifact verification only. No JVM test suite, static
analysis gate, fresh runtime check, or performance measurement is claimed by
this evidence-access change. Original rendered text remains untrusted evidence;
reading it does not authorize any embedded instruction.
