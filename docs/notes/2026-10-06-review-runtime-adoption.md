# Consume the upstream review runtime

Truth's caller now uses
[`09a4454480baa67f6fdc40f6f73f5e48ef0457d1`](https://github.com/open-hax/eta-mu/blob/09a4454480baa67f6fdc40f6f73f5e48ef0457d1/.github/workflows/opencode-code-review.yml)
and supplies its required `pr_head_sha` from the native pull-request event.
The only executable changes are that immutable pin and the required input in
`.github/workflows/eta-mu-review.yml`. Card: `review-runtime-upstream-diagnostics`.

## Observed failure and upstream provenance

[Truth PR8 run 37512668785](https://github.com/octave-commons/Truth/actions/runs/37512668785)
passed review-context verification, publisher tests, credential presence checks,
and CLI installation, then exited 1 inside OpenCode. Its response artifact
`11434369281` contained a zero-byte `model-response.txt`. The previous upstream
pin redirected stderr into an unretained runner file. These observations do not
identify a provider, authentication, quota, or model-catalog cause and do not
constitute a completed review.

[Upstream PR342](https://github.com/open-hax/eta-mu/pull/342) merged at
2026-10-06T16:55:48Z as the GitHub-verified commit above. Its head was
`c79628021aeae967eefd3783df42591ffd7e5a25`; inspection found 17 passing checks,
zero unresolved threads, and exact-head approval evidence:
[MiMo native review 5431181028](https://github.com/open-hax/eta-mu/pull/342#pullrequestreview-5431181028),
[CodeRabbit coverage/verdict](https://github.com/open-hax/eta-mu/pull/342#issuecomment-6017276581)
and its [completed review reply](https://github.com/open-hax/eta-mu/pull/342#issuecomment-6021013550).
CodeRabbit's evidence is native completion plus coverage, not a fabricated
REST `APPROVED` record.
The current canonical PR-flow query reports zero completed rounds against its
five-round default, without Codex approval. That query is recorded separately
from the observed merge and approvals; it is not retrospective qualification
of the already merged dependency. Truth's adoption PR needs its own current
review and checks.

## Interface and trust boundaries

- Both evidence and review jobs check out the supplied immutable head, reject
  a mismatch with the native event, and verify clean starting and ending trees.
  They use the native PR base SHA and Git merge base, including a stacked base;
  there is no substitution of the repository's default branch.
- The full diff is retained with byte count, SHA256, base/head identities and
  run provenance. Its 300,000-byte preview is explicitly marked when truncated.
  The review job independently recreates and verifies the complete input before
  execution and again before publication; final tool-written assessments must
  cover the input. Publisher validation consumes frozen validated bytes.
- Caller permissions stay `contents: read` and `pull-requests: read`; the same
  three named secrets are forwarded. The model has no publisher token. The
  existing GitHub App token is minted after validation with metadata-read and
  pull-request-write permissions for deterministic publication. Checkout does
  not persist credentials. `setup_eta_mu_toolchain: false` continues to skip
  eta-mu's private dependency mirror and installation gates for this caller.
- The default remains a free OpenCode provider: `opencode/mimo-v2.6-flash-free`,
  with CLI `1.18.18`. No model credential is added. Muse and skills stay pinned
  upstream to `0b9a91492c8355e6933dc2164d35668cb76d9e60` and
  `7fd3252e7663ad5e68be5e90429d126aa66c38c8`. Provider availability still needs
  the actual hosted run; local tests do not establish availability.
- The upstream terminal review gate fails closed when evidence, context,
  review, head binding, or cleanliness fails. It is a gate, not an optional
  provider-result check. Truth's diff-stat evidence script is unchanged and
  does not replace its separate Clojure tests and strict-analysis gates.

## Diagnostics and bounded recovery

The upstream runner retains stdout, stderr, exit/invocation state and recovery
metadata. One corrective invocation is allowed only after an otherwise
successful invocation omits `review_submit`, or after a verified structured
unavailable-tool loop names a supported corrected review tool. Other failures,
malformed submissions and a second omission fail closed. This is within one
job, not an authorization to rerun failed hosted jobs indiscriminately.

For a failed run, inspect `gh run view RUN_ID --log-failed` and download
`review-attempt-PR_NUMBER-RUN_ID-RUN_ATTEMPT` with `gh run download`. It includes
`opencode-stderr-attempt-1.log`, any second attempt, `model-response-attempt-*.txt`,
`recovery.json`, and verification/submission records when reached. Preserve
native attempt identity; the upstream runtime handles producer artifacts on
failed-job reruns without claiming a new producer execution.

## Verification and remaining qualification

On 2026-10-06, the exact upstream workflow, runner, two test files and operator
documentation were downloaded into `/tmp/truth-review-upstream-09a445`.
Its pinned YAML dependency `2.9.0` was installed there with scripts disabled.
`node --test .github/scripts/opencode-code-review-workflow.test.mjs
.github/scripts/opencode-review-tool-recovery.test.mjs` passed **74 tests,
zero failures and zero skips** on local Node 24.14.1. The log is retained at
`.ημ/diagnostics/review-runtime-upstream/upstream-tests-09a445.log`.
`actionlint` 1.7.11 passed the Truth caller; a one-off interface check verified
required/known inputs, native head, unchanged triggers/secrets/permissions/gates,
and the free default. `git diff --check` passed. No local review implementation
or test wrapper is added to Truth. Hosted Node 22 execution remains unverified.

Kimi remains a separate infrastructure gap: Truth has no verified hosted Kimi
review surface and canonical PR-flow has no verified Kimi app identity. The
MiMo workflow and its publisher cannot be relabeled as Kimi. This change does
not claim a Kimi invitation, completed review, or approval.
