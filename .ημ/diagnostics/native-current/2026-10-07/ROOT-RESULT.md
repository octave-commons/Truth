# Fresh qualified native game checkpoint

The dedicated PM2 process `truth-native-7896` launches a new natural default
nebula at source `9c5c889d8923c76bf9a9530a8bd18cd72f37a93f` in this checkout.
It uses DISPLAY `:0`, loopback nREPL 7896, explicit 256 MiB initial / 2 GiB maximum
heap and autorestart disabled. No saved process resurrection or unrelated
service change occurred. This is not recovery of either world lost at the
00:21:39 UTC host reboot; no complete reloadable world snapshot exists.

Fresh PID 131581, start ticks 125503, world atom 919497450 and native window
137768861339584 were verified. Two initial world reads advanced 1791 to 1913.
The one actual full-scene frame records Intel Arc MTL rendering, a 2536 by 1490
framebuffer, three zero GL error samples and exact read-buffer/scene restoration.
The visible HUD is tick 7975 with 24 naturally formed planets; the separate later
world read is tick 7978. Explicit restoration audit at tick 8254 found both
workers alive with no service/UI error. The initial proc-file identity-check
failure and its narrowly reviewed repair remain preserved in the observer bundle.

Root and the independent observer inspected the original PNG. The fit-all scene
is dark, field glyphs dominate small bodies, and the lower status bars overlap
the control hint. The existing Incoming `manual-hud-overlay-spacing` proposal in
PR9 owns that layout work; no new layout policy is implemented here. This single
frame does not establish readable trail fading, manual planetary approach,
binding, commitment, sculpt or Gate activation.

Root paused this exact process with SIGSTOP at 00:53:07 UTC for the separate warp
AFTER cost run, then resumed it with SIGCONT at 00:54:58. A read-only follow-up
confirmed the same world/window at tick 10267, 24 planets, both workers alive and
nil errors. No source hot reload, scene replacement or service restart occurred.
The separate warp repair and its unresolved timing signal are not loaded here.

The observer's 59-file inventory and 58 checksums remain unchanged. This parent
closure adds launch/process/source records, the pause/resume evidence, complete
reaped post-resume client output and immutable byte prefixes of growing runtime
logs. Prefix provenance is explicit: the live logs are excluded from the closed
inventory. `launch-public.json` retains the command, environment allowlist and
source/boot identity while replacing unrelated PM2 process names with a count;
the exact raw record remains local with its hash. Raw PM2 startup output is kept
locally; the rendered PM2 table is not needed to review the owned service identity.
No fresh full suite or static-analysis run is claimed by this evidence-only
checkpoint. The source qualification belongs to the previously recorded
6b160b2/9c5c889 composition. Native verification remains InProgress / 3 points.
