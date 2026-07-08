---

# continue

Yes — let’s turn this into the first real **canonical catalog**. Truth already gives you the governing idea: resolved gas collapse, resolved disk evolution, and sub-grid planets, with different observables owning different transitions rather than one variable faking all of them.

## Catalog rules

These tables are a proposed design layer for Truth, not a description of what is fully implemented today. They follow the formation spec’s core discipline: mass decides fate tiers, density decides condensation, and temperature/pressure decide ignition, while atmospheric escape later needs its own distinct regimes rather than being folded into one generic “wind loss” rule.

## Matter catalog

Use this FSM to answer one question only: **what physically is this entity?** Role, climate, and life should be separate machines.


| State | Owner | Required observables | Entry guard | Exit guard | Allowed successors | Notes |
| :-- | :-- | :-- | :-- | :-- | :-- | :-- |
| `:matter/nebula` | `domain.stellar` | density, temperature, pressure support, local self-gravity, velocity dispersion | Default diffuse gas state. | Becomes locally collapse-eligible when Jeans-like instability is met and support loses to gravity. | `:matter/collapsing-gas` | Canonical starting gas state for resolved cloud evolution. |
| `:matter/collapsing-gas` | `domain.stellar` | same as nebula + collapse timescale, convergence, local overdensity | Enter when gas is unstable and converging, but not yet a bound core. | Exit when core-condensation threshold is crossed or collapse is disrupted. | `:matter/condensed-core`, `:matter/nebula` | Important missing intermediate so “about to collapse” is not the same as “already condensed.” |
| `:matter/condensed-core` | `domain.stellar` | boundness, density above core threshold, mass | Enter when gas forms a bound core. | Exit when mass tier and thermal structure determine fate. | `:matter/protostar`, `:matter/brown-dwarf`, `:matter/gas-giant-embryo`, `:matter/planetesimal-seed-source` | This is the branch point where fate is decided mainly by mass tier, consistent with the formation spec. |
| `:matter/protostar` | `domain.stellar` | mass, core temperature, pressure, hydrogen fraction, accretion rate | Enter from condensed core when mass is above H-burning threshold but stable fusion is not yet on. | Exit when fusion becomes self-sustaining or when contraction fails / mass is stripped. | `:matter/star`, `:matter/brown-dwarf`, `:matter/stellar-remnant` | Mirrors the spec’s “mass sets fate, ignition sets starhood” logic. |
| `:matter/star` | `domain.stellar` | fusion state, composition, mass loss, luminosity | Enter when fusion is possible and self-sustaining. | Exit only when fusion can no longer continue, not on small transient mass dips alone. | `:matter/stellar-remnant` | This follows the spec’s anti-flicker hysteresis idea for stars. |
| `:matter/stellar-remnant` | `domain.stellar` | residual mass, cooling luminosity, compact radius | Enter when a formerly collapsed/fusing body is no longer sustaining fusion but remains bound. | Terminal except for cooling, mergers, or total ablation. | terminal, or merger products | Matches the wind/remnant direction in the stellar-wind spec rather than letting bound bodies drift back to nebula. |
| `:matter/dust-field` | `domain.planet-formation` | dust fraction, condensation chemistry, grain-size distribution proxy | Enter when disk solids are present but remain sub-resolved as fine grains. | Exit when grains grow/coagulate into pebbles or are vaporized back into gas chemistry. | `:matter/pebble-field`, chemistry back to gas field | Needed because planet formation starts in condensed solids fields, not immediate planetesimals. [^15_1] |
| `:matter/pebble-field` | `domain.planet-formation` | Stokes number proxy, dust-to-gas ratio, local midplane enhancement | Enter when solids are large enough to drift and concentrate aerodynamically. | Exit when streaming/clumping produces bound small bodies or when pebbles are lost/accreted. | `:matter/planetesimal`, `:matter/dust-field` | This is the right home for streaming-instability preconditions. [^15_1] |
| `:matter/planetesimal` | `domain.planet-formation` | size, mass, composition, internal strength, collision history | Enter when solids become bound small bodies. | Exit when persistent classification shifts to asteroid/comet/protoplanet, or body is disrupted. | `:matter/asteroid`, `:matter/comet`, `:matter/protoplanet`, `:matter/debris-cloud` | Truth’s current spec treats this tier as real and important, not a fake direct jump to planet. |
| `:matter/asteroid` | `domain.planet-formation` | rocky composition, non-rounded shape proxy, low volatile fraction | Enter when a planetesimal remains a small rocky body without hydrostatic roundness. | Exit on merger, catastrophic breakup, or promotion by growth. | `:matter/protoplanet`, `:matter/debris-cloud` | Useful persistent class for inner small bodies and belts. |
| `:matter/comet` | `domain.planet-formation` | volatile fraction, thermal activity, sublimation behavior | Enter when a small body is volatile-rich and behaves as an icy outgassing object under heating. | Exit on volatile exhaustion, disruption, or accretion. | `:matter/asteroid`, `:matter/debris-cloud`, accreted into larger body | Keeps icy active bodies distinct from rocky asteroids. |
| `:matter/protoplanet` | `domain.planet-formation` | mass, roundness proxy, differentiation, accretion rate | Enter when a growing solid body becomes large enough to dominate local collisions but has not yet settled into final classification. | Exit on orbit-clearing outcome, satellite capture outcome, or volatile/gas accretion outcome. | `:matter/dwarf-planet`, `:matter/planet`, `:matter/gas-giant`, `:matter/ice-giant` | Good bridge for Moon-forming impacts, Mars-like stalled growth, and failed embryos. |
| `:matter/dwarf-planet` | `domain.planet-formation` | self-rounding, composition, local dynamical dominance proxy | Enter when a body is round/self-gravitating but does not clear its orbital zone. | Exit on merger, capture, or rare promotion through later clearing. | `:matter/planet`, `:matter/debris-cloud` | Gives Pluto and Ceres a home without calling everything a planet. |
| `:matter/planet` | `domain.planet-formation` | mass, roundness, neighborhood-clearing proxy | Enter when a body is round and dynamically dominant in its orbital neighborhood. | Exit only through catastrophic disruption, engulfment, or stellar evolution effects. | `:matter/debris-cloud` or swallowed by star | This state should stay agnostic about climate; Earth and Mars are both planets here. |
| `:matter/gas-giant` | `domain.planet-formation` | total mass, envelope fraction, disk gas supply | Enter when a core or clump acquires a dominant gas envelope. | Exit by severe stripping, merger, or stellar evolution. | `:matter/ice-giant`, stripped planet variants | Core-accretion and GI channels can both land here, which is why channel is not the same as matter state. [^15_1] |
| `:matter/ice-giant` | `domain.planet-formation` | volatile-rich interior, smaller H/He envelope fraction | Enter when a giant planet is dominated by ices/volatiles rather than a huge H/He envelope. | Exit by stripping/merger only. | stripped planet variants | Separate from gas giant because Uranus/Neptune-like outcomes matter structurally and climatically. |
| `:matter/debris-cloud` | `domain.planet-formation` | fragment count proxy, unbound/bound fraction, collision energy | Enter after disruption or intense grinding collisions. | Exit when debris reaccretes, forms rings, or is cleared. | `:matter/ring-particle`, `:matter/planetesimal`, removal | Important for Moon-forming impacts, ring formation, and bombardment aftermath. |
| `:matter/ring-particle` | `domain.planet-formation` | Roche-limit context, particle population, host gravity | Enter when debris persists inside reaccretion-suppressed ring conditions. | Exit when rings spread, accrete into moons outside Roche regime, or decay. | `:matter/debris-cloud`, moon-seed populations | Gives Saturn-like rings and post-impact disks a true state. |

