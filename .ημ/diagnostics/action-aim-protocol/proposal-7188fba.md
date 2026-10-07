# Manual sculpt contextual intent: concrete implementation proposal

(己, p=0.99) **Proposal for root admission review, not implemented or executed.**
Exact checkout `7188fbab1400589c5a3ccadb560df6b5120a5f9b`; production parent
`b395c4049718f7ce015ddf25fc0a192d97821373`. Existing owner is
`focus-follows-pilot`, canonically read in this checkout as **InProgress, 3**.
The root reports current PR36 planning PASS under the available-provider law;
this artifact does not request or independently recheck hosted reviews.
The selected semantics are the existing
[serial-consumption proposal](../../../docs/notes/2026-10-07-manual-sculpt-aim-timing.md),
not a new timing or domain law. No production/test/Git/board/runtime mutations.

## Exact three-file implementation boundary

1. **New `src/law/input_intent.cljc`, namespace `law.input-intent`.** A small
   named registry with EDN schema bodies and three once-compiled predicates:
   - `::contextual-entry` → closed map with required `:update` of built-in
     symbol schema `ifn?`; compiled predicate `contextual-entry?`.
   - `::iteration-context` → closed map with required `:manual? :boolean` and
     required `:focus-offset :any`; predicate `iteration-context?`.
   - `::submission-options` → closed map with optional `:submit-contextual!`
     of built-in symbol schema `fn?`; predicate `submission-options?`.
   Compose this registry with `m/default-schemas` and compile by its named keys.
   Schemas contain symbols/data, not embedded closures/classes. Installed
   Malli0.16.4 `malli/core.cljc:2601–2609` registers both predicate names.
   `:any` for the raw offset is intentional: structural context validation
   must not preempt the selected missing-observer or tracking behavior.
   The existing `law.narrowing/focus-offset?` remains the semantic coordinate
   validator, invoked only at the manual action boundary described below.

2. **Existing `src/infra/dev/window/loop.clj`.** Keep host-only nominal
   `defrecord ContextualIntent [update]` next to the existing `IntentAtom`.
   No extra runtime namespace, protocol, command registry, queue, or polymorphic
   world adapter is necessary: producer and consumer already share this owner.
   - Public `enqueue-contextual! [^IntentAtom intents update]` wraps the update
     in `->ContextualIntent`, adds it to that exact object's existing queue,
     and returns its currently published world, matching queued-swap return
     behavior. It never invokes the update or dereferences config. Invalid
     update payloads are deliberately diagnosed at serial consumption through
     the existing guard, so a malformed contextual entry cannot bypass it.
     Its type hint documents the explicit dev-only capability; input dispatch
     does not detect Atom classes or select behavior from runtime object type.
   - Private `iteration-context [cfg]` returns exactly
     `{:manual? (= :manual (:mode cfg :manual)),
       :focus-offset (:focus-offset cfg [0.0 0.0 0.0])}`.
     Absent mode means manual; explicit nil mode remains nonmanual. Absent
     offset gets zero; explicit nil remains nil. No atom, full config, graphics
     handle, callback-time world, target, or camera goes into this context.
   - Extend private `apply-intent` to `[w entry context]`, retaining its existing
     two-argument arity for legacy/final-preparation callers. **Inside the same
     try/catch**, branch only on `(instance? ContextualIntent entry)`. For that
     branch validate entry payload first, then projected context, then call
     `((:update entry) w context)`. Invalid values throw named-boundary ex-info.
     All other entries still execute exactly `(entry w)`, without new `fn?`
     restrictions. Preserve the existing `map?` result acceptance, dropped
     non-map results, `catch Throwable`, stderr `[INTENT ERROR]`, and exact
     original `w` on failure. Plain callable maps/keywords are never envelopes.
     A malformed record remains on the contextual branch; it does not become
     legacy invocation or fallback.
   - Extend `drain-intents` to `[w queue context]`; keep `[w queue]` delegating
     with nil context for legacy test/dev callers. Legacy entries ignore that
     context; a contextual entry without valid context is guarded/dropped.
     Poll-until-empty and FIFO behavior are unchanged.
   - In `sim-loop`, project `C_n` once from the existing `cfg @config-atom`
     binding, pass it to the one drain, then retain the current after-drain
     manual preparation **unchanged**. No global context validation before the
     drain, no per-action config dereference, and no earlier attention refresh.
     Mid-drain arrivals receive the same immutable context; previous entries
     still determine each current serial world `W_j`.
   - Add only `:submit-contextual! (partial enqueue-contextual! world-intents)`
     to the existing `render/setup-input` options. This supplies the capability
     without introducing `input → dev-loop` imports (which would create a
     dependency cycle through the render facade).

