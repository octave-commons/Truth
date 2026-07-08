# Design: Planetary Disk Rework — the disk becomes matter

**Status:** design (decided 2026-07-07)
**Owner:** Claude Code
**Relates to:** `kanban/tasks/radial-disk-structure-spec-deferred-capability-d1.md` (deferred — this
supersedes/realizes its intent), `kanban/tasks/seed-and-grow-condensation.md`,
`kanban/tasks/protoplanetary-disk-and-planet-formation-spec.md`,
`kanban/tasks/law-planet-formation-namespace.md`,
`docs/designs/resolution-regimes-and-scale-coupling.md`,
`docs/designs/ux-architecture.md`.

---

## 1. The one root cause

Four separate symptoms all trace to a single design decision: **the disk is a
scalar, not matter.** Today the "disk" is one number (`c/disk-mass`, kg) plus an
angular-momentum vector (`c/disk-angular-mom`), stored on the star. Everything
wrong follows from that:

| symptom | cause |
|---|---|
| "no rotation, just grows and shrinks" | a scalar can only change magnitude — there is nothing orbiting to rotate |
| "renders with an offset / dislike the particles" | the renderer *fabricates* 600 point-sprites from the scalar and rotates them about the world origin (offset bug) |
| "400-Earth-mass planet forms instantly" | `planet-formation/planet-seeds` assigns `0.3 × disk-mass / 12` per planet — a mass fraction, with **no isolation-mass cap** |
| "no planetesimals/asteroids; they'd never collide" | `condensation-seeder-system` makes real 10 km planetesimals, but growth is gated on literal sphere-overlap over 31 kyr steps at AU distances — never happens |

This is not four fixes. It is **one reframe with three phases.**

## 2. The reframe

Orbiting matter already exists: the **disc-tagged gas parcels**. They spin about
z, are gravitationally bound, and `stellar/disc-identification-system` already
labels them `:disc` (vs `:envelope`/`:outflow`). The scalar disk is a *second,
redundant* abstraction layered on top of them — the fake one.

> **The disc-tagged parcels ARE the disk.** Render them as colored volumetric
> cloud (the froxel fog path already tints per-sample). Grow solids by accreting
> from these parcels within a feeding zone, capped at isolation mass. Retire the
> scalar-fiat planet seeder.

Consequences, all free:
- **Rotation is physically real** — we render the parcels that actually orbit.
- **The cloud look is free** — the froxel fog already colors per-sample.
- **Performance improves** — the fog rides a persistent 3D texture updated in
  place; retiring the 600 sprites removes the per-frame VBO pack/alloc/upload/
  delete churn.
- **Single-substrate clean** — this is a content layer over parcels that already
  exist, not a new engine (`[[single-ecs-substrate]]`).

`c/disk-mass` does not vanish entirely — it survives as an *accounting scalar*
for viscous drain onto the star and as the gas budget for runaway giant
accretion. It stops being the render source and stops being the planet-mass
oracle.

## 3. The physics we are honoring (with numbers)

Researched 2026-07-07; citations in §7. The sim can never be "right" (we don't
know the true history of any system), but it can be **physically plausible and
internally consistent**. The staged sequence below is the defensible skeleton.

1. **Dust → planetesimals.** Micron dust cannot cross the meter-size barrier by
   sticking (radial drift wins). The **streaming instability** concentrates
   pebbles and collapses them directly to **~10–100 km planetesimals**. This is
   what `condensation-seeder-system` models (~10¹⁶ kg / ~10 km seeds). **Keep it.**

2. **Planetesimal → protoplanet growth.** Runaway growth (dM/dt ∝ M^(4/3)) then
   **oligarchic growth** (dM/dt ∝ M^(2/3), Kokubo & Ida 1998): a body sweeps up
   solids in a **feeding zone ~8–10 Hill radii wide**. Pebble accretion is
   30–1000× faster and can build 10–20 M⊕ cores in ~1 Myr.

3. **Isolation mass — the cap we are missing.** A body stops growing when it has
   cleared its feeding zone of local solids:

   `M_iso = [4π · B · a² · Σ_solid]^(3/2) / (3 M_star)^(1/2)`   (B ≈ 4)
   ∝ `Σ_solid^(3/2) · a³ · M_star^(−1/2)`

   - **~0.1–0.15 M⊕ at 1 AU** (sub-Mars) — this alone kills the 400 M⊕ terrestrial.
   - **~several–10 M⊕ at 5 AU** (beyond the ice line, where Σ_solid jumps ×3–4) —
     the classic gas-giant core.

