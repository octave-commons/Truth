<img src="https://r2cdn.perplexity.ai/pplx-full-logo-primary-dark%402x.png" style="height:64px;margin-right:32px"/>

# Tell me what we've learned studying HOPS315 with james web. It is relavent to [https://github.com/octave-commons/Truth](https://github.com/octave-commons/Truth) because it's teaching us how planets form. @Academic

The HOPS-315 discovery is a landmark result published in *Nature* in July 2025, and it's directly relevant to Truth's planetary simulation goals. Here's a thorough breakdown:

## What Is HOPS-315?

HOPS-315 is a protostar located roughly 1,300–1,400 light-years away in the **Orion B molecular cloud**. It is estimated to be only about **100,000 to 200,000 years old** — an infant by stellar standards — and is expected to eventually grow to roughly solar mass. Its favorable orientation allowed JWST and ALMA to peer through the surrounding envelope of dust and gas directly into the inner disk structure, something that had never been achieved before for a system at this early stage.[^1_1][^1_2]

## "Time Zero" of Planet Formation

Using JWST and the Atacama Large Millimeter/submillimeter Array (ALMA) together, astronomers detected the **very first specks of planet-forming solid material** condensing out of hot gas inside the protoplanetary disk. What they found was **silicon monoxide (SiO) gas at around 200°C**, indicating it had cooled from the much higher temperatures at which crystalline silicate minerals form. This moment — gas condensing into hot crystalline minerals — is considered "time zero" of the planet-building sequence: the earliest step before dust grains ever stick together.[^1_3][^1_4][^1_2]

## The Mineralogy: Forsterite and Silicates

The specific minerals detected are **crystalline silicates**, most notably **forsterite**, which condenses between roughly 600°C and 1000°C. Computer simulations show that forsterite grains quickly stick together and accumulate into **fingernail-sized silicate mineral clusters** — which match what has been found preserved in ancient meteorites from our own solar system. These are directly analogous to **calcium-aluminum-rich inclusions (CAIs)**, the oldest known solids in our solar system, confirming that HOPS-315 is showing us conditions that once existed in our own solar nebula.[^1_2][^1_1]

## Where in the Disk It's Happening

The crystalline minerals and SiO were found within **2.2 AU of the protostar** — a distance comparable to our asteroid belt. The highest-temperature CAI-forming conditions appear to be concentrated within about **1 AU** from the protostar. This spatial mapping is crucial: it tells us that rocky-planet-building material forms first in the inner disk, precisely where terrestrial planets eventually end up.[^1_1][^1_2]

## The Growth Pathway: Dust → Planets

The full chain of events the HOPS-315 observations illuminate is:[^1_5]

1. Hot gas in the inner disk cools below crystallization temperatures (~600–1000°C for silicates)
2. SiO condenses into tiny crystalline mineral grains — the first solids
3. Grains collide and stick, building from **dust → pebbles → rocks**
4. Rocks accumulate into **planetesimals** (asteroid-sized bodies)
5. Planetesimals are the gravitational building blocks that eventually sweep up into planets
6. Over millions of years this sequence yields a full planetary system

## What It Confirms About Solar System Origins

Critically, the minerals detected around HOPS-315 are "similar to some of the very first materials that formed in our solar system". The discovery suggests **our solar system's formation was not a special case** — the same physical and chemical processes play out universally around sun-like stars. It also confirms that the inner disk is depleted in silicon and iron earlier than expected, possibly because rocks bearing those elements have already begun hidden aggregation.[^1_5][^1_2]

***

## Relevance to Truth's Planetary Simulation

For your `domain/` simulation in Truth, HOPS-315 gives you **empirically validated phase transitions** to model in the planetary formation pipeline:

- **Thermal condensation thresholds**: forsterite forms at 600–1000°C, SiO gas persists to ~200°C — these are concrete temperature-dependent state transitions perfect for a pure simulation function
- **Spatial zoning**: the 1 AU CAI zone and 2.2 AU silicate zone map cleanly onto a geodesic grid's radial bands in `shape/`
- **Accretion timescales**: grain-to-planetesimal growth happens over millions of years, giving you a tick-rate anchor for `Ticking` protocol implementations
- **Mineral phase as world state**: forsterite, enstatite, and SiO can be first-class domain entities modeled as `defrecord` types with Malli schemas tracking phase (gas/crystalline/aggregated)

