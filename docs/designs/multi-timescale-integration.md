# Multi-Timescale Integration — resolving fast orbits under a slow global clock

**Path:** `docs/designs/multi-timescale-integration.md`
**Status:** Design — approved direction (owner chose "design first" 2026-07-23).
Implements the fix for `kanban/tasks/planet-orbit-circularization-blocker.md`,
the upstream blocker beneath the whole spark-flight / voxel-progression roadmap.
**Research basis:** Wisdom-Holman/MVS splitting (Wisdom & Holman 1991; REBOUND
WHFast), block/individual timesteps (Aarseth, Makino, GADGET-2), symplecticity
under adaptive dt (Dehnen & Read 2023; Rein 2024). Report:
`docs/research/physics/multi-timescale-integration-jacobi-ecs.md`.
**Amended 2026-07-23** after review: added §3.0 (relative-coordinate formulation —
required, not optional), raised the K ceiling (§3.3), specified dv/absorb/foff
composition (§3.4), and recorded the close-encounter limitation (§4 note).

---

## 1. The problem

The global `dt` is paced to the **bulk cloud's** dynamical time (`domain.pacing`:
`dt = min(cfl·t_dyn(90%-mass radius), complexity-cap)`, clamped to
[~0.32 yr, ~1585 yr]; live ≈ 80 yr/tick). The integrator
(`domain.integrator.kinematics/kinematics-ws`) is **symplectic (semi-implicit)
Euler**: `v' = v + a·dt; x' = x + v'·dt`, with `a` one-tick Jacobi-stale.

That `dt` is orders of magnitude too coarse for a planet's own orbital period
(years). A body sampled at 2–3 points per orbit doesn't merely lose precision —
symplectic Euler with `dt ≫ T_orb` generates spurious energy/eccentricity growth
tick over tick. **Proven live** (see the blocker card): all 36 bound planets sit
at eccentricity 0.9999999–1.0; specific angular momentum is 15×–1000× below
circular. No world clears the `e < 0.4` handoff gate → zero `planet-candidate`s →
the binding→commitment→voxel pipeline can never fire.

This is the recurring dt-dilation bug class (`.agents/skills/physics-dt-unit-mismatch/`):
a per-tick process whose correctness silently depends on `dt`.

## 2. Why the obvious fixes fail

- **Shrink the global `dt`** (cap by shortest orbital period): a 1-AU orbit needs
  `dt ≤ ~7 days` — ~5 orders of magnitude below the current ceiling; a close-in
  planet needs hours. Applied globally this freezes bulk-cloud collapse to a crawl
  for the rest of the sim. A single global `dt` cannot serve both regimes.
- **Reuse the LOD tick-phase machinery** (`domain.lod`, `c/lod-level`/
  `c/lod-tick-phase`): dead end. It's (a) *inert* in production — its gate
  `:lod/throttle-ticks?` is never set true outside tests — and (b) *backwards* —
  it throttles distant bodies **down** (period ≥ 1, skip ticks), whereas we need
  fast nearby orbits resolved **finer** (sub-tick). Tick-skipping can only make a
  body coarser than the tick; it cannot make it finer. Reusable later as a
  performance layer (§5), not as the correctness fix.
- **Sub-step with the frozen *total* force**: fails here. When `dt` spans dozens
  of orbits, the gravitational force *direction* rotates through the orbit; holding
  the full acceleration constant across K sub-steps is wrong. The fix must advance
  the dominant central term with live geometry — see §3.

## 3. The scheme — Wisdom-Holman sub-stepping inside the integrator

### 3.0 Coordinates: relative (Jacobian) formulation — REQUIRED, not optional

The sub-step loop must integrate the body's state **relative to its parent**,
never its inertial position about a frozen parent point. Live numbers make this
fatal if skipped: `dt ≈ 80 yr/tick` and measured stellar velocities of
47–1180 m/s mean a parent star moves **5–20 AU per tick**. Kepler-advancing the
planet's world-frame position about the star's *frozen* position while the star
itself advances injects a `−V_star·dt` error of 5–20 AU into the relative orbit
**every tick** — the same order as the entire 1–30 AU orbit the placement arm
(§3.3) targets. That is the ejection bug re-born in a new frame.

