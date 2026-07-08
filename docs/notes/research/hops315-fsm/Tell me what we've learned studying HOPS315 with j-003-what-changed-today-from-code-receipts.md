---

# Can you grep through the commits I've made today, and the documentation, specs, research. Make sure the agents didn't get rid of anything in a misunderstanding that might get in our way... I think they were starting to strip out the "accretion zone" I think they thought I wanted to have one or the other not both.

Implemented seed-and-grow condensation. What changed - New components: c/spawn-request-condense, c/mass-flux-condense, c/condensation-seeded (src/domain/ecs/components.clj). - Seed mass: law.planet-formation/condensation-seed-mass-kg = 1.0e16 kg (~10× Chicxulub), overridable via :genesis/condensation-seed-mass-kg (src/domain/planet_formation.clj). - New system: domain.stellar/condensation-seeder-system — gated by condense-tick?, local-density maximum, per-tick cap, and one-shot marker; emits spawn spec + mass debit + seeded flag; offsets seed from parent (src/domain/stellar.clj). - Classifier split: classifier-system now skips :nebula → :planetesimal (seeder handles it) but still whole-parcel promotes :gas-giant/:brown-dwarf/:protostar so star formation stays intact. - Integrator: c/mass-flux-condense registered in the :mass accumulate; sole writer of mass remains the integrator. - Lifecycle: c/spawn-request-condense added to materialize-lifecycle. - Dead code removed: obsolete accretion-zone-system. - Spec updated: docs/specs/seed-and-grow-condensation-realspec.md now reflects planetesimal-only seeding, collisional growth, and the dedicated debit channel. - Tests: new test/domain/condensation_seeder_test.clj (9 tests) + updated test/domain/genesis_test.clj. Verification - clj -M:test: 590 tests, 0 failures, 0 errors - clj -M:cljfmt check: clean - bin/bench :phase0: full tick ~18–26 ms for 500 particles (no measurable regression) - clj -M:test -n architecture-test: single-writer invariant holds Receipt appended; session-mycology ledger + spore written

Short version: nothing critical to star formation or accretion has been deleted; what changed is *who* owns the accretion radius and *how* `:nebula → :planetesimal` is handled. The old **accretion‑zone system** as a separate owner is gone, but accretion radii, feeding zones, and big gas condensations (protostar / star / giants) are still present and wired through `classifier-system` and `sink-formation-system`. The seed‑and‑grow path is explicitly planetesimal‑only.

***

## What changed today (from code + receipts)

The receipt you pasted matches the actual commit (`tag: Π-20260707010913`):

- New components:
    - `c/spawn-request-condense`
    - `c/mass-flux-condense`
    - `c/condensation-seeded`
- Seed mass: `condensation-seed-mass-kg = 1e16` kg in `domain.planet-formation`, overridable via `:genesis/condensation-seed-mass-kg`.
- New system: `stellar/condensation-seeder-system` gated by:
    - `condense-tick?` (global cadence),
    - local density maximum within a `condensation-local-radius-factor` × gas smoothing radius,
    - per‑tick seed cap,
    - one‑shot `c/condensation-seeded` flag.
- Classifier split:
    - `classifier-system` now **skips `:nebula → :planetesimal`**, leaving that specific branch to the seeder,
    - but it still promotes `:nebula → :gas-giant / :brown-dwarf / :protostar` and latches `c/accretion-radius` for those big sinks.
- Integrator:
    - `c/mass-flux-condense` is added to the `:mass` accumulate list in `influence-registry`, so the integrator remains the sole writer of `c/mass`.
- Lifecycle:
    - `c/spawn-request-condense` is added to the `spawn-request-components` vector in `materialize-lifecycle`, so seeds become entities next tick.
- Dead code:
    - The obsolete **`accretion-zone-system`** was removed; its responsibilities were already absorbed by newer systems.

So the “one or the other” decision was: *no longer have a separate accretion‑zone system as a system*, but keep both:

