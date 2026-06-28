# Goal: Deep Physics Research

Research the computational physics foundations for Gates of Truth.
Focus on numerical methods that power the simulation.

## Priority Topics

1. **SPH methods** — Standard SPH, density-independent SPH, pressure-entropy SPH. What formulation is best for our needs?
2. **Artificial viscosity** — Balsara switch, time-dependent viscosity, Riemann-solver SPH. How do we handle shocks without excessive dissipation?
3. **Gravity solvers** — Barnes-Hut, FMM, direct summation. What opening angle gives acceptable error?
4. **N-body integration** — Leapfrog, Hermite, adaptive timesteps. What integrator preserves energy best?
5. **MHD in SPH** — Euler potentials, constrained transport, divergence cleaning. How do we handle magnetic fields?
6. **Radiative transfer** — Flux-limited diffusion, M1 closure, Monte Carlo. How do we couple radiation to hydrodynamics?
7. **EOS tables** — Tabulated equations of state, interpolation methods, phase boundaries. How do we handle material properties?
8. **Boundary conditions** — Periodic, reflective, outflow, shearing box. What boundaries do we need?
9. **Parallel algorithms** — Domain decomposition, load balancing, communication patterns. How does this scale?
10. **Error analysis** — Convergence studies, conservation monitoring, test problems. How do we validate numerical methods?

## Output

Research notebooks in `docs/research/physics/` with governing equations, Clojure
pseudocode, charts, and promotion paths.
