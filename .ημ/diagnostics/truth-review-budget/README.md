# Complete-input review budget canary

Truth PR54's hosted review exceeded its 45-minute job limit after assessing 401
of 807 required chunks, without a submission or published review. The native
run is [37605835577](https://github.com/octave-commons/Truth/actions/runs/37605835577).
The observed rate suggests roughly 90 minutes for assessment alone; it does
not establish a sufficient deadline for another invocation.

[Canonical eta-mu PR346](https://github.com/open-hax/eta-mu/pull/346) adds the
bounded `review_timeout_minutes` input at immutable revision
`a91a73e0e5efa165baf6d7247338f3ef17751752`: 45 by default, or an explicit 120.
Unsupported values fail admission. Both permitted model attempts and finalization
share the selected job budget. The free model, full-input coverage, submission,
required evidence gates and all other job deadlines remain unchanged.

Truth now pins that revision and requests 120. Parsed-YAML comparison in
[caller-validation.json](caller-validation.json) confirms those are the only
caller changes. Syntax, standard actionlint rules with actual ShellCheck, and
whitespace checks pass. The local lint executable uses the existing documented
subprocess-stdin transport repair; the original upstream tool stalls and its
source comparison remain in PR346. No global lint tool was modified.

The upstream repair is open for review. Local tests and source review qualify
this canary preparation, not hosted execution or approval. The next complete
native review must still finish all coverage and produce a valid submission;
timeouts, missing input and partial reviews remain failures. No retry of the
unchanged PR54 configuration is represented here.

This changes CI orchestration only. The active native run's game source and
frozen input scripts are unchanged. The canonical measurement owner's scope
and append-only board proof are in [caller-scope.json](caller-scope.json).
