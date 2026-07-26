# Cluster Dispersal: Integration Heating vs. Physics — and the Euler Fling Machine

**Domain:** physics | **Phase:** 0 (planet formation / cluster assembly)
**Date:** 2026-07-23 | **Author:** opencode investigation (probe data from aborted research agent, completed runs base+coarse)
**Status:** validated
**Primary sources:** controlled headless probe runs (`scratchpad/cluster_probe.clj`, `scratchpad/base.edn`, `scratchpad/coarse.edn`), live nREPL probes, Dehnen & Read (2023)[^1], Springel (2005)[^2]

---

## 1. Research Question

Live runs eject 100% of formed planets (0 bound of 12–24 per run; one lucky run kept 4/24). The owner hypothesized the pacing itself is at fault: "everything gets going a little too fast too soon — maybe if the baseline was moving slower, we might get the other stuff fixed too." We ask:

1. Is the cluster's dispersal physical (gas expulsion, low SFE) or numerical (integration heating)?
2. Would finer global `dt` save the planets?
3. Where does the kill actually happen?

## 2. Method

Headless driver (`scratchpad/cluster_probe.clj`): `genesis/create-world` pipeline with a rebindable nebula seed, ticked 12,000 steps, sampling every 100 ticks: `:sim/dt`, body counts, bound-planet count (two-body energy < 0 vs nearest star), stellar-subset K/U/E, virial ratio, min star–star separation. Two completed runs (the agent stalled starting the fine-dt leg; analysis completed post-hoc):

- **base** — production pacing (dt observed 869 → 97 yr/tick as complexity grows).
- **coarse** — `PROBE_DT_MAX=1.0e11` (dt observed 194–621 yr/tick ≈ 2× baseline), the heating-hypothesis control: if heating is real, the coarser run must heat faster.

## 3. Results

### 3.1 Stellar-subset energy grows monotonically — and faster when coarser

| sim-time | base E (4–5 stars) | coarse E (4–5 stars) |
|----------|--------------------|----------------------|
| 1.7e6 yr | 5.2e35 J | 7.7e35 J |
| 2.2e6 yr | 7.2e35 J | 7.9e35 J |
| 3.6e6 yr | 7.6e35 J (6 stars) | 1.01e36 J (9 stars) |

Between star-formation events (constant membership), the baseline gains ~25% per 1800 ticks. At matched sim-time the coarse run sits ~48% hotter. **Energy non-conservation is real and dt-dependent → numerical integration heating confirmed.** Source: star–star encounters at 10–100 AU periapsis (timescales ~yr) integrated on the raw Euler path at 100–600 yr/tick — stars never sub-step (`stellar-parent-states` only parents planets). The virial ratio 2K/|U| ≈ 200–800 throughout: the cluster is violently unbound in both runs, so heating rides on top of a genuinely unbound system (low effective SFE — physical dispersal is also real).

### 3.2 Planets are BORN unbound — thousands of AU from any star

Every FORMED event in the ejection log shows the planet already unbound at first sample (≤100 ticks after spawn), at **3,474–71,440 AU** from the nearest stellar body. At spawn speeds (≤ tens of km/s) no planet can travel that far in 100 ticks — so they are *spawned* there: card 2's "physical disk radius" is `0.3–0.5 × r-disk`, and the live `r-disk` at formation time is clump-scale (10³–10⁵ AU from the collapse-fed angular momentum budget), not the protostellar 2–100 AU the design assumed. (Current-epoch disks measured live are sane — star 491: r-disk ≈ 23 AU — but that is after the disk is exhausted.)

### 3.3 The Euler fallthrough is a fling machine