4. **Giants are legitimate but conditional.** A core that reaches **pebble
   isolation mass (~20 M⊕, beyond the ice line)** opens a pressure bump, halts
   pebble flux, and triggers **runaway gas accretion**, pulling gas from the whole
   disk to become a ~100–400 M⊕ giant — *only if gas remains* (disk lifetime
   ~1–10 Myr, median ~3 Myr). Inner rocky planets stay ~0.1–1 M⊕ and finish via a
   ~100 Myr **giant-impact phase**.

5. **The ice/snow line** sits where equilibrium T ≈ 150–170 K (~2.7 AU in the
   solar nebula; scales with L). Beyond it solids jump ×3–4. Rocky inside, cores/
   giants outside. (Already coded: `snow-line-radius`, `solid-surface-density`.)

6. **Asteroid belts are survivors, not failures.** A belt is an annulus where a
   nearby giant's mean-motion/secular resonances stir eccentricities so collision
   velocities (~5 km/s) exceed the accretion threshold — bodies **shatter instead
   of merge**, so the annulus never reaches isolation mass. ~99.9% of primordial
   mass is lost; the real belt is ~4×10⁻⁴ M⊕, with Kirkwood gaps at Jupiter's
   3:1 (2.5 AU), 5:2 (2.83 AU), 2:1 (3.28 AU) resonances. Model **statistically**:
   a belt = surviving-planetesimal count + size-frequency power law.

7. **Comets are a separate icy reservoir** (they do NOT form from the local
   planetary disk). Icy planetesimals born beyond the ice line are **scattered by
   the giants** into: the **Kuiper belt / scattered disk (~30–50 AU)** → short-
   period (Jupiter-family) comets; and the **Oort cloud (~2,000–100,000 AU)** →
   long-period comets. Composition: "dirty snowballs" — water ice + CO/CO₂/NH₃/CH₄
   + dust; nuclei ~1–50 km. Model as a **statistical reservoir** (count +
   composition + orbital family), not resolved bodies.

## 4. The three phases

### Phase 1 — Rendering (immediate, low risk)

1. **Fix the offset bug.** In `infra.render/disk-particles` the tilt rotation is
   applied to each particle's *absolute* position about the world origin, giving
   `R·center + R·offset` — the disk slides off the star by `R·center − center`.
   Fix: rotate only the local offset, then re-add center (`center + R·(p−center)`).
   Two call sites (cache-hit-mismatch and cache-miss branches).
2. **Render disc-tagged parcels as colored cloud, but distinguish gas from dust.** Feed disc-tagged parcels into the froxel fog as tinted samples. The renderer now decides "dust" vs "gas" from the parcel's **solid fraction** (`rock + metal + ice` from `domain.chemistry/bulk-categories`), not merely from `:disc-tag`. Dust parcels use the warm `disk-temp-color` ramp (green → orange → brown → red) and get a 5× density boost; gas parcels keep the blue-violet → magenta → orange nebula ramp and receive a weak ionization tint. Stars remain white/blue sparks. A soft tone map in the volume shader (`C = C / (1 + 0.5·C)`) keeps bright regions from clipping to white. Retire the 600 point-sprites (`disk-particles*`) and their per-frame VBO churn.
3. **Rotation now shows for free** — we render the actual orbiting parcels.

*Note:* the live `run-window` path currently does not pass `:volume`, so the fog
only runs via the screenshot path today. Wiring the live window to the fog is
part of this phase (or a documented prerequisite).

### Phase 2 — Formation physics (the heart)

1. **Retire the fiat mass assignment.** Delete `mass-kg (min (* 0.3 ann-mass) …)`
   in `planet-formation/planet-seeds`. Planets are no longer *placed* at a mass;
   they *grow*.
2. **Seed planetesimals first (exists).** Keep `condensation-seeder-system`.
3. **Sub-grid accretional growth (NOT literal collision).** A new system grows a
   body by sweeping the solids in its feeding zone (~10 Hill radii) at the
   oligarchic rate, drawing from local Σ_solid (= Σ_gas·Z, ×3–4 beyond the ice
   line). No two 10 km rocks need be caught overlapping. This is the key fix that
   makes planetesimals viable.
