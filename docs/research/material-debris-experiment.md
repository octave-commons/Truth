# Material debris experiment

User story: watch rocky, metallic and water-bearing material assemble, collide,
retain scars and shed fragments before assigning body labels.

This is a local comoving debris patch on the existing ECS, integrated by the
existing integrator and rendered as moving finite material cells through the
existing cube pass. Cells have fixed SiO2/Fe/H2O kg stocks. These are Lagrangian
material parcels with spherical contacts, drawn as cubes, not an Eulerian shock
hydrocode. No planet or comet is inserted by a formation seeder.

First model: self gravity; linear normal spring/dashpot contacts; damping work
credited as internal heat; contact exposure retained as damage; aggregate labels
derived from proximity, kinetic/binding energy and composition. Contact stiffness
and damping are experiment parameters, not measured rock properties. No tensile
strength, friction, chemical reaction kinetics, gas pressure dynamics, radiation,
stellar tides or validated planetary formation claim. Crater-like depressions and
ejecta must result from displaced parcels; no crater-shaped deletion is applied.

Water is an explicit molecular carrier of H and O, not oxygen renamed as ice.
The optional enthalpy closure uses constant heat capacities and a constant-latent-
heat Clausius–Clapeyron ice/vapor boundary at a declared sub-triple pressure.
It is an exploratory approximation, not IAPWS. No liquid water is inferred.

Contracts before domain changes: equal/opposite pair forces; nonnegative damping
heat; element and carrier stocks unchanged by transport; integrator applies a
heat impulse once; unsupported inventories/pressure reject; classifications do
not alter dynamics. Record energy residual and timestep sensitivity explicitly.

References: LAMMPS normal granular spring/damping documentation
https://docs.lammps.org/pair_granular.html ; NIST water sublimation reference
https://www.nist.gov/publications/new-equations-sublimation-pressure-and-melting-pressure-h2o-ice-ih .
