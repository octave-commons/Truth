# Native playable foundation verification

Date: 2026-10-06. Starting revision: `0112e22`, the existing native demo tour.
Verification card: `f369c598-279c-498d-a64f-45d2ce16ad34`.

## Observed live interaction

The ordinary `clojure -M:demo serve` route booted a fresh live nebula on a
private Xvfb display, with nREPL bound to loopback port 7892. No fixture was
selected and no agency, formation state, or intervention was injected through
the REPL. Runtime reads used `demo/formation-status` and the published ECS
snapshot. The display input was a real `xdotool key shift+g` event.

Before the action the observer held 52 agency and 4 resonance. A subsequent
snapshot at tick 1417 held 37 agency and a `:warp/repulsor` born at tick 1375,
with radius `4.0e15` metres and lifetime 600 ticks. The authoritative
`c/accel-warp` component column contained 374 entries. Both the service error
and UI error state were nil, and `:demo/fixture?` was false.

This establishes the existing key → paid intent → intervention → physics
influence path. It does not establish the magnitude of displacement relative
to a control run, the quality of visual feedback, planetary sculpting, or Gate
gameplay. The capture was taken immediately after key dispatch and before the
intent was applied, so its 52-agency HUD is a before-action frame, not evidence
of the completed spend.

Local raw evidence is under `.ημ/diagnostics/playable-foundation/`:
`baseline-state.edn` (initial tick 188, before resources were earned),
`repulsor-state.edn`, and `repulsor-keypress.png`. The 52-agency observation was
read at tick 989 in the execution transcript and is preserved in Receipt River.
The live sample still had zero planets. No fly → resolve → sculpt acceptance
is claimed from it. The existing frozen planet/life fixtures remain useful for
renderer inspection only.

## Launch regression recovery

Deletion `0a9343ae40562fbb667c6f5e160b35e6328627ef` removed `infra.main` while
`deps.edn` retained its `:run` alias. The regression test initially failed with
the missing-namespace exception (red commit `c43bee7`). The restored adapter
uses the current `genesis/create-world`, `arc/tick-genesis`, and `infra.render`;
it introduces no alternate world or renderer.

`env -u DISPLAY clojure -M:run console 2` exits successfully after its explicit
tick budget. `xvfb-run -a clojure -M:run demo` produces an actual tick-1 nebula
frame at `/tmp/truth-view.png`. Visual inspection confirmed a nonblank
1280×720 image containing the nebula, observer/focus marker, and HUD. The
captured image SHA-256 is
`30ef469433280d967bfd21ee08786ec6b8a79ccbbe84117f51c8d11c600c199d`.

The focused launch and architecture suite passed 11 tests / 44 assertions.
These are slice-level results; whole-tree gates and independent review govern
promotion. Console budget completion is not a successful formation outcome.

## Remaining player acceptance

Run actual flight and focus controls; establish a naturally formed stable
planet; commit, resolve its regional voxels, and exercise paid sculpting. Verify
the corresponding visual changes and graceful exit. The life, civilization,
embodiment, and Gate stages retain their own research and design prerequisites.
