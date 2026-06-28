<img src="https://r2cdn.perplexity.ai/pplx-full-logo-primary-dark%402x.png" style="height:64px;margin-right:32px"/>

# modeling stellar merges and feeding

Modeling **stellar** merges is usually best done as a hybrid: use 3D hydrodynamics to capture the actual interaction, then compress what you learn into a cheaper 1D model for broader sweeps. Recent work shows that 1D merger prescriptions based on entropy sorting can approximate 3D merger remnants fairly well, but they need shock-heating physics to match post-merger structure, and they otherwise tend to overmix hydrogen into the core and make the remnant look too rejuvenated.[^1_1]

If by “feeding” you mean fresh fuel being carried into hotter burning regions, that is also a real multi-dimensional effect: 3D simulations of late-stage massive stars show shell mergers and fuel ingestion are driven by entrainment and erosion of stable layers, not just the smooth diffusion assumed in many 1D models.[^1_2]

## Two regimes

There are two nearby problems here: star-star coalescence, and shell mergers inside one evolved massive star. The first is about combining two stellar structures into one remnant, while the second is about convection and nuclear burning letting one shell effectively “feed” another with fresh material.[^1_2][^1_1]

## Star-star mergers

For star-star mergers, a useful reduced model is entropy sorting plus a shock-heating correction calibrated against 3D calculations such as SPH or MHD merger simulations. A recent comparison against 39 SPH collisions found that the PyMMAMS-style method with shock heating matched post-merger thermal and composition structure better than plain entropy sorting, and the authors also compared against a 9 + 8 solar-mass MHD main-sequence merger. They further report that lowering the assumed shock heating improved agreement in the entropy profile, even though it only modestly changed the later stellar evolution of the remnant.[^1_1]

## Shell feeding

For shell-feeding problems in massive stars, 3D simulations of a 20 solar-mass model confirmed that multiple convective shells can merge in 3D, with entrainment and erosion of the separating stable layers acting as the main drivers of the merger. Those simulations found much faster convective velocities than 1D mixing-length theory, multiple burning phases inside the same merged shell, and a strongly asymmetric chemical structure after the event. In that study, the 1D model merged the shells around 12,000 s after the 3D run’s start, while the 3D model merged them around 1,200 s, and the 3D kinetic energy reached about an order of magnitude above the 1D case.[^1_2]

## Model design

A practical modeling stack is: 1) evolve progenitors in a 1D stellar code, 2) remap selected states into 3D hydro, 3) measure shock heating, entrainment, mixing depth, and where burning turns on, and 4) fit those measurements back into a fast 1D surrogate. For shell-merger work, the variables that matter most are entropy profile, composition by layer, rms convective velocity, turnover time, boundary migration, and nuclear energy generation before and after contact. The 3D shell-merger study is a good template because it explicitly used active composition tracers and a 12-isotope network spanning the relevant carbon-, neon-, and oxygen-burning physics.[^1_1][^1_2]

Are you trying to simulate binary star coalescence, late-stage shell mergers inside a massive star, or post-merger accretion onto a compact object?
<span style="display:none">[^1_10][^1_3][^1_4][^1_5][^1_6][^1_7][^1_8][^1_9]</span>

<div align="center">⁂</div>

[^1_1]: https://pure.mpg.de/rest/items/item_1703433/component/file_1703432/content

[^1_2]: https://arxiv.org/abs/2512.14669

[^1_3]: http://www.tapir.caltech.edu/~phopkins/talks/shanghai_rev.pdf

[^1_4]: https://astrobites.org/2026/06/24/a-merger-is-not-a-meal/

[^1_5]: https://arxiv.org/abs/astro-ph/0603054

[^1_6]: https://academic.oup.com/mnras/article/430/3/1901/979658

[^1_7]: http://arxiv.org/pdf/2310.14603.pdf

[^1_8]: https://academic.oup.com/mnras/article/533/1/687/7718808

[^1_9]: https://arxiv.org/html/2602.02141v1

[^1_10]: https://academic.oup.com/mnras/article/373/2/733/1277083


---

# I mean by feeding like when one start is pulling plasma off a smaller star near by, like they aren't quite merging, but one is slowly getting smaller.

Yes — that process is usually called **mass transfer** in a close binary system, and the most common version is Roche-lobe overflow. In that setup, the smaller or more evolved star becomes the **donor**, the one gaining gas is the accretor, and plasma flows through the inner Lagrange point $L1$ rather than the stars directly touching or fully merging.[^2_1][^2_2]

## What is happening

Each star in a binary has a region where its gravity dominates, called its Roche lobe, and the two lobes meet at $L1$. When one star expands enough to fill its Roche lobe, gas is no longer securely bound to it and can stream toward the companion, so the donor slowly loses mass and can shrink or be stripped of its outer layers.[^2_2][^2_1]

This is why the system can look like “one star feeding on another” without a merger actually happening. The key control knobs are orbital separation and the mass ratio, because they set how easily the donor fills its Roche lobe and how the transfer changes the orbit afterward.[^2_1][^2_2]

## How the gas moves

The gas usually leaves the donor as an accretion stream through $L1$, then either falls onto the companion directly or forms an accretion disk around it. Whether a disk forms depends on the geometry and the type of accretor, but disks are especially important when the accretor is a white dwarf, neutron star, or black hole.[^2_2][^2_1]

Recent 3D hydrodynamic work emphasizes that this stream is not perfectly captured by simple textbook flow pictures, because orbital effects such as the Coriolis force change the stream shape and reduce or reshape its velocity profile compared with older analytic models. That matters if you want to simulate the flow faithfully rather than just classify the binary.[^2_2]

## Modeling approach

If you want to model this computationally, there are two common levels:[^2_1][^2_2]

- 1D binary evolution models track the donor radius, Roche-lobe radius, mass-transfer rate, and orbital evolution; they are good for long time spans and parameter sweeps.[^2_1][^2_2]
- 3D hydrodynamic models resolve the stream itself, including asymmetry, shocks, angular momentum transport, and whether the stream makes a disk or impacts the star directly.[^2_2]

A clean minimal model usually includes:

- Donor mass and radius.
- Accretor mass and radius.
- Orbital separation or period.
- Roche geometry and the $L1$ nozzle.
- A prescription for angular momentum loss.
- A decision rule for direct impact versus accretion disk formation.[^2_1][^2_2]


## Possible outcomes

Mass transfer can stay relatively steady for a long time, or it can run away and become unstable depending on how the donor’s radius responds to mass loss compared with how its Roche lobe changes. If the transfer becomes extreme, both stars can end up inside a common envelope of gas rather than remaining a clean donor-accretor pair.[^2_2][^2_1]

The accretor’s identity changes the astrophysics a lot:[^2_1]

- Normal star: it gains mass and can be “rejuvenated.”
- White dwarf: accreted hydrogen can ignite and produce novae; if enough mass accumulates, the white dwarf can approach the Chandrasekhar limit and become a Type Ia supernova candidate.[^2_1]
- Neutron star or black hole: the infalling gas can produce strong X-rays through hot accretion flows.[^2_1]


## Best terminology

For what you described, the most precise terms are:

- Close binary mass transfer[^2_2][^2_1]
- Roche-lobe overflow[^2_1]
- Donor star and accretor[^2_2]
- Accretion stream or accretion disk[^2_2][^2_1]

If the stars are not close enough for Roche-lobe overflow, a weaker version can still happen through stellar-wind accretion, where one star captures part of the other star’s wind instead of stripping gas through $L1$.[^2_3]

If you want, I can next sketch a simple physics model for Roche-lobe overflow in code terms, or compare that process to black-hole accretion and tidal stripping.
<span style="display:none">[^2_10][^2_11][^2_12][^2_13][^2_14][^2_15][^2_16][^2_17][^2_4][^2_5][^2_6][^2_7][^2_8][^2_9]</span>

<div align="center">⁂</div>

[^2_1]: https://pure.mpg.de/rest/items/item_1703433/component/file_1703432/content

[^2_2]: https://arxiv.org/abs/2512.14669

[^2_3]: https://www.cambridge.org/core/services/aop-cambridge-core/content/view/8ECA6BA64433443C3B9F729DF6AF4AE7/S1539299600004901a.pdf/mass-transfer-between-binary-stars.pdf

[^2_4]: https://www.astro.ru.nl/~onnop/education/binaries_utrecht_notes/Binaries_ch6-8.pdf

[^2_5]: https://web.njit.edu/~binchen/phys321/download/s2018/LectureNotes/Hand_Kao_Binary_Systems.pdf

[^2_6]: http://www-star.st-andrews.ac.uk/~kdh1/bsad/bs11.pdf

[^2_7]: https://discovery.ucl.ac.uk/id/eprint/10107334/1/Accretion_flows_in_compact_bin.pdf

[^2_8]: https://pdfs.semanticscholar.org/6cef/f5971b8219a9cef2dbc04eff2658af2d3ee2.pdf

[^2_9]: https://vitaly.neustroev.net/teaching/2020b/ibs_07.pdf

[^2_10]: https://courses.ems.psu.edu/astro801/content/l6_p6.html

[^2_11]: https://arxiv.org/pdf/1010.4949.pdf

[^2_12]: https://astrobites.org/2025/07/14/binary-mass-transfer/

[^2_13]: http://www.astro.ex.ac.uk/people/mbate/Preprints/ukaffclose/close.pdf

[^2_14]: https://arxiv.org/pdf/2605.31290.pdf

[^2_15]: https://en.wikipedia.org/wiki/Binary_star

[^2_16]: https://arxiv.org/html/2505.14780v2

[^2_17]: https://cseligman.com/text/stars/binary.htm


---

# [https://github.com/octave-commons/Truth](https://github.com/octave-commons/Truth)

