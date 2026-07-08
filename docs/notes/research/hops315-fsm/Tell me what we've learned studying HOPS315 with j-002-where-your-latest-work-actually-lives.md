---

# @GitHub [https://github.com/octave-commons/Truth](https://github.com/octave-commons/Truth) read it

I’ve cloned and read through the Truth repo locally and have its structure and docs available now, including `AGENTS.md`, `receipts.edn`, and the existing design notes you captured in earlier research docs.[^4_1]

What would you like to do next with it—focus on the new SPH/parcel → planetesimal code, wire up `domain/accretion` against the ECS primitives, or start by updating `docs/research` to ground the current model in HOPS‑315 and planet‑formation literature?

<div align="center">⁂</div>

[^4_1]: 02ca0590-0163-420d-a549-97702c7aae03.md


---

# Yea find the code I was just talking about, it's the latest work. It's slow... 1.5k small bodies that are just in gravity shouldn't be an issue... and it isn't using all my threads at all, it never has. Get these things to actually collide, and get us a star. And get me real parallelization gains. I have a bigger computer to run a more high resolution sim with smaller parcels, more bodies, etc. But it won't do me any good if it can't saturate this computer.

You already have the “small bodies precipitating out of parcels” implemented in Truth, and the pipeline for gravity, collision, and star formation is mostly there. The main issues are: (1) your small bodies are spawned conservatively and won’t collide much unless you tune radii, timestep, and velocities, and (2) your parallelization is correct but not yet aggressive enough to fully saturate a big machine.

I’ll walk through where your latest work lives in the code, then outline concrete changes to get more collisions, get a star, and get real parallel gains.

***

## Where your latest work actually lives

### Condensation → small-body seeding

The mechanism you were describing is the **condensation seeder** in `domain.stellar` plus the seed mass definition in `domain.planet-formation`:

- `condensation-seed-mass-kg` in `domain.planet-formation` is set to $10^{16}\,\mathrm{kg}$, explicitly chosen to be in the upper end of 100‑km planetesimal masses and well below a single gas parcel ($\sim 4\times 10^{27}\,\mathrm{kg}$). That’s your “small bodies precipitating out of parcels” physical scale.
- `condensation-seed-mass` reads an override from `:genesis/condensation-seed-mass-kg` and otherwise uses that fixed $10^{16}\,\mathrm{kg}$. This is the mass debited from the parent parcel on each seed event.
- `condensation-seeder-system` in `domain.stellar`:
    - Looks at nebula parcels (`matter-state :nebula`) whose next state from `classify-next-state` would be `:planetesimal`.
    - Filters to **local density maxima** (using `spatial/query-within-radius` and comparing parcel density to neighbours).
    - Limits seeds per tick via `:genesis/max-condensation-seeds-per-tick`.
    - For each selected parcel, builds a `spec` with `:matter-state :planetesimal`, `:body-kind :body/rocky`, position offset outside the parent’s radius, and radius computed from the seed mass and `debris-material-density`.
    - Emits:
        - `c/spawn-request-condense` with that spec (so `materialize-lifecycle` will spawn the body next tick).
        - `c/mass-flux-condense` debiting seed mass from the parcel.
        - `c/condensation-seeded` to prevent reseeding from the same parcel.

This is exactly the seed‑and‑grow architecture you were describing for planetesimals: the parcel stays gas (`:nebula`), but small condensed bodies peel off and become resolved sinks. That’s also how modern disk models treat dust → planetesimal conversion statistically: gas carries dust and condensation, and when conditions are right, a fraction of solids convert to discrete planetesimals.[^5_1][^5_2][^5_3]

### Gravity, collision, and integration

On the dynamics side:

- **Gravity**: `domain.gravity.barnes-hut` builds a 3D Barnes–Hut octree and computes accelerations either from body maps or directly from a SoA physics cache. The SoA path (`acceleration-for-soa`) uses `par-mapv` to parallelize per‑body accelerations across cores.
- **Gravity emitter**: `orbital/gravity-acceleration` is the write‑set system that:
    - Reads `:genesis/physics-soa` if present, else `:genesis/spatial-tree` plus body maps.
    - Uses `acceleration-for-soa` or `bh/acceleration` to compute `c/accel-gravity` for every body, in parallel via `par/par-mapv`.