## Environment catalog

Use this FSM to answer a different question: **what regime is the body’s surface/interior in right now?** A body can remain `:matter/planet` while moving through several environment states over time.


| State | Owner | Required observables | Entry guard | Exit guard | Allowed successors | Notes |
| :-- | :-- | :-- | :-- | :-- | :-- | :-- |
| `:env/magma-ocean` | `domain.environment` | melt fraction, surface temperature, impact energy flux, radiative cooling | Enter when global or near-global melt dominates the surface. | Exit when sustained crust can form between major reset events. | `:env/impact-reset`, `:env/crusted-volcanic` | Early rocky worlds often begin here. |
| `:env/impact-reset` | `domain.environment` | bombardment energy rate, resurfacing fraction, crust persistence time | Enter when large impacts repeatedly remelt or sterilize much of the surface. | Exit when impact cadence drops below reset threshold. | `:env/crusted-volcanic`, back to `:env/magma-ocean` | This gives you the “habitable for a while, then partially molten again” loop you called out. |
| `:env/crusted-volcanic` | `domain.environment` | crust fraction, volcanism, outgassing, internal heat flux | Enter when a stable crust exists but internal heat still strongly shapes the world. | Exit when climate/hydrosphere stabilizes, air is lost, or tidal/impact forcing dominates. | `:env/ocean-world`, `:env/arid-thin-atmosphere`, `:env/airless-inert`, `:env/tidally-heated` | Good home for early Venus, Io-like rocky states, and young terrestrials. |
| `:env/ocean-world` | `domain.environment` | stable liquid inventory, pressure, temperature, salinity/chemistry proxy | Enter when surface liquid is persistent on geologic timescales. | Exit on freeze-out, desiccation, runaway heating, or atmosphere collapse. | `:env/temperate-habitable`, `:env/snowball`, `:env/runaway-greenhouse`, `:env/post-habitable` | This is broader than “habitable”; not every ocean world must be biologically friendly. |
| `:env/temperate-habitable` | `domain.environment` | persistent liquid solvent, stable climate window, tolerable radiation, long-lived atmosphere | Enter when climate and solvent stability remain inside a life-friendly band. | Exit when heat, cold, impacts, or atmosphere loss push it out. | `:env/post-habitable`, `:env/snowball`, `:env/runaway-greenhouse`, `:env/impact-reset` | Habitability is a regime, not a permanent badge. |
| `:env/post-habitable` | `domain.environment` | evidence/history of prior habitable regime plus present failure mode | Enter when a once-habitable world leaves the life-friendly window for secular reasons. | Exit only if conditions recover for long enough. | `:env/arid-thin-atmosphere`, `:env/snowball`, `:env/temperate-habitable` | This is the clean state for Mars-like “likely habitable once” worlds. |
| `:env/arid-thin-atmosphere` | `domain.environment` | low surface pressure, cold/dry climate, weak volatile cycling | Enter when a rocky world retains only a tenuous atmosphere and little stable surface liquid. | Exit on renewed atmosphere/ocean build-up or full collapse to airless. | `:env/airless-inert`, `:env/post-habitable`, `:env/temperate-habitable` | Mars belongs here better than in snowball or runaway-greenhouse. |
| `:env/airless-inert` | `domain.environment` | negligible atmosphere, low active resurfacing, exposed surface | Enter when atmosphere is effectively absent and surface evolution is slow/inertial. | Exit only through major resurfacing, capture of dense atmosphere, or extreme heating. | `:env/magma-ocean`, `:env/crusted-volcanic` | Strong fit for the modern Moon and many asteroids. |
| `:env/snowball` | `domain.environment` | global ice cover, high albedo feedback, low liquid-surface fraction | Enter when freezing feedback pushes surface liquid out globally. | Exit on sufficient warming or internal/ocean retention under ice. | `:env/ocean-world`, `:env/subsurface-ocean`, `:env/post-habitable` | Distinct from Pluto-like icy worlds because this is a climatic freeze of a potentially temperate-type world. |
| `:env/runaway-greenhouse` | `domain.environment` | radiative imbalance, volatile greenhouse loading, water loss trajectory | Enter when greenhouse forcing drives irreversible extreme heating and water loss. | Exit only under dramatic atmospheric loss or stellar evolution changes. | `:env/post-habitable` | Distinct from merely “hot.” |
| `:env/icy-volatile-world` | `domain.environment` | low temperatures, surface volatile ices, sublimation/condensation cycling | Enter when a small or distant world is dominated by frozen volatile behavior. | Exit on major warming, tidal heating, or interior activation. | `:env/cryovolcanic`, `:env/subsurface-ocean` | Pluto belongs here naturally. |
| `:env/subsurface-ocean` | `domain.environment` | internal heat, ice shell, liquid layer below surface, pressure structure | Enter when surface stays frozen but liquid persists below the ice shell. | Exit on freeze-through or surfacing through cryovolcanism/tidal disruption. | `:env/cryovolcanic`, `:env/icy-volatile-world` | Europa-like state; life-relevant even when surface is not habitable. |
| `:env/tidally-heated` | `domain.environment` | orbital resonance forcing, dissipation, heat flux | Enter when tidal dissipation is the dominant environmental power source. | Exit when resonance/heating weakens. | `:env/crusted-volcanic`, `:env/subsurface-ocean` | A modifier could handle this too, but making it a state is useful when tides dominate the whole regime. |
| `:env/cryovolcanic` | `domain.environment` | subsurface volatile reservoirs, fracture transport, episodic venting | Enter when icy interior activity is expressed at the surface. | Exit if interior freezes or heating fades. | `:env/icy-volatile-world`, `:env/subsurface-ocean` | Good for Pluto/Enceladus-like active icy worlds. |