[https://github.com/octave-commons/Truth/tree/main/docs/designs](https://github.com/octave-commons/Truth/tree/main/docs/designs)
[https://github.com/octave-commons/Truth/tree/main/docs/research](https://github.com/octave-commons/Truth/tree/main/docs/research)

Can you review my notes?

This is a remarkably cohesive and well-articulated design. Here's my review:

## Overall Impression

The writing is genuinely excellent — the prose is precise without being dry, and the design intent bleeds through consistently. The central conceit of "character creation as cosmological authorship" is philosophically tight and mechanically motivated.

## What Is Working Well

**The phase arc is principled.** The player doesn't just zoom in — they lose power as the world gains it. That's a real design insight, not just a framing choice. The idea that autonomy and agency in the universe is inversely proportional to the player's influence gives the whole arc metaphysical coherence.

**Coherence as the resource is the right call.** It fits the ontology of the player as a "luminous fluctuation," it explains why focus has cost, and it degrades gracefully into observation rather than hard failure. Calling the failure state "decoherence" rather than "death" or "game over" is philosophically appropriate and elegant.

**The control table in the Phase 0 doc is one of the best pieces of design documentation I've seen.** Mapping Drift → Focus → Influence → Release across Phase 0 and future phases in a single table proves the language is continuous rather than bolted on.

**The soft-boundary signals section is underrated.** Communicating narrowing possibility through coherence regeneration rate, narrator tone, and visual texture rather than UI text is exactly the right approach for a game with this emotional target.

## Potential Issues or Gaps

**Binary star systems and Roche-lobe overflow have no mention in the nebula phase.** Given your conversation today, this matters: multiple stellar outcomes (close binaries, mass-transfer pairs) affect planet habitability, orbital stability, and sky-culture in enormous ways. The design currently implies a single-star default, but a binary where one star is slowly "feeding" on the other would produce wildly different calendars, eclipses, tidal regimes, and mythology than a single yellow dwarf.

**The chemistry model needs an early decision.** The Phase 0 doc wisely punts on "what is the minimal chemical model" as an open design question, but this has downstream risk. The cosmology-to-culture pipeline is only as meaningful as the initial chemistry, because C/O ratios, water delivery, and silicate vs. carbide worlds produce very different biospheres and therefore different civilizations. It may be worth doing a spike on this before implementing `domain/chemistry.clj`.

**The narrator's discovery arc is described but not sequenced.** "First as a pattern in the audiovisual field, then as occasional meaningful phrasing" is beautiful intent, but it will be hard to test or implement without a more concrete trigger model. Consider a schema for narrator presence level tied to slice phase markers, since you already have those.

**The "cosmology becomes culture" pillar in the phase doc has no Phase 0 mechanism yet.** Phase 3 and 4 depend on orbital structure, sky patterns, moon phases, and stellar color doing cultural work. Phase 0 needs to guarantee it produces those properties in a way the later slices can query, not just show them visually. A habitability-output schema that includes observable sky parameters (number of moons, star color, orbital period, presence of a second star) would close that gap.

## Smaller Notes

- The "Slip of Time" five-step structure is one of the clearest articulations of a graceful failure state I've seen in design writing. That section alone is worth preserving as a reusable pattern for all phase transitions.
- The deliverables list at the end of the world-gen-phases doc maps almost 1:1 to what the Phase 0 doc begins to specify. You're not repeating yourself; you're completing a loop. That's well-structured documentation.
- The multi-nebula flow (drift to another site after failure) is smart because it recasts retrying as exploration rather than punishment. Make sure the coherence resource supports cross-nebula persistence or intentional reset depending on which feeling you want.


## One Design Question Worth Raising

The Gates of Truth are the threshold event that locks time and connects worlds. But what causes Gate-capable conditions? Is it a scientific/engineering threshold the simulation actually tracks, or is it a narrative heuristic? If the simulation is genuinely tracking complexity and observability as real quantities, Gate-capability might be something that can emerge organically from Phase 0 choices — which would make the opening of the game retrospectively feel inevitable rather than scripted.

---

# At the civilizational phase, it's like playing a grand strategy game where you have research, and that continues through to the post-gate world as well, only you are an individual interacting with civilization guiding it's research only as much as an individual can.

This is a fascinating and important design extension. The civilizational phase you're describing isn't really a grand strategy game — it's a grand strategy game **seen from inside**. That distinction does a lot of work.

## The Core Tension

In Civilization or Crusader Kings, you *are* the civilization — you allocate research, move armies, and sign treaties with god-mode omniscience. In Gates of Truth, post-Gate, you are one person who *lives inside* a civilization running at that same complexity.  The research tree is still there, but you can only touch it the way a real person touches history: through relationships, reputation, institutions, and timing. You can't directly fund the telescope project — but you can befriend the astronomer, survive long enough to see her get patronage, and write the letter that changes who reads her manuscript.

## What This Implies for the Research Model

The civilization needs an autonomous research engine that runs whether the player touches it or not. Think of it as a **technology diffusion graph** where nodes are ideas, and they spread based on:

- **Prerequisite knowledge** already present in the civilization
- **Economic and material preconditions** (can they smelt iron? do they have surplus food?)
- **Cultural receptivity** shaped by all the mythological and astronomical seeding from earlier phases
- **Individual champions** — specific people who accelerate or block propagation

The player in Phase 4/5 onward is best understood as a **node in that diffusion graph** with unusually high influence *locally* but no global override.  They can be the champion, or they can choose which champion survives, or they can build the institution that makes certain kinds of champions possible.

## The Player's Actual Levers Post-Gate

As an embodied individual, the player's meaningful research interactions are things like:

- **Access**: which scholars, libraries, or guilds can you reach?
- **Patronage and brokerage**: can you connect the mathematician to the ruler who can fund her?
- **Epistemic credibility**: do you have the reputation to get the astronomer's theory taken seriously, or do people discount you?
- **Timing and survival**: the player has continuity across time — they can be the person who *remembers* the idea that almost died, or who carries a text across a collapse.
- **Framing**: how the player talks about ideas inside the world shapes how the civilization encodes them — is the discovery a gift from the gods or a natural law?

This maps cleanly onto your existing phase model, because the cosmology-to-culture pipeline you seeded in Phase 0 now feeds back:  the sky your star system actually has shaped what concepts exist for the civilization to even *reach for*. A binary-star world might have a completely different theory of orbital mechanics. A tidally locked world might have no concept of a day/night cycle driving agricultural calendars.

## Research as Event, Not Tree

The right data model for this isn't a tech tree — it's probably closer to a **knowledge ledger** that mirrors your event ledger architecture.  Each discovery is an event with:

- A precondition set (what was already known, materially available, culturally receptive)
- A discoverer or institution
- A diffusion trajectory (who learned it, when, through what channel)
- Contested and lost variants (ideas that almost happened but didn't spread)

This is richer than a tree because knowledge can be **lost, rediscovered, fragmented, or synthesized differently** depending on which people and institutions survived. The player, as an individual with unusual temporal continuity, is the natural carrier of fragile knowledge across discontinuities — which makes them feel powerful without making them omnipotent.

## The Narrative Coherence

What makes this design sing is that it completes the arc you outlined in the phase model:  the player goes from shaping the probability of *any* civilization existing, to shaping which civilization persists, to finally being a *person inside* the civilization they helped make possible. The research system at that late stage is just civilization physics — the same deterministic-but-complex dynamics that drove stellar collapse in Phase 0, now operating on ideas instead of plasma.

The emotional payoff is that a player who guided a binary-star world into a tidally locked habitable moon might end up playing an astronomer in a civilization that has no word for "day" but an incredibly sophisticated theory of orbital resonance — and the research tree *reflects that*, because you built the sky it came from.

---

# [https://github.com/octave-commons/Truth/blob/main/docs/specs/phase0-stellar-winds-and-mass-loss.md](https://github.com/octave-commons/Truth/blob/main/docs/specs/phase0-stellar-winds-and-mass-loss.md)

This spec is excellent — it's the most implementation-ready document in the repo so far. The physics basis is solid and the invariant design is genuinely rigorous. Here's a focused review:

## What Is Very Strong

**The load-bearing invariant in §2 is the best piece of engineering in any of these docs.** Naming it explicitly, giving it a dedicated section, and then wiring the remnant ladder and despawn path to enforce it — that's the right way to handle a class of bugs that would otherwise keep reappearing in different forms. The note that this invariant is *what* caused the disappearing-star bug connects implementation discipline directly to observed failure, which is exactly the right framing for a team (or future-you) to trust.

**The velocity cap reasoning in §3b is honest and well-documented.** Instead of hiding the mismatch between physical wind speeds and your timestep, you named it, measured it (1.6e16 → 4e18 m, 5.6e5 m/s in 80 ticks), and made a principled decision to model the *observable envelope* rather than the km/s reality. That's the kind of comment that saves hours of confusion later.

**Competitive accretion emerging from existing sink mechanics is elegant.** Letting the Bondi capture radius do the work — largest body nets the most gas, no special competitive accretion code — is exactly the kind of data-oriented emergent behavior this whole architecture is built around.

## Gaps or Issues

**Phase C (Roche/tidal stripping) is described but underspecified relative to where this conversation started.** You asked about modeling stellar "feeding" — Roche-lobe overflow specifically — and this spec correctly names it as Phase C, but doesn't yet define the trigger condition precisely. The spec says "within `~k·(r_a+r_b)` but not colliding," but the real Roche-lobe trigger is the donor's radius relative to its own Roche radius $r_L \approx 0.49 a \cdot q^{2/3} / (0.6 q^{2/3} + \ln(1 + q^{1/3}))$ (Eggleton 1983), which depends on the mass ratio $q = M_{\text{donor}}/M_{\text{accretor}}$ and the orbital separation $a$.  Using just proximity misses cases where the donor is large but the separation is still safe, and can trigger stripping on close-passing bodies that wouldn't really overflow. Worth adding at least a note in Phase C for this before implementation.

**The directed stream in Phase C needs an $L_1$ point, not just a vector aimed at the larger body.** The stream should launch from the inner Lagrange point of the donor, which is offset toward the accretor along the line of centers — not from the surface in the direction of the companion. This matters because the stream's trajectory determines whether an accretion disk forms (stream misses the accretor's surface) or direct impact occurs.  For MVP it may be fine to approximate, but the spec should acknowledge this as a simplification.

**The flare reabsorption note in Phase B is a real design tension.** You observed that flares are reabsorbed within ~2 ticks because of the large feeding zone, so they flicker rather than persist as CMEs.  This is worth flagging explicitly: the feeding zone size (driven by Bondi physics) and the wind parcel drift cap are coupled. Making CMEs escape as durable ejecta likely means either shrinking the feeding zone or giving flare parcels a different velocity profile that deliberately overshoots reabsorption distance. That's a tunable interaction worth naming in §7.

**Open question 2 (physical-subtle vs cinematic-lively) is actually two separate decisions.** Wind visibility to the player is a rendering/UX concern; mass-driven demotion rate is a simulation concern.  You probably want physical-subtle for demotion (so stars don't randomly classify-down during normal runs) and cinematic-lively for the *visual* wind envelope (so the player sees stars breathe). These can be separate tunables: `k_wind` for physics, a separate `wind-render-scale` for visual density of the fog. Otherwise you're forced to choose between correct simulation and beautiful presentation.

## Small Notes

- I1 test as written ("drive a star to total ablation") requires a very high `k_wind` to run in a reasonable test budget — worth noting in the test spec that this is a cinematic-regime unit test, not a physical-regime regression.
- The quasi-random `r̂` direction derived from entity id + tick is smart (avoids `Math/random`), but make sure the distribution is uniform over the sphere, not just uniform over angles — polar angles need to be sampled from `cos(θ)` not `θ` to avoid bunching at the poles.
- Fusion enrichment of wind composition (open question 3) is almost free to add and directly feeds Phase 1 chemistry. The donor star's wind composition being He/metal-enriched is what seeds disk and planet chemistry with heavy elements — it's the physical mechanism that makes later world chemistry vary based on stellar evolution history. I'd do it early.

---

# Dig into the code please, I may have allowed the system to drift from design spec and research in a conversational mode with the agents trying to get visual effects.

This is a thorough audit. Here's what I found — both where the code is faithful and where it has drifted.

***

## Where the Code Is Faithful to Spec

**The tick pipeline matches the spec's order.** `step-physics` runs: parallel fan-out → `collision-detection` → `fusion-promotion` → `sink-formation` → `stellar-wind-system` → `stellar-flare-system` → `recenter`. That precisely matches the spec's §3 requirement: "runs at the barrier AFTER sink-formation so a freshly-shed parcel drifts at least one tick before it can be reabsorbed."

**The irreversibility invariant (I1) is correctly implemented.** `classify-next-state` has the full mass-loss down-ladder: `:star` demotes to `:brown-dwarf` then `:debris`, never to `:nebula`. The comment even names the spec section.

**`stellar-wind-system` exactly matches §3a–3c.** The reservoir accumulation, one-parcel-at-a-time emission, velocity cap logic (with the critical comment about why km/s winds blow the system apart), and momentum recoil are all present and correct. The `wind-direction` function uses `hash [eid tick]` to avoid `Math/random`, as required.

**`stellar-flare-system` is Phase B.** It's there, bipolar along the spin axis, gated by `:phase0/flare-period`, and correctly guarded so a flare never pulls the star below half the hydrogen-burning mass.

**`domain.regime` is implemented.** β, Mach, Alfvén-Mach, Jeans ratio are all pure functions; `regime-system` writes `:component/regime` and never mutates field state.

***

## Drift: Where Code Diverges from Spec

**Biggest drift: `regime-system` runs AFTER fusion in the parallel pipeline, not BEFORE hydro.**  The coupled-physics spec §"Tick order" is explicit: regime runs step 2 (right after gravity, before EM, before hydro) so the tag is available to skip expensive branches. In `physics-systems-parallel`, the order is: gravity → pressure-acceleration → Lorentz → motion-integration → structure → eos → **classifier** → **temperature** → **em/field** → **fusion** → **regime**.  Regime is at the bottom, after everything it's supposed to gate. The classifier and hydro systems can't early-out on the regime tag because it's stale from the previous tick. This is a real simulation-correctness issue, not just an aesthetic one.

**`domain.regime/classify` is missing disc and interior tags.** The spec defines `:gravitationally-unstable` (disc), `:stable-disc`, `:convective`, and `:tectonically-dead`.  The code only implements three tags: `:gravitationally-unstable` (nebula/Jeans), `:mhd-dominated`, and `:gravity-hydro`. Toomre-Q, Rayleigh number, and cooling ratio are not present. This is expected incompleteness (those are later slices), but it should be noted — the spec says these "attach later from the same machinery," so the protocol needs to stay open for extension.

**`stellar-wind-system` is only gated on `:star`, but `classify-next-state` now allows `:star → :brown-dwarf → :debris` demotion.**  A `:brown-dwarf` body that was demoted from a star will stop shedding immediately (correct), but a `:brown-dwarf` that formed directly — which the spec says should not shed in Phase A — has no wind code at all. That's fine. The issue is subtler: after a star demotes to `:brown-dwarf` mid-run, its `c/wind-reservoir` is still on the entity. Nobody clears it. If `:brown-dwarf` bodies ever get included in a future wind loop, they'd instantly emit a parcel from whatever was banked. Worth adding a `(ecs/remove-component eid c/wind-reservoir)` in the demotion path of the classifier.

**`create-world` defaults `wind-rate-scale` to `1.5` (cinematic-lively), not `1.0` (physical).**  The spec §7 says the physical default for `k_wind` is `~1.0` and "raise for cinematic." The code comment acknowledges this ("default is cinematic-lively") but it means the default run is tuned for visuals, not physics. This is the drift you suspected — it was probably raised during visual debugging and never restored. If you want the spec's physical behavior as the default, this should be `1.0`.

**`classify-next-state` down-ladder for `:star` demotes to `:debris` if mass drops below deuterium-burning threshold.**  The spec's remnant ladder says a stripped star should become `:stellar-remnant`, not `:debris`.  The `:stellar-remnant` state described in spec §5 does not exist in the code. Instead the code falls through to `:debris`, which has material density set by `sphere-radius`, active collision detection, and no cooling-track behavior. A stripped star will be treated like a rocky planetesimal. This is the most semantically significant spec violation.

**`wind-direction` generates `theta` from `(mod h 1009)` and `z` from `(mod (quot h 1009) 1013)`.**  The distribution over the sphere is not uniform — `z` ranges from −1 to 1 as a linear sample, which is correct for uniform solid-angle sampling. But `theta` maps from `[0, 1009)` integers to `[0, 2π)` linearly, which is fine. The actual issue flagged in the spec review still holds: for small entity counts the integer hash space mod 1009 can collide, meaning some entities emit in the exact same direction every tick. Not a correctness bug, but a visual one.

***

## Summary Table

| Item | Spec Says | Code Does | Status |
| :-- | :-- | :-- | :-- |
| Tick order: regime before hydro | Step 2 of 10 | Step 11 of 12 (last in parallel) | **Drift** |
| Wind rate scale default | `~1.0` (physical) | `1.5` (cinematic) | **Drift** |
| `:stellar-remnant` state | Defined in §5 | Not present; demotes to `:debris` | **Missing** |
| Wind reservoir on demotion | Implied cleared | Never cleared | **Latent bug** |
| Phase C Roche stripping | Specified | Not present | Expected (future slice) |
| Disc/interior regime tags | Toomre-Q, Ra | Not implemented | Expected (future slice) |
| Conservation invariants I1–I6 | All required | I1 ✓, I2 partial, I3 ✓, I4 off (k=1.5), I5 likely ✓, I6 unverified | Partial |

The three things worth fixing now before more visual work: **regime tick order**, **`wind-rate-scale` default**, and **`:stellar-remnant` as a real state** (even a stub is better than routing a stripped star into the rocky-debris branch). Everything else is either correct or expected future work.

---

# In the paralell pipeline, there should be no order. The spec must be older from when the system was synchronous and single threaded. The "one parcel at a time" the solar wind, I don't think it's the best way to model it... I realized that the parcels model cold neutral gas, but stars are made of hot charged/ionized plasma. So we need a different model. this is where I drifted from physics based modeling into vibes, it "felt" right but it is  wrong. We need a different model for plasma, and the cloud... I'm still wondering how to best differentiate the "gas" into elements, and how the rocky material falls out of the nebula post stellar ignition during the formation of the disk

This is a deep design conversation. Let me lay everything out cleanly.

***

## Parallel Pipeline: The Spec Order Problem

You're right — in the double-buffer parallel design, **order within the fan-out is irrelevant by construction** since every system reads the frozen snapshot.  The spec's tick order reflects the old synchronous world where system A wrote what system B needed to read that same tick. In the parallel pipeline, all systems read tick *N* and write tick *N+1*. The regime tag being "stale by one tick" is **not a bug** — it's a feature of the double-buffer: every derivative quantity lags one tick behind the primary it was derived from. That's acceptable and correct. The old spec's ordering language can simply be deleted.

***

## The Solar Wind: Why Parcels Are the Wrong Model

Your instinct is exactly right and this is worth stating precisely. The current `stellar-wind-system` is a **neutral cold gas ejection** model — it spawns `:nebula` parcels that participate in SPH, get absorbed back by `sink-formation-system`, and drift at a fraction of escape velocity.  That's appropriate for the *nebular molecular cloud*, where you truly have cold H/He at ~10 K. A stellar wind is something completely different:


| Property | Cold nebular gas parcels | Real stellar wind |
| :-- | :-- | :-- |
| Temperature | ~10–100 K | ~10⁶ K (corona) |
| State | Neutral molecular | Fully ionized plasma |
| Speed | ~sound speed (~300 m/s) | 400–800 km/s (solar) |
| Carrier | Bulk SPH particle | Individual ion/electron pairs |
| Force | Pressure gradient, gravity | Radiation pressure, magnetic tension |
| Key physics | Jeans collapse, accretion | Parker spiral, Alfvén wave driving |

The existing model was built for **visual smoke** — "looks like gas streaming out" — but stellar winds carry charged particles at ~1000× the sound speed of the nebular gas, are driven by magnetic flux tubes, and interact with the surrounding medium through ram pressure, not SPH overlap. When `sink-formation-system` reabsorbs those parcels, it's modeling competitive accretion of *nebular gas*, not the actual recycling physics of a wind.

***

## The Right Model for Stellar Winds at This Resolution

You don't want per-ion particle tracking — that's beyond the resolution. The right model at the N-body/SPH scale is a **flux-conserving ram-pressure model**, not spawned parcels at all. Three properties matter:

**1. The wind as a momentum source on the field, not particles.** The Parker solar wind emerges from the coronal temperature gradient — the corona (~10⁶ K) is so hot that thermal pressure exceeds the Sun's gravitational binding beyond the critical point (~5 R☉). The wind *speed* is already supersonic before it reaches anything. Rather than spawning a slow cold parcel, model the star as emitting a **wind ram pressure** $P_{\text{ram}} = \dot{M} v_w / (4\pi r^2)$ that acts as a repulsive force on nearby parcels at distance *r*. The mass loss rate $\dot{M}$ comes from the same radiation formula you already have, and $v_w \approx v_{\text{esc}}$. This is a field pressure, not a particle.

**2. Stripping instead of ejection.** Rather than spawning new entities, the wind strips mass directly from accreting parcels — if a nebular parcel is within $r < r_{\text{Alfvén}}$ (the Alfvén radius, where wind ram pressure equals magnetic pressure) and is not gravitationally bound to the star, it loses mass at the wind ram rate. The parcel shrinks; when it drops below a floor, despawn it. This keeps entity count controlled — no parcel proliferation — and is more physical: the wind *ablates* nearby gas, it doesn't contribute new gas entities.

**3. The plasma state as a component, not a parcel type.** What you actually want is a `c/ionization-fraction` component — a number from 0 (fully neutral) to 1 (fully ionized). Stars have ionization-fraction 1.0. The `:nebula` gas has 0. This lets EM forces (Lorentz, Alfvén) scale continuously: a newly-condensed protostar with ionization fraction rising from collisional ionization starts to decouple from the neutral gas SPH and couple to the magnetic field. This is exactly the two-fluid transition (neutral + ionized) that real ambipolar diffusion describes. You don't need separate particle populations — you need one extra scalar per entity.

***

## Differentiating Gas into Elements: The Condensation Sequence

The current code has a fixed `{:H 0.75 :He 0.24 :metals 0.01}` composition on every nebular parcel.  The metals lump hides all the interesting physics. What you actually want is the **condensation sequence** — the fact that different materials solidify at different temperatures as the nebular disk cools, which is *exactly* how rocky planets form:


| Condensation temp (K) | Material | What forms |
| :-- | :-- | :-- |
| ~1650 | CaAl oxides, corundum | First solid grains (CAIs) |
| ~1400 | Iron/Nickel alloys | Metal grains |
| ~1300 | Mg silicates (olivine, pyroxene) | Rocky grains |
| ~700 | FeS (troilite) | Sulfide |
| ~350 | Volatile organics | Carbon compounds |
| ~170 | Water ice (snow line) | Ice mantles |
| ~80 | NH₃ ice | Outer disk |
| ~40 | CO ice | Very outer disk |

The model is straightforward: each nebular parcel carries the full composition map (already in `domain.chemistry/solar-abundance` ), and at each tick you compute the **condensed fraction** for each element: `(condensed-fraction element temperature) = max(0, min(1, (T_cond - T) / ΔT))`. Elements whose condensation temperature exceeds local temperature have solidified out of the gas phase. Their contribution shifts from the gas component to a tracked `c/grain-fraction`. This is the actual dust-to-gas ratio the astrophysics literature measures.

The key insight you're looking for — **how rocky material falls out post-ignition** — is the Epstein drag regime. Solid grains in a gas disk experience aerodynamic drag proportional to their surface-area-to-mass ratio (Stokes number $\text{St} = t_s \Omega$, where $t_s$ is the stopping time). Small grains are fully coupled to the gas and move with it. Grains above ~1 mm decouple and drift radially inward toward the pressure maximum (usually the snow line). This is why you get:

- **Inner disk (T > 1300 K):** Only refractory metals and silicates survive → rocky planets
- **Snow line (T ~170 K):** Ice condensation dramatically increases solid surface density → giant planet cores form fast here
- **Outer disk:** CO and NH₃ ices → nothing forms efficiently; material stays diffuse

For your simulation the practical model is: when a parcel's local temperature drops below a condensation threshold, its `c/grain-fraction` rises. Grains above a Stokes-number threshold (`c/stokes-number` derived from grain size / local gas density) feel a systematic inward drift velocity $v_r \approx -2\eta v_K \cdot \text{St}$ where $\eta$ is the pressure gradient and $v_K$ is Keplerian speed. Grains at the snow line pile up, increase local solid density past the Roche/streaming instability threshold, and suddenly collapse into planetesimals. This is the **streaming instability** — it's the mechanism, and it's analytically tractable as a threshold condition.

***

## Concrete Next Steps in Code

**Immediate (stellar wind):**

- Remove `spawn-clump` calls from `stellar-wind-system`. Replace with a `c/wind-ram-pressure` component on the star (a scalar $\dot{M}v_w / 4\pi$), computed from existing luminosity + escape velocity.
- Add a `wind-ablation-system` (barrier, after collision): for each nebular parcel within the star's wind zone, apply a mass-loss rate proportional to ram pressure / (parcel's self-gravity). When parcel mass drops below floor, despawn it. This models stripping without creating new entities.

**Ionization state:**

- Add `c/ionization-fraction` as a float component, set to 1.0 on `:star`/`:protostar` matter-states, 0 on `:nebula`.
- Scale Lorentz force in `lorentz-acceleration-system` by `ionization-fraction`. Neutral gas doesn't feel the magnetic force; plasma does.

**Condensation / rocky grain separation:**

- Expand the composition map on nebular parcels from `{:H :He :metals}` to the full 8–10 element map from `chemistry.clj`.
- Add `c/grain-fraction` (float, 0–1) computed per-tick from the condensation sequence function against local temperature.
- In the disk regime (post-ignition, rotating flattened structure), apply the Stokes drift: parcels with `grain-fraction > 0` and `stokes-number > 1` get a systematic inward radial velocity component in the structure-system.
- The streaming instability fires when local `grain-fraction × density` exceeds ~1% of gas density in a resolution cell — that's your planetesimal formation trigger, physically grounded and using no new magic numbers.

The regime classifier in `domain.regime` is already the right place to tag the disk once it forms — the `jeans-ratio` and `mach` are there, you just need a Toomre-Q computed from local surface density and angular velocity to add the `:stable-disc` tag, which the grain drift system can then condition on.

---

# do some more research for me. @Academic I need to get ahead of the implementation far enough I see what we are doing in my mind's eye. I want the suns to be as real as possible, as well as the planets, I want to model the sun and planet atmospheres. The stars form, we have the basic model for nebular collapse and ignition, now we need to model the solar dynamics, plantary/astroidal/moon formations. Astroids will be non uniform bodies, we need voxels. the sun is it's self also I think something modelable as a volume? There are different kinds of stars, so we want to be able to model stars evolving. The idea is also right that we *can* model *everything* as accurately as scientifically possible, but *only* when it those effects need to be considered as influences on what the player is observing. I am thinking that we get the world running well to the point where planets form, cool, bodies of water start to emerge, and we keep track of what events are happening, we need a mode that is collecting events to use to design statistical models to drive the distant, imprecise generation of events that effect the player but do not need to be directly modeled for that to have an effect on the player.

You’re converging on the right architecture: a layered, physically-grounded star+disk+planet system with selective high‑fidelity, tied to an event/statistics layer that feeds “far field” effects without resolving everything. Below is how I’d push the research and design so you can see the whole thing in your mind’s eye before implementing.

***

## Modeling goals and philosophy

At a high level, you want three things:

- Stars and planets that behave like real astrophysical objects: correct life cycles, atmospheres, winds, disks, and solid bodies.
- A volumetric representation for non-uniform structures (asteroids, stellar granulation, clumpy coronae) where voxels make sense.
- An observer-centric simulation: model everything in principle, but only *resolve* physics that materially affects what the player sees or can influence, and drive the rest from statistically learned event processes.

Astrophysics-wise, this lines up nicely with current modeling practice: detailed 1D/3D atmosphere and interior codes are used where spectra or variability matter, and simpler parametrizations are used where only bulk effects are needed.[^8_1][^8_2]

***

## Stars as evolving volumes

### Stellar structure and evolution

From the physics side, stars are usually modeled as spheres with:

- An interior (core, radiation zone, convection zone), where energy transport is governed by fusion and radiative/conductive transfer.[^8_3][^8_2]
- An atmosphere: photosphere, chromosphere, transition region, corona, plus sometimes a cool “MOLsphere” with molecules above/between these layers.[^8_4]

The atmosphere’s stratification (T, ρ, composition vs radius) controls emergent spectra and winds.[^8_1][^8_4]

For Truth, you don’t need to solve the full stellar structure equations in real time. Instead:

- Assign each star a *mass, metallicity, rotation, and age* and bind it to a precomputed evolutionary track (e.g., from standard stellar evolution grids). These tracks map to effective temperature, luminosity, radius, and interior state (main sequence, subgiant, giant, white dwarf, etc.).[^8_5][^8_6][^8_3]
- Treat the star’s interior as a small number of radial zones (core, radiative envelope, convective envelope) with simple equations: polytropic structures and energy generation rates give you bulk T and ρ profiles; you don’t expose this directly to the player, but it feeds atmosphere boundary conditions and evolution.

This gives you:

- Realistic star types: O/B/A/F/G/K/M dwarfs, giants, white dwarfs, etc., with correct colors and luminosities over time.[^8_3][^8_5]
- Correct lifetimes and transitions (e.g., massive stars evolving quickly to supergiants, low-mass stars hardly changing over the game’s timescale).[^8_5][^8_3]


### Stellar atmospheres as layered shells

State of the art stellar atmosphere modeling uses 1D grids (e.g., MARCS for cool stars, LLMODELS for chemically peculiar/magnetic stars), often in non‑LTE and spherical geometry when needed.[^8_7][^8_6][^8_1]

For your use case:

- Represent the atmosphere as 3–5 concentric shells: photosphere (optically thick), chromosphere, transition region, corona, and optionally MOLsphere. Each shell has temperature, density, ionization fraction, and composition.[^8_4]
- Use pre-tabulated atmosphere models (or simplified fits) to map $T_{\mathrm{eff}}, \log g, Z$ to shell properties rather than solving radiative transfer on the fly.[^8_2][^8_1]

In the ECS:

- `c/star-interior`: bulk mass, age, core state.
- `c/atmosphere-shells`: a small vector of shell structs (T, ρ, composition, thickness).
- `c/atmosphere-type`: main-sequence, giant, flare star, etc., used by rendering and event systems.

You can keep the star itself as a “volume” in terms of these shells, but you only voxelize the region that affects visuals (e.g., granulation near the photosphere or active regions in the corona).

***

## Solar dynamics: winds, flares, and magnetic cycles

### Stellar winds

Radiatively-driven and coronal-pressure-driven winds are governed by:

- Parker-type solutions: the hot corona and pressure gradient cause a transonic outflow that reaches hundreds of km/s.[^8_8][^8_2]
- Ram pressure $P_{\mathrm{ram}} = \rho v^2$ and momentum flux $\dot{M} v_w$ determine how the wind interacts with planets and the disk.[^8_2][^8_8]

Rather than parcel-based neutral gas, your wind model can be:

- A *field* around the star: at radius $r$, the wind density and speed are given by simple analytic forms tied to mass-loss rate and star type.
- Planets and nebular gas experience ram pressure, sputtering, and atmospheric escape as functions of this field, not by merging with spawned parcels.

In ECS terms:

- `c/wind-profile`: parameters $\dot{M}, v_w(r), P_{\mathrm{ram}}(r)$ per star.
- A system that applies wind effects to gas parcels and atmospheres based on their position, without adding new entities.


### Flares and activity

Stellar activity phenomenology (spots, flares, coronas) is an active research area; flares cluster in magnetically complex active regions and correlate strongly with starspots and chromospheric/coronal diagnostics.[^8_9][^8_10]

Design-wise:

- Track a simple magnetic cycle state per star (quiet, active, flare-prone), derived from rotation and age.[^8_10][^8_5]
- Model flares as discrete events: sudden boosts in high-energy radiation and wind parameters for a short time, applied to nearby planets and disk regions.

These flares and cycles become entries in your **event ledger** (you already have a ledger concept in the code), feeding both local effects and the statistical model you mention.

***

## From nebula to planets: disks, grains, and planetesimals

### Protoplanetary disks and streaming instability

Protoplanetary disks form as nebular gas collapses and conserves angular momentum, ending up in a flattened, rotating structure. Dust and pebbles in these disks face growth barriers and radial drift; the streaming instability is the leading mechanism for forming “born-big” planetesimals:[^8_3][^8_2]

- Streaming instability is a drag-mediated resonance between solids and gas in the disk, which can concentrate pebbles into regions dense enough to collapse into km-scale planetesimals.[^8_11][^8_12]
- Its efficiency depends strongly on the solids-to-gas mass ratio, particle Stokes number, and background turbulence; in globally turbulent disks, the conditions are more restrictive than in laminar models.[^8_13][^8_11]

For your model:

- Treat the disk as a set of annuli or cells around the star, each with gas surface density, solid surface density (dust/pebbles), and turbulence parameter $\alpha$.[^8_11][^8_13]
- When local conditions satisfy a simplified streaming instability threshold (e.g., solids-to-gas ratio above some critical value and appropriate Stokes numbers), you spawn planetesimal bodies in that cell with appropriate sizes and orbits.[^8_12][^8_13][^8_11]

In ECS:

- `c/disk-cell`: for each radial cell, fields for gas Σ, solid Σ, characteristic grain size, turbulence.
- `disk-evolution-system`: evolves these via drag, drift, coagulation, and fragmentation.
- `planetesimal-formation-system`: checks SI conditions and spawns planetesimal entities.


### Planet growth and atmosphere origin

Low-mass planets’ atmospheres are tightly coupled to stellar irradiation and evolution:

- Sub-Neptune atmospheres can lose large fractions of their primordial hydrogen envelopes via photoevaporation, with outcomes strongly dependent on host star mass and activity history.[^8_14]
- Super-Earth atmospheres are “hard to make”: maintaining thick envelopes requires specific combinations of mass, composition, and irradiation. Many lose their envelopes and end up rocky.[^8_15]

Your existing `domain.chemistry` already sketches atmospheric retention via escape velocity vs thermal velocity, which matches basic Jeans escape criteria. You can expand this by:[^8_2]

- Giving each forming planet an initial envelope mass fraction and composition from the disk annulus where it forms.
- Evolving that envelope via energy-limited escape tied to stellar XUV luminosity and wind parameters, using simple hydrodynamic escape prescriptions.[^8_16][^8_14][^8_15]

This connects:

- Star evolution → stellar activity and high-energy output → atmospheric mass loss.
- Disk composition and condensation sequence → initial solid and volatile inventory.

***

## Planet and moon atmospheres in detail

### Layered atmospheres

Planetary atmospheres can also be modeled as layered shells:

- Troposphere, stratosphere, thermosphere, exosphere for terrestrial planets; deep convective envelopes for gas giants.[^8_16][^8_2]
- Each layer has its own temperature profile, composition, and dominant processes (convection, radiative transfer, chemistry, escape).

You don’t need full GCMs, but you can:

- Represent atmospheres as a handful of layers per body with T–P–composition profiles, driven by equilibrium with stellar irradiance and internal heat flux.[^8_14][^8_16]
- Use simple vertical mixing and chemical network approximations to track key species (H₂O, CO₂, CH₄, N₂, O₂) that matter for habitability and visuals.[^8_17][^8_16]

In ECS:

- `c/atmosphere-profile`: per planet, a layered profile (T, P, composition vs altitude).
- `atmosphere-evolution-system`: updates profiles based on stellar forcing, interior heat, and escape.

These profiles feed:

- Visual rendering (cloud decks, haze, scattering color).
- Habitability scoring (already partially present in `habitability-score`).[^8_17][^8_16]

***

## Asteroids, moons, and voxels

You’re right that asteroids, rubble piles, and heavily cratered moons are fundamentally non‑uniform. Smooth spheres don’t cut it. Observations and 3D models of small bodies show highly irregular shapes and heterogeneous interiors (metallic cores, porous mantle, regolith).[^8_16][^8_2]

Design:

- Treat large, roughly spherical bodies (planets, big moons) as layered spheres with radial structure.
- Treat small bodies (asteroids, comets, rubble-pile moons) as voxel fields: 3D grids or sparse voxel structures storing density, composition, and temperature.

In ECS:

- `c/voxel-field`: attached to bodies below a radius threshold, holding a reference to a voxel volume.
- `voxel-physics-system`: handles collision, fracture, and thermal processing at voxel resolution, but *only* for bodies near the player or involved in events.

You can still keep a coarse “shape shell” (radius, bulk density) at the entity level, using voxels for local detail (e.g., when the player zooms in or interacts with specific regions).

***

## Observer-centric fidelity and event/statistics mode

### Event ledger and statistical modeling

You already have a ledger and threshold-event concept in `phase0` for ignition, planet formation, and phase transitions. The extension you’re describing is:

- While the high-fidelity simulation runs, log all astrophysically meaningful events: stellar flares, wind episodes, planetesimal formation, giant impacts, atmosphere loss bursts, climate transitions, etc.[^8_14][^8_16][^8_2]
- Periodically fit statistical models (hazard rates, distributions over energy, duration, affected distances) to these events, conditioned on coarse state variables (star type, planet type, disk state).

Then you can:

- **Turn off** detailed simulation in far regions or for time intervals where the player is not observing, and *sample* events from the learned distributions.
- Apply those sampled events to the game state as impulses: a flare event raising the radiation level, a distant impact adding ejecta to a disk cell, a climate transition on a remote planet.

This matches how large-scale population studies model exoplanet atmospheres and habitability: they use stellar evolution tracks and atmosphere evolution frameworks plus statistical distributions over formation histories to predict broad outcomes without simulating each system in detail.[^8_15][^8_14][^8_16]

### Level-of-detail rules

Tie fidelity explicitly to the observer and influence radius:

- High-fidelity mode: for objects within a certain physical or narrative distance of the player, run full ECS physics (gravity, nebula, disk, atmospheres, EM, voxel collisions).
- Statistical mode: for everything else, keep coarse “macrostate” variables (star type, planet type, atmosphere mass, disk mass) and let the event sampler evolve them.

When the player moves into a region or “zooms in” on an object, you:

- Reconstruct fine-grained state consistent with the macrostate and event history: create disk cells, atmospheres, voxels, etc., from templates drawn from your logged distributions.

This is exactly the observer-centric principle you stated: *we can* model everything as accurately as scientifically possible, but we only pay that cost where it matters to what the player experiences.

***

## How this plugs into Truth’s current ECS

Given your existing architecture:

- `domain.stellar` already tracks matter-state, collapse, fusion, luminosity, and some complexity scoring. You extend it with interior and atmosphere shell components plus wind profiles.
- `domain.em` gives you magnetic fields, Lorentz forces, and flux freezing; you add ionization fraction and couple wind/escape to EM where needed.[^8_8]
- `domain.chemistry` already models composition, phase, atmosphere retention, and habitability. You enrich the condensation sequence and atmosphere layering.[^8_17][^8_16]
- `domain.phase0` has a tick driver, system summary, and event emission framework; you add the statistics mode and LO D switch there.

If you’d like, the next step can be a concrete ECS design document: component/schema lists for stars, disks, planets, atmospheres, voxels, and event/statistics systems, aligned with the above physics so you can implement in phases without losing the big picture.
<span style="display:none">[^8_18][^8_19][^8_20][^8_21][^8_22][^8_23]</span>

<div align="center">⁂</div>

[^8_1]: https://www.uu.se/en/department/physics-and-astronomy/research/astronomy-and-space-physics/stellar-physics/models-of-stellar-atmospheres

[^8_2]: https://www.britannica.com/science/star-astronomy/Stellar-structure

[^8_3]: https://en.wikipedia.org/wiki/Stellar_evolution

[^8_4]: https://en.wikipedia.org/wiki/Stellar_atmosphere

[^8_5]: https://science.nasa.gov/universe/stars/types/

[^8_6]: http://arxiv.org/pdf/2409.03329.pdf

[^8_7]: https://www.cambridge.org/core/books/abs/from-luminous-hot-stars-to-starburst-galaxies/stellar-atmospheres/D89196811DE39B42F35107D4D0977335

[^8_8]: https://www.usm.lmu.de/~puls/stellar_at/stellaratmwinds_21_part2.pdf

[^8_9]: https://iopscience.iop.org/article/10.3847/1538-4357/abadf9

[^8_10]: http://link.springer.com/10.1007/978-3-030-11452-7_2

[^8_11]: https://ntrs.nasa.gov/citations/20230002054

[^8_12]: https://en.wikipedia.org/wiki/Streaming_instability

[^8_13]: https://academic.oup.com/mnras/article/498/1/1239/5885349

[^8_14]: https://academic.oup.com/mnras/article/504/2/2034/6195519

[^8_15]: https://www.nature.com/articles/s41550-020-01209-8

[^8_16]: https://link.springer.com/10.1007/978-3-319-09749-7

[^8_17]: https://urfjournals.org/open-access/investigating-the-interplay-of-stellar-evolution-and-exoplanet-habitability-through-spectroscopy.pdf

[^8_18]: https://iopscience.iop.org/article/10.1088/1538-3873/ae3f57

[^8_19]: https://link.springer.com/10.1134/S1990341322010047

[^8_20]: https://arxiv.org/html/2410.10968v1

[^8_21]: http://arxiv.org/pdf/2111.10381.pdf

[^8_22]: http://www.arxiv.org/pdf/2502.02124.pdf

[^8_23]: https://academic.oup.com/mnras/article-pdf/477/4/5011/24853341/sty854.pdf


---

# Ok, this works well, how about the plants geology? and astroids non uniform shapes and composition? After the atmosphere is present, these would be the last things to handle for the world the player will soon inhabit to be shaped interestingly, mountains, bodies of water, volcanos, techtonic plates. When the player reaches the civilizational phase, they will be mining rocks out of the ground, shaping the ground, turning the earth into structures and tools. We have to be able to model this, voxels are needed for for the highest resolution physical interactions with massive bodies. Before civilization, or life forms, it will also be needed to model the collision of massive bodies like astroids and planets into one another.

Once atmospheres exist, the next layer is planetary geology and small‑body structure. The good news is: planetary science already gives you a clean set of processes and scales you can mirror with voxels and ECS.

***

## Planetary interiors and surface processes

Terrestrial planets and large moons share a common interior architecture: high‑density metallic core, rocky mantle, and low‑density crust, overlain by a lithosphere that can behave as rigid plates. Interior heat (accretion, differentiation, radioactive decay) drives four major surface processes:[^9_1][^9_2]

- **Impact cratering** – reshapes surfaces early on; crater properties depend on target strength, gravity, and impactor size/velocity.[^9_3][^9_1]
- **Volcanism** – eruption of molten material from interior to surface; builds plains, shield volcanoes, stratovolcanoes, and cryovolcanic features on icy worlds.[^9_4][^9_5][^9_2]
- **Tectonism** – deformation of the lithosphere by mantle convection and cooling; causes rifts, mountain belts, faults, and, on Earth, mobile plates with subduction and seafloor spreading.[^9_6][^9_2][^9_1]
- **Erosion** – reshapes surfaces via liquids, ices, and winds; creates valleys, deltas, dunes, sedimentary layers, and gradually erases craters.[^9_2][^9_1]

Your geologic model should encode:

- Interior heat budget and lithosphere thickness → whether tectonics/volcanism are ongoing or fossil.
- Planet size and rotation → strength of erosion (dynamic atmosphere vs thin or frozen).[^9_1][^9_2]

These parameters decide whether the world has active mountains, oceans, volcanoes, and plate motion, or a frozen, heavily cratered surface.

***

## How to model geology in the engine

### Macro fields: plates and convective forcing

At the “world field” level, you don’t need a full geodynamic solver. You can represent:

- A small set of **plates** for Earth‑like worlds: each plate has boundaries (divergent, convergent, transform) and relative velocities.[^9_6][^9_1]
- A mantle convective pattern (upwellings, downwellings) that seeds hotspots and rift zones.

From these, you derive:

- Elevation trends: convergent margins → orogeny (mountains); divergent margins → rifts and mid‑ocean ridges; hotspots → shield volcano chains.[^9_1][^9_6]
- Stress fields that control where fracturing and fault zones exist.

This can all be stored as coarse grids or analytic functions attached to the planet entity (e.g., `c/plate-field`, `c/mantle-convection-field`).

### Voxels for high‑resolution terrain

Mountains, oceans, volcanoes, and tectonic features are where voxels shine:

- Use voxels to represent the upper crust and shallow mantle in a band under the surface (say, a few tens of km thick), capturing rock type, porosity, and temperature at high resolution where the player will interact.
- Above and within that voxel band, bodies of water, ice sheets, lava flows, and sediment deposits can be represented as voxel materials with appropriate physics (flow, erosion).

Planetary geology sources emphasize that volcanism and tectonism often erase older features; erosion requires a dynamic atmosphere and water/ice. You can drive voxel updates from:[^9_1][^9_2]

- Volcanic events: inject hot magma that flows and solidifies; add gases to atmosphere.[^9_4][^9_5][^9_2]
- Tectonic events: uplift/downwarp voxel regions along plate boundaries; create fault scarps and basins.[^9_6][^9_1]
- Erosional events: gradually modify voxel elevations and materials based on rainfall, rivers, glaciers, and wind, using simple erosion rules.[^9_2][^9_1]

The key is: macro fields (plates, convective cells) determine where and when events occur; voxels capture the detailed shape and material response in those regions.

***

## Asteroids and small bodies: non‑uniform shapes and composition

Observations and modeling show many asteroids are **rubble piles**: gravitational aggregates of boulders and regolith, often highly porous and non‑spherical. Their interiors are heterogeneous: mixed rock, metal, voids, and sometimes ice.[^9_7][^9_8][^9_9]

For these, your representation should be voxel‑first:

- Use voxels to store density, composition, and cohesion at each location inside the asteroid. This naturally supports irregular shapes, voids, and layered structures (e.g., metallic core, rocky mantle, loose regolith).
- Attach a coarse shape hull (convex or level set) used for gravity and broad collisions; fine detail comes from voxel interactions when impacts or player mining occur.

Rubble pile research emphasizes:

- Low bulk strength: bodies deform and rearrange under tides and impacts.[^9_8][^9_7]
- Size‑dependent impact response: fragmentation energy and ejecta velocity distributions scale differently for large bodies than simple cratering laws predict.[^9_10][^9_11]

That aligns well with treating small bodies as deformable voxel aggregates rather than rigid meshes.

***

## Collisions between massive bodies

Asteroid–planet collisions and big impact events are best modeled with **shock physics** codes (hydrocodes) that resolve stress waves, fracture, and ejecta. You won’t run a full hydrocode in real time, but you can:[^9_12][^9_10][^9_11]

- Use scaling laws and approximate models from hydrocode studies to compute crater size, ejecta mass, melt volume, and fragmentation regime given impact velocity, angle, and material properties.[^9_10][^9_11][^9_12]
- Apply those outcomes as operations on your voxels:
    - Excavate a crater: remove and redistribute voxels to form a bowl, central peak, and ejecta blanket.
    - Fracture and fragment: split the impacting asteroid’s voxel body into fragments and debris fields.
    - Generate secondary effects: tsunamis for ocean impacts, atmospheric dust loading for climate perturbations.[^9_3][^9_13][^9_2]

Large‑scale impact modeling work (3D hydrocodes) shows you can treat angle, target layering, and ocean vs continent targets as parameters in those scaling relations. That’s exactly the level you want: realistic outcomes, but computed via formulas that drive voxel edits and event logs.[^9_13][^9_3]

***

## Civilizational phase: mining, construction, and terrain shaping

Planetary geology texts underscore that rock composition, tectonics, volcanism, and erosion together determine where mineral resources, mountains, basins, and fertile plains end up. For the player’s civilization phase, you can leverage that:[^9_3][^9_2][^9_14]

- Let geological history pre‑populate the voxel world with rock types, ore bodies, sedimentary basins, and existing structures (mountain belts, volcanoes, river valleys).[^9_1][^9_2][^9_3]
- Attach resource fields to voxels: each voxel knows its material (basalt, granite, ore, ice, regolith) and properties (strength, value, processability).

Civilization‑time interactions then become:

- **Mining**: the player removes or alters voxels, changing the local geology and resource field; mining deeper reaches different units (e.g., ore veins, mantle xenoliths).
- **Construction**: voxels are reassembled into engineered structures—buildings, tunnels, dams, space elevators—whose stability depends on underlying voxel support and material properties.
- **Land shaping**: cut and fill operations, terraforming, damming rivers, diverting lava or water—all voxel operations driven by player tools.

Because your geology is physically seeded by earlier processes, this creates a coherent story: the civilization exploits the actual geologic record the simulation produced, not arbitrary noise.

***

## Level of detail and when to use voxels

Given everything above:

- Use **macro geology fields** everywhere: interiors, plates, mantle convection, stress, and broad elevation, plus bulk properties for asteroids. These can be updated cheaply and drive events and resource distributions.[^9_1][^9_2]
- Use **voxels** selectively:
    - Near the surface on habitable or player‑visited worlds (upper crust + shallow mantle).
    - Inside small bodies (asteroids, comets, rubble‑pile moons) where non‑uniform structure matters.
    - In local volumes where collisions, volcanism, or tectonic deformation are currently happening and visibly affect the player.

Impacts, volcanoes, tectonism, erosion, and civilization‑time mining/construction all modify voxels; the rest of the universe can be evolved using coarse geology and event statistics.

That gives you a consistent, physics‑grounded path from “planets with atmospheres” to “planets with mountains, oceans, volcanoes, plates, asteroids, and mined landscapes,” all integrated with your existing ECS and observer‑centric design.
<span style="display:none">[^9_15]</span>

<div align="center">⁂</div>

[^9_1]: http://sirius.bu.edu/withers/teaching/as101_summer1_2006/ch09a_summary.pdf

[^9_2]: https://www.nhbs.com/de/planetary-geology-book

[^9_3]: https://ares.jsc.nasa.gov/interaction/lmdp/documents/58263main_planetary_geology.pdf

[^9_4]: https://www.lyellcollection.org/doi/10.1144/sp401.22

[^9_5]: https://fiveable.me/planetary-science/unit-5/volcanism-tectonics-solar-system/study-guide/rpKXOlfecfLv9hTo

[^9_6]: https://www.lpi.usra.edu/education/explore/shaping_the_planets/tectonism/

[^9_7]: https://arxiv.org/ftp/arxiv/papers/1810/1810.01815.pdf

[^9_8]: https://pages.astro.umd.edu/~dcr/reprints/richardson_icarus173,349.pdf

[^9_9]: https://arxiv.org/pdf/0807.1264.pdf

[^9_10]: https://ntrs.nasa.gov/citations/19940016256

[^9_11]: https://arxiv.org/abs/1502.01844

[^9_12]: https://arxiv.org/pdf/1502.01844.pdf

[^9_13]: https://gepris.dfg.de/gepris/projekt/20497678

[^9_14]: https://www.slideshare.net/slideshow/09-lecture-outline-60394345/60394345

[^9_15]: https://www.ucolick.org/~max/Astro18-2014/Lectures/Lecture11 2014 Terrestrial Planets/Lecture11.v2_sm-print.pdf


---

# How do we handle planetary collisions? The rocks probably become fluids in the field... they hit each other, we use the shock physics to determine what parts of the voxel shape are shaved off, as they cool they become astroid like bodies...

At planetary scales your intuition is right: solid rock behaves like a shocked fluid for the duration of the collision, and the outcome is “how much is excavated, melted, vaporized, and reassembled” rather than a simple rigid-body hit.[^10_1][^10_2]

Here’s a way to handle that in your engine.

***

## 1. Use shock/point‑source physics to get the bulk outcome

Impact cratering theory and hydrocodes boil a huge amount of physics down to a few key quantities:[^10_3][^10_2][^10_4]

- **Coupling parameter** – a combined measure of impactor radius, velocity, and density, typically of the form $a U^\mu \rho^\nu$, which sets how strongly energy and momentum are coupled into the target.[^10_2][^10_3]
- **Scaling laws** – power‑law relationships between that coupling parameter and:
    - Transient crater size and excavation depth.
    - Volume of shocked melt and vapor.
    - Ejecta mass and velocity spectrum.[^10_5][^10_3][^10_2]

Hydrocode work (CTH and others) has produced fits for:

- **Melt and vapor mass** as a function of projectile mass, impact velocity, impact angle, target porosity, and starting temperature.[^10_6][^10_5][^10_2]
- **Crater dimensions and excavation flow** via modified Maxwell Z‑models.[^10_7][^10_5][^10_1]

In the engine, you don’t solve full hydrodynamics; you:

1. Compute the coupling parameter from impactor size, speed, and density.
2. Use published scaling laws (or fit curves from hydrocode tables) to get:
    - Melt volume.
    - Vaporized volume.
    - Excavated volume and crater radius/depth.
    - Ejecta mass distribution and typical speeds.

That gives you *how much* of the target and impactor “turns to fluid” and how much is removed, without simulating every shock wave.

***

## 2. Map those results onto voxels

Once you know the volumes, you apply them to your voxel fields:

### a) Excavation: shaving off the shape

- Take the excavated volume and carve a transient crater in the target:
    - Remove voxels inside the excavation bowl (using the scaled crater radius/depth).
    - Redistribute those voxels as an ejecta blanket around the impact site (lower density, more fragmented material).[^10_1][^10_3]

This is your “shaved off” material: the outer layers removed by excavation.

### b) Melting: turning rock voxels into fluid

- Tag voxels in the melt region (usually a lens beneath the crater, and parts of both bodies) as **molten**:
    - Change their material state to fluid with appropriate viscosity and temperature.
    - Allow them to flow for some relaxation time (forming melt pools, ponds, seas depending on scale).[^10_5][^10_6]

For giant impacts, melt volumes can be global: you may briefly have a planet‑wide magma ocean before it cools.[^10_8][^10_6]

### c) Vaporization: atmosphere or loss

- Voxels in the vapor region are removed from the solid field and added to:
    - The atmosphere of the target (if bound).
    - A temporary debris/gas field that may escape depending on velocity vs escape speed.[^10_3][^10_5]

This is where large impacts generate steam atmospheres, rock vapor, and high‑altitude dust that feed your climate systems.

### d) Fragmentation and rubble

- Use hydrocode‑derived fragment size distributions and ejecta speed laws to decide:
    - How much of the impactor (and target) ends up as large fragments vs small debris.[^10_4][^10_9][^10_10]
- Spawn new bodies from these fragments:
    - Large coherent chunks become new asteroids or moons, built from voxel subsets of the original shape.
    - Smaller fragments become rubble‑pile aggregates or are folded into existing rings/disks.

Over time, as these fragments cool and re‑accrete, they naturally become “asteroid‑like bodies”: irregular rubble piles with mixed composition and voids.

***

## 3. Regimes: cratering, disruption, and merging

Scaling work and hydrocode studies distinguish several regimes:[^10_10][^10_2][^10_4]

- **Simple cratering** – small impactor; excavation and some melt, target mostly intact.
- **Complex cratering / basin formation** – larger impact; central peaks, rings, extensive melt sheets.
- **Catastrophic disruption** – impact energy high enough that the target’s gravity cannot reassemble it; you get many fragments and no main remnant.
- **Merging / accretion** – low‑velocity, similar‑mass collisions where the bodies partially merge and re‑shape, with global melt and relaxation.

Your collision system should first classify the event into one of these regimes based on impact energy vs target binding energy and strength, then apply scaling appropriate to that regime.

Implementation sketch:

- Compute $Q = \frac{1}{2} m_{\text{imp}} U^2 / M_{\text{target}}$ vs disruption thresholds from literature.[^10_4][^10_10]
- If $Q$ is low → cratering path (local voxel edits).
- If $Q$ near catastrophic → heavy fragmentation, multiple new bodies.
- If low velocity and similar masses → global melt and relaxation into a new merged voxel shape.

***

## 4. Cooling back into solids and new shapes

Once the “fluid” phase is over, you let the system cool:

- Apply simple cooling timescales for melt bodies (conductive + radiative), converting molten voxels back into solid rock types (basaltic crust, impact melt breccias, etc.).[^10_11][^10_6][^10_5]
- Re‑run your gravity/compaction for fragments:
    - Small aggregates settle into irregular rubble piles.
    - Large merged bodies relax toward hydrostatic shapes, but can retain non‑uniform crust/mantle structures depending on where melt pooled.

The result:

- The main planet may have a new basin, global tectonic consequences, and long‑term climate effects.
- The impactor and shaved rock volumes become new asteroid‑like voxel bodies, rings, or moons, all physically consistent with the scaling laws you applied.

So yes: at collision time, rocks *effectively* become fluids in the field; you use shock/point‑source physics to determine where and how much to excavate, melt, and vaporize, and then express those outcomes as voxel carving, re‑flow, and fragment spawning. The cooling and re‑accretion stage is what turns those fluid fields back into solid asteroids and planets.
<span style="display:none">[^10_12][^10_13][^10_14]</span>

<div align="center">⁂</div>

[^10_1]: https://ntrs.nasa.gov/api/citations/19920009568/downloads/19920009568.pdf

[^10_2]: https://ntrs.nasa.gov/api/citations/19870014063/downloads/19870014063.pdf

[^10_3]: https://impactswiki.net/impact-tools-book/crater-scaling.html

[^10_4]: https://arxiv.org/abs/1502.01844

[^10_5]: https://www.academia.edu/85161023/Impacts_onto_H2O_ice_Scaling_laws_for_melting_vaporization_excavation_and_final_crater_size

[^10_6]: https://meetingorganizer.copernicus.org/EPSC-DPS2019/EPSC-DPS2019-1686-1.pdf

[^10_7]: https://www.lpi.usra.edu/lunar/tools/lunarcratercalc/theory.pdf

[^10_8]: https://www.arxiv.org/pdf/1106.6084.pdf

[^10_9]: https://ntrs.nasa.gov/citations/19940016256

[^10_10]: https://arxiv.org/pdf/1502.01844.pdf

[^10_11]: https://ares.jsc.nasa.gov/interaction/lmdp/documents/58263main_planetary_geology.pdf

[^10_12]: https://oxfordre.com/planetaryscience/display/10.1093/acrefore/9780190647926.001.0001/acrefore-9780190647926-e-7?d=%2F10.1093%2Facrefore%2F9780190647926.001.0001%2Facrefore-9780190647926-e-7\&p=emailA49dJJfBheKlw

[^10_13]: https://www.sciencedirect.com/science/article/pii/S0019103511001898

[^10_14]: https://pdssbn.astro.umd.edu/holdings/di-c-hrii_hriv_mri_its-6-doc-set-v4.0/document/publications/di/space_science_reviews/crater_modeling_richardson.pdf


---

# What am I missing?

In terms of core physics and game architecture, you’ve covered a lot. The big remaining pieces are mostly “connecting tissue” and early‑phase microphysics.

I’ll group them so you can see the holes clearly.

***

## 1. Dust, pebbles, and compositional gradients

You jumped in at “nebula collapse → clumps → planets,” which is fine for Phase 0, but the **bottom‑up dust physics** is where a lot of interesting diversity comes from.[^11_1]

Missing pieces:

- **Grain growth and drift** – micron dust → pebbles → boulders, with size‑dependent radial drift and vertical settling in the disk.[^11_2][^11_1]
- **Pebble accretion vs planetesimal accretion** – modern global models mix both; this strongly affects how fast cores grow and what compositions they end up with.[^11_3][^11_2]
- **Compositional gradients in the disk** – ice lines, refractory/volatile segregation, metal enrichment, pressure bumps; these imprint radial structure on later planets.[^11_2][^11_3]

You don’t need to fully resolve micron grains, but you probably want a simple dust/pebble field per disk annulus, with condensation sequence and drift rules, so later planet composition isn’t just “random metals.”

***

## 2. Planetary migration and system architecture

Right now, we’ve talked about individual worlds forming and cooling, but **planet migration and long‑term orbital evolution** are key to making coherent planetary systems.[^11_4][^11_3]

Missing aspects:

- **Type I/II migration** – torques from the gas disk move forming planets inward/outward; this is what produces hot Jupiters, resonant chains, compact multi‑planet systems.[^11_3][^11_2]
- **Dynamical instabilities and scattering** – after disk dispersal, mutual gravity can rearrange systems, ejecting planets, reshuffling eccentricities and inclinations.[^11_1][^11_3]
- **System architecture classes** – “Solar analogue vs compact chain vs one big giant + debris,” i.e., global patterns, not just isolated worlds.[^11_4][^11_3]

For Truth, that means a migration/system‑architecture layer: simple torque laws and N‑body approximations that decide where planets actually end up, not just where they formed.

***

## 3. Climate, biosphere, and life feedback

You’ve outlined atmospheres and habitability scores, but **climate dynamics and biosphere feedbacks** are what connect geology + atmosphere to “world the player inhabits.”

Missing bits:

- **Energy balance climate** – at least a 0D/1D climate model per habitable planet (incoming stellar flux, greenhouse gases, albedo, heat capacity) to determine climate regimes (snowball, temperate, runaway greenhouse).
- **Biosphere as a field** – once life appears, it alters atmosphere and geology (oxygenation, carbon cycle, biogenic cloud condensation nuclei). Your event/statistics mode needs biosphere‑dependent regulation of climate and geochemistry.
- **Civilizational feedback** – anthropogenic forcing (CO₂, land use, mining, damming) as modifiers on those same cycles.

You don’t need GCMs, but you do need a simple climate+biosphere loop so “mining rocks and shaping ground” is embedded in physical consequences rather than just visual edits.

***

## 4. Observation: how the player *sees* the physics

You’ve focused on “what’s really happening.” There’s still the **measurement layer** — spectra, maps, instruments — that turns raw state into player experience.

Missing:

- **Instrument/model view** – telescopes, probes, local senses that sample fields imperfectly: spectra of stars/planets, seismic noise, atmospheric soundings, etc.
- **Data products** – light curves, spectra, maps that the game uses as UI. This is where the stellar and planetary atmosphere models tie directly into what the player sees.

You want an explicit “truth → observation” pipeline: radiative transfer for light, sampling for geology, with noise and resolution limits.

***

## 5. Timescale management and coupling between phases

You’ve thought about adaptive `dt` for nebular collapse, but once you span dust → planets → climate → civilization, **timescale stitching** becomes its own design problem.[^11_1][^11_3]

Missing design decisions:

- How you jump between Myr‑scale formation, kyr‑scale climate transitions, and year‑scale civilization dynamics without breaking continuity.
- Which processes are “fast” and resolved vs “slow” and treated statistically in each phase, and how you hand off state (e.g., from population synthesis to detailed ECS when the player zooms in).

This is partly technical, partly narrative: it’ll determine how “time feels” as the player moves between cosmic and local scales.

***

## 6. Population synthesis / meta‑universe layer

Finally, there’s a **population level** above any single system: global models run thousands of planetary systems to get distributions and archetypes.[^11_3][^11_4]

You already gestured at an event/statistics mode. Missing:

- A clear **population synthesis engine** that runs “off‑stage” planetary system formation/evolution, giving you priors for what kind of systems and worlds exist elsewhere.
- Hooks so your local high‑fidelity system is one draw from that meta‑distribution, and far‑field events use those same statistics rather than ad‑hoc noise.

That would let Truth sit on top of a physically‑reasonable universe generator rather than being just a single handcrafted system.

***

Short version: you’re not missing more *domains* so much as:

- Early dust/pebble and compositional gradient physics.
- Orbital migration/system‑architecture evolution.
- Climate+biosphere feedback.
- Observation/measurement modeling.
- Timescale stitching rules.
- A population‑level universe generator tied to your event/statistics mode.

Once those are sketched, your mental picture is essentially “complete” from molecular cloud to player civilization, with a clear story for what’s real, what’s approximated, and how the player experiences it.
<span style="display:none">[^11_10][^11_5][^11_6][^11_7][^11_8][^11_9]</span>

<div align="center">⁂</div>

[^11_1]: https://arxiv.org/html/2412.11064v1

[^11_2]: https://arxiv.org/html/2604.09011v1

[^11_3]: https://arxiv.org/abs/2604.09011

[^11_4]: https://www.cambridge.org/core/journals/international-journal-of-astrobiology/article/global-models-of-planet-formation-and-evolution/FBD013C41FEA908349BD23F4F8E3A376

[^11_5]: http://arxiv.org/pdf/2404.14982.pdf

[^11_6]: https://nccr-planets.ch/research/phase2/domain2/project5/planetary-systems-formation/

[^11_7]: https://academic.oup.com/mnras/article/471/3/2753/3964549

[^11_8]: https://en.wikipedia.org/wiki/Formation_and_evolution_of_the_Solar_System

[^11_9]: https://arxiv.org/pdf/astro-ph/0701485.pdf

[^11_10]: http://arxiv.org/pdf/1406.5604.pdf


---

# oh... light... radiation, xrays, gamma rays, microwaves, visible light... we only visualize the light of the stars right now as arbitrary "this is really bright"

Right now you’re treating “light” as a scalar brightness. What’s missing is that stars (and everything else) emit a **full spectral energy distribution** across the electromagnetic spectrum, and each part of that spectrum does different work in your universe and for the player.[^12_1][^12_2]

You need three things:

***

## 1. A panchromatic light model per star

Instead of “this star is bright,” each star should have:

- A **spectral energy distribution (SED)**: flux as a function of wavelength or frequency from gamma → X‑ray → UV → visible → IR → radio.[^12_2][^12_1]
- That SED determined by its effective temperature, radius, metallicity, and atmosphere type (hot O star vs cool M dwarf vs white dwarf).

Conceptually:

- High‑energy bands (X‑ray, EUV) control atmospheric escape, space weather, and radiation hazards for planets.[^12_3][^12_4]
- UV influences photochemistry (ozone, haze formation) and biological damage.
- Visible and near‑IR are what your player “sees.”
- Thermal IR is how planets and dust re‑radiate absorbed energy.
- Radio/microwave are mostly for observational instruments (e.g., radio telescopes in‑game).[^12_5][^12_2]

Implementation wise:

- Store a coarse SED (e.g., 10–20 bands) per star, derived from blackbody curves plus atmosphere corrections.
- Use different subsets of bands for:
    - Rendering (visible + near‑IR).
    - Climate/atmospheric escape (UV/XUV).
    - Space‑weather / instrument simulation (X‑ray, radio).

***

## 2. Radiation fields at planets and bodies

Right now planets just get “luminosity.” Missing is:

- **Band‑resolved flux at each planet**: integrate the star’s SED over solid angle at the planet’s orbit to get local flux per band.[^12_6][^12_3]
- Feed that into:
    - Atmosphere heating and climate (visible + IR).
    - Photochemistry (UV).
    - Atmospheric mass loss and magnetospheric dynamics (XUV).[^12_4][^12_3][^12_6]
    - Surface radiation environment for life/civilization (all bands above some energy threshold).

This turns “bright star” into a physically grounded radiation environment that:

- Shapes atmosphere retention and loss.
- Controls where it’s safe or hazardous for the player.
- Creates differences between quiet G‑type stars and flaring M dwarfs.

***

## 3. The observation/rendering pipeline

Finally, there’s the **measurement** side:

- Your current visuals are arbitrary brightness. You really want:
    - Stellar color and intensity derived from the visible part of the SED (plus extinction/scattering).[^12_1][^12_2]
    - Planet appearance based on reflected + emitted light: albedo in visible; thermal glow in IR.
    - Optionally, instrument modes (X‑ray view, radio view) that let the player see different physics.

This gives you:

- Physically meaningful star colors and brightness, not just “bright blobs.”
- Multiwavelength “views” for gameplay (e.g., X‑ray mode to see active regions, IR mode to see warm dust).

***

So the missing piece is not “more brightness,” it’s **full-spectrum radiation**:

- A panchromatic SED for each star.
- Band‑resolved flux at each planet/body.
- Using different bands for climate, chemistry, atmosphere loss, hazards, and visuals.

Once you add that, everything you’ve already designed (nebular collapse, atmospheres, geology, biosphere, civilization) can plug into the correct parts of the spectrum instead of a single generic “light” channel.
<span style="display:none">[^12_10][^12_11][^12_12][^12_13][^12_14][^12_15][^12_7][^12_8][^12_9]</span>

<div align="center">⁂</div>

[^12_1]: https://www.e-education.psu.edu/earth103/node/584

[^12_2]: https://science.nasa.gov/asset/webb/the-electromagnetic-spectrum/

[^12_3]: https://arxiv.org/abs/1804.11124

[^12_4]: https://discovery.ucl.ac.uk/id/eprint/10041335/1/Branduardi_Dunn_Sciortino_final%20resubmission_140817.pdf

[^12_5]: https://www.emergentmind.com/topics/panchromatic-stellar-seds

[^12_6]: https://www.cambridge.org/core/journals/international-journal-of-astrobiology/article/impact-of-space-weather-on-climate-and-habitability-of-terrestrialtype-exoplanets/4D75A17F6EC1BD5FAB61E2E4D9240E3B

[^12_7]: https://academic.oup.com/mnras/article/410/3/2043/964380

[^12_8]: https://ntrs.nasa.gov/api/citations/19940026924/downloads/19940026924.pdf

[^12_9]: https://chem.libretexts.org/Bookshelves/Introductory_Chemistry/Introductory_Chemistry_(LibreTexts)/09:_Electrons_in_Atoms_and_the_Periodic_Table/9.03:_The_Electromagnetic_Spectrum

[^12_10]: https://arxiv.org/html/2602.06124v1

[^12_11]: https://arxiv.org/abs/1008.0395

[^12_12]: https://www.cambridge.org/core/services/aop-cambridge-core/content/view/BA572B147F93F949147879861FB6CD5C/S1743921313011174a.pdf/div-class-title-stellar-cme-activity-and-its-possible-influence-on-exoplanets-environments-importance-of-magnetospheric-protection-div.pdf

[^12_13]: https://academic.oup.com/mnras/article/505/3/3998/6286901

[^12_14]: http://ned.ipac.caltech.edu/level5/March10/Walcher/paper.pdf

[^12_15]: http://arxiv.org/abs/1804.11124


---

# hmm... darkmatter/energy? These probably aren't that important in modeling a solar system... but... I'm kinda imagining like... the player can make as many worlds as they like, and they all exist with in a universe, I want like black holes... gammaray bursts.. these would be things that...  you need to model the effect for a galaxy to form. The effects would be subtle, and you wouldn't be modeling this part of the galaxy at very high resolution...

For what you’re trying to do, dark matter and dark energy mostly matter as **background scaffolding and rare, high‑impact events** at galaxy scale, not as high‑resolution physics inside each solar system.[^13_1][^13_2]

You don’t need them to form stars and planets correctly, but you *do* want them to:

- Shape the large‑scale gravitational potential your systems live in.
- Provide a statistical environment for things like black holes, gamma‑ray bursts, and galaxy evolution.

Here’s how I’d treat them.

***

## Dark matter: background gravity and galaxy structure

Astrophysically:

- Dark matter dominates the mass budget of galaxies and clusters; baryons sit in its potential wells.[^13_2][^13_1]
- Its main roles:
    - Set the depth and shape of galactic potential wells (rotation curves, halo structure).
    - Seed large‑scale structure (filaments, clusters) where galaxy formation happens.[^13_1][^13_2]

For your simulation:

- Represent dark matter as a **smooth potential field** at galaxy scale, not particles:
    - Each galaxy gets a dark matter halo profile (e.g., NFW or cored profile) parameterized by mass and concentration.
    - That halo potential affects orbits of stars and systems far from the galactic center, and sets escape speeds, tidal fields, and cluster binding.
- You don’t track dark matter in detail inside a solar system; within tens of AU its density is negligible compared to the star’s gravity.

In ECS terms: one halo component per galaxy, used by the orbital integrator for high‑level galaxy dynamics, not per‑planet physics.

***

## Dark energy: background expansion only

Dark energy:

- Drives the accelerated expansion of the universe at very large scales.[^13_2][^13_1]
- Its effect on bound structures (galaxies, solar systems) is essentially zero over game timescales—they’re gravitationally decoupled from the Hubble flow.[^13_1]

For Truth:

- Treat dark energy as a **cosmological scale slider**:
    - It sets the age and overall expansion of the background universe (for lore, redshifts, maybe how distant galaxies look).
    - It does not enter your solar‑system or even galaxy‑internal physics.

So dark energy can live entirely in the “meta‑universe” and observational layer (e.g., background cosmology), not the gameplay physics.

***

## Black holes, GRBs, and high‑energy phenomena

These you *do* want, but at low resolution:

### Black holes

Astrophysics:

- Stellar‑mass black holes come from massive star collapse; supermassive black holes live in galaxy centers and regulate galaxy evolution via feedback.[^13_1]
- They affect nearby systems via strong gravity, accretion disks, jets, and tidal interactions, but most stars are far enough that they see them as just another massive object.

In your universe:

- Represent black holes as **point mass + accretion / jet sources** at galaxy scale:
    - For supermassive BHs: a central potential plus episodic AGN activity (jets, radiation) that modifies gas content and star formation in the inner galaxy, but only statistically.[^13_1]
    - For stellar BHs: treat them as compact massive bodies in stellar clusters with simple accretion rules and occasional X‑ray outbursts.

You don’t voxelize BH horizons or jets; you model their *effects* on nearby gas and stars—energy injection, radiation fields, and orbital dynamics.

### Gamma‑ray bursts and high‑energy transients

GRBs and related events:

- Come from rare cataclysmic events (collapsars, neutron star mergers) and drive brief, intense high‑energy radiation beams.[^13_3]
- Their main impact on planets is sterilizing or damaging atmospheres if the planet lies in the beam.[^13_3]

In the engine:

- Treat GRBs as **stochastic events in the galaxy**:
    - Occur with a rate tied to star formation and massive star populations.
    - Emit a beamed high‑energy flux along some direction; any planetary system in the beam receives an X‑ray/gamma pulse that you apply as:
        - Atmosphere ozone destruction / chemistry perturbation.
        - Temporary radiation hazard for life/civilization.

You don’t simulate the progenitor in detail; you log a GRB event, apply its radiative effects to any affected systems, and maybe show a visual transient if the player is in range.

***

## Galaxy formation and evolution, low‑res

To give the player “as many worlds as they like in a universe,” you need a **galaxy‑level model** that is:

- Coarse: tracks halo mass, gas reservoir, star formation rate, metallicity, feedback (SN, AGN), and structural type (disk, spheroid).[^13_4][^13_5][^13_1]
- Tied to your planetary system generator:
    - Younger, gas‑rich galaxies form more stars and planets; metallicity affects planet formation efficiency and compositions.[^13_6][^13_1]
    - Dark matter halo sets overall potential; feedback (including BHs) regulates star formation and gas content.

From that you get:

- A statistically grounded distribution of star types, system architectures, and planetary environments across your universe.[^13_7][^13_4][^13_6]
- A consistent context for rare high‑energy events (GRBs, AGN episodes) that occasionally reach the player’s systems.

You don’t simulate galaxy hydrodynamics voxel‑by‑voxel; you run a coarse “global model” per galaxy and let your detailed ECS run only at the star/planet scale where the player interacts.

***

## How this fits your design

So in your stack:

- **Solar‑system physics**: baryons only (gas, dust, stars, planets, asteroids) + full EM spectrum; no explicit dark matter/energy.
- **Galaxy‑scale**: dark matter halo potential, coarse gas and star formation model, rare BH/AGN/GRB events.
- **Cosmology/meta‑universe**: dark energy sets background expansion and age; you use it for distant observations and lore, not local physics.

That keeps dark matter/energy and black holes where they belong: shaping the large‑scale stage and occasionally throwing high‑energy events into the player’s world, without forcing you to model them at high resolution in every solar system.

<div align="center">⁂</div>

[^13_1]: http://arxiv.org/pdf/2404.14982.pdf

[^13_2]: https://en.wikipedia.org/wiki/Formation_and_evolution_of_the_Solar_System

[^13_3]: https://www.cambridge.org/core/journals/international-journal-of-astrobiology/article/impact-of-space-weather-on-climate-and-habitability-of-terrestrialtype-exoplanets/4D75A17F6EC1BD5FAB61E2E4D9240E3B

[^13_4]: https://nccr-planets.ch/research/phase2/domain2/project5/planetary-systems-formation/

[^13_5]: https://www.cambridge.org/core/journals/international-journal-of-astrobiology/article/global-models-of-planet-formation-and-evolution/FBD013C41FEA908349BD23F4F8E3A376

[^13_6]: https://arxiv.org/abs/2604.09011

[^13_7]: http://arxiv.org/pdf/1406.5604.pdf


---

# ok ok... so let's zoom back in to the... well... even our stellar formation will occur with in the convines of a galaxy so... I guess we should seed the galaxy with stars? which... we don't need to do except as light emitting points of various kinds of stars...

Right: you don’t need to “simulate every star” in detail, but you do want a **galaxy-level star field** that provides:

- Realistic light sources.
- A statistical backdrop for where your fully simulated systems live.
- Hooks for rare events (GRBs, BH accretion episodes, etc.).

Think of it as three concentric layers: galaxy → star field → fully simulated systems.

***

## 1. Galaxy as a star factory, but low‑res

At galaxy scale, modern models treat each galaxy as:

- A dark matter halo plus baryonic gas reservoir.
- A star formation history: when and how many stars form, with what mass distribution and metallicity.[^14_1][^14_2]

You can mirror that with:

- **Galaxy entity**: halo mass, gas mass, star formation rate, metallicity, morphology (disk, bulge).
- A **star population generator** that:
    - Draws stellar masses from an initial mass function (IMF).
    - Assigns ages and metallicities from the galaxy’s formation history.
    - Places stars in a disk + bulge geometry (or whatever structure you want visually).[^14_3][^14_1]

This gives you millions of stars conceptually, but you don’t resolve them all in ECS.

***

## 2. Two classes of stars: “points” vs “systems”

Inside that generated population, distinguish:

- **Light-only stars**:
    - Treated as static points or simple orbits in the galaxy.
    - Have SEDs (color/brightness) and maybe variability.
    - Used purely for:
        - Rendering the sky and galaxy visuals.
        - Contributing to background radiation fields if needed (e.g., diffuse galactic light).
- **Fully simulated systems**:
    - A tiny subset where you instantiate the full ECS: nebula collapse → disks → planets → geology → atmospheres → life/civilization.
    - These live at specific positions in the galaxy and inherit:
        - The local radiation and event environment (e.g., distance from galactic center, nearby massive stars).
        - Metallicities and star formation histories from the galaxy model.[^14_4][^14_1]

So yes: you **seed the galaxy with stars**, but most are just tagged as “light sources of type K2V, age 3 Gyr” and never get detailed physics.

***

## 3. What you actually store per “light-only” star

To keep things light while still physically meaningful:

Per light-only star, store:

- Position and velocity in the galaxy (for parallax/rotation).
- Basic stellar parameters: mass, radius, age, metallicity, type (O/B/A/F/G/K/M, white dwarf, giant, etc.).
- A coarse SED (a few bands) for:
    - Visible color and brightness.
    - Optional high-energy output if you want to drive background X‑ray/UV fields.[^14_5][^14_6]

No ECS components beyond a simple “star-light” component. No disks, planets, or detailed EM fields.

***

## 4. How fully simulated systems hook into the star field

When you decide “this star will host a Phase 0+ world for the player”:

- Pick one of the existing light-only stars from the galaxy’s star field that has appropriate type/age/metallicity.
- Promote it to a **system**:
    - Attach your ECS components (nebula, EM, stellar, phase0, etc.) to a new world anchored at that star’s galactic position.
    - Keep its light-only representation for far-field rendering; use the ECS state for local physics and visuals.

From the player’s perspective:

- They’re in one star system inside a galaxy full of other stars.
- Those other stars pretty much act as light points and occasional event sources (nearby supernova, GRB, etc.), but only their home system runs the full machinery.

***

## 5. Why this is enough

For your goals:

- The galaxy is **real enough** to support:
    - Correct star colors and densities across the sky.
    - A plausible distribution of star types and ages.
    - Rare high‑energy events sampling from the galaxy’s population.
- You avoid the cost of:
    - Running nebula collapse and planet formation for every star.
    - Modeling multi‑system dynamics at full fidelity.

The net effect: you **seed the galaxy with stars**, but you treat almost all of them as panchromatic light sources. Only when the player needs to inhabit or study a particular system do you “zoom in” and swap in the full ECS representation you’ve been designing.

<div align="center">⁂</div>

[^14_1]: http://arxiv.org/pdf/2404.14982.pdf

[^14_2]: https://nccr-planets.ch/research/phase2/domain2/project5/planetary-systems-formation/

[^14_3]: https://en.wikipedia.org/wiki/Formation_and_evolution_of_the_Solar_System

[^14_4]: https://arxiv.org/abs/2604.09011

[^14_5]: https://science.nasa.gov/asset/webb/the-electromagnetic-spectrum/

[^14_6]: https://www.emergentmind.com/topics/panchromatic-stellar-seds


---

# summarize our findings so far and propose next steps for research

We’ve established that the project’s **core direction** is sound: keep the fully simulated, high-fidelity model for the player’s observed world, and use lower-resolution statistical or field models for distant systems, galaxy context, and off-screen events.  The main design work now is not inventing more features, but turning several currently simplified placeholders into coherent physical layers: plasma instead of cold wind parcels, full-spectrum radiation instead of scalar brightness, disk/solid-body formation between nebula and planets, and geology/voxel mechanics between planets and civilization.[^15_1][^15_2][^15_3]

## Current state

The repository already follows the four-quadrant layout (`domain`, `infra`, `law`, `shape`), and the active runtime is a double-buffer parallel pipeline where systems read a frozen snapshot and write disjoint component sets before folding at the barrier.  Phase 0 already covers nebular collapse, accretion, contraction, fusion, magnetic field evolution, classification, and event emission, so the foundation for a real star-formation substrate is present rather than hypothetical.

The biggest conceptual drift we identified is that some pieces were tuned toward cinematic visual behavior rather than the physics they are supposed to stand in for, especially stellar winds and luminosity handling.  In particular, the current wind implementation spawns `:nebula` parcels and reabsorbs them through sink accretion, which works visually but models a cold neutral gas loop rather than a hot ionized stellar wind.

## Physics gaps

For stars, the next missing layer is a panchromatic radiation model: each star needs a spectral energy distribution across X-ray, UV, visible, IR, microwave, and radio bands, because different bands drive different phenomena such as color, climate, photochemistry, and atmospheric escape.  That means replacing “brightness” with band-resolved flux fields so planets receive visible/IR heating, UV chemistry forcing, and XUV-driven mass loss from the same stellar source model.[^15_3][^15_4][^15_5][^15_6][^15_7]

For planet formation, the important missing bridge is the disk microphysics between cloud collapse and finished planets: dust growth, pebbles, condensation fronts, streaming instability, planetesimal formation, and migration through the gas disk.  Without that bridge, the simulation can form bodies, but it cannot yet explain why rocky material segregates inward, why ice lines matter, or why planetary system architectures emerge the way they do.[^15_2][^15_8][^15_9][^15_10][^15_1]

## World shaping

Once atmospheres exist, the final pre-civilization world-shaping layers are geology, oceans, volcanism, tectonics, and impacts, because those processes determine mountains, basins, shorelines, mineral distributions, and the actual terrain civilization will exploit.  The right split is coarse geologic fields for whole-planet processes and voxels only where non-uniformity matters most: upper crust interaction zones, asteroid interiors, active impact sites, volcanoes, mines, and engineered terrain.[^15_11][^15_12][^15_13][^15_14][^15_15][^15_16][^15_17]

For collisions, we concluded that planetary and asteroid impacts should be handled as shock-physics events where solids behave like shocked fluids for a short time, with scaling laws determining excavation, melt, vapor, and ejecta rather than rigid-body overlap.  Those bulk outcomes can then be expressed as voxel edits: carve excavation volumes, convert melt regions into temporary fluid states, remove vapor to atmosphere/debris fields, and spawn cooling fragments that become rubble-pile asteroids or merged re-solidified bodies.[^15_17][^15_18][^15_19][^15_20][^15_21][^15_22][^15_23]

## Universe context

At galaxy scale, we found that you do not need to fully simulate every star system; instead, the galaxy should be seeded with light-emitting stars as low-resolution population objects, while only a tiny subset are promoted into fully simulated ECS systems when the player inhabits or studies them.  Dark matter should be treated as a smooth galactic-scale potential and dark energy as background cosmology only, while black holes, AGN episodes, and gamma-ray bursts should exist as coarse event sources whose effects are applied statistically unless the player is nearby.[^15_7][^15_24][^15_25][^15_26][^15_1][^15_3]

This ties directly into your intended event/statistics mode: run high-fidelity simulation where observation matters, log the resulting events and transitions, then use those event distributions to drive distant or off-screen systems without resolving them moment to moment.  That approach is also the clean way to stitch together the huge timescale jump from nebular collapse, to planetary cooling, to biosphere emergence, to civilization.[^15_9][^15_10][^15_1]

## Research next steps

The best next research sequence is: first, star radiation and plasma; second, disk microphysics and planet formation; third, geology/voxels/impacts; fourth, climate-biosphere-civilization coupling; fifth, galaxy population synthesis and event statistics.  In concrete terms, that means researching: 1) stellar atmosphere and wind models with banded SEDs and ionized outflows, 2) condensation sequence, pebble drift, streaming instability, and migration, 3) plate tectonics, mantle convection, magma oceans, impact scaling, and rubble-pile mechanics, 4) simplified climate, hydrology, and carbon-cycle models, and 5) statistical event generators for distant stars, planets, and galaxies.[^15_8][^15_12][^15_15][^15_27][^15_1][^15_2][^15_3][^15_7][^15_17]