- **Motion integration**: `motion-integration` is the `{:id :motion}` write‑set system that:
    - Sums all acceleration contributors (`c/accel-gravity`, `c/accel-pressure`, `c/accel-lorentz`, etc.).
    - Advances velocity and position in a symplectic step (`v' = v + a·dt`, `x' = x + v'·dt`).
    - Is the **sole writer** of `c/position` and `c/velocity` in the Jacobi fan‑out.
- **Collision detection**: `domain.physics.collision`:
    - Projects the world into `[eid position radius velocity mass]` for all **non‑nebula** bodies — anything with a real radius is collidable.
    - Reuses the shared Barnes–Hut spatial tree (`:genesis/spatial-tree`) and walks it to find literal sphere overlaps: `dist ≤ radiusA + radiusB`.
    - Dispatches a `:event/collision` per overlapping pair; handlers (e.g. stellar merge) emit `c/absorb-merge`, `c/consumed-merge`, and `c/spawn-request-shatter`.
    - The integrator folds those absorb/merge packets into mass, position, velocity, and angular momentum next tick.
- **Tick and parallelization**: `domain.ecs.tick` and `domain.genesis` wire this into a fully parallel fan‑out:
    - `tick-world`:

1. Advances logical tick and builds the spatial index (`spatial/spatial-index`), including `:genesis/spatial-tree` and neighbor cache.
2. Calls `step-physics`, which:
            - Builds or rebuilds the neighbor cache in a future.
            - Builds the physics SoA (`pcache/build-physics-soa`) over the frozen world.
            - Runs **all physics systems** (gravity, hydro, EM, chemistry, classifier, condensation-seeder, collision, integrator, etc.) concurrently via `tick/run-parallel`.
            - Strips transient caches afterwards.
3. Applies lifecycle: spawns all bodies requested via `spawn-request.*` components (including your condensation seeds), then despawns everything marked `consumed.*`.

So your 1.5k planetesimals already live in a pipeline that *should* compute gravity, detect contact collisions, and update motion in parallel each tick.

***

## Why 1.5k bodies feels slow and threads look idle

A few things are likely contributing:

1. **Parallelization thresholds are tuned for hundreds, not tens of thousands, of bodies.**
`par-mapv` uses `parallel-threshold = 64` and `min-chunk-size = 32`. That means:
    - For $n < 64$, it runs sequentially.
    - For $n ≥ 64$, it chooses `chunks = min(n-cores, n / 32)` and partitions the work accordingly.
With ~1500 bodies and, say, 16 cores, you get only ~16 futures, each handling ~90 bodies. That’s fine for 16 cores, but if your machine has 32–64 cores, you’ll never get more than `n / 32` tasks — the code intentionally avoids spawning more tiny chunks.
2. **The heavy pre‑fan‑out work is partly serialized.**
`step-physics` builds the SoA and spatial index before the fan‑out; only neighbor-cache rebuild is done in a future. On a big machine, this means one core does SoA building and tree construction while others idle until `tick/run-parallel` starts. With 1500 bodies, the Barnes–Hut build is $O(N\log N)$ and can be noticeable.
3. **`run-parallel` uses one future per system, not per entity.**
In `tick/run-parallel`, each physics system (`gravity`, `motion`, `collision`, `stellar/classifier`, `condensation-seeder`, etc.) gets its own future. Within each system, per‑entity work is often parallelized (e.g. `gravity` uses `par-mapv`, hydro uses `par-mapv`), but some systems are relatively light. So you see a few cores heavily loaded (gravity, SoA, neighbor-cache) and others doing small jobs.
4. **Your timestep and softening/cutoff might be large.**
In `physics-systems-parallel` the gravity system is constructed as:

```clojure
(orbital/gravity-acceleration
  G theta
  (or softening 1e14)
  (or cutoff (* 0.1 (or softening 1e14))))
```

If `softening` or `cutoff` are big, close pairs contribute zero acceleration and small bodies won’t gravitationally focus into collisions; they’ll just drift. That makes the dynamics look “quiet” even when the math is running.[^5_2][^5_3]

***

## Getting real collisions between the small bodies

Given the current architecture, collisions should already happen when spheres overlap. If you’re not seeing many, it’s likely because the planetesimals are:

- Small in radius;
- Nearly co‑planar and low‑eccentricity;
- And not gravitationally focused due to strong softening or dead‑zone cutoff.

To make your 1.5k bodies **actually collide**:

