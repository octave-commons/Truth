# Spark Flight & Camera — Elite-style 6DOF piloting for the mote

**Path:** `docs/designs/spark-flight-and-camera.md`
**Status:** Design — approved direction (owner decisions 2026-07-23). Implementation
tracked as the `spark-flight` epic; see the Roadmap section at the end.
**Supersedes for movement/camera:** the tether/spring "narrowing" mechanism, which
`spark-as-gravity-bound-body` (done) already began dismantling. Design against
"the spark is a real physics body; player input applies force; camera tracks the
body" — never against the spring.

---

## 1. Why this exists

The spark is now a real, gravity-bound ECS body (`spark-as-gravity-bound-body`,
done). But the *player's* control over it is still a per-frame position teleport:
`drift` writes `pos' = pos + velocity·wall_dt` directly onto `c/position`
(`src/domain/player/focus.clj:19-45`), driven from the window loop
(`src/infra/dev/window/loop.clj:266-294`). That teleport is the root of every
movement complaint:

- **"The spark jumps on WASD."** Two writers fight over position each frame — the
  teleport and the gravity integrator (`kinematics.clj:76-79`). The camera lerp at
  `t=0.35` (`tracking.clj:147-149`) only *masks* the jitter.
- **"Going directly toward camera-forward feels wrong."** Correct — in third
  person the camera-forward ray and the ship-forward ray must not be the same ray
  (see §5). Teleport-along-camera-forward guarantees the wrong feel.
- **No sense of a vehicle.** Position teleport has no velocity, momentum,
  orientation, or inertia to read.

We rebuild movement as **force/torque applied to a physics body**, in the
Elite-Dangerous idiom the owner cited as the best space controls in any game.

## 2. Approved decisions (owner, 2026-07-23)

| Decision | Choice |
|---|---|
| Flight model | **Elite-style assisted 6DOF.** Flight-Assist ON by default (velocity + angular velocity auto-damp when input released); toggle to FA-OFF for pure Newtonian drift. Gravity always applies. |
| Rotation | **Full torque-based angular momentum.** New `c/orientation` + `c/angular-velocity` components; input applies torque; a rotation integrator advances them (single writer). FA-off spin persists. |
| Thrust cost | **Thrust drains coherence; regenerates while coasting.** Soft taper as coherence falls, hard lockout floor with hysteresis. Empty → drift only. |
| Input | **Mouse aims (yaw/pitch); keyboard for roll, the six translational thrusters, throttle, boost, FA toggle.** All rebindable. |
| Doctrine | **Amend: the 6DOF piloting layer IS the Phase-0 expression of "Camera-navigate."** The four semantic control pillars are unchanged (see §7). |
| Trails | **Star + planets + spark** keep a ring-buffer trail; render as fading line-strips. Dust/fragments excluded. |
| Mote render | **Bespoke shader:** bright core + soft halo + a heading flare that points along the nose. Distinct from real stars. |
| Priority | **Rebuild the force-based flight model first.** The jump bugs dissolve as a side effect. |

## 3. Physics model (all as ECS acceleration/torque channels)

Everything composes cleanly with the existing single-writer, double-buffer Jacobi
tick (`CLAUDE.md` → "Double-buffer / single-writer tick"). Input systems emit
**force and torque channels**; the linear and rotational integrators sum them at
the barrier. **No system may become a second writer of `c/position`,
`c/velocity`, `c/orientation`, or `c/angular-velocity`** — that fails
`write-conflicts` (`src/domain/ecs/registry.clj:536`) and `architecture_test`.

### 3.1 Linear

```
a_total = a_gravity                       ; existing (Barnes–Hut, halo, dark-matter)
        + a_thrust(input, orientation)    ; new: forward/strafe/vertical thrusters
        + (FA-on ? a_damp : 0)            ; new: flight-assist velocity damping
```

- `a_thrust` is applied along the **body axes** derived from `c/orientation`, not
  camera axes. Forward accel ≈ **1.5–2× lateral/vertical** (Elite ratio) so
  forward reads as *the* propulsion axis and strafe reads as maneuvering.
- `a_damp = -k_lin · v` (or damp the error `-k_lin·(v - v_commanded)` if we later
  want throttle-holds-a-target-speed). This is the fly-by-wire layer, expressed as
  a force so it's diegetic (thrusters fire to cancel drift).
