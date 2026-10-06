# Focus-offset boundary: observed RED

Production is unchanged at `b402939931575e377ae4409d9cd4061dff11df06`.
The real host-loop command in `command.json` completed on 2026-10-06 at
23:33:05.875555 UTC, exit 1: **11 tests, 164 assertions, 23 failures, 0 errors**.
The bounded JVM used `-J-Xms256m -J-Xmx2g`; PID 4149723 was reaped. Its duration
is operational evidence, not isolated performance data; a retained native
service could run concurrently under root coordination.

Five new tests extend the existing six cadence regressions. They exercise the
actual `sim-loop`, production `IntentAtom` queue and guarded application. New
boundary cases use identity/logical tick advancement to isolate attention
validation; the existing no-render flight tests retain the actual physical
integrator and binding fan-out. There is no native input or world injection.

Observed failures:

- Nine malformed/nonfinite offsets enter `player/focus-follow` before rejection.
- Three nonfinite offsets publish invalid attention coordinates; the separate
  corrected-setting test also observes the initially corrupted snapshot.
- Nine malformed cases lack a named `focus-offset` boundary error, as does the
  initial invalid iteration in the recovery test. Four coordinates are silently
  truncated by destructuring; nonfinite arithmetic does not throw.

These account for all 23 failures. The six existing cadence tests, finite list
and vector coordinates, absent-key zero default, queued-input preservation for
throwing inputs, continued tick publication, later valid-setting recovery and
unaffected tracking mode provide passing controls. No missing namespace/symbol
or test-runner failure supplies RED.

`git diff -- src` is empty. The test file SHA256 before and after execution is
`f8e187222867e61ea4679bfa08dbf65da70a1e9cf7031ef03aa8b9f7e374edc1`.
Raw log SHA256:
`2e6421ab6c4872cb553ebd184a5e96e52aefaa7db2229893bfe0c0d9c924a4eb`.
Touched clj-kondo and diff-whitespace results are recorded in `static.json`.
No cljfmt, full suite, strict suite, benchmark, native acceptance or hosted
approval is claimed by this checkpoint.

The existing card remains InProgress / 3. Canonical Rheos appended a current
implementation statement explicitly superseding the historical frozen-focus
and render-enqueue descriptions, preserving their original evidence. Parent
ownership covers review settlement and checkpoint publication. GREEN remains
held until the root commits this observed RED.

Independent source review by board_scout found no blocking test/contract issue:
the actual host boundary, full drained-world preservation, explicit nil versus
absent default, finite sequential controls, recovery and tracking are covered.
That reviewer did not rerun tests. GREEN review must verify that the named
Malli validator is actually used at the host call; these behavioral regressions
deliberately do not encode that implementation detail.
