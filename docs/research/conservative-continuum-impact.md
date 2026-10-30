# A compatible refinement cycle in a controlled impact

24 October 2026. This is a numerical mechanism experiment, not calibrated rock
physics. Published record: <https://truth-planetary-lab.enso-err.chatgpt.site/continuum-impact/>.

## Question and result

Can an impacted material patch move from coarse to fine resolution and back
without the contact-overlap energy creation seen in the earlier sphere model?
Within the bounded formulation below, yes. The dt=0.25 s adaptive run refines
at 54.75 s with zero measured mass, linear/angular momentum and accounted
energy residual. It rejects 19 coarse projections before accepting one at
1220 s. That projection changes accounted energy by −0.0053067 J against an
initial 289,065,932.27 J. The corresponding momentum residuals are
2.38e−7 kg m/s and 7.48e−4 kg m²/s. These are floating-point observations,
not a claim of exact arithmetic or validation beyond this experiment.

The maximum sampled displacement RMS between adaptive and fully fine runs is
23.65 micrometres, integrated with the fine consistent mass matrix after
prolonging the coarse displacement field. The adaptive patch ends with 16
elements; the fully fine patch retains 64. Handoff diagnostics are evaluated
at the transition itself. Other extrema refer to retained trajectory samples,
every 10 s before 900 s and every 20 s afterward, plus handoff samples.

| Policy | dt / s | Maximum sampled energy error | Peak damage | Final heat / MJ |
|---|---:|---:|---:|---:|
| coarse | 0.5 | 0.07201% | 0.01196 | 165.5865 |
| fine | 0.5 | 0.18960% | 0.30104 | 154.0278 |
| adaptive | 0.5 | 0.18960% | 0.30104 | 154.0278 |
| coarse | 0.25 | 0.03174% | 0.01217 | 165.8577 |
| fine | 0.25 | 0.08471% | 0.30181 | 153.8491 |
| adaptive | 0.25 | 0.08471% | 0.30181 | 153.8491 |

The coarse run's smaller energy residual does not imply a resolved local
damage field: peak damage is about 1.2%, compared with 30.2% on the fine mesh.
Halving dt reduces the fine/adaptive energy residual, but two timesteps and
two spatial resolutions do not establish an asymptotic convergence order.
At dt=0.5 s the adaptive patch refines at 55 s and coarsens at 1290 s, after
26 rejected projections; its coarsening energy change is −0.0030721 J.

## Model and transfer contracts

The patch is 256 × 128 m, with a 64 m out-of-plane extrusion and plane-strain
kinematics. Fifteen nodes form sixteen linear triangles. Four-way triangle
subdivision yields 45 nodes and 64 elements. Density is 3000 kg/m³, Young's
modulus 200 kPa, Poisson ratio 0.25. These deliberately soft illustrative
parameters are not a rock calibration. The cylindrical impactor has radius
24 m, thickness 64 m, density 7800 kg/m³ and initial speed 0.8 m/s.

Compressible neo-Hookean energy is the plane-strain specialization of
`W = mu/2 (F:F − 2) − mu ln(J) + lambda/2 ln(J)^2`. Forces are the derivative
of this same stored energy. Each triangle uses the consistent mass matrix
`rho V/12 (ones + I)`. Nested affine geometry and velocity interpolation
therefore preserve integrated inertia and elastic energy on refinement.
The reference stocks are 95% silicate and 5% water in the patch, plus iron in
the impactor. They remain passive finite inventories; no chemistry occurs.

Contact uses sixteen fixed reference-surface quadrature samples spaced 8 m
apart. Their positions interpolate boundary nodes; reciprocal forces derive
from a single normal penalty potential with stiffness E/(8 m). Samples and
stiffness do not change with element size. The impactor is not shattered and
there is no friction. This is a coarse surface-contact approximation with
about 2 m maximum penetration, not a resolved contact benchmark.

Damage is irreversible scalar softening capped at 0.65, driven by maximum
undamaged elastic energy density, beginning at 300 J/m³ and scaled by a
4000 J/m³ reference. Released elastic energy goes into explicit element heat.
Heat is an accounted stock, without temperature feedback, transport or phase
changes. Elements remain connected; this is not fracture or ejecta generation.

