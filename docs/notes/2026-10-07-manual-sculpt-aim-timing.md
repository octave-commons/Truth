# Manual sculpt aim: serial-consumption proposal

Status: **proposed design only; no implementation admission or executed RED**.
Owner: [`focus-follows-pilot`](../../kanban/tasks/focus-follows-pilot.md),
canonical InProgress, 3 points. This is a bounded continuation of that owner,
not a new card or a completion claim for ordinary approach/commitment/sculpt.
Source reference: `760ea79d017f4e012943789f896dfdfdb0a173b1`.

## Observed contract and gap

(己, p=1.0) The existing owner asks for manual flying, binding, and aimed
planet action. Accepted [flight design §7.5](../designs/spark-flight-and-camera.md#75-the-linchpin--focus-must-follow-the-pilot)
chooses position-only focus: Spark physical position plus persistent host
offset. Its present boundary explicitly excludes already captured action
positions and anchors. This proposal changes that boundary only for **new
manual sculpt requests**; it does not reinterpret it as an earlier approval.

(己, p=1.0) At the pinned source:

| Source | Current behavior |
| --- | --- |
| [`input.clj`](../../src/infra/render/input.clj), lines 158–169, 193–205 | T/Shift+T/Y press dispatches the existing sculpt verb/magnitude through `IntentAtom`; release/repeat do not dispatch. G/Shift+G/H/J instead set an intervention request in host config. |
| [`loop.clj`](../../src/infra/dev/window/loop.clj), lines 35–69, 135–143 | `IntentAtom` queues world functions. The host reads config once, drains them in arrival order through the existing failure guard, then prepares manual focus. |
| [`focus.clj`](../../src/domain/player/focus.clj), lines 17–49 | `focus-follow` uses Spark position plus offset, retaining radius/intensity and physical columns. It returns unchanged world if the observer or its position is absent. |
| [`sculpt.clj`](../../src/domain/voxel/sculpt.clj), lines 479–525 | `request-op` chooses the existing committed world, checks palette/Resonance, derives the relative anchor from observer focus and target position, then spends and appends a paid record. |
| [`loop.clj`](../../src/infra/dev/window/loop.clj), lines 361–364 | Intervention placement captures published focus in the renderer before queuing `place`; this separate seam remains outside the first slice. |

(己, p=0.99) A sculpt request can therefore combine published pre-fold focus
with post-fold physical positions. The existing
[`focus_cadence_test.clj`](../../test/infra/dev/focus_cadence_test.clj), lines
90–117, deliberately demonstrates the published focus/physical-position
difference with the real integrator. The
[`pilot_resolve_seam_test.clj`](../../test/domain/pilot_resolve_seam_test.clj),
lines 165–205, calls focus-follow immediately before request-op; it proves the
relative direction law and commitment gate, not the ordinary queued timing.
These are source-derived findings, not a new observed failing action trace.

## Proposed timing law for review

(己, p=0.95) Choose **serial consumption time**, before payment or creation,
rather than keypress time or the last rendered frame. This extends the current
per-iteration attention convention and avoids adding aim prediction or a
historical render-state buffer.

Let `C_n` be an immutable projection of mode and offset from the single host
config snapshot already read at the start of simulation iteration `n`, and
`W_j` the world after the preceding queued intents have completed. Preserve
the absent-mode manual default and distinguish absent offset from explicit nil;
do not pass the full config, resource handles, or atoms. For a new sculpt request
consumed as intent `j`:

1. If `C_n` is manual, a missing observer is first an unchanged-world no-op,
   including when the host offset is invalid. Otherwise validate the host offset
   with the existing named validator/absent-key zero default and require the
   observer's physical position. Apply the existing pure `focus-follow` to
   prepare `P_j`, setting focus to `Spark.position(W_j) + C_n.focus-offset`.
   **This attention preparation persists** on successful evaluation. Invoke
   the unchanged `request-op` on `P_j`, which derives its existing relative
   anchor and retains the coincident-point direction convention.
2. Keep the existing committed-target selection and domain palette,
   commitment, magnitude, and affordability rules. This does not snapshot or
   select a target at keypress, introduce automatic retargeting, or arm a verb.
3. Prepare aim and invoke the existing action in one guarded serial operation.
   With an observer present, invalid offset or unavailable Spark position takes
   precedence over the remaining domain gates: visibly reject before spending
   or appending, retaining exactly `W_j`, with no stale-focus fallback. This is
   an explicit proposed boundary, not today's domain gate. With valid aim,
   missing commitment, denied palette or insufficient funds creates no charge
   or paid record: `request-op` returns its exact prepared input `P_j`, which
   need not equal `W_j` because attention was refreshed. Keep those gate
   decisions in `request-op`; do not duplicate private domain gate logic in
   infra. Physical state remains untouched.
   Here "visible" means the existing `[INTENT ERROR]` stderr diagnostic, not a
   new UI error state. Unchanged final attention preparation may also diagnose
   the same invalid offset; do not promise only one error per iteration.
4. Preserve queue order. Earlier radius/intensity changes survive aim preparation;
   earlier spending affects the next request's affordability. In manual mode,
   an earlier queued camera-focus position cannot replace the position-only aim
   law. A later queued reader **does observe the refreshed attention**, including
   after a valid-aim request whose domain gates deny payment. This is an explicit
   proposed behavioral consequence, not a claim to preserve that old readback.
   Later focus/mode changes do not recalculate paid records already created.
   A later queued reset or camera write still runs normally in FIFO order;
   final manual preparation realigns the resulting world as it does today.
5. In non-manual `C_n`, retain the existing tracking-camera attention and sculpt
   consumption path. Retain final after-drain attention preparation, all existing
   physics/sculpt fan-out and barrier timing, and the one-tick Jacobi channel
   delay. No renderer writes simulation state directly.

## Snapshot boundaries and remaining design checks

(己, p=1.0) Host mode/offset are in one config atom; the intent queue and world
are separate. Arrow nudges mutate host config, while comma/period enqueue world
changes. There is no atomic transaction ordering an arrow or mode switch with a
sculpt keypress across these stores. Do not claim keypress-exact aim.

(己, p=0.95) Proposed resolution: all sculpt requests consumed in one iteration
use the same `C_n`, including requests arriving while that drain is running.
Mode/offset changes after the read affect the next iteration; do not reread
config per action. A switch to tracking before `C_n` uses the unchanged tracking
path; a switch to manual before `C_n` uses manual aim even if the request was
queued in tracking mode. These are explicit proposed timing choices for review.
Two requests in the same drain use their respective `W_j`, so intervening queued
world changes remain ordered. The drain still polls until empty; no new queue
cutoff, input coalescing, or cross-store atomicity is proposed.

(己, p=0.95) Implementation admission still needs review of these choices,
especially mid-drain arrivals, mode changes, validation precedence, and later
readers observing prepared attention. Root selected persistent preparation
during this design continuation. An action-local alternative would preserve
old attention for later readers but require an explicit aim extension to the
domain action or a temporary world rewrite/restoration; neither is selected.

## Smallest host mechanism: proposed, not admitted

(己, p=0.99) The existing anonymous `world -> world` closures cannot receive
`C_n` without a real adapter change. Three alternatives have different semantics:

| Alternative | Consequence |
| --- | --- |
| Refresh manual focus before draining, then retain after-drain refresh | Small source change, but changes attention seen by every queued reader. A preceding queued camera-focus write can replace it before T. If invalid preparation is dropped independently, T can still charge at stale focus. It does not satisfy the proposed contract. |
| Refresh before every queued intent | Repairs T's position, but also changes attention observed by unrelated reads/reset/scalar updates, repeats work across all input, and can reject unrelated intents on malformed config. This expands beyond the sculpt boundary. |
| Add a contextual entry form to the existing queue/application boundary | Pass the one immutable `C_n` projection explicitly at serial consumption. Legacy callable entries keep their existing behavior/order; only an opted-in contextual action prepares attention inside its operation. Entire preparation/action evaluation uses the existing Throwable/map-result guard. |

(己, p=0.95) Prefer the contextual-entry alternative as a bounded candidate: a **named, validated
contextual envelope** containing an update callable that receives `(W_j, C_n)`,
admitted through a generic contextual enqueue operation on the existing
IntentAtom. The existing queue, drain, and failure guard remain singular.
Selection is by explicit validated entry shape, never callable identity or a
sculpt-name lookup. Existing swap/reset world functions remain unchanged;
do not convert every legacy entry, add another queue, register commands, or
read config from inside a callback closure/domain function at consumption.
Legacy callable means the existing `IFn` contract, including keyword/map
callables, not just `fn?`. Choose an unambiguous contextual representation;
do not classify every map as an envelope or change legacy invocation/result
handling. Malformed contextual entries must fail through the same guard.

(己, p=0.99) Input compatibility requires an **opt-in submission capability**
supplied by the dev window's existing
[`setup-input` call](../../src/infra/dev/window/loop.clj) (lines 470–474).
The public input setup without that capability keeps its existing immediate
`swap! request-op` route. In particular, the ordinary Atom dispatch tests in
[`input_test.clj`](../../test/infra/render/input_test.clj), lines 56–88, use an
empty config and existing focus; do not infer manual mode there or change their
anchor. The opted-in ordinary callback submits the contextual envelope to the
same queue. No runtime Atom-class detection or function-identity special case.
The legacy route applies only when the capability is absent. A supplied invalid
capability, malformed contextual entry, or rejected evaluation must fail visibly
at its named boundary; none may fall back to direct dispatch or stale-focus aim.

(己, p=0.95) For sculpt, the contextual adapter first selects the mode from
`C_n`. Only in manual mode does it handle the early missing-observer no-op,
then validate offset/position availability, prepare attention using existing
`focus-follow`, and invoke unchanged `request-op`. Tracking delegates directly
to unchanged `request-op`, without preparation or a new observer shortcut;
its existing verb/magnitude validation still precedes its observer gate.
The whole operation uses the same failure guard, so a preparation or action
exception preserves `W_j`, not a partly prepared world. No new domain arity,
gate duplication, or second sculpt path is needed. Named envelope and projected
context boundary validation must be reviewed before implementation; this note
does not choose an unverified Malli representation.

(己, p=0.95) The 3-point design continuation is bounded. A later implementation
can credibly remain a single <=3-point repair if review confirms this alternate
queue payload and existing pure action composition fit that size with the compatibility
tests below. If they require broader queue/API changes, split two questions
under this owner before admission: (A) contextual payload law/legacy compatibility,
(B) manual preparation/payment ordering and validation precedence. No new card or split is
performed here, and InProgress status is not approval of either implementation.

## Later RED matrix and limits

(己, p=0.99) After design review, write tests before source changes:

| Real production boundary | Required assertion |
| --- | --- |
| Dev-window opt-in submission versus existing public input setup without it | Actual callback emits the contextual queue entry only when explicitly configured; plain Atom/no-capability dispatch preserves focus-based behavior. Legacy IFn (including map/keyword callables), reset and map-result guard controls remain unchanged; invalid capability/context never falls back. |
| Actual GLFW palette press → IntentAtom → sim-loop, after a real integrator fold | New sculpt anchor uses current Spark-plus-offset relative to the existing committed target; no manually queued idealized focus-follow in the test. |
| Zero/nonzero common frame translation and non-collinear movement | Same relative pose yields the same anchor; actual movement changes the aimed face. Preserve physical columns. |
| Two contextual requests interleaved with legacy narrow/thrust/camera entries and a later reader in one drain | FIFO charge/record order, radius/intensity preserved, later spending sees remaining funds, prior record unchanged; later reader observes refreshed attention, including after valid-aim denied payment. |
| Host offset/mode change before snapshot versus during drain; mid-drain arrival | Exact `C_n` rule, including manual↔tracking; tracking path unchanged and no per-action host reread. |
| Invalid explicit offset, absent-key default, absent Spark position, missing observer/commitment, denied palette/Resonance, including combinations | In manual mode, no observer no-ops before aim validation; otherwise aim validation precedes domain gates, failures retain exact W_j, and valid-aim denied gates retain prepared P_j with no charge/record. Tracking retains unchanged request-op validation/gate order, including malformed verb/magnitude with no observer. No stale-aim fallback/partial charge; later valid input recovers. |
| Press/repeat/release and real sculpt fan-out/fold | One paid request per physical press; existing magnitude, target choice, gate/spend, immutable records and Jacobi/barrier behavior unchanged. |

(己, p=1.0) No tests, source changes, native actions, fixtures presented as
gameplay, or runtime measurements were performed for this plan. Later native
acceptance must use ordinary controls and a naturally committed world; a
deterministic test fixture does not prove reachability. Pursuit/local-frame
assistance, published HUD lag, G/H/J placement timing, controls, costs, actor
creation, embodiment and Gate production are outside this slice.
