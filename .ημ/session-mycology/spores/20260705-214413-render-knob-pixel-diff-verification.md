---
status: rejected
reviewed: 2026-07-10T21:27:27Z
reviewer-session: 1b81c939-4735-419a-b6d8-c07830042386
created: 2026-07-06T02:44:13.784709815Z
source-session: /home/err/spaces/Truth
source-task: SPH froxel bridge: verify :volume-config threading end-to-end
p-efficiency: 0.5
p-friction: 0.5
p-skill-candidate: 0.75
p-recurrence: 0.75
p-generalizable: 0.65
p-worth-promoting: 0.75
promoted-to: ""
rejected-reason: "Too narrow: tightly coupled to this project's GL renderer, screenshot-request API, and nREPL bencode client. The frozen-scene control and host-side shader reproduction are valuable debugging habits, but they do not generalize enough to warrant a standalone skill."
---

## Problem
A render tuning knob can be wired through many layers (opts -> config merge -> descriptor -> shader uniform) and still silently not reach the GPU; unit tests cover the pure layers but not the GL uniform read, and dim scenes make eyeballing useless

## Pattern
Drive the knob to an extreme value through the real entry point (screenshot-request :opts), capture before/after frames of the same scene, and numerically pixel-diff them (mean/max/percent-changed); a nonzero structured diff proves the knob reaches the shader even when the change is invisible to the eye.

CORRECTION (same session, later): the diff is only evidence if the scene is
FROZEN between shots — with a live sim, drift produces a nonzero diff that
false-positives the knob. This exact mistake concluded "config reaches the
shader" while the screenshot fog was actually sampling an incomplete
cross-context texture (constant black). Pause the sim or diff same-tick
renders, and pair the pixel diff with a numeric reproduction of the shader
math on the host data — the mismatch between "math says bright blue" and
"render says black" is what actually exposed the context-bound texture-cache
bug.

## Candidate skill outline
- Name suggestion
- Trigger phrases
- Key steps or rules
- Anti-patterns to avoid

## Better path
Keep a tiny nREPL bencode client + pixel-diff snippet handy for the dev service; remember take-screenshot! ignores live window cfg — pass opts via the screenshot-request map. Control for sim drift (freeze or same-tick), and when a render contradicts host-side data, re-implement the shader math in Clojure over the same arrays — a bright-math/black-pixels contradiction localizes the fault to the GL boundary (uniforms, bindings, context-bound resources).

## Receipt refs
- none
