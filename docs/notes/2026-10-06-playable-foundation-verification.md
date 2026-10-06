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
No fixture or state injection produced these births. The completed 900-second
capture ended at published tick 6981 with 24 planets, 21 bound two-body orbits,
three currently eligible candidates (1021, 1022, 1023), and four prebiotic
ecologies. Persisted candidate components and the current predicate are recorded
separately: an earlier admission does not prove continued eligibility.

## Native selection and attention boundary

The run remained an ordinary untouched formation observation until
2026-10-06T18:36:57Z. Subsequent real native input is labeled separately in
`natural-formation-observation/segments.edn`: L had no living target while all
four ecologies were prebiotic; clicking the Entities row at 18:38:30Z selected
Niphaelar (1024) and entered follow-selection. The player narrowed focus twice
with comma at 18:39:29Z and pressed T approximately 18:39:59–18:40:00Z.

At tick 6800, focus was sustained and the candidate component existed, but the
production overlap predicate was false: the smoothed camera-derived focus was
about 661 AU from the moving planet, versus the existing 1 AU overlap radius.
At the final T observation, Resonance stayed 5, binding stayed empty, and no
commitment, planetary palette, or voxels existed. No sculpting success is
claimed. The capture also exposes overlapping menu/inspector/HUD text that
needs separate UI work.

The existing flight design §5 explicitly identifies non-manual camera modes as
debug/cinematic views. Their camera-target attention policy is an inspected
limitation, not a reason to enlarge the physical overlap gate or to certify
manual fly → resolve → sculpt. A pure diagnostic reproduces the lag and
separately confirms that ordinary manual focus-follow can accrue binding near
a body without changing spark position. Actual manual flight was the next
acceptance test; the subsequent segment below records its results.

## Independent-seed regression

A constructor probe found that requested seeds were ignored. Red checkpoint
`08161ab` contains two tests / seven assertions with two expected failures:
seeds 41 and 43 produced identical positions and velocities. Green `81207e7`
forwards the requested seed to the existing generator and retains default 42.
The same probe then distinguishes both seeds. Independent review found no
blocking defect; its canonical review transition passed 901 tests / 15,642
assertions and all six strict analysis gates. Both independent seeded runs have
now completed 12,000 ticks normally. Their 48 exact birth observations were all
within 25 AU of a bound host; 19 and 20 planets respectively remain bound at
every recorded post-birth sample. The
[two-seed report](2026-10-06-two-seed-natural-formation.md) preserves exact
source identity, sampling limits, life-transition events and raw diagnostics.
This qualifies the tested formation/sampled-survival horizon, pending fresh
canonical review gates; it does not establish continuous binding between samples
or native manual capture.

## Native manual flight and the precision boundary

The subsequent ordinary nebula session loaded `81207e7` and exercised actual
R/W/D/A/S/Space/Tab/mouse controls, without planted bodies or state injection.
Its [closed evidence inventory](../../.ημ/diagnostics/playable-foundation/manual-flight/CLOSED-FILES.txt)
contains three native videos with full-window GIF companions, input timestamps,
screenshots and published-state readbacks. The
[capture report](../../.ημ/diagnostics/playable-foundation/manual-flight/README.md)
records exact timing, settings, service identity and limitations.

A 30-second W burst reduced distance to naturally formed planet 1012 from
264,914.58 AU to 30,464.18 AU. A later correction reached 28,648.92 AU.
Release cleared thrust and slowed the spark. Read-only coordinates assisted
aiming; this does not establish discoverable navigation through the HUD alone.
At the existing minimum displacement setting, the spark reached about 242 m/s
while the target moved at about 1.21 km/s, and separation increased. No sample
reached the 1 AU overlap boundary; intermediate settings were not tested for
capture. No commitment or sculpt success follows from this segment.

The [precision note](2026-10-06-manual-flight-precision-boundary.md) separates
that native observation from an isolated production thrust/integrator probe.
Under fixed dt and default retention, the existing minimum setting still gives
6.68459 AU total travel for one accepted input tick and 216.13498 AU coast after
steady thrust. It derives a possible finer range without inventing automatic
target capture or changing the one-writer physics boundary.

At tick 8976 the retained native world had no currently eligible candidate.
Some stored candidate records persisted, and the existing commitment predicate
uses those historical records; that is not fresh physical admission evidence.
The session remains available with all controls released. Graceful Escape,
regional voxel resolution and paid sculpting remain unverified.

## Hosted review runtime qualification

The separately stacked [review-runtime PR #10](https://github.com/octave-commons/Truth/pull/10)
passed the complete hosted workflow on head
`2722e131c498a3d8777a3a2441eca5e3fc0fe5af` in
[run 37517152897](https://github.com/octave-commons/Truth/actions/runs/37517152897).
The upstream runtime bound the exact head, delivered the complete 64,861-byte
diff in ten pages, and published
[MiMo approval 5433523578](https://github.com/octave-commons/Truth/pull/10#pullrequestreview-5433523578)
with zero confirmed findings. Its terminal review gate, unit/integration tests,
coverage and strict analysis passed. This supplies hosted Node 22 evidence for
that adoption; it does not satisfy the remaining configured reviewer cohort.
No merge or paid review was performed.

## Remaining player acceptance

Run actual flight and focus controls; establish a naturally formed stable
planet; commit, resolve its regional voxels, and exercise paid sculpting. Verify
the corresponding visual changes and graceful exit. The life, civilization,
embodiment, and Gate stages retain their own research and design prerequisites.