- An **always-on** hard clamp on `|v|` stays active regardless of FA state.

### 3.2 Rotational (new substrate)

Rotational integration research and concrete convention/time-step contracts:
[Spark rotation integration](../research/physics/spark-rotation-integration.md).
This tracked note grounds the orientation card and records the unavailable
original research file without claiming to reconstruct it.

```
τ_total = τ_thrust(input)                 ; new: pitch/yaw/roll from mouse + keys
        + (FA-on ? τ_damp : 0)            ; new: angular-velocity damping
τ_damp  = -k_rot · ω
```

- New components: `c/orientation` (unit quaternion) and `c/angular-velocity` (ω,
  rad/s). New **rotation-integrator system** advances them from summed torque,
  one-tick Jacobi lag like the linear integrator.
- FA-off: release the stick and ω persists (drift-spin, flick-turns). FA-on:
  `τ_damp` brakes ω to zero. Always-on hard clamp on `|ω|`.
- Moment of inertia scales with spark radius (which shrinks as formation-progress
  rises) — a resolving spark becomes tighter/snappier to turn. Optional refinement,
  not required for v1; a constant `I` is acceptable first.

### 3.3 Flight assist is a boolean gate, not a mode fork

FA toggles the two `*_damp` terms only. There is exactly one physics path; the
hard `|v|`/`|ω|` safety clamps never toggle. This mirrors Elite's "partially-on
limiter" and keeps the tick logic single-path.

### 3.4 Time scaling (project gotcha)

Coherence regen and any "per-tick" pacing MUST scale by sim-time/dt, not raw tick
count — `dt` dilates with the bulk-collapse dynamical time (`CLAUDE.md` → Time
model; `.agents/skills/physics-dt-unit-mismatch/`). Thrust *forces* are physical
(per-second) and integrate correctly through the existing integrator; the economy
regen is the part that needs sim-time pacing.

### 3.5 Wave 0 precision range (2026-10-06 amendment)

Scope: repair the existing three-point `flight-no-jump-accel` smooth-approach
acceptance. The released [precision investigation](../notes/2026-10-06-manual-flight-precision-boundary.md)
and its [production-system probe](../../.ημ/diagnostics/playable-foundation/manual-flight/response-probe.edn)
show that the current minimum displacement setting, `D = 1e12 m/tick`, settles
through 6.68459 AU after a single accepted input tick. The binding radius is 1 AU.
Native coarse travel succeeded; that evidence does not establish fine approach
or commitment to a moving, currently eligible planet.

Expose **Cruise** and **Fine** choices in the existing Spark panel beside the
thrust setting. Cruise selects the unchanged default `D = 3e14 m/tick`; Fine
selects `D = 1e7 m/tick`. Keep the existing half/double stepper, lower its minimum
to `1e7`, and retain its `1e16` maximum. Both choices set only
`:genesis/spark-flight-displacement` through the existing world-intent path.
The current numeric value remains visible; a choice is marked selected only
when its value matches. Custom stepper values select neither preset. Retention,
camera mode, focus, position and velocity remain untouched by choosing a range.
Selecting Fine while cruising does not instantly stop the spark.

The sizing uses the actual frozen-snapshot channel delay. At constant `h`, let
`w = v h` and `α = 1−r`; the force-free recurrence is
`w[n+1] = w[n] − α w[n−1] + α D u[n−1]`. One input tick from rest settles through
`D`; release from steady thrust coasts `r D / (1−r)`. For a coast allowance of
`0.1 R`, require `D ≤ 0.1 R (1−r)/r`. Fine's `1e7` gives a maximum coast of
0.066779 AU over the full exposed `r = .80–.999` range, and a settled pulse of
0.000066846 AU. These are constant-dt, isolated-body bounds, not validated feel
or a guarantee under gravity, changing dt, inherited cruise velocity or moving
targets. Consecutive unequal timesteps consume the prior step's acceleration;
the tests must characterize that response without claiming adaptive invariance.