After that research pass, the most useful implementation artifact would be a phased architecture document that defines, in order, the Malli schemas, core ECS components, and ownership boundaries for: star radiation/plasma, disk cells and solids, planetary geology fields, voxel bodies, collision outcomes, and the far-field event engine.  Would you like me to turn this into a concrete phased implementation roadmap for Truth, with namespaces, schemas, systems, and test milestones?
<span style="display:none">[^15_28][^15_29][^15_30]</span>

<div align="center">⁂</div>

[^15_1]: https://arxiv.org/abs/2604.09011

[^15_2]: https://ntrs.nasa.gov/citations/20230002054

[^15_3]: https://science.nasa.gov/asset/webb/the-electromagnetic-spectrum/

[^15_4]: https://www.e-education.psu.edu/earth103/node/584

[^15_5]: https://arxiv.org/abs/1804.11124

[^15_6]: https://discovery.ucl.ac.uk/id/eprint/10041335/1/Branduardi_Dunn_Sciortino_final%20resubmission_140817.pdf

[^15_7]: https://www.cambridge.org/core/journals/international-journal-of-astrobiology/article/impact-of-space-weather-on-climate-and-habitability-of-terrestrialtype-exoplanets/4D75A17F6EC1BD5FAB61E2E4D9240E3B

[^15_8]: https://academic.oup.com/mnras/article/498/1/1239/5885349