## Hard boundaries

To keep this machine from turning mushy, each FSM needs a **single interpretation contract**. The formation spec already gives the pattern: distinct observables own distinct decisions, and the atmospheric research does the same by splitting escape into separate regimes instead of one universal law.

So the contract should be:

- **Matter FSM** decides physical identity.
- **Role FSM** decides orbital/social placement.
- **Environment FSM** decides current surface/interior regime.
- **Atmosphere/EM FSM** decides retention, stripping, and shielding regime.
- **Biosphere FSM** decides level of life.

That means:

- Mars is a `planet` in Matter, not a special failed class.
- Pluto is a `dwarf-planet` in Matter and an `icy-volatile-world` in Environment.
- The Moon is a rocky body in Matter, `satellite` in Role, and `airless-inert` in Environment.


## Priority order

The Environment FSM especially needs ordered guards, because several conditions can be true at once. A good evaluation order is: melt first, then bombardment reset, then airless collapse, then frozen/volatile states, then runaway greenhouse, then temperate/ocean states, then residual arid/post-habitable states.

That ordering is what lets a world move cleanly through arcs like:

- early Earth: `magma-ocean → impact-reset → crusted-volcanic → ocean-world → temperate-habitable`
- Mars-like: `crusted-volcanic → ocean-world → post-habitable → arid-thin-atmosphere`
- Pluto-like: `icy-volatile-world ↔ cryovolcanic ↔ subsurface-ocean`


## Next tables

The next two catalogs should be:

- **Role FSM**, for orbit-clearer, satellite, belt member, scattered body, resonant body, ring member.
- **Atmosphere/EM FSM**, for stable dynamo, weak dynamo, collapsed magnetosphere, retained atmosphere, actively stripped atmosphere, frozen atmosphere, and the XUV escape regimes the atmosphere research already distinguishes.

Would you like me to draft those next in the same canonical table format?

<div align="center">⁂</div>

[^15_1]: https://www.aanda.org/articles/aa/full_html/2019/09/aa35278-19/aa35278-19.html


---

# continue

