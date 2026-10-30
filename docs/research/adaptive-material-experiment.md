# Adaptive material experiment — 2026-10-24

Accepted direction: one physical ECS world, with local reversible changes in
physical model. This is the next bounded experiment toward nebula, aggregates,
geology, water/atmosphere, habitat regions and sampled Primordia ensembles.
No nebula-to-life connection is claimed by this run. (汝, p=0.99)

## Observations

A fresh 81-cell, finite-stock cloud assembled under the existing gravity and
normal contact influences. At 2400 s the experiment introduced three iron
cells (40 m edges) at 0.12 m/s, 260 m from the target COM. At 3040 s the quiet
classifier requested a coarse representation: 84 active cells became one
translating ECS entity. The cached packet retained each source cell's geometry,
relative velocity, thermal state, damage and material stocks. (己, p=0.99)

After 30 s in that regime a second three-cell projectile was introduced at
2.5 m/s, 650 m from the COM. The lookahead classifier requested detailed
reactivation at 3070.25 s, before contact. All 87 material cells then evolved
through the ordinary integrator. The run ended at 3470.25 s. Both handoffs
reported exactly zero sampled mass and expanded-state energy residual.
All sampled H2O stocks remained unchanged. (己, p=0.99)

A 30 s detailed shadow from the same coarse-entry microstate differed by
0.974439 m RMS and 4.834662 m maximum position difference. The verification
run confirmed that its coarse branch remained one packet for the entire
comparison. Timing is a single warmed/unwarmed process observation and is
not a general performance benchmark. (己, p=0.99)

The 0.8 m/s first-impact variant was intentionally stopped near 4800 s with
zero coarse handoffs: nearby independent debris kept the encounter guard
active. The model did not force a coarse transition to complete the demo.
The preliminary run also exposed a packing-temperature lookup bug, which
was fixed before the successful run. Historical attempts and logs remain.
(己, p=0.99)

## Rendering and numerical limits

The scalar display field is a compact polynomial-kernel reconstruction from
actual recorded cells. Marching tetrahedra at 12 m display spacing extracts
triangles; the existing native Truth renderer draws them with field-gradient
normals and interpolated composition. Raw cubes and active/cached overlays
show the same observations. A finer display grid adds no physical resolution.
Target cell edges remain 64 m and projectile cell edges 40 m. (己, p=0.99)

The packet is a reversible cache, not a lossy coarse graining: memory has not
been reduced by discarding cell history. The coarse body translates under the
existing integrator. Cached morphology does not rotate or undergo internal
relaxation. Finite interval, displacement-estimate and approaching-neighbor
guards bound its use; the displacement estimate is not a proven error bound.
The paired detailed run is the evidence for this interval only. (己, p=0.99)

The contact-dissipation damage proxy saturates early. Its persistence is not
validated fracture, nor a realistic crater-strength law. The energy trace
continues to expose about 7.1% assembly drift against the initial mechanical
energy scale, and about 2.6% in the final impact segment. Handoff accounting
cannot repair this integrator error. No shock EOS, tensile/frictional response,
new chemistry, fluid solver, tectonics or two-way Primordia exchange was added.
(己, p=0.99)

## Verification

Contracts and failing behavior checks preceded domain implementation. Final
focused checks: 31 tests, 121 assertions, zero failures/errors, including the
single ECS, one renderer and registry single-writer guards. Native shader
compilation and 45 exact-state captures in each of three views completed.
A portable checkpoint was read back and checked. The complete strict analysis
gate could not run: clj-kondo, the clojure CLI and clojure-lsp were absent from
PATH. No PR, merge or production readiness is claimed. (己, p=0.99)

## Next discriminating experiment

Repeat the adaptive-versus-detailed shadow over longer intervals and at finer
physical resolution, carrying angular momentum into a rotating coarse model.
Use prediction disagreement to determine how long that model remains adequate.
This keeps the planetary lifecycle and eventual regional Primordia sampling
as the destination while testing one model transition at a time. (己, p=0.95)
