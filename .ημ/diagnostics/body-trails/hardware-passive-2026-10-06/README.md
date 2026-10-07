# Passive hardware trail observation — temporal path verified, readability open

This was an authorized passive verification of existing InProgress5
`body-trails-ringbuffer` on the already running natural hardware game. No camera,
selection, field/HUD visibility, desktop input/unlock, fixture, world/body/time
injection or extra context was used. The existing source/deps/demo match launch
revision c79be1e; the evidence-only checkout HEAD at installation was 4805fae.
The installer received independent source-only review with no blocking finding.

## Observed result

The observer captured three complete 1264×1490 native frames and restored itself.
PID4070721, process-start3869117, world atom275227937 and window129843170276688
matched throughout. Actual context was Intel Arc MTL / OpenGL4.6 Core / Mesa25.2.8.
All9 bounded raw GL samples were[0], with no probe errors. GL error reads consume
flags, as disclosed in PLAN.md. Original renderer Var and bodies-fn key presence
and callable were restored; unrelated configuration was not replaced.

| Capture | Exact projection tick | Later published read tick | Simulated time (seconds) | Requested/rendered segments | Dropped |
|---|---:|---:|---:|---:|---:|
| [01](captures/pid-4070721-frame-01.png) |24989|24991|1.1924132019802139e14|1701 / 1701|0|
| [02](captures/pid-4070721-frame-02.png) |25092|25095|1.1955708695785584e14|1700 / 1700|0|
| [03](captures/pid-4070721-frame-03.png) |25195|25197|1.198728537176903e14|1688 / 1688|0|

The image HUD ticks match the exact projection ticks. Later world reads are
separately labeled, never substituted for projection inputs. Captures span
6.315335196689062e11 simulated seconds, slightly more than the6.3e11 history
horizon, and17.678 wall seconds. This duration is not a performance/FPS result.
`dt` was3.065696697424829e9 seconds in all three exact source snapshots; this
observation does not independently test a time-rate change.

The unchanged production projection tags real endpoints with `:trail/entity`.
Every captured scene's tagged endpoints exactly equaled production project-trails
on its retained immutable input world. The fixed natural targets were star258
and planet1001; magnetic loops/vectors were neither filtered nor recolored.

For one fixed sample at simulated time1.1923212310792912e14, the ACTUAL emitted
vertex alpha changed as follows. The derived lookup matches that sample's current
recentered position to actual recorded trail endpoints; it does not recreate the
opacity law or render substitute geometry.

| Target | Frame01 alpha | Frame02 alpha | Frame03 |
|---|---:|---:|---|
| Star258 |0.8375609756097561|0.4061576354679803|Sample expired from retained history|
| Planet1001 |0.8375609756097561|0.4083333333333333|Sample expired from retained history|

Each frame's complete target geometry still had alpha0..0.85 for its current
oldest point/head. This establishes actual production fade/age-out data delivered
to the real rendered scene, with no dropped trail budget.

## Actual visual conclusion

All three original PNGs were inspected by board_scout and independently by root.
**Readable star/planet fading is NOT established.** Fit-all remains very wide;
large cyan magnetic glyphs and the permanent HUD dominate the useful marks.
The selected star trail's first-frame endpoint bounds are only about2.52×0.39
pixels near(1217,541); the planet trail is about0.93×2.03 pixels near(981,1260).
These are derived endpoint bounds, not isolated pixel masks or a clipping proof.
The images cannot distinguish their tiny fade gradients reliably from surrounding
field/body pixels. Do not call the magnetic loops motion trails or infer visual
acceptance merely from the0..0.85 values. No image was edited or generated.

The temporal observation completed; the user-legible fading requirement remains
open. This is three still frames, not the accepted design's full native video/GIF
proof. No manual movement, overlap, commitment, binding, activation or Gate claim
is made. The source snapshots and visible HUD reported arc/life-emergence; that is
an observed current enum, not proof of every gameplay prerequisite.

## Restoration and later health audit

The install-boundary frame without a recorded body projection was safely skipped
and counted:1unpaired frame. Final counters were144body calls/145scene calls;
these are not FPS. Installation returned at23:56:19 UTC. Automatic capture closure
followed the third image. The explicit restoration/read-only audit finished at
23:57:23 UTC, exit0 with empty stderr: exact originals restored, both workers
alive, nil service/UI errors, same world/window, tick25631, modefit-all,
volumetrictrue,24planets/2stars/5protostars/1brown dwarf/701nebula. This is health
at that time, not a guarantee of later liveness. No service restart occurred.

An exception from the original bodies-fn would still enter the existing host
frame guard; operator restore would then be needed if no normal frame reached
the deadline. That reviewed limitation was not encountered.

## Reproduction and closed inventory

`bb summarize.bb .` in this directory reproduces derived-summary.edn from raw
observer-state.edn. Tagged Camera data is retained in raw records; the reader
converts it only in the disposable derivation. Original projection histories and
observations were never rewritten. Client stdout/stderr are complete closed
outputs archived byte-for-byte with deterministic gzip; decoded hashes are in
decoded-hash-provenance.json. Raw originals remain untracked.

CLOSED-FILES.txt is the explicit staging inventory; SHA256SUMS hashes every listed
path except itself. Run `sha256sum -c SHA256SUMS` here. No other diagnostic bundle
or production source was modified. Root owns checkpoint/publication and canonical
card comments; no board transition occurred in this task.