Perfect. The habitability handoff spec gives us exactly the bridge we needed: Phase 0 should not “make life,” but it **must** hand forward planet candidates with material class, thermal band, atmosphere class, retained species, dynamo estimate, and orbital stability.  That means the next two FSMs should be designed not just as simulation categories, but as **producers of the handoff contract**.

## Role catalog

Use the Role FSM to answer: **what is this entity’s dynamical relationship inside the system?** This is separate from what the thing physically is.


| State | Owner | Required observables | Entry guard | Exit guard | Allowed successors | Notes |
| :-- | :-- | :-- | :-- | :-- | :-- | :-- |
| `:role/free-body` | `domain.orbital` / `domain.planet-formation` | orbit elements, host binding energy, nearby dominant masses | Default for a resolved non-gas body not yet assigned to a more specific dynamical role. | Exit when the body becomes disk-embedded, belt-bound, satellite-bound, or orbit-clearing. | `:role/disk-embedded`, `:role/belt-member`, `:role/satellite`, `:role/orbit-clearer`, `:role/scattered-body` | Good neutral default for embryos and isolated bodies. |
| `:role/disk-embedded` | `domain.planet-formation` | gas density around orbit, relative drift, local disk membership | Enter when a body is still dynamically coupled to a gas disk strongly enough that migration and accretion are disk-mediated. | Exit when gas disperses or the body decouples into a mature orbit. | `:role/free-body`, `:role/orbit-clearer`, `:role/satellite` | This matches Truth’s resolved-disk / sub-grid planet architecture. |
| `:role/belt-member` | `domain.orbital` | semimajor axis clustering, non-cleared neighborhood, many-body local population | Enter when a body persists as part of a shared orbital population rather than dominating its region. | Exit when scattered, accreted, captured, or promoted into orbit-clearer status. | `:role/scattered-body`, `:role/satellite`, `:role/orbit-clearer`, removal by collision | This is where asteroids, Kuiper-belt objects, and many dwarf planets belong dynamically. |
| `:role/resonant-member` | `domain.orbital` | mean-motion resonance ratio, libration proxy | Enter when a body is stably trapped in resonance with a stronger perturber. | Exit when resonance breaks. | `:role/belt-member`, `:role/scattered-body`, `:role/orbit-clearer` | Useful for Pluto-like outcomes and migration history. |
| `:role/scattered-body` | `domain.orbital` | high eccentricity/inclination, repeated close encounters, weak local stability | Enter when giant-planet perturbations or strong encounters eject a body from a quiet population. | Exit on recapture, ejection, or damping into a stable population. | `:role/interstellar-escape`, `:role/belt-member`, `:role/satellite` | This is where Jupiter-like architecture matters. |
| `:role/orbit-clearer` | `domain.orbital` | Hill sphere dominance, local mass ratio, long-term orbital stability | Enter when a body dominates its orbital neighborhood strongly enough to count as a primary planet in the dynamical sense. | Exit only if later destabilized, engulfed, or demoted by extreme system evolution. | usually terminal | This is the clean dynamic distinction between planets and dwarf planets. |
| `:role/satellite` | `domain.orbital` | bound orbit around non-stellar primary, Hill-stable capture zone | Enter when the body’s dominant gravitational relationship is to a planet or dwarf planet rather than the star. | Exit on escape, collision, tidal disruption, or reclassification into rings/debris. | `:role/ring-member`, `:role/free-body` | This is where the Moon belongs; “moon” is better treated as role than matter. |
| `:role/ring-member` | `domain.orbital` | Roche-regime host relation, ring-plane membership, non-accreting orbit | Enter when a fragment population persists inside a reaccretion-suppressed ring zone. | Exit when spreading, reaccretion, or clearing removes the ring. | `:role/satellite`, `:role/debris-associated` | Best for Saturn-like rings or post-impact disks. |
| `:role/co-orbital` | `domain.orbital` | shared semimajor axis, Trojan/horseshoe stability proxy | Enter when a body occupies a persistent co-orbital configuration. | Exit when instability breaks shared-orbit behavior. | `:role/free-body`, `:role/scattered-body` | Optional at first, but useful for richer system architectures. |
| `:role/interstellar-escape` | `domain.orbital` | total orbital energy > 0 relative to star/system barycenter | Enter when the body is no longer bound to the system. | Terminal except rare recapture. | terminal | Makes ejection explicit rather than silent despawn. |

## Role rules

This FSM should be decided by **orbital dominance and binding**, not composition or climate. The handoff spec already wants each candidate planet bound to the star, reasonably stable, and not on obviously unstable or plunging orbits.  That means Role FSM and orbit-stability logic are tightly coupled: if something is not at least a stable `free-body` or `orbit-clearer`, it probably should not qualify as a Phase‑0 candidate.

This also gives you a clean dynamic reading of the Solar System:

- Earth: `:role/orbit-clearer`
- Pluto: `:role/resonant-member` plus probably `:role/belt-member`
- Moon: `:role/satellite`
- Main-belt asteroid: `:role/belt-member`
- Long-period comet: `:role/scattered-body`


## Atmosphere / EM catalog

