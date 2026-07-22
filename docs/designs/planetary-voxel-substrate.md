# Planetary Voxel Substrate

**Path:** `docs/designs/planetary-voxel-substrate.md`
**Status:** approved (2026-07-22) — §7 questions 1-3 resolved by owner;
6-slice breakdown approved, slices 1-3 in execution
**Scope:** How a committed world's interior and surface become editable matter
— voxels — under the existing focus-cone duality, seeded from the Phase 0
`:planet-candidate` handoff, and how impacts, sculpting, mining, and
construction all read and write the same substrate rather than a second
engine. This is the destination `docs/designs/the-first-narrowing-star-to-planet.md`
§5 and §7 point to.

> No voxel namespace exists in `src/` today (confirmed: `src/infra/render/field.clj`
> and `src/infra/render/volume.clj` implement the ray-marched nebula fog, not a
> voxel world; `src/domain/` has no `voxel*` or `interior*` namespace). This
> document proposes where one goes, grounded in the research notes and the
> repo's own architecture rules — it does not assume any of this is built.

---

## 1. The duality principle: statistical field until bound, voxel mass once focused

`docs/notes/designs/2026.06.25.16.41.16-002-why-this-matters.md` states the
governing rule for every scale in the game, not just Phase 0:

> "Anything outside the player's focus cone is a statistical field. Anything
> inside it is a voxel mass."

And, on why the voxel representation itself does not change between phases,
only its magnitude:

> "The voxel representation doesn't change between phases. The *scale* of a
> voxel changes. A Phase 0 voxel might represent 10¹⁵ kg of gas. A Phase 5
> voxel might represent a cubic meter of soil."

The planetary research note is explicit that this must NOT become a
whole-planet voxel grid. From
`docs/notes/research/stellar-mergers-accretion/modeling stellar merges and feeding-003-planetary-interiors-and-surface-processe.md`
(§"Level of detail and when to use voxels"):

> "Use **macro geology fields** everywhere: interiors, plates, mantle
> convection, stress, and broad elevation, plus bulk properties for asteroids.
> These can be updated cheaply and drive events and resource distributions.
> Use **voxels** selectively:
> - Near the surface on habitable or player-visited worlds (upper crust +
>   shallow mantle).
> - Inside small bodies (asteroids, comets, rubble-pile moons) where
>   non-uniform structure matters.
> - In local volumes where collisions, volcanism, or tectonic deformation are
>   currently happening and visibly affect the player."

That "selectively" is load-bearing for this whole design: a planet is a
**scalar/statistical field** — macro geology fields (plate boundaries,
mantle-convection pattern, bulk composition, elevation trend, resource-field
density) computed cheaply for the whole body — until the player's
`attention-shell`/focus-cone (per `commitment-and-resonance.md` §5.2's
Immediate/Regional/Global LOD zones) overlaps a region of it. Only the
overlapped volume promotes to a voxel mass; everywhere else stays a coarse
field that can still drive events (a volcano can erupt off-screen as a
statistical event with an aggregate mass/composition delta, without ever
materializing a single voxel).

This is the same promotion/demotion conservation machinery already specified
for Regional↔Immediate body promotion in `commitment-and-resonance.md` §5.5
and the `phase-0-player-focus-promotion-demotion` epic
(`the-first-narrowing-star-to-planet.md` §7) — voxelization of a planet's
crust is that same machinery applied one level deeper, inside a single body
rather than across a star system.

---

## 2. Voxel data model

### 2.1 What a voxel carries

Per source #1 (§"Voxels for high-resolution terrain", §"Asteroids and small
bodies"), a voxel must carry enough state to answer both a geology question
and a gameplay question:

- **Material/mineral identity** — a category consistent with
  `law.composition/rock-formers` / `ice-formers` / `gas-giants` element
  buckets (e.g. basalt, granite, ore-bearing rock, ice, regolith, ejecta,
  breccia — derived categories over the same element vocabulary, not a new
  taxonomy; see §3).
- **Density** — kg/m³, consistent with the SI convention already used
  throughout `law/` and `domain/`.
