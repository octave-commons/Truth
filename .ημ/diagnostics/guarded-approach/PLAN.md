# Guarded snapshot connection reuse — preparation only

This directory prepares one short diagnostic read/input smoke under the existing
native verification owner `f369c598-279c-498d-a64f-45d2ce16ad34`, still InProgress
3. The root-authored scope is in [root-scope.json](root-scope.json). No script in
this directory has been run against a JVM or native service during preparation.
Source review and static checks are not execution qualification.

Production stays byte-identical to
`b395c4049718f7ce015ddf25fc0a192d97821373`. Preparation began at
`2863728ad6ec1c0a8d218aa5abf38ee413a61005`; a later evidence-only parent merge must
not change the production pin. The frozen earlier input bundle is not edited.

## Why this adapter exists

The earlier driver starts `clojure -M:demo-client` for every observation. Its
small observations took several seconds end to end, including local JVM startup
and server work. Those timings are not an isolated measurement of either part.
The first completion run reached a deadline before a final camera readback; the
second exposed the production cursor callback's first-event initialization.
The separate initialized run then observed all four small look changes and
ordinary menu/Tab behavior. This preparation changes the local read transport;
it does not change the accepted input checks or claim that connection reuse has
already improved latency.

The existing client is [dev/demo_client.clj](../../../dev/demo_client.clj).
The dependency is already `nrepl/nrepl` version `1.0.0` in
[deps.edn](../../../deps.edn). Installed `nrepl/core.clj` documents `client` as a
transport response cursor and `message` as an ID-filtered response sequence that
ends on `done`. The exact jar hash and inspected API locations are in
[STATIC-CHECKS.json](STATIC-CHECKS.json). No new dependency version, production
alias, board runtime, or reusable benchmark framework is introduced.

## Fixed request and ownership contract

* [runner.py](runner.py) is a reviewed derivative of the earlier
  [input driver](../native-input-hygiene/runner.py). It remains the sole owner of
  its fresh private Xvfb, game, helper processes, and native input commands.
* [snapshot.clj](snapshot.clj) is an exact byte-copy of the guarded read-only
  snapshot. Every evaluation checks game PID, boot, process start, cwd, display,
  port, original world atom/window, production tick/projection functions, ordinary
  nebula provenance, worker liveness, heap and error state. The snapshot reads one
  published world; camera/config are separately sampled. This is not a guarantee
  of sustained overlap in the binding consumer's input.
* [snapshot_client.clj](snapshot_client.clj) is **one local diagnostic JVM and
  one loopback nREPL connection**, with a new lazy response cursor per completed
  request. It uses the same existing nREPL version through `-Sdeps`; no game source
  or `deps.edn` change is needed. It does not clone a retained nREPL session,
  install a remote Var, wrap a renderer, or accept a remote setter/code string.
* Its startup identity comes only from the supervisor's recorded owned process.
  The first successful, guarded snapshot supplies world/window identity; the
  worker and supervisor both pin it for later reads. The only local request file
  fields are the next sequential four-digit ID and a positive bounded timeout.
  Neither paths, ports, identities, forms nor operations are accepted per request.
  The worker internally constructs the same fixed `load-file`/snapshot call each
  time. The snapshot hash is checked before every call. The supervisor separately
  checks all production and preparation hashes before requests/actions.
* Requests are serialized. Each receives its own raw nREPL message transcript,
  stdout, stderr, metadata and completion marker under `snapshot-responses/`.
  A completed read requires the matching ID, exactly one returned value, one
  identity frame, `done`, and no remote error or unexpected status. Output chunks
  are joined before the existing framing is interpreted. Returned values are
  preserved as text; no general Clojure reader evaluates server output.
* On a failure, the sequence stops. There is no request retry, gesture retry,
  replay, replacement world, or resumed uncertain connection. The supervisor
  closes/reaps the diagnostic process and then its owned game/X server. Closing
  a local connection is **not** proof that a server evaluation was cancelled.

## Bounds and explicit limits

The unchanged attempt limit is **270 seconds of work, 300 seconds total**.
The supervisor's monotonic clock is the authority. The local client also has a
finite work lease; because that lease starts after JVM startup, it does not
replace the supervisor's earlier absolute deadline.

The persistent client uses `-Xms64m -Xmx256m`; the ordinary game uses
`-Xms256m -Xmx2g`; tools.deps preparation uses `-Xms64m -Xmx256m`. Inherited JVM
option overrides are removed as before, with only their names recorded. The
driver retains its 32 MiB per-child-output-file resource limit.

There are at most 128 snapshot requests. Encoded raw-response transcripts are
capped at 4 MiB per request and 16 MiB per run. Exceeding either cap fails visibly;
the error records the rejected message size and prior byte counts. This is an
**application-level encoded-EDN cap after a message is received**, not a claim
that nREPL's decoder cannot allocate one oversized incoming message. The raw
prefix remains; an oversized rejected message is explicitly not fully archived.

The request timeout is at most 35 seconds, normally at most 10 seconds during a
post-input observation. `nrepl/client` waits per message, not per whole request.
The supervisor independently bounds the whole request, stops on a timeout, and
then performs owned cleanup; the worker's before/after-message checks are
additional guards. Initial connection/startup is also bounded externally.
OS scheduling or an uninterruptible process can prevent an exact deadline;
overruns and unreaped children remain failures in the closure record.

All input remains ordinary native input. Clicks have a balanced press and one
release, with no extra release after a completed click or Tab. Look steps are at
most 100 pixels per axis, with the original 0.02-degree postcondition tolerance.
The short controller reserves 40 work seconds before each new input, validates
every command result/ready identity before the next, and submits at most one
stop request. It owns only the runner client processes it directly creates;
native cleanup remains with the detached supervisor.

## First execution, only after root review/release

From the worktree root, against a nonexistent new attempt directory and a free
port:

```sh
python3 .ημ/diagnostics/guarded-approach/short-smoke-client.py attempt-01 --port 7898
```

The fixed sequence is startup → two read-only inspections → R → Spark open →
Spark close → Tab lock → +100X → −100X → +100Y → −100Y → Tab free → Tab lock →
stop. Initial inspection plus two additional acknowledged reads establish reuse
before any input. Menu motion initializes the production cursor history. No
look expectation is relaxed. No target is required, and no movement, approach,
binding, commitment, intervention, fixture, follow camera or world setter is
available in this smoke sequence.

Qualification requires every requested read/input result to succeed with the
same worker PID and connection, monotonically numbered response transcripts,
stable source/identity pins, and a clean `operator-stop` closure. Inspect the
controller **and** supervisor result files; a supervisor exit code alone is not
success. Check partial messages/error evidence on any failure. Compare raw
snapshot latency descriptively only; this is not an isolated performance/FPS
benchmark.

Future 25-minute ordinary approach remains separately scoped and budgeted. This
preparation neither starts it nor implements automatic aiming or pursuit.

## Review inventory and evidence preservation

[COPY-PROOF.json](COPY-PROOF.json) contains the exact driver/controller diffs,
source hashes and the unchanged snapshot comparison.
[STATIC-CHECKS.json](STATIC-CHECKS.json) records Python AST parsing and individual
Clojure static checks, including the initial corrected lint findings. There was
no Python import, JVM evaluation, native input, game process, or runtime test.

`PREPARATION-FILES.txt` and `SHA256SUMS` close only the new preparation artifacts.
They exclude the root-owned scope record and all future mutable `runs/` and
`completion-client-runs/` outputs. The canonical card/event ledger and receipts
remain root-owned and are outside this agent's edits.
