# Natural-flight profile attempt01 — closed observation, no flight attempt

The fresh ordinary nebula produced12 natural planets in the saved observations.
The operator established manual mode and Fine range and captured three original
full-window PNGs, but issued **zero aiming, look or movement-hold commands**.
Every sampled current production handoff was empty. This run therefore provides
formation and guarded observation evidence, **not a failed flight/capture test**
and not successful binding, commitment, embodiment or Gate progression.

Closure was clean, but the operator protocol was not a clean pass: six polling
gaps exceeded the intended60s maximum, and the STOP marker was written33.739s
after the T+960 no-target cutoff. These deviations can hide short-lived eligible
targets. The unchanged1470s work /1500s total lease was not extended or exceeded.

The independent [audit](attempt-01-audit.json) contains executable data-only
verification source, per-span hashes, source/preparation hashes, all request
outcomes and the full sampled timeline. All120 pre-existing bundle files were
hashed before the audit and verified unchanged afterward. The audit did not
import a diagnostic module, load a project namespace, start a JVM, inspect a
live process, send input, or alter the board, ledgers or Git state.

## Provenance and guarded execution

Execution preparation was `cf9dcf2603244a70c43ad03315b2552bd660df4f`, rooted at
`64f8141b40cfb8db94ea2f005004f068a3f47a72`. All186 saved source hashes match the
current files and frozen copy proof for production
`b395c4049718f7ce015ddf25fc0a192d97821373`. All nine preparation checksums and five
run-local executable/PLAN pins match. The [root release](root-release.json) is
bound to the unchanged preparation manifest
`7d9769a055da65ecc4365b80f72ee0aaa81f62d541190af2b8b94908127a0eaa`.

The owned display was`:1`, port7898, world atom identity305915124,
GLFW window126880850854736 and X11 window2097159. Boot identity was
`b04f7fb6-ea65-4b47-8866-73e24a6887f6`. Principal PID/start pairs were
supervisor2687712/2809449, Xvfb2687739/2809458, game2687764/2809479, and
snapshot worker2689310/2810673. Initial `/usr/bin/env` child records precede
exec; their PID/start/boot/cwd match the later guarded Java identities.
The game used a2GiB maximum heap and the fixed snapshot worker256MiB.

One32-read worker connection served449 decoded nREPL response maps. The exact
encoded EDN journal is287,189 UTF-8 bytes. All32 request byte spans are contiguous,
all IDs0001–0032 match their summaries and responses, each has one result value
and terminal `done`, and all96 request/ack/consume events occur in order before
the next request. The66-entry request journal opens once and closes after32
completed requests. The final IPC slot agrees with read0032. These are exact
encoded decoded-map bytes, not original bencode wire packets; per-eval session
UUIDs are not additional TCP connections. No cap or failure-path injection was
performed. Snapshot elapsed times were36.117–289.204ms, median115.890ms; these
are descriptive live observations, not an isolated performance comparison.

All21 supervisor requests have matching successful saved results, completed
before the next submission:13 inspections, three menu clicks, two key taps and
three frames. R selected manual mode; Spark open → Fine → Spark close selected
`D=10,000,000m/tick`; Tab locked look. The three mouse presses have exactly
three matching releases. Thirteen input readbacks include asynchronous unmatched
observations followed by the intended postconditions; these are observation
polls, not repeated gestures. All32 snapshots retain nil thrust, empty observer binding,
nil observer commitment and unchanged yaw−90°/pitch−20°. All60 recorded planet
rows across the five12-planet snapshots also have nil commitment. Production
commitment is written to the planet, so observer nil alone cannot establish
its absence. No commitment was observed in these sampled fields; this is not
a claim that no commitment existed elsewhere, between samples or after the
last snapshot.

## Sampled formation and eligibility

All times below are UTC on2026-10-07. The first snapshot was tick46 at08:10:14.779;
the first sampled star was tick859 at08:11:42.994.

| Read | UTC | Tick | Planets | Current full-handoff members | Historical candidate1010 |
| --- | --- | ---: | ---: | ---: | --- |
| 0027 | 08:19:57.916 | 3935 | 0 | 0 | Absent |
| 0028 | 08:21:51.044 | 4429 | 12 | 0 | Stored; ready=true |
| 0029 | 08:22:43.318 | 4656 | 12 | 0 | Stored; ready=true |
| 0030 | 08:23:23.864 | 4834 | 12 | 0 | Stored; ready=true |
| 0031 | 08:24:22.413 | 5089 | 12 | 0 | Stored; ready=true |
| 0032 | 08:25:17.820 | 5346 | 12 | 0 | Stored; ready=true |

