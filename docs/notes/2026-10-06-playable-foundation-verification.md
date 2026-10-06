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

## Integrated gates and formation repair

Source revision `a3609f6931951f8e011950ec5eab0a24265e93cd` passed four
independent canonical Rheos review transitions. Each ran `clojure -M:test`
(899 tests, 15,635 assertions, zero failures/errors) and `bin/analyze --strict`
(exit 0, no blocking findings). The launch, rotation, dependency-plan, and
canonical board configuration cards entered review; formation and native
gameplay verification remain in progress. The raw logs are the four
`rheos-review-*-a3609f6.log` files in the evidence directory.

The formation regression was reproduced before repair in `ee13c84`: 53 tests,
225 assertions, 19 expected failures. After repair, the focused suites passed
79 tests / 362 assertions. The identical moving-frame probe measured GI birth
radius 999.550877 → 2.999879 AU and binary birth radius 998.939730 → 4.999698 AU.
Core-seed recentering error fell from 10 AU to 4.406e-14 AU. This verifies
parent-relative materialization and preserves the absolute-spawn path; it
does not by itself verify subsequent orbital survival.

A longer native run on that revision crossed disk maturity naturally. At tick
4373 it contained 24 planets, 24 planet-formation events, one phase-0 handoff,
and two ecology transition events. The production candidate predicate accepted
entities 1022, 1023, and 1024. The native window at tick 4616 visibly reported
24 planets and two stars in the planets-formed arc; see `planet-era-native.png`.
No fixture or state injection produced these births. The capture is still being
observed for survival; some outer planets were already unbound. A separate
constructor probe found that requested seeds were ignored, so independent
two-seed acceptance is explicitly withheld pending its regression repair.

## Remaining player acceptance

Run actual flight and focus controls; establish a naturally formed stable
planet; commit, resolve its regional voxels, and exercise paid sculpting. Verify
the corresponding visual changes and graceful exit. The life, civilization,
embodiment, and Gate stages retain their own research and design prerequisites.