- **Big gas condensations** with accretion radii (for star/giant formation).
- **Small seed‑and‑grow condensations** for planetesimals.

***

## Is anything important missing or stripped?

### Accretion radii and feeding zones

From `domain.stellar`:

- `classifier-system` is now documented as:

> “SOLE writer of matter‑state AND accretion‑radius… All other condense transitions (:gas‑giant, :brown‑dwarf, :protostar) still promote the whole gas parcel and latch an accretion‑radius so the big sink can feed.”

That’s exactly the accretion‑zone behavior you care about: the *zone* is now expressed as `c/accretion-radius` latched on the condensing core, rather than a separate `accretion-zone-system` writing it later.
- Constants and helpers related to feeding zones are still there:
    - `feeding-zone-factor` and `resolution-feeding-zone-factor`, used to set `c/accretion-radius` based on gas smoothing radius and resolution.
    - Disk regime logic still tags `:core-accretion-zone` as a disk state when Toomre $Q > 1.5$ and `sigma-solid > 0`.

So:

- The **concept of accretion zone** (a physical region from which a sink feeds) is intact.
- The **field** that implements it (`c/accretion-radius`) still exists and is owned by `classifier-system`.
- The **sink‑formation** and **mass‑transfer** systems still use those radii to route gas into sinks.

What’s gone is the extra *system* that used to stand between the classifier and sinks; its logic has been folded into classifier + sink‑formation + disk‑evolution, which is cleaner in the single‑writer/parallel tick architecture.

### Star formation

In the physics pipeline (`physics-systems-parallel` in `domain.genesis`):

- `stellar/classifier-system` runs every tick, deciding matter‑state transitions and accretion radii.
- `stellar/condensation-seeder-system` only touches the `:nebula → :planetesimal` branch:
    - It turns those into **small seeds**, debits mass, and leaves the parent parcel as `:nebula`.
- “Big” condensations (`:gas-giant / :brown-dwarf / :protostar`) are **still handled by `classifier-system`**, promoting entire parcels and giving them accretion radii.
- `stellar/sink-formation-system` and `mass-transfer-system` are still in the physics systems list, so sinks actually form and grow.

So you haven’t lost star formation; it’s just that:

- The star/giant path is pure “parcel condenses, classifier promotes, sink‑formation feeds.”
- The planetesimal path is now “parcel would condense to `:planetesimal`, but instead we seed a small solid body and leave parcel gaseous.”

If stars feel absent in practice, that’s likely because:

- Your current nebula setup doesn’t drive any parcel over the `protostar`/`gas-giant` thresholds given the new classification and mass‑transfer parameters.
- Or `condense-interval` and other gates mean condensations are rare in the window you’re watching.

But **architecturally**, the star path is still there and wired.

### Accretion‑zone documentation vs code

Docs/specs still mention `accretion-zone-system` in a few places:

- `genesis-formation-authoritative.md`
- `stage2-sink-formation.md`
- older “authentic phase0 formation physics” notes
- protoplanetary disk specs that talk about `:core-accretion-zone`.

That’s now a bit stale:

- The *system* named `accretion-zone-system` has been removed.
- The *behavior*—latching `c/accretion-radius` when a parcel condenses—has been folded into `classifier-system` and the resolution‑scaled feeding‑zone logic you saw.
- Disk regime tags like `:core-accretion-zone` still exist and matter for disk evolution and planet formation.

So you haven’t lost the physics; you just have some legacy documentation that talks about an intermediate system name which no longer exists.

***

## Is there a “one or the other, not both” misunderstanding?

From the receipt and the code:

- The agents explicitly preserved both:
    - **Whole-parcel condensations** for big sinks.
    - **Seed‑and‑grow condensations** for planetesimals only.
- The text in `classifier-system` and the seed‑and‑grow spec makes that clear:
    - Classifier skips `:nebula → :planetesimal`; seeder handles it.
    - Classifier still promotes `:nebula → :gas-giant / :brown-dwarf / :protostar` and latches accretion radii.
    - Seed‑and‑grow is explicitly “planetesimal‑only”.

