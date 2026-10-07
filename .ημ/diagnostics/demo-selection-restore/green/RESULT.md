# Scene-switch restoration GREEN

Committed RED d550a516a27f101464e5e9b7894495cba3233f0e establishes four expected settings failures (2 tests/26 assertions,22 pass,0 errors). This candidate changes only dev/demo.clj, SHA256d76a6420a7dfaf4e1d5c9fd96b424229d922d736af21bb4d2355ace0d3444220. Tests remain SHA2562a69586decb6d128ed5eb2c3e76a0d9bdd11a8171bf4209d1a58a29d4a51b7e4.

A private pure helper reuses prepare-formation!'s exact two-key restoration law. select! snapshots configuration after the service guard, catches failure only around enqueue/wait, restores prior tick/on-step key presence and values while retaining unrelated current changes, and rethrows the same Throwable. Queue semantics and successful selection remain unchanged. Already queued replacements are not cancelled; no world/camera rollback or upper timing bound is promised.

Root independently inspected the source; intent_research performed a separate source review without findings. Five sequential subprocesses exited0 and were reaped, last2026-10-07T02:52:40.147903Z. The RED base HEAD and319 tracked source/config hashes (including the candidate) stayed unchanged around every command:

- Demo:9 tests/85 assertions,0 failures/errors, including the unchanged actual30-second timeout.
- Full ordinary suite:948 tests/16,775 assertions,0 failures/errors.
- All6 bin/analyze --strict gates pass. Raw nonblocking structural/duplication findings remain visible; this is not a zero-diagnostic claim.
- Explicit kondo on dev/demo.clj and dev/demo_test.clj:0 errors/warnings.
- Explicit formatting check on those dev files:pass.

The retained native game ran concurrently on its prior source. No native connection/input/restart/reload, GL suite or performance measurement occurred. Raw full-suite stderr retains the existing intentional error-log test. The canonical native verification card remains InProgress3. This closes the bounded review restoration repair, not native gameplay acceptance, hosted review convergence or the larger Gate goal.
