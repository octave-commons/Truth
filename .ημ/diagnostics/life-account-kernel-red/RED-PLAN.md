# Pure life-account kernel: RED contract

The existing `life-account-origin-kernel` card is InProgress, three points, under
`life-to-represented-actor-spec`. Root admitted it through Rheos after the current
PR38 planning gate passed. The parent’s natural-life acceptance is still open.
The specification is `docs/notes/2026-10-07-first-represented-life-boundary.md`
§§6–8.1 and `docs/designs/resolution-regimes-and-scale-coupling.md` §6.1.

## Public surface

- `law.life-account/registry` contains named, EDN-round-trippable Malli shapes.
  `material?`, `identity?`, `request?`, `account?`, `history?`, `result?` and
  `budget-result?` are the executable boundaries. Cross-field stock/lineage
  invariants are named pure predicates over the compiled shapes. Existing
  `law.composition/element-set` and `mass-fraction?` remain authoritative.
- `domain.life-account/origin-budget material` will return either an invalid-input
  rejection or a budget with exact carbon units, allocation, origin amount,
  post-origin stocks and raw binary64 bit/discarded-fraction witnesses. A valid
  material below the origin threshold retains its numerical budget and rejects
  as `:zero-origin`; no account is thereby allocated.
- `domain.life-account/apply-operation account history request` will return the
  current/resulting account, disposition, immutable outcome, a separate `:retain`
  proposal, and pure effect descriptors. Origin material is part of the immutable
  request. History is an input map from operation ID to `{request,outcome}`;
  neither function mutates or owns persistence.

Well-formed identity is checked separately from payload validity. A new valid-key
payload rejection returns a mandatory retention proposal. Malformed identity
returns only a diagnostic rejection. A malformed caller account or history fails
its named input boundary with `ExceptionInfo` before admitting a transaction;
invalid history cannot truthfully establish whether an operation is new. For a
valid caller state, retry lookup precedes current revision/closed checks. Exact
request comparison must preserve binary64 bits, including signed zero.

A retry returns the historical outcome and **current** account, with no new
retention/effect. A conflicting key preserves the original outcome. Successful
origin moves conceptual revision 0 to 1. Each new accepted extent/closure,
including zero extent, increments once; outcome-count growth is separate.
Closure keeps a nonspendable terminal inventory and exports the full active sum.

## Runtime choice and scope

These four files use `.clj`: the repository is JVM Clojure, canonical composition
laws are `.clj`, and this contract requires exact binary64 decomposition plus
arbitrary-precision integers and ratios. There is no existing cross-host exact
numeric adapter. A `.cljc` label would not establish JS arithmetic equivalence;
no new portability framework is included in this three-point slice.

The two domain functions are explicitly non-implementing RED placeholders. They
return a labelled `:domain.life-account/not-implemented` rejection and never
perform budget arithmetic, transitions, I/O, ECS allocation, event creation or
physics writes. The named laws and acceptance tests are executable now. These
placeholders must not be represented as a usable feature or installed system.

## Acceptance coverage

The tests cover the specified capped partition; exact-product flooring that
would differ after double rounding; tiny/subnormal and above-long carbon values;
exact input-sum rejection without renormalization; raw signed-zero witnesses;
nonnegative integer stock and shape boundaries; exact q/g/L transitions and
conservation; zero-operation revision; ordered competing transfers; large
revision; malformed caller-state rejection; missing/cross-account balances;
rejected-origin retention; exact retry/conflicting reuse; terminal history and
no reopening or second export.

Small supplied accounts in extent tests exercise the transfer law independently
of the origin prescription; they are unit accounting examples, not generated
habitats or assertions about an available natural birth. No test consumes PR50
kinetics, a simulation clock, ecological scalar, renderer, input or paid Grow.
The 0.3×0.3 numerical oracle was independently derived offline with Python
`Fraction.from_float`; its integer floor is 89999999999999, while the rounded
binary64 product path yields 90000000000000. No production Clojure was evaluated
for that derivation.

## Resource and checkpoint boundary

Root released exactly one focused JVM. Command, PID, source hashes, complete raw
stdout/stderr, timeout and reap evidence are in `attempt-01/`. Earlier native
clj-kondo draft failures are recorded in `static-attempts.json`; they were fixed
before this JVM. No full suite, strict gate, benchmark or native game was run.
GREEN requires root review of this actual RED and a separate release.
