# PR 7 capture and demo-service regression plan

(己, p=1.0) Scope: four verified native CodeRabbit findings on
`0112e228e93abf346da31f15456290c1b4c9b7ae`, isolated in
`truth-demo-review-fixes`. Proposed hygiene/regression estimate: 3.
No production changes, passing tests, or completed planning review are claimed.

| Finding | Observed boundary | Smallest correction |
| --- | --- | --- |
| [PRRT_kwDOTDahac6plKPq](https://github.com/octave-commons/Truth/pull/7#discussion_r4198372968) | `bin/demo-capture` derives diagnostic identity from UTC seconds; separate ports/output paths can still share and truncate logs. | Atomically allocate a new timestamp-prefixed diagnostic directory with a unique suffix; document its path. |
| [PRRT_kwDOTDahac6plKPw](https://github.com/octave-commons/Truth/pull/7#discussion_r4198372981) | `bin/formation-capture` has the same diagnostic-directory collision. | Apply the same directory-identity contract without changing capture timing or rendering. |
| [PRRT_kwDOTDahac6plKP8](https://github.com/octave-commons/Truth/pull/7#discussion_r4198373005) | `demo-client/-main` opens nREPL before checking whether its form exists. | Reject absent code locally with the usage message before connection or request effects. |
| [PRRT_kwDOTDahac6plKQD](https://github.com/octave-commons/Truth/pull/7#discussion_r4198373016) | `demo/prepare-formation!` accesses a nil service and leaves identity hooks installed when waiting fails. | Guard service before setup; restore the original presence and value of `:tick-fn` and `:on-step` on failure while preserving unrelated concurrent config changes. |

The existing capture documentation promises separate displays/processes and
ports for parallel runs. These repairs preserve that existing contract. They
do not alter the ECS, camera semantics, formation parameters, or capture media.
The queued world replacement is not rolled back: the correction owns the two
temporary pause settings, not publication of simulation state.

Canonical Rheos title searches for `demo`, `Demo`, `capture`, `native`, `Native`,
`window`, `Window`, `service`, `Service`, `tour`, and `Tour` found no matching
existing card in this ancestor checkout. The JSON Promethean configuration is
read directly by the verified canonical Rheos artifact; migrating that ancestor
configuration is outside this correction.

## RED evidence to prepare

1. Invoke each real capture script twice concurrently, copying its bytes into
   one temporary workspace. Supply deterministic `date`, empty-port `ss`, and
   deliberately failing `Xvfb` through normal PATH resolution. A shared barrier
   makes both invocations reach allocation before either fails. Different
   explicit output directories and ports must produce two diagnostic directories
   with separate per-run log markers. No JVM, native display, or video is needed.
2. Replace nREPL effects with recorded test doubles: an absent form must produce
   a local usage exception and zero connection calls. Keep a supplied-form control
   that proves request dispatch and resource closure remain intact.
3. Use isolated service/config atoms and small scenario data. Missing service
   must be rejected before scenario construction. During a failed wait, the world
   dereference changes an unrelated config value and throws a known exception;
   test all absent/nil/function combinations for the two pause keys, exact
   exception propagation, camera preservation, and the resulting config map.
4. Retain one successful setup control: accepted replacement stays paused and
   gets the intended camera/config settings. Check the actual timeout branch
   once, separately from the nine immediate-error cases. The current wait is an
   inline fixed 300 × 100 ms loop with no clock or wait seam; do not invent a
   public timeout option solely for testing. Root will choose the bounded
   integration-test placement before execution.

The normal test alias needs the existing `dev` source path and already-pinned
`nrepl/nrepl` 1.0.0 as test-only inputs to load these caller namespaces. Neither
production dependencies nor runtime aliases need changing. Root owns the RED
checkpoint, GREEN authorization, source checkpoints, PR updates, and replies.
Focused execution waits for the isolated benchmark window to close; whole-tree
gates and final review occur only on the subsequent committed correction.
