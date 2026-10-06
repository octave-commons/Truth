# Native line-pass regression: GREEN

The production line pass now requests an explicit width of 1.0, with an inline
comment explaining the forward-compatible core restriction. The context hints,
shader, vertex opacity, mesh upload, draw call, and pass cleanup are unchanged.
The only production diff above RED checkpoint 923fd6d is that value and comment.

- `green-native.log`: the same real-context test now passes all nine assertions
  (one test, zero failures/errors), including native GL_NO_ERROR and nonblack
  framebuffer pixels for legacy/default-alpha and fading-alpha lines. Exit 0.
- `green-focused.log`: 17 tests and 72 assertions, zero failures/errors, across
  actual infra.render.passes-test, shader-test, and trail-test. The command also
  named infra.render.mesh-test, which does not exist and was not selected by
  the runner; no nonexistent namespace is counted as coverage. The trail test
  exercises real line-buffer packing.
- `green-static.json`: native-test and changed-production cljfmt/Splint clean;
  kondo zero errors/warnings; diff check clean.
- Start/end metadata records exact RED source checkpoint, complete production
  diff, bounded 1 GiB heap, private Xvfb ownership, elapsed times and exit codes.
  Every owned test/style process exited; no input or instrumentation touched
  the active game owner's display or world.
- Independent read-only review by board_scout reported no blocking findings.

This closes the specific native error demonstrated by the RED test. It does
not substitute for the card's live star/planet/Spark fading and readability
acceptance. The full headless suite and all six strict gates for this new
source checkpoint remain root-coordinated work. The existing ordinary native
world still runs its prior loaded source until its owner deliberately reloads
or restarts it; these isolated test results make no claim about that process.

The prior RED artifacts and checksums remain unchanged, including the explicitly
non-passing first heap-start and broader existing-tool-style probes.