An explicitly phenomenological relaxation closure projects velocity into rigid
translation, rigid rotation and a consistent-mass-orthogonal residual. The
residual decays with a 60 s timescale; its lost kinetic energy becomes heat.
This preserves linear/angular momentum. It is a global patch relaxation,
not a resolved local viscosity model. Time integration uses velocity Verlet
with half relaxation steps and damage energy release. Accounted energy is
kinetic + elastic + contact + released heat. There is no self-gravity, external
work, hidden heat correction or energy renormalization.

Adaptive refinement uses a 28 s contact lookahead. Coarsening needs a prior
contact, at least 60 s without contact, nonrigid RMS velocity below 1e−4 m/s
and 30 s of quiet hysteresis. It attempts a consistent-mass projection every
10 s. Admission requires |dE| < 1e−9 of initial energy, |dP| < 1e−4 kg m/s
and |dLz| < 1e−3 kg m²/s. Fine history is retained. The pilot exposed an
incorrect pre-impact coarsening cycle; the contact-seen guard fixes it and a
regression test covers it. The original pilot evidence is retained.

## Integration, source and reproduction

`c/continuum-region` lives on an ECS entity. Its internal material fields and
impactor degrees of freedom advance through the ordinary `genesis/tick-world`
pipeline, under the existing integrator's declared single writer. Other
entities retain the existing integration phases. This isolated region has no
coupling to surrounding planetary matter. The existing `infra.render`
material-surface pass renders an extrusion of the exact saved displacement,
pressure, damage and active triangle mesh. No images are generated by an AI.

All six trajectories ran to 900 s, then resumed their serialized region
checkpoints through that same ECS tick to 1800 s. Initialization asserts exact
checkpoint equality. The archive retains raw JSON observations, ECS EDN
snapshots, transfer records, final checkpoints, plots, native timelapse,
source overlays and a launcher. `summary.json` is derived from those records.

Scratch maintenance had removed the earlier working source. The preceding
experiment's published archive supplied its code overlay and runtime. Its old
local head (89964…) was unavailable from the remote. That overlay applied
cleanly to the recovered upstream commit
`58b0fd91ea65b7a4b3a1f571f22f3d394374a871`; the new experiment was implemented,
run and tested on this recovered base. The new archive names this actual
base and includes all required new source, rather than claiming the old head
was recovered. Previous hosted evidence and archives remain unchanged.

Focused verification passes: 43 tests, 157 assertions, zero failures/errors.
It covers affine refinement, force/energy gradients, reciprocal contact,
contact remapping, heat/momentum accounting, admitted/rejected coarsening,
pre-impact hysteresis, rigid elastic objectivity, ECS ownership, architecture
and existing render/window contracts. The logged `boom` stack trace is the
intentional window-error fixture, not a test error. The packaged launcher is
also checked independently. `bin/analyze --strict` could not run because
clj-kondo, Clojure CLI and clojure-lsp are missing from PATH; `bin/bench :ecs`
also could not resolve its Clojure CLI. Neither gate is represented as passing.
The blocking environment card is retained in the bundle. This remains an
experiment overlay; it has not been merged into Truth main.

## What this establishes, and the next physical boundary

This demonstrates one compatible transfer mechanism for a whole local patch.
It does not repair the earlier granular sphere-subdivision law, whose 4.30 GJ
discrepancy remains archived. It does not establish spatially local neighboring
AMR, fully 3D deformation, shocks, topological fracture, crater morphology,
planetary gravity coupling, atmospheric exchange or Primordia biology.

The next useful test is a localized fine region with a moving coarse/fine
interface, followed by a second disturbance after coarsening. It should
exercise transfer of retained irreversible history and measure reflected
stress waves before increasing model scope.

## Formulation references

- FEniCS/DOLFINx hyperelasticity example: <https://docs.fenicsproject.org/dolfinx/main/cpp/demos/demo_hyperelasticity.html>
- MFEM nonlinear elasticity example: <https://docs.mfem.org/4.8/ex10_8cpp_source.html>

These primary references motivate elastic energy and consistent inertia; they
do not validate this parameter choice, relaxation closure or reported outcomes.