The formulation (WHFast's democratic-heliocentric / Jacobian coordinates):

1. At tick entry, from the frozen snapshot, form the relative state
   `r = x_body − x_parent`, `u = v_body − v_parent`.
2. Advance the parent's *own* position/velocity for the tick with the existing
   symplectic-Euler step (stars don't sub-step — their cluster orbits are
   ~10⁵-yr periods, well-resolved at the global `dt`). `kinematics-ws` is the
   sole writer for *all* bodies, so computing the parent's advance first and
   then the sub-stepped bodies' is internal ordering inside one `:run` — legal.
3. Run the K-sub-step WH loop **on the relative state**: the two-body problem in
   relative coordinates is exact (`r̈ = −μ·r̂/r²`, μ = G·(M_parent + m_body)),
   so `drift_Kep` needs no frozen anchor point at all.
4. The perturbation kick on the relative state is the **tidal** acceleration
   `a_pert,body − a_pert,parent` (the parent absorbs its own perturbing kick in
   step 2; applying the raw body perturbation without subtracting the parent's
   double-counts it in the relative frame).
5. Final world-frame writes: `x_body' = x_parent' + r'`,
   `v_body' = v_parent' + u'`. One `{position, velocity}` write per body, as
   today.

Galilean invariance means the parent's *uniform* motion per tick is harmless;
only its *acceleration* during the tick perturbs the relative orbit — and that
is slow (cluster field), so freezing the tidal perturbation across the K
sub-steps carries the same `O(dt/τ_slow)` bound as §3.

### 3.1 The split

Split each compact body's Hamiltonian `H = H_Kep + H_pert` (in the relative
coordinates of §3.0):

- **`H_Kep`** — Keplerian motion of the relative coordinate about the body's
  dominant nearby mass (its parent star, μ = G·(M_parent + m_body)). Advanced
  *analytically* per sub-step via a closed-form Kepler propagation (solve the
  Kepler equation / f-g functions). **Zero discretization error at any step
  size** — this is why we don't need K to be large or exactly right.
- **`H_pert`** — everything else, as the tidal kick `a_pert,body − a_pert,parent`
  (other bodies, gas/pressure, dark-matter halo, observer, warp). Small relative
  to the parent pull; held **frozen** across the tick's K sub-steps and applied
  as a kick.

Symmetric (2nd-order, symplectic) composition per sub-step of size `h = dt/K`:

```
drift_Kep(h/2)  →  kick_pert(h)  →  drift_Kep(h/2)
```

where `drift_Kep` is the analytic Kepler advance of the relative coordinate, and
`kick_pert` uses the frozen tidal perturbation (`a_pert,body − a_pert,parent`).
Iterate K times, then compose with the parent's own advance (§3.0 step 5) and
emit one final `{position, velocity}` write for the body this tick.

**Slow→fast coupling** is a 1st-order Lie-Trotter split: the perturbing field is
held fixed for the tick. Its error is `O(dt_tick / τ_slow)` — small precisely
because `dt_tick` is dilated to the slow (gas) timescale, i.e. the very regime that
makes the global clock coarse also makes freezing the slow field accurate.

### 3.2 Where it lives (ECS fit)

Entirely **inside `domain.integrator.kinematics/kinematics-ws[-soa]`'s `:run`**
(`src/domain/integrator/kinematics.clj:65-96,147-183`) — the sole writer of
`c/position`/`c/velocity`. For each compact body (gate on `c/body-kind` ∈
star/planet, or presence of a resolvable parent), run the K-sub-step WH loop in
pure Clojure reading only the frozen snapshot, and return the same single
`{position, velocity}` write-set as today. This satisfies every hard constraint
(architecture map §(a)):

- One writer per component type — unchanged; no new position/velocity writer.
- One frozen snapshot per tick — the K sub-steps read only frozen parent/perturber
  state; the existing one-tick Jacobi force lag is simply applied K times inside
  one tick, introducing no new lag class.
- No new serial tier — it's internal to one fan-out emitter; invisible to the fold.
- Parallel-safe — `par-mapv` already parallelizes across entities; the K-loop is
  entity-local.