- **Temperature** — K. Needed for the melt/vapor state transitions in §6 and
  to keep the merge-bug gotcha in `CLAUDE.md` ("star temperature is
  re-derived from virial(M, R) every tick") from recurring at voxel scale:
  a voxel's temperature must be a derived/re-checked quantity when its mass
  or state changes, not a value that silently drifts.
- **State** — solid / molten / vapor / fragmented-rubble, mirroring the
  matter-state ladder in `docs/research/physics/nebula-to-life-fsm.md` §4.1,
  but scoped to a voxel instead of a whole body.
- **Cohesion/strength** — needed for rubble-pile bodies (source #1
  §"Asteroids and small bodies": "store density, composition, and cohesion at
  each location... naturally supports irregular shapes, voids, and layered
  structures") and for construction stability (§5 below).

### 2.2 Representing a committed world's surface/interior

Given the "selective" mandate in §1, a committed world is represented as:

- A **macro geology field** covering the whole body cheaply: a small set of
  plates with boundaries and relative velocities, a mantle-convection
  pattern, a coarse elevation/stress field, and a resource field (§3) — all
  as "coarse grids or analytic functions attached to the planet entity"
  (source #1, verbatim: `c/plate-field`, `c/mantle-convection-field`).
- A **voxel band** that exists only where focus currently resolves it: "the
  upper crust and shallow mantle in a band under the surface (say, a few
  tens of km thick)" per source #1. Above/within it, water, ice, lava, and
  sediment are voxel materials with flow/erosion physics.
- For small bodies (asteroids, comets, rubble-pile moons), source #1 is
  explicit that representation should be **voxel-first** rather than
  field-first, because non-uniform interior structure is the point: "Attach
  a coarse shape hull (convex or level set) used for gravity and broad
  collisions; fine detail comes from voxel interactions when impacts or
  player mining occur." Small bodies therefore invert the general rule: the
  hull is the cheap field, the interior is voxel by default (bounded by the
  body's total volume, which is itself small).

### 2.3 Resolution follows focus (LOD as scheduling, not a separate engine)

Per `commitment-and-resonance.md` §5.3 ("The LOD system is not a separate
engine. It is a scheduling layer over the existing ECS tick that decides
*which entities get integrated this frame*"), voxel resolution should be
implemented the same way: which region of a planet's crust gets voxelized
this frame, and at what voxel size, is a scheduling decision over the same
tick, not a second simulation running underneath the ECS one. The zone table
extends naturally:

| Zone (from `commitment-and-resonance.md` §5.2) | Planetary-crust meaning |
|---|---|
| Immediate | Voxel band materialized under the observer's focus cone, full material/density/temperature/state resolution |
| Regional | Rest of the committed world's crust: macro geology fields only (plates, convection, coarse elevation, resource-field density) |
| Global | Other bodies in system: statistical mass/composition only |

### 2.4 Quadrant placement

Consistent with `CLAUDE.md`'s four-quadrant rule (domain never imports infra;
one ECS substrate; single-writer tick):

- **`law/voxel.clj`** (new) — Malli/contract schemas for a voxel record
  (material, density, temperature, state, cohesion) and for the macro
  geology field records (plate, mantle-convection-cell, resource-field cell).
  Sibling to `law/composition.clj`, reusing its element vocabulary rather than
  inventing a parallel one.
- **`domain/interior.clj`** or **`domain/geology.clj`** (new, name TBD by
  owner) — pure functions: macro-field evolution (plate motion, convection
  update), voxelization/de-voxelization of a crust band under focus
  (promotion/demotion, per §1), erosion/tectonic/volcanic voxel-update rules,
  and the collision-response voxel edits in §6. Registered as fan-out
  emitters in `domain.ecs.registry` per the single-writer rule — one system
  owns `:component/voxel-band`, one owns `:component/plate-field`, etc.
  Note `nebula-to-life-fsm.md` §4.1 already names `domain.environment` and
  `domain.interior` as owners of environment/dynamo state; a new interior
  namespace should sit alongside these, not duplicate their FSM ownership.
- **`infra/render/`** — a voxel mesher/renderer analogous to the existing
  `infra/render/volume.clj` froxel fog, but reading discrete voxel data
  instead of a continuous field. Coordinate-path note from `CLAUDE.md`
  applies unchanged: voxel meshes are `:body`-path geometry (model-matrix),
  not `:particle`/`:line` raw-position geometry.
- **`shape/`** — any new geometric helpers for voxel-grid ↔ world-space
  transforms, level-set hull sampling for asteroid gravity (source #1), and
  crater/excavation-bowl geometry (§6).

This is new content over the single ECS substrate, per `CLAUDE.md`'s
governing rule — "new physics = a new system + components added to
`genesis/physics-systems-parallel`, not a new engine" — voxelization is a
representation change for existing mass/composition/temperature components,
not a second world model.

---

## 3. Planetary chemistry, minerals, ore fields

Source #1 grounds mineral/resource fields directly in the same macro-field/
voxel split as terrain:

> "Attach resource fields to voxels: each voxel knows its material (basalt,
> granite, ore, ice, regolith) and properties (strength, value,
> processability)."
> "Let geological history pre‑populate the voxel world with rock types, ore
> bodies, sedimentary basins, and existing structures (mountain belts,
> volcanoes, river valleys)."

Concretely, this repo already has the element vocabulary to ground rock
types without inventing a new one: `law.composition/rock-formers` (`:Mg :Al
:Si :Ca :Fe :Ni :Na :S`) and `ice-formers` (`:C :N :O`) are the same buckets
`domain.chemistry/bulk-categories` (referenced by the M5 handoff card, see
§4) already derives `:metal`/`:rock` categories from. A voxel's "material"
field should be a category **derived** from local element mass fractions
(inheriting from the coarse geology field it was promoted out of) plus a
condensation/differentiation history, using `law.composition/condensation-
temperatures` to decide which elements are locked into which mineral phase
at a given local temperature — the same table Phase 0 already uses for
nebular condensation, reapplied at crustal/magmatic temperatures.

Ore veins and resource fields are proposed as a **coarse field first**
(a scalar density-per-element or per-mineral-category grid over the whole
crust, seeded from the body's bulk composition and its differentiation/
volcanic/tectonic history — richer near convergent margins, hotspots, and
impact sites per source #1), that **resolves to voxels under focus** exactly
like terrain: when the player's focus cone (or a mining tool) enters a
region, the coarse ore-density value there is used to instantiate concrete
ore-bearing voxels (with material category, density, and a `:value`/
`:processability` property per source #1), consuming from the coarse field's
conserved total the same way Regional→Immediate body promotion conserves
mass (`commitment-and-resonance.md` §5.5).

**Open question:** the research notes do not specify a mineral taxonomy
(basalt/granite/ore/ice/regolith are named as examples, not a closed set) or
how many categories are practical for real-time voxel storage; see §7.

---

## 4. Seeding the initial voxel world from the M5 `:planet-candidate` record

`kanban/tasks/ecology-water-gate-snowline.md` §5 defines the Phase 0 → Phase 1
handoff contract. Each field maps to an initial condition for the voxel/
macro-field seed, per `the-first-narrowing-star-to-planet.md` §5:

| `:planet-candidate` field | Seeds |
|---|---|
| `:material-class` (`:rocky \| :icy \| :gaseous \| :mixed`) | Chooses the overall crust/mantle/core layer template — a rocky world seeds a metallic-core / silicate-mantle / rock-crust stack; an icy world seeds an ice-shell-over-ocean-or-rock-core stack (cf. `:env/subsurface-ocean` in `nebula-to-life-fsm.md` §4.3); a gaseous candidate has no meaningful crust voxel band at all (no solid surface to sculpt) |
| `:bulk-composition` (element mass fractions) | Seeds the initial macro geology field's mineral/ore distribution (§3) via `law.composition/rock-formers`/`ice-formers` split |
| `:thermal-band` / `:equilibrium-temperature` | Seeds initial voxel/crust state: `:hot` bands start closer to `:env/magma-ocean` (source #1 + `nebula-to-life-fsm.md` §4.3) with a thin or absent solid crust; `:frozen`/`:cold` bands start with an ice-formers-dominated crust and low tectonic activity |
| `:atmosphere-class` / `:retained-species` | Seeds starting climate/air above the voxel surface — does not itself affect voxel material, but determines whether erosion (source #1: "erosion requires a dynamic atmosphere and water/ice") is active from tick 0 |
| `:surface-gravity` | Seeds structural constraints for the macro geology field (how tall mountains/volcanic edifices can stand before collapsing) and feeds the binding-well shape in `the-first-narrowing-star-to-planet.md` §2.2 |
| `:rotation-axis` / orbit fields | Seeds seasonal/tidal forcing on the macro geology field (day/night thermal cycling, tidal-heating modifiers per `nebula-to-life-fsm.md` §7 `:mod/strong-tidal-heating`) |
| `:core-dynamo?` / `:magnetic-field` | Not a voxel input directly, but gates whether atmosphere/erosion stay active over time (feeds back into §"erosion" above) |

This reuses the FSM ownership already declared in
`docs/research/physics/nebula-to-life-fsm.md` §4.3 (`domain.environment` owns
environment-state, e.g. `:env/magma-ocean` → `:env/crusted-volcanic` →
`:env/temperate-habitable`) — the voxel crust's initial state and the
Environment FSM's initial state must agree, since they describe the same
physical fact at two resolutions.

**Open question:** neither source specifies the exact layer thicknesses
(core/mantle/crust fractions) or a formula translating `:bulk-composition` +
`:surface-gravity` into a differentiated layer stack; see §7.

---

## 5. Sculpting, mining, construction — two distinct tiers of one substrate

Source #1 (§"Civilizational phase: mining, construction, and terrain
shaping") names three verb classes at voxel granularity:

> - **Mining**: the player removes or alters voxels, changing the local
>   geology and resource field; mining deeper reaches different units.
> - **Construction**: voxels are reassembled into engineered structures ...
>   whose stability depends on underlying voxel support and material
>   properties.
> - **Land shaping**: cut and fill operations, terraforming, damming rivers,
>   diverting lava or water — all voxel operations driven by player tools.

These verbs operate on the **same substrate** at two structurally different
tiers of the game, which this design keeps explicitly distinct rather than
conflating:

1. **God-scale sculpting (Phase 1 ability palette).** Per
   `commitment-and-resonance.md` §4.4, the Phase 1 hotbar slots
   (Atmosphere / Hydrography / Tectonics / Orbit / Biosphere / Culture) act
   through the macro geology field, biasing plate motion, volcanism,
   erosion rates, and river/ice dynamics at the whole-world or regional
   scale. When the player's focus cone happens to overlap a voxelized
   region while an ability fires (e.g. Tectonics biasing an active rift
   under focus), the macro-field bias is what drives the *local* voxel
   edits described in source #1's "macro fields determine where and when
   events occur; voxels capture the detailed shape and material response
   in those regions." The player is nudging a statistical process, not
   directly placing individual voxels.
2. **Character-scale mining/construction (post-civilization, single-
   character mode).** Per source #1's civilizational-phase section, once
   play has narrowed to one character (the far end of the master arc in
   `the-first-narrowing-star-to-planet.md` — "nebula → star system → planet
   → biosphere → species → one character"), the same voxel operations
   (remove/place/reshape) are driven directly by tool use: picking, mining,
   digging, building. This is "Minecraft but voxel-aware planetary
   physics" — direct voxel edits, not statistical bias — but they still
   read and write the identical `law/voxel.clj` records and the same
   `domain/interior.clj` (or equivalent) systems, because per §1 there is
   only one voxel representation across all phases, only the scale and the
   verb-to-edit mapping change.

The four-control continuity table (`the-first-narrowing-star-to-planet.md`
§4, inherited from `2026.06.25.16.41.16-002-why-this-matters.md`) already
names this exact continuity: "Interact" means "Nudging particle density in
focused region" at Phase 0, "Encouraging a migration or ecological pressure"
at Phase 3, and "Picking up a rock, opening a door" at Phase 6 — the same
verb, narrowing from statistical bias to direct voxel manipulation.

**Open question:** neither source specifies the mechanics of construction
stability (what makes a voxel structure "hold" — a rigid-body proxy over
supported voxel columns? a full stress-relaxation model?) or how mining
depth maps to "different units" (ore veins, mantle xenoliths) in practice;
see §7.

---

## 6. Collision → shock → voxel-carving pipeline

Source #1's second half (§"How do we handle planetary collisions?") gives a
concrete, four-step pipeline that this design adopts as-is, since it already
reuses existing physics rather than proposing a second engine:

1. **Compute bulk outcome from scaling laws, not a hydrocode.** From the
   impactor's coupling parameter (`a U^μ ρ^ν` — radius, velocity, density),
   published/fitted scaling laws give melt volume, vaporized volume,
   excavated volume + crater radius/depth, and ejecta mass/velocity
   distribution — "you don't solve full hydrodynamics."
2. **Map results onto voxels:**
   - **Excavation** — "Remove voxels inside the excavation bowl ... and
     redistribute those voxels as an ejecta blanket around the impact site
     (lower density, more fragmented material)."
   - **Melting** — "Tag voxels in the melt region ... as molten: change
     their material state to fluid ... Allow them to flow for some
     relaxation time." For giant impacts this can mean a "briefly ... a
     planet-wide magma ocean" — i.e. the whole crust's voxel band (or, for
     off-focus impacts, the macro geology field's melt-fraction scalar,
     per §1) transitions to `:env/magma-ocean`.
   - **Vaporization** — voxels in the vapor region are removed from the
     solid field and added to the target's atmosphere (if bound) or a
     temporary escaping debris/gas field.
   - **Fragmentation** — hydrocode-derived fragment-size and ejecta-speed
     distributions decide how much becomes large coherent chunks (new
     asteroids/moons, built from voxel subsets of the original shape) vs.
     fine rubble.
3. **Classify the collision regime first** (source #1 §"Regimes"): simple
   cratering, complex cratering/basin formation, catastrophic disruption, or
   merging/accretion, from `Q = ½ m_imp U² / M_target` vs. literature
   disruption thresholds — this determines *which* of the voxel operations
   above apply and at what scale, before any voxel is touched.
4. **Cooling back into solids.** Melt voxels convert back to solid rock
   categories (basaltic crust, impact-melt breccia) on a conductive/
   radiative cooling timescale; fragments either settle into rubble-pile
   voxel aggregates (small bodies) or relax toward a hydrostatic shape
   (merged bodies) while retaining non-uniform crust/mantle structure where
   melt pooled.

This pipeline is explicitly a **reuse** of the physics already in the repo,
not a parallel one: the coupling-parameter/scaling-law step is the same
kind of "compute the bulk physical outcome from a closed-form approximation,
then apply it as a discrete operation" pattern the stellar-merger and
accretion code already uses elsewhere in `domain/`, just targeting voxel
edits instead of ECS component writes on a whole body. Per `CLAUDE.md`'s
merge-bug gotcha ("Merges must not volume-sum radii for compact objects...
temperature is re-derived from virial(M,R) every tick"), the cooling step
(4) must likewise **re-derive** voxel temperature/state from local physics
each tick rather than let it drift as a stored value — the same class of
bug the note warns recurs.

**Open question:** the research explicitly says outcomes are computed "via
formulas that drive voxel edits," but does not specify which scaling-law
fits (Holsapple/Housen-style vs. others) to adopt, nor the performance
budget for carving a crater's worth of voxels inside a single tick; see §7.

---

## 7. Open questions / gaps

The research notes are the user's own exploratory conversation, not a
finished spec — the following are genuine gaps, not filled in above:

1. **Performance envelope.** RESOLVED 2026-07-22 (Aaron): **deferred edit
   queue with a hard 2 ms/tick cap.** Voxel ops (carve, sculpt, mine) enqueue
   edits; a budgeted drain applies ≤2 ms of work per tick, spilling the rest
   to later ticks — a big impact's crater visibly *forms* over ~a second,
   which is a feature (felt mass) not a bug. Budget constant lives in `law/`
   as a tunable from day one. Original gap: no source gave a voxel count or
   frame-time budget; `CLAUDE.md`'s fixed-60Hz tick required an explicit one.
2. **Deep-interior resolution boundary.** RESOLVED 2026-07-22 (Aaron):
   **focus-driven dynamic band.** Voxels exist only near the player's focus;
   the depth boundary follows focus intensity — deeper play literally deepens
   the world. This is the attention ontology applied to depth: the same rule
   as the horizontal focus cone, projected downward. Consequence: the band
   must persist/unpersist as focus moves, which makes the save representation
   (item 3) load-bearing — unpersisted regions must round-trip through the
   macro field + edit diff without loss. Original gap: neither source defined
   the transition rule (fixed depth / isosurface / focus function) or what
   happens digging past the band.
3. **Save/world-size.** RESOLVED 2026-07-22 (Aaron): **field-seed + edit
   diff.** Persist only the diff of player/collision edits against the
   deterministically regenerable macro-field seed; load = regenerate +
   replay. This is the statistical/voxel duality's natural persistence form
   and the only option that survives item 2's persist/unpersist churn.
   Original gap: no source addressed persistence representation or storage
   cost.
4. **Chemistry/mineral fidelity.** The mineral examples given (basalt,
   granite, ore, ice, regolith) are illustrative, not a closed taxonomy;
   there's no specified mapping from `law.composition` element fractions +
   condensation temperature + pressure to a discrete, game-usable mineral
   enum, nor a specified ore-vein generation algorithm (only "richer near
   convergent margins, hotspots, impact sites" as a qualitative steer).
5. **Construction/stability physics.** Source #1 names construction as a
   voxel operation "whose stability depends on underlying voxel support and
   material properties" but does not specify the stability model.
6. **Scaling-law source.** The collision pipeline (§6) needs a specific
   fitted scaling-law set (crater-scaling literature is cited but not
   selected) to turn into `law/` constants; this is a research task, not a
   design gap the doc author should resolve by invention.

---

## 8. Board breakdown

APPROVED 2026-07-22 (Aaron) — materialized as kanban cards, in dependency
order, each sized to fit the ≤5-point child-slice convention already used by
the M5 epic (`ecology-water-gate-snowline.md`) breakdown. Owner sequencing
call: execute 1 → 2 → 3 first, hold 4–6 until the substrate proves out.

| # | Slice title | Est | Scope | Depends on |
|---|---|---|---|---|
| 1 | `voxel-substrate-law-schema` | 3 | `law/voxel.clj`: voxel record schema (material/density/temperature/state/cohesion), macro geology field record schemas (plate, mantle-convection-cell, resource-field cell); reuse `law.composition` element buckets, no behavior yet | — |
| 2 | `planet-candidate-to-voxel-seed` | 5 | `domain/interior.clj` (or agreed name): pure functions turning a `:planet-candidate` record (§4) into an initial macro geology field (layer template, mineral/ore distribution, initial environment-state agreement with `domain.environment`) | 1 |
| 3 | `voxel-focus-promotion-demotion` | 5 | Extend the promotion/demotion conservation machinery (`phase-0-player-focus-promotion-demotion` epic, `commitment-and-resonance.md` §5.5) one level deeper: materialize/de-materialize a voxel band under the observer's focus cone from the macro geology field, conserving mass/composition | 1, 2 |
| 4 | `voxel-god-scale-sculpting-ops` | 5 | Wire the Phase 1 ability palette (Tectonics/Hydrography/Atmosphere) to bias the macro geology field and, where focus overlaps, the resulting local voxel edits (erosion, uplift, volcanism) per source #1's macro-drives-local rule | 2, 3 |
| 5 | `collision-shock-voxel-carving` | 5 | Coupling-parameter/scaling-law classifier (regime + melt/vapor/excavation volumes) and the voxel-carving/melt-tag/cooling operations of §6, reusing existing collision-detection events rather than a new collision system | 1, 3 |
| 6 | `character-scale-mining-construction` | 5 | Direct voxel remove/place/reshape tool verbs for the far-future single-character mode, plus a first construction-stability check over supported voxel columns; explicitly deferred until the character-scale narrowing rung exists | 3, 4 |

Dependency order: 1 → 2 → 3 → {4, 5 in parallel} → 6.

---

## 9. Summary

A committed planet is a coarse macro geology field everywhere and a voxel
mass only where the player's focus cone currently resolves it — the same
duality that already governs Phase 0 nebular matter, applied one scale
deeper. The M5 `:planet-candidate` handoff record seeds that field's initial
layer template, mineral distribution, and thermal state. God-scale sculpting
(Phase 1 palette) biases the field and its locally-resolved voxels
statistically; character-scale mining/construction (post-civilization) edits
voxels directly — two tiers of one substrate, never two engines. Collisions
carve, melt, vaporize, and re-cool voxels using scaling-law approximations
of shock physics rather than a hydrocode, reusing the collision events the
sim already emits. What remains genuinely open — performance budget, exact
interior/voxel boundary rule, save representation, mineral taxonomy,
construction-stability model, and which scaling-law fits to adopt — is
named above rather than guessed at.