The first planet-bearing sample follows a113.128s observation gap; it does not
identify the birth tick. Each of the five planet samples records all12 bodies
with zero omissions. At the final sample, simulated time was
59,539,183,196,938.47s and dt4,138,029,443.01184s. These are simulation seconds,
not wall-clock duration.

Candidate1010's stored record persisted, and the actual game
`ready-to-commit?` predicate returned true in every planet-bearing sample.
The snapshot's current dominant-attractor lookup returned nil for1010, so its
conditional per-body eligibility observation is nil, not a computed false.
Its recorded range from Spark grew from176,535.118AU to199,291.806AU without
manual thrust. This is a paired published-world distance observation, not a
measurement of a flight attempt or a diagnosis of orbital failure.

The diagnostic target gate required membership in a freshly evaluated full
production handoff write-set, including its current stellar/system conditions.
That is **stricter than the game's historical stored-candidate readiness law**.
The empty sampled handoff did not establish that the game had no ready target,
or that no fresh member existed between samples. No target was admitted in this
run. Camera/config are sampled separately from the world, and published focus
is not a recording of the binding consumer's frozen input.

## Timing deviations and cleanup

The shared lease origin was08:09:54.396320. Startup reached ready atT+20.468s.
The intended observation cadence was30–60s. From read0021, the final post-input
snapshot, through0032, six gaps exceeded60s:74.150,139.338,70.945,63.930,64.147,
and113.128s. The maximum was139.338s. The last snapshot preceded closure by
71.941s; there is no observation covering that final interval.

The no-target cutoff T+960 was08:25:54.396320. The STOP file's observed filesystem
mtime was08:26:28.135412, orT+993.739092. The first teardown command is journaled
at08:26:28.221767, T+993.825478. These are respectively33.739s and33.825s late.
The STOP mtime is metadata, not an outer-client invocation timestamp; no reliable
exact outer invocation time is claimed. No request or target admission occurred
after T+960. The supervisor closed at08:26:29.761136, T+995.364838,
with `operator-stop`, no cleanup errors, no unreaped children and all pending
input/uncertain-release flags clear.

The journal contains22 unique child spawns and22 matching unique reaps;
21 exited0 and the owned game exited143 during termination cleanup. Nineteen
short helpers also have successful exit records. Root independently reported all
four principal PIDs absent and all outer `functions.exec`/`write_stdin` client
sessions returned0/reaped. Unlike the preceding automated smoke, this run has
no separate local outer-client stdout/journal package; this audit cannot
independently enumerate those clients or prove supervisor self-reaping.

Xvfb stderr preserves listener-collision diagnostics during display-number
selection and nonfatal xkbcomp keysym warnings; successful private-display
readiness and frame captures followed. Other child/supervisor stderr files are
empty. Runtime stdout retains4,893 occurrences of `K clamp binds:` and the
reported4096 substep clamp, including interleaved concurrent warning lines.
Those warnings are observed numerical-work limits; this audit does not attribute
the empty handoff, readiness distinction or approach outcome to them.

## Original captures and remaining boundary

The three unfiltered1280×720 X11 PNGs are unchanged. Root visually read ticks1252,
4912 and5415 and confirmed the ordinary manual third-person view. This audit
verified their headers, original bytes, capture commands and exit results;
it did not independently interpret pixels or demonstrate a planet close-up.

- [Original frame, root-read tick1252](runs/attempt-01/frame-1791360751819999506-b1501124.png): 208,834 bytes; SHA256 `2d8576ffb68d3d21f66cdd569277d363d64616a4453fe0071e2ae7dc8fff4c7d`.
- [Original frame, root-read tick4912](runs/attempt-01/frame-1791361422635587731-a1f0f83b.png): 253,035 bytes; SHA256 `eeb32b9a1d1c39fcc73d2a3170fd7a6b626401fb3f16907a93c073dd26c2d8a8`.
- [Original frame, root-read tick5415](runs/attempt-01/frame-1791361531588261573-afd26be0.png): 246,701 bytes; SHA256 `4a65aeb028dcdcb2d81e7bad1d8e70d35a17fda4a39b779a2ec3283c88db6671`.

The qualified boundary is ordinary formation, guarded observation, manual/Fine
menu setup and clean owned closure. Aiming, physical approach and sustained
binding remain untested in this run. The next diagnostic must make target
admission agree explicitly with the intended game contract and maintain its
finite sampling/cutoff policy; these records do not authorize or promise a new
controller, physics change or guaranteed capture.
