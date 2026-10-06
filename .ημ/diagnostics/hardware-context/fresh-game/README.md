# Fresh natural game rendered on the Intel hardware context

Root authorized a NEW unchanged native game after resource release. This is not
continuation or restoration of the evolved Java3083624/Xvfb:2 world: root reported
that service unavailable after resume, with a broken X-connection message and an
unproven X-server-exit cause. Its separate closed loss archive is not modified by
this task. Older paused Java2372912/:1 was not contacted.

## Actual launch and current-runtime scope

The new detached process started2026-10-06T23:19:00.153001Z from Truth
be832d798a984b0c6c79512d2d3497dd7f7df62a, branchcodex/truth-hardware-native,
in the truth-focus-cadence worktree. PID/session/process-group4070721 and
/proc start-ticks3869117 identify it. It has private file logs and DEVNULL stdin,
not an ephemeral foreground tool-session lifetime. The inspected game source
is unchanged fromc79be1e; exact launch command and source hashes are in launch.json.

```sh
env DISPLAY=:0 TRUTH_DEMO_PORT=7896 \
  clojure -J-Xms256m -J-Xmx2g -M:demo serve nebula
```

The loaded defaults are seed42,1000gas particles,mass4e30kg,radius3e16m,
spin0.6,turb0.15,population-I composition. `:demo/fixture? false` and the natural
arc/tick-genesis route were verified. The entrypoint's ordinary initial nebula
selection constructs its own default world and installs it through its own queue;
no retained-world transfer or later-arc fixture was supplied.

The initial read-only audit observed world-atom275227937, GLFWwindow129843170276688,
actual max-heap2147483648bytes, live render/sim workers and nil service/UI errors.
At tick1485 it held962nebula parcels,1star and1protostar. The normal startup
selector had enabled free UI cursor and fit-all mode. Root accepted the unchanged
visible-window focus/cursor behavior; this task sent no desktop input or unlock.

## Three actual frames and restoration

After source-only independent review by intent_research and root, the observer
was installed once on this new process only. It intercepted the actual
infra.render/render-scene facade, called the original once, and read the owned
GL_BACK after the full scene/HUD returned and before the native buffer swap.
It created no extra GL context and invoked no screenshot helper or world tick.

All3attempts completed. The observer restored itself automatically; the later
restore form verified exact Var identity and inactive state. All3 saved read-buffer
values were restored (1029/GL_BACK). All9 raw GL-error samples were[0], untruncated;
there were no probe errors. Error flags are consumptively read and cannot be
restored; this intervention is disclosed in the preserved preparation plan.

Each frame independently recorded the actual context: **Intel / Mesa Intel(R)
Arc(tm) Graphics(MTL),OpenGL4.6Core,Mesa25.2.8** on:0. All3 PNGs were opened for
visual inspection and show the actual purple gas, cyan field loops, green ring,
menu and HUD rather than an error screen. No image was edited or generated.

| Frame | Visible HUD tick | Published-world read after scene | Actual PNG size |
|---|---:|---:|---|
| [01](captures/pid-4070721-window-129843170276688-frame-01.png) |2835|2839|1264×1490|
| [02](captures/pid-4070721-window-129843170276688-frame-02.png) |2959|2962|1264×1490|
| [03](captures/pid-4070721-window-129843170276688-frame-03.png) |3066|3068|1264×1490|

The after-scene world read is explicitly separate from rendered input. The images
show accretion with0planets; do not describe them as planet screenshots. Initial
requested config1280×720 differed from the actual framebuffer1264×1490. No resize
input was sent; a window-manager explanation is plausible, not established here.

At2026-10-06T23:22:30Z, the post-restoration audit found the same world/window/PID,
both workers live and nil service/UI errors. It had naturally reached tick4371,
12planets,1star,3protostars,1brown dwarf and810nebula parcels, fixturefalse.
This later native state is observed separately from the three earlier images.
At23:27:25Z the exact process still existed in stateS; no pause or signal was sent
by this task. Root owns later resource pauses and lifecycle decisions.

## Evidence boundaries and reproduction

This proves an actual fresh game scene/HUD was rendered by the Intel hardware
context and remained healthy after a bounded observation. It does not establish
manual flight, overlap/commitment/sculpt, Gate completion, native FPS, GPU speed,
or an unobscured user-visible desktop. No screen/other-window pixels were captured.
A locked desktop can prevent external visibility; it was not unlocked. Concurrent
static-analysis/native work existed, and pixel readback/PNG writing add cost.
Neither the3screenshots nor the134observed scene calls are a performance result.

OBSERVER-PLAN.md is the preparation-only plan preserved as authored, not a claim
that its now-completed installation is pending. Actual command/results and all
readback errors are retained. Paired image EDN identifies PID/window, actual GL
identity, capture boundary and after-scene world read. `bb summarize-observer.bb
.` from this directory reproduces the derived summary from raw EDN without
loading runtime record classes; original tagged camera records remain preserved.

Closed client stdout/stderr are deterministic gzip archives, with decoded byte
hashes in decoded-hash-provenance.json. Runtime stdout/stderr remain open; their
committed gzip files are immutable PREFIX snapshots at23:27:25Z, not complete
future process logs. Raw originals are left untracked and named explicitly in
UNTRACKED-RAW-FILES.txt. No log bytes were rewritten to remove blank EOF lines.

CLOSED-FILES.txt lists only the closed inventory; SHA256SUMS hashes every listed
path except itself. Run `sha256sum -c SHA256SUMS` from this directory. The active
logs and pre-existing manual-approach evidence are excluded, not silently treated
as completed artifacts. Root owns commit/publication and the canonical live-game
card comment; the card remains in progress.