Regression verification must discover the actual panel actions and apply their
world intents, then exercise the real thrust/integrator pair through
`tick/run-parallel`: initial lag, pulse distance, steady-release coast, several
constant timesteps and the exposed retention endpoints. No test may reposition
the spark after fixture initialization. Check Cruise/default preservation and
that the settings intent changes no physical components. Full tests, strict
analysis and native visibility of the choices remain required. Correct the
emitter documentation's exact-retention and guaranteed-wall-60-Hz claims.

This slice adds no local-frame reference, velocity matching, automatic slowdown,
capture controller, FA toggle or binding-rule change. The separate native
fly→resolve→sculpt acceptance remains open until genuine controls demonstrate it.

## 4. Coherence-gated thrust (the "let it rise" economy)

The separate proposed [active-influence refinement](player-active-influence.md)
retires the passive focus halo and strengthens paid fields. The halo coupling
described below records the existing baseline, not a requirement to retain it
if that refinement is admitted; coherence/thrust behavior is unchanged there.

Coherence already exists as a `c/observer` field (0.8 init, drain/regen in
`src/domain/player/economy.clj:1-27`) and already modulates the observer halo
(`influence.clj:20`) and render opacity (`scene/bodies.clj:152`). We add a thrust
coupling on top, shaped as a heat/stamina meter (not a charge-and-spend ultimate):

- **Drain** while thrusting/boosting: `coherence -= k · |a_thrust| · dt_sim`
  (boost via E Nudge drains harder).
- **Regen** while coasting: existing regen, sim-time paced.
- **Soft taper:** below a soft threshold, available thrust accel scales down
  (telegraphs the limit — the player feels it before they hit the wall).
- **Hard floor + hysteresis:** at the floor, thrust locks out entirely until
  coherence recovers past a higher re-arm threshold (no flicker at the boundary).
  At lockout the spark can only drift on gravity + residual momentum — which is
  exactly the "let it rise" beat.

This keeps coherence a single meaningful resource: it is your attention, your halo
strength, your brightness, **and** your thrust budget. Burn to maneuver, coast to
recover.

## 5. Camera (third-person chase)

Every non-manual view is **debug/cinematic** by owner decision. `:manual` is the
one the player flies in, and it becomes a proper spring-damped chase rig.

### 5.1 Manual chase rig (rebuild `update-camera-manual`, `tracking.clj:143-152`)

1. **Anchor** behind the ship on a velocity-stabilized frame:
   `anchor = ship.pos − forward·chaseDist + up·chaseHeight`, fixed `chaseDist`
   (not velocity-scaled — a starfield has no road to read scale against; sell
   speed with FOV/parallax/vignette instead).
2. **Velocity-biased look-ahead** for the aim target:
   `lookTarget = ship.pos + normalize(v)·lead·clamp01(|v|/vRef)`, fading toward
   body-forward near zero speed so the camera doesn't hunt while stationary.
   **This is why flying "directly at camera-forward" felt wrong** — the aim point
   leads *velocity*, and the ship sits in the lower-center third of frame, not on
   the camera axis.
3. **Two critically-damped springs** (ζ=1, closed-form, no overshoot): one for
   chase position, a **slower** one for the aim target. Decoupling catch-up speed
   from aim speed is what stops the camera feeling like it's on a stiff rod.
   (Replaces the single `lerp-toward` at `t=0.35`.)
4. **Partial roll inheritance:** camera roll = ship roll × factor (~0.3–0.6),
   itself spring-damped. Full lock kills the banking read; full 1:1 is nauseating
   without a horizon. This factor is a first-class tunable.
5. **Look decoupled from movement:** mouse-look orbits the camera around the
   anchor; after a short idle it eases back to the velocity-aligned rest pose.

### 5.2 Debug/cinematic views (`:follow-selection`, `:track-largest-cluster`, `:fit-all`)

- Keep them, but **label them debug/cinematic** and stop them from stomping the
  player's manual view state. Today they reset yaw/pitch to defaults
  (`tracking.clj:207-208`) and never restore — this is the "switching views resets
  the camera / the mote jumps to where it used to be / jumps back when I move"
  cluster. Fix: **store the manual camera state on leaving `:manual`, restore it on
  return.** Debug views are free to reframe; manual is inviolable.
