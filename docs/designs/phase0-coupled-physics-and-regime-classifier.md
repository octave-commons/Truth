# Gates of Truth — Phase 0 Coupled Physics & Regime Classifier

This document commits the design for the **physics core** of Phase 0: a single coupled
gravity–hydro–MHD–thermal field carried on the shared ECS substrate, plus a
**regime classifier** that decides, per cell, which terms actually need to be
integrated there. It is the engine-facing companion to
[`truth-phase-0-stellar-nebula-design.md`](./truth-phase-0-stellar-nebula-design.md)
(the experience design) and [`phase0-volumetric-renderer.md`](./phase0-volumetric-renderer.md)
(presentation). It grounds the research in [`../notes/2026.06.25.22.11.59.md`](../notes/2026.06.25.22.11.59.md)
into Truth's four-quadrant layout (`domain/`, `shape/`, `law/`, `infra/`).

## Thesis

Phase 0 is **one field, evaluated everywhere; many regimes, integrated selectively.**

The same conserved state — density, velocity, pressure, internal energy, magnetic
field, gravitational potential, radiation energy, composition — describes a parsec
of nebular gas, an AU of protoplanetary disc, a mantle cell, and an atmospheric
voxel. Only the *parameters and dominant timescales* change between them, not the
form of the equations. This is the literal continuation of the project's core
commitment: **a single ECS substrate where phases are content layers, never
parallel simulations** (see memory `single-ecs-substrate`). We do not bolt a
separate "MHD engine" or "geodynamo engine" onto the side; we add field state to
the entities/cells we already tick, and we add the equations as ordered systems in
the same pipeline that today runs `orbital → collapse → fusion → thermal →
classify → collision` (`domain.phase0/physics-systems`).

The expensive part is not the equations — it is integrating *all* of them
*everywhere*. The regime classifier is what makes the full physics affordable: at
each location it computes a handful of dimensionless numbers and emits a tag
(`:gravity-hydro`, `:mhd-dominated`, `:radiation-dominated`, `:convective`) that
upstream systems and the Myth Engine read to decide how much detail to spend.

### Why this matters for play

The motivating bug, from [`../notes/2026.06.25.16.41.16.md`](../notes/2026.06.25.16.41.16.md)
and the earlier sections of the source note: gas collapses straight to the centre,
nebulae never acquire rotation, planets sit frozen, stars never orbit. The current
pipeline has gravity + a scalar thermal model and **no magnetic field, no
turbulent velocity field, and no pressure/tension support that resists radial
infall.** Adding the EM layer and a proper equation of state is what makes collapse
*anisotropic and slow enough* to produce filaments, discs, and orbiting
substructure instead of a single central clump. The physics here is therefore not
realism for its own sake — it is the missing support that buys the nebula time to
whirl, fragment, and settle before it hands Phase 1 a star system.

## Where this sits relative to existing code

What already exists and stays:

- `law.stellar` — physical constants (`G`, `k-B`, `m-H`, `stefan-boltzmann`,
  fusion thresholds) and matter-state contracts. We extend it, not replace it.
- `domain.stellar` — pure thermodynamics (`ideal-gas-pressure`,
  `gravitational-collapse-rate`, `virial-temperature`, `radiative-cooling-delta`,
  `fusion-rate`) plus the ECS systems `collapse-system`, `fusion-system`,
  `thermal-system`, `classify-system`, `stellar-merge-handler`. The Jeans logic in
  `gravitational-collapse-rate` is the first member of the regime classifier; it
  generalises here rather than being thrown away.
- `domain.ecs.components` — the shared component vocabulary. New field state attaches
  as new components on the same entities.
- `domain.phase0/physics-systems` and `tick-world` — the ordered tick pipeline. New
  systems slot into this list in a defined order; nothing forks it.
- `domain.orbital.*`, `domain.gravity.barnes_hut` — N-body gravity for the
  `∇²Φ = 4πGρ` term. (A particle-mesh FFT Poisson solver was removed with the old
  parallel particle world; if a smooth-potential solver is wanted later it must be
  added as an ECS gravity system, never a second world model.)