Use this FSM to answer: **what is happening to a body’s atmosphere and magnetic shielding right now?** The handoff spec already asks whether the body retains an atmosphere, what rough class it has, what species are retained, and whether it has a core dynamo.  The atmosphere research then adds a more detailed escape-regime ladder, distinguishing energy-limited, recombination-limited, photon-limited, and blow-off escape rather than one generic stripping mode.

I would treat this as one catalog with two linked subdomains: **magnetosphere state** and **atmosphere-retention/escape state**.

### Magnetosphere states

| State | Owner | Required observables | Entry guard | Exit guard | Allowed successors | Notes |
| :-- | :-- | :-- | :-- | :-- | :-- | :-- |
| `:mag/no-dynamo` | `domain.em` / `domain.interior` | rotation rate, convective core proxy, conductivity, heat flux | Enter when there is no sustained core dynamo. | Exit if convective+rotational conditions recover enough for dynamo onset. | `:mag/episodic-dynamo`, `:mag/stable-dynamo` | Good default for the Moon, Pluto, many small bodies. |
| `:mag/episodic-dynamo` | `domain.em` / `domain.interior` | same as above plus time variability | Enter when dynamo action is intermittent or weakly sustained. | Exit on stabilization or collapse. | `:mag/stable-dynamo`, `:mag/no-dynamo` | Good for transitional Mars-like or cooling-world cases. |
| `:mag/stable-dynamo` | `domain.em` / `domain.interior` | convective power above threshold, adequate rotation, magnetic dipole estimate | Enter when a robust global magnetic field is maintained. | Exit when core cooling/rotation changes shut it down, or external compression dominates. | `:mag/compressed`, `:mag/episodic-dynamo`, `:mag/no-dynamo` | Earth-like planetary protection state. |
| `:mag/compressed` | `domain.em` / `domain.atmosphere` | stellar-wind pressure, magnetopause stand-off estimate | Enter when a magnetosphere exists but is strongly compressed by stellar wind or XUV activity. | Exit when external pressure drops or field weakens to collapse. | `:mag/stable-dynamo`, `:mag/collapsed` | Important around active young stars. |
| `:mag/collapsed` | `domain.em` / `domain.atmosphere` | magnetic pressure < wind pressure at effective shielding boundary | Enter when the field no longer meaningfully shields the atmosphere. | Exit only if dynamo strengthens or stellar forcing weakens enough to restore shielding. | `:mag/compressed`, `:mag/no-dynamo` | Distinct from “no dynamo”: even a field can fail to protect under strong wind. |

### Atmosphere states

| State | Owner | Required observables | Entry guard | Exit guard | Allowed successors | Notes |
| :-- | :-- | :-- | :-- | :-- | :-- | :-- |
| `:atm/none` | `domain.atmosphere` | escape velocity, temperature, volatile inventory | Enter when no atmosphere can be retained at useful scale. | Exit if major outgassing, volatile delivery, or cooling creates a retained atmosphere. | `:atm/transient-outgassed`, `:atm/stable-secondary` | Moon-like endpoint; also useful for many asteroids. |
| `:atm/transient-outgassed` | `domain.atmosphere` | outgassing rate, volatile release, escape rate | Enter when an atmosphere exists but replenishment is episodic and retention weak. | Exit when it stabilizes or is lost. | `:atm/stable-secondary`, `:atm/collapsing`, `:atm/none` | Young rocky worlds often pass through this phase. |
| `:atm/stable-secondary` | `domain.atmosphere` | retained species, v_esc/v_thermal, replenishment vs loss | Enter when atmosphere retention is plausible over long intervals. | Exit on strong stripping, freeze-out, runaway greenhouse, or collapse. | `:atm/substantial`, `:atm/actively-stripped`, `:atm/frozen`, `:atm/collapsing` | This aligns well with the handoff spec’s “thin / substantial / thick” idea. |
| `:atm/substantial` | `domain.atmosphere` | surface pressure proxy, retained heavy species, volatile budget | Enter when the atmosphere is persistent and climatically important but not giant-envelope-like. | Exit on collapse, stripping, or runaway growth. | `:atm/thick`, `:atm/actively-stripped`, `:atm/frozen`, `:atm/collapsing` | Useful midpoint between bare-thin and massive atmospheres. |
| `:atm/thick` | `domain.atmosphere` | high column mass, strong greenhouse or dense volatile loading | Enter when atmosphere is thick enough to dominate surface conditions strongly. | Exit on loss or thermal transition. | `:atm/runaway-associated`, `:atm/actively-stripped`, `:atm/collapsing` | Venus-like or Titan-like in different thermal contexts. |
| `:atm/frozen` | `domain.atmosphere` | condensation temperatures, surface pressure, volatile phase stability | Enter when atmospheric volatiles collapse to the surface/ice seasonally or persistently. | Exit on warming or resurfacing release. | `:atm/transient-outgassed`, `:atm/stable-secondary` | Good for Pluto-like or nitrogen-collapse cases. |
| `:atm/collapsing` | `domain.atmosphere` | net loss > replenishment for secular timescales | Enter when atmosphere persists but is clearly on a downward trajectory. | Exit on recovery or full stripping. | `:atm/actively-stripped`, `:atm/none`, `:atm/stable-secondary` | Clean state for late-stage Mars-like decline. |
| `:atm/actively-stripped` | `domain.atmosphere` / `domain.em` | wind flux, XUV flux, shielding state, escape rate | Enter when external forcing dominates and loss is rapid. | Exit when forcing weakens or inventory is exhausted. | `:atm/xuv-energy-limited`, `:atm/xuv-recombination-limited`, `:atm/xuv-photon-limited`, `:atm/none` | This is the gateway into detailed escape regimes. |
| `:atm/xuv-energy-limited` | `domain.atmosphere` | XUV flux, heating efficiency, $R_{\rm XUV}$, gravity | Enter when absorbed XUV power mostly drives escape work and recombination losses are small. | Exit when cooling/recombination or photon supply takes over. | `:atm/xuv-recombination-limited`, `:atm/xuv-photon-limited`, `:atm/blowoff`, `:atm/collapsing` | Matches the atmosphere research’s first main regime. |
| `:atm/xuv-recombination-limited` | `domain.atmosphere` | recombination timescale vs flow timescale, electron density, XUV flux | Enter when radiative/recombination losses dominate enough to flatten the scaling of escape. | Exit when forcing weakens or transitions to another escape regime. | `:atm/xuv-energy-limited`, `:atm/blowoff`, `:atm/collapsing` | Explicitly grounded in the regime-transition research. |
| `:atm/xuv-photon-limited` | `domain.atmosphere` | ionizing photon budget, low-gravity response | Enter when escape is limited mainly by available ionizing photons. | Exit when flux/gravity conditions move it elsewhere. | `:atm/xuv-energy-limited`, `:atm/collapsing`, `:atm/none` | Important for low-mass worlds and some M-dwarf cases. |
| `:atm/blowoff` | `domain.atmosphere` | Roche geometry, inflated $R_{\rm XUV}$, tidal escape enhancement | Enter when atmosphere is in hydrodynamic blow-off or Roche-assisted overflow. | Exit only when enough atmosphere is lost to leave the blow-off regime. | `:atm/collapsing`, `:atm/none` | Extreme but physically distinct; useful for close-in planets. |

