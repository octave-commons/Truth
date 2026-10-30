# Controlled continuum impact

Open the interactive comparison at
<https://truth-planetary-lab.enso-err.chatgpt.site/continuum-impact/>.

![Native coarse, fine and adaptive damage comparison](continuum-impact.gif)

Three matched mesh policies at dt=0.5 and 0.25 s, continued to 1800 s. The
adaptive run refines before impact and returns to 16 elements only after its
coarse projection passes explicit accounted energy and momentum budgets.

Read [the research record](../../research/conservative-continuum-impact.md)
for numerical results, methods and limits. `summary.json` contains sampled
traces. `run-extended/` contains the measured trajectories and checkpoints;
the earlier `run/` and `pilot/` retain their original evidence.

This is an illustrative local plane-strain softening model. It has no gravity,
shock EOS, topological fracture or physical rock calibration. The native view
extrudes exact finite-element states; smoother display does not add physics.
