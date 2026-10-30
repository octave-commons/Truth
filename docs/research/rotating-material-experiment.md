# Truth — rotating aggregates and finer material, 2026-10-24

Research story: carry spin through a cheaper aggregate model and measure the
impact of finer physical cells, so that model switching stays tied to observed
error on the way toward the accepted nebula-to-habitat/Primordia lifecycle.
The experiments are local physical continuations, not a connected nebula-to-life
run. (己, p=0.99)

## Rotating coarse model

An ordinary ECS material packet now optionally carries world angular momentum,
a body-frame inverse inertia tensor, and a unit quaternion. The existing
integrator advances orientation with torque-free RK4. Relative velocity is
split into fitted rigid rotation and a residual with zero net linear/angular
momentum. Coarse observations rotate geometry and residual velocity; reactivation
returns current positions and velocities to the same ECS. No second world or
renderer was introduced. The optional mode leaves archived translating runs
reproducible. (己, p=0.99)

The paired trial starts from the saved 84-cell state and declares an added
rotation of 0.0002 rad/s about z. All continuations use dt=0.25 s and Barnes–Hut
theta=0.5 for 120 s. Forced coarse shadow arms intentionally bypass the normal
validity time/displacement limits to measure disagreement, not to extend normal
automatic admission. (己, p=0.99)

| Model | Active bodies | Final RMS position error | Maximum error |
| --- | ---: | ---: | ---: |
| Detailed 64 m reference | 84 | 0 | 0 |
| Rotating coarse packet | 1 | 3.263738 m | 18.676567 m |
| Translating coarse packet | 1 | 3.957348 m | 18.306185 m |
| Subdivided 32 m trial | 672 | 11.213925 m | 21.585404 m |

Errors are mass-weighted and aligned by COM. Finer cells are aggregated by their
source parent before positional comparison. Rotation improves this trial's RMS
error about 17.5%, while its maximum is slightly worse. Internal deformation
still matters. Rotating-arm angular momentum changed by about 1.26e-16 relative
to its initial value; the floating-point total-energy readout was unchanged.
These are bounded observations, not universal accuracy guarantees. (己, p=0.99)

## A failed refinement assumption

Eight-way subdivision of the already contacting body preserves extensive
material, internal heat and momentum to roundoff. It also reveals intra-cell
gravity and changes contact geometry. The discrete state's mechanical energy
jumps upward by 4,297,137,834 J. No compensating heat or unexplained conservation
claim is applied. This is an initialization sensitivity test, not an admitted
conservative refinement operation. Its later trajectory is not a clean accuracy
comparison against the 64 m model. (己, p=0.99)

## Fresh cloud: 64 m versus 32 m

Both new branches begin at the original macro cloud, before neighboring macro
carriers contact. Each of the 81 original carriers is either retained or divided
into eight cells, producing 648 fine cells. Both runs use dt=0.5 s, theta=0.5,
softening=8 m and the same material/contact parameters, through 2400 s.
Material inventories and momentum initially match to roundoff. Each discretized
model has its own mechanical-energy baseline. (己, p=0.99)

| Discretization | Largest final spatial cluster | Mass fraction | Maximum energy drift |
| --- | ---: | ---: | ---: |
| 64 m, 81 cells | 81 cells | 100% | 7.683680% |
| 32 m, 648 cells | 634 cells | 98.542763% | 5.445122% |

Cluster membership uses the existing material-neighbor reach of 1.18 times the
sum of cell radii. Fine material outside the largest cluster remains simulated.
Lower measured drift in this case does not establish continuum convergence:
subdivision changes the discrete granular response. Spring/dashpot parameters
are not a calibrated strength model or shock EOS. (己, p=0.99)

## Visual and verification evidence

Native recordings retain the same renderer. Smooth surfaces use a 6 m display
field; raw cells make the 64 m / 32 m physical distinction inspectable. The
rotation comparison has 52 exact-state captures per view, and the cloud
comparison has 22 per view, across composition, raw and resolution overlays.
The browser preview was checked for matched-time model switching, raw-cell
switching and image load. (己, p=0.99)

Final focused checks passed 34 tests / 131 assertions with zero failures/errors,
including single ECS, one renderer, single-writer registry, torque-free energy,
rotating handoff, retained spin and missing-orientation rejection. The complete
strict static gate remains unavailable because required local executables are
missing. Historical results remain unchanged. No PR or merge was performed.
(己, p=0.99)

Next discriminating step: design a refinement/material-response rule whose
geometry and energy remain compatible across cell sizes, then test deformable
coarse states against detailed references. Reaction chemistry, atmospheric
fluids, planetary geology and physical Primordia exchange remain future layers.
(己, p=0.95)
