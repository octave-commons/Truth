# Native line-pass regression: RED

The production line pass returns GL_INVALID_VALUE (1281) in the production
forward-compatible core context. This is reproduced independently for legacy
vertices with default opacity and fading trail vertices, while both paths draw
nonblack framebuffer pixels. The test compiles the real shader and invokes the
existing private line pass; no production API exposure, mock GL call, alternate
renderer, world fixture, or production source modification was used.

## Evidence

- `red-2.log`: one native test, nine assertions, two expected failures, zero
  errors; exit 1. The core/forward-compatible context flags, initial setup,
  actual framebuffer pixels, and readback error checks passed.
- `red-2-start.json` / `red-2-end.json`: exact source revision, command, bounded
  heap, private-display ownership, elapsed 13.913 seconds, and exit status.
- `red.log` / original start/end metadata: first attempt failed before tests
  because inherited JAVA_OPTS requested an initial 3 GiB heap while the command
  capped the maximum at 1 GiB. It is not counted as regression evidence.
- `owner-gl-debug-messages-2.edn`: byte-for-byte copy of the native owner's
  bounded driver diagnostics on gates-of-truth-dev-window: nine messages naming
  `GL_INVALID_VALUE in glLineWidth`. Copy provenance and hashes are recorded
  separately. This is diagnostic observation, not a new rendering path.
- `owner-gl-debug-restored.edn`: owner's readback after callback unregister,
  original debug-output state restoration, and callback free.
- `source-provenance.json`: both glLineWidth(1.5) and the forward-compatible
  context hint exist at e71b12f and blame to 63666769 (2026-07-08). The e71b12f
  to 400ba3a diff changes line mesh packing, not the requested width or context.
  This is an existing invalid call exposed by stronger native verification;
  no claim of an earlier native execution was inferred from source history.

The rule is explicit in the [Khronos OpenGL 4.5 core specification, Appendix
D.2.1](https://registry.khronos.org/OpenGL/specs/gl/glspec45.core.pdf): widths above
one are removed in forward-compatible core contexts and generate INVALID_VALUE.

## Verification scope

`red-static-final.json` records clean native cljfmt, native Splint (zero style
warnings), kondo over the new test/config/tool predicate (zero warnings/errors),
valid analysis-shell syntax, a clean diff check, and an empty `git diff HEAD --
src` proving production source untouched. The only post-run test edit was
cljfmt indentation. The explicit native alias uses existing dependencies and
keeps the ordinary test suite headless. All six analysis tools now include the
new test path through direct path/alias inclusion, with no rule relaxation.

The earlier broader `red-static-jvm.json` is preserved: it found native test
indentation (now fixed), plus preexisting formatting and the single-segment
namespace warning in dev/smell_report.clj when that normally excluded tool
source was explicitly checked. The unrelated existing tool forms were not
rewritten or suppressed. Its new project-file? path clause passes kondo.
The full normal suite and strict gate have not been rerun for this RED patch.

## Proposed GREEN after checkpoint

Set the existing line pass's explicit width to 1.0 and explain the context
restriction inline. An explicit supported width prevents dependence on prior
GL state. Keep context/profile selection and the primitive/shader/opacity path
unchanged. Repeat this actual native regression and focused static/render
checks; the active world owner separately controls native capture validation.