Gas, hydro, MHD, chemistry keep the global `dt` untouched — only compact bodies
sub-step, and only for their own kinematic advance.

### 3.3 Choosing K (and the one rule that matters most)

Per body, once per tick, from the **frozen** snapshot:

```
K_i        = clamp(ceil(dt_tick / dt_local_i), 1, 4096)
dt_local_i = min(f_orb · T_orb_i,  sqrt(2·η·ε / |a_i|))
```

`T_orb_i` = local Kepler period about the dominant parent; `f_orb ≈ 1/20…1/50`
(steps per orbit); the acceleration criterion (`η ≈ 0.03`, softening `ε`) is the
close-encounter/high-e safety net.

**Ceiling sizing:** the clamp must cover the placement floor (§3.5). At 1 AU
around 1 M☉ with `dt = 80 yr` and `f_orb = 1/20`, `K = 1600`; the old 512
ceiling would silently under-resolve the kick cadence 3× at exactly the radii
the placement arm targets. 4096 covers down to ~0.3 AU. **If the clamp binds,
`log` it** (entity id, demanded K, clamped K) — silent truncation reads as
"handled everything" when it wasn't (house rule; the Kepler drift itself stays
exact at any K, so a binding clamp degrades only perturbation fidelity, never
orbit shape).

**THE RULE:** compute `K_i` and the sub-step size **once, at tick entry, from the
already-one-tick-lagged snapshot — never recompute from state produced within this
tick's sub-step loop.** A step-size that depends on the phase-space state being
integrated destroys symplecticity/reversibility and produces exactly the secular
eccentricity drift we're fixing (Dehnen & Read 2023). Freeze it; don't adapt
mid-loop.

### 3.4 Composition with the existing write channels

The K-loop replaces only the *integration* of compact bodies; the other things
`kinematics-cell` does must compose with it explicitly:

- **`dv.*` influence channels** (mass-transfer recoil, etc.) and **absorb/merge
  COM blending** are impulsive, once-per-tick events. Apply them as **one outer
  kick/blend after the K-loop completes**, on the composed world-frame state —
  never inside the sub-step loop (they are not smooth forces; injecting them
  mid-loop would corrupt the Kepler arc and re-introduce phase-space-dependent
  stepping through the packet mass terms).
- **`foff` (COM frame-offset)** remains a pure Galilean shift: subtract it from
  the final composed world-frame position exactly as today
  (`x_body' = x_parent' + r' − foff`). It applies uniformly to parent and body,
  so it cancels in the relative coordinate and does not touch the K-loop.
- **Bodies with no resolvable parent** (unbound, or parent lookup fails): fall
  through to the existing symplectic-Euler path unchanged. No special third
  path.

### 3.5 The placement arm (needed for candidates to actually appear)

The blocker has a second arm: `disc/resolvable-orbit-radius` (`disc.clj:156-167`)
places every spawned fragment at the smallest radius whose period spans
`min-fragment-orbit-periods` (=50) steps **at the global `dt`** — ~162 AU live.
That's why planets sit at 34–310 AU. The orbit-stability gate also caps apoapsis
at `max-apoapsis-au` = 100 AU, so even perfectly circular planets beyond 100 AU
would still fail.

Once compact bodies sub-step (§3), placement no longer needs the coarse global
`dt` as its resolution basis: **decouple `resolvable-orbit-radius` from the bulk
`dt`** and place fragments at their physical disk radius (≈1–30 AU), relying on
sub-stepping to resolve them. This brings planets inside the apoapsis ceiling and
onto physically sensible orbits. (Integration fix alone may already yield the few
bodies with a < 100 AU; the placement fix makes candidates the norm, not the
exception.)