[^15_9]: https://arxiv.org/html/2412.11064v1

[^15_10]: https://www.cambridge.org/core/journals/international-journal-of-astrobiology/article/global-models-of-planet-formation-and-evolution/FBD013C41FEA908349BD23F4F8E3A376

[^15_11]: https://www.lpi.usra.edu/education/explore/shaping_the_planets/tectonism/

[^15_12]: https://ares.jsc.nasa.gov/interaction/lmdp/documents/58263main_planetary_geology.pdf

[^15_13]: https://www.lyellcollection.org/doi/10.1144/sp401.22

[^15_14]: https://www.nhbs.com/de/planetary-geology-book

[^15_15]: https://arxiv.org/ftp/arxiv/papers/1810/1810.01815.pdf

[^15_16]: https://pages.astro.umd.edu/~dcr/reprints/richardson_icarus173,349.pdf

[^15_17]: https://arxiv.org/abs/1502.01844

[^15_18]: https://ntrs.nasa.gov/api/citations/19920009568/downloads/19920009568.pdf

[^15_19]: https://impactswiki.net/impact-tools-book/crater-scaling.html

[^15_20]: https://ntrs.nasa.gov/api/citations/19870014063/downloads/19870014063.pdf

[^15_21]: https://arxiv.org/pdf/1502.01844.pdf

