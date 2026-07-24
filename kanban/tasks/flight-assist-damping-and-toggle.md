---
uuid: "flight-assist-damping-and-toggle"
title: "Flight assist: velocity + angular damping terms with FA-off toggle (R Release)"
status: "todo"
priority: "P1"
labels: ["domain", "physics", "player", "spark", "spark-flight"]
created_at: "2026-07-23T00:00:00Z"
source: "kanban/tasks/flight-assist-damping-and-toggle.md"
category: "specs"
estimate: 5
---

# Flight assist: damping terms + FA-off toggle

> spark-flight epic, Wave 1 card 3 of 10. Design: `docs/designs/spark-flight-and-camera.md` §3.3, §7.
> Elite-style fly-by-wire: FA ON (default) damps velocity and angular velocity
> toward zero when input is released; FA OFF removes ONLY the damping terms
> (pure Newtonian drift). Gravity always applies; hard safety clamps never toggle.

## Grounded integration (from design investigation, cite file:line)
- Add two damping channels, gated on an FA-on boolean (a `c/observer` flag or a
  spark component):
  - Linear: `a_damp = -k_lin · v`, emitted as an acceleration channel the linear
    integrator sums (`genesis/systems.clj:47`).
  - Angular: `τ_damp = -k_rot · ω`, emitted as a torque channel the rotation
    integrator (card 1) sums.
  These are ADDITIONAL channels, not new writers of velocity/ω — same
  single-writer rule (`registry.clj:536`).
- FA is a **boolean gate on the damp terms only** — one physics path, not a mode
  fork. Keep an ALWAYS-ON hard clamp on `|v|` and `|ω|` regardless of FA state
  (Elite's "partial limiter"); only the convergence damping toggles.
- **Bind the FA toggle to `F`** (a free key — `R` is reset-camera, `C` is
  cycle-camera; see the corrected binding map in
  `docs/designs/spark-flight-and-camera.md` §7). Wire the actual key in card 4;
  expose the toggle function + state here.
- `k_lin` should be a meaningful fraction of forward accel so FA-off measurably
  frees maneuver headroom (matches the Elite feel where damping consumes thruster
  budget). Constants in `law/`.

## Done when (player-visible via live pm2 window)
- FA ON: release all input and the spark coasts to a stop (relative to its local
  frame) instead of drifting forever; stop rotating and the spin brakes to zero.
- FA OFF (`R`): release input and the spark keeps its velocity and spin — pure
  drift; gravity still curves the path.
- `|v|`/`|ω|` never exceed the hard clamp in either FA state.
- `clojure -M:test` + architecture-test + `bin/analyze --strict` green;
  `write-conflicts {}`.

## Risks
Over-damping feels sluggish, under-damping feels floaty — live-tune `k_lin`,
`k_rot` in the pm2 window. FA-off must not disable the safety clamp. Ensure damping
channels compose additively with gravity, not overwrite it.

## Dependencies
Cards 1, 2. Unblocks card 5 (coherence taper reads FA state), card 10 (HUD shows
FA indicator).
