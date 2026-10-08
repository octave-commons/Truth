# Finite native Gate energization

**Planning proposal; no implementation admission or native success.**
Owner: `59da3d85-c9b6-488f-b33b-5442c3e784a4`, under
`embodied-character-voxel-mode`. Grounding:
[finite-scope evidence](../notes/2026-10-08-finite-native-gate-scope.md), the
[Spark flight design](spark-flight-and-camera.md), and the independently reviewed
[endpoint proposal](https://github.com/octave-commons/Truth/blob/01f0909b0c1cd5b86c274add98dfbf1b1146c2a4/docs/designs/gate-endpoint-provenance.md).
That endpoint proposal remains planning-only and is not silently rewritten here.

## Selected product boundary

The player controls the existing physical Spark in one supported live ECS scene,
approaches an initially cold Gate, presses **E** to energize it locally, and sees
its structure and aperture respond. The initial scene is explicitly authored.
The Gate is a persistent ECS device whose response follows an accepted ordinary
input, not a static scene prop, notification, invented discovery or REPL mutation.

This limited player choice does not complete evolved-person embodiment. Local
energization is a new operation distinct from the endpoint proposal's
receiver-bound activation attempt. The HUD says **"Gate energized · no link"**;
the aperture shows a local luminous response, never a destination view or a
"connected" label. Connection and travel retain their pre-existing receiver and
independently attributable technology/construction requirements.

This design proposes an **authored-initial-condition** provenance category for
this scene. It does not promote authored facts to naturally simulated events.
Review must explicitly accept that amendment and Spark/local-operation scope
before implementation. The full historical ladder remains a later milestone.

## Initial identity, provenance and physical scene

A versioned, closed scene manifest records a stable template ID and content
digest, author/source reference, declared producer, technology declaration,
construction declaration and initial operability. Every historical declaration
is tagged `authored-initial-condition`; it names the declared history and device
and has an explicit causal reference. Do not create a fictitious living actor
or civilization component just to satisfy a reference.

Each launch allocates a fresh run/history instance identity and fresh Spark and
Gate identities through the existing ECS allocator. Template identity and local
EID are not world identity. The constructor validates the complete manifest and
all numeric inputs before returning a world; duplicate or contradictory identity
references refuse construction without publishing a partial world. One real
tick-zero scene-instantiation event references the manifest. No backdated
simulated construction, `gate-discovery`, agency award or resonance award is
emitted. Initial agency remains zero. No receiver or connection is seeded.

Extract only shared empty-ECS/ledger initialization from the existing bootstrap
owner. Do not create and discard a nebula, pass zero gas through its gas-mass
division, tag a Gate as a planet, or use a second world representation.

The proposed initial settings are deliberately astronomical, not person-scale:

| Setting | Value and meaning |
| --- | --- |
| Physical Spark | Position `[0,0,0]`, velocity zero; existing mass-zero test-particle law at formation progress zero; initial radius `1e12 m` through its existing radius writer. |
| Physical Gate | Position `1e14 m × camera-forward(-90°, -20°)`; zero velocity; mass `1e20 kg`; outer extent `5e12 m`; explicit aperture basis. Distinct Gate body kind and components, no matter-state tag. |
| Interaction | Inclusive center distance `1.5e13 m`, checked from current physical component positions. Initial distance is outside range and extents do not overlap. |
| Clock and thrust | Fixed `dt=1e6 s`, adaptive pacing false, displacement `D=1e11 m/tick`, retention `.97`. The existing thrust floor is satisfied; equilibrium speed is `1e5 m/s`. |
| Other initial forces | Existing gravitational constant; observer-halo and dark-matter mass factors zero as declared empty-space conditions. No pre-seeded intervention, absorption or force requests. All normal systems remain installed. |
| Camera | Existing `1e15 m/ru` conversion; orbit `.01 ru`, minimum `.002 ru`, yaw `-90°`, pitch `-20°`, look `.1°/px`, zoom sensitivity `5`. |

Use `genesis/tick-world`, the existing registered fan-out, integrator and frame
handling. The scene's continuation adapter must preserve this world and its
identity. It must not apply `arc/tick-genesis`'s zero-matter termination or the
default dev launcher's inactive-world replacement. Empty-matter summary and
system safety are required tests, not established by this proposal. Do not
disable physics, use identity ticking, reset momentum during approach or repair
an invalid running scene with a replacement world.

The existing launcher gains one explicit scene selection, proposed as
`clojure -M:dev --scene gate-local`; the default nebula route remains unchanged.
Its argument validation must reject unknown selections. This supported route
owns the initial conditions; the verifier cannot write them into a running world.
Exit uses the existing window lifecycle and shuts down the owned threads.

Scene camera defaults must survive launch, R/reset and deselection. Today launch
does not admit the needed zoom option, zoom otherwise floors at `10 ru`, reset
returns to `50 ru`, and deselection discards `:zoom-min`. Extend those existing
option/reset owners with validated scene defaults; preserve exact legacy values
when no scene defaults are supplied. This is not a new camera or renderer.

## Movement derivation and its practical limit

At fixed dt, the existing delayed Euler/thrust recurrence starts with no motion
on tick 1 and `3e9 m` displacement on tick 2. Ignoring gravity and frame offsets,
held input gives displacement `2.6766925123e13 m` after 300 ticks and
`8.5166666667e13 m` after 884, entering range. After 900 held ticks it gives
`8.6766666667e13 m`. Release retains the pending channel; further coast tends to
`.97 D / .03 = 3.2333333333e12 m`. At tick 1200 the derived total displacement is
`8.9999741543e13 m`, inside range and outside the combined extents.

This is a source-equation calculation, not observed gameplay or a performance
claim. The actual registered map/SoA paths must validate range entry, finite
state, gravity/frame consistency and bounded release. Nine hundred ticks would
be 15 seconds at the loop's 60-Hz target and represent about 28.5 simulated years;
neither target throughput nor wall-time physics is promised. Camera and range
feedback must make the ordinary approach usable at actual throughput.

## One local request, one authoritative transition

The input adapter allocates an operation identity once for an unmodified
`GLFW_PRESS` on E while world input is active. Release, repeat and UI-captured
keys enqueue nothing. A visible, actually drawn device core is selectable with
the existing picker; line-only geometry or an invisible picking proxy is
insufficient. Missing selection refuses explicitly. No automatic retargeting is
allowed after a selection goes stale.

Existing picking enters `:follow-selection`, where manual thrust is inactive.
Ordinary C cycling returns to `:manual` and retains selection. The scene's
instructions and input tests must cover that existing route, and keep scene
zoom defaults intact through selection and deselection. Camera following is
never counted as physical approach; no global camera policy change is proposed.

The request binds run/history identity, requesting Spark identity, Gate EID and
immutable device identity, expected local revision, observed tick, operation
identity and requested verb. A future observed tick refuses; old requests
revalidate the current consuming snapshot. Queue it
through the existing `IntentAtom`; acknowledge success only from the resulting
world. The domain boundary names its Malli validator, rejects malformed requests
and uses the consuming tick's physical positions, not camera/focus distance or
cached render positions. Direction of gaze is not an extra range predicate.

Use the existing sculpt/commitment execution pattern:

1. The serial input function records a validated world-level command.
2. A registered `:gate-local` fan-out owner reads Gate identity/state, Spark
   identity and both physical positions from the frozen snapshot. It alone
   writes the new Gate-local state and dedicated player-facing outcome component.
   All component reads/writes are declared. No position, velocity, observer,
   mass, radius, agency or resonance component gets a second writer.
3. Immediately after the fold, **before `materialize-lifecycle` can reap the
   outcome carrier**, the existing lifecycle/event boundary publishes the decided
   outcome through the real ledger and then retires the processed command. It does not
   re-admit range or change the state decision. A dispatch performed inside a
   write-set function would be lost and is not acceptable evidence.

The intended local law is:

Local revision starts at `0`; only `dormant → energized` advances it to `1`.
Refusals and replays never advance it. Resolve an exact retained operation replay
before checking the current revision; conflicting reuse of that identity fails
integrity. For a distinct operation, a stale expected revision refuses before
fresh transition or already-energized evaluation. Thus an ordinary new press
observing revision `1` can report already energized, while an old revision-`0`
request cannot be reinterpreted as fresh consent to the newer state.

| Consumed condition | Outcome |
| --- | --- |
| Same live run, designated live Spark, same existing Gate identity, dormant and locally ready, finite physical range satisfied | Change `dormant → energized` once; preserve immutable identity/history and publish one `gate-local-energized` event with operation, actor, device and tick. |
| Out of range | Explicit refusal; Gate and unrelated state unchanged. A fresh later press after moving may succeed. |
| Missing/stale/replaced device, wrong run/history, wrong actor or not locally ready | Explicit refusal; no receiver creation, retargeting, Gate transition, charge or reward. |
| Already energized, distinct new operation | Report already energized; no second accepted transition, reward or charge. Energization is persistent for this scene. |
| Exact repeated operation and payload, including a previously refused operation | Reuse its original outcome; no second ledger event. A new press needs a fresh operation identity. |
| Reused operation identity with a different payload | Integrity refusal/error; preserve the old outcome, with no replacement event under that identity. |
| Malformed/nonfinite input | Validator refusal, no command publication or partial mutation. |

Refusal to a missing Gate is recorded on the requesting live Spark's dedicated
outcome component, not on a fabricated Gate. If the Spark is already gone at
serial admission, refuse without queuing. If it disappears after admission,
or its identity changes before the frozen decision snapshot, the execution
context is invalid: fail the fold visibly without a Gate/outcome component write,
invented business result or cancellation. The existing sim-loop error guard
retains the drained pre-tick world including the request, records error artifacts
and stops ticking through its error state. This is not automatic retry or command
retirement. A missing decision for a live actor is also an integrity failure.

A Spark valid in the decision snapshot may be reaped later by lifecycle handling;
that cannot retroactively cancel its already-decided and published result.
Likewise a Gate removed after acceptance produces no current device projection;
its historical accepted event does not prove it remains operable. No dead entity
is resurrected. Fold the request batch FIFO against one frozen physical snapshot
and accumulating local Gate state/results so two queued operations cannot both
accept revision zero. Post-fold publication failure must retain the pre-tick
world and requests through the existing error guard, without publishing a
component-only success. Tests must prove both actor-loss moments, same-fold Gate
removal, failed publication, duplicate retirement and exact replay semantics.

The local operation has no modeled resource charge, force or energy transfer.
Local readiness is a declared initial device property. This bounded fictional
power-up policy supplies neither a free-travel law nor a physical energy model;
existing discovery awards cannot supply its price. Unrelated world and player
state must remain unchanged except for ordinary physics and causal records.

## Visible result and native acceptance

The existing renderer projects the actual Gate identity, location and local state.
A cold structure becomes visibly energized: contrasting luminous ring/aperture,
persistent state contrast and clear on-device feedback. Use the existing shape
and material paths; the appearance is a local field, without a destination view.
Expose physical distance/range, E affordance and refusal reason through existing
HUD surfaces. An absent Gate yields no device projection or stale success badge.

The same native run must capture: declared initial manifest and identities;
changing ECS ticks; Spark initially outside range; ordinary movement reducing
physical separation; an outside-range refusal; one ordinary accepted E press;
matching Gate state and ledger operation; and clear cold/energized frames. A
second press must not produce another accepted event. Include a short actual
movement-and-response recording when the bounded capture supports it. Unit
fixtures, projection tests, notification text and unrelated screenshots cannot
substitute for this run.

Choose and freeze one finite native profile after the reviewed implementation
and gates are ready. Publish its source pins, paths, time/storage budgets and
owned process identities before release; preserve failed attempts and verify
cleanup. The earlier G04 attempt and its timing proposal are historical evidence,
not an admitted Gate input primitive or a retry authorization.

## Implementation breakdown after planning review

These are proposed slices within the fixed scope, not admitted cards or work:

1. **Gate domain contract and registered operation — 5 points.** Closed identity,
   initial-provenance and local-state shapes; pure decision law; request adapter,
   actual registered owner, outcome publication and retirement. RED covers every
   row above, equal EIDs across runs, actor/device loss, replay, no duplicate
   ledger result and unchanged unrelated components. No native launch/rendering.
2. **Supported scene and camera lifecycle — 5 points.** Consume the reviewed Gate
   contract; share validated empty bootstrap, initialize the two physical entities,
   supply launch selection and real tick continuation, and preserve scene camera
   defaults. RED covers invalid manifests, repeated construction, registered
   map/SoA ticks, movement/release, no fake rewards, no silent world replacement,
   zoom/reset/deselection and unchanged default nebula behavior.
3. **Ordinary input and visible Gate response — 3 points.** E press/selection/queue
   wiring, visible selectable projection, physical range/refusal HUD and local
   energized effect. RED covers repeat/UI capture, stale selection, queued current
   positions, projection identity and accepted/refused state-to-visual mapping.
4. **Native verification — existing 3-point owner**
   `f369c598-279c-498d-a64f-45d2ce16ad34`, after explicit scope reconciliation.
   Execute and capture the acceptance above; do not relabel fly-resolve-sculpt
   evidence as a Gate result.

Planning references are `98b847ce` and native `5ce5deca`. Select and pin the actual
implementation base before RED; account for their source differences explicitly.
No sibling fix is assumed merged. All production slices retain the full test
suite, six strict static gates, registry/architecture checks, meaningful
changed-path performance comparison and exact-head review. No source or runtime
admission follows merely from these estimates; split a slice again if review
finds it genuinely exceeds five points, within the same fixed boundary.

Full origin simulation, represented Life/civilization, avatar lineage, terrain
tools, cross-world transport, full save/menu and wider polish remain outside this
batch. Newly discovered unrelated work belongs to a later Rheos batch.