3. **Existing `src/infra/render/input.clj`.**
   - Preserve the current public `setup-input` map API; add optional
     `:submit-contextual!`. Extract its presence with `select-keys`, validate
     `submission-options?` **before installing any GLFW callback**. Presence
     is not truthiness: supplied nil/nonfunction is an error, never legacy.
     Restricting this new explicit capability to a function is deliberate;
     it does not restrict legacy queued IFn entries. Callable arity/behavior
     cannot be certified by this shallow schema and is exercised by integration
     tests; callback-time failures propagate without direct-dispatch fallback.
   - Preserve the existing private five-argument `key-callback` and
     four-argument `dispatch-palette-action!` as absent-capability arities.
     Add respectively a final submission-options argument. Validate options
     once during callback construction; setup also validates before native
     setters. A directly used dispatch arity validates options at its own
     boundary. Existing plain Atom and existing legacy callback tests keep
     their original calls and outcomes.
   - For sculpt only, an opted-in callback submits a closure capturing just
     the palette verb/magnitude. The closure accepts `[w context]` and calls
     private pure `request-sculpt [w context verb magnitude]` below. Without
     the capability, retain exact `swap! world-atom sculpt/request-op ...`.
     G/Shift+G/H/J config requests, held-key bookkeeping, focus controls and
     press/repeat/release conditions stay unchanged.

## Exact evaluation and failure order

Nominal entry recognition → named entry-payload check → named context-shape
check → contextual update, all within **one existing apply-intent guard**.
No world has been written or paid yet. The sculpt update then follows:

1. If `:manual?` is false, call unchanged `sculpt/request-op w verb magnitude`
   immediately. Do not add observer/offset checks: existing unknown-verb and
   magnitude errors still precede its observer/commitment gates.
2. Manual with no observer: return exact `W_j`, even with invalid offset or
   invalid verb/magnitude. This is the previously reviewed explicit precedence.
3. Manual with observer: validate offset with `narrowing/focus-offset?` first;
   require nonnil `player/observer-position` second. Missing position throws
   named ex-info; do not call focus-follow's missing-position no-op and then
   spend using old focus. No new physical-position schema or physical writer.
4. Apply existing `player/focus-follow W_j offset` to obtain `P_j`, then call
   unchanged `sculpt/request-op P_j verb magnitude`. Do not precheck private
   commitment/palette/affordability or copy anchor/payment code.
5. Successful action retains prepared attention and paid record. Valid aim
   denied by domain gates returns `P_j`, not necessarily `W_j`, with no spend.
   Any exception, including after preparation, rolls back this pure operation
   to exact `W_j` under the existing guard; earlier queue results survive and
   later inputs still run. Physical columns are never changed by this adapter.

An earlier radius/intensity/spend/reset acts first. An earlier camera-focus
write cannot override the manual action's position-plus-offset preparation.
A later reader intentionally sees prepared attention; later reset/camera writes
still act normally. A later action uses its own serial world but the same `C_n`.
Final manual preparation remains at the existing after-drain boundary and can
emit an additional invalid-offset diagnostic; no one-error-per-tick promise.

## Small RED matrix and qualification

Write laws/tests only after root records this exact scope; preserve unchanged
production until the observed RED checkpoint. Do not invent a paid native trace.

| Boundary | Minimal meaningful checks |
| --- | --- |
| Named laws + nominal dispatch | EDN schema round-trip; valid/invalid required keys, explicit nil; malformed nominal record, throwing update/AssertionError and non-map result preserve `W_j`; ordinary IFn map and keyword callables, all existing swap arities and reset retain old result/order behavior. A plain map with `:update` remains a callable map, never an envelope. |
| Capability + actual callback | Absent capability on plain Atom preserves the existing focus-based anchor. Supplied nil/nonfunction fails before callback registration, never falls back. T/Shift+T/Y actual callback `.invoke` plus `.free` emits exactly one contextual entry for press/repeat/release; config/physical world not mutated by submission; G/H/J unchanged. No real window needed. |
| Real moving/recentered queued action | Real finite `sim-loop` + production integrator; after a moving publication invoke the ordinary opted-in callback. Next drain's paid anchor must equal normalized current Spark-plus-offset minus actual committed target, using non-collinear motion and zero/nonzero common translation. No test-injected focus-follow. Assert physical columns unchanged at action boundary; preserve real sculpt fan-out/barrier test as downstream control. |
| FIFO + context timing | Two requests with intervening radius/intensity, spend, reset/camera writes and later reader; first paid record immutable, second sees current funds. Change mode/offset before the iteration versus during a preceding queued update; enqueue an additional request mid-drain. All requests in that drain use one `C_n`, next iteration adopts new host values. Both manual→tracking and reverse. |
| Domain gates + failure precedence | Manual missing observer with bad offset returns `W_j`; manual observer with nil/nonfinite offset or missing position rejects before domain spend, retaining `W_j`. Valid aim with no commitment/palette/funds retains `P_j` and no paid record. Tracking bad verb/magnitude with no observer retains its current validation order. Later valid input recovers; unrelated queued changes survive. |

The current callback/queue path can first furnish a semantic stale-anchor
baseline reproduction without new API plumbing. Tests for the new opt-in arity,
record and schemas will additionally be RED for absent APIs until implemented;
report those separately rather than describing an arity/missing-var failure as
an observed wrong numerical anchor. After GREEN, the decisive test must traverse
actual opted-in callback → IntentAtom → serial drain → real action, not a
manually inserted preparation closure or mocked domain action.