1. **Check and tune seed radii.**
    - Seed radius is computed via `sphere-radius` from `seed-mass` and `debris-material-density`. With $10^{16}\,\mathrm{kg}$ and a rocky density, the radius is tiny compared to orbital scales.
    - For simulation purposes, you can temporarily use an “interaction radius” larger than physical — e.g. multiply `seed-r` by a factor — to make contact collisions more frequent while keeping mass correct.
    - Alternatively, keep physical radius but accept that collisions will be rare unless velocities are stirred.
2. **Lower softening and cutoff for the planetesimal regime.**
    - Set `softening` to a scale comparable to the **inter‑body spacing**, not orders of magnitude larger.
    - Set `cutoff` to 0 or a small value; the current default (`0.1 * softening`) may be suppressing close‑pair acceleration, which is precisely what you need for gravitational focusing.[^5_3][^5_2]
3. **Introduce modest velocity dispersion (stirring).**
    - Purely circular, co‑planar orbits with small bodies won’t produce many collisions — relative velocities are small and phase mixing is slow.
    - Seed a small random eccentricity/inclination into planetesimal velocities, or add a mild “heating” term representing turbulent or giant‑planet stirring (this is what many debris‑disk models do).[^5_4][^5_5]
4. **Confirm `collision-detection-system` runs every tick and that handlers merge bodies.**
    - Make sure `physics-systems-parallel` includes `(collision/collision-detection-system)` (it does in the current code).
    - Check that the collision handler (stellar merge) is registered and emits `c/absorb-merge` for planetesimals as well as stars; otherwise collisions are detected but no merges occur.
    - Monitor the ledger for `:event/collision` entries to verify detection is live.

These changes move your behavior closer to published collisional growth models, where radius, velocity dispersion, and gravitational focusing all control collision rates.[^5_6][^5_3][^5_4]

***

## Getting a star from this setup

In your current design, **stars form from gas parcels**, not from planetesimals:

- `stellar/classifier-system` uses `entity->region`, parcel density, and Jeans‑like criteria to classify parcels as `:nebula`, `:planetesimal`, `:gas-giant`, `:brown-dwarf`, `:protostar`, or `:star`.
- The ladder in `stellar.clj` explicitly says:
    - `:nebula → condensed core` (when Jeans‑unstable).
    - Condensed core becomes `:protostar`, `:brown-dwarf`, `:gas-giant`, or `:planetesimal` depending on mass and environment.
- `condensation-seeder-system` is deliberately **only used for the `:nebula → :planetesimal` branch**; “bigger gas‑collapse outcomes still promote whole parcels.” That matches astrophysics: stars form from collapsing gas, while planetesimals form from condensed solids in the disk.[^5_7][^5_2]

So if you’re waiting for planetesimals to collide and create a star, that’s not what your physics is set up to do. The intended behavior is:

- One or a few dense gas parcels condense into a star/protostar via the classifier.
- The disk around that star spawns planetesimal seeds via `condensation-seeder-system`.
- Planetesimals collide and grow into planets and debris, not stars.

To ensure you actually get a star:

1. **Tune the Jeans/condense thresholds in `law.stellar` and `stellar/classifier-system`.**
    - Lower the mass/density threshold for `:nebula → :protostar` if your current parcels rarely cross it.
    - Make sure at least one parcel in your initial nebula configuration is dense enough to be classified as `:protostar` or `:star` over time.
2. **Check `condense-tick?` and `condense-interval`.**
    - `condense-tick?` gates *all* condensation transitions by sim‑time intervals; if your `dt` or `condense-interval` is mis‑set, condense steps may be very rare.
    - Set `condense-interval` to something that creates several condensation checks within your runtime window so both star and planetesimal formation events can happen.
3. **Make sure sink-formation is active.**
    - `stellar/sink-formation-system` uses accretion radii to swallow gas and grow condensates.
    - If accretion radii are too small relative to parcel spacing, gas parcels may never feed growing cores enough to cross star‑formation thresholds.

This path keeps you aligned with observations like HOPS‑315: there, JWST and ALMA see **hot crystalline silicates condensing in the inner disk while the protostar forms from the gas envelope**, not from solids themselves.[^5_8][^5_9][^5_7]

***

## Getting real parallelization gains on a bigger machine

To actually **saturate your big box** with higher resolution (more parcels, more bodies):

