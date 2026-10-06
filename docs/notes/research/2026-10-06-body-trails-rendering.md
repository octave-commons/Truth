# Body-trail rendering and clock evidence

Date: 2026-10-06. Source inspected: `e71b12f`, with native observations loaded
from `81207e7`. This note contains observed implementation facts and their
consequences; it does not promote proposed trail defaults to established facts.

The accepted visual requirement is [spark-flight-and-camera §6.1](../../designs/spark-flight-and-camera.md#61-body-trails): recent motion paths for the star,
planets/protoplanets, and spark, sampled in simulated time, capped, fading,
and excluding dust/fragments. The canonical `body-trails-ringbuffer` card is
TODO, estimate 5, and explicitly has no flight-card dependency. The existing
rotation research addresses rotation integration, not trail cadence.

## Observations

1. `domain.genesis.tick/step-physics` runs one Jacobi fan-out. The integrator and
   other systems read the same input snapshot. `advance-simulation-clock`
   advances `:genesis/sim-time` by that snapshot's `:sim/dt` after the fold.
   Therefore a position read by a trail writer is located at the input time,
   not the output time. See `src/domain/genesis/tick.clj:79` and `:178`.
2. `domain.spatial.index/spatial-index` computes the input snapshot COM and
   stores `:genesis/frame-offset`; kinematics subtracts this translation from
   every output position. Historical positions need the same translation on
   every fold, including folds without a new sample. Otherwise apparent trail
   motion includes the changing coordinate frame. See
   `src/domain/spatial/index.clj:89` and
   `src/domain/integrator/kinematics.clj:480`.
3. Normal pacing bounds `:sim/dt` from 1e7 to 5e10 seconds; time slip can increase
   it further. A fixed-capacity history without time-based expiry does not
   imply a fixed simulated-time span. See `src/domain/pacing.clj:42` and `:160`.
4. Native manual-flight samples at the planet-formed stage observed
   `:sim/dt = 4.13802944301184e9` seconds. The 30-second real W burst reduced the
   recorded target distance from 264,914.58 AU to 30,464.18 AU. Actual images
   showed little heading/depth guidance and dominant cyan field-line clusters.
   The closed evidence is in the sibling `truth-playable-gate` worktree at
   `.ημ/diagnostics/playable-foundation/manual-flight/README.md`, with exact
   hashes in `SHA256SUMS`. This was not an isolated performance benchmark.
5. `infra.render.scene.setup/render-lines-pass` already draws paired vertices
   with `GL_LINES` through the raw-position path, alpha blending enabled.
   `infra.render.shader/line-program` nevertheless hardcodes output alpha 0.85;
   its vertex input is RGB. `infra.render.mesh/particle->floats` packs RGB and
   drops a fourth color element. Merely emitting RGBA shapes cannot produce
   fading trails. See `src/infra/render/scene/setup.clj:119`,
   `src/infra/render/shader.clj:323`, `src/infra/render/mesh.clj:115`.
6. World-to-render conversion already belongs to `infra.render.units`. Scene
   setup then subtracts the render origin from raw line positions. A new trail
   must use those existing transforms exactly once. See
   `src/infra/render/units.clj:30` and
   [the existing coordinate research](../../research/unit-coordinate-transforms-game-render.md).
7. Collision shatter emits untagged `:planetesimal` entities; condensation seeds
   also use that state with `:body/rocky`. Core-accretion planets explicitly
   use `:body/planet`; GI embryos use `:gas-giant`. Thus all `:planetesimal`
   entities cannot be treated as protoplanets without also trailing fragments.
   See `src/domain/stellar/merge.clj:43`, `src/domain/stellar/seeder.clj:114`,
   `src/domain/planet_formation/orbit.clj:102`, and
   `src/domain/stellar/disc_evolution.clj:415`.

## Consequences

A trail implementation needs one component owner, explicit sampling timestamps,
a bounded policy for gaps larger than the cadence, historical frame translation,
and real per-vertex line opacity. Its cost should scale with significant bodies
and bounded history, not nebula-neighbor pairs. A visual acceptance run must
show actual fading and readable paths; schema tests alone cannot establish that.
No additional flight-assist, camera targeting, or commitment semantics follow
from this evidence.