4. **Cap growth at isolation mass** (§3.3). Enforced per body from local Σ_solid
   and orbital radius.
5. **Giant channel** (§3.4): a core past pebble-isolation mass beyond the ice line
   draws down `c/disk-mass` (the surviving gas budget) in runaway accretion.

### Phase 3 — Icy reservoir + statistical census (follow-up)

1. **Comets** (§3.7) as a scattered icy reservoir — statistical, not resolved.
2. **Belts** (§3.6) as surviving-planetesimal populations — statistical.
3. **System census / "zoom to life."** Population-level descriptors (Σ(r),
   size-frequency power laws, N surviving planetesimals per belt, comet-reservoir
   counts). The life-bearing planet and major planets stay **resolved ECS
   entities**; everything else collapses into statistical components. This is the
   architecture that lets the game focus on one world while the rest of the system
   stays coherent and cheap — the natural extension of
   `[[resolution-regimes-and-scale-coupling]]` and `domain.lod`.

Phase 3 is documented here but scheduled after Phases 1–2 land and are verified.

## 5. What gets retired

- The 600 fabricated disk point-sprites (`disk-particles*`) and their per-frame
  VBO churn.
- The scalar disk as the **render source** (kept as an accounting scalar for
  viscous drain + giant gas budget).
- The fiat planet mass `0.3 × disk-mass / 12` (replaced by isolation-mass-capped
  growth).

## 6. Consistency with the house rules

- **Single ECS substrate:** disk = disc-tagged parcels (already in the world) +
  new components/systems. No parallel sim.
- **Double-buffer / single-writer:** the growth system is a fan-out emitter with
  disjoint writes (a `mass-flux-*` channel + growth markers); one writer per
  component type, enforced by `architecture_test`.
- **Time model:** growth is paced per sim-time, scaled by dt (oligarchic rate is a
  real timescale, not per-tick) — `[[tick-coupling-bugs]]`.
- **Softened orbits:** any spawned/growing body's circular speed uses
  `law.stellar/softened-circular-speed` — `[[softened-field-spawn-orbits]]`.
- **z-up:** disk in the xy plane, spin about z — `[[coordinate-convention-z-up]]`.
- **Workflow:** schema in `law/` → failing test → minimal `domain/` impl.

## 7. Sources (researched 2026-07-07)

- Isolation mass / growth: D'Angelo & Lissauer, *Giant Planet Formation* review
  (arXiv:1006.5486); Lissauer 1993; Kokubo & Ida 1998 (arXiv:0709.1454).
- Pebble accretion / isolation: Lambrechts & Johansen 2012 (arXiv:1205.3030);
  Bitsch et al. 2018 pebble isolation mass ≈ 25·f(H/r) M⊕ (aa31931-17).
- Terrestrial timescales / ice line: Kokubo & Ida (Icarus); Jacobson et al. 2011
  PNAS (¹⁸²Hf–¹⁸²W); Hayashi 1981 frost line ~2.7 AU.
- Core accretion: Pollack et al. 1996 (critical core ~10 M⊕); Ribas et al. 2015
  disk lifetimes (aa24846-14).
- Asteroid belt: Wikipedia "Asteroid belt" / "Kirkwood gap"; Morbidelli et al.
  (arXiv:1501.06204); Bottke et al. Asteroids IV; Raymond et al. (arXiv:2012.07932).
- Comets: Kuiper belt / scattered disk / Oort cloud reservoirs; dirty-snowball
  composition (water + CO/CO₂/NH₃/CH₄ + dust).

## 8. Non-goals / rejected

- Keeping the scalar disk and faking clouds + rotation in the renderer (the
  "doesn't feel physical" problem persists — rejected in favor of real matter).
- Literal per-planetesimal collisional growth (numerically dead at AU scale over
  31 kyr steps — replaced by sub-grid feeding-zone accretion).
- A parallel disk simulation (violates single-substrate).
- Global parcel refinement to resolve individual asteroids (compute-dead — belts
  are statistical, per `[[resolution-regimes-and-scale-coupling]]`).
</content>
</invoke>