When the 100× dominance gate fails (always, in the embedded phase — clump tide ~2000× the host's pull at 5 AU/500 AU clump geometry), the body takes one raw symplectic-Euler step at dt ≈ 100–600 yr. Any tidal acceleration `a` then injects `a·dt ~ 10⁹ m/s` of Δv **in a single tick**. A gate-fail is not a degraded orbit — it is an instant ejection. The sub-stepper (WH, landed) only protects bodies whose gate *passes*; everyone else is one unlucky tick from interstellar space.

### 3.4 The softening feedback loop

ε = 0.05·R(90% mass) → as heating accelerates dispersal, R₉₀ inflates → ε pins at its 5e14 m ceiling (3342 AU) → the 334-AU gravitational dead-zone → compact gravity dies → orbits decohere → more dispersal. The loop is now mostly closed by pair-softening (landed: compact pairs use ε ≈ physical radius), but the R₉₀ inflation itself continues while heating persists.

## 4. Verdicts

**Q1:** Both, layered. Physical dispersal (low SFE, unbound from birth — virial ratio ≫ 1 from t=600) PLUS dt-dependent numerical heating (+48% energy at 2× dt at matched sim-time). The owner's instinct is confirmed in direction — coarser dt genuinely heats the cluster faster — but pacing is the amplifier, not the killer.

**Q2:** No. Finer global dt cannot save planets that are *born* unbound at kAU and flung in a single Euler tick when the dominance gate fails. Pacing reform is a tuning knob, not the fix.

**Q3:** The kill chain is: (a) spawn at clump-scale r-disk into tide-dominated territory → (b) gate fails → (c) one Euler tick at 100-yr dt = instant fling → (d) hyperbolic forever (the sub-stepper then faithfully maintains the hyperbola). Fixing any ONE of a/b/c changes the outcome; fixing (c) is the highest-leverage because it also protects every future embedded-phase body.

## 5. Recommendations (ranked)

1. **Universal sub-stepped integration for compact bodies (kills the fling machine).** When the dominance gate passes: WH-Kepler split (today). When it fails: K sub-steps of the *total* force (frozen per tick, same K criterion) instead of one raw Euler step. GADGET's block-step shape; no WH validity assumption needed; raw-leapfrog secular drift is irrelevant for bodies whose dynamics are tide-dominated anyway. See `docs/designs/multi-timescale-integration.md` amendment.
2. **Formation placement v2:** gate fragmentation on a sane disk scale (spawn only when r-disk ≲ 100 AU, or clamp placement to the Hill-stable region of the host), and investigate why disk identification matches clump-scale rotation rather than a compact protostellar disk.
3. **Star sub-stepping (kills the heating at the source):** extend the WH path to star–star encounters (parent = dominant other star; protostars included). The cluster's stellar dynamics becomes exact regardless of dt; dispersal slows, R₉₀/ε feedback weakens.
4. **Pacing (owner's suggestion, as a knob not a fix):** steeper `complexity-dt-cap` falloff (e.g. linear instead of √) or lower `cfl-factor` during the compact phase. Cheap to try after 1–3; expect it to slow the heating rate, not change the outcome.

## 6. Test contracts (for the design's implementation cards)

- **Fling-machine regression:** a compact body embedded in a fixed tidal field that fails the dominance gate must NOT gain more than a bounded Δv per tick (no `a·dt` fling); its energy after 10³ ticks stays within a physical envelope of its initial state.
- **Heating regression:** an isolated 2-star + 1-planet cluster at live dt: total energy bounded (no monotonic growth) once star sub-stepping lands.
- **Placement:** no fragment spawns with r-orbit > 100 AU (or the Hill-stable bound), asserted across seeded worlds.
- **Survival integration test:** a seeded nebula run to planet-formation yields ≥1 bound planet with e < 0.4 (the candidate-emergence north star).

## Sources

[^1]: Dehnen, W. & Read, J. (2023). *Switching Integrators Reversibly in the Astrophysical N-Body Problem*. arXiv:2301.06253.
[^2]: Springel, V. (2005). *The Cosmological Simulation Code GADGET-2* (block power-of-two individual timesteps with full-force integration). MNRAS 364, 1105.

## Cross-references

- `docs/research/physics/multi-timescale-integration-jacobi-ecs.md` — the WH sub-stepping research (the gate-passing half of the fix).
- `docs/designs/multi-timescale-integration.md` — design to amend (universal sub-stepping).
- `kanban/tasks/planet-orbit-circularization-blocker.md` — the parent blocker; its "formation-survival era" section.
- `kanban/tasks/compact-pair-softening.md` — landed; closes the ε arm of the feedback loop.
- `scratchpad/cluster_probe.clj`, `scratchpad/probe_analyze.clj`, `scratchpad/{base,coarse}.edn` — probe code + raw data.
