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