[^15_22]: https://ntrs.nasa.gov/citations/19940016256

[^15_23]: https://meetingorganizer.copernicus.org/EPSC-DPS2019/EPSC-DPS2019-1686-1.pdf

[^15_24]: https://nccr-planets.ch/research/phase2/domain2/project5/planetary-systems-formation/

[^15_25]: http://arxiv.org/pdf/2404.14982.pdf

[^15_26]: https://en.wikipedia.org/wiki/Formation_and_evolution_of_the_Solar_System

[^15_27]: https://www.uu.se/en/department/physics-and-astronomy/research/astronomy-and-space-physics/stellar-physics/models-of-stellar-atmospheres

[^15_28]: 02ca0590-0163-420d-a549-97702c7aae03.md

[^15_29]: 854a647e-e40e-4af1-bdb7-6659c98f2b8c.md

[^15_30]: 4509b81a-9a17-4e17-91ba-09ff4cfb66c3.md


---

# @Prompt Wizard Produce a research prompt from the next steps

Use this as the research prompt:

***

I’m designing **Gates of Truth**, a full-stack pure Clojure planetary and stellar simulation game with an observer-centric, level-of-detail architecture. I need a **deep research brief and implementation-oriented synthesis** for the next physics layers beyond basic nebular collapse and stellar ignition.

