# Spark rotation integration

Date: 2026-10-06. Status: researched implementation contract; verification pending.
Scope: `spark-orientation-angular-momentum`, backing
`docs/designs/spark-flight-and-camera.md` §3.2. GPL-3.0-or-later.

## Provenance and findings

The flight design's original `scratchpad/spark-flight-controls-research.md`
reference is absent in this checkout and has no entry in available Git history
(`git log --all -- scratchpad/spark-flight-controls-research.md`). This note
restores grounding for the rotational substrate only. It does not recreate the
missing Elite/Ace Combat research or imply those comparisons were verified.

Solà's primary paper distinguishes quaternion component order, algebra, and
frame conventions. With scalar-first Hamilton quaternions mapping body vectors
to world vectors, world angular velocity multiplies on the left; body angular
velocity multiplies on the right. Constant-rate integration is the product with
an exponential rotation increment. These choices must be explicit to prevent a
heading that works from identity but rotates about the wrong axes later.
[Solà 2017, §§3, 4.5.1, 4.6.1](https://arxiv.org/html/1711.02508v1).

The AHRS project's own integration documentation gives the closed-form
constant-rate update and recommends normalization to counter floating-point
roundoff. Its approximation assumes angular rate is constant within the step;
that does not make it an exact solution for arbitrary time-varying torque.
[AHRS AngularRate documentation](https://ahrs.readthedocs.io/en/stable/filters/angular.html).
Both sources were retrieved on 2026-10-06.

## Mapping into Truth

These are implementation decisions within the existing approved design,
not empirical claims from the cited sources:

- `c/orientation` is `[w x y z]`, scalar first, unit norm, body-to-world.
  Identity is `[1 0 0 0]`.
- `c/angular-velocity` is a finite world-axis vector in radians per simulation
  second. It is distinct from the existing stellar `c/spin`, whose evolution
  and writer remain unchanged.
- One `:rotation-integrator` fan-out system owns both columns. It selects only
  entities with rotational state, not every gas parcel. Missing one member of
  a rotational state pair is a contract error, not an implicit reset.
- Torque sources are listed under `:angular-velocity` in the existing
  `domain.integrator.base/influence-registry` and summed by its existing vector
  accumulator. This slice has no production torque emitter; the force-channel
  card supplies those next. An empty source list means conserved free spin.
- The approved constant-inertia option is used first: `I = 1 kg m²`. This is a
  tunable gameplay proxy, not the geometric inertia of the cosmological spark.
  Its finite positive value and the angular speed bound live in `law/`.
- The always-on speed ceiling starts at π rad/s, explicitly a safety bound
  awaiting input playtesting, not a claim that this is the final flight feel.
- Advance angular velocity with `ω' = clamp(ω + Στ Δt/I)`. Advance orientation
  with the exponential increment for `ω'`, normalized after multiplication:
  `q' = normalize([cos(θ/2), axis*sin(θ/2)] * q)`, `θ = |ω'| Δt`.
  Zero rate preserves orientation. This is a semi-implicit torque step; exact
  free-spin integration and first-order torque dynamics are different claims.
- Every input is from the frozen tick snapshot. A torque emitted in the same
  fan-out is consumed on the following tick, matching the existing Jacobi lag.

## Time-step boundary

`domain.genesis.systems/physics-systems-parallel` receives the live world's
`:sim/dt` and constructs the linear integrator from it each tick. Rotation uses
that same explicit argument, in simulation seconds; it does not invent a
wall-clock, assume 60 frames, or divide away dilation internally. Invalid or
missing `dt` fails at the boundary. Zero `dt` leaves state unchanged.

Physical radians/second alone do not solve flight feel when a tick spans
billions of seconds. The subsequent force/input card must specify how player
commands produce a useful angular rate under dilation. This slice verifies
physical integration across `dt` values and does not claim felt controls.

## Required verification

Malli contracts reject zero/non-unit/non-finite quaternions and malformed angular
velocity. Spawn and explicit legacy-load repair supply identity and zero without
overwriting existing orientation. Tests must establish analytic quarter-turns,
noncommuting rotations from a nonidentity starting attitude, persistent free spin,
normalization over many steps, correct integration at fractional and dilated
`dt`, torque summation, hard speed clamping, single-writer ownership, actual
physics-table wiring, and identical serial/parallel snapshot semantics. Include
a scripted torque pulse followed by free coast; input and heading rendering
remain later cards.