## Atmosphere / EM rules

The handoff spec’s coarse atmosphere classes — `:none`, `:thin`, `:substantial`, `:thick` — should probably remain as the **Phase‑0 summary output**, while the FSM above is the internal state machine that produces those simpler tags.  In other words:

- FSM state = rich internal truth.
- Handoff class = compressed outward summary.

Same for magnetism:

- Internal states: `no-dynamo`, `episodic`, `stable`, `compressed`, `collapsed`.
- Handoff output: `:core-dynamo?` boolean plus `:magnetic-field` estimate.

That preserves Phase‑0 tractability while giving you a better causal model.

## Example placements

This catalog handles your examples pretty naturally:

- **Moon**: `role=satellite`, `mag=no-dynamo`, `atm=none`.
- **Mars**: `role/orbit-clearer`, `mag=collapsed` or `episodic-dynamo`, `atm=collapsing` or `actively-stripped`, maybe previously `stable-secondary`.
- **Pluto**: `role=resonant-member`/`belt-member`, `mag=no-dynamo`, `atm=frozen` or tenuous transient-outgassed.
- **Earth**: `role/orbit-clearer`, `mag=stable-dynamo`, `atm=stable-secondary` or `substantial`.


## Canonical layering

If we stack all four catalogs we have now, the design begins to look coherent:

- **Matter FSM**: what the thing is.
- **Role FSM**: where/how it lives dynamically.
- **Environment FSM**: what regime its surface/interior is in.
- **Atmosphere/EM FSM**: whether it keeps/protects gas.

Then the **Biosphere FSM** sits on top and only runs for eligible worlds. The habitability handoff spec already says Phase 0’s job is to produce grounded planet candidates and pass the right structured record forward, not jump straight to life.

## What comes next

The next useful draft is the final top layer:

- **Biosphere FSM**
- plus a short **transition-modifier catalog** for things like `has-large-moon`, `giant-planet-shielding`, `tidal-heating`, `heavy-bombardment-era`, because those should bias transitions without becoming states themselves.

That would complete the conceptual `nebula → life` architecture.

---

# continue

Good — let’s finish the conceptual stack.

At this point the architecture wants two final pieces:

1. a **Biosphere FSM**, which should stay intentionally modest at first, and
2. a **Transition Modifier catalog**, because a lot of the things you care about — Jupiter, the Moon, bombardment eras, tides, migration history — should *shape transitions* rather than become giant primary states.

The habitability handoff spec already tells us that Phase 0’s responsibility is to hand forward worlds with enough structured physical context for later biology/ecology systems to operate, not to fully solve life in the formation phase.

## Biosphere catalog

Use the Biosphere FSM to answer one question only: **what level of organized life or pre-life exists on this world right now?**

This machine should be downstream of the others:

- Matter says whether there is even a world.
- Role says whether the orbit is stable enough.
- Environment says whether the world is molten, oceanic, frozen, airless, etc.
- Atmosphere/EM says whether the world retains and protects gases and chemistry.
- Then, and only then, Biosphere decides whether life can exist and what form it has.