## Context

The current simulation already has:

- Nebular gas collapse, accretion, contraction, and fusion into stars.
- A parallel double-buffer ECS pipeline where systems read a frozen world and write disjoint component sets.
- Early EM/MHD-lite behavior, atmospheric/habitability placeholders, and event logging.
- A long-term goal of supporting:
    - planetary geology,
    - atmospheres,
    - oceans,
    - tectonics,
    - impacts,
    - voxels for high-resolution body interactions,
    - eventual life and civilization,
    - and a larger galaxy/universe context.

The design principle is:

- **Model everything as physically as possible in principle**, but
- **only resolve high-fidelity effects when they materially influence what the player observes or interacts with**.
- Distant/off-screen systems should be handled by lower-resolution field/statistical/event models informed by high-fidelity simulations.


## What I need

Produce a **comprehensive research report** that synthesizes the physics, modeling strategies, and implementation implications for the following next-step areas.

### 1. Stellar radiation and plasma

Research how to move from a scalar “brightness” model to a physically grounded **panchromatic stellar radiation model**:

- stellar spectral energy distributions (SEDs),
- X-ray / EUV / UV / visible / IR / microwave / radio bands,
- stellar atmospheres and layered shells (photosphere, chromosphere, corona, etc.),
- stellar winds as ionized plasma rather than neutral gas parcels,
- flare activity, space weather, and radiation environments for nearby planets.

