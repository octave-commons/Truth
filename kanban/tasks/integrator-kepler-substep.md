---
uuid: "integrator-kepler-substep"
title: "Wisdom-Holman Kepler sub-stepping inside the integrator (fixes orbit decoherence)"
status: "review"
priority: "P1"
labels: ["domain", "physics", "integrator", "multi-timescale", "blocker"]
created_at: "2026-07-23T00:00:00Z"
source: "kanban/tasks/integrator-kepler-substep.md"
category: "specs"
estimate: 8
---

# Wisdom-Holman Kepler sub-stepping inside the integrator

> multi-timescale epic, card 1. Design: `docs/designs/multi-timescale-integration.md` §3.
> THE correctness fix for the orbit-decoherence blocker
> (`planet-orbit-circularization-blocker.md`): compact bodies get their own
> sub-tick orbital resolution without shrinking the global `dt` or adding a serial
> tier — all inside the existing single integrator writer.

## Grounded integration (from investigation, cite file:line)
- Sole writer of position/velocity is `domain.integrator.kinematics/kinematics-ws`
  (+ `-soa`) at `src/domain/integrator/kinematics.clj:65-96,147-183`, currently
  symplectic Euler `v'=v+a·dt; x'=x+v'·dt` at the global `dt`. Do the fix INSIDE
  this `:run` — no new writer, no registry change (architecture map §5/§(c)).
- For each compact body (gate on `c/body-kind` ∈ {star, planet} or "has a
  resolvable parent"), run a K-sub-step **Wisdom-Holman split** in pure Clojure
  **on the parent-relative state** (`r = x_body − x_parent`, `u = v_body −
  v_parent`; design §3.0 — REQUIRED: at 80 yr/tick a parent star moves 5–20 AU,
  so integrating the inertial position about a frozen parent point re-creates
  the ejection bug): `drift_Kep(h/2) → kick_pert(h) → drift_Kep(h/2)`,
  `h = dt/K`, where `drift_Kep` is an ANALYTIC Kepler advance of the relative
  coordinate (μ = G·(M_parent + m_body); solve Kepler eqn / f-g functions —
  zero discretization error at any step) and `kick_pert` is the frozen TIDAL
  perturbation `a_pert,body − a_pert,parent` (the parent takes its own pert kick
  in its ordinary Euler advance). The parent's own advance is computed first by
  the same `:run` (sole writer — legal internal ordering), then the final write
  composes: `x_body' = x_parent' + r'`, `v_body' = v_parent' + u'`. Emit ONE
  `{c/position c/velocity}` write.
- **K frozen at tick entry** from the frozen snapshot:
  `K = clamp(ceil(dt / min(f_orb·T_orb, sqrt(2η·ε/|a|))), 1, 4096)`,
  `f_orb≈1/20..1/50` (4096 covers the 1-AU placement floor: K=1600 at dt=80 yr;
  LOG when the clamp binds — eid, demanded K, clamped K).
  NEVER recompute K or h mid-loop from within-tick state — that phase-space-
  dependent stepping IS the secular-drift bug (Dehnen & Read 2023; design §3.3).
- **Composition (design §3.4):** `dv.*` channels + absorb/merge COM blending
  apply as ONE outer kick/blend after the K-loop on the composed world-frame
  state, never inside it; `foff` subtracts from the final composed position as
  today (cancels in relative coordinates). Bodies with no resolvable parent
  fall through to the existing symplectic-Euler path.
- Dominant parent = nearest bound star (see `central-star-nearest-attractor`, card
  4; a minimal nearest-star lookup can be folded here to unblock).
- Non-compact bodies (gas) keep the single global-`dt` symplectic-Euler path
  unchanged. `par-mapv` parallelism (`kinematics.clj:36-38,63`) is preserved — the
  K-loop is entity-local.

## Done when (player-visible via live pm2 window)
- An isolated two-body pair at realistic late-sim `dt`/`softening` holds bounded
  eccentricity over 10^4+ ticks instead of drifting to 1 (see
  `orbit-integration-regression-tests`).
- In a live run, some bound planets reach eccentricity < 0.4 and become
  `c/planet-candidate`s (with card 2 for placement, this becomes the norm).
- `clojure -M:test` + architecture-test + `bin/analyze --strict` green;
  `write-conflicts {}`.

## Risks
Kepler-solver correctness/perf (cache f-g coefficients; clamp K at 4096, log on
bind). Relative-frame bookkeeping (parent advance must be computed before the
dependent bodies compose — one `:run`, sequential within the system's own
closure is fine; keep the fold's write-set disjoint). Perturbation
freezing valid only while `dt ≪ τ_slow` — assert. High-blast-radius: it changes
core integration for compact bodies — land on its own branch, keep the gas path
byte-identical, live-tune.

## Dependencies
Benefits from card 4 (nearest attractor). Unblocks card 2's payoff and the entire
voxel/progression pipeline. Gated on card 3 tests before merge.

## Work notes (2026-07-23)

Implemented + full suite green (833/15292). WH K-sub-step in `kinematics-ws` + `-soa`
via shared helpers: parent-relative state, Stumpff f-g drift, frozen tidal kick,
parent Euler-advanced first in the same `:run`, dv/absorb outer kick, foff after
composition. `kep/propagate`: universal-variable bracketed Newton (monotonic ⇒
always converges, ex-info on failure). Deviations (documented in code):
sub-step gate narrowed to `#{:planet :gas-giant :stellar-remnant}` (formation-era
intermediates stay on Euler — embedded cores broke the frozen-tidal assumption);
added `substep-dominance-factor` (100×) validity gate with Euler fallthrough
(design §3.1's own "small perturbation" condition made executable); reversibility
asserted on relative orbit at 1e-4 (world-frame residual is Galilean COM-recenter
translation). Red-capture before fix: ejected within ~1 tick, 2×|E0| energy swing,
4e-2 reversibility error. Live candidate emergence still to be observed (needs a
fresh or long-run sim — existing e≈1 planets are not retroactively re-circularized).