So the only “either/or” decision was:

- **Either** keep a dedicated `accretion-zone-system`
- **Or** let `classifier-system` and resolution‑scaled feeding‑zone logic own `c/accretion-radius` directly.

The repo is now firmly in the second camp, which simplifies the parallel tick and single‑writer invariants. It does *not* remove accretion zones or star formation—just the extra layer.

***

## What I’d recommend next

To reduce confusion going forward:

- Mark the specs that still mention `accretion-zone-system` as **historical** or update them to say:
    - “Accretion radii (`c/accretion-radius`) are now latched in `stellar/classifier-system` at the moment of condensation.”
- Add a short note to `seed-and-grow-condensation-realspec.md` and `stage2-sink-formation.md` clarifying:
    - Seed‑and‑grow applies only to `:nebula → :planetesimal` condensations.
    - Big condensations use whole‑parcel promotion and still feed via accretion radii and BHL mass transfer.
- If stars are not appearing in your current runs, we can next inspect `classify-next-state` thresholds and your initial nebula parameters and tune them until you reliably get one protostar / star plus your 1.5k seeds in Phase 0.

From what’s in the repo today, you’re not blocked: both channels exist, accretion radii are still there, and the agents didn’t delete anything essential—just retired a now‑redundant system in favour of a cleaner single‑writer design.

---

# Ok back tracking then, what is missing for star formation?

Architecturally, the star‑formation path is still there; what’s “missing” now are *conditions* in your runs that produce a parcel that actually qualifies for the protostar/star ladder and isn’t being blocked by the isolation rules or accretion settings.[^8_1]

Here’s what must be true for a star to appear, and where things are most likely falling short:

***

## 1. A nebula parcel that actually condenses

For any parcel to leave `:nebula`, `classify-next-state` requires:[^8_1]

- `jeans-unstable?(region)` is true (self‑gravity beats support).
- Either:
    - Density ≥ `core-condensation-density`, or
    - Mass > `gas-particle-mass` (has accreted more than one parcel’s worth).
- Not within an existing sink’s accretion radius (`within-existing-sink?` must be false).

If no parcel ever satisfies *all* of these, nothing condenses, so you’ll never get a core at all, let alone a star. In practice, this depends heavily on:

- The **initial cloud density profile** and total mass.
- The **resolution** (`gas-particle-mass` and parcel count).
- Whether **feedback or dead‑zone settings** are too strong and preventing collapse.

***

## 2. Condensed mass above the stellar thresholds

When a nebula parcel does condense, `classify-next-state` uses mass to decide what it becomes:[^8_1]

- If $m ≥$ `hydrogen-burning-mass` (~0.08 M⊙), it becomes `:protostar`.
- If $m ≥$ `deuterium-burning-mass` but below H‑burning, it becomes `:brown-dwarf`.
- Otherwise it’s mapped into the substellar ladder (`:gas-giant` / `:planetesimal`).

So for star formation you need **at least one condensed core whose mass is above the hydrogen‑burning limit**. If your current cloud mass, parcel mass, or accretion rates are too low, every condensation event will land in the gas‑giant / brown‑dwarf regime and never reach `:protostar` with enough mass to ignite.

***

## 3. Growth via accretion radius and sink formation

Once a core exists, it has to grow:

- `classifier-system` now latches `c/accretion-radius` on the *single* “best” big condensation each time (highest density), using `feeding-zone-factor` × gas smoothing radius.[^8_1]
- `sink-formation-system` uses an **effective accretion radius**:
    - Max of the latched `c/accretion-radius` and a mass‑dependent Bondi radius.
    - It absorbs nebula parcels (and small planetesimals, for protostars/stars) within that zone.
- Those absorbed parcels feed mass into the core via `c/absorb-accrete`, which the integrator folds into `c/mass` on the next tick.

