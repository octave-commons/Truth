# Callback characterization and first short native input check

Production source remained `b395c4049718f7ce015ddf25fc0a192d97821373`.
The probe was prepared at `9f055ce280de134537d83dafaeee83d12ea046c1`
and executed in the `truth-native-input-probe` worktree; this publication copy
preserves every recorded byte and the original process/cwd identity.
No production source or tests changed. This is diagnostic evidence under the
existing native verification owner, not gameplay completion.

## Actual callback characterization

[The bounded JVM probe](callback-run-01/stdout.edn) invoked the production private
GLFW mouse callback and explicitly consumed picks with production menu hit/action
functions. All five predicted cases passed; exit0, empty stderr, reaped after
7.84 seconds. No GLFW initialization, window, GL context or ECS world was created.

A release without a preceding press opened Spark. A balanced click also opened
it. Two releases before one explicit consume coalesced into one pending pick;
a consume between releases opened and then closed the panel. A dragged release
produced no pick. These characterize the actual callback and the staged
consumption seam, not real X11 delivery or the historical cause of PR41's failure.

## Ordinary native attempt01

The new ordinary seed42 nebula used a private Xvfb display:1, loopback7898 and
2GiB game JVM. The retained primary world was not targeted. Real inputs and
read-only postconditions verified:

- R selected Manual.
- A single tracked mouse press/release opened Spark, selected Fine at1e7m/tick,
  selected Cruise at3e14m/tick, and closed Spark.
- Tab locked the cursor while the pointer remained over Spark, without opening
  the drawer or creating another pending pick.
- One +100px horizontal movement changed yaw from−90 to−89degrees at the
  observed0.01degree/pixel sensitivity.

[The full native PNG](runs/attempt-01/frame-1791353832018791162-9b29ef6f.png)
shows the earlier Fine state at tick873, with zero planets. It was visually
inspected. No video was requested and this image does not show later Cruise/look.

The final +100px vertical action was requested at262.423seconds. Its preceding
inspection completed near269.1seconds; the actual movement command exited0.
Only0.816675seconds remained for its postcondition client, which timed out and
produced no output. **The vertical camera result is unverified.** This is a
late-observation budget failure, not evidence of a UI input failure. Negative
X/Y checks and the additional Tab roundtrip were not performed in this attempt.

## Closed outcome and provenance

Supervisor outcome is **failed**, at06:19:32.218222UTC after271.373seconds,
inside its300second absolute limit. Cleanup records no errors, unreaped children,
pending mouse press or uncertain release. Owned supervisor2042265,
Xvfb2042295 and game2042305 were verified absent afterward. This process teardown
does not establish Escape lifecycle correctness.

The independent [attempt audit](native-attempt-01-audit.json) verifies nine
request/result pairs: eight successful, one timeout. Forty-five unique child
PIDs all have reap evidence;46 raw reap records include two records for the
same timed-out inspector2068835. Do not report46children. All42 ordinary command
exit records are zero; the timed-out client and owned game termination are
separately preserved. One fast xdotool process finished before complete /proc
identity sampling; Popen ownership and its reap remained recorded.

All186 production hashes and four preparation hashes match after execution.
No thrust, approach, overlap, commitment, sculpt, embodiment or Gate was attempted.
No new full-suite, strict-analysis or native-performance qualification is claimed.
This partial input result and its limitations remain separate from subsequent
fresh-run completion evidence. The card remains InProgress3; no board transition
is made by publication.

The original nine-file preparation manifest is unchanged. This publication adds
`MENU-CLOSED-FILES.txt` and `MENU-SHA256SUMS`; whole receipt/reflection hashes are
snapshots of this commit, not promises that append-only ledgers never grow.