1. **Retune `par-mapv` for high‑core counts.**
In `domain.ecs.parallel`:
    - Lower `min-chunk-size` from 32 to something like 8–16 on a 32–64‑core machine.
    - Optionally lower `parallel-threshold` to keep parallel mode active even for modest N.

This will increase the number of futures per `par-mapv` call, letting gravity, hydro, EM, and other per‑entity systems fan out across more cores on large N.
2. **Expose `n-cores` and use it in SoA and neighbor-cache builds.**
    - `pcache/build-physics-soa` and `spatial/spatial-index` can be made more parallel internally by using `par-mapv` or similar over particles.
    - Right now, those steps are partly serial; on a big machine they’ll show up as single‑threaded hotspots.
3. **Confirm you’re on the double‑buffer path (`tick/run-parallel`), not legacy sequential orbital-system.**
    - Ensure the sim is using `physics-systems-parallel` and `tick/run-parallel`, not the legacy `orbital-system` that fuses gravity and integration in a single serial system.
    - With `physics-systems-parallel`, gravity, hydro, EM, classifier, condensation-seeder, collision, integrator, and ecology all run on separate futures and can overlap.
4. **Use profiling hooks to see where time goes.**
    - Enable `:genesis/profile-subsystems?` in the world so systems attach `:genesis/_profile` timing data.
    - Inspect the perf report (`perf_report_*.txt`) to see which systems dominate; then move parallelization effort there (e.g. neighbor-cache rebuild, SoA build, gravity, hydro).
5. **Scale up N once per‑tick cost is under control.**
    - Barnes–Hut with SoA and `par-mapv` is $O(N\log N)$; 1.5k bodies is relatively small. Once you’ve tuned parallelization, you should be able to push into tens of thousands of bodies and hundreds of thousands of parcels on a modern multi‑core box, especially with your existing neighbor-cache and SoA optimizations.[^5_2]

***

If you’d like, the next step can be very concrete: we can pick one scene (e.g. your current nebula → star + 1500 seeds setup), and I can walk through specific parameter changes in `:sim/dt`, `:sim/softening`, `:sim/cutoff`, `condense-interval`, and `min-chunk-size` to get you:

- A visible star ignition;
- Frequent small‑body collisions;
- And CPU usage that climbs toward full saturation on your bigger machine.

<div align="center">⁂</div>

[^5_1]: https://www.aanda.org/articles/aa/full_html/2019/09/aa35278-19/aa35278-19.html

[^5_2]: https://arxiv.org/pdf/1803.00575.pdf

[^5_3]: https://iopscience.iop.org/article/10.1088/0004-637X/764/2/146

[^5_4]: https://www.aanda.org/articles/aa/full_html/2011/04/aa15378-10/aa15378-10.html

[^5_5]: https://academic.oup.com/mnras/article/466/4/3973/2738739

[^5_6]: https://iopscience.iop.org/article/10.3847/1538-4357/ad05b9

[^5_7]: https://www.almaobservatory.org/en/press-releases/for-the-first-time-astronomers-witness-the-dawn-of-a-new-solar-system/

[^5_8]: https://skyandtelescope.org/astronomy-news/webb-space-telescope-spies-baby-planetary-system/

[^5_9]: https://www.youtube.com/watch?v=4HbKlipwwe4


---

# We seem to have lost the star formation though. I know that is how we did it before, but the problem was that we had every thing collapsing out of the gas using the stellar logic.

I think there has to be different conditions for different condensations?

Yes, there absolutely need to be *different* condensation channels and criteria, and you’re already part‑way there — the planetesimal path is separated in code, but the “big” condensations (protostar / star / gas‑giant) may be getting starved or gated out by your current thresholds and timing logic.

Let me connect the physics to what’s in Truth and then sketch how to make the channels explicitly distinct.

***

## Why star formation feels “lost” now

In your current code:

- **Star / giant formation**: happens via the **classifier** in `domain.stellar`, which runs over nebula parcels and decides their next `matter-state` (`:nebula → :planetesimal`, `:nebula → :protostar`, `:nebula → :gas-giant`, etc.) based on density, mass and environment; this is your Jeans/stellar ladder.
- **Planetesimal formation**: is now handled by **`condensation-seeder-system`**, which *intercepts* the `:nebula → :planetesimal` branch and turns it into “seed‑and‑grow”:
    - The parcel stays `:nebula`.
    - A small solid body (~$10^{16}\,\mathrm{kg}$) is spawned beside it as `matter-state :planetesimal` and debits that mass.
