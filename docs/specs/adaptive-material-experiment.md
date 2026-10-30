# Adaptive material experiment — bounded prototype

Research story: follow the same finite material through assembly, collision,
quiet coarse evolution and disturbance-driven reactivation, so that the future
planetary lifecycle can change models without replacing its world.

Contract established before implementation:
- A quiet connected, bound group may become one translating ECS aggregate.
- Its packet retains every cell's local geometry, relative velocity, stocks,
  internal energy and damage. It is a reversible cache, not discarded detail.
- No cell is both active and cached. Reconstruction preserves source identity.
- Mass, elements, momentum and intrinsic angular momentum survive handoffs.
- The packet retains self-gravity, contact and relative kinetic energies.
- Finite-time coarse validity is limited by a displacement estimate, a maximum
  interval, and a near/approaching neighbor guard. It is not a calibrated bound.
- Coarse morphology does not rotate or relax. Residual motion and acceleration
  therefore limit the interval. A detailed shadow run measures the actual error.
- Smooth surfaces reconstruct a scalar field for display only; raw cells remain
  inspectable. No chemistry, fracture strength, shock EOS or increased physical
  resolution is claimed by this experiment.

Acceptance observations: automatic quiet entry; active entity reduction;
projectile-triggered reactivation before contact; no missing/duplicated stocks;
preserved cached geometry/damage; measured shadow error and integration error.

## Rotating coarse state and finer cells — next experiment

Research story: retain aggregate spin during coarse evolution, and compare a
32 m material discretization with the 64 m result, so that model validity is
measured against changing physical resolution rather than display smoothness.

Pre-implementation contract: split relative velocity into fitted rigid rotation
and a residual with zero net linear/angular momentum. Carry world-frame angular
momentum; evolve orientation with the torque-free rigid-body equations. Return
rotated positions and velocities to the same ECS at reactivation. Keep residual
motion unresolved, and quantify its error against detailed evolution. Track
angular momentum and energy as well as positional error. A spatial subdivision
is a resolution sensitivity experiment: it changes the discrete material model
and cannot by itself establish continuum convergence.