Proposed focused command (only after root grants a JVM slot):
`JAVA_OPTS='-Xms256m -Xmx2g' clojure -M:test -n law.input-intent-test -n infra.dev.sculpt-intent-test -n infra.dev.focus-cadence-test -n infra.dev.window-test -n infra.render.input-test -n domain.pilot-resolve-seam-test -n architecture-test`.
New namespaces should be at `test/law/input_intent_test.clj` and
`test/infra/dev/sculpt_intent_test.clj`; extend existing input tests for callback
compatibility. Root coordinates full ordinary suite, demo queue compatibility,
and all six strict gates afterward. This host boundary changes allocation/cost;
any cost qualification must exercise the actual host drain, not claim unchanged
pure domain bin/bench results prove host performance. Do not run it in parallel
with owned native measurements. Natural ordinary committed-world sculpt remains
separate gameplay acceptance, not inferred from deterministic fixture success.

## Sizing and admission

(己, p=0.95) **Retain 3 points** for this cohesive three-production-file host
repair, two focused test namespaces plus targeted existing-input assertions.
There is no new domain API, force/component writer, command framework, clock,
queue, cross-store transaction, persisted record format, or target-selection
policy. The explicit class is a local in-memory payload only. G/H/J placement,
postpublish focus lag, pursuit/reachability and native lifecycle are excluded.
If the implementation requires moving IntentAtom ownership, a protocol/registry,
rewriting all callbacks, or a domain sculpt arity, stop and re-scope rather than
silently widening. Three points is an estimate, not an observed implementation.

The remaining gate is **root review and canonical admission comment** settling
this concrete representation/validator/API under the existing InProgress3 owner,
then observed RED and its checkpoint. This artifact does not invent an additional
hosted planning gate or claim implementation permission from status alone.

## Read-only provenance

Canonical read command (exit0): Node22.20.0 + verified Rheos CLI
`/home/err/spaces/review-repair/rheos-comment-rendering/dist/cli.cjs read-task focus-follows-pilot --config openhax.kanban.edn`.
CLI SHA256 `83c6b397278d418d69ce6509b8c3d9fe87e88cca9141b28143d9efb5d78a75a7`.
Existing untracked root independent-review JSON was present before this turn
and remains untouched. No provider request or remote operation was performed.

Exact source anchors: loop35–69 queue/guard,135–143 snapshot/drain/preparation,
470–474 setup capability seam; input158–169 sculpt dispatch,182–205 actual key
callback,253–262 setup; player/focus17–49 preparation; player/state28–35 missing
physical position; sculpt479–525 domain validation/payment; law/narrowing13–21
offset validator; focus_cadence_test45–80 real bounded loop and90–117 actual
published lag; input_test56–88 unchanged plain-Atom route. All line numbers are
at the pinned checkout above.

| Input | SHA256 |
| --- | --- |
| `AGENTS.md` | `469f34c0eb48710fe631ffd21fbd18029f692cbecc707f7e776dcd28f4f49eb0` |
| `PROCESS.md` | `11069c68a36e7cf7c3aac4c00fc384b3e653d0179bead9948d82b1d994d6fae2` |
| `docs/notes/2026-10-07-manual-sculpt-aim-timing.md` | `4eaa38fb1c377c031e270a9c4b5faf65a72242032a6aaa84225b4346ef94e09f` |
| `docs/designs/spark-flight-and-camera.md` | `396eb70a86e0594847d2cb4b4c48178a51cf8403d96d95ccfbbdf5619468c7c9` |
| `src/infra/dev/window/loop.clj` | `3994a14ddb3fd052e7e9e4f20c4995422905d01f89a1d43877f634a8482c4067` |
| `src/infra/render/input.clj` | `d8af828b8628f98b0c90c1d1477e11820f23c6f73a9e2189db8ceff6a2e7869c` |
| `src/domain/player/focus.clj` | `7ffeafd0a3826d400318458d3128e9181ecfed82c93c8d303cae102b8d5f1b2a` |
| `src/domain/player/state.clj` | `e5296cbce5dc2dc525577cba4bd1ae90978f9a9158f85e52f4d3a9c697806191` |
| `src/domain/voxel/sculpt.clj` | `807f7eb41c406d71bdab1d31512de8fcdd7065d8eef2d1b804ad5a098ba5bf5f` |
| `src/law/narrowing.clj` | `e7e503e7197b37774babda62cf59c93f49e168221eb64f9491c434b26a47749b` |
| `test/infra/render/input_test.clj` | `e9f835558f61e7c83fd4eaf230fb530c60ec20d489411b5fbb65ffacfdd75969` |
| `test/infra/dev/focus_cadence_test.clj` | `f8e187222867e61ea4679bfa08dbf65da70a1e9cf7031ef03aa8b9f7e374edc1` |
| `test/infra/dev/window_test.clj` | `46f83b175bde6417482bad92451dc530cf1dfa555c1c90bca2bf77c18bd7bef4` |
