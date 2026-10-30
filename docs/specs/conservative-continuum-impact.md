# Conservative refinement — controlled continuum impact

Research story: compare the same local material impact at coarse, fine and
adaptive resolution, so refinement cannot create new contact-overlap energy.

One ECS region component owns a finite plane-strain patch and its internal
impactor degrees of freedom. The existing integrator is the only writer of its
physical state; the existing renderer displays it. The local closed experiment
has no self-gravity, external work or exchanges with the rest of the world.
It is not a new planetary engine or a claimed rock/shock calibration.

Contracts established before implementation:
- Linear triangles use a consistent mass matrix, not lumped nodal masses.
- Piecewise affine interpolation on four-way triangle subdivision preserves
  material stock, mass, linear/angular momentum, kinetic and elastic energy.
- Compressible neo-Hookean elastic energy and forces use the same density law
  at every resolution. Inverted elements fail loudly.
- Contact uses fixed physical surface quadrature and one penalty-energy law;
  its stiffness and samples do not change with the material mesh size.
- Damage can only decrease stored elastic energy; released energy is recorded
  as heat. Damage is softening, not topological fracture or ejecta generation.
- Any viscous operator is objective, dissipative and returns lost kinetic
  energy to thermal stock. It cannot silently remove energy or spin.
- Adaptive refinement occurs before predicted contact. Coarsening is admitted
  only if projection and accounted energy/momentum tolerances pass; otherwise
  detail remains active. No energy correction is hidden in heat.
- Run the same impact coarse/fine/adaptive at dt and dt/2; show errors, heat,
  stress/deformation, retained inventory and actual mesh changes.

The plane-strain experiment is extruded in the native 3D view. This isolates a
material/refinement mechanism; fully 3D impact fracture and planetary coupling
remain separate work.
