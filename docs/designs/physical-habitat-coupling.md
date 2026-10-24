# Physical habitats and conservative biological feedback

Status: proposed design, 2026-10-24. Grounding: [research, current-model probes and countermodels](../research/planetary-habitats/README.md). User outcome: run planetary scenarios in Truth, use their physical conditions as life-simulation inputs, and let supported biological activity alter the same voxel world. Fork creation is deferred.

The first slice uses a finite local rock–water–air habitat on the existing ECS, field-seed and voxel-band path. Its global stellar/interior boundary is declared and accounted for. It is a controlled initialized scenario, not proof of natural planet formation. No separate authoritative world, second renderer or uncalibrated life-to-geochemistry conversion is introduced.

## Selected boundaries for review

- Extend the **existing water owner and PR47 design** through review. Reuse elemental kg with explicit species/mineral/phase allocations and donor depletion. Review a liquid-water/enthalpy extension; PR47's current ice/vapor capacity is not actual water production. Distinguish unsupported stock, known dry and physically evolved wet state.
- Local finite-volume heat/flow/tracer exchanges debit and credit the same elemental stocks. The actual stencil, compressibility assumptions and stepping method must be selected against benchmark envelopes before coding. Global atmospheric circulation/clouds and mantle evolution are separate validated slices; static seeded fields do not satisfy their acceptance.
- Existing collision plans supply fitted geometry and energy scales within their validity ranges. Explicitly route excavated/ejected/vaporized material, latent/sensible heat, atmospheric escape and permeability changes. The 64 m voxel substrate and scaling-law classifier do not resolve shock fronts or molecular chemistry.
- Primordia receives versioned habitat observations through the planned asynchronous boundary. A named, calibrated profile maps supported abstract fields to physical quantities. Until then, real metabolic uptake, chemical waste and mineral modification are unsupported. Synthetic flux producers are accounting controls only.
- A serial settlement boundary validates both source and destination revisions and actual modeled interval. One writer per physical component applies balanced paired transfers once. Failed/retried/conflicting transfers cannot create water, carbon or heat. PR38 carbon units are not silently promoted to an all-element chemistry representation.

## Proposed data shapes (not implemented Malli registrations)

`habitat-observation/v0` requires identity, voxel revision, accepted [start,end] seconds, model/unit versions, T-K, pressure-Pa, volume-m3, radiation/heat-W-m2, flow-m-s, finite element-kg stocks, constrained species allocations, phase masses, energy reference and unknown/validity fields. No temperature-only habitability bool.

`habitat-exchange/v0` requires request/cause UUIDs, matching interval, profile version, source/destination identities and revisions, element-kg debits/credits, charge bookkeeping when ionic chemistry is present, heat-J, species/mineral changes and a reasoned terminal outcome. No arbitrary callable supplied as a profile.

Inputs and outputs need named reusable validators before domain behavior. Public functions remain pure; runtime effects stay in infra; source components use the established registry and influence/accumulate paths where applicable. Local fast substeps must cover the exact accepted planetary interval with explicit unresolved remainder or rejection, never skip physical time.

## Acceptance before any two-way claim

Zero-H silicate and dry-near-vacuum controls must not receive physically established liquid water. Closed-box phase changes conserve H/O and energy including latent heat. Flow preserves constant states and mass, with grid/timestep convergence. Gas–water–mineral fixtures compare against a pinned applicable chemistry reference. Impact transfers close their material/energy destinations. Inert biology yields zero biological exchange; funded uptake depletes actual donors, and exact replay does not debit twice. Missing calibration returns unsupported. Held-out environmental perturbations test any future coarse-graining choice.

The next deliverable is the reviewed scenario/contract boundary and meaningful red tests, followed by the finite water/transport slice. No acceptance, Rheos transition, native run or implementation admission follows from this file.