**The velocity pairing rule (added after the 2026-07-23 live regression):**
placement radius is only half of spawn consistency — the spawn *velocity* must
pair with the integrator path the spawned body will take. Sub-stepped compact
bodies (`:planet`/`:gas-giant`/`:stellar-remnant`) advance under the exact
Newtonian drift, so they must spawn at `law/newtonian-circular-speed`; Euler-path
bodies (gas, `:protostar` companions) stay on `law/softened-circular-speed`.
Verified live: a `:gas-giant` spawned at softened-circular speed (≈0.17 m/s at
2.2 AU under ε = 3342 AU) is read by the Newtonian drift as a radial plunge —
e ≈ 1 within ticks, exactly the blocker symptom reborn at the spawn seam.
Pinned by `spawn-velocity-pairs-with-substepper` in the card-3 suite.

## 4. Symplecticity / correctness rules (summary)

1. Analytic Kepler for the dominant term — correctness independent of K.
2. Freeze K and sub-step size at tick entry; never phase-space-adaptive mid-loop.
3. Any inter-tick rung/step change must be **block-synchronized** (only at a
   completed sub-step cycle), and gated by a time-symmetric acceptance test if ever
   made adaptive (Dehnen & Read; Rein 2024).
4. Perturbation freezing is a bounded Lie-Trotter error only while
   `dt_tick ≪ τ_slow` — assert this holds.
5. **Known limitation — close encounters:** the WH split degrades when a
   non-parent body's pull on the planet becomes comparable to the parent's
   (crossing orbits, scattering — common in the formation era). The production
   remedy is hybrid switching (MERCURY/TRACE: flip to direct high-order
   integration for the duration of an encounter). Not in this epic's scope;
   the acceleration criterion in `dt_local` (§3.3) bounds the damage for now.
   Track as a future card if candidate systems show scattering artifacts.

## 5. Performance layer (later, optional)

Correctness (§3) is independent of performance. As a later optimization, layer
GADGET-style **power-of-two rungs** and a KSP-style **on-rails analytic Kepler**
fallback for far/quiescent bodies — this is where the existing `domain.lod`
machinery can be *inverted and revived* (rung selection fed by both dynamical-time
`K_i` and distance/quiescence from the observer focus). Rung transitions
block-synchronized only. Not required to unblock the game; do it if the sub-step
cost of many close bodies becomes a wall-clock problem.

## 6. Secondary fixes (independently real, low-risk — do alongside)

- `domain.stellar.classifier/central-star` (`classifier.clj:411-434`) picks the
  world's most-massive star, not the nearest — wrong dominant attractor in a
  5-star system. Fix to nearest bound star per body (also what §3 needs to pick
  each body's Kepler parent). **(Folded into card 1 as `nearest-stellar-parent`;
  card 4 now covers only the classifier/gate callers.)**
- `domain.orbital.stability` two-body-elements (`stability.clj:49-52,99-103`)
  assumes `r ≫ softening`, now false (ε ≫ r by 10–100×). **Reframed after the
  velocity pairing rule:** for SUB-STEPPED compact bodies the gate's unsoftened
  elements are now *correct* — the sub-stepper's drift makes the Newtonian
  two-body law the one those bodies actually live under. The softened-potential
  fix applies only to Euler-path bodies (gas, protostars). Card 5 rescoped
  accordingly.
- **Do NOT** just retune the DM halo to hold the dispersing system together. The
  halo being too weak to retain 47–1180 m/s stars (escape ≈325 m/s) drives the
  system to million-AU scales and inflates softening to its ceiling — a real
  concern, but a *separate* one; the sub-step fix restores orbit coherence
  regardless of dispersal. Track halo/dispersal as its own investigation. **A
  second-order effect of the same inflation is now carded: at ε = 3342 AU,
  planet–planet (and planet–gas) gravitational interaction is effectively zero —
  no scattering, no dynamical friction, no compact accretion between resolved
  bodies. See the per-pair-softening card.**

## 7. Test plan (windowed-equivalence, per physics-dt-unit-mismatch skill)

- **(e, a) bounded:** isolated two-body Kepler pair at realistic late-sim
  `dt`/`softening`, run through `domain.ecs.tick/run-parallel` for 10⁴–10⁶ ticks
  at several K — eccentricity and semi-major axis stay flat/bounded, not drifting
  to 1. (Directly reproduces the bug; the regression that would have caught it.)
- **Energy bounded:** the same pair — energy oscillates within a bound, no
  monotonic growth (canonical symplectic regression).
