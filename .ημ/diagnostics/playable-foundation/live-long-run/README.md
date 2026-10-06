# Native formation and selection capture

Source loaded: `a3609f6931951f8e011950ec5eab0a24265e93cd`.
Command: `TRUTH_DEMO_PORT=7893 bin/formation-capture .ημ/diagnostics/playable-foundation/live-long-run 900`.
Initial conditions: ordinary default ECS nebula, 1000 gas parcels, default seed42.
Runtime: actual JVM/GLFW/OpenGL renderer on a private 1280×720 Xvfb display.
No fixture, physics tuning, forced birth, or resource injection was used.

`continuous-formation.mp4` preserves the full 900-second recording at normal
speed. `continuous-formation.gif` preserves its full duration at 8× playback.
The other GIFs and PNGs are fixed-time excerpts produced by the existing
capture script; their names are presentation labels, not independently detected
event timestamps. Use the actual ledger and sampled state for timing claims.

The script changed camera framing at 240 seconds. At 18:36:57Z, the observation
became a real-input selection test: L, Entities row1024, focus narrowing, and T.
The interaction segment is not an untouched control trajectory. See
[`segments.edn`](../natural-formation-observation/segments.edn) and the
[verification note](../../../../docs/notes/2026-10-06-playable-foundation-verification.md).

The 1 Hz status stream, initial/final state, stellar event extraction, runtime
log, and video probe are in `../../formation-20261006T182457Z/`. `runtime.log.gz`
preserves the raw interleaved runtime output byte-for-byte, including trailing
whitespace; `gzip -dc` exposes the original text with SHA256
`14ada32d4c93bf678087410ebc5e28331a1f2ac8d7f05e507316205004cc59ed`.
Empty stderr files from the individual read-only samples are retained locally;
they are not additional evidence of gameplay success. The detailed
planet/candidate/ecology reads and full relevant event evidence are in
`../natural-formation-observation/`. No separate classifier was implemented.

Observed: 24 planets formed at tick4169, first handoff at4171, first prebiotic
transitions at4200. Published final tick6981 retained24 planets,21 bound orbits,
three currently eligible candidates and four prebiotic ecologies. Selection
followed a natural planet, but lagging camera focus did not satisfy binding;
T produced no paid sculpt. This records formation and a blocked inspection
interaction, not a completed manual gameplay loop or Gate.

The service was stopped by the capture harness after encoding; this is not
evidence of player-triggered graceful window exit. Source/asset hashes are in
`SHA256SUMS`.
