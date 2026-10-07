# Commit-ready approach attempt01 — independent offline audit

**Evidence integrity checks pass; the native input attempt failed before approach.**
The fresh ordinary world ran for **744.115535s**, closing at
`2026-10-07T09:16:15.592976+00:00` with `Input postcondition unconfirmed: tap-Tab; gesture not retried`.
Root intended body1010, but successful supervisor admission stayed null. No aiming,
look gesture, W pulse or pursuit occurred. Fine (`D=1e7`) was the only applied
change from initial Cruise; the proposed `D=3.75e13` was never applied.

## Audited coverage

- **35** sequential fixed snapshots, **529** decoded nREPL messages and **337,000**
  exact UTF-8 bytes. All request byte spans are contiguous, IDs match, each request
  has one value/done outcome, every response was consumed before the next request,
  and all individual/aggregate/request-count caps hold. No remote errors occur.
- **19** submitted operations: **18 successful, one failed**. Six were input
  requests (five acknowledged, final Tab unconfirmed); the rest were reads and
  one screenshot. All raw request/result files and outer client responses agree.
- **22** unique supervisor children have matching reap records. Twenty outer
  clients are recorded reaped:19 exit0, final Tab client exit1. The game exits143
  during owned teardown; the snapshot worker and Xvfb exit0. Cleanup errors,
  unreaped lists, pending/uncertain releases and budget-overrun flags are clear.
  [Root's separate closure record](root-closure-observation.json) records all four
  owned persistent PIDs absent. This auditor did not inspect processes.
- All **186** current production hashes match the run and the pinned base inventory;
  all **six** run preparation pins and **21** frozen manifest rows still verify.
  Production base is `b395c4049718f7ce015ddf25fc0a192d97821373`; root reports preparation commit59ce1c0.
- Exactly one1280×720 private-display PNG was captured early, before planets.
  There is no post-formation screenshot or video in this attempt.

## Final Tab request: observed boundary, no causal conclusion

The pre-input snapshot0032 is tick4538 with manual mode, free cursor false,
locked look, no domain panel and nil thrust. The helper command
`xdotool key --delay 80 Tab` starts at09:16:12.410UTC and exits0 at09:16:12.536;
its owned keyup exits0 at09:16:12.592. Three later readbacks0033–0035, at
**ticks4540,4541,4543**, still report free cursor false, locked look and no pending
pick. The first-to-last readback interval is **1.000463s**;
release-to-last is **1.147235s**.
The three-attempt cap fails before the20-second observation ceiling is used.
There was no retry or subsequent state read.

The preserved runtime stdout contains `UI cursor : locked` at line10 and
`UI cursor : free` at line1505. The production key callback prints that label
following its config swap on a press. **The log has no event timestamp**; its
line order cannot establish callback time relative to the sampled readbacks,
and no later snapshot confirms the resulting host config. The game continued
advancing across those samples. This audit does not infer failed delivery,
renderer freeze, exact callback timing or a cause for the unconfirmed result.

## Natural candidates and observation cadence

Snapshot0029 at tick3937 has no planets. Snapshot0030 at tick4102 is the first
observation of12 planets and three stored/current-ready candidates1010–1012,
separated by **30.549s**. This brackets
observation, not exact birth or candidate admission. All35 samples retain zero
global commitments, nil thrust and empty binding/scar maps.

At tick4319, body1010 is nearest at154,761.241AU;1011 is168,237.225AU and1012
169,488.281AU. Historical candidate/current readiness does not establish fresh
M5 eligibility or present habitability. Target1010 has no current dominant bound
parent in the recorded observations. No body was actually admitted by the driver.

The root's eight-observation automatic poller (poll-00 through poll-07) produces
seven intervals between **30.440s and30.902s**, then stops
on its first stored/current-ready result without selecting a target. Earlier
manual observation gaps exceed the intended30–60s cadence; the maximum is
**96.615s**. Every gap and exact ID/tick pair is retained
in the JSON audit. Sparse observations cannot locate an ejection or prove its cause.

## Unapplied motion screen

Across the actual automatic poll intervals, maximum observed rate is
**6.157555 ticks/wall-second**, giving `N*=ceil(2*2*rate)+2=27` for the proposed2s pulse.
At the tick4319 range, proposed `D=3.75e13` gives an isolated constant-dt coast
screen of8,105.062AU (5.237%) and pulse-plus-coast screen
14,873.206AU (9.610%). The next larger half-Cruise setting
`7.5e13` fails the10% coast screen (10.474%). These settings were
never applied and are experiment heuristics, not physical stopping guarantees.

The same-world paired endpoints4102→4319 span217ticks/43.945wall-seconds.
With no thrust, range decreases163,734.800→154,761.241AU:Δrange=-8,973.559AU,
mean-204.200AU/wall-second. The relative endpoint chord is
[-8999.475,-6239.748,1027.391]AU, magnitude
10,999.116AU. This is measured endpoint motion, not velocity×dt,
interception evidence or FPS.

## Provenance and limits

[attempt-01-audit.json](attempt-01-audit.json) includes exact raw spans, per-request
summaries, input/readback timing, reap/outer-client records, all observation gaps,
source/preparation verification and hashes of every audited raw/client input.
Those bytes were verified unchanged before these two audit files were written.
Only offline BB standard EDN/JSON data parsing and Python byte/math checks were
used; no project namespace, JVM, native, Git, board or network operation ran.

No approach, sustained consumer overlap, binding, commitment, sculpt, embodiment
or Gate was proved. Automatic T960 no-target closure and terminal-commitment
runtime handling were not exercised: this run failed earlier atT744.1155.
A subsequent observer change or retry requires its own scope and reviewed bundle;
this audit does not extend or resume the closed run.