If accretion radii are effectively zero (e.g., gas smoothing radius or `feeding-zone-factor` too small), or if `competitive-accretion?`/Bondi settings are disabled/too weak, **cores won’t grow enough to cross the hydrogen‑burning mass** and will stall at substellar states.

***

## 4. Fusion conditions for protostar → star

Even a `:protostar` doesn’t automatically become a `:star`:

- In the `:protostar` branch, `classify-next-state` only promotes to `:star` when:
    - $m ≥$ `hydrogen-burning-mass`, **and**
    - `fusion-possible?(region)` is true (temperature and structure high enough).[^8_1]

So:

- You need the **fusion system and structure/eos systems** running and heating the core appropriately.
- If fusion never becomes “possible” (e.g., temperatures stay below `fusion-temp-threshold`), your protostar will either:
    - Stay a `:protostar`, or
    - Degrade to `:brown-dwarf` when contraction stalls below ignition conditions.

***

## 5. Interplay with seed‑and‑grow

The new seed‑and‑grow path only intercepts the **`:nebula → :planetesimal`** branch:

- `condensation-seeder-system` checks nebula parcels whose `classify-next-state` would be `:planetesimal`, and instead spawns a small solid seed and leaves the parent as `:nebula`.[^8_1]
- “Big” condensations (gas‑giant / brown‑dwarf / protostar) still go through whole‑parcel promotion.

That means seed‑and‑grow is **not** what’s blocking stars; what’s missing is:

- Having any parcel whose condensation event would land in the **deuterium / hydrogen‑burning mass regime** instead of the substellar ladder.
- Ensuring that once such a core exists, its accretion radius and Bondi capture are large enough to keep feeding it.

***

## So concretely, what’s missing?

From the current code, star formation will work again when your runs satisfy:

1. Some nebula parcels are **Jeans‑unstable and dense enough** to condense (check density vs `core-condensation-density` and total mass vs `gas-particle-mass`).
2. At least one of those condensation events produces a core with mass ≥ `hydrogen-burning-mass` (or can grow there via accretion), not routed into the planetesimal branch.
3. `classifier-system` actually latches a non‑zero `c/accretion-radius` on that core (gas smoothing radius and `feeding-zone-factor` must be sane).
4. `sink-formation-system` and mass‑transfer are active and able to feed gas into that core over time.
5. Fusion/structure systems raise core temperature enough that `fusion-possible?` becomes true.

If you want, we can next look at your current `create-world` parameters (total cloud mass, parcel count, `feeding-zone-factor`, `sim/dt`) and pick values that guarantee at least one condensation crosses the hydrogen‑burning mass threshold, so you reliably get a star again while keeping the seed‑and‑grow path for planetesimals.

<div align="center">⁂</div>

[^8_1]: 02ca0590-0163-420d-a549-97702c7aae03.md


---

# Let's talk about... let's build out the finite state machine starting from the nebula. gas can collapse into rocky, or gaseous bodies, what other things can happen? What else can the gas parcels become? Then what can happen to each of those states?

Here’s the finite state machine you already almost have in Truth, made explicit. I’ll stick to the `matter-state` ladder that’s coded in `classify-next-state`, and organize it as “what gas can become” and “what each state can do from there.”[^9_1]

***

## Core states in the ladder

From the code and stellar law, these are the main physical states your parcels/bodies can occupy:[^9_1]

- `:nebula` — diffuse gas parcels (SPH cloud)
- `:planetesimal` — condensed solid/rocky bodies below the opacity limit
- `:gas-giant` — condensed substellar bodies in the giant‑planet mass range
- `:brown-dwarf` — substellar bodies in the brown‑dwarf mass range
- `:protostar` — pre‑ignition stellar cores above the H‑burning mass
- `:star` — hydrogen‑burning stars
- `:planet` — disk‑owned worlds (sub‑grid in your spec)

Everything in the FSM is about how `matter-state` moves between these under collapse, accretion, fusion and mass loss.[^9_1]