| State | Owner | Required observables | Entry guard | Exit guard | Allowed successors | Notes |
| :-- | :-- | :-- | :-- | :-- | :-- | :-- |
| `:bio/none` | `domain.biology` / `domain.ecology` | solvent availability, energy gradients, chemistry inventory, sterilization rate | Default state when no organized prebiotic or biotic system is present. | Exit when prebiotic chemistry becomes sustained rather than transient. | `:bio/prebiotic` | Default for most bodies. |
| `:bio/prebiotic` | `domain.biology` | persistent solvent niche, organic inventory proxy, redox/UV/geothermal energy, environmental continuity window | Enter when chemistry is rich and persistent enough that self-organizing pre-life processes are plausible. | Exit when replication/evolution emerges, or when environment collapses and chemistry resets. | `:bio/microbial`, `:bio/none` | This is where “life-adjacent” chemistry lives without forcing a yes/no jump to life. |
| `:bio/microbial` | `domain.biology` / `domain.ecology` | replicator persistence, metabolic closure proxy, nutrient cycling, habitat continuity | Enter when self-replicating evolving life exists but remains mostly microbial/simple. | Exit on extinction, or on ecological/energetic complexity sufficient for multicellular/macroscopic organization. | `:bio/complex`, `:bio/extinct` | This should probably be the dominant living state in the universe. |
| `:bio/complex` | `domain.ecology` | oxygenation or alternative energetic surplus, ecological specialization, habitat diversity | Enter when large-scale differentiated ecosystems emerge. | Exit on collapse/extinction or on development of technological civilization. | `:bio/technological`, `:bio/extinct` | Not every microbial world should get here. |
| `:bio/technological` | `domain.ecology` / `domain.culture` | intelligence proxy, tool-use/engineering proxy, sustained surplus energy use, societal persistence | Enter when a biosphere develops technological civilization. | Exit on collapse, extinction, or transformation beyond biospheric dependence. | `:bio/post-biological`, `:bio/extinct` | This is where your ACTORS and cultural systems really begin to matter. |
| `:bio/extinct` | `domain.ecology` | evidence of prior life plus current absence of active biosphere | Enter when life once existed but no longer persists. | Exit only through re-emergence from surviving chemistry or reseeding. | `:bio/prebiotic`, rare `:bio/microbial` | Important for Mars-like or catastrophe-reset histories. |
| `:bio/post-biological` | `domain.culture` / `domain.ecology` | non-biological agent persistence, engineered substrate, decoupling from original biosphere | Enter when intelligence persists in forms no longer biologically rooted in ordinary ecosystem metabolism. | Terminal or transformed-state branch. | terminal / transformed branches | Optional for later, but it belongs in the long arc of Truth. |

## Biosphere rules

This FSM should **not** be allowed to infer physical plausibility on its own. It should only run when upstream machines provide a valid substrate.

A simple eligibility rule could be:

- `:bio/prebiotic` is allowed only if:
    - environment is one of `ocean-world`, `temperate-habitable`, `subsurface-ocean`, maybe some `cryovolcanic` niches,
    - atmosphere is not `none` unless the niche is subsurface,
    - sterilization regime is below a threshold for long enough.

Then:

- `prebiotic → microbial` should require a long stable window plus enough chemical free energy.
- `microbial → complex` should require ecological headroom and long-term continuity.
- `complex → technological` should be rare and contingent, not inevitable.

That keeps the biology honest while still leaving room for art.

## Modifier catalog

This is the other big missing piece.

A ton of the things you brought up should **not** be core states:

- Earth having a large moon.
- Jupiter changing impact statistics.
- A late heavy bombardment.
- Tidal heating in resonances.
- Migration history.
- Strong stellar youth activity.
- Metal-rich feedstock.
- Nearby supernova enrichment.

Those are best modeled as **transition modifiers**. They do not tell you what the object *is*; they tell you how likely or how fast certain transitions become.

I would define a canonical modifier catalog like this.