- Verify the tracking-offset (`distance-for-radius`, cluster centroid,
  radius-floor at `tracking.clj:193-227`) against the live window; the code reads
  the correct `c/position` (`observer-render-position`, `tracking.clj:56-63`) so
  the offset is a framing-math bug, not a wrong-target bug.

### 5.3 Coordinate discipline

Z-up is canonical; up vector stays `[0 0 1]` (`render/scene/setup.clj:66`). Never
reintroduce `[0 1 0]`. Render view scale is `phase0-view-scale = 1e15`
(`projection.clj:17-19`) — the spring math runs in render units after that divide.

## 6. Visuals

### 6.1 Body trails

New ring-buffer trail component on the **star, planets/protoplanets, and the
spark**. Sample position at a sim-time cadence (not per-render-frame), render as a
fading line-strip via the renderer's `:line` path (raw positions — note the
renderer's `:body` vs `:line`/`:particle` split, `CLAUDE.md` → Coordinates). Cap
total segments. Dust/fragments excluded to keep a dense nebula legible. Trails are
the perspective anchor that lets the owner judge whether flight and camera feel
right.

The [trail clock and renderer grounding](../notes/research/2026-10-06-body-trails-rendering.md)
records the current Jacobi snapshot timing, COM recentering, debris tags, and
missing line-opacity transport. The implementation contract is
[body-trails-ringbuffer](../../kanban/tasks/body-trails-ringbuffer.md), with the
[reviewed implementation plan](../notes/2026-10-06-body-trails-plan.md).

The trail owner retains up to 64 actual position/time observations per eligible
body, on a 1e10-simulated-second deadline cadence and within a 6.3e11-second
history horizon. It records at most one observation when a large step skips
deadlines, reports the skipped count, and never invents the intervening path.
Different step partitions agree only where they observe the same actual
position at shared sampling times; they are not required to reconstruct
unobserved positions. Every retained point, including a new sample, receives
the body's applied frame translation once per physics fold, even when no sample
is due. In the existing optional LOD throttle mode, a non-due body is frozen and
receives no position/recenter write, so its history likewise receives zero
translation. The trail adapter reuses the integrator's due-entity predicate;
sampling deadlines and expiration still advance. The renderer connects that
history to the current published body position.

Filter significant bodies before history work: spark, explicit star/planet/
gas-giant states, or a non-nebula body explicitly tagged `:body/planet`.
Unclassified planetesimals and collision fragments are not automatically
protoplanets. Trails use the existing raw line pass with real per-vertex
opacity, retain ordinary lines' 0.85 default opacity, and share a 4,096-segment
budget with deterministic allocation and visible dropped-segment diagnostics.
New pure timestamp, history, frame, and opacity logic belongs in portable
`.cljc` namespaces where practical; JVM ECS/GL adapters remain outside it.

### 6.2 Mote of light

Replace the screen-space particle sprite (`scene/hud.clj:29-46`) with a bespoke
world-space mote: **bright core + soft halo + a heading flare** oriented along the
nose (uses `c/orientation` from §3.2). Coherence modulates brightness. Reads as a
"vessel of light," visually separable from real stars — while still narratively a
proto-star that later resolves into an actual body.

## 7. Control doctrine (corrected 2026-07-23)

The real doctrine is **role stability per key**, not a fixed count of verbs (the
earlier `Q Focus / E Nudge / R Release` framing was aspirational text never bound
in code — `nudge` is docs-only, `release` was deleted with the spark spring). A
key's role is fixed for the life of the game; what changes is *what fills an
ability slot* (via the tech tree — `docs/designs/ability-tech-tree.md`). Keys are
**always on-screen** (the action palette already does this) so the player learns
to expect them. Three role-stable groups, none bleeding into another:

- **Pilot** — `WASD` + vertical thrusters + roll + boost + flight-assist toggle;
  mouse aims the mote; middle-mouse orbits the camera. Always movement.
- **Focus/aim** — arrows + `,`/`.` move and size the attention reticle (where
  abilities land and where binding/resolution accrue). Already wired. **In Phase 0
  this must also follow the pilot while flying — see §7.5, the linchpin.**
- **Abilities** — the on-screen quanta hotbar: `G` well · `Shift+G` repulsor ·
  `H` heat · `J` cool, each 15 quanta, placed at focus. Slots role-stable; the
  tech tree fills and re-arms them (→ sculpt verbs once a world resolves).