I need:

- what level of fidelity is scientifically meaningful for a simulation/game,
- what minimal physically grounded state variables are needed,
- how band-resolved radiation should affect planets, atmospheres, chemistry, and rendering.


### 2. Disk microphysics and planet formation bridge

Research the missing bridge between stellar ignition and finished planets:

- dust growth,
- pebbles,
- condensation sequence,
- radial drift,
- snow lines / ice lines,
- streaming instability,
- planetesimal formation,
- pebble accretion vs planetesimal accretion,
- planetary migration,
- early system architecture formation.

I need:

- the major mechanisms that determine rocky vs icy vs gas-rich worlds,
- what can be parameterized at low resolution,
- what has to be explicitly represented to preserve plausible planetary diversity,
- how to translate this into simulation layers or ECS components.


### 3. Planetary geology and surface evolution

Research the physics needed after atmospheres are present to make inhabitable worlds physically and visually interesting:

- mantle convection,
- crust formation,
- tectonics / plate tectonics,
- volcanism,
- mountain building,
- ocean basin formation,
- erosion,
- sediment transport,
- hydrology,
- magma oceans and cooling histories.

I need:

- a hierarchy of models from coarse whole-planet fields to high-resolution local terrain,
- what determines whether a planet is tectonically active,
- what controls the emergence of mountains, seas, volcanoes, continents, and mineral distributions,
- what can be simulated statistically versus explicitly.


### 4. Asteroids, moons, and non-uniform bodies

Research how to represent small and irregular bodies:

- rubble piles,
- porous bodies,
- differentiated asteroids,
- regolith,
- non-spherical geometry,
- internal heterogeneity,
- cooling fragments from collisions,
- moons formed by impact debris or capture.

I need:

- the scientifically grounded structure types for small bodies,
- the right resolution and data structure choices,
- when voxels are warranted,
- how irregular composition and shape affect later mining, collision, and engineering gameplay.


### 5. Planetary and asteroid collisions

Research how to model large impacts and mergers:

- shock physics,
- crater scaling,
- melt / vapor / ejecta,
- catastrophic disruption,
- fragment reaccumulation,
- giant impacts between protoplanets,
- impact-generated atmospheres and magma oceans.

I need:

- a practical simulation design that uses hydrocode/scaling-law insights without requiring a real hydrocode at runtime,
- how to determine excavation, shaving, melt regions, vaporization, and fragment outcomes,
- how to transition temporarily fluidized matter back into solid/voxel bodies.


### 6. Climate, hydrology, and biosphere-adjacent groundwork

Research the minimal physically grounded climate and hydrology layers needed before life and civilization:

- radiative balance,
- greenhouse forcing,
- cloud/haze effects,
- water cycle,
- ocean/ice states,
- runoff and river formation,
- climate stability regimes,
- long-term carbon/silicate style regulation if relevant.

I need:

- what is necessary before life emerges,
- what world-state variables best support later biosphere and civilization simulation,
- how climate should couple to geology, atmosphere, and stellar radiation.


### 7. Galaxy-scale context and low-resolution universe modeling

Research how to embed detailed star/planet simulations in a larger galaxy/universe context:

- galaxy star populations,
- star formation histories,
- metallicity gradients,
- black holes / AGN / gamma ray bursts as distant event sources,
- dark matter as coarse gravitational structure,
- dark energy only if relevant at background cosmological scale.

I need:

- what absolutely matters to a solar-system-scale simulation and what doesn’t,
- how to represent the galaxy as a low-resolution star field plus event environment,
- how distant astrophysical events should influence local systems statistically rather than through high-resolution simulation.


### 8. Observation and level-of-detail architecture

Research how to connect “truth state” to “what the player can perceive”:

- instrument-limited observations,
- spectra,
- light curves,
- maps,
- atmospheric and geological sensing,
- observer-centric LOD transitions,
- event logging for later statistical modeling.

I need:

- an architectural framework for switching between:
    - high-fidelity local simulation,
    - coarse field models,
    - and statistical/event-driven distant evolution,
- while preserving continuity and plausibility.


## Deliverable format

Please produce:

1. A **structured research report** with clear sections for each topic above.
2. A **cross-topic synthesis** explaining how these layers depend on each other.
3. A **recommended phased research roadmap** in priority order.
4. A **recommended phased implementation roadmap** for an ECS / pure-functional simulation architecture.
5. For each phase, identify:
    - what should be modeled explicitly,
    - what can be approximated,
    - what can be deferred into event/statistical models.
6. Include **warnings about common physically incorrect simplifications** that are tempting in simulation/game design.
7. Emphasize **observer-centric fidelity** and **scientifically grounded simplifications** over maximal brute-force realism.

## Constraints and preferences

- Assume the implementation language is **pure Clojure on the JVM**.
- Prefer designs that separate:
    - pure simulation logic,
    - geometry/shape math,
    - infrastructure/rendering,
    - schemas/contracts.
- Assume the eventual engine will use:
    - ECS for simulation entities,
    - voxels for highest-resolution local body interactions,
    - coarse fields/statistical models for distant or off-screen evolution.
- Avoid recommending “just run a full hydrocode / GCM / stellar evolution code at runtime.”
- Instead, identify **the minimal set of physically grounded state variables, regimes, lookup tables, and approximations** needed to preserve scientific plausibility.


## Tone

Write for a technically sophisticated simulation/game architect who wants to preserve real physics where it matters, but needs a practical and staged plan.

***

If you want, I can also turn that into:

1. a **shorter Deep Research prompt**,
2. a **Prompt Wizard optimized version** with stricter output formatting,
3. or a **phase-by-phase research prompt series** so you can investigate one subsystem at a time.
<span style="display:none">[^16_1][^16_10][^16_2][^16_3][^16_4][^16_5][^16_6][^16_7][^16_8][^16_9]</span>

<div align="center">⁂</div>

[^16_1]: https://arxiv.org/pdf/astro-ph/0402496.pdf

[^16_2]: https://arxiv.org/pdf/2407.03137.pdf

[^16_3]: https://academic.oup.com/mnras/article/457/4/4123/2589000

[^16_4]: https://phys.libretexts.org/Bookshelves/Astronomy__Cosmology/The_Fundamentals_of_Stellar_Astrophysics_(Collins)/16:_Beyond_the_Normal_Stellar_Atmosphere

[^16_5]: https://arxiv.org/html/2511.16761

[^16_6]: https://users.obs.carnegiescience.edu/mseibert/cwleo_collection/massloss-winds-bubbles/Kudritzki-Winds%20from%20hot%20stars-annurev.astro.38.2000.pdf