| Modifier | Owner | Applies to | Effect on transitions | Notes |
| :-- | :-- | :-- | :-- | :-- |
| `:mod/has-large-moon` | `domain.orbital` / `domain.environment` | primary rocky planet | Biases climate/obliquity stability transitions; may reduce chaotic seasonal forcing, may increase tides | Good place for the “did the Moon matter?” question without hardcoding a yes/no answer. |
| `:mod/giant-planet-shielding` | `domain.orbital` | inner rocky worlds | Reduces some impactor delivery pathways, but should be probabilistic rather than absolute | Keep nuanced; giant planets can shield or scatter inward depending on architecture. |
| `:mod/giant-planet-scattering` | `domain.orbital` | belts, comets, inner worlds | Increases `belt-member → scattered-body`, increases bombardment modifiers for inner planets | Jupiter-like worlds can do both. |
| `:mod/heavy-bombardment-era` | `domain.planet-formation` / `domain.environment` | young planets, moons | Raises chance of `crusted-volcanic → impact-reset`, `temperate-habitable → impact-reset`, `prebiotic → none` | Exactly addresses your “cool down, become habitable, partially remelt again” concern. |
| `:mod/strong-tidal-heating` | `domain.orbital` / `domain.environment` | satellites, close resonant worlds | Biases toward `tidally-heated`, `subsurface-ocean`, `cryovolcanic`, or volcanically active states | Essential for Europa/Io/Enceladus branches. |
| `:mod/stellar-youth-xuv` | `domain.stellar` / `domain.atmosphere` | early atmospheres | Pushes `stable-secondary → actively-stripped`, favors XUV escape regimes | Matches the atmosphere-escape research framing. |
| `:mod/volatile-rich-feedstock` | `domain.planet-formation` | worlds beyond/near snow lines | Increases probability of `ocean-world`, `icy-volatile-world`, thicker retained atmospheres | Important for composition inheritance from disk chemistry. [^17_1] |
| `:mod/metal-rich-feedstock` | `domain.planet-formation` | solid-body growth | Biases solids toward rocky/differentiated outcomes and faster core formation | Good tie-in to your enrichment/seeding specs. |
| `:mod/migration-history` | `domain.orbital` / `domain.planet-formation` | whole system architecture | Alters bombardment, resonance trapping, volatile delivery, and final role assignments | This one is system-scale, not just per-body. |
| `:mod/obliquity-chaos` | `domain.orbital` / `domain.environment` | terrestrial climates | Increases transition volatility between habitable, snowball, and arid states | Nice place for “no moon means unstable tilt” hypotheses. |
| `:mod/interior-dynamo-decline` | `domain.interior` / `domain.em` | rocky planets | Biases `stable-dynamo → episodic/no-dynamo`, which then amplifies atmospheric loss transitions | Clean Mars hook. |
| `:mod/late-volatile-delivery` | `domain.orbital` / `domain.environment` | dry rocky worlds | Can reopen `airless-inert` or `arid-thin-atmosphere` toward `transient-outgassed` or `ocean-world` under the right conditions | Good for impact-delivered oceans/atmospheres. |
| `:mod/sterilizing-impacts` | `domain.environment` / `domain.biology` | biospheres | Pushes `prebiotic → none`, `microbial → extinct`, or resets complex biospheres | Separate from generic bombardment because biospheres care about kill-thresholds, not just melting. |
| `:mod/subsurface-refugia` | `domain.environment` / `domain.biology` | icy worlds, harsh worlds | Allows life persistence despite hostile surface conditions; weakens extinction transitions | Important for Mars/Europa-like possibilities. |

## Why modifiers matter

This is how you avoid bad ontology.

For example:

- The Moon is **not** a biosphere state.
- Jupiter is **not** an environment state.
- A bombardment era is **not** a matter state.

They are **causal influences on transitions**.

That means you can write transition guards like:

- `temperate-habitable → impact-reset`
    - base chance from bombardment flux,
    - amplified by `heavy-bombardment-era`,
    - reduced by `giant-planet-shielding` if architecture actually supports that.

Or:

- `stable-secondary → actively-stripped`
    - base driven by XUV and wind,
    - amplified by `stellar-youth-xuv`,
    - amplified by `interior-dynamo-decline`,
    - weakened by `stable-dynamo`.

This gives you richness without making the state space explode.

## The full Truth stack

At this point the full conceptual architecture looks like this:

1. **Matter FSM**
    - nebula, collapsing gas, condensed core, protostar, star, remnant, dust, pebbles, planetesimals, asteroids, comets, protoplanets, dwarf planets, planets, giants, debris, rings.
2. **Role FSM**
    - free body, disk embedded, belt member, resonant, scattered, orbit clearer, satellite, ring member, escapee.
3. **Environment FSM**
    - magma ocean, impact reset, crusted volcanic, ocean world, habitable temperate, post-habitable, arid thin atmosphere, airless inert, snowball, runaway greenhouse, icy volatile world, subsurface ocean, tidally heated, cryovolcanic.
4. **Atmosphere / EM FSM**
    - dynamo states, magnetosphere compression/collapse, atmosphere retained/collapsing/stripped/frozen, XUV escape regimes.
5. **Biosphere FSM**
    - none, prebiotic, microbial, complex, technological, extinct, post-biological.
6. **Modifier catalog**
    - moon/tides, Jupiter/scattering, bombardment, migration, volatile delivery, dynamo decline, refugia, etc.

That is a true **nebula to life** architecture.

## What this buys you

This gives you exactly what you were reaching for earlier:

- more states in between,
- cleaner non-overlapping semantics,
- less pressure to “simulate everything all at once,”
- and a design where each module only owns a narrow, legible question.

It also makes room for worlds that are:

- habitable briefly,
- habitable subsurface only,
- once-habitable but now dead,
- atmospherically stripped,
- belt-trapped,
- moon-stabilized,
- impact-reset repeatedly,
- or culturally alive after biology changes.

That feels much closer to Truth.

## Next concrete step

The most useful next move would be to turn this into a **real spec artifact** in Truth style:

- sectioned by FSM,
- each state with observables, entry guards, exit guards, successors,
- then one implementation mapping section:
    - which ECS components store each active state,
    - which domain system owns transitions,
    - what the Phase‑0 handoff record exports.

That would be the document you can design the whole project around.

<div align="center">⁂</div>

[^17_1]: https://www.aanda.org/articles/aa/full_html/2019/09/aa35278-19/aa35278-19.html