### Corrected binding map (all rebindable)

Respecting keys already taken: `C` cycle-camera, `R` reset-camera, `L` jump-to-
life, `[`/`]` fit-margin, arrows + `,`/`.` focus, `G`/`Shift+G`/`H`/`J` abilities,
`TAB` menu, `ESC` quit (`render/input.clj`).

| Axis / action | Binding | Group |
|---|---|---|
| Yaw / Pitch | Mouse X / Y (aims the mote) | Pilot |
| Camera orbit (decoupled) | **Hold middle-mouse** + move | Pilot |
| Roll L / R | `Q` / `E` (free) | Pilot |
| Throttle fwd / back | `W` / `S` | Pilot |
| Strafe L / R | `A` / `D` | Pilot |
| Vertical up / down | `Space` / `Left-Ctrl` | Pilot |
| Boost | `Left-Shift` (held) | Pilot |
| Flight-assist toggle | `F` (free) | Pilot |
| Focus aim / size | Arrows + `,` / `.` (unchanged; + auto-follow §7.5) | Focus |
| Abilities | `G` `Shift+G` `H` `J` (unchanged; tech-tree-filled) | Abilities |

Open reconciliation for the input card: mouse currently does look + left-drag-
orbit + left-click-pick. With mouse-aims-the-mote, orbit moves to middle-mouse and
left-click stays pick; confirm left-drag no longer orbits. `Left-Shift` boost
coexists with `Shift+G` repulsor because `G` is a discrete ability press in a
different group — flagged as a rebindable default, not a hard choice.

### 7.5 The linchpin — focus must follow the pilot

The resolve pipeline (binding accrual → commitment → voxel band render) reads
the **focus point**. The accepted manual law is position-only:
`:focus-position = Spark c/position + persistent :focus-offset`. Arrows adjust
the offset by 0.1 AU per press; comma/period adjust radius and intensity.
Tracking modes retain `sync-observer-focus-to-camera` and its camera target.
Neither attention path writes physical position or velocity.

**Serial cadence (2026-10-06 correction):** the simulation thread drains queued
input, then applies the manual law using that iteration's host mode and offset,
before the frozen simulation fold. Render frames no longer enqueue manual
focus-follow. This makes binding and other snapshot consumers read attention
and physical bodies from the same state even when several simulation ticks run
between frames. Manual preparation wins over an already queued tracking-focus
intent when the current mode is manual. Queued radius/intensity changes survive.
Both drained input and attention preparation use the same Throwable/map-result
guard: a failed update is dropped, preserving prior changes and loop operation.
Attention is prepared on held/error-paused iterations too, without physical
advancement.

The host offset crosses the named Malli `law.narrowing/focus-offset?` boundary
inside that guard before `player/focus-follow`: exactly three finite numeric
coordinates, retaining list/vector support and the absent-key zero default.
An explicit nil, malformed shape or nonfinite coordinate is rejected visibly;
the drained world survives unchanged, without clamping the host setting.
The [boundary RED](../../.ημ/diagnostics/focus-contract-review/red/VERDICT.md)
records nonfinite attention corruption that the exception guard alone missed.

Grounding: the actual host-loop regression uses the production integrator and
binding system with co-moving bodies half an AU apart. Render-only follow made
binding decay after the first tick under both zero and nonzero frame shifts;
see [observed RED](../../.ημ/diagnostics/focus-cadence/red-verdict.md) and
`test/infra/dev/focus_cadence_test.clj`. The underlying law and gameplay acceptance
remain on `focus-follows-pilot`, Wave 0.

**Boundary:** preparation aligns pre-fold consumers. The subsequently published
focus still represents the pre-fold position, while the integrator has moved
and recentered physical bodies. This correction does not predict/recenter that
published attention, recalculate sculpt anchors already created during input
drain, or change action positions captured by the renderer. Natural manual
fly-bind-commit-voxel and paid sculpt acceptance remain open until demonstrated
through ordinary controls.

## 8. North star & roadmap