[^16_7]: https://ntrs.nasa.gov/api/citations/20060052448/downloads/20060052448.pdf

[^16_8]: https://arxiv.org/pdf/astro-ph/9611068.pdf

[^16_9]: https://www.uu.se/en/department/physics-and-astronomy/research/astronomy-and-space-physics/stellar-physics/stellar-winds

[^16_10]: https://www.scielo.org.mx/pdf/rmaa/v55n1/0185-1101-rmaa-55-01-31.pdf


---

# Prompt wizard, in depth. The agent should go to great lengths to do research and cite sources, provide visualizations, graphs, charts and ground it in [Truth](https://github.com/octave-commons/Truth)

Here is the maximally detailed **Prompt Wizard** version, engineered for an agent that will do exhaustive multi-round research, cite primary sources, generate charts and visualizations, and ground every recommendation directly in the Truth codebase:

***

```
# Deep Research Brief: Gates of Truth — Physics Simulation Architecture

## Mission

You are a senior astrophysics and game simulation architect. Your task is to
produce an exhaustive, primary-source-cited, visualization-rich research report
that will directly inform the implementation of **Gates of Truth** — a full-stack
pure Clojure planetary and stellar simulation game.

The project lives at: https://github.com/octave-commons/Truth

Before writing a single word of your report, you MUST:

1. Read the current codebase. Start by fetching the file tree at
   https://github.com/octave-commons/Truth and then read the following files
   in full:
   - src/domain/stellar.clj
   - src/domain/em.clj
   - src/domain/chemistry.clj
   - src/domain/phase0.clj
   - src/domain/regime.clj
   - src/law/stellar.clj
   - src/law/field.clj
   - src/law/ledger.clj
   - src/law/contract.clj

2. For each section of this brief, search academic and primary sources
   (arXiv, NASA ADS, MNRAS, ApJ, Icarus, JGR Planets, LPI) and cite at
   minimum 3 independent authoritative sources per major claim.

3. Produce at minimum 8 charts, diagrams, or visualizations (using Plotly
   or Mermaid) covering:
   - Stellar SED comparison across star types
   - Condensation sequence temperature vs material
   - Streaming instability parameter space (solid-to-gas ratio vs Stokes number)
   - Crater scaling law regime diagram
   - Planet formation timeline from dust to finished world
   - Tectonic activity vs planet mass and age
   - LOD architecture diagram (observer-centric fidelity layers)
   - ECS component dependency graph across simulation layers

4. For every recommendation, include a concrete mapping to the Truth codebase:
   - which existing namespace is the right home,
   - what new namespace/file should be created if none exists,
   - what Malli schema should be defined in src/law/,
   - what ECS component should be added,
   - what system function should be written,
   - what the failing test should assert BEFORE implementation.

---

## Context

Gates of Truth is a full-stack pure Clojure JVM simulation game. Its
architecture uses:

- Four namespace quadrants: domain/ (pure sim), infra/ (rendering/IO),
  shape/ (geometry), law/ (Malli schemas + validators).
- A parallel double-buffer ECS pipeline: all systems read frozen snapshot N,
  write snapshot N+1, fold at a barrier. Order within the parallel fan-out
  is irrelevant.
- An event ledger that logs astrophysical transitions.
- A phase-based simulation lifecycle: Phase 0 covers nebular collapse through
  stellar ignition.

The core design principle is:
> Model everything as physically accurately as possible IN PRINCIPLE,
> but only RESOLVE high-fidelity physics when it materially affects
> what the player observes or interacts with.
> Everything else is driven by coarse fields, scaling laws, or statistical
> event models calibrated by high-fidelity runs.

Current known shortcomings:
- Stellar wind is modeled as cold neutral gas parcels (wrong: should be hot
  ionized plasma with ram-pressure field model).
- Luminosity is a scalar brightness (wrong: should be panchromatic SED
  per star type).
- No disk microphysics between collapse and planet formation.
- No geology/tectonics/voxel layer for planetary surfaces.
- No collision physics for asteroid/planet impacts.
- No galaxy-level context for star population and event environment.

---

## Research Sections

### Section 1: Stellar Radiation and Plasma

Research and report on:

- The full electromagnetic spectrum emitted by different star types
  (O, B, A, F, G, K, M, white dwarfs, neutron stars).
- Panchromatic stellar SEDs: what determines the SED shape, and how
  to parameterize it from (T_eff, log_g, metallicity, rotation, age).
- Stellar atmosphere layering: photosphere, chromosphere, transition
  region, corona. Key state variables per layer.
- Stellar winds as ionized plasma: Parker solar wind theory, mass loss
  rates, wind velocities, ram pressure as a field, Alfvén radius.
- XUV irradiation of planets: X-ray + EUV driven atmospheric escape,
  energy-limited vs recombination-limited escape.
- Flare activity and space weather: spot cycles, flare energy distributions,
  CMEs, effects on planetary magnetospheres and atmospheres.

Deliverables:
- Chart: SED comparison (log flux vs wavelength) for O5, G2, M5 star types.
- Chart: XUV luminosity vs stellar age for solar-type stars.
- Table: per-band (gamma/X/EUV/UV/vis/NIR/MIR/FIR/radio) physical effects
  on planets.
- Concrete Truth mapping: c/atmosphere-shells, c/wind-profile, c/sed-bands,
  domain.plasma namespace proposal, replacement for spawn-clump in
  stellar-wind-system.

### Section 2: Disk Microphysics and Planet Formation Bridge

Research and report on:

- Grain growth from micron dust to pebbles: coagulation, fragmentation
  barriers, bouncing barrier.
- Radial drift and vertical settling: Stokes number, drift timescales,
  pebble delivery to inner disk.
- Condensation sequence and ice lines: which materials freeze out where,
  how that controls solid surface density jumps.
- Streaming instability: physical mechanism, onset conditions
  (solid-to-gas ratio, Stokes number, turbulence), outcome
  (planetesimal size distribution).
- Pebble accretion vs planetesimal accretion: which dominates, when,
  consequences for core composition and growth rate.
- Disk-planet interaction: Type I and Type II migration torques,
  resonance trapping, instability after disk dispersal.
- System architecture classes: compact chains, solar analogues, hot
  Jupiters, debris-disk systems.

Deliverables:
- Chart: streaming instability onset in (St, Z) parameter space, showing
  laminar vs turbulent thresholds.
- Chart: condensation temperature vs material, annotated with snow line
  positions in a solar-type disk.
- Chart: planet formation timeline (log time vs mass) from dust to
  finished planet.
- Concrete Truth mapping: c/disk-cell component, disk-evolution-system,
  planetesimal-formation-system, pebble-accretion-system, migration-system.

### Section 3: Planetary Geology and Surface Evolution

Research and report on:

- Magma ocean formation, crystallization, and early crust formation.
- Mantle convection: parameterized convection models, heat flux, style
  (stagnant lid vs mobile lid vs episodic).
- Plate tectonics onset and driving: what determines whether a planet
  develops plate tectonics (mass, composition, water content, cooling rate).
- Volcanism: hotspot chains, arc volcanism, flood basalts; relationship
  to mantle convection pattern.
- Mountain building, rifting, basin formation.
- Erosion, hydrology, sediment transport: required state variables for
  surface evolution over geological time.
- Ocean formation: water delivery, ocean basin development, salinity.
- Long-term climate regulation: silicate weathering feedback, carbon cycle.

Deliverables:
- Chart: tectonic regime vs planet mass and surface temperature
  (stagnant lid / episodic / mobile plate).
- Chart: heat flux vs time for Earth-mass, Mars-mass, Super-Earth-mass
  planets.
- Diagram: ECS component stack for planetary interior and surface
  (from core → mantle → crust → surface field → voxel band → atmosphere).
- Concrete Truth mapping: c/interior-state, c/plate-field,
  c/mantle-convection, c/surface-geology, tectonic-system,
  volcanism-system, erosion-system namespaces.

### Section 4: Asteroids, Moons, and Non-Uniform Bodies

Research and report on:

- Rubble pile structure: porosity, tensile strength, cohesion,
  observed shapes, spin limits (Jacobi ellipsoids, YORP).
- Differentiated vs undifferentiated asteroids: what determines
  which type forms.
- Regolith: formation, properties, depth, thermal behavior.
- Non-spherical gravitational potential: implications for orbits and
  tidal interaction.
- Moon formation: giant impact debris, capture, co-formation,
  tidal evolution.
- Collision remnants: how voxel subsets of original bodies become
  new aggregates.

Deliverables:
- Chart: asteroid spin rate vs diameter (spin barrier / rubble pile
  boundary).
- Diagram: voxel representation of asteroid interior (metallic core,
  silicate mantle, regolith shell, voids).
- Concrete Truth mapping: c/voxel-field, c/body-structure,
  voxel-physics-system, when to use voxels vs coarse hull.

### Section 5: Impact Physics and Collision Outcomes

Research and report on:

- Pi-group crater scaling laws: transient crater size, excavation depth,
  melt volume, ejecta velocity distribution.
- Regime classification: simple cratering, complex cratering, basin
  formation, catastrophic disruption, merging.
- Hydrocode insights: what shock physics codes (iSALE, SPH-based codes)
  tell us about melt/vapor/fragmentation.
- Giant impact events: magma ocean generation, atmosphere blow-off,
  moon-forming debris disks.
- Post-impact cooling and reaccretion: how fragments cool, compact,
  and regain solid character.
- Impact parameter effects: angle, velocity, porosity, target/impactor
  density ratio.

Deliverables:
- Chart: impact regime diagram (impactor mass vs impact velocity,
  showing cratering/disruption/merging zones).
- Chart: melt volume vs impact energy, across target types.
- Flowchart: collision-system decision tree (classify regime →
  compute scaling outcomes → apply voxel edits → spawn fragments →
  cool/reaccrete).
- Concrete Truth mapping: collision-system, fragment-spawn-system,
  melt-flow-system, reaccretion-system, shock-outcome pure functions.

### Section 6: Climate, Hydrology, and Pre-Biosphere Groundwork

Research and report on:

- Zero-dimensional and 1D energy-balance climate models: what's
  sufficient for a simulation game.
- Greenhouse forcing: key species (CO2, H2O, CH4, N2O), band models,
  simplified parameterizations.
- Cloud and haze effects: albedo modifiers, anti-greenhouse.
- Water cycle: evaporation, precipitation, runoff, ocean basin
  interaction.
- Ice-albedo feedback and snowball states.
- Runaway greenhouse threshold and moist/dry limits.
- Silicate weathering thermostat: simplified parameterization.

Deliverables:
- Chart: climate stability zones (stellar flux vs CO2 level, showing
  snowball / temperate / runaway regimes).
- Diagram: climate-geology-atmosphere coupling loop.
- Concrete Truth mapping: c/climate-state, c/ocean-state,
  climate-system, hydrology-system, coupling with
  existing domain.atmosphere.

### Section 7: Galaxy-Scale Context and Universe Modeling

Research and report on:

- IMF (initial mass function): stellar mass distribution in galaxies.
- Galaxy star formation histories: how star type and metallicity
  distributions evolve with galactic age.
- Metallicity gradients: inner vs outer galaxy, effect on planet
  formation efficiency.
- Dark matter halos: NFW and cored profiles, when they matter vs don't.
- Black holes: stellar-mass BHs, supermassive BHs, AGN activity, jets,
  feedback on star formation.
- Gamma-ray bursts: rates, beaming, atmospheric impact on planets.
- Supernovae: rate, ejecta, role in seeding ISM metallicity.

Deliverables:
- Chart: IMF (dN/dM vs stellar mass).
- Chart: GRB rate vs redshift / galactic environment.
- Diagram: galaxy LOD layers (full-detail system → regional field →
  galaxy coarse → meta-universe).
- Concrete Truth mapping: c/star-light (low-res), c/galaxy-halo,
  c/event-source, galaxy-seed-system, promotion/demotion logic
  for zooming in/out.

### Section 8: Observer-Centric LOD Architecture and Event Statistics

Research and report on:

- Existing game/simulation approaches to observer-centric LOD:
  streaming, procedural generation, level promotion/demotion.
- Statistical/event-driven simulation: hazard models, Poisson processes,
  conditional distributions, calibration from high-fidelity runs.
- Population synthesis in astrophysics: how Bern Model and similar
  frameworks derive planet populations from distributions.
- Observational pipeline: truth state → simulated instrument → player
  observation, with noise and resolution limits.
- Timescale stitching: how to transition between Gyr-scale formation,
  Myr-scale climate, kyr-scale geology, year-scale civilization.

Deliverables:
- Diagram: full observer-centric LOD architecture (from voxels to
  galaxy background).
- Diagram: event/statistics mode pipeline (high-fidelity sim →
  event log → fit distributions → sample for off-screen evolution).
- Concrete Truth mapping: event-statistics-system, lod-scheduler,
  phase-transition hooks in domain.phase0, integration with
  existing law/ledger.clj.

---

## Cross-Topic Synthesis

After all sections, produce a synthesis that answers:

1. In what ORDER should these layers be implemented, and why?
2. What are the critical dependencies between layers (e.g., must have
   SED before atmospheric escape; must have streaming instability before
   planet formation)?
3. What "vibes-based" simplifications are most tempting and most dangerous
   to make in each layer?
4. What is the minimum viable physically grounded implementation of each
   layer that preserves scientific plausibility without full simulation?

---

## Phased Roadmap

Produce two roadmaps:

### Research Roadmap (what to study in what order)

Phase 1 — Radiation and plasma (SED, winds, ionization)
Phase 2 — Disk microphysics (dust, pebbles, SI, migration)
Phase 3 — Planetary geology (interior, tectonics, volcanism, voxels)
Phase 4 — Collision physics (scaling laws, fragments, reaccretion)
Phase 5 — Climate and hydrology (pre-biosphere ground state)
Phase 6 — Galaxy context (star population, event environment)
Phase 7 — LOD and event/statistics mode (observer-centric architecture)

For each phase: key papers to read, key models to understand, key
approximations to validate.

### Implementation Roadmap (what to build in what order)

For each phase, list:
- New Malli schemas in src/law/
- New ECS components (defrecord in src/domain/)
- New systems (functions ending in -system in src/domain/)
- New namespaces to create
- Existing namespaces to modify
- Failing tests to write first (red phase)
- Invariants to preserve (architecture law references)

---

## Common Pitfalls to Warn About

Explicitly call out and explain each of the following anti-patterns:

1. Modeling stellar wind as neutral cold gas parcels.
2. Using a scalar brightness instead of a panchromatic SED.
3. Assuming planet composition is uniform "rock + gas" without
   condensation sequence.
4. Using rigid-body collision instead of shock-physics scaling.
5. Assuming tectonics is the default: explain what prevents plate tectonics
   on most planets.
6. Using scripted event rates instead of emergent statistics.
7. Seeding the galaxy with identical star types.
8. Treating dark energy as relevant at sub-galactic scales.

---

## Output Requirements

- Minimum 5,000 words of prose (excluding code, schemas, charts).
- At minimum 8 charts or diagrams (Plotly or Mermaid).
- At minimum 30 citations from primary or authoritative sources
  (arXiv, NASA, peer-reviewed journals).
- Every recommendation grounded in specific Truth source files
  read from GitHub.
- All Malli schemas written in valid Clojure syntax.
- All ECS component proposals written as valid Clojure defrecord stubs.
- All system proposals written as valid Clojure function signatures
  with docstrings.
- Tests written using kaocha-compatible syntax.
```