- **Reversibility:** integrate N ticks, reverse velocities, integrate back —
  return-to-start error small; verify across any rung transition.
- **Stale-slow-field budget:** confirm the Lie-Trotter error from freezing the
  perturbing field stays within budget as `τ_slow / dt_tick` shrinks.
- **Candidate emergence (integration test):** a scripted nebula→star→planets run
  yields ≥1 `c/planet-candidate` under normal simulation (the player-visible
  north star: a world can now resolve).

## 8. Roadmap — the `multi-timescale` epic (prerequisite to the voxel loop)

1. `integrator-kepler-substep` — WH/Kepler-split K-sub-step inside `kinematics-ws`;
   K frozen at tick entry; analytic Kepler about the dominant parent; perturbations
   frozen. **The correctness fix.** (P1)
2. `fragment-placement-decouple-dt` — place disk fragments at physical disk radius,
   decoupled from the bulk `dt`, so candidates land inside the apoapsis gate. (P1)
3. `orbit-integration-regression-tests` — the §7 suite (build alongside 1). (P1)
4. `central-star-nearest-attractor` — nearest bound star as dominant attractor;
   fixes `classifier/central-star` and feeds the Kepler split. (P2)
5. `stability-softened-elements` — softened two-body-elements in the gate. (P2)
6. `lod-rung-onrails-optimization` — performance rungs + on-rails Kepler; revive/
   invert `domain.lod`. (P3, only if needed)

**Dependency graph:** 4 unblocks the parent-attractor choice 1 needs (or fold a
minimal nearest-star lookup into 1); 1 + 2 together produce candidates; 3 gates
merge of 1; 5 independent; 6 last. Once 1–3 land, the spark-flight Wave-0 payoff
(`focus-follows-pilot` + `voxel-sculpt-verb-palette-wiring`) finally has worlds to
resolve.

---

## 9. Universal compact sub-stepping — killing the fling machine
**(added 2026-07-23, post-live-investigation; research:
`docs/research/physics/cluster-dispersal-integration-heating.md`)**

Live evidence: planets are *born* unbound at kAU spawn radii and ejected in
**one tick** when the dominance gate fails — the raw Euler fallthrough applies
`a·dt ~ 10⁹ m/s` of Δv at dt ≈ 100–600 yr. Any compact body in a tidal field
is one gate-fail away from interstellar space.

**The rule:** a compact body NEVER takes a single raw Euler step at the
global dt. The sub-step machinery becomes universal, with two integrands
chosen per body per tick from the frozen snapshot:

- **Gate passes** (parent-dominated): the WH-Kepler split of §3 — exact
  two-body drift + frozen tidal kick. Unchanged.
- **Gate fails** (embedded / scattering): **K sub-steps of the TOTAL force**
  (the same frozen `Σ accel.*` the Euler path uses today), `h = dt/K`, same
  frozen-at-tick-entry K criterion (§3.3). This is GADGET's block-step shape:
  no Kepler-split validity assumption is needed, and the raw-leapfrog secular
  drift the research warns about is irrelevant — a tide-dominated body has no
  Kepler orbit to protect; what it needs is the *tide resolved*, which K
  sub-steps deliver. The K loop stays entity-local inside `kinematics-ws`'s
  one `:run` (parallel-safe, single-writer, invisible to the barrier).

Second arm (same investigation): **star sub-stepping** — star–star close
encounters at 100–600 yr/tick measurably heat the cluster (+48% energy at 2×
dt, matched sim-time). Stars join the sub-stepped population with parent =
dominant OTHER star; protostars included. This is what actually slows the
dispersal → R₉₀ → ε feedback loop.

**What this is NOT:** a pacing change. The owner's finer-dt intuition
measures true (+48% heating at 2× coarseness) but pacing is the amplifier,
not the killer — see the notebook. A steeper `complexity-dt-cap` falloff is
kept as a tuning knob after 1–3 land, not instead.

---

*Companion designs:*
*`docs/designs/spark-flight-and-camera.md`, `docs/designs/ability-tech-tree.md`.*
*Blocker: `kanban/tasks/planet-orbit-circularization-blocker.md`.*