***

## From `:nebula` — what gas parcels can become

A nebula parcel can:

1. **Stay `:nebula`**
    - If it is not Jeans‑unstable, or
    - Its density is below the core‑condensation threshold and it hasn’t accreted past one parcel’s mass, or
    - It lies inside an existing sink’s accretion radius (isolation criterion blocks new cores).[^9_1]
2. **Condense directly into a core** (whole‑parcel collapse)
When all of these are true:[^9_1]
    - `jeans-unstable?(region)`
    - Density ≥ `core-condensation-density` **or** mass > `gas-particle-mass`
    - Not within an existing sink’s accretion radius

Then the parcel leaves `:nebula` and becomes:
    - `:protostar` if $m ≥$ hydrogen‑burning mass (~0.08 M⊙).
    - `:brown-dwarf` if between deuterium‑burning and hydrogen‑burning limits.
    - Otherwise one of the **substellar classes** via `substellar-mass-class`:
        - `:gas-giant` in the opacity‑limit to brown‑dwarf desert range.
        - `:planetesimal` below the opacity limit.[^9_1]
3. **Spawn a small rocky seed (seed‑and‑grow)**
When the classifier would map a nebula parcel to `:planetesimal`, the **condensation seeder** intercepts it:[^9_1]
    - If `classify-next-state` says `:planetesimal`,
    - And the parcel is a local density maximum among nebula neighbours,
    - And condense cadence and per‑tick seed cap allow it,

then:
    - The parent parcel **stays `:nebula`**.
    - A new small body is spawned as `matter-state :planetesimal`, `body-kind :body/rocky`, with mass ≈ $10^{16}\,\mathrm{kg}$.
    - The seed mass is debited from the parcel via `c/mass-flux-condense`.

So from gas you get two broad families of outcomes:

- **Big condensations**, where the whole parcel becomes a core: protostar / brown‑dwarf / gas‑giant / planetesimal.
- **Small condensations**, where the parcel emits planetesimal seeds but remains gas.

***

## What can happen to `:planetesimal`

Once you have a planetesimal (either from whole‑parcel collapse or seed‑and‑grow), it can:

- **Grow by accretion**
    - If accretion (from gas, pebbles, or collisions) pushes its mass above the deuterium‑burning limit, `classify-next-state` may promote it up the ladder:[^9_1]
        - `:planetesimal → :gas-giant` if it crosses the opacity limit but below the brown‑dwarf desert mass.
        - `:planetesimal → :brown-dwarf` or `:protostar` if it gets into those mass ranges.
- **Be merged into a sink**
    - If it falls within a protostar/star’s accretion radius, `sink-formation-system` routes small planetesimals either:
        - Through the disk (if the sink is `:protostar`/`:star`) for viscous accretion and planet formation, or
        - Directly into the sink mass for hierarchical competitive accretion.[^9_1]
- **Stay substellar forever**
    - If it doesn’t accrete enough mass, it can remain `:planetesimal` indefinitely as part of the debris/planet‑forming population.

***

## What can happen to `:gas-giant`

A `:gas-giant` is a condensed body above the opacity limit but below the brown‑dwarf desert mass. It can:[^9_1]

- **Accrete up the ladder**
    - If $m ≥$ deuterium‑burning mass, it becomes `:brown-dwarf`.
    - If $m ≥$ hydrogen‑burning mass, it becomes `:protostar`.
- **Lose mass and move down**
    - With strong winds or stripping, a gas‑giant can drop below the opacity limit and be re‑classified as `:planetesimal` in the substellar ladder.
- **Be swallowed by an existing sink**
    - If a `:protostar`/`:star` captures it, it is merged directly (not re‑disked), feeding the sink’s mass budget.

***

## What can happen to `:brown-dwarf`

A `:brown-dwarf` (between deuterium‑ and hydrogen‑burning thresholds) can:[^9_1]