What is new (detailed in [Namespace Plan](#namespace-plan)):

- `shape.field` — pure differential-geometry operators (grad, div, curl, Laplacian),
  **polymorphic over grid type** (see [Coordinate representation](#coordinate-representation-is-a-focus-decision)),
  with the SI/Gaussian unit convention pinned down once.
- `law.field` — schemas and invariants for the field state and the regime tags.
- `domain.regime` — **the classifier**; the keystone of this document.
- `domain.em` — induction step and Lorentz force (ideal MHD-lite, non-ideal hook).
- `domain.thermo` — generalised energy equation (compression, radiation, nuclear,
  radiogenic heating) lifting today's scalar thermal model.
- `domain.interior` — mantle convection (Rayleigh) and core dynamo, end-of-phase.
- `domain.atmosphere` — hydrostatic structure and stellar-wind/field coupling.

## Core field variables → ECS components

Per resolved cell or voxel, the field state is:

| Symbol | Meaning | ECS component | Status |
|---|---|---|---|
| ρ | mass density | `c/density` | exists |
| **v** | velocity | `c/velocity` | exists |
| p | gas pressure | `c/pressure` | exists |
| e | internal energy / mass | `c/internal-energy` | **new** |
| **B** | magnetic field | `c/b-field` | **new** |
| Φ | gravitational potential | (solver output, see PM field) | exists (transient) |
| E_rad | radiation energy density | `c/radiation` | **new** (coarse) |
| T, X, x_e | temperature, composition, ionisation | `c/temperature`, `c/composition`, `c/ionization` | T/X exist; x_e **new** |

The rule from the existing stellar design holds: **unfocused matter is statistical,
focused matter is resolved.** Field state lives at full resolution only inside the
player's focus volume; regional/global zones carry averaged **B**, mass-to-flux,
and turbulence metrics. Zone promotion/demotion must conserve flux and angular
momentum (see [LOD](#lod-and-field-state)).

## Units: pin the convention before any code

The source note writes MHD in **Gaussian/CGS** (the tell-tale `8π` and `4π`):
β = p/(B²/8π), v_A = B/√(4πρ), Lorentz = (∇×**B**)×**B**/4π.

The codebase is **SI** (`law.stellar/G = 6.674e-11` m³/kg·s²). To avoid silent
factor-of-4π bugs we commit to **SI everywhere**, with a new constant
`law.field/mu-0 = 1.25663706e-6` (vacuum permeability, T·m/A). The SI forms used
throughout this document:

| Quantity | SI form |
|---|---|
| Magnetic pressure | P_B = B² / (2μ₀) |
| Lorentz force density | **f** = (1/μ₀)(∇×**B**)×**B** |
| Alfvén speed | v_A = B / √(μ₀ρ) |
| Plasma beta | β = p / P_B = 2μ₀p / B² |
| Magnetic diffusivity | η = 1 / (μ₀σ) |

This single decision is a `law.field` invariant: every field equation is asserted to
be dimensionally consistent in SI in tests.

## Coordinate representation is a focus decision

There is no single global grid, and we deliberately do not pick one. The coordinate
representation is a property of **the object currently in focus and the physics most
at play there**, over the relevant timescale — the same per-regime logic as the
physics itself, one level down:

- **Round bodies** (stellar/planetary masses, mantle shells, atmospheres) → a
  **geodesic/icosphere shell**, matching the existing `c/atmos-cell` / `c/biome-cell`
  precedent. Sphericity is intrinsic to the object, so the grid should be too.
- **Gas volumes** (nebula, disc, jets) → a **Cartesian voxel lattice** in the
  immediate zone. These have no preferred centre at the scale that matters, so a
  voxel grid is simpler and the choice "doesn't matter much" — exactly the user's
  instinct.
- Future regimes (e.g. a shearing-box disc patch) can introduce their own grid
  without disturbing anything above `shape.field`.

This is safe because the physics is written **coordinate-free**: ∇×**B**, ρ∇Φ,
(∇×**B**)×**B** are vector statements that do not know what grid they live on. Only
the *operators* that evaluate ∇, ∇·, ∇×, ∇² carry coordinate specifics. Therefore:

1. `shape.field` operators are a **protocol** with one correct implementation **per
   grid type** (geodesic shell, voxel lattice, …). Everything above it — the
   classifier, `domain.em`, `domain.thermo`, the renderer — consumes the abstract
   operators and **never sees a coordinate system**.
2. The only thing that must hold globally is **convention consistency**. `law.field`
   owns it as **frame-transform invariants**: when a focused object changes
   representation, or a cell crosses an LOD boundary between grids, the physical
   conserved quantities (magnetic flux, energy budget, angular momentum) are
   preserved across the transform. This is the formal version of "be consistent
   about conventions so the numbers turn out right."

So the binary "geodesic vs voxel" dissolves: it is **both, chosen per focus, behind
one operator protocol, under one consistency contract.** This subsumes the LOD
flux/angular-momentum conservation requirement below — promotion/demotion is just a
frame transform that the same invariants must survive.

## The governing equations

All are present in the substrate; the classifier decides where each is integrated
with full detail versus collapsed to a cheap proxy.

### Gravity (Poisson)

∇²Φ = 4πGρ, with the gas feeling −ρ∇Φ in the momentum equation. Gravity uses the
existing N-body paths — Barnes–Hut (`domain.gravity.barnes_hut`) and the orbital
integrator (`domain.orbital.*`) — over ECS entities. Φ is a transient solver
output, not a stored component. (A standalone particle-mesh FFT solver once existed
in a separate particle world; it was removed when the project converged on the
single ECS substrate. A smooth-potential solver may return only as an ECS system.)

### Ideal MHD-lite (nebula and core scales)

- **Continuity:** ∂ρ/∂t + ∇·(ρ**v**) = 0
- **Momentum:** ρ(∂**v**/∂t + **v**·∇**v**) = −∇p − ρ∇Φ + (1/μ₀)(∇×**B**)×**B**
- **Induction:** ∂**B**/∂t = ∇×(**v**×**B**) + η∇²**B**, with ∇·**B** = 0
- **Energy:** ∂e/∂t + **v**·∇e = −(p/ρ)∇·**v** + heating − cooling

The induction term η∇²**B** starts negligible (ideal) and becomes the **non-ideal
hook**: density/ionisation-dependent resistivity for ambipolar diffusion and Ohmic
dissipation in dense cores. Ideal MHD is known to over-amplify fields and suppress
discs (the "magnetic braking catastrophe"), so `law.field` caps field amplification
per tick at protostellar densities — a contract, not a magic number buried in a loop.

The ∇·**B** = 0 constraint is enforced by `shape.field` (constrained transport or a
divergence-cleaning pass) and asserted by a `law.field` invariant; an uncleaned
field is treated as a bug, not a tolerance.

### Fusion ignition

Unchanged in spirit from `domain.stellar/fusion-rate` and
`law.stellar/fusion-possible?`: when central T ≳ 10⁷ K and pressure/composition
thresholds are met, ε_nuc(ρ,T) enters the energy equation's heating term, balances
radiative loss, halts contraction, and `fusion-system` promotes the body to
`:star`. The energy equation above is the home for ε_nuc, replacing the current
implicit handling.

### Disc & planet formation (Toomre Q + cooling)

Once a protostellar disc exists, the gravity-vs-pressure-vs-rotation balance is the
Toomre parameter Q(R) = c_s κ / (πGΣ):

- Q ≳ 1 — disc locally stable → smooth disc + core accretion.
- Q ≲ 1 — locally unstable → spiral arms, fragmentation into clumps/planets,
  **but only if cooling is fast enough**: Gammie's criterion t_cool ≲ 3Ω⁻¹.

Per disc cell the classifier evaluates Q and t_cool/t_dyn and tags
`:gravitationally-unstable` vs `:stable-disc`. Terrestrial growth, collisions, and
migration then reuse the existing N-body gravity and `collision/collision-detection-system`
+ `stellar-merge-handler` — no new field equations, just gravity plus disc drag/torque.

### Planet interior (end of Phase 0)

The exit state of Phase 0 is a planet with a molten/partially-molten core,
convecting mantle, volcanism/tectonics, and a global field.

- **Mantle convection — Rayleigh number:** Ra = ρgαΔT d³ / (μκ). Below Ra_c (≈10³)
  conduction dominates and the planet is tectonically dead; above it, convection
  vigour (and thus volcanism/plate tectonics) rises with Ra. The mantle energy
  equation ρc_p(∂T/∂t + **v**·∇T) = k∇²T + H_rad − H_loss is the same energy
  equation as the nebula's, with radiogenic heating H_rad and a Stokes-flow velocity.
- **Core dynamo — induction + rotation:** the *same* induction equation
  ∂**B**/∂t = ∇×(**v**×**B**) + η∇²**B**, now at core scales. Strong convective
  power + rotation → sustained dipole ("magnetic umbrella"); weak convection or a
  solidified core → decaying field, leaving only remanent crustal fields
  (Mars/Venus-like). We do not resolve dynamo turbulence; we feed a field consistent
  with the regime, gated by a convective-power-and-rotation test.

### Atmosphere–field coupling

- **Vertical structure:** dp/dz = −ρg with EOS p = ρk_BT/μ and radiative-convective
  balance.
- **Circulation:** Navier–Stokes on a rotating sphere with Coriolis −2**Ω**×**v** and
  an EM-force term **J**×**B** acting on the *ionised* fraction only.
- **Stellar wind / plasma:** the star emits a radial wind + XUV/EUV field (∝ 1/r²).
  Strongly magnetised planets stand the wind off (dayside magnetopause); weakly
  magnetised ones suffer ion pickup, sputtering, and nightside plasma escape. The
  classifier decides per upper-atmosphere voxel whether EM terms dominate, based on
  ionisation fraction x_e and field strength.

## The regime classifier — keystone

`domain.regime` is a pure function from cell field-state to a regime tag plus the raw
dimensionless numbers. It is what lets the full physics be *present* without being
*paid for* everywhere. This is the namespace the source note explicitly asked for.

```clojure
(ns domain.regime
  "Pure dimensionless-number classifier. Given a cell's field state, decide which
   physics dominates locally so upstream systems integrate only what matters.")

;; Each returns a dimensionless scalar (pure, no ECS).
(defn plasma-beta      [{:keys [pressure b-field]}] ...)        ; 2μ₀p / B²
(defn mach             [{:keys [velocity pressure density]}] ...) ; |v| / c_s
(defn alfven-mach      [{:keys [velocity b-field density]}] ...)  ; |v| / v_A
(defn jeans-ratio      [region] ...)   ; L / λ_J  (generalises domain.stellar)
(defn toomre-q         [disc-cell] ...) ; c_s κ / (πGΣ)
(defn cool-dyn-ratio   [region dt] ...) ; t_cool / t_dyn
(defn rayleigh         [mantle-cell] ...) ; ρgαΔT d³ / (μκ)

(defn classify
  "Return {:regime <tag> :numbers {...}} for a cell. Tag is one of
   :gravity-hydro :mhd-dominated :radiation-dominated :convective
   :gravitationally-unstable :stable-disc :tectonically-dead, etc."
  [cell scale dt] ...)
```

Decision sketch (thresholds are `law.field` constants, tunable, asserted in range):

| Tag | Condition |
|---|---|
| `:gravity-hydro` | Jeans-unstable, β ≫ 1, M_A ≫ 1 |
| `:mhd-dominated` | β ≪ 1, M_A ≲ 1 |
| `:radiation-dominated` | high E_rad, t_cool/t_dyn ≪ 1 |
| `:gravitationally-unstable` (disc) | Q ≲ 1 **and** t_cool ≲ 3Ω⁻¹ |
| `:convective` (interior) | Ra ≫ Ra_c |
| `:tectonically-dead` | Ra < Ra_c |

The tag is written to a `c/regime` component each tick by a thin `regime-system`, so
both the physics systems (to skip cheap-vs-detailed branches) and the renderer +
Myth Engine (to colour and narrate) read the same classification. The classifier
*never mutates field state* — it only reads and tags.

Note `domain.stellar/gravitational-collapse-rate` already computes the Jeans length
and collapse time; `jeans-ratio` is a refactor of that existing logic into the
classifier, with `collapse-system` then consuming the tag rather than recomputing.

## Tick order

To stop "all gas falls straight in," forces that *resist* gravity must be applied in
the same step as gravity. The Phase 0 pipeline (`domain.phase0/physics-systems`)
extends in this order; each entry is one ordered ECS system:

1. **Gravity** — Poisson solve / N-body accel (`orbital`, `barnes_hut`, `pm`).
2. **Regime** — `domain.regime/regime-system` tags every cell for this tick.
3. **EM** — `domain.em/tick-em` evolves **B**, emits Lorentz force density.
4. **Hydro** — velocity/density update applying gravity + Lorentz + pressure +
   turbulence driving (extends `collapse-system`).
5. **Thermo** — `domain.thermo` energy equation: compression, radiation, ε_nuc,
   radiogenic (lifts `thermal-system`).
6. **Fusion** — ignition check + promotion (`fusion-system`, unchanged interface).
7. **Sink/accretion** — collision detection + merge (`collision`, `stellar-merge-handler`).
8. **Interior / atmosphere** — `domain.interior`, `domain.atmosphere` (late-phase, gated
   by regime so they cost nothing until a planet exists).
9. **Classify** — planet/non-planet promotion (`classify-system`, unchanged).
10. **Observer** — runs last, as today, after complexity and events are known.

The EM and pressure terms in steps 3–4 partially counteract gravity *every step*,
producing slow filamentary infall rather than one free-fall plunge. The regime tag
from step 2 lets steps 3–8 early-out where their physics is irrelevant.

## Namespace Plan (four-quadrant)

### `shape/`
- `shape.field` — pure grad/div/curl/Laplacian as a **protocol with one
  implementation per grid type** (geodesic shell, voxel lattice); ∇·**B** cleaning;
  frame-transform helpers; SI unit helpers. No physics, no IO — geometry only.

### `law/`
- `law.field` — `mu-0` and other constants; Malli schemas for field state and regime
  tags; invariants: finite bounded **B**, ∇·**B** ≈ 0, SI dimensional consistency,
  per-tick field-amplification cap, energy-budget tolerance (kinetic + thermal +
  magnetic), angular-momentum conservation bands.
- Extend `law.stellar` only where stellar thresholds already live (fusion, rounding).

### `domain/`
- `domain.regime` — the classifier (pure) + thin `regime-system`.
- `domain.em` — `tick-em` (induction + Lorentz), non-ideal resistivity hook.
- `domain.thermo` — generalised energy equation; absorbs `domain.stellar/thermal-system`'s role.
- `domain.interior` — mantle convection (Rayleigh) + core dynamo, end-of-phase.
- `domain.atmosphere` — hydrostatic structure + stellar-wind/field coupling
  (uses existing `c/atmos-cell`).
- `domain.stellar` stays; its Jeans/virial/cooling helpers are reused and partly
  migrated into `domain.regime` / `domain.thermo`.

### `infra/`
- Field-line overlays, magnetised-jet and high-field-hub colouring, aurora — all in
  `infra.render` consuming pure `shape.field` / `domain.regime` output. The renderer
  never computes physics (existing rule, reaffirmed).

## LOD and field state

The three-zone LOD (immediate / regional / global) carries field state at decreasing
fidelity: full **B** + **v** in the immediate zone; averaged **B**, mass-to-flux,
turbulence metrics regionally; a parameterised influence on star-formation
efficiency and cluster rotation globally. Promotion/demotion must interpolate field
values **and** conserve global flux and large-structure angular momentum, so the
field does not jump when a region enters or leaves focus. This is a `law.field`
invariant tied to the snapshot/ledger system, same as every other domain.

## Law contracts & epistemic tests

Following the repository discipline — schema, then failing tests, then
implementation:

1. **Field geometry** (`shape.field`): curl/div/Laplacian correctness on known
   analytic fields; Alfvén-wave propagation at the predicted speed; energy
   conservation in an isolated box.
2. **∇·B and SI consistency** (`law.field`): divergence stays ≈0 across a tick;
   every equation passes a dimensional check in SI.
3. **Classifier** (`domain.regime`): each dimensionless number matches a hand-computed
   value on fixtures; tag boundaries fire at the documented thresholds; a known
   "G148.24-like" magnetised cloud classifies trans-Alfvénic (M_A ≈ 1).
4. **Magnetised collapse**: a low-resolution magnetised core reproduces, qualitatively,
   published SPH results — disc + outflow + bounded field amplification — *not* pure
   radial free-fall. This is the regression test for the original bug.
5. **Conservation**: total angular momentum and the kinetic+thermal+magnetic energy
   budget stay within `law.field` tolerance bands across many ticks.
6. **Ledger/replay**: field state is derivable from the event ledger; identical event
   sequences from identical state reproduce identical field + gas configurations
   (ties EM into the Merkle-DAG ledger like every other domain).
7. **Interior/atmosphere**: Ra crossing Ra_c flips `:tectonically-dead` →
   `:convective`; a strong-dynamo planet stands off a stellar wind that strips a
   weak-dynamo one.

## Implementation slices

Smallest-first, each shippable and testable:

1. **SI + geometry foundation** — `law.field/mu-0`, `shape.field` operators, ∇·**B**
   cleaning, unit tests. No behaviour change yet.
2. **Classifier** — `domain.regime` with β, M_A, Mach, Jeans (migrated), and the
   `regime-system` writing `c/regime`. Renderer can already colour by regime; *no
   new forces yet*. This alone makes the simulation legible/debuggable.
3. **EM-lite** — `c/b-field`, `domain.em/tick-em` (ideal induction + Lorentz), seed a
   weak large-scale field + turbulent velocity at nebula init. Target: collapse
   becomes anisotropic; the original bug's regression test passes.
4. **Generalised thermo** — `domain.thermo` energy equation absorbing compression +
   radiation + ε_nuc; retire the scalar `thermal-system` path.
5. **Disc regime** — Toomre Q + Gammie cooling tags; couple to existing accretion.
6. **Interior** — `domain.interior` Rayleigh + dynamo at the planet-formed transition.
7. **Atmosphere coupling** — `domain.atmosphere` hydrostatic + wind/field, gated by x_e
   and field strength.
8. **Non-ideal hook** — resistivity for ambipolar diffusion / Ohmic dissipation in
   dense cores; revisit the field-amplification cap.

Slices 1–3 are the critical path: they deliver the regime classifier the note asked
for and fix the "collapses too fast / never orbits" behaviour. Everything after
deepens realism toward the molten-core, tectonically-active, magnetised planet that
defines the Phase 0 → Phase 1 handoff.

## Open questions

1. ~~Cell grid for field state.~~ **Resolved** (see
   [Coordinate representation](#coordinate-representation-is-a-focus-decision)):
   not a global choice — geodesic shells for round bodies, voxel lattices for gas
   volumes, behind one `shape.field` operator protocol, under one `law.field`
   frame-transform consistency contract. Remaining sub-question: the exact set of
   grid types worth implementing for slice 1 (likely geodesic + voxel only).
2. ∇·**B** strategy: constrained transport vs divergence cleaning — which is cheap
   enough at our resolutions while satisfying the `law.field` invariant?
3. How weak can the seed field + turbulence be and still produce discs at our LOD,
   without tipping into the magnetic-braking-catastrophe regime?
4. Where does radiation E_rad need to be a real transported field vs a cheap local
   cooling term? (Likely: coarse everywhere, real only near ignited stars.)
5. Ra and dynamo thresholds: which constants are physically anchored vs tuned for
   readable play, and how do we keep them inside the "physics envelope" the tests assert?

## Final framing

The equations are always present. The regime classifier is the act of attention made
computational — it decides where the universe is articulate enough to deserve full
physics and where it can remain a statistical hum. That is the same move the player
makes with focus, one level down: Phase 0's physics core and Phase 0's player are
doing the same thing — choosing, locally, where reality gets resolved.
