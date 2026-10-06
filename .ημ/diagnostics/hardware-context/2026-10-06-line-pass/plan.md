# Proposed hardware-context validation; preparation only

No commands below have been executed. Root must first release the exclusive
cost-work window and authorize context creation. The retained Java3083624 world
on Xvfb:2 / nREPL7895 stays untouched. No world snapshot, migration, reset, input,
desktop unlock, screenshot, service restart, driver installation, Xauthority
cookie extraction, access-control change, or global setting is part of this plan.

## Passive observations

- Intel Meteor Lake-P / Arc8086:7d55 is bound to i915 (renderD128/card1).
- NVIDIA RTX4070 Laptop10de:2820 is bound to nvidia580.178.04
  (renderD129/card0). Both render/card pairs are readable+writable to uid1000.
- glxinfo, eglinfo, xdpyinfo, xwininfo, xauth and Xorg are installed.
  vglrun/VirtualGL and Weston are absent. Do not install a new route for this check.
- The current inherited display is :0, session type x11. /tmp/.X11-unix/X0
  exists. Inherited XAUTHORITY points to a readable uid1000 mode0600 file.
  Only pathname/stat/access metadata was inspected; its content was not read.
  This does not yet prove X-server authorization or a working hardware context.
- Existing capture records identify a distinct owned Xvfb:2 llvmpipe context.
  Neither its existence nor its software renderer establishes :0's renderer.

## Smallest capability check, only after release

Use the existing login's inherited authorization. Do not copy or print cookies,
run xhost, change DISPLAY globally, or touch the :2 service. Each command is
bounded and targets only its own transient query/context.

```sh
timeout --kill-after=5s 15s env DISPLAY=:0 glxinfo -B
```

This step CREATES a transient GL context and therefore is not a passive inventory
command. Record stdout, stderr and the actual exit status. Stop on authorization,
display or context failure; do not attempt to unlock the desktop or change its
access policy. Require an actual Intel/NVIDIA hardware renderer in the output;
a successful llvmpipe/softpipe/software result is not hardware acceptance. Do not
interpret `direct rendering: Yes` alone as hardware evidence. No PRIME override
or NVIDIA-specific offload configuration is proposed in this first check.

## Existing hidden native regression

Source inspected at Truth c79be1e957695505b48ea7f1905821927e7fb270 in
/home/err/spaces/foresight/.worktrees/truth-focus-cadence.

The existing test uses `infra.render.window/create-offscreen-window` at64x64:
GLFW_VISIBLE=false, the production3.3 forward-compatible core hints, and the
actual line shader/pass. It does not show the window, request focus or send input.
Its own try/finally retires shader program, current context, capabilities, window,
GLFW and error callback. A separate JVM isolates these resources from the game.

The unmodified canonical test invocation on the candidate display is:

```sh
cd /home/err/spaces/foresight/.worktrees/truth-focus-cadence
timeout --kill-after=10s 180s env DISPLAY=:0 clojure -J-Xms128m -J-Xmx1g -M:test:native-render-test
```

That command tests the real context but does not itself print GL identity. Prefer
ONE recorded execution below instead of running both forms. The prepared /tmp
script wraps only the existing test's private `with-hidden-context!` callback,
records GL_VENDOR/GL_RENDERER/GL_VERSION and the hidden attribute inside the exact
context being tested, then invokes the unchanged test callback once. It neither
creates a second context nor exposes/changes production APIs. The inherited test
cleanup remains in force even if identity recording or the test fails. Its var
replacement is process-local and restored after the test.

```sh
cd /home/err/spaces/foresight/.worktrees/truth-focus-cadence
timeout --kill-after=10s 180s env DISPLAY=:0 clojure -J-Xms128m -J-Xmx1g \
  -Sdeps '{:aliases {:hardware-native-record {:main-opts ["/tmp/truth-hardware-line-probe.clj"]}}}' \
  -M:test:native-render-test:hardware-native-record
```

The extra alias changes only this invocation's main entry, retaining existing
source/test paths and dependencies. The script calls clojure.test/run-tests for
the sole existing native regression; expected evidence is1test/9assertions,
0failures/0errors,1recordedhiddencontext and restored original test function.
Its exit0 means those checks passed, NOT automatically a hardware verdict: root
must inspect the recorded actual renderer, which may differ from glxinfo's
context selection. Preserve any failure/timeout as failure rather than skip.

Record exact git HEAD and source hashes before execution. If the source changes,
recheck the inspected lifecycle before proceeding. Current SHA256 identities:

- test-native/infra/render/line_pass_test.clj:
  dd9e771af5eee80fbcfda1d59d60588159c3106b67f127f3a3420ffb04b44fe7
- src/infra/render/window.clj:
  817fef4fc2f39f54b5184c14541d3f43c9afaf7a250ece349e926c3cb3d75b14
- src/infra/render/scene/setup.clj:
  a87cf23a26c38424e6d66f9f315ade5ef0112df563c2df320c8d4bf35bf716f7
- deps.edn:
  ca96620b1410a3184b135d2439dfea1b5ae7c4468646382294fdee1b403b4e8e

A passing hardware-context line regression establishes hardware access plus that
specific shader/pass contract. It does not establish visible game acceptance,
full-scene rendering, GPU performance, native FPS, or any Gate gameplay result.