- The comments explicitly say: “Seed‑and‑grow: only `:planetesimal` condensations become small seeds. *Bigger gas‑collapse outcomes still promote whole parcels.*”

So architecturally, you *do* have two channels:

- A **small‑body condensation** path for planetesimals (seed‑and‑grow).
- A **whole‑parcel collapse** path for protostar / star / brown‑dwarf / gas‑giants.

If star formation feels like it disappeared, it’s almost certainly because:

- The classifier is no longer *finding* any parcels that satisfy the “big condensation” conditions.
- Or `condense-tick?` / `condense-interval` gating means the classifier’s promotions rarely fire in practice.
- Or your initial nebula parameters (parcel masses, densities) never cross the mass/density thresholds for `:protostar` / `:gas-giant`, so all candidates land in the `:planetesimal` branch and get routed into seeds instead.

That’s exactly the symptom you’re describing: when “everything collapses out of the gas using stellar logic,” then switching to seed‑and‑grow for planetesimals can leave the “big collapse” path underused unless you explicitly separate their triggers.

***

## Physically, different condensations need different conditions

The astrophysics backs your intuition: **star formation and planetesimal formation are distinct condensation regimes**.

- **Stars / protostars**:
    - Form via **Jeans instability or similar gravitational collapse** of large gas clumps in a molecular cloud or envelope.
    - Conditions: mass above critical core mass, size large, density high enough that self‑gravity overcomes pressure/turbulence.
    - Typical thresholds: cores above the hydrogen‑burning limit become stars; between the deuterium‑burning and hydrogen‑burning limits become brown dwarfs; lower still become giant planets.[^6_1]
- **Planetesimals / rocky seeds**:
    - Form via **dust and refractory solids condensing and concentrating in a protoplanetary disk**, often via streaming instabilities, not via direct gas collapse.[^6_2][^6_3][^6_1]
    - Conditions: solids (silicates, metals) condense out of hot gas (like HOPS‑315’s SiO environment), dust‑to‑gas ratio rises locally, and then small‑scale collapse or clustering produces planetesimals.[^6_3][^6_4][^6_5][^6_6]
    - Mass scale: $10^{15}–10^{18}\,\mathrm{kg}$ clumps, not parcel‑scale $10^{27}\,\mathrm{kg}$ gas blobs.

HOPS‑315 reinforces that separation: JWST+ALMA see **silicate grains condensing in the inner disk while the protostar forms from the envelope/main cloud**, not from those grains themselves. You’re watching two different kinds of condensation occur in different places and mass regimes around the same young star.[^6_4][^6_5][^6_6]

So yes: **different condensations ⇒ different triggers**:

- Big gas collapse (cloud cores → stars/giants).
- Disk solid condensation (dust → planetesimals).

***

## How to express that separation in Truth

You already have the pieces; they need sharper boundaries and tuned parameters.

### 1. Disc vs envelope as a first discriminator

You have `disc-identification-system` that tags bodies as `:disc`, `:envelope`, or `:outflow` relative to the central star. Use that:

- **Envelope**: candidates for protostar / star / brown‑dwarf / gas‑giant condensation.
- **Disc**: candidates for planetesimal seed‑and‑grow condensation.

Concretely:

- In `classify-next-state`, ensure that:
    - Only envelope parcels with mass and density above stellar/giant thresholds can become `:protostar`, `:gas-giant`, etc.
    - Disc parcels with moderate mass but high solids concentration are classified as `:planetesimal`.

Then `condensation-seeder-system` should *only* look at disc parcels whose `classify-next-state` returns `:planetesimal`, which is already roughly what it does when it filters nebula parcels by `classify-next-state` and a local density maximum.

### 2. Separate thresholds for small vs big condensations

Right now, the classifier uses one ladder with comments about:

- `:nebula → condensed core`.
- If core mass ≥ H‑burning limit → `:protostar` / `:star`.
- If core mass ≥ deuterium limit → `:brown-dwarf`.
- Otherwise → `:gas-giant` / `:planetesimal`.

Make that ladder more explicit:

