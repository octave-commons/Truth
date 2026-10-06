# Five-point body-trails implementation contract

Status: root-reviewed contract; canonical transition and RED checkpoint precede implementation.
Worktree: `truth-motion-trails`, branch `codex/truth-motion-trails`, base `e71b12f`.
Task: existing `body-trails-ringbuffer` (5, no dependencies).
Authority: [accepted design §6.1](../designs/spark-flight-and-camera.md#61-body-trails).
Grounding: [current clock, frame, eligibility, and renderer evidence](research/2026-10-06-body-trails-rendering.md).

The existing §6.1 links the grounding note and this scoped contract. Root owns
the canonical Rheos record, transition, and RED checkpoint before implementation.
The rendering requirement is already accepted; the defaults below
are explicit engineering choices for that requirement, not research findings.

## Exact domain contract

- Add one bounded trail component and one registered fan-out writer. Do not
  write positions, velocities, camera state, or other systems' components. Wire
  into the existing genesis parallel system list, not a new post-fold phase.
- Put new pure timestamp, bounded-history, frame-translation, opacity, and
  allocation logic in portable `.cljc` where practical. Keep JVM ECS/GL
  adapters outside those portable decisions; data remains Clojure-shaped.
- Eligibility: positioned `:spark` bodies; explicit `:star`, `:planet`, or
  `:gas-giant` matter states; and explicitly tagged `:body/planet` entities
  except diffuse `:nebula`. Bare `:planetesimal`, `:body/rocky`, dust, collision
  fragments, and other populations are excluded. This deliberately does not
  add protostar/brown-dwarf trails. Filter before history work; clear the
  owner's stale trail column when an entity ceases to qualify. Normal entity
  despawn already removes its components.
- Defaults: capacity 64 samples/body, cadence 1e10 simulated seconds,
  and a 6.3e11-second visible history horizon (about 20,000 simulation years).
  These are named tunables, not render-frame counters. They cover the measured
  Phase-0 dt with a few ticks per sample and retain several samples at the
  normal maximum dt. Later slow time means fewer new samples per wall second;
  the current head connection below provides immediate visible displacement.
- Each stored sample is `{sim-time, world-position}`. Read the real
  `:genesis/sim-time` and `:sim/dt`; never substitute wall time or render frames.
  First eligible observation samples its input position at input time.
- Subsequent sampling uses an absolute next deadline. When due, append at most
  one actual observed position, then advance the deadline arithmetically past
  the current time. Never loop once per missed interval, duplicate one position
  as invented historical samples, or interpolate an unobserved orbit.
  Record skipped-deadline count as inspectable trail data.
- Bound the sample count and expire points outside the configured time horizon.
  Pruning is assessed against output/published time (`input time + input dt`)
  so a huge physics step cannot publish an apparently recent stale tail.
  An interval longer than the whole horizon can honestly leave no renderable
  historical segment. No fabricated continuity across that interval.
- Apply the current `:genesis/frame-offset` translation to every retained
  historical position on every fold, even when no sample is due. A newly
  recorded input position gets that same shift once. Its timestamp stays the
  input time. This aligns histories with the integrator's output frame without
  claiming to know its future trajectory.
- The renderer may connect the latest retained sample to the current published
  body position/time as the visible head. That is projection of actual state,
  not an additional history writer. Avoid duplicate zero-length head segments.

## Exact renderer contract

- Extend the existing `:line` path. Project stored world positions through
  `units/world->render`; retain the scene's existing render-origin subtraction.
  Emit two endpoints per adjacent segment for current `GL_LINES`, never a
  polyline whose boundary joins one body to the next.
- Carry a distinct per-vertex opacity through shape law, buffer packing/vertex
  layout, and line shader. Preserve current non-trail lines' 0.85 default
  opacity. Do not reuse density or size as a disguised opacity channel, or
  pretend that a discarded fourth RGB component supplies alpha.
- Fade by simulated sample timestamps from transparent oldest retained point
  to the ordinary line-opacity limit at the current head. Equal timestamps
  cannot produce division-by-zero/NaN. Keep RGB separate from opacity.
- Global budget: 4,096 line segments. Build per-body contiguous newest-first
  segments, then allocate in deterministic round-robin body order (spark first,
  remaining IDs stable) so one long history does not consume all coverage.
  Emit complete segment pairs only.
- Projection returns requested/rendered/dropped segment counts. When the cap
  binds, the infra adapter reports the dropped count visibly in diagnostics,
  suppressing identical repeated reports rather than logging every frame.
  Preserve a pure geometry/summary function for tests. No silent truncation.
- Domain work is O(eligible bodies × 64); rendered geometry is capped. No
  spatial query or per-nebula inner loop is introduced.

## RED tests, in priority order

1. Eligible star/planet/GI/spark histories appear; large nebula/dust/shatter
   populations do not. Loss of eligibility clears only the writer's component.
2. Exact cadence boundaries and no sample before deadline. Compare timestamps
   and positions only at shared actual observation/deadline times across step
   partitions; do not require reconstruction of observations a larger step
   never made. Explicitly test skipped deadlines without invented path samples.
   History count/time bounds hold and huge dt performs bounded work. Pause/no
   advancing time does not accumulate render-frame history.
3. Real `tick/run-parallel` with the existing integrator plus trail writer:
   nonzero repeated frame shifts keep current body and old/new samples in one
   frame. Include a tick when sampling is not due, and ensure no double shift.
4. One writer and complete registry reads/writes; pipeline inclusion and
   architecture conflict check. No position/velocity writes by trails.
5. Projection uses a non-default view scale correctly, joins only adjacent
   samples of one body, connects the current head, and emits finite fading
   opacity with complete paired vertices. Existing raw-line recentering applies
   once; no body-radius/model-matrix transform touches trail points.
6. Many-body overflow stays at 4,096 segments, gives deterministic coverage,
   and reports the exact dropped count. No-cap data retains every segment.
7. Packed line opacity reaches the shader input and preserves the old default;
   pure shader/law tests plus actual OpenGL compilation and native pixels verify
   fading. A mocked shape assertion alone is insufficient.

## Verification and boundaries

Run focused tests with a bounded heap after RED review/checkpoint. Root will
coordinate full suite, strict static gate, architecture check, and before/after
hot-path benchmark after the active natural-seed runs finish. No full suite,
strict gate, benchmark, or extra native runtime until that coordination.

Player-visible acceptance is a normal native world showing star, natural
planet, and real manual spark trails with visible fading and no fragment haze.
Use video plus full-window GIF, actual state/control readbacks, and hash the
closed evidence. Tests may use isolated fixtures; do not inject a fixture into
that acceptance world or claim that trails complete approach/binding/sculpt.

Expected source areas: component/registry + a small domain trail owner/law,
genesis wiring, render trail projection and existing line adapter/shader/law,
focused domain/render tests, and the existing design's grounding/contract note.
No flight-force/assist changes, target selection, camera semantics, physics law,
planet eligibility, world resets, or Gate progression belong to this card.