- **Accrete up to `:protostar`**
    - If $m ≥$ hydrogen‑burning mass, `classify-next-state` promotes it to `:protostar`.
- **Lose mass and step down**
    - If mass loss drops it into the gas‑giant regime, it can be re‑classified as `:gas-giant`.
    - Further loss would eventually demote it into `:planetesimal`, per the substellar ladder.
- **Remain a terminal substellar object**
    - If mass stays in the brown‑dwarf band and fusion remains impossible, it sits as a long‑lived `:brown-dwarf`.

***

## What can happen to `:protostar`

A `:protostar` is above the hydrogen‑burning mass but not yet a true star. It can:[^9_1]

- **Ignite and become a `:star`**
    - When $m ≥$ hydrogen‑burning mass **and** `fusion-possible?(region)` is true (temperature and structure above the threshold), `classify-next-state` promotes it to `:star`.
- **Fail ignition and demote to `:brown-dwarf`**
    - If contraction stalls at the main‑sequence radius while temperature stays below fusion threshold (`contraction-stalled?`), and mass is in the deuterium range, it becomes `:brown-dwarf`.
- **Lose mass and step back down the ladder**
    - If mass drops below the deuterium limit, it is classified via `substellar-mass-class` (gas‑giant or planetesimal).

So `:protostar` is the crossroads: onward to star if fusion lights; sideways/backward to brown‑dwarf or substellar if it cools or is stripped.

***

## What can happen to `:star`

Once something is a `:star`, you treat it as special in the FSM:[^9_1]

- **Stay a star while fusion sustains**
    - There is *hysteresis*: `fusion-sustaining?` keeps it as `:star` even if it dips slightly below the hydrogen‑burning mass threshold; it does not demote immediately on mass loss.
- **Demote after fusion cessation**
    - When fusion truly stops and mass has fallen:
        - Above deuterium but below hydrogen limit ⇒ `:brown-dwarf`.
        - Below deuterium limit ⇒ `substellar-mass-class` (gas‑giant / planetesimal).
- **Participate in feedback and sink formation**
    - Stars contribute UV feedback that heats nearby gas parcels and suppresses new `:nebula` condensations in a “feedback radius.”
    - They can also act as sinks that accrete gas and small bodies, growing their mass or feeding disk evolution.[^9_1]

In other words, stars are terminal in the sense of “no up‑ladder,” but not static: they can slowly walk back down the ladder once fusion ends.

***

## What can happen to `:planet`

Planets are explicitly treated as **disk‑owned, sub‑grid entities** in your spec:[^9_1]

- They are spawned by the planet‑seeder in disk evolution (`planet-formation/planet-seeds`) from a star’s disk solid surface density, not by direct parcel collapse.
- They don’t re‑enter the nebula ladder; their dynamics are handled by orbital and climate systems, not `classify-next-state`.
- In the FSM, you can treat `:planet` as terminal with respect to gas‑state transitions: once something is a planet, it doesn’t become a nebula parcel again.

***

## Summary as a ladder

From gas (`:nebula`):

- **Big collapse channel**
    - `:nebula → :protostar / :brown-dwarf / :gas-giant / :planetesimal`
- **Seed‑and‑grow channel**
    - `:nebula → :nebula + (spawned :planetesimal seeds)`

From there the mass ladder is:

- `:planetesimal ↔ :gas-giant ↔ :brown-dwarf ↔ :protostar ↔ :star`

with:

- Up‑transitions driven by **accretion** (Jeans collapse, Bondi accretion, sink feeding).
- Down‑transitions driven by **mass loss** (winds, stripping, feedback) and fusion cessation.[^9_1]

If you want, we can turn this into a literal FSM diagram (states + arrows) that maps cleanly to your `matter-state` and transition functions, and then use it to audit whether any desired transitions (e.g. `:nebula → :protostar`) are getting blocked in your current runs.

<div align="center">⁂</div>

[^9_1]: 02ca0590-0163-420d-a549-97702c7aae03.md