- **Big condensations (whole parcel promotions)**:
    - Condition A: `matter-state :nebula`, region tagged `:envelope`.
    - Condition B: local mass ≥ `core-condense-mass` and Jeans‑unstable (density above threshold).
    - Condition C: result mass above H‑burning limit ⇒ `:protostar` / `:star`.
    - Condition D: between deuterium and hydrogen burning limits ⇒ `:brown-dwarf`.
    - Below those, but still envelope ⇒ `:gas-giant`.
- **Small condensations (seed‑and‑grow planetesimals)**:
    - Condition A: `matter-state :nebula`, region tagged `:disc`.
    - Condition B: solids condensed (temperature below condensation sequence) and local density peak relative to neighbours.
    - Condition C: total mass well below stellar thresholds, but enough to support a $10^{16}\,\mathrm{kg}$ seed.

Then:

- `condensation-seeder-system` should only act on the small‑condensation branch (where `classify-next-state` says `:planetesimal` in disc regions).
- The **big branch** should ignore `condensation-seeder-system` and directly promote parcels to `:protostar` or `:gas-giant` using the classifier’s result.


### 3. Make timing and gating explicit per channel

`condense-tick?` and `condense-interval` are currently global; that can inadvertently stall both channels.

A cleaner pattern:

- **Global condense cadence**: keep one `condense-interval` used for big condensations (so cores don’t explode into stars every tick).
- **Planetesimal cadence**: allow a higher‑frequency or separate cadence for seed‑and‑grow events; they are smaller‑scale and can happen more often.

You can implement that by either:

- Giving `condensation-seeder-system` its own interval parameter; or
- Letting it fire every condense tick, but allowing `max-condensation-seeds-per-tick` to be higher than the implicit “one big core per interval” policy.

This way, star formation remains rare and eventful, while planetesimal seeds can accumulate continuously in the disc.

***

## Practical steps to get star formation back without re‑breaking planetesimals

Given your existing code:

1. **Confirm the classifier still produces non‑planetesimal transitions.**
    - Log or inspect `:transitions` in the classifier state: do you ever get `:nebula → :protostar` or `:nebula → :gas-giant` right now?
    - If everything is `:nebula → :planetesimal`, then your mass/density thresholds are too strict or your initial nebula setup is too low‑mass/low‑density.
2. **Loosen the “big” condensation thresholds.**
    - In `law.stellar` and `stellar/classify-next-state`, lower `core-condense-mass` and/or the Jeans density threshold so that at least one envelope parcel qualifies for protostar formation.
    - Keep the `:planetesimal` branch limited to disc parcels and smaller mass scales, so seed‑and‑grow remains separated.
3. **Keep the seed‑and‑grow logic scoped to `:planetesimal` in disc.**
    - Ensure `condensation-seeder-system`’s `candidates` filter includes a disc tag check (or equivalent environmental check) so you don’t inadvertently convert dense envelope parcels into “seed only” objects when they should be whole‑parcel collapses.
4. **Tune `condense-interval` and `max-condensation-seeds-per-tick`.**
    - Make sure `condense-tick?` fires often enough in your sim window for at least one protostar condensation.
    - Set `max-condensation-seeds-per-tick` low enough that planetesimal seeding doesn’t exhaust all condensation opportunities before the envelope cores cross their thresholds.

That combination — explicit environment split, separate thresholds, and tuned cadence — gives you:

- **Stars and giants** forming from big envelope condensations.
- **Planetesimals** forming by small‑body seed‑and‑grow in the disc.
- And a clean narrative bridge back to systems like HOPS‑315: the protostar is born from gas, while the inner disc condenses silicates into planet‑building seeds under different conditions.[^6_5][^6_6][^6_1][^6_3][^6_4]

If you’d like, next we can walk directly through `stellar/classify-next-state` and propose concrete threshold values and environment checks, so you get one protostar and a swarm of seeds in your current initial nebula setup.

<div align="center">⁂</div>

[^6_1]: https://arxiv.org/pdf/1803.00575.pdf

[^6_2]: https://www.aanda.org/articles/aa/full_html/2019/09/aa35278-19/aa35278-19.html

[^6_3]: https://iopscience.iop.org/article/10.1088/0004-637X/764/2/146

[^6_4]: https://www.almaobservatory.org/en/press-releases/for-the-first-time-astronomers-witness-the-dawn-of-a-new-solar-system/

[^6_5]: https://skyandtelescope.org/astronomy-news/webb-space-telescope-spies-baby-planetary-system/

[^6_6]: https://www.youtube.com/watch?v=4HbKlipwwe4