**North star (owner, 2026-07-23):** the point of this work is a *playable loop* —
**fly to a formed planet, watch it resolve into voxels, and act on it** — not
flight for its own sake. The resolve pipeline is already built and reachable on
the `spark-gravity-bound-body` branch (voxel band renderer, binding/commitment,
HUD readout — all branch-only, **not on `main`**; owner chose to keep it on the
branch and merge as a batch later). Two things block the loop: focus doesn't
follow the pilot (§7.5), and the sculpt verbs aren't wired to input
(`voxel-sculpt-verb-palette-wiring`, existing todo — `domain/voxel/sculpt.clj`
`request-op` is "called from nowhere"). Fast-path those first.

Each card carries a **player-visible "done when"** gate verified in the live pm2
window (`gates-of-truth-dev`) — the recurring project failure is work marked done
while player-invisible (`board-shape-2026-07` memory).

**Wave 0 — Fast-path to the playable voxel loop (do first)**
- `focus-follows-pilot` — focus tracks the mote while manually flying, so
  binding/resolution/ability-targeting work without a debug view; fixes the
  coarse manual-focus (§7.5). **The linchpin.**
- `flight-no-jump-accel` — minimal fix: replace the `drift` position-teleport with
  acceleration-based movement (+ light damping), so flying to a planet is smooth.
  A subset the full 6DOF cards later extend. **Kills the WASD jump.**
- `voxel-sculpt-verb-palette-wiring` — *(existing todo, est 2)* wire the sculpt
  verbs (uplift/erosion/volcanism) into the ability hotbar so you can act on
  resolved terrain. **The payoff.**
- *(after this wave: the loop is playable — fly, resolve, sculpt.)*

**Wave 1 — Full flight physics (extends Wave 0)**
1. `spark-orientation-angular-momentum` — orientation + angular-velocity + rotation
   integrator (single writer).
2. `spark-flight-force-channels` — body-frame thrust-force + torque channels
   (extends `flight-no-jump-accel` with orientation-relative thrust + rotation).
3. `flight-assist-damping-and-toggle` — FA-on lin+ang damping; FA-off via `F`;
   always-on hard clamps.

**Wave 2 — Piloting input & economy**
4. `spark-6dof-input-mapping` — mouse aim + middle-mouse orbit + the §7 binding
   map; rebindable; manual-mode only; intent-queue wired.
5. `coherence-gated-thrust` — drain/regen, soft taper, hard floor + hysteresis,
   sim-time paced; boost on `Left-Shift`.

**Wave 3 — Camera**
6. `chase-camera-spring-rebuild` — spring chase rig, velocity look-ahead, fixed
   distance, partial roll inheritance (§5.1).
7. `debug-view-state-restore` — store/restore manual camera state across
   debug/cinematic views; fix tracking-offset framing (§5.2).

**Wave 4 — Visuals & readability**
8. `body-trails-ringbuffer` — trails on star/planets/spark (§6.1). *No flight
   dependency — pullable forward for grounding.*
9. `mote-of-light-shader` — bespoke core+halo+heading-flare mote (§6.2). Needs
   card 1 (orientation).
10. `flight-hud-and-cues` — throttle/speed, coherence taper/lockout, FA indicator,
    velocity + nose cues.

**Parallel track — Ability tech tree** (owner: design + build now): its own epic,
`docs/designs/ability-tech-tree.md`. Fills the ability slots the hotbar shows;
independent of the flight physics but shares the Focus/aim + Abilities groups.

**Dependency graph:** Wave 0 first (`focus-follows-pilot`, `flight-no-jump-accel`,
`voxel-sculpt-verb-palette-wiring` are mutually independent — parallelizable).
Then 1 → 2 (2 extends `flight-no-jump-accel`) → 3; 4 needs 2,3; 5 needs 2; 6 needs
1 (+benefits from 2); 7 needs 6; 9 needs 1; 8 independent; 10 needs 3,5.

---

*Generated from a design session — 2026-07-23. Research basis:*
*`scratchpad/spark-flight-controls-research.md` (Elite Dangerous flight model, Ace*
*Combat chase-camera framing, critically-damped-spring camera math, flight-assist*
*damping as acceleration terms, 6DOF mapping, resource-gated thrust). Companion*
*design: `docs/designs/ability-tech-tree.md`. Link from README under "Design*
*documents."*