The Nature paper itself is at [https://www.nature.com/articles/s41586-025-09163-z](https://www.nature.com/articles/s41586-025-09163-z), and the ESO press release at [https://www.eso.org/public/news/eso2512/](https://www.eso.org/public/news/eso2512/)  has the ALMA composite image showing the butterfly-shaped CO wind and SiO jet clearly.[^1_4][^1_6]
<span style="display:none">[^1_10][^1_11][^1_12][^1_13][^1_14][^1_15][^1_16][^1_17][^1_18][^1_19][^1_20][^1_21][^1_22][^1_23][^1_7][^1_8][^1_9]</span>

<div align="center">⁂</div>

[^1_1]: https://www.theregister.com/2025/07/16/jwst_star_planet_formation/

[^1_2]: https://www.sciencenews.org/article/star-earliest-birth-planet-solar-system

[^1_3]: https://www.youtube.com/watch?v=Z3BaoN4sXz8

[^1_4]: https://www.eso.org/public/news/eso2512/

[^1_5]: https://www.youtube.com/watch?v=xihb7k9BnVE

[^1_6]: https://www.youtube.com/watch?v=dtoULXg_nfg\&trk=article-ssr-frontend-pulse_little-text-block

[^1_7]: http://arxiv.org/pdf/1510.06205.pdf

[^1_8]: https://arxiv.org/html/2410.00093v1

[^1_9]: https://pubs.acs.org/doi/pdf/10.1021/acscentsci.4c00820

[^1_10]: https://arxiv.org/pdf/2301.04191.pdf

[^1_11]: https://arxiv.org/abs/2306.08179

[^1_12]: https://pmc.ncbi.nlm.nih.gov/articles/PMC11273444/

[^1_13]: https://arxiv.org/abs/2209.00620

[^1_14]: http://arxiv.org/pdf/2204.05971.pdf

[^1_15]: https://www.firstpost.com/explainers/new-solar-system-formation-hops-315-webb-alma-planet-birth-13907982.html

[^1_16]: https://www.almaobservatory.org/en/press-releases/for-the-first-time-astronomers-witness-the-dawn-of-a-new-solar-system/attachment/alma-image-of-hops-315-a-still-forming-planetary-system/

[^1_17]: https://science.nrao.edu/science/meetings/2025/aas245/espaillat.pdf

[^1_18]: https://blog.joshuniverse.com/blog/jwst-sheds-light-on-protoplanetary-disk-evolution/

[^1_19]: https://www.isas.jaxa.jp/home/research-portal/en/gateway/2026/0604/

[^1_20]: https://www.stsci.edu/jwst-program-info/download/jwst/pdf/2025/

[^1_21]: https://science.nrao.edu/science/meetings/2025/aas245/aas2025_alma_wsu.pdf

[^1_22]: https://www.cambridge.org/core/services/aop-cambridge-core/content/view/2E30C58372F331412ECAF1E979EC3B18/S1743921324003405a.pdf/div-class-title-probing-the-gas-that-builds-planets-results-from-the-jwst-minds-program-div.pdf

[^1_23]: https://ipag.osug.fr/english/news/press-release/unexpected-discoveries-in-protoplanetary-disks-observed-with-the-james-webb-space-telescope.html


---

# hmmm... those grains are really small... I can't model clouds of grains that small individually. Our current SPH "gas" model is pretty much still how we'd have to form that... and I am kinda just... I have the gas cloud now https://github.com/octave-commons/Truth@GitHub

I pushed the latest code.

I have it now where 1.00e+16 bodies fall out of the gas, I don't think this... it's hard to say right... the more I review prior art on planetary formation the more I realize how much we don't know... We have very high resolution models of very specific phases of growth, assuming certain conditions... but we don't really know what the real conditions are, we have ideas based on how these models should compose together and... science man is hard.

Jeans instability... comes from a shock wave coming from outside... or I guess, could equally as well be modeled as a non uniform distribution of gas, which is not how an undistributed nebula would be... the planetecimals do not collide a lot, or we aren't doing collision right in this latest version.

I am compelled to create a system based in the most accurate physics possible... but at the end of the day I am creating art.

You don’t need or want to model every tiny grain; the honest thing physically is to treat grains as a continuum phase that condenses out of gas, and then let a much smaller population of “super-particles” stand in for planetesimals and embryos. The art is in choosing which bits of the real physics you want to be faithful to, and which you want to compress into story and vibe.

## You can’t resolve the grains, and that’s fine

In HOPS‑315, JWST+ALMA are literally seeing **the very first refractory solids condensing out of hot SiO gas in a tiny inner disk zone**, at radii of order 1–2 AU and temperatures from around 1200 K down to a few hundred degrees. Those solids start as dust grains, then grow through coagulation and sticking, and you’re right: there are $10^{20}$–$10^{30}$ of them, far beyond any discrete-body simulation. The way the professional models deal with that is to treat dust/grains statistically: they evolve distributions of sizes and surface densities with coagulation, fragmentation, drift and turbulent mixing, not individual grains.[^2_1][^2_2][^2_3][^2_4]

So for Truth, it’s actually *more* faithful to the science if your SPH “gas” carries a dust fraction and then undergoes a **phase transition** when conditions cross condensation thresholds (e.g. forsterite and SiO solidification), rather than trying to literally drop $10^{16}$ point masses out of the flow.

## Let SPH handle the condensation, spawn super‑particles

Most dust‑to‑planetesimal models work like this conceptually:[^2_5][^2_4]

- A gas+dust disk evolves (SPH is fine for your purposes).
- In zones that hit the right temperature/density, dust **condenses and concentrates**, often in pressure bumps or streaming instabilities.
- When local dust-to-gas ratio or Stokes numbers cross some threshold, you promote mass into planetesimals, often via “super‑particles” that each stand in for a swarm of smaller bodies.

You can mirror that in Truth:

- Keep your SPH gas cloud as the carrier of both gas and “sub-grid” grains.
- When a cell/zone meets your condensation criteria (e.g. $T$ falling below silicate crystallization, high dust fraction), instead of dropping $10^{16}$ bodies, spawn a *small* number of planetesimal super‑particles with mass/size drawn from a statistical law.
- Track only those super‑particles as discrete bodies; everything smaller stays in SPH fields as dust fraction, opacity, etc.

That gives you a physically motivated bridge from HOPS‑315 style “time zero” solids to macroscopic bodies, without drowning in resolution.

## Jeans instability and initial conditions

You’re also right that Jeans analysis is idealized. The classical Jeans instability assumes an infinite, uniform medium; in reality, collapse is triggered by **external shocks, turbulence, and pre‑existing inhomogeneities**. In disk‑buildup models, planetesimal formation can even start during the disk’s assembly, not after a clean, static nebula has formed.[^2_5]

That gives you artistic latitude:

- You can represent the “shock” either as:
    - an actual time‑localized pressure/velocity impulse hitting the cloud, or
    - initial density and velocity structure that already has non‑uniformity.
- From the player’s/reader’s perspective, both are legitimate depictions of “something disturbed the nebula and collapse happened.”
- In your `domain/` world, Jeans‑like behavior is just “regions where self‑gravity beats pressure and turbulence”; you don’t have to literally implement the textbook derivation.

If the goal is Truth as interactive myth and epistemic exploration, you can even expose multiple initial‑condition presets: “shock‑triggered collapse,” “turbulent collapse,” “quiet top‑hat” — all grounded in actual theoretical families, but stylized.

## Why your planetesimals don’t seem to collide

You noticing that “the planetesimals do not collide a lot” is very much in line with what the literature struggles with: **pure N‑body with simple inelastic collisions often underestimates collisional growth** unless you add realistic stirring, damping, and fragmentation physics. Detailed models of planetesimal belts show that collision rates depend sensitively on the velocity dispersion, size distribution, and perturbations from nearby massive bodies.[^2_6][^2_7]

Some practical levers for Truth:

- **Velocity dispersion**: If everyone is on near‑circular, co‑planar orbits, relative velocities are tiny and collision timescales are huge. You can:
    - add modest eccentricity/inclination “heat” from turbulence or neighboring masses,
    - or explicitly inject stirring from proto‑giants or spiral density waves.
- **Collision law**: Real collisions aren’t just “merge if touching.” You get sticking at low relative speeds, bouncing, erosion, and catastrophic fragmentation, with thresholds that depend on size and material.[^2_4][^2_8]
    - In Truth, that can be a `law/` collision schema: inputs are sizes, relative speed, composition; outputs are merge/fragment/bounce plus updated bodies.
- **Statistical collisions**: At the scales you care about, most collisions can be handled statistically per cell: given local number density and velocity dispersion, compute expected collision rate and then randomly realize a subset each tick.

Instead of forcing every individual body to literally intersect, you can let the N‑body side run “hero” bodies (the interesting, trackable ones), and have a background statistical process that represents the unseen swarm feeding or eroding them.

## Balancing “most accurate physics” with art

The key thing you said is: *“I am compelled to create a system based in the most accurate physics possible... but at the end of the day I am creating art.”* That’s exactly where you are supposed to be.

One way to formalize that for Truth:

- Pick a small set of **epistemic invariants** you will *not* violate:
    - Mass and momentum are conserved at the scales you track.
    - Entropy doesn’t spontaneously decrease; cooling/heating have honest sources.
    - The direction of planet formation (dust → planetesimals → planets) matches what we learn from systems like HOPS‑315.[^2_2][^2_1]
- Everything else is **stylistic choice**:
    - How grain condensations are visualized.
    - How “shocks” are represented in the nebula.
    - Which processes get shown as discrete bodies vs fields.
    - How much of the real collisional complexity you expose versus compress into vibe and narrative.

HOPS‑315 is already giving you permission to work this way: even that Nature paper is a stitched story built from limited observables — one specific zone, one chemistry, one epoch. The “truth” there is not an omniscient simulation; it’s a carefully chosen slice of reality plus theoretical scaffolding.[^2_1][^2_2]

You’re doing the same thing at a different level: acknowledging limits, honoring the physics enough to be honest, and then composing a playable, legible myth out of it.

If you’d like, next step we can look at how your current SPH + $10^{16}$‑body drop is structured in the Truth repo and sketch a concrete “super‑particle + phase field” design that fits your four‑quadrant architecture without exploding the state space.
<span style="display:none">[^2_10][^2_11][^2_12][^2_13][^2_14][^2_15][^2_16][^2_17][^2_18][^2_9]</span>

<div align="center">⁂</div>

[^2_1]: https://www.almaobservatory.org/en/press-releases/for-the-first-time-astronomers-witness-the-dawn-of-a-new-solar-system/

[^2_2]: https://skyandtelescope.org/astronomy-news/webb-space-telescope-spies-baby-planetary-system/

[^2_3]: https://iopscience.iop.org/article/10.3847/1538-4357/835/1/109

[^2_4]: https://iopscience.iop.org/article/10.1088/0004-637X/764/2/146

[^2_5]: https://arxiv.org/pdf/1803.00575.pdf

[^2_6]: https://www.aanda.org/articles/aa/full_html/2011/04/aa15378-10/aa15378-10.html

[^2_7]: https://academic.oup.com/mnras/article/466/4/3973/2738739

[^2_8]: https://iopscience.iop.org/article/10.3847/1538-4357/ad05b9

[^2_9]: 02ca0590-0163-420d-a549-97702c7aae03.md

[^2_10]: 854a647e-e40e-4af1-bdb7-6659c98f2b8c.md

[^2_11]: 4509b81a-9a17-4e17-91ba-09ff4cfb66c3.md

[^2_12]: https://www.ismo.universite-paris-saclay.fr/en/jwst-and-alma-reveal-the-birth-of-a-new-solar-system/

[^2_13]: https://www.youtube.com/watch?v=Z3BaoN4sXz8

[^2_14]: https://www.aanda.org/articles/aa/full_html/2019/09/aa35278-19/aa35278-19.html

[^2_15]: https://www.aanda.org/articles/aa/full_html/2016/06/aa28349-16/aa28349-16.html

[^2_16]: https://www.youtube.com/watch?v=4HbKlipwwe4

[^2_17]: https://www.eso.org/public/france/images/eso2512a/

[^2_18]: https://arxiv.org/html/2512.21493v1


---

# @GitHub @Academic Yea give my code the ol look over, and our existing research in docs/research. I don't think I have anything in there mentioning HOPS-315. Give us a gaps analysis, and ground this latest work (the small bodies precipitating out of the parcels) in academic literature.@EBSCOhost I just kinda... I pulled this thought process out of my ass, because the previous model was collapsing entire parcels, which are 1-4k earth masses, so it was impossible to get bodies smaller than that, we got stars, gas giants, *VERY* large terrestrial planets (1k earths) and life was forming...  definately take a look at the receipts and the specs,  as well as the code. You are most likely to discover the thought process behind how we got to the code, any steps missing I haven't described, in those.

You’re on solid conceptual ground: “small bodies precipitating out of parcels” is very close to how current planet‑formation models treat dust condensation and planetesimal formation, but your docs and (the parts I can see) don’t yet explicitly tie that to HOPS‑315, streaming instabilities, or modern coagulation/planetesimal literature.[^3_1][^3_2][^3_3][^3_4][^3_5]

Below I’ll do two things:

- Sketch a gaps analysis between your current Truth docs and contemporary planet-formation work.
- Show how to ground your “parcel → small bodies” idea directly in published models and in the HOPS‑315 observations.

***

## What’s currently in your Truth research

Your research docs describe a **nebular → solar system bootstrap**, but they don’t yet specify the microphysics of dust and planetesimal formation.[^3_1]

Key elements I can see:

- Truth starts with a **stellar nebula** that collapses into a star and planetary system; this is framed as an “epistemic grounding ritual” where the player watches physical law being established.[^3_1]
- You plan explicit `domain/stellar.clj` and `domain/accretion.clj` namespaces for protostellar collapse and planetesimal formation, but these are architectural placeholders rather than physics specs.[^3_1]
- Earlier design notes talk about **large parcels of gas** (1–4k Earth masses) collapsing into stars, giant planets, and extremely massive terrestrials, which matches your description of the old model where entire parcels collapse and you can’t get smaller bodies.[^3_1]

What’s missing is:

- A clear dust/grain phase: how gas carries solids, how and when they condense.
- A statistical or multi-phase treatment that lets small bodies exist below the mass scale of a parcel.
- References to specific observational constraints like HOPS‑315 or theoretical constructs like streaming instabilities and pebble accretion.

***

## Where the gaps are vs. modern planet formation

### 1. Dust and condensation physics

The HOPS‑315 work shows **hot silicate minerals condensing directly out of SiO gas** in a tightly confined inner-disk zone, essentially giving you an observational anchor for “t = 0” of rocky planet formation.[^3_6][^3_7][^3_8]

In the literature, global models treat:

- Dust as a continuum with a **size distribution and surface density**, evolving via coagulation, fragmentation, drift, and condensation/evaporation.[^3_3][^3_5]
- Refractory solids (silicates, metals) condensing at specific temperatures and radii, as seen in embedded disks and now directly in HOPS‑315.[^3_8][^3_6]

Your docs talk about nebular collapse and accretion in general terms but do not yet define:

- A dust fraction or grain field attached to each SPH parcel.
- Temperature-dependent condensation thresholds for silicates and metals.
- How condensed solids feed into the discrete “body” population you simulate.[^3_1]


### 2. Planetesimal formation from dust

Recent models link dust content and planetesimal mass via **localized concentration and collapse**, often through streaming instabilities: dust self-concentrates in parts of the disk and then gravitationally collapses into planetesimals.[^3_4][^3_9][^3_3]

They typically:

- Evolve dust in a 1D/2D disk, track where solids pile up, then “convert” some of that mass into planetesimals when thresholds are met.[^3_5][^3_4]
- Use **super‑particles** to represent swarms of pebbles or planetesimals numerically, rather than individual grains.[^3_9]

Your older parcel-based model collapses whole 1–4k Earth-mass blobs, which is closer to “proto‑star/gas‑giant collapse” than to the hierarchical process of dust → pebble → planetesimal → planet.[^3_1]

Your new idea (“small bodies precipitating out of parcels”) fits the literature much better, but the docs don’t yet spell out:

- A criterion for when and where parcels start shedding solids.
- How parcel mass is partitioned between gas and solids.
- How many and what kind of discrete bodies are spawned, and with what mass spectrum.[^3_1]


### 3. Collisional evolution and growth

Growth from planetesimals to planets depends on **collision physics**—sticking, bouncing, erosion, fragmentation—rather than pure “merge‑on‑contact”.[^3_10][^3_11][^3_5]

Models show that:

- Collision outcomes depend on relative speed, size, and material; low‑velocity collisions can stick, while high‑speed ones fragment.[^3_11][^3_5]
- Collision rates are controlled by number density, velocity dispersion, and stirring from massive bodies or gas disk structures.[^3_12][^3_10]

Your note that “planetesimals do not collide a lot” is exactly what simple N‑body integration with low eccentricities tends to produce, unless you add stirring and realistic collision laws.[^3_10][^3_12]

In the Truth docs:

- Collision physics is not yet specified.
- There’s no mention of fragmenting collisions or velocity-dependent sticking.
- There’s no link from your ECS/body model to a statistically driven collisional growth module.[^3_2]

***

## Grounding “small bodies precipitating out of parcels” in the literature

Let’s connect your current intuition to specific papers and the HOPS‑315 observations.

### 1. HOPS‑315: condensation in an inner disk parcel

In HOPS‑315, JWST and ALMA see **SiO gas at a few hundred degrees in a narrow inner-disk region**, with evidence that at higher temperatures in that same parcel, crystalline silicate minerals like forsterite have formed and are starting to grow.[^3_7][^3_6][^3_8]

Conceptually:

- A “parcel” of inner-disk gas cools through the silicate condensation temperature range.
- Within that parcel, refractory solids nucleate and grow from dust to fingernail‑sized grains.
- Those solids remain dynamically coupled to the gas until they reach sizes where drag and self‑gravity let them decouple and concentrate.[^3_3][^3_6][^3_7]

That’s almost exactly your story: **a gas parcel that, as it evolves, precipitates small bodies** rather than collapsing wholesale.

### 2. Dust → pebbles → planetesimals via parcel-based rules

Garaud et al. (2013) and related work model dust evolution and collisional growth from microscopic grains up to kilometer-scale planetesimals.[^3_5]

Their key ideas you can adopt:

- Treat grains statistically: track surface density and size distribution per “annulus” or cell rather than discretely.[^3_3][^3_5]
- When solids in a region reach certain size/density conditions, transition a fraction of that mass into planetesimals—i.e., **spawn discrete bodies out of a parcel’s solid mass budget**.[^3_4][^3_5]
- Use **super‑particles** that each represent a swarm of smaller bodies to keep N manageable.[^3_9]

Your new “precipitation” mechanism can be grounded as:

> For each parcel (SPH cell) with gas density $\rho_g$, dust fraction $f_d$, and temperature $T$, evolve $f_d$ and grain size according to a simplified coagulation/drift law; when $T$ falls below silicate condensation and $f_d$ exceeds some threshold relative to $\rho_g$, convert a fraction of dust mass into one or more planetesimal super‑particles.

That’s directly consistent with the **disk‑buildup + planetesimal formation** models on arXiv and A\&A.[^3_4][^3_5][^3_3]

### 3. Streaming instability as “parcel self-collapse” of solids

Streaming instability models show that when the local dust-to-gas ratio passes $\sim 1$ in some region, drag forces and self‑gravity can trigger rapid clumping and collapse into planetesimals—“dust clouds” self‑collapse without the gas collapsing globally.[^3_9][^3_4]

For Truth:

- Your parcels can represent regions where the solids have self‑concentrated.
- “Precipitating small bodies” is then the simulation shorthand for a **streaming instability event**: the solids in a parcel collapse into planetesimals while the gas remains mostly diffuse.[^3_4][^3_9]
- You don’t need to model the instability microphysics; you just need a trigger condition and a mass conversion step.

That’s far closer to the current state of the field than collapsing entire 1–4k Earth-mass parcels into a single body.

***

## Concrete model upgrades for Truth

Here’s how I’d translate all that into Truth’s architecture and your latest code direction.

### 1. Enrich parcels with dust fields

For each SPH parcel, add dust-related state:

- `:dust-mass` or `:dust-fraction` relative to gas mass.
- Optional `:mean-grain-size` or a small categorical size flag (e.g. dust/pebbles/boulders).
This turns your parcels into **gas+solids carriers** like the annuli in disk models.[^3_5][^3_3]

Tie dust fractions to temperature:

- At $T > T_\text{condense}$ (silicate condensation range from HOPS‑315), assume solids are vaporized and dust mass is carried as SiO or other gas phase.[^3_6][^3_7]
- As parcels cool below those thresholds, move mass from gas phase into dust phase, increasing `:dust-mass`.[^3_8][^3_6]


### 2. Define precipitation/planetesimal spawn rules

Add a rule: when in a parcel

- `dust-to-gas-ratio` exceeds some critical value (say, inspired by streaming instability thresholds), and
- Temperature is in the “cool but not fully icy” regime (inner-disk solids),

then:

- Compute a **planetesimal formation efficiency** (fraction of dust mass converted), inspired by global models that translate dust to planetesimal mass.[^3_3][^3_4]
- Spawn a small number of planetesimal **super‑particles**, each with:
    - `mass` drawn from a simple power-law or lognormal distribution.
    - `position` seeded within the parcel.
    - `body-kind` = `:body/planetesimal` in your ECS vocabulary.[^3_2]
- Reduce `:dust-mass` in the parcel by the mass allocated to those super‑particles.

This is precisely the “small bodies precipitate out of parcels” effect you want, and it’s aligned with the way disk models tie dust content to planetesimal content.[^3_5][^3_3][^3_4]

### 3. Keep grains sub-grid, bodies super-grid

Numerical practice is:

- **Grains and pebbles**: sub-grid, treated as fields or distributions.[^3_3][^3_5]
- **Planetesimals and larger bodies**: resolved as discrete super‑particles.[^3_11][^3_9]

So in Truth:

- You never simulate grains individually; they live in parcel fields.
- Your latest code that tried to drop $10^{16}$ bodies can become a **statistical spawn**: only a handful of representative bodies emerge each tick from parcels, carrying the integrated behavior of an underlying swarm.

That preserves the spirit of your idea while staying numerically sane and academically grounded.

### 4. Upgrade collision and growth

To make planetesimals actually grow:

- Add a collision module that:
    - Computes an expected collision rate per zone from number density and velocity dispersion.[^3_12][^3_10]
    - Applies a simple outcome law: low‑velocity collisions stick and merge; high‑velocity collisions erode or fragment.[^3_11][^3_5]
- Implement this either:
    - Directly with pairwise checks for a small N of bodies, or
    - Statistically per parcel for “background” collisions.

This will make your planetesimals actually interact in a way that resembles the “dust to planetesimals to planets” pathway used in current models.[^3_10][^3_11][^3_5]

***

## How this shows up in docs/research

To close the gaps in `docs/research`:

- Add a **HOPS‑315 section** that explicitly states:
    - Inner-disk condensation of refractory solids at 1–2 AU.
    - Observed SiO gas and inferred crystalline silicates, and how that maps to Truth’s condensation triggers.[^3_7][^3_6][^3_8]
- Spell out a **parcel→solids→planetesimals** subsection referencing:
    - Dust growth + planetesimal formation models (Garaud et al., streaming instability works).[^3_4][^3_5][^3_3]
    - The use of super‑particles in numerical simulations.[^3_9][^3_11]
- Tie this to your four‑quadrant architecture:
    - `domain/accretion` for the dust/planetesimal rules.
    - `law/` Malli schemas for parcels, dust fields, and planetesimal spawn events.
    - `shape/` to handle orbital and spatial geometry for spawned bodies.[^3_2][^3_1]

That way, the “thought process you pulled out of your ass” becomes a documented, literature‑anchored design decision:

> “In Truth, SPH parcels represent regions of a protoplanetary disk where gas and dust co‑evolve; refractory solids condense out of gas when temperatures fall below material‑dependent thresholds, and once solids reach sufficient local abundance, a fraction of that mass is converted into discrete planetesimal bodies, in line with dust‑to‑planetesimal conversion schemes used in contemporary disk models and observations of embedded systems such as HOPS‑315.”[^3_6][^3_5][^3_3][^3_4]

If you want, we can next sketch a concrete `domain.accretion` API and Malli schemas that encode those parcel/dust/planetesimal transitions without breaking your namespace law.
<span style="display:none">[^3_13][^3_14][^3_15][^3_16][^3_17][^3_18][^3_19][^3_20][^3_21][^3_22][^3_23][^3_24][^3_25][^3_26][^3_27][^3_28]</span>

<div align="center">⁂</div>

[^3_1]: 02ca0590-0163-420d-a549-97702c7aae03.md

[^3_2]: 854a647e-e40e-4af1-bdb7-6659c98f2b8c.md

[^3_3]: https://www.aanda.org/articles/aa/full_html/2019/09/aa35278-19/aa35278-19.html

[^3_4]: https://arxiv.org/pdf/1803.00575.pdf

[^3_5]: https://iopscience.iop.org/article/10.1088/0004-637X/764/2/146

[^3_6]: https://www.almaobservatory.org/en/press-releases/for-the-first-time-astronomers-witness-the-dawn-of-a-new-solar-system/

[^3_7]: https://skyandtelescope.org/astronomy-news/webb-space-telescope-spies-baby-planetary-system/

[^3_8]: https://www.youtube.com/watch?v=4HbKlipwwe4

[^3_9]: https://iopscience.iop.org/article/10.3847/1538-4357/835/1/109

[^3_10]: https://www.aanda.org/articles/aa/full_html/2011/04/aa15378-10/aa15378-10.html

[^3_11]: https://iopscience.iop.org/article/10.3847/1538-4357/ad05b9

[^3_12]: https://academic.oup.com/mnras/article/466/4/3973/2738739

[^3_13]: https://github.com/sotos82/SolarSystemSimulatorGame

[^3_14]: https://github.com/Rajil1213/Graphics-Project-Solar-System-Simulation

[^3_15]: https://discussions.unity.com/t/solar-system-simulator-game/583328

[^3_16]: https://github.com/jpretori/orbit3d-octave

[^3_17]: https://github.com/topics/solar-system-simulation?o=desc\&s=stars

[^3_18]: https://www.youtube.com/watch?v=WTLPmUHTPqo

[^3_19]: https://github.com/VirtualPlanetaryLaboratory/vplanet

[^3_20]: https://github.com/jan-tennert/solarsim

[^3_21]: https://news.ycombinator.com/item?id=48459837

[^3_22]: https://gnu-octave.github.io/packages/sole/

[^3_23]: https://github.com/topics/planetary-science

[^3_24]: https://github.com/schneems/Octave/actions

[^3_25]: https://github.com/OlivierGaland/Octave/activity

[^3_26]: https://github.com/cesar-ilharco/octave_simulation_framework

[^3_27]: https://github.com/gnu-octave/octave

[^3_28]: 4509b81a-9a17-4e17-91ba-09ff4cfb66c3.md


