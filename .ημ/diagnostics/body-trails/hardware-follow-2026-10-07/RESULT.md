# Natural star/planet follow observation: completed and restored

The single authorized diagnostic completed all six actual full-scene/HUD
captures in34.860 seconds, then restored automatically at
2026-10-07T01:16:18.066Z. Explicit restoration and the health audit completed
at01:16:20.711655Z with exit0, empty stderr and client360095 reaped. The install
client355798 also exited0 and was reaped. No second installation or runtime
retry occurred. The earlier target-selection client326774 exited0/reaped.

The game remained the fresh natural world launched at source
`9c5c889d8923c76bf9a9530a8bd18cd72f37a93f`: PID131581/start125503,
boot `b04f7fb6-ea65-4b47-8866-73e24a6887f6`, world atom919497450,
window137768861339584 on DISPLAY:0/7896. This is not a recovered older world.
The exact installer/restore/PLAN hashes are recorded in the reviewed command
records. Source/dependencies were verified equivalent to the loaded revision.

## Observed pixels and remaining acceptance

Closer ordinary follow makes a thin diagonal recent-path line discernible for
the selected star and planet, with a fainter tail. Actual tagged production
geometry identifies those paths separately from the surrounding magnetic
loops. In star frame02 the path runs from the head near(640,743) toward
(289,926); in planet frame05 it runs from(636,749) toward(415,330).

The magnetic loops are considerably brighter and dominate the images. The
ordinary inspector panel, telemetry and bottom overlays remain present and
compete with the path. This is a bounded improvement in visibility under
diagnostic framing, not a clean player-readable orbital presentation. One
selected star and one selected planet do not establish every-body acceptance;
neither normal user acquisition of this view nor manual flight was exercised.
No rendering filter, geometry recoloring, extra light, history manipulation or
alternate render pass was introduced.

All six captured framebuffers are1264×1490. The2536×1490 dimensions in target
planning came from the earlier actual frame and were explicitly historical;
the installer used each real scene's dimensions. Full PNGs and metadata are
preserved without cropping or alteration. The actual GL identity is Intel /
Mesa Intel(R) Arc(tm) Graphics(MTL) /4.6 Core, Mesa25.2.8-0ubuntu0.24.04.4.

## Paired geometry and simulation-time aging

Each row names the immutable world consumed by the original production body
projection, paired exactly once with the unchanged scene draw. The later
published world read is separate. HUD text can likewise differ from the
projection tick; for example frame03 displays21381 while its paired trail
input is21380. The geometry equality assertion concerns trails, not a claim
that every part of the HUD came from the same world snapshot.

| Frame | Selected body | Projection-input tick | Later published tick | Requested/rendered segments | Dropped |
| --- | --- | ---: | ---: | ---: | ---: |
| 01 | star258 | 21174 | 21177 | 1700 | 0 |
| 02 | star258 | 21278 | 21280 | 1689 | 0 |
| 03 | star258 | 21380 | 21382 | 1701 | 0 |
| 04 | planet1001 | 21404 | 21405 | 1688 | 0 |
| 05 | planet1001 | 21507 | 21508 | 1701 | 0 |
| 06 | planet1001 | 21611 | 21612 | 1700 | 0 |

Actual tagged trail vertices equal the unchanged production `project-trails`
result for every capture, using that paired world/context. Each selected path
has124 or126 recorded vertices, with alpha0..0.85. The budget counts cover the
whole scene's trail projection; they are not counts of visually readable bodies.

The selected star's three frames span6.315335196689062e11 simulation seconds;
the planet spans6.345992163663281e11s. Both exceed the existing6.3e11s horizon.
Of each first frame's stored sample timestamps,31 remain in its halfway frame
and none remain in its final frame. Every retained sample's actual recorded
vertex alpha decreases. A specific real star sample has alpha
0.8416667→0.4083333→not-retained; a planet sample has
0.8373762→0.4083333→not-retained. `derive.clj` matches recorded sample positions
to recorded rendered vertices using the existing world/render unit conversion;
it does not regenerate a trail or introduce a classifier.

This establishes recorded aging and expiry for these two natural histories at
the observed dt3.065696697424829e9s. The pass did not change time rate, so it does
not itself verify invariance across time-rate changes. The continuously moving
head refreshes the line while old samples disappear; sparse screenshots cannot
be treated as a full temporal animation or frame-rate measurement.

## Restoration and operational boundary

The final state records phase`:complete`, inactive, six attempts/six captures,
no diagnostic errors,350 observed scenes/349 body calls and one unpaired scene
skipped without capture. All18 bounded GL reads returned exactly[0], and every
read buffer was restored. Exact scene Var root and body-callable presence/value
restoration are verified, together with saved camera and four view fields at
the restoration boundary.

The later explicit audit reports tick21645, same world/window, both workers
alive, stopfalse, nil service/UI errors, original production body/tick callables
and fit-all mode. Camera values subsequently resume ordinary fit-all evolution;
their later distance65.300633 is not asserted equal to the saved boundary
distance65.723651. Unrelated config fields were preserved.

Following through the normal view API changes attention. This authorized
diagnostic therefore cannot claim that world evolution was unaffected or
undone. It used no desktop input/unlock, world/body/time injection, lifecycle
restart, source hot reload or extra GL context. GL error queries consume flags;
readback and PNG writing cost host time. No manual approach, overlap, binding,
commitment, Gate use or native-FPS claim follows.

The runtime was released to root immediately after successful restoration;
remaining work here was offline derivation, image inspection and closure.
The existing body-trails card remains the owner, with its broad native visual
acceptance open. Root owns receipts, board comments, commits and publication.

## Closed artifact policy

`CLOSED-FILES.txt` lists only this bundle's immutable paths. `SHA256SUMS` covers
every listed path except itself. Raw client stdout/stderr are preserved as
deterministic gzip with decoded-byte hashes in `RAW-PROVENANCE.json`; originals
remain untracked and are excluded from staging. No growing game log is included.
`PNG-VERIFICATION.json` records each untouched PNG's dimensions, bytes, hash and
all-chunk CRC verification. No source or board-state mutation is part of this
closure.
